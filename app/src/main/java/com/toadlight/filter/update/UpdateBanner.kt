package com.toadlight.filter.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toadlight.filter.data.GitHubRelease

/**
 * The subtle, dismissible "update available" card shown once on app open when the
 * silent startup check (ported from toadFILES v1.7.1) found a newer release.
 * Non-intrusive by design: a single quiet line above the app content, no dialog, no
 * sound, nothing downloads until the user acts. "Update" deep-links into the
 * EXISTING flow — it opens Settings, whose update panel arrives already seeded with
 * this release (UpdateViewModel), so the familiar "Download & install" panel is one
 * tap away. Styled like the app's own section cards (elevated, surfaceContainerLow).
 */
@Composable
fun UpdateBanner(
    release: GitHubRelease?,
    onOpenUpdateFlow: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (release == null) return // nothing to say — stays fully invisible
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "toadLIGHT ${release.tagName} available",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Update to the latest release — downloaded only if you ask.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onOpenUpdateFlow) { Text("Update") }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss update banner")
            }
        }
    }
}
