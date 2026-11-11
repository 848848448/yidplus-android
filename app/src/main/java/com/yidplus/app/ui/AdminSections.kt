package com.yidplus.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yidplus.app.data.Api
import com.yidplus.app.data.Res
import com.yidplus.app.data.Session
import com.yidplus.app.data.Store
import com.yidplus.app.data.objects
import com.yidplus.app.data.str
import com.yidplus.app.data.strOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

// ═════════════════════════════════════════════════════════════════════════════
// The site's admin panel (yidplus.com/yidplus-admin, js/admin.js), native. Every call is the one admin.js makes.
// The site's server checks the signed-in account's admin role on each /api/admin call (functions/api/admin/*.js:
// requireUser + isAdminRole); the email + PIN screen is only the web page's own door, so these screens open straight in.
// ═════════════════════════════════════════════════════════════════════════════

val adminIsOwner: Boolean get() = Session.me?.let { it.isOwner || it.role == "owner" } == true || SettingsProfile.demo

/** One admin list: the saved copy at once, then the site's answer (no spinner over what is already on screen). */
@Composable
fun AdminList(title: String, sub: String? = null, res: Res<JSONObject>, tick: Int, actions: @Composable RowScope.() -> Unit = {},
              header: (@Composable () -> Unit)? = null, body: LazyListScope.(JSONObject) -> Unit) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(res, tick) { res.load(force = true) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(title, onBack = { Nav.back() }, sub = sub, actions = actions)
        val d = res.data
        when {
            d == null && res.error != null -> Problem(res.error!!, res.offline, { scope.launch { res.load(true) } })
            d == null -> SkeletonRows(rows = 7)
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                if (header != null) item { header() }
                body(d)
            }
        }
    }
}

/** A small pill button (WhatsApp-style), focusable for the keypad. */
@Composable
fun AdminPill(text: String, color: Color = MaterialTheme.colorScheme.primary, filled: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(18.dp))
        .background(if (filled) color else color.copy(alpha = 0.10f))
        .tap(RoundedCornerShape(18.dp), onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (filled) Color.White else color, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun AdminTag(text: String, color: Color) {
    Text(text, Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 1.dp),
        color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1)
}

/** Runs one admin write and says how it went, the way the site's toasts do. */
fun adminDo(c: android.content.Context, scope: CoroutineScope, ok: String, after: () -> Unit = {}, call: suspend () -> Unit) {
    scope.launch {
        runCatching { call() }.onSuccess { toast(c, ok); after() }.onFailure { toast(c, Api.friendly(it)) }
    }
}

fun adminN(n: Int): String = when { n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f); n >= 10_000 -> "${n / 1000}k"; n >= 1000 -> "%.1fk".format(n / 1000f); else -> n.toString() }

private val roleName = mapOf("owner" to "Owner", "admin_super" to "Super admin", "admin_limited" to "Moderator")

fun adminMutedNow(u: JSONObject): Boolean = parseTime(u.strOrNull("muted_until"))?.let { it > System.currentTimeMillis() } == true

@Composable
fun AdminSearchField(q: String, onQ: (String) -> Unit, hint: String) {
    val kb = LocalSoftwareKeyboardController.current
    val fm = LocalFocusManager.current
    OutlinedTextField(q, onQ, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), singleLine = true,
        placeholder = { Text(hint) }, leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = { if (q.isNotEmpty()) IconBtn(Icons.Filled.Close, "Clear", { onQ(""); kb?.hide(); fm.clearFocus() }) },
        shape = RoundedCornerShape(24.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { kb?.hide(); fm.clearFocus() }))
}

// ── Users (admin.js buildUsersPanel: GET /api/admin/users, filtered on the phone by nickname or email) ──────────────

class AdminUsersPage : PageScreen() {
    override val perfName = "AdminUsers"
    override fun equals(other: Any?) = other is AdminUsersPage
    override fun hashCode() = 301
    @Composable override fun Content() {
        val res = remember { Res("/api/admin/users") { it } }
        var q by remember { mutableStateOf("") }
        var tick by remember { mutableIntStateOf(0) }
        LaunchedEffect(AdminChanged.n) { if (AdminChanged.n > 0) tick++ }
        val all = res.data?.optJSONArray("users").objects()
        AdminList("Users", sub = if (all.isNotEmpty()) "${all.size} members" else null, res = res, tick = tick,
            header = { AdminSearchField(q, { q = it }, if (adminIsOwner) "Search by nickname or email" else "Search by nickname") }) { d ->
            val ql = q.trim().lowercase()
            val list = d.optJSONArray("users").objects().filter { ql.isEmpty() || it.str("nickname").lowercase().contains(ql) || it.str("email").lowercase().contains(ql) }
            if (list.isEmpty()) item { Text("No users found", Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(list.take(300), key = { it.str("id") }) { u -> AdminUserRow(u, first = u === list.first()) { Nav.open(AdminUserPage(u.str("id"), u.toString())) } }
        }
    }
}

/** Bumped after a change on a person's page, so the list behind it reloads. */
object AdminChanged { var n by mutableIntStateOf(0) }

@Composable
fun AdminUserRow(u: JSONObject, first: Boolean = false, onClick: () -> Unit) {
    val nick = u.str("nickname").ifBlank { "user" }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min = 70.dp).then(if (first) Modifier.firstFocus() else Modifier).tap(RoundedCornerShape(0.dp), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Avatar(Api.abs(u.strOrNull("photo_url")), nick, 48.dp)
                if (u.optInt("online") == 1 || u.optBoolean("online")) Box(Modifier.align(Alignment.BottomEnd).size(13.dp).clip(CircleShape).background(BadgeGreen).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("@$nick", style = MaterialTheme.typography.titleMedium.merge(TextStyle(textDirection = TextDirection.Ltr)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (u.optInt("verified") == 1 || u.optBoolean("verified")) Icon(Icons.Filled.Verified, "Verified", tint = BadgeGreen, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                    roleName[u.str("role")]?.let { AdminTag(it, MaterialTheme.colorScheme.tertiary) }
                    if (u.optInt("blocked") == 1 || u.optBoolean("blocked")) AdminTag("Blocked", MaterialTheme.colorScheme.error)
                    if (adminMutedNow(u)) AdminTag("Muted", Color(0xFFE11D48))
                }
                val sub = listOfNotNull(u.str("email").ifBlank { null }?.takeIf { adminIsOwner }, u.strOrNull("created_at")?.let { "joined " + ago(it) }).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(Modifier.padding(start = 78.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

// ── One person (admin.js openUserDetailModal + the row's buttons) ────────────────────────────────────────────────

data class AdminUserPage(val id: String, val rowJson: String) : PageScreen() {
    override val perfName = "AdminUser"
    @OptIn(ExperimentalLayoutApi::class)
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        var u by remember { mutableStateOf(runCatching { JSONObject(rowJson) }.getOrDefault(JSONObject().put("id", id))) }
        var detail by remember { mutableStateOf<JSONObject?>(null) }
        var ask by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(id) {
            runCatching { Api.get("/api/admin/user-detail?user_id=" + Uri.encode(id)) }.onSuccess { j ->
                detail = j
                j.optJSONObject("profile")?.let { p -> val m = JSONObject(u.toString()); for (k in p.keys()) if (!p.isNull(k)) m.put(k, p.get(k)); u = m }
            }
        }
        fun patch(k: String, v: Any) { u = JSONObject(u.toString()).put(k, v); AdminChanged.n++ }
        val nick = u.str("nickname").ifBlank { "user" }
        val verified = u.optInt("verified") == 1 || u.optBoolean("verified")
        val blocked = u.optInt("blocked") == 1 || u.optBoolean("blocked")
        val noAds = u.optInt("no_ads") == 1 || u.optBoolean("no_ads")
        val muted = adminMutedNow(u)
        val role = u.str("role").ifBlank { "member" }
        val protected = u.optBoolean("protected") || u.optInt("protected") == 1 || role == "owner"
        SettingsPage("@$nick", sub = roleName[role] ?: "Member") {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(Api.abs(u.strOrNull("photo_url")), nick, 84.dp)
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("@$nick", style = MaterialTheme.typography.titleLarge.merge(TextStyle(textDirection = TextDirection.Ltr)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (verified) Icon(Icons.Filled.Verified, "Verified", tint = BadgeGreen, modifier = Modifier.padding(start = 6.dp).size(20.dp))
                    }
                    val line = listOfNotNull(if (u.optInt("online") == 1 || u.optBoolean("online")) "🟢 Online now" else "Offline",
                        u.strOrNull("created_at")?.let { "Joined " + ago(it) }).joinToString(" · ")
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (adminIsOwner) {
                        u.strOrNull("email")?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp)) }
                        u.strOrNull("phone")?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Row(Modifier.padding(top = 6.dp)) {
                        if (blocked) AdminTag("Blocked", MaterialTheme.colorScheme.error)
                        if (muted) AdminTag("Muted", Color(0xFFE11D48))
                        if (noAds) AdminTag("Ad-free", BadgeGreen)
                    }
                }
            }
            detail?.optJSONObject("counts")?.let { cn ->
                SettingsCard("Activity") {
                    FlowRow(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("messages" to "Messages", "followers" to "Followers", "following" to "Following", "shorts" to "Shorts", "statuses" to "Statuses",
                            "groups_created" to "Groups made", "groups_joined" to "Groups in", "music" to "Music", "telegram_channels" to "Telegram").forEach { (k, l) ->
                            Column(Modifier.widthIn(min = 92.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(adminN(cn.optInt(k)), style = MaterialTheme.typography.titleMedium)
                                Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
            if (protected) SettingsHint("This is the owner's account. It can't be changed here.") else {
                SettingsCard("Actions") {
                    SettingRow(Icons.Filled.Verified, if (verified) "Remove verified mark" else "Verify", if (verified) "The ✅ goes away" else "Gives the green ✅ mark", modifier = Modifier.firstFocus()) {
                        adminDo(c, scope, if (!verified) "✅ Verified badge granted!" else "Badge removed.", { patch("verified", if (verified) 0 else 1) }) {
                            Api.put("/api/admin/users", JSONObject().put("id", id).put("verified", !verified))
                        }
                    }
                    RowDivider(64.dp)
                    SettingRow(Icons.Filled.VolumeOff, if (muted) "Unmute" else "Mute", if (muted) "They can write again" else "They can't send messages for a while") {
                        if (muted) adminDo(c, scope, "🔊 Unmuted!", { patch("muted_until", "") }) { Api.post("/api/admin/mute", JSONObject().put("user_id", id).put("hours", 0)) }
                        else ask = "mute"
                    }
                    RowDivider(64.dp)
                    SettingRow(Icons.Filled.Block, if (blocked) "Unblock" else "Block", if (blocked) "They can use YID PLUS again" else "They can't sign in or post", danger = !blocked) {
                        if (blocked) adminDo(c, scope, "✅ User unblocked.", { patch("blocked", 0) }) { Api.put("/api/admin/users", JSONObject().put("id", id).put("blocked", false)) }
                        else ask = "block"
                    }
                    if (adminIsOwner) {
                        RowDivider(64.dp)
                        SettingRow(Icons.Filled.Shield, if (role == "admin_super") "Demote to member" else "Promote to super admin", "Role now: " + (roleName[role] ?: "Member")) { ask = "role" }
                        RowDivider(64.dp)
                        SettingRow(Icons.Filled.Campaign, "Ad-free", "No ads for this person", switch = noAds) {
                            adminDo(c, scope, if (!noAds) "🚫 Ad-free granted!" else "📣 Ads restored.", { patch("no_ads", if (noAds) 0 else 1) }) {
                                Api.put("/api/admin/users", JSONObject().put("id", id).put("no_ads", !noAds))
                            }
                        }
                        RowDivider(64.dp)
                        SettingRow(Icons.Filled.Logout, "Kick out", "Signs them out on every device") { ask = "kick" }
                        RowDivider(64.dp)
                        SettingRow(Icons.Filled.DeleteForever, "Delete account", "Removes them and everything they posted", danger = true) { ask = "delete" }
                    }
                }
            }
        }
        when (ask) {
            "mute" -> SettingsChoiceDialog("Mute @$nick for", listOf("1 hour", "24 hours", "1 week"), 1, { i ->
                val h = listOf(1, 24, 168)[i]
                adminDo(c, scope, "🔇 Muted for ${h}h!", { patch("muted_until", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(System.currentTimeMillis() + h * 3_600_000L))) }) {
                    Api.post("/api/admin/mute", JSONObject().put("user_id", id).put("hours", h))
                }
            }, { ask = null })
            "block" -> SettingsConfirmDialog("Block @$nick?", "They won't be able to use YID PLUS until you unblock them.", "Block", danger = true,
                onOk = { adminDo(c, scope, "🚫 User blocked.", { patch("blocked", 1) }) { Api.put("/api/admin/users", JSONObject().put("id", id).put("blocked", true)) } }, onDismiss = { ask = null })
            "role" -> { val newRole = if (role == "admin_super") "member" else "admin_super"
                SettingsConfirmDialog(if (newRole == "admin_super") "Make @$nick a super admin?" else "Demote @$nick?", if (newRole == "admin_super") "They get the admin panel." else "They become a member again.", "Yes",
                    onOk = { adminDo(c, scope, if (newRole == "admin_super") "🛡 Promoted to Super Admin!" else "Demoted to member.", { patch("role", newRole) }) { Api.put("/api/admin/users", JSONObject().put("id", id).put("role", newRole)) } },
                    onDismiss = { ask = null }) }
            "kick" -> SettingsConfirmDialog("Sign @$nick out everywhere?", "They'll need to sign in again.", "Kick out",
                onOk = { adminDo(c, scope, "👢 @$nick was signed out") { Api.post("/api/admin/access-control", JSONObject().put("action", "force_logout").put("user_id", id)) } }, onDismiss = { ask = null })
            "delete" -> SettingsConfirmDialog("Delete @$nick?", "Their account and everything in it is deleted for good. This can't be undone.", "Delete", danger = true,
                onOk = { adminDo(c, scope, "🗑 Deleted", { AdminChanged.n++; Nav.back() }) { Api.delete("/api/admin/users?id=" + Uri.encode(id)) } }, onDismiss = { ask = null })
        }
    }
}

// ── Reports (admin.js buildReportsPanel: GET /api/reports; Ban = PUT /api/admin/users {id, blocked:true}; Dismiss = DELETE /api/reports?id=) ──

class AdminReportsPage : PageScreen() {
    override val perfName = "AdminReports"
    override fun equals(other: Any?) = other is AdminReportsPage
    override fun hashCode() = 302
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        val res = remember { Res("/api/reports") { it } }
        val gone = remember { mutableStateListOf<String>() }
        var ban by remember { mutableStateOf<JSONObject?>(null) }
        AdminList("Reports", res = res, tick = 0) { d ->
            val list = d.optJSONArray("reports").objects().filter { it.str("id") !in gone }
            if (list.isEmpty()) item { EmptyNote(Icons.Filled.Flag, "No reports yet 🎉", modifier = Modifier.fillMaxWidth().padding(top = 40.dp)) }
            items(list, key = { it.str("id") }) { r ->
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
                        Avatar(null, r.str("reported_nick").ifBlank { "?" }, 44.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("@" + r.str("reported_nick").ifBlank { r.str("reported_id") }, style = MaterialTheme.typography.titleMedium.merge(TextStyle(textDirection = TextDirection.Ltr)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Reported by @" + r.str("reporter_nick").ifBlank { r.str("reporter_id") } + " · " + ago(r.strOrNull("created_at")),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.str("reason").ifBlank { "(no reason given)" }, style = MaterialTheme.typography.bodyLarge.merge(TextStyle(textDirection = TextDirection.Content)), modifier = Modifier.padding(top = 4.dp))
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AdminPill("Ban", MaterialTheme.colorScheme.error, modifier = if (r === list.first()) Modifier.firstFocus() else Modifier) { ban = r }
                                AdminPill("Dismiss") { adminDo(c, scope, "✓ Dismissed", { gone.add(r.str("id")) }) { Api.delete("/api/reports?id=" + Uri.encode(r.str("id"))) } }
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(start = 72.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        ban?.let { r ->
            val nick = r.str("reported_nick")
            SettingsConfirmDialog("Ban @$nick?", "They're blocked from YID PLUS.", "Ban", danger = true, onOk = {
                adminDo(c, scope, "🚫 @$nick banned!") { Api.put("/api/admin/users", JSONObject().put("id", r.str("reported_id")).put("blocked", true)) }
            }, onDismiss = { ban = null })
        }
    }
}

// ── Verification requests (admin.js _loadVerifyRequests: GET /api/verify-request; PUT {id, action: approve|deny}) ──

class AdminVerifyPage : PageScreen() {
    override val perfName = "AdminVerify"
    override fun equals(other: Any?) = other is AdminVerifyPage
    override fun hashCode() = 303
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        val res = remember { Res("/api/verify-request") { it } }
        val gone = remember { mutableStateListOf<String>() }
        AdminList("Verification requests", res = res, tick = 0) { d ->
            val list = d.optJSONArray("requests").objects().filter { it.str("id") !in gone }
            if (list.isEmpty()) item { EmptyNote(Icons.Filled.Verified, "No pending requests 👍", modifier = Modifier.fillMaxWidth().padding(top = 40.dp)) }
            items(list, key = { it.str("id") }) { r ->
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(Api.abs(r.strOrNull("photo_url")), r.str("nickname").ifBlank { "U" }, 48.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp).tap(RoundedCornerShape(8.dp)) { Nav.open(AdminUserPage(r.str("user_id"), JSONObject().put("id", r.str("user_id")).put("nickname", r.str("nickname")).toString())) }) {
                            Text("@" + r.str("nickname"), style = MaterialTheme.typography.titleMedium.merge(TextStyle(textDirection = TextDirection.Ltr)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            r.strOrNull("note")?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                        AdminPill("Approve", BadgeGreen, filled = true, modifier = if (r === list.first()) Modifier.firstFocus() else Modifier) {
                            adminDo(c, scope, "✅ Verified", { gone.add(r.str("id")) }) { Api.put("/api/verify-request", JSONObject().put("id", r.str("id")).put("action", "approve")) }
                        }
                        Spacer(Modifier.width(6.dp))
                        AdminPill("Deny", MaterialTheme.colorScheme.onSurfaceVariant) {
                            adminDo(c, scope, "Denied", { gone.add(r.str("id")) }) { Api.put("/api/verify-request", JSONObject().put("id", r.str("id")).put("action", "deny")) }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(start = 76.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
