package com.toadlight.filter

import com.toadlight.filter.core.color.ColorTemperature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorTemperatureTest {

    @Test
    fun `6500k is near white`() {
        val rgb = ColorTemperature.rgb(6500)
        assertEquals(255, rgb[0])
        assertTrue("g=${rgb[1]}", rgb[1] in 248..255)
        assertTrue("b=${rgb[2]}", rgb[2] in 240..255)
    }

    @Test
    fun `1000k is deep orange with no blue`() {
        val rgb = ColorTemperature.rgb(1000)
        assertEquals(255, rgb[0])
        assertEquals(0, rgb[2])
        assertTrue("g=${rgb[1]}", rgb[1] in 40..90)
    }

    @Test
    fun `blue decreases as temperature drops`() {
        val cold = ColorTemperature.rgb(4000)[2]
        val warm = ColorTemperature.rgb(2000)[2]
        assertTrue("cold=$cold warm=$warm", warm < cold)
    }

    @Test
    fun `red stays saturated across the slider range`() {
        for (kelvin in listOf(1000, 2000, 3200, 4500, 5500)) {
            assertEquals(255, ColorTemperature.rgb(kelvin)[0])
        }
    }

    @Test
    fun `values are always within byte range`() {
        for (kelvin in 1000..6500 step 137) {
            ColorTemperature.rgb(kelvin).forEach { channel ->
                assertTrue("$kelvin -> $channel", channel in 0..255)
            }
        }
    }

    @Test
    fun `out of range values are clamped`() {
        assertEquals(ColorTemperature.rgb(1000).contentToString(), ColorTemperature.rgb(500).contentToString())
        assertEquals(ColorTemperature.rgb(6500).contentToString(), ColorTemperature.rgb(9000).contentToString())
    }

    @Test
    fun `describe returns a friendly label`() {
        assertEquals("Candlelight", ColorTemperature.describe(1200))
        assertEquals("Relaxed", ColorTemperature.describe(2797))
        assertEquals("Daylight", ColorTemperature.describe(6200))
    }
}
