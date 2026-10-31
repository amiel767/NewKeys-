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
import androidx.compose.ui.geometry.Size
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
 * Le potard de la fenêtre FX — **neumorphisme 3D**.
 *
 * Le neumorphisme repose sur une seule règle : la même matière que le panneau,
 * dont on sculpte le relief par **deux ombres opposées**. Une lumière vient du
 * haut-gauche, donc l'objet porte une ombre SOMBRE en bas-droite et un reflet
 * CLAIR en haut-gauche. Le socle est donc creusé (ombre interne en haut, reflet
 * interne en bas) et le corps est bombé (exactement l'inverse). C'est ce
 * contraste socle-creusé / corps-bombé qui fait lire « extrudé » et pas
 * « rond posé dessus ».
 *
 * Six couches, dans cet ordre :
 *  1. le puits : creux sombre, ombre interne en haut-gauche, reflet interne en bas ;
 *  2. l'anneau d'appui : une arête claire qui accroche la lumière en haut-gauche
 *     et sombre en bas-droite — c'est le contact du corps sur son socle ;
 *  3. le corps : dégradé linéaire diagonal, pas radial, pour que la lumière
 *     « roule » d'un bord à l'autre comme sur une surface convexe ;
 *  4. la collerette : deux anneaux concentriques, l'un sombre, l'un clair ;
 *  5. le dôme central avec son liseré ;
 *  6. l'index (fente creusée, lèvre claire) et la bille d'accent.
 *
 * L'ARC VA DE 135° À 405° (270° de course), comme la référence.
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
                // La lumière : tout part d'un seul point, en haut à gauche.
                val light = Offset(c.x - r * .30f, c.y - r * .34f)

                // ── 1. LE PUITS. C'est la seule couche qui dit « c'est creusé » :
                //    sombre en haut-gauche (la lumière n'y entre pas), clair en
                //    bas-droite (elle rebondit sur la paroi opposée).
                drawCircle(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF161A24), Color(0xFF1D222E)),
                        start = light, end = Offset(c.x + r * .4f, c.y + r * .4f),
                    ),
                    radius = r * .985f, center = c,
                )
                drawArc(
                    color = Color(0x8A000000), startAngle = 190f, sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(c.x - r * .94f, c.y - r * .94f),
                    size = Size(r * 1.88f, r * 1.88f),
                    style = Stroke(width = r * .16f),
                )
                drawArc(
                    color = Color(0x14FFFFFF), startAngle = 12f, sweepAngle = 156f,
                    useCenter = false,
                    topLeft = Offset(c.x - r * .90f, c.y - r * .90f),
                    size = Size(r * 1.80f, r * 1.80f),
                    style = Stroke(width = r * .10f),
                )

                // ── 2. LA PISTE DE VALEUR, dans le puits.
                val ringR = r * .845f
                drawArc(
                    color = Color(0x1FFFFFFF), startAngle = a0, sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(c.x - ringR, c.y - ringR),
                    size = Size(ringR * 2f, ringR * 2f),
                    style = Stroke(width = r * .075f, cap = StrokeCap.Round),
                )
                if (v > .008f) {
                    drawArc(
                        color = accent.copy(alpha = .22f), startAngle = a0, sweepAngle = v * sweep,
                        useCenter = false,
                        topLeft = Offset(c.x - ringR, c.y - ringR),
                        size = Size(ringR * 2f, ringR * 2f),
                        style = Stroke(width = r * .155f, cap = StrokeCap.Round),
                    )
                    drawArc(
                        brush = Brush.linearGradient(
                            listOf(accent.copy(alpha = .70f), accent),
                            start = pt(a0, ringR), end = pt(a0 + v * sweep, ringR),
                        ),
                        startAngle = a0, sweepAngle = v * sweep,
                        useCenter = false,
                        topLeft = Offset(c.x - ringR, c.y - ringR),
                        size = Size(ringR * 2f, ringR * 2f),
                        style = Stroke(width = r * .075f, cap = StrokeCap.Round),
                    )
                }

                // ── 3. L'ANNEAU D'APPUI : l'arête de contact du corps sur le socle.
                //    Deux arcs opposés, pas un cercle : la lumière n'éclaire qu'un
                //    côté et ne fait que frôler l'autre.
                val seatR = r * .70f
                drawArc(
                    color = Color(0x99FFFFFF), startAngle = 186f, sweepAngle = 168f,
                    useCenter = false,
                    topLeft = Offset(c.x - seatR, c.y - seatR),
                    size = Size(seatR * 2f, seatR * 2f),
                    style = Stroke(width = r * .035f),
                )
                drawArc(
                    color = Color(0x8A000000), startAngle = 8f, sweepAngle = 164f,
                    useCenter = false,
                    topLeft = Offset(c.x - seatR, c.y - seatR),
                    size = Size(seatR * 2f, seatR * 2f),
                    style = Stroke(width = r * .035f),
                )

                // ── 4. LE CORPS. Dégradé LINÉAIRE diagonal, du clair en haut-gauche
                //    au sombre en bas-droite : c'est ce qui donne la convexité.
                val bodyR = r * .675f
                drawCircle(
                    brush = Brush.linearGradient(
                        listOf(
                            Color(0xFF8E96AC), Color(0xFF6B7288),
                            Color(0xFF474D60), Color(0xFF2A2F3E),
                        ),
                        start = light, end = Offset(c.x + r * .5f, c.y + r * .5f),
                    ),
                    radius = bodyR, center = c,
                )

                // ── 5. LA COLLERETTE : deux anneaux concentriques, la gorge sombre
                //    puis son arête claire. C'est l'usinage, vu de dessus.
                drawCircle(
                    color = Color(0x4D000000), radius = r * .575f, center = c,
                    style = Stroke(width = r * .022f),
                )
                drawCircle(
                    color = Color(0x2EFFFFFF), radius = r * .545f, center = c,
                    style = Stroke(width = r * .014f),
                )

                // ── 6. LE DÔME central, bombé dans l'autre sens que le corps.
                val domeR = r * .485f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            Color(0xFF9BA3B9), Color(0xFF737A90),
                            Color(0xFF4C5265), Color(0xFF2E3342),
                        ),
                        center = Offset(c.x - r * .20f, c.y - r * .24f),
                        radius = domeR * 1.85f,
                    ),
                    radius = domeR, center = c,
                )
                drawArc(
                    color = Color(0x4DFFFFFF), startAngle = 200f, sweepAngle = 118f,
                    useCenter = false,
                    topLeft = Offset(c.x - domeR * .82f, c.y - domeR * .82f),
                    size = Size(domeR * 1.64f, domeR * 1.64f),
                    style = Stroke(width = r * .045f, cap = StrokeCap.Round),
                )

                // ── 7. L'INDEX : une fente CREUSÉE (ombre d'abord, puis la lèvre
                //    claire sur son bord haut), avec la bille d'accent au bout.
                val deg = a0 + v * sweep
                drawLine(
                    color = Color(0xA6000000), start = pt(deg, r * .16f), end = pt(deg, r * .50f),
                    strokeWidth = r * .105f, cap = StrokeCap.Round,
                )
                drawLine(
                    color = accent.copy(alpha = .92f),
                    start = pt(deg, r * .19f), end = pt(deg, r * .47f),
                    strokeWidth = r * .060f, cap = StrokeCap.Round,
                )
                val lip = pt(deg - 7f, r * .19f)
                drawLine(
                    color = Color(0x66FFFFFF), start = lip,
                    end = pt(deg - 7f, r * .45f),
                    strokeWidth = r * .022f, cap = StrokeCap.Round,
                )
                val tip = pt(deg, r * .505f)
                drawCircle(Color(0x8A000000), radius = r * .105f,
                    center = Offset(tip.x + r * .012f, tip.y + r * .016f))
                drawCircle(accent, radius = r * .080f, center = tip)
                drawCircle(Color(0xCCFFFFFF), radius = r * .030f,
                    center = Offset(tip.x - r * .022f, tip.y - r * .024f))

                if (hot) {
                    drawCircle(
                        accent.copy(alpha = .40f), radius = r * .99f,
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
private const val RAIL_T = 12.6f    // 18 x 0,70 : le rail des pistes, amaigri de 30 %
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
            // Le rail des pistes, couché. L'ancre doit être la MÊME que celle du
            // bonnet : sans `align`, la bande place le rail en HAUT (26 dp de
            // bande pour 12,6 dp de rail) pendant que le bonnet est centré — d'où
            // l'écart d'une demi-différence de hauteur entre les deux.
            Box(
                Modifier.align(Alignment.CenterStart)
                    .size(width = maxWidth, height = RAIL_T.dp)
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
            // Le bonnet. Comme dans le master, la boîte EXTÉRIEURE garde son
            // empreinte couchée (capLong x capShort) et n'est PAS transposée :
            // seule la boîte intérieure l'est, puis tournée. Transposer aussi la
            // boîte extérieure décalait le bonnet d'une vingtaine d'unités.
            Box(
                Modifier.align(Alignment.CenterStart)
                    .offset(x = travel * v)
                    .size(CAP_LONG.dp, CAP_SHORT.dp),
            ) {
                Box(
                    Modifier.size(CAP_LONG.dp, CAP_SHORT.dp)
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
        }
        Text(
            display, color = if (hot) FxC.ink else Color(0xFF7E879C), fontSize = 9.sp,
            fontFamily = FxFonts.mono, maxLines = 1,
            modifier = Modifier.width(44.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

