package com.toadlight.filter.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toadlight.filter.core.AppGraph
import com.toadlight.filter.core.model.Profile
import com.toadlight.filter.core.model.ScheduleMode
import com.toadlight.filter.core.util.LocationGrabber
import com.toadlight.filter.filter.FilterService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class HomeViewModel(
    private val appContext: Context,
    private val graph: AppGraph,
) : ViewModel() {

    val engineState = graph.engine.state
    val settings = graph.settingsState
    val activeProfile = graph.activeProfileState
    val profiles = graph.profilesState
    val serviceRunning = graph.serviceRunning
    val overlayAttachFailed = graph.overlayAttachFailed

    /** Location fetch feedback for the UI. */
    val locationStatus = MutableStateFlow<String?>(null)

    // Debounced write-through pipes so dragging a slider updates the real
    // overlay with a light ~200 ms lag instead of hammering DataStore.
    private val tempPipe = MutableStateFlow<Int?>(null)
    private val intensityPipe = MutableStateFlow<Int?>(null)
    private val dimPipe = MutableStateFlow<Int?>(null)
    private val paperPipe = MutableStateFlow<Int?>(null)

    init {
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            tempPipe.filterNotNull().debounce(200).collect { v ->
                graph.repo.updateActiveProfile { it.copy(colorTempK = v) }
            }
        }
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            intensityPipe.filterNotNull().debounce(200).collect { v ->
                graph.repo.updateActiveProfile { it.copy(intensityPercent = v) }
            }
        }
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            dimPipe.filterNotNull().debounce(200).collect { v ->
                graph.repo.updateActiveProfile { it.copy(dimPercent = v) }
            }
        }
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            paperPipe.filterNotNull().debounce(200).collect { v ->
                graph.repo.updateActiveProfile { it.copy(paperIntensityPercent = v) }
            }
        }
    }

    fun setMaster(on: Boolean) {
        viewModelScope.launch {
            graph.repo.setMasterEnabled(on)
            if (on) {
                FilterService.start(appContext)
            }
            // Turning off: the running service observes the change and stops
            // itself after a short grace period. No racing writes needed.
        }
    }

    /**
     * Forces the filter on right now for [durationMs], bypassing the
     * schedule. Visible immediately – no waiting for sunset.
     */
    fun turnOnNow(durationMs: Long = 2 * 60 * 60_000L) {
        viewModelScope.launch {
            graph.repo.setMasterEnabled(true)
            graph.repo.startPreview(durationMs)
            if (!graph.serviceRunning.value) {
                FilterService.start(appContext)
            }
        }
    }

    /** Drops a running "On now" preview; the schedule takes over. */
    fun endPreviewNow() {
        viewModelScope.launch { graph.repo.endPreview() }
    }

    /** Restarts the service if the system killed it while it should run. */
    fun restartService() {
        viewModelScope.launch {
            if (graph.settingsState.value.masterEnabled) {
                FilterService.start(appContext)
            }
        }
    }

    /**
     * Called on every slider drag: if the filter is currently invisible
     * (daytime, closed schedule), start a short preview so the user sees the
     * effect of their changes live on the real screen.
     */
    fun nudgePreview() {
        val s = graph.engine.state.value
        if (!s.masterOn || s.paused || s.overlayOn || s.previewing) return
        viewModelScope.launch { graph.repo.startPreview(2 * 60_000L) }
    }

    /**
     * Single smart toggle for the play/pause button: pauses a visible filter,
     * resumes a paused one, and turns the filter on (2 h preview) when the
     * schedule keeps it off.
     */
    fun playPause() {
        viewModelScope.launch {
            val s = graph.engine.state.value
            when {
                s.paused -> graph.repo.resume()
                s.overlayOn || s.previewing -> graph.repo.pauseFor(30 * 60_000L)
                else -> {
                    graph.repo.setMasterEnabled(true)
                    graph.repo.startPreview(2 * 60 * 60_000L)
                    if (!graph.serviceRunning.value) {
                        FilterService.start(appContext)
                    }
                }
            }
        }
    }

    fun pause(durationMs: Long) {
        viewModelScope.launch {
            graph.repo.pauseFor(durationMs)
            if (!graph.serviceRunning.value) {
                FilterService.start(appContext)
            }
        }
    }

    fun resume() {
        viewModelScope.launch { graph.repo.resume() }
    }

    /** Switches the active mode/profile — applies globally, everywhere. */
    fun setActiveProfile(id: String) {
        viewModelScope.launch { graph.repo.setActiveProfile(id) }
    }

    /** Enables or disables the paper-like finish on the active profile. */
    fun setPaper(on: Boolean) {
        viewModelScope.launch {
            graph.repo.updateActiveProfile { it.copy(paperFinish = on) }
        }
    }

    fun setPaperIntensity(value: Int) {
        paperPipe.value = value.coerceIn(0, 100)
    }

    fun setTemp(value: Int) {
        tempPipe.value = value.coerceIn(1000, 6500)
    }

    fun setIntensity(value: Int) {
        intensityPipe.value = value.coerceIn(0, 100)
    }

    fun setDim(value: Int) {
        dimPipe.value = value.coerceIn(0, 100)
    }

    fun setScheduleMode(mode: ScheduleMode) {
        viewModelScope.launch {
            graph.repo.updateActiveProfile { it.copy(schedule = it.schedule.copy(mode = mode)) }
        }
    }

    fun setCustomTimes(startMinutes: Int, endMinutes: Int) {
        viewModelScope.launch {
            graph.repo.updateActiveProfile {
                it.copy(
                    schedule = it.schedule.copy(
                        startMinutes = startMinutes.coerceIn(0, 24 * 60),
                        endMinutes = endMinutes.coerceIn(0, 24 * 60),
                    ),
                )
            }
        }
    }

    fun refreshLocation() {
        locationStatus.value = "Locating…"
        viewModelScope.launch {
            val loc = LocationGrabber.currentLocation(appContext)
            if (loc != null) {
                graph.repo.setLocation(loc.first, loc.second)
                locationStatus.value = "Location updated"
            } else {
                locationStatus.value = "No fix – check location services or enter coordinates in Settings"
            }
        }
    }

    fun clearLocationStatus() {
        locationStatus.value = null
    }
}
