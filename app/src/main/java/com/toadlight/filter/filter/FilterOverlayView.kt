package com.toadlight.filter.filter

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.graphics.ColorUtils

/**
 * The actual overlay: a full-screen, non-touchable view that paints the
 * layers of the filter – warm color tint, optional paper finish (cream tone
 * + grain), and a plain dark dim layer – with a single window and a single
 * draw pass.
 *
 * Two rendering paths:
 *  - GPU (default): the view runs on a hardware layer and the grain is a
 *    tiled [BitmapShader] composited by the GPU.
 *  - CPU fallback: a software layer, for the rare device where hardware
 *    overlay windows glitch (flicker or torn colors on some OEMs).
 *
 * [setFilter] can animate changes (used for smooth mode switches) via a
 * [ValueAnimator]; the engine-driven sunrise/sunset/on-off fades instead
 * arrive as a stream of already-interpolated values.
 */
class FilterOverlayView(context: Context) : View(context) {

    private val paint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val grainPaint = Paint().apply {
        style = Paint.Style.FILL
    }
    private var grainShader: BitmapShader? = null

    /** Currently displayed values (may be mid-animation). */
    private var tintColor: Int = Color.TRANSPARENT
    private var tintAlpha: Float = 0f
    private var dimAlpha: Float = 0f
    private var paperAlpha: Float = 0f

    /** Latest targets; used so repeated identical calls don't restart animators. */
    private var targetTintColor: Int = Color.TRANSPARENT
    private var targetTintAlpha: Float = 0f
    private var targetDimAlpha: Float = 0f
    private var targetPaperAlpha: Float = 0f

    private var colorAnimator: ValueAnimator? = null

    /** GPU rendering toggle; switching re-selects the view's layer type. */
    var gpuMode: Boolean = true
        set(value) {
            field = value
            applyLayerType()
        }

    init {
        // Custom drawing: disable the "no drawing needed" optimization.
        setWillNotDraw(false)
        applyLayerType()
    }

    /**
     * @param animateMs when > 0 the change is animated over this duration
     *   (used for mode/profile switches so colors cross-fade).
     */
    fun setFilter(
        tintColor: Int,
        tintAlpha: Float,
        dimAlpha: Float,
        paperAlpha: Float,
        animateMs: Long = 0L,
    ) {
        if (tintColor == targetTintColor && tintAlpha == targetTintAlpha &&
            dimAlpha == targetDimAlpha && paperAlpha == targetPaperAlpha
        ) {
            return
        }

        val startColor = this.tintColor
        val startTint = this.tintAlpha
        val startDim = this.dimAlpha
        val startPaper = this.paperAlpha

        targetTintColor = tintColor
        targetTintAlpha = tintAlpha
        targetDimAlpha = dimAlpha
        targetPaperAlpha = paperAlpha

        colorAnimator?.cancel()

        if (animateMs > 0L) {
            colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = animateMs
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    val f = anim.animatedValue as Float
                    this@FilterOverlayView.tintColor =
                        ColorUtils.blendARGB(startColor, tintColor, f)
                    this@FilterOverlayView.tintAlpha = startTint + (tintAlpha - startTint) * f
                    this@FilterOverlayView.dimAlpha = startDim + (dimAlpha - startDim) * f
                    this@FilterOverlayView.paperAlpha = startPaper + (paperAlpha - startPaper) * f
                    invalidate()
                }
                doOnEnd { snapToTargets() }
                start()
            }
        } else {
            snapToTargets()
        }
    }

    private fun snapToTargets() {
        tintColor = targetTintColor
        tintAlpha = targetTintAlpha
        dimAlpha = targetDimAlpha
        paperAlpha = targetPaperAlpha
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (tintAlpha > 0.004f) {
            val alpha = (tintAlpha * 255f).toInt().coerceIn(0, 255)
            paint.color = (alpha shl 24) or (tintColor and 0x00FFFFFF)
            canvas.drawPaint(paint)
        }
        if (paperAlpha > 0.004f) {
            // Cream paper base tone under the grain.
            val alpha = (paperAlpha * 0.22f * 255f).toInt().coerceIn(0, 255)
            paint.color = (alpha shl 24) or (PaperGrain.PAPER_TONE and 0x00FFFFFF)
            canvas.drawPaint(paint)

            // Fibrous grain, tiled and scaled so speckles stay visible on
            // high-density screens.
            if (grainShader == null) {
                val tile = PaperGrain.tileBitmap()
                val matrix = Matrix().apply { setScale(3f, 3f) }
                grainShader = BitmapShader(
                    tile,
                    Shader.TileMode.REPEAT,
                    Shader.TileMode.REPEAT,
                ).apply { setLocalMatrix(matrix) }
            }
            grainPaint.shader = grainShader
            grainPaint.alpha = (paperAlpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawPaint(grainPaint)
        }
        if (dimAlpha > 0.004f) {
            val alpha = (dimAlpha * 255f).toInt().coerceIn(0, 255)
            paint.color = (alpha shl 24) and 0xFF000000.toInt()
            canvas.drawPaint(paint)
        }
    }

    override fun onDetachedFromWindow() {
        colorAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun applyLayerType() {
        setLayerType(
            if (gpuMode) LAYER_TYPE_HARDWARE else LAYER_TYPE_SOFTWARE,
            null,
        )
    }
}
