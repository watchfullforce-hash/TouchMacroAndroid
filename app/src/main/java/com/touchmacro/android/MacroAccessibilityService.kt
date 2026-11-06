package com.touchmacro.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import org.json.JSONArray
import org.json.JSONObject

class MacroAccessibilityService : AccessibilityService() {
    companion object {
        const val PREFS = "touchmacro"
        @Volatile var instance: MacroAccessibilityService? = null
    }
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var playing = false
    private var currentRepeat = 0
    private var repeatLimit = 1
    private var speedFactor = 1f
    private lateinit var playStepsData: JSONArray

    override fun onServiceConnected() { super.onServiceConnected(); instance = this }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || playing) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean("recording", false)) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) return
        val node = event.source ?: return
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        node.recycle()
        if (bounds.isEmpty) return
        val steps = try { JSONArray(prefs.getString("steps", "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        steps.put(JSONObject().apply {
            put("x", bounds.centerX()); put("y", bounds.centerY())
            put("delay", 450); put("duration", if (event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) 650 else 70)
        })
        prefs.edit().putString("steps", steps.toString()).apply()
    }

    fun play() {
        val raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString("steps", "[]") ?: "[]"
        playSteps(raw, 1, 100)
    }

    fun playSteps(raw: String, repeats: Int = 1, speedPercent: Int = 100) {
        if (playing) return
        val parsed = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        if (parsed.length() == 0) return
        playStepsData = parsed
        repeatLimit = repeats.coerceIn(0, 1000)
        currentRepeat = 0
        speedFactor = speedPercent.coerceIn(25, 400) / 100f
        playing = true
        playAt(0)
    }

    fun stopPlayback() {
        playing = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun playAt(index: Int) {
        if (!playing) return
        if (index >= playStepsData.length()) {
            currentRepeat++
            if (repeatLimit == 0 || currentRepeat < repeatLimit) playAt(0)
            else playing = false
            return
        }
        val item = playStepsData.optJSONObject(index)
        if (item == null) { playAt(index + 1); return }
        val delay = (item.optLong("delay", 450L) / speedFactor).toLong().coerceAtLeast(0L)
        handler.postDelayed({
            if (!playing) return@postDelayed
            val path = Path().apply { moveTo(item.optDouble("x").toFloat(), item.optDouble("y").toFloat()) }
            val duration = (item.optLong("duration", 70L) / speedFactor).toLong().coerceIn(50L, 2000L)
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { if (playing) playAt(index + 1) }
                override fun onCancelled(gestureDescription: GestureDescription?) { if (playing) playAt(index + 1) }
            }, handler)
        }, delay)
    }

    override fun onInterrupt() { stopPlayback(); handler.removeCallbacksAndMessages(null) }
    override fun onDestroy() { stopPlayback(); instance=null; super.onDestroy() }
}
