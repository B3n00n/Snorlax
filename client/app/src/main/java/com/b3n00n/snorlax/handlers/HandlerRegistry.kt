package com.b3n00n.snorlax.handlers

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.b3n00n.snorlax.handlers.impl.*
import com.b3n00n.snorlax.network.NetworkClient
import com.b3n00n.snorlax.protocol.PacketReader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HandlerRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioManager: AudioManager
) {
    companion object {
        private const val TAG = "HandlerRegistry"
    }

    private val handlers = mutableMapOf<Byte, IPacketHandler>()

    init {
        register(PingHandler())
        register(LaunchAppHandler(context))
        register(ExecuteShellHandler())
        register(RequestBatteryHandler(context))
        register(RequestInstalledAppsHandler(context))
        register(UninstallAppHandler(context))
        register(SetVolumeHandler(audioManager))
        register(GetVolumeHandler(audioManager))
        register(ShutdownHandler(context))
        register(InstallApkHandler(context))
        register(InstallLocalApkHandler(context))

        Log.d(TAG, "Registered ${handlers.size} handlers")
    }

    fun register(handler: IPacketHandler) {
        val annotation = handler.javaClass.getAnnotation(PacketHandler::class.java)
        if (annotation != null) {
            val opcode = annotation.opcode
            handlers[opcode] = handler
            Log.d(TAG, "Registered ${handler.javaClass.simpleName} for opcode 0x${String.format("%02X", opcode)}")
        } else {
            Log.w(TAG, "Handler ${handler.javaClass.simpleName} missing @PacketHandler annotation")
        }
    }

    /**
     * Route a complete packet to its handler.
     *
     * @param packet Complete packet bytes [opcode][length][payload]
     * @param client The network client for sending responses
     */
    fun routePacket(packet: ByteArray, client: NetworkClient) {
        try {
            val reader = PacketReader(ByteArrayInputStream(packet))
            val opcode = reader.readU8().toByte()
            val length = reader.readU16()

            Log.d(TAG, "Routing packet: opcode=0x${String.format("%02X", opcode)}, length=$length")

            val handler = handlers[opcode]
            if (handler == null) {
                Log.w(TAG, "No handler for opcode 0x${String.format("%02X", opcode)}")
                return
            }

            handler.handle(reader, client)
        } catch (e: Exception) {
            Log.e(TAG, "Error routing packet", e)
        }
    }
}
