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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zpic.launcher.data.ClockStyle
import com.zpic.launcher.data.ClockTint
import com.zpic.launcher.data.ClockWeight
import com.zpic.launcher.data.DateStyle
import com.zpic.launcher.data.HourFormat
import com.zpic.launcher.data.Settings
import com.zpic.launcher.system.SystemActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** While arranging the home screen, tapping must never open anything. */
val LocalHomeEditing = androidx.compose.runtime.compositionLocalOf { false }

@Composable
private fun rememberNow(resumeTick: Int): Calendar {
    val now by produceState(Calendar.getInstance(), resumeTick) {
        while (true) {
            val c = Calendar.getInstance()
            value = c
            delay(60_000L - (c.timeInMillis % 60_000L))
        }
    }
    return now
}

private fun ClockWeight.font() = when (this) {
    ClockWeight.Thin -> FontWeight.Thin
    ClockWeight.Light -> FontWeight.Light
    ClockWeight.Regular -> FontWeight.Normal
    ClockWeight.Medium -> FontWeight.Medium
    ClockWeight.Bold -> FontWeight.Bold
}

private val dotDigits = mapOf(
    '0' to "01110 10001 10011 10101 11001 10001 01110", '1' to "00100 01100 00100 00100 00100 00100 01110",
    '2' to "01110 10001 00001 00110 01000 10000 11111", '3' to "11111 00010 00100 00010 00001 10001 01110",
    '4' to "00010 00110 01010 10010 11111 00010 00010", '5' to "11111 10000 11110 00001 00001 10001 01110",
    '6' to "00110 01000 10000 11110 10001 10001 01110", '7' to "11111 00001 00010 00100 01000 01000 01000",
    '8' to "01110 10001 10001 01110 10001 10001 01110", '9' to "01110 10001 10001 01111 00001 00010 01100",
)

@Composable
private fun DotDigit(ch: Char, dot: Dp, ink: Color) {
    val rows = dotDigits[ch]?.split(' ').orEmpty()
    Canvas(Modifier.size(dot * 5.6f, dot * 7)) {
        val step = dot.toPx()
        rows.forEachIndexed { y, row -> row.forEachIndexed { x, bit ->
            if (bit == '1') drawCircle(ink, step * .42f, Offset(step * (x + .5f), step * (y + .5f)))
            else drawCircle(ink.copy(alpha = ink.alpha * .1f), step * .16f, Offset(step * (x + .5f), step * (y + .5f)))
        } }
    }
}

@Composable
private fun Rolling(text: String, cell: @Composable (Char) -> Unit) {
    val motion = LocalMotion.current
    Row {
        text.forEachIndexed { i, ch ->
            AnimatedContent(
                ch,
                transitionSpec = {
                    (slideInVertically(motion.spatial()) { it / 2 } + fadeIn(motion.effects()) + scaleIn(motion.spatial(), .8f)) togetherWith
                        (slideOutVertically(motion.spatial()) { -it / 2 } + fadeOut(motion.effects()) + scaleOut(motion.effects(), .8f))
                },
                label = "digit$i",
            ) { cell(it) }
        }
    }
}

@Composable
private fun Rolling(text: String, style: TextStyle) = Rolling(text) { Text(it.toString(), style = style) }

@Composable
private fun AnalogFace(now: Calendar, size: Dp, ink: Color, accent: Color) {
    val motion = LocalMotion.current
    // Absolute minutes, so the hand never spins backwards at the top of the hour.
    val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val hand by animateFloatAsState(minutes.toFloat(), motion.spatial(), label = "hand")
    Canvas(Modifier.size(size)) {
        val c = center
        val r = this.size.minDimension / 2
        fun at(deg: Float, len: Float) = Offset(c.x + cos((deg - 90f) * PI / 180).toFloat() * len, c.y + sin((deg - 90f) * PI / 180).toFloat() * len)
        for (i in 0 until 12) drawCircle(ink.copy(alpha = if (i % 3 == 0) 1f else .5f), r * (if (i % 3 == 0) .035f else .02f), at(i * 30f, r * .92f))
        drawLine(ink, c, at(hand / 2f, r * .5f), r * .075f, StrokeCap.Round)
        drawLine(accent, c, at(hand * 6f, r * .76f), r * .045f, StrokeCap.Round)
        drawCircle(accent, r * .06f, c)
    }
}

private val ones = listOf("twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven")
private val tens = listOf("", "ten", "twenty", "thirty", "forty", "fifty")
private val teens = listOf("ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")

private fun words(h: Int, m: Int): List<String> {
    val hour = ones[h % 12]
    val minute = when {
        m == 0 -> "o'clock"
        m < 10 -> "oh ${ones[m]}"
        m < 20 -> teens[m - 10]
        m % 10 == 0 -> tens[m / 10]
        else -> "${tens[m / 10]} ${ones[m % 10]}"
    }
    return listOf(hour, minute)
}

@Composable
fun ClockFace(s: Settings, ink: Color, resumeTick: Int, horizontal: Alignment.Horizontal) {
    val context = LocalContext.current
    val now = rememberNow(resumeTick)
    val colors = MaterialTheme.colorScheme
    val is24 = when (s.hourFormat) {
        HourFormat.System -> android.text.format.DateFormat.is24HourFormat(context)
        HourFormat.H12 -> false
        HourFormat.H24 -> true
    }
    val h24 = now.get(Calendar.HOUR_OF_DAY)
    val hourValue = if (is24) h24 else (h24 % 12).let { if (it == 0) 12 else it }
    val hour = if (is24 || s.leadingZero) "%02d".format(hourValue) else hourValue.toString()
    val minute = "%02d".format(now.get(Calendar.MINUTE))
    val ampm = if (!is24 && s.showAmPm) (if (h24 < 12) " AM" else " PM") else ""

    fun readable(c: Color) = if (ink == Color.White) lerp(c, Color.White, .5f) else lerp(c, Color.Black, .25f)
    val seed = LocalWallpaperSeed.current
    // Follows the wallpaper's own colour, lifted until it reads against that wallpaper.
    val fromWallpaper = seed?.let { argb ->
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)
        Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1].coerceIn(.35f, .85f), if (ink == Color.White) .8f else .3f)))
    } ?: readable(colors.primary)
    val face = when (s.clockTint) {
        ClockTint.Wallpaper -> fromWallpaper
        ClockTint.Ink -> ink
        ClockTint.Primary -> readable(colors.primary)
        ClockTint.Secondary -> readable(colors.secondary)
        ClockTint.Tertiary -> readable(colors.tertiary)
    }
    val accent = readable(colors.primary)
    val base = inkStyle(ink).copy(color = face)
    val display = MaterialTheme.typography.displayLarge
    fun big(size: Int, weight: FontWeight, family: FontFamily? = null) = base.merge(display).copy(
        fontSize = size.sp, lineHeight = (size * .98f).sp,
        fontWeight = s.clockWeight?.font() ?: weight, fontFamily = family ?: display.fontFamily,
    )
    val pattern = when (s.dateStyle) { DateStyle.Short -> "EEEMMMd"; DateStyle.Long -> "EEEEMMMMd"; DateStyle.Numeric -> "ddMMyyyy" }
    val date = remember(now.get(Calendar.DAY_OF_YEAR), s.dateStyle) {
        android.text.format.DateFormat.format(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), pattern), now).toString()
    }
    val alarm = remember(now, s.showAlarm) { if (s.showAlarm) SystemActions.nextAlarm(context) else null }
    val editing = LocalHomeEditing.current
    val open = Modifier.clickable(remember { MutableInteractionSource() }, null, enabled = !editing) { SystemActions.openClock(context) }
    val dateLine: @Composable () -> Unit = {
        if (s.showDate) Text(
            date, Modifier.padding(vertical = 4.dp).clickable(remember { MutableInteractionSource() }, null, enabled = !editing) { SystemActions.openCalendar(context) },
            style = inkStyle(ink).merge(MaterialTheme.typography.titleMedium),
        )
    }
    val dateFirst = s.dateAbove || s.clock == ClockStyle.Ios || s.clock == ClockStyle.OneUi

    Column(horizontalAlignment = horizontal) {
        if (dateFirst) dateLine()
        Box(open) {
            when (s.clock) {
                ClockStyle.Stacked -> Column(horizontalAlignment = horizontal) {
                    val b = big(104, FontWeight.Light)
                    Rolling(hour, b)
                    Rolling(minute, b.copy(color = face.copy(alpha = .78f)))
                }
                ClockStyle.Inline -> Rolling("$hour:$minute$ampm", big(72, FontWeight.Light))
                ClockStyle.OneUi -> Rolling("$hour:$minute", big(84, FontWeight.Thin))
                ClockStyle.Ios -> Rolling("$hour:$minute", big(92, FontWeight.SemiBold))
                ClockStyle.Dots -> {
                    val dot = 9.dp
                    Column(verticalArrangement = Arrangement.spacedBy(dot)) {
                        Rolling(hour.padStart(2, '0')) { DotDigit(it, dot, face) }
                        Rolling(minute) { DotDigit(it, dot, face.copy(alpha = face.alpha * .72f)) }
                    }
                }
                ClockStyle.Accent -> Row {
                    Rolling(hour, big(88, FontWeight.Medium).copy(color = accent))
                    Rolling(minute, big(88, FontWeight.Light))
                }
                ClockStyle.Hyper -> Rolling("$hour$minute", big(110, FontWeight.Bold, FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))))
                ClockStyle.Cards -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val b = big(64, FontWeight.Medium).copy(color = colors.onPrimaryContainer, shadow = null)
                    listOf(hour.padStart(2, '0'), minute).forEach { part ->
                        Box(Modifier.clip(RoundedCornerShape(28.dp)).background(colors.primaryContainer).padding(horizontal = 18.dp, vertical = 10.dp)) { Rolling(part, b) }
                    }
                }
                ClockStyle.Analog -> AnalogFace(now, 190.dp, face, accent)
                ClockStyle.Words -> Column(horizontalAlignment = horizontal) {
                    words(h24, now.get(Calendar.MINUTE)).forEachIndexed { i, w ->
                        Text(w, style = big(44, if (i == 0) FontWeight.Medium else FontWeight.Light).copy(color = if (i == 0) face else face.copy(alpha = .75f)))
                    }
                }
                ClockStyle.Small -> Rolling("$hour:$minute$ampm", base.merge(MaterialTheme.typography.headlineMedium).copy(fontSize = 28.sp))
                ClockStyle.Hidden -> Unit
            }
        }
        if (!dateFirst) dateLine()
        if (s.calendarGlance) {
            val event by produceState<String?>(null, now) { value = withContext(Dispatchers.IO) { nextEvent(context) } }
            event?.let { Text(it, style = inkStyle(ink).merge(MaterialTheme.typography.bodyMedium).copy(color = ink.copy(alpha = .78f)), maxLines = 1) }
        }
        if (s.showBattery) {
            val level = remember(now) { context.getSystemService(android.os.BatteryManager::class.java).getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) }
            Text("Battery $level%", style = inkStyle(ink).merge(MaterialTheme.typography.bodyMedium).copy(color = ink.copy(alpha = .78f)))
        }
        if (alarm != null) Text(
            "Alarm " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(alarm)),
            style = inkStyle(ink).merge(MaterialTheme.typography.bodyMedium).copy(color = ink.copy(alpha = .78f)),
        )
    }
}
