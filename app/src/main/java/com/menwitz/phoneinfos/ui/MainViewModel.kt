package com.menwitz.phoneinfos.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.menwitz.phoneinfos.core.apps.PackageRepository
import com.menwitz.phoneinfos.core.battery.BatteryRepository
import com.menwitz.phoneinfos.core.fingerprint.assemble
import com.menwitz.phoneinfos.core.location.LocationRepository
import com.menwitz.phoneinfos.core.media.CodecRepository
import com.menwitz.phoneinfos.core.model.*
import com.menwitz.phoneinfos.core.network.NetworkRepository
import com.menwitz.phoneinfos.core.sensor.SensorRepository
import com.menwitz.phoneinfos.core.util.FrequencyTableLoader
import com.menwitz.phoneinfos.core.util.digest
import kotlinx.coroutines.flow.*

/* ───────────────────────────────────────────────────────────── */

class MainViewModel(app: Application) : AndroidViewModel(app) {

    /* repositories ---------------------------------------------------- */
    private val locRepo  = LocationRepository(app, viewModelScope)
    private val netRepo  = NetworkRepository(app, viewModelScope)
    private val battRepo = BatteryRepository(app, viewModelScope)
    private val senRepo  = SensorRepository(app)
    private val codRepo  = CodecRepository()
    private val pkgRepo  = PackageRepository(app, viewModelScope)

    /* entropy frequency table ---------------------------------------- */
    private val freqTable by lazy { FrequencyTableLoader.load(app) }

    /* ─── stage-1 : 4-way combine (location/net/public/battery) ────── */
    private val quadFlow = combine(
        locRepo.locationFlow,
        netRepo.networkFlow,
        netRepo.publicIpFlow,
        battRepo.flow
    ) { loc, net, pub, batt -> Quad4(loc, net, pub, batt) }

    /* ─── stage-2 : add sensors, codecs, installed apps ────────────── */
    private val rawStateFlow = combine(
        quadFlow,
        senRepo.flow,
        codRepo.flow,
        pkgRepo.flow
    ) { q, sensors, codecs, apps ->
        UiState(
            location   = q.loc,
            network    = q.net,
            publicNet  = q.pub,
            battery    = q.batt,
            sensors    = sensors,
            codecs     = codecs,
            apps       = apps,
            appsDigest = apps.digest(),
            accuracy   = locRepo.accuracyMode(),
            mockPkgs   = locRepo.mockProviders()
        )
    }

    /* ─── stage-3 : attach fingerprint + entropy --------------------- */
    val uiState: StateFlow<UiState> = rawStateFlow
        .map { base ->
            val fp = assemble(base, freqTable)
            base.copy(
                fingerprint     = fp.sha256,
                fingerprintJson = fp.json,
                entropyBits     = fp.entropyBits,
                uniqueness      = fp.uniqueness
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    /* optional hook for pull-to-refresh (currently no-op) */
    fun manualRefresh() {}
}

/* helpers ------------------------------------------------------------ */

private data class Quad4<L, N, P, B>(
    val loc: L, val net: N, val pub: P, val batt: B
)

/** unified UI state consumed by Compose */
data class UiState(
    val location:  LocationSnapshot? = null,
    val network:   NetworkSnapshot   = NetworkSnapshot(),
    val publicNet: PublicNetSnapshot? = null,
    val battery:   BatterySnapshot   = BatterySnapshot(0,"Unknown","Unknown",0f,0),

    val sensors:   List<SensorInfo>  = emptyList(),
    val codecs:    List<CodecInfo>   = emptyList(),
    val apps:      List<AppInfo>     = emptyList(),
    val appsDigest:String            = "",

    /* fingerprint & entropy */
    val fingerprint:     String = "",
    val fingerprintJson: String = "",
    val entropyBits:     Double = 0.0,
    val uniqueness:      Long   = 0L,

    /* misc */
    val accuracy: Int = 0,
    val mockPkgs: List<String> = emptyList()
)