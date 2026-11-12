package com.yidplus.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.yidplus.app.data.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.json.JSONObject

// AD 2.1 — System category, part 2 (admin.js): Attacks, Health check, Code errors, Config & Keys, IP logs, Audit logs, Export, Nuclear, Features extras.

private fun flag(cc: String): String = if (cc.length != 2) "" else cc.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")

/** Attacks (buildSecurityPanel): live every 8 s, pause, phone alerts on/off + test, stats, top offenders, the feed with details, ban IP/country/network, clear log. */
class AdAttacksPage : PageScreen() {
    override val perfName = "AdAttacks"
    override fun equals(other: Any?) = other is AdAttacksPage
    override fun hashCode() = 3506
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/admin/security-log?limit=150", tick)
        var paused by remember { mutableStateOf(false) }
        LaunchedEffect(paused) { while (!paused) { delay(8_000); runCatching { res.load(force = true) } } }
        var open by remember { mutableStateOf<String?>(null) }
        fun ban(value: String, human: String, reason: String) = a.sure("Block?", "Block $human? They will be turned away from the entire site.") {
            adminDo(c, scope, "🚫 Blocked $human", { tick++ }) { Api.post("/api/admin/device-bans", jo("ip" to value, "reason" to reason)) }
        }
        AdPanel("Attacks", sub = if (paused) "Paused" else "● Live · every 8 s", res = res,
            actions = { IconBtn(if (paused) Sym.PlayArrow else Sym.Pause, if (paused) "Resume" else "Pause", { paused = !paused }, tint = Color.White) }) { j ->
            val st = j.optJSONObject("stats") ?: JSONObject()
            item { AdStats(listOf(Triple(adNum(st, "total"), "Total blocked", null), Triple(adNum(st, "last24"), "Last 24h", AdAmber), Triple(adNum(st, "unique_ips"), "Unique IPs", AdRed))) }
            adCard(null) {
                val alerts = j.optBoolean("alerts", true)
                SettingRow(if (alerts) Sym.NotificationsActive else Sym.NotificationsOff, "Phone alerts", if (alerts) "ON" else "OFF", switch = alerts, modifier = Modifier.firstFocus()) {
                    adminDo(c, scope, if (!alerts) "🔔 Alerts on" else "🔕 Alerts off", { tick++ }) { Api.post("/api/admin/security-log", jo("alerts" to !alerts)) }
                }
                if (alerts) AdPills("Send test alert" to { adminDo(c, scope, "🔔 Test sent") { Api.post("/api/admin/security-log", jo("test" to true)) } })
                AdPills("Clear log" to { a.sure("Clear?", "Clear the entire security log? This cannot be undone.") { adminDo(c, scope, "🧹 Security log cleared", { tick++ }) { Api.delete("/api/admin/security-log") } } })
            }
            val banned = j.optJSONArray("banned")?.let { b -> (0 until b.length()).map { i -> b.optJSONObject(i)?.s("ip") ?: b.optString(i) }.toSet() } ?: emptySet()
            val off = j.arr("offenders")
            item { SectionTitle("Top offenders") }
            if (off.isEmpty()) item { AdHint("No repeat attackers.") }
            items(off.take(20), key = { "o" + it.s("ip") }) { o ->
                val isB = o.s("ip") in banned || ("country:" + o.s("country").uppercase()) in banned
                AdRow(flag(o.s("country")) + " " + o.s("ip").ifBlank { "—" } + "  ×" + o.n("hits"), sub = listOf(o.s("city"), o.s("country")).filter { it.isNotBlank() }.joinToString(", ") + o.s("asn").let { if (it.isNotBlank()) " · $it" else "" },
                    sub2 = "first " + adWhen(o.s("first_seen")) + " · last " + adWhen(o.s("last_seen")), tag = if (isB) "Banned" to AdRed else null, icon = Icons.Filled.GppBad, iconTint = AdRed) {
                    if (!isB) a.actions(o.s("ip"), "🚫 Ban IP" to { ban(o.s("ip"), o.s("ip"), "Banned from Attacks panel") },
                        "🌍 Ban country" to { if (o.s("country").isBlank()) toast(c, "No country") else ban("country:" + o.s("country").uppercase(), "everyone from " + o.s("country").uppercase(), "Country block from Attacks panel") },
                        "📡 Ban network" to { val n = Regex("AS?(\\d+)").find(o.s("asn"))?.groupValues?.get(1); if (n == null) toast(c, "No network") else ban("asn:$n", "network $n", "Network block from Attacks panel") })
                }
            }
            val logs = j.arr("logs")
            item { SectionTitle("Blocked requests (" + logs.size + ")") }
            if (logs.isEmpty()) adEmpty("No attacks blocked yet 🛡️", Sym.Shield)
            items(logs, key = { "l" + it.s("id") }) { l ->
                val isOpen = open == l.s("id")
                Column {
                    AdRow(l.s("attack_type").ifBlank { "blocked" } + " — " + flag(l.s("country")) + " " + l.s("ip"), sub = l.s("method") + " " + l.s("path"), trail = adWhen(l.s("created_at")),
                        icon = Sym.Warning, iconTint = AdRed) { open = if (isOpen) null else l.s("id") }
                    if (isOpen) {
                        val meta = l.optJSONObject("meta") ?: runCatching { JSONObject(l.s("meta")) }.getOrNull() ?: JSONObject()
                        AdMono(listOf("City: " + l.s("city") + ", " + l.s("region") + " " + l.s("country"), "Network: " + l.s("asn"), "Browser: " + l.s("user_agent"), "Referer: " + l.s("referer"),
                            "Timezone: " + meta.s("timezone"), "Language: " + meta.s("acceptLanguage")).filter { !it.endsWith(": ") && !it.endsWith(":  ") }.joinToString("\n"), maxLines = 14)
                        AdPills("🚫 Ban IP" to { ban(l.s("ip"), l.s("ip"), "Banned from Attacks panel") }, "🌍 Country" to { if (l.s("country").isNotBlank()) ban("country:" + l.s("country").uppercase(), "everyone from " + l.s("country").uppercase(), "Country block from Attacks panel") })
                    }
                }
            }
        }
        AdDialogs(a)
    }
}

/** Health check (buildDiagnosticsPanel): server checks grouped by category + live probes of the main endpoints from this phone. */
class AdHealthCheckPage : PageScreen() {
    override val perfName = "AdHealthCheck"
    override fun equals(other: Any?) = other is AdHealthCheckPage
    override fun hashCode() = 3507
    @Composable override fun Content() {
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/admin/diagnostics", tick)
        val probes = listOf("Feed / posts" to "/api/posts", "Chat rooms" to "/api/chat/rooms", "Shorts" to "/api/shorts", "Music" to "/api/music", "Status updates" to "/api/statuses",
            "Channels" to "/api/channels", "Your session" to "/api/auth/me", "Admin stats" to "/api/admin/stats")
        val results = remember { mutableStateMapOf<String, Pair<Boolean, String>>() }
        LaunchedEffect(tick) {
            results.clear()
            probes.forEach { (l, u) -> launch { val t0 = System.currentTimeMillis(); val r = runCatching { Api.get(u) }; results[l] = r.isSuccess to (if (r.isSuccess) "${System.currentTimeMillis() - t0} ms" else Api.friendly(r.exceptionOrNull()!!)) } }
        }
        AdPanel("Health check", sub = "What's working, what needs a fix", res = res, onRefresh = { tick++ }) { j ->
            val sm = j.optJSONObject("summary") ?: JSONObject()
            item { AdStats(listOf(Triple(sm.n("ok").toString(), "✅ OK", AdGreen), Triple(sm.n("warn").toString(), "⚠️ Warnings", AdAmber), Triple(sm.n("fail").toString(), "❌ Broken", AdRed))) }
            val checks = j.arr("checks")
            if (checks.isEmpty()) item { AdHint("No checks returned.") }
            checks.groupBy { it.s("category").ifBlank { "Other" } }.forEach { (cat, list) ->
                adCard(cat) {
                    list.forEach { ck ->
                        val st = ck.s("status")
                        AdRow((when (st) { "ok" -> "✅ "; "warn" -> "⚠️ "; else -> "❌ " }) + ck.s("label"), sub = ck.s("detail"), sub2 = ck.s("fix").let { if (it.isNotBlank() && st != "ok") "Fix: $it" else "" })
                    }
                }
            }
            adCard("📱 From this phone") {
                probes.forEach { (l, _) -> val r = results[l]; AdRow((if (r == null) "⏳ " else if (r.first) "✅ " else "❌ ") + l, trail = r?.second ?: "…") }
            }
        }
    }
}

/** Code errors (buildCodeErrorsPanel): errors from the site and the app, copy one, send a test, clear all. */
class AdCodeErrorsPage : PageScreen() {
    override val perfName = "AdCodeErrors"
    override fun equals(other: Any?) = other is AdCodeErrorsPage
    override fun hashCode() = 3508
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        val clip = LocalClipboardManager.current
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/error-log", tick)
        AdPanel("Code errors", sub = "Problems people hit", res = res) { j ->
            item {
                AdPills("Send a test error" to { adminDo(c, scope, "✅ Test sent", { tick++ }) { Api.post("/api/error-log", jo("level" to "error", "message" to "TEST error — the Code Errors panel is working. You can clear this.", "source" to "admin-test", "line" to 1, "url" to "/yidplus-admin")) } },
                    "🧹 Clear all" to { a.sure("Clear?", "Clear every logged error?") { adminDo(c, scope, "Cleared", { tick++ }) { Api.delete("/api/error-log") } } })
            }
            val l = j.arr("errors")
            if (l.isEmpty()) adEmpty("No errors 🎉", Icons.Filled.BugReport)
            items(l, key = { "e" + it.s("id") }) { e ->
                val text = e.s("message") + "\n" + e.s("source") + ":" + e.s("line") + ":" + e.s("col") + "\n" + e.s("page") + "\n" + e.s("stack")
                AdRow(e.s("message").take(160), sub = e.s("source") + (if (e.s("line").isNotBlank()) ":" + e.s("line") else "") + " · " + e.s("page"),
                    sub2 = (if (e.n("count") > 1) "×" + e.n("count") + " · " else "") + e.s("nickname").let { if (it.isNotBlank()) "@$it · " else "" } + adWhen(e.s("created_at")),
                    icon = Icons.Filled.BugReport, iconTint = if (e.s("level") == "warn") AdAmber else AdRed) {
                    clip.setText(AnnotatedString(text)); toast(c, "📋 Copied")
                }
            }
        }
        AdDialogs(a)
    }
}

/** Config & Keys (buildAppConfigPanel): each setting with its state, save a new value (never shown back), clear; avatar cache; TURN test. */
class AdConfigPage : PageScreen() {
    override val perfName = "AdConfig"
    override fun equals(other: Any?) = other is AdConfigPage
    override fun hashCode() = 3509
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/admin/app-config", tick)
        val vals = remember { mutableStateMapOf<String, String>() }
        var cacheOut by remember { mutableStateOf<String?>(null) }; var turnOut by remember { mutableStateOf<String?>(null) }
        AdPanel("Config & Keys", sub = "Saved values are never shown back", res = res) { j ->
            val list = j.arr("items")
            if (list.isEmpty()) item { AdHint("Nothing to configure.") }
            list.forEach { it2 ->
                val k = it2.s("key")
                adCard(it2.s("label")) {
                    AdHint((if (it2.b("set_env")) "✅ Set on the server" else if (it2.b("set_app")) "✅ Saved" else "⚪ Not set") + " · " + k + it2.s("hint").let { if (it.isNotBlank()) " · $it" else "" })
                    AdField(vals[k] ?: "", { vals[k] = it }, if (it2.b("set_app")) "•••••• (saved) — type to change" else "enter value", password = it2.b("secret"))
                    AdPills(*listOfNotNull<Pair<String, () -> Unit>>("Save" to {
                        val v = (vals[k] ?: "").trim()
                        if (v.isEmpty()) toast(c, "Enter a value first") else scope.launch { runCatching { Api.post("/api/admin/app-config", jo("key" to k, "value" to v)) }.onSuccess { r -> if (r.optBoolean("ok", true)) { toast(c, "✅ Saved"); vals.remove(k); tick++ } else toast(c, r.s("error").ifBlank { "Failed" }) }.onFailure { toast(c, Api.friendly(it)) } }
                    }, if (it2.b("set_app")) "✕ Clear" to { a.sure("Clear?", "Clear this value?") { adminDo(c, scope, "Cleared", { tick++ }) { Api.delete("/api/admin/app-config?key=" + adEnc(k)) } } } else null).toTypedArray())
                }
            }
            adCard("🖼 Avatar cache", hint = "Downloads everyone's profile photos to the site's own storage and clears dead links.") {
                AdButton("Download & cache avatars") {
                    scope.launch {
                        var cached = 0; var failed = 0
                        while (true) {
                            val rr = runCatching { Api.post("/api/admin/cache-avatars", JSONObject()) }
                            val r = rr.getOrNull()
                            if (r == null) { cacheOut = "❌ " + Api.friendly(rr.exceptionOrNull()!!); break }
                            if (!r.optBoolean("ok", true)) { cacheOut = r.s("error").ifBlank { "Failed" }; break }
                            cached += r.n("cached"); failed += r.n("failed")
                            cacheOut = "✅ Cached $cached · cleared $failed dead links · " + r.n("remaining") + " left…"
                            if (r.n("remaining") > 0 && (r.n("cached") > 0 || r.n("failed") > 0)) delay(400) else { cacheOut = "🎉 Done! Cached $cached avatars, cleared $failed dead links."; break }
                        }
                    }
                }
                cacheOut?.let { AdHint(it) }
            }
            adCard("📞 Call relay (TURN) test", hint = "Without it, video calls don't connect on most networks (a frozen picture with no sound).") {
                AdButton("Run TURN test") { turnOut = "Checking…"; scope.launch { runCatching { Api.get("/api/admin/turn-test") }.onSuccess { r -> turnOut = (if (r.b("turn_working")) "✅ " else "❌ ") + r.s("message") }.onFailure { turnOut = "❌ " + Api.friendly(it) } } }
                turnOut?.let { AdHint(it) }
            }
        }
        AdDialogs(a)
    }
}

/** IP logs (buildIpLogsPanel): sign-ups and logins with IP; Ban IP. */
class AdIpLogsPage : PageScreen() {
    override val perfName = "AdIpLogs"
    override fun equals(other: Any?) = other is AdIpLogsPage
    override fun hashCode() = 3510
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        val res = adRes("/api/admin/device-bans?logs=1")
        AdPanel("IP logs", sub = "Who signed up and signed in, from where", res = res) { j ->
            val l = j.arr("logs")
            if (l.isEmpty()) adEmpty("No IP logs yet", Sym.Public)
            items(l, key = null) { x ->
                AdRow(x.s("nickname", "user_id"), sub = x.s("ip").ifBlank { "—" } + " · " + adWhen(x.s("created_at")), letter = x.s("nickname").ifBlank { "?" },
                    tag = x.s("action") to (if (x.s("action") == "register") AdGreen else AdAmber), trail = "🚫 Ban IP") {
                    val ip = x.s("ip")
                    if (ip.isBlank() || ip == "0.0.0.0") toast(c, "No valid IP")
                    else a.sure("Ban IP?", "Ban IP: $ip?") { adminDo(c, scope, "🚫 IP banned!") { Api.post("/api/admin/device-bans", jo("ip" to ip, "reason" to "Banned from IP Logs")) } }
                }
            }
        }
        AdDialogs(a)
    }
}

private val auditIcons = mapOf("delete_post" to "🗑️", "delete_short" to "🗑️", "delete_message" to "🗑️", "block_user" to "🚫", "unblock_user" to "✅", "verify_user" to "👑",
    "unverify_user" to "➖", "change_role" to "🔄", "resolve_report" to "✓", "resolve_feedback" to "✓")

/** Audit logs (buildAuditLogsPanel): the last 80 admin actions. */
class AdAuditLogsPage : PageScreen() {
    override val perfName = "AdAuditLogs"
    override fun equals(other: Any?) = other is AdAuditLogsPage
    override fun hashCode() = 3511
    @Composable override fun Content() {
        val res = adRes("/api/admin/audit-logs?limit=80")
        AdPanel("Audit logs", sub = "Every admin action", res = res) { j ->
            val l = j.arr("logs")
            if (l.isEmpty()) adEmpty("No admin actions yet", Sym.History)
            items(l, key = null) { x ->
                val role = when (x.s("actor_role")) { "owner" -> "👑"; "admin_super" -> "🛡"; else -> "🔒" }
                AdRow((auditIcons[x.s("action")] ?: "📋") + " " + role + " @" + x.s("actor_nick").ifBlank { "unknown" } + " " + x.s("action").replace('_', ' '), sub = x.s("details"), trail = adWhen(x.s("created_at")))
            }
        }
    }
}

/** Export (buildExportPanel): the users CSV (GET /api/admin/export), saved to Downloads. */
class AdExportPage : PageScreen() {
    override val perfName = "AdExport"
    override fun equals(other: Any?) = other is AdExportPage
    override fun hashCode() = 3512
    @Composable override fun Content() {
        val c = ctx()
        AdPanel("Export", sub = "Download your data") { _ ->
            adCard("📦 Export data", hint = "A spreadsheet (CSV) of every member.") {
                AdButton("Download users CSV") { toast(c, "Downloading..."); openUrl(c, Api.abs("/api/admin/export") ?: "https://yidplus.com/api/admin/export") }
                AdHint("Opens in the browser, which saves yidplus-users.csv (you need to be signed in there too).")
            }
        }
    }
}

private val nuclearCats = listOf(Triple("shorts", "📹 Videos (Shorts)", "All video uploads"), Triple("music", "🎵 Music", "All music tracks"), Triple("channels", "📡 Channel Posts", "All channel posts"),
    Triple("statuses", "🌀 Statuses / Stories", "All statuses"), Triple("messages", "💬 Chat Messages", "All group messages"), Triple("posts", "📝 Posts", "All posts"))

/** Nuclear (buildNuclearPanel): hide / restore a whole kind of content; who else may use it (grant / revoke). */
class AdNuclearPage : PageScreen() {
    override val perfName = "AdNuclear"
    override fun equals(other: Any?) = other is AdNuclearPage
    override fun hashCode() = 3513
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope(); val a = rememberAsk()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/admin/nuclear?permissions=1", tick)
        var q by remember { mutableStateOf("") }
        val found = adUserSearch(q)
        AdPanel("Nuclear", sub = "Hide or restore everything of one kind", res = res) { j ->
            adCard("☢️ Hide / restore content", hint = "Hiding never deletes anything. Restore brings it all back.") {
                nuclearCats.forEach { (id, label, desc) ->
                    AdRow(label, sub = desc)
                    AdPills("🔴 Hide all" to { a.sure("⚠️ HIDE ALL $id?", "Are you sure?") { adminDo(c, scope, "🔴 Hidden!") { Api.post("/api/admin/nuclear", jo("category" to id, "action" to "hide")) } } },
                        "🟢 Restore" to { a.sure("⚠️ RESTORE ALL $id?", "Are you sure?", danger = false) { adminDo(c, scope, "🟢 Restored!") { Api.post("/api/admin/nuclear", jo("category" to id, "action" to "restore")) } } })
                }
            }
            item { SectionTitle("👥 Nuclear permissions") }
            item { AdminSearchField(q, { q = it }, "Search user...") }
            found?.forEach { u -> item(key = "f" + u.s("id")) { AdRow(u.s("nickname"), letter = u.s("nickname").ifBlank { "?" }, trail = "+ Grant") {
                adminDo(c, scope, "☢️ Nuclear access granted!", { q = ""; tick++ }) { Api.post("/api/admin/nuclear?permissions=1", jo("user_id" to u.s("id"))) } } } }
            val perms = j.arr("permissions")
            if (perms.isEmpty()) item { AdHint("Only you can use Nuclear.") }
            items(perms, key = { "p" + it.s("user_id") }) { p ->
                AdRow(p.s("nickname"), sub = p.s("granted_by") + " · " + adWhen(p.s("created_at")), letter = p.s("nickname").ifBlank { "?" }, trail = "Revoke") {
                    a.sure("Revoke?", "Remove Nuclear access?") { adminDo(c, scope, "✓ Access revoked", { tick++ }) { Api.delete("/api/admin/nuclear?permissions=1&id=" + adEnc(p.s("user_id"))) } }
                }
            }
        }
        AdDialogs(a)
    }
}

/** Features extras (buildFeaturesPanel): auto-moderation threshold + database optimize. */
class AdFeaturesMorePage : PageScreen() {
    override val perfName = "AdFeaturesMore"
    override fun equals(other: Any?) = other is AdFeaturesMorePage
    override fun hashCode() = 3514
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var tick by remember { mutableIntStateOf(0) }
        val res = adRes("/api/settings", tick)
        val opt = adRes("/api/admin/optimize", tick)
        var th by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }; var out by remember { mutableStateOf<String?>(null) }
        AdPanel("Auto-moderation & speed", res = res) { j ->
            if (th == null && j.has("settings")) th = (j.optJSONObject("settings") ?: JSONObject()).s("automod_threshold").ifBlank { "0" }
            adCard("🛡️ Auto-moderation", hint = "Hide a post automatically after this many reports (0 = off).") {
                AdField(th ?: "", { th = it.filter(Char::isDigit) }, "Reports before auto-hide", Modifier.firstFocus(), number = true)
                AdButton("Save") { val v = ((th ?: "0").toIntOrNull() ?: 0).coerceAtLeast(0); adminDo(c, scope, if (v > 0) "🛡️ Auto-hide after $v reports" else "Auto-moderation off", { tick++ }) { Api.put("/api/settings", jo("key" to "automod_threshold", "value" to v.toString())) } }
            }
            adCard("⚡ Database speed", hint = "Adds the indexes the site needs to stay fast as it grows.") {
                opt.data?.let { o -> AdHint(o.n("indexes").toString() + " indexes active") }
                AdButton(if (busy) "⚡ Optimizing…" else "⚡ Optimize database", enabled = !busy) {
                    busy = true
                    scope.launch { runCatching { Api.post("/api/admin/optimize", JSONObject()) }.onSuccess { r -> toast(c, "⚡ " + r.n("created") + " indexes ready"); out = "✅ " + r.n("created") + " indexes active" + (if (r.n("skipped") > 0) " (" + r.n("skipped") + " skipped — columns not present)" else "") + ". The app is now optimized for scale." }.onFailure { toast(c, "❌ " + Api.friendly(it)) }; busy = false }
                }
                out?.let { AdHint(it, AdGreen) }
            }
        }
    }
}
