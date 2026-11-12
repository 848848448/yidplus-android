package com.yidplus.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yidplus.app.data.Api
import com.yidplus.app.data.Fixture
import com.yidplus.app.data.Session
import com.yidplus.app.data.objects
import com.yidplus.app.data.str
import com.yidplus.app.data.strOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/* 2.1 ST: Help & Support, the site's guided support (state.js SUPPORT_FLOW / openGuidedSupport) and its live support chat
   (_gsOpenLiveChat / _gsSendLive). Same calls: GET /api/support-chats?self=1, GET /api/support-chats?chat_id=,
   POST /api/support-chats {screen:'guided',question_label,text} | {chat_id,text} | multipart chat_id,text,file,
   POST /api/self-block {feature}, POST /api/auth/request-password-reset {email}, POST /api/auth/send-verification {} | {email}. */

private class StOpt(val label: String, val next: String? = null, val action: String? = null, val msg: String? = null, val feature: String? = null)
private class StNode(val bot: List<String>, val options: List<StOpt>)

private val stFeature = mapOf("shorts" to "Shorts", "statuses" to "Status", "music" to "Music", "chat" to "Chat", "channels" to "Channels")

private val stFlow = mapOf(
    "start" to StNode(listOf("Hi! 👋 I'm here to help.", "What do you need help with?"), listOf(
        StOpt("🔑 I can't sign in", next = "signin"), StOpt("💬 A problem with Chat", next = "chat"), StOpt("🎬 Shorts / Music / Status", next = "media"),
        StOpt("👤 My account or profile", next = "account"), StOpt("✍️ Something else", action = "free_text", msg = "Something else"),
        StOpt("💬 Message a person", action = "live_chat"))),
    "signin" to StNode(listOf("No problem — let's get you back in.", "What's happening?"), listOf(
        StOpt("I forgot my password", action = "reset_password", msg = "Forgot password"),
        StOpt("I didn't get my verification email", action = "resend_verification", msg = "Resend verification"),
        StOpt("My account is blocked", action = "free_text", msg = "Account blocked"), StOpt("⬅️ Back", next = "start"))),
    "chat" to StNode(listOf("Let's sort out the chat issue."), listOf(
        StOpt("Messages won't send", action = "free_text", msg = "Messages won't send"), StOpt("Block myself from Chat", action = "self_block", feature = "chat"),
        StOpt("Someone is bothering me", action = "free_text", msg = "Someone bothering me in chat"), StOpt("⬅️ Back", next = "start"))),
    "media" to StNode(listOf("Which one, and what's wrong?"), listOf(
        StOpt("Block myself from Shorts", action = "self_block", feature = "shorts"), StOpt("Block myself from Music", action = "self_block", feature = "music"),
        StOpt("Block myself from Status", action = "self_block", feature = "statuses"), StOpt("Something isn't loading", action = "free_text", msg = "Media not loading"),
        StOpt("⬅️ Back", next = "start"))),
    "account" to StNode(listOf("Happy to help with your account."), listOf(
        StOpt("Change my password", action = "reset_password", msg = "Change password"), StOpt("A privacy question", action = "free_text", msg = "Privacy question"),
        StOpt("Report a bug", action = "free_text", msg = "Bug report"), StOpt("⬅️ Back", next = "start"))),
)

private class StLine(val mine: Boolean, val text: String, val who: String = "", val time: String? = null, val media: String? = null)

/** Help & Support (Settings → Help & Support, and "Need help?" on the sign-in screen). */
class StSupportPage(val from: String = "settings") : PageScreen() {
    override val perfName: String get() = "Support"
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        val lines = remember { mutableStateListOf<StLine>() }
        var options by remember { mutableStateOf<List<StOpt>>(emptyList()) }
        // what the composer is for: null = hidden, "__email__" = asking an email, "__live__" = live chat, else a free-text topic
        var composer by remember { mutableStateOf<String?>(null) }
        var afterEmail by remember { mutableStateOf<((String) -> Unit)?>(null) }
        var liveId by remember { mutableStateOf<String?>(null) }
        var liveStatus by remember { mutableStateOf("") }
        var liveCount by remember { mutableStateOf(-1) }
        var resume by remember { mutableStateOf<String?>(null) }
        var text by remember { mutableStateOf("") }
        var sending by remember { mutableStateOf(false) }
        val list = rememberLazyListState()
        fun bot(vararg t: String) { t.forEach { lines.add(StLine(false, it, "YID PLUS")) } }
        fun goTo(k: String) { val n = stFlow[k] ?: return; bot(*n.bot.toTypedArray()); options = n.options; composer = null }
        fun askEmail(then: (String) -> Unit) { bot("What's your email address?"); options = emptyList(); afterEmail = then; composer = "__email__" }
        suspend fun loadLive(id: String) {
            runCatching { Api.get("/api/support-chats?chat_id=" + Uri.encode(id)) }.onSuccess { j ->
                val chat = j.optJSONObject("chat")
                liveStatus = when (chat?.str("status")) { "closed" -> "Resolved"; "new" -> "Waiting for the team"; null -> ""; else -> "The team is here" }
                val msgs = j.optJSONArray("messages").objects()
                if (msgs.size != liveCount) {
                    liveCount = msgs.size
                    lines.removeAll { it.who == "live" || (it.mine && it.time != null) || it.who == "team" }
                    msgs.forEach { m ->
                        val mine = m.str("sender_type") == "user"
                        lines.add(StLine(mine, m.str("text"), if (mine) "live" else "team", m.strOrNull("created_at"), Api.abs(m.strOrNull("media_url"))))
                    }
                }
            }
        }
        fun openLive(id: String?) {
            options = emptyList()
            if (id == null) {
                bot("Tell me what you need and a person from the YID PLUS team will answer here.")
                composer = "Support"; return
            }
            liveId = id; composer = "__live__"
            scope.launch { loadLive(id) }
        }
        fun pick(o: StOpt) {
            options = emptyList()
            lines.add(StLine(true, o.label))
            when {
                o.next != null -> goTo(o.next)
                o.action == "live_chat" -> openLive(null)
                o.action == "reset_password" -> {
                    bot("You can reset your password from the sign-in screen — tap \"Forgot password?\" and we'll email you a link.", "Want me to send it now?")
                    options = listOf(StOpt("📧 Email me a reset link", action = "do_reset"), StOpt("⬅️ Back to start", next = "start"))
                }
                o.action == "do_reset" -> askEmail { email ->
                    scope.launch {
                        runCatching { Api.post("/api/auth/request-password-reset", JSONObject().put("email", email)) }
                            .onSuccess { bot("✅ If that email has an account, a reset link is on its way. Check your inbox (and spam).") }
                            .onFailure { bot("❌ " + Api.friendly(it)) }
                        options = listOf(StOpt("⬅️ Back to start", next = "start"))
                    }
                }
                o.action == "resend_verification" -> if (Session.me != null) scope.launch {
                    runCatching { Api.post("/api/auth/send-verification", JSONObject()) }
                        .onSuccess { bot("✅ Verification email sent! Check your inbox.") }.onFailure { bot("❌ " + Api.friendly(it)) }
                    options = listOf(StOpt("⬅️ Back to start", next = "start"))
                } else askEmail { email ->
                    scope.launch {
                        runCatching { Api.post("/api/auth/send-verification", JSONObject().put("email", email)) }
                            .onSuccess { bot("✅ If that email is registered, a new verification link is on its way.") }.onFailure { bot("❌ " + Api.friendly(it)) }
                        options = listOf(StOpt("⬅️ Back to start", next = "start"))
                    }
                }
                o.action == "self_block" -> {
                    val f = stFeature[o.feature] ?: o.feature.orEmpty()
                    bot("This will block YOU from $f right away.", "Only an admin can undo it. Are you sure?")
                    options = listOf(StOpt("🚫 Yes, block me", action = "do_self_block", feature = o.feature), StOpt("No, cancel", next = "start"))
                }
                o.action == "do_self_block" -> scope.launch {
                    val f = stFeature[o.feature] ?: o.feature.orEmpty()
                    if (Session.me == null) { bot("Please sign in first."); options = listOf(StOpt("⬅️ Back to start", next = "start")); return@launch }
                    runCatching { Api.post("/api/self-block", JSONObject().put("feature", o.feature)) }
                        .onSuccess { bot("✅ Done. You're now blocked from $f. Ask an admin to undo it when you're ready.") }.onFailure { bot("❌ " + Api.friendly(it)) }
                    options = listOf(StOpt("⬅️ Back to start", next = "start"))
                }
                o.action == "free_text" -> { bot("Please describe the problem and send it. A person from the team will answer here."); composer = o.msg ?: "Support" }
                o.action == "resume" -> openLive(resume)
            }
        }
        LaunchedEffect(Unit) {
            goTo("start")
            if (Session.me != null) runCatching { Api.get("/api/support-chats?self=1") }.onSuccess { j ->
                val chat = j.optJSONObject("chat")
                if (chat != null && chat.str("id").isNotBlank()) { resume = chat.str("id"); options = listOf(StOpt("💬 Continue your conversation", action = "resume")) + options }
            }
        }
        LaunchedEffect(liveId) { val id = liveId ?: return@LaunchedEffect; while (true) { delay(5000); loadLive(id) } }
        // Keep the newest bot line at the top of the view so the question and its answers show together (480x640 has little room).
        LaunchedEffect(lines.size, options.size) {
            if (lines.isEmpty()) return@LaunchedEffect
            val lastBot = lines.indexOfLast { !it.mine }.coerceAtLeast(0)
            if (options.isNotEmpty() && composer == null) list.animateScrollToItem((lastBot - 1).coerceAtLeast(0)) else list.animateScrollToItem(lines.size - 1 + (if (options.isEmpty()) 0 else 1))
        }
        fun submit(file: Uri? = null) {
            val v = text.trim()
            val mode = composer ?: return
            if (v.isEmpty() && file == null) { toast(c, "Write a message first"); return }
            if (mode == "__email__") {
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(v).matches()) { toast(c, "Enter your email."); return }
                lines.add(StLine(true, v)); text = ""; composer = null; afterEmail?.invoke(v); afterEmail = null; return
            }
            sending = true
            scope.launch {
                if (mode == "__live__") {
                    val id = liveId ?: return@launch
                    runCatching { if (file != null) stSupportUpload(c, id, v, file) else Api.post("/api/support-chats", JSONObject().put("chat_id", id).put("text", v)) }
                        .onSuccess { text = ""; lines.add(StLine(true, if (file != null) "📷 Photo" + (if (v.isNotBlank()) " $v" else "") else v, "live", "now")); loadLive(id) }
                        .onFailure { toast(c, Api.friendly(it)) }
                } else {
                    val body = JSONObject().put("screen", "guided").put("question_label", mode).put("text", v)
                    runCatching { Api.post("/api/support-chats", body) }
                        .onSuccess { r -> text = ""; val id = r.strOrNull("chat_id"); if (id != null && !Fixture.on) openLive(id) else { lines.add(StLine(true, v)); bot("✅ Got it — your message is with our team. A person will answer here."); composer = null } }
                        .onFailure { bot("❌ " + Api.friendly(it)) }
                }
                sending = false
            }
        }
        val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) submit(u) }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
            TopBar("Help & Support", onBack = { Nav.back() }, sub = if (liveId != null) liveStatus.ifBlank { "Live chat with the team" } else "We're here for you",
                lead = { Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)), contentAlignment = Alignment.Center) { Icon(Sym.SupportAgent, null, tint = Color.White) } })
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(lines.size) { i -> StBubble(lines[i]) }
                if (options.isNotEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        options.forEachIndexed { k, o ->
                            Text(o.label, Modifier.fillMaxWidth().heightIn(min = 40.dp).then(if (k == 0) Modifier.firstFocus(always = true) else Modifier).clip(RoundedCornerShape(22.dp))
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp)).tap(RoundedCornerShape(22.dp)) { pick(o) }
                                .padding(horizontal = 14.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (composer != null) Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (composer == "__live__") IconBtn(Sym.AttachFile, "Attach a photo", { pickPhoto.launch("image/*") })
                    OutlinedTextField(text, { text = it }, Modifier.weight(1f).firstFocus(), placeholder = { Text(if (composer == "__email__") "Your email..." else "Type your message...") },
                        maxLines = 4, shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = if (composer == "__email__") KeyboardType.Email else KeyboardType.Text, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() }))
                    Box(Modifier.padding(start = 6.dp).size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).tap(CircleShape, enabled = !sending) { submit() },
                        contentAlignment = Alignment.Center) { Icon(Sym.Send, "Send", tint = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun StBubble(l: StLine) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (l.mine) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp, 16.dp, if (l.mine) 4.dp else 16.dp, if (l.mine) 16.dp else 4.dp))
            .background(if (l.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (l.who == "team") Text("YID PLUS team", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            if (l.media != null) AsyncImage(l.media, "Photo", Modifier.widthIn(max = 260.dp).heightIn(max = 220.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
            if (l.text.isNotBlank()) Text(l.text, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
            if (l.time != null) Text(if (l.time == "now") "now" else clock(l.time), Modifier.align(Alignment.End), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The site's _gsSendLive with a file: multipart POST /api/support-chats (chat_id, text, file). With the QA fixture on it is logged, never sent. */
private suspend fun stSupportUpload(c: Context, chatId: String, text: String, uri: Uri): JSONObject = withContext(Dispatchers.IO) {
    val type = c.contentResolver.getType(uri) ?: "image/jpeg"
    val bytes = c.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw Api.ApiError(400, "Couldn't read that file")
    if (bytes.size > 12 * 1024 * 1024) throw Api.ApiError(413, "Image too large (max 12MB)")
    val b = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("chat_id", chatId)
    if (text.isNotBlank()) b.addFormDataPart("text", text)
    b.addFormDataPart("file", "photo." + (if (type.contains("png")) "png" else "jpg"), bytes.toRequestBody(type.toMediaType()))
    val req = Request.Builder().url(Api.BASE + "/api/support-chats").post(b.build()).header("Accept", "application/json").build()
    Fixture.answer(req)?.let { return@withContext it }
    Api.http.newCall(req).execute().use { r ->
        val t = r.body?.string() ?: ""
        val j = runCatching { JSONObject(t) }.getOrNull()
        if (!r.isSuccessful) throw Api.ApiError(r.code, j?.optString("error")?.takeIf { it.isNotBlank() } ?: "The site answered ${r.code}")
        j ?: JSONObject()
    }
}
