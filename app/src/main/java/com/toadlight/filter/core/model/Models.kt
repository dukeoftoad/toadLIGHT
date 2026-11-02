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
    val schedule: Schedule = Schedule(),
    val builtIn: Boolean = false,
) {
    val summary: String
        get() = "${colorTempK}K · ${intensityPercent}% warmth · ${dimPercent}% dim"
}

@Serializable
enum class OverrideMode { DEFAULT, NO_FILTER, CUSTOM }

@Serializable
data class AppOverride(
    val packageName: String,
    val mode: OverrideMode = OverrideMode.DEFAULT,
    val colorTempK: Int = 2000,
    val intensityPercent: Int = 60,
    val dimPercent: Int = 0,
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

    /** Last remembered bubble position (top-left, raw px). [Int.MIN_VALUE] = unset. */
    val bubblePosX: Int = Int.MIN_VALUE,
    val bubblePosY: Int = Int.MIN_VALUE,
    val perAppEnabled: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val appOverrides: Map<String, AppOverride> = emptyMap(),
)
