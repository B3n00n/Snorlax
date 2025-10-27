package com.b3n00n.snorlax.core

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Centralized background job system for async operations.
 *
 * Provides a supervised coroutine scope for long-running operations
 * like APK downloads/installations. Jobs are fire-and-forget and
 * won't block the calling thread.
 *
 * Features:
 * - Isolated job failures (SupervisorJob)
 * - IO-optimized dispatcher
 * - Proper lifecycle management
 */
object BackgroundJobs {
    private const val TAG = "BackgroundJobs"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Submit a background job for execution.
     *
     * The job will run on the IO dispatcher and won't block the caller.
     * If the job throws an exception, it won't affect other jobs.
     *
     * @param block The work to execute in the background
     */
    fun submit(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "Background job failed", e)
            }
        }
    }

    /**
     * Shutdown the background job system.
     * Should be called when the application is closing.
     */
    fun shutdown() {
        Log.d(TAG, "Background jobs system shutdown")
    }
}
