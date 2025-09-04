import socket
import threading
from datetime import datetime
from typing import Optional, Callable, Any

from core.enums import MessageType
from core.models import DeviceInfo, BatteryInfo, CommandResult
from core.packet import PacketWriter


class QuestDevice:    
    def __init__(self, client_socket: socket.socket, address: tuple):
        self.socket = client_socket
        self.address = address
        self.device_info: Optional[DeviceInfo] = None
        self.battery_info: Optional[BatteryInfo] = None
        self.last_response: Optional[CommandResult] = None
        self.is_connected = True
        self.lock = threading.Lock()
        
        self._on_info_updated: Optional[Callable[[DeviceInfo], Any]] = None
        self._on_battery_updated: Optional[Callable[[BatteryInfo], Any]] = None
        self._on_response_received: Optional[Callable[[CommandResult], Any]] = None
        self._on_disconnected: Optional[Callable[[], Any]] = None
    
    def set_callbacks(self, 
                     on_info_updated: Optional[Callable] = None,
                     on_battery_updated: Optional[Callable] = None,
                     on_response_received: Optional[Callable] = None,
                     on_disconnected: Optional[Callable] = None) -> None:
        if on_info_updated:
            self._on_info_updated = on_info_updated
        if on_battery_updated:
            self._on_battery_updated = on_battery_updated
        if on_response_received:
            self._on_response_received = on_response_received
        if on_disconnected:
            self._on_disconnected = on_disconnected
    
    def update_device_info(self, info: DeviceInfo) -> None:
        with self.lock:
            self.device_info = info
            if self._on_info_updated:
                self._on_info_updated(info)
    
    def update_battery_info(self, info: BatteryInfo) -> None:
        with self.lock:
            self.battery_info = info
            if self._on_battery_updated:
                self._on_battery_updated(info)
    
    def update_response(self, result: CommandResult) -> None:
        with self.lock:
            self.last_response = result
            if self._on_response_received:
                self._on_response_received(result)
    
    def disconnect(self) -> None:
        with self.lock:
            self.is_connected = False
            if self._on_disconnected:
                self._on_disconnected()
    
    def send_message(self, message_type: MessageType, data: bytes = b'') -> bool:
        try:
            writer = PacketWriter()
            writer.write_u8(message_type.value)
            writer.data.extend(data)
            
            with self.lock:
                self.socket.send(writer.to_bytes())
            return True
        except Exception as e:
            print(f"Error sending message to {self.get_display_name()}: {e}")
            self.disconnect()
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
            return f"{self.device_info.model} ({self.device_info.serial})"
        return f"{self.address[0]}:{self.address[1]}"
    
    def get_id(self) -> str:
        return self.device_info.serial if self.device_info else f"{self.address[0]}:{self.address[1]}"
    
    def close(self) -> None:
        try:
            self.socket.close()
        except:
            pass