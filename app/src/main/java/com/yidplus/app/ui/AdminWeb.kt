package com.yidplus.app.ui

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yidplus.app.data.Api
import com.yidplus.app.data.Session
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The website's admin page for the rare sections the app doesn't draw itself (ads, logs, email, bots, backups…).
 * The site's page opens with its own door: the account's email (already filled in by the site) and then the 4-digit
 * admin PIN, checked by the site (POST /api/admin/verify-pin {pin}). The app asks for the PIN once, natively, checks it
 * the same way, and then types it into the site's own door for you, so the page opens straight on the section.
 */
object AdminPin { var pin: String? = null }

data class AdminWebPage(val section: String? = null) : PageScreen() {
    override val perfName = "AdminWeb"
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        var pin by remember { mutableStateOf("") }
        var unlocked by remember { mutableStateOf(AdminPin.pin) }
        var err by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        val kb = LocalSoftwareKeyboardController.current
        val url = Api.BASE + "/yidplus-admin"
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopBar("Admin · website", onBack = { Nav.back() }, sub = section?.let { "Section: $it" }) {
                IconBtn(Icons.AutoMirrored.Filled.OpenInNew, "Open in the browser", { openUrl(c, url) }, tint = LocalContentColor.current)
            }
            val ok = unlocked
            if (ok == null) {
                fun go() {
                    if (pin.length != 4) { err = "Enter all 4 digits."; return }
                    busy = true; err = null; kb?.hide()
                    scope.launch {
                        runCatching { Api.post("/api/admin/verify-pin", JSONObject().put("pin", pin)) }
                            .onSuccess { AdminPin.pin = pin; unlocked = pin }
                            .onFailure { err = Api.friendly(it).ifBlank { "Incorrect PIN. Access denied." }; pin = "" }
                        busy = false
                    }
                }
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                    Text("Admin PIN", style = MaterialTheme.typography.titleLarge)
                    Text("The website's admin page asks for your 4-digit admin PIN. Signed in as " + (Session.me?.email ?: "you") + ".",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    OutlinedTextField(pin, { v -> pin = v.filter { it.isDigit() }.take(4); if (pin.length == 4) err = null }, Modifier.width(180.dp).firstFocus(always = true),
                        singleLine = true, textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, letterSpacing = 12.sp),
                        visualTransformation = PasswordVisualTransformation(), placeholder = { Text("••••", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { go() }))
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
                    PrimaryButton(if (busy) "Checking…" else "Unlock", { if (!busy) go() }, Modifier.widthIn(min = 180.dp), enabled = !busy)
                    Text("Everything the app has natively (users, reports, channels, Telegram, broadcasts, word filter, features) opens without the PIN.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            } else AdminWebView(url, ok, section, Modifier.weight(1f))
        }
    }
}

/** The site's own door, filled in for you: waits for the gate, presses Continue on the email step, types the PIN, presses Unlock, then opens the section. */
private fun gateJs(pin: String, section: String?): String {
    val sec = section?.filter { it.isLetterOrDigit() || it == '-' } ?: ""
    return """(function(){var pin='$pin',sec='$sec',n=0,asked=0,typed=0;function go(){n++;if(n>120)return;
if(window.ADMIN_GATE_SESSION){if(sec&&window.navTo){setTimeout(function(){try{navTo(sec)}catch(e){console.log('yp navTo '+e)}},700);}return;}
var g=document.getElementById('admin-gate');if(!g||!g.classList.contains('open'))return setTimeout(go,250);
var ps=document.getElementById('gate-pin-step');if(ps&&ps.style.display==='block'){if(!typed){typed=1;for(var i=0;i<4;i++){var e=document.getElementById('p'+i);if(e)e.value=pin.charAt(i);}if(window.checkPin)checkPin();}return setTimeout(go,300);}
var ge=document.getElementById('gate-email');if(ge&&ge.value&&!asked&&window.checkGateEmail){asked=1;checkGateEmail();}setTimeout(go,250);}go();})();"""
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AdminWebView(url: String, pin: String, section: String?, modifier: Modifier) {
    AndroidView(factory = { ctx ->
        val cm = CookieManager.getInstance()
        Api.session()?.let { cm.setCookie(Api.BASE, "yp_session=$it; Path=/; Secure; HttpOnly") }
        WebView(ctx).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.useWideViewPort = true; settings.loadWithOverviewMode = true
            settings.userAgentString = settings.userAgentString + " YIDPLUS-Android"
            setBackgroundColor(android.graphics.Color.WHITE)
            // The page's own errors go to the log (tag YidAdminWeb), so a blank page can be explained.
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    android.util.Log.i("YidAdminWeb", "${m.messageLevel()} ${m.message()} @${m.sourceId()?.substringAfterLast('/')}:${m.lineNumber()}"); return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, u: String) {
                    if (u.contains("admin")) view.evaluateJavascript(gateJs(pin, section), null)
                }
            }
            android.util.Log.i("YidAdminWeb", "webview " + (if (android.os.Build.VERSION.SDK_INT >= 26) WebView.getCurrentWebViewPackage()?.versionName ?: "?" else "?"))
            loadUrl(url)
        }
    }, modifier = modifier.fillMaxWidth())
}
