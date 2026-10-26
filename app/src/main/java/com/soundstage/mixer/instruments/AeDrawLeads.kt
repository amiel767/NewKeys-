package com.soundstage.mixer.instruments

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.*

private val CY = Color(0xFF19E3FF)
private fun cl(v: Float) = v.coerceIn(0f, 1f)
private fun DrawScope.glow(p: Path, w: Float, col: Color = Color(0xFFD9FBFF), g: Color = CY) {
    drawPath(p, g.copy(alpha = 0.25f), style = Stroke(w * 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(p, col, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
private fun path(pts: List<Offset>) = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
private fun curveF(f: Float, fc: Float, q: Float, ft: Int): Float {
    val r = f / fc; val d = sqrt((1f - r * r).pow(2) + (r / q).pow(2))
    return when (ft) { 0 -> 1f / d; 1 -> r * r / d; else -> (r / q) / d }
}

/** Layer icon (sine, square, saw, super, soft), 80 x 40 units. */
fun DrawScope.drawWaveIcon(i: Int) = scale(size.width / 80f, Offset.Zero) {
    val pts = ArrayList<Offset>(); var x = 0f
    while (x <= 80f) {
        val u = (x / 80f * 2f) % 1f
        val y = when (i) { 0 -> sin(u * 6.283f); 1 -> if (u < 0.5f) 1f else -1f; 2 -> 2f * u - 1f; 3 -> (2f * u - 1f) * 0.6f + (2f * ((u * 1.03f) % 1f) - 1f) * 0.4f; else -> if (u < 0.5f) 4f * u - 1f else 3f - 4f * u }
        pts.add(Offset(x, 20f - y * 13f)); x += 2f
    }
    glow(path(pts), 2f)
}

/** Oscilloscope, 700 x 220 units. Uses real samples when the host provides them, otherwise a trace built from the layer levels. */
fun DrawScope.drawLeadScope(a: AeAnim, y: (Int) -> Float, held: Boolean, samples: FloatArray?) = scale(size.width / 700f, Offset.Zero) {
    drawRect(Color(0xFF02070D), Offset.Zero, Size(700f, 220f))
    for (i in 1 until 8) drawLine(Color(0x1819E3FF), Offset(i * 87.5f, 0f), Offset(i * 87.5f, 220f), 1f)
    for (i in 1 until 4) drawLine(Color(0x1819E3FF), Offset(0f, i * 55f), Offset(700f, i * 55f), 1f)
    val sum = max(0.1f, (0 until 5).sumOf { y(it).toDouble() }.toFloat())
    val amp = if (held) 0.35f + 0.45f * min(1f, a.lv * 5f) else 0.05f
    val pts = ArrayList<Offset>(); var x = 0f
    while (x <= 700f) {
        val v = if (samples != null && samples.isNotEmpty()) samples[min(samples.size - 1, (x / 700f * samples.size).toInt())] else {
            val u = (x / 700f * 3f + a.t * 0.8f) % 1f
            val s2 = 2f * u - 1f
            (y(0) * sin(u * 6.283f) + y(1) * (if (u < 0.5f) 1f else -1f) * 0.6f + y(2) * s2 + y(3) * (s2 * 0.6f + (2f * ((u * 1.03f) % 1f) - 1f) * 0.4f) + y(4) * (if (u < 0.5f) 4f * u - 1f else 3f - 4f * u)) / sum
        }
        pts.add(Offset(x, 110f - v * 200f * amp)); x += 3f
    }
    glow(path(pts), 2.4f)
}

/** Five LED columns of 12 segments, 566 x 160 units (aligned on the faders). */
fun DrawScope.drawMeters(a: AeAnim) = scale(size.width / 566f, Offset.Zero) {
    for (i in 0 until 5) {
        val x = 74f + i * 108f; val n = (a.la[i] * 12f).roundToInt()
        for (j in 0 until 12) {
            val on = j < n; val yy = 146f - j * 12.2f
            val col = if (on) (if (j > 9) Color(0xFFFF4F9A) else if (j > 7) Color(0xFF7AA8FF) else CY) else Color(0xFF0B1624)
            drawRect(col, Offset(x, yy), Size(12f, 8f))
            if (on) drawRect(col.copy(alpha = 0.25f), Offset(x - 2f, yy - 2f), Size(16f, 12f))
        }
    }
}

/** Header LED strip, 1000 x 8 units: a scanning light that swells with the output level. */
fun DrawScope.drawLedStrip(a: AeAnim) = scale(size.width / 1000f, Offset.Zero) {
    val pos = (a.t * 0.35f) % 1.4f - 0.2f
    for (i in 0 until 100) {
        val d = abs(i / 100f - pos)
        val b = 0.1f + min(1f, a.lv * 6f) * cl(1f - d * 4f) * 0.9f + a.fcm * 0.12f
        drawRect(CY.copy(alpha = cl(b)), Offset(i * 10f + 1f, 0f), Size(8f, 8f))
    }
}

/** AHDSR graph, 1076 x 300 units. Drag zones (share of the width): attack < 20 %, hold < 36 %, decay+sustain < 62 %, release > 74 %. */
fun DrawScope.drawLeadEnv(y: (Int) -> Float) = scale(size.width / 1076f, Offset.Zero) {
    val w = 1076f; val h = 300f
    val xa = 14f + y(12) * 0.17f * w; val xh = xa + 10f + y(13) * 0.14f * w; val xd = xh + 14f + y(14) * 0.2f * w
    val yS = h - 14f - y(15) * (h - 40f); val xs = xd + 0.12f * w; val xr = min(xs + 14f + y(16) * 0.2f * w, w - 6f)
    for (i in 1 until 10) drawLine(Color(0x1419E3FF), Offset(i * w / 10f, 0f), Offset(i * w / 10f, h), 1f)
    val pts = listOf(Offset(6f, h - 8f), Offset(xa, 16f), Offset(xh, 16f), Offset(xd, yS), Offset(xs, yS), Offset(xr, h - 8f))
    drawPath(path(pts).apply { lineTo(6f, h - 8f); close() }, Brush.verticalGradient(listOf(Color(0x5519E3FF), Color(0x0519E3FF)), 0f, h))
    glow(path(pts), 3.2f, Color.White)
    pts.subList(1, 5).forEach { drawCircle(Color.White, 8f, it) }
}

/** Filter response, 700 x 190 units. The bright band follows the envelope-swept cutoff while keys are held. */
fun DrawScope.drawLeadFilter(a: AeAnim, y: (Int) -> Float) = scale(size.width / 700f, Offset.Zero) {
    val w = 700f; val h = 190f; val fc = 40f * 500f.pow(y(18)); val q = 0.5f + y(19) * y(19) * 8f; val ft = (y(17) * 2).roundToInt()
    fun yy(m: Float) = h - cl((20f * log10(max(m, 1e-3f)) + 36f) / 54f) * h * 0.86f - 8f
    val pts = (0..120).map { i -> Offset(i / 120f * w, yy(curveF(40f * 500f.pow(i / 120f), fc, q, ft))) }
    drawPath(path(pts).apply { lineTo(w, h); lineTo(0f, h); close() }, Brush.verticalGradient(listOf(Color(0x5519E3FF), Color(0x0519E3FF)), 0f, h))
    glow(path(pts), 3f, Color.White)
    drawCircle(Color.White, 9f, Offset(y(18) * w, yy(curveF(fc, fc, q, ft))))
    if (a.fcm > 0.02f) {
        val f2 = min(18000f, fc * 2f.pow(y(20) * 6f * a.fcm)); val x = ln(f2 / 40f) / ln(500f) * w
        drawRect(Brush.horizontalGradient(listOf(Color(0x00FFFFFF), Color(0x55FFFFFF), Color(0x00FFFFFF)), x - 18f, x + 18f), Offset(x - 18f, 0f), Size(36f, h))
    }
}

/** Delay taps, 520 x 110 units. */
fun DrawScope.drawDelayViz(y: (Int) -> Float) = scale(size.width / 520f, Offset.Zero) {
    val dt = 0.06f + y(21) * y(21) * 0.84f; val fb = y(22) * 0.82f; val mx = y(23) * 0.8f
    for (i in 0 until 7) {
        val x = 20f + i * dt / 0.9f * 430f; val g = if (i == 0) 1f else mx * fb.pow(i - 1)
        if (x > 510f) break
        drawRect(CY.copy(alpha = cl(0.25f + 0.6f * g)), Offset(x, 100f - g * 80f), Size(10f, g * 80f))
    }
}

/** Reverb decay, 520 x 110 units. */
fun DrawScope.drawReverbViz(y: (Int) -> Float) = scale(size.width / 520f, Offset.Zero) {
    val sz = 0.5f + y(24) * 2.5f; val pts = ArrayList<Offset>(); var x = 0f
    while (x <= 500f) { pts.add(Offset(x + 10f, 100f - 90f * exp(-x / 500f * 3f / (sz / 3f)) * (0.2f + y(25) * 0.8f))); x += 4f }
    glow(path(pts), 2.4f)
}

/** Tone tilt and chorus shimmer, 560 x 110 units. */
fun DrawScope.drawToneViz(y: (Int) -> Float, t: Float) = scale(size.width / 560f, Offset.Zero) {
    val pts = ArrayList<Offset>(); var x = 0f
    while (x <= 540f) { val u = x / 540f; pts.add(Offset(x + 10f, 55f - (y(27) - 0.5f) * 70f * (u - 0.5f) * 2f + sin(u * 9f + t) * y(26) * 10f)); x += 6f }
    glow(path(pts), 2.4f)
}

/** Vibrato trace, 620 x 200 units. */
fun DrawScope.drawVibViz(y: (Int) -> Float, t: Float, held: Boolean) = scale(size.width / 620f, Offset.Zero) {
    val pts = ArrayList<Offset>(); var x = 0f
    while (x <= 620f) { pts.add(Offset(x, 100f + sin(x / 620f * 6.283f * 4f + t * 5.4f) * y(30) * 80f * (if (held) 1f else 0.3f))); x += 4f }
    glow(path(pts), 2.4f)
}

/** Played keys as LEDs, 1000 x 150 units (C3..C5). */
fun DrawScope.drawLeadKeys(isHeld: (Int) -> Boolean) = scale(size.width / 1000f, Offset.Zero) {
    val wn = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    for (i in 0 until 15) {
        val n = if (i == 14) 72 else 48 + (i / 7) * 12 + wn[i % 7]; val on = isHeld(n)
        drawRect(if (on) CY else Color(0xFF0C1A2A), Offset(i * 66f + 3f, 50f), Size(60f, 90f))
        if (on) drawRect(CY.copy(alpha = 0.3f), Offset(i * 66f - 3f, 44f), Size(72f, 102f))
    }
}
