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
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.handlers.MessageHandler
import com.b3n00n.snorlax.protocol.MessageType
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.receivers.DeviceOwnerReceiver
import kotlinx.coroutines.*

class UninstallAppHandler(private val context: Context) : MessageHandler {
    companion object {
        private const val TAG = "UninstallAppHandler"
        private const val ACTION_UNINSTALL_COMPLETE = "com.b3n00n.snorlax.UNINSTALL_COMPLETE"
        private const val UNINSTALL_TIMEOUT_MS = 15000L
    }

    override val messageType: Byte = MessageType.UNINSTALL_APP
    private val uninstallScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

        // Check if package exists
        val packageExists = try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }

        if (!packageExists) {
            commandHandler.sendResponse(false, "Package not found: $packageName")
            return
        }

        // Use coroutine for async uninstall with callback
        uninstallScope.launch {
            uninstallWithCallback(packageName, commandHandler)
        }
    }

    private suspend fun uninstallWithCallback(packageName: String, commandHandler: CommandHandler) = withContext(Dispatchers.IO) {
        val sessionId = packageName.hashCode()
        var receiverRegistered = false

        // Create a broadcast receiver for uninstall completion
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val extraPackage = intent.getStringExtra("package_name")
                val extraSessionId = intent.getIntExtra("session_id", -1)

                if (extraPackage != packageName || extraSessionId != sessionId) {
                    return // Not our uninstall
                }

                val status = intent.getIntExtra("status", PackageInstaller.STATUS_FAILURE)
                val message = intent.getStringExtra("message") ?: ""

                // Unregister this receiver
                try {
                    context.unregisterReceiver(this)
                    receiverRegistered = false
                } catch (e: Exception) {
                    Log.e(TAG, "Error unregistering receiver", e)
                }

                // Send response based on status
                when (status) {
                    PackageInstaller.STATUS_SUCCESS -> {
                        Log.d(TAG, "$packageName uninstalled successfully")
                        commandHandler.sendResponse(true, "$packageName uninstalled successfully")
                    }
                    PackageInstaller.STATUS_FAILURE_ABORTED -> {
                        Log.e(TAG, "Uninstall aborted for $packageName: $message")
                        commandHandler.sendResponse(false, "Uninstall aborted: $message")
                    }
                    else -> {
                        Log.e(TAG, "Failed to uninstall $packageName: $message")
                        commandHandler.sendResponse(false, "Uninstall failed: $message")
                    }
                }
            }
        }

        try {
            // Register the receiver
            val filter = IntentFilter(ACTION_UNINSTALL_COMPLETE)
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true

            // Start immediate response that uninstall is initiated
            commandHandler.sendResponse(true, "Uninstalling $packageName...")

            // Create PackageInstaller session for uninstall
            val packageInstaller = context.packageManager.packageInstaller

            // Create a custom intent for uninstall result
            val intent = Intent(ACTION_UNINSTALL_COMPLETE).apply {
                setPackage(context.packageName)
                putExtra("package_name", packageName)
                putExtra("session_id", sessionId)
            }

            // We need to create a wrapper receiver to capture the actual uninstall result
            val uninstallReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, resultIntent: Intent) {
                    val status = resultIntent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    val statusMessage = resultIntent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""

                    // Forward to our custom receiver
                    val forwardIntent = Intent(ACTION_UNINSTALL_COMPLETE).apply {
                        setPackage(context.packageName)
                        putExtra("package_name", packageName)
                        putExtra("session_id", sessionId)
                        putExtra("status", status)
                        putExtra("message", statusMessage)
                    }
                    context.sendBroadcast(forwardIntent)

                    // Unregister this temporary receiver
                    try {
                        context.unregisterReceiver(this)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error unregistering uninstall receiver", e)
                    }
                }
            }

            // Register the uninstall receiver
            val uninstallFilter = IntentFilter()
            uninstallFilter.addAction("com.b3n00n.snorlax.UNINSTALL_RESULT_$sessionId")
            ContextCompat.registerReceiver(
                context,
                uninstallReceiver,
                uninstallFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            // Create pending intent for the uninstall result
            val resultIntent = Intent("com.b3n00n.snorlax.UNINSTALL_RESULT_$sessionId").apply {
                setPackage(context.packageName)
            }

            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            } else {
                PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            // Perform the uninstall
            packageInstaller.uninstall(packageName, pendingIntent.intentSender)

            Log.d(TAG, "Uninstall initiated for $packageName with session ID: $sessionId")

            // Set a timeout for the uninstall
            GlobalScope.launch {
                delay(UNINSTALL_TIMEOUT_MS)
                if (receiverRegistered) {
                    try {
                        context.unregisterReceiver(receiver)
                        commandHandler.sendResponse(false, "Uninstall timeout for $packageName after ${UNINSTALL_TIMEOUT_MS/1000} seconds")
                    } catch (e: Exception) {
                        // Receiver was already unregistered, uninstall completed
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during uninstall", e)
            if (receiverRegistered) {
                try {
                    context.unregisterReceiver(receiver)
                } catch (ex: Exception) {
                    // Ignore
                }
            }
            commandHandler.sendResponse(false, "Uninstall error: ${e.message}")
        }
    }
}