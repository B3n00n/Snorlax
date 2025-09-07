class Config:
    DEFAULT_HOST = "0.0.0.0"
    DEFAULT_PORT = 8888
    
    WINDOW_WIDTH = 1400
    WINDOW_HEIGHT = 800
    REFRESH_RATE = 60
    
    DEVICE_LIST_UPDATE_INTERVAL = 1.0
    BATTERY_UPDATE_INTERVAL = 60.0
    
    COMMAND_TIMEOUT = 3.0
    
    USE_DARK_THEME = True
    
    LOG_LEVEL = "INFO"
    LOG_FILE = "quest_control.log"
    
    PROTECTED_PACKAGES = ["com.b3n00n.snorlax"]