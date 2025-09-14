package com.b3n00n.snorlax.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.b3n00n.snorlax.services.RemoteClientService

class DevStopReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "DevStopReceiver"
    }

    // For custom tools: shell am broadcast -a com.b3n00n.snorlax.DEV_STOP_SERVICE -n com.b3n00n.snorlax/.receivers.DevStopReceiver
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received stop broadcast - stopping Snorlax service")

        val serviceIntent = Intent(context, RemoteClientService::class.java)
        context.stopService(serviceIntent)

        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(0)
    }
}