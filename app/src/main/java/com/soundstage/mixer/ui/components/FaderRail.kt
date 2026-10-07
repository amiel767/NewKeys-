package com.soundstage.mixer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Rail de fader (châssis + rainure lumineuse), dessiné en code : net à toutes les tailles.
 *
 * - La LARGEUR du Modifier pilote l'échelle : tout (rayons, rainure, liserés) est proportionnel.
 * - La HAUTEUR est libre : coins et rainure gardent leur taille, seule la longueur change.
 * - [accent] = couleur de la rainure (n'importe quelle couleur de l'appli).
 * - [level] = null  -> rainure pleine sur toute la hauteur (design de référence).
 *             0..1 -> rainure colorée seulement du bas jusqu'à ce niveau (comportement actuel
 *                     du fader : coloré sous la poignée, sombre au-dessus).
 *
 * - [glow]  = 0..1 : intensité LED de la partie colorée (0 = éteint, 1 = pleine lueur).
 *             À piloter par l'activité audio du canal (voir les instructions d'intégration).
 *
 * Valeurs mesurées sur le design de référence (86 x 1183 unités), voir [RailSpec].
 * Usage : FaderRail(accent = Color(0xFF8B5CF6), modifier = Modifier.width(14.dp).fillMaxHeight())
 */
@Composable
fun FaderRail(
    accent: Color,
    modifier: Modifier = Modifier,
    level: Float? = null,
    glow: Float = 0f,
) {
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        drawBody()
        drawGroove(accent, level?.coerceIn(0f, 1f), glow.coerceIn(0f, 1f))
    }
}

private object RailSpec {
    const val REF_W = 86f

    const val BODY_R_TOP = 22f
    const val BODY_R_BOTTOM = 30f
    const val BODY_RIM = 1.5f
    const val BODY_RIM_MIN_PX = 1.0f

    const val GROOVE_W = 17f
    const val GROOVE_INSET_Y = 36f
    const val GROOVE_RIM = 1.5f
    const val GROOVE_RIM_MIN_PX = 0.8f

    const val SHADOW_DX = 1.5f
    const val SHADOW_DY = 2f
    const val SHADOW_LAYER_ALPHA = 0.11f
    val SHADOW_GROW = floatArrayOf(-0.8f, 0f, 0.8f, 1.6f, 2.4f)

    val EMPTY_GROOVE = Color.Black.copy(alpha = 0.40f)

    // Effet LED : halo de la couleur autour de la partie colorée + coeur lumineux au centre
    val LED_HALO_GROW = floatArrayOf(2f, 4.5f, 7.5f, 11f)
    val LED_HALO_ALPHA = floatArrayOf(0.42f, 0.28f, 0.16f, 0.08f)
    const val LED_CORE_WHITE = 0.55f

    private val W = Color.White
    private val K = Color.Black

    val bodyStops = arrayOf(
        0.00f to Color(0xFF2D3041),
        0.10f to Color(0xFF313443),
        0.38f to Color(0xFF373A4B),
        0.60f to Color(0xFF3A3C4F),
        0.85f to Color(0xFF3E4155),
        1.00f to Color(0xFF474A63),
    )
    val bodySideStops = arrayOf(
        0.00f to W.copy(alpha = 0.07f),
        0.07f to W.copy(alpha = 0f),
        0.90f to K.copy(alpha = 0f),
        1.00f to K.copy(alpha = 0.16f),
    )
    val bodyRimStops = arrayOf(
        0.0f to W.copy(alpha = 0.16f),
        0.5f to W.copy(alpha = 0.04f),
        1.0f to W.copy(alpha = 0.30f),
    )
    val grooveSideStops = arrayOf(
        0.00f to W.copy(alpha = 0.14f),
        0.45f to W.copy(alpha = 0f),
        0.45f to K.copy(alpha = 0f),
        1.00f to K.copy(alpha = 0.22f),
    )
    val grooveTopStops = arrayOf(
        0.00f to W.copy(alpha = 0.22f),
        0.12f to W.copy(alpha = 0f),
        0.12f to K.copy(alpha = 0f),
        0.90f to K.copy(alpha = 0f),
        1.00f to K.copy(alpha = 0.12f),
    )
    val grooveRimStops = arrayOf(
        0.0f to W.copy(alpha = 0.42f),
        0.5f to W.copy(alpha = 0.30f),
        1.0f to W.copy(alpha = 0.60f),
    )
}

private fun bodyPath(l: Float, t: Float, r: Float, b: Float, rTop: Float, rBottom: Float): Path {
    val h = b - t
    val k = if (rTop + rBottom > h) h / (rTop + rBottom) else 1f
    val top = (rTop * k).coerceAtLeast(0f)
    val bottom = (rBottom * k).coerceAtLeast(0f)
    return Path().apply {
        addRoundRect(
            RoundRect(
                left = l, top = t, right = r, bottom = b,
                topLeftCornerRadius = CornerRadius(top),
                topRightCornerRadius = CornerRadius(top),
                bottomRightCornerRadius = CornerRadius(bottom),
                bottomLeftCornerRadius = CornerRadius(bottom),
            )
        )
    }
}

private fun DrawScope.drawBody() {
    val w = size.width
    val h = size.height
    val s = w / RailSpec.REF_W
    val rTop = RailSpec.BODY_R_TOP * s
    val rBottom = RailSpec.BODY_R_BOTTOM * s

    val shape = bodyPath(0f, 0f, w, h, rTop, rBottom)
    drawPath(shape, Brush.verticalGradient(*RailSpec.bodyStops, startY = 0f, endY = h))
    drawPath(shape, Brush.horizontalGradient(*RailSpec.bodySideStops, startX = 0f, endX = w))

    val rim = max(RailSpec.BODY_RIM * s, RailSpec.BODY_RIM_MIN_PX)
    val half = rim / 2f
    val rimShape = bodyPath(half, half, w - half, h - half, rTop - half, rBottom - half)
    drawPath(
        rimShape,
        Brush.verticalGradient(*RailSpec.bodyRimStops, startY = 0f, endY = h),
        style = Stroke(width = rim),
    )
}

private fun DrawScope.drawGroove(accent: Color, level: Float?, glow: Float) {
    val w = size.width
    val h = size.height
    val s = w / RailSpec.REF_W

    val gw = RailSpec.GROOVE_W * s
    val gx = (w - gw) / 2f
    val gy = RailSpec.GROOVE_INSET_Y * s
    val gh = (h - 2f * gy).coerceAtLeast(0f)
    if (gh <= 0f) return
    val gr = gw / 2f

    // Ombre douce (flou simulé par couches translucides : pas de BlurMaskFilter, qui est
    // peu fiable en accélération matérielle selon la version d'Android).
    for (grow in RailSpec.SHADOW_GROW) {
        val e = grow * s
        drawRoundRect(
            color = Color.Black.copy(alpha = RailSpec.SHADOW_LAYER_ALPHA),
            topLeft = Offset(gx + RailSpec.SHADOW_DX * s - e, gy + RailSpec.SHADOW_DY * s - e),
            size = Size(gw + 2f * e, gh + 2f * e),
            cornerRadius = CornerRadius((gr + e).coerceAtLeast(0f)),
        )
    }

    val groovePath = Path().apply {
        addRoundRect(RoundRect(gx, gy, gx + gw, gy + gh, CornerRadius(gr)))
    }
    val fillTop = if (level == null) gy else gy + gh * (1f - level)
    val fillH = gy + gh - fillTop

    // Halo LED (derrière la rainure), uniquement sur la partie colorée
    if (glow > 0f && fillH > 0f) {
        for (i in RailSpec.LED_HALO_GROW.indices) {
            val e = RailSpec.LED_HALO_GROW[i] * s
            drawRoundRect(
                color = accent.copy(alpha = RailSpec.LED_HALO_ALPHA[i] * glow),
                topLeft = Offset(gx - e, fillTop - e),
                size = Size(gw + 2f * e, fillH + 2f * e),
                cornerRadius = CornerRadius(gr + e),
            )
        }
    }

    // Rainure : couleur adaptable
    if (level == null) {
        drawRoundRect(
            color = accent,
            topLeft = Offset(gx, gy),
            size = Size(gw, gh),
            cornerRadius = CornerRadius(gr),
        )
    } else {
        drawRoundRect(
            color = RailSpec.EMPTY_GROOVE,
            topLeft = Offset(gx, gy),
            size = Size(gw, gh),
            cornerRadius = CornerRadius(gr),
        )
        clipPath(groovePath) {
            drawRect(
                color = accent,
                topLeft = Offset(gx, gy + gh * (1f - level)),
                size = Size(gw, gh * level),
            )
        }
    }

    // Reflets et volume (overlays blanc/noir : fonctionnent avec n'importe quelle couleur)
    drawRoundRect(
        brush = Brush.horizontalGradient(*RailSpec.grooveSideStops, startX = gx, endX = gx + gw),
        topLeft = Offset(gx, gy),
        size = Size(gw, gh),
        cornerRadius = CornerRadius(gr),
    )
    drawRoundRect(
        brush = Brush.verticalGradient(*RailSpec.grooveTopStops, startY = gy, endY = gy + gh),
        topLeft = Offset(gx, gy),
        size = Size(gw, gh),
        cornerRadius = CornerRadius(gr),
    )

    // Coeur lumineux LED (ligne claire au centre de la partie colorée)
    if (glow > 0f && fillH > 0f) {
        clipPath(groovePath) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.White.copy(alpha = 0f),
                    0.5f to Color.White.copy(alpha = RailSpec.LED_CORE_WHITE * glow),
                    1f to Color.White.copy(alpha = 0f),
                    startX = gx,
                    endX = gx + gw,
                ),
                topLeft = Offset(gx, fillTop),
                size = Size(gw, fillH),
            )
        }
    }

    // Liseré clair autour de la rainure
    val rim = max(RailSpec.GROOVE_RIM * s, RailSpec.GROOVE_RIM_MIN_PX)
    val half = rim / 2f
    drawRoundRect(
        brush = Brush.verticalGradient(*RailSpec.grooveRimStops, startY = gy, endY = gy + gh),
        topLeft = Offset(gx + half, gy + half),
        size = Size((gw - rim).coerceAtLeast(0f), (gh - rim).coerceAtLeast(0f)),
        cornerRadius = CornerRadius((gr - half).coerceAtLeast(0f)),
        style = Stroke(width = rim),
    )
}

@Preview(widthDp = 360, heightDp = 420, showBackground = true, backgroundColor = 0xFF1A2335)
@Composable
private fun FaderRailPreview() {
    val colors = listOf(
        Color(0xFFE08A2E), Color(0xFF2FB58A), Color(0xFF3B82F6),
        Color(0xFF8B5CF6), Color(0xFFEC4899),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.Top,
    ) {
        colors.forEachIndexed { i, c ->
            FaderRail(
                accent = c,
                modifier = Modifier.width(14.dp).height(if (i % 2 == 0) 160.dp else 300.dp),
                level = if (i == 2) 0.35f else null,
            )
        }
    }
}

@Preview(widthDp = 120, heightDp = 360, showBackground = true, backgroundColor = 0xFF1A2335)
@Composable
private fun FaderRailLargePreview() {
    // Largeur 43 dp ≈ design de référence 86 px à 2x : à comparer au PNG d'origine
    Row(modifier = Modifier.padding(16.dp).background(Color.Transparent)) {
        FaderRail(
            accent = Color(0xFF8B5CF6),
            modifier = Modifier.width(43.dp).height(330.dp),
        )
    }
}
