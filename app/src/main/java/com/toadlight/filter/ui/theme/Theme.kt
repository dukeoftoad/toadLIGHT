package com.toadlight.filter.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// "Dusk amber" dark scheme: warm amber accent on a deep charcoal night base.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFC36B),
    onPrimary = Color(0xFF462A00),
    primaryContainer = Color(0xFF664400),
    onPrimaryContainer = Color(0xFFFFE0B3),
    secondary = Color(0xFFC6C1D8),
    onSecondary = Color(0xFF2E2941),
    secondaryContainer = Color(0xFF453F5C),
    onSecondaryContainer = Color(0xFFE3DFF5),
    tertiary = Color(0xFF9BCBD8),
    onTertiary = Color(0xFF10343D),
    tertiaryContainer = Color(0xFF2F4A54),
    onTertiaryContainer = Color(0xFFC5E8F4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF141419),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF141419),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF2A2A31),
    onSurfaceVariant = Color(0xFFCBC6D0),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF46464E),
    inverseSurface = Color(0xFFE5E1E9),
    inverseOnSurface = Color(0xFF302F35),
    inversePrimary = Color(0xFF825500),
    surfaceDim = Color(0xFF141419),
    surfaceBright = Color(0xFF3B3B41),
    surfaceContainerLowest = Color(0xFF0F0F13),
    surfaceContainerLow = Color(0xFF1C1C22),
    surfaceContainer = Color(0xFF202026),
    surfaceContainerHigh = Color(0xFF2A2A31),
    surfaceContainerHighest = Color(0xFF35353C),
)

// Warm "paper by daylight" light scheme.
private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5300),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDB4),
    onPrimaryContainer = Color(0xFF2C1700),
    secondary = Color(0xFF5D5674),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2DDF6),
    onSecondaryContainer = Color(0xFF1A1630),
    tertiary = Color(0xFF3E6470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC1E9F6),
    onTertiaryContainer = Color(0xFF00202A),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFEDE1D0),
    onSurfaceVariant = Color(0xFF4D4539),
    outline = Color(0xFF7F7567),
    outlineVariant = Color(0xFFD0C5B4),
    inverseSurface = Color(0xFF35302A),
    inverseOnSurface = Color(0xFFF9EFF7),
    inversePrimary = Color(0xFFFFC36B),
    surfaceDim = Color(0xFFE2D9D2),
    surfaceBright = Color(0xFFFFF8F4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF1E9),
    surfaceContainer = Color(0xFFF7EBE2),
    surfaceContainerHigh = Color(0xFFF1E5DC),
    surfaceContainerHighest = Color(0xFFEBDFD6),
)

private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0D0D11),
    surfaceContainer = Color(0xFF141419),
    surfaceContainerHigh = Color(0xFF1E1E24),
    surfaceContainerHighest = Color(0xFF28282E),
)

@Composable
fun toadLIGHTTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }.let {
        if (darkTheme && amoled && !dynamicColor) it.amoled() else it
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
