package com.zpic.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import com.zpic.launcher.data.SpanLimits
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Widgets describe their size in two ways: pixel minimums (every Android version) and, since Android 12,
 * a default size in launcher cells plus resize limits. Zpic's grid is finer than a launcher's, so both
 * are translated into Zpic cells here. Getting this wrong is what leaves widgets floating in empty space.
 */

/** Launcher cells a widget's targetCellWidth/Height are written against: a typical 4-wide, 5-tall phone grid. */
private const val LAUNCHER_COLS = 4f
private const val LAUNCHER_ROWS = 5f

private fun Int.cellsOf(px: Float) = ceil(this / px.coerceAtLeast(1f)).toInt()

/** The size a new widget starts at: what it asks for, never smaller than it can render. */
fun widgetStartSpan(info: AppWidgetProviderInfo?, cellW: Float, cellH: Float, cols: Int, rows: Int): Pair<Int, Int> {
    if (info == null) return cols to (rows / 4).coerceAtLeast(1)
    val minX = info.minWidth.cellsOf(cellW).coerceIn(1, cols)
    val minY = info.minHeight.cellsOf(cellH).coerceIn(1, rows)
    if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
        val x = (info.targetCellWidth * cols / LAUNCHER_COLS).roundToInt()
        val y = (info.targetCellHeight * rows / LAUNCHER_ROWS).roundToInt()
        return x.coerceIn(minX, cols) to y.coerceIn(minY, rows)
    }
    // Older widgets only give a minimum, written for a launcher's big cells. Round up to whole launcher cells
    // the way the system launcher does, or the widget lands as a thin strip far narrower than intended.
    val launcherW = cellW * cols / LAUNCHER_COLS
    val launcherH = cellH * rows / LAUNCHER_ROWS
    var x = (info.minWidth.cellsOf(launcherW) * cols / LAUNCHER_COLS).roundToInt().coerceIn(minX, cols)
    val y = (info.minHeight.cellsOf(launcherH) * rows / LAUNCHER_ROWS).roundToInt().coerceIn(minY, rows)
    // Three launcher cells of four leaves a one-cell gap nothing fits in, so take the whole width.
    if (x >= cols * 3 / 4) x = cols
    return x to y
}

/** How far the user may resize a widget, from its own resize mode and limits. */
fun widgetLimits(info: AppWidgetProviderInfo?, cellW: Float, cellH: Float, cols: Int, rows: Int): SpanLimits {
    if (info == null) return SpanLimits()
    val mode = info.resizeMode
    val minW = if (info.minResizeWidth > 0) info.minResizeWidth else info.minWidth
    val minH = if (info.minResizeHeight > 0) info.minResizeHeight else info.minHeight
    val minX = minW.cellsOf(cellW).coerceIn(1, cols)
    val minY = minH.cellsOf(cellH).coerceIn(1, rows)
    var maxX = cols; var maxY = rows
    if (Build.VERSION.SDK_INT >= 31) {
        if (info.maxResizeWidth > 0) maxX = floor(info.maxResizeWidth / cellW).toInt().coerceIn(minX, cols)
        if (info.maxResizeHeight > 0) maxY = floor(info.maxResizeHeight / cellH).toInt().coerceIn(minY, rows)
    }
    return SpanLimits(
        minX, minY, maxX, maxY,
        canX = mode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
        canY = mode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
    )
}
