package com.b3n00n.snorlax.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.b3n00n.snorlax.config.ServerConfigurationManager
import com.b3n00n.snorlax.services.RemoteClientService

class ServerConfigurationActivity : ComponentActivity() {
    private lateinit var configManager: ServerConfigurationManager
    private lateinit var ipEditText: EditText
    private lateinit var portEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        configManager = ServerConfigurationManager(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishActivity()
            }
        })

        setContentView(createMainLayout())

        window.decorView.setBackgroundColor(Color.BLACK)
    }

    private fun createMainLayout(): ScrollView {
        return ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(createContentLayout())
        }
    }

    private fun createContentLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 80, 80, 80)
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER_HORIZONTAL

            addView(createTitleText())
            addView(createCurrentConfigText())
            addView(createIpSection())
            addView(createPortSection())
            addView(createButtonLayout())
            addView(createFooterText())
        }
    }

    private fun createTitleText(): TextView {
        return TextView(this).apply {
            text = "Snorlax Configuration"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 60)
            gravity = Gravity.CENTER
        }
    }

    private fun createCurrentConfigText(): TextView {
        return TextView(this).apply {
            text = "Current Server: ${configManager.getServerIp()}:${configManager.getServerPort()}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(Color.CYAN)
            setPadding(0, 0, 0, 60)
            gravity = Gravity.CENTER
        }
    }

    private fun createIpSection(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            addView(TextView(this@ServerConfigurationActivity).apply {
                text = "Server IP Address:"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, 16)
            })

            ipEditText = EditText(this@ServerConfigurationActivity).apply {
                setText(configManager.getServerIp())
                hint = "192.168.1.100"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                setBackgroundColor(Color.DKGRAY)
                setPadding(40, 40, 40, 40)
                inputType = InputType.TYPE_CLASS_TEXT
                gravity = Gravity.CENTER
            }
            addView(ipEditText)
        }
    }

    private fun createPortSection(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            addView(TextView(this@ServerConfigurationActivity).apply {
                text = "Server Port:"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTextColor(Color.WHITE)
                setPadding(0, 60, 0, 16)
            })

            portEditText = EditText(this@ServerConfigurationActivity).apply {
                setText(configManager.getServerPort().toString())
                hint = "e.g., 8888"
                inputType = InputType.TYPE_CLASS_NUMBER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                setBackgroundColor(Color.DKGRAY)
                setPadding(40, 40, 40, 40)
                gravity = Gravity.CENTER
            }
            addView(portEditText)
        }
    }

    private fun createButtonLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 80, 0, 0)
            gravity = Gravity.CENTER

            addView(createSaveButton())
            addView(createSpacer())
        }
    }

    private fun createSaveButton(): Button {
        return Button(this).apply {
            text = "Save & Apply"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setPadding(60, 40, 60, 40)
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            setOnClickListener { saveConfiguration() }
        }
    }

    private fun createSpacer(): TextView {
        return TextView(this).apply {
            text = "    "
        }
    }

    private fun createFooterText(): TextView {
        return TextView(this).apply {
            text = "\nMade by B3n00n\n Combatica LTD"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 60, 0, 0)
        }
    }

    private fun saveConfiguration() {
        val newIp = ipEditText.text.toString().trim()
        val newPort = portEditText.text.toString().toIntOrNull() ?: 0

        if (configManager.isValidIpAddress(newIp) && configManager.isValidPort(newPort)) {
            configManager.setServerConfig(newIp, newPort)

            Toast.makeText(
                this,
                "Configuration saved!",
                Toast.LENGTH_SHORT
            ).show()

            restartService()
        } else {
            Toast.makeText(
                this,
                "Invalid IP or port",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun restartService() {
        val serviceIntent = Intent(this, RemoteClientService::class.java)
        stopService(serviceIntent)

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            startService(serviceIntent)
            finishActivity()
        }, 500)
    }

    private fun finishActivity() {
        finish()
    }
}