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
        self.row_tags: Dict[str, str] = {}  # serial -> row_tag
    
    def show(self):
        self.dialog_tag = dpg.generate_uuid()
        self.table_tag = dpg.generate_uuid()
        
        with dpg.window(
            label="Device Names Manager",
            modal=True,
            show=True,
            tag=self.dialog_tag,
            width=600,
            height=400,
            pos=[dpg.get_viewport_width() // 2 - 300, dpg.get_viewport_height() // 2 - 200],
            on_close=lambda: dpg.delete_item(self.dialog_tag)
        ):
            dpg.add_text("Manage custom names for Combatica Quest devices")
            dpg.add_separator()
            
            with dpg.child_window(height=-40, border=False):
                with dpg.table(
                    tag=self.table_tag,
                    header_row=True, 
                    borders_innerV=True, 
                    borders_outerV=True,
                    resizable=True
                ):
                    dpg.add_table_column(label="Serial Number", width_fixed=True, init_width_or_weight=200)
                    dpg.add_table_column(label="Custom Name", width_stretch=True)
                    dpg.add_table_column(label="Action", width_fixed=True, init_width_or_weight=80)
                    
                    # Add connected devices
                    devices = self.server.get_connected_devices()
                    for device in devices:
                        if device.device_info:
                            self._add_device_row(
                                device.device_info.serial,
                                True
                            )
                    
                    # Add disconnected devices that have custom names
                    all_names = device_name_manager.get_all_names()
                    connected_serials = {d.device_info.serial for d in devices if d.device_info}
                    disconnected_serials = set(all_names.keys()) - connected_serials
                    
                    for serial in disconnected_serials:
                        self._add_device_row(serial, False)
            
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
    
    def _add_device_row(self, serial: str, is_connected: bool):
        input_tag = dpg.generate_uuid()
        row_tag = dpg.generate_uuid()
        
        self.input_tags[serial] = input_tag
        self.row_tags[serial] = row_tag
        
        with dpg.table_row(parent=self.table_tag, tag=row_tag):
            color = (100, 250, 100) if is_connected else (150, 150, 150)
            status = "[CONNECTED] " if is_connected else "[OFFLINE] "
            dpg.add_text(f"{status}{serial}", color=color)
            
            current_name = device_name_manager.get_name(serial) or ""
            dpg.add_input_text(
                tag=input_tag,
                default_value=current_name,
                hint="Enter custom name",
                width=-1
            )
            
            dpg.add_button(
                label="Remove",
                callback=lambda: self._remove_device(serial),
                width=70,
                small=True
            )
    
    def _remove_device(self, serial: str):
        device_name_manager.remove_name(serial)
        
        for device in self.server.get_connected_devices():
            if device.device_info and device.device_info.serial == serial:
                device.invalidate_name_cache()
                
                event_bus.emit(EventType.DEVICE_NAME_CHANGED, {
                    'device_id': device.get_id(),
                    'serial': serial,
                    'new_name': None
                })
                event_bus.emit(EventType.DEVICE_UPDATED, device)
                break
        
        if serial in self.row_tags and dpg.does_item_exist(self.row_tags[serial]):
            dpg.delete_item(self.row_tags[serial])
        
        if serial in self.input_tags:
            del self.input_tags[serial]
        if serial in self.row_tags:
            del self.row_tags[serial]
    
    def _save_all(self):
        changes_made = False
        changed_serials = []
        
        for serial, input_tag in self.input_tags.items():
            if dpg.does_item_exist(input_tag):
                new_name = dpg.get_value(input_tag).strip()
                old_name = device_name_manager.get_name(serial) or ""
                
                if new_name != old_name:
                    device_name_manager.set_name(serial, new_name)
                    changes_made = True
                    changed_serials.append(serial)
        
        if changes_made:
            for serial in changed_serials:
                for device in self.server.get_connected_devices():
                    if device.device_info and device.device_info.serial == serial:
                        device.invalidate_name_cache()
                        
                        event_bus.emit(EventType.DEVICE_NAME_CHANGED, {
                            'device_id': device.get_id(),
                            'serial': serial,
                            'new_name': device_name_manager.get_name(serial)
                        })
                        event_bus.emit(EventType.DEVICE_UPDATED, device)
                        break
        
        dpg.delete_item(self.dialog_tag)