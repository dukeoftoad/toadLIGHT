package com.toadlight.filter.filter

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.toadlight.filter.ToadlightApp

/**
 * Optional accessibility service that reports the foreground app's package
 * name so the engine can apply per-app filter behavior.
 *
 * Privacy by design: the service only listens for window-state changes and
 * never retrieves window content (canRetrieveWindowContent = false).
 */
class ForegroundAppWatcher : AccessibilityService() {

    private val graph get() = (applicationContext as ToadlightApp).graph

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        graph.engine.setForegroundPackage(pkg)
    }

    override fun onInterrupt() {
        // Nothing to do.
    }

    override fun onDestroy() {
        graph.engine.setForegroundPackage(null)
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent): Boolean {
        graph.engine.setForegroundPackage(null)
        return super.onUnbind(intent)
    }
}
