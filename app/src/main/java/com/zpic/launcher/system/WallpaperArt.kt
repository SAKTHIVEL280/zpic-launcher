package com.zpic.launcher.system

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.material3.MaterialShapes
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Every setting the colour editor exposes for a design. */
data class ArtSpec(val hue: Float = 215f, val chroma: Float = .5f, val light: Boolean = false, val density: Float = 1f, val accentShift: Float = 40f)

data class ArtDesign(val family: String, val name: String, val draw: Art.() -> Unit) {
    val id get() = "$family/$name"
}

val artSwatches = listOf(212f, 262f, 330f, 8f, 28f, 48f, 95f, 150f, 185f)

/** The drawing context: tones of the chosen hue, and small original primitives that designs compose. */
class Art(val c: Canvas, val w: Float, val h: Float, val spec: ArtSpec, seed: Int) {
    val r = Random(seed.toLong())
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val d get() = spec.density

    fun t(l: Float, hue: Float = spec.hue, chroma: Float = spec.chroma) =
        ColorUtils.HSLToColor(floatArrayOf((hue + 360f) % 360f, chroma.coerceIn(0f, 1f), (if (spec.light) 1f - l * .78f else l).coerceIn(0f, 1f)))
    fun a(l: Float) = t(l, spec.hue + spec.accentShift, spec.chroma * 1.1f)

    fun rnd(from: Float, to: Float) = from + r.nextFloat() * (to - from)
    fun clear() { p.shader = null; p.maskFilter = null; p.style = Paint.Style.FILL; p.alpha = 255 }

    fun bg(top: Int, bottom: Int = top) { clear(); p.shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h, p); clear() }
    fun fill(color: Int, path: Path) { clear(); p.color = color; c.drawPath(path, p) }
    fun grad(a: Int, b: Int, path: Path, x0: Float = 0f, y0: Float = 0f, x1: Float = w, y1: Float = h) {
        clear(); p.shader = LinearGradient(x0, y0, x1, y1, a, b, Shader.TileMode.CLAMP); c.drawPath(path, p); clear()
    }
    fun disc(x: Float, y: Float, rad: Float, color: Int) { clear(); p.color = color; c.drawCircle(x, y, rad, p) }
    fun glow(x: Float, y: Float, rad: Float, color: Int) {
        clear(); p.shader = RadialGradient(x, y, rad, color, ColorUtils.setAlphaComponent(color, 0), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, rad, p); clear()
    }
    fun orb(x: Float, y: Float, rad: Float, inner: Int, outer: Int) {
        clear(); p.shader = RadialGradient(x - rad * .35f, y - rad * .4f, rad * 1.5f, inner, outer, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, rad, p); clear()
    }
    fun ring(x: Float, y: Float, rad: Float, width: Float, color: Int) {
        clear(); p.style = Paint.Style.STROKE; p.strokeWidth = width; p.color = color; c.drawCircle(x, y, rad, p); clear()
    }
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) {
        clear(); p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = width; p.color = color; c.drawLine(x0, y0, x1, y1, p); clear()
    }
    fun rect(x: Float, y: Float, rw: Float, rh: Float, rad: Float, color: Int) { clear(); p.color = color; c.drawRoundRect(RectF(x, y, x + rw, y + rh), rad, rad, p) }

    fun polygon(poly: RoundedPolygon, x: Float, y: Float, size: Float, rot: Float, top: Int, bottom: Int) {
        val path = Path()
        poly.normalized().toPath(path)
        path.transform(Matrix().apply { postTranslate(-.5f, -.5f); postRotate(rot); postScale(size, size); postTranslate(x, y) })
        grad(top, bottom, path, x - size / 2, y - size / 2, x + size / 2, y + size / 2)
    }

    /** A filled hill: a band rising from the bottom edge, with a smooth top. */
    fun hill(y: Float, swing: Float, phase: Float, freq: Float = 1f, top: Int, bottom: Int) {
        val path = Path().apply {
            moveTo(0f, h + 4)
            var x = 0f
            val step = w / 48f
            while (x <= w + step) { lineTo(x, y + sin(x / w * freq * 2 * PI + phase).toFloat() * swing); x += step }
            lineTo(w + 4, h + 4); close()
        }
        grad(top, bottom, path, 0f, y - swing, w, h)
    }

    fun dots(step: Float, color: Int, size: (Float, Float) -> Float) {
        val s = step / d.coerceAtLeast(.4f)
        var y = s / 2
        while (y < h) {
            var x = s / 2
            while (x < w) {
                val v = size(x / w, y / h).coerceIn(0f, 1f)
                if (v > .02f) disc(x, y, s * .46f * v, color)
                x += s
            }
            y += s
        }
    }

    fun rays(cx: Float, cy: Float, n: Int, a: Int, b: Int) {
        val far = hypot(w, h) * 1.2f
        val count = (n * d).toInt().coerceAtLeast(3)
        repeat(count) { i ->
            val a0 = i * 2 * PI / count; val a1 = (i + .5) * 2 * PI / count
            val path = Path().apply {
                moveTo(cx, cy)
                lineTo(cx + cos(a0).toFloat() * far, cy + sin(a0).toFloat() * far)
                lineTo(cx + cos(a1).toFloat() * far, cy + sin(a1).toFloat() * far); close()
            }
            fill(if (i % 2 == 0) a else b, path)
        }
    }

    fun rings(cx: Float, cy: Float, n: Int, from: Float, to: Float) {
        val count = (n * d).toInt().coerceAtLeast(2)
        val max = hypot(w, h)
        for (i in count downTo 1) disc(cx, cy, max * i / count, t(from + (to - from) * i / count))
    }

    fun stripes(angle: Float, n: Int, tone: (Int) -> Int) {
        val count = (n * d).toInt().coerceAtLeast(2)
        c.save(); c.rotate(angle, w / 2, h / 2)
        val span = hypot(w, h) * 1.5f
        val band = span / count
        repeat(count) { i -> rect(-span / 2 + w / 2, -span / 2 + h / 2 + i * band, span, band + 1, 0f, tone(i)) }
        c.restore()
    }

    fun tiles(columns: Int, cell: (Int, Int, Float, Float, Float) -> Unit) {
        val n = (columns * d).toInt().coerceAtLeast(2)
        val s = w / n
        var row = 0; var y = 0f
        while (y < h) { repeat(n) { cell(it, row, it * s, y, s) }; y += s; row++ }
    }

}

private val polygons get() = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.Flower, MaterialShapes.Cookie6Sided, MaterialShapes.Gem, MaterialShapes.Arch)

private fun mesh(a: Art, n: Int, chromaBoost: Float = 1f) = with(a) {
    bg(t(.1f), t(.06f))
    repeat(n) { glow(rnd(0f, w), rnd(0f, h), rnd(w * .6f, w * 1.2f), if (it % 2 == 0) t(rnd(.3f, .55f), spec.hue, spec.chroma * chromaBoost) else a(rnd(.3f, .55f))) }
}

private fun waves(a: Art, n: Int, swing: Float, freq: Float, start: Float = .25f) = with(a) {
    bg(t(.1f), t(.05f))
    val count = (n * d).toInt().coerceAtLeast(2)
    repeat(count) { i ->
        val f = i / (count - 1f)
        val tone = .14f + f * .42f
        hill(h * (start + (.82f - start) * f), h * swing, i * 1.7f + spec.accentShift, freq, t(tone + .08f), t(tone - .05f))
    }
}

private fun halftone(a: Art, ink: Int, fn: (Float, Float) -> Float) = with(a) {
    bg(if (spec.light) Color.rgb(238, 237, 232) else Color.BLACK)
    dots(w / 30f, ink, fn)
}

private fun mono(a: Art) = if (a.spec.light) Color.rgb(24, 24, 26) else Color.rgb(236, 236, 234)

/** Ten original designs in the spirit of each system's wallpapers, none copied from any of them. */
val artDesigns: List<ArtDesign> = buildList {
    fun add(family: String, name: String, draw: Art.() -> Unit) = add(ArtDesign(family, name, draw))

    val pixel = "Pixel"
    add(pixel, "Curves") { waves(this, 5, .1f, 1f) }
    add(pixel, "Hills") { waves(this, 4, .06f, .6f, .45f) }
    add(pixel, "Shapes") {
        bg(t(.11f))
        polygon(MaterialShapes.Cookie9Sided, w * .9f, h * .2f, w * 1.1f, 12f, t(.34f), t(.2f))
        polygon(MaterialShapes.Sunny, w * .1f, h * .56f, w * .95f, 30f, t(.44f), t(.28f))
        polygon(MaterialShapes.Clover4Leaf, w * .86f, h * .9f, w * .9f, 20f, a(.6f), a(.42f))
    }
    add(pixel, "Pebbles") {
        bg(t(.09f))
        val n = (9 * d).toInt().coerceAtLeast(3)
        repeat(n) { val s = polygons[it % polygons.size]; polygon(s, rnd(0f, w), h * (it + .5f) / n, rnd(w * .3f, w * .55f), rnd(0f, 90f), t(rnd(.25f, .55f)), t(.16f)) }
    }
    add(pixel, "Bloom") {
        bg(t(.08f))
        repeat(6) { i -> polygon(MaterialShapes.Flower, w * .5f, h * .62f, w * (1.9f - i * .28f), i * 18f, t(.14f + i * .08f), t(.1f + i * .07f)) }
    }
    add(pixel, "Ribbons") {
        bg(t(.08f))
        repeat((5 * d).toInt().coerceAtLeast(2)) { i ->
            val path = Path().apply { moveTo(-20f, h * (.1f + i * .17f)); cubicTo(w * .4f, h * (.0f + i * .2f), w * .6f, h * (.45f + i * .1f), w + 20, h * (.25f + i * .15f)) }
            clear(); p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = w * .17f; p.color = if (i % 2 == 0) t(.3f + i * .07f) else a(.4f + i * .04f); c.drawPath(path, p); clear()
        }
    }
    add(pixel, "Ripple") { rings(w * .78f, h * .78f, 7, .12f, .5f) }
    add(pixel, "Arcs") { bg(t(.1f)); repeat((5 * d).toInt().coerceAtLeast(2)) { i -> ring(0f, h, w * (.35f + i * .3f), w * .12f, t(.22f + i * .08f)) } }
    add(pixel, "Horizon") { bg(t(.14f), t(.3f)); disc(w * .5f, h * .52f, w * .3f, a(.68f)); waves2(this) }
    add(pixel, "Stack") {
        bg(t(.08f)); val n = (7 * d).toInt().coerceAtLeast(3)
        repeat(n) { i -> rect(w * (.08f + i * .03f), h * (.1f + i * .1f), w * .84f - i * w * .06f, h * .2f, w * .13f, t(.16f + i * .06f)) }
    }

    val nothing = "Nothing"
    add(nothing, "Dot wave") { halftone(this, mono(this)) { x, y -> (sin((x * 1.5f + y * 2.6f) * PI).toFloat() + 1) / 2 * .9f } }
    add(nothing, "Dot ring") {
        halftone(this, mono(this)) { x, y -> val q = hypot(x - .5f, (y - .45f) * h / w); if (q in .32f..(.42f)) 1f else (.42f - q).coerceIn(0f, .42f) * .5f }
        ring(w * .5f, h * .45f, w * .37f, w * .012f, a(.6f))
    }
    add(nothing, "Dot fade") { halftone(this, mono(this)) { _, y -> y * y * 1.1f } }
    add(nothing, "Dot sun") { halftone(this, a(.6f)) { x, y -> 1.1f - hypot(x - .5f, (y - .35f) * h / w) * 2.1f } }
    add(nothing, "Dot corner") { halftone(this, mono(this)) { x, y -> 1.2f - hypot(1f - x, (1f - y) * h / w) * 1.05f } }
    add(nothing, "Dot bars") { halftone(this, mono(this)) { x, y -> val b = (x * 7).toInt(); if (y > .25f + .6f * ((b * 37 % 10) / 10f)) .9f else .1f } }
    add(nothing, "Dot digit") {
        halftone(this, mono(this)) { x, y -> val on = (x in .22f..(.78f) && (y in .3f..(.36f) || y in .62f..(.68f))) || (x in .22f..(.3f) || x in .7f..(.78f)) && y in .3f..(.68f); if (on) .95f else .08f }
    }
    add(nothing, "Scan") { bg(if (spec.light) Color.rgb(238, 237, 232) else Color.BLACK); val n = (64 * d).toInt().coerceAtLeast(8); repeat(n) { i -> val f = i / n.toFloat(); rect(0f, f * h, w * (.2f + .8f * f * f), h / n * .45f, 0f, mono(this)) }; disc(w * .8f, h * .2f, w * .05f, a(.6f)) }
    add(nothing, "Crosshair") {
        bg(if (spec.light) Color.rgb(238, 237, 232) else Color.BLACK)
        val n = (10 * d).toInt().coerceAtLeast(3); val s = w / n; val ink = mono(this)
        var y = s / 2f
        while (y < h) { repeat(n) { i -> val x = (i + .5f) * s; line(x - s * .1f, y, x + s * .1f, y, 3f, ink); line(x, y - s * .1f, x, y + s * .1f, 3f, ink) }; y += s }
        disc(w * .5f + s * .5f, h * .5f, s * .3f, a(.6f))
    }
    add(nothing, "Stairs") {
        bg(if (spec.light) Color.rgb(238, 237, 232) else Color.BLACK)
        val n = (24 * d).toInt().coerceAtLeast(6); val s = w / n
        val ink = mono(this)
        repeat(n) { i -> repeat((i * 1.9f).toInt()) { j -> if ((i + j) % 2 == 0) rect(i * s, h - (j + 1) * s, s * .86f, s * .86f, 0f, ink) } }
    }

    val oneui = "One UI"
    add(oneui, "Orbs") { bg(t(.12f), t(.2f)); val n = (6 * d).toInt().coerceAtLeast(2); repeat(n) { val s = rnd(w * .2f, w * .5f); orb(rnd(0f, w), rnd(0f, h), s, t(.7f), t(.25f)) } }
    add(oneui, "Pair") { bg(t(.15f), t(.08f)); orb(w * .3f, h * .35f, w * .5f, a(.72f), a(.3f)); orb(w * .75f, h * .68f, w * .45f, t(.72f), t(.28f)) }
    add(oneui, "Aurora") { mesh(this, 5) }
    add(oneui, "Frost") { bg(t(.2f), t(.42f)); repeat((5 * d).toInt().coerceAtLeast(2)) { i -> rect(-w * .2f, h * (.1f + i * .19f), w * 1.4f, h * .1f, h * .05f, ColorUtils.setAlphaComponent(t(.8f), 34)) } }
    add(oneui, "Glass") { bg(t(.2f), t(.1f)); tiles(4) { _, _, x, y, s -> rect(x + s * .08f, y + s * .08f, s * .84f, s * .84f, s * .24f, ColorUtils.setAlphaComponent(t(rnd(.5f, .9f)), 40)) } }
    add(oneui, "Soft rings") { bg(t(.14f)); repeat((5 * d).toInt().coerceAtLeast(2)) { i -> glow(w * .5f, h * .5f, w * (1.1f - i * .18f), if (i % 2 == 0) t(.45f) else a(.45f)) } }
    add(oneui, "Dune") { waves(this, 3, .05f, .5f, .4f); glow(w * .8f, h * .15f, w * .7f, a(.7f)) }
    add(oneui, "Prism") { bg(t(.1f)); repeat(5) { i -> val path = Path().apply { moveTo(w * (i * .25f - .1f), 0f); lineTo(w * (i * .25f + .3f), 0f); lineTo(w * (.5f + (i - 2) * .05f), h); close() }; grad(t(.4f + i * .06f), a(.2f), path, 0f, 0f, 0f, h) } }
    add(oneui, "Drift") { mesh(this, 3, .7f); val n = (24 * d).toInt().coerceAtLeast(6); repeat(n) { disc(rnd(0f, w), rnd(0f, h), rnd(2f, w * .012f), ColorUtils.setAlphaComponent(t(.92f), 120)) } }
    add(oneui, "Petal") { bg(t(.1f)); repeat(8) { i -> c.save(); c.rotate(i * 45f, w * .5f, h * .5f); orb(w * .5f, h * .5f - w * .38f, w * .28f, t(.7f), t(.25f)); c.restore() } }

    val ios = "iOS"
    add(ios, "Mesh") { mesh(this, 6, 1.3f) }
    add(ios, "Dusk") { bg(t(.5f, spec.hue + 20), t(.16f, spec.hue - 30)); glow(w * .5f, h, w * 1.3f, a(.65f)) }
    add(ios, "Layers") { bg(t(.55f)); repeat((5 * d).toInt().coerceAtLeast(2)) { i -> hillSoft(this, h * (.3f + i * .13f), i, t(.5f - i * .08f)) } }
    add(ios, "Bokeh") { bg(t(.1f), t(.05f)); val n = (14 * d).toInt().coerceAtLeast(4); repeat(n) { disc(rnd(0f, w), rnd(0f, h), rnd(w * .05f, w * .2f), ColorUtils.setAlphaComponent(if (it % 2 == 0) t(.65f) else a(.65f), 60)) } }
    add(ios, "Rise") { bg(t(.12f), t(.5f)); disc(w * .5f, h * 1.05f, w * .95f, a(.7f)); glow(w * .5f, h, w * 1.5f, a(.6f)) }
    add(ios, "Depth") { bg(t(.08f)); repeat(5) { i -> orb(w * .5f, h * .5f, w * (1.1f - i * .2f), t(.22f + i * .11f), t(.1f + i * .06f)) } }
    add(ios, "Flow") { mesh(this, 4); repeat(3) { i -> val path = Path().apply { moveTo(0f, h * (.3f + i * .2f)); cubicTo(w * .3f, h * (.1f + i * .2f), w * .7f, h * (.6f + i * .15f), w, h * (.3f + i * .2f)); lineTo(w, h); lineTo(0f, h); close() }; fill(ColorUtils.setAlphaComponent(t(.6f - i * .1f), 70), path) } }
    add(ios, "Split") { bg(t(.62f), t(.5f)); rect(0f, h * .5f, w, h * .5f, 0f, t(.16f)); glow(w * .5f, h * .5f, w * .9f, a(.7f)) }
    add(ios, "Sphere") { mesh(this, 3); orb(w * .5f, h * .42f, w * .36f, t(.86f), a(.3f)) }
    add(ios, "Tide") { bg(t(.5f), t(.2f)); repeat((6 * d).toInt().coerceAtLeast(2)) { i -> hillSoft(this, h * (.2f + i * .12f), i * 2, ColorUtils.setAlphaComponent(t(.2f), 90)) } }

    val oxygen = "OxygenOS"
    add(oxygen, "Split") { bg(t(.14f)); val path = Path().apply { moveTo(0f, h); lineTo(w, h * .25f); lineTo(w, h); close() }; grad(a(.55f), a(.35f), path) }
    add(oxygen, "Stripes") { stripes(-28f, 10) { i -> t(.1f + (i % 3) * .1f) } }
    add(oxygen, "Chevrons") { bg(t(.1f)); val n = (6 * d).toInt().coerceAtLeast(2); repeat(n) { i -> val y = h * i / n; val path = Path().apply { moveTo(0f, y); lineTo(w / 2, y + h * .1f); lineTo(w, y); lineTo(w, y + h * .07f); lineTo(w / 2, y + h * .17f); lineTo(0f, y + h * .07f); close() }; fill(t(.2f + i * .08f), path) } }
    add(oxygen, "Burst") { rays(w * .15f, h * .85f, 12, t(.14f), t(.22f)) }
    add(oxygen, "Fold") { bg(t(.12f)); repeat(4) { i -> val path = Path().apply { moveTo(w * i / 4f, 0f); lineTo(w * (i + 1) / 4f, h * .15f); lineTo(w * (i + 1) / 4f, h); lineTo(w * i / 4f, h * .85f); close() }; grad(t(.18f + i * .1f), t(.1f + i * .06f), path) } }
    add(oxygen, "Facets") { bg(t(.1f)); tiles(5) { i, j, x, y, s -> val path = Path().apply { if ((i + j) % 2 == 0) { moveTo(x, y); lineTo(x + s, y); lineTo(x, y + s) } else { moveTo(x + s, y); lineTo(x + s, y + s); lineTo(x, y + s) }; close() }; fill(t(rnd(.14f, .4f)), path) } }
    add(oxygen, "Squares") { bg(t(.1f)); repeat((8 * d).toInt().coerceAtLeast(3)) { i -> val s = hypot(w, h) * (1f - i * .11f); rect(w / 2 - s / 2, h / 2 - s / 2, s, s, 0f, t(.1f + i * .06f)) } }
    add(oxygen, "Steps") { bg(t(.1f)); val n = (8 * d).toInt().coerceAtLeast(3); repeat(n) { i -> rect(w * i / n, h * (1f - (i + 1f) / n * .8f), w / n + 1, h, 0f, t(.16f + i * .05f)) } }
    add(oxygen, "Slash") { bg(t(.1f)); c.save(); c.rotate(24f, w / 2, h / 2); rect(w * .3f, -h, w * .14f, h * 3, 0f, a(.58f)); rect(w * .52f, -h, w * .06f, h * 3, 0f, t(.4f)); c.restore() }
    add(oxygen, "Delta") { bg(t(.08f)); repeat(6) { i -> val s = 1f - i * .15f; val path = Path().apply { moveTo(w / 2, h * .5f - w * .9f * s); lineTo(w / 2 + w * .85f * s, h * .5f + w * .6f * s); lineTo(w / 2 - w * .85f * s, h * .5f + w * .6f * s); close() }; fill(t(.1f + i * .09f), path) } }

    val hyper = "HyperOS"
    add(hyper, "Peaks") { bg(t(.3f), t(.5f)); repeat(4) { i -> val y = h * (.4f + i * .14f); val path = Path().apply { moveTo(0f, h); var x = 0f; lineTo(0f, y); while (x < w) { x += w / 3f; lineTo(x - w / 6f, y - rnd(h * .04f, h * .16f)); lineTo(x, y) }; lineTo(w, h); close() }; fill(t(.4f - i * .09f), path) } }
    add(hyper, "Sea") { bg(t(.62f), t(.4f)); repeat(5) { i -> hillSoft(this, h * (.5f + i * .1f), i * 3, t(.34f - i * .05f)) } }
    add(hyper, "Capsules") { bg(t(.1f)); val n = (5 * d).toInt().coerceAtLeast(2); repeat(n) { i -> rect(w * (.1f + i * .8f / n), h * (.15f + (i % 3) * .1f), w * .13f, h * .6f, w * .065f, if (i % 2 == 0) t(.5f) else a(.6f)) } }
    add(hyper, "Rings") { bg(t(.1f)); ring(w * .5f, h * .35f, w * .34f, w * .1f, a(.62f)); ring(w * .5f, h * .66f, w * .3f, w * .1f, t(.5f)) }
    add(hyper, "Check") { bg(t(.1f)); tiles(4) { i, j, x, y, s -> if ((i + j) % 2 == 0) rect(x, y, s, s, 0f, t(.3f)) } }
    add(hyper, "Quarters") { bg(t(.1f)); tiles(3) { i, j, x, y, s -> val cx = x + if ((i + j) % 2 == 0) 0f else s; val cy = y + if (j % 2 == 0) 0f else s; c.save(); c.clipRect(x, y, x + s, y + s); disc(cx, cy, s, if ((i * 3 + j) % 3 == 0) a(.6f) else t(.3f + (i % 2) * .15f)); c.restore() } }
    add(hyper, "Blocks") { bg(t(.1f)); rect(0f, 0f, w * .6f, h * .4f, 0f, t(.3f)); rect(w * .6f, h * .2f, w * .4f, h * .35f, 0f, a(.6f)); rect(0f, h * .6f, w * .4f, h * .4f, 0f, t(.5f)); disc(w * .72f, h * .8f, w * .22f, t(.7f)) }
    add(hyper, "Discs") { bg(t(.6f), t(.2f)); repeat(4) { i -> disc(w * .5f, h * (.25f + i * .15f), w * (.34f - i * .03f), t(.72f - i * .13f)) } }
    add(hyper, "Half") { bg(t(.12f)); c.save(); c.clipRect(0f, 0f, w, h * .5f); disc(w * .5f, h * .5f, w * .44f, a(.6f)); c.restore(); c.save(); c.clipRect(0f, h * .5f, w, h); disc(w * .5f, h * .5f, w * .44f, t(.4f)); c.restore() }
    add(hyper, "Grid") { bg(t(.08f)); tiles(6) { _, _, x, y, s -> rect(x + s * .12f, y + s * .12f, s * .76f, s * .76f, s * .2f, t(rnd(.12f, .34f))) } }
}

private fun waves2(a: Art) = with(a) {
    hill(h * .6f, h * .03f, 1f, 1f, t(.2f), t(.1f))
    hill(h * .72f, h * .03f, 3f, 1.3f, t(.14f), t(.08f))
}

private fun hillSoft(a: Art, y: Float, seed: Int, color: Int) = with(a) {
    hill(y, h * .035f, seed * 1.3f, .8f, color, color)
}

object WallpaperArt {
    fun render(design: ArtDesign, spec: ArtSpec, w: Int, h: Int): Bitmap {
        val out = createBitmap(w, h, Bitmap.Config.ARGB_8888)
        design.draw(Art(Canvas(out), w.toFloat(), h.toFloat(), spec, design.id.hashCode()))
        return out
    }
}
