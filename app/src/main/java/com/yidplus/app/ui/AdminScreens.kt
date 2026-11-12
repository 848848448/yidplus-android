package com.yidplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yidplus.app.data.Api
import com.yidplus.app.data.Res
import com.yidplus.app.data.Session
import com.yidplus.app.data.SiteConfig
import com.yidplus.app.data.objects
import com.yidplus.app.data.str
import com.yidplus.app.data.strOrNull
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** The site's Admin → Features switches (admin.js FEATURE_FLAGS). Off hides that tab in the bottom bar for everyone, on the site and in the app. */
private val featureFlags = listOf(
    Triple("feat_shorts", "Shorts", "The Shorts tab and video feed"),
    Triple("feat_music", "Music", "The Music tab and player"),
    Triple("feat_status", "Status", "Status / stories posting and viewing"),
    Triple("feat_channels", "Channels", "The Channels / explore tab"),
    Triple("feat_guest", "Guest mode", "Let people browse without an account"),
)
private val featureIcons = mapOf("feat_shorts" to Icons.Filled.Movie, "feat_music" to Icons.Filled.MusicNote, "feat_status" to Icons.Filled.Star,
    "feat_channels" to Icons.Filled.Explore, "feat_guest" to Icons.Filled.Person)

/** The website-only sections (admin.js ADMIN_NAV ids), opened on the site's page at that section. */
private val webSections = listOf(
    Triple("analytics", "Analytics", Icons.Filled.Insights), Triple("announcements", "Announcements", Icons.Filled.Campaign),
    Triple("support-chats", "Support chats", Icons.Filled.SupportAgent), Triple("feedback", "Feedback", Icons.Filled.Feedback),
    Triple("ads", "Ads", Icons.Filled.Sell), Triple("invite-codes", "Invite codes", Icons.Filled.ConfirmationNumber),
    Triple("badges", "Badges", Icons.Filled.MilitaryTech), Triple("maintenance", "Maintenance", Icons.Filled.Build),
    Triple("audit-logs", "Audit logs", Icons.Filled.History),
)

class AdminPage : PageScreen() {
    override val perfName = "Admin"
    override fun equals(other: Any?) = other is AdminPage
    override fun hashCode() = 300
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        val statsRes = remember { Res("/api/admin/stats") { it } }
        val nowRes = remember { Res("/api/admin/insights") { it } }
        var flags by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var allowFor by remember { mutableStateOf<String?>(null) }
        // null = still reading, false = the site didn't answer: the switches then stay locked, so nothing is saved from a wrong picture.
        var flagsOk by remember { mutableStateOf<Boolean?>(null) }
        var flagsTry by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            if (SettingsProfile.demo) {
                statsRes.data = JSONObject().put("stats", JSONObject().put("users", 1834).put("new_today", 12).put("online", 47).put("shorts", 3120).put("messages", 98210).put("channels", 64).put("open_reports", 2))
                flags = mapOf("feat_music" to "off"); flagsOk = true; return@LaunchedEffect
            }
            launch { statsRes.load(force = true) }
            launch { nowRes.load(force = true) }
        }
        LaunchedEffect(flagsTry) {
            if (SettingsProfile.demo) return@LaunchedEffect
            runCatching { Api.get("/api/settings").optJSONObject("settings") ?: error("no settings") }
                .onSuccess { j -> flags = j.keys().asSequence().filter { it.startsWith("feat_") }.associateWith { j.optString(it) }; flagsOk = true }
                .onFailure { flagsOk = false }
        }
        val s = statsRes.data?.optJSONObject("stats")
        val now = nowRes.data
        val owner = adminIsOwner
        val role = when { Session.me?.isOwner == true || Session.me?.role == "owner" -> "👑 Owner"; Session.me?.role == "admin_super" -> "🛡 Super admin"; SettingsProfile.demo -> "👑 Owner"; else -> "🔒 Moderator" }
        SettingsPage("Admin", sub = "$role · YID PLUS control center") {
            // ── 2.1: search pill on top (WhatsApp), then the site's 5 categories, every panel native (AdHome.kt) ──
            AdHomeSections(s)
            // ── Right now (admin.js _loadNow: GET /api/admin/insights) ──
            SettingsCard("At a glance · right now") {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(now?.optInt("online_now") ?: s?.optInt("online") ?: 0, "Online", Modifier.weight(1f), live = true)
                        Tile(now?.optInt("active_sessions") ?: 0, "Sessions", Modifier.weight(1f))
                        Tile(now?.optInt("total_users") ?: s?.optInt("users") ?: 0, "Members", Modifier.weight(1f))
                    }
                    if (s != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(s.optInt("new_today"), "New today", Modifier.weight(1f)); Tile(s.optInt("shorts"), "Shorts", Modifier.weight(1f))
                        Tile(s.optInt("messages"), "Messages", Modifier.weight(1f))
                    }
                    val online = now?.optJSONArray("online_users").objects()
                    if (online.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                        items(online.take(30), key = { it.str("id") }) { u ->
                            Column(Modifier.width(56.dp).tap(RoundedCornerShape(10.dp)) { Nav.open(AdminUserPage(u.str("id"), u.toString())) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Avatar(Api.abs(u.strOrNull("photo_url")), u.str("nickname").ifBlank { "?" }, 44.dp)
                                Text(u.str("nickname"), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    val growth = now?.optJSONArray("growth").objects()
                    if (growth.isNotEmpty()) GrowthBars(growth)
                }
            }
            // ── Features (admin.js buildFeaturesPanel / toggleFeature: PUT /api/settings {key, value:'on'|'off'}) ──
            SettingsCard("Features") {
                featureFlags.forEachIndexed { i, (key, label, desc) ->
                    if (i > 0) RowDivider(64.dp)
                    val on = flags[key] != "off"
                    SettingRow(featureIcons[key] ?: Icons.Filled.Star, label, desc + if (!owner) " · owner only" else "", switch = on) {
                        if (!owner) { toast(c, "Only the owner can change this"); return@SettingRow }
                        if (flagsOk != true) { toast(c, "Couldn't read the current switches from yidplus.com yet. Trying again…"); flagsTry++; return@SettingRow }
                        val v = if (on) "off" else "on"
                        flags = flags + (key to v)
                        scope.launch {
                            runCatching { if (!SettingsProfile.demo) Api.put("/api/settings", JSONObject().put("key", key).put("value", v)) }
                                .onSuccess { toast(c, if (v == "on") "✅ Enabled" else "🚫 Disabled"); SiteConfig.refresh() }
                                .onFailure { flags = flags + (key to if (on) "on" else "off"); toast(c, Api.friendly(it)) }
                        }
                    }
                    if (!on && owner) Text("Who can still see it: " + (flags[key + "_allow"]?.ifBlank { null } ?: "nobody") + " · change",
                        Modifier.fillMaxWidth().tap(RoundedCornerShape(0.dp)) { allowFor = key }.padding(start = 64.dp, end = 16.dp, bottom = 10.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (flagsOk == false) SettingsHint("⚠ yidplus.com didn't send the current switches, so they're locked to avoid saving the wrong value. Tap a switch to try again.")
            SettingsHint("Off = the tab disappears from the bottom bar for everyone, on the site and in the app.")
        }
        allowFor?.let { key ->
            var v by remember(key) { mutableStateOf(flags[key + "_allow"] ?: "") }
            AlertDialog(onDismissRequest = { allowFor = null }, title = { Text("Who can still see it") },
                text = { OutlinedTextField(v, { v = it }, Modifier.fillMaxWidth(), label = { Text("Nicknames or emails, comma-separated") }) },
                confirmButton = { TextButton({
                    val seen = HashSet<String>()
                    val list = v.split(',').map { it.trim() }.filter { it.isNotEmpty() && seen.add(it.lowercase()) }
                    val value = list.joinToString(", ")
                    allowFor = null
                    scope.launch {
                        runCatching { if (!SettingsProfile.demo) Api.put("/api/settings", JSONObject().put("key", key + "_allow").put("value", value)) }
                            .onSuccess { flags = flags + (key + "_allow" to value); toast(c, if (list.isNotEmpty()) "👁 ${list.size} can still see it" else "Nobody can see it while off") }
                            .onFailure { toast(c, Api.friendly(it)) }
                    }
                }) { Text("Save") } },
                dismissButton = { TextButton({ allowFor = null }) { Text("Cancel") } })
        }
    }
}

@Composable
private fun Tile(n: Int, label: String, modifier: Modifier, alert: Boolean = false, live: Boolean = false) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(if (alert) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer).padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(adminN(n), style = MaterialTheme.typography.titleLarge,
            color = if (live) BadgeGreen else if (alert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (alert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** New members per day (insights.growth: {day:'YYYY-MM-DD', count}), as small bars like the site's. */
@Composable
private fun GrowthBars(g: List<JSONObject>) {
    val max = (g.maxOfOrNull { it.optInt("count") } ?: 1).coerceAtLeast(1)
    Column {
        Text("New members per day", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        Row(Modifier.fillMaxWidth().height(86.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            g.takeLast(14).forEach { d ->
                val n = d.optInt("count")
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (n > 0) Text(n.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Box(Modifier.fillMaxWidth(0.75f).height((4 + 50f * n / max).dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF2B8A73), Color(0xFF14503F)))))
                    Text(d.str("day").takeLast(2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
