package com.b3n00n.snorlax.handlers.impl

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.b3n00n.snorlax.handlers.IPacketHandler
import com.b3n00n.snorlax.handlers.PacketHandler
import com.b3n00n.snorlax.network.NetworkClient
import com.b3n00n.snorlax.protocol.MessageOpcode
import com.b3n00n.snorlax.protocol.PacketReader

/**
 * Handles Ping command (0x45): [timestamp: u64]
 * Responds with PingResponse (0x13): [timestamp: u64] (echoes back)
 */
@PacketHandler(MessageOpcode.PING)
class PingHandler : IPacketHandler {
    companion object {
        private const val TAG = "PingHandler"
    }

    override fun handle(reader: PacketReader, client: NetworkClient) {
        val timestamp = reader.readU64()

        Log.d(TAG, "Ping received: $timestamp")

        // Play notification sound asynchronously to avoid blocking
        try {
            Thread {
                try {
                    val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    Thread.sleep(200) // Wait for tone to finish
                    toneGen.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in tone thread", e)
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing tone", e)
        }

        // Send response (echo timestamp)
        client.sendPacket(MessageOpcode.PING_RESPONSE) {
            writeU64(timestamp)
        }
    }
}
