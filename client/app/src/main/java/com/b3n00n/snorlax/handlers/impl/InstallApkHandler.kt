package com.b3n00n.snorlax.handlers.impl

import android.content.Context
import android.util.Log
import com.b3n00n.snorlax.handlers.IPacketHandler
import com.b3n00n.snorlax.handlers.PacketHandler
import com.b3n00n.snorlax.protocol.MessageOpcode
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.protocol.PacketWriter
import com.b3n00n.snorlax.utils.QuestApkInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles InstallApk command (0x46): [url: String]
 * Responds with ApkInstallResponse (0x14): [success: bool][message: String]
 * Downloads APK from URL and installs it
 */
@PacketHandler(MessageOpcode.INSTALL_APK)
class InstallApkHandler(private val context: Context) : IPacketHandler {
    companion object {
        private const val TAG = "InstallApkHandler"
    }

    override fun handle(reader: PacketReader, writer: PacketWriter) {
        val url = reader.readString()

        Log.d(TAG, "Installing APK from: $url")

        // Download and install synchronously (blocks until complete)
        val (success, message) = runBlocking {
            downloadAndInstall(url)
        }

        // Send final response
        sendResponse(writer, success, message)
    }

    private fun sendResponse(writer: PacketWriter, success: Boolean, message: String) {
        val payload = PacketWriter()
        payload.writeU8(if (success) 1 else 0)
        payload.writeString(message)

        writer.writeU8(MessageOpcode.APK_INSTALL_RESPONSE.toInt() and 0xFF)
        writer.writeU16(payload.toByteArray().size)
        writer.writeBytes(payload.toByteArray())
    }

    private suspend fun downloadAndInstall(urlString: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext false to "HTTP error: ${connection.responseCode}"
            }

            val tempDir = File(context.filesDir, "temp_apks").apply { mkdirs() }
            val tempFile = File(tempDir, "download_${System.currentTimeMillis()}.apk")

            FileOutputStream(tempFile).use { output ->
                connection.inputStream.use { input ->
                    input.copyTo(output)
                }
            }

            connection.disconnect()

            // Install
            when (val result = QuestApkInstaller.installApkAsync(context, tempFile, autoGrantPermissions = true)) {
                is QuestApkInstaller.InstallResult.Success -> {
                    tempFile.delete()
                    true to result.message
                }
                is QuestApkInstaller.InstallResult.Error -> {
                    tempFile.delete()
                    false to result.message
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
            false to "Error: ${e.message}"
        }
    }
}
