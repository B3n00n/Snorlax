package com.b3n00n.snorlax.handlers

import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.protocol.PacketWriter

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PacketHandler(
    val opcode: Byte
)

interface IPacketHandler {
    fun handle(reader: PacketReader, writer: PacketWriter)
}
