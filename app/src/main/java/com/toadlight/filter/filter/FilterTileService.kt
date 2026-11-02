package com.toadlight.filter.filter

import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.toadlight.filter.ToadlightApp
import com.toadlight.filter.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Quick-settings tile: taps toggle the filter master switch.
 */
class FilterTileService : TileService() {

    private val graph get() = (applicationContext as ToadlightApp).graph

    override fun onStartListening() {
        updateTile()
    }

    override fun onClick() {
        val masterOn = runBlocking { graph.repo.settingsFlow.first().masterEnabled }

        if (!Settings.canDrawOverlays(this)) {
            // Without the overlay permission the app cannot filter anything:
            // send the user to the app to finish setup.
            @Suppress("DEPRECATION")
            startActivityAndCollapse(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }

        if (masterOn) {
            if (graph.serviceRunning.value) {
                FilterService.stop(this)
            }
        } else {
            FilterService.start(this)
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val masterOn = runBlocking { graph.repo.settingsFlow.first().masterEnabled }
        tile.state = if (masterOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
