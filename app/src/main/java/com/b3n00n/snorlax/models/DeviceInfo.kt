package com.b3n00n.snorlax.models

import android.annotation.SuppressLint
import android.os.Build
import android.provider.Settings

class DeviceInfo {
    val model: String = Build.MODEL
    val serial: String = getDeviceSerial()
    val androidVersion: String = Build.VERSION.RELEASE
    val sdkVersion: Int = Build.VERSION.SDK_INT
    val manufacturer: String = Build.MANUFACTURER

    @SuppressLint("HardwareIds")
    private fun getDeviceSerial(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use Android ID for Android 10+
                Settings.Secure.getString(
                    android.app.ActivityThread.currentApplication()?.contentResolver,
                    Settings.Secure.ANDROID_ID
                ) ?: "Unknown"
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL ?: "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    override fun toString(): String {
        return "Model=$model,Android=$androidVersion,SDK=$sdkVersion,Manufacturer=$manufacturer"
    }
}