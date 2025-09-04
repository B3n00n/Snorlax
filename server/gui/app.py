import customtkinter as ctk
from typing import Optional
import threading

from config.settings import (
    WINDOW_WIDTH, WINDOW_HEIGHT, 
    MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT,
    DEFAULT_HOST, DEFAULT_PORT
)
from gui.styles import *
from gui.widgets.device_list import DeviceListWidget
from gui.widgets.device_detail import DeviceDetailWidget
from gui.widgets.command_panel import CommandPanel
from gui.widgets.dialogs import ServerConfigDialog, show_error, show_info
from network.server import QuestControlServer
from core.device import QuestDevice
from utils.logger import get_logger


class QuestControlApp(ctk.CTk):    
    def __init__(self):
        super().__init__()
        
        self.logger = get_logger(__name__)
        self.server: Optional[QuestControlServer] = None
        self.selected_device: Optional[QuestDevice] = None
        
        self._setup_window()
        self._create_widgets()
        self._layout_widgets()
        
        # Start with server config dialog
        self.after(100, self._show_server_config)
    
    def _setup_window(self) -> None:
        self.title("Quest Control Center")
        self.geometry(f"{WINDOW_WIDTH}x{WINDOW_HEIGHT}")
        self.minsize(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT)
        
        # Set theme
        ctk.set_appearance_mode("dark")
        ctk.set_default_color_theme("blue")
        
        # Handle window close
        self.protocol("WM_DELETE_WINDOW", self._on_closing)
    
    def _create_widgets(self) -> None:
        # Top toolbar
        self.toolbar = ctk.CTkFrame(self, height=60)
        
        # Server status
        self.server_status_label = ctk.CTkLabel(
            self.toolbar,
            text="Server: Disconnected",
            font=FONT_SUBHEADING,
            text_color=COLOR_ERROR
        )
        
        # Control buttons
        self.start_button = ctk.CTkButton(
            self.toolbar,
            text="Start Server",
            command=self._toggle_server,
            height=BUTTON_HEIGHT,
            font=FONT_NORMAL
        )
        
        self.refresh_button = ctk.CTkButton(
            self.toolbar,
            text="Refresh Devices",
            command=self._refresh_devices,
            height=BUTTON_HEIGHT,
            font=FONT_NORMAL,
            state="disabled"
        )
        
        # Main content
        self.main_frame = ctk.CTkFrame(self)
        
        # Left panel - Device list
        self.device_list = DeviceListWidget(
            self.main_frame,
            on_device_selected=self._on_device_selected
        )
        
        # Right panel - Tabbed interface
        self.right_panel = ctk.CTkTabview(self.main_frame)
        
        # Device detail tab
        self.detail_tab = self.right_panel.add("Device Details")
        self.device_detail = DeviceDetailWidget(self.detail_tab)
        
        # Commands tab
        self.commands_tab = self.right_panel.add("Commands")
        self.command_panel = CommandPanel(
            self.commands_tab,
            on_command_execute=self._execute_command
        )
        
        # Status bar
        self.status_bar = ctk.CTkFrame(self, height=30)
        self.status_label = ctk.CTkLabel(
            self.status_bar,
            text="Ready",
            font=FONT_SMALL
        )
    
    def _layout_widgets(self) -> None:
        # Toolbar
        self.toolbar.pack(fill="x", padx=PADDING_MEDIUM, pady=(PADDING_MEDIUM, 0))
        self.toolbar.pack_propagate(False)
        
        self.server_status_label.pack(side="left", padx=PADDING_LARGE)
        self.start_button.pack(side="left", padx=PADDING_SMALL)
        self.refresh_button.pack(side="left", padx=PADDING_SMALL)
        
        # Main content
        self.main_frame.pack(fill="both", expand=True, padx=PADDING_MEDIUM, pady=PADDING_MEDIUM)
        
        # Configure grid
        self.main_frame.grid_columnconfigure(0, weight=1)
        self.main_frame.grid_columnconfigure(1, weight=2)
        self.main_frame.grid_rowconfigure(0, weight=1)
        
        self.device_list.grid(row=0, column=0, sticky="nsew", padx=(0, PADDING_MEDIUM))
        self.right_panel.grid(row=0, column=1, sticky="nsew")
        
        # Status bar
        self.status_bar.pack(fill="x", padx=PADDING_MEDIUM, pady=(0, PADDING_MEDIUM))
        self.status_bar.pack_propagate(False)
        self.status_label.pack(side="left", padx=PADDING_MEDIUM)
    
    def _show_server_config(self) -> None:
        dialog = ServerConfigDialog(self, DEFAULT_HOST, DEFAULT_PORT)
        self.wait_window(dialog)
        
        if dialog.result:
            host, port = dialog.result
            self._start_server(host, port)
    
    def _start_server(self, host: str, port: int) -> None:
        if self.server and self.server.running:
            return
        
        self.server = QuestControlServer(host, port)
        
        # Set callbacks
        self.server.set_callbacks(
            on_device_connected=self._on_device_connected,
            on_device_disconnected=self._on_device_disconnected,
            on_server_started=self._on_server_started,
            on_server_stopped=self._on_server_stopped,
            on_error=self._on_server_error
        )
        
        # Start in thread
        thread = threading.Thread(target=self.server.start, daemon=True)
        thread.start()
    
    def _toggle_server(self) -> None:
        if self.server and self.server.running:
            self.server.stop()
        else:
            self._show_server_config()
    
    def _on_server_started(self) -> None:
        self.after(0, self._update_server_ui, True)
    
    def _on_server_stopped(self) -> None:
        self.after(0, self._update_server_ui, False)
    
    def _update_server_ui(self, running: bool) -> None:
        if running:
            self.server_status_label.configure(
                text=f"Server: Running ({self.server.host}:{self.server.port})",
                text_color=COLOR_SUCCESS
            )
            self.start_button.configure(text="Stop Server")
            self.refresh_button.configure(state="normal")
            self._set_status("Server started")
        else:
            self.server_status_label.configure(
                text="Server: Disconnected",
                text_color=COLOR_ERROR
            )
            self.start_button.configure(text="Start Server")
            self.refresh_button.configure(state="disabled")
            self.device_list.clear()
            self._set_status("Server stopped")
    
    def _on_server_error(self, error: str) -> None:
        self.after(0, show_error, "Server Error", error)
    
    def _on_device_connected(self, device: QuestDevice) -> None:
        device.set_callbacks(
            on_info_updated=lambda info: self.after(0, self.device_list.update_device, device),
            on_battery_updated=lambda info: self.after(0, self.device_list.update_device, device),
            on_response_received=lambda result: self.after(0, self._on_command_response, device, result),
            on_disconnected=lambda: self.after(0, self._on_device_disconnected, device.get_id())
        )
        
        self.after(0, self.device_list.add_device, device)
        self.after(0, self._set_status, f"Device connected: {device.get_display_name()}")
    
    def _on_device_disconnected(self, device_id: str) -> None:
        self.device_list.remove_device(device_id)
        if self.selected_device and self.selected_device.get_id() == device_id:
            self.selected_device = None
            self.device_detail.clear()
        self._set_status(f"Device disconnected: {device_id}")
    
    def _on_device_selected(self, device: Optional[QuestDevice]) -> None:
        self.selected_device = device
        if device:
            self.device_detail.set_device(device)
            self.command_panel.set_device(device)
            self._set_status(f"Selected: {device.get_display_name()}")
        else:
            self.device_detail.clear()
            self.command_panel.set_device(None)
    
    def _refresh_devices(self) -> None:
        if self.server:
            devices = self.server.get_all_devices()
            self.device_list.refresh(devices)
            self._set_status(f"Found {len(devices)} device(s)")
    
    def _execute_command(self, command_type: str, params: dict) -> None:
        if not self.server or not self.server.running:
            show_error("Server Error", "Server is not running")
            return
        
        self._set_status(f"Executing: {command_type}")
    
    def _on_command_response(self, device: QuestDevice, result) -> None:
        self.device_list.update_device(device)
        if self.selected_device and self.selected_device.get_id() == device.get_id():
            self.device_detail.update_response(result)
        self._set_status(f"{device.get_display_name()}: {result}")
    
    def _set_status(self, message: str) -> None:
        self.status_label.configure(text=message)
    
    def _on_closing(self) -> None:
        if self.server and self.server.running:
            self.server.stop()
        self.destroy()


def run_app():
    app = QuestControlApp()
    app.mainloop()