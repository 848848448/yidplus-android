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
import androidx.compose.ui.focus.focusRequester
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

/* ═════════════════════════ One conversation ═════════════════════════ */

private sealed class ChItem(val key: String) {
    class M(val m: ChatMsg, val first: Boolean) : ChItem(m.id)
    class Day(val label: String, k: String) : ChItem("day-$k")
}

/** One conversation: newest at the bottom, refreshes every few seconds while open; reply, react, edit, forward, pin, voice notes, attachments, emoji. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(room: Room, inPane: Boolean = false) {
    val c = ctx()
    val me = Session.me
    val scope = rememberCoroutineScope()
    val kb = LocalSoftwareKeyboardController.current
    // Instant open (gap 1): the messages we already have are on screen in the very first frame; the network only refreshes them.
    val msgs = remember(room.id) { mutableStateListOf<ChatMsg>().also { l -> ChatCache.get(room.id)?.let { l.addAll(it) } } }
    val inputFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var reacts by remember(room.id) { mutableStateOf<Map<String, ChReact>>(emptyMap()) }
    var typing by remember(room.id) { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var text by rememberSaveable(room.id) { mutableStateOf(ChDrafts.get(c, room.id)) }
    var sending by remember { mutableStateOf(false) }
    var replyTo by remember(room.id) { mutableStateOf<ChatMsg?>(null) }
    var editing by remember(room.id) { mutableStateOf<ChatMsg?>(null) }
    LaunchedEffect(text) { delay(600); if (editing == null) ChDrafts.put(c, room.id, text) }
    var actionFor by remember { mutableStateOf<ChatMsg?>(null) }
    var emojiOpen by remember { mutableStateOf(false) }
    var attachOpen by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var searchOn by remember { mutableStateOf(false) }
    var sq by remember { mutableStateOf("") }
    var hit by remember { mutableStateOf(0) }
    var confirm by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    var reportFor by remember { mutableStateOf<ChatMsg?>(null) }
    var info by remember(room.id) { mutableStateOf(ChatApi.info(room.id)) }
    val roomsRes = ChatApi.rooms
    LaunchedEffect(roomsRes.data) { ChatApi.info(room.id)?.let { info = it } }
    val cacheKey = "chat|" + room.id
    val joinedOk = info?.joined != false
    // An owner looking at someone else's private chat (the site's admin_spectating / view_private_dms): read-only, "Viewing as Admin", never a Join button.
    val spectating = info?.spectating == true || (room.type == "private" && info?.joined == false)
    var lastReadId by remember(room.id) { mutableStateOf<String?>(null) }
    val canWrite = me != null && !room.guestView && (!room.readOnly || info?.canManage == true) && joinedOk && !spectating
    val isGroup = room.type == "group"

    suspend fun pull() {
        try {
            val list = withContext(Dispatchers.IO) {
                val (j, l) = ChatApi.messages(room.id)
                ChatCache.put(room.id, j.optJSONArray("messages"), l)
                l
            }
            val loc: (ChatMsg) -> Boolean = { it.pending || it.failed || it.id.startsWith("tmp-") }
            val pend = msgs.filter(loc).filter { p -> list.none { it.senderId == me?.id && it.text == p.text && it.type == p.type && (parseTime(it.createdAt) ?: 0) >= System.currentTimeMillis() - 180_000 } }
            if (msgs.isEmpty() || list != msgs.filterNot(loc) || pend.size != msgs.count(loc)) {
                msgs.clear(); msgs.addAll(list); msgs.addAll(pend)
            }
            // The site posts /api/chat/read once when the room opens (members only; spectating leaves no trace) — and again here only when something new arrived.
            val newest = list.lastOrNull()?.id
            if (me != null && room.id.isNotBlank() && !room.guestView && joinedOk && !spectating && newest != lastReadId) { lastReadId = newest; runCatching { ChatApi.read(room.id) } }
            error = null; loaded = true
            Perf.shown("Chat")
        } catch (e: Exception) { if (msgs.isEmpty()) error = Api.friendly(e) }
    }
    suspend fun pullReacts() { if (me != null) runCatching { reacts = withContext(Dispatchers.IO) { ChatApi.reactions(room.id) } } }
    LaunchedEffect(room.id) {
        if (msgs.isNotEmpty()) Perf.shown("Chat", fromCache = true)
        if (info == null) launch { roomsRes.load() }
        runCatching { com.yidplus.app.NotifyWorker.cancelChat(c, room.id); com.yidplus.app.ChatNotify.clear(c, room.id) }
        var n = 0
        while (isActive) { pull(); if (n++ % 2 == 0) pullReacts(); delay(if (Net.online) 5_000 else 15_000) }
    }
    LaunchedEffect(room.id, me?.id) {
        if (me == null) return@LaunchedEffect
        while (isActive) { delay(3_500); runCatching { typing = withContext(Dispatchers.IO) { ChatApi.typing(room.id) } } }
    }
    var lastTypingSent by remember { mutableStateOf(0L) }
    LaunchedEffect(text) {
        if (text.isNotBlank() && me != null && System.currentTimeMillis() - lastTypingSent > 4_000) { lastTypingSent = System.currentTimeMillis(); runCatching { withContext(Dispatchers.IO) { ChatApi.sendTyping(room.id) } } }
    }

    fun refreshSoon() { scope.launch { pull(); pullReacts() } }

    fun sendText() {
        val t = text.trim(); if (t.isEmpty() || me == null) return
        val ed = editing
        if (ed != null) {
            text = ""; editing = null
            val i = msgs.indexOfFirst { it.id == ed.id }; if (i >= 0) msgs[i] = msgs[i].copy(text = t, edited = true)
            scope.launch { runCatching { ChatApi.edit(ed.id, t) }.onFailure { toast(c, "Not edited: " + Api.friendly(it)) }; pull() }
            return
        }
        text = ""
        val rt = replyTo; replyTo = null
        val temp = ChatMsg("tmp-" + System.nanoTime(), room.id, me.id, me.nickname, me.photo, "text", t, null, "", chNowIso(), replyToId = rt?.id, pending = true)
        msgs.add(temp)
        scope.launch {
            try {
                val m = ChatApi.sendText(room.id, t, rt?.id)
                val i = msgs.indexOfFirst { it.id == temp.id }
                // No message object in the answer: keep our copy as sent (✓) until the server's own copy shows up.
                if (i >= 0) { if (m != null) msgs[i] = m.copy(photo = me.photo) else msgs[i] = temp.copy(pending = false) }
                pull()
            } catch (e: Exception) {
                val i = msgs.indexOfFirst { it.id == temp.id }; if (i >= 0) msgs[i] = temp.copy(pending = false, failed = true)
                toast(c, Api.friendly(e))
            }
        }
    }
    /** Every attachment goes the website's way: the raw file streamed to /api/chat (type media / file / voice). */
    fun sendFile(uri: Uri?, file: java.io.File?, type: String, name: String, mime: String, t: String = "", localPreview: String? = null) {
        if (me == null) return
        val rt = replyTo; replyTo = null
        val temp = ChatMsg("tmp-" + System.nanoTime(), room.id, me.id, me.nickname, me.photo, type, t, null, name, chNowIso(), pending = true, local = localPreview)
        msgs.add(temp); sending = true
        scope.launch {
            try { ChatApi.upload(c, room.id, uri, file, type, name, mime, t, rt?.id); msgs.remove(temp); pull() }
            catch (e: Exception) { val i = msgs.indexOfFirst { it.id == temp.id }; if (i >= 0) msgs[i] = temp.copy(pending = false, failed = true); toast(c, "Not sent: " + Api.friendly(e)) }
            finally { sending = false; file?.takeIf { it.name.startsWith("cam_") || it.name.startsWith("voice_") }?.delete() }
        }
    }
    fun sendPhoto(uri: Uri?, bmp: Bitmap?) {
        scope.launch {
            val f = runCatching { withContext(Dispatchers.IO) { java.io.File(c.cacheDir, "cam_" + System.nanoTime() + ".jpg").also { it.writeBytes(chShrink(c, uri, bmp)) } } }.getOrElse { toast(c, "Can't read the photo"); return@launch }
            sendFile(null, f, "media", "photo.jpg", "image/jpeg", localPreview = Uri.fromFile(f).toString())
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u: Uri? -> if (u != null) sendPhoto(u, null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { b: Bitmap? -> if (b != null) sendPhoto(null, b) }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u: Uri? ->
        if (u != null) { val n = ChatApi.nameOf(c, u).let { if (it.contains('.')) it else "$it.mp4" }; sendFile(u, null, "media", n, c.contentResolver.getType(u) ?: "video/mp4", localPreview = u.toString()) }
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u: Uri? ->
        if (u != null) { val n = ChatApi.nameOf(c, u); sendFile(u, null, "file", n, c.contentResolver.getType(u) ?: "audio/mpeg", n) }
    }
    val pickDoc = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u: Uri? ->
        if (u != null) {
            val n = ChatApi.nameOf(c, u); val mime = c.contentResolver.getType(u) ?: "application/octet-stream"
            val t = if (mime.startsWith("image/") || mime.startsWith("video/")) "media" else "file"
            sendFile(u, null, t, n, mime, if (t == "file") n else "", if (t == "media") u.toString() else null)
        }
    }
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val u = r.data?.data ?: return@rememberLauncherForActivityResult
        val card = runCatching {
            c.contentResolver.query(u, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { cur ->
                if (cur.moveToFirst()) "👤 " + cur.getString(0) + "\n📞 " + cur.getString(1) else null
            }
        }.getOrNull()
        if (card != null) { text = card; sendText() } else toast(c, "Couldn't read that contact")
    }
    // Voice notes: tap the mic to record, tap ✓ to send or ✕ to cancel (works with the keypad too).
    val rec = remember { ChatVoiceRecorder(c.applicationContext) }
    var recording by remember { mutableStateOf(false) }
    var recSec by remember { mutableStateOf(0) }
    fun startRec() { if (rec.start()) { recording = true; recSec = 0; ChatVoicePlayer.stop() } else toast(c, "Can't use the microphone") }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) startRec() else toast(c, "Allow the microphone to record voice messages") }
    LaunchedEffect(recording) { while (recording) { delay(120); rec.sample(); recSec = rec.seconds() } }
    DisposableEffect(room.id) { onDispose { if (recording) rec.stop(false); ChatVoicePlayer.stop() } }
    fun mic() {
        if (ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRec() else micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }
    fun stopRec(send: Boolean) { recording = false; rec.stop(send)?.let { (f, packed) -> sendFile(null, f, "voice", f.name, "audio/mp4", packed) } }

    fun reactTo(m: ChatMsg, e: String) {
        val old = reacts[m.id]; val counts = HashMap(old?.counts ?: emptyMap())
        if (old?.mine != null) counts[old.mine] = (counts[old.mine] ?: 1) - 1
        val mine = if (old?.mine == e) null else e
        if (mine != null) counts[e] = (counts[e] ?: 0) + 1
        reacts = reacts + (m.id to ChReact(counts.filterValues { it > 0 }, mine))
        scope.launch { runCatching { ChatApi.react(m.id, e) }.onFailure { toast(c, Api.friendly(it)) }; pullReacts() }
    }

    val pinned = info?.pinnedId?.let { p -> msgs.firstOrNull { it.id == p } }
    val subtitle = when {
        spectating -> "👁 Viewing as Admin"
        typing.isNotEmpty() -> if (room.type == "private") "typing…" else typing.take(2).joinToString(", ") + if (typing.size == 1) " is typing…" else " are typing…"
        // The site: "online" when the other person is online, else "last seen recently" (it sends no exact last-seen time).
        room.type == "private" -> if (info?.online == true) "online" else "last seen recently"
        room.type == "channel" -> if (room.members > 0) "${room.members} subscribers" else "Channel"
        else -> (info?.members?.size?.takeIf { it > 0 } ?: room.members).let { n -> if (n > 0) "$n members" + (info?.onlineCount?.takeIf { it > 0 }?.let { ", $it online" } ?: "") else "Group" }
    }
    val state = rememberLazyListState()
    // A text message with no words and no file is dropped, like the site drops contentless bubbles (never an empty green bubble).
    val rows = remember(msgs.toList()) { buildItems(groupJoinsC(msgs.asReversed().filterNot { it.type == "text" && it.text.isBlank() && it.media == null && !it.deleted })) }
    val matches = remember(sq, rows) { if (sq.trim().length < 2) emptyList() else rows.withIndex().filter { (_, it) -> it is ChItem.M && it.m.text.contains(sq.trim(), true) && !it.m.isVoice }.map { it.index } }
    LaunchedEffect(matches, hit) { matches.getOrNull(hit)?.let { state.animateScrollToItem(it) } }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty() && !searchOn && state.firstVisibleItemIndex < 3) state.scrollToItem(0) }
    fun scrollTo(id: String) { val i = rows.indexOfFirst { it is ChItem.M && it.m.id == id }; if (i >= 0) scope.launch { state.animateScrollToItem(i) } else toast(c, "That message is too old to show here") }

    BackHandler(enabled = searchOn || emojiOpen || attachOpen || recording || editing != null || replyTo != null) {
        when { recording -> stopRec(false); attachOpen -> attachOpen = false; emojiOpen -> emojiOpen = false; searchOn -> { searchOn = false; sq = "" }
            else -> { if (editing != null) text = ""; editing = null; replyTo = null } }
    }
    Column(Modifier.fillMaxSize().imePadding().background(ChatLook.wall())) {
        if (searchOn) {
            Surface(color = ChatLook.bar, contentColor = Color.White) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { ChSearchField(sq, { sq = it; hit = 0 }, "Search in this chat", { searchOn = false; sq = "" }) }
                    if (matches.isNotEmpty()) Text("${hit + 1}/${matches.size}", color = Color.White, fontSize = 13.sp)
                    ChIcon(Icons.Filled.KeyboardArrowUp, "Older", { if (matches.isNotEmpty()) hit = (hit + 1) % matches.size })
                    ChIcon(Icons.Filled.KeyboardArrowDown, "Newer", { if (matches.isNotEmpty()) hit = (hit - 1 + matches.size) % matches.size })
                }
            }
        } else ChBar(room.name, sub = subtitle, onBack = if (inPane) null else fun() { Nav.back() },
            lead = { ChAvatar(room.photo, room.name, room.otherUserId ?: room.id, 38.dp) },
            onTitle = { Nav.open(ChInfo(room)) }) {
            if (me != null && (room.type == "private" || isGroup)) ChIcon(Icons.Filled.Call, "Call", { Nav.open(ChCall(room, "audio")) })
            Box {
                ChIcon(Icons.Filled.MoreVert, "More", { menu = true })
                DropdownMenu(menu, { menu = false }) {
                    ChMenuItem(Icons.Filled.Info, if (room.type == "private") "Contact info" else if (isGroup) "Group info" else "Channel info") { menu = false; Nav.open(ChInfo(room)) }
                    ChMenuItem(Icons.Filled.Search, "Search") { menu = false; searchOn = true }
                    if (me != null && (room.type == "private" || isGroup)) ChMenuItem(Icons.Filled.Videocam, "Video call") { menu = false; Nav.open(ChCall(room, "video")) }
                    if (me != null && info != null) ChMenuItem(if (info?.muted == true) Icons.Filled.Notifications else Icons.Filled.NotificationsOff, if (info?.muted == true) "Unmute notifications" else "Mute notifications") {
                        menu = false; val m0 = info!!.muted
                        scope.launch { runCatching { ChatApi.mute(room.id, !m0) }.onSuccess { info = info?.copy(muted = !m0); toast(c, if (m0) "Unmuted" else "Muted"); roomsRes.load(true) }.onFailure { toast(c, Api.friendly(it)) } }
                    }
                    if (me != null && room.type != "private" && (info?.inviteCode != null || info?.canManage == true)) ChMenuItem(Icons.Filled.Link, "Share invite link") {
                        menu = false; scope.launch { runCatching { info?.inviteCode ?: ChatApi.invite(room.id) }.onSuccess { share(c, "Join \"${room.name}\" on YID PLUS: ${Api.BASE}/chat?join=$it") }.onFailure { toast(c, Api.friendly(it)) } }
                    }
                    if (info?.canManage == true && room.type != "private") ChMenuItem(Icons.Filled.Settings, if (isGroup) "Edit group" else "Edit channel") { menu = false; Nav.open(ChEditGroup(room)) }
                    if (me != null) ChMenuItem(Icons.Filled.Bookmark, "Saved messages") { menu = false; Nav.open(ChSaved()) }
                    if (me != null && joinedOk && info != null) ChMenuItem(Icons.AutoMirrored.Filled.ExitToApp, if (room.type == "private") "Delete chat" else if (isGroup) "Leave group" else "Leave channel", Color(0xFFD32F2F)) {
                        menu = false
                        confirm = (if (room.type == "private") "Delete this chat from your list?" else "Leave \"${room.name}\"?") to suspend { ChatApi.leave(room.id); roomsRes.load(true); Nav.back(); Unit }
                    }
                }
            }
        }
        if (pinned != null) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).tap(RoundedCornerShape(0.dp)) { scrollTo(pinned.id) }.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(32.dp).background(ChatLook.bar))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Pinned message", fontSize = 13.sp, color = ChatLook.bar.let { if (ChatLook.dark()) Color(0xFF7FD6BC) else it }, fontWeight = FontWeight.SemiBold)
                    Text(pinned.preview, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                }
                if (info?.canManage == true) ChIcon(Icons.Filled.Close, "Unpin", { scope.launch { runCatching { ChatApi.pin(room.id, null) }.onSuccess { info = info?.copy(pinnedId = null); roomsRes.load(true) } } }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                else Icon(Icons.Filled.PushPin, null, Modifier.padding(10.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(thickness = 0.5.dp)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                msgs.isEmpty() && error != null -> Problem(error!!, !Net.online, { scope.launch { pull() } })
                msgs.isEmpty() && !loaded -> ChBubbleSkeleton()
                msgs.isEmpty() -> ChEmpty(Icons.Filled.WavingHand, "No messages yet. Say hi!", Modifier.align(Alignment.Center))
                else -> {
                    val firstReal = rows.firstOrNull { it is ChItem.M && !it.m.isSystem }
                    val gallery = remember(rows) { msgs.filter { (it.isImage || it.isVideo) && it.media != null && !it.viewOnce }.map { q -> GItem(q.media!!, q.isVideo, null, q.nick) } }
                    // Gap 7: while the "newest" button shows, the list gets room under it so no message hides behind it.
                    val showDown by remember { derivedStateOf { state.firstVisibleItemIndex > 2 } }
                    LazyColumn(Modifier.fillMaxSize(), state = state, reverseLayout = true, contentPadding = PaddingValues(top = 6.dp, bottom = if (showDown) 60.dp else 8.dp)) {
                        items(rows, key = { it.key }) { it0 ->
                            when (it0) {
                                is ChItem.Day -> ChDayChip(it0.label)
                                is ChItem.M -> {
                                    val m = it0.m
                                    val hl = sq.trim().length >= 2 && matches.getOrNull(hit)?.let { idx -> rows.getOrNull(idx) === it0 } == true
                                    ChBubble(m, mine = m.senderId == me?.id, group = room.type != "private", first = it0.first, reacts = reacts[m.id],
                                        reply = m.replyToId?.let { rid -> msgs.firstOrNull { it.id == rid } }, highlight = hl, privateChat = room.type == "private",
                                        modifier = if (it0 === firstReal) Modifier.firstFocus() else Modifier,
                                        onMedia = { url -> openGallery(gallery.ifEmpty { listOf(GItem(url, m.isVideo, null, m.nick)) }, url) }, onLong = { if (!m.pending && !m.isCentred) actionFor = m },
                                        onSwipeReply = if (canWrite && !m.pending && !m.isCentred && !m.deleted) ({ replyTo = m; editing = null; runCatching { inputFocus.requestFocus() }; kb?.show() }) else null,
                                        onJoin = { code -> scope.launch { runCatching { ChatApi.join(null, code) }.onSuccess { id -> toast(c, "Joined"); roomsRes.load(true); Nav.open(Screen.Chat(ChatApi.info(id)?.room ?: ChatApi.roomOf(id, "group", "Group"))) }.onFailure { toast(c, Api.friendly(it)) } } },
                                        onReact = { e -> if (me != null) reactTo(m, e) }, onReplyTap = { rid -> scrollTo(rid) })
                                }
                            }
                        }
                    }
                    if (showDown) Box(Modifier.align(Alignment.BottomEnd).padding(12.dp).size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                        .tap(CircleShape) { scope.launch { state.animateScrollToItem(0) } }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Newest", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        when {
            canWrite -> {
                val ctxBar = editing ?: replyTo
                if (ctxBar != null) Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (editing != null) Icons.Filled.Edit else Icons.AutoMirrored.Filled.Reply, null, tint = ChatLook.bar, modifier = Modifier.size(20.dp))
                    Box(Modifier.padding(start = 8.dp).width(3.dp).height(34.dp).background(ChatLook.nameColor(ctxBar.senderId)))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(if (editing != null) "Edit message" else "Reply to " + ctxBar.nick, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ChatLook.nameColor(ctxBar.senderId), maxLines = 1)
                        Text(ctxBar.preview, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = TextStyle(textDirection = TextDirection.Content))
                    }
                    ChIcon(Icons.Filled.Close, "Cancel", { if (editing != null) text = ""; editing = null; replyTo = null }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (recording) {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChIcon(Icons.Filled.Delete, "Cancel recording", { stopRec(false) }, tint = Color(0xFFD32F2F))
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE53935)))
                        Text("  %d:%02d".format(recSec / 60, recSec % 60), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("  Recording…", Modifier.weight(1f), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.size(48.dp).clip(CircleShape).background(ChatLook.bar).tap(CircleShape) { stopRec(true) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send voice message", tint = Color.White)
                        }
                    }
                } else Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 4.dp, vertical = 5.dp), verticalAlignment = Alignment.Bottom) {
                    ChIcon(if (emojiOpen) Icons.Filled.Keyboard else Icons.Filled.EmojiEmotions, if (emojiOpen) "Keyboard" else "Emoji", {
                        emojiOpen = !emojiOpen; attachOpen = false; if (emojiOpen) kb?.hide() else kb?.show()
                    }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextField(text, { text = it }, Modifier.weight(1f).heightIn(min = 48.dp).focusRequester(inputFocus), placeholder = { Text("Message", fontSize = 16.sp) }, maxLines = 5,
                        textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content, color = MaterialTheme.colorScheme.onSurface), shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { sendText() }))
                    // Composer actions in plain sight, like WhatsApp: attach (photo, video, file, contact, poll…) and the camera; the round button is Send or the mic.
                    if (editing == null) ChIcon(Icons.Filled.AttachFile, "Attach", { attachOpen = !attachOpen; emojiOpen = false }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (editing == null && text.isBlank()) ChIcon(Icons.Filled.PhotoCamera, "Camera", { runCatching { camera.launch(null) }.onFailure { toast(c, "No camera app") } }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(2.dp))
                    val canSend = text.isNotBlank()
                    Box(Modifier.size(48.dp).clip(CircleShape).background(ChatLook.bar).tap(CircleShape) { if (canSend) sendText() else if (!sending) mic() }, contentAlignment = Alignment.Center) {
                        if (sending && !canSend) androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        else Icon(if (canSend) (if (editing != null) Icons.Filled.Check else Icons.AutoMirrored.Filled.Send) else Icons.Filled.Mic, if (canSend) "Send" else "Record voice message", tint = Color.White)
                    }
                }
                if (emojiOpen && !recording) ChEmojiPanel(onSticker = { url ->
                    emojiOpen = false
                    scope.launch { runCatching { Api.post("/api/chat", JSONObject().put("room_id", room.id).put("type", "sticker").put("text", url)) }.onFailure { toast(c, Api.friendly(it)) }; pull() }
                }) { e -> text += e }
                if (attachOpen && !recording) ChAttachSheet(onPick = { what ->
                    attachOpen = false
                    when (what) {
                        "gallery" -> pickPhoto.launch("image/*"); "camera" -> runCatching { camera.launch(null) }.onFailure { toast(c, "No camera app") }
                        "video" -> pickVideo.launch("video/*"); "audio" -> pickAudio.launch("audio/*"); "file" -> pickDoc.launch("*/*")
                        "contact" -> runCatching { pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }.onFailure { toast(c, "No contacts app") }
                        "poll" -> Nav.open(ChPollNew(room))
                    }
                }, onClose = { attachOpen = false })
            }
            spectating -> Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.Visibility, null, Modifier.size(18.dp), tint = Color(0xFFB8860B))
                Text("  You are viewing this private chat as Admin", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            me != null && !joinedOk && !room.guestView && room.type != "private" -> Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(10.dp), horizontalArrangement = Arrangement.Center) {
                PrimaryButton(if (room.type == "channel") "Join channel" else "Join group", {
                    scope.launch { runCatching { ChatApi.join(room.id, null) }.onSuccess { toast(c, "Joined"); roomsRes.load(true); info = info?.copy(joined = true); pull() }.onFailure { toast(c, Api.friendly(it)) } }
                }, Modifier.fillMaxWidth())
            }
            else -> Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (me == null) "  Sign in to write here  " else "  Only admins can write here", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (me == null) SecondaryButton("Sign in", { Nav.go(Tab.Profile) })
            }
        }
    }

    actionFor?.let { m ->
        val mine = m.senderId == me?.id
        ChActions(m, mine = mine, canPin = info?.canManage == true || room.type == "private", isPinned = info?.pinnedId == m.id, canDelete = mine || chIsSiteAdmin() || info?.canManage == true,
            canWrite = canWrite, signedIn = me != null, myReact = reacts[m.id]?.mine, onClose = { actionFor = null }) { act ->
            actionFor = null
            when (act) {
                "open" -> when { m.isImage || m.isVideo -> m.media?.let { openGallery(msgs.filter { (it.isImage || it.isVideo) && it.media != null }.map { q -> GItem(q.media!!, q.isVideo, null, q.nick) }, it) }
                    m.isAudio -> m.media?.let { ChatVoicePlayer.toggle(c, m.id, it) }; m.media != null -> openUrl(c, m.media)
                    else -> urlRegex.find(m.text)?.value?.let { openUrl(c, it) } }
                "reply" -> { replyTo = m; editing = null }
                "copy" -> chCopy(c, if (m.isFile || m.isImage || m.isVideo) (m.caption.ifBlank { m.media ?: "" }) else m.text)
                "edit" -> { editing = m; replyTo = null; text = m.text }
                "forward" -> Nav.open(ChForward(m))
                "pin" -> scope.launch { runCatching { ChatApi.pin(room.id, if (info?.pinnedId == m.id) null else m.id) }.onSuccess { info = info?.copy(pinnedId = if (info?.pinnedId == m.id) null else m.id); toast(c, "Done"); roomsRes.load(true) }.onFailure { toast(c, Api.friendly(it)) } }
                "save" -> scope.launch { runCatching { ChatApi.save(m.id, room.id) }.onSuccess { toast(c, "⭐ Saved") }.onFailure { toast(c, Api.friendly(it)) } }
                "share" -> share(c, listOf(m.caption, m.media ?: "").filter { it.isNotBlank() }.joinToString("\n").ifBlank { m.text })
                "report" -> reportFor = m
                "delete" -> confirm = "Delete this message for everyone?" to suspend { ChatApi.delete(m.id); msgs.removeAll { it.id == m.id }; Unit }
                "retry" -> { msgs.remove(m); if (m.type == "text") { text = m.text; sendText() } else Unit }
                else -> if (act.startsWith("react:")) reactTo(m, act.removePrefix("react:")) else Unit
            }
            Unit
        }
    }
    reportFor?.let { m ->
        AlertDialog(onDismissRequest = { reportFor = null }, title = { Text("Report message") }, text = {
            Column { listOf("Spam", "Abuse or harassment", "Inappropriate content", "Other").forEach { r ->
                Text(r, Modifier.fillMaxWidth().tap { reportFor = null; scope.launch { runCatching { ChatApi.report(m, r) }.onSuccess { toast(c, "Reported. Thank you") }.onFailure { toast(c, Api.friendly(it)) } } }.padding(14.dp), fontSize = 16.sp)
            } }
        }, confirmButton = { TextButton({ reportFor = null }) { Text("Cancel") } })
    }
    confirm?.let { (q, act) ->
        AlertDialog(onDismissRequest = { confirm = null }, text = { Text(q, fontSize = 16.sp) },
            confirmButton = { TextButton({ confirm = null; scope.launch { runCatching { act() }.onFailure { toast(c, Api.friendly(it)) } } }, Modifier.firstFocus(always = true)) { Text("Yes", color = Color(0xFFD32F2F)) } },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } })
    }
}

private fun buildItems(rev: List<ChatMsg>): List<ChItem> {
    // rev is newest first (reverse layout): a day chip goes after the oldest message of each day.
    val out = ArrayList<ChItem>(rev.size + 8)
    for (i in rev.indices) {
        val m = rev[i]; val older = rev.getOrNull(i + 1)
        val first = older == null || older.senderId != m.senderId || older.isSystem || chatDay(older.createdAt) != chatDay(m.createdAt)
        out.add(ChItem.M(m, first))
        val d = chatDay(m.createdAt)
        if (d.isNotEmpty() && (older == null || chatDay(older.createdAt) != d)) out.add(ChItem.Day(d, m.id))
    }
    return out.distinctBy { it.key }
}

/** Runs of "X joined" lines become one small line: "Avi, Moshe and 5 others joined". */
private fun groupJoinsC(list: List<ChatMsg>): List<ChatMsg> {
    val out = ArrayList<ChatMsg>(list.size); var run = ArrayList<ChatMsg>()
    fun flush() {
        if (run.size <= 2) out.addAll(run) else {
            val names = run.map { it.text.substringBefore(" joined").trim() }.filter { it.isNotBlank() }
            out.add(run.first().copy(text = names.take(2).joinToString(", ") + " and ${names.size - 2} others joined"))
        }
        run = ArrayList()
    }
    for (m in list) { if (m.isSystem && m.text.contains(" joined")) run.add(m) else { flush(); out.add(m) } }
    flush(); return out
}

@Composable
private fun ChDayChip(t: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(t, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = ChatLook.sub(),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(ChatLook.theirs().copy(alpha = .92f)).padding(horizontal = 12.dp, vertical = 4.dp))
    }
}

