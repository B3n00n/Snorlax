package com.b3n00n.snorlax.monitoring

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.*
import java.io.IOException

/**
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 * TEMPORARY WORKAROUND - REMOVE THIS FILE WHEN NO LONGER NEEDED
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 *
 * This monitor detects when com.oculus.os.clearactivity becomes the foreground app
 * and automatically relaunches the previously active app 3 times to work around
 * the controller prompt issue.
 *
 * Requirements: android.permission.PACKAGE_USAGE_STATS
 */
class TemporaryOculusClearActivityWorkaround(
    private val context: Context
) {
    companion object {
        private const val TAG = "TempOculusWorkaround"
        private const val POLL_INTERVAL_MS = 500L
        private const val QUERY_WINDOW_MS = 2000L
        private const val TARGET_PACKAGE = "com.oculus.os.clearactivity"
        private const val RELAUNCH_COUNT = 3
        private const val RELAUNCH_DELAY_MS = 300L
        private const val COMBATICA_PACKAGE_PREFIX = "com.CombaticaLTD."
    }

    private var usageStatsManager: UsageStatsManager? = null
    private var isMonitoring = false
    private var previousForegroundApp: String? = null
    private var currentForegroundApp: String? = null

    private val monitoringScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitoringJob: Job? = null

    init {
        usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    }

    fun startMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "[TEMP WORKAROUND] Already monitoring")
            return
        }

        if (!hasUsageStatsPermission()) {
            Log.e(TAG, "[TEMP WORKAROUND] PACKAGE_USAGE_STATS permission not granted")
            return
        }

        Log.d(TAG, "[TEMP WORKAROUND] Starting Oculus clear activity monitor")
        isMonitoring = true

        monitoringJob = monitoringScope.launch {
            while (isActive && isMonitoring) {
                try {
                    val foregroundApp = getForegroundApp()

                    if (foregroundApp != null && foregroundApp != currentForegroundApp) {
                        if (currentForegroundApp != null &&
                            currentForegroundApp != TARGET_PACKAGE &&
                            currentForegroundApp!!.startsWith(COMBATICA_PACKAGE_PREFIX)) {
                            previousForegroundApp = currentForegroundApp
                            Log.d(TAG, "[TEMP WORKAROUND] Saved CombaticaLTD app: $previousForegroundApp")
                        }

                        currentForegroundApp = foregroundApp

                        // Check if Oculus clear activity is now in foreground and we have a CombaticaLTD app to relaunch
                        if (foregroundApp == TARGET_PACKAGE && previousForegroundApp != null) {
                            Log.i(TAG, "[TEMP WORKAROUND] Detected $TARGET_PACKAGE! Relaunching CombaticaLTD app: $previousForegroundApp")
                            relaunchPreviousApp()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[TEMP WORKAROUND] Error in monitoring loop", e)
                }

                delay(POLL_INTERVAL_MS)
            }
        }

        Log.d(TAG, "[TEMP WORKAROUND] Monitoring started")
    }

    fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }

        Log.d(TAG, "[TEMP WORKAROUND] Stopping monitor")
        isMonitoring = false
        monitoringJob?.cancel()
        monitoringJob = null
        currentForegroundApp = null
        previousForegroundApp = null
        Log.d(TAG, "[TEMP WORKAROUND] Monitor stopped")
    }

    private suspend fun relaunchPreviousApp() {
        val packageToRelaunch = previousForegroundApp ?: return

        repeat(RELAUNCH_COUNT) { attempt ->
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(packageToRelaunch)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    context.startActivity(intent)
                    Log.d(TAG, "[TEMP WORKAROUND] Relaunch attempt ${attempt + 1}/$RELAUNCH_COUNT: $packageToRelaunch")
                } else {
                    Log.w(TAG, "[TEMP WORKAROUND] No launch intent for $packageToRelaunch")
                    return
                }

                if (attempt < RELAUNCH_COUNT - 1) {
                    delay(RELAUNCH_DELAY_MS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "[TEMP WORKAROUND] Error relaunching app (attempt ${attempt + 1})", e)
            }
        }

        // Simulate Oculus home button press once
        simulateOculusButtonPress()
    }

    private fun simulateOculusButtonPress() {
        try {
            // KEY_FORWARD (keycode 125) - Oculus home button on controller
            val oculusHomeKeyCode = 125

            Log.d(TAG, "[TEMP WORKAROUND] Simulating Oculus home button (KEY_FORWARD / $oculusHomeKeyCode)...")

            val process = Runtime.getRuntime().exec(arrayOf("input", "keyevent", oculusHomeKeyCode.toString()))
            process.waitFor()

            Log.i(TAG, "[TEMP WORKAROUND] Sent Oculus home button keyevent")
        } catch (e: Exception) {
            Log.e(TAG, "[TEMP WORKAROUND] Error simulating Oculus button press", e)
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val currentTime = System.currentTimeMillis()
        val stats = usageStatsManager?.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - 1000,
            currentTime
        )
        return stats != null && stats.isNotEmpty()
    }

    private fun getForegroundApp(): String? {
        val currentTime = System.currentTimeMillis()
        val usageEvents = usageStatsManager?.queryEvents(currentTime - QUERY_WINDOW_MS, currentTime)
            ?: return null

        var foregroundApp: String? = null
        var latestTimestamp = 0L

        while (usageEvents.hasNextEvent()) {
            val event = UsageEvents.Event()
            usageEvents.getNextEvent(event)

            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && event.timeStamp > latestTimestamp) {
                foregroundApp = event.packageName
                latestTimestamp = event.timeStamp
            }
        }

        return foregroundApp
    }

    fun cleanup() {
        stopMonitoring()
        monitoringScope.cancel()
    }
}
