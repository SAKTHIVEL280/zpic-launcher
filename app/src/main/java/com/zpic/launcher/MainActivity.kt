package com.zpic.launcher

import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import com.zpic.launcher.data.AppAnim
import com.zpic.launcher.data.AppEntry
import com.zpic.launcher.data.HomeCell
import com.zpic.launcher.data.HomeKind
import com.zpic.launcher.data.activeMode
import com.zpic.launcher.data.addApps
import com.zpic.launcher.data.countOpen
import com.zpic.launcher.data.findFree
import com.zpic.launcher.data.gridOf
import com.zpic.launcher.data.Gesture
import com.zpic.launcher.data.GestureBinding
import com.zpic.launcher.data.RecentUse
import com.zpic.launcher.data.Settings
import com.zpic.launcher.data.label
import com.zpic.launcher.system.DotsService
import com.zpic.launcher.system.SystemActions
import com.zpic.launcher.ui.AppDrawer
import com.zpic.launcher.ui.AppMenu
import com.zpic.launcher.ui.DrawerController
import com.zpic.launcher.ui.LocalHaptics
import com.zpic.launcher.ui.HomeEditor
import com.zpic.launcher.ui.HomePages
import com.zpic.launcher.ui.WidgetSheet
import com.zpic.launcher.ui.LocalMotion
import com.zpic.launcher.ui.PauseOverlay
import com.zpic.launcher.ui.ZpicTheme
import com.zpic.launcher.ui.rememberIconColors
import com.zpic.launcher.ui.settings.SettingsActivity
import com.zpic.launcher.ui.wallpaperIsLight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Calendar
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val app get() = application as ZpicApp
    private val homePresses = MutableStateFlow(0)
    private val resumeTick = MutableStateFlow(0)
    private val stops = MutableStateFlow(0)
    private val launched = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val settings by app.settings.settings.collectAsState()
            ZpicTheme(settings) { Root(settings) }
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { app.widgetHost.startListening() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { app.widgetHost.stopListening() }
        stops.value++
    }

    override fun onResume() {
        super.onResume()
        resumeTick.value++
        app.apps.refresh()
        syncLockWallpaper()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) homePresses.value++
    }

    private fun syncLockWallpaper() {
        if (app.settings.settings.value.lockFollowsHome && app.wallpaper.hasSeparateLock) {
            app.scope.launchSafely { app.wallpaper.makeLockFollowHome() }
        }
    }

    private fun launch(entry: AppEntry, bounds: Rect?) {
        val view = window.decorView
        val options = launchOptions(view, bounds)
        launched.value++
        runCatching {
            app.apps.launcher.startMainActivity(entry.component, entry.user, bounds, options.toBundle())
            app.settings.update { s ->
                countOpen(s, entry.key, today()).copy(
                    launchCounts = s.launchCounts + (entry.key to ((s.launchCounts[entry.key] ?: 0) + 1)),
                    recents = (listOf(RecentUse(entry.key, System.currentTimeMillis())) + s.recents).take(80),
                )
            }
        }.onFailure { SystemActions.toast(this, "Couldn't open ${entry.systemLabel}") }
    }

    /**
     * The window animation is the system's. Only the phone's own launcher may drive the full shared
     * transition, so this picks the smoothest one offered to others, and the home screen moves with it.
     */
    private fun today() = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

    private fun launchOptions(view: android.view.View, b: Rect?): ActivityOptions {
        val anim = app.settings.settings.value.appAnim
        return when {
            anim == AppAnim.System -> ActivityOptions.makeBasic()
            anim == AppAnim.Rise || b == null -> ActivityOptions.makeCustomAnimation(this, R.anim.zpic_rise, R.anim.zpic_stay)
            anim == AppAnim.Reveal -> ActivityOptions.makeClipRevealAnimation(view, b.left, b.top, b.width(), b.height())
            else -> ActivityOptions.makeScaleUpAnimation(view, b.left, b.top, b.width(), b.height())
        }
    }

    private fun openSettings(route: String? = null) {
        // A clean task, so a page left open earlier can't stand in for the one asked for.
        startActivity(
            Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.ROUTE, route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
    }

    @Composable
    private fun Root(settings: Settings) {
        val allApps by app.apps.apps.collectAsState()
        val resumeForMode by resumeTick.collectAsState()
        val mode = remember(settings.modes, settings.manualMode, resumeForMode) { activeMode(settings, Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
        val apps = remember(allApps, mode) { if (mode == null) allApps else allApps.filter { it.key !in mode.hidden } }
        val waiting by DotsService.waiting.collectAsState()
        val dots = remember(waiting) { waiting.map { it.packageName }.toSet() }
        val home by homePresses.collectAsState()
        val resume by resumeTick.collectAsState()
        val stopCount by stops.collectAsState()
        val motion = LocalMotion.current
        val view = LocalView.current
        val haptics = LocalHaptics.current ?: return
        val scope = rememberCoroutineScope()
        val drawer = remember { DrawerController(scope) }
        drawer.spring = motion.spatial()
        var menu by remember { mutableStateOf<AppEntry?>(null) }
        val editor = remember { HomeEditor() }
        var widgetPage by remember { mutableIntStateOf(-1) }
        val leaving = remember { Animatable(1f) }
        val launchCount by launched.collectAsState()
        LaunchedEffect(launchCount) {
            // The home screen eases back as the app comes forward, and settles in when it returns.
            if (launchCount > 0) leaving.animateTo(1.05f, spring(1f, 500f * settings.animSpeed))
        }
        LaunchedEffect(resume) { if (leaving.value != 1f) { leaving.snapTo(.95f); leaving.animateTo(1f, motion.spatial()) } }
        DisposableEffect(Unit) {
            app.widgetHost.onLongPress = { id -> gridOf(app.settings.settings.value).firstOrNull { it.widgetId == id }?.let { haptics.longPress(); editor.open(it.id) } }
            onDispose { app.widgetHost.onLongPress = null }
        }
        var pending by remember { mutableStateOf<Pair<AppEntry, Rect?>?>(null) }
        var focusSearch by remember { mutableStateOf(false) }
        var heightPx by remember { mutableIntStateOf(1) }
        val light = wallpaperIsLight(settings.wallpaperDim)
        val ink = if (light) Color(0xFF1A1B20) else Color.White
        val iconColors = rememberIconColors(ink, settings.glyphInk, onWallpaper = true)

        LaunchedEffect(light, drawer.value > .5f) {
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
            val lightBars = if (drawer.value > .5f) !isNightTheme(settings) else light
            controller.isAppearanceLightStatusBars = lightBars
            controller.isAppearanceLightNavigationBars = lightBars
        }
        LaunchedEffect(settings.hideStatusBar) {
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (settings.hideStatusBar) controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            else controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }
        LaunchedEffect(home) { if (home > 0) { menu = null; editor.close(); pending = null; drawer.close() } }
        LaunchedEffect(stopCount) { if (stopCount > 0) { pending = null; drawer.snapClosed() } }

        val open: (AppEntry, Rect?) -> Unit = { entry, bounds ->
            haptics.confirm()
            val opens = if (settings.countsDay == today()) settings.openCounts[entry.key] ?: 0 else 0
            val overNudge = settings.openNudge > 0 && opens >= settings.openNudge
            if (entry.key in settings.distracting || overNudge) pending = entry to bounds else launch(entry, bounds)
        }
        val runGesture: (GestureBinding) -> Unit = { binding ->
            when (binding.action) {
                Gesture.None -> Unit
                Gesture.Notifications -> SystemActions.expandNotifications(this)
                Gesture.Drawer -> drawer.open()
                Gesture.Search -> { drawer.open(); focusSearch = true }
                Gesture.Lock -> if (!SystemActions.lockScreen()) {
                    SystemActions.toast(this, "Turn on Zpic screen lock to use this gesture")
                    openSettings(SettingsActivity.ROUTE_GESTURES)
                }
                Gesture.Settings -> openSettings()
                Gesture.App -> apps.firstOrNull { it.key == binding.app }?.let { open(it, null) }
            }
        }

        BackHandler(enabled = true) { }
        PredictiveBackHandler(enabled = drawer.isOpen) { events ->
            val start = drawer.value
            try {
                events.collect { e: BackEventCompat -> drawer.set(start - e.progress * .35f) }
                drawer.close()
            } catch (_: CancellationException) {
                drawer.open()
            }
        }

        Box(
            Modifier.fillMaxSize()
                .onSizeChanged { heightPx = it.height; drawer.travel = it.height * .8f }
                .background(Color.Black.copy(alpha = settings.wallpaperDim))
                .pointerInput(settings.doubleTap, editor.editing) {
                    if (!editor.editing) detectTapGestures(
                        onDoubleTap = { if (settings.doubleTap.action != Gesture.None) { haptics.tick(); runGesture(settings.doubleTap) } },
                        onLongPress = { haptics.longPress(); if (!editor.editing) editor.open(null) },
                    )
                }
                .pointerInput(settings.swipeDown, settings.swipeLeft, settings.swipeRight, editor.editing) {
                    if (editor.editing) return@pointerInput
                    var vertical: Boolean? = null
                    var dx = 0f; var dy = 0f
                    var tracker = VelocityTracker()
                    val fire = 64.dp.toPx()
                    detectDragGestures(
                        onDragStart = { vertical = null; dx = 0f; dy = 0f; tracker = VelocityTracker() },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity()
                            // Before the code warms up, touch samples get dropped and velocity reads near zero,
                            // so a clear upward swipe counts as intent unless it ended in a real flick down.
                            if (vertical == true && dy < -fire && velocity.y < 700f) drawer.open()
                            else if (vertical == true && drawer.value > 0f) drawer.settle(-velocity.y)
                            else if (vertical == true && dy > fire) runGesture(settings.swipeDown)
                            else if (vertical == false && dx > fire) runGesture(settings.swipeRight)
                            else if (vertical == false && dx < -fire) runGesture(settings.swipeLeft)
                        },
                        onDragCancel = { if (drawer.value > 0f) drawer.settle(0f) },
                        onDrag = { change, delta: Offset ->
                            change.consume()
                            tracker.addPointerInputChange(change)
                            if (vertical == null) vertical = abs(delta.y) >= abs(delta.x)
                            dx += delta.x; dy += delta.y
                            if (vertical == true && (dy < 0 || drawer.value > 0f)) drawer.drag(-delta.y)
                        },
                    )
                },
        ) {
            HomePages(
                apps, settings, dots, waiting, ink, iconColors, resume, home, mode?.page ?: -1, editor,
                onLaunch = open,
                onMenu = { haptics.longPress(); menu = it },
                onAddWidget = { widgetPage = it },
                onWallpaper = { editor.close(); openSettings(SettingsActivity.ROUTE_WALLPAPER) },
                onCustomize = { editor.close(); openSettings() },
                onSettingsPage = { openSettings(it) },
                onConfigure = { item -> runCatching { app.widgetHost.startAppWidgetConfigureActivityForResult(this@MainActivity, item.widgetId, 0, 7, null) } },
                modifier = Modifier.graphicsLayer {
                    val p = drawer.value
                    val s = (1f - p.coerceIn(0f, 1f) * .08f) * leaving.value
                    scaleX = s; scaleY = s
                    alpha = 1f - (p * 1.6f).coerceIn(0f, 1f)
                    translationY = -p * 64.dp.toPx()
                    transformOrigin = TransformOrigin(.5f, .35f)
                },
            )
            if (drawer.isShown) Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val p = drawer.value
                    // Past 1 the spring stretches the sheet from its base, so overshoot never bares the wallpaper below it.
                    translationY = (1f - p).coerceAtLeast(0f) * heightPx * .42f
                    scaleY = 1f + (p - 1f).coerceAtLeast(0f) * .04f
                    transformOrigin = TransformOrigin(.5f, 1f)
                    alpha = (p * 2.2f).coerceIn(0f, 1f)
                    val corner = (1f - p).coerceIn(0f, 1f) * 36.dp.toPx()
                    shape = RoundedCornerShape(corner, corner, 0f, 0f)
                    clip = true
                },
            ) {
                AppDrawer(
                    apps, settings, dots, drawer, haptics, open,
                    onMenu = { haptics.longPress(); menu = it },
                    onSearchWeb = { SystemActions.webSearch(this@MainActivity, it) },
                    onSettings = { openSettings() },
                    focusSearch = focusSearch,
                    onSearchFocused = { focusSearch = false },
                )
            }
            pending?.let { (entry, bounds) ->
                PauseOverlay(
                    entry.label(settings), settings.pauseSeconds, if (settings.countsDay == today()) settings.openCounts[entry.key] ?: 0 else 0,
                    onOpen = { pending = null; launch(entry, bounds) },
                    onCancel = { pending = null },
                )
            }
        }
        menu?.let { AppMenu(it, settings, app.settings, ::placeApp) { menu = null } }
        if (widgetPage >= 0) WidgetSheet(
            onDismiss = { widgetPage = -1 },
        ) { provider ->
            val page = widgetPage
            widgetPage = -1
            pendingWidget = PendingWidget(app.widgetHost.allocateAppWidgetId(), page)
            bindWidget(provider)
        }
    }

    private fun isNightTheme(s: Settings) = when (s.themeMode) {
        com.zpic.launcher.data.ThemeMode.Light -> false
        com.zpic.launcher.data.ThemeMode.Dark -> true
        else -> resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private data class PendingWidget(val id: Int, val page: Int)
    private var pendingWidget: PendingWidget? = null

    private val bindResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) configureWidget() else abandonWidget()
    }
    private val configureResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) placeWidget() else abandonWidget()
    }

    private fun bindWidget(provider: AppWidgetProviderInfo) {
        val id = pendingWidget?.id ?: return
        if (AppWidgetManager.getInstance(this).bindAppWidgetIdIfAllowed(id, provider.provider)) configureWidget()
        else bindResult.launch(
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider),
        )
    }

    private fun configureWidget() {
        val id = pendingWidget?.id ?: return
        val setup = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.configure
        if (setup == null) { placeWidget(); return }
        // Some setup screens aren't exported to launchers; the widget still works with its defaults.
        runCatching {
            configureResult.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(setup).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        }.onFailure { placeWidget() }
    }

    private fun placeWidget() {
        val w = pendingWidget ?: return
        pendingWidget = null
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(w.id)
        app.settings.update { s ->
            val cw = resources.displayMetrics.widthPixels / s.homeColumns
            val ch = resources.displayMetrics.heightPixels * .8f / s.homeRows
            val sx = kotlin.math.ceil((info?.minWidth ?: 0) / cw.toFloat()).toInt().coerceIn(2, s.homeColumns)
            val sy = kotlin.math.ceil((info?.minHeight ?: 0) / ch).toInt().coerceIn(1, s.homeRows)
            val items = gridOf(s)
            val spot = findFree(items, sx, sy, w.page, Int.MAX_VALUE / 2, s.homeColumns, s.homeRows)
            if (spot == null) s else s.copy(grid = items + HomeCell("w${w.id}", HomeKind.Widget, spot.first, spot.second, spot.third, sx, sy, widgetId = w.id))
        }
    }

    private fun abandonWidget() {
        pendingWidget?.let { app.widgetHost.deleteAppWidgetId(it.id) }
        pendingWidget = null
    }

    private fun placeApp(entry: AppEntry) {
        app.settings.update { it.copy(grid = addApps(gridOf(it), listOf(entry.key), it.homeMain, it.homeColumns, it.homeRows)) }
    }
}

private fun CoroutineScope.launchSafely(block: suspend () -> Unit) {
    launch { runCatching { block() } }
}
