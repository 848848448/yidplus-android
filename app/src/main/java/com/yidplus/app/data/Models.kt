package com.yidplus.app.data

import org.json.JSONObject

data class Me(val id: String, val nickname: String, val email: String, val photo: String?, val role: String, val isOwner: Boolean) {
    companion object {
        fun from(j: JSONObject) = Me(j.str("id"), j.str("nickname"), j.str("email"), Api.abs(j.strOrNull("photo_url")), j.str("role"), j.optBoolean("is_owner"))
    }
    fun json(): JSONObject = JSONObject().put("id", id).put("nickname", nickname).put("email", email).put("photo_url", photo).put("role", role).put("is_owner", isOwner)
}

data class TgChannel(val username: String, val title: String, val photo: String?, val members: Int, val lastText: String,
                     val lastMedia: String, val lastAt: String?, val unread: Int, val joined: Boolean) {
    companion object {
        fun from(j: JSONObject) = TgChannel(j.str("username"), j.str("title").ifBlank { j.str("username") }, Api.abs(j.strOrNull("photo_url")),
            j.optInt("members"), j.str("last_text"), j.str("last_media"), j.strOrNull("last_post_at"), j.optInt("unread"), j.optBoolean("joined"))
        fun list(j: JSONObject) = j.optJSONArray("channels").objects().map(::from)
    }
}

data class TgPost(val id: Long, val text: String, val mediaType: String, val media: String?, val title: String, val performer: String,
                  val duration: Int, val fileName: String, val link: String, val postedAt: String?, val views: Int, val author: String) {
    val isVideo get() = mediaType == "video" || mediaType == "animation" || mediaType == "video_note"
    val isPhoto get() = mediaType == "photo"
    val isAudio get() = mediaType == "audio" || mediaType == "voice"
    val thumb: String? get() = media?.let { if (isVideo) it + (if (it.contains('?')) "&" else "?") + "thumb=1&tv=2" else it }
    companion object {
        fun from(username: String, j: JSONObject): TgPost {
            val id = j.optLong("tg_msg_id")
            val type = j.str("media_type")
            val media = Api.abs(j.strOrNull("media_url")) ?: if (type.isNotBlank()) "${Api.BASE}/api/tg/media?ch=$username&id=$id" else null
            return TgPost(id, j.str("text"), type, media, j.str("media_title"), j.str("media_performer"), j.optInt("media_duration"),
                j.str("media_name"), j.str("link"), j.strOrNull("posted_at"), j.optInt("views"), j.str("author_name"))
        }
    }
}

/** The site's raw room previews ("[system]", "[call]", "[image]") read like an app: "System message", "📞 Call", "📷 Photo". */
fun prettyPreview(p: String): String {
    val s = p.trim()
    val m = Regex("""^(?:([^:\[\]]{1,40}):\s*)?\[(\w+)\]\s*(.*)$""").find(s) ?: return s
    val who = m.groupValues[1].let { if (it.isBlank()) "" else "$it: " }
    val rest = m.groupValues[3]
    val label = when (m.groupValues[2].lowercase()) {
        "system" -> "System message"
        "call", "voice_call" -> "📞 Call"; "video_call" -> "📹 Video call"
        "image", "photo" -> "📷 Photo"; "video" -> "🎬 Video"; "audio", "voice" -> "🎤 Voice message"
        "file", "document" -> "📎 File"; "sticker" -> "Sticker"; "gif" -> "GIF"; "poll" -> "📊 Poll"; "location" -> "📍 Location"
        else -> return s
    }
    return who + label + if (rest.isNotBlank()) " $rest" else ""
}

data class Room(val id: String, val type: String, val name: String, val photo: String?, val emoji: String, val preview: String,
                val unread: Int, val lastTime: String?, val readOnly: Boolean, val guestView: Boolean, val members: Int, val otherUserId: String?) {
    companion object {
        fun from(j: JSONObject) = Room(j.str("id"), j.str("type"), j.str("nick").ifBlank { j.str("name") }, Api.abs(j.strOrNull("photo_url")),
            j.str("emoji"), prettyPreview(j.str("preview")), j.optInt("unread"), j.strOrNull("last_time"), j.optBoolean("read_only"), j.optBoolean("guest_view"),
            j.optInt("members"), j.strOrNull("other_user_id"))
        fun list(j: JSONObject) = j.optJSONArray("rooms").objects().map(::from)
    }
    fun json(): JSONObject = JSONObject().put("id", id).put("type", type).put("nick", name).put("photo_url", photo).put("emoji", emoji)
        .put("read_only", readOnly).put("guest_view", guestView).put("members", members).put("other_user_id", otherUserId)
}

data class Msg(val id: String, val senderId: String, val nick: String, val photo: String?, val type: String, val text: String,
               val media: String?, val mediaKey: String, val createdAt: String?, val pending: Boolean = false, val failed: Boolean = false) {
    private val ext get() = mediaKey.substringAfterLast('.', "").lowercase().substringBefore('?')
    val isImage get() = media != null && (type == "sticker" || ext in setOf("jpg", "jpeg", "png", "gif", "webp", "heic") || (type == "media" && ext.isEmpty()))
    val isVideo get() = media != null && ext in setOf("mp4", "mov", "webm", "3gp", "mkv")
    val isAudio get() = media != null && (type == "voice" || ext in setOf("mp3", "m4a", "ogg", "opus", "wav", "aac", "webm") && !isVideo)
    companion object {
        fun from(j: JSONObject) = Msg(j.str("id"), j.str("sender_id"), j.str("sender_nick"), Api.abs(j.strOrNull("sender_photo")), j.str("type"),
            j.str("text"), Api.abs(j.strOrNull("media_url")) ?: j.strOrNull("media_key")?.let { "${Api.BASE}/api/media/" + java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") },
            j.str("media_key"), j.strOrNull("created_at"))
    }
}

/** One Shorts video, from the For You feed (outside sources) or the site's own Uploads. */
data class Clip(val key: String, val fromSrc: Boolean, val video: String, val poster: String?, val caption: String, val creator: String,
                 val creatorId: String, val avatar: String?, val verified: Boolean, val duration: Int, val size: Long,
                 val likes: Int, val comments: Int, val liked: Boolean, val shareId: String, val at: String?) {
    val shareUrl get() = if (fromSrc) "${Api.BASE}/shorts?v=" + java.net.URLEncoder.encode(shareId, "UTF-8") else "${Api.BASE}/shorts#$key"
    companion object {
        fun srcPage(j: JSONObject): List<Clip> {
            val creators = j.optJSONObject("creators") ?: JSONObject()
            return j.optJSONArray("items").objects().map { it ->
                val cr = creators.optJSONObject(it.str("cr"))
                Clip(it.str("id"), true, it.str("u"), it.strOrNull("p"), it.str("c"), cr?.str("n") ?: "", it.str("cr"),
                    cr?.strOrNull("a"), cr?.optInt("v") == 1, it.optInt("d"), it.optLong("z"), 0, 0, false, it.str("v"), it.strOrNull("t"))
            }
        }
        fun upload(j: JSONObject) = Clip(j.str("id"), false, Api.abs(j.str("media_url"))!!, null, j.str("caption"), j.str("nick"), j.str("owner_id"),
            Api.abs(j.strOrNull("photo_url")), j.optBoolean("verified"), 0, 0, j.optInt("likes"), j.optInt("comments"), j.optBoolean("liked"), j.str("id"), j.strOrNull("created_at"))
    }
}

data class Comment(val id: String, val userId: String, val nick: String, val photo: String?, val text: String, val at: String?, val mine: Boolean)

data class Track(val id: String, val title: String, val artist: String, val cover: String?, val audio: String?, val owner: String,
                 val ownerPhoto: String?, val plays: Int, val liked: Boolean) {
    companion object {
        fun from(j: JSONObject) = Track(j.str("id"), j.str("title").ifBlank { "Untitled" }, j.str("artist").ifBlank { j.str("owner_nick") },
            Api.abs(j.strOrNull("cover_url")), Api.abs(j.strOrNull("audio_url")) ?: Api.abs(j.strOrNull("video_url")), j.str("owner_nick"),
            Api.abs(j.strOrNull("owner_photo")), j.optInt("plays"), j.optBoolean("liked"))
        fun list(j: JSONObject) = j.optJSONArray("tracks").objects().map(::from).filter { it.audio != null }
    }
}

data class Notif(val id: String, val type: String, val actor: String, val text: String, val link: String, val read: Boolean, val at: String?) {
    companion object {
        fun from(j: JSONObject) = Notif(j.str("id"), j.str("type"), j.str("actor_nick"), j.str("text"), j.str("link"), j.optInt("read") == 1 || j.optBoolean("read"), j.strOrNull("created_at"))
    }
}
