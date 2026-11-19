package com.yidplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yidplus.app.data.Api
import com.yidplus.app.data.Session
import com.yidplus.app.data.objects
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Admin → Feedback: what members sent in (bug / suggestion / block appeal). */
class AdminFeedbackPage : PageScreen() {
    override val perfName get() = "AdminFeedback"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        val list = remember { mutableStateListOf<JSONObject>() }
        var loaded by remember { mutableStateOf(false) }
        var del by remember { mutableStateOf<JSONObject?>(null) }
        suspend fun load() { runCatching { Api.get("/api/feedback") }.onSuccess { list.clear(); it.optJSONArray("feedback").objects().forEach { x -> list.add(x) } }; loaded = true }
        LaunchedEffect(Unit) { load() }
        SettingsPage("Feedback", sub = "What members sent in") {
            if (loaded && list.isEmpty()) SettingsHint("No feedback yet.")
            if (list.isNotEmpty()) SettingsCard {
                list.forEach { f ->
                    val resolved = f.optInt("resolved") == 1 || f.optBoolean("resolved")
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(f.optString("nickname").ifBlank { "Someone" }, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Text("  " + f.optString("type") + "  ·  " + ago(f.optString("created_at").ifBlank { null }), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(f.optString("text"), Modifier.padding(top = 2.dp), color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
                        }
                        if (!resolved) IconBtn(Sym.Check, "Mark resolved", {
                            scope.launch { runCatching { Api.put("/api/feedback", JSONObject().put("id", f.optString("id")).put("resolved", true)) }; load() }
                        }, tint = MaterialTheme.colorScheme.primary)
                        IconBtn(Sym.Delete, "Delete", { del = f }, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        del?.let { f ->
            SettingsConfirmDialog("Delete this feedback?", f.optString("text"), "Delete", danger = true,
                onOk = { del = null; scope.launch { runCatching { Api.delete("/api/feedback?id=" + f.optString("id")) }; load() } }, onDismiss = { del = null })
        }
    }
}

/** Admin → Badges: give a member a badge, or take one away. */
class AdminBadgesPage : PageScreen() {
    override val perfName get() = "AdminBadges"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        val list = remember { mutableStateListOf<JSONObject>() }
        var userId by remember { mutableStateOf("") }
        var badge by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var del by remember { mutableStateOf<JSONObject?>(null) }
        suspend fun load() { runCatching { Api.get("/api/admin/badges") }.onSuccess { list.clear(); it.optJSONArray("badges").objects().forEach { x -> list.add(x) } } }
        LaunchedEffect(Unit) { load() }
        fun give() {
            if (userId.isBlank() || badge.isBlank()) return
            busy = true
            scope.launch {
                runCatching { Api.post("/api/admin/badges", JSONObject().put("user_id", userId.trim()).put("badge_text", badge.trim())) }
                    .onSuccess { userId = ""; badge = ""; toast(c, "Badge given"); load() }.onFailure { toast(c, Api.friendly(it)) }
                busy = false
            }
        }
        SettingsPage("Badges", sub = "Give a member a badge") {
            SettingsCard("New badge") {
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(userId, { userId = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("User ID") })
                    OutlinedTextField(badge, { badge = it.take(40) }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, label = { Text("Badge text / emoji") })
                    PrimaryButton(if (busy) "Giving…" else "Give badge", { if (!busy) give() }, Modifier.padding(top = 10.dp), enabled = !busy)
                }
            }
            if (list.isNotEmpty()) SettingsCard("Recent") {
                list.forEach { b ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.optString("badge_text"), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                            Text(b.optString("nickname").ifBlank { b.optString("user_id") }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconBtn(Sym.Delete, "Remove", { del = b }, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        del?.let { b ->
            SettingsConfirmDialog("Remove badge?", b.optString("badge_text") + " · " + b.optString("nickname"), "Remove", danger = true,
                onOk = { del = null; scope.launch { runCatching { Api.delete("/api/admin/badges?id=" + b.optString("id")) }; load() } }, onDismiss = { del = null })
        }
    }
}

/** Admin → Analytics: members, activity and the last two weeks' growth. */
class AdminAnalyticsPage : PageScreen() {
    override val perfName get() = "AdminAnalytics"
    @Composable override fun Content() {
        var stats by remember { mutableStateOf<JSONObject?>(null) }
        var insights by remember { mutableStateOf<JSONObject?>(null) }
        LaunchedEffect(Unit) {
            runCatching { Api.get("/api/admin/stats") }.onSuccess { stats = it.optJSONObject("stats") }
            runCatching { Api.get("/api/admin/insights") }.onSuccess { insights = it }
        }
        val s = stats; val now = insights
        SettingsPage("Analytics", sub = "Members, activity and growth") {
            SettingsCard("Totals") {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(now?.optInt("online_now") ?: s?.optInt("online") ?: 0, "Online", Modifier.weight(1f), live = true)
                        Tile(now?.optInt("total_users") ?: s?.optInt("users") ?: 0, "Members", Modifier.weight(1f))
                        Tile(s?.optInt("new_today") ?: 0, "New today", Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(s?.optInt("shorts") ?: 0, "Shorts", Modifier.weight(1f))
                        Tile(s?.optInt("messages") ?: 0, "Messages", Modifier.weight(1f))
                        Tile(s?.optInt("channels") ?: 0, "Channels", Modifier.weight(1f))
                    }
                }
            }
            // Native-app adoption (counts start from when the server change goes live; 0 until then).
            SettingsCard("The app") {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(now?.optInt("app_users") ?: 0, "Have the app", Modifier.weight(1f))
                        Tile(now?.optInt("app_active_7d") ?: 0, "Active (7 days)", Modifier.weight(1f))
                    }
                    Text("How many people have the native app and use it. Counts begin once the server update is live.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val growth = now?.optJSONArray("growth").objects()
            if (growth.isNotEmpty()) SettingsCard("New members (14 days)") {
                Box(Modifier.padding(12.dp)) { GrowthBars(growth) }
            }
        }
    }
}

/** Admin → Ads: turn ads on/off, reorder or remove. New ads with an image are added on the website. */
class AdminAdsPage : PageScreen() {
    override val perfName get() = "AdminAds"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        val list = remember { mutableStateListOf<JSONObject>() }
        var loaded by remember { mutableStateOf(false) }
        var del by remember { mutableStateOf<JSONObject?>(null) }
        suspend fun load() { runCatching { Api.get("/api/admin/ads") }.onSuccess { list.clear(); it.optJSONArray("ads").objects().forEach { x -> list.add(x) } }; loaded = true }
        LaunchedEffect(Unit) { load() }
        SettingsPage("Ads", sub = "Turn ads on or off, or remove them") {
            if (loaded && list.isEmpty()) SettingsHint("No ads yet. Add one with a picture on the website.")
            if (list.isNotEmpty()) SettingsCard {
                list.forEachIndexed { i, a ->
                    if (i > 0) RowDivider(14.dp)
                    val active = a.optInt("active") == 1 || a.optBoolean("active")
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.optString("title").ifBlank { "(untitled)" }, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            if (a.optString("subtitle").isNotBlank()) Text(a.optString("subtitle"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(active, { on ->
                            scope.launch { runCatching { Api.put("/api/admin/ads", JSONObject().put("id", a.optString("id")).put("active", on)) }.onSuccess { load() }.onFailure { toast(c, Api.friendly(it)) } }
                        })
                        IconBtn(Sym.Delete, "Remove", { del = a }, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        del?.let { a ->
            SettingsConfirmDialog("Remove this ad?", a.optString("title"), "Remove", danger = true,
                onOk = { del = null; scope.launch { runCatching { Api.delete("/api/admin/ads?id=" + a.optString("id")) }; load() } }, onDismiss = { del = null })
        }
    }
}

/** Admin → Support: the support inbox, with a native conversation + reply. */
class AdminSupportPage : PageScreen() {
    override val perfName get() = "AdminSupport"
    @Composable override fun Content() {
        val tabs = listOf("new" to "New", "mine" to "Mine", "all" to "All")
        var tab by remember { mutableIntStateOf(0) }
        val list = remember { mutableStateListOf<JSONObject>() }
        var loaded by remember { mutableStateOf(false) }
        suspend fun load() { loaded = false; runCatching { Api.get("/api/support-chats?admin=1&tab=" + tabs[tab].first) }.onSuccess { list.clear(); it.optJSONArray("chats").objects().forEach { x -> list.add(x) } }; loaded = true }
        LaunchedEffect(tab) { load() }
        SettingsPage("Support", sub = "People who asked for help") {
            SoChips(tabs.map { it.second }, tab, { tab = it }, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            if (loaded && list.isEmpty()) SettingsHint("Nothing here.")
            if (list.isNotEmpty()) SettingsCard {
                list.forEachIndexed { i, ch ->
                    if (i > 0) RowDivider(14.dp)
                    Row(Modifier.fillMaxWidth().tap(RoundedCornerShape(0.dp)) { Nav.open(AdminSupportChatPage(ch.optString("id"), ch.optString("user_nick").ifBlank { ch.optString("nickname").ifBlank { "Member" } })) }
                        .padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ch.optString("user_nick").ifBlank { ch.optString("nickname").ifBlank { "Member" } }, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text((ch.optString("subject").ifBlank { ch.optString("status").ifBlank { "open" } }) + "  ·  " + ago(ch.optString("created_at").ifBlank { null }), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Sym.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** One support conversation: the messages, a reply box, Claim and Close. */
data class AdminSupportChatPage(val chatId: String, val who: String) : PageScreen() {
    override val perfName get() = "AdminSupportChat"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        val msgs = remember { mutableStateListOf<JSONObject>() }
        var text by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        suspend fun load() { runCatching { Api.get("/api/support-chats?admin=1&chat_id=" + chatId) }.onSuccess { msgs.clear(); it.optJSONArray("messages").objects().forEach { m -> msgs.add(m) } } }
        LaunchedEffect(Unit) { load() }
        fun send() {
            val t = text.trim(); if (t.isEmpty() || busy) return
            busy = true; text = ""
            scope.launch {
                runCatching { Api.post("/api/support-chats", JSONObject().put("chat_id", chatId).put("text", t).put("admin", true)) }
                    .onSuccess { load() }.onFailure { toast(c, Api.friendly(it)); text = t }
                busy = false
            }
        }
        Column(Modifier.fillMaxSize()) {
            ChBar(who, sub = "Support", onBack = { Nav.back() }) {
                ChIcon(Sym.Check, "Claim", { scope.launch { runCatching { Api.post("/api/support-chats", JSONObject().put("chat_id", chatId).put("claim", true)) }.onSuccess { toast(c, "Claimed") } } })
                ChIcon(Sym.Close, "Close", { scope.launch { runCatching { Api.post("/api/support-chats", JSONObject().put("chat_id", chatId).put("close", true)) }.onSuccess { toast(c, "Closed"); Nav.back() } } })
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                msgs.forEach { m ->
                    val mine = m.optString("sender_type") == "admin"
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Column(Modifier.widthIn(max = 280.dp).background(if (mine) Color(0xFFD9FDD3) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(m.optString("text"), color = Color(0xFF111B21), style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
                            Text(ago(m.optString("created_at").ifBlank { null }), fontSize = 10.sp, color = Color(0xFF667781))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it.take(1000) }, Modifier.weight(1f), label = { Text("Reply") })
                Spacer(Modifier.width(6.dp))
                IconBtn(Sym.Send, "Send", { send() }, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
