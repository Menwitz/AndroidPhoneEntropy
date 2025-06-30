/* core/model/AppInfo.kt */
package com.menwitz.phoneinfos.core.model

data class AppInfo(
    val packageName:  String,
    val versionName:  String?,
    val versionCode:  Long,
    val firstInstall: Long,   // epoch millis
    val lastUpdate:   Long,
    val isSystem:     Boolean,
    val fromPlay:     Boolean,
    val sha256:       String   // signature digest
)