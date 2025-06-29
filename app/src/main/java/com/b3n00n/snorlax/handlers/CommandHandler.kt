package com.b3n00n.snorlax.handlers

import android.content.Context
import android.util.Log
import com.b3n00n.snorlax.handlers.impl.*
import com.b3n00n.snorlax.network.ConnectionManager
import com.b3n00n.snorlax.protocol.MessageType
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.protocol.PacketWriter
import java.io.ByteArrayInputStream
import java.io.IOException

class CommandHandler(
    val context: Context,
    private val connectionManager: ConnectionManager
) {
    companion object {
        private const val TAG = "CommandHandler"
    }

    private val handlers = mutableMapOf<Byte, MessageHandler>()

    init {
        registerHandlers()
    }

    private fun registerHandlers() {
        registerHandler(LaunchAppHandler(context))
        registerHandler(GetInstalledAppsHandler(context))
        registerHandler(BatteryStatusHandler(context))
        registerHandler(ShellCommandHandler())
    }

    private fun registerHandler(handler: MessageHandler) {
        handlers[handler.messageType] = handler
    }

    fun handleMessage(data: ByteArray) {
        try {
            val inputStream = ByteArrayInputStream(data)
            val reader = PacketReader(inputStream)

            val messageType = reader.readU8().toByte()
            val handler = handlers[messageType]

            if (handler != null) {
                handler.handle(reader, this)
            } else {
                Log.w(TAG, "Unknown message type: $messageType")
                sendError("Unknown message type: $messageType")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error handling message", e)
            sendError("Error handling message: ${e.message}")
        }
    }

    fun sendResponse(success: Boolean, message: String) {
        try {
            val writer = PacketWriter()
            writer.writeU8(MessageType.COMMAND_RESPONSE.toInt())
            writer.writeU8(if (success) 1 else 0)
            writer.writeString(message)
            connectionManager.sendData(writer.toByteArray())
        } catch (e: IOException) {
            Log.e(TAG, "Error sending response", e)
        }
    }

    fun sendError(errorMessage: String) {
        try {
            val writer = PacketWriter()
            writer.writeU8(MessageType.ERROR.toInt())
            writer.writeString(errorMessage)
            connectionManager.sendData(writer.toByteArray())
        } catch (e: IOException) {
            Log.e(TAG, "Error sending error message", e)
        }
    }

    fun sendBatteryStatus(
        headset: Int,
        leftController: Int,
        rightController: Int,
        isCharging: Boolean
    ) {
        try {
            val writer = PacketWriter()
            writer.writeU8(MessageType.BATTERY_STATUS.toInt())
            writer.writeU8(headset)
            writer.writeU8(leftController)
            writer.writeU8(rightController)
            writer.writeU8(if (isCharging) 1 else 0)
            connectionManager.sendData(writer.toByteArray())
        } catch (e: IOException) {
            Log.e(TAG, "Error sending battery status", e)
        }
    }
}