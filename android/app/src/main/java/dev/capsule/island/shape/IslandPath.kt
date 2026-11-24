package dev.capsule.island.shape

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * Path builders for the island outline, ported from islandcn's `path.ts`.
 *
 * Corners are continuous: a circular arc of radius r with a cubic on each side that eases the
 * curvature in from the straight edge. Each edge takes only the smoothing it has room for, so a
 * capsule keeps its height with round ends and soft shoulders.
 */
object IslandPath {

    private class EdgeEase(val p: Float, val a: Float, val b: Float, val along: Float, val across: Float)

    private fun edgeEase(r: Float, smoothing: Float, room: Float): EdgeEase {
        val s = max(0f, min(smoothing, room / r - 1f))
        val angle = (PI / 4 * s).toFloat()
        val p = (1 + s) * r
        val along = r * (1 - sin(angle))
        val across = r * (1 - cos(angle))
        val c = r * tan(angle / 2) * cos(angle)
        val b = max(0f, (p - c - along) / 3f)
        return EdgeEase(p, 2 * b, b, along, across)
    }

    /** Adds a continuous-corner rectangle to [path], clockwise from the top edge. */
    fun addContinuousRect(path: Path, x: Float, y: Float, width: Float, height: Float, radius: Float, smoothing: Float) {
        val w = max(0f, width)
        val h = max(0f, height)
        if (w <= 0f || h <= 0f) return
        val r = max(0f, min(radius, min(w / 2, h / 2)))
        if (r < 1e-3f) {
            path.addRect(Rect(x, y, x + w, y + h))
            return
        }
        val across = edgeEase(r, smoothing, w / 2)
        val down = edgeEase(r, smoothing, h / 2)
        val right = x + w
        val bottom = y + h
        path.moveTo(x + across.p, y)
        corner(path, right, y, -1f, 0f, 0f, 1f, across, down, r)
        corner(path, right, bottom, 0f, -1f, -1f, 0f, down, across, r)
        corner(path, x, bottom, 1f, 0f, 0f, -1f, across, down, r)
        corner(path, x, y, 0f, 1f, 1f, 0f, down, across, r)
        path.close()
    }

    // (kx, ky) is the corner; u points back along the arriving edge, v along the leaving edge.
    private fun corner(
        path: Path, kx: Float, ky: Float,
        ux: Float, uy: Float, vx: Float, vy: Float,
        inc: EdgeEase, out: EdgeEase, r: Float,
    ) {
        fun px(du: Float, dv: Float) = kx + ux * du + vx * dv
        fun py(du: Float, dv: Float) = ky + uy * du + vy * dv
        path.lineTo(px(inc.p, 0f), py(inc.p, 0f))
        val sx = px(inc.along, inc.across)
        val sy = py(inc.along, inc.across)
        path.cubicTo(
            px(inc.p - inc.a, 0f), py(inc.p - inc.a, 0f),
            px(inc.p - inc.a - inc.b, 0f), py(inc.p - inc.a - inc.b, 0f),
            sx, sy,
        )
        // The arc's centre sits r in from both edges.
        val cx = kx + ux * r + vx * r
        val cy = ky + uy * r + vy * r
        val ex = kx + vx * out.along + ux * out.across
        val ey = ky + vy * out.along + uy * out.across
        val start = Math.toDegrees(atan2((sy - cy).toDouble(), (sx - cx).toDouble())).toFloat()
        val end = Math.toDegrees(atan2((ey - cy).toDouble(), (ex - cx).toDouble())).toFloat()
        var sweep = end - start
        while (sweep < 0f) sweep += 360f
        while (sweep > 180f) sweep -= 360f
        if (abs(sweep) > 0.01f) {
            path.arcTo(Rect(cx - r, cy - r, cx + r, cy + r), start, sweep, false)
        }
        path.cubicTo(
            kx + vx * (out.p - out.a - out.b), ky + vy * (out.p - out.a - out.b),
            kx + vx * (out.p - out.a), ky + vy * (out.p - out.a),
            kx + vx * out.p, ky + vy * out.p,
        )
    }

    private fun polar(cx: Float, cy: Float, angle: Float, length: Float) =
        Offset(cx + length * cos(angle), cy + length * sin(angle))

    /**
     * The gooey neck between two circles (a metaball bridge). Filled with both circles the union
     * reads as one liquid shape; the neck thins to nothing as the gap reaches [maxGap], so the
     * pinch-off has no pop. Returns false when there is no neck to draw.
     */
    fun addBridge(
        path: Path,
        ax: Float, ay: Float, ar: Float,
        bx: Float, by: Float, br: Float,
        handleSize: Float, spread: Float, maxGap: Float,
    ): Boolean {
        val dx = bx - ax
        val dy = by - ay
        val d = hypot(dx, dy)
        if (ar <= 0f || br <= 0f || d <= abs(ar - br)) return false
        val gap = d - ar - br
        if (gap >= maxGap) return false
        val reach = max(0f, gap) / maxGap
        val v = spread * (1 - reach).pow(1.5f)
        if (v <= 1e-4f) return false

        var u1 = 0f
        var u2 = 0f
        if (d < ar + br) {
            u1 = acos(((ar * ar + d * d - br * br) / (2 * ar * d)).coerceIn(-1f, 1f))
            u2 = acos(((br * br + d * d - ar * ar) / (2 * br * d)).coerceIn(-1f, 1f))
        }
        val between = atan2(dy, dx)
        val maxSpread = acos(((ar - br) / d).coerceIn(-1f, 1f))
        val pi = PI.toFloat()

        val a1 = between + u1 + (maxSpread - u1) * v
        val a2 = between - u1 - (maxSpread - u1) * v
        val a3 = between + pi - u2 - (pi - u2 - maxSpread) * v
        val a4 = between - pi + u2 + (pi - u2 - maxSpread) * v

        val p1 = polar(ax, ay, a1, ar)
        val p2 = polar(ax, ay, a2, ar)
        val p3 = polar(bx, by, a3, br)
        val p4 = polar(bx, by, a4, br)

        val total = ar + br
        val handle = min(v * handleSize, hypot(p1.x - p3.x, p1.y - p3.y) / total) * min(1f, d * 2 / total)
        val h1 = polar(p1.x, p1.y, a1 - pi / 2, ar * handle)
        val h2 = polar(p2.x, p2.y, a2 + pi / 2, ar * handle)
        val h3 = polar(p3.x, p3.y, a3 + pi / 2, br * handle)
        val h4 = polar(p4.x, p4.y, a4 - pi / 2, br * handle)

        path.moveTo(p1.x, p1.y)
        path.cubicTo(h1.x, h1.y, h3.x, h3.y, p3.x, p3.y)
        path.lineTo(p4.x, p4.y)
        path.cubicTo(h4.x, h4.y, h2.x, h2.y, p2.x, p2.y)
        path.close()
        return true
    }
}
