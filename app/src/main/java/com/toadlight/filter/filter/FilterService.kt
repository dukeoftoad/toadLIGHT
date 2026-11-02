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
import android.os.Build
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
    private var lastNotificationKey: String = ""
    private var destroyed = false

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
                lifecycleScope.launch { graph.repo.setMasterEnabled(false) }
                stopSelf()
            }

            ACTION_TOGGLE -> {
                val masterOn = graph.engine.state.value.masterOn
                if (masterOn) {
                    lifecycleScope.launch { graph.repo.setMasterEnabled(false) }
                    stopSelf()
                } else {
                    lifecycleScope.launch { graph.repo.setMasterEnabled(true) }
                }
            }

            ACTION_PAUSE_30M -> lifecycleScope.launch { graph.repo.pauseFor(30 * 60_000L) }
            ACTION_PAUSE_1H -> lifecycleScope.launch { graph.repo.pauseFor(60 * 60_000L) }
            ACTION_RESUME -> lifecycleScope.launch { graph.repo.resume() }
            ACTION_REFRESH -> graph.engine.recompute()
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation: re-measure the overlay (and possibly suppress it in landscape).
        removeOverlay()
        graph.engine.recompute()
        applyState(graph.engine.state.value)
    }

    override fun onDestroy() {
        destroyed = true
        removeOverlay()
        graph.serviceRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun applyState(state: EngineState) {
        if (destroyed) return

        if (!state.masterOn) {
            stopSelf()
            return
        }

        val allowLandscape = graph.settingsState.value.filterLandscape
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

        maybeUpdateNotification(state)
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
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
    }

    private fun realScreenSize(): android.graphics.Point {
        return if (Build.VERSION.SDK_INT >= 30) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            android.graphics.Point(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            android.graphics.Point(metrics.widthPixels, metrics.heightPixels)
        }
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
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
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
            append(state.profileName).append('|')
            append(fmt(state.pauseEndsAt)).append('|')
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

        /** Only meaningful while the service is running. */
        fun stop(context: Context) {
            context.startService(
                Intent(context, FilterService::class.java).setAction(ACTION_STOP),
            )
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
