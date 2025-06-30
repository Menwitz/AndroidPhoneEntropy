/* core/model/SensorInventory.kt */
package com.menwitz.phoneinfos.core.model

data class SensorInfo(
    val name:        String,
    val vendor:      String,
    val type:        Int,
    val maxRange:    Float,
    val resolution:  Float,
    val powerMah:    Float
)

data class CodecInfo(
    val name:     String,   // "c2.android.avc.decoder"
    val encoder:  Boolean,
    val hardware: Boolean
)

data class InventorySnapshot(
    val sensors: List<SensorInfo>,
    val codecs:  List<CodecInfo>
)