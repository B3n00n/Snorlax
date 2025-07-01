package com.b3n00n.snorlax.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.models.DeviceInfo
import com.b3n00n.snorlax.network.ConnectionManager
import com.b3n00n.snorlax.network.NetworkClient
import com.b3n00n.snorlax.protocol.MessageType
import com.b3n00n.snorlax.protocol.PacketWriter
import java.io.IOException

class RemoteClientService : Service(), ConnectionManager.ConnectionListener {
    companion object {
        private const val TAG = "RemoteClientService"
        private const val SERVER_IP = "192.168.50.123"
        private const val SERVER_PORT = 8888
    }

    private var networkClient: NetworkClient? = null
    private var commandHandler: CommandHandler? = null
    private var isServiceRunning = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        networkClient = NetworkClient(SERVER_IP, SERVER_PORT)
        networkClient!!.setConnectionListener(this)

        commandHandler = CommandHandler(this, networkClient!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        if (!isServiceRunning) {
            isServiceRunning = true
            startNetworkConnection()
        }

        return START_STICKY
    }

    private fun startNetworkConnection() {
        networkClient!!.connect()
    }

    override fun onConnected() {
        Log.d(TAG, "Connected to server")
        sendDeviceInfo()
    }

    override fun onDisconnected() {
        Log.d(TAG, "Disconnected from server")
    }

    override fun onDataReceived(data: ByteArray) {
        commandHandler!!.handleMessage(data)
    }

    override fun onError(e: Exception) {
        Log.e(TAG, "Network error: ${e.message}")
    }

    private fun sendDeviceInfo() {
        try {
            // Pass the service context to DeviceInfo
            val deviceInfo = DeviceInfo(this)

            val writer = PacketWriter()
            writer.writeU8(MessageType.DEVICE_CONNECTED.toInt())
            writer.writeString(deviceInfo.model)
            writer.writeString(deviceInfo.serial)

            networkClient!!.sendData(writer.toByteArray())
            Log.d(TAG, "Sent device info")
        } catch (e: IOException) {
            Log.e(TAG, "Error sending device info", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")

        isServiceRunning = false
        networkClient?.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}