package com.zpic.launcher

import com.zpic.launcher.data.FocusMode
import com.zpic.launcher.data.HomeCell
import com.zpic.launcher.data.HomeKind
import com.zpic.launcher.data.Settings
import com.zpic.launcher.data.activeMode
import com.zpic.launcher.data.applyShared
import com.zpic.launcher.data.countOpen
import com.zpic.launcher.data.drop
import com.zpic.launcher.data.searchSettings
import com.zpic.launcher.data.shareable
import com.zpic.launcher.data.unstack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturesTest {
    private val w1 = HomeCell("a", HomeKind.Widget, col = 0, row = 0, spanX = 4, spanY = 2, widgetId = 1)
    private val w2 = HomeCell("b", HomeKind.Widget, col = 0, row = 4, spanX = 4, spanY = 2, widgetId = 2)

    @Test fun droppingAWidgetOnAWidgetStacksThem() {
        val out = drop(listOf(w1, w2), w2.copy(row = 0), 10, 12)
        assertEquals(listOf(2), out.single().stack)
    }

    @Test fun unstackPutsItBack() {
        val out = unstack(listOf(w1.copy(stack = listOf(2))), "a", 10, 12)
        assertEquals(2, out.size); assertTrue(out[0].stack.isEmpty())
    }

    @Test fun overnightScheduleWraps() {
        val s = Settings(modes = listOf(FocusMode("s", "Sleep", startHour = 22, endHour = 7)))
        assertEquals("s", activeMode(s, 23)?.id); assertEquals("s", activeMode(s, 3)?.id); assertNull(activeMode(s, 12))
    }

    @Test fun manualModeWins() {
        val s = Settings(modes = listOf(FocusMode("w", "Work")), manualMode = "w")
        assertEquals("w", activeMode(s, 12)?.id)
    }

    @Test fun countsResetEachDay() {
        val a = countOpen(countOpen(Settings(), "x", 5), "x", 5)
        assertEquals(2, a.openCounts["x"])
        assertEquals(1, countOpen(a, "x", 6).openCounts["x"])
    }

    @Test fun sharedLayoutCarriesNothingPrivateAndKeepsMine() {
        val mine = Settings(intention = "secret", hidden = setOf("h"), favorites = listOf("f"))
        val out = shareable(mine)
        assertEquals("", out.intention); assertTrue(out.hidden.isEmpty())
        val merged = applyShared(Settings(intention = "mine", hidden = setOf("z")), out)
        assertEquals("mine", merged.intention); assertEquals(setOf("z"), merged.hidden)
    }

    @Test fun settingsSearch() {
        assertEquals("Wi-Fi", searchSettings("wi").first().title)
        assertTrue(searchSettings("zzz").isEmpty())
    }
}
