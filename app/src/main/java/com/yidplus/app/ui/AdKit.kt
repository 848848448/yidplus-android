package com.yidplus.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yidplus.app.data.Api
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.yidplus.app.data.Res
import com.yidplus.app.data.objects
import com.yidplus.app.data.str
import com.yidplus.app.data.strOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// ═════════════════════════════════════════════════════════════════════════════
// AD 2.1 kit: the building blocks every native admin panel uses (admin.js has 45 panels; each one in Ad*.kt is a
// small screen made of these). One look everywhere: green top bar, white cards, 16 dp sides, pull to refresh,
// cached copy first (no spinner over content), D-pad focus on every control.
// ═════════════════════════════════════════════════════════════════════════════

val AdGreen = Color(0xFF1F6F5C)
val AdRed = Color(0xFFC62828)
val AdAmber = Color(0xFFB26A00)
val AdBlue = Color(0xFF185FA5)

/** GET once when the screen opens (and on every [tick] change); the saved copy shows at once. */
@Composable
fun adRes(path: String, tick: Int = 0): Res<JSONObject> {
    val res = remember(path) { Res(path) { it } }
    LaunchedEffect(path, tick) { res.load(force = true) }
    return res
}

/** Re-reads [res] every [ms] while the screen is open (the site's setInterval polls), paused when it closes. */
@Composable
fun AdPoll(res: Res<JSONObject>, ms: Long) {
    LaunchedEffect(res) { while (true) { delay(ms); runCatching { res.load(force = true) } } }
}

/** A whole admin panel: top bar, pull to refresh, a skeleton only on the very first load, the error with Retry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdPanel(title: String, sub: String? = null, res: Res<JSONObject>? = null, onRefresh: (suspend () -> Unit)? = null,
            actions: @Composable RowScope.() -> Unit = {}, body: LazyListScope.(JSONObject) -> Unit) {
    val scope = rememberCoroutineScope()
    var pulling by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(title, onBack = { Nav.back() }, sub = sub, actions = actions)
        PullToRefreshBox(isRefreshing = pulling, onRefresh = {
            scope.launch { pulling = true; runCatching { res?.load(force = true); onRefresh?.invoke() }; pulling = false }
        }, modifier = Modifier.weight(1f).fillMaxWidth()) {
            val d = if (res == null) JSONObject() else res.data
            when {
                d == null && res?.error != null -> Problem(res.error!!, res.offline, { scope.launch { res.load(true) } })
                d == null -> SkeletonRows(rows = 6)
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) { body(d) }
            }
        }
    }
}

/** A card title + white surface, like the site's .admin-card. */
fun LazyListScope.adCard(title: String?, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    item {
        if (title != null) SectionTitle(title) else Spacer(Modifier.height(12.dp))
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                if (hint != null) Text(hint, Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                content()
            }
        }
    }
}

/** Plain hint line under a card. */
@Composable
fun AdHint(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = color)
}

/** Number tiles in rows of three (the site's stats-grid). value = null shows "—". */
@Composable
fun AdStats(tiles: List<Triple<String?, String, Color?>>, perRow: Int = 3) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (v, l, col) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))
                        .padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(v ?: "—", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = col ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

fun adNum(j: JSONObject?, k: String): String? = j?.opt(k)?.let { v -> when (v) { is Number -> adminN(v.toInt()); is String -> v.ifBlank { null }; else -> null } }

/** Small bar chart (the site's growth bars): label under each bar, count above. */
@Composable
fun AdBars(points: List<Pair<String, Int>>, color: Color = AdGreen, height: Dp = 70.dp) {
    if (points.isEmpty()) return
    val max = (points.maxOf { it.second }).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(height + 30.dp), verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        points.forEach { (l, n) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (points.size <= 16) Text(if (n > 0) n.toString() else "", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Box(Modifier.fillMaxWidth(.7f).height((height.value * n / max).coerceAtLeast(2f).dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(color))
                Text(l, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** One list row: avatar (or icon), title (RTL-safe), subtitle, trailing text/tag; tap and long-press (= menu). */
@Composable
fun AdRow(title: String, sub: String? = null, photo: String? = null, letter: String? = null, icon: ImageVector? = null, iconTint: Color = AdGreen,
          trail: String? = null, tag: Pair<String, Color>? = null, sub2: String? = null, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 64.dp).then(if (onClick != null) Modifier.tap(RoundedCornerShape(0.dp), onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            icon != null -> Box(Modifier.size(40.dp).clip(CircleShape).background(iconTint.copy(alpha = .13f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp)) }
            letter != null || photo != null -> Avatar(Api.abs(photo), letter ?: title, 48.dp)
        }
        Column(Modifier.weight(1f).padding(start = if (icon != null || letter != null || photo != null) 12.dp else 0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tag != null) AdminTag(tag.first, tag.second)
                Spacer(Modifier.weight(1f, fill = true).widthIn(min = 4.dp))
                if (trail != null) Text(trail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (!sub2.isNullOrBlank()) Text(sub2, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A row of pill buttons that wraps onto two lines on a 480 px screen. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AdPills(vararg pills: Pair<String, () -> Unit>, color: Color = MaterialTheme.colorScheme.primary) {
    FlowRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pills.forEach { (t, f) -> AdminPill(t, color = if (t.startsWith("🚫") || t.startsWith("🗑") || t.startsWith("Delete") || t.startsWith("Ban") || t.startsWith("Remove") || t.startsWith("Revoke") || t.startsWith("End") || t.startsWith("Block")) AdRed else color, onClick = f) }
    }
}

/** A text field (label above, like the site's inputs). */
@Composable
fun AdField(value: String, onValue: (String) -> Unit, label: String, modifier: Modifier = Modifier, lines: Int = 1, number: Boolean = false, password: Boolean = false) {
    OutlinedTextField(value, onValue, modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), label = { Text(label) },
        singleLine = lines == 1, minLines = lines, textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = when { number -> KeyboardType.Number; password -> KeyboardType.Password; else -> KeyboardType.Text }))
}

/** A full-width button inside a card. */
@Composable
fun AdButton(text: String, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    PrimaryButton(text, onClick, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), enabled = enabled, color = if (danger) AdRed else MaterialTheme.colorScheme.primary)
}

/** Dialog state for one panel: confirm / ask text / pick one / action menu. */
class AdAsk {
    var confirm by mutableStateOf<Triple<String, String, () -> Unit>?>(null)
    var danger by mutableStateOf(true)
    var prompt by mutableStateOf<AdPromptSpec?>(null)
    var menu by mutableStateOf<Pair<String, List<Pair<String, () -> Unit>>>?>(null)
    var okText by mutableStateOf("OK")
    /** WhatsApp-style confirm: the button names the action ("Block", "Remove", "End call"), never a bare Yes. */
    fun sure(title: String, text: String, danger: Boolean = true, okLabel: String? = null, ok: () -> Unit) {
        this.danger = danger; okText = okLabel ?: adVerb(title); confirm = Triple(title, text, ok)
    }
    fun ask(title: String, label: String, value: String = "", number: Boolean = false, lines: Int = 1, ok: (String) -> Unit) { prompt = AdPromptSpec(title, label, value, number, lines, ok) }
    fun actions(title: String, vararg items: Pair<String, () -> Unit>) { menu = title to items.toList() }
}
data class AdPromptSpec(val title: String, val label: String, val value: String, val number: Boolean, val lines: Int, val ok: (String) -> Unit)

@Composable
fun rememberAsk(): AdAsk = remember { AdAsk() }

/** Draws whatever [a] is asking right now. Put it once at the end of a panel. */
@Composable
fun AdDialogs(a: AdAsk) {
    a.confirm?.let { (t, x, ok) -> SettingsConfirmDialog(t, x, a.okText, danger = a.danger, onOk = ok, onDismiss = { a.confirm = null }) }
    a.prompt?.let { p ->
        var v by remember(p) { mutableStateOf(p.value) }
        AlertDialog(onDismissRequest = { a.prompt = null }, title = { Text(p.title) },
            text = { OutlinedTextField(v, { v = it }, Modifier.fillMaxWidth().firstFocus(always = true), label = { Text(p.label) }, singleLine = p.lines == 1, minLines = p.lines,
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                keyboardOptions = KeyboardOptions(keyboardType = if (p.number) KeyboardType.Number else KeyboardType.Text)) },
            confirmButton = { TextButton({ a.prompt = null; p.ok(v) }) { Text("OK", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ a.prompt = null }) { Text("Cancel") } })
    }
    a.menu?.let { (t, items) ->
        AlertDialog(onDismissRequest = { a.menu = null }, confirmButton = { TextButton({ a.menu = null }) { Text("Close") } },
            title = { Text(t, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    items.forEachIndexed { i, (label, f) ->
                        Text(label, Modifier.fillMaxWidth().heightIn(min = 46.dp).then(if (i == 0) Modifier.firstFocus(always = true) else Modifier)
                            .tap(RoundedCornerShape(8.dp)) { a.menu = null; f() }.padding(horizontal = 8.dp, vertical = 12.dp),
                            color = if (label.startsWith("🚫") || label.startsWith("🗑") || label.contains("Delete") || label.contains("Ban")) AdRed else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge)
                    }
                }
            })
    }
}

/** Empty list line: icon + one sentence, the site's own words. */
fun LazyListScope.adEmpty(text: String, icon: ImageVector = Sym.Inbox) {
    item { EmptyNote(icon, text, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)) }
}

/** A monospace block (logs, JSON, error stacks). */
@Composable
fun AdMono(text: String, maxLines: Int = 12) {
    Text(text, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)).padding(10.dp),
        fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

fun JSONObject?.arr(vararg keys: String): List<JSONObject> {
    if (this == null) return emptyList()
    for (k in keys) optJSONArray(k)?.let { return it.objects() }
    return emptyList()
}
fun JSONObject.s(vararg keys: String): String { for (k in keys) { val v = strOrNull(k); if (!v.isNullOrBlank() && v != "null") return v }; return "" }
fun JSONObject.n(k: String): Int = optInt(k, 0)
fun JSONObject.b(k: String): Boolean = when (val v = opt(k)) { is Boolean -> v; is Number -> v.toInt() != 0; is String -> v == "1" || v == "true" || v == "on"; else -> false }
fun adEnc(s: String): String = Uri.encode(s)
fun jo(vararg kv: Pair<String, Any?>): JSONObject = JSONObject().apply { kv.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }
fun adWhen(s: String?): String = if (s.isNullOrBlank()) "" else ago(s)

/** "2026-11-12" + "18:30" in the phone's own time zone → millis (the site's datetime-local input), else null. */
fun adLocalMs(day: String, time: String): Long? {
    if (day.isBlank()) return null
    return runCatching { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).apply { isLenient = false }.parse(day.trim() + " " + time.trim().ifBlank { "09:00" })?.time }.getOrNull()
}
/** millis → ISO-8601 UTC like JavaScript's toISOString(). */
fun adIso(ms: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(ms))

/** Short status text for the site's bot/setup checks (connected / has_token / token_valid / url / errors). */
fun adStatusLine(r: JSONObject?): Pair<String, Color> {
    if (r == null) return "Checking…" to Color.Gray
    if (r.has("ok") && !r.optBoolean("ok", true)) return (r.s("error").ifBlank { "Could not check." }) to AdRed
    if (r.has("has_token") && !r.b("has_token")) return "⚠️ No token yet — paste one above and Save." to AdAmber
    if (r.has("token_valid") && !r.b("token_valid")) return "⚠️ Token invalid — paste a fresh one from BotFather." to AdRed
    if (r.has("connected")) {
        if (!r.b("connected")) return "Not connected" to AdAmber
        val bits = mutableListOf("✅ Connected")
        r.s("bot").takeIf { it.isNotBlank() }?.let { bits += "@$it" }
        r.s("url").takeIf { it.isNotBlank() }?.let { bits += it }
        if (r.n("pending_update_count") > 0) bits += r.n("pending_update_count").toString() + " pending"
        r.s("last_error_message").takeIf { it.isNotBlank() }?.let { bits += "⚠️ $it" }
        return bits.joinToString(" · ") to AdGreen
    }
    if (r.has("chat_linked")) return (if (r.b("chat_linked")) "✅ Ready" + r.s("bot").let { if (it.isNotBlank()) " · @$it" else "" } else "⚠️ Open the bot and send it /start") to (if (r.b("chat_linked")) AdGreen else AdAmber)
    return (r.s("message", "status").ifBlank { "✅ OK" }) to AdGreen
}

/** POST multipart/form-data like the site's FormData uploads (api.post(path, fd, true)). */
suspend fun adMultipart(c: android.content.Context, path: String, fields: List<Pair<String, String>>, fileField: String, file: Uri?): JSONObject {
    val mb = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
    fields.forEach { (k, v) -> mb.addFormDataPart(k, v) }
    if (file != null) {
        val type = c.contentResolver.getType(file) ?: "application/octet-stream"
        val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.contentResolver.openInputStream(file)!!.use { it.readBytes() } }
        val ext = when { type.startsWith("video") -> "mp4"; type.contains("png") -> "png"; else -> "jpg" }
        mb.addFormDataPart(fileField, "$fileField.$ext", bytes.toRequestBody(type.toMediaType()))
    }
    val body = mb.build(); val buf = okio.Buffer(); body.writeTo(buf)
    return Api.upload(path, buf.readByteArray(), body.contentType().toString())
}

/** "Remove track?" → "Remove track", "⚠️ HIDE ALL shorts?" → "Hide all shorts", long titles → "OK". */
fun adVerb(title: String): String {
    val t = title.replace(Regex("[^\\p{L}\\p{N} '’-]"), " ").trim().replace(Regex("\\s+"), " ")
    if (t.isEmpty() || t.length > 18) return "OK"
    val w = if (t == t.uppercase()) t.lowercase() else t
    return w.replaceFirstChar { it.uppercase() }
}
