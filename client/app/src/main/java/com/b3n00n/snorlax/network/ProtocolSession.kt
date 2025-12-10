package com.b3n00n.snorlax.network

import android.util.Log
import com.b3n00n.snorlax.config.SnorlaxConfigManager
import com.b3n00n.snorlax.core.ClientContext
import com.b3n00n.snorlax.handlers.HandlerRegistry
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
    private val client: NetworkClient
) : NetworkClient.ConnectionListener {

    companion object {
        private const val TAG = "ProtocolSession"
    }

    private val registry = HandlerRegistry()

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
     *
     * Packet format: [version][model][serial]
     * Note: Version is sent first to enable automatic client updates even when packet format changes.
     */
    private fun sendDeviceConnected() {
        try {
            val deviceInfo = ClientContext.deviceInfo
            client.sendPacket(MessageOpcode.DEVICE_CONNECTED) {
                writeString(SnorlaxConfigManager.APP_VERSION)
                writeString(deviceInfo.model)
                writeString(deviceInfo.serial)
            }
            Log.d(TAG, "Sent device connected: v${SnorlaxConfigManager.APP_VERSION} ${deviceInfo.model} (${deviceInfo.serial})")
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

    /**
     * Send foreground app change notification to the server.
     *
     * @param packageName The package name of the foreground app
     * @param appName The human-readable name of the app
     */
    fun sendForegroundAppChanged(packageName: String, appName: String) {
        try {
            client.sendPacket(MessageOpcode.FOREGROUND_APP_CHANGED) {
                writeString(packageName)
                writeString(appName)
            }
            Log.d(TAG, "Sent foreground app changed: $appName ($packageName)")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending foreground app changed", e)
        }
    }
}
