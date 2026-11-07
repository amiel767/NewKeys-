package com.soundstage.mixer.instruments

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.*

/** Animation state, stepped by the window loop (30 fps when active, ~12 fps idle). Canvases read [frame] while drawing only. */
class AeAnim {
    var t = 0f; var rot = 0f; var fcm = 0f; var lv = 0f; var seen = 0
    /** Leads LED meters (per layer) and sax breath particles [x, y, vx, vy, life, r] in image-normalised coordinates. */
    val la = FloatArray(5); val parts = ArrayList<FloatArray>()
    /** Retired particles, kept so the breath emitter does not allocate a new FloatArray every frame. */
    private val spareParts = ArrayList<FloatArray>()
    val pulses = ArrayList<FloatArray>()
    val frame = mutableIntStateOf(0)
    /** Piano : 34 niveaux d'enveloppe (un par touche affichee) et un temps propre. */
    val pianoE = FloatArray(34)
    var pianoT = 0f

    private fun spawnPart(x: Float, y: Float, vx: Float, vy: Float, life: Float, r: Float) {
        val p = if (spareParts.isNotEmpty()) spareParts.removeAt(spareParts.size - 1) else FloatArray(6)
        p[0] = x; p[1] = y; p[2] = vx; p[3] = vy; p[4] = life; p[5] = r
        parts.add(p)
    }

    fun step(dt: Float, s: AeSession) {
        t += dt; lv += (s.level - lv) * 0.4f
        val held = s.held.isNotEmpty()
        fcm += ((if (held) 1f else 0f) - fcm) * 0.18f
        if (AeSections.isKeyboardLike(s.instrument)) {
            val dtc = if (dt > 0f && dt < 0.1f) dt else 0.016f
            s.held.keys.forEach { pianoE[((it - 36) % pianoE.size + pianoE.size) % pianoE.size] = 1f }
            val rel = 2.2f - s.values[3] * 1.6f
            for (k in pianoE.indices) pianoE[k] *= exp(-dtc * rel)
        }
        if (s.instrument == AeInstrument.SYNTH) {
            val l = orbLevel()
            rot += dt * (0.15f + l * 1.2f)
            if (s.noteEvents.intValue != seen) { seen = s.noteEvents.intValue; pulses.add(floatArrayOf(0f)) }
            val it = pulses.iterator(); while (it.hasNext()) { val p = it.next(); p[0] += dt; if (p[0] > 1.2f) it.remove() }
        }
        if (s.instrument == AeInstrument.LEADS) for (i in 0 until 5) la[i] += ((if (held) s.values[i] * (0.55f + 0.45f * min(1f, lv * 5f)) else 0f) - la[i]) * 0.3f
        if (s.instrument == AeInstrument.SAX) {
            val g = AeSaxGeo.of((s.values[0] * 3).roundToInt().coerceIn(0, 3)); val l = orbLevel()
            val n = (l * 18f).toInt() + (if (Math.random() < l * 10f) 1 else 0)
            repeat(n) { spawnPart(g.bell.first + (Math.random().toFloat() - 0.5f) * 0.02f, g.bell.second, 0.05f + Math.random().toFloat() * 0.1f, -0.06f - Math.random().toFloat() * 0.12f, 1f, 0.006f + Math.random().toFloat() * 0.014f) }
            val it2 = parts.iterator()
            while (it2.hasNext()) { val p = it2.next(); p[4] -= dt * 0.9f; p[0] += p[2] * dt; p[1] += p[3] * dt; p[3] -= dt * 0.03f; if (p[4] <= 0f) { it2.remove(); spareParts.add(p) } }
        }
        frame.intValue++
    }
    /** Orb reacts to the real output level, with a floor while keys are held (host without a meter). */
    fun orbLevel(): Float = (max(lv * 3.2f, fcm * 0.28f)).coerceIn(0f, 1f)
}

private fun cl(v: Float) = v.coerceIn(0f, 1f)

// ───────── Synth ─────────
/** The sphere: glow tinted by the cutoff, glass body, orbiting dotted rings that swell with the level, ripple per note. 500 x 500 units. */
fun DrawScope.drawOrb(a: AeAnim, y: (Int) -> Float) = scale(size.width / 500f, Offset.Zero) {
    val l = a.orbLevel(); val cx = 250f; val cy = 250f; val hue = 285f - y(7) * 95f
    drawCircle(Brush.radialGradient(listOf(Color.hsl(hue, 0.9f, 0.62f, 0.28f + l * 0.5f), Color.hsl(hue, 0.9f, 0.5f, 0f)), Offset(cx, cy), 250f), 250f, Offset(cx, cy))
    val r0 = 92f + l * 14f
    drawCircle(Brush.radialGradient(0f to Color(0xFFFFF5FF), 0.28f to Color.hsl((hue + 20f).mod(360f), 0.9f, 0.7f), 0.7f to Color.hsl((hue - 20f).mod(360f), 0.8f, 0.45f), 1f to Color(0xFF140A3A), center = Offset(cx - 30f, cy - 38f), radius = r0 * 1.3f), r0, Offset(cx, cy))
    for (i in 0 until 6) {
        val an = a.rot * 0.6f + i * 1.047f
        rotate(a.rot * 0.3f * 57.3f, Offset(cx, cy)) { drawOval(Color(0x44FFFFFF), Offset(cx - r0 * 0.92f, cy - r0 * 0.92f * abs(cos(an))), Size(r0 * 1.84f, r0 * 1.84f * abs(cos(an))), style = Stroke(1.4f)) }
    }
    drawCircle(Brush.radialGradient(listOf(Color(0xAAFFFFFF), Color(0x00FFFFFF)), Offset(cx - 34f, cy - 44f), 50f), 50f, Offset(cx - 34f, cy - 44f))
    for (r in 0 until 3) {
        val rr0 = r0 + 22f + r * 22f; val n = 70 + r * 20
        for (i in 0 until n) {
            val an = i.toFloat() / n * 6.283f + a.rot * (if (r % 2 == 1) -1f else 1f) * (0.6f + r * 0.2f)
            val wob = sin(an * (3 + r * 2) + a.t * (1.4f + y(16) * 3f)) * (3f + l * 38f) * (0.5f + y(17))
            drawCircle(Color.hsl((hue + r * 30f).mod(360f), 0.95f, (0.72f - r * 0.06f), (0.55f - r * 0.1f + l * 0.3f).coerceIn(0f, 1f)), 1.6f + l * 1.4f, Offset(cx + cos(an) * (rr0 + wob), cy + sin(an) * (rr0 + wob)))
        }
    }
    a.pulses.forEach { p -> val u = p[0] / 1.2f; drawCircle(Color.hsl((hue + 30f).mod(360f), 1f, 0.8f, (1f - u) * 0.7f), r0 + 10f + u * 130f, Offset(cx, cy), style = Stroke(3f * (1f - u) + 1f)) }
}

private fun curveFt(f: Float, fc: Float, q: Float, ft: Int): Float {
    val r = f / fc; val d = sqrt((1f - r * r).pow(2) + (r / q).pow(2))
    return when (ft) { 0 -> 1f / d; 1 -> r * r / d; 2 -> (r / q) / d; else -> abs(1f - r * r) / d }
}
private fun dbY(m: Float, h: Float) = h - cl((20f * log10(max(m, 1e-3f)) + 36f) / 54f) * h * 0.88f - 6f

/** Filter response, 500 x 150 units. The bright band follows the envelope-swept cutoff while keys are held. */
fun DrawScope.drawFilter(a: AeAnim, y: (Int) -> Float) = scale(size.width / 500f, Offset.Zero) {
    val w = 500f; val h = 150f; val fc = 20f * 1000f.pow(y(7)); val q = 0.5f + y(8) * y(8) * 8f; val ft = (y(11) * 3).roundToInt()
    val fill = Path().apply { moveTo(0f, h); for (i in 0..120) lineTo(i / 120f * w, dbY(curveFt(20f * 1000f.pow(i / 120f), fc, q, ft), h)); lineTo(w, h); close() }
    drawPath(fill, Brush.verticalGradient(listOf(Color(0x88E05CFF), Color(0x085A3CFF)), 0f, h))
    for (i in 1 until 4) drawLine(Color(0x10FFFFFF), Offset(i * w / 4f, 0f), Offset(i * w / 4f, h), 1f)
    val line = Path().apply { for (i in 0..120) { val yy = dbY(curveFt(20f * 1000f.pow(i / 120f), fc, q, ft), h); if (i == 0) moveTo(0f, yy) else lineTo(i / 120f * w, yy) } }
    drawPath(line, Color(0x55C06AFF), style = Stroke(9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(line, Color.White, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(Color.White, 7f, Offset(y(7) * w, dbY(curveFt(fc, fc, q, ft), h)))
    if (a.fcm > 0.02f) {
        val f2 = min(18000f, fc * 2f.pow(y(10) * 7f * a.fcm)); val x = (ln(f2 / 20f) / ln(1000f) * w)
        drawRect(Brush.horizontalGradient(listOf(Color(0x00FFFFFF), Color(0x55FFFFFF), Color(0x00FFFFFF)), x - 14f, x + 14f), Offset(x - 14f, 0f), Size(28f, h))
    }
}

/** ADSR, 500 x 100 units: attack / decay+sustain / release nodes (same hit zones as the preview: x < 28 %, < 55 %, > 70 %). */
fun DrawScope.drawEnv(y: (Int) -> Float) = scale(size.width / 500f, Offset.Zero) {
    val w = 500f; val h = 100f; val x1 = y(12) * 0.28f * w + 8f; val x2 = x1 + y(13) * 0.27f * w + 8f; val yS = h - 8f - y(14) * (h - 24f)
    val x3 = w * 0.7f; val x4 = min(x3 + y(15) * 0.3f * w - 6f, w - 4f)
    val p = Path().apply { moveTo(4f, h - 6f); lineTo(x1, 8f); lineTo(x2, yS); lineTo(x3, yS); lineTo(x4, h - 6f) }
    val f = Path().apply { addPath(p); close() }
    drawPath(f, Brush.verticalGradient(listOf(Color(0x77E05CFF), Color(0x085A3CFF)), 0f, h))
    drawPath(p, Color(0x55C06AFF), style = Stroke(8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(p, Color.White, style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    listOf(Offset(x1, 8f), Offset(x2, yS), Offset(x4, h - 6f)).forEach { drawCircle(Color.White, 5f, it) }
}

/** Oscillator morph shape, 480 x 84 units. [phase] scrolls it while keys are held. */
fun DrawScope.drawWave(v: Float, phase: Float) = scale(size.width / 480f, Offset.Zero) {
    val w = 480f; val h = 84f; val p = v * 3f; val s0 = min(3, p.toInt()); val s1 = min(3, s0 + 1); val fr = p - s0
    fun sh(s: Int, x0: Float): Float { val x = ((x0 % 1f) + 1f) % 1f; return when (s) { 0 -> sin(x * 6.283f); 1 -> if (x < 0.5f) 4f * x - 1f else 3f - 4f * x; 2 -> 2f * x - 1f; else -> if (x < 0.5f) 1f else -1f } }
    val path = Path(); var i = 0f
    while (i <= w) { val yy = h / 2f - (sh(s0, i / w * 2f + phase) * (1f - fr) + sh(s1, i / w * 2f + phase) * fr) * (h * 0.38f); if (i == 0f) path.moveTo(0f, yy) else path.lineTo(i, yy); i += 3f }
    drawPath(path, Color(0x66E05CFF), style = Stroke(8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, Color.White, style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** LFO scope, 560 x 110 units. */
fun DrawScope.drawLfo(rate: Float, shape: Int, depth: Float, phase: Float) = scale(size.width / 560f, Offset.Zero) {
    val w = 560f; val h = 110f; val path = Path(); var i = 0f
    while (i <= w) {
        val f = ((i / w) * 3f * (0.4f + log10(rate * 10f) * 0.9f) + phase) % 1f
        val v = when (shape) { 0 -> sin(f * 6.283f); 1 -> if (f < 0.5f) 4f * f - 1f else 3f - 4f * f; 2 -> if (f < 0.5f) 1f else -1f; else -> 2f * f - 1f }
        val yy = h / 2f - v * (h * 0.4f) * (0.2f + depth * 0.8f); if (i == 0f) path.moveTo(0f, yy) else path.lineTo(i, yy); i += 3f
    }
    drawPath(path, Color(0x66E05CFF), style = Stroke(8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, Color.White, style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * Output level history for one instrument, newest on the right.
 *
 * This is a real readout, not filler: [peak] is the same strip peak the mixer's meters use, so
 * the trace follows what the instrument is actually putting out. It is drawn on a log-ish scale
 * because a linear bar spends most of its life near zero and reads as dead.
 */
fun DrawScope.drawLevelTrace(peak: Float, t: Float, accent: Color) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    drawRoundRect(Color(0xFF0A0E15), Offset.Zero, size, CornerRadius(h * 0.10f))
    drawRoundRect(Color(0x1AFFFFFF), Offset.Zero, size, CornerRadius(h * 0.10f), style = Stroke(1f))
    // Graticule and a dim floor of bars: a meter at rest still has to read as a meter, otherwise
    // the panel looks like an empty box whenever nothing is playing.
    for (i in 1..3) {
        val gy = h * i / 4f
        drawLine(Color(0x1FFFFFFF), Offset(0f, gy), Offset(w, gy), 1f)
    }
    val cols = 60
    val cw = w / cols
    // A 22 % floor, not a hairline: at rest the meter still has to show its scale. A 5 % floor
    // was invisible, so the band read as an empty panel until something was played.
    val floor = (h - 4f) * 0.22f
    for (i in 0 until cols) {
        drawRoundRect(
            accent.copy(alpha = 0.34f),
            Offset(i * cw + cw * 0.18f, h - 2f - floor), Size(cw * 0.64f, floor), CornerRadius(cw * 0.32f),
        )
    }
    for (i in 0 until cols) {
        // The current peak is the rightmost bar; everything left of it is a decaying tail, which
        // is what makes the strip read as history rather than as one number.
        val k = 1f - (cols - 1 - i) / (cols - 1f)
        val amp = peak * Math.pow(k.toDouble(), 1.6).toFloat() * (0.55f + 0.45f * sin((t * 6f + i * 0.7f).toDouble()).toFloat())
        val bh = (h - 4f) * amp.coerceIn(0f, 1f)
        if (bh < 0.6f) continue
        drawRoundRect(
            if (amp > 0.85f) Color(0xFFFF5A6E) else accent,
            Offset(i * cw + cw * 0.18f, h - 2f - bh), Size(cw * 0.64f, bh), CornerRadius(cw * 0.32f),
        )
    }
}
