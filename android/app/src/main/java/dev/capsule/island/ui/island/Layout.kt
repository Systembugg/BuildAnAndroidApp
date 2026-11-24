package dev.capsule.island.ui.island

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import dev.capsule.island.core.Metrics
import dev.capsule.island.core.Transition
import dev.capsule.island.engine.IslandActivity
import dev.capsule.island.engine.IslandState
import dev.capsule.island.engine.Payload
import dev.capsule.island.engine.Presentation
import dev.capsule.island.ui.activities.ActivityUi
import kotlin.math.max
import kotlin.math.min

/** A rectangle in screen points. */
data class Box4(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w get() = r - l
    val h get() = b - t
    val cx get() = (l + r) / 2
    val cy get() = (t + b) / 2
    fun union(o: Box4?) = if (o == null) this else Box4(min(l, o.l), min(t, o.t), max(r, o.r), max(b, o.b))
}

/**
 * Where the island sits on this screen. [ptPx] pixels per point; [centerX] and [top] in points
 * from the screen's top-left (the idle pill wraps the camera cutout).
 */
data class IslandFrame(val ptPx: Float, val screenWidth: Float, val centerX: Float, val top: Float) {
    val idle get() = Box4(centerX - Metrics.IDLE_WIDTH / 2, top, centerX + Metrics.IDLE_WIDTH / 2, top + Metrics.IDLE_HEIGHT)
}

/** One piece of content: laid out in [frame] for the resting shape [rest], following the live shape. */
class LayerSpec(
    val key: String,
    val rest: Box4,
    val frame: Box4,
    val align: Alignment,
    val onBubble: Boolean,
    /** Points-to-dp for this layer (the expanded canvas scales the kit's 367 pt to the island). */
    val ptScale: Float,
    val content: @Composable () -> Unit,
)

class Target(
    val key: String,
    val presentation: Presentation,
    val main: Box4,
    /** Corner radius cap: the pill's half height for capsules, the card radius when expanded. */
    val radius: Float,
    val bubble: Box4?,
    val expanded: Boolean,
    val layers: List<LayerSpec>,
)

private fun variant(a: IslandActivity): String = when (val p = a.payload) {
    is Payload.Ringer -> "s${p.silent}"
    is Payload.Battery -> p.kind.name
    is Payload.Music -> "r${p.remote}"
    is Payload.Call -> "i${p.incoming}v${p.video}"
    is Payload.AirPods -> "m${p.moved}"
    else -> ""
} + a.payload::class.simpleName

/** Lays out the island for [state]: islandcn's rules for the shape, the kit's content in it. */
fun layout(state: IslandState, frame: IslandFrame, uis: Map<String, ActivityUi>): Target {
    val pres = state.presentation
    val idle = frame.idle
    val sensorL = idle.l
    val sensorR = idle.r
    val top = frame.top
    val h = Metrics.COMPACT_HEIGHT
    return when (pres) {
        Presentation.Idle -> Target("idle", pres, idle, idle.h / 2, null, false, emptyList())

        is Presentation.Compact -> {
            val a = pres.activity
            val ui = uis.getValue(a.id)
            val lead = ui.leading
            val trail = ui.trailing
            fun inset(round: Boolean, w: Float) = if (round) max(4f, Metrics.END_CENTER - w / 2) else Metrics.TEXT_INSET
            val li = lead?.let { inset(it.round, it.w) } ?: 0f
            val ti = trail?.let { inset(it.round, it.w) } ?: 0f
            var eL = if (lead != null) li + min(lead.w, Metrics.COMPACT_MAX_SLOT) + Metrics.SENSOR_GAP else 0f
            var eT = if (trail != null) ti + min(trail.w, Metrics.COMPACT_MAX_SLOT) + Metrics.SENSOR_GAP else eL
            if (lead == null) eL = eT
            if (lead != null && trail != null) {
                eL = max(eL, Metrics.COMPACT_BALANCE * eT)
                eT = max(eT, Metrics.COMPACT_BALANCE * eL)
            }
            val main = Box4(sensorL - eL, top, sensorR + eT, top + h)
            val v = variant(a)
            val layers = buildList {
                if (lead != null) add(LayerSpec("${a.id}:c:lead:$v", main, Box4(main.l + li, top, main.l + li + lead.w, top + h), Alignment.CenterStart, false, 1f, lead.content))
                if (trail != null) add(LayerSpec("${a.id}:c:trail:$v", main, Box4(main.r - ti - trail.w, top, main.r - ti, top + h), Alignment.CenterEnd, false, 1f, trail.content))
            }
            Target("c:${a.id}:$v:${main.l.r1()}:${main.r.r1()}", pres, main, h / 2, null, false, layers)
        }

        is Presentation.Split -> {
            val p = pres.primary
            val s = pres.secondary
            val pu = uis.getValue(p.id)
            val su = uis.getValue(s.id)
            val lead = pu.leading
            var trail = pu.trailing
            fun span(t: Boolean) = Metrics.SPLIT_OUTER + (lead?.w ?: 0f) + (if (t && trail != null) Metrics.SPLIT_SLOT_GAP + trail!!.w else 0f) + Metrics.SPLIT_INNER
            if (span(true) + Metrics.IDLE_WIDTH + Metrics.SPLIT_GAP + h > Metrics.SPLIT_MAX_SPAN) trail = null
            val left = span(true)
            val main = Box4(sensorL - left, top, sensorR, top + h)
            val bubble = Box4(sensorR + Metrics.SPLIT_GAP, top, sensorR + Metrics.SPLIT_GAP + h, top + h)
            val v = variant(p)
            val layers = buildList {
                var x = main.l + Metrics.SPLIT_OUTER
                if (lead != null) {
                    add(LayerSpec("${p.id}:s:lead:$v", main, Box4(x, top, x + lead.w, top + h), Alignment.Center, false, 1f, lead.content))
                    x += lead.w + Metrics.SPLIT_SLOT_GAP
                }
                val t = trail
                if (t != null) add(LayerSpec("${p.id}:s:trail:$v", main, Box4(x, top, x + t.w, top + h), Alignment.CenterStart, false, 1f, t.content))
                add(LayerSpec("${s.id}:b:${variant(s)}", bubble, bubble, Alignment.Center, true, 1f, su.minimal))
            }
            Target("s:${p.id}:${s.id}:${main.l.r1()}", pres, main, h / 2, bubble, false, layers)
        }

        is Presentation.Expanded -> {
            val a = pres.activity
            val ex = uis.getValue(a.id).expanded
            val fullW = Metrics.expandedWidth(frame.screenWidth)
            val k = if (ex.stretch) fullW / ex.width else 1f
            val w = ex.width * k
            val hh = ex.height * k
            val cx = if (ex.stretch) frame.screenWidth / 2 else frame.centerX
            val t = top - (Metrics.TOP - Metrics.EXPANDED_TOP)
            val main = Box4(cx - w / 2, t, cx + w / 2, t + hh)
            val radius = min(ex.radius ?: Metrics.EXPANDED_RADIUS, hh / 2)
            val layer = LayerSpec("${a.id}:x:${variant(a)}", main, main, Alignment.TopStart, false, k, ex.content)
            Target("x:${a.id}:${variant(a)}:${hh.r1()}", pres, main, radius, null, true, listOf(layer))
        }
    }
}

private fun Float.r1() = (this * 10).toInt()

/** Which motion takes the island from [prev] to [next] (islandcn `ShapeTransition`). */
fun transitionFor(prev: Presentation?, next: Presentation, state: IslandState, alertEnding: Boolean): Transition = when {
    prev == null -> Transition.Resize
    prev is Presentation.Idle && next is Presentation.Idle -> Transition.Resize
    prev is Presentation.Idle && next is Presentation.Expanded -> Transition.AppearExpand
    prev is Presentation.Idle -> Transition.Appear
    next is Presentation.Idle -> Transition.Dismiss
    prev is Presentation.Compact && next is Presentation.Expanded ->
        if (prev.activity.id != next.activity.id) Transition.Swap
        else if (state.pressExpanded) Transition.PressExpand else Transition.Expand
    prev is Presentation.Split && next is Presentation.Expanded -> Transition.Swap
    prev is Presentation.Expanded && next is Presentation.Expanded ->
        if (prev.activity.id != next.activity.id) Transition.Swap else Transition.Resize
    prev is Presentation.Expanded && next is Presentation.Compact -> if (alertEnding) Transition.AlertCollapse else Transition.Collapse
    prev is Presentation.Expanded && next is Presentation.Split -> Transition.CollapseSplit
    prev is Presentation.Compact && next is Presentation.Split -> Transition.Split
    prev is Presentation.Split && next is Presentation.Compact -> Transition.Unsplit
    else -> Transition.Resize
}
