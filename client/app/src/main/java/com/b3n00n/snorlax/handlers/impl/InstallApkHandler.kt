package com.b3n00n.snorlax.handlers.impl

import android.util.Log
import com.b3n00n.snorlax.core.BackgroundJobs
import com.b3n00n.snorlax.core.ClientContext
import com.b3n00n.snorlax.handlers.IPacketHandler
import com.b3n00n.snorlax.handlers.PacketHandler
import com.b3n00n.snorlax.network.NetworkClient
import com.b3n00n.snorlax.protocol.MessageOpcode
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.utils.QuestApkInstaller
import kotlinx.coroutines.async
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.delay

/**
 * Handles InstallApk command (0x46): [url: String]
 * Responds with ApkInstallResponse (0x14): [success: bool][message: String]
 * Downloads APK from URL and installs it
 */
@PacketHandler(MessageOpcode.INSTALL_APK)
class InstallApkHandler : IPacketHandler {
    companion object {
        private const val TAG = "InstallApkHandler"
    }

    override fun handle(reader: PacketReader, client: NetworkClient) {
        val url = reader.readString()

        Log.d(TAG, "Installing APK from: $url")

        // Launch background job for download/install
        // Handler returns immediately, keeping message thread responsive
        BackgroundJobs.submit {
            val operationId = UUID.randomUUID()

            try {
                val (success, message) = downloadAndInstall(url, client, operationId)

                client.sendPacket(MessageOpcode.APK_INSTALL_RESPONSE) {
                    writeU8(if (success) 1 else 0)
                    writeString(message)
                }
                Log.d(TAG, "Sent install response: success=$success")
            } catch (e: Exception) {
                Log.e(TAG, "Error during background installation", e)

                // Send failed progress
                sendProgress(client, operationId, stage = 3, percentage = 0f, MessageOpcode.APK_DOWNLOAD_PROGRESS)

                client.sendPacket(MessageOpcode.APK_INSTALL_RESPONSE) {
                    writeU8(0)
                    writeString("Installation error: ${e.message}")
                }
            }
        }
    }

    private suspend fun downloadAndInstall(
        urlString: String,
        client: NetworkClient,
        operationId: UUID
    ): Pair<Boolean, String> {
        try {
            val context = ClientContext.context
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                sendProgress(client, operationId, stage = 3, percentage = 0f, MessageOpcode.APK_DOWNLOAD_PROGRESS)
                return false to "HTTP error: ${connection.responseCode}"
            }

            val totalBytes = connection.contentLength.toLong()
            val tempDir = File(context.filesDir, "temp_apks").apply { mkdirs() }
            val tempFile = File(tempDir, "download_${System.currentTimeMillis()}.apk")

            // Send download started
            sendProgress(client, operationId, stage = 0, percentage = 0f, MessageOpcode.APK_DOWNLOAD_PROGRESS)

            // Download with progress tracking
            var bytesDownloaded = 0L
            FileOutputStream(tempFile).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastProgressTime = System.currentTimeMillis()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        val now = System.currentTimeMillis()
                        // Send progress every 500ms
                        if (now - lastProgressTime > 500 || bytesDownloaded == totalBytes) {
                            val percentage = if (totalBytes > 0) {
                                (bytesDownloaded.toFloat() / totalBytes.toFloat()) * 100f
                            } else 0f

                            sendProgress(client, operationId, stage = 1, percentage, MessageOpcode.APK_DOWNLOAD_PROGRESS)
                            lastProgressTime = now
                        }
                    }
                }
            }

            connection.disconnect()

            // Send download completed
            sendProgress(client, operationId, stage = 2, percentage = 100f, MessageOpcode.APK_DOWNLOAD_PROGRESS)

            // Small delay before installation
            delay(100)

            // Send install started
            sendProgress(client, operationId, stage = 0, percentage = 0f, MessageOpcode.APK_INSTALL_PROGRESS)

            // Install with progress simulation
            val installResult = installApkWithProgress(context, tempFile, client, operationId)

            return when (installResult) {
                is QuestApkInstaller.InstallResult.Success -> {
                    sendProgress(client, operationId, stage = 2, percentage = 100f, MessageOpcode.APK_INSTALL_PROGRESS)
                    tempFile.delete()
                    true to installResult.message
                }
                is QuestApkInstaller.InstallResult.Error -> {
                    sendProgress(client, operationId, stage = 3, percentage = 0f, MessageOpcode.APK_INSTALL_PROGRESS)
                    tempFile.delete()
                    false to installResult.message
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
            return false to "Error: ${e.message}"
        }
    }

    private suspend fun installApkWithProgress(
        context: android.content.Context,
        apkFile: File,
        client: NetworkClient,
        operationId: UUID
    ): QuestApkInstaller.InstallResult {
        // Launch installation
        val installJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async {
            QuestApkInstaller.installApkAsync(context, apkFile, autoGrantPermissions = true)
        }

        // Simulate progress while waiting (installation progress is hard to track accurately)
        var progress = 0f
        while (!installJob.isCompleted && progress < 90f) {
            delay(200)
            progress += 10f
            sendProgress(client, operationId, stage = 1, percentage = progress, MessageOpcode.APK_INSTALL_PROGRESS)
        }

        return installJob.await()
    }

    private fun sendProgress(
        client: NetworkClient,
        operationId: UUID,
        stage: Int,
        percentage: Float,
        opcode: Byte
    ) {
        client.sendPacket(opcode) {
            writeUUID(operationId)
            writeU8(stage) // 0=Started, 1=InProgress, 2=Completed, 3=Failed
            writeFloat(percentage)
        }
    }
}
