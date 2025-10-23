package com.b3n00n.snorlax.handlers.impl

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.b3n00n.snorlax.handlers.IPacketHandler
import com.b3n00n.snorlax.handlers.PacketHandler
import com.b3n00n.snorlax.protocol.MessageOpcode
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.protocol.PacketWriter

/**
 * Handles GetVolume command (0x4B)
 * Responds with VolumeStatus (0x04): [percentage: u8][current: u8][max: u8]
 */
@PacketHandler(MessageOpcode.GET_VOLUME)
class GetVolumeHandler(private val context: Context) : IPacketHandler {
    companion object {
        private const val TAG = "GetVolumeHandler"
    }

    override fun handle(reader: PacketReader, writer: PacketWriter) {
        // No payload to read

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val percentage = if (max > 0) (current * 100 / max) else 0

            Log.d(TAG, "Volume: $current/$max ($percentage%)")

            // Build payload
            val payload = PacketWriter()
            payload.writeU8(percentage)
            payload.writeU8(current)
            payload.writeU8(max)

            // Write response packet
            writer.writeU8(MessageOpcode.VOLUME_STATUS.toInt() and 0xFF)
            writer.writeU16(payload.toByteArray().size)
            writer.writeBytes(payload.toByteArray())
        } catch (e: Exception) {
            Log.e(TAG, "Error getting volume", e)
        }
    }
}
