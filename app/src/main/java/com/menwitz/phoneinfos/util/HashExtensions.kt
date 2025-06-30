// core/util/HashExtensions.kt
package com.menwitz.phoneinfos.core.util

import com.menwitz.phoneinfos.core.model.AppInfo
import java.security.MessageDigest

/** Stable SHA-256 over the ordered app-list signature hashes. */
fun List<AppInfo>.digest(): String {
    val md = MessageDigest.getInstance("SHA-256")
    for (app in this) md.update(app.sha256.toByteArray())
    return md.digest().joinToString("") { "%02x".format(it) }
}