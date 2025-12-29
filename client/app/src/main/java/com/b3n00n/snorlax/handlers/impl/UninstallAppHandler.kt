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

/**
 * Handles UninstallApp command (0x49): [package_name: String]
 * Responds with UninstallAppResponse (0x15): [success: bool][message: String]
 */
@PacketHandler(MessageOpcode.UNINSTALL_APP)
class UninstallAppHandler : IPacketHandler {
    companion object {
        private const val TAG = "UninstallAppHandler"
    }

    override fun handle(reader: PacketReader, client: NetworkClient) {
        val packageName = reader.readString()

        Log.d(TAG, "Uninstalling: $packageName")

        // Submit background job for async uninstall
        BackgroundJobs.submit {
            val context = ClientContext.context
            val result = QuestApkInstaller.uninstallApkAsync(context, packageName)

            val (success, message) = when (result) {
                is QuestApkInstaller.UninstallResult.Success -> true to result.message
                is QuestApkInstaller.UninstallResult.Error -> false to result.message
            }

            // Send response
            client.sendPacket(MessageOpcode.UNINSTALL_APP_RESPONSE) {
                writeU8(if (success) 1 else 0)
                writeString(message)

                if (success) SoundManager.play(R.raw.uninstall_sound)
                else SoundManager.play(R.raw.error01_sound)
            }
        }
    }
}
