package com.toadlight.filter.filter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * The actual overlay: a full-screen, non-touchable view that paints two
 * translucent fills – the warm color tint and a plain dark dim layer.
 *
 * A custom view (instead of a plain background color) lets us change tint and
 * dim independently with a single window and a single draw pass, which keeps
 * GPU cost minimal.
 */
class FilterOverlayView(context: Context) : View(context) {

    private val paint = Paint().apply {
        style = Paint.Style.FILL
    }

    private var tintColor: Int = Color.TRANSPARENT
    private var tintAlpha: Float = 0f
    private var dimAlpha: Float = 0f

    init {
        // Custom drawing: disable the "no drawing needed" optimization.
        setWillNotDraw(false)
    }

    fun setFilter(tintColor: Int, tintAlpha: Float, dimAlpha: Float) {
        if (this.tintColor == tintColor && this.tintAlpha == tintAlpha && this.dimAlpha == dimAlpha) {
            return
        }
        this.tintColor = tintColor
        this.tintAlpha = tintAlpha
        this.dimAlpha = dimAlpha
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (tintAlpha > 0.004f) {
            val alpha = (tintAlpha * 255f).toInt().coerceIn(0, 255)
            paint.color = (alpha shl 24) or (tintColor and 0x00FFFFFF)
            canvas.drawPaint(paint)
        }
        if (dimAlpha > 0.004f) {
            val alpha = (dimAlpha * 255f).toInt().coerceIn(0, 255)
            paint.color = (alpha shl 24) and 0xFF000000.toInt()
            canvas.drawPaint(paint)
        }
    }
}
