package com.gshashank.btcagent.ui.theme

import androidx.compose.ui.graphics.Color

// Bitcoin design tokens — accent + direction are shared across light/dark.
val BtcAccent = Color(0xFFF7931A)
val BtcAccentDark = Color(0xFFC8740D)
val BtcUp = Color(0xFF22C55E)
val BtcDown = Color(0xFFEF4444)

// Price direction tokens (exact hex per MOBILE-7 spec).
val BtcPriceUp = Color(0xFF00C853)
val BtcPriceDown = Color(0xFFD50000)

// Dark surface palette (dark-first default).
val BtcBg = Color(0xFF0E0E12)
val BtcBg2 = Color(0xFF16161B)
val BtcCard = Color(0xFF1C1C23)
val BtcCardBorder = Color(0xFF2A2A35)
val BtcHairline = Color(0xFF232328)
val BtcText = Color(0xFFFAFAFA)
val BtcText2 = Color(0x8FFAFAFA) // #FAFAFA at ~56% alpha
val BtcText3 = Color(0x66FAFAFA) // #FAFAFA at ~40% alpha
val BtcChip = Color(0x1AFAFAFA) // #FAFAFA at ~10% alpha

// Light surface palette (per design handoff: bg #F4F4F6, bg2 #FFFFFF, text #16161B).
val BtcLightBg = Color(0xFFF4F4F6)
val BtcLightBg2 = Color(0xFFFFFFFF)
val BtcLightCard = Color(0xFFFFFFFF)
val BtcLightCardBorder = Color(0xFFE2E2E6)
val BtcLightHairline = Color(0xFFEAEAEE)
val BtcLightText = Color(0xFF16161B)
val BtcLightText2 = Color(0x8F16161B) // #16161B at ~56% alpha
val BtcLightText3 = Color(0x6616161B) // #16161B at ~40% alpha
val BtcLightChip = Color(0x0A16161B) // #16161B at ~4% alpha

// =============================================================================
// Cobalt skin — MOBILE-25
// =============================================================================

val CobaltAccent = Color(0xFF4F8DFD)
val CobaltUp = Color(0xFF22D39A)
val CobaltDown = Color(0xFFFF5C72)

// Cobalt dark surface palette — blue-tinted darks mirroring Bitcoin dark structure.
val CobaltDarkBg = Color(0xFF0B0E18)
val CobaltDarkBg2 = Color(0xFF111623)
val CobaltDarkCard = Color(0xFF16192B)
val CobaltDarkCardBorder = Color(0xFF222840)
val CobaltDarkText = Color(0xFFFAFAFA)
val CobaltDarkText2 = Color(0x8FFAFAFA)
val CobaltDarkText3 = Color(0x66FAFAFA)
val CobaltDarkChip = Color(0x1A4F8DFD)

// Cobalt light surface palette — blue-tinted light with sufficient contrast.
val CobaltLightBg = Color(0xFFEEF3FE)
val CobaltLightBg2 = Color(0xFFFFFFFF)
val CobaltLightCard = Color(0xFFFFFFFF)
val CobaltLightCardBorder = Color(0xFFCDD9FC)
val CobaltLightText = Color(0xFF0D1530)
val CobaltLightText2 = Color(0x8F0D1530)
val CobaltLightText3 = Color(0x660D1530)
val CobaltLightChip = Color(0x0A4F8DFD)

// =============================================================================
// Violet skin — MOBILE-25
// =============================================================================

val VioletAccent = Color(0xFF9B7BFF)
val VioletUp = Color(0xFF3DDC97)
val VioletDown = Color(0xFFF2616B)

// Violet dark surface palette — purple-tinted darks mirroring Bitcoin dark structure.
val VioletDarkBg = Color(0xFF0D0B18)
val VioletDarkBg2 = Color(0xFF131022)
val VioletDarkCard = Color(0xFF18142B)
val VioletDarkCardBorder = Color(0xFF25203F)
val VioletDarkText = Color(0xFFFAFAFA)
val VioletDarkText2 = Color(0x8FFAFAFA)
val VioletDarkText3 = Color(0x66FAFAFA)
val VioletDarkChip = Color(0x1A9B7BFF)

// Violet light surface palette — purple-tinted light with sufficient contrast.
val VioletLightBg = Color(0xFFF2EFFF)
val VioletLightBg2 = Color(0xFFFFFFFF)
val VioletLightCard = Color(0xFFFFFFFF)
val VioletLightCardBorder = Color(0xFFD8CFFF)
val VioletLightText = Color(0xFF150E30)
val VioletLightText2 = Color(0x8F150E30)
val VioletLightText3 = Color(0x66150E30)
val VioletLightChip = Color(0x0A9B7BFF)
