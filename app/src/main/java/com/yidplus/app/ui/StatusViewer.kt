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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
    // WhatsApp's status times: "Just now", "12 minutes ago", "Today, 10:21", "Yesterday, 22:05", then the date.
    val t = smParseTime(s) ?: return ago(s).let { a -> if (a.isBlank() || a == "now") "Just now" else a }
    val now = System.currentTimeMillis()
    val mins = ((now - t) / 60_000L).toInt()
    if (mins < 1) return "Just now"
    if (mins < 60) return if (mins == 1) "1 minute ago" else "$mins minutes ago"
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = t }
    val today = java.util.Calendar.getInstance()
    val hm = android.text.format.DateFormat.format(if (android.text.format.DateFormat.is24HourFormat(com.yidplus.app.ui.SmPrefs.appCtx ?: return ago(s))) "HH:mm" else "h:mm a", t).toString()
    val sameDay = cal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) && cal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
    today.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val yest = cal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) && cal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
    return when { sameDay -> "Today, $hm"; yest -> "Yesterday, $hm"; else -> android.text.format.DateFormat.format("d MMM", t).toString() + ", $hm" }
}

/** The site's times: ISO "2026-11-11T18:10:00.000Z" or D1's "2026-11-11 18:10:00" (UTC). */
fun smParseTime(s: String?): Long? {
    if (s.isNullOrBlank()) return null
    val v = s.trim().replace(' ', 'T').let { if (it.endsWith("Z") || Regex("[+-]\\d\\d:?\\d\\d$").containsMatchIn(it)) it else it + "Z" }
    return runCatching { java.time.Instant.parse(v).toEpochMilli() }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(v).toInstant().toEpochMilli() }.getOrNull()
}

/** Full-screen status viewer. [userId] = whose statuses to start with (null = the first one with something new). */
data class StatusViewerPage(val userId: String? = null) : PageScreen() {
    override val perfName: String get() = "StatusViewer"
    override val immersive: Boolean get() = true
    @Composable override fun Content() { StatusViewer(userId) }
}

/** Highlights saved on this phone (the site's "🔖 Highlights", localStorage yp_highlights), played like statuses but read-only. */
data class SmHighlightViewerPage(val start: Int = 0) : PageScreen() {
    override val perfName: String get() = "Highlights"
    override val immersive: Boolean get() = true
    @Composable override fun Content() {
        val slides = SmPrefs.highlights.map { h -> StSlide(h.optString("id"), h.optString("type"), h.optString("text"), h.optString("bg"), h.optString("color"),
            h.optString("media_url").takeIf { it.isNotBlank() && it != "null" }, h.optBoolean("is_video"), h.optString("created_at").ifBlank { null }, 0, false) }
        val nick = SmPrefs.highlights.getOrNull(start)?.optString("nick").orEmpty()
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (slides.isEmpty()) { LaunchedEffect(Unit) { Nav.back() } }
            else StatusPlayer(listOf(StUser("highlights", "Highlights" + if (nick.isNotBlank()) " · @$nick" else "", null, slides)), null, onDeleted = { _, _ -> },
                readOnly = true, startSlide = start)
        }
    }
}

@Composable
private fun StatusViewer(startUser: String?) {
    val meId = Session.me?.id
    var users by remember { mutableStateOf<List<StUser>?>(null) }
    SmPrefs.init(ctx())
    LaunchedEffect(Unit) {
        val r = Statuses.res
        if (r.data == null) r.load()
        val all = Statuses.ordered(r.data.orEmpty())
        val loud = Statuses.audible(r.data.orEmpty())
        users = when {
            startUser != null && startUser == meId -> all.filter { it.userId == meId }
            startUser != null && SmPrefs.isMuted(startUser) -> all.filter { it.userId == startUser }
            startUser != null -> loud.filter { it.userId != meId }.let { l -> val i = l.indexOfFirst { it.userId == startUser }; if (i > 0) l.drop(i) + l.take(i) else l }
            else -> loud.filter { it.userId != meId }.ifEmpty { all }
        }
    }
    val list = users
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        when {
            list == null -> CircularProgressIndicator(color = Color.White.copy(alpha = .6f))
            list.isEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Rounded.HistoryToggleOff, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(10.dp))
                Text(if (startUser != null) "This status is no longer active" else "No status updates right now", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (meId != null) SecondaryButton("Add status", { Nav.back(); Nav.open(PostStatusPage()) })
                    PrimaryButton("Close", { Nav.back() }, Modifier.firstFocus(always = true))
                }
                LaunchedEffect(Unit) { Statuses.scope.launch { Statuses.res.load(true) } }
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

private val SmReactions = listOf("❤️", "😂", "😮", "😢", "🙏", "👏", "🔥")

@Composable
private fun StatusPlayer(users: List<StUser>, meId: String?, onDeleted: (String, String) -> Unit, readOnly: Boolean = false, startSlide: Int = 0) {
    val c = ctx()
    SmPrefs.init(c)
    val scope = rememberCoroutineScope()
    var uiRaw by remember { mutableIntStateOf(0) }
    var siRaw by remember { mutableIntStateOf(startSlide) }
    val ui = uiRaw.coerceIn(0, users.size - 1)
    val user = users[ui]
    val si = siRaw.coerceIn(0, user.slides.size - 1)
    val slide = user.slides[si]
    val isMine = !readOnly && user.userId == meId
    var progress by remember { mutableFloatStateOf(0f) }
    var held by remember { mutableStateOf(false) }
    var keyPaused by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var reactBar by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    var privacyInfo by remember { mutableStateOf(false) }
    var ready by remember(ui, si) { mutableStateOf(slide.media == null) }
    var durMs by remember(ui, si) { mutableLongStateOf(5000L) }
    var reply by remember { mutableStateOf("") }
    var liked by remember(ui, si) { mutableStateOf(slide.reacted) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val paused = held || typing || menu || sheet || reactBar || confirmDel || privacyInfo || keyPaused
    val fr = remember { FocusRequester() }

    fun next() {
        if (si < user.slides.size - 1) { siRaw = si + 1; progress = 0f }
        else if (ui < users.size - 1) { uiRaw = ui + 1; siRaw = 0; progress = 0f }
        else Nav.back()
    }
    // Like the site's svPrev: at a person's first slide, go back to the previous person's LAST slide.
    fun prev() {
        if (si > 0) { siRaw = si - 1; progress = 0f }
        else if (ui > 0) { uiRaw = ui - 1; siRaw = users[ui - 1].slides.size - 1; progress = 0f }
        else progress = 0f
    }
    fun react(e: String) {
        reactBar = false
        if (readOnly || Session.me == null) return
        if (e == "❤️") liked = true
        scope.launch { try { Statuses.replyTo(user.userId, "$e (reacted to your status)"); toast(c, "$e Reacted!") } catch (x: Exception) { toast(c, Api.friendly(x)) } }
    }
    fun sendReply() {
        val t = reply.trim(); if (t.isEmpty()) return; reply = ""
        scope.launch { try { Statuses.replyTo(user.userId, "↩️ $t"); toast(c, "Reply sent") } catch (e: Exception) { toast(c, Api.friendly(e)) } }
    }

    LaunchedEffect(ui, si) {
        progress = 0f
        if (!readOnly && !isMine && slide.id.isNotBlank() && !Statuses.seen(slide.id)) Statuses.sendView(slide.id)
        if (!readOnly) Statuses.markSeen(slide.id)
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
    BackHandler(enabled = sheet || menu || reactBar) { sheet = false; menu = false; reactBar = false }

    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(Modifier.fillMaxSize()
        .graphicsLayer(translationY = dragY.coerceAtLeast(0f), alpha = (1f - dragY.coerceAtLeast(0f) / 900f).coerceIn(0.35f, 1f))
        .onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && (e.key == Key.Menu || e.key == Key.Zero) && !menu && !typing) { reactBar = false; sheet = false; menu = true; true }
            else if (e.type != KeyEventType.KeyDown || typing || sheet || menu || reactBar || confirmDel || privacyInfo) false
            else when (e.key) {
                Key.DirectionLeft -> { if (rtl) next() else prev(); true }
                Key.DirectionRight -> { if (rtl) prev() else next(); true }
                Key.DirectionCenter, Key.Enter, Key.Five -> { keyPaused = !keyPaused; true }
                Key.Four -> { prev(); true }
                Key.Six -> { next(); true }
                Key.Zero, Key.Menu -> { menu = true; true }
                else -> false
            }
        }.focusRequester(fr).focusable()) {
        // The slide itself
        Box(Modifier.fillMaxSize()
            .pointerInput(ui, si) {
                detectTapGestures(onPress = { held = true; tryAwaitRelease(); held = false },
                    onTap = { o -> if (reactBar) reactBar = false else if (o.x < size.width * 0.3f) prev() else next() })
            }
            .pointerInput(ui, si) {
                // Drag down = close (the status follows the finger and fades, like the site); sideways = next/previous person;
                // up = viewers on my own status, the reaction row on someone else's.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var dx = 0f; var dy = 0f; var dir = 0 // 1 = vertical, 2 = horizontal
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        val d = ch.positionChange(); dx += d.x; dy += d.y
                        if (dir == 0 && (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop)) dir = if (kotlin.math.abs(dy) > kotlin.math.abs(dx)) 1 else 2
                        if (dir != 0) { ch.consume(); held = false }
                        if (dir == 1) dragY = dy
                    }
                    val far = with(density) { 80.dp.toPx() }
                    if (dir == 1 && dy > with(density) { 110.dp.toPx() }) Nav.back()
                    else if (dir == 1 && dy < -far) { if (isMine) sheet = true else if (!readOnly && Session.me != null) reactBar = true }
                    else if (dir == 2 && kotlin.math.abs(dx) > far) {
                        val toNext = if (rtl) dx > 0 else dx < 0
                        if (toNext) { if (ui < users.size - 1) { uiRaw = ui + 1; siRaw = 0; progress = 0f } else Nav.back() }
                        else if (ui > 0) { uiRaw = ui - 1; siRaw = 0; progress = 0f }
                    }
                    dragY = 0f
                }
            }, contentAlignment = Alignment.Center) {
            when {
                slide.media != null && slide.video -> StVideo(slide.media, paused, onDur = { durMs = it.coerceIn(1500L, 60_000L) }, onReady = { ready = true })
                slide.media != null -> AsyncImage(slide.media, slide.text.ifBlank { "Status photo" }, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                    onSuccess = { ready = true }, onError = { ready = true })
                else -> Box(Modifier.fillMaxSize().background(Statuses.brush(slide.bg)).padding(28.dp), contentAlignment = Alignment.Center) {
                    Text(smLinkify(slide.text), color = Statuses.textColor(slide.color), fontSize = if (slide.text.length > 120) 20.sp else 26.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, lineHeight = if (slide.text.length > 120) 28.sp else 36.sp, style = TextStyle(textDirection = TextDirection.Content))
                }
            }
            if (slide.media != null && slide.text.isNotBlank()) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().alpha(if (held) 0f else 1f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))).padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 84.dp)) {
                Text(smLinkify(slide.text), color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), style = TextStyle(textDirection = TextDirection.Content))
            }
        }
        // Top: progress bars + who + actions
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().alpha(if (held) 0f else 1f).background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
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
                IconBtn(Sym.ArrowBack, "Close", { Nav.back() }, tint = Color.White)
                if (!readOnly) Avatar(user.photo, user.nick, 38.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(if (isMine) "My status" else user.nick, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                    Text(stAgo(slide.createdAt),
                        color = Color.White.copy(alpha = .8f), fontSize = 13.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                if (paused && (held || keyPaused)) Icon(Sym.Pause, "Paused", tint = Color.White.copy(alpha = .85f), modifier = Modifier.padding(end = 2.dp).size(20.dp))
                if (slide.video) IconBtn(if (SmPrefs.videoMuted) Sym.VolumeOff else Sym.VolumeUp,
                    if (SmPrefs.videoMuted) "Unmute" else "Mute", { SmPrefs.muteVideos(!SmPrefs.videoMuted) }, tint = Color.White)
                if (isMine) IconBtn(Sym.Add, "Add status", { Nav.open(PostStatusPage()) }, tint = Color.White)
                Box {
                    IconBtn(Sym.MoreVert, "More", { menu = true }, tint = Color.White)
                    DropdownMenu(menu, { menu = false }) {
                        if (!readOnly) {
                            val inHl = SmPrefs.hasHighlight(slide.id)
                            DropdownMenuItem(text = { Text(if (inHl) "In Highlights" else "Save to Highlights") }, leadingIcon = { Icon(if (inHl) Sym.BookmarkFill else Icons.Rounded.BookmarkAdd, null) },
                                onClick = { menu = false; toast(c, if (SmPrefs.addHighlight(slide, user.nick)) "Saved to Highlights" else "Already in Highlights") }, modifier = Modifier.firstFocus(always = true))
                        }
                        if (slide.media != null) {
                            DropdownMenuItem(text = { Text("Save to phone") }, leadingIcon = { Icon(Sym.Download, null) },
                                onClick = { menu = false; smSaveToPhone(c, slide.media, "status-${slide.id.take(8)}." + if (slide.video) "mp4" else "jpg") })
                            DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Sym.Share, null) },
                                onClick = { menu = false; scope.launch { smShareFile(c, slide.media, "status-${slide.id.take(8)}." + if (slide.video) "mp4" else "jpg", if (slide.video) "video/*" else "image/*") } })
                        } else DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Sym.Share, null) }, onClick = { menu = false; share(c, slide.text) })
                        if (!isMine && !readOnly) {
                            val m = SmPrefs.isMuted(user.userId)
                            DropdownMenuItem(text = { Text(if (m) "Unmute ${user.nick}" else "Mute ${user.nick}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Icon(if (m) Sym.NotificationsActive else Sym.NotificationsOff, null) },
                                onClick = { menu = false; SmPrefs.setMuted(user.userId, !m); toast(c, if (m) "${user.nick}'s status unmuted" else "${user.nick}'s status muted. You'll find it under Muted updates.") })
                        }
                        if (isMine) {
                            DropdownMenuItem(text = { Text("Privacy: " + Statuses.privacyLabel(slide.privacy)) }, leadingIcon = { Icon(Sym.Lock, null) },
                                onClick = { menu = false; privacyInfo = true })
                            DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Sym.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; confirmDel = true })
                        }
                    }
                }
            }
        }
        // Bottom: views for mine, reply + react for others
        if (!readOnly) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().alpha(if (held) 0f else 1f).navigationBarsPadding().imePadding().padding(horizontal = 10.dp, vertical = 10.dp)) {
            if (reactBar) Row(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xCC1F1F1F))
                .padding(horizontal = 4.dp, vertical = 4.dp)) {
                SmReactions.forEachIndexed { i, e ->
                    Box(Modifier.size(40.dp).semantics { contentDescription = "React $e" }.tap(CircleShape) { react(e) }.then(if (i == 0) Modifier.firstFocus(always = true) else Modifier), contentAlignment = Alignment.Center) {
                        Text(e, fontSize = 22.sp)
                    }
                }
            }
            if (isMine) {
                Row(Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(20.dp)).background(Color(0x66000000)).tap(RoundedCornerShape(20.dp)) { sheet = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Sym.Visibility, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${slide.views} views", color = Color.White, fontSize = 15.sp)
                    Icon(Sym.KeyboardArrowUp, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(20.dp))
                }
            } else if (Session.me != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(reply, { reply = it }, Modifier.weight(1f).onFocusChanged { typing = it.isFocused },
                        placeholder = { Text("Reply…", color = Color.White.copy(alpha = .75f)) }, singleLine = true, shape = RoundedCornerShape(24.dp),
                        textStyle = TextStyle(fontSize = 16.sp, color = Color.White, textDirection = TextDirection.Content),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color(0x80000000), unfocusedContainerColor = Color(0x59000000),
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = smCursor(Color.White),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { sendReply() }))
                    Spacer(Modifier.width(4.dp))
                    if (reply.isNotBlank()) Box(Modifier.size(48.dp).clip(CircleShape).background(Green).tap(CircleShape) { sendReply() }, contentAlignment = Alignment.Center) {
                        Icon(Sym.Send, "Send reply", tint = Color.White, modifier = Modifier.size(22.dp)) }
                    else {
                        IconBtn(Sym.AddReaction, "React", { reactBar = !reactBar }, tint = Color.White, size = 44.dp)
                        IconBtn(if (liked) Sym.FavoriteFill else Sym.FavoriteBorder, "Like status", { if (!liked) react("❤️") else liked = false },
                            tint = if (liked) Color(0xFFFF4D67) else Color.White, size = 44.dp)
                    }
                }
            }
        }
        if (sheet) StViewersSheet(slide) { sheet = false }
    }
    if (confirmDel) AlertDialog(onDismissRequest = { confirmDel = false }, title = { Text("Delete this status?") },
        text = { Text(if (user.slides.size > 1) "Only this one is deleted; your other updates stay." else "It will be gone for everyone.") },
        confirmButton = { TextButton({
            confirmDel = false
            scope.launch {
                try { Statuses.delete(slide.id); toast(c, if (user.slides.size > 1) "Slide deleted" else "Status deleted"); onDeleted(user.userId, slide.id) }
                catch (e: Exception) { toast(c, "Could not delete: " + Api.friendly(e)) }
            } }, Modifier.firstFocus(always = true)) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmDel = false }) { Text("Cancel") } })
    if (privacyInfo) AlertDialog(onDismissRequest = { privacyInfo = false }, title = { Text("Who can see this") },
        text = { Text(Statuses.privacyLabel(slide.privacy) + " — " + Statuses.privacyHint(slide.privacy) + ".\n\nThe audience is chosen when you post. To change it, post this status again with another choice and delete this one.") },
        confirmButton = { TextButton({ privacyInfo = false }, Modifier.firstFocus(always = true)) { Text("OK") } })
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
                    Text(viewers?.let { "Viewed by ${if (it.isEmpty()) slide.views else it.size}" } ?: "Viewed by", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconBtn(Sym.Close, "Close", onClose, modifier = Modifier.firstFocus(always = true))
                }
                val v = viewers
                when {
                    failed -> Text("Could not load viewers", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    v == null -> SkeletonRows(Modifier.fillMaxWidth(), rows = 2)
                    v.isEmpty() -> Text(if (slide.views > 0) "${slide.views} views. The names are not available for this one." else "No views yet", Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
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
    val player = remember(url) { Media.player(c).apply { setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = true; volume = if (SmPrefs.videoMuted) 0f else 1f } }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) { if (player.duration > 0) onDur(player.duration); onReady() } }
            override fun onPlayerError(error: PlaybackException) { onReady() }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    LaunchedEffect(paused) { player.playWhenReady = !paused }
    LaunchedEffect(SmPrefs.videoMuted) { player.volume = if (SmPrefs.videoMuted) 0f else 1f }
    AndroidView(factory = { ctx -> PlayerView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        this.player = player; useController = false; setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
    } }, modifier = Modifier.fillMaxSize())
}

