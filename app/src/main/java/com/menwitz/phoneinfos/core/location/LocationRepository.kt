package com.menwitz.phoneinfos.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.*
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.menwitz.phoneinfos.core.model.LocationSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant
import android.os.Handler

/**
 * Streams live GNSS fixes (GPS-provider only, to keep APK tiny) and exposes
 * helpers for accuracy-mode and mock-location inspection.
 */
class LocationRepository(
    private val ctx: Context,
    private val scope: CoroutineScope
) {

    private val lm: LocationManager = ctx.getSystemService(LocationManager::class.java)

    /** Null until the user grants FINE-LOCATION. */
    val locationFlow: StateFlow<LocationSnapshot?> =
        callbackFlow<LocationSnapshot?> {

            /* ----------  runtime permission guard  ---------- */
            if (ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                trySend(null)             // surface 'missing-perm' to UI
                awaitClose { /* noop */ }
                return@callbackFlow
            }

            /* ----------  listener registration  ---------- */
            var latest: LocationSnapshot? = null

            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    latest = loc.toSnapshot()
                    trySend(latest)
                }
            }

            try {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1_000L,               // 1 s
                    0f,
                    listener,
                    ctx.mainLooper
                )
            } catch (sec: SecurityException) {
                close(sec)               // permission was yanked mid-session
                return@callbackFlow
            }

            /*  last-known quick start  */
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?.let {
                    latest = it.toSnapshot()
                    trySend(latest)
                }

            /*  optional satellite-usage enrichment  */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val gnssCb = object : GnssStatus.Callback() {
                    override fun onSatelliteStatusChanged(status: GnssStatus) {
                        val used = (0 until status.satelliteCount).count { status.usedInFix(it) }
                        val view = status.satelliteCount
                        latest?.let {
                            latest = it.copy(satsUsed = used, satsInView = view)
                            trySend(latest)
                        }
                    }
                }

                lm.registerGnssStatusCallback(gnssCb, Handler(ctx.mainLooper))

                awaitClose {
                    lm.removeUpdates(listener)
                    lm.unregisterGnssStatusCallback(gnssCb)
                }
            } else {
                awaitClose { lm.removeUpdates(listener) }
            }
        }
            .stateIn(scope, SharingStarted.Eagerly, null)


    /** 0 = OFF · 1 = Device-only · 2 = Battery-saving · 3 = High-accuracy */
    fun accuracyMode(): Int =
        Settings.Secure.getInt(ctx.contentResolver, Settings.Secure.LOCATION_MODE, 0)


    /** Packages that currently hold the mock-location privilege. */
    fun mockProviders(): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return emptyList()

        val pm     = ctx.packageManager
        val appOps = ctx.getSystemService(AppOpsManager::class.java)

        val out = mutableListOf<String>()
        for (pkg in pm.getInstalledPackages(0)) {
            val appInfo = pkg.applicationInfo ?: continue          // ← null-guard
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_MOCK_LOCATION,
                appInfo.uid,                                        // ← use non-null appInfo
                pkg.packageName
            )
            if (mode == AppOpsManager.MODE_ALLOWED) out += pkg.packageName
        }
        return out
    }

    /* ---------- helpers ---------- */

    @SuppressLint("MissingPermission")
    private fun Location.toSnapshot(): LocationSnapshot =
        LocationSnapshot(
            lat  = latitude,
            lon  = longitude,
            alt  = takeIf { hasAltitude() }?.altitude,
            acc  = takeIf { hasAccuracy() }?.accuracy,
            bearing = takeIf { hasBearing() }?.bearing,
            speed   = takeIf { hasSpeed() }?.speed,
            satsInView = extras?.getInt("satellites"),
            satsUsed   = null,                 // updated by GnssStatus on API 24+
            provider   = provider,
            isMock     = if (Build.VERSION.SDK_INT >= 31) isMock else isFromMockProvider,
            timestamp  = Instant.fromEpochMilliseconds(time)
        )
}