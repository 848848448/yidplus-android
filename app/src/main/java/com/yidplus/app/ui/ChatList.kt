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
import androidx.compose.material.icons.automirrored.filled.Chat
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
import com.yidplus.app.data.TgChannel
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.semantics.selected
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material3.Checkbox


/* ═════════════════════════ Chat list ═════════════════════════ */

// The site's tabs (All / Private Chats / Groups / Channels) plus WhatsApp's Unread chip; custom folders follow, then "+".
private val CHAT_TABS = listOf("all" to "All", "unread" to "Unread", "private" to "Private", "groups" to "Groups", "channels" to "Channels")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen() {
    val me = Session.me
    val c = ctx()
    val res = remember(me?.id) { ChatApi.rooms }
    val tgRes = remember(me?.id) { Store.res("/api/telegram-channels", TgChannel::list) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(res, Net.online) { res.load(force = Net.online && res.offline) }
    // Chat list refresh while on screen: every 20 s (was 6 s like chat.js; slowed for the database read limit).
    // New messages still arrive at once through notifications/push; pull-to-refresh and resume reload immediately.
    LaunchedEffect(res) { while (isActive) { delay(20_000); res.load(force = true) } }
    // chat.js _loadTgChannels: the Telegram list every 60 s.
    LaunchedEffect(tgRes) { tgRes.load(); while (isActive) { delay(60_000); tgRes.load(force = true) } }
    val list = res.data
    LaunchedEffect(list) { if (list != null) { Perf.shown("Chats"); Badges.chats = list.filter { !it.muted }.sumOf { it.room.unread } } }
    // Mute "8 hours" / "1 week": the server keeps on/off only, so the app unmutes when the time is up.
    LaunchedEffect(list) {
        val due = ClLocal.expiredMutes(c); if (due.isEmpty()) return@LaunchedEffect
        due.forEach { id -> runCatching { ChatApi.mute(id, false) }; ClLocal.setMuteUntil(c, id, 0) }; res.load(true)
    }
    var tab by rememberSaveable { mutableStateOf(ClLocal.tab(c)) }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var folders by remember { mutableStateOf<List<ClFolder>>(emptyList()) }
    LaunchedEffect(me?.id, ClFolders.version) { if (me != null) runCatching { ClApi.folders() }.onSuccess { folders = it } }
    var searching by rememberSaveable { mutableStateOf(false) }
    var q by rememberSaveable { mutableStateOf("") }
    var fabMenu by remember { mutableStateOf(false) }
    var drawer by remember { mutableStateOf(false) }
    var pulling by remember { mutableStateOf(false) }
    val sel = remember { mutableStateListOf<String>() }
    var muteAsk by remember { mutableStateOf<List<ChatRoomInfo>?>(null) }
    var delAsk by remember { mutableStateOf<List<ChatRoomInfo>?>(null) }
    val kb = LocalSoftwareKeyboardController.current
    val focusMgr = androidx.compose.ui.platform.LocalFocusManager.current
    // Gap 10: leaving search (✕ or Back) also closes the keyboard.
    fun closeSearch() { searching = false; q = ""; kb?.hide(); focusMgr.clearFocus(force = true) }
    fun setTab(t: String) { tab = t; folder = null; ClLocal.setTab(c, t) }
    BackHandler(enabled = searching) { closeSearch() }
    BackHandler(enabled = sel.isNotEmpty()) { sel.clear() }
    // Gap 2: rows whose site preview is only "[system]" / "[call]" / "[voice]" get their real last line, fetched quietly once and cached.
    LaunchedEffect(list) { list?.take(24)?.filter { chBarePreview(it.rawPreview) }?.forEach { ri -> launch { ChatCache.prefetch(ri.room.id) } } }
    val pins = remember(ClLocal.pinVersion, me?.id) { ClLocal.pins(c) }
    val stUsers = Statuses.res.data
    LaunchedEffect(me?.id) { if (me != null) Statuses.res.load() }
    val ringOf = remember(stUsers) { stUsers.orEmpty().filter { it.slides.isNotEmpty() && it.userId != me?.id }.associateBy { it.userId } }
    val selRooms = list.orEmpty().filter { it.room.id in sel }
    // The Qin F21 (480x640, ~427 dp tall): status circles + AI row + chips left no room for a single chat. There they shrink to one 56 dp row each.
    val compact = LocalConfiguration.current.screenHeightDp < 600
    val listState = rememberLazyListState()
    var pillShown by remember { mutableStateOf(true) }
    val pillScroll = remember { object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
            if (available.y < -4f) pillShown = false else if (available.y > 4f) pillShown = true
            return androidx.compose.ui.geometry.Offset.Zero
        }
    } }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        when {
            sel.isNotEmpty() -> ClSelectBar(sel.size, selRooms.isNotEmpty() && selRooms.all { it.room.id in pins }, selRooms.isNotEmpty() && selRooms.all { it.muted }, sel.size == 1,
                onClose = { sel.clear() },
                onPin = {
                    val unpin = selRooms.all { it.room.id in pins }
                    selRooms.forEach { if ((it.room.id in pins) == unpin) ClLocal.togglePin(c, it.room.id) }
                    toast(c, if (unpin) "Unpinned" else "Pinned on this phone"); sel.clear()
                },
                onMute = {
                    if (selRooms.all { it.muted }) scope.launch {
                        selRooms.forEach { ri -> runCatching { ChatApi.mute(ri.room.id, false) }; ClLocal.setMuteUntil(c, ri.room.id, 0) }
                        toast(c, "🔊 Unmuted"); sel.clear(); res.load(true)
                    } else muteAsk = selRooms
                },
                onRead = {
                    scope.launch { selRooms.forEach { ri -> runCatching { ClApi.markRead(c, ri.room.id) } }; toast(c, "Marked as read"); sel.clear(); res.load(true) }
                },
                onDelete = { delAsk = selRooms },
                onInfo = { selRooms.firstOrNull()?.let { Nav.open(ChInfo(it.room)) }; sel.clear() })
            searching -> Unit
            // Avrumy 05:02: no pencil and no small search icon up here — the green button starts a chat, the wide "Search…" field sits under the bar.
            compact && me != null -> ClCompactSearchBar({ drawer = true }, { searching = true }, { Nav.open(AiChatPage()) })
            else -> ChBar("Chats", sub = if (me == null) "Guest · featured groups" else null,
                lead = if (me != null) ({ Box(Modifier.size(40.dp).tap(CircleShape) { drawer = true }.semantics { contentDescription = "Menu" }, contentAlignment = Alignment.Center) {
                    Icon(Sym.Menu, null, tint = LocalContentColor.current, modifier = Modifier.size(24.dp)) } }) else null)
        }
        if (me == null) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.Lock, null, Modifier.size(18.dp)); Text("  Sign in to see and send your own chats", Modifier.weight(1f), fontSize = 14.sp)
                PrimaryButton("Sign in", { Nav.go(Tab.Profile) })
            }
        }
        if (searching) { ClSearchScreen(list ?: emptyList()) { closeSearch() }; return@Column }
        // WhatsApp's rounded "Search…" field: hides while you scroll down the list, comes back as soon as you scroll up (or are at the top).
        val headerShown = pillShown || (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 24)
        if (!(compact && me != null)) AnimatedVisibility(headerShown, enter = expandVertically(), exit = shrinkVertically()) {
            ClSearchPill(compact) { searching = true }
        }
        // Gap 5: the chips scroll sideways with room at the end, so none is ever cut mid-word at 480 px. Folders (chat.js renderChatFoldersRow) follow the tabs.
        // 480x640: the chips row slides away with the search on scroll-down and comes back on scroll-up, so 5+ chats show.
        if (me != null) AnimatedVisibility(!compact || headerShown, enter = expandVertically(), exit = shrinkVertically()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 20.dp, top = if (compact) 0.dp else 8.dp, bottom = if (compact) 2.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (compact && tab == "all" && folder == null) ClStatusChip(stUsers.orEmpty())
            CHAT_TABS.forEach { (k, label) ->
                val n = list?.let { l -> l.filter { matchTab(it, k) && !it.muted }.sumOf { it.room.unread } } ?: 0
                ClChip(label, folder == null && tab == k, if (k == "unread") 0 else n) { setTab(k) }
            }
            folders.forEach { f ->
                val n = list?.let { l -> l.filter { it.room.id in f.roomIds && !it.muted }.sumOf { it.room.unread } } ?: 0
                ClChip(f.name, folder == f.id, n, icon = Sym.Folder) { folder = if (folder == f.id) null else f.id }
            }
            Box(Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).tap(CircleShape) { Nav.open(ClFolderManager()) }
                .semantics { contentDescription = "Chat folders" }, contentAlignment = Alignment.Center) { Icon(Sym.Add, null, Modifier.size(20.dp)) }
        }
        }
        // Gap 3: no spinner over the list, ever — the cached list stays and refreshes silently; only a pull the person makes shows the pull indicator.
        Box(Modifier.weight(1f).fillMaxWidth().pointerInput(tab, folder) {
            // chat.js _initChatTabSwipe: a clear sideways swipe on the list moves to the next / previous tab.
            var dx = 0f
            detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
                val i = CHAT_TABS.indexOfFirst { it.first == tab }
                if (dx < -140 && i < CHAT_TABS.lastIndex) setTab(CHAT_TABS[i + 1].first) else if (dx > 140 && i > 0) setTab(CHAT_TABS[i - 1].first)
            }) { _, d -> dx += d }
        }) {
            PullToRefreshBox(isRefreshing = pulling, onRefresh = { pulling = true; scope.launch { res.load(true); tgRes.load(true); pulling = false } }, modifier = Modifier.fillMaxSize()) {
                when {
                    list == null && res.error != null -> Problem(res.error!!, res.offline, { scope.launch { res.load(true) } })
                    list == null -> ChSkeleton()
                    else -> {
                        val f = folders.firstOrNull { it.id == folder }
                        val shown = remember(list, tab, f, pins) {
                            list.filter { ri -> (if (f != null) ri.room.id in f.roomIds else matchTab(ri, tab)) && !(ri.isPrivate && !ri.hasMessages && ri.room.unread == 0) }
                                .sortedBy { val p = pins.indexOf(it.room.id); if (p < 0) Int.MAX_VALUE else p }
                        }
                        val tg = if (f == null && (tab == "all" || tab == "channels" || tab == "unread")) tgRes.data.orEmpty().filter { (it.joined || me == null) && (tab != "unread" || it.unread > 0) }.sortedByDescending { it.lastAt ?: "" } else emptyList()
                        LazyColumn(Modifier.fillMaxSize().nestedScroll(pillScroll), state = listState, contentPadding = fabSafePadding(hasFab = me != null)) {
                            if (me != null && tab == "all" && f == null && q.isBlank() && !compact) item(key = "nw-status") { StatusStrip() }
                            if (me != null && (tab == "all" || tab == "private") && f == null && q.isBlank() && !compact) item(key = "nw-ai") { AiChatRow(compact) { Nav.open(AiChatPage()) } }
                            if (shown.isEmpty() && tg.isEmpty()) item {
                                if (me == null) ChEmpty(Sym.Forum, "No public groups right now")
                                else if (f != null) ChEmpty(Sym.Folder, "No chats in this folder", action = "Edit folders") { Nav.open(ClFolderManager()) }
                                else ChEmpty(Sym.Forum, if (tab == "all") "No chats yet. Start a conversation or create a group to get going" else "Nothing here", action = if (tab == "all") "Start Chatting" else null) { Nav.open(ChNewChat()) }
                            }
                            items(shown, key = { it.room.id }) { r ->
                                val ring = ringOf[r.room.otherUserId ?: ""]
                                ChRoomRow(r, if (r === shown.first()) Modifier.firstFocus() else Modifier, compact = compact, pinned = r.room.id in pins, selected = r.room.id in sel, selecting = sel.isNotEmpty(),
                                    ring = ring != null, ringSeen = ring != null && Statuses.allSeen(ring),
                                    onRing = { ring?.let { Nav.open(StatusViewerPage(it.userId)) } },
                                    onLong = { if (me != null) { if (r.room.id in sel) sel.remove(r.room.id) else sel.add(r.room.id) } },
                                    onToggle = { if (r.room.id in sel) sel.remove(r.room.id) else sel.add(r.room.id) })
                            }
                            items(tg, key = { "tg:" + it.username }) { t -> ClTgRow(t, if (me == null) null else ({
                                scope.launch { runCatching { Api.post("/api/telegram-join", JSONObject().put("username", t.username)) }.onSuccess { toast(c, "✅ Joined"); tgRes.load(true) }.onFailure { toast(c, Api.friendly(it)) } } })) }
                        }
                    }
                }
            }
            // Gap 13: WhatsApp's green round button for a new chat / group / channel (the site has the same green button).
            // 480x640: the button slides away while you scroll down the list (so no row is ever hidden under it) and is back on scroll-up / at the top.
            if (me != null && sel.isEmpty()) androidx.compose.animation.AnimatedVisibility(!compact || headerShown, Modifier.align(Alignment.BottomEnd),
                enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()) {
            Box(Modifier.padding(if (compact) 10.dp else 16.dp)) {
                Box(Modifier.size(if (compact) 46.dp else 56.dp).clip(RoundedCornerShape(if (compact) 14.dp else 16.dp)).background(ChatLook.unread).tap(RoundedCornerShape(16.dp)) { fabMenu = true }.semantics { contentDescription = "Start new chat" },
                    contentAlignment = Alignment.Center) { Icon(Sym.Chat, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                DropdownMenu(fabMenu, { fabMenu = false }) {
                    ChMenuItem(Sym.Person, "New chat") { fabMenu = false; Nav.open(ChNewChat()) }
                    ChMenuItem(Sym.Group, "New group") { fabMenu = false; Nav.open(ChNewGroup("group")) }
                    ChMenuItem(Sym.Campaign, "New channel") { fabMenu = false; Nav.open(ChNewGroup("channel")) }
                    ChMenuItem(Sym.Link, "Join by invite link") { fabMenu = false; Nav.open(ChJoinLink()) }
                }
            }
            }
        }
    }
    ClDrawer(drawer) { drawer = false }
    }
    ClListDialogs(muteAsk, delAsk, { muteAsk = null }, { delAsk = null }) { sel.clear(); scope.launch { res.load(true) } }
}

@Composable
private fun ClChip(label: String, on: Boolean, n: Int, icon: ImageVector? = null, onClick: () -> Unit) {
    val small = LocalConfiguration.current.screenHeightDp < 600
    Row(Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = .14f) else MaterialTheme.colorScheme.surfaceVariant)
        .tap(RoundedCornerShape(18.dp), onClick = onClick).semantics { contentDescription = label; selected = on }
        .padding(horizontal = if (small) 11.dp else 14.dp, vertical = if (small) 3.5.dp else 7.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(4.dp)) }
        Text(label, maxLines = 1, softWrap = false, fontSize = if (small) 13.5.sp else 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = TextStyle(textDirection = TextDirection.Content))
        if (n > 0) { Spacer(Modifier.width(6.dp)); ChBadge(n) }
    }
}

private fun matchTab(r: ChatRoomInfo, tab: String) = when (tab) {
    "private" -> r.isPrivate; "groups" -> r.isGroup; "channels" -> r.isChannel; "unread" -> r.room.unread > 0; else -> true
}

@Composable
internal fun ChMenuItem(icon: ImageVector, text: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text, fontSize = 16.sp, color = color) }, leadingIcon = { Icon(icon, null, tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color) }, onClick = onClick)
}

/** One chat row (WhatsApp: 72 dp, 50 dp avatar, name ellipsizes before the time). Long-press (or the keypad's long OK / Menu key) selects it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChRoomRow(ri: ChatRoomInfo, modifier: Modifier, compact: Boolean = false, pinned: Boolean = false, selected: Boolean = false, selecting: Boolean = false,
                       ring: Boolean = false, ringSeen: Boolean = false, onRing: () -> Unit = {}, onLong: (() -> Unit)? = null, onToggle: () -> Unit = {}) {
    val r = ri.room
    val c = ctx()
    val open = { if (selecting) onToggle() else { Nav.lastOpened = "room:" + r.id; Nav.open(Screen.Chat(r)) } }
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val sel = Nav.chatDetail?.room?.id == r.id
    Column(Modifier.background(when { selected -> ChatLook.unread.copy(alpha = .16f); sel -> MaterialTheme.colorScheme.primaryContainer; else -> Color.Transparent })) {
        Row(modifier.fillMaxWidth().height(if (compact) 48.dp else 72.dp).restoreFocus("room:" + r.id)
            .then(if (focused && Keys.keypad) Modifier.background(Gold.copy(alpha = 0.18f)).border(2.dp, Gold) else Modifier)
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyUp && e.key == Key.Menu && onLong != null) { onLong(); true } else false }
            .combinedClickable(interactionSource = src, indication = androidx.compose.material3.ripple(), onLongClick = onLong, onClick = open)
            .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                // The site shows "online" only to site admins (chat.js renderChatList: c.online && isAnyAdmin()).
                val av = @Composable { ClRingAvatar(r.photo, r.name, r.otherUserId ?: r.id, if (compact) 40.dp else 50.dp, online = ri.isPrivate && ri.online && chIsSiteAdmin(), ring = ring, seen = ringSeen) }
                if (ring && !selecting) Box(Modifier.clip(CircleShape).tap(CircleShape, onClick = onRing)) { av() } else av()
                if (selected) Box(Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(1.dp).clip(CircleShape).background(ChatLook.unread), contentAlignment = Alignment.Center) {
                    Icon(Sym.Check, "Selected", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Title takes the room it has and ellipsizes; the time always keeps its place at the far end (RTL names can't push it away).
                    Text(r.name, Modifier.weight(1f).padding(end = 10.dp), fontSize = if (compact) 15.5.sp else 16.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(textDirection = TextDirection.Content, textAlign = chLeadAlign()))
                    Text(chatTime(r.lastTime), fontSize = 12.sp, color = if (r.unread > 0 && !ri.muted) ChatLook.unread else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.height(if (compact) 1.dp else 3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val draft = remember(r.id, Nav.stack.size) { ChDrafts.get(c, r.id) }
                    val v = ChatCache.version
                    val pv = remember(ri, v) { chatPreview(ri) }
                    Text(if (draft.isNotBlank()) "Draft: $draft" else pv.ifBlank { if (ri.isChannel) "Channel" else if (ri.isGroup) "Group" else " " }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = if (compact) 13.5.sp else 14.sp, color = if (draft.isNotBlank()) Color(0xFF25A45A) else MaterialTheme.colorScheme.onSurfaceVariant, style = TextStyle(textDirection = TextDirection.Content, textAlign = chLeadAlign()))
                    if (ri.muted) Icon(Sym.NotificationsOff, "Muted", Modifier.padding(start = 4.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (pinned) Icon(Sym.PushPinFill, "Pinned", Modifier.padding(start = 4.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp)); ChBadge(r.unread, ri.muted)
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = if (compact) 70.dp else 80.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
    }
}

/** Row text sits at the row's leading edge whatever script it is in (a Yiddish name in an English UI starts at the left, like WhatsApp), so it never drifts over to the time. */
@Composable
internal fun chLeadAlign(): TextAlign = if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Ltr) TextAlign.Left else TextAlign.Right


/** The list's two confirm dialogs (kept out of ChatsScreen so that function stays small for ART on API 30). */
@Composable
private fun ClListDialogs(muteAsk: List<ChatRoomInfo>?, delAsk: List<ChatRoomInfo>?, clearMute: () -> Unit, clearDel: () -> Unit, done: () -> Unit) {
    val c = ctx(); val scope = rememberCoroutineScope()
    muteAsk?.let { rs ->
        ClMuteDialog(if (rs.size == 1) "Mute \"${rs[0].room.name}\"?" else "Mute ${rs.size} chats?", onPick = { ms ->
            clearMute()
            scope.launch {
                rs.forEach { ri -> runCatching { ChatApi.mute(ri.room.id, true) }.onSuccess { ClLocal.setMuteUntil(c, ri.room.id, if (ms > 0) System.currentTimeMillis() + ms else 0) } }
                toast(c, "🔇 Muted"); done()
            }
        }, onDismiss = { clearMute() })
    }
    delAsk?.let { rs ->
        AlertDialog(onDismissRequest = { clearDel() },
            text = { Text(if (rs.size == 1) (if (rs[0].isPrivate) "Delete chat with \"${rs[0].room.name}\"? This removes it from your list." else "Leave \"${rs[0].room.name}\"?") else "Delete ${rs.size} chats? This removes them from your list.", fontSize = 16.sp) },
            confirmButton = { TextButton({
                clearDel()
                scope.launch {
                    var ok = 0; rs.forEach { ri -> runCatching { ClApi.deleteChat(ri.room.id) }.onSuccess { ok++ }.onFailure { toast(c, Api.friendly(it)) } }
                    if (ok > 0) toast(c, if (rs.size == 1 && !rs[0].isPrivate) "🚪 You left the group." else "🗑 Chat removed")
                    done()
                }
            }) { Text(if (rs.size == 1 && !rs[0].isPrivate) "Leave" else if (rs.size == 1) "Delete chat" else "Delete ${rs.size} chats", color = Color(0xFFD32F2F)) } },
            dismissButton = { TextButton({ clearDel() }) { Text("Cancel") } })
    }
}
