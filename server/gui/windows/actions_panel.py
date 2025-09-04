import dearpygui.dearpygui as dpg
from typing import Optional

from core.server import QuestControlServer
from core.models import MessageType
from gui.dialogs.confirm_dialog import show_confirm_dialog
from gui.windows.device_list import DeviceListPanel
from config.settings import Config


class ActionsPanel:
    def __init__(self, server: QuestControlServer, device_list: DeviceListPanel, parent_tag: str):
        self.server = server
        self.device_list = device_list
        self.parent_tag = parent_tag
        self.log_tag = None
        
        self._setup_ui()
    
    def _setup_ui(self):
        with dpg.group(parent=self.parent_tag):
            
            # Quick actions
            dpg.add_text("Quick Actions")
            with dpg.group(horizontal=True):
                dpg.add_button(
                    label="Refresh Battery",
                    callback=self._refresh_battery,
                    width=120
                )
                dpg.add_button(
                    label="Ping Devices",
                    callback=self._ping_device,
                    width=120
                )
            
            dpg.add_spacer(height=10)
            
            # App management
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
            
            dpg.add_spacer(height=5)
            
            with dpg.group(horizontal=True):
                dpg.add_button(
                    label="Install APK",
                    callback=self._show_install_apk_dialog,
                    width=120
                )
            
            dpg.add_spacer(height=10)
            
            # System actions
            dpg.add_text("System Actions")
            with dpg.group(horizontal=True):
                dpg.add_button(
                    label="Shell Command",
                    callback=self._show_shell_dialog,
                    width=120
                )
                dpg.add_button(
                    label="Restart Devices",
                    callback=lambda: self._power_action("restart"),
                    width=120
                )
            
            dpg.add_spacer(height=20)
            
            # Command log
            dpg.add_text("Command Log")
            dpg.add_separator()
            
            self.log_tag = dpg.generate_uuid()
            dpg.add_child_window(
                tag=self.log_tag,
                height=-1,
                border=True
            )
    
    def _log_message(self, message: str, level: str = "info"):
        colors = {
            'info': (200, 200, 200),
            'success': (100, 250, 100),
            'error': (250, 100, 100),
            'warning': (250, 200, 100)
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
    
    def _refresh_battery(self):
        devices = self.server.get_connected_devices()
        if not devices:
            self._log_message("No connected devices", "warning")
            return
        
        for device in devices:
            device.send_message(MessageType.REQUEST_BATTERY)
        
        self._log_message(f"Requested battery status from {len(devices)} devices", "info")
    
    def _ping_device(self):
        devices = self._get_target_devices("ping")
        if not devices:
            return
        
        for device in devices:
            device.send_message(MessageType.PING)
            self._log_message(f"Pinging {device.get_display_name()}", "info")
    
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
            package_name = dpg.get_value(input_tag)
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
            package_name = dpg.get_value(input_tag)
            
            if not package_name:
                return
            
            if package_name in Config.PROTECTED_PACKAGES:
                self._log_message(f"Cannot uninstall protected package: {package_name}", "error")
                dpg.delete_item(dialog_tag)
                return
            
            def confirm_callback(confirmed):
                if confirmed:
                    for device in devices:
                        device.send_uninstall_command(package_name)
                        self._log_message(f"Uninstalling {package_name} from {device.get_display_name()}", "warning")
            
            dpg.delete_item(dialog_tag)
            
            device_names = ", ".join([d.get_display_name() for d in devices])
            show_confirm_dialog(
                "Confirm Uninstall",
                f"Are you sure you want to uninstall {package_name} from:\n{device_names}?",
                confirm_callback
            )
        
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
    
    def _show_install_apk_dialog(self):
        devices = self._get_target_devices("install APK")
        if not devices:
            return
        
        dialog_tag = dpg.generate_uuid()
        input_tag = dpg.generate_uuid()
        
        def install_apk():
            apk_url = dpg.get_value(input_tag)
            if apk_url and apk_url.startswith(('http://', 'https://')):
                for device in devices:
                    device.send_command(MessageType.DOWNLOAD_AND_INSTALL_APK, apk_url)
                    self._log_message(f"Installing APK on {device.get_display_name()}: {apk_url}", "info")
            else:
                self._log_message("Invalid APK URL", "error")
            dpg.delete_item(dialog_tag)
        
        device_names = ", ".join([d.get_display_name() for d in devices])
        
        with dpg.window(
            label="Install APK",
            modal=True,
            show=True,
            tag=dialog_tag,
            width=500,
            height=200,
            pos=[dpg.get_viewport_width() // 2 - 250, dpg.get_viewport_height() // 2 - 100]
        ):
            dpg.add_text(f"Install on: {device_names}", wrap=480)
            dpg.add_separator()
            dpg.add_text("Enter APK URL:")
            dpg.add_input_text(
                tag=input_tag,
                hint="https://storage.googleapis.com/combatica_test_bucket/Combatica_Platform.apk",
                width=-1
            )
            dpg.add_spacer(height=10)
            
            with dpg.group(horizontal=True):
                dpg.add_button(label="Install", callback=install_apk, width=75)
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
            command = dpg.get_value(input_tag)
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
    
    def _clear_all_logs(self):
        # Clear this log
        dpg.delete_item(self.log_tag, children_only=True)
        self._log_message("Logs cleared", "info")
    
    def _power_action(self, action: str):
        if action != "restart":
            return
            
        devices = self._get_target_devices(f"{action} devices")
        if not devices:
            return
        
        def confirm_callback(confirmed):
            if confirmed:
                for device in devices:
                    device.send_shutdown_command(action)
                    self._log_message(f"Sending {action} command to {device.get_display_name()}", "warning")
        
        device_names = "\n".join([f"  • {d.get_display_name()}" for d in devices])
        show_confirm_dialog(
            f"Confirm {action.capitalize()}",
            f"Are you sure you want to {action} these devices?\n\n{device_names}",
            confirm_callback
        )