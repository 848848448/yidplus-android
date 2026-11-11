package com.yidplus.app.ui

import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.composed
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yidplus.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody

// Shared pieces for the Channels, Shorts and header screens (helper B, feat-social). Names start with "So" so they
// never clash with the shared Common.kt pieces.

fun soEnc(s: String): String = URLEncoder.encode(s, "UTF-8")

/** 1234 -> "1.2K", like the site. */
fun soNum(n: Int): String = when {
    n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000f).replace(".0M", "M")
    n >= 10_000 -> "${n / 1000}K"
    n >= 1000 -> String.format(java.util.Locale.US, "%.1fK", n / 1000f).replace(".0K", "K")
    else -> n.toString()
}

fun soMembers(n: Int): String = if (n == 1) "1 member" else soNum(n) + " members"

object SoUser {
    val signedIn: Boolean get() = Session.me != null
    /** The site's super admins and the owner (what the site checks with isSuperOrOwner). */
    val isSuper: Boolean get() = Session.me?.let { it.isOwner || it.role == "admin_super" } == true
    val isAdmin: Boolean get() = Session.me?.let { it.isOwner || it.role == "admin_super" || it.role == "admin_limited" } == true
}

fun soCopy(ctx: Context, text: String, what: String = "Link copied") {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("YID PLUS", text))
    toast(ctx, what)
}

/** A site post's "content": a stored file key (posts/abc.jpg), a full link, or plain text. */
fun soPostMedia(content: String): String? {
    val c = content.trim()
    if (c.isEmpty()) return null
    if (c.startsWith("http")) return c
    if (!c.contains('/')) return null
    return Api.BASE + "/api/media/" + soEnc(c.trimStart('/')).replace("+", "%20")
}
fun soIsVideo(url: String): Boolean = Regex("""\.(mp4|webm|mov|m4v|mkv|3gp)(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(url)

/** Streams a picked file to the site as multipart (never the whole video in memory), with progress. */
suspend fun soUpload(ctx: Context, path: String, uri: Uri, fields: Map<String, String>, onProgress: (Float) -> Unit): JSONObject = withContext(Dispatchers.IO) {
    val cr = ctx.contentResolver
    val ext0 = (uri.lastPathSegment ?: "").substringAfterLast('.', "").lowercase()
    val mime = cr.getType(uri) ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext0) ?: "video/mp4"
    var name = "upload." + (android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "mp4")
    var len = -1L
    runCatching {
        cr.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (ni >= 0) c.getString(ni)?.let { if (it.contains('.')) name = it }
                val si = c.getColumnIndex(android.provider.OpenableColumns.SIZE); if (si >= 0 && !c.isNull(si)) len = c.getLong(si)
            }
        }
    }
    if (len < 0 && uri.scheme == "file") len = runCatching { java.io.File(uri.path!!).length() }.getOrDefault(-1L)
    val total = len
    // QA (the fixture account is on): never send a file to the live site; log what would be sent, like every other write.
    if (Fixture.on) {
        android.util.Log.i("YidFx", "POST $path multipart/form-data " + fields.keys.joinToString(",") { "$it=" + (fields[it] ?: "").take(60) } + ",file=$name ($mime, $total bytes)")
        onProgress(1f)
        return@withContext JSONObject().put("ok", true).put("id", "fx-" + System.nanoTime())
    }
    val body = object : RequestBody() {
        override fun contentType() = mime.toMediaTypeOrNull()
        override fun contentLength() = total
        override fun writeTo(sink: okio.BufferedSink) {
            val inp = cr.openInputStream(uri) ?: throw java.io.IOException("Can't read the file")
            inp.use {
                val buf = ByteArray(64 * 1024); var done = 0L; var last = 0L
                while (true) {
                    val n = it.read(buf); if (n < 0) break
                    sink.write(buf, 0, n); done += n
                    if (total > 0 && done - last > 128 * 1024) { last = done; onProgress((done.toFloat() / total).coerceIn(0f, 1f)) }
                }
            }
        }
    }
    val mp = MultipartBody.Builder().setType(MultipartBody.FORM)
    fields.forEach { (k, v) -> mp.addFormDataPart(k, v) }
    mp.addFormDataPart("file", name, body)
    val req = Request.Builder().url(Api.abs(path)!!).post(mp.build()).header("Accept", "application/json").build()
    Api.http.newCall(req).execute().use { res ->
        val text = res.body?.string() ?: ""
        val j = runCatching { JSONObject(text) }.getOrNull()
        if (!res.isSuccessful || j == null || (j.has("error") && j.optString("error").isNotBlank() && !j.optBoolean("ok", false)))
            throw Api.ApiError(res.code, j?.optString("error")?.takeIf { it.isNotBlank() } ?: "Upload failed (${res.code})", j)
        onProgress(1f)
        j
    }
}

/** QA only (the fixture account is on). The emulator's file picker can't always be driven by the test, so
 *  `adb shell am broadcast -a com.yidplus.app.QA_PICK --es kind image|video [--es open newpost|upload]` makes a small file
 *  inside the app and hands it to the open New post / Upload a Short page, as if it was picked; the upload request itself
 *  is then proved in the YidFx log. Mounted once in AppRoot. */
object SoQaPick { var uri by mutableStateOf<Uri?>(null) }

@Composable
fun SoQaPickReceiver() {
    val c = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(Fixture.on) {
        if (!Fixture.on) return@DisposableEffect onDispose { }
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(x: Context?, i: android.content.Intent?) {
                when (i?.getStringExtra("open")) { "newpost" -> Nav.open(SoNewPostPage()); "upload" -> Nav.open(SoUploadShortPage()) }
                val kind = i?.getStringExtra("kind") ?: return
                runCatching {
                    val vid = kind == "video"
                    val f = java.io.File(c.cacheDir, if (vid) "qa-pick.mp4" else "qa-pick.png")
                    if (vid) f.writeBytes(ByteArray(48 * 1024) { (it % 251).toByte() })
                    else {
                        val b = android.graphics.Bitmap.createBitmap(320, 240, android.graphics.Bitmap.Config.ARGB_8888)
                        android.graphics.Canvas(b).drawColor(0xFF25D366.toInt())
                        f.outputStream().use { o -> b.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, o) }
                    }
                    SoQaPick.uri = Uri.fromFile(f)
                    android.util.Log.i("YidFx", "LK-QA picked ${f.name} ${f.length()} bytes")
                }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(c, r, android.content.IntentFilter("com.yidplus.app.QA_PICK"), androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        onDispose { runCatching { c.unregisterReceiver(r) } }
    }
}

/** Clickable by touch, but skipped by the keypad's arrows (the keypad gets number keys instead, so OK on a row still opens it). */
fun Modifier.soTouchOnly(): Modifier = composed { if (Keys.keypad) this.focusProperties { canFocus = false } else this }

/** The site answers some actions with {ok:true, error:"..."}; treat that as a failure. */
fun JSONObject.soCheck(): JSONObject { val e = strOrNull("error"); if (e != null) throw Api.ApiError(200, e, this); return this }

/** Grey placeholder rows that pulse while a list loads (instead of a bare spinner). */
@Composable
fun SoSkeleton(rows: Int = 7, avatar: Dp = 48.dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "ska")
    val col = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark()) 0.12f else 0.08f)
    // Only for a first-ever load, and only after a quarter second (a quick answer never flashes grey rows).
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(250); show = true }
    if (!show) { Box(modifier.fillMaxWidth()); return }
    Column(modifier.fillMaxWidth().alpha(a)) {
        repeat(rows) {
            Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(avatar).clip(CircleShape).background(col))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Box(Modifier.fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(col))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.85f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(col))
                }
            }
        }
    }
}

/** An icon and one line, for empty lists. */
@Composable
fun SoEmpty(icon: ImageVector, text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(14.dp)); PrimaryButton(action, onAction) }
    }
}

/**
 * Comments as a full page over the screen (WhatsApp / Instagram on a small phone): the bar with Back, the list, and the
 * box to write at the bottom, which always stays above the keyboard (a bottom sheet hid it at 480x640). Back closes it.
 */
@Composable
fun SoCommentsPanel(title: String, onDismiss: () -> Unit, body: @Composable ColumnScope.() -> Unit) {
    androidx.activity.compose.BackHandler { onDismiss() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            TopBar(title, onBack = onDismiss)
            body()
        }
    }
}

/** A row of pill chips (topics, filters); the chosen one is filled green. Every chip is focusable for the keypad. */
@Composable
fun SoChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, dark: Boolean = false) {
    @Composable fun Chip(i: Int, o: String, m: Modifier) {
        val on = i == selected
        val bg = if (on) Green else if (dark) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
        val fg = if (on) Color.White else if (dark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        Box(m.heightIn(min = 34.dp).tap(RoundedCornerShape(17.dp)) { onSelect(i) }.background(bg, RoundedCornerShape(17.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center) {
            Text(o, color = fg, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, softWrap = false)
        }
    }
    // Up to four short filters share the width (they always fit at 480 px, nothing cut at the edge); more scroll
    // sideways with room at both ends, so no chip is ever cut mid-word.
    // Chips share the row when every word fits whole (each chip gets at least its own text's width, measured at the
    // current text size); otherwise the row scrolls sideways like WhatsApp's filter row, with room at the end.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val tm = androidx.compose.ui.text.rememberTextMeasurer()
        val d = androidx.compose.ui.platform.LocalDensity.current
        val style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        val need = options.map { o -> with(d) { tm.measure(o, style, maxLines = 1, softWrap = false).size.width.toDp() } + 24.dp }
        val room = maxWidth - 24.dp - 6.dp * (options.size - 1).coerceAtLeast(0)
        val fits = options.size <= 5 && need.fold(0.dp) { s, w -> s + w } <= room
        if (fits) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, o -> Chip(i, o, Modifier.weight(need[i].value)) }
        } else LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 12.dp, end = 28.dp, top = 6.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(options) { i, o -> Chip(i, o, Modifier.padding(horizontal = 2.dp)) }
        }
    }
}

/** Thin divider that starts after the avatar, like WhatsApp. */
@Composable
fun SoDivider(start: Dp = 80.dp) { HorizontalDivider(Modifier.padding(start = start), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)) }

/** Small count badge in brand green (the design's unread colour). */
@Composable
fun SoBadge(n: Int, modifier: Modifier = Modifier) {
    if (n <= 0) return
    Box(modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).clip(CircleShape).background(BadgeGreen).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
        Text(if (n > 99) "99+" else n.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp)
    }
}

/** A pill button (Join, Follow, ...): filled green when [filled], outlined when not. */
@Composable
fun SoPill(text: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val shape = RoundedCornerShape(18.dp)
    Row(modifier.heightIn(min = 34.dp).tap(shape, enabled = enabled, onClick = onClick)
        .then(if (filled) Modifier.background(Green, shape) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape))
        .padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        val fg = if (filled) Color.White else MaterialTheme.colorScheme.primary
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)) }
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Square/round picture: the photo first, else the name's first real letter on its colour (the same as Avatar everywhere). */
@Composable
fun SoThumb(url: String?, name: String, size: Dp, round: Boolean = true) {
    val shape = if (round) CircleShape else RoundedCornerShape(10.dp)
    val l = nameInitials(name)
    Box(Modifier.size(size).clip(shape).background(avatarColor(name.ifBlank { "Y" })), contentAlignment = Alignment.Center) {
        Text(l, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = (size.value * (if (l.length > 1) 0.38f else 0.46f)).sp)
        if (!url.isNullOrBlank()) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
fun soColorFor(name: String): Color = avatarColor(name)
fun soInitials(name: String): String = nameInitials(name)
