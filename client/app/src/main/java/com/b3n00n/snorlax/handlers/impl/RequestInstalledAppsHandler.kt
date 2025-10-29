package com.b3n00n.snorlax.handlers.impl

import android.content.Intent
import android.util.Log
import com.b3n00n.snorlax.core.ClientContext
import com.b3n00n.snorlax.handlers.IPacketHandler
import com.b3n00n.snorlax.handlers.PacketHandler
import com.b3n00n.snorlax.network.NetworkClient
import com.b3n00n.snorlax.protocol.MessageOpcode
import com.b3n00n.snorlax.protocol.PacketReader

/**
 * Handles RequestInstalledApps command (0x43): empty payload
 * Responds with InstalledAppsResponse (0x12): [count: u32][apps: Vec<String>]
 */
@PacketHandler(MessageOpcode.REQUEST_INSTALLED_APPS)
class RequestInstalledAppsHandler : IPacketHandler {
    companion object {
        private const val TAG = "RequestInstalledAppsHandler"
    }

    override fun handle(reader: PacketReader, client: NetworkClient) {
        // No payload to read

        Log.d(TAG, "Requesting installed apps")

        val apps = try {
            val context = ClientContext.context
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

        // Send response
        client.sendPacket(MessageOpcode.INSTALLED_APPS_RESPONSE) {
            writeU32(apps.size.toLong())
            for (app in apps) {
                writeString(app)
            }
        }
    }
}
