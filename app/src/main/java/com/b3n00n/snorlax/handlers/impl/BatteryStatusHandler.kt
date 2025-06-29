package com.b3n00n.snorlax.handlers.impl

import android.content.Context
import android.util.Log
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.handlers.MessageHandler
import com.b3n00n.snorlax.protocol.MessageType
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.utils.OculusBatteryManager

class BatteryStatusHandler(private val context: Context) : MessageHandler {
    companion object {
        private const val TAG = "BatteryStatusHandler"
    }

    override val messageType: Byte = MessageType.REQUEST_BATTERY

    override fun handle(reader: PacketReader, commandHandler: CommandHandler) {
        Log.d(TAG, "Getting battery status")

        try {
            val batteryManager = OculusBatteryManager(context)
            val batteryInfo = batteryManager.getBatteryInfo()

            commandHandler.sendBatteryStatus(
                batteryInfo.headsetLevel,
                batteryInfo.leftControllerLevel,
                batteryInfo.rightControllerLevel,
                batteryInfo.isCharging
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery status", e)
            commandHandler.sendError("Error getting battery status: ${e.message}")
        }
    }
}