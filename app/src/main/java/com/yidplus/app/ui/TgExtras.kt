package com.yidplus.app.ui

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

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

// Channels, as the website has them: the Telegram channels (join, reactions, info, members) and the members' own
// channels (directory, channel page, follow, posts with likes / comments / share, new post, follow requests).

/** Bumped after a change (new post, follow) so the lists showing it load again. */
object SoRefresh { var tick by mutableIntStateOf(0) }

// ───────────────────────── Telegram channels ─────────────────────────

val SO_TG_QUICK = listOf("👍", "❤️", "😂", "🔥", "😮", "😢", "🙏", "💯")

/** Reactions on one Telegram channel's posts: counts per emoji and mine. */
class SoTgReacts(val username: String) {
    val counts = mutableStateMapOf<Long, Map<String, Int>>()
    val mine = mutableStateMapOf<Long, String>()
    suspend fun load() {
        val r = try { Api.get("/api/telegram-reactions?username=" + soEnc(username)).optJSONObject("reactions") } catch (e: Exception) { null } ?: return
        val ks = r.keys()
        while (ks.hasNext()) { val k = ks.next(); val id = k.toLongOrNull() ?: continue; put(id, r.optJSONObject(k) ?: continue) }
    }
    private fun put(id: Long, o: JSONObject) {
        val m = LinkedHashMap<String, Int>(); val cj = o.optJSONObject("counts")
        if (cj != null) { val it = cj.keys(); while (it.hasNext()) { val e = it.next(); val n = cj.optInt(e); if (n > 0) m[e] = n } }
        counts[id] = m
        val my = o.strOrNull("my_reaction"); if (my != null) mine[id] = my else mine.remove(id)
    }
    suspend fun react(ctx: Context, id: Long, emoji: String) {
        if (Session.me == null) { toast(ctx, "Sign in to react"); return }
        val before = counts[id] ?: emptyMap(); val beforeMine = mine[id]
        val m = before.toMutableMap()
        beforeMine?.let { old -> m[old] = (m[old] ?: 1) - 1; if ((m[old] ?: 0) <= 0) m.remove(old) }
        if (beforeMine != emoji) { m[emoji] = (m[emoji] ?: 0) + 1; mine[id] = emoji } else mine.remove(id)
        counts[id] = m
        try {
            val r = Api.post("/api/telegram-reactions", JSONObject().put("username", username).put("tg_msg_id", id).put("emoji", emoji)).soCheck()
            put(id, r)
        } catch (e: Exception) { counts[id] = before; if (beforeMine != null) mine[id] = beforeMine else mine.remove(id); toast(ctx, Api.friendly(e)) }
    }
}

/** The reaction pills under a channel post, and the + that opens the 8 quick emoji. */
@Composable
fun SoTgReactionRow(id: Long, reacts: SoTgReacts, modifier: Modifier = Modifier) {
    val c = ctx(); val scope = rememberCoroutineScope()
    val counts = reacts.counts[id].orEmpty(); val my = reacts.mine[id]
    var pick by remember { mutableStateOf(false) }
    LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(counts.entries.toList(), key = { it.key }) { (e, n) ->
            val on = e == my
            Row(Modifier.heightIn(min = 30.dp).soTouchOnly().tap(RoundedCornerShape(15.dp)) { scope.launch { reacts.react(c, id, e) } }
                .background(if (on) Green.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(15.dp))
                .then(if (on) Modifier.border(1.dp, Green, RoundedCornerShape(15.dp)) else Modifier)
                .padding(horizontal = 9.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(e, fontSize = 15.sp); Spacer(Modifier.width(4.dp))
                Text(soNum(n), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Green else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "+") {
            Box {
                Box(Modifier.size(width = 40.dp, height = 30.dp).soTouchOnly().tap(RoundedCornerShape(15.dp)) { pick = true }
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                    Icon(Sym.AddReaction, "React", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(pick, { pick = false }) {
                    Row(Modifier.padding(horizontal = 6.dp)) {
                        for ((qi, e) in SO_TG_QUICK.withIndex()) Box(Modifier.size(42.dp).semantics { contentDescription = "Quick reaction ${qi + 1}" }.tap(CircleShape) { pick = false; scope.launch { reacts.react(c, id, e) } }
                            .background(if (e == my) Green.copy(alpha = 0.16f) else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) { Text(e, fontSize = 22.sp) }
                    }
                }
            }
        }
    }
}

/** Joins / leaves a Telegram channel here on YID PLUS (the site's Join bar). Returns the new state, or null. */
suspend fun soTgToggleJoin(ctx: Context, username: String, wasJoined: Boolean? = null): Pair<Boolean, Int>? {
    if (Session.me == null) { toast(ctx, "Sign in to join channels"); Nav.go(Tab.Profile); return null }
    return try {
        val r = Api.post("/api/telegram-join", JSONObject().put("username", username)).soCheck()
        // The site answers {joined}; when an answer has no "joined" (an old server), the tap still flips it.
        val joined = if (r.has("joined") || wasJoined == null) r.optBoolean("joined") else !wasJoined; toast(ctx, if (joined) "Joined ✓" else "Left the channel")
        SoRefresh.tick++
        joined to (if (r.has("members")) r.optInt("members") else -1)
    } catch (e: Exception) { toast(ctx, Api.friendly(e)); null }
}

fun soTgLink(username: String) = Api.BASE + "/chat#tg=" + username

/** A video's picture: its poster when Telegram has one, otherwise a calm card with the play button (never a black box). */
@Composable
fun SoVideoPoster(url: String?, title: String, duration: Int, modifier: Modifier = Modifier, fit: ContentScale = ContentScale.Fit) {
    val c = ctx()
    var failed by remember(url) { mutableStateOf(url.isNullOrBlank()) }
    var loaded by remember(url) { mutableStateOf(false) }
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF1F6F5C), Color(0xFF173F4A)))), contentAlignment = Alignment.Center) {
        if (!failed) AsyncImage(model = ImageRequest.Builder(c).data(url).crossfade(150).build(), contentDescription = null, contentScale = fit,
            modifier = Modifier.fillMaxWidth().then(if (loaded) Modifier else Modifier.heightIn(min = 160.dp)),
            onSuccess = { loaded = true }, onError = { failed = true })
        if (failed || !loaded) Column(Modifier.padding(top = 70.dp, bottom = 14.dp, start = 16.dp, end = 16.dp).align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            if (title.isNotBlank()) Text(title, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = .5f)), contentAlignment = Alignment.Center) {
            Icon(Sym.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(36.dp))
        }
        if (duration > 0) Text(dur(duration), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color.Black.copy(alpha = .6f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

/** Channel info: picture, name, how many joined here, Join / Leave, share, members (and the admin's remove). */
data class SoTgInfoPage(val ch: TgChannel) : PageScreen() {
    override val perfName = "ChannelInfo"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var joined by remember { mutableStateOf(ch.joined) }
        var members by remember { mutableIntStateOf(ch.members) }
        var posts by remember { mutableIntStateOf(-1) }
        var isAdmin by remember { mutableStateOf(false) }
        val list = remember { mutableStateListOf<JSONObject>() }
        var msg by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        LaunchedEffect(ch.username) {
            runCatching {
                val r = Api.get("/api/telegram-info?username=" + soEnc(ch.username))
                posts = r.optInt("post_count", -1); isAdmin = r.optBoolean("is_admin"); msg = r.strOrNull("error")
                list.clear(); list.addAll(r.optJSONArray("members").objects()); if (list.isNotEmpty()) members = list.size
            }.onFailure { msg = Api.friendly(it) }
            Perf.shown("ChannelInfo")
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopBar("Channel info", onBack = { Nav.back() })
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        SoThumb(ch.photo, ch.title, 88.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(ch.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp),
                            style = TextStyle(textDirection = TextDirection.Content))
                        Text("@" + ch.username, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(soMembers(members) + (if (posts >= 0) " · ${soNum(posts)} posts" else ""), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SoPill(if (joined) "Leave" else "Join", !joined, {
                                if (!busy) scope.launch { busy = true; soTgToggleJoin(c, ch.username, joined)?.let { (j, n) -> members = if (n >= 0) n else (members + (if (j) 1 else -1)).coerceAtLeast(0); joined = j }; busy = false } },
                                Modifier.firstFocus(), icon = if (joined) Sym.Logout else Sym.Add)
                            SoPill("Share", false, { share(c, ch.title + "\n" + soTgLink(ch.username)) }, icon = Sym.Share)
                            SoPill("Copy link", false, { soCopy(c, soTgLink(ch.username)) }, icon = Sym.Link)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("MEMBERS ON YID PLUS", Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
                if (list.isEmpty()) item { SoEmpty(Sym.Group, msg ?: "No members yet") }
                items(list, key = { it.str("user_id") }) { m ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SoThumb(Api.abs(m.strOrNull("photo_url")), m.str("name"), 42.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(m.str("name"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                            Text("joined " + ago(m.strOrNull("joined_at")), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isAdmin) IconBtn(Sym.PersonRemove, "Remove", {
                            scope.launch {
                                runCatching { Api.delete("/api/telegram-info?username=" + soEnc(ch.username) + "&user_id=" + soEnc(m.str("user_id"))).soCheck() }
                                    .onSuccess { list.remove(m); members = maxOf(0, members - 1); toast(c, "Removed") }.onFailure { toast(c, Api.friendly(it)) }
                            } }, tint = MaterialTheme.colorScheme.error)
                    }
                    SoDivider(70.dp)
                }
            }
        }
    }
}

/** Admins add a public Telegram channel by its @username (the site's "Add channel"). */
@Composable
fun SoAddTgChannelDialog(onDone: () -> Unit) {
    val c = ctx(); val scope = rememberCoroutineScope()
    var u by remember { mutableStateOf("") }; var t by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDone, title = { Text("Add a Telegram channel") },
        text = { Column {
            OutlinedTextField(u, { u = it.take(64) }, singleLine = true, label = { Text("@username or t.me link") }, modifier = Modifier.fillMaxWidth().firstFocus(always = true))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(t, { t = it.take(80) }, singleLine = true, label = { Text("Title (optional)") }, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton({
            if (busy || u.isBlank()) return@TextButton
            busy = true
            scope.launch {
                try { Api.post("/api/telegram-channels", JSONObject().put("username", u.trim()).put("title", t.trim())).soCheck(); toast(c, "Added. Posts arrive in a few minutes."); SoRefresh.tick++; onDone() }
                catch (e: Exception) { toast(c, Api.friendly(e)) } finally { busy = false }
            } }) { Text(if (busy) "Adding…" else "Add") } },
        dismissButton = { TextButton(onDone) { Text("Cancel") } })
}

