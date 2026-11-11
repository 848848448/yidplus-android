package com.yidplus.app.ui

import androidx.compose.foundation.layout.isImeVisible

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.yidplus.app.data.Net
import com.yidplus.app.data.Session
import com.yidplus.app.data.SiteConfig

@Composable
fun AppRoot(onExit: () -> Unit) {
    YidTheme {
        val cfg = LocalConfiguration.current
        val wide = cfg.screenWidthDp >= 600
        val twoPane = cfg.screenWidthDp >= 840 || (cfg.screenWidthDp >= 600 && cfg.screenWidthDp > cfg.screenHeightDp)
        Nav.wide = twoPane
        LaunchedEffect(Unit) { Session.refresh() }
        // A feature the owner switched off on the site disappears here too (and its screen closes).
        LaunchedEffect(SiteConfig.s) { if (!SiteConfig.visible(Nav.tab)) Nav.go(Nav.start) }
        BackHandler { if (!Nav.back()) onExit() }
        val top = Nav.stack.lastOrNull()
        val immersive = top is Screen.Video || top is Screen.Photo || top is Screen.Gallery || (top is PageScreen && top.immersive) || top is Screen.Player || (top == null && Nav.tab == Tab.Shorts)
        val holder = rememberSaveableStateHolder()
        NavBadgesFeed()
        SoQaPickReceiver()

        // The status bar takes the colour of the brand bar under it (black on full-screen video), with white icons.
        val view = androidx.compose.ui.platform.LocalView.current
        val dark = isDark()
        val barTop = if (immersive) Color.Black else if (top == null && Nav.tab == Tab.Shorts) Color.Black else topBarColor()
        androidx.compose.runtime.SideEffect {
            (view.context as? android.app.Activity)?.window?.let { w ->
                val c = androidx.core.view.WindowCompat.getInsetsController(w, view)
                c.isAppearanceLightStatusBars = false
                c.isAppearanceLightNavigationBars = !dark && !immersive
            }
        }
        Box(Modifier.fillMaxSize().background(if (immersive) Color.Black else MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()).background(barTop))
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxSize()) {
                if (wide && top !is Screen.Video && top !is Screen.Photo && top !is Screen.Gallery) NavRail()
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (!Net.online) OfflineStrip()
                    if (SiteConfig.maintenance) Notice(SiteConfig.maintenanceMessage)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        holder.SaveableStateProvider(Nav.tab.name) {
                            if (SiteConfig.guestOff && Session.me == null && Nav.tab != Tab.Profile) GuestGate() else
                            when (Nav.tab) {
                                Tab.Home -> if (twoPane) TwoPane({ HomeScreen() }, { Nav.homeDetail?.let { key(it.ch.username) { ChannelScreen(it.ch, inPane = true) } } ?: EmptyPane("Pick a channel") }) else HomeScreen()
                                Tab.Chats -> if (twoPane) TwoPane({ ChatsScreen() }, { Nav.chatDetail?.let { key(it.room.id) { ChatScreen(it.room, inPane = true) } } ?: EmptyPane("Pick a chat") }) else ChatsScreen()
                                Tab.Shorts -> if (top == null) ShortsScreen() else Box(Modifier.fillMaxSize())
                                Tab.Music -> if (SiteConfig.visible(Tab.Music)) MusicScreen() else Box(Modifier.fillMaxSize())
                                Tab.Profile -> ProfileScreen()
                            }
                        }
                        if (top != null) Box(Modifier.fillMaxSize().background(if (immersive) Color.Black else MaterialTheme.colorScheme.background)) {
                            androidx.compose.runtime.key(Nav.stack.size, top) {
                                when (top) {
                                    is Screen.Channel -> ChannelScreen(top.ch)
                                    is Screen.Chat -> ChatScreen(top.room)
                                    is Screen.Video -> VideoScreen(top.url, top.title)
                                    is Screen.Photo -> PhotoScreen(top.url)
                                    is Screen.Gallery -> GalleryScreen(top.items, top.start)
                                    is Screen.Web -> WebScreen(top.url)
                                    Screen.Player -> PlayerScreen()
                                    Screen.Notifications -> NotificationsScreen()
                                    is PageScreen -> top.Content()
                                }
                            }
                        }
                    }
                    if (top !is Screen.Player && top !is Screen.Video && top !is Screen.Photo && top !is Screen.Gallery && SiteConfig.visible(Tab.Music)) MiniPlayer()
                    // Like WhatsApp: inside a chat, a channel, a photo/video or any detail screen the bar hides; Back brings it back.
                    // Hidden while the keyboard is up (search, typing), like WhatsApp, so the screen keeps its room.
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    val imeUp = WindowInsets.isImeVisible
                    if (!wide && top == null && !imeUp) BottomBar(dark = immersive)
                }
            }
            UpdateDialogs()
        }
        }
    }
}

@Composable
private fun TwoPane(list: @Composable () -> Unit, detail: @Composable () -> Unit) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(360.dp).fillMaxHeight()) { list() }
        VerticalDivider()
        Box(Modifier.weight(1f).fillMaxHeight()) { detail() }
    }
}

@Composable
private fun EmptyPane(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Logo(); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** The owner turned guest browsing off on the site: signed-out people are asked to sign in, like on the website. */
@Composable
private fun GuestGate() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Logo()
            Text("Sign in to see chats, Shorts and channels", Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryButton("Sign in", { Nav.go(Tab.Profile) }, Modifier.firstFocus())
        }
    }
}

@Composable
private fun Notice(text: String) {
    Box(Modifier.fillMaxWidth().background(Color(0xFF1F6F5C)).padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 2)
    }
}

@Composable
private fun OfflineStrip() {
    Box(Modifier.fillMaxWidth().background(Color(0xFF8A3B12)).padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
        Text("Offline · showing saved copies", color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

private val tabIcons: Map<Tab, ImageVector> = mapOf(
    // The website's own icons for the same items (forum, music_note, movie, explore, settings).
    Tab.Home to Icons.Filled.Explore, Tab.Chats to Icons.Filled.Forum, Tab.Shorts to Icons.Filled.Movie,
    Tab.Music to Icons.Filled.MusicNote, Tab.Profile to Icons.Filled.Settings,
)

@Composable
private fun BottomBar(dark: Boolean) {
    val bg = if (dark) Color(0xFF0E1715) else if (isDark()) Color(0xFF141C19) else MaterialTheme.colorScheme.surface
    Surface(color = bg, shadowElevation = 8.dp) {
        Column {
            HorizontalDivider(thickness = 0.5.dp, color = if (dark) Color.White.copy(alpha = .08f) else MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                for (t in SiteConfig.tabs) TabItem(t, dark, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TabItem(t: Tab, dark: Boolean, modifier: Modifier) {
    val sel = Nav.tab == t && Nav.stack.isEmpty()
    val on = if (dark) Color.White.copy(alpha = .78f) else MaterialTheme.colorScheme.onSurfaceVariant
    val active = if (dark) Color.White else if (isDark()) MaterialTheme.colorScheme.primary else Green
    val pill = if (dark) Color.White.copy(alpha = .16f) else MaterialTheme.colorScheme.primaryContainer
    // Material 3 / WhatsApp navigation item: 24 dp icon in a pill when selected, 12 sp label under it, and the site's
    // badges: the unread number on Chats (and on Channels for joined Telegram channels), a dot on Shorts / Channels.
    val n = when (t) { Tab.Chats -> Badges.chats; Tab.Home -> Badges.channels; else -> 0 }
    val dot = n <= 0 && when (t) { Tab.Shorts -> NavDots.shorts; Tab.Home -> NavDots.channels; else -> false }
    Column(modifier.padding(horizontal = 2.dp, vertical = 2.dp).tap(RoundedCornerShape(14.dp)) { if (Nav.tab == t) Nav.stack.clear() else Nav.go(t) }
        .semantics { contentDescription = t.label + if (n > 0) ", $n unread" else if (dot) ", new" else "" }.padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 64.dp, height = 32.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 58.dp, height = 30.dp).clip(RoundedCornerShape(15.dp)).background(if (sel) pill else Color.Transparent))
            Icon(tabIcons[t]!!, null, tint = if (sel) active else on, modifier = Modifier.size(24.dp))
            if (n > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = (-3).dp)) { Badge(n) }
            else if (dot) Box(Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = 2.dp).size(9.dp).clip(CircleShape).background(BadgeGreen))
        }
        Text(t.label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = if (sel) active else on,
            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun NavRail() {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxHeight().width(84.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Y+", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 10.dp))
            for (t in SiteConfig.tabs) TabItem(t, false, Modifier.width(76.dp))
        }
    }
}
