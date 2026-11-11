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
import com.yidplus.app.data.Disk
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
                    val members: List<ChMember>, val featured: Boolean, val rawPreview: String = "", val spectating: Boolean = false, val hasMessages: Boolean = true) {
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
            j.optBoolean("featured"), j.str("preview"), j.optBoolean("admin_spectating") || j.optInt("admin_spectating") == 1, j.opt("has_messages") != false)
        fun list(j: JSONObject) = chObjs(j.optJSONArray("rooms")).map(::from)
    }
}

fun chIsSiteAdmin(): Boolean { val me = Session.me ?: return false; return me.isOwner || me.role.startsWith("admin") || me.role == "owner" || me.role == "co_owner" }

/** One chat message with every field the site's chat uses (reply, edit, read ticks, seen count, view-once). */
data class ChatMsg(val id: String, val roomId: String, val senderId: String, val nick: String, val photo: String?, val type: String, val text: String,
                   val media: String?, val mediaKey: String, val createdAt: String?, val replyToId: String? = null, val edited: Boolean = false,
                   val read: Boolean = false, val seen: Int = 0, val viewOnce: Boolean = false, val opened: Boolean = false,
                   val pending: Boolean = false, val failed: Boolean = false, val local: String? = null,
                   val forwarded: Boolean = false, val deleted: Boolean = false, val title: String = "") {
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
    /** Call-log lines ("📞 Missed voice call"): drawn centred like the site, never filtered out. */
    val isCall get() = type == "call" || type == "voice_call" || type == "video_call"
    /** A voice note saved without its audio (the site's "voice_text": the text is only its length). */
    val isVoiceText get() = type == "voice_text"
    /** A photo / file / voice whose file never arrived: the site shows "Media unavailable", never an empty bubble. */
    val isUnavailable get() = type in setOf("media", "file", "voice") && media == null && local == null
    val isMusic get() = isAudio && !isVoice
    /** Centred lines with no bubble: system notes and call logs. */
    val isCentred get() = isSystem || isCall
    /** Voice notes carry "M:SS|peaks" in their text (the site's format). */
    val voiceDur get() = if (isVoice) text.substringBefore('|').takeIf { Regex("""\d+:\d\d""").matches(it) } ?: "" else ""
    val caption get() = when { isVoice || isPoll || isSticker || isVoiceText || deleted || isCentred -> ""; (isFile || isMusic || isAudio) && (text == fileName || text == mediaKey.substringAfterLast('/').substringAfter('_')) -> ""; text == "__once__" -> ""; else -> text.removeSuffix(" ↩ Forwarded") }
    val fileName get() = mediaKey.substringAfterLast('/').substringAfter('_', mediaKey.substringAfterLast('/')).ifBlank { "File" }.let { if (type == "file" && text.isNotBlank() && !text.contains('\n') && text.length < 120) text else it }
    val preview: String get() = when {
        deleted -> "🚫 This message was deleted"
        isSystem -> text.take(120); isCall -> text.ifBlank { "📞 Call" }.take(120)
        viewOnce && (isImage || isVideo) -> "👁 View once " + (if (isVideo) "video" else "photo")
        isPoll -> "📊 Poll"; isVoice -> "🎤 Voice message" + (voiceDur.takeIf { it.isNotBlank() }?.let { " $it" } ?: "")
        isVoiceText -> "🎤 Voice message" + (text.substringBefore('|').trim().takeIf { Regex("""\d+:\d\d""").matches(it) }?.let { " $it" } ?: "")
        isSticker -> "💟 Sticker"; isImage -> "📷 " + caption.ifBlank { "Photo" }
        isVideo -> "🎬 " + caption.ifBlank { "Video" }; isMusic -> "🎵 " + fileName.substringBeforeLast('.'); isFile -> "📎 $fileName"
        isUnavailable -> "📎 " + caption.ifBlank { "Media" }
        else -> text.removeSuffix(" ↩ Forwarded").replace('\n', ' ').take(120)
    }
    companion object {
        fun from(j: JSONObject) = ChatMsg(j.str("id"), j.str("room_id"), j.str("sender_id"), j.str("sender_nick"), Api.abs(j.strOrNull("sender_photo")), j.str("type"), j.str("text"),
            Api.abs(j.strOrNull("media_url")) ?: j.strOrNull("media_key")?.let { "${Api.BASE}/api/media/" + chEnc(it).replace("+", "%20") }, j.str("media_key"), j.strOrNull("created_at"),
            j.strOrNull("reply_to_id"), j.strOrNull("edited_at") != null, j.optInt("read") == 1 || j.optBoolean("read"), j.optInt("seen_count"),
            j.optInt("view_once") == 1 || j.optBoolean("view_once"), j.optInt("opened") == 1 || j.optBoolean("opened"),
            forwarded = j.optInt("forwarded") == 1 || j.optBoolean("forwarded") || j.str("text").endsWith(" ↩ Forwarded"),
            deleted = j.str("type") == "deleted" || j.optInt("deleted") == 1 || j.optBoolean("deleted") || j.optBoolean("is_deleted") || j.strOrNull("deleted_at") != null,
            title = j.str("sender_title"))
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
    /** The site's ctxReport: {target_type:'message', target_id, reason}. */
    suspend fun report(m: ChatMsg, reason: String) = Api.post("/api/reports", JSONObject().put("target_type", "message").put("target_id", m.id).put("reason", reason))
    /** The site's doForward: {room_id, type, text, forwarded:true}. */
    suspend fun forwardText(toRoom: String, m: ChatMsg) = Api.post("/api/chat", JSONObject().put("room_id", toRoom).put("type", if (m.isSticker) "sticker" else "text")
        .put("text", m.text.removeSuffix(" ↩ Forwarded")).put("forwarded", true))
    suspend fun read(roomId: String) = Api.post("/api/chat/read", JSONObject().put("room_id", roomId))
    suspend fun openDm(userId: String): String = Api.post("/api/chat/rooms", JSONObject().put("type", "private").put("other_user_id", userId)).str("room_id")
    /** The site's createNewGroup {type,name,emoji,visibility,read_only} and createNewChannel {type,name,description,is_public,read_only:true}. */
    suspend fun create(type: String, name: String, desc: String, public: Boolean): String = Api.post("/api/chat/rooms",
        if (type == "channel") JSONObject().put("type", "channel").put("name", name).put("description", desc).put("is_public", public).put("read_only", true)
        else JSONObject().put("type", "group").put("name", name).put("emoji", "👥").put("visibility", if (public) "public" else "private").put("read_only", false)
            .also { if (desc.isNotBlank()) it.put("description", desc) }).let { r -> r.str("room_id").ifBlank { r.optJSONObject("room")?.str("id").orEmpty() }.ifBlank { r.str("id") } }
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
        // Exactly the site's _sendChatMedia: ?room_id&type&text&filename&ct, the bytes as application/octet-stream (the real type rides in ct).
        val url = "${Api.BASE}/api/chat?room_id=" + chEnc(roomId) + "&type=" + chEnc(type) + "&text=" + chEnc(text) + "&filename=" + chEnc(name) + "&ct=" + chEnc(mime) +
            (if (replyTo != null) "&reply_to_id=" + chEnc(replyTo) else "")
        val len = file?.length() ?: uri?.let { u -> runCatching { ctx.contentResolver.openAssetFileDescriptor(u, "r")?.use { it.length } }.getOrNull() } ?: -1L
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
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

/* ───────────────────────── Message cache: a chat you opened before opens instantly (memory, then the disk copy) ───────────────────────── */

object ChatCache {
    /** Bumps whenever a room's messages land, so the chat list re-reads its previews. */
    var version by mutableStateOf(0); private set
    private val mem = LruCache<String, List<ChatMsg>>(48)
    private val tried = HashSet<String>()
    private fun key(roomId: String) = Store.key("chat|$roomId")
    fun peek(roomId: String): List<ChatMsg>? = mem.get(roomId)
    /** Memory first, else the disk copy (only the last 60 messages, a few KB: fine on the main thread for the first frame). */
    fun get(roomId: String): List<ChatMsg>? {
        mem.get(roomId)?.let { return it }
        synchronized(tried) { if (!tried.add(roomId)) return null }
        val l = runCatching { chObjs(Disk.read(key(roomId))?.optJSONArray("messages")).map(ChatMsg::from) }.getOrNull().orEmpty()
        if (l.isNotEmpty()) mem.put(roomId, l)
        return l.ifEmpty { null }
    }
    fun put(roomId: String, raw: JSONArray?, parsed: List<ChatMsg>) {
        mem.put(roomId, parsed)
        if (raw != null) runCatching {
            val o = JSONArray(); val s = maxOf(0, raw.length() - 60); for (i in s until raw.length()) o.put(raw.get(i))
            Disk.write(key(roomId), JSONObject().put("messages", o))
        }
        version++
    }
    /** The newest message a chat list should show (system lines and call logs count: they have their own text). */
    fun last(roomId: String): ChatMsg? = (mem.get(roomId) ?: get(roomId))?.lastOrNull { !it.pending && !it.failed && (it.text.isNotBlank() || it.media != null || it.deleted) }

    private val gate = Semaphore(2)
    private val busy = HashSet<String>()
    /** Quietly fetches a room's messages (for a preview the site only gives as "[system]" / "[call]" / "[voice]"), once per app run. */
    suspend fun prefetch(roomId: String) {
        synchronized(busy) { if (!busy.add(roomId)) return }
        if (peek(roomId) != null) return
        gate.withPermit { runCatching { withContext(Dispatchers.IO) { val (j, l) = ChatApi.messages(roomId); put(roomId, j.optJSONArray("messages"), l) } } }
    }
}

/** True when the site's preview is only a marker ("[system]", "Yanky: [call]", "[voice]", "0:12|…") with no words of its own. */
fun chBarePreview(p: String): Boolean {
    val s = p.trim()
    return s.isEmpty() || Regex("""^(?:[^:\[\]]{1,40}:\s*)?\[\w+\]\s*$""").matches(s) || Regex("""^\d+:\d\d\|""").containsMatchIn(s) || s == "System message"
}

/** WhatsApp-style preview: "Yanky: text", "You: text", "📷 Photo", "🎤 Voice message 0:12", "Yanky joined"; the newest real message when the site only sends a marker. */
fun chatPreview(ri: ChatRoomInfo): String {
    val raw = ri.rawPreview
    val bare = chBarePreview(raw)
    val last = ChatCache.peek(ri.room.id)?.lastOrNull { !it.pending && !it.failed } ?: if (bare) ChatCache.last(ri.room.id) else null
    // Use our own copy when the site sent only a marker, or when our copy is at least as new as the list's last_time.
    val ours = last != null && (bare || ((parseTime(last.createdAt) ?: 0L) >= (parseTime(ri.room.lastTime) ?: Long.MAX_VALUE) - 1000))
    if (ours && last != null) {
        val me = Session.me?.id
        val body = last.preview
        return when {
            last.isCentred -> body
            last.senderId == me -> "You: $body"
            ri.isPrivate -> body
            last.nick.isNotBlank() -> last.nick + ": " + body
            else -> body
        }
    }
    if (bare) return when {
        raw.contains("[call]") -> "📞 Call"
        Regex("""\[(voice|audio)]""").containsMatchIn(raw) || Regex("""^\d+:\d\d\|""").containsMatchIn(raw) -> "🎤 Voice message"
        raw.contains("[system]") || raw.isBlank() -> if (ri.room.members > 0 && !ri.isPrivate) "${ri.room.members} members" else ""
        else -> chatRoomPreview(ri.room)
    }
    return chatRoomPreview(ri.room)
}

/* ───────────────────────── Link previews (the site's /api/link-preview and invite lookup) ───────────────────────── */

data class ChLink(val url: String, val title: String, val desc: String, val image: String?, val host: String,
                  val joinCode: String? = null, val roomName: String = "", val roomPhoto: String? = null, val roomMembers: Int = 0, val roomType: String = "")

object ChLinks {
    private val cache = HashMap<String, ChLink?>()
    private val gate = Semaphore(2)
    fun cached(url: String): ChLink? = synchronized(cache) { cache[url] }
    fun known(url: String): Boolean = synchronized(cache) { cache.containsKey(url) }
    /** The site's _detectJoinLink: yidplus.com/…?join=CODE (or yidplus.pages.dev). */
    fun joinCode(url: String): String? = Regex("""yidplus(?:\.com|\.pages\.dev)[^\s]*[?&]join=([a-zA-Z0-9-]+)""").find(url)?.groupValues?.get(1)
    fun youtubeId(url: String): String? = Regex("""(?:youtube\.com/(?:watch\?v=|shorts/|embed/)|youtu\.be/)([\w-]{11})""").find(url)?.groupValues?.get(1)
    suspend fun load(url: String): ChLink? {
        if (known(url)) return cached(url)
        val host = runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: ""
        val r = gate.withPermit { runCatching { withContext(Dispatchers.IO) {
            val code = joinCode(url)
            if (code != null) {
                val room = Api.get("/api/chat/rooms?invite=" + chEnc(code)).optJSONObject("room")
                room?.let { ChLink(url, it.str("name"), "", null, host, code, it.str("name").ifBlank { "Group" }, Api.abs(it.strOrNull("photo_url")), it.optInt("members"), it.str("type").ifBlank { "group" }) }
            } else {
                val j = Api.get("/api/link-preview?url=" + chEnc(url))
                val yt = youtubeId(url)
                val img = Api.abs(j.strOrNull("image")) ?: yt?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
                if (j.str("title").isBlank() && img == null) null else ChLink(url, j.str("title").ifBlank { host }, j.str("description"), img, host)
            }
        } }.getOrNull() }
        synchronized(cache) { cache[url] = r }
        return r
    }
}
