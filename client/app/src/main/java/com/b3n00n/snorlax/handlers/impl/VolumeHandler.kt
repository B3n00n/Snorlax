package com.b3n00n.snorlax.handlers.impl

import android.content.Context
import android.util.Log
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.handlers.ServerPacketHandler
import com.b3n00n.snorlax.protocol.ServerPacket
import com.b3n00n.snorlax.utils.VolumeManager

private const val TAG = "VolumeHandler"

class SetVolumeHandler(private val context: Context) : ServerPacketHandler {
    override fun canHandle(packet: ServerPacket): Boolean {
        return packet is ServerPacket.SetVolume
    }

    override fun handle(packet: ServerPacket, commandHandler: CommandHandler) {
        if (packet !is ServerPacket.SetVolume) return

        Log.d(TAG, "Setting volume to ${packet.level}%")

        try {
            val volumeManager = VolumeManager(context)
            val success = volumeManager.setVolume(packet.level)

            if (success) {
                val volumeInfo = volumeManager.getVolumeInfo()
                commandHandler.sendVolumeSetResponse(true, volumeInfo.volumePercentage)

                // Also send updated volume status
                commandHandler.sendVolumeStatus(
                    volumeInfo.volumePercentage,
                    volumeInfo.currentVolume,
                    volumeInfo.maxVolume
                )
            } else {
                commandHandler.sendVolumeSetResponse(false, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
            commandHandler.sendVolumeSetResponse(false, 0)
        }
    }
}

class GetVolumeHandler(private val context: Context) : ServerPacketHandler {
    override fun canHandle(packet: ServerPacket): Boolean {
        return packet is ServerPacket.GetVolume
    }

    override fun handle(packet: ServerPacket, commandHandler: CommandHandler) {
        if (packet !is ServerPacket.GetVolume) return

        Log.d(TAG, "Getting volume status")

        try {
            val volumeManager = VolumeManager(context)
            val volumeInfo = volumeManager.getVolumeInfo()

            commandHandler.sendVolumeStatus(
                volumeInfo.volumePercentage,
                volumeInfo.currentVolume,
                volumeInfo.maxVolume
            )

            Log.d(TAG, "Sent volume status: ${volumeInfo.volumePercentage}%")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting volume", e)
            commandHandler.sendError("Error getting volume: ${e.message}")
        }
    }
}