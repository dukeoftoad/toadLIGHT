package com.toadlight.filter.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toadlight.filter.appGraph
import com.toadlight.filter.core.color.ColorTemperature
import com.toadlight.filter.core.model.ScheduleMode
import com.toadlight.filter.ui.components.Gap
import com.toadlight.filter.ui.components.LabeledSlider
import com.toadlight.filter.ui.components.SectionCard
import com.toadlight.filter.ui.components.TimeField
import com.toadlight.filter.ui.components.TimePickerDialog
import com.toadlight.filter.ui.components.TimeText
import com.toadlight.filter.ui.components.TintDot
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val graph = context.appGraph
    val vm: HomeViewModel = viewModel { HomeViewModel(context.applicationContext, graph) }

    val engineState by vm.engineState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val profile by vm.activeProfile.collectAsStateWithLifecycle()
    val locationStatus by vm.locationStatus.collectAsStateWithLifecycle()

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { notifGranted = it }

    val haptics = LocalHapticFeedback.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        // ------------------------------------------------------- header ---
        StatusHeader(
            engineState = engineState,
            masterOn = engineState.masterOn,
            onMasterChange = { on ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.setMaster(on)
            },
        )

        if (!Settings.canDrawOverlays(context)) {
            Gap(12)
            PermissionCard(
                title = "Allow display over other apps",
                body = "The filter needs this permission to warm your screen.",
                actionLabel = "Open settings",
                onAction = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
            )
        }

        if (Build.VERSION.SDK_INT >= 33 && !notifGranted && engineState.masterOn) {
            Gap(12)
            PermissionCard(
                title = "Enable notification controls",
                body = "Pause, resume and stop the filter from your notification shade.",
                actionLabel = "Allow",
                onAction = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }

        if (engineState.paused) {
            Gap(12)
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Paused", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Resumes " + (TimeText.format(engineState.pauseEndsAt) ?: "soon"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AssistChip(onClick = { vm.resume() }, label = { Text("Resume now") })
                }
            }
        }

        Gap(16)

        profile?.let { p ->
            // -------------------------------------------------- preview ---
            FilterPreview(profile = p)
            Gap(16)

            // -------------------------------------------------- sliders ---
            SectionCard(title = "Filter strength") {
                var temp by remember(p.id) { mutableStateOf(p.colorTempK.toFloat()) }
                var intensity by remember(p.id) { mutableStateOf(p.intensityPercent.toFloat()) }
                var dim by remember(p.id) { mutableStateOf(p.dimPercent.toFloat()) }

                LabeledSlider(
                    label = "Color temperature",
                    valueText = "${temp.toInt()}K · ${ColorTemperature.describe(temp.toInt())}",
                    value = temp,
                    onValueChange = {
                        temp = it
                        vm.setTemp(it.toInt())
                    },
                    valueRange = 1000f..6500f,
                    swatchColor = run {
                        val c = ColorTemperature.rgb(temp.toInt())
                        Color(c[0], c[1], c[2])
                    },
                )
                Gap()
                LabeledSlider(
                    label = "Warmth intensity",
                    valueText = "${intensity.toInt()}%",
                    value = intensity,
                    onValueChange = {
                        intensity = it
                        vm.setIntensity(it.toInt())
                    },
                )
                Gap()
                LabeledSlider(
                    label = "Screen dim",
                    valueText = "${dim.toInt()}%",
                    value = dim,
                    onValueChange = {
                        dim = it
                        vm.setDim(it.toInt())
                    },
                )
                Gap()
                Text(
                    "Lower Kelvin = less blue light. Add dimming for dark rooms without losing contrast.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Gap(16)

            // ------------------------------------------------- schedule ---
            SectionCard(title = "Schedule") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val modes = listOf(
                        ScheduleMode.SUN to "Sun",
                        ScheduleMode.CUSTOM to "Custom",
                        ScheduleMode.ALWAYS to "Always",
                    )
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = p.schedule.mode == mode,
                            onClick = { vm.setScheduleMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        ) {
                            Text(label)
                        }
                    }
                }

                Gap(12)

                when (p.schedule.mode) {
                    ScheduleMode.SUN -> {
                        ScheduleRow(
                            label = "Sunset",
                            value = TimeText.format(engineState.sunsetAt) ?: "—",
                        )
                        ScheduleRow(
                            label = "Sunrise",
                            value = TimeText.format(engineState.sunriseAt) ?: "—",
                        )
                        if (engineState.usingFallbackSchedule) {
                            Gap()
                            Text(
                                "No location yet – using 9:00 PM → 6:00 AM. Add your location below or in Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        Gap(12)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                val lat = settings.latitude
                                val lon = settings.longitude
                                Text(
                                    if (lat != null && lon != null) {
                                        "Location: %.2f°, %.2f°".format(lat, lon)
                                    } else {
                                        "Location not set"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                locationStatus?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            }
                            IconButton(onClick = {
                                vm.refreshLocation()
                            }) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = "Refresh location",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    ScheduleMode.CUSTOM -> {
                        TimeField(
                            label = "Filter starts",
                            minutes = p.schedule.startMinutes,
                            onClick = { showStartPicker = true },
                        )
                        TimeField(
                            label = "Filter ends",
                            minutes = p.schedule.endMinutes,
                            onClick = { showEndPicker = true },
                        )
                        if (p.schedule.startMinutes == p.schedule.endMinutes) {
                            Gap()
                            Text(
                                "Start and end are the same – the filter stays off.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    ScheduleMode.ALWAYS -> {
                        Text(
                            "The filter stays on around the clock until you pause or stop it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Gap(16)

            // ------------------------------------------------ quick pause ---
            SectionCard(title = "Quick break") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { vm.pause(60_000L) },
                        label = { Text("1 min") },
                        modifier = Modifier.weight(1f),
                    )
                    AssistChip(
                        onClick = { vm.pause(30 * 60_000L) },
                        label = { Text("30 min") },
                        modifier = Modifier.weight(1f),
                    )
                    AssistChip(
                        onClick = { vm.pause(60 * 60_000L) },
                        label = { Text("1 hour") },
                        modifier = Modifier.weight(1f),
                    )
                }
                Gap()
                Text(
                    "Pausing temporarily lifts the filter; the schedule resumes it automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Gap(24)
    }

    if (showStartPicker && profile != null) {
        TimePickerDialog(
            initialMinutes = profile!!.schedule.startMinutes,
            onDismiss = { showStartPicker = false },
            onConfirm = {
                vm.setCustomTimes(it, profile!!.schedule.endMinutes)
                showStartPicker = false
            },
        )
    }
    if (showEndPicker && profile != null) {
        TimePickerDialog(
            initialMinutes = profile!!.schedule.endMinutes,
            onDismiss = { showEndPicker = false },
            onConfirm = {
                vm.setCustomTimes(profile!!.schedule.startMinutes, it)
                showEndPicker = false
            },
        )
    }
}

@Composable
private fun StatusHeader(
    engineState: com.toadlight.filter.filter.EngineState,
    masterOn: Boolean,
    onMasterChange: (Boolean) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val hour = Instant.now().atZone(zone).hour

    val statusText = when {
        !engineState.masterOn -> "Off"
        engineState.paused -> "Paused · resumes ${TimeText.format(engineState.pauseEndsAt) ?: "soon"}"
        engineState.overlayOn && engineState.scheduleOpen ->
            if (engineState.fading) {
                "Fading in · ${engineState.profileName}"
            } else {
                "Active · until ${TimeText.format(engineState.sunriseAt) ?: "schedule ends"}"
            }

        engineState.scheduleOpen -> "Ready"
        else -> "Starts ${TimeText.format(engineState.sunsetAt) ?: "at sunset"}"
    }

    val brush = Brush.verticalGradient(
        listOf(
            Color(0xFF2A2A4A),
            Color(0xFF4A3A5E),
        ),
    )

    Box(
        Modifier
            .fillMaxWidth()
            .background(brush, MaterialTheme.shapes.extraLarge)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    TimeText.greeting(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFC6C1D8),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    statusText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                if (engineState.perAppSuppressing) {
                    Text(
                        "Paused for this app",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFD9A0),
                    )
                } else if (engineState.masterOn) {
                    Text(
                        "${engineState.profileName} · ${engineState.colorTempK}K · ${engineState.intensityPercent}%" +
                            if (engineState.dimPercent > 0) " · dim ${engineState.dimPercent}%" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC6C1D8),
                    )
                }
            }
            Switch(checked = masterOn, onCheckedChange = onMasterChange)
        }
    }
}

@Composable
private fun FilterPreview(profile: com.toadlight.filter.core.model.Profile) {
    val rgb = ColorTemperature.rgb(profile.colorTempK)
    val tint = Color(rgb[0], rgb[1], rgb[2])
    val tintAlpha = (profile.intensityPercent / 100f).coerceIn(0f, 1f)
    val dimAlpha = (profile.dimPercent / 100f) * 0.95f

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF2A2A4A),
                            Color(0xFF6B3A6E),
                            Color(0xFFB96A4B),
                        ),
                    ),
                    MaterialTheme.shapes.extraLarge,
                ),
        ) {
            Row(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Aa", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                listOf(Color(0xFF4FC3F7), Color(0xFFFFEB3B), Color(0xFFFFFFFF)).forEach { c ->
                    Box(Modifier.size(30.dp).background(c, CircleShape))
                }
            }
            Box(
                Modifier
                    .matchParentSize()
                    .background(tint.copy(alpha = tintAlpha)),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = dimAlpha)),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TintDot(profile.colorTempK, profile.intensityPercent, profile.dimPercent, size = 14)
            Spacer(Modifier.width(8.dp))
            Text(
                "Live preview · ${profile.summary}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScheduleRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.WbSunny,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            androidx.compose.material3.OutlinedButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
