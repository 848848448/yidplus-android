package com.yidplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yidplus.app.data.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** WhatsApp/Google-style GIF picker: search Giphy (through the site), tap one to send it as an animated GIF. */
@Composable
internal fun GifPickerPanel(onPick: (String) -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var gifs by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(q) {
        gifs = null
        delay(350)
        gifs = runCatching {
            withContext(Dispatchers.IO) {
                val path = "/api/gif" + if (q.isBlank()) "" else "?q=" + java.net.URLEncoder.encode(q.trim(), "UTF-8")
                chObjs(Api.get(path).optJSONArray("gifs")).mapNotNull { o ->
                    val p = o.chStr("preview"); val u = o.chStr("url")
                    if (p.isBlank() || u.isBlank()) null else p to u
                }
            }
        }.getOrDefault(emptyList())
    }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().height(300.dp)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                ChSearchField(q, { q = it }, "Search GIFs", onClose)
            }
            val list = gifs
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    list == null -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    list.isEmpty() -> Text("No GIFs found", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    else -> LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(4.dp)) {
                        items(list, key = { it.second }) { (preview, fullUrl) ->
                            AsyncImage(preview, "GIF", Modifier.padding(3.dp).fillMaxWidth().height(100.dp)
                                .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                                .tap(RoundedCornerShape(8.dp)) { onPick(fullUrl) }, contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
            Text("Powered by GIPHY", Modifier.fillMaxWidth().padding(bottom = 4.dp), fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
