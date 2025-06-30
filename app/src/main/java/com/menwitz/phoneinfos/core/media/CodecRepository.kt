/* core/media/CodecRepository.kt */
package com.menwitz.phoneinfos.core.media

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import com.menwitz.phoneinfos.core.model.CodecInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CodecRepository {

    private val _flow = MutableStateFlow(readInventory())
    val  flow: StateFlow<List<CodecInfo>> = _flow

    private fun readInventory(): List<CodecInfo> {
        val list = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        return list.map { c ->
            CodecInfo(
                name     = c.name,
                encoder  = c.isEncoder,
                hardware = !c.name.startsWith("c2.android")   // crude heuristic
            )
        }
    }
}