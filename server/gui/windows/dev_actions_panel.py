import dearpygui.dearpygui as dpg
from typing import Optional

from core.server import QuestControlServer
from core.models import MessageType
from gui.windows.device_list import DeviceListPanel
from config.settings import Config


class DevActionsPanel:    
    def __init__(self, server: QuestControlServer, device_list: DeviceListPanel, parent_tag: str):
        self.server = server
        self.device_list = device_list
        self.parent_tag = parent_tag
        self.log_tag = None
        
        self._setup_ui()
    
    def _setup_ui(self):
        with dpg.group(parent=self.parent_tag):
            dpg.add_text("Developer Tools", color=(255, 200, 100))
            dpg.add_separator()
            
            dpg.add_text("App Management")
            with dpg.group(horizontal=True):
                dpg.add_button(
                    label="Launch App",
                    callback=self._show_launch_app_dialog,
                    width=120
                )
                dpg.add_button(
                    label="List Apps",
                    callback=self._list_apps,
                    width=120
                )
                dpg.add_button(
                    label="Uninstall App",
                    callback=self._show_uninstall_dialog,
                    width=120
                )
            
            dpg.add_spacer(height=10)
            
            dpg.add_text("System Actions")
            with dpg.group(horizontal=True):
                dpg.add_button(
                    label="Shell Command",
                    callback=self._show_shell_dialog,
                    width=120
                )
            
            dpg.add_spacer(height=20)
            
            # Dev Log
            with dpg.group(horizontal=True):
                dpg.add_text("Developer Log")
                dpg.add_spacer(width=235)
                dpg.add_button(
                    label="Clear",
                    callback=self._clear_dev_log,
                    small=True
                )
            dpg.add_separator()
    
    def _log_message(self, message: str, level: str = "info"):
        colors = {
            'info': (200, 200, 200),
            'success': (100, 250, 100),
            'error': (250, 100, 100),
            'warning': (250, 200, 100),
            'debug': (150, 150, 250)
        }
        
        with dpg.group(parent=self.log_tag, horizontal=True):
            dpg.add_text(
                f"[{dpg.get_frame_count()}]",
                color=(150, 150, 150)
            )
            dpg.add_text(message, color=colors.get(level, colors['info']))
    
    def _get_target_devices(self, action: str):
        selected = self.device_list.get_selected_devices()
        if not selected:
            self._log_message(f"No devices selected for {action}", "warning")
            return []
        return selected
    
    def _list_apps(self):
        devices = self._get_target_devices("list apps")
        if not devices:
            return
        
        for device in devices:
            device.send_message(MessageType.GET_INSTALLED_APPS)
            self._log_message(f"Requesting app list from {device.get_display_name()}", "info")
    
    def _show_launch_app_dialog(self):
        devices = self._get_target_devices("launch app")
        if not devices:
            return
        
        dialog_tag = dpg.generate_uuid()
        input_tag = dpg.generate_uuid()
        
        def launch_app():
            package_name = dpg.get_value(input_tag).strip()
            if package_name:
                for device in devices:
                    device.send_command(MessageType.LAUNCH_APP, package_name)
                    self._log_message(f"Launching {package_name} on {device.get_display_name()}", "info")
            dpg.delete_item(dialog_tag)
        
        device_names = ", ".join([d.get_display_name() for d in devices])
        
        with dpg.window(
            label="Launch App",
            modal=True,
            show=True,
            tag=dialog_tag,
            width=400,
            height=200,
            pos=[dpg.get_viewport_width() // 2 - 200, dpg.get_viewport_height() // 2 - 100]
        ):
            dpg.add_text(f"Launch app on: {device_names}", wrap=380)
            dpg.add_separator()
            dpg.add_text("Enter package name:")
            dpg.add_input_text(
                tag=input_tag,
                hint="com.example.app",
                width=-1
            )
            dpg.add_spacer(height=10)
            
            with dpg.group(horizontal=True):
                dpg.add_button(label="Launch", callback=launch_app, width=75)
                dpg.add_button(
                    label="Cancel",
                    callback=lambda: dpg.delete_item(dialog_tag),
                    width=75
                )
    
    def _show_uninstall_dialog(self):
        devices = self._get_target_devices("uninstall app")
        if not devices:
            return
        
        dialog_tag = dpg.generate_uuid()
        input_tag = dpg.generate_uuid()
        
        def uninstall_app():
            package_name = dpg.get_value(input_tag).strip()
            
            if not package_name:
                self._log_message("Package name cannot be empty", "error")
                return
            
            if package_name in Config.PROTECTED_PACKAGES:
                self._log_message(f"Cannot uninstall protected package: {package_name}", "error")
                dpg.delete_item(dialog_tag)
                return
            
            dpg.delete_item(dialog_tag)
            
            self._log_message(f"Uninstalling {package_name}...", "warning")
            
            for device in devices:
                success = device.send_uninstall_command(package_name)
                if success:
                    self._log_message(f"Uninstall command sent to {device.get_display_name()}", "success")
                else:
                    self._log_message(f"Failed to send uninstall command to {device.get_display_name()}", "error")
        
        device_names = ", ".join([d.get_display_name() for d in devices])
        
        with dpg.window(
            label="Uninstall App",
            modal=True,
            show=True,
            tag=dialog_tag,
            width=400,
            height=200,
            pos=[dpg.get_viewport_width() // 2 - 200, dpg.get_viewport_height() // 2 - 100]
        ):
            dpg.add_text(f"Uninstall from: {device_names}", wrap=380)
            dpg.add_separator()
            dpg.add_text("Enter package name to uninstall:")
            dpg.add_input_text(
                tag=input_tag,
                hint="com.example.app",
                width=-1
            )
            dpg.add_spacer(height=10)
            
            with dpg.group(horizontal=True):
                dpg.add_button(label="Uninstall", callback=uninstall_app, width=75)
                dpg.add_button(
                    label="Cancel",
                    callback=lambda: dpg.delete_item(dialog_tag),
                    width=75
                )
    
    def _show_shell_dialog(self):
        devices = self._get_target_devices("shell command")
        if not devices:
            return
        
        dialog_tag = dpg.generate_uuid()
        input_tag = dpg.generate_uuid()
        
        def execute_command():
            command = dpg.get_value(input_tag).strip()
            if command:
                for device in devices:
                    device.send_command(MessageType.EXECUTE_SHELL, command)
                    self._log_message(f"Executing on {device.get_display_name()}: {command}", "info")
            dpg.delete_item(dialog_tag)
        
        device_names = ", ".join([d.get_display_name() for d in devices])
        
        with dpg.window(
            label="Execute Shell Command",
            modal=True,
            show=True,
            tag=dialog_tag,
            width=500,
            height=200,
            pos=[dpg.get_viewport_width() // 2 - 250, dpg.get_viewport_height() // 2 - 100]
        ):
            dpg.add_text(f"Execute on: {device_names}", wrap=480)
            dpg.add_separator()
            dpg.add_text("Enter shell command:")
            dpg.add_input_text(
                tag=input_tag,
                hint="ls -la /sdcard/",
                width=-1
            )
            dpg.add_spacer(height=10)
            
            with dpg.group(horizontal=True):
                dpg.add_button(label="Execute", callback=execute_command, width=75)
                dpg.add_button(
                    label="Cancel",
                    callback=lambda: dpg.delete_item(dialog_tag),
                    width=75
                )


    def _clear_dev_log(self):
        if self.log_tag and dpg.does_item_exist(self.log_tag):
            dpg.delete_item(self.log_tag, children_only=True)