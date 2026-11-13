package com.yidplus.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yidplus.app.data.Api
import com.yidplus.app.data.Session
import com.yidplus.app.data.Store
import com.yidplus.app.data.objects
import com.yidplus.app.data.str
import com.yidplus.app.data.strOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest

/* ─────────────── Saved Items and History: the site's /api/saves and /api/history (home.js openSavedItems / openHistory) ─────────────── */

/** One saved or watched thing. type = "short" (id, media_url, nick) or a song (title, artist, cover_url), as the site's API returns them. */
data class NwItem(val type: String, val id: String, val media: String?, val nick: String, val caption: String, val title: String, val artist: String,
                  val cover: String?, val at: String?) {
    val key get() = "$type:$id"
    companion object {
        fun list(j: JSONObject): List<NwItem> = j.optJSONArray("items").objects().map {
            NwItem(it.str("item_type").ifBlank { "short" }, it.str("item_id").ifBlank { it.str("id") }, Api.abs(it.strOrNull("media_url")),
                it.str("nick").ifBlank { it.str("nickname") }, it.str("caption"), it.str("title"), it.str("artist"), Api.abs(it.strOrNull("cover_url")),
                it.strOrNull("saved_at") ?: it.strOrNull("viewed_at") ?: it.strOrNull("created_at"))
        }.filter { it.id.isNotBlank() }
    }
}

private fun nwEnc(s: String) = URLEncoder.encode(s, "UTF-8")

/** The site's save calls (shorts.js toggleShortSave): POST /api/saves {item_type,item_id}; DELETE /api/saves?item_type=&item_id=. */
object Saves {
    val res get() = Store.res("/api/saves") { NwItem.list(it) }
    suspend fun save(type: String, id: String) = Api.post("/api/saves", JSONObject().put("item_type", type).put("item_id", id))
    suspend fun unsave(type: String, id: String) = Api.delete("/api/saves?item_type=" + nwEnc(type) + "&item_id=" + nwEnc(id))
}

/** The site's history calls: POST /api/history {item_type,item_id} when something plays (shorts.js), DELETE /api/history = Clear all. */
object WatchHistory {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val res get() = Store.res("/api/history") { NwItem.list(it) }
    /** Call when a Short (or song) starts playing, signed in; fire-and-forget like the site. */
    fun record(type: String, id: String) {
        if (Session.me == null || id.isBlank()) return
        scope.launch { runCatching { Api.post("/api/history", JSONObject().put("item_type", type).put("item_id", id)) } }
    }
    suspend fun clear() = Api.delete("/api/history")
}

/** Saved Items (Settings → Saved Items). Opened with Nav.open(SavedItemsPage()). */
class SavedItemsPage : PageScreen() {
    override val perfName: String get() = "SavedItems"
    @Composable override fun Content() { NwListScreen(saved = true) }
}

/** History (Settings → History). Opened with Nav.open(HistoryPage()). */
class HistoryPage : PageScreen() {
    override val perfName: String get() = "History"
    @Composable override fun Content() { NwListScreen(saved = false) }
}

@Composable
private fun NwListScreen(saved: Boolean) {
    val c = ctx()
    val scope = rememberCoroutineScope()
    val res = if (saved) Saves.res else WatchHistory.res
    val hidden = remember { mutableStateListOf<String>() }
    var cleared by remember { mutableStateOf(false) }
    var undo by remember { mutableStateOf<NwItem?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val lst = rememberLazyListState()
    LaunchedEffect(Unit) { res.load(true) }
    LaunchedEffect(undo) { if (undo != null) { delay(5000); undo = null } }
    val list = res.data
    val shown = if (cleared) emptyList() else list.orEmpty().filter { it.key !in hidden }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(if (saved) "Saved items" else "History", onBack = { Nav.back() }, sub = if (shown.isNotEmpty()) ("${shown.size} " + (if (shown.size == 1) "item" else "items")) else null) {
            if (!saved && shown.isNotEmpty()) IconBtn(Sym.DeleteSweep, "Clear all", { confirm = true })
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                Session.me == null -> EmptyNote(Sym.Forum, "Sign in first", if (saved) "Sign in to see what you saved." else "Sign in to see your history.")
                list == null && res.error != null -> Problem(res.error!!, res.offline, { scope.launch { res.load(true) } })
                list == null -> SkeletonRows(rows = 6)
                shown.isEmpty() -> if (saved) EmptyNote(Sym.BookmarkBorder, "Nothing saved yet", "Tap Save on any Short or song to keep it here.")
                    else EmptyNote(Sym.History, "Nothing here yet", "Videos you watch and songs you play show up here.")
                else -> LazyColumn(Modifier.fillMaxSize(), state = lst, contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(shown, key = { it.key }) { x ->
                        NwItemRow(x, saved, if (x === shown.first()) Modifier.firstFocus() else Modifier, onUnsave = {
                            hidden.add(x.key)
                            scope.launch {
                                try { Saves.unsave(x.type, x.id); undo = x }
                                catch (e: Exception) { hidden.remove(x.key); toast(c, Api.friendly(e)) }
                            }
                        })
                    }
                }
            }
            val u = undo
            if (u != null) Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).fillMaxWidth(),
                shape = RoundedCornerShape(10.dp), color = Color(0xFF323232), shadowElevation = 6.dp) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Removed from saved", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    TextButton({
                        undo = null
                        scope.launch { try { Saves.save(u.type, u.id); hidden.remove(u.key); runCatching { lst.scrollToItem(0) }; toast(c, "Saved") } catch (e: Exception) { toast(c, Api.friendly(e)) } }
                    }, modifier = Modifier.firstFocus()) { Text("UNDO", color = Color(0xFF7FD6BC), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Clear history?") }, text = { Text("Clear your entire history?") },
        confirmButton = { TextButton({
            confirm = false
            scope.launch { try { WatchHistory.clear(); cleared = true; toast(c, "History cleared") } catch (e: Exception) { toast(c, Api.friendly(e)) } }
        }) { Text("Clear", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
}

private fun openNwItem(x: NwItem, c: Context) {
    val m = x.media
    if (m == null) { toast(c, "This item can't be played here"); return }
    WatchHistory.record(x.type, x.id)
    Nav.open(Screen.Video(m, if (x.type == "short") "@" + x.nick.ifBlank { "Short" } else x.title.ifBlank { "Song" }))
}

@Composable
private fun NwItemRow(it: NwItem, saved: Boolean, modifier: Modifier, onUnsave: () -> Unit) {
    val c = ctx()
    val short = it.type == "short"
    Column {
        Row(modifier.fillMaxWidth().heightIn(min = 84.dp).tap(RoundedCornerShape(0.dp)) { openNwItem(it, c) }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (short) Box(Modifier.width(50.dp).height(68.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E2421)), contentAlignment = Alignment.Center) {
                NwVideoThumb(it.media)
                Box(Modifier.size(24.dp).clip(CircleShape).background(Color(0x8C000000)), contentAlignment = Alignment.Center) {
                    Icon(Sym.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            } else Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Sym.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
                if (it.cover != null) AsyncImage(it.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                val title = if (short) it.caption.ifBlank { "@" + it.nick.ifBlank { "Short" } } else it.title.ifBlank { "Untitled" }
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Start))
                Spacer(Modifier.height(3.dp))
                val bits = buildList {
                    add(if (short) "Short" else "Song")
                    if (short && it.caption.isNotBlank() && it.nick.isNotBlank()) add("@" + it.nick)
                    if (!short && it.artist.isNotBlank()) add(it.artist)
                    ago(it.at).takeIf { a -> a.isNotBlank() }?.let { a -> add(a) }
                }
                Text(bits.joinToString(" · "), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Start))
            }
            if (saved) IconBtn(Sym.Bookmark, "Remove from saved", onUnsave, tint = BadgeGreen)
        }
        HorizontalDivider(Modifier.padding(start = 80.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
    }
}

/** A real first frame for a Short (never a black box): read once, kept in memory and on disk. */
object NwThumbs {
    private val mem = LruCache<String, Bitmap>(30)
    private val gate = Semaphore(2)
    fun cached(url: String): Bitmap? = mem.get(url)
    private fun file(c: Context, url: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(c.cacheDir, "nw-thumbs").apply { mkdirs() }, "$h.jpg")
    }
    suspend fun load(c: Context, url: String): Bitmap? = mem.get(url) ?: gate.withPermit {
        mem.get(url) ?: withContext(Dispatchers.IO) {
            val f = file(c, url)
            if (f.exists()) runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull()?.let { mem.put(url, it); return@withContext it }
            val r = MediaMetadataRetriever()
            try {
                if (url.startsWith("content:") || url.startsWith("file:")) r.setDataSource(c, android.net.Uri.parse(url))
                else r.setDataSource(url, HashMap<String, String>().apply { Api.session()?.let { put("Cookie", "yp_session=$it") }; put("User-Agent", Api.UA) })
                val b = if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 240, 320) else r.getFrameAtTime(500_000)
                b?.also { mem.put(url, it); runCatching { f.outputStream().use { o -> it.compress(Bitmap.CompressFormat.JPEG, 80, o) } } }
            } catch (e: Throwable) { null } finally { runCatching { r.release() } }
        }
    }
}

@Composable
private fun NwVideoThumb(url: String?) {
    val c = ctx()
    var bmp by remember(url) { mutableStateOf(url?.let { NwThumbs.cached(it) }) }
    LaunchedEffect(url) { if (bmp == null && url != null) bmp = NwThumbs.load(c, url) }
    bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
}

/* ─────────────── QA (fixture account): opens these pages by name from the QA_OPEN intent (MainActivity, by reflection) ─────────────── */

/** QA_OPEN page names: ai, ai_quick, status, status_me, post_status, saved, history, ai_row. */
fun qaOpenNew(page: String) {
    when (page) {
        "ai" -> Nav.open(AiChatPage())
        "ai_quick" -> Nav.open(AiChatPage("Give me an idea"))
        "status" -> Nav.open(StatusViewerPage())
        "status_me" -> Nav.open(StatusViewerPage(Session.me?.id))
        "post_status" -> Nav.open(PostStatusPage())
        "saved" -> Nav.open(SavedItemsPage())
        "history" -> Nav.open(HistoryPage())
        "ai_row" -> Nav.open(QaNewRowsPage())
    }
}

/** QA only: the status strip and the pinned AI row on top of a small fake chat list, as they will sit in ChatList once wired. */
class QaNewRowsPage : PageScreen() {
    override val perfName: String get() = "QaNewRows"
    @Composable override fun Content() {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopBar("Chats", onBack = { Nav.back() })
            LazyColumn(Modifier.fillMaxSize()) {
                item { StatusStrip() }
                item { AiChatRow { Nav.open(AiChatPage()) } }
                items(listOf("Yanky" to "Yanky: גוט שבת!", "חברים גרופע" to "Moishy: 📷 Photo", "Shloimy" to "You: See you tonight")) { (n, p) ->
                    Column {
                        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(null, n, 52.dp)
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Row { Text(n, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, style = TextStyle(textDirection = TextDirection.Content)); Text("12:40", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Spacer(Modifier.height(3.dp))
                                Text(p, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, style = TextStyle(textDirection = TextDirection.Content))
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 82.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                    }
                }
            }
        }
    }
}
