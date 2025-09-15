package com.b3n00n.snorlax.utils

import android.content.Context
import android.media.AudioManager
import android.util.Log

class VolumeManager(private val context: Context) {
    companion object {
        private const val TAG = "VolumeManager"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun getVolumeInfo(): VolumeInfo {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumePercentage = if (maxVolume > 0) {
            (currentVolume * 100) / maxVolume
        } else {
            0
        }

        Log.d(TAG, "Current volume: $currentVolume/$maxVolume ($volumePercentage%)")

        return VolumeInfo(
            currentVolume = currentVolume,
            maxVolume = maxVolume,
            volumePercentage = volumePercentage
        )
    }

    fun setVolume(percentage: Int): Boolean {
        return try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = (percentage * maxVolume) / 100

            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                targetVolume.coerceIn(0, maxVolume),
                AudioManager.FLAG_SHOW_UI
            )

            Log.d(TAG, "Volume set to $targetVolume/$maxVolume ($percentage%)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
            false
        }
    }

    data class VolumeInfo(
        val currentVolume: Int,
        val maxVolume: Int,
        val volumePercentage: Int
    )
}