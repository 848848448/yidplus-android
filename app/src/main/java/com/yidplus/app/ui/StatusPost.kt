package com.yidplus.app.ui

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
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
class PostStatusPage(val startMode: String? = null, val initialMedia: String? = null) : PageScreen() {
    override val perfName: String get() = "PostStatus"
    override val immersive: Boolean get() = true
    @Composable override fun Content() { PostStatus(startMode, initialMedia) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostStatus(startMode: String? = null, initialMedia: String? = null) {
    val c = ctx()
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var bg by remember { mutableIntStateOf(0) }
    var fontIdx by remember { mutableIntStateOf(0) }
    var privacyTouched by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(SettingsProfile.text("privacy_status").ifBlank { "public" }) }
    LaunchedEffect(Unit) { SettingsProfile.load(); if (!privacyTouched) privacy = SettingsProfile.text("privacy_status").ifBlank { "public" } }
    var media by remember { mutableStateOf(initialMedia?.let { runCatching { Uri.parse(it) }.getOrNull() }) }
    var caption by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    val recorder = remember { ChatVoiceRecorder(c) }
    var recording by remember { mutableStateOf(false) }
    var recSec by remember { mutableIntStateOf(0) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) media = u }
    var shotUri by remember { mutableStateOf<Uri?>(null) }
    val takePic = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) shotUri?.let { media = it } }
    fun shoot() {
        val f = java.io.File(java.io.File(c.cacheDir, "captures").apply { mkdirs() }, "status_" + System.currentTimeMillis() + ".jpg")
        val u = runCatching { androidx.core.content.FileProvider.getUriForFile(c, c.packageName + ".files", f) }.getOrNull() ?: return
        shotUri = u; runCatching { takePic.launch(u) }.onFailure { toast(c, "No camera app found") }
    }
    var composingLayout by remember { mutableStateOf(false) }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { list ->
        when {
            list.size >= 2 -> { composingLayout = true; scope.launch {
                val f = withContext(Dispatchers.IO) { csComposeCollage(c, list.take(4)) }
                composingLayout = false
                if (f != null) media = runCatching { androidx.core.content.FileProvider.getUriForFile(c, c.packageName + ".files", f) }.getOrNull()
                else toast(c, "Couldn't make the layout")
            } }
            list.size == 1 -> media = list[0]
        }
    }
    fun startRec() { if (recorder.start()) { recording = true } else toast(c, "Couldn't start recording") }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) startRec() else toast(c, "Allow the microphone to record a voice status") }
    fun askRec() { if (ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRec() else micPerm.launch(Manifest.permission.RECORD_AUDIO) }
    val isVideo = media?.let { (c.contentResolver.getType(it) ?: "").startsWith("video") } == true
    LaunchedEffect(Unit) {
        when (startMode) {
            "camera" -> shoot()
            "voice" -> askRec()
            "layout" -> runCatching { pickMany.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            "gallery" -> runCatching { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
            else -> { kotlinx.coroutines.delay(250); runCatching { fr.requestFocus() } }
        }
    }
    BackHandler(enabled = media != null) { media = null }
    fun post() {
        if (posting) return
        if (Session.me == null) { toast(c, "Please sign in first."); return }
        val m = media
        if (m == null && text.isBlank()) { toast(c, "Type something first."); return }
        posting = true
        scope.launch {
            try {
                if (m == null) Statuses.postText(text.trim(), Statuses.BGS[bg], privacy, Statuses.FONTS[fontIdx]) else Statuses.postMedia(c, m, caption.trim(), privacy)
                toast(c, "Status posted")
                Statuses.scope.launch { Statuses.res.load(true) }
                Nav.back()
            } catch (e: Exception) { toast(c, Api.friendly(e)) } finally { posting = false }
        }
    }
    fun stopRec(keep: Boolean) {
        val res = recorder.stop(keep); recording = false; recSec = 0
        if (keep && res != null) {
            if (Session.me == null) { toast(c, "Please sign in first."); return }
            posting = true
            scope.launch {
                try {
                    Statuses.postVoice(res.first, privacy)
                    toast(c, "Voice status posted")
                    Statuses.scope.launch { Statuses.res.load(true) }
                    Nav.back()
                } catch (e: Exception) { toast(c, Api.friendly(e)) } finally { posting = false }
            }
        } else if (keep) toast(c, "Too short — hold to record")
    }
    // Tick the timer while recording; auto-stop and post at 60s (WhatsApp's status cap).
    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        while (recording) { recSec = recorder.seconds(); if (recSec >= 60) { stopRec(true); break }; kotlinx.coroutines.delay(200) }
    }
    BackHandler(enabled = recording) { stopRec(false) }
    DisposableEffect(Unit) { onDispose { runCatching { recorder.stop(false) } } }
    val fieldColors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Color.White,
        focusedTextColor = Color.White, unfocusedTextColor = Color.White)
    val ime = WindowInsets.isImeVisible
    Box(Modifier.fillMaxSize().then(if (media == null) Modifier.background(Statuses.brush(Statuses.BGS[bg])) else Modifier.background(Color.Black))) {
      Column(Modifier.fillMaxSize().imePadding()) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Sym.Close, "Close", { if (media != null) media = null else Nav.back() }, tint = Color.White, size = 48.dp)
            Spacer(Modifier.weight(1f))
            if (media == null) Box(Modifier.size(48.dp).clip(CircleShape).tap(CircleShape) { fontIdx = (fontIdx + 1) % Statuses.FONTS.size }, contentAlignment = Alignment.Center) {
                // Hebrew sample so the font change is visible on the button itself (these are Hebrew fonts).
                Text("א", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = Statuses.fontFamily(Statuses.FONTS[fontIdx]), style = TextStyle(textDirection = TextDirection.Rtl))
            }
            if (media == null) IconBtn(Sym.Palette, "Change colour", { bg = (bg + 1) % Statuses.BGS.size }, tint = Color.White, size = 48.dp)
            if (media == null) IconBtn(Sym.Mic, "Voice status", { askRec() }, tint = Color.White, size = 48.dp)
            if (media == null) IconBtn(Sym.PhotoCamera, "Camera", { shoot() }, tint = Color.White, size = 48.dp)
            if (media == null) IconBtn(Sym.GridView, "Layout (collage)", {
                runCatching { pickMany.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.onFailure { toast(c, "No photo app found") }
            }, tint = Color.White, size = 48.dp)
            IconBtn(Sym.PhotoLibrary, "Photo or video", {
                runCatching { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }.onFailure { toast(c, "No photo app found") }
            }, tint = Color.White, size = 48.dp)
        }
        // Middle: the text (or the chosen media) takes whatever room is left, so the keyboard never pushes controls over it
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
            val m = media
            if (m == null) {
                val big = if (ime) 22.sp else 28.sp
                // The centred hint hides while the field is focused: the cursor sat inside "Type a status" otherwise.
                var fieldFocused by remember { mutableStateOf(false) }
                TextField(text, { if (it.length <= 700) text = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp).focusRequester(fr).onFocusChanged { fieldFocused = it.isFocused },
                    placeholder = { if (!fieldFocused) Text("Type a status", Modifier.fillMaxWidth(), color = Color.White.copy(alpha = .6f), fontSize = big, textAlign = TextAlign.Center) },
                    textStyle = TextStyle(fontSize = if (text.length > 120) 18.sp else big, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
                        fontFamily = Statuses.fontFamily(Statuses.FONTS[fontIdx]), textDirection = TextDirection.Content), colors = fieldColors, maxLines = 10)
            } else if (isVideo) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Sym.PlayCircle, null, tint = Color.White, modifier = Modifier.size(72.dp))
                Text("Video ready to post", color = Color.White, fontSize = 16.sp)
            } else AsyncImage(m, "Chosen photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        // Bottom: colours, caption, who sees it, post
        Column(Modifier.fillMaxWidth().then(if (ime) Modifier else Modifier.navigationBarsPadding()).padding(bottom = 10.dp)) {
            if (media == null) { if (!ime) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(Statuses.BGS) { i, b ->
                    Box(Modifier.size(if (i == bg) 40.dp else 36.dp).clip(CircleShape).background(Statuses.brush(b))
                        .border(if (i == bg) 3.dp else 1.5.dp, if (i == bg) Color.White else Color.White.copy(alpha = .45f), CircleShape)
                        .tap(CircleShape) { bg = i })
                }
            } } else TextField(caption, { caption = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp),
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
                        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) Color.White else Color(0x4D000000)).tap(RoundedCornerShape(16.dp)) { privacy = k; privacyTouched = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp)) {
                            Text(label, color = if (on) Color.Black else Color.White, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                        }
                    }
                }
                Box(Modifier.size(56.dp).clip(CircleShape).background(if (posting) BadgeGreen.copy(alpha = .5f) else BadgeGreen).tap(CircleShape) { post() },
                    contentAlignment = Alignment.Center) {
                    Icon(Sym.Send, "Post status", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
      }
      // Voice-status recording overlay (WhatsApp-style): big mic + timer, Cancel / Send.
      if (recording) Box(Modifier.fillMaxSize().background(Color(0xE6000000)), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Box(Modifier.size(140.dp).clip(CircleShape).background(BadgeGreen.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                  Box(Modifier.size(96.dp).clip(CircleShape).background(BadgeGreen), contentAlignment = Alignment.Center) {
                      Icon(Sym.Mic, null, tint = Color.White, modifier = Modifier.size(48.dp))
                  }
              }
              Spacer(Modifier.height(24.dp))
              Text("%d:%02d".format(recSec / 60, recSec % 60), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
              Spacer(Modifier.height(6.dp))
              Text("Recording voice status…", color = Color.White.copy(alpha = .7f), fontSize = 14.sp)
              Spacer(Modifier.height(40.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                      Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0x33FFFFFF)).tap(CircleShape) { stopRec(false) }, contentAlignment = Alignment.Center) {
                          Icon(Icons.Filled.Delete, "Cancel", tint = Color.White, modifier = Modifier.size(30.dp))
                      }
                      Spacer(Modifier.height(6.dp)); Text("Cancel", color = Color.White, fontSize = 13.sp)
                  }
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                      Box(Modifier.size(64.dp).clip(CircleShape).background(if (posting) BadgeGreen.copy(alpha = .5f) else BadgeGreen).tap(CircleShape) { stopRec(true) }, contentAlignment = Alignment.Center) {
                          Icon(Sym.Send, "Send", tint = Color.White, modifier = Modifier.size(30.dp))
                      }
                      Spacer(Modifier.height(6.dp)); Text("Send", color = Color.White, fontSize = 13.sp)
                  }
              }
          }
      }
      if (composingLayout) Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
          Text("Making layout…", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
      }
    }
}

/** Compose 2–4 photos into one status image (WhatsApp "Layout"): clean 2/3/4 templates, each cell centre-cropped. */
internal fun csComposeCollage(c: Context, uris: List<Uri>): java.io.File? = runCatching {
    val n = uris.size.coerceIn(1, 4)
    val W = 1080f; val H = 1350f
    val out = android.graphics.Bitmap.createBitmap(W.toInt(), H.toInt(), android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    canvas.drawColor(android.graphics.Color.BLACK)
    val rects: List<android.graphics.RectF> = when (n) {
        2 -> listOf(android.graphics.RectF(0f, 0f, W / 2, H), android.graphics.RectF(W / 2, 0f, W, H))
        3 -> listOf(android.graphics.RectF(0f, 0f, W / 2, H), android.graphics.RectF(W / 2, 0f, W, H / 2), android.graphics.RectF(W / 2, H / 2, W, H))
        4 -> listOf(android.graphics.RectF(0f, 0f, W / 2, H / 2), android.graphics.RectF(W / 2, 0f, W, H / 2), android.graphics.RectF(0f, H / 2, W / 2, H), android.graphics.RectF(W / 2, H / 2, W, H))
        else -> listOf(android.graphics.RectF(0f, 0f, W, H))
    }
    val gap = 6f
    for (i in 0 until n) {
        val r = rects[i]
        val cell = android.graphics.RectF(r.left + (if (r.left > 0f) gap / 2 else 0f), r.top + (if (r.top > 0f) gap / 2 else 0f),
            r.right - (if (r.right < W) gap / 2 else 0f), r.bottom - (if (r.bottom < H) gap / 2 else 0f))
        val bmp = csDecodeSampled(c, uris[i], cell.width().toInt(), cell.height().toInt()) ?: continue
        val scale = maxOf(cell.width() / bmp.width, cell.height() / bmp.height)
        val sw = cell.width() / scale; val sh = cell.height() / scale
        val sx = ((bmp.width - sw) / 2f).toInt(); val sy = ((bmp.height - sh) / 2f).toInt()
        canvas.drawBitmap(bmp, android.graphics.Rect(sx, sy, sx + sw.toInt(), sy + sh.toInt()), cell, null)
        bmp.recycle()
    }
    val f = java.io.File(java.io.File(c.cacheDir, "captures").apply { mkdirs() }, "collage_" + System.currentTimeMillis() + ".jpg")
    java.io.FileOutputStream(f).use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
    out.recycle()
    f
}.getOrNull()

private fun csDecodeSampled(c: Context, uri: Uri, reqW: Int, reqH: Int): android.graphics.Bitmap? = runCatching {
    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    c.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
    var s = 1
    while (reqW > 0 && reqH > 0 && o.outWidth / (s * 2) >= reqW && o.outHeight / (s * 2) >= reqH) s *= 2
    c.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = s }) }
}.getOrNull()

/* ─────────────── WhatsApp "Add status" sheet ─────────────── */

/** WhatsApp's "Add status" bottom sheet: Text / Music / Layout / Voice / AI images, then Camera + Gallery. */
class AddStatusPage : PageScreen() {
    override val perfName: String get() = "AddStatus"
    override val immersive: Boolean get() = true
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        fun go(mode: String?) { Nav.back(); Nav.open(PostStatusPage(mode)) }
        fun pickRecent(u: Uri) { Nav.back(); Nav.open(PostStatusPage(null, u.toString())) }
        val readPerm = if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        var recents by remember { mutableStateOf<List<Uri>>(emptyList()) }
        val permL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) scope.launch { recents = withContext(Dispatchers.IO) { statusRecentImages(c) } } }
        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(c, readPerm) == PackageManager.PERMISSION_GRANTED) recents = withContext(Dispatchers.IO) { statusRecentImages(c) }
            else permL.launch(readPerm)
        }
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).pointerInput(Unit) { detectTapGestures { Nav.back() } }) {
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
                Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp).pointerInput(Unit) { detectTapGestures {} }) {
                    Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBtn(Sym.Close, "Close", { Nav.back() })
                        Text("Add status", Modifier.weight(1f).padding(start = 6.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        AddStatusOpt(Sym.Edit, "Text", Color(0xFF7E57C2)) { go("text") }
                        AddStatusOpt(Sym.MusicNote, "Music", Color(0xFFEC407A)) { toast(c, "Music status is coming soon") }
                        AddStatusOpt(Sym.GridView, "Layout", Color(0xFF42A5F5)) { go("layout") }
                        AddStatusOpt(Sym.Mic, "Voice", Color(0xFF26A69A)) { go("voice") }
                        AddStatusOpt(Sym.Brush, "AI images", Color(0xFFFFA726)) { toast(c, "AI image status is coming soon") }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AddStatusTile(Modifier.weight(1f), Sym.PhotoCamera, "Camera") { go("camera") }
                        AddStatusTile(Modifier.weight(1f), Sym.PhotoLibrary, "Gallery") { go("gallery") }
                    }
                    if (recents.isNotEmpty()) {
                        Text("Recents", Modifier.padding(start = 16.dp, top = 6.dp, bottom = 4.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(Modifier.fillMaxWidth().height(92.dp).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(recents) { u ->
                                Box(Modifier.size(88.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).tap(RoundedCornerShape(10.dp)) { pickRecent(u) }) {
                                    AsyncImage(u, "Recent photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddStatusOpt(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).tap(RoundedCornerShape(14.dp), onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun AddStatusTile(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(modifier.height(64.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).tap(RoundedCornerShape(14.dp), onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, label, tint = ChatLook.accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Recent gallery image Uris (newest first) for the Add-status "Recents" row. */
internal fun statusRecentImages(c: Context): List<Uri> = runCatching {
    val uris = ArrayList<Uri>()
    val col = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val proj = arrayOf(android.provider.MediaStore.Images.Media._ID)
    c.contentResolver.query(col, proj, null, null, android.provider.MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { cur ->
        val idx = cur.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
        var n = 0
        while (cur.moveToNext() && n < 30) { uris.add(android.content.ContentUris.withAppendedId(col, cur.getLong(idx))); n++ }
    }
    uris
}.getOrDefault(emptyList())
