package com.toadlight.filter.core.schedule

import com.toadlight.filter.core.model.AppSettings
import com.toadlight.filter.core.model.Schedule
import com.toadlight.filter.core.model.ScheduleMode
import com.toadlight.filter.core.solar.SolarCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Result of evaluating whether the filter window is currently open.
 *
 * `nextChangeAt` is the next epoch-millisecond at which `open` may flip,
 * so the engine can sleep until then instead of polling.
 */
data class WindowEval(
    val open: Boolean,
    val nextChangeAt: Long?,
    val sunsetAt: Long?,
    val sunriseAt: Long?,
    val usingFallback: Boolean = false,
)

/**
 * Pure, unit-testable schedule logic shared by the engine and the UI previews.
 */
object ScheduleEvaluator {

    private const val FALLBACK_START_MIN = 21 * 60
    private const val FALLBACK_END_MIN = 6 * 60

    /**
     * @param sunProvider returns sun events for a UTC date. Callers decide the
     * horizon (official vs civil); offsets are applied here.
     */
    fun evaluate(
        schedule: Schedule,
        settings: AppSettings,
        nowMillis: Long,
        zone: ZoneId,
        sunProvider: (LocalDate) -> SolarCalculator.SunEvents,
    ): WindowEval = when (schedule.mode) {
        ScheduleMode.ALWAYS -> WindowEval(
            open = true,
            nextChangeAt = null,
            sunsetAt = null,
            sunriseAt = null,
        )

        ScheduleMode.CUSTOM -> customWindow(schedule.startMinutes, schedule.endMinutes, nowMillis, zone)

        ScheduleMode.SUN -> {
            val lat = settings.latitude
            val lon = settings.longitude
            if (lat == null || lon == null) {
                customWindow(FALLBACK_START_MIN, FALLBACK_END_MIN, nowMillis, zone)
                    .copy(usingFallback = true)
            } else {
                sunWindow(settings, nowMillis, zone, sunProvider)
            }
        }
    }

    private fun customWindow(
        startMinutes: Int,
        endMinutes: Int,
        nowMillis: Long,
        zone: ZoneId,
    ): WindowEval {
        val start = startMinutes.coerceIn(0, 24 * 60)
        val end = endMinutes.coerceIn(0, 24 * 60)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

        val startToday = minutesOnDate(today, start, zone)
        val endToday = minutesOnDate(today, end, zone)

        val boundaries = mutableListOf(
            startToday,
            endToday,
            minutesOnDate(today.plusDays(1), start, zone),
            minutesOnDate(today.plusDays(1), end, zone),
            minutesOnDate(today.minusDays(1), start, zone),
            minutesOnDate(today.minusDays(1), end, zone),
        )

        val open = if (start == end) {
            false // degenerate zero-length window; UI prevents this
        } else if (start < end) {
            nowMillis >= startToday && nowMillis < endToday
        } else {
            // Window wraps past midnight (e.g. 21:00 -> 06:00).
            nowMillis >= startToday || nowMillis < endToday
        }

        return WindowEval(
            open = open,
            nextChangeAt = boundaries.filter { it > nowMillis }.minOrNull(),
            sunsetAt = null,
            sunriseAt = null,
        )
    }

    private fun sunWindow(
        settings: AppSettings,
        nowMillis: Long,
        zone: ZoneId,
        sunProvider: (LocalDate) -> SolarCalculator.SunEvents,
    ): WindowEval {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val todayEvents = sunProvider(today)
        val tomorrowEvents = sunProvider(today.plusDays(1))

        val sunsetOffsetMs = settings.sunsetOffsetMinutes * 60_000L
        val sunriseOffsetMs = settings.sunriseOffsetMinutes * 60_000L

        val sunsetToday = todayEvents.sunsetUtcMillis?.plus(sunsetOffsetMs)
        val sunriseToday = todayEvents.sunriseUtcMillis?.plus(sunriseOffsetMs)
        val sunsetTomorrow = tomorrowEvents.sunsetUtcMillis?.plus(sunsetOffsetMs)
        val sunriseTomorrow = tomorrowEvents.sunriseUtcMillis?.plus(sunriseOffsetMs)

        // Polar edge cases for today.
        if (sunsetToday == null || sunriseToday == null) {
            val open = when {
                todayEvents.alwaysUp -> false // midnight sun: no night today
                todayEvents.alwaysDown -> true // polar night: filter all day
                sunsetToday == null && sunriseToday != null ->
                    // No sunset today but a sunrise exists: treat early morning as night.
                    nowMillis < sunriseToday

                sunsetToday != null && sunriseToday == null ->
                    // No sunrise today but a sunset exists: night starts at sunset.
                    nowMillis >= sunsetToday

                else -> true
            }
            return WindowEval(
                open = open,
                nextChangeAt = nowMillis + 60 * 60_000L, // re-check hourly in polar conditions
                sunsetAt = sunsetToday,
                sunriseAt = sunriseToday,
            )
        }

        // Night runs from today's sunset to tomorrow's sunrise; "this morning's"
        // sunrise terminates the window that started yesterday evening.
        val open = nowMillis >= sunsetToday || nowMillis < sunriseToday

        val next = listOfNotNull(sunriseToday, sunsetToday, sunriseTomorrow, sunsetTomorrow)
            .filter { it > nowMillis }
            .minOrNull()

        // For display: next upcoming sunrise / sunset.
        val displaySunrise = (if (nowMillis < sunriseToday) sunriseToday else sunriseTomorrow) ?: sunriseToday
        val displaySunset = (if (nowMillis < sunsetToday) sunsetToday else sunsetTomorrow) ?: sunsetToday

        return WindowEval(
            open = open,
            nextChangeAt = next,
            sunsetAt = displaySunset,
            sunriseAt = displaySunrise,
        )
    }

    private fun minutesOnDate(date: LocalDate, minutes: Int, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli() + minutes * 60_000L
}
