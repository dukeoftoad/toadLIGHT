package com.toadlight.filter.filter

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.toadlight.filter.R
import kotlin.math.abs

/**
 * Draggable floating bubble with a compact quick-action menu, drawn over
 * other apps (needs the same overlay permission as the filter itself).
 *
 * - The bubble is a small always-visible circle while the filter runs.
 *   Drag it anywhere; the position is reported back to be persisted.
 * - Tapping the bubble toggles a popup menu (play/pause, warmer, cooler,
 *   dim, stop). The menu closes on outside touch or after an action.
 */
class FloatingMenuView(
    context: Context,
    initialX: Int,
    initialY: Int,
    private val onAction: (Action) -> Unit,
    private val onPositionSaved: (Int, Int) -> Unit,
) {

    enum class Action { PLAY_PAUSE, WARMER, COOLER, DIM_UP, DIM_DOWN, STOP }

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)

    private val bubbleSize = dp(56)
    private val menuWidth = dp(224)
    private val slop = ViewConfiguration.get(appContext).scaledTouchSlop

    private val bubble: ImageView
    private val menu: LinearLayout
    private val playPauseItem: TextView
    private var menuAttached = false

    private val bubbleParams = WindowManager.LayoutParams(
        bubbleSize,
        bubbleSize,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = initialX
        y = initialY
    }

    private val bubbleTouch = object : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = bubbleParams.x
                    startY = bubbleParams.y
                    moved = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                    if (moved) {
                        bubbleParams.x = startX + dx.toInt()
                        bubbleParams.y = startY + dy.toInt()
                        runCatching {
                            windowManager?.updateViewLayout(bubble, bubbleParams)
                        }
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (event.actionMasked == MotionEvent.ACTION_UP && !moved) {
                        toggleMenu()
                    } else {
                        clampBubble()
                        runCatching {
                            windowManager?.updateViewLayout(bubble, bubbleParams)
                        }
                        onPositionSaved(bubbleParams.x, bubbleParams.y)
                    }
                    return true
                }

                else -> return false
            }
        }
    }

    init {
        bubble = ImageView(appContext).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xD91C1B2E.toInt())
                setStroke(dp(2), 0xFFFFD180.toInt())
            }
            setImageResource(R.drawable.ic_pause)
            imageTintList = ColorStateList.valueOf(0xFFFFD9A0.toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(14), dp(14), dp(14), dp(14))
            contentDescription = "toadLIGHT quick menu"
            setOnTouchListener(bubbleTouch)
        }

        menu = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(0xF21B1A2E.toInt())
                setStroke(dp(1), 0x40FFD180.toInt())
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    toggleMenu()
                    true
                } else {
                    false
                }
            }
        }

        playPauseItem = menuItem("Pause 30 min", Action.PLAY_PAUSE, highlight = true)
        menu.addView(playPauseItem)
        menu.addView(menuItem("Warmer · −300K", Action.WARMER))
        menu.addView(menuItem("Cooler · +300K", Action.COOLER))
        menu.addView(menuItem("Dim +10%", Action.DIM_UP))
        menu.addView(menuItem("Dim −10%", Action.DIM_DOWN))
        menu.addView(menuItem("Stop filter", Action.STOP, danger = true))
    }

    // ------------------------------------------------------------------ //
    // Window lifecycle
    // ------------------------------------------------------------------ //

    fun attach() {
        val wm = windowManager ?: return
        clampBubble()
        runCatching { wm.addView(bubble, bubbleParams) }
    }

    fun detach() {
        val wm = windowManager ?: return
        if (menuAttached) runCatching { wm.removeView(menu) }
        menuAttached = false
        runCatching { wm.removeView(bubble) }
    }

    /** Called on rotation / screen-size change: close menu, re-clamp position. */
    fun onScreenChanged() {
        if (menuAttached) toggleMenu()
        clampBubble()
        runCatching { windowManager?.updateViewLayout(bubble, bubbleParams) }
    }

    /**
     * Reflects the current engine state: bubble icon + first menu label.
     *
     * @param filterVisible the overlay is currently warming the screen.
     * @param paused the filter is paused (tap should resume).
     */
    fun update(filterVisible: Boolean, paused: Boolean) {
        val showPlay = paused || !filterVisible
        bubble.setImageResource(if (showPlay) R.drawable.ic_play else R.drawable.ic_pause)
        playPauseItem.text = when {
            paused -> "Resume filter"
            filterVisible -> "Pause 30 min"
            else -> "Turn on now"
        }
    }

    // ------------------------------------------------------------------ //
    // Internals
    // ------------------------------------------------------------------ //

    private fun toggleMenu() {
        val wm = windowManager ?: return
        if (menuAttached) {
            runCatching { wm.removeView(menu) }
            menuAttached = false
            return
        }

        menu.measure(
            View.MeasureSpec.makeMeasureSpec(menuWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val menuHeight = menu.measuredHeight

        val bounds = wm.maximumWindowMetrics.bounds
        val screenW = bounds.width()
        val screenH = bounds.height()

        // Right-align the menu with the bubble, clamped to the screen.
        val x = (bubbleParams.x + bubbleSize - menuWidth)
            .coerceIn(0, (screenW - menuWidth).coerceAtLeast(0))

        // Open away from the nearest screen edge (above if in lower half).
        val above = bubbleParams.y + bubbleSize / 2 > screenH / 2
        val y = if (above) {
            (bubbleParams.y - menuHeight - dp(12)).coerceAtLeast(0)
        } else {
            (bubbleParams.y + bubbleSize + dp(12)).coerceAtMost((screenH - menuHeight).coerceAtLeast(0))
        }

        val params = WindowManager.LayoutParams(
            menuWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

        runCatching { wm.addView(menu, params) }.onSuccess { menuAttached = true }
    }

    private fun clampBubble() {
        val wm = windowManager ?: return
        val bounds = wm.maximumWindowMetrics.bounds
        bubbleParams.x = bubbleParams.x.coerceIn(0, (bounds.width() - bubbleSize).coerceAtLeast(0))
        bubbleParams.y = bubbleParams.y.coerceIn(0, (bounds.height() - bubbleSize).coerceAtLeast(0))
    }

    private fun menuItem(
        label: String,
        action: Action,
        highlight: Boolean = false,
        danger: Boolean = false,
    ): TextView {
        val ripple = TypedValue().let {
            appContext.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
            it.resourceId
        }
        return TextView(appContext).apply {
            text = label
            textSize = 15f
            setTextColor(
                when {
                    danger -> 0xFFFF8A80.toInt()
                    highlight -> 0xFFFFD9A0.toInt()
                    else -> 0xFFF3EFE8.toInt()
                }
            )
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            setBackgroundResource(ripple)
            setOnClickListener {
                toggleMenu() // close after picking an action
                onAction(action)
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * appContext.resources.displayMetrics.density).toInt()
}
