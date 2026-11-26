package com.yidplus.app

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Message reminders - client-only, no server changes, no new dependencies.
 *
 * Long-press a message -> "Remind me" -> pick a delay. [MsgReminder.schedule] stores the
 * reminder (message id, chat id, fire time, a short text preview) in SharedPreferences,
 * following the app's small-local-state pattern, and enqueues a one-time WorkManager job.
 * [MsgReminderWorker] fires at that delay, posts a notification on the chat channel
 * (same icon and colour as [NotifyWorker]'s chat notifications) whose tap opens the chat,
 * and leaves a one-shot jump target so opening the chat scrolls to that message.
 *
 * Timing is best-effort: WorkManager may run the job late (Doze, battery saver), so the
 * UI never promises an exact time.
 */
object MsgReminder {
    private const val PREFS = "msg-reminders"
    private const val KEY_JUMP = "jump:"

    fun schedule(
        ctx: Context,
        msgId: String,
        roomId: String,
        roomJson: String,
        roomName: String,
        nick: String,
        preview: String,
        fireAtMs: Long
    ) {
        if (msgId.isBlank() || roomId.isBlank()) return
        val json = JSONObject()
            .put("at", fireAtMs)
            .put("roomId", roomId)
            .put("room", roomJson)
            .put("roomName", roomName)
            .put("nick", nick)
            .put("preview", preview.take(200))
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(msgId, json.toString()).apply()
        val delaySec = maxOf(0L, (fireAtMs - System.currentTimeMillis()) / 1000)
        val req = OneTimeWorkRequestBuilder<MsgReminderWorker>()
            .setInputData(workDataOf("msgId" to msgId))
            .setInitialDelay(delaySec, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniqueWork("msg-reminder-$msgId", ExistingWorkPolicy.REPLACE, req)
    }

    /** Reads and removes a stored reminder; null when there is none (already fired). */
    internal fun take(ctx: Context, msgId: String): JSONObject? {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(msgId, null) ?: return null
        prefs.edit().remove(msgId).apply()
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    /** One-shot jump target left by a fired reminder; the chat consumes it when opened. */
    internal fun setJump(ctx: Context, roomId: String, msgId: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_JUMP + roomId, msgId).apply()
    }

    fun takeJump(ctx: Context, roomId: String): String? {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = KEY_JUMP + roomId
        val id = prefs.getString(key, null) ?: return null
        prefs.edit().remove(key).apply()
        return id
    }
}

/** Fires one reminder: posts the notification and leaves the jump target for the chat. */
class MsgReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val msgId = inputData.getString("msgId") ?: return Result.success()
        val r = MsgReminder.take(ctx, msgId) ?: return Result.success()
        val roomId = r.optString("roomId")
        val roomJson = r.optString("room")
        if (roomId.isBlank() || roomJson.isBlank()) return Result.success()
        MsgReminder.setJump(ctx, roomId, msgId)
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { post(ctx, msgId, roomJson, r) }
        }
        return Result.success()
    }

    private fun post(ctx: Context, msgId: String, roomJson: String, r: JSONObject) {
        NotifyWorker.channels(ctx)
        val roomName = r.optString("roomName").ifBlank { "Chat" }
        val nick = r.optString("nick")
        val preview = r.optString("preview")
        val text = if (nick.isBlank()) preview else "$nick: $preview"
        val body = text.ifBlank { "A message you asked to be reminded about." }
        val open = Intent(ctx, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_CHAT
            putExtra("room", roomJson)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, msgId.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, NotifyWorker.CH_CHATS)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(0xFF008069.toInt())
            .setContentTitle("\u23F0 Reminder \u00B7 $roomName")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(ctx).notify("msg-reminder-$msgId".hashCode(), n)
    }
}
