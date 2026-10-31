package com.aetherkeys.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.soundstage.mixer.ui.components.FaderRail
import com.soundstage.mixer.ui.components.FaderThumb
import com.soundstage.mixer.ui.components.v2.swapAxes
import androidx.compose.ui.unit.sp
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
                // La collerette, le corps, le dôme.
                drawCircle(Color(0xFF2A2E40), radius = r * .72f, center = Offset(cx, cy))
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF767D92), Color(0xFF525869), Color(0xFF373C4E), Color(0xFF212538)),
                        center = Offset(cx - r * .2f, cy - r * .3f), radius = r * 1.1f,
                    ),
                    radius = r * .61f, center = Offset(cx, cy),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF959CB2), Color(0xFF565C70), Color(0xFF282C3E)),
                        center = Offset(cx - r * .18f, cy - r * .3f), radius = r * .9f,
                    ),
                    radius = r * .42f, center = Offset(cx, cy),
                )
                drawCircle(Color(0x24FFFFFF), radius = r * .31f, center = Offset(cx, cy), style = Stroke(1f))
                // L'index, dans l'accent.
                val deg = a0 + v * sweep
                val p1 = pt(deg, r * .28f)
                val p2 = pt(deg, r * .56f)
                drawLine(accent, p1, p2, strokeWidth = r * .075f, cap = StrokeCap.Round)
                drawLine(Color(0xBFFFFFFF), pt(deg, r * .28f), pt(deg, r * .40f),
                    strokeWidth = r * .075f, cap = StrokeCap.Round)
                // Le reflet de vitre.
                drawOval(
                    brush = Brush.linearGradient(listOf(Color(0x4DFFFFFF), Color.Transparent)),
                    topLeft = Offset(cx - r * .5f, cy - r * .55f),
                    size = androidx.compose.ui.geometry.Size(r * .62f, r * .34f),
                )
                if (hot) {
                    drawCircle(accent.copy(alpha = .5f), radius = r * .95f,
                        center = Offset(cx, cy), style = Stroke(1.5f))
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
 * LE FADER HORIZONTAL D'UN RÉGLAGE — le montage du **MasterSlider** de la bottom bar.
 *
 * Ce n'est pas une imitation de rail : ce sont les **mêmes composants** que les
 * pistes du mixer, montés de la même façon que le master horizontal.
 * `FaderRail` et `FaderThumb` sont verticaux par construction ; on leur donne une
 * empreinte horizontale avec `swapAxes()` puis on les tourne de 90° autour du
 * centre. C'est exactement ce que fait `MasterSliderV2` — rail 18 dp d'épaisseur,
 * bonnet couché le long du rail, remplissage qui part de la gauche.
 *
 * Deux pièges repris du master :
 *  - `swapAxes()` échange les contraintes AVANT la rotation, sinon le bonnet est
 *    rogné : il est mesuré dans la boîte couchée, pas dans la boîte debout ;
 *  - la course vaut `largeur - capLong` : centrer le bonnet sur la position
 *    pousserait la moitié de sa largeur hors du rail aux deux extrémités.
 */
private const val RAIL_T = 18f      // épaisseur du rail des pistes
private const val CAP_LONG = 22.5f  // 30 x 0,75 : le bonnet du master, réduit de 25 %
private const val CAP_SHORT = 18f   // 24 x 0,75

@Composable
fun FxSlider(
    label: String,
    value: Float,
    accent: Color,
    display: String,
    onChange: (Float) -> Unit,
    onReset: (() -> Unit)? = null,
) {
    var hot by remember { mutableStateOf(false) }
    val v = value.coerceIn(0f, 1f)

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            label.uppercase(), color = if (hot) FxC.ink else FxC.dim, fontSize = 7.5.sp,
            fontWeight = FontWeight.ExtraBold, letterSpacing = .8.sp, maxLines = 1,
            modifier = Modifier.width(52.dp),
        )
        BoxWithConstraints(
            Modifier.weight(1f).height((CAP_SHORT + 8f).dp)
                .pointerInput(label) {
                    detectHorizontalDragGestures(
                        onDragStart = { hot = true },
                        onDragEnd = { hot = false },
                        onDragCancel = { hot = false },
                    ) { change, delta ->
                        change.consume()
                        val travel = (size.width - CAP_LONG.dp.toPx()).coerceAtLeast(1f)
                        onChange((v + delta / travel).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(label) {
                    detectTapGestures(
                        onPress = {
                            hot = true
                            tryAwaitRelease()
                            hot = false
                        },
                        onTap = { o ->
                            val travel = (size.width - CAP_LONG.dp.toPx()).coerceAtLeast(1f)
                            onChange(((o.x - CAP_LONG.dp.toPx() / 2f) / travel).coerceIn(0f, 1f))
                        },
                        onDoubleTap = { onReset?.invoke() },
                    )
                },
        ) {
            val travel = maxWidth - CAP_LONG.dp
            // Le rail des pistes, couché : swapAxes() puis rotation 90°.
            Box(
                Modifier.size(width = maxWidth, height = RAIL_T.dp)
                    .swapAxes()
                    .graphicsLayer(rotationZ = 90f, transformOrigin = TransformOrigin(0.5f, 0.5f)),
            ) {
                FaderRail(
                    accent = accent,
                    modifier = Modifier.fillMaxSize(),
                    level = v,
                    glow = if (hot) .85f else .35f,
                )
            }
            // Le bonnet des pistes, couché, à sa position sur le rail.
            Box(
                Modifier.align(Alignment.CenterStart)
                    .offset(x = travel * v)
                    .size(CAP_LONG.dp, CAP_SHORT.dp)
                    .swapAxes()
                    .graphicsLayer(rotationZ = 90f, transformOrigin = TransformOrigin(0.5f, 0.5f)),
            ) {
                FaderThumb(
                    accent = accent,
                    modifier = Modifier.fillMaxSize(),
                    glow = if (hot) 1f else .6f,
                    showShadow = false,
                )
            }
        }
        Text(
            display, color = if (hot) FxC.ink else Color(0xFF7E879C), fontSize = 9.sp,
            fontFamily = FxFonts.mono, maxLines = 1,
            modifier = Modifier.width(44.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

