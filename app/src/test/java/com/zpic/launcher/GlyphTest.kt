package com.zpic.launcher

import com.zpic.launcher.icons.Glyph
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphTest {
    private val s = 96
    private val white = 0xFFFFFFFF.toInt()
    private val ink = 0xFF0B3D91.toInt()
    private val clear = 0

    private fun img(f: (Int, Int) -> Int) = IntArray(s * s) { f(it % s, it / s) }
    private fun logo(x: Int, y: Int) = x in 34..62 && y in 30..66 && !(x in 44..52 && y in 40..56)
    private fun on(m: IntArray) = m.count { it > 128 }

    @Test fun flatPngLogoOnPlainPlate() {
        val m = Glyph.extract(img { x, y -> if (logo(x, y)) ink else white }, s)!!
        assertTrue(m[48 * s + 38] > 128)   // logo kept
        assertTrue(m[5 * s + 5] < 128)     // plate gone
    }

    @Test fun jpegNoiseOnPlateStaysClean() {
        val noisy = img { x, y ->
            val n = (x * 7 + y * 13) % 9
            val c = if (logo(x, y)) ink else white
            if (c == white) (0xFF shl 24) or ((246 + n) shl 16) or ((246 + n) shl 8) or (246 + n) else c
        }
        val m = Glyph.extract(noisy, s)!!
        assertTrue(on(m) < s * s * 0.3f)
    }

    @Test fun logoOnTransparentPlateCircle() {
        val m = Glyph.extract(img { x, y ->
            val inside = (x - 48) * (x - 48) + (y - 48) * (y - 48) < 40 * 40
            if (!inside) clear else if (logo(x, y)) ink else white
        }, s)!!
        assertTrue(m[48 * s + 38] > 128)
        assertTrue(m[48 * s + 12] < 128)
    }

    @Test fun lightLogoOnGradientPlate() {
        val m = Glyph.extract(img { x, y ->
            if (logo(x, y)) white else (0xFF shl 24) or ((20 + y) shl 16) or (10 shl 8) or (120 + x)
        }, s)
        assertNotNull(m)
        assertTrue(on(m!!) < s * s * 0.3f)
        assertTrue(m[2 * s + 2] < 40)   // no tinted box left behind
    }

    @Test fun solidFreeStandingGlyphStays() {
        val m = Glyph.extract(img { x, y -> if (logo(x, y)) ink else clear }, s)
        assertNotNull(m)
    }

    @Test fun blankPlateHasNoGlyph() {
        assertNull(Glyph.extract(img { _, _ -> white }, s))
        assertNull(Glyph.extract(img { _, _ -> clear }, s))
    }
}

class WordmarkTest {
    private val s = 96
    /** Thin white wordmark on a saturated plate: the shape behind icons like Zepto's. */
    @Test fun thinWordmarkSurvives() {
        val purple = 0xFF5A1FA8.toInt(); val white = 0xFFFFFFFF.toInt()
        val px = IntArray(s * s) { i ->
            val x = i % s; val y = i / s
            if (y in 44..52 && x in 18..78 && (x - 18) % 7 < 3) white else purple
        }
        val m = com.zpic.launcher.icons.Glyph.extract(px, s)!!
        assertTrue(m[48 * s + 18] > 128)
        assertTrue(m[10 * s + 10] < 128)
    }
}
