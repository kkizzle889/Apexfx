package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ApexDarkColorScheme = darkColorScheme(
    primary = EmeraldBull,
    onPrimary = ObsidianBg,
    primaryContainer = EmeraldBullDim,
    onPrimaryContainer = EmeraldBull,
    secondary = CyanPulse,
    onSecondary = ObsidianBg,
    secondaryContainer = CyanPulseDim,
    onSecondaryContainer = CyanPulse,
    tertiary = VioletAi,
    onTertiary = ObsidianBg,
    tertiaryContainer = VioletAiDim,
    onTertiaryContainer = VioletAi,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = TerminalSurface,
    onSurface = TextPrimary,
    surfaceVariant = TerminalSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = HairlineBorder,
    outlineVariant = TerminalSurfaceHighlight,
    error = CrimsonBear,
    onError = ObsidianBg,
    errorContainer = CrimsonBearDim,
    onErrorContainer = CrimsonBear
)

private val ApexShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun ApexFxTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ApexDarkColorScheme,
        typography = Typography,
        shapes = ApexShapes,
        content = content
    )
}
