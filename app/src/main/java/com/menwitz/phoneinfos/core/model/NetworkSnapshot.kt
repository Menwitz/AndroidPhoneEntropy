package com.menwitz.phoneinfos.core.model

data class NetworkSnapshot(
    val localIpV4: String? = null,
    val localIpV6: String? = null,
    val transport: String  = "NONE",   // WIFI | CELLULAR | VPN | OTHER | NONE
    val ssid:      String? = null,
    val carrier:   String? = null
)