package com.b3n00n.snorlax.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class WifiConfigurationManager(private val context: Context) {
    companion object {
        private const val TAG = "WifiConfigurationManager"
        private const val PREFS_NAME = "snorlax_wifi_config"
        private const val KEY_WIFI_SSID = "wifi_ssid"
        private const val KEY_WIFI_PASSWORD = "wifi_password"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getWifiSSID(): String {
        return prefs.getString(KEY_WIFI_SSID, "") ?: ""
    }

    fun getWifiPassword(): String {
        return prefs.getString(KEY_WIFI_PASSWORD, "") ?: ""
    }

    fun isAutoReconnectEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_RECONNECT, true)
    }

    fun setWifiConfig(ssid: String, password: String, autoReconnect: Boolean) {
        prefs.edit().apply {
            putString(KEY_WIFI_SSID, ssid)
            putString(KEY_WIFI_PASSWORD, password)
            putBoolean(KEY_AUTO_RECONNECT, autoReconnect)
            apply()
        }
        Log.d(TAG, "WiFi configuration saved: SSID=$ssid, AutoReconnect=$autoReconnect")
    }

    fun hasWifiConfig(): Boolean {
        return getWifiSSID().isNotEmpty()
    }

    fun clearWifiConfig() {
        prefs.edit().clear().apply()
        Log.d(TAG, "WiFi configuration cleared")
    }
}