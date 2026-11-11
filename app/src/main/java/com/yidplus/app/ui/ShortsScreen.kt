package com.yidplus.app.ui

import androidx.compose.foundation.border

import androidx.compose.foundation.layout.imePadding

import androidx.compose.foundation.layout.navigationBarsPadding

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.sp
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
    /** Only this creator's videos (opened from their page); null = everyone. */
    var who by mutableStateOf<String?>(null)
    var whoName by mutableStateOf("")
    /** The video to show first when Shorts opens (from search, a channel, a creator page). */
    var startKey by mutableStateOf<String?>(null)
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

    private val seenKey = HashSet<String>()
    private fun fresh(s: Clip): Boolean {
        if (s.creatorId in Feeds.hidden) return false
        if (!seenKey.add(s.key)) return false
        val u = s.video.substringBefore('?')
        if (!seenUrl.add(u)) return false
        if (s.size > 0 && !seenSize.add(s.size)) return false
        return true
    }

    private val vetted = HashSet<String>()
    private val prints = ArrayList<Pair<Long, Int>>()
    var removedDupes = 0
        private set

    /**
     * The same video is often posted again as a new file (another creator, another size), so its id and address
     * differ. Before a video is reached, its poster's fingerprint (64-bit difference hash) and length are compared
     * with every video already in the feed; a match is dropped. Checks [count] videos from [from] on; the one on
     * screen ([from]) is only remembered, never removed.
     */
    suspend fun vet(ctx: android.content.Context, from: Int, count: Int = 4) {
        var i = from; var n = 0
        while (n < count && i < items.size) {
            val s = items[i]
            if (vetted.add(s.key)) {
                val h = s.poster?.let { posterPrint(ctx, it) }
                if (h != null) {
                    val dup = prints.any { (p, d) -> java.lang.Long.bitCount(p xor h) <= 6 && kotlin.math.abs(d - s.duration) <= 1 }
                    if (dup && i > from && i < items.size && items[i].key == s.key) { items.removeAt(i); removedDupes++; continue }
                    prints.add(h to s.duration)
                }
            }
            i++; n++
        }
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
                    if (cached != null) { ShortTopics.learn(cached); items.addAll(Clip.srcPage(cached).filter(::fresh)) }
                }
                val path = when (stage) {
                    0 -> "/api/src-shorts?n=8"
                    1 -> "/api/src-shorts"
                    else -> cursor?.let { "/api/src-shorts?before=" + URLEncoder.encode(it, "UTF-8") }
                }
                if (path == null) { end = true; return }
                val j = Api.get(path)
                if (j.optBoolean("off")) { Feeds.srcOff = true; end = true; items.clear(); return }
                ShortTopics.learn(j)
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
    val base: List<Clip> = when (tab) {
        ShortsTab.Following -> Feeds.forYou.items.filter { it.creatorId in Feeds.follows }
        ShortsTab.Saved -> Feeds.saved
        else -> feed.items
    }
    val who = Feeds.who
    val topic = ShortTopics.topic
    val items: List<Clip> = if (who == null && (topic == "All" || tab != ShortsTab.ForYou)) base
        else base.filter { (who == null || it.creatorId == who) && (tab != ShortsTab.ForYou || ShortTopics.matches(it)) }
    val smallScreen = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 560
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
    // Speed report: time from a swipe / key settling on a video to its first picture on screen.
    val swipeAt = remember { longArrayOf(0L) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                val t0 = swipeAt[0]
                if (t0 > 0) { android.util.Log.i("YidPerf", "Shorts video playing ${android.os.SystemClock.elapsedRealtime() - t0} ms after swipe"); swipeAt[0] = 0 }
            }
        }
        player.addListener(l); onDispose { player.removeListener(l) }
    }

    val pager = rememberPagerState(pageCount = { items.size })
    var commentsFor by remember { mutableStateOf<Clip?>(null) }
    val focus = remember { FocusRequester() }
    var showHint by remember { mutableStateOf(Keys.keypad && !Prefs.keypadHintSeen) }

    // Play the video on screen; warm up the next two.
    val itemsNow = rememberUpdatedState(items)
    LaunchedEffect(tab, who, topic) { if (pager.pageCount > 0 && Feeds.startKey == null) pager.scrollToPage(0) }
    LaunchedEffect(topic, items.size) { if (tab == ShortsTab.ForYou && topic != "All" && items.size < 4 && !feed.loading) feed.loadMore() }
    LaunchedEffect(Feeds.startKey, items.size) {
        val k = Feeds.startKey ?: return@LaunchedEffect
        val i = items.indexOfFirst { it.key == k }
        if (i >= 0) { pager.scrollToPage(i); Feeds.startKey = null }
    }
    LaunchedEffect(pager, tab) {
        snapshotFlow { pager.settledPage to itemsNow.value.getOrNull(pager.settledPage)?.key }.collect { (page, _) ->
            val items = itemsNow.value
            val s = items.getOrNull(page) ?: return@collect
            swipeAt[0] = android.os.SystemClock.elapsedRealtime()
            player.setMediaItem(MediaItem.fromUri(s.video)); player.prepare(); player.playWhenReady = !paused
            player.setPlaybackParameters(PlaybackParameters(speed))
            if ((tab == ShortsTab.ForYou || tab == ShortsTab.Uploads) && feed.items === items) feed.vet(c, page, 4)
            val itemsV = itemsNow.value
            Media.prefetch(c, itemsV.drop(page + 1).take(2).map { it.video })
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
            Key.Seven -> { Nav.open(UploadShortPage()); true }
            Key.Eight -> { cur?.let { Nav.open(CreatorPage(it.creatorId, it.creator, it.avatar, it.verified, it.fromSrc)) }; true }
            Key.Nine -> { Nav.open(SiteSearchPage(4)); true }
            else -> if (e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_STAR || e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_POUND) {
                Feeds.tab = tabs[(tabs.indexOf(tab) + 1) % tabs.size]; true
            } else false
        }
    }.focusRequester(focus).focusable()) {
        when {
            items.isEmpty() && feed.error != null && tab != ShortsTab.Saved -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Problem(feed.error!!, feed.offline, { scope.launch { feed.loadMore() } }) }
            items.isEmpty() && (who != null || (topic != "All" && tab == ShortsTab.ForYou)) && !feed.loading ->
                Text(if (who != null) "No more videos from @${Feeds.whoName}. Tap ✕ on top to see everyone." else "No videos in this topic yet.",
                    Modifier.align(Alignment.Center).padding(32.dp), color = Color.White)
            items.isEmpty() && (feed.loading || (tab != ShortsTab.Saved && tab != ShortsTab.Following)) -> Loading()
            items.isEmpty() -> Text(if (tab == ShortsTab.Saved) "Nothing saved yet. Press Save (or key 4) on a video." else "Follow creators (tap + on their picture) to see them here.",
                Modifier.align(Alignment.Center).padding(32.dp), color = Color.White)
            else -> VerticalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { items.getOrNull(it)?.key ?: it }) { page ->
                val s = items[page]
                ShortPage(s, current = page == pager.settledPage, player = player, paused = paused,
                    onTap = { togglePause() }, onLike = { like(s) }, onComments = { commentsFor = s }, onSave = { toggleSave(s) },
                    speed = speed, onSpeed = { speed = it; player.setPlaybackParameters(PlaybackParameters(it)) },
                    onHide = { Feeds.hidden.add(s.creatorId); Feeds.persist(c); Feeds.forYou.items.removeAll { it.creatorId == s.creatorId }; toast(c, "You won't see ${s.creator} again") },
                    onCreator = { Nav.open(CreatorPage(s.creatorId, s.creator, s.avatar, s.verified, s.fromSrc)) },
                    onNotInterested = { Feeds.markSeen(s.video); Feeds.forYou.items.removeAll { it.key == s.key }; toast(c, "Got it. You'll see fewer like this") },
                    onDelete = if (!s.fromSrc && (Session.me?.id == s.creatorId || SoUser.isAdmin)) ({ deleteUpload(c, s) }) else null)
            }
        }
        ShortsTopBar(tabs, tab, who, topic, smallScreen, Modifier.align(Alignment.TopCenter))
        AnimatedVisibility(showHint, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.padding(24.dp).background(Color.Black.copy(alpha = .85f), RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text("Keypad keys", color = Gold, fontWeight = FontWeight.Bold)
                for (l in listOf("▲ ▼  previous / next video", "OK  pause / play", "◀ ▶  back / forward 5 s", "1 like · 2 sound · 3 comments", "4 save · 6 share · ✱ switch tab", "7 upload · 8 creator · 9 search", "Back  leave Shorts"))
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
                      onComments: () -> Unit, onSave: () -> Unit, speed: Float, onSpeed: (Float) -> Unit, onHide: () -> Unit,
                      onCreator: () -> Unit = {}, onNotInterested: () -> Unit = {}, onDelete: (() -> Unit)? = null) {
    val c = ctx()
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        // Letterbox on wide screens (tablets, landscape): the video keeps its upright 9:16 frame.
        // Wide screens (tablets, landscape) letterbox the upright video in a 9:16 frame; small screens use the full
        // width so the buttons sit beside the video instead of on top of it.
        val w = if (maxWidth >= 600.dp && maxWidth > maxHeight) maxHeight * 9f / 16f else maxWidth
        val small = maxHeight < 560.dp
        Box(Modifier.width(w).fillMaxHeight().pointerInput(s.key) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { if (!s.liked) onLike() }) }) {
            if (s.poster != null) AsyncImage(s.poster, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            if (current) AndroidView(factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.short_player, null) as PlayerView).also { it.player = player }
            }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
            if (current && paused) Icon(Icons.Filled.PlayArrow, "Paused", tint = Color.White.copy(alpha = .85f), modifier = Modifier.align(Alignment.Center).size(72.dp))

            // Right side actions
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = if (small) 30.dp else 46.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (small) 4.dp else 10.dp)) {
                val following = s.creatorId in Feeds.follows
                // On short screens the creator's picture moves down next to the name, so the rail never reaches the top tabs.
                if (!small) Box {
                    Box(Modifier.tap(CircleShape, onClick = onCreator)) { Avatar(s.avatar, s.creator, 42.dp) }
                    if (s.fromSrc) Box(Modifier.align(Alignment.BottomCenter).size(18.dp).clip(CircleShape).background(if (following) Green else Color(0xFFE53935))
                        .tap(CircleShape) { if (following) Feeds.follows.remove(s.creatorId) else Feeds.follows.add(s.creatorId); Feeds.persist(c); toast(c, if (following) "Unfollowed ${s.creator}" else "Following ${s.creator}") },
                        contentAlignment = Alignment.Center) { Text(if (following) "✓" else "+", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                }
                Action(if (s.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (s.likes > 0) s.likes.toString() else "Like", if (s.liked) Color(0xFFFF4D67) else Color.White, small, onLike)
                Action(Icons.Filled.ChatBubble, if (s.comments > 0) s.comments.toString() else "Comment", Color.White, small, onComments)
                val isSaved = Feeds.saved.any { it.key == s.key }
                if (!small) Action(if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "Save", if (isSaved) Gold else Color.White, onClick = onSave)
                Action(Icons.Filled.Share, "Share", Color.White, small = small) { share(c, s.shareUrl) }
                if (!small) Action(if (Prefs.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, if (Prefs.muted) "Off" else "Sound", Color.White) { Prefs.setMute(!Prefs.muted) }
                var menu by remember { mutableStateOf(false) }
                var askDelete by remember { mutableStateOf(false) }
                if (askDelete && onDelete != null) AlertDialog(onDismissRequest = { askDelete = false }, title = { Text("Delete this video?") },
                    confirmButton = { TextButton({ askDelete = false; onDelete() }) { Text("Delete") } }, dismissButton = { TextButton({ askDelete = false }) { Text("Cancel") } })
                Box {
                    Action(Icons.Filled.MoreVert, "More", Color.White, small = small) { menu = true }
                    DropdownMenu(menu, { menu = false }) {
                        if (small) {
                            DropdownMenuItem(text = { Text(if (isSaved) "Saved ✓ (4)" else "Save (4)") }, onClick = { onSave(); menu = false })
                            DropdownMenuItem(text = { Text(if (Prefs.muted) "Sound on (2)" else "Sound off (2)") }, onClick = { Prefs.setMute(!Prefs.muted); menu = false })
                        }
                        for (sp in listOf(0.5f, 1f, 1.5f, 2f)) DropdownMenuItem(text = { Text((if (sp == speed) "✓ " else "   ") + "Speed ${sp}×") }, onClick = { onSpeed(sp); menu = false })
                        DropdownMenuItem(text = { Text("@${s.creator.ifBlank { "yidplus" }}'s videos (8)") }, onClick = { menu = false; onCreator() })
                        if (s.fromSrc) DropdownMenuItem(text = { Text("Not interested in this video") }, onClick = { menu = false; onNotInterested() })
                        if (s.fromSrc) DropdownMenuItem(text = { Text("Hide ${s.creator}") }, onClick = { onHide(); menu = false })
                        if (onDelete != null) DropdownMenuItem(text = { Text("Delete my video", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; askDelete = true })
                        DropdownMenuItem(text = { Text("Copy link") }, onClick = {
                            (c.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("YID PLUS", s.shareUrl))
                            toast(c, "Link copied"); menu = false })
                    }
                }
            }
            // Soft dark gradient under the caption so white text is always readable.
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .62f)))))
            // Caption + creator
            Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 64.dp, bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (small) {
                        Avatar(s.avatar, s.creator, 28.dp); Spacer(Modifier.width(6.dp))
                    }
                    Text("@" + s.creator.ifBlank { "yidplus" }, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).tap(RoundedCornerShape(6.dp), onClick = onCreator))
                    if (s.verified) Icon(Icons.Filled.Verified, null, tint = Gold, modifier = Modifier.padding(start = 3.dp).size(14.dp))
                    if (small && s.fromSrc) {
                        val f = s.creatorId in Feeds.follows
                        Text(if (f) "Following" else "+ Follow", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp).border(1.dp, Color.White.copy(alpha = .8f), RoundedCornerShape(12.dp))
                                .tap(RoundedCornerShape(12.dp)) { if (f) Feeds.follows.remove(s.creatorId) else Feeds.follows.add(s.creatorId); Feeds.persist(c) }
                                .padding(horizontal = 8.dp, vertical = 2.dp))
                    }
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
private fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, small: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.tap(RoundedCornerShape(12.dp), onClick = onClick).padding(if (small) 2.dp else 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(if (small) 26.dp else 30.dp))
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

/** 64-bit difference hash of a poster (from the image cache the feed already uses). */
suspend fun posterPrint(ctx: android.content.Context, url: String): Long? = withContext(Dispatchers.IO) {
    runCatching {
        val r = coil.Coil.imageLoader(ctx).execute(coil.request.ImageRequest.Builder(ctx).data(url).size(72, 64).allowHardware(false).build())
        val bmp = (r.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return@runCatching null
        val sm = android.graphics.Bitmap.createScaledBitmap(bmp, 9, 8, true)
        fun g(p: Int) = (p shr 16 and 255) * 299 + (p shr 8 and 255) * 587 + (p and 255) * 114
        var h = 0L
        for (y in 0 until 8) for (x in 0 until 8) if (g(sm.getPixel(x, y)) > g(sm.getPixel(x + 1, y))) h = h or (1L shl (y * 8 + x))
        h
    }.getOrNull()
}

/** QA: load 200+ For You videos, vet them all, and log how many re-posted duplicates were dropped and how many are left. */
suspend fun qaDedupe(ctx: android.content.Context) {
    val f = ShortFeed(true)
    var guard = 0
    while (f.items.size < 230 && guard++ < 20) { val before = f.items.size; f.loadMore(); if (f.items.size == before && guard > 3) break }
    f.vet(ctx, 0, f.items.size)
    val first = f.items.take(200)
    val keys = first.map { it.key }.toSet().size; val urls = first.map { it.video.substringBefore('?') }.toSet().size
    val pr = first.mapNotNull { s -> s.poster?.let { p -> posterPrint(ctx, p)?.let { it to s.duration } } }
    var left = 0
    for (a in pr.indices) for (b in 0 until a) if (java.lang.Long.bitCount(pr[a].first xor pr[b].first) <= 6 && kotlin.math.abs(pr[a].second - pr[b].second) <= 1) { left++; break }
    android.util.Log.i("YidDedupe", "checked ${first.size} videos: removed ${f.removedDupes} re-posted duplicates; left: same id ${first.size - keys}, same file ${first.size - urls}, same picture+length $left")
}
