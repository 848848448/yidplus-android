package com.yidplus.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yidplus.app.data.Api
import com.yidplus.app.data.SiteConfig
import com.yidplus.app.data.objects
import com.yidplus.app.data.str
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** The site's Admin → Features switches. Turning one off here hides that tab in the app's bottom bar (and on the site) at once. */
private val featureFlags = listOf(
    Triple("feat_shorts", "Shorts", "The Shorts tab and video feed"),
    Triple("feat_music", "Music", "The Music tab and player"),
    Triple("feat_status", "Status", "Status / stories posting and viewing"),
    Triple("feat_channels", "Channels", "The Channels / explore tab"),
    Triple("feat_guest", "Guest mode", "Let people browse without an account"),
)
private val featureIcons = mapOf("feat_shorts" to Icons.Filled.Movie, "feat_music" to Icons.Filled.MusicNote, "feat_status" to Icons.Filled.Star,
    "feat_channels" to Icons.Filled.Explore, "feat_guest" to Icons.Filled.Person)

class AdminPage : PageScreen() {
    override val perfName = "Admin"
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        var stats by remember { mutableStateOf<JSONObject?>(null) }
        var statsErr by remember { mutableStateOf<String?>(null) }
        var flags by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var q by remember { mutableStateOf("") }
        var users by remember { mutableStateOf<List<JSONObject>?>(null) }
        LaunchedEffect(Unit) {
            if (MyProfile.demo) {
                stats = JSONObject().put("users", 1834).put("new_today", 12).put("online", 47).put("shorts", 3120).put("messages", 98210).put("channels", 64).put("open_reports", 2)
                flags = mapOf("feat_music" to "off"); users = emptyList(); return@LaunchedEffect
            }
            runCatching { Api.get("/api/admin/stats").optJSONObject("stats") }.onSuccess { stats = it }.onFailure { statsErr = Api.friendly(it) }
            runCatching { Api.get("/api/settings").optJSONObject("settings") }.onSuccess { j -> if (j != null) flags = j.keys().asSequence().filter { it.startsWith("feat_") }.associateWith { j.optString(it) } }
        }
        // Search people as you type (the site's Admin → Users search).
        LaunchedEffect(q) {
            if (MyProfile.demo || q.trim().length < 2) { if (q.isBlank()) users = null; return@LaunchedEffect }
            delay(350)
            runCatching { Api.get("/api/admin/users?search=" + Uri.encode(q.trim())).optJSONArray("users").objects() }.onSuccess { users = it.distinctBy { u -> u.str("id") } }
        }
        SettingsPage("Admin", sub = "YID PLUS control center") {
            SettingsCard("Today") {
                val s = stats
                if (s == null) Text(statsErr ?: "Loading…", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(s.optInt("users"), "Members", Modifier.weight(1f)); Tile(s.optInt("online"), "Online now", Modifier.weight(1f)); Tile(s.optInt("new_today"), "New today", Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(s.optInt("shorts"), "Shorts", Modifier.weight(1f)); Tile(s.optInt("channels"), "Channels", Modifier.weight(1f)); Tile(s.optInt("open_reports"), "Open reports", Modifier.weight(1f), alert = s.optInt("open_reports") > 0)
                    }
                }
            }
            SettingsCard("Features") {
                featureFlags.forEachIndexed { i, (key, label, desc) ->
                    if (i > 0) RowDivider(64.dp)
                    val on = flags[key] != "off"
                    SettingRow(featureIcons[key] ?: Icons.Filled.Star, label, desc + if (!meIsSuperAdmin) " · owner only" else "", switch = on,
                        modifier = if (i == 0) Modifier.firstFocus() else Modifier) {
                        if (!meIsSuperAdmin) { toast(c, "Only the owner or a super admin can change this"); return@SettingRow }
                        val v = if (on) "off" else "on"
                        flags = flags + (key to v)
                        scope.launch {
                            runCatching { if (!MyProfile.demo) Api.put("/api/settings", JSONObject().put("key", key).put("value", v)) }
                                .onSuccess { toast(c, if (v == "on") "$label is on" else "$label is off for everyone"); SiteConfig.refresh() }
                                .onFailure { flags = flags + (key to if (on) "on" else "off"); toast(c, Api.friendly(it)) }
                        }
                    }
                }
            }
            SettingsHint("Off = the tab disappears from the bottom bar for everyone, on the site and in the app. People on a feature's allow-list (set in the full panel) still see it.")
            SettingsCard("Find a member") {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true, label = { Text("Name or email") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
                users?.take(20)?.forEach { u -> AdminUserRow(u) { changed -> users = users?.map { if (it.str("id") == changed.str("id")) changed else it } } }
                if (users?.isEmpty() == true && q.length >= 2) Text("Nobody found", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsCard("Everything else") {
                SettingRow(Icons.AutoMirrored.Filled.OpenInNew, "Open the full admin panel", "Reports, ads, channels, Telegram, bots, broadcasts, logs and more (your admin PIN is asked)") {
                    Nav.open(Screen.Web(Api.BASE + "/admin"))
                }
            }
        }
    }
}

@Composable
private fun Tile(n: Int, label: String, modifier: Modifier, alert: Boolean = false) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(if (alert) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer).padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (n >= 10000) "${n / 1000}k" else n.toString(), style = MaterialTheme.typography.titleLarge,
            color = if (alert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (alert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AdminUserRow(u: JSONObject, onChanged: (JSONObject) -> Unit) {
    val c = ctx()
    val scope = rememberCoroutineScope()
    val blocked = u.optInt("blocked") == 1
    val role = when (u.str("role")) { "admin_super" -> "Super admin"; "admin_limited" -> "Admin"; else -> null }
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(null, u.str("nickname"), 40.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(u.str("nickname"), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(role, if (u.optInt("online") == 1) "online" else null, if (blocked) "blocked" else null, u.str("email").ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Surface(color = androidx.compose.ui.graphics.Color.Transparent) {
            Text(if (blocked) "Unblock" else "Block", Modifier.tap(RoundedCornerShape(16.dp)) {
                scope.launch {
                    runCatching { Api.put("/api/admin/users", JSONObject().put("id", u.str("id")).put("blocked", !blocked)) }
                        .onSuccess { toast(c, if (blocked) "Unblocked" else "Blocked"); onChanged(JSONObject(u.toString()).put("blocked", if (blocked) 0 else 1)) }
                        .onFailure { toast(c, Api.friendly(it)) }
                }
            }.padding(horizontal = 12.dp, vertical = 8.dp), color = if (blocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
        }
    }
}
