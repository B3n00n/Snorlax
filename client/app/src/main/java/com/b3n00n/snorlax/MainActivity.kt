package com.b3n00n.snorlax

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.b3n00n.snorlax.services.RemoteClientService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serviceIntent = Intent(this, RemoteClientService::class.java)
        startService(serviceIntent)

        finish()
    }
}