# Quest Control Center

A GUI control server for managing Quest headsets running Snorlax.

<img width="1384" height="789" alt="image" src="https://github.com/user-attachments/assets/62a8c789-4c28-465f-892a-47e3cafb0e8c" />

## Features

### The basics
- **Device Management**: See all connected Quests, their battery levels, IP addresses
- **Multi select**: Control multiple devices at once
- **Real time updates**: Battery status, connection state, everything updates live
- **Command history**: See what commands ran on each device

### The good stuff
- **Combatica App Focus**: Special handling for Combatica apps
- **APK Server**: Built in HTTP server for hosting APKs locally
- **Custom Device Names** - Name your quest devices
- **Developer Mode** - More dev functionality

### Under the hood
- TCP server on port 8888 (for Snorlax connections)
- HTTP server on port 8889 (for APK hosting)
- Persistent device name storage

## Installation

### Requirements
- Python 3.8+
- Windows/Mac/Linux (anywhere Dear PyGui runs)

### Quick start
```bash
git clone <repo>
cd quest-control-center

pip install -r requirements.txt

python main.py
```

## How to use

### First time setup
1. Launch the app
2. Server starts automatically on `0.0.0.0:8888`
3. Configure your Quests to connect to your computer's IP
4. Watch them pop up in the device list

### Managing devices
- **Select devices**: Click checkboxes or use Select All
- **Battery check**: Refreshes every 60 seconds (or click Refresh Battery)
- **Device details**: Click a device name to see full info
- **Custom names**: Menu → View → Device Names Manager

### Installing APKs
Two ways to install APKs:

**Remote URL**:
1. Select devices
2. Click "Install Remote APK"
3. Paste URL
4. Done

**Local APK**:
1. Drop APK files in the `apks` folder
2. Select devices
3. Click "Install Local APK"
4. Pick from dropdown
5. Way faster than uploading to cloud storage

### Combatica apps
If you're working with Combatica apps, life is easier:
- **Launch**: Dropdown shows only Combatica apps installed on selected devices
- **Uninstall**: Same deal, but for removing them
- **List**: Shows all Combatica apps with nice formatting

### Power user stuff
Enable Developer Mode (Menu → Dev) for:
- Raw shell commands
- Launch any app by package name
- Uninstall any app (except protected ones)
- See detailed command responses

## Configuration

### Server settings
Menu → Server → Server Settings
- Change host/port
- Server auto restarts with new settings

### Config files
- `device_names.json` - Your custom device names
- `quest_control.log` - Everything that happens
- `apks/` - Local APK storage for quick installation on quest devices

## Protocol details

Same binary protocol as Snorlax uses. Check the Snorlax README for the full spec, but basically:
- 1 byte message type
- Length-prefixed strings
- Big-endian numbers
- No encryption

## Troubleshooting

**Devices not showing up**
- Check Windows Firewall isn't blocking port 8888
- Make sure Quest and PC are on same network
- Verify Snorlax is actually running (`adb shell ps | grep snorlax`)

**APK install fails**
- Check the HTTP server is running (port 8889)
- Make sure Quest can reach your PC's IP
- Try the URL in a browser first

**"Module not found" errors**
- You probably forgot `pip install -r requirements.txt`

## Why not just use ADB?

Because:
1. ADB requires USB or adb tcp setup each time
2. Can't manage 10 Quests with ADB easily  
3. No nice UI for non technical team members
4. Battery monitoring needs constant polling

## License

B3n00n / Combatica LTD
