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

/* ───────────── Helper SM (2.1): the music library, like the site's yidplus-music.html + js/music.js ───────────── */

data class SmTrack(val id: String, val title: String, val artist: String, val type: String, val albumId: String?, val albumName: String?,
                   val audio: String?, val video: String?, val cover: String?, val durationSec: Int, val plays: Int, val trending: Boolean,
                   val ownerId: String, val ownerNick: String, val ownerPhoto: String?, val liked: Boolean, val saved: Boolean, val createdAt: String?) {
    val art: String? get() = cover ?: ownerPhoto
    val isMine: Boolean get() = Session.me?.id?.let { it == ownerId } == true
}

data class SmAlbum(val id: String, val name: String, val artist: String, val cover: String?, val tracks: List<SmTrack>)

object SmMusic {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    fun parse(j: JSONObject): List<SmTrack> = j.optJSONArray("tracks").objects().map { t ->
        SmTrack(t.str("id"), t.str("title").ifBlank { "Untitled" }, t.str("artist").ifBlank { t.str("owner_nick").ifBlank { "Unknown" } }, t.str("type").ifBlank { "single" },
            t.strOrNull("album_id"), t.strOrNull("album_name"), Api.abs(t.strOrNull("audio_url")), Api.abs(t.strOrNull("video_url")), Api.abs(t.strOrNull("cover_url")),
            t.optInt("duration_sec"), t.optInt("plays"), t.optInt("trending") == 1 || t.optBoolean("trending"), t.str("owner_id"), t.str("owner_nick"),
            Api.abs(t.strOrNull("owner_photo")), t.optBoolean("liked"), t.optBoolean("saved"), t.strOrNull("created_at"))
    }.filter { it.id.isNotBlank() && (it.audio != null || it.video != null) }
    fun res() = Store.res("/api/music", ::parse)

    /** Optimistic like/save state on top of the last list (the site flips the heart at once too). */
    val likeOver = mutableStateMapOf<String, Boolean>()
    val saveOver = mutableStateMapOf<String, Boolean>()
    val deleted = mutableStateListOf<String>()
    fun liked(t: SmTrack) = likeOver[t.id] ?: t.liked
    fun saved(t: SmTrack) = saveOver[t.id] ?: t.saved
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // Same calls as js/music.js
    fun toggleLike(c: Context, t: SmTrack) {
        if (Session.me == null) { toast(c, "Please sign in first."); return }
        val on = !liked(t); likeOver[t.id] = on
        scope.launch { try { Api.put("/api/music", JSONObject().put("id", t.id).put("like", on)) } catch (e: Exception) { likeOver[t.id] = !on; toast(c, Api.friendly(e)) } }
    }
    fun toggleSave(c: Context, t: SmTrack) {
        if (Session.me == null) { toast(c, "Please sign in first."); return }
        val on = !saved(t); saveOver[t.id] = on
        scope.launch {
            try {
                if (on) Api.post("/api/saves", JSONObject().put("item_type", "music").put("item_id", t.id))
                else Api.delete("/api/saves?item_type=music&item_id=" + enc(t.id))
                toast(c, if (on) "Saved" else "Removed from saved")
            } catch (e: Exception) { saveOver[t.id] = !on; toast(c, Api.friendly(e)) }
        }
    }
    fun delete(c: Context, t: SmTrack) {
        scope.launch {
            try { Api.delete("/api/music?id=" + enc(t.id)); deleted.add(t.id); toast(c, "Track deleted"); res().load(true) }
            catch (e: Exception) { toast(c, Api.friendly(e)) }
        }
    }
    private val counted = HashSet<String>()
    /** When a song starts: the play count (PUT /api/music {id, play:true}) and history (POST /api/history), like playTrack(). */
    fun countPlay(id: String) {
        if (!counted.add(id + "@" + (System.currentTimeMillis() / 60_000))) return
        scope.launch {
            runCatching { Api.put("/api/music", JSONObject().put("id", id).put("play", true)) }
            if (Session.me != null) runCatching { Api.post("/api/history", JSONObject().put("item_type", "music").put("item_id", id)) }
        }
    }
    fun canDelete(t: SmTrack) = t.isMine || smIsAdmin()
    fun shareText(t: SmTrack) = "🎵 ${t.title} — ${t.artist}\nhttps://yidplus.com/music?track=${t.id}"
    fun albums(list: List<SmTrack>): List<SmAlbum> = list.filter { !it.albumName.isNullOrBlank() || it.type == "album_track" }
        .groupBy { it.albumId ?: it.albumName ?: it.id }
        .map { (id, ts) -> SmAlbum(id, ts.first().albumName ?: "Album", ts.first().artist, ts.firstNotNullOfOrNull { it.cover }, ts.sortedBy { it.createdAt ?: "" }) }
    fun singles(list: List<SmTrack>) = list.filter { it.albumName.isNullOrBlank() && it.type != "album_track" }
    fun videos(list: List<SmTrack>) = list.filter { it.video != null }
    var openVideo by mutableStateOf(false)
}

@Composable
fun SmCover(url: String?, size: Int, corner: Int = 8, video: Boolean = false) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(corner.dp)).background(Green), contentAlignment = Alignment.Center) {
        Icon(if (video) Sym.Movie else Sym.MusicNote, null, tint = Gold, modifier = Modifier.size((size * 0.5f).dp))
        if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** One song row: cover, title, artist · length · plays, heart, ⋮ (share, save, save MP3, delete). Tap = play this list from here. */
@Composable
fun SmTrackRow(t: SmTrack, list: List<SmTrack>, index: Int, modifier: Modifier = Modifier) {
    val c = ctx()
    val now = Music.currentId == t.id
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().heightIn(min = 68.dp).tap(RoundedCornerShape(0.dp)) { Music.playSm(c, list, index) }
        .background(if (now) MaterialTheme.colorScheme.primary.copy(alpha = .10f) else Color.Transparent)
        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box { SmCover(t.art, 48)
            if (now) Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                Icon(if (Music.playing) Icons.Rounded.GraphicEq else Sym.PauseFill, "Now playing", tint = Color.White, modifier = Modifier.size(24.dp)) } }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                color = if (now) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, style = TextStyle(textDirection = TextDirection.Content))
            val sub = listOfNotNull(t.artist, dur(t.durationSec).ifBlank { null }, if (t.plays > 0) "${t.plays} plays" else null, if (t.video != null) "video" else null).joinToString(" · ")
            Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, style = TextStyle(textDirection = TextDirection.Content))
        }
        val liked = SmMusic.liked(t)
        IconBtn(if (liked) Sym.FavoriteFill else Sym.FavoriteBorder, if (liked) "Unlike" else "Like", { SmMusic.toggleLike(c, t) },
            tint = if (liked) Color(0xFFE53950) else MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            IconBtn(Sym.MoreVert, "More for ${t.title}", { menu = true }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            DropdownMenu(menu, { menu = false }) { SmTrackMenu(t, { menu = false }, { confirm = true }) }
        }
    }
    if (confirm) SmDeleteDialog(t) { confirm = false }
}

@Composable
fun SmTrackMenu(t: SmTrack, close: () -> Unit, askDelete: () -> Unit) {
    val c = ctx()
    DropdownMenuItem(text = { Text("Play next") }, leadingIcon = { Icon(Sym.QueueMusic, null) }, onClick = { close(); Music.playNext(c, t) })
    DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Sym.Share, null) }, onClick = { close(); share(c, SmMusic.shareText(t)) })
    DropdownMenuItem(text = { Text(if (SmMusic.saved(t)) "Remove from saved" else "Save") }, leadingIcon = { Icon(if (SmMusic.saved(t)) Icons.Rounded.BookmarkRemove else Icons.Rounded.BookmarkAdd, null) },
        onClick = { close(); SmMusic.toggleSave(c, t) })
    t.audio?.let { a -> DropdownMenuItem(text = { Text("Save MP3 to phone") }, leadingIcon = { Icon(Sym.Download, null) },
        onClick = { close(); smSaveToPhone(c, a, "${t.title} - ${t.artist}." + (a.substringAfterLast('.', "mp3").substringBefore('?').take(4).ifBlank { "mp3" })) }) }
    if (t.video != null) DropdownMenuItem(text = { Text("Watch the video") }, leadingIcon = { Icon(Icons.Rounded.OndemandVideo, null) },
        onClick = { close(); Music.playSm(c, listOf(t), 0); SmMusic.openVideo = true; Nav.open(Screen.Player) })
    if (SmMusic.canDelete(t)) DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
        leadingIcon = { Icon(Sym.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { close(); askDelete() })
}

@Composable
fun SmDeleteDialog(t: SmTrack, close: () -> Unit) {
    val c = ctx()
    AlertDialog(onDismissRequest = close, title = { Text("Delete this track?") },
        text = { Text("\"${t.title}\" will be removed from YID PLUS for everyone.", style = TextStyle(textDirection = TextDirection.Content)) },
        confirmButton = { TextButton({ close(); SmMusic.delete(c, t) }, Modifier.firstFocus(always = true)) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(close) { Text("Cancel") } })
}

@Composable
fun SmAlbumCard(a: SmAlbum, modifier: Modifier = Modifier, width: Int = 140) {
    Column(modifier.width(width.dp).tap(RoundedCornerShape(12.dp)) { Nav.open(SmAlbumPage(a.id)) }.padding(4.dp)) {
        SmCover(a.cover, width - 8, 12)
        Text(a.name, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, style = TextStyle(textDirection = TextDirection.Content))
        Text("${a.artist} · ${a.tracks.size} ${if (a.tracks.size == 1) "song" else "songs"}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SmVideoCard(t: SmTrack, list: List<SmTrack>, index: Int, modifier: Modifier = Modifier, width: Int = 200) {
    val c = ctx()
    Column(modifier.width(width.dp).tap(RoundedCornerShape(12.dp)) { Music.playSm(c, list, index); SmMusic.openVideo = true; Nav.open(Screen.Player) }.padding(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(GreenDeep), contentAlignment = Alignment.Center) {
            if (t.art != null) AsyncImage(t.art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) { Icon(Sym.PlayArrowFill, "Play video", tint = Color.White, modifier = Modifier.size(30.dp)) }
        }
        Text(t.title, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, style = TextStyle(textDirection = TextDirection.Content))
        Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SmSectionHeader(title: String, onAll: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (onAll != null) Text("See all", Modifier.tap(RoundedCornerShape(12.dp), onClick = onAll).padding(horizontal = 10.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SmChip(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
        .tap(RoundedCornerShape(18.dp), onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp)) {
        Text(label, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

/** An album: cover, name, artist, Play all + Shuffle, its songs (openAlbum / playAlbum in js/music.js). */
data class SmAlbumPage(val albumId: String) : PageScreen() {
    override val perfName: String get() = "MusicAlbum"
    @Composable override fun Content() {
        val c = ctx()
        val res = remember { SmMusic.res() }
        LaunchedEffect(Unit) { res.load() }
        val a = remember(res.data) { SmMusic.albums(res.data.orEmpty().filter { it.id !in SmMusic.deleted }).firstOrNull { it.id == albumId } }
        Column(Modifier.fillMaxSize()) {
            TopBar(a?.name ?: "Album", onBack = { Nav.back() }, sub = a?.let { "${it.artist} · ${it.tracks.size} songs" })
            if (a == null) { if (res.data == null) Loading() else EmptyNote(Icons.Rounded.Album, "This album is not here any more"); return@Column }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        SmCover(a.cover, 112, 14)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(a.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                            Text(a.artist, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, style = TextStyle(textDirection = TextDirection.Content))
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryButton("Play all", { Music.playSm(c, a.tracks, 0) }, Modifier.firstFocus(always = true))
                                IconBtn(Sym.Shuffle, "Shuffle", { Music.playSm(c, a.tracks, (a.tracks.indices).random(), shuffle = true) }, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                itemsIndexed(a.tracks, key = { _, t -> t.id }) { i, t -> SmTrackRow(t, a.tracks, i) }
            }
        }
    }
}

/** Upload a song: title, artist, album, audio (required), music video and cover (optional) — the site's music-upload-modal. */
class SmMusicUploadPage : PageScreen() {
    override val perfName: String get() = "MusicUpload"
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        var title by rememberSaveable { mutableStateOf("") }
        var artist by rememberSaveable { mutableStateOf("") }
        var album by rememberSaveable { mutableStateOf("") }
        var audio by remember { mutableStateOf<Uri?>(null) }
        var video by remember { mutableStateOf<Uri?>(null) }
        var cover by remember { mutableStateOf<Uri?>(null) }
        val up = remember { SmUpload() }
        val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) audio = u }
        val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) video = u }
        val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) cover = u }
        LaunchedEffect(SoQaPick.uri) {
            SoQaPick.uri?.let { u -> val n = u.lastPathSegment.orEmpty(); if (n.endsWith(".png")) cover = u else if (audio == null) audio = u else video = u; SoQaPick.uri = null }
        }
        BackHandler(up.running) { up.cancel() }
        fun submit() {
            if (Session.me == null) { toast(c, "Please sign in first."); return }
            val t = title.trim()
            if (t.isEmpty()) { toast(c, "Title is required."); return }
            val a = audio ?: run { toast(c, "Please choose an audio file."); return }
            val art = artist.trim().ifBlank { Session.me?.nickname ?: "Unknown" }
            val alb = album.trim()
            val fields = buildList { add("title" to t); add("artist" to art); add("type" to if (alb.isNotEmpty()) "album_track" else "single"); if (alb.isNotEmpty()) add("album_name" to alb) }
            val files = buildList {
                add(SmUpload.Part("audio", a, smName(c, a, "audio")))
                video?.let { add(SmUpload.Part("video", it, smName(c, it, "video"))) }
                cover?.let { add(SmUpload.Part("cover", it, smName(c, it, "cover"))) }
            }
            scope.launch {
                try {
                    up.post(c, "/api/music", fields, files); toast(c, "Track uploaded!")
                    SmMusic.scope.launch { SmMusic.res().load(true) }; Nav.back()
                } catch (e: Exception) { toast(c, Api.friendly(e)) }
            }
        }
        Column(Modifier.fillMaxSize()) {
            TopBar("Upload music", onBack = { if (up.running) up.cancel() else Nav.back() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Track title") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(cursorColor = smCursor(MaterialTheme.colorScheme.primary)),
                    textStyle = TextStyle(textDirection = TextDirection.Content, fontSize = 16.sp), enabled = !up.running)
                OutlinedTextField(artist, { artist = it }, Modifier.fillMaxWidth(), label = { Text("Artist name") }, colors = OutlinedTextFieldDefaults.colors(cursorColor = smCursor(MaterialTheme.colorScheme.primary)), placeholder = { Text(Session.me?.nickname ?: "Artist") },
                    singleLine = true, textStyle = TextStyle(textDirection = TextDirection.Content, fontSize = 16.sp), enabled = !up.running)
                OutlinedTextField(album, { album = it }, Modifier.fillMaxWidth(), label = { Text("Album (optional)") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(cursorColor = smCursor(MaterialTheme.colorScheme.primary)),
                    textStyle = TextStyle(textDirection = TextDirection.Content, fontSize = 16.sp), enabled = !up.running)
                SmFileRow(Sym.AudioFile, "Audio file", "MP3, M4A, WAV, FLAC…", audio, required = true, { runCatching { pickAudio.launch("audio/*") }.onFailure { toast(c, "No file app found") } }) { audio = null }
                SmFileRow(Sym.VideoFile, "Music video (optional)", "MP4, MOV…", video, required = false, { runCatching { pickVideo.launch("video/*") }.onFailure { toast(c, "No file app found") } }) { video = null }
                SmFileRow(Sym.Image, "Cover picture (optional)", "JPG, PNG", cover, required = false, { runCatching { pickCover.launch("image/*") }.onFailure { toast(c, "No file app found") } }) { cover = null }
                if (up.running) SmProgress(up)
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(if (up.running) "Cancel upload" else "Cancel", { if (up.running) up.cancel() else Nav.back() }, Modifier.weight(1f))
                PrimaryButton("Upload", { submit() }, Modifier.weight(1f), enabled = !up.running)
            }
        }
    }
}

@Composable
fun SmFileRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, hint: String, uri: Uri?, required: Boolean, onPick: () -> Unit, onClear: () -> Unit) {
    val c = ctx()
    val name = remember(uri) { uri?.let { smName(c, it, "file") } }
    val size = remember(uri) { uri?.let { smSize(c, it) } ?: -1L }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
        .tap(RoundedCornerShape(12.dp), onClick = onPick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title + if (required) " *" else "", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(name?.let { n -> n + if (size > 0) " · " + smMb(size) else "" } ?: "Tap to choose · $hint", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (name != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (uri != null) IconBtn(Sym.Close, "Remove $title", onClear, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun smMb(b: Long): String = if (b >= 1024 * 1024) "%.1f MB".format(b / 1048576f) else "${(b / 1024).coerceAtLeast(1)} KB"

@Composable
fun SmProgress(up: SmUpload, dark: Boolean = false) {
    val col = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Uploading… ${(up.progress * 100).toInt()}%", Modifier.weight(1f), color = col, fontSize = 14.sp)
            TextButton({ up.cancel() }) { Text("Cancel", color = if (dark) Color.White else MaterialTheme.colorScheme.primary) }
        }
        LinearProgressIndicator(progress = { up.progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = if (dark) Gold else MaterialTheme.colorScheme.primary, trackColor = col.copy(alpha = .2f))
    }
}
