package com.yidplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yidplus.app.data.*
import kotlinx.coroutines.launch

/**
 * Shorts > Following > "Follow more": EVERY creator of the Status videos (the same directory the Channels "Video channels" list uses,
 * /api/src-shorts?creators), searchable, each with its own Follow button. Not limited to a few suggestions, and open even when you already follow some.
 * Follows are the same Feeds.follows the Following tab and the creator pages use.
 */
data class SoFollowPeoplePage(val unused: Int = 0) : PageScreen() {
    override val perfName = "FollowPeople"
    @Composable override fun Content() {
        val c = ctx()
        val scope = rememberCoroutineScope()
        val res = remember(Session.me?.id) { Store.res("/api/src-shorts?creators", ::soVideoChannels) }
        val rec = remember(Session.me?.id) { Store.res("/api/src-shorts?n=60", ::soVideoRecent) }
        LaunchedEffect(res, rec, Net.online) { Feeds.loadPrefs(c); res.load(force = Net.online && res.offline); rec.load(); Perf.shown("FollowPeople") }
        var q by remember { mutableStateOf("") }
        var searching by remember { mutableStateOf(false) }
        val kb = LocalSoftwareKeyboardController.current
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // The title bar stays; Search swaps it for a one-row search bar (like Channels) so the results keep the room above the keyboard on a small screen.
            if (searching) Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Sym.ArrowBack, "Close search", { kb?.hide(); searching = false; q = "" })
                    TextField(q, { q = it.take(80) }, Modifier.weight(1f).firstFocus(always = true), singleLine = true,
                        placeholder = { Text("Search people") }, textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { kb?.hide() }),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                    if (q.isNotEmpty()) IconBtn(Sym.Close, "Clear", { q = "" })
                }
            } else TopBar("Follow people", onBack = { Nav.back() }) { IconBtn(Sym.Search, "Search people", { searching = true }) }
            val all = res.data
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            when {
                all == null && res.error != null -> Problem(res.error!!, res.offline, { scope.launch { res.load(true) } })
                all == null -> SoSkeleton()
                else -> {
                    val recent = rec.data.orEmpty()
                    val sorted = remember(all, recent) { all.sortedWith(compareByDescending<SoVideoCh> { recent[it.key] ?: "" }.thenBy { it.name.lowercase() }) }
                    val t = q.trim()
                    val list = if (t.isEmpty()) sorted else sorted.filter { it.name.contains(t, true) }
                    if (list.isEmpty()) SoEmpty(Sym.Search, "No people for \u201c$t\u201d")
                    else LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            val n = all.count { it.key in Feeds.follows }
                            Text("${list.size} people" + (if (n > 0) " \u00b7 following $n" else "") + (if (res.fromCache) " \u00b7 saved copy" else ""),
                                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(list, key = { it.key }) { ch ->
                            val following = ch.key in Feeds.follows
                            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.weight(1f).tap(RoundedCornerShape(8.dp)) { Nav.open(SoCreatorPage(ch.key, ch.name, ch.avatar, ch.verified, true)) }, verticalAlignment = Alignment.CenterVertically) {
                                    SoThumb(ch.avatar, ch.name, 46.dp)
                                    Row(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(ch.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content, textAlign = TextAlign.Left),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (ch.verified) Icon(Sym.Verified, "Verified", tint = Color(0xFF1D9BF0), modifier = Modifier.padding(start = 4.dp).size(16.dp))
                                    }
                                }
                                SoPill(if (following) "Following \u2713" else "Follow", !following, {
                                    if (following) Feeds.follows.remove(ch.key) else Feeds.follows.add(ch.key)
                                    Feeds.persist(c); toast(c, if (following) "Unfollowed ${ch.name}" else "Following ${ch.name}")
                                })
                            }
                            SoDivider(74.dp)
                        }
                    }
                }
            }
        }
    }
}
