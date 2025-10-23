package com.b3n00n.snorlax.handlers.impl

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.handlers.ServerPacketHandler
import com.b3n00n.snorlax.protocol.ServerPacket
import com.b3n00n.snorlax.receivers.DeviceOwnerReceiver

class ShutdownHandler(private val context: Context) : ServerPacketHandler {
    companion object {
        private const val TAG = "ShutdownHandler"
    }

    override fun canHandle(packet: ServerPacket): Boolean {
        return packet is ServerPacket.Shutdown
    }

    override fun handle(packet: ServerPacket, commandHandler: CommandHandler) {
        if (packet !is ServerPacket.Shutdown) return

        Log.d(TAG, "Shutdown/restart requested")

        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, DeviceOwnerReceiver::class.java)

        // Check if we're device owner
        if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
            commandHandler.sendError("App is not device owner. Cannot reboot device.")
            return
        }

        try {
            // Send response first
            commandHandler.sendShutdownResponse()

            // Give time for response to be sent
            Thread.sleep(1000)

            // Reboot device
            devicePolicyManager.reboot(adminComponent)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing shutdown", e)
            // Already sent response, just log error
        }
    }
}