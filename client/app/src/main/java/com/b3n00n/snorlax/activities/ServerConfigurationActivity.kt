package com.b3n00n.snorlax.activities

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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

    private val bgColor = Color.parseColor("#0D0D0D")
    private val surfaceColor = Color.parseColor("#1A1A1A")
    private val accentColor = Color.parseColor("#4FC3F7")
    private val accentLight = Color.parseColor("#81D4FA")
    private val textPrimary = Color.parseColor("#EDEDED")
    private val textSecondary = Color.parseColor("#9E9E9E")

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
        window.decorView.setBackgroundColor(bgColor)
    }

    private fun createMainLayout(): ScrollView {
        return ScrollView(this).apply {
            setBackgroundColor(bgColor)
            addView(createContentLayout())
        }
    }

    private fun createContentLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
            setBackgroundColor(bgColor)
            gravity = Gravity.CENTER_HORIZONTAL

            addView(createTitleText())
            addView(createCurrentConfigCard())
            addView(createInputCard())
            addView(createSaveButton())
            addView(createFooterText())
        }
    }

    private fun createTitleText(): TextView {
        return TextView(this).apply {
            text = "Snorlax Config"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            setTextColor(accentColor)
            setPadding(0, 0, 0, 30)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }

    private fun createCurrentConfigCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(surfaceColor)
                cornerRadius = 20f
            }
            setPadding(40, 30, 40, 30)

            addView(TextView(this@ServerConfigurationActivity).apply {
                text = "Current Server"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(textSecondary)
                gravity = Gravity.CENTER
            })

            addView(TextView(this@ServerConfigurationActivity).apply {
                text = "${configManager.getServerIp()}:${configManager.getServerPort()}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(accentLight)
                gravity = Gravity.CENTER
                setPadding(0, 8, 0, 0)
            })
        }
    }

    private fun createInputCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(surfaceColor)
                cornerRadius = 20f
            }
            setPadding(40, 40, 40, 40)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 40
            layoutParams = params

            // IP Input
            addView(TextView(this@ServerConfigurationActivity).apply {
                text = "IP ADDRESS"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(accentColor)
                setPadding(10, 0, 0, 10)
                letterSpacing = 0.1f
            })

            ipEditText = EditText(this@ServerConfigurationActivity).apply {
                setText(configManager.getServerIp())
                hint = "192.168.1.100"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTextColor(textPrimary)
                setHintTextColor(Color.parseColor("#666666"))
                inputType = InputType.TYPE_CLASS_TEXT
                gravity = Gravity.CENTER

                background = GradientDrawable().apply {
                    setColor(bgColor)
                    cornerRadius = 15f
                }
                setPadding(30, 30, 30, 30)
            }
            addView(ipEditText)

            // Port Input
            addView(TextView(this@ServerConfigurationActivity).apply {
                text = "PORT"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(accentColor)
                setPadding(10, 40, 0, 10)
                letterSpacing = 0.1f
            })

            portEditText = EditText(this@ServerConfigurationActivity).apply {
                setText(configManager.getServerPort().toString())
                hint = "43572"
                inputType = InputType.TYPE_CLASS_NUMBER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTextColor(textPrimary)
                setHintTextColor(Color.parseColor("#666666"))
                gravity = Gravity.CENTER

                background = GradientDrawable().apply {
                    setColor(bgColor)
                    cornerRadius = 15f
                }
                setPadding(30, 30, 30, 30)
            }
            addView(portEditText)
        }
    }

    private fun createSaveButton(): Button {
        return Button(this).apply {
            text = "SAVE & RESTART"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(bgColor)
            setPadding(60, 40, 60, 40)
            letterSpacing = 0.1f

            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = 30f
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 50
            layoutParams = params

            setOnClickListener { saveConfiguration() }
        }
    }

    private fun createFooterText(): TextView {
        return TextView(this).apply {
            text = "\nMade by B3n00n\n Combatica LTD"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#555555"))
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 0)
        }
    }

    private fun saveConfiguration() {
        val newIp = ipEditText.text.toString().trim()
        val newPort = portEditText.text.toString().toIntOrNull() ?: 0

        if (configManager.isValidIpAddress(newIp) && configManager.isValidPort(newPort)) {
            configManager.setServerConfig(newIp, newPort)

            Toast.makeText(
                this,
                "Configuration saved",
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