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

/* ═════════════════════════ Chat list ═════════════════════════ */

private val CHAT_TABS = listOf("all" to "All", "private" to "Private", "groups" to "Groups", "channels" to "Channels", "unread" to "Unread")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen() {
    val me = Session.me
    val c = ctx()
    val res = remember(me?.id) { ChatApi.rooms }
    val scope = rememberCoroutineScope()
    LaunchedEffect(res, Net.online) { res.load(force = Net.online && res.offline) }
    LaunchedEffect(res) { while (isActive) { delay(20_000); res.load(force = true) } }
    val list = res.data
    LaunchedEffect(list) { if (list != null) { Perf.shown("Chats"); Badges.chats = list.filter { !it.muted }.sumOf { it.room.unread } } }
    var tab by rememberSaveable { mutableStateOf("all") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var q by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    BackHandler(enabled = searching) { searching = false; q = "" }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (searching) ChSearchField(q, { q = it }, "Search chats and messages", { searching = false; q = "" })
        else ChBar("Chats", sub = if (me == null) "Guest · featured groups" else null) {
            ChIcon(Icons.Filled.Search, "Search chats", { searching = true })
            if (me != null) Box {
                ChIcon(Icons.Filled.Edit, "New chat", { menu = true })
                DropdownMenu(menu, { menu = false }) {
                    ChMenuItem(Icons.Filled.Person, "New message") { menu = false; Nav.open(ChNewChat()) }
                    ChMenuItem(Icons.Filled.Group, "New group") { menu = false; Nav.open(ChNewGroup("group")) }
                    ChMenuItem(Icons.Filled.Campaign, "New channel") { menu = false; Nav.open(ChNewGroup("channel")) }
                    ChMenuItem(Icons.Filled.Link, "Join by invite link") { menu = false; Nav.open(ChJoinLink()) }
                    ChMenuItem(Icons.Filled.Bookmark, "Saved messages") { menu = false; Nav.open(ChSaved()) }
                }
            }
        }
        if (me == null) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, Modifier.size(18.dp)); Text("  Sign in to see and send your own chats", Modifier.weight(1f), fontSize = 14.sp)
                PrimaryButton("Sign in", { Nav.go(Tab.Profile) })
            }
        }
        if (searching) { ChSearchResults(q, list ?: emptyList()); return@Column }
        if (me != null) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CHAT_TABS.forEach { (k, label) ->
                val on = tab == k
                val n = list?.let { l -> l.filter { matchTab(it, k) }.sumOf { it.room.unread } } ?: 0
                Row(Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) ChatLook.bar.copy(alpha = .14f) else MaterialTheme.colorScheme.surfaceVariant)
                    .tap(RoundedCornerShape(18.dp)) { tab = k }.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) ChatLook.bar.let { if (ChatLook.dark()) Color(0xFF7FD6BC) else it } else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (n > 0 && k != "unread") { Spacer(Modifier.width(6.dp)); ChBadge(n) }
                }
            }
        }
        PullToRefreshBox(isRefreshing = res.loading && list != null, onRefresh = { scope.launch { res.load(true) } }, modifier = Modifier.weight(1f)) {
            when {
                list == null && res.error != null -> Problem(res.error!!, res.offline, { scope.launch { res.load(true) } })
                list == null -> ChSkeleton()
                else -> {
                    val shown = remember(list, tab) { list.filter { matchTab(it, tab) } }
                    LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState()) {
                        if (shown.isEmpty()) item {
                            if (me == null) ChEmpty(Icons.Filled.Forum, "No public groups right now")
                            else ChEmpty(Icons.Filled.Forum, if (tab == "all") "No chats yet. Start one!" else "Nothing here", action = if (tab == "all") "New message" else null) { Nav.open(ChNewChat()) }
                        }
                        items(shown, key = { it.room.id }) { r -> ChRoomRow(r, if (r === shown.first()) Modifier.firstFocus() else Modifier) }
                    }
                }
            }
        }
    }
}

private fun matchTab(r: ChatRoomInfo, tab: String) = when (tab) {
    "private" -> r.isPrivate; "groups" -> r.isGroup; "channels" -> r.isChannel; "unread" -> r.room.unread > 0; else -> true
}

@Composable
internal fun ChMenuItem(icon: ImageVector, text: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text, fontSize = 16.sp, color = color) }, leadingIcon = { Icon(icon, null, tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color) }, onClick = onClick)
}

@Composable
internal fun ChRoomRow(ri: ChatRoomInfo, modifier: Modifier) {
    val r = ri.room
    val c = ctx()
    val sel = Nav.chatDetail?.room?.id == r.id
    Column(Modifier.background(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) {
        Row(modifier.fillMaxWidth().heightIn(min = 72.dp).restoreFocus("room:" + r.id).tap(RoundedCornerShape(0.dp)) { Nav.lastOpened = "room:" + r.id; Nav.open(Screen.Chat(r)) }
            .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChAvatar(r.photo, r.name, r.otherUserId ?: r.id, 52.dp, online = ri.isPrivate && ri.online)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ri.isChannel) Icon(Icons.Filled.Campaign, null, Modifier.size(16.dp).padding(end = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    else if (ri.isGroup) Icon(Icons.Filled.Group, null, Modifier.size(16.dp).padding(end = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Title takes the room it has and ellipsizes; the time always keeps its place at the far end (RTL names can't push it away).
                    Text(r.name, Modifier.weight(1f).padding(start = 2.dp, end = 8.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Start))
                    Text(chatTime(r.lastTime), fontSize = 12.sp, color = if (r.unread > 0 && !ri.muted) ChatLook.unread else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val draft = remember(r.id, Nav.stack.size) { ChDrafts.get(c, r.id) }
                    Text(if (draft.isNotBlank()) "Draft: $draft" else chatRoomPreview(r).ifBlank { if (r.members > 0) "${r.members} members" else " " }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp, color = if (draft.isNotBlank()) Color(0xFF25A45A) else MaterialTheme.colorScheme.onSurfaceVariant, style = TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Start))
                    if (ri.muted) Icon(Icons.Filled.NotificationsOff, "Muted", Modifier.padding(start = 4.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp)); ChBadge(r.unread, ri.muted)
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 82.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
    }
}

