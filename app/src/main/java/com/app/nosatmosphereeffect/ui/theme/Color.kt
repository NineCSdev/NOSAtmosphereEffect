package com.app.nosatmosphereeffect.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Centralised palette for Atmo Engine.
 *
 * A neutral dark foundation keeps wallpapers visually dominant. Mint, amber,
 * and violet roles make controls distinguishable without turning the tool into
 * a one-color interface.
 */

// Brand accent (carried over from the original colors.xml)
val AtmoMint = Color(0xFF9BE7D4)
val AtmoMintDim = Color(0xFF73C9B6)
val OnAtmoMint = Color(0xFF00382F)
val AtmoMintContainer = Color(0xFF155F52)

val AtmoAmber = Color(0xFFF5C86F)
val OnAtmoAmber = Color(0xFF402D00)
val AtmoAmberContainer = Color(0xFF5C430B)

val AtmoPurple = Color(0xFFCBB8FF)
val AtmoPurpleDim = Color(0xFFAE98E7)
val OnAtmoPurple = Color(0xFF332060)
val AtmoPurpleContainer = Color(0xFF49377A)

// Core neutrals
val AtmoBlack = Color(0xFF000000)
val AtmoWhite = Color(0xFFFFFFFF)

// Subtle elevation steps above pure black (used for cards / sheets / fields)
val AtmoSurface = Color(0xFF000000)
val AtmoSurfaceContainerLow = Color(0xFF0C0D0D)
val AtmoSurfaceContainer = Color(0xFF121414)
val AtmoSurfaceContainerHigh = Color(0xFF1A1D1C)
val AtmoSurfaceContainerHighest = Color(0xFF232726)

// Text / hairlines
val AtmoOnSurface = Color(0xFFFFFFFF)
val AtmoOnSurfaceVariant = Color(0xFFBAC4C1)
val AtmoOutline = Color(0x1FFFFFFF)        // 12% white hairline
val AtmoOutlineStrong = Color(0x33FFFFFF)  // 20% white hairline

// Status colours
val AtmoError = Color(0xFFFF5449)
val AtmoDisabled = Color(0xFF55555A)
