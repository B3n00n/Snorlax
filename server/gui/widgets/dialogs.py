import customtkinter as ctk
from typing import Optional, Tuple
import tkinter.messagebox as messagebox

from gui.styles import *


class ServerConfigDialog(ctk.CTkToplevel):    
    def __init__(self, parent, default_host: str, default_port: int):
        super().__init__(parent)
        
        self.result: Optional[Tuple[str, int]] = None
        
        # Configure window
        self.title("Server Configuration")
        self.geometry("400x250")
        self.resizable(False, False)
        
        # Make modal
        self.transient(parent)
        self.grab_set()
        
        # Center on parent
        self.update_idletasks()
        x = (self.winfo_screenwidth() // 2) - (self.winfo_width() // 2)
        y = (self.winfo_screenheight() // 2) - (self.winfo_height() // 2)
        self.geometry(f"+{x}+{y}")
        
        self._create_widgets(default_host, default_port)
        
        # Focus
        self.host_entry.focus()
    
    def _create_widgets(self, default_host: str, default_port: int) -> None:
        # Title
        title_label = ctk.CTkLabel(
            self,
            text="Configure Quest Control Server",
            font=FONT_HEADING
        )
        title_label.pack(pady=PADDING_LARGE)
        
        # Form frame
        form_frame = ctk.CTkFrame(self)
        form_frame.pack(fill="both", expand=True, padx=PADDING_LARGE, pady=PADDING_MEDIUM)
        
        # Host field
        host_label = ctk.CTkLabel(
            form_frame,
            text="Server Host:",
            font=FONT_NORMAL
        )
        host_label.grid(row=0, column=0, sticky="w", padx=PADDING_MEDIUM, pady=PADDING_SMALL)
        
        self.host_entry = ctk.CTkEntry(
            form_frame,
            placeholder_text="0.0.0.0",
            font=FONT_NORMAL,
            height=ENTRY_HEIGHT
        )
        self.host_entry.grid(row=0, column=1, sticky="ew", padx=PADDING_MEDIUM, pady=PADDING_SMALL)
        self.host_entry.insert(0, default_host)
        
        # Port field
        port_label = ctk.CTkLabel(
            form_frame,
            text="Server Port:",
            font=FONT_NORMAL
        )
        port_label.grid(row=1, column=0, sticky="w", padx=PADDING_MEDIUM, pady=PADDING_SMALL)
        
        self.port_entry = ctk.CTkEntry(
            form_frame,
            placeholder_text="8888",
            font=FONT_NORMAL,
            height=ENTRY_HEIGHT
        )
        self.port_entry.grid(row=1, column=1, sticky="ew", padx=PADDING_MEDIUM, pady=PADDING_SMALL)
        self.port_entry.insert(0, str(default_port))
        
        # Configure grid
        form_frame.grid_columnconfigure(1, weight=1)
        
        # Buttons
        button_frame = ctk.CTkFrame(self, fg_color="transparent")
        button_frame.pack(fill="x", padx=PADDING_LARGE, pady=PADDING_MEDIUM)
        
        cancel_button = ctk.CTkButton(
            button_frame,
            text="Cancel",
            command=self.destroy,
            height=BUTTON_HEIGHT,
            font=FONT_NORMAL,
            fg_color="gray30"
        )
        cancel_button.pack(side="right", padx=PADDING_SMALL)
        
        start_button = ctk.CTkButton(
            button_frame,
            text="Start Server",
            command=self._on_start,
            height=BUTTON_HEIGHT,
            font=FONT_NORMAL
        )
        start_button.pack(side="right", padx=PADDING_SMALL)
        
        # Bind Enter key
        self.bind("<Return>", lambda e: self._on_start())
    
    def _on_start(self) -> None:
        host = self.host_entry.get().strip() or "0.0.0.0"
        
        try:
            port = int(self.port_entry.get().strip())
            if port < 1 or port > 65535:
                raise ValueError("Port must be between 1 and 65535")
        except ValueError as e:
            show_error("Invalid Port", str(e))
            return
        
        self.result = (host, port)
        self.destroy()


def show_error(title: str, message: str) -> None:
    messagebox.showerror(title, message)


def show_warning(title: str, message: str) -> None:
    messagebox.showwarning(title, message)


def show_info(title: str, message: str) -> None:
    messagebox.showinfo(title, message)


def confirm_dialog(title: str, message: str) -> bool:
    return messagebox.askyesno(title, message)


def input_dialog(title: str, prompt: str, placeholder: str = "") -> Optional[str]:
    dialog = InputDialog(None, title, prompt, placeholder)
    dialog.wait_window()
    return dialog.result


class InputDialog(ctk.CTkToplevel):    
    def __init__(self, parent, title: str, prompt: str, placeholder: str = ""):
        # Get the root window if parent is None
        if parent is None:
            parent = ctk.CTk._default_root
        
        super().__init__(parent)
        
        self.result: Optional[str] = None
        
        # Configure window
        self.title(title)
        self.geometry("400x180")
        self.resizable(False, False)
        
        # Make modal
        self.transient(parent)
        self.grab_set()
        
        # Center on screen
        self.update_idletasks()
        x = (self.winfo_screenwidth() // 2) - (self.winfo_width() // 2)
        y = (self.winfo_screenheight() // 2) - (self.winfo_height() // 2)
        self.geometry(f"+{x}+{y}")
        
        self._create_widgets(prompt, placeholder)
        
        # Focus
        self.entry.focus()
    
    def _create_widgets(self, prompt: str, placeholder: str) -> None:
        # Prompt
        prompt_label = ctk.CTkLabel(
            self,
            text=prompt,
            font=FONT_NORMAL
        )
        prompt_label.pack(pady=PADDING_LARGE)
        
        # Entry
        self.entry = ctk.CTkEntry(
            self,
            placeholder_text=placeholder,
            font=FONT_NORMAL,
            height=ENTRY_HEIGHT,
            width=350
        )
        self.entry.pack(padx=PADDING_LARGE, pady=PADDING_SMALL)
        
        # Buttons
        button_frame = ctk.CTkFrame(self, fg_color="transparent")
        button_frame.pack(fill="x", padx=PADDING_LARGE, pady=PADDING_MEDIUM)
        
        cancel_button = ctk.CTkButton(
            button_frame,
            text="Cancel",
            command=self.destroy,
            height=BUTTON_HEIGHT,
            font=FONT_NORMAL,
            fg_color="gray30"
        )
        cancel_button.pack(side="right", padx=PADDING_SMALL)
        
        ok_button = ctk.CTkButton(
            button_frame,
            text="OK",
            command=self._on_ok,
            height=BUTTON_HEIGHT,
            font=FONT_NORMAL
        )
        ok_button.pack(side="right", padx=PADDING_SMALL)
        
        self.bind("<Return>", lambda e: self._on_ok())
        self.bind("<Escape>", lambda e: self.destroy())
    
    def _on_ok(self) -> None:
        self.result = self.entry.get().strip()
        self.destroy()