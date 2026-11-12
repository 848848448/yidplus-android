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
fun SoHeaderActions(onSearch: (() -> Unit)? = null, showSearch: Boolean = true) {
    val c = ctx()
    val tint = LocalContentColor.current
    val me = Session.me
    LaunchedEffect(me?.id) { while (true) { SoNotifCount.refresh(); delay(60_000) } }
    if (showSearch) IconBtn(Sym.Search, "Search", { if (onSearch != null) onSearch() else Nav.open(SoSearchPage()) }, tint = tint)
    if (me != null) Box {
        IconBtn(Sym.Notifications, "Notifications", { Nav.open(SoNotifsPage()) }, tint = tint)
        if (SoNotifCount.unread > 0) SoBadge(SoNotifCount.unread, Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 2.dp))
    }
    var menu by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.size(44.dp).tap(CircleShape) { menu = true }, contentAlignment = Alignment.Center) {
            if (me != null) SoThumb(me.photo, me.nickname.ifBlank { "Me" }, 32.dp)
            else Icon(Sym.AccountCircle, "Profile", tint = tint, modifier = Modifier.size(28.dp))
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
                DropdownMenuItem(text = { Text("My channel") }, leadingIcon = { Icon(Sym.AccountBox, null) },
                    onClick = { menu = false; Nav.open(SoUserChannelPage(me.id, me.nickname)) })
                DropdownMenuItem(text = { Text("Notifications") }, leadingIcon = { Icon(Sym.Notifications, null) },
                    onClick = { menu = false; Nav.open(SoNotifsPage()) })
            }
            DropdownMenuItem(text = { Text(if (me != null) "Settings" else "Sign in") }, leadingIcon = { Icon(if (me != null) Sym.Settings else Sym.Login, null) },
                onClick = { menu = false; Nav.go(Tab.Profile) })
            DropdownMenuItem(text = { Text("Open yidplus.com") }, leadingIcon = { Icon(Sym.Public, null) },
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

/** The bell's list: what happened (likes, follows, comments, mentions, channel posts), newest first. The page itself is NotifsPageBody (NotifsUi.kt). */
class SoNotifsPage : PageScreen() {
    override val perfName = "Notifications"
    override fun equals(other: Any?) = other is SoNotifsPage
    override fun hashCode() = 7
    @Composable override fun Content() { NotifsPageBody() }
}

/** One search answer, from any part of the site. */
sealed class SoHit(val key: String) {
    class Member(val ownerId: String, val name: String, val photo: String?, val verified: Boolean, val bio: String, val followers: Int) : SoHit("u:$ownerId")
    class Telegram(val ch: TgChannel) : SoHit("t:" + ch.username)
    class Post(val id: String, val userId: String, val name: String, val text: String, val at: String?) : SoHit("p:$id")
    class Short(val id: String, val ownerId: String, val text: String, val at: String?) : SoHit("s:$id")
    class Feed(val clip: Clip) : SoHit("f:" + clip.key)
    class User(val id: String, val name: String, val photo: String?, val verified: Boolean) : SoHit("m:$id")
    class Group(val room: Room, val members: Int) : SoHit("g:" + room.id)
    class Chat(val room: Room, val sub: String) : SoHit("c:" + room.id)
}

/** The header's search: channels (members), Telegram channels, posts and Shorts, in one place. */
class SoSearchPage(val start: Int = 0, val q0: String = "") : PageScreen() {
    override val perfName = "Search"
    override fun equals(other: Any?) = other is SoSearchPage && other.start == start && other.q0 == q0
    override fun hashCode() = 11 + start + q0.hashCode()
    @Composable override fun Content() {
        val c = ctx()
        var q by rememberSaveable { mutableStateOf(q0) }
        var kind by rememberSaveable { mutableIntStateOf(start) }
        val kinds = listOf("All", "Channels", "Telegram", "Posts", "Shorts", "Users", "Groups", "Chats")
        val prefs = remember { Api.app.getSharedPreferences("social", 0) }
        val recent = remember { mutableStateListOf<String>().also { l -> runCatching { JSONArray(prefs.getString("ypSearchHist", "[]")).let { a -> for (i in 0 until a.length()) l.add(a.getString(i)) } } } }
        fun saveRecent(t: String) { val s = t.trim(); if (s.length < 2) return; recent.remove(s); recent.add(0, s); while (recent.size > 10) recent.removeAt(recent.lastIndex)
            prefs.edit().putString("ypSearchHist", JSONArray(recent.toList()).toString()).apply() }
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
                // Your chats first (the site's "Your Chats"), from the chat list already on the phone — no request.
                if ((kind == 0 || kind == 7) && Session.me != null) ChatApi.rooms.data.orEmpty().filter { it.room.name.contains(t, true) }.take(if (kind == 7) 40 else 5).forEach { r ->
                    out.add(SoHit.Chat(r.room, if (r.isPrivate) "Private chat" else if (r.isChannel) "Channel" else soMembers(r.members.size))) }
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
                if ((kind == 0 || kind == 5) && Session.me != null) runCatching {
                    Api.get("/api/users/search?q=" + soEnc(t)).optJSONArray("users").objects().take(20).forEach { u ->
                        out.add(SoHit.User(u.str("id"), u.str("nickname").ifBlank { "User" }, Api.abs(u.strOrNull("photo_url")), u.optInt("verified") == 1 || u.optBoolean("verified"))) }
                }
                if ((kind == 0 || kind == 6) && Session.me != null) runCatching {
                    Api.get("/api/chat/rooms?search=" + soEnc(t)).optJSONArray("rooms").objects().filter { it.str("type") != "private" }.take(15).forEach { r ->
                        out.add(SoHit.Group(ChatApi.roomOf(r.str("id"), r.str("type").ifBlank { "group" }, r.str("nick").ifBlank { r.str("name").ifBlank { "Group" } }, Api.abs(r.strOrNull("photo_url"))), r.optInt("members"))) }
                }
                if (out.isNotEmpty()) saveRecent(t)
                err = null
            } catch (e: Exception) { err = Api.friendly(e) }
            hits.clear(); hits.addAll(out.distinctBy { it.key }); busy = false
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Column {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBtn(Sym.ArrowBack, "Back", { Nav.back() })
                        TextField(q, { q = it.take(100) }, Modifier.weight(1f).firstFocus(always = true), singleLine = true,
                            placeholder = { Text("Search YID PLUS") }, textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                        if (q.isNotEmpty()) IconBtn(Sym.Close, "Clear", { q = "" })
                    }
                    SoChips(kinds, kind, { kind = it })
                }
            }
            when {
                q.trim().length < 2 && recent.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                    item { Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent searches", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        TextButton({ recent.clear(); prefs.edit().remove("ypSearchHist").apply(); toast(c, "Search history cleared") }) { Text("Clear") } } }
                    items(recent.toList(), key = { "r:$it" }) { s ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).tap(RoundedCornerShape(0.dp)) { q = s }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Sym.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Text(s, Modifier.weight(1f).padding(start = 16.dp), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                            Icon(Icons.Filled.NorthWest, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                q.trim().length < 2 -> SoEmpty(Sym.Search, "Search channels, people, groups, Telegram channels, posts and Shorts")
                busy && hits.isEmpty() -> SoSkeleton(rows = 6)
                err != null && hits.isEmpty() -> Problem(err!!, false, { retry++ })
                hits.isEmpty() -> SoEmpty(Sym.SearchOff, "Nothing found for \u201c${q.trim()}\u201d")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    // WhatsApp groups "All" results by kind under small headers; one kind needs no header.
                    var last: String? = null
                    val order = listOf("Your chats", "Users", "Channels", "Telegram channels", "Groups", "Posts", "Shorts")
                    (if (kind == 0) hits.sortedBy { order.indexOf(hmHitGroup(it)) } else hits.toList()).forEach { h ->
                        val g = hmHitGroup(h)
                        if (kind == 0 && g != last) { last = g; item(key = "g:$g") { Text(g, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) } }
                        item(key = h.key) { HitRow(h, c, q.trim()); SoDivider(76.dp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HitRow(h: SoHit, c: Context, q: String = "") {
    val (title, sub, kindLabel) = when (h) {
        is SoHit.Member -> Triple(h.name, h.bio.ifBlank { "${soNum(h.followers)} followers" }, "Channel")
        is SoHit.Telegram -> Triple(h.ch.title, "@" + h.ch.username + " · " + soMembers(h.ch.members), "Telegram")
        is SoHit.Post -> Triple(h.name.ifBlank { "Post" }, h.text.replace('\n', ' '), "Post")
        is SoHit.Short -> Triple(h.text.ifBlank { "Short video" }, ago(h.at), "Short")
        is SoHit.Feed -> Triple(h.clip.caption.replace('\n', ' ').ifBlank { "Short video" }, "@\u2068" + h.clip.creator + "\u2069", "Short")
        is SoHit.User -> Triple(h.name, "@\u2068" + h.name + "\u2069", "User")
        is SoHit.Group -> Triple(h.room.name, soMembers(h.members), "Group")
        is SoHit.Chat -> Triple(h.room.name, h.sub, "Chat")
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).tap(RoundedCornerShape(0.dp)) {
        when (h) {
            is SoHit.Member -> Nav.open(SoUserChannelPage(h.ownerId, h.name))
            is SoHit.Telegram -> Nav.open(Screen.Channel(h.ch))
            is SoHit.Post -> Nav.open(SoUserChannelPage(h.userId, h.name))
            is SoHit.Short -> soOpenUploadShort(h.id)
            is SoHit.Feed -> soOpenFeedClip(h.clip)
            is SoHit.User -> Nav.open(HmProfilePage(h.id, h.name))
            is SoHit.Group -> Nav.open(Screen.Chat(h.room))
            is SoHit.Chat -> Nav.open(Screen.Chat(h.room))
        }
    }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        when (h) {
            is SoHit.Member -> SoThumb(h.photo, h.name, 48.dp)
            is SoHit.Telegram -> SoThumb(h.ch.photo, h.ch.title, 48.dp)
            is SoHit.Post -> Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF2E7D9A).copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(Sym.Article, null, tint = Color(0xFF2E7D9A)) }
            is SoHit.Feed -> SoThumb(h.clip.poster, h.clip.creator, 48.dp, round = false)
            is SoHit.Short -> Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFE53958).copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(Sym.Movie, null, tint = Color(0xFFE53958)) }
            is SoHit.User -> SoThumb(h.photo, h.name, 48.dp)
            is SoHit.Group -> SoThumb(h.room.photo, h.room.name, 48.dp)
            is SoHit.Chat -> SoThumb(h.room.photo, h.room.name, 48.dp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(hmMark(title, q), Modifier.weight(1f, fill = false), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content))
                if ((h is SoHit.Member && h.verified) || (h is SoHit.User && h.verified)) Icon(Sym.Verified, null, tint = Color(0xFF2E7D9A), modifier = Modifier.padding(start = 4.dp).size(16.dp))
                Spacer(Modifier.weight(1f))
                Text(kindLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
            }
            if (sub.isNotBlank()) Text(hmMark(sub, q), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(textDirection = TextDirection.Content))
        }
    }
}

private fun hmHitGroup(h: SoHit) = when (h) {
    is SoHit.Chat -> "Your chats"; is SoHit.Telegram -> "Telegram channels"; is SoHit.Member -> "Channels"
    is SoHit.Post -> "Posts"; is SoHit.Short, is SoHit.Feed -> "Shorts"; is SoHit.User -> "Users"; is SoHit.Group -> "Groups"
}

/** The searched words in bold, in the app's colour (WhatsApp marks the match the same way). */
@Composable
fun hmMark(text: String, q: String): androidx.compose.ui.text.AnnotatedString {
    val col = MaterialTheme.colorScheme.primary
    return androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        val words = q.split(' ').map { it.trim() }.filter { it.length >= 2 }
        for (w in words) { var i = text.indexOf(w, ignoreCase = true)
            while (i >= 0) { addStyle(androidx.compose.ui.text.SpanStyle(color = col, fontWeight = FontWeight.Bold), i, i + w.length); i = text.indexOf(w, i + w.length, ignoreCase = true) } }
    }
}
