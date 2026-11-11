package com.yidplus.app.ui

import android.view.KeyEvent as AKeyEvent
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yidplus.app.data.Media
import kotlinx.coroutines.launch
import kotlin.math.abs

/** One photo or video in a full-screen gallery. */
data class GItem(val url: String, val video: Boolean, val poster: String? = null, val title: String = "")

/** Opens the full-screen viewer on [clicked], with every photo/video of the same list beside it (swipe for the next). */
fun openGallery(items: List<GItem>, clicked: String) {
    val list = items.distinctBy { it.url }
    val i = list.indexOfFirst { it.url == clicked }
    if (i < 0) Nav.open(Screen.Gallery(listOf(GItem(clicked, false)), 0)) else Nav.open(Screen.Gallery(list, i))
}

/**
 * Full-screen photos and videos, like WhatsApp/Telegram:
 * swipe left/right = previous/next, pinch or double-tap = zoom (drag to move while zoomed),
 * drag down = close; on a keypad phone Left/Right change the item and Back closes.
 */
@Composable
fun GalleryScreen(items: List<GItem>, start: Int) {
    val c = ctx()
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (items.size - 1).coerceAtLeast(0)), pageCount = { items.size })
    var zoomed by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(pager.currentPage) { zoomed = false; dragY = 0f }
    val fade = (1f - abs(dragY) / 900f).coerceIn(0.3f, 1f)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = fade)).focusRequester(focus).focusable()
        .onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key.nativeKeyCode) {
                AKeyEvent.KEYCODE_DPAD_RIGHT, AKeyEvent.KEYCODE_6 -> { if (pager.currentPage < items.size - 1) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }; true }
                AKeyEvent.KEYCODE_DPAD_LEFT, AKeyEvent.KEYCODE_4 -> { if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }; true }
                else -> false
            }
        }) {
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed, key = { items[it].url }, beyondViewportPageCount = 1) { page ->
            val item = items[page]
            Box(Modifier.fillMaxSize().graphicsLayer(translationY = if (page == pager.currentPage) dragY else 0f)) {
                if (item.video) VideoPage(item, active = page == pager.currentPage && !pager.isScrollInProgress, onDrag = { dy -> dragY = dy })
                else ZoomPhoto(item.url, onZoom = { z -> if (page == pager.currentPage) zoomed = z }, onDrag = { dy -> dragY = dy })
            }
        }
        Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { IconBtn(Icons.Filled.Close, "Close", { Nav.back() }, tint = Color.White) }
        Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { IconBtn(Icons.Filled.Share, "Share", { share(c, items[pager.currentPage].url) }, tint = Color.White) }
        if (items.size > 1) Text("${pager.currentPage + 1} / ${items.size}", color = Color.White, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).background(Color.Black.copy(alpha = .45f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
    }
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
private fun ZoomPhoto(url: String, onZoom: (Boolean) -> Unit, onDrag: (Float) -> Unit) {
    val c = ctx()
    var scale by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    fun set(s: Float) { scale = s; if (s <= 1f) { ox = 0f; oy = 0f }; onZoom(s > 1.01f) }
    Box(Modifier.fillMaxSize()
        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1f) set(1f) else set(2.5f) }) }
        // Two fingers = zoom; one finger while zoomed = move the photo. One finger at normal size is left to the
        // pager (left/right) and to drag-to-close (down).
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                while (true) {
                    val ev = awaitPointerEvent()
                    if (ev.changes.none { it.pressed }) break
                    val fingers = ev.changes.count { it.pressed }
                    if (fingers > 1 || scale > 1.01f) {
                        val z = ev.calculateZoom(); val p = ev.calculatePan()
                        set((scale * z).coerceIn(1f, 5f))
                        if (scale > 1f) { ox += p.x; oy += p.y }
                        ev.changes.forEach { it.consume() }
                    }
                }
            }
        }
        .dragToClose({ scale <= 1.01f }, onDrag)) {
        AsyncImage(model = ImageRequest.Builder(c).data(url).placeholderMemoryCacheKey(url).crossfade(false).build(), contentDescription = null,
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = ox, translationY = oy))
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
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            } }, modifier = Modifier.fillMaxSize())
        } else {
            if (item.poster != null) AsyncImage(item.poster, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(42.dp))
            }
        }
    }
}
