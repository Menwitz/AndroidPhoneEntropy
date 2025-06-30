package com.menwitz.phoneinfos.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.menwitz.phoneinfos.BuildConfig
import com.menwitz.phoneinfos.ui.theme.PhoneInfosTheme
import kotlinx.datetime.toJavaInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.saveable.rememberSaveable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            val state by vm.uiState.collectAsState()

            /* permissions */
            val permLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {}
            LaunchedEffect(Unit) {
                permLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_NETWORK_STATE
                    )
                )
            }

            PhoneInfosTheme(dynamicColor = true) {
                Scaffold(
                    floatingActionButton = {
                        ShareFab(state.fingerprint, state.fingerprintJson)
                    }
                ) { pad ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(pad)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { DeviceCard(state) }
                        item { LocationCard(state) }
                        item { BatteryCard(state) }
                        item { NetworkCard(state) }
                        item { SensorsCard(state) }
                        item { CodecsCard(state) }
                        item { AppsCard(state) }
                        item { FingerprintCard(state) }

                        item {
                            Spacer(Modifier.height(24.dp))
                            Text(
                                "v${BuildConfig.VERSION_NAME} • API ${android.os.Build.VERSION.SDK_INT}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ───────────── FAB ───────────── */
@Composable
private fun ShareFab(sha: String, json: String) {
    val ctx = LocalContext.current
    FloatingActionButton(
        onClick = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Phone fingerprint")
                putExtra(Intent.EXTRA_TEXT, "SHA-256: $sha\n\n$json")
            }
            ctx.startActivity(Intent.createChooser(send, "Share fingerprint"))
        }
    ) { Icon(Icons.Filled.Share, null) }
}

/* ───────────── Cards ───────────── */

@Composable
private fun DeviceCard(st: UiState) = SimpleCard("Device") {
    Labeled("Accuracy mode", accuracyLabel(st.accuracy))
    Labeled("Mock active", st.location?.isMock?.toString() ?: "—")
    if (st.mockPkgs.isNotEmpty()) Labeled("Mock pkgs", st.mockPkgs.joinToString())
}

@Composable
private fun LocationCard(st: UiState) = SimpleCard("Location") {
    st.location?.let { loc ->
        Labeled("Lat / Lon", "${loc.lat}, ${loc.lon}")
        Labeled("Altitude", loc.alt?.let { "$it m" } ?: "—")
        Labeled("Accuracy", loc.acc?.let { "$it m" } ?: "—")
        Labeled("Bearing", loc.bearing?.toString() ?: "—")
        Labeled("Speed", loc.speed?.let { "%.1f m/s".format(it) } ?: "—")
        Labeled("Sats", "${loc.satsUsed ?: "—"} / ${loc.satsInView ?: "—"}")
        val ts = loc.timestamp.toJavaInstant().atZone(ZoneId.systemDefault())
        Labeled("Timestamp", DateTimeFormatter.ISO_LOCAL_TIME.format(ts))
    } ?: Text("Waiting for fix…")
}

@Composable
private fun BatteryCard(st: UiState) = SimpleCard("Battery") {
    val b = st.battery
    Labeled("Level", "${b.levelPct}%")
    Labeled("Status", b.status)
    Labeled("Health", b.health)
    Labeled("Temp", "${b.temperatureC} °C")
    if (b.capacityMah > 0) Labeled("Charge now", "${b.capacityMah} mAh")
}

@Composable
private fun NetworkCard(st: UiState) = SimpleCard("Network") {
    Labeled("Transport", st.network.transport)
    Labeled("Local IPv4", st.network.localIpV4 ?: "—")
    Labeled("Local IPv6", st.network.localIpV6 ?: "—")
    Labeled("SSID / Carrier", st.network.ssid ?: st.network.carrier ?: "—")
    Labeled("Public IP", st.publicNet?.publicIp ?: "offline")
}

@Composable
private fun SensorsCard(st: UiState) =
    ExpandCard("Sensors (${st.sensors.size})", st.sensors) {
        Labeled(it.name, "${it.vendor} • ±${it.maxRange}")
    }

@Composable
private fun CodecsCard(st: UiState) =
    ExpandCard("Media Codecs (${st.codecs.size})", st.codecs) {
        val tag = (if (it.encoder) "enc" else "dec") + if (it.hardware) " • HW" else " • SW"
        Labeled(it.name.substringAfterLast('.'), tag)
    }

@Composable
private fun AppsCard(st: UiState) =
    ExpandCard("Installed Apps (${st.apps.size})", st.apps) {
        Labeled(it.packageName, "v${it.versionName ?: it.versionCode}")
    }

@Composable
private fun FingerprintCard(st: UiState) = SimpleCard("Fingerprint") {
    Labeled("SHA-256", st.fingerprint.take(16) + "…")
    Row(verticalAlignment = Alignment.CenterVertically) {
        val colour = when {
            st.entropyBits < 20 -> Color(0xFF2E7D32)
            st.entropyBits < 25 -> Color(0xFFFFA000)
            else                -> Color(0xFFD32F2F)
        }
        AssistChip(
            onClick = {},
            label = { Text("%.1f bits".format(st.entropyBits)) },
            colors = AssistChipDefaults.assistChipColors(containerColor = colour)
        )
        Spacer(Modifier.width(8.dp))
        Text("≈ 1 in ${st.uniqueness}")
    }
}

/* ───────────── reusable helpers ───────────── */

@Composable
private fun SimpleCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card {
        Column(Modifier.padding(12.dp), Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun <T> ExpandCard(
    header: String,
    list: List<T>,
    row: @Composable (T) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(onClick = { expanded = !expanded }) {
        Column(Modifier.padding(12.dp), Arrangement.spacedBy(6.dp)) {
            Text(header, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            val shown = if (expanded) list else list.take(6)
            shown.forEach { row(it) }
            if (list.size > 6) {
                Text(
                    if (expanded) "Tap to collapse"
                    else "…${list.size - 6} more (tap)",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            AnimatedVisibility(
                expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) { Spacer(Modifier.height(1.dp)) }
        }
    }
}

@Composable
private fun Labeled(label: String, value: String) =
    Row { Text("$label: ", Modifier.weight(0.45f)); Text(value, Modifier.weight(0.55f)) }

private fun accuracyLabel(mode: Int) = when (mode) {
    0 -> "Off"
    1 -> "Device-only"
    2 -> "Battery-saving"
    3 -> "High-accuracy"
    else -> "Unknown"
}