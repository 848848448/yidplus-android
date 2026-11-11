package com.yidplus.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yidplus.app.data.Api
import com.yidplus.app.data.Disk
import com.yidplus.app.data.Msg
import com.yidplus.app.data.Net
import com.yidplus.app.data.Perf
import com.yidplus.app.data.Room
import com.yidplus.app.data.Session
import com.yidplus.app.data.Store
import com.yidplus.app.data.objects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen() {
    val me = Session.me
    val res = remember(me?.id) { Store.res("/api/chat/rooms", Room::list) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(res, Net.online) { res.load(force = Net.online && res.offline) }
    LaunchedEffect(res) { while (isActive) { delay(20_000); res.load(force = true) } }
    val list = res.data
    LaunchedEffect(list) { if (list != null) { Perf.shown("Chats"); Badges.chats = list.sumOf { it.unread } } }
    var q by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        TopBar("Chats", sub = if (me == null) "Guest · featured groups" else me.nickname)
        if (me == null) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, Modifier.size(18.dp)); Text(" Sign in to see and send your own chats", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                PrimaryButton("Sign in", { Nav.go(Tab.Profile) })
            }
        }
        PullToRefreshBox(isRefreshing = res.loading && list != null, onRefresh = { scope.launch { res.load(true) } }, modifier = Modifier.weight(1f)) {
            when {
                list == null && res.error != null -> Problem(res.error!!, res.offline, { scope.launch { res.load(true) } })
                list == null -> Loading()
                else -> {
                    val shown = if (q.isBlank()) list else list.filter { it.name.contains(q, true) || it.preview.contains(q, true) }
                    LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState()) {
                        if (list.size > 8) item {
                            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), singleLine = true,
                                placeholder = { Text("Search chats") }, textStyle = TextStyle(textDirection = TextDirection.Content))
                        }
                        if (shown.isEmpty()) item { Text(if (me == null) "No public groups right now" else "No chats yet", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(shown, key = { it.id }) { r -> RoomRow(r, if (r == shown.first()) Modifier.firstFocus() else Modifier) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomRow(r: Room, modifier: Modifier) {
    val sel = Nav.chatDetail?.room?.id == r.id
    Row(modifier.fillMaxWidth().background(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
        .tap(RoundedCornerShape(0.dp)) { Nav.open(Screen.Chat(r)) }.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(r.photo, r.name, 44.dp, emoji = if (r.photo == null && r.type != "private") r.emoji else null)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, Modifier.weight(1f), fontWeight = if (r.unread > 0) FontWeight.ExtraBold else FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
                Text(ago(r.lastTime), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.preview.ifBlank { if (r.members > 0) "${r.members} members" else " " }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
                Badge(r.unread)
            }
        }
    }
}

/** One conversation: newest at the bottom, refreshes every few seconds while open, send text or a photo. */
@Composable
fun ChatScreen(room: Room, inPane: Boolean = false) {
    val c = ctx()
    val me = Session.me
    val scope = rememberCoroutineScope()
    val msgs = remember(room.id) { mutableStateListOf<Msg>() }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var text by rememberSaveable(room.id) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val cacheKey = "chat|" + room.id
    val canWrite = me != null && !room.guestView && !room.readOnly

    suspend fun pull() {
        try {
            val list = withContext(Dispatchers.IO) {
                val j = Api.get("/api/chat?room_id=" + URLEncoder.encode(room.id, "UTF-8"))
                Disk.write(Store.key(cacheKey), JSONObject().put("messages", j.optJSONArray("messages")?.let { a -> org.json.JSONArray().also { o -> val s = maxOf(0, a.length() - 60); for (i in s until a.length()) o.put(a.get(i)) } }))
                j.optJSONArray("messages").objects().map(Msg::from).filter { it.type != "call" }
            }
            val lastOld = msgs.lastOrNull { !it.pending }?.id
            if (msgs.isEmpty() || list.lastOrNull()?.id != lastOld || list.size != msgs.count { !it.pending }) {
                val pend = msgs.filter { it.pending || it.failed }.filter { p -> list.none { it.text == p.text && it.senderId == me?.id && (parseTime(it.createdAt) ?: 0) >= System.currentTimeMillis() - 120_000 } }
                msgs.clear(); msgs.addAll(list); msgs.addAll(pend)
                if (me != null && !room.guestView) runCatching { Api.post("/api/chat/read", JSONObject().put("room_id", room.id)) }
            }
            error = null; loaded = true
            Perf.shown("Chat")
        } catch (e: Exception) { if (msgs.isEmpty()) error = Api.friendly(e) }
    }
    LaunchedEffect(room.id) {
        val cached = withContext(Dispatchers.IO) { Disk.read(Store.key(cacheKey))?.optJSONArray("messages").objects().map(Msg::from) }
        if (cached.isNotEmpty() && msgs.isEmpty()) { msgs.addAll(cached); Perf.shown("Chat", fromCache = true) }
        while (isActive) { pull(); delay(if (Net.online) 5_000 else 15_000) }
    }

    fun sendText() {
        val t = text.trim(); if (t.isEmpty() || sending) return
        text = ""
        val temp = Msg("tmp-" + System.nanoTime(), me!!.id, me.nickname, me.photo, "text", t, null, "", null, pending = true)
        msgs.add(temp)
        scope.launch {
            try {
                val j = Api.post("/api/chat", JSONObject().put("room_id", room.id).put("type", "text").put("text", t))
                val m = j.optJSONObject("message")?.let(Msg::from)
                val i = msgs.indexOfFirst { it.id == temp.id }
                if (i >= 0) { if (m != null) msgs[i] = m.copy(photo = me.photo) else msgs.removeAt(i) }
                pull()
            } catch (e: Exception) {
                val i = msgs.indexOfFirst { it.id == temp.id }; if (i >= 0) msgs[i] = temp.copy(pending = false, failed = true)
                toast(c, Api.friendly(e))
            }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null || me == null) return@rememberLauncherForActivityResult
        sending = true
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { shrink(c, uri) }
                Api.upload("/api/chat?room_id=" + URLEncoder.encode(room.id, "UTF-8") + "&type=media&filename=photo.jpg&ct=image%2Fjpeg", bytes, "image/jpeg")
                pull()
            } catch (e: Exception) { toast(c, "Photo not sent: " + Api.friendly(e)) } finally { sending = false }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(room.name, onBack = { Nav.back() }, sub = when { room.type == "private" -> "Private chat"; room.members > 0 -> "${room.members} members"; else -> null },
            lead = { Avatar(room.photo, room.name, 34.dp, emoji = if (room.photo == null && room.type != "private") room.emoji else null) })
        val state = rememberLazyListState()
        LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) state.scrollToItem(0) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                msgs.isEmpty() && error != null -> Problem(error!!, !Net.online, { scope.launch { pull() } })
                msgs.isEmpty() && !loaded -> Loading()
                msgs.isEmpty() -> Text("No messages yet. Say hi!", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val rev = msgs.asReversed()
                    val firstReal = rev.firstOrNull { it.type != "system" }
                    LazyColumn(Modifier.fillMaxSize(), state = state, reverseLayout = true, contentPadding = PaddingValues(vertical = 6.dp)) {
                        items(rev, key = { it.id }) { m -> Bubble(m, mine = m.senderId == me?.id, group = room.type != "private", modifier = if (m == firstReal) Modifier.firstFocus() else Modifier) }
                    }
                }
            }
        }
        if (canWrite) {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Icons.Filled.Image, "Send a photo", { pick.launch("image/*") }, tint = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(text, { text = it }, Modifier.weight(1f).heightIn(min = 48.dp), placeholder = { Text("Message") }, maxLines = 4,
                        textStyle = TextStyle(textDirection = TextDirection.Content), shape = RoundedCornerShape(22.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { sendText() }))
                    if (sending) Loading(Modifier.size(40.dp)) else IconBtn(Icons.AutoMirrored.Filled.Send, "Send", { sendText() }, tint = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(if (me == null) "Sign in to write here " else "Only admins can write here", style = MaterialTheme.typography.bodySmall)
                if (me == null) SecondaryButton("Sign in", { Nav.go(Tab.Profile) })
            }
        }
    }
}

@Composable
private fun Bubble(m: Msg, mine: Boolean, group: Boolean, modifier: Modifier) {
    val c = ctx()
    if (m.type == "system") {
        Box(modifier.fillMaxWidth().padding(4.dp), contentAlignment = Alignment.Center) {
            Text(m.text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
        }
        return
    }
    val bg = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val link = remember(m.text) { urlRegex.find(m.text)?.value }
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!mine && group) { Avatar(m.photo, m.nick, 28.dp); Spacer(Modifier.size(6.dp)) }
        Column(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp)).background(bg)
            .tap(RoundedCornerShape(16.dp)) {
                when {
                    m.isVideo || m.isAudio -> Nav.open(Screen.Video(m.media!!, m.nick))
                    m.isImage -> Nav.open(Screen.Photo(m.media!!))
                    m.media != null -> openUrl(c, m.media)
                    link != null -> openUrl(c, link)
                }
            }.padding(horizontal = 10.dp, vertical = 6.dp)) {
            if (!mine && group) Text(m.nick, style = MaterialTheme.typography.labelMedium, color = Gold, fontWeight = FontWeight.Bold)
            when {
                m.isImage -> AsyncImage(m.media, null, contentScale = ContentScale.Crop, modifier = Modifier.padding(vertical = 2.dp).size(200.dp, 160.dp).clip(RoundedCornerShape(10.dp)))
                m.isVideo || m.isAudio -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (m.isAudio) Icons.Filled.MusicNote else Icons.Filled.PlayArrow, null, tint = fg); Text(if (m.isAudio) " Voice / audio" else " Video", color = fg)
                }
                m.media != null -> Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.AttachFile, null, tint = fg); Text(" " + m.mediaKey.substringAfterLast('/').take(40), color = fg, maxLines = 1) }
            }
            if (m.text.isNotBlank()) Text(m.text, color = fg, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
            Text(if (m.pending) "Sending…" else if (m.failed) "Not sent" else clock(m.createdAt), style = MaterialTheme.typography.labelSmall,
                color = if (m.failed) MaterialTheme.colorScheme.error else fg.copy(alpha = .7f), modifier = Modifier.align(Alignment.End))
        }
    }
}

/** Photos are shrunk to 1280 px before sending: faster on a slow connection, same as the website does. */
private fun shrink(ctx: android.content.Context, uri: Uri): ByteArray {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
    var s = 1; while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 1280) s *= 2
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: error("Can't read the photo")
    val scale = 1280f / maxOf(bmp.width, bmp.height)
    val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
    return ByteArrayOutputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 82, it); it.toByteArray() }
}
