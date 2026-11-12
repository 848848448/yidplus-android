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

/* CL: new group / channel (moved out of ChatPages.kt). */

/** New group or channel: name, description, public/private, members. */
data class ChNewGroup(val type: String) : PageScreen() {
    override val perfName get() = "NewGroup"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var public by remember { mutableStateOf(type == "channel") }
        var writeAll by remember { mutableStateOf(true) }
        var photo by remember { mutableStateOf<Uri?>(null) }
        val picked = remember { mutableStateListOf<ChMember>() }
        var busy by remember { mutableStateOf(false) }
        val what = if (type == "channel") "channel" else "group"
        val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u: Uri? -> if (u != null) photo = u }
        fun create() {
            if (busy) return
            if (name.trim().length < 2) { toast(c, "⚠ Name must be at least 2 characters."); return }
            busy = true
            scope.launch {
                try {
                    val id = ClApi.create(type, name.trim(), desc.trim(), public, writeAll)
                    if (id.isBlank()) error("The site did not return the new $what")
                    // The site shows a photo preview but never uploads it; the app sends it right after the group exists (Choice C3).
                    photo?.let { u -> runCatching { ClApi.setPhoto(c, id, u) } }
                    if (type != "channel" && desc.isNotBlank()) runCatching { ClApi.saveText(id, name, desc.trim()) }
                    picked.forEach { runCatching { ChatApi.addMember(id, it.id) } }
                    toast(c, "✅ ${what.replaceFirstChar { it.uppercase() }} \"${name.trim()}\" created!")
                    ChatApi.rooms.load(true)
                    Nav.back(); Nav.open(Screen.Chat(ChatApi.info(id)?.room ?: ChatApi.roomOf(id, type, name.trim())))
                } catch (e: Exception) { toast(c, Api.friendly(e)) } finally { busy = false }
            }
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChBar(if (type == "channel") "New channel" else "New group", onBack = { Nav.back() }) { ChIcon(Sym.Check, "Create", { create() }) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            // One scrolling page, so at 480x640 with the keyboard up nothing is squeezed: photo + name on one row, the rest below.
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).tap(CircleShape) { pick.launch("image/*") }
                            .semantics { contentDescription = "Add $what photo" }, contentAlignment = Alignment.Center) {
                            if (photo != null) AsyncImage(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Icon(Sym.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedTextField(name, { name = it.take(60) }, Modifier.weight(1f).padding(start = 12.dp), singleLine = true, label = { Text(if (type == "channel") "Channel name" else "Group name") },
                            textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content))
                    }
                    OutlinedTextField(desc, { desc = it.take(255) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), maxLines = 3, label = { Text("Description (optional)") },
                        textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content))
                    ClSwitch(Sym.Public, if (public) "Public" else "Private", if (public) "Anyone can find and join it" else "Only people with the invite link", public) { public = it }
                    if (type != "channel") ClSwitch(Sym.Edit, "Everyone can write", if (writeAll) "All members can send messages" else "Only admins can send", writeAll) { writeAll = it }
                    PrimaryButton(if (type == "channel") "Create channel" else "Create group" + if (picked.isNotEmpty()) " with ${picked.size}" else "", { create() }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), enabled = !busy)
                    if (picked.isNotEmpty()) Text(picked.joinToString(", ") { it.nick }, Modifier.padding(horizontal = 16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (type == "channel") "Add subscribers (optional)" else "Add members", Modifier.padding(start = 16.dp, top = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
                item { Box(Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 600.dp)) { UserSearch("Search people", selected = picked.map { it.id }.toSet(), onUser = { mb -> if (picked.any { it.id == mb.id }) picked.removeAll { it.id == mb.id } else picked.add(mb) }) } }
            }
        }
    }
}


