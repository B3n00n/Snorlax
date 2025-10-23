package com.b3n00n.snorlax.handlers.impl

import android.content.Context
import android.content.Intent
import android.util.Log
import com.b3n00n.snorlax.handlers.IPacketHandler
import com.b3n00n.snorlax.handlers.PacketHandler
import com.b3n00n.snorlax.protocol.MessageOpcode
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.protocol.PacketWriter

/**
 * Handles LaunchApp command (0x40): [package_name: String]
 * Responds with LaunchAppResponse (0x10): [success: bool][message: String]
 */
@PacketHandler(MessageOpcode.LAUNCH_APP)
class LaunchAppHandler(private val context: Context) : IPacketHandler {
    companion object {
        private const val TAG = "LaunchAppHandler"
    }

    override fun handle(reader: PacketReader, writer: PacketWriter) {
        val packageName = reader.readString()

        Log.d(TAG, "Launching: $packageName")

        var success = false
        var message = "Failed to launch"

        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                success = true
                message = "Launched $packageName"
            } else {
                message = "No launch intent for $packageName"
            }
        } catch (e: Exception) {
            message = "Error: ${e.message}"
            Log.e(TAG, "Error launching app", e)
        }

        // Build payload
        val payload = PacketWriter()
        payload.writeU8(if (success) 1 else 0)
        payload.writeString(message)

        // Write response packet
        writer.writeU8(MessageOpcode.LAUNCH_APP_RESPONSE.toInt() and 0xFF)
        writer.writeU16(payload.toByteArray().size)
        writer.writeBytes(payload.toByteArray())
    }
}
