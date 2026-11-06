package com.zpic.launcher.ui.settings

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zpic.launcher.ZpicApp
import com.zpic.launcher.data.AppAnim
import com.zpic.launcher.data.AppEntry
import com.zpic.launcher.data.ClockStyle
import com.zpic.launcher.data.ClockTint
import com.zpic.launcher.data.ClockWeight
import com.zpic.launcher.data.DateStyle
import com.zpic.launcher.data.DrawerMode

import com.zpic.launcher.data.FavStyle
import com.zpic.launcher.data.FontChoice
import com.zpic.launcher.data.GlyphInk
import com.zpic.launcher.data.Gesture
import com.zpic.launcher.data.GestureBinding
import com.zpic.launcher.data.HomeAlign
import com.zpic.launcher.data.HomeKind
import com.zpic.launcher.data.HomePreset
import com.zpic.launcher.data.applyPreset
import com.zpic.launcher.data.HourFormat
import com.zpic.launcher.data.gridOf
import com.zpic.launcher.data.withShelf
import com.zpic.launcher.data.IconShape
import com.zpic.launcher.data.IconStyle
import com.zpic.launcher.data.MotionLevel
import com.zpic.launcher.data.Palette
import com.zpic.launcher.data.RailSide
import com.zpic.launcher.data.Settings
import com.zpic.launcher.data.ThemeMode
import com.zpic.launcher.data.label
import com.zpic.launcher.system.ArtDesign
import com.zpic.launcher.system.ArtSpec
import com.zpic.launcher.system.artDesigns
import com.zpic.launcher.system.artSwatches
import com.zpic.launcher.system.DotsService
import com.zpic.launcher.system.SystemActions
import com.zpic.launcher.system.WallpaperArt
import com.zpic.launcher.system.WallpaperTarget
import com.zpic.launcher.ui.AppIcon
import com.zpic.launcher.ui.ClockFace
import com.zpic.launcher.ui.FitBox
import com.zpic.launcher.ui.LocalHaptics
import com.zpic.launcher.ui.LocalMotion
import com.zpic.launcher.ui.LocalWallpaperSeed
import com.zpic.launcher.ui.ZpicTheme
import com.zpic.launcher.ui.hasUsageAccess
import com.zpic.launcher.ui.rememberIconColors
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {
    companion object {
        const val ROUTE = "route"
        const val ROUTE_WALLPAPER = "Wallpaper"
        const val ROUTE_HOME = "Home screen"
        const val ROUTE_GESTURES = "Gestures"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val direct = intent.getStringExtra(ROUTE)
        setContent {
            val app = application as ZpicApp
            val settings by app.settings.settings.collectAsState()
            ZpicTheme(settings) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                    SettingsRoot(settings, direct) { finish() }
                }
            }
        }
    }
}

private typealias Up = ((Settings) -> Settings) -> Unit

private data class Page(val title: String, val hint: String, val icon: ImageVector)

private val pages = listOf(
    Page("Look and feel", "Colour, theme, type, motion, haptics", Icons.Outlined.Palette),
    Page("Clock", "Styles from every OS, weight, colour", Icons.Outlined.Schedule),
    Page("Home screen", "Grid, pinned apps, intention, layout", Icons.Outlined.Home),
    Page("App pages", "Text, icons, custom layout, smart groups", Icons.Outlined.Apps),
    Page("Icons", "Adaptive style, shape, icon packs", Icons.Outlined.Category),
    Page("Wallpaper", "60 designs you can recolour, lock screen", Icons.Outlined.Wallpaper),
    Page("Gestures", "Swipes and double-tap", Icons.Outlined.Swipe),
    Page("Focus", "Pauses, hidden apps, notification shelf", Icons.Outlined.SelfImprovement),
    Page("Backup", "Save or restore your setup", Icons.Outlined.Backup),
)

@Composable
private fun SettingsRoot(settings: Settings, direct: String?, finish: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ZpicApp
    val update: Up = { app.settings.update(it) }
    val motion = LocalMotion.current
    var route by rememberSaveable { mutableStateOf(direct) }
    BackHandler(route != null && direct == null) { route = null }

    AnimatedContent(
        route,
        transitionSpec = {
            val forward = targetState != null
            (slideInHorizontally(motion.spatial()) { if (forward) it / 4 else -it / 4 } + fadeIn(motion.effects()) + scaleIn(motion.spatial(), .94f)) togetherWith
                (slideOutHorizontally(motion.spatial()) { if (forward) -it / 4 else it / 4 } + fadeOut(motion.effects()))
        },
        label = "route",
    ) { current ->
        val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            Modifier.fillMaxSize().nestedScroll(bar.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                LargeFlexibleTopAppBar(
                    title = { Text(current ?: "Customize") },
                    navigationIcon = {
                        IconButton({ if (current == null || direct != null) finish() else route = null }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    scrollBehavior = bar,
                )
            },
        ) { inner ->
            Column(
                Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).navigationBarsPadding()
                    .padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (current) {
                    null -> if (settings.onboarded) Index { route = it } else Welcome(settings, update)
                    "Look and feel" -> Look(settings, update)
                    "Clock" -> ClockPage(settings, update)
                    "Home screen" -> HomePage(settings, update)
                    "App pages" -> AppsPage(settings, update)
                    "Icons" -> IconsPage(settings, update)
                    "Wallpaper" -> WallpaperPage(settings, update)
                    "Gestures" -> GesturesPage(settings, update)
                    "Focus" -> FocusPage(settings, update)
                    "Backup" -> BackupPage()
                }
            }
        }
    }
}

/** Rows joined by hairline gaps with a large outer radius: the grouped list of Android's own Settings. */
@Composable
private fun Group(title: String? = null, content: @Composable () -> Unit) {
    Column {
        if (title != null) Text(
            title, Modifier.padding(start = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}

@Composable
private fun RowSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceBright).then(modifier).padding(horizontal = 20.dp, vertical = 16.dp)) { content() }
}

@Composable
private fun TwoLine(title: String, hint: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** First run: only what someone must decide, and a way past it. */
@Composable
private fun Welcome(s: Settings, up: Up) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Group {
        Box(Modifier.fillMaxWidth().background(scheme.primaryContainer).padding(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Welcome to Zpic", style = MaterialTheme.typography.headlineMedium, color = scheme.onPrimaryContainer)
                Text("A calm home screen that stays out of your way. Set it up in a moment.", style = MaterialTheme.typography.bodyLarge, color = scheme.onPrimaryContainer)
            }
        }
    }
    Group("1. Your app page") {
        Choice("Show apps as", DrawerMode.entries, s.drawerMode, {
            when (it) {
                DrawerMode.Text -> "Names only, A to Z"
                DrawerMode.Icons -> "Icons with labels"
                DrawerMode.Custom -> "Custom pages"
                DrawerMode.Groups -> "Smart groups"
            }
        }, onPick = { v -> up { it.copy(drawerMode = v) } })
    }
    Group("2. Your icons") {
        Choice("Icon style", IconStyle.entries, s.iconStyle, { when (it) { IconStyle.System -> "Full colour"; IconStyle.Tonal -> "Tonal"; IconStyle.Surface -> "Surface"; IconStyle.Ghost -> "Glyph only"; IconStyle.Monogram -> "Monogram" } }, onPick = { v -> up { it.copy(iconStyle = v) } })
    }
    Group("3. Make it home") {
        Note("Set Zpic as your home app", "You can change this any time.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ up { it.copy(onboarded = true) }; if (!SystemActions.isDefaultHome(context)) SystemActions.requestDefaultHome(context as Activity) }) { Text("Finish") }
                TextButton({ up { it.copy(onboarded = true) } }) { Text("Skip") }
            }
        }
    }
}

@Composable
private fun Index(go: (String) -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    if (!SystemActions.isDefaultHome(context)) {
        Group {
            Box(Modifier.fillMaxWidth().background(colors.primaryContainer).padding(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Make Zpic your home screen", style = MaterialTheme.typography.titleLarge, color = colors.onPrimaryContainer)
                    Text("Press home to come back to it. App shortcuts also need this.", style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer)
                    Button({ SystemActions.requestDefaultHome(context as Activity) }) { Text("Set as default") }
                }
            }
        }
    }
    val shapes = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Cookie6Sided, MaterialShapes.Pill, MaterialShapes.Arch, MaterialShapes.Gem, MaterialShapes.Flower, MaterialShapes.Ghostish)
    val badges = listOf(
        colors.primaryContainer to colors.onPrimaryContainer, colors.tertiaryContainer to colors.onTertiaryContainer, colors.secondaryContainer to colors.onSecondaryContainer,
    )
    Group {
        pages.forEachIndexed { i, page ->
            val haptics = LocalHaptics.current
            RowSurface(Modifier.clickable { haptics?.tick(); go(page.title) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (bg, fg) = badges[i % badges.size]
                    val shape = shapes[i].toShape()
                    Box(Modifier.size(44.dp).clip(shape).background(bg), Alignment.Center) { Icon(page.icon, null, Modifier.size(22.dp), tint = fg) }
                    TwoLine(page.title, page.hint, Modifier.padding(start = 16.dp).weight(1f))
                    Icon(Icons.Outlined.ChevronRight, null, tint = colors.onSurfaceVariant)
                }
            }
        }
    }
}

/** Up to three short options sit in a connected button group; longer lists open a radio sheet. */
@Composable
private fun <T> Choice(title: String, options: List<T>, selected: T, name: (T) -> String = { it.toString() }, hint: String? = null, onPick: (T) -> Unit) {
    val haptics = LocalHaptics.current
    if (options.size <= 3) {
        RowSurface {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TwoLine(title, hint)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    options.forEachIndexed { i, o ->
                        ToggleButton(
                            o == selected, { haptics?.tick(); onPick(o) }, Modifier.weight(1f),
                            shapes = when (i) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) { Text(name(o), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
        return
    }
    var open by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, LocalMotion.current.spatial(), label = "chevron")
    RowSurface(Modifier.clickable { haptics?.tick(); open = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TwoLine(title, name(selected), Modifier.weight(1f))
            // A visible drop-down, so it's obvious this row opens a list.
            Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), Alignment.Center) {
                Icon(Icons.Outlined.ExpandMore, "Show options", Modifier.graphicsLayer { rotationZ = turn }, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
    if (open) ModalBottomSheet({ open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(title, Modifier.padding(start = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleLarge)
            options.forEach { o ->
                Row(
                    Modifier.fillMaxWidth().clickable { haptics?.tick(); onPick(o); open = false }.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(o == selected, null, Modifier.padding(12.dp))
                    Text(name(o), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun Toggle(title: String, hint: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = LocalHaptics.current
    RowSurface(Modifier.clickable { haptics?.tick(); onChange(!checked) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TwoLine(title, hint, Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Switch(checked, { haptics?.tick(); onChange(it) }, thumbContent = if (checked) { { Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) } } else null)
        }
    }
}

/** The slider is local while dragging so writes hit storage once, on release. */
@Composable
private fun Range(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, format: (Float) -> String, onChange: (Float) -> Unit) {
    var live by remember(value) { mutableFloatStateOf(value) }
    RowSurface {
        Column {
            Row {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(format(live), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Slider(live, { live = it }, valueRange = range, steps = steps, onValueChangeFinished = { onChange(live) })
        }
    }
}

@Composable
private fun Note(title: String, hint: String? = null, content: (@Composable () -> Unit)? = null) {
    RowSurface {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TwoLine(title, hint)
            content?.invoke()
        }
    }
}

@Composable
private fun Look(s: Settings, up: Up) {
    Group("Colour") {
        Choice("Colour source", Palette.entries, s.palette, {
            when (it) {
                Palette.Wallpaper -> "System colours (Material You, ColorBlendr)"
                Palette.Extracted -> "Directly from the wallpaper"
                Palette.Custom -> "My own colour"
                else -> it.name
            }
        }, onPick = { v -> up { it.copy(palette = v) } })
        if (s.palette == Palette.Extracted) {
            val seed = LocalWallpaperSeed.current
            Note(if (seed != null) "Colour found in your wallpaper" else "Couldn't read this wallpaper", if (seed != null) null else "Using system colours. Set a wallpaper from Zpic, or choose My own colour.") {
                if (seed != null) Box(Modifier.size(width = 72.dp, height = 28.dp).clip(CircleShape).background(Color(seed)))
            }
        }
        if (s.palette == Palette.Custom) {
            Range("Hue", s.customHue, 0f..360f, format = { "${it.toInt()}" }) { v -> up { it.copy(customHue = v) } }
            Range("Colourfulness", s.customChroma, .05f..1f, format = { "${(it * 100).toInt()}%" }) { v -> up { it.copy(customChroma = v) } }
        }
        if (!s.pureBlack) Choice("Theme", ThemeMode.entries, s.themeMode, onPick = { v -> up { it.copy(themeMode = v) } })
        Toggle("Pure black", "True black everywhere, for OLED screens. Turns dark theme on.", s.pureBlack) { v -> up { it.copy(pureBlack = v) } }
    }
    Group("Type") { Choice("Typeface", FontChoice.entries, s.font, onPick = { v -> up { it.copy(font = v) } }) }
    Group("Motion and touch") {
        Choice("Motion", MotionLevel.entries, s.motion, onPick = { v -> up { it.copy(motion = v) } })
        Range("Animation speed", s.animSpeed, .5f..2f, format = { "${(it * 100).toInt()}%" }) { v -> up { it.copy(animSpeed = v) } }
        if (s.motion == MotionLevel.Expressive) Range("Bounce", s.animBounce, 0f..1f, format = { "${(it * 100).toInt()}%" }) { v -> up { it.copy(animBounce = v) } }
        Choice("Opening apps", AppAnim.entries, s.appAnim, {
            when (it) {
                AppAnim.Scale -> "Grow from the icon"
                AppAnim.Rise -> "Rise and fade"
                AppAnim.Reveal -> "Reveal from the icon"
                AppAnim.System -> "Phone's default"
            }
        }, onPick = { v -> up { it.copy(appAnim = v) } })
        Toggle("Haptics", null, s.haptics) { v -> up { it.copy(haptics = v) } }
        if (s.haptics) Range("Haptic strength", s.hapticStrength, .2f..1f, format = { "${(it * 100).toInt()}%" }) { v -> up { it.copy(hapticStrength = v) } }
    }
}

private fun ClockStyle.title() = when (this) {
    ClockStyle.Stacked -> "Pixel stacked"
    ClockStyle.Inline -> "Android inline"
    ClockStyle.OneUi -> "One UI slim"
    ClockStyle.Ios -> "iOS bold"
    ClockStyle.Dots -> "Nothing dot matrix"
    ClockStyle.Accent -> "OxygenOS two-tone"
    ClockStyle.Hyper -> "HyperOS condensed"
    ClockStyle.Cards -> "Material cards"
    ClockStyle.Analog -> "Analog"
    ClockStyle.Words -> "In words"
    ClockStyle.Small -> "Small"
    ClockStyle.Hidden -> "No clock"
}

@Composable
private fun ClockPage(s: Settings, up: Up) {
    val colors = MaterialTheme.colorScheme
    Group {
        Box(Modifier.fillMaxWidth().height(240.dp).background(Color(0xFF15171C)).padding(20.dp), Alignment.Center) {
            FitBox(1f, Modifier.fillMaxSize(), alignX = .5f) { ClockFace(s, Color.White, 0, Alignment.Start) }
        }
    }
    Group("Style") {
        Choice("Clock style", ClockStyle.entries, s.clock, { it.title() }, onPick = { v -> up { it.copy(clock = v) } })
    }
    Group("Customize") {
        Choice("Weight", listOf<ClockWeight?>(null) + ClockWeight.entries, s.clockWeight, { it?.name ?: "Style default" }, onPick = { v -> up { it.copy(clockWeight = v) } })
        Choice("Colour", ClockTint.entries, s.clockTint, { when (it) { ClockTint.Wallpaper -> "Wallpaper colour"; ClockTint.Ink -> "White or black"; else -> "Theme ${it.name.lowercase()}" } }, onPick = { v -> up { it.copy(clockTint = v) } })
    }
    Group("Time and date") {
        Choice("Hour format", HourFormat.entries, s.hourFormat, { when (it) { HourFormat.System -> "System"; HourFormat.H12 -> "12-hour"; HourFormat.H24 -> "24-hour" } }, onPick = { v -> up { it.copy(hourFormat = v) } })
        Toggle("Leading zero", "07 instead of 7", s.leadingZero) { v -> up { it.copy(leadingZero = v) } }
        Toggle("AM / PM", null, s.showAmPm) { v -> up { it.copy(showAmPm = v) } }
        Toggle("Date", null, s.showDate) { v -> up { it.copy(showDate = v) } }
        if (s.showDate) {
            Choice("Date format", DateStyle.entries, s.dateStyle, onPick = { v -> up { it.copy(dateStyle = v) } })
            Toggle("Date above the clock", null, s.dateAbove) { v -> up { it.copy(dateAbove = v) } }
        }
        Toggle("Next alarm", null, s.showAlarm) { v -> up { it.copy(showAlarm = v) } }
        Toggle("Battery level", null, s.showBattery) { v -> up { it.copy(showBattery = v) } }
    }
}

@Composable
private fun HomePage(s: Settings, up: Up) {
    Group("Layouts") {
        var preset by remember { mutableStateOf(HomePreset.Minimal) }
        Choice("Starting layout", HomePreset.entries, preset, { it.title }, onPick = { preset = it })
        Note(preset.hint, "Moves the clock and text items. Your apps, folders and widgets stay.") {
            FilledTonalButton({ up { it.copy(grid = applyPreset(gridOf(it), preset, it.homeColumns, it.homeRows)) } }) { Text("Apply layout") }
        }
    }
    Group("Grid") {
        Range("Columns", s.homeColumns.toFloat(), 4f..7f, 2, { "${it.toInt()}" }) { v -> up { it.copy(homeColumns = v.toInt()) } }
        Range("Rows", s.homeRows.toFloat(), 5f..9f, 3, { "${it.toInt()}" }) { v -> up { it.copy(homeRows = v.toInt()) } }
        Toggle("App labels", null, s.homeLabels) { v -> up { it.copy(homeLabels = v) } }
        Toggle("Lock layout", "Stops items being moved by accident", s.lockLayout) { v -> up { it.copy(lockLayout = v) } }
        Toggle("Hide status bar", "Swipe down from the edge to see it", s.hideStatusBar) { v -> up { it.copy(hideStatusBar = v) } }
        if (s.dock.isNotEmpty()) Toggle("Dock", "Your fixed row of apps", s.showDock) { v -> up { it.copy(showDock = v) } }
    }
    Group("Intention") {
        RowSurface {
            var text by remember { mutableStateOf(s.intention) }
            OutlinedTextField(
                text, { text = it; up { c -> c.copy(intention = it) } }, Modifier.fillMaxWidth(),
                label = { Text("Today's intention") }, placeholder = { Text("The one thing that matters today") }, maxLines = 2,
            )
        }
    }
    Group("Pinned apps") {
        Choice("Align", HomeAlign.entries, s.align, onPick = { v -> up { it.copy(align = v) } })
        Choice("Show as", FavStyle.entries, s.favStyle, { when (it) { FavStyle.Text -> "Text"; FavStyle.IconText -> "Both"; FavStyle.Icons -> "Icons" } }, onPick = { v -> up { it.copy(favStyle = v) } })
        Note("Size follows the area", "Hold the pinned apps on your home screen and pull a corner.")
    }
    PinnedOrder(s, up)
    Group("Layout") {
        Note("Start over", "Puts everything back in its default place.") {
            FilledTonalButton({ up { it.copy(grid = null, homePages = 1, homeMain = 0) } }) { Text("Reset home screen") }
        }
    }
}

@Composable
private fun PinnedOrder(s: Settings, up: Up) {
    val app = LocalContext.current.applicationContext as ZpicApp
    val apps by app.apps.apps.collectAsState()
    val pinned = s.favorites.mapNotNull { k -> apps.firstOrNull { it.key == k } }
    if (pinned.isEmpty()) return
    Group("Pinned order") {
        pinned.forEachIndexed { i, a ->
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceBright).padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.label(s), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton({ up { it.copy(favorites = it.favorites.swap(i, i - 1)) } }, enabled = i > 0) { Icon(Icons.Outlined.ArrowUpward, "Move up") }
                    IconButton({ up { it.copy(favorites = it.favorites.swap(i, i + 1)) } }, enabled = i < pinned.lastIndex) { Icon(Icons.Outlined.ArrowDownward, "Move down") }
                    IconButton({ up { it.copy(favorites = it.favorites - a.key) } }) { Icon(Icons.Outlined.Close, "Unpin") }
                }
            }
        }
    }
}

private fun <T> List<T>.swap(a: Int, b: Int) = toMutableList().also {
    if (a in it.indices && b in it.indices) { val t = it[a]; it[a] = it[b]; it[b] = t }
}

@Composable
private fun AppsPage(s: Settings, up: Up) {
    val context = LocalContext.current
    Group("Layout") {
        Choice("App page", DrawerMode.entries, s.drawerMode, {
            when (it) {
                DrawerMode.Text -> "Names only, A to Z"
                DrawerMode.Icons -> "Icons with labels"
                DrawerMode.Custom -> "Custom pages: drag apps anywhere"
                DrawerMode.Groups -> "Smart groups"
            }
        }, onPick = { v -> up { it.copy(drawerMode = v) } })
        if (s.drawerMode == DrawerMode.Text || s.drawerMode == DrawerMode.Icons) {
            Choice("A to Z rail", RailSide.entries, s.rail, { when (it) { RailSide.End -> "Right"; RailSide.Start -> "Left"; RailSide.Off -> "Off" } }, onPick = { v -> up { it.copy(rail = v) } })
        }
        if (s.drawerMode == DrawerMode.Groups) Note("Grouped by what apps are for", "Always in the same order. Long-press an app to move it to another group.")
        if (s.drawerMode == DrawerMode.Custom) Note("Hold and drag an app to any cell", "Hold at the edge to change page.") {
            FilledTonalButton({ up { it.copy(customSlots = emptyMap()) } }) { Text("Sort A to Z") }
        }
    }
    Group("Size") {
        if (s.drawerMode == DrawerMode.Text) {
            Range("Text size", s.drawerTextSize.toFloat(), 16f..34f, 17, { "${it.toInt()}" }) { v -> up { it.copy(drawerTextSize = v.toInt()) } }
        } else {
            Range("Columns", s.columns.toFloat(), 3f..6f, 2, { "${it.toInt()}" }) { v -> up { it.copy(columns = v.toInt()) } }
            if (s.drawerMode == DrawerMode.Custom) Range("Rows", s.customRows.toFloat(), 4f..8f, 3, { "${it.toInt()}" }) { v -> up { it.copy(customRows = v.toInt()) } }
            Range("Icon size", s.iconSize.toFloat(), 40f..72f, 15, { "${it.toInt()} dp" }) { v -> up { it.copy(iconSize = v.toInt()) } }
            Toggle("Labels", null, s.showLabels) { v -> up { it.copy(showLabels = v) } }
        }
        Range("Background opacity", s.drawerOpacity, .5f..1f, format = { "${(it * 100).toInt()}%" }) { v -> up { it.copy(drawerOpacity = v) } }
    }
    Group("Recent") {
        Toggle("Show recent apps", "On their own card, with how long ago", s.suggestions) { v -> up { it.copy(suggestions = v) } }
        if (s.suggestions) {
            Toggle("Include apps opened elsewhere", "Also counts apps you opened outside Zpic", s.recentsFromSystem) { v -> up { it.copy(recentsFromSystem = v) } }
            if (s.recentsFromSystem && !hasUsageAccess(context)) Note("Needs usage access") {
                FilledTonalButton({ SystemActions.start(context, Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Allow usage access") }
            }
        }
    }
    Group("Search") {
        Toggle("Open keyboard with apps", "Start typing as soon as the page opens", s.autoKeyboard) { v -> up { it.copy(autoKeyboard = v) } }
        Toggle("Open the only match", "Launch as soon as search finds one app", s.autoLaunch) { v -> up { it.copy(autoLaunch = v) } }
    }
}

@Composable
private fun IconsPage(s: Settings, up: Up) {
    val app = LocalContext.current.applicationContext as ZpicApp
    val apps by app.apps.apps.collectAsState()
    val colors = rememberIconColors(MaterialTheme.colorScheme.onSurface, s.glyphInk)
    Group {
        RowSurface {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                apps.take(5).forEach { AppIcon(it, s, 52, colors) }
            }
        }
    }
    Group("Adaptive style") {
        Choice("Style", IconStyle.entries, s.iconStyle, {
            when (it) {
                IconStyle.System -> "Full colour"
                IconStyle.Tonal -> "Tonal"
                IconStyle.Surface -> "Surface"
                IconStyle.Ghost -> "Glyph only"
                IconStyle.Monogram -> "Monogram"
            }
        }, hint = null, onPick = { v -> up { it.copy(iconStyle = v) } })
        if (s.iconStyle == IconStyle.Ghost) Choice("Glyph colour", GlyphInk.entries, s.glyphInk, { if (it == GlyphInk.Theme) "Match theme" else "White" }, onPick = { v -> up { it.copy(glyphInk = v) } })
        Choice("Shape", IconShape.entries, s.iconShape, onPick = { v -> up { it.copy(iconShape = v) } })
    }
    val packs = remember { app.icons.installedPacks() }
    Group("Icon pack") {
        if (packs.isEmpty()) Note("No packs installed", "Any Nova-compatible pack appears here.")
        else Choice("Icon pack", listOf<String?>(null) + packs.map { it.packageName }, s.iconPack, { k -> packs.firstOrNull { it.packageName == k }?.label ?: "None" }, onPick = { v -> up { it.copy(iconPack = v) } })
    }
}

@Composable
private fun ArtImage(design: ArtDesign, spec: ArtSpec, w: Int, h: Int, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, design, spec) {
        // Holding the last image while the next renders keeps sliders from flickering.
        delay(40)
        value = withContext(Dispatchers.Default) { WallpaperArt.render(design, spec, w, h) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        bitmap?.let { Image(it.asImageBitmap(), design.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
private fun ArtSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value, onChange, valueRange = range)
    }
}

@Composable
private fun ArtEditor(design: ArtDesign, target: WallpaperTarget, onTarget: (WallpaperTarget) -> Unit, onDismiss: () -> Unit, onSystem: (ArtSpec) -> Unit, onApply: (ArtSpec) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var spec by remember(design) { mutableStateOf(ArtSpec()) }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${design.family} · ${design.name}", style = MaterialTheme.typography.titleLarge)
            ArtImage(design, spec, 360, 760, Modifier.align(Alignment.CenterHorizontally).size(width = 150.dp, height = 316.dp).clip(RoundedCornerShape(28.dp)))
            Text("Colour", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(artSwatches) { hue ->
                    val on = kotlin.math.abs(spec.hue - hue) < 1f
                    val swatch = Color(ColorUtils.HSLToColor(floatArrayOf(hue, .6f, .5f)))
                    Box(Modifier.size(44.dp).clip(CircleShape).background(swatch).clickable { spec = spec.copy(hue = hue) }, Alignment.Center) {
                        if (on) Icon(Icons.Outlined.Check, "Selected", tint = Color.White)
                    }
                }
            }
            ArtSlider("Hue", spec.hue, 0f..360f) { spec = spec.copy(hue = it) }
            ArtSlider("Colourfulness", spec.chroma, 0f..1f) { spec = spec.copy(chroma = it) }
            ArtSlider("Second colour", spec.accentShift, 0f..180f) { spec = spec.copy(accentShift = it) }
            ArtSlider("Detail", spec.density, .5f..1.8f) { spec = spec.copy(density = it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Light version", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(spec.light, { spec = spec.copy(light = it) })
            }
            Text("Apply to", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                WallpaperTarget.entries.forEachIndexed { i, o ->
                    ToggleButton(
                        o == target, { onTarget(o) }, Modifier.weight(1f),
                        shapes = when (i) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            WallpaperTarget.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    ) { Text(o.name) }
                }
            }
            Button({ onApply(spec) }, Modifier.fillMaxWidth()) { Text("Set wallpaper") }
            TextButton({ onSystem(spec) }, Modifier.fillMaxWidth()) { Text("Set with my phone's wallpaper app") }
        }
    }
}

@Composable
private fun WallpaperPage(s: Settings, up: Up) {
    val context = LocalContext.current
    val app = context.applicationContext as ZpicApp
    val scope = rememberCoroutineScope()
    var target by rememberSaveable { mutableStateOf(WallpaperTarget.Both) }
    var busy by remember { mutableStateOf(false) }
    var separate by remember { mutableStateOf(app.wallpaper.hasSeparateLock) }
    var open by remember { mutableStateOf<ArtDesign?>(null) }

    fun run(block: suspend () -> Unit) {
        busy = true
        scope.launch {
            val result = runCatching { block() }
            busy = false
            separate = app.wallpaper.hasSeparateLock
            SystemActions.toast(context, if (result.isSuccess) "Wallpaper set" else "Couldn't set wallpaper")
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) run { app.wallpaper.applyImage(uri, effective(target, s)) }
    }
    Group {
        Choice("Photos apply to", WallpaperTarget.entries, target, { it.name }, onPick = { target = it })
        Note("Your photos") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button({ pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy) { Text("Choose a photo") }
                if (busy) LoadingIndicator(Modifier.size(40.dp))
            }
        }
    }
    artDesigns.groupBy { it.family }.forEach { (family, designs) ->
        Column {
            Text(family, Modifier.padding(start = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                items(designs, key = { it.id }) { design ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ArtImage(design, ArtSpec(), 200, 430, Modifier.size(width = 104.dp, height = 220.dp).clip(RoundedCornerShape(24.dp)).clickable(enabled = !busy) { open = design })
                        Text(design.name, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    open?.let { design ->
        ArtEditor(design, target, { target = it }, { open = null }, { spec ->
            open = null
            scope.launch {
                val intent = app.wallpaper.systemSetIntent(app.wallpaper.renderFull(design, spec))
                if (!SystemActions.start(context, intent)) SystemActions.toast(context, "Your phone has no wallpaper app for this")
            }
        }) { spec ->
            open = null
            run { app.wallpaper.applyArt(design, spec, effective(target, s)) }
        }
    }
    Group("Lock screen") {
        Toggle("Keep lock screen matched", "What's on your home screen shows on the lock screen too, even if set from another app", s.lockFollowsHome) { v ->
            up { it.copy(lockFollowsHome = v) }
            if (v) run { app.wallpaper.makeLockFollowHome() }
        }
        if (!s.lockFollowsHome && separate) Note("Lock screen has its own wallpaper") {
            TextButton({ run { app.wallpaper.makeLockFollowHome() } }, enabled = !busy) { Text("Use home wallpaper once") }
        }
    }
    Group("Adjust") {
        Range("Dim wallpaper", s.wallpaperDim, 0f..0.7f, format = { "${(it * 100).toInt()}%" }) { v -> up { it.copy(wallpaperDim = v) } }
    }
}

/** With matching on, a lock-only wallpaper would just be undone, so home is what really gets set. */
private fun effective(target: WallpaperTarget, s: Settings) =
    if (s.lockFollowsHome && target == WallpaperTarget.Lock) WallpaperTarget.Both else target

private val gestureChoices = listOf(Gesture.None, Gesture.Notifications, Gesture.Drawer, Gesture.Search, Gesture.Lock, Gesture.Settings, Gesture.App)

@Composable
private fun GesturesPage(s: Settings, up: Up) {
    val context = LocalContext.current
    Group("Home screen") {
        Note("Swipe up", "Always opens your apps")
        GestureChoice("Swipe down", s.swipeDown, s) { v -> up { it.copy(swipeDown = v) } }
        GestureChoice("Swipe left", s.swipeLeft, s) { v -> up { it.copy(swipeLeft = v) } }
        GestureChoice("Swipe right", s.swipeRight, s) { v -> up { it.copy(swipeRight = v) } }
        GestureChoice("Double-tap", s.doubleTap, s) { v -> up { it.copy(doubleTap = v) } }
    }
    val uses = listOf(s.swipeDown, s.swipeLeft, s.swipeRight, s.doubleTap).any { it.action == Gesture.Lock }
    if (uses && !SystemActions.isLockServiceOn(context)) Group("Permission") {
        Note("Locking needs one permission", "Turn on Zpic screen lock in Accessibility. It only locks the screen and reads nothing.") {
            FilledTonalButton({ SystemActions.start(context, Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Open Accessibility") }
        }
    }
}

@Composable
private fun GestureChoice(title: String, binding: GestureBinding, s: Settings, onChange: (GestureBinding) -> Unit) {
    val app = LocalContext.current.applicationContext as ZpicApp
    val apps by app.apps.apps.collectAsState()
    var picking by remember { mutableStateOf(false) }
    val appName = apps.firstOrNull { it.key == binding.app }?.label(s)
    Choice(title, gestureChoices, binding.action, {
        when (it) {
            Gesture.None -> "Nothing"
            Gesture.Notifications -> "Notifications"
            Gesture.Drawer -> "Apps"
            Gesture.Search -> "Search"
            Gesture.Lock -> "Lock"
            Gesture.Settings -> "Customize"
            Gesture.App -> if (binding.action == Gesture.App && appName != null) appName else "Open an app"
        }
    }, onPick = { v -> if (v == Gesture.App) picking = true else onChange(GestureBinding(v)) })
    if (picking) AppPicker(apps, s, { picking = false }) { onChange(GestureBinding(Gesture.App, it.key)); picking = false }
}

@Composable
private fun AppPicker(apps: List<AppEntry>, s: Settings, onDismiss: () -> Unit, onPick: (AppEntry) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
        title = { Text("Choose an app") },
        text = {
            LazyColumn(Modifier.height(360.dp)) {
                items(apps, key = { it.key }) { a ->
                    Text(a.label(s), Modifier.fillMaxWidth().clickable { onPick(a) }.padding(vertical = 12.dp))
                }
            }
        },
    )
}

@Composable
private fun FocusPage(s: Settings, up: Up) {
    val context = LocalContext.current
    val app = context.applicationContext as ZpicApp
    val apps by app.apps.apps.collectAsState()
    var chooser by remember { mutableStateOf<String?>(null) }
    Group("Pause before opening") {
        Note("A short breath before distracting apps", "You can always continue.") {
            FilledTonalButton({ chooser = "pause" }) { Text("Choose apps (${s.distracting.size})") }
        }
        Range("Length", s.pauseSeconds.toFloat(), 3f..20f, 16, { "${it.toInt()} s" }) { v -> up { it.copy(pauseSeconds = v.toInt()) } }
    }
    Group("Hidden apps") {
        Note("Gone from the list and search", "They stay installed.") {
            FilledTonalButton({ chooser = "hide" }) { Text("Choose apps (${s.hidden.size})") }
        }
    }
    Group("Notification shelf") {
        val shelf = gridOf(s).any { it.kind == HomeKind.Shelf }
        Toggle("Show on home screen", "Apps with notifications waiting sit in a small shelf you can move and resize", shelf) { v ->
            up { it.copy(grid = withShelf(gridOf(it), v, it.homeMain, it.homeColumns, it.homeRows)) }
        }
        if (shelf) Note("Leave some apps out", "${s.shelfExcluded.size} hidden from the shelf") {
            FilledTonalButton({ chooser = "shelf" }) { Text("Choose apps") }
        }
        if (shelf && !SystemActions.isDotsOn(context)) Note("Needs notification access") {
            FilledTonalButton({
                SystemActions.start(context, Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).putExtra("android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME", ComponentName(context, DotsService::class.java).flattenToString()))
            }) { Text("Allow notification access") }
        }
    }
    Group("Notifications") {
        Toggle("Notification dots", "A small dot on apps with something waiting", s.dots) { v -> up { it.copy(dots = v) } }
        if (s.dots && !SystemActions.isDotsOn(context)) Note("Needs notification access") {
            FilledTonalButton({
                SystemActions.start(context, Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).putExtra("android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME", ComponentName(context, DotsService::class.java).flattenToString()))
            }) { Text("Allow notification access") }
        }
    }
    chooser?.let { mode ->
        val picked = remember { mutableStateListOf<String>().apply { addAll(when (mode) { "pause" -> s.distracting; "shelf" -> s.shelfExcluded; else -> s.hidden }) } }
        AlertDialog(
            onDismissRequest = { chooser = null },
            title = { Text(when (mode) { "pause" -> "Pause before opening"; "shelf" -> "Leave out of the shelf"; else -> "Hidden apps" }) },
            text = {
                LazyColumn(Modifier.height(380.dp)) {
                    items(apps, key = { it.key }) { a ->
                        val on = a.key in picked
                        Row(
                            Modifier.fillMaxWidth().clickable { if (on) picked.remove(a.key) else picked.add(a.key) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(a.label(s), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal)
                            RadioButton(on, null)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    val set = picked.toSet()
                    up { when (mode) { "pause" -> it.copy(distracting = set); "shelf" -> it.copy(shelfExcluded = set); else -> it.copy(hidden = set, favorites = it.favorites - set) } }
                    chooser = null
                }) { Text("Done") }
            },
            dismissButton = { TextButton({ chooser = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BackupPage() {
    val context = LocalContext.current
    val app = context.applicationContext as ZpicApp
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching { context.contentResolver.openOutputStream(uri)!!.use { it.write(app.settings.export().toByteArray()) } }.isSuccess
            SystemActions.toast(context, if (ok) "Setup saved" else "Couldn't save")
        }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = runCatching { app.settings.import(context.contentResolver.openInputStream(uri)!!.bufferedReader().readText()) }.getOrDefault(false)
            SystemActions.toast(context, if (ok) "Setup restored" else "That file isn't a Zpic setup")
        }
    }
    Group("Your setup, in one file") {
        Note("Includes all customization, pinned and hidden apps", "Widgets need adding again.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton({ save.launch("zpic-setup.json") }) { Text("Save setup") }
                FilledTonalButton({ load.launch(arrayOf("application/json", "*/*")) }) { Text("Restore") }
            }
        }
    }
}
