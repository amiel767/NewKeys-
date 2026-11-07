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

    /**
     * LE POTARD PLAT — les seules teintes du disque.
     *
     * Elles viennent de la mesure de la référence : chez Valhalla le disque vaut
     * 121 de luminance sur un panneau à 52, soit un rapport de 2,3. Un disque à
     * 40 (l'ancien `0xFF2A2F3C`) disparaissait dans le panneau — c'est ce qui
     * faisait qu'on ne « voyait » pas le potard.
     *
     * [knobDisc] est un gris-bleu moyen, dans notre palette froide : on garde la
     * même luminance que la référence, on lui donne notre teinte.
     */
    val knobDisc = Color(0xFF6E7484)
    /** La valeur affichée sous un potard. */
    val knobVal = Color(0xFFAAB2C4)
    /** L'aiguille, SOMBRE sur le disque clair (la référence ne la fait pas blanche). */
    val knobIndex = Color(0xFF14171F)
    /** L'anneau creusé, juste hors du disque. */
    val knobTrack = Color(0x66000000)
    /** Le creux d'une cellule de réglage. */
    val cellTop = Color(0xFF161B26)
    val cellBot = Color(0xFF0E121B)
}

/** `hx("#8FB2E8", .3f)` de la maquette : la même couleur, transparente. */
fun Color.alpha(a: Float): Color = this.copy(alpha = a)
