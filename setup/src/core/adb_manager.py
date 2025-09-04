import subprocess
import os
from typing import Tuple, List, Optional
import sys

from config import ADB_PATH
from ..utils.logger import logger

if sys.platform == "win32":
    startupinfo = subprocess.STARTUPINFO()
    startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
    startupinfo.wShowWindow = subprocess.SW_HIDE
else:
    startupinfo = None

class ADBManager:
    def __init__(self, adb_path: str = ADB_PATH):
        self.adb_path = adb_path
        
    def run_command(self, command: List[str], check_output: bool = True) -> Tuple[bool, str]:
        full_command = [self.adb_path] + command
        
        try:
            if check_output:
                result = subprocess.run(
                    full_command, 
                    capture_output=True, 
                    text=True, 
                    check=True,
                    startupinfo=startupinfo
                )
                return True, result.stdout.strip()
            else:
                subprocess.run(full_command, check=True)
                return True, ""
        except subprocess.CalledProcessError as e:
            error_msg = e.stderr.strip() if e.stderr else str(e)
            return False, error_msg
        except FileNotFoundError:
            return False, f"ADB not found at path: {self.adb_path}"
            
    def check_available(self) -> bool:
        try:
            if self.adb_path != "adb" and not os.path.exists(self.adb_path):
                logger.error(f"ADB not found at configured path: {self.adb_path}")
                return False
                
            subprocess.run(
                [self.adb_path, "version"], 
                capture_output=True, 
                check=True
            )
            return True
        except (subprocess.CalledProcessError, FileNotFoundError):
            return False
            
    def get_connected_devices(self) -> List[str]:
        success, output = self.run_command(["devices"])
        if not success:
            return []
            
        devices = []
        lines = output.strip().split('\n')
        for line in lines[1:]:
            if '\tdevice' in line:
                device_id = line.split('\t')[0]
                devices.append(device_id)
        return devices
        
    def shell(self, command: str) -> Tuple[bool, str]:
        return self.run_command(["shell"] + command.split())
        
    def install(self, apk_path: str) -> Tuple[bool, str]:
        return self.run_command(["install", "-r", apk_path])
        
    def set_device_owner(self, component: str) -> Tuple[bool, str]:
        return self.run_command(["shell", "dpm", "set-device-owner", component])
        
    def start_activity(self, activity: str) -> Tuple[bool, str]:
        return self.run_command(["shell", "am", "start", "-n", activity])