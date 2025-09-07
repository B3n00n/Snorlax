package com.b3n00n.snorlax.utils

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.b3n00n.snorlax.receivers.DeviceOwnerReceiver
import java.io.File
import java.io.FileInputStream

object QuestApkInstaller {
    private const val TAG = "QuestApkInstaller"

    fun installApk(context: Context, apkFile: File): InstallResult {
        Log.d(TAG, "Attempting to install: ${apkFile.absolutePath}")

        // Verify the APK file
        if (!apkFile.exists()) {
            return InstallResult.Error("APK file does not exist")
        }

        if (apkFile.length() == 0L) {
            return InstallResult.Error("APK file is empty")
        }

        // Check if we're device owner
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val isDeviceOwner = devicePolicyManager.isDeviceOwnerApp(context.packageName)

        if (!isDeviceOwner) {
            return InstallResult.Error("App is not device owner. Cannot install.")
        }

        return try {
            installSilently(context, apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Silent installation failed", e)
            InstallResult.Error("Installation failed: ${e.message}")
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
        val intent = android.content.Intent(context, DeviceOwnerReceiver::class.java).apply {
            action = "PACKAGE_INSTALLED"
        }

        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.app.PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
            )
        } else {
            android.app.PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        session.commit(pendingIntent.intentSender)
        session.close()

        Log.d(TAG, "Installation session committed for silent install")
        return InstallResult.Success("APK installation started")
    }

    sealed class InstallResult {
        data class Success(val message: String) : InstallResult()
        data class Error(val message: String) : InstallResult()
    }
}