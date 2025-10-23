package com.b3n00n.snorlax.protocol

import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Sealed class representing all packets sent from CLIENT → SERVER
 *
 * Wire format: [Opcode: u8][Length: u16 BE][Payload]
 */
sealed class ClientPacket(val opcode: Byte) {

    // =============================================================================
    // CLIENT-INITIATED PACKETS (0x01-0x05)
    // =============================================================================

    data class DeviceConnected(val model: String, val serial: String) :
        ClientPacket(MessageType.DEVICE_CONNECTED)

    object Heartbeat : ClientPacket(MessageType.HEARTBEAT)

    data class BatteryStatus(val level: Int, val isCharging: Boolean) :
        ClientPacket(MessageType.BATTERY_STATUS)

    data class VolumeStatus(val percentage: Int, val current: Int, val max: Int) :
        ClientPacket(MessageType.VOLUME_STATUS)

    data class Error(val message: String) :
        ClientPacket(MessageType.ERROR)

    // =============================================================================
    // RESPONSE PACKETS (0x10-0x17)
    // =============================================================================

    data class LaunchAppResponse(val success: Boolean, val message: String) :
        ClientPacket(MessageType.LAUNCH_APP_RESPONSE)

    data class ShellExecutionResponse(val success: Boolean, val output: String, val exitCode: Int) :
        ClientPacket(MessageType.SHELL_EXECUTION_RESPONSE)

    data class InstalledAppsResponse(val apps: List<String>) :
        ClientPacket(MessageType.INSTALLED_APPS_RESPONSE)

    data class PingResponse(val timestamp: Long) :
        ClientPacket(MessageType.PING_RESPONSE)

    data class ApkInstallResponse(val success: Boolean, val message: String) :
        ClientPacket(MessageType.APK_INSTALL_RESPONSE)

    data class UninstallAppResponse(val success: Boolean, val message: String) :
        ClientPacket(MessageType.UNINSTALL_APP_RESPONSE)

    object ShutdownResponse : ClientPacket(MessageType.SHUTDOWN_RESPONSE)

    data class VolumeSetResponse(val success: Boolean, val actualLevel: Int) :
        ClientPacket(MessageType.VOLUME_SET_RESPONSE)

    // =============================================================================
    // SERIALIZATION
    // =============================================================================

    /**
     * Serialize this packet to bytes with wire format: [Opcode][Length][Payload]
     */
    @Throws(IOException::class)
    fun toByteArray(): ByteArray {
        val payloadWriter = PacketWriter()

        // Write payload based on packet type
        when (this) {
            is DeviceConnected -> {
                payloadWriter.writeString(model)
                payloadWriter.writeString(serial)
            }
            is Heartbeat -> {
                // Empty payload
            }
            is BatteryStatus -> {
                payloadWriter.writeU8(level)
                payloadWriter.writeU8(if (isCharging) 1 else 0)
            }
            is VolumeStatus -> {
                payloadWriter.writeU8(percentage)
                payloadWriter.writeU8(current)
                payloadWriter.writeU8(max)
            }
            is Error -> {
                payloadWriter.writeString(message)
            }
            is LaunchAppResponse -> {
                payloadWriter.writeU8(if (success) 1 else 0)
                payloadWriter.writeString(message)
            }
            is ShellExecutionResponse -> {
                payloadWriter.writeU8(if (success) 1 else 0)
                payloadWriter.writeString(output)
                payloadWriter.writeI32(exitCode)
            }
            is InstalledAppsResponse -> {
                payloadWriter.writeU32(apps.size.toLong())
                apps.forEach { payloadWriter.writeString(it) }
            }
            is PingResponse -> {
                payloadWriter.writeU64(timestamp)
            }
            is ApkInstallResponse -> {
                payloadWriter.writeU8(if (success) 1 else 0)
                payloadWriter.writeString(message)
            }
            is UninstallAppResponse -> {
                payloadWriter.writeU8(if (success) 1 else 0)
                payloadWriter.writeString(message)
            }
            is ShutdownResponse -> {
                // Empty payload
            }
            is VolumeSetResponse -> {
                payloadWriter.writeU8(if (success) 1 else 0)
                payloadWriter.writeU8(actualLevel)
            }
        }

        val payload = payloadWriter.toByteArray()

        // Build final message: [opcode][length: u16 BE][payload]
        val outputStream = ByteArrayOutputStream()
        val writer = PacketWriter()
        writer.writeU8(opcode.toInt() and 0xFF)
        writer.writeU16(payload.size)
        outputStream.write(writer.toByteArray())
        outputStream.write(payload)

        return outputStream.toByteArray()
    }
}
