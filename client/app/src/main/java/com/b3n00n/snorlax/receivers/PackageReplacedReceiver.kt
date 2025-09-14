package com.b3n00n.snorlax.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.b3n00n.snorlax.services.RemoteClientService

class PackageReplacedReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "PackageReplacedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d(TAG, "Snorlax was updated, starting service")
            val serviceIntent = Intent(context, RemoteClientService::class.java)
            context.startService(serviceIntent)
        }
    }
}