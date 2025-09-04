import socket
import threading
from typing import Dict, List, Optional, Callable
from datetime import datetime

from .device import QuestDevice
from .models import MessageType, DeviceInfo, BatteryInfo
from .packet import PacketReader
from utils.event_bus import event_bus, EventType


class QuestControlServer:
    def __init__(self, host='0.0.0.0', port=8888):
        self.host = host
        self.port = port
        self.server_socket = None
        self.devices: Dict[str, QuestDevice] = {}
        self.running = False
        self.lock = threading.Lock()
        self._server_thread = None
    
    def start(self):
        if not self.running:
            self._server_thread = threading.Thread(target=self._run_server, daemon=True)
            self._server_thread.start()
    
    def _run_server(self):
        self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        
        try:
            self.server_socket.bind((self.host, self.port))
            self.server_socket.listen(10)
            self.running = True
            
            event_bus.emit(EventType.SERVER_STARTED, {
                'host': self.host,
                'port': self.port
            })
            
            while self.running:
                try:
                    client_socket, address = self.server_socket.accept()
                    
                    client_thread = threading.Thread(
                        target=self._handle_client,
                        args=(client_socket, address),
                        daemon=True
                    )
                    client_thread.start()
                    
                except socket.error:
                    if self.running:
                        event_bus.emit(EventType.ERROR_OCCURRED, "Error accepting connection")
                    break
                    
        except Exception as e:
            event_bus.emit(EventType.ERROR_OCCURRED, f"Server error: {e}")
        finally:
            self.cleanup()
    
    def _handle_client(self, client_socket: socket.socket, address: tuple):
        device = QuestDevice(client_socket, address)
        
        try:
            while self.running and device.is_connected:
                data = client_socket.recv(4096)
                if not data:
                    break
                
                self._process_message(device, data)
                
        except Exception as e:
            event_bus.emit(EventType.ERROR_OCCURRED, f"Error handling client {address}: {e}")
        finally:
            with self.lock:
                device_id = device.get_id()
                if device_id in self.devices:
                    del self.devices[device_id]
                    event_bus.emit(EventType.DEVICE_DISCONNECTED, device_id)
            
            try:
                client_socket.close()
            except:
                pass
    
    def _process_message(self, device: QuestDevice, data: bytes):
        try:
            reader = PacketReader(data)
            message_type = MessageType(reader.read_u8())
            
            if message_type == MessageType.DEVICE_CONNECTED:
                model = reader.read_string()
                serial = reader.read_string()
                
                device.device_info = DeviceInfo(
                    model=model,
                    serial=serial,
                    ip=device.address[0],
                    connected_at=datetime.now(),
                    last_seen=datetime.now()
                )
                
                with self.lock:
                    self.devices[serial] = device
                
                event_bus.emit(EventType.DEVICE_CONNECTED, device)
            
            elif message_type == MessageType.BATTERY_STATUS:
                headset = reader.read_u8()
                is_charging = bool(reader.read_u8())
                
                device.battery_info = BatteryInfo(
                    headset_level=headset,
                    is_charging=is_charging,
                    last_updated=datetime.now()
                )
                
                event_bus.emit(EventType.BATTERY_UPDATED, device)
                
            elif message_type == MessageType.COMMAND_RESPONSE:
                success = bool(reader.read_u8())
                message = reader.read_string()
                device.add_command_result(success, message)
                
                event_bus.emit(EventType.COMMAND_EXECUTED, {
                    'device': device,
                    'success': success,
                    'message': message
                })
                
            elif message_type == MessageType.ERROR:
                error_message = reader.read_string()
                device.add_command_result(False, error_message)
                
                event_bus.emit(EventType.ERROR_OCCURRED, f"{device.get_display_name()}: {error_message}")
                
            elif message_type == MessageType.HEARTBEAT:
                pass
                
            if device.device_info:
                device.device_info.last_seen = datetime.now()
                event_bus.emit(EventType.DEVICE_UPDATED, device)
                
        except Exception as e:
            event_bus.emit(EventType.ERROR_OCCURRED, f"Error processing message: {e}")
    
    def get_connected_devices(self) -> List[QuestDevice]:
        with self.lock:
            return list(self.devices.values())
    
    def get_device_by_id(self, device_id: str) -> Optional[QuestDevice]:
        with self.lock:
            return self.devices.get(device_id)
    
    def broadcast_command(self, message_type: MessageType, command: str = "") -> Dict[str, bool]:
        results = {}
        for device_id, device in self.devices.items():
            results[device_id] = device.send_command(message_type, command)
        return results
    
    def stop(self):
        self.running = False
        
        with self.lock:
            for device in self.devices.values():
                try:
                    device.socket.close()
                except:
                    pass
            self.devices.clear()
        
        if self.server_socket:
            try:
                self.server_socket.close()
            except:
                pass
        
        event_bus.emit(EventType.SERVER_STOPPED)
    
    def cleanup(self):
        self.stop()