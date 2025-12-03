package com.b3n00n.snorlax.protocol

/**
 * Protocol opcodes for all packet types.
 *
 * Wire format: [Opcode: u8][Length: u16 BE][Payload]
 */
object MessageOpcode {
    // =============================================================================
    // CLIENT → SERVER (Client-initiated) - 0x01-0x05
    // =============================================================================

    const val DEVICE_CONNECTED: Byte = 0x01
    const val HEARTBEAT: Byte = 0x02
    const val BATTERY_STATUS: Byte = 0x03
    const val VOLUME_STATUS: Byte = 0x04
    const val ERROR: Byte = 0x05
    const val FOREGROUND_APP_CHANGED: Byte = 0x06

    // =============================================================================
    // CLIENT → SERVER (Responses to server commands) - 0x10-0x16
    // =============================================================================

    const val LAUNCH_APP_RESPONSE: Byte = 0x10
    const val SHELL_EXECUTION_RESPONSE: Byte = 0x11
    const val INSTALLED_APPS_RESPONSE: Byte = 0x12
    const val PING_RESPONSE: Byte = 0x13
    const val APK_INSTALL_RESPONSE: Byte = 0x14
    const val UNINSTALL_APP_RESPONSE: Byte = 0x15
    const val VOLUME_SET_RESPONSE: Byte = 0x16
    const val APK_DOWNLOAD_STARTED: Byte = 0x17
    const val CLOSE_ALL_APPS_RESPONSE: Byte = 0x18
    const val APK_DOWNLOAD_PROGRESS: Byte = 0x19
    const val APK_INSTALL_PROGRESS: Byte = 0x1A

    // =============================================================================
    // SERVER → CLIENT (Commands from server) - 0x40-0x4C
    // =============================================================================

    const val LAUNCH_APP: Byte = 0x40
    const val EXECUTE_SHELL: Byte = 0x41
    const val REQUEST_BATTERY: Byte = 0x42
    const val REQUEST_INSTALLED_APPS: Byte = 0x43
    const val PING: Byte = 0x45
    const val INSTALL_APK: Byte = 0x46
    const val INSTALL_LOCAL_APK: Byte = 0x47
    const val SHUTDOWN: Byte = 0x48
    const val UNINSTALL_APP: Byte = 0x49
    const val SET_VOLUME: Byte = 0x4A
    const val GET_VOLUME: Byte = 0x4B
    const val CLOSE_ALL_APPS: Byte = 0x4C
    const val CONFIGURE_DEVICE: Byte = 0x4D
    const val CLEAR_WIFI_CREDENTIALS: Byte = 0x4E
    const val DISPLAY_MESSAGE: Byte = 0x50
}
