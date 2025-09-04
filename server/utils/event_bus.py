from typing import Dict, List, Callable, Any
from enum import Enum, auto
import threading

class EventType(Enum):
    DEVICE_CONNECTED = auto()
    DEVICE_DISCONNECTED = auto()
    DEVICE_UPDATED = auto()
    DEVICE_NAME_CHANGED = auto()
    BATTERY_UPDATED = auto()
    COMMAND_EXECUTED = auto()
    ERROR_OCCURRED = auto()
    SERVER_STARTED = auto()
    SERVER_STOPPED = auto()


class EventBus:
    def __init__(self):
        self._subscribers: Dict[EventType, List[Callable]] = {}
        self._lock = threading.Lock()
    
    def subscribe(self, event_type: EventType, callback: Callable):
        with self._lock:
            if event_type not in self._subscribers:
                self._subscribers[event_type] = []
            self._subscribers[event_type].append(callback)
    
    def unsubscribe(self, event_type: EventType, callback: Callable):
        with self._lock:
            if event_type in self._subscribers:
                self._subscribers[event_type].remove(callback)
    
    def emit(self, event_type: EventType, data: Any = None):
        with self._lock:
            if event_type in self._subscribers:
                for callback in self._subscribers[event_type]:
                    try:
                        callback(data)
                    except Exception as e:
                        print(f"Error in event handler: {e}")


event_bus = EventBus()