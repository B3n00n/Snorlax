package com.b3n00n.snorlax.handlers.impl

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.b3n00n.snorlax.core.BackgroundJobs
import com.b3n00n.snorlax.handlers.IPacketHandler
import com.b3n00n.snorlax.handlers.PacketHandler
import com.b3n00n.snorlax.network.NetworkClient
import com.b3n00n.snorlax.protocol.MessageOpcode
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.receivers.DeviceOwnerReceiver
import kotlinx.coroutines.delay

/**
 * Handles Shutdown command (0x48): empty payload
 * No response - device reboots immediately
 */
@PacketHandler(MessageOpcode.SHUTDOWN)
class ShutdownHandler(private val context: Context) : IPacketHandler {
    companion object {
        private const val TAG = "ShutdownHandler"
    }

    override fun handle(reader: PacketReader, client: NetworkClient) {
        // No payload to read

        Log.d(TAG, "Shutdown/reboot requested")

        BackgroundJobs.submit {
            delay(500)
            try {
                val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(context, DeviceOwnerReceiver::class.java)

                // Check if the app is device owner
                if (devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
                    Log.d(TAG, "Rebooting device using DevicePolicyManager")
                    devicePolicyManager.reboot(adminComponent)
                } else {
                    Log.e(TAG, "App is not device owner, cannot reboot device")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rebooting device", e)
            }
        }
    }
}
