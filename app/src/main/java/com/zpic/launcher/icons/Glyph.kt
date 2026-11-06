package com.zpic.launcher.icons

import kotlin.math.abs

/**
 * Turns any icon, including a flat PNG or JPG with its logo baked onto a plate, into a one-ink glyph.
 * Works on ARGB pixels so it stays testable off-device. Returns an alpha map, or null when no honest
 * glyph exists (a blank square, say), so the caller can use a monogram rather than a plain block.
 */
object Glyph {
    private fun a(p: Int) = p ushr 24
    private fun r(p: Int) = p shr 16 and 255
    private fun g(p: Int) = p shr 8 and 255
    private fun b(p: Int) = p and 255
    private fun luma(p: Int) = (r(p) * 299 + g(p) * 587 + b(p) * 114) / 1000
    private fun dist(p: Int, q: Int) = abs(r(p) - r(q)) + abs(g(p) - g(q)) + abs(b(p) - b(q))

    fun extract(px: IntArray, size: Int): IntArray? {
        val total = px.size
        val solid = IntArray(total) { (((a(px[it]) - 120).coerceAtLeast(0)) * 255 / 100).coerceAtMost(255) }
        val coverage = solid.count { it > 128 }
        if (coverage < total * 0.01f) return null

        val plate = dominant(px, solid)
        if (plate != null) {
            val share = solid.indices.count { solid[it] > 128 && dist(px[it], plate) < 60 }.toFloat() / coverage
            // Try for a logo first: a thin wordmark on a plate covers almost nothing, yet it's the whole icon.
            if (share > 0.42f) {
                val keyed = IntArray(total) { i -> solid[i] * ((dist(px[i], plate) - 45).coerceAtLeast(0) * 255 / 90).coerceAtMost(255) / 255 }
                if (usable(keyed, solid, total, size)) return keyed
            }
            // Nearly one colour with nothing on it: a real glyph, or a blank plate if it fills its bounds.
            if (share > 0.97f) return solid.takeIf { !isPlate(it, size) }
        }
        // Gradients and photos have no plate colour, so split on brightness and keep the minority side.
        val split = otsu(px, solid)
        var light = 0; var dark = 0
        for (i in px.indices) if (solid[i] > 128) { if (luma(px[i]) > split) light++ else dark++ }
        val keepLight = light < dark
        val byLuma = IntArray(total) { i ->
            val on = (luma(px[i]) > split) == keepLight
            if (on) solid[i] else 0
        }
        if (usable(byLuma, solid, total, size)) return byLuma
        tail(px, solid, total, size)?.let { return it }
        return solid.takeIf { !isPlate(it, size) }
    }

    /** A logo floats inside its plate. A mask that runs along the plate's outline is leftover plate. */
    private fun usable(alpha: IntArray, solid: IntArray, total: Int, size: Int): Boolean {
        val on = alpha.count { it > 40 }
        if (on <= total * 0.006f || on >= total * 0.6f) return false
        var rim = 0; var touching = 0
        for (i in 0 until total) {
            if (solid[i] <= 128) continue
            val x = i % size; val y = i / size
            val edge = x == 0 || y == 0 || x == size - 1 || y == size - 1 ||
                solid[i - 1] <= 128 || solid[i + 1] <= 128 || solid[i - size] <= 128 || solid[i + size] <= 128
            if (edge) { rim++; if (alpha[i] > 40) touching++ }
        }
        return rim == 0 || touching < rim * 0.12f
    }

    /** A logo on a gradient sits at the far end of the brightness range, so try each extreme. */
    private fun tail(px: IntArray, solid: IntArray, total: Int, size: Int): IntArray? {
        val values = px.indices.filter { solid[it] > 128 }.map { luma(px[it]) }
        val mean = values.average()
        val spread = kotlin.math.sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        for (sign in listOf(1, -1)) {
            val cut = mean + sign * spread * 1.3
            val m = IntArray(total) { i -> if (sign * (luma(px[i]) - cut) > 0) solid[i] else 0 }
            if (usable(m, solid, total, size)) return m
        }
        return null
    }

    private fun dominant(px: IntArray, solid: IntArray): Int? {
        val buckets = HashMap<Int, Int>()
        for (i in px.indices) if (solid[i] > 128) {
            val k = px[i] and 0xE0E0E0
            buckets[k] = (buckets[k] ?: 0) + 1
        }
        return buckets.maxByOrNull { it.value }?.key?.let { it or 0x101010 or (255 shl 24) }
    }

    /** A big filled block: the empty plate of an icon, not a logo. */
    private fun isPlate(alpha: IntArray, size: Int): Boolean {
        val box = bounds(alpha, size) ?: return true
        val area = (box[2] - box[0] + 1) * (box[3] - box[1] + 1)
        return fill(alpha, size) > 0.72f && area > alpha.size * 0.4f
    }

    /** How much of its bounding box a mask fills; a full square reads 1, a logo much less. */
    fun fill(alpha: IntArray, size: Int): Float {
        val box = bounds(alpha, size) ?: return 0f
        val area = (box[2] - box[0] + 1) * (box[3] - box[1] + 1)
        return alpha.count { it > 128 }.toFloat() / area
    }

    /** left, top, right, bottom of the opaque part of a mask. */
    fun bounds(alpha: IntArray, size: Int): IntArray? {
        var l = size; var t = size; var r = -1; var b = -1
        for (y in 0 until size) for (x in 0 until size) if (alpha[y * size + x] > 128) {
            if (x < l) l = x
            if (x > r) r = x
            if (y < t) t = y
            if (y > b) b = y
        }
        return if (r < 0) null else intArrayOf(l, t, r, b)
    }

    private fun otsu(px: IntArray, solid: IntArray): Int {
        val hist = IntArray(256)
        var n = 0
        for (i in px.indices) if (solid[i] > 128) { hist[luma(px[i]).coerceIn(0, 255)]++; n++ }
        var sum = 0L
        for (i in 0..255) sum += i.toLong() * hist[i]
        var sumB = 0L; var wB = 0; var best = 0.0; var at = 127
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = n - wB
            if (wF == 0) break
            sumB += t.toLong() * hist[t]
            val mB = sumB.toDouble() / wB
            val mF = (sum - sumB).toDouble() / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; at = t }
        }
        return at
    }
}
