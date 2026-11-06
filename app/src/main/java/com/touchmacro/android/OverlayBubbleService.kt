package com.touchmacro.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class OverlayBubbleService : Service() {
    companion object {
        const val ACTION_SHOW = "com.touchmacro.android.SHOW_BUBBLE"
        const val ACTION_PLAY = "com.touchmacro.android.PLAY_BUBBLE"
        private const val CHANNEL = "touchmacro_recording"
        private const val NOTIFICATION_ID = 731
    }
    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var playbackMode = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        playbackMode = getSharedPreferences(MacroAccessibilityService.PREFS, MODE_PRIVATE).getBoolean("playing", false)
        val title = if (playbackMode) "TouchMacro playing macro" else "TouchMacro recording"
        val note = if (playbackMode) "Tap the floating STOP bubble to stop playback." else "Tap STOP to finish and save."
        val notification = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, CHANNEL).setContentTitle(title).setContentText(note)
                .setSmallIcon(android.R.drawable.ic_media_pause).setOngoing(true).build()
        else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setContentTitle(title).setContentText(note)
                .setSmallIcon(android.R.drawable.ic_media_pause).setOngoing(true).build()
        }
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID, notification)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    private fun showBubble() {
        val view = TextView(this).apply {
            text = if (playbackMode) "■\nSTOP" else "●\nSTOP"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFB83232.toInt())
            setPadding(14, 12, 14, 12)
            elevation = 12f
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 16; y = 220 }
        view.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f; var downY = 0f; var originX = 0; var originY = 0; var dragged = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX=e.rawX; downY=e.rawY; originX=lp.x; originY=lp.y; dragged=false; return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx=(e.rawX-downX).toInt(); val dy=(e.rawY-downY).toInt()
                        if (kotlin.math.abs(dx)+kotlin.math.abs(dy)>12) dragged=true
                        lp.x=(originX-dx).coerceAtLeast(0); lp.y=(originY+dy).coerceAtLeast(0)
                        try { wm.updateViewLayout(view,lp) } catch (_:Exception) {}
                        return true
                    }
                    MotionEvent.ACTION_UP -> { if (!dragged) stopAndReturn(); return true }
                }
                return true
            }
        })
        try { wm.addView(view,lp); bubble=view } catch (_:Exception) { stopSelf() }
    }

    private fun stopAndReturn() {
        val prefs = getSharedPreferences(MacroAccessibilityService.PREFS, MODE_PRIVATE)
        val wasRecording = prefs.getBoolean("recording", false)
        prefs.edit().putBoolean("recording",false).putBoolean("playing",false).apply()
        if (!wasRecording) MacroAccessibilityService.instance?.stopPlayback()
        removeBubble()
        val open=Intent(this,MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (wasRecording) putExtra(MainActivity.EXTRA_SAVE_PENDING,true)
        }
        val pi=PendingIntent.getActivity(this,77,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try { pi.send() } catch (_:Exception) {}
        stopSelf()
    }

    private fun removeBubble() {
        bubble?.let { try { wm.removeView(it) } catch (_:Exception) {} }
        bubble=null
    }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT>=26) {
            val channel=NotificationChannel(CHANNEL,"Macro recording and playback",NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    override fun onDestroy() { removeBubble(); super.onDestroy() }
    override fun onBind(intent:Intent?):IBinder?=null
}
