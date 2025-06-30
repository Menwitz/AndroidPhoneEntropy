package com.menwitz.phoneinfos.core.model

import kotlinx.datetime.Instant

data class LocationSnapshot(
    val lat:  Double?,
    val lon:  Double?,
    val alt:  Double?,
    val acc:  Float?,           // horizontal accuracy (m)
    val bearing: Float?,
    val speed:  Float?,         // m/s
    val satsInView: Int?,
    val satsUsed:   Int?,
    val provider:  String?,
    val isMock:    Boolean,
    val timestamp: Instant
)