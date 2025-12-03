package com.b3n00n.snorlax.helpers

import android.content.Context
import android.content.Intent
import android.util.Log
import com.b3n00n.snorlax.services.RemoteClientService
import kotlinx.coroutines.delay

/**
 * Helper object for restarting the RemoteClientService.
 *
 * Provides a centralized way to restart the service with proper delays
 * to ensure clean shutdown and startup.
 */
object ServiceRestartHelper {
    private const val TAG = "ServiceRestartHelper"

    private const val STOP_DELAY_MS = 500L
    private const val START_DELAY_MS = 500L

    suspend fun restartRemoteClientService(context: Context) {
        delay(STOP_DELAY_MS)
        try {
            Log.d(TAG, "Restarting RemoteClientService")
            val serviceIntent = Intent(context, RemoteClientService::class.java)
            context.stopService(serviceIntent)
            delay(START_DELAY_MS)
            context.startService(serviceIntent)
            Log.d(TAG, "RemoteClientService restarted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting service", e)
            throw e
        }
    }
}
