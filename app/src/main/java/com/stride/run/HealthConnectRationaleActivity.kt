package com.stride.run

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/** Privacy rationale opened from the Health Connect permission screen. */
class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(48, 48, 48, 48)
        }
        content.addView(TextView(this).apply {
            text = "How Kadamm uses your health data"
            textSize = 24f
        })
        content.addView(TextView(this).apply {
            text = "Kadamm requests permission to read your step count from Health Connect so your daily total can include steps recorded by your phone or other connected apps. Kadamm uses this data only to show your daily progress. Steps during Kadamm sessions are counted from the session itself and are excluded from imported time ranges to reduce double-counting. Your Health Connect step data stays on this device and is not sent to Kadamm servers. You can change this permission at any time in Android Settings > Health Connect > App permissions > Kadamm."
            textSize = 16f
            setPadding(0, 24, 0, 0)
        })
        setContentView(content)
    }
}
