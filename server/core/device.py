import socket
import threading
from typing import Optional
from datetime import datetime

from .models import DeviceInfo, BatteryInfo, MessageType, CommandResult
from .packet import PacketWriter


class QuestDevice:
    def __init__(self, client_socket: socket.socket, address: tuple):
        self.socket = client_socket
        self.address = address
        self.device_info: Optional[DeviceInfo] = None
        self.battery_info: Optional[BatteryInfo] = None
        self.last_response: Optional[str] = None
        self.command_history: list[CommandResult] = []
        self.is_connected = True
        self.lock = threading.Lock()
    
    def send_message(self, message_type: MessageType, data: bytes = b'') -> bool:
        try:
            writer = PacketWriter()
            writer.write_u8(message_type.value)
            writer.data.extend(data)
            self.socket.send(writer.to_bytes())
            return True
        except Exception as e:
            print(f"Error sending message to {self.get_display_name()}: {e}")
            self.is_connected = False
            return False
    
    def send_command(self, message_type: MessageType, command: str = "") -> bool:
        writer = PacketWriter()
        if command:
            writer.write_string(command)
        return self.send_message(message_type, writer.to_bytes())
    
    def send_shutdown_command(self, action: str) -> bool:
        writer = PacketWriter()
        writer.write_string(action)
        return self.send_message(MessageType.SHUTDOWN_DEVICE, writer.to_bytes())
    
    def send_uninstall_command(self, package_name: str) -> bool:
        writer = PacketWriter()
        writer.write_string(package_name)
        return self.send_message(MessageType.UNINSTALL_APP, writer.to_bytes())
    
    def get_display_name(self) -> str:
        if self.device_info:
            try:
                from utils.device_names import device_name_manager
                custom_name = device_name_manager.get_name(self.device_info.serial)
                if custom_name:
                    return custom_name
            except Exception as e:
                print(f"Error getting custom name: {e}")
            return f"{self.device_info.model} ({self.device_info.serial})"
        return f"{self.address[0]}:{self.address[1]}"
    
    def invalidate_name_cache(self):
        self._cached_name = None
    
    def get_id(self) -> str:
        return self.device_info.serial if self.device_info else f"{self.address[0]}:{self.address[1]}"
    
    def add_command_result(self, success: bool, message: str):
        with self.lock:
            self.last_response = f"{'Success' if success else 'Failed'}: {message}"
            result = CommandResult(success=success, message=message)
            self.command_history.append(result)
            if len(self.command_history) > 50:
                self.command_history = self.command_history[-50:]