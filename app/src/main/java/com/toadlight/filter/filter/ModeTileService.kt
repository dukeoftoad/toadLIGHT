package com.toadlight.filter.filter

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
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
 * Quick-settings "mode" tile: cycles through the user's filter profiles
 * (modes) with one tap. The active profile is shown on the tile itself, so
 * the current mode is visible everywhere — app, notification, bubble, tile.
 *
 * Like [FilterTileService], this service is bound by SystemUI on the main
 * thread: no `runBlocking` anywhere. The cycle decision and the DataStore
 * write run on [tileScope] (single-threaded, so consecutive taps cycle in
 * order instead of racing); the engine observes the write and updates the
 * overlay (with a color cross-fade) on its own — no service restart needed.
 */
class ModeTileService : TileService() {

    private val graph get() = (applicationContext as ToadlightApp).graph

    private val tileScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }

    override fun onStartListening() {
        refreshTile(qsTile)
    }

    override fun onClick() {
        val tile = qsTile
        tileScope.launch {
            val profiles = graph.repo.profilesFlow.first()
            if (TileDecisions.shouldCycleMode(profiles.size)) {
                graph.repo.cycleNextProfile()
            }
            refreshTile(tile)
        }
    }

    /**
     * Applies the latest mode/master state to [tile]. A null tile (shade
     * closed before the coroutine ran) or an unbound tile (runCatching)
     * is safe to skip.
     */
    private fun refreshTile(tile: Tile?) {
        if (tile == null) return
        tileScope.launch {
            val profile = graph.activeProfileState.value
            val masterOn = graph.repo.settingsFlow.first().masterEnabled
            withContext(Dispatchers.Main) {
                runCatching {
                    tile.label = TileDecisions.modeTileLabel(profile?.name)
                    tile.state = if (masterOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    tile.updateTile()
                }
            }
        }
    }
}
