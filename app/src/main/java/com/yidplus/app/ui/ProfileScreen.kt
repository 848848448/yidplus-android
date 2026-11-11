package com.yidplus.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yidplus.app.BuildConfig
import com.yidplus.app.GoogleAuth
import com.yidplus.app.NotifyWorker
import com.yidplus.app.data.Api
import com.yidplus.app.data.Disk
import com.yidplus.app.data.Notif
import com.yidplus.app.data.Prefs
import com.yidplus.app.data.Session
import com.yidplus.app.data.Store
import com.yidplus.app.data.objects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen() {
    val me = Session.me
    val c = ctx()
    val scope = rememberCoroutineScope()
    // Emulator QA (signed out): `--es qa_page demo:Privacy` shows the signed-in screens with a sample profile.
    LaunchedEffect(Unit) {
        while (true) {
            val v = settingsQaExtra(c, "qa_page")
            if (v != null) {
                settingsQaClear(c, "qa_page")
                if (v.startsWith("demo")) SettingsProfile.demo = true
                val page: Screen? = when (v.substringAfter(':', "")) {
                    "EditProfile" -> SettingsEditProfile()
                    "Privacy" -> SettingsPrivacy()
                    "Security" -> SettingsSecurity()
                    "NotifPrefs" -> SettingsNotifPrefs()
                    "Blocked" -> SettingsBlocked()
                    "FollowRequests" -> FollowRequestsPage()
                    "MutedChats" -> SettingsMutedChats()
                    "Feedback" -> SettingsFeedback()
                    "Admin" -> AdminPage()
                    "About" -> SettingsAbout()
                    "ChatSettings" -> SettingsChat()
                    "Notifs" -> SoNotifsPage()
                    "AdminUsers" -> AdminUsersPage()
                    "AdminUser" -> AdminUserPage("u-yanky", org.json.JSONObject().put("id", "u-yanky").put("nickname", "Yanky").toString())
                    "AdminReports" -> AdminReportsPage()
                    "AdminVerify" -> AdminVerifyPage()
                    "AdminChannels" -> AdminChannelsPage()
                    "AdminTelegram" -> AdminTelegramPage()
                    "AdminBroadcast" -> AdminBroadcastPage()
                    "AdminBadWords" -> AdminBadWordsPage()
                    "AdminWeb" -> AdminWebPage("ads")
                    else -> null
                }
                if (v == "off") SettingsProfile.demo = false
                if (page != null) Nav.open(page)
            }
            delay(600)
        }
    }
    val signedIn = me != null || SettingsProfile.demo
    LaunchedEffect(me?.id, SettingsProfile.demo) { if (signedIn) SettingsProfile.load() }
    var deleting by remember { mutableStateOf(0) }
    var signingOut by remember { mutableStateOf(false) }
    LookPrefs.init(c)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar("Settings")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                    // The website's Settings page, in its order (js/theme.js buildSettingsPage).
                    if (!signedIn) Surface(color = MaterialTheme.colorScheme.surface) { LoginForm() } else {
                        SettingsTopCard { Nav.open(SettingsEditProfile()) }
                        SettingsCard {
                            // TODO(coordinator): SavedItemsPage / HistoryPage come from feat-new2 (ui/SavedHistory.kt); wire with Nav.open(SavedItemsPage()) / Nav.open(HistoryPage()).
                            SettingRow(Icons.Filled.Bookmark, "Saved Items", "Messages, posts and Shorts you saved") { Nav.open(SavedItemsPage()) }
                            RowDivider(64.dp)
                            SettingRow(Icons.Filled.History, "History", "What you watched and opened") { Nav.open(HistoryPage()) }
                        }
                    }
                    ReadingCard()
                    AppearanceCard()
                    ColorThemeCard()
                    if (signedIn) {
                        StatsCard()
                        SettingsCard("Account") {
                            SettingRow(Icons.Filled.Edit, "Profile", "Name, photo, cover, bio, location, links") { Nav.open(SettingsEditProfile()) }
                            RowDivider(64.dp)
                            SettingRow(Icons.Filled.Lock, "Privacy", "Private account, active status, read receipts, who can message you") { Nav.open(SettingsPrivacy()) }
                            RowDivider(64.dp)
                            SettingRow(Icons.Filled.Security, "Security", "Password, two-factor authentication") { Nav.open(SettingsSecurity()) }
                            RowDivider(64.dp)
                            SettingRow(Icons.Filled.Chat, "Chat", "Wallpaper, muted chats, silent calls") { Nav.open(SettingsChat()) }
                            RowDivider(64.dp)
                            SettingRow(Icons.Filled.NotificationsActive, "Notifications", "New messages, likes, followers, channel updates") { Nav.open(SettingsNotifPrefs()) }
                            RowDivider(64.dp)
                            val devs = SettingsProfile.res.data?.optJSONArray("sessions")?.length() ?: 0
                            SettingRow(Icons.Filled.Devices, "Devices", if (devs > 0) "$devs active · sign out other devices" else "Where you're signed in") { Nav.open(SettingsSecurity()) }
                            RowDivider(64.dp)
                            SettingRow(Icons.Filled.Block, "Blocked users", null) { Nav.open(SettingsBlocked()) }
                            RowDivider(64.dp)
                            SettingRow(Icons.Filled.Notifications, "Activity", "Likes, comments, follows") { Nav.open(SoNotifsPage()) }
                        }
                        VerificationCard()
                        ShareProfileCard()
                    }
                    AppSettings()
                    if (signedIn && settingsIsAdmin) SettingsCard("Admin") {
                        SettingRow(Icons.Filled.AdminPanelSettings, "Admin panel", "Users, reports, channels, broadcasts, features, stats") { Nav.open(AdminPage()) }
                    }
                    if (signedIn) SettingsCard {
                        SettingRow(Icons.Filled.DeleteForever, "Delete Account", "Removes your account and everything in it", danger = true) { deleting = 1 }
                        RowDivider(64.dp)
                        SettingRow(Icons.AutoMirrored.Filled.Logout, "Sign Out", Session.me?.email) { signingOut = true }
                    }
                    SettingsCard {
                        SettingRow(Icons.Filled.PrivacyTip, "Privacy Policy", null) { Nav.open(Screen.Web(Api.BASE + "/privacy.html")) }
                        RowDivider(64.dp)
                        SettingRow(Icons.Filled.Description, "Terms of Service", null) { Nav.open(Screen.Web(Api.BASE + "/terms.html")) }
                    }
                    Text("YID PLUS ${BuildConfig.VERSION_NAME}", Modifier.fillMaxWidth().padding(vertical = 20.dp), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (signingOut) SettingsConfirmDialog("Sign out?", "You can sign in again any time.", "Sign out", onOk = { scope.launch { Session.logout(); SettingsProfile.demo = false; toast(c, "Signed out") } }, onDismiss = { signingOut = false })
    if (deleting == 1) SettingsConfirmDialog("Delete your account?", "Your account, messages, videos and music are deleted for good. This can't be undone.", "Continue", danger = true,
        onOk = { deleting = 2 }, onDismiss = { if (deleting == 1) deleting = 0 })
    if (deleting == 2) SettingsPasswordDialog("Delete account", "Enter your password to delete your account for good.", "Delete forever", danger = true,
        onOk = {
            scope.launch {
                runCatching { if (!SettingsProfile.demo) Api.delete("/api/profile") }
                    .onSuccess { toast(c, "Account deleted"); Session.logout(); SettingsProfile.demo = false }.onFailure { toast(c, Api.friendly(it)) }
            }
        }, onDismiss = { deleting = 0 })
}

@Composable
private fun LoginForm() {
    val c = ctx()
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var pw by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var need2fa by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    // The email form opens on request, so the keyboard never pops up by itself over a small screen.
    var form by rememberSaveable { mutableStateOf(false) }
    fun go() {
        if (busy || email.isBlank() || pw.isBlank()) { err = "Enter your email and password"; return }
        busy = true; err = null
        scope.launch {
            when (val r = Session.login(email, pw, if (need2fa) code else null)) {
                Session.Login.Ok -> { toast(c, "Signed in"); NotifyWorker.schedule(c) }
                Session.Login.Need2fa -> { need2fa = true; err = "Enter the 6-digit code from your authenticator app" }
                is Session.Login.Fail -> err = r.msg
            }
            busy = false
        }
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Logo(Modifier.align(Alignment.CenterHorizontally))
        Text("Sign in with your yidplus.com account", Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!form) {
            PrimaryButton("Sign in with email", { form = true }, Modifier.fillMaxWidth().firstFocus())
            SecondaryButton("Sign in with Google", { GoogleAuth.start(c) }, Modifier.fillMaxWidth())
            if (com.yidplus.app.data.SiteConfig.registrationOpen) SecondaryButton("Create an account", { Nav.open(Screen.Web(Api.BASE + "/?signup=1")) }, Modifier.fillMaxWidth(), icon = Icons.Filled.PersonAdd)
            return@Column
        }
        OutlinedTextField(email, { email = it.trim() }, Modifier.fillMaxWidth().firstFocus(always = true), label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
        OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (need2fa) ImeAction.Next else ImeAction.Done), keyboardActions = KeyboardActions(onDone = { go() }))
        if (need2fa) OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text("6-digit code") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { go() }))
        if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        PrimaryButton(if (busy) "Signing in…" else "Sign in", { go() }, Modifier.fillMaxWidth(), enabled = !busy)
        SecondaryButton("Sign in with Google", { GoogleAuth.start(c) }, Modifier.fillMaxWidth())
        Text("Forgot your password? Reset it on yidplus.com.", Modifier.tap { Nav.open(Screen.Web(Api.BASE + "/reset-password")) }.padding(6.dp),
            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable
private fun AppSettings() {
    val c = ctx()
    val scope = rememberCoroutineScope()
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (!ok) toast(c, "Notifications are off in Android settings") }
    var pick by remember { mutableStateOf<String?>(null) }
    val themes = listOf("Same as phone", "Light", "Dark")
    val sizes = listOf("Small", "Normal", "Large")
    SettingsCard("App") {
        SettingRow(Icons.Filled.Notifications, "Phone notifications", if (Prefs.notify) "New messages, likes, comments, channel posts" + (if (com.yidplus.app.Push.state == "on") " · instant" else "") else "Off", switch = Prefs.notify) {
            Prefs.enableNotify(!Prefs.notify)
            if (Prefs.notify) {
                NotifyWorker.schedule(c)
                com.yidplus.app.Push.start(c)
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        RowDivider(64.dp)
        SettingRow(Icons.Filled.SystemUpdate, "Check for updates", "Version ${BuildConfig.VERSION_NAME}" + (UpdateUi.status?.let { " · $it" } ?: "")) { UpdateUi.check(c, force = true, userAsked = true) }
        RowDivider(64.dp)
        SettingRow(Icons.Filled.Keyboard, "Keypad help", "↑↓ move · OK open · Back goes back · Shorts: 1 like, 3 comments") { Prefs.keypadHintSeen = false; Feeds.tab = ShortsTab.ForYou; Nav.go(Tab.Shorts) }
        RowDivider(64.dp)
        SettingRow(Icons.Filled.CleaningServices, "Clear cache", "Frees space; your account and settings stay") {
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { Disk.clear() }; runCatching { coil.Coil.imageLoader(c).diskCache?.clear() } }
                runCatching { coil.Coil.imageLoader(c).memoryCache?.clear() }
                Store.clear(); toast(c, "Cache cleared")
            }
        }
    }
    SettingsCard("Help") {
        SettingRow(Icons.Filled.Feedback, "Send feedback", "Report a problem or share an idea") { Nav.open(SettingsFeedback()) }
        if (Session.me != null || SettingsProfile.demo) {
            RowDivider(64.dp)
            SettingRow(Icons.Filled.Download, "Download my data", "A copy of your profile, saved to Downloads") { scope.launch { toast(c, settingsExportData(c)) } }
        }
        RowDivider(64.dp)
        SettingRow(Icons.Filled.Info, "About YID PLUS", "Version ${BuildConfig.VERSION_NAME} · privacy · terms") { Nav.open(SettingsAbout()) }
    }
    when (pick) {
        "theme" -> SettingsChoiceDialog("Theme", themes, Prefs.theme, { Prefs.setThemeMode(it) }, { pick = null })
        "size" -> SettingsChoiceDialog("Text size", sizes, UiPrefs.textSize, { UiPrefs.setText(it) }, { pick = null })
    }
}

class SettingsAbout : PageScreen() {
    override val perfName = "About"
    @Composable override fun Content() {
        val c = ctx()
        SettingsPage("About") {
            Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Logo()
                Text("Version ${BuildConfig.VERSION_NAME} · native app", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsCard {
                SettingRow(Icons.Filled.SystemUpdate, "Check for updates", UpdateUi.status, modifier = Modifier.firstFocus()) { UpdateUi.check(c, force = true, userAsked = true) }
                RowDivider(64.dp)
                SettingRow(Icons.Filled.PrivacyTip, "Privacy policy", null) { Nav.open(Screen.Web(Api.BASE + "/privacy.html")) }
                RowDivider(64.dp)
                SettingRow(Icons.Filled.Description, "Terms of use", null) { Nav.open(Screen.Web(Api.BASE + "/terms.html")) }
                RowDivider(64.dp)
                SettingRow(Icons.AutoMirrored.Filled.OpenInNew, "Open yidplus.com in the browser", null) { openUrl(c, Api.BASE) }
            }
        }
    }
}

/** The site's own notifications (likes, comments, follows, replies): the same page as the bell (NotifsUi.kt). */
@Composable
fun NotificationsScreen() { NotifsPageBody() }
