package com.yidplus.app.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.yidplus.app.MusicService
import com.yidplus.app.data.Api
import com.yidplus.app.data.Media
import com.yidplus.app.data.Net
import com.yidplus.app.data.Perf
import com.yidplus.app.data.Session
import com.yidplus.app.data.Store
import com.yidplus.app.data.Track
import com.yidplus.app.data.objects
import com.yidplus.app.data.str
import com.yidplus.app.data.strOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder

/** The app side of the music player (the player itself runs in MusicService). */
object Music {
    var controller: MediaController? = null
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var art by mutableStateOf<String?>(null)
    var playing by mutableStateOf(false)
    var hasItem by mutableStateOf(false)
    var currentId by mutableStateOf<String?>(null)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    /** The songs handed to the player, in play order of the list they came from (for Up next, like, video). */
    val queue = mutableStateListOf<SmTrack>()
    private var connecting = false
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { sync(player) }
    }

    private fun sync(p: Player) {
        val m = p.mediaMetadata
        title = m.title?.toString() ?: ""; artist = m.artist?.toString() ?: ""; art = m.artworkUri?.toString()
        playing = p.isPlaying || (p.playWhenReady && p.playbackState == Player.STATE_BUFFERING)
        hasItem = p.mediaItemCount > 0; currentId = p.currentMediaItem?.mediaId
        shuffle = p.shuffleModeEnabled; repeat = p.repeatMode
        val id = currentId
        if (id != null && p.isPlaying && queue.any { it.id == id }) SmMusic.countPlay(id)
    }

    fun connect(ctx: Context, then: ((MediaController) -> Unit)? = null) {
        controller?.let { c -> then?.invoke(c); return }
        then?.let { pending.add(it) }
        if (connecting) return
        connecting = true
        // The application context, never an Activity: Android unbinds an Activity's services when it is destroyed
        // (e.g. the update notification reopening the app), and the controller's own unbind then crashed the app
        // with "Service not registered".
        val app = ctx.applicationContext
        val f = MediaController.Builder(app, SessionToken(app, ComponentName(app, MusicService::class.java))).buildAsync()
        f.addListener({
            connecting = false
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c; c.addListener(listener); sync(c)
            pending.toList().forEach { it(c) }; pending.clear()
        }, ContextCompat.getMainExecutor(app))
    }

    private fun item(t: SmTrack) = MediaItem.Builder().setMediaId(t.id).setUri(t.audio ?: t.video)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.albumName).setArtworkUri(t.art?.let(Uri::parse)).build()).build()

    /** Older callers (saved items, history) still hand the shared Track model. */
    fun play(ctx: Context, list: List<Track>, index: Int) {
        playSm(ctx, list.map { t -> SmTrack(t.id, t.title, t.artist, "single", null, null, t.audio, null, t.cover, 0, t.plays, false, "", t.owner, t.ownerPhoto, t.liked, false, null) }, index)
    }

    fun playSm(ctx: Context, list: List<SmTrack>, index: Int, shuffle: Boolean = false) {
        val ok = list.filter { (it.audio ?: it.video) != null }
        if (ok.isEmpty()) { toast(ctx, "This song can't be played"); return }
        val start = ok.indexOfFirst { it.id == list.getOrNull(index)?.id }.coerceAtLeast(0)
        queue.clear(); queue.addAll(ok)
        connect(ctx) { c ->
            c.setMediaItems(ok.map(::item), start, 0L)
            c.shuffleModeEnabled = shuffle
            c.prepare(); c.play()
        }
    }
    fun playNext(ctx: Context, t: SmTrack) {
        val c = controller
        if (c == null || c.mediaItemCount == 0) { playSm(ctx, listOf(t), 0); return }
        queue.removeAll { it.id == t.id }; queue.add(t)
        c.addMediaItem(c.currentMediaItemIndex + 1, item(t)); toast(ctx, "Plays next")
    }
    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun prev() { controller?.let { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() } }
    fun seek(ms: Long) { controller?.let { it.seekTo(ms.coerceIn(0, (it.duration.takeIf { d -> d > 0 } ?: Long.MAX_VALUE))) } }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() { controller?.let { it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } } }
    fun current(): SmTrack? = queue.firstOrNull { it.id == currentId }
    /** What plays after the current song, in the player's own order (shuffle aware). */
    fun upNext(): List<SmTrack> {
        val c = controller ?: return emptyList()
        val out = ArrayList<SmTrack>(); var i = c.currentMediaItemIndex; var guard = 0
        while (guard++ < 50) {
            i = c.currentTimeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            if (i < 0 || i >= c.mediaItemCount) break
            val id = c.getMediaItemAt(i).mediaId
            queue.firstOrNull { it.id == id }?.let(out::add)
        }
        return out
    }
    fun jumpTo(t: SmTrack) { controller?.let { c -> (0 until c.mediaItemCount).firstOrNull { c.getMediaItemAt(it).mediaId == t.id }?.let { c.seekTo(it, 0); c.play() } } }
}

/** The Music tab: Search, Liked, Upload; All · Albums · Singles · Music videos · Liked, like yidplus-music.html. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen() {
    val c = ctx()
    SmQaReceiver()
    val res = remember(Session.me?.id) { SmMusic.res() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(res, Net.online) { res.load(force = Net.online && res.offline) }
    val all = res.data?.filter { it.id !in SmMusic.deleted }
    LaunchedEffect(all != null) { if (all != null) Perf.shown("Music") }
    var tab by rememberSaveable { mutableStateOf("all") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var q by rememberSaveable { mutableStateOf("") }
    var pulling by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    BackHandler(searching) { searching = false; q = "" }
    fun upload() { if (Session.me == null) toast(c, "Please sign in first.") else Nav.open(SmMusicUploadPage()) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Music", sub = all?.let { "${it.size} ${if (it.size == 1) "song" else "songs"}" }) {
            IconBtn(Sym.Search, "Search music", { searching = !searching; if (!searching) q = "" })
            IconBtn(Sym.FavoriteFill, "Liked songs", { tab = "liked"; searching = false })
            IconBtn(Sym.Upload, "Upload music", { upload() })
        }
        if (searching) {
            LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).focusRequester(searchFocus), singleLine = true,
                placeholder = { Text("Search songs, artists, albums…") }, leadingIcon = { Icon(Sym.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconBtn(Sym.Close, "Clear search", { q = "" }) },
                textStyle = TextStyle(textDirection = TextDirection.Content, fontSize = 16.sp), shape = RoundedCornerShape(24.dp))
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "All", "albums" to "Albums", "singles" to "Singles", "videos" to "Music videos", "liked" to "Liked").forEach { (k, l) -> SmChip(l, tab == k) { tab = k } }
            }
        }
        PullToRefreshBox(isRefreshing = pulling, onRefresh = { scope.launch { pulling = true; res.load(true); pulling = false } }, modifier = Modifier.weight(1f)) {
            when {
                all == null && res.error != null -> Problem(res.error!!, res.offline, { scope.launch { res.load(true) } })
                all == null -> SkeletonRows()
                all.isEmpty() -> EmptyNote(Sym.LibraryMusic, "No music yet", "Be the first to upload a track", action = "Upload music", onAction = { upload() })
                searching && q.isNotBlank() -> {
                    val hits = all.filter { it.title.contains(q, true) || it.artist.contains(q, true) || (it.albumName?.contains(q, true) == true) }
                    if (hits.isEmpty()) EmptyNote(Sym.SearchOff, "No songs match \"$q\"")
                    else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) { itemsIndexed(hits, key = { _, t -> t.id }) { i, t -> SmTrackRow(t, hits, i) } }
                }
                else -> MusicTabBody(tab, all) { tab = it }
            }
        }
    }
}

@Composable
private fun MusicTabBody(tab: String, all: List<SmTrack>, setTab: (String) -> Unit) {
    val albums = remember(all) { SmMusic.albums(all) }
    val singles = remember(all) { SmMusic.singles(all) }
    val videos = remember(all) { SmMusic.videos(all) }
    val liked = all.filter { SmMusic.liked(it) }
    LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState(), contentPadding = PaddingValues(bottom = 16.dp)) {
        when (tab) {
            "all" -> {
                item { SmSectionHeader("Albums", if (albums.isNotEmpty()) ({ setTab("albums") }) else null) }
                if (albums.isEmpty()) item { Text("No albums yet — uploads with an album name will appear here.", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else item { LazyRow(contentPadding = PaddingValues(start = 12.dp, end = 24.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(albums, key = { it.id }) { SmAlbumCard(it) } } }
                item { SmSectionHeader("Singles", if (singles.size > 6) ({ setTab("singles") }) else null) }
                if (singles.isEmpty()) item { Text("No singles yet", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                itemsIndexed(singles.take(6), key = { _, t -> "s" + t.id }) { i, t -> SmTrackRow(t, singles, i, if (i == 0) Modifier.firstFocus() else Modifier) }
                if (videos.isNotEmpty()) {
                    item { SmSectionHeader("Music videos", { setTab("videos") }) }
                    item { LazyRow(contentPadding = PaddingValues(start = 12.dp, end = 24.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { itemsIndexed(videos, key = { _, t -> t.id }) { i, t -> SmVideoCard(t, videos, i) } } }
                }
                if (liked.isNotEmpty()) {
                    item { SmSectionHeader("Liked songs", if (liked.size > 4) ({ setTab("liked") }) else null) }
                    itemsIndexed(liked.take(4), key = { _, t -> "l" + t.id }) { i, t -> SmTrackRow(t, liked, i) }
                }
            }
            "albums" -> if (albums.isEmpty()) item { EmptyNote(Icons.Rounded.Album, "No albums yet", "Uploads with an album name will appear here.", Modifier.fillParentMaxSize()) }
                else items(albums.chunked(2), key = { it.first().id }) { pair ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) { pair.forEach { SmAlbumCard(it, width = 150) }; if (pair.size == 1) Spacer(Modifier.width(150.dp)) }
                }
            "singles" -> if (singles.isEmpty()) item { EmptyNote(Sym.MusicNote, "No singles yet", modifier = Modifier.fillParentMaxSize()) }
                else itemsIndexed(singles, key = { _, t -> t.id }) { i, t -> SmTrackRow(t, singles, i, if (i == 0) Modifier.firstFocus() else Modifier) }
            "videos" -> if (videos.isEmpty()) item { EmptyNote(Icons.Rounded.OndemandVideo, "No music videos yet", "Songs uploaded with a video appear here.", Modifier.fillParentMaxSize()) }
                else items(videos.chunked(2), key = { it.first().id }) { pair ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        pair.forEach { t -> SmVideoCard(t, videos, videos.indexOf(t), width = 150) }; if (pair.size == 1) Spacer(Modifier.width(150.dp)) }
                }
            else -> if (liked.isEmpty()) item { EmptyNote(Sym.FavoriteBorder, "No liked songs yet", "Tap ♡ on a song to keep it here.", Modifier.fillParentMaxSize()) }
                else itemsIndexed(liked, key = { _, t -> t.id }) { i, t -> SmTrackRow(t, liked, i, if (i == 0) Modifier.firstFocus() else Modifier) }
        }
    }
}

@Composable
fun MiniPlayer() {
    if (!Music.hasItem) return
    var pos by remember { mutableLongStateOf(0L) }
    var len by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (isActive) { Music.controller?.let { pos = it.currentPosition; len = it.duration.coerceAtLeast(0) }; delay(1000) } }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Column {
            LinearProgressIndicator(progress = { if (len > 0) pos.toFloat() / len else 0f }, modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).tap(RoundedCornerShape(0.dp)) { Nav.open(Screen.Player) }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SmCover(Music.art, 38)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(Music.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, style = TextStyle(textDirection = TextDirection.Content))
                    Text(Music.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconBtn(Sym.SkipPrevious, "Previous track", { Music.prev() })
                IconBtn(if (Music.playing) Sym.PauseFill else Sym.PlayArrowFill, if (Music.playing) "Pause" else "Play", { Music.toggle() })
                IconBtn(Sym.SkipNext, "Next track", { Music.next() })
            }
        }
    }
}

/** Full player: Song / Video, cover, like + save, seek bar you can drag, shuffle · prev · play · next · repeat, −10 / +10, Up next. */
@Composable
fun PlayerScreen() {
    val c = ctx()
    var pos by remember { mutableLongStateOf(0L) }
    var len by remember { mutableLongStateOf(0L) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (isActive) { Music.controller?.let { pos = it.currentPosition; len = it.duration.coerceAtLeast(0) }; delay(400) } }
    val t = Music.current()
    var videoMode by remember { mutableStateOf(SmMusic.openVideo) }
    LaunchedEffect(Unit) { SmMusic.openVideo = false }
    LaunchedEffect(t?.id) { if (t?.video == null) videoMode = false }
    BackHandler(menu) { menu = false }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Green, GreenDeep))).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Sym.ArrowBack, "Back to music", { Nav.back() }, tint = Color.White, size = 48.dp)
            Text("Now playing", Modifier.weight(1f), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (t != null) IconBtn(Sym.Share, "Share track", { share(c, SmMusic.shareText(t)) }, tint = Color.White)
            if (t != null) Box {
                IconBtn(Sym.MoreVert, "More", { menu = true }, tint = Color.White)
                DropdownMenu(menu, { menu = false }) { SmTrackMenu(t, { menu = false }, { confirm = true }) }
            }
        }
        if (t?.video != null) Row(Modifier.padding(vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(Color(0x33000000)).padding(3.dp)) {
            listOf(false to "Song", true to "Video").forEach { (v, l) ->
                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(if (videoMode == v) Color.White else Color.Transparent)
                    .tap(RoundedCornerShape(16.dp)) { if (videoMode != v) { videoMode = v } }.padding(horizontal = 18.dp, vertical = 6.dp)) {
                    Text(l, color = if (videoMode == v) GreenDeep else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (videoMode && t?.video != null) SmMusicVideo(t.video)
        else Box(Modifier.widthIn(max = 260.dp).fillMaxWidth(0.62f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Green), contentAlignment = Alignment.Center) {
            Icon(Sym.MusicNote, null, tint = Gold, modifier = Modifier.size(72.dp))
            if (Music.art != null) AsyncImage(Music.art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (t != null) { val s = SmMusic.saved(t)
                IconBtn(if (s) Sym.BookmarkFill else Sym.BookmarkBorder, if (s) "Remove from saved" else "Save", { SmMusic.toggleSave(c, t) }, tint = if (s) Gold else Color.White) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Music.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content))
                Text(Music.artist, color = Color.White.copy(alpha = .78f), fontSize = 14.sp, maxLines = 1, style = TextStyle(textDirection = TextDirection.Content))
            }
            if (t != null) { val l = SmMusic.liked(t)
                IconBtn(if (l) Sym.FavoriteFill else Sym.FavoriteBorder, if (l) "Unlike" else "Like", { SmMusic.toggleLike(c, t) }, tint = if (l) Color(0xFFFF4D67) else Color.White) }
        }
        val frac = drag ?: if (len > 0) (pos.toFloat() / len).coerceIn(0f, 1f) else 0f
        Slider(value = frac, onValueChange = { drag = it }, onValueChangeFinished = { drag?.let { f -> Music.seek((f * len).toLong()) }; drag = null },
            enabled = len > 0, modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false else when (e.key) {
                    Key.DirectionLeft -> { Music.seek(pos - 10_000); true }
                    Key.DirectionRight -> { Music.seek(pos + 10_000); true }
                    else -> false
                }
            }, colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold, inactiveTrackColor = Color.White.copy(alpha = .3f)))
        Row(Modifier.fillMaxWidth()) {
            Text(dur(((drag?.let { it * len } ?: pos.toFloat()) / 1000).toInt()).ifBlank { "0:00" }, color = Color.White, fontSize = 12.sp); Spacer(Modifier.weight(1f))
            Text(dur((len / 1000).toInt()).ifBlank { "0:00" }, color = Color.White, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Sym.Shuffle, if (Music.shuffle) "Shuffle on" else "Shuffle off", { Music.toggleShuffle() }, tint = if (Music.shuffle) Gold else Color.White.copy(alpha = .7f))
            IconBtn(Sym.SkipPrevious, "Previous", { Music.prev() }, tint = Color.White, size = 48.dp)
            Box(Modifier.size(64.dp).clip(CircleShape).background(Gold), contentAlignment = Alignment.Center) {
                IconBtn(if (Music.playing) Sym.PauseFill else Sym.PlayArrowFill, if (Music.playing) "Pause" else "Play", { Music.toggle() }, tint = GreenDeep, size = 64.dp, modifier = Modifier.firstFocus(always = true))
            }
            IconBtn(Sym.SkipNext, "Next", { Music.next() }, tint = Color.White, size = 48.dp)
            IconBtn(if (Music.repeat == Player.REPEAT_MODE_ONE) Sym.RepeatOne else Sym.Repeat,
                when (Music.repeat) { Player.REPEAT_MODE_ONE -> "Repeat one"; Player.REPEAT_MODE_ALL -> "Repeat all"; else -> "Repeat off" }, { Music.cycleRepeat() },
                tint = if (Music.repeat != Player.REPEAT_MODE_OFF) Gold else Color.White.copy(alpha = .7f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            IconBtn(Sym.Replay10, "Back 10 seconds", { Music.seek(pos - 10_000) }, tint = Color.White)
            IconBtn(Sym.Forward10, "Forward 10 seconds", { Music.seek(pos + 10_000) }, tint = Color.White)
        }
        val next = remember(Music.currentId, Music.shuffle, Music.queue.size) { Music.upNext() }
        if (next.isNotEmpty()) {
            Text("Up next", Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            next.take(12).forEach { q ->
                Row(Modifier.fillMaxWidth().tap(RoundedCornerShape(10.dp)) { Music.jumpTo(q) }.padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SmCover(q.art, 40)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(q.title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                        Text(q.artist, color = Color.White.copy(alpha = .7f), fontSize = 12.sp, maxLines = 1)
                    }
                    Text(dur(q.durationSec), color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    if (confirm && t != null) SmDeleteDialog(t) { confirm = false }
}

/** Video mode: the song's music video plays here with sound; the audio pauses and picks up where the video stopped. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun SmMusicVideo(url: String) {
    val c = ctx()
    val player = remember(url) { Media.player(c).apply { setMediaItem(MediaItem.fromUri(url)); seekTo(Music.controller?.currentPosition ?: 0L); prepare(); playWhenReady = true } }
    DisposableEffect(player) {
        Music.controller?.pause()
        onDispose { val p = player.currentPosition; player.release(); Music.controller?.let { it.seekTo(p); it.play() } }
    }
    AndroidView(factory = { x -> PlayerView(x).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        this.player = player; useController = true; setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS); setShowNextButton(false); setShowPreviousButton(false)
    } }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.Black))
}
