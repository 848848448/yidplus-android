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
    val bar = Color(0xFF1F6F5C)
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
    Surface(color = ChatLook.bar, contentColor = Color.White, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) ChIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack) else Spacer(Modifier.width(12.dp))
            Row(Modifier.weight(1f).then(if (onTitle != null) Modifier.tap(RoundedCornerShape(8.dp), onClick = onTitle) else Modifier).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (lead != null) { lead(); Spacer(Modifier.width(8.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(textDirection = TextDirection.Content))
                    if (!sub.isNullOrBlank()) Text(sub, color = Color.White.copy(alpha = .82f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            actions()
        }
    }
}

@Composable
internal fun ChIcon(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = Color.White, size: Dp = 44.dp) {
    Box(Modifier.size(size).tap(CircleShape, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** Coloured circle with initials when there's no photo; a green dot when online. */
@Composable
internal fun ChAvatar(url: String?, name: String, id: String, size: Dp = 52.dp, online: Boolean = false) {
    Box(Modifier.size(size)) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(ChatLook.nameColor(id.ifBlank { name })), contentAlignment = Alignment.Center) {
            val ini = name.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifBlank { "?" }
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
    Surface(color = ChatLook.bar, contentColor = Color.White, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ChIcon(Icons.AutoMirrored.Filled.ArrowBack, "Close search", onClose)
            TextField(q, onQ, Modifier.weight(1f).firstFocus(always = true), singleLine = true, placeholder = { Text(hint, color = Color.White.copy(alpha = .7f)) },
                textStyle = TextStyle(color = Color.White, fontSize = 17.sp, textDirection = TextDirection.Content),
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, cursorColor = Color.White,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
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
internal fun ChSearchResults(q: String, rooms: List<RoomInfo>) {
    var msgs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var groups by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(q) {
        msgs = emptyList(); groups = emptyList()
        if (q.trim().length < 2 || Session.me == null) return@LaunchedEffect
        delay(400); busy = true
        runCatching { msgs = chObjs(Api.get("/api/chat?search=" + chEnc(q.trim())).optJSONArray("messages")).take(40) }
        runCatching { val mine = rooms.map { it.room.id }.toSet(); groups = chObjs(Api.get("/api/chat/rooms?search=" + chEnc(q.trim())).optJSONArray("rooms")).filter { it.str2("id") !in mine } }
        busy = false
    }
    val hits = remember(q, rooms) { if (q.isBlank()) rooms else rooms.filter { it.room.name.contains(q.trim(), true) || it.room.preview.contains(q.trim(), true) } }
    LazyColumn(Modifier.fillMaxSize()) {
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (hits.isNotEmpty()) { item { ChSection("Chats") }; items(hits, key = { "r" + it.room.id }) { ChRoomRow(it, Modifier) } }
        if (groups.isNotEmpty()) {
            item { ChSection("Public groups") }
            items(groups, key = { "g" + it.str2("id") }) { g ->
                ChRow(g.str2("nick"), "${g.optInt("members")} members · tap to view", { ChAvatar(Api.abs(g.str2("photo_url").ifBlank { null }), g.str2("nick"), g.str2("id")) }) {
                    Nav.open(Screen.Chat(ChatApi.roomOf(g.str2("id"), "group", g.str2("nick"), Api.abs(g.str2("photo_url").ifBlank { null }))))
                }
            }
        }
        if (msgs.isNotEmpty()) {
            item { ChSection("Messages") }
            items(msgs, key = { "m" + it.str2("id") }) { m ->
                ChRow(m.str2("room_name").ifBlank { m.str2("sender_nick") }, m.str2("sender_nick") + ": " + m.str2("text").take(120), { ChAvatar(null, m.str2("sender_nick"), m.str2("sender_id"), 44.dp) },
                    trail = { Text(chatTime(m.str2("created_at")), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }) {
                    val ri = rooms.firstOrNull { it.room.id == m.str2("room_id") }
                    Nav.open(Screen.Chat(ri?.room ?: ChatApi.roomOf(m.str2("room_id"), m.str2("room_type").ifBlank { "group" }, m.str2("room_name"))))
                }
            }
        }
        if (q.trim().length >= 2 && !busy && hits.isEmpty() && msgs.isEmpty() && groups.isEmpty()) item { ChEmpty(Icons.Filled.SearchOff, "Nothing found for “$q”") }
    }
}

internal fun JSONObject.str2(k: String): String = if (isNull(k)) "" else optString(k, "")

@Composable
internal fun ChSection(t: String) {
    Text(t, Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

internal fun chNowIso(): String = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())

/** A message bubble: 16 sp text, the time (and ✓✓ for mine) inside the bubble, a tail on the first of a run, reply quote, reactions under it.
 *  Touch: tap opens media/links, long-press opens the actions. Keypad: OK opens the actions (with "Open" first for media). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChBubble(m: ChatMsg, mine: Boolean, group: Boolean, first: Boolean, reacts: ChReact?, reply: ChatMsg?, highlight: Boolean, privateChat: Boolean, modifier: Modifier,
                   onMedia: (String) -> Unit, onLong: () -> Unit, onReact: (String) -> Unit, onReplyTap: (String) -> Unit) {
    val c = ctx()
    if (m.isSystem) {
        Box(modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Text(m.text, fontSize = 13.sp, color = ChatLook.sub(), textAlign = TextAlign.Center, style = TextStyle(textDirection = TextDirection.Content),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(ChatLook.theirs().copy(alpha = .9f)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        return
    }
    val bg = if (mine) ChatLook.mine() else ChatLook.theirs()
    val fg = ChatLook.ink()
    val link = remember(m.text) { urlRegex.find(m.text)?.value }
    val maxW = (LocalConfiguration.current.screenWidthDp * 0.82f).dp.coerceAtMost(440.dp)
    val tail = 4.dp; val r = 14.dp
    val shape = if (mine) RoundedCornerShape(r, if (first) tail else r, r, r) else RoundedCornerShape(if (first) tail else r, r, r, r)
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    fun open() {
        when {
            m.viewOnce && !mine && m.media != null -> onMedia(m.media)
            m.isVideo || m.isImage -> (m.media ?: m.local)?.let(onMedia)
            m.isAudio -> m.media?.let { VoicePlayer.toggle(c, m.id, it) }
            m.media != null -> openUrl(c, m.media)
            link != null -> openUrl(c, link)
            else -> onLong()
        }
    }
    Column(modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = if (first) 5.dp else 1.dp, bottom = 1.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Row(verticalAlignment = Alignment.Top) {
            if (!mine && group) { if (first) ChAvatar(m.photo, m.nick, m.senderId, 30.dp) else Spacer(Modifier.width(30.dp)); Spacer(Modifier.width(6.dp)) }
            Column(Modifier.widthIn(min = 64.dp, max = maxW).clip(shape).background(if (highlight) Color(0xFFFFF59D) else bg)
                .then(if (focused && Keys.keypad) Modifier.border(2.dp, Gold, shape) else Modifier)
                .combinedClickable(interactionSource = src, indication = androidx.compose.material3.ripple(), onClick = { if (Keys.keypad) onLong() else open() }, onLongClick = onLong)
                .padding(horizontal = 4.dp, vertical = 4.dp)) {
                if (!mine && group && first) Text(m.nick, Modifier.padding(horizontal = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ChatLook.nameColor(m.senderId), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (m.text.endsWith(" ↩ Forwarded")) Text("↪ Forwarded", Modifier.padding(horizontal = 6.dp), fontSize = 12.sp, color = ChatLook.sub(), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                if (reply != null || m.replyToId != null) {
                    Row(Modifier.padding(2.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .05f)).tap(RoundedCornerShape(8.dp)) { m.replyToId?.let(onReplyTap) }) {
                        Box(Modifier.width(4.dp).heightIn(min = 40.dp).background(ChatLook.nameColor(reply?.senderId ?: "")))
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(reply?.nick ?: "Message", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ChatLook.nameColor(reply?.senderId ?: ""), maxLines = 1)
                            Text(reply?.preview ?: "Original message", fontSize = 13.sp, color = ChatLook.sub(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                        }
                    }
                }
                when {
                    m.viewOnce && !mine && m.media != null -> Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Visibility, null, tint = ChatLook.bar); Text("  View once · tap to open", color = fg, fontSize = 15.sp)
                    }
                    m.isSticker -> AsyncImage(m.stickerUrl, null, Modifier.size(140.dp))
                    m.isImage -> AsyncImage(m.media ?: m.local, null, contentScale = ContentScale.Fit, modifier = Modifier.widthIn(min = 160.dp, max = maxW).heightIn(min = 90.dp, max = 300.dp).clip(RoundedCornerShape(10.dp)))
                    m.isVideo -> ChVideoThumb(m.media ?: m.local!!, maxW)
                    m.isAudio -> ChVoice(m, fg)
                    m.isPoll -> ChPoll(m.text)
                    m.isFile -> Row(Modifier.padding(6.dp).widthIn(max = maxW), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(ChatLook.bar.copy(alpha = .15f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Description, null, tint = ChatLook.bar) }
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(m.fileName, color = fg, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(m.fileName.substringAfterLast('.', "file").uppercase().take(5) + " · tap to open", color = ChatLook.sub(), fontSize = 12.sp)
                        }
                    }
                }
                val cap = m.caption.removeSuffix(" ↩ Forwarded")
                val bigEmoji = cap.length <= 8 && cap.isNotBlank() && cap.none { it.isLetterOrDigit() } && m.media == null
                if (cap.isNotBlank()) Text(cap, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = if (link != null && cap.trim() == link) Color(0xFF1E88E5) else fg,
                    fontSize = if (bigEmoji) 34.sp else 16.sp, lineHeight = if (bigEmoji) 40.sp else 21.sp, style = TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Start))
                Row(Modifier.align(Alignment.End).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (m.edited) Text("edited  ", fontSize = 11.sp, color = ChatLook.sub())
                    Text(if (m.pending) "Sending…" else if (m.failed) "Not sent · tap" else clock(m.createdAt), fontSize = 11.sp, color = if (m.failed) Color(0xFFD32F2F) else ChatLook.sub())
                    if (mine && !m.pending && !m.failed) {
                        val seen = if (privateChat) m.read else m.seen > 0
                        Icon(if (seen || (!privateChat && m.seen > 0)) Icons.Filled.DoneAll else Icons.Filled.Done, if (seen) "Read" else "Sent", Modifier.padding(start = 3.dp).size(16.dp), tint = if (seen) ChatLook.tick else ChatLook.sub())
                        if (!privateChat && m.seen > 0) Text(" ${m.seen}", fontSize = 11.sp, color = ChatLook.sub())
                    }
                }
            }
        }
        val rc = reacts?.counts?.filterValues { it > 0 }
        if (!rc.isNullOrEmpty()) Row(Modifier.padding(start = if (!mine && group) 40.dp else 4.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            rc.entries.sortedByDescending { it.value }.take(5).forEach { (e, n) ->
                val my = reacts?.mine == e
                Text("$e $n", fontSize = 13.sp, color = fg, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (my) ChatLook.bar.copy(alpha = .22f) else ChatLook.theirs())
                    .border(0.5.dp, ChatLook.sub().copy(alpha = .3f), RoundedCornerShape(12.dp)).tap(RoundedCornerShape(12.dp)) { onReact(e) }.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
    }
}

/** Video message: a real frame from the video with a play button (never a black box). */
@Composable
private fun ChVideoThumb(url: String, maxW: Dp) {
    val c = ctx()
    var bmp by remember(url) { mutableStateOf(VideoThumbs.cached(url)) }
    LaunchedEffect(url) { if (bmp == null) bmp = VideoThumbs.load(c, url) }
    Box(Modifier.width(maxW.coerceAtMost(260.dp)).height(180.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF263238)), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.size(54.dp).clip(CircleShape).background(Color.Black.copy(alpha = .5f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, "Play video", tint = Color.White, modifier = Modifier.size(34.dp))
        }
        if (bmp == null) Text("Video", Modifier.align(Alignment.BottomStart).padding(8.dp), color = Color.White, fontSize = 12.sp)
    }
}

/** Voice note / audio: play-pause, a waveform that fills as it plays, the length. */
@Composable
private fun ChVoice(m: ChatMsg, fg: Color) {
    val c = ctx()
    val me = VoicePlayer.playing == m.id
    LaunchedEffect(me) { while (me) { VoicePlayer.tick(); delay(200) } }
    val peaks = remember(m.text) { m.text.substringAfter('|', "").split(',').mapNotNull { it.trim().toIntOrNull() }.ifEmpty { List(32) { i -> 20 + (i * 37 % 60) } } }
    Row(Modifier.padding(4.dp).width(230.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(ChatLook.bar).tap(CircleShape) { m.media?.let { VoicePlayer.toggle(c, m.id, it) } }, contentAlignment = Alignment.Center) {
            Icon(if (me && !VoicePlayer.paused) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (me) "Pause" else "Play", tint = Color.White)
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Row(Modifier.height(26.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val n = peaks.size.coerceAtMost(32); val done = if (me) (VoicePlayer.progress * n).toInt() else 0
                for (i in 0 until n) Box(Modifier.weight(1f).height((4 + peaks[i * peaks.size / n] * 22 / 100).dp).clip(RoundedCornerShape(2.dp)).background(if (i < done) ChatLook.bar else ChatLook.sub().copy(alpha = .45f)))
            }
            Text(if (m.isVoice) (m.voiceDur.ifBlank { "Voice message" }) else m.fileName, fontSize = 12.sp, color = ChatLook.sub(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (m.isVoice) Icon(Icons.Filled.Mic, null, Modifier.padding(start = 4.dp).size(18.dp), tint = ChatLook.bar)
    }
}

/** A poll message (the site stores the poll id in the message text): options with live percentages; tap to vote. */
@Composable
private fun ChPoll(id: String) {
    val c = ctx(); val scope = rememberCoroutineScope()
    var poll by remember(id) { mutableStateOf<JSONObject?>(null) }
    suspend fun load() { runCatching { poll = withContext(Dispatchers.IO) { Api.get("/api/polls?id=" + chEnc(id)).optJSONObject("poll") } } }
    LaunchedEffect(id) { load() }
    val p = poll
    Column(Modifier.padding(6.dp).widthIn(min = 220.dp, max = 300.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Poll, null, Modifier.size(16.dp), tint = ChatLook.bar); Text(" POLL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ChatLook.bar) }
        if (p == null) { Text("Loading poll…", fontSize = 14.sp, color = ChatLook.sub()); return@Column }
        Text(p.str2("question"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ChatLook.ink(), style = TextStyle(textDirection = TextDirection.Content))
        val closed = p.optBoolean("closed")
        chObjs(p.optJSONArray("options")).forEach { o ->
            val pct = o.optInt("pct"); val my = o.optBoolean("my_vote")
            Column(Modifier.fillMaxWidth().padding(top = 6.dp).tap { if (!closed && Session.me != null) scope.launch {
                runCatching { Api.put("/api/polls", JSONObject().put("id", id).put("vote", JSONArray().put(o.opt("id")))) }.onFailure { toast(c, Api.friendly(it)) }; load() } }.padding(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (my) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null, Modifier.size(18.dp), tint = if (my) ChatLook.bar else ChatLook.sub())
                    Text("  " + o.str2("text"), Modifier.weight(1f), fontSize = 15.sp, color = ChatLook.ink(), style = TextStyle(textDirection = TextDirection.Content))
                    Text("$pct%", fontSize = 13.sp, color = ChatLook.sub())
                }
                LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(5.dp).clip(RoundedCornerShape(3.dp)), color = ChatLook.bar, trackColor = ChatLook.sub().copy(alpha = .2f))
            }
        }
        Text(if (closed) "Poll closed" else "Tap an option to vote", fontSize = 12.sp, color = ChatLook.sub(), modifier = Modifier.padding(top = 4.dp))
    }
}

/** Long-press (or keypad OK) on a message: quick reactions, then Reply / Copy / Edit / Forward / Pin / Save / Share / Report / Delete — the site's menu. */
@Composable
internal fun ChActions(m: ChatMsg, mine: Boolean, canPin: Boolean, isPinned: Boolean, canDelete: Boolean, canWrite: Boolean, signedIn: Boolean, myReact: String?, onClose: () -> Unit, onAct: (String) -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(vertical = 8.dp).widthIn(max = 360.dp)) {
                Text(m.preview.ifBlank { "Message" }, Modifier.padding(horizontal = 18.dp, vertical = 6.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content))
                if (signedIn && !m.failed) Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    QUICK_REACTS.forEachIndexed { i, e ->
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

/** Emoji panel under the composer, with the site's categories. */
@Composable
internal fun ChEmojiPanel(onEmoji: (String) -> Unit) {
    var cat by remember { mutableStateOf(0) }
    val list = remember(cat) { EMOJI_SETS[cat].second.split(' ').filter { it.isNotBlank() } }
    Column(Modifier.fillMaxWidth().height(230.dp).background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            EMOJI_SETS.forEachIndexed { i, (icon, _) ->
                Box(Modifier.size(44.dp).tap(RoundedCornerShape(8.dp)) { cat = i }.background(if (i == cat) ChatLook.bar.copy(alpha = .15f) else Color.Transparent), contentAlignment = Alignment.Center) { Text(icon, fontSize = 20.sp) }
            }
        }
        HorizontalDivider(thickness = 0.5.dp)
        LazyVerticalGrid(GridCells.Adaptive(44.dp), Modifier.fillMaxSize()) {
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
