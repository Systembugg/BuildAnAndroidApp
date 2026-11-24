package dev.capsule.island.system

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.capsule.island.CapsuleApp
import dev.capsule.island.core.Metrics
import dev.capsule.island.data.IdleMode
import dev.capsule.island.data.Prefs
import dev.capsule.island.data.TapAction
import dev.capsule.island.engine.Action
import dev.capsule.island.engine.IslandActivity
import dev.capsule.island.engine.IslandEngine
import dev.capsule.island.engine.Presentation
import dev.capsule.island.ui.island.IslandFrame
import dev.capsule.island.ui.island.IslandGestures
import dev.capsule.island.ui.island.IslandSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** Lifecycle and saved state for a ComposeView that lives in a bare window, not an Activity. */
private class WindowOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    fun create() { saved.performRestore(Bundle()); registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE) }
    fun resume() { registry.handleLifecycleEvent(Lifecycle.Event.ON_START); registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
    fun destroy() { registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY) }
}

/**
 * Hosts the island in a system window of [type]: an accessibility overlay (above the status bar
 * and on the lock screen) or an app overlay. The window is resized to the island's bounds as it
 * moves, so it never blocks touches beyond the island itself.
 */
class OverlayHost(
    private val context: Context,
    private val wm: WindowManager,
    private val type: Int,
    /** Whether this host should draw right now (the other host may own the island). */
    private val active: StateFlow<Boolean>,
) {
    private val owner = WindowOwner()
    private var origin by mutableStateOf(Offset.Zero)
    private var frame by mutableStateOf(IslandFrame(3f, Metrics.SCREEN_WIDTH, Metrics.SCREEN_WIDTH / 2, Metrics.TOP))
    private var landscape by mutableStateOf(false)
    private var attached = false

    private val params = WindowManager.LayoutParams(
        1, 1, type,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        title = "Capsule island"
        setFitInsetsTypes(0)
    }

    /** Catches touches outside the window: they collapse an expanded island, as on iOS. */
    private val root = object : FrameLayout(context) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                if (IslandEngine.state.value.presentation is Presentation.Expanded) IslandEngine.collapse()
                return false
            }
            return super.dispatchTouchEvent(ev)
        }

        override fun onConfigurationChanged(newConfig: Configuration) {
            super.onConfigurationChanged(newConfig)
            measureScreen()
        }
    }

    private val gestures = object : IslandGestures {
        private val prefs get() = CapsuleApp.instance.prefs.value

        override fun tap(activity: IslandActivity?, onBubble: Boolean) {
            activity ?: return
            val expanded = IslandEngine.state.value.presentation is Presentation.Expanded
            when {
                expanded -> { activity.run(Action.Open); IslandEngine.collapse() }
                prefs.tapAction == TapAction.Expand -> IslandEngine.expand(activity.id)
                Action.Open in activity.actions -> activity.run(Action.Open)
                else -> IslandEngine.expand(activity.id)
            }
        }

        override fun longPress(activity: IslandActivity?, onBubble: Boolean) {
            activity ?: return
            if (prefs.tapAction == TapAction.Expand && Action.Open in activity.actions) activity.run(Action.Open)
            else IslandEngine.expand(activity.id, fromPress = true)
        }

        override fun swipeUp() = IslandEngine.collapse()

        override fun swipeSideways(activity: IslandActivity?, dx: Float) {
            if (IslandEngine.state.value.presentation is Presentation.Split) IslandEngine.swapSplit()
            else if (activity != null) {
                if (Action.Dismiss in activity.actions) activity.run(Action.Dismiss) else IslandEngine.end(activity.id)
            }
        }
    }

    private val view = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            val state by IslandEngine.state.collectAsState()
            val prefs by CapsuleApp.instance.prefs.collectAsState()
            val isActive by active.collectAsState()
            var idleHidden by androidx.compose.runtime.remember { mutableStateOf(false) }
            val idle = state.presentation is Presentation.Idle
            LaunchedEffect(idle, prefs.idleMode, prefs.autoHideSeconds) {
                idleHidden = false
                if (idle && prefs.idleMode == IdleMode.AutoHide) {
                    delay(prefs.autoHideSeconds * 1000L)
                    idleHidden = true
                }
            }
            LaunchedEffect(prefs.scale, prefs.offsetX, prefs.offsetY) { measureScreen() }
            val hidden = !isActive || !prefs.enabled || idleHidden || (landscape && prefs.hideInLandscape)
            LaunchedEffect(hidden) {
                // Once faded, stop taking touches; an untouchable app overlay must also be translucent
                // for Android 12+ to pass touches through it.
                if (hidden) delay(400)
                setTouchable(!hidden)
            }
            IslandSurface(
                state = state,
                frame = frame,
                gestures = gestures,
                reducedMotion = prefs.reducedMotion,
                keyline = prefs.keyline,
                hidden = hidden,
                origin = { origin },
                onBounds = ::place,
            )
        }
    }

    fun attach() {
        if (attached) return
        owner.create()
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        root.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        measureScreen()
        runCatching { wm.addView(root, params) }.onSuccess {
            attached = true
            owner.resume()
        }
    }

    fun detach() {
        if (!attached) return
        attached = false
        runCatching { wm.removeViewImmediate(root) }
        owner.destroy()
    }

    /** Reads the screen and the cutout and places the idle pill around the camera. */
    private fun measureScreen() {
        val prefs: Prefs = CapsuleApp.instance.prefs.value
        val metrics = wm.currentWindowMetrics
        val bounds = metrics.bounds
        landscape = bounds.width() > bounds.height()
        val width = minOf(bounds.width(), bounds.height()).toFloat()
        val ptPx = width / Metrics.SCREEN_WIDTH * prefs.scale
        val cutout = metrics.windowInsets.displayCutout?.boundingRects?.firstOrNull { it.top <= 0 || it.top < bounds.height() / 10 }
        val screenPt = bounds.width() / ptPx
        val cx = (cutout?.exactCenterX() ?: (bounds.width() / 2f)) / ptPx + prefs.offsetX
        val top = if (cutout != null) cutout.exactCenterY() / ptPx - Metrics.IDLE_HEIGHT / 2 + prefs.offsetY
        else Metrics.TOP + prefs.offsetY
        frame = IslandFrame(ptPx, screenPt, cx, maxOf(top, 1f))
    }

    private var lastPlaced: IntRect? = null

    private fun setTouchable(touchable: Boolean) {
        val flags = if (touchable) params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val alpha = if (touchable) 1f else 0.5f
        if (flags == params.flags && alpha == params.alpha) return
        params.flags = flags
        params.alpha = alpha
        if (attached) runCatching { wm.updateViewLayout(root, params) }
    }

    private fun place(rect: IntRect) {
        if (!attached || rect == lastPlaced) return
        lastPlaced = rect
        params.x = rect.left
        params.y = rect.top
        params.width = maxOf(1, rect.width)
        params.height = maxOf(1, rect.height)
        origin = Offset(rect.left.toFloat(), rect.top.toFloat())
        runCatching { wm.updateViewLayout(root, params) }
    }
}
