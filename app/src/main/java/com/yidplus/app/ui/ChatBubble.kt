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
import androidx.compose.foundation.clickable
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

/* ═════════════════════════ One message bubble: every type the site draws, swipe to reply, link cards ═════════════════════════ */

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChBubble(m: ChatMsg, mine: Boolean, group: Boolean, first: Boolean, reacts: ChReact?, reply: ChatMsg?, highlight: Boolean, privateChat: Boolean, modifier: Modifier,
                   onMedia: (String) -> Unit, onLong: () -> Unit, onReact: (String) -> Unit, onReplyTap: (String) -> Unit,
                   onSwipeReply: (() -> Unit)? = null, onJoin: (String) -> Unit = {},
                   onNick: (() -> Unit)? = null, onMention: (String) -> Unit = {}, selected: Boolean? = null, onSelect: (() -> Unit)? = null) {
    val c = ctx()
    if (m.isCentred) { ChCentredLine(m, modifier); return }
    val bg = if (mine) ChatLook.mine() else ChatLook.theirs()
    val fg = ChatLook.ink()
    val link = remember(m.text) { if (m.isCentred || m.deleted || m.isSticker || m.isVoice || m.isVoiceText) null else urlRegex.find(m.text)?.value?.trimEnd('.', ',', ')', '!', '?') }
    val maxW = (LocalConfiguration.current.screenWidthDp * 0.82f).dp.coerceAtMost(440.dp)
    val tail = 4.dp; val r = 14.dp
    val shape = if (mine) RoundedCornerShape(r, if (first) tail else r, r, r) else RoundedCornerShape(if (first) tail else r, r, r, r)
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val hasMedia = !m.deleted && (m.isImage || m.isVideo || (m.viewOnce && !mine && m.media != null))
    fun open() {
        when {
            m.deleted -> onLong()
            m.viewOnce && !mine && m.media != null -> onMedia(m.media)
            m.isVideo || m.isImage -> (m.media ?: m.local)?.let(onMedia)
            m.isSticker -> m.stickerUrl?.let(onMedia)
            m.isAudio -> m.media?.let { ChatVoicePlayer.toggle(c, m.id, it) }
            m.media != null -> chOpenLink(c, m.media)
            link != null -> chOpenLink(c, link)
            Keys.keypad -> onLong()
        }
    }
    // Swipe a bubble to the right to reply (WhatsApp): the reply arrow fades in, a tick of haptics at the point of no return, release = reply.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val hap = androidx.compose.ui.platform.LocalHapticFeedback.current
    val trigger = with(density) { 56.dp.toPx() }; val maxPx = with(density) { 80.dp.toPx() }
    var dx by remember(m.id) { mutableStateOf(0f) }
    var dragging by remember(m.id) { mutableStateOf(false) }
    val off by androidx.compose.animation.core.animateFloatAsState(dx, if (dragging) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(), label = "swipe")
    val swipeCb by rememberUpdatedState(onSwipeReply)
    val swipe = if (onSwipeReply == null) Modifier else Modifier.pointerInput(m.id) {
        var fired = false
        detectHorizontalDragGestures(
            onDragStart = { fired = false; dragging = true },
            onDragEnd = { dragging = false; if (dx >= trigger) swipeCb?.invoke(); dx = 0f },
            onDragCancel = { dragging = false; dx = 0f },
        ) { ch, amount ->
            val n = (dx + amount).coerceIn(0f, maxPx)
            if (n > 0f) ch.consume()
            dx = n
            if (!fired && dx >= trigger) { fired = true; hap.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) }
        }
    }
    Box(Modifier.fillMaxWidth().then(swipe)) {
        if (off > 4f) Box(Modifier.align(Alignment.CenterStart).padding(start = 10.dp).size(34.dp).graphicsLayer { alpha = (off / trigger).coerceIn(0f, 1f); scaleX = 0.6f + 0.4f * (off / trigger).coerceIn(0f, 1f); scaleY = scaleX }
            .clip(CircleShape).background(ChatLook.theirs().copy(alpha = .95f)), contentAlignment = Alignment.Center) {
            Icon(Sym.Reply, "Reply", Modifier.size(20.dp), tint = ChatLook.sub())
        }
    Column(Modifier.fillMaxWidth().absoluteOffset { IntOffset(off.toInt(), 0) }.background(if (selected == true) ChatLook.bar.copy(alpha = .18f) else Color.Transparent).padding(start = 8.dp, end = 8.dp, top = if (first) 5.dp else 1.dp, bottom = 1.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Row(verticalAlignment = Alignment.Top) {
            if (selected != null) Icon(if (selected) Sym.CheckCircle else Sym.RadioButtonUnchecked, if (selected) "Selected" else "Not selected",
                Modifier.padding(end = 6.dp, top = 6.dp).size(22.dp).clickable { onSelect?.invoke() }, tint = if (selected) ChatLook.bar else ChatLook.sub())
            if (!mine && group) { if (first) ChAvatar(m.photo, m.nick, m.senderId, 30.dp) else Spacer(Modifier.width(30.dp)); Spacer(Modifier.width(6.dp)) }
            Column(modifier.widthIn(min = 72.dp, max = maxW).clip(shape).background(if (highlight) Color(0xFFFFF59D) else if (m.isSticker) Color.Transparent else bg)
                .then(if (focused && Keys.keypad) Modifier.border(2.dp, Gold, shape) else Modifier)
                .combinedClickable(interactionSource = src, indication = androidx.compose.material3.ripple(), onClick = { if (onSelect != null && selected != null) onSelect() else open() }, onLongClick = { if (onSelect != null && selected != null) onSelect() else onLong() },
                    onDoubleClick = if (selected == null && !m.pending && !m.failed && !m.deleted) ({ onReact("❤️") }) else null)
                .padding(horizontal = 4.dp, vertical = 4.dp)) {
                if (!mine && group && first) Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(m.nick, Modifier.weight(1f, fill = false).then(if (onNick != null) Modifier.clickable(onClick = onNick) else Modifier), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ChatLook.nameColor(m.senderId), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                    if (m.title.isNotBlank()) Text(m.title, Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp)).background(ChatLook.bar.copy(alpha = .12f)).padding(horizontal = 6.dp), fontSize = 11.sp, color = ChatLook.bar, maxLines = 1)
                }
                if (m.forwarded && !m.deleted) Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Sym.Forward, null, Modifier.size(14.dp), tint = ChatLook.sub())
                    Text(" Forwarded", fontSize = 12.sp, color = ChatLook.sub(), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
                if (!m.deleted && (reply != null || m.replyToId != null)) ChReplyQuote(reply, m.replyToId, onReplyTap)
                when {
                    m.deleted -> Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Sym.Block, null, Modifier.size(16.dp), tint = ChatLook.sub())
                        Text(" This message was deleted", fontSize = 15.sp, color = ChatLook.sub(), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    }
                    m.viewOnce && !mine && m.media != null -> Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (m.opened) Sym.VisibilityOff else Sym.Visibility, null, tint = ChatLook.bar)
                        Text(if (m.opened) "  Opened" else "  View once · tap to open", color = fg, fontSize = 15.sp)
                    }
                    m.isSticker -> m.stickerUrl?.let { AsyncImage(it, "Sticker", Modifier.size(130.dp)) } ?: Text(m.text.ifBlank { "😊" }, fontSize = 48.sp)
                    m.isUnavailable -> Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp).widthIn(min = 150.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (m.isVoice) Sym.MicOff else Sym.BrokenImage, null, Modifier.size(20.dp), tint = ChatLook.sub())
                        Text("  " + if (m.isVoice) "Voice message unavailable" else m.caption.ifBlank { "Media unavailable" }, fontSize = 14.sp, color = ChatLook.sub(), style = TextStyle(textDirection = TextDirection.Content))
                    }
                    m.isImage -> AsyncImage(m.media ?: m.local, "Photo", contentScale = ContentScale.Crop, modifier = Modifier.widthIn(min = 180.dp, max = maxW.coerceAtMost(300.dp)).heightIn(min = 120.dp, max = 320.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF263238)))
                    m.isVideo -> ChVideoThumb(m.media ?: m.local!!, maxW)
                    m.isAudio -> CsVoiceBubble(m)
                    m.isVoiceText -> Row(Modifier.padding(4.dp).width(220.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(ChatLook.bar.copy(alpha = .5f)).tap(CircleShape) { toast(c, "Audio not available") }, contentAlignment = Alignment.Center) { Icon(Sym.PlayArrow, null, tint = Color.White) }
                        Row(Modifier.weight(1f).height(26.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (i in 0 until 24) Box(Modifier.weight(1f).height((5 + (i * 37 % 17)).dp).clip(RoundedCornerShape(2.dp)).background(ChatLook.sub().copy(alpha = .45f)))
                        }
                        Text(m.text.substringBefore('|').trim().take(8).ifBlank { "0:00" }, fontSize = 12.sp, color = ChatLook.sub())
                    }
                    m.isPoll -> CsPollBubble(m.text)
                    m.isFile -> Row(Modifier.padding(6.dp).widthIn(min = 180.dp, max = maxW), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(ChatLook.bar.copy(alpha = .15f)), contentAlignment = Alignment.Center) { Icon(Sym.Description, null, tint = ChatLook.bar) }
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(m.fileName, color = fg, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, style = TextStyle(textDirection = TextDirection.Content))
                            Text(m.fileName.substringAfterLast('.', "file").uppercase().take(5) + " · tap to open", color = ChatLook.sub(), fontSize = 12.sp)
                        }
                    }
                }
                val cap = if (m.deleted) "" else m.caption
                val bigEmoji = cap.length <= 8 && cap.isNotBlank() && cap.none { it.isLetterOrDigit() } && m.media == null
                if (cap.isNotBlank()) Text(csFormat(cap, { u -> chOpenLink(c, u) }, onMention), Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = fg,
                    fontSize = if (bigEmoji) 34.sp else 16.sp, lineHeight = if (bigEmoji) 40.sp else 21.sp, style = TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Start))
                else if (!m.deleted && !hasMedia && !m.isSticker && !m.isAudio && !m.isPoll && !m.isFile && !m.isUnavailable && !m.isVoiceText)
                    Text("💬 Message", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = ChatLook.sub(), fontSize = 15.sp)
                if (link != null) ChLinkCard(link, mine, onJoin)
                Row(Modifier.align(Alignment.End).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (m.edited && !m.deleted) Text("edited  ", fontSize = 11.sp, color = ChatLook.sub())
                    Text(if (m.pending) "Sending…" else if (m.failed) "Not sent · tap" else clock(m.createdAt), fontSize = 11.sp, color = if (m.failed) Color(0xFFD32F2F) else ChatLook.sub())
                    if (mine && !m.pending && !m.failed && !m.deleted) {
                        // The site: ✓ sent, ✓✓ blue read in a DM; in a group "👁 n" once n members have seen it.
                        val seen = if (privateChat) m.read else m.seen > 0
                        if (!privateChat && m.seen > 0) Text(" 👁 ${m.seen}", fontSize = 11.sp, color = ChatLook.sub())
                        else Icon(if (seen) Sym.DoneAll else Sym.Done, if (seen) "Read" else "Sent", Modifier.padding(start = 3.dp).size(16.dp), tint = if (seen) ChatLook.tick else ChatLook.sub())
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
}

/** System notes ("Yanky joined") and call logs ("📞 Missed voice call"), centred like the site; missed calls in red. */
@Composable
private fun ChCentredLine(m: ChatMsg, modifier: Modifier) {
    val missed = m.isCall && m.text.contains("Missed", true)
    Box(modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.clip(RoundedCornerShape(8.dp)).background(ChatLook.theirs().copy(alpha = .92f)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (m.isCall && !m.text.trimStart().startsWith("📞") && !m.text.trimStart().startsWith("📹"))
                Icon(if (missed) Sym.PhoneMissed else Sym.Call, null, Modifier.size(15.dp).padding(end = 4.dp), tint = if (missed) Color(0xFFE53935) else ChatLook.sub())
            Text(m.text.ifBlank { if (m.isCall) "📞 Call" else "" } + (if (m.isCall && m.createdAt != null) " · " + clock(m.createdAt) else ""), fontSize = 13.sp,
                color = if (missed) Color(0xFFE53935) else ChatLook.sub(), textAlign = TextAlign.Center, style = TextStyle(textDirection = TextDirection.Content))
        }
    }
}

@Composable
private fun ChReplyQuote(reply: ChatMsg?, replyId: String?, onReplyTap: (String) -> Unit) {
    val me = Session.me?.id
    val nc = ChatLook.nameColor(reply?.senderId ?: "")
    Row(Modifier.padding(2.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .05f)).tap(RoundedCornerShape(8.dp)) { replyId?.let(onReplyTap) }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(44.dp).background(nc))
        Column(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(if (reply == null) "Message" else if (reply.senderId == me) "You" else reply.nick.ifBlank { "User" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = nc, maxLines = 1, style = TextStyle(textDirection = TextDirection.Content))
            Text(reply?.preview ?: "Original message", fontSize = 13.sp, color = ChatLook.sub(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
        }
        if (reply != null && reply.isImage && !reply.viewOnce) AsyncImage(reply.media ?: reply.local, null, Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
    }
}

/** Links in blue (tap the bubble or the card to open; yidplus links stay in the app). */
internal fun chLinkify(t: String): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    var last = 0
    for (mt in urlRegex.findAll(t)) {
        append(t.substring(last, mt.range.first))
        pushStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFF1E88E5), textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))
        append(mt.value); pop(); last = mt.range.last + 1
    }
    if (last < t.length) append(t.substring(last))
}

/** WhatsApp/Telegram link card: picture, site, title, description (the site's /api/link-preview); a YID PLUS invite shows the group's photo, name, members and Join. */
@Composable
internal fun ChLinkCard(url: String, mine: Boolean, onJoin: (String) -> Unit) {
    val c = ctx()
    var lk by remember(url) { mutableStateOf(ChLinks.cached(url)) }
    LaunchedEffect(url) { if (!ChLinks.known(url)) lk = ChLinks.load(url) }
    val l = lk ?: return
    val cardBg = if (mine) Color.Black.copy(alpha = .06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f)
    if (l.joinCode != null) {
        Row(Modifier.padding(2.dp).widthIn(min = 220.dp).clip(RoundedCornerShape(10.dp)).background(cardBg).tap(RoundedCornerShape(10.dp)) { Nav.open(ChJoinLink(url)) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChAvatar(l.roomPhoto, l.roomName, l.joinCode, 46.dp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(l.roomName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ChatLook.ink(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                Text("${l.roomMembers} members · ${l.roomType}", fontSize = 12.sp, color = ChatLook.sub(), maxLines = 1)
            }
            Text("Join", Modifier.clip(RoundedCornerShape(8.dp)).background(ChatLook.bar).tap(RoundedCornerShape(8.dp)) { onJoin(l.joinCode) }.padding(horizontal = 14.dp, vertical = 7.dp), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        return
    }
    Column(Modifier.padding(2.dp).widthIn(max = 300.dp).clip(RoundedCornerShape(10.dp)).background(cardBg).tap(RoundedCornerShape(10.dp)) { chOpenLink(c, url) }) {
        if (l.image != null) Box(contentAlignment = Alignment.Center) {
            AsyncImage(l.image, null, Modifier.fillMaxWidth().height(150.dp).background(Color(0xFF263238)), contentScale = ContentScale.Crop)
            if (ChLinks.youtubeId(url) != null) Box(Modifier.size(46.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f)), contentAlignment = Alignment.Center) { Icon(Sym.PlayArrow, "Play", tint = Color.White) }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(l.host, fontSize = 12.sp, color = ChatLook.sub(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(l.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ChatLook.ink(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
            if (l.desc.isNotBlank()) Text(l.desc, fontSize = 13.sp, color = ChatLook.sub(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
        }
    }
}

/** Links stay in the app where they can: photos open the viewer, YID PLUS invites the join page, yidplus.com its own screen (or the in-app page); other sites the browser. */
internal fun chOpenLink(c: Context, url: String) {
    val u = runCatching { Uri.parse(url) }.getOrNull()
    val host = u?.host?.lowercase()?.removePrefix("www.") ?: ""
    val path = u?.path?.lowercase() ?: ""
    val yid = host == "yidplus.com" || host.endsWith(".yidplus.com") || host.endsWith("yidplus.pages.dev")
    when {
        Regex("""\.(jpe?g|png|webp|gif|heic|avif)$""").containsMatchIn(path) || (yid && path.startsWith("/api/media/") && !Regex("""\.(mp4|webm|mov|m4v|mp3|m4a|ogg|pdf|zip|docx?)$""").containsMatchIn(path)) ->
            openGallery(listOf(GItem(url, false)), url)
        Regex("""\.(mp4|webm|mov|m4v)$""").containsMatchIn(path) -> openGallery(listOf(GItem(url, true)), url)
        ChLinks.joinCode(url) != null -> Nav.open(ChJoinLink(url))
        host == "yidplus.com" -> runCatching { com.yidplus.app.MainActivity.route(c, url) }.onFailure { Nav.open(Screen.Web(url)) }
        yid -> Nav.open(Screen.Web(url))
        else -> openUrl(c, url)
    }
}

/** First-ever open of a chat: a few bubble shapes for a moment instead of a spinner or a grey page. */
@Composable
internal fun ChBubbleSkeleton() {
    val k = ChatLook.theirs().copy(alpha = .7f); val k2 = ChatLook.mine().copy(alpha = .7f)
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.Bottom) {
        listOf(0.55f to false, 0.4f to false, 0.62f to true, 0.35f to true, 0.5f to false).forEach { (w, me) ->
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = if (me) Alignment.CenterEnd else Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth(w).height(38.dp).clip(RoundedCornerShape(12.dp)).background(if (me) k2 else k))
            }
        }
    }
}

/** Video message: a real frame from the video with a play button (never a black box). */
@Composable
private fun ChVideoThumb(url: String, maxW: Dp) {
    val c = ctx()
    var bmp by remember(url) { mutableStateOf(ChatVideoThumbs.cached(url)) }
    LaunchedEffect(url) { if (bmp == null) bmp = ChatVideoThumbs.load(c, url) }
    Box(Modifier.width(maxW.coerceAtMost(260.dp)).height(180.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF263238)), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.size(54.dp).clip(CircleShape).background(Color.Black.copy(alpha = .5f)), contentAlignment = Alignment.Center) {
            Icon(Sym.PlayArrow, "Play video", tint = Color.White, modifier = Modifier.size(34.dp))
        }
        if (bmp == null) Text("Video", Modifier.align(Alignment.BottomStart).padding(8.dp), color = Color.White, fontSize = 12.sp)
    }
}

/** Voice note / audio: play-pause, a waveform that fills as it plays, the length. */
@Composable
private fun ChVoice(m: ChatMsg, fg: Color) {
    val c = ctx()
    val me = ChatVoicePlayer.playing == m.id
    LaunchedEffect(me) { while (me) { ChatVoicePlayer.tick(); delay(200) } }
    val peaks = remember(m.text) { m.text.substringAfter('|', "").split(',').mapNotNull { it.trim().toIntOrNull() }.ifEmpty { List(32) { i -> 20 + (i * 37 % 60) } } }
    Row(Modifier.padding(4.dp).width(230.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(ChatLook.bar).tap(CircleShape) { m.media?.let { ChatVoicePlayer.toggle(c, m.id, it) } }, contentAlignment = Alignment.Center) {
            Icon(if (me && !ChatVoicePlayer.paused) Sym.Pause else Sym.PlayArrow, if (me) "Pause" else "Play", tint = Color.White)
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Row(Modifier.height(26.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val n = peaks.size.coerceAtMost(32); val done = if (me) (ChatVoicePlayer.progress * n).toInt() else 0
                for (i in 0 until n) Box(Modifier.weight(1f).height((4 + peaks[i * peaks.size / n] * 22 / 100).dp).clip(RoundedCornerShape(2.dp)).background(if (i < done) ChatLook.bar else ChatLook.sub().copy(alpha = .45f)))
            }
            Text(if (m.isVoice) (m.voiceDur.ifBlank { "Voice message" }) else m.fileName, fontSize = 12.sp, color = ChatLook.sub(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (m.isVoice) Icon(Sym.Mic, null, Modifier.padding(start = 4.dp).size(18.dp), tint = ChatLook.bar)
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
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Sym.Poll, null, Modifier.size(16.dp), tint = ChatLook.bar); Text(" POLL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ChatLook.bar) }
        if (p == null) { Text("Loading poll…", fontSize = 14.sp, color = ChatLook.sub()); return@Column }
        Text(p.chStr("question"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ChatLook.ink(), style = TextStyle(textDirection = TextDirection.Content))
        val closed = p.optBoolean("closed")
        chObjs(p.optJSONArray("options")).forEach { o ->
            val pct = o.optInt("pct"); val my = o.optBoolean("my_vote")
            Column(Modifier.fillMaxWidth().padding(top = 6.dp).tap { if (!closed && Session.me != null) scope.launch {
                runCatching { Api.put("/api/polls", JSONObject().put("id", id).put("vote", JSONArray().put(o.opt("id")))) }.onFailure { toast(c, Api.friendly(it)) }; load() } }.padding(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (my) Sym.CheckCircle else Sym.RadioButtonUnchecked, null, Modifier.size(18.dp), tint = if (my) ChatLook.bar else ChatLook.sub())
                    Text("  " + o.chStr("text"), Modifier.weight(1f), fontSize = 15.sp, color = ChatLook.ink(), style = TextStyle(textDirection = TextDirection.Content))
                    Text("$pct%", fontSize = 13.sp, color = ChatLook.sub())
                }
                LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(5.dp).clip(RoundedCornerShape(3.dp)), color = ChatLook.bar, trackColor = ChatLook.sub().copy(alpha = .2f))
            }
        }
        Text(if (closed) "Poll closed" else "Tap an option to vote", fontSize = 12.sp, color = ChatLook.sub(), modifier = Modifier.padding(top = 4.dp))
    }
}

