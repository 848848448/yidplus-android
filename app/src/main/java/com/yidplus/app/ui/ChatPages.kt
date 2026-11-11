package com.yidplus.app.ui

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

/** Group / channel / contact info: photo, name, description, mute, invite link, members (admins can add, remove, make admin), media, leave. */
data class ChInfo(val room: Room) : PageScreen() {
    override val perfName get() = "ChatInfo"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val me = Session.me
        val res = ChatApi.rooms
        LaunchedEffect(Unit) { res.load(true) }
        val info = res.data?.firstOrNull { it.room.id == room.id }
        var memberMenu by remember { mutableStateOf<ChMember?>(null) }
        var confirm by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
        var media by remember { mutableStateOf<List<ChatMsg>>(emptyList()) }
        LaunchedEffect(room.id) { runCatching { media = withContext(Dispatchers.IO) { ChatApi.messages(room.id).second }.filter { (it.isImage || it.isVideo) && it.media != null && !it.viewOnce }.takeLast(30).reversed() } }
        val isPrivate = room.type == "private"
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChBar(if (isPrivate) "Contact info" else if (room.type == "group") "Group info" else "Channel info", onBack = { Nav.back() })
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ChAvatar(room.photo, room.name, room.otherUserId ?: room.id, 96.dp, online = isPrivate && info?.online == true)
                    Spacer(Modifier.height(10.dp))
                    Text(room.name, Modifier.padding(horizontal = 16.dp), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, style = TextStyle(textDirection = TextDirection.Content))
                    Text(when { isPrivate -> if (info?.online == true) "online" else "Private chat"; room.type == "channel" -> "Channel · ${info?.members?.size?.takeIf { it > 0 } ?: room.members} subscribers"
                        else -> "Group · ${info?.members?.size?.takeIf { it > 0 } ?: room.members} members" }, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (me != null) Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoAction(Icons.Filled.Chat, "Message") { Nav.back() }
                        if (isPrivate || room.type == "group") InfoAction(Icons.Filled.Call, "Call") { Nav.open(ChCall(room, "audio")) }
                        if (isPrivate || room.type == "group") InfoAction(Icons.Filled.Videocam, "Video") { Nav.open(ChCall(room, "video")) }
                        InfoAction(Icons.Filled.Search, "Search") { Nav.back() }
                    }
                }
                HorizontalDivider(thickness = 6.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                if (!info?.description.isNullOrBlank()) {
                    Text(info!!.description, Modifier.padding(16.dp), fontSize = 15.sp, style = TextStyle(textDirection = TextDirection.Content))
                    HorizontalDivider(thickness = 6.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                }
                if (info != null && me != null) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).tap(RoundedCornerShape(0.dp)) {
                        val m0 = info.muted; scope.launch { runCatching { ChatApi.mute(room.id, !m0) }.onSuccess { res.load(true) }.onFailure { toast(c, Api.friendly(it)) } }
                    }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.NotificationsOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Mute notifications", Modifier.weight(1f).padding(start = 18.dp), fontSize = 16.sp)
                        Switch(info.muted, null)
                    }
                    if (!isPrivate && (info.inviteCode != null || info.canManage)) Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).tap(RoundedCornerShape(0.dp)) {
                        scope.launch { runCatching { info.inviteCode ?: ChatApi.invite(room.id) }.onSuccess { share(c, "Join \"${room.name}\" on YID PLUS: ${Api.BASE}/chat?join=$it") }.onFailure { toast(c, Api.friendly(it)) } }
                    }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f).padding(start = 18.dp)) { Text("Invite link", fontSize = 16.sp); Text(info.inviteCode?.let { "${Api.BASE}/chat?join=$it" } ?: "Create and share a link", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        Icon(Icons.Filled.Share, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(thickness = 6.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                }
                if (media.isNotEmpty()) {
                    ChSection("Media")
                    LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(media, key = { it.id }) { m ->
                            Box(Modifier.size(84.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF263238)).tap(RoundedCornerShape(8.dp)) { openGallery(media.map { GItem(it.media!!, it.isVideo, null, it.nick) }, m.media!!) }, contentAlignment = Alignment.Center) {
                                if (m.isImage) AsyncImage(m.media, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon(Icons.Filled.PlayArrow, "Video", tint = Color.White)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp)); HorizontalDivider(thickness = 6.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                }
                if (info != null && !isPrivate && info.members.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { ChSection("${info.members.size} members" + (info.onlineCount.takeIf { it > 0 }?.let { " · $it online" } ?: "")) }
                    }
                    if (info.canManage) ChRow("Add members", null, { Box(Modifier.size(44.dp).clip(CircleShape).background(ChatLook.bar), contentAlignment = Alignment.Center) { Icon(Icons.Filled.PersonAdd, null, tint = Color.White) } },
                        titleColor = ChatLook.bar) { Nav.open(ChPickUsers(room.id, room.name)) }
                    info.members.sortedWith(compareByDescending<ChMember> { it.admin || it.id == info.createdBy }.thenByDescending { it.online }.thenBy { it.nick.lowercase() }).take(300).forEach { mb ->
                        ChRow(mb.nick + if (mb.id == me?.id) " (You)" else "", if (mb.online) "online" else mb.title.ifBlank { null }, { ChAvatar(mb.photo, mb.nick, mb.id, 44.dp, mb.online) },
                            trail = { if (mb.admin || mb.id == info.createdBy) Text(if (mb.id == info.createdBy) "Owner" else "Admin", fontSize = 12.sp, color = ChatLook.bar, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(ChatLook.bar.copy(alpha = .12f)).padding(horizontal = 6.dp, vertical = 2.dp)) }) {
                            if (mb.id != me?.id && me != null) memberMenu = mb
                        }
                    }
                }
                if (info != null && me != null && info.joined) {
                    Spacer(Modifier.height(8.dp))
                    if (isPrivate && room.otherUserId != null) ChRow("Block ${room.name}", null, { Icon(Icons.Filled.Block, null, Modifier.size(44.dp).padding(10.dp), tint = Color(0xFFD32F2F)) }, titleColor = Color(0xFFD32F2F)) {
                        confirm = "Block ${room.name}? They won't be able to message you." to suspend { ChatApi.block(room.otherUserId ?: ""); toast(c, "Blocked"); Unit }
                    }
                    ChRow(if (isPrivate) "Delete chat" else if (room.type == "group") "Leave group" else "Leave channel", null, { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, Modifier.size(44.dp).padding(10.dp), tint = Color(0xFFD32F2F)) }, titleColor = Color(0xFFD32F2F)) {
                        confirm = "Leave \"${room.name}\"?" to suspend { ChatApi.leave(room.id); res.load(true); Nav.back(); Nav.back(); Unit }
                    }
                    if (!isPrivate && (info.createdBy == me.id || chIsSiteAdmin())) ChRow("Delete for everyone", null, { Icon(Icons.Filled.DeleteForever, null, Modifier.size(44.dp).padding(10.dp), tint = Color(0xFFD32F2F)) }, titleColor = Color(0xFFD32F2F)) {
                        confirm = "Delete \"${room.name}\" and all its messages for everyone?" to suspend { ChatApi.leave(room.id, all = true); res.load(true); Nav.back(); Nav.back(); Unit }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        memberMenu?.let { mb ->
            AlertDialog(onDismissRequest = { memberMenu = null }, title = { Text(mb.nick) }, text = {
                Column {
                    ChDialogRow("Message ${mb.nick}") { memberMenu = null; scope.launch { runCatching { ChatApi.openDm(mb.id) }.onSuccess { id -> Nav.open(Screen.Chat(ChatApi.roomOf(id, "private", mb.nick, mb.photo, mb.id))) }.onFailure { toast(c, Api.friendly(it)) } } }
                    if (info?.canManage == true) {
                        ChDialogRow(if (mb.admin) "Remove admin" else "Make admin") { memberMenu = null; scope.launch { runCatching { ChatApi.setAdmin(room.id, mb.id, !mb.admin) }.onSuccess { res.load(true) }.onFailure { toast(c, Api.friendly(it)) } } }
                        ChDialogRow("Remove from group", Color(0xFFD32F2F)) { memberMenu = null; scope.launch { runCatching { ChatApi.removeMember(room.id, mb.id) }.onSuccess { res.load(true) }.onFailure { toast(c, Api.friendly(it)) } } }
                    }
                }
            }, confirmButton = { TextButton({ memberMenu = null }) { Text("Close") } })
        }
        confirm?.let { (q, act) ->
            AlertDialog(onDismissRequest = { confirm = null }, text = { Text(q, fontSize = 16.sp) },
                confirmButton = { TextButton({ confirm = null; scope.launch { runCatching { act() }.onFailure { toast(c, Api.friendly(it)) } } }) { Text("Yes", color = Color(0xFFD32F2F)) } },
                dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } })
        }
    }
}

@Composable
private fun InfoAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.width(72.dp).clip(RoundedCornerShape(12.dp)).border1().tap(RoundedCornerShape(12.dp), onClick = onClick).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = ChatLook.bar); Text(label, fontSize = 13.sp, color = ChatLook.bar, maxLines = 1)
    }
}
@Composable private fun Modifier.border1(): Modifier = this.then(Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)))

@Composable
internal fun ChDialogRow(text: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    Text(text, Modifier.fillMaxWidth().tap(onClick = onClick).padding(vertical = 14.dp, horizontal = 6.dp), fontSize = 16.sp, color = color)
}

/** User search used by New message and Add members. */
@Composable
private fun UserSearch(hint: String, onUser: (ChMember) -> Unit, selected: Set<String> = emptySet(), header: @Composable () -> Unit = {}) {
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

/** New group or channel: name, description, public/private, members. */
data class ChNewGroup(val type: String) : PageScreen() {
    override val perfName get() = "NewGroup"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var public by remember { mutableStateOf(type == "channel") }
        val picked = remember { mutableStateListOf<ChMember>() }
        var busy by remember { mutableStateOf(false) }
        val what = if (type == "channel") "channel" else "group"
        fun create() {
            if (name.isBlank() || busy) { toast(c, "Give the $what a name"); return }
            busy = true
            scope.launch {
                try {
                    val id = ChatApi.create(type, name.trim(), desc.trim(), public)
                    picked.forEach { runCatching { ChatApi.addMember(id, it.id) } }
                    ChatApi.rooms.load(true)
                    Nav.back(); Nav.open(Screen.Chat(ChatApi.info(id)?.room ?: ChatApi.roomOf(id, type, name.trim())))
                } catch (e: Exception) { toast(c, Api.friendly(e)) } finally { busy = false }
            }
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChBar(if (type == "channel") "New channel" else "New group", onBack = { Nav.back() }) { ChIcon(Icons.Filled.Check, "Create", { create() }) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().firstFocus(), singleLine = true, label = { Text(if (type == "channel") "Channel name" else "Group name") }, textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content))
                OutlinedTextField(desc, { desc = it }, Modifier.fillMaxWidth().padding(top = 6.dp), maxLines = 3, label = { Text("Description (optional)") }, textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp).tap { public = !public }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(if (public) "Public" else "Private", fontSize = 16.sp); Text(if (public) "Anyone can find and join it" else "Only people with the invite link", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(public, null)
                }
                PrimaryButton(if (type == "channel") "Create channel" else "Create group" + if (picked.isNotEmpty()) " with ${picked.size}" else "", { create() }, Modifier.fillMaxWidth().padding(top = 6.dp), enabled = !busy)
                if (picked.isNotEmpty()) Text(picked.joinToString(", ") { it.nick }, Modifier.padding(top = 6.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(if (type == "channel") "Add subscribers (optional)" else "Add members", Modifier.padding(start = 16.dp, top = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            UserSearch("Search people", selected = picked.map { it.id }.toSet(), onUser = { mb -> if (picked.any { it.id == mb.id }) picked.removeAll { it.id == mb.id } else picked.add(mb) })
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
            UserSearch("Search people", selected = added.toSet(), onUser = { mb ->
                if (mb.id !in added) scope.launch { runCatching { ChatApi.addMember(roomId, mb.id) }.onSuccess { added.add(mb.id); toast(c, "${mb.nick} added"); ChatApi.rooms.load(true) }.onFailure { toast(c, Api.friendly(it)) } }
            })
        }
    }
}

/** Join a group by its invite link (https://yidplus.com/chat?join=CODE) or code: preview, then join. */
class ChJoinLink : PageScreen() {
    override val perfName get() = "JoinLink"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var link by remember { mutableStateOf("") }
        var preview by remember { mutableStateOf<JSONObject?>(null) }
        var err by remember { mutableStateOf<String?>(null) }
        val code = remember(link) { Regex("""(?:join|invite|code)=([A-Za-z0-9_-]+)""").find(link)?.groupValues?.get(1) ?: link.trim().substringAfterLast('/').takeIf { Regex("""[A-Za-z0-9_-]{6,40}""").matches(it) } }
        LaunchedEffect(code) { preview = null; err = null; if (code != null) { delay(300); runCatching { Api.get("/api/chat/rooms?invite=" + chEnc(code)).optJSONObject("room") }.onSuccess { preview = it }.onFailure { err = Api.friendly(it) } } }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChBar("Join by invite link", onBack = { Nav.back() })
            OutlinedTextField(link, { link = it }, Modifier.fillMaxWidth().padding(16.dp).firstFocus(), singleLine = true, label = { Text("Paste the invite link") }, placeholder = { Text("${Api.BASE}/chat?join=…") })
            val p = preview
            if (p != null) Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ChAvatar(Api.abs(p.chStr("photo_url").ifBlank { null }), p.chStr("name"), p.chStr("id"), 80.dp)
                Text(p.chStr("name"), Modifier.padding(top = 8.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, style = TextStyle(textDirection = TextDirection.Content))
                Text("${p.optInt("members")} members", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PrimaryButton("Join", {
                    scope.launch { runCatching { ChatApi.join(null, code) }.onSuccess { id -> ChatApi.rooms.load(true); Nav.back(); Nav.open(Screen.Chat(ChatApi.info(id)?.room ?: ChatApi.roomOf(id, p.chStr("type").ifBlank { "group" }, p.chStr("name")))) }.onFailure { toast(c, Api.friendly(it)) } }
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
                    } else Api.post("/api/chat", JSONObject().put("room_id", to.room.id).put("type", if (m.isSticker) "sticker" else "text").put("text", if (m.isSticker) m.text else m.text.removeSuffix(" ↩ Forwarded") + " ↩ Forwarded"))
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
