package dev.capsule.island.system

import android.accessibilityservice.AccessibilityService
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/** Hosts the island in an accessibility overlay. Reads no events and no window content. */
class IslandAccessibilityService : AccessibilityService() {
    private var host: OverlayHost? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Hosts.accessibilityConnected.value = true
        Hosts.sync(this)
        host = OverlayHost(this, getSystemService(WindowManager::class.java), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, Hosts.accessibilityActive)
            .also { it.attach() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        host?.detach()
        host = null
        Hosts.accessibilityConnected.value = false
        Hosts.sync(this)
        super.onDestroy()
    }
}
