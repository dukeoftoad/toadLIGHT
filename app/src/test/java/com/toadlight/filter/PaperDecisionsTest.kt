package com.toadlight.filter

import com.toadlight.filter.core.PaperDecisions
import com.toadlight.filter.core.PaperPresets
import com.toadlight.filter.core.model.AppOverride
import com.toadlight.filter.core.model.OverrideMode
import com.toadlight.filter.core.model.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decision-table tests for Paper Mode: the pure gating rules
 * ([PaperDecisions]) and the one-tap preset filtering ([PaperPresets]).
 */
class PaperDecisionsTest {

    private val profile = Profile(id = "p", name = "Evening")
    private val paperApp = AppOverride(
        packageName = "com.adobe.reader",
        mode = OverrideMode.PAPER,
        paperColorTempK = 3000,
        paperIntensityPercent = 50,
        paperDimPercent = 10,
        paperGrainPercent = 40,
    )

    // ---- targetOn ----------------------------------------------------------

    @Test
    fun `paper app forces the overlay on even with the schedule closed`() {
        assertTrue(
            PaperDecisions.targetOn(
                masterEnabled = true, paused = false, suppress = false,
                scheduleOpen = false, previewing = false, paperApp = true,
            )
        )
    }

    @Test
    fun `without a paper app the schedule alone decides`() {
        assertFalse(
            PaperDecisions.targetOn(
                masterEnabled = true, paused = false, suppress = false,
                scheduleOpen = false, previewing = false, paperApp = false,
            )
        )
        assertTrue(
            PaperDecisions.targetOn(
                masterEnabled = true, paused = false, suppress = false,
                scheduleOpen = true, previewing = false, paperApp = false,
            )
        )
    }

    @Test
    fun `master off silences everything including paper apps`() {
        assertFalse(
            PaperDecisions.targetOn(
                masterEnabled = false, paused = false, suppress = false,
                scheduleOpen = true, previewing = false, paperApp = true,
            )
        )
    }

    @Test
    fun `pause silences paper apps too`() {
        assertFalse(
            PaperDecisions.targetOn(
                masterEnabled = true, paused = true, suppress = false,
                scheduleOpen = true, previewing = false, paperApp = true,
            )
        )
    }

    @Test
    fun `preview keeps the overlay on for a paper app and a normal app`() {
        assertTrue(
            PaperDecisions.targetOn(
                masterEnabled = true, paused = false, suppress = false,
                scheduleOpen = false, previewing = true, paperApp = false,
            )
        )
        assertTrue(
            PaperDecisions.targetOn(
                masterEnabled = true, paused = false, suppress = false,
                scheduleOpen = false, previewing = true, paperApp = true,
            )
        )
    }

    @Test
    fun `suppression always wins`() {
        assertFalse(
            PaperDecisions.targetOn(
                masterEnabled = true, paused = false, suppress = true,
                scheduleOpen = true, previewing = false, paperApp = false,
            )
        )
    }

    // ---- params ------------------------------------------------------------

    @Test
    fun `paper params come from the override's paper fields`() {
        val p = PaperDecisions.params(paperApp, profile, scheduleOpen = false, paused = false)
        assertEquals(3000, p.colorTempK)
        assertEquals(50, p.intensityPercent)
        assertEquals(10, p.dimPercent)
    }

    @Test
    fun `plain apps fall back to the profile`() {
        val p = PaperDecisions.params(null, profile, scheduleOpen = true, paused = false)
        assertEquals(profile.colorTempK, p.colorTempK)
        assertEquals(profile.intensityPercent, p.intensityPercent)
        assertEquals(profile.dimPercent, p.dimPercent)
    }

    @Test
    fun `custom override applies only while the schedule is open`() {
        val custom = AppOverride(
            packageName = "x",
            mode = OverrideMode.CUSTOM,
            colorTempK = 1800,
            intensityPercent = 70,
            dimPercent = 5,
        )
        val open = PaperDecisions.params(custom, profile, scheduleOpen = true, paused = false)
        assertEquals(1800, open.colorTempK)

        // schedule closed → profile params (pre-Paper behavior preserved)
        val closed = PaperDecisions.params(custom, profile, scheduleOpen = false, paused = false)
        assertEquals(profile.colorTempK, closed.colorTempK)
    }

    @Test
    fun `paper params ignore the schedule state`() {
        val closed = PaperDecisions.params(paperApp, profile, scheduleOpen = false, paused = false)
        val open = PaperDecisions.params(paperApp, profile, scheduleOpen = true, paused = false)
        assertEquals(3000, closed.colorTempK)
        assertEquals(3000, open.colorTempK)
    }

    // ---- paperIntensity ----------------------------------------------------

    @Test
    fun `grain strength comes from the paper override while it is active`() {
        assertEquals(40, PaperDecisions.paperIntensity(paperApp, profile))
    }

    @Test
    fun `profile paper finish applies when no override is active`() {
        val paperProfile = profile.copy(paperFinish = true, paperIntensityPercent = 60)
        assertEquals(60, PaperDecisions.paperIntensity(null, paperProfile))
        // a paper app wins over the profile's finish
        assertEquals(40, PaperDecisions.paperIntensity(paperApp, paperProfile))
    }

    @Test
    fun `no paper anywhere means the grain layer is off`() {
        assertEquals(0, PaperDecisions.paperIntensity(null, profile))
        assertEquals(0, PaperDecisions.paperIntensity(paperApp.copy(mode = OverrideMode.CUSTOM), profile))
    }

    // ---- PaperPresets --------------------------------------------------------

    @Test
    fun `preset only targets installed apps without existing rules`() {
        val installed = setOf(
            "com.adobe.reader",
            "com.amazon.kindle",
            "com.tencent.weread",
            "com.random.game",
        )
        val existing = mapOf(
            // user already gave Kindle a custom rule — must not be clobbered
            "com.amazon.kindle" to AppOverride(
                packageName = "com.amazon.kindle",
                mode = OverrideMode.CUSTOM,
            ),
        )

        val targets = PaperPresets.applicable(installed, existing)
        assertEquals(listOf("com.adobe.reader", "com.tencent.weread"), targets)
    }

    @Test
    fun `preset ignores uninstalled candidates entirely`() {
        val targets = PaperPresets.applicable(emptySet(), emptyMap())
        assertTrue(targets.isEmpty())
    }

    @Test
    fun `paperPackages lists exactly the apps on paper mode`() {
        val existing = mapOf(
            "com.adobe.reader" to paperApp,
            "com.other" to AppOverride(packageName = "com.other", mode = OverrideMode.CUSTOM),
        )
        assertEquals(listOf("com.adobe.reader"), PaperPresets.paperPackages(existing))
    }
}
