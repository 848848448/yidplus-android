package com.yidplus.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.yidplus.app.data.Api
import com.yidplus.app.data.Disk
import com.yidplus.app.data.Net
import com.yidplus.app.data.Perf
import com.yidplus.app.data.Room
import com.yidplus.app.data.Session
import com.yidplus.app.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/* ═════════════════════════ Look (WhatsApp-like, light and dark) ═════════════════════════ */

internal object ChatLook {
    val bar: Color @Composable get() = topBarColor()
    val unread = Color(0xFF25A45A)
    val tick = Color(0xFF34B7F1)
    @Composable fun dark() = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    @Composable fun wall() = if (dark()) Color(0xFF0B141A) else Color(0xFFEDE7DE)
    @Composable fun mine() = if (dark()) Color(0xFF005C4B) else Color(0xFFD9FDD3)
    @Composable fun theirs() = if (dark()) Color(0xFF202C33) else Color.White
    @Composable fun ink() = if (dark()) Color(0xFFE9EDEF) else Color(0xFF111B21)
    @Composable fun sub() = if (dark()) Color(0xFF8696A0) else Color(0xFF667781)
    private val names = listOf(Color(0xFF1F7AE0), Color(0xFFD9534F), Color(0xFF8E44AD), Color(0xFFE67E22), Color(0xFF16A085), Color(0xFFC2185B), Color(0xFF2E7D32), Color(0xFF6D4C41))
    fun nameColor(id: String) = names[(id.hashCode() and 0x7fffffff) % names.size]
}

/** The brand-green bar every chat screen uses: back, optional avatar, title + subtitle (tap = info), white icons. */
@Composable
internal fun ChBar(title: String, sub: String? = null, onBack: (() -> Unit)? = null, lead: (@Composable () -> Unit)? = null, onTitle: (() -> Unit)? = null,
                   actions: @Composable RowScope.() -> Unit = {}) {
    Surface(color = ChatLook.bar, contentColor = barContent(), shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) ChIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack) else Spacer(Modifier.width(12.dp))
            Row(Modifier.weight(1f).then(if (onTitle != null) Modifier.tap(RoundedCornerShape(8.dp), onClick = onTitle) else Modifier).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (lead != null) { lead(); Spacer(Modifier.width(8.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(title, color = LocalContentColor.current, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(textDirection = TextDirection.Content))
                    if (!sub.isNullOrBlank()) Text(sub, color = LocalContentColor.current.copy(alpha = .82f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            actions()
        }
    }
}

@Composable
internal fun ChIcon(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = LocalContentColor.current, size: Dp = 44.dp) {
    Box(Modifier.size(size).tap(CircleShape, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** Coloured circle with initials when there's no photo; a green dot when online. */
@Composable
internal fun ChAvatar(url: String?, name: String, id: String, size: Dp = 52.dp, online: Boolean = false) {
    Box(Modifier.size(size)) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(ChatLook.nameColor(id.ifBlank { name })), contentAlignment = Alignment.Center) {
            val ini = remember(name) { chInitials(name) }
            Text(ini, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp, maxLines = 1)
            if (!url.isNullOrBlank()) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (online) Box(Modifier.align(Alignment.BottomEnd).size(size * 0.26f).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(2.dp).clip(CircleShape).background(ChatLook.unread))
    }
}

@Composable
internal fun ChBadge(n: Int, muted: Boolean = false) {
    if (n <= 0) return
    Box(Modifier.heightIn(min = 20.dp).widthIn(min = 20.dp).clip(CircleShape).background(if (muted) Color(0xFF9AA5AC) else ChatLook.unread).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center) {
        Text(if (n > 99) "99+" else n.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Polished empty state: icon + one line (+ optional button). */
@Composable
internal fun ChEmpty(icon: ImageVector, text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        if (action != null) { Spacer(Modifier.height(12.dp)); PrimaryButton(action, onAction) }
    }
}

/** Skeleton rows while the list loads (instead of a bare spinner). */
@Composable
internal fun ChSkeleton(rows: Int = 8) {
    val c = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)
    Column(Modifier.fillMaxSize()) {
        repeat(rows) {
            Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(c))
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Box(Modifier.fillMaxWidth(.55f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(c)); Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(.8f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(c))
                }
            }
        }
    }
}

@Composable
internal fun ChSearchField(q: String, onQ: (String) -> Unit, hint: String, onClose: () -> Unit) {
    Surface(color = ChatLook.bar, contentColor = barContent(), shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ChIcon(Icons.AutoMirrored.Filled.ArrowBack, "Close search", onClose)
            TextField(q, onQ, Modifier.weight(1f).firstFocus(always = true), singleLine = true, placeholder = { Text(hint, color = LocalContentColor.current.copy(alpha = .7f)) },
                textStyle = TextStyle(color = LocalContentColor.current, fontSize = 17.sp, textDirection = TextDirection.Content),
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, cursorColor = LocalContentColor.current,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = LocalContentColor.current, unfocusedTextColor = LocalContentColor.current))
            if (q.isNotEmpty()) ChIcon(Icons.Filled.Close, "Clear", { onQ("") })
        }
    }
}

fun chCopy(c: Context, s: String) {
    (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("message", s)); toast(c, "Copied")
}

/** A list row: 56-72 dp, 16 dp side padding, divider inset after the avatar. */
@Composable
internal fun ChRow(title: String, sub: String?, lead: @Composable () -> Unit, modifier: Modifier = Modifier, trail: @Composable RowScope.() -> Unit = {}, titleColor: Color = Color.Unspecified, onClick: () -> Unit) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).tap(RoundedCornerShape(0.dp), onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            lead()
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = titleColor,
                    style = TextStyle(textDirection = TextDirection.Content))
                if (!sub.isNullOrBlank()) Text(sub, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content))
            }
            trail()
        }
        HorizontalDivider(Modifier.padding(start = 82.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
    }
}

/** Search: my chats by name, messages in all my chats (the site's search), and public groups to join. */
@Composable
internal fun ChSearchResults(q: String, rooms: List<ChatRoomInfo>) {
    var msgs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var groups by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var people by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val c = ctx()
    // runtime-reliability.js global search: Telegram channels and people too (the list of joined Telegram channels is already loaded for the chat list).
    val tg = remember(q) { com.yidplus.app.data.Store.res("/api/telegram-channels", com.yidplus.app.data.TgChannel::list).data.orEmpty()
        .filter { q.trim().length >= 2 && (it.title.contains(q.trim(), true) || it.username.contains(q.trim(), true)) } }
    LaunchedEffect(q) {
        msgs = emptyList(); groups = emptyList(); people = emptyList()
        if (q.trim().length < 2 || Session.me == null) return@LaunchedEffect
        delay(400); busy = true
        runCatching { msgs = chObjs(Api.get("/api/chat?search=" + chEnc(q.trim())).optJSONArray("messages")).take(40) }
        runCatching { val mine = rooms.map { it.room.id }.toSet(); groups = chObjs(Api.get("/api/chat/rooms?search=" + chEnc(q.trim())).optJSONArray("rooms")).filter { it.chStr("id") !in mine } }
        runCatching { people = chObjs(Api.get("/api/users/search?q=" + chEnc(q.trim())).optJSONArray("users")).filter { it.chStr("id") != Session.me?.id }.take(20) }
        busy = false
    }
    val hits = remember(q, rooms) { if (q.isBlank()) rooms else rooms.filter { it.room.name.contains(q.trim(), true) || it.room.preview.contains(q.trim(), true) } }
    LazyColumn(Modifier.fillMaxSize()) {
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (hits.isNotEmpty()) { item { ChSection("Chats") }; items(hits, key = { "r" + it.room.id }) { ChRoomRow(it, Modifier) } }
        if (groups.isNotEmpty()) {
            item { ChSection("Public groups") }
            items(groups, key = { "g" + it.chStr("id") }) { g ->
                ChRow(g.chStr("nick"), "${g.optInt("members")} members · tap to view", { ChAvatar(Api.abs(g.chStr("photo_url").ifBlank { null }), g.chStr("nick"), g.chStr("id")) }) {
                    Nav.open(Screen.Chat(ChatApi.roomOf(g.chStr("id"), "group", g.chStr("nick"), Api.abs(g.chStr("photo_url").ifBlank { null }))))
                }
            }
        }
        if (people.isNotEmpty()) {
            item { ChSection("People") }
            items(people, key = { "u" + it.chStr("id") }) { u ->
                ChRow(u.chStr("nickname"), "Tap to message", { ChAvatar(Api.abs(u.chStr("photo_url").ifBlank { null }), u.chStr("nickname"), u.chStr("id"), 44.dp) }) {
                    scope.launch { runCatching { ChatApi.openDm(u.chStr("id")) }.onSuccess { id -> Nav.open(Screen.Chat(ChatApi.roomOf(id, "private", u.chStr("nickname"), Api.abs(u.chStr("photo_url").ifBlank { null }), u.chStr("id")))) }.onFailure { toast(c, Api.friendly(it)) } }
                }
            }
        }
        if (tg.isNotEmpty()) { item { ChSection("Telegram channels") }; items(tg, key = { "t" + it.username }) { t -> ClTgRow(t, if (t.joined) null else ({
            // chat.js tgQuickJoin: POST /api/telegram-join {username}
            scope.launch { runCatching { Api.post("/api/telegram-join", JSONObject().put("username", t.username)) }.onSuccess { toast(c, "✅ Joined") }.onFailure { toast(c, Api.friendly(it)) } } })) } }
        if (msgs.isNotEmpty()) {
            item { ChSection("Messages") }
            items(msgs, key = { "m" + it.chStr("id") }) { m ->
                ChRow(m.chStr("room_name").ifBlank { m.chStr("sender_nick") }, m.chStr("sender_nick") + ": " + m.chStr("text").take(120), { ChAvatar(null, m.chStr("sender_nick"), m.chStr("sender_id"), 44.dp) },
                    trail = { Text(chatTime(m.chStr("created_at")), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }) {
                    val ri = rooms.firstOrNull { it.room.id == m.chStr("room_id") }
                    Nav.open(Screen.Chat(ri?.room ?: ChatApi.roomOf(m.chStr("room_id"), m.chStr("room_type").ifBlank { "group" }, m.chStr("room_name"))))
                }
            }
        }
        if (q.trim().length >= 2 && !busy && hits.isEmpty() && msgs.isEmpty() && groups.isEmpty() && people.isEmpty() && tg.isEmpty()) item { ChEmpty(Sym.SearchOff, "Nothing found for “$q”") }
    }
}

internal fun JSONObject.chStr(k: String): String = if (isNull(k)) "" else optString(k, "")

@Composable
internal fun ChSection(t: String) {
    Text(t, Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

internal fun chNowIso(): String = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())

/** A message bubble: 16 sp text, the time (and ✓✓ for mine) inside the bubble, a tail on the first of a run, reply quote, reactions under it.
 *  Touch: tap opens media/links, long-press opens the actions. Keypad: OK opens the actions (with "Open" first for media). */
/** Long-press (or keypad OK) on a message: quick reactions, then Reply / Copy / Edit / Forward / Pin / Save / Share / Report / Delete — the site's menu. */
@Composable
internal fun ChActions(m: ChatMsg, mine: Boolean, canPin: Boolean, isPinned: Boolean, canDelete: Boolean, canWrite: Boolean, signedIn: Boolean, myReact: String?, onClose: () -> Unit, onAct: (String) -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(vertical = 8.dp).widthIn(max = 360.dp)) {
                Text(m.preview.ifBlank { "Message" }, Modifier.padding(horizontal = 18.dp, vertical = 6.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content))
                if (signedIn && !m.failed) Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CHAT_QUICK_REACTS.forEachIndexed { i, e ->
                        Box(Modifier.size(44.dp).clip(CircleShape).background(if (myReact == e) ChatLook.bar.copy(alpha = .2f) else Color.Transparent).then(if (i == 0) Modifier.firstFocus(always = true) else Modifier)
                            .tap(CircleShape) { onAct("react:$e") }, contentAlignment = Alignment.Center) { Text(e, fontSize = 24.sp) }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val hasOpen = m.media != null || m.local != null || urlRegex.containsMatchIn(m.text)
                    if (m.failed) ActRow(Icons.Filled.Refresh, "Try again") { onAct("retry") }
                    if (hasOpen) ActRow(Icons.Filled.OpenInNew, if (m.isAudio) "Play" else "Open") { onAct("open") }
                    if (canWrite && !m.failed) ActRow(Icons.AutoMirrored.Filled.Reply, "Reply") { onAct("reply") }
                    if (m.text.isNotBlank() || m.media != null) ActRow(Icons.Filled.ContentCopy, "Copy") { onAct("copy") }
                    if (mine && m.type == "text" && !m.failed) ActRow(Icons.Filled.Edit, "Edit") { onAct("edit") }
                    if (signedIn && !m.failed) ActRow(Icons.AutoMirrored.Filled.Forward, "Forward") { onAct("forward") }
                    if (canPin && !m.failed) ActRow(Icons.Filled.PushPin, if (isPinned) "Unpin" else "Pin") { onAct("pin") }
                    if (signedIn && !m.failed) ActRow(Icons.Filled.Star, "Save message") { onAct("save") }
                    ActRow(Icons.Filled.Share, "Share to apps") { onAct("share") }
                    if (signedIn && !mine) ActRow(Icons.Filled.Flag, "Report") { onAct("report") }
                    if (canDelete && !m.failed) ActRow(Icons.Filled.Delete, "Delete", Color(0xFFD32F2F)) { onAct("delete") }
                }
            }
        }
    }
}


@Composable
private fun ActRow(icon: ImageVector, text: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).tap(RoundedCornerShape(0.dp), onClick = onClick).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color, modifier = Modifier.size(22.dp))
        Text(text, Modifier.padding(start = 18.dp), fontSize = 16.sp, color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color)
    }
}

/** Emoji panel under the composer, with the site's categories and its stickers. */
@Composable
internal fun ChEmojiPanel(onSticker: ((String) -> Unit)? = null, onEmoji: (String) -> Unit) {
    var cat by remember { mutableStateOf(0) }
    val stickerTab = if (onSticker != null) CHAT_EMOJI_SETS.size else -1
    val list = remember(cat) { if (cat == stickerTab) emptyList() else CHAT_EMOJI_SETS[cat].second.split(' ').filter { it.isNotBlank() } }
    Column(Modifier.fillMaxWidth().height(230.dp).background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            CHAT_EMOJI_SETS.forEachIndexed { i, (icon, _) ->
                Box(Modifier.size(44.dp).tap(RoundedCornerShape(8.dp)) { cat = i }.background(if (i == cat) ChatLook.bar.copy(alpha = .15f) else Color.Transparent), contentAlignment = Alignment.Center) { Text(icon, fontSize = 20.sp) }
            }
            if (onSticker != null) Box(Modifier.height(44.dp).tap(RoundedCornerShape(8.dp)) { cat = stickerTab }.background(if (cat == stickerTab) ChatLook.bar.copy(alpha = .15f) else Color.Transparent).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                Text("Stickers", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        HorizontalDivider(thickness = 0.5.dp)
        if (cat == stickerTab && onSticker != null) LazyVerticalGrid(GridCells.Adaptive(76.dp), Modifier.fillMaxSize()) {
            gridItems(CHAT_STICKERS) { s -> Box(Modifier.size(76.dp).tap(RoundedCornerShape(10.dp)) { onSticker(s) }.padding(6.dp), contentAlignment = Alignment.Center) { AsyncImage(s, "Sticker", Modifier.fillMaxSize()) } }
        } else LazyVerticalGrid(GridCells.Adaptive(44.dp), Modifier.fillMaxSize()) {
            gridItems(list) { e -> Box(Modifier.size(44.dp).tap(RoundedCornerShape(8.dp)) { onEmoji(e) }, contentAlignment = Alignment.Center) { Text(e, fontSize = 24.sp) } }
        }
    }
}

/** Attachment menu (the site's: Gallery, Camera, Video, Audio, Document, Poll — plus Contact). */
@Composable
internal fun ChAttachSheet(onPick: (String) -> Unit, onClose: () -> Unit) {
    val opts = listOf(Triple("gallery", "Gallery", Icons.Filled.Image to Color(0xFF7E57C2)), Triple("camera", "Camera", Icons.Filled.PhotoCamera to Color(0xFFE91E63)),
        Triple("video", "Video", Icons.Filled.Movie to Color(0xFFEF6C00)), Triple("audio", "Audio", Icons.Filled.Headphones to Color(0xFFFF7043)),
        Triple("file", "Document", Icons.Filled.Description to Color(0xFF5C6BC0)), Triple("contact", "Contact", Icons.Filled.ContactPhone to Color(0xFF1E88E5)),
        Triple("poll", "Poll", Icons.Filled.Poll to Color(0xFF00897B)))
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            opts.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { (k, label, ic) ->
                        Column(Modifier.width(76.dp).then(if (k == "gallery") Modifier.firstFocus(always = true) else Modifier).tap(RoundedCornerShape(12.dp)) { onPick(k) }.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(50.dp).clip(CircleShape).background(ic.second), contentAlignment = Alignment.Center) { Icon(ic.first, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                            Text(label, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.width(76.dp)) }
                }
            }
        }
    }
}

/** Avatar letters: the first real LETTER of the name (skips ' " ״ ׳ - . and emoji); two for Latin names ("Yossi Klein" → YK), one for Yiddish/Hebrew. */
internal fun chInitials(name: String): String {
    val words = name.trim().split(Regex("""\s+""")).mapNotNull { w -> w.firstOrNull { it.isLetterOrDigit() } }
    if (words.isEmpty()) return "#"
    val a = words[0]
    val latin = a.code < 0x0250
    return (if (latin && words.size > 1 && words[1].code < 0x0250) "$a${words[1]}" else "$a").uppercase()
}
