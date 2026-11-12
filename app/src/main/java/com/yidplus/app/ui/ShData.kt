package com.yidplus.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yidplus.app.data.Api
import com.yidplus.app.data.Clip
import com.yidplus.app.data.objects
import com.yidplus.app.data.str
import com.yidplus.app.data.strOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.text.Normalizer

// Shorts 2.1 (helper SH): the site's js/src-shorts.js rules, on the phone. Nothing here is sent anywhere
// except the same reads the site makes (/api/src-shorts?cr=, ?creators=1) and likes (/api/src-react).

/** A small JSON file in the app's files dir (follows, seen keys, profile, queue). */
internal object ShStore {
    private var dir: File? = null
    fun init(c: Context) { if (dir == null) dir = File(c.filesDir, "sh").apply { mkdirs() } }
    fun read(name: String): JSONObject? = runCatching { dir?.let { File(it, "$name.json") }?.takeIf { it.exists() }?.readText()?.let { JSONObject(it) } }.getOrNull()
    fun write(name: String, j: JSONObject) {
        val d = dir ?: return
        val text = j.toString()
        Thread { runCatching { val t = File(d, "$name.tmp"); t.writeText(text); t.renameTo(File(d, "$name.json")) } }.start()
    }
}

/**
 * "No video twice" (site: vidKeys / isDup / markShown). A video shown on this phone in the last 3 days never
 * comes back in For You or Following, not from the other source, not from another creator, not re-posted
 * as a new file: same file name, same poster, same byte size, same caption with the length ±1 s, or the same
 * creator name on the other source with the same length in the same 15 minutes.
 */
object ShKeys {
    private const val KEEP = 3L * 86_400_000L
    private val vk = HashMap<String, Long>()          // key → time shown (kept 3 days, ≤ 6,000)
    private val session = HashSet<String>()           // keys already in this visit's feeds
    private var loaded = false
    var dups by mutableIntStateOf(0)
    private val stop = setOf("status", "statuses", "news", "official", "channel", "the", "and", "jewish", "yid", "live", "update", "updates", "media",
        "\u05e0\u05d9\u05d9\u05e2\u05e1", "\u05e1\u05d8\u05d0\u05d8\u05d5\u05e1", "\u05e1\u05d8\u05d0\u05d8\u05d5\u05e1\u05df")

    fun load() {
        if (loaded) return; loaded = true
        val j = ShStore.read("vk") ?: return
        val old = System.currentTimeMillis() - KEEP
        j.keys().forEach { k -> val t = j.optLong(k); if (t > old) vk[k] = t }
    }
    private fun save() {
        val j = JSONObject(); synchronized(vk) { vk.forEach { (k, v) -> j.put(k, v) } }; ShStore.write("vk", j)
    }
    fun fileKey(u: String?): String {
        val p = (u ?: "").substringBefore('?').substringBefore('#').substringAfterLast('/').lowercase()
        return p.replace(Regex("\\.(mp4|mov|m4v|webm|3gp)$"), "")
            .replace(Regex("([_-](\\d{3,4}p|\\d{2,4}x\\d{2,4}|small|medium|large|hd|sd|compressed|web))+$"), "")
    }
    fun capKey(s: String?): String = (s ?: "").replace(Regex("https?://\\S+"), "").lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "").take(160)
    fun nameWords(n: String?): List<String> {
        val t = Normalizer.normalize((n ?: "").lowercase(), Normalizer.Form.NFKD).replace(Regex("[\\u0591-\\u05c7]"), "")
        val out = ArrayList<String>()
        Regex("[a-z0-9]+|[\\u05d0-\\u05ea]+").findAll(t).forEach { m -> val w = m.value; if (w.length >= 3 && w !in stop && w !in out) out.add(w) }
        return out
    }
    fun keys(s: Clip, probe: Boolean): List<String> {
        val ks = ArrayList<String>()
        val d = s.duration; val f = fileKey(s.video); val cp = capKey(s.caption)
        if (f.length >= 8) ks.add("m:$f")
        val pf = fileKey(s.poster); if (pf.length >= 8) ks.add("p:$pf")
        if (s.size > 50_000) ks.add("z:${s.size}")
        if (d > 0 && cp.length >= 6) { ks.add("c:$d|$cp"); if (probe) { ks.add("c:${d - 1}|$cp"); ks.add("c:${d + 1}|$cp") } }
        if (d > 0) {
            val src = s.key.take(1); val other = if (src == "1") "2" else "1"
            val b = (parseTime(s.at) ?: 0L) / 900_000L
            for (w in nameWords(s.creator)) {
                if (probe) { for (dd in d - 1..d + 1) for (bb in b - 1..b + 1) ks.add("x:$other|$dd|$w|$bb") }
                else ks.add("x:$src|$d|$w|$b")
            }
        }
        return ks
    }
    /** True when this video (or a copy) is already in this visit's feeds, or was on screen in the last 3 days (unless [creatorPage]). */
    fun isDup(s: Clip, creatorPage: Boolean = false): Boolean {
        load()
        val own = keys(s, false); val probe = keys(s, true) + own
        val old = System.currentTimeMillis() - KEEP
        // a creator's own page shows every video of theirs (also ones already in For You or seen); its list dedupes by id itself
        if (creatorPage) return false
        for (k in probe) { if (k in session || (vk[k] ?: 0L) > old) { dups++; return true } }
        session.addAll(own)
        return false
    }
    fun seenBefore(s: Clip): Boolean { load(); val old = System.currentTimeMillis() - KEEP; return keys(s, false).any { (vk[it] ?: 0L) > old } }
    private var unsaved = 0
    /** The video is on screen now: remember its keys for 3 days. */
    fun shown(s: Clip) {
        load()
        val now = System.currentTimeMillis()
        synchronized(vk) {
            keys(s, false).forEach { vk[it] = now }
            if (vk.size > 6000) vk.entries.sortedBy { it.value }.take(vk.size - 5000).forEach { vk.remove(it.key) }
        }
        if (++unsaved >= 3) { unsaved = 0; save() }
    }
    fun flush() { if (loaded) save() }
    fun resetSession() { session.clear() }
    fun resetAll() { vk.clear(); session.clear(); save() }
}

/** For You learns what this phone likes (site: learn / score / rank). Lives only on the phone. */
object ShRank {
    private var c = HashMap<String, Double>(); private var t = HashMap<String, Double>(); var n = 0; private var at = System.currentTimeMillis()
    private val watched = HashMap<String, Long>()
    private var loaded = false
    fun load() {
        if (loaded) return; loaded = true
        ShStore.read("prof")?.let { j ->
            j.optJSONObject("c")?.let { o -> o.keys().forEach { k -> c[k] = o.optDouble(k) } }
            j.optJSONObject("t")?.let { o -> o.keys().forEach { k -> t[k] = o.optDouble(k) } }
            n = j.optInt("n"); at = j.optLong("at", System.currentTimeMillis())
            j.optJSONObject("w")?.let { o -> o.keys().forEach { k -> watched[k] = o.optLong(k) } }
        }
        val days = (System.currentTimeMillis() - at) / 86_400_000.0
        if (days > 0.5) { val f = Math.pow(0.95, days); for (m in listOf(c, t)) { m.keys.toList().forEach { k -> val v = m[k]!! * f; if (Math.abs(v) < 0.05) m.remove(k) else m[k] = v } }; at = System.currentTimeMillis() }
    }
    fun save() {
        val j = JSONObject().put("c", JSONObject(c as Map<*, *>)).put("t", JSONObject(t as Map<*, *>)).put("n", n).put("at", at).put("w", JSONObject(watched as Map<*, *>))
        ShStore.write("prof", j)
    }
    fun creator(k: String) = c[k] ?: 0.0
    fun learn(s: Clip?, w: Double) {
        if (s == null || w == 0.0) return; load()
        c[s.creatorId] = ((c[s.creatorId] ?: 0.0) + w).coerceIn(-12.0, 25.0)
        (SoShortTopics.byKey[s.key] ?: emptyList()).forEach { x -> t[x] = ((t[x] ?: 0.0) + w * 0.4).coerceIn(-8.0, 15.0) }
        n++
        if (c.size > 400) c.entries.sortedBy { Math.abs(it.value) }.take(100).forEach { c.remove(it.key) }
        save()
    }
    fun topic(x: String, w: Double) { load(); t[x] = ((t[x] ?: 0.0) + w).coerceAtMost(15.0); save() }
    fun follow(k: String, on: Boolean) { load(); c[k] = if (on) ((c[k] ?: 0.0) + 5).coerceAtMost(25.0) else (c[k] ?: 0.0) - 5; save() }
    fun watchedOnce(key: String) { watched[key] = System.currentTimeMillis(); if (watched.size > 3000) watched.entries.sortedBy { it.value }.take(800).forEach { watched.remove(it.key) } }
    fun isWatched(key: String) = key in watched
    private fun score(s: Clip, now: Long): Double {
        var v = creator(s.creatorId)
        (SoShortTopics.byKey[s.key] ?: emptyList()).forEach { x -> v += (t[x] ?: 0.0) * 0.6 }
        if (s.creatorId in Feeds.follows) v += 6
        v -= Math.max(0.0, (now - (parseTime(s.at) ?: now)) / 3_600_000.0) * 0.08
        if (isWatched(s.key)) v -= 40
        return v
    }
    /** One loaded batch: best first, ~1 in 5 a random pick (exploration), never the same creator twice in a row. */
    fun rank(items: List<Clip>): List<Clip> {
        load()
        if (items.size < 3 || n < 3) return items.filter { !isWatched(it.key) } + items.filter { isWatched(it.key) }
        val now = System.currentTimeMillis()
        val pool = items.map { it to score(it, now) + Math.random() * 0.6 }.sortedByDescending { it.second }.toMutableList()
        val out = ArrayList<Clip>(); var last = ""
        while (pool.isNotEmpty()) {
            var k = 0
            if (Math.random() < 0.2 && pool.size > 3) k = 1 + (Math.random() * (pool.size - 1)).toInt()
            if (pool[k].first.creatorId == last) { val j = pool.indexOfFirst { it.first.creatorId != last }; if (j >= 0) k = j }
            val x = pool.removeAt(k).first; out.add(x); last = x.creatorId
        }
        return out
    }
    fun reset() { c.clear(); t.clear(); n = 0; watched.clear(); at = System.currentTimeMillis(); save() }
}

/** One creator (both sources merged when the name is the same): keys, name, avatar, verified. */
data class ShCreator(val keys: List<String>, val name: String, val avatar: String?, val verified: Boolean, val q: String, val score: Double, val live: Boolean)

/** Follows (on the phone only, like the site), the creators directory, and one creator's own videos. */
object ShDir {
    /** creator key → name, avatar, verified, followed-at. */
    val meta = HashMap<String, JSONObject>()
    var dir by mutableStateOf<Map<String, JSONObject>?>(null)
    var dirLoading by mutableStateOf(false)
    private var loadedMeta = false
    fun loadMeta() {
        if (loadedMeta) return; loadedMeta = true
        ShStore.read("follow")?.let { j -> j.keys().forEach { k -> j.optJSONObject(k)?.let { meta[k] = it } } }
        // follows from 2.0.x (key only) keep working
        Feeds.follows.forEach { k -> if (k !in meta) meta[k] = JSONObject().put("n", "").put("at", 0) }
        meta.keys.forEach { k -> if (k !in Feeds.follows) Feeds.follows.add(k) }
    }
    private fun saveMeta() { val j = JSONObject(); Feeds.follows.forEach { k -> meta[k]?.let { j.put(k, it) } }; ShStore.write("follow", j) }
    fun info(k: String): Triple<String, String?, Boolean>? {
        known[k]?.let { return it }
        val j = meta[k]?.takeIf { it.str("n").isNotBlank() } ?: dir?.get(k) ?: return null
        return Triple(j.str("n"), j.strOrNull("a"), j.optInt("v") == 1)
    }
    /** names seen in loaded feeds this visit */
    val known = HashMap<String, Triple<String, String?, Boolean>>()
    fun learnCreators(j: JSONObject) {
        val cr = j.optJSONObject("creators") ?: return
        cr.keys().forEach { k -> cr.optJSONObject(k)?.let { o -> known[k] = Triple(o.str("n"), o.strOrNull("a"), o.optInt("v") == 1)
            meta[k]?.let { m -> if (k in Feeds.follows) { m.put("n", o.str("n")); m.put("a", o.str("a")) } } } }
    }
    fun isFollowed(keys: List<String>) = keys.any { it in Feeds.follows }
    /** Follow or unfollow every key of one creator. Returns the new state. */
    fun setFollow(ctx: Context, keys: List<String>, on: Boolean, name: String = "", avatar: String? = null, verified: Boolean = false, quiet: Boolean = false): Boolean {
        loadMeta()
        for (k in keys) {
            if (on && k !in Feeds.follows) {
                val i = info(k)
                meta[k] = JSONObject().put("n", i?.first ?: name).put("a", i?.second ?: avatar ?: "").put("v", if (i?.third ?: verified) 1 else 0).put("at", System.currentTimeMillis())
                Feeds.follows.add(k); ShRank.follow(k, true)
            } else if (!on && k in Feeds.follows) { Feeds.follows.remove(k); meta.remove(k); ShRank.follow(k, false) }
        }
        saveMeta(); Feeds.persist(ctx)
        if (!quiet) toast(ctx, if (on) "Following ${name.ifBlank { info(keys.first())?.first ?: "" }}".trim() else "Unfollowed ${name.ifBlank { info(keys.first())?.first ?: "" }}".trim())
        return on
    }
    fun followedAt(k: String) = meta[k]?.optLong("at") ?: 0L

    /** Every creator on both sources (site: /api/src-shorts?creators=1, edge-cached; names + avatars only). */
    suspend fun loadDir() {
        if (dir != null || dirLoading) return
        dirLoading = true
        try {
            val cached = withContext(Dispatchers.IO) { com.yidplus.app.data.Disk.read("sh-creators") }
            if (cached != null) dir = parseDir(cached)
            val j = Api.get("/api/src-shorts?creators=1")
            if (j.optBoolean("ok") && j.optJSONObject("creators") != null) { dir = parseDir(j); withContext(Dispatchers.IO) { com.yidplus.app.data.Disk.write("sh-creators", j) } }
        } catch (_: Exception) { } finally { dirLoading = false }
    }
    private fun parseDir(j: JSONObject): Map<String, JSONObject> { val o = j.optJSONObject("creators") ?: return emptyMap(); val m = LinkedHashMap<String, JSONObject>(); o.keys().forEach { k -> o.optJSONObject(k)?.let { m[k] = it } }; return m }

    fun norm(n: String) = n.lowercase().replace(Regex("[\\u0591-\\u05c7]"), "").replace(Regex("[^\\p{L}\\p{N}]+"), "")
    /** The list to follow: same name on the other source = one row; most watched here, posting now, verified, A→Z, numbers last. */
    fun groups(): List<ShCreator> {
        loadMeta()
        val src = LinkedHashMap<String, Triple<String, String?, Boolean>>()
        fun add(k: String, n: String, a: String?, v: Boolean) { if (n.isNotBlank() && k !in Feeds.hidden && k !in src) src[k] = Triple(n, a, v) }
        known.forEach { (k, x) -> add(k, x.first, x.second, x.third) }
        dir?.forEach { (k, o) -> add(k, o.str("n"), o.strOrNull("a"), o.optInt("v") == 1) }
        meta.forEach { (k, o) -> add(k, o.str("n"), o.strOrNull("a"), o.optInt("v") == 1) }
        val g = LinkedHashMap<String, MutableList<String>>()
        for ((k, x) in src) {
            val q = norm(x.first); var gk = (q.ifBlank { k }) + "|"
            val ex = g[gk]
            if (ex != null && ex.any { it.take(1) == k.take(1) }) gk += k
            g.getOrPut(gk) { ArrayList() }.add(k)
        }
        val out = g.values.map { ks ->
            val x = src[ks.first()]!!
            ShCreator(ks, x.first, ks.firstNotNullOfOrNull { src[it]?.second?.takeIf { a -> a.isNotBlank() && !ShAvatars.bad(a) } }, ks.any { src[it]!!.third }, norm(x.first),
                ks.maxOf { ShRank.creator(it) }.coerceAtLeast(0.0), ks.any { it in known })
        }
        return out.sortedWith(compareByDescending<ShCreator> { it.score }.thenByDescending { it.live }.thenByDescending { it.verified }
            .thenBy { if (it.q.isBlank() || it.q.first().isDigit()) 1 else 0 }.thenBy { it.q })
    }
    fun groupOf(k: String): List<String> { val gs = groups(); return gs.firstOrNull { k in it.keys }?.keys ?: listOf(k) }

    /** One creator's own videos, newest first, in one request (site: crFetch). Nothing written anywhere. */
    suspend fun fetchCreator(k: String, name: String): JSONObject? = runCatching {
        Api.get("/api/src-shorts?cr=" + URLEncoder.encode(k, "UTF-8") + (if (name.isNotBlank()) "&q=" + URLEncoder.encode(name, "UTF-8") else "")).takeIf { it.optBoolean("ok") }
    }.getOrNull()
}

/** Avatars that answer with an error page (one source sends 384 of them) show the letter instead. */
object ShAvatars {
    private val badSet = HashSet<String>()
    fun bad(u: String) = u in badSet
    fun mark(u: String) { badSet.add(u) }
}

/** A list of videos for one mode (Following, a creator's page): loaded from ?cr= answers. */
class ShList {
    val items = mutableStateListOf<Clip>()
    var loading by mutableStateOf(false)
    var done by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var key by mutableStateOf("")
    var at = 0L
    private val ids = HashSet<String>()
    fun clear() { items.clear(); ids.clear(); done = false; failed = false; key = ""; at = 0L }
    fun add(list: List<Clip>) { list.forEach { if (ids.add(it.key)) items.add(it) } }

    /** A creator's page: every key of the creator, merged, newest first, each video once (seen before is fine here). */
    suspend fun loadWho(keys: List<String>, name: String) {
        val k0 = keys.joinToString(",")
        if (key == k0 && (loading || (done && System.currentTimeMillis() - at < 600_000))) return
        clear(); key = k0; loading = true
        try {
            val rs = coroutineScope { keys.map { k -> async { ShDir.fetchCreator(k, ShDir.info(k)?.first ?: name) } }.awaitAll() }
            if (key != k0) return
            val got = ArrayList<Clip>()
            rs.filterNotNull().forEach { j -> SoShortTopics.learn(j); ShDir.learnCreators(j); got.addAll(Clip.srcPage(j)) }
            failed = rs.all { it == null }
            val fresh = got.filter { it.creatorId !in Feeds.hidden && !ShKeys.isDup(it, creatorPage = true) }
            add(fresh.sortedByDescending { it.at ?: "" })
        } finally { if (key == k0) { loading = false; done = true; at = System.currentTimeMillis() } }
    }

    /** Following: each followed creator's recent videos, 4 requests at a time (newest followed first, up to 40). */
    suspend fun loadFollowing(fromFeed: List<Clip>) {
        ShDir.loadMeta()
        val ks = Feeds.follows.toList().sortedByDescending { ShDir.followedAt(it) }.take(40)
        val k0 = "F:" + ks.sorted().joinToString(",")
        if (key == k0 && (loading || (done && System.currentTimeMillis() - at < 300_000))) { addFeed(fromFeed); return }
        clear(); key = k0; loading = true
        try {
            addFeed(fromFeed)
            val sem = Semaphore(4)
            coroutineScope {
                ks.map { k -> async { sem.withPermit {
                    val j = ShDir.fetchCreator(k, ShDir.info(k)?.first ?: "")
                    if (j != null && key == k0) {
                        SoShortTopics.learn(j); ShDir.learnCreators(j)
                        val got = Clip.srcPage(j).filter { it.creatorId !in Feeds.hidden && !ShKeys.seenBefore(it) }
                        // newest first overall; what is already on screen stays where it is
                        val cur = items.size
                        add(got)
                        if (items.size > cur) { val head = items.take(cur); val tail = items.drop(cur).sortedByDescending { it.at ?: "" }; items.clear(); items.addAll(head + tail) }
                    }
                } } }.awaitAll()
            }
        } finally { if (key == k0) { loading = false; done = true; at = System.currentTimeMillis() } }
    }
    fun addFeed(fromFeed: List<Clip>) { add(fromFeed.filter { it.creatorId in Feeds.follows }) }
}

/** Likes that the server asked to hold ("queued", or a server error) wait on the phone and are sent later (site: queue / flushQueue). */
object ShLikeQueue {
    private val q = ArrayList<Pair<String, Boolean>>()
    private var loaded = false
    private var busy = false
    fun load() { if (loaded) return; loaded = true; ShStore.read("likeq")?.optJSONArray("q").objects().forEach { q.add(it.str("vid") to it.optBoolean("like")) } }
    private fun save() { val a = JSONArray(); q.forEach { a.put(JSONObject().put("vid", it.first).put("like", it.second)) }; ShStore.write("likeq", JSONObject().put("q", a)) }
    fun add(vid: String, on: Boolean) { load(); q.removeAll { it.first == vid }; q.add(vid to on); while (q.size > 200) q.removeAt(0); save() }
    val size get() = q.size
    /** Sends up to 10; stops at the first one the server still holds. */
    suspend fun flush() {
        load(); if (q.isEmpty() || busy || com.yidplus.app.data.Session.me == null) return
        busy = true
        try {
            for (x in q.take(10)) {
                val ok = runCatching { Api.post("/api/src-react", JSONObject().put("vid", x.first).put("like", x.second)) }.getOrNull()?.let { it.optBoolean("ok") && !it.optBoolean("queued") } ?: false
                if (!ok) break
                q.remove(x); save()
            }
        } finally { busy = false }
    }
}

/** Uploads extras the shared Clip has no room for: views and the account's own Saved flag (site: s.views, s.saved). */
object ShMeta {
    val views = HashMap<String, Int>()
    val serverSaved = HashSet<String>()
    fun learn(j: JSONObject) {
        j.optJSONArray("shorts").objects().forEach { s -> val id = s.str("id"); views[id] = s.optInt("views"); if (s.optBoolean("saved")) serverSaved.add(id) }
    }
}
