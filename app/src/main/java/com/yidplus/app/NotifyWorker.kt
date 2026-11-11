package com.yidplus.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yidplus.app.data.Api
import com.yidplus.app.data.Notif
import com.yidplus.app.data.Prefs
import com.yidplus.app.data.Room
import com.yidplus.app.data.TgChannel
import com.yidplus.app.data.objects
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Status-bar notifications without Google Play services (the Qin F21 has none): every 15 minutes Android lets
 * this worker ask yidplus.com — with the same read-only calls the app uses — for unread chats, the site's own
 * notifications (likes, comments, follows, replies) and new posts in joined channels. Nothing is written to the site.
 */
class NotifyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        Api.init(ctx); Prefs.init(ctx)
        if (!Prefs.notify || !Api.hasSession()) return Result.success()
        return try { check(ctx); Result.success() } catch (e: Api.ApiError) {
            if (e.code == 401) Result.success() else Result.retry()
        } catch (e: Exception) { Result.retry() }
    }

    companion object {
        const val CH_CHATS = "chats"; const val CH_ACTIVITY = "activity"; const val CH_CHANNELS = "channels"
        private const val GROUP_CHATS = "com.yidplus.app.CHATS"
        private const val GROUP_ACTIVITY = "com.yidplus.app.ACTIVITY"

        fun channels(ctx: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_CHATS, "Chat messages", NotificationManager.IMPORTANCE_HIGH).apply { setShowBadge(true) })
            nm.createNotificationChannel(NotificationChannel(CH_ACTIVITY, "Likes, comments, follows", NotificationManager.IMPORTANCE_DEFAULT).apply { setShowBadge(true) })
            nm.createNotificationChannel(NotificationChannel(CH_CHANNELS, "New channel posts", NotificationManager.IMPORTANCE_DEFAULT).apply { setShowBadge(true) })
        }

        fun schedule(ctx: Context) {
            val c = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("notify", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<NotifyWorker>(15, TimeUnit.MINUTES).setConstraints(c).setInitialDelay(2, TimeUnit.MINUTES).build())
        }

        /** Run one check now (QA, and right after the app goes to the background). */
        fun now(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork("notify-now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<NotifyWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }

        fun cancelChat(ctx: Context, roomId: String) = runCatching { NotificationManagerCompat.from(ctx).cancel("chat", roomId.hashCode()) }

        suspend fun check(ctx: Context) {
            val st = ctx.getSharedPreferences("notify-state", Context.MODE_PRIVATE)
            val first = !st.getBoolean("baseline", false)
            val nm = NotificationManagerCompat.from(ctx)
            val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val e = st.edit()
            var shown = 0

            // 1. Chats: one notification per chat with unread messages (grouped, like SMS).
            val rooms = Room.list(Api.get("/api/chat/rooms"))
            for (r in rooms) {
                val k = "room:" + r.id
                val last = r.lastTime ?: continue
                if (r.unread <= 0) { if (st.contains(k)) { e.remove(k); nm.cancel("chat", r.id.hashCode()) }; continue }
                if (st.getString(k, null) == last) continue
                e.putString(k, last)
                if (first || !allowed) continue
                val open = Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_CHAT).putExtra("room", r.json().toString())
                val n = NotificationCompat.Builder(ctx, CH_CHATS).setSmallIcon(R.drawable.ic_stat_logo).setColor(0xFF1F6F5C.toInt())
                    .setContentTitle(r.name).setContentText(r.preview.ifBlank { "New message" })
                    .setStyle(NotificationCompat.BigTextStyle().bigText(r.preview.ifBlank { "New message" }))
                    .setNumber(r.unread).setSubText(if (r.unread > 1) "${r.unread} new" else null)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE).setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setGroup(GROUP_CHATS).setAutoCancel(true).setOnlyAlertOnce(false)
                    .setContentIntent(pi(ctx, r.id.hashCode(), open)).build()
                runCatching { nm.notify("chat", r.id.hashCode(), n) }; shown++
            }
            val unreadChats = rooms.count { it.unread > 0 }
            if (!first && allowed && unreadChats > 1) runCatching {
                nm.notify("chat-summary", 1, NotificationCompat.Builder(ctx, CH_CHATS).setSmallIcon(R.drawable.ic_stat_logo).setColor(0xFF1F6F5C.toInt())
                    .setContentTitle("YID PLUS").setContentText("$unreadChats chats with new messages").setGroup(GROUP_CHATS).setGroupSummary(true).setAutoCancel(true)
                    .setContentIntent(pi(ctx, 2, Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_CHATS))).build())
            }

            // 2. The site's own notifications: likes, comments, replies, follows.
            runCatching {
                val list = Api.get("/api/notifications").optJSONArray("notifications").objects().map(Notif::from).filter { !it.read }
                val seen = st.getStringSet("notifs", emptySet()) ?: emptySet()
                val fresh = list.filter { it.id !in seen }
                e.putStringSet("notifs", (seen + list.map { it.id }).toList().takeLast(300).toSet())
                if (!first && allowed) for (n in fresh.take(5)) {
                    val open = Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_NOTIFS).putExtra("link", n.link)
                    nm.notify("activity", n.id.hashCode(), NotificationCompat.Builder(ctx, CH_ACTIVITY).setSmallIcon(R.drawable.ic_stat_logo).setColor(0xFF1F6F5C.toInt())
                        .setContentTitle(n.actor.ifBlank { "YID PLUS" }).setContentText(n.text.ifBlank { n.type }).setGroup(GROUP_ACTIVITY).setAutoCancel(true)
                        .setContentIntent(pi(ctx, n.id.hashCode(), open)).build()); shown++
                }
            }

            // 3. New posts in channels the person joined.
            runCatching {
                for (ch in TgChannel.list(Api.get("/api/telegram-channels")).filter { it.joined && it.unread > 0 }) {
                    val k = "tg:" + ch.username
                    if (st.getString(k, null) == ch.lastAt) continue
                    e.putString(k, ch.lastAt)
                    if (first || !allowed) continue
                    val open = Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_CHANNEL).putExtra("username", ch.username).putExtra("title", ch.title)
                    nm.notify("tg", ch.username.hashCode(), NotificationCompat.Builder(ctx, CH_CHANNELS).setSmallIcon(R.drawable.ic_stat_logo).setColor(0xFF1F6F5C.toInt())
                        .setContentTitle(ch.title).setContentText(ch.lastText.ifBlank { "New post" }).setNumber(ch.unread).setAutoCancel(true)
                        .setContentIntent(pi(ctx, ch.username.hashCode(), open)).build()); shown++
                }
            }
            e.putBoolean("baseline", true).putLong("lastRun", System.currentTimeMillis()).putInt("lastShown", shown).apply()
        }

        private fun pi(ctx: Context, code: Int, i: Intent) =
            PendingIntent.getActivity(ctx, code, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
