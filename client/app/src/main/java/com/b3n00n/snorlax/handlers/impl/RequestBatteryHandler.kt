package com.b3n00n.snorlax.handlers.impl

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.b3n00n.snorlax.handlers.IPacketHandler
import com.b3n00n.snorlax.handlers.PacketHandler
import com.b3n00n.snorlax.protocol.MessageOpcode
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.protocol.PacketWriter

/**
 * Handles RequestBattery command (0x42): empty payload
 * Responds with BatteryStatus (0x03): [level: u8][is_charging: bool]
 */
@PacketHandler(MessageOpcode.REQUEST_BATTERY)
class RequestBatteryHandler(private val context: Context) : IPacketHandler {
    companion object {
        private const val TAG = "RequestBatteryHandler"
    }

    override fun handle(reader: PacketReader, writer: PacketWriter) {
        // No payload to read

        Log.d(TAG, "Requesting battery status")

        var level = 0
        var isCharging = false

        try {
            val batteryStatus: Intent? = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

            if (batteryStatus != null) {
                val batteryLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val batteryScale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                level = if (batteryScale > 0) (batteryLevel * 100 / batteryScale) else 0

                val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
            }

            Log.d(TAG, "Battery: $level%, charging=$isCharging")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery status", e)
        }

        // Build payload
        val payload = PacketWriter()
        payload.writeU8(level)
        payload.writeU8(if (isCharging) 1 else 0)

        // Write response packet
        writer.writeU8(MessageOpcode.BATTERY_STATUS.toInt() and 0xFF)
        writer.writeU16(payload.toByteArray().size)
        writer.writeBytes(payload.toByteArray())
    }
}
