package com.zpic.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zpic.launcher.data.AppEntry
import com.zpic.launcher.data.FavStyle
import com.zpic.launcher.data.HomeAlign
import com.zpic.launcher.data.Settings
import com.zpic.launcher.data.label
import com.zpic.launcher.icons.IconColors
import kotlin.math.min

internal fun inkStyle(ink: Color) = TextStyle(
    color = ink,
    shadow = if (ink == Color.White) Shadow(Color(0x40000000), blurRadius = 12f) else null,
)

@Composable
fun Intention(settings: Settings, ink: Color) {
    Text(
        settings.intention,
        style = inkStyle(ink).merge(MaterialTheme.typography.bodyLarge).copy(color = ink.copy(alpha = .86f)), maxLines = 2,
    )
}

/** Rows share the whole area and text and icons grow with it, so resizing changes what you see. */
@Composable
fun PinnedApps(
    apps: List<AppEntry>, settings: Settings, dots: Set<String>, ink: Color, colors: IconColors, box: DpSize, onLaunch: Launch,
) {
    val favorites = remember(apps, settings.favorites) { settings.favorites.mapNotNull { k -> apps.firstOrNull { it.key == k } } }
    val horizontal = when (settings.align) {
        HomeAlign.Start -> Arrangement.Start
        HomeAlign.Center -> Arrangement.Center
        HomeAlign.End -> Arrangement.End
    }
    if (favorites.isEmpty()) {
        if (LocalHomeEditing.current) Text("Pinned apps", style = inkStyle(ink).merge(MaterialTheme.typography.bodyMedium).copy(color = ink.copy(alpha = .8f)))
        return
    }
    if (settings.favStyle == FavStyle.Icons) {
        val icon = min(box.width.value / favorites.size * .8f, box.height.value * .8f).toInt().coerceAtLeast(24)
        Row(Modifier.fillMaxSize(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
            favorites.forEach { PinnedRow(it, settings, settings.dots && it.packageName in dots, ink, colors, 0f, icon, Arrangement.Center, onLaunch) }
        }
        return
    }
    val rowHeight = box.height.value / favorites.size
    val text = (rowHeight * .5f).coerceIn(13f, 44f)
    val icon = if (settings.favStyle == FavStyle.IconText) (rowHeight * .78f).toInt().coerceIn(20, 64) else 0
    Column(Modifier.fillMaxSize()) {
        favorites.forEach {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.CenterStart) {
                PinnedRow(it, settings, settings.dots && it.packageName in dots, ink, colors, text, icon, horizontal, onLaunch)
            }
        }
    }
}

@Composable
private fun PinnedRow(
    app: AppEntry, settings: Settings, dot: Boolean, ink: Color, colors: IconColors, textSp: Float, icon: Int,
    horizontal: Arrangement.Horizontal, onLaunch: Launch,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Row(
        Modifier.then(if (textSp > 0f) Modifier.fillMaxWidth() else Modifier).onGloballyPositioned { coords[0] = it }
            .clickable(source, null, enabled = !LocalHomeEditing.current) { onLaunch(app, coords[0]?.toRect()) },
        horizontalArrangement = horizontal, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon > 0) AppIcon(app, settings, icon, colors, pressed = pressed)
        if (textSp > 0f) Text(
            app.label(settings), Modifier.padding(start = if (icon > 0) 12.dp else 0.dp),
            style = inkStyle(ink).copy(fontSize = textSp.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (dot) Spacer(Modifier.padding(start = 8.dp).size(6.dp).clip(CircleShape).background(ink))
    }
}

/** One home cell: icon and optional label, sized to the cell so nothing can ever overlap. */
@Composable
fun SingleApp(app: AppEntry, settings: Settings, dot: Boolean, ink: Color, colors: IconColors, icon: Int, onLaunch: Launch, second: AppEntry? = null) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Column(
        Modifier.fillMaxSize().onGloballyPositioned { coords[0] = it }
            .combinedClickable(source, null, enabled = !LocalHomeEditing.current, onDoubleClick = second?.let { s -> { onLaunch(s, coords[0]?.toRect()) } }) { onLaunch(app, coords[0]?.toRect()) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            AppIcon(app, settings, icon, colors, pressed = pressed)
            if (dot) Spacer(Modifier.align(Alignment.TopEnd).size(9.dp).clip(CircleShape).background(ink))
        }
        if (settings.homeLabels) Text(app.label(settings), Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp), style = inkStyle(ink).copy(fontSize = 12.sp, textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
