package com.soundstage.mixer.instruments

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Textures and relief for the instrument consoles, drawn in code.
 *
 * The window already carried one hand-measured piece of realism — the mixer's [com.soundstage.mixer.ui.components.FaderThumb],
 * sampled pixel by pixel from a photo. These helpers extend the same approach to the panels
 * behind the controls, so a console reads as a manufactured object rather than a flat gradient:
 * wood grain, brushed metal, moulded plastic and a turned knob with knurling and a specular.
 *
 * Everything is procedural and deterministic. A texture bitmap would have to ship in three or
 * four densities and would blur when the console is letterboxed; noise from a hash costs nothing
 * and stays sharp at any scale.
 */

/** Deterministic white noise in 0..1 for an integer lattice point. */
internal fun hash(x: Int, y: Int, seed: Int): Float {
    var h = x * 374761393 + y * 668265263 + seed * 1442695041
    h = (h xor (h shr 13)) * 1274126177
    return ((h xor (h shr 16)) and 0x7FFFFFFF) / 2147483647f
}

/**
 * Smooth wood, the finish of an organ cabinet rather than raw timber.
 *
 * A cabinet panel is sanded and lacquered: the grain is still there but it is a long, soft
 * variation in the surface, not a set of drawn lines, and there are no open pores. So this is a
 * base tint, a handful of very wide low-contrast streaks, and a sheen across the top third. The
 * earlier version drew 900 pores and hard grain lines, which read as plywood.
 */
internal fun DrawScope.drawWood(base: Color, seed: Int = 7) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    drawRect(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.07f), base, lerp(base, Color.Black, 0.15f))))
    // Grain: a few wide, soft streaks. Two overlapping passes at different widths give the
    // irregularity of real grain without any line that reads as drawn.
    var i = 0
    while (i < 26) {
        val x = hash(i, 5, seed) * w
        val wide = 14f + hash(i, 6, seed) * 46f
        val dark = hash(i, 7, seed) > 0.5f
        val c = if (dark) lerp(base, Color.Black, 0.30f) else lerp(base, Color.White, 0.16f)
        drawRect(
            Brush.horizontalGradient(
                listOf(c.copy(alpha = 0f), c.copy(alpha = 0.055f + hash(i, 8, seed) * 0.05f), c.copy(alpha = 0f)),
                startX = x - wide, endX = x + wide,
            ),
            Offset(x - wide, 0f), Size(wide * 2f, h),
        )
        i++
    }
    // Sheen: lacquer reflects the room, strongest near the top edge and gone by mid panel.
    drawRect(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.05f),
            0.16f to Color.White.copy(alpha = 0.022f),
            0.5f to Color.Transparent,
            startY = 0f, endY = h,
        ),
    )
}

internal fun Modifier.aeWood(base: Color = Color(0xFF5A3218), seed: Int = 7): Modifier = drawBehind { drawWood(base, seed) }

/**
 * Turned plastic knob, filling the current canvas. [angleDeg] is the pointer angle from vertical.
 *
 * Relief comes from four cues a flat circle lacks: a cast shadow under the body, a cylinder
 * gradient for the barrel, radial knurling around the rim, and a specular on the upper-left of the
 * top face. [tint] lets a knob belong to its instrument without losing the black-plastic read.
 */
internal fun DrawScope.drawPlasticKnob(angleDeg: Float, tint: Color = Color(0xFF202027), seed: Int = 11) {
    val d = min(size.width, size.height)
    if (d <= 0f) return
    val r = d / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawOval(
        Color(0x70000000),
        topLeft = Offset(cx - r * 0.98f, cy - r * 0.86f + r * 0.16f),
        size = Size(r * 1.96f, r * 1.72f),
    )
    // Barrel: light from the upper left, so the lit band sits left of centre.
    drawCircle(
        Brush.linearGradient(
            listOf(
                lerp(tint, Color.Black, 0.72f), lerp(tint, Color.White, 0.30f),
                lerp(tint, Color.White, 0.10f), lerp(tint, Color.Black, 0.55f), lerp(tint, Color.Black, 0.80f),
            ),
            start = Offset(cx - r, cy), end = Offset(cx + r, cy),
        ),
        r, Offset(cx, cy),
    )
    repeat(56) { i ->
        val a = Math.toRadians((i * 360f / 56f).toDouble())
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        drawLine(
            if (i % 2 == 0) Color(0x40000000) else Color(0x1AFFFFFF),
            Offset(cx + ca * r * 0.855f, cy + sa * r * 0.855f),
            Offset(cx + ca * r * 0.99f, cy + sa * r * 0.99f),
            r * 0.045f,
        )
    }
    // Top face: a shallower dome than the barrel, so the rim stays visible as a shoulder.
    val rf = r * 0.72f
    drawCircle(
        Brush.radialGradient(
            listOf(lerp(tint, Color.White, 0.34f), lerp(tint, Color.White, 0.10f), lerp(tint, Color.Black, 0.52f)),
            center = Offset(cx - rf * 0.32f, cy - rf * 0.38f), radius = rf * 1.7f,
        ),
        rf, Offset(cx, cy),
    )
    drawCircle(Color(0x33000000), rf * 0.93f, Offset(cx, cy), style = Stroke(r * 0.045f))
    drawOval(
        Color(0x4DFFFFFF),
        topLeft = Offset(cx - rf * 0.66f, cy - rf * 0.80f),
        size = Size(rf * 0.94f, rf * 0.46f),
    )
    rotate(angleDeg, Offset(cx, cy)) {
        drawRoundRect(
            Color(0xFFF4F6FA),
            Offset(cx - r * 0.045f, cy - r * 0.82f),
            Size(r * 0.09f, r * 0.40f),
            CornerRadius(r * 0.045f),
        )
    }
}

/**
 * Translucent relief for a knob whose look is already drawn (an SVG cap): a specular on the upper
 * left, a shadow under the lower rim and a lit outer rim. Purely additive — the underlying cap and
 * its pointer stay visible, so an instrument keeps its identity while gaining a lit surface.
 */
internal fun DrawScope.drawKnobRelief() {
    val d = min(size.width, size.height)
    if (d <= 0f) return
    val r = d / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawCircle(
        Brush.radialGradient(
            0f to Color(0x59FFFFFF), 0.42f to Color(0x14FFFFFF), 1f to Color(0x00000000),
            center = Offset(cx - r * 0.36f, cy - r * 0.42f), radius = r * 1.15f,
        ),
        r, Offset(cx, cy),
    )
    drawCircle(
        Brush.radialGradient(
            0.55f to Color(0x00000000), 1f to Color(0x66000000),
            center = Offset(cx, cy), radius = r,
        ),
        r, Offset(cx, cy),
    )
    drawCircle(Color(0x1FFFFFFF), r * 0.985f, Offset(cx, cy), style = Stroke(d * 0.028f))
}

/**
 * Five small bars, one per voice layer, in the order they appear on the SOUND page.
 *
 * Drawn from the engine parameters rather than from any UI state, so a layer that is silenced by
 * a preset or by a macro shows as down. A muted layer is outlined instead of filled: a zero-height
 * fill would read as a missing bar rather than a deliberate silence.
 */
