package com.b3n00n.snorlax.network

import android.util.Log
import com.b3n00n.snorlax.protocol.PacketWriter
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class NetworkClient(
    private val serverIp: String,
    private val serverPort: Int
) {

    companion object {
        private const val TAG = "NetworkClient"
        private const val RECONNECT_DELAY_MS = 5000L
    }

    private val executor = Executors.newFixedThreadPool(2)
    private val isRunning = AtomicBoolean(false)
    private val packetBuffer = PacketBuffer()
    private val connected = AtomicBoolean(false)

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var listener: ConnectionListener? = null

    /**
     * Listener for connection events and complete packets.
     */
    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected()
        fun onPacketReceived(packet: ByteArray)
        fun onError(e: Exception)
    }

    fun connect() {
        if (isRunning.get()) {
            Log.w(TAG, "Already connecting or connected")
            return
        }

        isRunning.set(true)
        executor.execute { connectionLoop() }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        isRunning.set(false)
        closeConnection()
    }

    fun isConnected(): Boolean {
        return connected.get() && socket?.isConnected == true
    }

    fun sendPacket(opcode: Byte, buildPayload: PacketWriter.() -> Unit) {
        if (!isConnected()) {
            Log.w(TAG, "Cannot send packet 0x${String.format("%02X", opcode)} - not connected")
            return
        }

        executor.execute {
            try {
                val packet = PacketWriter()
                packet.writePacket(opcode, buildPayload)
                val bytes = packet.toByteArray()

                outputStream?.write(bytes)
                outputStream?.flush()

                Log.d(TAG, "Sent packet: opcode=0x${String.format("%02X", opcode)}, size=${bytes.size}")
            } catch (e: IOException) {
                Log.e(TAG, "Error sending packet", e)
                handleError(e)
            }
        }
    }

    fun setConnectionListener(listener: ConnectionListener) {
        this.listener = listener
    }

    private fun connectionLoop() {
        while (isRunning.get()) {
            try {
                Log.d(TAG, "Connecting...")
                establishConnection()
                connected.set(true)
                Log.d(TAG, "Connected")
                notifyConnected()
                readLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                handleError(e)
            }

            if (isRunning.get()) {
                Log.d(TAG, "Reconnecting...")
                try {
                    Thread.sleep(RECONNECT_DELAY_MS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun establishConnection() {
        Log.d(TAG, "Connecting to $serverIp:$serverPort")
        socket = Socket(serverIp, serverPort).also { sock ->
            outputStream = sock.getOutputStream()
        }
        packetBuffer.clear() // Clear any stale data
        Log.d(TAG, "Connected successfully")
    }

    @Throws(IOException::class)
    private fun readLoop() {
        val buffer = ByteArray(4096)
        while (isRunning.get() && isConnected()) {
            val bytesRead = socket?.getInputStream()?.read(buffer) ?: -1
            if (bytesRead == -1) {
                throw IOException("Connection closed by server")
            }

            if (bytesRead > 0) {
                val data = buffer.copyOf(bytesRead)
                packetBuffer.append(data)

                while (true) {
                    val packet = packetBuffer.tryReadPacket() ?: break
                    notifyPacketReceived(packet)
                }
            }
        }
    }

    private fun closeConnection() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing connection", e)
        } finally {
            outputStream = null
            socket = null
            packetBuffer.clear()
            connected.set(false)
            Log.d(TAG, "Disconnected")
            notifyDisconnected()
        }
    }

    private fun handleError(e: Exception) {
        connected.set(false)
        closeConnection()
        listener?.onError(e)
    }

    private fun notifyConnected() {
        listener?.onConnected()
    }

    private fun notifyDisconnected() {
        listener?.onDisconnected()
    }

    private fun notifyPacketReceived(packet: ByteArray) {
        listener?.onPacketReceived(packet)
    }

    fun shutdown() {
        disconnect()
        executor.shutdown()
    }
}