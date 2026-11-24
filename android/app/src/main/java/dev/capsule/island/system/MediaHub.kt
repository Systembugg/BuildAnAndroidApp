package dev.capsule.island.system

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import dev.capsule.island.engine.Action
import dev.capsule.island.engine.IslandActivity
import dev.capsule.island.engine.IslandEngine
import dev.capsule.island.engine.Img
import dev.capsule.island.engine.Payload
import dev.capsule.island.engine.Priority

/**
 * Follows the active media session: title, artist, art (and its colours for the bars), position
 * and transport controls. Callback-driven; nothing runs between changes.
 */
class MediaHub(private val context: Context, private val listener: ComponentName) {
    private val main = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private var controller: MediaController? = null
    private var artKey: String? = null
    private var art: Img? = null
    private var accent = Color(0xFFF84BAB) to Color(0xFFB4CDFB)
    var enabled = true
        set(value) { field = value; if (!value) clear() else pick() }

    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { pick() }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = pick()
    }

    private val endPaused = Runnable { IslandEngine.end(ID) }

    fun attach() {
        runCatching { manager.addOnActiveSessionsChangedListener(sessions, listener, main) }
        pick()
    }

    fun detach() {
        runCatching { manager.removeOnActiveSessionsChangedListener(sessions) }
        clear()
    }

    private fun clear() {
        controller?.unregisterCallback(callback)
        controller = null
        IslandEngine.end(ID)
    }

    private fun pick() {
        if (!enabled) return
        val list = runCatching { manager.getActiveSessions(listener) }.getOrDefault(emptyList())
        val next = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = next
            next?.registerCallback(callback, main)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val state = c?.playbackState
        val meta = c?.metadata
        val playing = state?.state == PlaybackState.STATE_PLAYING
        val paused = state?.state == PlaybackState.STATE_PAUSED
        if (c == null || meta == null || !(playing || paused)) {
            IslandEngine.end(ID)
            return
        }
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val key = "$title|$artist|${c.packageName}"
        if (key != artKey) {
            artKey = key
            val bitmap = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            if (bitmap != null) {
                val small = runCatching { bitmap.scale(160, 160) }.getOrNull()
                art = small?.let { Img.Bitmap(it.asImageBitmap()) }
                accent = small?.let(::accents) ?: accent
            } else {
                art = null
            }
        }
        val remote = c.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE
        val controls = c.transportControls
        val payload = Payload.Music(
            title = title,
            artist = artist,
            art = art,
            accent = accent.first,
            accentEnd = accent.second,
            playing = playing,
            durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
            positionMs = state?.position ?: 0,
            positionAt = state?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            remote = remote,
            device = if (remote) "Cast device" else null,
        )
        IslandEngine.start(
            IslandActivity(
                ID, payload, Priority.MUSIC, group = "media",
                actions = mapOf(
                    Action.PlayPause to { if (c.playbackState?.state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play() },
                    Action.Next to { controls.skipToNext() },
                    Action.Previous to { controls.skipToPrevious() },
                    Action.Open to { runCatching { c.sessionActivity?.send() }; Unit },
                ),
            ),
        )
        main.removeCallbacks(endPaused)
        if (paused) main.postDelayed(endPaused, PAUSED_TIMEOUT)
    }

    /** Two colours from the artwork for the bars' gradient: its most saturated light tones. */
    private fun accents(b: Bitmap): Pair<Color, Color> {
        val s = b.scale(8, 8)
        val hsv = FloatArray(3)
        val picks = (0 until 64).map { s.getPixel(it % 8, it / 8) }
            .map { px -> AColor.colorToHSV(px, hsv); Triple(px, hsv[1] * hsv[2], hsv[0]) }
            .sortedByDescending { it.second }
        val first = picks.firstOrNull() ?: return accent
        val second = picks.firstOrNull { kotlin.math.abs(it.third - first.third) > 40f } ?: first
        fun bright(px: Int): Color {
            val f = FloatArray(3)
            AColor.colorToHSV(px, f)
            f[2] = maxOf(f[2], 0.85f)
            f[1] = minOf(f[1], 0.75f)
            return Color(AColor.HSVToColor(f))
        }
        if (first.second < 0.12f) return Color(0xFFA8A8A8) to Color(0xFFD9D9D9)
        return bright(first.first) to bright(second.first)
    }

    companion object {
        const val ID = "media"
        private const val PAUSED_TIMEOUT = 10 * 60 * 1000L
    }
}
