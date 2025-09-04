import dearpygui.dearpygui as dpg
from typing import Dict

from core.server import QuestControlServer
from utils.device_names import device_name_manager
from utils.event_bus import event_bus, EventType


class DeviceNamesDialog:
    def __init__(self, server: QuestControlServer):
        self.server = server
        self.dialog_tag = None
        self.table_tag = None
        self.input_tags: Dict[str, str] = {}  # serial -> input_tag
    
    def show(self):
        """Show the device names dialog"""
        self.dialog_tag = dpg.generate_uuid()
        self.table_tag = dpg.generate_uuid()
        
        with dpg.window(
            label="Device Names Manager",
            modal=True,
            show=True,
            tag=self.dialog_tag,
            width=700,
            height=500,
            pos=[dpg.get_viewport_width() // 2 - 350, dpg.get_viewport_height() // 2 - 250],
            on_close=lambda: dpg.delete_item(self.dialog_tag)
        ):
            dpg.add_text("Manage custom names for your Quest devices")
            dpg.add_separator()
            
            # Create scrollable area with table
            with dpg.child_window(height=-40, border=False):
                with dpg.table(
                    tag=self.table_tag,
                    header_row=True, 
                    borders_innerV=True, 
                    borders_outerV=True,
                    resizable=True
                ):
                    dpg.add_table_column(label="Device Model", width_fixed=True, init_width_or_weight=200)
                    dpg.add_table_column(label="Serial Number", width_fixed=True, init_width_or_weight=200)
                    dpg.add_table_column(label="Custom Name", width_stretch=True)
                    
                    # Add connected devices
                    devices = self.server.get_connected_devices()
                    for device in devices:
                        if device.device_info:
                            self._add_device_row(
                                device.device_info.model,
                                device.device_info.serial,
                                True
                            )
                    
                    # Add saved names for disconnected devices
                    all_names = device_name_manager.get_all_names()
                    connected_serials = {d.device_info.serial for d in devices if d.device_info}
                    disconnected_serials = set(all_names.keys()) - connected_serials
                    
                    for serial in disconnected_serials:
                        self._add_device_row(
                            "Unknown Device",
                            serial,
                            False
                        )
            
            dpg.add_separator()
            
            # Buttons
            with dpg.group(horizontal=True):
                dpg.add_button(
                    label="Save All Changes",
                    callback=self._save_all,
                    width=150
                )
                dpg.add_button(
                    label="Close",
                    callback=lambda: dpg.delete_item(self.dialog_tag),
                    width=100
                )
    
    def _add_device_row(self, model: str, serial: str, is_connected: bool):
        """Add a device row to the dialog"""
        input_tag = dpg.generate_uuid()
        self.input_tags[serial] = input_tag
        
        with dpg.table_row(parent=self.table_tag):
            # Model column
            color = (100, 250, 100) if is_connected else (150, 150, 150)
            status = "● " if is_connected else "○ "
            dpg.add_text(f"{status}{model}", color=color)
            
            # Serial column
            dpg.add_text(serial, color=color)
            
            # Custom name input column
            current_name = device_name_manager.get_name(serial) or ""
            dpg.add_input_text(
                tag=input_tag,
                default_value=current_name,
                hint="Enter custom name",
                width=-1
            )
    
    def _save_all(self):
        """Save all custom names"""
        changes_made = False
        
        for serial, input_tag in self.input_tags.items():
            if dpg.does_item_exist(input_tag):
                new_name = dpg.get_value(input_tag)
                old_name = device_name_manager.get_name(serial) or ""
                
                if new_name != old_name:
                    device_name_manager.set_name(serial, new_name)
                    changes_made = True
        
        if changes_made:
            # Force update all connected devices
            for device in self.server.get_connected_devices():
                if device.device_info:
                    event_bus.emit(EventType.DEVICE_UPDATED, device)
        
        dpg.delete_item(self.dialog_tag)
    
    def _remove_name(self, serial: str):
        """Remove a saved name"""
        device_name_manager.remove_name(serial)
        
        # Refresh the dialog
        dpg.delete_item(self.dialog_tag)
        self.show()