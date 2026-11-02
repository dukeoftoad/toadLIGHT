package com.toadlight.filter.filter

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Random

/**
 * Deterministic paper-grain texture shared by the real overlay and the
 * in-app previews.
 *
 * A 256×256 tile of soft speckle noise: mostly faint white fibers with a
 * sprinkle of darker flecks, smoothed once so single pixels don't sparkle.
 * The same seed always produces the same tile, so the texture is stable
 * across restarts and identical between the preview and the real overlay.
 */
object PaperGrain {

    /** Warm cream base tone the paper finish lays under the grain. */
    const val PAPER_TONE = 0xFFF4E8D0.toInt()

    fun tileBitmap(size: Int = 256, seed: Long = 20260311L): Bitmap {
        val rnd = Random(seed)
        val n = size * size
        val pixels = IntArray(n)
        for (i in 0 until n) {
            val v = rnd.nextInt(256)
            val white = rnd.nextFloat() < 0.62f
            pixels[i] = if (white) {
                // Faint white fiber.
                ((v * 0.14f).toInt() shl 24) or 0x00FFFFFF
            } else {
                // Darker fleck (fully transparent color channels).
                ((v * 0.10f).toInt() shl 24)
            }
        }

        val softened = smooth(pixels, size, size)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        out.setPixels(softened, 0, size, 0, 0, size, size)
        return out
    }

    fun tileImageBitmap(size: Int = 96, seed: Long = 20260311L): ImageBitmap =
        tileBitmap(size, seed).asImageBitmap()

    /** One 3×3 box-blur pass to keep the grain fibrous instead of sparkling. */
    private fun smooth(src: IntArray, w: Int, h: Int): IntArray {
        val dst = IntArray(src.size)
        for (y in 0 until h) {
            val yUp = ((y - 1).coerceAtLeast(0)) * w
            val yMid = y * w
            val yDown = ((y + 1).coerceAtMost(h - 1)) * w
            for (x in 0 until w) {
                val xl = (x - 1).coerceAtLeast(0)
                val xr = (x + 1).coerceAtMost(w - 1)
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                for (row in intArrayOf(yUp, yMid, yDown)) {
                    for (col in intArrayOf(xl, x, xr)) {
                        val p = src[row + col]
                        a += p ushr 24
                        r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF
                        b += p and 0xFF
                    }
                }
                val i = yMid + x
                dst[i] = ((a / 9) shl 24) or (((r / 9) and 0xFF) shl 16) or
                    (((g / 9) and 0xFF) shl 8) or ((b / 9) and 0xFF)
            }
        }
        return dst
    }
}
