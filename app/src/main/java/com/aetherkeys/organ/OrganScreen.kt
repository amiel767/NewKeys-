package com.aetherkeys.organ

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Interface orgue — traduction 1:1 de la maquette HTML (891 × 411).
 * La maquette est mise à l'échelle uniformément (comme `fitToScreen`) en redéfinissant la densité :
 * tous les dp/sp restent ceux du HTML, donc rendu net à toute taille d'écran.
 * À placer en paysage ; masque barres système / encoche côté Activity si tu veux le plein écran.
 *
 * [startInset] réserve une bande à gauche (unités de la maquette) pour la flèche retour de la
 * fenêtre hôte : la maquette démarre ses panneaux à x=8, sous la flèche. Le fond couvre toujours
 * toute la surface, seul le bloc est décalé.
 */
@Composable
fun OrganScreen(
    modifier: Modifier = Modifier,
    state: OrganState = remember { OrganState() },
    listener: OrganListener = OrganListener.None,
    startInset: Float = 0f,
) {
    state.listener = listener
    BoxWithConstraints(
        modifier.fillMaxSize().background(Brush.verticalGradient(listOf(OrganColors.BgMainTop, OrganColors.BgMainBottom))),
    ) {
        val base = LocalDensity.current
        val availW = (constraints.maxWidth - startInset * base.density).coerceAtLeast(1f)
        val scale = min(availW / (OrganDesign.W * base.density), constraints.maxHeight / (OrganDesign.H * base.density))
        CompositionLocalProvider(LocalDensity provides Density(base.density * scale, fontScale = 1f)) {
            val D = OrganDesign
            Box(
                Modifier.align(Alignment.Center).offset(x = startInset.dp).size(D.W.dp, D.H.dp)
                    // clic hors du bloc Preset → ferme le menu (sans consommer l'événement)
                    .pointerInput(state.dropdownOpen) {
                        if (!state.dropdownOpen) return@pointerInput
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                val p = e.changes.firstOrNull() ?: continue
                                if (p.pressed && !p.previousPressed) {
                                    val x = p.position.x / density; val y = p.position.y / density
                                    val inPreset = x in D.X2..(D.X2 + D.COL2) && y in D.Y1..(D.Y1 + D.ROW1 + 220f)
                                    if (!inPreset) state.dropdownOpen = false
                                }
                            }
                        }
                    },
            ) {
                OctPanel(PanelRect(D.X1, D.Y1, D.COL1, D.ROW1), state)
                VibratoPanel(PanelRect(D.X1, D.Y2, D.COL1, D.ROW2), state)
                RotaryPanel(PanelRect(D.X1, D.Y3, D.COL1, D.ROW3 + D.GAP + D.ROW4), state)
                DrawbarsPanel(PanelRect(D.X2, D.Y2, D.COL2, D.ROW2 + D.GAP + D.ROW3 + D.GAP + D.ROW4), state)
                ModelPanel(PanelRect(D.X3, D.Y1, D.COL3, D.ROW1), state)
                EqPanel(PanelRect(D.X3, D.Y2, D.COL3, D.ROW2), state)
                DrivePanel(PanelRect(D.X3, D.Y3, D.COL3, D.ROW3), state)
                ReverbPanel(PanelRect(D.X3, D.Y4, D.COL3, D.ROW4), state)
                PresetsPanel(PanelRect(D.X2, D.Y1, D.COL2, D.ROW1), state) // en dernier : le menu déroulant passe au-dessus
            }
        }
    }
}
