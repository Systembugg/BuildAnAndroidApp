package dev.capsule.island.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Each source of activities. Turning one off unregisters its listener. */
enum class Module(val title: String, val detail: String, val default: Boolean = true) {
    Unlock("Lock and unlock", "The padlock opens when you unlock the phone"),
    Music("Now playing", "Album art, live bars and controls for any media app"),
    Calls("Calls", "Incoming and ongoing calls from the phone and calling apps"),
    Timers("Timers", "Clock app timers and countdowns"),
    Navigation("Navigation", "Turn-by-turn from Google Maps, Waze and others"),
    Transfers("Transfers", "Quick Share, downloads and uploads with progress"),
    Recording("Recording", "Screen and voice recording indicators"),
    Charging("Charging", "Plugging in and battery level"),
    LowBattery("Low battery", "Warnings at 20% and 10%"),
    Ringer("Silent and ring", "Ringer switch and ringer volume"),
    Focus("Do Not Disturb", "Focus changes"),
    AirPods("Earbuds", "Bluetooth audio devices connecting, with battery"),
    Hotspot("Hotspot", "While your hotspot is on"),
    Mirroring("Screen mirroring", "While casting the screen"),
    Airplane("Airplane mode", "A card when airplane mode turns on"),
    NoData("No data", "A card when you lose mobile data and Wi-Fi", default = false),
}

enum class IdleMode { Visible, AutoHide }
enum class TapAction { OpenApp, Expand }
enum class HostMode { Auto, Accessibility, Overlay }

data class Prefs(
    val enabled: Boolean = true,
    val host: HostMode = HostMode.Auto,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val idleMode: IdleMode = IdleMode.Visible,
    val autoHideSeconds: Int = 5,
    val tapAction: TapAction = TapAction.OpenApp,
    val reducedMotion: Boolean = false,
    val keyline: Boolean = false,
    val hideInLandscape: Boolean = true,
    val modules: Set<Module> = Module.entries.filter { it.default }.toSet(),
) {
    fun on(m: Module) = enabled && m in modules
}

private val Context.store: DataStore<Preferences> by preferencesDataStore("capsule")

class Settings(private val context: Context) {
    private object K {
        val enabled = booleanPreferencesKey("enabled")
        val host = stringPreferencesKey("host")
        val offsetX = floatPreferencesKey("offset_x")
        val offsetY = floatPreferencesKey("offset_y")
        val scale = floatPreferencesKey("scale")
        val idleMode = stringPreferencesKey("idle_mode")
        val autoHide = intPreferencesKey("auto_hide")
        val tap = stringPreferencesKey("tap")
        val reduced = booleanPreferencesKey("reduced")
        val keyline = booleanPreferencesKey("keyline")
        val landscape = booleanPreferencesKey("hide_landscape")
        val modules = stringSetPreferencesKey("modules")
    }

    val prefs: Flow<Prefs> = context.store.data.map(::read)

    private fun read(p: Preferences): Prefs {
        val d = Prefs()
        return Prefs(
            enabled = p[K.enabled] ?: d.enabled,
            host = p[K.host]?.let { runCatching { HostMode.valueOf(it) }.getOrNull() } ?: d.host,
            offsetX = p[K.offsetX] ?: d.offsetX,
            offsetY = p[K.offsetY] ?: d.offsetY,
            scale = p[K.scale] ?: d.scale,
            idleMode = p[K.idleMode]?.let { runCatching { IdleMode.valueOf(it) }.getOrNull() } ?: d.idleMode,
            autoHideSeconds = p[K.autoHide] ?: d.autoHideSeconds,
            tapAction = p[K.tap]?.let { runCatching { TapAction.valueOf(it) }.getOrNull() } ?: d.tapAction,
            reducedMotion = p[K.reduced] ?: d.reducedMotion,
            keyline = p[K.keyline] ?: d.keyline,
            hideInLandscape = p[K.landscape] ?: d.hideInLandscape,
            modules = p[K.modules]?.mapNotNull { runCatching { Module.valueOf(it) }.getOrNull() }?.toSet() ?: d.modules,
        )
    }

    suspend fun update(transform: (Prefs) -> Prefs) {
        context.store.edit { p ->
            val n = transform(read(p))
            p[K.enabled] = n.enabled
            p[K.host] = n.host.name
            p[K.offsetX] = n.offsetX
            p[K.offsetY] = n.offsetY
            p[K.scale] = n.scale
            p[K.idleMode] = n.idleMode.name
            p[K.autoHide] = n.autoHideSeconds
            p[K.tap] = n.tapAction.name
            p[K.reduced] = n.reducedMotion
            p[K.keyline] = n.keyline
            p[K.landscape] = n.hideInLandscape
            p[K.modules] = n.modules.map { it.name }.toSet()
        }
    }
}
