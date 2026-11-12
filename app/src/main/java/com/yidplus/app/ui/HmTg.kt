package com.yidplus.app.ui

import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yidplus.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.composed

// ───────────────────────── HM: Telegram post extras — formatting (entities), albums, day chips, link cards ─────────────────────────

/** The fields of a Telegram post the shared TgPost model doesn't keep: entities (bold, links…), album id, audio cover. */
data class HmTgX(val entities: String, val grouped: String, val thumb: String?)
object HmTgExtras {
    private val map = java.util.concurrent.ConcurrentHashMap<String, HmTgX>()
    fun put(user: String, j: JSONObject) {
        val id = j.optLong("tg_msg_id"); if (id == 0L) return
        val ent = j.opt("entities")?.let { if (it is JSONArray) it.toString() else it.toString() }.orEmpty().let { if (it == "null") "" else it }
        map["$user|$id"] = HmTgX(ent, j.str("grouped_id").let { if (it == "null" || it == "0") "" else it }, Api.abs(j.strOrNull("media_thumb")))
    }
    fun of(user: String, id: Long): HmTgX? = map["$user|$id"]
    /** Adds the extra fields back onto a post's saved copy. */
    fun into(user: String, id: Long, j: JSONObject): JSONObject { of(user, id)?.let { x -> j.put("entities", x.entities).put("grouped_id", x.grouped); x.thumb?.let { j.put("media_thumb", it) } }; return j }
}

private val hmUrlRe = Regex("""https?://[^\s<]+""")
private fun hmIsTme(u: String) = Regex("""(?i)^https?://(t|telegram)\.me/""").containsMatchIn(u)

/** The site's _tgFormat: Telegram entities → styled text (bold, italic, underline, strike, code, pre, quote, spoiler, links, @mentions, #tags).
 *  t.me links are shown as plain text, like the site. A spoiler stays hidden until [revealed]. */
fun hmTgFormat(text: String, entities: String, link: Color, spoilerBg: Color, revealed: Boolean): AnnotatedString {
    val ents = runCatching { JSONArray(entities.ifBlank { "[]" }).objects() }.getOrDefault(emptyList())
    return buildAnnotatedString {
        append(text)
        if (ents.isEmpty()) {
            hmUrlRe.findAll(text).forEach { m -> val u = m.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']')
                if (!hmIsTme(u)) addLink(LinkAnnotation.Url(u, TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline))), m.range.first, m.range.first + u.length) }
            Regex("""(^|\s)([@#][^\s<]+)""").findAll(text).forEach { m -> val g = m.groups[2]!!
                if (g.value.startsWith("#")) hmTagLink(this, g.value, link, g.range.first, g.range.last + 1) else addStyle(SpanStyle(color = link), g.range.first, g.range.last + 1) }
            return@buildAnnotatedString
        }
        for (e in ents) {
            val a = e.optInt("offset", -1); val b = a + e.optInt("length", 0)
            if (a < 0 || b <= a || b > text.length) continue
            when (e.str("_")) {
                "messageEntityBold" -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), a, b)
                "messageEntityItalic" -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), a, b)
                "messageEntityUnderline" -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), a, b)
                "messageEntityStrike" -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), a, b)
                "messageEntityCode", "messageEntityPre" -> addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = spoilerBg.copy(alpha = .12f)), a, b)
                "messageEntityBlockquote" -> addStyle(SpanStyle(fontStyle = FontStyle.Italic, color = link), a, b)
                "messageEntitySpoiler" -> if (!revealed) addStyle(SpanStyle(color = spoilerBg, background = spoilerBg), a, b)
                "messageEntityTextUrl" -> { val u = e.str("url"); if (u.isNotBlank() && !hmIsTme(u)) addLink(LinkAnnotation.Url(u, TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline))), a, b) }
                "messageEntityUrl" -> { val u = text.substring(a, b).trim(); if (!hmIsTme(u)) addLink(LinkAnnotation.Url(if (u.startsWith("http")) u else "https://$u", TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline))), a, b) }
                "messageEntityHashtag" -> hmTagLink(this, text.substring(a, b), link, a, b)
                "messageEntityMention", "messageEntityCashtag", "messageEntityBotCommand" -> addStyle(SpanStyle(color = link), a, b)
            }
        }
    }
}
/** A #hashtag that opens YID PLUS search for it (the site's searchHashtag). */
fun hmTagLink(b: AnnotatedString.Builder, tag: String, link: Color, start: Int, end: Int) {
    b.addLink(LinkAnnotation.Clickable("tag:$tag", TextLinkStyles(SpanStyle(color = link))) { Nav.open(SoSearchPage(q0 = tag)) }, start, end)
}
fun hmHasSpoiler(entities: String) = entities.contains("messageEntitySpoiler")
/** The first link worth a card (not t.me, not a YID PLUS chat link — the site skips those). */
fun hmCardUrl(text: String): String? = hmUrlRe.findAll(text).map { it.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']') }
    .firstOrNull { !hmIsTme(it) && !Regex("""(?i)yidplus\.com/(chat|invite)|/chat\?join=""").containsMatchIn(it) }

/** "Today", "Yesterday", a weekday this week, else "November 3" (the site's _dateLabel). */
fun hmDayLabel(at: String?): String {
    val t = parseTime(at) ?: return ""
    val cal = java.util.Calendar.getInstance(); cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
    val today = cal.timeInMillis
    return when {
        t >= today -> "Today"
        t >= today - 86_400_000L -> "Yesterday"
        t >= today - 6 * 86_400_000L -> java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(java.util.Date(t))
        else -> java.text.SimpleDateFormat("MMMM d", java.util.Locale.US).format(java.util.Date(t))
    }
}
fun hmDayKey(at: String?): String = parseTime(at)?.let { java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date(it)) } ?: ""

/** A centered day chip between posts, like Telegram. */
@Composable
fun HmDayChip(label: String) {
    if (label.isBlank()) return
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .9f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Posts of one Telegram album shown as one grid (the site's _tgAlbumCard). */
@Composable
fun HmAlbumGrid(items: List<TgPost>, onOpen: (TgPost) -> Unit) {
    val shown = items.filter { it.isPhoto || it.isVideo }.take(10)
    if (shown.isEmpty()) return
    val rows = if (shown.size <= 2) listOf(shown) else shown.chunked(if (shown.size == 4) 2 else 3)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { p ->
                    Box(Modifier.weight(1f).aspectRatio(if (row.size == 1) 1.4f else 1f).background(MaterialTheme.colorScheme.surfaceVariant).tap(RoundedCornerShape(0.dp)) { onOpen(p) }) {
                        AsyncImage(p.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        if (p.isVideo) Box(Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = .5f)), contentAlignment = Alignment.Center) {
                            Icon(Sym.PlayArrow, "Play", tint = Color.White) }
                        if (p.isVideo && p.duration > 0) Text(dur(p.duration), Modifier.align(Alignment.BottomStart).padding(4.dp).background(Color.Black.copy(alpha = .55f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp),
                            color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** Groups newest-first posts into display units: an album (same grouped_id, next to each other) becomes one unit. */
fun hmGroupPosts(user: String, posts: List<TgPost>): List<List<TgPost>> {
    val out = ArrayList<List<TgPost>>(); var i = 0
    while (i < posts.size) {
        val g = HmTgExtras.of(user, posts[i].id)?.grouped.orEmpty()
        if (g.isNotEmpty()) { var j = i + 1; while (j < posts.size && HmTgExtras.of(user, posts[j].id)?.grouped == g) j++
            out.add(posts.subList(i, j).sortedBy { it.id }); i = j } else { out.add(listOf(posts[i])); i++ }
    }
    return out
}


/** Like Common.kt's tap() (same keypad focus ring), plus long-press — and the keypad Menu key — for the item's menu (WhatsApp/Telegram long-press). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.hmTapLong(shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(10.dp), onLong: () -> Unit, onClick: () -> Unit): Modifier = composed {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    this
        .clip(shape)
        .then(if (focused && Keys.keypad) Modifier.background(Gold.copy(alpha = 0.18f)).border(2.dp, Gold, shape) else Modifier)
        .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MENU) { onLong(); true } else false }
        .combinedClickable(interactionSource = src, indication = androidx.compose.material3.ripple(), onLongClick = onLong, onClick = onClick)
}
