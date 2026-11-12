package com.yidplus.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yidplus.app.data.Api
import kotlinx.coroutines.launch
import org.json.JSONObject

// ═════════════════════════════════════════════════════════════════════════════
// AD 2.1 — the rest of the site's "User 360" (admin.js openUserDetailModal) under the app's user page:
// edit details, new password, devices & IPs with Ban device, login history, moderation actions, feature blocks,
// private admin notes, and every activity number opens its list (admin.js adminOpenUserList).
// ═════════════════════════════════════════════════════════════════════════════

private val featLabels = listOf("shorts" to "Shorts", "statuses" to "Status", "music" to "Music", "channels" to "Channels", "chat" to "Chat")

@Composable
fun AdUserMore(id: String, nick: String, u: JSONObject, detail: JSONObject?) {
    val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
    var tick by remember { mutableIntStateOf(0) }
    var blocks by remember { mutableStateOf<List<JSONObject>?>(null) }
    var notes by remember { mutableStateOf<List<JSONObject>?>(null) }
    LaunchedEffect(id, tick) {
        launch { runCatching { Api.get("/api/admin/feature-blocks?user_id=" + adEnc(id)) }.onSuccess { j -> val now = System.currentTimeMillis(); blocks = j.arr("blocks").filter { b -> b.s("blocked_until").isBlank() || (parseTime(b.s("blocked_until")) ?: 0) > now } }.onFailure { blocks = emptyList() } }
        launch { runCatching { Api.get("/api/admin/notes?user_id=" + adEnc(id)) }.onSuccess { notes = it.arr("notes") }.onFailure { notes = emptyList() } }
    }
    SettingsCard("Account") {
        SettingRow(Sym.Edit, "Edit details", "Nickname, email, phone, password") { Nav.open(AdEditUserPage(id, u.toString())) }
        RowDivider(64.dp)
        SettingRow(Sym.Key, "🔑 New password", "Set a temporary password") {
            a.ask("New password", "Temporary password (6+ characters)") { p ->
                if (p.trim().length < 6) toast(c, "⚠️ Password must be at least 6 characters.")
                else adminDo(c, scope, "🔑 New password set! Let them know to change it once they sign in.") { Api.put("/api/admin/users", jo("id" to id, "password" to p.trim())) }
            }
        }
    }
    detail?.optJSONObject("counts")?.let { cn ->
        SettingsCard("Lists") {
            listOf(Triple("followers", "👤 Followers", Sym.People), Triple("following", "➡️ Following", Sym.PersonAdd), Triple("shorts", "🎬 Shorts", Sym.Movie),
                Triple("statuses", "⭐ Statuses", Sym.Star), Triple("music", "🎵 Music", Sym.MusicNote), Triple("groups_created", "👥 Groups made", Sym.Groups),
                Triple("groups_joined", "🚪 Groups joined", Icons.Filled.MeetingRoom), Triple("telegram_channels", "📨 TG channels", Sym.Send)).forEach { (k, l, ic) ->
                val n = cn.optInt(k)
                SettingRow(ic, l, null, value = adminN(n)) { if (n > 0) Nav.open(AdUserListPage(id, k)) else toast(c, "Nothing here.") }
            }
        }
    }
    SettingsCard("📱 Devices & IPs") {
        SettingsHint("Ban a device to stop this person returning with a new account from the same device/IP.")
        val devs = detail?.arr("devices") ?: emptyList()
        if (devs.isEmpty()) AdHint("No device history yet")
        devs.forEach { d ->
            val label = d.s("ip").ifBlank { "?" } + d.s("fingerprint").let { if (it.isNotBlank()) " · " + it.take(10) else "" }
            AdRow(label, sub = d.optInt("hits", 1).toString() + " logins · " + adWhen(d.s("last_seen")), icon = Sym.Devices,
                tag = if (d.b("banned")) "🚫 Banned" to AdRed else null) {
                if (d.b("banned")) return@AdRow
                if (d.s("ip").isBlank() && d.s("fingerprint").isBlank()) { toast(c, "No device info"); return@AdRow }
                a.sure("Ban device?", "Ban this device/IP? Nobody will be able to sign in or register from it.") {
                    adminDo(c, scope, "🚫 Device banned") { Api.post("/api/admin/device-bans", jo("ip" to d.s("ip").ifBlank { null }, "fingerprint" to d.s("fingerprint").ifBlank { null }, "reason" to "Banned from user 360 view")) }
                }
            }
        }
    }
    SettingsCard("🔐 Login history (last 10)") {
        val l = detail?.arr("login_history")?.take(10) ?: emptyList()
        if (l.isEmpty()) AdHint("No login history yet")
        l.forEach { x -> AdRow((if (x.s("action") == "fail") "❌ Failed login" else "✅ Login") + " — " + x.s("ip").ifBlank { "?" }, trail = adWhen(x.s("created_at"))) }
    }
    SettingsCard("📋 Moderation actions against this user") {
        val l = detail?.arr("audit_as_target")?.take(10) ?: emptyList()
        if (l.isEmpty()) AdHint("No moderation actions on this user")
        l.forEach { x -> AdRow("⚡ " + x.s("action") + " by @" + x.s("actor_nick").ifBlank { "?" }, trail = adWhen(x.s("created_at"))) }
    }
    SettingsCard("🚫 Feature blocks") {
        SettingsHint("Blocking only hides access to that feature — nothing is ever deleted.")
        val bl = blocks
        if (bl != null && bl.isEmpty()) AdHint("No active blocks.")
        bl?.forEach { b ->
            val lab = featLabels.firstOrNull { it.first == b.s("feature") }?.second ?: b.s("feature")
            AdRow(lab, sub = (if (b.s("blocked_until").isNotBlank()) "until " + clock(b.s("blocked_until")) + " " + b.s("blocked_until").take(10) else "indefinitely") + " · by " + b.s("blocked_by").ifBlank { "?" },
                icon = Sym.Block, iconTint = AdRed, trail = "Unblock") {
                adminDo(c, scope, "✅ Unblocked from $lab", { tick++ }) { Api.delete("/api/admin/feature-blocks?user_id=" + adEnc(id) + "&feature=" + adEnc(b.s("feature"))) }
            }
        }
        var feat by remember { mutableIntStateOf(0) }
        var hours by remember { mutableStateOf("") }
        var pick by remember { mutableStateOf(false) }
        SettingRow(Sym.Tune, "Feature", null, value = featLabels[feat].second) { pick = true }
        AdField(hours, { hours = it.filter { ch -> ch.isDigit() || ch == '.' } }, "Hours (blank = forever)", number = true)
        AdButton("Block from this") {
            val f = featLabels[feat]
            adminDo(c, scope, "🚫 Blocked from " + f.second + "!", { hours = ""; tick++ }) { Api.post("/api/admin/feature-blocks", jo("user_id" to id, "feature" to f.first, "hours" to (hours.toDoubleOrNull() ?: 0.0))) }
        }
        if (pick) SettingsChoiceDialog("Block from", featLabels.map { it.second }, feat, { feat = it }, { pick = false })
    }
    SettingsCard("📝 Private admin notes") {
        val nl = notes
        if (nl != null && nl.isEmpty()) AdHint("No notes yet.")
        nl?.forEach { n -> AdRow(n.s("note"), sub = "@" + n.s("author_nick").ifBlank { "?" } + " · " + adWhen(n.s("created_at")), icon = Sym.StickyNote2) }
        var note by remember { mutableStateOf("") }
        AdField(note, { note = it }, "Add a private note only admins can see...", lines = 2)
        AdButton("Add note") {
            if (note.isBlank()) toast(c, "⚠️ Write something first.")
            else adminDo(c, scope, "✅ Note added!", { note = ""; tick++ }) { Api.post("/api/admin/notes", jo("user_id" to id, "note" to note.trim())) }
        }
    }
    AdDialogs(a)
}

/** admin.js adminOpenUserList: GET /api/admin/user-lists?user_id=&type= → {title, items}. People rows open their own page. */
data class AdUserListPage(val id: String, val type: String) : PageScreen() {
    override val perfName = "AdUserList"
    @Composable override fun Content() {
        val res = adRes("/api/admin/user-lists?user_id=" + adEnc(id) + "&type=" + adEnc(type))
        val people = type == "followers" || type == "following"
        val d = res.data
        AdPanel(d?.let { it.s("title").ifBlank { type } + " (" + it.arr("items").size + ")" } ?: type, res = res) { j ->
            if (j.s("error").isNotBlank()) { item { AdHint(j.s("error"), AdRed) }; return@AdPanel }
            val l = j.arr("items")
            if (l.isEmpty()) adEmpty("Nothing here.")
            items(l, key = null) { it2 ->
                val label = it2.s("nickname").ifBlank { if (people) "Unknown" else "(untitled)" }
                AdRow(label, photo = it2.s("photo_url"), letter = it2.s("emoji").ifBlank { label }, trail = it2.s("created_at").take(10)) {
                    if (people && it2.s("id").isNotBlank()) adOpenUser(it2.s("id"), label)
                }
            }
        }
    }
}

/** admin.js adminEditUser / adminSaveUserEdit: PUT /api/admin/users {id, nickname?, email?, phone, password?}. */
data class AdEditUserPage(val id: String, val rowJson: String) : PageScreen() {
    override val perfName = "AdEditUser"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        val u = remember { runCatching { JSONObject(rowJson) }.getOrDefault(JSONObject()) }
        var nick by remember { mutableStateOf(u.s("nickname")) }
        var email by remember { mutableStateOf(u.s("email")) }
        var phone by remember { mutableStateOf(u.s("phone")) }
        var pass by remember { mutableStateOf("") }
        SettingsPage("Edit @" + u.s("nickname"), sub = "Saved to the site at once") {
            SettingsCard("Details") {
                AdField(nick, { nick = it }, "Nickname")
                AdField(email, { email = it }, "Email")
                AdField(phone, { phone = it }, "Phone", number = true)
                AdField(pass, { pass = it }, "New password (leave blank = no change)", password = true)
                AdButton("💾 Save") {
                    val p = jo("id" to id)
                    if (nick.isNotBlank()) p.put("nickname", nick.trim())
                    if (email.isNotBlank()) p.put("email", email.trim())
                    p.put("phone", phone.trim())
                    if (pass.isNotEmpty()) p.put("password", pass)
                    adminDo(c, scope, "💾 Saved!", { AdminChanged.n++; Nav.back() }) { Api.put("/api/admin/users", p) }
                }
            }
        }
    }
}
