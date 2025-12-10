package com.b3n00n.snorlax.handlers.impl

import android.util.Log
import com.b3n00n.snorlax.config.SnorlaxConfigManager
import com.b3n00n.snorlax.core.ClientContext
import com.b3n00n.snorlax.handlers.IPacketHandler
import com.b3n00n.snorlax.handlers.PacketHandler
import com.b3n00n.snorlax.network.NetworkClient
import com.b3n00n.snorlax.protocol.MessageOpcode
import com.b3n00n.snorlax.protocol.PacketReader

/**
 * Handles VERSION_OK command (0x44): []
 * This response indicates that the client version is up-to-date.
 * Upon receiving this, the client proceeds to send DEVICE_CONNECTED with full device info.
 */
@PacketHandler(MessageOpcode.VERSION_OK)
class VersionOkHandler : IPacketHandler {
    companion object {
        private const val TAG = "VersionOkHandler"
    }

    override fun handle(reader: PacketReader, client: NetworkClient) {
        // No payload to read

        Log.i(TAG, "VERSION_OK received - client version ${SnorlaxConfigManager.APP_VERSION} is up-to-date")

        // Send DEVICE_CONNECTED with full device information
        sendDeviceConnected(client)
    }

    private fun sendDeviceConnected(client: NetworkClient) {
        try {
            val deviceInfo = ClientContext.deviceInfo
            client.sendPacket(MessageOpcode.DEVICE_CONNECTED) {
                writeString(deviceInfo.model)
                writeString(deviceInfo.serial)
            }
            Log.i(TAG, "Sent DEVICE_CONNECTED: ${deviceInfo.model} (${deviceInfo.serial})")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending DEVICE_CONNECTED", e)
        }
    }
}
