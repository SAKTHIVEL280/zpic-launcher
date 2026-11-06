package com.zpic.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ThemeMode { System, Light, Dark }
enum class Palette { Wallpaper, Extracted, Custom, Slate, Sage, Clay, Rose, Iris, Graphite }
enum class FontChoice { System, Serif, Mono, Condensed }
enum class MotionLevel { Expressive, Standard, Reduced }
enum class ClockStyle { Stacked, Inline, OneUi, Ios, Dots, Accent, Hyper, Cards, Analog, Words, Small, Hidden }
enum class ClockWeight { Thin, Light, Regular, Medium, Bold }
enum class ClockTint { Wallpaper, Ink, Primary, Secondary, Tertiary }
enum class HourFormat { System, H12, H24 }
enum class DateStyle { Short, Long, Numeric }
enum class HomeAlign { Start, Center, End }
enum class FavStyle { Text, IconText, Icons }
enum class DrawerMode { Text, Icons, Custom, Groups }
enum class RailSide { End, Start, Off }
enum class IconStyle { System, Tonal, Surface, Ghost, Monogram }
enum class AppAnim { Scale, Rise, Reveal, System }
enum class GlyphInk { Theme, White }
enum class IconOverride { Auto, Glyph, Monogram, Original }
enum class IconShape { Circle, Squircle, Square, Cookie, Clover, Sunny, Arch }
enum class Gesture { None, Notifications, Drawer, Search, Lock, Settings, App }

@Serializable
data class RecentUse(val key: String, val at: Long)

@Serializable
data class GestureBinding(val action: Gesture = Gesture.None, val app: String? = null)

@Serializable
data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    val palette: Palette = Palette.Wallpaper,
    val pureBlack: Boolean = false,
    val customHue: Float = 225f,
    val customChroma: Float = .45f,
    val wallpaperSeed: Int? = null,
    val font: FontChoice = FontChoice.System,
    val motion: MotionLevel = MotionLevel.Expressive,
    val animSpeed: Float = 1f,
    val animBounce: Float = .55f,
    val appAnim: AppAnim = AppAnim.Scale,
    val haptics: Boolean = true,
    val hapticStrength: Float = 0.7f,

    val clock: ClockStyle = ClockStyle.Stacked,
    val clockWeight: ClockWeight? = null,
    val clockTint: ClockTint = ClockTint.Wallpaper,
    val hourFormat: HourFormat = HourFormat.System,
    val leadingZero: Boolean = false,
    val showAmPm: Boolean = false,
    val dateStyle: DateStyle = DateStyle.Short,
    val dateAbove: Boolean = false,
    val showDate: Boolean = true,
    val showAlarm: Boolean = true,
    val showBattery: Boolean = false,
    val intention: String = "",
    val align: HomeAlign = HomeAlign.Start,
    val favStyle: FavStyle = FavStyle.Text,
    val favorites: List<String> = emptyList(),
    val widgetId: Int = -1,
    val grid: List<HomeCell>? = null,
    val homeColumns: Int = 5,
    val homeRows: Int = 6,
    val showDock: Boolean = false,
    val dock: List<String> = emptyList(),
    val homeLabels: Boolean = true,
    val lockLayout: Boolean = false,
    val hideStatusBar: Boolean = false,
    val homePages: Int = 1,
    val homeMain: Int = 0,

    val drawerMode: DrawerMode = DrawerMode.Text,
    val columns: Int = 4,
    val iconSize: Int = 56,
    val showLabels: Boolean = true,
    val rail: RailSide = RailSide.End,
    val customRows: Int = 6,
    val customSlots: Map<String, Int> = emptyMap(),
    val groupOverrides: Map<String, String> = emptyMap(),
    val drawerTextSize: Int = 24,
    val drawerOpacity: Float = 0.94f,
    val autoKeyboard: Boolean = false,
    val autoLaunch: Boolean = false,
    val suggestions: Boolean = true,

    val iconStyle: IconStyle = IconStyle.Tonal,
    val iconShape: IconShape = IconShape.Squircle,
    val glyphInk: GlyphInk = GlyphInk.Theme,
    val iconPack: String? = null,

    val wallpaperDim: Float = 0.15f,
    val lockFollowsHome: Boolean = false,

    val swipeDown: GestureBinding = GestureBinding(Gesture.Notifications),
    val swipeLeft: GestureBinding = GestureBinding(),
    val swipeRight: GestureBinding = GestureBinding(),
    val doubleTap: GestureBinding = GestureBinding(Gesture.Lock),

    val hidden: Set<String> = emptySet(),
    val renamed: Map<String, String> = emptyMap(),
    val distracting: Set<String> = emptySet(),
    val pauseSeconds: Int = 5,
    val dots: Boolean = false,
    val shelfExcluded: Set<String> = emptySet(),
    val iconOverrides: Map<String, IconOverride> = emptyMap(),
    val launchCounts: Map<String, Int> = emptyMap(),
    val recents: List<RecentUse> = emptyList(),
    val recentsFromSystem: Boolean = false,
    val onboarded: Boolean = false,
)

private val Context.store by preferencesDataStore("zpic")
private val KEY = stringPreferencesKey("settings")
val SettingsJson = Json { ignoreUnknownKeys = true; encodeDefaults = false; coerceInputValues = true }

class SettingsRepo(private val context: Context, private val scope: CoroutineScope) {
    private val state = MutableStateFlow(
        // A launcher must paint correctly on its first frame, so the single small read is done up front.
        runBlocking { context.store.data.map { it[KEY] }.first() }?.let(::decode) ?: Settings()
    )
    val settings: StateFlow<Settings> = state

    fun update(change: (Settings) -> Settings) {
        val next = synchronized(state) { change(state.value).also { state.value = it } }
        scope.launch { context.store.edit { it[KEY] = SettingsJson.encodeToString(Settings.serializer(), next) } }
    }

    fun export(): String = SettingsJson.encodeToString(Settings.serializer(), state.value)

    fun import(json: String): Boolean {
        val parsed = decode(json) ?: return false
        update { parsed }
        return true
    }

    private fun decode(json: String) = runCatching { SettingsJson.decodeFromString(Settings.serializer(), json) }.getOrNull()
}
