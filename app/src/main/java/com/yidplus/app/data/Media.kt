package com.yidplus.app.data

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Video plumbing: one disk cache for all videos, and a pre-loader that fetches the start of the next Clip. */
@OptIn(UnstableApi::class)
object Media {
    private var cache: SimpleCache? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefetchJob: Job? = null

    @Synchronized fun cache(ctx: Context): SimpleCache = cache ?: SimpleCache(File(ctx.cacheDir, "video"), LeastRecentlyUsedCacheEvictor(150L * 1024 * 1024),
        StandaloneDatabaseProvider(ctx)).also { cache = it }

    fun dataSource(ctx: Context): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache(ctx))
        .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(Api.http))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** A light player for weak phones: small buffers so the first frame comes fast. */
    fun player(ctx: Context): ExoPlayer {
        val load = DefaultLoadControl.Builder().setBufferDurationsMs(2500, 15000, 700, 1500).setPrioritizeTimeOverSizeThresholds(true).build()
        return ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(dataSource(ctx))).setLoadControl(load).build()
    }

    /** Download the first ~700 KB of the next videos into the cache so they start at once. */
    fun prefetch(ctx: Context, urls: List<String>) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            for (u in urls) runCatching {
                val spec = DataSpec.Builder().setUri(Uri.parse(u)).setPosition(0).setLength(700L * 1024).build()
                CacheWriter(dataSource(ctx).createDataSource(), spec, null, null).cache()
            }
        }
    }
}
