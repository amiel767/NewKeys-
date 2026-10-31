package com.aetherkeys.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Les icônes, gardées en TRACÉS SVG (chaînes de `path`), pas redessinées.
 *
 * C'est le même choix que les SVG isolés : le tracé vient de la maquette mot
 * pour mot, donc la forme ne dérive pas au fil des retouches. Elles sont
 * dessinées en trait (pas remplies), sur une grille 24 × 24, comme la référence.
 */
object AeFxIcons {
    const val BACK = "M15 18l-6-6 6-6"
    const val CLOSE = "M18 6L6 18|M6 6l12 12"
    const val PLUS = "M12 5v14|M5 12h14"
    const val SAVE = "M12 3.4v9.4|M7.6 8.8 12 13.2l4.4-4.4|M4.4 17.6h15.2"
    const val CHEV_DOWN = "M6 9l6 6 6-6"
    const val CHEV_UP = "M6 15l6-6 6 6"
    const val TRASH = "M4 7h16|M9 7V5.4a1.6 1.6 0 0 1 1.6-1.6h2.8A1.6 1.6 0 0 1 15 5.4V7|M6.5 7l1 12.2A1.8 1.8 0 0 0 9.3 21h5.4a1.8 1.8 0 0 0 1.8-1.8L17.5 7"
    const val STEP_PREV = "M15 18l-6-6 6-6"
    const val STEP_NEXT = "M9 6l6 6-6 6"
    const val POWER = "M15.4 14.2a13 13 0 1 0 17.2 0|M24 9.5v10.6"
    const val WAVE = "M2 12c2 0 2-4 4-4s2 8 4 8 2-8 4-8 2 4 4 4"
    const val SLIDERS = "M4 21v-7|M4 10V3|M12 21v-9|M12 8V3|M20 21v-5|M20 12V3|M1 14h6|M9 8h6|M17 16h6"
    const val ARP_UP = "M3 19h4v-4h4v-4h4V7h4"
    const val ARP_DOWN = "M3 5h4v4h4v4h4v4h4"
    const val ARP_UPDOWN = "M3 18l4-6 5-6 5 6 4 6"
    const val ARP_RANDOM = "M3 14l4-6 4 8 4-10 5 8"
}

/**
 * Dessine une icône. Le tracé est analysé à chaque appel — négligeable devant
 * une recomposition, et cela évite de garder un cache à invalider.
 *
 * `sw` est l'épaisseur du trait en unités de la grille 24 : la référence utilise
 * 2 par défaut et 2,1 pour l'icône d'enregistrement. Les tracés sont déjà écrits
 * sur la grille 24 × 24, donc il n'y a qu'à mettre à l'échelle.
 */
@Composable
fun FxIcon(
    icon: String,
    size: Dp = 14.dp,
    color: Color = FxC.dim,
    sw: Float = 2f,
) {
    Canvas(Modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
            icon.split('|').forEach { d ->
                drawPath(PathParser().parsePathString(d).toPath(), color, style = stroke)
            }
        }
    }
}
