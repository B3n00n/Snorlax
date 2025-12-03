package com.b3n00n.snorlax.handlers.impl

import android.util.Log
import com.b3n00n.snorlax.config.ServerConfigurationManager
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
 * Handles ConfigureDevice command (0x4D): Configure WiFi and server connection settings.
 *
 * Payload format:
 * [has_wifi_config: u8]  // 1 if WiFi config included, 0 if not
 * [wifi_ssid: string]    // Only if has_wifi_config = 1
 * [wifi_password: string]  // Only if has_wifi_config = 1
 * [server_ip: string]
 * [server_port: u16]
 *
 * No response is sent as the device will restart its connection immediately after.
 */
@PacketHandler(MessageOpcode.CONFIGURE_DEVICE)
class ConfigureDeviceHandler : IPacketHandler {
    companion object {
        private const val TAG = "ConfigureDeviceHandler"
    }

    override fun handle(reader: PacketReader, client: NetworkClient) {
        try {
            val hasWifiConfig = reader.readU8().toInt() == 1
            val wifiSsid = if (hasWifiConfig) reader.readString() else null
            val wifiPassword = if (hasWifiConfig) reader.readString() else null
            val serverIp = reader.readString()
            val serverPort = reader.readU16()

            Log.d(TAG, "Configuration received - WiFi: ${if (hasWifiConfig) "<configured>" else "none"}, Server: ***:$serverPort")

            val context = ClientContext.context
            val wifiConfigManager = WiFiConfigurationManager(context)
            val serverConfigManager = ServerConfigurationManager(context)

            if (hasWifiConfig && wifiSsid != null && wifiPassword != null) {
                if (!wifiConfigManager.isValidSsid(wifiSsid)) {
                    Log.e(TAG, "Invalid WiFi SSID: $wifiSsid (must be 1-32 characters)")
                    return
                }
                if (!wifiConfigManager.isValidPassword(wifiPassword)) {
                    Log.e(TAG, "Invalid WiFi password (must be empty or 8-63 characters)")
                    return
                }
                wifiConfigManager.setWifiConfig(wifiSsid, wifiPassword)
                Log.d(TAG, "WiFi configuration saved: SSID=$wifiSsid")
            }

            if (!serverConfigManager.isValidIpAddress(serverIp)) {
                Log.e(TAG, "Invalid server IP address: $serverIp")
                return
            }
            if (!serverConfigManager.isValidPort(serverPort)) {
                Log.e(TAG, "Invalid server port: $serverPort (must be 1-65535)")
                return
            }
            serverConfigManager.setServerConfig(serverIp, serverPort)
            Log.d(TAG, "Server configuration saved: $serverIp:$serverPort")

            BackgroundJobs.submit {
                ServiceRestartHelper.restartRemoteClientService(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling configure device command", e)
        }
    }
}
