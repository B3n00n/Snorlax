import dearpygui.dearpygui as dpg

from core.server import QuestControlServer
from core.models import MessageType
from .device_list import DeviceListPanel
from .device_details import DeviceDetailsPanel
from .actions_panel import ActionsPanel
from .dev_actions_panel import DevActionsPanel
from utils.event_bus import event_bus, EventType


class MainWindow:
    def __init__(self, server: QuestControlServer):
        self.server = server
        self.device_list_panel = None
        self.device_details_panel = None
        self.actions_panel = None
        self.dev_actions_panel = None
        self.status_tag = None
        
        self._setup_ui()
        self._subscribe_events()
    
    def _setup_ui(self):
        with dpg.window(tag="main_window", label="Combatica Quest Control Center", no_close=True):
            # Menu bar
            with dpg.menu_bar():
                with dpg.menu(label="File"):
                    dpg.add_menu_item(label="Exit", callback=dpg.stop_dearpygui)
                
                with dpg.menu(label="Server"):
                    dpg.add_menu_item(label="Start Server", callback=self._start_server)
                    dpg.add_menu_item(label="Stop Server", callback=self._stop_server)
                    dpg.add_separator()
                    dpg.add_menu_item(label="Server Settings", callback=self._show_server_settings)
                
                with dpg.menu(label="View"):
                    dpg.add_menu_item(label="Clear All Logs", callback=self._clear_all_logs)
                    dpg.add_separator()
                    dpg.add_menu_item(label="Device Names Manager", callback=self._show_device_names_manager)
                    dpg.add_menu_item(label="APK File Manager", callback=self._show_apk_manager)
                
                with dpg.menu(label="Dev"):
                    dpg.add_menu_item(label="Toggle Developer Mode", callback=self._toggle_dev_mode)
            
            self.status_tag = dpg.add_text("Server: Stopped", color=(200, 200, 200))
            dpg.add_separator()
            
            with dpg.group(horizontal=True):
                with dpg.child_window(width=500, height=-30, border=True):
                    self.device_list_panel = DeviceListPanel(self.server, dpg.last_item())
                
                with dpg.child_window(width=450, height=-30, border=True):
                    self.device_details_panel = DeviceDetailsPanel(dpg.last_item())
                
                with dpg.child_window(width=-1, height=-30, border=True):
                    with dpg.tab_bar():
                        with dpg.tab(label="Actions"):
                            actions_container = dpg.generate_uuid()
                            with dpg.child_window(tag=actions_container, border=False):
                                self.actions_panel = ActionsPanel(
                                    self.server, 
                                    self.device_list_panel, 
                                    actions_container
                                )
                        
                        with dpg.tab(label="Dev", show=False, tag="dev_tab"):
                            dev_container = dpg.generate_uuid()
                            with dpg.child_window(tag=dev_container, border=False):
                                self.dev_actions_panel = DevActionsPanel(
                                    self.server,
                                    self.device_list_panel,
                                    dev_container
                                )
    
    def _subscribe_events(self):
        event_bus.subscribe(EventType.SERVER_STARTED, self._on_server_started)
        event_bus.subscribe(EventType.SERVER_STOPPED, self._on_server_stopped)
        event_bus.subscribe(EventType.ERROR_OCCURRED, self._on_error)
    
    def _on_server_started(self, data: dict):
        if self.status_tag and dpg.does_item_exist(self.status_tag):
            host = data.get('host', 'unknown')
            port = data.get('port', 'unknown')
            dpg.set_value(self.status_tag, f"Server: Running on {host}:{port}")
            dpg.configure_item(self.status_tag, color=(100, 250, 100))
    
    def _on_server_stopped(self, data=None):
        if self.status_tag and dpg.does_item_exist(self.status_tag):
            dpg.set_value(self.status_tag, "Server: Stopped")
            dpg.configure_item(self.status_tag, color=(250, 100, 100))
    
    def _on_error(self, error_message: str):
        if self.actions_panel:
            self.actions_panel._log_message(error_message, "error")
        if self.dev_actions_panel and dpg.does_item_exist("dev_tab") and dpg.get_item_configuration("dev_tab")["show"]:
            self.dev_actions_panel._log_message(error_message, "error")
    
    def _toggle_dev_mode(self):
        if dpg.does_item_exist("dev_tab"):
            current_state = dpg.get_item_configuration("dev_tab")["show"]
            dpg.configure_item("dev_tab", show=not current_state)
            
            if not current_state:
                if self.actions_panel:
                    self.actions_panel._log_message("Developer mode enabled", "warning")
            else:
                if self.actions_panel:
                    self.actions_panel._log_message("Developer mode disabled", "info")
    
    def _start_server(self):
        if not self.server.running:
            self.server.start()
    
    def _stop_server(self):
        if self.server.running:
            self.server.stop()
    
    def _show_server_settings(self):
        dialog_tag = dpg.generate_uuid()
        host_tag = dpg.generate_uuid()
        port_tag = dpg.generate_uuid()
        
        def save_settings():
            host = dpg.get_value(host_tag)
            port = int(dpg.get_value(port_tag))
            
            if self.server.running:
                self.server.stop()
            
            self.server.host = host
            self.server.port = port
            
            dpg.delete_item(dialog_tag)
        
        with dpg.window(
            label="Server Settings",
            modal=True,
            show=True,
            tag=dialog_tag,
            width=400,
            height=200,
            pos=[dpg.get_viewport_width() // 2 - 200, dpg.get_viewport_height() // 2 - 100]
        ):
            dpg.add_text("Server Configuration")
            dpg.add_separator()
            
            dpg.add_text("Host:")
            dpg.add_input_text(
                tag=host_tag,
                default_value=self.server.host,
                width=-1
            )
            
            dpg.add_text("Port:")
            dpg.add_input_int(
                tag=port_tag,
                default_value=self.server.port,
                min_value=1,
                max_value=65535,
                width=-1
            )
            
            dpg.add_spacer(height=10)
            
            with dpg.group(horizontal=True):
                dpg.add_button(label="Save", callback=save_settings, width=75)
                dpg.add_button(
                    label="Cancel",
                    callback=lambda: dpg.delete_item(dialog_tag),
                    width=75
                )
    
    def _clear_all_logs(self):
        if self.actions_panel and self.actions_panel.log_tag and dpg.does_item_exist(self.actions_panel.log_tag):
            dpg.delete_item(self.actions_panel.log_tag, children_only=True)
        
        if self.dev_actions_panel and self.dev_actions_panel.log_tag and dpg.does_item_exist(self.dev_actions_panel.log_tag):
            dpg.delete_item(self.dev_actions_panel.log_tag, children_only=True)
        
        if (self.device_details_panel and 
            self.device_details_panel.detail_tags.get('command_history') and 
            dpg.does_item_exist(self.device_details_panel.detail_tags['command_history'])):
            dpg.delete_item(self.device_details_panel.detail_tags['command_history'], children_only=True)
        
        for device in self.server.get_connected_devices():
            device.command_history.clear()
    
    def _show_device_names_manager(self):
        from gui.dialogs.device_names_dialog import DeviceNamesDialog
        dialog = DeviceNamesDialog(self.server)
        dialog.show()
        
    def _show_apk_manager(self):
        from gui.dialogs.apk_manager_dialog import APKManagerDialog
        dialog = APKManagerDialog(self.server)
        dialog.show()