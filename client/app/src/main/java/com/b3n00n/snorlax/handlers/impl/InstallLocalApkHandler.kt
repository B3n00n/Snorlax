package com.b3n00n.snorlax.handlers.impl

import android.util.Log
import com.b3n00n.snorlax.R
import com.b3n00n.snorlax.core.BackgroundJobs
import com.b3n00n.snorlax.core.ClientContext
import com.b3n00n.snorlax.handlers.IPacketHandler
import com.b3n00n.snorlax.handlers.PacketHandler
import com.b3n00n.snorlax.network.NetworkClient
import com.b3n00n.snorlax.protocol.MessageOpcode
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.utils.QuestApkInstaller
import com.b3n00n.snorlax.utils.SoundManager
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles InstallLocalApk command (0x47): [filename: String]
 * Responds with ApkInstallResponse (0x14): [success: bool][message: String]
 * Downloads APK from local server and installs it
 */
@PacketHandler(MessageOpcode.INSTALL_LOCAL_APK)
class InstallLocalApkHandler : IPacketHandler {
    companion object {
        private const val TAG = "InstallLocalApkHandler"
    }

    override fun handle(reader: PacketReader, client: NetworkClient) {
        val filename = reader.readString()

        Log.d(TAG, "Installing local APK: $filename")

        // Send download started notification immediately
        client.sendPacket(MessageOpcode.APK_DOWNLOAD_STARTED) {
            writeString(filename)
        }

        // Launch background job for download/install
        // Handler returns immediately, keeping message thread responsive
        BackgroundJobs.submit {
            try {
                val (success, message) = downloadAndInstall(filename)

                client.sendPacket(MessageOpcode.APK_INSTALL_RESPONSE) {
                    writeU8(if (success) 1 else 0)
                    writeString(message)

                    if (success) SoundManager.play(R.raw.download_start_sound)
                    else SoundManager.play(R.raw.error01_sound)
                }
                Log.d(TAG, "Sent install response: success=$success")
            } catch (e: Exception) {
                Log.e(TAG, "Error during background installation", e)
                client.sendPacket(MessageOpcode.APK_INSTALL_RESPONSE) {
                    writeU8(0)
                    writeString("Installation error: ${e.message}")
                }
            }
        }
    }

    private suspend fun downloadAndInstall(filename: String): Pair<Boolean, String> {
        try {
            val context = ClientContext.context
            // Construct local URL (server address from filename)
            val url = URL(filename)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return false to "HTTP error: ${connection.responseCode}"
            }

            val tempDir = File(context.filesDir, "temp_apks").apply { mkdirs() }
            val tempFile = File(tempDir, "local_${System.currentTimeMillis()}.apk")

            FileOutputStream(tempFile).use { output ->
                connection.inputStream.use { input ->
                    input.copyTo(output)
                }
            }

            connection.disconnect()

            // Install
            return when (val result = QuestApkInstaller.installApkAsync(context, tempFile, autoGrantPermissions = true)) {
                is QuestApkInstaller.InstallResult.Success -> {
                    tempFile.delete()
                    SoundManager.play(R.raw.install_complete_sound)
                    true to result.message
                }
                is QuestApkInstaller.InstallResult.Error -> {
                    tempFile.delete()
                    false to result.message
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error installing local APK", e)
            return false to "Error: ${e.message}"
        }
    }
}
