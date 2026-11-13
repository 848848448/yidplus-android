package com.yidplus.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yidplus.app.data.Api
import com.yidplus.app.data.Res
import com.yidplus.app.data.Session
import com.yidplus.app.data.Store
import com.yidplus.app.data.objects
import com.yidplus.app.data.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

// ── Lists: blocked, follow requests, muted chats ──────────────────────────────

@Composable
private fun PersonRow(photo: String?, name: String, sub: String? = null, first: Boolean = false, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).then(if (first) Modifier.firstFocus() else Modifier).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(photo, name, 44.dp)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        actions()
    }
}

@Composable
private fun SmallAction(text: String, primary: Boolean = false, onClick: () -> Unit) {
    Text(text, Modifier.padding(start = 6.dp).clip(RoundedCornerShape(18.dp))
        .then(if (primary) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)))
        .tap(RoundedCornerShape(18.dp), onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.padding(start = 8.dp).size(40.dp).clip(CircleShape)
        .then(if (primary) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        .tap(CircleShape, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
}

/** A list page that loads `path` and shows the rows, with the skeleton, error and empty states done right. */
@Composable
private fun <T> ListPage(title: String, path: String, parse: (JSONObject) -> List<T>, demo: List<T>, emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
                         emptyTitle: String, emptySub: String, key: (T) -> String, row: @Composable (T, Boolean, () -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<T>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    suspend fun load() {
        if (SettingsProfile.demo) { list = demo; return }
        runCatching { withContext(Dispatchers.IO) { parse(Api.get(path)) } }.onSuccess { list = it.distinctBy(key); err = null }.onFailure { err = Api.friendly(it); offline = Api.isOffline(it) }
    }
    LaunchedEffect(path) { load() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(title, onBack = { Nav.back() }, sub = list?.let { if (it.isEmpty()) null else if (it.size == 1) "1 person" else "${it.size} people" })
        val l = list
        when {
            l == null && err != null -> Problem(err!!, offline, { scope.launch { load() } })
            l == null -> Loading()
            l.isEmpty() -> EmptyNote(emptyIcon, emptyTitle, emptySub)
            else -> LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                items(l, key = key) { item ->
                    row(item, item == l.first()) { list = list?.filter { key(it) != key(item) } }
                    RowDivider(74.dp)
                }
            }
        }
    }
}

class SettingsBlocked : PageScreen() {
    override val perfName = "Blocked"
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        ListPage("Blocked users", "/api/blocks", { it.optJSONArray("blocked").objects() },
            listOf(JSONObject().put("blocked_id", "d1").put("nickname", "Spam Account")), Sym.Block,
            "You haven't blocked anyone", "You can block someone from their profile or a chat.", { it.str("blocked_id") }) { u, first, gone ->
            PersonRow(null, u.str("nickname").ifBlank { "User" }, first = first) {
                SmallAction("Unblock") {
                    scope.launch {
                        runCatching { if (!SettingsProfile.demo) Api.delete("/api/blocks?id=" + Uri.encode(u.str("blocked_id"))) }
                            .onSuccess { toast(c, "Unblocked"); gone() }.onFailure { toast(c, Api.friendly(it)) }
                    }
                }
            }
        }
    }
}

class FollowRequestsPage : PageScreen() {
    override val perfName = "FollowRequests"
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        ListPage("Follow requests", "/api/follows?requests=1", { it.optJSONArray("requests").objects() },
            listOf(JSONObject().put("id", "d2").put("nickname", "Moshe Klein").put("created_at", "2026-11-10T20:00:00")), Sym.PersonAdd,
            "No requests", "When your account is private, people who want to follow you show up here.", { it.str("id") }) { r, first, gone ->
            PersonRow(Api.abs(r.str("photo_url").ifBlank { null }), r.str("nickname").ifBlank { "User" }, ago(r.str("created_at")).let { if (it.isBlank()) null else "$it ago" }, first) {
                RoundAction(Sym.Check, "Accept", primary = true) {
                    scope.launch {
                        runCatching { if (!SettingsProfile.demo) Api.post("/api/follows", JSONObject().put("action", "accept").put("requester_id", r.str("id"))) }
                            .onSuccess { toast(c, "Accepted"); gone() }.onFailure { toast(c, Api.friendly(it)) }
                    }
                }
                RoundAction(Sym.Close, "Decline") {
                    scope.launch {
                        runCatching { if (!SettingsProfile.demo) Api.delete("/api/follows?user_id=" + Uri.encode(r.str("id")) + "&request=1") }
                            .onSuccess { gone() }.onFailure { toast(c, Api.friendly(it)) }
                    }
                }
            }
        }
    }
}

class SettingsMutedChats : PageScreen() {
    override val perfName = "MutedChats"
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        ListPage("Muted chats", "/api/chat/rooms", { j -> j.optJSONArray("rooms").objects().filter { it.optBoolean("muted") || it.optInt("muted") == 1 } },
            listOf(JSONObject().put("id", "d3").put("name", "Yeshiva news").put("muted", true)), Sym.NotificationsOff,
            "No muted chats", "Mute a chat from its menu and it shows up here.", { it.str("id") }) { r, first, gone ->
            val name = r.str("nick").ifBlank { r.str("name") }.ifBlank { "Chat" }
            PersonRow(Api.abs(r.str("photo_url").ifBlank { null }), name, first = first) {
                SmallAction("Unmute") {
                    scope.launch {
                        runCatching { if (!SettingsProfile.demo) Api.put("/api/chat/rooms", JSONObject().put("room_id", r.str("id")).put("muted", false)) }
                            .onSuccess { toast(c, "Unmuted"); gone() }.onFailure { toast(c, Api.friendly(it)) }
                    }
                }
            }
        }
    }
}

// ── Feedback, data export, delete ─────────────────────────────────────────────

class SettingsFeedback : PageScreen() {
    override val perfName = "Feedback"
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        var type by remember { mutableStateOf("bug") }
        var text by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        SettingsPage("Send feedback") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("What is it about?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((k, label) in listOf("bug" to "🐞 Problem", "suggestion" to "💡 Idea", "other" to "💬 Other")) {
                        val sel = type == k
                        Text(label, Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                            .tap(RoundedCornerShape(20.dp), role = Role.RadioButton) { type = k }.then(if (k == "bug") Modifier.firstFocus() else Modifier)
                            .padding(vertical = 10.dp), textAlign = TextAlign.Center, maxLines = 1,
                            color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
                    }
                }
                OutlinedTextField(text, { text = it.take(2000) }, Modifier.fillMaxWidth(), label = { Text("Tell us more") }, minLines = 5, maxLines = 10)
                PrimaryButton(if (busy) "Sending…" else "Send", {
                    if (text.isBlank()) { toast(c, "Please write something first"); return@PrimaryButton }
                    busy = true
                    scope.launch {
                        runCatching { if (!SettingsProfile.demo) Api.post("/api/feedback", JSONObject().put("type", type).put("text", text.trim()).put("device", Api.UA.take(100))) }
                            .onSuccess { toast(c, "Thank you! We got it."); Nav.back() }.onFailure { toast(c, Api.friendly(it)) }
                        busy = false
                    }
                }, Modifier.fillMaxWidth(), enabled = !busy)
            }
        }
    }
}

/** The site's "Download my data": the whole /api/profile answer saved as a JSON file in Downloads. */
suspend fun settingsExportData(c: Context): String = withContext(Dispatchers.IO) {
    try {
        val text = (if (SettingsProfile.demo) SettingsProfile.sample() else Api.get("/api/profile")).toString(2)
        val name = "yidplus-my-data-${System.currentTimeMillis() / 1000}.json"
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "application/json"); put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS) }
            val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return@withContext "Couldn't save the file"
            c.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            "Saved to Downloads: $name"
        } else {
            val dir = c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: c.filesDir
            java.io.File(dir, name).writeText(text)
            "Saved: ${dir.absolutePath}/$name"
        }
    } catch (e: Exception) { Api.friendly(e) }
}

/** Read a QA-only extra on the launch intent (emulator tests drive the screens with adb). */
fun settingsQaExtra(c: Context, key: String): String? = (c as? Activity)?.intent?.getStringExtra(key)
fun settingsQaClear(c: Context, key: String) { (c as? Activity)?.intent?.removeExtra(key) }
