package com.yidplus.app.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.yidplus.app.MusicService
import com.yidplus.app.data.Api
import com.yidplus.app.data.Media
import com.yidplus.app.data.Net
import com.yidplus.app.data.Perf
import com.yidplus.app.data.Session
import com.yidplus.app.data.Store
import com.yidplus.app.data.Track
import com.yidplus.app.data.objects
import com.yidplus.app.data.str
import com.yidplus.app.data.strOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder

/* ───────────── Helper SM (2.1): WhatsApp's "Updates" page and the site's Highlights ───────────── */

/** Every status in one list: My status, Recent updates (not seen), Viewed updates, Muted updates (folded), with the camera and pencil buttons. */
class SmUpdatesPage : PageScreen() {
    override val perfName: String get() = "Updates"
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable override fun Content() {
        val c = ctx()
        SmPrefs.init(c)
        val scope = rememberCoroutineScope()
        val res = Statuses.res
        LaunchedEffect(Unit) { res.load() }
        var pulling by remember { mutableStateOf(false) }
        var showMuted by rememberSaveable { mutableStateOf(false) }
        @Suppress("UNUSED_VARIABLE") val v = Statuses.seenVersion
        val me = Session.me
        val all = res.data
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TopBar("Updates", onBack = { Nav.back() }, sub = all?.let { l -> val n = Statuses.unseenCount(l); if (n > 0) "$n new" else null }) {
                    if (SmPrefs.highlights.isNotEmpty()) IconBtn(Sym.Bookmarks, "Highlights", { Nav.open(SmHighlightsPage()) })
                    IconBtn(Sym.Refresh, "Refresh", { scope.launch { res.load(true) } })
                }
                PullToRefreshBox(isRefreshing = pulling, onRefresh = { scope.launch { pulling = true; res.load(true); pulling = false } }, modifier = Modifier.weight(1f)) {
                    when {
                        all == null && res.error != null -> Problem(res.error!!, res.offline, { scope.launch { res.load(true) } })
                        all == null -> SkeletonRows()
                        else -> {
                            val ordered = Statuses.ordered(all)
                            val mine = ordered.firstOrNull { it.userId == me?.id }
                            val others = ordered.filter { it.userId != me?.id }
                            val muted = others.filter { SmPrefs.isMuted(it.userId) }
                            val loud = others.filter { !SmPrefs.isMuted(it.userId) }
                            val recent = loud.filter { !Statuses.allSeen(it) }
                            val viewed = loud.filter { Statuses.allSeen(it) }
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp)) {
                                if (me != null) item(key = "me") {
                                    SmUpdateRow(me.photo, me.nickname, mine?.slides?.map { true } ?: emptyList(), mine = true, title = "My status",
                                        sub = mine?.slides?.lastOrNull()?.let { "${it.views} views · " + stAgo(it.createdAt) } ?: "Tap to add a status update",
                                        modifier = Modifier.firstFocus(), plus = mine == null) { if (mine != null) Nav.open(StatusViewerPage(me.id)) else Nav.open(PostStatusPage()) }
                                }
                                if (recent.isNotEmpty()) { item(key = "hr") { SmHeader("Recent updates") }
                                    items(recent, key = { "r" + it.userId }) { u -> SmUserRow(u) } }
                                if (viewed.isNotEmpty()) { item(key = "hv") { SmHeader("Viewed updates") }
                                    items(viewed, key = { "v" + it.userId }) { u -> SmUserRow(u) } }
                                if (muted.isNotEmpty()) {
                                    item(key = "hm") {
                                        Row(Modifier.fillMaxWidth().tap(RoundedCornerShape(0.dp)) { showMuted = !showMuted }.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Text("Muted updates (${muted.size})", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Icon(if (showMuted) Sym.ExpandLess else Sym.ExpandMore, if (showMuted) "Hide muted" else "Show muted", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (showMuted) items(muted, key = { "m" + it.userId }) { u -> SmUserRow(u, Modifier.alpha(.6f)) }
                                }
                                if (others.isEmpty()) item(key = "empty") {
                                    EmptyNote(Icons.Rounded.HistoryToggleOff, "No updates from people you follow", "Statuses last 24 hours. Follow people to see theirs here.", Modifier.fillMaxWidth().padding(top = 24.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (me != null) Column(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton({ Nav.open(PostStatusPage()) }, containerColor = MaterialTheme.colorScheme.surfaceVariant) { Icon(Sym.Edit, "Text status") }
                FloatingActionButton({ Nav.open(PostStatusPage(startMedia = true)) }, containerColor = BadgeGreen, contentColor = Color.White) { Icon(Sym.PhotoCamera, "Photo or video status") }
            }
        }
    }
}

@Composable
private fun SmHeader(t: String) = Text(t, Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun SmUserRow(u: StUser, modifier: Modifier = Modifier) {
    val c = ctx()
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        SmUpdateRow(u.photo, u.nick, u.slides.map { Statuses.seen(it.id) }, mine = false, title = u.nick,
            sub = stAgo(u.slides.lastOrNull()?.createdAt) + if (u.slides.size > 1) " · ${u.slides.size} updates" else "", onLong = { menu = true }) { Nav.open(StatusViewerPage(u.userId)) }
        DropdownMenu(menu, { menu = false }) {
            val m = SmPrefs.isMuted(u.userId)
            DropdownMenuItem(text = { Text(if (m) "Unmute" else "Mute") }, leadingIcon = { Icon(if (m) Sym.NotificationsActive else Sym.NotificationsOff, null) },
                onClick = { menu = false; SmPrefs.setMuted(u.userId, !m); toast(c, if (m) "${u.nick}'s status unmuted" else "${u.nick}'s status muted") })
            DropdownMenuItem(text = { Text("View") }, leadingIcon = { Icon(Sym.Visibility, null) }, onClick = { menu = false; Nav.open(StatusViewerPage(u.userId)) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmUpdateRow(photo: String?, name: String, seen: List<Boolean>, mine: Boolean, title: String, sub: String, modifier: Modifier = Modifier, plus: Boolean = false,
                        onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 72.dp).tap(RoundedCornerShape(0.dp), onClick = onClick)
        .then(if (onLong != null) Modifier.combinedClickable(onLongClick = onLong, onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp)) {
            StatusRing(seen, mine, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape)) { Avatar(photo, name, 48.dp) }
            if (plus) Box(Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(2.dp)
                .clip(CircleShape).background(BadgeGreen), contentAlignment = Alignment.Center) { Icon(Sym.Add, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
            Text(sub, fontSize = 14.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The site's Highlights list: thumbnail, @nick, time, Remove; tap plays them. Kept on this phone only, like the site. */
class SmHighlightsPage : PageScreen() {
    override val perfName: String get() = "Highlights"
    @Composable override fun Content() {
        val c = ctx()
        SmPrefs.init(c)
        Column(Modifier.fillMaxSize()) {
            TopBar("Highlights", onBack = { Nav.back() }, sub = "${SmPrefs.highlights.size} saved on this phone")
            if (SmPrefs.highlights.isEmpty()) EmptyNote(Sym.BookmarkBorder, "No highlights saved yet", "Open a status, tap ⋮ and choose Save to Highlights.")
            else LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(SmPrefs.highlights.toList(), key = { i, h -> h.optString("id") + i }) { i, h ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).tap(RoundedCornerShape(0.dp)) { Nav.open(SmHighlightViewerPage(i)) }
                        .then(if (i == 0) Modifier.firstFocus() else Modifier).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val media = h.optString("media_url").takeIf { it.isNotBlank() && it != "null" }
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Statuses.brush(h.optString("bg"))), contentAlignment = Alignment.Center) {
                            if (media != null && !h.optBoolean("is_video")) AsyncImage(media, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else if (media != null) Icon(Sym.PlayArrowFill, null, tint = Color.White)
                            else Text(h.optString("text").take(14), color = Color.White, fontSize = 10.sp, maxLines = 2, textAlign = TextAlign.Center)
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text("@" + h.optString("nick"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, style = TextStyle(textDirection = TextDirection.Content))
                            Text(stAgo(h.optString("created_at").ifBlank { null }), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton({ SmPrefs.removeHighlight(i); toast(c, "Removed from Highlights") }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
