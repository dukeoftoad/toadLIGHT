package com.toadlight.filter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// "Dusk amber" dark scheme: warm amber accent on a deep charcoal night base.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA3E635),
    onPrimary = Color(0xFF14210A),
    primaryContainer = Color(0xFF2E5312),
    onPrimaryContainer = Color(0xFFD9F99D),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF062A20),
    secondaryContainer = Color(0xFF18362A),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFF2DD4BF),
    onTertiary = Color(0xFF003B35),
    tertiaryContainer = Color(0xFF11443D),
    onTertiaryContainer = Color(0xFFC9F2E7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B100F),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF131A18),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF1A2320),
    onSurfaceVariant = Color(0xFFB8C7C0),
    outline = Color(0xFF53645B),
    outlineVariant = Color(0xFF334139),
    inverseSurface = Color(0xFFF3F4F6),
    inverseOnSurface = Color(0xFF0B100F),
    inversePrimary = Color(0xFF446E17),
    surfaceDim = Color(0xFF0B100F),
    surfaceBright = Color(0xFF212D29),
    surfaceContainerLowest = Color(0xFF070B0A),
    surfaceContainerLow = Color(0xFF0F1614),
    surfaceContainer = Color(0xFF131A18),
    surfaceContainerHigh = Color(0xFF1A2320),
    surfaceContainerHighest = Color(0xFF212D29),
)

// Toadmark paper palette: warm cream surfaces with moss and pond accents.
private val LightColors = lightColorScheme(
    primary = Color(0xFF446E17),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3EFB4),
    onPrimaryContainer = Color(0xFF102000),
    secondary = Color(0xFF0F766E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC9F2E7),
    onSecondaryContainer = Color(0xFF003B35),
    tertiary = Color(0xFF8F5C00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE8B0),
    onTertiaryContainer = Color(0xFF2D1800),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6F1E0),
    onBackground = Color(0xFF2B2B20),
    surface = Color(0xFFFEFAEC),
    onSurface = Color(0xFF2B2B20),
    surfaceVariant = Color(0xFFE9E2C0),
    onSurfaceVariant = Color(0xFF615E4F),
    outline = Color(0xFFA9A38B),
    outlineVariant = Color(0xFFD8D1B8),
    inverseSurface = Color(0xFF2B2B20),
    inverseOnSurface = Color(0xFFF6F1E0),
    inversePrimary = Color(0xFFA3E635),
    surfaceDim = Color(0xFFE4DDC8),
    surfaceBright = Color(0xFFFEFAEC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF5E5),
    surfaceContainer = Color(0xFFF6F1E0),
    surfaceContainerHigh = Color(0xFFECE5CC),
    surfaceContainerHighest = Color(0xFFE3DCC2),
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
        dynamicColor -> {
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
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content,
    )
}
