package dev.capsule.island.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.capsule.island.CapsuleApp
import dev.capsule.island.data.HostMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Decides which window hosts the island. The accessibility host is preferred: it draws above the
 * status bar (so the island takes touches there) and on the lock screen, and the system keeps it
 * running. The app overlay, in a foreground service, is the fallback.
 */
object Hosts {
    val accessibilityConnected = MutableStateFlow(false)
    private val _accessibilityActive = MutableStateFlow(false)
    private val _overlayActive = MutableStateFlow(false)
    val accessibilityActive: StateFlow<Boolean> get() = _accessibilityActive
    val overlayActive: StateFlow<Boolean> get() = _overlayActive

    fun sync(context: Context) {
        val prefs = CapsuleApp.instance.prefs.value
        val useA11y = prefs.enabled && accessibilityConnected.value && prefs.host != HostMode.Overlay
        val useOverlay = prefs.enabled && !useA11y && prefs.host != HostMode.Accessibility && Settings.canDrawOverlays(context)
        _accessibilityActive.value = useA11y
        _overlayActive.value = useOverlay
        val intent = Intent(context, IslandOverlayService::class.java)
        if (useOverlay) runCatching { context.startForegroundService(intent) }
        else context.stopService(intent)
    }

    fun accessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, IslandAccessibilityService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }
}
