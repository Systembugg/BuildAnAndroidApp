package dev.capsule.island.system

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import dev.capsule.island.CapsuleApp
import dev.capsule.island.R
import dev.capsule.island.engine.Action
import dev.capsule.island.engine.IslandActivity
import dev.capsule.island.engine.IslandEngine
import dev.capsule.island.engine.Img
import dev.capsule.island.engine.Payload
import dev.capsule.island.engine.Payload.Navigation.Maneuver
import dev.capsule.island.engine.Priority

/** Every component in the kit, with the kit's sample content. Used by the Stage and automation. */
enum class Demo(val title: String, val group: String) {
    Unlock("Unlock", "System"),
    Music("Music", "Media"),
    AirPlay("AirPlay", "Media"),
    SharePlay("SharePlay", "Media"),
    IncomingCall("Incoming call", "Calls"),
    OngoingCall("Ongoing call", "Calls"),
    FaceTime("FaceTime", "Calls"),
    Timer("Timer", "Live"),
    Navigation("Navigation", "Live"),
    Transit("Transit", "Live"),
    AirDrop("AirDrop", "Live"),
    VoiceRecording("Voice memo", "Live"),
    ScreenRecording("Screen recording", "Live"),
    Charging("Charging", "System"),
    LowBattery("Low battery", "System"),
    CriticalBattery("Critical battery", "System"),
    Silent("Silent", "System"),
    Ring("Ring", "System"),
    RingVolume("Ringer volume", "System"),
    DoNotDisturb("Do Not Disturb", "System"),
    Driving("Driving", "System"),
    FaceId("Face ID", "System"),
    AirPods("AirPods", "Devices"),
    AirPodsMoved("AirPods moved", "Devices"),
    Hotspot("Hotspot", "Devices"),
    Mirroring("Mirroring", "Devices"),
    Shortcut("Shortcut", "Alerts"),
    Airplane("Airplane mode", "Alerts"),
    NoData("No data", "Alerts"),
    Satellite("Satellite", "Alerts"),
    FindMy("Find My", "Alerts"),
    ;

    val id get() = "demo.${name.lowercase()}"
}

object Demos {
    private val main = Handler(Looper.getMainLooper())

    fun running(d: Demo) = IslandEngine.has(d.id)

    /** Starts [d], or ends it if it is already running. [title]/[subtitle] override the kit's text. */
    fun toggle(d: Demo, title: String? = null, subtitle: String? = null, value: Float? = null) {
        if (d == Demo.Unlock) { CapsuleApp.instance.events.unlock(); return }
        if (running(d)) { IslandEngine.end(d.id); return }
        start(d, title, subtitle, value)
    }

    fun start(d: Demo, title: String? = null, subtitle: String? = null, value: Float? = null, alertOverride: Boolean? = null) {
        val id = d.id
        val now = SystemClock.elapsedRealtime()
        val end = { IslandEngine.end(id) }
        fun go(payload: Payload, priority: Int, alert: Boolean = false, ttl: Long? = null, actions: Map<Action, () -> Unit> = emptyMap(), group: String? = null) =
            IslandEngine.start(IslandActivity(id, payload, priority, group, actions = actions + (Action.Dismiss to end)), alertOverride ?: alert, ttl)

        when (d) {
            Demo.Unlock -> CapsuleApp.instance.events.unlock()
            Demo.Music, Demo.AirPlay -> {
                val remote = d == Demo.AirPlay
                fun music(playing: Boolean, position: Long) = Payload.Music(
                    title = title ?: if (remote) "Video" else "Heat Waves",
                    artist = subtitle ?: "Grass Animals",
                    art = if (remote) null else Img.Res(R.drawable.demo_artwork),
                    accent = Color(0xFFF84BAB), accentEnd = Color(0xFFB4CDFB),
                    playing = playing, durationMs = 241_000, positionMs = position, positionAt = SystemClock.elapsedRealtime(), speed = 1f,
                    remote = remote, device = if (remote) "Samsung TV" else null, explicit = !remote,
                )
                lateinit var actions: Map<Action, () -> Unit>
                actions = mapOf(
                    Action.PlayPause to {
                        IslandEngine.update(id) { a ->
                            val m = a.payload as Payload.Music
                            val pos = if (m.playing) m.positionMs + (SystemClock.elapsedRealtime() - m.positionAt) else m.positionMs
                            a.copy(payload = music(!m.playing, pos))
                        }
                    },
                    Action.Next to { IslandEngine.update(id) { it.copy(payload = music(true, 0)) } },
                    Action.Previous to { IslandEngine.update(id) { it.copy(payload = music(true, 0)) } },
                )
                go(music(true, 50_000), Priority.MUSIC, actions = actions, group = "media")
            }
            Demo.SharePlay -> go(Payload.SharePlay(title ?: "Asia Wild Nature", subtitle ?: "For me", Img.Res(R.drawable.demo_artwork_video)), Priority.CALL, actions = mapOf(Action.HangUp to end))
            Demo.IncomingCall, Demo.OngoingCall, Demo.FaceTime -> {
                val video = d == Demo.FaceTime
                val name = title ?: "Tamia Castillo"
                fun call(incoming: Boolean, connectedAt: Long?, muted: Boolean = false, speaker: Boolean = false) = Payload.Call(
                    name, if (incoming) "Mobile" else "FaceTime Audio",
                    Img.Res(if (incoming) R.drawable.demo_avatar_large else R.drawable.demo_avatar), incoming, video, connectedAt, muted, speaker,
                )
                val toggleOf = { f: (Payload.Call) -> Payload.Call -> { IslandEngine.update(id) { it.copy(payload = f(it.payload as Payload.Call)) } } as () -> Unit }
                val actions = mapOf(
                    Action.Answer to {
                        IslandEngine.update(id) { it.copy(payload = call(false, SystemClock.elapsedRealtime()), priority = Priority.CALL) }
                        IslandEngine.collapse()
                    },
                    Action.Decline to end,
                    Action.HangUp to end,
                    Action.Mute to toggleOf { it.copy(muted = !it.muted) },
                    Action.Speaker to toggleOf { it.copy(speaker = !it.speaker) },
                )
                if (d == Demo.IncomingCall) go(call(true, null), Priority.INCOMING_CALL, alert = true, actions = actions)
                else go(call(false, now - 8_000), Priority.CALL, actions = actions)
            }
            Demo.Timer -> {
                val total = ((value ?: 215f) * 1000).toLong()
                val actions = mapOf(
                    Action.PauseResume to {
                        IslandEngine.update(id) { a ->
                            val t = a.payload as Payload.Timer
                            val left = if (t.paused) t.remainingMs else t.endsAt - SystemClock.elapsedRealtime()
                            a.copy(payload = t.copy(paused = !t.paused, remainingMs = left, endsAt = SystemClock.elapsedRealtime() + left))
                        }
                    },
                    Action.Cancel to end,
                )
                go(Payload.Timer(now + total, total, paused = false), Priority.TIMER, actions = actions, group = "timer")
                main.postDelayed({
                    val t = IslandEngine.get(id)?.payload as? Payload.Timer
                    if (t != null && !t.paused) end()
                }, total)
            }
            Demo.Navigation -> go(
                Payload.Navigation(
                    "0.2", "mi", title ?: "North", subtitle ?: "San Francisco", Maneuver.Right, null,
                    lanes = listOf(Maneuver.Left, Maneuver.Straight, Maneuver.Straight, Maneuver.Right), activeLane = 3,
                    shield = Img.Res(R.drawable.demo_route_shield),
                ),
                Priority.NAVIGATION, actions = mapOf(Action.EndRoute to end),
            )
            Demo.Transit -> go(Payload.Transit(title ?: "Prague Main Train Station"), Priority.NAVIGATION, actions = mapOf(Action.EndRoute to end))
            Demo.AirDrop -> {
                go(Payload.Transfer(title ?: "AirDrop from Chi", subtitle, 0.1f, Img.Res(R.drawable.demo_airdrop_photo)), Priority.ONGOING, actions = mapOf(Action.Cancel to end))
                var p = 0.1f
                fun step() {
                    if (!IslandEngine.has(id)) return
                    p += 0.06f
                    if (p >= 1f) { end(); return }
                    IslandEngine.update(id) { it.copy(payload = (it.payload as Payload.Transfer).copy(progress = p)) }
                    main.postDelayed({ step() }, 600)
                }
                main.postDelayed({ step() }, 600)
            }
            Demo.VoiceRecording -> go(Payload.Recording(screen = false, startedAt = now - 6_000), Priority.RECORDING, actions = mapOf(Action.Cancel to end))
            Demo.ScreenRecording -> go(Payload.Recording(screen = true, startedAt = now - 6_000), Priority.RECORDING, actions = mapOf(Action.Cancel to end))
            Demo.Charging -> go(Payload.Battery((value ?: 75f).toInt(), Payload.Battery.Kind.Charging), Priority.ALERT, ttl = 3800)
            Demo.LowBattery -> go(Payload.Battery((value ?: 20f).toInt(), Payload.Battery.Kind.Low), Priority.ALERT, ttl = 3800)
            Demo.CriticalBattery -> go(Payload.Battery((value ?: 1f).toInt(), Payload.Battery.Kind.Critical), Priority.ALERT, ttl = 3800)
            Demo.Silent -> go(Payload.Ringer(silent = true), Priority.ALERT, ttl = 2600, actions = mapOf(Action.Unmute to { IslandEngine.update(id) { it.copy(payload = Payload.Ringer(false)) }; IslandEngine.collapse() }))
            Demo.Ring -> go(Payload.Ringer(silent = false), Priority.ALERT, ttl = 2600)
            Demo.RingVolume -> go(Payload.RingVolume(value ?: 0.55f), Priority.ALERT, ttl = 2200)
            Demo.DoNotDisturb -> go(Payload.Focus(Payload.Focus.Kind.DoNotDisturb, true), Priority.ALERT, ttl = 2600)
            Demo.Driving -> go(Payload.Focus(Payload.Focus.Kind.Driving, true), Priority.ALERT, ttl = 2600)
            Demo.FaceId -> go(Payload.FaceId(), Priority.LOCK, alert = true, ttl = 2200)
            Demo.AirPods -> go(Payload.AirPods(title ?: "Ladislav\u2019s Air Pods", (value ?: 75f).toInt()), Priority.ALERT, alert = true, ttl = 5000)
            Demo.AirPodsMoved -> go(Payload.AirPods(title ?: "AirPods", null, moved = true), Priority.ALERT, alert = true, ttl = 5000, actions = mapOf(Action.Return to end))
            Demo.Hotspot -> go(Payload.Hotspot(value?.toInt() ?: 1), Priority.ONGOING)
            Demo.Mirroring -> go(Payload.Mirroring(title ?: "MacBook Pro"), Priority.ONGOING, actions = mapOf(Action.StopMirroring to end))
            Demo.Shortcut -> {
                go(Payload.Shortcut(title ?: "Shortcut", 0.2f, false), Priority.ALERT, alert = true, ttl = 4200)
                main.postDelayed({ IslandEngine.update(id) { it.copy(payload = Payload.Shortcut(title ?: "Shortcut", 1f, true)) } }, 1600)
            }
            Demo.Airplane -> go(Payload.Airplane, Priority.ALERT, alert = true, ttl = 5500, actions = mapOf(Action.OpenSettings to end))
            Demo.NoData -> go(Payload.NoData, Priority.ALERT, alert = true, ttl = 6000, actions = mapOf(Action.OpenSettings to end))
            Demo.Satellite -> go(Payload.Satellite, Priority.ONGOING)
            Demo.FindMy -> go(Payload.FindMy(title ?: "Find My iPhone Alert"), Priority.ALERT, alert = true, ttl = 5000)
        }
    }
}
