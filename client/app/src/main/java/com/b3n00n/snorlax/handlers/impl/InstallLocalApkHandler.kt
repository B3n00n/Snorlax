package com.b3n00n.snorlax.handlers.impl

import android.content.Context
import android.util.Log
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

class InstallLocalApkHandler(private val context: Context) : MessageHandler {
    companion object {
        private const val TAG = "InstallLocalApkHandler"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 10000
        private const val READ_TIMEOUT = 30000
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
                commandHandler.sendResponse(false, "HTTP error: $responseCode")
                return@withContext
            }

            val contentLength = connection.contentLengthLong
            Log.d(TAG, "Content length: $contentLength bytes")

            // Create temporary file in app's private storage
            val tempDir = File(context.filesDir, "temp_apks")
            tempDir.mkdirs()
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
                commandHandler.sendResponse(false, "Downloaded file is empty or missing")
                tempFile.delete()
                return@withContext
            }

            // Verify it's a valid APK
            val packageInfo = context.packageManager.getPackageArchiveInfo(tempFile.absolutePath, 0)
            if (packageInfo == null) {
                commandHandler.sendResponse(false, "Invalid APK file")
                tempFile.delete()
                return@withContext
            }

            val packageName = packageInfo.packageName
            Log.d(TAG, "Valid APK downloaded: $packageName")

            // Install the APK
            withContext(Dispatchers.Main) {
                when (val result = QuestApkInstaller.installApk(context, tempFile)) {
                    is QuestApkInstaller.InstallResult.Success -> {
                        commandHandler.sendResponse(true, "Installation started: $packageName")

                        // Check installation status after a delay
                        delay(10000)
                        checkInstallation(packageName, commandHandler)
                    }
                    is QuestApkInstaller.InstallResult.Error -> {
                        commandHandler.sendResponse(false, result.message)
                    }
                }
            }

            delay(30000)
            tempFile.delete()

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

    private fun checkInstallation(packageName: String, commandHandler: CommandHandler) {
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            commandHandler.sendResponse(true, "$packageName successfully installed!")
        } catch (e: Exception) {
            Log.d(TAG, "$packageName not found after installation")
        }
    }

    fun cleanup() {
        downloadScope.cancel()

        try {
            val tempDir = File(context.filesDir, "temp_apks")
            tempDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up temp files", e)
        }
    }
}