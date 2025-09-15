package com.b3n00n.snorlax.handlers.impl

import android.content.Context
import android.util.Log
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.handlers.MessageHandler
import com.b3n00n.snorlax.protocol.MessageType
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.protocol.PacketWriter
import com.b3n00n.snorlax.utils.VolumeManager

class VolumeHandler(private val context: Context) {
    companion object {
        private const val TAG = "VolumeHandler"
    }

    class SetVolumeHandler(private val context: Context) : MessageHandler {
        override val messageType: Byte = MessageType.SET_VOLUME

        override fun handle(reader: PacketReader, commandHandler: CommandHandler) {
            val volumePercentage = reader.readU8()
            Log.d(TAG, "Setting volume to $volumePercentage%")

            try {
                val volumeManager = VolumeManager(context)
                val success = volumeManager.setVolume(volumePercentage)

                if (success) {
                    commandHandler.sendResponse(true, "Volume set to $volumePercentage%")

                    // Also send back the updated volume status
                    val volumeInfo = volumeManager.getVolumeInfo()
                    sendVolumeStatus(volumeInfo, commandHandler)
                } else {
                    commandHandler.sendResponse(false, "Failed to set volume")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting volume", e)
                commandHandler.sendResponse(false, "Error: ${e.message}")
            }
        }

        private fun sendVolumeStatus(volumeInfo: VolumeManager.VolumeInfo, commandHandler: CommandHandler) {
            val writer = PacketWriter()
            writer.writeU8(MessageType.VOLUME_STATUS.toInt())
            writer.writeU8(volumeInfo.volumePercentage)
            writer.writeU8(volumeInfo.currentVolume)
            writer.writeU8(volumeInfo.maxVolume)
            commandHandler.sendData(writer.toByteArray())
        }
    }

    class GetVolumeHandler(private val context: Context) : MessageHandler {
        override val messageType: Byte = MessageType.GET_VOLUME

        override fun handle(reader: PacketReader, commandHandler: CommandHandler) {
            Log.d(TAG, "Getting volume status")

            try {
                val volumeManager = VolumeManager(context)
                val volumeInfo = volumeManager.getVolumeInfo()

                // Send volume status back to server
                val writer = PacketWriter()
                writer.writeU8(MessageType.VOLUME_STATUS.toInt())
                writer.writeU8(volumeInfo.volumePercentage)
                writer.writeU8(volumeInfo.currentVolume)
                writer.writeU8(volumeInfo.maxVolume)

                commandHandler.sendData(writer.toByteArray())

                Log.d(TAG, "Sent volume status: ${volumeInfo.volumePercentage}%")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting volume", e)
                commandHandler.sendError("Error getting volume: ${e.message}")
            }
        }
    }
}