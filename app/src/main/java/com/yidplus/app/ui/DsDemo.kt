package com.yidplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Helper DS: a live sample of the shared design pieces (top bar without a duplicate button, SearchPill that hides on
 * scroll-down, list rows, Fab with fabSafePadding so the last row is never under it, Snack with Undo). QA opens it
 * with the QA_LOOK intent (--es demo 1); the real screens (Chats, Channels…) use the same components.
 */
class DsDemoPage : PageScreen() {
    override val perfName get() = "DsDemo"
    @Composable override fun Content() {
        val state = rememberLazyListState()
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            TopBar("Chats", onBack = { Nav.back() })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = fabSafePadding()) {
                    item { SearchPill("Search…", { Snack.show("Search opens here") }) }
                    items((1..24).toList()) { n ->
                        Row(Modifier.fillMaxWidth().heightIn(min = Dim.row).tap(RoundedCornerShape(0.dp)) { }.padding(horizontal = Dim.l),
                            verticalAlignment = Alignment.CenterVertically) {
                            Avatar(null, "Chat $n", size = Dim.avatar)
                            Column(Modifier.weight(1f).padding(start = Dim.m)) {
                                Text("Chat $n", fontSize = 16.sp, maxLines = 1)
                                Text(if (n == 24) "Last row: fully above the button" else "Message preview", fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
                Fab(Sym.AddComment, "New chat", { Snack.show("Chat archived", "Undo") { } }, Modifier.align(Alignment.BottomEnd))
            }
        }
    }
}
