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

/* ─────────────── Statuses (stories): the site's /api/statuses (home.js loadStatuses / openSV / submitStatus) ─────────────── */

data class StSlide(val id: String, val type: String, val text: String, val bg: String, val color: String, val media: String?, val video: Boolean,
                   val createdAt: String?, val views: Int, val reacted: Boolean, val font: String? = null)
data class StUser(val userId: String, val nick: String, val photo: String?, val slides: List<StSlide>)

object Statuses {
    /** The site's text-status backgrounds (home.js STATUS_BGS), sent as written in "bg". */
    val BGS = listOf(
        "linear-gradient(135deg,#1a1a2e,#16213e)", "linear-gradient(135deg,#0f3460,#533483)", "linear-gradient(135deg,#1b4332,#40916c)",
        "linear-gradient(135deg,#7b2d8b,#e040fb)", "linear-gradient(135deg,#b5179e,#f72585)", "linear-gradient(135deg,#e85d04,#faa307)",
        "linear-gradient(135deg,#03045e,#0096c7)", "linear-gradient(135deg,#2d3436,#636e72)")
    /** WhatsApp's text-status fonts. Built-in families only (no bundled font files), cycled with the "Aa" button. */
    val FONTS = listOf("default", "serif", "mono", "cursive")
    fun fontFamily(name: String?): androidx.compose.ui.text.font.FontFamily = when (name) {
        "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
        "mono" -> androidx.compose.ui.text.font.FontFamily.Monospace
        "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
        else -> androidx.compose.ui.text.font.FontFamily.SansSerif
    }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val res get() = Store.res("/api/statuses") { parse(it) }

    fun parse(j0: JSONObject): List<StUser> {
        val j = j0
        val me = Session.me?.id
        val vid = Regex("\\.(mp4|webm|mov|avi|3gp|mkv)(\\?|$)", RegexOption.IGNORE_CASE)
        val list = j.optJSONArray("statuses").objects().mapNotNull { s ->
            val slides = s.optJSONArray("slides").objects().map { sl ->
                val type = sl.str("type")
                val media = Api.abs(sl.strOrNull("media_url"))
                StSlide(sl.str("id"), type, sl.str("text").ifBlank { sl.str("caption") }, sl.str("bg"), sl.str("color"), media,
                    type == "video" || sl.optBoolean("is_video") || vid.containsMatchIn(sl.str("media_key")) || (media != null && vid.containsMatchIn(media)),
                    sl.strOrNull("created_at"), sl.optInt("views", 0), sl.optBoolean("i_reacted"), sl.strOrNull("font"))
            }.filter { it.text.isNotBlank() || it.media != null }
            val uid = s.str("user_id").ifBlank { s.str("owner_id") }
            if (slides.isEmpty() || uid.isBlank()) null else StUser(uid, s.str("nickname").ifBlank { s.str("nick").ifBlank { "User" } }, Api.abs(s.strOrNull("photo_url")), slides)
        }
        return list.sortedBy { if (it.userId == me) 0 else 1 }
    }

    // Which slides this phone has already shown (the site's API has no "seen" flag): green ring = something new.
    private const val SEEN = "nw|status-seen"
    private val seenIds: MutableSet<String> by lazy {
        val a = Disk.read(SEEN)?.optJSONArray("ids")
        HashSet<String>().apply { if (a != null) for (i in 0 until a.length()) add(a.optString(i)) }
    }
    var seenVersion by mutableIntStateOf(0); private set
    fun seen(id: String) = id in seenIds
    fun allSeen(u: StUser) = u.slides.all { seen(it.id) }
    fun markSeen(id: String): Boolean {
        if (id.isBlank() || !seenIds.add(id)) return false
        seenVersion++
        val keep = seenIds.toList().takeLast(600)
        Disk.write(SEEN, JSONObject().put("ids", JSONArray(keep)))
        return true
    }

    // Muted people's updates (WhatsApp "Mute"): kept on this phone; moved to a "Muted updates" section.
    private const val MUTED = "nw|status-muted"
    private val mutedIds: MutableSet<String> by lazy {
        val a = Disk.read(MUTED)?.optJSONArray("ids")
        HashSet<String>().apply { if (a != null) for (i in 0 until a.length()) add(a.optString(i)) }
    }
    var muteVersion by mutableIntStateOf(0); private set
    fun isMuted(userId: String) = userId in mutedIds
    fun toggleMute(userId: String): Boolean {
        val nowMuted = if (userId in mutedIds) { mutedIds.remove(userId); false } else { mutedIds.add(userId); true }
        muteVersion++
        Disk.write(MUTED, JSONObject().put("ids", JSONArray(mutedIds.toList())))
        return nowMuted
    }

    /** Mine first, then people with something new, then the ones already watched (WhatsApp's Updates order). */
    fun ordered(all: List<StUser>): List<StUser> {
        val me = Session.me?.id
        val mine = all.filter { it.userId == me }
        val others = all.filter { it.userId != me }
        return mine + others.filter { !allSeen(it) } + others.filter { allSeen(it) }
    }

    /** "linear-gradient(135deg,#a,#b)" or "#hex" → a brush. */
    fun brush(bg: String): Brush {
        val cols = Regex("#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})\\b").findAll(bg).mapNotNull { m ->
            var h = m.groupValues[1]; if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
            runCatching { Color(0xFF000000 or h.toLong(16)) }.getOrNull()
        }.toList()
        return when {
            cols.size >= 2 -> Brush.linearGradient(cols)
            cols.size == 1 -> Brush.linearGradient(listOf(cols[0], cols[0]))
            else -> Brush.linearGradient(listOf(Color(0xFF1A1A2E), Color(0xFF16213E)))
        }
    }
    fun textColor(c: String): Color = Regex("#([0-9a-fA-F]{6})").find(c)?.let { Color(0xFF000000 or it.groupValues[1].toLong(16)) } ?: Color.White

    // ── The site's calls ──
    /** Viewing someone else's slide: PUT /api/statuses {view:true,id} (home.js _svShowSlide). */
    fun sendView(id: String) { scope.launch { runCatching { Api.put("/api/statuses", JSONObject().put("view", true).put("id", id)) } } }
    /** Deleting my slide: PUT /api/statuses {action:'delete',id}. */
    suspend fun delete(id: String) = Api.put("/api/statuses", JSONObject().put("action", "delete").put("id", id))
    /** Who viewed my slide: PUT /api/statuses {viewers:true,id} → {viewers:[{nickname,photo_url,verified,viewed_at}]}. */
    suspend fun viewers(id: String): List<JSONObject> = Api.put("/api/statuses", JSONObject().put("viewers", true).put("id", id)).optJSONArray("viewers").objects()
    /** A text status: POST /api/statuses {type:'text', text, bg, privacy}. */
    suspend fun postText(text: String, bg: String, privacy: String, font: String? = null) =
        Api.post("/api/statuses", JSONObject().put("type", "text").put("text", text).put("bg", bg).put("privacy", privacy)
            .apply { if (!font.isNullOrBlank() && font != "default") put("font", font) })
    /** Reply / react: the site opens the private chat (POST /api/chat/rooms) and sends a text line in it (POST /api/chat). */
    suspend fun replyTo(userId: String, text: String) {
        val r = Api.post("/api/chat/rooms", JSONObject().put("type", "private").put("other_user_id", userId))
        val room = r.str("room_id").ifBlank { r.str("id") }
        Api.post("/api/chat", JSONObject().put("room_id", room).put("type", "text").put("text", text))
    }

    /** A photo/video status: multipart POST /api/statuses (type=media, media=<file>, caption, privacy), like the site's FormData. */
    suspend fun postMedia(ctx: Context, uri: Uri, caption: String, privacy: String): JSONObject = withContext(Dispatchers.IO) {
        val cr = ctx.contentResolver
        val mime = cr.getType(uri) ?: "application/octet-stream"
        val size = runCatching { cr.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else -1L } }.getOrNull() ?: -1L
        if (size > 100L * 1024 * 1024) throw Api.ApiError(413, "That file is over 100 MB.")
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: if (mime.startsWith("video")) "mp4" else "jpg"
        val file = object : RequestBody() {
            override fun contentType() = mime.toMediaType()
            override fun contentLength() = size
            override fun writeTo(sink: BufferedSink) { cr.openInputStream(uri)!!.use { sink.writeAll(it.source()) } }
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("type", "media").addFormDataPart("media", "status.$ext", file)
            .addFormDataPart("caption", caption).addFormDataPart("privacy", privacy).build()
        val req = Request.Builder().url(Api.abs("/api/statuses")!!).post(body).header("Accept", "application/json").build()
        Fixture.answer(req)?.let { return@withContext it }
        Api.http.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            val j = runCatching { JSONObject(t) }.getOrNull()
            if (!r.isSuccessful) throw Api.ApiError(r.code, j?.strOrNull("error") ?: "The site answered ${r.code}", j)
            j ?: JSONObject().put("ok", true)
        }
    }
}

/* ─────────────── The strip of circles above the chat list ─────────────── */

/** WhatsApp-style status circles: "My status" first (tap = view mine or post, long-press / + = post), then everyone else. */
@Composable
fun StatusStrip() {
    if (SiteConfig.hidden("statuses")) return
    val res = Statuses.res
    LaunchedEffect(Session.me?.id) { res.load() }
    val me = Session.me
    val all = res.data.orEmpty()
    if (me == null && all.isEmpty()) return
    @Suppress("UNUSED_VARIABLE") val v = Statuses.seenVersion
    val ordered = Statuses.ordered(all)
    val mine = ordered.firstOrNull { it.userId == me?.id }
    val others = ordered.filter { it.userId != me?.id }
    Column(Modifier.fillMaxWidth()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (me != null) item(key = "me") {
                StatusCircle("My status", me.photo, me.nickname, mine?.slides?.map { true } ?: emptyList(), mine = true, plus = true,
                    onClick = { if (mine != null) Nav.open(StatusViewerPage(me.id)) else Nav.open(PostStatusPage()) },
                    onLong = { Nav.open(PostStatusPage()) }, onPlus = { Nav.open(PostStatusPage()) })
            }
            items(others, key = { it.userId }) { u ->
                StatusCircle(u.nick, u.photo, u.nick, u.slides.map { Statuses.seen(it.id) }, mine = false, plus = false,
                    onClick = { Nav.open(StatusViewerPage(u.userId)) }, onLong = null, onPlus = null)
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatusCircle(label: String, photo: String?, colorName: String, seen: List<Boolean>, mine: Boolean, plus: Boolean,
                         onClick: () -> Unit, onLong: (() -> Unit)?, onPlus: (() -> Unit)?) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.width(72.dp).clip(shape)
        .then(if (focused && Keys.keypad) Modifier.background(Gold.copy(alpha = 0.18f)).border(2.dp, Gold, shape) else Modifier)
        .combinedClickable(interactionSource = src, indication = ripple(), onLongClick = onLong, onClick = onClick)
        .padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(62.dp)) {
            StatusRing(seen, mine, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.Center).size(54.dp).clip(CircleShape)) { Avatar(photo, colorName, 54.dp) }
            if (plus) Box(Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(2.dp)
                .clip(CircleShape).background(BadgeGreen).then(if (onPlus != null) Modifier.combinedClickable(onClick = onPlus) else Modifier),
                contentAlignment = Alignment.Center) {
                Icon(Sym.Add, "Add status", tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            fontWeight = if (!mine && seen.any { !it }) FontWeight.SemiBold else FontWeight.Normal,
            style = TextStyle(textDirection = TextDirection.Content), modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp))
    }
}

/* ─────────────── The dedicated WhatsApp "Updates" screen (vertical list) ─────────────── */

/** WhatsApp's Status/Updates screen: My status, then Recent updates and Viewed updates as a vertical list, plus a camera to post. */
class StatusUpdatesPage : PageScreen() {
    override val perfName get() = "Status"
    @Composable override fun Content() {
        val me = Session.me
        val res = Statuses.res
        LaunchedEffect(me?.id) { res.load() }
        @Suppress("UNUSED_VARIABLE") val v = Statuses.seenVersion
        @Suppress("UNUSED_VARIABLE") val v2 = Statuses.muteVersion
        val all = res.data.orEmpty()
        val mine = all.firstOrNull { it.userId == me?.id }
        val everyone = Statuses.ordered(all).filter { it.userId != me?.id }
        val others = everyone.filter { !Statuses.isMuted(it.userId) }
        val muted = everyone.filter { Statuses.isMuted(it.userId) }
        val unseen = others.filter { !Statuses.allSeen(it) }
        val seenList = others.filter { Statuses.allSeen(it) }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxSize()) {
                ChBar("Status", onBack = { Nav.back() })
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp)) {
                    item(key = "mine") { StatusMyRow(me, mine) }
                    if (unseen.isNotEmpty()) { item(key = "h1") { StatusHeader("Recent updates") }; items(unseen, key = { it.userId }) { StatusUserRow(it) } }
                    if (seenList.isNotEmpty()) { item(key = "h2") { StatusHeader("Viewed updates") }; items(seenList, key = { it.userId }) { StatusUserRow(it) } }
                    if (muted.isNotEmpty()) { item(key = "h3") { StatusHeader("Muted updates") }; items(muted, key = { it.userId }) { StatusUserRow(it) } }
                    if (everyone.isEmpty()) item(key = "empty") { Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { Text("No recent updates to show.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) } }
                }
            }
            // Camera + text-status buttons (WhatsApp's two FABs).
            Column(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).tap(CircleShape) { Nav.open(PostStatusPage()) }, contentAlignment = Alignment.Center) {
                    Icon(Sym.Edit, "Text status", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.size(56.dp).clip(CircleShape).background(ChatLook.unread).tap(CircleShape) { Nav.open(PostStatusPage()) }, contentAlignment = Alignment.Center) {
                    Icon(Sym.PhotoCamera, "Add status", tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusHeader(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatusMyRow(me: com.yidplus.app.data.Me?, mine: StUser?) {
    me ?: return
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).tap(RoundedCornerShape(0.dp)) {
        if (mine != null) Nav.open(StatusViewerPage(me.id)) else Nav.open(PostStatusPage())
    }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(58.dp)) {
            if (mine != null) StatusRing(mine.slides.map { true }, mine = true, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.Center).size(50.dp).clip(CircleShape)) { Avatar(me.photo, me.nickname, 50.dp) }
            Box(Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(2.dp).clip(CircleShape).background(BadgeGreen), contentAlignment = Alignment.Center) {
                Icon(Sym.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text("My status", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(if (mine != null) "Tap to view your update" else "Tap to add status update", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    HorizontalDivider(Modifier.padding(start = 88.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = .3f))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatusUserRow(u: StUser) {
    val c = ctx()
    var menu by remember { mutableStateOf(false) }
    val muted = Statuses.muteVersion.let { Statuses.isMuted(u.userId) }
    Box {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .combinedClickable(onClick = { Nav.open(StatusViewerPage(u.userId)) }, onLongClick = { menu = true })
            .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp)) {
                StatusRing(u.slides.map { Statuses.seen(it.id) }, mine = false, Modifier.fillMaxSize())
                Box(Modifier.align(Alignment.Center).size(50.dp).clip(CircleShape)) { Avatar(u.photo, u.nick, 50.dp) }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(u.nick, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                Text(if (muted) "Muted" else chatTime(u.slides.firstOrNull()?.createdAt), fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text(if (muted) "Unmute ${u.nick}" else "Mute ${u.nick}") }, onClick = {
                menu = false; val nm = Statuses.toggleMute(u.userId); toast(c, if (nm) "Muted ${u.nick}'s updates" else "Unmuted")
            })
            DropdownMenuItem(text = { Text("View") }, onClick = { menu = false; Nav.open(StatusViewerPage(u.userId)) })
        }
    }
}

/** The segmented ring: one arc per slide, green = not seen yet, grey = seen, brand green for my own. */
@Composable
private fun StatusRing(seen: List<Boolean>, mine: Boolean, modifier: Modifier) {
    if (seen.isEmpty()) return
    val unseenC = Color(0xFF25D366)
    val seenC = MaterialTheme.colorScheme.outline.copy(alpha = .55f)
    val mineC = Color(0xFF2B8A73)
    Canvas(modifier) {
        val stroke = 2.6.dp.toPx()
        val n = seen.size
        val gap = if (n > 1) (if (n > 12) 3f else 7f) else 0f
        val sweep = 360f / n
        for (i in 0 until n) {
            val col = if (mine) mineC else if (seen[i]) seenC else unseenC
            drawArc(col, startAngle = -90f + i * sweep + gap / 2, sweepAngle = sweep - gap, useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = if (!mine && seen[i]) stroke * 0.75f else stroke, cap = StrokeCap.Round))
        }
    }
}
