package com.app.nosatmosphereeffect.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Centralised palette for Atmo Engine.
 *
 * The app keeps its signature "pure-black, single purple accent" identity but
 * introduces a few near-black elevation tones so cards and sheets read with a
 * little depth instead of melting into the background.
 */

// Brand accent (carried over from the original colors.xml)
val AtmoPurple = Color(0xFFD0BCFF)
val AtmoPurpleDim = Color(0xFFB9A3F0)
val OnAtmoPurple = Color(0xFF381E72)
val AtmoPurpleContainer = Color(0xFF4A357F)

// Core neutrals
val AtmoBlack = Color(0xFF000000)
val AtmoWhite = Color(0xFFFFFFFF)

// Subtle elevation steps above pure black (used for cards / sheets / fields)
val AtmoSurface = Color(0xFF000000)
val AtmoSurfaceContainerLow = Color(0xFF0C0C0E)
val AtmoSurfaceContainer = Color(0xFF121214)
val AtmoSurfaceContainerHigh = Color(0xFF1A1A1D)
val AtmoSurfaceContainerHighest = Color(0xFF222226)

// Text / hairlines
val AtmoOnSurface = Color(0xFFFFFFFF)
val AtmoOnSurfaceVariant = Color(0xFFB7B7BC)
val AtmoOutline = Color(0x1FFFFFFF)        // 12% white hairline
val AtmoOutlineStrong = Color(0x33FFFFFF)  // 20% white hairline

// Status colours
val AtmoError = Color(0xFFFF5449)
val AtmoDisabled = Color(0xFF55555A)
