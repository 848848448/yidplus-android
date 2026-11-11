package com.yidplus.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yidplus.app.data.Api
import com.yidplus.app.data.Room
import com.yidplus.app.data.Session
import com.yidplus.app.data.Store
import com.yidplus.app.data.prettyPreview
import com.yidplus.app.data.str
import com.yidplus.app.data.strOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

/* ───────────────────────── Chat data (parsed here so Models.kt stays shared and untouched) ───────────────────────── */

internal fun chObjs(a: JSONArray?): List<JSONObject> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it) }
internal fun chEnc(s: String): String = URLEncoder.encode(s, "UTF-8")

data class ChMember(val id: String, val nick: String, val photo: String?, val online: Boolean, val role: String, val admin: Boolean, val title: String)

/** One room from /api/chat/rooms with everything the site sends (mute, members, pin, invite, admin rights). */
data class ChatRoomInfo(val room: Room, val muted: Boolean, val online: Boolean, val joined: Boolean, val isGroupAdmin: Boolean, val createdBy: String?,
                    val inviteCode: String?, val pinnedId: String?, val description: String, val visibility: String,
                    val members: List<ChMember>, val featured: Boolean) {
    val isGroup get() = room.type == "group"
    val isChannel get() = room.type == "channel"
    val isPrivate get() = room.type == "private"
    /** Group admin, the creator, or a site admin: may pin, edit the group, add/remove members. */
    val canManage: Boolean get() { val me = Session.me ?: return false; return isGroupAdmin || createdBy == me.id || chIsSiteAdmin() }
    val onlineCount get() = members.count { it.online }
    companion object {
        fun from(j: JSONObject) = ChatRoomInfo(Room.from(j), j.optBoolean("muted"), j.optBoolean("online"), j.optBoolean("joined", true), j.optBoolean("is_group_admin"),
            j.strOrNull("created_by"), j.strOrNull("invite_code"), j.strOrNull("pinned_message_id"), j.str("description"), j.str("visibility"),
            chObjs(j.optJSONArray("member_list")).map { m -> ChMember(m.str("id"), m.str("nickname"), Api.abs(m.strOrNull("photo_url")), m.optInt("online") == 1 || m.optBoolean("online"), m.str("role"), m.optInt("is_group_admin") == 1 || m.optBoolean("is_group_admin"), m.str("title")) },
            j.optBoolean("featured"))
        fun list(j: JSONObject) = chObjs(j.optJSONArray("rooms")).map(::from)
    }
}

fun chIsSiteAdmin(): Boolean { val me = Session.me ?: return false; return me.isOwner || me.role.startsWith("admin") || me.role == "owner" || me.role == "co_owner" }

/** One chat message with every field the site's chat uses (reply, edit, read ticks, seen count, view-once). */
data class ChatMsg(val id: String, val roomId: String, val senderId: String, val nick: String, val photo: String?, val type: String, val text: String,
                   val media: String?, val mediaKey: String, val createdAt: String?, val replyToId: String? = null, val edited: Boolean = false,
                   val read: Boolean = false, val seen: Int = 0, val viewOnce: Boolean = false, val opened: Boolean = false,
                   val pending: Boolean = false, val failed: Boolean = false, val local: String? = null) {
    private val ext get() = mediaKey.substringAfterLast('.', "").lowercase().substringBefore('?')
    val isSticker get() = type == "sticker"
    val stickerUrl get() = if (!isSticker) null else media ?: text.takeIf { it.startsWith("http") || it.startsWith("/") }?.let { Api.abs(it) }
    val isVoice get() = type == "voice"
    val isImage get() = !isSticker && (media != null || local != null) && (ext in setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "avif") || (type == "media" && ext.isEmpty() && local == null))
    val isVideo get() = (media != null || local != null) && ext in setOf("mp4", "mov", "webm", "3gp", "mkv", "m4v") && !isVoice
    val isAudio get() = media != null && !isVideo && (isVoice || ext in setOf("mp3", "m4a", "ogg", "opus", "wav", "aac", "amr", "flac"))
    val isFile get() = media != null && !isImage && !isVideo && !isAudio && !isSticker
    val isPoll get() = type == "poll"
    val isSystem get() = type == "system"
    /** Voice notes carry "M:SS|peaks" in their text (the site's format). */
    val voiceDur get() = if (isVoice) text.substringBefore('|').takeIf { Regex("""\d+:\d\d""").matches(it) } ?: "" else ""
    val caption get() = when { isVoice || isPoll || isSticker -> ""; isFile && text == fileName -> ""; text == "__once__" -> ""; else -> text }
    val fileName get() = mediaKey.substringAfterLast('/').substringAfter('_', mediaKey.substringAfterLast('/')).ifBlank { "File" }.let { if (type == "file" && text.isNotBlank() && !text.contains('\n') && text.length < 120) text else it }
    val preview: String get() = when {
        isPoll -> "📊 Poll"; isVoice -> "🎤 Voice message"; isSticker -> "Sticker"; isImage -> "📷 Photo" + (caption.takeIf { it.isNotBlank() }?.let { " $it" } ?: "")
        isVideo -> "🎬 Video"; isAudio -> "🎵 Audio"; isFile -> "📎 $fileName"; else -> text.take(120)
    }
    companion object {
        fun from(j: JSONObject) = ChatMsg(j.str("id"), j.str("room_id"), j.str("sender_id"), j.str("sender_nick"), Api.abs(j.strOrNull("sender_photo")), j.str("type"), j.str("text"),
            Api.abs(j.strOrNull("media_url")) ?: j.strOrNull("media_key")?.let { "${Api.BASE}/api/media/" + chEnc(it).replace("+", "%20") }, j.str("media_key"), j.strOrNull("created_at"),
            j.strOrNull("reply_to_id"), j.strOrNull("edited_at") != null, j.optInt("read") == 1 || j.optBoolean("read"), j.optInt("seen_count"),
            j.optInt("view_once") == 1 || j.optBoolean("view_once"), j.optInt("opened") == 1 || j.optBoolean("opened"))
    }
}

data class ChReact(val counts: Map<String, Int>, val mine: String?)

/* ───────────────────────── Chat API calls (all existing yidplus.com endpoints) ───────────────────────── */

object ChatApi {
    val rooms get() = Store.res("/api/chat/rooms", ChatRoomInfo::list)
    fun info(id: String): ChatRoomInfo? = rooms.data?.firstOrNull { it.room.id == id }

    suspend fun messages(roomId: String): Pair<JSONObject, List<ChatMsg>> {
        val j = Api.get("/api/chat?room_id=" + chEnc(roomId))
        return j to chObjs(j.optJSONArray("messages")).map(ChatMsg::from)
    }
    suspend fun reactions(roomId: String): Map<String, ChReact> {
        val j = Api.get("/api/chat/reactions?room_id=" + chEnc(roomId)).optJSONObject("reactions") ?: return emptyMap()
        val out = HashMap<String, ChReact>()
        j.keys().forEach { id -> val o = j.optJSONObject(id) ?: return@forEach; val c = o.optJSONObject("counts") ?: JSONObject()
            out[id] = ChReact(c.keys().asSequence().associateWith { c.optInt(it) }.filterValues { it > 0 }, o.strOrNull("my_reaction")) }
        return out
    }
    suspend fun react(msgId: String, emoji: String) = Api.post("/api/chat/reactions", JSONObject().put("message_id", msgId).put("emoji", emoji))
    suspend fun typing(roomId: String): List<String> = chObjs(Api.get("/api/chat/typing?room_id=" + chEnc(roomId)).optJSONArray("typing")).map { it.str("user_nick") }.filter { it.isNotBlank() }
    suspend fun sendTyping(roomId: String) = Api.post("/api/chat/typing", JSONObject().put("room_id", roomId))
    suspend fun sendText(roomId: String, text: String, replyTo: String?): ChatMsg? {
        val b = JSONObject().put("room_id", roomId).put("type", "text").put("text", text)
        if (replyTo != null) b.put("reply_to_id", replyTo)
        return Api.post("/api/chat", b).optJSONObject("message")?.let(ChatMsg::from)
    }
    suspend fun edit(id: String, text: String) = Api.put("/api/chat", JSONObject().put("id", id).put("text", text))
    suspend fun delete(id: String) = Api.delete("/api/chat?id=" + chEnc(id))
    suspend fun pin(roomId: String, msgId: String?) = Api.put("/api/chat/rooms", JSONObject().put("room_id", roomId).put("pinned_message_id", msgId ?: JSONObject.NULL))
    suspend fun mute(roomId: String, muted: Boolean) = Api.put("/api/chat/rooms", JSONObject().put("room_id", roomId).put("muted", muted))
    suspend fun leave(roomId: String, all: Boolean = false) = Api.delete("/api/chat/rooms?room_id=" + chEnc(roomId) + if (all) "&delete_all=1" else "")
    suspend fun save(msgId: String, roomId: String) = Api.post("/api/chat/bookmarks", JSONObject().put("message_id", msgId).put("room_id", roomId))
    suspend fun unsave(msgId: String) = Api.delete("/api/chat/bookmarks?message_id=" + chEnc(msgId))
    suspend fun report(m: ChatMsg, reason: String) = Api.post("/api/reports", JSONObject().put("reported_id", m.senderId).put("reported_nick", m.nick)
        .put("message_id", m.id).put("room_id", m.roomId).put("reason", reason))
    suspend fun read(roomId: String) = Api.post("/api/chat/read", JSONObject().put("room_id", roomId))
    suspend fun openDm(userId: String): String = Api.post("/api/chat/rooms", JSONObject().put("type", "private").put("other_user_id", userId)).str("room_id")
    suspend fun create(type: String, name: String, desc: String, public: Boolean): String = Api.post("/api/chat/rooms", JSONObject().put("type", type).put("name", name)
        .put("description", desc).put("visibility", if (public) "public" else "private").put("read_only", type == "channel")).str("room_id")
    suspend fun addMember(roomId: String, userId: String) = Api.put("/api/chat/rooms", JSONObject().put("room_id", roomId).put("member_id", userId).put("add", true))
    suspend fun removeMember(roomId: String, userId: String) = Api.put("/api/chat/rooms", JSONObject().put("room_id", roomId).put("member_id", userId).put("remove", true))
    suspend fun setAdmin(roomId: String, userId: String, on: Boolean) = Api.put("/api/chat/rooms", JSONObject().put("room_id", roomId).put("member_id", userId).put("make_admin", on))
    suspend fun invite(roomId: String): String = Api.put("/api/chat/rooms", JSONObject().put("room_id", roomId).put("generate_invite", true)).str("invite_code")
    suspend fun join(roomId: String?, code: String?): String = Api.post("/api/chat/join", JSONObject().apply { if (roomId != null) put("room_id", roomId); if (code != null) put("invite_code", code) }).str("room_id").ifBlank { roomId ?: "" }
    suspend fun block(userId: String) = Api.post("/api/blocks", JSONObject().put("blocked_id", userId))
    suspend fun users(q: String): List<ChMember> = chObjs(Api.get("/api/users/search?q=" + chEnc(q)).optJSONArray("users")).map { ChMember(it.str("id"), it.str("nickname"), Api.abs(it.strOrNull("photo_url")), false, it.str("role"), false, it.str("bio")) }
        .filter { it.id != Session.me?.id }

    /** A room that is not in my list yet (just created, joined, or opened from search): a minimal Room to open it with. */
    fun roomOf(id: String, type: String, name: String, photo: String? = null, other: String? = null) = Room(id, type, name, photo, "", "", 0, null, type == "channel", false, 0, other)

    /** Streams a file straight from the phone to the site's chat (no copy in memory; the same raw upload the website uses). */
    suspend fun upload(ctx: Context, roomId: String, uri: Uri?, file: File?, type: String, name: String, mime: String, text: String = "", replyTo: String? = null): JSONObject = withContext(Dispatchers.IO) {
        val url = "${Api.BASE}/api/chat?room_id=" + chEnc(roomId) + "&type=" + type + "&filename=" + chEnc(name) + "&ct=" + chEnc(mime) +
            (if (text.isNotBlank()) "&text=" + chEnc(text) else "") + (if (replyTo != null) "&reply_to_id=" + chEnc(replyTo) else "")
        val len = file?.length() ?: uri?.let { u -> runCatching { ctx.contentResolver.openAssetFileDescriptor(u, "r")?.use { it.length } }.getOrNull() } ?: -1L
        val body = object : RequestBody() {
            override fun contentType() = mime.toMediaTypeOrNull()
            override fun contentLength() = if (len > 0) len else -1L
            override fun writeTo(sink: BufferedSink) {
                val ins = file?.inputStream() ?: ctx.contentResolver.openInputStream(uri!!) ?: error("Can't read the file")
                ins.use { sink.writeAll(it.source()) }
            }
        }
        Api.http.newCall(Request.Builder().url(url).post(body).build()).execute().use { r ->
            val t = r.body?.string() ?: ""
            val j = runCatching { JSONObject(t) }.getOrElse { JSONObject() }
            if (!r.isSuccessful) throw Api.ApiError(r.code, j.optString("error").ifBlank { "Upload failed (${r.code})" }, j)
            j
        }
    }

    fun nameOf(ctx: Context, uri: Uri): String = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    fun sizeOf(ctx: Context, uri: Uri): Long = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else -1L }
    }.getOrNull() ?: -1L
}

/** "12:40 PM" today, "Yesterday", the weekday this week, else "Nov 3". */
fun chatTime(s: String?): String {
    val t = parseTime(s) ?: return ""
    val now = java.util.Calendar.getInstance(); val c = java.util.Calendar.getInstance().apply { timeInMillis = t }
    val days = ((startOfDay(now.timeInMillis) - startOfDay(t)) / 86_400_000L).toInt()
    return when {
        days <= 0 -> java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(c.time)
        days == 1 -> "Yesterday"
        days < 7 -> java.text.SimpleDateFormat("EEE", java.util.Locale.US).format(c.time)
        else -> java.text.SimpleDateFormat(if (now.get(java.util.Calendar.YEAR) == c.get(java.util.Calendar.YEAR)) "MMM d" else "d/M/yy", java.util.Locale.US).format(c.time)
    }
}
/** Date chip between messages: Today / Yesterday / Monday / November 3. */
fun chatDay(s: String?): String {
    val t = parseTime(s) ?: return ""
    val days = ((startOfDay(System.currentTimeMillis()) - startOfDay(t)) / 86_400_000L).toInt()
    return when { days <= 0 -> "Today"; days == 1 -> "Yesterday"; days < 7 -> java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(java.util.Date(t))
        else -> java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.US).format(java.util.Date(t)) }
}
private fun startOfDay(ms: Long): Long = java.util.Calendar.getInstance().apply { timeInMillis = ms; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis

fun chatRoomPreview(r: Room): String {
    val p = r.preview
    if (Regex("""^\d+:\d\d\|""").containsMatchIn(p)) return "🎤 Voice message"
    return prettyPreview(p)
}

/* ───────────────────────── Video posters (a real frame, never a black box) ───────────────────────── */

object ChatVideoThumbs {
    private val cache = LruCache<String, Bitmap>(24)
    private val gate = Semaphore(2)
    fun cached(url: String): Bitmap? = cache.get(url)
    suspend fun load(ctx: Context, url: String): Bitmap? = cache.get(url) ?: gate.withPermit {
        cache.get(url) ?: withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                if (url.startsWith("content:") || url.startsWith("file:")) r.setDataSource(ctx, Uri.parse(url))
                else r.setDataSource(url, HashMap<String, String>().apply { Api.session()?.let { put("Cookie", "yp_session=$it") }; put("User-Agent", Api.UA) })
                val f = if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 480) else r.getFrameAtTime(1_000_000)
                f?.also { cache.put(url, it) }
            } catch (e: Throwable) { null } finally { runCatching { r.release() } }
        }
    }
}

/* ───────────────────────── Voice notes: one player for the whole app chat, and the recorder ───────────────────────── */

object ChatVoicePlayer {
    var playing by mutableStateOf<String?>(null); private set
    var progress by mutableStateOf(0f)
    var paused by mutableStateOf(false); private set
    private var mp: MediaPlayer? = null
    fun toggle(ctx: Context, id: String, url: String) {
        if (playing == id && mp != null) { val p = mp!!; if (p.isPlaying) { p.pause(); paused = true } else { p.start(); paused = false }; return }
        stop()
        playing = id; paused = false; progress = 0f
        val p = MediaPlayer()
        mp = p
        runCatching {
            p.setDataSource(ctx.applicationContext, Uri.parse(url), HashMap<String, String>().apply { Api.session()?.let { put("Cookie", "yp_session=$it") } })
            p.setOnPreparedListener { it.start() }
            p.setOnCompletionListener { stop() }
            p.setOnErrorListener { _, _, _ -> stop(); true }
            p.prepareAsync()
        }.onFailure { stop() }
    }
    fun tick() { val p = mp ?: return; runCatching { if (p.duration > 0) progress = p.currentPosition.toFloat() / p.duration } }
    fun stop() { runCatching { mp?.release() }; mp = null; playing = null; paused = false; progress = 0f }
}

class ChatVoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    var file: File? = null; private set
    var startedAt = 0L; private set
    val peaks = ArrayList<Int>()
    fun start(): Boolean = runCatching {
        val f = File(ctx.cacheDir, "voice_" + System.currentTimeMillis() + ".m4a")
        @Suppress("DEPRECATION") val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(48_000); r.setAudioSamplingRate(44_100); r.setAudioChannels(1)
        r.setOutputFile(f.absolutePath)
        r.prepare(); r.start()
        rec = r; file = f; startedAt = System.currentTimeMillis(); peaks.clear(); true
    }.getOrElse { runCatching { rec?.release() }; rec = null; false }
    fun sample() { val r = rec ?: return; runCatching { peaks.add((r.maxAmplitude / 327).coerceIn(0, 100)) } }
    fun seconds(): Int = if (startedAt == 0L) 0 else ((System.currentTimeMillis() - startedAt) / 1000).toInt()
    /** Stops; returns the file and the site's "M:SS|peaks" text, or null when cancelled / too short. */
    fun stop(keep: Boolean): Pair<File, String>? {
        val r = rec ?: return null; rec = null
        val ok = runCatching { r.stop() }.isSuccess; runCatching { r.release() }
        val f = file ?: return null
        val sec = seconds().coerceAtLeast(1)
        if (!keep || !ok || f.length() < 800) { f.delete(); return null }
        val n = 40; val ps = if (peaks.isEmpty()) List(n) { 30 } else List(n) { i -> peaks[(i * peaks.size / n).coerceIn(0, peaks.size - 1)].coerceAtLeast(6) }
        return f to ("%d:%02d".format(sec / 60, sec % 60) + "|" + ps.joinToString(","))
    }
}

/** Photos go out at 1280 px max, like the website. */
fun chShrink(ctx: Context, uri: Uri?, bmpIn: Bitmap?): ByteArray {
    val bmp = bmpIn ?: run {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri!!)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1; while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 1280) s *= 2
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: error("Can't read the photo")
    }
    val scale = 1280f / maxOf(bmp.width, bmp.height)
    val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
    return java.io.ByteArrayOutputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 82, it); it.toByteArray() }
}

/** Emoji panel sets (the site's categories). */
val CHAT_EMOJI_SETS: List<Pair<String, String>> = listOf(
    "😀" to "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🥳 😏 😒 😞 😔 😟 😕 🙁 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕",
    "👍" to "👍 👎 👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 👏 🙌 👐 🤲 🤝 🙏 ✍️ 💪 🦾 👀 👁️ 👂 👃 🧠 👶 🧒 👦 👧 🧑 👨 👩 🧔 👴 👵 🙋 🙇 🤦 🤷 💁 🙆 🙅 👪 👨‍👩‍👦 👨‍👩‍👧‍👦",
    "🐶" to "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🐴 🦄 🐝 🦋 🐌 🐞 🐢 🐍 🐙 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🐘 🦒 🐪 🐫 🐑 🐐 🦌 🐕 🐈 🌸 🌹 🌻 🌷 🌲 🌳 🌴 🍀 🍁 🌈 ☀️ 🌙 ⭐ 🔥 💧 ❄️",
    "🍎" to "🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🥦 🥒 🌶️ 🌽 🥕 🧄 🧅 🥔 🍠 🥐 🥯 🍞 🥖 🧀 🥚 🍳 🥞 🧇 🥓 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍣 🍱 🍩 🍪 🎂 🍰 🧁 🍫 🍬 🍭 🍯 ☕ 🍵 🥤 🍷 🥂",
    "❤️" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 ✅ ❌ ⭕ ❗ ❓ ‼️ ⁉️ 💯 🔴 🟢 🔵 ⚪ ⚫ 🎉 🎊 🎁 🏆 🥇 ⚽ 🏀 🎵 🎶 🎤 🎧 📞 📱 💻 📷 🎥 📚 ✏️ 📌 📎 🔒 🔑 💡 ⏰ 💰 🚗 ✈️ 🏠 🌍",
    "🕎" to "🕎 ✡️ 🕍 📜 🍷 🕯️ 🍞 🥯 🍯 🍎 🫒 🌿 🎺 📖 🙏 💐 🎉 👑 🏛️ 🌟 ⭐ 🌙 🔯 🪔 🛡️ 🎭 🍇 🥂 🫓",
).map { it.first to it.second }
val CHAT_QUICK_REACTS = listOf("❤️", "😂", "😮", "😢", "👍", "🔥")

/** The site's sticker set (Noto animated emoji, the same URLs the website sends). */
val CHAT_STICKERS: List<String> = listOf("1f600", "1f602", "1f60d", "1f62d", "1f621", "1f973", "1f44f", "1f525", "2764_fe0f", "1f64f", "1f44d", "1f914", "1f389", "1f48b", "1f499", "2728").map { "https://fonts.gstatic.com/s/e/notoemoji/latest/$it/512.gif" }

/** Unsent text per chat survives leaving the chat and closing the app (like WhatsApp drafts). */
object ChDrafts {
    private fun p(c: Context) = c.getSharedPreferences("chat_drafts", Context.MODE_PRIVATE)
    fun get(c: Context, room: String): String = runCatching { p(c).getString(room, "") ?: "" }.getOrDefault("")
    fun put(c: Context, room: String, text: String) { runCatching { p(c).edit().apply { if (text.isBlank()) remove(room) else putString(room, text) }.apply() } }
}
