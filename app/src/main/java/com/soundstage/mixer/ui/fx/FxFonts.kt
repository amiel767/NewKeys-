package com.soundstage.mixer.ui.fx

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.soundstage.mixer.R

/**
 * Fonts for the FX window. No new font file is added: this reuses the resources
 * already shipped with the app (the set used by the Sheets module) plus the two
 * unused ones (`inter`, `lexend`).
 *
 * The design reference asks for Share Tech Mono, Orbitron, Space Grotesk and
 * Sora, which are not in the project. Each is mapped to the closest available
 * family instead (see [title]).
 */
object FxFonts {
    @OptIn(ExperimentalTextApi::class)
    private fun variable(res: Int, w: FontWeight) =
        Font(res, weight = w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))

    /** Interface font of the whole app (Sheets uses it too). */
    val ui = FontFamily(
        variable(R.font.plus_jakarta_sans, FontWeight.Normal),
        variable(R.font.plus_jakarta_sans, FontWeight.Medium),
        variable(R.font.plus_jakarta_sans, FontWeight.Bold),
        variable(R.font.plus_jakarta_sans, FontWeight.ExtraBold),
    )

    /** Technical labels (stand-in for Share Tech Mono). */
    val mono = FontFamily(
        variable(R.font.jetbrains_mono, FontWeight.Normal),
        variable(R.font.jetbrains_mono, FontWeight.Medium),
        variable(R.font.jetbrains_mono, FontWeight.Bold),
    )

    /** Secondary mono, kept for density-heavy readouts. */
    val firaMono = FontFamily(
        variable(R.font.fira_code, FontWeight.Normal),
        variable(R.font.fira_code, FontWeight.Medium),
    )

    /** Geometric display face (stand-in for Orbitron / Space Grotesk / Sora). */
    val display = FontFamily(
        variable(R.font.lexend, FontWeight.Normal),
        variable(R.font.lexend, FontWeight.Medium),
        variable(R.font.lexend, FontWeight.Bold),
        variable(R.font.lexend, FontWeight.ExtraBold),
    )

    /** Neutral sans, used where a lighter geometric look is wanted. */
    val inter = FontFamily(
        variable(R.font.inter, FontWeight.Normal),
        variable(R.font.inter, FontWeight.Medium),
        variable(R.font.inter, FontWeight.Bold),
    )

    /**
     * Title face per effect, from the doc 03 table mapped onto available
     * families: Space Grotesk -> Inter, Orbitron/Sora -> Lexend,
     * Share Tech Mono -> JetBrains Mono.
     */
    fun title(effectId: Int): FontFamily = when (effectId) {
        0, 8, 9, 12, 13 -> inter      // Space Grotesk
        1 -> mono                     // JetBrains Mono
        2, 10, 14 -> display          // Orbitron
        3 -> mono                     // Share Tech Mono
        else -> display               // Sora
    }
}
