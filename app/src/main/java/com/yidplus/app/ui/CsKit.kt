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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.LayoutDirection
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
    val token = Regex("""(https?://[^\s<>"]+)|((?:www\.)?[a-z0-9-]+\.(?:com|org|net|co|io|me|il|info|gov|edu|app|dev|tv|us|uk)(?:/[^\s<>"]*)?)|(@[\p{L}0-9_]{2,32})|(`[^`\n]+`)|(\*\*[^\s*][^*]*?\*\*)|(~[^\s~][^~]*?~)|((?<=^|[\s(])_[^\s_][^_]*?_(?=[\s).,!?]|$))""", RegexOption.IGNORE_CASE)
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
            m.groups[3] != null -> withLink(LinkAnnotation.Clickable("mention", TextLinkStyles(SpanStyle(color = Color(0xFF00A884), fontWeight = FontWeight.SemiBold))) { onMention(v.removePrefix("@")) }) { append(v) }
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
                    val reacts = ClLocal.reactVersion.let { ClLocal.quickReacts(ctx()) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        reacts.forEachIndexed { i, e ->
                            Box(Modifier.size(40.dp).clip(CircleShape).background(if (myReact == e) ChatLook.accent.copy(alpha = .2f) else Color.Transparent)
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
                    if (mine && !m.failed && !m.deleted) CsActRow(Sym.Info, "Info") { onAct("info") }
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
    var search by remember { mutableStateOf("") }
    val found = remember(search) { chatEmojiSearch(search) }
    LaunchedEffect(cat, reloadKey) { if (cat == -2 && Session.me != null) runCatching { custom = withContext(Dispatchers.IO) { ChatApi.stickers() } } }
    Column(Modifier.fillMaxWidth().height(286.dp).background(MaterialTheme.colorScheme.surface)) {
        // WhatsApp-style emoji search box.
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Sym.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(search, { search = it }, Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 10.dp), singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(ChatLook.accent),
                decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (search.isEmpty()) Text("Search emoji", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } })
            if (search.isNotEmpty()) Icon(Sym.Close, "Clear", Modifier.size(18.dp).tap(CircleShape) { search = "" }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (search.isNotEmpty()) {
            if (found.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No emoji found", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else LazyVerticalGrid(GridCells.Adaptive(44.dp), Modifier.fillMaxSize()) {
                gridItems(found) { e -> Box(Modifier.size(44.dp).tap(RoundedCornerShape(8.dp)) { CsRecent.add(e); onEmoji(e) }, contentAlignment = Alignment.Center) { Text(e, fontSize = 24.sp) } }
            }
            return@Column
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            @Composable fun tab(sel: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) =
                Box(Modifier.height(44.dp).widthIn(min = 44.dp).background(if (sel) ChatLook.accent.copy(alpha = .15f) else Color.Transparent).tap(RoundedCornerShape(8.dp), onClick = onClick).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) { content() }
            tab(cat == -1, { cat = -1 }) { Icon(Sym.Schedule, "Recent", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            tab(cat == -2, { cat = -2 }) { Text("Stickers", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            CHAT_EMOJI_SETS.forEachIndexed { i, (icon, _) -> tab(cat == i, { cat = i }) { Text(icon, fontSize = 20.sp) } }
        }
        HorizontalDivider(thickness = 0.5.dp)
        when (cat) {
            -2 -> LazyVerticalGrid(GridCells.Adaptive(76.dp), Modifier.fillMaxSize()) {
                if (Session.me != null) item { Box(Modifier.size(76.dp).padding(6.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).tap(RoundedCornerShape(10.dp)) { onPickSticker() }, contentAlignment = Alignment.Center) {
                    Icon(Sym.AddPhotoAlternate, "Add sticker", tint = ChatLook.accent) } }
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
internal fun CsAttachSheet(onPick: (String) -> Unit, onRecent: ((Uri) -> Unit)? = null) {
    val opts = listOf(Triple("gallery", "Gallery", Sym.Image to Color(0xFF7E57C2)), Triple("camera", "Camera", Sym.PhotoCamera to Color(0xFFE91E63)),
        Triple("videocam", "Record video", Sym.Videocam to Color(0xFFD81B60)), Triple("video", "Video", Sym.Movie to Color(0xFFEF6C00)),
        Triple("audio", "Audio", Sym.Headphones to Color(0xFFFF7043)), Triple("file", "Document", Sym.Description to Color(0xFF5C6BC0)),
        Triple("contact", "Contact", Sym.ContactPhone to Color(0xFF1E88E5)), Triple("location", "Location", Sym.LocationOn to Color(0xFF43A047)),
        Triple("gif", "GIF", Sym.Gif to Color(0xFF8E24AA)), Triple("videonote", "Video note", Sym.Videocam to Color(0xFF00ACC1)),
        Triple("poll", "Poll", Sym.Poll to Color(0xFF00897B)), Triple("options", "Schedule", Sym.Schedule to Color(0xFF546E7A)))
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
            // WhatsApp shows a strip of your recent photos at the top of the attach panel — tap one to send it straight away.
            if (onRecent != null) {
                val c = ctx()
                val perm = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES else android.Manifest.permission.READ_EXTERNAL_STORAGE
                fun granted() = androidx.core.content.ContextCompat.checkSelfPermission(c, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
                var hasPerm by remember { mutableStateOf(granted()) }
                var recents by remember { mutableStateOf<List<Uri>>(emptyList()) }
                val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok -> hasPerm = ok }
                // Ask for photo access the moment the panel opens (like WhatsApp), so Recents can appear.
                LaunchedEffect(Unit) { if (!granted()) permLauncher.launch(perm) }
                LaunchedEffect(hasPerm) { if (hasPerm) recents = withContext(Dispatchers.IO) { runCatching { statusRecentImages(c).take(20) }.getOrDefault(emptyList()) } }
                if (recents.isNotEmpty()) {
                    Text("Recent", Modifier.padding(start = 16.dp, top = 2.dp, bottom = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        recents.forEach { u ->
                            AsyncImage(u, "Recent photo", contentScale = ContentScale.Crop,
                                modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).tap(RoundedCornerShape(10.dp)) { onRecent(u) })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                } else if (!hasPerm) {
                    // Permission not granted yet: a tap-to-allow row so Recents can show.
                    Row(Modifier.fillMaxWidth().tap(RoundedCornerShape(10.dp)) { permLauncher.launch(perm) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Sym.Image, null, Modifier.size(20.dp), tint = ChatLook.accent)
                        Text("  Allow photo access to show recent photos", fontSize = 13.sp, color = ChatLook.accent)
                    }
                }
            }
            opts.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { (k, label, ic) ->
                        Column(Modifier.width(76.dp).then(if (k == "gallery") Modifier.firstFocus(always = true) else Modifier).tap(RoundedCornerShape(12.dp)) { onPick(k) }.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(46.dp).clip(CircleShape).background(ic.second), contentAlignment = Alignment.Center) { Icon(ic.first, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
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
                        if (p.str("sender_nick").isNotBlank()) Text("@" + p.str("sender_nick"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ChatLook.accent)
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

/** Select mode bar = WhatsApp's long-press bar: ✕, count, then Reply · Star · Copy · Forward · Delete · ⋮ (one message) or Copy · Forward · Delete (several). */
@Composable
internal fun CsSelectBar(n: Int, canDelete: Boolean, canSave: Boolean, onClose: () -> Unit, onCopy: () -> Unit, onForward: () -> Unit, onDelete: () -> Unit,
                         onReply: (() -> Unit)? = null, onStar: (() -> Unit)? = null, onMore: (() -> Unit)? = null) {
    Surface(color = ChatLook.accent, contentColor = Color.White) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ChIcon(Sym.Close, "Cancel selection", onClose)
            Text("$n", Modifier.weight(1f).padding(start = 8.dp), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            if (n == 1 && onReply != null) ChIcon(Sym.Reply, "Reply", onReply)
            if (n == 1 && onStar != null && canSave) ChIcon(Sym.Star, "Star", onStar)
            if (canSave) ChIcon(Sym.ContentCopy, "Copy", onCopy)
            if (canSave) ChIcon(Sym.Forward, "Forward", onForward)
            if (canDelete) ChIcon(Sym.Delete, "Delete", onDelete)
            if (n == 1 && onMore != null) ChIcon(Sym.MoreVert, "More actions", onMore)
        }
    }
}

/** WhatsApp's reaction strip under the long-press bar: the quick reactions and "+" for any emoji. */
@Composable
internal fun CsReactStrip(myReact: String?, onReact: (String) -> Unit, onMore: () -> Unit) {
    val c = ctx()
    val reacts = ClLocal.reactVersion.let { ClLocal.quickReacts(c) }
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        reacts.forEach { e ->
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (myReact == e) ChatLook.accent.copy(alpha = .2f) else Color.Transparent).tap(CircleShape) { onReact(e) }, contentAlignment = Alignment.Center) { Text(e, fontSize = 22.sp) }
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).tap(CircleShape) { onMore() }, contentAlignment = Alignment.Center) {
            Icon(Sym.Add, "More reactions", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}

private val REACT_EMOJIS = listOf("👍","❤️","😂","😮","😢","🙏","🔥","🎉","👏","💯","😍","🥰","😊","😁","😅","😆","😉","😎","🤔","🤗","🙌","👌","✌️","🤝","💪","👀","😇","🥳","😋","😜","🤪","😭","😤","😡","🤯","😱","🥺","😴","🙄","😬","🤫","😏","😌","💔","💕","💖","✨","⭐","🌟","⚡","💥","🎊","🏆","✅","❌","🤣")

/** Pick a wallpaper for this one chat (WhatsApp's per-chat wallpaper). */
@Composable
internal fun CsWallpaperPicker(current: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, confirmButton = { TextButton(onClose) { Text("Close") } }, title = { Text("Chat wallpaper", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }, text = {
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.heightIn(max = 340.dp)) {
            gridItems(ChatWallpapers) { w ->
                Column(Modifier.padding(6.dp).tap(RoundedCornerShape(10.dp)) { onPick(w.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    val mod = Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(10.dp))
                        .then(if (w.from != null && w.to != null) Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(w.from, w.to))) else Modifier.background(MaterialTheme.colorScheme.surfaceVariant))
                        .then(if (w.id == current) Modifier.border(2.dp, ChatLook.accent, RoundedCornerShape(10.dp)) else Modifier)
                    Box(mod, contentAlignment = Alignment.Center) { if (w.id == current) Icon(Sym.Check, null, tint = ChatLook.accent) }
                    Text(w.name, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    })
}

/** Pick any emoji to react with (WhatsApp's "+" on the reaction bar). */
@Composable
internal fun CsReactPicker(onPick: (String) -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, confirmButton = {}, title = { Text("React", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }, text = {
        LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.heightIn(max = 320.dp)) {
            gridItems(REACT_EMOJIS) { e ->
                Box(Modifier.padding(2.dp).size(44.dp).clip(CircleShape).tap(CircleShape) { onPick(e) }, contentAlignment = Alignment.Center) { Text(e, fontSize = 26.sp) }
            }
        }
    })
}

/** "N unread messages" line (the site's _insertUnreadSeparator, which the site never shows; WhatsApp does). */
@Composable
internal fun CsUnreadLine(n: Int) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(ChatLook.theirs().copy(alpha = .85f)).padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
        Text(if (n == 1) "1 unread message" else "$n unread messages", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ChatLook.accent)
    }
}


/** Rotate an image Uri by `degrees` (clockwise, a multiple of 90) and save it as a fresh JPEG in the cache; returns a FileProvider Uri, or the original on any failure or no rotation. */
internal fun csRotateImage(c: Context, uri: Uri, degrees: Int): Uri {
    if (degrees % 360 == 0) return uri
    return runCatching {
        val src = c.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it) }!!
        val m = android.graphics.Matrix().apply { postRotate((degrees % 360).toFloat()) }
        val rot = android.graphics.Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        val dir = java.io.File(c.cacheDir, "captures").apply { mkdirs() }
        val out = java.io.File(dir, "edit_" + System.currentTimeMillis() + ".jpg")
        java.io.FileOutputStream(out).use { rot.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
        if (rot !== src) src.recycle()
        androidx.core.content.FileProvider.getUriForFile(c, c.packageName + ".files", out)
    }.getOrDefault(uri)
}

/** Burn [text] onto a photo, centred (white bold with a soft shadow), and save a fresh JPEG; returns the original on failure or blank text. */
internal fun csBurnText(c: Context, uri: Uri, text: String): Uri {
    if (text.isBlank()) return uri
    return runCatching {
        val src = c.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it) }!!
        val bmp = src.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(bmp)
        val w = bmp.width
        val size = (w / 14f).coerceAtLeast(28f)
        val paint = android.text.TextPaint().apply {
            color = android.graphics.Color.WHITE; textSize = size; isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(size / 5f, 0f, size / 16f, android.graphics.Color.argb(200, 0, 0, 0))
        }
        val lineW = (w * 0.86f).toInt().coerceAtLeast(1)
        val layout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, paint, lineW)
            .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).build()
        canvas.save()
        canvas.translate((w - lineW) / 2f, ((bmp.height - layout.height) / 2f).coerceAtLeast(0f))
        layout.draw(canvas)
        canvas.restore()
        val dir = java.io.File(c.cacheDir, "captures").apply { mkdirs() }
        val out = java.io.File(dir, "txt_" + System.currentTimeMillis() + ".jpg")
        java.io.FileOutputStream(out).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
        if (bmp !== src) src.recycle()
        androidx.core.content.FileProvider.getUriForFile(c, c.packageName + ".files", out)
    }.getOrDefault(uri)
}

/** Photos/videos before sending (WhatsApp's preview): the pictures, remove one, rotate a photo, add text, a caption, Send. */
@Composable
internal fun CsMediaPreview(list: List<Uri>, onRemove: (Uri) -> Unit, onSend: (List<Uri>, String, Boolean) -> Unit, onClose: () -> Unit) {
    var cap by remember { mutableStateOf("") }
    val c = ctx(); val scope = rememberCoroutineScope()
    val rotations = remember { mutableStateMapOf<Uri, Int>() }
    var sending by remember { mutableStateOf(false) }
    var hd by remember { mutableStateOf(false) }
    val overlays = remember { mutableStateMapOf<Uri, String>() }
    var editingText by remember { mutableStateOf<Uri?>(null) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
                var cur by remember { mutableStateOf(0) }
                val shown = list.getOrNull(cur.coerceIn(0, (list.size - 1).coerceAtLeast(0)))
                val isPhoto = shown != null && !shown.toString().lowercase().let { it.endsWith(".mp4") || it.contains("video") }
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChIcon(Sym.Close, "Cancel", onClose, tint = Color.White)
                    Text(if (list.size == 1) "1 item" else "${list.size} items", Modifier.weight(1f).padding(start = 8.dp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    if (shown != null && isPhoto) Box(Modifier.clip(RoundedCornerShape(8.dp)).then(if (hd) Modifier.background(Color.White) else Modifier).tap(RoundedCornerShape(8.dp)) { hd = !hd }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text("HD", color = if (hd) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    if (shown != null && isPhoto) ChIcon(Sym.TextFields, "Add text", { editingText = shown }, tint = Color.White)
                    if (shown != null && isPhoto) ChIcon(Sym.RotateRight, "Rotate", { rotations[shown] = ((rotations[shown] ?: 0) + 90) % 360 }, tint = Color.White)
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (shown != null) AsyncImage(shown, "Preview", Modifier.fillMaxSize().padding(6.dp).rotate((rotations[shown] ?: 0).toFloat()))
                    // The text sits centred over the photo (it's burned into the picture, centred, when you send).
                    val ov = shown?.let { overlays[it] }.orEmpty()
                    if (ov.isNotBlank()) Text(ov, Modifier.fillMaxWidth(0.86f), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, style = TextStyle(textDirection = TextDirection.Content, shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 8f)))
                }
                if (list.size > 1) LazyRow(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(list.size) { i ->
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).then(if (i == cur) Modifier.border(2.dp, ChatLook.accent, RoundedCornerShape(8.dp)) else Modifier).tap(RoundedCornerShape(8.dp)) { cur = i }) {
                            AsyncImage(list[i], null, Modifier.fillMaxSize().rotate((rotations[list[i]] ?: 0).toFloat()), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            Box(Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(Color(0xAA000000)).tap(CircleShape) { onRemove(list[i]); if (cur >= list.size - 1) cur = 0 }, contentAlignment = Alignment.Center) {
                                Icon(Sym.Close, "Remove", Modifier.size(14.dp), tint = Color.White) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextField(cap, { cap = it }, Modifier.weight(1f), placeholder = { Text("Add a caption…") }, maxLines = 4, shape = RoundedCornerShape(24.dp),
                        textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content),
                        colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(52.dp).clip(CircleShape).background(ChatLook.accent).tap(CircleShape) {
                        if (sending) return@tap
                        sending = true
                        val snap = list.toList(); val rots = rotations.toMap(); val ovs = overlays.toMap(); val text = cap.trim()
                        scope.launch {
                            val finals = withContext(Dispatchers.IO) { snap.map { u -> csBurnText(c, csRotateImage(c, u, rots[u] ?: 0), ovs[u] ?: "") } }
                            onSend(finals, text, hd)
                        }
                    }, contentAlignment = Alignment.Center) {
                        if (sending) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        else Icon(Sym.Send, "Send", tint = Color.White) }
                }
            }
        }
    }
    editingText?.let { u ->
        var t by remember(u) { mutableStateOf(overlays[u] ?: "") }
        AlertDialog(onDismissRequest = { editingText = null },
            title = { Text("Add text to photo", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
            text = { TextField(t, { t = it }, Modifier.fillMaxWidth(), placeholder = { Text("Type text…") }, maxLines = 3,
                textStyle = TextStyle(textDirection = TextDirection.Content)) },
            confirmButton = { TextButton({ if (t.isBlank()) overlays.remove(u) else overlays[u] = t.trim(); editingText = null }) { Text("Done") } },
            dismissButton = { TextButton({ overlays.remove(u); editingText = null }) { Text("Remove") } })
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
                        if (r.room.id in sent) Icon(Sym.Check, "Sent", tint = ChatLook.accent)
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


/** WhatsApp's bubble tail: a small triangle at the top outer corner of the first bubble in a run (drawn outside the bubble, before the clip). */
internal fun Modifier.csTail(on: Boolean, mine: Boolean, color: Color): Modifier = if (!on) this else drawBehind {
    val t = 8.dp.toPx(); val h = 10.dp.toPx()
    val right = mine != (layoutDirection == LayoutDirection.Rtl)
    val p = Path().apply {
        if (right) { moveTo(size.width - 1f, 0f); lineTo(size.width + t, 0f); lineTo(size.width - 1f, h) }
        else { moveTo(1f, 0f); lineTo(-t, 0f); lineTo(1f, h) }
        close()
    }
    drawPath(p, color)
}
