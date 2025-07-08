package com.b3n00n.snorlax.protocol

object MessageType {
    // Client to Server
    const val DEVICE_CONNECTED: Byte = 0x01
    const val HEARTBEAT: Byte = 0x02
    const val BATTERY_STATUS: Byte = 0x03
    const val COMMAND_RESPONSE: Byte = 0x04
    const val ERROR: Byte = 0x05

    // Server to Client
    const val LAUNCH_APP: Byte = 0x10
    const val EXECUTE_SHELL: Byte = 0x12
    const val REQUEST_BATTERY: Byte = 0x13
    const val GET_INSTALLED_APPS: Byte = 0x14
    const val GET_DEVICE_INFO: Byte = 0x15
    const val PING: Byte = 0x16
    const val DOWNLOAD_AND_INSTALL_APK: Byte = 0x17
    const val SHUTDOWN_DEVICE: Byte = 0x18
    const val UNINSTALL_APP: Byte = 0x19
}