package com.b3n00n.snorlax.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

object QuestApkInstaller {
    private const val TAG = "QuestApkInstaller"

    fun installApk(context: Context, apkFile: File): InstallResult {
        Log.d(TAG, "Attempting to install: ${apkFile.absolutePath}")

        // First, verify the APK file
        if (!apkFile.exists()) {
            return InstallResult.Error("APK file does not exist")
        }

        if (apkFile.length() == 0L) {
            return InstallResult.Error("APK file is empty")
        }

        // Get proper URI using FileProvider
        val apkUri = getApkUri(context, apkFile)
        Log.d(TAG, "APK URI: $apkUri")

        // Try different installation methods in order

        // Method 1: Try using ACTION_INSTALL_PACKAGE (requires REQUEST_INSTALL_PACKAGES permission)
        try {
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = apkUri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
            }

            context.startActivity(installIntent)
            return InstallResult.Success("Installation prompt displayed. Please confirm on headset.")
        } catch (e: Exception) {
            Log.e(TAG, "ACTION_INSTALL_PACKAGE failed", e)
        }

        // Method 2: Fallback to ACTION_VIEW
        try {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            context.startActivity(viewIntent)
            return InstallResult.Success("Installation viewer opened. Please confirm installation.")
        } catch (e: Exception) {
            Log.e(TAG, "ACTION_VIEW failed", e)
        }

        // Method 3: Try Package Installer directly
        try {
            val installerIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                setPackage("com.android.packageinstaller")
            }

            context.startActivity(installerIntent)
            return InstallResult.Success("Package installer opened. Please confirm installation.")
        } catch (e: Exception) {
            Log.e(TAG, "Package installer method failed", e)
        }

        return InstallResult.Error(
            "Could not launch installation dialog. APK saved to: ${apkFile.absolutePath}\n" +
                    "Please check if 'Unknown Sources' is enabled in Quest settings."
        )
    }

    private fun getApkUri(context: Context, apkFile: File): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Use FileProvider for Android N and above
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            // Use file URI for older versions
            Uri.fromFile(apkFile)
        }
    }

    sealed class InstallResult {
        data class Success(val message: String) : InstallResult()
        data class Error(val message: String) : InstallResult()
    }
}