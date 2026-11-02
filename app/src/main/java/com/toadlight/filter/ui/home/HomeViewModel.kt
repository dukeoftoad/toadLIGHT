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

    /** Location fetch feedback for the UI. */
    val locationStatus = MutableStateFlow<String?>(null)

    // Debounced write-through pipes so dragging a slider updates the real
    // overlay with a light ~200 ms lag instead of hammering DataStore.
    private val tempPipe = MutableStateFlow<Int?>(null)
    private val intensityPipe = MutableStateFlow<Int?>(null)
    private val dimPipe = MutableStateFlow<Int?>(null)

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
    }

    fun setMaster(on: Boolean) {
        viewModelScope.launch {
            graph.repo.setMasterEnabled(on)
            if (on) {
                FilterService.start(appContext)
            } else if (graph.serviceRunning.value) {
                FilterService.stop(appContext)
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
