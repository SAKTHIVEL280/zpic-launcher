package com.zpic.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * The A–Z rail. Under the thumb the letters swell into a wave and push toward the list, while a
 * bubble rides alongside and rotates its scalloped edge on every letter, like a dial clicking over.
 */
@Composable
fun AlphabetRail(
    letters: List<Char>,
    current: Char?,
    atEnd: Boolean,
    onTouch: (Char?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return
    val motion = LocalMotion.current
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var activeIndex by remember { mutableIntStateOf(-1) }
    var heightPx by remember { mutableIntStateOf(0) }
    val active = activeIndex in letters.indices
    val push = if (atEnd) -1f else 1f

    BoxWithConstraints(modifier.fillMaxHeight().width(44.dp).onSizeChanged { heightPx = it.height }) {
        val row = min(maxHeight / letters.size, 22.dp)
        val rowPx = with(density) { row.toPx() }
        val total = rowPx * letters.size
        val latest = letters
        Box(Modifier.fillMaxSize().pointerInput(letters) {
            fun hit(y: Float): Int {
                val top = (size.height - total) / 2f
                return ((y - top) / rowPx).toInt().coerceIn(0, latest.lastIndex)
            }
            fun update(y: Float) {
                val i = hit(y)
                if (i != activeIndex) {
                    activeIndex = i
                    onTouch(latest[i])
                }
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                update(down.position.y)
                val id = down.id
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == id } ?: break
                    if (!change.pressed) break
                    change.consume()
                    update(change.position.y)
                }
                activeIndex = -1
                onTouch(null)
            }
        })
        Column(
            Modifier.fillMaxSize().padding(vertical = 0.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEachIndexed { i, letter ->
                // A gaussian around the thumb gives the wave its soft shoulders.
                val distance = (i - activeIndex).toFloat()
                val lift = if (active) exp(-(distance * distance) / 4.5f) else 0f
                val animated by animateFloatAsState(lift, motion.spatial(), label = "wave")
                val isCurrent = !active && letter == current
                val tint = when {
                    i == activeIndex -> colors.primary
                    isCurrent -> colors.primary
                    else -> lerp(colors.onSurfaceVariant.copy(alpha = .62f), colors.onSurface, animated.coerceIn(0f, 1f))
                }
                Box(Modifier.height(row).width(44.dp), Alignment.Center) {
                    Text(
                        letter.toString(),
                        color = tint,
                        fontSize = 11.sp,
                        fontWeight = if (isCurrent || i == activeIndex) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.graphicsLayer {
                            val s = 1f + animated * 1.1f
                            scaleX = s; scaleY = s
                            translationX = push * animated * 26.dp.toPx()
                        },
                    )
                }
            }
        }
    }

    RailBubble(
        visible = active,
        letter = letters.getOrNull(activeIndex),
        index = activeIndex,
        y = if (active && heightPx > 0) {
            val rowPx = with(density) { min(with(density) { heightPx.toDp() } / letters.size, 22.dp).toPx() }
            (heightPx - rowPx * letters.size) / 2f + rowPx * (activeIndex + .5f)
        } else null,
        atEnd = atEnd,
    )
}

@Composable
private fun RailBubble(visible: Boolean, letter: Char?, index: Int, y: Float?, atEnd: Boolean) {
    val motion = LocalMotion.current
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val size = 76.dp
    val sizePx = with(density) { size.toPx() }
    val appear by animateFloatAsState(if (visible) 1f else 0f, motion.spatial(), label = "bubble")
    val yAnim = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf(' ') }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(y) {
        if (y == null) { first = true; return@LaunchedEffect }
        if (first) { yAnim.snapTo(y); first = false } else scope.launch { yAnim.animateTo(y, motion.spatial()) }
    }
    LaunchedEffect(index) {
        if (index >= 0) scope.launch { spin.animateTo(index * 40f, motion.spatial()) }
    }
    letter?.let { shown = it }
    if (appear <= 0.01f) return
    val scallop = remember { MaterialShapes.Cookie12Sided.normalized() }
    val shape = remember(scallop) { MorphShape(androidx.graphics.shapes.Morph(scallop, scallop), 0f) }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(if (atEnd) Alignment.TopEnd else Alignment.TopStart)
                .offset { IntOffset(0, (yAnim.value - sizePx / 2).roundToInt().coerceAtLeast(0)) }
                .padding(start = if (atEnd) 0.dp else 96.dp, end = if (atEnd) 96.dp else 0.dp)
                .size(size)
                .graphicsLayer {
                    scaleX = appear; scaleY = appear
                    alpha = appear.coerceIn(0f, 1f)
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { rotationZ = spin.value; clip = true; this.shape = shape }
                    .background(colors.primaryContainer)
            )
            AnimatedContent(
                targetState = shown,
                transitionSpec = {
                    val down = targetState > initialState
                    (slideInVertically(motion.spatial()) { if (down) it else -it } + fadeIn(motion.effects())) togetherWith
                        (slideOutVertically(motion.spatial()) { if (down) -it else it } + fadeOut(motion.effects()))
                },
                label = "letter",
            ) {
                Text(it.toString(), color = colors.onPrimaryContainer, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}
