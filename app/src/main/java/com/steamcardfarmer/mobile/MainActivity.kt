package com.steamcardfarmer.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Black = Color(0xFF050505)
private val Panel = Color(0xFF151515)
private val Green = Color(0xFF35D477)
private val Muted = Color(0xFFAAAAAA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://steamcommunity.com/openid/"))) } }
    }
}

@Composable
private fun App(onSteamLogin: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    var protectFoils by remember { mutableStateOf(true) }
    var autoSell by remember { mutableStateOf(false) }
    val names = listOf("Home", "Farm", "Games", "Cards", "More")
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
                        item { CardBox("STEAM ACCOUNT", "Not connected", "Sign-in is handed to Steam's official website. Account linking still needs secure server-side OpenID verification.") {
                            Button(onClick = onSteamLogin, modifier = Modifier.fillMaxWidth()) { Text("OPEN STEAM SIGN-IN") }
                            Text("Never enter your Steam password or Steam Guard code into this app.", color = Muted, fontSize = 11.sp)
                        } }
                        item { CardBox("AUTOMATIC FARMING", "Not available yet", "This prototype does not run Steam sessions, discover eligible games or detect card drops.") {} }
                    }
                    1 -> item { CardBox("FARMING QUEUE", "No games queued", "Queue interface is planned. Actual farming engine and verified drop tracking are not implemented.") {} }
                    2 -> item { CardBox("GAME LIBRARY", "No Steam library loaded", "Connect a verified Steam account in a future release to import owned games. No sample games are shown as real data.") {} }
                    3 -> item { CardBox("CARD INVENTORY", "No inventory loaded", "Live inventory, market prices and selling are not connected.") {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Protect foil cards", Modifier.weight(1f), color = Color.White); Switch(protectFoils, { protectFoils = it }) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Automatic selling", Modifier.weight(1f), color = Color.White); Switch(autoSell, { autoSell = it }) }
                    } }
                    else -> item { CardBox("MORE WORKSPACES", "Coming in later builds", "Deal discovery • Playtime tools • Achievement viewer • Chat • Farming history • Background service") {} }
                }
                item { Text("Unofficial prototype • Not affiliated with Valve • v0.2.0", color = Muted, fontSize = 10.sp) }
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
