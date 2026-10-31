package com.aetherkeys.fx

import androidx.compose.ui.graphics.Color

/**
 * Les couleurs de la fenêtre FX, reprises une à une de la maquette.
 *
 * Elles sont écrites en dur, et non tirées de `AppColors` : la fenêtre FX est
 * DÉTACHÉE de l'app, elle ne doit pas changer d'aspect quand le thème du mixer
 * change. C'est la contrepartie assumée du détachement.
 */
object FxC {
    /** Fond de la fenêtre. */
    val winTop = Color(0xFF1F2635)
    val winBot = Color(0xFF131925)

    /** Textes. */
    val ink = Color(0xFFDFE4EE)
    val dim = Color(0xFF8D96AB)
    val mute = Color(0xFF5F6880)

    /** Filets : `.07` et `.13` de blanc. */
    val line = Color(0x12FFFFFF)
    val line2 = Color(0x21FFFFFF)

    /** Accent des commandes remplies. */
    val in1 = Color(0xFFC7D9F9)
    val in2 = Color(0xFF9DB9EF)
    val inText = Color(0xFF1B2440)

    /** Accent général (le bleu des jauges et des sélections). */
    val acc = Color(0xFF8FB2E8)

    /** Boutons sobres. */
    val rail = Color(0xFF2D3041)
    val rail2 = Color(0xFF474A63)

    /** Le rouge de la corbeille. */
    val danger = Color(0xFFFF3B5C)
    val dangerSoft = Color(0xFFFF8095)

    /** Fond des cartes, au repos et survolées. */
    val cardTop = Color(0xFF323845)
    val cardBot = Color(0xFF222733)
    val cardTopHot = Color(0xFF373E4C)
    val cardBotHot = Color(0xFF252A37)

    /** Le voile sombre derrière une fenêtre flottante. */
    val scrim = Color(0xCC05070C)
}

/** `hx("#8FB2E8", .3f)` de la maquette : la même couleur, transparente. */
fun Color.alpha(a: Float): Color = this.copy(alpha = a)
