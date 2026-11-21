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

    /** Finger distance in px for a full open. Matches how far the sheet itself moves, so it stays under the finger. */
    var travel = 1500f

    /**
     * Where the finger has put the sheet. Drag events arrive faster than snapTo runs, and each snapTo cancels
     * the one before it, so deltas are summed here synchronously and never dropped.
     */
    private var dragged: Float? = null

    val value: Float get() = progress.value
    val isOpen: Boolean get() = progress.targetValue > 0.5f
    val isShown: Boolean get() = progress.value > 0.001f || progress.isRunning || (dragged ?: 0f) > 0f

    fun drag(deltaPx: Float) {
        val next = ((dragged ?: progress.value) + deltaPx / travel).coerceIn(0f, 1f)
        dragged = next
        scope.launch { progress.snapTo(next) }
    }

    fun set(value: Float) {
        val next = value.coerceIn(0f, 1f)
        dragged = next
        scope.launch { progress.snapTo(next) }
    }

    /** velocity is px/s with up positive. */
    fun settle(velocity: Float) {
        val at = dragged ?: progress.value
        val open = if (kotlin.math.abs(velocity) > 500f) velocity > 0 else at > 0.3f
        animateTo(if (open) 1f else 0f, velocity / travel)
    }

    fun open() = animateTo(1f, 0f)
    fun close() = animateTo(0f, 0f)
    fun snapClosed() { dragged = null; scope.launch { progress.snapTo(0f) } }

    private fun animateTo(target: Float, velocity: Float) {
        dragged = null
        scope.launch { progress.animateTo(target, spring, velocity) }
    }
}
