package com.b3n00n.snorlax.handlers.impl

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.handlers.MessageHandler
import com.b3n00n.snorlax.protocol.MessageType
import com.b3n00n.snorlax.protocol.PacketReader
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.*
import android.app.DownloadManager

class DownloadAndInstallApkHandler(private val context: Context) : MessageHandler {
    companion object {
        private const val TAG = "DownloadAndInstallApkHandler"
        private const val INSTALL_REQUEST_CODE = 1001
    }

    override val messageType: Byte = MessageType.DOWNLOAD_AND_INSTALL_APK
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun handle(reader: PacketReader, commandHandler: CommandHandler) {
        val apkUrl = reader.readString()
        Log.d(TAG, "Downloading and installing APK from: $apkUrl")

        try {
            downloadAndInstallApk(apkUrl, commandHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading/installing APK", e)
            commandHandler.sendResponse(false, "Error: ${e.message}")
        }
    }

    private fun downloadAndInstallApk(apkUrl: String, commandHandler: CommandHandler) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Extract filename from URL and decode it properly
        val urlFileName = apkUrl.substringAfterLast("/").substringBefore("?")
        val decodedFileName = Uri.decode(urlFileName)

        // Force a safe filename without spaces
        val safeFileName = decodedFileName.replace(" ", "_").replace("%20", "_")
        val fileName = if (safeFileName.endsWith(".apk")) safeFileName else "app_${System.currentTimeMillis()}.apk"

        Log.d(TAG, "URL filename: $urlFileName")
        Log.d(TAG, "Decoded filename: $decodedFileName")
        Log.d(TAG, "Safe filename: $fileName")
        Log.d(TAG, "Full URL: $apkUrl")

        // Clean up any existing downloads with similar names
        cleanupOldDownloads(fileName)

        // Create download request
        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("Downloading $fileName")
            setDescription("Downloading APK for installation")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

            // Set destination with safe filename
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

            // Allow download over mobile data and wifi
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)

            // Set MIME type
            setMimeType("application/vnd.android.package-archive")

            // Add headers to ensure we get the file, not an error page
            addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36")

            // Don't show errors in the notification
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }

        // Enqueue download
        val downloadId = downloadManager.enqueue(request)
        commandHandler.sendResponse(true, "Download started. ID: $downloadId")

        // Monitor download progress
        downloadScope.launch {
            monitorDownload(downloadId, downloadManager, commandHandler, fileName)
        }
    }

    private fun cleanupOldDownloads(baseFileName: String) {
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val baseName = baseFileName.removeSuffix(".apk")

            // Delete old versions of the same file
            downloadDir.listFiles { file ->
                file.name.startsWith(baseName) && file.name.endsWith(".apk")
            }?.forEach { file ->
                Log.d(TAG, "Deleting old download: ${file.name}")
                file.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up old downloads: ${e.message}")
        }
    }

    private suspend fun monitorDownload(
        downloadId: Long,
        downloadManager: DownloadManager,
        commandHandler: CommandHandler,
        fileName: String
    ) {
        var downloading = true
        var lastProgress = -1

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

                            // Get the actual downloaded file URI
                            val localUriIndex = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                            val localUri = it.getString(localUriIndex)
                            Log.d(TAG, "Downloaded file URI: $localUri")

                            // Parse the actual filename from the URI
                            val uri = Uri.parse(localUri)
                            val actualFileName = uri.lastPathSegment
                            Log.d(TAG, "Actual filename from URI: $actualFileName")

                            // Wait a bit to ensure file is fully written
                            delay(1000)

                            // Install the APK using the actual filename
                            withContext(Dispatchers.Main) {
                                if (!actualFileName.isNullOrEmpty()) {
                                    installApk(Uri.decode(actualFileName), commandHandler)
                                } else {
                                    installApk(fileName, commandHandler)
                                }
                            }
                        }

                        DownloadManager.STATUS_FAILED -> {
                            downloading = false
                            val reasonIndex = it.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = it.getInt(reasonIndex)
                            Log.e(TAG, "Download failed with reason: $reason")
                            commandHandler.sendResponse(false, "Download failed. Reason code: $reason")
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
                                    commandHandler.sendResponse(true, "Download progress: $progress%")
                                }
                            }
                        }
                    }
                }
            }

            if (downloading) {
                delay(500) // Check every 500ms
            }
        }
    }

    private fun installApk(fileName: String, commandHandler: CommandHandler) {
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            // Try to find the file with various naming patterns
            val baseFileName = fileName.removeSuffix(".apk")
            val possibleFiles = mutableListOf<File>()

            // Add exact filename
            possibleFiles.add(File(downloadDir, fileName))

            // Add decoded version
            possibleFiles.add(File(downloadDir, Uri.decode(fileName)))

            // Add variations with number suffixes (for duplicate downloads)
            for (i in 1..10) {
                possibleFiles.add(File(downloadDir, "$baseFileName-$i.apk"))
                possibleFiles.add(File(downloadDir, "${Uri.decode(baseFileName)}-$i.apk"))
            }

            var apkFile: File? = null
            for (file in possibleFiles) {
                if (file.exists() && file.length() > 0) {
                    Log.d(TAG, "Found file: ${file.absolutePath} (${file.length()} bytes)")
                    apkFile = file
                    break
                }
            }

            if (apkFile == null) {
                // List all APK files in download directory for debugging
                Log.d(TAG, "APK files in download directory:")
                downloadDir.listFiles { file -> file.name.endsWith(".apk") }?.forEach { file ->
                    Log.d(TAG, "  ${file.name} (${file.length()} bytes)")
                }

                commandHandler.sendResponse(false, "Downloaded file not found. Check logs for available files.")
                return
            }

            Log.d(TAG, "Installing APK: ${apkFile.absolutePath}")

            // Validate the APK file
            if (!isValidApkFile(apkFile)) {
                Log.e(TAG, "Downloaded file is not a valid APK")

                // Log file details for debugging
                logFileDetails(apkFile)

                // Check if it's an HTML error page
                val isHtml = try {
                    apkFile.readText(Charsets.UTF_8).take(100).lowercase().let {
                        it.contains("<!doctype") || it.contains("<html") || it.contains("error") || it.contains("denied")
                    }
                } catch (e: Exception) {
                    false
                }

                if (isHtml) {
                    commandHandler.sendResponse(
                        false,
                        "Download failed: Google Cloud Storage returned an HTML page instead of the APK. " +
                                "Please check: 1) File permissions in GCS, 2) Make sure the file is set to public access, " +
                                "3) Try using https://storage.googleapis.com instead of storage.cloud.google.com"
                    )
                } else {
                    commandHandler.sendResponse(false, "Downloaded file is corrupted or not a valid APK.")
                }

                return
            }

            // Use PackageInstaller API directly
            installUsingPackageInstaller(apkFile, commandHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
            commandHandler.sendResponse(false, "Installation error: ${e.message}")
        }
    }

    private fun isValidApkFile(file: File): Boolean {
        return try {
            // First check if it's an HTML file (common error response)
            file.inputStream().use { input ->
                val header = ByteArray(20)
                val bytesRead = input.read(header)
                if (bytesRead > 0) {
                    val headerStr = String(header, 0, bytesRead).lowercase()
                    if (headerStr.contains("<!doctype") || headerStr.contains("<html") || headerStr.contains("<?xml")) {
                        Log.e(TAG, "Downloaded file appears to be HTML/XML, not an APK")
                        return false
                    }
                }
            }

            // Check if it's a valid ZIP file (APKs are ZIP files)
            val zipFile = java.util.zip.ZipFile(file)
            zipFile.close()

            // Also check for APK magic bytes
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                if (input.read(magic) == 4) {
                    // ZIP magic bytes: PK\x03\x04
                    magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte() &&
                            magic[2] == 0x03.toByte() && magic[3] == 0x04.toByte()
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "APK validation failed", e)
            false
        }
    }

    private fun logFileDetails(file: File) {
        try {
            Log.d(TAG, "File details:")
            Log.d(TAG, "  Path: ${file.absolutePath}")
            Log.d(TAG, "  Size: ${file.length()} bytes")
            Log.d(TAG, "  Readable: ${file.canRead()}")

            // Read first few bytes to check file type
            file.inputStream().use { input ->
                val header = ByteArray(16)
                val bytesRead = input.read(header)
                if (bytesRead > 0) {
                    val headerHex = header.take(bytesRead).joinToString(" ") {
                        String.format("%02X", it)
                    }
                    Log.d(TAG, "  Header bytes: $headerHex")

                    // Try to interpret as ASCII
                    val headerAscii = header.take(bytesRead).map {
                        if (it in 32..126) it.toInt().toChar() else '.'
                    }.joinToString("")
                    Log.d(TAG, "  Header ASCII: $headerAscii")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging file details", e)
        }
    }

    private fun installUsingPackageInstaller(apkFile: File, commandHandler: CommandHandler) {
        try {
            // First, try to extract the package name from the APK
            val packageName = getPackageNameFromApk(apkFile)
            Log.d(TAG, "Extracted package name: $packageName")

            // If we can't get the package name, the APK might be corrupted
            if (packageName == null) {
                Log.w(TAG, "Could not extract package name from APK, file might be corrupted")
                // Still try to install it, the system might handle it better
            }

            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                if (!packageName.isNullOrEmpty()) {
                    setAppPackageName(packageName)
                }
            }

            // Create a new session
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            Log.d(TAG, "Created PackageInstaller session: $sessionId")

            // Copy APK to the session
            session.use { activeSession ->
                val input = FileInputStream(apkFile)
                val output = activeSession.openWrite("base.apk", 0, apkFile.length())

                input.use { fileInput ->
                    output.use { sessionOutput ->
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        while (fileInput.read(buffer).also { bytesRead = it } != -1) {
                            sessionOutput.write(buffer, 0, bytesRead)
                        }
                        activeSession.fsync(sessionOutput)
                    }
                }

                // Create an intent for installation result
                val intent = Intent(context, context::class.java).apply {
                    action = "com.b3n00n.snorlax.INSTALL_RESULT"
                    putExtra("install_session_id", sessionId)
                }

                val pendingIntent = PendingIntent.getActivity(
                    context,
                    INSTALL_REQUEST_CODE,
                    intent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )

                // Commit the session
                activeSession.commit(pendingIntent.intentSender)

                val message = if (!packageName.isNullOrEmpty()) {
                    "Installation initiated for package: $packageName. Please approve the installation prompt."
                } else {
                    "Installation initiated. Please approve the installation prompt. (Warning: Could not verify package name)"
                }
                commandHandler.sendResponse(true, message)

                // Schedule a check to verify installation after a delay
                if (!packageName.isNullOrEmpty()) {
                    downloadScope.launch {
                        delay(10000) // Wait 10 seconds
                        checkInstallationStatus(packageName, commandHandler)
                    }
                }

                // Clean up the downloaded file after successful initiation
                apkFile.delete()
            }

        } catch (e: Exception) {
            Log.e(TAG, "PackageInstaller error", e)

            // Fallback: try using Intent
            try {
                installUsingIntent(apkFile, commandHandler)
            } catch (intentError: Exception) {
                commandHandler.sendResponse(false, "Installation failed: ${e.message}")
            }
        }
    }

    private fun getPackageNameFromApk(apkFile: File): String? {
        return try {
            val packageInfo = context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                0
            )
            val packageName = packageInfo?.packageName
            Log.d(TAG, "Package info: ${packageInfo?.applicationInfo?.loadLabel(context.packageManager)}")
            packageName
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract package name from APK", e)
            null
        }
    }

    private suspend fun checkInstallationStatus(packageName: String, commandHandler: CommandHandler) {
        try {
            // Check if the package is installed
            val isInstalled = isPackageInstalled(packageName)

            if (isInstalled) {
                val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                val appName = appInfo.loadLabel(context.packageManager).toString()

                commandHandler.sendResponse(
                    true,
                    "✅ Installation confirmed! App '$appName' (package: $packageName) is now installed."
                )

                // Get version info
                val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
                val versionName = packageInfo.versionName ?: "Unknown"
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }

                Log.d(TAG, "Installed app details - Name: $appName, Package: $packageName, Version: $versionName ($versionCode)")
            } else {
                Log.d(TAG, "Package $packageName not found after installation attempt")
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

    private fun installUsingIntent(apkFile: File, commandHandler: CommandHandler) {
        try {
            // Extract package name first
            val packageName = getPackageNameFromApk(apkFile)

            // For Oculus Quest, we'll use a different approach
            // Create an intent that opens the file in the system's package installer
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
            }

            // Try to start the installer activity
            try {
                context.startActivity(installIntent)
                val message = if (!packageName.isNullOrEmpty()) {
                    "Installation dialog opened for package: $packageName. Please confirm."
                } else {
                    "Installation dialog opened. Please confirm."
                }
                commandHandler.sendResponse(true, message)

                // Schedule installation check
                if (!packageName.isNullOrEmpty()) {
                    downloadScope.launch {
                        delay(10000) // Wait 10 seconds
                        checkInstallationStatus(packageName, commandHandler)
                    }
                }
            } catch (e: Exception) {
                // If ACTION_INSTALL_PACKAGE doesn't work, try ACTION_VIEW
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(viewIntent)
                val message = if (!packageName.isNullOrEmpty()) {
                    "Installation dialog opened via VIEW action. Package: $packageName"
                } else {
                    "Installation dialog opened via VIEW action."
                }
                commandHandler.sendResponse(true, message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Intent installation failed", e)
            commandHandler.sendResponse(
                false,
                "Automatic installation failed. Please install manually from: ${apkFile.absolutePath}"
            )
        }
    }
}