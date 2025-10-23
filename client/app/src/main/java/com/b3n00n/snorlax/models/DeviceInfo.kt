package com.b3n00n.snorlax.models

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings

class DeviceInfo(private val context: Context? = null) {
    val model: String = Build.MODEL
    val serial: String = getDeviceSerial()

    @SuppressLint("HardwareIds")
    private fun getDeviceSerial(): String {
        return try {
            context?.let { ctx ->
                Settings.Secure.getString(
                    ctx.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
            } ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
}