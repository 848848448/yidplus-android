package com.yidplus.app.data

import android.content.Context
import android.os.Build
import com.yidplus.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Talks to yidplus.com's own JSON API (the same one the website uses), signed in with the site's session cookie. */
object Api {
    const val BASE = "https://yidplus.com"
    val UA = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) YIDPLUS-Android/${BuildConfig.VERSION_NAME}"
    lateinit var app: Context; private set
    private lateinit var jar: PrefsCookieJar
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(jar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", UA).build()) }
            .build()
    }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        jar = PrefsCookieJar(app)
        Disk.init(app)
        Fixture.init(app)
    }

    fun hasSession() = Fixture.on || jar.has("yp_session")
    fun session(): String? = jar.value("yp_session")
    fun setSession(token: String) = jar.put("yp_session", token)
    fun clearSession() = jar.clear()

    fun abs(u: String?): String? {
        if (u.isNullOrBlank()) return null
        if (u.startsWith("http://") || u.startsWith("https://")) return u
        if (u.startsWith("file:") || u.startsWith("content:")) return u
        if (u.startsWith("//")) return "https:$u"
        return BASE + (if (u.startsWith("/")) u else "/$u")
    }

    class ApiError(val code: Int, msg: String, val body: JSONObject? = null) : IOException(msg)

    suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        exec(Request.Builder().url(abs(path)!!).header("Accept", "application/json").build())
    }

    suspend fun send(method: String, path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val rb: RequestBody? = body?.toString()?.toRequestBody(JSON) ?: if (method == "GET" || method == "DELETE") null else "{}".toRequestBody(JSON)
        exec(Request.Builder().url(abs(path)!!).method(method, rb).header("Accept", "application/json").build())
    }

    suspend fun post(path: String, body: JSONObject? = null) = send("POST", path, body)
    suspend fun put(path: String, body: JSONObject? = null) = send("PUT", path, body)
    suspend fun delete(path: String) = send("DELETE", path, null)

    suspend fun upload(path: String, bytes: ByteArray, contentType: String): JSONObject = withContext(Dispatchers.IO) {
        exec(Request.Builder().url(abs(path)!!).post(bytes.toRequestBody(contentType.toMediaType())).build())
    }

    private fun exec(r: Request): JSONObject {
        Fixture.answer(r)?.let { return it }
        // Fail closed: with the fixture on, no answer from the canned set means this call is unknown - never fall through to the site.
        if (Fixture.on) throw ApiError(0, "Fixture has no answer for " + r.method + " " + r.url.encodedPath, JSONObject())
        http.newCall(r).execute().use { res ->
            val text = res.body?.string() ?: ""
            val j = runCatching { JSONObject(text) }.getOrNull()
            if (!res.isSuccessful) {
                if (Fixture.on && (res.code == 401 || res.code == 403)) return JSONObject().put("ok", true)
                val msg = j?.optString("error")?.takeIf { it.isNotBlank() } ?: "The site answered ${res.code}"
                throw ApiError(res.code, msg, j)
            }
            return j ?: throw ApiError(res.code, if (text.contains("<html", true)) "The site is checking your connection. Try again." else "Unexpected answer from the site")
        }
    }

    val JSON = "application/json; charset=utf-8".toMediaType()

    fun isOffline(e: Throwable) = e is UnknownHostException || e is ConnectException || e is SocketTimeoutException ||
        (e is IOException && e !is ApiError)

    fun friendly(e: Throwable): String = when {
        e is ApiError -> e.message ?: "Something went wrong"
        isOffline(e) -> "No internet connection"
        else -> "Something went wrong"
    }
}


/** QA only: a local demo account fed from assets/qa/fixtures.json (shaped like the site's own API answers) so every
 *  signed-in screen can be photographed without a real password. Nothing is ever sent to the site while it is on:
 *  sign-in-only reads come from the file, every write is answered locally. Switched on by the QA script (adb intent). */
object Fixture {
    @Volatile var data: JSONObject? = null; private set
    val on get() = data != null
    private fun prefs(c: Context) = c.getSharedPreferences("qa", Context.MODE_PRIVATE)
    fun init(c: Context) { if (prefs(c).getBoolean("fixture", false)) load(c) }
    private fun load(c: Context) {
        // fixtures.json plus any fixtures-*.json next to it (each screen area keeps its own sample answers).
        val all = JSONObject()
        val names = runCatching { c.assets.list("qa")?.toList() }.getOrNull().orEmpty().filter { it.startsWith("fixtures") && it.endsWith(".json") }.sortedBy { if (it == "fixtures.json") "" else it }
        for (n in names) runCatching {
            val j = JSONObject(c.assets.open("qa/$n").bufferedReader().use { it.readText() })
            for (k in j.keys()) all.put(k, j.get(k))
        }
        // Media reads stay local too: canned answers point at the site's /api/media files; swap them for bundled placeholders so QA pulls no bytes from the site.
        val dir = java.io.File(c.filesDir, "qa-media").apply { mkdirs() }
        val vf = java.io.File(dir, "voice.m4a"); if (!vf.exists()) runCatching { c.assets.open("qa/cs-voice.m4a").use { i -> vf.outputStream().use { o -> i.copyTo(o) } } }
        voiceFile = "file://" + vf.absolutePath
        // A WebM/Opus voice note (what the site's own recorder produces: live voice media is audio/webm) so the player path for it is exercised too. Generated, non-secret, 8 s 440 Hz tone.
        val wf = java.io.File(dir, "voice.webm"); if (!wf.exists()) runCatching { wf.writeBytes(android.util.Base64.decode(WEBM_B64, android.util.Base64.DEFAULT)) }
        webmFile = "file://" + wf.absolutePath
        val im = java.io.File(dir, "img.png"); if (!im.exists()) runCatching { im.writeBytes(android.util.Base64.decode(IMG_PNG, android.util.Base64.DEFAULT)) }
        imgFile = "file://" + im.absolutePath
        remapMedia(all)
        sent.clear()
        data = if (all.length() > 0) all else null
    }
    fun set(c: Context, enable: Boolean) {
        prefs(c).edit().putBoolean("fixture", enable).commit()
        if (enable) load(c) else data = null
        Disk.clear()
    }
    /** Messages sent while the fixture is on, appended to that room's history answers so reopen flows see them (the site would persist them). */
    private val sent = java.util.concurrent.CopyOnWriteArrayList<JSONObject>()
    private var voiceFile: String? = null
    private var webmFile: String? = null
    // Generated 8 s 440 Hz Opus-in-WebM tone (no private content), fixture mode only.
    private const val WEBM_B64 = "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"
    private var imgFile: String? = null
    private const val IMG_PNG = "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAEUlEQVR4nGOYM2cFVsQwtCQA4Hp4AX8OoekAAAAASUVORK5CYII="

    /** A media upload in fixture mode: the caller kept the bytes on disk; record the message so history answers include it. */
    fun noteSend(roomId: String, type: String, text: String, name: String, mediaPath: String?) {
        val me = data?.optJSONObject("/api/auth/me")?.optJSONObject("user")
        val m = JSONObject()
            .put("id", "fx-" + System.nanoTime()).put("room_id", roomId)
            .put("sender_id", me?.optString("id") ?: "demo-me").put("sender_nick", me?.optString("nickname") ?: "Me")
            .put("sender_photo", JSONObject.NULL).put("type", type)
            .put("text", if (type == "voice" && !text.contains('|')) "0:03|40,60,80,50,70,30,60,80,40,50,70,30,60,40,80,50,70,60,40,30" else text)
            .put("created_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())).put("read", 1).put("_fx", true)
        if (mediaPath != null) { m.put("media_url", mediaPath); m.put("media_key", mediaPath) }
        sent.add(m)
    }

    private fun localMedia(u: String): String {
        val a = u.substringAfterLast('.', "").substringBefore('?').lowercase()
        if (a == "webm") return webmFile ?: u
        return (if (a in setOf("m4a", "mp3", "ogg", "opus", "wav", "aac", "amr")) voiceFile else imgFile) ?: u
    }
    // Site media, plus the QA sample files the fixtures used to point at on raw.githubusercontent.com: both go to the bundled local copies so fixture mode opens no network connection from the player.
    private fun isSiteMedia(v: String) = v.startsWith("/api/media/") || v.contains("://yidplus.com/api/media/") || (v.startsWith("https://raw.githubusercontent.com/848848448/yidplus-android/") && v.contains("/qa/"))
    private fun remapMedia(o: Any?) {
        when (o) {
            is JSONObject -> for (k in o.keys().asSequence().toList()) { val v = o.opt(k); if (v is String && (isSiteMedia(v))) o.put(k, localMedia(v)) else remapMedia(v) }
            is JSONArray -> for (i in 0 until o.length()) { val v = o.opt(i); if (v is String && (isSiteMedia(v))) o.put(i, localMedia(v)) else remapMedia(v) }
        }
    }

    fun answer(r: Request): JSONObject? {
        val d = data ?: return null
        val u = r.url
        if (!u.host.endsWith("yidplus.com") || !u.encodedPath.startsWith("/api/")) return null
        val q = u.encodedQuery
        val full = u.encodedPath + (if (q.isNullOrBlank()) "" else "?$q")
        if (r.method != "GET") {
            // The QA log shows exactly what this action would send to the site (tag YidFx), so each action can be checked against the site's own code.
            val ct = r.body?.contentType()?.toString().orEmpty()
            val raw = if (ct.contains("json")) runCatching { okio.Buffer().also { r.body?.writeTo(it) }.readUtf8() }.getOrDefault("") else ""
            android.util.Log.i("YidFx", "${r.method} $full " + if (ct.contains("json")) raw.take(400) else "<$ct ${r.body?.contentLength() ?: 0} bytes>")
            // A chat send is remembered (new messages only: edits/deletes carry the message id) so a reopened room shows it.
            if (r.method == "POST" && u.encodedPath == "/api/chat" && raw.isNotEmpty()) runCatching { JSONObject(raw).let {
                if (it.optString("room_id").isNotEmpty() && it.optString("id").isEmpty()) noteSend(it.optString("room_id"), it.optString("type", "text"), it.optString("text"), "", null) } }
            return JSONObject().put("ok", true).put("id", "fx-" + System.nanoTime())
        }
        var a = d.optJSONObject(full) ?: d.optJSONObject(u.encodedPath)
        if (a == null && u.encodedPath == "/api/chat" && q != null && q.contains("room_id=")) {
            // History variants (view=1 and friends): fall back to the room's canned history by room_id.
            val rid = q.substringAfter("room_id=").substringBefore('&')
            a = d.keys().asSequence().firstOrNull { it == "/api/chat?room_id=$rid" || it.startsWith("/api/chat?room_id=$rid&") }?.let { d.optJSONObject(it) }
        }
        if (a != null && u.encodedPath == "/api/chat" && q != null && q.contains("room_id=")) {
            // A reopened room sees what was sent this session (texts, the just-recorded voice note kept on disk).
            val rid = q.substringAfter("room_id=").substringBefore('&')
            val arr = a.optJSONArray("messages")
            if (arr != null) {
                var i = arr.length() - 1
                while (i >= 0) { if (arr.optJSONObject(i)?.optBoolean("_fx") == true) arr.remove(i); i-- }
                for (m in sent) if (m.optString("room_id") == rid) arr.put(m)
            }
        }
        // QA never reads the live database: an /api GET with no fixture answers {"ok":true} (logged so the area can add a fixture).
        return a ?: if (u.encodedPath.startsWith("/api/media/")) null
            else JSONObject().put("ok", true).also { android.util.Log.i("YidFx", "GET-unfixtured $full") }
    }
}

/** Keeps the site's cookies (the yp_session sign-in) across app restarts. */
class PrefsCookieJar(ctx: Context) : CookieJar {
    private val prefs = ctx.getSharedPreferences("cookies", Context.MODE_PRIVATE)
    private val mem = LinkedHashMap<String, Cookie>()
    private val base = HttpUrl.Builder().scheme("https").host("yidplus.com").build()

    init {
        for ((k, v) in prefs.all) {
            val c = (v as? String)?.let { Cookie.parse(base, it) } ?: continue
            if (c.expiresAt > System.currentTimeMillis()) mem[k] = c
        }
    }

    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!url.host.endsWith("yidplus.com")) return
        val e = prefs.edit()
        for (c in cookies) {
            if (c.expiresAt <= System.currentTimeMillis() || c.value.isEmpty()) { mem.remove(c.name); e.remove(c.name) }
            else { mem[c.name] = c; e.putString(c.name, c.toString()) }
        }
        e.apply()
    }

    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> =
        if (url.host.endsWith("yidplus.com")) mem.values.filter { it.expiresAt > System.currentTimeMillis() } else emptyList()

    @Synchronized fun has(name: String) = mem[name]?.let { it.expiresAt > System.currentTimeMillis() } == true
    @Synchronized fun value(name: String) = mem[name]?.value
    @Synchronized fun put(name: String, value: String) {
        val c = Cookie.Builder().name(name).value(value).domain("yidplus.com").path("/").secure().httpOnly()
            .expiresAt(System.currentTimeMillis() + 30L * 24 * 3600 * 1000).build()
        mem[name] = c; prefs.edit().putString(name, c.toString()).apply()
    }
    @Synchronized fun clear() { mem.clear(); prefs.edit().clear().apply() }
}

/** Small JSON disk cache: every screen opens from here instantly, then refreshes from the network. */
object Disk {
    private lateinit var dir: File
    fun init(ctx: Context) { dir = File(ctx.filesDir, "jc").apply { mkdirs() } }
    private fun f(key: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$h.json")
    }
    fun read(key: String): JSONObject? = runCatching { f(key).takeIf { it.exists() }?.readText()?.let { JSONObject(it) } }.getOrNull()
    /** When the cached entry was written (0 when unknown): lets a screen say how old the cached data it shows is. */
    fun modified(key: String): Long = runCatching { f(key).takeIf { it.exists() }?.lastModified() ?: 0L }.getOrDefault(0L)
    fun write(key: String, j: JSONObject) { runCatching { val t = File(dir, "tmp-" + System.nanoTime()); t.writeText(j.toString()); t.renameTo(f(key)) } }
    fun clear() { runCatching { dir.listFiles()?.forEach { it.delete() } } }
}

fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k, "")
fun JSONObject.strOrNull(k: String): String? = if (isNull(k)) null else optString(k, "").takeIf { it.isNotBlank() && it != "null" }
fun JSONArray?.objects(): List<JSONObject> { if (this == null) return emptyList(); return (0 until length()).mapNotNull { optJSONObject(it) } }
