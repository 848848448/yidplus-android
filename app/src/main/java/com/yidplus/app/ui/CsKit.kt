package com.yidplus.app.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.yidplus.app.data.Api
import com.yidplus.app.data.Session
import com.yidplus.app.data.Room
import com.yidplus.app.data.str
import com.yidplus.app.data.strOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/* ═══════════ CS kit: everything the site's open chat has that the app lacked (helper CS, 2.1) ═══════════ */

/** The site's _linkify: links (http and bare domains), @mentions (tap = open a DM), `code`, **bold**, _italic_, ~strike~. Links/mentions are tappable spans. */
internal fun csFormat(t: String, onLink: (String) -> Unit, onMention: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    val token = Regex("""(https?://[^\s<>"]+)|((?:www\.)?[a-z0-9-]+\.(?:com|org|net|co|io|me|il|info|gov|edu|app|dev|tv|us|uk)(?:/[^\s<>"]*)?)|(@[A-Za-z0-9_]{2,32})|(`[^`\n]+`)|(\*\*[^\s*][^*]*?\*\*)|(~[^\s~][^~]*?~)|((?<=^|[\s(])_[^\s_][^_]*?_(?=[\s).,!?]|$))""", RegexOption.IGNORE_CASE)
    val linkStyle = TextLinkStyles(SpanStyle(color = Color(0xFF1E88E5), textDecoration = TextDecoration.Underline))
    var last = 0
    for (m in token.findAll(t)) {
        if (m.range.first < last) continue
        append(t.substring(last, m.range.first))
        val v = m.value
        when {
            m.groups[1] != null || m.groups[2] != null -> {
                val clean = v.trimEnd('.', ',', ')', '!', '?')
                val url = if (m.groups[1] != null) clean else "https://$clean"
                withLink(LinkAnnotation.Clickable("url", linkStyle) { onLink(url) }) { append(clean) }
                append(v.substring(clean.length))
            }
            m.groups[3] != null -> withLink(LinkAnnotation.Clickable("mention", TextLinkStyles(SpanStyle(color = Color(0xFF1E88E5), fontWeight = FontWeight.SemiBold))) { onMention(v.removePrefix("@")) }) { append(v) }
            m.groups[4] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22000000))) { append(v.trim('`')) }
            m.groups[5] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(v.removePrefix("**").removeSuffix("**")) }
            m.groups[6] != null -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(v.trim('~')) }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(v.removePrefix("_").removeSuffix("_")) }
        }
        last = m.range.last + 1
    }
    if (last < t.length) append(t.substring(last))
}

/** The site's openMention: @name → a private chat with that person ("That's you 🙂", "User not found"). */
internal suspend fun csOpenMention(c: Context, username: String) {
    val me = Session.me
    if (me != null && me.nickname.equals(username, true)) { toast(c, "That's you 🙂"); return }
    toast(c, "Opening @$username…")
    runCatching { Api.post("/api/chat/rooms", JSONObject().put("type", "private").put("other_username", username)) }
        .onSuccess { r -> val id = r.str("room_id"); if (id.isBlank()) toast(c, r.optString("error").ifBlank { "User not found" }) else Nav.open(Screen.Chat(ChatApi.roomOf(id, "private", "@$username"))) }
        .onFailure { toast(c, Api.friendly(it)) }
}

/** Long-press sheet (the site's quick-react bar + context menu, WhatsApp layout): reactions row with "+" for any emoji, then every action the site has, plus Select and Info-free extras. */
@Composable
internal fun CsActions(m: ChatMsg, mine: Boolean, canPin: Boolean, isPinned: Boolean, canDelete: Boolean, canWrite: Boolean, canSave: Boolean, signedIn: Boolean,
                       myReact: String?, onClose: () -> Unit, onAct: (String) -> Unit) {
    var more by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(vertical = 8.dp).widthIn(max = 360.dp)) {
                Text(m.preview.ifBlank { "Message" }, Modifier.padding(horizontal = 18.dp, vertical = 6.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                if (signedIn && !m.failed && !m.deleted) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        CHAT_QUICK_REACTS.forEachIndexed { i, e ->
                            Box(Modifier.size(40.dp).clip(CircleShape).background(if (myReact == e) ChatLook.bar.copy(alpha = .2f) else Color.Transparent)
                                .then(if (i == 0) Modifier.firstFocus(always = true) else Modifier).tap(CircleShape) { onAct("react:$e") }, contentAlignment = Alignment.Center) { Text(e, fontSize = 22.sp) }
                        }
                        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).tap(CircleShape) { more = !more }, contentAlignment = Alignment.Center) {
                            Icon(if (more) Sym.ExpandLess else Sym.Add, "More reactions", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (more) Box(Modifier.fillMaxWidth().height(180.dp)) {
                        val all = remember { CHAT_EMOJI_SETS.flatMap { it.second.split(' ').filter { s -> s.isNotBlank() } }.distinct() }
                        LazyVerticalGrid(GridCells.Adaptive(42.dp), Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                            gridItems(all) { e -> Box(Modifier.size(42.dp).tap(RoundedCornerShape(8.dp)) { CsRecent.add(e); onAct("react:$e") }, contentAlignment = Alignment.Center) { Text(e, fontSize = 22.sp) } }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val hasOpen = m.media != null || m.local != null || urlRegex.containsMatchIn(m.text)
                    if (m.failed) CsActRow(Sym.Refresh, "Try again") { onAct("retry") }
                    if (hasOpen && !m.deleted) CsActRow(Sym.OpenInNew, if (m.isAudio) "Play" else "Open") { onAct("open") }
                    if (canWrite && !m.failed && !m.deleted) CsActRow(Sym.Reply, "Reply") { onAct("reply") }
                    if (canSave && !m.deleted && (m.text.isNotBlank() || m.media != null)) CsActRow(Sym.ContentCopy, "Copy") { onAct("copy") }
                    if (mine && m.type == "text" && !m.failed && !m.deleted) CsActRow(Sym.Edit, "Edit") { onAct("edit") }
                    if (signedIn && canSave && !m.failed && !m.deleted) CsActRow(Sym.Forward, "Forward") { onAct("forward") }
                    if (canPin && !m.failed && !m.deleted) CsActRow(Sym.PushPin, if (isPinned) "Unpin" else "Pin") { onAct("pin") }
                    if (signedIn && canSave && !m.failed && !m.deleted) CsActRow(Sym.Star, "Save message") { onAct("save") }
                    if (canSave && !m.deleted) CsActRow(Sym.Share, "Share to apps") { onAct("share") }
                    if (!m.failed) CsActRow(Sym.CheckCircle, "Select") { onAct("select") }
                    if (signedIn && !mine) CsActRow(Sym.Flag, "Report") { onAct("report") }
                    if (canDelete && !m.failed && !m.deleted) CsActRow(Sym.Delete, "Delete", Color(0xFFD32F2F)) { onAct("delete") }
                }
            }
        }
    }
}

@Composable
internal fun CsActRow(icon: ImageVector, text: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).tap(RoundedCornerShape(0.dp), onClick = onClick).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color, modifier = Modifier.size(22.dp))
        Text(text, Modifier.padding(start = 18.dp), fontSize = 16.sp, color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color)
    }
}

/** The site's recent emoji (yp_emoji_recent: 24 kept, newest first). */
internal object CsRecent {
    private const val K = "cs_emoji_recent"
    var list by mutableStateOf<List<String>>(emptyList()); private set
    private var ctx: Context? = null
    fun init(c: Context) { if (ctx != null) return; ctx = c.applicationContext; list = c.getSharedPreferences("chat_ui", Context.MODE_PRIVATE).getString(K, null)?.split(' ')?.filter { it.isNotBlank() } ?: listOf("❤️", "😂", "👍", "🙏", "😍", "😊", "🔥", "😢") }
    fun add(e: String) { list = (listOf(e) + list.filter { it != e }).take(24); ctx?.getSharedPreferences("chat_ui", Context.MODE_PRIVATE)?.edit()?.putString(K, list.joinToString(" "))?.apply() }
}

/** Emoji + stickers panel (the site's): Recent first, every category, Stickers = built-in + everyone's custom stickers, "+" to add your own, long-press to delete yours. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CsEmojiPanel(onSticker: (String) -> Unit, onPickSticker: () -> Unit, reloadKey: Int, onEmoji: (String) -> Unit) {
    val c = ctx(); val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { CsRecent.init(c) }
    var cat by remember { mutableStateOf(-1) } // -1 recent, 0..n categories, -2 stickers
    var custom by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var delFor by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(cat, reloadKey) { if (cat == -2 && Session.me != null) runCatching { custom = withContext(Dispatchers.IO) { ChatApi.stickers() } } }
    Column(Modifier.fillMaxWidth().height(240.dp).background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            @Composable fun tab(sel: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) =
                Box(Modifier.height(44.dp).widthIn(min = 44.dp).background(if (sel) ChatLook.bar.copy(alpha = .15f) else Color.Transparent).tap(RoundedCornerShape(8.dp), onClick = onClick).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) { content() }
            tab(cat == -1, { cat = -1 }) { Icon(Sym.Schedule, "Recent", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            tab(cat == -2, { cat = -2 }) { Text("Stickers", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            CHAT_EMOJI_SETS.forEachIndexed { i, (icon, _) -> tab(cat == i, { cat = i }) { Text(icon, fontSize = 20.sp) } }
        }
        HorizontalDivider(thickness = 0.5.dp)
        when (cat) {
            -2 -> LazyVerticalGrid(GridCells.Adaptive(76.dp), Modifier.fillMaxSize()) {
                if (Session.me != null) item { Box(Modifier.size(76.dp).padding(6.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).tap(RoundedCornerShape(10.dp)) { onPickSticker() }, contentAlignment = Alignment.Center) {
                    Icon(Sym.AddPhotoAlternate, "Add sticker", tint = ChatLook.bar) } }
                gridItems(custom) { s ->
                    val url = Api.abs(s.str("url")) ?: ""
                    val mineS = s.str("owner_id") == Session.me?.id || chIsSiteAdmin()
                    Box(Modifier.size(76.dp).combinedClickable(onClick = { onSticker(s.str("url")) }, onLongClick = { if (mineS) delFor = s }).padding(6.dp), contentAlignment = Alignment.Center) {
                        AsyncImage(url, "Sticker", Modifier.fillMaxSize())
                    }
                }
                gridItems(CHAT_STICKERS) { s -> Box(Modifier.size(76.dp).tap(RoundedCornerShape(10.dp)) { onSticker(s) }.padding(6.dp), contentAlignment = Alignment.Center) { AsyncImage(s, "Sticker", Modifier.fillMaxSize()) } }
            }
            else -> {
                val list = if (cat == -1) CsRecent.list else remember(cat) { CHAT_EMOJI_SETS[cat].second.split(' ').filter { it.isNotBlank() } }
                LazyVerticalGrid(GridCells.Adaptive(44.dp), Modifier.fillMaxSize()) {
                    gridItems(list) { e -> Box(Modifier.size(44.dp).tap(RoundedCornerShape(8.dp)) { CsRecent.add(e); onEmoji(e) }, contentAlignment = Alignment.Center) { Text(e, fontSize = 24.sp) } }
                }
            }
        }
    }
    delFor?.let { s ->
        AlertDialog(onDismissRequest = { delFor = null }, text = { Text("Delete this sticker?") },
            confirmButton = { TextButton({ delFor = null; scope.launch { runCatching { ChatApi.deleteSticker(s.str("id")) }.onSuccess { toast(c, "✅ Deleted"); custom = custom - s }.onFailure { toast(c, Api.friendly(it)) } } }) { Text("Delete", color = Color(0xFFD32F2F)) } },
            dismissButton = { TextButton({ delFor = null }) { Text("Cancel") } })
    }
}

/** Attach menu: the site's Gallery, Camera, Video, Audio, Document, Poll, Schedule — plus WhatsApp's Video camera and Contact. */
@Composable
internal fun CsAttachSheet(onPick: (String) -> Unit) {
    val opts = listOf(Triple("gallery", "Gallery", Sym.Image to Color(0xFF7E57C2)), Triple("camera", "Camera", Sym.PhotoCamera to Color(0xFFE91E63)),
        Triple("videocam", "Record video", Sym.Videocam to Color(0xFFD81B60)), Triple("video", "Video", Sym.Movie to Color(0xFFEF6C00)),
        Triple("audio", "Audio", Sym.Headphones to Color(0xFFFF7043)), Triple("file", "Document", Sym.Description to Color(0xFF5C6BC0)),
        Triple("contact", "Contact", Sym.ContactPhone to Color(0xFF1E88E5)), Triple("poll", "Poll", Sym.Poll to Color(0xFF00897B)),
        Triple("options", "Schedule", Sym.Schedule to Color(0xFF546E7A)))
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            opts.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { (k, label, ic) ->
                        Column(Modifier.width(76.dp).then(if (k == "gallery") Modifier.firstFocus(always = true) else Modifier).tap(RoundedCornerShape(12.dp)) { onPick(k) }.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(50.dp).clip(CircleShape).background(ic.second), contentAlignment = Alignment.Center) { Icon(ic.first, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                            Text(label, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.width(76.dp)) }
                }
            }
        }
    }
}

/** The site's Message options sheet: 🕓 Schedule for later + 💨 Disappear after Off / 1 min / 1 hour / 1 day / 1 week. */
@Composable
internal fun CsMsgOptions(disappear: Int, onSchedule: () -> Unit, onDisappear: (Int) -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("Message options") }, text = {
        Column {
            CsActRow(Sym.Schedule, "Schedule for later") { onSchedule() }
            Text("💨 Disappear after", Modifier.padding(top = 10.dp, bottom = 6.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Off" to 0, "1 min" to 60, "1 hour" to 3600, "1 day" to 86400, "1 week" to 604800).forEach { (l, s) ->
                    FilterChip(selected = disappear == s, onClick = { onDisappear(s) }, label = { Text(l) })
                }
            }
        }
    }, confirmButton = { TextButton(onClose) { Text("Done") } })
}

/** "🕓 Mar 3, 4:15 PM · 💨 1h  ✕" above the composer (the site's msg-opts-badge); tap clears. */
@Composable
internal fun CsOptsBadge(scheduleAt: Long?, disappear: Int, onClear: () -> Unit) {
    if (scheduleAt == null && disappear == 0) return
    val parts = buildList {
        if (scheduleAt != null) add("🕓 " + java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US).format(java.util.Date(scheduleAt)))
        if (disappear > 0) add("💨 " + when { disappear >= 86400 -> "${disappear / 86400}d"; disappear >= 3600 -> "${disappear / 3600}h"; else -> "${disappear / 60}m" })
    }
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).tap(RoundedCornerShape(0.dp), onClick = onClear).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(parts.joinToString("  ·  "), Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Sym.Close, "Clear message options", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The site's report modal: Spam / Harassment / Inappropriate Content / Other + "Additional details (optional)". */
@Composable
internal fun CsReport(onSend: (String, String) -> Unit, onClose: () -> Unit) {
    var reason by remember { mutableStateOf("spam") }
    var details by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Report message") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            listOf("spam" to "Spam", "harassment" to "Harassment", "inappropriate" to "Inappropriate Content", "other" to "Other").forEach { (v, l) ->
                Row(Modifier.fillMaxWidth().tap { reason = v }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(reason == v, { reason = v }); Text(l, fontSize = 16.sp) }
            }
            OutlinedTextField(details, { details = it }, Modifier.fillMaxWidth().padding(top = 6.dp), placeholder = { Text("Additional details (optional)") }, minLines = 2,
                textStyle = TextStyle(fontSize = 15.sp, textDirection = TextDirection.Content))
        }
    }, confirmButton = { TextButton({ onSend(reason, details.trim()) }) { Text("Submit Report") } }, dismissButton = { TextButton(onClose) { Text("Cancel") } })
}

/** The site's pinned-messages list (📌 history): who pinned what, tap = jump. */
@Composable
internal fun CsPinnedList(roomId: String, onJump: (String) -> Unit, onClose: () -> Unit) {
    var list by remember { mutableStateOf<List<JSONObject>?>(null) }
    var err by remember { mutableStateOf(false) }
    LaunchedEffect(roomId) { runCatching { list = withContext(Dispatchers.IO) { ChatApi.pinnedHistory(roomId) } }.onFailure { err = true } }
    AlertDialog(onDismissRequest = onClose, title = { Text("Pinned messages") }, text = {
        val l = list
        when {
            err -> Text("Could not load pinned messages")
            l == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            l.isEmpty() -> Text("No pinned messages yet")
            else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(l) { p ->
                    Column(Modifier.fillMaxWidth().tap { onJump(p.str("message_id")) }.padding(vertical = 8.dp)) {
                        if (p.str("sender_nick").isNotBlank()) Text("@" + p.str("sender_nick"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ChatLook.bar)
                        Text(if (p.str("text").isBlank()) "[deleted message]" else if (p.str("type") == "text") p.str("text").take(80) else "[" + p.str("type") + "]",
                            fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                        Text("Pinned by @" + p.str("pinned_by_nick").ifBlank { "admin" } + " · " + chatTime(p.strOrNull("pinned_at")), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
            }
        }
    }, confirmButton = { TextButton(onClose) { Text("Close") } })
}

/** Select mode bar (the site's select-action-bar): ✕, "N selected", Copy, Forward, Delete. */
@Composable
internal fun CsSelectBar(n: Int, canDelete: Boolean, canSave: Boolean, onClose: () -> Unit, onCopy: () -> Unit, onForward: () -> Unit, onDelete: () -> Unit) {
    Surface(color = ChatLook.bar, contentColor = Color.White) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ChIcon(Sym.Close, "Cancel selection", onClose)
            Text("$n selected", Modifier.weight(1f).padding(start = 8.dp), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            if (canSave) ChIcon(Sym.ContentCopy, "Copy", onCopy)
            if (canSave) ChIcon(Sym.Forward, "Forward", onForward)
            if (canDelete) ChIcon(Sym.Delete, "Delete", onDelete)
        }
    }
}

/** "N unread messages" line (the site's _insertUnreadSeparator, which the site never shows; WhatsApp does). */
@Composable
internal fun CsUnreadLine(n: Int) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(ChatLook.theirs().copy(alpha = .85f)).padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
        Text(if (n == 1) "1 unread message" else "$n unread messages", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ChatLook.bar)
    }
}


/** Photos/videos before sending (WhatsApp's preview): the pictures, remove one, a caption, Send. */
@Composable
internal fun CsMediaPreview(list: List<Uri>, onRemove: (Uri) -> Unit, onSend: (String) -> Unit, onClose: () -> Unit) {
    var cap by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChIcon(Sym.Close, "Cancel", onClose, tint = Color.White)
                    Text(if (list.size == 1) "1 item" else "${list.size} items", Modifier.weight(1f).padding(start = 8.dp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                var cur by remember { mutableStateOf(0) }
                val shown = list.getOrNull(cur.coerceIn(0, (list.size - 1).coerceAtLeast(0)))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (shown != null) AsyncImage(shown, "Preview", Modifier.fillMaxSize().padding(6.dp))
                }
                if (list.size > 1) LazyRow(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(list.size) { i ->
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).then(if (i == cur) Modifier.border(2.dp, ChatLook.bar, RoundedCornerShape(8.dp)) else Modifier).tap(RoundedCornerShape(8.dp)) { cur = i }) {
                            AsyncImage(list[i], null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            Box(Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(Color(0xAA000000)).tap(CircleShape) { onRemove(list[i]); if (cur >= list.size - 1) cur = 0 }, contentAlignment = Alignment.Center) {
                                Icon(Sym.Close, "Remove", Modifier.size(14.dp), tint = Color.White) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextField(cap, { cap = it }, Modifier.weight(1f).firstFocus(), placeholder = { Text("Add a caption…") }, maxLines = 4, shape = RoundedCornerShape(24.dp),
                        textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content),
                        colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(52.dp).clip(CircleShape).background(ChatLook.bar).tap(CircleShape) { onSend(cap.trim()) }, contentAlignment = Alignment.Center) {
                        Icon(Sym.Send, "Send", tint = Color.White) }
                }
            }
        }
    }
}

/** Forward several messages (select mode): pick chats, each message goes the site's way (text as text, media by its stored key, no re-upload). */
data class CsForwardMany(val list: List<ChatMsg>) : PageScreen() {
    override val perfName get() = "ForwardMany"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        val res = ChatApi.rooms
        LaunchedEffect(Unit) { res.load() }
        var q by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val sent = remember { mutableStateListOf<String>() }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChBar("Forward ${list.size} message" + (if (list.size == 1) "" else "s"), sub = "to…", onBack = { Nav.back() })
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), singleLine = true, placeholder = { Text("Search chats") },
                leadingIcon = { Icon(Sym.Search, null) }, shape = RoundedCornerShape(24.dp), textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content))
            val l = (res.data ?: emptyList()).filter { it.joined && (!it.room.readOnly || it.canManage) && (q.isBlank() || it.room.name.contains(q.trim(), true)) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(l, key = { it.room.id }) { r ->
                    Row(Modifier.fillMaxWidth().then(if (r === l.first()) Modifier.firstFocus() else Modifier).tap {
                        if (busy || r.room.id in sent) return@tap
                        busy = true
                        scope.launch {
                            var ok = 0
                            for (m in list) runCatching { if (m.media != null && !m.isSticker && m.mediaKey.isNotBlank()) ChatApi.forwardMedia(r.room.id, m) else ChatApi.forwardText(r.room.id, m) }.onSuccess { ok++ }
                            busy = false; sent.add(r.room.id)
                            toast(c, if (ok == list.size) "✅ Forwarded to ${r.room.name}" else "Forwarded $ok of ${list.size} to ${r.room.name}")
                        }
                    }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChAvatar(r.room.photo, r.room.name, r.room.otherUserId ?: r.room.id, 44.dp)
                        Text(r.room.name, Modifier.weight(1f).padding(start = 12.dp), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (r.room.id in sent) Icon(Sym.Check, "Sent", tint = ChatLook.bar)
                    }
                }
            }
        }
    }
}


/** The site's _groupMediaAlbums: a run of photos/videos from one sender (later ones without a caption, at most 10) is one album bubble. */
internal fun csAlbums(chrono: List<ChatMsg>): Map<String, List<ChatMsg>> {
    val out = HashMap<String, List<ChatMsg>>()
    fun ok(m: ChatMsg) = (m.isImage || m.isVideo) && m.media != null && !m.viewOnce && !m.deleted && !m.pending && !m.failed
    var i = 0
    while (i < chrono.size) {
        val m = chrono[i]
        if (ok(m)) {
            var j = i + 1
            val g = arrayListOf(m)
            while (j < chrono.size && g.size < 10) { val n = chrono[j]; if (ok(n) && n.senderId == m.senderId && n.caption.isBlank()) { g.add(n); j++ } else break }
            if (g.size > 1) { out[m.id] = g; i = j; continue }
        }
        i++
    }
    return out
}

/** Album grid inside one bubble: 2 columns for 2, else 3; videos carry a play mark; each tile opens the viewer at itself. */
@Composable
internal fun CsAlbumGrid(list: List<ChatMsg>, maxW: androidx.compose.ui.unit.Dp, onMedia: (String) -> Unit) {
    val cols = if (list.size == 2 || list.size == 4) 2 else 3
    val w = maxW.coerceAtMost(300.dp)
    Column(Modifier.width(w).clip(RoundedCornerShape(10.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        list.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { m ->
                    Box(Modifier.weight(1f).aspectRatio(1f).background(Color.Black).clickable { m.media?.let(onMedia) }) {
                        if (m.isVideo) { Box(Modifier.fillMaxSize().background(Color(0xFF222222))); Icon(Sym.PlayCircle, "Video", Modifier.align(Alignment.Center).size(30.dp), tint = Color.White) }
                        else AsyncImage(m.media, "Photo", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
