import logging
from datetime import datetime
from typing import Optional, Callable
from enum import Enum

from .colors import ConsoleColors, GUIColors


class LogLevel(Enum):
    INFO = "INFO"
    SUCCESS = "SUCCESS"
    WARNING = "WARNING"
    ERROR = "ERROR"


class Logger:
    def __init__(self):
        self.console_enabled = True
        self.gui_callback: Optional[Callable] = None
        
    def set_gui_callback(self, callback: Callable):
        self.gui_callback = callback
        
    def log(self, message: str, level: LogLevel = LogLevel.INFO):
        timestamp = datetime.now().strftime("%H:%M:%S")
        
        if self.console_enabled:
            color = self._get_console_color(level)
            reset = ConsoleColors.RESET
            print(f"[{timestamp}] {color}{message}{reset}")
            
        if self.gui_callback:
            self.gui_callback(message, level, timestamp)
            
    def _get_console_color(self, level: LogLevel) -> str:
        color_map = {
            LogLevel.SUCCESS: ConsoleColors.GREEN,
            LogLevel.ERROR: ConsoleColors.RED,
            LogLevel.WARNING: ConsoleColors.YELLOW,
            LogLevel.INFO: ConsoleColors.BLUE
        }
        return color_map.get(level, "")
        
    def info(self, message: str):
        self.log(message, LogLevel.INFO)
        
    def success(self, message: str):
        self.log(message, LogLevel.SUCCESS)
        
    def warning(self, message: str):
        self.log(message, LogLevel.WARNING)
        
    def error(self, message: str):
        self.log(message, LogLevel.ERROR)


logger = Logger()