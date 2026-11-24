package dev.capsule.island.ui.kit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import dev.capsule.island.core.LocalPt
import dev.capsule.island.core.pt
import kotlin.math.PI
import kotlin.math.sin

/** Draw in kit points: `u` is one point in pixels. */
@Composable
private fun KitCanvas(w: Float, h: Float, modifier: Modifier = Modifier, draw: DrawScope.(u: Float) -> Unit) {
    val ptDp = LocalPt.current
    Canvas(modifier.size(w.pt, h.pt)) { draw(ptDp * density) }
}

/** A frame-driven phase in [0, 1). Frames stop with the screen, so this costs nothing while off. */
@Composable
private fun phase(periodMs: Int, running: Boolean): State<Float> {
    if (!running) return remember { mutableFloatStateOf(0f) }
    val t = rememberInfiniteTransition(label = "phase")
    return t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "p")
}

/** Track plus progress arc, from the top, clockwise (battery, AirPods and transfer rings). */
@Composable
fun ProgressRing(size: Float, stroke: Float, color: Color, track: Color, fraction: Float, modifier: Modifier = Modifier) {
    KitCanvas(size, size, modifier) { u ->
        val sw = stroke * u
        val inset = sw / 2
        val box = Size(this.size.width - sw, this.size.height - sw)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), box, style = Stroke(sw))
        if (fraction > 0f) drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), box, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

/** The kit's battery: body at 40%, the fill, and the cap. */
@Composable
fun BatteryIndicator(level: Int, color: Color, modifier: Modifier = Modifier) {
    KitCanvas(34.9f, 16.8f, modifier) { u ->
        val body = RoundRect(0f, 0f, 32.3f * u, 16.8f * u, CornerRadius(5.2f * u))
        drawRoundRect(color.copy(alpha = 0.4f), Offset.Zero, Size(body.width, body.height), CornerRadius(5.2f * u))
        val fill = Path().apply { addRoundRect(body) }
        clipPath(fill) { drawRect(color, Offset.Zero, Size(32.3f * u * (level / 100f).coerceIn(0.04f, 1f), 16.8f * u)) }
        drawRoundRect(color.copy(alpha = 0.4f), Offset(33.2f * u, 5.6f * u), Size(1.7f * u, 5.6f * u), CornerRadius(1f * u))
    }
}

private val callBack = floatArrayOf(3f, 8f, 12f, 13f, 10f, 8f, 7f, 9f, 13f, 10f, 14f, 12f, 10f, 6f)
private val callFront = floatArrayOf(1.5f, 3f, 5f, 6f, 7f, 8f, 7f, 3f, 5f, 6f, 6f, 5f, 3f, 2f)

/** The call waveform: green and orange levels with the white voice line over them. */
@Composable
fun CallWave(live: Boolean, modifier: Modifier = Modifier) {
    val p by phase(1400, live)
    KitCanvas(34f, 14f, modifier) { u ->
        for (i in 0 until 14) {
            val wobble = if (live) 0.6f + 0.4f * sin((p * 2 * PI + i * 0.9).toFloat()) else 1f
            val hb = callBack[i] * wobble
            val x = i * 2.5f * u
            val c = if (i < 7) KitColor.Green else KitColor.Orange
            drawRoundRect(c, Offset(x, (7f - hb / 2) * u), Size(1.5f * u, hb * u), CornerRadius(3f * u))
            val hf = minOf(callFront[i] * wobble, hb)
            drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(x, (7f - hf / 2) * u), Size(1.5f * u, hf * u), CornerRadius(3f * u))
        }
    }
}

/** Now-playing bars in the album's colours. Static glyph when paused, as on iOS. */
@Composable
fun MusicBars(brush: Brush, playing: Boolean, size: Float = 18f, modifier: Modifier = Modifier) {
    if (!playing) {
        Glyph(Sf.Waveform, size, Color.White, modifier, brush = brush)
        return
    }
    val p by phase(1100, true)
    val k = size / 18f
    KitCanvas(17f * k, 18.4f * k, modifier) { u ->
        val n = 6
        val bw = 2f * k * u
        val gap = (17f * k * u - n * bw) / (n - 1)
        val path = Path()
        for (i in 0 until n) {
            val s = sin((p * 2 * PI * (1 + i % 3) + i * 1.7).toFloat())
            val h = (0.35f + 0.65f * (0.5f + 0.5f * s)) * 18.4f * k * u
            path.addRoundRect(RoundRect(Rect(i * (bw + gap), (this.size.height - h) / 2, i * (bw + gap) + bw, (this.size.height + h) / 2), CornerRadius(bw / 2)))
        }
        drawPath(path, brush)
    }
}

private val voiceCompact = floatArrayOf(9.8f, 13.4f, 16.5f, 9.2f, 11.6f, 7.3f)
private val voiceExpanded = floatArrayOf(25f, 20f, 16f, 20f, 25f, 20f, 14f, 10f, 16f, 22f, 27f, 15f, 19f, 12f)

/** Recorded level history (red) leading into the dim future dots. */
@Composable
fun VoiceBars(expanded: Boolean, live: Boolean, modifier: Modifier = Modifier) {
    val p by phase(900, live)
    val levels = if (expanded) voiceExpanded else voiceCompact
    val bw = if (expanded) 3f else 1.8f
    val step = if (expanded) 5f else 3.06f
    val height = if (expanded) 27f else 16.5f
    val dots = if (expanded) 11 else 7
    val total = levels.size + dots
    KitCanvas(step * (total - 1) + bw, height, modifier) { u ->
        val r = CornerRadius((if (expanded) 4f else 2.4f) * u)
        for (i in 0 until total) {
            val x = i * step * u
            if (i < levels.size) {
                val wobble = if (live) 0.75f + 0.25f * sin((p * 2 * PI + i * 1.3).toFloat()) else 1f
                val h = levels[i] * wobble
                val alpha = if (i == levels.size - 1) 0.4f else 1f
                drawRoundRect(KitColor.Red.copy(alpha = alpha), Offset(x, (height - h) / 2 * u), Size(bw * u, h * u), r)
            } else {
                drawRoundRect(KitColor.Dim.copy(alpha = 0.4f), Offset(x, (height - bw) / 2 * u), Size(bw * u, bw * u), r)
            }
        }
    }
}

/** AirDrop's three rings and dot, open at the bottom. */
@Composable
fun AirDropRings(size: Float, stroke: Float, dot: Color = Color(0xFF1A73B7), modifier: Modifier = Modifier) {
    KitCanvas(size, size, modifier) { u ->
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val sw = stroke * u
        for (f in floatArrayOf(1f, 0.7225f, 0.445f)) {
            val r = size * f / 2 * u - sw / 2
            drawArc(KitColor.Cyan, 120f, 303f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(sw, cap = StrokeCap.Round))
        }
        drawCircle(dot, size * 0.168f / 2 * u, c)
    }
}

/** A transfer's ring with the stop square (AirDrop trailing). */
@Composable
fun StopRing(size: Float, stroke: Float, progress: Float, square: Float, squareRadius: Float, color: Color = KitColor.Cyan, modifier: Modifier = Modifier) {
    KitCanvas(size, size, modifier) { u ->
        val sw = stroke * u
        val box = Size(this.size.width - sw, this.size.height - sw)
        drawArc(color.copy(alpha = 0.5f), 0f, 360f, false, Offset(sw / 2, sw / 2), box, style = Stroke(sw))
        drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(sw / 2, sw / 2), box, style = Stroke(sw, cap = StrokeCap.Round))
        val s = square * u
        drawRoundRect(color, Offset((this.size.width - s) / 2, (this.size.height - s) / 2), Size(s, s), CornerRadius(squareRadius * u))
    }
}

/** The recording stop button: white ring, red rounded square. */
@Composable
fun StopButton(modifier: Modifier = Modifier) {
    KitCanvas(54f, 54f, modifier) { u ->
        val sw = 2.5f * u
        drawCircle(Color.White, 26.5f * u - sw / 2, Offset(27f * u, 27f * u), style = Stroke(sw))
        drawRoundRect(KitColor.Red, Offset(15f * u, 15f * u), Size(24f * u, 24f * u), CornerRadius(4f * u))
    }
}

/**
 * The padlock, drawn from the kit's lock.fill and lock.open.fill outlines so the shackle can move:
 * the body is shared, and the open shackle is the closed one moved 7.63 pt right with its free leg
 * lifted clear of the body. [open] runs 0 (locked) to 1 (unlocked); the shackle lifts as it swings.
 */
@Composable
fun LockGlyph(open: Float, size: Float, color: Color, modifier: Modifier = Modifier) {
    val k = size / 18f
    // Box wide enough for the open glyph; the body stays at the left, as in the kit.
    KitCanvas(17.96f * k, 17.15f * k, modifier) { u ->
        val s = k * u
        val t = open.coerceIn(0f, 1f)
        val lift = sin(t * PI).toFloat() * 1.3f
        val dx = 7.63f * t
        val cx = (5.87f + dx) * s
        val outer = 4.474f
        val inner = 3.06f
        val sw = (outer - inner) * s
        val r = (outer + inner) / 2 * s
        val top = (outer - lift) * s
        val leftLeg = 7.6f * s
        val rightLeg = (7.6f - (7.6f - 6.0f) * t) * s
        val shackle = Path().apply {
            moveTo(cx - r, leftLeg)
            lineTo(cx - r, top)
            arcTo(Rect(cx - r, top - r, cx + r, top + r), 180f, 180f, false)
            lineTo(cx + r, rightLeg)
        }
        drawPath(shackle, color, style = Stroke(sw, cap = StrokeCap.Round))
        drawRoundRect(color, Offset(0f, 7.12f * s), Size(11.74f * s, 10.03f * s), CornerRadius(1.69f * s))
    }
}

/** The satellite finder: compass ring, green bearing and the satellite overhead. */
@Composable
fun SatelliteIcon(size: Float, innerRing: Boolean, modifier: Modifier = Modifier) {
    KitCanvas(size, size, modifier) { u ->
        val k = size / 28.165f * u
        withTransform({ scale(1f, 1f, Offset.Zero) }) {
            val c = Offset(14f * k, 13.16f * k)
            val wedge = Path().apply {
                moveTo(c.x, c.y + 0.9f * k)
                arcTo(Rect(c.x - 10f * k, c.y - 9f * k, c.x + 10f * k, c.y + 11f * k), 220f, 100f, false)
                close()
            }
            drawPath(wedge, KitColor.Green.copy(alpha = 0.2f))
            drawCircle(Color(0xFF3E4140), 10.7f * k, c, style = Stroke(1.2f * k))
            if (innerRing) drawCircle(Color(0xFF3E4140), 6.47f * k, c, style = Stroke(0.73f * k))
            drawCircle(KitColor.Green, 3.25f * k, c)
            val sat = Offset(14f * k, 4.29f * k)
            drawLine(Color.White, Offset(sat.x - 5.75f * k, sat.y), Offset(sat.x - 3.56f * k, sat.y), 0.84f * k, StrokeCap.Round)
            drawLine(Color.White, Offset(sat.x + 3.7f * k, sat.y), Offset(sat.x + 5.88f * k, sat.y), 0.84f * k, StrokeCap.Round)
            drawCircle(Color.White, 2.5f * k, sat)
        }
    }
}
