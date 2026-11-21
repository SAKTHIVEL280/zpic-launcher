package com.zpic.launcher.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.service.media.MediaBrowserService
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import com.zpic.launcher.ZpicApp
import com.zpic.launcher.data.HomeCell
import com.zpic.launcher.data.HomeKind
import com.zpic.launcher.data.Settings
import com.zpic.launcher.system.DotsService
import com.zpic.launcher.system.SystemActions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.min

/*
 * Zpic's own widgets. Each one has a few designs (style) and a background (look) that can be mixed
 * freely, and every design lays itself out for the space it's given instead of being scaled to fit.
 */

enum class WidgetLook(val label: String) { Glass("Translucent"), Solid("Solid"), Clear("No background"), Accent("Accent") }
enum class MusicStyle(val label: String) { Disc("Disc"), Card("Album art"), Bar("Bar"), Minimal("Minimal") }
enum class BatteryStyle(val label: String) { Ring("Ring"), Bar("Bar"), Number("Number") }
enum class DateStyle(val label: String) { Big("Big date"), Agenda("Next event"), Month("Month") }

val HomeKind.isBuiltInWidget get() = this == HomeKind.Media || this == HomeKind.Battery || this == HomeKind.Calendar

fun HomeCell.lookOf(default: WidgetLook): WidgetLook = WidgetLook.entries.firstOrNull { it.name == look } ?: default
fun HomeCell.musicStyle() = MusicStyle.entries.firstOrNull { it.name == style } ?: MusicStyle.Disc
fun HomeCell.batteryStyle() = BatteryStyle.entries.firstOrNull { it.name == style } ?: BatteryStyle.Ring
fun HomeCell.dateStyle() = DateStyle.entries.firstOrNull { it.name == style } ?: DateStyle.Big

/** The background a widget starts with: Minimal music reads best straight on the wallpaper. */
fun HomeCell.defaultLook() = if (kind == HomeKind.Media && musicStyle() == MusicStyle.Minimal) WidgetLook.Clear else WidgetLook.Glass
fun HomeCell.currentLook() = lookOf(defaultLook())

/** Text and control colours that work on a given background. */
data class LookColors(val content: Color, val muted: Color, val onContent: Color)

fun lookColors(look: WidgetLook, ink: Color, scheme: ColorScheme): LookColors = when (look) {
    WidgetLook.Glass -> LookColors(Color.White, Color.White.copy(alpha = .72f), Color.Black)
    WidgetLook.Solid -> LookColors(scheme.onSurface, scheme.onSurfaceVariant, scheme.surface)
    WidgetLook.Clear -> LookColors(ink, ink.copy(alpha = .72f), if (ink.luminanceSimple() > .5f) Color.Black else Color.White)
    WidgetLook.Accent -> LookColors(scheme.onPrimaryContainer, scheme.onPrimaryContainer.copy(alpha = .72f), scheme.primaryContainer)
}

private fun Color.luminanceSimple() = .2126f * red + .7152f * green + .0722f * blue

/** Translucent is a smoky glass that keeps white text readable on any wallpaper, light or dark. */
fun Modifier.lookSurface(look: WidgetLook, shape: Shape, scheme: ColorScheme): Modifier = when (look) {
    WidgetLook.Glass -> clip(shape).background(Color.Black.copy(alpha = .28f)).border(1.dp, Color.White.copy(alpha = .16f), shape)
    WidgetLook.Solid -> clip(shape).background(scheme.surfaceContainerHigh)
    WidgetLook.Clear -> this
    WidgetLook.Accent -> clip(shape).background(scheme.primaryContainer)
}

/** Any built-in widget, by kind. [preview] makes it inert, for the style picker. */
@Composable
fun BuiltInWidget(cell: HomeCell, box: DpSize, ink: Color, s: Settings, preview: Boolean = false) {
    when (cell.kind) {
        HomeKind.Media -> MusicWidget(cell, box, ink, s, preview)
        HomeKind.Battery -> BatteryWidget(cell, ink)
        HomeKind.Calendar -> DateWidget(cell, ink, preview)
        else -> Unit
    }
}

// ---------------------------------------------------------------- Music

private data class NowPlaying(val controller: MediaController, val pkg: String, val title: String, val artist: String, val playing: Boolean, val art: ImageBitmap?)
private data class MusicState(val access: Boolean, val now: NowPlaying?)

private fun hasAccess(context: Context) = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/** What's playing, refreshed every second or two, and straight away when [refresh] changes. */
@Composable
private fun rememberMusic(refresh: Int): MusicState {
    val context = LocalContext.current
    val state by produceState(MusicState(hasAccess(context), null), refresh) {
        if (refresh > 0) delay(350)
        var lastKey: String? = null
        var lastArt: ImageBitmap? = null
        while (true) {
            val access = hasAccess(context)
            val now = if (!access) null else runCatching {
                val sessions = context.getSystemService(MediaSessionManager::class.java).getActiveSessions(ComponentName(context, DotsService::class.java))
                val active = setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING)
                // Only real media counts: something playing, or a paused session that still knows its track.
                // Apps that hold an empty session (assistants, readers) would otherwise show up as music.
                fun titled(c: MediaController) = !c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()
                val c = sessions.firstOrNull { it.playbackState?.state in active && titled(it) }
                    ?: sessions.firstOrNull { it.playbackState?.state in active }
                    ?: sessions.firstOrNull { titled(it) && it.playbackState?.state == PlaybackState.STATE_PAUSED }
                c?.let {
                    val md = it.metadata
                    val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
                    val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                        ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
                    val key = it.packageName + title + artist
                    if (key != lastKey) {
                        lastKey = key
                        lastArt = (md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))?.let { b -> fitted(b).asImageBitmap() }
                    }
                    NowPlaying(it, it.packageName, title, artist, it.playbackState?.state in active, lastArt)
                }
            }.getOrNull()
            value = MusicState(access, now)
            delay(if (now?.playing == true) 1000 else 2000)
        }
    }
    return state
}

private fun fitted(b: Bitmap): Bitmap {
    val side = maxOf(b.width, b.height)
    if (side <= 512) return b.copy(Bitmap.Config.ARGB_8888, false) ?: b
    val f = 512f / side
    return b.copy(Bitmap.Config.ARGB_8888, false).scale((b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1))
}

/** Starting music when nothing is playing yet. */
object MusicControl {
    private const val BROWSER = "android.media.browse.MediaBrowserService"

    /** Apps that can be asked to play: they publish a media browser or accept media buttons. */
    fun musicApps(context: Context): List<String> {
        val pm = context.packageManager
        val found = pm.queryIntentServices(Intent(BROWSER), 0).map { it.serviceInfo.packageName } +
            pm.queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON), 0).map { it.activityInfo.packageName }
        return found.distinct().filter { it != context.packageName && pm.getLaunchIntentForPackage(it) != null }
            .sortedBy { label(context, it).lowercase() }
    }

    fun label(context: Context, pkg: String): String =
        runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    fun open(context: Context, pkg: String) {
        context.packageManager.getLaunchIntentForPackage(pkg)?.let { SystemActions.start(context, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /**
     * Resumes the app's most recent music, the way the system's media controls do after a restart: connect
     * to its media browser asking for recent media and press play. Apps without one get a play button press.
     */
    fun resume(context: Context, pkg: String) {
        val pm = context.packageManager
        val service = pm.queryIntentServices(Intent(BROWSER).setPackage(pkg), 0).firstOrNull()?.serviceInfo
        if (service == null) { mediaButton(context, pkg); return }
        val main = Handler(Looper.getMainLooper())
        var settled = false
        var browser: MediaBrowser? = null
        browser = MediaBrowser(
            context, ComponentName(pkg, service.name),
            object : MediaBrowser.ConnectionCallback() {
                override fun onConnected() {
                    if (settled) return
                    settled = true
                    val b = browser ?: return
                    runCatching { MediaController(context, b.sessionToken).transportControls.play() }.onFailure { mediaButton(context, pkg) }
                    main.postDelayed({ runCatching { b.disconnect() } }, 5000)
                }
                override fun onConnectionFailed() { if (!settled) { settled = true; mediaButton(context, pkg) } }
            },
            Bundle().apply { putBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT, true) },
        )
        runCatching { browser.connect() }.onFailure { settled = true; mediaButton(context, pkg) }
        main.postDelayed({ if (!settled) { settled = true; runCatching { browser.disconnect() }; mediaButton(context, pkg) } }, 2500)
    }

    private fun mediaButton(context: Context, pkg: String) {
        val receiver = context.packageManager.queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg), 0).firstOrNull()?.activityInfo
        if (receiver == null) { open(context, pkg); return }
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            runCatching {
                context.sendBroadcast(
                    Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(ComponentName(pkg, receiver.name))
                        .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY)),
                )
            }
        }
    }
}

private fun openAccess(context: Context) {
    SystemActions.start(
        context,
        Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .putExtra("android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME", ComponentName(context, DotsService::class.java).flattenToString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private class MusicUi(val title: String, val subtitle: String, val playing: Boolean, val art: ImageBitmap?, val hasTrack: Boolean)
private class MusicActions(val playPause: () -> Unit, val next: () -> Unit, val previous: () -> Unit, val open: () -> Unit)

@Composable
fun MusicWidget(cell: HomeCell, box: DpSize, ink: Color, s: Settings, preview: Boolean = false) {
    val context = LocalContext.current
    val app = context.applicationContext as ZpicApp
    val scope = rememberCoroutineScope()
    val enabled = !preview && !LocalHomeEditing.current
    var refresh by remember { mutableIntStateOf(0) }
    val music = rememberMusic(refresh)
    val latest by rememberUpdatedState(music)
    var picking by remember { mutableStateOf(false) }
    val fixed = s.musicApp
    val now = music.now
    val fixedLabel = remember(fixed) { fixed?.let { MusicControl.label(context, it) } }

    fun start(pkg: String) {
        MusicControl.resume(context, pkg)
        refresh++
        // Some apps won't start from the background. If nothing is playing a moment later, open the app instead.
        scope.launch { delay(4000); if (latest.now?.playing != true) MusicControl.open(context, pkg) }
    }

    val ui = MusicUi(
        title = now?.title?.ifBlank { null } ?: when { now != null -> MusicControl.label(context, now.pkg); !music.access -> "Music"; else -> "Nothing playing" },
        subtitle = when {
            now != null -> now.artist.ifBlank { MusicControl.label(context, now.pkg) }
            !music.access -> "Tap to allow access"
            fixedLabel != null -> "Tap play for $fixedLabel"
            else -> "Tap play to pick an app"
        },
        playing = now?.playing == true, art = now?.art, hasTrack = now != null,
    )
    val actions = MusicActions(
        playPause = {
            when {
                !music.access -> openAccess(context)
                now != null -> { now.controller.transportControls.run { if (now.playing) pause() else play() }; refresh++ }
                fixed != null -> start(fixed)
                else -> picking = true
            }
        },
        next = { now?.controller?.transportControls?.skipToNext(); refresh++ },
        previous = { now?.controller?.transportControls?.skipToPrevious(); refresh++ },
        open = {
            val pkg = now?.pkg ?: fixed
            when { !music.access -> openAccess(context); pkg != null -> MusicControl.open(context, pkg); else -> picking = true }
        },
    )
    val scheme = MaterialTheme.colorScheme
    val look = cell.currentLook()
    val colors = lookColors(look, ink, scheme)
    when (cell.musicStyle()) {
        MusicStyle.Disc -> DiscPlayer(ui, actions, look, colors, enabled)
        MusicStyle.Card -> CardPlayer(ui, actions, look, colors, enabled)
        MusicStyle.Bar -> BarPlayer(ui, actions, look, colors, enabled)
        MusicStyle.Minimal -> MinimalPlayer(ui, actions, look, colors, enabled)
    }
    if (picking) MusicAppPicker(fixed, { picking = false }) { pkg ->
        picking = false
        app.settings.update { it.copy(musicApp = pkg) }
        start(pkg)
    }
}

@Composable
private fun Modifier.tap(enabled: Boolean, onClick: () -> Unit) =
    clickable(remember { MutableInteractionSource() }, null, enabled = enabled, onClick = onClick)

/** Previous, play or pause, next. The play button is solid so it's the obvious thing to press. */
@Composable
private fun Controls(ui: MusicUi, actions: MusicActions, colors: LookColors, enabled: Boolean, size: Dp = 44.dp, modifier: Modifier = Modifier, skips: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(size * .18f)) {
        if (ui.hasTrack && skips) Icon(Icons.Outlined.SkipPrevious, "Previous", Modifier.size(size * .8f).clip(CircleShape).tap(enabled, actions.previous).padding(size * .12f), tint = colors.content)
        Box(Modifier.size(size).clip(CircleShape).background(colors.content).tap(enabled, actions.playPause), Alignment.Center) {
            Icon(if (ui.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (ui.playing) "Pause" else "Play", Modifier.size(size * .56f), tint = colors.onContent)
        }
        if (ui.hasTrack && skips) Icon(Icons.Outlined.SkipNext, "Next", Modifier.size(size * .8f).clip(CircleShape).tap(enabled, actions.next).padding(size * .12f), tint = colors.content)
    }
}

@Composable
private fun Titles(ui: MusicUi, colors: LookColors, align: TextAlign = TextAlign.Start, scale: Float = 1f, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = if (align == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(ui.title, color = colors.content, fontSize = (16 * scale).sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align)
        Text(ui.subtitle, color = colors.muted, fontSize = (13 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align)
    }
}

/** A translucent record that turns while music plays and rests where it stopped. */
@Composable
private fun Disc(ui: MusicUi, onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val turn = remember { Animatable(0f) }
    LaunchedEffect(ui.playing) {
        if (ui.playing) while (true) {
            turn.snapTo(turn.value % 360f)
            turn.animateTo(turn.value + 360f, tween(9000, easing = LinearEasing))
        }
    }
    Box(modifier.aspectRatio(1f).clip(CircleShape).tap(enabled, onClick), Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(Color.Black.copy(alpha = .55f), r)
            // Grooves, then a fixed sheen: the light stays put while the record turns under it.
            var g = .5f
            while (g < .97f) { drawCircle(Color.White.copy(alpha = .06f), r * g, style = Stroke(1.dp.toPx())); g += .055f }
            drawCircle(Brush.linearGradient(listOf(Color.White.copy(alpha = .16f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = .08f)), Offset.Zero, Offset(size.width, size.height)), r)
            drawCircle(Color.White.copy(alpha = .22f), r, style = Stroke(1.dp.toPx()))
        }
        Box(Modifier.fillMaxSize(.42f).graphicsLayer { rotationZ = turn.value }.clip(CircleShape).background(scheme.primary), Alignment.Center) {
            if (ui.art != null) Image(ui.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else {
                Icon(Icons.Outlined.MusicNote, null, Modifier.fillMaxSize(.5f), tint = scheme.onPrimary)
                // A mark off-centre, so the turning shows even without album art.
                Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(5.dp).clip(CircleShape).background(scheme.onPrimary.copy(alpha = .8f)))
            }
        }
        Box(Modifier.fillMaxSize(.06f).clip(CircleShape).background(Color.Black.copy(alpha = .7f)))
    }
}

@Composable
private fun DiscPlayer(ui: MusicUi, actions: MusicActions, look: WidgetLook, colors: LookColors, enabled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().lookSurface(look, RoundedCornerShape(28.dp), scheme).padding(12.dp)) {
        val mw = maxWidth; val mh = maxHeight
        val wide = maxWidth > maxHeight * 1.25f
        if (wide) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Disc(ui, actions.open, enabled, Modifier.size(min(mh, mw * .42f)))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Titles(ui, colors)
                Controls(ui, actions, colors, enabled, size = min(44.dp, mh * .34f), skips = mw >= 260.dp)
            }
        } else Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            val disc = min(mw, mh - 96.dp).coerceAtLeast(48.dp)
            Disc(ui, actions.open, enabled, Modifier.size(disc))
            Titles(ui, colors, TextAlign.Center)
            Controls(ui, actions, colors, enabled, size = 40.dp)
        }
    }
}

@Composable
private fun CardPlayer(ui: MusicUi, actions: MusicActions, look: WidgetLook, colors: LookColors, enabled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    val art = ui.art
    // Over album art the text is always white on a shade; without art the chosen background applies.
    val c = if (art != null || look != WidgetLook.Clear) LookColors(Color.White, Color.White.copy(alpha = .78f), Color.Black) else colors
    BoxWithConstraints(Modifier.fillMaxSize().then(if (look != WidgetLook.Clear) Modifier.clip(shape) else Modifier).tap(enabled, actions.open)) {
        // Narrow cards keep the title and just the play button; skip buttons need room to be worth it.
        val roomy = maxWidth >= 240.dp
        if (art != null) {
            Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .45f to Color.Black.copy(alpha = .1f), 1f to Color.Black.copy(alpha = .72f))))
        } else if (look != WidgetLook.Clear) {
            // No cover: a soft cover made from the theme colours, so the card still reads as album art.
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondaryContainer))))
            Icon(Icons.Outlined.MusicNote, null, Modifier.align(Alignment.Center).fillMaxSize(.42f), tint = Color.White.copy(alpha = .22f))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .5f to Color.Transparent, 1f to Color.Black.copy(alpha = .55f))))
        }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Titles(ui, c, modifier = Modifier.weight(1f).padding(end = 12.dp))
            Controls(ui, actions, c, enabled, size = 44.dp, skips = roomy)
        }
    }
}

@Composable
private fun BarPlayer(ui: MusicUi, actions: MusicActions, look: WidgetLook, colors: LookColors, enabled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val mw = maxWidth; val mh = maxHeight
        val h = min(maxHeight, 72.dp)
        Row(
            Modifier.fillMaxWidth().height(h).lookSurface(look, RoundedCornerShape(h / 2), scheme).padding(horizontal = h * .14f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(h * .7f).clip(CircleShape).background(scheme.primary).tap(enabled, actions.open), Alignment.Center) {
                if (ui.art != null) Image(ui.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Outlined.MusicNote, null, Modifier.fillMaxSize(.5f), tint = scheme.onPrimary)
            }
            Titles(ui, colors, scale = .92f, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            Controls(ui, actions, colors, enabled, size = h * .56f, skips = mw >= 260.dp)
        }
    }
}

@Composable
private fun MinimalPlayer(ui: MusicUi, actions: MusicActions, look: WidgetLook, colors: LookColors, enabled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.lookSurface(look, RoundedCornerShape(50), scheme).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (ui.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (ui.playing) "Pause" else "Play",
                Modifier.size(28.dp).clip(CircleShape).tap(enabled, actions.playPause).padding(2.dp), tint = colors.content,
            )
            Text(
                if (ui.hasTrack) "${ui.title}  ·  ${ui.subtitle}" else ui.subtitle,
                Modifier.padding(start = 8.dp).tap(enabled, actions.open), color = colors.content, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MusicAppPicker(current: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val apps = remember { MusicControl.musicApps(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Play music with") },
        text = {
            if (apps.isEmpty()) Text("No music apps found. Install one, then try again.")
            else LazyColumn {
                items(apps, key = { it }) { pkg ->
                    val icon = remember(pkg) { runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(pkg) }
                            .background(if (pkg == current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (icon != null) Image(icon, null, Modifier.size(36.dp))
                        Text(MusicControl.label(context, pkg), Modifier.padding(start = 14.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- Battery

@Composable
private fun rememberBattery(): Pair<Int, Boolean> {
    val context = LocalContext.current
    var state by remember { mutableStateOf(-1 to false) }
    DisposableEffect(Unit) {
        fun read(i: Intent?) {
            i ?: return
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            state = (if (level >= 0 && scale > 0) level * 100 / scale else -1) to (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
        }
        val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) = read(i) }
        read(ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return state
}

@Composable
fun BatteryWidget(cell: HomeCell, ink: Color) {
    val scheme = MaterialTheme.colorScheme
    val (level, charging) = rememberBattery()
    val look = cell.currentLook()
    val colors = lookColors(look, ink, scheme)
    val pct = if (level < 0) "–" else "$level%"
    val fill = (level.coerceAtLeast(0) / 100f)
    val low = level in 0..15 && !charging
    val bar = if (low) Color(0xFFFF6B5E) else colors.content
    // The bar is a slim card centred in its space; the ring and number fill theirs.
    val slim = cell.batteryStyle() == BatteryStyle.Bar
    BoxWithConstraints(Modifier.fillMaxSize().then(if (slim) Modifier else Modifier.lookSurface(look, RoundedCornerShape(28.dp), scheme)).padding(if (slim) 0.dp else 14.dp), contentAlignment = Alignment.Center) {
        val mw = maxWidth; val mh = maxHeight
        val side = min(maxWidth, maxHeight)
        val density = LocalDensity.current
        when (cell.batteryStyle()) {
            BatteryStyle.Ring -> Box(Modifier.size(side), Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.minDimension * .09f
                    val inset = w / 2
                    drawArc(colors.content.copy(alpha = .18f), 0f, 360f, false, Offset(inset, inset), Size(size.width - w, size.height - w), style = Stroke(w))
                    drawArc(bar, -90f, 360f * fill, false, Offset(inset, inset), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (charging) Icon(Icons.Outlined.Bolt, "Charging", Modifier.size(side * .14f), tint = colors.content)
                    Text(pct, color = colors.content, fontSize = with(density) { (side * .2f).toSp() }, fontWeight = FontWeight.Medium)
                }
            }
            BatteryStyle.Bar -> Column(Modifier.fillMaxWidth().lookSurface(look, RoundedCornerShape(24.dp), scheme).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (charging) "Charging" else "Battery", Modifier.weight(1f), color = colors.muted, fontSize = 14.sp)
                    if (charging) Icon(Icons.Outlined.Bolt, null, Modifier.size(18.dp), tint = colors.content)
                    Text(pct, color = colors.content, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
                Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(colors.content.copy(alpha = .18f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fill).clip(CircleShape).background(bar))
                }
            }
            BatteryStyle.Number -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(pct, color = bar, fontSize = with(density) { (side * .38f).toSp() }, fontWeight = FontWeight.Light, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (charging) Icon(Icons.Outlined.Bolt, null, Modifier.size(16.dp), tint = colors.muted)
                    Text(if (charging) "Charging" else "Battery", color = colors.muted, fontSize = 13.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Date

@Composable
fun DateWidget(cell: HomeCell, ink: Color, preview: Boolean = false) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val look = cell.currentLook()
    val colors = lookColors(look, ink, scheme)
    val today by produceState(Calendar.getInstance()) { while (true) { value = Calendar.getInstance(); delay(60_000) } }
    var asked by remember { mutableIntStateOf(0) }
    val event by produceState<String?>(null, today.get(Calendar.MINUTE), asked) { value = nextEvent(context) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked++ }
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val enabled = !preview && !LocalHomeEditing.current
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val date = today.time
    BoxWithConstraints(Modifier.fillMaxSize().lookSurface(look, RoundedCornerShape(28.dp), scheme).padding(16.dp)) {
        val mw = maxWidth; val mh = maxHeight
        val density = LocalDensity.current
        when (cell.dateStyle()) {
            DateStyle.Big -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Text(SimpleDateFormat("EEEE", locale).format(date), color = scheme.primary.takeIf { look == WidgetLook.Solid } ?: colors.muted, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    "${today.get(Calendar.DAY_OF_MONTH)}", color = colors.content, fontWeight = FontWeight.Light, maxLines = 1,
                    fontSize = with(density) { min(mh * .5f, mw * .5f).toSp() },
                )
                Text(SimpleDateFormat("MMMM yyyy", locale).format(date), color = colors.muted, fontSize = 14.sp)
            }
            DateStyle.Agenda -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Text(SimpleDateFormat("EEE, MMM d", locale).format(date), color = colors.content, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 3.dp, height = 32.dp).clip(CircleShape).background(colors.content.copy(alpha = .6f)))
                    Text(
                        when { !granted -> "Tap to show your events"; event != null -> event!!; else -> "Nothing else today" },
                        Modifier.padding(start = 10.dp).then(if (!granted) Modifier.tap(enabled) { ask.launch(Manifest.permission.READ_CALENDAR) } else Modifier),
                        color = colors.muted, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            DateStyle.Month -> MonthGrid(today, colors, scheme, mh)
        }
    }
}

@Composable
private fun MonthGrid(today: Calendar, colors: LookColors, scheme: ColorScheme, height: Dp) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val first = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val offset = (first.get(Calendar.DAY_OF_WEEK) - first.firstDayOfWeek + 7) % 7
    val days = today.getActualMaximum(Calendar.DAY_OF_MONTH)
    val rows = (offset + days + 6) / 7
    val day = today.get(Calendar.DAY_OF_MONTH)
    val names = (0 until 7).map { i ->
        val c = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, (first.firstDayOfWeek - 1 + i) % 7 + 1) }
        SimpleDateFormat("EEEEE", locale).format(c.time)
    }
    val font = ((height.value - 28f) / (rows + 1.6f) * .5f).coerceIn(8f, 15f).sp
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        Text(SimpleDateFormat("MMMM", locale).format(today.time), color = colors.content, fontSize = font * 1.2f, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth()) { names.forEach { Text(it, Modifier.weight(1f), color = colors.muted, fontSize = font, textAlign = TextAlign.Center) } }
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { c ->
                    val n = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f), Alignment.Center) {
                        if (n in 1..days) {
                            val on = n == day
                            Box(Modifier.size(font.value.dp * 2f).clip(CircleShape).background(if (on) colors.content else Color.Transparent), Alignment.Center) {
                                Text("$n", color = if (on) colors.onContent else colors.content, fontSize = font, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Style sheet

/** Pick a design and a background for a built-in widget, with live previews of each. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetStyleSheet(cell: HomeCell, s: Settings, ink: Color, onDismiss: () -> Unit, onChange: (HomeCell) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ZpicApp
    val scheme = MaterialTheme.colorScheme
    var picking by remember { mutableStateOf(false) }
    val styles: List<Pair<String, String>> = when (cell.kind) {
        HomeKind.Media -> MusicStyle.entries.map { it.name to it.label }
        HomeKind.Battery -> BatteryStyle.entries.map { it.name to it.label }
        HomeKind.Calendar -> DateStyle.entries.map { it.name to it.label }
        else -> emptyList()
    }
    val current = cell.style.ifEmpty { styles.firstOrNull()?.first.orEmpty() }
    val title = when (cell.kind) { HomeKind.Media -> "Music player"; HomeKind.Battery -> "Battery"; else -> "Date" }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(title, Modifier.padding(start = 24.dp, bottom = 16.dp), style = MaterialTheme.typography.headlineSmall)
            Text("Style", Modifier.padding(start = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall, color = scheme.primary)
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(styles, key = { it.first }) { (name, label) ->
                    val on = name == current
                    Column(Modifier.clip(RoundedCornerShape(24.dp)).clickable { onChange(cell.copy(style = name)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        // A small stand-in for the wallpaper, so translucent and clear backgrounds read as they will at home.
                        Box(
                            Modifier.size(168.dp, 120.dp).clip(RoundedCornerShape(22.dp))
                                .background(Brush.linearGradient(listOf(scheme.primary.copy(alpha = .9f), scheme.tertiary.copy(alpha = .7f), Color(0xFF15161A))))
                                .border(BorderStroke(if (on) 3.dp else 0.dp, if (on) scheme.primary else Color.Transparent), RoundedCornerShape(22.dp))
                                .clickable { onChange(cell.copy(style = name)) }.padding(8.dp),
                        ) {
                            BuiltInWidget(cell.copy(style = name), DpSize(152.dp, 104.dp), Color.White, s, preview = true)
                            Box(Modifier.matchParentSizeCompat().clickable { onChange(cell.copy(style = name)) })
                        }
                        Text(label, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge, color = if (on) scheme.primary else scheme.onSurface)
                    }
                }
            }
            Text("Background", Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall, color = scheme.primary)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val look = cell.currentLook()
                WidgetLook.entries.forEach { l ->
                    FilterChip(look == l, { onChange(cell.copy(look = l.name)) }, label = { Text(l.label, maxLines = 1) })
                }
            }
            if (cell.kind == HomeKind.Media) {
                val label = s.musicApp?.let { remember(it) { MusicControl.label(context, it) } }
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp).clickable { picking = true }.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Music app", style = MaterialTheme.typography.bodyLarge)
                        Text(label ?: "Asks the first time you tap play", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    TextButton({ picking = true }) { Text(if (label == null) "Choose" else "Change") }
                }
            }
        }
    }
    if (picking) MusicAppPicker(s.musicApp, { picking = false }) { pkg -> picking = false; app.settings.update { it.copy(musicApp = pkg) } }
}

/** A tap shield over a preview, filling the box it sits in. */
private fun Modifier.matchParentSizeCompat() = fillMaxSize()
