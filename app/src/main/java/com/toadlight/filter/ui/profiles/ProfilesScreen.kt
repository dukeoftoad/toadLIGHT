package com.toadlight.filter.ui.profiles

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.toadlight.filter.appGraph
import com.toadlight.filter.core.AppGraph
import com.toadlight.filter.core.color.ColorTemperature
import com.toadlight.filter.core.model.Profile
import com.toadlight.filter.core.model.ScheduleMode
import com.toadlight.filter.ui.components.Gap
import com.toadlight.filter.ui.components.LabeledSlider
import com.toadlight.filter.ui.components.TimeField
import com.toadlight.filter.ui.components.TimePickerDialog
import com.toadlight.filter.ui.components.TimeText
import com.toadlight.filter.ui.components.TintDot
import kotlinx.coroutines.launch
import java.util.UUID

class ProfilesViewModel(private val graph: AppGraph) : ViewModel() {

    val profiles = graph.profilesState
    val activeId = graph.activeProfileIdState

    fun setActive(id: String) {
        graph.scope.launch { graph.repo.setActiveProfile(id) }
    }

    fun save(profile: Profile) {
        graph.scope.launch {
            val exists = graph.profilesState.value.any { it.id == profile.id }
            if (exists) graph.repo.updateProfile(profile) else graph.repo.addProfile(profile)
            setActive(profile.id)
        }
    }

    fun duplicate(profile: Profile) {
        graph.scope.launch { graph.repo.duplicateProfile(profile) }
    }

    fun delete(id: String) {
        graph.scope.launch { graph.repo.deleteProfile(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen() {
    val context = LocalContext.current
    val graph = context.appGraph
    val vm: ProfilesViewModel = viewModel { ProfilesViewModel(graph) }

    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val activeId by vm.activeId.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(title = { Text("Profiles") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New profile") },
            )
        },
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No profiles yet – create one to get started.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        isActive = profile.id == activeId || (activeId.isBlank() && profiles.firstOrNull() == profile),
                        onActivate = { vm.setActive(profile.id) },
                        onEdit = { editing = profile },
                        onDuplicate = { vm.duplicate(profile) },
                        onDelete = { vm.delete(profile.id) },
                        canDelete = profiles.size > 1,
                    )
                }
            }
        }
    }

    if (creating) {
        ProfileEditorSheet(
            profile = null,
            onDismiss = { creating = false },
            onSave = {
                vm.save(it)
                creating = false
            },
        )
    }
    editing?.let { profile ->
        ProfileEditorSheet(
            profile = profile,
            onDismiss = { editing = null },
            onSave = {
                vm.save(it)
                editing = null
            },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
) {
    val cardColors = if (isActive) {
        CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        )
    } else {
        CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )
    }

    androidx.compose.material3.ElevatedCard(
        colors = cardColors,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onActivate),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TintDot(profile.colorTempK, profile.intensityPercent, profile.dimPercent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isActive) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Active",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    profile.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    scheduleLabel(profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ProfileOverflowMenu(
                onEdit = onEdit,
                onDuplicate = onDuplicate,
                onDelete = onDelete,
                canDelete = canDelete,
            )
        }
    }
}

private fun scheduleLabel(profile: Profile): String = when (profile.schedule.mode) {
    ScheduleMode.SUN -> "Sunset → sunrise"
    ScheduleMode.CUSTOM ->
        "Custom ${TimeText.formatMinutes(profile.schedule.startMinutes)} → ${TimeText.formatMinutes(profile.schedule.endMinutes)}"

    ScheduleMode.ALWAYS -> "Always on"
}

@Composable
private fun ProfileOverflowMenu(
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("Duplicate") },
                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDuplicate()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                enabled = canDelete,
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorSheet(
    profile: Profile?,
    onDismiss: () -> Unit,
    onSave: (Profile) -> Unit,
) {
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var tempK by remember { mutableStateOf(profile?.colorTempK ?: 3200) }
    var intensity by remember { mutableStateOf(profile?.intensityPercent ?: 55) }
    var dim by remember { mutableStateOf(profile?.dimPercent ?: 15) }
    var mode by remember { mutableStateOf(profile?.schedule?.mode ?: ScheduleMode.SUN) }
    var startMinutes by remember { mutableStateOf(profile?.schedule?.startMinutes ?: 21 * 60) }
    var endMinutes by remember { mutableStateOf(profile?.schedule?.endMinutes ?: 6 * 60) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                if (profile == null) "New profile" else "Edit profile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Gap(16)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Gap(16)

            LabeledSlider(
                label = "Color temperature",
                valueText = "${tempK}K · ${ColorTemperature.describe(tempK)}",
                value = tempK.toFloat(),
                onValueChange = { tempK = it.toInt() },
                valueRange = 1000f..6500f,
                swatchColor = run {
                    val c = ColorTemperature.rgb(tempK)
                    androidx.compose.ui.graphics.Color(c[0], c[1], c[2])
                },
            )
            Gap()
            LabeledSlider(
                label = "Warmth intensity",
                valueText = "$intensity%",
                value = intensity.toFloat(),
                onValueChange = { intensity = it.toInt() },
            )
            Gap()
            LabeledSlider(
                label = "Screen dim",
                valueText = "$dim%",
                value = dim.toFloat(),
                onValueChange = { dim = it.toInt() },
            )
            Gap(16)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val modes = listOf(
                    ScheduleMode.SUN to "Sun",
                    ScheduleMode.CUSTOM to "Custom",
                    ScheduleMode.ALWAYS to "Always",
                )
                modes.forEachIndexed { index, (m, label) ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { mode = m },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    ) {
                        Text(label)
                    }
                }
            }
            if (mode == ScheduleMode.CUSTOM) {
                Gap()
                TimeField("Filter starts", startMinutes) { showStartPicker = true }
                TimeField("Filter ends", endMinutes) { showEndPicker = true }
            }

            Gap(24)
            Row(
                Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                androidx.compose.material3.Button(
                    onClick = {
                        val trimmed = name.ifBlank { "Untitled" }
                        onSave(
                            (profile ?: Profile(id = UUID.randomUUID().toString(), name = trimmed)).copy(
                                name = trimmed,
                                colorTempK = tempK,
                                intensityPercent = intensity,
                                dimPercent = dim,
                                schedule = com.toadlight.filter.core.model.Schedule(
                                    mode = mode,
                                    startMinutes = startMinutes,
                                    endMinutes = endMinutes,
                                ),
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
            }
        }
    }

    if (showStartPicker) {
        TimePickerDialog(
            initialMinutes = startMinutes,
            onDismiss = { showStartPicker = false },
            onConfirm = {
                startMinutes = it
                showStartPicker = false
            },
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initialMinutes = endMinutes,
            onDismiss = { showEndPicker = false },
            onConfirm = {
                endMinutes = it
                showEndPicker = false
            },
        )
    }
}
