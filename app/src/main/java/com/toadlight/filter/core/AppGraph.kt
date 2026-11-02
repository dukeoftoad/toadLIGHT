package com.toadlight.filter.core

import android.content.Context
import com.toadlight.filter.core.model.AppSettings
import com.toadlight.filter.core.model.Profile
import com.toadlight.filter.data.AppRepository
import com.toadlight.filter.filter.FilterEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Hand-rolled dependency graph (kept intentionally simple – the app is small
 * enough that a DI framework would be overkill).
 */
class AppGraph(context: Context) {

    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repo = AppRepository(appContext)
    val engine = FilterEngine(this)

    val settingsState: kotlinx.coroutines.flow.StateFlow<AppSettings> =
        repo.settingsFlow.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    val profilesState: kotlinx.coroutines.flow.StateFlow<List<Profile>> =
        repo.profilesFlow.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val activeProfileIdState: kotlinx.coroutines.flow.StateFlow<String> =
        repo.activeProfileIdFlow.stateIn(scope, SharingStarted.Eagerly, "")

    val activeProfileState: kotlinx.coroutines.flow.StateFlow<Profile?> =
        combine(profilesState, activeProfileIdState) { list, id ->
            list.firstOrNull { it.id == id } ?: list.firstOrNull()
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Set by FilterService so external entry points (tile) know if it is alive. */
    val serviceRunning = MutableStateFlow(false)

    /**
     * True when the system refused to attach the overlay window (e.g. an OEM
     * blocked it). The UI surfaces a troubleshooting card while this is set.
     */
    val overlayAttachFailed = MutableStateFlow(false)

    init {
        scope.launch { repo.seedIfEmpty() }
        engine.observe(scope)
    }
}
