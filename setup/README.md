## What's this for?

This sets up Snorlax as the device owner on your Meta Quest headset. If you don't know what Snorlax is or why you'd want it as device owner, this tool probably isn't for you. But if you do know, this automates the whole painful process of downloading, installing, and configuring it.

The tool checks if you've got a Quest connected, downloads Snorlax if needed, installs it, and sets it up as device owner.

## Before you start

You'll need:
- A fresh Meta Quest device, post factory reset. (Quest 2, Quest 3, Quest Pro..)
- Developer mode enabled on your Quest
- ADB installed on your computer
- Python 3.7+ if running from source

The Quest needs to be freshly factory reset for the device owner setup to work. That's an Android thing, not something I can fix. If it fails at the device owner step, that's probably why.

## Quick start (using the exe)

1. Download the latest `SnorlaxSetup.exe` from releases or build from source
2. Connect your Quest via USB
3. Run the exe
4. Click "Start Setup" and wait

That's literally it. The tool will tell you what's happening and whether it worked.

## Running from source

If you're the type who likes to see what code is running on their machine (smart):

```bash
git clone [this repo]
cd snorlax-setup
pip install -r requirements.txt
python main.py
```

## Building from source

Want to build your own exe? Here's how:

1. Install dependencies:
```bash
pip install customtkinter Pillow pyinstaller
```
2. Build it:
```bash
python -m PyInstaller --onefile --windowed --icon=resources/icon.ico --name=SnorlaxSetup --add-data "config.py;." --add-data "src;src" --hidden-import=customtkinter main.py
```

Your exe will be in the `dist` folder. 

## CLI mode

Don't like GUIs? There's a CLI mode:

```bash
python main.py --cli
# or with the exe:
SnorlaxSetup.exe --cli
```

You can also specify a custom ADB path:
```bash
python main.py --cli --adb-path "C:\path\to\adb.exe"
```

## Common issues

**"ADB not found"**
- Install ADB from Android Platform Tools
- Add it to your environment variables, or use the 'Advanced' button in the GUI to set the path

**"Device not found"**
- Make sure developer mode is on
- Check the USB connection
- Accept the debugging prompt on your Quest

**"Failed to set device owner"**
- Your Quest needs to be freshly set up (factory reset)
- Can't have multiple user accounts
- Can't have work profiles

**Multiple CMD windows popping up**
- That's ADB doing its thing. Annoying but harmless.

## What it actually does

1. Checks you've got a Quest connected (won't work with phones or other Android devices)
2. Downloads Snorlax APK from the official source
3. Installs it on your Quest
4. Sets it as device owner (gives it special permissions)
5. Launches the app

## Credits

This is just a wrapper around ADB commands. The real magic is in Snorlax itself - This was made just to make the setup less painful.