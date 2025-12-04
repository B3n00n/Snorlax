package com.b3n00n.snorlax.utils

import android.content.Context
import android.media.MediaPlayer
import android.util.Log

/**
 * Manages audio playback for command notifications.
 * Uses MediaPlayer for Quest compatibility.
 * Caches players per resource ID for performance.
 */
object SoundManager {
    private const val TAG = "SoundManager"
    private var context: Context? = null
    private val players = mutableMapOf<Int, MediaPlayer>()

    fun initialize(context: Context) {
        this.context = context.applicationContext
        Log.d(TAG, "Initialized")
    }

    fun play(resId: Int, volume: Float = 1f) {
        val ctx = context ?: return

        try {
            val player = players.getOrPut(resId) {
                MediaPlayer.create(ctx, resId).also {
                    Log.d(TAG, "Created player for resource $resId")
                }
            }

            player.setVolume(volume, volume)
            player.seekTo(0)
            player.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play sound", e)
            players.remove(resId)?.release()
        }
    }

    fun release() {
        players.values.forEach { it.release() }
        players.clear()
        context = null
        Log.d(TAG, "Released")
    }
}
