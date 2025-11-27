package com.b3n00n.snorlax.handlers.impl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.b3n00n.snorlax.R
import com.b3n00n.snorlax.core.ClientContext
import com.b3n00n.snorlax.handlers.IPacketHandler
import com.b3n00n.snorlax.handlers.PacketHandler
import com.b3n00n.snorlax.network.NetworkClient
import com.b3n00n.snorlax.protocol.MessageOpcode
import com.b3n00n.snorlax.protocol.PacketReader

/**
 * Handles DisplayMessage command (0x50): [message: String]
 * Displays a system notification with "ALERT" as title and the message as content
 */
@PacketHandler(MessageOpcode.DISPLAY_MESSAGE)
class DisplayMessageHandler : IPacketHandler {
    companion object {
        private const val TAG = "DisplayMessageHandler"
        private const val CHANNEL_ID = "snorlax_messages"
        private var notificationId = 1000
    }

    override fun handle(reader: PacketReader, client: NetworkClient) {
        try {
            val message = reader.readString()

            Log.d(TAG, "Received message: message='$message'")

            val context = ClientContext.context
            ensureNotificationChannel(context)
            showNotification(context, message)

            Log.d(TAG, "Notification displayed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying message", e)
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Server Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Messages from Snorlax server"
            setShowBadge(true)
            enableLights(true)
            lightColor = Color.CYAN
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun showNotification(context: Context, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("ALERT")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(notificationId++, notification)
    }
}
