import sys
import argparse

try:
    from src.gui.main_window import MainWindow
    GUI_AVAILABLE = True
except ImportError:
    GUI_AVAILABLE = False
    
from src.core.setup_manager import SetupManager
from src.utils.logger import logger


def run_gui():
    if not GUI_AVAILABLE:
        logger.error("GUI dependencies not installed. Please install customtkinter:")
        logger.error("pip install customtkinter")
        sys.exit(1)
        
    try:
        app = MainWindow()
        app.run()
    except Exception as e:
        logger.error(f"GUI Error: {str(e)}")
        sys.exit(1)


def run_cli(adb_path=None, skip_check=False):
    logger.info("Running in CLI mode")
    
    setup = SetupManager(adb_path)
    
    try:
        success = setup.run_setup(skip_quest_check=skip_check)
        sys.exit(0 if success else 1)
    except KeyboardInterrupt:
        logger.warning("Setup interrupted by user")
        setup.cleanup()
        sys.exit(1)
    except Exception as e:
        logger.error(f"Unexpected error: {str(e)}")
        setup.cleanup()
        sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description='Snorlax Setup Tool')
    parser.add_argument(
        '--cli', 
        action='store_true', 
        help='Run in CLI mode instead of GUI'
    )
    parser.add_argument(
        '--adb-path', 
        type=str, 
        help='Custom path to ADB executable'
    )
    parser.add_argument(
        '--skip-quest-check', 
        action='store_true',
        help='Skip Quest device verification'
    )
    
    args = parser.parse_args()
    
    if args.cli:
        run_cli(args.adb_path, args.skip_quest_check)
    else:
        run_gui()


if __name__ == "__main__":
    main()