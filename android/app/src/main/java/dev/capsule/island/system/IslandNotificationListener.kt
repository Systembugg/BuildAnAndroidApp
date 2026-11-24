package dev.capsule.island.system

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.capsule.island.CapsuleApp
import dev.capsule.island.data.Module
import dev.capsule.island.engine.Action
import dev.capsule.island.engine.IslandActivity
import dev.capsule.island.engine.IslandEngine
import dev.capsule.island.engine.Img
import dev.capsule.island.engine.Payload
import dev.capsule.island.engine.Payload.Navigation.Maneuver
import dev.capsule.island.engine.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Reads other apps' notifications for live activities: calls, timers, navigation, transfers and
 * recordings, plus media sessions. Each notification maps to at most one activity, ended when the
 * notification goes away.
 */
class IslandNotificationListener : NotificationListenerService() {
    private var media: MediaHub? = null
    private var prefsJob: Job? = null
    /** Notification key to the activity it started. */
    private val owned = HashMap<String, String>()

    override fun onListenerConnected() {
        media = MediaHub(this, ComponentName(this, IslandNotificationListener::class.java)).also { it.attach() }
        prefsJob = CapsuleApp.instance.scope.launch {
            CapsuleApp.instance.prefs.collect { p -> media?.enabled = p.on(Module.Music) }
        }
        runCatching { activeNotifications }.getOrNull()?.forEach(::onNotificationPosted)
    }

    override fun onListenerDisconnected() {
        prefsJob?.cancel()
        media?.detach()
        media = null
        owned.values.toList().forEach(IslandEngine::end)
        owned.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val activity = runCatching { classify(sbn) }.getOrNull()
        val previous = owned[sbn.key]
        if (activity == null) {
            if (previous != null) { IslandEngine.end(previous); owned.remove(sbn.key) }
            return
        }
        if (previous != null && previous != activity.first.id) IslandEngine.end(previous)
        owned[sbn.key] = activity.first.id
        val wasRinging = (IslandEngine.get(activity.first.id)?.payload as? Payload.Call)?.incoming == true
        IslandEngine.start(activity.first, alert = activity.second && !IslandEngine.has(activity.first.id))
        // Answered: the card closes to the compact call, as on iOS.
        if (wasRinging && (activity.first.payload as? Payload.Call)?.incoming == false) IslandEngine.collapse()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        owned.remove(sbn.key)?.let(IslandEngine::end)
    }

    private val prefs get() = CapsuleApp.instance.prefs.value

    /** The activity for [sbn], and whether it should alert (expand) when it first appears. */
    private fun classify(sbn: StatusBarNotification): Pair<IslandActivity, Boolean>? {
        val n = sbn.notification
        val x = n.extras
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = x.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val pkg = sbn.packageName
        val ongoing = sbn.isOngoing
        val chrono = x.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        val open: () -> Unit = { send(n.contentIntent) }
        val id = "n:${sbn.key}"

        // Calls.
        val callType = x.getInt(Notification.EXTRA_CALL_TYPE, 0)
        val isCallStyle = x.getString(Notification.EXTRA_TEMPLATE)?.contains("CallStyle") == true
        if (prefs.on(Module.Calls) && (n.category == Notification.CATEGORY_CALL || isCallStyle)) {
            val incoming = callType == Notification.CallStyle.CALL_TYPE_INCOMING ||
                (callType == 0 && n.fullScreenIntent != null && !chrono)
            val person = x.parcel<android.app.Person>(Notification.EXTRA_CALL_PERSON)
            val name = person?.name?.toString() ?: title
            val avatar = (person?.icon ?: n.getLargeIcon())?.let(::bitmapOf)
            val video = x.getBoolean(Notification.EXTRA_CALL_IS_VIDEO, false) || text.contains("video", ignoreCase = true)
            val answer = x.parcel<PendingIntent>(Notification.EXTRA_ANSWER_INTENT) ?: actionLike(n, "answer", "accept")
            val decline = x.parcel<PendingIntent>(Notification.EXTRA_DECLINE_INTENT) ?: actionLike(n, "decline", "reject", "dismiss")
            val hangUp = x.parcel<PendingIntent>(Notification.EXTRA_HANG_UP_INTENT) ?: actionLike(n, "hang up", "end call", "end")
            val connectedAt = if (!incoming) toElapsed(n.`when`) else null
            val subtitle = if (incoming) (if (isDialer(pkg)) "Mobile" else appLabel(pkg)) else if (video) "Video" else appLabel(pkg)
            return IslandActivity(
                "call", Payload.Call(name, subtitle, avatar, incoming, video, connectedAt),
                if (incoming) Priority.INCOMING_CALL else Priority.CALL,
                actions = mapOf(
                    Action.Answer to { send(answer); Unit },
                    Action.Decline to { send(decline ?: hangUp); Unit },
                    Action.HangUp to { send(hangUp ?: decline); Unit },
                    Action.Open to open,
                ),
            ) to incoming
        }

        // Timers: a countdown chronometer, from any clock app.
        val countdown = x.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
        if (prefs.on(Module.Timers) && ongoing && chrono && countdown) {
            val endsAt = toElapsed(n.`when`)
            return IslandActivity(
                id, Payload.Timer(endsAt, endsAt - SystemClock.elapsedRealtime(), paused = false, label = "Timer"), Priority.TIMER,
                group = "timer",
                actions = mapOf(
                    Action.PauseResume to { send(actionLike(n, "pause", "resume")); Unit },
                    Action.Cancel to { send(actionLike(n, "cancel", "stop", "delete", "dismiss")); Unit },
                    Action.Open to open,
                ),
            ) to false
        }

        // Navigation.
        if (prefs.on(Module.Navigation) && (n.category == Notification.CATEGORY_NAVIGATION || (ongoing && pkg in NAV_APPS))) {
            val m = Regex("""^\s*([\d.,]+)\s*([^\s\d]+)?""").find(title)
            val distance = m?.groupValues?.get(1) ?: title.take(6)
            val unit = m?.groupValues?.get(2).orEmpty()
            val lower = (text + " " + title).lowercase()
            val maneuver = when {
                "u-turn" in lower || "u turn" in lower -> Maneuver.UTurn
                "left" in lower -> Maneuver.Left
                "right" in lower -> Maneuver.Right
                else -> Maneuver.Straight
            }
            val detail = x.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
            return IslandActivity(
                id, Payload.Navigation(distance, unit, text.ifEmpty { title }, detail, maneuver, n.getLargeIcon()?.let(::bitmapOf)),
                Priority.NAVIGATION,
                actions = mapOf(Action.Open to open, Action.EndRoute to { send(actionLike(n, "exit", "stop", "end")); Unit }),
            ) to false
        }

        // Recording.
        val recordingText = (title + " " + text).lowercase()
        val screenRec = "screenrecord" in pkg.lowercase() || ("screen" in recordingText && "record" in recordingText)
        val voiceRec = "recorder" in pkg.lowercase() && chrono
        if (prefs.on(Module.Recording) && ongoing && (screenRec || voiceRec)) {
            return IslandActivity(
                id, Payload.Recording(screen = screenRec, startedAt = toElapsed(n.`when`)), Priority.RECORDING,
                actions = mapOf(Action.Open to open, Action.Cancel to { send(actionLike(n, "stop", "end")); Unit }),
            ) to false
        }

        // Transfers: anything ongoing with determinate progress.
        val max = x.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        if (prefs.on(Module.Transfers) && ongoing && max > 0 && n.category != Notification.CATEGORY_TRANSPORT) {
            val progress = x.getInt(Notification.EXTRA_PROGRESS, 0).toFloat() / max
            return IslandActivity(
                id, Payload.Transfer(title.ifEmpty { appLabel(pkg) }, appLabel(pkg), progress, n.getLargeIcon()?.let(::bitmapOf)),
                Priority.ONGOING, group = "transfer:$pkg",
                actions = mapOf(Action.Open to open, Action.Cancel to { send(actionLike(n, "cancel", "stop")); Unit }),
            ) to false
        }
        return null
    }

    private fun send(intent: PendingIntent?) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                val opts = android.app.ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                intent?.send(this, 0, null, null, null, null, opts.toBundle())
            } else intent?.send()
        }
        IslandEngine.collapse()
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T> android.os.Bundle.parcel(key: String): T? = get(key) as? T

    private fun actionLike(n: Notification, vararg words: String): PendingIntent? =
        n.actions?.firstOrNull { a -> val t = a.title?.toString()?.lowercase().orEmpty(); words.any { it in t } }?.actionIntent

    private fun bitmapOf(icon: Icon): Img? = runCatching {
        val d = icon.loadDrawable(this) ?: return null
        Img.Bitmap(d.toBitmap(144, 144).asImageBitmap())
    }.getOrNull()

    private fun toElapsed(wallClock: Long) = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - wallClock)

    private fun appLabel(pkg: String) = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    private fun isDialer(pkg: String) = pkg in setOf("com.google.android.dialer", "com.android.dialer", "com.android.incallui", "com.android.server.telecom")

    private companion object {
        val NAV_APPS = setOf("com.google.android.apps.maps", "com.waze", "com.here.app.maps", "net.osmand", "com.sygic.aura", "com.mapmyindia.app.beta")
    }
}
