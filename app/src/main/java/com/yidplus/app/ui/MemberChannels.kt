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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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

// The members' own channels (the site's Channels tab): directory, channel page, follow, posts with likes / comments /
// share, new post, follow requests.

// ───────────────────────── Members' channels (the site's Channels tab) ─────────────────────────

data class SoSiteChannel(val ownerId: String, val name: String, val photo: String?, val verified: Boolean, val bio: String, val followers: Int, val lastPostAt: String?) {
    companion object {
        fun from(j: JSONObject) = SoSiteChannel(j.str("owner_id"), j.str("nickname").ifBlank { "Member" }, Api.abs(j.strOrNull("photo_url")),
            j.optInt("verified") == 1 || j.optBoolean("verified"), j.str("bio"), j.optInt("followers"), j.strOrNull("last_post_at"))
        fun list(j: JSONObject) = j.optJSONArray("channels").objects().map(::from).filter { it.ownerId.isNotBlank() }.distinctBy { it.ownerId }
    }
}

/** One member channel in a list: photo, name + verified, last post time, followers · bio (like the site's Channels list). */
@Composable
fun SoSiteChannelRow(ch: SoSiteChannel, first: Boolean = false) {
                Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).restoreFocus("sc:" + ch.ownerId)
        .then(if (first) Modifier.firstFocus() else Modifier)
        .tap(RoundedCornerShape(0.dp)) { Nav.lastOpened = "sc:" + ch.ownerId; Nav.open(SoUserChannelPage(ch.ownerId, ch.name)) }
        .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SoThumb(ch.photo, ch.name, 50.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Name (left-aligned even in Yiddish) + verified mark together; the time keeps its place at the end.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(ch.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content, textAlign = TextAlign.Left),
                        color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (ch.verified) Icon(Icons.Filled.Verified, "Verified", tint = Color(0xFF1D9BF0), modifier = Modifier.padding(start = 4.dp).size(17.dp))
                }
                if (ch.lastPostAt != null) Text(ago(ch.lastPostAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
            }
            val bio = ch.bio.replace('\n', ' ').trim()
            Text((if (ch.followers == 1) "1 follower" else "${soNum(ch.followers)} followers") + if (bio.isNotEmpty()) " · $bio" else "",
                Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content, textAlign = TextAlign.Left),
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The members' channels directory: most followed first, or the most recently active. */
@Composable
fun SoSiteChannelsList(query: String, sort: Int) {
    val scope = rememberCoroutineScope()
    val res = remember(Session.me?.id) { Store.res("/api/channels?limit=400", SoSiteChannel::list) }
    LaunchedEffect(res, SoRefresh.tick) { res.load(force = SoRefresh.tick > 0) }
    val found = remember { mutableStateListOf<SoSiteChannel>() }
    LaunchedEffect(query) {
        val q = query.trim(); if (q.length < 2) { found.clear(); return@LaunchedEffect }
        delay(300); runCatching { val l = SoSiteChannel.list(Api.get("/api/channels?limit=50&search=" + soEnc(q))); found.clear(); found.addAll(l) }
    }
    val all = res.data
    val q = query.trim()
    val shown = if (q.length >= 2) (found + all.orEmpty().filter { it.name.contains(q, true) || it.bio.contains(q, true) }).distinctBy { it.ownerId }
        else when (sort) { 1 -> all.orEmpty().sortedByDescending { parseTime(it.lastPostAt) ?: 0L }; 2 -> all.orEmpty().filter { it.verified }; else -> all.orEmpty() }
    when {
        all == null && res.error != null -> Problem(res.error!!, res.offline, { scope.launch { res.load(true) } })
        all == null -> SoSkeleton()
        shown.isEmpty() -> SoEmpty(Icons.Filled.Search, if (q.isNotEmpty()) "No channels for \u201c$q\u201d" else "No channels yet")
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.ownerId }) { ch ->
                SoSiteChannelRow(ch, ch == shown.first())
                SoDivider(80.dp)
            }
        }
    }
}

data class SoSitePost(val id: String, val userId: String, val caption: String, val media: String?, val likes: Int, val comments: Int, val liked: Boolean, val at: String?,
                      val views: Int = 0, val author: String = "", val authorPhoto: String? = null, val verified: Boolean = false) {
    val isVideo get() = media != null && soIsVideo(media)
    companion object {
        fun from(j: JSONObject): SoSitePost {
            val content = j.str("content")
            val media = soPostMedia(content)
            val caption = j.str("caption").ifBlank { if (media == null) content else "" }
            return SoSitePost(j.str("id"), j.str("user_id"), caption, media, j.optInt("likes"), j.optInt("comments"), j.optBoolean("liked") || j.optInt("liked") == 1, j.strOrNull("created_at"),
                j.optInt("views", j.optInt("view_count")), j.str("nickname").ifBlank { j.str("username") }, Api.abs(j.strOrNull("photo_url")), j.optInt("verified") == 1 || j.optBoolean("verified"))
        }
    }
}
data class SoWallShort(val id: String, val caption: String, val video: String, val likes: Int, val views: Int, val at: String?)

/** A member's channel: picture, bio, numbers, Follow (or Request), Share, and its Posts and Shorts. */
data class SoUserChannelPage(val ownerId: String, val name: String) : PageScreen() {
    override val perfName = "UserChannel"
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var ch by remember { mutableStateOf<JSONObject?>(null) }
        var err by remember { mutableStateOf<String?>(null) }
        var locked by remember { mutableStateOf(false) }
        var following by remember { mutableStateOf(false) }
        var reqStatus by remember { mutableStateOf<String?>(null) }
        val posts = remember { mutableStateListOf<SoSitePost>() }
        val shorts = remember { mutableStateListOf<SoWallShort>() }
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var busy by remember { mutableStateOf(false) }
        var commentsFor by remember { mutableStateOf<SoSitePost?>(null) }
        var retry by remember { mutableIntStateOf(0) }
        var menu by remember { mutableStateOf(false) }
        var report by remember { mutableStateOf(false) }
        var askPrivacy by remember { mutableStateOf(false) }
        var pulling by remember { mutableStateOf(false) }
        val music = remember { mutableStateListOf<Track>() }
        var musicLoaded by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { tab = runCatching { Api.app.getSharedPreferences("social", 0).getInt("ypChTab", tab) }.getOrDefault(tab) }
        fun pickTab(t: Int) { tab = t; runCatching { Api.app.getSharedPreferences("social", 0).edit().putInt("ypChTab", t).apply() } }
        val mine = Session.me?.id == ownerId
        // The saved copy shows at once (no skeleton when you've been here before); the site's answer replaces it quietly.
        val cacheKey = Store.key("sc|" + ownerId)
        fun applyData(r: JSONObject, p: List<JSONObject>?) {
            ch = r.optJSONObject("channel"); locked = r.optBoolean("locked"); following = r.optBoolean("is_following"); reqStatus = r.strOrNull("request_status")
            shorts.clear(); r.optJSONArray("wall").objects().filter { it.str("wall_type") == "short" }.forEach { s ->
                Api.abs(s.strOrNull("media_url"))?.let { shorts.add(SoWallShort(s.str("id"), s.str("caption"), it, s.optInt("likes"), s.optInt("views"), s.strOrNull("created_at"))) } }
            if (!locked) {
                val pl = p ?: r.optJSONArray("wall").objects().filter { it.str("wall_type") == "post" }
                posts.clear(); posts.addAll(pl.map(SoSitePost::from).distinctBy { it.id })
            }
        }
        LaunchedEffect(ownerId, retry, SoRefresh.tick) {
            if (ch == null) withContext(Dispatchers.IO) { Disk.read(cacheKey) }?.let { d ->
                runCatching { d.optJSONObject("r")?.let { r -> applyData(r, d.optJSONArray("posts")?.objects()); Perf.shown("UserChannel", fromCache = true) } }
            }
            try {
                val r = Api.get("/api/channels?owner_id=" + soEnc(ownerId))
                val p = if (r.optBoolean("locked")) null else runCatching { Api.get("/api/posts?user_id=" + soEnc(ownerId)).optJSONArray("posts").objects() }.getOrNull()
                applyData(r, p)
                withContext(Dispatchers.IO) { runCatching { Disk.write(cacheKey, JSONObject().put("r", r).put("posts", JSONArray().also { a -> p?.forEach { a.put(it) } })) } }
                if (!following && !mine && Session.me != null && reqStatus == null) runCatching {
                    val k = Api.get("/api/channel-follows?channel_owner_id=" + soEnc(ownerId) + "&check=1"); following = k.optBoolean("is_follower"); reqStatus = k.strOrNull("request_status") }
                err = null
            } catch (e: Exception) { if (ch == null) err = Api.friendly(e) }
            Perf.shown("UserChannel")
        }
        LaunchedEffect(tab, ownerId) { if (tab == 2 && !musicLoaded) { runCatching { val l = Track.list(Api.get("/api/music?owner_id=" + soEnc(ownerId))); music.clear(); music.addAll(l) }; musicLoaded = true } }
        suspend fun reload() {
            runCatching {
                val r = Api.get("/api/channels?owner_id=" + soEnc(ownerId))
                val p = if (r.optBoolean("locked")) null else runCatching { Api.get("/api/posts?user_id=" + soEnc(ownerId)).optJSONArray("posts").objects() }.getOrNull()
                applyData(r, p)
            }.onFailure { toast(c, Api.friendly(it)) }
        }
        val title = ch?.str("nickname")?.ifBlank { null } ?: name.ifBlank { "Channel" }
        val photo = Api.abs(ch?.strOrNull("photo_url"))
        fun follow() {
            if (Session.me == null) { toast(c, "Sign in to follow"); Nav.go(Tab.Profile); return }
            if (busy) return; busy = true
            scope.launch {
                try {
                    if (following || reqStatus == "pending") {
                        Api.delete("/api/channel-follows?channel_owner_id=" + soEnc(ownerId)); if (following) toast(c, "Unfollowed"); following = false; reqStatus = null
                        ch?.let { it.put("followers", maxOf(0, it.optInt("followers") - 1)) }
                    } else {
                        val st = Api.post("/api/channel-follows", JSONObject().put("channel_owner_id", ownerId)).str("status")
                        if (st == "pending") { reqStatus = "pending"; toast(c, "Follow request sent! Waiting for approval.") } else { val wasLocked = locked; following = true; ch?.let { it.put("followers", it.optInt("followers") + 1) }
                            toast(c, if (wasLocked) "You are now following this channel!" else "Following @$title"); if (wasLocked) reload() }
                    }
                } catch (e: Exception) { toast(c, Api.friendly(e)) } finally { busy = false }
            }
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopBar(title, onBack = { Nav.back() }) {
                IconBtn(Icons.Filled.Share, "Share", { share(c, "@$title on YID PLUS\n" + Api.BASE + "/?channel=" + soEnc(ownerId)) }, tint = LocalContentColor.current)
                Box {
                    IconBtn(Icons.Filled.MoreVert, "Channel options", { menu = true }, tint = LocalContentColor.current)
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Share channel") }, leadingIcon = { Icon(Icons.Filled.Share, null) }, onClick = { menu = false; share(c, "@$title on YID PLUS\n" + Api.BASE + "/?channel=" + soEnc(ownerId)) })
                        DropdownMenuItem(text = { Text("Copy link") }, leadingIcon = { Icon(Icons.Filled.Link, null) }, onClick = { menu = false; soCopy(c, Api.BASE + "/?channel=" + soEnc(ownerId)) })
                        DropdownMenuItem(text = { Text("View profile") }, leadingIcon = { Icon(Icons.Filled.Person, null) }, onClick = { menu = false; Nav.open(HmProfilePage(ownerId, title)) })
                        if (!mine) DropdownMenuItem(text = { Text("Message @$title") }, leadingIcon = { Icon(Icons.Filled.ChatBubbleOutline, null) }, onClick = { menu = false; hmOpenDm(c, scope, ownerId, title, Api.abs(ch?.strOrNull("photo_url"))) })
                        if (!mine && Session.me != null) DropdownMenuItem(text = { Text("Report", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Filled.Flag, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; report = true })
                    }
                }
            }
            if (ch == null && err != null) { Problem(err!!, false, { retry++ }); return@Column }
            if (ch == null) { SoSkeleton(rows = 4); return@Column }
            val j = ch!!
            PullToRefreshBox(isRefreshing = pulling, onRefresh = { scope.launch { pulling = true; reload(); pulling = false } }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "head") {
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                        val banner = Api.abs(j.strOrNull("banner_url"))
                        Box(Modifier.fillMaxWidth().height(96.dp).background(Brush.linearGradient(listOf(Green, Color(0xFF2E7D9A))))) {
                            if (banner != null) AsyncImage(banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Row(Modifier.padding(horizontal = 16.dp).offset(y = (-30).dp), verticalAlignment = Alignment.Bottom) {
                            Box(Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(3.dp)) { SoThumb(photo, title, 74.dp) }
                            Spacer(Modifier.weight(1f))
                            if (!mine) {
                                SoPill("Message", false, { hmOpenDm(c, scope, ownerId, title, photo) }, icon = Icons.Filled.ChatBubbleOutline)
                                Spacer(Modifier.width(8.dp))
                                SoPill(when { following -> "Following ✓"; reqStatus == "pending" -> "Requested"; reqStatus == "rejected" -> "Declined"; else -> "Follow" }, !following && reqStatus != "pending", { follow() },
                                    Modifier.firstFocus(), icon = if (!following && reqStatus != "pending") Icons.Filled.PersonAdd else null, enabled = !busy)
                            }
                            else SoPill("New post", true, { Nav.open(SoNewPostPage()) }, Modifier.firstFocus(), icon = Icons.Filled.Edit)
                        }
                        Column(Modifier.padding(start = 16.dp, end = 16.dp).offset(y = (-22).dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                    style = TextStyle(textDirection = TextDirection.Content))
                                if (j.optInt("verified") == 1 || j.optBoolean("verified")) Icon(Icons.Filled.Verified, "Verified", tint = Color(0xFF1D9BF0), modifier = Modifier.padding(start = 4.dp).size(18.dp))
                                if (j.str("privacy") == "followers_only") Icon(Icons.Filled.Lock, "Followers only", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                            }
                            Text("@$title", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (j.str("bio").isNotBlank()) Text(j.str("bio"), fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp), style = TextStyle(textDirection = TextDirection.Content))
                            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Stat(soNum(j.optInt("followers")), "Followers") { Nav.open(HmFollowListPage(ownerId, title, true)) }
                                Stat(soNum(j.optInt("following")), "Following") { Nav.open(HmFollowListPage(ownerId, title, false)) }
                                Stat(soNum(j.optInt("total_views")), "Views", null); Stat(soNum(j.optInt("shorts_count").let { if (it > 0) it else shorts.size }), "Shorts") { pickTab(1) }
                            }
                            if (mine) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val priv = j.str("privacy") == "followers_only"
                                SoPill(if (priv) "Private · tap to change" else "Public · tap to change", false, { askPrivacy = true }, icon = if (priv) Icons.Filled.Lock else Icons.Filled.Public)
                                if (askPrivacy) AlertDialog(onDismissRequest = { askPrivacy = false },
                                    title = { Text(if (priv) "Make this channel PUBLIC?" else "Make this channel PRIVATE?") },
                                    text = { Text(if (priv) "Everyone will be able to see your content." else "Only followers will see your content. New followers must be approved by you.") },
                                    dismissButton = { TextButton({ askPrivacy = false }) { Text("Cancel") } },
                                    confirmButton = { TextButton({ askPrivacy = false
                                    scope.launch {
                                        val nv = if (priv) "public" else "followers_only"
                                        runCatching { Api.put("/api/channels", JSONObject().put("owner_id", ownerId).put("privacy", nv)) }
                                            .onSuccess { ch = JSONObject(j.toString()).put("privacy", nv); toast(c, if (nv == "public") "Channel is now public — everyone can see your content" else "Channel is now private — only followers can see your content") }
                                            .onFailure { toast(c, Api.friendly(it)) }
                                    } }) { Text("OK") } })
                                SoPill("Requests", false, { Nav.open(SoChannelFollowRequestsPage(ownerId)) }, icon = Icons.Filled.PersonAdd)
                            }
                        }
                    }
                }
                item(key = "tabs") {
                    Surface(color = MaterialTheme.colorScheme.surface) { SoChips(listOf("Posts (${posts.size})", "Shorts (${shorts.size})", "Music", "About"), tab, { pickTab(it) }) }
                    HorizontalDivider(thickness = 0.5.dp)
                }
                if (locked) item(key = "locked") {
                    SoEmpty(Icons.Filled.Lock, when (reqStatus) { "pending" -> "Request pending… You'll see the posts once it's approved."; "rejected" -> "Your request was declined"
                        else -> "This channel is private. Follow this channel to see their content" },
                        action = if (reqStatus == "pending" || reqStatus == "rejected") null else "Request to Follow", onAction = { follow() })
                } else if (tab == 0) {
                    if (posts.isEmpty()) item(key = "noposts") { SoEmpty(Icons.Filled.Article, if (mine) "Write your first post" else "No posts yet", action = if (mine) "New post" else null, onAction = { Nav.open(SoNewPostPage()) }) }
                    // Every photo / video of the channel in one viewer: swipe from one to the next, like WhatsApp.
                    val gal = posts.mapNotNull { q -> q.media?.let { GItem(it, q.isVideo, null, q.caption.take(60)) } }
                    items(posts, key = { "p" + it.id }) { p ->
                        SoSitePostCard(p, title, photo, onChange = { n -> val i = posts.indexOfFirst { it.id == n.id }; if (i >= 0) posts[i] = n },
                            onComments = { commentsFor = p }, onDeleted = { posts.removeAll { it.id == p.id } }, gallery = gal)
                    }
                } else if (tab == 2) {
                    if (music.isEmpty()) item(key = "nomusic") { if (musicLoaded) SoEmpty(Icons.Filled.MusicNote, "No tracks yet") else SoSkeleton(rows = 2) }
                    itemsIndexed(music, key = { _, t -> "m" + t.id }) { i, t -> HmTrackRow(t) { Music.play(c, music.toList(), i) }; SoDivider(74.dp) }
                } else if (tab == 3) {
                    item(key = "about") {
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                            Text("About", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Text(j.str("bio").ifBlank { "No bio yet." }, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp),
                                color = if (j.str("bio").isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, style = TextStyle(textDirection = TextDirection.Content))
                            Text("${posts.size} posts · ${shorts.size} shorts", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                            j.strOrNull("created_at")?.let { Text("On YID PLUS since " + it.take(10), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp)) }
                            if (mine && j.str("privacy") == "followers_only") SoPill("Follow requests", false, { Nav.open(SoChannelFollowRequestsPage(ownerId)) }, Modifier.padding(top = 12.dp), icon = Icons.Filled.PersonAdd)
                            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SoPill("Copy link", false, { soCopy(c, Api.BASE + "/?channel=" + soEnc(ownerId)) }, icon = Icons.Filled.Link)
                                SoPill("Profile", false, { Nav.open(HmProfilePage(ownerId, title)) }, icon = Icons.Filled.Person)
                            }
                        }
                    }
                } else {
                    if (shorts.isEmpty()) item(key = "noshorts") { SoEmpty(Icons.Filled.Movie, "No Shorts yet") }
                    items(shorts.chunked(3), key = { "s" + it.first().id }) { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (s in row) Box(Modifier.weight(1f).aspectRatio(9f / 14f).tap(RoundedCornerShape(2.dp)) { soOpenUploadShort(s.id) }) {
                                SoVideoPoster(null, s.caption, 0, Modifier.fillMaxSize())
                                Row(Modifier.align(Alignment.BottomStart).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Text(" " + soNum(s.views), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    if (s.likes > 0) { Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.padding(start = 6.dp).size(12.dp)); Text(" " + soNum(s.likes), color = Color.White, fontSize = 12.sp) }
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }
                item(key = "end") { Spacer(Modifier.height(24.dp)) }
            }
            }
        }
        if (report) HmReportDialog(ownerId, title, true) { report = false }
        commentsFor?.let { p -> SoPostCommentsSheet(p, onDismiss = { commentsFor = null }, onCount = { n -> val i = posts.indexOfFirst { it.id == p.id }; if (i >= 0) posts[i] = posts[i].copy(comments = n) }) }
    }
}

@Composable
private fun Stat(n: String, label: String, onClick: (() -> Unit)? = null) {
    Column(Modifier.then(if (onClick != null) Modifier.tap(RoundedCornerShape(8.dp), onClick = onClick) else Modifier).padding(horizontal = 4.dp, vertical = 2.dp)) {
        Text(n, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One post on a member's channel: who and when, the words, the photo / video, then like, comment, share (and delete). */
@Composable
fun SoSitePostCard(p: SoSitePost, name: String, photo: String?, onChange: (SoSitePost) -> Unit, onComments: () -> Unit, onDeleted: () -> Unit, gallery: List<GItem>? = null,
                   onAuthor: (() -> Unit)? = null) {
    val c = ctx(); val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf(false) }
    val canDelete = Session.me?.id == p.userId || SoUser.isAdmin
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).then(if (onAuthor != null) Modifier.tap(RoundedCornerShape(8.dp), onClick = onAuthor) else Modifier), verticalAlignment = Alignment.CenterVertically) {
            SoThumb(photo, name, 40.dp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false), style = TextStyle(textDirection = TextDirection.Content))
                    if (p.verified) Icon(Icons.Filled.Verified, "Verified", tint = Color(0xFF1D9BF0), modifier = Modifier.padding(start = 3.dp).size(15.dp))
                }
                Text("@$name · " + ago(p.at), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            }
            if (canDelete) IconBtn(Icons.Filled.DeleteOutline, "Delete post", { ask = true }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val emojiOnly = remember(p.caption) { hmEmojiOnly(p.caption) }
        if (p.caption.isNotBlank()) Text(p.caption, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).tap(RoundedCornerShape(4.dp)) { expanded = !expanded },
            fontSize = if (emojiOnly) 44.sp else 16.sp, lineHeight = if (emojiOnly) 52.sp else 22.sp, maxLines = if (expanded) Int.MAX_VALUE else 8, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
        val tags = remember(p.caption) { hmHashtags(p.caption) }
        if (tags.isNotEmpty()) LazyRow(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(tags) { t -> Text(t, Modifier.heightIn(min = 32.dp).tap(RoundedCornerShape(16.dp)) { Nav.open(SoSearchPage(0, t)) }
                .background(MaterialTheme.colorScheme.primary.copy(alpha = .10f), RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        }
        p.media?.let { m ->
            val maxH = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.6f).dp
            Box(Modifier.fillMaxWidth().padding(top = 4.dp).tap(RoundedCornerShape(0.dp)) { openGallery(gallery ?: listOf(GItem(m, p.isVideo, null, p.caption.take(60))), m) }) {
                if (p.isVideo) SoVideoPoster(null, p.caption.take(60), 0, Modifier.fillMaxWidth().height(200.dp))
                else AsyncImage(ImageRequest.Builder(c).data(m).crossfade(150).build(), "Photo", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = maxH).background(MaterialTheme.colorScheme.surfaceVariant))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            SoAction(if (p.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (p.likes > 0) soNum(p.likes) else "Like", if (p.liked) Color(0xFFE53958) else null, "Like") {
                if (Session.me == null) { toast(c, "Sign in to like"); return@SoAction }
                val on = !p.liked; onChange(p.copy(liked = on, likes = maxOf(0, p.likes + if (on) 1 else -1)))
                scope.launch { runCatching { Api.put("/api/posts", JSONObject().put("id", p.id).put("like", on)) }.onSuccess { onChange(p.copy(liked = on, likes = it.optInt("likes"))) }
                    .onFailure { onChange(p); toast(c, Api.friendly(it)) } }
            }
            SoAction(Icons.Filled.ChatBubbleOutline, if (p.comments > 0) soNum(p.comments) else "Comment", null, "Comment", onComments)
            SoAction(Icons.Filled.Share, "Share", null) { share(c, (if (p.caption.isNotBlank()) p.caption.take(140) + "\n" else "") + Api.BASE + "/?post=" + soEnc(p.id)) }
            Spacer(Modifier.weight(1f))
            if (p.views > 0) { Icon(Icons.Filled.Visibility, "Views", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(" " + soNum(p.views), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp)) }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
    }
    if (ask) AlertDialog(onDismissRequest = { ask = false }, title = { Text("Delete this post?") },
        confirmButton = { TextButton({ ask = false; scope.launch { runCatching { Api.delete("/api/posts?id=" + soEnc(p.id)) }.onSuccess { onDeleted(); toast(c, "Deleted") }.onFailure { toast(c, Api.friendly(it)) } } }) { Text("Delete") } },
        dismissButton = { TextButton({ ask = false }) { Text("Cancel") } })
}

@Composable
fun SoAction(icon: ImageVector, label: String, tint: Color?, desc: String = label, onClick: () -> Unit) {
    val col = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.heightIn(min = 44.dp).tap(RoundedCornerShape(22.dp), onClick = onClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, desc, tint = col, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 14.sp, color = col, fontWeight = FontWeight.Medium)
    }
}

/** A post's comments: read, write, like and delete (your own, or any as an admin). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoPostCommentsSheet(p: SoSitePost, onDismiss: () -> Unit, onCount: (Int) -> Unit) {
    val c = ctx(); val scope = rememberCoroutineScope()
    val list = remember(p.id) { mutableStateListOf<JSONObject>() }
    var loading by remember { mutableStateOf(true) }
    var text by remember { mutableStateOf("") }
    var delAsk by remember { mutableStateOf<JSONObject?>(null) }
    suspend fun load() {
        loading = true
        runCatching { val l = Api.get("/api/posts/comments?post_id=" + soEnc(p.id)).optJSONArray("comments").objects(); list.clear(); list.addAll(l); onCount(l.size) }
            .onFailure { toast(c, Api.friendly(it)) }
        loading = false
    }
    LaunchedEffect(p.id) { load() }
    delAsk?.let { cm -> AlertDialog(onDismissRequest = { delAsk = null }, title = { Text("Delete this comment?") },
        confirmButton = { TextButton({ delAsk = null; scope.launch { runCatching { Api.delete("/api/posts/comments?id=" + soEnc(cm.str("id"))) }
            .onSuccess { list.removeAll { it.str("id") == cm.str("id") }; onCount(list.size); toast(c, "Deleted") }.onFailure { toast(c, Api.friendly(it)) } } }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ delAsk = null }) { Text("Cancel") } }) }
    SoCommentsPanel(if (list.isEmpty()) "Comments" else if (list.size == 1) "1 comment" else "${list.size} comments", onDismiss) {
        run {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (loading && list.isEmpty()) SoSkeleton(rows = 3, avatar = 34.dp)
                else if (list.isEmpty()) SoEmpty(Icons.Filled.ChatBubbleOutline, "No comments yet. Be the first.")
                else LazyColumn {
                    items(list, key = { it.str("id") }) { cm ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SoThumb(Api.abs(cm.strOrNull("photo_url")), cm.str("nickname"), 36.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Row { Text(cm.str("nickname"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    Text("  " + ago(cm.strOrNull("created_at")), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Text(cm.str("text"), fontSize = 15.sp, style = TextStyle(textDirection = TextDirection.Content))
                            }
                            val liked = cm.optBoolean("my_liked")
                            Column(Modifier.tap(CircleShape) {
                                if (Session.me == null) { toast(c, "Sign in to like"); return@tap }
                                val idx = list.indexOfFirst { it.str("id") == cm.str("id") }
                                    if (idx >= 0) list[idx] = JSONObject(cm.toString()).put("my_liked", !liked).put("likes", maxOf(0, cm.optInt("likes") + if (liked) -1 else 1))
                                    scope.launch { runCatching { Api.put("/api/posts/comments", JSONObject().put("comment_id", cm.str("id"))) }
                                        .onSuccess { r -> val k = list.indexOfFirst { it.str("id") == cm.str("id") }; if (k >= 0 && r.has("likes")) list[k] = JSONObject(list[k].toString()).put("my_liked", r.optBoolean("liked")).put("likes", r.optInt("likes")) }
                                        .onFailure { val k = list.indexOfFirst { it.str("id") == cm.str("id") }; if (k >= 0) list[k] = cm; toast(c, Api.friendly(it)) } }
                            }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Like comment", tint = if (liked) Color(0xFFE53958) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                if (cm.optInt("likes") > 0) Text(cm.optInt("likes").toString(), fontSize = 11.sp)
                            }
                            if (cm.optBoolean("can_delete") || cm.str("user_id") == Session.me?.id) IconBtn(Icons.Filled.DeleteOutline, "Delete comment", { delAsk = cm }, size = 36.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (Session.me == null) Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sign in to comment", Modifier.weight(1f)); PrimaryButton("Sign in", { onDismiss(); Nav.go(Tab.Profile) })
            } else Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { if (it.length <= 500) text = it }, Modifier.weight(1f).firstFocus(), placeholder = { Text("Add a comment") }, maxLines = 3,
                    shape = RoundedCornerShape(22.dp), textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content))
                IconBtn(Icons.AutoMirrored.Filled.Send, "Send", {
                    val t = text.trim(); if (t.isEmpty()) return@IconBtn
                    text = ""
                    scope.launch { runCatching { Api.post("/api/posts/comments", JSONObject().put("post_id", p.id).put("text", t)) }.onSuccess { load() }.onFailure { toast(c, Api.friendly(it)); text = t } }
                }, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Write a post on your own channel: words, and a photo or a video if you like. */
class SoNewPostPage : PageScreen() {
    override val perfName = "NewPost"
    override fun equals(other: Any?) = other is SoNewPostPage
    override fun hashCode() = 13
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var text by rememberSaveable { mutableStateOf("") }
        var file by remember { mutableStateOf<Uri?>(null) }
        var progress by remember { mutableStateOf<Float?>(null) }
        val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) file = u }
        LaunchedEffect(SoQaPick.uri) { SoQaPick.uri?.let { file = it; SoQaPick.uri = null } }
        fun send() {
            if (progress != null) return
            if (text.isBlank() && file == null) { toast(c, "Write something or add a photo / video"); return }
            file?.let { f ->
                // The same limits the site's post box enforces (home.js checkFileSize): say so before the upload, not after.
                val vid = (c.contentResolver.getType(f) ?: "").startsWith("video") || (f.lastPathSegment ?: "").lowercase().let { n -> n.endsWith(".mp4") || n.endsWith(".mov") || n.endsWith(".webm") || n.endsWith(".3gp") }
                val len = runCatching { if (f.scheme == "file") java.io.File(f.path!!).length() else c.contentResolver.openAssetFileDescriptor(f, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
                if (len > (if (vid) 60L else 12L) * 1024 * 1024) { toast(c, if (vid) "That video is over 60 MB" else "That photo is over 12 MB"); return }
            }
            progress = 0f
            scope.launch {
                try {
                    val f = file
                    if (f != null) soUpload(c, "/api/posts", f, mapOf("caption" to text.trim())) { v -> progress = v }
                    else Api.post("/api/posts", JSONObject().put("caption", text.trim()).put("content", "")).soCheck()
                    toast(c, "Posted ✓"); SoRefresh.tick++; Nav.back()
                } catch (e: Exception) { toast(c, Api.friendly(e)) } finally { progress = null }
            }
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Photo / Video ride in the top bar too, so they're never under the keyboard on a small screen.
            TopBar("New post", onBack = { Nav.back() }) {
                if (Session.me != null) {
                    IconBtn(Icons.Filled.Image, "Add photo", { pick.launch("image/*") }, tint = Color.White)
                    IconBtn(Icons.Filled.Videocam, "Add video", { pick.launch("video/*") }, tint = Color.White)
                }
                TextButton({ send() }, enabled = progress == null) { Text("Post", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (progress == null) Color.White else Color.White.copy(alpha = .5f)) } }
            if (Session.me == null) { SoEmpty(Icons.Filled.Edit, "Sign in to post on your channel", action = "Sign in", onAction = { Nav.go(Tab.Profile) }); return@Column }
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedTextField(text, { text = it.take(2000) }, Modifier.fillMaxWidth().heightIn(min = 120.dp).firstFocus(always = true), placeholder = { Text("What's happening?") },
                    textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoPill("Photo", false, { pick.launch("image/*") }, icon = Icons.Filled.Image)
                    SoPill("Video", false, { pick.launch("video/*") }, icon = Icons.Filled.Videocam)
                }
                file?.let { f ->
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val isVid = (c.contentResolver.getType(f) ?: "").startsWith("video") || (f.lastPathSegment ?: "").lowercase().endsWith(".mp4")
                        if (isVid) Icon(Icons.Filled.Movie, null, Modifier.size(48.dp), tint = Green)
                        else AsyncImage(f, null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                        Text(if (isVid) "Video added" else "Photo added", Modifier.weight(1f).padding(horizontal = 10.dp), fontSize = 15.sp)
                        IconBtn(Icons.Filled.Close, "Remove", { file = null })
                    }
                }
                progress?.let { v -> Spacer(Modifier.height(16.dp)); LinearProgressIndicator(progress = { v }, modifier = Modifier.fillMaxWidth()); Text("Uploading ${(v * 100).toInt()}%", fontSize = 13.sp) }
            }
        }
    }
}

/** Your channel's follow requests (when it is followers-only): approve or reject. */
data class SoChannelFollowRequestsPage(val ownerId: String) : PageScreen() {
    override val perfName = "FollowRequests"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        val list = remember { mutableStateListOf<JSONObject>() }
        var loaded by remember { mutableStateOf(false) }
        var err by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(ownerId) {
            runCatching { Api.get("/api/channel-follows?channel_owner_id=" + soEnc(ownerId)).optJSONArray("requests").objects() }
                .onSuccess { list.clear(); list.addAll(it) }.onFailure { err = Api.friendly(it) }
            loaded = true
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopBar("Follow requests", onBack = { Nav.back() })
            when {
                !loaded -> SoSkeleton(rows = 4)
                err != null && list.isEmpty() -> SoEmpty(Icons.Filled.ErrorOutline, err!!)
                list.isEmpty() -> SoEmpty(Icons.Filled.PersonAdd, "No requests right now")
                else -> LazyColumn {
                    items(list, key = { it.str("id") }) { r ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            SoThumb(null, r.str("requester_nick"), 44.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(r.str("requester_nick").ifBlank { "Member" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(ago(r.strOrNull("created_at")), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            for ((a, label) in listOf("approve" to "Approve", "reject" to "Reject")) SoPill(label, a == "approve", {
                                scope.launch { runCatching { Api.put("/api/channel-follows", JSONObject().put("request_id", r.str("id")).put("action", a)) }
                                    .onSuccess { list.remove(r); toast(c, if (a == "approve") "Approved" else "Rejected") }.onFailure { toast(c, Api.friendly(it)) } }
                            }, Modifier.padding(start = 6.dp))
                        }
                        SoDivider(72.dp)
                    }
                }
            }
        }
    }
}


/** A caption that is only emoji (the site's isEmojiOnly) is shown big. */
fun hmEmojiOnly(s: String): Boolean {
    val t = s.trim(); if (t.isEmpty() || t.length > 16) return false
    var i = 0
    while (i < t.length) { val cp = t.codePointAt(i); val ok = Character.isWhitespace(cp) || cp >= 0x2190 || cp == 0x200D || cp == 0xFE0F || Character.getType(cp) == Character.SURROGATE.toInt()
        if (!ok || Character.isLetterOrDigit(cp)) return false; i += Character.charCount(cp) }
    return true
}
/** #hashtags in a caption (the site's _linkHashtags: latin, digits, _ and Hebrew letters). */
fun hmHashtags(s: String): List<String> = Regex("#[\\w\u0590-\u05FF\uFB1D-\uFB4F]+").findAll(s).map { it.value }.distinct().take(8).toList()
