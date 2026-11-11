package com.yidplus.app.ui

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
    val list = res.data
    LaunchedEffect(list) { if (list != null) { Perf.shown("Home"); Badges.channels = list.filter { it.joined }.sumOf { it.unread } } }
    val views = listOf("Telegram", "Joined", "Members", "New posts")

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (searching) Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Filled.ArrowBack, "Close search", { searching = false; q = "" })
                TextField(q, { q = it.take(80) }, Modifier.weight(1f).firstFocus(always = true), singleLine = true,
                    placeholder = { Text("Search channels") }, textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                if (q.isNotEmpty()) IconBtn(Icons.Filled.Close, "Clear", { q = "" })
            }
        } else TopBar("Channels") {
            if (SoUser.isSuper && view <= 1) IconBtn(Icons.Filled.Add, "Add a Telegram channel", { adding = true }, tint = LocalContentColor.current)
            HeaderActions(onSearch = { searching = true })
        }
        SoChips(views, view, { view = it; runCatching { Api.app.getSharedPreferences("social", 0).edit().putInt("chView", it).apply() } })
        if (searching && q.trim().length >= 2) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).tap(RoundedCornerShape(0.dp)) { Nav.open(SiteSearchPage()) }
            .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.TravelExplore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text("Search all of YID PLUS", Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        Box(Modifier.weight(1f)) {
            if (view >= 2) SiteChannelsList(if (searching) q else "", if (view == 3) 1 else 0)
            else PullToRefreshBox(isRefreshing = res.loading && list != null, onRefresh = { scope.launch { res.load(force = true) } }, modifier = Modifier.fillMaxSize()) {
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
                            if (shown.isEmpty()) item { SoEmpty(if (view == 1) Icons.Filled.Campaign else Icons.Filled.Search,
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
    if (adding) AddTgChannelDialog { adding = false }
}

@Composable
private fun ChannelRow(ch: TgChannel, modifier: Modifier) {
    val c = ctx(); val scope = rememberCoroutineScope()
    val selected = Nav.homeDetail?.ch?.username == ch.username
    var joined by remember(ch.username, ch.joined) { mutableStateOf(ch.joined) }
    Row(modifier.fillMaxWidth().heightIn(min = 72.dp).background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
        .restoreFocus("ch:" + ch.username).tap(RoundedCornerShape(0.dp)) { Nav.lastOpened = "ch:" + ch.username; Nav.open(Screen.Channel(ch.copy(joined = joined))) }
        .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SoThumb(ch.photo, ch.title, 50.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ch.title, Modifier.weight(1f).padding(end = 8.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content))
                if (joined && ch.lastAt != null) Text(ago(ch.lastAt), fontSize = 12.sp, color = if (ch.unread > 0) Color(0xFF25A36F) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                val prev = if (!joined) "${soNum(ch.members)} members" else ch.lastText.replace('\n', ' ').ifBlank {
                    when (ch.lastMedia) { "photo" -> "📷 Photo"; "video" -> "🎬 Video"; "audio" -> "🎵 Audio"; "" -> "${soNum(ch.members)} members"; else -> "📎 File" } }
                Text(prev, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = TextStyle(textDirection = TextDirection.Content))
                if (joined) SoBadge(ch.unread, Modifier.padding(start = 8.dp))
                else SoPill("Join", true, { scope.launch { tgToggleJoin(c, ch.username)?.let { joined = it.first } } }, Modifier.padding(start = 8.dp).soTouchOnly())
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
    val reacts = remember(ch.username) { TgReacts(ch.username) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    var end by remember { mutableStateOf(false) }
    var joined by remember(ch.username) { mutableStateOf(ch.joined) }
    var members by remember(ch.username) { mutableIntStateOf(ch.members) }
    var joining by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
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
        j.optJSONArray("posts").objects().map { TgPost.from(ch.username, it) }.filter { before == null || it.id < before }.sortedByDescending { it.id }
    }
    suspend fun refresh() {
        loading = true
        try {
            val got = fetch(null)
            val fresh = ArrayList<TgPost>(); addNew(fresh, got)
            buffer.clear(); buffer.addAll(fresh.drop(PAGE))
            posts.clear(); posts.addAll(fresh.take(PAGE)); error = null; offline = false; end = got.isEmpty()
            withContext(Dispatchers.IO) {
                val arr = JSONArray(); fresh.take(PAGE).forEach { p -> arr.put(JSONObject().put("tg_msg_id", p.id).put("text", p.text).put("media_type", p.mediaType)
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
        loading = true
        try { val got = fetch(posts.last().id); if (got.isEmpty()) end = true; val tmp = ArrayList(posts); addNew(tmp, got); val added = tmp.drop(posts.size)
            buffer.addAll(added.drop(PAGE)); posts.addAll(added.take(PAGE)) }
        catch (e: Exception) { error = Api.friendly(e) } finally { loading = false }
    }
    LaunchedEffect(ch.username) {
        val cached = withContext(Dispatchers.IO) { Disk.read(Store.key(cacheKey))?.optJSONArray("posts").objects().map { TgPost.from(ch.username, it) } }
        if (cached.isNotEmpty() && posts.isEmpty()) { addNew(posts, cached); Perf.shown("Channel", fromCache = true) }
        launch { reacts.load() }
        refresh(); if (posts.isNotEmpty()) Perf.shown("Channel")
    }
    LaunchedEffect(Net.online) { if (Net.online && offline) refresh() }
    fun toggleJoin() { if (!joining) scope.launch { joining = true; tgToggleJoin(c, ch.username)?.let { (j, n) -> joined = j; members = n }; joining = false } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(ch.title, onBack = { Nav.back() }, sub = "${soNum(members)} members", lead = {
            Box(Modifier.tap(CircleShape) { Nav.open(TgInfoPage(ch.copy(joined = joined, members = members))) }) { SoThumb(ch.photo, ch.title, 36.dp) }
        }) {
            IconBtn(Icons.Filled.Share, "Share channel", { share(c, ch.title + "\n" + tgLink(ch.username)) }, tint = LocalContentColor.current)
            Box {
                IconBtn(Icons.Filled.MoreVert, "More", { menu = true }, tint = LocalContentColor.current)
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Channel info") }, leadingIcon = { Icon(Icons.Filled.Info, null) }, onClick = { menu = false; Nav.open(TgInfoPage(ch.copy(joined = joined, members = members))) })
                    DropdownMenuItem(text = { Text(if (joined) "Leave channel" else "Join channel") }, leadingIcon = { Icon(if (joined) Icons.Filled.Logout else Icons.Filled.Add, null) },
                        onClick = { menu = false; toggleJoin() })
                    DropdownMenuItem(text = { Text("Copy link") }, leadingIcon = { Icon(Icons.Filled.Link, null) }, onClick = { menu = false; soCopy(c, tgLink(ch.username)) })
                    DropdownMenuItem(text = { Text("Open in Telegram") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, null) }, onClick = { menu = false; openUrl(c, "https://t.me/" + ch.username) })
                }
            }
        }
        val state = rememberLazyListState()
        LaunchedEffect(state, posts.size) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { if (it >= posts.size - 5) more() }
        }
        PullToRefreshBox(isRefreshing = loading && posts.isNotEmpty(), onRefresh = { scope.launch { refresh() } }, modifier = Modifier.weight(1f)) {
            when {
                posts.isEmpty() && error != null -> Problem(error!!, offline, { scope.launch { refresh() } })
                posts.isEmpty() && (loading || !end) -> PostSkeleton()
                posts.isEmpty() -> SoEmpty(Icons.Filled.HourglassTop, "Fetching posts from Telegram. A new channel takes a few minutes.")
                else -> LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(posts, key = { it.id }) { p -> PostCard(p, ch, reacts, if (p == posts.first()) Modifier.firstFocus() else Modifier,
                        onMedia = { url -> openGallery(posts.filter { it.isPhoto || it.isVideo }.mapNotNull { q -> q.media?.let { GItem(it, q.isVideo, if (q.isVideo) q.thumb else null, q.title) } }, url) },
                        onDeleted = { posts.removeAll { it.id == p.id } }) }
                    if (loading) item { Loading(Modifier.fillMaxWidth().padding(16.dp)) }
                }
            }
        }
        if (!joined) Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                SoPill(if (joining) "Joining…" else "Join channel", true, { toggleJoin() }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add, enabled = !joining)
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
private fun PostCard(p: TgPost, ch: TgChannel, reacts: TgReacts, modifier: Modifier, onMedia: (String) -> Unit = { }, onDeleted: () -> Unit = {}) {
    val c = ctx(); val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf(false) }
    val firstUrl = remember(p.text) { urlRegex.find(p.text)?.value }
    Surface(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column {
            Column(Modifier.onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val n = e.nativeKeyEvent.keyCode - android.view.KeyEvent.KEYCODE_1
                when {
                    n in 0..7 -> { scope.launch { reacts.react(c, p.id, TG_QUICK[n]) }; true }
                    e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_0 -> { share(c, listOf(ch.title, p.text.take(200), tgLink(ch.username)).filter { it.isNotBlank() }.joinToString("\n")); true }
                    else -> false
                }
            }.tap(RoundedCornerShape(14.dp)) {
                when {
                    p.isVideo || p.isPhoto -> onMedia(p.media!!)
                    p.isAudio -> Nav.open(Screen.Video(p.media!!, p.title.ifBlank { p.author }))
                    p.text.length > 280 && !expanded -> expanded = true
                    firstUrl != null -> openUrl(c, firstUrl)
                    p.media != null -> openUrl(c, p.media + "&download=1")
                    else -> expanded = !expanded
                }
            }) {
                if (p.isPhoto || p.isVideo) {
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
                            Icon(if (p.isAudio) Icons.Filled.MusicNote else Icons.Filled.Description, null, tint = Color.White)
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(p.title.ifBlank { p.fileName.ifBlank { if (p.isAudio) "Audio" else "File" } }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOf(p.performer, dur(p.duration)).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (p.text.isNotBlank()) {
                    Text(p.text, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), maxLines = if (expanded) Int.MAX_VALUE else 8, overflow = TextOverflow.Ellipsis,
                        fontSize = 16.sp, lineHeight = 22.sp, style = TextStyle(textDirection = TextDirection.Content))
                }
            }
            TgReactionRow(p.id, reacts, Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (p.views > 0) { Icon(Icons.Filled.Visibility, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" " + soNum(p.views), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text((if (p.views > 0) "  ·  " else "") + ago(p.postedAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (SoUser.isSuper) IconBtn(Icons.Filled.DeleteOutline, "Delete post", { ask = true }, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 40.dp, modifier = Modifier.soTouchOnly())
                IconBtn(Icons.Filled.Share, "Share post", {
                    share(c, listOf(ch.title, p.text.take(200), tgLink(ch.username)).filter { it.isNotBlank() }.joinToString("\n"))
                }, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 40.dp, modifier = Modifier.soTouchOnly())
            }
        }
    }
    if (ask) AlertDialog(onDismissRequest = { ask = false }, title = { Text("Delete this post for everyone?") },
        confirmButton = { TextButton({ ask = false; scope.launch {
            runCatching { Api.delete("/api/telegram-info?username=" + soEnc(ch.username) + "&tg_msg_id=" + p.id).soCheck() }
                .onSuccess { onDeleted(); toast(c, "Deleted") }.onFailure { toast(c, Api.friendly(it)) } } }) { Text("Delete") } },
        dismissButton = { TextButton({ ask = false }) { Text("Cancel") } })
}
