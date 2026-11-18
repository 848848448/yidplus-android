package com.yidplus.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yidplus.app.data.Api
import com.yidplus.app.data.SiteConfig
import com.yidplus.app.data.objects
import com.yidplus.app.data.Session
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Admin → Maintenance: put the site into maintenance mode with a message, natively. */
class AdminMaintenancePage : PageScreen() {
    override val perfName get() = "AdminMaintenance"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var on by remember { mutableStateOf(false) }
        var msg by remember { mutableStateOf("") }
        var loaded by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            runCatching { Api.get("/api/admin/maintenance") }.onSuccess { on = it.optBoolean("maintenance_mode"); msg = it.optString("message"); loaded = true }
        }
        fun save(enabled: Boolean, message: String) {
            busy = true
            scope.launch {
                runCatching { Api.post("/api/admin/maintenance", JSONObject().put("enabled", enabled).put("message", message)) }
                    .onSuccess { toast(c, "Saved"); runCatching { SiteConfig.refresh() } }
                    .onFailure { toast(c, Api.friendly(it)) }
                busy = false
            }
        }
        SettingsPage("Maintenance", sub = "A notice for everyone while the site is down") {
            SettingsCard {
                SettingRow(Sym.Build, "Maintenance mode", if (on) "On · members see the notice" else "Off", switch = on) {
                    if (!loaded) { toast(c, "Still loading…"); return@SettingRow }
                    on = !on; save(on, msg)
                }
            }
            SettingsCard("Message") {
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(msg, { msg = it.take(300) }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Shown to members") })
                    PrimaryButton(if (busy) "Saving…" else "Save message", { if (!busy) save(on, msg) }, Modifier.padding(top = 10.dp), enabled = !busy && loaded)
                }
            }
        }
    }
}

/** Admin → Announcements: a pinned message for everyone. */
class AdminAnnouncementsPage : PageScreen() {
    override val perfName get() = "AdminAnnouncements"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        val list = remember { mutableStateListOf<JSONObject>() }
        var text by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var del by remember { mutableStateOf<JSONObject?>(null) }
        suspend fun load() { runCatching { Api.get("/api/admin/announcements") }.onSuccess { list.clear(); it.optJSONArray("announcements").objects().forEach { a -> list.add(a) } } }
        LaunchedEffect(Unit) { load() }
        fun post() {
            val t = text.trim(); if (t.isEmpty()) return
            busy = true
            scope.launch {
                runCatching { Api.post("/api/admin/announcements", JSONObject().put("text", t)) }
                    .onSuccess { text = ""; toast(c, "Posted"); load() }.onFailure { toast(c, Api.friendly(it)) }
                busy = false
            }
        }
        SettingsPage("Announcements", sub = "A pinned message shown to everyone") {
            SettingsCard("New announcement") {
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(text, { text = it.take(500) }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Text") })
                    PrimaryButton(if (busy) "Posting…" else "Post", { if (!busy) post() }, Modifier.padding(top = 10.dp), enabled = !busy && text.isNotBlank())
                }
            }
            if (list.isNotEmpty()) SettingsCard("Active") {
                list.forEach { a ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.optString("text"), color = MaterialTheme.colorScheme.onSurface)
                            Text(ago(a.optString("created_at").ifBlank { null }), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconBtn(Sym.Delete, "Remove", { del = a }, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        del?.let { a ->
            SettingsConfirmDialog("Remove announcement?", a.optString("text"), "Remove", danger = true,
                onOk = { del = null; scope.launch { runCatching { Api.delete("/api/admin/announcements?id=" + a.optString("id")) }; load() } },
                onDismiss = { del = null })
        }
    }
}

/** Admin → Invite codes: create and revoke sign-up invite codes. */
class AdminInviteCodesPage : PageScreen() {
    override val perfName get() = "AdminInviteCodes"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        val list = remember { mutableStateListOf<JSONObject>() }
        var maxUses by remember { mutableStateOf("1") }
        var days by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var revoke by remember { mutableStateOf<String?>(null) }
        suspend fun load() { runCatching { Api.get("/api/admin/invite-codes") }.onSuccess { list.clear(); it.optJSONArray("codes").objects().forEach { x -> list.add(x) } } }
        LaunchedEffect(Unit) { load() }
        fun gen() {
            busy = true
            scope.launch {
                val body = JSONObject().put("max_uses", maxUses.toIntOrNull() ?: 1)
                days.toIntOrNull()?.let { body.put("expires_days", it) }
                if (note.isNotBlank()) body.put("note", note.trim())
                runCatching { Api.post("/api/admin/invite-codes", body) }
                    .onSuccess { note = ""; toast(c, "Code " + it.optString("code")); load() }.onFailure { toast(c, Api.friendly(it)) }
                busy = false
            }
        }
        SettingsPage("Invite codes", sub = "Codes people use to sign up") {
            SettingsCard("New code") {
                Column(Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(maxUses, { maxUses = it.filter { ch -> ch.isDigit() }.take(5) }, Modifier.weight(1f), singleLine = true, label = { Text("Max uses") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(days, { days = it.filter { ch -> ch.isDigit() }.take(4) }, Modifier.weight(1f), singleLine = true, label = { Text("Expires (days)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    OutlinedTextField(note, { note = it.take(80) }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, label = { Text("Note (optional)") })
                    PrimaryButton(if (busy) "Creating…" else "Create code", { if (!busy) gen() }, Modifier.padding(top = 10.dp), enabled = !busy)
                }
            }
            if (list.isNotEmpty()) SettingsCard("Codes") {
                list.forEach { x ->
                    val code = x.optString("code")
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(code, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                            Text(x.optInt("used_count").toString() + " / " + x.optInt("max_uses") + " used" + (x.optString("note").ifBlank { null }?.let { " · $it" } ?: ""),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconBtn(Sym.Delete, "Revoke", { revoke = code }, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        revoke?.let { code ->
            SettingsConfirmDialog("Revoke $code?", "People can no longer sign up with this code.", "Revoke", danger = true,
                onOk = { revoke = null; scope.launch { runCatching { Api.delete("/api/admin/invite-codes?code=" + code) }; load() } },
                onDismiss = { revoke = null })
        }
    }
}

/** Admin → Audit logs: recent moderator / admin actions (read only). */
class AdminAuditLogsPage : PageScreen() {
    override val perfName get() = "AdminAuditLogs"
    @Composable override fun Content() {
        val list = remember { mutableStateListOf<JSONObject>() }
        var loaded by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            runCatching { Api.get("/api/admin/audit-logs?limit=100") }.onSuccess { list.clear(); it.optJSONArray("logs").objects().forEach { x -> list.add(x) } }
            loaded = true
        }
        SettingsPage("Audit logs", sub = "Recent admin and moderator actions") {
            if (loaded && list.isEmpty()) SettingsHint("Nothing logged yet.")
            if (list.isNotEmpty()) SettingsCard {
                list.forEach { x ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(x.optString("actor_nick").ifBlank { "?" }, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                            Text("  " + ago(x.optString("created_at").ifBlank { null }), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(x.optString("action") + (x.optString("target_type").ifBlank { null }?.let { " · $it" } ?: ""),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Sessions (admin.js "Sessions"): who is signed in right now, sign out one person or everyone. Native, same calls as the website admin. */
class AdminSessionsPage : PageScreen() {
    override val perfName get() = "AdminSessions"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        val list = remember { mutableStateListOf<JSONObject>() }
        var loaded by remember { mutableStateOf(false) }
        var confirmAll by remember { mutableStateOf(false) }
        suspend fun load() {
            runCatching { Api.get("/api/admin/sessions-mgr") }.onSuccess { list.clear(); it.optJSONArray("sessions").objects().forEach { x -> list.add(x) } }
            loaded = true
        }
        LaunchedEffect(Unit) { load() }
        SettingsPage("Sessions", sub = "People signed in right now") {
            if (loaded && list.isEmpty()) SettingsHint("No active sessions.")
            if (list.isNotEmpty()) {
                SettingsCard { SettingRow(Sym.Logout, "Sign out everyone", "Ends every session and marks all offline", danger = true) { confirmAll = true } }
                SettingsCard {
                    list.forEachIndexed { i, x ->
                        if (i > 0) RowDivider(64.dp)
                        val uid = x.optString("user_id"); val nick = x.optString("nickname").ifBlank { "?" }
                        val sub = listOfNotNull(x.optString("email").ifBlank { null }, ago(x.optString("created_at").ifBlank { null })).joinToString(" · ")
                        SettingRow(if (x.optBoolean("online")) Sym.RadioButtonChecked else Sym.Person, nick, sub, value = "Sign out") {
                            scope.launch {
                                runCatching { Api.delete("/api/admin/sessions-mgr?user_id=" + chEnc(uid)) }
                                    .onSuccess { list.removeAll { row -> row.optString("user_id") == uid }; toast(c, "Signed out $nick") }
                                    .onFailure { toast(c, Api.friendly(it)) }
                            }
                        }
                    }
                }
            }
        }
        if (confirmAll) SettingsConfirmDialog("Sign out everyone?", "Every person is signed out and marked offline.", "Sign out all", danger = true, onOk = {
            confirmAll = false
            scope.launch { runCatching { Api.delete("/api/admin/sessions-mgr?all=1") }.onSuccess { list.clear(); toast(c, "All sessions ended") }.onFailure { toast(c, Api.friendly(it)) } }
        }, onDismiss = { confirmAll = false })
    }
}

/** Banned devices (admin.js "Banned"): blocked IPs / devices, with Unban. Native, same /api/admin/device-bans calls as the website. */
class AdminBannedDevicesPage : PageScreen() {
    override val perfName get() = "AdminBannedDevices"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        val list = remember { mutableStateListOf<JSONObject>() }
        var loaded by remember { mutableStateOf(false) }
        suspend fun load() {
            runCatching { Api.get("/api/admin/device-bans") }.onSuccess { list.clear(); it.optJSONArray("bans").objects().forEach { x -> list.add(x) } }
            loaded = true
        }
        LaunchedEffect(Unit) { load() }
        SettingsPage("Banned devices", sub = "Blocked IPs and devices") {
            if (loaded && list.isEmpty()) SettingsHint("No banned devices.")
            if (list.isNotEmpty()) SettingsCard {
                list.forEachIndexed { i, x ->
                    if (i > 0) RowDivider(64.dp)
                    val id = x.optString("id")
                    val who = x.optString("ip").ifBlank { x.optString("fingerprint").take(18).ifBlank { "device" } }
                    val sub = listOfNotNull(x.optString("reason").ifBlank { null }, ago(x.optString("created_at").ifBlank { null })).joinToString(" · ")
                    SettingRow(Sym.Block, who, sub, value = "Unban") {
                        scope.launch { runCatching { Api.delete("/api/admin/device-bans?id=" + chEnc(id)) }.onSuccess { list.removeAll { r -> r.optString("id") == id }; toast(c, "Unbanned") }.onFailure { toast(c, Api.friendly(it)) } }
                    }
                }
            }
        }
    }
}

/** Ad-Free users (admin.js "Ad-Free"): the people who see no ads, with Remove. Native, same /api/admin/ad-exempt calls. */
class AdminAdFreePage : PageScreen() {
    override val perfName get() = "AdminAdFree"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        val list = remember { mutableStateListOf<JSONObject>() }
        var loaded by remember { mutableStateOf(false) }
        suspend fun load() {
            runCatching { Api.get("/api/admin/ad-exempt") }.onSuccess { list.clear(); it.optJSONArray("users").objects().forEach { x -> list.add(x) } }
            loaded = true
        }
        LaunchedEffect(Unit) { load() }
        SettingsPage("Ad-Free", sub = "People who see no ads") {
            if (loaded && list.isEmpty()) SettingsHint("Nobody is ad-free yet.")
            if (list.isNotEmpty()) SettingsCard {
                list.forEachIndexed { i, x ->
                    if (i > 0) RowDivider(64.dp)
                    val id = x.optString("id")
                    SettingRow(Sym.Sell, x.optString("nickname").ifBlank { "?" }, x.optString("email").ifBlank { null }, value = "Remove") {
                        scope.launch { runCatching { Api.delete("/api/admin/ad-exempt?id=" + chEnc(id)) }.onSuccess { list.removeAll { r -> r.optString("id") == id }; toast(c, "Removed") }.onFailure { toast(c, Api.friendly(it)) } }
                    }
                }
            }
        }
    }
}

/** Health check (admin.js "Health Check"): runs the owner diagnostics and lists each check with its status. Native, read-only. */
class AdminHealthPage : PageScreen() {
    override val perfName get() = "AdminHealth"
    @Composable override fun Content() {
        val checks = remember { mutableStateListOf<JSONObject>() }
        var summary by remember { mutableStateOf<JSONObject?>(null) }
        var loaded by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            runCatching { Api.get("/api/admin/diagnostics") }.onSuccess { checks.clear(); it.optJSONArray("checks").objects().forEach { x -> checks.add(x) }; summary = it.optJSONObject("summary") }
            loaded = true
        }
        SettingsPage("Health check", sub = summary?.let { "✅ ${it.optInt("ok")}   ⚠️ ${it.optInt("warn")}   ❌ ${it.optInt("fail")}" }) {
            if (loaded && checks.isEmpty()) SettingsHint("No checks returned.")
            if (checks.isNotEmpty()) SettingsCard {
                checks.forEachIndexed { i, x ->
                    if (i > 0) RowDivider(16.dp)
                    val icon = when (x.optString("status")) { "ok" -> "✅"; "warn" -> "⚠️"; "fail" -> "❌"; else -> "➖" }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp)) {
                        Text("$icon  " + x.optString("label").ifBlank { x.optString("category") }, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        x.optString("detail").ifBlank { null }?.let { Text(it, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp)) }
                    }
                }
            }
        }
    }
}

/** Attacks (admin.js "Attacks"): the security log — headline numbers, repeat offenders, and the recent attack feed. Native, read-only. */
class AdminSecurityPage : PageScreen() {
    override val perfName get() = "AdminSecurity"
    @Composable override fun Content() {
        val logs = remember { mutableStateListOf<JSONObject>() }
        val offenders = remember { mutableStateListOf<JSONObject>() }
        var stats by remember { mutableStateOf<JSONObject?>(null) }
        var loaded by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            runCatching { Api.get("/api/admin/security-log") }.onSuccess {
                stats = it.optJSONObject("stats")
                logs.clear(); it.optJSONArray("logs").objects().forEach { x -> logs.add(x) }
                offenders.clear(); it.optJSONArray("offenders").objects().forEach { x -> offenders.add(x) }
            }
            loaded = true
        }
        SettingsPage("Attacks", sub = stats?.let { "${it.optInt("total")} total · ${it.optInt("last24")} in 24h · ${it.optInt("unique_ips")} IPs" }) {
            if (loaded && logs.isEmpty() && offenders.isEmpty()) SettingsHint("No attacks logged. 🎉")
            if (offenders.isNotEmpty()) SettingsCard("Repeat offenders") {
                offenders.forEachIndexed { i, x ->
                    if (i > 0) RowDivider(16.dp)
                    val loc = listOfNotNull(x.optString("city").ifBlank { null }, x.optString("country").ifBlank { null }).joinToString(", ")
                    SettingRow(Sym.Public, x.optString("ip").ifBlank { "?" }, (if (loc.isNotBlank()) "$loc · " else "") + "${x.optInt("hits")} hits") {}
                }
            }
            if (logs.isNotEmpty()) SettingsCard("Recent") {
                logs.take(40).forEachIndexed { i, x ->
                    if (i > 0) RowDivider(16.dp)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(x.optString("attack_type").ifBlank { (x.optString("method") + " " + x.optString("path")).trim() }.take(60), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        Text(x.optString("ip") + " · " + ago(x.optString("created_at").ifBlank { null }), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
