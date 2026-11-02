package com.toadlight.filter.core.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Location helper without any Google Play Services dependency: uses the
 * platform LocationManager with a "last known, else single fix" strategy.
 */
object LocationGrabber {

    /**
     * Returns (latitude, longitude) or null when no fix is available.
     */
    suspend fun currentLocation(context: Context, timeoutMs: Long = 15_000L): Pair<Double, Double>? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null

        // 1) Cheapest option: last known location from any provider.
        for (provider in listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            )
        ) {
            val last = runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
            if (last != null) {
                return Pair(last.latitude, last.longitude)
            }
        }

        // 2) Otherwise request a single fresh fix (GPS or network).
        val activeProvider = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        ).firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return null

        val fix = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (continuation.isActive) {
                            continuation.resume(Pair(location.latitude, location.longitude))
                        }
                        runCatching { lm.removeUpdates(this) }
                    }
                }
                try {
                    @Suppress("DEPRECATION")
                    lm.requestLocationUpdates(
                        activeProvider,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper(),
                    )
                } catch (e: Exception) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }
                continuation.invokeOnCancellation {
                    runCatching { lm.removeUpdates(listener) }
                }
            }
        }
        return fix
    }

    fun hasPermission(context: Context): Boolean {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        return granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}
