package com.aetherkeys.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.ui.components.FaderThumb
import kotlin.math.cos
import kotlin.math.sin

/**
 * Le potard de la fenêtre FX — inspiré de Sunday Keys, avec une touche maison.
 *
 * Ce qui vient de Sunday Keys :
 *  - la **bague de valeur à l'EXTÉRIEUR** du corps, pas un arc collé au bord ;
 *  - la **jupe crantée** (une couronne de dents radiales) : c'est la signature
 *    visuelle de leurs potards, et c'est ce qui les fait lire « à tourner » ;
 *  - la lumière unique en haut à gauche, sur toutes les couches.
 *
 * La touche personnelle : le sommet du crantage porte une **gemme** — un point
 * d'accent avec son halo — là où l'index classique s'arrête. Un point qui
 * s'allume se repère plus vite qu'un trait, et il ne se confond pas avec la
 * bague quand la valeur approche 0 ou 1.
 *
 * LA VALEUR EST NORMALISÉE (0..1) : c'est elle qui dessine la bague et la
 * position de la gemme, l'hôte convertit vers la plage réelle.
 *
 * Le glissement est VERTICAL et RELATIF : 160 unités pour la plage entière. Une
 * échelle absolue rendrait les petites plages impossibles à régler.
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
    var hot by remember { mutableStateOf(false) }

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
                val c = Offset(this.size.width / 2f, this.size.height / 2f)
                val v = value.coerceIn(0f, 1f)
                val a0 = 135f
                val sweep = 270f
                fun pt(deg: Float, rad: Float): Offset {
                    val a = Math.toRadians(deg.toDouble())
                    return Offset(c.x + rad * cos(a).toFloat(), c.y + rad * sin(a).toFloat())
                }

                // ── 1. L'ombre portée, sous le corps seulement.
                drawCircle(
                    color = Color(0x8A000000), radius = r * .80f,
                    center = Offset(c.x, c.y + r * .10f),
                )

                // ── 2. La bague de valeur, à l'extérieur.
                val ringR = r * .93f
                drawArc(
                    color = Color(0x1AFFFFFF), startAngle = a0, sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(c.x - ringR, c.y - ringR),
                    size = Size(ringR * 2f, ringR * 2f),
                    style = Stroke(width = r * .085f, cap = StrokeCap.Round),
                )
                if (v > .008f) {
                    drawArc(
                        color = accent.copy(alpha = .30f), startAngle = a0, sweepAngle = v * sweep,
                        useCenter = false,
                        topLeft = Offset(c.x - ringR, c.y - ringR),
                        size = Size(ringR * 2f, ringR * 2f),
                        style = Stroke(width = r * .17f, cap = StrokeCap.Round),
                    )
                    drawArc(
                        brush = Brush.linearGradient(
                            listOf(accent.copy(alpha = .75f), accent),
                            start = pt(a0, ringR), end = pt(a0 + v * sweep, ringR),
                        ),
                        startAngle = a0, sweepAngle = v * sweep,
                        useCenter = false,
                        topLeft = Offset(c.x - ringR, c.y - ringR),
                        size = Size(ringR * 2f, ringR * 2f),
                        style = Stroke(width = r * .085f, cap = StrokeCap.Round),
                    )
                }

                // ── 3. Le socle sombre qui creuse le potard dans son logement.
                drawCircle(Color(0xFF0B0E15), radius = r * .76f, center = c)

                // ── 4. La jupe crantée : 40 dents, alternées clair / sombre.
                //    Deux tons plutôt qu'un seul : c'est ce qui donne la
                //    sensation de métal usiné, sans aucun dégradé par dent.
                val teeth = 40
                val rIn = r * .60f
                val rOut = r * .755f
                for (i in 0 until teeth) {
                    val deg = i * 360f / teeth
                    val lit = (deg < 200f && deg > 20f)
                    drawLine(
                        color = if (lit) Color(0xFF5A6072) else Color(0xFF2A2E3B),
                        start = pt(deg, rIn), end = pt(deg, rOut),
                        strokeWidth = r * .052f,
                    )
                }
                // Un liseré net en haut de la jupe : la lumière accroche l'arête.
                drawArc(
                    color = Color(0x3DFFFFFF), startAngle = 200f, sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(c.x - rOut, c.y - rOut),
                    size = Size(rOut * 2f, rOut * 2f),
                    style = Stroke(width = r * .03f),
                )

                // ── 5. Le corps bombé, lumière en haut à gauche.
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            Color(0xFF9BA2B6), Color(0xFF6E7488),
                            Color(0xFF4A4F62), Color(0xFF2E3342),
                        ),
                        center = Offset(c.x - r * .22f, c.y - r * .28f),
                        radius = r * .78f,
                    ),
                    radius = r * .605f, center = c,
                )
                // La gorge concentrique : un anneau fin qui donne l'épaisseur.
                drawCircle(
                    color = Color(0x33000000), radius = r * .50f, center = c,
                    style = Stroke(width = r * .022f),
                )
                // Le reflet de vitre, en haut à gauche.
                drawArc(
                    color = Color(0x40FFFFFF), startAngle = 205f, sweepAngle = 105f,
                    useCenter = false,
                    topLeft = Offset(c.x - r * .43f, c.y - r * .43f),
                    size = Size(r * .86f, r * .86f),
                    style = Stroke(width = r * .055f, cap = StrokeCap.Round),
                )

                // ── 6. La gemme : la touche personnelle, à la pointe de l'index.
                val deg = a0 + v * sweep
                drawLine(
                    color = Color(0x59000000), start = pt(deg, r * .18f), end = pt(deg, r * .52f),
                    strokeWidth = r * .085f, cap = StrokeCap.Round,
                )
                drawLine(
                    color = accent.copy(alpha = .95f),
                    start = pt(deg, r * .20f), end = pt(deg, r * .50f),
                    strokeWidth = r * .062f, cap = StrokeCap.Round,
                )
                val gem = pt(deg, r * .52f)
                drawCircle(accent.copy(alpha = .35f), radius = r * .17f, center = gem)
                drawCircle(accent, radius = r * .095f, center = gem)
                drawCircle(Color(0xCCFFFFFF), radius = r * .042f,
                    center = Offset(gem.x - r * .022f, gem.y - r * .022f))

                if (hot) {
                    drawCircle(
                        accent.copy(alpha = .45f), radius = r * .99f,
                        center = c, style = Stroke(r * .04f),
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
 * LE FADER HORIZONTAL D'UN RÉGLAGE — la forme de la maquette.
 *
 * Rail creusé sur toute la largeur, remplissage d'accent par la gauche, et un
 * bonnet rond qui glisse. Le bonnet est **FaderThumb** du mixer, réduit de 25 %
 * par rapport à la taille de la maquette (CAP_D 22 -> 16,5) : à 22 le bonnet
 * écrasait le rail et la rangée paraissait comprimée.
 *
 * `FaderThumb` est dessiné en **portrait** (1327/1657). Sa géométrie interne est
 * mise à l'échelle uniformément puis centrée, donc l'inscrire dans une boîte
 * carrée ne le déforme pas : il se contente d'y tenir. Le liseré et la LED
 * restent donc verticaux, comme sur une poignée réelle.
 */
private const val CAP_D = 16.5f          // 22 x 0,75
private const val RAIL_H = 8f

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
            Modifier.weight(1f).height((CAP_D + 6f).dp)
                .pointerInput(label) {
                    detectDragGestures(
                        onDragStart = { hot = true },
                        onDragEnd = { hot = false },
                        onDragCancel = { hot = false },
                    ) { change, _ ->
                        change.consume()
                        val usable = (size.width - CAP_D.dp.toPx()).coerceAtLeast(1f)
                        onChange(((change.position.x - CAP_D.dp.toPx() / 2f) / usable).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(label) { detectTapGestures(onDoubleTap = { onReset?.invoke() }) },
        ) {
            // Le rail : creusé, puis rempli d'accent jusqu'au bonnet.
            Canvas(Modifier.fillMaxSize()) {
                val cy = size.height / 2f
                val rh = RAIL_H.dp.toPx()
                val rr = rh / 2f
                val inner = rh - 6f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF2D3041), Color(0xFF474A63)),
                        startY = cy - rr, endY = cy + rr,
                    ),
                    topLeft = Offset(0f, cy - rr),
                    size = Size(size.width, rh),
                    cornerRadius = CornerRadius(rr),
                )
                drawRoundRect(
                    color = Color(0xFF090C13),
                    topLeft = Offset(3f, cy - inner / 2f),
                    size = Size(size.width - 6f, inner),
                    cornerRadius = CornerRadius(inner / 2f),
                )
                val fillW = (v * size.width - 6f).coerceAtLeast(0f)
                if (fillW > 1f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(accent.copy(alpha = .28f), accent.copy(alpha = .92f)),
                            startX = 3f, endX = 3f + fillW,
                        ),
                        topLeft = Offset(3f, cy - inner / 2f),
                        size = Size(fillW, inner),
                        cornerRadius = CornerRadius(inner / 2f),
                    )
                }
            }
            // Le bonnet, à sa position exacte sur le rail. `maxWidth` est en dp :
            // la course vaut la largeur moins le diamètre du bonnet.
            val capX = (maxWidth - CAP_D.dp) * v
            Box(
                Modifier.offset(x = capX).size(CAP_D.dp).align(Alignment.CenterStart),
            ) {
                FaderThumb(
                    accent = accent,
                    modifier = Modifier.fillMaxSize(),
                    glow = .3f + .7f * v,
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

