package com.menwitz.phoneinfos.core.apps

import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.menwitz.phoneinfos.core.model.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.security.MessageDigest

class PackageRepository(
    ctx: Context,
    scope: CoroutineScope
) {
    private val pm  = ctx.packageManager
    private val app = ctx.applicationContext

    /** Emits full list now and whenever an app is added / removed / changed. */
    val flow: StateFlow<List<AppInfo>> = callbackFlow {
        fun push() = trySend(buildSnapshot())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        val br = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { push() }   // Unit return
        }
        app.registerReceiver(br, filter, Context.RECEIVER_NOT_EXPORTED)
        push()

        awaitClose { app.unregisterReceiver(br) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /* ------------- helpers ------------- */
    private fun buildSnapshot(): List<AppInfo> {
        val pkgs = pm.getInstalledPackages(PackageManager.GET_SIGNING_CERTIFICATES)
        val md   = MessageDigest.getInstance("SHA-256")

        return pkgs.map { pkg ->
            /* signature digest (empty when unavailable) */
            val sigBytes: ByteArray = when {
                Build.VERSION.SDK_INT >= 28 ->
                    pkg.signingInfo
                        ?.apkContentsSigners
                        ?.firstOrNull()
                        ?.toByteArray()
                        ?: ByteArray(0)

                else -> @Suppress("DEPRECATION")
                pkg.signatures
                    ?.firstOrNull()
                    ?.toByteArray()
                    ?: ByteArray(0)
            }
            val hex = md.digest(sigBytes).joinToString("") { "%02x".format(it) }

            /* app flags (null-safe for rare edge cases) */
            val ai: ApplicationInfo? = pkg.applicationInfo
            val isSystem  = ai?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0
            val fromPlay  = ai?.sourceDir?.contains("/data/app/") == true &&
                    ai.sourceDir.contains("/base.apk")

            AppInfo(
                packageName = pkg.packageName,
                versionName = pkg.versionName,
                versionCode = if (Build.VERSION.SDK_INT >= 28)
                    pkg.longVersionCode
                else @Suppress("DEPRECATION") pkg.versionCode.toLong(),
                firstInstall = pkg.firstInstallTime,
                lastUpdate   = pkg.lastUpdateTime,
                isSystem     = isSystem,
                fromPlay     = fromPlay,
                sha256       = hex
            )
        }.sortedBy { it.packageName }
    }
}