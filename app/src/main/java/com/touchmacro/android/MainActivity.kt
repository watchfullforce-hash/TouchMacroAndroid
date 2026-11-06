package com.touchmacro.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var count: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 38, 28, 28)
            setBackgroundColor(0xFF101318.toInt())
        }
        root.addView(TextView(this).apply {
            text = "TOUCHMACRO"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
        })
        status = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFFB9C2CE.toInt())
            setPadding(0, 24, 0, 12)
        }
        count = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(0xFF68D391.toInt())
            setPadding(0, 8, 0, 22)
        }
        root.addView(status)
        root.addView(count)
        root.addView(makeButton("ENABLE ACCESSIBILITY SERVICE") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(makeButton("START RECORDING") {
            prefs().edit().putBoolean("recording", true).apply()
            refresh()
        })
        root.addView(makeButton("STOP RECORDING") {
            prefs().edit().putBoolean("recording", false).apply()
            refresh()
        })
        root.addView(makeButton("PLAY MACRO") {
            MacroAccessibilityService.instance?.play()
            refresh()
        })
        root.addView(makeButton("CLEAR MACRO") {
            prefs().edit().putString("steps", "[]").apply()
            refresh()
        })
        root.addView(TextView(this).apply {
            text = "Test build 0.1\n\nThis first test records Android accessibility click events and replays their screen positions. Some games and custom-drawn apps do not expose touch events to Accessibility, so recording may not work in those apps yet."
            textSize = 13f
            setTextColor(0xFF8B96A5.toInt())
            setPadding(4, 22, 4, 0)
        })
        setContentView(root)
        refresh()
    }

    private fun prefs() = getSharedPreferences(MacroAccessibilityService.PREFS, MODE_PRIVATE)

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 9 }
    }

    private fun refresh() {
        val recording = prefs().getBoolean("recording", false)
        status.text = if (recording) "● RECORDING" else "READY"
        val steps = try { JSONArray(prefs().getString("steps", "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        count.text = "Recorded actions: ${steps.length()}"
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refresh()
    }
}
