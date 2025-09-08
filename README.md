# Snorlax For Quest Management

Remote control and management tools for Meta Quest headsets. Three tools, one goal: make Quest management suck less.

## What's in here?

This repo contains three interconnected projects:

### 🎮 [Snorlax](./client/)
The Android app that runs on your Quest. Turns your headset into a remotely controllable device. Install APKs, check battery, launch apps - without putting the headset on your face.

### 🖥️ [Quest Control Center](./server/)
Desktop GUI server that talks to Snorlax. Manage multiple Quest devices from one interface. Built with Python and Dear PyGui.

### 🔧 [SnorlaxSetup](./setup/)
Windows tool that automates the painful setup process. Handles the ADB commands, device owner setup, and all that jazz. One click and you're done.

## Quick Start

1. **Setup a Quest**: Use SnorlaxSetup on a freshly reset Quest
2. **Run the server**: Launch Quest Control Center on your computer  
3. **Connect**: Snorlax on the Quest connects to your server
4. **Control**: Manage your Quest(s) from the Control Center

## The Stack

- **Snorlax**: Kotlin, Android Service, TCP client
- **Control Center**: Python, DearPyGui, TCP/HTTP servers
- **Setup Tool**: Python, customtkinter, ADB wrapper

## Use Cases

- Managing Quest devices for VR arcades
- Remote app deployment for development teams
- Battery monitoring for charging stations
- Demo setups without controller juggling
- Bulk app installation/updates

## Requirements

- Meta Quest with developer mode
- Windows/Mac/Linux computer
- Same network (or routable connection)
- Coffee (optional but recommended)

## Documentation

Each project has its own detailed README:
- [Snorlax README](./client/README.md) - The Quest app
- [Control Center README](./server/README.md) - The server
- [Setup Tool README](./setup/README.md) - The installer

## License

Made by B3n00n

---

*Because sometimes you need to manage 20 Quest headsets and only have two hands, and VR is horrific*