package com.yidplus.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.yidplus.app.data.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** In-app PDF viewer for a chat file message. Opened with Nav.open(PdfView(url, fileName));
 * a PageScreen, so no edit to Nav.kt or AppRoot.kt is needed and Back closes it. */
data class PdfView(val url: String, val fileName: String = "PDF", val remoteUrl: String? = null) : PageScreen() {
    @Composable override fun Content() { PdfViewScreen(url, fileName, remoteUrl) }
}

@Composable
internal fun PdfViewScreen(url: String, fileName: String, remoteUrl: String? = null) {
    val c = ctx()
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var fellBack by remember { mutableStateOf(false) }
    var pageCount by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var descriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }

    // Open the PDF once. A local file is used as-is; a remote one is downloaded to the
    // app cache first, with the same Api.http stream-to-cache download ChatPages uses
    // when forwarding a file.
    LaunchedEffect(url) {
        runCatching {
            val file = withContext(Dispatchers.IO) { resolvePdfFile(c, url) }
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            descriptor = pfd
            val opened = PdfRenderer(pfd)
            if (opened.pageCount == 0) error("Empty PDF")
            renderer = opened
            pageCount = opened.pageCount
        }.onFailure { if (it !is kotlinx.coroutines.CancellationException) failed = true }
        loading = false
    }
    // Close the renderer and its descriptor when this page leaves.
    DisposableEffect(Unit) {
        onDispose {
            runCatching { renderer?.close() }
            runCatching { descriptor?.close() }
        }
    }
    // If the PDF cannot be opened, fall back to the existing opener instead of
    // leaving the user on a dead page.
    LaunchedEffect(failed) {
        if (failed && !fellBack) {
            fellBack = true
            toast(c, "Couldn't open this PDF here")
            Nav.back()
            chOpenLink(c, remoteUrl ?: url)
        }
    }
    // Render the current page as a bitmap; a failed render falls back the same way.
    LaunchedEffect(renderer, page) {
        val opened = renderer ?: return@LaunchedEffect
        val rendered = withContext(Dispatchers.IO) {
            try {
                val current = opened.openPage(page.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
                val scale = if (current.width >= 1200 || current.height >= 1600) 1 else 2
                val bmp = Bitmap.createBitmap(current.width * scale, current.height * scale, Bitmap.Config.ARGB_8888)
                current.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                current.close()
                bmp
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
        }
        if (rendered == null) failed = true else bitmap = rendered
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ChBar(fileName, sub = if (pageCount > 0) "Page ${page + 1} of $pageCount" else "PDF", onBack = { Nav.back() })
        when {
            loading || bitmap == null && !failed -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            failed -> Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't open this PDF.", color = MaterialTheme.colorScheme.onBackground)
                    Button(onClick = { chOpenLink(c, remoteUrl ?: url) }, modifier = Modifier.padding(top = 12.dp)) { Text("Open externally") }
                }
            }
            else -> Box(Modifier.weight(1f).verticalScroll(rememberScrollState()).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Image(bitmap!!.asImageBitmap(), contentDescription = "Page ${page + 1} of $pageCount", modifier = Modifier.fillMaxWidth().padding(8.dp).background(Color.White))
            }
        }
        if (pageCount > 1) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { page -= 1 }, enabled = page > 0) { Icon(Sym.ChevronLeft, contentDescription = "Previous page") }
                Text("${page + 1} / $pageCount", color = MaterialTheme.colorScheme.onBackground)
                IconButton(onClick = { page += 1 }, enabled = page < pageCount - 1) { Icon(Sym.ChevronRight, contentDescription = "Next page") }
            }
        }
    }
}

/** A local PDF file is used as-is; a remote one is downloaded to the app cache first.
 * Content Uris are copied to the cache too, so the renderer always gets a seekable file. */
private fun resolvePdfFile(c: Context, url: String): File {
    if (url.startsWith("content://")) {
        val file = File(c.cacheDir, "pdf_" + System.nanoTime() + ".pdf")
        val descriptor = c.contentResolver.openFileDescriptor(Uri.parse(url), "r") ?: error("Cannot open PDF")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }
    if (url.startsWith("file://")) return File(Uri.parse(url).path ?: error("Bad file URL"))
    if (url.startsWith("/")) return File(url)
    val file = File(c.cacheDir, "pdf_" + System.nanoTime() + ".pdf")
    Api.http.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) error("Couldn't download the PDF")
        response.body?.byteStream()?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Empty PDF")
    }
    return file
}
