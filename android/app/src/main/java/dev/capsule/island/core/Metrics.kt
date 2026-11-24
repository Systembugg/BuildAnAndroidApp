package dev.capsule.island.core

/**
 * Every size the island draws, in points. Measured by islandcn on the iPhone 18 Pro simulator
 * (`lib/island/metrics.ts`): the smaller iOS 27 island. Content sizes come from the Figma kit.
 */
object Metrics {
    const val SCREEN_WIDTH = 402f
    const val TOP = 13.91f

    const val IDLE_WIDTH = 94.85f
    const val IDLE_HEIGHT = 36.84f

    const val COMPACT_HEIGHT = 36.85f
    // islandcn caps slots at 72; the kit's battery and charging pills need room for their labels.
    const val COMPACT_MAX_SLOT = 110f
    const val COMPACT_MAX_WIDTH = 280f
    /** Neither side reaches less than this fraction of the other beyond the sensor. */
    const val COMPACT_BALANCE = 0.7475f
    /** Content with a round footprint sits concentric with the pill's end. */
    const val END_CENTER = COMPACT_HEIGHT / 2f
    /** Inset for wide content (text), from the kit's 12 pt padding. */
    const val TEXT_INSET = 12f
    const val SENSOR_GAP = 2f

    const val SPLIT_GAP = 7.82f
    const val SPLIT_OUTER = 6.59f
    const val SPLIT_SLOT_GAP = 6.15f
    const val SPLIT_INNER = 8.18f
    const val BUBBLE_WIDTH = 62.18f
    const val BUBBLE_PADDING = 6.42f
    const val SPLIT_MAX_SPAN = 290f

    const val EXPANDED_SIDE = 13.83f
    const val EXPANDED_TOP = 13.83f
    const val EXPANDED_RADIUS = 47.9f
    /** Width of the Figma kit's expanded canvas. */
    const val KIT_EXPANDED_WIDTH = 367f

    const val CORNER_SMOOTHING = 0.72f

    const val BRIDGE_HANDLE = 2.4f
    const val BRIDGE_SPREAD = 0.3f
    const val BRIDGE_MAX_GAP = 5f

    const val FOLLOW_MIN = 0.2f
    const val FOLLOW_MAX = 2.5f

    const val PRESS_SWELL = 1.12f

    /** Expanded shadow: key layer 10.7 down, 30.76 blur, 0.353 black. */
    const val SHADOW_DY = 10.7f
    const val SHADOW_BLUR = 30.76f
    const val SHADOW_ALPHA = 0.353f

    const val KEYLINE_WIDTH = 1.5f
    const val KEYLINE_ALPHA = 0.16f

    fun expandedWidth(screenWidth: Float) = minOf(screenWidth, SCREEN_WIDTH) - 2 * EXPANDED_SIDE
}
