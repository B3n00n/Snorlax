package com.b3n00n.snorlax.handlers.impl

import android.util.Log
import com.b3n00n.snorlax.handlers.IPacketHandler
import com.b3n00n.snorlax.handlers.PacketHandler
import com.b3n00n.snorlax.network.NetworkClient
import com.b3n00n.snorlax.protocol.MessageOpcode
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.utils.ShellExecutor

/**
 * Handles ExecuteShell command (0x41): [command: String]
 * Responds with ShellExecutionResponse (0x11): [success: bool][output: String][exit_code: i32]
 */
@PacketHandler(MessageOpcode.EXECUTE_SHELL)
class ExecuteShellHandler : IPacketHandler {
    companion object {
        private const val TAG = "ExecuteShellHandler"
    }

    override fun handle(reader: PacketReader, client: NetworkClient) {
        val command = reader.readString()

        Log.d(TAG, "Executing: $command")

        var success = false
        var output = ""
        var exitCode = -1

        try {
            output = ShellExecutor.execute(command)
            success = true
            exitCode = 0
        } catch (e: Exception) {
            output = "Error: ${e.message}"
            Log.e(TAG, "Error executing shell command", e)
        }

        // Send response
        client.sendPacket(MessageOpcode.SHELL_EXECUTION_RESPONSE) {
            writeU8(if (success) 1 else 0)
            writeString(output)
            writeI32(exitCode)
        }
    }
}
