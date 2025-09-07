import dearpygui.dearpygui as dpg
from typing import Callable, Optional


class ConfirmDialog:
    def __init__(self):
        self.callback: Optional[Callable] = None
        self.dialog_tag = None
    
    def show(self, title: str, message: str, callback: Callable, parent=None):
        self.callback = callback
        self.dialog_tag = dpg.generate_uuid()
        
        with dpg.window(
            label=title,
            modal=True,
            show=True,
            tag=self.dialog_tag,
            width=400,
            height=150,
            pos=[dpg.get_viewport_width() // 2 - 200, dpg.get_viewport_height() // 2 - 75],
            on_close=lambda: dpg.delete_item(self.dialog_tag)
        ):
            dpg.add_text(message, wrap=380)
            dpg.add_spacer(height=20)
            
            with dpg.group(horizontal=True):
                dpg.add_button(
                    label="Yes",
                    width=75,
                    callback=self._on_confirm
                )
                dpg.add_button(
                    label="No",
                    width=75,
                    callback=self._on_cancel
                )
    
    def _on_confirm(self):
        if self.callback:
            self.callback(True)
        dpg.delete_item(self.dialog_tag)
    
    def _on_cancel(self):
        if self.callback:
            self.callback(False)
        dpg.delete_item(self.dialog_tag)


def show_confirm_dialog(title: str, message: str, callback: Callable):
    dialog = ConfirmDialog()
    dialog.show(title, message, callback)