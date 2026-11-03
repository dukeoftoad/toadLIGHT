package com.toadlight.filter

import com.toadlight.filter.core.TileDecisions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the pure quick-settings tile decision logic (no Android
 * framework involved — that is the point of the extraction).
 */
class TileDecisionsTest {

    // ---- onFilterClicked -------------------------------------------------

    @Test
    fun `click without overlay permission always requests setup`() {
        // Even with the stored switch "on", nothing can be drawn without
        // the permission, so the tile must redirect to setup.
        assertEquals(
            TileDecisions.FilterAction.REQUEST_SETUP,
            TileDecisions.onFilterClicked(canDrawOverlays = false, masterOn = true),
        )
        assertEquals(
            TileDecisions.FilterAction.REQUEST_SETUP,
            TileDecisions.onFilterClicked(canDrawOverlays = false, masterOn = false),
        )
    }

    @Test
    fun `click with permission and master on turns off`() {
        assertEquals(
            TileDecisions.FilterAction.TURN_OFF,
            TileDecisions.onFilterClicked(canDrawOverlays = true, masterOn = true),
        )
    }

    @Test
    fun `click with permission and master off turns on`() {
        assertEquals(
            TileDecisions.FilterAction.TURN_ON,
            TileDecisions.onFilterClicked(canDrawOverlays = true, masterOn = false),
        )
    }

    // ---- filterTileActive ------------------------------------------------

    @Test
    fun `filter tile is active exactly when master is on`() {
        assertTrue(TileDecisions.filterTileActive(masterOn = true))
        assertFalse(TileDecisions.filterTileActive(masterOn = false))
    }

    // ---- modeTileLabel ---------------------------------------------------

    @Test
    fun `mode tile label without active profile falls back to bare Mode`() {
        assertEquals("Mode", TileDecisions.modeTileLabel(null))
    }

    @Test
    fun `mode tile label shows active profile name`() {
        assertEquals("Mode · Evening", TileDecisions.modeTileLabel("Evening"))
    }

    // ---- shouldCycleMode -------------------------------------------------

    @Test
    fun `cycling is impossible with no profiles`() {
        assertFalse(TileDecisions.shouldCycleMode(0))
    }

    @Test
    fun `cycling is pointless with a single profile`() {
        assertFalse(TileDecisions.shouldCycleMode(1))
    }

    @Test
    fun `cycling works once there are two or more profiles`() {
        assertTrue(TileDecisions.shouldCycleMode(2))
        assertTrue(TileDecisions.shouldCycleMode(5))
    }
}
