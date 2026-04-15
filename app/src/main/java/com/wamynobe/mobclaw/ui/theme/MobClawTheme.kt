package com.wamynobe.mobclaw.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MobClawColorScheme = darkColorScheme(
    primary = MobClawColors.Primary,
    onPrimary = MobClawColors.OnPrimary,
    primaryContainer = MobClawColors.PrimaryContainer,
    onPrimaryContainer = MobClawColors.OnPrimaryContainer,
    inversePrimary = MobClawColors.InversePrimary,
    secondary = MobClawColors.Secondary,
    onSecondary = MobClawColors.OnSecondary,
    secondaryContainer = MobClawColors.SecondaryContainer,
    onSecondaryContainer = MobClawColors.OnSecondaryContainer,
    tertiary = MobClawColors.Tertiary,
    onTertiary = MobClawColors.OnTertiary,
    tertiaryContainer = MobClawColors.TertiaryContainer,
    onTertiaryContainer = MobClawColors.OnTertiaryContainer,
    error = MobClawColors.Error,
    onError = MobClawColors.OnError,
    errorContainer = MobClawColors.ErrorContainer,
    onErrorContainer = MobClawColors.OnErrorContainer,
    background = MobClawColors.Background,
    onBackground = MobClawColors.OnBackground,
    surface = MobClawColors.Surface,
    onSurface = MobClawColors.OnSurface,
    surfaceVariant = MobClawColors.SurfaceVariant,
    onSurfaceVariant = MobClawColors.OnSurfaceVariant,
    surfaceTint = MobClawColors.Primary,
    inverseSurface = MobClawColors.InverseSurface,
    inverseOnSurface = MobClawColors.InverseOnSurface,
    outline = MobClawColors.Outline,
    outlineVariant = MobClawColors.OutlineVariant,
    surfaceBright = MobClawColors.SurfaceBright,
    surfaceDim = MobClawColors.SurfaceDim,
    surfaceContainer = MobClawColors.SurfaceContainer,
    surfaceContainerHigh = MobClawColors.SurfaceContainerHigh,
    surfaceContainerHighest = MobClawColors.SurfaceContainerHighest,
    surfaceContainerLow = MobClawColors.SurfaceContainerLow,
    surfaceContainerLowest = MobClawColors.SurfaceContainerLowest,
)

private val MobClawShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun MobClawTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MobClawColorScheme,
        typography = MobClawTypography,
        shapes = MobClawShapes,
        content = content,
    )
}
