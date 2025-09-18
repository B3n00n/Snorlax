import dearpygui.dearpygui as dpg
from typing import Optional, List, Dict
import os

from core.server import QuestControlServer
from core.models import MessageType
from gui.windows.device_list import DeviceListPanel
from config.settings import Config
from utils.event_bus import event_bus, EventType


class ActionsPanel:
    def __init__(self, server: QuestControlServer, device_list: DeviceListPanel, parent_tag: str):
        self.server = server
        self.device_list = device_list
        self.parent_tag = parent_tag
        self.log_tag = None
        self.combatica_apps_cache: Dict[str, List[str]] = {}  # device_id -> list of combatica packages
        
        self._setup_ui()
        self._subscribe_events()
    
    def _setup_ui(self):
        with dpg.group(parent=self.parent_tag):
            
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
            
            # Combatica App Management
            dpg.add_text("App Management")
            with dpg.group(horizontal=True):
                dpg.add_button(
                    label="Launch App",
                    callback=self._show_launch_combatica_app_dialog,
                    width=120
                )
                dpg.add_button(
                    label="Uninstall App",
                    callback=self._show_uninstall_combatica_app_dialog,
                    width=120
                )
            
            dpg.add_spacer(height=5)
            
            with dpg.group(horizontal=True):
                dpg.add_button(
                    label="Install Remote APK",
                    callback=self._show_install_apk_dialog,
                    width=135
                )
                dpg.add_button(
                    label="Install Local APK",
                    callback=self._show_install_local_apk_dialog,
                    width=135
                )
            
            dpg.add_spacer(height=10)
            
            # System actions
            dpg.add_text("System Actions")
            with dpg.group(horizontal=True):
                dpg.add_button(
                    label="List Combatica Apps",
                    callback=self._list_combatica_apps,
                    width=140
                )
                dpg.add_button(
                    label="Restart Devices",
                    callback=lambda: self._power_action("restart"),
                    width=120
                )
            
            dpg.add_spacer(height=10)
                
            # Volume Control
            dpg.add_text("Volume Control")
            with dpg.group(horizontal=True):
                self.volume_slider_tag = dpg.add_slider_int(
                    default_value=50,
                    min_value=0,
                    max_value=100,
                    format="%d%%",
                    width=200,
                    callback=self._on_volume_changed
                )
                dpg.add_button(
                    label="Get Volume",
                    callback=self._get_current_volume,
                    width=100
                )

            dpg.add_spacer(height=20)
            
            with dpg.group(horizontal=True):
                dpg.add_text("Command Log")
                dpg.add_spacer(width=245)
                dpg.add_button(
                    label="Clear",
                    callback=self._clear_command_log,
                    small=True
                )
            dpg.add_separator()
            
            self.log_tag = dpg.generate_uuid()
            dpg.add_child_window(
                tag=self.log_tag,
                height=-1,
                border=True
            )
    
    def _subscribe_events(self):
        event_bus.subscribe(EventType.COMMAND_EXECUTED, self._on_command_executed)
        event_bus.subscribe(EventType.DEVICE_UPDATED, self._on_device_updated)

    def _on_device_updated(self, device):
        # Update volume slider if this is one of our selected devices
        selected_devices = self.device_list.get_selected_devices()
        if device in selected_devices and device.volume_info:
            dpg.set_value(self.volume_slider_tag, device.volume_info['percentage'])
            
    def _on_command_executed(self, data: dict):
        device = data.get('device')
        success = data.get('success')
        message = data.get('message', '')
        
        if device and success and ',' in message:
            # This might be an app list response
            apps = message.split(',')
            combatica_apps = [app.strip() for app in apps if app.strip().startswith('com.CombaticaLTD.')]
            
            if combatica_apps:
                device_id = device.get_id()
                self.combatica_apps_cache[device_id] = combatica_apps
                self._log_message(f"Found {len(combatica_apps)} Combatica apps on {device.get_display_name()}", "success")
                for app in combatica_apps:
                    # Display only the product name
                    product_name = app.replace("com.CombaticaLTD.", "")
                    self._log_message(f"  - {product_name}", "info")
    
    def _log_message(self, message: str, level: str = "info"):
        colors = {
            'info': (200, 200, 200),
            'success': (100, 250, 100),
            'error': (250, 100, 100),
            'warning': (250, 200, 100),
            'combatica': (100, 200, 250)
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
    
    def _show_launch_combatica_app_dialog(self):
        devices = self._get_target_devices("launch Combatica app")
        if not devices:
            return
        
        self._log_message("Fetching installed Combatica apps...", "info")
        for device in devices:
            device.send_message(MessageType.GET_INSTALLED_APPS)
        
        self._show_launch_dialog_with_apps(devices)
    
    def _show_launch_dialog_with_apps(self, devices):
        dialog_tag = dpg.generate_uuid()
        combo_tag = dpg.generate_uuid()
        loading_text_tag = dpg.generate_uuid()
        
        def update_app_list():
            all_combatica_apps = set()
            device_app_map = {}
            
            for device in devices:
                device_id = device.get_id()
                if device_id in self.combatica_apps_cache:
                    device_apps = self.combatica_apps_cache[device_id]
                    all_combatica_apps.update(device_apps)
                    for app in device_apps:
                        if app not in device_app_map:
                            device_app_map[app] = []
                        device_app_map[app].append(device.get_display_name())
            
            app_display_map = {}
            for app in all_combatica_apps:
                if app.startswith("com.CombaticaLTD."):
                    display_name = app.replace("com.CombaticaLTD.", "")
                    app_display_map[display_name] = app
            
            if not app_display_map:
                dpg.set_value(loading_text_tag, "No Combatica apps found. Click Refresh to try again.")
                dpg.configure_item(loading_text_tag, color=(250, 200, 100))
                return
            
            app_options = ["Select a Combatica app..."] + sorted(list(app_display_map.keys()))
            dpg.configure_item(combo_tag, items=app_options)
            dpg.hide_item(loading_text_tag)
            dpg.show_item(combo_tag)
            
            dpg.set_item_user_data(combo_tag, app_display_map)
        
        def launch_app():
            selected_display = dpg.get_value(combo_tag)
            app_display_map = dpg.get_item_user_data(combo_tag)
            
            if selected_display and selected_display != "Select a Combatica app..." and app_display_map:
                full_package = app_display_map.get(selected_display)
                if full_package:
                    for device in devices:
                        device_id = device.get_id()
                        if device_id in self.combatica_apps_cache and full_package in self.combatica_apps_cache[device_id]:
                            device.send_command(MessageType.LAUNCH_APP, full_package)
                            self._log_message(
                                f"Launching {selected_display} on {device.get_display_name()}", 
                                "combatica"
                            )
                        else:
                            self._log_message(
                                f"Warning: {selected_display} not found on {device.get_display_name()}, attempting launch anyway", 
                                "warning"
                            )
                            device.send_command(MessageType.LAUNCH_APP, full_package)
            dpg.delete_item(dialog_tag)
        
        def refresh_apps():
            dpg.hide_item(combo_tag)
            dpg.show_item(loading_text_tag)
            dpg.set_value(loading_text_tag, "Fetching apps...")
            dpg.configure_item(loading_text_tag, color=(150, 150, 150))
            
            for device in devices:
                device.send_message(MessageType.GET_INSTALLED_APPS)
            
            dpg.set_frame_callback(dpg.get_frame_count() + 60, callback=lambda: update_app_list())
        
        device_names = ", ".join([d.get_display_name() for d in devices])
        
        with dpg.window(
            label="Launch Combatica App",
            modal=True,
            show=True,
            tag=dialog_tag,
            width=550,
            height=250,
            pos=[dpg.get_viewport_width() // 2 - 275, dpg.get_viewport_height() // 2 - 125],
            on_close=lambda: dpg.delete_item(dialog_tag)
        ):
            dpg.add_text(f"Launch on: {device_names}", wrap=530)
            dpg.add_separator()
            dpg.add_text("Select Combatica app to launch:")
            
            dpg.add_text(
                "Fetching apps...",
                tag=loading_text_tag,
                color=(150, 150, 150)
            )
            
            dpg.add_combo(
                tag=combo_tag,
                items=["Select a Combatica app..."],
                default_value="Select a Combatica app...",
                width=-1,
                show=False
            )
            
            dpg.add_spacer(height=10)
            
            with dpg.group(horizontal=True):
                dpg.add_button(label="Launch", callback=launch_app, width=75)
                dpg.add_button(label="Refresh", callback=lambda: refresh_apps(), width=75)
                dpg.add_button(
                    label="Cancel",
                    callback=lambda: dpg.delete_item(dialog_tag),
                    width=75
                )
        
        dpg.set_frame_callback(dpg.get_frame_count() + 60, callback=lambda: update_app_list())
    
    def _refresh_and_reopen_launch(self, devices, current_dialog):
        dpg.delete_item(current_dialog)
        for device in devices:
            device_id = device.get_id()
            if device_id in self.combatica_apps_cache:
                del self.combatica_apps_cache[device_id]
        self._show_launch_combatica_app_dialog()
    
    def _show_uninstall_combatica_app_dialog(self):
        devices = self._get_target_devices("uninstall Combatica app")
        if not devices:
            return
        
        self._log_message("Fetching installed Combatica apps...", "info")
        for device in devices:
            device.send_message(MessageType.GET_INSTALLED_APPS)
        
        self._show_uninstall_dialog_with_apps(devices)
    
    def _show_uninstall_dialog_with_apps(self, devices):
        dialog_tag = dpg.generate_uuid()
        combo_tag = dpg.generate_uuid()
        loading_text_tag = dpg.generate_uuid()
        
        def update_app_list():
            all_combatica_apps = set()
            device_app_map = {}
            
            for device in devices:
                device_id = device.get_id()
                if device_id in self.combatica_apps_cache:
                    device_apps = self.combatica_apps_cache[device_id]
                    all_combatica_apps.update(device_apps)
                    for app in device_apps:
                        if app not in device_app_map:
                            device_app_map[app] = []
                        device_app_map[app].append(device.get_display_name())
            
            app_display_map = {}
            for app in all_combatica_apps:
                if app.startswith("com.CombaticaLTD."):
                    display_name = app.replace("com.CombaticaLTD.", "")
                    app_display_map[display_name] = app
            
            if not app_display_map:
                dpg.set_value(loading_text_tag, "No Combatica apps found on selected devices.")
                dpg.configure_item(loading_text_tag, color=(250, 200, 100))
                return
            
            app_options = ["Select a Combatica app..."] + sorted(list(app_display_map.keys()))
            dpg.configure_item(combo_tag, items=app_options)
            dpg.hide_item(loading_text_tag)
            dpg.show_item(combo_tag)
            
            dpg.set_item_user_data(combo_tag, (app_display_map, device_app_map))
        
        def uninstall_app():
            selected_display = dpg.get_value(combo_tag)
            data = dpg.get_item_user_data(combo_tag)
            
            if selected_display and selected_display != "Select a Combatica app..." and data:
                app_display_map, device_app_map = data
                full_package = app_display_map.get(selected_display)
                
                if full_package:
                    dpg.delete_item(dialog_tag)
                    
                    self._log_message(f"Uninstalling {selected_display}...", "warning")
                    
                    for device in devices:
                        device_id = device.get_id()
                        if device_id in self.combatica_apps_cache and full_package in self.combatica_apps_cache[device_id]:
                            success = device.send_uninstall_command(full_package)
                            if success:
                                self._log_message(
                                    f"Uninstall command sent to {device.get_display_name()}", 
                                    "combatica"
                                )
                                self.combatica_apps_cache[device_id] = [
                                    app for app in self.combatica_apps_cache[device_id] 
                                    if app != full_package
                                ]
                            else:
                                self._log_message(
                                    f"Failed to send uninstall command to {device.get_display_name()}", 
                                    "error"
                                )
                        else:
                            self._log_message(
                                f"Skipping {device.get_display_name()} - {selected_display} not installed", 
                                "info"
                            )
            else:
                dpg.delete_item(dialog_tag)
        
        def refresh_apps():
            dpg.hide_item(combo_tag)
            dpg.show_item(loading_text_tag)
            dpg.set_value(loading_text_tag, "Fetching apps...")
            dpg.configure_item(loading_text_tag, color=(150, 150, 150))
            
            for device in devices:
                device.send_message(MessageType.GET_INSTALLED_APPS)
            
            dpg.set_frame_callback(dpg.get_frame_count() + 60, callback=lambda: update_app_list())
        
        device_names = ", ".join([d.get_display_name() for d in devices])
        
        with dpg.window(
            label="Uninstall Combatica App",
            modal=True,
            show=True,
            tag=dialog_tag,
            width=550,
            height=250,
            pos=[dpg.get_viewport_width() // 2 - 275, dpg.get_viewport_height() // 2 - 125],
            on_close=lambda: dpg.delete_item(dialog_tag)
        ):
            dpg.add_text(f"Uninstall from: {device_names}", wrap=530)
            dpg.add_separator()
            dpg.add_text("Select Combatica app to uninstall:")
            
            dpg.add_text(
                "Fetching apps...",
                tag=loading_text_tag,
                color=(150, 150, 150)
            )
            
            dpg.add_combo(
                tag=combo_tag,
                items=["Select a Combatica app..."],
                default_value="Select a Combatica app...",
                width=-1,
                show=False
            )
            
            dpg.add_spacer(height=10)
            
            with dpg.group(horizontal=True):
                dpg.add_button(label="Uninstall", callback=uninstall_app, width=75)
                dpg.add_button(label="Refresh", callback=lambda: refresh_apps(), width=75)
                dpg.add_button(
                    label="Cancel",
                    callback=lambda: dpg.delete_item(dialog_tag),
                    width=75
                )
        
        # Schedule initial update after a delay
        dpg.set_frame_callback(dpg.get_frame_count() + 60, callback=lambda: update_app_list())
    
    def _refresh_and_reopen_uninstall(self, devices, current_dialog):
        dpg.delete_item(current_dialog)
        # Clear cache to force refresh
        for device in devices:
            device_id = device.get_id()
            if device_id in self.combatica_apps_cache:
                del self.combatica_apps_cache[device_id]
        self._show_uninstall_combatica_app_dialog()
    
    def _list_combatica_apps(self):
        devices = self._get_target_devices("list Combatica apps")
        if not devices:
            return
        
        for device in devices:
            device.send_message(MessageType.GET_INSTALLED_APPS)
            self._log_message(
                f"Checking Combatica apps on {device.get_display_name()}", 
                "combatica"
            )
    
    def _show_install_apk_dialog(self):
        devices = self._get_target_devices("install APK")
        if not devices:
            return
        
        dialog_tag = dpg.generate_uuid()
        input_tag = dpg.generate_uuid()
        
        def install_apk():
            apk_url = dpg.get_value(input_tag).strip()
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
    
    def _show_install_local_apk_dialog(self):
        devices = self._get_target_devices("install local APK")
        if not devices:
            return
    
        apk_files = self.server.get_available_apks()
    
        if not apk_files:
            self._log_message("No APK files found in server's 'apks' directory", "warning")
            return
    
        dialog_tag = dpg.generate_uuid()
        combo_tag = dpg.generate_uuid()
    
        def install_local_apk():
            selected_apk = dpg.get_value(combo_tag)
            if selected_apk and selected_apk != "Select an APK...":
                apk_url = self.server.apk_server.get_apk_url(selected_apk)
                
                for device in devices:
                    device.send_install_local_apk_command(apk_url)
                    self._log_message(
                        f"Installing {selected_apk} on {device.get_display_name()}", 
                        "info"
                    )
            dpg.delete_item(dialog_tag)
    
        device_names = ", ".join([d.get_display_name() for d in devices])
    
        with dpg.window(
            label="Install Local APK",
            modal=True,
            show=True,
            tag=dialog_tag,
            width=500,
            height=200,
            pos=[dpg.get_viewport_width() // 2 - 250, dpg.get_viewport_height() // 2 - 125]
        ):
            dpg.add_text(f"Install on: {device_names}", wrap=480)
            dpg.add_separator()
            dpg.add_text("Select APK from server:")
        
            dpg.add_combo(
                tag=combo_tag,
                items=["Select an APK..."] + apk_files,
                default_value="Select an APK...",
                width=-1
            )
                        
            dpg.add_spacer(height=10)
        
            with dpg.group(horizontal=True):
                dpg.add_button(label="Install", callback=install_local_apk, width=75)
                dpg.add_button(
                    label="Cancel",
                    callback=lambda: dpg.delete_item(dialog_tag),
                    width=75
                )
    
    def _power_action(self, action: str):
        if action != "restart":
            return
            
        devices = self._get_target_devices(f"{action} devices")
        if not devices:
            return
        
        self._log_message(f"Sending {action} command to {len(devices)} devices", "warning")
        
        for device in devices:
            device.send_shutdown_command(action)
            self._log_message(f"Sent {action} command to {device.get_display_name()}", "warning")

    def _on_volume_changed(self, sender, value):
        devices = self._get_target_devices("volume control")
        if not devices:
            return
    
        for device in devices:
            device.send_volume_command(value)
    
        self._log_message(f"Setting volume to {value}% on {len(devices)} device(s)", "info")

    def _get_current_volume(self):
        devices = self._get_target_devices("get volume")
        if not devices:
            return
    
        device = devices[0]
        device.request_volume_status()
        self._log_message(f"Requesting volume status from {device.get_display_name()}", "info")

    def _clear_command_log(self):
        if self.log_tag and dpg.does_item_exist(self.log_tag):
            dpg.delete_item(self.log_tag, children_only=True)