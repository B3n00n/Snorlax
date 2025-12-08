package com.b3n00n.snorlax.utils

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
import com.b3n00n.snorlax.receivers.DeviceOwnerReceiver
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object QuestApkInstaller {
    private const val TAG = "QuestApkInstaller"
    private const val ACTION_INSTALL_COMPLETE = "com.b3n00n.snorlax.INSTALL_COMPLETE"
    private const val INSTALL_TIMEOUT_MS = 30000L
    private const val UNINSTALL_TIMEOUT_MS = 10000L

    suspend fun installApkAsync(
        context: Context,
        apkFile: File,
        autoGrantPermissions: Boolean = true
    ): InstallResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Attempting to install: ${apkFile.absolutePath}")

        // Verify the APK file
        if (!apkFile.exists()) {
            return@withContext InstallResult.Error("APK file does not exist")
        }

        if (apkFile.length() == 0L) {
            return@withContext InstallResult.Error("APK file is empty")
        }

        // Check if we're device owner
        val devicePolicyManager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val isDeviceOwner = devicePolicyManager.isDeviceOwnerApp(context.packageName)

        if (!isDeviceOwner) {
            return@withContext InstallResult.Error("App is not device owner. Cannot install.")
        }

        val packageInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        val packageName = packageInfo?.packageName

        if (packageName == null) {
            return@withContext InstallResult.Error("Cannot read package name from APK")
        }

        val installResult = installSilentlyAsync(context, apkFile)

        if (installResult is InstallResult.Success && autoGrantPermissions) {
            delay(1000)

            val permissionManager = PermissionManager(context)
            val grantResult = permissionManager.grantBluetoothPermissions(packageName)

            when (grantResult) {
                is PermissionManager.GrantResult.Success -> {
                    Log.d(TAG, "Bluetooth permissions granted: ${grantResult.message}")
                }

                is PermissionManager.GrantResult.PartialSuccess -> {
                    Log.d(TAG, "Some permissions granted: ${grantResult.message}")
                }

                is PermissionManager.GrantResult.Error -> {
                    Log.e(TAG, "Permission grant failed: ${grantResult.message}")
                }
            }
        }

        return@withContext installResult
    }

    private suspend fun installSilentlyAsync(context: Context, apkFile: File): InstallResult =
        suspendCoroutine { continuation ->
            val sessionId = System.currentTimeMillis().toInt() and 0x7FFFFFFF

            // Create a broadcast receiver for this specific installation
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val extraSessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)

                    if (extraSessionId != sessionId) {
                        return // Not our session
                    }

                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""

                    // Unregister this receiver
                    try {
                        context.unregisterReceiver(this)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error unregistering receiver", e)
                    }

                    when (status) {
                        PackageInstaller.STATUS_SUCCESS -> {
                            Log.d(TAG, "Installation successful for session $sessionId")
                            continuation.resume(InstallResult.Success("APK installed successfully"))
                        }
                        PackageInstaller.STATUS_FAILURE_ABORTED -> {
                            Log.e(TAG, "Installation aborted: $message")
                            continuation.resume(InstallResult.Error("Installation aborted: $message"))
                        }
                        PackageInstaller.STATUS_FAILURE_BLOCKED -> {
                            Log.e(TAG, "Installation blocked: $message")
                            continuation.resume(InstallResult.Error("Installation blocked: $message"))
                        }
                        PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                            Log.e(TAG, "Installation conflict: $message")
                            continuation.resume(InstallResult.Error("Installation conflict: $message"))
                        }
                        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                            Log.e(TAG, "Incompatible APK: $message")
                            continuation.resume(InstallResult.Error("Incompatible APK: $message"))
                        }
                        PackageInstaller.STATUS_FAILURE_INVALID -> {
                            Log.e(TAG, "Invalid APK: $message")
                            continuation.resume(InstallResult.Error("Invalid APK: $message"))
                        }
                        PackageInstaller.STATUS_FAILURE_STORAGE -> {
                            Log.e(TAG, "Storage error: $message")
                            continuation.resume(InstallResult.Error("Storage error: $message"))
                        }
                        else -> {
                            Log.e(TAG, "Installation failed with code $status: $message")
                            continuation.resume(InstallResult.Error("Installation failed: $message"))
                        }
                    }
                }
            }

            try {
                // Register the receiver
                val filter = IntentFilter(ACTION_INSTALL_COMPLETE)
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )

                // Perform the installation
                val packageInstaller = context.packageManager.packageInstaller
                val packageName = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)?.packageName

                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(packageName)
                }

                val realSessionId = packageInstaller.createSession(params)
                val session = packageInstaller.openSession(realSessionId)

                // Copy APK to session
                FileInputStream(apkFile).use { input ->
                    session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }

                // Create intent for installation result with our custom action
                val intent = Intent(ACTION_INSTALL_COMPLETE).apply {
                    setPackage(context.packageName)
                    putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
                }

                val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                } else {
                    PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                }

                session.commit(pendingIntent.intentSender)
                session.close()

                Log.d(TAG, "Installation session committed for session ID: $sessionId")

                // Set a timeout
                GlobalScope.launch {
                    delay(INSTALL_TIMEOUT_MS)
                    try {
                        context.unregisterReceiver(receiver)
                        continuation.resume(InstallResult.Error("Installation timeout after ${INSTALL_TIMEOUT_MS/1000} seconds"))
                    } catch (e: Exception) {
                        // Receiver was already unregistered, installation completed
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during installation", e)
                try {
                    context.unregisterReceiver(receiver)
                } catch (ex: Exception) {
                    // Ignore
                }
                continuation.resume(InstallResult.Error("Installation error: ${e.message}"))
            }
        }

    private fun installSilently(context: Context, apkFile: File): InstallResult {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)?.packageName)
        }

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        // Copy APK to session
        FileInputStream(apkFile).use { input ->
            session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                input.copyTo(output)
                session.fsync(output)
            }
        }

        // Create intent for installation result
        val intent = Intent(context, DeviceOwnerReceiver::class.java).apply {
            action = "PACKAGE_INSTALLED"
        }

        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        } else {
            PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        session.commit(pendingIntent.intentSender)
        session.close()

        Log.d(TAG, "Installation session committed for silent install")
        return InstallResult.Success("APK installation started")
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun uninstallApkAsync(
        context: Context,
        packageName: String
    ): UninstallResult = suspendCoroutine { continuation ->
        try {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
                continuation.resume(UninstallResult.Error("App is not device owner. Cannot uninstall."))
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
                            Log.d(TAG, "Uninstall successful for $packageName")
                            continuation.resume(UninstallResult.Success("Uninstalled $packageName"))
                        }
                        else -> {
                            Log.e(TAG, "Uninstall failed: $message")
                            continuation.resume(UninstallResult.Error("Failed: $message"))
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

            // Set timeout
            GlobalScope.launch {
                delay(UNINSTALL_TIMEOUT_MS)
                try {
                    context.unregisterReceiver(receiver)
                    continuation.resume(UninstallResult.Error("Uninstall timeout after ${UNINSTALL_TIMEOUT_MS/1000} seconds"))
                } catch (e: Exception) {
                    // Receiver was already unregistered, uninstall completed
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during uninstall", e)
            continuation.resume(UninstallResult.Error("Uninstall error: ${e.message}"))
        }
    }

    sealed class InstallResult {
        data class Success(val message: String) : InstallResult()
        data class Error(val message: String) : InstallResult()
    }

    sealed class UninstallResult {
        data class Success(val message: String) : UninstallResult()
        data class Error(val message: String) : UninstallResult()
    }
}