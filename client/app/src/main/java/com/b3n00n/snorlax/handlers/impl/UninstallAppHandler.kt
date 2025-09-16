package com.b3n00n.snorlax.handlers.impl

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.handlers.MessageHandler
import com.b3n00n.snorlax.protocol.MessageType
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.receivers.DeviceOwnerReceiver

class UninstallAppHandler(private val context: Context) : MessageHandler {
    companion object {
        private const val TAG = "UninstallAppHandler"
    }

    override val messageType: Byte = MessageType.UNINSTALL_APP

    override fun handle(reader: PacketReader, commandHandler: CommandHandler) {
        val packageName = reader.readString()
        Log.d(TAG, "Uninstalling app: $packageName")

        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        // Check if we're device owner
        if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
            commandHandler.sendResponse(false, "App is not device owner. Cannot uninstall apps.")
            return
        }

        // Don't allow uninstalling ourselves
        if (packageName == context.packageName) {
            commandHandler.sendResponse(false, "Cannot uninstall Snorlax itself!")
            return
        }

        try {
            // Check if package exists
            val packageInfo = try {
                context.packageManager.getPackageInfo(packageName, 0)
            } catch (e: Exception) {
                commandHandler.sendResponse(false, "Package not found: $packageName")
                return
            }

            // Use PackageInstaller for silent uninstall
            uninstallSilently(packageName, commandHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Error uninstalling app", e)
            commandHandler.sendResponse(false, "Failed to uninstall: ${e.message}")
        }
    }

    private fun uninstallSilently(packageName: String, commandHandler: CommandHandler) {
        try {
            val packageInstaller = context.packageManager.packageInstaller

            // Create intent for uninstall result
            val intent = android.content.Intent(context, DeviceOwnerReceiver::class.java).apply {
                action = "PACKAGE_UNINSTALLED"
                putExtra("package_name", packageName)
            }

            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                packageName.hashCode(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
            )

            // Uninstall the package
            packageInstaller.uninstall(packageName, pendingIntent.intentSender)

            Log.d(TAG, "Uninstall initiated for $packageName")
            commandHandler.sendResponse(true, "Uninstalling $packageName...")

        } catch (e: Exception) {
            Log.e(TAG, "Silent uninstall failed", e)
            throw e
        }
    }
}