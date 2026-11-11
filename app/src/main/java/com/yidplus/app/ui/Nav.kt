package com.yidplus.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yidplus.app.data.Perf
import com.yidplus.app.data.Room
import com.yidplus.app.data.TgChannel

enum class Tab(val label: String) { Home("Channels"), Chats("Chats"), Shorts("Shorts"), Music("Music"), Profile("Profile") }

sealed class Screen {
    data class Channel(val ch: TgChannel) : Screen()
    data class Chat(val room: Room) : Screen()
    data class Video(val url: String, val title: String) : Screen()
    data class Photo(val url: String) : Screen()
    data class Web(val url: String) : Screen()
    object Player : Screen()
    object Notifications : Screen()
}

/** The app's own navigation: 5 tabs, each with its own stack; Back goes back inside the app. */
object Nav {
    var tab by mutableStateOf(Tab.Home)
    val stack = mutableStateListOf<Screen>()
    /** On tablets the opened channel / chat shows beside its list instead of over it. */
    var homeDetail by mutableStateOf<Screen.Channel?>(null)
    var chatDetail by mutableStateOf<Screen.Chat?>(null)
    var wide = false
    var shortsDeepLink by mutableStateOf<String?>(null)

    fun go(t: Tab) { stack.clear(); tab = t; Perf.start(t.name) }

    fun open(s: Screen) {
        Perf.start(s.javaClass.simpleName)
        if (wide && s is Screen.Channel && tab == Tab.Home && stack.isEmpty()) { homeDetail = s; return }
        if (wide && s is Screen.Chat && tab == Tab.Chats && stack.isEmpty()) { chatDetail = s; return }
        stack.add(s)
    }

    /** true when it went back; false when the app is at its start (Back then leaves the app). */
    fun back(): Boolean {
        if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); return true }
        if (tab == Tab.Home && homeDetail != null) { homeDetail = null; return true }
        if (tab == Tab.Chats && chatDetail != null) { chatDetail = null; return true }
        if (tab != Tab.Home) { tab = Tab.Home; return true }
        return false
    }
}

object Badges {
    var chats by mutableStateOf(0)
    var channels by mutableStateOf(0)
}
