package com.zpic.launcher.ui

import android.graphics.Matrix
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath
import com.zpic.launcher.ZpicApp
import com.zpic.launcher.data.AppEntry
import com.zpic.launcher.data.GlyphInk
import com.zpic.launcher.data.IconOverride
import com.zpic.launcher.data.IconShape
import com.zpic.launcher.data.IconStyle
import com.zpic.launcher.data.Settings
import com.zpic.launcher.data.label
import com.zpic.launcher.icons.IconColors
import androidx.compose.ui.graphics.Color

private fun polygon(shape: IconShape): RoundedPolygon = when (shape) {
    IconShape.Circle -> MaterialShapes.Circle
    IconShape.Squircle -> RoundedPolygon.rectangle(2f, 2f, CornerRounding(0.72f, 1f))
    IconShape.Square -> RoundedPolygon.rectangle(2f, 2f, CornerRounding(0.36f, 0.4f))
    IconShape.Cookie -> MaterialShapes.Cookie9Sided
    IconShape.Clover -> MaterialShapes.Clover4Leaf
    IconShape.Sunny -> MaterialShapes.Sunny
    IconShape.Arch -> MaterialShapes.Arch
}.normalized()

private val morphs = HashMap<IconShape, Morph>()

/** Every icon shape can flow into a circle, which is what a press does. */
private fun morphFor(shape: IconShape) = morphs.getOrPut(shape) { Morph(polygon(shape), polygon(IconShape.Circle)) }

class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        path.transform(Matrix().apply { setScale(size.width, size.height) })
        return Outline.Generic(path.asComposePath())
    }
}

fun shapeOf(polygon: RoundedPolygon, other: RoundedPolygon, progress: Float): Shape = MorphShape(Morph(polygon, other), progress)

/**
 * ink is what reads on the surface behind the icon. Glyph icons take either the theme colour or
 * white; on a light app page white would vanish, so there the mono option uses ink instead.
 */
@Composable
fun rememberIconColors(ink: Color, glyph: GlyphInk = GlyphInk.Theme, onWallpaper: Boolean = false): IconColors {
    val c = MaterialTheme.colorScheme
    return remember(c, ink, glyph, onWallpaper) {
        val darkBehind = ink.luminance() > .5f
        val themed = if (darkBehind) lerp(c.primary, Color.White, .35f) else c.primary
        val mono = if (onWallpaper || darkBehind) Color.White else ink
        IconColors(
            c.primaryContainer.toArgb(), c.onPrimaryContainer.toArgb(),
            c.surfaceContainerHigh.toArgb(), c.primary.toArgb(),
            (if (glyph == GlyphInk.White) mono else themed).toArgb(),
        )
    }
}

@Composable
fun AppIcon(
    app: AppEntry,
    settings: Settings,
    sizeDp: Int,
    colors: IconColors,
    modifier: Modifier = Modifier,
    pressed: Boolean = false,
) {
    val engine = (LocalContext.current.applicationContext as ZpicApp).icons
    val label = app.label(settings)
    val override = settings.iconOverrides[app.key] ?: IconOverride.Auto
    val bitmap by produceState<ImageBitmap?>(null, app.key, label, settings.iconStyle, settings.iconPack, sizeDp, colors, override) {
        value = engine.load(app, label, settings.iconStyle, settings.iconPack, engine.pxFor(sizeDp), colors, override)
    }
    val motion = LocalMotion.current
    val morph by animateFloatAsState(if (pressed) 1f else 0f, motion.spatial(), label = "press")
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, motion.fastSpatial(), label = "scale")
    val morphShape = morphFor(settings.iconShape)
    val clip = (settings.iconStyle != IconStyle.Ghost || override == IconOverride.Original || override == IconOverride.Monogram) && settings.iconPack == null
    Box(modifier.size(sizeDp.dp).graphicsLayer {
        scaleX = scale
        scaleY = scale
        if (clip) {
            this.clip = true
            shape = MorphShape(morphShape, morph.coerceIn(0f, 1f))
        }
    }) {
        bitmap?.let { Image(it, null, Modifier.size(sizeDp.dp)) }
    }
}
