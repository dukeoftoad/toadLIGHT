package com.toadlight.filter.filter

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.toadlight.filter.ToadlightApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Quick-settings "mode" tile: cycles through the user's filter profiles
 * (modes) with one tap. The active profile is shown on the tile itself, so
 * the current mode is visible everywhere — app, notification, bubble, tile.
 */
class ModeTileService : TileService() {

    private val graph get() = (applicationContext as ToadlightApp).graph

    override fun onStartListening() {
        updateTile()
    }

    override fun onClick() {
        runBlocking {
            val profiles = graph.repo.profilesFlow.first()
            if (profiles.size < 2) return@runBlocking
            graph.repo.cycleNextProfile()
        }
        // The engine observes the DataStore write and updates the overlay
        // (with a color cross-fade) on its own; no service restart needed.
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        runBlocking {
            val profile = graph.activeProfileState.first()
            val masterOn = graph.repo.settingsFlow.first().masterEnabled
            tile.label = profile?.let { "Mode · ${it.name}" } ?: "Mode"
            tile.state = if (masterOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}
