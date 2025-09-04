import os
import platform

# ADB Configuration
DEFAULT_ADB_PATH = "adb"
WINDOWS_ADB_PATH = r"C:\platform-tools\adb.exe"
ADB_PATH = WINDOWS_ADB_PATH if platform.system() == "Windows" else DEFAULT_ADB_PATH

# Package Configuration
APK_URL = "https://storage.googleapis.com/combatica_test_bucket/Snorlax.apk"
PACKAGE_NAME = "com.b3n00n.snorlax"
DEVICE_OWNER_COMPONENT = f"{PACKAGE_NAME}/.receivers.DeviceOwnerReceiver"
MAIN_ACTIVITY = f"{PACKAGE_NAME}/.MainActivity"

# GUI Configuration
WINDOW_TITLE = "Snorlax Setup Tool"
WINDOW_ICON = None
THEME = "DarkBlue3"
FONT_FAMILY = "Arial"
FONT_SIZE_NORMAL = 10
FONT_SIZE_HEADING = 14
WINDOW_SIZE = (800, 600)

# Logging Configuration
LOG_FORMAT = "[%(asctime)s] %(levelname)s: %(message)s"
DATE_FORMAT = "%H:%M:%S"