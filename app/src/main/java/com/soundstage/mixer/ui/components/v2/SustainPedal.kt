package com.soundstage.mixer.ui.components.v2

import android.graphics.BlurMaskFilter
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.min

/**
 * Square "sustain pedal" case of the Topbar. 1:1 port of `app/assets/SustainPedal.jsx`.
 *
 * The JSX draws on a 100x100 viewBox; everything here is drawn in that same
 * coordinate system and scaled, so the shape stays identical at any size.
 * No label is drawn (the 6.4-unit text of the JSX would be ~2.8 dp at 42 dp and
 * unreadable); the geometry, gradients and the spring are unchanged.
 *
 * [value] is the raw MIDI CC64 value (0..127, half-pedal supported). The caller
 * drives it; this component owns only the animation.
 */
@Composable
fun SustainPedal(
    value: Int,
    modifier: Modifier = Modifier,
    caseSize: Dp = 96.dp,
    accent: Color = Color(0xFF8F7BFF),
    threshold: Int = 64,
) {
    val density = LocalDensity.current
    val geo = remember(density.density, caseSize) { CaseGeo(density, caseSize) }
    val paints = remember(geo) { Paints(geo) }

    // Spring integrator, copied from the JSX effect: firmer on press, softer on return.
    val target = (value / 127f).coerceIn(0f, 1f)
    val sim = remember { PedalSim(target) }
    LaunchedEffect(target) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = min(0.032f, (now - last) / 1_000_000_000f)
            last = now
            val pressing = target > sim.x
            val k = if (pressing) 340f else 190f
            val c = if (pressing) 26f else 19f
            sim.v += (k * (target - sim.x) - c * sim.v) * dt
            sim.x += sim.v * dt
            sim.pos.floatValue = sim.x
            if (abs(target - sim.x) < 0.0008f && abs(sim.v) < 0.002f) {
                sim.x = target; sim.v = 0f; sim.pos.floatValue = target
                break
            }
        }
    }

    val topPath = remember { Path() }
    val frontPath = remember { Path() }
    val down = value >= threshold

    Canvas(
        modifier
            .size(caseSize + 2.dp)
            .semantics { contentDescription = "Sustain pedal ${if (down) "down" else "up"}" }
    ) {
        val b = geo.b
        val s = geo.s
        val px = geo.px
        val outer = geo.outer
        fun u(block: DrawScope.() -> Unit) = withTransform({ translate(b, b); scale(s, s, Offset.Zero) }, block)
        fun pos(v: Float) = b + v * s

        val p = sim.pos.floatValue.coerceIn(-0.05f, 1.06f) // tiny overshoot allowed
        val g = p.coerceIn(0f, 1f)

        // Case: drop shadow, gradient fill, border.
        paints.outer.color = Color.Black.copy(alpha = 0.45f).toArgb()
        drawIntoCanvas {
            it.nativeCanvas.drawRoundRect(0f, 3f * b, outer, 3f * b + outer, geo.r, geo.r, paints.outer)
        }
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF242C45), Color(0xFF171D32)), startY = b, endY = b + px),
            size = Size(outer, outer),
            cornerRadius = CornerRadius(geo.r),
        )
        drawRoundRect(
            color = Color(0xFF2B3452),
            topLeft = Offset(b / 2, b / 2),
            size = Size(outer - b, outer - b),
            cornerRadius = CornerRadius(geo.r - b / 2),
            style = Stroke(b),
        )

        clipPath(geo.inner) {
            // inset 0 1px 0 rgba(255,255,255,.07)
            drawPath(geo.topLight, Color.White.copy(alpha = 0.07f))
            // inset 0 -2px 6px rgba(0,0,0,.35)
            paints.inset.color = Color.Black.copy(alpha = 0.35f).toArgb()
            drawIntoCanvas { it.nativeCanvas.drawPath(geo.shadowFrame.asAndroidPath(), paints.inset) }

            // Ambient light rising with the pedal (radial-gradient ellipse at 50% 88%).
            run {
                val rx = 0.5f * px * 1.41421356f
                val ry = 0.88f * px * 1.41421356f
                val r0 = rx * 0.62f
                val c = Offset(b + px * 0.5f, b + px * 0.88f)
                withTransform({ translate(c.x, c.y); scale(1f, ry / rx, Offset.Zero) }) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = g * 0.2f), accent.copy(alpha = 0f)),
                            center = Offset.Zero, radius = r0,
                        ),
                        radius = r0, center = Offset.Zero,
                    )
                }
            }

            // LED
            blob(pos(85f), pos(13f), 5.5f * s, 5.5f * s, 1.6f * s, accent.copy(alpha = g * 0.55f), paints.small)
            u {
                val led = Offset(85f, 13f)
                drawCircle(Color(0xFF1B2137), 2.3f, led)
                drawCircle(Color(0xFF323C5C), 2.3f, led, style = Stroke(0.7f))
                drawCircle(accent.copy(alpha = (g * 1.2f).coerceIn(0f, 1f)), 2.3f, led)

                // Base slab
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color(0xFF1D243B), Color(0xFF0E1324)), startY = 74f, endY = 87f),
                    Offset(15f, 74f), Size(70f, 13f), CornerRadius(5f),
                )
                drawRoundRect(Color(0xFF2A3250), Offset(15f, 74f), Size(70f, 13f), CornerRadius(5f), style = Stroke(0.8f))
                drawLine(Color.White.copy(alpha = 0.09f), Offset(20f, 74.9f), Offset(80f, 74.9f), 0.8f, StrokeCap.Round)
                for (x in floatArrayOf(22f, 78f)) {
                    drawCircle(Color(0xFF080C18), 1.3f, Offset(x, 81f))
                    drawCircle(Color(0xFF3A4466), 1.3f, Offset(x, 81f), style = Stroke(0.5f))
                }
            }

            // Under-glow and contact shadow
            blob(pos(50f), pos(73.5f), lerp(12f, 27f, g) * s, 4f * s, 4f * s, accent.copy(alpha = g * 0.55f), paints.glow)
            blob(
                pos(50f), pos(76f), lerp(20f, 28f, g) * s, lerp(3f, 2f, g) * s, 1.6f * s,
                Color.Black.copy(alpha = lerp(0.22f, 0.55f, g)), paints.small,
            )

            u {
                // Hinge
                drawRoundRect(Color(0xFF2A3150), Offset(28.5f, 20.5f), Size(4.5f, 13f), CornerRadius(2f))
                drawRoundRect(Color(0xFF2A3150), Offset(67f, 20.5f), Size(4.5f, 13f), CornerRadius(2f))
                drawRoundRect(
                    Brush.verticalGradient(
                        0f to Color(0xFF7A85A6), 0.45f to Color(0xFF3F486A), 1f to Color(0xFF222940),
                        startY = 22f, endY = 31f,
                    ),
                    Offset(31f, 22f), Size(38f, 9f), CornerRadius(4.5f),
                )
                drawLine(Color.White.copy(alpha = 0.28f), Offset(35f, 23.6f), Offset(65f, 23.6f), 0.8f, StrokeCap.Round)

                // Pedal geometry
                val yb = 30f
                val bw = 17f
                val yf = lerp(53f, 66f, p)
                val hw = lerp(27f, 24f, p)
                val th = lerp(13f, 5f, p)

                frontPath.rewind().also {
                    frontPath.moveTo(50f - hw, yf); frontPath.lineTo(50f + hw, yf)
                    frontPath.lineTo(50f + hw - 1.6f, yf + th); frontPath.lineTo(50f - hw + 1.6f, yf + th)
                    frontPath.close()
                }
                topPath.rewind().also {
                    topPath.moveTo(50f - bw, yb); topPath.lineTo(50f + bw, yb)
                    topPath.lineTo(50f + hw, yf); topPath.lineTo(50f - hw, yf)
                    topPath.close()
                }

                // Front face
                val frontBrush = Brush.verticalGradient(
                    listOf(Color(0xFF363E5B), Color(0xFF1E243B)), startY = yf, endY = yf + th,
                )
                drawPath(frontPath, frontBrush)
                drawPath(frontPath, frontBrush, style = Stroke(3f, join = StrokeJoin.Round))
                drawLine(
                    accent.copy(alpha = g * 0.9f),
                    Offset(50f - hw + 1f, yf + th + 1.4f), Offset(50f + hw - 1f, yf + th + 1.4f),
                    1.2f, StrokeCap.Round,
                )

                // Top surface
                val topBrush = Brush.verticalGradient(
                    0f to Color(0xFF4A5474), 0.55f to Color(0xFF5F6A8C), 1f to Color(0xFF7681A3),
                    startY = yb, endY = yf,
                )
                drawPath(topPath, topBrush)
                drawPath(topPath, topBrush, style = Stroke(3f, join = StrokeJoin.Round))

                // Diagonal sheen (SVG objectBoundingBox: unit square stretched over 60x42)
                clipPath(topPath) {
                    withTransform({ translate(20f, 28f); scale(60f, 42f, Offset.Zero) }) {
                        drawRect(
                            Brush.linearGradient(
                                0f to Color.White.copy(alpha = 0f),
                                0.5f to Color.White.copy(alpha = 0.14f),
                                1f to Color.White.copy(alpha = 0f),
                                start = Offset.Zero, end = Offset(1f, 1f),
                            ),
                            size = Size(1f, 1f),
                        )
                    }
                }

                // Anti-slip grooves
                for (f in floatArrayOf(0.4f, 0.6f, 0.8f)) {
                    val y = lerp(yb, yf, f)
                    val half = lerp(bw, hw, f) - 3.5f
                    drawLine(Color(0xFF0C1020).copy(alpha = 0.55f), Offset(50f - half, y), Offset(50f + half, y), 1.1f, StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.14f), Offset(50f - half, y + 1.1f), Offset(50f + half, y + 1.1f), 0.8f, StrokeCap.Round)
                }

                // Front lip highlight
                drawLine(
                    Color.White.copy(alpha = lerp(0.4f, 0.18f, g)),
                    Offset(50f - hw + 1.2f, yf + 0.2f), Offset(50f + hw - 1.2f, yf + 0.2f),
                    1f, StrokeCap.Round,
                )
            }
        }
    }
}

private class PedalSim(start: Float) {
    var x = start
    var v = 0f
    val pos = mutableFloatStateOf(start)
}

private class CaseGeo(d: androidx.compose.ui.unit.Density, caseSize: Dp) {
    val px = with(d) { caseSize.toPx() }
    val b = with(d) { 1.dp.toPx() }
    val s = px / 100f
    val outer = px + 2f * b
    val r = px * 0.2f
    private val ri = (r - b).coerceAtLeast(0f)

    val inner = Path().apply { addRoundRect(RoundRect(b, b, b + px, b + px, ri, ri)) }
    val topLight: Path = Path.combine(
        PathOperation.Difference, inner,
        Path().apply { addPath(inner, Offset(0f, b)) },
    )
    val shadowFrame = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(-px, -px, outer + px, outer + px))
        addPath(inner, Offset(0f, -2f * b))
    }
}

private class Paints(g: CaseGeo) {
    val outer = blurPaint(4f * g.b)      // box-shadow 0 3px 8px
    val inset = blurPaint(3f * g.b)      // inset 0 -2px 6px
    val glow = blurPaint(4f * g.s)       // feGaussianBlur 4
    val small = blurPaint(1.6f * g.s)    // feGaussianBlur 1.6
}

private fun blurPaint(sigmaPx: Float) = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
    // Skia: sigma = 0.57735 * radius + 0.5
    maskFilter = BlurMaskFilter(((sigmaPx - 0.5f) / 0.57735f).coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)
}

/** Blurred ellipse, clipped like the SVG filter region (-50% / 200% of the bbox). */
private fun DrawScope.blob(
    cx: Float, cy: Float, rx: Float, ry: Float, sigma: Float, color: Color, paint: AndroidPaint,
) {
    clipRect(cx - 2f * rx, cy - 2f * ry, cx + 2f * rx, cy + 2f * ry) {
        paint.color = color.toArgb()
        drawIntoCanvas { it.nativeCanvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, paint) }
    }
}
