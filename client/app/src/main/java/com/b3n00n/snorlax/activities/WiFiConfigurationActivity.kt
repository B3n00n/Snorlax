package com.b3n00n.snorlax.activities

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
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
import com.b3n00n.snorlax.config.WiFiConfigurationManager
import com.b3n00n.snorlax.services.RemoteClientService

class WiFiConfigurationActivity : ComponentActivity() {
    private lateinit var configManager: WiFiConfigurationManager
    private lateinit var ssidEditText: EditText
    private lateinit var passwordEditText: EditText
    private var passwordVisible = false

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

        configManager = WiFiConfigurationManager(this)

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
            addView(createClearButton())
            addView(createFooterText())
        }
    }

    private fun createTitleText(): TextView {
        return TextView(this).apply {
            text = "WiFi Configuration"
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

            addView(TextView(this@WiFiConfigurationActivity).apply {
                text = "Current WiFi"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(textSecondary)
                gravity = Gravity.CENTER
            })

            val currentSsid = configManager.getWifiSsid()
            addView(TextView(this@WiFiConfigurationActivity).apply {
                text = if (currentSsid.isNullOrEmpty()) "Not configured" else currentSsid
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(if (currentSsid.isNullOrEmpty()) textSecondary else accentLight)
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

            addView(TextView(this@WiFiConfigurationActivity).apply {
                text = "NETWORK NAME (SSID)"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(accentColor)
                setPadding(10, 0, 0, 10)
                letterSpacing = 0.1f
            })

            ssidEditText = EditText(this@WiFiConfigurationActivity).apply {
                setText(configManager.getWifiSsid() ?: "")
                hint = "Enter WIFI Name"
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
            addView(ssidEditText)

            addView(TextView(this@WiFiConfigurationActivity).apply {
                text = "PASSWORD"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(accentColor)
                setPadding(10, 40, 0, 10)
                letterSpacing = 0.1f
            })

            passwordEditText = EditText(this@WiFiConfigurationActivity).apply {
                setText(configManager.getWifiPassword() ?: "")
                hint = "Enter Password"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTextColor(textPrimary)
                setHintTextColor(Color.parseColor("#666666"))
                inputType = InputType.TYPE_CLASS_TEXT
                transformationMethod = PasswordTransformationMethod.getInstance()
                gravity = Gravity.CENTER

                background = GradientDrawable().apply {
                    setColor(bgColor)
                    cornerRadius = 15f
                }
                setPadding(30, 30, 30, 30)
            }
            addView(passwordEditText)

            addView(Button(this@WiFiConfigurationActivity).apply {
                text = "SHOW PASSWORD"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(accentLight)
                setPadding(20, 20, 20, 20)
                letterSpacing = 0.05f

                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(2, accentLight)
                    cornerRadius = 15f
                }

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.topMargin = 20
                params.gravity = Gravity.CENTER
                layoutParams = params

                setOnClickListener {
                    passwordVisible = !passwordVisible
                    if (passwordVisible) {
                        passwordEditText.transformationMethod = null
                        text = "HIDE PASSWORD"
                    } else {
                        passwordEditText.transformationMethod = PasswordTransformationMethod.getInstance()
                        text = "SHOW PASSWORD"
                    }
                    passwordEditText.setSelection(passwordEditText.text.length)
                }
            })
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

    private fun createClearButton(): Button {
        return Button(this).apply {
            text = "CLEAR WIFI CREDENTIALS"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#FF6B6B"))
            setPadding(40, 30, 40, 30)
            letterSpacing = 0.08f

            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(2, Color.parseColor("#FF6B6B"))
                cornerRadius = 25f
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 20
            layoutParams = params

            setOnClickListener { clearConfiguration() }
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
        val ssid = ssidEditText.text.toString().trim()
        val password = passwordEditText.text.toString()

        if (configManager.isValidSsid(ssid) && configManager.isValidPassword(password)) {
            configManager.setWifiConfig(ssid, password)

            Toast.makeText(
                this,
                "WiFi configuration saved",
                Toast.LENGTH_SHORT
            ).show()

            restartService()
        } else {
            val errorMessage = when {
                !configManager.isValidSsid(ssid) -> "SSID must be 1-32 characters"
                !configManager.isValidPassword(password) -> "Password invalid"
                else -> "Invalid configuration"
            }
            Toast.makeText(
                this,
                errorMessage,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun clearConfiguration() {
        configManager.clearWifiConfig()

        Toast.makeText(
            this,
            "WiFi configuration cleared",
            Toast.LENGTH_SHORT
        ).show()

        restartService()
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
