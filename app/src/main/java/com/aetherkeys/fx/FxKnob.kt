package com.aetherkeys.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.R
import com.soundstage.mixer.ui.components.FaderRail
import com.soundstage.mixer.ui.components.FaderThumb
import com.soundstage.mixer.ui.components.FaderThumbRatio
import kotlin.math.cos
import kotlin.math.sin

/**
 * Le potard de la fenêtre FX.
 *
 * LA VALEUR EST NORMALISÉE (0..1), comme dans la maquette : c'est elle qui
 * dessine l'arc et la position de l'index, et l'hôte convertit vers la plage
 * réelle. Ça évite de faire dépendre le dessin de l'unité du réglage.
 *
 * L'ARC VA DE 135° À 405° (270° de course), comme la référence. Le corps est
 * usiné : collerette cannelée, dôme, index lumineux, reflet de vitre. La lumière
 * vient d'en haut à gauche, comme le rail des pistes.
 *
 * Le glissement est VERTICAL et RELATIF : 160 unités de course pour la plage
 * entière. Une échelle absolue rendrait les petites plages impossibles à régler.
 * Un double-tap remet la valeur par défaut.
 */
@Composable
fun FxKnob(
    label: String,
    value: Float,
    accent: Color,
    display: String,
    size: Int = 44,
    onChange: (Float) -> Unit,
    onReset: (() -> Unit)? = null,
) {
    var drag by remember { mutableFloatStateOf(0f) }
    var hot by remember { androidx.compose.runtime.mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size.dp).pointerInput(label) {
                detectDragGestures(
                    onDragStart = { hot = true; drag = value },
                    onDragEnd = { hot = false },
                    onDragCancel = { hot = false },
                ) { change, amount ->
                    change.consume()
                    val next = (drag - amount.y / 160f * density).coerceIn(0f, 1f)
                    drag = next
                    onChange(next)
                }
            }.pointerInput(label) {
                detectTapGestures(onDoubleTap = { onReset?.invoke() })
            },
        ) {
            // Le corps est un VECTOR DRAWABLE (ic_fx_knob_body) : le relief 3D
            // vient de ses quatre dégradés, pas de cercles empilés en code.
            Image(
                painter = painterResource(R.drawable.ic_fx_knob_body),
                contentDescription = null,
                modifier = Modifier.size(size.dp),
            )
            // L'arc et l'index restent en Canvas : eux seuls dépendent de la
            // valeur, donc eux seuls doivent être redessinés au glissement.
            Canvas(Modifier.size(size.dp)) {
                val r = this.size.minDimension / 2f
                val cx = this.size.width / 2f
                val cy = this.size.height / 2f
                val v = value.coerceIn(0f, 1f)
                val a0 = 135f
                val sweep = 270f
                fun pt(deg: Float, rad: Float): Offset {
                    val a = Math.toRadians(deg.toDouble())
                    return Offset(cx + rad * cos(a).toFloat(), cy + rad * sin(a).toFloat())
                }
                // La piste, puis la valeur.
                drawArc(
                    color = Color(0x14FFFFFF), startAngle = a0, sweepAngle = sweep,
                    useCenter = false, topLeft = Offset(cx - r * .9f, cy - r * .9f),
                    size = androidx.compose.ui.geometry.Size(r * 1.8f, r * 1.8f),
                    style = Stroke(width = r * .052f, cap = StrokeCap.Round),
                )
                if (v > .012f) {
                    drawArc(
                        color = accent, startAngle = a0, sweepAngle = v * sweep,
                        useCenter = false, topLeft = Offset(cx - r * .9f, cy - r * .9f),
                        size = androidx.compose.ui.geometry.Size(r * 1.8f, r * 1.8f),
                        style = Stroke(width = r * .052f, cap = StrokeCap.Round),
                    )
                }
                // L'index, dans l'accent, avec sa pointe claire.
                val deg = a0 + v * sweep
                drawLine(
                    accent, pt(deg, r * .28f), pt(deg, r * .56f),
                    strokeWidth = r * .075f, cap = StrokeCap.Round,
                )
                drawLine(
                    Color(0xBFFFFFFF), pt(deg, r * .28f), pt(deg, r * .40f),
                    strokeWidth = r * .075f, cap = StrokeCap.Round,
                )
                if (hot) {
                    drawCircle(
                        accent.copy(alpha = .5f), radius = r * .95f,
                        center = Offset(cx, cy), style = Stroke(1.5f),
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label.uppercase(), color = if (hot) FxC.ink else FxC.dim,
            fontSize = 7.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, maxLines = 1,
        )
        Text(display, color = if (hot) FxC.ink else Color(0xFF7E879C), fontSize = 9.sp, fontFamily = FxFonts.mono)
    }
}


/**
 * LE FADER D'UN RÉGLAGE — les composants du MIXER, pas une imitation.
 *
 * Le rail est `FaderRail` (celui des pistes) et le bonnet est `FaderThumb`,
 * pris tels quels. Le rail est aminci : sa largeur d'usage dans le mixer est
 * 18 dp, on prend 60 % de cette valeur (RAIL_W), soit −40 % comme demandé.
 * `FaderRail` dérive toute son échelle de sa largeur, donc amincir la boîte
 * amincit l'ensemble (rainure, liserés, halo) sans rien d'autre à régler.
 *
 * La course du bonnet reprend celle du mixer : `CAP_TOP`..`CAP_BOTTOM`, avec
 * `capCenterY` qui inverse la valeur (1 en haut). Le libellé est sous le rail
 * et la valeur entre les deux, comme dans la maquette.
 */
private const val FADER_H = 128f
private const val FADER_W = 30f
private const val CAP_H = 34f
private const val CAP_W = CAP_H * FaderThumbRatio
private const val RAIL_W = 10.8f          // 18 dp (mixer) x 0,6 = −40 %
private const val CAP_TOP = 8f
private const val CAP_BOTTOM = FADER_H - CAP_H / 2f - 4f
private const val CAP_TRAVEL = CAP_BOTTOM - CAP_TOP

private fun capCenterY(v: Float) = CAP_TOP + (1f - v.coerceIn(0f, 1f)) * CAP_TRAVEL

@Composable
fun FxSlider(
    label: String,
    value: Float,
    accent: Color,
    display: String,
    onChange: (Float) -> Unit,
    onReset: (() -> Unit)? = null,
) {
    var hot by remember { androidx.compose.runtime.mutableStateOf(false) }
    val v = value.coerceIn(0f, 1f)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(FADER_W.dp)) {
        Box(
            Modifier.width(FADER_W.dp).height(FADER_H.dp)
                .pointerInput(label) {
                    detectDragGestures(
                        onDragStart = { hot = true },
                        onDragEnd = { hot = false },
                        onDragCancel = { hot = false },
                    ) { change, _ ->
                        change.consume()
                        // La position du doigt est ABSOLUE dans la boîte : on
                        // reprend exactement la course du bonnet.
                        val py = change.position.y / density
                        onChange((1f - ((py - CAP_TOP) / CAP_TRAVEL)).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(label) { detectTapGestures(onDoubleTap = { onReset?.invoke() }) },
        ) {
            // Le rail du mixer, aminci. Le bonnet est centré dessus.
            Box(Modifier.align(Alignment.TopCenter).width(RAIL_W.dp).fillMaxHeight()) {
                FaderRail(
                    accent = accent,
                    modifier = Modifier.fillMaxSize(),
                    level = v,
                    glow = .25f + .75f * v,
                )
            }
            val cy = capCenterY(v)
            Box(Modifier.align(Alignment.TopCenter).offset(y = (cy - CAP_H / 2f).dp).width(CAP_W.dp).height(CAP_H.dp)) {
                FaderThumb(accent = accent, modifier = Modifier.fillMaxSize(), glow = .3f + .7f * v)
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label.uppercase(), color = if (hot) FxC.ink else FxC.dim,
            fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .6.sp,
            maxLines = 1,
        )
        Text(
            display, color = if (hot) FxC.ink else Color(0xFF7E879C),
            fontSize = 8.5.sp, fontFamily = FxFonts.mono, maxLines = 1,
        )
    }
}

