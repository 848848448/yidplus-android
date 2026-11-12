package com.yidplus.app.ui

import android.util.Log
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yidplus.app.data.Api
import com.yidplus.app.data.Prefs
import com.yidplus.app.data.Session
import com.yidplus.app.data.str

// The website's Settings page, section by section (js/theme.js buildSettingsPage + js/comfort.js "Reading and help").

/** The site's top card: photo, name, @nickname, email and an Edit Profile button (laid out as a row so it fits a 480x640 screen). */
@Composable
fun SettingsTopCard(onEdit: () -> Unit) {
    val me = Session.me
    val p = SettingsProfile.p
    val nick = p?.str("nickname")?.ifBlank { null } ?: me?.nickname ?: ""
    val first = p?.str("first_name")?.ifBlank { null }
    val photo = Api.abs(p?.str("photo_url")?.ifBlank { null }) ?: me?.photo
    val email = p?.str("email")?.ifBlank { null } ?: me?.email
    val verified = (p?.optInt("verified") ?: 0) == 1
    val role = when { me?.isOwner == true || me?.role == "owner" -> "Owner"; me?.role == "admin_super" -> "Super admin"; me?.role == "admin_limited" -> "Moderator"; else -> null }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.tap(CircleShape, onClick = onEdit)) {
                    Avatar(photo, nick.ifBlank { "Me" }, 72.dp)
                    Box(Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center) { Icon(Icons.Filled.CameraAlt, "Change photo", tint = Color.White, modifier = Modifier.size(13.dp)) }
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(first ?: nick, style = MaterialTheme.typography.titleLarge.merge(TextStyle(textDirection = TextDirection.Content)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (verified) Icon(Icons.Filled.Verified, "Verified", tint = BadgeGreen, modifier = Modifier.padding(start = 6.dp).size(18.dp))
                        if (role != null) Text(role, Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer, maxLines = 1)
                    }
                    Text("@$nick", style = MaterialTheme.typography.bodyLarge.merge(TextStyle(textDirection = TextDirection.Ltr)), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!email.isNullOrBlank()) Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                .firstFocus().tap(RoundedCornerShape(20.dp), onClick = onEdit).padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text("Edit Profile", Modifier.padding(start = 6.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** A row of 2-3 choices like the site's S / M / L buttons. */
@Composable
fun Segmented(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                .tap(RoundedCornerShape(8.dp)) { onPick(i) }.widthIn(min = 38.dp).padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text(o, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LabelRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String? = null, end: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 24.dp, end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        end()
    }
}

/** comfort.js "Reading and help": Higher contrast, Reduce motion (kept on this phone, like the site), and the app guide. */
@Composable
fun ReadingCard() {
    var guide by remember { mutableStateOf(false) }
    SettingsCard("Reading and help") {
        SettingRow(Icons.Filled.Contrast, "Higher contrast", "Darker text and lines", switch = LookPrefs.highContrast) { LookPrefs.putHighContrast(!LookPrefs.highContrast) }
        RowDivider(64.dp)
        SettingRow(Icons.Filled.MotionPhotosOff, "Reduce motion", "Fewer animations", switch = LookPrefs.reduceMotion) { LookPrefs.putReduceMotion(!LookPrefs.reduceMotion) }
        RowDivider(64.dp)
        SettingRow(Icons.Filled.HelpOutline, "App guide and what's new", null) { guide = true }
    }
    if (guide) AlertDialog(onDismissRequest = { guide = false }, title = { Text("Getting started") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Chats: open a conversation to send a message. Swipe or long-press a message to reply or forward. The search icon finds messages.")
                Text("Channels: open a channel to read updates or join. Music posts have a Download MP3 button.")
                Text("Your privacy: Settings controls your profile visibility, messages, read receipts and blocked users.")
                Text("Keypad: ↑↓ move · OK opens · Back goes back.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, confirmButton = { TextButton({ guide = false }, Modifier.firstFocus()) { Text("Done") } })
}

/** theme.js "Appearance": Dark Mode, Text Size S / M / L. */
@Composable
fun AppearanceCard() {
    SettingsCard("Appearance") {
        LabelRow(Icons.Filled.DarkMode, "Dark mode", if (Prefs.theme == 0) "Same as the phone" else null) {
            Segmented(listOf("Light", "Dark", "Auto"), when (Prefs.theme) { 1 -> 0; 2 -> 1; else -> 2 }) { Prefs.setThemeMode(when (it) { 0 -> 1; 1 -> 2; else -> 0 }) }
        }
        RowDivider(64.dp)
        LabelRow(Icons.Filled.FormatSize, "Text size") { Segmented(listOf("S", "M", "L"), UiPrefs.textSize) { UiPrefs.setText(it) } }
    }
}

/** theme.js "Color Theme": the site's 7 presets as swatches. */
@Composable
fun ColorThemeCard() {
    SettingsCard("Color theme") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
            // Two rows (4 + 3) so all 7 fit at 480 px without scrolling sideways.
            ColorPresets.indices.chunked(4).forEach { rowIdx ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    rowIdx.forEach { i -> Swatch(i) }
                    repeat(4 - rowIdx.size) { Spacer(Modifier.width(72.dp)) }
                }
            }
            if (LookPrefs.colorTheme != 0) Text("Reset to default", Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp).tap(RoundedCornerShape(8.dp)) { LookPrefs.putColor(0) }.padding(8.dp),
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Swatch(i: Int) {
    val p = ColorPresets[i]
    val sel = LookPrefs.colorTheme == i
    Column(Modifier.width(72.dp).semantics(mergeDescendants = true) { contentDescription = p.name }.tap(RoundedCornerShape(10.dp)) { LookPrefs.putColor(i); Log.i("YidFx", "LOOK color_theme=$i ${p.name}") }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(44.dp).clip(CircleShape).border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(if (sel) 4.dp else 0.dp).clip(CircleShape).background(Brush.linearGradient(listOf(p.accent, p.accentLight))), contentAlignment = Alignment.Center) {
            if (p.dark) Box(Modifier.size(16.dp).clip(CircleShape).background(p.bg))
            if (sel) Icon(Icons.Filled.Check, "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Text(p.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
    }
}

/** theme.js "Stats": Videos, Music Tracks, Messages, Profile Views, Followers (GET /api/profile → stats). */
@Composable
fun StatsCard() {
    val st = SettingsProfile.res.data?.optJSONObject("stats") ?: return
    SettingsCard("Stats") {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("shorts" to "Videos", "music" to "Music", "messages" to "Messages", "profile_views" to "Views", "followers" to "Followers").forEach { (k, l) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(adminN(st.optInt(k)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

/** theme.js "Chat": Chat wallpaper (yp_chat_wallpaper), Muted chats, Silent calls (yp_silent_calls). */
class SettingsChat : PageScreen() {
    override val perfName = "ChatSettings"
    override fun equals(other: Any?) = other is SettingsChat
    override fun hashCode() = 321
    @Composable override fun Content() {
        SettingsPage("Chat settings") {
            SettingsCard("Chat wallpaper") {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChatWallpapers.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { w ->
                                val sel = LookPrefs.wallpaper == w.id
                                Column(Modifier.weight(1f).tap(RoundedCornerShape(12.dp)) { LookPrefs.putWallpaper(w.id) }.then(if (w.id == "default") Modifier.firstFocus() else Modifier),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(12.dp))
                                        .background(if (w.from != null && w.to != null) Brush.verticalGradient(listOf(w.from, w.to)) else Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceVariant)))
                                        .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center) { if (sel) Icon(Icons.Filled.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary) }
                                    Text(w.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            SettingsCard("Chats") {
                SettingRow(Icons.Filled.NotificationsOff, "Muted chats", "Chats you silenced") { Nav.open(SettingsMutedChats()) }
                RowDivider(64.dp)
                SettingRow(Icons.Filled.PhoneDisabled, "Silent calls", "Incoming calls don't ring", switch = LookPrefs.silentCalls) { LookPrefs.putSilentCalls(!LookPrefs.silentCalls) }
            }
            SettingsHint("Wallpaper and silent calls are kept on this phone, like on the website.")
        }
    }
}

/** theme.js "Verification": Verified account (Active) or Get verified (POST /api/verify-request {}). */
@Composable
fun VerificationCard() {
    val c = ctx()
    val scope = rememberCoroutineScope()
    var sent by remember { mutableStateOf(false) }
    val verified = (SettingsProfile.p?.optInt("verified") ?: 0) == 1
    SettingsCard("Verification") {
        if (verified) SettingRow(Icons.Filled.Verified, "Verified account", null, value = "Active") {}
        else SettingRow(Icons.Filled.Verified, "Get verified", if (sent) "Request sent" else "Ask the YID PLUS team for the ✅ mark", value = if (sent) null else "Request") {
            if (sent) return@SettingRow
            adminDo(c, scope, "Request sent. The team will look at it.", { sent = true }) { if (!SettingsProfile.demo) Api.post("/api/verify-request", org.json.JSONObject()) }
        }
    }
}

/** The share rows (theme.js "Share Profile"). */
@Composable
fun ShareProfileCard() {
    val c = ctx()
    val nick = SettingsProfile.text("nickname").ifBlank { Session.me?.nickname ?: "" }
    val link = "https://yidplus.com/@" + Uri.encode(nick)
    SettingsCard("Share profile") {
        SettingRow(Icons.Filled.Share, "Share my profile", "yidplus.com/@$nick") { share(c, link) }
        RowDivider(64.dp)
        SettingRow(Icons.Filled.Link, "Copy link", null) { settingsCopy(c, link) }
    }
}
