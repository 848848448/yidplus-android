package com.yidplus.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.yidplus.app.data.Api
import com.yidplus.app.data.Fixture
import com.yidplus.app.data.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Chat notifications like WhatsApp: one notification per chat (MessagingStyle, a conversation shortcut on Android 11+),
 * each message with its sender's name and round photo (a coloured first letter when there is none), the group's photo
 * and name for groups, a summary when several chats wait, inline Reply (sent with the signed-in session, "Not sent" +
 * Retry when it fails) and Mark as read (marks the room read on the site and removes the notification).
 * A per-room "seen" record (newest message time + ids) makes sure a message that was shown, read, replied to or swiped
 * away never alerts again, also when the site keeps counting the room unread (spectated rooms, a failed read call).
 * Both paths end here: the Firebase push and the background checks both run NotifyWorker.check -> ChatNotify.build.
 */
object ChatNotify {
    const val TAG = "YidNotify"
    const val KEY_REPLY = "chat_reply"
    const val ACTION_REPLY = "com.yidplus.app.CHAT_REPLY"
    const val ACTION_READ = "com.yidplus.app.CHAT_READ"
    const val ACTION_RETRY = "com.yidplus.app.CHAT_RETRY"
    const val ACTION_DISMISS = "com.yidplus.app.CHAT_DISMISS"
    const val GROUP = "com.yidplus.app.CHATS"
    private const val MAX_STACK = 8
    private val palette = intArrayOf(0xFF1F7AE0.toInt(), 0xFFD9534F.toInt(), 0xFF8E44AD.toInt(), 0xFFE67E22.toInt(), 0xFF16A085.toInt(), 0xFFC2185B.toInt(), 0xFF2E7D32.toInt(), 0xFF6D4C41.toInt())

    private fun store(c: Context) = c.getSharedPreferences("chat-notif", Context.MODE_PRIVATE)
    private fun seenStore(c: Context) = c.getSharedPreferences("chat-seen", Context.MODE_PRIVATE)
    private fun me(c: Context): JSONObject? = runCatching { c.getSharedPreferences("session", Context.MODE_PRIVATE).getString("me", null)?.let { JSONObject(it) } }.getOrNull()
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    fun shortcutId(roomId: String) = "chat_$roomId"

    // ---- seen record -------------------------------------------------------------------------------------------

    private fun seenOf(c: Context, roomId: String): Pair<Long, Set<String>> = runCatching {
        val j = JSONObject(seenStore(c).getString(roomId, null)!!)
        val a = j.optJSONArray("ids") ?: JSONArray()
        j.optLong("t") to (0 until a.length()).map { a.optString(it) }.toSet()
    }.getOrDefault(0L to emptySet())

    private fun markSeen(c: Context, roomId: String, t: Long, ids: Collection<String>) {
        val (oldT, oldIds) = seenOf(c, roomId)
        val all = (oldIds.toList() + ids.filter { it.isNotBlank() }).distinct().takeLast(60)
        seenStore(c).edit().putString(roomId, JSONObject().put("t", maxOf(oldT, t)).put("ids", JSONArray(all)).toString()).apply()
    }

    /** True when everything up to [lastTime] (the room list's last_time) was already shown, read or dismissed. */
    fun alreadySeen(c: Context, roomId: String, lastTime: String?): Boolean {
        val t = parseTime(lastTime) ?: return false
        return t <= seenOf(c, roomId).first
    }

    /** The first check after install: what is unread now is old news, don't alert it. */
    fun markSeenUpTo(c: Context, roomId: String, lastTime: String?) { parseTime(lastTime)?.let { markSeen(c, roomId, it, emptyList()) } }

    private fun stack(c: Context, roomId: String): JSONObject? = runCatching { JSONObject(store(c).getString(roomId, null)!!) }.getOrNull()

    /** Everything this notification showed counts as seen (read, opened, replied, swiped away). */
    private fun seenFromStack(c: Context, roomId: String) {
        val s = stack(c, roomId) ?: return
        val msgs = s.optJSONArray("msgs") ?: JSONArray()
        val ids = (0 until msgs.length()).mapNotNull { msgs.optJSONObject(it)?.optString("id") }
        markSeen(c, roomId, s.optLong("newest"), ids)
    }

    /** The chat was opened in the app (or the site says it is read): mark it seen, remove its notification. */
    fun clear(c: Context, roomId: String) {
        runCatching {
            seenFromStack(c, roomId)
            store(c).edit().remove(roomId).apply()
            NotificationManagerCompat.from(c).cancel("chat", roomId.hashCode())
            updateSummary(c)
        }.onFailure { Log.w(TAG, "clear $roomId: ${it.message}") }
    }

    // ---- message text ------------------------------------------------------------------------------------------

    fun parseTime(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .parse(s.trim().replace(' ', 'T').take(19))!!.time
        }.getOrNull()
    }

    private fun flag(m: JSONObject, k: String) = m.optInt(k) == 1 || m.optBoolean(k)

    /** What a message says in one line, for every type the site renders (never empty). */
    fun textOf(m: JSONObject): String {
        if (flag(m, "deleted") || flag(m, "is_deleted")) return "🚫 This message was deleted"
        val t = m.optString("text", "").let { if (it == "null") "" else it }.trim()
        val cap = t.takeIf { it.isNotBlank() && it != "__once__" }
        val key = (m.optString("media_key") + " " + m.optString("media_url")).lowercase()
        val isVideo = Regex("""\.(mp4|mov|webm|3gp|mkv|m4v)(\?|\s|$)""").containsMatchIn(key)
        val s = when (m.optString("type")) {
            "voice" -> "🎤 Voice message" + (m.optString("duration").takeIf { Regex("""^\d+:\d\d$""").matches(it) }?.let { " $it" } ?: "")
            "voice_text" -> "🎤 Voice message" + (t.substringBefore('|').takeIf { Regex("""^\d+:\d\d$""").matches(it) }?.let { " $it" } ?: "")
            "video_msg" -> "📹 Video message"
            "sticker" -> "💟 Sticker"
            "gif" -> "GIF"
            "poll" -> "📊 Poll" + (m.optString("question").ifBlank { m.optJSONObject("poll")?.optString("question").orEmpty() }.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
            "music" -> "🎵 " + (cap ?: m.optString("media_key").substringAfterLast('/').substringAfter('_').ifBlank { "Music" })
            "file" -> "📎 " + (cap ?: m.optString("media_key").substringAfterLast('/').ifBlank { "File" })
            "location" -> "📍 Location"
            "contact" -> "👤 Contact"
            "call" -> t.ifBlank { "📞 Call" }
            "system" -> t
            "media" -> when {
                t == "__once__" -> if (isVideo) "👁 View once video" else "👁 View once photo"
                else -> (if (isVideo) "🎬 Video" else "📷 Photo") + (cap?.let { " $it" } ?: "")
            }
            else -> when {
                cap != null -> cap.removeSuffix(" ↩ Forwarded")
                m.optString("media_url").isNotBlank() -> if (isVideo) "🎬 Video" else "📷 Photo"
                else -> ""
            }
        }
        return s.ifBlank { "New message" }.take(700)
    }

    private fun entry(m: JSONObject) = JSONObject().put("id", m.optString("id")).put("nick", m.optString("sender_nick"))
        .put("sid", m.optString("sender_id")).put("photo", Api.abs(m.optString("sender_photo").takeIf { it.isNotBlank() && it != "null" }))
        .put("text", textOf(m)).put("t", parseTime(m.optString("created_at")) ?: System.currentTimeMillis())

    // ---- avatars ----------------------------------------------------------------------------------------------

    /** Round avatar: the photo (downloaded with the session, scaled down, cached on disk), else a coloured circle with the first letter. */
    fun avatar(c: Context?, name: String, key: String, url: String?): Bitmap {
        val size = 128
        val u = Api.abs(url?.takeIf { it.isNotBlank() && it != "null" })
        if (u != null) {
            val f = c?.let { File(it.cacheDir, "notif-av/" + Integer.toHexString(u.hashCode()) + ".png") }
            if (f != null && f.exists() && System.currentTimeMillis() - f.lastModified() < 3L * 86_400_000) {
                BitmapFactory.decodeFile(f.path)?.let { return it }
            }
            try {
                val http = Api.http.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
                http.newCall(Request.Builder().url(u).build()).execute().use { r ->
                    if (!r.isSuccessful) Log.w(TAG, "avatar ${r.code} for $u")
                    else {
                        val bytes = r.body!!.bytes()
                        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                        var sample = 1
                        while (minOf(o.outWidth, o.outHeight) / (sample * 2) >= size) sample *= 2
                        val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                        if (bm == null) Log.w(TAG, "avatar not an image (${bytes.size} bytes) $u")
                        else {
                            val out = round(bm, size)
                            if (f != null) runCatching { f.parentFile?.mkdirs(); f.outputStream().use { s -> out.compress(Bitmap.CompressFormat.PNG, 100, s) } }
                            return out
                        }
                    }
                }
            } catch (e: Throwable) { Log.w(TAG, "avatar download failed for $u: ${e.javaClass.simpleName} ${e.message}") }
        }
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); val cv = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette[(key.ifBlank { name }.hashCode() and 0x7fffffff) % palette.size] }
        cv.drawCircle(size / 2f, size / 2f, size / 2f, p)
        val ini = (name.firstOrNull { Character.isLetterOrDigit(it) } ?: '?').toString().uppercase()
        p.color = 0xFFFFFFFF.toInt(); p.textSize = size * 0.46f; p.textAlign = Paint.Align.CENTER; p.isFakeBoldText = true
        val fm = p.fontMetrics
        cv.drawText(ini, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, p)
        return b
    }
    fun avatar(name: String, key: String, url: String?): Bitmap = avatar(null, name, key, url)

    private fun round(src: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); val cv = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        cv.drawOval(RectF(0f, 0f, size.toFloat(), size.toFloat()), p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val s = minOf(src.width, src.height)
        cv.drawBitmap(src, Rect((src.width - s) / 2, (src.height - s) / 2, (src.width + s) / 2, (src.height + s) / 2), Rect(0, 0, size, size), p)
        return out
    }

    // ---- build ------------------------------------------------------------------------------------------------

    /**
     * Reads this chat's newest messages and builds its notification with the new ones stacked under what is already
     * shown. Null means nothing new to alert (all already shown, read or written by me) — the caller posts nothing.
     * If the chat can't be read, it still builds the full notification (photo, Reply, Mark as read) from the room
     * list's preview, and logs why (tag YidNotify).
     */
    suspend fun build(c: Context, r: Room): android.app.Notification? {
        val my = me(c); val myId = my?.optString("id").orEmpty()
        val (seenT, seenIds) = seenOf(c, r.id)
        val saved = stack(c, r.id)?.optJSONArray("msgs") ?: JSONArray()
        val stackIds = (0 until saved.length()).mapNotNull { saved.optJSONObject(it)?.optString("id") }.toSet()
        var newest = stack(c, r.id)?.optLong("newest") ?: 0L
        val loaded: List<JSONObject>? = try {
            val j = Api.get("/api/chat?room_id=" + enc(r.id))
            val a = j.optJSONArray("messages")
            if (a == null) { Log.w(TAG, "room ${r.id}: the chat answer has no messages (keys ${j.keys().asSequence().take(6).joinToString()}); using the list preview"); null }
            else (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        } catch (e: Exception) { Log.w(TAG, "room ${r.id}: reading the chat failed (${e.javaClass.simpleName}: ${e.message}); using the list preview"); null }
        val add = ArrayList<JSONObject>()
        if (!loaded.isNullOrEmpty()) {
            for (m in loaded) { val t = parseTime(m.optString("created_at")) ?: 0L; if (t > newest) newest = t }
            val news = loaded.filter { m ->
                val id = m.optString("id"); val t = parseTime(m.optString("created_at")) ?: 0L
                (myId.isEmpty() || m.optString("sender_id") != myId) && m.optString("type") != "system" &&
                    id !in seenIds && id !in stackIds && (t == 0L || t > seenT)
            }.takeLast(r.unread.coerceIn(1, MAX_STACK))
            if (news.isEmpty()) {
                Log.i(TAG, "room ${r.id}: nothing new to alert")
                markSeen(c, r.id, newest, emptyList())
                return null
            }
            for (m in news) runCatching { add.add(entry(m)) }.onFailure { Log.w(TAG, "room ${r.id}: message ${m.optString("id")} skipped: ${it.message}") }
        } else {
            val pv = r.preview.takeIf { it.isNotBlank() && it != "System message" } ?: "New message"
            val dm = r.type == "private"
            val nick = if (!dm && pv.contains(": ")) pv.substringBefore(": ") else if (dm) r.name else ""
            val text = if (!dm && pv.contains(": ")) pv.substringAfter(": ") else pv
            val id = "pv:" + (r.lastTime ?: "")
            if (id in stackIds || id in seenIds) return null
            val t = parseTime(r.lastTime) ?: System.currentTimeMillis()
            if (t > newest) newest = t
            add.add(JSONObject().put("id", id).put("nick", nick.ifBlank { r.name }).put("sid", if (dm) (r.otherUserId ?: r.id) else "pv:" + nick)
                .put("photo", if (dm) r.photo else null).put("text", text).put("t", t))
        }
        val msgs = JSONArray()
        val keep = (0 until saved.length()).mapNotNull { saved.optJSONObject(it) } + add
        keep.takeLast(MAX_STACK).forEach { msgs.put(it) }
        saveStack(c, r, msgs, newest)
        return compose(c, r, msgs, myId, my?.optString("nickname").orEmpty(), my?.optString("photo_url"))
    }

    private fun saveStack(c: Context, r: Room, msgs: JSONArray, newest: Long) {
        store(c).edit().putString(r.id, JSONObject().put("room", r.json()).put("msgs", msgs).put("newest", newest).toString()).apply()
    }

    private fun mutableFlag() = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0

    private fun broadcast(c: Context, code: Int, action: String, r: Room, mutable: Boolean, extra: String? = null): PendingIntent {
        val i = Intent(c, ChatReplyReceiver::class.java).setAction(action).putExtra("room", r.json().toString())
        if (extra != null) i.putExtra("text", extra)
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT or (if (mutable) mutableFlag() else PendingIntent.FLAG_IMMUTABLE))
    }

    private fun compose(c: Context, r: Room, msgs: JSONArray, myId: String, myName: String, myPhoto: String?, alertOnce: Boolean = false): android.app.Notification {
        val icons = HashMap<String, Bitmap>()
        fun icon(name: String, key: String, url: String?) = icons.getOrPut(key.ifBlank { name }) { avatar(c, name, key, url) }
        val mePerson = Person.Builder().setName(myName.ifBlank { "You" }).setKey(myId.ifBlank { "me" })
            .apply { if (!myPhoto.isNullOrBlank() && myPhoto != "null") setIcon(IconCompat.createWithBitmap(icon(myName, myId.ifBlank { "me" }, myPhoto))) }.build()
        val group = r.type != "private"
        val style = NotificationCompat.MessagingStyle(mePerson).setGroupConversation(group)
        if (group) style.setConversationTitle(r.name)
        var lastSender: Person? = null; var lastSenderIcon: Bitmap? = null
        var others = 0; var lastFailed: String? = null; var lastT = 0L
        for (i in 0 until msgs.length()) {
            val m = msgs.optJSONObject(i) ?: continue
            val mine = m.optBoolean("mine")
            val t = m.optLong("t", System.currentTimeMillis()); if (t > lastT) lastT = t
            val p = if (mine) null else {
                val nick = m.optString("nick").ifBlank { r.name }
                val bm = icon(nick, m.optString("sid"), m.optString("photo"))
                others++; lastSenderIcon = bm
                Person.Builder().setName(nick).setKey(m.optString("sid").ifBlank { nick }).setIcon(IconCompat.createWithBitmap(bm)).build().also { lastSender = it }
            }
            lastFailed = if (mine && m.optBoolean("failed")) m.optString("raw") else if (mine) null else lastFailed
            style.addMessage(NotificationCompat.MessagingStyle.Message(m.optString("text").ifBlank { "New message" }, t, p))
        }
        val code = r.id.hashCode()
        val open = Intent(c, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_CHAT).putExtra("room", r.json().toString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val reply = NotificationCompat.Action.Builder(R.drawable.ic_stat_logo, "Reply", broadcast(c, code, ACTION_REPLY, r, true))
            .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel(if (group) "Reply to ${r.name}" else "Reply").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).setShowsUserInterface(false).setAllowGeneratedReplies(true).build()
        val read = NotificationCompat.Action.Builder(R.drawable.ic_stat_logo, "Mark as read", broadcast(c, code + 1, ACTION_READ, r, false))
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).setShowsUserInterface(false).build()
        val large = if (group) icon(r.name, r.id, r.photo) else if (!r.photo.isNullOrBlank()) icon(r.name, r.otherUserId ?: r.id, r.photo) else (lastSenderIcon ?: icon(r.name, r.otherUserId ?: r.id, null))
        val sid = shortcutId(r.id)
        pushShortcut(c, r, large, open, if (group) null else lastSender)
        val last = (0 until msgs.length()).mapNotNull { msgs.optJSONObject(it) }.lastOrNull()
        val b = NotificationCompat.Builder(c, NotifyWorker.CH_CHATS).setSmallIcon(R.drawable.ic_stat_logo).setColor(0xFF1F6F5C.toInt())
            .setStyle(style).setLargeIcon(large)
            .setContentTitle(r.name).setContentText(last?.let { (if (group && !it.optBoolean("mine")) it.optString("nick") + ": " else "") + it.optString("text") } ?: "New message")
            .setWhen(if (lastT > 0) lastT else System.currentTimeMillis()).setShowWhen(true)
            .setNumber(others).setCategory(NotificationCompat.CATEGORY_MESSAGE).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setShortcutId(sid).setLocusId(LocusIdCompat(sid))
            .setGroup(GROUP).setAutoCancel(true).setOnlyAlertOnce(alertOnce)
            .addAction(reply)
        if (lastFailed != null) b.addAction(NotificationCompat.Action.Builder(R.drawable.ic_stat_logo, "Retry", broadcast(c, code + 2, ACTION_RETRY, r, false, lastFailed)).setShowsUserInterface(false).build())
        b.addAction(read)
        b.setDeleteIntent(broadcast(c, code + 3, ACTION_DISMISS, r, false))
        b.setContentIntent(PendingIntent.getActivity(c, code, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return b.build()
    }

    /** Android 11+: a long-lived conversation shortcut, so the system shows the chat as a conversation with its photo. */
    private fun pushShortcut(c: Context, r: Room, icon: Bitmap, open: Intent, person: Person?) {
        if (Build.VERSION.SDK_INT < 25) return
        runCatching {
            val sb = ShortcutInfoCompat.Builder(c, shortcutId(r.id)).setShortLabel(r.name.ifBlank { "Chat" }.take(24)).setLongLabel(r.name.ifBlank { "Chat" })
                .setIcon(IconCompat.createWithBitmap(icon)).setIntent(Intent(open)).setLongLived(true).setLocusId(LocusIdCompat(shortcutId(r.id)))
            if (person != null) sb.setPerson(person)
            ShortcutManagerCompat.pushDynamicShortcut(c, sb.build())
        }.onFailure { Log.w(TAG, "shortcut for ${r.id} failed: ${it.message}") }
    }

    /** Re-post this chat's notification from what it already shows (after a reply, quietly). */
    private fun repost(c: Context, r: Room, msgs: JSONArray, newest: Long) {
        val my = me(c)
        saveStack(c, r, msgs, newest)
        runCatching { NotificationManagerCompat.from(c).notify("chat", r.id.hashCode(), compose(c, r, msgs, my?.optString("id").orEmpty(), my?.optString("nickname").orEmpty(), my?.optString("photo_url"), alertOnce = true)) }
            .onFailure { Log.w(TAG, "repost ${r.id} failed: ${it.message}") }
        updateSummary(c)
    }

    /** After an inline reply: my line joins the stack ("Not sent" + Retry when it failed). Replying counts as reading. */
    fun addMine(c: Context, r: Room, text: String, ok: Boolean) {
        val s = stack(c, r.id)
        val msgs = s?.optJSONArray("msgs") ?: JSONArray()
        if (ok) { seenFromStack(c, r.id) }
        // A retried line replaces its "Not sent" copy.
        val kept = JSONArray()
        for (i in 0 until msgs.length()) { val m = msgs.optJSONObject(i) ?: continue; if (!(m.optBoolean("failed") && m.optString("raw") == text)) kept.put(m) }
        kept.put(JSONObject().put("mine", true).put("id", "me:" + System.currentTimeMillis()).put("raw", text).put("failed", !ok)
            .put("text", if (ok) text else "⚠ Not sent: $text").put("t", System.currentTimeMillis()))
        val trimmed = JSONArray(); val list = (0 until kept.length()).mapNotNull { kept.optJSONObject(it) }.takeLast(MAX_STACK); list.forEach { trimmed.put(it) }
        repost(c, r, trimmed, s?.optLong("newest") ?: 0L)
    }

    /** Re-post what the notification shows (clears the reply spinner when nothing was sent). */
    fun refresh(c: Context, r: Room) {
        val s = stack(c, r.id) ?: return
        repost(c, r, s.optJSONArray("msgs") ?: JSONArray(), s.optLong("newest"))
    }

    /** Mark as read pressed: everything shown counts as seen and the notification goes away. */
    fun markRead(c: Context, roomId: String) = clear(c, roomId)

    /** Swiped away: not read on the site (like WhatsApp), but these messages never alert again. */
    fun dismissed(c: Context, roomId: String) {
        seenFromStack(c, roomId); store(c).edit().remove(roomId).apply(); updateSummary(c)
    }

    /** One summary for several chats (Android groups them under it). */
    fun updateSummary(c: Context) {
        runCatching {
            val nm = c.getSystemService(NotificationManager::class.java) ?: return
            val active = nm.activeNotifications.filter { it.tag == "chat" }.sortedByDescending { it.notification.`when` }
            val nmc = NotificationManagerCompat.from(c)
            if (active.size < 2) { nmc.cancel("chat-summary", 1); return }
            val style = NotificationCompat.InboxStyle()
            var count = 0
            for (sbn in active) {
                val ex = sbn.notification.extras
                count += sbn.notification.number.coerceAtLeast(1)
                if (active.indexOf(sbn) < 6) style.addLine("${ex.getCharSequence("android.title") ?: ""}: ${ex.getCharSequence("android.text") ?: ""}")
            }
            val line = "$count messages from ${active.size} chats"
            style.setBigContentTitle("YID PLUS").setSummaryText(line)
            nmc.notify("chat-summary", 1, NotificationCompat.Builder(c, NotifyWorker.CH_CHATS).setSmallIcon(R.drawable.ic_stat_logo).setColor(0xFF1F6F5C.toInt())
                .setContentTitle("YID PLUS").setContentText(line).setStyle(style).setNumber(count)
                .setGroup(GROUP).setGroupSummary(true).setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN).setOnlyAlertOnce(true).setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(PendingIntent.getActivity(c, 2, Intent(c, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_CHATS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
        }.onFailure { Log.w(TAG, "summary failed: ${it.message}") }
    }

    /** QA only (adb with the DUMP permission): a sample group chat notification, to photograph the expanded shade with its reply box. */
    fun demo(c: Context) {
        NotifyWorker.channels(c)
        val r = Room("qa-demo-room", "group", "YID PLUS Chat", Api.abs("/api/media/user-avatars/392d8eda-e0dd-44ea-a51e-f55ead12fb0c_1785879385829.png"), "", "", 3, null, false, false, 12, null)
        val now = System.currentTimeMillis()
        val msgs = JSONArray()
            .put(JSONObject().put("id", "d1").put("nick", "Moshe Klein").put("sid", "u1").put("text", "גוט מארגן! ווער קומט היינט צום שיעור?").put("t", now - 120_000))
            .put(JSONObject().put("id", "d2").put("nick", "Yossi Weiss").put("sid", "u2").put("text", "📷 Photo").put("t", now - 60_000))
            .put(JSONObject().put("id", "d3").put("nick", "Moshe Klein").put("sid", "u1").put("text", "I'll be there at 8").put("t", now - 5_000))
        saveStack(c, r, msgs, now)
        runCatching { NotificationManagerCompat.from(c).notify("chat", r.id.hashCode(), compose(c, r, msgs, "me", "You", null)) }
    }

    /** QA only: forget every notification record (the caller then runs a first, silent check). */
    fun qaReset(c: Context) {
        store(c).edit().clear().commit(); seenStore(c).edit().clear().commit()
        c.getSharedPreferences("notify-state", Context.MODE_PRIVATE).edit().clear().commit()
        NotificationManagerCompat.from(c).cancelAll()
    }
}

/** Reply, Retry, Mark as read and swipe-away straight from the notification, without opening the app. */
class ChatReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.applicationContext
        val r = runCatching { Room.from(JSONObject(intent.getStringExtra("room")!!)) }.getOrNull() ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Api.init(c)
                when (intent.action) {
                    ChatNotify.ACTION_REPLY, ChatNotify.ACTION_RETRY -> {
                        val text = (if (intent.action == ChatNotify.ACTION_RETRY) intent.getStringExtra("text")
                            else RemoteInput.getResultsFromIntent(intent)?.getCharSequence(ChatNotify.KEY_REPLY)?.toString())?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            val res = runCatching {
                                val qa = c.getSharedPreferences("qa-nt", Context.MODE_PRIVATE)
                                if (Fixture.on && qa.getBoolean("failNext", false)) { qa.edit().putBoolean("failNext", false).commit(); throw java.io.IOException("QA: simulated network failure") }
                                Api.post("/api/chat", JSONObject().put("room_id", r.id).put("type", "text").put("text", text))
                            }
                            val ok = res.isSuccess
                            Log.i(ChatNotify.TAG, "reply room=${r.id} sent=$ok" + (res.exceptionOrNull()?.let { " (${it.javaClass.simpleName}: ${it.message})" } ?: ""))
                            if (ok) runCatching { Api.post("/api/chat/read", JSONObject().put("room_id", r.id)) }
                            ChatNotify.addMine(c, r, text, ok)
                            NotifyWorker.burst(c, 15)
                        } else { Log.w(ChatNotify.TAG, "reply room=${r.id}: empty text"); ChatNotify.refresh(c, r) }
                    }
                    ChatNotify.ACTION_READ -> {
                        val ok = runCatching { Api.post("/api/chat/read", JSONObject().put("room_id", r.id)) }
                        Log.i(ChatNotify.TAG, "mark read room=${r.id} site=${ok.isSuccess}" + (ok.exceptionOrNull()?.let { " (${it.message})" } ?: ""))
                        ChatNotify.markRead(c, r.id)
                    }
                    ChatNotify.ACTION_DISMISS -> ChatNotify.dismissed(c, r.id)
                }
            } catch (e: Throwable) { Log.w(ChatNotify.TAG, "notification action ${intent.action} failed: ${e.message}") }
            finally { pending.finish() }
        }
    }
}

/**
 * QA hook (adb shell holds DUMP; no app does). mode: "demo" (default) a sample group notification; "reset" forget all
 * notification records; "poll" run the real chat check now (extra "rooms" = the room-list path, e.g. a fixture key);
 * "forget" drop the worker's per-room list state (keeps the seen record, so the seen rule alone must stop re-alerts);
 * "failnext" (fixture account only) makes the next notification reply fail, to show "Not sent" + Retry.
 */
class ChatQaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.yidplus.app.QA_CHAT_NOTIFY") return
        val c = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Api.init(c); com.yidplus.app.data.Prefs.init(c)
                when (intent.getStringExtra("mode") ?: "demo") {
                    "reset" -> { ChatNotify.qaReset(c); NotifyWorker.check(c, chatsOnly = true) }
                    "forget" -> c.getSharedPreferences("notify-state", Context.MODE_PRIVATE).edit().clear().putBoolean("baseline", true).commit()
                    "poll" -> NotifyWorker.check(c, roomsPath = intent.getStringExtra("rooms") ?: "/api/chat/rooms", chatsOnly = true)
                    "failnext" -> c.getSharedPreferences("qa-nt", Context.MODE_PRIVATE).edit().putBoolean("failNext", true).commit()
                    else -> ChatNotify.demo(c)
                }
                Log.i(ChatNotify.TAG, "qa ${intent.getStringExtra("mode") ?: "demo"} done")
            } catch (e: Throwable) { Log.w(ChatNotify.TAG, "qa failed: ${e.javaClass.simpleName} ${e.message}") }
            finally { pending.finish() }
        }
    }
}
