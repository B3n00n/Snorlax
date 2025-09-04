import dearpygui.dearpygui as dpg
from datetime import datetime
from typing import Dict, Optional

from core.device import QuestDevice
from core.server import QuestControlServer
from gui.themes.dark_theme import get_status_colors
from utils.event_bus import event_bus, EventType


class DeviceListPanel:
    def __init__(self, server: QuestControlServer, parent_tag: str):
        self.server = server
        self.parent_tag = parent_tag
        self.selected_device_id: Optional[str] = None
        self.device_rows: Dict[str, str] = {}
        self.table_tag = None
        self.colors = get_status_colors()
        
        self._setup_ui()
        self._subscribe_events()
    
    def _setup_ui(self):
        with dpg.group(parent=self.parent_tag):
            dpg.add_text("Connected Devices", tag="device_list_title")
            dpg.add_separator()
            
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
                dpg.add_table_column(label="Device", width_stretch=True, init_width_or_weight=0.0)
                dpg.add_table_column(label="IP", width_fixed=True, init_width_or_weight=120)
                dpg.add_table_column(label="Battery", width_fixed=True, init_width_or_weight=80)
    
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
    
    def _on_device_updated(self, device: QuestDevice):
        self._update_device_row(device)
    
    def _on_battery_updated(self, device: QuestDevice):
        self._update_device_row(device)
    
    def _on_device_name_changed(self, device: QuestDevice):
        print(f"Device name change event received for {device.get_id()}")
        device_id = device.get_id()
        if device_id in self.device_rows:
            print(f"Recreating row for {device_id} with new name: {device.get_display_name()}")
            row_tag = self.device_rows[device_id]
            dpg.delete_item(row_tag)
            del self.device_rows[device_id]
            self._add_device_row(device)
            
            if self.selected_device_id == device_id:
                self._on_device_selected(device_id)
        else:
            print(f"Device {device_id} not found in device_rows")
    
    def _on_server_stopped(self, data=None):
        for row_tag in list(self.device_rows.values()):
            dpg.delete_item(row_tag)
        self.device_rows.clear()
        self.selected_device_id = None
    
    def _add_device_row(self, device: QuestDevice):
        device_id = device.get_id()
        if device_id in self.device_rows:
            return
        
        row_tag = dpg.generate_uuid()
        self.device_rows[device_id] = row_tag
        
        with dpg.table_row(parent=self.table_tag, tag=row_tag):
            # Cell 1: Device name (clickable)
            device_tag = dpg.add_selectable(
                label=device.get_display_name(),
                callback=lambda: self._on_device_selected(device_id),
                span_columns=True
            )
            
            # Cell 2: IP address
            ip_tag = dpg.add_text(device.device_info.ip if device.device_info else "Unknown")
            
            # Cell 3: Battery status
            battery_tag = dpg.add_text(self._get_battery_text(device))
            
            # Store tags for updates
            dpg.set_item_user_data(row_tag, {
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
            # Update battery
            dpg.set_value(tags['battery'], self._get_battery_text(device))
            
            # Update battery color based on level
            if device.battery_info:
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
        
        charging = "⚡" if device.battery_info.is_charging else ""
        return f"{device.battery_info.headset_level}%{charging}"
    
    def _on_device_selected(self, device_id: str):
        self.selected_device_id = device_id
        device = self.server.get_device_by_id(device_id)
        if device:
            # Update device details panel
            event_bus.emit(EventType.DEVICE_UPDATED, device)
    
    def get_selected_device(self) -> Optional[QuestDevice]:
        if self.selected_device_id:
            return self.server.get_device_by_id(self.selected_device_id)
        return None
    
    def get_selected_devices(self) -> list[QuestDevice]:
        if self.selected_device_id:
            device = self.server.get_device_by_id(self.selected_device_id)
            return [device] if device else []
        return []