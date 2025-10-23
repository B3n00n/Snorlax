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
 * Handles RequestInstalledApps command (0x43): empty payload
 * Responds with InstalledAppsResponse (0x12): [count: u32][apps: Vec<String>]
 */
@PacketHandler(MessageOpcode.REQUEST_INSTALLED_APPS)
class RequestInstalledAppsHandler(private val context: Context) : IPacketHandler {
    companion object {
        private const val TAG = "RequestInstalledAppsHandler"
    }

    override fun handle(reader: PacketReader, writer: PacketWriter) {
        // No payload to read

        Log.d(TAG, "Requesting installed apps")

        val apps = try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            context.packageManager
                .queryIntentActivities(mainIntent, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .sorted()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed apps", e)
            emptyList()
        }

        Log.d(TAG, "Found ${apps.size} apps")

        // Build payload
        val payload = PacketWriter()
        payload.writeU32(apps.size.toLong())
        for (app in apps) {
            payload.writeString(app)
        }

        // Write response packet
        writer.writeU8(MessageOpcode.INSTALLED_APPS_RESPONSE.toInt() and 0xFF)
        writer.writeU16(payload.toByteArray().size)
        writer.writeBytes(payload.toByteArray())
    }
}
