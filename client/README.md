# Snorlax

Remote control and management for Meta Quest headsets. Because sometimes you need to manage your Quest without putting it on your face.

## What is this?

Snorlax is an Android app that runs on your Quest and lets you control it remotely from a server. Install apps, check battery, launch games, run shell commands - without touching the headset. Perfect for:

- Managing multiple Quest devices
- Remote app deployment and testing
- Checking battery status of headsets on charge
- Launching apps for demos without fumbling with controllers
- Basic device administration

The app runs as a background service and maintains a persistent connection to your control server.

## How it works

```
Your Computer → Snorlax Server → Quest running Snorlax
```

The app connects to a server you specify and waits for commands. just direct device control over your local network.

## Features

- **App Management**: List installed apps, launch them, install APKs, uninstall apps
- **Device Info**: Battery status, charging state, device model and serial
- **Remote Shell**: Execute shell commands (limited by Meta shit modified Android OS permissions)
- **Silent APK Installation**: Download and install apps from URLs without user interaction
- **Power Control**: Shutdown or restart the device remotely (requires device owner)
- **Auto Start**: Starts automatically on device boot
- **Persistent Connection**: Automatic reconnection with heartbeat monitoring
- **Configuration UI**: Built-in configuration activity to change server settings
- **Audio Feedback**: Ping command plays a beep sound for device identification

## Requirements

### On the Quest:
- Meta Quest running Android
- Developer mode enabled
- Device owner privileges (for advanced features like silent install/uninstall, shutdown)

### For the server:
- A computer running the Snorlax server
- Same network as your Quest (or routable connection)

## Installation
Use SnorlaxSetup.exe - it handles everything automatically (Read it's docs)

## Configuration
The app includes a configuration UI:

1. Tap the notification while the service is running
2. The configuration screen will appear
3. Enter your server IP and port
4. Tap "Save & Apply"

Configuration is stored persistently and survives reboots.

### Message Format:
The app uses a custom binary protocol over TCP:

- Messages start with a 1-byte message type
- Followed by message specific data
- Strings are length-prefixed (4 bytes) + UTF-8 data
- Numbers are big endian

### Configuration UI
- Visual configuration interface optimized for VR
- Persistent settings storage

### Android 13+ Support
- Proper notification permissions handling
- Updated to latest Android APIs
- Foreground service type declarations

**This app is a remote control backdoor.** It's meant for development and testing, not production use yet. Consider:

- No authentication on connections
- Commands aren't encrypted
- Shell access is available (though limited by Oculus garbage Android modifications)
- Device owner = can install/uninstall anything

## Troubleshooting

**App keeps stopping/not connecting:**
- Check the server is actually running
- Verify Quest and server are on same network
- Check firewall isn't blocking the port
- Use the configuration UI to verify/update server settings

**Configuration not opening:**
- Try stopping and restarting the service
- Check logcat for errors: `adb logcat | grep Snorlax`

**Silent install not working:**
- Requires device owner - run the dpm command after fresh reset
- Check if app is actually set as device owner

**Downloads failing:**
- Ensure the Quest has internet access (if remote download)
- Check if the APK URL is accessible
- Verify storage permissions

**Can't execute certain shell commands:**
- Android restricts shell access for apps
- Even with device owner, you're not root
- Some commands simply won't work

## Why "Snorlax"?

Because it runs in the background doing nothing most of the time, then wakes up when you need it to do something. Also I was tired when naming it :^).

## License

Made by B3n00n / Combatica LTD