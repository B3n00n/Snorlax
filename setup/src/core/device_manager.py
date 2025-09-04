from typing import Tuple, Optional

from config import PACKAGE_NAME
from .adb_manager import ADBManager
from ..utils.logger import logger


class DeviceManager:
    def __init__(self, adb_manager: ADBManager):
        self.adb = adb_manager
        
    def get_device_info(self) -> Tuple[Optional[str], Optional[str]]:
        success_mfr, manufacturer = self.adb.shell("getprop ro.product.manufacturer")
        success_model, model = self.adb.shell("getprop ro.product.model")
        
        if success_mfr and success_model:
            return manufacturer.strip(), model.strip()
        return None, None
        
    def is_quest_device(self) -> bool:
        manufacturer, model = self.get_device_info()
        
        if manufacturer and model:
            is_quest = (
                "oculus" in manufacturer.lower() or 
                "meta" in manufacturer.lower() or
                "quest" in model.lower()
            )
            
            if is_quest:
                logger.success(f"Detected Quest device: {manufacturer} {model}")
                return True
            else:
                logger.warning(f"Device is not a Quest: {manufacturer} {model}")
                
        return False
        
    def check_package_installed(self, package_name: str = PACKAGE_NAME) -> bool:
        success, output = self.adb.shell(f"pm list packages {package_name}")
        return success and package_name in output
        
    def check_device_owner(self, package_name: str = PACKAGE_NAME) -> bool:
        success, output = self.adb.shell("dumpsys device_policy")
        
        if success and "Device Owner:" in output:
            lines = output.split('\n')
            for i, line in enumerate(lines):
                if "Device Owner:" in line:
                    for j in range(i, min(i+5, len(lines))):
                        if package_name in lines[j]:
                            return True
        return False
        
    def get_device_status(self) -> dict:
        manufacturer, model = self.get_device_info()
        
        return {
            "manufacturer": manufacturer or "Unknown",
            "model": model or "Unknown",
            "is_quest": self.is_quest_device(),
            "package_installed": self.check_package_installed(),
            "is_device_owner": self.check_device_owner(),
            "connected": bool(self.adb.get_connected_devices())
        }