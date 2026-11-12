package com.yidplus.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.yidplus.app.data.Session

/* 2.1 ST: the Settings home rows in WhatsApp's order and grouping (Settings → Account, Privacy, Chats, Notifications, …).
   Kept as small composables (a single giant one can fail verification on API 30 after R8). */

@Composable
fun StMainRows() {
    SettingsCard {
        val devs = SettingsProfile.res.data?.optJSONArray("sessions")?.length() ?: 0
        SettingRow(Sym.Key, "Account", "Security, two-step verification, " + (if (devs > 0) "$devs devices" else "devices")) { Nav.open(SettingsSecurity()) }
        RowDivider(64.dp)
        SettingRow(Sym.Lock, "Privacy", "Blocked users, app lock, who can message you") { Nav.open(SettingsPrivacy()) }
        RowDivider(64.dp)
        SettingRow(Sym.Chat, "Chats", "Wallpaper, muted chats, silent calls") { Nav.open(SettingsChat()) }
        RowDivider(64.dp)
        SettingRow(Sym.Notifications, "Notifications", "Messages, likes, followers, channel updates") { Nav.open(SettingsNotifPrefs()) }
        RowDivider(64.dp)
        SettingRow(Sym.Bookmark, "Saved items", "Shorts and songs you saved") { Nav.open(SavedItemsPage()) }
        RowDivider(64.dp)
        SettingRow(Sym.History, "History", "What you watched and played") { Nav.open(HistoryPage()) }
    }
}

@Composable
fun StPeopleRows() {
    SettingsCard {
        SettingRow(Sym.Visibility, "My profile page", "See your profile the way others see it") { stOpenUser(Session.me?.id ?: "demo-me", Session.me?.nickname ?: "", Session.me?.photo) }
        RowDivider(64.dp)
        SettingRow(Sym.Group, "Followers and following", "See who follows you, remove a follower") { Nav.open(StFollowListPage(Session.me?.id ?: "demo-me", Session.me?.nickname ?: "", true)) }
        RowDivider(64.dp)
        SettingRow(Sym.NotificationsActive, "Activity", "Likes, comments, follows") { Nav.open(SoNotifsPage()) }
    }
}
