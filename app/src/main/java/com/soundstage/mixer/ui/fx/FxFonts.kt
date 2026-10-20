package com.soundstage.mixer.ui.fx

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.soundstage.mixer.R

/**
 * Fonts for the FX window. The families named by the design reference now ship
 * in `res/font` (they arrived in the `JetBrains_Mono,Orbitron,Share_Tech_Mono,
 * Sora,Space_Grotesk.zip` asset). The app's own interface font (Plus Jakarta
 * Sans, also used by the Sheets module) and JetBrains Mono were already present
 * and are reused rather than duplicated.
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

    /** Technical labels, from the reference. */
    val mono = FontFamily(variable(R.font.share_tech_mono, FontWeight.Normal))

    /** JetBrains Mono, already in the project, kept for dense readouts. */
    val jetBrainsMono = FontFamily(
        variable(R.font.jetbrains_mono, FontWeight.Normal),
        variable(R.font.jetbrains_mono, FontWeight.Medium),
        variable(R.font.jetbrains_mono, FontWeight.Bold),
    )

    val orbitron = FontFamily(
        variable(R.font.orbitron, FontWeight.Normal),
        variable(R.font.orbitron, FontWeight.Medium),
        variable(R.font.orbitron, FontWeight.Bold),
        variable(R.font.orbitron, FontWeight.ExtraBold),
    )

    val spaceGrotesk = FontFamily(
        variable(R.font.space_grotesk, FontWeight.Normal),
        variable(R.font.space_grotesk, FontWeight.Medium),
        variable(R.font.space_grotesk, FontWeight.Bold),
    )

    val sora = FontFamily(
        variable(R.font.sora, FontWeight.Normal),
        variable(R.font.sora, FontWeight.Medium),
        variable(R.font.sora, FontWeight.Bold),
        variable(R.font.sora, FontWeight.ExtraBold),
    )

    /** Title face per effect, exactly as the doc 03 table specifies. */
    fun title(effectId: Int): FontFamily = when (effectId) {
        0, 8, 9, 12, 13 -> spaceGrotesk
        1 -> jetBrainsMono
        2, 10, 14 -> orbitron
        3 -> mono
        else -> sora
    }
}
