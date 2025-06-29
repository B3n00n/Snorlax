package com.b3n00n.snorlax.network

import android.util.Log
import com.b3n00n.snorlax.protocol.PacketReader
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class NetworkClient(
    private val serverIp: String,
    private val serverPort: Int
) : ConnectionManager {

    companion object {
        private const val TAG = "NetworkClient"
        private const val RECONNECT_DELAY_MS = 5000L
    }

    private val executor = Executors.newFixedThreadPool(2)
    private val isRunning = AtomicBoolean(false)

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var packetReader: PacketReader? = null
    private var listener: ConnectionManager.ConnectionListener? = null
    private var state = ConnectionState.DISCONNECTED

    override fun connect() {
        if (isRunning.get()) {
            Log.w(TAG, "Already connecting or connected")
            return
        }

        isRunning.set(true)
        executor.execute { connectionLoop() }
    }

    override fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        isRunning.set(false)
        closeConnection()
    }

    override fun isConnected(): Boolean {
        return state == ConnectionState.CONNECTED &&
                socket?.isConnected == true
    }

    override fun sendData(data: ByteArray) {
        if (!isConnected()) {
            Log.w(TAG, "Cannot send data - not connected")
            return
        }

        executor.execute {
            try {
                outputStream?.write(data)
                outputStream?.flush()
            } catch (e: IOException) {
                Log.e(TAG, "Error sending data", e)
                handleError(e)
            }
        }
    }

    override fun setConnectionListener(listener: ConnectionManager.ConnectionListener) {
        this.listener = listener
    }

    private fun connectionLoop() {
        while (isRunning.get()) {
            try {
                setState(ConnectionState.CONNECTING)
                establishConnection()
                setState(ConnectionState.CONNECTED)
                notifyConnected()
                readLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                handleError(e)
            }

            if (isRunning.get()) {
                setState(ConnectionState.RECONNECTING)
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
            packetReader = PacketReader(sock.getInputStream())
        }
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
                notifyDataReceived(data)
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
            packetReader = null
            setState(ConnectionState.DISCONNECTED)
            notifyDisconnected()
        }
    }

    private fun setState(newState: ConnectionState) {
        state = newState
        Log.d(TAG, "Connection state: $newState")
    }

    private fun handleError(e: Exception) {
        setState(ConnectionState.ERROR)
        closeConnection()
        listener?.onError(e)
    }

    private fun notifyConnected() {
        listener?.onConnected()
    }

    private fun notifyDisconnected() {
        listener?.onDisconnected()
    }

    private fun notifyDataReceived(data: ByteArray) {
        listener?.onDataReceived(data)
    }

    fun shutdown() {
        disconnect()
        executor.shutdown()
    }
}