package com.b3n00n.snorlax.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.b3n00n.snorlax.models.BatteryInfo

class OculusBatteryManager(private val context: Context) {
    companion object {
        private const val TAG = "OculusBatteryManager"
    }

    fun getBatteryInfo(): BatteryInfo {
        val headsetBattery = getHeadsetBattery()
        val isCharging = isHeadsetCharging()

        return BatteryInfo(
            headsetBattery,
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
}