package com.zpic.launcher.data

import kotlinx.serialization.Serializable

enum class HomeKind { Clock, Pins, Intention, Widget, App, Folder, Shelf }

/** A rectangle of grid cells on one page. Items never overlap and always sit on the grid. */
@Serializable
data class HomeCell(
    val id: String,
    val kind: HomeKind,
    val page: Int = 0,
    val col: Int,
    val row: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val widgetId: Int = -1,
    val app: String? = null,
    val apps: List<String> = emptyList(),
    val title: String = "",
) {
    val resizable get() = kind != HomeKind.App && kind != HomeKind.Folder
}

fun defaultGrid(s: Settings): List<HomeCell> {
    val c = s.homeColumns; val r = s.homeRows
    return buildList {
        add(HomeCell("clock", HomeKind.Clock, col = 0, row = 0, spanX = (c * 3 / 5).coerceAtLeast(2), spanY = 2))
        if (s.widgetId != -1) add(HomeCell("w${s.widgetId}", HomeKind.Widget, col = 0, row = 2, spanX = c, spanY = 2, widgetId = s.widgetId))
        add(HomeCell("pins", HomeKind.Pins, col = 0, row = r - 2, spanX = (c * 3 / 5).coerceAtLeast(2), spanY = 2))
    }
}

/** The saved layout, fitted to the current grid so a smaller grid can't push anything off the page. */
fun gridOf(s: Settings): List<HomeCell> {
    val base = s.grid ?: defaultGrid(s)
    return fitted(base, s.homeColumns, s.homeRows)
}

fun fitted(items: List<HomeCell>, cols: Int, rows: Int): List<HomeCell> {
    val out = ArrayList<HomeCell>()
    for (raw in items) {
        val sx = raw.spanX.coerceIn(1, cols); val sy = raw.spanY.coerceIn(1, rows)
        val c = raw.copy(spanX = sx, spanY = sy, col = raw.col.coerceIn(0, cols - sx), row = raw.row.coerceIn(0, rows - sy))
        if (isFree(out, c)) out += c
        else {
            val spot = findFree(out, sx, sy, c.page, Int.MAX_VALUE / 2, cols, rows) ?: continue
            out += c.copy(page = spot.first, col = spot.second, row = spot.third)
        }
    }
    return out
}

private fun touches(a: HomeCell, b: HomeCell) = a.page == b.page &&
    a.col < b.col + b.spanX && b.col < a.col + a.spanX && a.row < b.row + b.spanY && b.row < a.row + a.spanY

fun isFree(items: List<HomeCell>, cell: HomeCell): Boolean = items.none { it.id != cell.id && touches(it, cell) }

/** First open spot of this size, searching from a page onward: page, col, row. */
fun findFree(items: List<HomeCell>, spanX: Int, spanY: Int, fromPage: Int, pages: Int, cols: Int, rows: Int): Triple<Int, Int, Int>? {
    val last = minOf(pages.toLong(), fromPage + 64L).toInt()
    for (p in fromPage until last) for (r in 0..rows - spanY) for (c in 0..cols - spanX) {
        val probe = HomeCell("?", HomeKind.App, p, c, r, spanX, spanY)
        if (isFree(items, probe)) return Triple(p, c, r)
    }
    return null
}

private var nextId = System.nanoTime()
fun newId(prefix: String) = "$prefix${nextId++}"

/** Places apps one by one in open cells, on the given page or the pages after it. */
fun addApps(items: List<HomeCell>, keys: List<String>, page: Int, cols: Int, rows: Int): List<HomeCell> {
    var out = items
    for (k in keys) {
        if (out.any { it.app == k || k in it.apps }) continue
        val spot = findFree(out, 1, 1, page, Int.MAX_VALUE / 2, cols, rows) ?: continue
        out = out + HomeCell(newId("a"), HomeKind.App, spot.first, spot.second, spot.third, app = k)
    }
    return out
}

fun addFolder(items: List<HomeCell>, keys: List<String>, title: String, page: Int, cols: Int, rows: Int): List<HomeCell> {
    val spot = findFree(items, 1, 1, page, Int.MAX_VALUE / 2, cols, rows) ?: return items
    return items.filter { it.kind != HomeKind.App || it.app !in keys } +
        HomeCell(newId("f"), HomeKind.Folder, spot.first, spot.second, spot.third, apps = keys, title = title)
}

/**
 * Dropping an item. On an app, an app makes a folder; on a folder, it joins. Otherwise it takes the
 * cell if free, or the nearest free spot, or goes back where it came from.
 */
fun drop(items: List<HomeCell>, moved: HomeCell, cols: Int, rows: Int): List<HomeCell> {
    val rest = items.filter { it.id != moved.id }
    val at = moved.copy(col = moved.col.coerceIn(0, cols - moved.spanX), row = moved.row.coerceIn(0, rows - moved.spanY))
    if (moved.kind == HomeKind.App && moved.app != null) {
        val hit = rest.firstOrNull { it.page == at.page && it.col == at.col && it.row == at.row && (it.kind == HomeKind.App || it.kind == HomeKind.Folder) }
        when (hit?.kind) {
            HomeKind.Folder -> return rest.map { if (it.id == hit.id) it.copy(apps = (it.apps + moved.app).distinct()) else it }
            HomeKind.App -> return rest.filter { it.id != hit.id } + hit.copy(id = newId("f"), kind = HomeKind.Folder, app = null, apps = listOfNotNull(hit.app, moved.app), title = "Folder")
            else -> Unit
        }
    }
    if (isFree(rest, at)) return rest + at
    var best: HomeCell? = null
    var bestGap = Int.MAX_VALUE
    for (r in 0..rows - at.spanY) for (c in 0..cols - at.spanX) {
        val probe = at.copy(col = c, row = r)
        if (isFree(rest, probe)) {
            val gap = kotlin.math.abs(c - at.col) + kotlin.math.abs(r - at.row)
            if (gap < bestGap) { bestGap = gap; best = probe }
        }
    }
    return if (best != null) rest + best else items
}

/** Corner resize in whole cells; null when the new size would hit something or leave the page. */
fun resizedCell(items: List<HomeCell>, base: HomeCell, dCols: Int, dRows: Int, signX: Int, signY: Int, cols: Int, rows: Int): HomeCell? {
    var c = base.col; var r = base.row; var sx = base.spanX; var sy = base.spanY
    if (signX > 0) sx += dCols else { c += dCols; sx -= dCols }
    if (signY > 0) sy += dRows else { r += dRows; sy -= dRows }
    if (sx < 1 || sy < 1 || c < 0 || r < 0 || c + sx > cols || r + sy > rows) return null
    return base.copy(col = c, row = r, spanX = sx, spanY = sy).takeIf { isFree(items, it) }
}

/** Removing a page pulls later pages in; what sat on it moves to the page before. */
fun removePage(items: List<HomeCell>, page: Int, cols: Int, rows: Int): List<HomeCell> =
    fitted(items.map { if (it.page >= page && it.page > 0) it.copy(page = it.page - 1) else it }, cols, rows)

/** A folder down to one app turns back into that app; an empty one goes. */
fun withoutFromFolder(items: List<HomeCell>, folderId: String, key: String): List<HomeCell> = items.mapNotNull { c ->
    if (c.id != folderId) return@mapNotNull c
    val left = c.apps - key
    when (left.size) {
        0 -> null
        1 -> c.copy(id = newId("a"), kind = HomeKind.App, app = left[0], apps = emptyList(), title = "")
        else -> c.copy(apps = left)
    }
}

/** The shelf is a home item like any other, so its switch simply adds or removes it. */
fun withShelf(items: List<HomeCell>, on: Boolean, page: Int, cols: Int, rows: Int): List<HomeCell> {
    if (!on) return items.filter { it.kind != HomeKind.Shelf }
    if (items.any { it.kind == HomeKind.Shelf }) return items
    val spot = findFree(items, 1, 2, page, Int.MAX_VALUE / 2, cols, rows) ?: return items
    return items + HomeCell(newId("shelf"), HomeKind.Shelf, spot.first, spot.second, spot.third, 1, 2)
}

enum class HomePreset(val title: String, val hint: String) {
    Minimal("Minimal", "A clock and your pinned list"),
    Focused("Focused", "Adds your intention and the notification shelf"),
    Stock("Stock", "A compact clock on top, with room for icons"),
}

/** Lays out the clock and text items for a preset. Apps, folders and widgets stay and flow around them. */
fun applyPreset(items: List<HomeCell>, preset: HomePreset, cols: Int, rows: Int): List<HomeCell> {
    val wide = (cols * 3 / 5).coerceAtLeast(2)
    val shapes = when (preset) {
        HomePreset.Minimal -> listOf(
            HomeCell("clock", HomeKind.Clock, col = 0, row = 0, spanX = wide, spanY = 2),
            HomeCell("pins", HomeKind.Pins, col = 0, row = rows - 2, spanX = wide, spanY = 2),
        )
        HomePreset.Focused -> listOf(
            HomeCell("clock", HomeKind.Clock, col = 0, row = 0, spanX = wide, spanY = 2),
            HomeCell("intention", HomeKind.Intention, col = 0, row = 2, spanX = cols, spanY = 1),
            HomeCell("pins", HomeKind.Pins, col = 0, row = rows - 2, spanX = wide, spanY = 2),
            HomeCell("shelf", HomeKind.Shelf, col = cols - 1, row = rows - 2, spanX = 1, spanY = 2),
        )
        HomePreset.Stock -> listOf(HomeCell("clock", HomeKind.Clock, col = 0, row = 0, spanX = cols, spanY = 1))
    }
    val kept = items.filter { it.kind == HomeKind.App || it.kind == HomeKind.Folder || it.kind == HomeKind.Widget }
    return fitted(shapes + kept, cols, rows)
}
