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
// AD 2.1 — Content category (admin.js): Announcements, Email all users, YID PLUS AI, Email settings, Featured,
// Telegram (activity bot, private bot → email, email sending, channel session, bot connection, publishing bot, bridges).
// ═════════════════════════════════════════════════════════════════════════════

/** Announcements (buildAnnouncementsPanel + admin-control.js confirms). */
class AdAnnouncementsPage : PageScreen() {
    override val perfName = "AdAnnouncements"
    override fun equals(other: Any?) = other is AdAnnouncementsPage
    override fun hashCode() = 3401
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/admin/announcements", tick)
        var text by remember { mutableStateOf("") }
        AdPanel("Announcements", sub = "Pinned at the top for everyone", res = res) { j ->
            adCard("📌 New announcement") {
                AdField(text, { text = it }, "Type announcement...", Modifier.firstFocus(), lines = 3)
                AdButton("Post announcement") {
                    if (text.isBlank()) toast(c, "Type something first")
                    else a.sure("Publish?", "Publish this announcement for all users?\n\n" + text.trim(), danger = false) {
                        adminDo(c, scope, "Posted!", { text = ""; tick++ }) { Api.post("/api/admin/announcements", jo("text" to text.trim())) }
                    }
                }
            }
            val l = j.arr("announcements")
            item { SectionTitle("Pinned (" + l.size + ")") }
            if (l.isEmpty()) item { AdHint("No announcements yet") }
            items(l, key = { it.s("id") }) { x ->
                AdRow(x.s("text"), sub = x.s("created_by") + " · " + adWhen(x.s("created_at")), icon = Sym.PushPin, trail = "Remove") {
                    a.sure("Remove?", "Remove this announcement from the app?") { adminDo(c, scope, "Removed", { tick++ }) { Api.delete("/api/admin/announcements?id=" + adEnc(x.s("id"))) } }
                }
            }
        }
        AdDialogs(a)
    }
}

/** Email all users (sendEmailBlast): subject + message, confirm, POST /api/admin/email-blast. */
class AdEmailBlastPage : PageScreen() {
    override val perfName = "AdEmailBlast"
    override fun equals(other: Any?) = other is AdEmailBlastPage
    override fun hashCode() = 3402
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        var subj by remember { mutableStateOf("") }; var msg by remember { mutableStateOf("") }
        AdPanel("✉️ Email all users", sub = "One email to every member") { _ ->
            adCard(null) {
                AdField(subj, { subj = it }, "Subject", Modifier.firstFocus())
                AdField(msg, { msg = it }, "Email message...", lines = 5)
                AdButton("✉️ Send email to all", danger = true) {
                    if (subj.isBlank() || msg.isBlank()) toast(c, "⚠ Add a subject and message.")
                    else a.sure("Email everyone?", "Send this email to ALL users? This cannot be undone.") {
                        toast(c, "✉️ Sending…")
                        scope.launch {
                            runCatching { Api.post("/api/admin/email-blast", jo("subject" to subj.trim(), "message" to msg.trim())) }
                                .onSuccess { r -> toast(c, "✉️ Sent to " + r.n("sent") + " / " + r.n("total") + " users"); subj = ""; msg = "" }
                                .onFailure { toast(c, "❌ " + Api.friendly(it)) }
                        }
                    }
                }
            }
        }
        AdDialogs(a)
    }
}

/** YID PLUS AI (buildAIPanel): engine line, on/off, name, instructions, welcome, hourly limit; Save. */
class AdAiPage : PageScreen() {
    override val perfName = "AdAi"
    override fun equals(other: Any?) = other is AdAiPage
    override fun hashCode() = 3403
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        val res = adRes("/api/admin/ai-settings")
        var en by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }
        var instr by remember { mutableStateOf("") }; var welcome by remember { mutableStateOf("") }; var limit by remember { mutableStateOf("40") }
        var loaded by remember { mutableStateOf(false) }; var saving by remember { mutableStateOf(false) }
        LaunchedEffect(res.data) {
            val s = res.data?.optJSONObject("settings") ?: return@LaunchedEffect
            if (loaded) return@LaunchedEffect
            en = s.b("enabled"); name = s.s("name").ifBlank { "YID PLUS AI" }; instr = s.s("instructions"); welcome = s.s("welcome"); limit = s.optInt("hourly_limit", 40).toString(); loaded = true
        }
        AdPanel("YID PLUS AI", sub = "The assistant in everyone's chat list", res = res) { j ->
            if (j.has("ok") && !j.b("ok")) { item { AdHint(j.s("error").ifBlank { "Failed to load" }, AdRed) }; return@AdPanel }
            val prov = j.s("provider").ifBlank { "none" }
            item { AdHint(when (prov) { "claude", "cloudflare" -> "✅ Engine connected"; else -> "⚠️ No engine connected." }, if (prov == "none") AdAmber else AdGreen) }
            adCard(null) {
                SettingRow(Icons.Filled.SmartToy, "AI is on", "Off = it disappears from everyone's chat list and no one can use it. On = it comes back.", switch = en, modifier = Modifier.firstFocus()) { en = !en }
                AdField(name, { name = it }, "Name")
                AdField(instr, { instr = it }, "How the AI should behave (your instructions)", lines = 6)
                AdField(welcome, { welcome = it }, "Welcome message (shown before the first message)", lines = 2)
                AdField(limit, { limit = it.filter(Char::isDigit) }, "Messages per user per hour", number = true)
                AdButton(if (saving) "Saving…" else "💾 Save", enabled = !saving && loaded) {
                    saving = true
                    scope.launch {
                        runCatching { Api.post("/api/admin/ai-settings", jo("enabled" to en, "name" to name.trim(), "instructions" to instr, "welcome" to welcome, "hourly_limit" to ((limit.toIntOrNull() ?: 40).coerceIn(1, 1000)))) }
                            .onSuccess { r -> toast(c, if (r.optBoolean("ok", true)) "✅ Saved" else "❌ " + r.s("error").ifBlank { "Failed" }) }.onFailure { toast(c, "❌ " + Api.friendly(it)) }
                        saving = false
                    }
                }
            }
        }
    }
}

/** Saves one site setting like state.js saveSetting (PUT /api/settings {key, value}). */
suspend fun adSaveSetting(key: String, value: String) { Api.put("/api/settings", jo("key" to key, "value" to value)) }

/** Email settings (buildEmailTemplatesPanel): sender name, verification subject + intro. */
class AdEmailSettingsPage : PageScreen() {
    override val perfName = "AdEmailSettings"
    override fun equals(other: Any?) = other is AdEmailSettingsPage
    override fun hashCode() = 3404
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        val res = adRes("/api/settings")
        var from by remember { mutableStateOf("") }; var subj by remember { mutableStateOf("") }; var intro by remember { mutableStateOf("") }
        var loaded by remember { mutableStateOf(false) }
        LaunchedEffect(res.data) {
            val s = res.data?.optJSONObject("settings") ?: return@LaunchedEffect
            if (!loaded) { from = s.s("email_from_name"); subj = s.s("email_verify_subject"); intro = s.s("email_verify_intro"); loaded = true }
        }
        AdPanel("Email", sub = "What the site's emails say", res = res) { _ ->
            adCard("✉️ Email settings") {
                AdField(from, { from = it }, "From name (shown as the sender) — YID PLUS", Modifier.firstFocus())
                AdField(subj, { subj = it }, "Verification email — subject")
                AdField(intro, { intro = it }, "Verification email — intro text", lines = 3)
                AdButton("Save email templates", enabled = loaded) {
                    scope.launch {
                        runCatching { adSaveSetting("email_from_name", from); adSaveSetting("email_verify_subject", subj); adSaveSetting("email_verify_intro", intro) }
                            .onSuccess { toast(c, "✅ Email templates saved!") }.onFailure { toast(c, "❌ " + Api.friendly(it)) }
                    }
                }
                if (!loaded) AdHint("Waiting for the current values from the site…")
            }
        }
    }
}

/** Featured (buildFeaturedPanel): latest posts, ⭐ Feature / Unfeature. */
class AdFeaturedPage : PageScreen() {
    override val perfName = "AdFeatured"
    override fun equals(other: Any?) = other is AdFeaturedPage
    override fun hashCode() = 3405
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/admin/feature", tick)
        AdPanel("Featured", sub = "Featured posts go to the top of everyone's feed", res = res) { j ->
            if (j.has("ok") && !j.b("ok")) { item { AdHint(j.s("error").ifBlank { "Error" }, AdRed) }; return@AdPanel }
            val l = j.arr("posts")
            if (l.isEmpty()) adEmpty("No posts yet.", Sym.Star)
            items(l, key = { it.s("id") }) { p ->
                val f = p.b("is_featured")
                AdRow((if (f) "⭐ " else "") + p.s("caption").ifBlank { "(no caption)" }.take(60), sub = "@" + p.s("username").ifBlank { "?" } + " · " + adWhen(p.s("created_at")),
                    trail = if (f) "Unfeature" else "⭐ Feature") {
                    adminDo(c, scope, if (!f) "⭐ Featured!" else "Unfeatured", { tick++ }) { Api.post("/api/admin/feature", jo("post_id" to p.s("id"), "featured" to !f)) }
                }
            }
        }
    }
}

/** Telegram (buildTelegramPanel): every bot and bridge card of the site, same calls. */
class AdTelegramBotsPage : PageScreen() {
    override val perfName = "AdTelegramBots"
    override fun equals(other: Any?) = other is AdTelegramBotsPage
    override fun hashCode() = 3406
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        var tick by remember { mutableIntStateOf(0) }
        var abot by remember { mutableStateOf<JSONObject?>(null) }; var pbot by remember { mutableStateOf<JSONObject?>(null) }
        var hook by remember { mutableStateOf<JSONObject?>(null) }; var post by remember { mutableStateOf<JSONObject?>(null) }
        var bmail by remember { mutableStateOf<JSONObject?>(null) }; var bsend by remember { mutableStateOf<JSONObject?>(null) }
        var bridges by remember { mutableStateOf<JSONObject?>(null) }
        suspend fun g(p: String): JSONObject = runCatching { Api.get(p) }.getOrElse { JSONObject().put("ok", false).put("error", Api.friendly(it)) }
        LaunchedEffect(tick) {
            launch { abot = g("/api/admin/admin-bot-setup") }; launch { pbot = g("/api/admin/private-bot-setup") }
            launch { hook = g("/api/telegram/set-webhook") }; launch { post = g("/api/telegram/post-bot-setup") }
            launch { bmail = g("/api/admin/bot-email") }; launch { bsend = g("/api/admin/bot-email-sender") }
            launch { bridges = g("/api/admin/telegram-bridges") }
        }
        var aTok by remember { mutableStateOf("") }; var pTok by remember { mutableStateOf("") }
        val recips = remember { mutableStateListOf<String>() }
        var mailOn by remember { mutableStateOf(false) }; var addMail by remember { mutableStateOf("") }
        LaunchedEffect(bmail) { bmail?.let { b -> if (b.optBoolean("ok", false)) { mailOn = b.b("enabled"); recips.clear(); (0 until (b.optJSONArray("recipients")?.length() ?: 0)).forEach { recips.add(b.optJSONArray("recipients")!!.optString(it)) } } } }
        var testTo by remember { mutableStateOf("") }; var verifyMail by remember { mutableStateOf("") }
        var fromName by remember { mutableStateOf("") }; var fromMail by remember { mutableStateOf("") }
        var secret by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }; var pw by remember { mutableStateOf("") }
        var sessSay by remember { mutableStateOf<String?>(null) }
        var chatId by remember { mutableStateOf("") }; var roomId by remember { mutableStateOf("") }; var label by remember { mutableStateOf("") }
        var updates by remember { mutableStateOf<String?>(null) }
        fun sess(step: String, extra: JSONObject) {
            if (secret.isBlank()) { sessSay = "Enter the worker admin secret first."; return }
            extra.put("step", step).put("secret", secret.trim())
            scope.launch {
                sessSay = "Working…"
                runCatching { Api.post("/api/admin/tg-login", extra) }.onSuccess { res ->
                    val r = res.optJSONObject("result")
                    sessSay = when {
                        r?.b("logged_in") == true -> "✅ Logged in as " + r.s("as", "user").ifBlank { "the account" } + ". New posts should start flowing again shortly."
                        r?.b("sent") == true -> "✅ Code sent! Check your Telegram app (a message from \"Telegram\") — it usually is NOT an SMS. Enter it in step 2."
                        r?.b("needs_password") == true -> "🔒 Two-step is on — enter your password in step 3."
                        r?.optBoolean("ok") == true -> "✅ " + r.s("message", "next").ifBlank { "Done — continue to the next step." }
                        else -> "⚠️ Worker said: " + (r ?: res).toString().take(300)
                    }
                }.onFailure { sessSay = "⚠️ " + Api.friendly(it) }
            }
        }
        AdPanel("Telegram", sub = "Bots, email forwarding, bridges", onRefresh = { tick++ }) { _ ->
            adCard("🔔 Activity Bot (get all site activity)", hint = "1. In Telegram, message @BotFather → /newbot → get a token. 2. Paste it below → Save token. 3. Open the bot and send it /start. 4. Send test.") {
                val (t, col) = adStatusLine(abot); AdHint(t, col)
                AdField(aTok, { aTok = it }, "Paste bot token (12345:AA...)")
                AdPills("💾 Save token" to { if (aTok.isBlank()) toast(c, "Paste the token first") else adminDo(c, scope, "✅ Saved", { aTok = ""; tick++ }) { Api.post("/api/admin/admin-bot-setup", jo("action" to "save", "token" to aTok.trim())) } },
                    "Send test" to { adminDo(c, scope, "✅ Test sent") { Api.post("/api/admin/admin-bot-setup", jo("action" to "test")) } },
                    "Open bot ↗" to { abot?.s("bot")?.takeIf { it.isNotBlank() }?.let { openUrl(c, "https://t.me/$it") } ?: toast(c, "Save a token first") })
            }
            adCard("📧 Private bot → email", hint = "1. @BotFather → /newbot (or /token) → token. 2. Paste it and Save token. 3. Connect bot.") {
                val (t, col) = adStatusLine(pbot); AdHint(t, col)
                AdField(pTok, { pTok = it }, "Paste bot token (12345:AA...)")
                AdPills("💾 Save token" to { if (pTok.isBlank()) toast(c, "Paste the token first") else adminDo(c, scope, "✅ Saved", { pTok = ""; tick++ }) { Api.post("/api/admin/private-bot-setup", jo("action" to "save", "token" to pTok.trim())) } },
                    "🔌 Connect bot" to { adminDo(c, scope, "🔌 Connected", { tick++ }) { Api.post("/api/admin/private-bot-setup", JSONObject()) } })
                SettingRow(Icons.Filled.Email, "Turn on email forwarding", null, switch = mailOn) { mailOn = !mailOn }
                AdHint("Send to these email addresses:")
                if (recips.isEmpty()) AdHint("No recipients yet.")
                recips.toList().forEach { e -> AdRow(e, icon = Sym.AlternateEmail, trail = "✕") { recips.remove(e) } }
                AdField(addMail, { addMail = it }, "add an email…")
                AdPills("Add" to { val e = addMail.trim(); if (e.contains("@") && e !in recips) { recips.add(e); addMail = "" } else toast(c, "Enter an email address") },
                    "Save recipients" to { adminDo(c, scope, "✅ Saved", { tick++ }) { Api.post("/api/admin/bot-email", jo("enabled" to mailOn, "recipients" to recips.joinToString("\n"))) } },
                    "🔍 Diagnose" to { scope.launch { runCatching { Api.get("/api/admin/bot-email-diagnose") }.onSuccess { r -> a.sure("Diagnose", r.toString(2).take(1500), danger = false) {} }.onFailure { toast(c, "Could not run the diagnostic.") } } })
            }
            adCard("📨 Email sending (no daily limit)") {
                val b = bsend
                AdHint("Provider now: " + (b?.s("provider")?.ifBlank { "auto" } ?: "…") + b?.s("from").let { if (!it.isNullOrBlank()) " · From: $it" else "" })
                listOf("auto" to "Automatic (recommended)", "cloudflare" to "Verified addresses only", "resend" to "Backup sender only (100 a day)").forEach { (k, l) ->
                    SettingRow(Sym.Send, l, null, switch = (b?.s("provider")?.ifBlank { "auto" } ?: "auto") == k) {
                        adminDo(c, scope, "✅ Saved", { tick++ }) { Api.post("/api/admin/bot-email-sender", jo("action" to "provider", "provider" to k)) }
                    }
                }
                AdField(testTo, { testTo = it }, "Send a test to — you@gmail.com")
                AdPills("Send test" to { if (testTo.isBlank()) toast(c, "Enter an email") else adminDo(c, scope, "✅ Test sent") { Api.post("/api/admin/bot-email-sender", jo("action" to "test", "email" to testTo.trim())) } })
                AdField(verifyMail, { verifyMail = it }, "Verify another address — group@googlegroups.com")
                AdPills("Verify" to { if (verifyMail.isBlank()) toast(c, "Enter an email") else adminDo(c, scope, "📧 Verification email sent", { tick++ }) { Api.post("/api/admin/bot-email-sender", jo("action" to "verify", "email" to verifyMail.trim(), "resend" to false)) } })
                AdField(fromName, { fromName = it }, "Sender name — YID PLUS"); AdField(fromMail, { fromMail = it }, "Sender email — bot@yidplus.com")
                AdPills("Save sender" to { adminDo(c, scope, "✅ Saved", { tick++ }) { Api.post("/api/admin/bot-email-sender", jo("action" to "from", "email" to fromMail.trim(), "name" to fromName.trim())) } })
                b?.arr("recent")?.take(8)?.let { r -> if (r.isEmpty()) AdHint("Nothing sent yet.") else r.forEach { x -> AdRow(x.s("to", "email"), sub = x.s("status", "via"), trail = adWhen(x.s("at", "created_at"))) } }
            }
            adCard("📡 Channel session (login)", hint = "You'll need the worker's admin secret. Step 1: phone → code. Step 2: the code. Step 3: two-step password if asked.") {
                AdField(secret, { secret = it }, "Worker admin secret", password = true)
                AdPills("Check session status" to { sess("status", JSONObject()) })
                AdField(phone, { phone = it }, "+1XXXXXXXXXX", number = true)
                AdPills("Send me a code" to { sess("login", jo("phone" to phone.trim())) })
                AdField(code, { code = it }, "12345", number = true)
                AdPills("Confirm code" to { sess("code", jo("code" to code.trim())) })
                AdField(pw, { pw = it }, "Two-step password", password = true)
                AdPills("Submit password" to { sess("password", jo("password" to pw)) })
                sessSay?.let { AdHint(it) }
            }
            adCard("🔌 Bot connection", hint = "The site's bot must be connected here. Do this first — without it, nothing comes through.") {
                val (t, col) = adStatusLine(hook); AdHint(t, col)
                AdPills("🔌 Connect bot" to { adminDo(c, scope, "🔌 Connected", { tick++ }) { Api.post("/api/telegram/set-webhook", JSONObject()) } },
                    "Disconnect" to { a.sure("Disconnect?", "Disconnect the bot? Nothing comes through until you connect again.") { adminDo(c, scope, "Disconnected", { tick++ }) { Api.delete("/api/telegram/set-webhook") } } })
            }
            adCard("📤 Publishing bot", hint = "Telegram doesn't allow both at once. Reconnect when done.") {
                val (t, col) = adStatusLine(post); AdHint(t, col)
                AdPills("📤 Connect publishing bot" to { adminDo(c, scope, "📤 Connected", { tick++ }) { Api.post("/api/telegram/post-bot-setup", JSONObject()) } },
                    "Disconnect" to { adminDo(c, scope, "Disconnected", { tick++ }) { Api.delete("/api/telegram/post-bot-setup") } })
            }
            adCard("🤖 Telegram bridge", hint = "Link a Telegram group to a YID PLUS room. 1. Add @yidplus_bot to your Telegram group as admin. 2. Find the group chat ID (below). 3. Link it.") {
                AdField(chatId, { chatId = it }, "Telegram Chat ID (e.g. -1001234567890)"); AdField(roomId, { roomId = it }, "YID PLUS Room ID"); AdField(label, { label = it }, "Label (e.g. Main Group)")
                AdPills("Link group" to {
                    if (chatId.isBlank() || roomId.isBlank()) toast(c, "Enter the chat ID and room ID")
                    else adminDo(c, scope, "🔗 Linked!", { chatId = ""; roomId = ""; label = ""; tick++ }) { Api.post("/api/admin/telegram-bridges", jo("telegram_chat_id" to chatId.trim(), "room_id" to roomId.trim(), "label" to label.trim())) }
                }, "🔍 Find group ID" to {
                    scope.launch { updates = "Loading..."; runCatching { Api.get("/api/telegram/get-updates") }.onSuccess { r ->
                        val chats = (0 until (r.optJSONArray("updates")?.length() ?: 0)).mapNotNull { i -> val u = r.optJSONArray("updates")!!.optJSONObject(i); val ch = (u?.optJSONObject("message") ?: u?.optJSONObject("channel_post"))?.optJSONObject("chat"); ch?.let { it.s("title", "username").ifBlank { "Unknown" } + ": " + it.opt("id") } }
                        updates = if (chats.isEmpty()) "No recent messages found.\nSend a message in your Telegram group first." else chats.joinToString("\n")
                    }.onFailure { updates = "Error fetching updates" } }
                })
                updates?.let { AdMono(it) }
            }
            item { SectionTitle("🔗 Active bridges") }
            val bl = bridges.arr("bridges")
            if (bl.isEmpty()) item { AdHint("No bridges yet") }
            items(bl, key = { "br" + it.s("id") }) { b ->
                AdRow(b.s("label", "telegram_chat_id"), sub = b.s("telegram_chat_id") + " → " + b.s("room_name", "room_id"), icon = Sym.Link,
                    tag = if (b.b("active")) "● Active" to AdGreen else "○ Inactive" to AdAmber, trail = "Remove") {
                    a.sure("Remove?", "Remove this bridge?") { adminDo(c, scope, "Removed", { tick++ }) { Api.delete("/api/admin/telegram-bridges?id=" + adEnc(b.s("id"))) } }
                }
            }
        }
        AdDialogs(a)
    }
}
