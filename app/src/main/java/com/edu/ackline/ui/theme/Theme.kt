package com.edu.ackline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Ackline teal/blue-green visual system (Phase 9, visual direction V2).
//
// The theme follows the device system light/dark setting. Material Dynamic
// Color is deliberately NOT used: the Ackline palette stays under app control
// and ColorOS / Material You wallpaper colors cannot override it.
//
// Tonal layer graph (both schemes, visible separation in each mode):
//   surfaceContainerLowest   recessed inset / segmented track
//   background               screen
//   surfaceContainerLow      quiet (viewed) card
//   surfaceContainerHigh     nav/header band
//   surfaceContainer         raised (pending) card
//   surfaceContainerHighest  selected segment fill (teal-tonal)
//   secondaryContainer       primary actionable pill ("Visto")
//   tertiary / tertiaryContainer  Important severity (distinct from primary)
private val AcklineDarkColorScheme = darkColorScheme(
    primary = TealDarkAccent,
    onPrimary = InkOnTealDarkAction,
    primaryContainer = TealDarkSegmentSelected,
    onPrimaryContainer = TealDarkAccentBright,

    secondary = TealDarkAccent,
    onSecondary = InkOnTealDarkAction,
    secondaryContainer = TealDarkAccentBright,
    onSecondaryContainer = InkOnTealDarkAction,

    tertiary = TealDarkBlueGreen,
    onTertiary = InkOnTealDarkAction,
    tertiaryContainer = TealDarkBlueGreenContainer,
    onTertiaryContainer = TealDarkBlueGreenInk,

    error = TealDarkCoral,
    onError = InkOnErrorDark,
    errorContainer = TealDarkCoralContainer,
    onErrorContainer = TealDarkCoralInk,

    background = TealDarkScreen,
    onBackground = TealDarkInkPrimary,

    surface = TealDarkScreen,
    onSurface = TealDarkInkPrimary,
    onSurfaceVariant = TealDarkInkSecondary,

    surfaceContainerLowest = TealDarkInset,
    surfaceContainerLow = TealDarkCardQuiet,
    surfaceContainer = TealDarkCard,
    surfaceContainerHigh = TealDarkHeaderBand,
    surfaceContainerHighest = TealDarkSegmentSelected,
    surfaceBright = TealDarkHeaderBand,

    outline = TealDarkInkTertiary,
    outlineVariant = TealDarkHairline,
)

private val AcklineLightColorScheme = lightColorScheme(
    primary = TealLightAccent,
    onPrimary = InkOnTealLightAction,
    primaryContainer = TealLightAccentContainer,
    onPrimaryContainer = TealLightAccentInk,

    secondary = TealLightAccent,
    onSecondary = InkOnTealLightAction,
    secondaryContainer = TealLightAccent,
    onSecondaryContainer = InkOnTealLightAction,

    tertiary = TealLightBlueGreen,
    onTertiary = InkOnTealLightAction,
    tertiaryContainer = TealLightBlueGreenContainer,
    onTertiaryContainer = TealLightBlueGreenInk,

    error = TealLightCoral,
    onError = InkOnErrorLight,
    errorContainer = TealLightCoralContainer,
    onErrorContainer = TealLightCoralInk,

    background = TealLightScreen,
    onBackground = TealLightInkPrimary,

    surface = TealLightScreen,
    onSurface = TealLightInkPrimary,
    onSurfaceVariant = TealLightInkSecondary,

    surfaceContainerLowest = TealLightInset,
    surfaceContainerLow = TealLightCardQuiet,
    surfaceContainer = TealLightCard,
    surfaceContainerHigh = TealLightHeaderBand,
    surfaceContainerHighest = TealLightSegmentSelected,
    surfaceBright = TealLightCard,

    outline = TealLightInkTertiary,
    outlineVariant = TealLightHairline,
)

@Composable
fun AcklineTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) AcklineDarkColorScheme else AcklineLightColorScheme,
        content = content,
    )
}
