package com.b3n00n.snorlax.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WiFiConnectionManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "WiFiConnectionManager"
        private const val RECONNECT_INTERVAL_SECONDS = 15L
    }

    private val wifiManager: WifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager: ConnectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var currentNetwork: Network? = null
    private val isRunning = AtomicBoolean(false)
    private val isConnected = AtomicBoolean(false)
    private var listener: WiFiConnectionListener? = null

    @Volatile private var targetSsid: String? = null
    @Volatile private var targetPassword: String? = null
    @Volatile private var currentNetworkId: Int = -1
    private var reconnectExecutor: ScheduledExecutorService? = null

    interface WiFiConnectionListener {
        fun onWiFiConnected(network: Network)
        fun onWiFiDisconnected()
        fun onWiFiConnectionFailed(reason: String)
    }

    fun connect(ssid: String, password: String) {
        if (isRunning.get()) {
            Log.w(TAG, "WiFi connection management already running")
            return
        }

        targetSsid = ssid
        targetPassword = password
        isRunning.set(true)

        Log.d(TAG, "Starting WiFi connection management for SSID: $ssid")

        if (!wifiManager.isWifiEnabled) {
            Log.d(TAG, "Enabling WiFi...")
            wifiManager.isWifiEnabled = true
        }

        startNetworkMonitoring()

        startReconnectionLoop()
    }

    fun disconnect() {
        Log.d(TAG, "Stopping WiFi connection management")
        isRunning.set(false)

        reconnectExecutor?.shutdownNow()
        reconnectExecutor = null

        unregisterNetworkCallback()

        if (currentNetworkId != -1) {
            wifiManager.removeNetwork(currentNetworkId)
            wifiManager.saveConfiguration()
            currentNetworkId = -1
        }

        currentNetwork = null
        isConnected.set(false)
        targetSsid = null
        targetPassword = null

        Log.d(TAG, "WiFi connection management stopped")
    }

    fun setConnectionListener(listener: WiFiConnectionListener) {
        this.listener = listener
    }

    private fun startReconnectionLoop() {
        reconnectExecutor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "WiFiReconnect").apply { isDaemon = true }
        }

        reconnectExecutor?.scheduleWithFixedDelay({
            try {
                if (isRunning.get() && !isConnected.get()) {
                    val ssid = targetSsid
                    val password = targetPassword
                    if (ssid != null && password != null) {
                        attemptConnection(ssid, password)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in reconnection loop", e)
            }
        }, 0, RECONNECT_INTERVAL_SECONDS, TimeUnit.SECONDS)
    }

    private fun attemptConnection(ssid: String, password: String) {
        if (!isRunning.get()) {
            return
        }

        Log.d(TAG, "Attempting WiFi connection to SSID: $ssid")

        try {
            if (currentNetworkId != -1) {
                wifiManager.removeNetwork(currentNetworkId)
                wifiManager.saveConfiguration()
                currentNetworkId = -1
            }

            val wifiConfig = createWifiConfiguration(ssid, password)

            val networkId = wifiManager.addNetwork(wifiConfig)

            if (networkId == -1) {
                Log.e(TAG, "Failed to add WiFi network configuration")
                listener?.onWiFiConnectionFailed("Failed to configure network")
                return
            }

            currentNetworkId = networkId

            wifiManager.saveConfiguration()
            val enableResult = wifiManager.enableNetwork(networkId, true)
            val reconnectResult = wifiManager.reconnect()

            Log.d(TAG, "WiFi connection initiated (networkId=$networkId, enabled=$enableResult, reconnected=$reconnectResult)")

        } catch (e: Exception) {
            Log.e(TAG, "Error configuring WiFi connection", e)
            isConnected.set(false)
            listener?.onWiFiConnectionFailed("Configuration error: ${e.message}")
        }
    }

    private fun createWifiConfiguration(ssid: String, password: String): WifiConfiguration {
        return WifiConfiguration().apply {
            SSID = "\"$ssid\""

            if (password.isEmpty()) {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            } else {
                preSharedKey = "\"$password\""
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                allowedProtocols.set(WifiConfiguration.Protocol.RSN)
                allowedProtocols.set(WifiConfiguration.Protocol.WPA)
                allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
            }
        }
    }

    private fun startNetworkMonitoring() {
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val ssid = getCurrentSsid()
                if (ssid == targetSsid) {
                    Log.d(TAG, "WiFi network connected: $ssid")
                    currentNetwork = network
                    isConnected.set(true)
                    listener?.onWiFiConnected(network)
                }
            }

            override fun onLost(network: Network) {
                if (currentNetwork == network) {
                    Log.d(TAG, "WiFi network lost")
                    currentNetwork = null
                    isConnected.set(false)
                    listener?.onWiFiDisconnected()
                }
            }
        }

        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
            Log.d(TAG, "Network monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting network monitoring", e)
            networkCallback = null
        }
    }

    private fun getCurrentSsid(): String? {
        return try {
            wifiManager.connectionInfo?.ssid?.removeSurrounding("\"")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current SSID", e)
            null
        }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { callback ->
            try {
                connectivityManager.unregisterNetworkCallback(callback)
                Log.d(TAG, "Network callback unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering network callback", e)
            } finally {
                networkCallback = null
            }
        }
    }
}
