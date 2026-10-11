package com.tmaem.recovo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Recovo dark scheme — Amber on Charcoal.
// Every role the app uses is defined explicitly so nothing falls back to an
// unrelated Material default (purple/grey). The error family stays a dedicated
// red so active recording and destructive actions never become amber.
private val DarkColorScheme = darkColorScheme(
    // Primary — Amber
    primary = AmberPrimaryDark,
    onPrimary = AmberOnPrimaryDark,
    primaryContainer = AmberPrimaryContainerDark,
    onPrimaryContainer = AmberOnPrimaryContainerDark,
    inversePrimary = InversePrimaryDark,

    // Secondary — Bronze
    secondary = BronzeSecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = BronzeSecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,

    // Tertiary — Slate (paused state)
    tertiary = SlateTertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = SlateTertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,

    // Error — recording / destructive / error red
    error = RedErrorDark,
    onError = OnRedErrorDark,
    errorContainer = RedErrorContainerDark,
    onErrorContainer = OnRedErrorContainerDark,

    // Neutrals — Charcoal
    background = CharcoalBackgroundDark,
    onBackground = TextPrimaryDark,
    surface = CharcoalSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = CharcoalSurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceTint = AmberPrimaryDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,

    // Outline
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    scrim = RecovoScrim,

    // Surface tonal roles
    surfaceBright = CharcoalSurfaceBrightDark,
    surfaceDim = CharcoalSurfaceDimDark,
    surfaceContainer = CharcoalSurfaceContainerDarkColor,
    surfaceContainerHigh = CharcoalSurfaceContainerHighDark,
    surfaceContainerHighest = CharcoalSurfaceContainerHighestDark,
    surfaceContainerLow = CharcoalSurfaceContainerLowDark,
    surfaceContainerLowest = CharcoalSurfaceContainerLowestDark,
)

// Recovo light scheme — Amber on warm parchment.
private val LightColorScheme = lightColorScheme(
    primary = AmberPrimaryLight,
    onPrimary = AmberOnPrimaryLight,
    primaryContainer = AmberPrimaryContainerLight,
    onPrimaryContainer = AmberOnPrimaryContainerLight,
    inversePrimary = InversePrimaryLight,

    secondary = BronzeSecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = BronzeSecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,

    tertiary = SlateTertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = SlateTertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,

    error = RedErrorLight,
    onError = OnRedErrorLight,
    errorContainer = RedErrorContainerLight,
    onErrorContainer = OnRedErrorContainerLight,

    background = SurfaceLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceTint = AmberPrimaryLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,

    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    scrim = RecovoScrim,

    surfaceBright = SurfaceBrightLight,
    surfaceDim = SurfaceDimLight,
    surfaceContainer = SurfaceContainerLightColor,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
)

/**
 * Recovo Material 3 theme.
 *
 * [dynamicColor] is OFF by default so the Amber/Charcoal identity is the
 * default experience on every API level. Callers may explicitly opt in to
 * wallpaper-based Dynamic Color on Android 12+.
 */
@Composable
fun RecovoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = RecovoShapes,
        content = content,
    )
}
