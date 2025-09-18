import dearpygui.dearpygui as dpg
from typing import Optional

from core.device import QuestDevice
from utils.event_bus import event_bus, EventType


class DeviceDetailsPanel:
    def __init__(self, parent_tag: str):
        self.parent_tag = parent_tag
        self.current_device: Optional[QuestDevice] = None
        self.detail_tags = {}
        
        self._setup_ui()
        self._subscribe_events()
    
    def _setup_ui(self):
        with dpg.group(parent=self.parent_tag):
            dpg.add_text("Device Details")
            dpg.add_separator()
            
            with dpg.group():
                self.detail_tags['name'] = dpg.add_text("Device: N/A")
                self.detail_tags['model'] = dpg.add_text("Model: N/A")
                self.detail_tags['serial'] = dpg.add_text("Serial: N/A")
                self.detail_tags['ip'] = dpg.add_text("IP Address: N/A")
                self.detail_tags['connected_at'] = dpg.add_text("Connected: N/A")
                self.detail_tags['last_seen'] = dpg.add_text("Last Seen: N/A")
            
            dpg.add_spacer(height=10)
            
            dpg.add_text("Battery Status")
            dpg.add_separator()
            
            with dpg.group():
                self.detail_tags['battery_level'] = dpg.add_text("Level: N/A")
                self.detail_tags['battery_charging'] = dpg.add_text("Charging: N/A")
                self.detail_tags['battery_updated'] = dpg.add_text("Updated: N/A")
                
                self.detail_tags['battery_progress'] = dpg.add_progress_bar(
                    default_value=0.0,
                    width=-1
                )
            
            dpg.add_spacer(height=10)
            
            with dpg.group(horizontal=True):
                dpg.add_text("Recent Commands")
                dpg.add_spacer(width=265)
                dpg.add_button(
                    label="Clear",
                    callback=self._clear_recent_commands,
                    small=True
                )
            dpg.add_separator()

            self.detail_tags['command_history'] = dpg.generate_uuid()
            dpg.add_child_window(
                tag=self.detail_tags['command_history'],
                height=-1,
                border=True
            )
    
    def _subscribe_events(self):
        event_bus.subscribe(EventType.DEVICE_UPDATED, self._on_device_updated)
        event_bus.subscribe(EventType.BATTERY_UPDATED, self._on_battery_updated)
        event_bus.subscribe(EventType.COMMAND_EXECUTED, self._on_command_executed)
        event_bus.subscribe(EventType.DEVICE_NAME_CHANGED, self._on_device_name_changed)
    
    def _on_device_updated(self, device: QuestDevice):
        if device and (not self.current_device or device.get_id() == self.current_device.get_id()):
            self.current_device = device
            self._update_display()
    
    def _on_battery_updated(self, device: QuestDevice):
        if device and self.current_device and device.get_id() == self.current_device.get_id():
            self._update_battery_display()
    
    def _on_command_executed(self, data: dict):
        device = data.get('device')
        if device and self.current_device and device.get_id() == self.current_device.get_id():
            self._add_command_to_history(data.get('success'), data.get('message'))
    
    def _on_device_name_changed(self, data: dict):
        device_id = data.get('device_id')
        if device_id and self.current_device and device_id == self.current_device.get_id():
            self.current_device.invalidate_name_cache()
            dpg.set_value(self.detail_tags['name'], f"Device: {self.current_device.get_display_name()}")
    
    def _update_display(self):
        if not self.current_device:
            return
        
        dpg.set_value(self.detail_tags['name'], f"Device: {self.current_device.get_display_name()}")
        
        if self.current_device.device_info:
            info = self.current_device.device_info
            dpg.set_value(self.detail_tags['model'], f"Model: {info.model}")
            dpg.set_value(self.detail_tags['serial'], f"Serial: {info.serial}")
            dpg.set_value(self.detail_tags['ip'], f"IP Address: {info.ip}")
            dpg.set_value(self.detail_tags['connected_at'], 
                         f"Connected: {info.connected_at.strftime('%Y-%m-%d %H:%M:%S')}")
            dpg.set_value(self.detail_tags['last_seen'], 
                         f"Last Seen: {info.last_seen.strftime('%Y-%m-%d %H:%M:%S')}")
        
        self._update_battery_display()
        self._update_command_history()
    
    def _update_battery_display(self):
        if not self.current_device or not self.current_device.battery_info:
            return
        
        battery = self.current_device.battery_info
        
        battery_updates = [
            ('battery_level', f"Level: {battery.headset_level}%"),
            ('battery_charging', f"Charging: {'Yes' if battery.is_charging else 'No'}"),
            ('battery_updated', f"Updated: {battery.last_updated.strftime('%H:%M:%S')}")
        ]
        
        for key, value in battery_updates:
            if key in self.detail_tags and dpg.does_item_exist(self.detail_tags[key]):
                dpg.set_value(self.detail_tags[key], value)
        
        if 'battery_progress' in self.detail_tags and dpg.does_item_exist(self.detail_tags['battery_progress']):
            dpg.set_value(self.detail_tags['battery_progress'], battery.headset_level / 100.0)
            
            if battery.headset_level < 20:
                color = (250, 100, 100, 255)  # Red
            elif battery.headset_level < 50:
                color = (250, 200, 100, 255)  # Yellow
            else:
                color = (100, 250, 100, 255)  # Green
            
            dpg.configure_item(self.detail_tags['battery_progress'], overlay=f"{battery.headset_level}%")
    
    def _update_command_history(self):
        if not self.current_device:
            return
        
        dpg.delete_item(self.detail_tags['command_history'], children_only=True)
        
        for cmd in self.current_device.command_history[-10:]:  # Show last 10
            self._add_command_to_history(cmd.success, cmd.message, False)
    
    def _add_command_to_history(self, success: bool, message: str, prepend: bool = True):
        color = (100, 250, 100) if success else (250, 100, 100)
        status = "[OK]" if success else "[FAIL]"
        
        with dpg.group(parent=self.detail_tags['command_history'], horizontal=True):
            dpg.add_text(status, color=color)
            dpg.add_text(message, wrap=300)
        
        if prepend:
            dpg.set_y_scroll(self.detail_tags['command_history'], -1.0)

    def _clear_recent_commands(self):
        if self.detail_tags.get('command_history') and dpg.does_item_exist(self.detail_tags['command_history']):
            dpg.delete_item(self.detail_tags['command_history'], children_only=True)
        
        if self.current_device:
            self.current_device.command_history.clear()