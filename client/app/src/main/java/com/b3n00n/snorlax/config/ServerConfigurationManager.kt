package com.b3n00n.snorlax.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class ServerConfigurationManager(private val context: Context) {
    companion object {
        private const val TAG = "ServerConfigurationManager"
        private const val PREFS_NAME = "snorlax_config"
        private const val KEY_SERVER_IP = "server_ip"
        private const val KEY_SERVER_PORT = "server_port"
        private const val DEFAULT_SERVER_IP = "192.168.0.77"
        private const val DEFAULT_SERVER_PORT = 8888
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getServerIp(): String {
        return prefs.getString(KEY_SERVER_IP, DEFAULT_SERVER_IP) ?: DEFAULT_SERVER_IP
    }

    fun getServerPort(): Int {
        return prefs.getInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT)
    }

    fun setServerConfig(ip: String, port: Int) {
        prefs.edit().apply {
            putString(KEY_SERVER_IP, ip)
            putInt(KEY_SERVER_PORT, port)
            apply()
        }
        Log.d(TAG, "Configuration saved: $ip:$port")
    }

    fun isValidIpAddress(ip: String): Boolean {
        return ip.matches(
            Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")
        )
    }

    fun isValidPort(port: Int): Boolean {
        return port in 1..65535
    }
}