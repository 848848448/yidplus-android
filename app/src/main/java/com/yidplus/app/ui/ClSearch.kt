package com.yidplus.app.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yidplus.app.data.Api
import com.yidplus.app.data.Session
import com.yidplus.app.data.Store
import com.yidplus.app.data.TgChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/* ═════════════ CL: the WhatsApp-style search (pill → full search): field with Back + clear, filter chips, recent searches,
   live results grouped Chats / Contacts / Telegram / Messages with the match in bold, empty and no-result states. ═════════════
   Calls are the site's own: GET /api/chat?search= (messages in my chats), /api/chat/rooms?search= (public groups), /api/users/search?q= (people). */

private val CL_FILTERS = listOf("unread" to "Unread", "groups" to "Groups", "photos" to "Photos", "videos" to "Videos", "links" to "Links", "audio" to "Audio", "docs" to "Docs")
private val CL_URL = Regex("""https?://[^\s<>"]+""")

object ClRecent {
    private fun p(c: Context) = c.getSharedPreferences("cl", Context.MODE_PRIVATE)
    private fun k() = "recent:" + (Session.me?.id ?: "guest")
    fun list(c: Context): List<String> = (p(c).getString(k(), "") ?: "").split('\n').filter { it.isNotBlank() }
    fun add(c: Context, q: String) { val t = q.trim(); if (t.length < 2) return; val l = (listOf(t) + list(c).filter { !it.equals(t, true) }).take(8); p(c).edit().putString(k(), l.joinToString("\n")).apply() }
    fun remove(c: Context, q: String) { p(c).edit().putString(k(), list(c).filter { it != q }.joinToString("\n")).apply() }
    fun clear(c: Context) { p(c).edit().remove(k()).apply() }
}

/** Bold + accent on every case-insensitive match of [q] (WhatsApp's highlighted hit). */
@Composable
internal fun clHighlight(text: String, q: String): AnnotatedString {
    val accent = MaterialTheme.colorScheme.primary
    return remember(text, q, accent) {
        buildAnnotatedString {
            val t = q.trim(); if (t.isEmpty()) { append(text); return@buildAnnotatedString }
            var i = 0
            while (i < text.length) {
                val j = text.indexOf(t, i, ignoreCase = true)
                if (j < 0) { append(text.substring(i)); break }
                append(text.substring(i, j))
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)); append(text.substring(j, j + t.length)); pop()
                i = j + t.length
            }
        }
    }
}

/** A message hit cut around the match, so the bold word is always visible on a 480 px screen. */
private fun clSnippet(text: String, q: String): String {
    val flat = text.replace('\n', ' ').removeSuffix(" ↩ Forwarded")
    val i = flat.indexOf(q.trim(), ignoreCase = true)
    return if (i <= 24) flat.take(140) else "…" + flat.substring(i - 18).take(130)
}

private fun matchFilter(m: ChatMsg, f: String?): Boolean = when (f) {
    "photos" -> m.isImage && !m.viewOnce; "videos" -> m.isVideo && !m.viewOnce; "links" -> CL_URL.containsMatchIn(m.text)
    "audio" -> m.isAudio; "docs" -> m.isFile; else -> true
}

@Composable
internal fun ClSearchScreen(rooms: List<ChatRoomInfo>, onClose: () -> Unit) {
    val c = ctx(); val scope = rememberCoroutineScope(); val me = Session.me
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var recent by remember { mutableStateOf(ClRecent.list(c)) }
    var msgs by remember { mutableStateOf<List<Pair<ChatMsg, JSONObject>>>(emptyList()) }
    var groups by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var people by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val kb = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { focus.requestFocus() }; kb?.show() }
    BackHandler { onClose() }
    val t = q.trim()
    LaunchedEffect(t) {
        msgs = emptyList(); groups = emptyList(); people = emptyList()
        if (t.length < 2 || me == null) return@LaunchedEffect
        delay(350); busy = true
        runCatching { msgs = chObjs(Api.get("/api/chat?search=" + chEnc(t)).optJSONArray("messages")).take(60).map { ChatMsg.from(it) to it } }
        runCatching { val mine = rooms.map { it.room.id }.toSet(); groups = chObjs(Api.get("/api/chat/rooms?search=" + chEnc(t)).optJSONArray("rooms")).filter { it.chStr("id") !in mine } }
        runCatching { val dm = rooms.mapNotNull { it.room.otherUserId }.toSet(); people = chObjs(Api.get("/api/users/search?q=" + chEnc(t)).optJSONArray("users")).filter { it.chStr("id") != me.id && it.chStr("id") !in dm }.take(20) }
        busy = false; ClRecent.add(c, t); recent = ClRecent.list(c)
    }
    val tg = remember(t) { Store.res("/api/telegram-channels", TgChannel::list).data.orEmpty().filter { t.length >= 2 && (it.title.contains(t, true) || it.username.contains(t, true)) } }
    // Media chips with no words typed: what this phone already holds of my chats (the last messages of each), newest first — like WhatsApp's media filters.
    val local = remember(filter, ChatCache.version) {
        if (filter == null || filter == "unread" || filter == "groups") emptyList()
        else rooms.flatMap { ri -> ChatCache.get(ri.room.id).orEmpty().map { it to ri } }.filter { (m, _) -> !m.deleted && matchFilter(m, filter) }.sortedByDescending { it.first.createdAt ?: "" }.take(80)
    }
    val chatHits = remember(t, rooms, filter) {
        rooms.filter { ri -> (filter != "unread" || ri.room.unread > 0) && (filter != "groups" || ri.isGroup) && (t.isEmpty() || ri.room.name.contains(t, true)) }
            .let { if (t.isEmpty() && filter != "unread" && filter != "groups") emptyList() else it }
    }
    val msgHits = msgs.filter { (m, _) -> matchFilter(m, filter) && (filter != "unread" && filter != "groups" || filter == null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // The field: Back · words · clear (WhatsApp 2024: a rounded field on the page colour, the keyboard up).
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).height(48.dp).clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (ChatLook.dark()) .55f else .8f)), verticalAlignment = Alignment.CenterVertically) {
            ChIcon(Sym.ArrowBack, "Close search", onClose, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (q.isEmpty()) Text("Search…", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(q, { q = it }, Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Search field" }, singleLine = true,
                    textStyle = TextStyle(fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface, textDirection = TextDirection.Content),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { kb?.hide(); ClRecent.add(c, q); recent = ClRecent.list(c) }))
            }
            if (q.isNotEmpty()) ChIcon(Sym.Close, "Clear", { q = "" }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 20.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CL_FILTERS.forEach { (k, label) ->
                val on = filter == k
                Row(Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = .16f) else MaterialTheme.colorScheme.surfaceVariant)
                    .tap(RoundedCornerShape(18.dp)) { filter = if (on) null else k }.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (on) { androidx.compose.material3.Icon(Sym.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(4.dp)) }
                    Text(label, fontSize = 14.sp, maxLines = 1, softWrap = false, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            // Nothing typed, no chip: recent searches (tap = search again, ✕ = forget), else a one-line hint.
            if (t.isEmpty() && filter == null) {
                if (recent.isNotEmpty()) {
                    item { Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent searches", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        TextButton({ ClRecent.clear(c); recent = emptyList() }) { Text("Clear all") } } }
                    items(recent, key = { "rs:$it" }) { r ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).tap(RoundedCornerShape(0.dp)) { q = r }.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(Sym.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(r, Modifier.weight(1f).padding(start = 18.dp), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                            ChIcon(Sym.Close, "Remove $r", { ClRecent.remove(c, r); recent = ClRecent.list(c) }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else item { ClSearchHint() }
                return@LazyColumn
            }
            if (chatHits.isNotEmpty()) {
                item { ChSection("Chats") }
                items(chatHits, key = { "c:" + it.room.id }) { ri -> ClSearchChatRow(ri, t) }
            }
            if (people.isNotEmpty() && filter == null) {
                item { ChSection("Contacts") }
                items(people, key = { "u:" + it.chStr("id") }) { u ->
                    ClHitRow(clHighlight(u.chStr("nickname"), t), AnnotatedString("Tap to message"), null, { ChAvatar(Api.abs(u.chStr("photo_url").ifBlank { null }), u.chStr("nickname"), u.chStr("id"), 46.dp) }) {
                        scope.launch { runCatching { ChatApi.openDm(u.chStr("id")) }.onSuccess { id -> Nav.open(Screen.Chat(ChatApi.roomOf(id, "private", u.chStr("nickname"), Api.abs(u.chStr("photo_url").ifBlank { null }), u.chStr("id")))) }.onFailure { toast(c, Api.friendly(it)) } }
                    }
                }
            }
            if (groups.isNotEmpty() && (filter == null || filter == "groups")) {
                item { ChSection("Public groups") }
                items(groups, key = { "g:" + it.chStr("id") }) { g ->
                    ClHitRow(clHighlight(g.chStr("nick").ifBlank { g.chStr("name") }, t), AnnotatedString("${g.optInt("members")} members"), null, { ChAvatar(Api.abs(g.chStr("photo_url").ifBlank { null }), g.chStr("nick"), g.chStr("id"), 46.dp) }) {
                        Nav.open(Screen.Chat(ChatApi.roomOf(g.chStr("id"), "group", g.chStr("nick").ifBlank { g.chStr("name") }, Api.abs(g.chStr("photo_url").ifBlank { null }))))
                    }
                }
            }
            if (tg.isNotEmpty() && filter == null) {
                item { ChSection("Telegram channels") }
                items(tg, key = { "t:" + it.username }) { ch -> ClTgRow(ch, if (ch.joined) null else ({
                    // chat.js tgQuickJoin: POST /api/telegram-join {username}
                    scope.launch { runCatching { Api.post("/api/telegram-join", JSONObject().put("username", ch.username)) }.onSuccess { toast(c, "✅ Joined") }.onFailure { toast(c, Api.friendly(it)) } } })) }
            }
            val shownMsgs: List<Triple<ChatMsg, String, String>> = if (t.isEmpty()) local.map { (m, ri) -> Triple(m, ri.room.id, ri.room.name) }
                else msgHits.map { (m, j) -> Triple(m, j.chStr("room_id"), j.chStr("room_name").ifBlank { rooms.firstOrNull { it.room.id == j.chStr("room_id") }?.room?.name ?: "" }) }
            if (shownMsgs.isNotEmpty()) {
                item { ChSection(when (filter) { "photos" -> "Photos"; "videos" -> "Videos"; "links" -> "Links"; "audio" -> "Audio"; "docs" -> "Documents"; else -> "Messages" }) }
                items(shownMsgs, key = { "m:" + it.first.id + it.second }) { (m, roomId, roomName) ->
                    val line = (if (m.nick.isNotBlank()) m.nick + ": " else "") + (if (m.isImage || m.isVideo || m.isFile || m.isAudio) m.preview else clSnippet(m.text, t))
                    ClHitRow(AnnotatedString(roomName.ifBlank { m.nick }), clHighlight(line, t), chatTime(m.createdAt), { ChAvatar(m.photo, m.nick, m.senderId, 46.dp) }) {
                        val ri = rooms.firstOrNull { it.room.id == roomId }
                        Nav.open(Screen.Chat(ri?.room ?: ChatApi.roomOf(roomId, "group", roomName)))
                    }
                }
            }
            val nothing = chatHits.isEmpty() && people.isEmpty() && groups.isEmpty() && tg.isEmpty() && shownMsgs.isEmpty()
            if (nothing && !busy && (t.length >= 2 || filter != null)) item {
                ChEmpty(Sym.SearchOff, if (t.isNotEmpty()) "No results found for “$t”" else "Nothing here yet. Type to search " + (CL_FILTERS.firstOrNull { it.first == filter }?.second?.lowercase() ?: "") )
            }
        }
    }
}

@Composable
private fun ClSearchHint() {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(Sym.Search, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        Text("Search chats, contacts and messages", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ClSearchChatRow(ri: ChatRoomInfo, q: String) {
    val r = ri.room
    ClHitRow(clHighlight(r.name, q), AnnotatedString(chatPreview(ri).ifBlank { if (ri.isGroup) "Group" else if (ri.isChannel) "Channel" else " " }), chatTime(r.lastTime),
        { ChAvatar(r.photo, r.name, r.otherUserId ?: r.id, 46.dp) }, badge = r.unread, muted = ri.muted) { Nav.lastOpened = "room:" + r.id; Nav.open(Screen.Chat(r)) }
}

/** One result row: 64 dp, 46 dp photo, title (bold match) + time, the line under it (bold match), unread badge. */
@Composable
private fun ClHitRow(title: AnnotatedString, sub: AnnotatedString, time: String?, lead: @Composable () -> Unit, badge: Int = 0, muted: Boolean = false, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).tap(RoundedCornerShape(0.dp), onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            lead()
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                    if (!time.isNullOrBlank()) Text(time, Modifier.padding(start = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sub, Modifier.weight(1f), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                    if (badge > 0) { Spacer(Modifier.width(6.dp)); ChBadge(badge, muted) }
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 76.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = .3f))
    }
}
