package com.b3n00n.snorlax.handlers.impl

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.handlers.MessageHandler
import com.b3n00n.snorlax.models.DeviceInfo
import com.b3n00n.snorlax.protocol.MessageType
import com.b3n00n.snorlax.protocol.PacketReader

class PingHandler(private val context: Context) : MessageHandler {
    companion object {
        private const val TAG = "PingHandler"
    }

    override val messageType: Byte = MessageType.PING

    override fun handle(reader: PacketReader, commandHandler: CommandHandler) {
        Log.d(TAG, "Ping received")

        try {
            playPingSound()

            val deviceInfo = DeviceInfo(context)
            val responseMessage = "${deviceInfo.model} (${deviceInfo.serial}) - Beep played!"

            commandHandler.sendResponse(true, responseMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling ping", e)
            commandHandler.sendResponse(false, "Ping error: ${e.message}")
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