package com.b3n00n.snorlax.network

import android.content.Context
import android.util.Log
import com.b3n00n.snorlax.utils.DeviceOwnerWifiManager
import kotlinx.coroutines.*

class WifiConnectionManager(private val context: Context) {
    companion object {
        private const val TAG = "WifiConnectionManager"
        private const val RECONNECT_INTERVAL_MS = 15000L
        private const val CONNECTION_CHECK_DELAY_MS = 5000L
    }

    private val deviceOwnerWifiManager = DeviceOwnerWifiManager(context)
    private var reconnectJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    interface WifiConnectionListener {
        fun onWifiConnected(ssid: String)
        fun onWifiDisconnected()
        fun onWifiConnectionFailed(reason: String)
    }

    private var listener: WifiConnectionListener? = null

    fun setListener(listener: WifiConnectionListener) {
        this.listener = listener
    }

    fun connectToWifi(ssid: String, password: String): Boolean {
        return try {
            val success = deviceOwnerWifiManager.configureAndConnectWifi(ssid, password)

            if (success) {
                monitorConnection(ssid)
            } else {
                listener?.onWifiConnectionFailed("Failed to configure WiFi")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to WiFi", e)
            listener?.onWifiConnectionFailed(e.message ?: "Unknown error")
            false
        }
    }

    private fun monitorConnection(ssid: String) {
        coroutineScope.launch {
            delay(CONNECTION_CHECK_DELAY_MS)

            // Keep checking until connected
            while (isActive && !isConnectedToWifi(ssid)) {
                delay(CONNECTION_CHECK_DELAY_MS)
            }

            if (isConnectedToWifi(ssid)) {
                listener?.onWifiConnected(ssid)
            }
        }
    }

    fun getCurrentSsid(): String? = deviceOwnerWifiManager.getCurrentSsid()

    fun isConnectedToWifi(ssid: String): Boolean = deviceOwnerWifiManager.isConnectedToWifi(ssid)

    fun startAutoReconnect(ssid: String, password: String) {
        stopAutoReconnect()

        reconnectJob = coroutineScope.launch {
            while (isActive) {
                if (!isConnectedToWifi(ssid)) {
                    Log.d(TAG, "Not connected to $ssid, attempting reconnection...")
                    connectToWifi(ssid, password)
                }
                delay(RECONNECT_INTERVAL_MS)
            }
        }
    }

    fun stopAutoReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    fun cleanup() {
        stopAutoReconnect()
        coroutineScope.cancel()
        deviceOwnerWifiManager.cleanup()
    }
}