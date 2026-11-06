package com.zpic.launcher.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zpic.launcher.data.Settings
import com.zpic.launcher.data.rank
import com.zpic.launcher.data.searchSettings
import com.zpic.launcher.system.SystemActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun granted(c: Context, p: String) = c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

private fun allShortcuts(context: Context): List<ShortcutInfo> = runCatching {
    val launcher = context.getSystemService(LauncherApps::class.java)
    if (!launcher.hasShortcutHostPermission()) return emptyList()
    val q = LauncherApps.ShortcutQuery().setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
    launcher.profiles.flatMap { launcher.getShortcuts(q, it).orEmpty() }.filter { it.isEnabled }
}.getOrDefault(emptyList())

private data class Contact(val name: String, val uri: Uri)

private fun contacts(context: Context, query: String): List<Contact> {
    if (!granted(context, Manifest.permission.READ_CONTACTS)) return emptyList()
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
    return runCatching {
        context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext() && size < 3) add(Contact(c.getString(1).orEmpty(), Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, c.getLong(0).toString())))
            }
        }.orEmpty()
    }.getOrDefault(emptyList())
}

/** The next calendar event in the coming day, for the line under the clock. */
fun nextEvent(context: Context): String? {
    if (!granted(context, Manifest.permission.READ_CALENDAR)) return null
    val now = System.currentTimeMillis()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        android.content.ContentUris.appendId(it, now); android.content.ContentUris.appendId(it, now + 86_400_000L)
    }.build()
    return runCatching {
        context.contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN), "${CalendarContract.Instances.ALL_DAY}=0", null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            if (!c.moveToFirst()) null
            else "${c.getString(0)} · ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(c.getLong(1)))}"
        }
    }.getOrNull()
}

@Composable
private fun ExtraRow(icon: ImageVector, title: String, hint: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = scheme.primary)
        Text(title, Modifier.weight(1f).padding(horizontal = 12.dp), color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(hint, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
    }
}

/** Beyond apps: contacts if allowed, app shortcuts, and pages of the phone's Settings. */
@Composable
fun SearchExtras(query: String, s: Settings) {
    val context = LocalContext.current
    val launcher = remember { context.getSystemService(LauncherApps::class.java) }
    val shortcuts by produceState(emptyList<ShortcutInfo>()) { value = withContext(Dispatchers.IO) { allShortcuts(context) } }
    val people by produceState(emptyList<Contact>(), query, s.searchContacts) {
        value = emptyList()
        if (s.searchContacts && query.length >= 2) { delay(120); value = withContext(Dispatchers.IO) { contacts(context, query) } }
    }
    people.forEach { p -> ExtraRow(Icons.Outlined.Person, p.name, "Contact") { SystemActions.start(context, Intent(Intent.ACTION_VIEW, p.uri)) } }
    val matched = remember(shortcuts, query) { shortcuts.filter { rank(query, (it.shortLabel ?: "").toString()) > 0 }.take(3) }
    matched.forEach { sc ->
        ExtraRow(Icons.Outlined.ArrowOutward, (sc.shortLabel ?: "").toString(), "Shortcut") { runCatching { launcher.startShortcut(sc, null, null) } }
    }
    searchSettings(query).forEach { l -> ExtraRow(Icons.Outlined.Settings, l.title, "Settings") { SystemActions.start(context, Intent(l.action)) } }
}
