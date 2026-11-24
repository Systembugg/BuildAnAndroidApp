package dev.capsule.island.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.capsule.island.engine.IslandEngine

/**
 * Lets Tasker, MacroDroid or adb drive the island:
 *
 *   adb shell am broadcast -a dev.capsule.island.action.SHOW -n dev.capsule.island/.system.AutomationReceiver \
 *       --es type timer --ef value 90
 *   adb shell am broadcast -a dev.capsule.island.action.END -n dev.capsule.island/.system.AutomationReceiver --es type timer
 *
 * `type` is any [Demo] name (case-insensitive); optional `title`, `subtitle`, `value`, `alert`.
 */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: return
        val demo = Demo.entries.firstOrNull { it.name.equals(type, ignoreCase = true) || it.title.equals(type, ignoreCase = true) } ?: return
        when (intent.action) {
            ACTION_SHOW -> Demos.start(
                demo,
                title = intent.getStringExtra("title"),
                subtitle = intent.getStringExtra("subtitle"),
                value = if (intent.hasExtra("value")) intent.getFloatExtra("value", 0f) else null,
                alertOverride = if (intent.hasExtra("alert")) intent.getBooleanExtra("alert", false) else null,
            )
            ACTION_END -> IslandEngine.end(demo.id)
        }
    }

    companion object {
        const val ACTION_SHOW = "dev.capsule.island.action.SHOW"
        const val ACTION_END = "dev.capsule.island.action.END"
    }
}
