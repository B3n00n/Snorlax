package com.b3n00n.snorlax.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Buffer for handling incomplete TCP packets.
 *
 * TCP can split packets across multiple reads. This buffer accumulates
 * incoming data and extracts complete packets when available.
 *
 * Packet format: [opcode: u8][length: u16][payload: bytes]
 */
class PacketBuffer {
    private val buffer = ByteArrayOutputStream()

    /**
     * Append incoming data to the buffer.
     */
    fun append(data: ByteArray) {
        buffer.write(data)
    }

    /**
     * Try to extract a complete packet from the buffer.
     *
     * @return Complete packet bytes if available, null if incomplete
     */
    fun tryReadPacket(): ByteArray? {
        val bytes = buffer.toByteArray()
        if (bytes.size < 3) {
            return null
        }
        val length = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[2].toInt() and 0xFF)
        val totalSize = 3 + length

        if (bytes.size < totalSize) {
            return null
        }

        val packet = bytes.copyOfRange(0, totalSize)

        buffer.reset()
        if (bytes.size > totalSize) {
            buffer.write(bytes, totalSize, bytes.size - totalSize)
        }

        return packet
    }

    fun clear() {
        buffer.reset()
    }
}
