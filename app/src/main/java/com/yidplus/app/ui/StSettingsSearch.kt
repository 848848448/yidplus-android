package com.yidplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yidplus.app.data.Api
import com.yidplus.app.data.Session

/* 2.1 ST: "Search settings", like WhatsApp's Settings search: type a word, see every setting that matches, tap to open it. */

private class StHit(val icon: ImageVector, val title: String, val where: String, val words: String, val open: () -> Unit)

private fun stIndex(): List<StHit> {
    val me = Session.me
    val l = mutableListOf(
        StHit(Sym.Edit, "Profile", "Account", "name photo picture cover banner bio about location website birthday edit", { Nav.open(SettingsEditProfile()) }),
        StHit(Sym.Group, "Followers and following", "Account", "followers following remove follower", { Nav.open(StFollowListPage(me?.id ?: "demo-me", me?.nickname ?: "", true)) }),
        StHit(Sym.Lock, "Privacy", "Account", "private profile who can message last seen online read receipts", { Nav.open(SettingsPrivacy()) }),
        StHit(Sym.Security, "Security", "Account", "password change two-step 2fa verification sign-in devices sessions recent logins", { Nav.open(SettingsSecurity()) }),
        StHit(Sym.Password, "Change password", "Security", "password change reset", { Nav.open(SettingsSecurity()) }),
        StHit(Sym.Devices, "Devices", "Security", "devices sessions sign out everywhere logged in phones", { Nav.open(SettingsSecurity()) }),
        StHit(Sym.Chat, "Chats", "Account", "chat wallpaper enter send media auto download font", { Nav.open(SettingsChat()) }),
        StHit(Sym.Notifications, "Notifications", "Account", "notifications sounds alerts likes followers messages channels", { Nav.open(SettingsNotifPrefs()) }),
        StHit(Sym.Block, "Blocked users", "Privacy", "blocked block unblock", { Nav.open(SettingsBlocked()) }),
        StHit(Sym.Pin, "App lock", "Privacy", "pin lock code fingerprint secure", { Nav.open(StAppLockPage()) }),
        StHit(Sym.Bookmark, "Saved items", "Account", "saved bookmarks shorts songs", { Nav.open(SavedItemsPage()) }),
        StHit(Sym.History, "History", "Account", "history watched played recently", { Nav.open(HistoryPage()) }),
        StHit(Sym.SupportAgent, "Help & Support", "Help", "help support contact team problem question forgot password verification", { Nav.open(StSupportPage()) }),
        StHit(Sym.Feedback, "Send feedback", "Help", "feedback idea bug report", { Nav.open(SettingsFeedback()) }),
        StHit(Sym.Download, "Download my data", "Help", "download export my data copy backup", { Nav.open(SettingsAbout()) }),
        StHit(Sym.PersonAdd, "Invite a friend", "Help", "invite share friend", { Nav.go(Tab.Profile) }),
        StHit(Sym.Info, "About YID PLUS", "Help", "about version update terms privacy policy", { Nav.open(SettingsAbout()) }),
        StHit(Sym.Policy, "Privacy policy", "About", "privacy policy", { Nav.open(Screen.Web(Api.BASE + "/privacy.html")) }),
        StHit(Sym.Description, "Terms of service", "About", "terms of service use rules", { Nav.open(Screen.Web(Api.BASE + "/terms.html")) }),
        StHit(Sym.DarkMode, "Dark mode", "Appearance", "dark light theme night", { Nav.go(Tab.Profile) }),
        StHit(Sym.FormatSize, "Text size", "Appearance", "text size font big small large", { Nav.go(Tab.Profile) }),
        StHit(Sym.Palette, "Color theme", "Appearance", "color colour theme accent ocean forest crimson violet amber midnight", { Nav.go(Tab.Profile) }),
        StHit(Sym.Logout, "Sign out", "Account", "sign out log out logout", { Nav.go(Tab.Profile) }),
    )
    return l
}

class StSettingsSearchPage : PageScreen() {
    override val perfName: String get() = "SettingsSearch"
    @Composable override fun Content() {
        var q by rememberSaveable { mutableStateOf("") }
        val all = stIndex()
        val words = q.trim().lowercase().split(" ").filter { it.isNotBlank() }
        val hits = if (words.isEmpty()) all else all.filter { h -> val t = (h.title + " " + h.where + " " + h.words).lowercase(); words.all { t.contains(it) } }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Sym.ArrowBack, "Back", { Nav.back() })
                    Column(Modifier.weight(1f)) {
                        if (q.isEmpty()) Text("Search settings…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        BasicTextField(q, { q = it }, Modifier.fillMaxWidth().firstFocus(always = true), singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary))
                    }
                    if (q.isNotEmpty()) IconBtn(Sym.Close, "Clear", { q = "" })
                }
            }
            if (hits.isEmpty()) EmptyNote(Sym.Search, "No settings found", "Try another word, like \"password\" or \"dark\".")
            else LazyColumn(Modifier.fillMaxSize()) {
                items(hits, key = { it.title }) { h ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).tap(RoundedCornerShape(0.dp)) { Nav.back(); h.open() }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(h.icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.padding(start = 24.dp).weight(1f)) {
                            Text(h.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(h.where, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    RowDivider(64.dp)
                }
            }
        }
    }
}
