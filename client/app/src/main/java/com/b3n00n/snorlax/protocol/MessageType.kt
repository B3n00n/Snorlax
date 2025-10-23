package com.b3n00n.snorlax.protocol

/**
 * Protocol opcodes for Arceus client-server communication
 *
 * Wire format: [Opcode: u8][Length: u16 BE][Payload: varies]
 */
object MessageType {
    // =============================================================================
    // CLIENT → SERVER (Initiated by client) - 0x01-0x05
    // =============================================================================

    /** Device identification on connection - Payload: [model: String, serial: String] */
    const val DEVICE_CONNECTED: Byte = 0x01

    /** Keep-alive heartbeat - Payload: empty */
    const val HEARTBEAT: Byte = 0x02

    /** Battery status update - Payload: [level: u8, is_charging: bool] */
    const val BATTERY_STATUS: Byte = 0x03

    /** Volume status update - Payload: [percentage: u8, current: u8, max: u8] */
    const val VOLUME_STATUS: Byte = 0x04

    /** Error message from client - Payload: [message: String] */
    const val ERROR: Byte = 0x05

    // =============================================================================
    // CLIENT → SERVER (Responses to commands) - 0x10-0x17
    // =============================================================================

    /** Response to LaunchApp command - Payload: [success: bool, message: String] */
    const val LAUNCH_APP_RESPONSE: Byte = 0x10

    /** Response to ExecuteShell command - Payload: [success: bool, output: String, exit_code: i32] */
    const val SHELL_EXECUTION_RESPONSE: Byte = 0x11

    /** Response to RequestInstalledApps - Payload: [count: u32, apps: Vec<String>] */
    const val INSTALLED_APPS_RESPONSE: Byte = 0x12

    /** Response to Ping command - Payload: [timestamp: u64] */
    const val PING_RESPONSE: Byte = 0x13

    /** Response to InstallApk command - Payload: [success: bool, message: String] */
    const val APK_INSTALL_RESPONSE: Byte = 0x14

    /** Response to UninstallApp command - Payload: [success: bool, message: String] */
    const val UNINSTALL_APP_RESPONSE: Byte = 0x15

    /** Response to Shutdown command - Payload: empty */
    const val SHUTDOWN_RESPONSE: Byte = 0x16

    /** Response to SetVolume command - Payload: [success: bool, actual_level: u8] */
    const val VOLUME_SET_RESPONSE: Byte = 0x17

    // =============================================================================
    // SERVER → CLIENT (Commands from server) - 0x40-0x4B
    // =============================================================================

    /** Launch an application - Payload: [package_name: String] */
    const val LAUNCH_APP: Byte = 0x40

    /** Execute shell command - Payload: [command: String] */
    const val EXECUTE_SHELL: Byte = 0x41

    /** Request battery status - Payload: empty */
    const val REQUEST_BATTERY: Byte = 0x42

    /** Request installed apps list - Payload: empty */
    const val REQUEST_INSTALLED_APPS: Byte = 0x43

    /** Request device information - Payload: empty */
    const val REQUEST_DEVICE_INFO: Byte = 0x44

    /** Ping/connectivity test - Payload: [timestamp: u64] */
    const val PING: Byte = 0x45

    /** Install APK from URL - Payload: [url: String] */
    const val INSTALL_APK: Byte = 0x46

    /** Install APK from local server - Payload: [filename: String] */
    const val INSTALL_LOCAL_APK: Byte = 0x47

    /** Shutdown/restart device - Payload: empty */
    const val SHUTDOWN: Byte = 0x48

    /** Uninstall application - Payload: [package_name: String] */
    const val UNINSTALL_APP: Byte = 0x49

    /** Set device volume - Payload: [level: u8] */
    const val SET_VOLUME: Byte = 0x4A

    /** Get current volume - Payload: empty */
    const val GET_VOLUME: Byte = 0x4B
}
