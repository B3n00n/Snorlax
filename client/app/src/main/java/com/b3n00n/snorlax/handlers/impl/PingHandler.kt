package com.b3n00n.snorlax.handlers.impl

import android.util.Log
import com.b3n00n.snorlax.R
import com.b3n00n.snorlax.handlers.IPacketHandler
import com.b3n00n.snorlax.handlers.PacketHandler
import com.b3n00n.snorlax.network.NetworkClient
import com.b3n00n.snorlax.protocol.MessageOpcode
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.utils.SoundManager

/**
 * Handles Ping command (0x45): [timestamp: u64]
 * Responds with PingResponse (0x13): [timestamp: u64] (echoes back)
 */
@PacketHandler(MessageOpcode.PING)
class PingHandler : IPacketHandler {
    companion object {
        private const val TAG = "PingHandler"
    }

    override fun handle(reader: PacketReader, client: NetworkClient) {
        val timestamp = reader.readU64()

        Log.d(TAG, "Ping received: $timestamp")

        SoundManager.play(R.raw.ping_sound)

        client.sendPacket(MessageOpcode.PING_RESPONSE) {
            writeU64(timestamp)
        }
    }
}
