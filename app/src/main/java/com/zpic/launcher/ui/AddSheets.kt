package com.zpic.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Subject
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zpic.launcher.data.AppEntry
import com.zpic.launcher.data.HomeCell
import com.zpic.launcher.data.HomeKind
import com.zpic.launcher.data.Settings
import com.zpic.launcher.data.addApps
import com.zpic.launcher.data.appSpanX
import com.zpic.launcher.data.appSpanY
import com.zpic.launcher.data.addFolder
import com.zpic.launcher.data.findFree
import com.zpic.launcher.data.label
import com.zpic.launcher.data.newId
import com.zpic.launcher.data.rank

@Composable
private fun AppChecklist(apps: List<AppEntry>, s: Settings, picked: MutableList<String>, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    val colors = rememberIconColors(MaterialTheme.colorScheme.onSurface, s.glyphInk)
    val shown = remember(apps, query, s.hidden) {
        apps.filter { it.key !in s.hidden && (query.isBlank() || rank(query, it.label(s)) > 0) }.sortedBy { it.label(s).lowercase() }
    }
    Column(modifier) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 24.dp), singleLine = true, placeholder = { Text("Search apps") })
        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
            items(shown, key = { it.key }) { a ->
                val on = a.key in picked
                Row(
                    Modifier.fillMaxWidth().clickable { if (on) picked.remove(a.key) else picked.add(a.key) }.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(a, s, 40, colors)
                    Text(a.label(s), Modifier.weight(1f).padding(start = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Checkbox(on, null)
                }
            }
        }
    }
}

@Composable
private fun ElementRow(icon: ImageVector, title: String, hint: String, added: Boolean, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (added) Icon(Icons.Outlined.CheckCircle, "On home screen", tint = MaterialTheme.colorScheme.primary)
        else FilledTonalButton(onAdd) { Text("Add") }
    }
}

/** Everything that can go on a home page, including whatever was removed earlier. */
@Composable
fun AddToHomeSheet(apps: List<AppEntry>, s: Settings, items: List<HomeCell>, page: Int, onDismiss: () -> Unit, onDock: () -> Unit, onDone: (List<HomeCell>) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val picked = remember { mutableStateListOf<String>() }
    fun element(kind: HomeKind, id: String, spanX: Int, spanY: Int) {
        val spot = findFree(items, spanX, spanY, page, Int.MAX_VALUE / 2, s.homeColumns, s.homeRows) ?: return
        onDone(items + HomeCell(id, kind, spot.first, spot.second, spot.third, spanX, spanY))
    }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(.92f).navigationBarsPadding()) {
            Text("Add to home", Modifier.padding(start = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.headlineSmall)
            SecondaryTabRow(tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Apps") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Clock and more") })
            }
            if (tab == 0) {
                AppChecklist(apps, s, picked, Modifier.weight(1f).padding(top = 12.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ onDone(addApps(items, picked.toList(), page, s.homeColumns, s.homeRows)) }, Modifier.weight(1f), enabled = picked.isNotEmpty()) {
                        Text(if (picked.size > 1) "Add ${picked.size} apps" else "Add app", maxLines = 1)
                    }
                    FilledTonalButton({ onDone(addFolder(items, picked.toList(), "Folder", page, s.homeColumns, s.homeRows)) }, Modifier.weight(1f), enabled = picked.size > 1) {
                        Text("Group in folder", maxLines = 1)
                    }
                }
            } else Column(Modifier.padding(top = 8.dp)) {
                ElementRow(Icons.Outlined.Schedule, "Clock", "Time and date, in your chosen style", items.any { it.kind == HomeKind.Clock }) { element(HomeKind.Clock, newId("clock"), (s.homeColumns * 3 / 5).coerceAtLeast(2), s.homeRows / 3) }
                ElementRow(Icons.Outlined.ViewList, "Pinned list", "Your pinned apps as a tidy list", items.any { it.kind == HomeKind.Pins }) { element(HomeKind.Pins, newId("pins"), (s.homeColumns * 3 / 5).coerceAtLeast(2), s.homeRows / 3) }
                ElementRow(Icons.Outlined.Subject, "Today's intention", "The line you set in Settings", items.any { it.kind == HomeKind.Intention }) { element(HomeKind.Intention, newId("int"), s.homeColumns, appSpanY(s.homeRows)) }
                ElementRow(Icons.Outlined.Notifications, "Notification shelf", "Apps with something waiting", items.any { it.kind == HomeKind.Shelf }) { element(HomeKind.Shelf, newId("shelf"), appSpanX(s.homeColumns), appSpanY(s.homeRows) * 2) }
                ElementRow(Icons.Outlined.ViewList, "Dock", "A fixed row of apps on every page", s.showDock && s.dock.isNotEmpty(), onDock)
            }
        }
    }
}

@Composable
fun DockSheet(apps: List<AppEntry>, s: Settings, onDismiss: () -> Unit, onDone: (List<String>) -> Unit) {
    val picked = remember { mutableStateListOf<String>().apply { addAll(s.dock) } }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(.92f).navigationBarsPadding()) {
            Text("Dock apps", Modifier.padding(start = 24.dp, bottom = 12.dp), style = MaterialTheme.typography.headlineSmall)
            AppChecklist(apps, s, picked, Modifier.weight(1f))
            Box(Modifier.padding(24.dp, 12.dp)) { Button({ onDone(picked.toList().take(6)) }, Modifier.fillMaxWidth()) { Text("Save (up to 6)") } }
        }
    }
}
