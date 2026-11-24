package dev.capsule.island.ui.activities

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import dev.capsule.island.core.LocalPt
import dev.capsule.island.ui.kit.W
import dev.capsule.island.ui.kit.kitStyle
import kotlinx.coroutines.delay

/** A compact slot: [w] points wide, drawn by [content]. [round] content sits concentric with the pill's end. */
class Slot(val w: Float, val round: Boolean, val content: @Composable () -> Unit)

/**
 * The expanded view: the kit's canvas, [width] x [height] points. [stretch] canvases scale to the
 * full expanded width (374.34 on the reference phone); others draw at the kit's size, centred.
 */
class Expanded(val width: Float, val height: Float, val stretch: Boolean = true, val radius: Float? = null, val content: @Composable () -> Unit)

class ActivityUi(
    val leading: Slot?,
    val trailing: Slot?,
    /** Drawn in a 36.85 pt circle: the detached bubble, or the pill when content won't fit. */
    val minimal: @Composable () -> Unit,
    val expanded: Expanded,
)

/** Measures kit text in points, under the current [LocalPt]. */
class TextWidth(private val measurer: TextMeasurer, private val pxPerPt: Float, private val styleFor: (Float, W, Boolean) -> androidx.compose.ui.text.TextStyle) {
    fun of(text: String, size: Float, weight: W, tabular: Boolean = false): Float =
        measurer.measure(text, styleFor(size, weight, tabular), maxLines = 1).size.width / pxPerPt
}

@Composable
fun rememberTextWidth(): TextWidth {
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val pxPerPt = LocalPt.current * LocalDensity.current.density
    val base = kitStyle(14f, W.Regular, androidx.compose.ui.graphics.Color.White)
    val density = LocalDensity.current
    val pt = LocalPt.current
    return remember(measurer, pxPerPt) {
        TextWidth(measurer, pxPerPt) { size, weight, tabular ->
            base.copy(
                fontSize = with(density) { (size * pt).dp.toSp() },
                fontWeight = weight.weight,
                fontFeatureSettings = if (tabular) "tnum" else null,
            )
        }
    }
}

private val Float.dp get() = androidx.compose.ui.unit.Dp(this)

/**
 * elapsedRealtime, refreshed every [periodMs] on a frame boundary. Waits for a frame each tick,
 * so it stops with the screen and costs nothing while the display is off.
 */
@Composable
fun rememberNow(periodMs: Long = 1000L): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(periodMs) {
        while (true) {
            withFrameNanos { }
            now = SystemClock.elapsedRealtime()
            delay(periodMs - now % periodMs)
        }
    }
    return now
}

fun clock(ms: Long, hours: Boolean = false): String {
    val total = (ms.coerceAtLeast(0) + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0 || hours) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun elapsed(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
