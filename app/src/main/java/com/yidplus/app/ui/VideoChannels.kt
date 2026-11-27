package com.yidplus.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yidplus.app.data.*
import kotlinx.coroutines.launch
import org.json.JSONObject

/** One creator of the Status video sources that feed Shorts (the site lists each one as a "video channel" in its Channels tab). */
data class SoVideoCh(val key: String, val name: String, val avatar: String?, val verified: Boolean)

/** The site's creator directory: /api/src-shorts?creators -> {creators:{key:{n,a,v}}}. */
fun soVideoChannels(j: JSONObject): List<SoVideoCh> {
    val cr = j.optJSONObject("creators") ?: return emptyList()
    return cr.keys().asSequence().mapNotNull { k ->
        val o = cr.optJSONObject(k) ?: return@mapNotNull null
        val n = o.optString("n", "")
        if (n.isBlank()) null else SoVideoCh(k, n, o.optString("a", "").ifBlank { null }, o.optInt("v") == 1 || o.optBoolean("v"))
    }.toList()
}

/** Newest upload per creator from the recent feed (/api/src-shorts?n=60), used to order the list like the site does. */
fun soVideoRecent(j: JSONObject): Map<String, String> {
    val m = HashMap<String, String>()
    val a = j.optJSONArray("items") ?: return m
    for (i in 0 until a.length()) { val it = a.optJSONObject(i) ?: continue; val t = it.optString("t", ""); val k = it.optString("cr", "")
        if (t.isNotEmpty() && k.isNotEmpty() && (m[k] ?: "") < t) m[k] = t }
    return m
}

/** Channels > Videos: every Status video creator as a channel row (photo, name, verified, "Videos"); a row opens that creator's videos. */
@Composable
fun SoVideoChannelsList(query: String) {
    val scope = rememberCoroutineScope()
    val res = remember(Session.me?.id) { Store.res("/api/src-shorts?creators", ::soVideoChannels) }
    val rec = remember(Session.me?.id) { Store.res("/api/src-shorts?n=60", ::soVideoRecent) }
    LaunchedEffect(res, rec, Net.online, SoRefresh.tick) {
        res.load(force = (Net.online && res.offline) || SoRefresh.tick > 0)
        rec.load(force = SoRefresh.tick > 0)
    }
    val all = res.data
    val recent = rec.data.orEmpty()
    val q = query.trim()
    when {
        all == null && res.error != null -> Problem(res.error!!, res.offline, { scope.launch { res.load(true) } })
        all == null -> SoSkeleton()
        else -> {
            // Newest upload first, then everyone else A to Z (the site's order).
            val sorted = remember(all, recent) { all.sortedWith(compareByDescending<SoVideoCh> { recent[it.key] ?: "" }.thenBy { it.name.lowercase() }) }
            val list = if (q.isEmpty()) sorted else sorted.filter { it.name.contains(q, true) }
            val shown = list.take(60)
            if (list.isEmpty()) SoEmpty(Sym.Search, if (q.isNotEmpty()) "No video channels for \u201c$q\u201d" else "No video channels yet")
            else LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text("${list.size} video channels" + if (res.fromCache) " \u00b7 saved copy" else "", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(shown, key = { it.key }) { ch ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).restoreFocus("vc:" + ch.key)
                        .then(if (ch == shown.first() && q.isEmpty()) Modifier.firstFocus() else Modifier)
                        .tap(RoundedCornerShape(0.dp)) { Nav.lastOpened = "vc:" + ch.key; Nav.open(SoCreatorPage(ch.key, ch.name, ch.avatar, ch.verified, true)) }
                        .padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        SoThumb(ch.avatar, ch.name, 50.dp)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ch.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content, textAlign = TextAlign.Left),
                                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (ch.verified) Icon(Sym.Verified, "Verified", tint = BadgeGreen, modifier = Modifier.padding(start = 4.dp).size(17.dp))
                            }
                            Text("Videos", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    SoDivider(82.dp)
                }
                if (list.size > shown.size) item {
                    Text("${list.size - shown.size} more \u2014 search to find a channel", Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A creator's full video list (/api/src-shorts?cr=key), merged into the For You feed so the grid and the player share it. */
suspend fun soLoadCreatorVideos(key: String, name: String, avatar: String?, verified: Boolean) {
    val j = Api.get("/api/src-shorts?cr=" + soEnc(key))
    val a = j.optJSONArray("items") ?: return
    val have = Feeds.forYou.items.mapTo(HashSet()) { it.key }
    for (i in 0 until a.length()) {
        val it = a.optJSONObject(i) ?: continue
        val u = it.optString("u", ""); if (u.isBlank()) continue
        val id = it.optString("id", "").ifBlank { u }
        if (!have.add(id)) continue
        Feeds.forYou.items.add(Clip(id, true, u, it.optString("p", "").ifBlank { null }, it.optString("c", ""), name, key, avatar, verified,
            it.optInt("d"), it.optLong("z"), 0, 0, false, it.optString("v", "").ifBlank { id }, it.optString("t", "").ifBlank { null }))
    }
}

/** The top of the Channels tab, like the site: "Video channels" with the newest creators as a row of photos; "See all" opens the full list. */
@Composable
fun SoVideoStrip(onAll: () -> Unit) {
    val res = remember(Session.me?.id) { Store.res("/api/src-shorts?creators", ::soVideoChannels) }
    val rec = remember(Session.me?.id) { Store.res("/api/src-shorts?n=60", ::soVideoRecent) }
    LaunchedEffect(res, rec, Net.online) { res.load(force = Net.online && res.offline); rec.load() }
    val all = res.data ?: return
    if (all.isEmpty()) return
    val recent = rec.data.orEmpty()
    val top = remember(all, recent) { all.sortedWith(compareByDescending<SoVideoCh> { recent[it.key] ?: "" }.thenBy { it.name.lowercase() }).take(12) }
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).tap(RoundedCornerShape(0.dp)) { onAll() }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Sym.Movie, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text("Video channels", Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("${all.size}  See all", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 10.dp)) {
            items(top, key = { it.key }) { ch ->
                Column(Modifier.width(76.dp).tap(RoundedCornerShape(10.dp)) { Nav.open(SoCreatorPage(ch.key, ch.name, ch.avatar, ch.verified, true)) }.padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    SoThumb(ch.avatar, ch.name, 52.dp)
                    Text(ch.name, Modifier.padding(top = 4.dp), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Content))
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}
