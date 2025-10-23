package com.b3n00n.snorlax.handlers.impl

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.b3n00n.snorlax.handlers.IPacketHandler
import com.b3n00n.snorlax.handlers.PacketHandler
import com.b3n00n.snorlax.protocol.MessageOpcode
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.protocol.PacketWriter

/**
 * Handles Shutdown command (0x48): empty payload
 * Responds with ShutdownResponse (0x16): empty payload, then reboots device
 */
@PacketHandler(MessageOpcode.SHUTDOWN)
class ShutdownHandler(private val context: Context) : IPacketHandler {
    companion object {
        private const val TAG = "ShutdownHandler"
    }

    override fun handle(reader: PacketReader, writer: PacketWriter) {
        // No payload to read

        Log.d(TAG, "Shutdown/reboot requested")

        // Send response first (empty payload)
        writer.writeU8(MessageOpcode.SHUTDOWN_RESPONSE.toInt() and 0xFF)
        writer.writeU16(0)

        // Schedule reboot after response is sent
        Thread {
            Thread.sleep(1000)
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                powerManager.reboot(null)
            } catch (e: Exception) {
                Log.e(TAG, "Error rebooting device", e)
            }
        }.start()
    }
}
