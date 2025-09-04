from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional, Dict, Any


@dataclass
class DeviceInfo:
    model: str
    serial: str
    ip: str
    connected_at: datetime = field(default_factory=datetime.now)
    last_seen: datetime = field(default_factory=datetime.now)
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            'model': self.model,
            'serial': self.serial,
            'ip': self.ip,
            'connected_at': self.connected_at.isoformat(),
            'last_seen': self.last_seen.isoformat()
        }


@dataclass
class BatteryInfo:
    headset_level: int
    is_charging: bool
    last_updated: datetime = field(default_factory=datetime.now)
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            'headset_level': self.headset_level,
            'is_charging': self.is_charging,
            'last_updated': self.last_updated.isoformat()
        }


@dataclass
class CommandResult:
    success: bool
    message: str
    timestamp: datetime = field(default_factory=datetime.now)
    
    def __str__(self) -> str:
        status = "Success" if self.success else "Failed"
        return f"{status}: {self.message}"