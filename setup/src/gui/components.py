import customtkinter as ctk
from typing import Optional, Callable
import tkinter as tk

from .themes import get_fonts, get_colors, get_paddings
from ..utils.logger import LogLevel


class StatusFrame(ctk.CTkFrame):    
    def __init__(self, parent):
        super().__init__(parent)
        self.colors = get_colors()
        self.fonts = get_fonts()
        self.create_widgets()
        
    def create_widgets(self):
        """Create status widgets"""
        
        # Status fields
        self.status_labels = {}
        fields = [
            ("Connection:", "-CONNECTION-"),
            ("Device:", "-DEVICE-"),
            ("Package:", "-PACKAGE-"),
            ("Device Owner:", "-OWNER-")
        ]
        
        for i, (label, key) in enumerate(fields, start=1):
            lbl = ctk.CTkLabel(self, text=label, font=self.fonts['normal'])
            lbl.grid(row=i, column=0, sticky="w", padx=(20, 10), pady=2)
            
            status = ctk.CTkLabel(self, text="Unknown", font=self.fonts['normal'],
                                 text_color=self.colors['warning'])
            status.grid(row=i, column=1, sticky="w", pady=2)
            self.status_labels[key] = status
            
        # Refresh button
        self.refresh_btn = ctk.CTkButton(
            self, text="Refresh", width=100, height=30,
            font=self.fonts['button'],
            text_color="white"
        )
        self.refresh_btn.grid(row=1, column=2, padx=(20, 0), sticky="e")
        
    def update_status(self, status: dict):
        """Update status display"""
        colors = self.colors
        
        # Connection
        if status['connected']:
            self.status_labels['-CONNECTION-'].configure(
                text="Connected", text_color=colors['success']
            )
        else:
            self.status_labels['-CONNECTION-'].configure(
                text="Not Connected", text_color=colors['error']
            )
            
        # Device
        if status['connected']:
            device_text = f"{status['manufacturer']} {status['model']}"
            color = colors['success'] if status['is_quest'] else colors['warning']
            self.status_labels['-DEVICE-'].configure(text=device_text, text_color=color)
        else:
            self.status_labels['-DEVICE-'].configure(
                text="Unknown", text_color=colors['warning']
            )
            
        # Package
        if status['package_installed']:
            self.status_labels['-PACKAGE-'].configure(
                text="Installed", text_color=colors['success']
            )
        else:
            self.status_labels['-PACKAGE-'].configure(
                text="Not Installed", text_color=colors['warning']
            )
            
        # Owner
        if status['is_device_owner']:
            self.status_labels['-OWNER-'].configure(
                text="Set", text_color=colors['success']
            )
        else:
            self.status_labels['-OWNER-'].configure(
                text="Not Set", text_color=colors['warning']
            )


class LogFrame(ctk.CTkFrame):    
    def __init__(self, parent):
        super().__init__(parent)
        self.colors = get_colors()
        self.fonts = get_fonts()
        self.create_widgets()
        
    def create_widgets(self):
        self.text_widget = ctk.CTkTextbox(
            self, font=self.fonts['console'],
            height=200
        )
        self.text_widget.pack(fill="both", expand=True)
        
        self.text_widget._textbox.tag_config("SUCCESS", foreground=self.colors['success'])
        self.text_widget._textbox.tag_config("ERROR", foreground=self.colors['error'])
        self.text_widget._textbox.tag_config("WARNING", foreground=self.colors['warning'])
        self.text_widget._textbox.tag_config("INFO", foreground=self.colors['info'])
        
    def add_log(self, message: str, level: LogLevel, timestamp: str):
        """Add log entry with color"""
        formatted = f"[{timestamp}] {message}\n"
        tag = level.value
        
        self.text_widget._textbox.config(state="normal")
        self.text_widget._textbox.insert("end", formatted, tag)
        self.text_widget._textbox.see("end")
        self.text_widget._textbox.config(state="disabled")


class ProgressFrame(ctk.CTkFrame):
    
    def __init__(self, parent):
        super().__init__(parent)
        self.fonts = get_fonts()
        self.create_widgets()
        
    def create_widgets(self):
        self.status_label = ctk.CTkLabel(self, text="Ready", font=self.fonts['normal'])
        self.status_label.pack(anchor="w")
        
        self.progress_bar = ctk.CTkProgressBar(self)
        self.progress_bar.pack(fill="x", pady=(5, 0))
        self.progress_bar.set(0)
        
        self.percent_label = ctk.CTkLabel(self, text="0%", font=self.fonts['small'])
        self.percent_label.pack(anchor="e")
        
    def update_progress(self, text: str, percent: float):
        self.status_label.configure(text=text)
        self.progress_bar.set(percent / 100)
        self.percent_label.configure(text=f"{int(percent)}%")


class AdvancedDialog(ctk.CTkToplevel):    
    def __init__(self, parent, current_adb_path: str):
        super().__init__(parent)
        self.title("Advanced Settings")
        self.geometry("500x250")
        self.fonts = get_fonts()
        self.result = None
        self.current_adb_path = current_adb_path
        
        self.transient(parent)
        self.grab_set()
        
        self.create_widgets()
        
    def create_widgets(self):
        title = ctk.CTkLabel(self, text="Advanced Settings", font=self.fonts['subheading'])
        title.pack(pady=10)
        
        path_frame = ctk.CTkFrame(self)
        path_frame.pack(fill="x", padx=20, pady=10)
        
        ctk.CTkLabel(path_frame, text="ADB Path:", font=self.fonts['normal']).pack(anchor="w")
        
        self.path_entry = ctk.CTkEntry(path_frame, width=400)
        self.path_entry.pack(fill="x", pady=5)
        self.path_entry.insert(0, self.current_adb_path)
        
        browse_btn = ctk.CTkButton(
            path_frame, text="Browse", width=100,
            text_color="white",
            command=self.browse_file
        )
        browse_btn.pack(anchor="e", pady=5)
        
        button_frame = ctk.CTkFrame(self)
        button_frame.pack(fill="x", padx=20, pady=20)
        
        save_btn = ctk.CTkButton(
            button_frame, text="Save", width=100,
            text_color="white",
            command=self.save_settings
        )
        save_btn.pack(side="left", padx=5)
        
        cancel_btn = ctk.CTkButton(
            button_frame, text="Cancel", width=100,
            text_color="white",
            command=self.cancel
        )
        cancel_btn.pack(side="left", padx=5)
        
    def browse_file(self):
        from tkinter import filedialog
        filename = filedialog.askopenfilename(
            title="Select ADB executable",
            filetypes=[("Executable", "*.exe"), ("All files", "*.*")]
        )
        if filename:
            self.path_entry.delete(0, "end")
            self.path_entry.insert(0, filename)
            
    def save_settings(self):
        self.result = {
            'adb_path': self.path_entry.get()
        }
        self.destroy()
        
    def cancel(self):
        self.result = None
        self.destroy()