package com.toadlight.filter.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ScheduleMode { SUN, CUSTOM, ALWAYS }

@Serializable
data class Schedule(
    val mode: ScheduleMode = ScheduleMode.SUN,
    val startMinutes: Int = 21 * 60,
    val endMinutes: Int = 6 * 60,
)

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val colorTempK: Int = 3200,
    val intensityPercent: Int = 55,
    val dimPercent: Int = 15,
    /** Paper-like finish: cream tone + fibrous grain over the warm tint. */
    val paperFinish: Boolean = false,
    /** How visible the paper grain is, 0..100. */
    val paperIntensityPercent: Int = 35,
    val schedule: Schedule = Schedule(),
    val builtIn: Boolean = false,
) {
    val summary: String
        get() = "${colorTempK}K · ${intensityPercent}% warmth · ${dimPercent}% dim" +
            if (paperFinish) " · paper" else ""
}

@Serializable
enum class OverrideMode { DEFAULT, NO_FILTER, CUSTOM, PAPER }

@Serializable
data class AppOverride(
    val packageName: String,
    val mode: OverrideMode = OverrideMode.DEFAULT,
    val colorTempK: Int = 2000,
    val intensityPercent: Int = 60,
    val dimPercent: Int = 0,
    /**
     * Paper Mode parameters (used when [mode] == [OverrideMode.PAPER]): a
     * warm reading tone with the fibrous paper grain, scoped to this app —
     * typically PDF readers and e-book apps. Defaults match a comfortable
     * long-reading setup; the user tunes them per app.
     */
    val paperColorTempK: Int = 3200,
    val paperIntensityPercent: Int = 45,
    val paperDimPercent: Int = 8,
    val paperGrainPercent: Int = 35,
)

/** Filter parameters resolved for the current moment (profile or per-app override). */
data class EngineParams(
    val colorTempK: Int,
    val intensityPercent: Int,
    val dimPercent: Int,
)

@Serializable
enum class ThemeMode { FOLLOW_SYSTEM, DARK, AMOLED, LIGHT }

@Serializable
data class AppSettings(
    val masterEnabled: Boolean = true,
    val pausedUntil: Long = 0L,

    /**
     * Epoch millis until which the filter is forced on, bypassing the
     * schedule ("On now" / live slider preview). After it expires the
     * regular schedule takes over again.
     */
    val previewUntil: Long = 0L,
    val onboardingDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val dynamicColors: Boolean = false,
    val startOnBoot: Boolean = true,
    val transitionMinutes: Int = 10,
    val sunsetOffsetMinutes: Int = 0,
    val sunriseOffsetMinutes: Int = 0,
    val civilTwilight: Boolean = false,
    val filterNavbar: Boolean = true,
    val filterLandscape: Boolean = false,

    /** Draggable floating bubble menu over the screen while the filter runs. */
    val floatingMenuEnabled: Boolean = true,

    /** Render the overlay on the GPU (hardware layer). Turn off for OEM glitches. */
    val useGpuRendering: Boolean = true,

    /** Fade duration for manual on/off, pause, preview and mode switches, ms (0 = instant). */
    val quickTransitionMs: Int = 600,

    /** Keep warming the screen while the lock screen / keyguard is showing. */
    val filterLockScreen: Boolean = true,

    /**
     * Global coverage: render the filter through the accessibility service's
     * overlay window so it tints everything — status bar, navigation area,
     * and even the expanded notification shade. Requires the toadLIGHT
     * accessibility service to be enabled in system settings.
     */
    val filterNotificationShade: Boolean = false,

    /** Last remembered bubble position (top-left, raw px). [Int.MIN_VALUE] = unset. */
    val bubblePosX: Int = Int.MIN_VALUE,
    val bubblePosY: Int = Int.MIN_VALUE,
    val perAppEnabled: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val appOverrides: Map<String, AppOverride> = emptyMap(),
)
