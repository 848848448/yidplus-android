package com.yidplus.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yidplus.app.data.Api
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

// ═════════════════════════════════════════════════════════════════════════════
// AD 2.1 — Overview category (admin.js: God Mode, Calls, Now, Analytics, Growth, Leaderboard) + Storage.
// Same calls as the site; the live ones poll only while the screen is open.
// ═════════════════════════════════════════════════════════════════════════════

fun adOpenUser(id: String, nick: String = "") { if (id.isNotBlank()) Nav.open(AdminUserPage(id, JSONObject().put("id", id).put("nickname", nick).toString())) }

private fun evAgo(t: String?): String = adWhen(t)

/** God Mode (buildGodModePanel): search anyone, bulk verify/block, live activity feed with filters, live watch. */
class AdGodModePage : PageScreen() {
    override val perfName = "AdGodMode"
    override fun equals(other: Any?) = other is AdGodModePage
    override fun hashCode() = 3101
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        val feed = adRes("/api/admin/god-feed")
        AdPoll(feed, 12_000)
        var q by remember { mutableStateOf("") }
        var found by remember { mutableStateOf<List<JSONObject>?>(null) }
        val sel = remember { mutableStateListOf<String>() }
        var filter by remember { mutableStateOf("all") }
        LaunchedEffect(q) {
            if (q.trim().length < 2) { found = null; return@LaunchedEffect }
            delay(300)
            runCatching { Api.get("/api/admin/user-lists?search=" + adEnc(q.trim()) + "&limit=10") }.onSuccess { found = it.arr("users", "results") }
        }
        fun bulk(action: String) {
            val verb = when (action) { "verify" -> "Verify"; "block" -> "Block"; else -> "Unblock" }
            a.sure("$verb ${sel.size} users?", "$verb ${sel.size} selected user(s)?", danger = action == "block", okLabel = verb) {
                val ids = sel.toList()
                scope.launch {
                    var failed = 0
                    ids.forEach { id ->
                        runCatching { Api.put("/api/admin/users", when (action) { "verify" -> jo("id" to id, "verified" to true); "block" -> jo("id" to id, "blocked" to true); else -> jo("id" to id, "blocked" to false) }) }.onFailure { failed++ }
                    }
                    toast(c, if (failed == 0) "✅ ${verb}ed ${ids.size} user(s)" else "✅ ${ids.size - failed} — $failed failed")
                    sel.clear()
                }
            }
        }
        val d = feed.data
        AdPanel("God Mode", sub = d?.let { "· " + it.n("online_now") + " online now" } ?: "See everything, find anyone, act on anyone", res = feed,
            actions = { IconBtn(Sym.Refresh, "Refresh", { scope.launch { feed.load(true) } }) }) { j ->
            item { AdminSearchField(q, { q = it }, "Search any user by name or email…") }
            found?.let { list ->
                if (list.isEmpty()) item { AdHint("No users found.") }
                items(list, key = { "u" + it.s("id") }) { u ->
                    val id = u.s("id"); val on = id in sel
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(on, { if (on) sel.remove(id) else sel.add(id) }, Modifier.padding(start = 6.dp))
                        AdRow(u.s("nickname").ifBlank { "User" } + if (u.b("verified")) " ✓" else "", sub = u.s("email") + if (u.b("blocked")) " · 🚫 blocked" else "",
                            photo = u.s("photo_url"), letter = u.s("nickname").ifBlank { "?" }, modifier = Modifier.weight(1f)) { adOpenUser(id, u.s("nickname")) }
                        IconBtn(Sym.Visibility, "Live watch", { Nav.open(AdLiveWatchPage(id, u.s("nickname"))) })
                    }
                }
            }
            if (sel.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${sel.size} selected", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    AdminPill("✓ Verify") { bulk("verify") }; AdminPill("🚫 Block", color = AdRed) { bulk("block") }; AdminPill("Unblock") { bulk("unblock") }
                }
            }
            item { AdStats(listOf(Triple(adNum(j, "total_users"), "👥 Total users", null), Triple(adNum(j, "online_now"), "🟢 Online now", AdGreen),
                Triple(adNum(j, "new_today"), "✨ New today", null), Triple(adNum(j, "reports_pending"), "🚩 Open reports", if (j.n("reports_pending") > 0) AdRed else null)), perRow = 2) }
            item { SectionTitle("🌐 Live activity") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("all" to "All", "signup" to "Signups", "login" to "Logins", "post" to "Posts", "report" to "🚩 Reports", "short" to "Shorts", "status" to "Status")) { (k, l) ->
                        AdminPill(l, filled = filter == k) { filter = k }
                    }
                }
            }
            val evs = j.arr("events").filter { filter == "all" || it.s("type") == filter }
            if (evs.isEmpty()) adEmpty("No activity for this filter.", Sym.Bolt)
            items(evs.take(200), key = null) { e ->
                AdRow(e.s("icon") + "  " + e.s("text"), trail = evAgo(e.s("t"))) { e.s("user_id").takeIf { it.isNotBlank() }?.let { adOpenUser(it) } }
            }
        }
        AdDialogs(a)
    }
}

/** Live Watch (openLiveWatch): one person's recent activity, refreshed every 8 s. */
data class AdLiveWatchPage(val id: String, val nick: String) : PageScreen() {
    override val perfName = "AdLiveWatch"
    @Composable override fun Content() {
        val res = adRes("/api/admin/user-activity?user_id=" + adEnc(id))
        AdPoll(res, 8_000)
        AdPanel("📡 Live Watch", sub = "@$nick · ● LIVE", res = res) { j ->
            val p = j.optJSONObject("profile")
            if (p != null) item {
                AdRow("@" + p.s("nickname").ifBlank { "user" }, sub = (if (p.b("online")) "🟢 online" else "⚪ offline") + if (p.b("blocked")) " · 🚫 blocked" else "",
                    letter = p.s("nickname").ifBlank { "?" }, trail = "full profile →") { adOpenUser(id, nick) }
            }
            val evs = j.arr("events")
            if (evs.isEmpty()) adEmpty("No recent activity.", Sym.Bolt)
            items(evs, key = null) { e -> AdRow(e.s("icon") + "  " + e.s("text"), trail = evAgo(e.s("t"))) }
        }
    }
}

/** Calls (buildCallsPanel): live 1:1 and group calls with End, recent calls; refreshed every 6 s. */
class AdCallsPage : PageScreen() {
    override val perfName = "AdCalls"
    override fun equals(other: Any?) = other is AdCallsPage
    override fun hashCode() = 3102
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        val res = adRes("/api/admin/calls")
        AdPoll(res, 6_000)
        AdPanel("Calls", sub = res.data?.let { "· " + it.n("calls_today") + " calls today" } ?: "Live calls, history and control", res = res,
            actions = { IconBtn(Sym.Refresh, "Refresh", { scope.launch { res.load(true) } }) }) { j ->
            item { SectionTitle("🔴 Live now") }
            val live = j.arr("active"); val groups = j.arr("group_active")
            if (live.isEmpty() && groups.isEmpty()) item { AdHint("No calls happening right now.") }
            items(live, key = { "c" + it.s("id") }) { k ->
                val ring = k.s("status") == "ringing"
                AdRow(k.s("caller_nick").ifBlank { "?" } + " → " + k.s("callee_nick").ifBlank { "?" }, sub = if (ring) "● Ringing…" else "● In call · " + adWhen(k.s("updated_at")),
                    icon = if (k.s("kind") == "audio") Sym.Mic else Sym.Videocam, iconTint = if (ring) AdAmber else AdGreen) {
                    a.sure("End call?", "Force-end this call?") { adminDo(c, scope, "Call ended", { scope.launch { res.load(true) } }) { Api.post("/api/admin/calls", jo("action" to "end", "call_id" to k.s("id"))) } }
                }
            }
            items(groups, key = { "g" + it.s("room_id") }) { g ->
                AdRow("Group call" + g.s("room_name").let { if (it.isNotBlank()) " · $it" else "" }, sub = "● " + g.n("n") + " on the call", icon = Sym.Groups) {
                    a.sure("End group call?", "Force-end this group call?") { adminDo(c, scope, "Group call ended", { scope.launch { res.load(true) } }) { Api.post("/api/admin/calls", jo("action" to "end_group", "room_id" to g.s("room_id"))) } }
                }
            }
            if (live.isNotEmpty() || groups.isNotEmpty()) item { AdHint("Tap a live call to end it.") }
            item { SectionTitle("🕘 Recent calls") }
            val rec = j.arr("recent")
            if (rec.isEmpty()) item { AdHint("No calls yet.") }
            items(rec, key = { "r" + it.s("id") }) { k ->
                AdRow(k.s("caller_nick").ifBlank { "?" } + " → " + k.s("callee_nick").ifBlank { "?" } + if (k.s("status") == "declined") " (missed)" else "",
                    icon = if (k.s("kind") == "audio") Sym.Mic else Sym.Videocam, iconTint = if (k.s("status") == "declined") AdRed else AdGreen,
                    trail = adWhen(k.s("updated_at").ifBlank { k.s("created_at") }))
            }
        }
        AdDialogs(a)
    }
}

/** Now (buildNowPanel / _loadNow): right now, today, 14-day growth, possible duplicate accounts; every 20 s. */
class AdNowPage : PageScreen() {
    override val perfName = "AdNow"
    override fun equals(other: Any?) = other is AdNowPage
    override fun hashCode() = 3103
    @Composable override fun Content() {
        val res = adRes("/api/admin/insights")
        AdPoll(res, 20_000)
        AdPanel("Now", sub = "Live, refreshes every 20 s", res = res) { j ->
            adCard("🟢 Right now") {
                AdStats(listOf(Triple(adNum(j, "online_now"), "Online", AdGreen), Triple(adNum(j, "active_sessions"), "Sessions", null), Triple(adNum(j, "total_users"), "Total users", null)))
                val on = j.arr("online_users")
                if (on.isEmpty()) AdHint("Nobody is active in the last minute.")
                else LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(on, key = { it.s("id") }) { u -> AdminPill("@" + u.s("nickname")) { adOpenUser(u.s("id"), u.s("nickname")) } }
                }
            }
            val t = j.optJSONObject("today")
            if (t != null) adCard("📅 Today") {
                AdStats(t.keys().asSequence().take(9).map { k -> Triple(adNum(t, k), k.replace('_', ' ').replaceFirstChar { it.uppercase() }, null) }.toList())
            }
            val g = j.arr("growth")
            adCard("📈 New users (14 days)") {
                if (g.isEmpty()) AdHint("No signups in the last 14 days.") else AdBars(g.map { it.s("day").takeLast(2) to it.n("count") })
            }
            adCard("👥 Possible duplicate accounts", hint = "Accounts that share one device or IP") {
                val dups = j.arr("duplicates")
                if (dups.isEmpty()) AdHint("No shared devices/IPs detected 👍")
                dups.forEach { dd ->
                    Text((if (dd.s("type") == "IP") "🌐 " else "📱 ") + dd.s("key") + " · " + dd.n("count") + " accounts", Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(dd.arr("users"), key = { it.s("id") }) { u -> AdminPill("@" + u.s("nickname")) { adOpenUser(u.s("id"), u.s("nickname")) } }
                    }
                }
            }
        }
    }
}

/** Analytics (refreshAnalytics): six counters and 7-day visitors, live every 10 s. */
class AdAnalyticsPage : PageScreen() {
    override val perfName = "AdAnalytics"
    override fun equals(other: Any?) = other is AdAnalyticsPage
    override fun hashCode() = 3104
    @Composable override fun Content() {
        val res = adRes("/api/admin/stats")
        AdPoll(res, 10_000)
        AdPanel("Analytics", sub = res.data?.optJSONObject("stats")?.let { "● " + it.n("online") + " users online now" } ?: "● loading…", res = res) { j ->
            val s = j.optJSONObject("stats") ?: JSONObject()
            item { AdStats(listOf(Triple(adNum(s, "users"), "Total users · ↑ " + s.n("new_today") + " today", null), Triple(adNum(s, "online"), "Online now", AdGreen),
                Triple(adNum(s, "shorts"), "Videos", null), Triple(adNum(s, "messages"), "Messages", null), Triple(adNum(s, "music"), "Music tracks", null),
                Triple(adNum(s, "open_reports"), "Open reports · needs review", if (s.n("open_reports") > 0) AdRed else null))) }
            adCard("📈 Daily visitors — last 7 days") {
                val v = j.arr("daily_visitors").map { it.n("visitors") }.takeLast(7).let { List(7 - it.size) { 0 } + it }
                AdBars(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").zip(v))
            }
        }
    }
}

/** Growth (buildGrowthPanel). */
class AdGrowthPage : PageScreen() {
    override val perfName = "AdGrowth"
    override fun equals(other: Any?) = other is AdGrowthPage
    override fun hashCode() = 3105
    @Composable override fun Content() {
        val res = adRes("/api/admin/growth")
        AdPanel("Growth", res = res) { d ->
            if (d.has("ok") && !d.b("ok")) { item { AdHint("❌ " + d.s("error").ifBlank { "Error" }, AdRed) }; return@AdPanel }
            item { AdStats(listOf(Triple(adNum(d, "total"), "Total users", null), Triple(adNum(d, "new_24h"), "New 24h", AdGreen), Triple(adNum(d, "new_7d"), "New 7d", null),
                Triple(adNum(d, "dau"), "Active today", null), Triple(adNum(d, "wau"), "Active 7d", null), Triple(adNum(d, "mau"), "Active 30d", null))) }
            adCard("New users / day (30d)") {
                val per = d.arr("per_day")
                if (per.isEmpty()) AdHint("No signups in the last 30 days.") else AdBars(per.map { it.s("day").takeLast(2) to it.n("count") })
            }
        }
    }
}

/** Leaderboard (buildLeaderboardPanel): top users by videos, music, messages. */
class AdLeaderboardPage : PageScreen() {
    override val perfName = "AdLeaderboard"
    override fun equals(other: Any?) = other is AdLeaderboardPage
    override fun hashCode() = 3106
    @Composable override fun Content() {
        val res = adRes("/api/admin/leaderboard")
        AdPanel("Leaderboard", sub = "Top users by activity", res = res) { j ->
            var any = false
            listOf(Triple("shorts", "Videos (Shorts)", "vids"), Triple("music", "Music uploads", "tracks"), Triple("messages", "Messages sent", "msgs")).forEach { (k, t, unit) ->
                val l = j.arr(k)
                if (l.isNotEmpty()) { any = true
                    adCard(t) { l.forEachIndexed { i, u -> AdRow("\u200E#${i + 1}  \u2068" + u.s("uid", "nickname").ifBlank { "unknown" } + "\u2069", trail = adminN(u.n("cnt")) + " " + unit) { u.s("uid").let { adOpenUser(it) } } } }
                }
            }
            if (!any) adEmpty("No data yet", Icons.Filled.EmojiEvents)
        }
    }
}

/** Storage (buildHealthPanel): media files + bytes, database rows per table. */
class AdStoragePage : PageScreen() {
    override val perfName = "AdStorage"
    override fun equals(other: Any?) = other is AdStoragePage
    override fun hashCode() = 3107
    @Composable override fun Content() {
        val res = adRes("/api/admin/health")
        AdPanel("Storage", res = res) { d ->
            if (d.has("ok") && !d.b("ok")) { item { AdHint("❌ " + d.s("error").ifBlank { "Error" }, AdRed) }; return@AdPanel }
            val r2 = d.optJSONObject("r2") ?: JSONObject()
            val more = if (r2.b("truncated")) "+" else ""
            adCard("📦 Media storage") {
                AdStats(listOf(Triple(adminN(r2.n("objects")) + more, "files", null), Triple(adBytes(r2.optLong("bytes")) + more, "used", null)), perRow = 2)
                if (r2.b("truncated")) AdHint("Showing first ~10k files.")
            }
            adCard("🗄 Database rows") {
                val cts = d.optJSONObject("counts") ?: JSONObject()
                cts.keys().asSequence().toList().forEach { t -> AdRow(t, trail = if (cts.isNull(t)) "—" else "%,d".format(cts.optLong(t))) }
            }
        }
    }
}

fun adBytes(b: Long): String = when { b < 1024 -> "$b B"; b < 1048576 -> "%.1f KB".format(b / 1024.0); b < 1073741824 -> "%.1f MB".format(b / 1048576.0); else -> "%.2f GB".format(b / 1073741824.0) }
