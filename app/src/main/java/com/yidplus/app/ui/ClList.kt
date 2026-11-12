package com.yidplus.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.yidplus.app.data.Api
import com.yidplus.app.data.Session
import com.yidplus.app.data.TgChannel
import kotlinx.coroutines.launch

/* ═════════════ CL: chat list extras — ☰ side menu, selection bar, mute choices, folders, chat settings, Telegram rows ═════════════ */

/** The site's ☰ menu (yidplus-chat.html #hamburger-overlay): profile header, Saved Messages, Chat Folders, New Group, Settings, Admin Panel. */
@Composable
internal fun ClDrawer(open: Boolean, onClose: () -> Unit) {
    BackHandler(enabled = open) { onClose() }
    AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)).tap(RoundedCornerShape(0.dp)) { onClose() })
    }
    AnimatedVisibility(open, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
        val me = Session.me
        Column(Modifier.fillMaxHeight().widthIn(max = 300.dp).fillMaxWidth(.82f).background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().background(ChatLook.bar).tap(RoundedCornerShape(0.dp)) { onClose(); Nav.go(Tab.Profile) }.padding(16.dp)) {
                ChAvatar(me?.photo, me?.nickname ?: "User", me?.id ?: "me", 64.dp)
                Spacer(Modifier.height(10.dp))
                Text(me?.nickname ?: "User", color = barContent(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content))
                Text("@" + (me?.nickname ?: "user"), color = barContent().copy(alpha = .8f), fontSize = 14.sp, maxLines = 1, style = TextStyle(textDirection = TextDirection.Content))
            }
            ClDrawerItem(Sym.Person, "My Profile", Modifier.firstFocus(always = true)) { onClose(); Nav.go(Tab.Profile) }
            ClDrawerItem(Sym.Bookmark, "Saved Messages") { onClose(); Nav.open(ChSaved()) }
            ClDrawerItem(Sym.Folder, "Chat Folders") { onClose(); Nav.open(ClFolderManager()) }
            ClDrawerItem(Sym.Group, "New Group") { onClose(); Nav.open(ChNewGroup("group")) }
            ClDrawerItem(Sym.Campaign, "New Channel") { onClose(); Nav.open(ChNewGroup("channel")) }
            ClDrawerItem(Sym.Link, "Join by invite link") { onClose(); Nav.open(ChJoinLink()) }
            ClDrawerItem(Sym.Settings, "Chat settings") { onClose(); Nav.open(ClChatSettings()) }
            if (chIsSiteAdmin()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ClDrawerItem(Sym.AdminPanelSettings, "Admin Panel") { onClose(); Nav.open(AdminPage()) }
            }
        }
    }
}

@Composable
private fun ClDrawerItem(icon: ImageVector, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).tap(RoundedCornerShape(0.dp), onClick = onClick).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Text(text, Modifier.padding(start = 24.dp), fontSize = 16.sp)
    }
}

/** WhatsApp's selection bar (long-press a chat): count, then Pin, Mute, Mark as read, Delete, Info. */
@Composable
internal fun ClSelectBar(n: Int, allPinned: Boolean, allMuted: Boolean, single: Boolean, onClose: () -> Unit, onPin: () -> Unit, onMute: () -> Unit,
                         onRead: () -> Unit, onDelete: () -> Unit, onInfo: () -> Unit) {
    Surface(color = ChatLook.bar, contentColor = barContent(), shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ChIcon(Sym.Close, "Cancel selection", onClose)
            Text("$n", Modifier.weight(1f).padding(start = 8.dp), color = LocalContentColor.current, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            ChIcon(if (allPinned) Sym.PushPinFill else Sym.PushPin, if (allPinned) "Unpin" else "Pin", onPin)
            ChIcon(if (allMuted) Sym.Notifications else Sym.NotificationsOff, if (allMuted) "Unmute" else "Mute", onMute)
            ChIcon(Sym.MarkChatRead, "Mark as read", onRead)
            ChIcon(Sym.Delete, "Delete", onDelete)
            if (single) ChIcon(Sym.Info, "Info", onInfo)
        }
    }
}

/** WhatsApp's mute choices. The server keeps only on/off; 8 hours and 1 week are unmuted by the app when the time is up. */
@Composable
internal fun ClMuteDialog(title: String, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    var pick by remember { mutableStateOf(0) }
    val opts = listOf("8 hours" to 8L * 3600_000L, "1 week" to 7L * 24 * 3600_000L, "Always" to -1L)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Other members won't see that you muted this chat.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            opts.forEachIndexed { i, (label, _) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).tap(RoundedCornerShape(8.dp)) { pick = i }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = pick == i, onClick = null); Text(label, Modifier.padding(start = 12.dp), fontSize = 16.sp)
                }
            }
        }
    }, confirmButton = { TextButton({ onPick(opts[pick].second) }) { Text("OK") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

/** Status ring around an avatar (chat.js has-status-ring): green = something new, grey = all seen. */
@Composable
internal fun ClRingAvatar(url: String?, name: String, id: String, size: Dp, online: Boolean, ring: Boolean, seen: Boolean) {
    if (!ring) { ChAvatar(url, name, id, size, online); return }
    Box(Modifier.size(size).border(2.dp, if (seen) Color(0xFFB0BEC5) else ChatLook.unread, CircleShape).padding(3.dp)) { ChAvatar(url, name, id, size - 6.dp, online) }
}

/** A joined Telegram channel in the chat list (chat.js _tgChannelRow): photo or blue letter, last post, unread / Join. */
@Composable
internal fun ClTgRow(t: TgChannel, onJoin: (() -> Unit)?) {
    Column {
        Row(Modifier.fillMaxWidth().height(72.dp).restoreFocus("tg:" + t.username).tap(RoundedCornerShape(0.dp)) { Nav.lastOpened = "tg:" + t.username; Nav.open(Screen.Channel(t)) }
            .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).clip(CircleShape).background(Color(0xFF229ED9)), contentAlignment = Alignment.Center) {
                Text(chInitials(t.title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (!t.photo.isNullOrBlank()) coil.compose.AsyncImage(t.photo, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.title, Modifier.weight(1f).padding(end = 10.dp), fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(textDirection = TextDirection.Content, textAlign = chLeadAlign()))
                    if (t.joined) Text(chatTime(t.lastAt), fontSize = 12.sp, color = if (t.unread > 0) ChatLook.unread else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val pv = if (t.joined && t.lastText.isNotBlank()) t.lastText.take(60) else if (t.joined && t.lastMedia.isNotBlank())
                        (mapOf("photo" to "📷 Photo", "video" to "🎥 Video", "audio" to "🎵 Audio")[t.lastMedia] ?: "📎 File") else clMembersLabel(t.members)
                    Text(pv, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = TextStyle(textDirection = TextDirection.Content, textAlign = chLeadAlign()))
                    if (t.unread > 0) ChBadge(t.unread)
                    else if (!t.joined && onJoin != null) Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF229ED9)).tap(RoundedCornerShape(14.dp), onClick = onJoin).padding(horizontal = 14.dp, vertical = 5.dp)) {
                        Text("Join", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 80.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
    }
}

/** chat.js _tgMembersLabel */
internal fun clMembersLabel(n: Int): String = when { n <= 0 -> "Telegram channel"; n >= 1000 -> String.format(java.util.Locale.US, "%.1fK members", n / 1000f).replace(".0K", "K"); else -> "$n members" }

/** chat.js openFolderManager: list my folders (delete), create a new one (name + chats). */
class ClFolderManager : PageScreen() {
    override val perfName get() = "Folders"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var list by remember { mutableStateOf<List<ClFolder>?>(null) }
        var name by remember { mutableStateOf("") }
        val picked = remember { mutableStateListOf<String>() }
        var ask by remember { mutableStateOf<ClFolder?>(null) }
        suspend fun load() { list = runCatching { ClApi.folders() }.getOrElse { toast(c, Api.friendly(it)); emptyList() } }
        LaunchedEffect(Unit) { load(); ChatApi.rooms.load() }
        val rooms = ChatApi.rooms.data.orEmpty()
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChBar("Chat folders", onBack = { Nav.back() })
            LazyColumn(Modifier.fillMaxSize()) {
                item { Text("Custom folders help you organize your chats. Pick the chats for a folder, then tap its chip above the chat list.", Modifier.padding(16.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                val l = list
                if (l == null) item { ChSkeleton(2) }
                else if (l.isEmpty()) item { Text("No folders yet", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else items(l, key = { "f" + it.id }) { f ->
                    ChRow(f.name, if (f.roomIds.isEmpty()) "No chats" else "${f.roomIds.size} chats", { Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(Sym.Folder, null, tint = MaterialTheme.colorScheme.primary) } },
                        trail = { ChIcon(Sym.Delete, "Delete folder ${f.name}", { ask = f }, tint = Color(0xFFD32F2F)) }) { ask = f }
                }
                item {
                    ChSection("New folder")
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(name, { name = it.take(30) }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Folder name") },
                            textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content))
                        Spacer(Modifier.width(8.dp))
                        PrimaryButton("Create", {
                            if (name.isBlank()) { toast(c, "Give the folder a name"); return@PrimaryButton }
                            scope.launch { runCatching { ClApi.createFolder(name, picked.toList(), l?.size ?: 0) }.onSuccess { toast(c, "Folder created!"); name = ""; picked.clear(); ClFolders.version++; load() }.onFailure { toast(c, Api.friendly(it)) } }
                        })
                    }
                    Text(if (picked.isEmpty()) "Tap chats to put them in the folder" else "${picked.size} chats picked", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(rooms, key = { "r" + it.room.id }) { ri ->
                    val on = ri.room.id in picked
                    ChRow(ri.room.name, if (ri.isGroup) "Group" else if (ri.isChannel) "Channel" else "Private chat", { ChAvatar(ri.room.photo, ri.room.name, ri.room.otherUserId ?: ri.room.id, 44.dp) },
                        trail = { Checkbox(on, null) }) { if (on) picked.remove(ri.room.id) else picked.add(ri.room.id) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
        ask?.let { f ->
            AlertDialog(onDismissRequest = { ask = null }, text = { Text("Delete the folder \"${f.name}\"? Your chats stay.", fontSize = 16.sp) },
                confirmButton = { TextButton({ ask = null; scope.launch { runCatching { ClApi.deleteFolder(f.id) }.onSuccess { ClFolders.version++; load() }.onFailure { toast(c, Api.friendly(it)) } } }) { Text("Delete", color = Color(0xFFD32F2F)) } },
                dismissButton = { TextButton({ ask = null }) { Text("Cancel") } })
        }
    }
}

object ClFolders { var version by mutableIntStateOf(0) }

/** The site's ☰ → Settings (chat-settings-modal): Dark mode, Message text size, Read receipts. */
class ClChatSettings : PageScreen() {
    override val perfName get() = "ChatSettings"
    @Composable override fun Content() {
        val c = ctx()
        var font by remember { mutableStateOf(ClLocal.font(c)) }
        var receipts by remember { mutableStateOf(ClLocal.receipts(c)) }
        val dark = com.yidplus.app.data.Prefs.theme == 2
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState())) {
            ChBar("Chat settings", onBack = { Nav.back() })
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).firstFocus().tap(RoundedCornerShape(0.dp)) {
                val on = !dark; com.yidplus.app.data.Prefs.setThemeMode(if (on) 2 else 1); toast(c, if (on) "🌙 Dark mode on" else "☀️ Light mode on")
            }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.DarkMode, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Dark mode", Modifier.weight(1f).padding(start = 18.dp), fontSize = 16.sp); Switch(dark, null)
            }
            HorizontalDivider()
            Row(Modifier.padding(start = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.FormatSize, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Text("Message text size", Modifier.padding(start = 18.dp), fontSize = 16.sp)
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("sm" to "Small", "md" to "Medium", "lg" to "Large").forEach { (k, label) ->
                    val on = font == k
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .tap(RoundedCornerShape(18.dp)) { font = k; ClLocal.setFont(c, k); toast(c, "Font size: $label") }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(label, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = when (k) { "sm" -> 14.sp; "lg" -> 18.sp; else -> 16.sp })
                    }
                }
            }
            Text("Hello! Messages will look like this.", Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(10.dp)).background(ChatLook.mine()).padding(10.dp),
                color = ChatLook.ink(), fontSize = (15f * ClLocal.fontScale(c)).sp)
            Spacer(Modifier.height(12.dp)); HorizontalDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).tap(RoundedCornerShape(0.dp)) {
                receipts = !receipts; ClLocal.setReceipts(c, receipts); toast(c, if (receipts) "✓✓ Read receipts on" else "Read receipts off")
            }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.DoneAll, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f).padding(start = 18.dp)) { Text("Read receipts", fontSize = 16.sp); Text("Let others see when you've read their messages", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(receipts, null)
            }
        }
    }
}

/** Small screens: the status circles become one 56 dp row (WhatsApp's "Status" row): stacked rings of who posted, tap = the first unseen. */
@Composable
internal fun ClStatusRow(all: List<StUser>) {
    val me = Session.me ?: return
    val others = all.filter { it.userId != me.id && it.slides.isNotEmpty() }
    val mine = all.firstOrNull { it.userId == me.id && it.slides.isNotEmpty() }
    val unseen = others.filter { !Statuses.allSeen(it) }
    Column {
        Row(Modifier.fillMaxWidth().height(56.dp).tap(RoundedCornerShape(0.dp)) {
            val first = unseen.firstOrNull() ?: others.firstOrNull()
            if (first != null) Nav.open(StatusViewerPage(first.userId)) else if (mine != null) Nav.open(StatusViewerPage(me.id)) else Nav.open(PostStatusPage())
        }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(if (others.isEmpty()) 40.dp else (40 + 14 * (others.size.coerceAtMost(3) - 1)).dp).height(40.dp)) {
                if (others.isEmpty()) Box(Modifier.size(40.dp).clip(CircleShape).background(ChatLook.unread.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                    Icon(Sym.Add, null, tint = ChatLook.unread, modifier = Modifier.size(22.dp)) }
                others.take(3).forEachIndexed { i, u ->
                    Box(Modifier.padding(start = (14 * i).dp)) { ClRingAvatar(u.photo, u.nick, u.userId, 40.dp, false, ring = true, seen = Statuses.allSeen(u)) }
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Status", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(when { unseen.isNotEmpty() -> "${unseen.size} new update" + (if (unseen.size > 1) "s" else ""); others.isNotEmpty() -> "Seen all updates"; else -> "Tap to add a status update" },
                    fontSize = 13.sp, color = if (unseen.isNotEmpty()) ChatLook.unread else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Box(Modifier.size(40.dp).clip(CircleShape).tap(CircleShape) { Nav.open(PostStatusPage()) }.semantics { contentDescription = "Add status" }, contentAlignment = Alignment.Center) {
                Icon(Sym.PhotoCamera, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
    }
}

/** The wide rounded "Search…" field at the top of the chat list (current WhatsApp). One tap / D-pad OK opens the search. 40 dp on 480x640, 48 dp elsewhere. */
@Composable
internal fun ClSearchPill(compact: Boolean, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = if (compact) 6.dp else 8.dp, bottom = 2.dp).height(if (compact) 40.dp else 48.dp)
        .clip(shape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (ChatLook.dark()) .55f else .8f))
        .tap(shape, onClick = onOpen).semantics { contentDescription = "Search chats" }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Sym.Search, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Search…", Modifier.padding(start = 12.dp), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}


/**
 * 480x640 (Qin F21, ~427 dp tall): the bar IS the search field, so the chat list keeps room for 5 rows.
 * One 48 dp bar row: a rounded pill with ☰ (menu) at the start, "Search chats" in the middle (opens the search) and
 * the AI chat avatar at the end. Three D-pad stops. No pencil (the green button starts a chat).
 */
@Composable
internal fun ClCompactSearchBar(onMenu: (() -> Unit)?, onSearch: () -> Unit, onAi: (() -> Unit)?) {
    val ink = barContent()
    val shape = RoundedCornerShape(22.dp)
    val pillBg = if (ink.luminance() > .5f) Color.White.copy(alpha = .17f) else (if (ChatLook.dark()) Color(0xFF202C33) else Color(0xFFF0F2F5))
    Surface(color = ChatLook.bar, contentColor = ink, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp, vertical = 5.dp).clip(shape).background(pillBg),
            verticalAlignment = Alignment.CenterVertically) {
            if (onMenu != null) Box(Modifier.size(40.dp).tap(CircleShape, onClick = onMenu).semantics { contentDescription = "Menu" }, contentAlignment = Alignment.Center) {
                Icon(Sym.Menu, null, tint = ink, modifier = Modifier.size(22.dp)) }
            else Spacer(Modifier.width(8.dp))
            Row(Modifier.weight(1f).fillMaxHeight().tap(shape, onClick = onSearch).semantics { contentDescription = "Search chats" }.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.Search, null, Modifier.size(20.dp), tint = ink.copy(alpha = .85f))
                Text("Search chats", Modifier.padding(start = 10.dp), fontSize = 15.sp, color = ink.copy(alpha = .85f), maxLines = 1)
            }
            if (onAi != null) Box(Modifier.size(40.dp).tap(CircleShape, onClick = onAi).semantics { contentDescription = "AI chat" }, contentAlignment = Alignment.Center) {
                AiAvatar(28.dp) }
        }
    }
}

/** 480x640: the Status entry as the first chip (WhatsApp keeps statuses out of the chat list too): a ring dot when there is something new. */
@Composable
internal fun ClStatusChip(all: List<StUser>) {
    val me = Session.me ?: return
    val others = all.filter { it.userId != me.id && it.slides.isNotEmpty() }
    val mine = all.firstOrNull { it.userId == me.id && it.slides.isNotEmpty() }
    val unseen = others.filter { !Statuses.allSeen(it) }
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.height(26.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant).tap(shape) {
            val first = unseen.firstOrNull() ?: others.firstOrNull()
            if (first != null) Nav.open(StatusViewerPage(first.userId)) else if (mine != null) Nav.open(StatusViewerPage(me.id)) else Nav.open(PostStatusPage())
        }.semantics { contentDescription = "Status" + if (unseen.isNotEmpty()) ", ${unseen.size} new" else "" }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(CircleShape).border(2.dp, if (unseen.isNotEmpty()) ChatLook.unread else MaterialTheme.colorScheme.outline, CircleShape))
        Text("Status", Modifier.padding(start = 6.dp), fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        if (unseen.isNotEmpty()) { Spacer(Modifier.width(5.dp)); ChBadge(unseen.size) }
    }
}
