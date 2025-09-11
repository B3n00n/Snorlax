import dearpygui.dearpygui as dpg
import os
import shutil
from typing import Optional
import sys

from core.server import QuestControlServer
from utils.logger import logger


class APKManagerDialog:
    def __init__(self, server: QuestControlServer):
        self.server = server
        self.dialog_tag = None
        self.file_list_tag = None
    
    def show(self):
        self.dialog_tag = dpg.generate_uuid()
        
        with dpg.window(
            label="APK File Manager",
            modal=True,
            show=True,
            tag=self.dialog_tag,
            width=600,
            height=400,
            pos=[dpg.get_viewport_width() // 2 - 300, dpg.get_viewport_height() // 2 - 200]
        ):
            dpg.add_text("Manage APK files on server")
            dpg.add_separator()
            
            with dpg.group(horizontal=True):
                dpg.add_button(
                    label="Add APK File",
                    callback=self._show_file_selector,
                    width=120
                )
                dpg.add_button(
                    label="Refresh",
                    callback=self._refresh_list,
                    width=120
                )
                dpg.add_button(
                    label="Open Folder",
                    callback=self._open_apk_folder,
                    width=120
                )
            
            dpg.add_separator()
            
            self.file_list_tag = dpg.generate_uuid()
            with dpg.child_window(
                tag=self.file_list_tag,
                height=-40,
                border=True
            ):
                self._populate_file_list()
            
            dpg.add_separator()
            
            dpg.add_button(
                label="Close",
                callback=lambda: dpg.delete_item(self.dialog_tag),
                width=100
            )
    
    def _populate_file_list(self):
        dpg.delete_item(self.file_list_tag, children_only=True)
        
        apk_files = self.server.get_available_apks()
        apk_dir = getattr(self.server.apk_server, 'apk_directory', 'apks')
        if apk_dir is None:
            apk_dir = 'apks'
        
        if not apk_files:
            dpg.add_text(
                "No APK files found. Add APK files to the 'apks' directory.",
                parent=self.file_list_tag,
                color=(200, 200, 100)
            )
            return
        
        for apk_file in apk_files:
            with dpg.group(horizontal=True, parent=self.file_list_tag):
                dpg.add_text(apk_file, color=(200, 200, 200))
                
                try:
                    file_path = os.path.join(apk_dir, apk_file)
                    size_mb = os.path.getsize(file_path) / (1024 * 1024)
                    dpg.add_text(f"({size_mb:.1f} MB)", color=(150, 150, 150))
                except:
                    pass
                
                dpg.add_button(
                    label="Remove",
                    callback=self._remove_button_callback,
                    user_data=apk_file,
                    small=True
                )
    
    def _remove_button_callback(self, sender, app_data, user_data):
        filename = user_data
        self._remove_file(filename)
    
    def _show_file_selector(self):
        try:
            default_path = os.path.expanduser("~")
            
            file_dialog_tag = dpg.generate_uuid()
            
            with dpg.file_dialog(
                directory_selector=False,
                show=True,
                callback=self._on_file_selected,
                tag=file_dialog_tag,
                width=700,
                height=400,
                modal=True,
                default_path=default_path
            ):
                dpg.add_file_extension(".*", color=(255, 255, 255, 255))
                dpg.add_file_extension(".apk", color=(0, 255, 0, 255), custom_text="[APK]")
                
        except Exception as e:
            logger.error(f"Error showing file dialog: {e}")
            self._show_manual_input_dialog()
    
    def _show_manual_input_dialog(self):
        dialog_tag = dpg.generate_uuid()
        input_tag = dpg.generate_uuid()
        
        def browse_and_add():
            file_path = dpg.get_value(input_tag).strip()
            if file_path and os.path.exists(file_path) and file_path.lower().endswith('.apk'):
                self._copy_apk_to_server(file_path)
                self._refresh_list()
                dpg.delete_item(dialog_tag)
            else:
                logger.error("Invalid file path or not an APK file")
        
        with dpg.window(
            label="Add APK File",
            modal=True,
            show=True,
            tag=dialog_tag,
            width=600,
            height=200,
            pos=[dpg.get_viewport_width() // 2 - 300, dpg.get_viewport_height() // 2 - 100]
        ):
            dpg.add_text("Enter full path to APK file:")
            dpg.add_input_text(
                tag=input_tag,
                hint="C:\\Users\\Combatica\\Downloads\\app.apk",
                width=-1
            )
            dpg.add_spacer(height=10)
            
            with dpg.group(horizontal=True):
                dpg.add_button(label="Add", callback=browse_and_add, width=100)
                dpg.add_button(
                    label="Cancel",
                    callback=lambda: dpg.delete_item(dialog_tag),
                    width=100
                )
    
    def _on_file_selected(self, sender, app_data):
        try:
            selections = app_data.get("selections", {})
            if selections:
                for file_name, file_path in selections.items():
                    if file_path.lower().endswith('.apk'):
                        self._copy_apk_to_server(file_path)
                    else:
                        logger.warning(f"Skipped non-APK file: {file_name}")
            
            dpg.delete_item(sender)
            self._refresh_list()
        except Exception as e:
            logger.error(f"Error processing file selection: {e}")
    
    def _copy_apk_to_server(self, source_path: str):
        try:
            filename = os.path.basename(source_path)
            apk_dir = getattr(self.server.apk_server, 'apk_directory', 'apks')
            if apk_dir is None:
                apk_dir = 'apks'
            
            os.makedirs(apk_dir, exist_ok=True)
            
            dest_path = os.path.join(apk_dir, filename)
            
            shutil.copy2(source_path, dest_path)
            logger.info(f"Copied APK to server: {filename}")
        except Exception as e:
            logger.error(f"Error copying APK: {e}")
    
    def _refresh_list(self):
        self._populate_file_list()
    
    def _remove_file(self, filename: str):
        try:
            logger.info(f"Attempting to remove file: {filename}")
            
            if filename is None:
                logger.error("Filename is None!")
                return
            
            if hasattr(self.server, 'apk_server') and hasattr(self.server.apk_server, 'apk_directory'):
                apk_dir = self.server.apk_server.apk_directory
            else:
                if getattr(sys, 'frozen', False):
                    base_path = os.path.dirname(sys.executable)
                else:
                    base_path = os.path.abspath('.')
                apk_dir = os.path.join(base_path, 'apks')
                
            file_path = os.path.join(apk_dir, filename)
            
            if os.path.exists(file_path):
                os.remove(file_path)
                logger.info(f"Removed APK: {filename}")
                self._refresh_list()
            else:
                logger.error(f"File not found: {file_path}")
                self._refresh_list()
                
        except Exception as e:
            logger.error(f"Error removing APK {filename}: {str(e)}")
            self._refresh_list()
    
    def _open_apk_folder(self):
        import platform
        import subprocess
        
        apk_dir = getattr(self.server.apk_server, 'apk_directory', 'apks')
        if apk_dir is None:
            apk_dir = 'apks'
        
        if getattr(sys, 'frozen', False):
            base_path = os.path.dirname(sys.executable)
        else:
            base_path = os.path.abspath('.')
            
        apk_dir = os.path.join(base_path, apk_dir)
        
        os.makedirs(apk_dir, exist_ok=True)
        
        try:
            if platform.system() == "Windows":
                os.startfile(apk_dir)
            elif platform.system() == "Darwin":
                subprocess.Popen(["open", apk_dir])
            else:
                subprocess.Popen(["xdg-open", apk_dir])
        except Exception as e:
            logger.error(f"Error opening folder: {e}")