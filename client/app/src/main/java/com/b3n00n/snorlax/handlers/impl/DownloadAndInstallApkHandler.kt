package com.b3n00n.snorlax.handlers.impl

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.handlers.MessageHandler
import com.b3n00n.snorlax.protocol.MessageType
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.utils.QuestApkInstaller
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class DownloadAndInstallApkHandler(private val context: Context) : MessageHandler {
    companion object {
        private const val TAG = "DownloadAndInstallApkHandler"
        private const val INTERNET_CHECK_TIMEOUT = 5000
    }

    override val messageType: Byte = MessageType.DOWNLOAD_AND_INSTALL_APK
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadReceiver: BroadcastReceiver? = null

    override fun handle(reader: PacketReader, commandHandler: CommandHandler) {
        val apkUrl = reader.readString()
        Log.d(TAG, "Downloading and installing APK from: $apkUrl")

        downloadScope.launch {
            val hasInternet = checkInternetConnection()
            withContext(Dispatchers.Main) {
                if (!hasInternet) {
                    commandHandler.sendResponse(
                        false,
                        "No internet connection available. Cannot download from: $apkUrl"
                    )
                    Log.e(TAG, "Download failed: No internet connection")
                } else {
                    try {
                        downloadAndInstallApk(apkUrl, commandHandler)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error downloading/installing APK", e)
                        commandHandler.sendResponse(false, "Error: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun checkInternetConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://www.google.com")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = INTERNET_CHECK_TIMEOUT
                readTimeout = INTERNET_CHECK_TIMEOUT
                requestMethod = "HEAD"
            }
            connection.connect()
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Internet check failed: ${e.message}")
            false
        }
    }

    private fun downloadAndInstallApk(apkUrl: String, commandHandler: CommandHandler) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val urlFileName = apkUrl.substringAfterLast("/").substringBefore("?")
        val fileName = if (urlFileName.endsWith(".apk")) urlFileName else "download_${System.currentTimeMillis()}.apk"

        Log.d(TAG, "Downloading to filename: $fileName")

        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("Downloading $fileName")
            setDescription("Downloading APK for installation")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

            // Set destination
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

            // Allow download over any network
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)

            // Set MIME type
            setMimeType("application/vnd.android.package-archive")
        }

        // Register broadcast receiver for download completion
        registerDownloadReceiver(commandHandler)

        // Enqueue download
        val downloadId = downloadManager.enqueue(request)
        commandHandler.sendResponse(true, "Download started. ID: $downloadId")

        // Monitor download progress
        downloadScope.launch {
            monitorDownload(downloadId, downloadManager, commandHandler, fileName)
        }
    }

    private fun registerDownloadReceiver(commandHandler: CommandHandler) {
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    Log.d(TAG, "Download completed broadcast received for ID: $downloadId")
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private suspend fun monitorDownload(
        downloadId: Long,
        downloadManager: DownloadManager,
        commandHandler: CommandHandler,
        fileName: String
    ) {
        var lastProgress = -1
        var downloading = true

        while (downloading) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor: Cursor? = downloadManager.query(query)

            cursor?.use {
                if (it.moveToFirst()) {
                    val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = it.getInt(statusIndex)

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            downloading = false
                            Log.d(TAG, "Download completed successfully")

                            // Get the downloaded file path
                            val downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            val apkFile = File(downloadPath, fileName)

                            if (apkFile.exists()) {
                                Log.d(TAG, "APK file found at: ${apkFile.absolutePath}")
                                withContext(Dispatchers.Main) {
                                    installApk(apkFile, commandHandler)
                                }
                            } else {
                                Log.e(TAG, "Downloaded file not found at expected location")
                                commandHandler.sendResponse(false, "Downloaded file not found")
                            }
                        }

                        DownloadManager.STATUS_FAILED -> {
                            downloading = false
                            val reasonIndex = it.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = it.getInt(reasonIndex)

                            val errorMessage = when (reason) {
                                DownloadManager.ERROR_CANNOT_RESUME -> "Download cannot resume"
                                DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage device not found"
                                DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
                                DownloadManager.ERROR_FILE_ERROR -> "File storage error"
                                DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP data error"
                                DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space"
                                DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
                                DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP code"
                                DownloadManager.ERROR_UNKNOWN -> "Unknown error"
                                else -> "Download failed with code: $reason"
                            }

                            Log.e(TAG, "Download failed: $errorMessage")
                            commandHandler.sendResponse(false, "Download failed: $errorMessage")
                        }

                        DownloadManager.STATUS_RUNNING -> {
                            val bytesDownloadedIndex = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val bytesTotalIndex = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                            val bytesDownloaded = it.getLong(bytesDownloadedIndex)
                            val bytesTotal = it.getLong(bytesTotalIndex)

                            if (bytesTotal > 0) {
                                val progress = ((bytesDownloaded * 100) / bytesTotal).toInt()
                                if (progress != lastProgress && progress % 10 == 0) {
                                    lastProgress = progress
                                    Log.d(TAG, "Download progress: $progress%")
                                }
                            }
                        }

                        DownloadManager.STATUS_PAUSED -> {
                            val reasonIndex = it.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = it.getInt(reasonIndex)

                            val pauseReason = when (reason) {
                                DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for WiFi"
                                DownloadManager.PAUSED_UNKNOWN -> "Paused for unknown reason"
                                DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for network"
                                DownloadManager.PAUSED_WAITING_TO_RETRY -> "Waiting to retry"
                                else -> "Paused with code: $reason"
                            }

                            Log.d(TAG, "Download paused: $pauseReason")
                        }
                    }
                }
            }

            if (downloading) {
                delay(1000) // Check every second
            }
        }
    }

    private fun installApk(apkFile: File, commandHandler: CommandHandler) {
        try {
            Log.d(TAG, "Installing APK: ${apkFile.absolutePath}")

            val packageInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            val packageName = packageInfo?.packageName ?: "unknown"
            Log.d(TAG, "Package to install: $packageName")

            when (val result = QuestApkInstaller.installApk(context, apkFile)) {
                is QuestApkInstaller.InstallResult.Success -> {
                    commandHandler.sendResponse(true, result.message)

                    if (packageName != "unknown") {
                        downloadScope.launch {
                            delay(15000)
                            checkInstallationStatus(packageName, commandHandler)
                        }
                    }
                }
                is QuestApkInstaller.InstallResult.Error -> {
                    commandHandler.sendResponse(false, result.message)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
            commandHandler.sendResponse(
                false,
                "Installation failed. APK saved to: ${apkFile.absolutePath}"
            )
        }
    }

    private fun checkInstallationStatus(packageName: String, commandHandler: CommandHandler) {
        try {
            val isInstalled = isPackageInstalled(packageName)

            if (isInstalled) {
                commandHandler.sendResponse(
                    true,
                    "Installation verified! $packageName is now installed."
                )
            } else {
                Log.d(TAG, "$packageName not found after installation attempt")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking installation status", e)
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun cleanup() {
        downloadReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver", e)
            }
        }
        downloadScope.cancel()
    }
}