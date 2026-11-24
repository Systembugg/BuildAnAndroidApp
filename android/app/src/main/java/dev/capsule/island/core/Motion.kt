package dev.capsule.island.core

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlin.math.PI

/**
 * A SwiftUI spring: `response` is the undamped period in seconds, `damping` the damping ratio.
 * Compose's spring is the same oscillator with unit mass, so stiffness = (2π / response)².
 */
data class IslandSpring(val response: Float, val damping: Float) {
    val stiffness: Float get() = ((2 * PI / response) * (2 * PI / response)).toFloat()
    fun spec(reduced: Boolean = false): AnimationSpec<Float> =
        if (reduced) tween(200, easing = FastOutSlowInEasing)
        else spring(dampingRatio = damping, stiffness = stiffness, visibilityThreshold = 0.01f)
}

/** Which motion a presentation change uses (islandcn `ShapeTransition`). */
enum class Transition { Appear, Expand, PressExpand, AppearExpand, Swap, Collapse, AlertCollapse, CollapseSplit, Split, Unsplit, Dismiss, Resize }

/** Every timing fitted frame by frame to the simulator by islandcn (`lib/island/motion.ts`). */
object Motion {
    val shape = mapOf(
        Transition.Appear to IslandSpring(0.483f, 0.62f),
        Transition.Expand to IslandSpring(0.517f, 0.824f),
        Transition.PressExpand to IslandSpring(0.387f, 0.792f),
        Transition.AppearExpand to IslandSpring(0.54f, 0.811f),
        Transition.Swap to IslandSpring(0.498f, 0.829f),
        Transition.Collapse to IslandSpring(0.479f, 0.894f),
        Transition.AlertCollapse to IslandSpring(0.535f, 0.84f),
        Transition.CollapseSplit to IslandSpring(0.4f, 0.9f),
        Transition.Split to IslandSpring(0.505f, 0.815f),
        Transition.Unsplit to IslandSpring(0.487f, 0.794f),
        Transition.Dismiss to IslandSpring(0.432f, 1.019f),
        Transition.Resize to IslandSpring(0.5f, 0.82f),
    )

    /** Height springs where they differ from the edges'. */
    val height = mapOf(
        Transition.Expand to IslandSpring(0.497f, 0.825f),
        Transition.Collapse to IslandSpring(0.453f, 0.928f),
        Transition.AlertCollapse to IslandSpring(0.481f, 0.908f),
        Transition.CollapseSplit to IslandSpring(0.45f, 0.9f),
    )

    /** Seconds the edges aim at the idle shape before turning to the target. */
    val viaIdle = mapOf(
        Transition.Swap to 0.223f,
        Transition.Collapse to 0.311f,
        Transition.AlertCollapse to 0.29f,
        Transition.Split to 0.215f,
        Transition.Unsplit to 0.203f,
    )

    /** The spring after the turn (the waist pulls in to about 105 wide and bounces back). */
    val retarget = mapOf(
        Transition.Collapse to IslandSpring(0.46f, 0.555f),
        Transition.AlertCollapse to IslandSpring(0.466f, 0.59f),
    )

    /** The bubble's left edge springs out of the pill this late, and pinches off. */
    val bubbleSplit = IslandSpring(0.376f, 0.811f)
    const val BUBBLE_SPLIT_DELAY = 0.345f
    val bubbleUnsplitSlide = IslandSpring(0.42f, 1f)
    const val BUBBLE_UNSPLIT_ABSORB = 0.205f

    val pressSwell = IslandSpring(0.803f, 0.641f)
    val pressRelease = IslandSpring(0.3f, 1.5f)
    const val LONG_PRESS = 0.216f
    const val PRESS_DECIDE = 0.1f

    const val ALERT_HOLD = 5.975f
    const val ALERT_CONTENT_DELAY = 0.12f

    /** Content presence: incoming layers wait for the shape, outgoing leave quickly. */
    val contentEnter = IslandSpring(0.36f, 1f)
    val contentExit = IslandSpring(0.16f, 1f)

    /** How long incoming content waits per transition, and how much it blurs (points). */
    fun enterDelay(t: Transition): Float = when (t) {
        Transition.Appear -> 0.12f
        Transition.Expand, Transition.PressExpand -> 0.14f
        Transition.AppearExpand -> 0.02f
        Transition.Swap -> 0.252f
        Transition.Collapse, Transition.AlertCollapse -> 0.36f
        Transition.CollapseSplit -> 0.3f
        Transition.Split -> 0.375f
        Transition.Unsplit -> 0.22f
        Transition.Dismiss -> 0f
        Transition.Resize -> 0.08f
    }

    fun blur(t: Transition): Float = when (t) {
        Transition.PressExpand -> 10.5f
        Transition.Expand -> 9.5f
        Transition.AppearExpand, Transition.Swap, Transition.AlertCollapse -> 8f
        Transition.Collapse, Transition.CollapseSplit -> 7f
        Transition.Split, Transition.Resize -> 5.5f
        Transition.Unsplit -> 6f
        Transition.Dismiss -> 4.5f
        Transition.Appear -> 6f
    }
}
