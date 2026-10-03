package com.childprotect.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Single entry point for acquiring a location fix. */
object Locator {

    /** Good enough to stop early and report immediately. */
    private const val TARGET_ACCURACY_M = 20f

    /** How long we keep refining before reporting the best we have. */
    private const val MAX_WAIT_MS = 25_000L

    fun hasPermission(context: Context): Boolean =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Acquire the most accurate fix we reasonably can before replying.
     *
     * Rather than take a single one-shot reading (often the first, coarse fix),
     * we stream high-accuracy updates and keep the best one, returning early as
     * soon as a fix is within [TARGET_ACCURACY_M] or when [MAX_WAIT_MS] elapses.
     * Falls back to a one-shot current fix and then last-known so we always send
     * *something* if any location is available. Returns null only with no fix at
     * all or no permission.
     */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Location? {
        if (!hasPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)

        val best = withTimeoutOrNull(MAX_WAIT_MS) {
            suspendCancellableCoroutine<Location?> { cont ->
                var bestFix: Location? = null
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                    .setMinUpdateIntervalMillis(500L)
                    .setMaxUpdateDelayMillis(0L)
                    .build()

                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        for (loc in result.locations) {
                            if (bestFix == null || loc.accuracy < bestFix!!.accuracy) {
                                bestFix = loc
                            }
                        }
                        val b = bestFix
                        if (b != null && b.accuracy <= TARGET_ACCURACY_M && cont.isActive) {
                            client.removeLocationUpdates(this)
                            cont.resume(b)
                        }
                    }
                }

                client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                cont.invokeOnCancellation {
                    // Timed out: hand back the best fix gathered so far.
                    client.removeLocationUpdates(callback)
                }
            }
        }
        if (best != null) return best

        // The streaming attempt timed out with nothing — try a one-shot, then last-known.
        val oneShot = suspendCancellableCoroutine<Location?> { cont ->
            val cts = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            cont.invokeOnCancellation { cts.cancel() }
        }
        if (oneShot != null) return oneShot

        return suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
    }
}
