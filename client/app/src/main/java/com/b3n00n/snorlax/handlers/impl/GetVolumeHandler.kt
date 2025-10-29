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
 * Handles GetVolume command (0x4B)
 * Responds with VolumeStatus (0x04): [percentage: u8][current: u8][max: u8]
 */
@PacketHandler(MessageOpcode.GET_VOLUME)
class GetVolumeHandler : IPacketHandler {
    companion object {
        private const val TAG = "GetVolumeHandler"
    }

    override fun handle(reader: PacketReader, client: NetworkClient) {
        // No payload to read

        try {
            val context = ClientContext.context
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val percentage = if (max > 0) (current * 100 / max) else 0

            Log.d(TAG, "Volume: $current/$max ($percentage%)")

            // Send response
            client.sendPacket(MessageOpcode.VOLUME_STATUS) {
                writeU8(percentage)
                writeU8(current)
                writeU8(max)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting volume", e)
        }
    }
}
