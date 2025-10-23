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
 * Handles SetVolume command (0x4A): [level: u8]
 * Responds with VolumeSetResponse (0x17): [success: bool][actual_level: u8]
 */
@PacketHandler(MessageOpcode.SET_VOLUME)
class SetVolumeHandler(private val context: Context) : IPacketHandler {
    companion object {
        private const val TAG = "SetVolumeHandler"
    }

    override fun handle(reader: PacketReader, writer: PacketWriter) {
        val level = reader.readU8()

        Log.d(TAG, "Setting volume to: $level%")

        var success = false
        var actualPercentage = 0

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = (level * maxVolume / 100).coerceIn(0, maxVolume)

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)

            val actualVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            actualPercentage = if (maxVolume > 0) (actualVolume * 100 / maxVolume) else 0
            success = true

            Log.d(TAG, "Volume set to $actualPercentage%")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
        }

        // Build payload
        val payload = PacketWriter()
        payload.writeU8(if (success) 1 else 0)
        payload.writeU8(actualPercentage)

        // Write response packet
        writer.writeU8(MessageOpcode.VOLUME_SET_RESPONSE.toInt() and 0xFF)
        writer.writeU16(payload.toByteArray().size)
        writer.writeBytes(payload.toByteArray())
    }
}
