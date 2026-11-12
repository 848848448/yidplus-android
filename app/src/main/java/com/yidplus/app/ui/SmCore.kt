package com.yidplus.app.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.content.FileProvider
import com.yidplus.app.data.Api
import com.yidplus.app.data.Fixture
import com.yidplus.app.data.str
import com.yidplus.app.data.strOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.buffer
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Helper SM (2.1): small things the statuses, music and photo viewer share. Local-only settings live in prefs "sm". */
object SmPrefs {
    private var ctx: Context? = null
    val appCtx: Context? get() = ctx
    private fun p() = ctx?.getSharedPreferences("sm", Context.MODE_PRIVATE)
    val muted = mutableStateListOf<String>()
    val highlights = mutableStateListOf<JSONObject>()
    var videoMuted by mutableStateOf(false)
        private set
    fun init(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
        muted.clear(); muted.addAll(p()?.getStringSet("muted", emptySet()).orEmpty())
        highlights.clear(); runCatching { JSONArray(p()?.getString("hl", "[]") ?: "[]").let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let(highlights::add) } }
        videoMuted = p()?.getBoolean("vmute", false) ?: false
    }
    fun isMuted(uid: String) = uid in muted
    fun setMuted(uid: String, on: Boolean) {
        if (on && uid !in muted) muted.add(uid); if (!on) muted.remove(uid)
        p()?.edit()?.putStringSet("muted", muted.toSet())?.apply()
    }
    fun muteVideos(on: Boolean) { videoMuted = on; p()?.edit()?.putBoolean("vmute", on)?.apply() }
    fun hasHighlight(id: String) = highlights.any { it.optString("id") == id }
    /** Same fields the site keeps in localStorage "yp_highlights". */
    fun addHighlight(s: StSlide, nick: String): Boolean {
        if (hasHighlight(s.id)) return false
        highlights.add(JSONObject().put("id", s.id).put("type", s.type).put("text", s.text).put("media_url", s.media ?: JSONObject.NULL)
            .put("is_video", s.video).put("bg", s.bg).put("color", s.color).put("nick", nick).put("created_at", s.createdAt ?: ""))
        saveHl(); return true
    }
    fun removeHighlight(i: Int) { if (i in highlights.indices) { highlights.removeAt(i); saveHl() } }
    private fun saveHl() { p()?.edit()?.putString("hl", JSONArray(highlights.toList()).toString())?.apply() }
}

/** Status text and captions: http(s) links and bare domains like the site's _svLinkify become tappable. */
fun smLinkify(text: String, linkColor: Color = Color(0xFF6CB2FF)): AnnotatedString = buildAnnotatedString {
    val re = Regex("""(https?://[^\s<]+)|((?<![\w@/.])(?:www\.)?[a-z0-9-]+\.(?:com|org|net|co|io|me|il|info|gov|edu|app|shop|news)(?:/[^\s<]*)?)""", RegexOption.IGNORE_CASE)
    var last = 0
    for (m in re.findAll(text)) {
        append(text.substring(last, m.range.first))
        val raw = m.value.trimEnd('.', ',', ')', '!', '?')
        val url = if (raw.startsWith("http", true)) raw else "https://$raw"
        pushLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))))
        append(raw); pop()
        if (raw.length < m.value.length) append(m.value.substring(raw.length))
        last = m.range.last + 1
    }
    append(text.substring(last))
}

/** One running upload (status media, music), with progress 0..1 and Cancel, like WhatsApp's ring. */
class SmUpload {
    var progress by mutableFloatStateOf(0f)
    var running by mutableStateOf(false)
    private var call: Call? = null
    @Volatile private var cancelled = false
    fun cancel() { cancelled = true; call?.cancel() }

    class Part(val name: String, val uri: Uri, val fileName: String)

    /** POST multipart to [path] with text [fields] and file [files]. In QA fixture mode the request is answered locally and the
     *  field names/values are logged to YidFx (the generic fixture log only shows "<multipart … N bytes>"). */
    suspend fun post(ctx: Context, path: String, fields: List<Pair<String, String>>, files: List<Part>, maxMb: Long = 500): JSONObject = withContext(Dispatchers.IO) {
        cancelled = false; running = true; progress = 0f
        try {
            val cr = ctx.contentResolver
            val sizes = files.map { f -> smSize(ctx, f.uri) }
            sizes.forEachIndexed { i, s -> if (s > maxMb * 1024 * 1024) throw Api.ApiError(413, "${files[i].fileName} is over $maxMb MB.") }
            val total = sizes.sumOf { it.coerceAtLeast(0) }.coerceAtLeast(1)
            var sent = 0L
            val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
            fields.forEach { (k, v) -> mb.addFormDataPart(k, v) }
            files.forEachIndexed { i, f ->
                val mime = cr.getType(f.uri) ?: smMimeOf(f.fileName)
                val body = object : RequestBody() {
                    override fun contentType() = mime.toMediaType()
                    override fun contentLength() = sizes[i]
                    override fun writeTo(sink: BufferedSink) {
                        cr.openInputStream(f.uri)!!.source().use { src ->
                            val buf = okio.Buffer()
                            while (true) {
                                if (cancelled) throw java.io.IOException("Cancelled")
                                val n = src.read(buf, 64 * 1024)
                                if (n < 0) break
                                sink.write(buf, n); sent += n
                                progress = (sent.toFloat() / total).coerceIn(0f, 1f)
                            }
                        }
                    }
                }
                mb.addFormDataPart(f.name, f.fileName, body)
            }
            val req = Request.Builder().url(Api.abs(path)!!).post(mb.build()).header("Accept", "application/json").build()
            if (Fixture.on) {
                val desc = fields.joinToString(",") { (k, v) -> "\"$k\":\"${v.take(60)}\"" } + files.joinToString("") { f -> ",\"${f.name}\":<file ${f.fileName}>" }
                android.util.Log.i("YidFx", "SM-UPLOAD POST $path {$desc}")
                // let the fixture answer it (it also logs the generic line); show the progress moving for the screenshot
                progress = 1f
                return@withContext Fixture.answer(req) ?: JSONObject().put("ok", true)
            }
            val c = Api.http.newCall(req); call = c
            c.execute().use { r ->
                val t = r.body?.string().orEmpty()
                val j = runCatching { JSONObject(t) }.getOrNull()
                if (!r.isSuccessful) throw Api.ApiError(r.code, j?.strOrNull("error") ?: "The site answered ${r.code}", j)
                j ?: JSONObject().put("ok", true)
            }
        } catch (e: java.io.IOException) {
            if (cancelled) throw Api.ApiError(499, "Upload cancelled") else throw e
        } finally { running = false; call = null }
    }
}

fun smSize(ctx: Context, uri: Uri): Long = runCatching {
    if (uri.scheme == "file") File(uri.path!!).length()
    else ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else -1L }
}.getOrNull() ?: -1L

fun smName(ctx: Context, uri: Uri, fallback: String): String = runCatching {
    if (uri.scheme == "file") File(uri.path!!).name
    else ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
}.getOrNull()?.takeIf { it.isNotBlank() } ?: run {
    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(ctx.contentResolver.getType(uri) ?: "")
    if (ext != null) "$fallback.$ext" else fallback
}

fun smMimeOf(name: String): String = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"

/** Save a photo, video or song to the phone's Downloads/YID PLUS folder (the site's _mvDownload / MP3 download). */
fun smSaveToPhone(ctx: Context, url: String, name: String) {
    val clean = name.replace(Regex("""[\\/:*?"<>|]"""), "_").take(80).ifBlank { "yidplus" }
    if (Fixture.on) android.util.Log.i("YidFx", "SM-DOWNLOAD GET $url as $clean")
    runCatching {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val r = DownloadManager.Request(Uri.parse(url)).setTitle(clean)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "YID PLUS/$clean")
        Api.session()?.let { r.addRequestHeader("Cookie", "yp_session=$it") }
        dm.enqueue(r)
        toast(ctx, "Downloading… it will be in Downloads › YID PLUS")
    }.onFailure { toast(ctx, "Could not save: ${it.message ?: "error"}") }
}

/** Share the real file (downloaded to the cache first), falling back to the link. */
suspend fun smShareFile(ctx: Context, url: String, name: String, mime: String) {
    val f = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(ctx.cacheDir, "captures").apply { mkdirs() }
            val out = File(dir, name.replace(Regex("""[\\/:*?"<>|]"""), "_").take(60))
            Api.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                r.body!!.byteStream().use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            }
            out
        }.getOrNull()
    }
    if (f == null || f.length() == 0L) { share(ctx, url); return }
    runCatching {
        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { share(ctx, url) }
}

fun smIsAdmin(): Boolean { val me = com.yidplus.app.data.Session.me ?: return false; return me.isOwner || me.role in setOf("owner", "co_owner", "admin", "admin_super", "super_admin", "moderator") }
