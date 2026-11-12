package com.yidplus.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yidplus.app.data.Api
import com.yidplus.app.data.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/* 2.1 ST: the site's sign-up / password pages, natively.
   Register: auth.js doRegister → POST /api/auth/register {email,password,nickname,phone,invite_code}; nickname check GET /api/auth/check-nick?nick=.
   Forgot: auth.js requestPasswordReset → POST /api/auth/request-password-reset {email}.
   Reset (link from the email, /reset-password?token=): reset-password.html → POST /api/auth/reset-password {token,password}.
   Verify banner: auth.js renderVerifyBanner (me.unverified) → POST /api/auth/send-verification {}. */

/** A password box with the eye button (show / hide), like Android's own sign-in screens. */
@Composable
fun StPasswordField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, done: Boolean = false, onDone: () -> Unit = {}) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { IconButton({ show = !show }) { Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Hide password" else "Show password") } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (done) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { onDone() }))
}

@Composable
private fun StMsg(text: String?, ok: Boolean) {
    if (text.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().background(if (ok) Color(0x1F1F6F5C) else MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null, Modifier.size(20.dp), tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Text(text, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun StFormPage(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        TopBar(title, onBack = { Nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

/** Create an account (index.html #auth-register). */
class StRegisterPage(val invite: String = "") : PageScreen() {
    override val perfName: String get() = "Register"
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        var email by rememberSaveable { mutableStateOf("") }
        var nick by rememberSaveable { mutableStateOf("") }
        var phone by rememberSaveable { mutableStateOf("") }
        var pw by rememberSaveable { mutableStateOf("") }
        var pw2 by rememberSaveable { mutableStateOf("") }
        var code by rememberSaveable { mutableStateOf(invite) }
        var nickOk by remember { mutableStateOf<Boolean?>(null) }
        var nickWhy by remember { mutableStateOf("") }
        var msg by remember { mutableStateOf<String?>(null) }
        var ok by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        LaunchedEffect(nick) {
            nickOk = null; nickWhy = ""
            val n = nick.trim(); if (n.length < 3) return@LaunchedEffect
            delay(400)
            runCatching { Api.get("/api/auth/check-nick?nick=" + Uri.encode(n)) }.onSuccess { j -> nickOk = j.optBoolean("available"); nickWhy = j.optString("reason") }
        }
        fun go() {
            msg = null; ok = false
            val e = email.trim(); val n = nick.trim()
            when {
                e.isEmpty() || n.isEmpty() || pw.isEmpty() || pw2.isEmpty() -> msg = "Please fill in all required fields."
                !android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches() -> msg = "Enter a valid email address."
                n.length < 3 -> msg = "Nickname must be at least 3 characters."
                pw.length < 6 -> msg = "Password must be at least 6 characters."
                pw != pw2 -> msg = "Passwords do not match."
                else -> {
                    busy = true
                    scope.launch {
                        runCatching {
                            Api.post("/api/auth/register", JSONObject().put("email", e).put("password", pw).put("nickname", n).put("phone", phone.trim()).put("invite_code", code.trim()))
                        }.onSuccess {
                            ok = true; msg = "Account created! Your channel is being set up..."
                            Session.refresh()
                            delay(900)
                            if (Session.me != null) { toast(c, "Welcome to YID PLUS!"); Nav.back() }
                        }.onFailure { t ->
                            val m = Api.friendly(t)
                            msg = if (m.contains("already", true)) "This email is already registered." else m
                        }
                        busy = false
                    }
                }
            }
        }
        StFormPage("Create an account") {
            Logo(Modifier.align(Alignment.CenterHorizontally))
            if (invite.isNotBlank()) StMsg("You were invited! Your invite code is filled in.", true)
            StMsg(msg, ok)
            OutlinedTextField(email, { email = it.trim() }, Modifier.fillMaxWidth().firstFocus(always = true), label = { Text("Email address") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
            OutlinedTextField(nick, { nick = it.replace(" ", "") }, Modifier.fillMaxWidth(), label = { Text("Nickname (cannot be changed later)") }, singleLine = true,
                isError = nickOk == false,
                supportingText = { when (nickOk) { true -> Text("Available", color = MaterialTheme.colorScheme.primary); false -> Text(if (nickWhy.isNotBlank()) "Not allowed: $nickWhy" else "Already taken", color = MaterialTheme.colorScheme.error); null -> Text("Letters, numbers and _ (3 to 20)") } },
                trailingIcon = { when (nickOk) { true -> Icon(Icons.Rounded.CheckCircle, "Available", tint = MaterialTheme.colorScheme.primary); false -> Icon(Icons.Rounded.Error, "Taken", tint = MaterialTheme.colorScheme.error); null -> {} } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Phone (optional, private)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))
            StPasswordField(pw, { pw = it }, "Password (min 6 chars)")
            StPasswordField(pw2, { pw2 = it }, "Confirm password", done = true, onDone = { go() })
            OutlinedTextField(code, { code = it.trim().uppercase() }, Modifier.fillMaxWidth(), label = { Text("Invite code (if you have one)") }, singleLine = true)
            PrimaryButton(if (busy) "Creating…" else "Create Account", { if (!busy) go() }, Modifier.fillMaxWidth(), enabled = !busy)
            Text("By creating an account you agree to our Terms of Service and Privacy Policy.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("Terms of Service", Modifier.tap { Nav.open(Screen.Web(Api.BASE + "/terms.html")) }.padding(8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                Text("Privacy Policy", Modifier.tap { Nav.open(Screen.Web(Api.BASE + "/privacy.html")) }.padding(8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Forgot password (auth.js requestPasswordReset): the email gets a reset link. */
class StForgotPage(val email0: String = "") : PageScreen() {
    override val perfName: String get() = "ForgotPassword"
    @Composable override fun Content() {
        val scope = rememberCoroutineScope()
        var email by rememberSaveable { mutableStateOf(email0) }
        var msg by remember { mutableStateOf<String?>(null) }
        var ok by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        fun go() {
            val e = email.trim()
            if (e.isEmpty()) { ok = false; msg = "Please enter your email."; return }
            busy = true
            scope.launch {
                runCatching { Api.post("/api/auth/request-password-reset", JSONObject().put("email", e)) }
                    .onSuccess { ok = true; msg = "If that email has an account, a reset link is on its way. Check your inbox (and spam)." }
                    .onFailure { ok = false; msg = Api.friendly(it) }
                busy = false
            }
        }
        StFormPage("Forgot password") {
            Icon(Icons.Rounded.MarkEmailUnread, null, Modifier.size(56.dp).align(Alignment.CenterHorizontally), tint = MaterialTheme.colorScheme.primary)
            Text("Type the email of your account. We'll send you a link to set a new password.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            StMsg(msg, ok)
            OutlinedTextField(email, { email = it.trim() }, Modifier.fillMaxWidth().firstFocus(always = true), label = { Text("Email address") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { go() }))
            PrimaryButton(if (busy) "Sending…" else "Send reset link", { if (!busy) go() }, Modifier.fillMaxWidth(), enabled = !busy)
            SecondaryButton("Need help? Start chat", { Nav.open(StSupportPage("login")) }, Modifier.fillMaxWidth())
        }
    }
}

/** Set a new password from the email link (reset-password.html). */
class StResetPage(val token: String) : PageScreen() {
    override val perfName: String get() = "ResetPassword"
    @Composable override fun Content() {
        val scope = rememberCoroutineScope()
        var pw by rememberSaveable { mutableStateOf("") }
        var pw2 by rememberSaveable { mutableStateOf("") }
        var msg by remember { mutableStateOf<String?>(null) }
        var ok by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        fun go() {
            ok = false
            when {
                pw.length < 6 -> msg = "Password must be at least 6 characters."
                pw != pw2 -> msg = "The two passwords don’t match."
                else -> {
                    busy = true
                    scope.launch {
                        runCatching { Api.post("/api/auth/reset-password", JSONObject().put("token", token).put("password", pw)) }
                            .onSuccess { ok = true; msg = "Your password is changed. You can sign in now." }
                            .onFailure { msg = Api.friendly(it).ifBlank { "Something went wrong." } }
                        busy = false
                    }
                }
            }
        }
        StFormPage("Set a new password") {
            StMsg(msg, ok)
            if (!ok) {
                StPasswordField(pw, { pw = it }, "New password", Modifier.firstFocus(always = true))
                StPasswordField(pw2, { pw2 = it }, "Confirm new password", done = true, onDone = { go() })
                PrimaryButton(if (busy) "Saving…" else "Save New Password", { if (!busy) go() }, Modifier.fillMaxWidth(), enabled = !busy)
            } else PrimaryButton("Sign in", { Nav.go(Tab.Profile) }, Modifier.fillMaxWidth().firstFocus(always = true))
        }
    }
}

/** "Please verify your email" (auth.js renderVerifyBanner): shown on Settings while the account is unverified, until dismissed. */
object StVerify {
    var unverified by mutableStateOf(false)
    var dismissed by mutableStateOf(false)
    suspend fun check() { unverified = runCatching { Api.get("/api/auth/me").optJSONObject("user")?.let { it.optBoolean("unverified") || it.optInt("unverified") == 1 } ?: false }.getOrDefault(false) }
}

@Composable
fun StVerifyBanner() {
    val c = ctx()
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    LaunchedEffect(Session.me?.id) { if (Session.me != null) StVerify.check() }
    if (!StVerify.unverified || StVerify.dismissed || Session.me == null) return
    Surface(color = Color(0xFFFFF4E5), contentColor = Color(0xFF7A4E00)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MarkEmailRead, null, Modifier.size(22.dp))
            Text("Please verify your email to secure your account.", Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.bodySmall)
            Text(if (sending) "Sending…" else "Resend", Modifier.padding(start = 6.dp).background(Color(0xFF1F6F5C), RoundedCornerShape(14.dp)).tap(RoundedCornerShape(14.dp), enabled = !sending) {
                sending = true
                scope.launch {
                    runCatching { Api.post("/api/auth/send-verification", JSONObject()) }
                        .onSuccess { toast(c, "Verification email sent — check your inbox.") }.onFailure { toast(c, Api.friendly(it)) }
                    sending = false
                }
            }.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, style = MaterialTheme.typography.labelLarge)
            IconButton({ StVerify.dismissed = true }) { Icon(Icons.Rounded.Close, "Dismiss") }
        }
    }
}
