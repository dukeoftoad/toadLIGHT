package com.toadlight.filter.core

/**
 * Pure decision logic for the quick-settings tiles, extracted from the two
 * [android.service.quicksettings.TileService] implementations so the
 * branching can be unit-tested on the JVM without Android.
 *
 * The tile services stay thin adapters: they read a snapshot of the current
 * state, ask [TileDecisions] what should happen, then perform the side
 * effects (DataStore writes, service start/stop) inside a coroutine — never
 * with blocking reads on SystemUI's main thread.
 */
object TileDecisions {

    /** What the filter (master switch) tile should do for a click. */
    enum class FilterAction {
        /** Overlay permission is missing: open the app to finish setup. */
        REQUEST_SETUP,

        /** Master is off: persist on, then make sure the service runs. */
        TURN_ON,

        /** Master is on: persist off, then stop the service if running. */
        TURN_OFF,
    }

    /**
     * Decides what a click on the filter tile means. Without the overlay
     * permission the app cannot draw the filter at all, so the click is
     * redirected to setup regardless of the stored master switch.
     */
    fun onFilterClicked(canDrawOverlays: Boolean, masterOn: Boolean): FilterAction = when {
        !canDrawOverlays -> FilterAction.REQUEST_SETUP
        masterOn -> FilterAction.TURN_OFF
        else -> FilterAction.TURN_ON
    }

    /** Whether the filter tile should render as active (on). */
    fun filterTileActive(masterOn: Boolean): Boolean = masterOn

    /**
     * Label for the mode tile: shows the active profile name so the current
     * mode is visible everywhere (app, notification, bubble, tile).
     */
    fun modeTileLabel(profileName: String?): String =
        profileName?.let { "Mode · $it" } ?: "Mode"

    /**
     * Cycling modes needs at least two profiles to be meaningful; with zero
     * or one there is nothing to switch to.
     */
    fun shouldCycleMode(profileCount: Int): Boolean = profileCount >= 2
}
