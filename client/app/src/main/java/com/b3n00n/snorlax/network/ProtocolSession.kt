package com.b3n00n.snorlax.network

import android.util.Log
import com.b3n00n.snorlax.handlers.HandlerRegistry
import com.b3n00n.snorlax.models.DeviceInfo
import com.b3n00n.snorlax.protocol.MessageOpcode

/**
 * Manages the protocol lifecycle and session state.
 *
 * This class is responsible for:
 * - Routing incoming packets to handlers
 * - Sending protocol-level messages (heartbeat, device connected, errors)
 * - Managing the session lifecycle
 */
class ProtocolSession(
    private val client: NetworkClient,
    private val registry: HandlerRegistry,
    private val deviceInfo: DeviceInfo
) : NetworkClient.ConnectionListener {

    companion object {
        private const val TAG = "ProtocolSession"
    }

    init {
        client.setConnectionListener(this)
    }

    override fun onConnected() {
        Log.d(TAG, "Protocol session started")
        sendDeviceConnected()
    }

    override fun onDisconnected() {
        Log.d(TAG, "Protocol session ended")
    }

    override fun onPacketReceived(packet: ByteArray) {
        // Route packet to appropriate handler
        registry.routePacket(packet, client)
    }

    override fun onError(e: Exception) {
        Log.e(TAG, "Protocol error: ${e.message}")
    }

    /**
     * Send a heartbeat packet.
     * Called periodically by the service to keep the connection alive.
     */
    fun sendHeartbeat() {
        client.sendPacket(MessageOpcode.HEARTBEAT) {
            // Empty payload
        }
    }

    /**
     * Send device connection notification with device info.
     * Called once after connecting to the server.
     */
    private fun sendDeviceConnected() {
        try {
            client.sendPacket(MessageOpcode.DEVICE_CONNECTED) {
                writeString(deviceInfo.model)
                writeString(deviceInfo.serial)
            }
            Log.d(TAG, "Sent device connected: ${deviceInfo.model} (${deviceInfo.serial})")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending device connected", e)
        }
    }

    /**
     * Send an error message to the server.
     *
     * @param message The error message
     */
    fun sendError(message: String) {
        client.sendPacket(MessageOpcode.ERROR) {
            writeString(message)
        }
    }
}
