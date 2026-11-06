package com.touchmacro.android

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private val bg = Color.rgb(16, 19, 24)
    private val panel = Color.rgb(29, 34, 42)
    private val green = Color.rgb(80, 205, 135)
    private val muted = Color.rgb(170, 181, 196)
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
        if (intent.getBooleanExtra(EXTRA_SAVE_PENDING, false)) promptSaveMacro()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.getBooleanExtra(EXTRA_SAVE_PENDING, false) == true) promptSaveMacro()
    }

    private fun base(title: String): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 20)
            setBackgroundColor(bg)
        }
        root.addView(label(title, 26f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 20)
        })
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nav.addView(navButton("HOME") { showHome() }, weightParams())
        nav.addView(navButton("MACROS") { showMacros() }, weightParams())
        nav.addView(navButton("SETTINGS") { showSettings() }, weightParams())
        root.addView(nav)
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        return content
    }

    private fun showHome() {
        val page = base("TOUCHMACRO")
        page.addView(label("Your touch macro workspace", 15f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 8)
        })
        val recording = prefs().getBoolean("recording", false)
        page.addView(label(if (recording) "● RECORDING" else "READY", 20f, if (recording) green else Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 8)
        })
        page.addView(label("Saved macros: ${macros().length()}", 14f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        })
        page.addView(actionButton(if (recording) "STOP RECORDING" else "START RECORDING") {
            if (recording) stopAndSave() else beginRecording()
        })
        page.addView(actionButton("OPEN SAVED MACROS") { showMacros() })
        page.addView(actionButton("SETTINGS & PERMISSIONS") { showSettings() })
        page.addView(label("Recording note: this test build records accessibility click events. Some games draw their own controls and do not expose those events to Android.", 13f, muted).apply {
            setPadding(4, 24, 4, 0)
        })
    }

    private fun showSettings() {
        val page = base("SETTINGS")
        page.addView(section("Permissions"))
        page.addView(label("Accessibility service: " + if (isAccessibilityEnabled()) "Enabled" else "Not enabled", 15f, muted))
        page.addView(actionButton("OPEN ACCESSIBILITY SETTINGS") {
            showRestrictedPermissionHelp()
        })
        page.addView(label("Samsung may block accessibility for newly installed apps. Open this app's App info, tap the three-dot menu, choose Allow restricted settings, then return here and enable TouchMacro under Installed apps.", 13f, muted).apply {
            setPadding(4, 8, 4, 16)
        })
        page.addView(label("Floating bubble: " + if (Settings.canDrawOverlays(this)) "Allowed" else "Permission needed", 15f, muted))
        page.addView(actionButton("ALLOW DISPLAY OVER OTHER APPS") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })
        page.addView(section("Recording"))
        page.addView(label("The floating control lets you return to the app underneath while recording. Tap STOP on the bubble to finish and name the macro. Macros stay saved on this phone.", 13f, muted))
        page.addView(actionButton("BACK TO HOME") { showHome() })
    }

    private fun showMacros() {
        val page = base("SAVED MACROS")
        val list = macros()
        if (list.length() == 0) {
            page.addView(label("No macros saved yet. Start a recording and tap Stop to save it.", 15f, muted).apply {
                gravity = Gravity.CENTER
                setPadding(0, 40, 0, 20)
            })
        }
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14, 12, 14, 12)
                setBackgroundColor(panel)
            }
            card.addView(label(item.optString("name", "Untitled macro"), 18f, Color.WHITE, true))
            card.addView(label("Actions: ${item.optJSONArray("steps")?.length() ?: 0}", 13f, muted))
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(smallButton("PLAY") { MacroAccessibilityService.instance?.playSteps(item.optJSONArray("steps")?.toString() ?: "[]") }, weightParams())
            buttons.addView(smallButton("RENAME") { renameMacro(id) }, weightParams())
            buttons.addView(smallButton("DELETE") { deleteMacro(id) }, weightParams())
            card.addView(buttons)
            val params = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 }
            page.addView(card, params)
        }
    }

    private fun beginRecording() {
        if (!isAccessibilityEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Accessibility permission needed")
                .setMessage("Samsung has blocked this permission for this newly installed app. First allow restricted settings for TouchMacro, then enable its accessibility service.")
                .setPositiveButton("OPEN APP INFO") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
                .setNegativeButton("CANCEL", null).show()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Floating bubble permission")
                .setMessage("Allow TouchMacro to display over other apps so its Stop button can float above the app you want to record.")
                .setPositiveButton("ALLOW") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
                .setNegativeButton("CANCEL", null).show()
            return
        }
        prefs().edit().putString("steps", "[]").putBoolean("recording", true).apply()
        val bubbleIntent = Intent(this, OverlayBubbleService::class.java).setAction(OverlayBubbleService.ACTION_SHOW)
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(bubbleIntent) else startService(bubbleIntent)
        finish()
    }

    private fun stopAndSave() {
        prefs().edit().putBoolean("recording", false).apply()
        stopService(Intent(this, OverlayBubbleService::class.java))
        promptSaveMacro()
    }

    private fun promptSaveMacro() {
        intent.removeExtra(EXTRA_SAVE_PENDING)
        val steps = prefs().getString("steps", "[]") ?: "[]"
        val count = try { JSONArray(steps).length() } catch (_: Exception) { 0 }
        if (count == 0) {
            AlertDialog.Builder(this).setTitle("No actions captured")
                .setMessage("No accessibility click events were captured. The target app may not expose its touch controls to Android.")
                .setPositiveButton("OK", null).show()
            return
        }
        val input = EditText(this).apply {
            hint = "Macro name"
            setSingleLine(true)
        }
        AlertDialog.Builder(this).setTitle("Save macro")
            .setMessage("$count actions captured. Give this macro a name.")
            .setView(input)
            .setPositiveButton("SAVE") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "Macro ${macros().length() + 1}" }
                val saved = macros()
                saved.put(JSONObject().apply {
                    put("id", System.currentTimeMillis().toString())
                    put("name", name)
                    put("steps", JSONArray(steps))
                    put("created", System.currentTimeMillis())
                })
                prefs().edit().putString("macros", saved.toString()).apply()
                showMacros()
            }
            .setNegativeButton("DISCARD") { _, _ -> showHome() }
            .show()
    }

    private fun renameMacro(id: String) {
        val arr = macros()
        val item = findMacro(arr, id) ?: return
        val input = EditText(this).apply { setText(item.optString("name")); setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("Rename macro").setView(input)
            .setPositiveButton("SAVE") { _, _ ->
                item.put("name", input.text.toString().trim().ifBlank { "Untitled macro" })
                prefs().edit().putString("macros", arr.toString()).apply()
                showMacros()
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun deleteMacro(id: String) {
        val arr = macros()
        val next = JSONArray()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            if (item.optString("id") != id) next.put(item)
        }
        prefs().edit().putString("macros", next.toString()).apply()
        showMacros()
    }

    private fun findMacro(arr: JSONArray, id: String): JSONObject? {
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i)
            if (item?.optString("id") == id) return item
        }
        return null
    }

    private fun macros(): JSONArray = try {
        JSONArray(prefs().getString("macros", "[]") ?: "[]")
    } catch (_: Exception) { JSONArray() }

    private fun prefs() = getSharedPreferences(MacroAccessibilityService.PREFS, Context.MODE_PRIVATE)

    private fun showRestrictedPermissionHelp() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(this, MacroAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun section(text: String) = label(text, 17f, green, true).apply { setPadding(0, 22, 0, 10) }
    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
    }
    private fun actionButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10; topMargin = 4 }
    }
    private fun smallButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 11f
        isAllCaps = false
        setOnClickListener { action() }
    }
    private fun navButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 11f
        isAllCaps = false
        setOnClickListener { action() }
    }
    private fun weightParams() = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(2, 2, 2, 8) }

    companion object { const val EXTRA_SAVE_PENDING = "save_pending" }
}
