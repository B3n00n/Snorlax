package com.b3n00n.snorlax.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class WiFiConfigurationManager(private val context: Context) {
    companion object {
        private const val TAG = "WiFiConfigurationManager"
        private const val PREFS_NAME = "snorlax_config"
        private const val KEY_WIFI_SSID = "wifi_ssid"
        private const val KEY_WIFI_PASSWORD = "wifi_password"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getWifiSsid(): String? {
        return prefs.getString(KEY_WIFI_SSID, null)
    }

    fun getWifiPassword(): String? {
        return prefs.getString(KEY_WIFI_PASSWORD, null)
    }

    fun setWifiConfig(ssid: String, password: String) {
        prefs.edit().apply {
            putString(KEY_WIFI_SSID, ssid)
            putString(KEY_WIFI_PASSWORD, password)
            apply()
        }
        Log.d(TAG, "WiFi configuration saved: SSID=$ssid")
    }

    fun clearWifiConfig() {
        prefs.edit().apply {
            remove(KEY_WIFI_SSID)
            remove(KEY_WIFI_PASSWORD)
            apply()
        }
        Log.d(TAG, "WiFi configuration cleared")
    }

    fun isValidSsid(ssid: String): Boolean {
        return ssid.isNotEmpty() && ssid.length <= 32
    }

    fun isValidPassword(password: String): Boolean {
        return password.isEmpty() || password.length in 8..63
    }

    fun hasWifiConfig(): Boolean {
        return !getWifiSsid().isNullOrEmpty()
    }
}
