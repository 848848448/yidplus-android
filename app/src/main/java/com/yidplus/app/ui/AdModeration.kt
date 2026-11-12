package com.yidplus.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yidplus.app.data.Api
import kotlinx.coroutines.launch
import org.json.JSONObject

// ═════════════════════════════════════════════════════════════════════════════
// AD 2.1 — Moderation category (admin.js + admin-control.js): Reports, Feedback, Support, Chats, Shorts, Statuses.
// ═════════════════════════════════════════════════════════════════════════════

/** Reports as the site has them now (admin-control.js buildReportsPanel): Open/Resolved/All, Resolve keeps the history,
 *  Warn, Block user, Add user note (owner). */
class AdReportsPage : PageScreen() {
    override val perfName = "AdReports"
    override fun equals(other: Any?) = other is AdReportsPage
    override fun hashCode() = 3301
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/reports", tick)
        var filter by remember { mutableStateOf("open") }
        AdPanel("Reports", sub = "Review without opening private chats", res = res) { j ->
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("open" to "Open", "resolved" to "Resolved", "all" to "All").forEachIndexed { i, (k, l) ->
                        AdminPill(l, filled = filter == k, modifier = if (i == 0) Modifier.firstFocus() else Modifier) { filter = k }
                    }
                }
            }
            val rows = j.arr("reports").filter { filter == "all" || (if (filter == "resolved") it.b("resolved") else !it.b("resolved")) }
            if (rows.isEmpty()) adEmpty("No matching reports.", Sym.Flag)
            items(rows, key = { it.s("id") }) { r ->
                val nick = r.s("reported_nick").ifBlank { "user" }
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        AdRow("@" + r.s("reported_nick", "reported_id").ifBlank { "User" }, sub = r.s("reason").ifBlank { "No reason given" },
                            sub2 = (if (r.b("resolved")) "Resolved" else "Open") + " · " + adWhen(r.s("created_at")) + r.s("reporter_nick").let { if (it.isNotBlank()) " · by @$it" else "" },
                            letter = nick, tag = if (r.b("resolved")) "Resolved" to AdGreen else "Open" to AdAmber) { adOpenUser(r.s("reported_id"), nick) }
                        val pills = mutableListOf<Pair<String, () -> Unit>>()
                        if (!r.b("resolved")) {
                            pills += "Resolve" to { a.sure("Resolve?", "Mark this report resolved? The report stays in the review history.", danger = false) {
                                adminDo(c, scope, "Report resolved", { tick++ }) { Api.put("/api/reports", jo("id" to r.s("id"), "resolved" to true)) } } }
                            pills += "Warn" to { a.ask("Warning to @$nick", "Warning to send to @$nick:", lines = 2) { m ->
                                if (m.isNotBlank()) a.sure("Send this warning?", m.trim(), danger = false) {
                                    adminDo(c, scope, "Warning sent") { Api.post("/api/admin/warnings", jo("user_id" to r.s("reported_id"), "message" to m.trim())) } } } }
                            pills += "Block user" to { a.sure("Ban @$nick?", "Ban @$nick? This will also block their device.") {
                                adminDo(c, scope, "🚫 @$nick banned!", { tick++ }) { Api.put("/api/admin/users", jo("id" to r.s("reported_id"), "blocked" to true)) } } }
                        }
                        if (adminIsOwner) pills += "Add user note" to { a.ask("Private note", "Private admin note for this user:", lines = 2) { n ->
                            if (n.isNotBlank()) adminDo(c, scope, "Note saved") { Api.post("/api/admin/notes", jo("user_id" to r.s("reported_id"), "note" to "Report " + r.s("id") + ": " + n.trim())) } } }
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(pills) { (t, f) -> AdminPill(t, color = if (t == "Block user") AdRed else MaterialTheme.colorScheme.primary, onClick = f) }
                        }
                    }
                }
            }
        }
        AdDialogs(a)
    }
}

/** Feedback (buildFeedbackPanel): bugs, suggestions, block appeals; Resolve, Delete. */
class AdFeedbackPage : PageScreen() {
    override val perfName = "AdFeedback"
    override fun equals(other: Any?) = other is AdFeedbackPage
    override fun hashCode() = 3302
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/feedback", tick)
        val list = res.data.arr("feedback")
        AdPanel("Feedback", sub = list.count { !it.b("resolved") }.toString() + " new · bug reports & ideas", res = res) { j ->
            val l = j.arr("feedback")
            if (l.isEmpty()) adEmpty("No feedback yet", Sym.Feedback)
            items(l, key = { it.s("id") }) { f ->
                val kind = when (f.s("type")) { "bug" -> "🐛 Bug"; "block_appeal" -> "🚫 Block Appeal"; else -> "💡 Suggestion" }
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column {
                        AdRow(f.s("nickname").ifBlank { "user" }, sub = f.s("text"), sub2 = f.s("device"), letter = f.s("nickname").ifBlank { "?" }, trail = adWhen(f.s("created_at")),
                            tag = kind to (if (f.s("type") == "bug") AdRed else AdBlue))
                        AdPills(*listOfNotNull(
                            if (!f.b("resolved")) "✓ Resolve" to { adminDo(c, scope, "✅ Marked as resolved.", { tick++ }) { Api.put("/api/feedback", jo("id" to f.s("id"), "resolved" to true)) } } else null,
                            "🗑 Delete" to { a.sure("Delete?", "Delete this feedback?") { adminDo(c, scope, "🗑 Deleted.", { tick++ }) { Api.delete("/api/feedback?id=" + adEnc(f.s("id"))) } } }
                        ).toTypedArray())
                        if (f.b("resolved")) AdHint("✓ Resolved", AdGreen)
                    }
                }
            }
        }
        AdDialogs(a)
    }
}

/** Support (buildSupportChatsPanel): New / Mine / All tickets, open a thread, Questions (owner). */
class AdSupportPage : PageScreen() {
    override val perfName = "AdSupport"
    override fun equals(other: Any?) = other is AdSupportPage
    override fun hashCode() = 3303
    @Composable override fun Content() {
        var tab by remember { mutableStateOf("new") }
        val res = adRes("/api/support-chats?tab=$tab")
        AdPanel("Support", sub = "Help chats from members and guests", res = res,
            actions = { if (adminIsOwner) IconBtn(Sym.Settings, "Questions", { Nav.open(AdSupportQuestionsPage()) }) }) { j ->
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("new" to "New", "mine" to "Mine", "all" to "All").forEachIndexed { i, (k, l) -> AdminPill(l, filled = tab == k, modifier = if (i == 0) Modifier.firstFocus() else Modifier) { tab = k } }
                    if (adminIsOwner) AdminPill("⚙️ Questions") { Nav.open(AdSupportQuestionsPage()) }
                }
            }
            val l = j.arr("chats")
            if (l.isEmpty()) adEmpty("No chats here.", Sym.SupportAgent)
            items(l, key = { it.s("id") }) { ch ->
                val who = if (ch.s("user_id").isNotBlank()) "@" + ch.s("user_nick").ifBlank { "user" } else ch.s("user_nick").ifBlank { "Guest" }
                val st = when { ch.s("status") == "closed" -> "Closed" to android.graphics.Color.GRAY; ch.s("claimed_by").isNotBlank() -> ("✋ " + ch.s("claimed_by_nick").ifBlank { "?" }) to 0; else -> "New" to 1 }
                AdRow(ch.s("question_label"), sub = who + ch.s("user_email").let { if (it.isNotBlank()) " · $it" else "" } + " · " + (if (ch.s("screen") == "login") "Sign-in screen" else "Main app"),
                    icon = Sym.SupportAgent, trail = adWhen(ch.s("updated_at")), tag = st.first to (if (st.second == 1) AdAmber else if (st.second == 0) AdGreen else androidx.compose.ui.graphics.Color.Gray)) {
                    Nav.open(AdSupportThreadPage(ch.s("id")))
                }
            }
        }
    }
}

/** One support ticket (_loadSupportChatThread): user info, Claim, Close, the thread, Reply. */
data class AdSupportThreadPage(val chatId: String) : PageScreen() {
    override val perfName = "AdSupportThread"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/support-chats?chat_id=" + adEnc(chatId) + "&admin=1", tick)
        var reply by remember { mutableStateOf("") }
        AdPanel("Support chat", res = res) { j ->
            val ch = j.optJSONObject("chat") ?: JSONObject()
            item {
                if (ch.s("user_id").isNotBlank()) AdRow("🔍 View full user info & set password", icon = Sym.Person) { adOpenUser(ch.s("user_id"), ch.s("user_nick")) }
                else AdHint("Not signed in — contact: " + ch.s("user_email").ifBlank { "no email given" } + ch.s("user_nick").let { if (it.isNotBlank()) " ($it)" else "" })
            }
            if (ch.s("status") != "closed") item {
                AdPills(*listOfNotNull(
                    if (ch.s("claimed_by").isBlank()) "✋ Claim this" to { adminDo(c, scope, "✋ Claimed!", { tick++ }) { Api.post("/api/support-chats", jo("chat_id" to chatId, "claim" to true)) } } else null,
                    "✅ Close ticket" to { adminDo(c, scope, "✅ Ticket closed.", { tick++ }) { Api.post("/api/support-chats", jo("chat_id" to chatId, "close" to true)) } }
                ).toTypedArray())
            }
            val msgs = j.arr("messages")
            if (msgs.isEmpty()) item { AdHint("No messages yet.") }
            items(msgs, key = null) { m ->
                val admin = m.s("sender_type") == "admin"
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = if (admin) Arrangement.End else Arrangement.Start) {
                    Surface(shape = RoundedCornerShape(12.dp), color = if (admin) AdGreen.copy(alpha = .14f) else MaterialTheme.colorScheme.surface, modifier = Modifier.widthIn(max = 320.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            if (admin) Text(m.s("sender_nick").ifBlank { "admin" }, style = MaterialTheme.typography.labelMedium, color = AdGreen, fontWeight = FontWeight.Bold)
                            if (m.s("text").isNotBlank()) Text(m.s("text"), style = MaterialTheme.typography.bodyMedium.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content))
                            if (m.s("media_url").isNotBlank()) AsyncImage(Api.abs(m.s("media_url")), null, Modifier.padding(top = 6.dp).sizeIn(maxWidth = 240.dp, maxHeight = 240.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
                            Text(adWhen(m.s("created_at")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (ch.s("status") != "closed") item {
                AdField(reply, { reply = it }, "Reply...", lines = 2)
                AdButton("Send reply") {
                    if (reply.isBlank()) toast(c, "⚠️ Write a reply first.")
                    else adminDo(c, scope, "Sent", { reply = ""; tick++ }) { Api.post("/api/support-chats", jo("chat_id" to chatId, "text" to reply.trim(), "admin" to true)) }
                }
            }
        }
    }
}

private val sqActions = listOf("admin_message" to "Sends a message to admins", "auto_resend" to "Auto-resend verification email", "self_block" to "Let user block themselves from a feature", "free_text" to "Free text (something else)")

/** Support questions (openManageSupportQuestions): per screen (Sign-in / Main app), add, delete. */
class AdSupportQuestionsPage : PageScreen() {
    override val perfName = "AdSupportQuestions"
    override fun equals(other: Any?) = other is AdSupportQuestionsPage
    override fun hashCode() = 3304
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var screen by remember { mutableStateOf("login") }
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/support-questions?screen=$screen", tick)
        var label by remember { mutableStateOf("") }
        var act by remember { mutableIntStateOf(0) }
        var pick by remember { mutableStateOf(false) }
        AdPanel("Support questions", sub = "The choices people see when they ask for help", res = res) { j ->
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminPill("Sign-in screen", filled = screen == "login", modifier = Modifier.firstFocus()) { screen = "login" }
                    AdminPill("Main app", filled = screen == "home") { screen = "home" }
                }
            }
            val qs = j.arr("questions")
            if (qs.isEmpty()) item { AdHint("No questions yet.") }
            items(qs, key = { it.s("id") }) { q ->
                AdRow(q.s("label"), sub = "(" + q.s("action_type") + ")", icon = Sym.HelpOutline, trail = "Delete") {
                    adminDo(c, scope, "Deleted", { tick++ }) { Api.delete("/api/support-questions?id=" + adEnc(q.s("id"))) }
                }
            }
            adCard("Add a question") {
                AdField(label, { label = it }, "Question text (in English)")
                SettingRow(Sym.Tune, "What it does", null, value = sqActions[act].second) { pick = true }
                AdButton("Add question") {
                    if (label.isBlank()) toast(c, "⚠️ Enter the question text.")
                    else adminDo(c, scope, "✅ Question added!", { label = ""; tick++ }) { Api.post("/api/support-questions", jo("screen" to screen, "label" to label.trim(), "action_type" to sqActions[act].first)) }
                }
                if (pick) SettingsChoiceDialog("What it does", sqActions.map { it.second }, act, { act = it }, { pick = false })
            }
        }
    }
}

/** Chats (buildChatWatchPanel): every chat, filter All/Private/Groups/Channels, search, Read, Hide/Unhide, Delete. */
class AdChatsPage : PageScreen() {
    override val perfName = "AdChats"
    override fun equals(other: Any?) = other is AdChatsPage
    override fun hashCode() = 3305
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/admin/rooms", tick)
        var hidden by remember { mutableStateOf<Set<String>>(emptySet()) }
        LaunchedEffect(tick) { runCatching { Api.get("/api/admin/room-control") }.onSuccess { j -> hidden = (0 until (j.optJSONArray("disabled")?.length() ?: 0)).map { j.optJSONArray("disabled")!!.optString(it) }.toSet() } }
        var filter by remember { mutableStateOf("all") }
        var q by remember { mutableStateOf("") }
        AdPanel("Chats", sub = "All chats (God Mode)", res = res) { j ->
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("all" to "All", "private" to "🔒 Private", "group" to "👥 Groups", "channel" to "📡 Channels")) { (k, l) -> AdminPill(l, filled = filter == k) { filter = k } }
                }
            }
            item { AdminSearchField(q, { q = it }, "Search names…") }
            val rooms = j.arr("rooms").filter { r -> (filter == "all" || r.s("type") == filter) && (q.isBlank() || r.s("nick", "name").lowercase().contains(q.trim().lowercase())) }
            item { AdHint(rooms.size.toString() + " " + if (filter == "all") "chats" else filter + "s") }
            if (rooms.isEmpty()) adEmpty("No chats here", Sym.Forum)
            items(rooms, key = { it.s("id") }) { r ->
                val off = r.s("id") in hidden
                val name = r.s("nick", "name").ifBlank { if (r.s("type") == "private") "Private chat" else "Chat" }
                val icon = r.s("emoji").ifBlank { when (r.s("type")) { "private" -> "🔒"; "channel" -> "📡"; else -> "👥" } }
                AdRow("$icon  $name" + if (r.s("type") != "private") " (" + r.n("members") + ")" else "", sub = r.s("preview"), tag = if (off) "HIDDEN" to AdRed else null) {
                    a.actions(name, "👁 Read" to { Nav.open(AdRoomReadPage(r.s("id"), name)) },
                        (if (off) "↩ Unhide" else "🚫 Hide") to {
                            val go = { adminDo(c, scope, if (off) "↩ Unhidden" else "🚫 Hidden", { tick++ }) { Api.post("/api/admin/room-control", jo("room_id" to r.s("id"), "action" to if (off) "unhide" else "hide")) } }
                            if (!off) a.sure("Hide?", "Hide this group/channel? Regular users won't see it. You can unhide anytime.", danger = false) { go() } else go()
                        },
                        "🗑 Delete" to { a.sure("Delete?", "PERMANENTLY delete \"$name\"? All its messages will be gone. This cannot be undone.") {
                            a.sure("Are you sure?", "Are you absolutely sure? This is permanent.") { adminDo(c, scope, "🗑 Deleted", { tick++ }) { Api.delete("/api/admin/room-control?room_id=" + adEnc(r.s("id"))) } } } })
                }
            }
        }
        AdDialogs(a)
    }
}

/** adminViewRoom: read a chat's messages (GET /api/chat?room_id=), photos shown. */
data class AdRoomReadPage(val roomId: String, val name: String) : PageScreen() {
    override val perfName = "AdRoomRead"
    @Composable override fun Content() {
        val res = adRes("/api/chat?room_id=" + adEnc(roomId))
        AdPanel(name, sub = "Read only", res = res) { j ->
            val msgs = j.arr("messages")
            if (msgs.isEmpty()) adEmpty("No messages in this chat", Sym.Forum)
            items(msgs, key = null) { m ->
                val k = m.s("media_key").lowercase()
                val body = when {
                    m.s("type") == "text" || m.s("type").isBlank() -> m.s("text")
                    m.s("type") == "poll" -> "📊 Poll"; m.s("type") == "voice" -> "🎤 Voice note"
                    Regex("\\.(mp4|webm|mov)$").containsMatchIn(k) -> "🎬 Video"
                    Regex("\\.(jpg|jpeg|png|gif|webp)$").containsMatchIn(k) -> "🖼️ Photo"
                    k.isNotBlank() -> "📎 File"; else -> m.s("text").ifBlank { "[" + m.s("type").ifBlank { "message" } + "]" }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(m.s("sender_nick").ifBlank { "?" } + " · " + adWhen(m.s("created_at")), style = MaterialTheme.typography.labelMedium, color = AdGreen, fontWeight = FontWeight.Bold)
                    Text(body, style = MaterialTheme.typography.bodyMedium.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content))
                    if (Regex("\\.(jpg|jpeg|png|gif|webp)$").containsMatchIn(k)) AsyncImage(Api.abs("/api/media/" + adEnc(m.s("media_key"))), null,
                        Modifier.padding(top = 4.dp).sizeIn(maxWidth = 220.dp, maxHeight = 220.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}

/** Shorts (buildShortsModPanel): every upload with caption, poster, likes; Delete. */
class AdShortsModPage : PageScreen() {
    override val perfName = "AdShortsMod"
    override fun equals(other: Any?) = other is AdShortsModPage
    override fun hashCode() = 3306
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/shorts", tick)
        AdPanel("Shorts", sub = "Video uploads moderation", res = res) { j ->
            val l = j.arr("shorts")
            if (l.isEmpty()) adEmpty("No shorts uploaded yet", Sym.Movie)
            items(l, key = { it.s("id") }) { s ->
                AdRow(s.s("caption").ifBlank { "(no caption)" }, sub = s.s("nick") + " · ❤️ " + adminN(s.n("likes")) + " · " + adWhen(s.s("created_at")), icon = Sym.Movie, trail = "🗑") {
                    a.sure("Delete?", "Delete this short?") { adminDo(c, scope, "🗑 Deleted.", { tick++ }) { Api.delete("/api/shorts?id=" + adEnc(s.s("id"))) } }
                }
            }
        }
        AdDialogs(a)
    }
}

/** Statuses (buildStatusesModPanel): active statuses (24 h) with preview, views, privacy; Delete for everyone. */
class AdStatusesModPage : PageScreen() {
    override val perfName = "AdStatusesMod"
    override fun equals(other: Any?) = other is AdStatusesModPage
    override fun hashCode() = 3307
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/admin/statuses", tick)
        AdPanel("Statuses", res = res) { j ->
            val l = j.arr("statuses")
            if (l.isEmpty()) { adEmpty("No active statuses right now.", Sym.Star); return@AdPanel }
            item { AdHint(l.size.toString() + " active status" + (if (l.size == 1) "" else "es") + " (last 24h). Deleting removes it for everyone, including the person who posted it.") }
            items(l, key = { it.s("id") }) { s ->
                val nick = s.s("nickname").ifBlank { "User" }
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column {
                        AdRow(nick + if (s.b("verified")) " ✔️" else "", sub = adWhen(s.s("created_at")) + " · 👁 " + adminN(s.n("views")) + s.s("privacy").let { if (it.isNotBlank() && it != "public") " · 🔒 $it" else "" }, letter = nick)
                        if (s.s("type") == "text" || s.s("media_url").isBlank()) AdHint(s.s("text").ifBlank { "(text)" }, MaterialTheme.colorScheme.onSurface)
                        else if (s.s("type") == "video") AdHint("🎬 Video" + s.s("text").let { if (it.isNotBlank()) " — $it" else "" }, MaterialTheme.colorScheme.onSurface)
                        else AsyncImage(Api.abs(s.s("media_url")), null, Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        AdPills("🗑 Delete for everyone" to { a.sure("Delete?", "Delete @$nick's status for everyone? This cannot be undone.") {
                            scope.launch { runCatching { Api.put("/api/statuses", jo("action" to "delete", "id" to s.s("id"))) }.onSuccess { r -> if (r.optBoolean("ok", true)) { toast(c, "🗑 Status deleted."); tick++ } else toast(c, r.s("error").ifBlank { "Failed" }) }.onFailure { toast(c, "Failed to delete") } } } })
                    }
                }
            }
        }
        AdDialogs(a)
    }
}


/** Music (site music-mod is a static sample with no data): the real library from GET /api/music, remove = DELETE /api/music?id= (music.js onRequestDelete, admins allowed, audit-logged). */
class AdMusicModPage : PageScreen() {
    override val perfName = "AdMusicMod"
    override fun equals(other: Any?) = other is AdMusicModPage
    override fun hashCode() = 3308
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/music", tick)
        var q by remember { mutableStateOf("") }
        AdPanel("🎵 Music library", sub = "Remove any track", res = res) { j ->
            item { AdminSearchField(q, { q = it }, "Search title, artist or uploader…") }
            val all = j.arr("tracks")
            val l = all.filter { t -> q.isBlank() || (t.s("title") + " " + t.s("artist") + " " + t.s("owner_nick")).contains(q.trim(), true) }
            item { AdHint(all.size.toString() + " tracks" + if (q.isNotBlank()) " · " + l.size + " match" else "") }
            if (l.isEmpty()) adEmpty("No tracks", Sym.MusicNote)
            items(l, key = { it.s("id") }) { t ->
                AdRow(t.s("title").ifBlank { "(untitled)" }, sub = listOf(t.s("artist"), "@" + t.s("owner_nick").ifBlank { "?" }).filter { it.isNotBlank() }.joinToString(" · "),
                    sub2 = (if (t.s("video_url").isNotBlank()) "🎬 video · " else "") + adminN(t.n("plays")) + " plays · " + adWhen(t.s("created_at")),
                    photo = t.s("cover_url"), letter = t.s("title").ifBlank { "♪" }, trail = "🗑") {
                    a.sure("Remove track?", "Remove \u201C" + t.s("title") + "\u201D from YID PLUS? The audio and cover are deleted too.") {
                        adminDo(c, scope, "🗑 Track removed.", { tick++ }) { Api.delete("/api/music?id=" + adEnc(t.s("id"))) }
                    }
                }
            }
        }
        AdDialogs(a)
    }
}
