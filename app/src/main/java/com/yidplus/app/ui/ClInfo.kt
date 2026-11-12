package com.yidplus.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yidplus.app.data.Api
import com.yidplus.app.data.Room
import com.yidplus.app.data.Session
import kotlinx.coroutines.launch

/* ═════════════ CL: chat info extras — Media / Files / Links / Voice tabs, invite sheet, report, member actions, photo change ═════════════ */

private val URL_RX = Regex("""https?://[^\s<>"]+""")

/** media-history.js: the info page's four tabs, paged with ?before=&before_id= ("Load more" until has_more is false). */
@Composable
internal fun ClMediaTabs(roomId: String) {
    var tab by remember { mutableStateOf(0) }
    val msgs = remember(roomId) { mutableStateListOf<ChatMsg>() }
    var more by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope(); val c = ctx()
    suspend fun page(first: Boolean) {
        loading = true
        val old = msgs.minByOrNull { it.createdAt ?: "" }
        runCatching { ClApi.history(roomId, if (first) null else old?.createdAt, if (first) null else old?.id) }.onSuccess { j ->
            val got = chObjs(j.optJSONArray("messages")).map(ChatMsg::from).filter { m -> msgs.none { it.id == m.id } }
            msgs.addAll(got); more = j.optBoolean("has_more") && got.isNotEmpty()
        }.onFailure { if (!first) toast(c, Api.friendly(it)) }
        loading = false
    }
    LaunchedEffect(roomId) { page(true) }
    val all = msgs.filter { !it.deleted && !it.viewOnce }.sortedByDescending { it.createdAt ?: "" }
    val media = all.filter { (it.isImage || it.isVideo) && it.media != null }
    val files = all.filter { it.isFile || it.isMusic }
    val links = all.filter { !it.isCentred && URL_RX.containsMatchIn(it.text) }
    val voice = all.filter { it.isVoice && it.media != null }
    val tabs = listOf("Media" to media.size, "Files" to files.size, "Links" to links.size, "Voice" to voice.size)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEachIndexed { i, (label, n) ->
            val on = tab == i
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) ChatLook.bar else MaterialTheme.colorScheme.surfaceVariant).tap(RoundedCornerShape(16.dp)) { tab = i }
                .padding(horizontal = 14.dp, vertical = 7.dp)) {
                Text(if (n > 0) "$label $n" else label, color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
    val shown = when (tab) { 0 -> media; 1 -> files; 2 -> links; else -> voice }
    if (shown.isEmpty() && !loading) Text(when (tab) { 0 -> "No photos or videos yet"; 1 -> "No files yet"; 2 -> "No links yet"; else -> "No voice messages yet" },
        Modifier.fillMaxWidth().padding(16.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    when (tab) {
        0 -> {
            // 3 a row, square tiles (WhatsApp's media grid); tap = the full-screen viewer with swipe between them.
            shown.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEach { m ->
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(Color(0xFF263238))
                            .tap(RoundedCornerShape(4.dp)) { openGallery(media.map { GItem(it.media!!, it.isVideo, null, it.nick) }, m.media!!) }, contentAlignment = Alignment.Center) {
                            if (m.isImage) AsyncImage(m.media, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon(Icons.Filled.PlayCircle, "Video", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        1 -> shown.forEach { m -> ChRow(m.fileName, m.nick + " · " + chatTime(m.createdAt), { ClTileIcon(if (m.isMusic) Icons.Filled.MusicNote else Icons.Filled.Description) }) { m.media?.let { openUrl(c, it) } } }
        2 -> shown.forEach { m -> val u = URL_RX.find(m.text)?.value ?: ""
            ChRow(u.removePrefix("https://").removePrefix("http://").take(60), m.text.replace(u, "").trim().ifBlank { m.nick + " · " + chatTime(m.createdAt) }, { ClTileIcon(Icons.Filled.Link) }) { openUrl(c, u) } }
        else -> shown.forEach { m -> ChRow("🎤 Voice message" + (m.voiceDur.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), m.nick + " · " + chatTime(m.createdAt), { ClTileIcon(Icons.Filled.Mic) }) { m.media?.let { openUrl(c, it) } } }
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
    else if (more) TextButton({ scope.launch { page(false) } }, Modifier.fillMaxWidth()) { Text("Load more") }
}

@Composable
private fun ClTileIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(ChatLook.bar.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = ChatLook.bar) }
}

/** The invite link (chat.js _geCopyInvite / _geRevokeInvite): copy, share, and — for managers — revoke and make a new one. */
@Composable
internal fun ClInviteDialog(room: Room, code0: String?, canManage: Boolean, onCode: (String) -> Unit, onDismiss: () -> Unit) {
    val c = ctx(); val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(code0) }
    var revokeAsk by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (code == null && canManage) runCatching { ChatApi.invite(room.id) }.onSuccess { code = it; onCode(it) }.onFailure { toast(c, Api.friendly(it)) } }
    val url = code?.let { "${Api.BASE}/chat?join=$it" }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Invite link") }, text = {
        Column {
            Text(url ?: "Making a link…", fontSize = 15.sp, color = ChatLook.bar, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp))
            Spacer(Modifier.height(6.dp))
            Text("Anyone with this link can " + (if (room.type == "channel") "join this channel." else "ask to join or join this group."), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (url != null) {
                ChDialogRow("Copy link") { chCopy(c, url); toast(c, "✅ Invite link copied") }
                ChDialogRow("Share link") { share(c, "Join \"${room.name}\" on YID PLUS: $url") }
                if (canManage) ChDialogRow("Revoke link", Color(0xFFD32F2F)) { revokeAsk = true }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("Close") } })
    if (revokeAsk) AlertDialog(onDismissRequest = { revokeAsk = false }, text = { Text("Revoke the current invite link and create a new one? The old link will stop working.", fontSize = 16.sp) },
        confirmButton = { TextButton({ revokeAsk = false; scope.launch { runCatching { ClApi.revokeInvite(room.id) }.onSuccess { val n = it.ifBlank { code ?: "" }; code = n; onCode(n); toast(c, "🔗 New invite link created") }.onFailure { toast(c, Api.friendly(it)) } } }) { Text("Revoke", color = Color(0xFFD32F2F)) } },
        dismissButton = { TextButton({ revokeAsk = false }) { Text("Cancel") } })
}

/** The site's report sheet (yidplus-chat.html #report-modal → submitReport). */
@Composable
internal fun ClReportDialog(reportedId: String, roomId: String?, onDismiss: () -> Unit) {
    val c = ctx(); val scope = rememberCoroutineScope()
    val reasons = listOf("spam" to "Spam", "harassment" to "Harassment", "inappropriate" to "Inappropriate Content", "other" to "Other")
    var pick by remember { mutableStateOf(0) }
    var details by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("🚩 Report") }, text = {
        Column {
            reasons.forEachIndexed { i, (_, label) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).tap(RoundedCornerShape(8.dp)) { pick = i }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(pick == i, null); Text(label, Modifier.padding(start = 10.dp), fontSize = 16.sp)
                }
            }
            OutlinedTextField(details, { details = it.take(500) }, Modifier.fillMaxWidth().padding(top = 6.dp), placeholder = { Text("Additional details (optional)") }, maxLines = 3,
                textStyle = TextStyle(fontSize = 15.sp, textDirection = TextDirection.Content))
        }
    }, confirmButton = { TextButton({
        busy = true
        scope.launch { runCatching { ClApi.report(reportedId, roomId, reasons[pick].first, details.trim()) }.onSuccess { toast(c, "✅ Report submitted. Thank you!"); onDismiss() }.onFailure { toast(c, Api.friendly(it)) }; busy = false }
    }, enabled = !busy) { Text("Submit Report") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

/** chat.js openMemberActions: Message, Set title, Make / Dismiss admin, Remove (asks first). */
@Composable
internal fun ClMemberSheet(room: Room, mb: ChMember, canManage: Boolean, isOwnerRow: Boolean, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val c = ctx(); val scope = rememberCoroutineScope()
    var titleAsk by remember { mutableStateOf(false) }
    var removeAsk by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChAvatar(mb.photo, mb.nick, mb.id, 40.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(mb.nick, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                if (mb.title.isNotBlank()) Text(mb.title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }, text = {
        Column {
            ChDialogRow("💬 Message ${mb.nick}") { onDismiss(); scope.launch { runCatching { ChatApi.openDm(mb.id) }.onSuccess { id -> Nav.open(Screen.Chat(ChatApi.roomOf(id, "private", mb.nick, mb.photo, mb.id))) }.onFailure { toast(c, Api.friendly(it)) } } }
            if (canManage && !isOwnerRow) {
                ChDialogRow("🏷 Set title") { titleAsk = true }
                ChDialogRow(if (mb.admin) "⬇ Dismiss as admin" else "⭐ Make admin", ChatLook.bar) {
                    scope.launch { runCatching { ChatApi.setAdmin(room.id, mb.id, !mb.admin) }.onSuccess { toast(c, if (mb.admin) "Admin removed" else "⭐ ${mb.nick} is now an admin"); onChanged(); onDismiss() }.onFailure { toast(c, Api.friendly(it)) } }
                }
                ChDialogRow("🚫 Remove from group", Color(0xFFD32F2F)) { removeAsk = true }
            }
            ChDialogRow("🚩 Report ${mb.nick}", Color(0xFFD32F2F)) { report = true }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("Close") } })
    if (titleAsk) {
        var t by remember { mutableStateOf(mb.title) }
        AlertDialog(onDismissRequest = { titleAsk = false }, title = { Text("Title for ${mb.nick}") }, text = {
            Column {
                OutlinedTextField(t, { t = it.take(32) }, Modifier.fillMaxWidth().firstFocus(always = true), singleLine = true, placeholder = { Text("e.g. Gabbai, Moderator") },
                    textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content))
                Text("Shown next to their name. Leave empty to remove.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }, confirmButton = { TextButton({ titleAsk = false; scope.launch { runCatching { ClApi.setTitle(room.id, mb.id, t) }.onSuccess { toast(c, if (t.isBlank()) "Title removed" else "🏷 Title set"); onChanged(); onDismiss() }.onFailure { toast(c, Api.friendly(it)) } } }) { Text("Save") } },
            dismissButton = { TextButton({ titleAsk = false }) { Text("Cancel") } })
    }
    if (removeAsk) AlertDialog(onDismissRequest = { removeAsk = false }, text = { Text("Remove ${mb.nick} from \"${room.name}\"?", fontSize = 16.sp) },
        confirmButton = { TextButton({ removeAsk = false; scope.launch { runCatching { ChatApi.removeMember(room.id, mb.id) }.onSuccess { toast(c, "Removed"); onChanged(); onDismiss() }.onFailure { toast(c, Api.friendly(it)) } } }) { Text("Remove", color = Color(0xFFD32F2F)) } },
        dismissButton = { TextButton({ removeAsk = false }) { Text("Cancel") } })
    if (report) ClReportDialog(mb.id, room.id) { report = false; onDismiss() }
}

/** Disappearing messages (chat.js openDisappearingMessages → setAutoDelete). */
@Composable
internal fun ClAutoDeleteDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("⏱ Disappearing messages") }, text = {
        Column {
            Text("New messages disappear after the time you pick.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            CL_AUTO_DELETE.forEach { (m, label) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).tap(RoundedCornerShape(8.dp)) { onPick(m) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(current == m, null); Text(label, Modifier.padding(start = 10.dp), fontSize = 16.sp)
                }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("Cancel") } })
}

/** Group photo picker → PUT /api/chat/rooms multipart {room_id, photo} (yidplus-chat.html handleGroupPhoto). */
@Composable
internal fun rememberClPhotoPicker(roomId: String, onDone: (String) -> Unit): () -> Unit {
    val c = ctx(); val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u: Uri? ->
        if (u != null) scope.launch { toast(c, "Uploading photo…"); runCatching { ClApi.setPhoto(c, roomId, u) }.onSuccess { toast(c, "✅ Photo updated"); onDone(it) }.onFailure { toast(c, Api.friendly(it)) } }
    }
    return { pick.launch("image/*") }
}
