package com.b3n00n.snorlax.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.b3n00n.snorlax.config.WifiConfigurationManager
import com.b3n00n.snorlax.network.WifiConnectionManager
import com.b3n00n.snorlax.services.RemoteClientService
import com.b3n00n.snorlax.utils.DeviceRestrictionManager
import kotlinx.coroutines.*

class WifiConfigurationActivity : ComponentActivity(), WifiConnectionManager.WifiConnectionListener {
    private lateinit var wifiConfigManager: WifiConfigurationManager
    private lateinit var wifiConnectionManager: WifiConnectionManager
    private lateinit var deviceRestrictionManager: DeviceRestrictionManager
    private lateinit var ssidEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var autoReconnectCheckBox: CheckBox
    private lateinit var statusText: TextView
    private lateinit var currentSsidText: TextView

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        wifiConfigManager = WifiConfigurationManager(this)
        wifiConnectionManager = WifiConnectionManager(this)
        wifiConnectionManager.setListener(this)
        deviceRestrictionManager = DeviceRestrictionManager(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishActivity()
            }
        })

        setContentView(createMainLayout())
        window.decorView.setBackgroundColor(Color.BLACK)

        updateCurrentConnectionStatus()
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
            addView(createCurrentConnectionSection())
            addView(createSsidSection())
            addView(createPasswordSection())
            addView(createAutoReconnectSection())
            addView(createStatusSection())
            addView(createButtonLayout())
            addView(createFooterText())
        }
    }

    private fun createTitleText(): TextView {
        return TextView(this).apply {
            text = "WiFi Configuration"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 60)
            gravity = Gravity.CENTER
        }
    }

    private fun createCurrentConnectionSection(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            currentSsidText = TextView(this@WifiConfigurationActivity).apply {
                text = "Current WiFi: Checking..."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTextColor(Color.CYAN)
                setPadding(0, 0, 0, 60)
                gravity = Gravity.CENTER
            }
            addView(currentSsidText)
        }
    }

    private fun createSsidSection(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            addView(TextView(this@WifiConfigurationActivity).apply {
                text = "WiFi Network Name (SSID):"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, 16)
            })

            ssidEditText = EditText(this@WifiConfigurationActivity).apply {
                setText(wifiConfigManager.getWifiSSID())
                hint = "Enter WiFi name"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                setBackgroundColor(Color.DKGRAY)
                setPadding(40, 40, 40, 40)
                inputType = InputType.TYPE_CLASS_TEXT
                gravity = Gravity.CENTER
            }
            addView(ssidEditText)
        }
    }

    private fun createPasswordSection(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            addView(TextView(this@WifiConfigurationActivity).apply {
                text = "WiFi Password:"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTextColor(Color.WHITE)
                setPadding(0, 60, 0, 16)
            })

            passwordEditText = EditText(this@WifiConfigurationActivity).apply {
                setText(wifiConfigManager.getWifiPassword())
                hint = "Enter password"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                transformationMethod = PasswordTransformationMethod.getInstance()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                setBackgroundColor(Color.DKGRAY)
                setPadding(40, 40, 40, 40)
                gravity = Gravity.CENTER
            }
            addView(passwordEditText)
        }
    }

    private fun createAutoReconnectSection(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 0)

            val checkboxLayout = LinearLayout(this@WifiConfigurationActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER

                autoReconnectCheckBox = CheckBox(this@WifiConfigurationActivity).apply {
                    isChecked = wifiConfigManager.isAutoReconnectEnabled()
                    scaleX = 1.5f
                    scaleY = 1.5f
                    setTextColor(Color.WHITE)
                }
                addView(autoReconnectCheckBox)

                addView(TextView(this@WifiConfigurationActivity).apply {
                    text = "  Auto-reconnect if connection drops"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                    setTextColor(Color.WHITE)
                    setPadding(20, 0, 0, 0)
                })
            }
            addView(checkboxLayout)

            addView(TextView(this@WifiConfigurationActivity).apply {
                text = "When enabled: Restricts WiFi settings access & auto-reconnects every 15s"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(0, 10, 0, 0)
            })
        }
    }

    private fun createStatusSection(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 0)

            statusText = TextView(this@WifiConfigurationActivity).apply {
                text = ""
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(Color.YELLOW)
                gravity = Gravity.CENTER
            }
            addView(statusText)
        }
    }

    private fun createButtonLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 60, 0, 0)
            gravity = Gravity.CENTER

            addView(createSaveButton())
            addView(TextView(this@WifiConfigurationActivity).apply { text = "    " })
            addView(createClearButton())
        }
    }

    private fun createSaveButton(): Button {
        return Button(this).apply {
            text = "Save & Connect"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setPadding(60, 40, 60, 40)
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            setOnClickListener { saveAndConnect() }
        }
    }

    private fun createClearButton(): Button {
        return Button(this).apply {
            text = "Clear"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setPadding(60, 40, 60, 40)
            setBackgroundColor(Color.parseColor("#F44336"))
            setTextColor(Color.WHITE)
            setOnClickListener { clearConfiguration() }
        }
    }

    private fun createFooterText(): TextView {
        return TextView(this).apply {
            text = "\nMade by B3n00n\nCombatica LTD"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 60, 0, 0)
        }
    }

    private fun updateCurrentConnectionStatus() {
        val currentSsid = wifiConnectionManager.getCurrentSsid()
        currentSsidText.text = if (currentSsid != null) {
            "Current WiFi: $currentSsid"
        } else {
            "Current WiFi: Not connected"
        }
    }

    private fun saveAndConnect() {
        val ssid = ssidEditText.text.toString().trim()
        val password = passwordEditText.text.toString()
        val autoReconnect = autoReconnectCheckBox.isChecked

        if (ssid.isEmpty()) {
            statusText.text = "Please enter SSID"
            statusText.setTextColor(Color.RED)
            return
        }

        val restrictionApplied = deviceRestrictionManager.setWifiRestriction(autoReconnect)
        if (!restrictionApplied && autoReconnect) {
            statusText.text = "Warning: Could not apply WiFi restriction"
            statusText.setTextColor(Color.YELLOW)
        }

        wifiConfigManager.setWifiConfig(ssid, password, autoReconnect)
        statusText.text = "Connecting to $ssid..."
        statusText.setTextColor(Color.YELLOW)

        activityScope.launch {
            val success = withContext(Dispatchers.IO) {
                wifiConnectionManager.connectToWifi(ssid, password)
            }

            if (success) {
                statusText.text = "Connection initiated..."
            } else {
                statusText.text = "Connection failed"
                statusText.setTextColor(Color.RED)
            }
        }

        // Notify service about WiFi config change
        val serviceIntent = Intent(this, RemoteClientService::class.java).apply {
            action = RemoteClientService.ACTION_WIFI_CONFIG_CHANGED
        }
        startService(serviceIntent)
    }

    private fun clearConfiguration() {
        wifiConfigManager.clearWifiConfig()
        ssidEditText.setText("")
        passwordEditText.setText("")
        autoReconnectCheckBox.isChecked = true
        statusText.text = "Configuration cleared"
        statusText.setTextColor(Color.GREEN)

        deviceRestrictionManager.setWifiRestriction(false)

        // Stop auto-reconnect
        wifiConnectionManager.stopAutoReconnect()
    }

    override fun onWifiConnected(ssid: String) {
        runOnUiThread {
            statusText.text = "Connected to $ssid!"
            statusText.setTextColor(Color.GREEN)
            updateCurrentConnectionStatus()

            // Close activity after successful connection
            activityScope.launch {
                delay(2000)
                finishActivity()
            }
        }
    }

    override fun onWifiDisconnected() {
        runOnUiThread {
            statusText.text = "WiFi disconnected"
            statusText.setTextColor(Color.RED)
            updateCurrentConnectionStatus()
        }
    }

    override fun onWifiConnectionFailed(reason: String) {
        runOnUiThread {
            statusText.text = "Failed: $reason"
            statusText.setTextColor(Color.RED)
        }
    }

    private fun finishActivity() {
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}