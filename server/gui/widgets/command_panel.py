import customtkinter as ctk
from typing import Optional, Callable, Dict, List
import threading

from core.device import QuestDevice
from core.enums import MessageType, PowerAction
from config.settings import PROTECTED_PACKAGES, COMMAND_TIMEOUT
from gui.styles import *
from gui.widgets.dialogs import (
    show_error, show_warning, show_info, 
    confirm_dialog, input_dialog
)


class CommandButton(ctk.CTkButton):    
    def __init__(self, parent, text: str, command: Callable, **kwargs):
        super().__init__(
            parent,
            text=text,
            command=command,
            height=BUTTON_HEIGHT,
            font=FONT_NORMAL,
            **kwargs
        )


class CommandPanel(ctk.CTkFrame):    
    def __init__(self, parent, on_command_execute: Optional[Callable] = None):
        super().__init__(parent)
        
        self.device: Optional[QuestDevice] = None
        self.on_command_execute = on_command_execute
        self.selected_devices: List[QuestDevice] = []
        
        self._create_widgets()
        self._update_state()
    
    def _create_widgets(self) -> None:
        # Target selection
        self.target_frame = ctk.CTkFrame(self)
        self.target_frame.pack(fill="x", padx=PADDING_LARGE, pady=PADDING_MEDIUM)
        
        target_label = ctk.CTkLabel(
            self.target_frame,
            text="Target:",
            font=FONT_SUBHEADING
        )
        target_label.pack(side="left", padx=(0, PADDING_MEDIUM))
        
        self.target_var = ctk.StringVar(value="selected")
        self.target_selected = ctk.CTkRadioButton(
            self.target_frame,
            text="Selected Device",
            variable=self.target_var,
            value="selected",
            font=FONT_NORMAL
        )
        self.target_selected.pack(side="left", padx=PADDING_SMALL)
        
        self.target_all = ctk.CTkRadioButton(
            self.target_frame,
            text="All Devices",
            variable=self.target_var,
            value="all",
            font=FONT_NORMAL
        )
        self.target_all.pack(side="left", padx=PADDING_SMALL)
        
        # Command sections
        self.sections_frame = ctk.CTkScrollableFrame(self)
        self.sections_frame.pack(fill="both", expand=True, padx=PADDING_LARGE, pady=PADDING_MEDIUM)
        
        # Basic commands
        self._create_section("Basic Commands", [
            ("Get Battery Status", self._cmd_battery_status),
            ("Ping Device", self._cmd_ping),
            ("Get Device Info", self._cmd_device_info),
            ("Get Installed Apps", self._cmd_installed_apps)
        ])
        
        # App management
        self._create_section("App Management", [
            ("Launch App", self._cmd_launch_app),
            ("Install APK", self._cmd_install_apk),
            ("Uninstall App", self._cmd_uninstall_app)
        ])
        
        # System commands
        self._create_section("System Commands", [
            ("Execute Shell Command", self._cmd_shell),
            ("Shutdown Device", self._cmd_shutdown),
            ("Restart Device", self._cmd_restart)
        ])
    
    def _create_section(self, title: str, commands: List[tuple]) -> None:
        # Section frame
        section = ctk.CTkFrame(self.sections_frame)
        section.pack(fill="x", pady=PADDING_MEDIUM)
        
        # Title
        title_label = ctk.CTkLabel(
            section,
            text=title,
            font=FONT_SUBHEADING
        )
        title_label.pack(anchor="w", padx=PADDING_MEDIUM, pady=(PADDING_MEDIUM, PADDING_SMALL))
        
        # Commands
        for text, command in commands:
            btn = CommandButton(section, text, command)
            btn.pack(fill="x", padx=PADDING_MEDIUM, pady=PADDING_SMALL)
    
    def set_device(self, device: Optional[QuestDevice]) -> None:
        self.device = device
        self._update_state()
    
    def _update_state(self) -> None:
        enabled = self.device is not None
        
        # Update all buttons
        for widget in self.sections_frame.winfo_children():
            if isinstance(widget, ctk.CTkFrame):
                for btn in widget.winfo_children():
                    if isinstance(btn, CommandButton):
                        btn.configure(state="normal" if enabled else "disabled")
        
        # Update target selection
        self.target_selected.configure(state="normal" if enabled else "disabled")
        if not enabled:
            self.target_var.set("all")
    
    def _get_target_devices(self) -> List[QuestDevice]:
        if self.target_var.get() == "selected" and self.device:
            return [self.device]
        else:
            # Get all devices from parent app
            app = self.winfo_toplevel()
            if hasattr(app, 'server') and app.server:
                return app.server.get_all_devices()
        return []
    
    def _execute_on_devices(self, devices: List[QuestDevice], message_type: MessageType, 
                           command: str = "", success_msg: str = "Command sent") -> None:
        if not devices:
            show_error("No Devices", "No devices available for this command")
            return
        
        def execute():
            for device in devices:
                device.send_command(message_type, command)
        
        # Execute in thread
        thread = threading.Thread(target=execute, daemon=True)
        thread.start()
        
        # Notify
        device_names = ", ".join(d.get_display_name() for d in devices[:3])
        if len(devices) > 3:
            device_names += f" and {len(devices) - 3} more"
        
        show_info("Command Sent", f"{success_msg} to: {device_names}")
        
        if self.on_command_execute:
            self.on_command_execute(message_type.name, {"command": command, "devices": len(devices)})
    
    # Command handlers
    def _cmd_battery_status(self) -> None:
        devices = self._get_target_devices()
        self._execute_on_devices(devices, MessageType.REQUEST_BATTERY, 
                               success_msg="Battery status requested")
    
    def _cmd_ping(self) -> None:
        devices = self._get_target_devices()
        self._execute_on_devices(devices, MessageType.PING, 
                               success_msg="Ping sent")
    
    def _cmd_device_info(self) -> None:
        devices = self._get_target_devices()
        self._execute_on_devices(devices, MessageType.GET_DEVICE_INFO,
                               success_msg="Device info requested")
    
    def _cmd_installed_apps(self) -> None:
        devices = self._get_target_devices()
        self._execute_on_devices(devices, MessageType.GET_INSTALLED_APPS,
                               success_msg="App list requested")
    
    def _cmd_launch_app(self) -> None:
        package = input_dialog(
            "Launch App",
            "Enter package name to launch:",
            "e.g., com.oculus.vrshell"
        )
        
        if package:
            devices = self._get_target_devices()
            self._execute_on_devices(devices, MessageType.LAUNCH_APP, package,
                                   success_msg=f"Launching {package}")
    
    def _cmd_install_apk(self) -> None:
        url = input_dialog(
            "Install APK",
            "Enter APK URL:",
            "https://example.com/app.apk"
        )
        
        if url:
            if not url.startswith(('http://', 'https://')):
                show_error("Invalid URL", "URL must start with http:// or https://")
                return
            
            devices = self._get_target_devices()
            self._execute_on_devices(devices, MessageType.DOWNLOAD_AND_INSTALL_APK, url,
                                   success_msg="APK download started")
    
    def _cmd_uninstall_app(self) -> None:
        package = input_dialog(
            "Uninstall App",
            "Enter package name to uninstall:",
            "e.g., com.example.app"
        )
        
        if package:
            if package in PROTECTED_PACKAGES:
                show_error("Protected App", f"Cannot uninstall {package}")
                return
            
            if confirm_dialog("Confirm Uninstall", 
                            f"Are you sure you want to uninstall {package}?"):
                devices = self._get_target_devices()
                self._execute_on_devices(devices, MessageType.UNINSTALL_APP, package,
                                       success_msg=f"Uninstalling {package}")
    
    def _cmd_shell(self) -> None:
        command = input_dialog(
            "Shell Command",
            "Enter shell command to execute:",
            "e.g., ls -la /sdcard/"
        )
        
        if command:
            show_warning("Shell Command", 
                        "Be careful with shell commands!\nThey can modify system files.")
            
            devices = self._get_target_devices()
            self._execute_on_devices(devices, MessageType.EXECUTE_SHELL, command,
                                   success_msg=f"Executing: {command}")
    
    def _cmd_shutdown(self) -> None:
        devices = self._get_target_devices()
        device_count = len(devices)
        
        if confirm_dialog("Confirm Shutdown", 
                         f"Are you sure you want to shutdown {device_count} device(s)?"):
            for device in devices:
                device.send_shutdown_command("shutdown")
            
            show_info("Shutdown", f"Shutdown command sent to {device_count} device(s)")
            
            if self.on_command_execute:
                self.on_command_execute("SHUTDOWN", {"devices": device_count})
    
    def _cmd_restart(self) -> None:
        devices = self._get_target_devices()
        device_count = len(devices)
        
        if confirm_dialog("Confirm Restart", 
                         f"Are you sure you want to restart {device_count} device(s)?"):
            for device in devices:
                device.send_shutdown_command("restart")
            
            show_info("Restart", f"Restart command sent to {device_count} device(s)")
            
            if self.on_command_execute:
                self.on_command_execute("RESTART", {"devices": device_count})