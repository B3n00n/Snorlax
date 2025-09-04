from dataclasses import dataclass
from datetime import datetime
from enum import IntEnum
from typing import Optional


class MessageType(IntEnum):
    DEVICE_CONNECTED = 0x01
    HEARTBEAT = 0x02
    BATTERY_STATUS = 0x03
    COMMAND_RESPONSE = 0x04
    ERROR = 0x05
    
    LAUNCH_APP = 0x10
    EXECUTE_SHELL = 0x12
    REQUEST_BATTERY = 0x13
    GET_INSTALLED_APPS = 0x14
    GET_DEVICE_INFO = 0x15
    PING = 0x16
    DOWNLOAD_AND_INSTALL_APK = 0x17
    SHUTDOWN_DEVICE = 0x18
    UNINSTALL_APP = 0x19


@dataclass
class DeviceInfo:
    model: str
    serial: str
    ip: str
    connected_at: datetime
    last_seen: datetime


@dataclass
class BatteryInfo:
    headset_level: int
    is_charging: bool
    last_updated: datetime


@dataclass
class CommandResult:
    success: bool
    message: str
    timestamp: datetime = None
    
    def __post_init__(self):
        if self.timestamp is None:
            self.timestamp = datetime.now()