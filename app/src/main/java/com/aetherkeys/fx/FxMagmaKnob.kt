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
 * Il remplace l'orbe. Sa construction suit la MÊME mesure que [FxKnob] : un
 * disque APLAT, sans dôme ni dégradé de corps — c'est ce que montre la référence
 * Valhalla (disque uniforme à 121 sur un panneau à 52). Le « magma » n'est donc
 * plus un relief : c'est une lueur COLORÉE qui emplit le disque, dont l'intensité
 * et le rayon suivent la valeur. À zéro c'est une braise, à fond le disque
 * s'embrase. Trois lobes tournent lentement, ce qui donne le bouillonnement.
 *
 * Par-dessus, comme le petit potard : un liseré fin, l'arc de valeur, et
 * l'aiguille blanche avec sa bille. Rien de creusé, rien de bombé.
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
                drawMagma(this, shown, churn, core, second, hot)
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

    second: Color,
    hot: Boolean,
) {
    val r = ds.size.minDimension / 2f
    val c = Offset(ds.size.width / 2f, ds.size.height / 2f)
    fun pt(deg: Float, rad: Float): Offset {
        val a = Math.toRadians(deg.toDouble())
        return Offset(c.x + rad * cos(a).toFloat(), c.y + rad * sin(a).toFloat())
    }

    // ── 1. LE DISQUE PLAT. Même matière que les petits potards : un aplat
    //    légèrement plus clair que le panneau, sans dégradé. C'est le fond du
    //    « puits » dans lequel la lueur s'installe.
    val discR = r * .80f
    ds.drawCircle(Color(0xFF232836), radius = discR, center = c)

    // ── 2. LA BRAISE : elle reste CONTENUE au centre du disque. La remplir
    //    jusqu'au bord redonnait exactement le « dôme » qu'on vient de retirer
    //    (mesuré : 43 au bord contre 164 au centre, un dégradé de corps). Une
    //    braise serrée laisse le disque se lire PLAT, et c'est la valeur qu'on
    //    lit dans son rayon, pas un relief.
    ds.drawCircle(
        brush = Brush.radialGradient(
            listOf(core.copy(alpha = .10f + .16f * v), Color.Transparent),
            center = c, radius = discR * .62f,
        ),
        radius = discR * .62f, center = c,
    )
    val mr = discR * (.16f + .30f * v)
    ds.drawCircle(
        brush = Brush.radialGradient(
            listOf(core.copy(alpha = .34f + .30f * v), second.copy(alpha = .16f), Color.Transparent),
            center = c, radius = mr,
        ),
        radius = mr, center = c,
    )
    // Les trois lobes : ils tournent lentement, donc la lueur « vit ».
    for (k in 0 until 3) {
        val a = phase * 2f * PI.toFloat() + k * (2f * PI.toFloat() / 3f)
        val wob = 1f + .18f * sin(a * 2f)
        val lobe = Offset(c.x + cos(a) * mr * .34f, c.y + sin(a) * mr * .34f)
        ds.drawCircle(
            brush = Brush.radialGradient(
                listOf(second.copy(alpha = .20f + .16f * v), Color.Transparent),
                center = lobe, radius = mr * wob * .95f,
            ),
            radius = mr * wob * .95f, center = lobe,
        )
    }

    // ── 3. LE LISERÉ, comme les petits potards : un filet clair sur le bord,
    //    et son ombre externe. C'est ce qui détache le disque du panneau.
    ds.drawCircle(
        color = Color(0x3AFFFFFF), radius = discR - r * .012f, center = c,
        style = Stroke(r * .026f),
    )
    ds.drawCircle(
        color = Color(0x5A000000), radius = discR + r * .012f, center = c,
        style = Stroke(r * .022f),
    )

    // ── 4. L'ARC DE VALEUR, juste hors du disque, FIN et net.
    val ringR = r * .93f
    ds.drawArc(
        color = Color(0x66000000), startAngle = 135f, sweepAngle = 270f, useCenter = false,
        topLeft = Offset(c.x - ringR, c.y - ringR), size = Size(ringR * 2f, ringR * 2f),
        style = Stroke(r * .055f, cap = StrokeCap.Round),
    )
    if (v > .008f) {
        ds.drawArc(
            color = core, startAngle = 135f, sweepAngle = v * 270f, useCenter = false,
            topLeft = Offset(c.x - ringR, c.y - ringR), size = Size(ringR * 2f, ringR * 2f),
            style = Stroke(r * .055f, cap = StrokeCap.Round),
        )
    }

    // ── 5. L'INDEX : l'aiguille blanche du centre vers le bord, avec sa bille.
    val deg = 135f + 270f * v
    ds.drawLine(
        color = Color(0xFFE8ECF5), start = pt(deg, r * .16f), end = pt(deg, r * .70f),
        strokeWidth = r * .075f, cap = StrokeCap.Round,
    )
    val tip = pt(deg, r * .70f)
    ds.drawCircle(Color(0xFFE8ECF5), radius = r * .085f, center = tip)

    if (hot) {
        ds.drawCircle(
            core.copy(alpha = .55f), radius = discR + r * .02f, center = c,
            style = Stroke(r * .035f),
        )
    }
}
