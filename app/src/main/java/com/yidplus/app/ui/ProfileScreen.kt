package com.yidplus.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yidplus.app.BuildConfig
import com.yidplus.app.GoogleAuth
import com.yidplus.app.NotifyWorker
import com.yidplus.app.data.Api
import com.yidplus.app.data.Notif
import com.yidplus.app.data.Prefs
import com.yidplus.app.data.Session
import com.yidplus.app.data.Store
import com.yidplus.app.data.objects
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen() {
    val me = Session.me
    Column(Modifier.fillMaxSize()) {
        TopBar(if (me == null) "Sign in" else "Profile")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                    if (me == null) LoginForm() else {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(me.photo, me.nickname, 64.dp)
                            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                Text(me.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(me.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (me.isOwner) Text("Owner", color = Gold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        SettingRow(Icons.Filled.Notifications, "Notifications", "Likes, comments, follows") { Nav.open(Screen.Notifications) }
                        SettingRow(Icons.AutoMirrored.Filled.OpenInNew, "My page on yidplus.com", "Edit profile, upload, settings") { Nav.open(Screen.Web(Api.BASE + "/")) }
                    }
                    Settings()
                }
            }
        }
    }
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
        Text("Sign in with your yidplus.com account", Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun Settings() {
    val c = ctx()
    val scope = rememberCoroutineScope()
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (!ok) toast(c, "Notifications are off in Android settings") }
    SectionTitle("App")
    SettingRow(Icons.Filled.SystemUpdate, "Check for updates", "Version ${BuildConfig.VERSION_NAME}" + (UpdateUi.status?.let { " · $it" } ?: "")) { UpdateUi.check(c, force = true, userAsked = true) }
    SettingRow(Icons.Filled.DarkMode, "Theme", listOf("Same as phone", "Light", "Dark")[Prefs.theme]) { Prefs.setThemeMode((Prefs.theme + 1) % 3) }
    SettingRow(Icons.Filled.Notifications, "Notifications", if (Prefs.notify) "On · new messages, likes, comments, channel posts" + (if (com.yidplus.app.Push.state == "on") " · instant" else "") else "Off") {
        Prefs.enableNotify(!Prefs.notify)
        if (Prefs.notify) {
            NotifyWorker.schedule(c)
            com.yidplus.app.Push.start(c)
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    SettingRow(Icons.Filled.Keyboard, "Keypad help", "↑↓ move · OK open · Back goes back · Shorts: 1 like, 3 comments") { Prefs.keypadHintSeen = false; Feeds.tab = ShortsTab.ForYou; Nav.go(Tab.Shorts) }
    SettingRow(Icons.AutoMirrored.Filled.OpenInNew, "Open yidplus.com in the browser", null) { openUrl(c, Api.BASE) }
    SettingRow(Icons.Filled.Info, "About", "YID PLUS ${BuildConfig.VERSION_NAME} · native app") { toast(c, "YID PLUS ${BuildConfig.VERSION_NAME}") }
    if (Session.me != null) SettingRow(Icons.AutoMirrored.Filled.Logout, "Sign out", Session.me?.email) { scope.launch { Session.logout(); toast(c, "Signed out") } }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, sub: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).tap(RoundedCornerShape(0.dp), onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The site's own notifications (likes, comments, follows, replies). */
@Composable
fun NotificationsScreen() {
    val c = ctx()
    val scope = rememberCoroutineScope()
    val res = remember(Session.me?.id) { Store.res("/api/notifications") { j -> j.optJSONArray("notifications").objects().map(Notif::from) } }
    LaunchedEffect(res) { res.load(force = true); if (res.data?.any { !it.read } == true) runCatching { Api.post("/api/notifications", org.json.JSONObject().put("read", "all")) } }
    Column(Modifier.fillMaxSize()) {
        TopBar("Notifications", onBack = { Nav.back() })
        val list = res.data
        when {
            list == null && res.error != null -> Problem(res.error!!, res.offline, { scope.launch { res.load(true) } })
            list == null -> Loading()
            list.isEmpty() -> Text("Nothing yet", Modifier.padding(24.dp))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { n ->
                    Row(Modifier.fillMaxWidth().background(if (!n.read) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f) else androidx.compose.ui.graphics.Color.Transparent)
                        .tap(RoundedCornerShape(0.dp)) { if (n.link.isNotBlank()) com.yidplus.app.MainActivity.route(c, Api.abs(n.link)!!) }
                        .then(if (n == list.first()) Modifier.firstFocus() else Modifier).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(null, n.actor.ifBlank { "Y" }, 36.dp)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(n.text.ifBlank { n.type }, style = MaterialTheme.typography.bodyMedium)
                            Text(ago(n.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
