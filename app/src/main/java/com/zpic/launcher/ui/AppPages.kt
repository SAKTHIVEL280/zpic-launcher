package com.zpic.launcher.ui

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateIntOffsetAsState
import com.zpic.launcher.ZpicApp
import com.zpic.launcher.data.AppEntry
import com.zpic.launcher.data.GroupInput
import com.zpic.launcher.data.Settings
import com.zpic.launcher.data.buildGroups
import com.zpic.launcher.data.group
import com.zpic.launcher.data.label
import com.zpic.launcher.data.moveApp
import com.zpic.launcher.data.placeApps
import com.zpic.launcher.icons.IconColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class RecentApp(val app: AppEntry, val at: Long)

fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(AppOpsManager::class.java)
    return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
}

@Composable
fun rememberRecents(apps: List<AppEntry>, s: Settings): List<RecentApp> {
    val context = LocalContext.current
    val visible = remember(apps, s.hidden) { apps.filter { it.key !in s.hidden } }
    val state by produceState(emptyList<RecentApp>(), visible, s.recents, s.recentsFromSystem) {
        value = withContext(Dispatchers.Default) {
            val seen = LinkedHashMap<String, RecentApp>()
            s.recents.forEach { r -> visible.firstOrNull { it.key == r.key }?.let { seen.putIfAbsent(it.key, RecentApp(it, r.at)) } }
            if (s.recentsFromSystem && hasUsageAccess(context)) {
                val now = System.currentTimeMillis()
                context.getSystemService(UsageStatsManager::class.java)
                    .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86_400_000L * 2, now).orEmpty()
                    .filter { it.lastTimeUsed > 0 && it.packageName != context.packageName }
                    .forEach { u ->
                        visible.firstOrNull { it.packageName == u.packageName && !it.isWork }?.let { a ->
                            val old = seen[a.key]
                            if (old == null || old.at < u.lastTimeUsed) seen[a.key] = RecentApp(a, u.lastTimeUsed)
                        }
                    }
            }
            seen.values.sortedByDescending { it.at }.take(10)
        }
    }
    return state
}

private fun ago(at: Long): String {
    val m = (System.currentTimeMillis() - at) / 60_000
    return when {
        m < 1 -> "Now"
        m < 60 -> "${m}m"
        m < 1440 -> "${m / 60}h"
        else -> "${m / 1440}d"
    }
}

/** Set apart on its own tonal sheet, so it reads as history and not as more of the list. */
@Composable
fun RecentsRow(recent: List<RecentApp>, s: Settings, colors: IconColors, onLaunch: Launch, onMenu: (AppEntry) -> Unit) {
    if (recent.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 16.dp)
            .clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainerHigh).padding(vertical = 14.dp),
    ) {
        Text("Recent", Modifier.padding(start = 18.dp, bottom = 10.dp), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(recent, key = { it.app.key }) { r ->
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
                Column(
                    Modifier.width(72.dp).animateItem().clip(RoundedCornerShape(20.dp))
                        .onGloballyPositioned { coords[0] = it }
                        .combinedClickable(source, null, onLongClick = { onMenu(r.app) }) { onLaunch(r.app, coords[0]?.toRect()) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppIcon(r.app, s, 48, colors, pressed = pressed)
                    Text(r.app.label(s), Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = scheme.onSurface)
                    Text(ago(r.at), fontSize = 10.sp, color = scheme.primary, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun GroupsPage(
    apps: List<AppEntry>, s: Settings, dots: Set<String>, colors: IconColors, pad: PaddingValues,
    header: @Composable () -> Unit, onLaunch: Launch, onMenu: (AppEntry) -> Unit,
) {
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val sorted = remember(apps, s.renamed) { apps.sortedBy { it.label(s).lowercase() } }
    val groups = remember(sorted, s.groupOverrides) { buildGroups(sorted.map { GroupInput(it.key, it.group(s)) }) }
    val closed = remember { mutableStateMapOf<String, Boolean>() }
    val motion = LocalMotion.current
    val scheme = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "header") { header() }
        items(groups, key = { it.first.name }) { (group, keys) ->
            val isClosed = closed[group.name] == true
            val turn by animateFloatAsState(if (isClosed) -90f else 0f, motion.spatial(), label = "chevron")
            Column(Modifier.animateItem().fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainerLow).animateContentSize(motion.spatial())) {
                Row(
                    Modifier.fillMaxWidth().clickable { closed[group.name] = !isClosed }.padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(group.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                    Text("${keys.size}", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Icon(Icons.Outlined.ExpandMore, if (isClosed) "Expand" else "Collapse", Modifier.padding(start = 4.dp).graphicsLayer { rotationZ = turn }, tint = scheme.onSurfaceVariant)
                }
                AnimatedVisibility(!isClosed, enter = expandVertically(motion.spatial()) + fadeIn(motion.effects()), exit = shrinkVertically(motion.spatial()) + fadeOut(motion.effects())) {
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 10.dp)) {
                        val cell = maxWidth / s.columns
                        FlowRow {
                            keys.mapNotNull(byKey::get).forEach { a ->
                                Box(Modifier.width(cell)) {
                                    GridCell(a, a.label(s), s, s.dots && a.packageName in dots, null, colors, onLaunch, onMenu)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Paged app screen like One UI's. The icon is sized from the cell, so a dense grid shrinks icons
 * rather than overlapping them, and a held app drops into whichever cell it's over.
 */
@Composable
fun CustomPages(
    apps: List<AppEntry>, s: Settings, dots: Set<String>, colors: IconColors, controller: DrawerController, top: Dp,
    onLaunch: Launch, onMenu: (AppEntry) -> Unit,
) {
    val context = LocalContext.current
    val repo = (context.applicationContext as ZpicApp).settings
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val perPage = s.columns * s.customRows
    val sorted = remember(apps, s.renamed) { apps.sortedBy { it.label(s).lowercase() } }
    val slots = remember(sorted, s.customSlots) { placeApps(sorted.map { it.key }, s.customSlots) }
    val used = (slots.values.maxOrNull() ?: 0) / perPage + 1
    var dragging by remember { mutableStateOf<String?>(null) }
    val pageCount = if (dragging != null) used + 1 else used
    val pager = rememberPagerState { pageCount }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }

    Column(Modifier.fillMaxSize().padding(top = top + 8.dp, bottom = 4.dp).pointerInput(Unit) {
        var tracker = VelocityTracker()
        detectVerticalDragGestures(
            onDragStart = { tracker = VelocityTracker() },
            onDragEnd = { controller.settle(tracker.calculateVelocity().y.let { -it }) },
            onDragCancel = { controller.settle(0f) },
        ) { change, dy ->
            tracker.addPointerInputChange(change)
            if (dy > 0 || controller.value < 1f) { change.consume(); controller.drag(-dy) }
        }
    }) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).onGloballyPositioned { gridOrigin = it.positionInRoot() }) {
            val cw = maxWidth / s.columns
            val ch = (maxHeight - 24.dp) / s.customRows
            val cwPx = with(density) { cw.toPx() }; val chPx = with(density) { ch.toPx() }
            val iconDp = minOf(s.iconSize.toFloat(), cw.value * .7f, (ch.value - if (s.showLabels) 24f else 0f) * .92f).toInt().coerceAtLeast(28)
            val onCell = { p: Offset ->
                val col = ((p.x - gridOrigin.x) / cwPx).toInt().coerceIn(0, s.columns - 1)
                val row = ((p.y - gridOrigin.y) / chPx).toInt().coerceIn(0, s.customRows - 1)
                pager.currentPage * perPage + row * s.columns + col
            }
            HorizontalPager(pager, Modifier.fillMaxWidth().height(ch * s.customRows), beyondViewportPageCount = 1, userScrollEnabled = dragging == null) { page ->
                Box(Modifier.fillMaxSize()) {
                    val target = dragging?.let { onCell(pointer) }
                    if (target != null && target / perPage == page) {
                        val local = target % perPage
                        val at by animateIntOffsetAsState(IntOffset((local % s.columns * cwPx).roundToInt(), (local / s.columns * chPx).roundToInt()), motion.fastSpatial(), label = "target")
                        Box(Modifier.offset { at }.size(cw, ch).padding(4.dp).clip(RoundedCornerShape(20.dp)).background(scheme.primaryContainer.copy(alpha = .5f)))
                    }
                    sorted.filter { (slots[it.key] ?: -1) / perPage == page }.forEach { a ->
                        key(a.key) {
                            val local = slots.getValue(a.key) % perPage
                            val at by animateIntOffsetAsState(IntOffset((local % s.columns * cwPx).roundToInt(), (local / s.columns * chPx).roundToInt()), motion.spatial(), label = "cell")
                            val lifted = dragging == a.key
                            val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
                            val source = remember { MutableInteractionSource() }
                            val pressed by source.collectIsPressedAsState()
                            val latest by rememberUpdatedState(slots)
                            var moved by remember { mutableStateOf(false) }
                            Column(
                                Modifier.offset { at }.size(cw, ch).graphicsLayer { alpha = if (lifted) 0f else 1f }
                                    .onGloballyPositioned { coords[0] = it }
                                    .pointerInput(a.key) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { o ->
                                                haptics?.longPress()
                                                dragging = a.key; moved = false
                                                pointer = (coords[0]?.positionInRoot() ?: Offset.Zero) + o
                                            },
                                            onDragEnd = {
                                                val to = onCell(pointer)
                                                val wasMoved = moved
                                                dragging = null
                                                if (!wasMoved) onMenu(a)
                                                else {
                                                    haptics?.confirm()
                                                    repo.update { it.copy(customSlots = moveApp(latest, a.key, to)) }
                                                }
                                            },
                                            // Lifting the app rebuilds the pager, which can end a held touch as a cancel.
                                            onDragCancel = { val still = !moved; dragging = null; if (still) onMenu(a) },
                                        ) { change, delta ->
                                            change.consume()
                                            pointer += delta
                                            if (delta.getDistance() > 1f) moved = true
                                        }
                                    }
                                    .clickable(source, null, enabled = dragging == null) { onLaunch(a, coords[0]?.toRect()) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Box {
                                    AppIcon(a, s, iconDp, colors, pressed = pressed)
                                    if (s.dots && a.packageName in dots) Spacer(Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(scheme.primary))
                                }
                                if (s.showLabels) Text(a.label(s), Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = scheme.onSurface)
                            }
                        }
                    }
                }
            }
            Row(Modifier.align(Alignment.BottomCenter).height(20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(pageCount) { i ->
                    val on = pager.currentPage == i
                    val w by animateFloatAsState(if (on) 18f else 6f, motion.spatial(), label = "pip")
                    Spacer(Modifier.size(w.dp, 6.dp).clip(CircleShape).background(if (on) scheme.primary else scheme.outlineVariant))
                }
            }
            val lifted = dragging?.let { k -> apps.firstOrNull { it.key == k } }
            if (lifted != null) {
                val grow = entrance(1f, 1.14f)
                val size = with(density) { iconDp.dp.toPx() }
                Box(Modifier.offset { IntOffset((pointer.x - gridOrigin.x - size / 2).roundToInt(), (pointer.y - gridOrigin.y - size / 2).roundToInt()) }.graphicsLayer { scaleX = grow; scaleY = grow }) {
                    AppIcon(lifted, s, iconDp, colors)
                }
            }
            // Holding at a screen edge turns the page, as a phone's own app screen does.
            LaunchedEffect(dragging) {
                while (dragging != null) {
                    delay(600)
                    val x = pointer.x - gridOrigin.x
                    val width = cwPx * s.columns
                    val next = when {
                        x < cwPx * .4f -> pager.currentPage - 1
                        x > width - cwPx * .4f -> pager.currentPage + 1
                        else -> null
                    }
                    if (next != null && next in 0 until pageCount) {
                        haptics?.tick()
                        scope.launch { pager.animateScrollToPage(next) }
                    }
                }
            }
        }
    }
}
