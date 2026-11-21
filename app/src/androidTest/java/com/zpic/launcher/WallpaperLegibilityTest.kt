package com.zpic.launcher

import android.util.Log
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zpic.launcher.system.ArtSpec
import com.zpic.launcher.system.DarkInk
import com.zpic.launcher.system.WallpaperArt
import com.zpic.launcher.system.artDesigns
import com.zpic.launcher.system.artSwatches
import com.zpic.launcher.system.contrast
import com.zpic.launcher.system.inkFor
import com.zpic.launcher.system.measureWallpaper
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every built-in wallpaper, light and dark, in several colours: the clock area must end up readable once
 * the home screen has picked its ink and added protection. Large text like the clock needs 3:1 (WCAG AA).
 */
@RunWith(AndroidJUnit4::class)
class WallpaperLegibilityTest {
    private val clockArea = Rect(.05f, .08f, .95f, .45f)

    @Test
    fun everyDesignKeepsTheClockReadable() {
        val failures = mutableListOf<String>()
        val heavy = mutableListOf<String>()
        for (design in artDesigns) for (light in listOf(false, true)) for (hue in artSwatches.filterIndexed { i, _ -> i % 3 == 0 }) {
            val bmp = WallpaperArt.render(design, ArtSpec(hue = hue, light = light), 540, 1200)
            val luma = measureWallpaper(bmp)
            bmp.recycle()
            val region = luma.copy(id = 0).inkFor(clockArea, 0f)!!
            val inkL = if (region.ink == Color.White) 1f else .0115f
            // The worst cell under the clock, with the protection shade the home screen draws there.
            val cols = (clockArea.left * 9).toInt()..(clockArea.right * 9).toInt().coerceAtMost(8)
            val rows = (clockArea.top * 16).toInt()..(clockArea.bottom * 16).toInt().coerceAtMost(15)
            val a = .42f * region.protect
            val shadeL = if (region.ink == Color.White) 0f else 1f
            var worst = Float.MAX_VALUE
            for (r in rows) for (c in cols) {
                val l = luma.mean[r * 9 + c]
                worst = minOf(worst, contrast(inkL, l * (1 - a) + shadeL * a))
            }
            val name = "${design.id} ${if (light) "light" else "dark"} hue=${hue.toInt()}"
            if (region.protect > .6f) heavy += "$name protect=${"%.2f".format(region.protect)}"
            if (worst < 3f) failures += "$name worst=${"%.2f".format(worst)} ink=${if (region.ink == DarkInk) "dark" else "white"} protect=${"%.2f".format(region.protect)}"
        }
        heavy.forEach { Log.i("Legibility", "heavy: $it") }
        failures.forEach { Log.w("Legibility", "unreadable: $it") }
        Log.i("Legibility", "checked ${artDesigns.size * 2 * 3} renders, ${heavy.size} heavy, ${failures.size} unreadable")
        assertTrue("Clock unreadable on:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
