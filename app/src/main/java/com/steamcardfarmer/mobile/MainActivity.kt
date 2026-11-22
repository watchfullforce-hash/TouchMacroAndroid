package com.steamcardfarmer.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private val Black = Color(0xFF050505)
private val Panel = Color(0xFF151515)
private val Green = Color(0xFF35D477)
private val Muted = Color(0xFFAAAAAA)
private const val RELEASE_API = "https://api.github.com/repos/watchfullforce-hash/TouchMacroAndroid/releases/latest"

private data class SavedSteamAccount(val username: String)
private data class ReleaseInfo(val version: String, val apkUrl: String, val notes: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App(
                loadAccounts = { readAccounts(this) },
                saveAccounts = { writeAccounts(this, it) },
                onSteamLogin = { openSteamLogin() },
                onCheckUpdates = { callback -> checkForUpdate(callback) },
                onDownloadUpdate = { release, progress, complete -> downloadUpdate(release, progress, complete) },
                onInstallUpdate = { installDownloadedUpdate() }
            )
        }
    }

    private var pendingApk: File? = null

    private fun checkForUpdate(callback: (ReleaseInfo?, String?) -> Unit) {
        Thread {
            try {
                val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "SteamCardFarmer-Android")
                }
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val release = JSONObject(json)
                val tag = release.optString("tag_name").removePrefix("v")
                val assets = release.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk", true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
                if (apkUrl.isNullOrBlank()) throw IllegalStateException("The latest release does not contain a signed APK yet.")
                val result = if (compareVersions(tag, BuildConfig.VERSION_NAME) > 0)
                    ReleaseInfo(tag, apkUrl, release.optString("body").take(1200)) else null
                runOnUiThread { callback(result, null) }
            } catch (e: Exception) {
                runOnUiThread { callback(null, e.message ?: "Could not check for updates.") }
            }
        }.start()
    }

    private fun downloadUpdate(
        release: ReleaseInfo,
        onProgress: (Float) -> Unit,
        onComplete: (String?) -> Unit
    ) {
        Thread {
            try {
                val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "SteamCardFarmer-Android")
                }
                val total = connection.contentLengthLong
                val dir = File(cacheDir, "updates").apply { mkdirs() }
                val output = File(dir, "steam-card-farmer-${release.version}.apk")
                connection.inputStream.use { input ->
                    output.outputStream().use { out ->
                        val buffer = ByteArray(16 * 1024)
                        var received = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                            received += count
                            if (total > 0) {
                                val pct = (received.toFloat() / total).coerceIn(0f, 1f)
                                runOnUiThread { onProgress(pct) }
                            }
                        }
                    }
                }
                connection.disconnect()
                if (!output.exists() || output.length() < 100_000L) throw IllegalStateException("Downloaded APK looks incomplete.")
                pendingApk = output
                runOnUiThread { onProgress(1f); onComplete(null) }
            } catch (e: Exception) {
                runOnUiThread { onComplete(e.message ?: "Download failed.") }
            }
        }.start()
    }

    private fun installDownloadedUpdate() {
        val file = pendingApk ?: return
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Unable to open Android installer: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun openSteamLogin() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://steamcommunity.com/login/home/")))
    }
}

private fun compareVersions(a: String, b: String): Int {
    val left = a.split(".").map { it.toIntOrNull() ?: 0 }
    val right = b.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(left.size, right.size)) {
        val x = left.getOrElse(i) { 0 }
        val y = right.getOrElse(i) { 0 }
        if (x != y) return x.compareTo(y)
    }
    return 0
}

private fun readAccounts(context: Context): List<SavedSteamAccount> {
    val raw = context.getSharedPreferences("steam_card_farmer", Context.MODE_PRIVATE).getString("saved_accounts", "[]") ?: "[]"
    return try {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val username = array.optJSONObject(index)?.optString("username")?.trim().orEmpty()
            if (username.isBlank()) null else SavedSteamAccount(username)
        }
    } catch (_: Exception) { emptyList() }
}

private fun writeAccounts(context: Context, accounts: List<SavedSteamAccount>) {
    val array = JSONArray()
    accounts.forEach { array.put(JSONObject().put("username", it.username)) }
    context.getSharedPreferences("steam_card_farmer", Context.MODE_PRIVATE).edit().putString("saved_accounts", array.toString()).apply()
}

@Composable
private fun App(
    loadAccounts: () -> List<SavedSteamAccount>,
    saveAccounts: (List<SavedSteamAccount>) -> Unit,
    onSteamLogin: () -> Unit,
    onCheckUpdates: ((ReleaseInfo?, String?) -> Unit) -> Unit,
    onDownloadUpdate: (ReleaseInfo, (Float) -> Unit, (String?) -> Unit) -> Unit,
    onInstallUpdate: () -> Unit
) {
    var page by remember { mutableStateOf(0) }
    var protectFoils by remember { mutableStateOf(true) }
    var autoSell by remember { mutableStateOf(false) }
    var usernameInput by remember { mutableStateOf("") }
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(true) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    val accounts = remember { mutableStateListOf<SavedSteamAccount>().apply { addAll(loadAccounts()) } }
    val names = listOf("Home", "Farm", "Games", "Cards", "More")
    fun persist() = saveAccounts(accounts.toList())

    LaunchedEffect(Unit) {
        onCheckUpdates { info, error ->
            release = info
            updateError = error
            checking = false
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Green, background = Black, surface = Panel)) {
        Scaffold(containerColor = Black, topBar = {
            Column(Modifier.fillMaxWidth().background(Black).padding(18.dp)) {
                Text("STEAM CARD FARMER", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text("MOBILE EDITION • AMOLED", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }, bottomBar = {
            NavigationBar(containerColor = Panel) {
                names.forEachIndexed { i, name ->
                    val icon = when(i) { 0 -> Icons.Default.Home; 1 -> Icons.Default.Bolt; 2 -> Icons.Default.SportsEsports; 3 -> Icons.Default.Style; else -> Icons.Default.MoreHoriz }
                    NavigationBarItem(selected = page == i, onClick = { page = i }, icon = { Icon(icon, null) }, label = { Text(name) })
                }
            }
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    CardBox("YOUR FARMING COMMAND CENTRE", "Cards. Games. Market.", "A mobile dashboard for managing your Steam card workflow.") {
                        Text("ENGINE STATUS: NOT CONNECTED", color = Color(0xFFFF6262), fontWeight = FontWeight.Bold)
                    }
                }
                if (page == 0) {
                    item {
                        CardBox(
                            title = "APP UPDATER",
                            headline = release?.let { "Version ${it.version} available" } ?: if (checking) "Checking for updates…" else if (updateError != null) "Update check failed" else "You're up to date",
                            body = "Installed: ${BuildConfig.VERSION_NAME}. Checks GitHub when the app opens. Android asks you to approve installation.",
                            content = {
                                if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                                val available = release
                                if (available != null) {
                                    if (available.notes.isNotBlank()) Text(available.notes, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                                    Button(
                                        enabled = !downloading,
                                        onClick = {
                                            downloading = true
                                            downloadProgress = 0f
                                            onDownloadUpdate(available, { pct -> downloadProgress = pct }) { error ->
                                                downloading = false
                                                if (error != null) updateError = error else onInstallUpdate()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(if (downloading) "DOWNLOADING…" else "DOWNLOAD UPDATE") }
                                    if (downloading) LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                                }
                                updateError?.let { error -> Text(error, color = Color(0xFFFFC266), fontSize = 11.sp) }
                                Button(onClick = {
                                    checking = true
                                    updateError = null
                                    release = null
                                    onCheckUpdates { info, error -> release = info; updateError = error; checking = false }
                                }, modifier = Modifier.fillMaxWidth()) { Text("CHECK FOR UPDATES") }
                            }
                        )
                    }
                }
                when(page) {
                    0 -> {
                        item {
                            CardBox("STEAM ACCOUNTS", if (accounts.isEmpty()) "No saved accounts" else "${accounts.size} saved account(s)", "Save Steam login names for quick access. Sign-in stays on Steam's official website.") {
                                OutlinedTextField(value = usernameInput, onValueChange = { usernameInput = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Steam account name") }, placeholder = { Text("Enter Steam username") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
                                Button(onClick = {
                                    val name = usernameInput.trim()
                                    if (name.isNotEmpty() && accounts.none { it.username.equals(name, true) }) { accounts.add(SavedSteamAccount(name)); persist(); usernameInput = "" }
                                }, enabled = usernameInput.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("SAVE ACCOUNT") }
                                accounts.forEach { account ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text(account.username, color = Color.White, modifier = Modifier.weight(1f))
                                        TextButton(onClick = onSteamLogin) { Text("SIGN IN", color = Green) }
                                        IconButton(onClick = { accounts.remove(account); persist() }) { Icon(Icons.Default.DeleteOutline, "Remove", tint = Color(0xFFFF7777)) }
                                    }
                                }
                                Button(onClick = onSteamLogin, modifier = Modifier.fillMaxWidth()) { Text("OPEN STEAM LOGIN / QR") }
                                Text("Usernames are saved locally. Passwords, Steam Guard codes and session cookies are never stored here.", color = Muted, fontSize = 11.sp)
                            }
                        }
                        item { CardBox("AUTOMATIC FARMING", "Not available yet", "This prototype does not run Steam sessions, discover eligible games or detect card drops.") {} }
                    }
                    1 -> item { CardBox("FARMING QUEUE", "No games queued", "Actual farming engine and verified drop tracking are not implemented.") {} }
                    2 -> item { CardBox("GAME LIBRARY", "No Steam library loaded", "Secure Steam account linking is not implemented yet.") {} }
                    3 -> item { CardBox("CARD INVENTORY", "No inventory loaded", "Live inventory, market prices and selling are not connected.") {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Protect foil cards", Modifier.weight(1f), color = Color.White); Switch(protectFoils, { protectFoils = it }) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Automatic selling", Modifier.weight(1f), color = Color.White); Switch(autoSell, { autoSell = it }) }
                    } }
                    else -> item { CardBox("MORE WORKSPACES", "Coming in later builds", "Deal discovery • Playtime tools • Achievement viewer • Chat • Farming history • Background service") {} }
                }
                item { Text("Unofficial prototype • Not affiliated with Valve • v${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun CardBox(title: String, headline: String, body: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(headline, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(body, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        content()
    }
}
