package com.b3n00n.snorlax.handlers.impl

import android.content.Context
import android.content.Intent
import android.util.Log
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.handlers.MessageHandler
import com.b3n00n.snorlax.protocol.MessageType
import com.b3n00n.snorlax.protocol.PacketReader

class LaunchAppHandler(private val context: Context) : MessageHandler {
    companion object {
        private const val TAG = "LaunchAppHandler"
    }

    override val messageType: Byte = MessageType.LAUNCH_APP

    override fun handle(reader: PacketReader, commandHandler: CommandHandler) {
        val packageName = reader.readString()
        Log.d(TAG, "Launching app: $packageName")

        try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                commandHandler.sendResponse(true, "Launched $packageName")
            } else {
                commandHandler.sendResponse(false, "App not found: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app", e)
            commandHandler.sendResponse(false, "Error: ${e.message}")
        }
    }
}