package com.b3n00n.snorlax.handlers.impl

import android.content.Context
import android.util.Log
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.handlers.MessageHandler
import com.b3n00n.snorlax.protocol.MessageType
import com.b3n00n.snorlax.protocol.PacketReader

class ShutdownHandler(private val context: Context) : MessageHandler {
    companion object {
        private const val TAG = "ShutdownHandler"
    }

    override val messageType: Byte = MessageType.SHUTDOWN_DEVICE

    override fun handle(reader: PacketReader, commandHandler: CommandHandler) {
        Log.d(TAG, "Shutdown command received")

        commandHandler.sendResponse(
            false,
            "Shutdown not supported on Quest devices (requires root access)"
        )
    }
}