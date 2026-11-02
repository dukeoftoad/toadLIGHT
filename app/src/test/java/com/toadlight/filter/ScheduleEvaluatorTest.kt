package com.toadlight.filter

import com.toadlight.filter.core.model.AppSettings
import com.toadlight.filter.core.model.Schedule
import com.toadlight.filter.core.model.ScheduleMode
import com.toadlight.filter.core.schedule.ScheduleEvaluator
import com.toadlight.filter.core.solar.SolarCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ScheduleEvaluatorTest {

    private val zone = ZoneId.of("UTC")

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    /** Fixed sun: sunrise 06:00 UTC, sunset 17:00 UTC every day. */
    private val fakeSun: (LocalDate) -> SolarCalculator.SunEvents = { date ->
        SolarCalculator.SunEvents(
            transitUtcMillis = at(date, 11, 30),
            sunriseUtcMillis = at(date, 6),
            sunsetUtcMillis = at(date, 17),
            alwaysUp = false,
            alwaysDown = false,
        )
    }

    @Test
    fun `always mode is open`() {
        val result = ScheduleEvaluator.evaluate(
            Schedule(ScheduleMode.ALWAYS), AppSettings(), at(LocalDate.of(2026, 1, 1), 12), zone, fakeSun,
        )
        assertTrue(result.open)
        assertNull(result.nextChangeAt)
    }

    @Test
    fun `custom window during the evening is open`() {
        val schedule = Schedule(ScheduleMode.CUSTOM, startMinutes = 20 * 60, endMinutes = 6 * 60)
        val now = at(LocalDate.of(2026, 6, 10), 23, 0)
        val result = ScheduleEvaluator.evaluate(schedule, AppSettings(), now, zone, fakeSun)
        assertTrue(result.open)
        // Next boundary: tomorrow 06:00.
        assertEquals(at(LocalDate.of(2026, 6, 11), 6), result.nextChangeAt)
    }

    @Test
    fun `custom window during the afternoon is closed`() {
        val schedule = Schedule(ScheduleMode.CUSTOM, startMinutes = 20 * 60, endMinutes = 6 * 60)
        val now = at(LocalDate.of(2026, 6, 10), 14, 0)
        val result = ScheduleEvaluator.evaluate(schedule, AppSettings(), now, zone, fakeSun)
        assertFalse(result.open)
        assertEquals(at(LocalDate.of(2026, 6, 10), 20), result.nextChangeAt)
    }

    @Test
    fun `custom window wraps midnight`() {
        val schedule = Schedule(ScheduleMode.CUSTOM, startMinutes = 21 * 60, endMinutes = 6 * 60)
        val now = at(LocalDate.of(2026, 6, 10), 3, 0)
        val result = ScheduleEvaluator.evaluate(schedule, AppSettings(), now, zone, fakeSun)
        assertTrue(result.open)
        assertEquals(at(LocalDate.of(2026, 6, 10), 6), result.nextChangeAt)
    }

    @Test
    fun `sun window at night is open until sunrise`() {
        val now = at(LocalDate.of(2026, 6, 10), 22, 0)
        val result = ScheduleEvaluator.evaluate(
            Schedule(ScheduleMode.SUN),
            AppSettings(latitude = 40.0, longitude = 0.0),
            now, zone, fakeSun,
        )
        assertTrue(result.open)
        assertEquals(at(LocalDate.of(2026, 6, 11), 6), result.nextChangeAt)
        assertEquals(at(LocalDate.of(2026, 6, 11), 6), result.sunriseAt)
    }

    @Test
    fun `sun window in the morning before sunrise is still open`() {
        val now = at(LocalDate.of(2026, 6, 10), 5, 0)
        val result = ScheduleEvaluator.evaluate(
            Schedule(ScheduleMode.SUN),
            AppSettings(latitude = 40.0, longitude = 0.0),
            now, zone, fakeSun,
        )
        assertTrue(result.open)
    }

    @Test
    fun `sun window in the day is closed until sunset`() {
        val now = at(LocalDate.of(2026, 6, 10), 12, 0)
        val result = ScheduleEvaluator.evaluate(
            Schedule(ScheduleMode.SUN),
            AppSettings(latitude = 40.0, longitude = 0.0),
            now, zone, fakeSun,
        )
        assertFalse(result.open)
        assertEquals(at(LocalDate.of(2026, 6, 10), 17), result.nextChangeAt)
        assertEquals(at(LocalDate.of(2026, 6, 10), 17), result.sunsetAt)
    }

    @Test
    fun `sunset offset delays the start`() {
        val settings = AppSettings(latitude = 40.0, longitude = 0.0, sunsetOffsetMinutes = 60)
        // 17:30 -> official sunset was 17:00, offset window starts 18:00.
        val before = ScheduleEvaluator.evaluate(Schedule(ScheduleMode.SUN), settings, at(LocalDate.of(2026, 6, 10), 17, 30), zone, fakeSun)
        assertFalse(before.open)
        val after = ScheduleEvaluator.evaluate(Schedule(ScheduleMode.SUN), settings, at(LocalDate.of(2026, 6, 10), 18, 30), zone, fakeSun)
        assertTrue(after.open)
    }

    @Test
    fun `sunrise offset ends the night earlier`() {
        val settings = AppSettings(latitude = 40.0, longitude = 0.0, sunriseOffsetMinutes = -60)
        // Sunrise 06:00 shifted to 05:00: at 05:30 the window must be closed.
        val result = ScheduleEvaluator.evaluate(Schedule(ScheduleMode.SUN), settings, at(LocalDate.of(2026, 6, 10), 5, 30), zone, fakeSun)
        assertFalse(result.open)
    }

    @Test
    fun `missing location falls back to custom window`() {
        val now = at(LocalDate.of(2026, 6, 10), 22, 0)
        val result = ScheduleEvaluator.evaluate(
            Schedule(ScheduleMode.SUN),
            AppSettings(latitude = null, longitude = null),
            now, zone, fakeSun,
        )
        assertTrue(result.usingFallback)
        assertTrue(result.open)
        assertNotNull(result.nextChangeAt)
    }
}
