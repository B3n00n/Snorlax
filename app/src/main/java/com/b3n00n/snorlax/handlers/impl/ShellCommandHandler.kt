package com.b3n00n.snorlax.handlers.impl

import android.util.Log
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.handlers.MessageHandler
import com.b3n00n.snorlax.protocol.MessageType
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.utils.ShellExecutor

class ShellCommandHandler : MessageHandler {
    companion object {
        private const val TAG = "ShellCommandHandler"
    }

    override val messageType: Byte = MessageType.EXECUTE_SHELL

    override fun handle(reader: PacketReader, commandHandler: CommandHandler) {
        val command = reader.readString()
        Log.d(TAG, "Executing shell command: $command")

        try {
            val output = ShellExecutor.execute(command)
            commandHandler.sendResponse(true, output)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing shell command", e)
            commandHandler.sendResponse(false, "Error: ${e.message}")
        }
    }
}