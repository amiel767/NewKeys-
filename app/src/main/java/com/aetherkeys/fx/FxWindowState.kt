package com.aetherkeys.fx

import androidx.compose.ui.graphics.Color

/**
 * Le contrat de la fenêtre FX.
 *
 * C'EST CE QUI LA REND DÉTACHABLE. La fenêtre ne lit aucun état du mixer et
 * n'appelle aucune méthode du ViewModel : elle reçoit un [FxWindowState] et
 * émet des [FxWindowActions]. Le jour où on la branche, il suffit d'écrire un
 * adaptateur qui traduit le mixer vers ce contrat — la fenêtre elle-même ne
 * change pas d'une ligne.
 *
 * La contrepartie est qu'un champ oublié ici est un champ qui n'existe pas dans
 * l'interface. C'est voulu : ça oblige à décider, plutôt qu'à laisser un
 * composant piocher directement dans l'état global.
 */

/** Un emplacement occupé. */
data class FxSlotState(
    val info: FxInfo,
    /** Engagé ou contourné. */
    val on: Boolean,
    /** Le preset affiché au pied de la carte, `null` s'il n'y en a pas. */
    val presetName: String?,
    /** La couleur de l'effet, résolue par l'hôte. */
    val accent: Color,
)

/**
 * Tout ce que la fenêtre a besoin de savoir.
 *
 * [slots] fait TOUJOURS la longueur de [FxGeo.SLOTS] : une entrée `null` est un
 * emplacement libre. Une liste plus courte que quatre afficherait moins de
 * cases, ce qui casserait la grille.
 */
data class FxWindowState(
    val slots: List<FxSlotState?>,
    /** Les effets qu'on peut ajouter. `null` quand le catalogue est fermé. */
    val catalog: List<FxInfo>? = null,
    /** L'effet dont la page est ouverte, `null` si on est sur le rack. */
    val openEffect: FxInfo? = null,
    /** Les valeurs courantes des paramètres de [openEffect]. */
    val values: List<Float> = emptyList(),
    /** Vrai dès qu'un réglage a bougé : c'est ce qui allume le bouton Save. */
    val custom: Boolean = false,
    /** La couleur d'un effet, par son identifiant. */
    val accentOf: (Int) -> Color = { FxC.acc },
) {
    /** Le catalogue est-il ouvert ? */
    val catalogOpen: Boolean get() = catalog != null
}

/** Tout ce que la fenêtre peut demander à son hôte. */
interface FxWindowActions {
    /** Fermer la fenêtre entière. */
    fun onClose()

    /** Revenir au rack depuis une page ou le catalogue. */
    fun closePage()

    /** Ouvrir la page d'un effet déjà installé, par son identifiant. */
    fun open(effectId: Int)

    /** Ouvrir le catalogue pour ajouter un effet. */
    fun openCatalog()

    /** Installer un effet du catalogue dans le premier emplacement libre. */
    fun add(effectId: Int)

    /** Retirer l'effet d'un emplacement. */
    fun remove(slot: Int)

    /** Engager ou contourner un effet. */
    fun toggle(slot: Int)

    /** Régler un paramètre de l'effet ouvert. */
    fun setValue(index: Int, value: Float)

    /** Ouvrir la feuille de nommage d'un preset utilisateur. */
    fun beginSave()
}
