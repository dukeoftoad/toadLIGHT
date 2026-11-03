package com.toadlight.filter.filter

import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.toadlight.filter.MainActivity
import com.toadlight.filter.ToadlightApp
import com.toadlight.filter.core.TileDecisions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick-settings tile: taps toggle the filter master switch.
 *
 * A TileService is bound by SystemUI and every callback here runs on **its**
 * main thread — a blocking DataStore read (`runBlocking { flow.first() }`)
 * can freeze SystemUI and trip an ANR. All suspend work therefore runs on
 * [tileScope] instead:
 *
 *  - the scope is single-threaded (`limitedParallelism(1)`) so rapid taps
 *    stay ordered: each job re-reads fresh state before writing, which
 *    keeps the toggle semantics correct without ever blocking the caller;
 *  - `qsTile` is captured on the main thread and only mutated inside
 *    `withContext(Main)` wrapped in [runCatching], because the tile can be
 *    unbound at any moment after the user leaves the shade;
 *  - `startActivityAndCollapse` must be called synchronously from `onClick`
 *    (API 34+ requirement for launching from a tile), so the setup
 *    redirect happens before any coroutine is launched.
 */
class FilterTileService : TileService() {

    private val graph get() = (applicationContext as ToadlightApp).graph

    private val tileScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }

    override fun onStartListening() {
        refreshTile()
    }

    override fun onClick() {
        val action = TileDecisions.onFilterClicked(
            canDrawOverlays = Settings.canDrawOverlays(this),
            masterOn = graph.settingsState.value.masterEnabled,
        )

        when (action) {
            TileDecisions.FilterAction.REQUEST_SETUP -> {
                // Without the overlay permission the app cannot filter
                // anything: send the user to the app to finish setup.
                @Suppress("DEPRECATION")
                startActivityAndCollapse(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }

            TileDecisions.FilterAction.TURN_ON -> tileScope.launch {
                // Persist FIRST (the service used to die instantly when
                // settings still said "off"), then make sure it is running.
                graph.repo.setMasterEnabled(true)
                FilterService.start(this@FilterTileService)
                refreshTile()
            }

            TileDecisions.FilterAction.TURN_OFF -> tileScope.launch {
                // Persist immediately, then stop a running service. This
                // also works when the service was already killed by the
                // system.
                graph.repo.setMasterEnabled(false)
                if (graph.serviceRunning.value) {
                    FilterService.stop(this@FilterTileService)
                }
                refreshTile()
            }
        }
    }

    /**
     * Reads fresh state and updates the tile. Called both from the main
     * thread (onStartListening) and from [tileScope]; the tile mutation
     * itself always hops back to the main thread.
     */
    private fun refreshTile() {
        val tile = qsTile ?: return
        tileScope.launch {
            val masterOn = graph.repo.settingsFlow.first().masterEnabled
            withContext(Dispatchers.Main) {
                runCatching {
                    tile.state = if (TileDecisions.filterTileActive(masterOn)) {
                        Tile.STATE_ACTIVE
                    } else {
                        Tile.STATE_INACTIVE
                    }
                    tile.updateTile()
                }
            }
        }
    }
}
