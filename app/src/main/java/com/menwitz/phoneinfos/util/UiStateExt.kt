package com.menwitz.phoneinfos.core.util

import com.menwitz.phoneinfos.ui.UiState
import java.util.Locale

val UiState.displayLabel: String
    get() = "${network.transport.lowercase()}@${network.localIpV4 ?: "?"}"

val UiState.localeLabel: String
    get() = Locale.getDefault().toLanguageTag()