package com.yidplus.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            // "updates_v2" pops up (heads-up); the old quiet channel from 1.0.0-1.0.2 is removed
            runCatching { nm.deleteNotificationChannel("updates") }
            nm.createNotificationChannel(
                NotificationChannel(Notifier.CHANNEL_UPDATES, getString(R.string.channel_updates), NotificationManager.IMPORTANCE_HIGH)
            )
        }
        UpdateWorker.schedule(this)
    }
}
