package com.zpic.launcher.ui

import android.R
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zpic.launcher.ZpicApp
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.core.graphics.ColorUtils
import com.zpic.launcher.data.FontChoice
import com.zpic.launcher.data.MotionLevel
import com.zpic.launcher.data.Palette
import com.zpic.launcher.data.Settings
import com.zpic.launcher.data.ThemeMode

/** Motion in one place, so the Expressive/Standard/Reduced setting reaches every gesture and transition. */
@Immutable
class ZpicMotion(val level: MotionLevel, private val speed: Float = 1f, private val bounce: Float = .55f) {
    private val damping = 1f - bounce * .62f

    /** For things that travel: drawer, rail wave, bubble, items lifting. */
    fun <T> spatial(): SpringSpec<T> = when (level) {
        MotionLevel.Expressive -> spring(damping, 380f * speed)
        MotionLevel.Standard -> spring(.9f, 500f * speed)
        MotionLevel.Reduced -> spring(Spring.DampingRatioNoBouncy, 3000f)
    }

    /** For the home screen, where anything that overshoots reads as a bug: moves and stops. */
    fun <T> settle(): SpringSpec<T> = spring(Spring.DampingRatioNoBouncy, 700f * speed)

    /** For quick feedback on press. */
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = when (level) {
        MotionLevel.Expressive -> spring((damping - .15f).coerceAtLeast(.4f), 900f * speed)
        MotionLevel.Standard -> spring(.85f, 1000f * speed)
        MotionLevel.Reduced -> spring(Spring.DampingRatioNoBouncy, 3000f)
    }

    /** For alpha and colour, which must never overshoot. */
    fun <T> effects(): SpringSpec<T> = spring(
        Spring.DampingRatioNoBouncy,
        if (level == MotionLevel.Reduced) 3000f else 600f * speed,
    )

    val stagger: Boolean get() = level != MotionLevel.Reduced
}

/** A value that springs once from start to end as it appears. */
@Composable
fun entrance(from: Float, to: Float): Float {
    val motion = LocalMotion.current
    val value = remember { androidx.compose.animation.core.Animatable(from) }
    androidx.compose.runtime.LaunchedEffect(Unit) { value.animateTo(to, motion.settle()) }
    return value.value
}

val LocalMotion = staticCompositionLocalOf { ZpicMotion(MotionLevel.Expressive) }

/**
 * Talks to the vibrator directly. View haptics depend on the phone's touch-feedback setting and
 * some phones ignore their tick constants, which leaves a launcher feeling dead.
 */
class Haptics(context: Context, private val enabled: Boolean, private val strength: Float) {
    private val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java).defaultVibrator
    else context.getSystemService(Vibrator::class.java)
    private val rich = Build.VERSION.SDK_INT >= 30 && vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK, VibrationEffect.Composition.PRIMITIVE_TICK)

    private fun play(primitive: Int, scale: Float, fallbackMs: Long) {
        if (!enabled || !vibrator.hasVibrator()) return
        val level = (scale * strength).coerceIn(.05f, 1f)
        val effect = if (rich && Build.VERSION.SDK_INT >= 30) VibrationEffect.startComposition().addPrimitive(primitive, level).compose()
        else VibrationEffect.createOneShot(fallbackMs, if (vibrator.hasAmplitudeControl()) (level * 255).toInt().coerceAtLeast(1) else VibrationEffect.DEFAULT_AMPLITUDE)
        if (Build.VERSION.SDK_INT >= 33) vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK))
        else vibrator.vibrate(effect)
    }

    fun tick() = play(if (Build.VERSION.SDK_INT >= 30) VibrationEffect.Composition.PRIMITIVE_TICK else 0, .6f, 10)
    fun confirm() = play(if (Build.VERSION.SDK_INT >= 30) VibrationEffect.Composition.PRIMITIVE_CLICK else 0, .8f, 16)
    fun longPress() = play(if (Build.VERSION.SDK_INT >= 30) VibrationEffect.Composition.PRIMITIVE_CLICK else 0, 1f, 26)
}

val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }

private fun tone(hue: Float, sat: Float, l: Float) =
    Color(ColorUtils.HSLToColor(floatArrayOf(hue, sat, l)))

/** A compact tonal palette from a hue, for users who choose a fixed accent over wallpaper colour. */
private fun presetScheme(hue: Float, chroma: Float, dark: Boolean): ColorScheme {
    val n = chroma * 0.18f
    val p = { l: Float -> tone(hue, chroma, l) }
    val s = { l: Float -> tone(hue, chroma * 0.4f, l) }
    val t = { l: Float -> tone((hue + 60f) % 360f, chroma * 0.6f, l) }
    val g = { l: Float -> tone(hue, n, l) }
    return if (dark) darkColorScheme(
        primary = p(.80f), onPrimary = p(.20f), primaryContainer = p(.30f), onPrimaryContainer = p(.90f),
        secondary = s(.80f), onSecondary = s(.20f), secondaryContainer = s(.30f), onSecondaryContainer = s(.90f),
        tertiary = t(.80f), onTertiary = t(.20f), tertiaryContainer = t(.30f), onTertiaryContainer = t(.90f),
        background = g(.06f), onBackground = g(.90f), surface = g(.06f), onSurface = g(.90f),
        surfaceVariant = g(.28f), onSurfaceVariant = g(.80f), outline = g(.60f), outlineVariant = g(.30f),
        surfaceContainerLowest = g(.04f), surfaceContainerLow = g(.10f), surfaceContainer = g(.12f),
        surfaceContainerHigh = g(.17f), surfaceContainerHighest = g(.22f),
    ) else lightColorScheme(
        primary = p(.38f), onPrimary = Color.White, primaryContainer = p(.90f), onPrimaryContainer = p(.12f),
        secondary = s(.38f), onSecondary = Color.White, secondaryContainer = s(.90f), onSecondaryContainer = s(.12f),
        tertiary = t(.38f), onTertiary = Color.White, tertiaryContainer = t(.90f), onTertiaryContainer = t(.12f),
        background = g(.98f), onBackground = g(.10f), surface = g(.98f), onSurface = g(.10f),
        surfaceVariant = g(.90f), onSurfaceVariant = g(.30f), outline = g(.50f), outlineVariant = g(.80f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = g(.96f), surfaceContainer = g(.94f),
        surfaceContainerHigh = g(.92f), surfaceContainerHighest = g(.90f),
    )
}

@Composable
fun ZpicTheme(settings: Settings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Pure black is a dark theme by definition, so choosing it must win over a light system.
    val dark = settings.pureBlack || when (settings.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val seed = rememberWallpaperSeed(settings.wallpaperSeed)
    var scheme = when (settings.palette) {
        Palette.Wallpaper -> if (Build.VERSION.SDK_INT >= 31) systemScheme(context, dark) else presetScheme(225f, .35f, dark)
        Palette.Extracted -> when {
            seed != null -> extracted(seed, dark)
            Build.VERSION.SDK_INT >= 31 -> systemScheme(context, dark)
            else -> presetScheme(225f, .35f, dark)
        }
        Palette.Custom -> presetScheme(settings.customHue, settings.customChroma, dark)
        Palette.Slate -> presetScheme(225f, .35f, dark)
        Palette.Sage -> presetScheme(140f, .25f, dark)
        Palette.Clay -> presetScheme(20f, .45f, dark)
        Palette.Rose -> presetScheme(340f, .45f, dark)
        Palette.Iris -> presetScheme(265f, .50f, dark)
        Palette.Graphite -> presetScheme(220f, .05f, dark)
    }
    // Without these the builders leave Material's default purple, which tints every settings row.
    scheme = scheme.copy(
        surfaceBright = if (dark) scheme.surfaceContainerHighest else scheme.surfaceContainerLowest,
        surfaceDim = if (dark) scheme.surfaceContainerLowest else scheme.surfaceContainerHigh,
        surfaceTint = scheme.primary,
    )
    if (dark && settings.pureBlack) scheme = scheme.copy(
        background = Color.Black, surface = Color.Black, surfaceDim = Color.Black, surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0A0A0B), surfaceContainer = Color.Black, surfaceContainerHigh = Color(0xFF141416),
        surfaceContainerHighest = Color(0xFF1C1C1F), surfaceBright = Color(0xFF111113), surfaceVariant = Color(0xFF141416),
    )
    val family = when (settings.font) {
        FontChoice.System -> FontFamily.Default
        FontChoice.Serif -> FontFamily.Serif
        FontChoice.Mono -> FontFamily.Monospace
        FontChoice.Condensed -> FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))
    }
    val base = Typography()
    fun TextStyle.f() = copy(fontFamily = family)
    val type = base.copy(
        displayLarge = base.displayLarge.f(), displayMedium = base.displayMedium.f(), displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(), headlineMedium = base.headlineMedium.f(), headlineSmall = base.headlineSmall.f(),
        titleLarge = base.titleLarge.f(), titleMedium = base.titleMedium.f(), titleSmall = base.titleSmall.f(),
        bodyLarge = base.bodyLarge.f(), bodyMedium = base.bodyMedium.f(), bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(), labelMedium = base.labelMedium.f(), labelSmall = base.labelSmall.f(),
    )
    val motion = remember(settings.motion, settings.animSpeed, settings.animBounce) { ZpicMotion(settings.motion, settings.animSpeed, settings.animBounce) }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        typography = type,
        motionScheme = if (settings.motion == MotionLevel.Expressive) MotionScheme.expressive() else MotionScheme.standard(),
    ) {
        val haptics = remember(settings.haptics, settings.hapticStrength) { Haptics(context.applicationContext, settings.haptics, settings.hapticStrength) }
        CompositionLocalProvider(LocalMotion provides motion, LocalHaptics provides haptics, LocalWallpaperSeed provides seed, content = content)
    }
}

/**
 * Reads the accent palettes rather than Compose's dynamic scheme. That scheme uses Android 14's
 * colour-role resources, which colour tools such as ColorBlendr may leave alone, so the launcher
 * ignored them. The palettes are what those tools write, and what Material You derives roles from.
 */
@RequiresApi(31)
private fun systemScheme(context: Context, dark: Boolean): ColorScheme {
    fun c(id: Int) = Color(context.getColor(id))
    val p = mapOf(100 to R.color.system_accent1_0, 95 to R.color.system_accent1_50, 90 to R.color.system_accent1_100, 80 to R.color.system_accent1_200, 40 to R.color.system_accent1_600, 30 to R.color.system_accent1_700, 20 to R.color.system_accent1_800, 10 to R.color.system_accent1_900).mapValues { c(it.value) }
    val s = mapOf(90 to R.color.system_accent2_100, 80 to R.color.system_accent2_200, 40 to R.color.system_accent2_600, 30 to R.color.system_accent2_700, 20 to R.color.system_accent2_800, 10 to R.color.system_accent2_900).mapValues { c(it.value) }
    val t = mapOf(90 to R.color.system_accent3_100, 80 to R.color.system_accent3_200, 40 to R.color.system_accent3_600, 30 to R.color.system_accent3_700, 20 to R.color.system_accent3_800, 10 to R.color.system_accent3_900).mapValues { c(it.value) }
    val n = mapOf(99 to R.color.system_neutral1_10, 95 to R.color.system_neutral1_50, 90 to R.color.system_neutral1_100, 80 to R.color.system_neutral1_200, 20 to R.color.system_neutral1_800, 10 to R.color.system_neutral1_900, 0 to R.color.system_neutral1_1000).mapValues { c(it.value) }
    val v = mapOf(90 to R.color.system_neutral2_100, 80 to R.color.system_neutral2_200, 60 to R.color.system_neutral2_400, 50 to R.color.system_neutral2_500, 30 to R.color.system_neutral2_700).mapValues { c(it.value) }
    fun mix(a: Color, b: Color, f: Float) = lerp(a, b, f)
    return if (dark) darkColorScheme(
        primary = p[80]!!, onPrimary = p[20]!!, primaryContainer = p[30]!!, onPrimaryContainer = p[90]!!, inversePrimary = p[40]!!,
        secondary = s[80]!!, onSecondary = s[20]!!, secondaryContainer = s[30]!!, onSecondaryContainer = s[90]!!,
        tertiary = t[80]!!, onTertiary = t[20]!!, tertiaryContainer = t[30]!!, onTertiaryContainer = t[90]!!,
        background = mix(n[10]!!, n[0]!!, .4f), onBackground = n[90]!!, surface = mix(n[10]!!, n[0]!!, .4f), onSurface = n[90]!!,
        surfaceVariant = v[30]!!, onSurfaceVariant = v[80]!!, outline = v[60]!!, outlineVariant = v[30]!!,
        surfaceContainerLowest = mix(n[10]!!, n[0]!!, .7f), surfaceContainerLow = n[10]!!, surfaceContainer = mix(n[10]!!, n[20]!!, .2f),
        surfaceContainerHigh = mix(n[10]!!, n[20]!!, .55f), surfaceContainerHighest = n[20]!!,
    ) else lightColorScheme(
        primary = p[40]!!, onPrimary = p[100]!!, primaryContainer = p[90]!!, onPrimaryContainer = p[10]!!, inversePrimary = p[80]!!,
        secondary = s[40]!!, onSecondary = Color.White, secondaryContainer = s[90]!!, onSecondaryContainer = s[10]!!,
        tertiary = t[40]!!, onTertiary = Color.White, tertiaryContainer = t[90]!!, onTertiaryContainer = t[10]!!,
        background = mix(n[99]!!, n[95]!!, .3f), onBackground = n[10]!!, surface = mix(n[99]!!, n[95]!!, .3f), onSurface = n[10]!!,
        surfaceVariant = v[90]!!, onSurfaceVariant = v[30]!!, outline = v[50]!!, outlineVariant = v[80]!!,
        surfaceContainerLowest = Color.White, surfaceContainerLow = n[95]!!, surfaceContainer = mix(n[95]!!, n[90]!!, .3f),
        surfaceContainerHigh = mix(n[95]!!, n[90]!!, .6f), surfaceContainerHighest = n[90]!!,
    )
}

private fun extracted(argb: Int, dark: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    return presetScheme(hsl[0], hsl[1].coerceIn(.18f, .6f), dark)
}

/** The wallpaper's accent colour, or null if neither the phone nor Zpic knows it. */
val LocalWallpaperSeed = staticCompositionLocalOf<Int?> { null }

@Composable
fun rememberWallpaperSeed(stored: Int?): Int? {
    val context = LocalContext.current
    val app = context.applicationContext as ZpicApp
    var live by remember { mutableStateOf<Int?>(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val manager = WallpaperManager.getInstance(context)
        val read = { live = app.wallpaper.currentAccent() }
        read()
        // Some phones skip the change callback, so re-reading on every return is the reliable path.
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) read() }
        owner.lifecycle.addObserver(observer)
        val listener = WallpaperManager.OnColorsChangedListener { _, _ -> read() }
        manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose { owner.lifecycle.removeObserver(observer); manager.removeOnColorsChangedListener(listener) }
    }
    return live ?: stored
}

/** True while the home wallpaper is light enough that dark text reads better. */
@Composable
fun wallpaperIsLight(dim: Float): Boolean {
    val context = LocalContext.current
    var light by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val manager = WallpaperManager.getInstance(context)
        fun read(c: WallpaperColors?) {
            light = c != null && Build.VERSION.SDK_INT >= 31 &&
                c.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
        }
        read(runCatching { manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull())
        val listener = WallpaperManager.OnColorsChangedListener { c, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) read(c)
        }
        manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager.removeOnColorsChangedListener(listener) }
    }
    return light && dim < 0.2f
}
