package com.b3n00n.snorlax.handlers.impl

import android.content.Context
import android.content.Intent
import android.util.Log
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.handlers.ServerPacketHandler
import com.b3n00n.snorlax.protocol.ServerPacket

class GetInstalledAppsHandler(private val context: Context) : ServerPacketHandler {
    companion object {
        private const val TAG = "GetInstalledAppsHandler"
    }

    override fun canHandle(packet: ServerPacket): Boolean {
        return packet is ServerPacket.RequestInstalledApps
    }

    override fun handle(packet: ServerPacket, commandHandler: CommandHandler) {
        if (packet !is ServerPacket.RequestInstalledApps) return

        Log.d(TAG, "Getting installed apps")

        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val appList = context.packageManager
                .queryIntentActivities(mainIntent, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .sorted()

            Log.d(TAG, "Found ${appList.size} installed apps")
            commandHandler.sendInstalledAppsResponse(appList)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed apps", e)
            commandHandler.sendError("Error getting apps: ${e.message}")
        }
    }
}