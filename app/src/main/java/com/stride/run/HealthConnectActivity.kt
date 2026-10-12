package com.stride.run

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Health Connect consent and manual refresh. The sync itself lives in [HealthSync]. */
class HealthConnectActivity : ComponentActivity() {
    private lateinit var statusText: TextView
    private lateinit var theme: KadammTheme
    private var client: HealthConnectClient? = null
    private var refreshAfterSamsungHealth = false

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted: Set<String> ->
        if (granted.containsAll(HealthSync.readSteps)) syncSteps()
        else showStatus("Permission not granted. Kadamm keeps counting with your phone's step sensor, and you can connect later.")
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density + .5f).toInt()

    private fun action(label: String, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (primary) theme.accentOn else theme.text)
        background = GradientDrawable().apply {
            setColor(if (primary) theme.accent else theme.surfaceRaised)
            cornerRadius = dp(12f).toFloat()
            setStroke(dp(1f), theme.border)
        }
        isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(50f)).apply { topMargin = dp(10f) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        theme = KadammTheme.resolve(this, getSharedPreferences("kadamm_settings", MODE_PRIVATE).getString("themeMode", "system"))
        window.statusBarColor = theme.background
        window.navigationBarColor = theme.background
        window.decorView.systemUiVisibility =
            if (theme.dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22f), dp(28f), dp(22f), dp(28f))
        }
        layout.addView(TextView(this).apply {
            text = "HEALTH CONNECT"
            textSize = 10f
            letterSpacing = .14f
            setTextColor(theme.accent)
        })
        layout.addView(TextView(this).apply {
            text = "Bring in your daily steps"
            textSize = 26f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(theme.text)
            setPadding(0, dp(8f), 0, dp(14f))
        })
        statusText = TextView(this).apply {
            textSize = 15f
            setTextColor(theme.text)
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
            background = GradientDrawable().apply {
                setColor(theme.surface)
                cornerRadius = dp(16f).toFloat()
                setStroke(dp(1f), theme.border)
            }
        }
        layout.addView(statusText)
        layout.addView(action("Connect / refresh steps", true) { connect() })
        layout.addView(TextView(this).apply {
            text = "Samsung Health keeps its step count private until you switch on sharing: Samsung Health > Settings > Health Connect > allow Steps. Kadamm only ever reads steps, and nothing leaves this phone."
            textSize = 13f
            setTextColor(theme.textMuted)
            setPadding(0, dp(18f), 0, dp(2f))
        })
        layout.addView(action("Open Samsung Health", false) {
            val launch = packageManager.getLaunchIntentForPackage("com.sec.android.app.shealth")
            if (launch != null) {
                refreshAfterSamsungHealth = true
                startActivity(launch)
            } else showStatus("Samsung Health is not installed. Connect another step source to Health Connect, then refresh.")
        })
        layout.addView(action("Done", false) { finish() })
        val scroll = ScrollView(this).apply {
            setBackgroundColor(theme.background)
            isFillViewport = true
            fitsSystemWindows = true
            addView(layout)
        }
        setContentView(scroll)

        when (HealthConnectClient.getSdkStatus(this)) {
            HealthConnectClient.SDK_AVAILABLE -> {
                client = HealthConnectClient.getOrCreate(this)
                lifecycleScope.launch {
                    if (client!!.permissionController.getGrantedPermissions().containsAll(HealthSync.readSteps)) syncSteps()
                    else showStatus("Health Connect is available. Grant step access to add steps counted by Samsung Health and other apps.")
                }
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> showStatus("Health Connect needs to be installed or updated. Do that, then come back and tap Connect / refresh steps.")
            else -> showStatus("Health Connect is unavailable on this device. Kadamm keeps counting with your phone's step sensor.")
        }
    }

    override fun onResume() {
        super.onResume()
        if (refreshAfterSamsungHealth) {
            refreshAfterSamsungHealth = false
            connect()
        }
    }

    private fun connect() {
        val hc = client
        if (hc == null) {
            if (HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
                showStatus("Install or update Health Connect from Google Play, then try again.")
            } else showStatus("Health Connect is not available on this device.")
            return
        }
        lifecycleScope.launch {
            if (hc.permissionController.getGrantedPermissions().containsAll(HealthSync.readSteps)) syncSteps()
            else permissionLauncher.launch(HealthSync.readSteps)
        }
    }

    private fun syncSteps() {
        val hc = client ?: return
        showStatus("Syncing today's step records…")
        lifecycleScope.launch {
            try {
                statusText.text = HealthSync.sync(applicationContext, hc)
                setResult(RESULT_OK)
            } catch (error: Exception) {
                showStatus("Sync failed. Check Health Connect permissions and try again. ${error.message ?: ""}")
            }
        }
    }

    private fun showStatus(message: String) {
        if (::statusText.isInitialized) statusText.text = message
        getSharedPreferences("kadamm_health", MODE_PRIVATE).edit().putString("healthStatus", message).apply()
    }
}
