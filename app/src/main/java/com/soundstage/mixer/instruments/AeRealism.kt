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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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

/** Vertical wood grain with pores: the end cheeks and the console body. */
internal fun DrawScope.drawWood(base: Color, seed: Int = 7) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    drawRect(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.10f), base, lerp(base, Color.Black, 0.30f))))
    // Grain lines run down the panel. The spacing and darkness come from the noise, so no two
    // panels repeat; the slight sideways drift keeps them from reading as ruled lines.
    var x = 0f
    while (x < w) {
        val n1 = hash(x.toInt(), 0, seed)
        val n2 = hash(x.toInt(), 1, seed)
        val c = if (n2 > 0.5f) lerp(base, Color.Black, 0.42f) else lerp(base, Color.White, 0.22f)
        drawLine(c.copy(alpha = 0.05f + n2 * 0.14f), Offset(x, 0f), Offset(x + (n1 - 0.5f) * 3.5f, h), 0.6f + n1 * 2.2f)
        x += 1f + n1 * 2.4f
    }
    // Open pores, denser than the grain lines so the surface has a tooth.
    repeat((w * h / 110f).toInt().coerceAtMost(900)) { i ->
        drawCircle(
            Color.Black.copy(alpha = 0.04f + hash(i, 31, seed) * 0.09f),
            0.5f + hash(i, 41, seed) * 1.2f,
            Offset(hash(i, 11, seed) * w, hash(i, 23, seed) * h),
        )
    }
}

/** Brushed metal: fine horizontal striations under a broad vertical polish gradient. */
internal fun DrawScope.drawBrushed(base: Color, seed: Int = 3) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    drawRect(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.16f), base, lerp(base, Color.Black, 0.34f))))
    var y = 0f
    while (y < h) {
        val n = hash(0, y.toInt(), seed)
        val c = if (n > 0.5f) Color.White else Color.Black
        drawLine(c.copy(alpha = 0.015f + n * 0.055f), Offset(0f, y), Offset(w, y + (n - 0.5f) * 0.7f), 0.5f + n * 0.8f)
        y += 1f
    }
    drawRect(
        Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.055f), Color.White.copy(alpha = 0f))),
        Offset(0f, h * 0.16f), Size(w, h * 0.18f),
    )
}

/** Moulded black plastic: soft top-to-bottom falloff, a lit top lip and a fine speckle grain. */
internal fun DrawScope.drawPlastic(base: Color, seed: Int = 5) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    drawRect(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.13f), base, lerp(base, Color.Black, 0.42f))))
    repeat((w * h / 60f).toInt().coerceAtMost(3000)) { i ->
        val v = hash(i, 3, seed)
        drawCircle(
            (if (v > 0.5f) Color.White else Color.Black).copy(alpha = 0.02f + v * 0.045f),
            0.6f,
            Offset(hash(i, 1, seed) * w, hash(i, 2, seed) * h),
        )
    }
    drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, 0f), Offset(w, 0f), 1.2f)
    drawLine(Color.Black.copy(alpha = 0.38f), Offset(0f, h), Offset(w, h), 1.2f)
}

internal fun Modifier.aeWood(base: Color = Color(0xFF5A3218), seed: Int = 7): Modifier = drawBehind { drawWood(base, seed) }

internal fun Modifier.aeBrushed(base: Color = Color(0xFF39404A), seed: Int = 3): Modifier = drawBehind { drawBrushed(base, seed) }

internal fun Modifier.aePlastic(base: Color = Color(0xFF141419), seed: Int = 5): Modifier = drawBehind { drawPlastic(base, seed) }

/**
 * Turned plastic knob, filling the current canvas. [angleDeg] is the pointer angle from vertical.
 *
 * Relief comes from four cues, all of which a flat circle lacks: a cast shadow under the body,
 * a cylinder gradient (dark rim, lit crown) for the barrel, radial knurling around the rim, and
 * a specular on the upper-left of the top face. [tint] lets a knob belong to its instrument
 * without losing the black-plastic read.
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

/** Recessed screw head: used to pin panels to the console, and as a visual scale cue. */
internal fun DrawScope.drawScrew(cx: Float, cy: Float, r: Float, angleDeg: Float = 0f) {
    drawCircle(Color(0x66000000), r * 1.25f, Offset(cx, cy + r * 0.22f))
    drawCircle(
        Brush.linearGradient(
            listOf(lerp(Color(0xFF8A9099), Color.Black, 0.15f), Color(0xFF3A3F47), Color(0xFF20242A)),
            start = Offset(cx - r, cy), end = Offset(cx + r, cy),
        ),
        r, Offset(cx, cy),
    )
    drawCircle(Color(0x33000000), r * 0.72f, Offset(cx, cy), style = Stroke(r * 0.16f))
    rotate(angleDeg, Offset(cx, cy)) {
        drawRoundRect(Color(0x88000000), Offset(cx - r * 0.62f, cy - r * 0.10f), Size(r * 1.24f, r * 0.20f), CornerRadius(r * 0.10f))
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
internal fun DrawScope.drawLayerBars(off: Int, c: AeCtl, x: Float, y: Float, w: Float, h: Float, accent: Color) {
    val u = w / 170f
    val names = arrayOf("SIN", "SQR", "SAW", "SUP", "SFT")
    val bw = 26f * u
    for (i in 0..4) {
        val bx = x + i * 34f * u
        val v = c.v(off + i).coerceIn(0f, 1f)
        drawRoundRect(Color(0xFF070A11), Offset(bx, y), Size(bw, h), CornerRadius(4f * u))
        if (v > 0.02f) {
            val bh = (h - 4f * u) * v
            drawRoundRect(
                Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.45f))),
                Offset(bx + 2f * u, y + h - 2f * u - bh), Size(bw - 4f * u, bh), CornerRadius(3f * u),
            )
        }
        drawRoundRect(accent.copy(alpha = 0.30f), Offset(bx, y), Size(bw, h), CornerRadius(4f * u), style = Stroke(1f * u))
    }
    for (i in 0..4) AeLabel(names[i], x + i * 34f * u, y + h + 2f * u, bw, accent)
}

/** Small centred caption used by the drawn consoles; kept here so the draw helpers stay one file. */
private fun DrawScope.AeLabel(text: String, x: Float, y: Float, w: Float, color: Color) {
    val paint = android.graphics.Paint().apply {
        this.color = color.copy(alpha = 0.85f).toArgb()
        textSize = w * 0.42f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, x + w / 2f, y + paint.textSize, paint)
}

/**
 * Stereo output meter, filling the band the virtual keyboard used to occupy.
 *
 * [level] is the instrument's own level parameter, so the meter answers "how hot is this patch"
 * rather than pretending to be an analyser; the ripple rides the animation clock so it reads as
 * live. Green through red with a peak tick and a printed scale, because a bare bar gives the eye
 * no reference to judge against.
 */
internal fun DrawScope.drawOutputMeter(a: AeAnim, level: Float, accent: Color) {
    val u = size.width / 600f
    if (u <= 0f) return
    val lv = (0.18f + 0.72f * level.coerceIn(0f, 1f))
    for (ch in 0..1) {
        val y = (10f + ch * 30f) * u
        drawRoundRect(Color(0xFF08080C), Offset(0f, y), Size(size.width, 22f * u), CornerRadius(6f * u))
        val ripple = 0.10f * sin(a.t * (2.0f + ch * 0.4f) + ch)
        val l = (lv + ripple).coerceIn(0f, 1f)
        val w = size.width * l
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0xFF39D98A), Color(0xFFB8D94C), Color(0xFFE0A84C), Color(0xFFE04C4C))),
            Offset(2f * u, y + 2f * u), Size((w - 4f * u).coerceAtLeast(0f), 18f * u), CornerRadius(5f * u),
        )
        // Peak tick, held slightly above the bar: a tick that sits exactly on the end reads as
        // part of the bar rather than as a peak.
        drawRoundRect(Color(0xE6FFFFFF), Offset((w - 3f * u).coerceAtLeast(0f), y + 2f * u), Size(2.2f * u, 18f * u), CornerRadius(1.1f * u))
        val pk = (l + 0.08f).coerceAtMost(1f)
        drawRoundRect(accent.copy(alpha = 0.85f), Offset((size.width * pk - 1.5f * u).coerceAtLeast(0f), y - 1f * u), Size(1.6f * u, 24f * u), CornerRadius(0.8f * u))
    }
    for (i in 0..6) {
        val x = size.width * i / 6f
        drawLine(Color(0x26FFFFFF), Offset(x, 4f * u), Offset(x, 66f * u), 1f * u)
    }
}
