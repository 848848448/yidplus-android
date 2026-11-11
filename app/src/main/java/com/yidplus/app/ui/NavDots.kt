package com.yidplus.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yidplus.app.data.Api
import com.yidplus.app.data.Net
import com.yidplus.app.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The bottom bar's badges, exactly like the site (chat.js _refreshChatNavBadge / _updateContentBadges):
 * Chats = the sum of unread over the rooms that aren't muted (from the same cached /api/chat/rooms copy the chat list uses,
 * so it costs nothing extra), and a dot on Shorts / Channels when /api/shorts?limit=1 or /api/posts?limit=1 has something
 * newer than your last visit to that tab. Checked when the app opens and every minute while it is in front.
 */
object NavDots {
    var shorts by mutableStateOf(false); private set
    var channels by mutableStateOf(false); private set
    private fun prefs() = Api.app.getSharedPreferences("navdots", 0)
    private fun now(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    /** You opened the tab: like the site's yp_last_shorts_visit / yp_last_feed_visit. */
    fun seen(t: Tab) {
        when (t) {
            Tab.Shorts -> { shorts = false; runCatching { prefs().edit().putString("shorts", now()).apply() } }
            Tab.Home -> { channels = false; runCatching { prefs().edit().putString("feed", now()).apply() } }
            else -> {}
        }
    }

    suspend fun check() {
        val lastS = runCatching { prefs().getString("shorts", null) }.getOrNull() ?: "1970-01-01"
        val lastF = runCatching { prefs().getString("feed", null) }.getOrNull() ?: "1970-01-01"
        val s = withContext(Dispatchers.IO) { runCatching { Api.get("/api/shorts?limit=1").optJSONArray("shorts")?.optJSONObject(0)?.optString("created_at") }.getOrNull() }
        val f = withContext(Dispatchers.IO) { runCatching { Api.get("/api/posts?limit=1").optJSONArray("posts")?.optJSONObject(0)?.optString("created_at") }.getOrNull() }
        shorts = !s.isNullOrBlank() && s > lastS && !(Nav.tab == Tab.Shorts && Nav.stack.isEmpty())
        channels = !f.isNullOrBlank() && f > lastF && !(Nav.tab == Tab.Home && Nav.stack.isEmpty())
    }
}

/** Keeps the bottom bar's numbers and dots fresh (put once in AppRoot). Signed out: no chat number. */
@Composable
fun NavBadgesFeed() {
    val me = Session.me
    LaunchedEffect(Nav.tab) { NavDots.seen(Nav.tab) }
    LaunchedEffect(me?.id, Net.online) {
        while (true) {
            if (me != null) runCatching { ChatApi.rooms.load() }
            runCatching { NavDots.check() }
            delay(60_000)
        }
    }
    val rooms = ChatApi.rooms.data
    LaunchedEffect(rooms, me?.id) {
        Badges.chats = if (me == null) 0 else rooms?.filter { !it.muted }?.sumOf { it.room.unread } ?: Badges.chats
    }
}
