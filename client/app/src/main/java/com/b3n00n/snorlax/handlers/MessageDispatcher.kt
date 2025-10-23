package com.b3n00n.snorlax.handlers

import android.content.Context
import android.util.Log
import com.b3n00n.snorlax.handlers.impl.*
import com.b3n00n.snorlax.network.ConnectionManager
import com.b3n00n.snorlax.protocol.MessageOpcode
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.protocol.PacketWriter
import java.io.ByteArrayInputStream

class MessageDispatcher(
    private val context: Context,
    private val connectionManager: ConnectionManager
) {
    companion object {
        private const val TAG = "MessageDispatcher"
    }

    private val registry = HandlerRegistry(context)

    init {
        registerHandlers()
        Log.d(TAG, "Initialized with ${registry.getHandlerCount()} handlers")
    }

    private fun registerHandlers() {
        registry.register(LaunchAppHandler(context))
        registry.register(ExecuteShellHandler())
        registry.register(RequestBatteryHandler(context))
        registry.register(RequestInstalledAppsHandler(context))
        registry.register(PingHandler(context))
        registry.register(InstallApkHandler(context))
        registry.register(InstallLocalApkHandler(context))
        registry.register(ShutdownHandler(context))
        registry.register(UninstallAppHandler(context))
        registry.register(SetVolumeHandler(context))
        registry.register(GetVolumeHandler(context))
    }

    fun handleIncoming(data: ByteArray) {
        try {
            val inputStream = ByteArrayInputStream(data)
            val reader = PacketReader(inputStream)

            val opcode = reader.readU8().toByte()
            val length = reader.readU16()

            Log.d(TAG, "Received: opcode=0x${String.format("%02X", opcode)}, length=$length")

            val responseBytes = registry.handle(opcode, reader)

            if (responseBytes != null && responseBytes.isNotEmpty()) {
                connectionManager.sendData(responseBytes)
                Log.d(TAG, "Sent response: ${responseBytes.size} bytes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming packet", e)
            sendError("Error: ${e.message}")
        }
    }

    // =============================================================================
    // CLIENT-INITIATED PACKETS
    // =============================================================================

    fun sendDeviceConnected(model: String, serial: String) {
        try {
            val payload = PacketWriter()
            payload.writeString(model)
            payload.writeString(serial)

            val packet = PacketWriter()
            packet.writeU8(MessageOpcode.DEVICE_CONNECTED.toInt() and 0xFF)
            packet.writeU16(payload.toByteArray().size)
            packet.writeBytes(payload.toByteArray())

            connectionManager.sendData(packet.toByteArray())
            Log.d(TAG, "Sent DeviceConnected: $model / $serial")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send DeviceConnected", e)
        }
    }

    fun sendHeartbeat() {
        try {
            val packet = PacketWriter()
            packet.writeU8(MessageOpcode.HEARTBEAT.toInt() and 0xFF)
            packet.writeU16(0) // Empty payload

            connectionManager.sendData(packet.toByteArray())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Heartbeat", e)
        }
    }

    private fun sendError(message: String) {
        try {
            val payload = PacketWriter()
            payload.writeString(message)

            val packet = PacketWriter()
            packet.writeU8(MessageOpcode.ERROR.toInt() and 0xFF)
            packet.writeU16(payload.toByteArray().size)
            packet.writeBytes(payload.toByteArray())

            connectionManager.sendData(packet.toByteArray())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Error", e)
        }
    }
}
