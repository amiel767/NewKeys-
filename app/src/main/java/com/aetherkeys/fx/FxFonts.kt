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
 * Elles vivent ICI et pas dans le mixer : la fenêtre est détachée, elle ne doit
 * pas dépendre des polices de l'application. Les fichiers sont dans `res/font`,
 * déjà présents dans l'app — on ne les duplique pas, on les référence.
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

    /**
     * Police du TITRE DE LA PAGE D'EFFET — Orbitron.
     *
     * C'est la police « technique, large, géométrique » qui approche le mieux le
     * lettrage des plugins de reverb de la référence : des capitales larges aux
     * contre-formes carrées, l'air « panneau d'instrument ». Cinzel était une
     * sérif d'enseigne, pas d'interface audio.
     */
    val display = FontFamily(
        Font(R.font.orbitron_medium, FontWeight.Medium),
        Font(R.font.orbitron_bold, FontWeight.Bold),
    )

    /** Libellés techniques. */
    val mono = FontFamily(variable(R.font.share_tech_mono, FontWeight.Normal))
}
