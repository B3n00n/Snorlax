import os
import tempfile
import urllib.request
from typing import Optional, Callable

from config import (
    APK_URL, PACKAGE_NAME, DEVICE_OWNER_COMPONENT, 
    MAIN_ACTIVITY
)
from .adb_manager import ADBManager
from .device_manager import DeviceManager
from ..utils.logger import logger


class SetupManager:
    def __init__(self, adb_path: Optional[str] = None):
        self.adb = ADBManager(adb_path) if adb_path else ADBManager()
        self.device = DeviceManager(self.adb)
        self.temp_apk_path: Optional[str] = None
        self.progress_callback: Optional[Callable] = None
        self.should_cancel = False
        
    def set_progress_callback(self, callback: Callable):
        self.progress_callback = callback
        
    def cancel_operation(self):
        self.should_cancel = True
        
    def download_apk(self) -> bool:
        if self.should_cancel:
            return False
            
        logger.info("Downloading Snorlax APK...")
        
        try:
            with tempfile.NamedTemporaryFile(suffix='.apk', delete=False) as tmp_file:
                self.temp_apk_path = tmp_file.name
                
            def download_progress(block_num, block_size, total_size):
                if self.should_cancel:
                    raise InterruptedError("Download cancelled")
                    
                downloaded = block_num * block_size
                percent = min(100, (downloaded / total_size) * 100)
                
                if self.progress_callback:
                    self.progress_callback("download", percent)
                    
            urllib.request.urlretrieve(APK_URL, self.temp_apk_path, download_progress)
            
            if os.path.exists(self.temp_apk_path) and os.path.getsize(self.temp_apk_path) > 0:
                logger.success(f"APK downloaded successfully ({os.path.getsize(self.temp_apk_path)} bytes)")
                return True
            else:
                logger.error("Downloaded file is empty or missing")
                return False
                
        except InterruptedError:
            logger.warning("Download cancelled by user")
            return False
        except Exception as e:
            logger.error(f"Failed to download APK: {str(e)}")
            return False
            
    def install_apk(self) -> bool:
        if self.should_cancel:
            return False
            
        if not self.temp_apk_path or not os.path.exists(self.temp_apk_path):
            logger.error("APK file not found")
            return False
            
        logger.info("Installing Snorlax APK...")
        
        if self.progress_callback:
            self.progress_callback("install", 0)
            
        success, output = self.adb.install(self.temp_apk_path)
        
        if self.progress_callback:
            self.progress_callback("install", 100)
            
        if success and "Success" in output:
            logger.success("Snorlax APK installed successfully")
            return True
        else:
            logger.error(f"Failed to install APK: {output}")
            return False
            
    def set_device_owner(self) -> bool:
        if self.should_cancel:
            return False
            
        logger.info("Setting Snorlax as device owner...")
        
        success, output = self.adb.set_device_owner(DEVICE_OWNER_COMPONENT)
        
        if success and "Success" in output:
            logger.success("Snorlax set as device owner successfully")
            return True
        else:
            if "already has a device owner" in output:
                logger.error("Device already has a device owner")
                logger.warning("Device must be factory reset to set a new device owner")
            elif "users on the device" in output:
                logger.error("Device has multiple users configured")
                logger.warning("Remove additional users or factory reset the device")
            elif "Not allowed to set the device owner" in output:
                logger.error("Device has existing accounts that prevent device owner setup")
                logger.warning("This typically means the device wasn't freshly set up")
            else:
                logger.error(f"Failed to set device owner: {output}")
            return False
            
    def launch_snorlax(self) -> bool:
        logger.info("Launching Snorlax app...")
        
        success, output = self.adb.start_activity(MAIN_ACTIVITY)
        
        if success:
            logger.success("Snorlax app launched")
            return True
        else:
            logger.warning(f"Failed to launch app: {output}")
            return False
            
    def cleanup(self):
        if self.temp_apk_path and os.path.exists(self.temp_apk_path):
            try:
                os.remove(self.temp_apk_path)
                logger.info("Cleaned up temporary files")
            except Exception as e:
                logger.warning(f"Failed to clean up temp file: {e}")
                
    def run_setup(self, skip_quest_check: bool = False) -> bool:
        self.should_cancel = False
        
        try:
            if not self.adb.check_available():
                logger.error("ADB is not available")
                return False
                
            devices = self.adb.get_connected_devices()
            if not devices:
                logger.error("No ADB devices found")
                return False
            elif len(devices) > 1:
                logger.error("Multiple devices found. Please connect only one device.")
                return False
                
            logger.success(f"Found device: {devices[0]}")
            
            if not skip_quest_check and not self.device.is_quest_device():
                logger.warning("This doesn't appear to be a Quest device")
                return False
                
            if self.device.check_package_installed():
                logger.warning("Snorlax is already installed")
                
                if self.device.check_device_owner():
                    logger.success("Snorlax is already set as device owner!")
                    logger.info("Setup complete - no changes needed")
                    return True
                    
            if not self.download_apk():
                return False
                
            if not self.install_apk():
                return False
                
            if not self.set_device_owner():
                logger.error("Setup failed at device owner step")
                return False
                
            self.launch_snorlax()
            
            logger.success("Setup completed successfully!")
            return True
            
        finally:
            self.cleanup()