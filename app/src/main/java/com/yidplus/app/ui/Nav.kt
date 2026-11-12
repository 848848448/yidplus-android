package com.yidplus.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yidplus.app.data.Perf
import com.yidplus.app.data.Room
import com.yidplus.app.data.TgChannel

/** The website's own bottom-bar items and labels (Home = the site's "Channels" tab, Profile = its "Settings" tab). */
enum class Tab(val label: String) { Home("Channels"), Chats("Chats"), Shorts("Shorts"), Music("Music"), Profile("Settings") }

sealed class Screen {
    data class Channel(val ch: TgChannel) : Screen()
    data class Chat(val room: Room) : Screen()
    data class Video(val url: String, val title: String) : Screen()
    data class Photo(val url: String) : Screen()
    /** Full-screen photos and videos of one chat or channel: swipe left/right for the next, pinch to zoom, drag down to close. */
    data class Gallery(val items: List<GItem>, val start: Int) : Screen()
    data class Web(val url: String) : Screen()
    object Player : Screen()
    object Notifications : Screen()
}

/**
 * A screen that draws itself. New screens subclass this in their own file (no edit to Nav.kt or AppRoot.kt needed):
 * `data class GroupInfo(val room: Room) : PageScreen() { override val perfName = "GroupInfo"; @Composable override fun Content() { ... } }`
 * Opened with Nav.open(GroupInfo(room)); the bottom bar hides while it is open, Back closes it.
 */
abstract class PageScreen : Screen() {
    open val perfName: String get() = this::class.simpleName ?: "Page"
    /** true = black, full screen, no bars (like the photo viewer). */
    open val immersive: Boolean get() = false
    @androidx.compose.runtime.Composable abstract fun Content()
}

/** The app's own navigation: 5 tabs, each with its own stack; Back goes back inside the app. */
object Nav {
    var tab by mutableStateOf(runCatching { Tab.valueOf(com.yidplus.app.data.Prefs.lastTab) }.getOrNull()?.takeIf { com.yidplus.app.data.SiteConfig.visible(it) }
        ?: com.yidplus.app.data.SiteConfig.tabs.firstOrNull() ?: Tab.Chats)
    val start: Tab get() = com.yidplus.app.data.SiteConfig.tabs.firstOrNull() ?: Tab.Chats
    val stack = mutableStateListOf<Screen>()
    /** On tablets the opened channel / chat shows beside its list instead of over it. */
    var homeDetail by mutableStateOf<Screen.Channel?>(null)
    var chatDetail by mutableStateOf<Screen.Chat?>(null)
    var wide = false
    var shortsDeepLink by mutableStateOf<String?>(null)
    /** The list row that opened the screen on top, so Back puts the keypad focus back on it. */
    var lastOpened by mutableStateOf<String?>(null)
    /**
     * Goes up by one each time the user taps the tab that is already open (with nothing on top). Like WhatsApp, a
     * screen's list scrolls back to the top: `val r = Nav.reselect[Tab.Chats] ?: 0; LaunchedEffect(r) { if (r > 0) listState.animateScrollToItem(0) }`.
     */
    val reselect = androidx.compose.runtime.mutableStateMapOf<Tab, Int>()

    fun go(t: Tab) {
        val ok = if (com.yidplus.app.data.SiteConfig.visible(t)) t else start
        stack.clear(); tab = ok; Perf.start(ok.name); com.yidplus.app.data.Prefs.lastTab = ok.name
    }

    fun open(s: Screen) {
        Perf.start(when (s) { is Screen.Channel -> "Channel"; is Screen.Chat -> "Chat"; is Screen.Video -> "Video"; is Screen.Photo -> "Photo"; is Screen.Gallery -> "Photo"; is Screen.Web -> "Web"; Screen.Player -> "Player"; Screen.Notifications -> "Notifications"; is PageScreen -> s.perfName })
        if (wide && s is Screen.Channel && tab == Tab.Home && stack.isEmpty()) { homeDetail = s; return }
        if (wide && s is Screen.Chat && tab == Tab.Chats && stack.isEmpty()) { chatDetail = s; return }
        stack.add(s)
    }

    /** true when it went back; false when the app is at its start (Back then leaves the app). */
    fun back(): Boolean {
        if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); return true }
        if (tab == Tab.Home && homeDetail != null) { homeDetail = null; return true }
        if (tab == Tab.Chats && chatDetail != null) { chatDetail = null; return true }
        if (tab != start) { tab = start; com.yidplus.app.data.Prefs.lastTab = start.name; return true }
        return false
    }
}

object Badges {
    var chats by mutableStateOf(0)
    var channels by mutableStateOf(0)
}
