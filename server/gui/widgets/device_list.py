import customtkinter as ctk
from typing import Dict, List, Optional, Callable
from datetime import datetime

from core.device import QuestDevice
from gui.styles import *


class DeviceListItem(ctk.CTkFrame):    
    def __init__(self, parent, device: QuestDevice, on_click: Callable):
        super().__init__(parent, height=LIST_ROW_HEIGHT, corner_radius=8)
        
        self.device = device
        self.on_click = on_click
        
        self._create_widgets()
        self._update_display()
        
        # Make entire frame clickable
        self.bind("<Button-1>", lambda e: on_click(device))
        for widget in self.winfo_children():
            widget.bind("<Button-1>", lambda e: on_click(device))
    
    def _create_widgets(self) -> None:
        self.info_frame = ctk.CTkFrame(self, fg_color="transparent")
        self.info_frame.pack(side="left", fill="both", expand=True, padx=PADDING_MEDIUM)
        
        self.name_label = ctk.CTkLabel(
            self.info_frame,
            text="",
            font=FONT_SUBHEADING,
            anchor="w"
        )
        self.name_label.pack(fill="x")
        
        self.detail_label = ctk.CTkLabel(
            self.info_frame,
            text="",
            font=FONT_SMALL,
            anchor="w",
            text_color="gray"
        )
        self.detail_label.pack(fill="x")
        
        # Status indicators
        self.status_frame = ctk.CTkFrame(self, fg_color="transparent")
        self.status_frame.pack(side="right", padx=PADDING_MEDIUM)
        
        self.battery_label = ctk.CTkLabel(
            self.status_frame,
            text="",
            font=FONT_SMALL
        )
        self.battery_label.pack()
        
        self.status_indicator = ctk.CTkLabel(
            self.status_frame,
            text="●",
            font=("Arial", 16),
            text_color=STATUS_CONNECTED
        )
        self.status_indicator.pack()
    
    def _update_display(self) -> None:
        self.name_label.configure(text=self.device.get_display_name())
        
        if self.device.device_info:
            detail = f"IP: {self.device.device_info.ip}"
            self.detail_label.configure(text=detail)
        
        if self.device.battery_info:
            battery = self.device.battery_info
            battery_text = f"{battery.headset_level}%"
            if battery.is_charging:
                battery_text += " ⚡"
            self.battery_label.configure(text=battery_text)
            
            if battery.headset_level < 20:
                self.battery_label.configure(text_color=STATUS_LOW_BATTERY)
            elif battery.is_charging:
                self.battery_label.configure(text_color=STATUS_CHARGING)
            else:
                self.battery_label.configure(text_color="white")
        
        if self.device.is_connected:
            self.status_indicator.configure(text_color=STATUS_CONNECTED)
        else:
            self.status_indicator.configure(text_color=STATUS_DISCONNECTED)
    
    def update(self) -> None:
        self._update_display()
    
    def set_selected(self, selected: bool) -> None:
        if selected:
            self.configure(fg_color=COLOR_PRIMARY)
        else:
            self.configure(fg_color=("gray20", "gray20"))


class DeviceListWidget(ctk.CTkScrollableFrame):    
    def __init__(self, parent, on_device_selected: Optional[Callable] = None):
        super().__init__(parent)
        
        self.on_device_selected = on_device_selected
        self.devices: Dict[str, DeviceListItem] = {}
        self.selected_device_id: Optional[str] = None
        
        self._create_widgets()
    
    def _create_widgets(self) -> None:
        self.header = ctk.CTkLabel(
            self,
            text="Connected Devices",
            font=FONT_HEADING,
            anchor="w"
        )
        self.header.pack(fill="x", padx=PADDING_MEDIUM, pady=PADDING_MEDIUM)
        
        self.empty_label = ctk.CTkLabel(
            self,
            text="No devices connected\nWaiting for Quest devices...",
            font=FONT_NORMAL,
            text_color="gray"
        )
    
    def add_device(self, device: QuestDevice) -> None:
        device_id = device.get_id()
        
        if device_id in self.devices:
            return
        
        if self.empty_label.winfo_viewable():
            self.empty_label.pack_forget()
        
        item = DeviceListItem(
            self,
            device,
            self._on_item_clicked
        )
        item.pack(fill="x", padx=PADDING_MEDIUM, pady=PADDING_SMALL)
        
        self.devices[device_id] = item
    
    def remove_device(self, device_id: str) -> None:
        if device_id not in self.devices:
            return
        
        item = self.devices[device_id]
        item.destroy()
        del self.devices[device_id]
        
        if not self.devices:
            self.empty_label.pack(fill="both", expand=True, pady=PADDING_LARGE)
        
        if self.selected_device_id == device_id:
            self.selected_device_id = None
            if self.on_device_selected:
                self.on_device_selected(None)
    
    def update_device(self, device: QuestDevice) -> None:
        device_id = device.get_id()
        if device_id in self.devices:
            self.devices[device_id].update()
    
    def refresh(self, devices: List[QuestDevice]) -> None:
        current_ids = {d.get_id() for d in devices}
        to_remove = [id for id in self.devices if id not in current_ids]
        for device_id in to_remove:
            self.remove_device(device_id)
        
        for device in devices:
            if device.get_id() in self.devices:
                self.update_device(device)
            else:
                self.add_device(device)
    
    def clear(self) -> None:
        for item in self.devices.values():
            item.destroy()
        self.devices.clear()
        self.selected_device_id = None
        self.empty_label.pack(fill="both", expand=True, pady=PADDING_LARGE)
    
    def _on_item_clicked(self, device: QuestDevice) -> None:
        device_id = device.get_id()
        
        if self.selected_device_id:
            if self.selected_device_id in self.devices:
                self.devices[self.selected_device_id].set_selected(False)
        
        self.selected_device_id = device_id
        self.devices[device_id].set_selected(True)
        
        if self.on_device_selected:
            self.on_device_selected(device)