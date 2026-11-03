package com.toadlight.filter

import com.toadlight.filter.core.ScreenOffPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM tests for the screen-off battery policy. The constants mirror the
 * behaviour shipped in v1.4.5 (recovered from the v1.4.6 release APK after
 * the sandbox rollback) — do not change them casually: the 9-minute tick is
 * the entire point, and the override happens AFTER the awake delay has been
 * coerced, so it always wins.
 */
class ScreenOffPolicyTest {

    @Test
    fun `screen off always uses the 9 minute tick`() {
        // Whatever the awake policy computed (fast fade tick, 1 s rhythm,
        // 60 s idle, boundary wakeup), a dark screen overrides it.
        for (awake in listOf(120L, 1_000L, 60_000L, 253L)) {
            assertEquals(
                "awakeDelay=$awake",
                ScreenOffPolicy.SCREEN_OFF_TICK_MS,
                ScreenOffPolicy.effectiveTickDelayMs(screenOn = false, awakeDelayMs = awake),
            )
        }
    }

    @Test
    fun `screen on passes the awake delay through untouched`() {
        for (awake in listOf(120L, 1_000L, 60_000L, 253L, 540_000L)) {
            assertEquals(
                "awakeDelay=$awake",
                awake,
                ScreenOffPolicy.effectiveTickDelayMs(screenOn = true, awakeDelayMs = awake),
            )
        }
    }

    @Test
    fun `screen-off tick is exactly 9 minutes`() {
        assertEquals(540_000L, ScreenOffPolicy.SCREEN_OFF_TICK_MS)
        assertEquals(9L * 60L * 1000L, ScreenOffPolicy.SCREEN_OFF_TICK_MS)
    }

    @Test
    fun `screen-off tick is longer than the largest awake delay`() {
        // The override must never be reachable by the awake policy itself
        // (which coerces to at most 60 s), otherwise it would be a no-op.
        org.junit.Assert.assertTrue(ScreenOffPolicy.SCREEN_OFF_TICK_MS > 60_000L)
    }
}
