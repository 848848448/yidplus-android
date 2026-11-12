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
import android.util.Log
import android.view.KeyEvent as AKeyEvent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil.request.ImageRequest
import kotlin.math.abs

/** One photo or video in the viewer. [title] is the line under the close button (sender / channel); the 2.1 fields are optional. */
data class GItem(val url: String, val video: Boolean, val poster: String? = null, val title: String = "",
                 val time: String = "", val caption: String = "", val msgId: String = "")

/** Hooks a screen can set while it shows the viewer (e.g. the chat sets [forward] so "Forward" appears in ⋮). */
object SmGallery { var forward: ((GItem) -> Unit)? = null }

fun openGallery(items: List<GItem>, clicked: String) {
    val list = items.distinctBy { it.url }
    val i = list.indexOfFirst { it.url == clicked }
    if (i < 0) Nav.open(Screen.Gallery(listOf(GItem(clicked, false)), 0)) else Nav.open(Screen.Gallery(list, i))
}

private class ZoomBus { var seq by mutableIntStateOf(0); var cmd = ""; fun send(c: String) { cmd = c; seq++ } }

/**
 * Full-screen photos and videos, like WhatsApp/Telegram:
 * swipe left/right = previous/next, pinch or double-tap = zoom (drag to move while zoomed), one tap = hide/show the bars,
 * drag down = close. Keypad: 4/6 or Left/Right = previous/next, OK or 5 = zoom 2.5x / back, 2 = zoom in, 8 = zoom out,
 * arrows move a zoomed photo, Menu or 0 = options (Save, Share, Forward, Open in browser), Back closes.
 */
@Composable
fun GalleryScreen(items: List<GItem>, start: Int) {
    val c = ctx()
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (items.size - 1).coerceAtLeast(0)), pageCount = { items.size })
    var zoomed by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var chrome by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    val bus = remember { ZoomBus() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(pager.currentPage) { zoomed = false; dragY = 0f }
    // QA only (the fixture account is on): `adb shell am broadcast -a com.yidplus.app.QA_GESTURE --es g pinch|dtap|pinchin`
    // plays a real two-finger MotionEvent sequence through this window, so the gesture code itself is proved in CI.
    val qaView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        if (!com.yidplus.app.data.Fixture.on) return@DisposableEffect onDispose { }
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, i: android.content.Intent?) { qaGesture(qaView.rootView, i?.getStringExtra("g") ?: "pinch") }
        }
        androidx.core.content.ContextCompat.registerReceiver(c, r, android.content.IntentFilter("com.yidplus.app.QA_GESTURE"), androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        onDispose { runCatching { c.unregisterReceiver(r) } }
    }
    BackHandler(menu) { menu = false }
    val cur = items.getOrNull(pager.currentPage)
    fun go(d: Int) { val n = pager.currentPage + d; if (n in items.indices) scope.launch { pager.animateScrollToPage(n) } }
    val fade = (1f - abs(dragY) / 900f).coerceIn(0.3f, 1f)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = fade)).focusRequester(focus).focusable()
        .onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || menu) return@onPreviewKeyEvent false
            val photo = cur?.video == false
            when (e.key.nativeKeyCode) {
                AKeyEvent.KEYCODE_DPAD_RIGHT -> { if (zoomed) bus.send("right") else go(1); true }
                AKeyEvent.KEYCODE_DPAD_LEFT -> { if (zoomed) bus.send("left") else go(-1); true }
                AKeyEvent.KEYCODE_6 -> { go(1); true }
                AKeyEvent.KEYCODE_4 -> { go(-1); true }
                AKeyEvent.KEYCODE_DPAD_UP -> if (zoomed) { bus.send("up"); true } else false
                AKeyEvent.KEYCODE_DPAD_DOWN -> if (zoomed) { bus.send("down"); true } else false
                AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER, AKeyEvent.KEYCODE_5 -> if (photo) { bus.send("toggle"); true } else false
                AKeyEvent.KEYCODE_2, AKeyEvent.KEYCODE_PLUS, AKeyEvent.KEYCODE_NUMPAD_ADD -> if (photo) { bus.send("in"); true } else false
                AKeyEvent.KEYCODE_8, AKeyEvent.KEYCODE_MINUS, AKeyEvent.KEYCODE_NUMPAD_SUBTRACT -> if (photo) { bus.send("out"); true } else false
                AKeyEvent.KEYCODE_MENU, AKeyEvent.KEYCODE_0 -> { menu = true; chrome = true; true }
                else -> false
            }
        }) {
        // A short flick is enough for the next photo (like WhatsApp), with a small dark gap between photos while it slides.
        val fling = PagerDefaults.flingBehavior(state = pager, snapPositionalThreshold = 0.22f,
            snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed, key = { items[it].url }, beyondViewportPageCount = 1,
            pageSpacing = 12.dp, flingBehavior = fling) { page ->
            val item = items[page]
            Box(Modifier.fillMaxSize().graphicsLayer(translationY = if (page == pager.currentPage) dragY else 0f)) {
                if (item.video) VideoPage(item, active = page == pager.currentPage && !pager.isScrollInProgress, onDrag = { dy -> dragY = dy })
                else ZoomPhoto(item.url, if (page == pager.currentPage) bus else null, onZoom = { z -> if (page == pager.currentPage) zoomed = z },
                    onDrag = { dy -> dragY = dy }, onTap = { chrome = !chrome })
            }
        }
        if (chrome && cur != null) {
            // Top: close, who/when, ⋮
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent)))
                .padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Sym.Close, "Close", { Nav.back() }, tint = Color.White, size = 48.dp)
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    if (cur.title.isNotBlank()) Text(cur.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(textDirection = TextDirection.Content))
                    val sub = listOf(cur.time, if (items.size > 1) "${pager.currentPage + 1} of ${items.size}" else "").filter { it.isNotBlank() }.joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, color = Color.White.copy(alpha = .8f), fontSize = 13.sp, maxLines = 1)
                }
                IconBtn(Sym.Share, "Share", { scope.launch { smShareFile(c, cur.url, smFileName(cur), if (cur.video) "video/*" else "image/*") } }, tint = Color.White)
                Box {
                    IconBtn(Sym.MoreVert, "More options", { menu = true }, tint = Color.White)
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Save to phone") }, leadingIcon = { Icon(Sym.Download, null) },
                            onClick = { menu = false; smSaveToPhone(c, cur.url, smFileName(cur)) }, modifier = Modifier.firstFocus(always = true))
                        DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Sym.Share, null) },
                            onClick = { menu = false; scope.launch { smShareFile(c, cur.url, smFileName(cur), if (cur.video) "video/*" else "image/*") } })
                        SmGallery.forward?.let { fw -> DropdownMenuItem(text = { Text("Forward") }, leadingIcon = { Icon(Sym.Forward, null) },
                            onClick = { menu = false; fw(cur) }) }
                        DropdownMenuItem(text = { Text("Copy link") }, leadingIcon = { Icon(Sym.Link, null) }, onClick = {
                            menu = false
                            (c.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("link", cur.url))
                            toast(c, "Link copied") })
                        DropdownMenuItem(text = { Text("Open in browser") }, leadingIcon = { Icon(Sym.OpenInNew, null) },
                            onClick = { menu = false; openUrl(c, cur.url) })
                    }
                }
            }
            // Sides: ‹ › (touch and keypad focus)
            if (items.size > 1 && !zoomed) {
                if (pager.currentPage > 0) Box(Modifier.align(Alignment.CenterStart).padding(6.dp).size(44.dp).clip(CircleShape).background(Color(0x66000000))
                    .tap(CircleShape) { go(-1) }, contentAlignment = Alignment.Center) { Icon(Sym.KeyboardArrowLeft, "Previous", tint = Color.White) }
                if (pager.currentPage < items.size - 1) Box(Modifier.align(Alignment.CenterEnd).padding(6.dp).size(44.dp).clip(CircleShape).background(Color(0x66000000))
                    .tap(CircleShape) { go(1) }, contentAlignment = Alignment.Center) { Icon(Sym.KeyboardArrowRight, "Next", tint = Color.White) }
            }
            // Bottom: caption (links tappable, Yiddish right-to-left)
            if (cur.caption.isNotBlank() && !zoomed) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))).padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = if (cur.video) 64.dp else 16.dp)) {
                Text(smLinkify(cur.caption), color = Color.White, fontSize = 15.sp, maxLines = 6, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
            }
        }
    }
}

fun smFileName(g: GItem): String {
    val last = Uri.decode(g.url.substringBefore('?').substringAfterLast('/')).substringAfterLast('/')
    return last.takeIf { it.contains('.') && it.length < 90 } ?: ("yidplus-" + System.currentTimeMillis() + if (g.video) ".mp4" else ".jpg")
}

/** QA: a real two-finger pinch (out or in) or a double-tap at the middle of [v], as the touch screen would send it. */
private fun qaGesture(v: android.view.View, g: String) {
    val h = android.os.Handler(android.os.Looper.getMainLooper())
    val t0 = android.os.SystemClock.uptimeMillis()
    val cx = v.width / 2f; val cy = v.height / 2f
    fun props(n: Int) = Array(n) { k -> android.view.MotionEvent.PointerProperties().apply { id = k; toolType = android.view.MotionEvent.TOOL_TYPE_FINGER } }
    fun coords(n: Int, d: Float) = Array(n) { k -> android.view.MotionEvent.PointerCoords().apply { x = if (n == 1) cx else if (k == 0) cx - d else cx + d; y = cy; pressure = 1f; size = 1f } }
    fun send(at: Long, action: Int, n: Int, d: Float) = h.postAtTime({
        val e = android.view.MotionEvent.obtain(t0, android.os.SystemClock.uptimeMillis(), action, n, props(n), coords(n, d), 0, 0, 1f, 1f, 0, 0,
            android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
        v.dispatchTouchEvent(e); e.recycle()
    }, t0 + at)
    val ptr1 = 1 shl android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT
    if (g == "dtap") {
        send(0, android.view.MotionEvent.ACTION_DOWN, 1, 0f); send(60, android.view.MotionEvent.ACTION_UP, 1, 0f)
        send(160, android.view.MotionEvent.ACTION_DOWN, 1, 0f); send(220, android.view.MotionEvent.ACTION_UP, 1, 0f)
        Log.i("YidGallery", "qa double-tap sent"); return
    }
    val from = if (g == "pinchin") 170f else 40f; val to = if (g == "pinchin") 40f else 170f
    send(0, android.view.MotionEvent.ACTION_DOWN, 1, from)
    send(20, android.view.MotionEvent.ACTION_POINTER_DOWN or ptr1, 2, from)
    for (k in 1..12) send(20L + k * 20L, android.view.MotionEvent.ACTION_MOVE, 2, from + (to - from) * k / 12f)
    send(300, android.view.MotionEvent.ACTION_POINTER_UP or ptr1, 2, to)
    send(320, android.view.MotionEvent.ACTION_UP, 1, to)
    Log.i("YidGallery", "qa $g sent")
}

/** Vertical drag (one finger, not zoomed) moves the item with the finger; let go past ~120dp and the viewer closes. */
private fun Modifier.dragToClose(enabled: () -> Boolean, onDrag: (Float) -> Unit): Modifier = this.pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    val closeAt = 120.dp.toPx()
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var dx = 0f; var dy = 0f; var vertical: Boolean? = null
        while (true) {
            val ev = awaitPointerEvent()
            if (ev.changes.none { it.pressed }) break
            if (ev.changes.count { it.pressed } > 1 || !enabled()) { vertical = false; continue }
            val ch = ev.changes.first()
            val d = ch.positionChange()
            dx += d.x; dy += d.y
            if (vertical == null && (abs(dx) > slop || abs(dy) > slop)) vertical = abs(dy) > abs(dx) * 1.2f
            if (vertical == true) { ch.consume(); onDrag(dy.coerceAtLeast(-200f)) }
        }
        if (vertical == true) { if (dy > closeAt) Nav.back() else onDrag(0f) }
    }
}

@Composable
private fun ZoomPhoto(url: String, bus: ZoomBus?, onZoom: (Boolean) -> Unit, onDrag: (Float) -> Unit, onTap: () -> Unit) {
    val c = ctx()
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    // Never let the photo be pushed off the screen: at most its own zoomed edge reaches the screen's edge.
    fun clamp() {
        val mx = box.width * (scale - 1f) / 2f; val my = box.height * (scale - 1f) / 2f
        ox = ox.coerceIn(-mx, mx); oy = oy.coerceIn(-my, my)
    }
    // Zoom to [s] keeping the point under the fingers (or the double-tap) where it is, like WhatsApp.
    fun zoomTo(s: Float, at: Offset) {
        val ns = s.coerceIn(1f, 5f)
        val cx = at.x - box.width / 2f; val cy = at.y - box.height / 2f
        ox = cx - (cx - ox) * ns / scale; oy = cy - (cy - oy) * ns / scale
        scale = ns
        if (scale <= 1.01f) { scale = 1f; ox = 0f; oy = 0f }
        clamp(); onZoom(scale > 1.01f)
    }
    fun animTo(to: Float, at: Offset, why: String) { val from = scale
        scope.launch { animate(from, to, animationSpec = tween(200)) { v, _ -> zoomTo(v, at) }; Log.i("YidGallery", "$why zoom " + "%.2f".format(scale)) } }
    // Keypad commands from the viewer (OK/5, 2, 8, arrows while zoomed)
    if (bus != null) LaunchedEffect(bus.seq) {
        if (bus.seq == 0) return@LaunchedEffect
        val mid = Offset(box.width / 2f, box.height / 2f); val step = 80.dp.value * c.resources.displayMetrics.density
        when (bus.cmd) {
            "toggle" -> animTo(if (scale > 1.2f) 1f else 2.5f, mid, "key")
            "in" -> animTo((scale + 0.75f).coerceAtMost(5f), mid, "key")
            "out" -> animTo((scale - 0.75f).coerceAtLeast(1f), mid, "key")
            "left" -> { ox += step; clamp() }
            "right" -> { ox -= step; clamp() }
            "up" -> { oy += step; clamp() }
            "down" -> { oy -= step; clamp() }
        }
    }
    Box(Modifier.fillMaxSize().onSizeChanged { box = it }
        // Double-tap: zoom in to 2.5x on that spot, double-tap again to go back (animated). One tap: hide/show the bars.
        .pointerInput(Unit) {
            detectTapGestures(onTap = { onTap() }, onDoubleTap = { at -> animTo(if (scale > 1.2f) 1f else 2.5f, at, "double-tap") })
        }
        // Two fingers = pinch zoom around the fingers; one finger while zoomed = move the photo. One finger at normal
        // size is left to the pager (left/right = next photo) and to drag-to-close (down).
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var pinched = false; var two = false
                while (true) {
                    val ev = awaitPointerEvent()
                    val fingers = ev.changes.count { it.pressed }
                    if (fingers == 0) break
                    if (fingers > 1 && !two) { two = true; Log.i("YidGallery", "two fingers down") }
                    if (fingers > 1 || scale > 1.01f) {
                        val z = ev.calculateZoom(); val p = ev.calculatePan(); val cen = ev.calculateCentroid(useCurrent = true)
                        if (fingers > 1) onZoom(true)
                        if (z != 1f && cen != Offset.Unspecified) { zoomTo(scale * z, cen); pinched = true }
                        if (scale > 1.01f) { ox += p.x; oy += p.y; clamp() }
                        ev.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
                if (pinched) Log.i("YidGallery", "pinch zoom " + "%.2f".format(scale))
                onZoom(scale > 1.01f)
            }
        }
        .dragToClose({ scale <= 1.01f }, onDrag), contentAlignment = Alignment.Center) {
        if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Sym.BrokenImage, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(56.dp))
            Text("Could not load this photo", color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            TextButton({ failed = false; retry++ }) { Text("Try again", color = Gold) }
        } else {
            AsyncImage(model = ImageRequest.Builder(c).data(url).placeholderMemoryCacheKey(url).crossfade(false).setParameter("r", retry).build(), contentDescription = "Photo",
                contentScale = ContentScale.Fit, onSuccess = { loaded = true }, onError = { failed = true },
                modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = ox, translationY = oy))
            if (!loaded) CircularProgressIndicator(color = Color.White.copy(alpha = .7f), strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPage(item: GItem, active: Boolean, onDrag: (Float) -> Unit) {
    val c = ctx()
    Box(Modifier.fillMaxSize().dragToClose({ true }, onDrag), contentAlignment = Alignment.Center) {
        if (active) {
            val player = remember(item.url) { Media.player(c).apply { setMediaItem(MediaItem.fromUri(item.url)); prepare(); playWhenReady = true } }
            DisposableEffect(player) { onDispose { player.release() } }
            AndroidView(factory = { ctx -> PlayerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                this.player = player; useController = true; controllerShowTimeoutMs = 3000; setShowNextButton(false); setShowPreviousButton(false)
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            } }, modifier = Modifier.fillMaxSize())
        } else {
            if (item.poster != null) AsyncImage(item.poster, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f)), contentAlignment = Alignment.Center) {
                Icon(Sym.PlayArrowFill, "Play", tint = Color.White, modifier = Modifier.size(42.dp))
            }
        }
    }
}
