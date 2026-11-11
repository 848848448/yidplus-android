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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

// Shorts, the rest of what the site has: upload, topics, a creator's page, delete your own, open one from search.

private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** The site's Shorts topics (value, chip label), same order as the website. */
val SO_SHORT_TOPICS = listOf("All" to "For You", "News" to "News", "Humor & Entertainment" to "Humor", "Simcha & Events" to "Simcha", "Torah & Religion" to "Torah",
    "Community" to "Community", "Business & Deals" to "Business", "Food" to "Food", "Travel & Traffic" to "Travel", "Politics" to "Politics",
    "Chesed & Tzedakah" to "Chesed", "Health" to "Health", "Safety & Alerts" to "Alerts", "Real Estate" to "Real Estate", "Weather" to "Weather", "Technology" to "Tech")

object SoShortTopics {
    /** Topics of each For You video, from the feed's own "tp" field. */
    val byKey = HashMap<String, List<String>>()
    var topic by mutableStateOf("All")
    fun learn(j: JSONObject) {
        j.optJSONArray("items").objects().forEach { it ->
            val a = it.optJSONArray("tp") ?: return@forEach
            byKey[it.str("id")] = (0 until a.length()).map { i -> a.optString(i) }.filter { s -> s.isNotBlank() }
        }
    }
    fun matches(s: Clip) = topic == "All" || !s.fromSrc || byKey[s.key]?.contains(topic) == true
}

/** Opens one of the site's own uploads (from search or a channel's Shorts) at the top of the Uploads tab. */
fun soOpenUploadShort(id: String) {
    appScope.launch {
        try {
            val s = Api.get("/api/shorts?id=" + soEnc(id)).optJSONArray("shorts").objects().firstOrNull()?.let(Clip::upload)
            if (s == null) { toast(Api.app, "This video is no longer there"); return@launch }
            Feeds.uploads.items.removeAll { it.key == s.key }; Feeds.uploads.items.add(0, s)
            Feeds.who = null; Feeds.startKey = s.key; Feeds.tab = ShortsTab.Uploads; Nav.go(Tab.Shorts)
        } catch (e: Exception) { toast(Api.app, Api.friendly(e)) }
    }
}

/** Opens a For You video found by search. */
fun soOpenFeedClip(s: Clip) {
    Feeds.forYou.items.removeAll { it.key == s.key }; Feeds.forYou.items.add(0, s)
    Feeds.who = null; SoShortTopics.topic = "All"; Feeds.startKey = s.key; Feeds.tab = ShortsTab.ForYou; Nav.go(Tab.Shorts)
}

/** A creator's page: picture, name, Follow, and every video of theirs the app has (tap one to watch from there). */
data class SoCreatorPage(val creatorId: String, val name: String, val avatar: String?, val verified: Boolean, val fromSrc: Boolean) : PageScreen() {
    override val perfName = "Creator"
    @Composable override fun Content() {
        val c = ctx()
        val own = remember { mutableStateListOf<Clip>() }
        var loading by remember { mutableStateOf(!fromSrc) }
        LaunchedEffect(creatorId) {
            if (!fromSrc) runCatching { Api.get("/api/shorts?owner_id=" + soEnc(creatorId)).optJSONArray("shorts").objects().map(Clip::upload) }
                .onSuccess { own.clear(); own.addAll(it.distinctBy { s -> s.key }) }
            loading = false; Perf.shown("Creator")
        }
        val clips = if (fromSrc) Feeds.forYou.items.filter { it.creatorId == creatorId } else own
        val following = creatorId in Feeds.follows
        // A member who uploads: Follow them on the site, exactly like the site's profile (home.js followUser / unfollowUser).
        val scope = rememberCoroutineScope()
        val me = Session.me
        var mFollow by remember { mutableStateOf<Boolean?>(null) }
        var mPending by remember { mutableStateOf(false) }
        var mPrivate by remember { mutableStateOf(false) }
        LaunchedEffect(creatorId, me?.id) {
            if (!fromSrc && me != null && me.id != creatorId) runCatching { Api.get("/api/follows?user_id=" + soEnc(creatorId)) }.onSuccess { j ->
                mFollow = j.optBoolean("is_following"); mPending = j.optBoolean("has_pending_request"); mPrivate = j.optBoolean("is_private") }
        }
        fun memberFollow() {
            if (me == null) { toast(c, "Sign in to follow"); Nav.go(Tab.Profile); return }
            val was = mFollow == true || mPending
            scope.launch {
                try {
                    if (was) { Api.delete("/api/follows?user_id=" + soEnc(creatorId)); mFollow = false; mPending = false; toast(c, "Unfollowed") }
                    else { val r = Api.post("/api/follows", JSONObject().put("user_id", creatorId))
                        if (r.optBoolean("requested")) { mPending = true; toast(c, "Follow request sent") } else { mFollow = true; toast(c, "Following!") } }
                } catch (e: Exception) { toast(c, Api.friendly(e)) }
            }
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopBar(name.ifBlank { "Creator" }, onBack = { Nav.back() })
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                SoThumb(avatar, name, 72.dp)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("@" + name.ifBlank { "yidplus" }, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (verified) Icon(Icons.Filled.Verified, "Verified", tint = Color(0xFF1D9BF0), modifier = Modifier.padding(start = 4.dp).size(18.dp))
                    }
                    Text("${clips.size} videos", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (fromSrc) SoPill(if (following) "Following ✓" else "Follow", !following, {
                    if (following) Feeds.follows.remove(creatorId) else Feeds.follows.add(creatorId); Feeds.persist(c)
                    toast(c, if (following) "Unfollowed $name" else "Following $name")
                }, Modifier.firstFocus())
                else Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (me?.id != creatorId) SoPill(when { mFollow == true -> "Following ✓"; mPending -> "Requested"; mPrivate -> "Request"; else -> "Follow" },
                        mFollow != true && !mPending, { memberFollow() }, Modifier.firstFocus())
                    SoPill("Channel", false, { Nav.open(SoUserChannelPage(creatorId, name)) })
                }
            }
            when {
                loading -> SoSkeleton(rows = 3)
                clips.isEmpty() -> SoEmpty(Icons.Filled.Movie, "No videos yet")
                else -> LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(clips.size, key = { clips[it].key }) { i ->
                        val s = clips[i]
                        Box(Modifier.aspectRatio(9f / 14f).tap(RoundedCornerShape(2.dp)) {
                            if (!fromSrc) { Feeds.uploads.items.removeAll { it.key == s.key }; Feeds.uploads.items.add(0, s) }
                            Feeds.who = creatorId; Feeds.whoName = name; Feeds.startKey = s.key
                            Feeds.tab = if (fromSrc) ShortsTab.ForYou else ShortsTab.Uploads; Nav.go(Tab.Shorts)
                        }) {
                            SoVideoPoster(s.poster, "", s.duration, Modifier.fillMaxSize(), fit = ContentScale.Crop)
                            if (s.likes > 0) Row(Modifier.align(Alignment.BottomEnd).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Text(" " + soNum(s.likes), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Upload a Short: pick a video, write a caption, send it (with progress), like the site's Upload button. */
class SoUploadShortPage : PageScreen() {
    override val perfName = "UploadShort"
    override fun equals(other: Any?) = other is SoUploadShortPage
    override fun hashCode() = 17
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var file by remember { mutableStateOf<Uri?>(null) }
        var caption by rememberSaveable { mutableStateOf("") }
        var progress by remember { mutableStateOf<Float?>(null) }
        var sizeMb by remember { mutableStateOf(0f) }
        val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
            if (u != null) {
                val len = runCatching { c.contentResolver.openAssetFileDescriptor(u, "r")?.use { it.length } }.getOrNull() ?: -1L
                if (len > 100L * 1024 * 1024) toast(c, "That video is over 100 MB") else { file = u; sizeMb = if (len > 0) len / 1048576f else 0f }
            }
        }
        LaunchedEffect(Unit) { if (Session.me != null && file == null) runCatching { pick.launch("video/*") } }
        fun send() {
            val f = file ?: run { pick.launch("video/*"); return }
            if (progress != null) return
            progress = 0f
            scope.launch {
                try {
                    val r = soUpload(c, "/api/shorts", f, mapOf("caption" to caption.trim())) { v -> progress = v }
                    r.optJSONObject("short")?.let { s -> runCatching { Clip.upload(s) }.getOrNull()?.let { clip -> Feeds.uploads.items.removeAll { it.key == clip.key }; Feeds.uploads.items.add(0, clip); Feeds.startKey = clip.key } }
                    toast(c, "Uploaded ✓"); Feeds.who = null; Feeds.tab = ShortsTab.Uploads; Nav.back()
                } catch (e: Exception) {
                    toast(c, if (Api.isOffline(e)) "Connection lost. Check Uploads before trying again, so it isn't posted twice." else Api.friendly(e))
                } finally { progress = null }
            }
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopBar("Upload a Short", onBack = { if (progress == null) Nav.back() })
            if (Session.me == null) { SoEmpty(Icons.Filled.Upload, "Sign in to upload Shorts", action = "Sign in", onAction = { Nav.go(Tab.Profile) }); return@Column }
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)).tap(RoundedCornerShape(14.dp)) { if (progress == null) pick.launch("video/*") }
                    .background(if (file == null) MaterialTheme.colorScheme.surfaceVariant else Green.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (file == null) Icons.Filled.VideoLibrary else Icons.Filled.CheckCircle, null, tint = Green, modifier = Modifier.size(44.dp))
                        Text(if (file == null) "Choose a video (up to 100 MB)" else "Video ready" + (if (sizeMb > 0) " · %.1f MB".format(sizeMb) else "") + " · tap to change",
                            fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(caption, { caption = it.take(300) }, Modifier.fillMaxWidth().heightIn(min = 90.dp), placeholder = { Text("Write a caption (optional)") },
                    textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content))
                Spacer(Modifier.height(16.dp))
                progress?.let { v ->
                    LinearProgressIndicator(progress = { v }, modifier = Modifier.fillMaxWidth())
                    Text(if (v >= 1f) "Processing…" else "Uploading ${(v * 100).toInt()}%", fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(12.dp))
                }
                PrimaryButton(if (file == null) "Choose video" else "Upload", { send() }, Modifier.fillMaxWidth().firstFocus(), enabled = progress == null)
            }
        }
    }
}

/** Deletes one of your own uploads (or any, as an admin). */
fun soDeleteUpload(ctx: Context, s: Clip) {
    appScope.launch {
        runCatching { Api.delete("/api/shorts?id=" + soEnc(s.key)) }
            .onSuccess { Feeds.uploads.items.removeAll { it.key == s.key }; toast(ctx, "Deleted") }
            .onFailure { toast(ctx, Api.friendly(it)) }
    }
}

/** Shorts' one top row: search, the feeds (sideways scroll if they don't fit), topics (small screens), upload; then the creator / topic line. */
@Composable
fun SoShortsTopBar(tabs: List<ShortsTab>, tab: ShortsTab, who: String?, topic: String, smallScreen: Boolean, modifier: Modifier) {
        // One row on top: search, the feeds (scroll sideways if they don't fit), topics (small screens), upload.
    Column(modifier.fillMaxWidth()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = .45f), Color.Transparent)))) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Filled.Search, "Search Shorts", { Nav.open(SoSearchPage(4)) }, tint = Color.White, size = if (smallScreen) 34.dp else 40.dp)
                // The feeds always fit at 480 px (For You · Following · Uploads · Saved); on a small screen the topics
                // button rides at the end of the same row instead of taking room from the names.
                androidx.compose.foundation.lazy.LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically) {
                    items(tabs.size) { ti ->
                        val t = tabs[ti]
                        Column(Modifier.tap(RoundedCornerShape(14.dp)) { Feeds.tab = t; Feeds.who = null }.padding(horizontal = if (smallScreen) 5.dp else 8.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(t.label, color = if (t == tab) Color.White else Color.White.copy(alpha = .7f), fontWeight = if (t == tab) FontWeight.Bold else FontWeight.Medium,
                                fontSize = if (smallScreen) 13.sp else 15.sp, maxLines = 1, softWrap = false,
                                style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = .6f), blurRadius = 4f)))
                            Box(Modifier.padding(top = 2.dp).size(width = 18.dp, height = 2.dp).background(if (t == tab) Color.White else Color.Transparent, RoundedCornerShape(1.dp)))
                        }
                    }
                    if (tab == ShortsTab.ForYou && smallScreen && who == null) item {
                        Box {
                            var topicsOpen by remember { mutableStateOf(false) }
                            IconBtn(Icons.Filled.Tune, "Topics", { topicsOpen = true }, tint = if (topic == "All") Color.White else Gold, size = 34.dp)
                            DropdownMenu(topicsOpen, { topicsOpen = false }) {
                                for ((v, label) in SO_SHORT_TOPICS) DropdownMenuItem(text = { Text((if (v == topic) "✓ " else "   ") + label) }, onClick = { SoShortTopics.topic = v; topicsOpen = false })
                            }
                        }
                    }
                }
                IconBtn(Icons.Filled.AddCircle, "Upload a Short", { Nav.open(SoUploadShortPage()) }, tint = Color.White, size = if (smallScreen) 36.dp else 40.dp)
            }
            if (who != null) Row(Modifier.padding(start = 12.dp, bottom = 4.dp).tap(RoundedCornerShape(16.dp)) { Feeds.who = null }
                .background(Color.White.copy(alpha = .2f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("@" + Feeds.whoName.ifBlank { "creator" }, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Icon(Icons.Filled.Close, "Show everyone", tint = Color.White, modifier = Modifier.padding(start = 6.dp).size(16.dp))
            }
            else if (tab == ShortsTab.ForYou && !smallScreen) SoChips(SO_SHORT_TOPICS.map { it.second }, SO_SHORT_TOPICS.indexOfFirst { it.first == topic }.coerceAtLeast(0),
                { SoShortTopics.topic = SO_SHORT_TOPICS[it].first }, dark = true)
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SoShortCommentsSheet(s: Clip, onDismiss: () -> Unit, onCount: (Int) -> Unit) {
    val c = ctx()
    val scope = rememberCoroutineScope()
    val list = remember(s.key) { mutableStateListOf<Comment>() }
    var loading by remember { mutableStateOf(true) }
    var canDelete by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    suspend fun load() {
        loading = true
        try {
            if (s.fromSrc) {
                val j = Api.get("/api/src-comments?vid=" + URLEncoder.encode(s.key, "UTF-8"))
                canDelete = j.optBoolean("can_delete")
                list.clear(); list.addAll(j.optJSONArray("comments").objects().map { Comment(it.str("id"), it.str("user_id"), it.str("nickname"), Api.abs(it.strOrNull("photo")), it.str("text"),
                    it.optLong("t").takeIf { t -> t > 0 }?.let { t -> java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(t)) }, it.optBoolean("mine")) })
            } else {
                val j = Api.get("/api/shorts/comments?short_id=" + URLEncoder.encode(s.key, "UTF-8"))
                list.clear(); list.addAll(j.optJSONArray("comments").objects().reversed().map { Comment(it.str("id"), it.str("user_id"), it.str("nickname"), Api.abs(it.strOrNull("photo_url")), it.str("text"), it.strOrNull("created_at"), it.str("user_id") == Session.me?.id) })
            }
            onCount(list.size)
        } catch (e: Exception) { toast(c, Api.friendly(e)) } finally { loading = false }
    }
    LaunchedEffect(s.key) { load() }
    fun post() {
        val t = text.trim(); if (t.isEmpty()) return
        text = ""
        scope.launch {
            try {
                if (s.fromSrc) Api.post("/api/src-comments", JSONObject().put("vid", s.key).put("text", t))
                else Api.post("/api/shorts/comments", JSONObject().put("short_id", s.key).put("text", t))
                load()
            } catch (e: Exception) { toast(c, Api.friendly(e)); text = t }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(min = 200.dp).navigationBarsPadding().imePadding().padding(bottom = 8.dp)) {
            Text(if (list.size == 1) "1 comment" else "${list.size} comments", Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1f, fill = false).heightIn(max = 380.dp)) {
                if (loading && list.isEmpty()) Loading(Modifier.fillMaxWidth().height(120.dp))
                else if (list.isEmpty()) SoEmpty(Icons.Filled.ChatBubbleOutline, "No comments yet. Be the first.")
                else LazyColumn {
                    items(list, key = { it.id }) { cm ->
                        Row(Modifier.fillMaxWidth().tap(RoundedCornerShape(0.dp)) {}.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Avatar(cm.photo, cm.nick, 36.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(cm.nick, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content),
                                        color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("  " + ago(cm.at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                Text(cm.text, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content, textAlign = TextAlign.Left))
                            }
                            if (s.fromSrc && (cm.mine || canDelete)) IconBtn(Icons.Filled.Delete, "Delete", {
                                scope.launch { runCatching { Api.delete("/api/src-comments?vid=" + URLEncoder.encode(s.key, "UTF-8") + "&id=" + URLEncoder.encode(cm.id, "UTF-8")) }; load() } }, size = 32.dp)
                        }
                    }
                }
            }
            if (Session.me == null) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sign in to comment", Modifier.weight(1f)); PrimaryButton("Sign in", { onDismiss(); Nav.go(Tab.Profile) })
                }
            } else Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { if (it.length <= 300) text = it }, Modifier.weight(1f).firstFocus(), placeholder = { Text("Add a comment") }, maxLines = 3,
                    textStyle = TextStyle(textDirection = TextDirection.Content), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { post() }))
                IconBtn(Icons.AutoMirrored.Filled.Send, "Post", { post() }, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

