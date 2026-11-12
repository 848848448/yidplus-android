package com.yidplus.app.ui

import android.content.Context
import android.net.Uri
import com.yidplus.app.data.Api
import com.yidplus.app.data.Fixture
import com.yidplus.app.data.Session
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/* ═════════════ CL (chat list & chat management): the site's own API calls, one function each, same paths and bodies as js/chat.js ═════════════ */

data class ClFolder(val id: String, val name: String, val icon: String, val roomIds: List<String>)

object ClApi {
    private fun room(id: String) = JSONObject().put("room_id", id)

    /** chat.js loadChatFolders: GET /api/chat/folders */
    suspend fun folders(): List<ClFolder> = chObjs(Api.get("/api/chat/folders").optJSONArray("folders")).map { f ->
        val ids = runCatching { val raw = f.opt("room_ids"); val a = if (raw is JSONArray) raw else JSONArray(raw?.toString() ?: "[]"); (0 until a.length()).map { a.optString(it) } }.getOrDefault(emptyList())
        ClFolder(f.chStr("id"), f.chStr("name").ifBlank { "Folder" }, f.chStr("icon").ifBlank { "📁" }, ids)
    }
    /** chat.js createNewFolder: {name, icon:'📁', filter:'custom', room_ids, sort_order} (the site sends room_ids:[]; the app lets you pick chats first). */
    suspend fun createFolder(name: String, roomIds: List<String>, order: Int) = Api.post("/api/chat/folders", JSONObject().put("name", name.trim()).put("icon", "📁")
        .put("filter", "custom").put("room_ids", JSONArray(roomIds)).put("sort_order", order))
    /** chat.js deleteFolder */
    suspend fun deleteFolder(id: String) = Api.delete("/api/chat/folders?id=" + chEnc(id))

    /** chat.js deleteChatRoom / confirmLeaveGroup: DELETE /api/chat/rooms?room_id= */
    suspend fun deleteChat(roomId: String) = Api.delete("/api/chat/rooms?room_id=" + chEnc(roomId))
    /** chat-fixes loadMessages: POST /api/chat/read {room_id, receipts} */
    suspend fun markRead(c: Context, roomId: String) = Api.post("/api/chat/read", room(roomId).put("receipts", ClLocal.receipts(c)))

    /** One setting at a time, exactly like the site's toggles (toggleGroupReadOnly, _geToggle, _gePermToggle …): {room_id, <key>: value}. */
    suspend fun set(roomId: String, key: String, value: Any?) = Api.put("/api/chat/rooms", room(roomId).put(key, value ?: JSONObject.NULL))
    /** _geSaveText: {room_id, name, description} */
    suspend fun saveText(roomId: String, name: String, desc: String) = Api.put("/api/chat/rooms", room(roomId).put("name", name.trim()).put("description", desc))
    /** _geRevokeInvite: {room_id, revoke_invite:true} → new invite_code */
    suspend fun revokeInvite(roomId: String): String = Api.put("/api/chat/rooms", room(roomId).put("revoke_invite", true)).chStr("invite_code")
    /** promptSetMemberTitle: {room_id, member_id, member_title} (blank removes it) */
    suspend fun setTitle(roomId: String, memberId: String, title: String) = Api.put("/api/chat/rooms", room(roomId).put("member_id", memberId).put("member_title", title.trim()))
    /** chat.js openGroupEdit: GET /api/chat/rooms?group_settings= */
    suspend fun settings(roomId: String): JSONObject? = Api.get("/api/chat/rooms?group_settings=" + chEnc(roomId)).optJSONObject("settings")
    suspend fun joinRequests(roomId: String): List<JSONObject> = chObjs(Api.get("/api/chat/join-requests?room_id=" + chEnc(roomId)).optJSONArray("requests"))
    /** _answerJoinReq: {request_id, action:'approve'|'reject'} */
    suspend fun answerJoin(reqId: Any?, approve: Boolean) = Api.post("/api/chat/join-requests", JSONObject().put("request_id", reqId).put("action", if (approve) "approve" else "reject"))

    /** HTML handleGroupPhoto: multipart PUT /api/chat/rooms {room_id, photo}. Goes through the QA fixture first, like every Api call. */
    suspend fun setPhoto(c: Context, roomId: String, uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = chShrink(c, uri, null)
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("room_id", roomId)
            .addFormDataPart("photo", "group.jpg", bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())).build()
        val req = Request.Builder().url(Api.BASE + "/api/chat/rooms").put(body).header("Accept", "application/json").build()
        Fixture.answer(req)?.let { return@withContext it.chStr("photo_url") }
        Api.http.newCall(req).execute().use { r ->
            val t = r.body?.string() ?: ""
            val j = runCatching { JSONObject(t) }.getOrElse { JSONObject() }
            if (!r.isSuccessful) throw Api.ApiError(r.code, j.optString("error").ifBlank { "Upload failed (${r.code})" }, j)
            j.chStr("photo_url")
        }
    }

    /** HTML submitReport: {reported_id, room_id, message_id, reason, details} */
    suspend fun report(reportedId: String, roomId: String?, reason: String, details: String) = Api.post("/api/reports", JSONObject().put("reported_id", reportedId)
        .put("room_id", roomId ?: JSONObject.NULL).put("message_id", JSONObject.NULL).put("reason", reason).put("details", details))
    /** blocks.js: DELETE /api/blocks?id= */
    suspend fun unblock(userId: String) = Api.delete("/api/blocks?id=" + chEnc(userId))

    /** chat.js _handleInviteJoin: GET /api/invite?code= → {room:{name, visibility, members, …}} */
    suspend fun inviteInfo(code: String): JSONObject? = Api.get("/api/invite?code=" + chEnc(code)).optJSONObject("room")
    /** chat.js _handleInviteJoin: POST /api/invite {code} → {status:'joined'|'already_member'|'pending', room_id} */
    suspend fun inviteJoin(code: String): JSONObject = Api.post("/api/invite", JSONObject().put("code", code))

    /** media-history.js: GET /api/chat?room_id=…[&before=&before_id=] → messages, next_cursor, has_more */
    suspend fun history(roomId: String, before: String?, beforeId: String?): JSONObject =
        Api.get("/api/chat?room_id=" + chEnc(roomId) + (if (before != null) "&before=" + chEnc(before) + "&before_id=" + chEnc(beforeId ?: "") else ""))

    /** chat.js createGroup {type:'group', name, emoji, visibility, read_only: !everyoneCanWrite} / createChannel {type:'channel', name, description, is_public, read_only:true}. */
    suspend fun create(type: String, name: String, desc: String, public: Boolean, everyoneCanWrite: Boolean): String {
        val body = if (type == "channel") JSONObject().put("type", "channel").put("name", name).put("description", desc).put("is_public", public).put("read_only", true)
            else JSONObject().put("type", "group").put("name", name).put("emoji", "👥").put("visibility", if (public) "public" else "private").put("read_only", !everyoneCanWrite)
        return Api.post("/api/chat/rooms", body).chStr("room_id")
    }

    /** chat.js openMention / _openMemberDM: POST /api/chat/rooms {type:'private', other_user_id} */
    suspend fun dm(userId: String): String = ChatApi.openDm(userId)
}

/** Things the site keeps in the browser (localStorage) and the app keeps on the phone. */
object ClLocal {
    private fun p(c: Context) = c.getSharedPreferences("cl", Context.MODE_PRIVATE)
    private fun me() = Session.me?.id ?: "guest"

    // Chat pins: the site has no pin; WhatsApp-style, on this phone only (up to 5, newest pin first).
    fun pins(c: Context): List<String> = (p(c).getString("pins:" + me(), "") ?: "").split(',').filter { it.isNotBlank() }
    fun togglePin(c: Context, id: String): Boolean {
        val l = pins(c).toMutableList(); val on = id !in l
        if (on) { l.add(0, id); while (l.size > 5) l.removeAt(l.lastIndex) } else l.remove(id)
        p(c).edit().putString("pins:" + me(), l.joinToString(",")).apply(); pinVersion++; return on
    }
    var pinVersion by androidx.compose.runtime.mutableIntStateOf(0)

    // Mute until: the server keeps only on/off (PUT {room_id, muted}); "8 hours" / "1 week" are unmuted by the app when the time is up.
    fun muteUntil(c: Context, id: String): Long = p(c).getLong("mu:" + id, 0L)
    fun setMuteUntil(c: Context, id: String, until: Long) { p(c).edit().apply { if (until > 0) putLong("mu:" + id, until) else remove("mu:" + id) }.apply() }
    fun expiredMutes(c: Context): List<String> { val now = System.currentTimeMillis(); return p(c).all.filter { it.key.startsWith("mu:") && (it.value as? Long ?: 0L) in 1 until now }.map { it.key.removePrefix("mu:") } }

    // chat.js yp_chat_tab
    fun tab(c: Context): String = p(c).getString("tab", "all") ?: "all"
    fun setTab(c: Context, t: String) { p(c).edit().putString("tab", t).apply() }
    // chat.js yp_read_receipts (default on) — sent as "receipts" in POST /api/chat/read
    fun receipts(c: Context): Boolean = p(c).getBoolean("receipts", true)
    fun setReceipts(c: Context, on: Boolean) { p(c).edit().putBoolean("receipts", on).apply() }
    // chat.js yp_chat_font: sm / md / lg
    fun font(c: Context): String = p(c).getString("font", "md") ?: "md"
    fun setFont(c: Context, f: String) { p(c).edit().putString("font", f).apply(); fontVersion++ }
    var fontVersion by androidx.compose.runtime.mutableIntStateOf(0)
    fun fontScale(c: Context): Float = when (font(c)) { "sm" -> 0.88f; "lg" -> 1.12f; else -> 1f }
}

/** chat.js _autoDeleteLabel */
fun clAutoDeleteLabel(minutes: Int?): String = when {
    minutes == null || minutes <= 0 -> "Off"
    minutes >= 43200 -> "1 month"; minutes >= 10080 -> "1 week"; minutes >= 1440 -> "1 day"; minutes >= 60 -> "1 hour"
    else -> "$minutes min"
}
val CL_AUTO_DELETE = listOf(0 to "Off", 60 to "1 hour", 1440 to "1 day", 10080 to "1 week", 43200 to "1 month")
