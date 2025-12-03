package com.b3n00n.snorlax.handlers.impl

import android.util.Log
import com.b3n00n.snorlax.config.WiFiConfigurationManager
import com.b3n00n.snorlax.core.BackgroundJobs
import com.b3n00n.snorlax.core.ClientContext
import com.b3n00n.snorlax.handlers.IPacketHandler
import com.b3n00n.snorlax.handlers.PacketHandler
import com.b3n00n.snorlax.helpers.ServiceRestartHelper
import com.b3n00n.snorlax.network.NetworkClient
import com.b3n00n.snorlax.protocol.MessageOpcode
import com.b3n00n.snorlax.protocol.PacketReader

/**
 * Handles ClearWifiCredentials command (0x4E): Clear WiFi configuration.
 *
 * Payload format: Empty
 *
 * Clears the stored WiFi credentials and restarts the RemoteClientService.
 * No response is sent as the device will restart its connection immediately after.
 */
@PacketHandler(MessageOpcode.CLEAR_WIFI_CREDENTIALS)
class ClearWifiCredentialsHandler : IPacketHandler {
    companion object {
        private const val TAG = "ClearWifiCredentialsHandler"
    }

    override fun handle(reader: PacketReader, client: NetworkClient) {
        try {
            Log.d(TAG, "Clearing WiFi credentials")

            val context = ClientContext.context
            val wifiConfigManager = WiFiConfigurationManager(context)

            wifiConfigManager.clearWifiConfig()
            Log.d(TAG, "WiFi credentials cleared")

            BackgroundJobs.submit {
                ServiceRestartHelper.restartRemoteClientService(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling clear WiFi credentials command", e)
        }
    }
}
