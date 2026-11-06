package com.zpic.launcher

import com.zpic.launcher.data.rank
import com.zpic.launcher.data.sectionOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexTest {
    @Test fun sections() {
        assertEquals('A', sectionOf("Ádobe"))
        assertEquals('G', sectionOf("  gmail"))
        assertEquals('#', sectionOf("1Password"))
        assertEquals('#', sectionOf(""))
    }

    @Test fun ranking() {
        assertTrue(rank("cal", "Calendar") > rank("cal", "Google Calendar"))
        assertTrue(rank("cal", "Google Calendar") > rank("cal", "Local Cast"))
        assertTrue(rank("gm", "Google Maps") > rank("gm", "Pigment"))
        assertTrue(rank("gm", "Pigment") > 0)
        assertEquals(0, rank("zz", "Calendar"))
        assertEquals(0, rank("  ", "Calendar"))
        assertTrue(rank("cafe", "Café Finder") > 0)
        assertEquals(20, rank("ytm", "YouTube Music Extra".replace(" ", "")))
    }
}

class MathTest {
    private fun m(s: String) = com.zpic.launcher.data.evalMath(s)
    @Test fun sums() {
        assertEquals(14.0, m("2+3*4")!!, 0.0)
        assertEquals(20.0, m("(2+3)*4")!!, 0.0)
        assertEquals(8.0, m("2^3")!!, 0.0)
        assertEquals(-1.0, m("2-3")!!, 0.0)
    }
    @Test fun notSums() {
        assertEquals(null, m("chrome"))
        assertEquals(null, m("2+"))
        assertEquals(null, m("1/0"))
        assertEquals(null, m("12"))
        assertEquals("0.5", com.zpic.launcher.data.formatNumber(.5))
        assertEquals("3", com.zpic.launcher.data.formatNumber(3.0))
    }
}
