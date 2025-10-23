package com.b3n00n.snorlax.handlers.impl

import android.content.Context
import android.util.Log
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.handlers.ServerPacketHandler
import com.b3n00n.snorlax.protocol.ServerPacket
import com.b3n00n.snorlax.utils.OculusBatteryManager

class BatteryStatusHandler(private val context: Context) : ServerPacketHandler {
    companion object {
        private const val TAG = "BatteryStatusHandler"
    }

    override fun canHandle(packet: ServerPacket): Boolean {
        return packet is ServerPacket.RequestBattery
    }

    override fun handle(packet: ServerPacket, commandHandler: CommandHandler) {
        if (packet !is ServerPacket.RequestBattery) return

        Log.d(TAG, "Battery status requested")

        try {
            val batteryManager = OculusBatteryManager(context)
            val batteryInfo = batteryManager.getBatteryInfo()

            commandHandler.sendBatteryStatus(
                level = batteryInfo.headsetLevel,
                isCharging = batteryInfo.isCharging
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery status", e)
            commandHandler.sendError("Error getting battery: ${e.message}")
        }
    }
}