package com.b3n00n.snorlax

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("SnorlaxBoot", "Boot completed, starting Snorlax service")
            val serviceIntent = Intent(context, RemoteClientService::class.java)
            context.startService(serviceIntent)
        }
    }
}