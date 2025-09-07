import os
import threading
from http.server import HTTPServer, SimpleHTTPRequestHandler
from functools import partial
import socket
from typing import Optional

from utils.logger import logger


class APKHttpServer:    
    def __init__(self, host: str = '0.0.0.0', port: int = 8889, apk_directory: str = "apks"):
        self.host = host
        self.port = port
        self.apk_directory = apk_directory
        self.server: Optional[HTTPServer] = None
        self.thread: Optional[threading.Thread] = None
        self.running = False
        self._local_ip: Optional[str] = None
        
        os.makedirs(self.apk_directory, exist_ok=True)
    
    def start(self):
        if self.running:
            return
        
        try:
            handler = partial(SimpleHTTPRequestHandler, directory=self.apk_directory)
            self.server = HTTPServer((self.host, self.port), handler)
            
            handler.log_message = lambda *args: None
            
            self.running = True
            self.thread = threading.Thread(target=self._run_server, daemon=True)
            self.thread.start()
            
        except Exception as e:
            logger.error(f"Failed to start HTTP server: {e}")
            self.running = False
            raise
    
    def _run_server(self):
        try:
            self.server.serve_forever()
        except Exception as e:
            logger.error(f"HTTP server error: {e}")
        finally:
            self.running = False
    
    def stop(self):
        self.running = False
        if self.server:
            self.server.shutdown()
            self.server.server_close()
        
        if self.thread and self.thread.is_alive():
            self.thread.join(timeout=5)
            
    def get_local_ip(self) -> str:
        if not self._local_ip:
            try:
                s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                s.connect(("8.8.8.8", 80))
                self._local_ip = s.getsockname()[0]
                s.close()
            except Exception as e:
                logger.error(f"Failed to determine local IP: {e}")
                self._local_ip = "127.0.0.1"
        return self._local_ip
    
    def get_apk_url(self, filename: str) -> str:
        return f"http://{self.get_local_ip()}:{self.port}/{filename}"
    
    def list_apk_files(self) -> list[str]:
        try:
            files = []
            for f in os.listdir(self.apk_directory):
                if f.endswith('.apk') and os.path.isfile(os.path.join(self.apk_directory, f)):
                    files.append(f)
            return sorted(files)
        except Exception as e:
            logger.error(f"Error listing APK files: {e}")
            return []
    
    def is_running(self) -> bool:
        return self.running and self.server is not None