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

// The website's header items (search, the notifications bell with its unread count, the profile menu), native.

object SoNotifCount {
    var unread by mutableIntStateOf(0)
    private var at = 0L
    suspend fun refresh(force: Boolean = false) {
        if (Session.me == null) { unread = 0; return }
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - at < 45_000) return
        at = now
        runCatching { unread = Api.get("/api/notifications?count=1").optInt("unread") }
    }
}

/** Search, bell (with the unread count) and the profile picture menu, for a top bar's actions. */
@Composable
fun SoHeaderActions(onSearch: (() -> Unit)? = null) {
    val c = ctx()
    val tint = LocalContentColor.current
    val me = Session.me
    LaunchedEffect(me?.id) { while (true) { SoNotifCount.refresh(); delay(60_000) } }
    IconBtn(Icons.Filled.Search, "Search", { if (onSearch != null) onSearch() else Nav.open(SoSearchPage()) }, tint = tint)
    if (me != null) Box {
        IconBtn(Icons.Filled.Notifications, "Notifications", { Nav.open(SoNotifsPage()) }, tint = tint)
        if (SoNotifCount.unread > 0) SoBadge(SoNotifCount.unread, Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 2.dp))
    }
    var menu by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.size(44.dp).tap(CircleShape) { menu = true }, contentAlignment = Alignment.Center) {
            if (me != null) SoThumb(me.photo, me.nickname.ifBlank { "Me" }, 32.dp)
            else Icon(Icons.Filled.AccountCircle, "Profile", tint = tint, modifier = Modifier.size(28.dp))
        }
        DropdownMenu(menu, { menu = false }) {
            if (me != null) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SoThumb(me.photo, me.nickname, 40.dp)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(me.nickname, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (me.email.isNotBlank()) Text(me.email, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("My channel") }, leadingIcon = { Icon(Icons.Filled.AccountBox, null) },
                    onClick = { menu = false; Nav.open(SoUserChannelPage(me.id, me.nickname)) })
                DropdownMenuItem(text = { Text("Notifications") }, leadingIcon = { Icon(Icons.Filled.Notifications, null) },
                    onClick = { menu = false; Nav.open(SoNotifsPage()) })
            }
            DropdownMenuItem(text = { Text(if (me != null) "Settings" else "Sign in") }, leadingIcon = { Icon(if (me != null) Icons.Filled.Settings else Icons.Filled.Login, null) },
                onClick = { menu = false; Nav.go(Tab.Profile) })
            DropdownMenuItem(text = { Text("Open yidplus.com") }, leadingIcon = { Icon(Icons.Filled.Public, null) },
                onClick = { menu = false; openUrl(c, Api.BASE) })
        }
    }
}

/** Where a notification's link leads, inside the app. */
fun soRoute(ctx: Context, link: String) {
    val u = Api.abs(link.ifBlank { "/" }) ?: return
    val uri = Uri.parse(u)
    uri.getQueryParameter("channel")?.let { Nav.open(SoUserChannelPage(it, "")); return }
    if ((uri.path ?: "").startsWith("/c/")) { Nav.open(Screen.Web(u)); return }
    com.yidplus.app.MainActivity.route(ctx, u)
}

private fun notifIcon(t: String): Pair<ImageVector, Color> = when (t) {
    "like" -> Icons.Filled.Favorite to Color(0xFFE53958)
    "follow", "follow_request" -> Icons.Filled.PersonAdd to Color(0xFF2E7D9A)
    "comment", "reply" -> Icons.Filled.ChatBubble to Color(0xFF1F6F5C)
    "mention" -> Icons.Filled.AlternateEmail to Color(0xFF7B5EA7)
    "channel_post" -> Icons.Filled.Campaign to Color(0xFFB7892B)
    else -> Icons.Filled.Notifications to Color(0xFF607D8B)
}

/** The bell's list: what happened (likes, follows, comments, mentions, channel posts), newest first. */
class SoNotifsPage : PageScreen() {
    override val perfName = "Notifications"
    override fun equals(other: Any?) = other is SoNotifsPage
    override fun hashCode() = 7
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        val res = remember(Session.me?.id) { Store.res("/api/notifications") { j -> j.optJSONArray("notifications").objects().map(Notif::from) } }
        val readNow = remember { mutableStateListOf<String>() }
        LaunchedEffect(res) {
            res.load(force = true); Perf.shown("Notifications")
            // Opening the list reads everything, like the site's bell.
            if (res.data?.any { !it.read } == true) runCatching { Api.post("/api/notifications", JSONObject().put("read", "all")); SoNotifCount.unread = 0 }
        }
        var confirm by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopBar("Notifications", onBack = { Nav.back() }) {
                if (!res.data.isNullOrEmpty()) IconBtn(Icons.Filled.DeleteSweep, "Clear all", { confirm = true }, tint = LocalContentColor.current)
            }
            val list = res.data
            when {
                Session.me == null -> SoEmpty(Icons.Filled.Notifications, "Sign in to see your notifications", action = "Sign in", onAction = { Nav.go(Tab.Profile) })
                list == null && res.error != null -> Problem(res.error!!, res.offline, { scope.launch { res.load(true) } })
                list == null -> SoSkeleton(rows = 8, avatar = 44.dp)
                list.isEmpty() -> SoEmpty(Icons.Filled.NotificationsNone, "No notifications yet 🎉")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { n ->
                        val unread = !n.read && n.id !in readNow
                        val (ic, col) = notifIcon(n.type)
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .background(if (unread) MaterialTheme.colorScheme.primary.copy(alpha = 0.07f) else Color.Transparent)
                            .then(if (n == list.first()) Modifier.firstFocus() else Modifier)
                            .tap(RoundedCornerShape(0.dp)) { readNow.add(n.id); soRoute(c, n.link) }
                            .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(col.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                                Icon(ic, null, tint = col, modifier = Modifier.size(22.dp))
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(n.text.ifBlank { n.type.replace('_', ' ') }, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal, style = TextStyle(textDirection = TextDirection.Content))
                                Text(ago(n.at), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (unread) Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF25A36F)))
                        }
                        SoDivider(72.dp)
                    }
                }
            }
        }
        if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Clear all notifications?") },
            confirmButton = { TextButton({ confirm = false; scope.launch { runCatching { Api.delete("/api/notifications") }; res.data = emptyList(); SoNotifCount.unread = 0 } }) { Text("Clear") } },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
    }
}

/** One search answer, from any part of the site. */
sealed class SoHit(val key: String) {
    class Member(val ownerId: String, val name: String, val photo: String?, val verified: Boolean, val bio: String, val followers: Int) : SoHit("u:$ownerId")
    class Telegram(val ch: TgChannel) : SoHit("t:" + ch.username)
    class Post(val id: String, val userId: String, val name: String, val text: String, val at: String?) : SoHit("p:$id")
    class Short(val id: String, val ownerId: String, val text: String, val at: String?) : SoHit("s:$id")
    class Feed(val clip: Clip) : SoHit("f:" + clip.key)
}

/** The header's search: channels (members), Telegram channels, posts and Shorts, in one place. */
class SoSearchPage(val start: Int = 0) : PageScreen() {
    override val perfName = "Search"
    override fun equals(other: Any?) = other is SoSearchPage && other.start == start
    override fun hashCode() = 11 + start
    @Composable override fun Content() {
        val c = ctx()
        var q by rememberSaveable { mutableStateOf("") }
        var kind by rememberSaveable { mutableIntStateOf(start) }
        val kinds = listOf("All", "Channels", "Telegram", "Posts", "Shorts")
        val hits = remember { mutableStateListOf<SoHit>() }
        var busy by remember { mutableStateOf(false) }
        var err by remember { mutableStateOf<String?>(null) }
        var retry by remember { mutableIntStateOf(0) }
        val tg = remember { Store.res("/api/telegram-channels", TgChannel::list) }
        LaunchedEffect(Unit) { tg.load() }
        LaunchedEffect(q, kind, retry) {
            val t = q.trim()
            if (t.length < 2) { hits.clear(); err = null; return@LaunchedEffect }
            delay(350)
            busy = true
            val out = ArrayList<SoHit>()
            try {
                if (kind == 0 || kind == 2) tg.data.orEmpty().filter { it.title.contains(t, true) || it.username.contains(t, true) }.take(15).forEach { out.add(SoHit.Telegram(it)) }
                if (kind == 0 || kind == 1) runCatching {
                    Api.get("/api/channels?limit=20&search=" + soEnc(t)).optJSONArray("channels").objects().forEach { j ->
                        out.add(SoHit.Member(j.str("owner_id"), j.str("nickname"), Api.abs(j.strOrNull("photo_url")), j.optInt("verified") == 1 || j.optBoolean("verified"), j.str("bio"), j.optInt("followers")))
                    }
                }
                if (kind == 0 || kind == 4) Feeds.forYou.items.filter { it.caption.contains(t, true) || it.creator.contains(t, true) }.take(20).forEach { out.add(SoHit.Feed(it)) }
                if (kind == 0 || kind == 3 || kind == 4) {
                    val j = Api.get("/api/search?q=" + soEnc(t))
                    if (kind != 4) j.optJSONArray("posts").objects().forEach { p ->
                        out.add(SoHit.Post(p.str("id"), p.str("user_id"), p.str("username"), p.str("caption").ifBlank { p.str("content") }, p.strOrNull("created_at"))) }
                    if (kind != 3) j.optJSONArray("shorts").objects().forEach { s -> out.add(SoHit.Short(s.str("id"), s.str("owner_id"), s.str("caption"), s.strOrNull("created_at"))) }
                }
                err = null
            } catch (e: Exception) { err = Api.friendly(e) }
            hits.clear(); hits.addAll(out.distinctBy { it.key }); busy = false
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Column {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBtn(Icons.Filled.ArrowBack, "Back", { Nav.back() })
                        TextField(q, { q = it.take(100) }, Modifier.weight(1f).firstFocus(always = true), singleLine = true,
                            placeholder = { Text("Search YID PLUS") }, textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                        if (q.isNotEmpty()) IconBtn(Icons.Filled.Close, "Clear", { q = "" })
                    }
                    SoChips(kinds, kind, { kind = it })
                }
            }
            when {
                q.trim().length < 2 -> SoEmpty(Icons.Filled.Search, "Search channels, Telegram channels, posts and Shorts")
                busy && hits.isEmpty() -> SoSkeleton(rows = 6)
                err != null && hits.isEmpty() -> Problem(err!!, false, { retry++ })
                hits.isEmpty() -> SoEmpty(Icons.Filled.SearchOff, "Nothing found for \u201c${q.trim()}\u201d")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(hits, key = { it.key }) { h -> HitRow(h, c); SoDivider(76.dp) }
                }
            }
        }
    }
}

@Composable
private fun HitRow(h: SoHit, c: Context) {
    val (title, sub, kindLabel) = when (h) {
        is SoHit.Member -> Triple(h.name, h.bio.ifBlank { "${soNum(h.followers)} followers" }, "Channel")
        is SoHit.Telegram -> Triple(h.ch.title, "@" + h.ch.username + " · " + soMembers(h.ch.members), "Telegram")
        is SoHit.Post -> Triple(h.name.ifBlank { "Post" }, h.text.replace('\n', ' '), "Post")
        is SoHit.Short -> Triple(h.text.ifBlank { "Short video" }, ago(h.at), "Short")
        is SoHit.Feed -> Triple(h.clip.caption.replace('\n', ' ').ifBlank { "Short video" }, "@" + h.clip.creator, "Short")
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).tap(RoundedCornerShape(0.dp)) {
        when (h) {
            is SoHit.Member -> Nav.open(SoUserChannelPage(h.ownerId, h.name))
            is SoHit.Telegram -> Nav.open(Screen.Channel(h.ch))
            is SoHit.Post -> Nav.open(SoUserChannelPage(h.userId, h.name))
            is SoHit.Short -> soOpenUploadShort(h.id)
            is SoHit.Feed -> soOpenFeedClip(h.clip)
        }
    }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        when (h) {
            is SoHit.Member -> SoThumb(h.photo, h.name, 48.dp)
            is SoHit.Telegram -> SoThumb(h.ch.photo, h.ch.title, 48.dp)
            is SoHit.Post -> Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF2E7D9A).copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Article, null, tint = Color(0xFF2E7D9A)) }
            is SoHit.Feed -> SoThumb(h.clip.poster, h.clip.creator, 48.dp, round = false)
            is SoHit.Short -> Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFE53958).copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Movie, null, tint = Color(0xFFE53958)) }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content))
                if (h is SoHit.Member && h.verified) Icon(Icons.Filled.Verified, null, tint = Color(0xFF2E7D9A), modifier = Modifier.padding(start = 4.dp).size(16.dp))
                Text(kindLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
            }
            if (sub.isNotBlank()) Text(sub, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(textDirection = TextDirection.Content))
        }
    }
}
