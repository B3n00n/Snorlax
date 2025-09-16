package com.b3n00n.snorlax.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.util.Log
import com.b3n00n.snorlax.receivers.DeviceOwnerReceiver
import kotlinx.coroutines.*

class DeviceOwnerWifiManager(private val context: Context) {
    companion object {
        private const val TAG = "DeviceOwnerWifiManager"
    }

    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun configureAndConnectWifi(ssid: String, password: String): Boolean {
        if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
            Log.e(TAG, "App is not device owner")
            return false
        }

        return try {
            // First enable WiFi if disabled
            if (!enableWifi()) {
                Log.e(TAG, "Failed to enable WiFi")
                return false
            }

            // Configure and connect
            configureWifi(ssid, password)
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring WiFi", e)
            false
        }
    }

    private fun enableWifi(): Boolean {
        return try {
            if (!wifiManager.isWifiEnabled) {
                Log.d(TAG, "WiFi is disabled, enabling...")

                // For device owners, we can still use the deprecated API
                @Suppress("DEPRECATION")
                val success = wifiManager.setWifiEnabled(true)

                if (!success) {
                    Log.e(TAG, "Failed to enable WiFi using WifiManager")
                    return false
                }

                // Wait for WiFi to actually turn on
                coroutineScope.launch {
                    var attempts = 0
                    while (!wifiManager.isWifiEnabled && attempts < 20) {
                        delay(500)
                        attempts++
                    }
                }

                Thread.sleep(3000) // Give it time to stabilize
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable WiFi", e)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun configureWifi(ssid: String, password: String): Boolean {
        return try {
            // Remove existing configuration
            removeExistingNetwork(ssid)

            // Create new configuration
            val config = createWifiConfiguration(ssid, password)
            val networkId = wifiManager.addNetwork(config)

            if (networkId == -1) {
                Log.e(TAG, "Failed to add network configuration")
                return false
            }

            // Save and connect
            wifiManager.saveConfiguration()
            wifiManager.disconnect()
            val success = wifiManager.enableNetwork(networkId, true)
            wifiManager.reconnect()

            if (success) {
                Log.d(TAG, "WiFi configured and connection initiated")
            } else {
                Log.e(TAG, "Failed to enable network")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring WiFi", e)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun createWifiConfiguration(ssid: String, password: String): WifiConfiguration {
        return WifiConfiguration().apply {
            SSID = "\"$ssid\""

            if (password.isEmpty()) {
                // Open network
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            } else {
                // WPA/WPA2
                preSharedKey = "\"$password\""
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
            }

            status = WifiConfiguration.Status.ENABLED
            priority = 9999
        }
    }

    @Suppress("DEPRECATION")
    private fun removeExistingNetwork(ssid: String) {
        try {
            val configuredNetworks = wifiManager.configuredNetworks
            configuredNetworks?.forEach { config ->
                if (config.SSID == "\"$ssid\"") {
                    wifiManager.removeNetwork(config.networkId)
                    wifiManager.saveConfiguration()
                    Log.d(TAG, "Removed existing network configuration for $ssid")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing existing network", e)
        }
    }

    fun getCurrentSsid(): String? {
        return try {
            val info = wifiManager.connectionInfo
            if (info != null && info.networkId != -1) {
                info.ssid?.replace("\"", "")?.takeIf {
                    it != "<unknown ssid>" && it.isNotBlank()
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current SSID", e)
            null
        }
    }

    fun isConnectedToWifi(ssid: String): Boolean {
        val currentSsid = getCurrentSsid()
        return currentSsid == ssid
    }

    fun cleanup() {
        coroutineScope.cancel()
    }
}