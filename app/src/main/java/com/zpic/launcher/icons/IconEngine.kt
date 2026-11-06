package com.zpic.launcher.icons

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import com.zpic.launcher.data.AppEntry
import com.zpic.launcher.data.IconOverride
import com.zpic.launcher.data.IconStyle
import com.zpic.launcher.data.sectionOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import kotlin.math.min

data class IconColors(val tonalBg: Int, val tonalFg: Int, val surfaceBg: Int, val surfaceFg: Int, val ghostFg: Int, val duoBg: Int = 0, val duoA: Int = 0, val duoB: Int = 0)

/** Identifies an installed ADW/Nova-format icon pack. */
data class IconPackInfo(val packageName: String, val label: String)

class IconEngine(private val context: Context) {
    private val cache = LruCache<String, ImageBitmap>(600)
    private val packs = HashMap<String, Map<String, String>>()

    fun clear() = cache.evictAll()

    fun installedPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        return listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.teslacoilsw.launcher.THEME")
            .flatMap { pm.queryIntentActivities(Intent(it), PackageManager.GET_META_DATA) }
            .map { IconPackInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label }
    }

    suspend fun load(app: AppEntry, label: String, style: IconStyle, pack: String?, px: Int, c: IconColors, override: IconOverride = IconOverride.Auto): ImageBitmap =
        withContext(Dispatchers.Default) {
            val id = "${app.key}|$label|$style|$pack|$px|${c.hashCode()}|$override"
            cache.get(id) ?: render(app, label, style, pack, px, c, override).asImageBitmap().also { cache.put(id, it) }
        }

    private fun render(app: AppEntry, label: String, style: IconStyle, pack: String?, s: Int, c: IconColors, override: IconOverride): Bitmap {
        if (override == IconOverride.Monogram) return monogram(label, s, c, c.tonalBg, c.tonalFg)
        val icon = app.info.getIcon(0)
        if (override == IconOverride.Original) return full(icon, s, c)
        if (override == IconOverride.Auto) pack?.let { packDrawable(it, app) }?.let { d -> return drawPlain(d, s) }
        if (style == IconStyle.Monogram) return monogram(label, s, c, c.tonalBg, c.tonalFg)
        return if (style == IconStyle.System) full(icon, s, c) else themed(icon, label, style, s, c)
    }

    /** Full-bleed square; the UI clips it to the chosen shape, which is what makes shapes morphable. */
    private fun full(icon: Drawable, s: Int, c: IconColors): Bitmap {
        val out = createBitmap(s, s)
        val canvas = Canvas(out)
        if (icon is AdaptiveIconDrawable) {
            icon.background?.let { drawLayer(canvas, it, s) }
            icon.foreground?.let { drawLayer(canvas, it, s) }
        } else {
            canvas.drawColor(c.surfaceBg)
            drawInset(canvas, icon, s, 0.68f)
        }
        return out
    }

    private fun themed(icon: Drawable, label: String, style: IconStyle, s: Int, c: IconColors): Bitmap {
        val (bg, fg) = when (style) {
            IconStyle.Surface -> c.surfaceBg to c.surfaceFg
            IconStyle.Ghost -> Color.TRANSPARENT to c.ghostFg
            IconStyle.TwoTone -> c.duoBg to c.duoA
            else -> c.tonalBg to c.tonalFg
        }
        val out = createBitmap(s, s)
        val canvas = Canvas(out)
        canvas.drawColor(bg)
        // Two-tone paints the glyph on its own layer, so the gradient touches only the logo.
        val layer = if (style == IconStyle.TwoTone) canvas.saveLayer(null, null) else -1
        val mono = (icon as? AdaptiveIconDrawable)?.takeIf { Build.VERSION.SDK_INT >= 33 }?.monochrome
        if (mono != null) {
            val m = mono.mutate()
            m.colorFilter = BlendModeColorFilter(fg, BlendMode.SRC_IN)
            drawLayer(canvas, m, s)
            m.colorFilter = null
        } else {
            val glyph = derivedGlyph(icon, s, fg)
            if (glyph != null) canvas.drawBitmap(glyph, 0f, 0f, null)
            else return monogram(label, s, c, bg, fg)
        }
        if (layer >= 0) {
            val paint = Paint().apply {
                shader = android.graphics.LinearGradient(0f, 0f, s.toFloat(), s.toFloat(), c.duoA, c.duoB, android.graphics.Shader.TileMode.CLAMP)
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            }
            canvas.drawRect(0f, 0f, s.toFloat(), s.toFloat(), paint)
            canvas.restoreToCount(layer)
        }
        return out
    }

    /** Null when the icon has no honest glyph; the caller then uses a monogram, never a blank plate. */
    private fun derivedGlyph(icon: Drawable, s: Int, ink: Int): Bitmap? {
        val src = createBitmap(s, s)
        val canvas = Canvas(src)
        if (icon is AdaptiveIconDrawable) {
            // Foreground first: its own alpha is the cleanest outline. PNG-based apps often leave it empty.
            val fg = icon.foreground
            if (fg != null) drawLayer(canvas, fg, s)
            val px = IntArray(s * s)
            src.getPixels(px, 0, s, 0, 0, s, s)
            if (px.count { (it ushr 24) > 200 } > s * s * 0.9f || fg == null) icon.background?.let { bg ->
                canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                drawLayer(canvas, bg, s)
                fg?.let { drawLayer(canvas, it, s) }
            }
        } else drawInset(canvas, icon, s, 0.8f)

        val px = IntArray(s * s)
        src.getPixels(px, 0, s, 0, 0, s, s)
        val alpha = Glyph.extract(px, s) ?: return null
        val r = Color.red(ink); val g = Color.green(ink); val b = Color.blue(ink)
        val mapped = IntArray(px.size) { Color.argb(alpha[it], r, g, b) }
        val glyph = createBitmap(s, s).also { it.setPixels(mapped, 0, s, 0, 0, s, s) }
        val box = Glyph.bounds(alpha, s) ?: return null
        // Logos come at any size on their plates; settle them all to the same optical size.
        val out = createBitmap(s, s)
        val w = box[2] - box[0] + 1; val h = box[3] - box[1] + 1
        val scale = s * 0.54f / maxOf(w, h)
        val cx = (box[0] + box[2] + 1) / 2f; val cy = (box[1] + box[3] + 1) / 2f
        val m = android.graphics.Matrix().apply {
            postTranslate(-cx, -cy); postScale(scale, scale); postTranslate(s / 2f, s / 2f)
        }
        Canvas(out).drawBitmap(glyph, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun drawLayer(canvas: Canvas, d: Drawable, s: Int) {
        // Same outset AdaptiveIconDrawable uses, so the 72dp safe zone lands inside the clip.
        val o = s / 4
        d.setBounds(-o, -o, s + o, s + o)
        d.draw(canvas)
    }

    private fun drawInset(canvas: Canvas, d: Drawable, s: Int, scale: Float) {
        val side = (s * scale).toInt()
        val at = (s - side) / 2
        d.setBounds(at, at, at + side, at + side)
        d.draw(canvas)
    }

    private fun drawPlain(d: Drawable, s: Int): Bitmap {
        val out = createBitmap(s, s)
        d.setBounds(0, 0, s, s)
        d.draw(Canvas(out))
        return out
    }

    private fun monogram(label: String, s: Int, c: IconColors, bg: Int, fg: Int): Bitmap {
        val out = createBitmap(s, s)
        val canvas = Canvas(out)
        canvas.drawColor(bg)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = s * 0.44f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val letter = sectionOf(label).toString()
        canvas.drawText(letter, s / 2f, s / 2f - (paint.ascent() + paint.descent()) / 2, paint)
        return out
    }

    private fun packDrawable(pack: String, app: AppEntry): Drawable? = runCatching {
        val map = synchronized(packs) { packs.getOrPut(pack) { readAppFilter(pack) } }
        val name = map["${app.component.packageName}/${app.component.className}"] ?: return null
        val res = context.packageManager.getResourcesForApplication(pack)
        val id = res.getIdentifier(name, "drawable", pack)
        if (id == 0) null else res.getDrawable(id, null)
    }.getOrNull()

    private fun readAppFilter(pack: String): Map<String, String> {
        val res = context.packageManager.getResourcesForApplication(pack)
        val parser = appFilterParser(res, pack) ?: return emptyMap()
        val out = HashMap<String, String>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "item") {
                val comp = parser.getAttributeValue(null, "component")
                val draw = parser.getAttributeValue(null, "drawable")
                if (comp != null && draw != null) {
                    out[comp.removePrefix("ComponentInfo{").removeSuffix("}")] = draw
                }
            }
            parser.next()
        }
        return out
    }

    private fun appFilterParser(res: Resources, pack: String): XmlPullParser? {
        val id = res.getIdentifier("appfilter", "xml", pack)
        if (id != 0) return res.getXml(id)
        return runCatching {
            val stream = res.assets.open("appfilter.xml")
            android.util.Xml.newPullParser().apply { setInput(stream, "utf-8") }
        }.getOrNull()
    }

    fun pxFor(dp: Int): Int = min((dp * context.resources.displayMetrics.density).toInt(), 256)
}
