package com.b3n00n.snorlax.protocol

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID

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
    fun writeU64(value: Long) {
        dataStream.writeLong(value)
    }

    @Throws(IOException::class)
    fun writeI32(value: Int) {
        dataStream.writeInt(value)
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

    @Throws(IOException::class)
    fun writeBytes(bytes: ByteArray) {
        dataStream.write(bytes)
    }

    @Throws(IOException::class)
    fun writeFloat(value: Float) {
        dataStream.writeFloat(value)
    }

    @Throws(IOException::class)
    fun writeUUID(uuid: UUID) {
        val buffer = ByteBuffer.allocate(16)
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
        dataStream.write(buffer.array())
    }

    fun toByteArray(): ByteArray {
        dataStream.flush()
        return byteStream.toByteArray()
    }

    fun size(): Int {
        return byteStream.size()
    }

    fun clear() {
        byteStream.reset()
    }

    @Throws(IOException::class)
    fun close() {
        dataStream.close()
        byteStream.close()
    }

    fun writePacket(opcode: Byte, buildPayload: PacketWriter.() -> Unit) {
        // Build payload in temporary writer to calculate size
        val payloadWriter = PacketWriter()
        buildPayload(payloadWriter)
        val payloadSize = payloadWriter.size()

        // Write header
        writeU8(opcode.toInt() and 0xFF)
        writeU16(payloadSize)

        // Write payload bytes directly
        if (payloadSize > 0) {
            writeBytes(payloadWriter.toByteArray())
        }
    }
}