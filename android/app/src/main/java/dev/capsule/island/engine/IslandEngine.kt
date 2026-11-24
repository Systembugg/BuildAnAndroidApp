package dev.capsule.island.engine

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.capsule.island.core.Motion

data class IslandActivity(
    val id: String,
    val payload: Payload,
    /** Higher shows first. See [Priority]. */
    val priority: Int,
    /** Activities sharing a group show one at a time (one app's activities). */
    val group: String? = null,
    val startedAt: Long = SystemClock.elapsedRealtime(),
    val actions: Map<Action, () -> Unit> = emptyMap(),
) {
    fun run(action: Action) = actions[action]?.invoke()
}

object Priority {
    const val LOCK = 100
    const val INCOMING_CALL = 95
    const val ALERT = 80
    const val CALL = 75
    const val RECORDING = 70
    const val NAVIGATION = 65
    const val TIMER = 60
    const val MUSIC = 50
    const val ONGOING = 40
}

sealed interface Presentation {
    data object Idle : Presentation
    data class Compact(val activity: IslandActivity) : Presentation
    /** The iOS 27 split: [primary] in the pill, [secondary] in the detached bubble. */
    data class Split(val primary: IslandActivity, val secondary: IslandActivity) : Presentation
    data class Expanded(val activity: IslandActivity) : Presentation
}

data class IslandState(
    /** Every running activity, primary first (hidden group members included). */
    val activities: List<IslandActivity> = emptyList(),
    val expandedId: String? = null,
    /** Set while an alert holds the island open; its end uses the alert's collapse. */
    val alertId: String? = null,
    /** True when the last expansion came from a long-press (the swell carries into it). */
    val pressExpanded: Boolean = false,
    /** User swapped the split's sides. */
    val pinnedPrimary: String? = null,
) {
    /** Activities on show: one per group, primary first. */
    val visible: List<IslandActivity>
        get() {
            val seen = HashSet<String>()
            return activities.filter { a -> a.group == null || seen.add(a.group) }
        }

    val presentation: Presentation
        get() {
            val shown = visible
            val expanded = expandedId?.let { id -> activities.firstOrNull { it.id == id } }
            if (expanded != null) return Presentation.Expanded(expanded)
            return when (shown.size) {
                0 -> Presentation.Idle
                1 -> Presentation.Compact(shown[0])
                else -> {
                    val pinned = shown.firstOrNull { it.id == pinnedPrimary }
                    if (pinned != null) Presentation.Split(pinned, shown.first { it !== pinned })
                    else Presentation.Split(shown[0], shown[1])
                }
            }
        }
}

/**
 * The activity stack: what runs, what shows, and the timers that close alerts and transient
 * activities. Pure state; the island surface turns each change into motion. Main thread only.
 */
object IslandEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(IslandState())
    val state: StateFlow<IslandState> = _state.asStateFlow()

    private val ttlJobs = HashMap<String, Job>()
    private var alertJob: Job? = null

    private val order = compareByDescending<IslandActivity> { it.priority }.thenBy { it.startedAt }

    /**
     * Starts an activity, or updates it if [IslandActivity.id] is already running.
     * [alert] expands it for the alert hold (5.975 s), then returns it to compact.
     * [ttlMs] ends it automatically (transient activities: charging, ringer, unlock).
     */
    fun start(activity: IslandActivity, alert: Boolean = false, ttlMs: Long? = null) {
        _state.update { s ->
            val existing = s.activities.firstOrNull { it.id == activity.id }
            val merged = if (existing != null) activity.copy(startedAt = existing.startedAt) else activity
            s.copy(activities = (s.activities.filter { it.id != activity.id } + merged).sortedWith(order))
        }
        ttlJobs.remove(activity.id)?.cancel()
        if (ttlMs != null) ttlJobs[activity.id] = scope.launch { delay(ttlMs); end(activity.id) }
        if (alert) alert(activity.id)
    }

    /** Changes a running activity in place. No-op if it has ended. */
    fun update(id: String, alert: Boolean = false, transform: (IslandActivity) -> IslandActivity) {
        var found = false
        _state.update { s ->
            val current = s.activities.firstOrNull { it.id == id } ?: return@update s
            found = true
            s.copy(activities = s.activities.map { if (it.id == id) transform(current) else it }.sortedWith(order))
        }
        if (found && alert) alert(id)
    }

    fun end(id: String) {
        ttlJobs.remove(id)?.cancel()
        _state.update { s ->
            s.copy(
                activities = s.activities.filter { it.id != id },
                expandedId = s.expandedId.takeIf { it != id },
                alertId = s.alertId.takeIf { it != id },
                pinnedPrimary = s.pinnedPrimary.takeIf { it != id },
            )
        }
    }

    fun has(id: String) = _state.value.activities.any { it.id == id }
    fun get(id: String) = _state.value.activities.firstOrNull { it.id == id }

    /** Expands [id], or the primary activity. [fromPress] carries a long-press swell into it. */
    fun expand(id: String? = null, fromPress: Boolean = false) {
        alertJob?.cancel()
        _state.update { s ->
            val target = id ?: (s.presentation as? Presentation.Compact)?.activity?.id
                ?: (s.presentation as? Presentation.Split)?.primary?.id
                ?: return@update s
            s.copy(expandedId = target, alertId = null, pressExpanded = fromPress)
        }
    }

    fun collapse() {
        alertJob?.cancel()
        _state.update { it.copy(expandedId = null, alertId = null, pressExpanded = false) }
    }

    /** Swaps the split's pill and bubble. */
    fun swapSplit() {
        val p = _state.value.presentation as? Presentation.Split ?: return
        _state.update { it.copy(pinnedPrimary = p.secondary.id) }
    }

    private fun alert(id: String) {
        alertJob?.cancel()
        _state.update { it.copy(expandedId = id, alertId = id, pressExpanded = false) }
        alertJob = scope.launch {
            delay((Motion.ALERT_HOLD * 1000).toLong())
            _state.update { s -> if (s.alertId == id) s.copy(expandedId = null) else s }
            // Keep alertId through the collapse so the surface picks the alert's motion.
            delay(50)
            _state.update { s -> if (s.alertId == id && s.expandedId == null) s.copy(alertId = null) else s }
        }
    }
}
