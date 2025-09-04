"""GUI dialogs module initialization"""

from .confirm_dialog import ConfirmDialog, show_confirm_dialog
from .device_names_dialog import DeviceNamesDialog

__all__ = ['ConfirmDialog', 'show_confirm_dialog', 'DeviceNamesDialog']