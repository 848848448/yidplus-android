package com.yidplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yidplus.app.data.Api
import com.yidplus.app.data.Room
import com.yidplus.app.data.Session
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Edit a group or channel — the site's Edit page (chat.js openGroupEdit): photo, name + description (Save), then every switch saves AT ONCE
 * with its own PUT and flips back if the site says no (_geToggle / _geToggleSend / _gePermToggle). Sections: type & invite link,
 * what members can do (Send Messages, Send Media ›, Add Users, Pin Messages, Change Info), reactions / saving / history,
 * disappearing messages, featured (site owner), administrators ›, members ›, join requests.
 */
data class ChEditGroup(val room: Room) : PageScreen() {
    override val perfName get() = "EditGroup"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var s by remember { mutableStateOf<JSONObject?>(null) }
        var err by remember { mutableStateOf<String?>(null) }
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var savingText by remember { mutableStateOf(false) }
        var requests by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
        var v by remember { mutableIntStateOf(0) } // redraw after a flip
        var inviteOpen by remember { mutableStateOf(false) }
        var adAsk by remember { mutableStateOf(false) }
        var photo by remember { mutableStateOf(room.photo) }
        val isChannel = room.type == "channel"
        val what = if (isChannel) "Channel" else "Group"
        suspend fun loadReq() { runCatching { requests = ClApi.joinRequests(room.id) } }
        LaunchedEffect(room.id) {
            runCatching { ClApi.settings(room.id) }.onSuccess { j -> s = j; if (j != null) { name = j.chStr("name"); desc = j.chStr("description") } }.onFailure { err = Api.friendly(it) }
            loadReq()
        }
        val pickPhoto = rememberClPhotoPicker(room.id) { url -> if (url.isNotBlank()) photo = Api.abs(url); scope.launch { ChatApi.rooms.load(true) } }
        // One switch = one PUT {room_id, key: value}; flips back on failure, like the site.
        fun flip(key: String, now: Boolean, send: Any = now, done: String? = null) {
            val j = s ?: return
            val before = j.opt(key); j.put(key, send); v++
            scope.launch {
                runCatching { ClApi.set(room.id, key, send) }.onSuccess { if (done != null) toast(c, done); ChatApi.rooms.load(true) }
                    .onFailure { toast(c, "❌ " + Api.friendly(it)); j.put(key, before ?: JSONObject.NULL); v++ }
            }
        }
        fun perm(key: String, on: Boolean) {
            val j = s ?: return
            val p = j.optJSONObject("permissions") ?: JSONObject().also { j.put("permissions", it) }
            p.put(key, on); v++
            scope.launch { runCatching { ClApi.set(room.id, "permissions", JSONObject(p.toString())) }.onFailure { toast(c, "❌ " + Api.friendly(it)); p.put(key, !on); v++ } }
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChBar("Edit " + what.lowercase(), sub = room.name, onBack = { Nav.back() })
            if (s == null && err == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (err != null) { Text(err!!, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error); return@Column }
            val j = s ?: return@Column
            @Suppress("UNUSED_EXPRESSION") v
            val p = j.optJSONObject("permissions") ?: JSONObject()
            fun pget(k: String, d: Boolean) = if (p.has(k)) p.optBoolean(k, d) else d
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(CircleShape).tap(CircleShape) { pickPhoto() }) {
                        ChAvatar(photo, name.ifBlank { room.name }, room.id, 72.dp)
                        Box(Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape).background(ChatLook.unread), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PhotoCamera, "Change photo", tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        OutlinedTextField(name, { name = it.take(60) }, Modifier.fillMaxWidth().firstFocus(), singleLine = true, label = { Text("$what name") },
                            textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content))
                    }
                }
                OutlinedTextField(desc, { desc = it.take(255) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), maxLines = 4, label = { Text("Description") },
                    supportingText = { Text("${255 - desc.length}", Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End) },
                    textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content))
                PrimaryButton(if (savingText) "Saving…" else "Save name & description", {
                    if (name.trim().length < 2) { toast(c, "The name needs at least 2 letters"); return@PrimaryButton }
                    savingText = true
                    scope.launch { runCatching { ClApi.saveText(room.id, name, desc) }.onSuccess { j.put("name", name.trim()); j.put("description", desc); toast(c, "✅ Saved"); ChatApi.rooms.load(true) }.onFailure { toast(c, "❌ " + Api.friendly(it)) }; savingText = false }
                }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), enabled = !savingText)

                ClHdr("$what type")
                val public = j.chStr("visibility") == "public"
                ClSwitch(Icons.Filled.Public, "Public $what".lowercase().replaceFirstChar { it.uppercase() }, if (public) "Anyone can find and join" else "Only people with the invite link", public) {
                    flip("visibility", it, if (it) "public" else "private") }
                ClNav(Icons.Filled.Link, "Invite link", j.chStr("invite_code").takeIf { it.isNotBlank() }?.let { "${Api.BASE}/chat?join=$it" } ?: "Create a link") { inviteOpen = true }
                ClSwitch(Icons.Filled.HowToReg, "Approve new members", "An admin must approve joins", j.optBoolean("approve_members")) { flip("approve_members", it) }

                if (!isChannel) {
                    ClHdr("What members can do")
                    ClSwitch(Icons.Filled.Edit, "Send Messages", "Members can write", !j.optBoolean("read_only")) { on -> flip("read_only", on, !on, if (on) "✍️ Everyone can write now" else "🔒 Group set to admins-only") }
                    val mediaOn = CL_MEDIA_PERMS.count { (k, _) -> pget(k, k != "polls") }
                    ClNav(Icons.Filled.PermMedia, "Send Media", "$mediaOn/${CL_MEDIA_PERMS.size}") { Nav.open(ClSendMedia(room, j)) }
                    ClSwitch(Icons.Filled.PersonAdd, "Add Users", "Members can add others", pget("add_users", false)) { perm("add_users", it) }
                    ClSwitch(Icons.Filled.PushPin, "Pin Messages", null, pget("pin_messages", false)) { perm("pin_messages", it) }
                    ClSwitch(Icons.Filled.Info, "Change $what Info", "Name, photo, description", pget("change_info", false)) { perm("change_info", it) }
                }

                ClHdr("Messages")
                ClSwitch(Icons.Filled.AddReaction, "Reactions", "Members can react to messages", j.optBoolean("reactions_enabled", true)) { flip("reactions_enabled", it) }
                ClSwitch(Icons.Filled.Save, "Allow saving content", "Members can save and forward media", j.optBoolean("allow_saving", true)) { flip("allow_saving", it) }
                ClSwitch(Icons.Filled.History, "Chat history for new members", "New members see earlier messages", j.optBoolean("history_visible", true)) { flip("history_visible", it) }
                ClNav(Icons.Filled.Timer, "Disappearing messages", clAutoDeleteLabel(j.optInt("auto_delete_minutes", 0))) { adAsk = true }
                if (Session.me?.isOwner == true || Session.me?.role == "owner") ClSwitch(Icons.Filled.Star, "Featured", "Everyone sees this $what in discovery", j.optBoolean("featured")) { on ->
                    flip("featured", on, on, if (on) "⭐ Everyone will see this group" else "Group hidden from discovery — reachable by search or link") }

                ClHdr("People")
                val info = ChatApi.info(room.id)
                ClNav(Icons.Filled.AdminPanelSettings, "Administrators", "${(info?.members?.count { it.admin || it.id == info.createdBy } ?: 0).coerceAtLeast(1)}") { Nav.open(ClPeople(room, admins = true)) }
                ClNav(Icons.Filled.Group, if (isChannel) "Subscribers" else "Members", "${info?.members?.size ?: j.optInt("members")}") { Nav.open(ClPeople(room, admins = false)) }
                if (requests.isNotEmpty()) {
                    ClHdr("Join requests (${requests.size})")
                    requests.forEach { r ->
                        ChRow(r.chStr("nickname"), chatTime(r.chStr("created_at")), { ChAvatar(Api.abs(r.chStr("photo_url").ifBlank { null }), r.chStr("nickname"), r.chStr("user_id"), 44.dp) }, trail = {
                            ChIcon(Icons.Filled.Done, "Approve ${r.chStr("nickname")}", { scope.launch { runCatching { ClApi.answerJoin(r.opt("id"), true) }.onSuccess { toast(c, "✅ Approved") }.onFailure { toast(c, Api.friendly(it)) }; loadReq(); ChatApi.rooms.load(true) } }, tint = ChatLook.unread)
                            ChIcon(Icons.Filled.Close, "Decline ${r.chStr("nickname")}", { scope.launch { runCatching { ClApi.answerJoin(r.opt("id"), false) }.onFailure { toast(c, Api.friendly(it)) }; loadReq() } }, tint = Color(0xFFD32F2F))
                        }) { }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            if (inviteOpen) ClInviteDialog(room, j.chStr("invite_code").ifBlank { null }, true, { j.put("invite_code", it); v++ }) { inviteOpen = false }
            if (adAsk) ClAutoDeleteDialog(j.optInt("auto_delete_minutes", 0), onPick = { m -> adAsk = false; flip("auto_delete_minutes", m > 0, m, if (m > 0) "⏱ Auto-delete: " + clAutoDeleteLabel(m) else "Auto-delete off") }) { adAsk = false }
        }
    }
}

/** chat.js _geSendMediaPanel: the ten kinds of media members may send. */
internal val CL_MEDIA_PERMS = listOf("photos" to "Photos", "videos" to "Videos", "stickers" to "Stickers & GIFs", "music" to "Music", "files" to "Files",
    "voice" to "Voice Messages", "video_msg" to "Video Messages", "links" to "Embed Links", "polls" to "Polls", "reactions" to "Reactions")

data class ClSendMedia(val room: Room, val s: JSONObject) : PageScreen() {
    override val perfName get() = "SendMedia"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var v by remember { mutableIntStateOf(0) }
        val p = s.optJSONObject("permissions") ?: JSONObject().also { s.put("permissions", it) }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChBar("Send Media", sub = room.name, onBack = { Nav.back() })
            @Suppress("UNUSED_EXPRESSION") v
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text("What members can send in this group.", Modifier.padding(16.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CL_MEDIA_PERMS.forEachIndexed { i, (k, label) ->
                    val on = if (p.has(k)) p.optBoolean(k) else k != "polls"
                    ClSwitch(null, label, null, on, if (i == 0) Modifier.firstFocus() else Modifier) { now ->
                        p.put(k, now); v++
                        scope.launch { runCatching { ClApi.set(room.id, "permissions", JSONObject(p.toString())) }.onFailure { toast(c, "❌ " + Api.friendly(it)); p.put(k, !now); v++ } }
                    }
                }
            }
        }
    }
}

/** Administrators / Members of a group (chat.js _geAdminsPanel / _geMembersPanel): tap a person for Set title, Make / Dismiss admin, Remove. */
data class ClPeople(val room: Room, val admins: Boolean) : PageScreen() {
    override val perfName get() = if (admins) "Admins" else "Members"
    @Composable override fun Content() {
        val scope = rememberCoroutineScope(); val me = Session.me
        val info = ChatApi.info(room.id)
        var menu by remember { mutableStateOf<ChMember?>(null) }
        var q by remember { mutableStateOf("") }
        val all = info?.members.orEmpty()
        val list = all.filter { !admins || it.admin || it.id == info?.createdBy }.filter { q.isBlank() || it.nick.contains(q, true) }
            .sortedWith(compareByDescending<ChMember> { it.id == info?.createdBy }.thenByDescending { it.admin }.thenBy { it.nick.lowercase() })
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChBar(if (admins) "Administrators" else if (room.type == "channel") "Subscribers" else "Members", sub = "${list.size}", onBack = { Nav.back() })
            if (!admins) OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), singleLine = true, placeholder = { Text("Search members") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content))
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                if (!admins && info?.canManage == true) item { ChRow("Add members", null, { Box(Modifier.size(44.dp).clip(CircleShape).background(ChatLook.bar), contentAlignment = Alignment.Center) { Icon(Icons.Filled.PersonAdd, null, tint = Color.White) } }, titleColor = ChatLook.bar) { Nav.open(ChPickUsers(room.id, room.name)) } }
                if (admins) item { Text("Admins can edit the group, add and remove members and pin messages. Tap a member to make them an admin.", Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(list.size, key = { list[it].id }) { i -> val mb = list[i]
                    ChRow(mb.nick + if (mb.id == me?.id) " (You)" else "", mb.title.ifBlank { null }, { ChAvatar(mb.photo, mb.nick, mb.id, 44.dp) },
                        trail = { if (mb.admin || mb.id == info?.createdBy) Text(if (mb.id == info?.createdBy) "Owner" else "Admin", fontSize = 12.sp, color = ChatLook.bar) }) { if (mb.id != me?.id) menu = mb }
                }
                if (admins) item { ChRow("Add admin", null, { Box(Modifier.size(44.dp).clip(CircleShape).background(ChatLook.bar), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, null, tint = Color.White) } }, titleColor = ChatLook.bar) { Nav.open(ClPeople(room, admins = false)) } }
            }
        }
        menu?.let { mb -> ClMemberSheet(room, mb, info?.canManage == true, mb.id == info?.createdBy, { scope.launch { ChatApi.rooms.load(true) } }) { menu = null } }
    }
}

@Composable
internal fun ClHdr(t: String) {
    HorizontalDivider(thickness = 6.dp, color = MaterialTheme.colorScheme.surfaceVariant)
    Text(t, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ChatLook.bar.let { if (ChatLook.dark()) Color(0xFF7FD3BC) else it })
}

@Composable
internal fun ClSwitch(icon: ImageVector?, title: String, sub: String?, on: Boolean, modifier: Modifier = Modifier, set: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).tap(RoundedCornerShape(0.dp)) { set(!on) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(18.dp)) }
        Column(Modifier.weight(1f)) { Text(title, fontSize = 16.sp); if (sub != null) Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(on, null)
    }
}

@Composable
internal fun ClNav(icon: ImageVector, title: String, value: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).tap(RoundedCornerShape(0.dp), onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) { Text(title, fontSize = 16.sp); if (!value.isNullOrBlank()) Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
