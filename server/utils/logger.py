import logging
import sys
from typing import Optional

from config.settings import LOG_LEVEL, LOG_FORMAT


def get_logger(name: str, level: Optional[str] = None) -> logging.Logger:
    logger = logging.getLogger(name)
    
    if not logger.handlers:
        handler = logging.StreamHandler(sys.stdout)
        handler.setFormatter(logging.Formatter(LOG_FORMAT))
        logger.addHandler(handler)
        
        log_level = level or LOG_LEVEL
        logger.setLevel(getattr(logging, log_level.upper(), logging.INFO))
    
    return logger