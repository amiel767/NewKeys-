@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.aetherkeys.organ

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/* =====================================================================
   Moteur "CSS box" : fond, bordure, box-shadow (externes + inset) fidèles.
   Les flous sont approchés par 10 couches (quantiles d'une gaussienne, σ = blur/2),
   donc identiques sur toutes les versions d'Android (pas de BlurMaskFilter).
   Toutes les valeurs d'entrée sont en dp de conception.
   ===================================================================== */

internal data class BoxShadow(
    val dx: Float, val dy: Float, val blur: Float, val color: Color,
    val spread: Float = 0f, val inset: Boolean = false,
)

internal fun shadow(dx: Float, dy: Float, blur: Float, color: Color, spread: Float = 0f) =
    BoxShadow(dx, dy, blur, color, spread, false)

internal fun inset(dx: Float, dy: Float, blur: Float, color: Color) =
    BoxShadow(dx, dy, blur, color, 0f, true)

internal fun black(a: Float) = Color(0f, 0f, 0f, a)
internal fun white(a: Float) = Color(1f, 1f, 1f, a)

internal class Corners(val tl: Float, val tr: Float, val br: Float, val bl: Float) {
    constructor(r: Float) : this(r, r, r, r)
    fun grow(e: Float) = Corners(max(0f, tl + e), max(0f, tr + e), max(0f, br + e), max(0f, bl + e))
    fun scale(k: Float) = Corners(tl * k, tr * k, br * k, bl * k)
    val uniform get() = tl == tr && tr == br && br == bl
}

typealias Fill = (Rect) -> Brush

internal fun solid(c: Color): Fill = { SolidColor(c) }
internal fun vGrad(vararg stops: Pair<Float, Color>): Fill =
    { r -> Brush.verticalGradient(*stops, startY = r.top, endY = r.bottom) }
internal fun vGrad(a: Color, b: Color): Fill = vGrad(0f to a, 1f to b)
internal fun hGrad(vararg stops: Pair<Float, Color>): Fill =
    { r -> Brush.horizontalGradient(*stops, startX = r.left, endX = r.right) }
internal fun hGrad(a: Color, b: Color): Fill = hGrad(0f to a, 1f to b)

private val Q10 = floatArrayOf(-1.6449f, -1.0364f, -0.6745f, -0.3853f, -0.1257f, 0.1257f, 0.3853f, 0.6745f, 1.0364f, 1.6449f)

private fun erf(x: Float): Float {
    val t = 1f / (1f + 0.3275911f * abs(x))
    val y = 1f - (((((1.061405429f * t - 1.453152027f) * t) + 1.421413741f) * t - 0.284496736f) * t + 0.254829592f) * t * exp(-x * x)
    return if (x >= 0f) y else -y
}

private fun rr(r: Rect, c: Corners) = RoundRect(
    r.left, r.top, r.right, r.bottom,
    CornerRadius(c.tl), CornerRadius(c.tr), CornerRadius(c.br), CornerRadius(c.bl),
)

private fun roundPath(r: Rect, c: Corners) = Path().apply { addRoundRect(rr(r, c)) }

private fun DrawScope.fillRR(r: Rect, c: Corners, brush: Brush, alpha: Float = 1f) {
    if (r.width <= 0f || r.height <= 0f) return
    if (c.uniform) drawRoundRect(brush, r.topLeft, r.size, CornerRadius(c.tl), alpha = alpha)
    else drawPath(roundPath(r, c), brush, alpha = alpha)
}

private fun DrawScope.fillRR(r: Rect, c: Corners, color: Color) = fillRR(r, c, SolidColor(color))

/** Remplit une boîte (px) avec des coins éventuellement différents. */
internal fun DrawScope.drawRRectCorners(r: Rect, c: Corners, brush: Brush) = fillRR(r, c, brush)

/** Ombre externe. rect/corners en PX. */
internal fun DrawScope.drawOuterShadow(rect: Rect, c: Corners, s: BoxShadow) {
    if (s.color.alpha <= 0f) return
    val sigma = s.blur / 2f * density
    val spread = s.spread * density
    val dx = s.dx * density
    val dy = s.dy * density
    var amax = s.color.alpha
    val qs = if (sigma == 0f) floatArrayOf(0f) else Q10
    if (sigma > 0f) {
        // forme petite : l'intensité d'un flou ne dépasse jamais celle du centre d'un pavé w×h flouté
        val f = erf((rect.width + 2 * spread) / (2f * 1.41421356f * sigma)) * erf((rect.height + 2 * spread) / (2f * 1.41421356f * sigma))
        amax *= f.coerceIn(0f, 1f).pow(0.6f)
    }
    val es = qs.map { sigma * it + spread }.sortedDescending()
    var prev = 0f
    es.forEachIndexed { j, e ->
        val t = amax * (j + 1) / es.size
        val a = (t - prev) / (1f - prev)
        prev = t
        val r = Rect(rect.left + dx - e, rect.top + dy - e, rect.right + dx + e, rect.bottom + dy + e)
        fillRR(r, c.grow(e), s.color.copy(alpha = a))
    }
}

/** Ombre interne. rect/corners = boîte de remplissage (PX). */
internal fun DrawScope.drawInsetShadow(rect: Rect, c: Corners, s: BoxShadow) {
    if (s.color.alpha <= 0f || rect.width <= 0f || rect.height <= 0f) return
    val sigma = s.blur / 2f * density
    val dx = s.dx * density
    val dy = s.dy * density
    val qs = if (sigma == 0f) floatArrayOf(0f) else Q10
    val es = qs.map { sigma * it }.sortedDescending()
    val big = 80f * density
    val outer = Rect(rect.left - big, rect.top - big, rect.right + big, rect.bottom + big)
    clipPath(roundPath(rect, c)) {
        var prev = 0f
        es.forEachIndexed { j, e ->
            val t = s.color.alpha * (j + 1) / es.size
            val a = (t - prev) / (1f - prev)
            prev = t
            val hole = Rect(rect.left + dx + e, rect.top + dy + e, rect.right + dx - e, rect.bottom + dy - e)
            val p = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(outer)
                if (hole.width > 0f && hole.height > 0f) addRoundRect(rr(hole, c.grow(-e)))
            }
            drawPath(p, s.color.copy(alpha = a))
        }
    }
}

/**
 * Équivalent d'un élément CSS : background + border (1px intérieur) + box-shadow.
 * Ordre CSS respecté : ombres externes (dessous) → fond → ombres inset → bordure.
 * `shadows` est donné dans l'ordre d'écriture CSS (la première ombre est la plus haute).
 */
internal fun Modifier.cssBox(
    radius: Float = 0f,
    corners: Corners? = null,
    fill: Fill? = null,
    border: Color? = null,
    borderWidth: Float = 1f,
    shadows: List<BoxShadow> = emptyList(),
): Modifier = drawBehind {
    val r = Rect(Offset.Zero, size)
    val c = (corners ?: Corners(radius)).scale(density)
    shadows.filter { !it.inset }.asReversed().forEach { drawOuterShadow(r, c, it) }
    if (fill != null) fillRR(r, c, fill(r))
    val bw = if (border != null) borderWidth * density else 0f
    val pr = Rect(r.left + bw, r.top + bw, r.right - bw, r.bottom - bw)
    shadows.filter { it.inset }.asReversed().forEach { drawInsetShadow(pr, c.grow(-bw), it) }
    if (border != null && bw > 0f) {
        drawRoundRect(
            border, Offset(bw / 2, bw / 2), Size(r.width - bw, r.height - bw),
            CornerRadius(max(0f, c.tl - bw / 2)), style = Stroke(bw),
        )
    }
}

/** repeating-linear-gradient(0deg, transparent 0, transparent rampStart, rgba(0,0,0,alpha) period) — dessiné AU-DESSUS du contenu. */
internal fun Modifier.scanlines(period: Float, rampStart: Float, alpha: Float, radius: Float): Modifier = drawWithContent {
    drawContent()
    val p = period * density
    val rs = rampStart * density
    val path = Path().apply { addRoundRect(rr(Rect(Offset.Zero, size), Corners(radius * density))) }
    clipPath(path) {
        var k = 0
        while (k * p < size.height + p) {
            val yTop = size.height - (k + 1) * p
            val yBot = size.height - (k * p + rs)
            if (yBot > yTop) {
                drawRect(
                    Brush.verticalGradient(0f to black(alpha), 1f to black(0f), startY = yTop, endY = yBot),
                    Offset(0f, yTop), Size(size.width, yBot - yTop),
                )
            }
            k++
        }
    }
}

/** `.disabled { opacity:.25; filter:grayscale(1); pointer-events:none }` */
internal fun Modifier.groupDisabled(disabled: Boolean): Modifier =
    if (!disabled) this else this
        .drawWithContent {
            val paint = Paint().apply {
                alpha = 0.25f
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
            }
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(Offset.Zero, size), paint)
                drawContent()
                canvas.restore()
            }
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    e.changes.forEach { it.consume() }
                }
            }
        }

/** Conteneur "bouton" : :active = pressed (translateY + ombre rentrée). */
@Composable
internal fun PressBox(
    modifier: Modifier = Modifier,
    pressShift: Float = 1f,
    onClick: () -> Unit,
    content: @Composable BoxScope.(pressed: Boolean) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .graphicsLayer { translationY = if (pressed) pressShift * density else 0f }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onClick() },
                )
            },
    ) { content(pressed) }
}

/* ========================= Texte ========================= */

internal fun otext(
    size: Float, weight: FontWeight, color: Color,
    family: FontFamily = OrganFonts.Ui, ls: Float = 0f, lh: Float = size * 1.2f,
) = TextStyle(
    fontSize = size.sp, fontWeight = weight, color = color, fontFamily = family,
    letterSpacing = ls.sp, lineHeight = lh.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/** `text-shadow: 0 0 Bpx color` multiples : le flou CSS (σ = B/2) est converti en rayon d'ombre Android. */
@Composable
internal fun GlowText(
    text: String, style: TextStyle, glows: List<Pair<Color, Float>>,
    modifier: Modifier = Modifier, maxLines: Int = 1, overflow: TextOverflow = TextOverflow.Clip,
) {
    val d = LocalDensity.current.density
    Box(modifier) {
        glows.forEach { (c, blur) ->
            val sigmaPx = blur / 2f * d
            val radius = max(0.1f, (sigmaPx - 0.5f) / 0.57735f)
            Text(text, style = style.copy(shadow = Shadow(c, Offset.Zero, radius)), maxLines = maxLines, softWrap = false, overflow = overflow)
        }
        Text(text, style = style, maxLines = maxLines, softWrap = false, overflow = overflow)
    }
}

internal val AccentGlow70 = Color(0xB38FB2E8)     // rgba(143,178,232,.7)
internal val AccentSoftGlow90 = Color(0xE6A8C5F0) // rgba(168,197,240,.9)
internal val AccentGlow60 = Color(0x998FB2E8)
internal val AccentSoftGlow80 = Color(0xCCA8C5F0)
