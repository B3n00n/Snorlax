import customtkinter as ctk
from typing import Optional
from datetime import datetime

from core.device import QuestDevice
from core.models import CommandResult
from gui.styles import *


class DeviceDetailWidget(ctk.CTkFrame):    
    def __init__(self, parent):
        super().__init__(parent)
        
        self.device: Optional[QuestDevice] = None
        self._create_widgets()
        self.clear()
    
    def _create_widgets(self) -> None:
        # Header
        self.header_label = ctk.CTkLabel(
            self,
            text="Device Details",
            font=FONT_HEADING
        )
        self.header_label.pack(pady=PADDING_LARGE)
        
        # Info frame
        self.info_frame = ctk.CTkFrame(self)
        self.info_frame.pack(fill="both", expand=True, padx=PADDING_LARGE, pady=PADDING_MEDIUM)
        
        # Device info section
        self._create_info_section("Device Information", [
            ("device_model", "Model"),
            ("device_serial", "Serial Number"),
            ("device_ip", "IP Address"),
            ("device_connected", "Connected At"),
            ("device_last_seen", "Last Seen")
        ])
        
        # Separator
        separator = ctk.CTkFrame(self.info_frame, height=2)
        separator.pack(fill="x", pady=PADDING_MEDIUM)
        
        # Battery info section
        self._create_info_section("Battery Status", [
            ("battery_level", "Battery Level"),
            ("battery_charging", "Charging Status"),
            ("battery_updated", "Last Updated")
        ])
        
        # Separator
        separator2 = ctk.CTkFrame(self.info_frame, height=2)
        separator2.pack(fill="x", pady=PADDING_MEDIUM)
        
        # Last response section
        response_label = ctk.CTkLabel(
            self.info_frame,
            text="Last Response",
            font=FONT_SUBHEADING
        )
        response_label.pack(anchor="w", pady=(PADDING_MEDIUM, PADDING_SMALL))
        
        self.response_text = ctk.CTkTextbox(
            self.info_frame,
            height=100,
            font=FONT_SMALL
        )
        self.response_text.pack(fill="both", expand=True, pady=PADDING_SMALL)
    
    def _create_info_section(self, title: str, fields: list) -> None:
        # Section title
        title_label = ctk.CTkLabel(
            self.info_frame,
            text=title,
            font=FONT_SUBHEADING
        )
        title_label.pack(anchor="w", pady=(PADDING_MEDIUM, PADDING_SMALL))
        
        # Fields
        for field_name, label_text in fields:
            frame = ctk.CTkFrame(self.info_frame, fg_color="transparent")
            frame.pack(fill="x", pady=2)
            
            label = ctk.CTkLabel(
                frame,
                text=f"{label_text}:",
                font=FONT_NORMAL,
                width=150,
                anchor="w"
            )
            label.pack(side="left", padx=(PADDING_MEDIUM, PADDING_SMALL))
            
            value_label = ctk.CTkLabel(
                frame,
                text="N/A",
                font=FONT_NORMAL,
                anchor="w"
            )
            value_label.pack(side="left", fill="x", expand=True)
            
            # Store reference
            setattr(self, f"{field_name}_value", value_label)
    
    def set_device(self, device: QuestDevice) -> None:
        self.device = device
        self._update_display()
    
    def clear(self) -> None:
        self.device = None
        
        # Clear all value labels
        for attr_name in dir(self):
            if attr_name.endswith("_value"):
                label = getattr(self, attr_name)
                if isinstance(label, ctk.CTkLabel):
                    label.configure(text="N/A")
        
        # Clear response text
        self.response_text.delete("1.0", "end")
        self.response_text.insert("1.0", "No responses yet")
    
    def _update_display(self) -> None:
        if not self.device:
            return
        
        # Update device info
        if self.device.device_info:
            info = self.device.device_info
            self.device_model_value.configure(text=info.model)
            self.device_serial_value.configure(text=info.serial)
            self.device_ip_value.configure(text=info.ip)
            self.device_connected_value.configure(
                text=info.connected_at.strftime("%Y-%m-%d %H:%M:%S")
            )
            self.device_last_seen_value.configure(
                text=info.last_seen.strftime("%Y-%m-%d %H:%M:%S")
            )
        
        # Update battery info
        if self.device.battery_info:
            battery = self.device.battery_info
            self.battery_level_value.configure(
                text=f"{battery.headset_level}%",
                text_color=STATUS_LOW_BATTERY if battery.headset_level < 20 else "white"
            )
            self.battery_charging_value.configure(
                text="Charging" if battery.is_charging else "On Battery",
                text_color=STATUS_CHARGING if battery.is_charging else "white"
            )
            self.battery_updated_value.configure(
                text=battery.last_updated.strftime("%Y-%m-%d %H:%M:%S")
            )
        
        # Update last response
        if self.device.last_response:
            self.update_response(self.device.last_response)
    
    def update_response(self, result: CommandResult) -> None:
        timestamp = result.timestamp.strftime("%H:%M:%S")
        status = "SUCCESS" if result.success else "FAILED"
        
        # Clear and update response text
        self.response_text.delete("1.0", "end")
        self.response_text.insert("1.0", f"[{timestamp}] {status}\n{result.message}")
        
        # Scroll to end
        self.response_text.see("end")