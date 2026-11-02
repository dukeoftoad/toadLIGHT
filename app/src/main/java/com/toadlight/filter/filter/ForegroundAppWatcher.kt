package com.toadlight.filter.filter

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.toadlight.filter.ToadlightApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Optional accessibility service with two jobs:
 *
 * 1. **Foreground-app detection** — reports the foreground app's package so
 *    per-app filter rules work. Mature implementation: instead of trusting
 *    window-state-change events (which fire for dialogs, toasts and the
 *    notification shade too), it scans the live window list for the focused
 *    application window and filters out system noise.
 *
 * 2. **Global coverage** — when the user turns on "Filter notification
 *    shade", the service owns the filter overlay using
 *    [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY]. That window
 *    type sits above the status bar, the navigation bar and even the
 *    expanded notification shade — the one surface a normal app overlay
 *    (TYPE_APPLICATION_OVERLAY) can never reach.
 *
 * Privacy by design: the service never retrieves window content
 * (canRetrieveWindowContent = false); it only reads package names.
 */
class ForegroundAppWatcher : AccessibilityService() {

    private val graph get() = (applicationContext as ToadlightApp).graph
    private lateinit var windowManager: WindowManager
    private var scope: CoroutineScope? = null
    private var overlayView: FilterOverlayView? = null
    private var lastGpuMode: Boolean? = null
    private var lastPkg: String? = null
    private var destroyed = false

    @Volatile
    private var keyguardLocked = false

    private val keyguardReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val locked = getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true
            if (locked != keyguardLocked) {
                keyguardLocked = locked
                applyOverlay(graph.engine.state.value)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WindowManager::class.java)
        graph.a11yConnected.value = true
        lastPkg = null

        ContextCompat.registerReceiver(
            this,
            keyguardReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        s.launch { graph.engine.state.collect { applyOverlay(it) } }
        s.launch { graph.settingsState.collect { applyOverlay(graph.engine.state.value) } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (isNoise(pkg)) return
        if (!isLaunchable(pkg)) return
        if (pkg != lastPkg) {
            lastPkg = pkg
            graph.engine.setForegroundPackage(pkg)
        }
    }

    override fun onInterrupt() {
        // Nothing to do.
    }

    override fun onUnbind(intent: Intent): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        if (destroyed) return
        destroyed = true
        graph.a11yConnected.value = false
        graph.engine.setForegroundPackage(null)
        lastPkg = null
        runCatching { unregisterReceiver(keyguardReceiver) }
        removeOverlay()
        scope?.cancel()
        scope = null
    }

    // ------------------------------------------------------------------ //
    // Foreground-app detection
    // ------------------------------------------------------------------ //

    /** Packages already confirmed to have a launcher entry. */
    private val launchableCache = HashSet<String>()

    /**
     * The old naive version fed *every* window-state event into the engine —
     * including the notification shade, IMEs and service popups — so opening
     * the shade silently reset per-app rules to the global default. This one
     * filters all of that noise: only launchable, non-system app windows
     * count as "the foreground app", and the last real app is kept while
     * transient system windows are on screen.
     */
    private fun isNoise(pkg: String): Boolean {
        if (pkg.isBlank() || pkg == packageName || pkg == "com.android.systemui") return true
        if (imePackages.contains(pkg)) return true
        return false
    }

    private val imePackages: Set<String> by lazy {
        runCatching {
            getSystemService(InputMethodManager::class.java)
                ?.enabledInputMethodList
                ?.mapNotNull { it.packageName }
                ?.toSet()
        }.getOrNull() ?: emptySet()
    }

    /** A real app (has a launcher entry) — filters toasts from services etc. */
    private fun isLaunchable(pkg: String): Boolean {
        if (pkg in launchableCache) return true
        val launchable = runCatching {
            packageManager.getLaunchIntentForPackage(pkg) != null
        }.getOrDefault(true)
        if (launchable) launchableCache.add(pkg)
        return launchable
    }

    // ------------------------------------------------------------------ //
    // Global overlay (TYPE_ACCESSIBILITY_OVERLAY)
    // ------------------------------------------------------------------ //

    private fun applyOverlay(state: EngineState) {
        if (destroyed || !this::windowManager.isInitialized) return
        val settings = graph.settingsState.value

        val orientationOk = settings.filterLandscape ||
            resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val lockOk = settings.filterLockScreen || !keyguardLocked
        val want = settings.filterNotificationShade &&
            (state.overlayOn || state.fading) && orientationOk && lockOk
        val gpuNow = settings.useGpuRendering

        if (want && (overlayView == null || lastGpuMode != gpuNow)) {
            removeOverlay()
            addOverlay(gpuNow)
        } else if (!want && overlayView != null) {
            removeOverlay()
        }
        overlayView?.setFilter(
            state.tintArgb,
            state.tintAlpha,
            state.dimAlpha,
            state.paperAlpha,
            animateMs = if (state.colorFading) settings.quickTransitionMs.toLong() else 0L,
        )
    }

    private fun addOverlay(gpu: Boolean) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        val view = FilterOverlayView(this).apply { gpuMode = gpu }
        runCatching {
            windowManager.addView(view, params)
            overlayView = view
            lastGpuMode = gpu
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
    }
}
