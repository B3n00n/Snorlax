package com.b3n00n.snorlax.handlers.impl

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.handlers.ServerPacketHandler
import com.b3n00n.snorlax.protocol.ServerPacket

class PingHandler(private val context: Context) : ServerPacketHandler {
    companion object {
        private const val TAG = "PingHandler"
    }

    override fun canHandle(packet: ServerPacket): Boolean {
        return packet is ServerPacket.Ping
    }

    override fun handle(packet: ServerPacket, commandHandler: CommandHandler) {
        if (packet !is ServerPacket.Ping) return

        Log.d(TAG, "Received ping with timestamp: ${packet.timestamp}")

        try {
            playPingSound()
            // Echo back the timestamp
            commandHandler.sendPingResponse(packet.timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling ping", e)
            commandHandler.sendPingResponse(packet.timestamp)
        }
    }

    private fun playPingSound() {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 500)

            Thread {
                Thread.sleep(1000)
                toneGenerator.release()
            }.start()

            Log.d(TAG, "Ping tone played successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play ping sound: ${e.message}")
        }
    }
}