package com.yidplus.app.ui

import androidx.compose.foundation.text.KeyboardActions
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yidplus.app.data.Api
import com.yidplus.app.data.Res
import com.yidplus.app.data.Session
import com.yidplus.app.data.Store
import com.yidplus.app.data.objects
import com.yidplus.app.data.str
import com.yidplus.app.data.strOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

// ── Member channels (admin.js buildChannelsMgrPanel: GET /api/channels; verify = PUT /api/admin/users {id: owner_id, verified};
//    delete = DELETE /api/channels?id=) ─────────────────────────────────────────────────────────────────────────────

class AdminChannelsPage : PageScreen() {
    override val perfName = "AdminChannels"
    override fun equals(other: Any?) = other is AdminChannelsPage
    override fun hashCode() = 311
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        val res = remember { Res("/api/channels") { it } }
        val gone = remember { mutableStateListOf<String>() }
        val ver = remember { mutableStateMapOf<String, Boolean>() }
        var del by remember { mutableStateOf<JSONObject?>(null) }
        var q by remember { mutableStateOf("") }
        AdminList("Channels", sub = "Member channels", res = res, tick = 0, header = { AdminSearchField(q, { q = it }, "Search channels") }) { d ->
            val ql = q.trim().lowercase()
            val list = d.optJSONArray("channels").objects().filter { it.str("id") !in gone && (ql.isEmpty() || it.str("nickname").lowercase().contains(ql) || it.str("name").lowercase().contains(ql)) }
            if (list.isEmpty()) item { EmptyNote(Icons.Filled.Campaign, "No channels yet", modifier = Modifier.fillMaxWidth().padding(top = 40.dp)) }
            items(list, key = { it.str("id") }) { ch ->
                val id = ch.str("id")
                val verified = ver[id] ?: (ch.optInt("verified") == 1 || ch.optBoolean("verified"))
                val name = ch.str("nickname").ifBlank { ch.str("name") }
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(Api.abs(ch.strOrNull("photo_url")), name.ifBlank { "C" }, 48.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("@$name", style = MaterialTheme.typography.titleMedium.merge(TextStyle(textDirection = TextDirection.Ltr)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (verified) Icon(Icons.Filled.Verified, "Verified", tint = BadgeGreen, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                            }
                            Text(adminN(ch.optInt("followers")) + " followers · " + adminN(ch.optInt("total_views")) + " views", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        AdminPill(if (verified) "Unverify" else "Verify", if (verified) MaterialTheme.colorScheme.onSurfaceVariant else BadgeGreen, modifier = if (ch === list.first()) Modifier.firstFocus() else Modifier) {
                            adminDo(c, scope, if (!verified) "✅ Verified!" else "Unverified", { ver[id] = !verified }) {
                                Api.put("/api/admin/users", JSONObject().put("id", ch.str("owner_id")).put("verified", !verified))
                            }
                        }
                        IconBtn(Icons.Filled.Delete, "Delete channel", { del = ch }, tint = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider(Modifier.padding(start = 76.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        del?.let { ch ->
            val nick = ch.str("nickname")
            SettingsConfirmDialog("Delete channel @$nick?", "This cannot be undone.", "Delete", danger = true, onOk = {
                adminDo(c, scope, "🗑 Deleted", { gone.add(ch.str("id")) }) { Api.delete("/api/channels?id=" + Uri.encode(ch.str("id"))) }
            }, onDismiss = { del = null })
        }
    }
}

// ── Telegram channels (admin.js _tgcLoad / tgcAdd / tgcRemove: GET, POST {username,title,is_private,allowed_users}, DELETE ?id=) ──

class AdminTelegramPage : PageScreen() {
    override val perfName = "AdminTelegram"
    override fun equals(other: Any?) = other is AdminTelegramPage
    override fun hashCode() = 312
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        val res = remember { Res("/api/telegram-channels") { it } }
        var tick by remember { mutableIntStateOf(0) }
        val gone = remember { mutableStateListOf<String>() }
        var del by remember { mutableStateOf<JSONObject?>(null) }
        var user by remember { mutableStateOf("") }
        var title by remember { mutableStateOf("") }
        var priv by remember { mutableStateOf(false) }
        var allowed by remember { mutableStateOf("") }
        val kb = LocalSoftwareKeyboardController.current
        AdminList("Telegram channels", sub = "Shown in the Channels tab", res = res, tick = tick, header = {
            SettingsCard("Add a public Telegram channel") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("@channelusername") })
                    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Display name (optional)") })
                    Row(Modifier.fillMaxWidth().tap(RoundedCornerShape(8.dp)) { priv = !priv }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Private (only the people below can see it)", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(priv, { priv = it })
                    }
                    if (priv) OutlinedTextField(allowed, { allowed = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Allowed people, one @username or email per line") })
                    PrimaryButton("Add channel", {
                        if (user.isBlank()) { toast(c, "Enter a @username"); return@PrimaryButton }
                        kb?.hide()
                        adminDo(c, scope, if (priv) "✅ Private channel added" else "✅ Channel added", { user = ""; title = ""; priv = false; allowed = ""; tick++ }) {
                            Api.post("/api/telegram-channels", JSONObject().put("username", user.trim()).put("title", title.trim()).put("is_private", priv).put("allowed_users", allowed))
                        }
                    }, Modifier.fillMaxWidth())
                }
            }
            SectionTitle("On YID PLUS")
        }) { d ->
            val list = d.optJSONArray("channels").objects().filter { it.str("id") !in gone }
            if (list.isEmpty()) item { Text("No Telegram channels yet.", Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(list, key = { it.str("id").ifBlank { it.str("username") } }) { ch ->
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(Api.abs(ch.strOrNull("photo_url")), ch.str("title").ifBlank { ch.str("username") }, 46.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text((if (ch.optInt("is_private") == 1 || ch.optBoolean("is_private")) "🔒 " else "") + ch.str("title").ifBlank { ch.str("username") },
                                style = MaterialTheme.typography.titleMedium.merge(TextStyle(textDirection = TextDirection.Content)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("@" + ch.str("username") + " · " + ch.optInt("members") + " joined here", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        AdminPill("Remove", MaterialTheme.colorScheme.error) { del = ch }
                    }
                }
                HorizontalDivider(Modifier.padding(start = 74.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        del?.let { ch ->
            SettingsConfirmDialog("Remove @" + ch.str("username") + "?", "It disappears from the Channels tab.", "Remove", danger = true, onOk = {
                adminDo(c, scope, "Removed", { gone.add(ch.str("id")) }) { Api.delete("/api/telegram-channels?id=" + Uri.encode(ch.str("id"))) }
            }, onDismiss = { del = null })
        }
    }
}

// ── Broadcast (admin.js sendBroadcast: POST /api/broadcasts {text, segment, sender_email}; with push also POST /api/push/send
//    {title:'YID PLUS', body, url:'/chat'}; history GET /api/broadcasts?limit=10; cancel DELETE /api/broadcasts?scheduled_id=) ──

private val segments = listOf("all" to "Everyone", "new" to "New members (joined < 7 days)", "verified" to "Verified users only", "unverified" to "Unverified users only")

class AdminBroadcastPage : PageScreen() {
    override val perfName = "AdminBroadcast"
    override fun equals(other: Any?) = other is AdminBroadcastPage
    override fun hashCode() = 313
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        val res = remember { Res("/api/broadcasts?limit=10") { it } }
        var tick by remember { mutableIntStateOf(0) }
        var text by remember { mutableStateOf("") }
        var seg by remember { mutableIntStateOf(0) }
        var push by remember { mutableStateOf(false) }
        var ask by remember { mutableStateOf<String?>(null) }
        var whenDay by remember { mutableStateOf("") }
        var whenTime by remember { mutableStateOf("") }
        val whenMs = adLocalMs(whenDay, whenTime)
        val isSchedule = whenMs != null && whenMs > System.currentTimeMillis() + 60_000
        val kb = LocalSoftwareKeyboardController.current
        AdminList("Broadcast", sub = "A message to every member", res = res, tick = tick,
            actions = { IconBtn(Icons.Filled.Email, "Email all users", { Nav.open(AdEmailBlastPage()) }, tint = androidx.compose.ui.graphics.Color.White) }, header = {
            SettingsCard("New broadcast") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().firstFocus(), minLines = 3, label = { Text("Message") },
                        textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(textDirection = TextDirection.Content)))
                }
                SettingRow(Icons.Filled.Group, "Send to", null, value = segments[seg].second) { ask = "seg" }
                RowDivider(64.dp)
                SettingRow(Icons.Filled.NotificationsActive, "Also send a phone notification", null, switch = push) { push = !push }
                RowDivider(64.dp)
                SettingsHint("⏰ Schedule: leave empty to send now, or pick a day and time.")
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1.2f)) { AdField(whenDay, { whenDay = it }, "Day (YYYY-MM-DD)") }
                    Box(Modifier.weight(1f)) { AdField(whenTime, { whenTime = it }, "Time (HH:MM)") }
                }
                Box(Modifier.padding(16.dp)) {
                    PrimaryButton(if (isSchedule) "⏰ Schedule" else "📢 Send now", { if (text.isBlank()) toast(c, "⚠ Type a message first.") else { kb?.hide(); ask = "send" } }, Modifier.fillMaxWidth())
                }
            }
        }) { d ->
            val sched = d.optJSONArray("scheduled").objects()
            if (sched.isNotEmpty()) {
                item { SectionTitle("Scheduled") }
                items(sched, key = { "s" + it.str("id") }) { s ->
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.str("text"), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge.merge(TextStyle(textDirection = TextDirection.Content)))
                                Text((if (s.optBoolean("push") || s.optInt("push") == 1) "🔔 " else "") + "⏰ " + clock(s.strOrNull("scheduled_for")) + " " + (s.strOrNull("scheduled_for")?.take(10) ?: ""),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            AdminPill("Cancel", MaterialTheme.colorScheme.error) {
                                adminDo(c, scope, "Cancelled", { tick++ }) { Api.delete("/api/broadcasts?scheduled_id=" + Uri.encode(s.str("id"))) }
                            }
                        }
                    }
                }
            }
            item { SectionTitle("Sent") }
            val sent = d.optJSONArray("broadcasts").objects()
            if (sent.isEmpty()) item { Text("No broadcasts yet", Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(sent, key = { "b" + it.str("id") + it.str("created_at") }) { b ->
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(b.str("text"), style = MaterialTheme.typography.bodyLarge.merge(TextStyle(textDirection = TextDirection.Content)))
                        Text("Sent by " + b.str("sender_email").ifBlank { "admin" } + " · " + ago(b.strOrNull("created_at")), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        when (ask) {
            "seg" -> SettingsChoiceDialog("Send to", segments.map { it.second }, seg, { seg = it }, { ask = null })
            "send" -> SettingsConfirmDialog(if (isSchedule) "Schedule this for $whenDay $whenTime?" else "Send this to " + segments[seg].second.lowercase() + " now?", "\u201C" + text.trim() + "\u201D", if (isSchedule) "Schedule" else "Send", danger = true, onOk = {
                val t = text.trim()
                val email = Session.me?.email.orEmpty()
                val body = JSONObject().put("text", t).put("segment", segments[seg].first).put("sender_email", email)
                if (isSchedule) body.put("scheduled_for", adIso(whenMs!!)).put("push", push)
                scope.launch {
                    runCatching { Api.post("/api/broadcasts", body) }
                        .onSuccess { r ->
                            text = ""; whenDay = ""; whenTime = ""; tick++
                            if (r.optBoolean("scheduled") || body.has("scheduled_for")) { toast(c, "⏰ Scheduled!"); push = false; return@onSuccess }
                            toast(c, "📢 Broadcast sent to all users!")
                            if (push) runCatching { Api.post("/api/push/send", JSONObject().put("title", "YID PLUS").put("body", t).put("url", "/chat")) }
                                .onSuccess { r -> toast(c, "🔔 Pushed to " + r.optInt("sent") + " devices") }.onFailure { toast(c, "⚠ In-app sent, but push failed") }
                            push = false
                        }.onFailure { toast(c, "Failed: " + Api.friendly(it)) }
                }
            }, onDismiss = { ask = null })
        }
    }
}

// ── Word filter (admin.js buildBadWordsPanel: GET /api/admin/bad-words → words[], phrases[]; POST {word} / {phrase};
//    DELETE ?id= (and &type=phrase)) ──────────────────────────────────────────────────────────────────────────────

class AdminBadWordsPage : PageScreen() {
    override val perfName = "AdminBadWords"
    override fun equals(other: Any?) = other is AdminBadWordsPage
    override fun hashCode() = 314
    @OptIn(ExperimentalLayoutApi::class)
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        val res = remember { Res("/api/admin/bad-words") { it } }
        var tick by remember { mutableIntStateOf(0) }
        var word by remember { mutableStateOf("") }
        var phrases by remember { mutableStateOf(false) }
        val gone = remember { mutableStateListOf<String>() }
        val kb = LocalSoftwareKeyboardController.current
        AdminList("Word filter", sub = "Blurred for everyone, with a warning while typing", res = res, tick = tick, header = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminPill("Words", filled = !phrases, modifier = Modifier.weight(1f).firstFocus()) { phrases = false }
                AdminPill("Phrases", filled = phrases, modifier = Modifier.weight(1f)) { phrases = true }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(word, { word = it }, Modifier.weight(1f), singleLine = true, label = { Text(if (phrases) "Add a phrase (2+ words)" else "Add a word") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { kb?.hide() }))
                Spacer(Modifier.width(8.dp))
                AdminPill("Add", MaterialTheme.colorScheme.error, filled = true) {
                    val w = word.trim()
                    if (w.isEmpty()) return@AdminPill
                    kb?.hide()
                    adminDo(c, scope, if (phrases) "✅ Phrase added!" else "✅ \u201C$w\u201D added to filter", { word = ""; tick++ }) {
                        Api.post("/api/admin/bad-words", if (phrases) JSONObject().put("phrase", w) else JSONObject().put("word", w))
                    }
                }
            }
        }) { d ->
            val list = (if (phrases) d.optJSONArray("phrases") else d.optJSONArray("words")).objects().filter { it.str("id") !in gone }
            item {
                if (list.isEmpty()) Text(if (phrases) "No phrases blocked yet" else "No words blocked yet", Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else FlowRow(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { w ->
                        val t = if (phrases) w.str("phrase") else w.str("word")
                        Row(Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0x1ADC2626)).border(1.dp, Color(0x40DC2626), RoundedCornerShape(18.dp))
                            .tap(RoundedCornerShape(18.dp)) {
                                adminDo(c, scope, if (phrases) "Phrase removed" else "Removed from filter", { gone.add(w.str("id")) }) {
                                    Api.delete("/api/admin/bad-words?id=" + Uri.encode(w.str("id")) + if (phrases) "&type=phrase" else "")
                                }
                            }.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t, color = Color(0xFFDC2626), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            Icon(Icons.Filled.Close, "Remove", tint = Color(0xFFDC2626), modifier = Modifier.padding(start = 4.dp).size(16.dp))
                        }
                    }
                }
            }
            item { SettingsHint("Tap a word to take it off the list. Pasting a long list at once is on the website's admin page.") }
        }
    }
}
