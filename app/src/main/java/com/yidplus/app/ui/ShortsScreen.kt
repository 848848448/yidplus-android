package com.yidplus.app.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.yidplus.app.R
import com.yidplus.app.data.Api
import com.yidplus.app.data.Comment
import com.yidplus.app.data.Disk
import com.yidplus.app.data.Media
import com.yidplus.app.data.Net
import com.yidplus.app.data.Perf
import com.yidplus.app.data.Prefs
import com.yidplus.app.data.Session
import com.yidplus.app.data.Clip
import com.yidplus.app.data.objects
import com.yidplus.app.data.str
import com.yidplus.app.data.strOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

enum class ShortsTab(val label: String) { ForYou("For You"), Following("Following"), Uploads("Uploads"), Saved("Saved") }

/** The feeds live as long as the app, so going back to Shorts is instant and keeps your place. */
object Feeds {
    var tab by mutableStateOf(ShortsTab.ForYou)
    var srcOff by mutableStateOf(false)
    val forYou = ShortFeed(true)
    val uploads = ShortFeed(false)
    val follows = mutableStateListOf<String>()
    val saved = mutableStateListOf<Clip>()
    var loadedPrefs = false
    val hidden = HashSet<String>()
    private var seen: MutableList<String>? = null
    private var unsaved = 0
    fun seenSet(): Set<String> = (seen ?: Prefs.seen().also { seen = it }).toHashSet()
    /** Watched videos go to the back next time (kept on the phone, like the website). Saved every 8 videos. */
    fun markSeen(url: String) {
        val l = seen ?: Prefs.seen().also { seen = it }
        if (url in l) return
        l.add(url); if (++unsaved >= 8) { unsaved = 0; val copy = l.toList(); Thread { Prefs.saveSeen(copy) }.start() }
    }

    fun loadPrefs(ctx: android.content.Context) {
        if (loadedPrefs) return; loadedPrefs = true
        val p = ctx.getSharedPreferences("shorts", android.content.Context.MODE_PRIVATE)
        follows.addAll(p.getStringSet("follows", emptySet()) ?: emptySet())
        hidden.addAll(p.getStringSet("hidden", emptySet()) ?: emptySet())
        runCatching { JSONArray(p.getString("saved", "[]")).objects().forEach { j ->
            saved.add(Clip(j.str("key"), j.optBoolean("src"), j.str("video"), j.strOrNull("poster"), j.str("caption"), j.str("creator"), j.str("cid"),
                j.strOrNull("avatar"), false, j.optInt("d"), 0, 0, 0, false, j.str("share"), null)) } }
    }
    fun persist(ctx: android.content.Context) {
        val arr = JSONArray(); saved.forEach { s -> arr.put(JSONObject().put("key", s.key).put("src", s.fromSrc).put("video", s.video).put("poster", s.poster)
            .put("caption", s.caption).put("creator", s.creator).put("cid", s.creatorId).put("avatar", s.avatar).put("d", s.duration).put("share", s.shareId)) }
        ctx.getSharedPreferences("shorts", android.content.Context.MODE_PRIVATE).edit().putStringSet("follows", follows.toSet())
            .putStringSet("hidden", hidden).putString("saved", arr.toString()).apply()
    }
}

class ShortFeed(private val src: Boolean) {
    val items = mutableStateListOf<Clip>()
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var offline by mutableStateOf(false)
    private var cursor: String? = null
    private var stage = 0
    private var end = false
    private val seenUrl = HashSet<String>()
    private val seenSize = HashSet<Long>()
    private val localLikes by lazy { Prefs.localLikes() }

    private fun fresh(s: Clip): Boolean {
        if (s.creatorId in Feeds.hidden) return false
        val u = s.video.substringBefore('?')
        if (!seenUrl.add(u)) return false
        if (s.size > 0 && !seenSize.add(s.size)) return false
        return true
    }

    suspend fun loadMore() {
        if (loading || end) return
        loading = true
        try {
            if (!src) {
                if (items.isEmpty()) {
                    val cached = withContext(Dispatchers.IO) { Disk.read("shorts-uploads") }
                    if (cached != null) items.addAll(cached.optJSONArray("shorts").objects().map(Clip::upload).filter(::fresh))
                    val j = Api.get("/api/shorts")
                    withContext(Dispatchers.IO) { Disk.write("shorts-uploads", j) }
                    val all = j.optJSONArray("shorts").objects().map(Clip::upload)
                    if (items.isEmpty()) items.addAll(all.filter(::fresh)) else {
                        val byId = all.associateBy { it.key }
                        for (i in items.indices) byId[items[i].key]?.let { items[i] = it }
                        items.addAll(all.filter(::fresh))
                    }
                }
                end = true
            } else {
                if (items.isEmpty() && stage == 0) {
                    val cached = withContext(Dispatchers.IO) { Disk.read("shorts-first") }
                    if (cached != null) items.addAll(Clip.srcPage(cached).filter(::fresh))
                }
                val path = when (stage) {
                    0 -> "/api/src-shorts?n=8"
                    1 -> "/api/src-shorts"
                    else -> cursor?.let { "/api/src-shorts?before=" + URLEncoder.encode(it, "UTF-8") }
                }
                if (path == null) { end = true; return }
                val j = Api.get(path)
                if (j.optBoolean("off")) { Feeds.srcOff = true; end = true; items.clear(); return }
                Feeds.srcOff = false
                if (stage == 0) withContext(Dispatchers.IO) { Disk.write("shorts-first", j) }
                cursor = j.strOrNull("next")
                stage = if (stage == 0 && !j.optBoolean("more")) 2 else stage + 1
                val watched = withContext(Dispatchers.IO) { Feeds.seenSet() }
                val page = Clip.srcPage(j).filter(::fresh).sortedBy { if (it.video in watched) 1 else 0 }
                val counted = withContext(Dispatchers.IO) { counts(page) }
                items.addAll(counted)
                if (cursor == null && stage >= 2) end = true
            }
            error = null; offline = false
        } catch (e: Exception) { error = Api.friendly(e); offline = Api.isOffline(e) } finally { loading = false }
    }

    private suspend fun counts(page: List<Clip>): List<Clip> {
        if (page.isEmpty()) return page
        return try {
            val out = ArrayList<Clip>()
            for (chunk in page.chunked(60)) {
                val j = Api.get("/api/src-react?ids=" + chunk.joinToString(",") { it.key })
                val likes = j.optJSONObject("likes") ?: JSONObject(); val cm = j.optJSONObject("comments") ?: JSONObject()
                val mine = j.optJSONArray("mine")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()
                chunk.forEach { s -> out.add(s.copy(likes = likes.optInt(s.key), comments = cm.optInt(s.key), liked = s.key in mine || (Session.me == null && s.key in localLikes))) }
            }
            out
        } catch (e: Exception) { page }
    }

    fun update(s: Clip) { val i = items.indexOfFirst { it.key == s.key }; if (i >= 0) items[i] = s }

    fun reset() { items.clear(); seenUrl.clear(); seenSize.clear(); cursor = null; stage = 0; end = false; error = null }

    fun saveLocalLike(key: String, on: Boolean) { if (on) localLikes.add(key) else localLikes.remove(key); Prefs.saveLocalLikes(localLikes) }
}

@SuppressLint("UnsafeOptInUsageError")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShortsScreen() {
    val c = ctx()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Feeds.loadPrefs(c) }
    LaunchedEffect(Feeds.srcOff) { if (Feeds.srcOff && (Feeds.tab == ShortsTab.ForYou || Feeds.tab == ShortsTab.Following)) Feeds.tab = ShortsTab.Uploads }
    val tab = Feeds.tab
    val feed = if (tab == ShortsTab.Uploads) Feeds.uploads else Feeds.forYou
    val items: List<Clip> = when (tab) {
        ShortsTab.Following -> Feeds.forYou.items.filter { it.creatorId in Feeds.follows }
        ShortsTab.Saved -> Feeds.saved
        else -> feed.items
    }
    LaunchedEffect(tab, Net.online) { if (feed.items.isEmpty() || feed.offline) feed.loadMore() }
    LaunchedEffect(Nav.shortsDeepLink) {
        val v = Nav.shortsDeepLink ?: return@LaunchedEffect
        Nav.shortsDeepLink = null
        runCatching {
            val j = Api.get("/api/src-shorts?v=" + URLEncoder.encode(v, "UTF-8"))
            Clip.srcPage(j).firstOrNull()?.let { s -> Feeds.tab = ShortsTab.ForYou; Feeds.forYou.items.removeAll { it.key == s.key }; Feeds.forYou.items.add(0, s) }
        }
    }

    val player = remember { Media.player(c).apply { repeatMode = Player.REPEAT_MODE_ONE; volume = if (Prefs.muted) 0f else 1f } }
    var paused by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) player.pause()
            if (e == Lifecycle.Event.ON_RESUME && !paused) player.play()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); player.release() }
    }
    LaunchedEffect(Prefs.muted) { player.volume = if (Prefs.muted) 0f else 1f }

    val pager = rememberPagerState(pageCount = { items.size })
    var commentsFor by remember { mutableStateOf<Clip?>(null) }
    val focus = remember { FocusRequester() }
    var showHint by remember { mutableStateOf(Keys.keypad && !Prefs.keypadHintSeen) }

    // Play the video on screen; warm up the next two.
    val itemsNow = rememberUpdatedState(items)
    LaunchedEffect(tab) { if (pager.pageCount > 0) pager.scrollToPage(0) }
    LaunchedEffect(pager, tab) {
        snapshotFlow { pager.settledPage to itemsNow.value.getOrNull(pager.settledPage)?.key }.collect { (page, _) ->
            val items = itemsNow.value
            val s = items.getOrNull(page) ?: return@collect
            player.setMediaItem(MediaItem.fromUri(s.video)); player.prepare(); player.playWhenReady = !paused
            player.setPlaybackParameters(PlaybackParameters(speed))
            Media.prefetch(c, items.drop(page + 1).take(2).map { it.video })
            if (tab == ShortsTab.ForYou || tab == ShortsTab.Uploads) { if (page >= items.size - 4) feed.loadMore() }
            Perf.shown("Shorts")
            Feeds.markSeen(s.video)
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { focus.requestFocus() } }

    fun like(s: Clip) {
        val on = !s.liked
        val next = s.copy(liked = on, likes = maxOf(0, s.likes + if (on) 1 else -1))
        if (s.fromSrc) Feeds.forYou.update(next) else Feeds.uploads.update(next)
        scope.launch {
            try {
                if (s.fromSrc) {
                    if (Session.me == null) Feeds.forYou.saveLocalLike(s.key, on)
                    else Api.post("/api/src-react", JSONObject().put("vid", s.key).put("like", on))
                } else {
                    if (Session.me == null) { toast(c, "Sign in to like Uploads"); Feeds.uploads.update(s); return@launch }
                    Api.put("/api/shorts", JSONObject().put("id", s.key).put("like", on))
                }
            } catch (e: Exception) { toast(c, Api.friendly(e)) }
        }
    }
    fun toggleSave(s: Clip) {
        val i = Feeds.saved.indexOfFirst { it.key == s.key }
        if (i >= 0) { Feeds.saved.removeAt(i); toast(c, "Removed from Saved") } else { Feeds.saved.add(0, s); toast(c, "Saved") }
        Feeds.persist(c)
    }
    fun togglePause() { paused = !paused; player.playWhenReady = !paused }
    fun go(d: Int) { val t = (pager.currentPage + d).coerceIn(0, maxOf(0, items.size - 1)); scope.launch { pager.animateScrollToPage(t) } }
    val tabs = ShortsTab.values().filter { !(Feeds.srcOff && (it == ShortsTab.ForYou || it == ShortsTab.Following)) }

    Box(Modifier.fillMaxSize().background(Color.Black).onKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
        if (showHint) { showHint = false; Prefs.hintSeen(); return@onKeyEvent true }
        val cur = items.getOrNull(pager.currentPage)
        when (e.key) {
            Key.DirectionUp -> { go(-1); true }
            Key.DirectionDown -> { go(1); true }
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Five -> { togglePause(); true }
            Key.DirectionLeft -> { player.seekTo(maxOf(0, player.currentPosition - 5000)); true }
            Key.DirectionRight -> { player.seekTo(player.currentPosition + 5000); true }
            Key.One -> { cur?.let { like(it) }; true }
            Key.Two -> { Prefs.setMute(!Prefs.muted); true }
            Key.Three -> { commentsFor = cur; true }
            Key.Four -> { cur?.let { toggleSave(it) }; true }
            Key.Six -> { cur?.let { share(c, it.shareUrl) }; true }
            else -> if (e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_STAR || e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_POUND) {
                Feeds.tab = tabs[(tabs.indexOf(tab) + 1) % tabs.size]; true
            } else false
        }
    }.focusRequester(focus).focusable()) {
        when {
            items.isEmpty() && feed.error != null && tab != ShortsTab.Saved -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Problem(feed.error!!, feed.offline, { scope.launch { feed.loadMore() } }) }
            items.isEmpty() && (feed.loading || (tab != ShortsTab.Saved && tab != ShortsTab.Following)) -> Loading()
            items.isEmpty() -> Text(if (tab == ShortsTab.Saved) "Nothing saved yet. Press Save (or key 4) on a video." else "Follow creators (tap + on their picture) to see them here.",
                Modifier.align(Alignment.Center).padding(32.dp), color = Color.White)
            else -> VerticalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { items.getOrNull(it)?.key ?: it }) { page ->
                val s = items[page]
                ShortPage(s, current = page == pager.settledPage, player = player, paused = paused,
                    onTap = { togglePause() }, onLike = { like(s) }, onComments = { commentsFor = s }, onSave = { toggleSave(s) },
                    speed = speed, onSpeed = { speed = it; player.setPlaybackParameters(PlaybackParameters(it)) },
                    onHide = { Feeds.hidden.add(s.creatorId); Feeds.persist(c); Feeds.forYou.items.removeAll { it.creatorId == s.creatorId }; toast(c, "You won't see ${s.creator} again") })
            }
        }
        Row(Modifier.align(Alignment.TopCenter).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (t in tabs) {
                Text(t.label, color = if (t == tab) Color.White else Color.White.copy(alpha = .6f), fontWeight = if (t == tab) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.tap(RoundedCornerShape(14.dp)) { Feeds.tab = t }.padding(horizontal = 9.dp, vertical = 5.dp))
            }
        }
        AnimatedVisibility(showHint, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.padding(24.dp).background(Color.Black.copy(alpha = .85f), RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text("Keypad keys", color = Gold, fontWeight = FontWeight.Bold)
                for (l in listOf("▲ ▼  previous / next video", "OK  pause / play", "◀ ▶  back / forward 5 s", "1 like · 2 sound · 3 comments", "4 save · 6 share · ✱ switch tab", "Back  leave Shorts"))
                    Text(l, color = Color.White, style = MaterialTheme.typography.bodySmall)
                Text("Press any key", color = Color.White.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    commentsFor?.let { s -> CommentsSheet(s, onDismiss = { commentsFor = null; scope.launch { delay(100); runCatching { focus.requestFocus() } } },
        onCount = { n -> val u = s.copy(comments = n); if (s.fromSrc) Feeds.forYou.update(u) else Feeds.uploads.update(u) }) }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun ShortPage(s: Clip, current: Boolean, player: androidx.media3.exoplayer.ExoPlayer, paused: Boolean, onTap: () -> Unit, onLike: () -> Unit,
                      onComments: () -> Unit, onSave: () -> Unit, speed: Float, onSpeed: (Float) -> Unit, onHide: () -> Unit) {
    val c = ctx()
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        // Letterbox on wide screens (tablets, landscape): the video keeps its upright 9:16 frame.
        val w = if (maxWidth > maxHeight * 0.75f) maxHeight * 9f / 16f else maxWidth
        Box(Modifier.width(w).fillMaxHeight().pointerInput(s.key) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { if (!s.liked) onLike() }) }) {
            if (s.poster != null) AsyncImage(s.poster, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            if (current) AndroidView(factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.short_player, null) as PlayerView).also { it.player = player }
            }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
            if (current && paused) Icon(Icons.Filled.PlayArrow, "Paused", tint = Color.White.copy(alpha = .85f), modifier = Modifier.align(Alignment.Center).size(72.dp))

            // Right side actions
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 46.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val following = s.creatorId in Feeds.follows
                Box {
                    Avatar(s.avatar, s.creator, 40.dp)
                    if (s.fromSrc) Box(Modifier.align(Alignment.BottomCenter).size(18.dp).clip(CircleShape).background(if (following) Green else Color(0xFFE53935))
                        .tap(CircleShape) { if (following) Feeds.follows.remove(s.creatorId) else Feeds.follows.add(s.creatorId); Feeds.persist(c); toast(c, if (following) "Unfollowed ${s.creator}" else "Following ${s.creator}") },
                        contentAlignment = Alignment.Center) { Text(if (following) "✓" else "+", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                }
                Action(if (s.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (s.likes > 0) s.likes.toString() else "Like", if (s.liked) Color(0xFFFF4D67) else Color.White, onLike)
                Action(Icons.Filled.ChatBubble, if (s.comments > 0) s.comments.toString() else "Comment", Color.White, onComments)
                val isSaved = Feeds.saved.any { it.key == s.key }
                Action(if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "Save", if (isSaved) Gold else Color.White, onSave)
                Action(Icons.Filled.Share, "Share", Color.White) { share(c, s.shareUrl) }
                Action(if (Prefs.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, if (Prefs.muted) "Off" else "Sound", Color.White) { Prefs.setMute(!Prefs.muted) }
                var menu by remember { mutableStateOf(false) }
                Box {
                    Action(Icons.Filled.MoreVert, "More", Color.White) { menu = true }
                    DropdownMenu(menu, { menu = false }) {
                        for (sp in listOf(0.5f, 1f, 1.5f, 2f)) DropdownMenuItem(text = { Text((if (sp == speed) "✓ " else "   ") + "Speed ${sp}×") }, onClick = { onSpeed(sp); menu = false })
                        if (s.fromSrc) DropdownMenuItem(text = { Text("Not interested in ${s.creator}") }, onClick = { onHide(); menu = false })
                        DropdownMenuItem(text = { Text("Copy link") }, onClick = {
                            (c.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("YID PLUS", s.shareUrl))
                            toast(c, "Link copied"); menu = false })
                    }
                }
            }
            // Caption + creator
            Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 64.dp, bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("@" + s.creator.ifBlank { "yidplus" }, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (s.verified) Icon(Icons.Filled.Verified, null, tint = Gold, modifier = Modifier.padding(start = 3.dp).size(14.dp))
                }
                var open by remember { mutableStateOf(false) }
                if (s.caption.isNotBlank()) Text(s.caption, color = Color.White, maxLines = if (open) 8 else 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content), modifier = Modifier.tap { open = !open })
            }
            if (current) ScrubBar(player, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(Modifier.tap(RoundedCornerShape(12.dp), onClick = onClick).padding(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(28.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** The line at the bottom: drag along it with a finger to move through the video (shows the time). */
@Composable
private fun ScrubBar(player: Player, modifier: Modifier) {
    var pos by remember { mutableLongStateOf(0L) }
    var len by remember { mutableLongStateOf(0L) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(player) { while (isActive) { if (drag == null) { pos = player.currentPosition; len = player.duration.coerceAtLeast(0) }; delay(250) } }
    val frac = drag ?: if (len > 0) (pos.toFloat() / len).coerceIn(0f, 1f) else 0f
    Box(modifier.fillMaxWidth().height(22.dp)
        .pointerInput(Unit) {
            widthPx = size.width.toFloat()
            detectHorizontalDragGestures(onDragStart = { drag = (it.x / size.width).coerceIn(0f, 1f) },
                onDragEnd = { drag?.let { f -> if (len > 0) player.seekTo((f * len).toLong()) }; drag = null },
                onDragCancel = { drag = null }) { ch, _ -> drag = (ch.position.x / size.width).coerceIn(0f, 1f) }
        }
        .pointerInput(Unit) { detectTapGestures { o -> if (len > 0) player.seekTo((o.x / size.width * len).toLong()) } }) {
        val h = if (drag != null) 5.dp else 2.dp
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(h).background(Color.White.copy(alpha = .3f)))
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(frac).height(h).background(Gold))
        if (drag != null) Text("${dur((frac * len / 1000).toInt()).ifBlank { "0:00" }} / ${dur((len / 1000).toInt())}", color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .6f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(s: Clip, onDismiss: () -> Unit, onCount: (Int) -> Unit) {
    val c = ctx()
    val scope = rememberCoroutineScope()
    val list = remember(s.key) { mutableStateListOf<Comment>() }
    var loading by remember { mutableStateOf(true) }
    var canDelete by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    suspend fun load() {
        loading = true
        try {
            if (s.fromSrc) {
                val j = Api.get("/api/src-comments?vid=" + URLEncoder.encode(s.key, "UTF-8"))
                canDelete = j.optBoolean("can_delete")
                list.clear(); list.addAll(j.optJSONArray("comments").objects().map { Comment(it.str("id"), it.str("user_id"), it.str("nickname"), Api.abs(it.strOrNull("photo")), it.str("text"),
                    it.optLong("t").takeIf { t -> t > 0 }?.let { t -> java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(t)) }, it.optBoolean("mine")) })
            } else {
                val j = Api.get("/api/shorts/comments?short_id=" + URLEncoder.encode(s.key, "UTF-8"))
                list.clear(); list.addAll(j.optJSONArray("comments").objects().reversed().map { Comment(it.str("id"), it.str("user_id"), it.str("nickname"), null, it.str("text"), it.strOrNull("created_at"), it.str("user_id") == Session.me?.id) })
            }
            onCount(list.size)
        } catch (e: Exception) { toast(c, Api.friendly(e)) } finally { loading = false }
    }
    LaunchedEffect(s.key) { load() }
    fun post() {
        val t = text.trim(); if (t.isEmpty()) return
        text = ""
        scope.launch {
            try {
                if (s.fromSrc) Api.post("/api/src-comments", JSONObject().put("vid", s.key).put("text", t))
                else Api.post("/api/shorts/comments", JSONObject().put("short_id", s.key).put("text", t))
                load()
            } catch (e: Exception) { toast(c, Api.friendly(e)); text = t }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(min = 200.dp).padding(bottom = 8.dp)) {
            Text("${list.size} comments", Modifier.align(Alignment.CenterHorizontally).padding(6.dp), fontWeight = FontWeight.Bold)
            Box(Modifier.weight(1f, fill = false).heightIn(max = 380.dp)) {
                if (loading && list.isEmpty()) Loading(Modifier.fillMaxWidth().height(120.dp))
                else if (list.isEmpty()) Text("No comments yet", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else LazyColumn {
                    items(list, key = { it.id }) { cm ->
                        Row(Modifier.fillMaxWidth().tap(RoundedCornerShape(0.dp)) {}.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Avatar(cm.photo, cm.nick, 30.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(cm.nick + "  ·  " + ago(cm.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(cm.text, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
                            }
                            if (s.fromSrc && (cm.mine || canDelete)) IconBtn(Icons.Filled.Delete, "Delete", {
                                scope.launch { runCatching { Api.delete("/api/src-comments?vid=" + URLEncoder.encode(s.key, "UTF-8") + "&id=" + URLEncoder.encode(cm.id, "UTF-8")) }; load() } }, size = 32.dp)
                        }
                    }
                }
            }
            if (Session.me == null) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sign in to comment", Modifier.weight(1f)); PrimaryButton("Sign in", { onDismiss(); Nav.go(Tab.Profile) })
                }
            } else Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { if (it.length <= 300) text = it }, Modifier.weight(1f).firstFocus(), placeholder = { Text("Add a comment") }, maxLines = 3,
                    textStyle = TextStyle(textDirection = TextDirection.Content), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { post() }))
                IconBtn(Icons.AutoMirrored.Filled.Send, "Post", { post() }, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
