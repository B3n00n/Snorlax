package com.b3n00n.snorlax.handlers.impl

import android.content.Context
import android.content.Intent
import android.util.Log
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.handlers.ServerPacketHandler
import com.b3n00n.snorlax.protocol.ServerPacket

class LaunchAppHandler(private val context: Context) : ServerPacketHandler {
    companion object {
        private const val TAG = "LaunchAppHandler"
    }

    override fun canHandle(packet: ServerPacket): Boolean {
        return packet is ServerPacket.LaunchApp
    }

    override fun handle(packet: ServerPacket, commandHandler: CommandHandler) {
        if (packet !is ServerPacket.LaunchApp) return

        Log.d(TAG, "Launching app: ${packet.packageName}")

        try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packet.packageName)

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                commandHandler.sendLaunchAppResponse(true, "Launched ${packet.packageName}")
            } else {
                commandHandler.sendLaunchAppResponse(false, "App not found: ${packet.packageName}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app", e)
            commandHandler.sendLaunchAppResponse(false, "Error: ${e.message}")
        }
    }
}
