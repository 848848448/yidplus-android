package com.yidplus.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
    val notes: String,
)

/**
 * Updates come from this repo's GitHub Releases. Each release carries the signed APK and a
 * small version.json; "latest/download/version.json" always points at the newest release.
 */
object Updater {
    private const val TAG = "YidUpdater"
    val FEED = "https://github.com/${BuildConfig.UPDATE_REPO}/releases/latest/download/version.json"
    private const val PREFS = "updates"

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun open(url: String): HttpURLConnection {
        var current = URL(url)
        repeat(6) {
            val c = current.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.useCaches = false
            c.setRequestProperty("User-Agent", "YidPlusApp/${BuildConfig.VERSION_NAME} (Android)")
            c.setRequestProperty("Cache-Control", "no-cache")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IllegalStateException("redirect without location")
                c.disconnect()
                current = URL(current, loc)
                if (current.protocol != "https") throw IllegalStateException("insecure redirect")
            } else return c
        }
        throw IllegalStateException("too many redirects")
    }

    /** Blocking. Null when offline or the feed can't be read. */
    fun fetch(): UpdateInfo? = runCatching {
        val c = open(FEED)
        try {
            check(c.responseCode == 200) { "feed answered ${c.responseCode}" }
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val apk = j.getString("apkUrl")
            require(apk.startsWith("https://github.com/${BuildConfig.UPDATE_REPO}/releases/download/"))
            UpdateInfo(
                versionCode = j.getLong("versionCode"),
                versionName = j.getString("versionName"),
                apkUrl = apk,
                sha256 = j.getString("sha256").lowercase(),
                size = j.optLong("size", 0L),
                notes = j.optString("notes", ""),
            )
        } finally { c.disconnect() }
    }.onFailure { Log.w(TAG, "feed", it) }.getOrNull()

    fun installedCode(ctx: Context): Long = runCatching {
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
    }.getOrDefault(BuildConfig.VERSION_CODE.toLong())

    fun isNewer(ctx: Context, info: UpdateInfo) = info.versionCode > installedCode(ctx)

    fun updatesDir(ctx: Context) = File(ctx.cacheDir, "updates").apply { mkdirs() }

    fun cleanup(ctx: Context) { updatesDir(ctx).listFiles()?.forEach { it.delete() } }

    class UpdateException(message: String) : Exception(message)

    /** Blocking download with progress 0..100, then integrity and signature checks. */
    fun download(ctx: Context, info: UpdateInfo, cancelled: () -> Boolean, onProgress: (Int) -> Unit): File {
        val dir = updatesDir(ctx)
        val out = File(dir, "yidplus-${info.versionCode}.apk")
        val part = File(dir, "yidplus-${info.versionCode}.part")
        if (out.exists() && sha256(out) == info.sha256) { onProgress(100); return verify(ctx, info, out) }
        dir.listFiles()?.forEach { it.delete() }
        val c = open(info.apkUrl)
        try {
            if (c.responseCode != 200) throw UpdateException("Download failed (${c.responseCode})")
            val total = if (c.contentLengthLong > 0) c.contentLengthLong else info.size
            val md = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var last = -1
            c.inputStream.use { ins ->
                part.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled()) throw UpdateException("Cancelled")
                        val n = ins.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n); md.update(buf, 0, n); done += n
                        if (total > 0) {
                            val p = ((done * 100) / total).toInt().coerceIn(0, 100)
                            if (p != last) { last = p; onProgress(p) }
                        }
                    }
                }
            }
            val hash = md.digest().joinToString("") { "%02x".format(it) }
            if (hash != info.sha256) { part.delete(); throw UpdateException("The download was damaged. Please try again.") }
            if (!part.renameTo(out)) throw UpdateException("Could not save the update")
        } finally { c.disconnect() }
        onProgress(100)
        return verify(ctx, info, out)
    }

    private fun verify(ctx: Context, info: UpdateInfo, apk: File): File {
        val pm = ctx.packageManager
        val archive = archiveInfo(pm, apk) ?: throw UpdateException("The update file is not a valid app")
        if (archive.packageName != ctx.packageName) throw UpdateException("The update is for a different app")
        if (PackageInfoCompat.getLongVersionCode(archive) <= installedCode(ctx)) throw UpdateException("You already have this version")
        val mine = certs(pm.getPackageInfo(ctx.packageName, certFlags()))
        val theirs = certs(archive)
        // Android refuses a differently signed update anyway; checking here gives a clear message.
        if (mine.isNotEmpty() && theirs.isNotEmpty() && mine.intersect(theirs).isEmpty()) {
            apk.delete(); throw UpdateException("The update is signed with a different key")
        }
        return apk
    }

    @Suppress("DEPRECATION")
    private fun certFlags(): Int = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? = runCatching {
        val pi = if (Build.VERSION.SDK_INT >= 33) pm.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(certFlags().toLong()))
        else pm.getPackageArchiveInfo(apk.path, certFlags())
        pi?.applicationInfo?.let { it.sourceDir = apk.path; it.publicSourceDir = apk.path }
        pi
    }.getOrNull()

    @Suppress("DEPRECATION")
    @SuppressLint("PackageManagerGetSignatures")
    private fun certs(pi: PackageInfo?): Set<String> {
        if (pi == null) return emptySet()
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            val si = pi.signingInfo ?: return emptySet()
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else pi.signatures
        return sigs?.map { sha256(it.toByteArray()) }?.toSet() ?: emptySet()
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins -> val buf = ByteArray(64 * 1024); while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))

    /** Hands the APK to Android's installer. Android shows its own "Update this app?" prompt. */
    fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        params.setSize(apk.length())
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { ins ->
                session.openWrite("base.apk", 0, apk.length()).use { out -> ins.copyTo(out); session.fsync(out) }
            }
            val intent = Intent(ctx, InstallResultReceiver::class.java).setPackage(ctx.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(ctx, id, intent, flags)
            session.commit(pending.intentSender)
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirm != null) runCatching { ctx.startActivity(confirm) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
                Log.w("YidUpdater", "install failed: $msg")
                Toast.makeText(ctx, ctx.getString(R.string.update_install_failed), Toast.LENGTH_LONG).show()
            }
        }
    }
}

/** After an update the old process is gone; a notification brings the person back in one tap. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Updater.cleanup(ctx)
        Updater.prefs(ctx).edit().putLong("lastCheck", 0L).apply()
        Notifier.show(ctx, 7001, ctx.getString(R.string.updated_title), ctx.getString(R.string.updated_text, BuildConfig.VERSION_NAME), Intent(ctx, MainActivity::class.java))
    }
}

object Notifier {
    const val CHANNEL_UPDATES = "app-updates"

    fun show(ctx: Context, id: Int, title: String, text: String, open: Intent, big: String? = null) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val pi = PendingIntent.getActivity(ctx, id, open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(0xFFD4AF37.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .apply { if (big != null) setStyle(NotificationCompat.BigTextStyle().bigText(big)) }
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    fun cancel(ctx: Context, id: Int) = runCatching { (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id) }
}

/** Daily background check. Posts "Update available" when a newer release exists. */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val info = Updater.fetch() ?: return@withContext Result.success()
        Updater.prefs(applicationContext).edit().putLong("lastCheck", System.currentTimeMillis()).apply()
        if (Updater.isNewer(applicationContext, info) && !BuildConfig.VERSION_NAME.startsWith("0.0.")) {
            // "YID PLUS update v2.0.1" + what's new (the release notes); tap = download and install.
            val notes = info.notes.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { if (it.startsWith("- ") || it.startsWith("* ")) it.substring(2) else it }
            Notifier.show(
                applicationContext, 7000,
                "YID PLUS update v${info.versionName}",
                notes.firstOrNull() ?: applicationContext.getString(R.string.update_tap, info.versionName),
                Intent(applicationContext, MainActivity::class.java).setAction(MainActivity.ACTION_INSTALL_UPDATE),
                big = (notes.joinToString("\n") { "• $it" } + "\n\nTap to update.").trim(),
            )
        }
        Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(8, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            runCatching { WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("update-check-8h", ExistingPeriodicWorkPolicy.KEEP, req) }
            runCatching { WorkManager.getInstance(ctx).cancelUniqueWork("update-check") }
        }

        /** One check right now (QA, and the in-app "Check for updates" while the app goes to the background). */
        fun now(ctx: Context) {
            runCatching { WorkManager.getInstance(ctx).enqueueUniqueWork("update-now", androidx.work.ExistingWorkPolicy.REPLACE,
                androidx.work.OneTimeWorkRequestBuilder<UpdateWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()) }
        }
    }
}
