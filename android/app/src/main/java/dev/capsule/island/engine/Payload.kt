package dev.capsule.island.engine

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/** An image from a resource (demo, bundled art) or a live bitmap (album art, caller photo). */
sealed interface Img {
    data class Res(@DrawableRes val id: Int) : Img
    class Bitmap(val bitmap: ImageBitmap) : Img
}

/**
 * What an activity shows. One case per component type in the Figma kit; the renderer in
 * `ui/activities` draws each case's minimal, compact and expanded views from the kit's specs.
 */
sealed interface Payload {
    /** Now playing. [remote] switches to the kit's AirPlay variant (casting to another device). */
    data class Music(
        val title: String,
        val artist: String,
        val art: Img?,
        val accent: Color,
        val accentEnd: Color,
        val playing: Boolean,
        val durationMs: Long,
        val positionMs: Long,
        /** elapsedRealtime of [positionMs], so the bar can extrapolate without ticks. */
        val positionAt: Long,
        val speed: Float,
        val remote: Boolean = false,
        val device: String? = null,
        val explicit: Boolean = false,
    ) : Payload

    /** A transfer with progress: Quick Share, downloads, uploads (the kit's AirDrop). */
    data class Transfer(val title: String, val subtitle: String?, val progress: Float?, val photo: Img?) : Payload

    data class Call(
        val name: String,
        val subtitle: String,
        val avatar: Img?,
        val incoming: Boolean,
        val video: Boolean,
        /** elapsedRealtime the call connected, or null while ringing. */
        val connectedAt: Long?,
        val muted: Boolean = false,
        val speaker: Boolean = false,
    ) : Payload

    data class Battery(val level: Int, val kind: Kind) : Payload {
        enum class Kind { Charging, Low, Critical }
    }

    data class Ringer(val silent: Boolean) : Payload
    data class RingVolume(val fraction: Float) : Payload
    data class Focus(val kind: Kind, val on: Boolean) : Payload {
        enum class Kind { DoNotDisturb, Driving }
    }

    data class Shortcut(val name: String, val progress: Float, val done: Boolean) : Payload

    data class Recording(val screen: Boolean, val startedAt: Long, val levels: List<Float> = emptyList()) : Payload

    /** [endsAt] is elapsedRealtime when running; [remainingMs] holds the time left while paused. */
    data class Timer(val endsAt: Long, val remainingMs: Long, val paused: Boolean, val label: String = "Timer") : Payload

    data class Navigation(
        val distance: String,
        val unit: String,
        val instruction: String,
        val detail: String,
        val maneuver: Maneuver,
        val arrow: Img?,
        val lanes: List<Maneuver> = emptyList(),
        val activeLane: Int = -1,
        val shield: Img? = null,
    ) : Payload {
        enum class Maneuver { Left, Straight, Right, UTurn }
    }

    /** Lock to unlock. [unlocked] runs the shackle; the kit's Lock and UnLock components. */
    data class Lock(val unlocked: Boolean) : Payload

    data class FaceId(val scanning: Boolean = true) : Payload

    data class AirPods(val name: String, val battery: Int?, val moved: Boolean = false) : Payload

    data class Hotspot(val devices: Int) : Payload
    data class Mirroring(val device: String) : Payload
    data class Transit(val station: String) : Payload
    data object NoData : Payload
    data object Airplane : Payload
    data class SharePlay(val title: String, val subtitle: String, val art: Img?) : Payload
    data object Satellite : Payload
    data class FindMy(val text: String) : Payload
}

/** Things an activity's views can do. Wired by whoever started it. */
enum class Action {
    Open, PlayPause, Next, Previous, Answer, Decline, HangUp, Mute, Speaker, Video,
    PauseResume, Cancel, Unmute, StopMirroring, OpenSettings, Dismiss, EndRoute, Return,
}
