package com.b3n00n.snorlax.handlers

import android.content.Context
import android.util.Log
import com.b3n00n.snorlax.handlers.impl.*
import com.b3n00n.snorlax.network.ConnectionManager
import com.b3n00n.snorlax.protocol.ClientPacket
import com.b3n00n.snorlax.protocol.PacketReader
import com.b3n00n.snorlax.protocol.ServerPacket
import java.io.ByteArrayInputStream
import java.io.IOException

class CommandHandler(
    val context: Context,
    private val connectionManager: ConnectionManager
) {
    companion object {
        private const val TAG = "CommandHandler"
    }

    private val handlers = mutableListOf<ServerPacketHandler>()

    init {
        registerHandlers()
    }

    private fun registerHandlers() {
        registerHandler(LaunchAppHandler(context))
        registerHandler(GetInstalledAppsHandler(context))
        registerHandler(BatteryStatusHandler(context))
        registerHandler(ShellCommandHandler())
        registerHandler(PingHandler(context))
        registerHandler(DownloadAndInstallApkHandler(context))
        registerHandler(ShutdownHandler(context))
        registerHandler(UninstallAppHandler(context))
        registerHandler(SetVolumeHandler(context))
        registerHandler(GetVolumeHandler(context))
        registerHandler(InstallLocalApkHandler(context))
    }

    private fun registerHandler(handler: ServerPacketHandler) {
        handlers.add(handler)
        Log.d(TAG, "Registered handler: ${handler.javaClass.simpleName}")
    }

    fun handleMessage(data: ByteArray) {
        try {
            val inputStream = ByteArrayInputStream(data)
            val reader = PacketReader(inputStream)

            val opcode = reader.readU8().toByte()
            val length = reader.readU16()

            Log.d(TAG, "Received packet: opcode=0x${opcode.toString(16)}, length=$length")

            val packet = ServerPacket.fromReader(opcode, reader)

            val handler = handlers.find { it.canHandle(packet) }
            if (handler != null) {
                Log.d(TAG, "Routing to handler: ${handler.javaClass.simpleName}")
                handler.handle(packet, this)
            } else {
                Log.w(TAG, "No handler registered for packet type: ${packet.javaClass.simpleName}")
                sendError("No handler for command")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error handling message", e)
            sendError("Error handling message: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            sendError("Unexpected error: ${e.message}")
        }
    }

    fun sendPacket(packet: ClientPacket) {
        try {
            val data = packet.toByteArray()
            connectionManager.sendData(data)
            Log.d(TAG, "Sent packet: opcode=0x${packet.opcode.toString(16)}")
        } catch (e: IOException) {
            Log.e(TAG, "Error sending packet", e)
        }
    }

    fun sendDeviceConnected(model: String, serial: String) {
        sendPacket(ClientPacket.DeviceConnected(model, serial))
    }

    fun sendHeartbeat() {
        sendPacket(ClientPacket.Heartbeat)
    }

    fun sendBatteryStatus(level: Int, isCharging: Boolean) {
        sendPacket(ClientPacket.BatteryStatus(level, isCharging))
    }

    fun sendVolumeStatus(percentage: Int, current: Int, max: Int) {
        sendPacket(ClientPacket.VolumeStatus(percentage, current, max))
    }

    fun sendError(errorMessage: String) {
        sendPacket(ClientPacket.Error(errorMessage))
    }

    fun sendLaunchAppResponse(success: Boolean, message: String) {
        sendPacket(ClientPacket.LaunchAppResponse(success, message))
    }

    fun sendShellExecutionResponse(success: Boolean, output: String, exitCode: Int) {
        sendPacket(ClientPacket.ShellExecutionResponse(success, output, exitCode))
    }

    fun sendInstalledAppsResponse(apps: List<String>) {
        sendPacket(ClientPacket.InstalledAppsResponse(apps))
    }

    fun sendPingResponse(timestamp: Long) {
        sendPacket(ClientPacket.PingResponse(timestamp))
    }

    fun sendApkInstallResponse(success: Boolean, message: String) {
        sendPacket(ClientPacket.ApkInstallResponse(success, message))
    }

    fun sendUninstallAppResponse(success: Boolean, message: String) {
        sendPacket(ClientPacket.UninstallAppResponse(success, message))
    }

    fun sendShutdownResponse() {
        sendPacket(ClientPacket.ShutdownResponse)
    }

    fun sendVolumeSetResponse(success: Boolean, actualLevel: Int) {
        sendPacket(ClientPacket.VolumeSetResponse(success, actualLevel))
    }
}
