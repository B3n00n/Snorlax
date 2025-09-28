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
import com.b3n00n.snorlax.utils.QuestApkInstaller
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class InstallLocalApkHandler(private val context: Context) : MessageHandler {
    companion object {
        private const val TAG = "InstallLocalApkHandler"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 10000
        private const val READ_TIMEOUT = 30000
        private const val UNINSTALL_TIMEOUT_MS = 10000L
    }

    override val messageType: Byte = MessageType.INSTALL_LOCAL_APK
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun handle(reader: PacketReader, commandHandler: CommandHandler) {
        val localUrl = reader.readString()
        Log.d(TAG, "Installing local APK from: $localUrl")

        commandHandler.sendResponse(true, "Starting local APK download...")

        downloadScope.launch {
            downloadAndInstallLocalApk(localUrl, commandHandler)
        }
    }

    private suspend fun downloadAndInstallLocalApk(
        localUrl: String,
        commandHandler: CommandHandler
    ) = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var outputStream: FileOutputStream? = null

        try {
            val fileName = localUrl.substringAfterLast("/").substringBefore("?")
                .takeIf { it.endsWith(".apk", true) }
                ?: "local_download_${System.currentTimeMillis()}.apk"

            Log.d(TAG, "Downloading to: $fileName")

            val url = URL(localUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Snorlax Local APK Installer")
                useCaches = false
                defaultUseCaches = false
            }

            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                withContext(Dispatchers.Main) {
                    commandHandler.sendResponse(false, "HTTP error: $responseCode")
                }
                return@withContext
            }

            val contentLength = connection.contentLengthLong
            Log.d(TAG, "Content length: $contentLength bytes")

            // Create temporary file in app's private storage
            val tempDir = File(context.filesDir, "temp_apks")
            tempDir.mkdirs()

            // Clean up old temp files first
            tempDir.listFiles()?.forEach {
                if (it.name.endsWith(".apk") && it.lastModified() < System.currentTimeMillis() - 3600000) {
                    it.delete()
                }
            }

            val tempFile = File(tempDir, fileName)

            outputStream = FileOutputStream(tempFile)
            val inputStream = connection.inputStream
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalBytesRead = 0L
            var lastProgressLog = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                // Log progress every 10%
                if (contentLength > 0) {
                    val progress = (totalBytesRead * 100 / contentLength)
                    if (progress >= lastProgressLog + 10) {
                        Log.d(TAG, "Download progress: $progress%")
                        lastProgressLog = progress
                    }
                }
            }

            outputStream.flush()
            Log.d(TAG, "Download completed: $totalBytesRead bytes")

            // Verify the downloaded file
            if (!tempFile.exists() || tempFile.length() == 0L) {
                withContext(Dispatchers.Main) {
                    commandHandler.sendResponse(false, "Downloaded file is empty or missing")
                }
                tempFile.delete()
                return@withContext
            }

            // Verify it's a valid APK and get package info
            val packageInfo = context.packageManager.getPackageArchiveInfo(tempFile.absolutePath, 0)
            if (packageInfo == null) {
                withContext(Dispatchers.Main) {
                    commandHandler.sendResponse(false, "Invalid APK file")
                }
                tempFile.delete()
                return@withContext
            }

            val packageName = packageInfo.packageName
            val versionCode = packageInfo.versionCode
            val versionName = packageInfo.versionName ?: "Unknown"

            Log.d(TAG, "Valid APK downloaded: $packageName v$versionName ($versionCode)")

            // Check if already installed and uninstall if necessary
            val existingVersion = getInstalledVersionCode(packageName)
            if (existingVersion != null) {
                val existingVersionName = getInstalledVersionName(packageName) ?: "Unknown"
                Log.d(TAG, "Found existing installation: $packageName v$existingVersionName (code: $existingVersion)")

                withContext(Dispatchers.Main) {
                    commandHandler.sendResponse(
                        true,
                        "Uninstalling existing $packageName v$existingVersionName..."
                    )
                }

                // Uninstall existing package
                val uninstallSuccess = uninstallPackage(packageName, commandHandler)

                if (uninstallSuccess) {
                    Log.d(TAG, "Successfully uninstalled old version of $packageName")
                    withContext(Dispatchers.Main) {
                        commandHandler.sendResponse(
                            true,
                            "Old version removed. Installing $packageName v$versionName..."
                        )
                    }
                    delay(1000) // Give system a moment to clean up
                } else {
                    Log.w(TAG, "Failed to uninstall old version, proceeding with installation anyway")
                    withContext(Dispatchers.Main) {
                        commandHandler.sendResponse(
                            true,
                            "Proceeding with installation of $packageName v$versionName..."
                        )
                    }
                }
            } else {
                Log.d(TAG, "No existing installation found for $packageName")
                withContext(Dispatchers.Main) {
                    commandHandler.sendResponse(
                        true,
                        "Installing new package: $packageName v$versionName..."
                    )
                }
            }

            // Install the APK using async installation
            Log.d(TAG, "Starting installation...")

            when (val result = QuestApkInstaller.installApkAsync(context, tempFile, autoGrantPermissions = true)) {
                is QuestApkInstaller.InstallResult.Success -> {
                    Log.d(TAG, "Installation completed: ${result.message}")

                    withContext(Dispatchers.Main) {
                        commandHandler.sendResponse(true, result.message)
                    }
                }
                is QuestApkInstaller.InstallResult.Error -> {
                    Log.e(TAG, "Installation failed: ${result.message}")
                    withContext(Dispatchers.Main) {
                        commandHandler.sendResponse(false, "Installation failed: ${result.message}")
                    }
                }
            }

            // Clean up temp file after a delay
            delay(5000)
            try {
                if (tempFile.exists()) {
                    tempFile.delete()
                    Log.d(TAG, "Cleaned up temp file: ${tempFile.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting temp file", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error downloading/installing local APK", e)
            withContext(Dispatchers.Main) {
                commandHandler.sendResponse(false, "Error: ${e.message}")
            }
        } finally {
            try {
                outputStream?.close()
                connection?.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing resources", e)
            }
        }
    }

    private suspend fun uninstallPackage(packageName: String, commandHandler: CommandHandler): Boolean =
        suspendCoroutine { continuation ->
            try {
                val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
                    Log.e(TAG, "Not device owner, cannot uninstall")
                    continuation.resume(false)
                    return@suspendCoroutine
                }

                val sessionId = System.currentTimeMillis().toInt() and 0x7FFFFFFF
                val packageInstaller = context.packageManager.packageInstaller

                // Create receiver for uninstall result
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
                                continuation.resume(true)
                            }
                            else -> {
                                Log.e(TAG, "Uninstall failed for $packageName: $message")
                                continuation.resume(false)
                            }
                        }
                    }
                }

                // Register receiver
                val filter = IntentFilter("com.b3n00n.snorlax.LOCAL_UNINSTALL_$sessionId")
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )

                // Create pending intent
                val intent = Intent("com.b3n00n.snorlax.LOCAL_UNINSTALL_$sessionId").apply {
                    setPackage(context.packageName)
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

                // Start uninstall
                packageInstaller.uninstall(packageName, pendingIntent.intentSender)
                Log.d(TAG, "Uninstall initiated for $packageName")

                // Set timeout
                GlobalScope.launch {
                    delay(UNINSTALL_TIMEOUT_MS)
                    try {
                        context.unregisterReceiver(receiver)
                        Log.w(TAG, "Uninstall timeout for $packageName")
                        continuation.resume(false)
                    } catch (e: Exception) {
                        // Already unregistered
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error uninstalling package: ${e.message}", e)
                continuation.resume(false)
            }
        }

    private fun getInstalledVersionCode(packageName: String): Int? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionCode
        } catch (e: Exception) {
            null
        }
    }

    private fun getInstalledVersionName(packageName: String): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            null
        }
    }

    fun cleanup() {
        downloadScope.cancel()

        // Clean up all temp files
        try {
            val tempDir = File(context.filesDir, "temp_apks")
            tempDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up temp files", e)
        }
    }
}