import json
import os
from typing import Dict, Optional


class DeviceNameManager:    
    def __init__(self, filename: str = "device_names.json"):
        self.filename = filename
        self.names: Dict[str, str] = {}
        self.load()
    
    def load(self):
        if os.path.exists(self.filename):
            try:
                with open(self.filename, 'r') as f:
                    self.names = json.load(f)
            except Exception as e:
                print(f"Error loading device names: {e}")
                self.names = {}
    
    def save(self):
        try:
            with open(self.filename, 'w') as f:
                json.dump(self.names, f, indent=2)
        except Exception as e:
            print(f"Error saving device names: {e}")
    
    def get_name(self, serial: str) -> Optional[str]:
        return self.names.get(serial)
    
    def set_name(self, serial: str, name: str):
        if name.strip():
            self.names[serial] = name.strip()
        else:
            self.names.pop(serial, None)
        self.save()
    
    def remove_name(self, serial: str):
        self.names.pop(serial, None)
        self.save()
    
    def get_all_names(self) -> Dict[str, str]:
        return self.names.copy()


device_name_manager = DeviceNameManager()