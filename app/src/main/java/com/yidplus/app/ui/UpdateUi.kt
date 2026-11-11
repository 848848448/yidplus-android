package com.yidplus.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yidplus.app.BuildConfig
import com.yidplus.app.Notifier
import com.yidplus.app.UpdateInfo
import com.yidplus.app.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** "Update available" → one tap → download (checked: complete + same signing key) → Android's "Update?" prompt. Same updater as v1. */
object UpdateUi {
    var offer by mutableStateOf<UpdateInfo?>(null)
    var askPermission by mutableStateOf<UpdateInfo?>(null)
    var progress by mutableStateOf<Int?>(null)
    var status by mutableStateOf<String?>(null)
    private var pending: UpdateInfo? = null
    private var awaitingPermission = false
    private val cancelled = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private const val EVERY = 6L * 60 * 60 * 1000

    fun check(ctx: Context, force: Boolean, userAsked: Boolean = false) {
        if (BuildConfig.VERSION_NAME.startsWith("0.0.") && !userAsked) return // CI test builds never offer updates
        val prefs = Updater.prefs(ctx)
        if (!force && System.currentTimeMillis() - prefs.getLong("lastUiCheck", 0L) < EVERY) return
        prefs.edit().putLong("lastUiCheck", System.currentTimeMillis()).apply()
        if (userAsked) status = "checking…"
        scope.launch {
            val info = withContext(Dispatchers.IO) { Updater.fetch() }
            when {
                info == null -> { if (userAsked) { status = "couldn't check"; toast(ctx, "Couldn't check for updates right now") } }
                !Updater.isNewer(ctx, info) -> { status = "up to date"; if (userAsked) toast(ctx, "YID PLUS is up to date (${BuildConfig.VERSION_NAME})") }
                else -> {
                    status = "${info.versionName} available"
                    val snoozed = prefs.getLong("laterCode", -1L) == info.versionCode && System.currentTimeMillis() - prefs.getLong("laterAt", 0L) < 24L * 3600 * 1000
                    if (!snoozed || force) { Notifier.cancel(ctx, 7000); offer = info }
                }
            }
        }
    }

    fun later(ctx: Context, info: UpdateInfo) {
        Updater.prefs(ctx).edit().putLong("laterCode", info.versionCode).putLong("laterAt", System.currentTimeMillis()).apply()
        offer = null
    }

    fun start(ctx: Context, info: UpdateInfo) {
        offer = null
        if (!Updater.canInstall(ctx)) { askPermission = info; return }
        cancelled.set(false); progress = 0
        scope.launch {
            try {
                val apk = withContext(Dispatchers.IO) { Updater.download(ctx, info, { cancelled.get() }) { p -> scope.launch { progress = p } } }
                progress = null
                if (!cancelled.get()) runCatching { Updater.install(ctx, apk) }.onFailure { toast(ctx, "The update couldn't be installed.") }
            } catch (e: Exception) {
                progress = null
                if (!cancelled.get()) toast(ctx, (e as? Updater.UpdateException)?.message ?: "The update couldn't download. Try again later.")
            }
        }
    }

    fun openPermission(ctx: Context, info: UpdateInfo) {
        askPermission = null; pending = info; awaitingPermission = true
        runCatching { ctx.startActivity(Updater.installPermissionIntent(ctx)) }
    }

    fun onResume(ctx: Context) {
        if (awaitingPermission) {
            awaitingPermission = false
            val info = pending; pending = null
            if (info != null && Updater.canInstall(ctx)) start(ctx, info)
        } else check(ctx, force = false)
    }

    fun cancel() { cancelled.set(true); progress = null }
}

@Composable
fun UpdateDialogs() {
    val c = ctx()
    UpdateUi.offer?.let { info ->
        val notes = info.notes.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .joinToString("\n") { if (it.startsWith("- ") || it.startsWith("* ")) "•  " + it.substring(2) else it }
        AlertDialog(onDismissRequest = { UpdateUi.offer = null },
            title = { Text("Update available", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    Text("You have ${BuildConfig.VERSION_NAME} · New: ${info.versionName}" + if (info.size > 0) " · %.1f MB".format(info.size / 1048576.0) else "",
                        style = MaterialTheme.typography.bodySmall)
                    if (notes.isNotBlank()) Text(notes, Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { PrimaryButton("Update", { UpdateUi.start(c, info) }, Modifier.firstFocus(always = true)) },
            dismissButton = { SecondaryButton("Later", { UpdateUi.later(c, info) }) })
    }
    UpdateUi.askPermission?.let { info ->
        AlertDialog(onDismissRequest = { UpdateUi.askPermission = null },
            title = { Text("Allow updates", fontWeight = FontWeight.Bold) },
            text = { Text("To install updates, Android needs you to allow YID PLUS to install apps. You only do this once: turn on \"Allow from this source\", then come back.") },
            confirmButton = { PrimaryButton("Open settings", { UpdateUi.openPermission(c, info) }, Modifier.firstFocus(always = true)) },
            dismissButton = { SecondaryButton("Later", { UpdateUi.askPermission = null }) })
    }
    UpdateUi.progress?.let { p ->
        AlertDialog(onDismissRequest = {},
            title = { Text("Downloading update…", fontWeight = FontWeight.Bold) },
            text = { Column { LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth()); Text("$p%", Modifier.padding(top = 6.dp)) } },
            confirmButton = {}, dismissButton = { SecondaryButton("Cancel", { UpdateUi.cancel() }, Modifier.firstFocus(always = true)) })
    }
}
