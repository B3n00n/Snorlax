package com.b3n00n.snorlax.handlers.impl

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.b3n00n.snorlax.core.ClientContext
import com.b3n00n.snorlax.handlers.IPacketHandler
import com.b3n00n.snorlax.handlers.PacketHandler
import com.b3n00n.snorlax.network.NetworkClient
import com.b3n00n.snorlax.protocol.MessageOpcode
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.receivers.DeviceOwnerReceiver

/**
 * Handles CloseAllApps command (0x4C): empty payload
 * Responds with CloseAllAppsResponse (0x18): [success: bool][message: String][closed_count: u32][closed_apps: List<String>]
 */
@PacketHandler(MessageOpcode.CLOSE_ALL_APPS)
class CloseAllAppsHandler : IPacketHandler {
    companion object {
        private const val TAG = "CloseAllAppsHandler"
    }

    override fun handle(reader: PacketReader, client: NetworkClient) {
        // No payload to read

        Log.d(TAG, "Close all apps requested")

        val context = ClientContext.context
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, DeviceOwnerReceiver::class.java)

        val closedApps = mutableListOf<String>()
        val failedApps = mutableListOf<String>()
        var success = false
        var message = ""

        try {
            if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
                message = "Not a device owner - cannot force stop apps"
                Log.e(TAG, message)

                client.sendPacket(MessageOpcode.CLOSE_ALL_APPS_RESPONSE) {
                    writeU8(0)
                    writeString(message)
                    writeU32(0)
                }
                return
            }

            val packageManager = context.packageManager
            val installedPackages = packageManager.getInstalledApplications(0)
                .filter { appInfo ->
                    // Exclude system apps and the Snorlax app itself
                    (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    appInfo.packageName != context.packageName
                }

            Log.d(TAG, "Found ${installedPackages.size} user apps to close")

            for (appInfo in installedPackages) {
                val packageName = appInfo.packageName

                try {
                    devicePolicyManager.setApplicationHidden(adminComponent, packageName, true)
                    devicePolicyManager.setApplicationHidden(adminComponent, packageName, false)

                    closedApps.add(packageName)
                    Log.d(TAG, "Stopped: $packageName")
                } catch (e: Exception) {
                    failedApps.add(packageName)
                    Log.w(TAG, "Failed to stop $packageName: ${e.message}")
                }
            }

            success = closedApps.isNotEmpty()
            message = if (failedApps.isEmpty()) {
                "Successfully closed ${closedApps.size} apps"
            } else {
                "Closed ${closedApps.size} apps, failed ${failedApps.size} apps"
            }
            Log.i(TAG, message)

            if (failedApps.isNotEmpty()) {
                Log.w(TAG, "Failed apps: ${failedApps.joinToString(", ")}")
            }
        } catch (e: Exception) {
            message = "Error: ${e.message}"
            Log.e(TAG, "Error closing apps", e)
        }

        // Send response
        client.sendPacket(MessageOpcode.CLOSE_ALL_APPS_RESPONSE) {
            writeU8(if (success) 1 else 0)
            writeString(message)
            writeU32(closedApps.size.toLong())
            for (packageName in closedApps) {
                writeString(packageName)
            }
        }
    }
}
