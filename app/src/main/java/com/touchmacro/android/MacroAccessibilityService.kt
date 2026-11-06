package com.touchmacro.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.InputDevice
import android.view.MotionEvent
import android.accessibilityservice.AccessibilityServiceInfo
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
    private var gesturePath: JSONArray? = null
    private var gestureStart = 0L
    private var lastPointAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        updateExperimentalCapture(getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("experimental_gesture", false))
    }

    fun updateExperimentalCapture(enabled: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < 34) return
        val info = serviceInfo ?: return
        info.setMotionEventSources(if (enabled) InputDevice.SOURCE_TOUCHSCREEN else 0)
        setServiceInfo(info)
    }

    @android.annotation.TargetApi(34)
    override fun onMotionEvent(event: MotionEvent) {
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("recording", false)) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gesturePath = JSONArray()
                gestureStart = event.eventTime
                lastPointAt = 0L
                addGesturePoint(event.x, event.y, 0L)
            }
            MotionEvent.ACTION_MOVE -> {
                // Sample at roughly 16 ms to keep paths smooth without creating huge macros.
                if (event.eventTime - lastPointAt >= 16L) addGesturePoint(event.x, event.y, event.eventTime - gestureStart)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val path = gesturePath ?: return
                addGesturePoint(event.x, event.y, event.eventTime - gestureStart)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val steps = try { JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString("steps", "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
                    steps.put(JSONObject().apply {
                        put("points", path)
                        put("x", path.optJSONObject(0)?.optDouble("x") ?: event.x.toDouble())
                        put("y", path.optJSONObject(0)?.optDouble("y") ?: event.y.toDouble())
                        put("delay", 250)
                        put("duration", (event.eventTime - gestureStart).coerceIn(50L, 5000L))
                    })
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("steps", steps.toString()).apply()
                }
                gesturePath = null
            }
        }
    }

    private fun addGesturePoint(x: Float, y: Float, elapsed: Long) {
        val path = gesturePath ?: return
        val point = JSONObject().put("x", x.toDouble()).put("y", y.toDouble()).put("t", elapsed)
        path.put(point)
        lastPointAt = elapsed + gestureStart
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || playing) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean("recording", false) || prefs.getBoolean("experimental_gesture", false)) return
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
            val points = item.optJSONArray("points")
            val path = Path()
            if (points != null && points.length() > 0) {
                val first = points.optJSONObject(0)!!
                path.moveTo(first.optDouble("x").toFloat(), first.optDouble("y").toFloat())
                for (p in 1 until points.length()) {
                    val point = points.optJSONObject(p) ?: continue
                    path.lineTo(point.optDouble("x").toFloat(), point.optDouble("y").toFloat())
                }
            } else {
                path.moveTo(item.optDouble("x").toFloat(), item.optDouble("y").toFloat())
            }
            val duration = (item.optLong("duration", 70L) / speedFactor).toLong().coerceIn(50L, 5000L)
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
    override fun onDestroy() { stopPlayback(); updateExperimentalCapture(false); instance=null; super.onDestroy() }
}
