package com.yidplus.app.ui

import android.net.Uri
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.yidplus.app.data.Api
import com.yidplus.app.data.Room
import com.yidplus.app.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/* CL: the chat / group / channel info page (moved out of ChatPages.kt). */

/** Group / channel / contact info: photo, name, description, mute, invite link, members (admins can add, remove, make admin), media, leave. */
data class ChInfo(val room: Room) : PageScreen() {
    override val perfName get() = "ChatInfo"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val me = Session.me
        val res = ChatApi.rooms
        LaunchedEffect(Unit) { res.load(true) }
        val info = res.data?.firstOrNull { it.room.id == room.id }
        var memberMenu by remember { mutableStateOf<ChMember?>(null) }
        var confirm by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
        var muteAsk by remember { mutableStateOf(false) }
        var inviteOpen by remember { mutableStateOf(false) }
        var report by remember { mutableStateOf(false) }
        var adAsk by remember { mutableStateOf(false) }
        var code by remember(info?.inviteCode) { mutableStateOf(info?.inviteCode) }
        var photo by remember(room.photo) { mutableStateOf(room.photo) }
        var settings by remember { mutableStateOf<JSONObject?>(null) }
        val isPrivate = room.type == "private"
        val manage = info?.canManage == true && !isPrivate
        LaunchedEffect(manage) { if (manage) settings = runCatching { ClApi.settings(room.id) }.getOrNull() }
        var autoDel by remember(settings) { mutableStateOf(settings?.optInt("auto_delete_minutes", 0) ?: 0) }
        val pickPhoto = rememberClPhotoPicker(room.id) { url -> if (url.isNotBlank()) photo = Api.abs(url); scope.launch { res.load(true) } }
        // The site shows "online" only to site admins (chat.js: isAnyAdmin()).
        val seeOnline = chIsSiteAdmin()
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChBar(if (isPrivate) "Contact info" else if (room.type == "group") "Group info" else "Channel info", onBack = { Nav.back() }) {
                if (manage) ChIcon(Sym.Edit, "Edit", { Nav.open(ChEditGroup(room)) })
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(if (manage) Modifier.clip(CircleShape).tap(CircleShape) { pickPhoto() } else Modifier) {
                        ChAvatar(photo, room.name, room.otherUserId ?: room.id, 96.dp, online = isPrivate && info?.online == true && seeOnline)
                        if (manage) Box(Modifier.align(Alignment.BottomEnd).size(32.dp).clip(CircleShape).background(ChatLook.unread), contentAlignment = Alignment.Center) {
                            Icon(Sym.PhotoCamera, "Change photo", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(info?.room?.name ?: room.name, Modifier.padding(horizontal = 16.dp), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, style = TextStyle(textDirection = TextDirection.Content))
                    val n = info?.members?.size?.takeIf { it > 0 } ?: room.members
                    Text(when { isPrivate -> if (info?.online == true && seeOnline) "online" else "Private chat"; room.type == "channel" -> "Channel · $n subscribers"
                        else -> "Group · $n members" } + (if (!isPrivate && info?.visibility == "public") " · Public" else ""), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (me != null) Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InfoAction(Sym.Chat, "Message") { Nav.back() }
                        if (isPrivate || room.type == "group") InfoAction(Sym.Call, "Call") { Nav.open(ChCall(room, "audio")) }
                        if (isPrivate || room.type == "group") InfoAction(Sym.Videocam, "Video") { Nav.open(ChCall(room, "video")) }
                        if (!isPrivate && manage) InfoAction(Sym.PersonAdd, "Add") { Nav.open(ChPickUsers(room.id, room.name)) }
                        else InfoAction(Sym.Search, "Search") { Nav.back() }
                    }
                }
                HorizontalDivider(thickness = 6.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                if (!info?.description.isNullOrBlank()) {
                    Text(info!!.description, Modifier.padding(16.dp), fontSize = 15.sp, style = TextStyle(textDirection = TextDirection.Content))
                    HorizontalDivider(thickness = 6.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                }
                if (info != null && me != null) {
                    val until = ClLocal.muteUntil(c, room.id)
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).tap(RoundedCornerShape(0.dp)) {
                        if (info.muted) scope.launch { runCatching { ChatApi.mute(room.id, false) }.onSuccess { ClLocal.setMuteUntil(c, room.id, 0); toast(c, "🔊 Unmuted"); res.load(true) }.onFailure { toast(c, Api.friendly(it)) } }
                        else muteAsk = true
                    }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Sym.NotificationsOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f).padding(start = 18.dp)) {
                            Text("Mute notifications", fontSize = 16.sp)
                            if (info.muted) Text(if (until > 0) "Until " + java.text.SimpleDateFormat("EEE h:mm a", java.util.Locale.US).format(java.util.Date(until)) else "Always", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(info.muted, null)
                    }
                    if (manage) Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).tap(RoundedCornerShape(0.dp)) { adAsk = true }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Sym.Timer, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f).padding(start = 18.dp)) { Text("Disappearing messages", fontSize = 16.sp); Text(clAutoDeleteLabel(autoDel), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (!isPrivate && (code != null || manage)) Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).tap(RoundedCornerShape(0.dp)) { inviteOpen = true }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Sym.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f).padding(start = 18.dp)) { Text("Invite link", fontSize = 16.sp); Text(code?.let { "${Api.BASE}/chat?join=$it" } ?: "Create and share a link", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        Icon(Sym.Share, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(thickness = 6.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                }
                ClMediaTabs(room.id)
                HorizontalDivider(thickness = 6.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                if (info != null && !isPrivate && info.members.isNotEmpty()) {
                    ChSection("${info.members.size} members" + (if (seeOnline) info.onlineCount.takeIf { it > 0 }?.let { " · $it online" } ?: "" else ""))
                    if (manage) ChRow("Add members", null, { Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Icon(Sym.PersonAdd, null, tint = Color.White) } },
                        titleColor = MaterialTheme.colorScheme.primary) { Nav.open(ChPickUsers(room.id, room.name)) }
                    if (manage || code != null) ChRow("Invite via link", null, { Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Icon(Sym.Link, null, tint = Color.White) } },
                        titleColor = MaterialTheme.colorScheme.primary) { inviteOpen = true }
                    info.members.sortedWith(compareByDescending<ChMember> { it.id == info.createdBy }.thenByDescending { it.admin }.thenByDescending { it.online && seeOnline }.thenBy { it.nick.lowercase() }).take(300).forEach { mb ->
                        val sub = listOfNotNull(mb.title.ifBlank { null }, if (mb.online && seeOnline) "online" else null).joinToString(" · ").ifBlank { null }
                        ChRow(mb.nick + if (mb.id == me?.id) " (You)" else "", sub, { ChAvatar(mb.photo, mb.nick, mb.id, 44.dp, mb.online && seeOnline) },
                            trail = { if (mb.admin || mb.id == info.createdBy) Text(if (mb.id == info.createdBy) "Owner" else "Admin", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)).padding(horizontal = 6.dp, vertical = 2.dp)) }) {
                            if (mb.id != me?.id && me != null) memberMenu = mb
                        }
                    }
                }
                if (info != null && me != null && info.joined) {
                    Spacer(Modifier.height(8.dp))
                    if (isPrivate && room.otherUserId != null) ChRow("Block ${room.name}", null, { Icon(Sym.Block, null, Modifier.size(44.dp).padding(10.dp), tint = Color(0xFFD32F2F)) }, titleColor = Color(0xFFD32F2F)) {
                        confirm = "Block ${room.name}? They won't be able to message you." to suspend { ChatApi.block(room.otherUserId ?: ""); toast(c, "Blocked"); Unit }
                    }
                    if (info.createdBy != me.id) ChRow("Report " + if (isPrivate) room.name else if (room.type == "group") "group" else "channel", null, { Icon(Sym.Flag, null, Modifier.size(44.dp).padding(10.dp), tint = Color(0xFFD32F2F)) }, titleColor = Color(0xFFD32F2F)) { report = true }
                    ChRow(if (isPrivate) "Delete chat" else if (room.type == "group") "Leave group" else "Leave channel", null, { Icon(Sym.ExitToApp, null, Modifier.size(44.dp).padding(10.dp), tint = Color(0xFFD32F2F)) }, titleColor = Color(0xFFD32F2F)) {
                        confirm = (if (isPrivate) "Delete chat with \"${room.name}\"?" else "Leave \"${room.name}\"?") to suspend { ChatApi.leave(room.id); res.load(true); Nav.back(); Nav.back(); Unit }
                    }
                    if (!isPrivate && (info.createdBy == me.id || chIsSiteAdmin())) ChRow("Delete for everyone", null, { Icon(Sym.DeleteForever, null, Modifier.size(44.dp).padding(10.dp), tint = Color(0xFFD32F2F)) }, titleColor = Color(0xFFD32F2F)) {
                        confirm = "Delete \"${room.name}\" and all its messages for everyone?" to suspend { ChatApi.leave(room.id, all = true); res.load(true); Nav.back(); Nav.back(); Unit }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        memberMenu?.let { mb -> ClMemberSheet(room, mb, manage, mb.id == info?.createdBy, { scope.launch { res.load(true) } }) { memberMenu = null } }
        if (muteAsk) ClMuteDialog("Mute \"${room.name}\"?", onPick = { ms ->
            muteAsk = false
            scope.launch { runCatching { ChatApi.mute(room.id, true) }.onSuccess { ClLocal.setMuteUntil(c, room.id, if (ms > 0) System.currentTimeMillis() + ms else 0); toast(c, "🔇 Muted"); res.load(true) }.onFailure { toast(c, Api.friendly(it)) } }
        }, onDismiss = { muteAsk = false })
        if (inviteOpen) ClInviteDialog(room, code, manage, { code = it; scope.launch { res.load(true) } }) { inviteOpen = false }
        if (report) ClReportDialog(room.otherUserId ?: info?.createdBy ?: "", room.id) { report = false }
        if (adAsk) ClAutoDeleteDialog(autoDel, onPick = { m ->
            adAsk = false
            scope.launch { runCatching { ClApi.set(room.id, "auto_delete_minutes", m) }.onSuccess { autoDel = m; toast(c, if (m > 0) "⏱ Auto-delete: " + clAutoDeleteLabel(m) else "Auto-delete off") }.onFailure { toast(c, Api.friendly(it)) } }
        }) { adAsk = false }
        confirm?.let { (q, act) ->
            AlertDialog(onDismissRequest = { confirm = null }, text = { Text(q, fontSize = 16.sp) },
                confirmButton = { TextButton({ confirm = null; scope.launch { runCatching { act() }.onFailure { toast(c, Api.friendly(it)) } } }) { Text("Yes", color = Color(0xFFD32F2F)) } },
                dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } })
        }
    }
}

