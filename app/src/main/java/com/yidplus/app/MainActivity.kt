package com.yidplus.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.app.Dialog
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {

    companion object {
        const val ACTION_CHECK_UPDATE = "com.yidplus.app.CHECK_UPDATE"
        const val HOME = "https://yidplus.com/"
        private val APP_HOSTS = setOf("yidplus.com", "www.yidplus.com")
        private val BRIDGE_ORIGINS = setOf("https://yidplus.com", "https://www.yidplus.com")
        private const val MAX_SAVE = 200L * 1024 * 1024
        private const val CHECK_EVERY_MS = 12L * 60 * 60 * 1000
    }

    private lateinit var web: WebView
    private lateinit var refresh: PullRefreshLayout
    private lateinit var progress: LinearProgressIndicator
    private lateinit var offline: View
    private lateinit var fullscreen: FrameLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var nav: BottomNavigationView
    private lateinit var titleView: TextView
    private var section = JSONObject()
    private var navFromCode = false
    private var authDialog: Dialog? = null

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newCachedThreadPool()
    private var started = 0L
    @Volatile private var pageShown = false
    private var mainFrameFailed = false
    @Volatile private var loadedOffline = false
    private var defaultBarColor = Color.WHITE
    private var lastTop = Color.WHITE
    private var lastBottom = Color.WHITE

    // Fullscreen video
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    // Uploads
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var cameraOutput: Uri? = null
    private var pendingChooser: WebChromeClient.FileChooserParams? = null
    private var askedCamera = false

    // Camera / microphone for calls
    private var mediaRequest: PermissionRequest? = null

    // Saving blob downloads
    private var saveReply: JavaScriptReplyProxy? = null
    private var saveStream: OutputStream? = null
    private var saveId: String? = null
    private var saveExpected = 0L
    private var saveBytes = 0L
    private val saveTimeout = Runnable { abortSave("Save timed out") }

    // Google sign-in hand-off
    private var verifier: String? = null
    private var authStarted = 0L

    // Updates
    private var updateDialogShowing = false
    private var awaitingInstallPermission = false

    private var network: ConnectivityManager.NetworkCallback? = null

    private val chooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val cb = fileCallback ?: return@registerForActivityResult
        fileCallback = null
        var uris: Array<Uri>? = null
        if (res.resultCode == RESULT_OK) {
            val data = res.data
            val clip: ClipData? = data?.clipData
            uris = when {
                clip != null && clip.itemCount > 0 -> Array(clip.itemCount) { clip.getItemAt(it).uri }
                data?.data != null -> arrayOf(data.data!!)
                cameraOutput != null && capturedSomething() -> arrayOf(cameraOutput!!)
                else -> null
            }
        }
        cameraOutput = null
        cb.onReceiveValue(uris)
    }

    private val cameraForChooser = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        val p = pendingChooser
        pendingChooser = null
        if (p != null && fileCallback != null) openChooser(p) else { fileCallback?.onReceiveValue(null); fileCallback = null }
    }

    private val mediaPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val r = mediaRequest ?: return@registerForActivityResult
        mediaRequest = null
        if (result.values.all { it }) r.grant(r.resources) else r.deny()
    }

    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) toast(getString(R.string.storage_needed))
    }

    private val saveLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val reply = saveReply ?: return@registerForActivityResult
        val uri = res.data?.data
        if (res.resultCode != RESULT_OK || uri == null) { abortSave("Save cancelled"); return@registerForActivityResult }
        try {
            saveStream = contentResolver.openOutputStream(uri, "wt") ?: throw IOException()
            reply.postMessage("{\"ready\":true}")
            resetSaveTimeout()
        } catch (e: Exception) { abortSave("Could not open file") }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // ---------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        started = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition { !pageShown && SystemClock.uptimeMillis() - started < 2500 }
        // The splash library re-applies the theme's bar colours when it leaves; put the page's back.
        splash.setOnExitAnimationListener { provider ->
            provider.remove()
            main.post { applyBars(lastTop, lastBottom) }
            main.postDelayed({ applyBars(lastTop, lastBottom) }, 300)
        }

        if (resources.configuration.smallestScreenWidthDp < 600) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        setContentView(R.layout.activity_main)
        web = findViewById(R.id.web)
        refresh = findViewById(R.id.refresh)
        progress = findViewById(R.id.progress)
        offline = findViewById(R.id.offline)
        fullscreen = findViewById(R.id.fullscreen)
        toolbar = findViewById(R.id.toolbar)
        nav = findViewById(R.id.nav)
        titleView = findViewById(R.id.title)
        setupBars()

        defaultBarColor = ContextCompat.getColor(this, R.color.bar_default)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        applyBars(defaultBarColor, defaultBarColor)

        prefsNative().let { verifier = it.getString("authVerifier", null); authStarted = it.getLong("authStarted", 0L) }

        setupWebView()

        refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.gold), ContextCompat.getColor(this, R.color.brand))
        refresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.refresh_bg))
        refresh.webScrollY = { web.scrollY }
        refresh.setOnRefreshListener { reload() }
        refresh.isEnabled = false // the refresh button in the top bar replaces pull-to-refresh (it fought scrolling on some phones)

        findViewById<MaterialButton>(R.id.retry).setOnClickListener { reload() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> exitFullscreen()
                    offline.visibility == View.VISIBLE && web.canGoBack() -> { offline.visibility = View.GONE; web.goBack() }
                    web.canGoBack() -> web.goBack()
                    else -> moveTaskToBack(true)
                }
            }
        })

        if (savedInstanceState != null && web.restoreState(savedInstanceState) != null) {
            pageShown = true
        } else {
            if (!handleIntent(intent, coldStart = true)) web.loadUrl(HOME)
        }
        watchNetwork()
        maybeCheckForUpdate(force = intent?.action == ACTION_CHECK_UPDATE)
    }

    private fun prefsNative() = getSharedPreferences("native", MODE_PRIVATE)

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val bg = ContextCompat.getColor(this, R.color.page_bg)
        web.setBackgroundColor(bg)
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.mediaPlaybackRequiresUserGesture = false
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.javaScriptCanOpenWindowsAutomatically = true
        s.setSupportMultipleWindows(false)
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.displayZoomControls = false
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.userAgentString = s.userAgentString + " YidPlusApp/" + BuildConfig.VERSION_NAME
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, false)
        if (Build.VERSION.SDK_INT >= 26) web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
        web.overScrollMode = View.OVER_SCROLL_NEVER

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(web, true)

        web.addJavascriptInterface(WebBridge(
            onPullable = { refresh.pageAllowsPull = it },
            onBars = { _, _ -> },
            onSection = { json -> main.post { onSection(json) } },
            onFocusNav = { main.post { focusNav() } },
            onFocusTop = { main.post { focusTop() } },
            onTab = { n -> main.post { if (n in 1..5) goTab(n - 1) } },
        ), "YidplusAppBridge")

        val script = assets.open("compat.js").bufferedReader().use { it.readText() } + "\n" +
            assets.open("app.js").bufferedReader().use { it.readText() }
        val docStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (docStart) WebViewCompat.addDocumentStartJavaScript(web, script, BRIDGE_ORIGINS)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "YidplusNative", BRIDGE_ORIGINS) { _, message, origin, isMainFrame, reply ->
                if (isMainFrame && origin.scheme == "https" && origin.host in APP_HOSTS) onPageMessage(message.data, reply)
            }
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                return route(request.url)
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                mainFrameFailed = false
                progress.visibility = View.VISIBLE
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                if (!mainFrameFailed) { pageShown = true; offline.visibility = View.GONE }
                // Old web engines can't run the script at document start: run it as early as they let us
                if (!docStart && url != null && Uri.parse(url).host in APP_HOSTS) view.evaluateJavascript(script, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                refresh.isRefreshing = false
                progress.visibility = View.GONE
                if (!mainFrameFailed) { pageShown = true; offline.visibility = View.GONE }
                // The site's own offline page (from its service worker) counts as offline too
                loadedOffline = !isOnline()
                if (!docStart && url != null && Uri.parse(url).host in APP_HOSTS) view.evaluateJavascript(script, null)
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                mainFrameFailed = true
                refresh.isRefreshing = false
                progress.visibility = View.GONE
                showOffline()
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                recreate()
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.setProgressCompat(newProgress, true)
                if (newProgress >= 100) progress.visibility = View.GONE
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) { callback.onCustomViewHidden(); return }
                customView = view
                customCallback = callback
                fullscreen.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                fullscreen.visibility = View.VISIBLE
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                WindowCompat.getInsetsController(window, window.decorView).let {
                    it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    it.hide(WindowInsetsCompat.Type.systemBars())
                }
            }

            override fun onHideCustomView() = exitFullscreen()

            override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val wantsMedia = acceptTypes(params).let { it.isEmpty() || it.any { t -> t.startsWith("image/") || t.startsWith("video/") || t == "*/*" } }
                if (wantsMedia && !hasPermission(Manifest.permission.CAMERA) && !askedCamera) {
                    askedCamera = true
                    pendingChooser = params
                    cameraForChooser.launch(Manifest.permission.CAMERA)
                    return true
                }
                openChooser(params)
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                main.post {
                    if (request.origin.host !in APP_HOSTS) { request.deny(); return@post }
                    val wanted = mutableListOf<String>()
                    for (r in request.resources) when (r) {
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> wanted += Manifest.permission.RECORD_AUDIO
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> wanted += Manifest.permission.CAMERA
                        PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> Unit
                        else -> { request.deny(); return@post }
                    }
                    val missing = wanted.filterNot { hasPermission(it) }
                    if (missing.isEmpty()) { request.grant(request.resources); return@post }
                    mediaRequest?.deny()
                    mediaRequest = request
                    mediaPermissions.launch(missing.toTypedArray())
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest) { if (mediaRequest == request) mediaRequest = null }
        }

        web.setDownloadListener { url, userAgent, disposition, mime, _ ->
            val name = URLUtil.guessFileName(url, disposition, mime)
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                web.evaluateJavascript("window.__ypSaveUrl && window.__ypSaveUrl(" + JSONObject.quote(url) + "," + JSONObject.quote(name) + ")", null)
                return@setDownloadListener
            }
            val u = Uri.parse(url)
            if (u.scheme != "https") { toast(getString(R.string.download_failed)); return@setDownloadListener }
            if (Build.VERSION.SDK_INT < 29 && !hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE); return@setDownloadListener
            }
            try {
                val req = DownloadManager.Request(u)
                    .setTitle(name)
                    .setMimeType(mime)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                    .addRequestHeader("User-Agent", userAgent)
                if (u.host in APP_HOSTS) CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                toast(getString(R.string.downloading, name))
            } catch (e: Exception) { toast(getString(R.string.download_failed)) }
        }
    }

    /** true = handled natively (kept out of the WebView). */
    private fun route(u: Uri): Boolean {
        val scheme = u.scheme?.lowercase() ?: return false
        if ((scheme == "https" || scheme == "http") && u.host?.lowercase() in APP_HOSTS) {
            if (u.path == "/api/auth/google-login") { startGoogle(); return true }
            if (scheme == "http") { web.loadUrl(u.buildUpon().scheme("https").build().toString()); return true }
            return false
        }
        if (scheme == "about" || scheme == "blob" || scheme == "data" || scheme == "javascript") return false
        if (scheme == "intent") {
            try {
                val i = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME)
                i.addCategory(Intent.CATEGORY_BROWSABLE); i.component = null; i.selector = null
                try { startActivity(i) } catch (e: ActivityNotFoundException) {
                    i.getStringExtra("browser_fallback_url")?.let { openExternal(Uri.parse(it)) }
                }
            } catch (e: Exception) { }
            return true
        }
        openExternal(u)
        return true
    }

    private fun openExternal(u: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, u).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (e: ActivityNotFoundException) { toast(getString(R.string.no_app_for_link)) }
    }

    // ---- Intents: app links, sign-in hand-off, notification taps ---------------------------

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent, coldStart = false)
        if (intent.action == ACTION_CHECK_UPDATE) maybeCheckForUpdate(force = true)
    }

    /** true when it loaded something. */
    private fun handleIntent(intent: Intent?, coldStart: Boolean): Boolean {
        val data = intent?.data ?: return false
        if (data.scheme == "yidplus" && data.host == "auth") { finishGoogle(data); return false }
        if (intent.action == Intent.ACTION_VIEW && data.scheme == "https" && data.host?.lowercase() in APP_HOSTS) {
            web.loadUrl(data.toString()); return true
        }
        return false
    }

    // ---- Google sign-in runs in the browser (Google blocks it inside apps' web views) -------

    private fun startGoogle() {
        try {
            val b = ByteArray(32); SecureRandom().nextBytes(b)
            val v = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(v.toByteArray(StandardCharsets.US_ASCII)), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            verifier = v; authStarted = System.currentTimeMillis()
            prefsNative().edit().putString("authVerifier", v).putLong("authStarted", authStarted).apply()
            val target = "/native-login-complete?challenge=$challenge"
            val url = Uri.parse("https://yidplus.com/api/auth/google-login?return_to=" + Uri.encode(target))
            val colors = CustomTabColorSchemeParams.Builder().setToolbarColor(ContextCompat.getColor(this, R.color.brand)).build()
            // Always name the browser: yidplus.com links open this app, so an unnamed link would come straight back here
            val tabsPkg = try { androidx.browser.customtabs.CustomTabsClient.getPackageName(this, emptyList<String>()) } catch (e: Exception) { null }
            val browser = tabsPkg?.takeIf { it != packageName }
                ?: packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE), 0)
                    .map { it.activityInfo.packageName }.firstOrNull { it != packageName }
            if (browser == null) { googleInApp(url); return }
            try {
                val tab = CustomTabsIntent.Builder().setDefaultColorSchemeParams(colors).setShowTitle(true).build()
                tab.intent.setPackage(browser)
                tab.launchUrl(this, url)
            } catch (e: Exception) {
                try { startActivity(Intent(Intent.ACTION_VIEW, url).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(browser)) }
                catch (e2: Exception) { googleInApp(url) }
            }
        } catch (e: Exception) { toast(getString(R.string.google_failed)) }
    }

    private fun finishGoogle(u: Uri) {
        val v = verifier
        val code = u.getQueryParameter("code")
        if (v == null || System.currentTimeMillis() - authStarted > 10 * 60 * 1000 || code == null || !code.matches(Regex("[0-9a-f]{64}"))) {
            toast(getString(R.string.google_failed)); return
        }
        verifier = null
        prefsNative().edit().remove("authVerifier").remove("authStarted").apply()
        io.execute {
            try {
                val c = URL("https://yidplus.com/api/auth/native-exchange").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.connectTimeout = 20_000; c.readTimeout = 20_000; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(JSONObject().put("code", code).put("verifier", v).toString().toByteArray(StandardCharsets.UTF_8)) }
                if (c.responseCode != 200) throw IOException("exchange ${c.responseCode}")
                val set = c.headerFields.entries.filter { it.key != null && it.key.equals("Set-Cookie", true) }.flatMap { it.value }
                c.disconnect()
                if (set.isEmpty()) throw IOException("no cookie")
                main.post {
                    val left = AtomicInteger(set.size)
                    for (cookie in set) CookieManager.getInstance().setCookie("https://yidplus.com", cookie) {
                        if (left.decrementAndGet() == 0) { CookieManager.getInstance().flush(); web.loadUrl(HOME) }
                    }
                }
            } catch (e: Exception) { main.post { toast(getString(R.string.google_failed)) } }
        }
    }

    // ---- Messages from the page --------------------------------------------------------

    private fun onPageMessage(raw: String?, reply: JavaScriptReplyProxy) {
        if (raw == null || raw.length > 200_000) return
        try {
            val m = JSONObject(raw)
            when (m.optString("type")) {
                "share" -> share(m.optString("title"), m.optString("text"), m.optString("url"))
                "toast" -> toast(m.optString("text").take(200))
                "googleLogin" -> startGoogle()
                "checkUpdate" -> maybeCheckForUpdate(force = true, userAsked = true)
                "saveBegin" -> {
                    if (saveReply != null) { reply.postMessage("{\"error\":\"Another save is in progress\"}"); return }
                    val size = m.optLong("size", -1)
                    if (size < 0 || size > MAX_SAVE) { reply.postMessage("{\"error\":\"File is larger than 200 MB\"}"); return }
                    saveReply = reply; saveExpected = size; saveBytes = 0; saveId = m.getString("id")
                    var name = m.optString("name", "download").replace(Regex("[^\\p{L}\\p{N} ._()\\-]"), "_").take(120)
                    if (name.isBlank()) name = "download"
                    val mime = m.optString("mime").ifBlank { MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream" }
                    val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE, name)
                    saveLauncher.launch(i)
                    resetSaveTimeout()
                }
                "saveChunk" -> {
                    val out = saveStream
                    if (saveReply == null || out == null || saveId != m.optString("id")) return
                    val bytes = Base64.decode(m.getString("data"), Base64.NO_WRAP)
                    if (bytes.size > 49152 || saveBytes + bytes.size > saveExpected) throw IOException("bad size")
                    out.write(bytes); saveBytes += bytes.size
                    resetSaveTimeout()
                    reply.postMessage("{\"ack\":true}")
                }
                "saveEnd" -> {
                    val out = saveStream
                    if (saveReply == null || out == null || saveId != m.optString("id")) return
                    if (saveBytes != saveExpected) throw IOException("incomplete")
                    out.close(); saveStream = null
                    reply.postMessage("{\"done\":true}")
                    clearSave()
                    toast(getString(R.string.file_saved))
                }
                "saveAbort" -> abortSave("Save cancelled")
            }
        } catch (e: Exception) { if (saveReply != null) abortSave("Could not save file") }
    }

    private fun share(title: String, text: String, url: String) {
        val body = listOf(text, url).filter { it.isNotBlank() }.distinct().joinToString("\n")
        if (body.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body)
        if (title.isNotBlank()) send.putExtra(Intent.EXTRA_SUBJECT, title)
        try { startActivity(Intent.createChooser(send, title.ifBlank { null })) } catch (e: Exception) { }
    }

    private fun resetSaveTimeout() { main.removeCallbacks(saveTimeout); main.postDelayed(saveTimeout, 120_000) }
    private fun clearSave() { main.removeCallbacks(saveTimeout); saveReply = null; saveId = null; saveBytes = 0; saveExpected = 0 }
    private fun abortSave(error: String) {
        try { saveStream?.close() } catch (_: IOException) { }
        saveStream = null
        saveReply?.let { runCatching { it.postMessage(JSONObject().put("error", error).toString()) } }
        clearSave()
    }

    // ---- Uploads -----------------------------------------------------------------------

    private fun acceptTypes(p: WebChromeClient.FileChooserParams): List<String> =
        p.acceptTypes.flatMap { it.split(",") }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.map { t ->
            if (t.startsWith(".")) MimeTypeMap.getSingleton().getMimeTypeFromExtension(t.substring(1)) ?: "*/*" else t
        }.distinct()

    private fun openChooser(p: WebChromeClient.FileChooserParams) {
        val types = acceptTypes(p)
        val anyType = types.isEmpty() || types.contains("*/*")
        val wantsImage = anyType || types.any { it.startsWith("image/") }
        val wantsVideo = anyType || types.any { it.startsWith("video/") }
        val extra = mutableListOf<Intent>()
        if (hasPermission(Manifest.permission.CAMERA)) {
            if (wantsImage) runCatching {
                val dir = File(cacheDir, "captures").apply { mkdirs() }
                dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
                val f = File(dir, "photo-${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
                cameraOutput = uri
                extra += Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (wantsVideo) extra += Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE)
        }
        val pick = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE)
        if (anyType || types.size != 1) {
            pick.type = "*/*"
            if (!anyType) pick.putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
        } else pick.type = types[0]
        if (p.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        val launch = if (p.isCaptureEnabled && extra.isNotEmpty()) extra.first()
        else Intent.createChooser(pick, null).apply { if (extra.isNotEmpty()) putExtra(Intent.EXTRA_INITIAL_INTENTS, extra.toTypedArray()) }
        try { chooserLauncher.launch(launch) } catch (e: Exception) { fileCallback?.onReceiveValue(null); fileCallback = null }
    }

    private fun capturedSomething(): Boolean {
        val u = cameraOutput ?: return false
        return runCatching { contentResolver.openFileDescriptor(u, "r")?.use { it.statSize > 0 } ?: false }.getOrDefault(false)
    }

    private fun hasPermission(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    // ---- Fullscreen, bars, offline -----------------------------------------------------

    private fun exitFullscreen() {
        val v = customView ?: return
        fullscreen.removeView(v)
        fullscreen.visibility = View.GONE
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        if (resources.configuration.smallestScreenWidthDp < 600) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onSection(section.toString())
    }


    // ---- Native top bar and bottom bar ------------------------------------------------------

    private val tabIds = intArrayOf(R.id.tab_home, R.id.tab_chats, R.id.tab_shorts, R.id.tab_music, R.id.tab_me)
    private val tabNames = arrayOf("home", "chats", "shorts", "music", "me")

    private fun setupBars() {
        toolbar.inflateMenu(R.menu.top_bar)
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_refresh -> { reload(); true }
                R.id.action_update -> { maybeCheckForUpdate(force = true, userAsked = true); true }
                else -> false
            }
        }
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        findViewById<View>(R.id.brand).setOnClickListener { goTab(0) }
        nav.setOnItemSelectedListener { item ->
            if (!navFromCode) { val i = tabIds.indexOf(item.itemId); if (i >= 0) goTab(i, fromNav = true) }
            true
        }
        nav.setOnItemReselectedListener { item -> val i = tabIds.indexOf(item.itemId); if (i >= 0) goTab(i, fromNav = true) }
        // Keypad phones: every bottom-bar button can take focus and shows it
        nav.post {
            for (id in tabIds) nav.findViewById<View>(id)?.let { v -> v.isFocusable = true; v.nextFocusUpId = R.id.web }
        }
        // A plain colour while a page loads; the page never paints the system bars any more
        applyBars(0, 0)
    }

    /** Opens a section. On the main page this switches screens in place; elsewhere it loads the page. */
    private fun goTab(i: Int, fromNav: Boolean = false) {
        if (customView != null) exitFullscreen()
        if (offline.visibility == View.VISIBLE) offline.visibility = View.GONE
        selectTab(i)
        val name = tabNames[i]
        val url = when (name) {
            "home" -> "https://yidplus.com/?goto=home"
            "chats" -> "https://yidplus.com/yidplus-chat.html"
            "shorts" -> "https://yidplus.com/yidplus-shorts.html"
            "music" -> "https://yidplus.com/yidplus-music.html"
            else -> if (section.optBoolean("signedIn")) "https://yidplus.com/?goto=settings" else "https://yidplus.com/?signin=1"
        }
        val host = web.url?.let { Uri.parse(it).host }
        if (pageShown && host in APP_HOSTS) {
            web.evaluateJavascript("(function(){if(window.__ypGo){window.__ypGo('$name');return 1}return 0})()") { r ->
                if (r != "1") web.loadUrl(url)
            }
        } else web.loadUrl(url)
        if (fromNav) web.requestFocus()
    }

    private fun selectTab(i: Int) {
        if (i < 0) return
        navFromCode = true
        try { if (nav.selectedItemId != tabIds[i]) nav.selectedItemId = tabIds[i] } finally { navFromCode = false }
    }

    private fun onSection(json: String) {
        val s = try { JSONObject(json) } catch (e: Exception) { return }
        section = s
        val tab = s.optString("tab")
        val i = tabNames.indexOf(tab)
        if (i >= 0) selectTab(i)
        nav.menu.findItem(R.id.tab_me)?.title = getString(if (s.optBoolean("signedIn")) R.string.tab_me else R.string.tab_sign_in)
        val shorts = tab == "shorts" && !s.optBoolean("inner")
        toolbar.visibility = if (shorts || customView != null) View.GONE else View.VISIBLE
        titleView.text = s.optString("title").ifBlank { getString(R.string.app_name) }
        val inner = s.optBoolean("inner")
        if (inner) {
            toolbar.setNavigationIcon(R.drawable.ic_back)
            toolbar.navigationContentDescription = getString(R.string.back)
            findViewById<ImageView>(R.id.brand_logo).visibility = View.GONE
        } else {
            toolbar.navigationIcon = null
            findViewById<ImageView>(R.id.brand_logo).visibility = View.VISIBLE
        }
        applyBars(0, 0)
    }

    private fun focusNav() {
        val id = if (nav.selectedItemId != 0) nav.selectedItemId else tabIds[0]
        (nav.findViewById<View>(id) ?: nav).requestFocus()
    }

    private fun focusTop() {
        if (toolbar.visibility != View.VISIBLE) return
        // the refresh button (or the back arrow on inner pages)
        val target = (0 until toolbar.childCount).map { toolbar.getChildAt(it) }.lastOrNull { it.isFocusable || it is ViewGroup }
        (target ?: toolbar).requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // From the top bar, D-pad down goes back into the page; from the page, the digits 1-5 are handled by the page script.
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            val f = currentFocus
            if (f != null && f !== web && isInside(f, toolbar)) { web.requestFocus(); return true }
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_5) {
            val f = currentFocus
            if (f == null || isInside(f, nav) || isInside(f, toolbar)) { goTab(event.keyCode - KeyEvent.KEYCODE_1); return true }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isInside(v: View, parent: View): Boolean {
        var p: View? = v
        while (p != null) { if (p === parent) return true; p = p.parent as? View }
        return false
    }

    // ---- Google sign-in on phones with no browser (keypad phones, phones without Google) ----

    private fun googleInApp(url: Uri) {
        authDialog?.dismiss()
        val d = Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar)
        val w = WebView(this)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        // Google turns away embedded web views by their user agent; present as the phone's browser
        w.settings.userAgentString = web.settings.userAgentString.replace("; wv)", ")").replace(Regex("Version/[0-9.]+ "), "").replace(Regex(" YidPlusApp/\\S+"), "")
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.scheme == "yidplus" && u.host == "auth") { d.dismiss(); finishGoogle(u); return true }
                if (u.scheme == "intent") {
                    try { val i = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME); val du = i.data
                        if (du != null && du.scheme == "yidplus" && du.host == "auth") { d.dismiss(); finishGoogle(du); return true } } catch (e: Exception) {}
                    return true
                }
                return !(u.scheme == "https" || u.scheme == "http")
            }
        }
        d.setContentView(w)
        d.setOnDismissListener { w.destroy(); if (authDialog === d) authDialog = null }
        d.setOnKeyListener { _, code, ev ->
            if (code == KeyEvent.KEYCODE_BACK && ev.action == KeyEvent.ACTION_UP) { if (w.canGoBack()) w.goBack() else d.dismiss(); true } else false
        }
        authDialog = d
        d.show()
        toast(getString(R.string.google_in_app))
        w.loadUrl(url.toString())
    }

    private fun applyBars(@Suppress("UNUSED_PARAMETER") pageTop: Int, @Suppress("UNUSED_PARAMETER") pageBottom: Int) {
        val fullscreenVideo = customView != null
        val top = when {
            fullscreenVideo -> Color.BLACK
            ::toolbar.isInitialized && toolbar.visibility == View.VISIBLE -> ContextCompat.getColor(this, R.color.top_bar)
            else -> Color.BLACK
        }
        val bottom = if (fullscreenVideo) Color.BLACK else ContextCompat.getColor(this, R.color.bottom_bar)
        lastTop = top; lastBottom = bottom
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = top
        window.navigationBarColor = bottom
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.isAppearanceLightStatusBars = isLight(top)
        if (Build.VERSION.SDK_INT >= 26) c.isAppearanceLightNavigationBars = isLight(bottom)
    }

    private fun isLight(color: Int): Boolean {
        val l = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
        return l > 0.6
    }

    private fun showOffline() {
        pageShown = true
        offline.visibility = View.VISIBLE
        val online = isOnline()
        findViewById<TextView>(R.id.offline_title).setText(if (online) R.string.load_failed_title else R.string.offline_title)
        findViewById<TextView>(R.id.offline_text).setText(if (online) R.string.load_failed_text else R.string.offline_text)
        applyBars(ContextCompat.getColor(this, R.color.page_bg), ContextCompat.getColor(this, R.color.page_bg))
    }

    private fun reload() {
        if (offline.visibility == View.VISIBLE) {
            offline.visibility = View.GONE
            val u = web.url
            if (u.isNullOrBlank() || u.startsWith("about:") || u.startsWith("data:")) web.loadUrl(HOME) else web.reload()
        } else web.reload()
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun watchNetwork() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) {
                main.postDelayed({
                    if (offline.visibility == View.VISIBLE || loadedOffline) { loadedOffline = false; reload() }
                }, 800)
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb); network = cb }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    // ---- Updates ----------------------------------------------------------------------

    private fun maybeCheckForUpdate(force: Boolean, userAsked: Boolean = false) {
        val prefs = Updater.prefs(this)
        val last = prefs.getLong("lastUiCheck", 0L)
        if (!force && System.currentTimeMillis() - last < CHECK_EVERY_MS && !isColdStart()) return
        prefs.edit().putLong("lastUiCheck", System.currentTimeMillis()).apply()
        io.execute {
            val info = Updater.fetch()
            main.post {
                if (isFinishing || isDestroyed) return@post
                when {
                    info == null -> if (userAsked) toast(getString(R.string.update_check_failed))
                    !Updater.isNewer(this, info) -> if (userAsked) toast(getString(R.string.up_to_date, BuildConfig.VERSION_NAME))
                    else -> {
                        val laterFor = prefs.getLong("laterCode", -1L)
                        val laterAt = prefs.getLong("laterAt", 0L)
                        val snoozed = laterFor == info.versionCode && System.currentTimeMillis() - laterAt < 24L * 60 * 60 * 1000
                        if (!snoozed || force) showUpdateDialog(info)
                    }
                }
            }
        }
    }

    private var coldStartChecked = false
    private fun isColdStart(): Boolean { val r = !coldStartChecked; coldStartChecked = true; return r }

    private fun showUpdateDialog(info: UpdateInfo) {
        if (updateDialogShowing) return
        updateDialogShowing = true
        Notifier.cancel(this, 7000)
        val view = layoutInflater.inflate(R.layout.dialog_update, null)
        view.findViewById<TextView>(R.id.update_version).text = getString(R.string.update_version_line, BuildConfig.VERSION_NAME, info.versionName)
        val notes = view.findViewById<TextView>(R.id.update_notes)
        val cleanNotes = info.notes.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .joinToString("\n") { if (it.startsWith("- ") || it.startsWith("* ")) "•  " + it.substring(2) else it }
        if (cleanNotes.isBlank()) notes.visibility = View.GONE else notes.text = cleanNotes
        if (info.size > 0) view.findViewById<TextView>(R.id.update_size).text = getString(R.string.update_size, info.size / 1024.0 / 1024.0)
        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_YidPlus_Dialog)
            .setView(view)
            .setCancelable(true)
            .setPositiveButton(R.string.update_now) { _, _ -> startUpdate(info) }
            .setNegativeButton(R.string.later) { _, _ ->
                Updater.prefs(this).edit().putLong("laterCode", info.versionCode).putLong("laterAt", System.currentTimeMillis()).apply()
            }
            .setOnDismissListener { updateDialogShowing = false }
            .show()
    }

    private fun startUpdate(info: UpdateInfo) {
        if (!Updater.canInstall(this)) {
            MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_YidPlus_Dialog)
                .setTitle(R.string.allow_title)
                .setMessage(R.string.allow_text)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    awaitingInstallPermission = true
                    pendingUpdate = info
                    runCatching { startActivity(Updater.installPermissionIntent(this)) }
                }
                .setNegativeButton(R.string.later, null)
                .show()
            return
        }
        val view = layoutInflater.inflate(R.layout.dialog_download, null)
        val bar = view.findViewById<LinearProgressIndicator>(R.id.download_progress)
        val label = view.findViewById<TextView>(R.id.download_label)
        val cancelled = AtomicBoolean(false)
        val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_YidPlus_Dialog)
            .setView(view)
            .setCancelable(false)
            .setNegativeButton(R.string.cancel) { _, _ -> cancelled.set(true) }
            .show()
        io.execute {
            try {
                val apk = Updater.download(this, info, { cancelled.get() }) { p ->
                    main.post { bar.isIndeterminate = false; bar.setProgressCompat(p, true); label.text = getString(R.string.downloading_update, p) }
                }
                main.post {
                    dialog.dismiss()
                    if (!cancelled.get()) install(apk)
                }
            } catch (e: Exception) {
                main.post {
                    dialog.dismiss()
                    if (!cancelled.get()) toast(if (e is Updater.UpdateException) e.message ?: getString(R.string.update_failed) else getString(R.string.update_failed))
                }
            }
        }
    }

    private var pendingUpdate: UpdateInfo? = null

    private fun install(apk: File) {
        try { Updater.install(this, apk) } catch (e: Exception) { toast(getString(R.string.update_install_failed)) }
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        if (awaitingInstallPermission) {
            awaitingInstallPermission = false
            val info = pendingUpdate
            pendingUpdate = null
            if (info != null && Updater.canInstall(this)) startUpdate(info)
        } else maybeCheckForUpdate(force = false)
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        web.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        abortSave("App closed")
        fileCallback?.onReceiveValue(null); fileCallback = null
        mediaRequest?.deny(); mediaRequest = null
        network?.let { runCatching { (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it) } }
        runCatching { (web.parent as? ViewGroup)?.removeView(web); web.destroy() }
        io.shutdown()
        super.onDestroy()
    }
}
