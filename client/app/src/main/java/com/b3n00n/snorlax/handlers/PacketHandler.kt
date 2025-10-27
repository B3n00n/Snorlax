package com.b3n00n.snorlax.handlers

import com.b3n00n.snorlax.network.NetworkClient
import com.b3n00n.snorlax.protocol.PacketReader

/**
 * Annotation for marking packet handler classes with their opcode.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PacketHandler(
    val opcode: Byte
)

/**
 * Interface for packet handlers.
 *
 * Handlers process incoming packets and can send responses using the NetworkClient.
 * For long-running operations (like downloads), use BackgroundJobs.submit() to avoid
 * blocking the packet processing thread.
 */
interface IPacketHandler {
    /**
     * Handle an incoming packet.
     *
     * This method should return quickly. For long-running operations,
     * submit a background job instead.
     *
     * @param reader The packet reader positioned after opcode and length
     * @param client The network client for sending responses
     */
    fun handle(reader: PacketReader, client: NetworkClient)
}
