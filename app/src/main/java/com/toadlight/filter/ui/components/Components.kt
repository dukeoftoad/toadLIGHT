package com.toadlight.filter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toadlight.filter.core.color.ColorTemperature
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Time helpers shared by all screens. */
object TimeText {
    private val fmt = DateTimeFormatter.ofPattern("h:mm a")

    fun format(millis: Long?): String? = millis?.let {
        fmt.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
    }

    fun formatMinutes(minutesOfDay: Int): String {
        val m = ((minutesOfDay % (24 * 60)) + 24 * 60) % (24 * 60)
        val h = m / 60
        val min = m % 60
        val ampm = if (h < 12) "AM" else "PM"
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return String.format("%d:%02d %s", h12, min, ampm)
    }

    fun greeting(): String {
        val hour = Instant.now().atZone(ZoneId.systemDefault()).hour
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
    }
}

/** A rounded elevated card with an optional small caps section title. */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

/** One settings row: icon + title/subtitle on the left, trailing content. */
@Composable
fun SettingsRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** Label + optional leading swatch + slider + value text. */
@Composable
fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    steps: Int = 0,
    swatchColor: Color? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (swatchColor != null) {
                Box(
                    Modifier
                        .size(18.dp)
                        .background(swatchColor, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

/** A row that shows a picked time of day and opens the picker on tap. */
@Composable
fun TimeField(label: String, minutes: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            TimeText.formatMinutes(minutes) ?: "",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Material3 24-hour time picker inside a dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = (initialMinutes / 60).coerceIn(0, 23),
        initialMinute = (initialMinutes % 60).coerceIn(0, 59),
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Pick a time") },
        text = { TimePicker(state = state) },
    )
}

/** Small tint dot used in profile cards. */
@Composable
fun TintDot(colorTempK: Int, intensityPercent: Int, dimPercent: Int, size: Int = 40) {
    val rgb = ColorTemperature.rgb(colorTempK)
    val base = Color(rgb[0], rgb[1], rgb[2])
    val alpha = (intensityPercent / 100f).coerceIn(0.1f, 1f)
    Box(
        Modifier
            .size(size.dp)
            .background(
                Color(
                    red = ((1 - alpha) * 0.08f + alpha * rgb[0] / 255f),
                    green = ((1 - alpha) * 0.08f + alpha * rgb[1] / 255f),
                    blue = ((1 - alpha) * 0.08f + alpha * rgb[2] / 255f),
                ),
                CircleShape,
            ),
    )
}

/** Thin spacer for consistent vertical rhythm inside cards. */
@Composable
fun Gap(height: Int = 8) {
    Spacer(Modifier.height(height.dp))
}

/** Row of helper text used under controls. */
@Composable
fun HelperText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Simple chip-like status pill. */
@Composable
fun StatusPill(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.16f), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun RowScopeGap() {
    Spacer(Modifier.width(8.dp))
}
