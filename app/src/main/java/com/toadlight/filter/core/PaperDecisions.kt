package com.toadlight.filter.core

import com.toadlight.filter.core.model.AppOverride
import com.toadlight.filter.core.model.EngineParams
import com.toadlight.filter.core.model.OverrideMode
import com.toadlight.filter.core.model.Profile

/**
 * Pure decisions for Paper Mode — the reading finish (warm tone + paper
 * grain) that activates per app, typically for PDF readers and e-book apps.
 *
 * Extracted from [com.toadlight.filter.filter.FilterEngine] so the gating
 * rules are unit-testable without Android. Everything here is a function of
 * its arguments only.
 */
object PaperDecisions {

    /** True while the foreground app has a Paper Mode override. */
    fun isPaperApp(override: AppOverride?): Boolean =
        override?.mode == OverrideMode.PAPER

    /** True while the foreground app asked for no filter at all. */
    fun isSuppressing(override: AppOverride?): Boolean =
        override?.mode == OverrideMode.NO_FILTER

    /**
     * Whether the overlay should be on right now.
     *
     * Paper Mode is a *reading* mode: when the foreground app is a paper app,
     * the filter turns on for it even if the day schedule is closed — the
     * user opened a book, the paper finish should be there. Explicit global
     * intent still wins: the master switch and a pause silence everything,
     * and a NO_FILTER app always suppresses (a paper app and a suppressing
     * app cannot be the same app — one override per package — but the pure
     * function keeps the priority explicit: suppress > paper).
     */
    fun targetOn(
        masterEnabled: Boolean,
        paused: Boolean,
        suppress: Boolean,
        scheduleOpen: Boolean,
        previewing: Boolean,
        paperApp: Boolean,
    ): Boolean = masterEnabled && !paused && !suppress && (scheduleOpen || previewing || paperApp)

    /**
     * Filter parameters for the current moment. Precedence:
     *
     * 1. CUSTOM override — but only while the schedule is open (or
     *    previewing) and not paused, matching the pre-Paper behavior.
     * 2. PAPER override — the app's reading tone, whenever it applies
     *    (the target logic already turned the overlay on for it).
     * 3. The active profile.
     */
    fun params(
        override: AppOverride?,
        profile: Profile,
        scheduleOpen: Boolean,
        paused: Boolean,
    ): EngineParams = when {
        override?.mode == OverrideMode.CUSTOM && scheduleOpen && !paused ->
            EngineParams(override.colorTempK, override.intensityPercent, override.dimPercent)

        override?.mode == OverrideMode.PAPER ->
            EngineParams(
                override.paperColorTempK,
                override.paperIntensityPercent,
                override.paperDimPercent,
            )

        else -> EngineParams(profile.colorTempK, profile.intensityPercent, profile.dimPercent)
    }

    /**
     * Grain strength for the paper layer: the paper app's grain while a
     * Paper Mode override is active, otherwise the active profile's paper
     * finish (if any). Returns 0 when the paper layer should be off.
     */
    fun paperIntensity(
        override: AppOverride?,
        profile: Profile,
    ): Int = when {
        override?.mode == OverrideMode.PAPER -> override.paperGrainPercent.coerceIn(0, 100)
        profile.paperFinish -> profile.paperIntensityPercent.coerceIn(0, 100)
        else -> 0
    }
}
