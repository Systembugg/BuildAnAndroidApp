package dev.capsule.island.ui.island

import android.graphics.Paint
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import dev.capsule.island.core.LocalPt
import dev.capsule.island.core.Metrics
import dev.capsule.island.core.Motion
import dev.capsule.island.core.Transition
import dev.capsule.island.engine.IslandActivity
import dev.capsule.island.engine.IslandState
import dev.capsule.island.engine.Payload
import dev.capsule.island.engine.Presentation
import dev.capsule.island.shape.IslandPath
import dev.capsule.island.ui.activities.rememberTextWidth
import dev.capsule.island.ui.activities.ui
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** What a gesture on the island asks for. The host maps these to the engine and settings. */
interface IslandGestures {
    fun tap(activity: IslandActivity?, onBubble: Boolean)
    fun longPress(activity: IslandActivity?, onBubble: Boolean)
    fun swipeUp()
    fun swipeSideways(activity: IslandActivity?, dx: Float)
}

private class LiveLayer(spec: LayerSpec, val blur: Float) {
    val key = spec.key
    /** The spec as last laid out; drawn only while leaving (live layers draw the current one). */
    var spec by mutableStateOf(spec)
    var leaving by mutableStateOf(false)
    val presence = Animatable(0f)
}

/**
 * The island: one black shape whose edges spring between presentations exactly as islandcn's
 * fitted motion does, with the kit's content in crossfading layers that follow the shape.
 *
 * Draws in screen coordinates offset by [origin] (the hosting window's top-left in pixels).
 * Reports the window rect it needs through [onBounds]: the union of where it is and where it is
 * going while it moves, then just the resting shape, so the window never blocks more than it must.
 */
@Composable
fun IslandSurface(
    state: IslandState,
    frame: IslandFrame,
    gestures: IslandGestures?,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    keyline: Boolean = false,
    hidden: Boolean = false,
    origin: () -> Offset = { Offset.Zero },
    onBounds: ((IntRect) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val ptDp = frame.ptPx / density.density
    CompositionLocalProvider(LocalPt provides ptDp) {
        SurfaceContent(state, frame, gestures, modifier, reducedMotion, keyline, hidden, origin, onBounds)
    }
}

@Composable
private fun SurfaceContent(
    state: IslandState,
    frame: IslandFrame,
    gestures: IslandGestures?,
    modifier: Modifier,
    reduced: Boolean,
    keyline: Boolean,
    hidden: Boolean,
    origin: () -> Offset,
    onBounds: ((IntRect) -> Unit)?,
) {
    val ptPx = frame.ptPx
    val tw = rememberTextWidth()

    // The padlock's shackle runs once the lock activity turns unlocked.
    val unlock = remember { Animatable(0f) }
    val unlocked = state.activities.any { (it.payload as? Payload.Lock)?.unlocked == true }
    LaunchedEffect(unlocked) {
        if (unlocked) {
            delay(120)
            unlock.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 260f))
        } else unlock.snapTo(0f)
    }

    val uis = state.activities.associate { it.id to ui(it, tw) { unlock.value } }
    val target = layout(state, frame, uis)

    // Live shape, in points.
    val l = remember { Animatable(target.main.l) }
    val t = remember { Animatable(target.main.t) }
    val r = remember { Animatable(target.main.r) }
    val b = remember { Animatable(target.main.b) }
    val cap = remember { Animatable(target.radius) }
    val bl = remember { Animatable(target.bubble?.l ?: 0f) }
    val br = remember { Animatable(target.bubble?.r ?: 0f) }
    var bubbleOn by remember { mutableStateOf(target.bubble != null) }
    val shadow = remember { Animatable(if (target.expanded) 1f else 0f) }
    val press = remember { Animatable(1f) }
    val alpha = remember { Animatable(if (hidden) 0f else 1f) }

    var prevPres by remember { mutableStateOf<Presentation?>(null) }
    var transition by remember { mutableStateOf(Transition.Resize) }
    val layers = remember { mutableStateListOf<LiveLayer>() }

    val currentTarget by rememberUpdatedState(target)
    val reportBounds by rememberUpdatedState(onBounds)

    fun boundsPx(box: Box4, expanded: Boolean): IntRect {
        val side = if (expanded) 20f else 14f
        val bottom = if (expanded) 46f else 10f
        return IntRect(
            ((box.l - side) * ptPx).toInt(), max(0f, (box.t - 8f) * ptPx).toInt(),
            ((box.r + side) * ptPx).toInt(), ((box.b + bottom) * ptPx).toInt(),
        )
    }

    fun liveBox() = Box4(l.value, t.value, r.value, b.value)
    fun liveBubble() = if (bubbleOn) Box4(bl.value, t.value, br.value, b.value) else null

    LaunchedEffect(hidden) { alpha.animateTo(if (hidden) 0f else 1f, Motion.shape.getValue(Transition.Dismiss).spec(reduced)) }

    LaunchedEffect(target.key) {
        val tr = transitionFor(prevPres, target.presentation, state, alertEnding = state.alertId != null && state.expandedId == null)
        transition = tr
        val first = prevPres == null
        prevPres = target.presentation
        val tg = target

        // Content: keep layers whose key survives, bring new ones in late, send old ones out fast.
        val wanted = tg.layers.associateBy { it.key }
        for (layer in layers.toList()) {
            val spec = wanted[layer.key]
            if (spec != null && !layer.leaving) layer.spec = spec else layer.leaving = true
        }
        val have = layers.filter { !it.leaving }.map { it.key }.toSet()
        for (spec in tg.layers) if (spec.key !in have) layers.add(LiveLayer(spec, Motion.blur(tr)))

        reportBounds?.invoke(boundsPx(liveBox().union(liveBubble()).union(tg.main).union(tg.bubble), true))

        if (first) {
            l.snapTo(tg.main.l); r.snapTo(tg.main.r); t.snapTo(tg.main.t); b.snapTo(tg.main.b); cap.snapTo(tg.radius)
            tg.bubble?.let { bl.snapTo(it.l); br.snapTo(it.r) }
            bubbleOn = tg.bubble != null
            shadow.snapTo(if (tg.expanded) 1f else 0f)
            for (layer in layers) layer.presence.snapTo(1f)
            reportBounds?.invoke(boundsPx(tg.main.union(tg.bubble), tg.expanded))
            return@LaunchedEffect
        }

        val shape = Motion.shape.getValue(tr)
        val heightSpring = Motion.height[tr] ?: shape
        val via = if (reduced) 0f else Motion.viaIdle[tr] ?: 0f
        val idle = frame.idle
        coroutineScope {
            // Content presence.
            for (layer in layers.toList()) {
                launch {
                    if (layer.leaving) {
                        layer.presence.animateTo(0f, Motion.contentExit.spec(reduced))
                        layers.remove(layer)
                    } else if (layer.presence.value < 1f) {
                        delay((Motion.enterDelay(tr) * 1000).toLong())
                        layer.presence.animateTo(1f, Motion.contentEnter.spec(reduced))
                    }
                }
            }
            launch { shadow.animateTo(if (tg.expanded) 1f else 0f, Motion.contentEnter.spec(reduced)) }
            launch { cap.animateTo(tg.radius, heightSpring.spec(reduced)) }

            // The split's bubble.
            launch {
                val bub = tg.bubble
                val h = Metrics.COMPACT_HEIGHT
                when {
                    bub != null && !bubbleOn -> {
                        if (tr == Transition.CollapseSplit) {
                            // It waits at its place under the card and pinches off as the card shrinks.
                            bl.snapTo(bub.l); br.snapTo(bub.r); bubbleOn = true
                        } else {
                            // It grows out of the pill's end as its own lobe, then springs free.
                            bl.snapTo(r.value - h); br.snapTo(r.value); bubbleOn = true
                            launch { br.animateTo(bub.r, shape.spec(reduced)) }
                            delay(if (reduced) 0L else (Motion.BUBBLE_SPLIT_DELAY * 1000).toLong())
                            bl.animateTo(bub.l, Motion.bubbleSplit.spec(reduced))
                        }
                    }
                    bub != null -> {
                        launch { bl.animateTo(bub.l, shape.spec(reduced)) }
                        br.animateTo(bub.r, shape.spec(reduced))
                    }
                    bubbleOn -> {
                        // Slides left under the sensor as a lobe and is absorbed.
                        launch { bl.animateTo(idle.r - h, Motion.bubbleUnsplitSlide.spec(reduced)) }
                        launch { br.animateTo(idle.r, Motion.bubbleUnsplitSlide.spec(reduced)) }
                        delay((Motion.BUBBLE_UNSPLIT_ABSORB * 1000).toLong())
                        bubbleOn = false
                    }
                }
            }

            // The pill's edges: through the idle shape and back out where the transition calls for it.
            if (via > 0f) {
                launch { l.animateTo(idle.l, shape.spec()) }
                launch { r.animateTo(idle.r, shape.spec()) }
                if (tr != Transition.Swap) {
                    launch { t.animateTo(tg.main.t, heightSpring.spec()) }
                    launch { b.animateTo(tg.main.b, heightSpring.spec()) }
                }
                delay((via * 1000).toLong())
                val re = Motion.retarget[tr] ?: shape
                launch { l.animateTo(tg.main.l, re.spec()) }
                launch { r.animateTo(tg.main.r, re.spec()) }
                if (tr == Transition.Swap) {
                    launch { t.animateTo(tg.main.t, heightSpring.spec()) }
                    launch { b.animateTo(tg.main.b, heightSpring.spec()) }
                }
            } else {
                launch { l.animateTo(tg.main.l, shape.spec(reduced)) }
                launch { r.animateTo(tg.main.r, shape.spec(reduced)) }
                launch { t.animateTo(tg.main.t, heightSpring.spec(reduced)) }
                launch { b.animateTo(tg.main.b, heightSpring.spec(reduced)) }
            }
        }
        reportBounds?.invoke(boundsPx(tg.main.union(tg.bubble), tg.expanded))
    }

    // ---- Drawing --------------------------------------------------------------------------

    val islandPath = remember { Path() }
    val bridgePath = remember { Path() }
    val clip = remember { Path() }
    val sensorPath = remember { Path() }
    val shadowPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK } }

    fun build(o: Offset) {
        val k = ptPx
        val box = liveBox()
        val radius = min(cap.value, box.h / 2)
        islandPath.rewind(); bridgePath.rewind(); clip.rewind(); sensorPath.rewind()
        val x = box.l * k - o.x
        val y = box.t * k - o.y
        IslandPath.addContinuousRect(islandPath, x, y, box.w * k, box.h * k, radius * k, Metrics.CORNER_SMOOTHING)
        IslandPath.addContinuousRect(clip, x, y, box.w * k, box.h * k, radius * k, Metrics.CORNER_SMOOTHING)
        val bub = liveBubble()
        if (bub != null && bub.w > 0.5f) {
            val bx = bub.l * k - o.x
            val bh = bub.h * k
            IslandPath.addContinuousRect(islandPath, bx, y, bub.w * k, bh, bh / 2, Metrics.CORNER_SMOOTHING)
            IslandPath.addContinuousRect(clip, bx, y, bub.w * k, bh, bh / 2, Metrics.CORNER_SMOOTHING)
            val mr = box.h * k / 2
            IslandPath.addBridge(
                bridgePath,
                x + box.w * k - mr, y + mr, mr,
                bx + bh / 2, y + bh / 2, bh / 2,
                Metrics.BRIDGE_HANDLE, Metrics.BRIDGE_SPREAD, Metrics.BRIDGE_MAX_GAP * k,
            )
        }
        val idle = frame.idle
        IslandPath.addContinuousRect(sensorPath, idle.l * k - o.x, idle.t * k - o.y, idle.w * k, idle.h * k, idle.h * k / 2, Metrics.CORNER_SMOOTHING)
    }

    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val slop = LocalViewConfiguration.current.touchSlop
    val stateNow by rememberUpdatedState(state)

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val box = liveBox()
                val o = origin()
                val px = box.cx * ptPx - o.x
                val py = box.cy * ptPx - o.y
                if (size.width > 0 && size.height > 0) transformOrigin = TransformOrigin(px / size.width, py / size.height)
                scaleX = press.value
                scaleY = press.value
                this.alpha = alpha.value
            }
            .then(
                if (gestures == null) Modifier else Modifier.pointerInput(gestures) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val o = origin()
                        val sx = (down.position.x + o.x) / ptPx
                        val sy = (down.position.y + o.y) / ptPx
                        val box = liveBox()
                        val bub = liveBubble()
                        val onBubble = bub != null && sx in bub.l..bub.r && sy in bub.t..bub.b
                        val onMain = sx in box.l - 4f..box.r + 4f && sy in box.t - 6f..box.b + 6f
                        if (!onBubble && !onMain) return@awaitEachGesture
                        val pres = stateNow.presentation
                        val expanded = pres is Presentation.Expanded
                        val activity = when (pres) {
                            is Presentation.Compact -> pres.activity
                            is Presentation.Split -> if (onBubble) pres.secondary else pres.primary
                            is Presentation.Expanded -> pres.activity
                            Presentation.Idle -> null
                        }
                        if (!expanded) scope.launch { press.animateTo(Metrics.PRESS_SWELL, Motion.pressSwell.spec(reduced)) }
                        val deadline = down.uptimeMillis + (Motion.LONG_PRESS * 1000).toLong()
                        var total = Offset.Zero
                        var swiping = false
                        var longPressed = false
                        while (true) {
                            val armed = !longPressed && !swiping && !expanded
                            val event = if (armed) {
                                val left = deadline - SystemClock.uptimeMillis()
                                if (left <= 0L) null else withTimeoutOrNull(left) { awaitPointerEvent(PointerEventPass.Main) }
                            } else {
                                awaitPointerEvent(PointerEventPass.Main)
                            }
                            if (event == null) {
                                if (!longPressed && !swiping && !expanded) {
                                    longPressed = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch { press.animateTo(1f, Motion.pressRelease.spec(reduced)) }
                                    gestures.longPress(activity, onBubble)
                                }
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            total += change.positionChange()
                            if (!swiping && total.getDistance() > slop * 2) {
                                swiping = true
                                scope.launch { press.animateTo(1f, Motion.pressRelease.spec(reduced)) }
                            }
                            if (!change.pressed) {
                                scope.launch { press.animateTo(1f, Motion.pressRelease.spec(reduced)) }
                                when {
                                    swiping && expanded && total.y < -slop * 3 -> gestures.swipeUp()
                                    swiping && !expanded && abs(total.x) > abs(total.y) && abs(total.x) > slop * 4 -> gestures.swipeSideways(activity, total.x)
                                    swiping && !expanded && total.y > slop * 4 -> gestures.longPress(activity, onBubble)
                                    !swiping && !longPressed && !change.isConsumed -> gestures.tap(activity, onBubble)
                                }
                                break
                            }
                        }
                    }
                },
            )
            .drawBehind {
                val o = origin()
                build(o)
                val s = shadow.value
                if (s > 0.01f) {
                    shadowPaint.setShadowLayer(Metrics.SHADOW_BLUR / 2 * ptPx, 0f, Metrics.SHADOW_DY * ptPx, Color.Black.copy(alpha = Metrics.SHADOW_ALPHA * s).toArgb())
                    drawIntoCanvas { it.nativeCanvas.drawPath(islandPath.asAndroidPath(), shadowPaint) }
                }
                if (keyline) {
                    val idleish = ((liveBox().w - Metrics.IDLE_WIDTH) / 12f).coerceIn(0f, 1f)
                    if (idleish > 0f) {
                        drawPath(islandPath, Color.White.copy(alpha = Metrics.KEYLINE_ALPHA * idleish * (1f - 0.07f * s)), style = Stroke(Metrics.KEYLINE_WIDTH * 2 * ptPx))
                    }
                }
                drawPath(islandPath, Color.Black)
                drawPath(bridgePath, Color.Black)
            }
            .drawWithContent {
                clipPath(clip) { this@drawWithContent.drawContent() }
                // The sensor housing hides content sliding under the camera mid-morph. Expanded cards
                // are laid out around the camera, so it fades out as the island opens.
                val housing = 1f - shadow.value
                if (housing > 0.01f) drawPath(sensorPath, Color.Black.copy(alpha = housing))
            },
    ) {
        // Live layers draw the current content, so in-place updates (play/pause, a muted call) show at once.
        val current = target.layers.associateBy { it.key }
        for (layer in layers) {
            key(layer.key) {
                val spec = if (layer.leaving) layer.spec else current[layer.key] ?: layer.spec
                ContentLayer(layer, spec, ptPx, origin, { liveBox() }, { liveBubble() })
            }
        }
    }
}

@Composable
private fun ContentLayer(layer: LiveLayer, spec: LayerSpec, ptPx: Float, origin: () -> Offset, box: () -> Box4, bubble: () -> Box4?) {
    val basePt = LocalPt.current
    val f = spec.frame
    val density = LocalDensity.current.density
    Box(
        Modifier
            .offset {
                val o = origin()
                IntOffset((f.l * ptPx - o.x).toInt(), (f.t * ptPx - o.y).toInt())
            }
            .size((f.w * ptPx / density).dp, (f.h * ptPx / density).dp)
            .graphicsLayer {
                val rest = spec.rest
                val cur = (if (spec.onBubble) bubble() else box()) ?: rest
                val fx = (f.cx - rest.l) / rest.w
                val fy = (f.cy - rest.t) / rest.h
                val cx = cur.l + fx * cur.w
                val cy = cur.t + fy * cur.h
                val follow = ((cur.w / rest.w) * (cur.h / rest.h)).pow(0.5f).coerceIn(Metrics.FOLLOW_MIN, Metrics.FOLLOW_MAX)
                val p = layer.presence.value.coerceIn(0f, 1f)
                val enter = 0.9f + 0.1f * p
                translationX = (cx - f.cx) * ptPx
                translationY = (cy - f.cy) * ptPx
                scaleX = follow * enter
                scaleY = follow * enter
                alpha = if (layer.leaving) p.pow(0.6f) else p.pow(1.4f)
                val blur = (1f - p) * layer.blur * ptPx * 0.5f
                renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
            },
        contentAlignment = spec.align,
    ) {
        CompositionLocalProvider(LocalPt provides basePt * spec.ptScale) { spec.content() }
    }
}
