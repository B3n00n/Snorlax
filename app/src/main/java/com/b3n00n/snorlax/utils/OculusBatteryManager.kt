package com.b3n00n.snorlax.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.b3n00n.snorlax.models.BatteryInfo
import java.io.BufferedReader
import java.io.InputStreamReader

class OculusBatteryManager(private val context: Context) {
    companion object {
        private const val TAG = "OculusBatteryManager"
    }

    fun getBatteryInfo(): BatteryInfo {
        val headsetBattery = getHeadsetBattery()
        val isCharging = isHeadsetCharging()
        val leftControllerBattery = getControllerBattery("left")
        val rightControllerBattery = getControllerBattery("right")

        return BatteryInfo(
            headsetBattery,
            leftControllerBattery,
            rightControllerBattery,
            isCharging
        )
    }

    private fun getHeadsetBattery(): Int {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)

        return batteryStatus?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

            if (level != -1 && scale != -1) {
                (level * 100 / scale.toFloat()).toInt()
            } else -1
        } ?: -1
    }

    private fun isHeadsetCharging(): Boolean {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)

        return batteryStatus?.let {
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        } ?: false
    }

    private fun getControllerBattery(controller: String): Int {
        return try {
            val property = "debug.oculus.controller.$controller.battery"
            getSystemProperty(property)?.toIntOrNull()
                ?: readControllerBatteryFromSysfs(controller)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get $controller controller battery: ${e.message}")
            -1
        }
    }

    private fun readControllerBatteryFromSysfs(controller: String): Int {
        return try {
            val batteryPath = "/sys/class/power_supply/bms_$controller/capacity"
            val result = ShellExecutor.execute("cat $batteryPath")

            if (result.isNotEmpty() && !result.contains("ERROR")) {
                result.trim().toIntOrNull() ?: -1
            } else -1
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read $controller controller battery from sysfs: ${e.message}")
            -1
        }
    }

    private fun getSystemProperty(property: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("getprop $property")
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readLine()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting system property: $property", e)
            null
        }
    }
}
