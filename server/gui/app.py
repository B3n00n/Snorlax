import dearpygui.dearpygui as dpg
import threading
import time

from config.settings import Config
from core.server import QuestControlServer
from core.models import MessageType
from gui.windows.main_window import MainWindow
from gui.themes.dark_theme import apply_dark_theme
from utils.event_bus import event_bus, EventType


class QuestControlGUI:
    def __init__(self):
        self.config = Config()
        self.server = None
        self.main_window = None
        self.running = False
        
    def run(self):
        self.running = True
        
        dpg.create_context()
        
        dpg.create_viewport(
            title="Combatica Quest Control Center",
            width=self.config.WINDOW_WIDTH,
            height=self.config.WINDOW_HEIGHT
        )
        
        if self.config.USE_DARK_THEME:
            apply_dark_theme()
        
        self.server = QuestControlServer(
            host=self.config.DEFAULT_HOST,
            port=self.config.DEFAULT_PORT
        )
        
        self.main_window = MainWindow(self.server)
        
        dpg.setup_dearpygui()
        dpg.show_viewport()
        dpg.set_primary_window("main_window", True)
        
        self.server.start()
        
        update_thread = threading.Thread(target=self._update_loop, daemon=True)
        update_thread.start()
        
        while dpg.is_dearpygui_running():
            dpg.render_dearpygui_frame()
        
        self.running = False
        self.cleanup()
    
    def _update_loop(self):
        last_battery_update = 0
        
        while self.running:
            current_time = time.time()
            
            if current_time - last_battery_update > self.config.BATTERY_UPDATE_INTERVAL:
                devices = self.server.get_connected_devices()
                for device in devices:
                    device.send_message(MessageType.REQUEST_BATTERY)
                last_battery_update = current_time
            
            time.sleep(0.1)
    
    def cleanup(self):
        if self.server:
            self.server.cleanup()
        
        dpg.destroy_context()


def main():
    app = QuestControlGUI()
    app.run()


if __name__ == "__main__":
    main()