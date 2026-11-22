package com.toadlight.filter.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toadlight.filter.ui.components.Gap
import com.toadlight.filter.ui.components.HelperText
import com.toadlight.filter.util.UpdateIntents

/**
 * The update panel proper — every [UpdateState] rendered in the app's existing
 * settings-card style. Ported from toadFILES' AboutScreen update panel. All actions
 * are explicit taps: check, download & install, open GitHub, dismiss.
 */
@Composable
internal fun UpdatePanel(
    state: UpdateState,
    onCheck: () -> Unit,
    onDownloadInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateState.Idle -> {
            HelperText(
                "Contacts api.github.com once per app open (silently, in the " +
                    "background) and shows the result here. No account, no telemetry, " +
                    "no periodic polling."
            )
            Gap(10)
            TextButton(onClick = onCheck) { Text("Check for updates") }
        }

        is UpdateState.Checking -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Checking GitHub Releases…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is UpdateState.UpToDate -> {
            Text(
                "You're up to date (${state.currentVersion}).",
                style = MaterialTheme.typography.bodyLarge,
            )
            Gap(10)
            TextButton(onClick = onCheck) { Text("Check again") }
        }

        is UpdateState.Available -> UpdateAvailable(
            available = state,
            onDownloadInstall = onDownloadInstall,
            onDismiss = onDismiss,
        )

        is UpdateState.Error -> {
            Text(
                state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Gap(10)
            TextButton(onClick = onCheck) { Text("Retry") }
        }
    }
}

@Composable
internal fun UpdateAvailable(
    available: UpdateState.Available,
    onDownloadInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var notesExpanded by rememberSaveable { mutableStateOf(false) }
    Text(
        "toadLIGHT ${available.version} is available",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Gap(8)
    InfoRow("New version", available.version)
    InfoRow("Size", formatSize(available.sizeBytes))
    available.notes?.let { notes ->
        Gap(8)
        val lines = notes.split('\n')
        val truncated = !notesExpanded && lines.size > NOTES_PREVIEW_LINES
        Text(
            if (truncated) lines.take(NOTES_PREVIEW_LINES).joinToString("\n") + "\n…" else notes,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (lines.size > NOTES_PREVIEW_LINES) {
            TextButton(onClick = { notesExpanded = !notesExpanded }) {
                Text(if (notesExpanded) "Show less" else "Show full notes")
            }
        }
    }
    if (available.needsInstallPermission) {
        Gap(8)
        Text(
            "Allow \"Install unknown apps\" for toadLIGHT in the Android screen that just " +
                "opened, then tap Install again — the APK is already downloaded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (available.downloadInProgress) {
        Gap(10)
        // Real in-app progress. Determinate when the release's total size is known
        // (GitHub always sends Content-Length), indeterminate otherwise — the bar
        // never lies. The byte counters come from the system DownloadManager's own
        // watch loop; the notification shade keeps mirroring the same job.
        val fraction = available.downloadProgress
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
            Gap(6)
            Text(
                "Downloading… ${(fraction * 100).toInt()}%" +
                    " — also visible in the notification shade.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
            Gap(6)
            Text(
                "Downloading… — also visible in the notification shade.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (available.downloadError != null) {
        Gap(8)
        Text(
            available.downloadError, // smart-cast: immutable same-module val, null-checked above
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Gap(12)
    Row(verticalAlignment = Alignment.CenterVertically) {
        val buttonLabel = when {
            available.downloadedUri != null -> "Install downloaded APK"
            available.downloadError != null -> "Retry download" // the failed attempt kept everything else
            else -> "Download & install"
        }
        Button(onClick = onDownloadInstall, enabled = !available.downloadInProgress) {
            Text(buttonLabel)
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { UpdateIntents.openUrl(context, available.htmlUrl) }) {
            Text("View on GitHub")
        }
    }
    Gap(4)
    TextButton(onClick = onDismiss) { Text("Dismiss") }
}

/** Label on the left, monospace value on the right (matches the app's row rhythm). */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

/** Human-readable APK size; "—" when the payload carried no usable number. */
private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "—"
    bytes < 1024 * 1024 -> "%.0f kB".format(bytes / 1024f)
    else -> "%.1f MB".format(bytes / (1024f * 1024f))
}

private const val NOTES_PREVIEW_LINES = 4
