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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle


import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems

// ───────────────────────── HM: the site's Home feed (greeting, Hebrew date, broadcast, composer, Hot Shorts, Popular Channels, community posts) ─────────────────────────

private val hmPostTimes = ArrayList<Long>()

/** The site's _renderHomeHero greeting by the hour. */
fun hmGreeting(h: Int) = when (h) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Good night" }

/** Weekday · Hebrew date (the site's Intl 'he-u-ca-hebrew'), e.g. "Thursday · כ״ט בחשוון תשפ״ז". */
fun hmHebrewDateLine(): String {
    val now = java.util.Date()
    val wd = java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(now)
    // ICU's Hebrew long date ("2 בכסלו 5787", like Chrome's Intl 'he-u-ca-hebrew'), kept as one right-to-left run so the numbers stay in place.
    val heb = runCatching { android.icu.text.DateFormat.getDateInstance(android.icu.text.DateFormat.LONG, android.icu.util.ULocale("he@calendar=hebrew")).format(now) }.getOrNull().orEmpty()
    return if (heb.isBlank()) wd else "$wd · \u2067$heb\u2069"
}

/** Friday all day and Shabbos day: Good Shabbos; Motzei Shabbos evening: Good week. */
fun hmShabbosBadge(): String {
    val cal = java.util.Calendar.getInstance(); val d = cal.get(java.util.Calendar.DAY_OF_WEEK); val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
    return when { d == java.util.Calendar.FRIDAY -> "🕯️ Good Shabbos!"; d == java.util.Calendar.SATURDAY && h < 19 -> "🕯️ Good Shabbos!"; d == java.util.Calendar.SATURDAY -> "✨ Good week!"; else -> "" }
}

@Composable
private fun HmSectionHead(title: String, onAll: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        if (onAll != null) TextButton(onAll) { Text("See all"); Icon(Sym.ChevronRight, null, Modifier.size(18.dp)) }
    }
}

@Composable
private fun HmTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).tap(RoundedCornerShape(14.dp), onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Green.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Green) }
        Text(label, Modifier.padding(top = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** The site's Home page as a Channels chip ("Feed"). Every call is the site's own: GET /posts?limit=20, /shorts?limit=6, /channels?limit=6, /broadcasts?limit=1; POST /posts {username, caption, content:""}. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HmFeed(onSeeChannels: () -> Unit = {}) {
    val c = ctx(); val scope = rememberCoroutineScope()
    val me = Session.me
    val posts = remember { mutableStateListOf<SoSitePost>() }
    val shorts = remember { mutableStateListOf<JSONObject>() }
    val chans = remember { mutableStateListOf<SoSiteChannel>() }
    var banner by remember { mutableStateOf<Pair<String, String>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var pulling by remember { mutableStateOf(false) }
    var commentsFor by remember { mutableStateOf<SoSitePost?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    val prefs = remember { Api.app.getSharedPreferences("social", 0) }
    val cacheKey = Store.key("hmfeed")
    suspend fun load() {
        try {
            val r = Api.get("/api/posts?limit=20")
            val l = r.optJSONArray("posts").objects().map { SoSitePost.from(it) }
            posts.clear(); posts.addAll(l); err = null; offline = false
            withContext(Dispatchers.IO) { runCatching { Disk.write(cacheKey, JSONObject().put("posts", r.optJSONArray("posts") ?: JSONArray())) } }
        } catch (e: Exception) { if (posts.isEmpty()) { err = Api.friendly(e); offline = Api.isOffline(e) } }
        runCatching { Api.get("/api/shorts?limit=6").optJSONArray("shorts").objects() }.onSuccess { shorts.clear(); shorts.addAll(it.take(8)) }
        runCatching { SoSiteChannel.list(Api.get("/api/channels?limit=6")) }.onSuccess { chans.clear(); chans.addAll(it.take(8)) }
        runCatching { Api.get("/api/broadcasts?limit=1").optJSONArray("broadcasts")?.optJSONObject(0) }.getOrNull()?.let { b ->
            val id = b.str("id").ifBlank { b.str("text").hashCode().toString() }
            banner = if (b.str("text").isNotBlank() && prefs.getString("hmBcastHidden", "") != id) b.str("text") to id else null
        }
        loaded = true
    }
    LaunchedEffect(me?.id) {
        if (posts.isEmpty()) withContext(Dispatchers.IO) { Disk.read(cacheKey) }?.optJSONArray("posts").objects().map { SoSitePost.from(it) }.let { if (it.isNotEmpty()) posts.addAll(it) }
        load(); Perf.shown("Feed")
    }
    // Like the site: look for a new announcement every 30 s while Home is open.
    LaunchedEffect(Unit) {
        while (true) { delay(30_000)
            runCatching { Api.get("/api/broadcasts?limit=1").optJSONArray("broadcasts")?.optJSONObject(0) }.getOrNull()?.let { b ->
                val id = b.str("id").ifBlank { b.str("text").hashCode().toString() }
                if (b.str("text").isNotBlank() && prefs.getString("hmBcastHidden", "") != id) banner = b.str("text") to id }
        }
    }
    fun publish() {
        val t = text.trim()
        if (t.isEmpty()) { toast(c, "⚠ Please write something!"); return }
        if (me == null) { toast(c, "⚠ Please sign in first."); Nav.go(Tab.Profile); return }
        val now = System.currentTimeMillis(); hmPostTimes.removeAll { now - it > 60_000 }
        if (hmPostTimes.size >= 10) { toast(c, "Too many posts. Please wait a minute."); return }
        if (posting) return; posting = true
        scope.launch {
            runCatching { Api.post("/api/posts", JSONObject().put("username", me.nickname.ifBlank { me.email.substringBefore('@') }).put("caption", t).put("content", "")).soCheck() }
                .onSuccess { hmPostTimes.add(now); toast(c, "✅ Posted!"); text = ""; load() }
                .onFailure { toast(c, "❌ " + Api.friendly(it)) }
            posting = false
        }
    }
    PullToRefreshBox(isRefreshing = pulling, onRefresh = { scope.launch { pulling = true; load(); pulling = false } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            item(key = "hero") {
                Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Green.copy(alpha = .16f), Color.Transparent))).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(hmGreeting(h) + (me?.nickname?.takeIf { it.isNotBlank() }?.let { ", \u2068$it\u2069" } ?: "") + "!", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                            Text(remember { hmHebrewDateLine() }, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                        }
                        if (me != null) Box(Modifier.padding(start = 12.dp).tap(CircleShape) { Nav.go(Tab.Profile) }) { SoThumb(Api.abs(me.photo), me.nickname, 44.dp) }
                    }
                    val badge = remember { hmShabbosBadge() }
                    if (badge.isNotBlank()) Text(badge, Modifier.padding(top = 8.dp).background(Color(0xFFC9A84C).copy(alpha = .18f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF8A6D1F))
                }
            }
            banner?.let { (bt, bid) -> item(key = "bcast") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFC9A84C).copy(alpha = .12f))
                    .border(0.5.dp, Color(0xFFC9A84C).copy(alpha = .5f), RoundedCornerShape(12.dp)).padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
                    Text("📢", fontSize = 16.sp)
                    Text(bt, Modifier.weight(1f).padding(start = 8.dp, top = 1.dp), fontSize = 14.sp, lineHeight = 19.sp, style = TextStyle(textDirection = TextDirection.Content))
                    IconBtn(Sym.Close, "Hide announcement", { prefs.edit().putString("hmBcastHidden", bid).apply(); banner = null }, size = 36.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } }
            item(key = "tiles") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HmTile(Sym.Movie, "Shorts", Modifier.weight(1f)) { Nav.go(Tab.Shorts) }
                    HmTile(Sym.MusicNote, "Music", Modifier.weight(1f)) { Nav.go(Tab.Music) }
                    HmTile(Sym.Forum, "Chats", Modifier.weight(1f)) { Nav.go(Tab.Chats) }
                    HmTile(Sym.TravelExplore, "Explore", Modifier.weight(1f)) { Nav.open(SoSearchPage()) }
                }
            }
            item(key = "composer") {
                Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            SoThumb(Api.abs(me?.photo), me?.nickname ?: "?", 40.dp)
                            OutlinedTextField(text, { text = it.take(2000) }, Modifier.weight(1f).padding(start = 10.dp), placeholder = { Text("What's happening?") }, minLines = 2, maxLines = 6,
                                textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content), shape = RoundedCornerShape(12.dp))
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SoPill("Short", false, { if (me == null) { toast(c, "⚠ Please sign in first."); Nav.go(Tab.Profile) } else Nav.open(SoUploadShortPage()) }, icon = Sym.Videocam)
                            SoPill("Status", false, { if (me == null) { toast(c, "⚠ Please sign in first."); Nav.go(Tab.Profile) } else Nav.open(PostStatusPage()) }, icon = Sym.Star)
                            Spacer(Modifier.weight(1f))
                            SoPill(if (posting) "Posting…" else "Post", true, { publish() }, icon = Sym.Send, enabled = !posting)
                        }
                    }
                }
            }
            if (shorts.isNotEmpty() || loaded) {
                item(key = "shead") { HmSectionHead("🔥 Hot Shorts") { Nav.go(Tab.Shorts) } }
                item(key = "srow") {
                    if (shorts.isEmpty()) Text("No shorts yet", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        lazyItems(shorts.toList(), key = { "s" + it.str("id") }) { s ->
                            Box(Modifier.width(104.dp).aspectRatio(9f / 15f).clip(RoundedCornerShape(12.dp)).background(Color.Black).tap(RoundedCornerShape(12.dp)) { soOpenUploadShort(s.str("id")) }) {
                                SoVideoPoster(null, s.str("caption"), 0, Modifier.fillMaxSize(), fit = ContentScale.Crop)
                                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .7f)))).padding(6.dp)) {
                                    Text("@" + s.str("nick").ifBlank { "user" }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("♥ " + soNum(s.optInt("likes")), color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
            if (chans.isNotEmpty() || loaded) {
                item(key = "chead") { HmSectionHead("Popular channels") { onSeeChannels() } }
                item(key = "crow") {
                    if (chans.isEmpty()) Text("No channels yet", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        lazyItems(chans.toList(), key = { "c" + it.ownerId }) { ch ->
                            Column(Modifier.width(112.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).tap(RoundedCornerShape(14.dp)) { Nav.open(SoUserChannelPage(ch.ownerId, ch.name)) }
                                .padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                SoThumb(ch.photo, ch.name, 56.dp)
                                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(ch.name, Modifier.weight(1f, fill = false), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = TextStyle(textDirection = TextDirection.Content))
                                    if (ch.verified) Icon(Sym.Verified, "Verified", Modifier.padding(start = 2.dp).size(14.dp), tint = Color(0xFF1D9BF0))
                                }
                                Text(soNum(ch.followers) + " followers", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
            item(key = "fhead") { HmSectionHead("Community", null) }
            when {
                posts.isEmpty() && err != null -> item(key = "ferr") { Problem("Could not load posts.", offline, { scope.launch { load() } }, Modifier.fillMaxWidth().height(220.dp)) }
                posts.isEmpty() && !loaded -> item(key = "fsk") { SoSkeleton(rows = 3) }
                posts.isEmpty() -> item(key = "fempty") { SoEmpty(Sym.Inbox, "No posts yet. Be the first to share something!", Modifier.fillMaxWidth().height(200.dp)) }
                else -> items(posts.toList(), key = { "f" + it.id }) { p ->
                    SoSitePostCard(p, p.author.ifBlank { "Member" }, p.authorPhoto, onChange = { n -> val i = posts.indexOfFirst { it.id == n.id }; if (i >= 0) posts[i] = n },
                        onComments = { commentsFor = p }, onDeleted = { posts.removeAll { it.id == p.id } })
                }
            }
        }
    }
    commentsFor?.let { p -> SoPostCommentsSheet(p, onDismiss = { commentsFor = null }, onCount = { n -> val i = posts.indexOfFirst { it.id == p.id }; if (i >= 0) posts[i] = posts[i].copy(comments = n) }) }
}
