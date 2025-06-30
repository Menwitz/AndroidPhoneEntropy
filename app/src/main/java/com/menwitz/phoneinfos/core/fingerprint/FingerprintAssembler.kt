package com.menwitz.phoneinfos.core.fingerprint

import android.os.Build
import com.menwitz.phoneinfos.ui.UiState
import com.menwitz.phoneinfos.core.model.*
import kotlinx.serialization.json.*
import java.security.MessageDigest
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong
import com.menwitz.phoneinfos.core.util.displayLabel
import com.menwitz.phoneinfos.core.util.localeLabel

/** Canonical fingerprint JSON + SHA-256 digest + entropy estimate. */
data class FingerprintBundle(
    val json:        String,
    val sha256:      String,
    val entropyBits: Double,
    val uniqueness:  Long          // 2^entropyBits rounded
)

/** Build canonical JSON, hash it, compute entropy on the fly. */
fun assemble(
    ui: UiState,
    freq: Map<String, Map<String, Double>>
): FingerprintBundle {
    /* ---------- canonical object ---------- */
    val obj = buildJsonObject {
        put("brand", Build.BRAND.lowercase())
        put("model", Build.MODEL.lowercase())
        put("display", ui.displayLabel)              // add this helper later
        put("appsDigest", ui.appsDigest)
        put("sensorSet", ui.sensors.joinToString("|") { it.type.toString() })
        put("codecHw", ui.codecs.filter { it.hardware }
            .joinToString("|") { it.name.substringAfterLast('.') })
        put("netTransport", ui.network.transport.lowercase())
        put("batteryCap", ui.battery.capacityMah)
        put("locale", ui.localeLabel)                // derive as you wish
    }
    val jsonText = obj.toString()

    /* ---------- digest ---------- */
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(jsonText.toByteArray())
        .joinToString("") { "%02x".format(it) }

    /* ---------- entropy ---------- */
    val bits = obj.entries.sumOf { (k, v) ->
        freq[k]?.get(v.jsonPrimitive.content)?.let { p -> -p * ln(p) / ln(2.0) } ?: 0.0
    }
    val uniq = 2.0.pow(bits).roundToLong()

    return FingerprintBundle(jsonText, digest, bits, uniq)
}