import dearpygui.dearpygui as dpg
from datetime import datetime
from typing import Dict, Optional, Set

from core.device import QuestDevice
from core.server import QuestControlServer
from gui.themes.dark_theme import get_status_colors
from utils.event_bus import event_bus, EventType


class DeviceListPanel:
    def __init__(self, server: QuestControlServer, parent_tag: str):
        self.server = server
        self.parent_tag = parent_tag
        self.selected_device_ids: Set[str] = set()
        self.device_rows: Dict[str, str] = {}
        self.table_tag = None
        self.colors = get_status_colors()
        self.checkbox_tags: Dict[str, str] = {}  # device_id -> checkbox_tag
        
        self._setup_ui()
        self._subscribe_events()
    
    def _setup_ui(self):
        with dpg.group(parent=self.parent_tag):
            dpg.add_text("Connected Devices", tag="device_list_title")
            dpg.add_separator()
            
            with dpg.group(horizontal=True):
                dpg.add_button(
                    label="Select All",
                    callback=self._select_all,
                    width=100
                )
                dpg.add_button(
                    label="Deselect All",
                    callback=self._deselect_all,
                    width=100
                )
                dpg.add_text("", tag="selected_count_text")
            
            dpg.add_spacer(height=5)
            
            self.table_tag = dpg.generate_uuid()
            with dpg.table(
                tag=self.table_tag,
                header_row=True,
                borders_innerH=True,
                borders_outerH=True,
                borders_innerV=True,
                borders_outerV=True,
                scrollY=True,
                freeze_rows=1,
                height=-1,
                resizable=True,
                reorderable=True,
                hideable=True,
                sortable=True,
                context_menu_in_body=True
            ):
                dpg.add_table_column(label="Select", width_fixed=True, init_width_or_weight=50)
                dpg.add_table_column(label="Device", width_stretch=True, init_width_or_weight=0.0)
                dpg.add_table_column(label="IP", width_fixed=True, init_width_or_weight=120)
                dpg.add_table_column(label="Battery", width_fixed=True, init_width_or_weight=80)
            
            self._update_selected_count()
    
    def _subscribe_events(self):
        event_bus.subscribe(EventType.DEVICE_CONNECTED, self._on_device_connected)
        event_bus.subscribe(EventType.DEVICE_DISCONNECTED, self._on_device_disconnected)
        event_bus.subscribe(EventType.DEVICE_UPDATED, self._on_device_updated)
        event_bus.subscribe(EventType.DEVICE_NAME_CHANGED, self._on_device_name_changed)
        event_bus.subscribe(EventType.BATTERY_UPDATED, self._on_battery_updated)
        event_bus.subscribe(EventType.SERVER_STOPPED, self._on_server_stopped)
    
    def _on_device_connected(self, device: QuestDevice):
        self._add_device_row(device)
    
    def _on_device_disconnected(self, device_id: str):
        if device_id in self.device_rows:
            dpg.delete_item(self.device_rows[device_id])
            del self.device_rows[device_id]
            self.selected_device_ids.discard(device_id)
            if device_id in self.checkbox_tags:
                del self.checkbox_tags[device_id]
            self._update_selected_count()
    
    def _on_device_updated(self, device: QuestDevice):
        self._update_device_row(device)
    
    def _on_battery_updated(self, device: QuestDevice):
        self._update_device_row(device)
    
    def _on_device_name_changed(self, data: dict):
        device_id = data.get('device_id')
        if device_id and device_id in self.device_rows:
            device = self.server.get_device_by_id(device_id)
            if device:
                device.invalidate_name_cache()
                self._update_device_row(device)
    
    def _on_server_stopped(self, data=None):
        for row_tag in list(self.device_rows.values()):
            dpg.delete_item(row_tag)
        self.device_rows.clear()
        self.selected_device_ids.clear()
        self.checkbox_tags.clear()
        self._update_selected_count()
    
    def _add_device_row(self, device: QuestDevice):
        device_id = device.get_id()
        if device_id in self.device_rows:
            return
        
        row_tag = dpg.generate_uuid()
        self.device_rows[device_id] = row_tag
        
        with dpg.table_row(parent=self.table_tag, tag=row_tag):
            checkbox_tag = dpg.add_checkbox(
                default_value=device_id in self.selected_device_ids,
                callback=lambda s, v: self._on_device_checkbox_changed(device_id, v)
            )
            self.checkbox_tags[device_id] = checkbox_tag
            
            device_tag = dpg.add_selectable(
                label=device.get_display_name(),
                callback=lambda: self._on_device_clicked(device_id),
                span_columns=True
            )
            
            ip_tag = dpg.add_text(device.device_info.ip if device.device_info else "Unknown")
            
            battery_tag = dpg.add_text(self._get_battery_text(device))
            
            dpg.set_item_user_data(row_tag, {
                'checkbox': checkbox_tag,
                'device': device_tag,
                'ip': ip_tag,
                'battery': battery_tag
            })
    
    def _update_device_row(self, device: QuestDevice):
        device_id = device.get_id()
        if device_id not in self.device_rows:
            self._add_device_row(device)
            return
        
        row_tag = self.device_rows[device_id]
        tags = dpg.get_item_user_data(row_tag)
        
        if tags:
            if 'device' in tags:
                dpg.configure_item(tags['device'], label=device.get_display_name())
            
            if 'battery' in tags:
                dpg.set_value(tags['battery'], self._get_battery_text(device))
            
            if device.battery_info and 'battery' in tags:
                level = device.battery_info.headset_level
                if level < 20:
                    color = self.colors['battery_critical']
                elif level < 50:
                    color = self.colors['battery_low']
                else:
                    color = self.colors['connected']
                dpg.configure_item(tags['battery'], color=color)
    
    def _get_battery_text(self, device: QuestDevice) -> str:
        if not device.battery_info:
            return "N/A"
        
        charging = " [C]" if device.battery_info.is_charging else ""
        return f"{device.battery_info.headset_level}%{charging}"
    
    def _on_device_checkbox_changed(self, device_id: str, checked: bool):
        if checked:
            self.selected_device_ids.add(device_id)
        else:
            self.selected_device_ids.discard(device_id)
        self._update_selected_count()
    
    def _on_device_clicked(self, device_id: str):
        device = self.server.get_device_by_id(device_id)
        if device:
            event_bus.emit(EventType.DEVICE_UPDATED, device)
    
    def _select_all(self):
        for device_id, checkbox_tag in self.checkbox_tags.items():
            dpg.set_value(checkbox_tag, True)
            self.selected_device_ids.add(device_id)
        self._update_selected_count()
    
    def _deselect_all(self):
        for checkbox_tag in self.checkbox_tags.values():
            dpg.set_value(checkbox_tag, False)
        self.selected_device_ids.clear()
        self._update_selected_count()
    
    def _update_selected_count(self):
        count = len(self.selected_device_ids)
        total = len(self.device_rows)
        text = f"Selected: {count}/{total}" if total > 0 else ""
        if dpg.does_item_exist("selected_count_text"):
            dpg.set_value("selected_count_text", text)
    
    def get_selected_device(self) -> Optional[QuestDevice]:
        if self.selected_device_ids:
            device_id = next(iter(self.selected_device_ids))
            return self.server.get_device_by_id(device_id)
        return None
    
    def get_selected_devices(self) -> list[QuestDevice]:
        devices = []
        for device_id in self.selected_device_ids:
            device = self.server.get_device_by_id(device_id)
            if device:
                devices.append(device)
        return devices