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
                    // 2.1 ST pages
                    "UserProfile" -> StProfilePage("5df73439-246d-43d2-9b1e-fd72b30432e1", "DealPoint")
                    "PrivateProfile" -> StProfilePage("u-private", "Shloimy")
                    "MyProfile" -> StProfilePage("demo-me", Session.me?.nickname ?: "")
                    "Followers" -> StFollowListPage("demo-me", Session.me?.nickname ?: "", true)
                    "Following" -> StFollowListPage("5df73439-246d-43d2-9b1e-fd72b30432e1", "DealPoint", false)
                    "Support" -> StSupportPage()
                    "Register" -> StRegisterPage("YID2026")
                    "Forgot" -> StForgotPage("avrumy@example.com")
                    "Reset" -> StResetPage("fx-reset-token")
                    "AppLock" -> StAppLockPage()
                    "SettingsSearch" -> StSettingsSearchPage()
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
                        StVerifyBanner()
                        SearchPill("Search settings", { Nav.open(StSettingsSearchPage()) })
                        SettingsTopCard { Nav.open(SettingsEditProfile()) }
                    }
                    // 2.1 ST: WhatsApp's order: profile row, then the account rows, then look & feel, then help/about.
                    if (signedIn) { StMainRows(); StPeopleRows() }
                    AppearanceCard()
                    ColorThemeCard()
                    ReadingCard()
                    if (signedIn) { StatsCard(); VerificationCard(); ShareProfileCard() }
                    AppSettings()
                    if (signedIn && settingsIsAdmin) SettingsCard("Admin") {
                        SettingRow(Sym.AdminPanelSettings, "Admin panel", "Users, reports, channels, broadcasts, features, stats") { Nav.open(AdminPage()) }
                    }
                    if (signedIn) SettingsCard {
                        SettingRow(Sym.DeleteForever, "Delete Account", "Removes your account and everything in it", danger = true) { deleting = 1 }
                        RowDivider(64.dp)
                        SettingRow(Sym.Logout, "Sign Out", Session.me?.email) { signingOut = true }
                    }
                    SettingsCard {
                        SettingRow(Sym.PrivacyTip, "Privacy Policy", null) { Nav.open(Screen.Web(Api.BASE + "/privacy.html")) }
                        RowDivider(64.dp)
                        SettingRow(Sym.Description, "Terms of Service", null) { Nav.open(Screen.Web(Api.BASE + "/terms.html")) }
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
            if (com.yidplus.app.data.SiteConfig.registrationOpen) SecondaryButton("Create an account", { Nav.open(StRegisterPage()) }, Modifier.fillMaxWidth(), icon = Sym.PersonAdd)
            Text("Need help? Start chat", Modifier.align(Alignment.CenterHorizontally).tap { Nav.open(StSupportPage("login")) }.padding(8.dp),
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        OutlinedTextField(email, { email = it.trim() }, Modifier.fillMaxWidth().firstFocus(always = true), label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
        StPasswordField(pw, { pw = it }, "Password", done = !need2fa, onDone = { go() })
        if (need2fa) OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text("6-digit code") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { go() }))
        if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        PrimaryButton(if (busy) "Signing in…" else "Sign in", { go() }, Modifier.fillMaxWidth(), enabled = !busy)
        SecondaryButton("Sign in with Google", { GoogleAuth.start(c) }, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Forgot password?", Modifier.tap { Nav.open(StForgotPage(email)) }.padding(8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            Text("Need help?", Modifier.tap { Nav.open(StSupportPage("login")) }.padding(8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        if (com.yidplus.app.data.SiteConfig.registrationOpen) SecondaryButton("Create an account", { Nav.open(StRegisterPage()) }, Modifier.fillMaxWidth(), icon = Sym.PersonAdd)
    }
}

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable
private fun AppSettings() {
    val c = ctx()
    val scope = rememberCoroutineScope()
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (!ok) toast(c, "Notifications are off in Android settings") }
    var pick by remember { mutableStateOf<String?>(null) }
    var stExport by remember { mutableStateOf("") }
    val themes = listOf("Same as phone", "Light", "Dark")
    val sizes = listOf("Small", "Normal", "Large")
    SettingsCard("App") {
        SettingRow(Sym.Notifications, "Phone notifications", if (Prefs.notify) "New messages, likes, comments, channel posts" + (if (com.yidplus.app.Push.state == "on") " · instant" else "") else "Off", switch = Prefs.notify) {
            Prefs.enableNotify(!Prefs.notify)
            if (Prefs.notify) {
                NotifyWorker.schedule(c)
                com.yidplus.app.Push.start(c)
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        RowDivider(64.dp)
        SettingRow(Sym.SystemUpdate, "Check for updates", "Version ${BuildConfig.VERSION_NAME}" + (UpdateUi.status?.let { " · $it" } ?: "")) { UpdateUi.check(c, force = true, userAsked = true) }
        RowDivider(64.dp)
        SettingRow(Sym.Keyboard, "Keypad help", "↑↓ move · OK open · Back goes back · Shorts: 1 like, 3 comments") { Prefs.keypadHintSeen = false; Feeds.tab = ShortsTab.ForYou; Nav.go(Tab.Shorts) }
        RowDivider(64.dp)
        SettingRow(Sym.CleaningServices, "Clear cache", "Frees space; your account and settings stay") {
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { Disk.clear() }; runCatching { coil.Coil.imageLoader(c).diskCache?.clear() } }
                runCatching { coil.Coil.imageLoader(c).memoryCache?.clear() }
                Store.clear(); toast(c, "Cache cleared")
            }
        }
    }
    SettingsCard("Help") {
        SettingRow(Sym.SupportAgent, "Help & Support", "Ask a question or chat with the YID PLUS team") { Nav.open(StSupportPage()) }
        RowDivider(64.dp)
        SettingRow(Sym.Feedback, "Send feedback", "Report a problem or share an idea") { Nav.open(SettingsFeedback()) }
        if (Session.me != null || SettingsProfile.demo) {
            RowDivider(64.dp)
            SettingRow(Sym.Download, "Download my data", stExport.ifBlank { "Your account, posts, Shorts, statuses, music and sent messages" }) {
                if (stExport.startsWith("Preparing")) return@SettingRow
                scope.launch { toast(c, stExportMyData(c) { stExport = it }); stExport = "" }
            }
        }
        RowDivider(64.dp)
        SettingRow(Sym.PersonAdd, "Invite a friend", "Share YID PLUS with someone") { share(c, "Join me on YID PLUS, the Yiddish community: chats, channels, Shorts and music. https://yidplus.com") }
        RowDivider(64.dp)
        SettingRow(Sym.Info, "About YID PLUS", "Version ${BuildConfig.VERSION_NAME} · privacy · terms") { Nav.open(SettingsAbout()) }
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
                SettingRow(Sym.SystemUpdate, "Check for updates", UpdateUi.status, modifier = Modifier.firstFocus()) { UpdateUi.check(c, force = true, userAsked = true) }
                RowDivider(64.dp)
                SettingRow(Sym.PrivacyTip, "Privacy policy", null) { Nav.open(Screen.Web(Api.BASE + "/privacy.html")) }
                RowDivider(64.dp)
                SettingRow(Sym.Description, "Terms of use", null) { Nav.open(Screen.Web(Api.BASE + "/terms.html")) }
                RowDivider(64.dp)
                SettingRow(Sym.OpenInNew, "Open yidplus.com in the browser", null) { openUrl(c, Api.BASE) }
            }
        }
    }
}

/** The site's own notifications (likes, comments, follows, replies): the same page as the bell (NotifsUi.kt). */
@Composable
fun NotificationsScreen() { NotifsPageBody() }
