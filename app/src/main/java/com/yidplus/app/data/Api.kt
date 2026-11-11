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
        val names = runCatching { c.assets.list("qa")?.toList() }.getOrNull().orEmpty().filter { it.startsWith("fixtures") && it.endsWith(".json") }.sorted()
        for (n in names) runCatching {
            val j = JSONObject(c.assets.open("qa/$n").bufferedReader().use { it.readText() })
            for (k in j.keys()) all.put(k, j.get(k))
        }
        data = if (all.length() > 0) all else null
    }
    fun set(c: Context, enable: Boolean) {
        prefs(c).edit().putBoolean("fixture", enable).commit()
        if (enable) load(c) else data = null
        Disk.clear()
    }
    fun answer(r: Request): JSONObject? {
        val d = data ?: return null
        val u = r.url
        if (!u.host.endsWith("yidplus.com") || !u.encodedPath.startsWith("/api/")) return null
        if (r.method != "GET") return JSONObject().put("ok", true).put("id", "fx-" + System.nanoTime())
        val q = u.encodedQuery
        val full = u.encodedPath + (if (q.isNullOrBlank()) "" else "?$q")
        return d.optJSONObject(full) ?: d.optJSONObject(u.encodedPath)
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
    fun write(key: String, j: JSONObject) { runCatching { val t = File(dir, "tmp-" + System.nanoTime()); t.writeText(j.toString()); t.renameTo(f(key)) } }
    fun clear() { runCatching { dir.listFiles()?.forEach { it.delete() } } }
}

fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k, "")
fun JSONObject.strOrNull(k: String): String? = if (isNull(k)) null else optString(k, "").takeIf { it.isNotBlank() && it != "null" }
fun JSONArray?.objects(): List<JSONObject> { if (this == null) return emptyList(); return (0 until length()).mapNotNull { optJSONObject(it) } }
