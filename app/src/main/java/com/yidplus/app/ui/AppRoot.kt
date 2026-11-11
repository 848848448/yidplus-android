package com.yidplus.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.SmartDisplay
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
import com.yidplus.app.data.Net
import com.yidplus.app.data.Session

@Composable
fun AppRoot(onExit: () -> Unit) {
    YidTheme {
        val cfg = LocalConfiguration.current
        val wide = cfg.screenWidthDp >= 600
        val twoPane = cfg.screenWidthDp >= 840 || (cfg.screenWidthDp >= 600 && cfg.screenWidthDp > cfg.screenHeightDp)
        Nav.wide = twoPane
        LaunchedEffect(Unit) { Session.refresh() }
        BackHandler { if (!Nav.back()) onExit() }
        val top = Nav.stack.lastOrNull()
        val immersive = top is Screen.Video || top is Screen.Photo || top is Screen.Player || (top == null && Nav.tab == Tab.Shorts)
        val holder = rememberSaveableStateHolder()

        Box(Modifier.fillMaxSize().background(if (immersive) Color.Black else MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxSize()) {
                if (wide && top !is Screen.Video && top !is Screen.Photo) NavRail()
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (!Net.online) OfflineStrip()
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        holder.SaveableStateProvider(Nav.tab.name) {
                            when (Nav.tab) {
                                Tab.Home -> if (twoPane) TwoPane({ HomeScreen() }, { Nav.homeDetail?.let { key(it.ch.username) { ChannelScreen(it.ch, inPane = true) } } ?: EmptyPane("Pick a channel") }) else HomeScreen()
                                Tab.Chats -> if (twoPane) TwoPane({ ChatsScreen() }, { Nav.chatDetail?.let { key(it.room.id) { ChatScreen(it.room, inPane = true) } } ?: EmptyPane("Pick a chat") }) else ChatsScreen()
                                Tab.Shorts -> if (top == null) ShortsScreen() else Box(Modifier.fillMaxSize())
                                Tab.Music -> MusicScreen()
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
                                    is Screen.Web -> WebScreen(top.url)
                                    Screen.Player -> PlayerScreen()
                                    Screen.Notifications -> NotificationsScreen()
                                }
                            }
                        }
                    }
                    if (top !is Screen.Player && top !is Screen.Video && top !is Screen.Photo) MiniPlayer()
                    if (!wide && top !is Screen.Chat && top !is Screen.Video && top !is Screen.Photo && top !is Screen.Player) BottomBar(dark = immersive)
                }
            }
            UpdateDialogs()
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

@Composable
private fun OfflineStrip() {
    Box(Modifier.fillMaxWidth().background(Color(0xFF8A3B12)).padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
        Text("Offline · showing saved copies", color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

private val tabIcons: Map<Tab, ImageVector> = mapOf(
    Tab.Home to Icons.Filled.Home, Tab.Chats to Icons.AutoMirrored.Filled.Chat, Tab.Shorts to Icons.Filled.SmartDisplay,
    Tab.Music to Icons.Filled.LibraryMusic, Tab.Profile to Icons.Filled.AccountCircle,
)

@Composable
private fun BottomBar(dark: Boolean) {
    val bg = if (dark) Color(0xFF0E1715) else MaterialTheme.colorScheme.surface
    Surface(color = bg, tonalElevation = 3.dp) {
        Column {
            if (!dark) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .3f))
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                for (t in Tab.values()) TabItem(t, dark, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TabItem(t: Tab, dark: Boolean, modifier: Modifier) {
    val sel = Nav.tab == t && Nav.stack.isEmpty()
    val on = if (dark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val active = if (dark) Gold else MaterialTheme.colorScheme.primary
    Column(modifier.padding(2.dp).tap(RoundedCornerShape(12.dp)) { if (Nav.tab == t) Nav.stack.clear() else Nav.go(t) }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Icon(tabIcons[t]!!, t.label, tint = if (sel) active else on, modifier = Modifier.size(24.dp))
            val n = when (t) { Tab.Chats -> Badges.chats; Tab.Home -> Badges.channels; else -> 0 }
            if (n > 0) Box(Modifier.align(Alignment.TopEnd).padding(start = 14.dp)) { Badge(n) }
        }
        Text(t.label, style = MaterialTheme.typography.labelSmall, color = if (sel) active else on, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NavRail() {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxHeight().width(84.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Y+", fontWeight = FontWeight.Black, color = Gold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 10.dp))
            for (t in Tab.values()) TabItem(t, false, Modifier.width(76.dp))
        }
    }
}
