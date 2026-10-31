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
import androidx.compose.ui.unit.dp
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
 * LE FADER D'UN RÉGLAGE — rail creusé, valeur remplie, bonnet bombé.
 *
 * Le rail est celui de la maquette : 11 unités de haut, une gorge sombre, et la
 * valeur qui remplit le creux dans l'accent. Le bonnet est un cercle de 22 unités,
 * bombé en huit plans, lumière en haut à gauche. Le libellé est à gauche (52
 * unités) et la valeur à droite (44), donc les rails s'alignent d'une ligne à
 * l'autre.
 */
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

    androidx.compose.foundation.layout.Row(
        Modifier.width(244.dp).height(26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.uppercase(), color = if (hot) FxC.ink else FxC.dim, fontSize = 7.5.sp,
            fontWeight = FontWeight.ExtraBold, letterSpacing = .8.sp, maxLines = 1,
            modifier = Modifier.width(52.dp),
        )
        Box(
            Modifier.weight(1f).height(26.dp)
                .pointerInput(label) {
                    detectDragGestures(
                        onDragStart = { hot = true },
                        onDragEnd = { hot = false },
                        onDragCancel = { hot = false },
                    ) { change, _ ->
                        change.consume()
                        val x = change.position.x
                        onChange((x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(label) { detectTapGestures(onDoubleTap = { onReset?.invoke() }) },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val railH = 11f
                val capD = 22f
                val midY = size.height / 2f
                // Le rail, puis la gorge, puis la valeur.
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(FxC.rail, Color(0xFF313443), Color(0xFF3A3C4F), Color(0xFF3E4155), FxC.rail2),
                    ),
                    topLeft = Offset(0f, midY - railH / 2f),
                    size = androidx.compose.ui.geometry.Size(size.width, railH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(railH / 2f),
                )
                val gutter = railH - 6f
                drawRoundRect(
                    color = Color(0xFF07090F),
                    topLeft = Offset(3f, midY - gutter / 2f),
                    size = androidx.compose.ui.geometry.Size(size.width - 6f, gutter),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(gutter / 2f),
                )
                val fillW = (size.width - 6f) * v
                if (fillW > 1f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(accent.copy(alpha = .25f), accent.copy(alpha = .9f))),
                        topLeft = Offset(3f, midY - gutter / 2f),
                        size = androidx.compose.ui.geometry.Size(fillW, gutter),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(gutter / 2f),
                    )
                }
                // Le bonnet.
                val capX = (size.width - capD) * v + capD / 2f
                drawCircle(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF7C8397), Color(0xFF5A6072), Color(0xFF41465A), Color(0xFF2E3344)),
                    ),
                    radius = capD / 2f, center = Offset(capX, midY),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF6C7387), Color(0xFF4B5164), Color(0xFF333849), Color(0xFF262B3B)),
                    ),
                    radius = capD * .37f, center = Offset(capX - 1f, midY - 1.5f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White, accent, accent.copy(alpha = .5f)),
                    ),
                    radius = capD * .12f, center = Offset(capX - 1f, midY - 2f),
                )
                if (hot) {
                    drawCircle(accent.copy(alpha = .65f), radius = capD / 2f + 1f,
                        center = Offset(capX, midY), style = Stroke(1.5f))
                }
            }
        }
        Text(
            display, color = if (hot) FxC.ink else Color(0xFF7E879C), fontSize = 9.sp,
            fontFamily = FxFonts.mono,
            modifier = Modifier.width(44.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}
