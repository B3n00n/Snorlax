package com.b3n00n.snorlax.handlers

import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.protocol.PacketWriter

/**
 * Annotation to mark a class as a packet handler for a specific opcode.
 *
 * Usage:
 * ```
 * @PacketHandler(0x4B)
 * class GetVolumeHandler(private val context: Context) : IPacketHandler {
 *     override fun handle(reader: PacketReader, writer: PacketWriter) {
 *         // Read request, process, write response
 *     }
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PacketHandler(
    /** The opcode this handler responds to (SERVER → CLIENT command opcode) */
    val opcode: Byte
)

/**
 * Interface for packet handlers.
 *
 * Handlers read request data directly from PacketReader and write response data
 * directly to PacketWriter. No intermediate packet objects.
 */
interface IPacketHandler {
    /**
     * Handle an incoming packet.
     *
     * @param reader Positioned at the start of the packet payload (after opcode and length)
     * @param writer Used to write the complete response packet (opcode + length + payload)
     */
    fun handle(reader: PacketReader, writer: PacketWriter)
}
