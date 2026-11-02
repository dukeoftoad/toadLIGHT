package com.toadlight.filter.core.color

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Maps a color temperature in Kelvin to an approximate RGB color of the light.
 *
 * Uses the classic black-body approximation popularised by Tanner Helland's
 * "Temperature to RGB conversion" article, which is the de-facto standard for
 * blue-light-filter apps. Values below ~6600K are warm (more red, less blue),
 * which is exactly the range a night filter needs.
 */
object ColorTemperature {

    const val MIN_K = 1000
    const val MAX_K = 6500

    /** Returns [r, g, b] (0..255) for a color temperature in Kelvin. */
    fun rgb(kelvin: Int): IntArray {
        val t = kelvin.coerceIn(MIN_K, MAX_K) / 100.0
        var r: Double
        var g: Double
        var b: Double

        if (t <= 66.0) {
            r = 255.0
            g = 99.4708025861 * ln(t) - 161.1195681661
        } else {
            r = 329.698727446 * (t - 60.0).pow(-0.1332047592)
            g = 288.1221695283 * (t - 60.0).pow(-0.0755148492)
        }

        b = when {
            t >= 66.0 -> 255.0
            t <= 19.0 -> 0.0
            else -> 138.5177312231 * ln(t - 10.0) - 305.0447927307
        }

        return intArrayOf(clampChannel(r), clampChannel(g), clampChannel(b))
    }

    /** Opaque ARGB color of the given temperature (alpha 255). */
    fun argb(kelvin: Int): Int {
        val c = rgb(kelvin)
        return (0xFF shl 24) or (c[0] shl 16) or (c[1] shl 8) or c[2]
    }

    /** Human-friendly label for the slider. */
    fun describe(kelvin: Int): String = when {
        kelvin < 1500 -> "Candlelight"
        kelvin < 2100 -> "Ember"
        kelvin < 2700 -> "Cozy"
        kelvin < 3300 -> "Relaxed"
        kelvin < 4100 -> "Warm"
        kelvin < 5000 -> "Balanced"
        else -> "Daylight"
    }

    private fun clampChannel(v: Double): Int = v.coerceIn(0.0, 255.0).roundToInt()
}
