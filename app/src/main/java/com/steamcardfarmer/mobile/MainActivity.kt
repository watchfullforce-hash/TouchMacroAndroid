package com.steamcardfarmer.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

private val Black = Color(0xFF050505)
private val Panel = Color(0xFF151515)
private val Green = Color(0xFF35D477)
private val Muted = Color(0xFFAAAAAA)

private data class SavedSteamAccount(val username: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App(
                loadAccounts = { readAccounts(this) },
                saveAccounts = { writeAccounts(this, it) },
                onSteamLogin = { openSteamLogin() }
            )
        }
    }

    private fun openSteamLogin() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://steamcommunity.com/login/home/"))
        startActivity(intent)
    }
}

private fun readAccounts(context: Context): List<SavedSteamAccount> {
    val raw = context.getSharedPreferences("steam_card_farmer", Context.MODE_PRIVATE)
        .getString("saved_accounts", "[]") ?: "[]"
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
    accounts.forEach { account ->
        array.put(JSONObject().put("username", account.username))
    }
    context.getSharedPreferences("steam_card_farmer", Context.MODE_PRIVATE)
        .edit().putString("saved_accounts", array.toString()).apply()
}

@Composable
private fun App(
    loadAccounts: () -> List<SavedSteamAccount>,
    saveAccounts: (List<SavedSteamAccount>) -> Unit,
    onSteamLogin: () -> Unit
) {
    var page by remember { mutableStateOf(0) }
    var protectFoils by remember { mutableStateOf(true) }
    var autoSell by remember { mutableStateOf(false) }
    var usernameInput by remember { mutableStateOf("") }
    val accounts = remember { mutableStateListOf<SavedSteamAccount>().apply { addAll(loadAccounts()) } }
    val names = listOf("Home", "Farm", "Games", "Cards", "More")

    fun persist() = saveAccounts(accounts.toList())

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
                when(page) {
                    0 -> {
                        item {
                            CardBox("STEAM ACCOUNTS", if (accounts.isEmpty()) "No saved accounts" else "${accounts.size} saved account(s)", "Save Steam login names for quick access. Sign-in always happens on Steam's official website.") {
                                OutlinedTextField(
                                    value = usernameInput,
                                    onValueChange = { usernameInput = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("Steam account name") },
                                    placeholder = { Text("Enter Steam username") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Green,
                                        unfocusedBorderColor = Muted,
                                        focusedLabelColor = Green,
                                        unfocusedLabelColor = Muted,
                                        cursorColor = Green
                                    )
                                )
                                Button(
                                    onClick = {
                                        val name = usernameInput.trim()
                                        if (name.isNotEmpty() && accounts.none { it.username.equals(name, ignoreCase = true) }) {
                                            accounts.add(SavedSteamAccount(name))
                                            persist()
                                            usernameInput = ""
                                        }
                                    },
                                    enabled = usernameInput.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("SAVE ACCOUNT") }
                                if (accounts.isNotEmpty()) {
                                    HorizontalDivider(color = Color.DarkGray)
                                    accounts.forEach { account ->
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                            Column(Modifier.weight(1f)) {
                                                Text(account.username, color = Color.White, fontWeight = FontWeight.SemiBold)
                                                Text("Saved on this device", color = Muted, fontSize = 10.sp)
                                            }
                                            TextButton(onClick = onSteamLogin) { Text("SIGN IN", color = Green) }
                                            IconButton(onClick = { accounts.remove(account); persist() }) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove account", tint = Color(0xFFFF7777))
                                            }
                                        }
                                    }
                                }
                                Button(onClick = onSteamLogin, modifier = Modifier.fillMaxWidth()) {
                                    Text("OPEN STEAM LOGIN / QR")
                                }
                                Text("Your username is saved locally. Passwords and Steam Guard codes are never stored by this app. Enter them only on Steam's official sign-in page.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                                Text("Account linking is not complete yet: secure Steam OpenID return verification is still required before the app can load your library.", color = Color(0xFFFFC266), fontSize = 11.sp, lineHeight = 16.sp)
                            }
                        }
                        item { CardBox("AUTOMATIC FARMING", "Not available yet", "This prototype does not run Steam sessions, discover eligible games or detect card drops.") {} }
                    }
                    1 -> item { CardBox("FARMING QUEUE", "No games queued", "Queue interface is planned. Actual farming engine and verified drop tracking are not implemented.") {} }
                    2 -> item { CardBox("GAME LIBRARY", "No Steam library loaded", "A verified Steam account connection is required to import owned games. No sample games are shown as real data.") {} }
                    3 -> item { CardBox("CARD INVENTORY", "No inventory loaded", "Live inventory, market prices and selling are not connected.") {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Protect foil cards", Modifier.weight(1f), color = Color.White); Switch(protectFoils, { protectFoils = it }) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Automatic selling", Modifier.weight(1f), color = Color.White); Switch(autoSell, { autoSell = it }) }
                    } }
                    else -> item { CardBox("MORE WORKSPACES", "Coming in later builds", "Deal discovery • Playtime tools • Achievement viewer • Chat • Farming history • Background service") {} }
                }
                item { Text("Unofficial prototype • Not affiliated with Valve • v0.3.0", color = Muted, fontSize = 10.sp) }
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
