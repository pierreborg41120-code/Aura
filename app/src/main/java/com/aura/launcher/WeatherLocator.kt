package com.aura.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Position approximative pour la météo, sans Google Play Services :
 * dernière position connue du système, sinon une position fraîche demandée au réseau.
 * Seule ACCESS_COARSE_LOCATION est utilisée (précision de l'ordre du kilomètre).
 */
object WeatherLocator {
    /** Une position de moins de 3 h suffit pour la météo. */
    private const val FRESH_ENOUGH_MS = 3 * 60 * 60_000L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Position (latitude, longitude), ou null si indisponible (localisation coupée, refusée…). */
    suspend fun locate(context: Context): Pair<Double, Double>? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null

        val last = lastKnown(manager)
        if (last != null && System.currentTimeMillis() - last.time < FRESH_ENOUGH_MS) {
            return last.latitude to last.longitude
        }
        val fresh = withTimeoutOrNull(15_000) { current(context, manager) }
        val best = fresh ?: last ?: return null
        return best.latitude to best.longitude
    }

    /** La plus récente des dernières positions connues (réseau, passive, puis GPS flouté). */
    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? =
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (e: Exception) {
                    // SecurityException, fournisseur absent : on passe au suivant.
                    null
                }
            }
            .maxByOrNull { it.time }

    /** Demande une position fraîche au fournisseur réseau. */
    @SuppressLint("MissingPermission")
    private suspend fun current(context: Context, manager: LocationManager): Location? {
        val provider = when {
            isEnabled(manager, LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            isEnabled(manager, LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }
        return suspendCancellableCoroutine { cont ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (cont.isActive) cont.resume(location)
                        }

                        @Deprecated("Requis avant Android 10")
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {
                        }

                        override fun onProviderEnabled(provider: String) {}

                        override fun onProviderDisabled(provider: String) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                    cont.invokeOnCancellation { manager.removeUpdates(listener) }
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    private fun isEnabled(manager: LocationManager, provider: String): Boolean = try {
        manager.isProviderEnabled(provider)
    } catch (e: Exception) {
        false
    }
}
