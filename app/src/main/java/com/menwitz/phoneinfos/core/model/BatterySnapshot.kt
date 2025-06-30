// core/model/BatterySnapshot.kt
package com.menwitz.phoneinfos.core.model

data class BatterySnapshot(
    val levelPct:      Int,      // 0-100
    val status:        String,   // Charging | Discharging | Full | NotPresent | Unknown
    val health:        String,   // Good | Overheat | Dead | …
    val temperatureC:  Float,    // °C
    val capacityMah:   Int
)