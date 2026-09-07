package com.edu.ackline.ui.theme

import androidx.compose.ui.graphics.Color

// Ackline teal/blue-green visual system (Phase 9, visual direction V2).
// Source of truth: .design-reference/VISUAL_DIRECTION.md + teal V2 mockup.
//
// Rules encoded here:
//   - dark theme = blue-graphite / teal-neutral surfaces, never pure black
//   - light theme = cool off-white screen, white cards, subtly layered header
//   - primary identity = muted mint-teal (actions, selection, eyebrow)
//   - Important severity = bluer blue-green (tertiary) so it never reads as
//     identical to the global primary action
//   - Urgent = muted coral, clearly separated from both
//   - lavender/purple-first identity removed

// ── DARK: screen + tonal layers (lighter = further forward)
internal val TealDarkScreen = Color(0xFF0A1417)
internal val TealDarkInset = Color(0xFF0D1B22)
internal val TealDarkCardQuiet = Color(0xFF112129)
internal val TealDarkHeaderBand = Color(0xFF11212A)
internal val TealDarkCard = Color(0xFF162A34)
internal val TealDarkSegmentSelected = Color(0xFF17453F)
internal val TealDarkHairline = Color(0xFF243941)

// ── DARK: ink
internal val TealDarkInkPrimary = Color(0xFFE8F1F2)
internal val TealDarkInkSecondary = Color(0xFF9AB0B5)
internal val TealDarkInkTertiary = Color(0xFF6E888E)

// ── DARK: mint-teal accent (primary actions + selection identity)
internal val TealDarkAccent = Color(0xFF4ECDAC)
internal val TealDarkAccentBright = Color(0xFF5FE0BD)
internal val InkOnTealDarkAction = Color(0xFF00281C)

// ── DARK: Important = bluer blue-green (tertiary family)
internal val TealDarkBlueGreen = Color(0xFF58B7BF)
internal val TealDarkBlueGreenContainer = Color(0xFF153A41)
internal val TealDarkBlueGreenInk = Color(0xFFA9DFE4)

// ── DARK: Urgent = muted coral
internal val TealDarkCoral = Color(0xFFEC8E7E)
internal val TealDarkCoralContainer = Color(0xFF4C2420)
internal val TealDarkCoralInk = Color(0xFFFFDAD2)

// ── LIGHT: screen + tonal layers
internal val TealLightScreen = Color(0xFFEEF2F3)
internal val TealLightInset = Color(0xFFDFE7E9)
internal val TealLightHeaderBand = Color(0xFFE4EBED)
internal val TealLightCardQuiet = Color(0xFFF3F7F8)
internal val TealLightCard = Color(0xFFFFFFFF)
internal val TealLightSegmentSelected = Color(0xFFC4E3DA)
internal val TealLightHairline = Color(0xFFC7D3D6)

// ── LIGHT: ink
internal val TealLightInkPrimary = Color(0xFF11252B)
internal val TealLightInkSecondary = Color(0xFF425A60)
internal val TealLightInkTertiary = Color(0xFF6E848A)

// ── LIGHT: deeper muted teal for primary actions/identity
internal val TealLightAccent = Color(0xFF0E7A67)
internal val TealLightAccentContainer = Color(0xFFB7E3D6)
internal val TealLightAccentInk = Color(0xFF00352A)
internal val InkOnTealLightAction = Color(0xFFEFFAF6)

// ── LIGHT: Important = deeper blue-green (tertiary family)
internal val TealLightBlueGreen = Color(0xFF2A6E7E)
internal val TealLightBlueGreenContainer = Color(0xFFCFE7EB)
internal val TealLightBlueGreenInk = Color(0xFF0A333E)

// ── LIGHT: Urgent = muted coral (deeper for light backgrounds)
internal val TealLightCoral = Color(0xFFB3402E)
internal val TealLightCoralContainer = Color(0xFFFBDFD8)
internal val TealLightCoralInk = Color(0xFF4E130B)

// ── Shared on-color surfaces (error dialog/text-button cases)
internal val InkOnErrorDark = Color(0xFF2B0604)
internal val InkOnErrorLight = Color(0xFF690005)
