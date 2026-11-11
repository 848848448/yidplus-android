package com.yidplus.app.ui

import androidx.compose.foundation.gestures.detectTapGestures

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.yidplus.app.data.Api
import com.yidplus.app.data.Media
import com.yidplus.app.data.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Full-screen video (or audio) with the standard controls, which also work with the D-pad. */
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun VideoScreen(url: String, title: String) {
    val c = ctx()
    val player = remember(url) { Media.player(c).apply { setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = true } }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            this.player = player; useController = true; controllerShowTimeoutMs = 3000; setShowNextButton(false); setShowPreviousButton(false)
            requestFocus()
        } }, modifier = Modifier.fillMaxSize())
        Box(Modifier.align(Alignment.TopStart).padding(androidx.compose.ui.unit.Dp(6f))) { IconBtn(Icons.Filled.Close, "Close", { Nav.back() }, tint = Color.White) }
    }
}


/** A photo, pinch to zoom; Back closes. */
@Composable
fun PhotoScreen(url: String) {
    val c = ctx()
    var scale by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    // Opens at once from the picture already loaded in the list (same address = same cached image), fitted to the
    // screen. Pinch or double-tap to zoom; swipe down to close.
    Box(Modifier.fillMaxSize().background(Color.Black)
        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; ox = 0f; oy = 0f } else scale = 2.5f }) }
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 5f)
                if (scale > 1f) { ox += pan.x; oy += pan.y } else { oy += pan.y; ox = 0f; if (oy > 260f) { oy = 0f; Nav.back() } }
            }
        }) {
        AsyncImage(model = coil.request.ImageRequest.Builder(c).data(url).placeholderMemoryCacheKey(url).crossfade(false).build(), contentDescription = null,
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = ox, translationY = if (scale > 1f) oy else oy.coerceAtLeast(0f)))
        Box(Modifier.align(Alignment.TopStart).padding(androidx.compose.ui.unit.Dp(6f))) { IconBtn(Icons.Filled.Close, "Close", { Nav.back() }, tint = Color.White, modifier = Modifier.firstFocus()) }
        Box(Modifier.align(Alignment.TopEnd).padding(androidx.compose.ui.unit.Dp(6f))) { IconBtn(Icons.Filled.Share, "Share", { share(c, url) }, tint = Color.White) }
    }
}

/** Fallback for the few website pages the app has no screen for (and the sign-up page). Shares the app's sign-in. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(url: String) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("yidplus.com") }
    Column(Modifier.fillMaxSize()) {
        TopBar(title, onBack = { Nav.back() })
        AndroidView(factory = { ctx ->
            val cm = CookieManager.getInstance()
            Api.session()?.let { cm.setCookie(Api.BASE, "yp_session=$it; Path=/; Secure; HttpOnly") }
            WebView(ctx).apply {
                settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                settings.userAgentString = settings.userAgentString + " YIDPLUS-Android"
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, u: String) {
                        title = view.title ?: title
                        // Signed up / signed in on the page: the app takes over the same session.
                        val tok = cm.getCookie(Api.BASE)?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("yp_session=") }?.substringAfter('=')
                        if (!tok.isNullOrBlank() && tok != Api.session()) scope.launch { Session.adopt(tok) }
                    }
                }
                loadUrl(url)
            }
        }, modifier = Modifier.weight(1f))
    }
}
