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
 * LE GROS POTARD PRINCIPAL — le cœur de la page Reverb.
 *
 * Il remplace l'orbe. Sa matière est un **magma** : un noyau incandescent dont
 * le rayon, la luminosité et le mouvement suivent la valeur. À zéro le noyau est
 * une braise ; à fond, il emplit le puits. Trois lobes tournent lentement autour
 * du centre, ce qui donne le bouillonnement sans jamais redessiner une texture.
 *
 * Le relief est celui des potards de la fenêtre : puits creusé, corps bombé,
 * collerette, index. Le CONTOUR BRILLE : un anneau balayé par un dégradé
 * `sweepGradient` qui mêle le liseré, la teinte secondaire et le cœur, plus un
 * arc clair en haut-gauche là où tombe la lumière.
 *
 * La course va de 135° à 405° (270°), comme les autres potards ; le glissement
 * est VERTICAL et RELATIF, et un double-tap remet la valeur par défaut.
 */
@Composable
fun FxMagmaKnob(
    label: String,
    value: Float,
    display: String,
    palette: FxPalette?,
    accent: Color,
    size: Int = 206,
    onChange: (Float) -> Unit,
    onReset: (() -> Unit)? = null,
) {
    var drag by remember { mutableFloatStateOf(0f) }
    var hot by remember { mutableStateOf(false) }
    val v = value.coerceIn(0f, 1f)
    val shown by animateFloatAsState(v, tween(180), label = "magmaValue")
    // Un tour complet en neuf secondes : assez lent pour ne pas distraire, assez
    // vif pour que le noyau « vive » même immobile.
    val churn by rememberInfiniteTransition(label = "magma").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "churn",
    )
    val core = palette?.core ?: accent
    val rim = palette?.rim ?: accent
    val second = palette?.let { Color.hsv(it.h2, it.s / 100f, it.l / 100f) } ?: accent

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
                drawMagma(this, shown, churn, core, rim, second, hot)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label.uppercase(), color = if (hot) FxC.ink else FxC.dim,
            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            display, color = FxC.ink, fontSize = 15.sp, fontFamily = FxFonts.mono,
            fontWeight = FontWeight.Bold, maxLines = 1,
        )
    }
}

/** Le dessin complet du magma, dans un carré de rayon [r] centré. */
private fun drawMagma(
    ds: DrawScope,
    v: Float,
    phase: Float,
    core: Color,
    rim: Color,
    second: Color,
    hot: Boolean,
) {
    val r = ds.size.minDimension / 2f
    val c = Offset(ds.size.width / 2f, ds.size.height / 2f)
    fun pt(deg: Float, rad: Float): Offset {
        val a = Math.toRadians(deg.toDouble())
        return Offset(c.x + rad * cos(a).toFloat(), c.y + rad * sin(a).toFloat())
    }

    // ── 1. LE HALO : la lumière que le magma jette hors du puits. Il grandit
    //    avec la valeur, sinon un réglage bas et un réglage haut se ressemblent.
    ds.drawCircle(
        brush = Brush.radialGradient(
            listOf(core.copy(alpha = .10f + .26f * v), Color.Transparent),
            center = c, radius = r * 1.06f,
        ),
        radius = r * 1.06f, center = c,
    )

    // ── 2. LE CONTOUR QUI BRILLE. Un anneau balayé : le dégradé fait le tour, si
    //    bien que le bord n'a jamais la même teinte des deux côtés. C'est ce qui
    //    le fait lire « allumé » et pas « cerclé de gris ».
    val ringR = r * .93f
    ds.drawCircle(
        brush = Brush.sweepGradient(listOf(rim, second, core, rim), center = c),
        radius = ringR, center = c, style = Stroke(r * .085f),
    )
    // L'arc clair, en haut-gauche : la lumière qui accroche le métal.
    ds.drawArc(
        color = Color.White.copy(alpha = .20f + .18f * v),
        startAngle = 148f, sweepAngle = 116f, useCenter = false,
        topLeft = Offset(c.x - ringR, c.y - ringR),
        size = Size(ringR * 2f, ringR * 2f),
        style = Stroke(r * .085f, cap = StrokeCap.Round),
    )

    // ── 3. LE PUITS. Sombre en haut-gauche, plus clair en bas-droite : la paroi
    //    opposée renvoie la lumière.
    ds.drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFF090C14), Color(0xFF161C28)),
            center = Offset(c.x - r * .22f, c.y - r * .26f), radius = r * .92f,
        ),
        radius = r * .86f, center = c,
    )

    // ── 4. LE CORPS. Dégradé linéaire diagonal, pas radial : la lumière roule
    //    d'un bord à l'autre comme sur une surface convexe.
    val bodyR = r * .78f
    ds.drawCircle(
        brush = Brush.linearGradient(
            listOf(Color(0xFF2C3446), Color(0xFF161B27)),
            start = Offset(c.x - r * .55f, c.y - r * .62f),
            end = Offset(c.x + r * .58f, c.y + r * .66f),
        ),
        radius = bodyR, center = c,
    )

    // ── 5. LE MAGMA. Le rayon suit la valeur : c'est la lecture immédiate du
    //    réglage, avant même l'index. Les lobes tournent, la braise respire.
    //    Le noyau reste CONTENU : c'est une braise dans un puits, pas une lampe.
    //    Une version plus large et plus blanche emplissait le dôme et effaçait
    //    le relief.
    val mr = r * (.14f + .26f * v)
    ds.drawCircle(
        brush = Brush.radialGradient(
            listOf(core.copy(alpha = .50f), second.copy(alpha = .22f), Color.Transparent),
            center = c, radius = mr * 1.9f,
        ),
        radius = mr * 1.9f, center = c,
    )
    for (k in 0 until 3) {
        val a = phase * 2f * PI.toFloat() + k * (2f * PI.toFloat() / 3f)
        val wob = 1f + .18f * sin(a * 2f)
        val lobe = Offset(c.x + cos(a) * mr * .42f, c.y + sin(a) * mr * .42f)
        ds.drawCircle(
            brush = Brush.radialGradient(
                listOf(second.copy(alpha = .26f + .14f * v), Color.Transparent),
                center = lobe, radius = mr * wob * 1.1f,
            ),
            radius = mr * wob * 1.1f, center = lobe,
        )
    }
    // Le noyau chaud : petit, et il blanchit seulement quand la valeur monte.
    val hotC = Offset(c.x - mr * .10f, c.y - mr * .14f)
    ds.drawCircle(
        brush = Brush.radialGradient(
            listOf(
                Color.White.copy(alpha = .10f + .28f * v),
                core.copy(alpha = .70f),
                Color.Transparent,
            ),
            center = hotC, radius = mr * .95f,
        ),
        radius = mr * .95f, center = hotC,
    )

    // ── 6. LA COLLERETTE du corps : une arête claire qui sépare le dôme du puits.
    ds.drawCircle(
        brush = Brush.linearGradient(
            listOf(Color(0x33FFFFFF), Color(0x00FFFFFF), Color(0x44000000)),
            start = Offset(c.x - r * .5f, c.y - r * .55f),
            end = Offset(c.x + r * .5f, c.y + r * .6f),
        ),
        radius = bodyR, center = c, style = Stroke(r * .016f),
    )

    // ── 7. L'INDEX : fente creusée avec sa lèvre claire, et la bille au bout.
    val deg = 135f + 270f * v
    ds.drawLine(
        color = Color(0xAA000000), start = pt(deg + 2.2f, r * .40f), end = pt(deg + 2.2f, r * .80f),
        strokeWidth = r * .040f, cap = StrokeCap.Round,
    )
    ds.drawLine(
        color = core, start = pt(deg, r * .42f), end = pt(deg, r * .78f),
        strokeWidth = r * .030f, cap = StrokeCap.Round,
    )
    val tip = pt(deg, r * .80f)
    ds.drawCircle(Color(0x8A000000), radius = r * .052f,
        center = Offset(tip.x + r * .008f, tip.y + r * .010f))
    ds.drawCircle(core, radius = r * .040f, center = tip)
    ds.drawCircle(Color.White.copy(alpha = .75f), radius = r * .014f,
        center = Offset(tip.x - r * .010f, tip.y - r * .012f))

    if (hot) {
        ds.drawCircle(
            core.copy(alpha = .35f), radius = r * .99f, center = c, style = Stroke(r * .035f),
        )
    }
}
