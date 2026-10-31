package com.aetherkeys.fx

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.soundstage.mixer.R

/**
 * Les polices de la fenêtre FX.
 *
 * Elles appartiennent au paquet détaché : la fenêtre ne doit pas dépendre de
 * `com.soundstage.mixer.ui.fx.FxFonts`, sinon détacher la fenêtre ne changerait
 * rien. Les fichiers sont dans `res/font`, déjà présents dans l'app — on ne les
 * duplique pas, on les référence.
 */
object FxFonts {

    @OptIn(ExperimentalTextApi::class)
    private fun variable(res: Int, w: FontWeight) =
        Font(res, weight = w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))

    /** Police d'interface de la fenêtre (la même que la maquette : Space Grotesk). */
    val ui = FontFamily(
        variable(R.font.space_grotesk, FontWeight.Normal),
        variable(R.font.space_grotesk, FontWeight.Medium),
        variable(R.font.space_grotesk, FontWeight.Bold),
    )

    /**
     * Police des titres : Cinzel, celle de la maquette et déjà utilisée par les
     * instruments Æther. Les titres parlent donc la même langue que le reste.
     */
    val title = FontFamily(variable(R.font.cinzel, FontWeight.Bold))

    /** Libellés techniques. */
    val mono = FontFamily(variable(R.font.share_tech_mono, FontWeight.Normal))
}
