package com.yidplus.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.yidplus.app.data.Api
import com.yidplus.app.data.Net
import com.yidplus.app.data.Prefs
import com.yidplus.app.data.Session

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Api.init(this)
        Prefs.init(this)
        Session.init(this)
        Net.init(this)
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(Notifier.CHANNEL_UPDATES, getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT))
        }
        NotifyWorker.channels(this)
        UpdateWorker.schedule(this)
        if (Api.hasSession() && Prefs.notify) NotifyWorker.schedule(this)
    }

    /** Images: small memory cache (weak phones), 120 MB on disk so channels open instantly offline too. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { Api.http }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(120L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .crossfade(false)
        .build()
}
