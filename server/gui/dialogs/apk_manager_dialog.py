import dearpygui.dearpygui as dpg
import os
import shutil
from typing import Optional

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
        apk_dir = self.server.apk_server.apk_directory
        
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
                    callback=lambda s, a, f=apk_file: self._remove_file(f),
                    small=True
                )
    
    def _show_file_selector(self):
        file_dialog_tag = dpg.generate_uuid()
        
        with dpg.file_dialog(
            directory_selector=False,
            show=True,
            callback=self._on_file_selected,
            tag=file_dialog_tag,
            width=700,
            height=400,
            modal=True
        ):
            dpg.add_file_extension(".*", color=(255, 255, 255, 255))
            dpg.add_file_extension(".apk", color=(0, 255, 0, 255), custom_text="[APK]")
    
    def _on_file_selected(self, sender, app_data):
        selections = app_data["selections"]
        if selections:
            for file_name, file_path in selections.items():
                if file_path.lower().endswith('.apk'):
                    self._copy_apk_to_server(file_path)
                else:
                    logger.warning(f"Skipped non-APK file: {file_name}")
        
        dpg.delete_item(sender)
        self._refresh_list()
    
    def _copy_apk_to_server(self, source_path: str):
        try:
            filename = os.path.basename(source_path)
            dest_path = os.path.join(self.server.apk_server.apk_directory, filename)
            
            shutil.copy2(source_path, dest_path)
            logger.info(f"Copied APK to server: {filename}")
        except Exception as e:
            logger.error(f"Error copying APK: {e}")
    
    def _remove_file(self, filename: str):
        try:
            apk_dir = self.server.apk_server.apk_directory
            if not apk_dir:
                logger.error("APK directory is not set")
                return
                
            file_path = os.path.join(apk_dir, filename)
            
            if os.path.exists(file_path):
                os.remove(file_path)
                logger.info(f"Removed APK: {filename}")
                self._refresh_list()
            else:
                logger.error(f"File not found: {filename}")
                self._refresh_list()
                
        except Exception as e:
            logger.error(f"Error removing APK: {e}")
    
    def _refresh_list(self):
        self._populate_file_list()
    
    def _open_apk_folder(self):
        import platform
        import subprocess
        
        apk_dir = os.path.abspath(self.server.apk_server.apk_directory)
        
        try:
            if platform.system() == "Windows":
                os.startfile(apk_dir)
            elif platform.system() == "Darwin":
                subprocess.Popen(["open", apk_dir])
            else:  # Linux
                subprocess.Popen(["xdg-open", apk_dir])
        except Exception as e:
            logger.error(f"Error opening APK folder: {e}")