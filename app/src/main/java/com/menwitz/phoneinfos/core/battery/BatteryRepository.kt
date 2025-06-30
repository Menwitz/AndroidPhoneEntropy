// core/battery/BatteryRepository.kt
package com.menwitz.phoneinfos.core.battery

import android.content.*
import android.os.BatteryManager
import com.menwitz.phoneinfos.core.model.BatterySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

class BatteryRepository(
    ctx: Context,
    scope: CoroutineScope
) {
    private val appCtx = ctx.applicationContext
    private val bm     = appCtx.getSystemService(BatteryManager::class.java)

    val flow: StateFlow<BatterySnapshot> = callbackFlow {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

        val recv = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { trySend(i.toSnapshot()) }
        }
        appCtx.registerReceiver(recv, filter, Context.RECEIVER_NOT_EXPORTED)
        // fire once immediately
        appCtx.registerReceiver(null, filter)?.let { trySend(it.toSnapshot()) }

        awaitClose { appCtx.unregisterReceiver(recv) }
    }.stateIn(scope, SharingStarted.Eagerly, BatterySnapshot(0, "Unknown", "Unknown", 0f, 0))

    /* ---------- helpers ---------- */
    private fun Intent.toSnapshot(): BatterySnapshot {
        val level   = getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        val scale   = getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct     = (level * 100f / scale).toInt()

        return BatterySnapshot(
            levelPct     = pct,
            status       = mapStatus(getIntExtra(BatteryManager.EXTRA_STATUS, -1)),
            health       = mapHealth(getIntExtra(BatteryManager.EXTRA_HEALTH, -1)),
            temperatureC = getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
            capacityMah  = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        )
    }
    private fun mapStatus(s: Int) = when (s) {
        BatteryManager.BATTERY_STATUS_CHARGING    -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_FULL        -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING-> "Idle"
        else                                      -> "Unknown"
    }
    private fun mapHealth(h: Int) = when (h) {
        BatteryManager.BATTERY_HEALTH_GOOD        -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT    -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD        -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE-> "Over-volt"
        else                                      -> "Unknown"
    }
}