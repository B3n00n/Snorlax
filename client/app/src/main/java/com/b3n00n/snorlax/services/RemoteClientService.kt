package com.b3n00n.snorlax.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.b3n00n.snorlax.activities.ServerConfigurationActivity
import com.b3n00n.snorlax.activities.WifiConfigurationActivity
import com.b3n00n.snorlax.config.ServerConfigurationManager
import com.b3n00n.snorlax.config.WifiConfigurationManager
import com.b3n00n.snorlax.handlers.CommandHandler
import com.b3n00n.snorlax.models.DeviceInfo
import com.b3n00n.snorlax.network.ConnectionManager
import com.b3n00n.snorlax.network.NetworkClient
import com.b3n00n.snorlax.network.WifiConnectionManager
import com.b3n00n.snorlax.protocol.MessageType
import com.b3n00n.snorlax.protocol.PacketWriter
import com.b3n00n.snorlax.utils.DeviceRestrictionManager
import java.io.IOException

class RemoteClientService : Service(),
    ConnectionManager.ConnectionListener,
    WifiConnectionManager.WifiConnectionListener {

    companion object {
        private const val TAG = "RemoteClientService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "snorlax_service_channel"
        const val ACTION_OPEN_CONFIG = "com.b3n00n.snorlax.OPEN_CONFIG"
        const val ACTION_OPEN_WIFI_CONFIG = "com.b3n00n.snorlax.OPEN_WIFI_CONFIG"
        const val ACTION_WIFI_CONFIG_CHANGED = "com.b3n00n.snorlax.WIFI_CONFIG_CHANGED"
        private const val HEARTBEAT_INTERVAL_MS = 15000L
    }

    // Network components
    private var networkClient: NetworkClient? = null
    private var commandHandler: CommandHandler? = null

    // Service state
    private var isServiceRunning = false
    private var isWifiConnected = false
    private var currentConnectedSsid: String? = null

    // Managers
    private lateinit var configManager: ServerConfigurationManager
    private lateinit var wifiConfigManager: WifiConfigurationManager
    private lateinit var wifiConnectionManager: WifiConnectionManager
    private lateinit var deviceRestrictionManager: DeviceRestrictionManager
    private lateinit var connectivityManager: ConnectivityManager

    // Heartbeat
    private var heartbeatHandler: Handler? = null
    private var heartbeatRunnable: Runnable? = null

    // Network monitoring
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val notificationHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        initializeComponents()
        startForeground(NOTIFICATION_ID, createForegroundNotification())
        registerNetworkCallback()
        initializeWifiConnection()
    }

    private fun initializeComponents() {
        // Initialize configuration managers
        configManager = ServerConfigurationManager(this)
        wifiConfigManager = WifiConfigurationManager(this)
        deviceRestrictionManager = DeviceRestrictionManager(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Initialize WiFi connection manager
        wifiConnectionManager = WifiConnectionManager(this)
        wifiConnectionManager.setListener(this)

        // Initialize network client (but don't connect yet)
        val serverIp = configManager.getServerIp()
        val serverPort = configManager.getServerPort()
        Log.d(TAG, "Server configuration: $serverIp:$serverPort")

        networkClient = NetworkClient(serverIp, serverPort)
        networkClient!!.setConnectionListener(this)
        commandHandler = CommandHandler(this, networkClient!!)

        // Create notification channel
        createNotificationChannel()
    }

    private fun registerNetworkCallback() {
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                notificationHandler.post {
                    handleNetworkAvailable()
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                notificationHandler.post {
                    handleNetworkLost()
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                notificationHandler.post {
                    updateNotification()
                }
            }
        }

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
    }

    private fun handleNetworkAvailable() {
        val newSsid = wifiConnectionManager.getCurrentSsid()
        Log.d(TAG, "Network available, SSID: $newSsid")

        currentConnectedSsid = newSsid
        updateNotification()

        // Check if we should connect to TCP
        if (shouldConnectToTcp()) {
            startTcpConnection()
        }
    }

    private fun handleNetworkLost() {
        Log.d(TAG, "WiFi network lost")
        currentConnectedSsid = null
        isWifiConnected = false
        updateNotification()

        // Disconnect TCP when WiFi is lost
        if (networkClient?.isConnected() == true) {
            Log.d(TAG, "Disconnecting TCP due to WiFi loss")
            networkClient?.disconnect()
        }
    }

    private fun shouldConnectToTcp(): Boolean {
        // If we have WiFi config, only connect when on the right network
        return if (wifiConfigManager.hasWifiConfig()) {
            val targetSsid = wifiConfigManager.getWifiSSID()
            currentConnectedSsid == targetSsid
        } else {
            // No WiFi config, connect whenever we have network
            currentConnectedSsid != null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_OPEN_CONFIG -> launchConfigurationActivity()
            ACTION_OPEN_WIFI_CONFIG -> launchWifiConfigurationActivity()
            ACTION_WIFI_CONFIG_CHANGED -> handleWifiConfigChanged()
            else -> {
                if (!isServiceRunning) {
                    isServiceRunning = true
                    checkAndStartTcpConnection()
                }
            }
        }

        return START_STICKY
    }

    private fun checkAndStartTcpConnection() {
        if (shouldConnectToTcp()) {
            Log.d(TAG, "Conditions met, starting TCP connection")
            startTcpConnection()
        } else {
            Log.d(TAG, "Waiting for correct WiFi network")
        }
    }

    private fun startTcpConnection() {
        if (networkClient?.isConnected() == false) {
            networkClient?.connect()
        }
    }

    // WiFi Connection Listener callbacks
    override fun onWifiConnected(ssid: String) {
        Log.d(TAG, "WiFi connected to: $ssid")
        isWifiConnected = true
        currentConnectedSsid = ssid
        updateNotification()

        // Start TCP connection now that WiFi is connected
        if (isServiceRunning && networkClient?.isConnected() == false) {
            startTcpConnection()
        }
    }

    override fun onWifiDisconnected() {
        Log.d(TAG, "WiFi disconnected")
        isWifiConnected = false
        currentConnectedSsid = null
        updateNotification()

        // Disconnect TCP
        if (networkClient?.isConnected() == true) {
            networkClient?.disconnect()
        }
    }

    override fun onWifiConnectionFailed(reason: String) {
        Log.w(TAG, "WiFi connection failed: $reason")
        updateNotification()
    }

    // TCP Connection Listener callbacks
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

    override fun onDataReceived(data: ByteArray) {
        commandHandler?.handleMessage(data)
    }

    override fun onError(e: Exception) {
        Log.e(TAG, "Network error: ${e.message}")
    }

    private fun initializeWifiConnection() {
        if (wifiConfigManager.hasWifiConfig()) {
            val ssid = wifiConfigManager.getWifiSSID()
            val password = wifiConfigManager.getWifiPassword()
            val autoReconnectEnabled = wifiConfigManager.isAutoReconnectEnabled()

            // Apply WiFi restrictions based on settings
            deviceRestrictionManager.setWifiRestriction(autoReconnectEnabled)

            if (autoReconnectEnabled) {
                wifiConnectionManager.startAutoReconnect(ssid, password)
            } else {
                // Single connection attempt
                wifiConnectionManager.connectToWifi(ssid, password)
            }
        }
    }

    private fun handleWifiConfigChanged() {
        if (wifiConfigManager.hasWifiConfig()) {
            val ssid = wifiConfigManager.getWifiSSID()
            val password = wifiConfigManager.getWifiPassword()
            val autoReconnectEnabled = wifiConfigManager.isAutoReconnectEnabled()

            deviceRestrictionManager.setWifiRestriction(autoReconnectEnabled)

            // Disconnect TCP if we're changing networks
            if (networkClient?.isConnected() == true && currentConnectedSsid != ssid) {
                networkClient?.disconnect()
            }

            if (autoReconnectEnabled) {
                wifiConnectionManager.startAutoReconnect(ssid, password)
            } else {
                wifiConnectionManager.stopAutoReconnect()
                wifiConnectionManager.connectToWifi(ssid, password)
            }
        } else {
            deviceRestrictionManager.setWifiRestriction(false)
            wifiConnectionManager.stopAutoReconnect()
        }
    }

    private fun startHeartbeat() {
        stopHeartbeat()

        heartbeatHandler = Handler(Looper.getMainLooper())
        heartbeatRunnable = object : Runnable {
            override fun run() {
                try {
                    if (networkClient?.isConnected() == true) {
                        val writer = PacketWriter()
                        writer.writeU8(MessageType.HEARTBEAT.toInt())
                        networkClient!!.sendData(writer.toByteArray())
                        Log.d(TAG, "TCP Heartbeat sent")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending heartbeat", e)
                }
                heartbeatHandler?.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }
        heartbeatHandler?.postDelayed(heartbeatRunnable!!, HEARTBEAT_INTERVAL_MS)
    }

    private fun stopHeartbeat() {
        heartbeatRunnable?.let { runnable ->
            heartbeatHandler?.removeCallbacks(runnable)
        }
        heartbeatHandler = null
        heartbeatRunnable = null
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
        val serverInfo = "${configManager.getServerIp()}:${configManager.getServerPort()}"
        val wifiStatus = buildWifiStatusText()
        val connectionStatus = if (networkClient?.isConnected() == true) " [Connected]" else ""

        val configPendingIntent = createPendingIntent(ACTION_OPEN_CONFIG, 0)
        val wifiConfigPendingIntent = createPendingIntent(ACTION_OPEN_WIFI_CONFIG, 1)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Snorlax Remote Active$connectionStatus")
            .setContentText("Server: $serverInfo | $wifiStatus")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_preferences, "Server", configPendingIntent)
            .addAction(android.R.drawable.ic_menu_preferences, "WiFi", wifiConfigPendingIntent)
            .setContentIntent(configPendingIntent)
            .build()
    }

    private fun buildWifiStatusText(): String {
        return when {
            currentConnectedSsid != null -> "WiFi: $currentConnectedSsid"
            wifiConfigManager.hasWifiConfig() -> "WiFi: Connecting..."
            else -> "WiFi: Not configured"
        }
    }

    private fun createPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, RemoteClientService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                notificationManager.notify(NOTIFICATION_ID, createForegroundNotification())
            }
        } else {
            notificationManager.notify(NOTIFICATION_ID, createForegroundNotification())
        }
    }

    private fun launchConfigurationActivity() {
        launchActivity(ServerConfigurationActivity::class.java)
    }

    private fun launchWifiConfigurationActivity() {
        launchActivity(WifiConfigurationActivity::class.java)
    }

    private fun launchActivity(activityClass: Class<*>) {
        try {
            val intent = Intent(this, activityClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            Log.d(TAG, "${activityClass.simpleName} launched")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch ${activityClass.simpleName}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")

        isServiceRunning = false
        stopHeartbeat()

        // Unregister network callback
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }

        networkClient?.shutdown()
        wifiConnectionManager.cleanup()
        deviceRestrictionManager.clearAllRestrictions()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}