package com.yidplus.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.yidplus.app.data.Room
import com.yidplus.app.data.Session
import com.yidplus.app.data.Track
import com.yidplus.app.data.objects
import com.yidplus.app.data.str
import com.yidplus.app.data.strOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder

/* 2.1 ST: another person's profile, like the site's openUserProfile (home.js _buildProfileScreen).
   Same API calls: GET /api/profile?user_id, /api/follows?user_id, /api/statuses?user_id, /api/shorts?owner_id, /api/music?owner_id;
   POST /api/follows {user_id}; DELETE /api/follows?user_id; POST /api/chat/rooms {type:'private',other_user_id};
   POST /api/blocks {blocked_id}; DELETE /api/blocks?id; POST /api/reports {reported_id,reported_nick,reason}. */

/** Open another person's profile from ST screens (followers/following rows, blocked list).
 *  Coordinator decision 04:47Z: HM's HmProfilePage is THE public profile page. At merge this body becomes
 *  `Nav.open(HmProfilePage(id))` and StProfilePage can be deleted (see yidplus-21-st-patches.md). Until then it opens ST's page. */
fun stOpenUser(id: String, nick: String = "", photo: String? = null) {
    if (id.isBlank()) return
    Nav.open(StProfilePage(id, nick, photo))
}

private fun stEnc(s: String): String = URLEncoder.encode(s, "UTF-8")

private fun stCount(n: Int): String = when {
    n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 10_000 -> "${n / 1000}k"
    n >= 1000 -> String.format(java.util.Locale.US, "%.1fk", n / 1000.0)
    else -> n.toString()
}

private class StProf(val p: JSONObject, val f: JSONObject, val statusSlides: Int)

data class StProfilePage(val userId: String, val nickHint: String = "", val photoHint: String? = null) : PageScreen() {
    override val perfName: String get() = "UserProfile"

    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        var data by remember { mutableStateOf<StProf?>(null) }
        var err by remember { mutableStateOf<String?>(null) }
        var offline by remember { mutableStateOf(false) }
        var tab by remember { mutableIntStateOf(0) }
        var menu by remember { mutableStateOf(false) }
        var reporting by remember { mutableStateOf(false) }
        var confirmBlock by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        val isMe = Session.me?.id == userId
        suspend fun load() {
            runCatching {
                coroutineScope {
                    val a = async { Api.get("/api/profile?user_id=" + stEnc(userId)) }
                    val b = async { runCatching { Api.get("/api/follows?user_id=" + stEnc(userId)) }.getOrElse { JSONObject() } }
                    val s = async { runCatching { Api.get("/api/statuses?user_id=" + stEnc(userId)) }.getOrElse { JSONObject() } }
                    val st = s.await().optJSONArray("statuses").objects().firstOrNull()
                    val pa = a.await()
                    if (pa.has("ok") && !pa.optBoolean("ok")) throw Api.ApiError(if (pa.optString("error").contains("private", true)) 403 else 404, pa.optString("error").ifBlank { "User not found" })
                    StProf(pa.optJSONObject("profile") ?: JSONObject(), b.await(), st?.optJSONArray("slides")?.length() ?: 0)
                }
            }.onSuccess { data = it; err = null }.onFailure { err = Api.friendly(it); offline = Api.isOffline(it) }
        }
        LaunchedEffect(userId) { load() }
        val p = data?.p
        val nick = p?.str("nickname")?.ifBlank { null } ?: nickHint.ifBlank { "User" }
        val iBlocked = p?.optBoolean("i_blocked") == true || p?.optInt("i_blocked") == 1
        val link = "https://yidplus.com/@" + nick
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopBar("@$nick", onBack = { Nav.back() }) {
                Box {
                IconBtn(Sym.MoreVert, "More", { menu = true })
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Share profile") }, { menu = false; share(c, "@$nick on YID PLUS $link") }, leadingIcon = { Icon(Sym.Share, null) })
                    DropdownMenuItem({ Text("Copy link") }, {
                        menu = false
                        (c.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("link", link)); toast(c, "Link copied")
                    }, leadingIcon = { Icon(Sym.ContentCopy, null) })
                    if (!isMe && Session.me != null) {
                        DropdownMenuItem({ Text(if (iBlocked) "Unblock @$nick" else "Block @$nick") }, { menu = false; confirmBlock = true },
                            leadingIcon = { Icon(if (iBlocked) Sym.LockOpen else Sym.Block, null) })
                        DropdownMenuItem({ Text("Report @$nick", color = MaterialTheme.colorScheme.error) }, { menu = false; reporting = true },
                            leadingIcon = { Icon(Sym.Flag, null, tint = MaterialTheme.colorScheme.error) })
                    }
                }
                }
            }
            when {
                data == null && err != null -> {
                    val e = err!!
                    if (e.contains("private", true)) EmptyNote(Sym.Lock, "This profile is private", "Only people @$nick allows can see it.")
                    else if (e.contains("not found", true)) EmptyNote(Sym.PersonOff, "User not found", "This account may have been deleted.")
                    else Problem(e, offline, { scope.launch { load() } })
                }
                data == null -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(170.dp)) {
                        Box(Modifier.fillMaxWidth().height(120.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                        Box(Modifier.align(Alignment.BottomCenter)) { Avatar(photoHint, nick, 88.dp) }
                    }
                    SkeletonRows(Modifier.fillMaxWidth(), rows = 3, avatar = false)
                }
                else -> {
                    val d = data!!
                    LazyColumn(Modifier.fillMaxSize()) {
                        item { StProfileHeader(d, nick, isMe, iBlocked, busy,
                            onFollow = {
                                if (Session.me == null) { toast(c, "Sign in first"); return@StProfileHeader }
                                busy = true
                                scope.launch {
                                    val f = d.f
                                    runCatching {
                                        if (f.optBoolean("is_following")) { Api.delete("/api/follows?user_id=" + stEnc(userId)); toast(c, "Unfollowed") }
                                        else { val r = Api.post("/api/follows", JSONObject().put("user_id", userId)); toast(c, if (r.optBoolean("requested")) "Follow request sent" else "Following!") }
                                    }.onFailure { toast(c, Api.friendly(it)) }
                                    load(); busy = false
                                }
                            },
                            onMessage = {
                                if (Session.me == null) { toast(c, "Sign in first"); return@StProfileHeader }
                                scope.launch {
                                    runCatching { Api.post("/api/chat/rooms", JSONObject().put("type", "private").put("other_user_id", userId)) }
                                        .onSuccess { r ->
                                            val room = r.str("room_id").ifBlank { r.str("id") }
                                            if (room.isBlank()) { toast(c, "Couldn't open the chat"); return@onSuccess }
                                            Nav.go(Tab.Chats)
                                            Nav.open(Screen.Chat(Room(room, "private", nick, Api.abs(d.p.strOrNull("photo_url")), "", "", 0, null, false, false, 2, userId)))
                                        }.onFailure { toast(c, Api.friendly(it)) }
                                }
                            },
                            onEdit = { Nav.open(SettingsEditProfile()) },
                            onList = { followers -> Nav.open(StFollowListPage(userId, nick, followers)) },
                            onStatus = { Nav.open(StatusViewerPage(userId)) })
                        }
                        item {
                            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                                listOf("Videos", "Music").forEachIndexed { i, t ->
                                    Column(Modifier.weight(1f).tap(RoundedCornerShape(0.dp)) { tab = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(t, Modifier.padding(vertical = 12.dp), fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (tab == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Box(Modifier.fillMaxWidth().height(3.dp).background(if (tab == i) MaterialTheme.colorScheme.primary else Color.Transparent))
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                        item { if (tab == 0) StProfileVideos(userId, nick) else StProfileMusic(userId) }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
        if (confirmBlock) SettingsConfirmDialog(if (iBlocked) "Unblock @$nick?" else "Block @$nick?",
            if (iBlocked) "They will be able to message you and see your content again." else "They won't be able to message you or see your content.",
            if (iBlocked) "Unblock" else "Block", danger = !iBlocked,
            onOk = {
                confirmBlock = false
                scope.launch {
                    runCatching { if (iBlocked) Api.delete("/api/blocks?id=" + stEnc(userId)) else Api.post("/api/blocks", JSONObject().put("blocked_id", userId)) }
                        .onSuccess { toast(c, if (iBlocked) "Unblocked @$nick" else "Blocked @$nick"); load() }.onFailure { toast(c, Api.friendly(it)) }
                }
            }, onDismiss = { confirmBlock = false })
        if (reporting) StReportDialog(nick, onSend = { reason ->
            reporting = false
            scope.launch {
                runCatching { Api.post("/api/reports", JSONObject().put("reported_id", userId).put("reported_nick", nick).put("reason", reason)) }
                    .onSuccess { toast(c, "Report sent. Thank you.") }.onFailure { toast(c, Api.friendly(it)) }
            }
        }, onDismiss = { reporting = false })
    }
}

@Composable
private fun StProfileHeader(d: StProf, nick: String, isMe: Boolean, iBlocked: Boolean, busy: Boolean, onFollow: () -> Unit, onMessage: () -> Unit,
                            onEdit: () -> Unit, onList: (Boolean) -> Unit, onStatus: () -> Unit) {
    val c = ctx()
    val p = d.p
    val photo = Api.abs(p.strOrNull("photo_url"))
    val banner = Api.abs(p.strOrNull("banner_url"))
    val stats = p.optJSONObject("stats")
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth().height(170.dp)) {
            Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.linearGradient(listOf(accent, accent.copy(alpha = .55f))))) {
                if (banner != null) AsyncImage(banner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            val ring = d.statusSlides > 0
            Box(Modifier.align(Alignment.BottomCenter).size(98.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .then(if (ring) Modifier.border(3.dp, accent, CircleShape) else Modifier)
                .then(if (ring) Modifier.tap(CircleShape, onClick = onStatus) else Modifier), contentAlignment = Alignment.Center) {
                Avatar(photo, nick, 86.dp)
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(nick, style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (p.optInt("verified") == 1 || p.optBoolean("verified")) Icon(Sym.Verified, "Verified", Modifier.padding(start = 4.dp).size(20.dp), tint = Color(0xFF1D9BF0))
            }
            val role = p.str("role")
            if (role.isNotBlank() && role != "member" && role != "user") Text(role.replace('_', ' ').uppercase(), color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            if (iBlocked) Text("You blocked this person", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            val bio = p.str("bio")
            if (bio.isNotBlank()) Text(bio, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content), textAlign = TextAlign.Center)
            val loc = p.str("location"); val web = p.str("website")
            if (loc.isNotBlank() || web.isNotBlank()) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (loc.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Sym.LocationOn, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(loc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (web.isNotBlank()) Row(Modifier.tap { openUrl(c, if (web.startsWith("http")) web else "https://$web") }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Sym.Language, null, Modifier.size(16.dp), tint = accent)
                    Text("Website", style = MaterialTheme.typography.bodySmall, color = accent)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                StStat(d.f.optInt("followers"), "Followers") { onList(true) }
                StStat(d.f.optInt("following"), "Following") { onList(false) }
                StStat(stats?.optInt("shorts") ?: 0, "Videos", null)
                StStat(stats?.optInt("music") ?: 0, "Music", null)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isMe) PrimaryButton("Edit profile", onEdit, Modifier.weight(1f).firstFocus())
                else {
                    val f = d.f
                    val label = when {
                        f.optBoolean("is_following") -> "Following"
                        f.optBoolean("has_pending_request") -> "Requested"
                        f.optBoolean("is_private") -> "Request"
                        else -> "Follow"
                    }
                    if (label == "Following" || label == "Requested") OutlinedButton(onFollow, Modifier.weight(1f).heightIn(min = 44.dp).firstFocus(), enabled = !busy && label == "Following",
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) { Text(label) }
                    else PrimaryButton(if (busy) "…" else label, onFollow, Modifier.weight(1f).firstFocus(), enabled = !busy)
                    OutlinedButton(onMessage, Modifier.weight(1f).heightIn(min = 44.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) { Text("Message") }
                }
            }
        }
    }
}

@Composable
private fun StStat(n: Int, label: String, onClick: (() -> Unit)?) {
    Column(Modifier.clip(RoundedCornerShape(10.dp)).then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stCount(n), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StProfileVideos(userId: String, nick: String) {
    var list by remember { mutableStateOf<List<JSONObject>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(userId) {
        runCatching { Api.get("/api/shorts?owner_id=" + stEnc(userId)).optJSONArray("shorts").objects() }
            .onSuccess { list = it }.onFailure { failed = true; list = emptyList() }
    }
    val l = list
    when {
        l == null -> SkeletonRows(Modifier.fillMaxWidth(), rows = 2, avatar = false)
        l.isEmpty() -> EmptyNote(Sym.VideoLibrary, if (failed) "Could not load videos" else "No videos yet", modifier = Modifier.fillMaxWidth())
        else -> Column(Modifier.padding(2.dp)) {
            l.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { s -> StVideoTile(s, nick, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun StVideoTile(s: JSONObject, nick: String, modifier: Modifier) {
    val c = ctx()
    val url = Api.abs(s.strOrNull("media_url"))
    var frame by remember(url) { mutableStateOf<Bitmap?>(url?.let { ChatVideoThumbs.cached(it) }) }
    LaunchedEffect(url) { if (frame == null && url != null) frame = ChatVideoThumbs.load(c, url) }
    Box(modifier.padding(1.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(4.dp)).background(Color.Black)
        .tap(RoundedCornerShape(4.dp)) {
            if (url == null) return@tap
            WatchHistory.record("short", s.str("id"))
            Nav.open(Screen.Video(url, "@$nick"))
        }) {
        frame?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Row(Modifier.align(Alignment.BottomStart).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Sym.PlayArrow, null, Modifier.size(14.dp), tint = Color.White)
            Text(stCount(s.optInt("views")), color = Color.White, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun StProfileMusic(userId: String) {
    val c = ctx()
    var list by remember { mutableStateOf<List<Track>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(userId) {
        runCatching { val j = Api.get("/api/music?owner_id=" + stEnc(userId)); (j.optJSONArray("tracks") ?: j.optJSONArray("music")).objects().map(Track::from) }
            .onSuccess { list = it }.onFailure { failed = true; list = emptyList() }
    }
    val l = list
    when {
        l == null -> SkeletonRows(Modifier.fillMaxWidth(), rows = 3)
        l.isEmpty() -> EmptyNote(Sym.MusicNote, if (failed) "Could not load music" else "No music yet", modifier = Modifier.fillMaxWidth())
        else -> Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            l.forEachIndexed { i, t ->
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).tap(RoundedCornerShape(0.dp)) { Music.play(c, l, i); WatchHistory.record("music", t.id) }
                    .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Icon(Sym.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (t.cover != null) AsyncImage(t.cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stCount(t.plays) + " plays", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Sym.PlayArrow, "Play", tint = MaterialTheme.colorScheme.primary)
                }
                RowDivider(78.dp)
            }
        }
    }
}

@Composable
private fun StReportDialog(nick: String, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    val quick = listOf("Spam", "Harassment", "Inappropriate content", "Fake account")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Report @$nick") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Why are you reporting this person?", style = MaterialTheme.typography.bodyMedium)
                quick.forEach { q -> Text(q, Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).tap { reason = q; err = false }
                    .background(if (reason == q) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).padding(10.dp)) }
                OutlinedTextField(reason, { reason = it; err = false }, Modifier.fillMaxWidth(), label = { Text("Reason") }, isError = err, maxLines = 3)
                if (err) Text("Please give a reason.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton({ if (reason.isBlank()) err = true else onSend(reason.trim()) }) { Text("Report", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

/** Followers / Following of one person (home.js _openFollowModal); on MY followers list each row can Remove or Block, like the site. */
data class StFollowListPage(val userId: String, val nick: String, val followers: Boolean) : PageScreen() {
    override val perfName: String get() = "FollowList"
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        val mine = followers && Session.me?.id == userId
        var list by remember { mutableStateOf<List<JSONObject>?>(null) }
        var err by remember { mutableStateOf<String?>(null) }
        var ask by remember { mutableStateOf<Pair<JSONObject, Boolean>?>(null) }
        suspend fun load() {
            runCatching { Api.get("/api/follows?" + (if (followers) "followers=1" else "following=1") + "&user_id=" + stEnc(userId)).optJSONArray("users").objects() }
                .onSuccess { list = it; err = null }.onFailure { err = Api.friendly(it) }
        }
        LaunchedEffect(userId, followers) { load() }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopBar(if (followers) "Followers" else "Following", onBack = { Nav.back() }, sub = "@$nick")
            val l = list
            when {
                l == null && err != null -> Problem(err!!, false, { scope.launch { load() } })
                l == null -> SkeletonRows()
                l.isEmpty() -> EmptyNote(Sym.Group, "Nobody here yet")
                else -> LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    items(l, key = { it.str("id") }) { u ->
                        val un = u.str("nickname").ifBlank { "User" }
                        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).then(if (u == l.first()) Modifier.firstFocus() else Modifier)
                            .tap(RoundedCornerShape(0.dp)) { stOpenUser(u.str("id"), un, Api.abs(u.strOrNull("photo_url"))) }
                            .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(Api.abs(u.strOrNull("photo_url")), un, 48.dp)
                            Row(Modifier.padding(start = 14.dp).weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Text("@$un", style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (u.optInt("verified") == 1 || u.optBoolean("verified")) Icon(Sym.Verified, "Verified", Modifier.padding(start = 4.dp).size(16.dp), tint = Color(0xFF1D9BF0))
                            }
                            if (mine) {
                                TextButton({ ask = u to false }) { Text("Remove") }
                                TextButton({ ask = u to true }) { Text("Block", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                        RowDivider(78.dp)
                    }
                }
            }
        }
        ask?.let { (u, block) ->
            val un = u.str("nickname").ifBlank { "User" }
            SettingsConfirmDialog(if (block) "Block @$un?" else "Remove @$un?",
                if (block) "They'll be removed as a follower and won't see your content or message you." else "Remove @$un from your followers?",
                if (block) "Block" else "Remove", danger = block,
                onOk = {
                    ask = null
                    scope.launch {
                        val id = u.str("id")
                        runCatching {
                            if (block) { Api.post("/api/blocks", JSONObject().put("blocked_id", id)); runCatching { Api.delete("/api/follows?remove=1&user_id=" + stEnc(id)) } }
                            else Api.delete("/api/follows?remove=1&user_id=" + stEnc(id))
                        }.onSuccess { toast(c, if (block) "Blocked @$un" else "Removed @$un"); list = list?.filter { it.str("id") != id } }
                            .onFailure { toast(c, Api.friendly(it)) }
                    }
                }, onDismiss = { ask = null })
        }
    }
}
