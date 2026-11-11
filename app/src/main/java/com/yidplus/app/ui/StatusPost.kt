package com.yidplus.app.ui

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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

/* ─────────────── Post a status ─────────────── */

/** WhatsApp-style "post status": coloured text status (the site's 8 backgrounds) or a photo/video with a caption, and who sees it. */
class PostStatusPage : PageScreen() {
    override val perfName: String get() = "PostStatus"
    override val immersive: Boolean get() = true
    @Composable override fun Content() { PostStatus() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostStatus() {
    val c = ctx()
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var bg by remember { mutableIntStateOf(0) }
    var privacy by remember { mutableStateOf("public") }
    var media by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) media = u }
    val isVideo = media?.let { (c.contentResolver.getType(it) ?: "").startsWith("video") } == true
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(250); runCatching { fr.requestFocus() } }
    BackHandler(enabled = media != null) { media = null }
    fun post() {
        if (posting) return
        if (Session.me == null) { toast(c, "Please sign in first."); return }
        val m = media
        if (m == null && text.isBlank()) { toast(c, "Type something first."); return }
        posting = true
        scope.launch {
            try {
                if (m == null) Statuses.postText(text.trim(), Statuses.BGS[bg], privacy) else Statuses.postMedia(c, m, caption.trim(), privacy)
                toast(c, "Status posted")
                Statuses.scope.launch { Statuses.res.load(true) }
                Nav.back()
            } catch (e: Exception) { toast(c, Api.friendly(e)) } finally { posting = false }
        }
    }
    val fieldColors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Color.White,
        focusedTextColor = Color.White, unfocusedTextColor = Color.White)
    val ime = WindowInsets.isImeVisible
    Box(Modifier.fillMaxSize().then(if (media == null) Modifier.background(Statuses.brush(Statuses.BGS[bg])) else Modifier.background(Color.Black))) {
      Column(Modifier.fillMaxSize().imePadding()) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Filled.Close, "Close", { if (media != null) media = null else Nav.back() }, tint = Color.White, size = 48.dp)
            Spacer(Modifier.weight(1f))
            if (media == null) IconBtn(Icons.Filled.Palette, "Change colour", { bg = (bg + 1) % Statuses.BGS.size }, tint = Color.White, size = 48.dp)
            IconBtn(Icons.Filled.PhotoLibrary, "Photo or video", {
                runCatching { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }.onFailure { toast(c, "No photo app found") }
            }, tint = Color.White, size = 48.dp)
        }
        // Middle: the text (or the chosen media) takes whatever room is left, so the keyboard never pushes controls over it
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
            val m = media
            if (m == null) {
                val big = if (ime) 22.sp else 28.sp
                TextField(text, { if (it.length <= 700) text = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp).focusRequester(fr),
                    placeholder = { Text("Type a status", Modifier.fillMaxWidth(), color = Color.White.copy(alpha = .6f), fontSize = big, textAlign = TextAlign.Center) },
                    textStyle = TextStyle(fontSize = if (text.length > 120) 18.sp else big, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
                        textDirection = TextDirection.Content), colors = fieldColors, maxLines = 10)
            } else if (isVideo) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.PlayCircle, null, tint = Color.White, modifier = Modifier.size(72.dp))
                Text("Video ready to post", color = Color.White, fontSize = 16.sp)
            } else AsyncImage(m, "Chosen photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        // Bottom: colours, caption, who sees it, post
        Column(Modifier.fillMaxWidth().then(if (ime) Modifier else Modifier.navigationBarsPadding()).padding(bottom = 10.dp)) {
            if (media == null && !ime) LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(Statuses.BGS) { i, b ->
                    Box(Modifier.size(30.dp).clip(CircleShape).background(Statuses.brush(b))
                        .border(if (i == bg) 2.5.dp else 1.dp, if (i == bg) Color.White else Color.White.copy(alpha = .4f), CircleShape)
                        .tap(CircleShape) { bg = i })
                }
            } else TextField(caption, { caption = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                placeholder = { Text("Add a caption…", color = Color.White.copy(alpha = .7f)) }, singleLine = true, shape = RoundedCornerShape(24.dp),
                textStyle = TextStyle(fontSize = 16.sp, color = Color.White, textDirection = TextDirection.Content),
                colors = TextFieldDefaults.colors(focusedContainerColor = Color(0x80000000), unfocusedContainerColor = Color(0x59000000),
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Color.White,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    items(listOf("public" to "Everyone", "followers" to "Followers", "private" to "Only me")) { (k, label) ->
                        val on = privacy == k
                        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) Color.White else Color(0x4D000000)).tap(RoundedCornerShape(16.dp)) { privacy = k }
                            .padding(horizontal = 12.dp, vertical = 7.dp)) {
                            Text(label, color = if (on) Color.Black else Color.White, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                        }
                    }
                }
                Box(Modifier.size(56.dp).clip(CircleShape).background(if (posting) BadgeGreen.copy(alpha = .5f) else BadgeGreen).tap(CircleShape) { post() },
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Post status", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
      }
    }
}
