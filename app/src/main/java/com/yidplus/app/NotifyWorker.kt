package com.yidplus.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yidplus.app.data.Api
import com.yidplus.app.data.Notif
import com.yidplus.app.data.Prefs
import com.yidplus.app.data.Room
import com.yidplus.app.data.TgChannel
import com.yidplus.app.data.objects
import java.util.concurrent.TimeUnit

/**
 * Status-bar notifications without Google Play services (the Qin F21 has none). Android lets a periodic background
 * job run at most every 15 minutes, so on top of that this worker runs a SHORT FAST POLL after the app was in use:
 * once the app leaves the screen it checks the chats every minute for 10 minutes, then every 3 minutes until 30
 * minutes have passed (and again for 15 minutes after a reply from a notification) — one small read of the chat list
 * each time, nothing when nothing changed, so the battery cost stays small. On phones with Google services the site's
 * Firebase message (data only) triggers the same check instantly (Push.onPush).
 * The check uses the same read-only calls the app uses: unread chats, the site's own notifications (likes, comments,
 * follows, replies) and new posts in joined channels. Nothing is written to the site.
 */
class NotifyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        Api.init(ctx); Prefs.init(ctx)
        if (!Prefs.notify || !Api.hasSession()) return Result.success()
        val fast = inputData.getBoolean("fast", false)
        return try {
            if (fast) { if (!foreground) check(ctx, chatsOnly = true); nextFast(ctx) } else check(ctx)
            Result.success()
        } catch (e: Api.ApiError) {
            Log.w(TAG, "check failed: site answered ${e.code} ${e.message}")
            if (fast) { nextFast(ctx); Result.success() } else if (e.code == 401) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.w(TAG, "check failed: ${e.javaClass.simpleName} ${e.message}")
            if (fast) { nextFast(ctx); Result.success() } else Result.retry()
        }
    }

    companion object {
        const val TAG = "YidNotify"
        const val CH_CHATS = "chats"; const val CH_ACTIVITY = "activity"; const val CH_CHANNELS = "channels"
        private const val GROUP_ACTIVITY = "com.yidplus.app.ACTIVITY"
        @Volatile var foreground = false; private set
        @Volatile private var watching = false

        fun channels(ctx: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(NotificationChannel(CH_CHATS, "Chat messages", NotificationManager.IMPORTANCE_HIGH).apply { setShowBadge(true) })
                nm.createNotificationChannel(NotificationChannel(CH_ACTIVITY, "Likes, comments, follows", NotificationManager.IMPORTANCE_DEFAULT).apply { setShowBadge(true) })
                nm.createNotificationChannel(NotificationChannel(CH_CHANNELS, "New channel posts", NotificationManager.IMPORTANCE_DEFAULT).apply { setShowBadge(true) })
            }
            watchApp(ctx)
        }

        /** Knows when the app is on screen: the fast poll runs only after it leaves (the app's own screens refresh themselves). */
        private fun watchApp(ctx: Context) {
            if (watching) return
            watching = true
            val app = ctx.applicationContext
            val add = Runnable {
                runCatching {
                    ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                        override fun onStart(owner: LifecycleOwner) { foreground = true; runCatching { WorkManager.getInstance(app).cancelUniqueWork("notify-fast") } }
                        override fun onStop(owner: LifecycleOwner) {
                            foreground = false
                            if (Prefs.notify && Api.hasSession()) burst(app, 30)
                        }
                    })
                }.onFailure { Log.w(TAG, "app watch failed: ${it.message}") }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) add.run() else Handler(Looper.getMainLooper()).post(add)
        }

        fun schedule(ctx: Context) {
            val c = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("notify", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<NotifyWorker>(15, TimeUnit.MINUTES).setConstraints(c).setInitialDelay(2, TimeUnit.MINUTES).build())
        }

        /** Run one check now (QA, and after a push that could not finish in time). */
        fun now(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork("notify-now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<NotifyWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }

        private fun fastPrefs(ctx: Context) = ctx.getSharedPreferences("notify-fast", Context.MODE_PRIVATE)

        /** Check the chats every minute (later every 3 minutes) for the next [minutes] minutes. */
        fun burst(ctx: Context, minutes: Int) {
            runCatching {
                val now = System.currentTimeMillis(); val p = fastPrefs(ctx)
                val until = p.getLong("until", 0)
                val e = p.edit().putLong("until", maxOf(until, now + minutes * 60_000L))
                if (until < now) e.putLong("start", now)
                e.apply()
                WorkManager.getInstance(ctx).enqueueUniqueWork("notify-fast", ExistingWorkPolicy.KEEP, fastRequest(45))
                Log.i(TAG, "fast check on for $minutes min")
            }.onFailure { Log.w(TAG, "fast check not started: ${it.message}") }
        }

        private fun fastRequest(delaySec: Long) = OneTimeWorkRequestBuilder<NotifyWorker>().setInputData(workDataOf("fast" to true))
            .setInitialDelay(delaySec, TimeUnit.SECONDS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()

        private fun nextFast(ctx: Context) {
            val now = System.currentTimeMillis(); val p = fastPrefs(ctx)
            if (foreground || now >= p.getLong("until", 0)) return
            val delay = if (now - p.getLong("start", now) < 10 * 60_000L) 60L else 180L
            WorkManager.getInstance(ctx).enqueueUniqueWork("notify-fast", ExistingWorkPolicy.APPEND_OR_REPLACE, fastRequest(delay))
        }

        fun cancelChat(ctx: Context, roomId: String) = runCatching { NotificationManagerCompat.from(ctx).cancel("chat", roomId.hashCode()); ChatNotify.updateSummary(ctx) }

        suspend fun check(ctx: Context, roomsPath: String = "/api/chat/rooms", chatsOnly: Boolean = false) {
            val st = ctx.getSharedPreferences("notify-state", Context.MODE_PRIVATE)
            val first = !st.getBoolean("baseline", false)
            val nm = NotificationManagerCompat.from(ctx)
            val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val e = st.edit()
            var shown = 0

            // 1. Chats: one WhatsApp-style notification per chat with NEW unread messages (ChatNotify). Never a plain fallback.
            val rooms = Room.list(Api.get(roomsPath))
            for (r in rooms) {
                try {
                    val k = "room:" + r.id
                    val last = r.lastTime ?: continue
                    if (r.unread <= 0) { if (st.contains(k)) { e.remove(k); ChatNotify.clear(ctx, r.id) }; continue }
                    if (st.getString(k, null) == last) continue
                    e.putString(k, last)
                    if (first || !allowed) { ChatNotify.markSeenUpTo(ctx, r.id, last); continue }
                    // Read, replied or swiped away up to this message already (also when the site still counts it unread).
                    if (ChatNotify.alreadySeen(ctx, r.id, last)) { Log.i(TAG, "room ${r.id}: already seen up to $last"); continue }
                    val n = ChatNotify.build(ctx, r) ?: continue
                    nm.notify("chat", r.id.hashCode(), n); shown++
                    Log.i(TAG, "room ${r.id}: notification posted (${r.type}, photo=${r.photo != null})")
                } catch (x: Exception) { Log.w(TAG, "room ${r.id}: notification failed: ${x.javaClass.simpleName} ${x.message}") }
            }
            if (!first && allowed) ChatNotify.updateSummary(ctx)

            if (!chatsOnly) {
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
                }.onFailure { Log.w(TAG, "site notifications: ${it.message}") }

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
                }.onFailure { Log.w(TAG, "channel posts: ${it.message}") }
            }
            e.putBoolean("baseline", true).putLong("lastRun", System.currentTimeMillis()).putInt("lastShown", shown).apply()
            Log.i(TAG, "check done: ${rooms.size} rooms, $shown shown" + if (chatsOnly) " (chats only)" else "")
        }

        private fun pi(ctx: Context, code: Int, i: Intent) =
            PendingIntent.getActivity(ctx, code, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
