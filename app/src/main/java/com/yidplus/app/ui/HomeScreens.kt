package com.yidplus.app.ui

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.activity.compose.BackHandler
import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yidplus.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/** The Channels tab: the Telegram channels (as on the site's chats page) and the members' own channels (the site's Channels tab). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val res = remember(Session.me?.id) { Store.res("/api/telegram-channels", TgChannel::list) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(res, Net.online, SoRefresh.tick) { res.load(force = (Net.online && res.offline) || SoRefresh.tick > 0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var q by rememberSaveable { mutableStateOf("") }
    var view by rememberSaveable { mutableIntStateOf(runCatching { Api.app.getSharedPreferences("social", 0).getInt("chView", 0) }.getOrDefault(0)) }
    var adding by remember { mutableStateOf(false) }
    var pulling by remember { mutableStateOf(false) }
    val kb = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    BackHandler(enabled = searching) { kb?.hide(); searching = false; q = "" }
    val list = res.data
    LaunchedEffect(list) { if (list != null) { Perf.shown("Home"); Badges.channels = list.filter { it.joined }.sumOf { it.unread } } }
    val views = listOf("Telegram", "Joined", "Members", "Recent")
    var hdrShown by remember { mutableStateOf(true) }
    LaunchedEffect(view) { hdrShown = true }
    val hdrConn = remember { object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
            // Hide after a real scroll down; show on any scroll up or when the list is pulled past its top.
            if (consumed.y < -8f) hdrShown = false else if (consumed.y > 8f || available.y > 8f) hdrShown = true
            return androidx.compose.ui.geometry.Offset.Zero
        } } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (searching) Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Sym.ArrowBack, "Close search", { kb?.hide(); searching = false; q = "" })
                TextField(q, { q = it.take(80) }, Modifier.weight(1f).firstFocus(always = true), singleLine = true,
                    placeholder = { Text("Search channels") }, textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                if (q.isNotEmpty()) IconBtn(Sym.Close, "Clear", { q = "" })
            }
        } else TopBar("Channels") {
            if (SoUser.isSuper && view <= 1) IconBtn(Sym.Add, "Add a Telegram channel", { adding = true }, tint = LocalContentColor.current)
            SoHeaderActions(onSearch = { searching = true }, showSearch = false)
        }
        // WhatsApp-2024: the search pill at the top of the tab (it opens the search in place; Back closes it).
        // The pill and the chips slide away while the list scrolls down and come back on the way up, so a 480x640 screen shows more rows.
        androidx.compose.animation.AnimatedVisibility(visible = hdrShown || searching,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()) { Column {
        if (!searching) SearchPill("Search", { searching = true })
        // "Feed" (the site's Home page) is shown first but saved as view 4, so the saved Telegram/Joined/Members/Recent numbers stay the same.
        SoChips(listOf("Feed") + views, if (view == 4) 0 else view + 1, { d -> val v = if (d == 0) 4 else d - 1; view = v; runCatching { Api.app.getSharedPreferences("social", 0).edit().putInt("chView", v).apply() } })
        } }
        if (searching && q.trim().length >= 2) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).tap(RoundedCornerShape(0.dp)) { Nav.open(SoSearchPage(q0 = q.trim())) }
            .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Sym.TravelExplore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text("Search all of YID PLUS", Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        Box(Modifier.weight(1f).nestedScroll(hdrConn)) {
            if (view == 4) HmFeed(onSeeChannels = { view = 2; runCatching { Api.app.getSharedPreferences("social", 0).edit().putInt("chView", 2).apply() } })
            else if (view >= 2) SoSiteChannelsList(if (searching) q else "", if (view == 3) 1 else 0)
            else PullToRefreshBox(isRefreshing = pulling, onRefresh = { scope.launch { pulling = true; res.load(force = true); pulling = false } }, modifier = Modifier.fillMaxSize()) {
                when {
                    list == null && res.error != null -> Problem(res.error!!, res.offline, { scope.launch { res.load(true) } })
                    list == null -> SoSkeleton()
                    else -> {
                        val t = q.trim()
                        val base = if (view == 1) list.filter { it.joined } else list
                        val shown = (if (!searching || t.isEmpty()) base else base.filter { it.title.contains(t, true) || it.username.contains(t, true) || it.lastText.contains(t, true) })
                            .distinctBy { it.username }
                            .sortedWith(compareByDescending<TgChannel> { it.joined && it.unread > 0 }.thenByDescending { parseTime(it.lastAt) ?: 0L })
                        val state = rememberLazyListState()
                        LazyColumn(Modifier.fillMaxSize(), state = state) {
                            if (shown.isEmpty()) item { SoEmpty(if (view == 1) Sym.Campaign else Sym.Search,
                                if (view == 1) "You haven't joined a channel yet. Open one and tap Join." else "No channels found") }
                            items(shown, key = { it.username }) { ch ->
                                ChannelRow(ch, Modifier.then(if (ch == shown.first() && !searching) Modifier.firstFocus() else Modifier))
                                SoDivider(82.dp)
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) SoAddTgChannelDialog { adding = false }
}

@Composable
private fun ChannelRow(ch: TgChannel, modifier: Modifier) {
    val c = ctx(); val scope = rememberCoroutineScope()
    val selected = Nav.homeDetail?.ch?.username == ch.username
    var joined by remember(ch.username, ch.joined) { mutableStateOf(ch.joined) }
    var rowMenu by remember { mutableStateOf(false) }
    var readNow by remember(ch.username) { mutableStateOf(false) }
    // Like the site's channel list / WhatsApp's Updates: photo, name and time on one line, the last post under it,
    // then the unread number (joined) or a small Join button. Members count when there is no post yet.
    Row(modifier.fillMaxWidth().heightIn(min = 68.dp).background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
        .restoreFocus("ch:" + ch.username).hmTapLong(RoundedCornerShape(0.dp), onLong = { rowMenu = true }) { Nav.lastOpened = "ch:" + ch.username; Nav.open(Screen.Channel(ch.copy(joined = joined))) }
        .padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            SoThumb(ch.photo, ch.title, 50.dp)
        }
        // Telegram's long-press menu on a channel row, as the app's bottom sheet.
        if (rowMenu) ActionSheet(listOfNotNull(
            SheetAction(Sym.OpenInNew, "Open") { rowMenu = false; Nav.open(Screen.Channel(ch.copy(joined = joined))) },
            if (joined && ch.unread > 0 && !readNow) SheetAction(Sym.DoneAll, "Mark as read") { rowMenu = false; readNow = true
                scope.launch { runCatching { Api.post("/api/telegram-read", JSONObject().put("username", ch.username)) }; Badges.channels = (Badges.channels - ch.unread).coerceAtLeast(0) } } else null,
            SheetAction(if (joined) Sym.Logout else Sym.Add, if (joined) "Leave channel" else "Join channel", destructive = joined) { rowMenu = false; scope.launch { soTgToggleJoin(c, ch.username, joined)?.let { joined = it.first } } },
            SheetAction(Sym.Info, "Channel info") { rowMenu = false; Nav.open(SoTgInfoPage(ch.copy(joined = joined))) },
            SheetAction(Sym.Link, "Copy link") { rowMenu = false; soCopy(c, soTgLink(ch.username)) }
        ), onDismiss = { rowMenu = false }, title = ch.title)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Left-aligned even for a Yiddish name (like WhatsApp), so the name never runs into the time.
                Text(ch.title, Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content, textAlign = TextAlign.Left),
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (ch.lastAt != null) Text(ago(ch.lastAt), style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    color = if (joined && ch.unread > 0) BadgeGreen else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                val media = when (ch.lastMedia) { "photo" -> "📷 Photo"; "video", "animation" -> "🎬 Video"; "audio" -> "🎵 Audio"; "voice" -> "🎤 Voice message"; "" -> ""; else -> "📎 File" }
                val text = ch.lastText.replace('\n', ' ').trim()
                val prev = when { text.isNotEmpty() && media.isNotEmpty() -> media.substringBefore(' ') + " " + text; text.isNotEmpty() -> text; media.isNotEmpty() -> media; else -> soMembers(ch.members) }
                Text(prev, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content, textAlign = TextAlign.Left), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (joined) SoBadge(if (readNow) 0 else ch.unread, Modifier.padding(start = 8.dp))
                // A small outlined Join (like Telegram's), so the row keeps WhatsApp's height.
                else Box(Modifier.padding(start = 8.dp).heightIn(min = 28.dp).soTouchOnly().tap(RoundedCornerShape(14.dp)) { scope.launch { soTgToggleJoin(c, ch.username, joined)?.let { joined = it.first } } }
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
                    Text("Join", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
            }
        }
    }
}

private const val PAGE = 15

/** A Telegram channel's posts, newest first, loaded a page at a time (and saved for offline), with reactions, share and Join. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(ch: TgChannel, inPane: Boolean = false) {
    val c = ctx()
    val scope = rememberCoroutineScope()
    val posts = remember(ch.username) { mutableStateListOf<TgPost>() }
    val buffer = remember(ch.username) { ArrayList<TgPost>() }
    val reacts = remember(ch.username) { SoTgReacts(ch.username) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var pulling by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    var end by remember { mutableStateOf(false) }
    var joined by remember(ch.username) { mutableStateOf(ch.joined) }
    var members by remember(ch.username) { mutableIntStateOf(ch.members) }
    var joining by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf("") }
    val cacheKey = "tg-posts|" + ch.username

    /** Never the same post twice: by id, and by the same file posted again. */
    fun addNew(into: MutableList<TgPost>, got: List<TgPost>) {
        val ids = into.mapTo(HashSet()) { it.id }
        val files = into.mapNotNullTo(HashSet()) { p -> p.fileName.takeIf { it.isNotBlank() }?.let { it + "|" + p.duration } }
        for (p in got) {
            if (!ids.add(p.id)) continue
            val f = p.fileName.takeIf { it.isNotBlank() }?.let { it + "|" + p.duration }
            if (f != null && p.isVideo && !files.add(f)) continue
            into.add(p)
        }
    }
    suspend fun fetch(before: Long?): List<TgPost> = withContext(Dispatchers.IO) {
        val j = Api.get("/api/telegram-ingest?username=" + ch.username + "&limit=$PAGE" + (before?.let { "&before=$it" } ?: ""))
        j.optJSONArray("posts").objects().map { HmTgExtras.put(ch.username, it); TgPost.from(ch.username, it) }.filter { before == null || it.id < before }.sortedByDescending { it.id }
    }
    suspend fun refresh() {
        loading = true
        try {
            val got = fetch(null)
            val fresh = ArrayList<TgPost>(); addNew(fresh, got)
            buffer.clear(); buffer.addAll(fresh.drop(PAGE))
            posts.clear(); posts.addAll(fresh.take(PAGE)); error = null; offline = false; end = got.isEmpty()
            withContext(Dispatchers.IO) {
                val arr = JSONArray(); fresh.take(PAGE).forEach { p -> arr.put(HmTgExtras.into(ch.username, p.id, JSONObject()).put("tg_msg_id", p.id).put("text", p.text).put("media_type", p.mediaType)
                    .put("media_title", p.title).put("media_performer", p.performer).put("media_duration", p.duration).put("media_name", p.fileName)
                    .put("link", p.link).put("posted_at", p.postedAt).put("views", p.views).put("author_name", p.author)) }
                Disk.write(Store.key(cacheKey), JSONObject().put("posts", arr))
            }
            if (Session.me != null) runCatching { Api.post("/api/telegram-read", JSONObject().put("username", ch.username)) }
        } catch (e: Exception) { error = Api.friendly(e); offline = Api.isOffline(e) } finally { loading = false }
    }
    suspend fun more() {
        if (loading || end || posts.isEmpty()) return
        if (buffer.isNotEmpty()) { val n = buffer.take(PAGE); buffer.subList(0, n.size).clear(); addNew(posts, n); return }
        loading = true; loadingMore = true
        try { val got = fetch(posts.last().id); if (got.isEmpty()) end = true; val tmp = ArrayList(posts); addNew(tmp, got); val added = tmp.drop(posts.size)
            buffer.addAll(added.drop(PAGE)); posts.addAll(added.take(PAGE)) }
        catch (e: Exception) { error = Api.friendly(e) } finally { loading = false; loadingMore = false }
    }
    LaunchedEffect(ch.username) {
        val cached = withContext(Dispatchers.IO) { Disk.read(Store.key(cacheKey))?.optJSONArray("posts").objects().map { HmTgExtras.put(ch.username, it); TgPost.from(ch.username, it) } }
        if (cached.isNotEmpty() && posts.isEmpty()) { addNew(posts, cached); Perf.shown("Channel", fromCache = true) }
        launch { reacts.load() }
        refresh(); if (posts.isNotEmpty()) Perf.shown("Channel")
    }
    LaunchedEffect(Net.online) { if (Net.online && offline) refresh() }
    val listState = rememberLazyListState()
    var newCount by remember(ch.username) { mutableIntStateOf(0) }
    // Live: like the site, look for new posts every 25 s while the channel is open; new ones appear at the bottom (Telegram order).
    LaunchedEffect(ch.username) {
        while (true) {
            delay(25_000)
            if (loading || posts.isEmpty()) continue
            val got = runCatching { fetch(null) }.getOrNull() ?: continue
            val top = posts.first().id
            val ids = posts.mapTo(HashSet()) { it.id }
            val add = got.filter { it.id > top && it.id !in ids }.sortedByDescending { it.id }
            if (add.isEmpty()) continue
            val atNewest = listState.firstVisibleItemIndex <= 1
            posts.addAll(0, add)
            if (atNewest) listState.animateScrollToItem(0) else newCount += add.size
        }
    }
    LaunchedEffect(listState) { snapshotFlow { listState.firstVisibleItemIndex }.collect { if (it <= 1) newCount = 0 } }
    val farFromNewest by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
    fun toggleJoin() { if (!joining) scope.launch { joining = true; soTgToggleJoin(c, ch.username, joined)?.let { (j, n) -> members = if (n >= 0) n else (members + (if (j) 1 else -1)).coerceAtLeast(0); joined = j }; joining = false } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(ch.title, onBack = { Nav.back() }, sub = soMembers(members), lead = {
            Box(Modifier.tap(CircleShape) { Nav.open(SoTgInfoPage(ch.copy(joined = joined, members = members))) }) { SoThumb(ch.photo, ch.title, 36.dp) }
        }) {
            IconBtn(Sym.Search, "Search in channel", { findOpen = !findOpen; if (!findOpen) find = "" }, tint = LocalContentColor.current)
            Box {
                IconBtn(Sym.MoreVert, "More", { menu = true }, tint = LocalContentColor.current)
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Channel info") }, leadingIcon = { Icon(Sym.Info, null) }, onClick = { menu = false; Nav.open(SoTgInfoPage(ch.copy(joined = joined, members = members))) })
                    DropdownMenuItem(text = { Text(if (joined) "Leave channel" else "Join channel") }, leadingIcon = { Icon(if (joined) Sym.Logout else Sym.Add, null) },
                        onClick = { menu = false; toggleJoin() })
                    DropdownMenuItem(text = { Text("Share channel") }, leadingIcon = { Icon(Sym.Share, null) }, onClick = { menu = false; share(c, ch.title + "\n" + soTgLink(ch.username)) })
                    DropdownMenuItem(text = { Text("Copy link") }, leadingIcon = { Icon(Sym.Link, null) }, onClick = { menu = false; soCopy(c, soTgLink(ch.username)) })
                    DropdownMenuItem(text = { Text("Open in Telegram") }, leadingIcon = { Icon(Sym.Send, null) }, onClick = { menu = false; openUrl(c, "https://t.me/" + ch.username) })
                }
            }
        }
        if (findOpen) Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            OutlinedTextField(find, { find = it.take(60) }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).firstFocus(always = true), singleLine = true,
                placeholder = { Text("Search this channel") }, leadingIcon = { Icon(Sym.Search, null) },
                trailingIcon = { if (find.isNotEmpty()) IconBtn(Sym.Close, "Clear", { find = "" }) }, textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content))
        }
        val state = listState
        LaunchedEffect(state, posts.size) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { if (it >= posts.size - 5) more() }
        }
        // Only a pull you made shows the refresh circle; opening the channel refreshes quietly behind the saved posts.
        PullToRefreshBox(isRefreshing = pulling, onRefresh = { scope.launch { pulling = true; refresh(); pulling = false } }, modifier = Modifier.weight(1f)) {
            when {
                posts.isEmpty() && error != null -> Problem(error!!, offline, { scope.launch { refresh() } })
                posts.isEmpty() && (loading || !end) -> PostSkeleton()
                posts.isEmpty() -> SoEmpty(Sym.HourglassTop, "Fetching posts from Telegram. A new channel takes a few minutes.")
                // Telegram order: newest at the bottom, scroll up for older (reverseLayout keeps index 0 = newest).
                else -> LazyColumn(Modifier.fillMaxSize(), state = state, reverseLayout = true, contentPadding = PaddingValues(vertical = 6.dp)) {
                    val f = find.trim()
                    val units = hmGroupPosts(ch.username, posts.toList()).let { all -> if (f.length < 2) all else all.filter { u -> u.any { it.text.contains(f, true) || it.title.contains(f, true) } } }
                    val gallery = { url: String -> openGallery(posts.filter { it.isPhoto || it.isVideo }.sortedBy { it.id }.mapNotNull { q -> q.media?.let { GItem(it, q.isVideo, if (q.isVideo) q.thumb else null, q.title) } }, url) }
                    units.forEachIndexed { ui, u ->
                        val main = u.firstOrNull { it.text.isNotBlank() } ?: u.first()
                        item(key = "p" + u.first().id) { PostCard(main, ch, reacts, if (ui == 0) Modifier.firstFocus() else Modifier,
                            onMedia = gallery, onDeleted = { posts.removeAll { q -> u.any { it.id == q.id } } }, album = if (u.size > 1) u else emptyList(),
                            // Audio plays in the app's music player, and the channel's next audio plays after it (the site's _tgPlayNext).
                            onAudio = { a -> val auds = posts.filter { it.isAudio && it.media != null }.sortedBy { it.id }
                                Music.play(c, auds.map { q -> Track("tg:" + ch.username + ":" + q.id, q.title.ifBlank { q.fileName.ifBlank { "Audio" } }, q.performer.ifBlank { ch.title },
                                    HmTgExtras.of(ch.username, q.id)?.thumb ?: ch.photo, q.media, ch.title, ch.photo, 0, false) }, auds.indexOfFirst { it.id == a.id }.coerceAtLeast(0)) }) }
                        val older = units.getOrNull(ui + 1)
                        val day = hmDayKey(u.last().postedAt)
                        if ((older == null && end) || (older != null && hmDayKey(older.last().postedAt) != day))
                            item(key = "d" + u.first().id) { HmDayChip(hmDayLabel(u.last().postedAt)) }
                    }
                    if (loadingMore) item { Loading(Modifier.fillMaxWidth().padding(vertical = 8.dp), skeleton = true) }
                }
            }
            if (posts.isNotEmpty() && (newCount > 0 || farFromNewest)) {
                Surface(Modifier.align(Alignment.BottomEnd).padding(12.dp).tap(RoundedCornerShape(20.dp)) { scope.launch { listState.animateScrollToItem(0); newCount = 0 } },
                    shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary, shadowElevation = 4.dp) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Sym.KeyboardArrowDown, "Newest posts", tint = Color.White)
                        if (newCount > 0) Text(" $newCount new", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (!joined) Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                SoPill(if (joining) "Joining…" else "Join channel", true, { toggleJoin() }, Modifier.fillMaxWidth(), icon = Sym.Add, enabled = !joining)
            }
        }
    }
}

@Composable
private fun PostSkeleton() {
    val col = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)) {
                Box(Modifier.fillMaxWidth().height(150.dp).background(col))
                Box(Modifier.padding(12.dp).fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(col))
                Box(Modifier.padding(start = 12.dp, bottom = 12.dp).fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(col))
            }
        }
    }
}

@Composable
private fun PostCard(p: TgPost, ch: TgChannel, reacts: SoTgReacts, modifier: Modifier, onMedia: (String) -> Unit = { }, onDeleted: () -> Unit = {}, album: List<TgPost> = emptyList(), onAudio: ((TgPost) -> Unit)? = null) {
    val c = ctx(); val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    var opts by remember { mutableStateOf(false) }
    val ents = HmTgExtras.of(ch.username, p.id)?.entities.orEmpty()
    val linkC = MaterialTheme.colorScheme.primary; val spoilC = MaterialTheme.colorScheme.onSurfaceVariant
    val styled = remember(p.text, ents, revealed) { hmTgFormat(p.text, ents, linkC, spoilC, revealed) }
    val cardUrl = remember(p.text) { if (album.isEmpty() && !p.isPhoto && !p.isVideo) hmCardUrl(p.text) else null }
    var ask by remember { mutableStateOf(false) }
    val firstUrl = remember(p.text) { urlRegex.find(p.text)?.value }
    Surface(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column {
            Column(Modifier.onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val n = e.nativeKeyEvent.keyCode - android.view.KeyEvent.KEYCODE_1
                when {
                    n in 0..7 -> { scope.launch { reacts.react(c, p.id, SO_TG_QUICK[n]) }; true }
                    e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_0 -> { share(c, listOf(ch.title, p.text.take(200), soTgLink(ch.username)).filter { it.isNotBlank() }.joinToString("\n")); true }
                    else -> false
                }
            }.hmTapLong(RoundedCornerShape(14.dp), onLong = { opts = true }) {
                when {
                    hmHasSpoiler(ents) && !revealed -> revealed = true
                    album.isNotEmpty() -> expanded = !expanded
                    p.isVideo || p.isPhoto -> onMedia(p.media!!)
                    p.isAudio && onAudio != null -> onAudio(p)
                    p.isAudio -> Nav.open(Screen.Video(p.media!!, p.title.ifBlank { p.author }))
                    p.text.length > 280 && !expanded -> expanded = true
                    firstUrl != null -> openUrl(c, firstUrl)
                    p.media != null -> openUrl(c, p.media + "&download=1")
                    else -> expanded = !expanded
                }
            }) {
                if (album.isNotEmpty()) {
                    HmAlbumGrid(album) { q -> q.media?.let(onMedia) }
                } else if (p.isPhoto || p.isVideo) {
                    // Full card width, its own shape kept (never cropped), at most ~60% of the screen tall.
                    val maxH = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.6f).dp
                    Box(Modifier.fillMaxWidth()) {
                        if (p.isVideo) SoVideoPoster(p.thumb, p.title.ifBlank { p.fileName }, p.duration, Modifier.fillMaxWidth().heightIn(max = maxH))
                        else AsyncImage(model = ImageRequest.Builder(c).data(p.thumb).crossfade(120).build(), contentDescription = null, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = maxH).background(MaterialTheme.colorScheme.surfaceVariant))
                        Text("Yidplus.com", color = Color.White, fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = .45f), RoundedCornerShape(7.dp)).padding(horizontal = 6.dp))
                    }
                } else if (p.isAudio || (p.media != null && p.mediaType.isNotBlank())) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                            Icon(if (p.isAudio) Sym.MusicNote else Sym.Description, null, tint = Color.White)
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(p.title.ifBlank { p.fileName.ifBlank { if (p.isAudio) "Audio" else "File" } }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOf(p.performer, dur(p.duration)).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (p.text.isNotBlank()) {
                    Text(styled, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), maxLines = if (expanded) Int.MAX_VALUE else 8, overflow = TextOverflow.Ellipsis,
                        fontSize = 16.sp, lineHeight = 22.sp, style = TextStyle(textDirection = TextDirection.Content))
                }
            }
            cardUrl?.let { u -> Box(Modifier.padding(horizontal = 8.dp)) { ChLinkCard(u, false) { } } }
            SoTgReactionRow(p.id, reacts, Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (p.views > 0) { Icon(Sym.Visibility, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" " + soNum(p.views), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text((if (p.views > 0) "  ·  " else "") + ago(p.postedAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (SoUser.isSuper) IconBtn(Sym.DeleteOutline, "Delete post", { ask = true }, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 40.dp, modifier = Modifier.soTouchOnly())
                IconBtn(Sym.Share, "Share post", {
                    share(c, listOf(ch.title, p.text.take(200), soTgLink(ch.username)).filter { it.isNotBlank() }.joinToString("\n"))
                }, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 40.dp, modifier = Modifier.soTouchOnly())
                // Telegram's post menu: react, copy text, copy link, share, open in Telegram, delete (admins).
                Box {
                    IconBtn(Sym.MoreVert, "Post options", { opts = true }, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 40.dp, modifier = Modifier.soTouchOnly())
                    DropdownMenu(opts, { opts = false }) {
                        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            SO_TG_QUICK.take(6).forEach { e -> Box(Modifier.size(36.dp).tap(CircleShape) { opts = false; scope.launch { reacts.react(c, p.id, e) } }, contentAlignment = Alignment.Center) { Text(e, fontSize = 20.sp) } }
                        }
                        HorizontalDivider()
                        if (p.text.isNotBlank()) DropdownMenuItem(text = { Text("Copy text") }, leadingIcon = { Icon(Sym.ContentCopy, null) }, onClick = { opts = false; soCopy(c, p.text, "Text copied") })
                        DropdownMenuItem(text = { Text("Copy post link") }, leadingIcon = { Icon(Sym.Link, null) }, onClick = { opts = false; soCopy(c, "https://t.me/" + ch.username + "/" + p.id, "Link copied") })
                        DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Sym.Share, null) }, onClick = { opts = false
                            share(c, listOf(ch.title, p.text.take(200), soTgLink(ch.username)).filter { it.isNotBlank() }.joinToString("\n")) })
                        DropdownMenuItem(text = { Text("Open in Telegram") }, leadingIcon = { Icon(Sym.OpenInNew, null) }, onClick = { opts = false; openUrl(c, "https://t.me/" + ch.username + "/" + p.id) })
                        if (SoUser.isSuper) DropdownMenuItem(text = { Text("Delete for everyone", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Sym.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { opts = false; ask = true })
                    }
                }
            }
        }
    }
    if (ask) AlertDialog(onDismissRequest = { ask = false }, title = { Text("Delete this post for everyone?") },
        confirmButton = { TextButton({ ask = false; scope.launch {
            runCatching { Api.delete("/api/telegram-info?username=" + soEnc(ch.username) + "&tg_msg_id=" + p.id).soCheck() }
                .onSuccess { onDeleted(); toast(c, "Deleted") }.onFailure { toast(c, Api.friendly(it)) } } }) { Text("Delete") } },
        dismissButton = { TextButton({ ask = false }) { Text("Cancel") } })
}
