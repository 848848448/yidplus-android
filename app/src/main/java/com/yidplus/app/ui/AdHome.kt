package com.yidplus.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yidplus.app.data.Session
import kotlinx.coroutines.delay
import org.json.JSONObject

// ═════════════════════════════════════════════════════════════════════════════
// AD 2.1 — the admin home like the site's control center: a search box, then the 5 categories
// (admin.js ADMIN_CATEGORIES) with every panel the signed-in role may open (ADMIN_PANELS roles).
// ═════════════════════════════════════════════════════════════════════════════

/** One site panel: its admin.js id, label, short line, icon, roles that see it, and the native page. */
class AdPanelDef(val id: String, val label: String, val sub: String, val icon: ImageVector, val roles: String, val open: () -> Screen)

private const val ALL = "admin_limited,admin_super,owner"
private const val SUPER = "admin_super,owner"
private const val OWNER = "owner"

val adCategories: List<Triple<String, String, Color>> = listOf(
    Triple("Overview", "God Mode, analytics and leaderboard", Color(0xFF7C3AED)),
    Triple("People", "Access, verify, users, invites", Color(0xFF1F6F5C)),
    Triple("Moderation", "Reports, feedback, support, filter", Color(0xFFDC2626)),
    Triple("Content", "Announce, broadcast, channels, more", Color(0xFF2563EB)),
    Triple("System", "Features, app, ads, logs, export", Color(0xFF6B7280)),
)

val adPanels: Map<String, List<AdPanelDef>> = mapOf(
    "Overview" to listOf(
        AdPanelDef("god-mode", "👁 God Mode", "Everyone online, live activity, bulk actions", Sym.Visibility, OWNER) { AdGodModePage() },
        AdPanelDef("calls", "📞 Calls", "Live calls, end a call, recent calls", Sym.Call, OWNER) { AdCallsPage() },
        AdPanelDef("now", "Now", "Who's online right now", Sym.Bolt, ALL) { AdNowPage() },
        AdPanelDef("analytics", "Analytics", "Visits, pages, devices", Sym.Insights, ALL) { AdAnalyticsPage() },
        AdPanelDef("growth", "Growth", "Sign-ups over time", Sym.TrendingUp, ALL) { AdGrowthPage() },
        AdPanelDef("leaderboard", "Leaderboard", "Most active members", Icons.Filled.EmojiEvents, ALL) { AdLeaderboardPage() },
    ),
    "People" to listOf(
        AdPanelDef("users", "Users", "Search, verify, mute, block, roles", Sym.People, ALL) { AdminUsersPage() },
        AdPanelDef("verify-requests", "Verify", "Approve or deny the ✅ mark", Sym.Verified, ALL) { AdminVerifyPage() },
        AdPanelDef("warnings", "Warnings", "Warnings given to members", Sym.Warning, ALL) { AdWarningsPage() },
        AdPanelDef("access-control", "Access", "Lock the site, allow-list, create accounts", Sym.Lock, OWNER) { AdAccessPage() },
        AdPanelDef("invite-codes", "Invites", "Invite codes", Sym.ConfirmationNumber, OWNER) { AdInvitesPage() },
        AdPanelDef("sessions", "Sessions", "Signed-in devices, sign people out", Sym.Devices, OWNER) { AdSessionsPage() },
        AdPanelDef("banned-devices", "Banned", "Banned devices and IPs", Sym.Block, OWNER) { AdBannedPage() },
        AdPanelDef("badges", "Badges", "Give and take badges", Sym.MilitaryTech, OWNER) { AdBadgesPage() },
        AdPanelDef("ad-exempt", "Ad-Free", "Members who never see ads", Sym.DoNotDisturbOn, OWNER) { AdAdFreePage() },
    ),
    "Moderation" to listOf(
        AdPanelDef("reports", "Reports", "Resolve, warn, block", Sym.Flag, ALL) { AdReportsPage() },
        AdPanelDef("feedback", "Feedback", "What members sent", Sym.Feedback, ALL) { AdFeedbackPage() },
        AdPanelDef("support-chats", "Support", "Help chats from members", Sym.SupportAgent, ALL) { AdSupportPage() },
        AdPanelDef("chat-watch", "Chats", "Read, hide or delete rooms", Sym.Forum, ALL) { AdChatsPage() },
        AdPanelDef("shorts-mod", "Shorts", "Remove videos", Sym.Movie, ALL) { AdShortsModPage() },
        AdPanelDef("music-mod", "Music", "The music library: remove tracks", Sym.MusicNote, ALL) { AdMusicModPage() },
        AdPanelDef("statuses-mod", "Statuses", "Remove statuses", Sym.Animation, SUPER) { AdStatusesModPage() },
        AdPanelDef("bad-words", "Word filter", "Words and phrases blurred for everyone", Sym.Block, OWNER) { AdminBadWordsPage() },
    ),
    "Content" to listOf(
        AdPanelDef("announcements", "Announcements", "Pinned at the top for everyone", Sym.PushPin, ALL) { AdAnnouncementsPage() },
        AdPanelDef("featured", "Featured", "Put posts at the top of the feed", Sym.Star, SUPER) { AdFeaturedPage() },
        AdPanelDef("broadcast", "Broadcast", "A message to every member, now or scheduled", Sym.Podcasts, OWNER) { AdminBroadcastPage() },
        AdPanelDef("email-blast", "Email all users", "One email to every member", Icons.Filled.Email, OWNER) { AdEmailBlastPage() },
        AdPanelDef("channels-mgr", "Channels", "Member channels: verify or delete", Sym.Campaign, OWNER) { AdminChannelsPage() },
        AdPanelDef("tg-channels", "Telegram channels", "The channels in the Channels tab", Sym.Send, OWNER) { AdminTelegramPage() },
        AdPanelDef("telegram", "Telegram", "Bots, email forwarding, bridges", Icons.Filled.SmartToy, OWNER) { AdTelegramBotsPage() },
        AdPanelDef("ai", "YID PLUS AI", "The assistant in everyone's chat list", Icons.Filled.AutoAwesome, OWNER) { AdAiPage() },
        AdPanelDef("email-templates", "Email", "What the site's emails say", Sym.MarkEmailRead, OWNER) { AdEmailSettingsPage() },
    ),
    "System" to listOf(
        AdPanelDef("features", "Auto-moderation & speed", "Auto-hide after reports, optimize database", Sym.Tune, OWNER) { AdFeaturesMorePage() },
        AdPanelDef("app-settings", "App", "Name, sign-up and help chat", Icons.Filled.PhoneAndroid, OWNER) { AdAppSettingsPage() },
        AdPanelDef("maintenance", "Maintenance", "Maintenance mode, guests, verification", Sym.Build, OWNER) { AdMaintenancePage() },
        AdPanelDef("antispam", "Anti-spam", "Keep fake accounts out", Sym.Shield, OWNER) { AdAntispamPage() },
        AdPanelDef("ads", "Ads", "Ads shown on the site and in the app", Sym.Campaign, OWNER) { AdAdsPage() },
        AdPanelDef("security", "Attacks", "Blocked attacks, ban IP / country / network", Icons.Filled.GppBad, OWNER) { AdAttacksPage() },
        AdPanelDef("diagnostics", "Health check", "What's working, what needs a fix", Icons.Filled.HealthAndSafety, OWNER) { AdHealthCheckPage() },
        AdPanelDef("code-errors", "Code errors", "Problems people hit", Icons.Filled.BugReport, SUPER) { AdCodeErrorsPage() },
        AdPanelDef("health", "Storage", "How much space everything uses", Sym.Storage, OWNER) { AdStoragePage() },
        AdPanelDef("app-config", "Config & Keys", "Site settings and keys", Sym.Key, OWNER) { AdConfigPage() },
        AdPanelDef("ip-logs", "IP logs", "Who signed up and in, from where", Sym.Public, OWNER) { AdIpLogsPage() },
        AdPanelDef("audit-logs", "Audit logs", "Every admin action", Sym.History, OWNER) { AdAuditLogsPage() },
        AdPanelDef("export", "Export", "Download the members list", Sym.Download, OWNER) { AdExportPage() },
        AdPanelDef("nuclear", "Nuclear", "Hide or restore everything of one kind", Sym.Warning, OWNER) { AdNuclearPage() },
        AdPanelDef("admin-settings", "Admin settings", "Outside videos in Shorts", Sym.AdminPanelSettings, OWNER) { AdAdminSettingsPage() },
    ),
)

/** The signed-in admin's role word, as the site's ADMIN_gateRole. */
fun adRole(): String = when {
    Session.me?.isOwner == true || Session.me?.role == "owner" || SettingsProfile.demo -> "owner"
    Session.me?.role == "admin_super" -> "admin_super"
    else -> "admin_limited"
}

fun adVisible(p: AdPanelDef) = adRole() in p.roles.split(',')

/** The search + 5 categories on the admin home. Inside SettingsPage's column. */
@Composable
fun AdHomeSections(stats: JSONObject?) {
    var q by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    // QA: `--es qa_ad <panel id>` opens that panel natively (fixtures answer its calls).
    val c = ctx()
    LaunchedEffect(Unit) {
        while (true) {
            settingsQaExtra(c, "qa_ad")?.let { id -> settingsQaClear(c, "qa_ad"); adQaPage(id)?.let { Nav.open(it) } }
            delay(700)
        }
    }
    AdminSearchField(q, { q = it }, "Search admin…")
    val query = q.trim().lowercase()
    if (query.isNotEmpty()) {
        val hits = adPanels.flatMap { (cat, l) -> l.filter(::adVisible).filter { (it.label + " " + it.sub + " " + cat).lowercase().contains(query) }.map { cat to it } }
        SettingsCard("Results (" + hits.size + ")") {
            if (hits.isEmpty()) SettingsHint("Nothing matches \u201C" + q.trim() + "\u201D")
            hits.forEachIndexed { i, (cat, p) ->
                if (i > 0) RowDivider(64.dp)
                SettingRow(p.icon, p.label, cat + " · " + p.sub, modifier = if (i == 0) Modifier.firstFocus() else Modifier) { Nav.open(p.open()) }
            }
        }
        return
    }
    adCategories.forEach { (cat, sub, color) ->
        val list = adPanels[cat].orEmpty().filter(::adVisible)
        if (list.isEmpty()) return@forEach
        val isOpen = open == cat
        SettingsCard(null) {
            SettingRow(when (cat) { "Overview" -> Icons.Filled.Dashboard; "People" -> Sym.People; "Moderation" -> Sym.Gavel; "Content" -> Sym.Article; else -> Sym.Settings },
                cat, sub, value = (if (isOpen) "▲ " else "") + list.size) { open = if (isOpen) null else cat }
            if (isOpen) list.forEach { p ->
                RowDivider(64.dp)
                val badge = when (p.id) {
                    "reports" -> stats?.optInt("open_reports")?.takeIf { it > 0 }?.toString()
                    "users" -> stats?.optInt("users")?.takeIf { it > 0 }?.let { adminN(it) }
                    "channels-mgr" -> stats?.optInt("channels")?.takeIf { it > 0 }?.toString()
                    else -> null
                }
                SettingRow(p.icon, p.label, p.sub, value = badge) { Nav.open(p.open()) }
            }
        }
    }
    SettingsHint("Rare tools: the website's admin page has everything too.")
    SettingsCard(null) { SettingRow(Sym.OpenInNew, "Open the website admin", "Signs in there with your account") { Nav.open(AdminWebPage(null)) } }
}

/** QA ids → pages (panel ids above plus a few inner pages). */
fun adQaPage(id: String): Screen? {
    adPanels.values.flatten().firstOrNull { it.id == id }?.let { return it.open() }
    return when (id) {
        "support-thread" -> AdSupportThreadPage("sc-1")
        "room-read" -> AdRoomReadPage("r-1", "General")
        "live-watch" -> AdLiveWatchPage("u-yanky", "Yanky")
        "user-list" -> AdUserListPage("u-yanky", "followers")
        "edit-user" -> AdEditUserPage("u-yanky", JSONObject().put("id", "u-yanky").put("nickname", "Yanky").put("email", "yanky@example.com").toString())
        "ad-exempt-one" -> AdAdExemptPage("ad-1")
        "support-questions" -> AdSupportQuestionsPage()
        else -> null
    }
}
