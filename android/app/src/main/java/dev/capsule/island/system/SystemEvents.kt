package dev.capsule.island.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.NotificationManager
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import dev.capsule.island.data.Module
import dev.capsule.island.data.Prefs
import dev.capsule.island.engine.Action
import dev.capsule.island.engine.IslandActivity
import dev.capsule.island.engine.IslandEngine
import dev.capsule.island.engine.Payload
import dev.capsule.island.engine.Priority

/**
 * Turns system broadcasts into island activities. Everything is event-driven: no polling, no
 * wake locks. Each module registers only while it is on.
 */
class SystemEvents(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var prefs = Prefs()
    private val registered = HashMap<Module, Any>()
    private var screenRegistered = false

    @Volatile var screenOn = true
        private set

    fun apply(p: Prefs) {
        prefs = p
        if (!screenRegistered) {
            register(screen, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
            })
            screenRegistered = true
        }
        val wantBattery = p.on(Module.Charging) || p.on(Module.LowBattery)
        if (wantBattery && !batteryRegistered) {
            register(battery, IntentFilter().apply { addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_POWER_CONNECTED) })
            batteryRegistered = true
        } else if (!wantBattery && batteryRegistered) {
            runCatching { context.unregisterReceiver(battery) }
            batteryRegistered = false
        }
        for (m in Module.entries) {
            val want = p.on(m)
            val has = m in registered
            if (want && !has) start(m)
            if (!want && has) stop(m)
        }
    }

    private fun register(receiver: BroadcastReceiver, filter: IntentFilter) {
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
    }

    private fun receiver(block: (Intent, BroadcastReceiver) -> Unit) = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) = block(intent, this)
    }

    private fun start(m: Module) {
        val handle: Any? = when (m) {
            Module.Ringer -> receiver { i, r -> onRinger(i, r.isInitialStickyBroadcast) }.also {
                register(it, IntentFilter().apply { addAction(AudioManager.RINGER_MODE_CHANGED_ACTION); addAction(VOLUME_CHANGED) })
            }
            Module.Focus -> receiver { _, _ -> onFocus() }.also { register(it, IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)) }
            Module.AirPods -> receiver { i, _ -> onBluetooth(i) }.also {
                register(it, IntentFilter().apply { addAction(BluetoothDevice.ACTION_ACL_CONNECTED); addAction(BATTERY_LEVEL_CHANGED) })
            }
            Module.Airplane -> receiver { i, _ -> onAirplane(i) }.also { register(it, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED)) }
            Module.Hotspot -> receiver { i, _ -> onHotspot(i) }.also { register(it, IntentFilter(HOTSPOT_STATE)) }
            Module.Mirroring -> mirroring.also {
                context.getSystemService(DisplayManager::class.java).registerDisplayListener(it, main)
                checkDisplays()
            }
            Module.NoData -> network.also { context.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(it) }
            else -> Unit // Unlock reads the screen receiver; the rest come from the notification listener.
        }
        if (handle != null) registered[m] = handle
    }

    private fun stop(m: Module) {
        val handle = registered.remove(m) ?: return
        when {
            handle is BroadcastReceiver -> runCatching { context.unregisterReceiver(handle) }
            handle is DisplayManager.DisplayListener -> context.getSystemService(DisplayManager::class.java).unregisterDisplayListener(handle)
            handle is ConnectivityManager.NetworkCallback -> runCatching { context.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(handle) }
        }
        when (m) {
            Module.Hotspot -> IslandEngine.end(ID_HOTSPOT)
            Module.Mirroring -> IslandEngine.end(ID_MIRRORING)
            else -> Unit
        }
    }

    // ---- Lock and unlock ------------------------------------------------------------------

    private val screen = receiver { intent, _ ->
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> { screenOn = false; IslandEngine.end(ID_LOCK) }
            Intent.ACTION_SCREEN_ON -> {
                screenOn = true
                val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
                if (locked && prefs.on(Module.Unlock)) showLocked()
            }
            Intent.ACTION_USER_PRESENT -> if (prefs.on(Module.Unlock)) unlock()
        }
    }

    private fun showLocked() {
        IslandEngine.start(IslandActivity(ID_LOCK, Payload.Lock(unlocked = false), Priority.LOCK))
    }

    /** The shackle swings open, then the island settles back to whatever was running. */
    fun unlock() {
        if (!IslandEngine.has(ID_LOCK)) {
            // The lock screen wasn't drawn (app overlay host): show the closed lock for a beat first.
            showLocked()
            main.postDelayed({ IslandEngine.update(ID_LOCK) { it.copy(payload = Payload.Lock(unlocked = true)) } }, 280)
        } else {
            IslandEngine.update(ID_LOCK) { it.copy(payload = Payload.Lock(unlocked = true)) }
        }
        main.removeCallbacks(endLock)
        main.postDelayed(endLock, 1250)
    }

    private val endLock = Runnable { IslandEngine.end(ID_LOCK) }

    // ---- Battery --------------------------------------------------------------------------

    private var batteryRegistered = false
    private val battery = receiver { i, r -> onBattery(i, r.isInitialStickyBroadcast) }
    private var lastLevel = -1
    private var plugged = false

    private fun onBattery(intent: Intent, sticky: Boolean) {
        if (intent.action == Intent.ACTION_POWER_CONNECTED) {
            if (prefs.on(Module.Charging)) main.postDelayed({ showBattery(Payload.Battery.Kind.Charging, alert = false) }, 300)
            return
        }
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val previous = lastLevel
        lastLevel = level
        if (sticky || previous < 0 || plugged || !prefs.on(Module.LowBattery)) return
        if (previous > 20 && level <= 20 && level > 10) showBattery(Payload.Battery.Kind.Low, alert = false)
        if (previous > 10 && level <= 10) showBattery(Payload.Battery.Kind.Critical, alert = false)
    }

    private fun showBattery(kind: Payload.Battery.Kind, alert: Boolean) {
        val level = if (lastLevel >= 0) lastLevel else context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        IslandEngine.start(IslandActivity(ID_BATTERY, Payload.Battery(level, kind), Priority.ALERT), alert = alert, ttlMs = 3800)
    }

    // ---- Ringer, volume, focus ------------------------------------------------------------

    private fun onRinger(intent: Intent, sticky: Boolean) {
        if (sticky) return
        val audio = context.getSystemService(AudioManager::class.java)
        if (intent.action == VOLUME_CHANGED) {
            if (intent.getIntExtra(EXTRA_VOLUME_STREAM, -1) != AudioManager.STREAM_RING) return
            val f = audio.getStreamVolume(AudioManager.STREAM_RING).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
            IslandEngine.start(IslandActivity(ID_RINGER, Payload.RingVolume(f), Priority.ALERT), ttlMs = 2200)
            return
        }
        val silent = audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
        IslandEngine.start(
            IslandActivity(ID_RINGER, Payload.Ringer(silent), Priority.ALERT, actions = mapOf(Action.Unmute to {
                runCatching { audio.ringerMode = AudioManager.RINGER_MODE_NORMAL }
                Unit
            })),
            ttlMs = 2600,
        )
    }

    private fun onFocus() {
        val nm = context.getSystemService(NotificationManager::class.java)
        val on = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        IslandEngine.start(IslandActivity(ID_FOCUS, Payload.Focus(Payload.Focus.Kind.DoNotDisturb, on), Priority.ALERT), ttlMs = 2600)
    }

    // ---- Bluetooth audio ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun onBluetooth(intent: Intent) {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        @Suppress("DEPRECATION")
        val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
        val audio = device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
        if (!audio) return
        val name = runCatching { device.alias ?: device.name }.getOrNull() ?: "Headphones"
        if (intent.action == BATTERY_LEVEL_CHANGED) {
            val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1).takeIf { it >= 0 } ?: return
            IslandEngine.update(ID_AIRPODS) { it.copy(payload = Payload.AirPods(name, level)) }
            return
        }
        val battery = runCatching { device.javaClass.getMethod("getBatteryLevel").invoke(device) as Int }.getOrNull()?.takeIf { it >= 0 }
        IslandEngine.start(IslandActivity(ID_AIRPODS, Payload.AirPods(name, battery), Priority.ALERT), alert = true, ttlMs = 5000)
    }

    // ---- Airplane, hotspot, mirroring, data -----------------------------------------------

    private fun openSettings(action: String): () -> Unit = {
        runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        IslandEngine.collapse()
    }

    private fun onAirplane(intent: Intent) {
        if (!intent.getBooleanExtra("state", false)) { IslandEngine.end(ID_AIRPLANE); return }
        IslandEngine.start(
            IslandActivity(ID_AIRPLANE, Payload.Airplane, Priority.ALERT, actions = mapOf(Action.OpenSettings to openSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS))),
            alert = true, ttlMs = 5500,
        )
    }

    private fun onHotspot(intent: Intent) {
        when (intent.getIntExtra("wifi_state", 0)) {
            13 -> IslandEngine.start(IslandActivity(ID_HOTSPOT, Payload.Hotspot(0), Priority.ONGOING, actions = mapOf(Action.Open to openSettings(Settings.ACTION_WIRELESS_SETTINGS))))
            10, 11 -> IslandEngine.end(ID_HOTSPOT)
        }
    }

    private val mirroring = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = checkDisplays()
        override fun onDisplayRemoved(displayId: Int) = checkDisplays()
        override fun onDisplayChanged(displayId: Int) = Unit
    }

    private fun checkDisplays() {
        val dm = context.getSystemService(DisplayManager::class.java)
        val cast = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (cast == null) { IslandEngine.end(ID_MIRRORING); return }
        IslandEngine.start(
            IslandActivity(ID_MIRRORING, Payload.Mirroring(cast.name), Priority.ONGOING, actions = mapOf(
                Action.StopMirroring to openSettings(Settings.ACTION_CAST_SETTINGS),
                Action.Open to openSettings(Settings.ACTION_CAST_SETTINGS),
            )),
        )
    }

    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            main.postDelayed({
                val cm = context.getSystemService(ConnectivityManager::class.java)
                val airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
                if (cm.activeNetwork == null && !airplane && screenOn) {
                    IslandEngine.start(
                        IslandActivity(ID_NODATA, Payload.NoData, Priority.ALERT, actions = mapOf(
                            Action.Dismiss to { IslandEngine.end(ID_NODATA) },
                            Action.OpenSettings to openSettings(Settings.ACTION_DATA_ROAMING_SETTINGS),
                        )),
                        alert = true, ttlMs = 6000,
                    )
                }
            }, 1500)
        }
    }

    companion object {
        const val ID_LOCK = "sys.lock"
        const val ID_BATTERY = "sys.battery"
        const val ID_RINGER = "sys.ringer"
        const val ID_FOCUS = "sys.focus"
        const val ID_AIRPODS = "sys.airpods"
        const val ID_AIRPLANE = "sys.airplane"
        const val ID_HOTSPOT = "sys.hotspot"
        const val ID_MIRRORING = "sys.mirroring"
        const val ID_NODATA = "sys.nodata"

        // Framework broadcasts without public constants; long stable across Android versions.
        private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        private const val BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        private const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
        private const val HOTSPOT_STATE = "android.net.wifi.WIFI_AP_STATE_CHANGED"
    }
}
