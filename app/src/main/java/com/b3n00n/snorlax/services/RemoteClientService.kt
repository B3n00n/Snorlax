package com.b3n00n.snorlax.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
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
        private const val SERVER_IP = "192.168.50.124"
        private const val SERVER_PORT = 8888
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "snorlax_service_channel"
    }

    private var networkClient: NetworkClient? = null
    private var commandHandler: CommandHandler? = null
    private var isServiceRunning = false

    private var heartbeatHandler: Handler? = null
    private var heartbeatRunnable: Runnable? = null
    private val heartbeatInterval = 15000L

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createForegroundNotification())

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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Snorlax Remote Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps connection to remote server active"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Snorlax Remote Active")
            .setContentText("Connected to remote server")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }


    private fun startHeartbeat() {
        heartbeatHandler = Handler(Looper.getMainLooper())
        heartbeatRunnable = object : Runnable {
            override fun run() {
                try {
                    if (networkClient?.isConnected() == true) {
                        val writer = PacketWriter()
                        writer.writeU8(MessageType.HEARTBEAT.toInt())
                        networkClient!!.sendData(writer.toByteArray())
                        Log.d(TAG, "Heartbeat sent")
                    }
                    heartbeatHandler?.postDelayed(this, heartbeatInterval)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending heartbeat", e)
                }
            }
        }
        heartbeatHandler?.postDelayed(heartbeatRunnable!!, heartbeatInterval)
    }

    private fun stopHeartbeat() {
        heartbeatRunnable?.let { runnable ->
            heartbeatHandler?.removeCallbacks(runnable)
        }
        heartbeatHandler = null
        heartbeatRunnable = null
    }
    private fun startNetworkConnection() {
        networkClient!!.connect()
    }

    override fun onConnected() {
        Log.d(TAG, "Connected to server")
        sendDeviceInfo()
        startHeartbeat()
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
        stopHeartbeat()
        networkClient?.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}