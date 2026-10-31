package com.aetherkeys.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * La vignette d'une carte.
 *
 * Les courbes sont celles de la maquette, mot pour mot, tracées dans un cadre
 * 56 × 44 étiré à la boîte (`preserveAspectRatio="none"`). Le trait porte
 * l'accent quand l'effet est engagé, le gris sinon — et la surface sous la
 * courbe se remplit d'un dégradé de cet accent.
 */
private val GRAPHS = mapOf(
    "Delay" to "M4 34h10c6 0 6-22 12-22s6 22 12 22h8",
    "Equalizer" to "M4 34c8 0 8-22 16-22s8 22 16 22",
    "Compressor" to "M4 12c10 0 12 22 22 22",
    "Maximizer" to "M4 30c10 0 14 4 22 4",
)

/** Les hauteurs des 16 barres du motif MIDI, de la maquette. */
private val STEP_HEIGHTS = listOf(
    .9f, .6f, .75f, .5f, 1f, .55f, .7f, .45f,
    .85f, .6f, .8f, .5f, .95f, .55f, .7f, .45f,
)

@Composable
fun FxMiniGraph(type: String, accent: Color, on: Boolean) {
    // Un effet MIDI montre ses pas, pas une courbe.
    if (type.equals("MIDI", ignoreCase = true) || type.equals("Arp", ignoreCase = true)) {
        FxMiniSteps(accent, on)
        return
    }
    val d = GRAPHS[type] ?: GRAPHS.getValue("Equalizer")
    val ink = if (on) accent else FxC.mute
    Canvas(Modifier.fillMaxSize()) {
        val sx = size.width / 56f
        val sy = size.height / 44f
        val p = PathParser().parsePathString(d).toPath()
        val b = p.getBounds()
        // La surface fermée : la courbe, puis on redescend aux deux coins.
        val fill = Path().apply {
            addPath(p)
            lineTo(b.right, 44f)
            lineTo(4f, 44f)
            close()
        }
        scale(sx, sy, pivot = Offset.Zero) {
            drawPath(
                fill,
                brush = Brush.verticalGradient(listOf(accent.copy(alpha = .32f), accent.copy(alpha = 0f))),
            )
            drawPath(p, ink, style = Stroke(width = 1.6f, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun FxMiniSteps(accent: Color, on: Boolean) {
    Row(
        Modifier.fillMaxSize().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        STEP_HEIGHTS.forEach { v ->
            Box(
                Modifier.weight(1f).fillMaxHeight(v)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (on) Brush.verticalGradient(listOf(accent, accent.copy(alpha = .45f)))
                        else Brush.verticalGradient(listOf(Color(0xFF2A3040), Color(0xFF2A3040)))
                    ),
            )
        }
    }
}
