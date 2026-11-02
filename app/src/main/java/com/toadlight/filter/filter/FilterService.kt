package com.toadlight.filter.filter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.toadlight.filter.ToadlightApp
import com.toadlight.filter.MainActivity
import com.toadlight.filter.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Foreground service that owns the overlay window.
 *
 * - Adds / removes / recolors [FilterOverlayView] based on [FilterEngine].
 * - Runs a ticker that wakes the engine right before the next schedule event
 *   (sunset, sunrise, custom boundary, pause expiry or in-progress fade),
 *   so nothing needs exact alarms.
 * - Shows an actionable notification (pause / resume / stop).
 */
class FilterService : LifecycleService() {

    private lateinit var graph: com.toadlight.filter.core.AppGraph
    private lateinit var windowManager: WindowManager

    private var overlayView: FilterOverlayView? = null
    private var floatingMenu: FloatingMenuView? = null
    private var lastNotificationKey: String = ""
    private var destroyed = false
    private var gracefulStopScheduled = false

    /**
     * App-level scope for settings writes. Writes launched from the service's
     * own lifecycleScope used to be cancelled mid-flight when the service
     * stopped itself, leaving settings saying "on" while nothing ran.
     */
    private val appScope get() = graph.scope

    override fun onCreate() {
        super.onCreate()

        graph = (application as ToadlightApp).graph
        graph.serviceRunning.value = true
        windowManager = getSystemService(WindowManager::class.java)

        createNotificationChannel()
        startForegroundTyped(buildNotification(graph.engine.state.value))

        lifecycleScope.launch {
            graph.engine.state.collect { applyState(it) }
        }
        lifecycleScope.launch {
            while (isActive) {
                graph.engine.recompute()
                applyState(graph.engine.state.value)
                delay(graph.engine.nextTickDelayMs())
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                appScope.launch {
                    graph.repo.setMasterEnabled(false)
                    graph.repo.endPreview()
                }
                stopSelf()
            }

            ACTION_TOGGLE -> {
                val currentlyOn = graph.settingsState.value.masterEnabled
                appScope.launch {
                    if (currentlyOn) {
                        graph.repo.setMasterEnabled(false)
                        graph.repo.endPreview()
                    } else {
                        graph.repo.setMasterEnabled(true)
                    }
                }
                // State observation (or the graceful stop below) does the rest.
            }

            ACTION_PAUSE_30M -> appScope.launch { graph.repo.pauseFor(30 * 60_000L) }
            ACTION_PAUSE_1H -> appScope.launch { graph.repo.pauseFor(60 * 60_000L) }
            ACTION_RESUME -> appScope.launch { graph.repo.resume() }
            ACTION_REFRESH -> graph.engine.recompute()
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation: re-measure the overlay (and possibly suppress it in landscape).
        removeOverlay()
        floatingMenu?.onScreenChanged()
        graph.engine.recompute()
        applyState(graph.engine.state.value)
    }

    override fun onDestroy() {
        destroyed = true
        removeOverlay()
        detachFloatingMenu()
        graph.serviceRunning.value = false
        graph.overlayAttachFailed.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun applyState(state: EngineState) {
        if (destroyed) return

        if (!state.masterOn) {
            removeOverlay()
            detachFloatingMenu()
            graph.overlayAttachFailed.value = false
            scheduleGracefulStop()
            maybeUpdateNotification(state)
            return
        }

        val settingsNow = graph.settingsState.value
        val allowLandscape = settingsNow.filterLandscape
        val orientationOk =
            allowLandscape || resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val canOverlay = Settings.canDrawOverlays(this)

        val wantOverlay = state.overlayOn && orientationOk && canOverlay
        if (wantOverlay && overlayView == null) {
            addOverlay()
        } else if (!wantOverlay && overlayView != null) {
            removeOverlay()
        }
        overlayView?.setFilter(state.tintArgb, state.tintAlpha, state.dimAlpha)

        // Draggable bubble menu (optional).
        val wantBubble = settingsNow.floatingMenuEnabled && canOverlay
        if (wantBubble && floatingMenu == null) {
            attachFloatingMenu(settingsNow)
        } else if (!wantBubble && floatingMenu != null) {
            detachFloatingMenu()
        }
        floatingMenu?.update(
            filterVisible = state.overlayOn && orientationOk,
            paused = state.paused,
        )

        maybeUpdateNotification(state)
    }

    /**
     * When the engine reports "off", give the toggle a short grace period
     * before really stopping. This covers two races: (a) the user flips the
     * switch back on within a second, (b) a stale StateFlow snapshot still
     * said "off" while the freshly written "on" lands a moment later.
     */
    private fun scheduleGracefulStop() {
        if (gracefulStopScheduled) return
        gracefulStopScheduled = true
        lifecycleScope.launch {
            delay(1500)
            gracefulStopScheduled = false
            if (destroyed) return@launch
            if (!graph.settingsState.value.masterEnabled) {
                stopSelf()
            }
        }
    }

    private fun addOverlay() {
        if (overlayView != null || !Settings.canDrawOverlays(this)) return

        val navbarFilter = graph.settingsState.value.filterNavbar
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            if (navbarFilter) WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS else 0

        val width: Int
        val height: Int
        if (navbarFilter) {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        } else {
            val size = realScreenSize()
            width = size.x
            height = size.y
        }

        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        val view = FilterOverlayView(this)
        runCatching {
            windowManager.addView(view, params)
            overlayView = view
            graph.overlayAttachFailed.value = false
        }.onFailure {
            // Some OEMs reject the window (battery savers, "display pop-up"
            // toggles per app). Surface it instead of failing silently.
            graph.overlayAttachFailed.value = true
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
    }

    // ------------------------------------------------------------------ //
    // Floating bubble menu
    // ------------------------------------------------------------------ //

    private fun attachFloatingMenu(settings: com.toadlight.filter.core.model.AppSettings) {
        if (!Settings.canDrawOverlays(this)) return

        val bounds = windowManager.maximumWindowMetrics.bounds
        val defaultX = bounds.width() - dp(72)
        val defaultY = bounds.height() * 58 / 100

        val x = settings.bubblePosX.takeIf { it != Int.MIN_VALUE } ?: defaultX
        val y = settings.bubblePosY.takeIf { it != Int.MIN_VALUE } ?: defaultY

        floatingMenu = FloatingMenuView(
            context = this,
            initialX = x,
            initialY = y,
            onAction = ::handleBubbleAction,
            onPositionSaved = { px, py ->
                graph.scope.launch { graph.repo.saveBubblePosition(px, py) }
            },
        ).also { it.attach() }
    }

    private fun detachFloatingMenu() {
        floatingMenu?.detach()
        floatingMenu = null
    }

    private fun handleBubbleAction(action: FloatingMenuView.Action) {
        graph.scope.launch {
            when (action) {
                FloatingMenuView.Action.PLAY_PAUSE -> {
                    val s = graph.engine.state.value
                    when {
                        s.paused -> graph.repo.resume()
                        s.overlayOn || s.previewing -> graph.repo.pauseFor(30 * 60_000L)
                        else -> {
                            graph.repo.setMasterEnabled(true)
                            graph.repo.startPreview(2 * 60 * 60_000L)
                        }
                    }
                }

                FloatingMenuView.Action.WARMER -> {
                    graph.repo.updateActiveProfile {
                        it.copy(colorTempK = (it.colorTempK - 300).coerceIn(1000, 6500))
                    }
                    nudgePreviewAfterAdjust()
                }

                FloatingMenuView.Action.COOLER -> {
                    graph.repo.updateActiveProfile {
                        it.copy(colorTempK = (it.colorTempK + 300).coerceIn(1000, 6500))
                    }
                    nudgePreviewAfterAdjust()
                }

                FloatingMenuView.Action.DIM_UP -> {
                    graph.repo.updateActiveProfile {
                        it.copy(dimPercent = (it.dimPercent + 10).coerceIn(0, 95))
                    }
                    nudgePreviewAfterAdjust()
                }

                FloatingMenuView.Action.DIM_DOWN -> {
                    graph.repo.updateActiveProfile {
                        it.copy(dimPercent = (it.dimPercent - 10).coerceIn(0, 95))
                    }
                    nudgePreviewAfterAdjust()
                }

                FloatingMenuView.Action.STOP -> {
                    graph.repo.setMasterEnabled(false)
                    graph.repo.endPreview()
                }
            }
        }
    }

    /** After tweaking values from the bubble while the filter is hidden, show it briefly. */
    private suspend fun nudgePreviewAfterAdjust() {
        val s = graph.engine.state.value
        if (s.masterOn && !s.paused && !s.overlayOn && !s.previewing) {
            graph.repo.startPreview(2 * 60_000L)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun realScreenSize(): android.graphics.Point {
        val bounds = windowManager.maximumWindowMetrics.bounds
        return android.graphics.Point(bounds.width(), bounds.height())
    }

    // ------------------------------------------------------------------ //
    // Notification
    // ------------------------------------------------------------------ //

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundTyped(notification: Notification) {
        startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun maybeUpdateNotification(state: EngineState) {
        val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
        val zone = ZoneId.systemDefault()
        fun fmt(t: Long?): String? =
            t?.let { timeFmt.format(Instant.ofEpochMilli(it).atZone(zone)) }

        val key = buildString {
            append(state.masterOn).append('|')
            append(state.paused).append('|')
            append(state.overlayOn).append('|')
            append(state.scheduleOpen).append('|')
            append(state.previewing).append('|')
            append(state.profileName).append('|')
            append(fmt(state.pauseEndsAt)).append('|')
            append(fmt(state.previewEndsAt)).append('|')
            append(fmt(state.sunriseAt)).append('|')
            append(fmt(state.sunsetAt)).append('|')
            append(state.colorTempK).append('|')
            append(state.usingFallbackSchedule)
        }
        if (key == lastNotificationKey) return
        lastNotificationKey = key

        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ID, buildNotification(state))
        }
    }

    private fun buildNotification(state: EngineState): Notification {
        val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
        val zone = ZoneId.systemDefault()
        fun fmt(t: Long?): String? =
            t?.let { timeFmt.format(Instant.ofEpochMilli(it).atZone(zone)) }

        val title: String
        val text: String
        when {
            state.paused -> {
                title = getString(R.string.app_name) + " · paused"
                text = "Resumes " + (fmt(state.pauseEndsAt) ?: "soon")
            }

            state.overlayOn && state.previewing -> {
                title = "Warm filter on · ${state.profileName}"
                text = "Preview · schedule takes over at " + (fmt(state.previewEndsAt) ?: "soon")
            }

            state.overlayOn && state.scheduleOpen -> {
                title = "Warm filter on · ${state.profileName}"
                text = buildString {
                    append("${state.colorTempK}K · ${state.intensityPercent}% · dim ${state.dimPercent}%")
                    fmt(state.sunriseAt)?.let { append(" · until sunrise $it") }
                }
            }

            !state.scheduleOpen -> {
                title = "Waiting for sunset"
                text = buildString {
                    fmt(state.sunsetAt)?.let { append("Starts $it") }
                    if (state.usingFallbackSchedule) {
                        append(" · set your location for real sun times")
                    }
                }
            }

            else -> {
                title = getString(R.string.app_name)
                text = "Filter ready"
            }
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun action(action: String, label: String): NotificationCompat.Action {
            val pending = PendingIntent.getService(
                this,
                action.hashCode(),
                Intent(this, FilterService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Action.Builder(0, label, pending).build()
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_toadlight)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .apply {
                if (state.paused) {
                    addAction(action(ACTION_RESUME, getString(R.string.notif_action_resume)))
                } else {
                    addAction(action(ACTION_PAUSE_30M, getString(R.string.notif_action_pause)))
                }
                addAction(action(ACTION_STOP, getString(R.string.notif_action_stop)))
            }
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "filter"
        private const val NOTIF_ID = 42

        const val ACTION_START = "com.toadlight.filter.action.START"
        const val ACTION_STOP = "com.toadlight.filter.action.STOP"
        const val ACTION_TOGGLE = "com.toadlight.filter.action.TOGGLE"
        const val ACTION_PAUSE_30M = "com.toadlight.filter.action.PAUSE_30M"
        const val ACTION_PAUSE_1H = "com.toadlight.filter.action.PAUSE_1H"
        const val ACTION_RESUME = "com.toadlight.filter.action.RESUME"
        const val ACTION_REFRESH = "com.toadlight.filter.action.REFRESH"

        /** Idempotent start; safe to call when already running. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, FilterService::class.java).setAction(ACTION_START),
            )
        }

        /**
         * Turns the filter off. Persists the setting first (on the app-level
         * scope so it survives the service), then asks a running service to
         * stop itself.
         */
        fun stop(context: Context) {
            val app = context.applicationContext as ToadlightApp
            app.graph.scope.launch {
                app.graph.repo.setMasterEnabled(false)
                app.graph.repo.endPreview()
            }
            if (app.graph.serviceRunning.value) {
                runCatching {
                    context.startService(
                        Intent(context, FilterService::class.java).setAction(ACTION_STOP),
                    )
                }
            }
        }

        fun refresh(context: Context) {
            if ((context.applicationContext as ToadlightApp).graph.serviceRunning.value) {
                context.startService(
                    Intent(context, FilterService::class.java).setAction(ACTION_REFRESH),
                )
            }
        }
    }
}
