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
