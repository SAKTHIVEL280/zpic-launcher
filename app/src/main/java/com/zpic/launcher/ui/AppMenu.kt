package com.zpic.launcher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zpic.launcher.ZpicApp
import com.zpic.launcher.data.AppEntry
import com.zpic.launcher.data.DrawerFolder
import com.zpic.launcher.data.newId
import com.zpic.launcher.data.IconOverride
import com.zpic.launcher.data.AppGroup
import com.zpic.launcher.data.group
import com.zpic.launcher.data.Settings
import com.zpic.launcher.data.SettingsRepo
import com.zpic.launcher.data.label
import com.zpic.launcher.system.SystemActions

private fun IconOverride.title() = when (this) {
    IconOverride.Auto -> "Automatic"
    IconOverride.Glyph -> "Logo in theme colour"
    IconOverride.Monogram -> "Letter"
    IconOverride.Original -> "Original colours"
}

private fun shortcuts(context: Context, app: AppEntry): List<ShortcutInfo> {
    val launcher = context.getSystemService(LauncherApps::class.java)
    if (!launcher.hasShortcutHostPermission()) return emptyList()
    val query = LauncherApps.ShortcutQuery().setPackage(app.packageName).setQueryFlags(
        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
    )
    return runCatching { launcher.getShortcuts(query, app.user).orEmpty().filter { it.isEnabled }.take(4) }.getOrDefault(emptyList())
}

@Composable
fun AppMenu(app: AppEntry, settings: Settings, repo: SettingsRepo, onPlace: ((AppEntry) -> Unit)? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var styling by remember { mutableStateOf(false) }
    var foldering by remember { mutableStateOf(false) }
    var doubling by remember { mutableStateOf(false) }
    val allApps by (context.applicationContext as ZpicApp).apps.apps.collectAsState()
    val shortcutList = remember(app) { shortcuts(context, app) }
    val done = onDismiss
    val colors = rememberIconColors(MaterialTheme.colorScheme.onSurface, settings.glyphInk)
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, settings, 40, colors)
                Text(app.label(settings), Modifier.padding(start = 16.dp), style = MaterialTheme.typography.titleLarge)
            }
            shortcutList.forEach { s ->
                Item(Icons.Outlined.ArrowOutward, (s.shortLabel ?: s.longLabel ?: "").toString()) {
                    runCatching { context.getSystemService(LauncherApps::class.java).startShortcut(s, null, null) }
                    done()
                }
            }
            if (shortcutList.isNotEmpty()) HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            val pinned = app.key in settings.favorites
            Item(Icons.Outlined.PushPin, if (pinned) "Remove from pinned list" else "Add to pinned list") {
                repo.update { it.copy(favorites = if (pinned) it.favorites - app.key else it.favorites + app.key) }
                done()
            }
            onPlace?.let { place -> Item(Icons.Outlined.Home, "Add to home screen") { place(app); done() } }
            Item(Icons.Outlined.DriveFileRenameOutline, "Rename") { renaming = true }
            Item(Icons.Outlined.CreateNewFolder, "Add to app page folder") { foldering = true }
            val second = settings.appDouble[app.key]?.let { k -> allApps.firstOrNull { it.key == k } }
            Item(Icons.Outlined.TouchApp, if (second != null) "Double-tap opens ${second.label(settings)}" else "Double-tap on home opens…") { doubling = true }
            val look = settings.iconOverrides[app.key] ?: IconOverride.Auto
            Item(Icons.Outlined.AutoFixHigh, "Icon: ${look.title()}") { styling = true }
            val flagged = app.key in settings.distracting
            Item(Icons.Outlined.SelfImprovement, if (flagged) "Remove pause" else "Pause before opening") {
                repo.update { it.copy(distracting = if (flagged) it.distracting - app.key else it.distracting + app.key) }
                done()
            }
            Item(Icons.Outlined.FolderOpen, "Group: ${app.group(settings).title}") { moving = true }
            Item(Icons.Outlined.VisibilityOff, "Hide") {
                repo.update { it.copy(hidden = it.hidden + app.key, favorites = it.favorites - app.key) }
                done()
            }
            Item(Icons.Outlined.Info, "App info") {
                context.getSystemService(LauncherApps::class.java).startAppDetailsActivity(app.component, app.user, null, null)
                done()
            }
            Item(Icons.Outlined.Delete, "Uninstall") { SystemActions.uninstall(context, app); done() }
        }
    }
    if (doubling) AlertDialog(
        onDismissRequest = { doubling = false },
        title = { Text("Double-tap opens") },
        confirmButton = { TextButton({ repo.update { it.copy(appDouble = it.appDouble - app.key) }; doubling = false; done() }) { Text("Nothing") } },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                allApps.filter { it.key != app.key }.forEach { a ->
                    Text(a.label(settings), Modifier.fillMaxWidth().clickable { repo.update { it.copy(appDouble = it.appDouble + (app.key to a.key)) }; doubling = false; done() }.padding(vertical = 12.dp))
                }
            }
        },
    )
    if (foldering) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { foldering = false },
            title = { Text("App page folder") },
            text = {
                Column {
                    settings.drawerFolders.forEach { f ->
                        Text(f.title, Modifier.fillMaxWidth().clickable {
                            repo.update { s -> s.copy(drawerFolders = s.drawerFolders.map { if (it.id == f.id) it.copy(apps = (it.apps + app.key).distinct()) else it.copy(apps = it.apps - app.key) }.filter { it.apps.isNotEmpty() }) }
                            foldering = false; done()
                        }.padding(vertical = 12.dp))
                    }
                    OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("New folder") })
                }
            },
            confirmButton = {
                TextButton({
                    val t = name.trim()
                    if (t.isNotEmpty()) repo.update { s -> s.copy(drawerFolders = s.drawerFolders.map { it.copy(apps = it.apps - app.key) }.filter { it.apps.isNotEmpty() } + DrawerFolder(newId("d"), t, listOf(app.key))) }
                    foldering = false; done()
                }) { Text("Create") }
            },
            dismissButton = { TextButton({ foldering = false }) { Text("Cancel") } },
        )
    }
    if (styling) AlertDialog(
        onDismissRequest = { styling = false },
        title = { Text("This app's icon") },
        confirmButton = { TextButton({ styling = false }) { Text("Cancel") } },
        text = {
            Column {
                IconOverride.entries.forEach { o ->
                    Text(o.title(), Modifier.fillMaxWidth().clickable {
                        repo.update { it.copy(iconOverrides = if (o == IconOverride.Auto) it.iconOverrides - app.key else it.iconOverrides + (app.key to o)) }
                        styling = false; done()
                    }.padding(vertical = 12.dp))
                }
            }
        },
    )
    if (moving) AlertDialog(
        onDismissRequest = { moving = false },
        title = { Text("Move to group") },
        confirmButton = { TextButton({ moving = false }) { Text("Cancel") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AppGroup.entries.forEach { g ->
                    Text(g.title, Modifier.fillMaxWidth().clickable {
                        repo.update { it.copy(groupOverrides = it.groupOverrides + (app.key to g.name)) }
                        moving = false; done()
                    }.padding(vertical = 12.dp))
                }
            }
        },
    )
    if (renaming) {
        var text by remember { mutableStateOf(app.label(settings)) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton({
                    val name = text.trim()
                    repo.update { it.copy(renamed = if (name.isEmpty() || name == app.systemLabel) it.renamed - app.key else it.renamed + (app.key to name)) }
                    renaming = false; done()
                }) { Text("Save") }
            },
            dismissButton = { TextButton({ renaming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Item(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
