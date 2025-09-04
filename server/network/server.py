import socket
import threading
from datetime import datetime
from typing import Dict, Optional, Callable, List

from core.device import QuestDevice
from core.enums import MessageType
from core.models import DeviceInfo, BatteryInfo, CommandResult
from core.packet import PacketReader
from utils.logger import get_logger


class QuestControlServer:    
    def __init__(self, host: str = '0.0.0.0', port: int = 8888):
        self.host = host
        self.port = port
        self.server_socket: Optional[socket.socket] = None
        self.devices: Dict[str, QuestDevice] = {}
        self.running = False
        self.lock = threading.Lock()
        self.logger = get_logger(__name__)
        
        self._on_device_connected: Optional[Callable[[QuestDevice], None]] = None
        self._on_device_disconnected: Optional[Callable[[str], None]] = None
        self._on_server_started: Optional[Callable[[], None]] = None
        self._on_server_stopped: Optional[Callable[[], None]] = None
        self._on_error: Optional[Callable[[str], None]] = None
    
    def set_callbacks(self,
                     on_device_connected: Optional[Callable] = None,
                     on_device_disconnected: Optional[Callable] = None,
                     on_server_started: Optional[Callable] = None,
                     on_server_stopped: Optional[Callable] = None,
                     on_error: Optional[Callable] = None) -> None:
        if on_device_connected:
            self._on_device_connected = on_device_connected
        if on_device_disconnected:
            self._on_device_disconnected = on_device_disconnected
        if on_server_started:
            self._on_server_started = on_server_started
        if on_server_stopped:
            self._on_server_stopped = on_server_stopped
        if on_error:
            self._on_error = on_error
    
    def start(self) -> bool:
        if self.running:
            return False
        
        try:
            self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.server_socket.bind((self.host, self.port))
            self.server_socket.listen(10)
            self.server_socket.settimeout(1.0)  # Allow periodic checking of running flag
            
            self.running = True
            self.logger.info(f"Server started on {self.host}:{self.port}")
            
            if self._on_server_started:
                self._on_server_started()
            
            # Start accept thread
            accept_thread = threading.Thread(target=self._accept_loop, daemon=True)
            accept_thread.start()
            
            return True
            
        except Exception as e:
            self.logger.error(f"Failed to start server: {e}")
            if self._on_error:
                self._on_error(f"Failed to start server: {e}")
            return False
    
    def stop(self) -> None:
        self.logger.info("Stopping server...")
        self.running = False
        
        # Close all device connections
        with self.lock:
            for device in self.devices.values():
                device.close()
            self.devices.clear()
        
        # Close server socket
        if self.server_socket:
            try:
                self.server_socket.close()
            except:
                pass
            self.server_socket = None
        
        if self._on_server_stopped:
            self._on_server_stopped()
        
        self.logger.info("Server stopped")
    
    def _accept_loop(self) -> None:
        while self.running:
            try:
                client_socket, address = self.server_socket.accept()
                self.logger.info(f"New connection from {address[0]}:{address[1]}")
                
                # Handle client in new thread
                client_thread = threading.Thread(
                    target=self._handle_client,
                    args=(client_socket, address),
                    daemon=True
                )
                client_thread.start()
                
            except socket.timeout:
                continue
            except Exception as e:
                if self.running:
                    self.logger.error(f"Error accepting connection: {e}")
                    if self._on_error:
                        self._on_error(f"Error accepting connection: {e}")
    
    def _handle_client(self, client_socket: socket.socket, address: tuple) -> None:
        device = QuestDevice(client_socket, address)
        
        try:
            while self.running and device.is_connected:
                data = client_socket.recv(4096)
                if not data:
                    break
                
                self._process_message(device, data)
                
        except Exception as e:
            self.logger.error(f"Error handling client {address}: {e}")
        finally:
            # Remove device and notify
            device_id = device.get_id()
            with self.lock:
                if device_id in self.devices:
                    del self.devices[device_id]
            
            device.close()
            self.logger.info(f"Device disconnected: {device.get_display_name()}")
            
            if self._on_device_disconnected:
                self._on_device_disconnected(device_id)
    
    def _process_message(self, device: QuestDevice, data: bytes) -> None:
        try:
            reader = PacketReader(data)
            message_type = MessageType(reader.read_u8())
            
            if message_type == MessageType.DEVICE_CONNECTED:
                self._handle_device_connected(device, reader)
            elif message_type == MessageType.BATTERY_STATUS:
                self._handle_battery_status(device, reader)
            elif message_type == MessageType.COMMAND_RESPONSE:
                self._handle_command_response(device, reader)
            elif message_type == MessageType.ERROR:
                self._handle_error_message(device, reader)
            elif message_type == MessageType.HEARTBEAT:
                self._handle_heartbeat(device)
            
        except Exception as e:
            self.logger.error(f"Error processing message: {e}")
    
    def _handle_device_connected(self, device: QuestDevice, reader: PacketReader) -> None:
        model = reader.read_string()
        serial = reader.read_string()
        
        info = DeviceInfo(
            model=model,
            serial=serial,
            ip=device.address[0],
            connected_at=datetime.now(),
            last_seen=datetime.now()
        )
        
        device.update_device_info(info)
        
        with self.lock:
            self.devices[serial] = device
        
        self.logger.info(f"Device registered: {device.get_display_name()}")
        
        if self._on_device_connected:
            self._on_device_connected(device)
    
    def _handle_battery_status(self, device: QuestDevice, reader: PacketReader) -> None:
        headset = reader.read_u8()
        is_charging = bool(reader.read_u8())
        
        info = BatteryInfo(
            headset_level=headset,
            is_charging=is_charging,
            last_updated=datetime.now()
        )
        
        device.update_battery_info(info)
    
    def _handle_command_response(self, device: QuestDevice, reader: PacketReader) -> None:
        success = bool(reader.read_u8())
        message = reader.read_string()
        
        result = CommandResult(success=success, message=message)
        device.update_response(result)
    
    def _handle_error_message(self, device: QuestDevice, reader: PacketReader) -> None:
        error_message = reader.read_string()
        
        result = CommandResult(success=False, message=f"Error: {error_message}")
        device.update_response(result)
    
    def _handle_heartbeat(self, device: QuestDevice) -> None:
        if device.device_info:
            device.device_info.last_seen = datetime.now()
    
    def get_all_devices(self) -> List[QuestDevice]:
        with self.lock:
            return list(self.devices.values())
    
    def get_device(self, device_id: str) -> Optional[QuestDevice]:
        with self.lock:
            return self.devices.get(device_id)
    
    def broadcast_message(self, message_type: MessageType, data: bytes = b'') -> Dict[str, bool]:
        results = {}
        with self.lock:
            for device_id, device in self.devices.items():
                results[device_id] = device.send_message(message_type, data)
        return results
    
    def broadcast_command(self, message_type: MessageType, command: str = "") -> Dict[str, bool]:
        results = {}
        with self.lock:
            for device_id, device in self.devices.items():
                results[device_id] = device.send_command(message_type, command)
        return results