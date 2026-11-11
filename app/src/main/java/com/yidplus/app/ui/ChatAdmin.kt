package com.yidplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yidplus.app.data.Api
import com.yidplus.app.data.Room
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Edit a group or channel (its admins): name, description, public/private, who can write, approve new members, reactions, saving; pending join requests. */
data class ChEditGroup(val room: Room) : PageScreen() {
    override val perfName get() = "EditGroup"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var s by remember { mutableStateOf<JSONObject?>(null) }
        var err by remember { mutableStateOf<String?>(null) }
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var public by remember { mutableStateOf(false) }
        var readOnly by remember { mutableStateOf(false) }
        var approve by remember { mutableStateOf(false) }
        var reactions by remember { mutableStateOf(true) }
        var saving by remember { mutableStateOf(true) }
        var history by remember { mutableStateOf(true) }
        var busy by remember { mutableStateOf(false) }
        var requests by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
        suspend fun loadReq() { runCatching { requests = chObjs(Api.get("/api/chat/join-requests?room_id=" + chEnc(room.id)).optJSONArray("requests")) } }
        LaunchedEffect(room.id) {
            runCatching { Api.get("/api/chat/rooms?group_settings=" + chEnc(room.id)).optJSONObject("settings") }.onSuccess { j ->
                s = j; if (j != null) { name = j.chStr("name"); desc = j.chStr("description"); public = j.chStr("visibility") == "public"; readOnly = j.optBoolean("read_only")
                    approve = j.optBoolean("approve_members"); reactions = j.optBoolean("reactions_enabled", true); saving = j.optBoolean("allow_saving", true); history = j.optBoolean("history_visible", true) }
            }.onFailure { err = Api.friendly(it) }
            loadReq()
        }
        fun save() {
            if (name.isBlank()) { toast(c, "The name can't be empty"); return }
            busy = true
            scope.launch {
                runCatching {
                    Api.put("/api/chat/rooms", JSONObject().put("room_id", room.id).put("name", name.trim()).put("description", desc.trim()).put("visibility", if (public) "public" else "private")
                        .put("read_only", readOnly).put("approve_members", approve).put("reactions_enabled", reactions).put("allow_saving", saving).put("history_visible", history))
                }.onSuccess { toast(c, "Saved"); ChatApi.rooms.load(true); Nav.back() }.onFailure { toast(c, Api.friendly(it)) }
                busy = false
            }
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChBar(if (room.type == "channel") "Edit channel" else "Edit group", sub = room.name, onBack = { Nav.back() }) { if (s != null) ChIcon(Icons.Filled.Check, "Save", { save() }) }
            if (busy || (s == null && err == null)) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (err != null) { Text(err!!, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error); return@Column }
            if (s == null) return@Column
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(name, { name = it.take(60) }, Modifier.fillMaxWidth().firstFocus(), singleLine = true, label = { Text("Name") }, textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content))
                    OutlinedTextField(desc, { desc = it.take(255) }, Modifier.fillMaxWidth().padding(top = 8.dp), maxLines = 4, label = { Text("Description") }, textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content))
                }
                ChToggle("Public", if (public) "Anyone can find and join" else "Only with the invite link", public) { public = it }
                ChToggle("Only admins can write", "Members can read but not send", readOnly) { readOnly = it }
                ChToggle("Approve new members", "People ask to join and an admin approves", approve) { approve = it }
                ChToggle("Reactions", "Members can react to messages", reactions) { reactions = it }
                ChToggle("Allow saving", "Members can save and forward media", saving) { saving = it }
                ChToggle("History for new members", "New members see earlier messages", history) { history = it }
                PrimaryButton("Save", { save() }, Modifier.fillMaxWidth().padding(16.dp), enabled = !busy)
                if (requests.isNotEmpty()) {
                    HorizontalDivider(thickness = 6.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                    ChSection("Join requests (${requests.size})")
                    requests.forEach { r ->
                        ChRow(r.chStr("nickname"), chatTime(r.chStr("created_at")), { ChAvatar(Api.abs(r.chStr("photo_url").ifBlank { null }), r.chStr("nickname"), r.chStr("user_id"), 44.dp) }, trail = {
                            ChIcon(Icons.Filled.Done, "Approve", { scope.launch { runCatching { Api.post("/api/chat/join-requests", JSONObject().put("request_id", r.opt("id")).put("action", "approve")) }.onFailure { toast(c, Api.friendly(it)) }; loadReq(); ChatApi.rooms.load(true) } }, tint = ChatLook.unread)
                            ChIcon(Icons.Filled.Close, "Decline", { scope.launch { runCatching { Api.post("/api/chat/join-requests", JSONObject().put("request_id", r.opt("id")).put("action", "reject")) }.onFailure { toast(c, Api.friendly(it)) }; loadReq() } }, tint = MaterialTheme.colorScheme.error)
                        }) { }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ChToggle(title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).tap(RoundedCornerShape(0.dp)) { set(!on) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontSize = 16.sp); Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(on, null)
    }
}
