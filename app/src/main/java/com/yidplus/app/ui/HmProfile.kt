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


// ───────────────────────── HM: a member's public profile (the site's openUserProfile), follower lists, DM / block / report ─────────────────────────

/** The site's openDMWith: POST /api/chat/rooms {type:"private", other_user_id}, then the chat opens. */
fun hmOpenDm(c: Context, scope: kotlinx.coroutines.CoroutineScope, userId: String, nick: String, photo: String?) {
    if (Session.me == null) { toast(c, "Sign in to send a message"); Nav.go(Tab.Profile); return }
    if (Session.me?.id == userId) { toast(c, "That's you 🙂"); return }
    toast(c, "Opening chat…")
    scope.launch {
        runCatching { ChatApi.openDm(userId) }
            .onSuccess { id -> if (id.isNotBlank()) Nav.open(Screen.Chat(ChatApi.roomOf(id, "private", nick, photo, userId))) else toast(c, "Could not open the chat") }
            .onFailure { toast(c, Api.friendly(it)) }
    }
}

/** Report a person (the site's reportUserAction: {reported_id, reported_nick, reason}) or a channel (reportChannel: {target_type:"channel", target_id, reason}).
 *  For a channel the owner's id also rides as reported_id, so reports.js (which reads reported_id) really stores it. */
@Composable
fun HmReportDialog(userId: String, nick: String, channel: Boolean, onDone: () -> Unit) {
    val c = ctx(); val scope = rememberCoroutineScope()
    val reasons = listOf("Spam", "Harassment", "Inappropriate content", "Fake account", "Other")
    var pick by remember { mutableIntStateOf(-1) }
    var other by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDone, title = { Text(if (channel) "Report @$nick's channel?" else "Report @$nick") },
        text = {
            Column {
                Text("Why are you reporting this?", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                reasons.forEachIndexed { i, r ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).tap(RoundedCornerShape(8.dp)) { pick = i }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = pick == i, onClick = { pick = i }); Text(r, fontSize = 15.sp)
                    }
                }
                if (pick == reasons.lastIndex) OutlinedTextField(other, { other = it.take(300) }, Modifier.fillMaxWidth(), placeholder = { Text("Tell us more") }, maxLines = 3,
                    textStyle = TextStyle(textDirection = TextDirection.Content))
            }
        },
        confirmButton = { TextButton({
            val reason = if (pick == reasons.lastIndex) other.trim() else reasons.getOrNull(pick).orEmpty()
            if (reason.isBlank()) { toast(c, "Please give a reason."); return@TextButton }
            if (busy) return@TextButton; busy = true
            scope.launch {
                val body = if (channel) JSONObject().put("target_type", "channel").put("target_id", userId).put("reason", if (reason == "Other") "User report" else reason)
                    .put("reported_id", userId).put("reported_nick", nick)
                    else JSONObject().put("reported_id", userId).put("reported_nick", nick).put("reason", reason)
                runCatching { Api.post("/api/reports", body).soCheck() }
                    .onSuccess { toast(c, if (channel) "Reported. We'll review this channel." else "Report sent. Thank you.") }
                    .onFailure { toast(c, Api.friendly(it)) }
                busy = false; onDone()
            }
        }, enabled = !busy) { Text("Report", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onDone) { Text("Cancel") } })
}

/** Block (POST /api/blocks {blocked_id}) or unblock (DELETE /api/blocks?id=) with the site's words. */
@Composable
fun HmBlockDialog(userId: String, nick: String, blocked: Boolean, alsoRemoveFollower: Boolean = false, onDone: (Boolean?) -> Unit) {
    val c = ctx(); val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { onDone(null) },
        title = { Text(if (blocked) "Unblock @$nick?" else "Block @$nick?") },
        text = { Text(if (blocked) "They will be able to see your content and message you again."
            else if (alsoRemoveFollower) "They'll be removed as a follower and won't see your content or message you."
            else "You won't see their content or messages, and they won't be able to message you.") },
        confirmButton = { TextButton({
            scope.launch {
                runCatching {
                    if (blocked) Api.delete("/api/blocks?id=" + soEnc(userId))
                    else { Api.post("/api/blocks", JSONObject().put("blocked_id", userId)).soCheck(); if (alsoRemoveFollower) runCatching { Api.delete("/api/follows?remove=1&user_id=" + soEnc(userId)) } }
                }.onSuccess { toast(c, if (blocked) "Unblocked @$nick" else "Blocked @$nick"); onDone(!blocked) }
                    .onFailure { toast(c, Api.friendly(it)); onDone(null) }
            }
        }) { Text(if (blocked) "Unblock" else "Block", color = if (blocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ onDone(null) }) { Text("Cancel") } })
}

@Composable
private fun HmStat(n: Int, label: String, onClick: (() -> Unit)?) {
    Column(Modifier.widthIn(min = 64.dp).then(if (onClick != null) Modifier.tap(RoundedCornerShape(10.dp), onClick = onClick) else Modifier).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(soNum(n), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** A member's profile, like the site's: banner, photo (with a status ring), name, verified, role, bio, place, website, numbers,
 *  Follow / Request / Following, Message, ⋮ (channel, share, copy link, block, report), and their Videos and Music. */
data class HmProfilePage(val userId: String, val name: String = "") : PageScreen() {
    override val perfName = "Profile"
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var p by remember { mutableStateOf<JSONObject?>(null) }
        var f by remember { mutableStateOf<JSONObject?>(null) }
        var hasStatus by remember { mutableStateOf(false) }
        var err by remember { mutableStateOf<String?>(null) }
        var retry by remember { mutableIntStateOf(0) }
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var menu by remember { mutableStateOf(false) }
        var report by remember { mutableStateOf(false) }
        var block by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var pulling by remember { mutableStateOf(false) }
        val videos = remember { mutableStateListOf<JSONObject>() }
        val music = remember { mutableStateListOf<Track>() }
        var contentLoaded by remember { mutableStateOf(false) }
        val me = Session.me
        val isMe = me?.id == userId
        val cacheKey = Store.key("hmprof|" + userId)
        suspend fun load() {
            try {
                val pr = Api.get("/api/profile?user_id=" + soEnc(userId))
                val fr = runCatching { Api.get("/api/follows?user_id=" + soEnc(userId)) }.getOrNull() ?: JSONObject()
                hasStatus = runCatching { Api.get("/api/statuses?user_id=" + soEnc(userId)).optJSONArray("statuses")?.optJSONObject(0)?.optJSONArray("slides")?.length() ?: 0 }.getOrDefault(0) > 0
                p = pr.optJSONObject("profile") ?: JSONObject(); f = fr; err = null
                // The site reads res.stats but profile.js puts stats inside profile; read both so the numbers are real.
                if (p?.optJSONObject("stats") == null && pr.optJSONObject("stats") != null) p?.put("stats", pr.optJSONObject("stats"))
                withContext(Dispatchers.IO) { runCatching { Disk.write(cacheKey, JSONObject().put("p", p).put("f", fr)) } }
            } catch (e: Exception) { if (p == null) err = Api.friendly(e) }
        }
        suspend fun loadContent() {
            runCatching { val l = Api.get("/api/shorts?owner_id=" + soEnc(userId)).optJSONArray("shorts").objects(); videos.clear(); videos.addAll(l) }
            runCatching { val l = Api.get("/api/music?owner_id=" + soEnc(userId)); music.clear(); music.addAll(Track.list(l).filter { true }) }
            contentLoaded = true
        }
        LaunchedEffect(userId, retry) {
            if (p == null) withContext(Dispatchers.IO) { Disk.read(cacheKey) }?.let { d -> p = d.optJSONObject("p"); f = d.optJSONObject("f") }
            load(); Perf.shown("Profile"); loadContent()
        }
        val nick = p?.str("nickname")?.ifBlank { null } ?: name.ifBlank { "User" }
        val photo = Api.abs(p?.strOrNull("photo_url"))
        val following = f?.optBoolean("is_following") == true
        val pending = f?.optBoolean("has_pending_request") == true
        val isPrivate = f?.optBoolean("is_private") == true
        val iBlocked = p?.optBoolean("i_blocked") == true
        fun follow() {
            if (me == null) { toast(c, "Please sign in first."); Nav.go(Tab.Profile); return }
            if (busy) return; busy = true
            scope.launch {
                try {
                    if (following) { Api.delete("/api/follows?user_id=" + soEnc(userId)); toast(c, "Unfollowed") }
                    else if (!pending) { val r = Api.post("/api/follows", JSONObject().put("user_id", userId)).soCheck(); toast(c, if (r.optBoolean("requested")) "Follow request sent" else "Following!") }
                    load()
                } catch (e: Exception) { toast(c, Api.friendly(e)) } finally { busy = false }
            }
        }
        val link = Api.BASE + "/?channel=" + soEnc(userId)
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopBar("@$nick", onBack = { Nav.back() }) {
                IconBtn(Icons.Filled.Share, "Share", { share(c, "@$nick on YID PLUS\n$link") }, tint = LocalContentColor.current)
                Box {
                    IconBtn(Icons.Filled.MoreVert, "More", { menu = true }, tint = LocalContentColor.current)
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("View channel") }, leadingIcon = { Icon(Icons.Filled.Campaign, null) }, onClick = { menu = false; Nav.open(SoUserChannelPage(userId, nick)) })
                        DropdownMenuItem(text = { Text("Copy link") }, leadingIcon = { Icon(Icons.Filled.Link, null) }, onClick = { menu = false; soCopy(c, link) })
                        if (!isMe && me != null) {
                            DropdownMenuItem(text = { Text(if (iBlocked) "Unblock this user" else "Block this user") }, leadingIcon = { Icon(Icons.Filled.Block, null) }, onClick = { menu = false; block = true })
                            DropdownMenuItem(text = { Text("Report this user", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Filled.Flag, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; report = true })
                        }
                    }
                }
            }
            if (p == null && err != null) { Problem(err!!, false, { retry++ }); return@Column }
            if (p == null) { SoSkeleton(rows = 4); return@Column }
            val j = p!!
            val stats = j.optJSONObject("stats") ?: JSONObject()
            PullToRefreshBox(isRefreshing = pulling, onRefresh = { scope.launch { pulling = true; load(); loadContent(); pulling = false } }, modifier = Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "head") {
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                        val banner = Api.abs(j.strOrNull("banner_url"))
                        Box(Modifier.fillMaxWidth().height(96.dp).background(Brush.linearGradient(listOf(Green, Color(0xFF2E7D9A))))) {
                            if (banner != null) AsyncImage(banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Row(Modifier.padding(horizontal = 16.dp).offset(y = (-32).dp), verticalAlignment = Alignment.Bottom) {
                            Box(Modifier.size(84.dp).clip(CircleShape)
                                .background(if (hasStatus) Brush.sweepGradient(listOf(Green, Color(0xFF25D366), Green)) else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)))
                                .padding(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(2.dp)
                                .then(if (hasStatus) Modifier.tap(CircleShape) { Nav.open(StatusViewerPage(userId)) } else Modifier)) { SoThumb(photo, nick, 74.dp) }
                        }
                        Column(Modifier.padding(start = 16.dp, end = 16.dp).offset(y = (-24).dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(nick, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                    style = TextStyle(textDirection = TextDirection.Content))
                                if (j.optInt("verified") == 1 || j.optBoolean("verified")) Icon(Icons.Filled.Verified, "Verified", tint = Color(0xFF1D9BF0), modifier = Modifier.padding(start = 4.dp).size(19.dp))
                                if (isPrivate) Icon(Icons.Filled.Lock, "Private", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                            }
                            Text("@$nick", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val role = j.str("role")
                            if (role.isNotBlank() && role != "member") Text(role.replace('_', ' ').replaceFirstChar { it.uppercase() }, Modifier.padding(top = 4.dp)
                                .background(Green.copy(alpha = .14f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 12.sp, color = Green, fontWeight = FontWeight.SemiBold)
                            if (j.str("bio").isNotBlank()) Text(j.str("bio"), fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp), style = TextStyle(textDirection = TextDirection.Content))
                            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (j.str("location").isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Place, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(" " + j.str("location"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                                val web = j.str("website")
                                if (web.isNotBlank()) Row(Modifier.tap(RoundedCornerShape(8.dp)) { openUrl(c, if (web.startsWith("http")) web else "https://$web") }, verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Language, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(" Website", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium) }
                            }
                            Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                HmStat(f?.optInt("followers") ?: 0, "Followers") { Nav.open(HmFollowListPage(userId, nick, true)) }
                                HmStat(f?.optInt("following") ?: 0, "Following") { Nav.open(HmFollowListPage(userId, nick, false)) }
                                HmStat(stats.optInt("shorts", videos.size), "Videos") { tab = 0 }
                                HmStat(stats.optInt("music", music.size), "Music") { tab = 1 }
                            }
                            Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isMe) SoPill("Edit profile", false, { Nav.go(Tab.Profile) }, Modifier.weight(1f).firstFocus(), icon = Icons.Filled.Edit)
                                else {
                                    SoPill(when { following -> "Following"; pending -> "Requested"; isPrivate -> "Request"; else -> "Follow" }, !following && !pending, { follow() },
                                        Modifier.weight(1f).firstFocus(), icon = if (following) Icons.Filled.Check else if (pending) Icons.Filled.Schedule else Icons.Filled.PersonAdd, enabled = !busy && !pending)
                                    SoPill("Message", false, { hmOpenDm(c, scope, userId, nick, photo) }, Modifier.weight(1f), icon = Icons.Filled.ChatBubbleOutline)
                                }
                            }
                        }
                    }
                }
                item(key = "tabs") {
                    Surface(color = MaterialTheme.colorScheme.surface) { SoChips(listOf("Videos", "Music"), tab, { tab = it }) }
                    HorizontalDivider(thickness = 0.5.dp)
                }
                if (iBlocked) item(key = "blocked") { SoEmpty(Icons.Filled.Block, "You blocked @$nick", action = "Unblock", onAction = { block = true }) }
                else if (tab == 0) {
                    if (videos.isEmpty()) item(key = "nov") { if (contentLoaded) SoEmpty(Icons.Filled.Movie, "No videos yet") else SoSkeleton(rows = 2) }
                    items(videos.chunked(3), key = { "v" + it.first().str("id") }) { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (s in row) Box(Modifier.weight(1f).aspectRatio(9f / 14f).tap(RoundedCornerShape(2.dp)) { soOpenUploadShort(s.str("id")) }) {
                                SoVideoPoster(null, s.str("caption"), 0, Modifier.fillMaxSize())
                                Row(Modifier.align(Alignment.BottomStart).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Text(soNum(s.optInt("views")), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                } else {
                    if (music.isEmpty()) item(key = "nom") { if (contentLoaded) SoEmpty(Icons.Filled.MusicNote, "No music yet") else SoSkeleton(rows = 2) }
                    itemsIndexed(music, key = { _, t -> "m" + t.id }) { i, t -> HmTrackRow(t) { Music.play(c, music.toList(), i) } }
                }
                item(key = "end") { Spacer(Modifier.height(24.dp)) }
            }
            }
        }
        if (report) HmReportDialog(userId, nick, false) { report = false }
        if (block) HmBlockDialog(userId, nick, iBlocked) { r -> block = false; if (r != null) { p?.put("i_blocked", r); p = JSONObject(p.toString()) } }
    }
}

@Composable
fun HmTrackRow(t: Track, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).background(MaterialTheme.colorScheme.surface).tap(RoundedCornerShape(0.dp), onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(Green.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
            if (t.cover != null) AsyncImage(t.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Filled.MusicNote, null, tint = Green)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(t.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
            Text(listOf(t.artist, if (t.plays > 0) soNum(t.plays) + " plays" else "").filter { it.isNotBlank() }.joinToString(" · "), fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.PlayCircle, "Play", tint = Green, modifier = Modifier.size(30.dp))
    }
}

/** Followers or Following of a member (GET /api/follows?followers=1|following=1&user_id=). On your own followers: Remove and Block, like the site. */
data class HmFollowListPage(val userId: String, val nick: String, val followers: Boolean) : PageScreen() {
    override val perfName = "FollowList"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        val list = remember { mutableStateListOf<JSONObject>() }
        var loading by remember { mutableStateOf(true) }
        var err by remember { mutableStateOf<String?>(null) }
        var retry by remember { mutableIntStateOf(0) }
        var removing by remember { mutableStateOf<JSONObject?>(null) }
        var blocking by remember { mutableStateOf<JSONObject?>(null) }
        val mine = Session.me?.id == userId && followers
        LaunchedEffect(retry) {
            loading = true
            runCatching { Api.get("/api/follows?" + (if (followers) "followers=1" else "following=1") + "&user_id=" + soEnc(userId)) }
                .onSuccess { r -> val l = (r.optJSONArray("users") ?: r.optJSONArray("followers") ?: r.optJSONArray("following")).objects(); list.clear(); list.addAll(l); err = null }
                .onFailure { err = Api.friendly(it) }
            loading = false; Perf.shown("FollowList")
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopBar((if (followers) "Followers" else "Following") + if (!loading && err == null) " (${list.size})" else "", onBack = { Nav.back() }, sub = "@$nick")
            when {
                list.isEmpty() && err != null -> Problem(err!!, false, { retry++ })
                list.isEmpty() && loading -> SoSkeleton()
                list.isEmpty() -> SoEmpty(Icons.Filled.Group, if (followers) "Nobody follows this channel yet." else "Not following anyone yet.")
                else -> LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    items(list, key = { it.str("id") }) { u ->
                        val name = u.str("nickname").ifBlank { "User" }
                        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).then(if (u == list.first()) Modifier.firstFocus() else Modifier)
                            .tap(RoundedCornerShape(0.dp)) { Nav.open(HmProfilePage(u.str("id"), name)) }.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            SoThumb(Api.abs(u.strOrNull("photo_url")), name, 48.dp)
                            Row(Modifier.weight(1f).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("@$name", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                    style = TextStyle(textDirection = TextDirection.Content))
                                if (u.optInt("verified") == 1 || u.optBoolean("verified")) Icon(Icons.Filled.Verified, "Verified", tint = Color(0xFF1D9BF0), modifier = Modifier.padding(start = 4.dp).size(16.dp))
                            }
                            if (mine) {
                                TextButton({ removing = u }) { Text("Remove") }
                                IconBtn(Icons.Filled.Block, "Block", { blocking = u }, tint = MaterialTheme.colorScheme.error, size = 40.dp)
                            }
                        }
                        SoDivider(76.dp)
                    }
                }
            }
        }
        removing?.let { u ->
            val name = u.str("nickname").ifBlank { "User" }
            AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remove @$name from your followers?") },
                confirmButton = { TextButton({ removing = null; scope.launch {
                    runCatching { Api.delete("/api/follows?remove=1&user_id=" + soEnc(u.str("id"))) }
                        .onSuccess { list.removeAll { it.str("id") == u.str("id") }; toast(c, "Removed @$name") }.onFailure { toast(c, Api.friendly(it)) } } }) { Text("Remove") } },
                dismissButton = { TextButton({ removing = null }) { Text("Cancel") } })
        }
        blocking?.let { u -> HmBlockDialog(u.str("id"), u.str("nickname").ifBlank { "User" }, false, alsoRemoveFollower = true) { r -> if (r == true) list.removeAll { it.str("id") == u.str("id") }; blocking = null } }
    }
}
