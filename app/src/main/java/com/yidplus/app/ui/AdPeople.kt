package com.yidplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.yidplus.app.data.Api
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

// ═════════════════════════════════════════════════════════════════════════════
// AD 2.1 — People category (admin.js): Access, Invites, Warnings, Badges, Sessions, Banned devices, Ad-Free.
// ═════════════════════════════════════════════════════════════════════════════

/** The site's "search user..." boxes: GET /api/admin/users?search=q, first 5, typed 2+ letters. */
@Composable
fun adUserSearch(q: String): List<JSONObject>? {
    var found by remember { mutableStateOf<List<JSONObject>?>(null) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { found = null; return@LaunchedEffect }
        delay(300)
        runCatching { Api.get("/api/admin/users?search=" + adEnc(q.trim())) }.onSuccess { found = it.arr("users").take(5) }
    }
    return found
}

/** Access (buildAccessControlPanel): sign-in lock, allow-list add/remove, create an account. */
class AdAccessPage : PageScreen() {
    override val perfName = "AdAccess"
    override fun equals(other: Any?) = other is AdAccessPage
    override fun hashCode() = 3201
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/admin/access-control", tick)
        var allow by remember { mutableStateOf("") }
        var nEmail by remember { mutableStateOf("") }; var nNick by remember { mutableStateOf("") }; var nPass by remember { mutableStateOf("") }
        AdPanel("Access", sub = "Who can sign in", res = res) { j ->
            val locked = j.b("locked")
            adCard("🔒 Sign-in lock") {
                AdRow(if (locked) "Sign-in is LOCKED" else "Sign-in is open", sub = if (locked) "Only you and the allow-list can sign in" else "Everyone can sign in",
                    icon = if (locked) Sym.Lock else Sym.LockOpen, iconTint = if (locked) AdRed else AdGreen, modifier = Modifier.firstFocus())
                AdButton(if (locked) "Unlock" else "Lock now", danger = !locked) {
                    val go = { adminDo(c, scope, if (!locked) "🔒 Sign-in locked" else "🔓 Sign-in open", { tick++ }) { Api.put("/api/admin/access-control", jo("locked" to !locked)) } }
                    if (!locked) a.sure("Lock sign-in?", "Lock sign-in for EVERYONE except you and the allow-list?") { go() } else go()
                }
            }
            adCard("✅ Allow someone in") {
                AdField(allow, { allow = it }, "email@example.com")
                AdButton("Add") {
                    val e = allow.trim()
                    if (e.isEmpty()) toast(c, "Enter an email") else adminDo(c, scope, "✅ Allowed $e", { allow = ""; tick++ }) { Api.post("/api/admin/access-control", jo("action" to "allow", "email" to e)) }
                }
            }
            adCard("➕ Create an account", hint = "They'll be able to sign in right away (auto allow-listed).") {
                AdField(nEmail, { nEmail = it }, "email@example.com"); AdField(nNick, { nNick = it }, "Nickname"); AdField(nPass, { nPass = it }, "Temporary password (min 6)")
                AdButton("Create account") {
                    if (nEmail.isBlank() || nNick.isBlank() || nPass.isBlank()) toast(c, "Fill in all fields")
                    else adminDo(c, scope, "✅ Account created for @" + nNick.trim(), { nEmail = ""; nNick = ""; nPass = ""; tick++ }) {
                        Api.post("/api/admin/access-control", jo("action" to "create_account", "email" to nEmail.trim(), "nickname" to nNick.trim(), "password" to nPass.trim()))
                    }
                }
            }
            val list = j.arr("allowlist")
            item { SectionTitle("🗝️ Allow-list (" + list.size + ")") }
            if (list.isEmpty()) item { AdHint("No one on the allow-list yet.") }
            items(list, key = { "a" + it.s("email") }) { x ->
                AdRow(x.s("email"), sub = (if (x.s("nickname").isNotBlank()) "@" + x.s("nickname") else "no account yet") + x.s("note").let { if (it.isNotBlank()) " · $it" else "" },
                    icon = Icons.Filled.Email, trail = "Remove") {
                    a.sure("Remove?", "Remove " + x.s("email") + " from the allow-list?") {
                        adminDo(c, scope, "Removed", { tick++ }) { Api.delete("/api/admin/access-control?email=" + adEnc(x.s("email"))) }
                    }
                }
            }
        }
        AdDialogs(a)
    }
}

/** Invites (buildInviteCodesPanel): generate (uses, days, note), copy link, revoke. */
class AdInvitesPage : PageScreen() {
    override val perfName = "AdInvites"
    override fun equals(other: Any?) = other is AdInvitesPage
    override fun hashCode() = 3202
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        val clip = LocalClipboardManager.current
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/admin/invite-codes", tick)
        var uses by remember { mutableStateOf("1") }; var days by remember { mutableStateOf("30") }; var note by remember { mutableStateOf("") }
        AdPanel("Invites", sub = "Invite codes for new members", res = res) { j ->
            adCard("🎟️ Generate an invite") {
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { AdField(uses, { uses = it.filter(Char::isDigit) }, "Uses", Modifier.firstFocus(), number = true) }
                    Box(Modifier.weight(1f)) { AdField(days, { days = it.filter(Char::isDigit) }, "Days (0=never)", number = true) }
                }
                AdField(note, { note = it }, "Note (optional)")
                AdButton("Generate code") {
                    scope.launch {
                        runCatching { Api.post("/api/admin/invite-codes", jo("max_uses" to (uses.toIntOrNull() ?: 1), "expires_days" to (days.toIntOrNull() ?: 0), "note" to note)) }
                            .onSuccess { r -> toast(c, "🎟️ Code: " + r.s("code")); note = ""; tick++ }.onFailure { toast(c, "❌ " + Api.friendly(it)) }
                    }
                }
            }
            val codes = j.arr("codes")
            item { SectionTitle("🎫 Active codes (" + codes.size + ")") }
            if (codes.isEmpty()) item { AdHint("No invite codes yet.") }
            items(codes, key = { "c" + it.s("code") }) { k ->
                val exp = k.s("expires_at"); val expired = exp.isNotBlank() && (parseTime(exp) ?: Long.MAX_VALUE) < System.currentTimeMillis()
                val dead = expired || (k.has("uses_left") && !k.isNull("uses_left") && k.n("uses_left") <= 0)
                AdRow(k.s("code"), sub = k.n("used_count").toString() + "/" + k.optInt("max_uses", 1) + " used" + (if (exp.isNotBlank()) " · " + (if (expired) "expired" else "expires " + exp.take(10)) else " · no expiry") + k.s("note").let { if (it.isNotBlank()) " · $it" else "" },
                    icon = Sym.ConfirmationNumber, iconTint = if (dead) Color.Gray else AdGreen) {
                    val link = "https://yidplus.com/?invite=" + k.s("code")
                    a.actions(k.s("code"), "📋 Copy link" to { clip.setText(AnnotatedString(link)); toast(c, "📋 Copied: $link") },
                        "📤 Share link" to { share(c, link) },
                        "🗑 Revoke" to { a.sure("Revoke?", "Revoke code " + k.s("code") + "?") { adminDo(c, scope, "Revoked", { tick++ }) { Api.delete("/api/admin/invite-codes?code=" + adEnc(k.s("code"))) } } })
                }
            }
        }
        AdDialogs(a)
    }
}

/** Warnings (buildWarningsPanel): find a user, write the warning, send. */
class AdWarningsPage : PageScreen() {
    override val perfName = "AdWarnings"
    override fun equals(other: Any?) = other is AdWarningsPage
    override fun hashCode() = 3203
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var q by remember { mutableStateOf("") }
        val found = adUserSearch(q)
        val msgs = remember { mutableStateMapOf<String, String>() }
        AdPanel("Warnings", sub = "Send a warning to a user") { _ ->
            item { AdminSearchField(q, { q = it }, "Search user to warn...") }
            if (found != null && found.isEmpty()) item { AdHint("No users found") }
            found?.forEach { u ->
                val id = u.s("id")
                adCard(null) {
                    AdRow(u.s("nickname"), photo = u.s("photo_url"), letter = u.s("nickname").ifBlank { "?" })
                    AdField(msgs[id] ?: "", { msgs[id] = it }, "Warning message...", lines = 2)
                    AdButton("⚠️ Send warning") {
                        val m = (msgs[id] ?: "").trim()
                        if (m.isEmpty()) toast(c, "Write a message first")
                        else adminDo(c, scope, "⚠️ Warning sent!", { msgs.remove(id); q = "" }) { Api.post("/api/admin/warnings", jo("user_id" to id, "message" to m)) }
                    }
                }
            }
            if (found == null) item { AdHint("Type 2+ letters of a nickname or email.") }
        }
    }
}

private val badgeColors = listOf("#1F6F5C" to "Green", "#185FA5" to "Blue", "#A32D2D" to "Red", "#534AB7" to "Purple", "#B26A00" to "Gold", "#5F5E5A" to "Grey")

/** Badges (buildBadgesPanel): list + remove; find a user, badge text + colour, add. */
class AdBadgesPage : PageScreen() {
    override val perfName = "AdBadges"
    override fun equals(other: Any?) = other is AdBadgesPage
    override fun hashCode() = 3204
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/admin/badges", tick)
        var q by remember { mutableStateOf("") }
        val found = adUserSearch(q)
        val texts = remember { mutableStateMapOf<String, String>() }
        val colors = remember { mutableStateMapOf<String, Int>() }
        AdPanel("Badges", sub = "Custom badges next to a name", res = res) { j ->
            item { AdminSearchField(q, { q = it }, "Search user...") }
            if (found != null && found.isEmpty()) item { AdHint("No users found") }
            found?.forEach { u ->
                val id = u.s("id")
                adCard(null) {
                    AdRow(u.s("nickname"), photo = u.s("photo_url"), letter = u.s("nickname").ifBlank { "?" })
                    AdField(texts[id] ?: "", { texts[id] = it }, "Badge text...")
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        badgeColors.forEachIndexed { i, (hex, name) ->
                            val sel = (colors[id] ?: 0) == i
                            Box(Modifier.size(if (sel) 34.dp else 28.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(hex))).tap(CircleShape) { colors[id] = i }, contentAlignment = Alignment.Center) {
                                if (sel) Icon(Sym.Check, name, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    AdButton("Add") {
                        val t = (texts[id] ?: "").trim()
                        if (t.isEmpty()) toast(c, "Enter badge text")
                        else adminDo(c, scope, "Badge granted!", { texts.remove(id); tick++ }) { Api.post("/api/admin/badges", jo("user_id" to id, "badge_text" to t, "badge_color" to badgeColors[colors[id] ?: 0].first)) }
                    }
                }
            }
            val bl = j.arr("badges")
            item { SectionTitle("Given badges (" + bl.size + ")") }
            if (bl.isEmpty()) item { AdHint("No badges yet") }
            items(bl, key = { "b" + it.s("id") }) { b ->
                AdRow(b.s("badge_text"), sub = b.s("nickname", "user_id"), icon = Sym.MilitaryTech,
                    iconTint = runCatching { Color(android.graphics.Color.parseColor(b.s("badge_color").ifBlank { "#1F6F5C" })) }.getOrDefault(AdGreen), trail = "Remove") {
                    adminDo(c, scope, "Removed", { tick++ }) { Api.delete("/api/admin/badges?id=" + adEnc(b.s("id"))) }
                }
            }
        }
    }
}

/** Sessions (buildSessionsPanel): every signed-in session, Logout one, Force logout everyone. */
class AdSessionsPage : PageScreen() {
    override val perfName = "AdSessions"
    override fun equals(other: Any?) = other is AdSessionsPage
    override fun hashCode() = 3205
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/admin/sessions-mgr", tick)
        AdPanel("Sessions", sub = "Active sessions", res = res) { j ->
            item {
                AdButton("Force logout everyone", danger = true) {
                    a.sure("Log out everyone?", "Force logout ALL users? (You will also be logged out)") {
                        adminDo(c, scope, "Done — all sessions cleared", { tick++ }) { Api.delete("/api/admin/sessions-mgr?all=1") }
                    }
                }
            }
            val l = j.arr("sessions")
            if (l.isEmpty()) adEmpty("No active sessions", Sym.Devices)
            items(l, key = null) { s ->
                AdRow(s.s("nickname", "user_id") + if (s.b("online")) "  ● online" else "", sub = s.s("email") + " · " + adWhen(s.s("created_at")), letter = s.s("nickname").ifBlank { "?" }, trail = "Logout") {
                    a.actions("@" + s.s("nickname", "user_id"), "Log this person out" to { adminDo(c, scope, "Logged out", { tick++ }) { Api.delete("/api/admin/sessions-mgr?user_id=" + adEnc(s.s("user_id"))) } },
                        "Open their page" to { adOpenUser(s.s("user_id"), s.s("nickname")) })
                }
            }
        }
        AdDialogs(a)
    }
}

/** Banned (buildBannedDevicesPanel): ban an IP by hand, list, Unban. */
class AdBannedPage : PageScreen() {
    override val perfName = "AdBanned"
    override fun equals(other: Any?) = other is AdBannedPage
    override fun hashCode() = 3206
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/admin/device-bans", tick)
        var ip by remember { mutableStateOf("") }
        AdPanel("Banned", sub = "Banned devices / IPs", res = res) { j ->
            adCard("🚫 Ban an IP") {
                AdField(ip, { ip = it }, "IP address (e.g. 1.2.3.4)", Modifier.firstFocus())
                AdButton("+ Ban IP", danger = true) {
                    if (ip.isBlank()) toast(c, "Enter an IP address")
                    else adminDo(c, scope, "🚫 IP banned!", { ip = ""; tick++ }) { Api.post("/api/admin/device-bans", jo("ip" to ip.trim(), "reason" to "Admin manual ban")) }
                }
            }
            val l = j.arr("bans")
            item { SectionTitle("Bans (" + l.size + ")") }
            if (l.isEmpty()) item { AdHint("No device bans yet") }
            items(l, key = { "b" + it.s("id") }) { b ->
                AdRow(b.s("ip", "fingerprint").ifBlank { "—" }, sub = b.s("banned_by") + " · " + adWhen(b.s("created_at")) + b.s("reason").let { if (it.isNotBlank()) " · $it" else "" },
                    icon = Sym.Block, iconTint = AdRed, trail = "Unban") {
                    a.sure("Unban?", "Remove this ban?") { adminDo(c, scope, "✓ Ban removed", { tick++ }) { Api.delete("/api/admin/device-bans?id=" + adEnc(b.s("id"))) } }
                }
            }
        }
        AdDialogs(a)
    }
}

/** Ad-Free (buildAdExemptPanel): everyone with no ads, Remove; search a user, Grant. */
class AdAdFreePage : PageScreen() {
    override val perfName = "AdAdFree"
    override fun equals(other: Any?) = other is AdAdFreePage
    override fun hashCode() = 3207
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/admin/ad-exempt", tick)
        var q by remember { mutableStateOf("") }
        val found = adUserSearch(q)?.filter { !it.b("no_ads") }
        AdPanel("Ad-Free", sub = "People who never see ads", res = res) { j ->
            item { AdminSearchField(q, { q = it }, "Search user to grant ad-free...") }
            if (found != null && found.isEmpty()) item { AdHint("No users found") }
            found?.let { f -> items(f, key = { "f" + it.s("id") }) { u ->
                AdRow(u.s("nickname"), photo = u.s("photo_url"), letter = u.s("nickname").ifBlank { "?" }, trail = "+ Grant Ad-Free") {
                    adminDo(c, scope, "🚫 Ad-free granted!", { q = ""; tick++ }) { Api.put("/api/admin/users", jo("id" to u.s("id"), "no_ads" to true)) }
                }
            } }
            val l = j.arr("users")
            item { SectionTitle("🚫📣 Ad-free users (" + l.size + ")") }
            if (l.isEmpty()) item { AdHint("No ad-free users yet") }
            items(l, key = { "u" + it.s("id") }) { u ->
                AdRow(u.s("nickname"), sub = u.s("email"), photo = u.s("photo_url"), letter = u.s("nickname").ifBlank { "?" }, trail = "Remove") {
                    adminDo(c, scope, "✅ Ads restored", { tick++ }) { Api.put("/api/admin/users", jo("id" to u.s("id"), "no_ads" to false)) }
                }
            }
        }
    }
}
