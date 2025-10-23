package com.b3n00n.snorlax.handlers.impl

import android.util.Log
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.handlers.ServerPacketHandler
import com.b3n00n.snorlax.protocol.ServerPacket
import com.b3n00n.snorlax.utils.ShellExecutor

class ShellCommandHandler : ServerPacketHandler {
    companion object {
        private const val TAG = "ShellCommandHandler"
    }

    override fun canHandle(packet: ServerPacket): Boolean {
        return packet is ServerPacket.ExecuteShell
    }

    override fun handle(packet: ServerPacket, commandHandler: CommandHandler) {
        if (packet !is ServerPacket.ExecuteShell) return

        Log.d(TAG, "Executing shell command: ${packet.command}")

        try {
            val output = ShellExecutor.execute(packet.command)
            commandHandler.sendShellExecutionResponse(
                success = true,
                output = output,
                exitCode = 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing shell command", e)
            commandHandler.sendShellExecutionResponse(
                success = false,
                output = "Error: ${e.message}",
                exitCode = -1
            )
        }
    }
}