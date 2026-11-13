package com.yidplus.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A screen's data: shown from the disk cache at once, then refreshed from yidplus.com. */
class Res<T>(val path: String, private val parse: (JSONObject) -> T) {
    var data by mutableStateOf<T?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var offline by mutableStateOf(false)
    /** True while the data on screen came from the disk cache and the network has not refreshed it this session. */
    var fromCache by mutableStateOf(false)
    /** Wall-clock time of the data on screen (cache write time or last successful load; 0 = unknown). */
    var dataAt by mutableStateOf(0L)
    private var loadedAt = 0L
    private var diskTried = false

    suspend fun load(force: Boolean = false) {
        if (data == null && !diskTried) {
            diskTried = true
            val (cached, cachedAt) = withContext(Dispatchers.IO) { Disk.read(Store.key(path))?.let { runCatching { parse(it) }.getOrNull() } to Disk.modified(Store.key(path)) }
            if (cached != null && data == null) { data = cached; fromCache = true; dataAt = cachedAt }
        }
        if (loading) return
        if (!force && loadedAt > 0 && SystemClock.elapsedRealtime() - loadedAt < 45_000) return
        loading = true
        try {
            val (parsed, raw) = withContext(Dispatchers.IO) { val j = Api.get(path); parse(j) to j }
            data = parsed; error = null; offline = false; fromCache = false; dataAt = System.currentTimeMillis()
            loadedAt = SystemClock.elapsedRealtime()
            withContext(Dispatchers.IO) { Disk.write(Store.key(path), raw) }
        } catch (e: Exception) {
            error = Api.friendly(e); offline = Api.isOffline(e)
        } finally { loading = false }
    }
}

object Store {
    private val map = HashMap<String, Res<*>>()
    fun key(path: String) = (Session.me?.id ?: "guest") + "|" + path
    @Suppress("UNCHECKED_CAST")
    fun <T> res(path: String, parse: (JSONObject) -> T): Res<T> = synchronized(map) { map.getOrPut(path) { Res(path, parse) } as Res<T> }
    fun clear() { synchronized(map) { map.clear() } }
}

/** Is the phone online? Screens refresh by themselves when it comes back. */
object Net {
    var online by mutableStateOf(true)
    fun init(ctx: Context) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        online = runCatching { cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true }.getOrDefault(true)
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { android.os.Handler(android.os.Looper.getMainLooper()).post { online = true } }
                override fun onLost(network: Network) { android.os.Handler(android.os.Looper.getMainLooper()).post { online = false } }
            })
        }
    }
}

/** Timing marks for the QA report (adb logcat -s YidPerf). */
object Perf {
    private val marks = HashMap<String, Long>()
    private val done = HashSet<String>()
    fun sinceStart(): Long = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
    fun start(name: String) { marks[name] = SystemClock.elapsedRealtime(); done.remove(name) }
    fun shown(name: String, fromCache: Boolean = false) {
        if (!done.add(name)) return
        val t0 = marks[name]
        if (t0 == null) Log.i("YidPerf", "$name content ${sinceStart()} ms after process start" + if (fromCache) " (cache)" else "")
        else Log.i("YidPerf", "$name content ${SystemClock.elapsedRealtime() - t0} ms after open" + if (fromCache) " (cache)" else "")
    }
}
