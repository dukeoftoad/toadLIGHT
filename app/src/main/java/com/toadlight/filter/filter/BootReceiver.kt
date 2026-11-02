package com.toadlight.filter.filter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.toadlight.filter.ToadlightApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restarts the filter after reboot (or app update) and nudges the engine when
 * the clock or time zone changes.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
            )
        ) {
            return
        }

        val graph = (context.applicationContext as ToadlightApp).graph
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = graph.repo.settingsFlow.first()
                if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    if (settings.masterEnabled && settings.onboardingDone && settings.startOnBoot &&
                        Settings.canDrawOverlays(context)
                    ) {
                        FilterService.start(context)
                    }
                } else {
                    // Clock / time zone changed: re-evaluate the schedule.
                    if (settings.masterEnabled && settings.onboardingDone &&
                        Settings.canDrawOverlays(context) && graph.serviceRunning.value
                    ) {
                        FilterService.refresh(context)
                    } else {
                        graph.engine.recompute()
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
