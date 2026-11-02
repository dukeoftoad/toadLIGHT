package com.toadlight.filter.ui.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.provider.Settings
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toadlight.filter.appGraph
import com.toadlight.filter.core.AppGraph
import com.toadlight.filter.core.color.ColorTemperature
import com.toadlight.filter.core.model.AppOverride
import com.toadlight.filter.core.model.OverrideMode
import com.toadlight.filter.filter.ForegroundAppWatcher
import com.toadlight.filter.ui.components.Gap
import com.toadlight.filter.ui.components.LabeledSlider
import com.toadlight.filter.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

class AppsViewModel(private val appContext: Context, private val graph: AppGraph) : ViewModel() {

    val settings = graph.settingsState

    private val apps = MutableStateFlow<List<AppEntry>>(emptyList())
    private val query = MutableStateFlow("")

    val filteredApps = combine(apps, query) { list, q ->
        if (q.isBlank()) list
        else list.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
    }.stateIn(graph.scope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    val accessibilityEnabled = MutableStateFlow(false)

    val loading = MutableStateFlow(false)

    init {
        loadApps()
        refreshAccessibility()
    }

    fun loadApps() {
        loading.value = true
        graph.scope.launch {
            val entries = withContext(Dispatchers.IO) {
                runCatching {
                    val pm = appContext.packageManager
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    val resolved = pm.queryIntentActivities(intent, 0)
                    resolved
                        .map { resolveInfo ->
                            AppEntry(
                                packageName = resolveInfo.activityInfo.packageName,
                                label = resolveInfo.loadLabel(pm).toString(),
                                icon = loadIcon(resolveInfo.loadIcon(pm)),
                            )
                        }
                        .distinctBy { it.packageName }
                        .sortedBy { it.label.lowercase() }
                }.getOrDefault(emptyList())
            }
            apps.value = entries
            loading.value = false
        }
    }

    fun setQuery(q: String) {
        query.value = q
    }

    fun setPerAppEnabled(on: Boolean) {
        graph.scope.launch {
            graph.repo.updateSettings { it.copy(perAppEnabled = on) }
        }
    }

    fun setOverride(override: AppOverride) {
        graph.scope.launch { graph.repo.setAppOverride(override) }
    }

    fun refreshAccessibility() {
        accessibilityEnabled.value = isAccessibilityEnabled(appContext)
    }

    private fun loadIcon(drawable: Drawable): ImageBitmap? = runCatching {
        val size = 96
        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    }.getOrNull()

    companion object {
        fun isAccessibilityEnabled(context: Context): Boolean {
            val setting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val component = ComponentName(context, ForegroundAppWatcher::class.java)
            return setting.split(':').any {
                it.equals(component.flattenToString(), ignoreCase = true) ||
                    it.equals(component.flattenToShortString(), ignoreCase = true)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val graph = context.appGraph
    val vm: AppsViewModel = viewModel { AppsViewModel(context.applicationContext, graph) }

    val settings by vm.settings.collectAsStateWithLifecycle()
    val apps by vm.filteredApps.collectAsStateWithLifecycle()
    val accessibilityOn by vm.accessibilityEnabled.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()

    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<AppEntry?>(null) }

    // Re-check the accessibility state whenever the user returns from settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshAccessibility()
            }
        })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Per-app behavior") }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Per-app behavior",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Gap(2)
                        Text(
                            "Turn the filter off inside chosen apps, or give an app its own warmth. " +
                                "Uses an accessibility service that only reads app names – never screen content.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.perAppEnabled,
                        onCheckedChange = { vm.setPerAppEnabled(it) },
                    )
                }

                if (settings.perAppEnabled && !accessibilityOn) {
                    Gap(12)
                    Text(
                        "To detect the foreground app, enable the toadLIGHT accessibility service:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Gap(8)
                    Button(onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    }) {
                        Text("Open accessibility settings")
                    }
                }
            }

            Gap(16)

            OutlinedTextField(
                value = search,
                onValueChange = {
                    search = it
                    vm.setQuery(it)
                },
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Gap(8)

            if (loading && apps.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Loading apps…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    val override = settings.appOverrides[app.packageName]
                    AppRow(
                        app = app,
                        overrideLabel = when (override?.mode) {
                            OverrideMode.NO_FILTER -> "No filter"
                            OverrideMode.CUSTOM ->
                                "${override.colorTempK}K · ${override.intensityPercent}%"

                            else -> "Default"
                        },
                        highlight = override?.mode != OverrideMode.DEFAULT,
                        onClick = { editing = app },
                    )
                }
            }
        }
    }

    editing?.let { app ->
        AppOverrideSheet(
            app = app,
            current = settings.appOverrides[app.packageName],
            onDismiss = { editing = null },
            onSave = { override ->
                vm.setOverride(override)
                editing = null
            },
        )
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    overrideLabel: String,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app.icon != null) {
            androidx.compose.foundation.Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        } else {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            overrideLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppOverrideSheet(
    app: AppEntry,
    current: AppOverride?,
    onDismiss: () -> Unit,
    onSave: (AppOverride) -> Unit,
) {
    var mode by remember { mutableStateOf(current?.mode ?: OverrideMode.DEFAULT) }
    var tempK by remember { mutableStateOf(current?.colorTempK ?: 2000) }
    var intensity by remember { mutableStateOf(current?.intensityPercent ?: 60) }
    var dim by remember { mutableStateOf(current?.dimPercent ?: 0) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app.icon != null) {
                    androidx.compose.foundation.Image(
                        bitmap = app.icon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "in this app the filter will…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Gap(16)

            listOf(
                Triple(OverrideMode.DEFAULT, "Follow the schedule", "Use your normal profile."),
                Triple(OverrideMode.NO_FILTER, "Turn off", "No filter while this app is open."),
                Triple(OverrideMode.CUSTOM, "Custom settings", "Give this app its own warmth."),
            ).forEach { (m, title, subtitle) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { mode = m }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == m, onClick = { mode = m })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (mode == OverrideMode.CUSTOM) {
                Gap(8)
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
                LabeledSlider(
                    label = "Warmth intensity",
                    valueText = "$intensity%",
                    value = intensity.toFloat(),
                    onValueChange = { intensity = it.toInt() },
                )
                LabeledSlider(
                    label = "Screen dim",
                    valueText = "$dim%",
                    value = dim.toFloat(),
                    onValueChange = { dim = it.toInt() },
                )
            }

            Gap(24)
            Row(
                Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("Cancel") }
                Button(
                    onClick = {
                        onSave(
                            AppOverride(
                                packageName = app.packageName,
                                mode = mode,
                                colorTempK = tempK,
                                intensityPercent = intensity,
                                dimPercent = dim,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
        }
    }
}
