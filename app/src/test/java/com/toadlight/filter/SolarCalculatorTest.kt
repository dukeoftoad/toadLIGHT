package com.toadlight.filter

import com.toadlight.filter.core.solar.SolarCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * Fixtures verified against timeanddate.com (fetched 2026-11-02):
 *  - New York, 2026-11-15: sunrise 6:42 am, sunset 4:37 pm EST
 *  - Delhi,    2026-11-15: sunrise 6:43 am, sunset 5:27 pm IST
 */
class SolarCalculatorTest {

    private fun assertCloseTo(actual: ZonedDateTime, hour: Int, minute: Int, toleranceMinutes: Int = 4) {
        val target = actual.toLocalDate().atTime(hour, minute).atZone(actual.zone)
        val deltaMinutes = abs(actual.toEpochSecond() - target.toEpochSecond()) / 60.0
        assertTrue(
            "expected ~$hour:${"%02d".format(minute)} but was ${actual.hour}:${"%02d".format(actual.minute)} " +
                "(off by $deltaMinutes min)",
            deltaMinutes <= toleranceMinutes,
        )
    }

    @Test
    fun `new york sunrise and sunset on 2026-11-15`() {
        val events = SolarCalculator.eventsFor(LocalDate.of(2026, 11, 15), 40.7128, -74.0060)
        val zone = ZoneId.of("America/New_York")

        assertNotNull(events.sunriseUtcMillis)
        assertNotNull(events.sunsetUtcMillis)
        assertFalse(events.alwaysUp)
        assertFalse(events.alwaysDown)

        val sunrise = Instant.ofEpochMilli(events.sunriseUtcMillis!!).atZone(zone)
        val sunset = Instant.ofEpochMilli(events.sunsetUtcMillis!!).atZone(zone)
        assertCloseTo(sunrise, 6, 42)
        assertCloseTo(sunset, 16, 37)
    }

    @Test
    fun `delhi sunrise and sunset on 2026-11-15`() {
        val events = SolarCalculator.eventsFor(LocalDate.of(2026, 11, 15), 28.6139, 77.2090)
        val zone = ZoneId.of("Asia/Kolkata")

        val sunrise = Instant.ofEpochMilli(events.sunriseUtcMillis!!).atZone(zone)
        val sunset = Instant.ofEpochMilli(events.sunsetUtcMillis!!).atZone(zone)
        assertCloseTo(sunrise, 6, 43)
        assertCloseTo(sunset, 17, 27)
    }

    @Test
    fun `sunrise always happens before sunset on the same utc day`() {
        val events = SolarCalculator.eventsFor(LocalDate.of(2026, 6, 15), 51.5074, -0.1278)
        assertTrue(events.sunriseUtcMillis!! < events.sunsetUtcMillis!!)
        assertTrue(events.sunriseUtcMillis!! < events.transitUtcMillis)
        assertTrue(events.transitUtcMillis < events.sunsetUtcMillis!!)
    }

    @Test
    fun `civil dusk is later than official sunset`() {
        val date = LocalDate.of(2026, 11, 15)
        val official = SolarCalculator.eventsFor(date, 40.7128, -74.0060, SolarCalculator.HORIZON_OFFICIAL_DEG)
        val civil = SolarCalculator.eventsFor(date, 40.7128, -74.0060, SolarCalculator.HORIZON_CIVIL_DEG)

        val deltaMinutes = (civil.sunsetUtcMillis!! - official.sunsetUtcMillis!!) / 60_000.0
        assertTrue("civil dusk should be later, delta=$deltaMinutes", deltaMinutes in 15.0..45.0)

        val dawnDelta = (official.sunriseUtcMillis!! - civil.sunriseUtcMillis!!) / 60_000.0
        assertTrue("civil dawn should be earlier, delta=$dawnDelta", dawnDelta in 15.0..45.0)
    }

    @Test
    fun `polar day in tromso on summer solstice`() {
        val events = SolarCalculator.eventsFor(LocalDate.of(2026, 6, 21), 69.6496, 18.9553)
        assertEquals(null, events.sunriseUtcMillis)
        assertEquals(null, events.sunsetUtcMillis)
        assertTrue(events.alwaysUp)
        assertFalse(events.alwaysDown)
    }

    @Test
    fun `polar night in tromso on winter solstice`() {
        val events = SolarCalculator.eventsFor(LocalDate.of(2026, 12, 21), 69.6496, 18.9553)
        assertEquals(null, events.sunriseUtcMillis)
        assertEquals(null, events.sunsetUtcMillis)
        assertFalse(events.alwaysUp)
        assertTrue(events.alwaysDown)
    }

    @Test
    fun `equator day is roughly twelve hours`() {
        val events = SolarCalculator.eventsFor(LocalDate.of(2026, 3, 20), 0.35, 37.58) // Mt Kenya area
        val dayLengthMinutes = (events.sunsetUtcMillis!! - events.sunriseUtcMillis!!) / 60_000.0
        assertTrue("day length was $dayLengthMinutes min", dayLengthMinutes in (12 * 60 - 15.0)..(12 * 60 + 15.0))
    }
}
