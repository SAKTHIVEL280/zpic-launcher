package com.zpic.launcher.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zpic.launcher.ZpicApp
import com.zpic.launcher.data.AppEntry
import com.zpic.launcher.data.ClockStyle
import com.zpic.launcher.data.HomeAlign
import com.zpic.launcher.data.HomeCell
import com.zpic.launcher.data.HomeKind
import com.zpic.launcher.data.Settings
import com.zpic.launcher.data.addApps
import com.zpic.launcher.data.addFolder
import com.zpic.launcher.data.drop
import com.zpic.launcher.data.gridOf
import com.zpic.launcher.data.label
import com.zpic.launcher.data.removePage
import com.zpic.launcher.data.resizedCell
import com.zpic.launcher.data.withoutFromFolder
import com.zpic.launcher.icons.IconColors
import com.zpic.launcher.system.SystemActions
import com.zpic.launcher.system.WaitingApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/** A widget host whose views report a long press, so a widget can be picked up like anything else. */
class ZpicWidgetHost(context: Context) : AppWidgetHost(context, 2048) {
    var onLongPress: ((Int) -> Unit)? = null
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        ZpicWidgetView(context) { onLongPress?.invoke(appWidgetId) }
}

private class ZpicWidgetView(context: Context, private val onHold: () -> Unit) : AppWidgetHostView(context) {
    private val main = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f; private var downY = 0f; private var held = false
    private val hold = Runnable { held = true; parent?.requestDisallowInterceptTouchEvent(true); onHold() }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { held = false; downX = e.x; downY = e.y; main.postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong()) }
            MotionEvent.ACTION_MOVE -> if (kotlin.math.hypot(e.x - downX, e.y - downY) > slop) main.removeCallbacks(hold)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> main.removeCallbacks(hold)
        }
        return held
    }
}

/** Scales its child to fill the space, so resizing changes the content and not just the frame around it. */
@Composable
fun FitBox(maxScale: Float = 6f, modifier: Modifier = Modifier, alignX: Float = 0f, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, c ->
        // An empty shelf draws nothing, and that has to be a valid state.
        val p = measurables.firstOrNull()?.measure(Constraints()) ?: return@Layout layout(c.maxWidth, c.maxHeight) {}
        val scale = min(min(c.maxWidth.toFloat() / p.width.coerceAtLeast(1), c.maxHeight.toFloat() / p.height.coerceAtLeast(1)), maxScale)
        layout(c.maxWidth, c.maxHeight) {
            val x = ((c.maxWidth - p.width * scale) * alignX).toInt()
            val y = ((c.maxHeight - p.height * scale) / 2f).toInt()
            p.placeWithLayer(x, y) { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) }
        }
    }
}

/** The single source of truth for what the arrange mode is doing right now. */
class HomeEditor {
    var editing by mutableStateOf(false)
    var selected by mutableStateOf<String?>(null)
    var dragged by mutableStateOf<HomeCell?>(null)
    var dragOffset by mutableStateOf(Offset.Zero)
    var resizing by mutableStateOf<HomeCell?>(null)
    var dragPage by mutableStateOf(0)
    fun open(id: String? = null) { editing = true; selected = id }
    fun close() { editing = false; selected = null; dragged = null; resizing = null }
}

@Composable
private fun WidgetCell(cell: HomeCell, box: DpSize) {
    val context = LocalContext.current
    val app = context.applicationContext as ZpicApp
    val info = remember(cell.widgetId) { AppWidgetManager.getInstance(context).getAppWidgetInfo(cell.widgetId) }
    if (info == null) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), Alignment.Center) {
            Text("Widget unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        return
    }
    AndroidView(
        factory = { c ->
            app.widgetHost.createView(c.applicationContext, cell.widgetId, info).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
        },
        update = { view ->
            view.setPadding(0, 0, 0, 0)
            val w = box.width.value.toInt(); val h = box.height.value.toInt()
            view.updateAppWidgetSize(Bundle(), w, h, w, h)
        },
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
    )
}

/** Apps with something waiting, stacked in one quiet pill. Tap an icon to go there. */
@Composable
private fun NotificationShelf(waiting: List<Pair<AppEntry, WaitingApp>>, s: Settings, colors: IconColors, onLaunch: Launch) {
    val scheme = MaterialTheme.colorScheme
    val editing = LocalHomeEditing.current
    if (waiting.isEmpty() && !editing) return
    Column(
        Modifier.clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainerHigh.copy(alpha = .86f)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (waiting.isEmpty()) Text("Shelf", color = scheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(6.dp))
        waiting.take(5).forEach { (app, w) ->
            Box {
                AppIcon(app, s, 40, colors, Modifier.clickable(remember { MutableInteractionSource() }, null, enabled = !editing) { onLaunch(app, null) })
                if (w.count > 1) Box(Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(scheme.primary), Alignment.Center) {
                    Text(if (w.count > 9) "9+" else "${w.count}", color = scheme.onPrimary, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun FolderTile(cell: HomeCell, apps: List<AppEntry>, s: Settings, ink: Color, colors: IconColors, icon: Int, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val editing = LocalHomeEditing.current
    val inside = remember(cell.apps, apps) { cell.apps.mapNotNull { k -> apps.firstOrNull { it.key == k } }.take(4) }
    val mini = (icon * .38f).toInt().coerceAtLeast(14)
    Column(
        Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null, enabled = !editing, onClick = onOpen),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(icon.dp).clip(RoundedCornerShape(icon.dp * .3f)).background(scheme.surfaceContainerHigh.copy(alpha = .86f)), Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                inside.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { row.forEach { AppIcon(it, s, mini, colors) } }
                }
            }
        }
        if (s.homeLabels) Text(cell.title, Modifier.padding(top = 4.dp), style = inkStyle(ink).copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CellContent(
    cell: HomeCell, box: DpSize, apps: List<AppEntry>, s: Settings, dots: Set<String>, waiting: List<Pair<AppEntry, WaitingApp>>, ink: Color,
    colors: IconColors, resumeTick: Int, icon: Int, onLaunch: Launch, onMenu: (AppEntry) -> Unit, onFolder: (HomeCell) -> Unit,
) {
    val editing = LocalHomeEditing.current
    when (cell.kind) {
        HomeKind.Clock -> {
            // A wide cell reads better on one line, so the clock reflows instead of floating in space.
            val wide = box.width > box.height * 1.4f
            val face = when {
                wide && s.clock == ClockStyle.Stacked -> s.copy(clock = ClockStyle.Inline)
                wide && s.clock == ClockStyle.Analog -> s.copy(showDate = false, showAlarm = false, showBattery = false)
                else -> s
            }
            FitBox(alignX = alignOf(s)) { ClockFace(face, ink, resumeTick, Alignment.Start) }
        }
        HomeKind.Intention -> if (s.intention.isNotBlank()) FitBox(2f, alignX = alignOf(s)) { Intention(s, ink) }
        else if (editing) Text("Today's intention", color = ink.copy(alpha = .7f), fontSize = 13.sp)
        HomeKind.Pins -> PinnedApps(apps, s, dots, ink, colors, box, onLaunch)
        HomeKind.Widget -> WidgetCell(cell, box)
        HomeKind.App -> apps.firstOrNull { it.key == cell.app }?.let { a -> SingleApp(a, s, s.dots && a.packageName in dots, ink, colors, icon, onLaunch) }
        HomeKind.Folder -> FolderTile(cell, apps, s, ink, colors, icon) { onFolder(cell) }
        HomeKind.Shelf -> FitBox(2f, alignX = .5f) { NotificationShelf(waiting, s, colors, onLaunch) }
    }
}

private fun alignOf(s: Settings) = when (s.align) {
    HomeAlign.Start -> 0f
    HomeAlign.Center -> .5f
    HomeAlign.End -> 1f
}

private val corners = listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)

@Composable
private fun BoxScope.ResizeHandles(
    saved: HomeCell, items: List<HomeCell>, cw: Float, ch: Float, cols: Int, rows: Int, editor: HomeEditor, onCommit: (HomeCell) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val latestItems by rememberUpdatedState(items)
    val latestCell by rememberUpdatedState(saved)
    corners.forEach { (sx, sy) ->
        Box(
            Modifier.align(
                when {
                    sx < 0 && sy < 0 -> Alignment.TopStart
                    sx > 0 && sy < 0 -> Alignment.TopEnd
                    sx < 0 -> Alignment.BottomStart
                    else -> Alignment.BottomEnd
                },
            ).offset(x = (sx * 6).dp, y = (sy * 6).dp).size(36.dp)
                .pointerInput(saved.id, sx, sy, cw, ch) {
                    var dx = 0f; var dy = 0f
                    var base = latestCell
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f; base = latestCell; editor.resizing = latestCell; haptics?.tick() },
                        onDragEnd = { editor.resizing?.let(onCommit); editor.resizing = null; haptics?.confirm() },
                        onDragCancel = { editor.resizing = null },
                    ) { change, delta ->
                        change.consume()
                        dx += delta.x; dy += delta.y
                        val next = resizedCell(latestItems, base, (dx / cw).roundToInt(), (dy / ch).roundToInt(), sx, sy, cols, rows)
                        if (next != null && next != editor.resizing) { if (editor.resizing != null) haptics?.tick(); editor.resizing = next }
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Spacer(Modifier.size(16.dp).clip(CircleShape).background(scheme.primary).border(2.dp, Color.White, CircleShape)) }
    }
}

@Composable
private fun ContextPill(actions: List<Triple<ImageVector, String, () -> Unit>>) {
    val haptics = LocalHaptics.current
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, shadowElevation = 8.dp) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            actions.forEach { (icon, label, run) ->
                Row(
                    Modifier.clip(CircleShape).clickable { haptics?.tick(); run() }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.size(6.dp))
                    Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PageCanvas(
    page: Int, items: List<HomeCell>, editor: HomeEditor, apps: List<AppEntry>, s: Settings, dots: Set<String>, waiting: List<Pair<AppEntry, WaitingApp>>,
    ink: Color, colors: IconColors, resumeTick: Int, onLaunch: Launch, onMenu: (AppEntry) -> Unit, onFolder: (HomeCell) -> Unit,
    onCommit: (HomeCell) -> Unit, onDrop: (HomeCell) -> Unit, pillFor: (HomeCell) -> List<Triple<ImageVector, String, () -> Unit>>,
) {
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cwPx = constraints.maxWidth.toFloat() / s.homeColumns
        val chPx = constraints.maxHeight.toFloat() / s.homeRows
        val cw = with(density) { cwPx.toDp() }; val ch = with(density) { chPx.toDp() }
        val live = editor.dragged
        if (editor.editing) Canvas(Modifier.fillMaxSize()) {
            val dot = Color.White.copy(alpha = if (live != null) .55f else .22f)
            for (c in 1 until s.homeColumns) for (r in 1 until s.homeRows) drawCircle(dot, 1.5.dp.toPx(), Offset(c * cwPx, r * chPx))
        }
        // Where the item will land, shown while dragging so a drop is never a surprise.
        if (live != null && editor.dragPage == page) {
            val wantC = ((live.col * cwPx + editor.dragOffset.x) / cwPx).roundToInt()
            val wantR = ((live.row * chPx + editor.dragOffset.y) / chPx).roundToInt()
            // Asks the real drop logic, so the preview can't disagree with where the item lands.
            val landed = remember(wantC, wantR, page, items) { drop(items, live.copy(page = page, col = wantC, row = wantR), s.homeColumns, s.homeRows).firstOrNull { it.id == live.id } }
            val tc = landed?.col ?: wantC.coerceIn(0, s.homeColumns - live.spanX)
            val tr = landed?.row ?: wantR.coerceIn(0, s.homeRows - live.spanY)
            val at by animateIntOffsetAsState(IntOffset((tc * cwPx).roundToInt(), (tr * chPx).roundToInt()), motion.settle(), label = "target")
            Box(Modifier.offset { at }.size(cw * live.spanX, ch * live.spanY).padding(4.dp).clip(RoundedCornerShape(20.dp)).background(scheme.primaryContainer.copy(alpha = .4f)))
        }
        items.filter { it.page == page }.forEach { base ->
            val cell = if (editor.resizing?.id == base.id) editor.resizing!! else base
            val selected = editor.selected == base.id
            val isDragged = live?.id == base.id
            // Items sit exactly in their cells. Animating them here is what made the page bounce on every move.
            val at = IntOffset((cell.col * cwPx).roundToInt(), (cell.row * chPx).roundToInt())
            val size = with(density) { DpSize((cell.spanX * cwPx).toDp(), (cell.spanY * chPx).toDp()) }
            val grabbed by rememberUpdatedState(base)
            Box(
                Modifier.offset { at }.size(size)
                    .graphicsLayer { alpha = if (isDragged) 0f else 1f }
                    .then(
                        if (s.lockLayout) Modifier else Modifier.pointerInput(base.id) {
                            var moved = false
                            var mine = false
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    // Already arranging: the overlay owns this touch, and two owners would double every move.
                                    mine = !editor.editing
                                    if (!mine) return@detectDragGesturesAfterLongPress
                                    haptics?.longPress(); moved = false
                                    editor.open(grabbed.id); editor.dragged = grabbed; editor.dragOffset = Offset.Zero; editor.dragPage = grabbed.page
                                },
                                onDragEnd = {
                                    if (!mine) return@detectDragGesturesAfterLongPress
                                    val d = editor.dragged
                                    if (d != null && moved) onDrop(d.copy(page = editor.dragPage, col = ((d.col * cwPx + editor.dragOffset.x) / cwPx).roundToInt(), row = ((d.row * chPx + editor.dragOffset.y) / chPx).roundToInt()))
                                    editor.dragged = null
                                },
                                onDragCancel = { if (mine) editor.dragged = null },
                            ) { change, delta ->
                                if (!mine) return@detectDragGesturesAfterLongPress
                                change.consume()
                                if (delta.getDistance() > 0f) moved = true
                                editor.dragOffset += delta
                            }
                        },
                    ),
            ) {
                CellContent(cell, size, apps, s, dots, waiting, ink, colors, resumeTick, homeIconSize(s, cell.spanX, cell.spanY, cw, ch), onLaunch, onMenu, onFolder)
                if (editor.editing) {
                    Box(
                        Modifier.matchParentSize().clip(RoundedCornerShape(16.dp))
                            .then(if (selected) Modifier.border(2.dp, scheme.primary, RoundedCornerShape(16.dp)) else Modifier)
                            .pointerInput(base.id) { detectTapGestures { haptics?.tick(); editor.selected = if (editor.selected == base.id) null else base.id } }
                            .pointerInput(base.id, cwPx, chPx, s.lockLayout) {
                                if (!s.lockLayout) detectDragGestures(
                                    onDragStart = { editor.selected = grabbed.id; editor.dragged = grabbed; editor.dragOffset = Offset.Zero; editor.dragPage = grabbed.page; haptics?.tick() },
                                    onDragEnd = {
                                        val d = editor.dragged
                                        if (d != null) onDrop(d.copy(page = editor.dragPage, col = ((d.col * cwPx + editor.dragOffset.x) / cwPx).roundToInt(), row = ((d.row * chPx + editor.dragOffset.y) / chPx).roundToInt()))
                                        editor.dragged = null; haptics?.confirm()
                                    },
                                    onDragCancel = { editor.dragged = null },
                                ) { change, delta -> change.consume(); editor.dragOffset += delta }
                            },
                    )
                    if (selected && base.resizable && !s.lockLayout) ResizeHandles(base, items, cwPx, chPx, s.homeColumns, s.homeRows, editor, onCommit)
                }
            }
        }
        // The action pill sits by the item, above it unless it's at the top of the page.
        val sel = items.firstOrNull { it.id == editor.selected && it.page == page }
        if (sel != null && live == null) {
            val above = sel.row > 0
            val pill = with(density) { 52.dp.toPx() }
            val y = (if (above) (sel.row * chPx) - pill else (sel.row + sel.spanY) * chPx + with(density) { 8.dp.toPx() }).coerceIn(0f, (constraints.maxHeight - pill).coerceAtLeast(0f))
            Box(Modifier.fillMaxWidth().offset { IntOffset(0, y.roundToInt()) }, Alignment.Center) { ContextPill(pillFor(sel)) }
        }
    }
}

private fun homeIconSize(s: Settings, spanX: Int, spanY: Int, cw: Dp, ch: Dp): Int {
    val w = cw.value * spanX; val h = ch.value * spanY
    return min(s.iconSize.toFloat(), min(w * .72f, (h - if (s.homeLabels) 24f else 0f) * .9f)).toInt().coerceAtLeast(24)
}

/** One flat action, like the row a phone shows under its own home screen. */
@Composable
private fun EditAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val motion = LocalMotion.current
    val press by animateFloatAsState(if (pressed) .88f else 1f, motion.settle(), label = "action")
    Column(
        modifier.graphicsLayer { scaleX = press; scaleY = press }.clickable(source, null) { haptics?.tick(); onClick() }.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = .16f)), Alignment.Center) {
            Icon(icon, null, Modifier.size(24.dp), tint = Color.White)
        }
        Text(label, Modifier.padding(top = 6.dp), fontSize = 12.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PageChip(icon: ImageVector, label: String, on: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    Row(
        Modifier.clip(CircleShape).background(if (on) scheme.primaryContainer else scheme.surfaceContainerHigh.copy(alpha = .9f))
            .clickable { haptics?.tick(); onClick() }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = if (on) scheme.onPrimaryContainer else scheme.onSurface)
        Spacer(Modifier.size(6.dp))
        Text(label, fontSize = 12.sp, color = if (on) scheme.onPrimaryContainer else scheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun FolderPopup(cell: HomeCell, apps: List<AppEntry>, s: Settings, colors: IconColors, editing: Boolean, onLaunch: Launch, onRemove: (String) -> Unit, onRename: (String) -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    var renaming by remember { mutableStateOf(false) }
    val appear = entrance(.9f, 1f)
    BackHandler(onBack = onClose)
    val inside = remember(cell.apps, apps) { cell.apps.mapNotNull { k -> apps.firstOrNull { it.key == k } } }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .4f)).pointerInput(Unit) { detectTapGestures { onClose() } }.padding(24.dp),
        Alignment.Center,
    ) {
        Surface(
            Modifier.fillMaxWidth().graphicsLayer { scaleX = appear; scaleY = appear }.pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(32.dp), color = scheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(cell.title, Modifier.fillMaxWidth().clickable { renaming = true }.padding(bottom = 16.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                inside.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        row.forEach { a ->
                            Column(
                                Modifier.weight(1f).pointerInput(a.key) {
                                    detectTapGestures(onTap = { onLaunch(a, null); onClose() }, onLongPress = { haptics?.longPress(); onRemove(a.key) })
                                },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AppIcon(a, s, 52, colors)
                                Text(a.label(s), Modifier.padding(top = 4.dp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = scheme.onSurface)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Text("Hold an app to take it out", fontSize = 12.sp, color = scheme.onSurfaceVariant)
            }
        }
    }
    if (renaming) RenameDialog(cell.title, { renaming = false }) { onRename(it); renaming = false }
}

@Composable
fun RenameDialog(title: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Folder name") },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton({ onSave(text.trim().ifEmpty { "Folder" }) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun HomePages(
    apps: List<AppEntry>, s: Settings, dots: Set<String>, waiting: List<WaitingApp>, ink: Color, iconColors: IconColors, resumeTick: Int,
    home: Int, editor: HomeEditor, onLaunch: Launch, onMenu: (AppEntry) -> Unit,
    onAddWidget: (Int) -> Unit, onWallpaper: () -> Unit, onCustomize: () -> Unit, onSettingsPage: (String) -> Unit, onConfigure: (HomeCell) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as ZpicApp
    val repo = app.settings
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val items = remember(s.grid, s.widgetId, s.homeColumns, s.homeRows) { gridOf(s) }
    val pages = (items.maxOfOrNull { it.page + 1 } ?: 1).coerceAtLeast(s.homePages)
    val pager = rememberPagerState(initialPage = s.homeMain.coerceIn(0, pages - 1)) { pages }
    fun save(next: List<HomeCell>) = repo.update { it.copy(grid = next) }
    LaunchedEffect(home) { if (home > 0) pager.animateScrollToPage(s.homeMain.coerceIn(0, pages - 1), animationSpec = motion.settle()) }
    BackHandler(editor.editing) { editor.close() }

    var folder by remember { mutableStateOf<String?>(null) }
    var addSheet by remember { mutableStateOf(false) }
    var dockSheet by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<HomeCell?>(null) }
    var undo by remember { mutableStateOf<HomeCell?>(null) }
    var undoTick by remember { mutableStateOf(0) }
    val current = rememberUpdatedState(items)
    val shown = remember(waiting, apps, s.shelfExcluded) {
        waiting.mapNotNull { w -> apps.firstOrNull { it.packageName == w.packageName && it.key !in s.shelfExcluded }?.let { it to w } }
    }
    val zoom by animateFloatAsState(if (editor.editing) .76f else 1f, motion.settle(), label = "zoom")
    var pageWidth by remember { mutableFloatStateOf(1f) }
    var pageRect by remember { mutableStateOf(Rect.Zero) }

    fun remove(c: HomeCell) {
        undo = c
        undoTick++
        save(current.value.filter { it.id != c.id })
        editor.selected = null
    }
    // A removed widget's binding is only released once undo has passed.
    LaunchedEffect(undoTick) {
        val c = undo ?: return@LaunchedEffect
        delay(5000)
        if (c.kind == HomeKind.Widget) app.widgetHost.deleteAppWidgetId(c.widgetId)
        undo = null
    }
    val pillFor = { c: HomeCell ->
        buildList<Triple<ImageVector, String, () -> Unit>> {
            when (c.kind) {
                HomeKind.Clock -> add(Triple(Icons.Outlined.Tune, "Style") { editor.close(); onSettingsPage("Clock") })
                HomeKind.Pins -> add(Triple(Icons.Outlined.Edit, "Edit") { editor.close(); onSettingsPage("Home screen") })
                HomeKind.Intention -> add(Triple(Icons.Outlined.Edit, "Edit") { editor.close(); onSettingsPage("Home screen") })
                HomeKind.Shelf -> add(Triple(Icons.Outlined.Tune, "Options") { editor.close(); onSettingsPage("Focus") })
                HomeKind.Widget -> if (AppWidgetManager.getInstance(context).getAppWidgetInfo(c.widgetId)?.configure != null) add(Triple(Icons.Outlined.Tune, "Setup") { onConfigure(c) })
                HomeKind.Folder -> add(Triple(Icons.Outlined.DriveFileRenameOutline, "Rename") { renaming = c })
                HomeKind.App -> apps.firstOrNull { it.key == c.app }?.let { a -> add(Triple(Icons.Outlined.Info, "Options") { onMenu(a) }) }
            }
            if (!s.lockLayout) add(Triple(Icons.Outlined.Delete, "Remove") { remove(c) })
        }
    }

    CompositionLocalProvider(LocalHomeEditing provides editor.editing) {
        Box(modifier.fillMaxSize()) {
        // Everything but the page is dimmed, so the page reads as a card and its edges are obvious.
        val dim by animateFloatAsState(if (editor.editing) .62f else 0f, motion.settle(), label = "dim")
        if (dim > 0f) Canvas(Modifier.fillMaxSize()) {
            val r = 28.dp.toPx() * .76f
            val hole = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addRoundRect(RoundRect(pageRect, CornerRadius(r)))
            }
            drawPath(hole, Color.Black.copy(alpha = dim))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 4.dp)) {
                BoxWithConstraints(
                    Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; transformOrigin = TransformOrigin(.5f, .36f) }
                        .onGloballyPositioned { pageRect = it.boundsInRoot() },
                ) {
                    pageWidth = constraints.maxWidth.toFloat()
                    HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = pages > 1 && editor.dragged == null && editor.resizing == null, beyondViewportPageCount = if (editor.dragged != null) pages else 1) { page ->
                        Box(
                            Modifier.fillMaxSize().then(
                                if (editor.editing) Modifier.pointerInput(Unit) { detectTapGestures { editor.selected = null } } else Modifier,
                            ),
                        ) {
                            PageCanvas(
                                page, items, editor, apps, s, dots, shown, ink, iconColors, resumeTick, onLaunch, onMenu, { folder = it.id },
                                onCommit = { c -> save(current.value.map { if (it.id == c.id) c else it }) },
                                onDrop = { c -> haptics?.confirm(); save(drop(current.value, c, s.homeColumns, s.homeRows)) },
                                pillFor = pillFor,
                            )
                        }
                    }
                    // The lifted copy floats above every page, so it can travel between them.
                    val d = editor.dragged
                    if (d != null) {
                        val cw = constraints.maxWidth.toFloat() / s.homeColumns; val ch = constraints.maxHeight.toFloat() / s.homeRows
                        val density = LocalDensity.current
                        val size = with(density) { DpSize((d.spanX * cw).toDp(), (d.spanY * ch).toDp()) }
                        val lift = entrance(1f, 1.06f)
                                                Box(
                            Modifier.offset { IntOffset((d.col * cw + editor.dragOffset.x).roundToInt(), (d.row * ch + editor.dragOffset.y).roundToInt()) }
                                .size(size).graphicsLayer { scaleX = lift; scaleY = lift; alpha = .92f },
                        ) {
                            if (d.kind == HomeKind.Widget) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(scheme.surfaceContainerHigh), Alignment.Center) {
                                Icon(Icons.Outlined.Widgets, null, tint = scheme.onSurfaceVariant)
                            } else CellContent(d, size, apps, s, dots, shown, ink, iconColors, resumeTick, homeIconSize(s, d.spanX, d.spanY, with(density) { cw.toDp() }, with(density) { ch.toDp() }), onLaunch, onMenu) {}
                        }
                    }
                }
                // Hold a lifted item at a side to carry it to the next page.
                LaunchedEffect(editor.dragged?.id) {
                    var armed = true
                    while (editor.dragged != null) {
                        delay(450)
                        val d = editor.dragged ?: break
                        val cw = pageWidth / s.homeColumns
                        val x = d.col * cw + editor.dragOffset.x + d.spanX * cw / 2
                        val dir = when { x < cw * .5f -> -1; x > pageWidth - cw * .5f -> 1; else -> 0 }
                        val to = pager.currentPage + dir
                        if (dir == 0) armed = true
                        if (dir != 0 && armed && to in 0 until pages) {
                            armed = false
                            haptics?.tick()
                            editor.dragPage = to
                            pager.animateScrollToPage(to, animationSpec = motion.settle())
                        }
                    }
                }
            }
            if (pages > 1) Row(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(pages) { i ->
                    val on = pager.currentPage == i
                    val w by animateFloatAsState(if (on) 18f else 6f, motion.settle(), label = "pip")
                    Spacer(Modifier.size(w.dp, 6.dp).clip(CircleShape).background(ink.copy(alpha = if (on) .95f else .45f)))
                }
            }
            if (s.showDock) Dock(apps, s, ink, iconColors, onLaunch, onMenu, editor.editing) { dockSheet = true }
        }
        // Arranging controls float over a page that stays exactly the same size underneath.
        AnimatedVisibility(editor.editing, Modifier.fillMaxSize(), enter = fadeIn(motion.effects()), exit = fadeOut(motion.effects())) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(220.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .62f)))))
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    PageChip(Icons.Outlined.Home, if (s.homeMain == pager.currentPage) "Main" else "Make main", s.homeMain == pager.currentPage) { repo.update { it.copy(homeMain = pager.currentPage) } }
                    PageChip(Icons.Outlined.Add, "Page") {
                        repo.update { it.copy(homePages = pages + 1) }
                        scope.launch { delay(120); pager.animateScrollToPage(pages, animationSpec = motion.settle()) }
                    }
                    if (pages > 1) PageChip(Icons.Outlined.Delete, "Page") {
                        val at = pager.currentPage
                        repo.update { it.copy(grid = removePage(gridOf(it), at, it.homeColumns, it.homeRows), homePages = pages - 1, homeMain = it.homeMain.coerceAtMost(pages - 2)) }
                    }
                    Spacer(Modifier.weight(1f))
                    Button({ haptics?.confirm(); editor.close() }) { Text("Done") }
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp).padding(bottom = 12.dp)) {
                    undo?.let { gone ->
                        Surface(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp), shape = CircleShape, color = scheme.inverseSurface) {
                            Row(Modifier.padding(start = 20.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Removed", color = scheme.inverseOnSurface, fontSize = 14.sp)
                                TextButton({ save(drop(current.value, gone, s.homeColumns, s.homeRows)); undo = null }) { Text("Undo", color = scheme.inversePrimary) }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        EditAction(Icons.Outlined.Wallpaper, "Wallpaper", Modifier.weight(1f), onWallpaper)
                        EditAction(Icons.Outlined.Widgets, "Widgets", Modifier.weight(1f)) { onAddWidget(pager.currentPage) }
                        EditAction(Icons.Outlined.AddCircleOutline, "Add", Modifier.weight(1f)) { addSheet = true }
                        EditAction(Icons.Outlined.Settings, "Settings", Modifier.weight(1f), onCustomize)
                    }
                }
            }
        }
        }
        folder?.let { id ->
            items.firstOrNull { it.id == id }?.let { cell ->
                FolderPopup(
                    cell, apps, s, iconColors, editor.editing, onLaunch,
                    onRemove = { key ->
                        val without = withoutFromFolder(current.value, id, key)
                        save(addApps(without, listOf(key), cell.page, s.homeColumns, s.homeRows))
                        if (without.none { it.id == id && it.kind == HomeKind.Folder }) folder = null
                    },
                    onRename = { t -> save(current.value.map { if (it.id == id) it.copy(title = t) else it }) },
                    onClose = { folder = null },
                )
            }
        }
        renaming?.let { c -> RenameDialog(c.title, { renaming = null }) { t -> save(current.value.map { if (it.id == c.id) it.copy(title = t) else it }); renaming = null } }
        if (addSheet) AddToHomeSheet(apps, s, items, pager.currentPage, { addSheet = false }, { dockSheet = true; addSheet = false }) { save(it); addSheet = false }
        if (dockSheet) DockSheet(apps, s, { dockSheet = false }) { keys -> repo.update { it.copy(dock = keys, showDock = true) }; dockSheet = false }
    }
}

@Composable
private fun Dock(apps: List<AppEntry>, s: Settings, ink: Color, colors: IconColors, onLaunch: Launch, onMenu: (AppEntry) -> Unit, editing: Boolean, onEdit: () -> Unit) {
    val docked = remember(apps, s.dock) { s.dock.mapNotNull { k -> apps.firstOrNull { it.key == k } } }
    if (docked.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp).graphicsLayer { alpha = if (editing) .55f else 1f }.then(if (editing) Modifier.clickable(onClick = onEdit) else Modifier), horizontalArrangement = Arrangement.SpaceEvenly) {
        docked.take(6).forEach { a ->
            Box(if (editing) Modifier else Modifier.pointerInput(a.key) { detectTapGestures(onTap = { onLaunch(a, null) }, onLongPress = { onMenu(a) }) }) { AppIcon(a, s, s.iconSize.coerceAtMost(56), colors) }
        }
    }
}
