package com.aetherkeys.fx

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * La géométrie de la fenêtre FX.
 *
 * TOUT EST EN UNITÉS DE LA MAQUETTE (891 × 411). La référence est dessinée sur
 * un canvas de 891 × 411 ; ses cotes sont donc des unités de maquette, pas des
 * dp. La fenêtre se met à l'échelle en UNE fois, à la racine : aucune cote
 * interne n'a besoin d'être convertie, et rien ne peut « s'étendre sur tout
 * l'écran » puisque la largeur est celle de la maquette multipliée par un seul
 * facteur.
 *
 * POURQUOI `fontScale = 1`. La géométrie est en unités fixes mais les `sp`
 * suivent la taille de police système. Un utilisateur à 1,3× obtenait des
 * libellés 30 % plus gros que leur case, alors que la géométrie ne bougeait
 * pas : textes écrasés, valeurs décalées. On fige donc le `fontScale` à 1 à la
 * racine, comme le fait déjà la scène des instruments Æther.
 */
object FxGeo {
    /** La maquette de référence. */
    const val W = 891f
    const val H = 411f

    /** Rayon de la fenêtre. */
    const val RADIUS = 20f

    /** Bande d'en-tête : 46 unités, comme la référence. */
    const val HEAD = 46f

    /** Marge du corps. */
    const val PAD = 12f

    /** Le corps disponible : 411 − 46 − 24 = 341. */
    const val BODY = H - HEAD - PAD * 2

    /**
     * LA PAGE D'UN EFFET REMPLIT TOUTE LA FENÊTRE.
     *
     * Il n'y a PAS de bande d'en-tête sur cette page : le titre est posé dans le
     * corps, en haut de la colonne de gauche, et les contrôles occupent tout le
     * reste. Une bande d'en-tête laissait une réserve vide que la référence n'a
     * pas — chez Valhalla le mot est écrit DANS le panneau, il ne consomme pas
     * une ligne à lui seul.
     */
    const val PAGE_PAD = 10f

    /** La colonne de gauche de la page d'effet : le titre, le gros potard. */
    const val COL_L = 300f

    /** La gouttière entre la colonne de gauche et les cellules. */
    const val COL_GAP = 10f

    /** Trois colonnes de cellules : à deux, les rangées écrasent le potard. */
    const val CELLS = 3

    /** Rayon d'une cellule de réglage. */
    const val CELL_R = 14f

    /**
     * Diamètre VISÉ d'un potard de cellule.
     *
     * C'est une CIBLE, pas une contrainte : la cellule mesure ce qu'elle a et
     * prend le plus grand disque qui entre ([FxControlCell]). Une taille figée
     * était le bug de la version à deux colonnes — le potard était écrasé à 52
     * unités dans une case de 63 sans que rien ne le signale.
     */
    const val KNOB = 96

    /** La place réservée sous le disque : libellé et valeur. */
    const val KNOB_TEXT = 34f

    /** Diamètre du GROS potard principal. */
    const val KNOB_MAIN = 232

    /** Quatre emplacements, une gouttière de 10 entre eux. */
    const val SLOTS = 4
    const val GAP = 10f

    /** Largeur d'une carte : (891 − 24 − 30) / 4 = 209,25 → 208,8 mesuré. */
    const val CARD_W = (W - PAD * 2 - GAP * (SLOTS - 1)) / SLOTS

    /** Décalage d'un emplacement depuis le bord gauche. */
    fun slotX(i: Int): Float = PAD + i * (CARD_W + GAP)
}

/**
 * Racine de la fenêtre. On met à l'échelle la DENSITÉ, pas chaque cote : le
 * contenu s'écrit alors directement en unités de maquette (`891.dp` = toute la
 * fenêtre), et un seul facteur suffit à la faire tenir à l'écran.
 */
@Composable
fun FxStage(
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val d = LocalDensity.current
    val s = if (scale > 0f) scale else 1f
    CompositionLocalProvider(
        LocalDensity provides Density(d.density * s, fontScale = 1f)
    ) {
        Box(modifier.size(FxGeo.W.dp, FxGeo.H.dp)) { content() }
    }
}

/**
 * Échelle qui fait tenir la maquette dans la place disponible. On ne dépasse
 * JAMAIS 1 : au-delà, la fenêtre s'étendrait sur tout l'écran au lieu de garder
 * la taille de la maquette.
 */
@Composable
fun rememberFitScale(availableW: Dp, availableH: Dp): Float {
    val d = LocalDensity.current
    val w = availableW.value / FxGeo.W
    val h = availableH.value / FxGeo.H
    return minOf(w, h, 1f)
}
