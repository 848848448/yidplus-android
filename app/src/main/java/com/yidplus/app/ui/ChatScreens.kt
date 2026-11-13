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
import androidx.compose.ui.draw.shadow
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
    val compact = LocalConfiguration.current.screenHeightDp < 600
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
    // ── helper CS 2.1 state ──
    val older = remember(room.id) { mutableStateListOf<ChatMsg>() }
    var hasMore by remember(room.id) { mutableStateOf(false) }
    var loadingOlder by remember { mutableStateOf(false) }
    val selected = remember(room.id) { mutableStateListOf<String>() }
    var selecting by remember(room.id) { mutableStateOf(false) }
    var scheduleAt by remember(room.id) { mutableStateOf<Long?>(null) }
    var disappear by remember(room.id) { mutableStateOf(0) }
    var optsOpen by remember { mutableStateOf(false) }
    var pinsOpen by remember { mutableStateOf(false) }
    var personFor by remember { mutableStateOf<ChatMsg?>(null) }
    var flash by remember { mutableStateOf<String?>(null) }
    var stickerKey by remember { mutableStateOf(0) }
    var pendingMedia by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val unreadAtOpen = remember(room.id) { room.unread }
    var unreadAnchor by remember(room.id) { mutableStateOf<String?>(null) }
    var didAnchorG by remember(room.id) { mutableStateOf(false) }
    var callOn by remember(room.id) { mutableStateOf(0) }

    suspend fun pull() {
        try {
            val (jj, list) = withContext(Dispatchers.IO) {
                val (j, l) = ChatApi.messages(room.id)
                ChatCache.put(room.id, j.optJSONArray("messages"), l)
                j to l
            }
            val loc: (ChatMsg) -> Boolean = { it.pending || it.failed || it.id.startsWith("tmp-") }
            val pend = msgs.filter(loc).filter { p -> list.none { it.senderId == me?.id && it.text == p.text && it.type == p.type && (parseTime(it.createdAt) ?: 0) >= System.currentTimeMillis() - 180_000 } }
            // Older pages the person scrolled back to stay above the newest page on every refresh.
            val keepOld = older.filter { o -> list.none { it.id == o.id } }
            if (msgs.isEmpty() || keepOld + list != msgs.filterNot(loc) || pend.size != msgs.count(loc)) {
                msgs.clear(); msgs.addAll(keepOld); msgs.addAll(list); msgs.addAll(pend)
            }
            if (!loaded) hasMore = jj.optBoolean("has_more", list.size >= 50) && list.isNotEmpty()
            if (unreadAnchor == null && !loaded && unreadAtOpen > 0) list.filter { it.senderId != me?.id && !it.isSystem }.let { th -> th.getOrNull(th.size - unreadAtOpen)?.let { unreadAnchor = it.id } }
            // The site posts /api/chat/read once when the room opens (members only; spectating leaves no trace) — and again here only when something new arrived.
            val newest = list.lastOrNull()?.id
            if (me != null && room.id.isNotBlank() && !room.guestView && joinedOk && !spectating && newest != lastReadId) { lastReadId = newest; runCatching { ChatApi.read(room.id) } }
            error = null; loaded = true
            Perf.shown("Chat")
        } catch (e: Exception) { if (msgs.isEmpty()) error = Api.friendly(e) }
    }
    /** The site's loadOlderMessages: the page before the oldest message we hold. */
    suspend fun loadOlder(): Boolean {
        if (loadingOlder || !hasMore) return false
        val first = msgs.firstOrNull { !it.id.startsWith("tmp-") } ?: return false
        loadingOlder = true
        return try {
            val (j, l) = withContext(Dispatchers.IO) { ChatApi.older(room.id, first.createdAt ?: "", first.id) }
            val fresh = l.filter { n -> msgs.none { it.id == n.id } }
            older.addAll(0, fresh); msgs.addAll(0, fresh)
            hasMore = j.optBoolean("has_more", l.isNotEmpty()) && l.isNotEmpty()
            fresh.isNotEmpty()
        } catch (e: Exception) { toast(c, Api.friendly(e)); false } finally { loadingOlder = false }
    }
    // Reactions: one full read when the room opens, then only what changed (&since=), merged over what we have.
    var reactsAt by remember(room.id) { mutableStateOf<String?>(null) }
    suspend fun pullReacts() {
        if (me == null) return
        val t0 = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(System.currentTimeMillis() - 15_000))
        runCatching { withContext(Dispatchers.IO) { ChatApi.reactions(room.id, reactsAt) } }.onSuccess { r -> reacts = if (reactsAt == null) r else reacts + r; reactsAt = t0 }
    }
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
                val sch = scheduleAt?.let { at -> java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(at)) }
                val m = ChatApi.sendText(room.id, t, rt?.id, sch, disappear)
                if (sch != null) { scheduleAt = null; msgs.removeAll { it.id == temp.id }; toast(c, "🕓 Message scheduled"); return@launch }
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
    fun sendPhoto(uri: Uri?, bmp: Bitmap?, caption: String = "") {
        scope.launch {
            val f = runCatching { withContext(Dispatchers.IO) { java.io.File(c.cacheDir, "cam_" + System.nanoTime() + ".jpg").also { it.writeBytes(chShrink(c, uri, bmp)) } } }.getOrElse { toast(c, "Can't read the photo"); return@launch }
            sendFile(null, f, "media", "photo.jpg", "image/jpeg", caption, localPreview = Uri.fromFile(f).toString())
        }
    }
    /** Photos and videos picked or shot go to the preview first (caption, remove one), like WhatsApp; then each is sent the site's way. */
    fun sendPicked(list: List<Uri>, caption: String) {
        list.forEachIndexed { i, u ->
            val mime = c.contentResolver.getType(u) ?: (if (u.toString().endsWith(".mp4")) "video/mp4" else "image/jpeg")
            val cap = if (i == 0) caption else ""
            if (mime.startsWith("video/")) { val n = ChatApi.nameOf(c, u).let { if (it.contains('.')) it else "$it.mp4" }; sendFile(u, null, "media", n, mime, cap, localPreview = u.toString()) }
            else sendPhoto(u, null, cap)
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { l: List<Uri> -> if (l.isNotEmpty()) pendingMedia = l.take(30) }
    // Full-size camera photo (the old preview-size picture was ~200 px): the camera writes into our FileProvider's captures/.
    var shotUri by remember { mutableStateOf<Uri?>(null) }
    fun newCapture(ext: String): Uri? = runCatching {
        val dir = java.io.File(c.cacheDir, "captures").apply { mkdirs() }
        androidx.core.content.FileProvider.getUriForFile(c, c.packageName + ".files", java.io.File(dir, "cam_" + System.currentTimeMillis() + ext))
    }.getOrNull()
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok: Boolean -> val u = shotUri; if (ok && u != null) pendingMedia = listOf(u) }
    val videoCam = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok: Boolean -> val u = shotUri; if (ok && u != null) pendingMedia = listOf(u) }
    fun shoot(video: Boolean) {
        val u = newCapture(if (video) ".mp4" else ".jpg") ?: run { toast(c, "Can't open the camera"); return }
        shotUri = u
        runCatching { if (video) videoCam.launch(u) else camera.launch(u) }.onFailure { toast(c, "No camera app") }
    }
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
    val pickSticker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u: Uri? ->
        if (u != null) scope.launch { toast(c, "Uploading sticker…"); runCatching { ChatApi.uploadSticker(c, u) }.onSuccess { toast(c, "✅ Sticker added"); stickerKey++ }.onFailure { toast(c, Api.friendly(it)) } }
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
    // Voice notes (helper CS 2.1): hold to record, release to send, slide left to cancel, slide up to lock;
    // tap (or OK on the keypad) = hands-free right away with Pause, Stop-to-listen, Delete and Send.
    val vr = remember { CsRec(c.applicationContext) }
    val recording = vr.mode != 0
    // localPreview: the bubble shows the real voice note (playable from the file) while it uploads, never "Voice message unavailable".
    fun sendTake(t: Pair<java.io.File, String>?) { t?.let { (f, packed) -> sendFile(null, f, "voice", f.name, "audio/mp4", packed, localPreview = Uri.fromFile(f).toString()) } }
    fun startRec(locked: Boolean) { if (!vr.start(locked)) toast(c, "Can't use the microphone") }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) startRec(true) else toast(c, "Allow the microphone to send voice messages") }
    LaunchedEffect(vr.mode) { while (vr.mode == 1 || vr.mode == 2) { delay(100); vr.tick() } }
    DisposableEffect(room.id) { onDispose { if (vr.mode != 0) vr.finish(false); ChatVoicePlayer.stop(); ChatVoicePlayer.onDone = null } }
    fun mic(locked: Boolean) {
        if (ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRec(locked) else micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }
    fun stopRec(send: Boolean) { sendTake(vr.finish(send)) }
    // The site's _autoPlayNextVoice: when a voice note ends, the next one below it plays.
    LaunchedEffect(room.id) {
        ChatVoicePlayer.onDone = { id ->
            val i = msgs.indexOfFirst { it.id == id }
            if (i >= 0) msgs.drop(i + 1).firstOrNull { it.isVoice && it.media != null }?.let { n -> ChatVoicePlayer.toggle(c, n.id, n.media!!) }
        }
    }

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
    fun itemsNow(): List<ChItem> {
        val al = csAlbums(msgs.toList())
        val tail = al.values.flatMap { it.drop(1) }.map { it.id }.toHashSet()
        return buildItems(groupJoinsC(msgs.asReversed().filterNot { (it.type == "text" && it.text.isBlank() && it.media == null && !it.deleted) || it.id in tail }))
    }
    val rows = remember(msgs.toList()) { itemsNow() }
    val albums = remember(msgs.toList()) { csAlbums(msgs.toList()) }
    val matches = remember(sq, rows) { if (sq.trim().length < 2) emptyList() else rows.withIndex().filter { (_, it) -> it is ChItem.M && it.m.text.contains(sq.trim(), true) && !it.m.isVoice }.map { it.index } }
    LaunchedEffect(matches, hit) { matches.getOrNull(hit)?.let { state.animateScrollToItem(it) } }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty() && !searchOn && state.firstVisibleItemIndex < 3 && (unreadAnchor == null || didAnchorG)) state.scrollToItem(0) }
    /** Jump to a message (reply quote, pinned bar, pinned list): older pages load until it is found, then it flashes like the site's highlight. */
    fun jumpTo(id: String) {
        scope.launch {
            var tries = 0
            while (msgs.none { it.id == id } && hasMore && tries++ < 10) { if (!loadOlder()) break }
            delay(60)
            val i = itemsNow().indexOfFirst { it is ChItem.M && it.m.id == id }
            if (i >= 0) { state.animateScrollToItem(i); flash = id; delay(1600); if (flash == id) flash = null } else toast(c, "Message not found")
        }
    }
    fun scrollTo(id: String) = jumpTo(id)
    // Older history: when the top (the end of the reversed list) comes into view, the page before loads.
    val nearTop by remember { derivedStateOf { val li = state.layoutInfo; li.totalItemsCount > 0 && (li.visibleItemsInfo.lastOrNull()?.index ?: 0) >= li.totalItemsCount - 3 } }
    LaunchedEffect(nearTop, hasMore) { if (nearTop && hasMore && loaded) loadOlder() }
    // Open at the first unread message, under an "N unread messages" line.
    LaunchedEffect(unreadAnchor, rows.size) {
        val a = unreadAnchor ?: return@LaunchedEffect
        if (didAnchorG) return@LaunchedEffect
        delay(80) // after the "stick to the newest" scroll of the same refresh
        val i = itemsNow().indexOfFirst { it is ChItem.M && it.m.id == a }
        if (i >= 0) {
            didAnchorG = true
            // reverse layout: item i sits on the bottom edge, its "N unread messages" line right above it (the rest is one swipe down)
            if (i > 2) state.scrollToItem(i)
        }
    }
    // A group call running in this room (the site's banner "Call in progress · N on the call — tap to join").
    LaunchedEffect(room.id, me?.id) {
        if (me == null || room.type == "channel") return@LaunchedEffect
        while (isActive) {
            runCatching { withContext(Dispatchers.IO) { Api.get("/api/group-call?room_id=" + chEnc(room.id)) } }.getOrNull()?.let { j ->
                callOn = j.optJSONArray("participants")?.length() ?: 0
            }
            delay(20_000)
        }
    }

    BackHandler(enabled = selecting || searchOn || emojiOpen || attachOpen || recording || editing != null || replyTo != null) {
        when { selecting -> { selecting = false; selected.clear() }; recording -> stopRec(false); attachOpen -> attachOpen = false; emojiOpen -> emojiOpen = false; searchOn -> { searchOn = false; sq = "" }
            else -> { if (editing != null) text = ""; editing = null; replyTo = null } }
    }
    val csHeader: @Composable ColumnScope.() -> Unit = {
        if (selecting) {
            val sel = msgs.filter { it.id in selected }
            val one = sel.singleOrNull()
            CsSelectBar(selected.size, canDelete = sel.isNotEmpty() && sel.all { it.senderId == me?.id || chIsSiteAdmin() || info?.canManage == true },
                canSave = sel.all { it.senderId == me?.id } || info?.allowSaving != false || info?.canManage == true,
                onClose = { selecting = false; selected.clear() },
                onCopy = { chCopy(c, if (sel.size == 1) (if (sel[0].type == "text") sel[0].text else sel[0].preview) else sel.joinToString("\n") { "[" + chatTime(it.createdAt) + "] " + it.nick + ": " + (if (it.type == "text") it.text else it.preview) }); selecting = false; selected.clear() },
                onForward = { if (sel.isNotEmpty()) Nav.open(CsForwardMany(sel)); selecting = false; selected.clear() },
                onDelete = { val ids = sel.map { it.id }; confirm = (if (ids.size == 1) "Delete this message for everyone?" else "Delete ${ids.size} messages for everyone?") to suspend {
                    ids.forEach { id -> runCatching { ChatApi.delete(id) }; msgs.removeAll { it.id == id }; older.removeAll { it.id == id } }; selecting = false; selected.clear(); Unit } },
                onReply = if (canWrite && one != null && !one.deleted) ({ replyTo = one; editing = null; selecting = false; selected.clear(); runCatching { inputFocus.requestFocus() }; kb?.show() }) else null,
                onStar = if (me != null && one != null) ({ scope.launch { runCatching { ChatApi.save(one.id, room.id) }.onSuccess { toast(c, "⭐ Saved") }.onFailure { toast(c, Api.friendly(it)) } }; selecting = false; selected.clear() }) else null,
                onMore = if (one != null) ({ actionFor = one; selecting = false; selected.clear() }) else null)
            if (one != null && me != null && !one.deleted) CsReactStrip(reacts[one.id]?.mine, onReact = { e -> reactTo(one, e); selecting = false; selected.clear() },
                onMore = { actionFor = one; selecting = false; selected.clear() })
        } else if (searchOn) {
            Surface(color = ChatLook.bar, contentColor = Color.White) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { ChSearchField(sq, { sq = it; hit = 0 }, "Search in this chat", { searchOn = false; sq = "" }) }
                    if (matches.isNotEmpty()) Text("${hit + 1}/${matches.size}", color = Color.White, fontSize = 13.sp)
                    ChIcon(Sym.KeyboardArrowUp, "Older", { if (matches.isNotEmpty()) hit = (hit + 1) % matches.size })
                    ChIcon(Sym.KeyboardArrowDown, "Newer", { if (matches.isNotEmpty()) hit = (hit - 1 + matches.size) % matches.size })
                }
            }
        } else ChBar(room.name, sub = if (compact && (WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0)) null else subtitle, onBack = if (inPane) null else fun() { Nav.back() },
            lead = { ChAvatar(room.photo, room.name, room.otherUserId ?: room.id, 38.dp) },
            onTitle = { Nav.open(ChInfo(room)) }) {
            if (me != null && (room.type == "private" || isGroup)) ChIcon(Sym.Call, "Call", { Nav.open(ChCall(room, "audio")) })
            Box {
                ChIcon(Sym.MoreVert, "More", { menu = true })
                DropdownMenu(menu, { menu = false }) {
                    ChMenuItem(Sym.Info, if (room.type == "private") "Contact info" else if (isGroup) "Group info" else "Channel info") { menu = false; Nav.open(ChInfo(room)) }
                    ChMenuItem(Sym.Search, "Search") { menu = false; searchOn = true }
                    if (me != null && (room.type == "private" || isGroup)) ChMenuItem(Sym.Videocam, "Video call") { menu = false; Nav.open(ChCall(room, "video")) }
                    if (me != null && info != null) ChMenuItem(if (info?.muted == true) Sym.Notifications else Sym.NotificationsOff, if (info?.muted == true) "Unmute notifications" else "Mute notifications") {
                        menu = false; val m0 = info!!.muted
                        scope.launch { runCatching { ChatApi.mute(room.id, !m0) }.onSuccess { info = info?.copy(muted = !m0); toast(c, if (m0) "Unmuted" else "Muted"); roomsRes.load(true) }.onFailure { toast(c, Api.friendly(it)) } }
                    }
                    if (me != null && room.type != "private" && (info?.inviteCode != null || info?.canManage == true)) ChMenuItem(Sym.Link, "Share invite link") {
                        menu = false; scope.launch { runCatching { info?.inviteCode ?: ChatApi.invite(room.id) }.onSuccess { share(c, "Join \"${room.name}\" on YID PLUS: ${Api.BASE}/chat?join=$it") }.onFailure { toast(c, Api.friendly(it)) } }
                    }
                    if (me != null && room.type != "private" && (info?.inviteCode != null || info?.canManage == true)) ChMenuItem(Sym.ContentCopy, "Copy invite link") {
                        menu = false; scope.launch { runCatching { info?.inviteCode ?: ChatApi.invite(room.id) }.onSuccess { chCopy(c, "${Api.BASE}/chat?join=$it") }.onFailure { toast(c, Api.friendly(it)) } }
                    }
                    if (info?.pinnedId != null || info?.canManage == true) ChMenuItem(Sym.PushPin, "Pinned messages") { menu = false; pinsOpen = true }
                    if (canWrite) ChMenuItem(Sym.Schedule, "Message options") { menu = false; optsOpen = true }
                    if (msgs.isNotEmpty()) ChMenuItem(Sym.CheckCircle, "Select messages") { menu = false; selecting = true; selected.clear() }
                    if (info?.canManage == true && room.type != "private") ChMenuItem(Sym.Settings, if (isGroup) "Edit group" else "Edit channel") { menu = false; Nav.open(ChEditGroup(room)) }
                    if (me != null) ChMenuItem(Sym.Bookmark, "Saved messages") { menu = false; Nav.open(ChSaved()) }
                    if (me != null && joinedOk && info != null) ChMenuItem(Sym.ExitToApp, if (room.type == "private") "Delete chat" else if (isGroup) "Leave group" else "Leave channel", Color(0xFFD32F2F)) {
                        menu = false
                        confirm = (if (room.type == "private") "Delete this chat from your list?" else "Leave \"${room.name}\"?") to suspend { ChatApi.leave(room.id); roomsRes.load(true); Nav.back(); Unit }
                    }
                }
            }
        }
    }
    val csList: @Composable ColumnScope.() -> Unit = {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                msgs.isEmpty() && error != null -> Problem(error!!, !Net.online, { scope.launch { pull() } })
                msgs.isEmpty() && !loaded -> ChBubbleSkeleton()
                msgs.isEmpty() -> ChEmpty(Sym.WavingHand, "No messages yet. Say hi!", Modifier.align(Alignment.Center))
                else -> {
                    val firstReal = rows.firstOrNull { it is ChItem.M && !it.m.isSystem }
                    val gallery = remember(rows) { msgs.filter { (it.isImage || it.isVideo) && it.media != null && !it.viewOnce }.map { q -> GItem(q.media!!, q.isVideo, null, q.nick) } }
                    // Gap 7: while the "newest" button shows, the list gets room under it so no message hides behind it.
                    val showDown by remember { derivedStateOf { state.firstVisibleItemIndex > 2 } }
                    // New messages that came in while scrolled up: a count on the "newest" button (WhatsApp's green badge).
                    // counted from the newest message when the button appeared, so older pages loading above never count
                    var baseId by remember(room.id) { mutableStateOf<String?>(null) }
                    LaunchedEffect(showDown) { baseId = if (showDown) msgs.lastOrNull()?.id else null }
                    val newBelow = baseId?.let { b -> val i = msgs.indexOfLast { it.id == b }; if (i >= 0) msgs.size - 1 - i else 0 } ?: 0
                    LazyColumn(Modifier.fillMaxSize(), state = state, reverseLayout = true, contentPadding = PaddingValues(top = 6.dp, bottom = if (showDown) 60.dp else 8.dp)) {
                        items(rows, key = { it.key }) { it0 ->
                            when (it0) {
                                is ChItem.Day -> ChDayChip(it0.label)
                                is ChItem.M -> {
                                    val m = it0.m
                                    val hl = sq.trim().length >= 2 && matches.getOrNull(hit)?.let { idx -> rows.getOrNull(idx) === it0 } == true
                                    if (m.id == unreadAnchor) CsUnreadLine(unreadAtOpen)
                                    ChBubble(m, mine = m.senderId == me?.id, group = room.type != "private", first = it0.first, reacts = reacts[m.id],
                                        reply = m.replyToId?.let { rid -> msgs.firstOrNull { it.id == rid } }, highlight = hl || flash == m.id, privateChat = room.type == "private",
                                        modifier = if (it0 === firstReal) Modifier.firstFocus() else Modifier,
                                        onMedia = { url -> openGallery(gallery.ifEmpty { listOf(GItem(url, m.isVideo, null, m.nick)) }, url) }, onLong = { if (!m.pending && !m.isCentred) { if (m.failed) actionFor = m else { selecting = true; selected.clear(); selected.add(m.id) } } },
                                        onSwipeReply = if (canWrite && !m.pending && !m.isCentred && !m.deleted) ({ replyTo = m; editing = null; runCatching { inputFocus.requestFocus() }; kb?.show() }) else null,
                                        onJoin = { code -> scope.launch { runCatching { ChatApi.join(null, code) }.onSuccess { id -> toast(c, "Joined"); roomsRes.load(true); Nav.open(Screen.Chat(ChatApi.info(id)?.room ?: ChatApi.roomOf(id, "group", "Group"))) }.onFailure { toast(c, Api.friendly(it)) } } },
                                        onReact = { e -> if (me != null) reactTo(m, e) }, onReplyTap = { rid -> jumpTo(rid) },
                                        onNick = if (room.type != "private" && m.senderId != me?.id) ({ personFor = m }) else null,
                                        onMention = { u -> scope.launch { csOpenMention(c, u) } },
                                        selected = if (selecting) m.id in selected else null, album = albums[m.id],
                                        onSelect = { if (m.id in selected) selected.remove(m.id) else if (!m.pending && !m.isCentred) selected.add(m.id); if (selected.isEmpty()) selecting = false })
                                }
                            }
                        }
                    }
                    if (showDown) Box(Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                            .tap(CircleShape) { scope.launch { state.animateScrollToItem(0) } }, contentAlignment = Alignment.Center) {
                            Icon(Sym.KeyboardArrowDown, if (newBelow > 0) "Newest, $newBelow new" else "Newest", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (newBelow > 0) Text(if (newBelow > 99) "99+" else "$newBelow", Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-6).dp).clip(CircleShape).background(ChatLook.bar)
                            .padding(horizontal = 6.dp, vertical = 1.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    if (loadingOlder) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = ChatLook.bar)
                    // WhatsApp's floating date while scrolled back: the day of the top visible message.
                    val topDay by remember(rows) { derivedStateOf { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { i -> (rows.getOrNull(i) as? ChItem.M)?.m?.createdAt?.let { chatDay(it) } } ?: "" } }
                    // Solid chip with a shadow (WhatsApp): it never shows the bubble text through it.
                    if (showDown && state.isScrollInProgress && topDay.isNotEmpty()) Text(topDay, Modifier.align(Alignment.TopCenter).padding(top = 6.dp)
                        .shadow(2.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(csDayChipColor())
                        .padding(horizontal = 12.dp, vertical = 5.dp), fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = ChatLook.sub())
                }
            }
        }
    }
    val csComposer: @Composable ColumnScope.() -> Unit = {
        when {
            canWrite -> {
                val ctxBar = editing ?: replyTo
                if (ctxBar != null) Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (editing != null) Sym.Edit else Sym.Reply, null, tint = ChatLook.bar, modifier = Modifier.size(20.dp))
                    Box(Modifier.padding(start = 8.dp).width(3.dp).height(34.dp).background(ChatLook.nameColor(ctxBar.senderId)))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(if (editing != null) "Edit message" else "Reply to " + ctxBar.nick, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ChatLook.nameColor(ctxBar.senderId), maxLines = 1)
                        Text(ctxBar.preview, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = TextStyle(textDirection = TextDirection.Content))
                    }
                    ChIcon(Sym.Close, "Cancel", { if (editing != null) text = ""; editing = null; replyTo = null }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CsOptsBadge(scheduleAt, disappear) { scheduleAt = null; disappear = 0 }
                if (vr.mode == 2 || vr.mode == 3) CsLockedBar(vr, onDelete = { stopRec(false) }, onSend = { stopRec(true) })
                else Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 4.dp, vertical = if (compact && (WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0)) 2.dp else 5.dp), verticalAlignment = Alignment.Bottom) {
                    if (vr.mode == 1) Box(Modifier.weight(1f)) { CsHoldBar(vr) }
                    else {
                    ChIcon(if (emojiOpen) Sym.Keyboard else Sym.EmojiEmotions, if (emojiOpen) "Keyboard" else "Emoji", {
                        emojiOpen = !emojiOpen; attachOpen = false; if (emojiOpen) kb?.hide() else kb?.show()
                    }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextField(text, { text = it }, Modifier.weight(1f).heightIn(min = if (compact && (WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0)) 46.dp else 52.dp).focusRequester(inputFocus), placeholder = { Text("Message", fontSize = if (compact) 17.sp else 16.sp) }, maxLines = 5,
                        textStyle = TextStyle(fontSize = if (compact) 17.sp else 16.sp, textDirection = TextDirection.Content, color = MaterialTheme.colorScheme.onSurface), shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { sendText() }))
                    if (editing == null) ChIcon(Sym.AttachFile, "Attach", { attachOpen = !attachOpen; emojiOpen = false }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (editing == null && text.isBlank()) ChIcon(Sym.PhotoCamera, "Camera", { shoot(false) }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(2.dp))
                    CsMicButton(canSend = text.isNotBlank(), editing = editing != null, busy = sending && vr.mode == 0, r = vr, onSend = { sendText() },
                        onStart = { locked -> mic(locked) }, onRelease = { stopRec(true) }, onCancel = { stopRec(false); toast(c, "Recording cancelled") })
                }
                if (emojiOpen && !recording) CsEmojiPanel(onSticker = { url ->
                    emojiOpen = false
                    scope.launch { runCatching { Api.post("/api/chat", JSONObject().put("room_id", room.id).put("type", "sticker").put("text", url)) }.onFailure { toast(c, Api.friendly(it)) }; pull() }
                }, onPickSticker = { pickSticker.launch("image/*") }, reloadKey = stickerKey) { e -> text += e }
                if (attachOpen && !recording) CsAttachSheet(onPick = { what ->
                    attachOpen = false
                    when (what) {
                        "gallery" -> pickPhoto.launch("image/*"); "camera" -> shoot(false); "videocam" -> shoot(true)
                        "video" -> pickVideo.launch("video/*"); "audio" -> pickAudio.launch("audio/*"); "file" -> pickDoc.launch("*/*")
                        "contact" -> runCatching { pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }.onFailure { toast(c, "No contacts app") }
                        "poll" -> Nav.open(CsPollNew(room))
                        "options" -> optsOpen = true
                    }
                })
            }
            spectating -> Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Sym.Visibility, null, Modifier.size(18.dp), tint = Color(0xFFB8860B))
                Text("  You are viewing this private chat as Admin", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            me != null && !joinedOk && !room.guestView && room.type != "private" -> Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(10.dp), horizontalArrangement = Arrangement.Center) {
                PrimaryButton(if (room.type == "channel") "Join channel" else "Join group", {
                    scope.launch { runCatching { ChatApi.join(room.id, null) }.onSuccess { toast(c, "Joined"); roomsRes.load(true); info = info?.copy(joined = true); pull() }.onFailure { toast(c, Api.friendly(it)) } }
                }, Modifier.fillMaxWidth())
            }
            else -> Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Sym.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (me == null) "  Sign in to write here  " else "  Only admins can write here", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (me == null) SecondaryButton("Sign in", { Nav.go(Tab.Profile) })
            }
        }
    }
    val csDialogs: @Composable () -> Unit = {
    actionFor?.let { m ->
        val mine = m.senderId == me?.id
        // The site: only a site admin or a group admin pins; "allow saving" off hides copy/forward/save/share of others' messages.
        CsActions(m, mine = mine, canPin = info?.canManage == true, isPinned = info?.pinnedId == m.id, canDelete = mine || chIsSiteAdmin() || info?.canManage == true,
            canWrite = canWrite, canSave = mine || info?.allowSaving != false || info?.canManage == true, signedIn = me != null, myReact = reacts[m.id]?.mine, onClose = { actionFor = null }) { act ->
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
                "select" -> { selecting = true; selected.clear(); selected.add(m.id) }
                "delete" -> confirm = "Delete this message for everyone?" to suspend { ChatApi.delete(m.id); msgs.removeAll { it.id == m.id }; Unit }
                "retry" -> { msgs.remove(m); if (m.type == "text") { text = m.text; sendText() } else Unit }
                else -> if (act.startsWith("react:")) reactTo(m, act.removePrefix("react:")) else Unit
            }
            Unit
        }
    }
    reportFor?.let { m ->
        CsReport(onSend = { reason, details -> reportFor = null
            scope.launch { runCatching { ChatApi.reportMsg(m, reason, details) }.onSuccess { toast(c, "✅ Report submitted. Thank you.") }.onFailure { toast(c, Api.friendly(it)) } } },
            onClose = { reportFor = null })
    }
    if (optsOpen) CsMsgOptions(disappear, onSchedule = {
        optsOpen = false
        val cal = java.util.Calendar.getInstance()
        android.app.DatePickerDialog(c, { _, y, mo, d ->
            android.app.TimePickerDialog(c, { _, h, mi ->
                val at = java.util.Calendar.getInstance().apply { set(y, mo, d, h, mi, 0) }.timeInMillis
                if (at <= System.currentTimeMillis() + 30_000) toast(c, "Pick a time in the future") else { scheduleAt = at; toast(c, "🕓 Your next message will be sent then") }
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), false).show()
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
    }, onDisappear = { disappear = it }, onClose = { optsOpen = false })
    if (pinsOpen) CsPinnedList(room.id, onJump = { id -> pinsOpen = false; jumpTo(id) }, onClose = { pinsOpen = false })
    personFor?.let { p -> CsPersonCard(p, onMention = { text = (text.trimEnd() + " @" + p.nick + " ").trimStart(); runCatching { inputFocus.requestFocus() } }, onClose = { personFor = null }) }
    if (pendingMedia.isNotEmpty()) CsMediaPreview(pendingMedia, onRemove = { u -> pendingMedia = pendingMedia - u }, onSend = { cap -> val l = pendingMedia; pendingMedia = emptyList(); sendPicked(l, cap) },
        onClose = { pendingMedia = emptyList() })
    confirm?.let { (q, act) ->
        AlertDialog(onDismissRequest = { confirm = null }, text = { Text(q, fontSize = 16.sp) },
            confirmButton = { TextButton({ confirm = null; scope.launch { runCatching { act() }.onFailure { toast(c, Api.friendly(it)) } } }, Modifier.firstFocus(always = true)) { Text("Yes", color = Color(0xFFD32F2F)) } },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } })
    }
    }
    // ChatScreen is split into lambdas: one huge composable method failed ART verification after R8 (CI 43156298868, VerifyError on API 30).
    Column(Modifier.fillMaxSize().imePadding().background(ChatLook.wall()).chatWallpaper()) {
        csHeader()
        if (pinned != null) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).tap(RoundedCornerShape(0.dp)) { scrollTo(pinned.id) }.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(32.dp).background(ChatLook.bar))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Pinned message", fontSize = 13.sp, color = ChatLook.bar.let { if (ChatLook.dark()) Color(0xFF7FD6BC) else it }, fontWeight = FontWeight.SemiBold)
                    Text(pinned.preview, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                }
                ChIcon(Sym.ViewList, "All pinned messages", { pinsOpen = true }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (info?.canManage == true) ChIcon(Sym.Close, "Unpin", { scope.launch { runCatching { ChatApi.pin(room.id, null) }.onSuccess { info = info?.copy(pinnedId = null); roomsRes.load(true) } } }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                else Icon(Sym.PushPin, null, Modifier.padding(10.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(thickness = 0.5.dp)
        }
        if (callOn > 0 && !selecting) Row(Modifier.fillMaxWidth().background(Color(0xFF2E7D32)).tap(RoundedCornerShape(0.dp)) { Nav.open(ChCall(room, "audio")) }.padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Sym.Call, null, Modifier.size(18.dp), tint = Color.White)
            Text("  Call in progress · $callOn on the call", Modifier.weight(1f), fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Join", Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 4.dp), color = Color(0xFF2E7D32), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        csList()
        csComposer()
    }
    csDialogs()
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
        Text(t, fontSize = if (LocalConfiguration.current.screenHeightDp < 600) 12.5.sp else 12.sp, fontWeight = FontWeight.Medium, color = ChatLook.sub(),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(csDayChipColor()).padding(horizontal = 12.dp, vertical = 4.dp))
    }
}



/** WhatsApp's date chip: light #FFFFFF with shadow on the wallpaper / dark #182229 — opaque, never see-through. */
@Composable
private fun csDayChipColor() = if (ChatLook.dark()) Color(0xFF182229) else Color(0xFFFFFFFF)
