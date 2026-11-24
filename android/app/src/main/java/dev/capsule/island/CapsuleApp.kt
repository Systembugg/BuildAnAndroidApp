package dev.capsule.island

import android.app.Application
import dev.capsule.island.data.Prefs
import dev.capsule.island.data.Settings
import dev.capsule.island.system.Hosts
import dev.capsule.island.system.SystemEvents
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CapsuleApp : Application() {
    val settings by lazy { Settings(this) }
    private val _prefs = MutableStateFlow(Prefs())
    val prefs: StateFlow<Prefs> get() = _prefs
    val scope = MainScope()
    lateinit var events: SystemEvents
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        events = SystemEvents(this)
        scope.launch {
            settings.prefs.collect { p ->
                _prefs.value = p
                events.apply(p)
                Hosts.sync(this@CapsuleApp)
            }
        }
    }

    companion object {
        lateinit var instance: CapsuleApp
            private set
    }
}
