package com.toadlight.filter.core

/**
 * Battery policy for the [com.toadlight.filter.filter.FilterService] ticker
 * while the screen is off.
 *
 * When the screen is off the overlay is not composited, so re-ticking the
 * engine every 1 s (or 120 ms during a fade) is pure battery waste. The
 * fade is wall-time derived — the blend is computed from
 * `System.currentTimeMillis()`, not from tick count — so a single
 * recompute right after wake lands the fade exactly where it should be.
 * The ticker therefore drops to one lazy wake every [SCREEN_OFF_TICK_MS]
 * (9 minutes) while the screen is dark, and the SCREEN_ON broadcast
 * triggers an immediate recompute + re-apply.
 *
 * The overlay window is deliberately *not* removed on screen-off: nothing
 * is composited then anyway, and removing/re-adding the window on wake is
 * a known source of one-frame flashes.
 */
object ScreenOffPolicy {

    /** 9 minutes — the ticker's fallback interval while the screen is off. */
    const val SCREEN_OFF_TICK_MS = 540_000L

    /**
     * The delay to actually sleep with. [awakeDelayMs] is the normal
     * screen-on delay (fade tiers + boundary wakeups, already coerced to
     * 120 ms..60 s); when the screen is off it is overridden wholesale.
     */
    fun effectiveTickDelayMs(screenOn: Boolean, awakeDelayMs: Long): Long =
        if (screenOn) awakeDelayMs else SCREEN_OFF_TICK_MS
}
