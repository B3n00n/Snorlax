package com.b3n00n.snorlax.handlers

import android.content.Context
import android.util.Log
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.protocol.PacketWriter

class HandlerRegistry(private val context: Context) {
    companion object {
        private const val TAG = "HandlerRegistry"
    }

    private val handlers = mutableMapOf<Byte, IPacketHandler>()

    fun register(handler: IPacketHandler) {
        val annotation = handler.javaClass.getAnnotation(PacketHandler::class.java)
        if (annotation != null) {
            val opcode = annotation.opcode
            handlers[opcode] = handler
            Log.d(TAG, "Registered ${handler.javaClass.simpleName} for opcode 0x${String.format("%02X", opcode)}")
        } else {
            Log.w(TAG, "Handler ${handler.javaClass.simpleName} missing @PacketHandler annotation")
        }
    }

    fun handle(opcode: Byte, reader: PacketReader): ByteArray? {
        val handler = handlers[opcode]
        if (handler == null) {
            Log.w(TAG, "No handler for opcode 0x${String.format("%02X", opcode)}")
            return null
        }

        return try {
            val writer = PacketWriter()
            handler.handle(reader, writer)
            writer.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling opcode 0x${String.format("%02X", opcode)}", e)
            null
        }
    }

    fun getHandlerCount(): Int = handlers.size
}
