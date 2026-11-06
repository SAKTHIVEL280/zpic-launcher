package com.zpic.launcher

import com.zpic.launcher.data.HomeCell
import com.zpic.launcher.data.HomeKind
import com.zpic.launcher.data.Settings
import com.zpic.launcher.data.addApps
import com.zpic.launcher.data.addFolder
import com.zpic.launcher.data.drop
import com.zpic.launcher.data.fitted
import com.zpic.launcher.data.gridOf
import com.zpic.launcher.data.isFree
import com.zpic.launcher.data.removePage
import com.zpic.launcher.data.resizedCell
import com.zpic.launcher.data.withoutFromFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    private val a = HomeCell("a", HomeKind.App, col = 0, row = 0, app = "A")
    private val b = HomeCell("b", HomeKind.App, col = 1, row = 0, app = "B")
    private val clock = HomeCell("c", HomeKind.Clock, col = 0, row = 2, spanX = 3, spanY = 2)

    private fun noOverlaps(items: List<HomeCell>) = items.all { isFree(items, it) }

    @Test fun droppingAppOnAppMakesAFolder() {
        val out = drop(listOf(a, b), a.copy(col = 1), 5, 6)
        assertEquals(1, out.size)
        assertEquals(HomeKind.Folder, out[0].kind)
        assertEquals(setOf("A", "B"), out[0].apps.toSet())
    }

    @Test fun droppingOnFolderJoinsIt() {
        val f = HomeCell("f", HomeKind.Folder, col = 2, row = 0, apps = listOf("X", "Y"))
        val out = drop(listOf(a, f), a.copy(col = 2), 5, 6)
        assertEquals(listOf("X", "Y", "A"), out.single().apps)
    }

    @Test fun droppingOnAWidgetFindsTheNearestFreeCell() {
        val out = drop(listOf(a, clock), a.copy(col = 1, row = 2), 5, 6)
        assertTrue(noOverlaps(out))
        assertEquals(2, out.size)
    }

    @Test fun nothingLeavesTheGrid() {
        val out = drop(listOf(clock), clock.copy(col = 9, row = 9), 5, 6).single()
        assertEquals(2, out.col); assertEquals(4, out.row)
    }

    @Test fun resizeStopsAtNeighboursAndEdges() {
        val other = HomeCell("o", HomeKind.App, col = 3, row = 2, app = "O")
        assertNull(resizedCell(listOf(clock, other), clock, 1, 0, 1, 1, 5, 6))
        assertNotNull(resizedCell(listOf(clock), clock, 1, 0, 1, 1, 5, 6))
        assertNull(resizedCell(listOf(clock), clock, 3, 0, 1, 1, 5, 6))
        val shifted = clock.copy(col = 1)
        assertEquals(4, resizedCell(listOf(shifted), shifted, -1, 0, -1, 1, 5, 6)?.spanX)
    }

    @Test fun shrinkingTheGridKeepsEverythingAndOverlapsNothing() {
        val many = (0 until 30).map { HomeCell("i$it", HomeKind.App, col = it % 6, row = it / 6, app = "$it") }
        val out = fitted(many, 4, 5)
        assertEquals(30, out.size)
        assertTrue(noOverlaps(out))
        assertTrue(out.all { it.col < 4 && it.row < 5 })
    }

    @Test fun addingAppsSpillsToTheNextPage() {
        val out = addApps(emptyList(), (0 until 6).map { "k$it" }, 0, 5, 1)
        assertEquals(1, out.last().page)
    }

    @Test fun groupingTakesAppsOffTheirOwnCells() {
        val out = addFolder(listOf(a, b), listOf("A", "B"), "Work", 0, 5, 6)
        assertEquals(1, out.size); assertEquals("Work", out[0].title)
    }

    @Test fun folderDownToOneBecomesThatApp() {
        val f = HomeCell("f", HomeKind.Folder, col = 2, row = 0, apps = listOf("X", "Y"))
        assertEquals(HomeKind.App, withoutFromFolder(listOf(f), "f", "X").single().kind)
    }

    @Test fun removingAPageKeepsItsItems() {
        val items = listOf(a.copy(page = 0), b.copy(page = 1), clock.copy(page = 2))
        assertEquals(3, removePage(items, 1, 5, 6).size)
    }

    @Test fun shelfSwitchAddsAndRemovesIt() {
        val on = com.zpic.launcher.data.withShelf(listOf(a), true, 0, 5, 6)
        assertTrue(on.any { it.kind == HomeKind.Shelf })
        assertEquals(1, com.zpic.launcher.data.withShelf(on, false, 0, 5, 6).size)
    }

    @Test fun oldCoarseLayoutsConvertWithoutMoving() {
        val old = Settings(grid = listOf(clock), homeColumns = 10, homeRows = 12)
        val c = com.zpic.launcher.data.migrateGrid(old).grid!!.single()
        assertEquals(0, c.col); assertEquals(4, c.row); assertEquals(6, c.spanX); assertEquals(4, c.spanY)
        val once = com.zpic.launcher.data.migrateGrid(old)
        assertEquals(once, com.zpic.launcher.data.migrateGrid(once))
    }

    @Test fun droppingOnAWideIconStillMakesAFolder() {
        val big = a.copy(spanX = 2, spanY = 2); val other = b.copy(col = 2, spanX = 2, spanY = 2)
        val out = drop(listOf(big, other), big.copy(col = 1), 10, 12)
        assertEquals(HomeKind.Folder, out.single().kind)
    }

    @Test fun freshHomeIsValid() {
        assertTrue(noOverlaps(gridOf(Settings(widgetId = 7))))
    }
}

class PresetTest {
    @Test fun presetsKeepAppsAndNeverOverlap() {
        val app = com.zpic.launcher.data.HomeCell("a", com.zpic.launcher.data.HomeKind.App, col = 0, row = 0, app = "A")
        com.zpic.launcher.data.HomePreset.entries.forEach { p ->
            val out = com.zpic.launcher.data.applyPreset(listOf(app), p, 5, 6)
            assertTrue(out.any { it.app == "A" })
            assertTrue(out.all { com.zpic.launcher.data.isFree(out, it) })
        }
    }
}
