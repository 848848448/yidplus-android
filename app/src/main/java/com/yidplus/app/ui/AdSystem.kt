package com.yidplus.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.yidplus.app.data.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.json.JSONObject

// ═════════════════════════════════════════════════════════════════════════════
// AD 2.1 — System category (admin.js): Features extras (auto-moderation, optimize), App, Ads, Maintenance,
// Anti-spam, Attacks, Health check, Code errors, Config & Keys, IP logs, Audit logs, Export, Nuclear, Admin settings.
// ═════════════════════════════════════════════════════════════════════════════

/** App (inline 'app-settings'): site title, registration open, support chat on sign-in / main app, test email. */
class AdAppSettingsPage : PageScreen() {
    override val perfName = "AdAppSettings"
    override fun equals(other: Any?) = other is AdAppSettingsPage
    override fun hashCode() = 3501
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/settings", tick)
        var title by remember { mutableStateOf<String?>(null) }
        var testTo by remember { mutableStateOf("") }
        var testOut by remember { mutableStateOf<String?>(null) }
        AdPanel("App", sub = "Name, sign-up and help chat", res = res) { j ->
            val s = j.optJSONObject("settings") ?: JSONObject()
            if (title == null && j.has("settings")) title = s.s("app_title").ifBlank { "YID PLUS" }
            fun toggle(k: String, on: Boolean, ok: String) = adminDo(c, scope, ok, { tick++ }) { adSaveSetting(k, if (on) "true" else "false") }
            adCard("🏷 Site name") {
                AdField(title ?: "", { title = it }, "App title", Modifier.firstFocus())
                AdButton("Save") { val t = (title ?: "").trim(); if (t.isEmpty()) toast(c, "⚠ Title cannot be empty.") else adminDo(c, scope, "✅ Saved", { tick++ }) { adSaveSetting("app_title", t) } }
                if (s.s("logo_url").isNotBlank()) AdRow("Logo", sub = "Change the logo on the website (upload is website only for now)", photo = s.s("logo_url"), letter = "Y")
            }
            adCard("🚪 Registration") {
                val open = s.s("registration_open") != "false"
                SettingRow(Sym.PersonAdd, "New sign-ups", if (open) "Open" else "Closed", switch = open) {
                    toggle("registration_open", !open, if (!open) "✅ Registration is now OPEN" else "🔒 Registration is now CLOSED")
                }
            }
            adCard("💬 Support chat", hint = "Lets people who can't sign in get help") {
                val l = s.s("support_chat_login_enabled") != "false"; val h = s.s("support_chat_home_enabled") != "false"
                SettingRow(Sym.Login, "On the sign-in screen", if (l) "On" else "Off", switch = l) { toggle("support_chat_login_enabled", !l, if (!l) "On" else "Off") }
                RowDivider(64.dp)
                SettingRow(Sym.Home, "In the main app", if (h) "On" else "Off", switch = h) { toggle("support_chat_home_enabled", !h, if (!h) "On" else "Off") }
            }
            adCard("📧 Email test", hint = "Check that the site's emails are arriving.") {
                AdField(testTo, { testTo = it }, "Send test to... (defaults to your email)")
                AdButton("Send test email") {
                    testOut = "Sending…"
                    scope.launch {
                        runCatching { Api.post("/api/admin/email-test", jo("to" to testTo.trim())) }.onSuccess { r ->
                            val cfg = r.optJSONObject("config")
                            testOut = if (r.b("delivered")) "✅ Sent to " + r.s("sent_to") + "\nFrom: " + (cfg?.s("from_address_used") ?: "") + "\nStatus: " + r.s("resend_status") + "\n\nIf it's not in the inbox, check the spam folder."
                            else "❌ Not delivered.\n" + (cfg?.let { "API key set: " + (if (it.b("resend_api_key_present")) "yes" else "NO") + "\nFrom-email configured: " + (if (it.b("resend_from_email_configured")) "yes" else "NO") } ?: r.toString().take(300))
                        }.onFailure { testOut = "❌ " + Api.friendly(it) }
                    }
                }
                testOut?.let { AdMono(it) }
            }
        }
    }
}

/** Ads (buildAdsPanel): create (title, link, email, interval, countdown, pages, photo/video), list with On/Off, Interval, Pages, Exempt, Delete. */
class AdAdsPage : PageScreen() {
    override val perfName = "AdAds"
    override fun equals(other: Any?) = other is AdAdsPage
    override fun hashCode() = 3502
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/admin/ads", tick)
        var title by remember { mutableStateOf("") }; var link by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }
        var interval by remember { mutableStateOf("60") }; var countdown by remember { mutableStateOf("5") }
        val pagesOpt = listOf("all" to "All pages", "home" to "Home only", "chat" to "Chat only", "shorts" to "Shorts only", "music" to "Music only", "home,shorts" to "Home + Shorts", "chat,home" to "Chat + Home")
        var page by remember { mutableIntStateOf(0) }; var pick by remember { mutableStateOf(false) }
        var media by remember { mutableStateOf<Uri?>(null) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { media = it }
        AdPanel("Ads", sub = "Ads shown on the site and in the app", res = res) { j ->
            adCard("➕ New ad") {
                AdField(title, { title = it }, "Ad title (optional)", Modifier.firstFocus()); AdField(link, { link = it }, "Link URL (optional)"); AdField(email, { email = it }, "Email address (optional)")
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { AdField(interval, { interval = it.filter(Char::isDigit) }, "Show every (minutes)", number = true) }
                    Box(Modifier.weight(1f)) { AdField(countdown, { countdown = it.filter(Char::isDigit) }, "Countdown (seconds)", number = true) }
                }
                SettingRow(Icons.Filled.Web, "Show on pages", null, value = pagesOpt[page].second) { pick = true }
                SettingRow(Sym.Image, "Image or video", if (media == null) "None chosen" else "Chosen ✓") { picker.launch("*/*") }
                AdButton("Create ad") {
                    if (title.isBlank() && media == null) { toast(c, "⚠ Add a title or image/video."); return@AdButton }
                    scope.launch {
                        runCatching {
                            val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
                                .addFormDataPart("title", title.trim()).addFormDataPart("link_url", link).addFormDataPart("email_url", email)
                                .addFormDataPart("interval_minutes", interval.ifBlank { "60" }).addFormDataPart("countdown_seconds", countdown.ifBlank { "5" })
                                .addFormDataPart("pages", pagesOpt[page].first).addFormDataPart("exempt_users", "[]")
                            media?.let { u ->
                                val type = c.contentResolver.getType(u) ?: "application/octet-stream"
                                val bytes = withContext(Dispatchers.IO) { c.contentResolver.openInputStream(u)!!.use { it.readBytes() } }
                                mb.addFormDataPart("media", if (type.startsWith("video")) "ad.mp4" else "ad.jpg", bytes.toRequestBody(type.toMediaType()))
                            }
                            val body = mb.build(); val buf = Buffer(); body.writeTo(buf)
                            Api.upload("/api/admin/ads", buf.readByteArray(), body.contentType().toString())
                        }.onSuccess { toast(c, "✅ Ad created!"); title = ""; link = ""; email = ""; interval = "60"; countdown = "5"; media = null; tick++ }
                            .onFailure { toast(c, "❌ " + Api.friendly(it)) }
                    }
                }
                if (pick) SettingsChoiceDialog("Show on pages", pagesOpt.map { it.second }, page, { page = it }, { pick = false })
            }
            val ads = j.arr("ads")
            item { SectionTitle("Ads (" + ads.size + ")") }
            if (ads.isEmpty()) item { AdHint("No ads yet") }
            items(ads, key = { it.s("id") }) { ad ->
                val id = ad.s("id"); val on = ad.b("active")
                AdRow(ad.s("title").ifBlank { "(no title)" }, sub = ad.optInt("interval_minutes", 60).toString() + "min · " + ad.optInt("countdown_seconds", 5) + "s countdown · " + (if (ad.s("pages") == "all" || ad.s("pages").isBlank()) "All pages" else ad.s("pages")),
                    sub2 = ad.s("link_url"), photo = if (ad.b("is_video")) null else ad.s("media_url"), icon = if (ad.s("media_url").isBlank() || ad.b("is_video")) Sym.Campaign else null,
                    tag = if (on) "🟢 On" to AdGreen else "⚪ Off" to Color.Gray) {
                    a.actions(ad.s("title").ifBlank { "Ad" },
                        (if (on) "⚪ Turn off" else "🟢 Turn on") to { adminDo(c, scope, if (on) "Off" else "On", { tick++ }) { Api.put("/api/admin/ads", jo("id" to id, "active" to !on)) } },
                        "⏱ Interval" to { a.ask("Ad interval", "Show every how many minutes?", "60", number = true) { m -> if (m.isNotBlank()) a.ask("Skip countdown", "Countdown seconds before Skip?", "5", number = true) { s2 ->
                            if (s2.isNotBlank()) adminDo(c, scope, "✅ Updated!", { tick++ }) { Api.put("/api/admin/ads", jo("id" to id, "interval_minutes" to (m.toIntOrNull() ?: 60), "countdown_seconds" to (s2.toIntOrNull() ?: 5))) } } } },
                        "📄 Pages" to { a.ask("Ad pages", "Pages (all / home / chat / shorts / music or comma-separated):", "all") { p -> if (p.isNotBlank()) adminDo(c, scope, "✅ Updated!", { tick++ }) { Api.put("/api/admin/ads", jo("id" to id, "pages" to p.trim())) } } },
                        "🚫 Exempt people" to { Nav.open(AdAdExemptPage(id)) },
                        "🗑 Delete" to { a.sure("Delete?", "Delete this ad?") { adminDo(c, scope, "🗑 Deleted.", { tick++ }) { Api.delete("/api/admin/ads?id=" + adEnc(id)) } } })
                }
            }
        }
        AdDialogs(a)
    }
}

/** adminEditExemptUsers: people who never see this one ad (PUT /api/admin/ads {id, exempt_users: JSON}). */
data class AdAdExemptPage(val adId: String) : PageScreen() {
    override val perfName = "AdAdExempt"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/admin/ads", tick)
        var q by remember { mutableStateOf("") }
        val found = adUserSearch(q)
        fun current(j: JSONObject?): MutableList<String> {
            val ad = j.arr("ads").firstOrNull { it.s("id") == adId } ?: return mutableListOf()
            return runCatching { org.json.JSONArray(ad.s("exempt_users").ifBlank { "[]" }).let { arr -> (0 until arr.length()).map { arr.optString(it) }.toMutableList() } }.getOrDefault(mutableListOf())
        }
        fun save(list: List<String>, ok: String) = adminDo(c, scope, ok, { tick++ }) { Api.put("/api/admin/ads", jo("id" to adId, "exempt_users" to org.json.JSONArray(list).toString())) }
        AdPanel("Exempt from this ad", res = res) { j ->
            val cur = current(j)
            item { AdminSearchField(q, { q = it }, "Search user by nickname...") }
            found?.forEach { u -> item(key = "f" + u.s("id")) { AdRow(u.s("nickname"), letter = u.s("nickname").ifBlank { "?" }, trail = "+ Exempt") { if (u.s("id") !in cur) save(cur + u.s("id"), "✅ @" + u.s("nickname") + " exempted") } } }
            item { SectionTitle("Exempt (" + cur.size + ")") }
            if (cur.isEmpty()) item { AdHint("Nobody is exempt from this ad.") }
            items(cur, key = { "e$it" }) { uid -> AdRow(uid, icon = Sym.Person, trail = "Remove") { save(cur - uid, "Removed") } }
        }
    }
}

/** Maintenance (buildMaintenancePanel): reset views, email verification, guest mode, maintenance mode + message. */
class AdMaintenancePage : PageScreen() {
    override val perfName = "AdMaintenance"
    override fun equals(other: Any?) = other is AdMaintenancePage
    override fun hashCode() = 3503
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        var tick by remember { mutableIntStateOf(0) }
        var ev by remember { mutableStateOf<JSONObject?>(null) }; var gm by remember { mutableStateOf<JSONObject?>(null) }; var mt by remember { mutableStateOf<JSONObject?>(null) }
        var msg by remember { mutableStateOf("") }; var resetOut by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(tick) {
            launch { ev = runCatching { Api.get("/api/admin/email-verify-toggle") }.getOrNull() }
            launch { gm = runCatching { Api.get("/api/admin/guest-mode") }.getOrNull() }
            launch { mt = runCatching { Api.get("/api/admin/maintenance") }.getOrNull()?.also { msg = it.s("message") } }
        }
        AdPanel("Maintenance", onRefresh = { tick++ }) { _ ->
            adCard("🔴 Maintenance mode", hint = "When ON, nobody but admins can sign in.") {
                val on = mt?.b("maintenance_mode") == true
                AdHint(if (mt == null) "Checking…" else if (on) "🔴 ON — Nobody can log in right now" else "🟢 OFF — Site is open normally", if (on) AdRed else AdGreen)
                AdField(msg, { msg = it }, "Message shown to users (e.g. Back in 10 minutes!)", lines = 2)
                AdPills("🔴 TURN ON" to { a.sure("Maintenance ON?", "Nobody but admins can sign in until you turn it off.") { adminDo(c, scope, "🔴 Maintenance ON", { tick++ }) { Api.post("/api/admin/maintenance", jo("enabled" to true, "message" to msg)) } } },
                    "🟢 TURN OFF" to { adminDo(c, scope, "🟢 Maintenance OFF", { tick++ }) { Api.post("/api/admin/maintenance", jo("enabled" to false, "message" to msg)) } })
            }
            adCard("✉️ Email verification") {
                val on = ev?.b("enabled") == true
                AdHint(if (ev == null) "Checking…" else if (on) "✅ VERIFICATION REQUIRED — Email links sent" else "⚡ NO VERIFICATION — Instant signup", if (on) AdGreen else Color.Gray)
                AdPills("✅ Require verification" to { adminDo(c, scope, "✅ Email Verification Required", { tick++ }) { Api.post("/api/admin/email-verify-toggle", jo("enabled" to true)) } },
                    "⚡ Skip verification" to { adminDo(c, scope, "⚡ Verification Skipped", { tick++ }) { Api.post("/api/admin/email-verify-toggle", jo("enabled" to false)) } })
            }
            adCard("👁️ Guest mode") {
                val on = gm?.b("enabled") == true
                AdHint(if (gm == null) "Checking…" else if (on) "👁️ GUEST MODE IS ON — Anyone can browse" else "🔒 LOGIN REQUIRED — Guests cannot access", if (on) AdBlue else Color(0xFF7C3AED))
                AdPills("👁️ Turn ON (allow guests)" to { adminDo(c, scope, "👁️ Guest Mode ON", { tick++ }) { Api.post("/api/admin/guest-mode", jo("enabled" to true)) } },
                    "🔒 Turn OFF" to { adminDo(c, scope, "🔒 Login Required", { tick++ }) { Api.post("/api/admin/guest-mode", jo("enabled" to false)) } })
            }
            adCard("🔁 Reset views", hint = "Sets every short and post view count back to 0.") {
                AdButton("Reset all views to 0", danger = true) {
                    a.sure("Reset views?", "Reset ALL short and post view counts to 0? This cannot be undone.") {
                        resetOut = "Resetting…"
                        scope.launch { runCatching { Api.post("/api/admin/reset-views", JSONObject()) }.onSuccess { r -> resetOut = if (r.optBoolean("ok", true)) "✓ Done — " + r.n("shorts_reset") + " shorts, " + r.n("posts_reset") + " posts reset to 0." else r.s("error").ifBlank { "Failed" } }.onFailure { resetOut = "Error: " + Api.friendly(it) } }
                    }
                }
                resetOut?.let { AdHint(it) }
            }
        }
        AdDialogs(a)
    }
}

/** Admin settings (inline 'admin-settings'): outside videos in Shorts on/off (POST /api/src-shorts). */
class AdAdminSettingsPage : PageScreen() {
    override val perfName = "AdAdminSettings"
    override fun equals(other: Any?) = other is AdAdminSettingsPage
    override fun hashCode() = 3504
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/src-shorts?switch=1", tick)
        AdPanel("Admin settings", res = res) { j ->
            item { AdHint("Admin roles are managed from Users (promote/demote).") }
            adCard("🎬 Shorts: outside videos", hint = "When OFF, no outside videos come in: the For You and Following tabs are hidden and Shorts opens on Uploads. Takes effect for everyone within about a minute.") {
                val on = j.optBoolean("enabled", true)
                AdHint(if (on) "🟢 ON — For You shows outside videos" else "🔴 OFF — Shorts shows only Uploads", if (on) AdGreen else AdRed)
                AdPills("🟢 ON" to { adminDo(c, scope, "🟢 Outside videos ON", { tick++ }) { val r = Api.post("/api/src-shorts", jo("enabled" to true)); if (r.has("ok") && !r.optBoolean("ok")) error(r.s("error").ifBlank { "Could not save" }) } },
                    "🔴 OFF" to { adminDo(c, scope, "🔴 Outside videos OFF — Shorts shows only Uploads", { tick++ }) { val r = Api.post("/api/src-shorts", jo("enabled" to false)); if (r.has("ok") && !r.optBoolean("ok")) error(r.s("error").ifBlank { "Could not save" }) } })
            }
        }
    }
}

/** Anti-spam (buildAntispamPanel): email verification switch + test, sign-ups per hour, welcome message. */
class AdAntispamPage : PageScreen() {
    override val perfName = "AdAntispam"
    override fun equals(other: Any?) = other is AdAntispamPage
    override fun hashCode() = 3505
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/settings", tick)
        var rate by remember { mutableStateOf<String?>(null) }; var welOn by remember { mutableStateOf<Boolean?>(null) }; var welMsg by remember { mutableStateOf<String?>(null) }
        var testOut by remember { mutableStateOf<String?>(null) }
        AdPanel("Anti-spam", sub = "Keep fake accounts out", res = res) { j ->
            val s = j.optJSONObject("settings") ?: JSONObject()
            if (rate == null && j.has("settings")) { rate = s.s("register_rate_max").ifBlank { "5" }; welOn = s.s("welcome_enabled") == "true"; welMsg = s.s("welcome_message") }
            val verifyOn = s.s("require_email_verify") == "true"
            adCard("✉️ Email verification", hint = "New accounts must click a link sent to their email before they can use the site. This is the only way to check an address is real.") {
                SettingRow(Sym.MarkEmailRead, "Require email verification", if (verifyOn) "On" else "Off", switch = verifyOn, modifier = Modifier.firstFocus()) {
                    scope.launch {
                        runCatching { Api.post("/api/admin/email-verify-toggle", jo("enabled" to !verifyOn)) }.onSuccess { r ->
                            if (r.has("ok") && !r.optBoolean("ok")) toast(c, "❌ " + r.s("error").ifBlank { "Failed" })
                            else { toast(c, if (!verifyOn) "✅ New accounts must verify" + (if (r.n("grandfathered") > 0) " · " + r.n("grandfathered") + " existing accounts kept their access" else "") else "Email verification off"); tick++ }
                        }.onFailure { toast(c, "❌ " + Api.friendly(it)) }
                    }
                }
                if (!verifyOn) AdHint("⚠️ Test that emails actually arrive before switching this on — if they don't, nobody will be able to register at all.", AdAmber)
                AdButton("Send test email") {
                    testOut = "Sending…"
                    scope.launch { runCatching { Api.post("/api/admin/email-test", JSONObject()) }.onSuccess { r -> testOut = if (r.optBoolean("ok")) "✅ Sent. Check your inbox (and spam). If it arrived, verification is safe to switch on." else "❌ " + r.s("error").ifBlank { "Failed" } + r.s("resend_status").let { if (it.isNotBlank()) " ($it)" else "" } }.onFailure { testOut = "❌ " + Api.friendly(it) } }
                }
                testOut?.let { AdHint(it) }
            }
            adCard("🚦 Sign-up limit", hint = "Most new accounts allowed from one address per hour") {
                AdField(rate ?: "", { rate = it.filter(Char::isDigit) }, "Max sign-ups per hour", number = true)
                AdButton("Save") { val v = ((rate ?: "5").toIntOrNull() ?: 5).coerceAtLeast(1).toString(); adminDo(c, scope, "✅ Saved", { tick++ }) { adSaveSetting("register_rate_max", v) } }
            }
            adCard("👋 Welcome message", hint = "Sent to every new member. {name} becomes their name.") {
                SettingRow(Icons.Filled.Waves, "Send a welcome message", null, switch = welOn == true) { welOn = !(welOn ?: false) }
                AdField(welMsg ?: "", { welMsg = it }, "Welcome {name}! Glad to have you here...", lines = 4)
                AdButton("Save welcome message") { adminDo(c, scope, "✅ Saved", { tick++ }) { adSaveSetting("welcome_enabled", if (welOn == true) "true" else "false"); adSaveSetting("welcome_message", welMsg ?: "") } }
            }
        }
    }
}

