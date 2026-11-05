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
 * La référence a été MESURÉE, pas interprétée : dans une capture Valhalla, le
 * disque du potard vaut 121 de luminance quand le panneau vaut 52, et il est
 * **uniforme** sur tout son rayon. Il n'y a donc ni dôme, ni dégradé de corps,
 * ni collerette : ces couches donnaient l'aspect « 3D bricolée » que la
 * référence n'a pas. Ce qui fait lire le potard, c'est uniquement :
 *
 *  1. le disque : un aplat plus clair que le panneau, sans dégradé ;
 *  2. le liseré : un filet fin, un peu plus clair, sur le bord du disque ;
 *  3. la piste : l'arc creusé, très sombre ;
 *  4. la valeur : un arc d'accent, fin, qui part de 135° ;
 *  5. l'index : une aiguille du centre vers le bord, avec sa bille.
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

                // ── 1. LE DISQUE. Un APLAT, sans le moindre dégradé : c'est la
                //    mesure de la référence (121 uniforme sur un panneau à 52).
                //    Le disque est plus clair que le panneau, donc il se détache
                //    par le contraste, pas par un relief.
                val discR = r * .80f
                drawCircle(Color(0xFF2A2F3C), radius = discR, center = c)

                // ── 2. LE LISERÉ : un filet fin, un peu plus clair, sur le bord.
                //    C'est lui, et l'index, qui donnent toute la lecture.
                drawCircle(
                    color = Color(0x30FFFFFF), radius = discR - r * .012f, center = c,
                    style = Stroke(width = r * .024f),
                )
                drawCircle(
                    color = Color(0x5A000000), radius = discR + r * .010f, center = c,
                    style = Stroke(width = r * .020f),
                )

                // ── 3. LA PISTE DE VALEUR : l'arc creusé, dans le panneau, juste
                //    hors du disque. Très sombre, sans liseré clair.
                val ringR = r * .93f
                drawArc(
                    color = Color(0x66000000), startAngle = a0, sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(c.x - ringR, c.y - ringR),
                    size = Size(ringR * 2f, ringR * 2f),
                    style = Stroke(width = r * .055f, cap = StrokeCap.Round),
                )

                // ── 4. LA VALEUR : l'arc d'accent, FIN. La référence ne pose pas
                //    de halo : un trait net qui part de 135° et s'arrête à la
                //    valeur. Une version épaisse et lumineuse faisait « jouet ».
                if (v > .008f) {
                    drawArc(
                        color = accent, startAngle = a0, sweepAngle = v * sweep,
                        useCenter = false,
                        topLeft = Offset(c.x - ringR, c.y - ringR),
                        size = Size(ringR * 2f, ringR * 2f),
                        style = Stroke(width = r * .055f, cap = StrokeCap.Round),
                    )
                }

                // ── 5. L'INDEX : une aiguille rectiligne du centre vers le bord,
                //    blanche, avec la bille au bout. Rien de creusé.
                val deg = a0 + v * sweep
                drawLine(
                    color = Color(0xFFE8ECF5), start = pt(deg, r * .16f), end = pt(deg, r * .70f),
                    strokeWidth = r * .075f, cap = StrokeCap.Round,
                )
                val tip = pt(deg, r * .70f)
                drawCircle(Color(0xFFE8ECF5), radius = r * .085f, center = tip)

                if (hot) {
                    drawCircle(
                        accent.copy(alpha = .55f), radius = discR + r * .02f, center = c,
                        style = Stroke(r * .035f),
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

