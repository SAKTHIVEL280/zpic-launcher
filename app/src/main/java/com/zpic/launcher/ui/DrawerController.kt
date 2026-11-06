package com.zpic.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One value, 0 = home to 1 = drawer, shared by finger, spring, predictive back and the home button. */
@Stable
class DrawerController(private val scope: CoroutineScope) {
    val progress = Animatable(0f)
    var spring: SpringSpec<Float> = SpringSpec()
    var travel = 1500f

    val value: Float get() = progress.value
    val isOpen: Boolean get() = progress.targetValue > 0.5f
    val isShown: Boolean get() = progress.value > 0.001f || progress.isRunning

    fun drag(deltaPx: Float) {
        scope.launch { progress.snapTo((progress.value + deltaPx / travel).coerceIn(0f, 1f)) }
    }

    fun set(value: Float) { scope.launch { progress.snapTo(value.coerceIn(0f, 1f)) } }

    /** velocity is px/s with up positive. */
    fun settle(velocity: Float) {
        val open = if (kotlin.math.abs(velocity) > 700f) velocity > 0 else progress.value > 0.4f
        animateTo(if (open) 1f else 0f, velocity / travel)
    }

    fun open() = animateTo(1f, 0f)
    fun close() = animateTo(0f, 0f)
    fun snapClosed() { scope.launch { progress.snapTo(0f) } }

    private fun animateTo(target: Float, velocity: Float) {
        scope.launch { progress.animateTo(target, spring, velocity) }
    }
}
