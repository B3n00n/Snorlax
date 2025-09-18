package com.b3n00n.snorlax.models

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings

class DeviceInfo(private val context: Context? = null) {
    val model: String = Build.MODEL
    val serial: String = getDeviceSerial()
    val androidVersion: String = Build.VERSION.RELEASE
    val sdkVersion: Int = Build.VERSION.SDK_INT
    val manufacturer: String = Build.MANUFACTURER

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

    override fun toString(): String {
        return "Model=$model,Android=$androidVersion,SDK=$sdkVersion,Manufacturer=$manufacturer"
    }
}