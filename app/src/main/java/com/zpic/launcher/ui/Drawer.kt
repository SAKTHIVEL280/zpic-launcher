package com.zpic.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zpic.launcher.data.AppEntry
import com.zpic.launcher.data.DrawerMode
import com.zpic.launcher.data.RailSide
import com.zpic.launcher.data.Settings
import com.zpic.launcher.data.evalMath
import com.zpic.launcher.data.formatNumber
import com.zpic.launcher.data.label
import com.zpic.launcher.system.SystemActions
import com.zpic.launcher.data.rank
import com.zpic.launcher.data.sectionOf
import com.zpic.launcher.icons.IconColors
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.toSize
import android.graphics.Rect as AndroidRect

private data class AppRow(val app: AppEntry, val label: String, val letter: Char, val firstOfLetter: Boolean)

/** Slot 0 of both lists holds the suggestions and search header, so app rows start at slot 1. */
private const val FIRST_ROW = 1

typealias Launch = (AppEntry, AndroidRect?) -> Unit

@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    settings: Settings,
    dots: Set<String>,
    controller: DrawerController,
    haptics: Haptics,
    onLaunch: Launch,
    onMenu: (AppEntry) -> Unit,
    onSearchWeb: (String) -> Unit,
    onSettings: () -> Unit,
    focusSearch: Boolean,
    onSearchFocused: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // Glyph icons here sit on the drawer surface, not the wallpaper, so they take its ink.
    val iconColors = rememberIconColors(colors.onSurface, settings.glyphInk)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var touched by remember { mutableStateOf<Char?>(null) }
    val isSearching = query.isNotBlank()

    val visible = remember(apps, settings.hidden, settings.renamed) {
        apps.filter { it.key !in settings.hidden }
            .map { it to it.label(settings) }
            .sortedWith(compareBy<Pair<AppEntry, String>> { sectionOf(it.second).let { c -> if (c == '#') '\u0000' else c } }.thenBy { it.second.lowercase() })
    }
    val results = remember(visible, query, settings.launchCounts) {
        visible.mapNotNull { (a, l) ->
            rank(query, l).takeIf { it > 0 }?.let { Triple(a, l, it) }
        }.sortedWith(compareByDescending<Triple<AppEntry, String, Int>> { it.third }.thenByDescending { settings.launchCounts[it.first.key] ?: 0 })
            .map { it.first to it.second }
    }
    val rows = remember(visible, results, isSearching) {
        val source = if (isSearching) results else visible
        buildList {
            var last: Char? = null
            for ((a, l) in source) {
                val c = sectionOf(l)
                val first = c != last && !isSearching
                add(AppRow(a, l, c, first))
                last = c
            }
        }
    }
    val letters = remember(rows) { rows.filter { it.firstOfLetter }.map { it.letter } }
    val firstIndex = remember(rows) {
        buildMap { rows.forEachIndexed { i, r -> if (r.firstOfLetter) put(r.letter, i + FIRST_ROW) } }
    }
    val recent = rememberRecents(apps, settings)
    val pages = settings.drawerMode
    val listLike = pages == DrawerMode.Text
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val currentLetter by remember(rows) {
        derivedStateOf {
            val first = if (settings.drawerMode != DrawerMode.Text) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
            rows.getOrNull((first - FIRST_ROW).coerceAtLeast(0))?.letter
        }
    }

    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }
    val drawerOpen = controller.isOpen
    LaunchedEffect(drawerOpen) {
        if (drawerOpen && settings.autoKeyboard) runCatching { focus.requestFocus() }
        if (!drawerOpen) {
            query = ""
            focusManager.clearFocus()
            keyboard?.hide()
            listState.scrollToItem(0); gridState.scrollToItem(0)
        }
    }
    // One-shot: the drawer is rebuilt on every open, so a request left standing would replay each time.
    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            onSearchFocused()
            runCatching { focus.requestFocus(); keyboard?.show() }
        }
    }
    LaunchedEffect(query) { listState.scrollToItem(0); gridState.scrollToItem(0) }
    LaunchedEffect(results, isSearching) {
        if (isSearching && settings.autoLaunch && results.size == 1) onLaunch(results[0].first, null)
    }

    val nested = remember(controller) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0 && controller.value < 1f) {
                    controller.drag(-available.y)
                    return available
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    controller.drag(-available.y)
                    return available
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (controller.value < 1f) {
                    controller.settle(-available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val onTouchLetter = { c: Char? ->
        touched = c
        if (c != null) {
            haptics.tick()
            firstIndex[c]?.let { i ->
                scope.launch {
                    if (settings.drawerMode != DrawerMode.Text) gridState.scrollToItem(i) else listState.scrollToItem(i)
                }
            }
        }
        Unit
    }
    val statusPad = WindowInsets.statusBars.asPaddingValues()
    val railOn = settings.rail != RailSide.Off && !isSearching && (pages == DrawerMode.Text || pages == DrawerMode.Icons)
    val railEnd = settings.rail != RailSide.Start
    val contentPad = PaddingValues(
        top = statusPad.calculateTopPadding() + 16.dp,
        bottom = 96.dp,
        start = if (railOn && !railEnd) 44.dp else 8.dp,
        end = if (railOn && railEnd) 44.dp else 8.dp,
    )
    val fade = Modifier
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val edge = 48.dp.toPx()
            val top = statusPad.calculateTopPadding().toPx()
            drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = top, endY = top + edge), blendMode = BlendMode.DstIn)
            drawRect(Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - edge, endY = size.height), blendMode = BlendMode.DstIn)
        }

    Box(Modifier.fillMaxSize().background(colors.surface.copy(alpha = settings.drawerOpacity))) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth().nestedScroll(nested).then(fade)) {
                val progress = { controller.value }
                val header: @Composable () -> Unit = {
                    if (settings.suggestions && !isSearching) RecentsRow(recent, settings, iconColors, onLaunch, onMenu)
                    if (isSearching) evalMath(query)?.let { MathRow(formatNumber(it)) }
                    if (isSearching && results.isEmpty()) NoResults(query, onSearchWeb)
                    else if (isSearching) WebRow(query, onSearchWeb)
                }
                when {
                    !isSearching && pages == DrawerMode.Custom -> CustomPages(apps.filter { it.key !in settings.hidden }, settings, dots, iconColors, controller, statusPad.calculateTopPadding(), onLaunch, onMenu)
                    !isSearching && pages == DrawerMode.Groups -> GroupsPage(apps.filter { it.key !in settings.hidden }, settings, dots, iconColors, contentPad, header, onLaunch, onMenu)
                    listLike -> TextList(rows, listState, settings, dots, touched, contentPad, progress, onLaunch, onMenu, header)
                    else -> IconGrid(rows, gridState, settings, dots, touched, iconColors, contentPad, onLaunch, onMenu, header)
                }
                if (railOn) AlphabetRail(
                    letters, currentLetter, railEnd, onTouchLetter,
                    Modifier.align(if (railEnd) Alignment.CenterEnd else Alignment.CenterStart),
                )
            }
            SearchBar(
                query, { query = it }, focus,
                onSubmit = { results.firstOrNull()?.let { onLaunch(it.first, null) } ?: if (query.isNotBlank()) onSearchWeb(query) else Unit },
                onSettings = onSettings,
            )
        }
    }
}

@Composable
private fun rowInteraction(active: Boolean?): Pair<Float, Color> {
    val colors = MaterialTheme.colorScheme
    val motion = LocalMotion.current
    val alpha by animateFloatAsState(if (active == false) .28f else 1f, motion.effects(), label = "dim")
    val tint by animateColorAsState(if (active == true) colors.primary else colors.onSurface, motion.effects(), label = "tint")
    return alpha to tint
}

@Composable
private fun TextRow(
    row: AppRow, settings: Settings, dot: Boolean, touched: Char?, index: Int, progress: () -> Float,
    onLaunch: Launch, onMenu: (AppEntry) -> Unit,
) {
    val motion = LocalMotion.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val active = touched?.let { it == row.letter }
    val (alpha, tint) = rowInteraction(active)
    val shift by animateFloatAsState(if (active == true) 1f else if (pressed) .5f else 0f, motion.spatial(), label = "shift")
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val stagger = motion.stagger
    Row(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { coords[0] = it }
            .graphicsLayer {
                val p = progress()
                // Rows catch up in sequence as the sheet rises, so the list lands instead of appearing.
                val t = if (stagger) ((p - (index.coerceAtMost(14) * .025f)) / .65f).coerceIn(0f, 1f) else 1f
                this.alpha = alpha * t
                translationY = (1f - t) * 48.dp.toPx()
                translationX = shift * 12.dp.toPx()
            }
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(source, null, onLongClick = { onMenu(row.app) }) { onLaunch(row.app, coords[0]?.toRect()) }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(36.dp), Alignment.Center) {
            if (row.firstOfLetter) Text(row.letter.toString(), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            row.label, color = tint, fontSize = settings.drawerTextSize.sp, lineHeight = (settings.drawerTextSize + 6).sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            fontWeight = FontWeight.Normal,
        )
        if (row.app.isWork) Text("  work", color = MaterialTheme.colorScheme.tertiary, fontSize = 12.sp)
        if (dot) Spacer(Modifier.padding(start = 8.dp).size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun TextList(
    rows: List<AppRow>, state: LazyListState, settings: Settings, dots: Set<String>, touched: Char?,
    pad: PaddingValues, progress: () -> Float, onLaunch: Launch, onMenu: (AppEntry) -> Unit, header: @Composable () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), state, pad) {
        item(key = "header") { header() }
        itemsIndexed(rows, key = { _, r -> r.app.key }) { i, r ->
            TextRow(r, settings, settings.dots && r.app.packageName in dots, touched, i, progress, onLaunch, onMenu)
        }
    }
}

@Composable
private fun IconGrid(
    rows: List<AppRow>, state: LazyGridState, settings: Settings, dots: Set<String>, touched: Char?,
    colors: IconColors, pad: PaddingValues, onLaunch: Launch, onMenu: (AppEntry) -> Unit, header: @Composable () -> Unit,
) {
    LazyVerticalGrid(GridCells.Fixed(settings.columns), Modifier.fillMaxSize(), state, pad) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
        itemsIndexed(rows, key = { _, r -> r.app.key }) { _, r ->
            GridCell(r.app, r.label, settings, settings.dots && r.app.packageName in dots, touched?.let { it == r.letter }, colors, onLaunch, onMenu)
        }
    }
}

@Composable
internal fun GridCell(
    app: AppEntry, label: String, settings: Settings, dot: Boolean, active: Boolean?, colors: IconColors,
    onLaunch: Launch, onMenu: (AppEntry) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val (alpha, tint) = rowInteraction(active)
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Column(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { coords[0] = it }
            .graphicsLayer { this.alpha = alpha }
            .combinedClickable(source, null, onLongClick = { onMenu(app) }) { onLaunch(app, coords[0]?.toRect()) }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIcon(app, settings, settings.iconSize, colors, pressed = pressed || active == true)
            if (dot) Spacer(Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
        if (settings.showLabels) Text(
            label, color = tint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
        )
    }
}

@Composable
private fun MathRow(answer: String) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer)
            .combinedClickable {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Result", answer))
                SystemActions.toast(context, "Copied")
            }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("= $answer", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Tap to copy", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun WebRow(query: String, onSearchWeb: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = { onSearchWeb(query) }).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Language, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Search the web for “$query”", Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NoResults(query: String, onSearchWeb: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
        Text("No apps match", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        WebRow(query, onSearchWeb)
    }
}

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit, focus: FocusRequester, onSubmit: () -> Unit, onSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().height(56.dp)
            .clip(CircleShape).background(colors.surfaceContainerHigh).padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = colors.onSurfaceVariant)
        Box(Modifier.weight(1f).padding(start = 12.dp), Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search apps", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            BasicTextField(
                query, onChange, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            )
        }
        if (query.isNotEmpty()) IconButton({ onChange("") }) { Icon(Icons.Outlined.Close, "Clear", tint = colors.onSurfaceVariant) }
        else IconButton(onSettings) { Icon(Icons.Outlined.Tune, "Customize", tint = colors.onSurfaceVariant) }
    }
}

fun LayoutCoordinates.toRect(): AndroidRect? {
    if (!isAttached) return null
    val b = boundsInWindow()
    return AndroidRect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
}
