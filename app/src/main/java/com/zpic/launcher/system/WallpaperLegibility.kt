package com.zpic.launcher.system

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * Text over a wallpaper has to be readable where it sits, not on average. Android itself samples the
 * region behind the clock to choose light or dark text and adds protection when neither is enough;
 * this does the same for every item on the home screen, from a small brightness map of the wallpaper.
 */

const val LUMA_COLS = 9
const val LUMA_ROWS = 16
private const val SAMPLE = 16

/** Dark ink used on light wallpapers, matching the home screen's own dark text. */
val DarkInk = Color(0xFF1A1B20)

/**
 * The wallpaper, region by region: mean relative luminance, and how busy (patterned) it is there.
 * [id] is the system wallpaper id it was measured for, so a wallpaper set by another app is never misread.
 */
@Serializable
data class WallLuma(val id: Int = -1, val mean: List<Float> = emptyList(), val busy: List<Float> = emptyList()) {
    val valid get() = mean.size == LUMA_COLS * LUMA_ROWS && busy.size == mean.size
}

/** What text in one region should look like: its colour, and how much protection it needs (0 to 1). */
data class RegionInk(val ink: Color, val protect: Float)

private fun channel(c: Int): Float {
    val v = c / 255f
    return if (v <= .03928f) v / 12.92f else ((v + .055f) / 1.055f).pow(2.4f)
}

/** WCAG relative luminance of an ARGB pixel. */
fun luminance(px: Int): Float =
    .2126f * channel((px shr 16) and 255) + .7152f * channel((px shr 8) and 255) + .0722f * channel(px and 255)

/** WCAG contrast ratio between two luminances. */
fun contrast(a: Float, b: Float) = (max(a, b) + .05f) / (minOf(a, b) + .05f)

/** Measures a wallpaper exactly as it will be shown (already cropped to the screen). */
fun measureWallpaper(bitmap: Bitmap): WallLuma {
    val w = LUMA_COLS * SAMPLE; val h = LUMA_ROWS * SAMPLE
    val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
    val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
    if (small !== bitmap) small.recycle()
    val mean = ArrayList<Float>(LUMA_COLS * LUMA_ROWS)
    val busy = ArrayList<Float>(LUMA_COLS * LUMA_ROWS)
    for (r in 0 until LUMA_ROWS) for (c in 0 until LUMA_COLS) {
        var sum = 0f; var sq = 0f
        for (y in r * SAMPLE until (r + 1) * SAMPLE) for (x in c * SAMPLE until (c + 1) * SAMPLE) {
            val l = luminance(px[y * w + x]); sum += l; sq += l * l
        }
        val n = (SAMPLE * SAMPLE).toFloat()
        val m = sum / n
        mean += m
        busy += sqrt((sq / n - m * m).coerceAtLeast(0f))
    }
    return WallLuma(mean = mean, busy = busy)
}

/**
 * The best ink for a region of the screen, given as fractions of the screen (0 to 1), after the home
 * screen's own dim. Protection grows when even the better ink falls short of comfortable contrast, or
 * when the wallpaper there is patterned enough to break up the shapes of letters and digits.
 */
fun WallLuma.inkFor(region: Rect, dim: Float): RegionInk? {
    if (!valid) return null
    val c0 = (region.left * LUMA_COLS).toInt().coerceIn(0, LUMA_COLS - 1)
    val c1 = (region.right * LUMA_COLS).toInt().coerceIn(c0, LUMA_COLS - 1)
    val r0 = (region.top * LUMA_ROWS).toInt().coerceIn(0, LUMA_ROWS - 1)
    val r1 = (region.bottom * LUMA_ROWS).toInt().coerceIn(r0, LUMA_ROWS - 1)
    var sum = 0f; var n = 0; var busiest = 0f; var brightest = 0f; var darkest = 1f
    for (r in r0..r1) for (c in c0..c1) {
        val m = mean[r * LUMA_COLS + c] * (1f - dim)
        sum += m; n++
        busiest = max(busiest, busy[r * LUMA_COLS + c])
        brightest = max(brightest, m); darkest = minOf(darkest, m)
    }
    val avg = sum / n.coerceAtLeast(1)
    val white = contrast(1f, avg)
    val darkL = luminance(DarkInk.toArgbInt())
    val dark = contrast(darkL, avg)
    val useWhite = white >= dark
    // The worst spot under the text matters, not just the average: check the ink against it too.
    val worst = if (useWhite) contrast(1f, brightest) else contrast(darkL, darkest)
    val short = ((4.5f - minOf(maxOf(white, dark), worst * 1.4f)) / 3f).coerceIn(0f, 1f)
    val patterned = ((busiest - .06f) / .14f).coerceIn(0f, 1f)
    return RegionInk(if (useWhite) Color.White else DarkInk, max(short, patterned))
}

private fun Color.toArgbInt(): Int {
    val a = (alpha * 255).toInt(); val r = (red * 255).toInt(); val g = (green * 255).toInt(); val b = (blue * 255).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
