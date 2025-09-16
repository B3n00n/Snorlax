package com.b3n00n.snorlax.handlers.impl

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.handlers.MessageHandler
import com.b3n00n.snorlax.protocol.MessageType
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.receivers.DeviceOwnerReceiver

class ShutdownHandler(private val context: Context) : MessageHandler {
    companion object {
        private const val TAG = "ShutdownHandler"
    }

    override val messageType: Byte = MessageType.SHUTDOWN_DEVICE

    override fun handle(reader: PacketReader, commandHandler: CommandHandler) {
        val action = reader.readString() // "shutdown" or "restart"
        Log.d(TAG, "Received $action command")

        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, DeviceOwnerReceiver::class.java)

        // Check if we're device owner
        if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
            commandHandler.sendResponse(false, "App is not device owner. Cannot $action device.")
            return
        }

        try {
            when (action) {
                "shutdown" -> {
                    commandHandler.sendResponse(true, "Shutting down device...")
                    Thread.sleep(1000)
                    devicePolicyManager.reboot(adminComponent)
                }
                "restart" -> {
                    commandHandler.sendResponse(true, "Restarting device...")
                    Thread.sleep(1000)
                    devicePolicyManager.reboot(adminComponent)
                }
                else -> {
                    commandHandler.sendResponse(false, "Unknown action: $action. Use 'shutdown' or 'restart'")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $action", e)
            commandHandler.sendResponse(false, "Failed to $action: ${e.message}")
        }
    }
}