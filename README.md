# Snorlax

Remote control and management for Meta Quest headsets. Because sometimes you need to manage your Quest without putting it on your face.

## What is this?

Snorlax is an Android app that runs on your Quest and lets you control it remotely from a server. Install apps, check battery, launch games, run shell commands - all without touching the headset. Perfect for:

- Managing multiple Quest devices
- Remote app deployment and testing
- Checking battery status of headsets on charge
- Launching apps for demos without fumbling with controllers
- Basic device administration

The app runs as a background service and maintains a persistent connection to your control server.

## How it works

```
Your Computer → Control Server (port 8888) → Quest running Snorlax
```

The app connects to a server you specify and waits for commands. No cloud services, no third parties - just direct device control over your local network.

## Features

- **App Management**: List installed apps, launch them, install APKs, uninstall apps
- **Device Info**: Battery status, charging state, device model and serial
- **Remote Shell**: Execute shell commands (limited by Meta modified Android OS permissions)
- **Silent APK Installation**: Install apps without user interaction
- **Power Control**: Shutdown or restart the device remotely
- **Auto start**: Starts Snorlax automatically on device boot
- **Persistent Connection**: Automatic reconnection if network drops

## Requirements

### On the Quest:
- Meta Quest running Android
- Developer mode enabled
- Device owner privileges

### For the server:
- A computer running the control server on defined port
- Same network as your Quest (or routable connection)

## Installation

### Using setup tool:
Use SnorlaxSetup.exe - it handles everything automatically

## Configuration

Currently the server IP is hardcoded to `192.168.0.77:8888` in `RemoteClientService.kt`. Yeah, I know. PR welcome.

To change it:
1. Edit `SERVER_IP` and `SERVER_PORT` in `RemoteClientService.kt`
2. Rebuild the APK
3. Reinstall

## Protocol

The app uses a custom binary protocol over TCP:

- Messages start with a 1-byte message type
- Followed by message-specific data
- Strings are length-prefixed (4 bytes) + UTF-8 data
- Numbers are big-endian

Message types:
- see `MessageType.kt` for full list

## Security considerations

**This app is a remote control backdoor.** It's meant for development and testing, not production use. Consider:

- It connects to a hardcoded IP (no auth)
- Commands aren't encrypted
- Shell access is available (though limited by Android permissions)
- Device owner = can install anything silently

Only use on test devices. Only use on trusted networks. You've been warned.

## Troubleshooting

**App keeps stopping/not connecting:**
- Check the server is actually running on port 8888
- Make sure Quest and server are on same network
- Check firewall isn't blocking port 8888

**Silent install not working:**
- Needs device owner - only works on freshly reset devices

**Can't execute certain shell commands:**
- Android restricts what apps can do via shell
- Even with device owner, you're not root

## Why "Snorlax"?

Because it runs in the background doing nothing most of the time, then wakes up when you need it to do something. Also I was tired when naming it :^)

## Contributing

Found a bug? Fixed the hardcoded IP thing? PRs welcome. Keep it simple - this is meant to be a lightweight tool, not a full MDM solution like ArborXR.
