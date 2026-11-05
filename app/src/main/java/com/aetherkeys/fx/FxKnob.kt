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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
 * Le potard de la fenêtre FX — **plat, à la Valhalla**.
 *
 * La référence a été MESURÉE, pas interprétée. Trois chiffres commandent tout le
 * dessin, pris sur `room_24.jpg` :
 *
 *  - le disque vaut **121 de luminance** (47 %) sur un panneau à **52** (20 %).
 *    Le rapport est de **2,3** : le disque est un gris MOYEN, pas un noir. C'est
 *    le point qu'une première version avait manqué — à 40 de luminance, le disque
 *    se confondait avec le panneau et le potard ne se lisait plus ;
 *  - il est **uniforme** sur tout son rayon : ni dôme, ni dégradé de corps, ni
 *    collerette. Ce sont ces couches qui donnaient l'aspect « 3D bricolée » ;
 *  - il n'y a **pas de liseré** : la transition disque → panneau est franche, en
 *    un pixel (121 puis 51). Un filet clair au bord était une invention.
 *
 * Ce qui fait lire le potard, c'est donc uniquement :
 *
 *  1. le disque : un aplat CLAIR, bien détaché du panneau ;
 *  2. l'index : une aiguille SOMBRE, du centre vers le bord (elle est sombre sur
 *     le disque clair, jamais blanche) ;
 *  3. la valeur : un **coin** plein, au bord, dans l'anneau — mesuré de rayon
 *     28 à 57 sur un disque de 54, soit de la moitié au bord, et large de ~25°.
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
            Canvas(Modifier.size(size.dp)) { drawFxKnob(value, accent, hot) }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label.uppercase(), color = if (hot) FxC.ink else FxC.dim,
            fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, maxLines = 1,
        )
        Spacer(Modifier.height(1.dp))
        Text(display, color = if (hot) FxC.ink else FxC.knobVal, fontSize = 11.sp, fontFamily = FxFonts.mono)
    }
}

/**
 * Le dessin d'un potard plat, dans un carré de rayon `r` centré.
 *
 * Extrait de [FxKnob] et [FxMagmaKnob] : les deux montrent la MÊME pièce, à deux
 * échelles. Deux copies auraient dérivé — c'est exactement ce qui s'était produit
 * entre le disque du gros potard et celui des petits.
 */
fun DrawScope.drawFxKnob(v0: Float, accent: Color, hot: Boolean) {
    val r = size.minDimension / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    val v = v0.coerceIn(0f, 1f)

    // ── 1. LE DISQUE. Un APLAT CLAIR, sans le moindre dégradé. Le rapport avec
    //    le panneau est celui de la référence : 121 sur 52, soit 2,3.
    val discR = r * .84f
    drawCircle(FxC.knobDisc, radius = discR, center = c)
    if (hot) {
        drawCircle(accent.copy(alpha = .22f), radius = discR, center = c)
    }

    // ── 2. LA PISTE : l'anneau creusé dans le panneau, juste hors du disque.
    //    Très sombre, SANS liseré clair — la référence n'en a pas.
    val ringR = r * .955f
    drawArc(
        color = FxC.knobTrack, startAngle = 135f, sweepAngle = 270f, useCenter = false,
        topLeft = Offset(c.x - ringR, c.y - ringR), size = Size(ringR * 2f, ringR * 2f),
        style = Stroke(width = r * .13f, cap = StrokeCap.Butt),
    )

    // ── 3. LA VALEUR : un COIN PLEIN posé sur l'anneau, à l'angle de la valeur.
    //    C'est la mesure : rayon 28 → 57 sur un disque de 54, largeur ~25°. Un
    //    arc d'épaisseur constante ne donnait pas ce coin.
    if (v > .012f) {
        val deg = 135f + v * 270f
        val ri = r * .52f
        val ro = r * 1.02f
        val half = 12.5f
        fun pt(d: Float, rad: Float) = Offset(
            c.x + rad * cos(Math.toRadians(d.toDouble())).toFloat(),
            c.y + rad * sin(Math.toRadians(d.toDouble())).toFloat(),
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(pt(deg - half, ri).x, pt(deg - half, ri).y)
            lineTo(pt(deg - half, ro).x, pt(deg - half, ro).y)
            lineTo(pt(deg + half, ro).x, pt(deg + half, ro).y)
            lineTo(pt(deg + half, ri).x, pt(deg + half, ri).y)
            close()
        }
        drawPath(path, accent)
    }

    // ── 4. L'INDEX : une aiguille SOMBRE du centre vers le bord. Sur un disque
    //    clair, c'est le sombre qui se voit — la référence fait exactement ça.
    val deg = 135f + v * 270f
    fun pt(rad: Float) = Offset(
        c.x + rad * cos(Math.toRadians(deg.toDouble())).toFloat(),
        c.y + rad * sin(Math.toRadians(deg.toDouble())).toFloat(),
    )
    drawLine(
        color = FxC.knobIndex, start = pt(r * .10f), end = pt(r * .68f),
        strokeWidth = r * .085f, cap = StrokeCap.Round,
    )
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

