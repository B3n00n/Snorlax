package com.b3n00n.snorlax.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

class WiFiStateMonitor(
    private val context: Context
) {
    companion object {
        private const val TAG = "WiFiStateMonitor"
    }

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var listener: WiFiStateListener? = null
    private var isMonitoring = false

    interface WiFiStateListener {
        fun onWiFiAvailable()
        fun onWiFiLost()
    }

    init {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    fun startMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "Already monitoring WiFi state")
            return
        }

        Log.d(TAG, "Starting WiFi state monitoring")

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "WiFi network available")
                listener?.onWiFiAvailable()
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "WiFi network lost")

                if (!isConnectedToWiFi()) {
                    listener?.onWiFiLost()
                }
            }
        }

        connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
        isMonitoring = true
        Log.d(TAG, "WiFi state monitoring started")
    }

    fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }

        Log.d(TAG, "Stopping WiFi state monitoring")

        try {
            networkCallback?.let { callback ->
                connectivityManager?.unregisterNetworkCallback(callback)
            }
            networkCallback = null
            isMonitoring = false
            Log.d(TAG, "WiFi state monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping WiFi state monitoring", e)
        }
    }

    fun setStateListener(listener: WiFiStateListener) {
        this.listener = listener
    }

    fun isConnectedToWiFi(): Boolean {
        return try {
            val activeNetwork = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking WiFi connectivity", e)
            false
        }
    }
}
