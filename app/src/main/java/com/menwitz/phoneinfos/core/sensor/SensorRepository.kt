/* core/sensor/SensorRepository.kt */
package com.menwitz.phoneinfos.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import com.menwitz.phoneinfos.core.model.SensorInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SensorRepository(ctx: Context) {
    private val sm = ctx.getSystemService(SensorManager::class.java)

    private val _flow = MutableStateFlow(readInventory())
    val  flow: StateFlow<List<SensorInfo>> = _flow

    private fun readInventory(): List<SensorInfo> =
        sm.getSensorList(Sensor.TYPE_ALL).map { s ->
            SensorInfo(
                name       = s.name,
                vendor     = s.vendor,
                type       = s.type,
                maxRange   = s.maximumRange,
                resolution = s.resolution,
                powerMah   = s.power
            )
        }
}