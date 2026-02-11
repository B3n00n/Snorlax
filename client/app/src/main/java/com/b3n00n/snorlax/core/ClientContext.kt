package com.b3n00n.snorlax.core

import android.content.Context
import com.b3n00n.snorlax.models.DeviceInfo
import com.b3n00n.snorlax.monitoring.ForegroundAppMonitor

/**
 * Global context holder for the Snorlax client application.
 *
 * This singleton provides shared access to application-wide resources
 * without requiring every handler to accept Context in their constructor.
 * Must be initialized before any handlers are used.
 */
object ClientContext {
    private var _context: Context? = null
    private var _deviceInfo: DeviceInfo? = null
    private var _foregroundAppMonitor: ForegroundAppMonitor? = null

    /**
     * Initialize the client context. Must be called once during application startup.
     */
    fun initialize(context: Context) {
        _context = context.applicationContext
        _deviceInfo = DeviceInfo(context)
        _foregroundAppMonitor = ForegroundAppMonitor(context.applicationContext)
    }

    /**
     * Get the application context.
     * @throws IllegalStateException if not initialized
     */
    val context: Context
        get() = _context ?: throw IllegalStateException("ClientContext not initialized")

    /**
     * Get the device information.
     * @throws IllegalStateException if not initialized
     */
    val deviceInfo: DeviceInfo
        get() = _deviceInfo ?: throw IllegalStateException("ClientContext not initialized")

    val foregroundAppMonitor: ForegroundAppMonitor
        get() = _foregroundAppMonitor ?: throw IllegalStateException("ClientContext not initialized")
}
