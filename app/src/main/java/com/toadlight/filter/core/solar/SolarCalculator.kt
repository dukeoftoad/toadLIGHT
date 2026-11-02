package com.toadlight.filter.core.solar

import java.time.LocalDate
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Sunrise / sunset calculation based on the USNO "sunrise equation"
 * (the same family of formulas used by NOAA's solar calculator and Wikipedia's
 * sunrise equation article). Pure math, no dependencies, works offline.
 *
 * All returned timestamps are UTC epoch milliseconds; callers convert them to
 * the device time zone.
 */
object SolarCalculator {

    /** Standard sunrise/sunset horizon including refraction and solar radius. */
    const val HORIZON_OFFICIAL_DEG = -0.833

    /** Civil dawn/dusk horizon (sun 6 degrees below the horizon). */
    const val HORIZON_CIVIL_DEG = -6.0

    private const val JULIAN_1970_01_01 = 2440587.5
    private const val JULIAN_J2000 = 2451545.0
    private const val OBLIQUITY_DEG = 23.4397

    data class SunEvents(
        /** Solar noon (transit), UTC epoch millis. */
        val transitUtcMillis: Long,
        /** Null in polar day / polar night. */
        val sunriseUtcMillis: Long?,
        /** Null in polar day / polar night. */
        val sunsetUtcMillis: Long?,
        /** True when the sun never sets on that date (midnight sun). */
        val alwaysUp: Boolean,
        /** True when the sun never rises on that date (polar night). */
        val alwaysDown: Boolean,
    )

    /**
     * Computes sun events for the given UTC date, latitude and longitude.
     *
     * @param horizonDeg altitude of the sun center at the event; use
     * [HORIZON_OFFICIAL_DEG] for official sunrise/sunset or
     * [HORIZON_CIVIL_DEG] for civil dawn/dusk.
     */
    fun eventsFor(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        horizonDeg: Double = HORIZON_OFFICIAL_DEG,
    ): SunEvents {
        // Anchor on noon UTC of the requested date so events land on that date
        // for any reasonable longitude/timezone combination.
        val anchorJulian = date.toEpochDay() + 0.5 + JULIAN_1970_01_01

        // Mean solar time in days since J2000.
        val meanSolarTime = anchorJulian - JULIAN_J2000 - longitude / 360.0

        // Solar mean anomaly.
        var m = (357.5291 + 0.98560028 * meanSolarTime) % 360.0
        if (m < 0) m += 360.0

        // Equation of the center.
        val c = 1.9148 * sinDeg(m) + 0.02 * sinDeg(2 * m) + 0.0003 * sinDeg(3 * m)

        // Ecliptic longitude.
        var lambda = (m + c + 180.0 + 102.9372) % 360.0
        if (lambda < 0) lambda += 360.0

        // Solar transit (true solar noon).
        val transitJulian =
            JULIAN_J2000 + meanSolarTime + 0.0053 * sinDeg(m) - 0.0069 * sinDeg(2 * lambda)

        // Declination of the sun.
        val sinDeclination = sinDeg(lambda) * sinDeg(OBLIQUITY_DEG)
        val declination = asin(sinDeclination)

        val latRad = Math.toRadians(latitude)
        val horizonRad = Math.toRadians(horizonDeg)
        val cosOmega =
            (sin(horizonRad) - sin(latRad) * sinDeclination) / (cos(latRad) * cos(declination))

        val transitMillis = julianToMillis(transitJulian)

        return when {
            cosOmega > 1.0 -> SunEvents(
                transitMillis, null, null,
                alwaysUp = false, alwaysDown = true,
            )

            cosOmega < -1.0 -> SunEvents(
                transitMillis, null, null,
                alwaysUp = true, alwaysDown = false,
            )

            else -> {
                val halfDayDegrees = Math.toDegrees(acos(cosOmega))
                val halfDay = halfDayDegrees / 360.0
                SunEvents(
                    transitMillis,
                    julianToMillis(transitJulian - halfDay),
                    julianToMillis(transitJulian + halfDay),
                    alwaysUp = false,
                    alwaysDown = false,
                )
            }
        }
    }

    private fun julianToMillis(jd: Double): Long = ((jd - JULIAN_1970_01_01) * 86400000.0).roundToLong()

    private fun sinDeg(deg: Double): Double = sin(Math.toRadians(deg))
}
