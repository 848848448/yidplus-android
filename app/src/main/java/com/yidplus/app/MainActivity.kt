package com.yidplus.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import com.yidplus.app.ui.LookPrefs
import com.yidplus.app.ui.UiPrefs
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.yidplus.app.data.Perf
import com.yidplus.app.data.Room
import com.yidplus.app.data.Session
import com.yidplus.app.data.TgChannel
import com.yidplus.app.ui.AppRoot
import com.yidplus.app.ui.Keys
import com.yidplus.app.ui.Music
import com.yidplus.app.ui.Nav
import com.yidplus.app.ui.Screen
import com.yidplus.app.ui.Tab
import com.yidplus.app.ui.UpdateUi
import com.yidplus.app.ui.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Settings → Reduce motion (the site's comfort.js yp_reduced_motion): every Compose animation in the app reads this
 * scale when it starts, so 0 makes them all jump to the end, app-wide. Otherwise it follows the phone's own animation scale.
 */
object YidMotion : MotionDurationScale {
    @Volatile var system = 1f
    override val scaleFactor: Float get() = if (LookPrefs.reduceMotion) 0f else system
}

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_CHECK_UPDATE = "com.yidplus.app.CHECK_UPDATE"
        const val ACTION_OPEN_PLAYER = "com.yidplus.app.OPEN_PLAYER"
        const val ACTION_OPEN_CHAT = "com.yidplus.app.OPEN_CHAT"
        const val ACTION_OPEN_CHATS = "com.yidplus.app.OPEN_CHATS"
        const val ACTION_OPEN_NOTIFS = "com.yidplus.app.OPEN_NOTIFS"
        const val ACTION_OPEN_CHANNEL = "com.yidplus.app.OPEN_CHANNEL"
        const val ACTION_OPEN_TAB = "com.yidplus.app.OPEN_TAB"
        const val ACTION_INSTALL_UPDATE = "com.yidplus.app.INSTALL_UPDATE"
        private val DPAD = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER)

        /** yidplus.com links open the matching native screen; anything else on the site opens in the fallback page view. */
        fun route(ctx: Context, url: String) {
            val u = Uri.parse(url)
            val host = u.host?.lowercase() ?: ""
            if (host != "yidplus.com" && host != "www.yidplus.com") { com.yidplus.app.ui.openUrl(ctx, url); return }
            val path = (u.path ?: "/").trimEnd('/').lowercase()
            // The site's own link parameters: ?join=CODE (group invite), ?goto=explore|settings|chats, ?signin / ?invite.
            val join = u.getQueryParameter("join")?.takeIf { it.isNotBlank() }
            if (join != null) { Nav.go(Tab.Chats); Nav.open(com.yidplus.app.ui.ChJoinLink(join)); return }
            when (u.getQueryParameter("goto")?.lowercase()) {
                "explore", "channels", "home" -> { Nav.go(Tab.Home); return }
                "settings", "profile" -> { Nav.go(Tab.Profile); return }
                "chats", "chat" -> { Nav.go(Tab.Chats); return }
            }
            if (u.getQueryParameter("signin") != null || u.getQueryParameter("invite") != null) { Nav.go(Tab.Profile); return }
            when {
                path == "" || path == "/index" || path == "/index.html" -> Nav.go(Nav.start)
                path == "/chat" || path == "/yidplus-chat" || path == "/chats" -> Nav.go(Tab.Chats)
                path == "/shorts" || path == "/yidplus-shorts" -> { Nav.go(Tab.Shorts); (u.getQueryParameter("v") ?: u.fragment?.takeIf { it.isNotBlank() }?.let { "#$it" })?.let { Nav.shortsDeepLink = it } }
                path == "/music" || path == "/yidplus-music" -> Nav.go(Tab.Music)
                else -> Nav.open(Screen.Web(url))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val cfg = resources.configuration
        if (cfg.navigation == Configuration.NAVIGATION_DPAD || cfg.keyboard == Configuration.KEYBOARD_12KEY) Keys.keypad = true
        LookPrefs.init(this); UiPrefs.init(this)
        YidMotion.system = runCatching { android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
        // Our own recomposer (same as Compose's window recomposer) so Reduce motion reaches every animation.
        val recomposer = Recomposer(AndroidUiDispatcher.CurrentThread + YidMotion)
        lifecycleScope.launch(AndroidUiDispatcher.CurrentThread + YidMotion) { recomposer.runRecomposeAndApplyChanges() }
        setContentView(ComposeView(this).apply {
            setParentCompositionContext(recomposer)
            setContent { AppRoot(onExit = { finish() }) }
        })
        // On a keypad phone the very first arrow press only leaves "touch mode" (Android eats that press). Catch it
        // here so the screen's first item gets the focus, instead of whatever Android picks (the top bar's search icon).
        window.decorView.viewTreeObserver.addOnTouchModeChangeListener { inTouch -> if (!inTouch) Keys.keypad = true }
        if (savedInstanceState == null) handle(intent)
        lifecycleScope.launch {
            delay(1200)
            if (intent?.action != ACTION_INSTALL_UPDATE) UpdateUi.check(this@MainActivity, force = intent?.action == ACTION_CHECK_UPDATE, userAsked = intent?.action == ACTION_CHECK_UPDATE)
            Music.connect(this@MainActivity)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun qaLook(i: Intent) {
        i.getStringExtra("color")?.toIntOrNull()?.let { LookPrefs.putColor(it) }
        i.getStringExtra("text")?.toIntOrNull()?.let { UiPrefs.setText(it) }
        i.getStringExtra("contrast")?.let { LookPrefs.putHighContrast(it == "1") }
        i.getStringExtra("motion")?.let { LookPrefs.putReduceMotion(it == "1") }
        i.getStringExtra("wallpaper")?.let { LookPrefs.putWallpaper(it) }
        i.getStringExtra("bar")?.let { LookPrefs.putLightBar(it == "light") }
        i.getStringExtra("dark")?.toIntOrNull()?.let { com.yidplus.app.data.Prefs.setThemeMode(it) }
        if (i.getStringExtra("demo") != null) Nav.open(com.yidplus.app.ui.DsDemoPage())
        i.getStringExtra("snack")?.let { com.yidplus.app.ui.Snack.show(it, "Undo") { toast(this, "Undone") } }
        android.util.Log.i("YidLook", "color=${LookPrefs.colorTheme} text=${UiPrefs.textSize} contrast=${LookPrefs.highContrast} reduceMotion=${LookPrefs.reduceMotion} motionScale=${YidMotion.scaleFactor} lightBar=${LookPrefs.lightBar}")
    }

    private fun handle(i: Intent?) {
        i ?: return
        when (i.action) {
            ACTION_CHECK_UPDATE -> UpdateUi.check(this, force = true, userAsked = true)
            ACTION_OPEN_PLAYER -> Nav.open(Screen.Player)
            ACTION_OPEN_CHATS -> Nav.go(Tab.Chats)
            "com.yidplus.app.QA_PUSH_PROBE" -> Push.probe(this)
            // QA (helper DS): set a look setting exactly as Settings does, to prove it changes the screens.
            "com.yidplus.app.QA_LOOK" -> qaLook(i)
            "com.yidplus.app.QA_FEATURES" -> com.yidplus.app.data.SiteConfig.qaShowAll = i.getBooleanExtra("all", false)
            "com.yidplus.app.QA_UPDATE_NOTIFY" -> UpdateWorker.now(this)
            "com.yidplus.app.QA_FIXTURE" -> { com.yidplus.app.data.Fixture.set(this, i.getBooleanExtra("on", true)); com.yidplus.app.data.Store.clear(); lifecycleScope.launch { Session.refresh() } }
            "com.yidplus.app.QA_OPEN" -> i.getStringExtra("page")?.let { p -> runCatching { Class.forName("com.yidplus.app.ui.SavedHistoryKt").getMethod("qaOpenNew", String::class.java).invoke(null, p) } }
            "com.yidplus.app.QA_DEDUPE" -> lifecycleScope.launch { com.yidplus.app.ui.qaDedupe(applicationContext) }
            ACTION_INSTALL_UPDATE -> UpdateUi.installNow(this)
            ACTION_OPEN_TAB -> runCatching { Tab.valueOf(i.getStringExtra("tab") ?: "Home") }.getOrNull()?.let { Nav.go(it) }
            ACTION_OPEN_NOTIFS -> { Nav.go(Tab.Home); Nav.open(Screen.Notifications) }
            ACTION_OPEN_CHAT -> i.getStringExtra("room")?.let { runCatching { Room.from(JSONObject(it)) }.getOrNull() }?.let { Nav.go(Tab.Chats); Nav.open(Screen.Chat(it)) }
            ACTION_OPEN_CHANNEL -> i.getStringExtra("username")?.let { u ->
                Nav.go(Tab.Home); Nav.open(Screen.Channel(TgChannel(u, i.getStringExtra("title") ?: u, null, 0, "", "", null, 0, true)))
            }
            Intent.ACTION_VIEW -> {
                val d = i.data ?: return
                if (d.scheme == "yidplus" && d.host == "auth") {
                    lifecycleScope.launch { if (GoogleAuth.finish(this@MainActivity, d)) { toast(this@MainActivity, "Signed in"); NotifyWorker.schedule(this@MainActivity) } else toast(this@MainActivity, getString(R.string.google_failed)) }
                } else if (d.scheme == "https") route(this, d.toString())
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode in DPAD && !Keys.keypad && Nav.tab == Tab.Shorts && Nav.stack.isEmpty()) Keys.keypad = true
        if (event.keyCode in DPAD && !Keys.keypad) {
            // First D-pad press: show the keypad focus on the screen's first item (that press only wakes the focus,
            // so it never jumps to the top bar first).
            if (event.action == KeyEvent.ACTION_DOWN) Keys.keypad = true
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        UpdateUi.onResume(this)
        if (Session.me != null) lifecycleScope.launch { Session.refresh() }
        lifecycleScope.launch { com.yidplus.app.data.SiteConfig.refresh() }
    }

    override fun onStop() {
        super.onStop()
        if (Session.me != null) NotifyWorker.schedule(this)
    }
}
