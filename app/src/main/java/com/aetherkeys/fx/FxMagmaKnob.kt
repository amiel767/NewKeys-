package com.aetherkeys.fx

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * LE GROS POTARD PRINCIPAL — la MÊME pièce que [FxKnob], à une autre échelle.
 *
 * C'est le point qui a demandé plusieurs passes. Une version intermédiaire
 * dessinait ici une « braise » : trois lobes qui tournaient, une lueur qui
 * grandissait avec la valeur. C'était une invention — la référence ne fait pas
 * deux sortes de potards. Chez Valhalla, TOUS les disques sont la même pièce
 * plate, le gros comme les petits ; ce qui le distingue, c'est uniquement sa
 * TAILLE et le fait qu'il porte le réglage principal.
 *
 * Le dessin est donc littéralement `drawFxKnob`, partagé avec les petits : un
 * disque plat, une aiguille sombre, un coin de valeur. Ce qui s'ajoute ici et
 * nulle part ailleurs :
 *
 *  - la teinte du preset ([core]) colore le coin de valeur et pose un voile
 *    TRÈS léger dans le disque, dosé par la valeur — assez pour que le preset se
 *    sente, trop peu pour casser l'aplat ;
 *  - l'anneau est plus épais, à proportion de la taille.
 *
 * La course va de 135° à 405° (270°), le glissement est VERTICAL et RELATIF, et
 * un double-tap remet la valeur par défaut.
 */
@Composable
fun FxMagmaKnob(
    label: String,
    value: Float,
    display: String,
    palette: FxPalette?,
    accent: Color,
    size: Int = FxGeo.KNOB_MAIN,
    onChange: (Float) -> Unit,
    onReset: (() -> Unit)? = null,
) {
    var drag by remember { mutableFloatStateOf(0f) }
    var hot by remember { mutableStateOf(false) }
    val v = value.coerceIn(0f, 1f)
    val core = palette?.core ?: accent

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size.dp)
                .pointerInput(label) {
                    detectDragGestures(
                        onDragStart = { hot = true; drag = value },
                        onDragEnd = { hot = false },
                        onDragCancel = { hot = false },
                    ) { change, amount ->
                        change.consume()
                        val next = (drag - amount.y / 200f * density).coerceIn(0f, 1f)
                        drag = next
                        onChange(next)
                    }
                }
                .pointerInput(label) { detectTapGestures(onDoubleTap = { onReset?.invoke() }) },
        ) {
            Canvas(Modifier.size(size.dp)) {
                val r = this.size.minDimension / 2f
                val c = Offset(this.size.width / 2f, this.size.height / 2f)
                // Le voile de preset : il ne s'installe que quand le réglage est
                // poussé, donc le disque reste plat au repos.
                if (v > .02f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(core.copy(alpha = .06f + .16f * v), Color.Transparent),
                            center = c, radius = r * .84f,
                        ),
                        radius = r * .84f, center = c,
                    )
                }
                drawFxKnob(v, core, hot)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label.uppercase(), color = if (hot) FxC.ink else FxC.dim,
            fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.2.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            display, color = FxC.ink, fontSize = 22.sp, fontFamily = FxFonts.mono,
            fontWeight = FontWeight.Bold, maxLines = 1,
        )
    }
}
