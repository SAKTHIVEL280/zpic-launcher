package com.zpic.launcher.data

import kotlinx.serialization.Serializable

@Serializable
data class DrawerFolder(val id: String, val title: String, val apps: List<String>)

/** A profile that hides apps, by hand or on a daily schedule. Hours of -1 mean no schedule. */
@Serializable
data class FocusMode(val id: String, val name: String, val hidden: Set<String> = emptySet(), val startHour: Int = -1, val endHour: Int = -1, val page: Int = -1)

val defaultModes = listOf(FocusMode("work", "Work"), FocusMode("sleep", "Sleep"))

fun activeMode(s: Settings, hour: Int): FocusMode? {
    s.modes.firstOrNull { it.id == s.manualMode }?.let { return it }
    return s.modes.firstOrNull { m ->
        m.startHour in 0..23 && m.endHour in 0..23 && m.startHour != m.endHour &&
            if (m.startHour < m.endHour) hour in m.startHour until m.endHour else hour >= m.startHour || hour < m.endHour
    }
}

/** Counts an open, starting over on a new day. */
fun countOpen(s: Settings, key: String, day: Int): Settings {
    val counts = if (s.countsDay == day) s.openCounts else emptyMap()
    return s.copy(countsDay = day, openCounts = counts + (key to ((counts[key] ?: 0) + 1)))
}

/** A layout others can import: looks and arrangement, none of this person's private habits. */
fun shareable(s: Settings) = s.copy(
    sharedLayout = true, intention = "", hidden = emptySet(), renamed = emptyMap(), distracting = emptySet(), launchCounts = emptyMap(),
    recents = emptyList(), openCounts = emptyMap(), modes = emptyList(), manualMode = null, wallpaperSeed = null, widgetId = -1,
    grid = s.grid?.filter { it.kind != HomeKind.Widget }, iconOverrides = emptyMap(), appDouble = emptyMap(), drawerFolders = emptyList(),
)

/** Applies a shared layout over someone's own setup, keeping everything personal. */
fun applyShared(mine: Settings, shared: Settings) = shared.copy(
    sharedLayout = false, intention = mine.intention, hidden = mine.hidden, renamed = mine.renamed, distracting = mine.distracting,
    launchCounts = mine.launchCounts, recents = mine.recents, openCounts = mine.openCounts, countsDay = mine.countsDay, modes = mine.modes,
    manualMode = mine.manualMode, wallpaperSeed = mine.wallpaperSeed, favorites = mine.favorites, dock = mine.dock, iconOverrides = mine.iconOverrides,
    appDouble = mine.appDouble, drawerFolders = mine.drawerFolders, onboarded = true, gridScaled = true,
    grid = shared.grid.orEmpty().filter { it.kind != HomeKind.App && it.kind != HomeKind.Folder } + mine.grid.orEmpty().filter { it.kind == HomeKind.Widget || it.kind == HomeKind.App || it.kind == HomeKind.Folder },
)

data class SettingsLink(val title: String, val action: String)

private val settingsLinks = listOf(
    SettingsLink("Wi-Fi", "android.settings.WIFI_SETTINGS"), SettingsLink("Bluetooth", "android.settings.BLUETOOTH_SETTINGS"),
    SettingsLink("Mobile network", "android.settings.DATA_ROAMING_SETTINGS"), SettingsLink("Battery", "android.intent.action.POWER_USAGE_SUMMARY"),
    SettingsLink("Display", "android.settings.DISPLAY_SETTINGS"), SettingsLink("Sound", "android.settings.SOUND_SETTINGS"),
    SettingsLink("Storage", "android.settings.INTERNAL_STORAGE_SETTINGS"), SettingsLink("Apps", "android.settings.MANAGE_APPLICATIONS_SETTINGS"),
    SettingsLink("Notifications", "android.settings.ALL_APPS_NOTIFICATION_SETTINGS"), SettingsLink("Accessibility", "android.settings.ACCESSIBILITY_SETTINGS"),
    SettingsLink("Location", "android.settings.LOCATION_SOURCE_SETTINGS"), SettingsLink("Security", "android.settings.SECURITY_SETTINGS"),
    SettingsLink("Date and time", "android.settings.DATE_SETTINGS"), SettingsLink("Language", "android.settings.LOCALE_SETTINGS"),
)

fun searchSettings(query: String) = settingsLinks.filter { rank(query, it.title) >= 50 }.take(3)
