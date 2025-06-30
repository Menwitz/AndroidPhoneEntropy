package com.menwitz.phoneinfos.core.util

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Loads and caches the compressed frequency-table shipped in assets. */
object FrequencyTableLoader {
    private var cached: Map<String, Map<String, Double>>? = null

    fun load(context: Context): Map<String, Map<String, Double>> {
        cached?.let { return it }
        val raw = context.assets.open("freq_table.json").bufferedReader().use { it.readText() }
        val json = Json.parseToJsonElement(raw) as JsonObject
        val out  = json.mapValues { (_, v) ->
            (v as JsonObject).mapValues { it.value.toString().toDouble() }
        }
        cached = out
        return out
    }
}