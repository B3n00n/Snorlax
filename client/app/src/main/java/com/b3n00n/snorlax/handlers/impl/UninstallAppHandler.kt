package com.b3n00n.snorlax.handlers.impl

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.b3n00n.snorlax.handlers.IPacketHandler
import com.b3n00n.snorlax.handlers.PacketHandler
import com.b3n00n.snorlax.protocol.MessageOpcode
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.protocol.PacketWriter
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Handles UninstallApp command (0x49): [package_name: String]
 * Responds with UninstallAppResponse (0x15): [success: bool][message: String]
 */
@PacketHandler(MessageOpcode.UNINSTALL_APP)
class UninstallAppHandler(private val context: Context) : IPacketHandler {
    companion object {
        private const val TAG = "UninstallAppHandler"
        private const val TIMEOUT_MS = 10000L
    }

    override fun handle(reader: PacketReader, writer: PacketWriter) {
        val packageName = reader.readString()

        Log.d(TAG, "Uninstalling: $packageName")

        // Run uninstall synchronously (blocks until complete or timeout)
        val (success, message) = runBlocking {
            uninstallPackage(packageName)
        }

        // Build payload
        val payload = PacketWriter()
        payload.writeU8(if (success) 1 else 0)
        payload.writeString(message)

        // Write response packet
        writer.writeU8(MessageOpcode.UNINSTALL_APP_RESPONSE.toInt() and 0xFF)
        writer.writeU16(payload.toByteArray().size)
        writer.writeBytes(payload.toByteArray())
    }

    private suspend fun uninstallPackage(packageName: String): Pair<Boolean, String> =
        suspendCoroutine { continuation ->
            try {
                val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
                    continuation.resume(false to "Not device owner")
                    return@suspendCoroutine
                }

                val sessionId = System.currentTimeMillis().toInt() and 0x7FFFFFFF
                val packageInstaller = context.packageManager.packageInstaller

                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""

                        try {
                            context.unregisterReceiver(this)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error unregistering receiver", e)
                        }

                        when (status) {
                            PackageInstaller.STATUS_SUCCESS -> {
                                continuation.resume(true to "Uninstalled $packageName")
                            }
                            else -> {
                                continuation.resume(false to "Failed: $message")
                            }
                        }
                    }
                }

                val filter = IntentFilter("com.b3n00n.snorlax.UNINSTALL_$sessionId")
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

                val intent = Intent("com.b3n00n.snorlax.UNINSTALL_$sessionId").apply {
                    setPackage(context.packageName)
                }

                val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                } else {
                    PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT)
                }

                packageInstaller.uninstall(packageName, pendingIntent.intentSender)

                // Timeout
                GlobalScope.launch {
                    delay(TIMEOUT_MS)
                    try {
                        context.unregisterReceiver(receiver)
                        continuation.resume(false to "Timeout")
                    } catch (e: Exception) {
                        // Already unregistered
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error uninstalling", e)
                continuation.resume(false to "Error: ${e.message}")
            }
        }
}
