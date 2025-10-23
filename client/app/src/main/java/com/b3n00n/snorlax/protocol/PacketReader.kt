package com.b3n00n.snorlax.protocol

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

class PacketReader(inputStream: InputStream) {
    private val dataStream = DataInputStream(inputStream)

    @Throws(IOException::class)
    fun readU8(): Int = dataStream.readUnsignedByte()

    @Throws(IOException::class)
    fun readU16(): Int = dataStream.readUnsignedShort()

    @Throws(IOException::class)
    fun readU32(): Long = dataStream.readInt().toLong() and 0xFFFFFFFFL

    @Throws(IOException::class)
    fun readU64(): Long = dataStream.readLong()

    @Throws(IOException::class)
    fun readI32(): Int = dataStream.readInt()

    @Throws(IOException::class)
    fun readString(): String {
        val length = readU32()
        if (length > Int.MAX_VALUE) {
            throw IOException("String length too large: $length")
        }

        val bytes = ByteArray(length.toInt())
        dataStream.readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    @Throws(IOException::class)
    fun readAsciiString(): String {
        val length = readU16()
        val bytes = ByteArray(length)
        dataStream.readFully(bytes)
        return String(bytes, StandardCharsets.US_ASCII)
    }

    @Throws(IOException::class)
    fun close() {
        dataStream.close()
    }
}