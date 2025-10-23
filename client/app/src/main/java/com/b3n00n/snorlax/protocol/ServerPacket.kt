package com.b3n00n.snorlax.protocol

import java.io.IOException

/**
 * Sealed class representing all packets sent from SERVER → CLIENT
 *
 * Wire format: [Opcode: u8][Length: u16 BE][Payload]
 */
sealed class ServerPacket {

    // =============================================================================
    // SERVER COMMAND PACKETS (0x40-0x4B)
    // =============================================================================

    data class LaunchApp(val packageName: String) : ServerPacket()
    data class ExecuteShell(val command: String) : ServerPacket()
    object RequestBattery : ServerPacket()
    object RequestInstalledApps : ServerPacket()
    object RequestDeviceInfo : ServerPacket()
    data class Ping(val timestamp: Long) : ServerPacket()
    data class InstallApk(val url: String) : ServerPacket()
    data class InstallLocalApk(val filename: String) : ServerPacket()
    object Shutdown : ServerPacket()
    data class UninstallApp(val packageName: String) : ServerPacket()
    data class SetVolume(val level: Int) : ServerPacket()
    object GetVolume : ServerPacket()

    companion object {
        /**
         * Deserialize a ServerPacket from a PacketReader
         *
         * NOTE: The opcode and length have already been read by the caller.
         * This method only reads the payload portion.
         */
        @Throws(IOException::class)
        fun fromReader(opcode: Byte, reader: PacketReader): ServerPacket {
            return when (opcode) {
                MessageType.LAUNCH_APP -> {
                    LaunchApp(reader.readString())
                }
                MessageType.EXECUTE_SHELL -> {
                    ExecuteShell(reader.readString())
                }
                MessageType.REQUEST_BATTERY -> {
                    RequestBattery
                }
                MessageType.REQUEST_INSTALLED_APPS -> {
                    RequestInstalledApps
                }
                MessageType.REQUEST_DEVICE_INFO -> {
                    RequestDeviceInfo
                }
                MessageType.PING -> {
                    Ping(reader.readU64())
                }
                MessageType.INSTALL_APK -> {
                    InstallApk(reader.readString())
                }
                MessageType.INSTALL_LOCAL_APK -> {
                    InstallLocalApk(reader.readString())
                }
                MessageType.SHUTDOWN -> {
                    Shutdown
                }
                MessageType.UNINSTALL_APP -> {
                    UninstallApp(reader.readString())
                }
                MessageType.SET_VOLUME -> {
                    SetVolume(reader.readU8())
                }
                MessageType.GET_VOLUME -> {
                    GetVolume
                }
                else -> {
                    throw IOException("Unknown server packet opcode: 0x${opcode.toString(16)}")
                }
            }
        }
    }
}
