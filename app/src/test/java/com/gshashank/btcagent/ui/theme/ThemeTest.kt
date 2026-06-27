package com.gshashank.btcagent.ui.theme

import com.gshashank.btcagent.data.model.ColorTheme
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pure JVM unit tests for the per-skin typography helpers — MOBILE-25.
 *
 * Verifies that [typographyFor] and [monoFamilyFor] return the correct [FontFamily] instance
 * for each [ColorTheme] skin. Assertions use referential equality ([assertSame]) against the
 * top-level [FontFamily] vals declared in Type.kt so that any accidental new-allocation or
 * wrong-family substitution is caught immediately.
 *
 * These tests have NO Android runtime dependency — [typographyFor] and [monoFamilyFor] are
 * pure Kotlin functions that produce [androidx.compose.ui.text.font.FontFamily] instances from
 * statically declared top-level vals.
 *
 * All tests MUST fail (red) until [typographyFor] and [monoFamilyFor] are implemented in
 * Type.kt and the four new [FontFamily] vals (SpaceGroteskFontFamily, IbmPlexMonoFontFamily,
 * SoraFontFamily, JetBrainsMonoFontFamily) are declared.
 *
 * Font pairing per skin (PLAN.md):
 *   - BITCOIN  → UI: RobotoFlexFontFamily   / Mono: RobotoMonoFontFamily  (= MonoFontFamily)
 *   - COBALT   → UI: SpaceGroteskFontFamily / Mono: IbmPlexMonoFontFamily
 *   - VIOLET   → UI: SoraFontFamily         / Mono: JetBrainsMonoFontFamily
 *
 * Test coverage:
 *   1.  typographyFor(BITCOIN).bodyLarge.fontFamily  === RobotoFlexFontFamily
 *   2.  typographyFor(COBALT).bodyLarge.fontFamily   === SpaceGroteskFontFamily
 *   3.  typographyFor(VIOLET).bodyLarge.fontFamily   === SoraFontFamily
 *   4.  monoFamilyFor(BITCOIN) === RobotoMonoFontFamily  (the MonoFontFamily alias)
 *   5.  monoFamilyFor(COBALT)  === IbmPlexMonoFontFamily
 *   6.  monoFamilyFor(VIOLET)  === JetBrainsMonoFontFamily
 */
class ThemeTest {

    // =========================================================================
    // typographyFor — UI font family per skin
    // =========================================================================

    @Test
    fun `typographyFor BITCOIN returns Typography with RobotoFlexFontFamily as UI family`() {
        val typography = typographyFor(ColorTheme.BITCOIN)

        assertSame(
            "typographyFor(BITCOIN).bodyLarge.fontFamily must be the top-level RobotoFlexFontFamily val",
            RobotoFlexFontFamily,
            typography.bodyLarge.fontFamily,
        )
    }

    @Test
    fun `typographyFor COBALT returns Typography with SpaceGroteskFontFamily as UI family`() {
        val typography = typographyFor(ColorTheme.COBALT)

        assertSame(
            "typographyFor(COBALT).bodyLarge.fontFamily must be the top-level SpaceGroteskFontFamily val",
            SpaceGroteskFontFamily,
            typography.bodyLarge.fontFamily,
        )
    }

    @Test
    fun `typographyFor VIOLET returns Typography with SoraFontFamily as UI family`() {
        val typography = typographyFor(ColorTheme.VIOLET)

        assertSame(
            "typographyFor(VIOLET).bodyLarge.fontFamily must be the top-level SoraFontFamily val",
            SoraFontFamily,
            typography.bodyLarge.fontFamily,
        )
    }

    // =========================================================================
    // monoFamilyFor — mono/number font family per skin
    // =========================================================================

    @Test
    fun `monoFamilyFor BITCOIN returns RobotoMonoFontFamily (the MonoFontFamily alias)`() {
        val monoFamily = monoFamilyFor(ColorTheme.BITCOIN)

        assertSame(
            "monoFamilyFor(BITCOIN) must return the top-level RobotoMonoFontFamily val " +
                "(same reference as MonoFontFamily)",
            RobotoMonoFontFamily,
            monoFamily,
        )
    }

    @Test
    fun `monoFamilyFor COBALT returns IbmPlexMonoFontFamily`() {
        val monoFamily = monoFamilyFor(ColorTheme.COBALT)

        assertSame(
            "monoFamilyFor(COBALT) must return the top-level IbmPlexMonoFontFamily val",
            IbmPlexMonoFontFamily,
            monoFamily,
        )
    }

    @Test
    fun `monoFamilyFor VIOLET returns JetBrainsMonoFontFamily`() {
        val monoFamily = monoFamilyFor(ColorTheme.VIOLET)

        assertSame(
            "monoFamilyFor(VIOLET) must return the top-level JetBrainsMonoFontFamily val",
            JetBrainsMonoFontFamily,
            monoFamily,
        )
    }
}
