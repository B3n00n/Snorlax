import customtkinter as ctk


def apply_theme():
    ctk.set_appearance_mode("dark")
    ctk.set_default_color_theme("blue")


def get_fonts():
    return {
        'normal': ("Arial", 11),
        'heading': ("Arial", 16, "bold"),
        'subheading': ("Arial", 13, "bold"),
        'console': ("Consolas", 9),
        'button': ("Arial", 11, "bold"),
        'small': ("Arial", 9)
    }


def get_colors():
    return {
        'bg': "#1a1a1a",
        'fg': "#ffffff",
        'button_bg': "#0d47a1",
        'button_hover': "#1565c0",
        'frame_bg': "#2d2d2d",
        'entry_bg': "#3d3d3d",
        'success': "#4CAF50",
        'error': "#F44336",
        'warning': "#FF9800",
        'info': "#2196F3"
    }


def get_paddings():
    """Get standard padding values"""
    return {
        'small': 5,
        'medium': 10,
        'large': 20,
        'section': (10, 10)
    }