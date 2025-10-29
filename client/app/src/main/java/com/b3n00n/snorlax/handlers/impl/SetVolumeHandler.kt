package com.b3n00n.snorlax.handlers.impl

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.b3n00n.snorlax.core.ClientContext
import com.b3n00n.snorlax.handlers.IPacketHandler
import com.b3n00n.snorlax.handlers.PacketHandler
import com.b3n00n.snorlax.network.NetworkClient
import com.b3n00n.snorlax.protocol.MessageOpcode
import com.b3n00n.snorlax.protocol.PacketReader

/**
 * Handles SetVolume command (0x4A): [level: u8]
 * Responds with VolumeSetResponse (0x17): [success: bool][message: String]
 */
@PacketHandler(MessageOpcode.SET_VOLUME)
class SetVolumeHandler : IPacketHandler {
    companion object {
        private const val TAG = "SetVolumeHandler"
    }

    override fun handle(reader: PacketReader, client: NetworkClient) {
        val level = reader.readU8()

        Log.d(TAG, "Setting volume to: $level%")

        val (success, message) = try {
            val context = ClientContext.context
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = (level * maxVolume / 100).coerceIn(0, maxVolume)

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)

            val actualVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val actualPercentage = if (maxVolume > 0) (actualVolume * 100 / maxVolume) else 0

            Log.d(TAG, "Volume set to $actualPercentage%")
            true to "Volume set to $actualPercentage%"
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
            false to "Error: ${e.message}"
        }

        // Send response
        client.sendPacket(MessageOpcode.VOLUME_SET_RESPONSE) {
            writeU8(if (success) 1 else 0)
            writeString(message)
        }
    }
}
