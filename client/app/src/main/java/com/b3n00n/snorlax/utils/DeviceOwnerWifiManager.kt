package com.b3n00n.snorlax.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiEnterpriseConfig
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.b3n00n.snorlax.receivers.DeviceOwnerReceiver
import kotlinx.coroutines.*

class DeviceOwnerWifiManager(private val context: Context) {
    companion object {
        private const val TAG = "DeviceOwnerWifiManager"
    }

    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val adminComponent = ComponentName(context, DeviceOwnerReceiver::class.java)

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun configureAndConnectWifi(ssid: String, password: String): Boolean {
        if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
            Log.e(TAG, "App is not device owner")
            return false
        }

        return try {
            // First enable WiFi if disabled
            enableWifi()

            // Configure network based on Android version
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    // Android 11+ - use addWifiNetwork
                    configureWifiAndroid11Plus(ssid, password)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    // Android 10 - use device owner privileges
                    configureWifiAndroid10(ssid, password)
                }
                else -> {
                    // Android 9 and below
                    configureWifiLegacy(ssid, password)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring WiFi", e)
            false
        }
    }

    private fun enableWifi(): Boolean {
        return try {
            if (!wifiManager.isWifiEnabled) {
                Log.d(TAG, "Enabling WiFi...")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Use device owner API for Android Q+
                    devicePolicyManager.setGlobalSetting(
                        adminComponent,
                        Settings.Global.WIFI_ON,
                        "1"
                    )
                } else {
                    @Suppress("DEPRECATION")
                    wifiManager.isWifiEnabled = true
                }

                // Wait for WiFi to enable
                Thread.sleep(3000)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable WiFi", e)
            false
        }
    }

    private fun configureWifiAndroid11Plus(ssid: String, password: String): Boolean {
        return try {
            // On Android 11+, device owners can use private APIs via reflection
            // or use the legacy approach which still works for device owners

            // First, try to remove any existing configuration
            removeExistingNetwork(ssid)

            // Use legacy API which still works for device owners
            val config = createWifiConfiguration(ssid, password)
            val networkId = wifiManager.addNetwork(config)

            if (networkId != -1) {
                // Save configuration
                wifiManager.saveConfiguration()

                // Enable and connect
                wifiManager.disconnect()
                wifiManager.enableNetwork(networkId, true)
                wifiManager.reconnect()

                Log.d(TAG, "WiFi configured successfully for Android 11+")
                true
            } else {
                Log.e(TAG, "Failed to add network configuration")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring WiFi for Android 11+", e)
            false
        }
    }

    private fun configureWifiAndroid10(ssid: String, password: String): Boolean {
        return try {
            // For Android 10, device owners can still use WifiManager APIs
            removeExistingNetwork(ssid)

            val config = createWifiConfiguration(ssid, password)
            val networkId = wifiManager.addNetwork(config)

            if (networkId != -1) {
                wifiManager.saveConfiguration()
                wifiManager.disconnect()
                wifiManager.enableNetwork(networkId, true)
                wifiManager.reconnect()

                Log.d(TAG, "WiFi configured successfully for Android 10")
                true
            } else {
                Log.e(TAG, "Failed to add network configuration")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring WiFi for Android 10", e)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun configureWifiLegacy(ssid: String, password: String): Boolean {
        return try {
            removeExistingNetwork(ssid)

            val config = createWifiConfiguration(ssid, password)
            val networkId = wifiManager.addNetwork(config)

            if (networkId != -1) {
                wifiManager.saveConfiguration()
                wifiManager.disconnect()
                wifiManager.enableNetwork(networkId, true)
                wifiManager.reconnect()

                Log.d(TAG, "WiFi configured successfully (legacy)")
                true
            } else {
                Log.e(TAG, "Failed to add network configuration")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring WiFi (legacy)", e)
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
                allowedProtocols.set(WifiConfiguration.Protocol.RSN)
                allowedProtocols.set(WifiConfiguration.Protocol.WPA)
                allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
                allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
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
                info.ssid?.replace("\"", "")?.takeIf { it != "<unknown ssid>" }
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