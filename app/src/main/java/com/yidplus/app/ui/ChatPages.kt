package com.yidplus.app.ui

import android.net.Uri
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.yidplus.app.data.Api
import com.yidplus.app.data.Room
import com.yidplus.app.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/* ═════════════════════════ Chat pages (each one draws itself; Nav.open(...) shows it, Back closes it) ═════════════════════════ */

@Composable
internal fun InfoAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.width(72.dp).clip(RoundedCornerShape(12.dp)).border1().tap(RoundedCornerShape(12.dp), onClick = onClick).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = ChatLook.bar); Text(label, fontSize = 13.sp, color = ChatLook.bar, maxLines = 1)
    }
}
@Composable internal fun Modifier.border1(): Modifier = this.then(Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)))

@Composable
internal fun ChDialogRow(text: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    Text(text, Modifier.fillMaxWidth().tap(onClick = onClick).padding(vertical = 14.dp, horizontal = 6.dp), fontSize = 16.sp, color = color)
}

/** User search used by New message and Add members. */
@Composable
internal fun UserSearch(hint: String, onUser: (ChMember) -> Unit, selected: Set<String> = emptySet(), header: @Composable () -> Unit = {}) {
    var q by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<ChMember>?>(null) }
    LaunchedEffect(q) { delay(if (q.isBlank()) 0 else 350); users = runCatching { withContext(Dispatchers.IO) { ChatApi.users(q.trim()) } }.getOrElse { emptyList() } }
    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).firstFocus(), singleLine = true, placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Filled.Search, null) }, textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content), shape = RoundedCornerShape(24.dp))
    LazyColumn(Modifier.fillMaxSize()) {
        item { header() }
        val u = users
        when {
            u == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            u.isEmpty() -> item { ChEmpty(Icons.Filled.PersonSearch, if (q.isBlank()) "Type a name to find people" else "No one found for “$q”") }
            else -> items(u, key = { it.id }) { mb ->
                ChRow(mb.nick, mb.title.ifBlank { null }, { ChAvatar(mb.photo, mb.nick, mb.id, 44.dp) },
                    trail = { if (mb.id in selected) Icon(Icons.Filled.CheckCircle, "Selected", tint = ChatLook.unread) }) { onUser(mb) }
            }
        }
    }
}

/** New message: find a person, open (or create) the private chat. */
class ChNewChat : PageScreen() {
    override val perfName get() = "NewChat"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChBar("New message", onBack = { Nav.back() })
            UserSearch("Search people", onUser = { mb ->
                scope.launch {
                    runCatching { ChatApi.openDm(mb.id) }.onSuccess { id -> Nav.back(); ChatApi.rooms.load(true); Nav.open(Screen.Chat(ChatApi.info(id)?.room ?: ChatApi.roomOf(id, "private", mb.nick, mb.photo, mb.id))) }
                        .onFailure { toast(c, Api.friendly(it)) }
                }
            }, header = {
                ChRow("New group", null, { Box(Modifier.size(44.dp).clip(CircleShape).background(ChatLook.bar), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Group, null, tint = Color.White) } }) { Nav.back(); Nav.open(ChNewGroup("group")) }
                ChRow("New channel", null, { Box(Modifier.size(44.dp).clip(CircleShape).background(ChatLook.bar), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Campaign, null, tint = Color.White) } }) { Nav.back(); Nav.open(ChNewGroup("channel")) }
                ChRow("Join by invite link", null, { Box(Modifier.size(44.dp).clip(CircleShape).background(ChatLook.bar), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Link, null, tint = Color.White) } }) { Nav.back(); Nav.open(ChJoinLink()) }
            })
        }
    }
}

/** Add members to an existing group (admins). */
data class ChPickUsers(val roomId: String, val roomName: String) : PageScreen() {
    override val perfName get() = "AddMembers"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        val added = remember { mutableStateListOf<String>() }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChBar("Add members", sub = roomName, onBack = { Nav.back() })
            val existing = remember(roomId, ChatApi.rooms.data) { ChatApi.info(roomId)?.members?.map { it.id }?.toSet().orEmpty() }
            UserSearch("Search people", selected = added.toSet() + existing, onUser = { mb ->
                if (mb.id in existing) toast(c, "${mb.nick} is already in the group")
                else if (mb.id !in added) scope.launch { runCatching { ChatApi.addMember(roomId, mb.id) }.onSuccess { added.add(mb.id); toast(c, "${mb.nick} added"); ChatApi.rooms.load(true) }.onFailure { toast(c, Api.friendly(it)) } }
            })
        }
    }
}

/** Join a group by its invite link (https://yidplus.com/chat?join=CODE) or code: preview, then join. */
class ChJoinLink(val initial: String = "") : PageScreen() {
    override val perfName get() = "JoinLink"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var link by remember { mutableStateOf(initial) }
        var preview by remember { mutableStateOf<JSONObject?>(null) }
        var err by remember { mutableStateOf<String?>(null) }
        val code = remember(link) { Regex("""(?:join|invite|code)=([A-Za-z0-9_-]+)""").find(link)?.groupValues?.get(1) ?: link.trim().substringAfterLast('/').takeIf { Regex("""[A-Za-z0-9_-]{6,40}""").matches(it) } }
        LaunchedEffect(code) { preview = null; err = null; if (code != null) { delay(300); runCatching { ClApi.inviteInfo(code) }.onSuccess { preview = it; if (it == null) err = "Invalid invite link" }.onFailure { err = Api.friendly(it) } } }
        var pending by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChBar("Join by invite link", onBack = { Nav.back() })
            OutlinedTextField(link, { link = it }, Modifier.fillMaxWidth().padding(16.dp).firstFocus(), singleLine = true, label = { Text("Paste the invite link") }, placeholder = { Text("${Api.BASE}/chat?join=…") })
            val p = preview
            if (p != null) Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ChAvatar(Api.abs(p.chStr("photo_url").ifBlank { null }), p.chStr("name"), p.chStr("id"), 80.dp)
                Text(p.chStr("name"), Modifier.padding(top = 8.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, style = TextStyle(textDirection = TextDirection.Content))
                Text("${p.optInt("members")} members · " + (if (p.chStr("visibility") == "public") "Public" else "Private — an admin approves new members"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (p.chStr("description").isNotBlank()) Text(p.chStr("description"), Modifier.padding(top = 6.dp), fontSize = 14.sp, textAlign = TextAlign.Center, style = TextStyle(textDirection = TextDirection.Content))
                if (pending) Text("⏳ Request sent. You'll be in once an admin approves.", Modifier.padding(top = 14.dp), fontSize = 15.sp, color = ChatLook.bar, textAlign = TextAlign.Center)
                else PrimaryButton(if (p.chStr("visibility") == "public") "Join" else "Request to join", {
                    scope.launch { runCatching { ClApi.inviteJoin(code!!) }.onSuccess { r ->
                        // chat.js _handleInviteJoin: joined / already_member open the chat; pending = a request the admins approve.
                        when (r.chStr("status")) {
                            "pending" -> { pending = true; toast(c, "⏳ Request sent! The admin will approve you.") }
                            else -> { val id = r.chStr("room_id").ifBlank { p.chStr("id") }; toast(c, if (r.chStr("status") == "already_member") "You're already in this group" else "✅ Joined \"${p.chStr("name")}\"")
                                ChatApi.rooms.load(true); Nav.back(); Nav.open(Screen.Chat(ChatApi.info(id)?.room ?: ChatApi.roomOf(id, p.chStr("type").ifBlank { "group" }, p.chStr("name")))) }
                        } }.onFailure { toast(c, Api.friendly(it)) } }
                }, Modifier.fillMaxWidth().padding(top = 14.dp))
            } else if (err != null) Text(err!!, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            else ChEmpty(Icons.Filled.Link, "Paste a group's invite link to see it and join")
        }
    }
}

/** Saved (bookmarked) messages from every chat. */
class ChSaved : PageScreen() {
    override val perfName get() = "Saved"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var list by remember { mutableStateOf<List<JSONObject>?>(null) }
        suspend fun load() { list = runCatching { withContext(Dispatchers.IO) { chObjs(Api.get("/api/chat/bookmarks").optJSONArray("bookmarks")) } }.getOrElse { toast(c, Api.friendly(it)); emptyList() } }
        LaunchedEffect(Unit) { load() }
        var sel by remember { mutableStateOf<JSONObject?>(null) }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChBar("Saved messages", onBack = { Nav.back() })
            val l = list
            when {
                l == null -> ChSkeleton(5)
                l.isEmpty() -> ChEmpty(Icons.Filled.StarBorder, "No saved messages yet. Long-press a message → Save message")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(l, key = { it.chStr("message_id") }) { b ->
                        val txt = b.chStr("text").let { if (b.chStr("type") == "voice") "🎤 Voice message" else if (it.isBlank() && b.chStr("media_key").isNotBlank()) "📎 Media" else it }
                        ChRow(b.chStr("sender_nick"), txt, { ChAvatar(null, b.chStr("sender_nick"), b.chStr("sender_nick"), 44.dp) },
                            trail = { Text(chatTime(b.chStr("msg_time")), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }) { sel = b }
                    }
                }
            }
        }
        sel?.let { b ->
            AlertDialog(onDismissRequest = { sel = null }, title = { Text(b.chStr("sender_nick")) }, text = {
                Column {
                    Text(b.chStr("text"), fontSize = 15.sp, maxLines = 8, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                    ChDialogRow("Open chat") { sel = null; val rid = b.chStr("room_id"); Nav.open(Screen.Chat(ChatApi.info(rid)?.room ?: ChatApi.roomOf(rid, "group", "Chat"))) }
                    ChDialogRow("Copy") { sel = null; chCopy(c, b.chStr("text")) }
                    ChDialogRow("Remove from saved", Color(0xFFD32F2F)) { sel = null; scope.launch { runCatching { ChatApi.unsave(b.chStr("message_id")) }; load() } }
                }
            }, confirmButton = { TextButton({ sel = null }) { Text("Close") } })
        }
    }
}

/** Forward a message to any of my chats (text the site's way, with "↩ Forwarded"; photos/videos/files are sent again as a new copy). */
data class ChForward(val m: ChatMsg) : PageScreen() {
    override val perfName get() = "Forward"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        val res = ChatApi.rooms
        LaunchedEffect(Unit) { res.load() }
        var q by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val sent = remember { mutableStateListOf<String>() }
        fun forward(to: ChatRoomInfo) {
            if (busy || to.room.id in sent) return
            busy = true
            scope.launch {
                try {
                    if (m.media != null && !m.isSticker) withContext(Dispatchers.IO) {
                        val f = java.io.File(c.cacheDir, "fwd_" + System.nanoTime())
                        Api.http.newCall(okhttp3.Request.Builder().url(m.media!!).build()).execute().use { r -> if (!r.isSuccessful) error("Couldn't fetch the file"); r.body!!.byteStream().use { i -> f.outputStream().use { o -> i.copyTo(o) } } }
                        val mime = when { m.isVoice -> "audio/mp4"; m.isImage -> "image/jpeg"; m.isVideo -> "video/mp4"; else -> "application/octet-stream" }
                        try { ChatApi.upload(c, to.room.id, null, f, if (m.isVoice) "voice" else m.type.ifBlank { "media" }, m.mediaKey.substringAfterLast('/').ifBlank { "file" }, mime, if (m.isVoice) m.text else m.caption) } finally { f.delete() }
                    } else ChatApi.forwardText(to.room.id, m)
                    sent.add(to.room.id); toast(c, "✅ Forwarded to ${to.room.name}")
                } catch (e: Exception) { toast(c, "Forward failed: " + Api.friendly(e)) } finally { busy = false }
            }
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChBar("Forward to…", sub = m.preview, onBack = { Nav.back() })
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), singleLine = true, placeholder = { Text("Search chats") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(24.dp), textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content))
            val l = (res.data ?: emptyList()).filter { it.joined && (!it.room.readOnly || it.canManage) && (q.isBlank() || it.room.name.contains(q.trim(), true)) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(l, key = { it.room.id }) { r ->
                    ChRow(r.room.name, if (r.room.id in sent) "Sent ✓" else when (r.room.type) { "private" -> "Private chat"; "channel" -> "Channel"; else -> "Group" },
                        { ChAvatar(r.room.photo, r.room.name, r.room.otherUserId ?: r.room.id, 44.dp) }, modifier = if (r === l.first()) Modifier.firstFocus() else Modifier,
                        trail = { if (r.room.id in sent) Icon(Icons.Filled.CheckCircle, null, tint = ChatLook.unread) else Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = ChatLook.bar) }) { forward(r) }
                }
            }
        }
    }
}

/** New poll (the site's poll: a question, 2-12 options, one or several answers). */
data class ChPollNew(val room: Room) : PageScreen() {
    override val perfName get() = "NewPoll"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var question by remember { mutableStateOf("") }
        val opts = remember { mutableStateListOf("", "") }
        var multi by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        fun send() {
            val o = opts.map { it.trim() }.filter { it.isNotEmpty() }
            if (question.isBlank() || o.size < 2) { toast(c, "Write a question and at least 2 options"); return }
            busy = true
            scope.launch {
                runCatching { Api.post("/api/polls", JSONObject().put("room_id", room.id).put("question", question.trim()).put("options", JSONArray(o)).put("allow_multiple", multi).put("allow_revote", true)) }
                    .onSuccess { toast(c, "📊 Poll sent"); Nav.back() }.onFailure { toast(c, Api.friendly(it)) }
                busy = false
            }
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChBar("New poll", sub = room.name, onBack = { Nav.back() }) { ChIcon(Icons.AutoMirrored.Filled.Send, "Send poll", { send() }) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                OutlinedTextField(question, { question = it }, Modifier.fillMaxWidth().firstFocus(), label = { Text("Question") }, textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content))
                opts.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(s, { opts[i] = it }, Modifier.weight(1f).padding(top = 6.dp), singleLine = true, label = { Text("Option ${i + 1}") }, textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content))
                        if (opts.size > 2) ChIcon(Icons.Filled.Close, "Remove option", { opts.removeAt(i) }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (opts.size < 12) TextButton({ opts.add("") }) { Icon(Icons.Filled.Add, null); Text(" Add option") }
                Row(Modifier.fillMaxWidth().tap { multi = !multi }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Allow several answers", Modifier.weight(1f), fontSize = 16.sp); Switch(multi, null)
                }
                PrimaryButton("Send poll", { send() }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !busy)
            }
        }
    }
}

/** Audio / video call: the site's own call (WebRTC) for this chat, in the app, signed in, with the microphone allowed. */
data class ChCall(val room: Room, val kind: String) : PageScreen() {
    override val perfName get() = "Call"
    @SuppressLint("SetJavaScriptEnabled")
    @Composable override fun Content() {
        val c = ctx()
        var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
        val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> allowed = ok; if (!ok) toast(c, "Allow the microphone to make calls") }
        LaunchedEffect(Unit) { if (!allowed) perm.launch(Manifest.permission.RECORD_AUDIO) }
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            ChBar(room.name, sub = if (kind == "video") "Video call" else "Voice call", onBack = { Nav.back() })
            if (allowed) AndroidView(factory = { ctx ->
                val cm = CookieManager.getInstance()
                Api.session()?.let { cm.setCookie(Api.BASE, "yp_session=$it; Path=/; Secure; HttpOnly") }
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true; settings.domStorageEnabled = true; settings.mediaPlaybackRequiresUserGesture = false
                    settings.userAgentString = settings.userAgentString + " YIDPLUS-Android"
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) {
                            val ok = request.resources.filter { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE || it == PermissionRequest.RESOURCE_VIDEO_CAPTURE }
                            if (ok.isNotEmpty()) request.grant(ok.toTypedArray()) else request.deny()
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        var started = false
                        override fun onPageFinished(view: WebView, u: String) {
                            if (started) return; started = true
                            // Open this room on the site's chat page, then start the call the same way its call button does.
                            view.evaluateJavascript("(function(){var n=0;var t=setInterval(function(){n++;if(typeof CHAT_curRoom!=='undefined'&&CHAT_curRoom&&typeof crStartCall==='function'){clearInterval(t);crStartCall('$kind');}if(n>40)clearInterval(t);},500);})()", null)
                        }
                    }
                    loadUrl("${Api.BASE}/yidplus-chat.html#room=" + chEnc(room.id))
                }
            }, modifier = Modifier.weight(1f).fillMaxWidth())
            else ChEmpty(Icons.Filled.MicOff, "The microphone is needed for calls", action = "Allow") { perm.launch(Manifest.permission.RECORD_AUDIO) }
        }
    }
}
