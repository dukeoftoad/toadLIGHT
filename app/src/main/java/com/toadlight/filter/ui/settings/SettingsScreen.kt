package com.toadlight.filter.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toadlight.filter.appGraph
import com.toadlight.filter.core.AppGraph
import com.toadlight.filter.core.model.AppSettings
import com.toadlight.filter.core.model.ThemeMode
import com.toadlight.filter.core.util.LocationGrabber
import com.toadlight.filter.ui.components.Gap
import com.toadlight.filter.ui.components.LabeledSlider
import com.toadlight.filter.ui.components.SectionCard
import com.toadlight.filter.ui.components.SettingsRow
import kotlinx.coroutines.launch

class SettingsViewModel(private val graph: AppGraph) : ViewModel() {

    val settings = graph.settingsState

    fun update(transform: (AppSettings) -> AppSettings) {
        graph.scope.launch { graph.repo.updateSettings(transform) }
    }

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    fun setDynamicColors(on: Boolean) = update { it.copy(dynamicColors = on) }
    fun setStartOnBoot(on: Boolean) = update { it.copy(startOnBoot = on) }
    fun setTransition(minutes: Int) = update { it.copy(transitionMinutes = minutes.coerceIn(0, 120)) }
    fun setSunsetOffset(minutes: Int) = update { it.copy(sunsetOffsetMinutes = minutes.coerceIn(-120, 120)) }
    fun setSunriseOffset(minutes: Int) = update { it.copy(sunriseOffsetMinutes = minutes.coerceIn(-120, 120)) }
    fun setCivilTwilight(on: Boolean) = update { it.copy(civilTwilight = on) }
    fun setFilterNavbar(on: Boolean) = update { it.copy(filterNavbar = on) }
    fun setFilterLandscape(on: Boolean) = update { it.copy(filterLandscape = on) }
    fun setManualLocation(lat: Double?, lon: Double?) {
        graph.scope.launch { graph.repo.setLocation(lat, lon) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val graph = context.appGraph
    val vm: SettingsViewModel = viewModel { SettingsViewModel(graph) }

    val settings by vm.settings.collectAsStateWithLifecycle()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showManualLocation by remember { mutableStateOf(false) }
    var showHowItWorks by remember { mutableStateOf(false) }
    var locationStatus by remember { mutableStateOf<String?>(null) }

    val pm = context.getSystemService(PowerManager::class.java)
    val ignoringBattery = remember {
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Gap(8)

            // ---------------------------------------------------- appearance --
            SectionCard(title = "Appearance") {
                SettingsRow(
                    icon = Icons.Rounded.Palette,
                    title = "Theme",
                    subtitle = themeLabel(settings.themeMode),
                    onClick = { showThemeDialog = true },
                    trailing = {
                        Text(
                            "›",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                if (Build.VERSION.SDK_INT >= 31) {
                    SettingsRow(
                        icon = Icons.Rounded.DarkMode,
                        title = "Dynamic colors",
                        subtitle = "Follow your wallpaper palette (Android 12+)",
                        trailing = {
                            Switch(
                                checked = settings.dynamicColors,
                                onCheckedChange = { vm.setDynamicColors(it) },
                            )
                        },
                    )
                }
            }

            Gap(16)

            // ----------------------------------------------------- schedule --
            SectionCard(title = "Sun & schedule") {
                LabeledSlider(
                    label = "Fade duration",
                    valueText = if (settings.transitionMinutes == 0) {
                        "Instant"
                    } else {
                        "${settings.transitionMinutes} min"
                    },
                    value = settings.transitionMinutes.toFloat(),
                    onValueChange = { vm.setTransition(it.toInt()) },
                    valueRange = 0f..120f,
                    steps = 119,
                )
                Gap(4)
                LabeledSlider(
                    label = "Sunset offset",
                    valueText = offsetLabel(settings.sunsetOffsetMinutes),
                    value = settings.sunsetOffsetMinutes.toFloat(),
                    onValueChange = { vm.setSunsetOffset(it.toInt()) },
                    valueRange = -120f..120f,
                    steps = 47,
                )
                Gap(4)
                LabeledSlider(
                    label = "Sunrise offset",
                    valueText = offsetLabel(settings.sunriseOffsetMinutes),
                    value = settings.sunriseOffsetMinutes.toFloat(),
                    onValueChange = { vm.setSunriseOffset(it.toInt()) },
                    valueRange = -120f..120f,
                    steps = 47,
                )
                Gap(8)
                SettingsRow(
                    icon = Icons.Rounded.WbSunny,
                    title = "Civil twilight times",
                    subtitle = "Start at civil dusk / end at civil dawn (about 30 min later/earlier than official)",
                    trailing = {
                        Switch(
                            checked = settings.civilTwilight,
                            onCheckedChange = { vm.setCivilTwilight(it) },
                        )
                    },
                )
                Gap(4)
                SettingsRow(
                    icon = Icons.Rounded.Refresh,
                    title = locationTitle(settings),
                    subtitle = locationSubtitle(settings) + locationStatus.orEmpty(),
                    onClick = {
                        locationStatus = " · locating…"
                        graph.scope.launch {
                            val loc = LocationGrabber.currentLocation(context)
                            locationStatus = if (loc != null) {
                                vm.setManualLocation(loc.first, loc.second)
                                " · updated"
                            } else {
                                " · no fix – try manual entry"
                            }
                        }
                    },
                    trailing = {
                        Text(
                            "Update",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                )
                SettingsRow(
                    title = "Enter coordinates manually",
                    subtitle = "Useful without location services",
                    onClick = { showManualLocation = true },
                    trailing = {
                        Text(
                            "Edit",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                )
            }

            Gap(16)

            // ------------------------------------------------------- filter --
            SectionCard(title = "Filter") {
                SettingsRow(
                    icon = Icons.Rounded.Bedtime,
                    title = "Filter navigation bar",
                    subtitle = "Cover the gesture/navbar area too",
                    trailing = {
                        Switch(
                            checked = settings.filterNavbar,
                            onCheckedChange = { vm.setFilterNavbar(it) },
                        )
                    },
                )
                SettingsRow(
                    title = "Filter in landscape",
                    subtitle = "Keep the filter on when the screen rotates",
                    trailing = {
                        Switch(
                            checked = settings.filterLandscape,
                            onCheckedChange = { vm.setFilterLandscape(it) },
                        )
                    },
                )
            }

            Gap(16)

            // -------------------------------------------------- reliability --
            SectionCard(title = "Reliability") {
                SettingsRow(
                    icon = Icons.Rounded.PowerSettingsNew,
                    title = "Start on boot",
                    subtitle = "Resume filtering after the device restarts",
                    trailing = {
                        Switch(
                            checked = settings.startOnBoot,
                            onCheckedChange = { vm.setStartOnBoot(it) },
                        )
                    },
                )
                if (!ignoringBattery) {
                    SettingsRow(
                        title = "Keep running reliably",
                        subtitle = "Exclude toadLIGHT from battery optimization so vendors don't kill it",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }
                        },
                        trailing = {
                            Text(
                                "Fix",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                    )
                } else {
                    SettingsRow(
                        title = "Battery optimization",
                        subtitle = "Already excluded – the filter should run uninterrupted",
                    )
                }
            }

            Gap(16)

            // -------------------------------------------------------- about --
            SectionCard(title = "About") {
                SettingsRow(
                    icon = Icons.Rounded.Thermostat,
                    title = "How toadLIGHT works",
                    subtitle = "The short technical story",
                    onClick = { showHowItWorks = true },
                    trailing = {
                        Text(
                            "›",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                SettingsRow(
                    title = "Version",
                    subtitle = versionName(context),
                )
                SettingsRow(
                    title = "Pricing",
                    subtitle = "Every feature is free, forever – no Pro tier",
                )
            }

            Gap(32)
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    listOf(
                        ThemeMode.FOLLOW_SYSTEM to "Follow system",
                        ThemeMode.DARK to "Dark",
                        ThemeMode.AMOLED to "AMOLED black",
                        ThemeMode.LIGHT to "Light",
                    ).forEach { (mode, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.themeMode == mode,
                                onClick = {
                                    vm.setThemeMode(mode)
                                    showThemeDialog = false
                                },
                            )
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Close") }
            },
        )
    }

    if (showManualLocation) {
        ManualLocationDialog(
            initialLat = settings.latitude,
            initialLon = settings.longitude,
            onDismiss = { showManualLocation = false },
            onSave = { lat, lon ->
                vm.setManualLocation(lat, lon)
                showManualLocation = false
            },
        )
    }

    if (showHowItWorks) {
        AlertDialog(
            onDismissRequest = { showHowItWorks = false },
            title = { Text("How toadLIGHT works") },
            text = {
                Text(
                    "toadLIGHT draws a translucent, touch-transparent window over the whole screen " +
                        "(that's why it needs the \"Display over other apps\" permission). The window paints " +
                        "two layers: a warm color tint and a black dimming layer.\n\n" +
                        "Sun times come from the classic sunrise equation used by NOAA's solar calculators – " +
                        "computed on-device from your coordinates, offline, with no network access.\n\n" +
                        "A small foreground service keeps the filter alive, wakes up for sunset/sunrise, " +
                        "and shows notification controls. The optional accessibility service only reports " +
                        "which app is in the foreground (package name only – never screen content) so " +
                        "per-app behavior can work.\n\n" +
                        "All data stays on your device.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showHowItWorks = false }) { Text("Got it") }
            },
        )
    }
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.FOLLOW_SYSTEM -> "Follow system"
    ThemeMode.DARK -> "Dark"
    ThemeMode.AMOLED -> "AMOLED black"
    ThemeMode.LIGHT -> "Light"
}

private fun offsetLabel(minutes: Int): String = when {
    minutes == 0 -> "0 min"
    minutes > 0 -> "+$minutes min"
    else -> "$minutes min"
}

private fun locationTitle(settings: AppSettings): String =
    if (settings.latitude != null && settings.longitude != null) "Sun location" else "Set sun location"

private fun locationSubtitle(settings: AppSettings): String =
    if (settings.latitude != null && settings.longitude != null) {
        "%.2f°, %.2f°".format(settings.latitude, settings.longitude)
    } else {
        "Tap to detect, or enter coordinates manually"
    }

private fun versionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
}.getOrDefault("?")

@Composable
private fun ManualLocationDialog(
    initialLat: Double?,
    initialLon: Double?,
    onDismiss: () -> Unit,
    onSave: (Double?, Double?) -> Unit,
) {
    var lat by remember { mutableStateOf(initialLat?.toString() ?: "") }
    var lon by remember { mutableStateOf(initialLon?.toString() ?: "") }
    var error by remember { mutableStateOf(false) }

    fun parse(text: String): Double? = text.trim().toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it; error = false },
                    label = { Text("Latitude (-90 … 90)") },
                    isError = error,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = lon,
                    onValueChange = { lon = it; error = false },
                    label = { Text("Longitude (-180 … 180)") },
                    isError = error,
                    singleLine = true,
                )
                if (error) {
                    Text(
                        "Check the values – use decimal degrees like 28.61 and 77.21.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val latVal = parse(lat)
                    val lonVal = parse(lon)
                    if (latVal == null || lonVal == null ||
                        latVal !in -90.0..90.0 || lonVal !in -180.0..180.0
                    ) {
                        error = true
                    } else {
                        onSave(latVal, lonVal)
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
