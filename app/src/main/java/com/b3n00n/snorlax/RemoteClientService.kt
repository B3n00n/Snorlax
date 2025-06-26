package com.b3n00n.snorlax

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class RemoteClientService : Service() {
    private val TAG = "SnorlaxService"
    private var serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val SERVER_IP = "192.168.50.123"
    private val SERVER_PORT = 8888
    private val RECONNECT_DELAY = 5000L

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Snorlax service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Snorlax service started")
        startConnection()
        return START_STICKY
    }

    private fun startConnection() {
        serviceScope.launch {
            while (isActive) {
                try {
                    connectToServer()
                } catch (e: Exception) {
                    Log.e(TAG, "Connection error: ${e.message}")
                    delay(RECONNECT_DELAY)
                }
            }
        }
    }

    private suspend fun connectToServer() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Connecting to $SERVER_IP:$SERVER_PORT")
                socket = Socket(SERVER_IP, SERVER_PORT)
                writer = PrintWriter(socket!!.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))

                Log.d(TAG, "Connected successfully")

                sendMessage("DEVICE_CONNECTED:${android.os.Build.MODEL}")

                listenForCommands()

            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}")
                throw e
            }
        }
    }

    private suspend fun listenForCommands() {
        withContext(Dispatchers.IO) {
            try {
                while (socket?.isConnected == true) {
                    val command = reader?.readLine()
                    if (command != null) {
                        Log.d(TAG, "Received command: $command")
                        handleCommand(command)
                    } else {
                        // Connection closed
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading commands: ${e.message}")
            } finally {
                closeConnection()
            }
        }
    }

    private fun handleCommand(command: String) {
        when {
            command.startsWith("LAUNCH_APP:") -> {
                val packageName = command.substring("LAUNCH_APP:".length)
                launchApp(packageName)
            }
            command.startsWith("EXECUTE_SHELL:") -> {
                val shellCommand = command.substring("EXECUTE_SHELL:".length)
                executeShellCommand(shellCommand)
            }
            command == "GET_INSTALLED_APPS" -> {
                sendInstalledApps()
            }
            command == "GET_DEVICE_INFO" -> {
                sendDeviceInfo()
            }
            command == "SHUTDOWN" -> {
                stopSelf()
            }
            else -> {
                Log.w(TAG, "Unknown command: $command")
                sendMessage("ERROR:Unknown command")
            }
        }
    }

    private fun launchApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                sendMessage("SUCCESS:Launched $packageName")
            } else {
                sendMessage("ERROR:App not found: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app: ${e.message}")
            sendMessage("ERROR:${e.message}")
        }
    }

    private fun executeShellCommand(command: String) {
        try {
            val process = Runtime.getRuntime().exec(command)
            val output = process.inputStream.bufferedReader().readText()
            sendMessage("SHELL_OUTPUT:$output")
        } catch (e: Exception) {
            Log.e(TAG, "Error executing shell command: ${e.message}")
            sendMessage("ERROR:${e.message}")
        }
    }

    private fun sendInstalledApps() {
        try {
            val packages = packageManager.getInstalledPackages(0)
            val appList = packages.joinToString(",") { it.packageName }
            sendMessage("INSTALLED_APPS:$appList")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed apps: ${e.message}")
            sendMessage("ERROR:${e.message}")
        }
    }

    private fun sendDeviceInfo() {
        val info = buildString {
            append("DEVICE_INFO:")
            append("Model=${android.os.Build.MODEL},")
            append("Android=${android.os.Build.VERSION.RELEASE},")
            append("SDK=${android.os.Build.VERSION.SDK_INT},")
            append("Manufacturer=${android.os.Build.MANUFACTURER}")
        }
        sendMessage(info)
    }

    private fun sendMessage(message: String) {
        serviceScope.launch {
            try {
                writer?.println(message)
                Log.d(TAG, "Sent: $message")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message: ${e.message}")
            }
        }
    }

    private fun closeConnection() {
        try {
            writer?.close()
            reader?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing connection: ${e.message}")
        }
        writer = null
        reader = null
        socket = null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        closeConnection()
        Log.d(TAG, "Snorlax service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}