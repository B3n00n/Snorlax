package com.b3n00n.snorlax.handlers

import com.b3n00n.snorlax.protocol.PacketReader
import java.io.IOException

interface MessageHandler {
    val messageType: Byte

    @Throws(IOException::class)
    fun handle(reader: PacketReader, commandHandler: CommandHandler)
}