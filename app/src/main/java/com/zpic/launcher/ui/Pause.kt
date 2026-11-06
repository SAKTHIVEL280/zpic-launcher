package com.zpic.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import kotlinx.coroutines.delay

/** A speed bump, not a wall: the app always opens if the person still wants it after a breath. */
@Composable
fun PauseOverlay(label: String, seconds: Int, opensToday: Int, onOpen: () -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    val colors = MaterialTheme.colorScheme
    var left by remember { mutableIntStateOf(seconds) }
    LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- } }
    val appear = remember { Animatable(0f) }
    val motion = LocalMotion.current
    LaunchedEffect(Unit) { appear.animateTo(1f, motion.spatial()) }
    val morph = remember { Morph(MaterialShapes.Circle.normalized(), MaterialShapes.Cookie7Sided.normalized()) }
    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "b",
    )
    Column(
        Modifier.fillMaxSize().graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }.background(colors.surface).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(180.dp), Alignment.Center) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val s = .75f + breath * .25f
                    scaleX = s; scaleY = s
                    rotationZ = breath * 40f
                    clip = true
                    shape = MorphShape(morph, breath)
                }.background(colors.primaryContainer)
            )
            Text(if (left > 0) left.toString() else "", style = MaterialTheme.typography.displayMedium, color = colors.onPrimaryContainer)
        }
        Spacer(Modifier.height(40.dp))
        Text("Take a breath", style = MaterialTheme.typography.headlineMedium, color = colors.onSurface)
        Spacer(Modifier.height(8.dp))
        Text("Do you still want to open $label?", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        if (opensToday > 0) Text(if (opensToday == 1) "Opened once today" else "Opened $opensToday times today", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, color = colors.primary)
        Spacer(Modifier.height(48.dp))
        Button(onCancel, Modifier.fillMaxWidth()) { Text("Not now") }
        TextButton(onOpen, Modifier.fillMaxWidth(), enabled = left == 0) { Text(if (left > 0) "Open in ${left}s" else "Open $label") }
    }
}
