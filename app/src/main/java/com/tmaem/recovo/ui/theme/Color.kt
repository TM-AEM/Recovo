package com.tmaem.recovo.ui.theme

import androidx.compose.ui.graphics.Color

// Recovo Studio Palette
//
// Brand foundation: warm Amber accents on a Charcoal studio surface.
// Recording / destructive / error semantics keep a dedicated red so the
// active recording control is never recolored to brand amber.
//
// Every named color below is mapped to a Material 3 role in Theme.kt so the
// app no longer falls back to unrelated Material defaults (purple/grey).

// ---------------------------------------------------------------------------
// Dark scheme (Charcoal studio)
// ---------------------------------------------------------------------------
// Primary - Amber
val AmberPrimaryDark = Color(0xFFFFB74D) // Vivid warm amber
val AmberOnPrimaryDark = Color(0xFF452B00)
val AmberPrimaryContainerDark = Color(0xFF633F00)
val AmberOnPrimaryContainerDark = Color(0xFFFFDDB3)

// Secondary - muted bronze (supports/complements amber)
val BronzeSecondaryDark = Color(0xFFE7C08A)
val OnSecondaryDark = Color(0xFF3F2E00)
val BronzeSecondaryContainerDark = Color(0xFF52432A)
val OnSecondaryContainerDark = Color(0xFFFFDFA8)

// Tertiary - cool slate blue, used for "paused" so it never reads as amber or red
val SlateTertiaryDark = Color(0xFFA8C7E8)
val OnTertiaryDark = Color(0xFF0A2A45)
val SlateTertiaryContainerDark = Color(0xFF23405C)
val OnTertiaryContainerDark = Color(0xFFCFE3F7)

// Error - dedicated recording / destructive / error red
val RedErrorDark = Color(0xFFFF8A80)
val OnRedErrorDark = Color(0xFF5C0000)
val RedErrorContainerDark = Color(0xFF93000A)
val OnRedErrorContainerDark = Color(0xFFFFDAD6)

// Neutrals - Charcoal
val CharcoalBackgroundDark = Color(0xFF121316)
val CharcoalSurfaceDark = Color(0xFF1A1C20)
val CharcoalSurfaceVariantDark = Color(0xFF26292E)
val CharcoalSurfaceContainerLowestDark = Color(0xFF0D0E11)
val CharcoalSurfaceContainerLowDark = Color(0xFF1A1C20)
val CharcoalSurfaceContainerDarkColor = Color(0xFF1E2024)
val CharcoalSurfaceContainerHighDark = Color(0xFF282A2E)
val CharcoalSurfaceContainerHighestDark = Color(0xFF333539)
val CharcoalSurfaceDimDark = Color(0xFF121316)
val CharcoalSurfaceBrightDark = Color(0xFF38393D)
val TextPrimaryDark = Color(0xFFE6E1E5)
val TextSecondaryDark = Color(0xFFC7C5D0)
val OutlineDark = Color(0xFF938F99)
val OutlineVariantDark = Color(0xFF49454F)
val InverseSurfaceDark = Color(0xFFE6E1E5)
val InverseOnSurfaceDark = Color(0xFF313033)
val InversePrimaryDark = Color(0xFF8A5100)

// ---------------------------------------------------------------------------
// Light scheme (warm parchment)
// ---------------------------------------------------------------------------
val AmberPrimaryLight = Color(0xFF8A5100)
val AmberOnPrimaryLight = Color(0xFFFFFFFF)
val AmberPrimaryContainerLight = Color(0xFFFFDDB3)
val AmberOnPrimaryContainerLight = Color(0xFF2C1600)

val BronzeSecondaryLight = Color(0xFF7A5A1E)
val OnSecondaryLight = Color(0xFFFFFFFF)
val BronzeSecondaryContainerLight = Color(0xFFFFDFA8)
val OnSecondaryContainerLight = Color(0xFF281A00)

val SlateTertiaryLight = Color(0xFF3A6080)
val OnTertiaryLight = Color(0xFFFFFFFF)
val SlateTertiaryContainerLight = Color(0xFFCFE3F7)
val OnTertiaryContainerLight = Color(0xFF001D33)

val RedErrorLight = Color(0xFFBA1A1A)
val OnRedErrorLight = Color(0xFFFFFFFF)
val RedErrorContainerLight = Color(0xFFFFDAD6)
val OnRedErrorContainerLight = Color(0xFF410002)

val SurfaceLight = Color(0xFFFFF8F6)
val SurfaceVariantLight = Color(0xFFEFE0D9)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFFFF1EC)
val SurfaceContainerLightColor = Color(0xFFFCEBE5)
val SurfaceContainerHighLight = Color(0xFFF6E5DF)
val SurfaceContainerHighestLight = Color(0xFFF0DFD9)
val SurfaceDimLight = Color(0xFFE0D8D5)
val SurfaceBrightLight = Color(0xFFFFF8F6)
val TextPrimaryLight = Color(0xFF201A17)
val TextSecondaryLight = Color(0xFF52443C)
val OutlineLight = Color(0xFF85736B)
val OutlineVariantLight = Color(0xFFD8C2B8)
val InverseSurfaceLight = Color(0xFF362F2C)
val InverseOnSurfaceLight = Color(0xFFFBEEE9)
val InversePrimaryLight = Color(0xFFFFB74D)

// Shared
val RecovoScrim = Color(0xFF000000)
