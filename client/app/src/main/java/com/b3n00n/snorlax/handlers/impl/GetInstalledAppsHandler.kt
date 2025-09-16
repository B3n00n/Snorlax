package com.b3n00n.snorlax.handlers.impl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.handlers.MessageHandler
import com.b3n00n.snorlax.protocol.MessageType
import com.b3n00n.snorlax.protocol.PacketReader

class GetInstalledAppsHandler(private val context: Context) : MessageHandler {
    companion object {
        private const val TAG = "GetInstalledAppsHandler"
    }

    override val messageType: Byte = MessageType.GET_INSTALLED_APPS

    override fun handle(reader: PacketReader, commandHandler: CommandHandler) {
        Log.d(TAG, "Getting installed apps")

        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val appList = context.packageManager
                .queryIntentActivities(mainIntent, 0)
                .joinToString(",") { it.activityInfo.packageName }

            commandHandler.sendResponse(true, appList)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed apps", e)
            commandHandler.sendResponse(false, "Error: ${e.message}")
        }
    }
}