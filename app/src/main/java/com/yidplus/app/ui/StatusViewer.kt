package com.yidplus.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.yidplus.app.data.Api
import com.yidplus.app.data.Disk
import com.yidplus.app.data.Fixture
import com.yidplus.app.data.Media
import com.yidplus.app.data.Prefs
import com.yidplus.app.data.Session
import com.yidplus.app.data.SiteConfig
import com.yidplus.app.data.Store
import com.yidplus.app.data.objects
import com.yidplus.app.data.str
import com.yidplus.app.data.strOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject

/* ─────────────── Full-screen viewer ─────────────── */

/** "5m ago", "2h ago", "Nov 4" (never "Nov 4 ago"). */
fun stAgo(s: String?): String {
    val a = ago(s)
    return when { a.isBlank() || a == "now" -> "just now"; a.last() in "mhd" && a.dropLast(1).all { it.isDigit() } -> "$a ago"; else -> a }
}

/** Full-screen status viewer. [userId] = whose statuses to start with (null = the first one with something new). */
data class StatusViewerPage(val userId: String? = null) : PageScreen() {
    override val perfName: String get() = "StatusViewer"
    override val immersive: Boolean get() = true
    @Composable override fun Content() { StatusViewer(userId) }
}

@Composable
private fun StatusViewer(startUser: String?) {
    val c = ctx()
    val scope = rememberCoroutineScope()
    val meId = Session.me?.id
    var users by remember { mutableStateOf<List<StUser>?>(null) }
    LaunchedEffect(Unit) {
        val r = Statuses.res
        if (r.data == null) r.load()
        val all = Statuses.ordered(r.data.orEmpty())
        users = when {
            startUser != null && startUser == meId -> all.filter { it.userId == meId }
            startUser != null -> all.filter { it.userId != meId }.let { l -> val i = l.indexOfFirst { it.userId == startUser }; if (i > 0) l.drop(i) + l.take(i) else l }
            else -> all.filter { it.userId != meId }.ifEmpty { all }
        }
    }
    val list = users
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        when {
            list == null -> {}
            list.isEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No status updates right now", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Close", { Nav.back() }, Modifier.firstFocus(always = true))
            }
            else -> StatusPlayer(list, meId, onDeleted = { uid, sid ->
                val nl = list.map { u -> if (u.userId == uid) u.copy(slides = u.slides.filter { it.id != sid }) else u }.filter { it.slides.isNotEmpty() }
                users = nl
                Statuses.scope.launch { Statuses.res.load(true) }
                if (nl.isEmpty()) Nav.back()
            })
        }
    }
}

@Composable
private fun StatusPlayer(users: List<StUser>, meId: String?, onDeleted: (String, String) -> Unit) {
    val c = ctx()
    val scope = rememberCoroutineScope()
    var uiRaw by remember { mutableIntStateOf(0) }
    var siRaw by remember { mutableIntStateOf(0) }
    val ui = uiRaw.coerceIn(0, users.size - 1)
    val user = users[ui]
    val si = siRaw.coerceIn(0, user.slides.size - 1)
    val slide = user.slides[si]
    val isMine = user.userId == meId
    var progress by remember { mutableFloatStateOf(0f) }
    var held by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var ready by remember(ui, si) { mutableStateOf(slide.media == null) }
    var durMs by remember(ui, si) { mutableLongStateOf(5000L) }
    var reply by remember { mutableStateOf("") }
    var liked by remember(ui, si) { mutableStateOf(slide.reacted) }
    val paused = held || typing || menu || sheet
    val fr = remember { FocusRequester() }

    fun next() {
        if (si < user.slides.size - 1) { siRaw = si + 1; progress = 0f }
        else if (ui < users.size - 1) { uiRaw = ui + 1; siRaw = 0; progress = 0f }
        else Nav.back()
    }
    fun prev() {
        if (si > 0) { siRaw = si - 1; progress = 0f }
        else if (ui > 0) { uiRaw = ui - 1; siRaw = 0; progress = 0f }
        else progress = 0f
    }

    LaunchedEffect(ui, si) {
        progress = 0f
        if (!isMine && slide.id.isNotBlank() && !Statuses.seen(slide.id)) Statuses.sendView(slide.id)
        Statuses.markSeen(slide.id)
        runCatching { fr.requestFocus() }
    }
    LaunchedEffect(ui, si, paused, ready, durMs) {
        if (paused || !ready) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (progress < 1f) {
            val now = withFrameMillis { it }
            progress = (progress + (now - last) / durMs.toFloat()).coerceAtMost(1f)
            last = now
        }
        next()
    }
    BackHandler(enabled = sheet || menu) { sheet = false; menu = false }

    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()
        .onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || typing || sheet) false
            else when (e.key) {
                Key.DirectionLeft -> { prev(); true }
                Key.DirectionRight -> { next(); true }
                else -> false
            }
        }.focusRequester(fr).focusable()) {
        // The slide itself
        Box(Modifier.fillMaxSize()
            .pointerInput(ui, si) {
                detectTapGestures(onPress = { held = true; tryAwaitRelease(); held = false },
                    onTap = { o -> if (o.x < size.width / 3f) prev() else next() })
            }
            .pointerInput(ui, si) {
                var dy = 0f
                detectVerticalDragGestures(onDragStart = { dy = 0f }, onDragEnd = { if (dy > with(density) { 120.dp.toPx() }) Nav.back() }) { _, d -> dy += d }
            }, contentAlignment = Alignment.Center) {
            when {
                slide.media != null && slide.video -> StVideo(slide.media, paused, onDur = { durMs = it.coerceIn(1500L, 60_000L) }, onReady = { ready = true })
                slide.media != null -> AsyncImage(slide.media, slide.text.ifBlank { "Status photo" }, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                    onSuccess = { ready = true }, onError = { ready = true })
                else -> Box(Modifier.fillMaxSize().background(Statuses.brush(slide.bg)).padding(28.dp), contentAlignment = Alignment.Center) {
                    Text(slide.text, color = Statuses.textColor(slide.color), fontSize = if (slide.text.length > 120) 20.sp else 26.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, lineHeight = if (slide.text.length > 120) 28.sp else 36.sp, style = TextStyle(textDirection = TextDirection.Content))
                }
            }
            if (slide.media != null && slide.text.isNotBlank()) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))).padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 84.dp)) {
                Text(slide.text, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), style = TextStyle(textDirection = TextDirection.Content))
            }
        }
        // Top: progress bars + who + actions
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
            .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                user.slides.indices.forEach { j ->
                    val f = when { j < si -> 1f; j == si -> progress; else -> 0f }
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = .35f))) {
                        Box(Modifier.fillMaxWidth(f).height(3.dp).background(Color.White))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Close", { Nav.back() }, tint = Color.White)
                Avatar(user.photo, user.nick, 38.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(if (isMine) "My status" else user.nick, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                    Text(stAgo(slide.createdAt),
                        color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                }
                if (paused && held) Text("Paused", color = Color.White.copy(alpha = .8f), fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
                if (isMine) {
                    IconBtn(Icons.Filled.Add, "Add status", { Nav.open(PostStatusPage()) }, tint = Color.White)
                    Box {
                        IconBtn(Icons.Filled.MoreVert, "More", { menu = true }, tint = Color.White)
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 16.sp) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menu = false
                                    scope.launch {
                                        try { Statuses.delete(slide.id); toast(c, "Status deleted"); onDeleted(user.userId, slide.id) }
                                        catch (e: Exception) { toast(c, Api.friendly(e)) }
                                    }
                                })
                        }
                    }
                }
            }
        }
        // Bottom: views for mine, reply + heart for others
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 10.dp, vertical = 10.dp)) {
            if (isMine) {
                Row(Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).background(Color(0x66000000)).tap(RoundedCornerShape(20.dp)) { sheet = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${slide.views} views", color = Color.White, fontSize = 15.sp)
                }
            } else if (Session.me != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(reply, { reply = it }, Modifier.weight(1f).onFocusChanged { typing = it.isFocused },
                        placeholder = { Text("Reply…", color = Color.White.copy(alpha = .75f)) }, singleLine = true, shape = RoundedCornerShape(24.dp),
                        textStyle = TextStyle(fontSize = 16.sp, color = Color.White, textDirection = TextDirection.Content),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color(0x80000000), unfocusedContainerColor = Color(0x59000000),
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Color.White,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            val t = reply.trim(); if (t.isNotEmpty()) { reply = ""; scope.launch {
                                try { Statuses.replyTo(user.userId, "↩️ $t"); toast(c, "Reply sent") } catch (e: Exception) { toast(c, Api.friendly(e)) } } }
                        }))
                    Spacer(Modifier.width(6.dp))
                    if (reply.isNotBlank()) Box(Modifier.size(48.dp).clip(CircleShape).background(Green).tap(CircleShape) {
                        val t = reply.trim(); reply = ""
                        scope.launch { try { Statuses.replyTo(user.userId, "↩️ $t"); toast(c, "Reply sent") } catch (e: Exception) { toast(c, Api.friendly(e)) } }
                    }, contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.Send, "Send reply", tint = Color.White, modifier = Modifier.size(22.dp)) }
                    else IconBtn(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Like status", {
                        liked = !liked
                        if (liked) scope.launch { try { Statuses.replyTo(user.userId, "❤️ (reacted to your status)"); toast(c, "❤️ Reacted!") } catch (e: Exception) { toast(c, Api.friendly(e)) } }
                    }, tint = if (liked) Color(0xFFFF4D67) else Color.White, size = 48.dp)
                }
            }
        }
        if (sheet) StViewersSheet(slide) { sheet = false }
    }
}

@Composable
private fun StViewersSheet(slide: StSlide, onClose: () -> Unit) {
    var viewers by remember(slide.id) { mutableStateOf<List<JSONObject>?>(null) }
    var failed by remember(slide.id) { mutableStateOf(false) }
    LaunchedEffect(slide.id) { try { viewers = Statuses.viewers(slide.id) } catch (_: Exception) { failed = true } }
    Box(Modifier.fillMaxSize().background(Color(0x66000000)).pointerInput(Unit) { detectTapGestures { onClose() } }) {
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(min = 160.dp, max = 420.dp).pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(viewers?.let { "Viewed by ${it.size}" } ?: "Viewed by", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconBtn(Icons.Filled.Close, "Close", onClose, modifier = Modifier.firstFocus(always = true))
                }
                val v = viewers
                when {
                    failed -> Text("Could not load viewers", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    v == null -> SkeletonRows(Modifier.fillMaxWidth(), rows = 2)
                    v.isEmpty() -> Text("No views yet", Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    else -> LazyColumn { items(v) { u ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(Api.abs(u.strOrNull("photo_url")), u.str("nickname").ifBlank { "User" }, 44.dp)
                            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                Text(u.str("nickname").ifBlank { "User" } + if (u.optBoolean("verified")) " ✔️" else "", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                                Text(ago(u.strOrNull("viewed_at")), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun StVideo(url: String, paused: Boolean, onDur: (Long) -> Unit, onReady: () -> Unit) {
    val c = ctx()
    val player = remember(url) { Media.player(c).apply { setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = true; volume = if (Prefs.muted) 0f else 1f } }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) { if (player.duration > 0) onDur(player.duration); onReady() } }
            override fun onPlayerError(error: PlaybackException) { onReady() }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    LaunchedEffect(paused) { player.playWhenReady = !paused }
    AndroidView(factory = { ctx -> PlayerView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        this.player = player; useController = false; setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
    } }, modifier = Modifier.fillMaxSize())
}

