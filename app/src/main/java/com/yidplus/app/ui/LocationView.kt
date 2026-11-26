package com.yidplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * In-app location screen for a chat location message, in the style of WhatsApp:
 * the place with a pin, its coordinates and actions. Opened with
 * Nav.open(LocationView(url, coords)); a PageScreen, so no edit to Nav.kt or
 * AppRoot.kt is needed and Back closes it.
 *
 * There is no maps SDK in the project and no maps API key, so the screen shows
 * a pin panel with the coordinates instead of a live map. Directions and
 * Open in Maps leave the app on purpose, from those buttons only.
 */
data class LocationView(val url: String, val coords: String) : PageScreen() {
    @Composable override fun Content() { LocationScreen(url, coords) }
}

@Composable
private fun LocationScreen(url: String, coords: String) {
    val c = ctx()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ChBar("Location", sub = coords, onBack = { Nav.back() })
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Box(
                Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Sym.LocationOn, "Location", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(coords, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Location", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Text(coords, fontSize = 15.sp)
            Text(url, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(
                "A live map is not available in the app yet: it needs a maps API key. " +
                    "Open the location in Maps for the live map.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = { openUrl(c, "https://www.google.com/maps/dir/?api=1&destination=$coords") },
                    modifier = Modifier.weight(1f)
                ) { Text("Directions") }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(
                    onClick = { share(c, "📍 Location: $url") },
                    modifier = Modifier.weight(1f)
                ) { Text("Share") }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { openUrl(c, url) }, modifier = Modifier.fillMaxWidth()) { Text("Open in Maps") }
        }
    }
}
