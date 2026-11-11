package com.yidplus.app

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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.yidplus.app.data.Api
import com.yidplus.app.data.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Chat notifications like WhatsApp: one notification per chat (MessagingStyle), the sender's name and round
 * profile photo (coloured initials when there is none), the group's name as the title, the last messages stacked,
 * an inline Reply box that sends through the site's own /api/chat without opening the app, and Mark as read.
 * Built for both paths: the Firebase push and the 15-minute check both end in NotifyWorker.check → ChatNotify.build.
 */
object ChatNotify {
    const val KEY_REPLY = "chat_reply"
    const val ACTION_REPLY = "com.yidplus.app.CHAT_REPLY"
    const val ACTION_READ = "com.yidplus.app.CHAT_READ"
    private const val GROUP = "com.yidplus.app.CHATS"
    private val palette = intArrayOf(0xFF1F7AE0.toInt(), 0xFFD9534F.toInt(), 0xFF8E44AD.toInt(), 0xFFE67E22.toInt(), 0xFF16A085.toInt(), 0xFFC2185B.toInt(), 0xFF2E7D32.toInt(), 0xFF6D4C41.toInt())

    private fun store(c: Context) = c.getSharedPreferences("chat-notif", Context.MODE_PRIVATE)
    private fun me(c: Context): JSONObject? = runCatching { c.getSharedPreferences("session", Context.MODE_PRIVATE).getString("me", null)?.let { JSONObject(it) } }.getOrNull()

    fun clear(c: Context, roomId: String) { runCatching { store(c).edit().remove(roomId).apply() } }

    private fun textOf(m: JSONObject): String {
        val t = m.optString("text", "")
        return when (m.optString("type")) {
            "voice" -> "🎤 Voice message"; "sticker" -> "Sticker"; "poll" -> "📊 Poll"
            "file" -> "📎 " + t.ifBlank { "File" }
            "media" -> { val k = m.optString("media_key").lowercase(); (if (Regex("""\.(mp4|mov|webm|3gp|mkv|m4v)$""").containsMatchIn(k)) "🎬 Video" else "📷 Photo") + if (t.isNotBlank() && t != "__once__") " $t" else "" }
            else -> t.removeSuffix(" ↩ Forwarded")
        }.ifBlank { "New message" }
    }

    private fun time(s: String?): Long = runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(s!!.take(19))!!.time
    }.getOrDefault(System.currentTimeMillis())

    /** Round avatar: the photo, else a coloured circle with initials. */
    fun avatar(name: String, key: String, url: String?): Bitmap {
        val size = 128
        if (!url.isNullOrBlank()) runCatching {
            val http = Api.http.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()
            http.newCall(Request.Builder().url(Api.abs(url)!!).build()).execute().use { r ->
                if (r.isSuccessful) BitmapFactory.decodeStream(r.body!!.byteStream())?.let { return round(it, size) }
            }
        }
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); val cv = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette[(key.ifBlank { name }.hashCode() and 0x7fffffff) % palette.size] }
        cv.drawCircle(size / 2f, size / 2f, size / 2f, p)
        val ini = name.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifBlank { "?" }
        p.color = 0xFFFFFFFF.toInt(); p.textSize = size * 0.4f; p.textAlign = Paint.Align.CENTER; p.isFakeBoldText = true
        val bounds = Rect(); p.getTextBounds(ini, 0, ini.length, bounds)
        cv.drawText(ini, size / 2f, size / 2f + bounds.height() / 2f, p)
        return b
    }

    private fun round(src: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); val cv = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        cv.drawOval(RectF(0f, 0f, size.toFloat(), size.toFloat()), p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val s = minOf(src.width, src.height)
        cv.drawBitmap(src, Rect((src.width - s) / 2, (src.height - s) / 2, (src.width + s) / 2, (src.height + s) / 2), Rect(0, 0, size, size), p)
        return out
    }

    /** Fetches the newest unread messages of this chat and builds its notification (null = could not load; the caller falls back). */
    suspend fun build(c: Context, r: Room): android.app.Notification? {
        val my = me(c); val myId = my?.optString("id").orEmpty()
        val j = runCatching { Api.get("/api/chat?room_id=" + URLEncoder.encode(r.id, "UTF-8")) }.getOrNull() ?: return null
        val a = j.optJSONArray("messages") ?: return null
        val list = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { it.optString("sender_id") != myId && it.optString("type") !in setOf("system", "call") }
            .takeLast(r.unread.coerceIn(1, 7))
        if (list.isEmpty()) return null
        val msgs = JSONArray()
        list.forEach { m -> msgs.put(JSONObject().put("nick", m.optString("sender_nick")).put("sid", m.optString("sender_id")).put("photo", m.optString("sender_photo"))
            .put("text", textOf(m)).put("t", time(m.optString("created_at")))) }
        store(c).edit().putString(r.id, JSONObject().put("room", r.json()).put("msgs", msgs).toString()).apply()
        return compose(c, r, msgs, myId, my?.optString("nickname").orEmpty())
    }

    private fun compose(c: Context, r: Room, msgs: JSONArray, myId: String, myName: String, alertOnce: Boolean = false): android.app.Notification {
        val icons = HashMap<String, Bitmap>()
        fun icon(name: String, key: String, url: String?) = icons.getOrPut(key.ifBlank { name }) { avatar(name, key, url?.takeIf { it.isNotBlank() && it != "null" }) }
        val mePerson = Person.Builder().setName(myName.ifBlank { "You" }).setKey(myId.ifBlank { "me" }).build()
        val group = r.type != "private"
        val style = NotificationCompat.MessagingStyle(mePerson).setGroupConversation(group)
        if (group) style.setConversationTitle(r.name)
        var lastSender: Bitmap? = null
        for (i in 0 until msgs.length()) {
            val m = msgs.getJSONObject(i)
            val mine = m.optBoolean("mine")
            val p = if (mine) null else Person.Builder().setName(m.optString("nick").ifBlank { r.name }).setKey(m.optString("sid"))
                .setIcon(IconCompat.createWithBitmap(icon(m.optString("nick"), m.optString("sid"), m.optString("photo")).also { lastSender = it })).build()
            style.addMessage(NotificationCompat.MessagingStyle.Message(m.optString("text"), m.optLong("t", System.currentTimeMillis()), p))
        }
        val code = r.id.hashCode()
        val open = Intent(c, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_CHAT).putExtra("room", r.json().toString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val replyPi = PendingIntent.getBroadcast(c, code, Intent(c, ChatReplyReceiver::class.java).setAction(ACTION_REPLY).putExtra("room", r.json().toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or mutable)
        val readPi = PendingIntent.getBroadcast(c, code + 1, Intent(c, ChatReplyReceiver::class.java).setAction(ACTION_READ).putExtra("room", r.json().toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val reply = NotificationCompat.Action.Builder(R.drawable.ic_stat_logo, "Reply", replyPi)
            .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel(if (group) "Reply to ${r.name}" else "Reply").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).setShowsUserInterface(false).setAllowGeneratedReplies(true).build()
        val read = NotificationCompat.Action.Builder(R.drawable.ic_stat_logo, "Mark as read", readPi)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).setShowsUserInterface(false).build()
        val large = if (group) icon(r.name, r.id, r.photo) else (lastSender ?: icon(r.name, r.otherUserId ?: r.id, r.photo))
        val n = msgs.length()
        return NotificationCompat.Builder(c, NotifyWorker.CH_CHATS).setSmallIcon(R.drawable.ic_stat_logo).setColor(0xFF1F6F5C.toInt())
            .setStyle(style).setLargeIcon(large)
            .setContentTitle(r.name).setContentText(if (n > 0) msgs.getJSONObject(n - 1).optString("text") else "New message")
            .setNumber(r.unread).setCategory(NotificationCompat.CATEGORY_MESSAGE).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP).setAutoCancel(true).setOnlyAlertOnce(alertOnce)
            .addAction(reply).addAction(read)
            .setContentIntent(PendingIntent.getActivity(c, code, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
    }

    /** After an inline reply: my message joins the stack (or "not sent"), quietly. */
    fun addMine(c: Context, r: Room, text: String, ok: Boolean) {
        val my = me(c)
        val saved = runCatching { JSONObject(store(c).getString(r.id, null)!!) }.getOrNull()
        val msgs = saved?.optJSONArray("msgs") ?: JSONArray()
        msgs.put(JSONObject().put("mine", true).put("text", if (ok) text else "⚠ Not sent: $text").put("t", System.currentTimeMillis()))
        store(c).edit().putString(r.id, JSONObject().put("room", r.json()).put("msgs", msgs).toString()).apply()
        runCatching { NotificationManagerCompat.from(c).notify("chat", r.id.hashCode(), compose(c, r, msgs, my?.optString("id").orEmpty(), my?.optString("nickname").orEmpty(), alertOnce = true)) }
    }

    /** QA only (adb with the DUMP permission): a sample group chat notification, to photograph the expanded shade with its reply box. */
    fun demo(c: Context) {
        NotifyWorker.channels(c)
        val r = Room("qa-demo-room", "group", "YID PLUS Chat", null, "", "", 3, null, false, false, 12, null)
        val now = System.currentTimeMillis()
        val msgs = JSONArray()
            .put(JSONObject().put("nick", "Moshe Klein").put("sid", "u1").put("text", "גוט מארגן! ווער קומט היינט צום שיעור?").put("t", now - 120_000))
            .put(JSONObject().put("nick", "Yossi Weiss").put("sid", "u2").put("text", "📷 Photo").put("t", now - 60_000))
            .put(JSONObject().put("nick", "Moshe Klein").put("sid", "u1").put("text", "I'll be there at 8").put("t", now - 5_000))
        store(c).edit().putString(r.id, JSONObject().put("room", r.json()).put("msgs", msgs).toString()).apply()
        runCatching { NotificationManagerCompat.from(c).notify("chat", r.id.hashCode(), compose(c, r, msgs, "me", "You")) }
    }
}

/** Reply and Mark as read straight from the notification, without opening the app. */
class ChatReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.applicationContext
        val r = runCatching { Room.from(JSONObject(intent.getStringExtra("room")!!)) }.getOrNull() ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Api.init(c)
                when (intent.action) {
                    ChatNotify.ACTION_REPLY -> {
                        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(ChatNotify.KEY_REPLY)?.toString()?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            val ok = runCatching { Api.post("/api/chat", JSONObject().put("room_id", r.id).put("type", "text").put("text", text)) }.isSuccess
                            if (ok) runCatching { Api.post("/api/chat/read", JSONObject().put("room_id", r.id)) }
                            ChatNotify.addMine(c, r, text, ok)
                        }
                    }
                    ChatNotify.ACTION_READ -> {
                        runCatching { Api.post("/api/chat/read", JSONObject().put("room_id", r.id)) }
                        NotificationManagerCompat.from(c).cancel("chat", r.id.hashCode()); ChatNotify.clear(c, r.id)
                    }
                }
            } finally { pending.finish() }
        }
    }
}

/** QA hook (adb shell holds DUMP; no app does): posts the sample chat notification. */
class ChatQaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { if (intent.action == "com.yidplus.app.QA_CHAT_NOTIFY") ChatNotify.demo(context.applicationContext) }
}
