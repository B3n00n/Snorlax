import customtkinter as ctk
import threading
import os
from typing import Optional

from config import WINDOW_TITLE, ADB_PATH
from .themes import apply_theme, get_fonts, get_colors
from .components import StatusFrame, LogFrame, ProgressFrame, AdvancedDialog
from ..core.setup_manager import SetupManager
from ..utils.logger import logger, LogLevel


class MainWindow:
    def __init__(self):
        apply_theme()
        self.root = ctk.CTk()
        self.root.title(WINDOW_TITLE)
        self.root.geometry("600x650")
        self.root.minsize(500, 400)
        
        self.root.grid_rowconfigure(2, weight=1)
        self.root.grid_columnconfigure(0, weight=1)
        
        self.fonts = get_fonts()
        self.colors = get_colors()
        
        self.setup_manager = SetupManager()
        self.setup_thread: Optional[threading.Thread] = None
        self.adb_path = ADB_PATH
        
        self.create_widgets()
        self.setup_logger()
        self.refresh_status()
        
        icon_path = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'assets', 'icon.ico'))
        if os.path.exists(icon_path):
            self.root.iconbitmap(icon_path)
        
    def create_widgets(self):
        row = 0
        
        header = ctk.CTkLabel(
            self.root, 
            text="Snorlax Setup Tool",
            font=self.fonts['heading']
        )
        header.grid(row=row, column=0, pady=(20, 5), sticky="ew")
        row += 1
        
        self.status_frame = StatusFrame(self.root)
        self.status_frame.grid(row=row, column=0, padx=20, pady=10, sticky="ew")
        self.status_frame.refresh_btn.configure(command=self.refresh_status)
        row += 1
        
        self.log_frame = LogFrame(self.root)
        self.log_frame.grid(row=row, column=0, padx=20, pady=5, sticky="nsew")
        row += 1
        
        self.progress_frame = ProgressFrame(self.root)
        self.progress_frame.grid(row=row, column=0, padx=20, pady=5, sticky="ew")
        row += 1
        
        button_frame = ctk.CTkFrame(self.root)
        button_frame.grid(row=row, column=0, padx=20, pady=(5, 20), sticky="ew")
        
        button_frame.grid_columnconfigure(1, weight=1)
        
        left_frame = ctk.CTkFrame(button_frame, fg_color="transparent")
        left_frame.grid(row=0, column=0, sticky="w")
        
        self.start_btn = ctk.CTkButton(
            left_frame, text="Start Setup", width=120,
            font=self.fonts['button'],
            fg_color=self.colors['success'],
            hover_color="#5cb85c",
            text_color="white",
            command=self.start_setup
        )
        self.start_btn.pack(side="left", padx=(0, 5))

        self.grant_perms_btn = ctk.CTkButton(
            left_frame, text="Grant Permissions", width=140,
            font=self.fonts['button'],
            fg_color="#FFA500",
            hover_color="#FF8C00",
            text_color="white",
            command=self.grant_permissions_only
        )
        self.grant_perms_btn.pack(side="left", padx=(0, 5))

        self.cancel_btn = ctk.CTkButton(
            left_frame, text="Cancel", width=120,
            font=self.fonts['button'],
            fg_color=self.colors['error'],
            hover_color="#ef5350",
            text_color="white",
            command=self.cancel_setup,
            state="disabled"
        )
        self.cancel_btn.pack(side="left")
        
        self.advanced_btn = ctk.CTkButton(
            button_frame, text="Advanced", width=100,
            font=self.fonts['button'],
            text_color="white",
            command=self.show_advanced_settings
        )
        self.advanced_btn.grid(row=0, column=2, sticky="e")
        
    def setup_logger(self):
        logger.set_gui_callback(self.log_callback)
        
    def log_callback(self, message: str, level: LogLevel, timestamp: str):
        self.root.after(0, lambda: self.log_frame.add_log(message, level, timestamp))
        
    def refresh_status(self):
        try:
            if self.adb_path != self.setup_manager.adb.adb_path:
                self.setup_manager = SetupManager(self.adb_path)

            status = self.setup_manager.device.get_device_status()
            self.status_frame.update_status(status)

            if status['connected'] and not status['is_device_owner']:
                self.start_btn.configure(state="normal")
            else:
                self.start_btn.configure(state="disabled")

            # Enable Grant Permissions button if connected and package is installed
            if status['connected'] and status['package_installed']:
                self.grant_perms_btn.configure(state="normal")
            else:
                self.grant_perms_btn.configure(state="disabled")

        except Exception as e:
            logger.error(f"Failed to refresh status: {str(e)}")
            
    def setup_progress_callback(self, operation: str, progress: float):
        if operation == "download":
            text = "Downloading APK"
        elif operation == "install":
            text = "Installing APK"
        else:
            text = "Processing"
            
        self.root.after(0, lambda: self.progress_frame.update_progress(text, progress))
        
    def run_setup(self):
        self.setup_manager.set_progress_callback(self.setup_progress_callback)
        success = self.setup_manager.run_setup(skip_quest_check=False)

        self.root.after(0, lambda: self.setup_complete(success))

    def run_grant_permissions(self):
        success = self.setup_manager.grant_permissions_only()

        self.root.after(0, lambda: self.grant_permissions_complete(success))

    def start_setup(self):
        self.start_btn.configure(state="disabled")
        self.grant_perms_btn.configure(state="disabled")
        self.cancel_btn.configure(state="normal")
        self.status_frame.refresh_btn.configure(state="disabled")
        self.advanced_btn.configure(state="disabled")

        self.progress_frame.update_progress("Starting setup...", 0)

        self.setup_thread = threading.Thread(target=self.run_setup, daemon=True)
        self.setup_thread.start()

    def grant_permissions_only(self):
        self.start_btn.configure(state="disabled")
        self.grant_perms_btn.configure(state="disabled")
        self.cancel_btn.configure(state="normal")
        self.status_frame.refresh_btn.configure(state="disabled")
        self.advanced_btn.configure(state="disabled")

        self.progress_frame.update_progress("Granting permissions...", 0)

        self.setup_thread = threading.Thread(target=self.run_grant_permissions, daemon=True)
        self.setup_thread.start()
        
    def cancel_setup(self):
        if self.setup_manager:
            self.setup_manager.cancel_operation()
            
        self.cancel_btn.configure(state="disabled")
        self.progress_frame.update_progress("Cancelling...", 0)
        
    def setup_complete(self, success: bool):
        self.start_btn.configure(state="normal")
        self.cancel_btn.configure(state="disabled")
        self.status_frame.refresh_btn.configure(state="normal")
        self.advanced_btn.configure(state="normal")

        if success:
            self.progress_frame.update_progress("Setup completed successfully!", 100)
        else:
            self.progress_frame.update_progress("Setup failed", 0)

        self.refresh_status()

    def grant_permissions_complete(self, success: bool):
        self.start_btn.configure(state="normal")
        self.grant_perms_btn.configure(state="normal")
        self.cancel_btn.configure(state="disabled")
        self.status_frame.refresh_btn.configure(state="normal")
        self.advanced_btn.configure(state="normal")

        if success:
            self.progress_frame.update_progress("Permissions granted successfully!", 100)
        else:
            self.progress_frame.update_progress("Permission granting failed", 0)

        self.refresh_status()
        
    def show_advanced_settings(self):
        dialog = AdvancedDialog(self.root, self.adb_path)
        self.root.wait_window(dialog)
        
        if dialog.result:
            self.adb_path = dialog.result['adb_path']
            logger.info(f"ADB path updated to: {self.adb_path}")
            self.refresh_status()
            
    def run(self):
        """Run the main window"""
        self.root.mainloop()