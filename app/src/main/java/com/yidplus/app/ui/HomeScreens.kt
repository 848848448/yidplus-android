package com.yidplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yidplus.app.data.Api
import com.yidplus.app.data.Disk
import com.yidplus.app.data.Net
import com.yidplus.app.data.Perf
import com.yidplus.app.data.Session
import com.yidplus.app.data.Store
import com.yidplus.app.data.TgChannel
import com.yidplus.app.data.TgPost
import com.yidplus.app.data.objects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val res = remember(Session.me?.id) { Store.res("/api/telegram-channels", TgChannel::list) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(res, Net.online) { res.load(force = Net.online && res.offline) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var q by rememberSaveable { mutableStateOf("") }
    val list = res.data
    LaunchedEffect(list) { if (list != null) { Perf.shown("Home"); Badges.channels = list.filter { it.joined }.sumOf { it.unread } } }

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (searching) {
                    OutlinedTextField(q, { q = it }, Modifier.weight(1f).padding(vertical = 4.dp).firstFocus(always = true), singleLine = true,
                        placeholder = { Text("Search channels") }, textStyle = TextStyle(textDirection = TextDirection.Content),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
                    IconBtn(Icons.Filled.Close, "Close search", { searching = false; q = "" })
                } else {
                    Text("Channels", Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp), fontWeight = FontWeight.Bold)
                    if (Session.me != null) IconBtn(Icons.Filled.Notifications, "Notifications", { Nav.open(Screen.Notifications) })
                    IconBtn(Icons.Filled.Search, "Search", { searching = true })
                }
            }
        }
        PullToRefreshBox(isRefreshing = res.loading && list != null, onRefresh = { scope.launch { res.load(force = true) } }, modifier = Modifier.weight(1f)) {
            when {
                list == null && res.error != null -> Problem(res.error!!, res.offline, { scope.launch { res.load(true) } })
                list == null -> Loading()
                else -> {
                    val shown = if (q.isBlank()) list else list.filter { it.title.contains(q, true) || it.username.contains(q, true) || it.lastText.contains(q, true) }
                    val state = rememberLazyListState()
                    LazyColumn(Modifier.fillMaxSize(), state = state) {
                        if (shown.isEmpty()) item { Text("No channels found", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(shown, key = { it.username }) { ch -> ChannelRow(ch, Modifier.then(if (ch == shown.first() && !searching) Modifier.firstFocus() else Modifier)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(ch: TgChannel, modifier: Modifier) {
    val selected = Nav.homeDetail?.ch?.username == ch.username
    Row(modifier.fillMaxWidth().background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
        .restoreFocus("ch:" + ch.username).tap(RoundedCornerShape(0.dp)) { Nav.lastOpened = "ch:" + ch.username; Nav.open(Screen.Channel(ch)) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Avatar(ch.photo, ch.title, 48.dp)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ch.title, Modifier.weight(1f).padding(end = 6.dp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
                Text(ago(ch.lastAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val prev = ch.lastText.replace('\n', ' ').ifBlank { when (ch.lastMedia) { "photo" -> "📷 Photo"; "video" -> "🎬 Video"; "audio" -> "🎵 Audio"; "" -> "${ch.members} members"; else -> "📎 File" } }
                Text(prev, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
                if (ch.joined) Badge(ch.unread)
            }
        }
    }
}

private const val PAGE = 15

/** A Telegram channel's posts, newest first, loaded a page at a time (and saved for offline). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(ch: TgChannel, inPane: Boolean = false) {
    val scope = rememberCoroutineScope()
    val posts = remember(ch.username) { mutableStateListOf<TgPost>() }
    val buffer = remember(ch.username) { ArrayList<TgPost>() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    var end by remember { mutableStateOf(false) }
    val cacheKey = "tg-posts|" + ch.username

    suspend fun fetch(before: Long?): List<TgPost> = withContext(Dispatchers.IO) {
        val j = Api.get("/api/telegram-ingest?username=" + ch.username + "&limit=$PAGE" + (before?.let { "&before=$it" } ?: ""))
        val all = j.optJSONArray("posts").objects().map { TgPost.from(ch.username, it) }.filter { before == null || it.id < before }
        all
    }
    suspend fun refresh() {
        loading = true
        try {
            val got = fetch(null)
            buffer.clear(); buffer.addAll(got.drop(PAGE))
            posts.clear(); posts.addAll(got.take(PAGE)); error = null; offline = false; end = got.isEmpty()
            withContext(Dispatchers.IO) {
                val arr = JSONArray(); got.take(PAGE).forEach { p -> arr.put(JSONObject().put("tg_msg_id", p.id).put("text", p.text).put("media_type", p.mediaType)
                    .put("media_title", p.title).put("media_performer", p.performer).put("media_duration", p.duration).put("media_name", p.fileName)
                    .put("link", p.link).put("posted_at", p.postedAt).put("views", p.views).put("author_name", p.author)) }
                Disk.write(Store.key(cacheKey), JSONObject().put("posts", arr))
            }
            if (Session.me != null) runCatching { Api.post("/api/telegram-read", JSONObject().put("username", ch.username)) }
        } catch (e: Exception) { error = Api.friendly(e); offline = Api.isOffline(e) } finally { loading = false }
    }
    suspend fun more() {
        if (loading || end || posts.isEmpty()) return
        if (buffer.isNotEmpty()) { val n = buffer.take(PAGE); buffer.subList(0, n.size).clear(); posts.addAll(n); return }
        loading = true
        try { val got = fetch(posts.last().id); if (got.isEmpty()) end = true; buffer.addAll(got.drop(PAGE)); posts.addAll(got.take(PAGE)) }
        catch (e: Exception) { error = Api.friendly(e) } finally { loading = false }
    }
    LaunchedEffect(ch.username) {
        val cached = withContext(Dispatchers.IO) { Disk.read(Store.key(cacheKey))?.optJSONArray("posts").objects().map { TgPost.from(ch.username, it) } }
        if (cached.isNotEmpty() && posts.isEmpty()) { posts.addAll(cached); Perf.shown("Channel", fromCache = true) }
        refresh(); if (posts.isNotEmpty()) Perf.shown("Channel")
    }
    LaunchedEffect(Net.online) { if (Net.online && offline) refresh() }

    Column(Modifier.fillMaxSize()) {
        TopBar(ch.title, onBack = { Nav.back() }, sub = "${ch.members} members", lead = { Avatar(ch.photo, ch.title, 34.dp) })
        val state = rememberLazyListState()
        LaunchedEffect(state, posts.size) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { if (it >= posts.size - 5) more() }
        }
        PullToRefreshBox(isRefreshing = loading && posts.isNotEmpty(), onRefresh = { scope.launch { refresh() } }, modifier = Modifier.weight(1f)) {
            when {
                posts.isEmpty() && error != null -> Problem(error!!, offline, { scope.launch { refresh() } })
                posts.isEmpty() && loading -> Loading()
                posts.isEmpty() -> Text("No posts yet", Modifier.padding(24.dp))
                else -> LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
                    items(posts, key = { it.id }) { p -> PostCard(p, if (p == posts.first()) Modifier.firstFocus() else Modifier) }
                    if (loading) item { Loading(Modifier.fillMaxWidth().padding(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PostCard(p: TgPost, modifier: Modifier) {
    val c = ctx()
    var expanded by remember { mutableStateOf(false) }
    val firstUrl = remember(p.text) { urlRegex.find(p.text)?.value }
    Surface(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(Modifier.tap(RoundedCornerShape(14.dp)) {
            when {
                p.isVideo || p.isAudio -> Nav.open(Screen.Video(p.media!!, p.title.ifBlank { p.author }))
                p.isPhoto -> Nav.open(Screen.Photo(p.media!!))
                p.text.length > 280 && !expanded -> expanded = true
                firstUrl != null -> openUrl(c, firstUrl)
                p.media != null -> openUrl(c, p.media + "&download=1")
                else -> expanded = !expanded
            }
        }) {
            if (p.isPhoto || p.isVideo) {
                // Every photo/video the same way: full card width, its own shape kept (never cropped), at most ~60% of the
                // screen tall, on a dark frame while it loads.
                val maxH = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.6f).dp
                Box(Modifier.fillMaxWidth().heightIn(min = 140.dp).background(Color(0xFF101614)), contentAlignment = Alignment.Center) {
                    AsyncImage(model = ImageRequest.Builder(c).data(p.thumb).crossfade(120).build(), contentDescription = null,
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = maxH))
                    if (p.isVideo) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                        if (p.duration > 0) Text(dur(p.duration), color = Color.White, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color.Black.copy(alpha = .6f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                    Text("Yidplus.com", color = Color.White, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = .45f), RoundedCornerShape(7.dp)).padding(horizontal = 6.dp))
                }
            } else if (p.isAudio || (p.media != null && p.mediaType.isNotBlank())) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Icon(if (p.isAudio) Icons.Filled.MusicNote else Icons.Filled.Description, null, tint = Color.White)
                    }
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(p.title.ifBlank { p.fileName.ifBlank { if (p.isAudio) "Audio" else "File" } }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(p.performer, dur(p.duration)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (p.text.isNotBlank()) {
                Text(p.text, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), maxLines = if (expanded) Int.MAX_VALUE else 8, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (p.views > 0) { Icon(Icons.Filled.Visibility, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(" ${p.views}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.weight(1f))
                Text(ago(p.postedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
