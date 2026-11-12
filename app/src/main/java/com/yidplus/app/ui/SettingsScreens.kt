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

// ═════════════════════════════════════════════════════════════════════════════
// The signed-in person's own profile and account (the website's Settings page, /api/profile and friends).
// ═════════════════════════════════════════════════════════════════════════════

object SettingsProfile {
    /** Emulator QA only: the screens show a sample profile (QA runs signed out). Never set by people. */
    var demo by mutableStateOf(false)
    val res: Res<JSONObject> get() = Store.res("/api/profile") { it }
    val p: JSONObject? get() = res.data?.optJSONObject("profile")
    fun on(key: String, default: Boolean = true): Boolean = p?.let { if (it.isNull(key) || !it.has(key)) default else it.optInt(key, if (default) 1 else 0) != 0 } ?: default
    fun text(key: String): String = p?.str(key) ?: ""

    suspend fun load(force: Boolean = false) {
        if (demo) { if (res.data == null) res.data = sample(); return }
        if (Session.me == null) return
        res.load(force)
    }

    private fun merged(fields: JSONObject): JSONObject {
        val all = JSONObject((res.data ?: JSONObject().put("profile", JSONObject())).toString())
        val p = all.optJSONObject("profile") ?: JSONObject().also { all.put("profile", it) }
        for (k in fields.keys()) if (k != "password") p.put(k, fields.get(k))
        return all
    }

    /** PUT /api/profile with some fields. Returns null when it saved, else the site's words. */
    suspend fun save(fields: JSONObject): String? {
        if (demo) { res.data = merged(fields); return null }
        return try {
            Api.put("/api/profile", fields)
            res.data = merged(fields)
            if (fields.has("nickname") || fields.has("photo_url")) Session.refresh()
            null
        } catch (e: Exception) { Api.friendly(e) }
    }

    /** The website's "Edit photo": a multipart PUT /api/profile with `photo` (or `banner`). The picture is made small first. */
    suspend fun upload(ctx: Context, uri: Uri, field: String): String? = withContext(Dispatchers.IO) {
        if (demo) return@withContext null
        try {
            val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext "Couldn't read that picture"
            val (bytes, type) = shrink(raw, if (field == "banner") 1600 else 900) ?: (raw to (ctx.contentResolver.getType(uri) ?: "image/jpeg"))
            val ext = if (type.contains("png")) "png" else if (type.contains("webp")) "webp" else "jpg"
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart(field, "$field.$ext", bytes.toRequestBody(type.toMediaType())).build()
            val req = Request.Builder().url(Api.BASE + "/api/profile").put(body).header("Accept", "application/json").build()
            if (com.yidplus.app.data.Fixture.answer(req) != null) { withContext(Dispatchers.Main) { res.load(force = true) }; return@withContext null }
            Api.http.newCall(req).execute().use { r ->
                val t = r.body?.string() ?: ""
                if (!r.isSuccessful) return@withContext runCatching { JSONObject(t).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() } ?: "The site answered ${r.code}"
            }
            withContext(Dispatchers.Main) { res.load(force = true); Session.refresh() }
            null
        } catch (e: Exception) { Api.friendly(e) }
    }

    private fun shrink(raw: ByteArray, max: Int): Pair<ByteArray, String>? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, o)
        if (o.outWidth <= 0) return null
        var sample = 1
        while (o.outWidth / (sample * 2) >= max || o.outHeight / (sample * 2) >= max) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = minOf(1f, max.toFloat() / maxOf(bmp.width, bmp.height))
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 86, bos)
        bos.toByteArray() to "image/jpeg"
    }.getOrNull()

    fun sample(): JSONObject = JSONObject().put("ok", true).put("profile", JSONObject()
        .put("id", "demo").put("nickname", "Yanky Weiss").put("email", "yanky@example.com").put("bio", "Sample profile shown in the emulator test")
        .put("location", "Brooklyn, NY").put("website", "https://yidplus.com").put("instagram", "").put("youtube", "").put("birthday", "")
        .put("profile_color", "#1F6F5C").put("verified", 0).put("role", "user").put("privacy_profile", "public").put("privacy_messages", "everyone")
        .put("is_private", 0).put("show_online", 1).put("read_receipts", 1).put("notif_messages", 1).put("notif_likes", 1).put("notif_followers", 1).put("notif_channels", 0))
        .put("stats", JSONObject().put("shorts", 12).put("music", 3).put("messages", 1480).put("profile_views", 236).put("followers", 58))
        .put("sessions", JSONArray().put(JSONObject().put("id", "sh_1").put("created_at", "2026-11-11T06:00:00")).put(JSONObject().put("id", "sh_2").put("created_at", "2026-11-02T18:30:00")))
}

val settingsIsAdmin: Boolean get() = Session.me?.let { it.isOwner || it.role == "admin_super" || it.role == "admin_limited" } == true || SettingsProfile.demo
val settingsIsSuperAdmin: Boolean get() = Session.me?.let { it.isOwner || it.role == "admin_super" } == true || SettingsProfile.demo

// ── Small shared pieces for these screens ─────────────────────────────────────

@Composable
fun SettingsPage(title: String, sub: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(title, onBack = { Nav.back() }, sub = sub)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(bottom = 24.dp)) { content() }
            }
        }
    }
}

/** A white card that groups rows, like Telegram's settings sections. */
@Composable
fun SettingsCard(title: String? = null, content: @Composable () -> Unit) {
    if (title != null) SectionTitle(title) else Spacer(Modifier.height(12.dp))
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) { Column { content() } }
}

@Composable
fun SettingsHint(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SettingsChoiceDialog(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                options.forEachIndexed { i, o ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).tap(RoundedCornerShape(8.dp), role = Role.RadioButton) { onPick(i); onDismiss() }
                        .then(if (i == selected) Modifier.firstFocus() else Modifier).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(i == selected, null)
                        Text(o, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        })
}

@Composable
fun SettingsConfirmDialog(title: String, text: String, action: String, danger: Boolean = false, onOk: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton({ onDismiss(); onOk() }, Modifier.firstFocus()) { Text(action, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

/**
 * The website's _confirmWithPassword: before a password change or deleting the account the site wants the current
 * password confirmed (POST /api/auth/verify-password). Accounts made with Google have no password: a plain "Are you sure".
 */
@Composable
fun SettingsPasswordDialog(title: String, message: String, action: String, danger: Boolean = false, onOk: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var noPassword by remember { mutableStateOf<Boolean?>(null) }
    var pw by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        noPassword = if (SettingsProfile.demo) false else runCatching { Api.post("/api/auth/verify-password", JSONObject().put("password", "")).optBoolean("no_password") }.getOrDefault(false)
    }
    fun go() {
        if (noPassword == true) { onDismiss(); onOk(); return }
        if (pw.isBlank()) { err = "Enter your current password"; return }
        busy = true; err = null
        scope.launch {
            val ok = if (SettingsProfile.demo) true else runCatching { Api.post("/api/auth/verify-password", JSONObject().put("password", pw)).optBoolean("valid") }
                .getOrElse { err = Api.friendly(it); busy = false; return@launch }
            busy = false
            if (ok) { onDismiss(); onOk() } else err = "That password isn't right"
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (noPassword == true) "You signed up with Google, so there is no password to enter. Continue?" else message)
                if (noPassword == false) OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth().firstFocus(always = true), label = { Text("Current password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton({ go() }, enabled = !busy && noPassword != null) { Text(if (busy) "Checking…" else action, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

/** The person's own header: photo (tap to change), name, email, and the site's stats under it. */
@Composable
fun SettingsProfileHeader(onEdit: () -> Unit) {
    val me = Session.me
    val p = SettingsProfile.p
    val name = p?.str("nickname")?.ifBlank { null } ?: me?.nickname ?: ""
    val photo = Api.abs(p?.str("photo_url")?.ifBlank { null }) ?: me?.photo
    val stats = SettingsProfile.res.data?.optJSONObject("stats")
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().tap(RoundedCornerShape(0.dp), onClick = onEdit).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Avatar(photo, name, 68.dp)
                    Box(Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center) { Icon(Sym.CameraAlt, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
                }
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if ((p?.optInt("verified") ?: 0) == 1) Icon(Sym.Check, "Verified", tint = Color.White,
                            modifier = Modifier.padding(start = 6.dp).size(16.dp).clip(CircleShape).background(BadgeGreen).padding(2.dp))
                    }
                    val bio = p?.str("bio").orEmpty()
                    Text(bio.ifBlank { me?.email ?: "" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val role = when { me?.isOwner == true -> "Owner"; me?.role == "admin_super" -> "Super admin"; me?.role == "admin_limited" -> "Admin"; else -> null }
                    if (role != null) Text(role, Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            if (stats != null) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat(stats.optInt("followers"), "Followers"); Stat(stats.optInt("shorts"), "Videos"); Stat(stats.optInt("profile_views"), "Views"); Stat(stats.optInt("messages"), "Messages")
                }
            }
        }
    }
}

@Composable
private fun Stat(n: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (n >= 10000) "${n / 1000}k" else if (n >= 1000) "%.1fk".format(n / 1000f) else n.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

// ── Edit profile ──────────────────────────────────────────────────────────────

private val profileColors = listOf("#1F6F5C", "#2B7A9B", "#4767A8", "#7056A8", "#AD4F7E", "#C0564A", "#C7822A", "#6D7F2B", "#37474F", "#000000")

class SettingsEditProfile : PageScreen() {
    override val perfName = "EditProfile"
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) { SettingsProfile.load() }
        val p = SettingsProfile.p
        var nick by remember(p) { mutableStateOf(SettingsProfile.text("nickname").ifBlank { Session.me?.nickname ?: "" }) }
        var bio by remember(p) { mutableStateOf(SettingsProfile.text("bio")) }
        var location by remember(p) { mutableStateOf(SettingsProfile.text("location")) }
        var birthday by remember(p) { mutableStateOf(SettingsProfile.text("birthday")) }
        var website by remember(p) { mutableStateOf(SettingsProfile.text("website")) }
        var instagram by remember(p) { mutableStateOf(SettingsProfile.text("instagram")) }
        var youtube by remember(p) { mutableStateOf(SettingsProfile.text("youtube")) }
        var color by remember(p) { mutableStateOf(SettingsProfile.text("profile_color").ifBlank { "#1F6F5C" }) }
        var busy by remember { mutableStateOf(false) }
        var err by remember { mutableStateOf<String?>(null) }
        var uploading by remember { mutableStateOf<String?>(null) }
        val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) { uploading = "photo"; scope.launch { val e = SettingsProfile.upload(c, uri, "photo"); uploading = null; toast(c, e ?: "Photo updated") } }
        }
        val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) { uploading = "banner"; scope.launch { val e = SettingsProfile.upload(c, uri, "banner"); uploading = null; toast(c, e ?: "Cover updated") } }
        }
        fun save() {
            val f = JSONObject()
            fun put(k: String, v: String) { if (v.trim() != SettingsProfile.text(k)) f.put(k, v.trim()) }
            if (nick.trim() != (SettingsProfile.text("nickname").ifBlank { Session.me?.nickname ?: "" })) {
                if (nick.trim().length !in 3..40) { err = "Your name must be 3 to 40 characters"; return }
                f.put("nickname", nick.trim())
            }
            put("bio", bio); put("location", location); put("birthday", birthday); put("website", website); put("instagram", instagram); put("youtube", youtube)
            if (color != SettingsProfile.text("profile_color")) f.put("profile_color", color)
            if (f.length() == 0) { Nav.back(); return }
            busy = true; err = null
            scope.launch { val e = SettingsProfile.save(f); busy = false; if (e == null) { toast(c, "Saved"); Nav.back() } else err = e }
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopBar("Edit profile", onBack = { Nav.back() }) {
                Text(if (busy) "Saving…" else "SAVE", Modifier.tap(RoundedCornerShape(8.dp)) { if (!busy) save() }.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                        // Cover + photo, like the site's profile page.
                        Box(Modifier.fillMaxWidth().height(150.dp)) {
                            val banner = Api.abs(p?.str("banner_url")?.ifBlank { null })
                            Box(Modifier.fillMaxWidth().height(110.dp).background(runCatching { Color(android.graphics.Color.parseColor(color)) }.getOrDefault(Green))
                                .tap(RoundedCornerShape(0.dp)) { pickBanner.launch("image/*") }) {
                                if (banner != null) AsyncImage(banner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                Row(Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = .35f)).padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Sym.CameraAlt, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Text(if (uploading == "banner") " Uploading…" else " Cover", color = Color.White, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Box(Modifier.align(Alignment.BottomStart).padding(start = 16.dp).size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(3.dp)
                                .firstFocus().tap(CircleShape) { pickPhoto.launch("image/*") }) {
                                Avatar(Api.abs(p?.str("photo_url")?.ifBlank { null }) ?: Session.me?.photo, nick, 78.dp)
                                Box(Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                    Icon(Sym.CameraAlt, "Change photo", tint = Color.White, modifier = Modifier.size(15.dp))
                                }
                            }
                            if (uploading == "photo") Text("Uploading photo…", Modifier.align(Alignment.BottomStart).padding(start = 112.dp, bottom = 8.dp), style = MaterialTheme.typography.bodySmall)
                        }
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Field("Name", nick, { nick = it })
                            Field("Bio", bio, { bio = it.take(1000) }, lines = 3, help = "${bio.length}/1000")
                            Field("Location", location, { location = it.take(120) })
                            Field("Birthday (YYYY-MM-DD)", birthday, { birthday = it.filter { ch -> ch.isDigit() || ch == '-' }.take(10) }, type = KeyboardType.Number)
                            Field("Website", website, { website = it.take(500) }, type = KeyboardType.Uri)
                            Field("Instagram", instagram, { instagram = it.take(200) })
                            Field("YouTube", youtube, { youtube = it.take(500) }, type = KeyboardType.Uri)
                            Text("Profile colour", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (hex in profileColors.take(5)) Swatch(hex, color.equals(hex, true)) { color = hex }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (hex in profileColors.drop(5)) Swatch(hex, color.equals(hex, true)) { color = hex }
                            }
                            if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            PrimaryButton(if (busy) "Saving…" else "Save", { save() }, Modifier.fillMaxWidth().padding(top = 6.dp), enabled = !busy)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Swatch(hex: String, sel: Boolean, onClick: () -> Unit) {
    val col = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Green)
    Box(Modifier.size(38.dp).clip(CircleShape).background(col).tap(CircleShape, role = Role.RadioButton, onClick = onClick)
        .then(if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
        if (sel) Icon(Sym.Check, hex, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, lines: Int = 1, type: KeyboardType = KeyboardType.Text, help: String? = null, first: Boolean = false) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth().then(if (first) Modifier.firstFocus() else Modifier), label = { Text(label) },
        singleLine = lines == 1, minLines = lines, maxLines = if (lines == 1) 1 else 6,
        keyboardOptions = KeyboardOptions(keyboardType = type), supportingText = if (help != null) ({ Text(help) }) else null)
}

// ── Privacy ───────────────────────────────────────────────────────────────────

private val seeOpts = listOf("public" to "Everyone", "friends" to "Friends only", "private" to "Only me")
private val msgOpts = listOf("everyone" to "Everyone", "friends" to "Friends only", "nobody" to "Nobody")

class SettingsPrivacy : PageScreen() {
    override val perfName = "Privacy"
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) { SettingsProfile.load(force = true) }
        var pick by remember { mutableStateOf<String?>(null) }
        fun set(k: String, v: Any) = scope.launch { SettingsProfile.save(JSONObject().put(k, v))?.let { toast(c, it) } }
        SettingsPage("Privacy") {
            SettingsCard {
                SettingRow(Sym.Block, "Blocked users", "People you blocked can't message you or see your profile", modifier = Modifier.firstFocus()) { Nav.open(SettingsBlocked()) }
                RowDivider(64.dp)
                SettingRow(Sym.Pin, "App lock", if (StLock.enabled) "On · asks your PIN when the app opens" else "Off") { Nav.open(StAppLockPage()) }
            }
            SettingsCard("Who can see me") {
                SettingRow(Sym.Lock, "Private account", "People must ask to follow you before they see your posts and statuses", switch = SettingsProfile.on("is_private", false), modifier = Modifier.firstFocus()) {
                    set("is_private", if (SettingsProfile.on("is_private", false)) 0 else 1)
                }
                RowDivider(64.dp)
                SettingRow(Sym.PersonAdd, "Follow requests", "People who asked to follow you") { Nav.open(FollowRequestsPage()) }
                RowDivider(64.dp)
                val see = SettingsProfile.text("privacy_profile").ifBlank { "public" }
                SettingRow(Sym.Visibility, "Who can see my profile", value = seeOpts.firstOrNull { it.first == see }?.second ?: "Everyone") { pick = "privacy_profile" }
                RowDivider(64.dp)
                val msg = SettingsProfile.text("privacy_messages").ifBlank { "everyone" }
                SettingRow(Sym.Chat, "Who can message me", value = msgOpts.firstOrNull { it.first == msg }?.second ?: "Everyone") { pick = "privacy_messages" }
            }
            SettingsCard("Activity") {
                SettingRow(Sym.RadioButtonChecked, "Active status", "Let others see when you're online", switch = SettingsProfile.on("show_online")) {
                    set("show_online", if (SettingsProfile.on("show_online")) 0 else 1)
                }
                RowDivider(64.dp)
                SettingRow(Sym.DoneAll, "Read receipts", "Show others when you've read their message", switch = SettingsProfile.on("read_receipts")) {
                    set("read_receipts", if (SettingsProfile.on("read_receipts")) 0 else 1)
                }
            }
            SettingsCard("Blocking") {
                SettingRow(Sym.Block, "Blocked users", "People you blocked can't message you") { Nav.open(SettingsBlocked()) }
                RowDivider(64.dp)
                SettingRow(Sym.NotificationsOff, "Muted chats", "Chats that don't notify you") { Nav.open(SettingsMutedChats()) }
            }
        }
        when (pick) {
            "privacy_profile" -> SettingsChoiceDialog("Who can see my profile", seeOpts.map { it.second }, seeOpts.indexOfFirst { it.first == SettingsProfile.text("privacy_profile").ifBlank { "public" } },
                { set("privacy_profile", seeOpts[it].first) }, { pick = null })
            "privacy_messages" -> SettingsChoiceDialog("Who can message me", msgOpts.map { it.second }, msgOpts.indexOfFirst { it.first == SettingsProfile.text("privacy_messages").ifBlank { "everyone" } },
                { set("privacy_messages", msgOpts[it].first) }, { pick = null })
        }
    }
}

// ── The site's notification choices (what yidplus.com notifies you about) ─────

class SettingsNotifPrefs : PageScreen() {
    override val perfName = "NotifPrefs"
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) { SettingsProfile.load() }
        fun flip(k: String) = scope.launch { SettingsProfile.save(JSONObject().put(k, if (SettingsProfile.on(k)) 0 else 1))?.let { toast(c, it) } }
        SettingsPage("Notifications") {
            SettingsCard("Notify me about") {
                SettingRow(Sym.Mail, "New messages", switch = SettingsProfile.on("notif_messages"), modifier = Modifier.firstFocus()) { flip("notif_messages") }
                RowDivider(64.dp)
                SettingRow(Sym.Favorite, "New likes", switch = SettingsProfile.on("notif_likes")) { flip("notif_likes") }
                RowDivider(64.dp)
                SettingRow(Sym.PersonAdd, "New followers", switch = SettingsProfile.on("notif_followers")) { flip("notif_followers") }
                RowDivider(64.dp)
                SettingRow(Sym.Campaign, "Channel updates", switch = SettingsProfile.on("notif_channels")) { flip("notif_channels") }
            }
            SettingsHint("These are saved on your yidplus.com account, so the website follows them too. The phone's own on/off switch is on the Settings page.")
        }
    }
}

// ── Security: password, two-step code, devices ────────────────────────────────

class SettingsSecurity : PageScreen() {
    override val perfName = "Security"
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) { SettingsProfile.load(force = true) }
        var twofa by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(Unit) { twofa = if (SettingsProfile.demo) false else runCatching { Api.get("/api/2fa").optBoolean("enabled") }.getOrNull() }
        var askPw by remember { mutableStateOf(false) }
        var newPw by remember { mutableStateOf(false) }
        var show2fa by remember { mutableStateOf(false) }
        var logoutAll by remember { mutableStateOf(false) }
        val sessions = SettingsProfile.res.data?.optJSONArray("sessions").objects()
        SettingsPage("Security") {
            SettingsCard("Sign-in") {
                SettingRow(Sym.Password, "Change password", "You'll confirm your current password first", modifier = Modifier.firstFocus()) { askPw = true }
                RowDivider(64.dp)
                SettingRow(Sym.Security, "Two-step verification", when (twofa) { true -> "On · a code from your authenticator app"; false -> "Off"; null -> "…" }) { if (twofa != null) show2fa = true }
            }
            SettingsCard("Devices (${sessions.size} signed in)") {
                sessions.take(10).forEachIndexed { i, s ->
                    if (i > 0) RowDivider(64.dp)
                    val id = s.str("id")
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Sym.Devices, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Column(Modifier.padding(start = 24.dp).weight(1f)) {
                            Text(if (i == 0) "This device" else "Device ${i + 1}", style = MaterialTheme.typography.bodyLarge)
                            Text("Signed in " + ago(s.str("created_at")).let { if (it == "now") "just now" else "$it ago" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (i == 0) Text("Active", color = BadgeGreen, style = MaterialTheme.typography.labelLarge)
                        else Text("Sign out", Modifier.tap(RoundedCornerShape(8.dp)) {
                            scope.launch {
                                runCatching { if (!SettingsProfile.demo) Api.delete("/api/auth/sessions?id=" + Uri.encode(id)) }
                                    .onSuccess { toast(c, "Signed out that device"); SettingsProfile.load(force = true) }.onFailure { toast(c, Api.friendly(it)) }
                            }
                        }.padding(horizontal = 10.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (sessions.size > 1) {
                    RowDivider(0.dp)
                    SettingRow(Sym.Devices, "Sign out all devices", "Including this phone", danger = true) { logoutAll = true }
                }
            }
            // 2.1 ST: the site's GET /api/profile already returns login_logs (ip, action, created_at); shown here like Telegram's "Active sessions" history.
            val logs = SettingsProfile.res.data?.optJSONArray("login_logs").objects()
            if (logs.isNotEmpty()) SettingsCard("Recent sign-ins") {
                logs.take(10).forEachIndexed { i, l ->
                    if (i > 0) RowDivider(64.dp)
                    val fail = l.str("action").contains("fail", true)
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (fail) Sym.Security else Sym.Devices, null, tint = if (fail) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        Column(Modifier.padding(start = 24.dp).weight(1f)) {
                            Text(if (fail) "Failed sign-in attempt" else "Signed in", style = MaterialTheme.typography.bodyLarge, color = if (fail) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            Text(listOf(l.str("ip"), ago(l.str("created_at")).let { if (it.isBlank()) "" else if (it == "now") "just now" else "$it ago" }).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (askPw) SettingsPasswordDialog("Change password", "Enter your current password to continue.", "Continue", onOk = { newPw = true }, onDismiss = { askPw = false })
        if (newPw) NewPasswordDialog { newPw = false }
        if (show2fa) TwoFactorDialog(twofa == true, onDone = { twofa = it }) { show2fa = false }
        if (logoutAll) SettingsConfirmDialog("Sign out everywhere?", "Every phone and computer signed in to your account will be signed out, this one too.", "Sign out all", danger = true,
            onOk = {
                scope.launch {
                    runCatching { if (!SettingsProfile.demo) Api.delete("/api/auth/sessions?all=1") }.onFailure { toast(c, Api.friendly(it)) }
                    Session.logout(); Nav.go(Tab.Profile); toast(c, "Signed out everywhere")
                }
            }, onDismiss = { logoutAll = false })
    }
}

@Composable
private fun NewPasswordDialog(onDismiss: () -> Unit) {
    val c = ctx()
    val scope = rememberCoroutineScope()
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(a, { a = it }, Modifier.fillMaxWidth().firstFocus(always = true), label = { Text("New password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                OutlinedTextField(b, { b = it }, Modifier.fillMaxWidth(), label = { Text("Type it again") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Text("At least 6 characters. Your other devices will be signed out.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton({
                when {
                    a.length < 6 -> err = "Use at least 6 characters"
                    a != b -> err = "The two passwords are not the same"
                    else -> { busy = true; scope.launch { val e = SettingsProfile.save(JSONObject().put("password", a)); busy = false; if (e == null) { toast(c, "Password changed"); onDismiss() } else err = e } }
                }
            }, enabled = !busy) { Text(if (busy) "Saving…" else "Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

/** The site's 2FA: setup gives a secret for an authenticator app, a 6-digit code turns it on; a code turns it off. */
@Composable
private fun TwoFactorDialog(enabled: Boolean, onDone: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val c = ctx()
    val scope = rememberCoroutineScope()
    var secret by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (!enabled) runCatching { if (SettingsProfile.demo) "JBSWY3DPEHPK3PXP" else Api.post("/api/2fa", JSONObject().put("action", "setup")).optString("secret") }
            .onSuccess { secret = it }.onFailure { err = Api.friendly(it) }
    }
    fun go() {
        if (code.length != 6) { err = "Enter the 6-digit code"; return }
        busy = true; err = null
        scope.launch {
            runCatching {
                val body = if (enabled) JSONObject().put("action", "disable").put("code", code) else JSONObject().put("action", "enable").put("secret", secret).put("code", code)
                if (!SettingsProfile.demo) Api.post("/api/2fa", body)
            }.onSuccess { toast(c, if (enabled) "Two-step verification is off" else "Two-step verification is on"); onDone(!enabled); onDismiss() }
                .onFailure { err = Api.friendly(it) }
            busy = false
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (enabled) "Turn off two-step verification" else "Set up two-step verification") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!enabled) {
                    Text("1. Open an authenticator app (Google Authenticator, Authy…) and add an account with this key:", style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(secret?.chunked(4)?.joinToString(" ") ?: "…", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("COPY", Modifier.tap(RoundedCornerShape(6.dp)) { secret?.let { settingsCopy(c, it) } }.padding(6.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                    Text("2. Type the 6-digit code it shows:", style = MaterialTheme.typography.bodyMedium)
                } else Text("Enter the current 6-digit code from your authenticator app.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth().firstFocus(always = true), label = { Text("6-digit code") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton({ go() }, enabled = !busy && (enabled || secret != null)) { Text(if (busy) "…" else if (enabled) "Turn off" else "Turn on", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

fun settingsCopy(c: Context, text: String) {
    runCatching { (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("YID PLUS", text)) }
    toast(c, "Copied")
}

