package com.menwitz.phoneinfos.core.network

import android.content.Context
import android.net.*
import com.menwitz.phoneinfos.core.model.NetworkSnapshot
import com.menwitz.phoneinfos.core.model.PublicNetSnapshot
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class NetworkRepository(
    ctx: Context,
    scope: CoroutineScope
) {
    /*─────────── local (private-IP) observer ───────────*/

    private val cm = ctx.getSystemService(ConnectivityManager::class.java)

    val networkFlow: StateFlow<NetworkSnapshot> = callbackFlow {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(net: Network, nc: NetworkCapabilities) = push()
            override fun onLinkPropertiesChanged(net: Network, lp: LinkProperties)   = push()
            override fun onLost(net: Network)                                        = push()
            fun push() { trySend(current()) }
        }
        cm.registerDefaultNetworkCallback(cb)
        trySend(current())
        awaitClose { cm.unregisterNetworkCallback(cb) }
    }.stateIn(scope, SharingStarted.Eagerly, NetworkSnapshot())

    /** Snapshot of the *currently active* network, or an empty struct when offline. */
    private fun current(): NetworkSnapshot {
        val active = cm.activeNetwork ?: return NetworkSnapshot()

        val caps = cm.getNetworkCapabilities(active) ?: return NetworkSnapshot()
        val lp   = cm.getLinkProperties(active)     ?: return NetworkSnapshot()

        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)      -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)  -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)       -> "VPN"
            else                                                       -> "OTHER"
        }

        val ipv4 = lp.linkAddresses
            .firstOrNull { it.address is java.net.Inet4Address }
            ?.address?.hostAddress
        val ipv6 = lp.linkAddresses
            .firstOrNull { it.address is java.net.Inet6Address }
            ?.address?.hostAddress
        val ssid = lp.interfaceName?.takeIf { transport == "WIFI" }

        return NetworkSnapshot(
            localIpV4 = ipv4,
            localIpV6 = ipv6,
            transport = transport,
            ssid      = ssid,
            carrier   = null
        )
    }

    /*───────────── public (WAN) IP resolver ─────────────*/

    @Serializable private data class Ipify(val ip: String)

    private val ipEndpoints = listOf(
        "https://api.ipify.org?format=json",     // JSON  v4/v6
        "https://api64.ipify.org?format=json",   // JSON  v6-biased
        "https://ifconfig.me/ip",                // plain text
        "https://icanhazip.com"                  // plain text
    )

    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json() }
        install(HttpTimeout) { requestTimeoutMillis = 5_000 }   // 5-second hard cut-off
    }

    val publicIpFlow: StateFlow<PublicNetSnapshot?> =
        flow {
            while (true) {
                var found: String? = null

                for (url in ipEndpoints) {
                    val attempt = runCatching {
                        if (url.endsWith("json"))
                            client.get(url).body<Ipify>().ip
                        else
                            client.get(url).body<String>().trim()
                    }
                    if (attempt.isSuccess) { found = attempt.getOrNull(); break }
                    /* else fall through to next endpoint */
                }

                emit(found?.let { PublicNetSnapshot(it, null, null) })   // null ⇒ offline
                delay(15.minutes)
            }
        }
            .retryWhen { _, _ -> delay(30.seconds); true }    // self-heal if coroutine crashes
            .stateIn(scope, SharingStarted.Eagerly, null)
}