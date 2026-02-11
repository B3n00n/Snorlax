package com.b3n00n.snorlax.monitoring

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.*

/**
 * Monitors the foreground application on the device and notifies listeners when the active app changes.
 * This monitor uses UsageStatsManager to track which app is currently in the foreground,
 * Requires: android.permission.PACKAGE_USAGE_STATS
 */
class ForegroundAppMonitor(
    private val context: Context
) {
    companion object {
        private const val TAG = "ForegroundAppMonitor"
        private const val POLL_INTERVAL_MS = 1000L // Check every 1 second
        private const val QUERY_WINDOW_MS = 3000L // Query events from last 3 seconds
    }

    private var usageStatsManager: UsageStatsManager? = null
    private var packageManager: PackageManager? = null
    private var listener: ForegroundAppListener? = null

    private var isMonitoring = false
    private var currentForegroundApp: String? = null

    // Coroutine scope for background polling
    private val monitoringScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitoringJob: Job? = null

    data class ForegroundAppInfo(val packageName: String, val appName: String)

    interface ForegroundAppListener {
        /**
         * Called when the foreground app changes
         * @param packageName The package name of the new foreground app
         * @param appName The human-readable name of the app (or package name if name unavailable)
         */
        fun onForegroundAppChanged(packageName: String, appName: String)
    }

    init {
        usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        packageManager = context.packageManager
    }

    /**
     * Starts monitoring the foreground application.
     * Polls UsageStatsManager at regular intervals to detect app changes.
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "Already monitoring foreground app")
            return
        }

        if (!hasUsageStatsPermission()) {
            Log.e(TAG, "PACKAGE_USAGE_STATS permission not granted. Cannot monitor foreground apps.")
            return
        }

        Log.d(TAG, "Starting foreground app monitoring")
        isMonitoring = true

        // Start the monitoring coroutine
        monitoringJob = monitoringScope.launch {
            while (isActive && isMonitoring) {
                try {
                    val foregroundApp = getForegroundApp()

                    if (foregroundApp != null && foregroundApp != currentForegroundApp) {
                        val appName = getAppName(foregroundApp)
                        currentForegroundApp = foregroundApp

                        Log.d(TAG, "Foreground app changed: $appName ($foregroundApp)")

                        // Notify listener on main thread if needed, or keep it on background thread
                        withContext(Dispatchers.Main) {
                            listener?.onForegroundAppChanged(foregroundApp, appName)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error polling foreground app", e)
                }

                delay(POLL_INTERVAL_MS)
            }
        }

        Log.d(TAG, "Foreground app monitoring started")
    }

    /**
     * Stops monitoring the foreground application and cleans up resources.
     */
    fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }

        Log.d(TAG, "Stopping foreground app monitoring")

        try {
            isMonitoring = false
            monitoringJob?.cancel()
            monitoringJob = null
            currentForegroundApp = null

            Log.d(TAG, "Foreground app monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground app monitoring", e)
        }
    }

    /**
     * Sets the listener that will receive foreground app change notifications.
     */
    fun setListener(listener: ForegroundAppListener) {
        this.listener = listener
    }

    /**
     * Gets the currently active foreground application package name.
     * @return The package name of the foreground app, or null if unable to determine
     */
    fun getCurrentForegroundApp(): String? {
        return currentForegroundApp
    }

    /**
     * Performs a fresh query to determine the current foreground application.
     * Unlike [getCurrentForegroundApp], this does not rely on cached state from the polling loop.
     * Safe to call from any thread.
     * @return The foreground app info (package name + human-readable name), or null if unable to determine
     */
    fun queryCurrentForegroundAppInfo(): ForegroundAppInfo? {
        if (!hasUsageStatsPermission()) return null
        val packageName = getForegroundApp() ?: return null
        val appName = getAppName(packageName)
        return ForegroundAppInfo(packageName, appName)
    }

    /**
     * Checks if the app has PACKAGE_USAGE_STATS permission granted.
     * This permission is required to access UsageStatsManager.
     */
    private fun hasUsageStatsPermission(): Boolean {
        val currentTime = System.currentTimeMillis()
        val stats = usageStatsManager?.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - 1000,
            currentTime
        )
        // If we can query usage stats, permission is granted
        return stats != null && stats.isNotEmpty()
    }

    /**
     * Queries UsageStatsManager to determine the current foreground app.
     * Looks at recent usage events to find the most recent MOVE_TO_FOREGROUND event.
     * Uses a configurable window to ensure we catch app launches that might have a slight delay.
     */
    private fun getForegroundApp(): String? {
        val currentTime = System.currentTimeMillis()
        // Query events from the last QUERY_WINDOW_MS to catch delayed MOVE_TO_FOREGROUND events
        val usageEvents = usageStatsManager?.queryEvents(currentTime - QUERY_WINDOW_MS, currentTime)
            ?: return null

        var foregroundApp: String? = null
        var latestTimestamp = 0L

        while (usageEvents.hasNextEvent()) {
            val event = UsageEvents.Event()
            usageEvents.getNextEvent(event)

            // Look for the most recent MOVE_TO_FOREGROUND event
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && event.timeStamp > latestTimestamp) {
                foregroundApp = event.packageName
                latestTimestamp = event.timeStamp
            }
        }

        return foregroundApp
    }

    /**
     * Gets the human-readable application name from a package name.
     * Falls back to package name if the app name cannot be determined.
     */
    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager?.getApplicationInfo(packageName, 0)
            appInfo?.let { packageManager?.getApplicationLabel(it).toString() } ?: packageName
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Package not found: $packageName")
            packageName
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app name for $packageName", e)
            packageName
        }
    }

    fun cleanup() {
        stopMonitoring()
        monitoringScope.cancel()
    }
}
