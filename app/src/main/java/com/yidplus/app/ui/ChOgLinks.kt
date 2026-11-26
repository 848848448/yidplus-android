package com.yidplus.app.ui

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Client-side OpenGraph fallback for chat link previews.
 *
 * Used only when the server preview ([ChLinks]) returns nothing for a URL:
 * the page itself is fetched, its og:title / og:description / og:image /
 * og:site_name are read out of the <head> with plain string parsing, and the
 * result is rendered as an ordinary [ChLink] by ChLinkCard. No new
 * dependencies; runs on Dispatchers.IO and never throws to callers.
 * Results (including "no preview") are cached in memory so a chat list
 * does not refetch the same URL.
 */
object ChOgLinks {
    private const val MAX_BYTES = 64 * 1024
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000

    private val cache = HashMap<String, ChLink?>()

    fun cached(url: String): ChLink? = synchronized(cache) { cache[url] }
    fun known(url: String): Boolean = synchronized(cache) { cache.containsKey(url) }

    /** Fetches and parses the OpenGraph preview for [url], or null. Cached. */
    suspend fun load(url: String): ChLink? {
        if (known(url)) return cached(url)
        val link = runCatching { withContext(Dispatchers.IO) { fetch(url) } }.getOrNull()
        synchronized(cache) { cache[url] = link }
        return link
    }

    private fun fetch(pageUrl: String): ChLink? {
        if (!pageUrl.startsWith("http://") && !pageUrl.startsWith("https://")) return null
        val host = runCatching { Uri.parse(pageUrl).host?.removePrefix("www.") }.getOrNull().orEmpty()
        if (host.isBlank()) return null
        val conn = (URL(pageUrl).openConnection() as? HttpURLConnection) ?: return null
        try {
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 YidPlusApp")
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            conn.connect()
            if (conn.responseCode !in 200..299) return null
            val type = conn.contentType.orEmpty()
            if (type.isNotBlank() && !type.contains("text/html", ignoreCase = true) && !type.contains("application/xhtml+xml", ignoreCase = true)) return null
            val buf = ByteArray(MAX_BYTES)
            var n = 0
            conn.inputStream.use { input ->
                while (n < buf.size) {
                    val read = input.read(buf, n, buf.size - n)
                    if (read <= 0) break
                    n += read
                }
            }
            if (n == 0) return null
            val html = String(buf, 0, n, Charsets.UTF_8)
            // OpenGraph tags live in <head>; ignore the body entirely.
            val head = html.substringBefore("</head>", html)
            val title = og(head, "og:title") ?: pageTitle(head)
            val desc = og(head, "og:description").orEmpty()
            val image = og(head, "og:image")?.takeIf { it.isNotBlank() }?.let { resolve(pageUrl, it) }
            val siteName = og(head, "og:site_name")?.takeIf { it.isNotBlank() }
            if (title.isNullOrBlank() && image == null) return null
            return ChLink(
                url = pageUrl,
                title = title?.ifBlank { host } ?: host,
                desc = desc,
                image = image,
                host = siteName ?: host,
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Value of the <meta property="<property>" content="..."> tag, either attribute order. */
    private fun og(head: String, property: String): String? {
        var from = 0
        while (true) {
            val at = head.indexOf(property, from, ignoreCase = true)
            if (at < 0) return null
            val tagStart = head.lastIndexOf("<meta", at, ignoreCase = true)
            val tagEnd = head.indexOf('>', at)
            if (tagStart >= 0 && tagEnd > tagStart) {
                val tag = head.substring(tagStart, tagEnd)
                val value = attr(tag, "content")?.let { decode(it) }?.trim()
                if (!value.isNullOrBlank()) return value
            }
            from = at + property.length
        }
    }

    /** Contents of the <title> element, if any. */
    private fun pageTitle(head: String): String? {
        val start = head.indexOf("<title", ignoreCase = true)
        if (start < 0) return null
        val contentStart = head.indexOf('>', start)
        val end = head.indexOf("</title>", contentStart, ignoreCase = true)
        if (contentStart < 0 || end < 0) return null
        return decode(head.substring(contentStart + 1, end)).trim().takeIf { it.isNotBlank() }
    }

    /** Value of attribute [name] inside a tag fragment, double- or single-quoted. */
    private fun attr(tag: String, name: String): String? {
        val at = tag.indexOf(name, ignoreCase = true)
        if (at < 0) return null
        val eq = tag.indexOf('=', at + name.length)
        if (eq < 0) return null
        var i = eq + 1
        while (i < tag.length && tag[i].isWhitespace()) i++
        if (i >= tag.length) return null
        val quote = tag[i]
        if (quote != '"' && quote != '\'') return null
        val end = tag.indexOf(quote, i + 1)
        if (end < 0) return null
        return tag.substring(i + 1, end)
    }

    /** Resolves a possibly relative og:image URL against the page URL. */
    private fun resolve(pageUrl: String, image: String): String? {
        if (image.startsWith("data:")) return null
        return runCatching { URL(URL(pageUrl), image).toString() }.getOrNull()
    }

    /** Decodes the few HTML entities that appear in meta values. */
    private fun decode(raw: String): String {
        var out = raw
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
        // Numeric entities such as &#8212;
        while (true) {
            val start = out.indexOf("&#")
            if (start < 0) break
            val end = out.indexOf(';', start)
            if (end < 0) break
            val code = out.substring(start + 2, end).toIntOrNull() ?: break
            val chars = runCatching { Character.toChars(code) }.getOrNull() ?: break
            out = out.substring(0, start) + String(chars) + out.substring(end + 1)
        }
        return out
    }
}
