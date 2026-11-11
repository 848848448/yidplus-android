package com.yidplus.app.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.yidplus.app.MusicService
import com.yidplus.app.data.Net
import com.yidplus.app.data.Perf
import com.yidplus.app.data.Session
import com.yidplus.app.data.Store
import com.yidplus.app.data.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The app side of the music player (the player itself runs in MusicService). */
object Music {
    var controller: MediaController? = null
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var art by mutableStateOf<String?>(null)
    var playing by mutableStateOf(false)
    var hasItem by mutableStateOf(false)
    var currentId by mutableStateOf<String?>(null)
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
    }

    fun connect(ctx: Context, then: ((MediaController) -> Unit)? = null) {
        controller?.let { c -> then?.invoke(c); return }
        then?.let { pending.add(it) }
        if (connecting) return
        connecting = true
        val f = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, MusicService::class.java))).buildAsync()
        f.addListener({
            connecting = false
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c; c.addListener(listener); sync(c)
            pending.toList().forEach { it(c) }; pending.clear()
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun play(ctx: Context, list: List<Track>, index: Int) {
        connect(ctx) { c ->
            c.setMediaItems(list.map { t ->
                MediaItem.Builder().setMediaId(t.id).setUri(t.audio)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setArtworkUri((t.cover ?: t.ownerPhoto)?.let(Uri::parse)).build()).build()
            }, index, 0L)
            c.prepare(); c.play()
        }
    }
    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen() {
    val c = ctx()
    val res = remember(Session.me?.id) { Store.res("/api/music", Track::list) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(res, Net.online) { res.load(force = Net.online && res.offline) }
    val list = res.data
    LaunchedEffect(list) { if (list != null) Perf.shown("Music") }
    var q by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        TopBar("Music", sub = list?.let { "${it.size} songs" })
        PullToRefreshBox(isRefreshing = res.loading && list != null, onRefresh = { scope.launch { res.load(true) } }, modifier = Modifier.weight(1f)) {
            when {
                list == null && res.error != null -> Problem(res.error!!, res.offline, { scope.launch { res.load(true) } })
                list == null -> Loading()
                else -> {
                    val shown = if (q.isBlank()) list else list.filter { it.title.contains(q, true) || it.artist.contains(q, true) }
                    LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState()) {
                        if (list.size > 8) item {
                            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), singleLine = true,
                                placeholder = { Text("Search songs") }, textStyle = TextStyle(textDirection = TextDirection.Content))
                        }
                        if (shown.isEmpty()) item { Text("No songs", Modifier.padding(24.dp)) }
                        itemsIndexed(shown, key = { _, t -> t.id }) { i, t -> TrackRow(t, Music.currentId == t.id, if (i == 0) Modifier.firstFocus() else Modifier) { Music.play(c, shown, i) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(t: Track, now: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().background(if (now) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).tap(RoundedCornerShape(0.dp), onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(t.cover ?: t.ownerPhoto, 46)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(t.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
            Text(t.artist + if (t.plays > 0) "  ·  ${t.plays} plays" else "", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content))
        }
        Icon(if (now && Music.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Cover(url: String?, size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)).background(Green), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.MusicNote, null, tint = Gold, modifier = Modifier.size((size * 0.5f).dp))
        if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun MiniPlayer() {
    if (!Music.hasItem) return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).tap(RoundedCornerShape(0.dp)) { Nav.open(Screen.Player) }.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Cover(Music.art, 36)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(Music.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
                Text(Music.artist, maxLines = 1, style = MaterialTheme.typography.labelSmall)
            }
            IconBtn(if (Music.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (Music.playing) "Pause" else "Play", { Music.toggle() })
            IconBtn(Icons.Filled.SkipNext, "Next", { Music.controller?.seekToNextMediaItem() })
        }
    }
}

@Composable
fun PlayerScreen() {
    var pos by remember { mutableLongStateOf(0L) }
    var len by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (isActive) { Music.controller?.let { pos = it.currentPosition; len = it.duration.coerceAtLeast(0) }; delay(500) } }
    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Green, GreenDeep))).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) { IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back", { Nav.back() }, tint = Color.White) }
        Spacer(Modifier.weight(1f))
        Box(Modifier.widthIn(max = 320.dp).fillMaxWidth(0.72f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Green), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MusicNote, null, tint = Gold, modifier = Modifier.size(72.dp))
            if (Music.art != null) AsyncImage(Music.art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(14.dp))
        Text(Music.title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content), textAlign = TextAlign.Center, maxLines = 2)
        Text(Music.artist, color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { if (len > 0) pos.toFloat() / len else 0f }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = Gold, trackColor = Color.White.copy(alpha = .25f))
        Row(Modifier.fillMaxWidth()) {
            Text(dur((pos / 1000).toInt()).ifBlank { "0:00" }, color = Color.White, style = MaterialTheme.typography.labelSmall); Spacer(Modifier.weight(1f))
            Text(dur((len / 1000).toInt()), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Filled.SkipPrevious, "Previous", { Music.controller?.seekToPreviousMediaItem() }, tint = Color.White, size = 48.dp)
            IconBtn(Icons.Filled.Replay10, "Back 10 seconds", { Music.controller?.let { it.seekTo(maxOf(0, it.currentPosition - 10_000)) } }, tint = Color.White, size = 44.dp)
            Box(Modifier.size(64.dp).clip(CircleShape).background(Gold), contentAlignment = Alignment.Center) {
                IconBtn(if (Music.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (Music.playing) "Pause" else "Play", { Music.toggle() }, tint = GreenDeep, size = 64.dp, modifier = Modifier.firstFocus(always = true))
            }
            IconBtn(Icons.Filled.Forward10, "Forward 10 seconds", { Music.controller?.let { it.seekTo(it.currentPosition + 10_000) } }, tint = Color.White, size = 44.dp)
            IconBtn(Icons.Filled.SkipNext, "Next", { Music.controller?.seekToNextMediaItem() }, tint = Color.White, size = 48.dp)
        }
        Spacer(Modifier.weight(1f))
    }
}
