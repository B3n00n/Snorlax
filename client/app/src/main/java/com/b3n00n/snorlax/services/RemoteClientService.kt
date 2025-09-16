package com.b3n00n.snorlax.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.b3n00n.snorlax.activities.ServerConfigurationActivity
import com.b3n00n.snorlax.config.ServerConfigurationManager
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
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "snorlax_service_channel"
        const val ACTION_OPEN_CONFIG = "com.b3n00n.snorlax.OPEN_CONFIG"
    }

    private var networkClient: NetworkClient? = null
    private var commandHandler: CommandHandler? = null
    private var isServiceRunning = false
    private lateinit var configManager: ServerConfigurationManager

    private var heartbeatHandler: Handler? = null
    private var heartbeatRunnable: Runnable? = null
    private val heartbeatInterval = 15000L

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        createNotificationChannel()
        configManager = ServerConfigurationManager(this)

        startForeground(NOTIFICATION_ID, createForegroundNotification())

        val serverIp = configManager.getServerIp()
        val serverPort = configManager.getServerPort()

        Log.d(TAG, "Using server configuration: $serverIp:$serverPort")

        networkClient = NetworkClient(serverIp, serverPort)
        networkClient!!.setConnectionListener(this)

        commandHandler = CommandHandler(this, networkClient!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")

        // Handle configuration action
        if (intent?.action == ACTION_OPEN_CONFIG) {
            launchConfigurationActivity()
            return START_STICKY
        }

        if (!isServiceRunning) {
            isServiceRunning = true
            startNetworkConnection()
        }

        return START_STICKY
    }

    private fun launchConfigurationActivity() {
        try {
            val intent = Intent(this, ServerConfigurationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            Log.d(TAG, "Configuration activity launched")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch configuration activity", e)
        }
    }

    private fun createNotificationChannel() {
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

    @SuppressLint("LaunchActivityFromNotification")
    private fun createForegroundNotification(): Notification {
        val serverInfo = if (::configManager.isInitialized) {
            "${configManager.getServerIp()}:${configManager.getServerPort()}"
        } else {
            "Initializing..."
        }

        // Create intent for configuration
        val configIntent = Intent(this, RemoteClientService::class.java).apply {
            action = ACTION_OPEN_CONFIG
        }

        val configPendingIntent = PendingIntent.getService(
            this,
            0,
            configIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Snorlax Remote Active")
            .setContentText("Server: $serverInfo")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_preferences,
                "Configure",
                configPendingIntent
            )
            .setContentIntent(configPendingIntent)
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

        updateNotification()
    }

    override fun onDisconnected() {
        Log.d(TAG, "Disconnected from server")
        stopHeartbeat()
        updateNotification()
    }

    private fun updateNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 13+, check if we can post notifications
            if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, createForegroundNotification())
            }
        } else {
            // For older versions, just update normally
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createForegroundNotification())
        }
    }

    override fun onDataReceived(data: ByteArray) {
        commandHandler!!.handleMessage(data)
    }

    override fun onError(e: Exception) {
        Log.e(TAG, "Network error: ${e.message}")
    }

    private fun sendDeviceInfo() {
        try {
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