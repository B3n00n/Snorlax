package com.b3n00n.snorlax.protocol

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

class PacketWriter {
    private val byteStream = ByteArrayOutputStream()
    private val dataStream = DataOutputStream(byteStream)

    @Throws(IOException::class)
    fun writeU8(value: Int) {
        dataStream.writeByte(value and 0xFF)
    }

    @Throws(IOException::class)
    fun writeU16(value: Int) {
        dataStream.writeShort(value and 0xFFFF)
    }

    @Throws(IOException::class)
    fun writeU32(value: Long) {
        dataStream.writeInt((value and 0xFFFFFFFFL).toInt())
    }

    @Throws(IOException::class)
    fun writeString(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeU32(bytes.size.toLong())
        dataStream.write(bytes)
    }

    @Throws(IOException::class)
    fun writeAsciiString(value: String) {
        val bytes = value.toByteArray(StandardCharsets.US_ASCII)
        writeU16(bytes.size)
        dataStream.write(bytes)
    }

    fun toByteArray(): ByteArray = byteStream.toByteArray()

    fun clear() {
        byteStream.reset()
    }

    @Throws(IOException::class)
    fun close() {
        dataStream.close()
        byteStream.close()
    }
}