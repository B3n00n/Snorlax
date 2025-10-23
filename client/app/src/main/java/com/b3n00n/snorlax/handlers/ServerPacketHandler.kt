package com.b3n00n.snorlax.handlers

import com.b3n00n.snorlax.protocol.ServerPacket

/**
 * Interface for handlers that process ServerPackets
 *
 * Each handler implements this to handle specific types of server commands.
 */
interface ServerPacketHandler {
    /**
     * Check if this handler can process the given packet type
     */
    fun canHandle(packet: ServerPacket): Boolean

    /**
     * Handle the server packet
     *
     * @param packet The server packet to handle
     * @param commandHandler The command handler for sending responses
     */
    fun handle(packet: ServerPacket, commandHandler: CommandHandler)
}
