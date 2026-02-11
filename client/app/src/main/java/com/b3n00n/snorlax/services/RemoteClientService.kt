package com.b3n00n.snorlax.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.b3n00n.snorlax.R
import com.b3n00n.snorlax.activities.ServerConfigurationActivity
import com.b3n00n.snorlax.activities.WiFiConfigurationActivity
import com.b3n00n.snorlax.config.ServerConfigurationManager
import com.b3n00n.snorlax.config.SnorlaxConfigManager
import com.b3n00n.snorlax.config.WiFiConfigurationManager
import com.b3n00n.snorlax.core.ClientContext
import com.b3n00n.snorlax.network.NetworkClient
import com.b3n00n.snorlax.network.ProtocolSession
import com.b3n00n.snorlax.network.WiFiConnectionManager
import com.b3n00n.snorlax.network.WiFiStateMonitor
import com.b3n00n.snorlax.monitoring.ForegroundAppMonitor
import com.b3n00n.snorlax.monitoring.TemporaryOculusClearActivityWorkaround
import com.b3n00n.snorlax.utils.SoundManager
import android.net.Network

class RemoteClientService : Service() {
    companion object {
        private const val TAG = "RemoteClientService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "snorlax_service_channel"
        const val ACTION_OPEN_CONFIG = "com.b3n00n.snorlax.OPEN_CONFIG"
        const val ACTION_OPEN_WIFI_CONFIG = "com.b3n00n.snorlax.OPEN_WIFI_CONFIG"
    }

    private var networkClient: NetworkClient? = null
    private var protocolSession: ProtocolSession? = null
    private var wifiConnectionManager: WiFiConnectionManager? = null
    private var wifiStateMonitor: WiFiStateMonitor? = null
    private var foregroundAppMonitor: ForegroundAppMonitor? = null
    private var oculusWorkaround: TemporaryOculusClearActivityWorkaround? = null // TODO: REMOVE THIS TEMPORARY WORKAROUND
    private var isServiceRunning = false
    private lateinit var configManager: ServerConfigurationManager
    private lateinit var wifiConfigManager: WiFiConfigurationManager

    private var heartbeatHandler: Handler? = null
    private var heartbeatRunnable: Runnable? = null
    private val heartbeatInterval = 15000L
    private var isWifiConnected = false
    private var isTcpConnected = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        ClientContext.initialize(this)
        SoundManager.initialize(this)

        createNotificationChannel()
        configManager = ServerConfigurationManager(this)
        wifiConfigManager = WiFiConfigurationManager(this)

        startForeground(NOTIFICATION_ID, createForegroundNotification())

        val serverIp = configManager.getServerIp()
        val serverPort = configManager.getServerPort()

        Log.d(TAG, "Using server configuration: $serverIp:$serverPort")

        networkClient = NetworkClient(serverIp, serverPort)
        protocolSession = ProtocolSession(networkClient!!)

        wifiConnectionManager = WiFiConnectionManager(this)
        wifiStateMonitor = WiFiStateMonitor(this)
        foregroundAppMonitor = ClientContext.foregroundAppMonitor
        oculusWorkaround = TemporaryOculusClearActivityWorkaround(this) // TODO: REMOVE THIS TEMPORARY WORKAROUND

        setupWiFiListeners()
        setupForegroundAppListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_OPEN_CONFIG -> {
                launchConfigurationActivity()
                return START_STICKY
            }
            ACTION_OPEN_WIFI_CONFIG -> {
                launchWiFiConfigurationActivity()
                return START_STICKY
            }
        }

        if (!isServiceRunning) {
            isServiceRunning = true
            startConnectionManagement()
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

    private fun launchWiFiConfigurationActivity() {
        try {
            val intent = Intent(this, WiFiConfigurationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            Log.d(TAG, "WiFi configuration activity launched")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch WiFi configuration activity", e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Snorlax",
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

        val wifiInfo = if (::wifiConfigManager.isInitialized && wifiConfigManager.hasWifiConfig()) {
            " | WiFi: ${wifiConfigManager.getWifiSsid()}"
        } else {
            ""
        }

        val configIntent = Intent(this, RemoteClientService::class.java).apply {
            action = ACTION_OPEN_CONFIG
        }

        val configPendingIntent = PendingIntent.getService(
            this,
            0,
            configIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wifiConfigIntent = Intent(this, RemoteClientService::class.java).apply {
            action = ACTION_OPEN_WIFI_CONFIG
        }

        val wifiConfigPendingIntent = PendingIntent.getService(
            this,
            1,
            wifiConfigIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(SnorlaxConfigManager.APP_NAME)
            .setContentText("Server: $serverInfo$wifiInfo | v${SnorlaxConfigManager.APP_VERSION}")
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_notification))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_preferences,
                "Server",
                configPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_edit,
                "WiFi",
                wifiConfigPendingIntent
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
                        protocolSession?.sendHeartbeat()
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

    private fun setupWiFiListeners() {
        wifiConnectionManager?.setConnectionListener(object : WiFiConnectionManager.WiFiConnectionListener {
            override fun onWiFiConnected(network: Network) {
                Log.d(TAG, "WiFi connected, starting TCP connection")
                isWifiConnected = true
                startTcpConnection()
            }

            override fun onWiFiDisconnected() {
                Log.d(TAG, "WiFi disconnected, stopping TCP connection")
                isWifiConnected = false
                stopTcpConnection()
            }

            override fun onWiFiConnectionFailed(reason: String) {
                Log.w(TAG, "WiFi connection failed: $reason")
                isWifiConnected = false
            }
        })

        wifiStateMonitor?.setStateListener(object : WiFiStateMonitor.WiFiStateListener {
            override fun onWiFiAvailable() {
                Log.d(TAG, "WiFi became available")
                updateNotification()
            }

            override fun onWiFiLost() {
                Log.d(TAG, "WiFi lost")
                updateNotification()
            }
        })
    }

    private fun setupForegroundAppListener() {
        foregroundAppMonitor?.setListener(object : ForegroundAppMonitor.ForegroundAppListener {
            override fun onForegroundAppChanged(packageName: String, appName: String) {
                Log.i(TAG, "Foreground app changed: $appName ($packageName)")
                protocolSession?.sendForegroundAppChanged(packageName, appName)
            }
        })
    }

    private fun startConnectionManagement() {
        if (wifiConfigManager.hasWifiConfig()) {
            val ssid = wifiConfigManager.getWifiSsid()
            val password = wifiConfigManager.getWifiPassword()

            Log.d(TAG, "WiFi configuration found, connecting to: $ssid")

            wifiStateMonitor?.startMonitoring()

            wifiConnectionManager?.connect(ssid ?: "", password ?: "")
        } else {
            Log.d(TAG, "No WiFi configuration, connecting directly to TCP server")
            isWifiConnected = true
            startTcpConnection()
        }

        // Start monitoring foreground apps
        foregroundAppMonitor?.startMonitoring()

        // TODO: REMOVE THIS TEMPORARY WORKAROUND
        oculusWorkaround?.startMonitoring()
    }

    private fun startTcpConnection() {
        if (!isWifiConnected) {
            Log.w(TAG, "Cannot start TCP connection - WiFi not connected")
            return
        }

        Log.d(TAG, "Starting TCP connection")
        networkClient?.connect()
        startHeartbeat()
    }

    private fun stopTcpConnection() {
        Log.d(TAG, "Stopping TCP connection")
        stopHeartbeat()
        networkClient?.disconnect()
        isTcpConnected = false
    }

    private fun updateNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, createForegroundNotification())
            }
        } else {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createForegroundNotification())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")

        isServiceRunning = false
        isWifiConnected = false
        isTcpConnected = false

        stopHeartbeat()

        networkClient?.shutdown()

        wifiStateMonitor?.stopMonitoring()
        wifiConnectionManager?.disconnect()

        foregroundAppMonitor?.cleanup()
        oculusWorkaround?.cleanup() // TODO: REMOVE THIS TEMPORARY WORKAROUND
        SoundManager.release()

        Log.d(TAG, "All connections closed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}