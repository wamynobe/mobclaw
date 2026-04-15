package com.wamynobe.mobclaw.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * MobClaw design tokens — extracted from Stitch design system.
 * "The Autonomous Kineticist" — deep tech blue canvas with vibrant 'Crab' orange.
 */
object MobClawColors {
    // Core surfaces
    val Background = Color(0xFF11131C)
    val Surface = Color(0xFF11131C)
    val SurfaceDim = Color(0xFF11131C)
    val SurfaceBright = Color(0xFF373943)
    val SurfaceContainer = Color(0xFF1D1F29)
    val SurfaceContainerLow = Color(0xFF191B24)
    val SurfaceContainerLowest = Color(0xFF0C0E17)
    val SurfaceContainerHigh = Color(0xFF282933)
    val SurfaceContainerHighest = Color(0xFF32343E)
    val SurfaceVariant = Color(0xFF32343E)

    // Primary — 'Crab' orange
    val Primary = Color(0xFFFFB59E)
    val PrimaryContainer = Color(0xFFFF5719)
    val OnPrimary = Color(0xFF5E1700)
    val OnPrimaryContainer = Color(0xFF521300)
    val InversePrimary = Color(0xFFAD3200)
    val BrandOrange = Color(0xFFFF4E00)

    // Secondary — cool slate
    val Secondary = Color(0xFFB8C9D3)
    val SecondaryContainer = Color(0xFF3B4B53)
    val OnSecondary = Color(0xFF23333A)
    val OnSecondaryContainer = Color(0xFFAABBC5)

    // Tertiary — tech blue
    val Tertiary = Color(0xFFA3C9FF)
    val TertiaryContainer = Color(0xFF3B93F1)
    val OnTertiary = Color(0xFF00315C)
    val OnTertiaryContainer = Color(0xFF002A51)

    // Error
    val Error = Color(0xFFFFB4AB)
    val ErrorContainer = Color(0xFF93000A)
    val OnError = Color(0xFF690005)
    val OnErrorContainer = Color(0xFFFFDAD6)

    // On-surface
    val OnBackground = Color(0xFFE1E1EF)
    val OnSurface = Color(0xFFE1E1EF)
    val OnSurfaceVariant = Color(0xFFE6BEB2)
    val InverseOnSurface = Color(0xFF2E303A)
    val InverseSurface = Color(0xFFE1E1EF)

    // Outline
    val Outline = Color(0xFFAC897E)
    val OutlineVariant = Color(0xFF5C4037)

    // Gradients
    val GradientStart = Primary
    val GradientEnd = PrimaryContainer

    // Status
    val StatusOptimal = Tertiary
    val StatusWarning = Primary
    val StatusError = Error
    val StatusSuccess = Color(0xFF4CAF50)
}
