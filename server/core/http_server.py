import os
import sys
import threading
from http.server import HTTPServer, SimpleHTTPRequestHandler
from functools import partial
import socket
from typing import Optional
import logging

from utils.logger import logger


class APKHttpHandler(SimpleHTTPRequestHandler):    
    def log_message(self, format, *args):
        logger.debug(f"HTTP: {format}" % args)
    
    def log_error(self, format, *args):
        logger.error(f"HTTP Error: {format}" % args)


class APKHttpServer:    
    def __init__(self, host: str = '0.0.0.0', port: int = 8889, apk_directory: str = "apks"):
        self.host = host
        self.port = port
        self.apk_directory_name = apk_directory
        self.apk_directory = self._get_absolute_apk_path(apk_directory)
        self.server: Optional[HTTPServer] = None
        self.thread: Optional[threading.Thread] = None
        self.running = False
        self._local_ip: Optional[str] = None
        
        os.makedirs(self.apk_directory, exist_ok=True)
    
    def _get_absolute_apk_path(self, apk_directory: str) -> str:
        if getattr(sys, 'frozen', False):
            base_path = os.path.dirname(sys.executable)
        else:
            base_path = os.path.dirname(os.path.abspath(__file__))
            base_path = os.path.dirname(os.path.dirname(base_path))
        
        return os.path.join(base_path, apk_directory)
    
    def start(self):
        if self.running:
            return
        
        try:
            original_cwd = os.getcwd()
            
            handler_class = partial(APKHttpHandler, directory=self.apk_directory)
            
            self.server = HTTPServer((self.host, self.port), handler_class)
            self.server.socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            
            self.running = True
            self.thread = threading.Thread(target=self._run_server, daemon=True)
            self.thread.start()
            
        except Exception as e:
            logger.error(f"Failed to start HTTP server: {e}")
            self.running = False
            raise
    
    def _run_server(self):
        try:
            logger.info(f"HTTP server listening on {self.get_local_ip()}:{self.port}")
            self.server.serve_forever()
        except Exception as e:
            logger.error(f"HTTP server error: {e}")
        finally:
            self.running = False
    
    def stop(self):
        self.running = False
        if self.server:
            try:
                self.server.shutdown()
                self.server.server_close()
                logger.info("HTTP server stopped")
            except Exception as e:
                logger.error(f"Error stopping HTTP server: {e}")
        
        if self.thread and self.thread.is_alive():
            self.thread.join(timeout=5)
            
    def get_local_ip(self) -> str:
        if not self._local_ip:
            try:
                s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                s.settimeout(0.1)
                s.connect(("8.8.8.8", 80))
                self._local_ip = s.getsockname()[0]
                s.close()
            except Exception as e:
                logger.error(f"Failed to determine local IP: {e}")
                try:
                    hostname = socket.gethostname()
                    self._local_ip = socket.gethostbyname(hostname)
                except:
                    self._local_ip = "127.0.0.1"
        return self._local_ip
    
    def get_apk_url(self, filename: str) -> str:
        from urllib.parse import quote
        encoded_filename = quote(filename)
        url = f"http://{self.get_local_ip()}:{self.port}/{encoded_filename}"
        logger.info(f"Generated APK URL: {url}")
        return url
    
    def list_apk_files(self) -> list[str]:
        try:
            files = []
            logger.debug(f"Listing files in: {self.apk_directory}")
            
            if not os.path.exists(self.apk_directory):
                logger.error(f"APK directory does not exist: {self.apk_directory}")
                return []
            
            for f in os.listdir(self.apk_directory):
                if f.endswith('.apk') and os.path.isfile(os.path.join(self.apk_directory, f)):
                    files.append(f)
                    
            logger.debug(f"Found {len(files)} APK files")
            return sorted(files)
        except Exception as e:
            logger.error(f"Error listing APK files: {e}")
            return []
    
    def is_running(self) -> bool:
        return self.running and self.server is not None