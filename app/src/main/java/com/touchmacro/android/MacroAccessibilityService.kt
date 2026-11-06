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
    private var playing = false

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
        playSteps(raw)
    }

    fun playSteps(raw: String) {
        if (playing) return
        val steps = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        if (steps.length() == 0) return
        playing = true
        playAt(steps, 0)
    }

    private fun playAt(steps: JSONArray, index: Int) {
        if (index >= steps.length()) { playing = false; return }
        val item = steps.optJSONObject(index)
        if (item == null) { playAt(steps, index + 1); return }
        handler.postDelayed({
            val path = Path().apply { moveTo(item.optDouble("x").toFloat(), item.optDouble("y").toFloat()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, item.optLong("duration",70L).coerceIn(50L,2000L)))
                .build()
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { playAt(steps,index+1) }
                override fun onCancelled(gestureDescription: GestureDescription?) { playAt(steps,index+1) }
            }, handler)
        }, item.optLong("delay",450L).coerceAtLeast(0L))
    }

    override fun onInterrupt() { playing=false; handler.removeCallbacksAndMessages(null) }
    override fun onDestroy() { instance=null; handler.removeCallbacksAndMessages(null); super.onDestroy() }
}
