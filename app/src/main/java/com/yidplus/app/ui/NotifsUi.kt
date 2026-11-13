package com.yidplus.app.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.yidplus.app.data.Api
import com.yidplus.app.data.Notif
import com.yidplus.app.data.Session
import com.yidplus.app.data.Store
import com.yidplus.app.data.objects
import com.yidplus.app.data.str
import com.yidplus.app.data.strOrNull
import kotlinx.coroutines.launch
import org.json.JSONObject

// The bell's page, like the site's notification panel (js/home.js openNotifPanel): GET /api/notifications,
// unread rows highlighted, POST /api/notifications {read:id} when one is opened and {read:'all'} for "Mark all read".
// The site's rows carry actor_nick but no picture, so the app looks the person up once (GET /api/users/search?q=nick,
// the site's own people search) for their photo and id; until then the row shows their coloured first letter.

/** nickname → (user id, photo url), filled once per name per app run. */
object NotifActors {
    val known = mutableStateMapOf<String, Pair<String, String?>>()
    private val asked = HashSet<String>()
    suspend fun look(nick: String) {
        val n = nick.trim()
        if (n.isBlank() || n == "YID PLUS" || !synchronized(asked) { asked.add(n) }) return
        val hit = runCatching { Api.get("/api/users/search?q=" + Uri.encode(n)).optJSONArray("users").objects() }.getOrNull()
            ?.firstOrNull { it.str("nickname").equals(n, ignoreCase = true) } ?: return
        known[n] = hit.str("id") to Api.abs(hit.strOrNull("photo_url"))
    }
}

fun notifsRes() = Store.res("/api/notifications") { j -> j.optJSONArray("notifications").objects().map(Notif::from).distinctBy { it.id } }

private fun notifBadge(t: String): Pair<ImageVector, Color> = when (t) {
    "like" -> Sym.Favorite to Color(0xFFE53958)
    "follow", "follow_request", "follow_accept" -> Sym.PersonAdd to Color(0xFF2E7D9A)
    "comment", "reply" -> Sym.ChatBubble to Color(0xFF1F6F5C)
    "mention" -> Sym.AlternateEmail to Color(0xFF7B5EA7)
    "channel_post" -> Sym.Campaign to Color(0xFFB7892B)
    "verified", "verify" -> Sym.Verified to Color(0xFF25A36F)
    else -> Sym.Notifications to Color(0xFF607D8B)
}

/** "Yanky liked your short" → ("Yanky", "liked your short"); a text without the name keeps the whole text as the action. */
fun notifParts(n: Notif): Pair<String, String> {
    val who = n.actor.trim()
    var rest = n.text.trim()
    if (who.isNotEmpty()) for (p in listOf("@$who", who)) if (rest.startsWith(p, ignoreCase = true)) { rest = rest.substring(p.length).trimStart(' ', ':', '-', '·'); break }
    if (rest.isEmpty()) rest = when (n.type) { "like" -> "liked your post"; "follow" -> "started following you"; "comment" -> "commented"; "mention" -> "mentioned you"; else -> n.type.replace('_', ' ') }
    return who to rest
}

/** Where a tap on a notification leads: its link inside the app; a follow with no link opens the person's page. */
fun openNotif(c: Context, n: Notif) {
    val link = n.link.trim()
    val who = NotifActors.known[n.actor.trim()]
    if ((link.isBlank() || link == "/") && who != null && who.first.isNotBlank()) { Nav.open(SoUserChannelPage(who.first, n.actor)); return }
    if (link.isBlank()) return
    soRoute(c, link)
}

@Composable
fun NotifRow(n: Notif, unread: Boolean, first: Boolean, onClick: () -> Unit) {
    val (who, action) = notifParts(n)
    val actor = NotifActors.known[who]
    val (ic, col) = notifBadge(n.type)
    val hi = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark()) 0.16f else 0.08f)
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).background(if (unread) hi else Color.Transparent)
        .then(if (first) Modifier.firstFocus() else Modifier)
        .tap(RoundedCornerShape(0.dp), onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp)) {
            Avatar(actor?.second, who.ifBlank { "YID PLUS" }, 50.dp)
            Box(Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(col)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
                Icon(ic, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
            val line = buildAnnotatedString {
                if (who.isNotBlank()) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(who) }; append(" ") }
                append(action)
            }
            Text(line, style = MaterialTheme.typography.bodyLarge.merge(TextStyle(textDirection = TextDirection.Ltr)),
                color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(ago(n.at), style = MaterialTheme.typography.labelMedium,
                color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        if (unread) Box(Modifier.size(10.dp).clip(CircleShape).background(BadgeGreen))
    }
}

/** The whole notifications page (used by the bell and by Settings → Activity). */
@Composable
fun NotifsPageBody() {
    val c = ctx()
    val scope = rememberCoroutineScope()
    val res = remember(Session.me?.id) { notifsRes() }
    val readNow = remember { mutableStateListOf<String>() }
    var allRead by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(res) { res.load(force = true); com.yidplus.app.data.Perf.shown("Notifications") }
    val list = res.data
    LaunchedEffect(list) { list?.map { it.actor }?.distinct()?.take(25)?.forEach { NotifActors.look(it) } }
    val anyUnread = !allRead && list?.any { !it.read && it.id !in readNow } == true
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar("Notifications", onBack = { Nav.back() }) {
            if (anyUnread) IconBtn(Sym.DoneAll, "Mark all read", {
                allRead = true; SoNotifCount.unread = 0
                scope.launch { runCatching { Api.post("/api/notifications", JSONObject().put("read", "all")) } }
                toast(c, "All marked as read")
            }, tint = LocalContentColor.current)
            if (!list.isNullOrEmpty()) IconBtn(Sym.DeleteSweep, "Clear all", { confirm = true }, tint = LocalContentColor.current)
        }
        when {
            Session.me == null -> EmptyNote(Sym.Notifications, "Sign in to see your notifications", action = "Sign in", onAction = { Nav.go(Tab.Profile) })
            list == null && res.error != null -> Problem(res.error!!, res.offline, { scope.launch { res.load(true) } })
            list == null -> SkeletonRows(rows = 7)
            list.isEmpty() -> EmptyNote(Sym.NotificationsNone, "No notifications yet", "Likes, comments and new followers show up here.")
            else -> LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(list, key = { it.id }) { n ->
                    val unread = !allRead && !n.read && n.id !in readNow
                    NotifRow(n, unread, first = n.id == list.first().id) {
                        if (unread) {
                            readNow.add(n.id); SoNotifCount.unread = (SoNotifCount.unread - 1).coerceAtLeast(0)
                            scope.launch { runCatching { Api.post("/api/notifications", JSONObject().put("read", n.id)) } }
                        }
                        openNotif(c, n)
                    }
                    HorizontalDivider(Modifier.padding(start = 82.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Clear all notifications?") },
        confirmButton = { TextButton({ confirm = false; scope.launch { runCatching { Api.delete("/api/notifications") }; res.data = emptyList(); SoNotifCount.unread = 0 } }) { Text("Clear") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
}
