package com.yidplus.app.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.yidplus.app.R
import com.yidplus.app.data.Api
import com.yidplus.app.data.Clip
import com.yidplus.app.data.Disk
import com.yidplus.app.data.Media
import com.yidplus.app.data.Net
import com.yidplus.app.data.Perf
import com.yidplus.app.data.Prefs
import com.yidplus.app.data.Session
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

// Shorts (2.1, helper SH): the site's yidplus-shorts.html + js/shorts.js + js/src-shorts.js, natively.
// Following | For You | Uploads, Saved as a chip; every creator to follow, one creator's own videos,
// no video twice (3 days on this phone), For You that learns this phone, instant taps, sheets, end pages.

enum class ShortsTab(val label: String) { Following("Following"), ForYou("For You"), Uploads("Uploads"), Saved("Saved") }

/** The feeds live as long as the app, so going back to Shorts is instant and keeps your place. */
object Feeds {
    var tab by mutableStateOf(ShortsTab.ForYou)
    /** The tab Saved was opened from (Saved is a chip, like the site). */
    var savedFrom = ShortsTab.ForYou
    var srcOff by mutableStateOf(false)
    /** One creator's own videos (site: S.who), every key of the creator; null = everyone. */
    var who by mutableStateOf<String?>(null)
    var whoKeys by mutableStateOf<List<String>>(emptyList())
    var whoName by mutableStateOf("")
    var whoFrom = "feed"
    /** Following: the creators list is showing (nobody followed yet, or "‹ Back" from a creator). */
    var pick by mutableStateOf(false)
    var refresh by mutableIntStateOf(0)
    /** The video to show first when Shorts opens (from search, a channel, a creator page). */
    var startKey by mutableStateOf<String?>(null)
    val forYou = ShortFeed(true)
    val uploads = ShortFeed(false)
    val following = ShList()
    val whoList = ShList()
    val follows = mutableStateListOf<String>()
    val saved = mutableStateListOf<Clip>()
    var loadedPrefs = false
    val hidden = HashSet<String>()
    /** uploads counted as viewed in this app run (one view per video per run, like one page view on the site) */
    val viewed = HashSet<String>()
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
        ShPrefs.init(ctx)
        val p = ctx.getSharedPreferences("shorts", android.content.Context.MODE_PRIVATE)
        follows.addAll(p.getStringSet("follows", emptySet()) ?: emptySet())
        hidden.addAll(p.getStringSet("hidden", emptySet()) ?: emptySet())
        runCatching { JSONArray(p.getString("saved", "[]")).objects().forEach { j ->
            saved.add(Clip(j.str("key"), j.optBoolean("src"), j.str("video"), j.strOrNull("poster"), j.str("caption"), j.str("creator"), j.str("cid"),
                j.strOrNull("avatar"), false, j.optInt("d"), 0, 0, 0, false, j.str("share"), j.strOrNull("at"))) } }
        ShDir.loadMeta()
        // the last feed he was on (site: yp_shorts_tab)
        if (startKey == null && Nav.shortsDeepLink == null) when (ShPrefs.lastTab()) { "following" -> tab = ShortsTab.Following; "yp" -> tab = ShortsTab.Uploads }
    }
    fun persist(ctx: android.content.Context) {
        val arr = JSONArray(); saved.forEach { s -> arr.put(JSONObject().put("key", s.key).put("src", s.fromSrc).put("video", s.video).put("poster", s.poster)
            .put("caption", s.caption).put("creator", s.creator).put("cid", s.creatorId).put("avatar", s.avatar).put("d", s.duration).put("share", s.shareId).put("at", s.at)) }
        ctx.getSharedPreferences("shorts", android.content.Context.MODE_PRIVATE).edit().putStringSet("follows", follows.toSet())
            .putStringSet("hidden", HashSet(hidden)).putString("saved", arr.toString()).apply()
    }
    fun isSaved(s: Clip) = saved.any { it.key == s.key } || (!s.fromSrc && s.key in ShMeta.serverSaved)
    /** One creator's own videos, in the feed (site: openWho). */
    fun openWho(keys: List<String>, name: String, from: String) {
        val ks = keys.filter { it.isNotBlank() }.ifEmpty { return }
        if (tab == ShortsTab.Saved) tab = savedFrom
        whoKeys = ks; whoName = name; whoFrom = from; pick = false; who = ks.first()
    }
    /** "‹ Back" (site: whoBack): to the list, the sheet, or the feed he came from. */
    fun whoBack(): String { val f = whoFrom; who = null; whoKeys = emptyList(); whoFrom = "feed"; if (f == "list" && tab == ShortsTab.Following) pick = true; return f }
}

class ShortFeed(private val src: Boolean) {
    val items = mutableStateListOf<Clip>()
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var offline by mutableStateOf(false)
    var ended by mutableStateOf(false)
    /** Blocks walked back without a single new video (site: emptyRun; gives up after 12). */
    var emptyRun by mutableIntStateOf(0)
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
        // site: the same video (or a copy) shown on this phone in the last 3 days never comes back
        if (src && ShKeys.isDup(s)) return false
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

    /** One page; For You walks back block by block (up to 12 empty ones in a row) until it has something new. */
    suspend fun loadMore() {
        if (loading || end) return
        loading = true
        try {
            if (!src) {
                if (items.isEmpty()) {
                    val cached = withContext(Dispatchers.IO) { Disk.read("shorts-uploads") }
                    if (cached != null) { ShMeta.learn(cached); items.addAll(cached.optJSONArray("shorts").objects().map(Clip::upload).filter(::fresh)) }
                    val j = Api.get("/api/shorts")
                    withContext(Dispatchers.IO) { Disk.write("shorts-uploads", j) }
                    ShMeta.learn(j)
                    val all = j.optJSONArray("shorts").objects().map(Clip::upload)
                    if (items.isEmpty()) items.addAll(all.filter(::fresh)) else {
                        val byId = all.associateBy { it.key }
                        for (i in items.indices) byId[items[i].key]?.let { items[i] = it }
                        items.addAll(all.filter(::fresh))
                    }
                }
                end = true; ended = true
            } else {
                if (items.isEmpty() && stage == 0) {
                    val cached = withContext(Dispatchers.IO) { Disk.read("shorts-first") }
                    if (cached != null) { SoShortTopics.learn(cached); ShDir.learnCreators(cached); items.addAll(ShRank.rank(Clip.srcPage(cached).filter(::fresh))) }
                }
                var tries = 0
                while (tries++ < 12) {
                    val path = when (stage) {
                        0 -> "/api/src-shorts?n=12"
                        1 -> "/api/src-shorts"
                        else -> cursor?.let { "/api/src-shorts?before=" + URLEncoder.encode(it, "UTF-8") }
                    }
                    if (path == null) { end = true; ended = true; break }
                    val j = Api.get(path)
                    if (j.optBoolean("off")) { Feeds.srcOff = true; end = true; items.clear(); return }
                    SoShortTopics.learn(j); ShDir.learnCreators(j)
                    Feeds.srcOff = false
                    if (stage == 0) withContext(Dispatchers.IO) { Disk.write("shorts-first", j) }
                    cursor = j.strOrNull("next")
                    stage = if (stage == 0 && !j.optBoolean("more")) 2 else stage + 1
                    val all = Clip.srcPage(j)
                    val page = ShRank.rank(all.filter(::fresh))
                    // the counts also refresh videos already on screen from the cached first page (they came without likes/comments)
                    val shown = all.filter { a -> a.key !in page.map { it.key } && items.any { it.key == a.key } }
                    val byKey = withContext(Dispatchers.IO) { counts(page + shown) }.associateBy { it.key }
                    for (i in items.indices) byKey[items[i].key]?.let { u -> if (u.likes != items[i].likes || u.comments != items[i].comments || u.liked != items[i].liked) items[i] = u }
                    val counted = page.map { byKey[it.key] ?: it }
                    items.addAll(counted)
                    if (cursor == null && stage >= 2) { end = true; ended = true; break }
                    if (counted.isNotEmpty()) { emptyRun = 0; break }
                    emptyRun++
                    if (emptyRun >= 12) break
                }
            }
            error = null; offline = false
        } catch (e: Exception) { error = Api.friendly(e); offline = Api.isOffline(e) } finally { loading = false }
    }

    private suspend fun counts(page: List<Clip>): List<Clip> {
        if (page.isEmpty()) return page
        return try {
            val out = ArrayList<Clip>()
            for (chunk in page.chunked(40)) {
                val j = Api.get("/api/src-react?ids=" + chunk.joinToString(",") { it.key })
                val likes = j.optJSONObject("likes") ?: JSONObject(); val cm = j.optJSONObject("comments") ?: JSONObject()
                val mine = j.optJSONArray("mine")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()
                chunk.forEach { s -> out.add(s.copy(likes = likes.optInt(s.key), comments = cm.optInt(s.key), liked = s.key in mine || (Session.me == null && s.key in localLikes))) }
            }
            out
        } catch (e: Exception) { page }
    }

    fun update(s: Clip) { val i = items.indexOfFirst { it.key == s.key }; if (i >= 0) items[i] = s }

    fun reset() { items.clear(); seenUrl.clear(); seenSize.clear(); seenKey.clear(); cursor = null; stage = 0; end = false; ended = false; error = null; emptyRun = 0; if (src) ShKeys.resetSession() }
    /** "Load more" / "Try again" after the walk gave up. */
    fun retry() { emptyRun = 0; error = null }

    fun saveLocalLike(key: String, on: Boolean) { if (on) localLikes.add(key) else localLikes.remove(key); Prefs.saveLocalLikes(localLikes) }
}

/** Every list that can hold a clip: a like or a comment count changes it everywhere. */
private fun shUpdate(s: Clip) { Feeds.forYou.update(s); Feeds.uploads.update(s)
    Feeds.following.items.indexOfFirst { it.key == s.key }.takeIf { it >= 0 }?.let { Feeds.following.items[it] = s }
    Feeds.whoList.items.indexOfFirst { it.key == s.key }.takeIf { it >= 0 }?.let { Feeds.whoList.items[it] = s } }

private data class ShBurstAt(val id: Long, val x: Float, val y: Float, val rot: Float)

@SuppressLint("UnsafeOptInUsageError")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShortsScreen() {
    val c = ctx()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Feeds.loadPrefs(c) }
    LaunchedEffect(Feeds.srcOff) { if (Feeds.srcOff && (Feeds.tab == ShortsTab.ForYou || Feeds.tab == ShortsTab.Following)) Feeds.tab = ShortsTab.Uploads }
    // the admin's switch came back on? (site: /api/src-shorts?switch=1)
    LaunchedEffect(Feeds.srcOff) { if (Feeds.srcOff) runCatching { if (Api.get("/api/src-shorts?switch=1").optBoolean("enabled")) { Feeds.srcOff = false; Feeds.forYou.reset() } } }
    val tab = Feeds.tab
    val who = Feeds.who
    val feed = if (tab == ShortsTab.Uploads) Feeds.uploads else Feeds.forYou
    LaunchedEffect(tab) { if (tab != ShortsTab.Saved) ShPrefs.setLastTab(when (tab) { ShortsTab.Following -> "following"; ShortsTab.Uploads -> "yp"; else -> "fy" }) }
    // An admin can block a member from Shorts (site: FeatureBlock) — they see why, and can write to the admins.
    var blocked by remember { mutableStateOf<String?>(null) }
    var isBlocked by remember { mutableStateOf(false) }
    LaunchedEffect(Session.me?.id) {
        if (Session.me != null) runCatching { Api.get("/api/admin/feature-blocks").optJSONArray("blocks").objects().firstOrNull { it.str("feature") == "shorts" } }
            .getOrNull()?.let { b -> blocked = b.strOrNull("blocked_until"); isBlocked = true }
    }
    LaunchedEffect(Session.me?.id) { ShLikeQueue.flush(); while (isActive) { delay(5 * 60_000L); ShLikeQueue.flush() } }
    val topic = SoShortTopics.topic
    val following = Feeds.follows.size
    val pickList = who == null && tab == ShortsTab.Following && (following == 0 || Feeds.pick)
    val base: List<Clip> = when {
        who != null -> Feeds.whoList.items
        tab == ShortsTab.Saved -> Feeds.saved
        tab == ShortsTab.Following -> Feeds.following.items
        else -> feed.items
    }
    val items: List<Clip> = if (who != null || tab == ShortsTab.Saved || topic == "All" || tab == ShortsTab.Uploads) base
        else base.filter { SoShortTopics.matches(it) }
    val smallScreen = LocalConfiguration.current.screenHeightDp < 560
    LaunchedEffect(tab, Net.online, Feeds.refresh) { if (feed.items.isEmpty() || feed.offline) feed.loadMore() }
    LaunchedEffect(who, Feeds.whoKeys) { if (who != null) Feeds.whoList.loadWho(Feeds.whoKeys, Feeds.whoName) }
    LaunchedEffect(tab, who, pickList, following) { if (tab == ShortsTab.Following && who == null && !pickList && following > 0) Feeds.following.loadFollowing(Feeds.forYou.items) }
    LaunchedEffect(Nav.shortsDeepLink) {
        val v = Nav.shortsDeepLink ?: return@LaunchedEffect
        Nav.shortsDeepLink = null
        // yidplus.com/shorts#<id> = a member's upload (site: shorts.js hash)
        if (v.startsWith("#")) { val id = v.drop(1); Feeds.who = null; Feeds.tab = ShortsTab.Uploads; Feeds.startKey = id
            if (Feeds.uploads.items.isEmpty()) Feeds.uploads.loadMore()
            if (Feeds.uploads.items.none { it.key == id }) { delay(200); if (Feeds.uploads.items.none { it.key == id }) { Feeds.startKey = null; toast(c, "That short isn’t available anymore") } }
            return@LaunchedEffect }
        runCatching {
            val j = Api.get("/api/src-shorts?v=" + URLEncoder.encode(v, "UTF-8"))
            SoShortTopics.learn(j); ShDir.learnCreators(j)
            Clip.srcPage(j).firstOrNull()?.let { s -> Feeds.who = null; Feeds.tab = ShortsTab.ForYou; Feeds.forYou.items.removeAll { it.key == s.key }; Feeds.forYou.items.add(0, s); Feeds.startKey = s.key }
        }
    }

    val player = remember { Media.player(c).apply { repeatMode = if (ShPrefs.auto) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE; volume = if (Prefs.muted) 0f else 1f } }
    var paused by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var firstFrame by remember { mutableStateOf(false) }
    var videoShape by remember { mutableFloatStateOf(0f) }
    var failedKey by remember { mutableStateOf<String?>(null) }
    var flashIcon by remember { mutableStateOf<ImageVector?>(null) }
    var flashAt by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    val bursts = remember { mutableStateListOf<ShBurstAt>() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) { player.pause(); ShKeys.flush() }
            if (e == Lifecycle.Event.ON_RESUME && !paused) player.play()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); ShKeys.flush(); player.release() }
    }
    LaunchedEffect(Prefs.muted) { player.volume = if (Prefs.muted) 0f else 1f }
    LaunchedEffect(ShPrefs.auto) { player.repeatMode = if (ShPrefs.auto) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE }
    LaunchedEffect(ShPrefs.speed) { player.setPlaybackParameters(PlaybackParameters(ShPrefs.speed)) }

    val pageCount = if (items.isEmpty()) 0 else items.size + 1   // + the end page
    val pager = rememberPagerState(pageCount = { pageCount })
    val itemsNow = rememberUpdatedState(items)
    val retried = remember { HashSet<String>() }
    // Speed report: time from a swipe / key settling on a video to its first picture on screen.
    val swipeAt = remember { longArrayOf(0L) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrame = true
                val t0 = swipeAt[0]
                if (t0 > 0) { android.util.Log.i("YidPerf", "Shorts video playing ${android.os.SystemClock.elapsedRealtime() - t0} ms after swipe"); swipeAt[0] = 0 }
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                // auto-scroll (site: menu → "Auto-scroll to the next video")
                if (state == Player.STATE_ENDED && ShPrefs.auto) scope.launch { val t = pager.currentPage + 1; if (t < pager.pageCount) pager.animateScrollToPage(t) }
            }
            override fun onVideoSizeChanged(v: VideoSize) { if (v.width > 0 && v.height > 0) videoShape = v.width.toFloat() / v.height }
            override fun onPlayerError(error: PlaybackException) {
                // site: an outside video that fails is tried once more through /api/src-media; an upload once more after 0.7 s
                val s = itemsNow.value.getOrNull(pager.settledPage) ?: return
                if (!retried.add(s.key)) { failedKey = s.key; buffering = false; return }
                scope.launch {
                    delay(700)
                    val u = if (s.fromSrc) Api.BASE + "/api/src-media?u=" + URLEncoder.encode(s.video, "UTF-8") else s.video
                    player.setMediaItem(MediaItem.fromUri(u)); player.prepare(); player.playWhenReady = !paused
                }
            }
        }
        player.addListener(l); onDispose { player.removeListener(l) }
    }

    var commentsFor by remember { mutableStateOf<Clip?>(null) }
    var optionsFor by remember { mutableStateOf<Clip?>(null) }
    var creatorsSheet by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf<Clip?>(null) }
    val focus = remember { FocusRequester() }
    var showHint by remember { mutableStateOf(Keys.keypad && !Prefs.keypadHintSeen) }
    var swipeHint by remember { mutableStateOf(false) }

    // watch time of the video on screen (site: track / settle → what this phone likes)
    val watch = remember { doubleArrayOf(0.0, 0.0) }   // seconds watched, loops
    LaunchedEffect(player) { while (isActive) { delay(500); if (player.isPlaying) { watch[0] += 0.5 * ShPrefs.speed; val d = player.duration; if (d > 0 && player.currentPosition < 600 && watch[0] > d / 1000.0) watch[1] = Math.floor(watch[0] / (d / 1000.0)) } } }
    fun settle(s: Clip?) {
        if (s == null || !s.fromSrc) return
        val w = watch[0]; val d = s.duration.toDouble().takeIf { it > 0 } ?: (player.duration / 1000.0)
        val sig = when { w < 2 && d > 4 -> -1.0; d > 0 && (w / d >= 0.9 || watch[1] > 0) -> 1.5 + Math.min(3.0, watch[1]); d > 0 -> Math.min(1.0, w / d); else -> 0.3 }
        if (w >= 1.5) ShRank.watchedOnce(s.key)
        ShRank.learn(s, sig)
        watch[0] = 0.0; watch[1] = 0.0
    }

    // Play the video on screen; the next one warms up only once this one plays (site: warmNext).
    LaunchedEffect(tab, who, topic) { if (pager.pageCount > 0 && Feeds.startKey == null) pager.scrollToPage(0) }
    LaunchedEffect(topic, items.size) { if (tab == ShortsTab.ForYou && topic != "All" && items.size < 4 && !feed.loading && !feed.ended) feed.loadMore() }
    LaunchedEffect(Feeds.startKey, items.size) {
        val k = Feeds.startKey ?: return@LaunchedEffect
        val i = items.indexOfFirst { it.key == k }
        if (i >= 0) { pager.scrollToPage(i); Feeds.startKey = null }
    }
    var lastSettled by remember { mutableStateOf<Clip?>(null) }
    LaunchedEffect(pager, tab, who) {
        snapshotFlow { pager.settledPage to itemsNow.value.getOrNull(pager.settledPage)?.key }.collect { (page, _) ->
            val items = itemsNow.value
            if (page > 0 && swipeHint) { swipeHint = false; ShPrefs.hintDone() }
            val s = items.getOrNull(page)
            if (s == null) { player.pause(); if (page >= items.size && items.isNotEmpty()) { if (who == null && (tab == ShortsTab.ForYou)) feed.loadMore() }; return@collect }
            if (lastSettled?.key != s.key) { settle(lastSettled); lastSettled = s }
            swipeAt[0] = android.os.SystemClock.elapsedRealtime()
            firstFrame = false; buffering = true; videoShape = 0f; paused = false; flashIcon = null; failedKey = null
            player.setMediaItem(MediaItem.fromUri(s.video)); player.prepare(); player.playWhenReady = true
            player.setPlaybackParameters(PlaybackParameters(ShPrefs.speed))
            if (s.fromSrc) ShKeys.shown(s)
            if ((tab == ShortsTab.ForYou || tab == ShortsTab.Uploads) && who == null && feed.items === items) feed.vet(c, page, 4)
            if (tab == ShortsTab.ForYou && who == null && page >= items.size - 8) feed.loadMore()
            Perf.shown("Shorts")
            Feeds.markSeen(s.video)
            // Like the site (shorts.js): an upload you see counts a view (not your own; signed out too), and goes into your History when signed in.
            if (!s.fromSrc && s.creatorId != Session.me?.id && Feeds.viewed.add(s.key)) launch {
                runCatching { Api.put("/api/shorts", JSONObject().put("id", s.key).put("view", true)) }.getOrNull()?.let { r -> if (r.has("views")) ShMeta.views[s.key] = r.optInt("views") }
                if (Session.me != null) runCatching { Api.post("/api/history", JSONObject().put("item_type", "short").put("item_id", s.key)) }
            }
            // the next video loads only once this one plays (it no longer competes with the one he is watching)
            launch {
                var waited = 0
                while (!firstFrame && waited < 8000) { delay(200); waited += 200 }
                delay(1200)
                if (pager.settledPage == page) Media.prefetch(c, itemsNow.value.drop(page + 1).take(1).map { it.video })
            }
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { focus.requestFocus() } }
    LaunchedEffect(items.isNotEmpty()) {
        if (items.isNotEmpty() && !Keys.keypad && !ShPrefs.swipeHint) { delay(2200); if (pager.settledPage == 0) { swipeHint = true; delay(4200); if (swipeHint) { swipeHint = false; ShPrefs.hintDone() } } }
    }

    fun like(s: Clip, on: Boolean = !s.liked) {
        val next = s.copy(liked = on, likes = maxOf(0, s.likes + if (on) 1 else -1))
        shUpdate(next)
        ShRank.learn(s, if (on) 3.0 else -3.0)
        scope.launch {
            try {
                if (s.fromSrc) {
                    if (Session.me == null) {
                        Feeds.forYou.saveLocalLike(s.key, on)
                        if (on && !ShPrefs.likeHint) { ShPrefs.likeHintDone(); toast(c, "Liked — sign in so your like counts for everyone") }
                    } else {
                        val r = runCatching { Api.post("/api/src-react", JSONObject().put("vid", s.key).put("like", on)) }
                        val j = r.getOrNull()
                        if (j != null && j.optBoolean("ok") && !j.optBoolean("queued")) shUpdate(next.copy(likes = j.optInt("likes", next.likes)))
                        else if (j?.optBoolean("queued") == true || (r.exceptionOrNull() as? Api.ApiError)?.let { it.code >= 500 } != false) ShLikeQueue.add(s.key, on)
                        else r.exceptionOrNull()?.let { throw it }
                    }
                } else {
                    if (Session.me == null) { toast(c, "Please sign in first."); shUpdate(s); return@launch }
                    val j = Api.put("/api/shorts", JSONObject().put("id", s.key).put("like", on))
                    if (j.has("likes")) shUpdate(next.copy(likes = j.optInt("likes")))
                }
            } catch (e: Exception) { toast(c, Api.friendly(e)); shUpdate(s) }
        }
    }
    fun toggleSave(s: Clip) {
        val was = Feeds.isSaved(s)
        if (was) { Feeds.saved.removeAll { it.key == s.key }; ShMeta.serverSaved.remove(s.key); toast(c, "Removed from saved") }
        else { Feeds.saved.add(0, s); while (Feeds.saved.size > 300) Feeds.saved.removeAt(Feeds.saved.size - 1); ShRank.learn(s, 3.0); toast(c, "Saved — find it under Saved") }
        Feeds.persist(c)
        // A member's upload is also saved on your account, exactly like the site's bookmark (shorts.js /saves), so it shows in Saved Items.
        if (!s.fromSrc && Session.me != null) scope.launch {
            runCatching {
                if (was) Api.delete("/api/saves?item_type=short&item_id=" + URLEncoder.encode(s.key, "UTF-8"))
                else { Api.post("/api/saves", JSONObject().put("item_type", "short").put("item_id", s.key)); ShMeta.serverSaved.add(s.key) }
            }.onFailure { toast(c, Api.friendly(it)) }
        }
    }
    fun follow(s: Clip) {
        val keys = ShDir.groupOf(s.creatorId).ifEmpty { listOf(s.creatorId) }
        ShDir.setFollow(c, keys, !ShDir.isFollowed(keys), s.creator, s.avatar, s.verified)
    }
    fun flash(i: ImageVector) { flashIcon = i; flashAt = System.currentTimeMillis() }
    fun go(d: Int) { val t = (pager.currentPage + d).coerceIn(0, maxOf(0, pager.pageCount - 1)); scope.launch { pager.animateScrollToPage(t) } }
    fun openCreator(s: Clip) {
        if (s.fromSrc) { ShRank.learn(s, 2.0); if (who == null || s.creatorId !in Feeds.whoKeys) Feeds.openWho(ShDir.groupOf(s.creatorId), s.creator, "feed") }
        else Nav.open(SoCreatorPage(s.creatorId, s.creator, s.avatar, s.verified, false))
    }
    fun notInterested(s: Clip) {
        Feeds.markSeen(s.video); ShRank.learn(s, -6.0)
        Feeds.forYou.items.removeAll { it.key == s.key }; Feeds.following.items.removeAll { it.key == s.key }
        toast(c, "Got it — you’ll see fewer videos like this")
    }
    fun hideCreator(s: Clip) {
        val keys = ShDir.groupOf(s.creatorId)
        if (ShDir.isFollowed(keys)) ShDir.setFollow(c, keys, false, quiet = true)
        Feeds.hidden.addAll(keys); Feeds.persist(c); ShRank.learn(s, -10.0)
        listOf(Feeds.forYou.items, Feeds.following.items).forEach { l -> l.removeAll { it.creatorId in keys && it.key != s.key } }
        Feeds.forYou.items.removeAll { it.key == s.key }; Feeds.following.items.removeAll { it.key == s.key }
        toast(c, "You won’t see ${s.creator.ifBlank { "this creator" }} anymore")
    }
    // one tap acts at once (site: no 0.26 s wait); a second tap within 0.3 s undoes it and likes instead
    val tapState = remember { longArrayOf(0L) }
    var undo by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun onTap(s: Clip, o: Offset) {
        val now = android.os.SystemClock.uptimeMillis()
        if (com.yidplus.app.data.Fixture.on) android.util.Log.i("YidSh", "tap dt=${now - tapState[0]} muted=${Prefs.muted} paused=$paused first=$firstFrame buf=$buffering")
        if (now - tapState[0] < 350) {
            tapState[0] = 0L; undo?.invoke(); undo = null
            bursts.add(ShBurstAt(now, o.x, o.y, (Math.random() * 30 - 15).toFloat()))
            if (!s.liked) like(s, true)
            return
        }
        tapState[0] = now; undo = null
        if (Prefs.muted) { Prefs.setMute(false); if (paused) { paused = false; player.play() }; flash(Sym.VolumeUp); return }
        if (paused) { paused = false; player.playWhenReady = true; flash(Sym.Pause); undo = { paused = true; player.playWhenReady = false }; return }
        // a video that is still loading is never paused by a tap (on a slow phone that was "tap again and again until it plays")
        if (!firstFrame || buffering) { player.playWhenReady = true; return }
        paused = true; player.playWhenReady = false; undo = { paused = false; player.playWhenReady = true }
    }
    fun togglePause() { if (paused) { paused = false; player.playWhenReady = true; flash(Sym.Pause) } else if (firstFrame && !buffering) { paused = true; player.playWhenReady = false } }
    val tabs = listOf(ShortsTab.Following, ShortsTab.ForYou, ShortsTab.Uploads).filter { !(Feeds.srcOff && (it == ShortsTab.ForYou || it == ShortsTab.Following)) }
    fun backToForYou() { Feeds.who = null; Feeds.pick = false; SoShortTopics.topic = "All"; Feeds.tab = ShortsTab.ForYou }

    if (isBlocked) { ShBlocked(blocked); return }

    Box(Modifier.fillMaxSize().background(Color.Black).onKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
        if (showHint) { showHint = false; Prefs.hintSeen(); return@onKeyEvent true }
        val cur = items.getOrNull(pager.currentPage)
        when (e.key) {
            Key.DirectionUp -> { go(-1); true }
            Key.DirectionDown -> { go(1); true }
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Five -> { if (cur != null) togglePause() else return@onKeyEvent false; true }
            Key.DirectionLeft -> { player.seekTo(maxOf(0, player.currentPosition - 5000)); true }
            Key.DirectionRight -> { player.seekTo(player.currentPosition + 5000); true }
            Key.One -> { cur?.let { if (!it.liked) bursts.add(ShBurstAt(System.currentTimeMillis(), 0f, 0f, 0f)); like(it) }; true }
            Key.Two -> { Prefs.setMute(!Prefs.muted); true }
            Key.Three -> { commentsFor = cur; true }
            Key.Four -> { cur?.let { toggleSave(it) }; true }
            Key.Six -> { cur?.let { shShare(c, it) }; true }
            Key.Seven -> { Nav.open(SoUploadShortPage()); true }
            Key.Eight -> { cur?.let { openCreator(it) }; true }
            Key.Nine -> { Nav.open(SoSearchPage(4)); true }
            Key.Zero -> { Feeds.who = null; Feeds.tab = ShortsTab.Following; Feeds.pick = true; true }
            else -> when (e.nativeKeyEvent.keyCode) {
                android.view.KeyEvent.KEYCODE_STAR -> { Feeds.who = null; Feeds.pick = false; Feeds.tab = tabs[(tabs.indexOf(if (tab == ShortsTab.Saved) Feeds.savedFrom else tab) + 1) % tabs.size]; true }
                android.view.KeyEvent.KEYCODE_POUND -> { optionsFor = cur; true }
                else -> false
            }
        }
    }.focusRequester(focus).focusable()) {
        val top = if (smallScreen) 78.dp else 92.dp
        val nm = Feeds.whoName.ifBlank { "this creator" }
        when {
            pickList -> Box(Modifier.fillMaxSize().padding(top = top)) {
                ShCreatorList("Follow creators to see them here", inline = true,
                    onWho = { ks, n -> Feeds.openWho(ks, n, "list") },
                    onSeeFollowed = { Feeds.pick = false; Feeds.following.clear() },
                    onBackToForYou = { backToForYou() })
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize().padding(top = top)) {
                when {
                    who != null -> if (Feeds.whoList.loading || !Feeds.whoList.done) ShEndPage(null, "", spinner = true)
                        else ShEndPage(Sym.VideoLibrary, "No videos from $nm yet", buttons = listOf("‹ Back" to { if (Feeds.whoBack() == "sheet") creatorsSheet = true }))
                    tab == ShortsTab.Saved -> ShEndPage(Sym.BookmarkBorder, "No saved videos yet", "Tap the bookmark on any video to keep it here. Saved videos stay on this phone.",
                        buttons = listOf("Back to For You" to { backToForYou() }))
                    tab == ShortsTab.Following -> if (Feeds.following.loading) ShEndPage(null, "", spinner = true)
                        else ShEndPage(Sym.Check, "No new videos from the creators you follow", buttons = listOf("+ Follow more creators" to { creatorsSheet = true }, "Back to For You" to { backToForYou() }))
                    tab == ShortsTab.Uploads -> when {
                        feed.error != null -> ShEndPage(Sym.ErrorOutline, "Could not load shorts", feed.error, buttons = listOf("Try Again" to { scope.launch { feed.reset(); feed.loadMore() } }))
                        feed.loading || !feed.ended -> ShEndPage(null, "", spinner = true)
                        else -> ShEndPage(Sym.Movie, "No shorts yet", "Tap + to upload the first one!", buttons = listOf("Upload a Short" to { Nav.open(SoUploadShortPage()) }))
                    }
                    feed.error != null -> ShEndPage(Sym.ErrorOutline, "Couldn’t load videos", if (feed.offline) "No connection" else null, buttons = listOf("Try again" to { scope.launch { feed.retry(); feed.loadMore() } }))
                    topic != "All" && !feed.loading -> ShEndPage(Sym.Tag, "No videos in this topic yet", buttons = listOf("Back to For You" to { SoShortTopics.topic = "All" }))
                    feed.loading || (!feed.ended && feed.emptyRun < 12) -> ShSkeletonSlide()
                    else -> ShEndPage(Sym.Check, "No videos here right now", buttons = listOf("Back to For You" to { backToForYou(); Feeds.forYou.reset(); Feeds.refresh++ }))
                }
            }
            // A quick short flick moves to the next Short (like TikTok / WhatsApp), and it settles without a bounce.
            else -> PullToRefreshBox(isRefreshing = feed.loading && feed.items.isEmpty(), onRefresh = {
                    if (who != null) { Feeds.whoList.clear(); scope.launch { Feeds.whoList.loadWho(Feeds.whoKeys, Feeds.whoName) } }
                    else if (tab == ShortsTab.Following) { Feeds.following.clear(); scope.launch { Feeds.following.loadFollowing(Feeds.forYou.items) } }
                    else if (tab != ShortsTab.Saved) { feed.reset(); Feeds.refresh++ }
                }, modifier = Modifier.fillMaxSize()) {
                VerticalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, userScrollEnabled = !scrubbing, key = { items.getOrNull(it)?.key ?: "end" },
                    flingBehavior = PagerDefaults.flingBehavior(state = pager, snapPositionalThreshold = 0.2f,
                        snapAnimationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))) { page ->
                    val s = items.getOrNull(page)
                    if (s == null) Box(Modifier.fillMaxSize().padding(top = top)) {
                        when {
                            who != null -> if (Feeds.whoList.loading) ShEndPage(null, "", spinner = true)
                                else ShEndPage(Sym.Check, "That’s all from $nm", buttons = listOf("‹ Back" to { if (Feeds.whoBack() == "sheet") creatorsSheet = true }))
                            tab == ShortsTab.Saved -> ShEndPage(Sym.Bookmark, "That’s everything you saved", buttons = listOf("Back to For You" to { backToForYou() }))
                            tab == ShortsTab.Following -> if (Feeds.following.loading) ShEndPage(null, "", spinner = true)
                                else ShEndPage(Sym.Check, "You’re all caught up", buttons = listOf("+ Follow more creators" to { creatorsSheet = true }, "Back to For You" to { backToForYou() }))
                            feed.loading -> ShEndPage(null, "", spinner = true)
                            feed.error != null -> ShEndPage(Sym.ErrorOutline, "Couldn’t load more", buttons = listOf("Try again" to { scope.launch { feed.retry(); feed.loadMore() } }))
                            feed.ended || feed.emptyRun >= 12 || tab == ShortsTab.Uploads -> ShEndPage(Sym.Check, "You’re all caught up",
                                buttons = listOf("Back to newest" to { feed.reset(); SoShortTopics.topic = "All"; Feeds.refresh++ }))
                            else -> ShEndPage(Sym.ExpandMore, "", buttons = listOf("Load more" to { scope.launch { feed.retry(); feed.loadMore() } }))
                        }
                    } else {
                        val current = page == pager.settledPage
                        ShortPage(s, current = current, player = player, paused = paused && current, loading = current && (!firstFrame || buffering) && !paused && failedKey != s.key,
                            failed = current && failedKey == s.key, onSkip = { go(1) }, onRetry = { failedKey = null; retried.remove(s.key); firstFrame = false; buffering = true
                                player.setMediaItem(MediaItem.fromUri(s.video)); player.prepare(); player.playWhenReady = true },
                            videoShape = if (current) videoShape else 0f, flashIcon = if (current) flashIcon else null, flashAt = flashAt, topPad = top,
                            onTap = { o -> onTap(s, o) }, onLike = { like(s) }, onComments = { ShRank.learn(s, 1.0); commentsFor = s }, onSave = { toggleSave(s) },
                            onFollow = { follow(s) }, onShare = { ShRank.learn(s, 4.0); shShare(c, s) }, onMore = { optionsFor = s }, onCreator = { openCreator(s) },
                            onScrub = { scrubbing = it }, bursts = if (current) bursts else emptyList(), onBurstDone = { b -> bursts.remove(b) })
                    }
                }
            }
        }
        SoShortsTopBar(tabs, tab, who, topic, smallScreen, Modifier.align(Alignment.TopCenter),
            onTab = { t ->
                if (t == tab && t == ShortsTab.ForYou && who == null) { feed.reset(); SoShortTopics.topic = "All"; Feeds.refresh++; scope.launch { if (pager.pageCount > 0) pager.scrollToPage(0) } }
                Feeds.who = null; Feeds.pick = false; Feeds.tab = t
            },
            onSaved = { if (tab == ShortsTab.Saved) Feeds.tab = Feeds.savedFrom else { Feeds.savedFrom = if (tab == ShortsTab.Saved) Feeds.savedFrom else tab; Feeds.who = null; Feeds.tab = ShortsTab.Saved } },
            onFollowMore = { creatorsSheet = true },
            onWhoBack = { if (Feeds.whoBack() == "sheet") creatorsSheet = true },
            onWhoFollow = { ShDir.setFollow(c, Feeds.whoKeys, !ShDir.isFollowed(Feeds.whoKeys), Feeds.whoName) },
            onMore = if (smallScreen && !pickList && items.isNotEmpty()) ({ optionsFor = items.getOrNull(pager.settledPage) }) else null)
        if (ShPrefs.speed != 1f && items.isNotEmpty() && !pickList) Text(fmtSpeed(ShPrefs.speed), Modifier.align(Alignment.TopEnd).padding(top = top + 4.dp, end = 10.dp)
            .background(Color.Black.copy(alpha = .5f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (Prefs.muted && items.isNotEmpty() && !pickList && pager.settledPage < items.size) Row(Modifier.align(Alignment.TopCenter).padding(top = top + 6.dp)
            .background(Color.Black.copy(alpha = .55f), RoundedCornerShape(14.dp)).tap(RoundedCornerShape(14.dp)) { Prefs.setMute(false) }.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Sym.VolumeOff, null, tint = Color.White, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
            Text("Tap for sound", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        AnimatedVisibility(swipeHint, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.background(Color.Black.copy(alpha = .6f), RoundedCornerShape(18.dp)).padding(horizontal = 22.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Sym.ArrowUpward, null, tint = Color.White, modifier = Modifier.size(44.dp))
                Text("Swipe up for the next video", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            }
        }
        AnimatedVisibility(showHint, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.padding(24.dp).background(Color.Black.copy(alpha = .85f), RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text("Keypad keys", color = Gold, fontWeight = FontWeight.Bold)
                for (l in listOf("▲ ▼  previous / next video", "OK  pause / play", "◀ ▶  back / forward 5 s", "1 like · 2 sound · 3 comments", "4 save · 6 share · # options",
                        "7 upload · 8 creator · 9 search", "0 creators to follow · ✱ next tab", "Back  leave Shorts"))
                    Text(l, color = Color.White, style = MaterialTheme.typography.bodySmall)
                Text("Press any key", color = Color.White.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    commentsFor?.let { s -> SoShortCommentsSheet(s, onDismiss = { commentsFor = null; scope.launch { delay(100); runCatching { focus.requestFocus() } } },
        onCount = { n -> shUpdate(s.copy(comments = n)) }) }
    optionsFor?.let { s ->
        val canDelete = !s.fromSrc && (Session.me?.id == s.creatorId || SoUser.isAdmin)
        ShOptionsSheet(s, ShPrefs.speed, { ShPrefs.changeSpeed(it) }, onDismiss = { optionsFor = null; scope.launch { delay(100); runCatching { focus.requestFocus() } } },
            onNotInterested = { notInterested(s) }, onHide = { hideCreator(s) }, onCopy = { soCopy(c, s.shareUrl) },
            onSaved = { Feeds.savedFrom = if (tab == ShortsTab.Saved) Feeds.savedFrom else tab; Feeds.who = null; Feeds.tab = ShortsTab.Saved },
            onReset = { ShRank.reset(); ShKeys.resetAll(); Feeds.hidden.clear(); Feeds.persist(c); Feeds.forYou.reset(); Feeds.refresh++; toast(c, "For You starts fresh") },
            onDelete = if (canDelete) ({ askDelete = s }) else null, onCreator = { openCreator(s) })
    }
    askDelete?.let { s -> AlertDialog(onDismissRequest = { askDelete = null }, title = { Text("Delete this short?") },
        confirmButton = { TextButton({ askDelete = null; soDeleteUpload(c, s) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ askDelete = null }) { Text("Cancel") } }) }
    if (creatorsSheet) ShCreatorsSheet(onDismiss = {
        creatorsSheet = false
        if (ShCreatorsState.changed && tab == ShortsTab.Following) { ShCreatorsState.changed = false; Feeds.following.clear(); scope.launch { Feeds.following.loadFollowing(Feeds.forYou.items) } }
    }, onWho = { ks, n -> creatorsSheet = false; Feeds.openWho(ks, n, "sheet") })
}

private fun fmtSpeed(v: Float) = (if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()) + "×"

/** The first-load placeholder (site: skeleton): a soft glow, the rail's dots, the name and caption bars. */
@Composable
private fun ShSkeletonSlide() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(0.35f, 0.7f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    val g = Color.White.copy(alpha = 0.16f * a)
    Box(Modifier.fillMaxSize().background(Color(0xFF101010))) {
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            repeat(4) { Box(Modifier.size(38.dp).clip(CircleShape).background(g)) }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 34.dp, end = 80.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(30.dp).clip(CircleShape).background(g)); Spacer(Modifier.width(8.dp)); Box(Modifier.size(110.dp, 14.dp).clip(RoundedCornerShape(7.dp)).background(g)) }
            Spacer(Modifier.height(10.dp)); Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(g))
            Spacer(Modifier.height(6.dp)); Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(g))
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun ShortPage(s: Clip, current: Boolean, player: androidx.media3.exoplayer.ExoPlayer, paused: Boolean, loading: Boolean, failed: Boolean, onSkip: () -> Unit, onRetry: () -> Unit, videoShape: Float,
                      flashIcon: ImageVector?, flashAt: Long, topPad: androidx.compose.ui.unit.Dp,
                      onTap: (Offset) -> Unit, onLike: () -> Unit, onComments: () -> Unit, onSave: () -> Unit, onFollow: () -> Unit, onShare: () -> Unit,
                      onMore: () -> Unit, onCreator: () -> Unit, onScrub: (Boolean) -> Unit, bursts: List<ShBurstAt>, onBurstDone: (ShBurstAt) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        // Wide screens (tablets, landscape) letterbox the upright video in a 9:16 frame.
        val w = if (maxWidth >= 600.dp && maxWidth > maxHeight) maxHeight * 9f / 16f else maxWidth
        val small = maxHeight < 560.dp
        val screenShape = w.value / maxHeight.value
        // site: fit() — a video close to the screen's shape fills it (like Reels), any other keeps its whole picture
        val fill = videoShape > 0f && Math.abs(videoShape - screenShape) / screenShape < 0.28f
        val tapNow by rememberUpdatedState(onTap)
        val moreNow by rememberUpdatedState(onMore)
        // long-press = the ⋯ sheet (TikTok); it never delays a single tap
        Box(Modifier.width(w).fillMaxHeight().pointerInput(s.key) { detectTapGestures(onTap = { tapNow(it) }, onLongPress = { moreNow() }) }) {
            if (s.poster != null) {
                AsyncImage(s.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.35f))
                AsyncImage(s.poster, null, contentScale = if (fill) ContentScale.Crop else ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            if (current) AndroidView(factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.short_player, null) as PlayerView).also { it.player = player }
            }, update = { it.player = player; it.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT }, modifier = Modifier.fillMaxSize())
            if (paused) Icon(Sym.PlayArrowFill, "Paused", tint = Color.White.copy(alpha = .85f), modifier = Modifier.align(Alignment.Center).size(76.dp))
            else if (failed) Column(Modifier.align(Alignment.Center).padding(horizontal = 40.dp).background(Color.Black.copy(alpha = .55f), RoundedCornerShape(16.dp)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Sym.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(30.dp))
                Text("This video can’t play right now", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Try again", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.tap(RoundedCornerShape(16.dp), onClick = onRetry)
                        .border(1.dp, Color.White.copy(alpha = .6f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 7.dp))
                    Text("Next video", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.tap(RoundedCornerShape(16.dp), onClick = onSkip)
                        .background(Green, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 7.dp))
                }
            }
            else if (loading) CircularProgressIndicator(color = Color.White.copy(alpha = .8f), strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(34.dp))
            if (flashIcon != null) key(flashAt) { ShFlash(flashIcon, Modifier.align(Alignment.Center)) }
            for (b in bursts) key(b.id) {
                if (b.x == 0f && b.y == 0f) Box(Modifier.align(Alignment.Center)) { ShBurst(0f, 0f, b.rot) { onBurstDone(b) } }
                else ShBurst(b.x, b.y, b.rot) { onBurstDone(b) }
            }
            // Soft dark gradient under the caption and the rail so white text is always readable.
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(if (small) 170.dp else 200.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .62f)))))

            // Right side actions (site: the rail) — all of them, also at 480x640.
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = if (small) 8.dp else 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (small) 0.dp else 8.dp)) {
                val keys = if (s.fromSrc) listOf(s.creatorId) else emptyList()
                val following = s.fromSrc && (s.creatorId in Feeds.follows || ShDir.isFollowed(keys))
                // at 480x640 the creator sits in the caption row (Reels), so the rail is only like · comment · save · share
                if (!small) Box(Modifier.padding(bottom = 6.dp)) {
                    Box(Modifier.tap(CircleShape, onClick = onCreator).semantics { contentDescription = "Creator videos" }.border(1.5.dp, Color.White, CircleShape)) { ShAvatar(s.avatar, s.creator, if (small) 32.dp else 46.dp) }
                    if (s.fromSrc) Box(Modifier.align(Alignment.BottomCenter).offset(y = 8.dp).size(if (small) 18.dp else 20.dp).clip(CircleShape)
                        .background(if (following) Green else Color(0xFFFF3B5C)).tap(CircleShape, onClick = onFollow), contentAlignment = Alignment.Center) {
                        Icon(if (following) Sym.Check else Sym.Add, if (following) "Unfollow" else "Follow", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                Action(if (s.liked) Sym.FavoriteFill else Sym.Favorite, if (s.likes > 0) soNum(s.likes) else "Like", if (s.liked) Color(0xFFFF3B5C) else Color.White, small, "Like", pop = s.liked, onClick = onLike)
                Action(Sym.ChatBubbleFill, if (s.comments > 99 && !s.fromSrc) "99+" else soNum(s.comments), Color.White, small, "Comment", onClick = onComments)
                val isSaved = Feeds.isSaved(s)
                Action(if (isSaved) Sym.BookmarkFill else Sym.Bookmark, if (small) "" else "Save", if (isSaved) Gold else Color.White, small, if (isSaved) "Remove from saved" else "Save", onClick = onSave)
                Action(Sym.ShareFill, if (small) "" else "Share", Color.White, small, "Share", mirror = false, onClick = onShare)
                // at 480x640 sound is the tap / "Tap for sound" and ⋯ sits at the end of the chips row, so the rail never runs into the top bar
                if (!small) Action(if (Prefs.muted) Sym.VolumeOff else Sym.VolumeUp, "", Color.White, small, if (Prefs.muted) "Sound off" else "Sound on") { Prefs.setMute(!Prefs.muted) }
                if (!small) Action(Sym.MoreHoriz, "", Color.White, small, "More options", onClick = onMore)
                if (!s.fromSrc) ShMeta.views[s.key]?.let { v -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Sym.Visibility, "Views", tint = Color.White.copy(alpha = .85f), modifier = Modifier.size(13.dp))
                    Text(" " + soNum(v), color = Color.White.copy(alpha = .85f), fontSize = 11.sp) } }
            }
            // Caption + creator (site: .src-info): name · verified · time ago, the caption opens on a tap.
            Column(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 70.dp, bottom = if (small) 20.dp else 26.dp)) {
                Row(Modifier.tap(RoundedCornerShape(6.dp), onClick = onCreator), verticalAlignment = Alignment.CenterVertically) {
                    if (small) { Box(Modifier.semantics { contentDescription = "Creator videos" }.border(1.dp, Color.White, CircleShape)) { ShAvatar(s.avatar, s.creator, 28.dp) }; Spacer(Modifier.width(7.dp)) }
                    Text((if (s.fromSrc) "" else "@") + s.creator.ifBlank { "YID PLUS" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        style = TextStyle(textDirection = TextDirection.Content, shadow = Shadow(Color.Black.copy(alpha = .6f), blurRadius = 4f)))
                    if (s.verified) Icon(Sym.Verified, "Verified", tint = Color(0xFF1D9BF0), modifier = Modifier.padding(start = 3.dp).size(15.dp))
                    val a = ago(s.at)
                    if (a.isNotBlank()) Text(" · $a", color = Color.White.copy(alpha = .8f), fontSize = 13.sp, maxLines = 1)
                    if (small && s.fromSrc) {
                        val fol = s.creatorId in Feeds.follows || ShDir.isFollowed(listOf(s.creatorId))
                        Text(if (fol) "Following" else "Follow", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp).semantics { contentDescription = if (fol) "Unfollow" else "Follow" }.tap(RoundedCornerShape(8.dp), onClick = onFollow)
                                .border(1.dp, Color.White.copy(alpha = .8f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
                var open by remember { mutableStateOf(false) }
                if (s.caption.isNotBlank()) Text(s.caption, color = Color.White, maxLines = if (open) 8 else 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content, shadow = Shadow(Color.Black.copy(alpha = .6f), blurRadius = 4f)),
                    modifier = Modifier.padding(top = 3.dp).tap { open = !open })
            }
            if (current) ShScrub(player, Modifier.align(Alignment.BottomCenter), onScrub)
        }
    }
}

/** The quick pause/sound icon in the middle (site: flash), gone on its own. */
@Composable
private fun ShFlash(icon: ImageVector, modifier: Modifier) {
    var show by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(550); show = false }
    AnimatedVisibility(show, modifier, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.size(78.dp).clip(CircleShape).background(Color.Black.copy(alpha = .4f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(42.dp))
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, tint: Color, small: Boolean = false, desc: String = label, pop: Boolean = false, mirror: Boolean = false, onClick: () -> Unit) {
    val sc = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(pop) { if (pop) { sc.snapTo(0.7f); sc.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = 600f)) } }
    Column(Modifier.widthIn(min = if (small) 48.dp else 52.dp).tap(RoundedCornerShape(12.dp), onClick = onClick).padding(vertical = if (small) 2.dp else 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(if (small) 25.dp else 32.dp).scale(sc.value).then(if (mirror) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier))
        if (label.isNotBlank()) Text(label, color = Color.White, fontSize = if (small) 10.sp else 12.sp, lineHeight = if (small) 11.sp else 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .6f), blurRadius = 3f)))
    }
}

/**
 * The seek bar (site: scrubber "מיש"): thin while playing; while a finger drags it grows, shows a thumb and "0:12 / 0:45",
 * seeks live (every 90 ms), and the feed can't swipe to the next video until the finger lifts. Right-to-left aware.
 */
@Composable
private fun ShScrub(player: Player, modifier: Modifier, onScrub: (Boolean) -> Unit) {
    var pos by remember { mutableLongStateOf(0L) }
    var len by remember { mutableLongStateOf(0L) }
    var drag by remember { mutableStateOf<Float?>(null) }
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    LaunchedEffect(player) { while (isActive) { if (drag == null) { pos = player.currentPosition; len = player.duration.coerceAtLeast(0) }; delay(250) } }
    val frac = drag ?: if (len > 0) (pos.toFloat() / len).coerceIn(0f, 1f) else 0f
    val lastSeek = remember { longArrayOf(0L) }
    fun at(x: Float, w: Int): Float { val f = (x / w).coerceIn(0f, 1f); return if (rtl) 1f - f else f }
    Box(modifier.fillMaxWidth().height(26.dp)
        .pointerInput(Unit) {
            detectHorizontalDragGestures(onDragStart = { drag = at(it.x, size.width); onScrub(true) },
                onDragEnd = { drag?.let { f -> if (len > 0) player.seekTo((f * len).toLong()) }; drag = null; onScrub(false) },
                onDragCancel = { drag = null; onScrub(false) }) { ch, _ ->
                val f = at(ch.position.x, size.width); drag = f
                val now = System.currentTimeMillis(); if (len > 0 && now - lastSeek[0] > 90) { lastSeek[0] = now; player.seekTo((f * len).toLong()) }
            }
        }
        .pointerInput(Unit) { detectTapGestures { o -> if (len > 0) player.seekTo((at(o.x, size.width) * len).toLong()) } }) {
        val h = if (drag != null) 6.dp else 2.5.dp
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(h).background(Color.White.copy(alpha = .3f)))
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(frac).height(h).background(Color.White))
        if (drag != null) {
            BoxWithConstraints(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(16.dp)) {
                Box(Modifier.offset(x = maxWidth * frac - 8.dp, y = 2.dp).size(14.dp).clip(CircleShape).background(Color.White))
            }
            Text("${dur((frac * len / 1000).toInt()).ifBlank { "0:00" }} / ${dur((len / 1000).toInt())}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-36).dp).background(Color.Black.copy(alpha = .6f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
        }
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

/** " (4)": the keypad's number for a menu item, shown only on a keypad phone (a touch phone gets the plain label). */
internal fun keyHint(n: Int): String = if (Keys.keypad) " ($n)" else ""
