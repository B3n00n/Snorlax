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
        
        os.makedirs(self.apk_directory, exist_ok=True)
    
    def start(self):
        if self.running:
            return
        
        handler = partial(SimpleHTTPRequestHandler, directory=self.apk_directory)
        self.server = HTTPServer((self.host, self.port), handler)
        
        self.running = True
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        
        logger.info(f"APK HTTP server started on http://{self.get_local_ip()}:{self.port}")
    
    def stop(self):
        self.running = False
        if self.server:
            self.server.shutdown()
    
    def get_local_ip(self) -> str:
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
            return ip
        except:
            return "127.0.0.1"
    
    def get_apk_url(self, filename: str) -> str:
        return f"http://{self.get_local_ip()}:{self.port}/{filename}"
    
    def list_apk_files(self) -> list[str]:
        try:
            return [f for f in os.listdir(self.apk_directory) if f.endswith('.apk')]
        except:
            return []