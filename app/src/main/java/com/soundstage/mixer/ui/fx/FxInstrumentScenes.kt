package com.soundstage.mixer.ui.fx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Mutable animation state shared by the instrument scenes. The preview keeps the
 * same accumulators as module-level globals; here they live in one holder owned
 * by the window so a recomposition never resets an animation.
 */
class FxSceneState {
    var presetIndex = 0

    val pianoE = FloatArray(64)
    var pianoEv = -1
    var pianoT = 0f

    var organT = 0f
    var organLs = 0f
    var organLp = 0f

    val saxP = mutableListOf<FloatArray>()
    var saxT = 0f

    val strP = mutableListOf<FloatArray>()
    var strSol = 0f
    var strT = 0f

    val gtrE = FloatArray(6)
    var gtrT = 0f

    val bsP = mutableListOf<FloatArray>()
    var bt = 0f
    var ampMute = -1
    var ampMuteT = 0f
}

// ---------------------------------------------------------------------------
// Geometry shared by the scenes (paths built once, as in the preview).
// ---------------------------------------------------------------------------

private fun chaikin(points: List<FloatArray>, n: Int): List<FloatArray> {
    var p = points
    repeat(n) {
        val q = mutableListOf(p[0])
        for (i in 0 until p.size - 1) {
            val a = p[i]; val b = p[i + 1]
            q.add(floatArrayOf(0.75f * a[0] + 0.25f * b[0], 0.75f * a[1] + 0.25f * b[1]))
            q.add(floatArrayOf(0.25f * a[0] + 0.75f * b[0], 0.25f * a[1] + 0.75f * b[1]))
        }
        q.add(p.last())
        p = q
    }
    return p
}

private fun resample(p: List<FloatArray>, n: Int): List<FloatArray> {
    val l = FloatArray(p.size)
    for (i in 1 until p.size) l[i] = l[i - 1] + hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1])
    val total = l.last()
    val out = ArrayList<FloatArray>(n)
    var j = 0
    for (k in 0 until n) {
        val d = total * k / (n - 1)
        while (j < l.size - 2 && l[j + 1] < d) j++
        val f = (d - l[j]) / (l[j + 1] - l[j]).let { if (it == 0f) 1f else it }
        out.add(floatArrayOf(
            p[j][0] + (p[j + 1][0] - p[j][0]) * f,
            p[j][1] + (p[j + 1][1] - p[j][1]) * f,
        ))
    }
    return out
}

private val SXP = resample(chaikin(listOf(
    floatArrayOf(40f, 14f), floatArrayOf(120f, 22f), floatArrayOf(200f, 44f), floatArrayOf(310f, 84f),
    floatArrayOf(430f, 126f), floatArrayOf(530f, 164f), floatArrayOf(600f, 184f), floatArrayOf(660f, 170f),
    floatArrayOf(698f, 130f), floatArrayOf(712f, 86f), floatArrayOf(718f, 60f)), 3), 160)

private val BXP = resample(chaikin(listOf(
    floatArrayOf(40f, 34f), floatArrayOf(180f, 34f), floatArrayOf(180f, 112f), floatArrayOf(360f, 112f),
    floatArrayOf(360f, 34f), floatArrayOf(500f, 34f), floatArrayOf(500f, 112f), floatArrayOf(560f, 112f),
    floatArrayOf(640f, 100f), floatArrayOf(700f, 80f)), 3), 150)

private val FW = FloatArray(16) { k -> 110f + 759f * (1f - 2.0.pow(-k / 12.0).toFloat()) }
private val GCH = listOf(
    intArrayOf(-1, 0, 2, 2, 1, 0), intArrayOf(3, 2, 0, 0, 0, 3),
    intArrayOf(-1, 3, 2, 0, 1, 0), intArrayOf(1, 3, 3, 2, 1, 1))

private fun hsl(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = (((h % 360f) + 360f) % 360f) / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r, g, b) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(r + m, g + m, b + m, alpha)
}

private fun parseHex(s: String): Color {
    val v = s.removePrefix("#")
    return when (v.length) {
        6 -> Color(0xFF000000L.toInt() or v.toLong(16).toInt())
        8 -> Color(v.toLong(16).toInt())
        else -> Color.Gray
    }
}

private fun DrawScope.rrect(x0: Float, y0: Float, w: Float, h: Float, r: Float): Path = Path().apply {
    moveTo(x0 + r, y0)
    arcTo(Rect(x0 + w - r * 2, y0, x0 + w, y0 + r * 2), -90f, 90f, false)
    arcTo(Rect(x0 + w - r * 2, y0 + h - r * 2, x0 + w, y0 + h), 0f, 90f, false)
    arcTo(Rect(x0, y0 + h - r * 2, x0 + r * 2, y0 + h), 90f, 90f, false)
    arcTo(Rect(x0, y0, x0 + r * 2, y0 + r * 2), 180f, 90f, false)
    close()
}

private fun DrawScope.tag(tm: TextMeasurer, text: String, cx: Float, cy: Float, alignCenter: Boolean = true) {
    val layout = tm.measure(text, style = TextStyle(color = Color(0xCCF3E6CC), fontSize = 8.sp,
        fontFamily = FxFonts.mono))
    drawText(layout, topLeft = Offset(if (alignCenter) cx - layout.size.width / 2f else cx, cy - 8f))
}

private fun DrawScope.mic(cx: Float, cy: Float, r: Float, col: Color = Color(0xFF2A2E34)) {
    drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFAEB6C0), col),
        center = Offset(cx - r * 0.3f, cy - r * 0.3f), radius = r), radius = r, center = Offset(cx, cy))
    for (k in -2..2) {
        drawLine(Color(0x88000000), Offset(cx - r * 0.8f, cy + k * r * 0.32f),
            Offset(cx + r * 0.8f, cy + k * r * 0.32f), 0.8f)
    }
}

// ---------------------------------------------------------------------------
// Instrument scenes.
// ---------------------------------------------------------------------------

fun DrawScope.scenePiano(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    val hard = v[0]; val lid = v[1]; val sus = v[2]; val room = v[3]; val wid = v[4]
    val tone = v[5]; val body = v[6]; val space = v[7]
    val kind = intArrayOf(0, 1, 2, 3, 0, 1).getOrElse(st.presetIndex) { 0 }
    val brt = 0.2f + 0.65f * lid * (0.55f + 0.45f * tone)
    val pal = listOf(
        listOf("#4a2a16", "#1a0d07"), listOf("#1b2027", "#0b0e12"),
        listOf("#5a341c", "#22110a"), listOf("#101018", "#050508"))[kind]

    drawRect(brush = Brush.linearGradient(listOf(parseHex(pal[0]), parseHex(pal[1])),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    for (k in 0 until 24) {
        val yy = y0 + rh * k / 24f
        val p = Path()
        var u = 0f
        while (u <= rw) {
            val y = yy + sin(u * 0.01f + k) * 3f + sin(u * 0.043f + k * 2f) * 1.2f
            if (u == 0f) p.moveTo(x0 + u, y) else p.lineTo(x0 + u, y)
            u += 12f
        }
        drawPath(p, Color(0x11000000), style = Stroke(width = 1f))
    }
    val lx = if (kind == 3) x0 + rw * 0.5f else x0 + rw * 0.22f
    drawCircle(brush = Brush.radialGradient(
        listOf(Color(0xFFFFE2AA).copy(alpha = 0.1f + 0.32f * brt), Color.Transparent),
        center = Offset(lx, y0), radius = rh * 1.1f), radius = rh * 1.1f, center = Offset(lx, y0))

    val nS = 46
    val top = y0 + rh * 0.2f
    val bot = y0 + rh * 0.6f
    val dt = max(0f, min(0.1f, t - st.pianoT)); st.pianoT = t
    val ev = floor(t * 2.4f).toInt()
    if (ev != st.pianoEv) { st.pianoEv = ev; st.pianoE[(ev * 17 + 5) % nS] = 1f }
    for (k in 0 until nS) st.pianoE[k] *= exp(-dt * (2.6f - sus * 2.2f))

    val harp = Path().apply {
        moveTo(x0 + rw * 0.03f, bot + 8f)
        for (k in 0..24) {
            val u = k / 24f
            lineTo(x0 + rw * (0.03f + 0.94f * u), top + (bot - top) * 0.5f * u.pow(0.9f) - 12f)
        }
        lineTo(x0 + rw * 0.97f, bot + 8f); close()
    }
    drawPath(harp, Color(0x29C9A45C))
    if (kind == 2) drawRect(Color(0xD98A1C24), topLeft = Offset(x0, top + (bot - top) * 0.38f),
        size = Size(rw, 9f))

    for (k in 0 until nS) {
        val u = k / (nS - 1f)
        val px = x0 + rw * (0.05f + 0.9f * u)
        val y0s = top + (bot - top) * 0.5f * u.pow(0.9f)
        val len = bot - y0s
        val pb = px + (x0 + rw * 0.5f - px) * 0.1f
        val amp = 3.6f * st.pianoE[k]
        val p = Path()
        for (s in 0..10) {
            val yy = y0s + len * s / 10f
            val dx = sin(Math.PI.toFloat() * s / 10f) * amp * sin(t * (40f + u * 60f))
            val xx = px + (pb - px) * s / 10f + dx
            if (s == 0) p.moveTo(xx, yy) else p.lineTo(xx, yy)
        }
        drawPath(p, color = if (kind == 1) Color(0xFFC9D3DF) else if (u < 0.3f) Color(0xFFD49A5A) else Color(0xFFE6DCC2),
            style = Stroke(width = 0.8f + (1f - u) * 1.5f, cap = StrokeCap.Round), alpha = 0.5f + 0.45f * brt)
        drawCircle(Color(0xFFB99A58), radius = 2.2f, center = Offset(px, y0s - 5f))
        if (brt > 0.25f && st.pianoE[k] > 0.15f) {
            drawRect(Color.White, topLeft = Offset(px - 1f, y0s + len * (0.3f + 0.4f * sin(t * 3f + k))),
                size = Size(2f, 6f), alpha = st.pianoE[k] * brt)
        }
    }
    val hw = (rw * 0.9f / nS) * 2.3f * (0.6f + 0.7f * hard)
    for (k in 0 until nS step 2) {
        val u = k / (nS - 1f)
        val px0 = x0 + rw * (0.05f + 0.9f * u)
        val px = px0 + (x0 + rw * 0.5f - px0) * 0.1f
        val hh = 20f
        val hy = bot + 4f - max(0f, st.pianoE[k] - 0.55f) * 40f
        drawRect(Color(0xFF2A1810), topLeft = Offset(px - 1.5f, hy + hh * 0.5f), size = Size(3f, 26f))
        val f = 0.55f + 0.4f * (1f - hard)
        drawOval(brush = Brush.radialGradient(
            listOf(Color((240 * f).toInt(), (228 * f).toInt(), (200 * f).toInt()), Color(0xFF6B5A3A)),
            center = Offset(px, hy), radius = hh), topLeft = Offset(px - hw / 2f, hy - hh * 0.55f),
            size = Size(hw, hh * 1.1f))
    }
    if (kind == 3) drawRect(brush = Brush.linearGradient(
        listOf(Color.Transparent, Color(0x14FFFFFF), Color.Transparent),
        start = Offset(x0, y0), end = Offset(x0 + rw, y0 + rh * 0.5f)),
        topLeft = Offset(x0, y0), size = Size(rw, rh * 0.5f))
    if (space > 0.05f) {
        val u = (t * 0.3f) % 1f
        drawOval(Color(0xFFFFE2AA).copy(alpha = (1f - u) * space * 0.4f),
            topLeft = Offset(x0 + rw * 0.5f - rw * (0.15f + u * 0.6f), y0 + rh * 0.5f - rh * (0.1f + u * 0.35f)),
            size = Size(rw * (0.15f + u * 0.6f) * 2, rh * (0.1f + u * 0.35f) * 2),
            style = Stroke(width = 2f))
    }
    drawCircle(brush = Brush.radialGradient(listOf(Color.Transparent, Color(0xAA000000)),
        center = Offset(x0 + rw / 2f, y0 + rh / 2f), radius = rw * 0.62f),
        radius = rw * 0.62f, center = Offset(x0 + rw / 2f, y0 + rh / 2f))

    val ly = y0 + 16f + (1f - lid) * 100f
    drawPath(Path().apply {
        moveTo(440f, y0); lineTo(x0 + rw, y0); lineTo(x0 + rw, ly + 22f); lineTo(540f, ly); close()
    }, brush = Brush.linearGradient(listOf(Color(0xFF05060A), Color(0xFF1A1D26)),
        start = Offset(0f, y0), end = Offset(0f, ly)))
    drawLine(Color(0xFFC9A45C), Offset(720f, 40f), Offset(720f, ly + 16f), 2f)
    mic(720f, 40f, 9f, Color(0xFF6B5428))
    tag(tm, "LID ${(lid * 100).roundToInt()}%", 720f, 62f)

    drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFF6E6BD), Color(0xFF8A6A2C)),
        center = Offset(40f, 154f), radius = 16f), radius = 15f, center = Offset(44f, 158f))
    val an = Math.toRadians((-135f + 270f * hard).toDouble()).toFloat()
    drawLine(Color(0xFF2A1A10), Offset(44f + 4f * cos(an), 158f + 4f * sin(an)),
        Offset(44f + 12f * cos(an), 158f + 12f * sin(an)), 2.4f)
    tag(tm, "FELT ${(hard * 100).roundToInt()}%", 44f, 184f)

    for ((k, px) in listOf(350f, 389f, 428f).withIndex()) {
        val dy = if (k == 2) sus * 12f else 0f
        drawPath(Path().apply {
            moveTo(px - 12f, 196f + dy); lineTo(px + 12f, 196f + dy)
            lineTo(px + 9f, 181f + dy); lineTo(px - 9f, 181f + dy); close()
        }, brush = Brush.linearGradient(listOf(Color(0xFF7A5E2A), Color(0xFFF2D58A), Color(0xFF6B5224)),
            start = Offset(px - 14f, 0f), end = Offset(px + 14f, 0f)))
    }
    tag(tm, "PEDAL ${(sus * 100).roundToInt()}%", 428f, 212f)

    val my = 30f + room * 110f
    drawLine(Color(0x40FFFFFF), Offset(80f, y0), Offset(80f, my), 1f)
    mic(80f, my, 11f)
    tag(tm, "ROOM ${(room * 100).roundToInt()}%", 80f, my + 24f)
    val rx = 430f + wid * 300f
    val lx2 = 778f - rx
    drawLine(Color(0x40FFFFFF), Offset(lx2, 118f), Offset(rx, 118f), 1f,
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f, 4f)))
    mic(lx2, 118f, 9f); mic(rx, 118f, 9f)
    tag(tm, "WIDTH ${(wid * 100).roundToInt()}%", rx, 140f)
}

fun DrawScope.sceneOrgan(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    val dtt = max(0f, min(0.1f, t - st.organT)); st.organT = t
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFF3D2314), Color(0xFF150A05)),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    for (k in 0 until 40) {
        val yy = y0 + rh * k / 40f
        val p = Path()
        var u = 0f
        while (u <= rw) {
            val y = yy + sin(u * 0.012f + k * 1.7f) * 4f
            if (u == 0f) p.moveTo(x0 + u, y) else p.lineTo(x0 + u, y)
            u += 14f
        }
        drawPath(p, Color(0x14000000), style = Stroke(width = 1f))
    }
    val cap = listOf("#6b3d22", "#6b3d22", "#f2eadb", "#f2eadb", "#1c1a18", "#f2eadb", "#1c1a18", "#1c1a18", "#f2eadb")
    val nm = listOf("16'", "5⅓'", "8'", "4'", "2⅔'", "2'", "1⅗'", "1⅓'", "1'")
    val up = FloatArray(9) { (v[it] * 8f).roundToInt().toFloat() }
    val lo = FloatArray(9) { (v[9 + it] * 8f).roundToInt().toFloat() }
    val tgt = floatArrayOf(0f, 2.2f, 9f)[(v[25] * 2f).roundToInt().coerceIn(0, 2)]
    st.organLs += (tgt - st.organLs) * (1f - exp(-dtt * (if (tgt > st.organLs) 0.9f else 0.5f)))
    st.organLp += st.organLs * dtt

    fun manual(ox: Float, vals: FloatArray, title: String) {
        drawPath(rrect(ox - 16f, 2f, 238f, 200f, 10f),
            brush = Brush.linearGradient(listOf(Color(0xFF1D1814), Color(0xFF0E0B09)), start = Offset(0f, 0f), end = Offset(0f, 200f)))
        drawPath(rrect(ox - 16f, 2f, 238f, 200f, 10f), Color(0x55C9A45C), style = Stroke(width = 1.2f))
        val lt = tm.measure(title, style = TextStyle(color = Color(0xFFF2D58A), fontSize = 9.sp,
            fontFamily = FxFonts.mono, fontWeight = FontWeight.Bold))
        drawText(lt, topLeft = Offset(ox - 6f, 16f - lt.size.height / 2f))
        val rt = tm.measure("${vals[0].toInt()}${vals[1].toInt()} ${vals[2].toInt()}${vals[3].toInt()}${vals[4].toInt()}${vals[5].toInt()} ${vals[6].toInt()}${vals[7].toInt()}${vals[8].toInt()}",
            style = TextStyle(color = Color(0xFFF2D58A), fontSize = 9.sp, fontFamily = FxFonts.mono, fontWeight = FontWeight.Bold))
        drawText(rt, topLeft = Offset(ox + 214f - rt.size.width, 16f - rt.size.height / 2f))
        for (k in 0 until 9) {
            val px = ox + k * 26f; val top = 38f; val len = 124f
            val cy = top + vals[k] / 8f * len
            drawPath(rrect(px - 3f, top - 6f, 6f, len + 12f, 3f), color = Color(0x99000000))
            drawRect(if (cap[k] == "#1c1a18") Color(0xFF555555) else parseHex(cap[k]),
                topLeft = Offset(px - 1.6f, top), size = Size(3.2f, cy - top))
            drawPath(rrect(px - 11f, cy - 8f, 22f, 16f, 4f), color = parseHex(cap[k]))
            val vt = tm.measure(vals[k].toInt().toString(), style = TextStyle(
                color = if (cap[k] == "#f2eadb") Color(0xFF2A1A10) else Color(0xFFF2EADB),
                fontSize = 9.sp, fontFamily = FxFonts.mono, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(px - vt.size.width / 2f, cy + 3.2f - vt.size.height / 2f))
            val nt = tm.measure(nm[k], style = TextStyle(color = Color(0xFFC9B88F), fontSize = 7.sp, fontFamily = FxFonts.mono))
            drawText(nt, topLeft = Offset(px - nt.size.width / 2f, top - 12f - nt.size.height / 2f))
        }
    }
    manual(24f, up, "UPPER")
    manual(278f, lo, "LOWER")

    val dcx = 578f; val dcy = 112f
    val vi = (v[22] * 5f).roundToInt().coerceIn(0, 5)
    val lab = listOf("V1", "V2", "V3", "C1", "C2", "C3")
    drawCircle(brush = Brush.radialGradient(listOf(Color(0xFF5B4630), Color(0xFF1E140C)),
        center = Offset(dcx - 8f, dcy - 8f), radius = 34f), radius = 24f, center = Offset(dcx, dcy))
    for (k in 0 until 6) {
        val an = Math.toRadians((-200f + k * 40f).toDouble()).toFloat()
        val lt = tm.measure(lab[k], style = TextStyle(color = if (k == vi) Color(0xFFF2D58A) else Color(0xFFC9B88F),
            fontSize = 8.sp, fontFamily = FxFonts.mono, fontWeight = FontWeight.Bold))
        drawText(lt, topLeft = Offset(dcx + 38f * cos(an) - lt.size.width / 2f, dcy + 38f * sin(an) + 3f - lt.size.height / 2f))
        drawCircle(if (k == vi) Color(0xFFF2D58A) else Color(0xFF5A4A30), radius = 1.6f,
            center = Offset(dcx + 29f * cos(an), dcy + 29f * sin(an)))
    }
    val va = Math.toRadians((-200f + vi * 40f).toDouble()).toFloat()
    drawLine(Color(0xFFF2EADB), Offset(dcx + 5f * cos(va), dcy + 5f * sin(va)),
        Offset(dcx + 20f * cos(va), dcy + 20f * sin(va)), 3f)
    tag(tm, "VIBRATO / CHORUS", dcx + 8f, 150f)

    val cx = 764f; val cy = 186f
    drawPath(rrect(cx - 42f, 150f, 84f, 54f, 8f), brush = Brush.linearGradient(
        listOf(Color(0xFF2A1A10), Color(0xFF3D2616), Color(0xFF1E120A)), start = Offset(cx - 44f, 0f), end = Offset(cx + 44f, 0f)))
    drawPath(rrect(cx - 42f, 150f, 84f, 54f, 8f), Color(0x44C9A45C), style = Stroke(width = 1f))
    drawOval(Color(0x99070503), topLeft = Offset(cx - 38f, 157f), size = Size(40f, 18f))
    val hx = cos(st.organLp * 1.2f) * 13f
    drawOval(Color(0xFFD9C08A), topLeft = Offset(cx - 18f + hx - (6f + abs(sin(st.organLp * 1.2f)) * 3f), 162f),
        size = Size((6f + abs(sin(st.organLp * 1.2f)) * 3f) * 2, 8f))
    drawOval(Color(0x99070503), topLeft = Offset(cx - 2f, 162f), size = Size(40f, 36f))
    for (k in 0 until 6) {
        val ph2 = st.organLp * 0.8f + k * 1.05f
        val px = cx + 18f + sin(ph2) * 15f
        drawRect(Color(0xFFC9A45C), topLeft = Offset(px - 1f, 166f), size = Size(2f, 28f),
            alpha = 0.25f + 0.6f * max(0f, cos(ph2)))
    }
}

fun DrawScope.sceneSax(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    val br = v[0]; val rd = v[1]; val gr = v[2]; val vb = v[3]; val vr = v[4]; val tone = v[5]; val body = v[6]; val sp = v[7]
    val kind = st.presetIndex % 6
    val pal = listOf(
        listOf("#0e2229", "#04090c", "#e0a45a"), listOf("#171c26", "#05080c", "#d9c8b0"),
        listOf("#2a0e12", "#080305", "#ff5a4a"), listOf("#101a3a", "#05070f", "#6ab0ff"),
        listOf("#12261e", "#060a07", "#ffd36a"), listOf("#1c1c28", "#06060a", "#c9a45c"))[kind]
    drawRect(brush = Brush.linearGradient(listOf(parseHex(pal[0]), parseHex(pal[1])),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    for (k in 0 until 5) {
        val bx = x0 + rw * (0.15f + 0.18f * k) + sin(t * 0.2f + k * 2f) * 60f
        val by = y0 + rh * (0.35f + 0.2f * sin(t * 0.15f + k))
        val r = 130f + 60f * sin(t * 0.3f + k)
        drawCircle(brush = Brush.radialGradient(listOf(parseHex(pal[2]).copy(alpha = 0.16f), Color.Transparent),
            center = Offset(bx, by), radius = r), radius = r, center = Offset(bx, by),
            alpha = 0.35f + 0.65f * br)
    }
    val n = SXP.size
    for (pass in 0 until 3) for (i in 1 until n) {
        val u = i / n.toFloat()
        val w = (9f + u * 9f + (if (u > 0.84f) ((u - 0.84f) / 0.16f).pow(2) * 30f else 0f)) *
                (if (pass == 0) 1.35f else if (pass == 1) 1f else 0.25f)
        val aa = SXP[i - 1]; val bb = SXP[i]
        val off = if (pass == 2) -2.5f else 0f
        val col = when (pass) {
            0 -> Color(0xFF2A1C08)
            1 -> hsl(34f + tone * 10f, 0.55f + body * 0.2f, 0.28f + tone * 0.24f)
            else -> Color(0xFFFFF0C8).copy(alpha = 0.3f + 0.4f * tone)
        }
        drawLine(col, Offset(aa[0] + off, aa[1] + off), Offset(bb[0] + off, bb[1] + off), w, cap = StrokeCap.Round)
    }
    val e = SXP[n - 1]; val e0 = SXP[n - 6]
    val ang = Math.toDegrees(kotlin.math.atan2(e[1] - e0[1], e[0] - e0[0]).toDouble()).toFloat()
    rotate(ang, pivot = Offset(e[0], e[1])) {
        drawOval(brush = Brush.radialGradient(listOf(Color(0xFFFFE2AA).copy(alpha = 0.5f + 0.5f * br), Color(0xFF1A0F04)),
            center = Offset(e[0], e[1]), radius = 30f), topLeft = Offset(e[0] - 10f, e[1] - 30f), size = Size(20f, 60f))
    }
    val dt = max(0f, min(0.1f, t - st.saxT)); st.saxT = t
    if (st.saxP.isEmpty()) repeat(90) {
        st.saxP.add(floatArrayOf(Random.nextFloat(), 0.6f + Random.nextFloat() * 0.8f, Random.nextFloat() * 6.283f))
    }
    for (p in st.saxP) {
        p[0] += dt * (0.1f + 0.9f * br) * p[1]
        if (p[0] > 1f) p[0] -= 1f
        val i = min(n - 2, floor(p[0] * n).toInt())
        val q = SXP[i]; val q2 = SXP[i + 1]
        val nx = -(q2[1] - q[1]); val ny = q2[0] - q[0]
        val nl = hypot(nx, ny).let { if (it == 0f) 1f else it }
        val off = sin(p[0] * 22f - t * (4f + vr * 14f)) * (3f + vb * 13f) + (Random.nextFloat() - 0.5f) * gr * 12f
        drawCircle(if (gr > 0.5f && p[2] > 3f) Color(0xFFFF7A5A) else Color(0xFFFFF3DC),
            radius = 1.2f + br * 1.6f, center = Offset(q[0] + nx / nl * off, q[1] + ny / nl * off),
            alpha = sin(p[0] * Math.PI.toFloat()) * (0.35f + 0.55f * br))
    }
    val ra = SXP[0]
    val reed = Path()
    for (k in 0..8) {
        val ux = ra[0] - 22f + k * 4f
        val uy = ra[1] - 8f + sin(t * (30f + rd * 40f) + k * 0.5f) * rd * 5f * (k / 8f)
        if (k == 0) reed.moveTo(ux, uy) else reed.lineTo(ux, uy)
    }
    drawPath(reed, Color(0xFFF0DCAE), style = Stroke(width = 2f))
    if (sp > 0.05f) {
        val u = (t * 0.35f) % 1f
        drawCircle(parseHex(pal[2]), radius = 30f + u * 150f, center = Offset(e[0], e[1]),
            style = Stroke(width = 2f), alpha = (1f - u) * sp * 0.5f)
    }
    val hs = listOf(
        floatArrayOf(0.2f, 0.32f, 0.44f, 0.56f, 0.68f).map { u ->
            val q = SXP[(u * 159f).roundToInt()]; floatArrayOf(q[0], q[1], 17f)
        })
    val nmL = listOf("BREATH", "REED", "GROWL", "VIBRATO", "RATE")
    hs[0].forEachIndexed { j, h ->
        val value = v[j]
        drawCircle(brush = Brush.radialGradient(
            listOf(Color.White, Color(0xFFE9E0D4), Color(0xFF9D8F7A)),
            center = Offset(h[0] - 4f, h[1] - 4f), radius = h[2]), radius = h[2] - 4f, center = Offset(h[0], h[1]))
        drawArc(parseHex(pal[2]), -135f, 270f * value, false,
            topLeft = Offset(h[0] - h[2], h[1] - h[2]), size = Size(h[2] * 2, h[2] * 2),
            style = Stroke(width = 2.4f))
        tag(tm, "${nmL[j]} ${(value * 100).roundToInt()}%", h[0], h[1] + h[2] + 13f)
    }
}

fun DrawScope.sceneStrings(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    val pr = v[0]; val spd = v[1]; val mot = v[2]; val mor = v[3]; val tim = v[4]
    val hue = v[5] * 360f; val tone = v[6]; val bd = v[7]; val sp = v[8]
    val dt = max(0f, min(0.1f, t - st.strT)); st.strT = t
    val act = (sin(t * 0.9f) + sin(t * 0.37f + 1f)) > -0.5f
    val target = if (act) 0.3f + 0.7f * pr else 0f
    st.strSol += (target - st.strSol) * (1f - exp(-dt * (0.6f + (1f - tim) * 3.4f)))
    val sol = st.strSol
    fun hs(o: Float, s2: Float = 85f, l: Float = 62f, al: Float = 1f) = hsl(hue + o, s2 / 100f, l / 100f, al)

    drawRect(brush = Brush.linearGradient(listOf(hs(0f, 45f, 13f), hs(20f, 50f, 3f)),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    listOf(Triple(0.2f, 0.3f, 0f), Triple(0.75f, 0.6f, 90f), Triple(0.5f, 0.1f, 200f)).forEachIndexed { k, (u, w, o) ->
        val bx = x0 + rw * u + sin(t * 0.2f + k) * 40f
        val by = y0 + rh * w
        drawCircle(brush = Brush.radialGradient(listOf(hs(o, 80f, 50f, 0.22f), Color.Transparent),
            center = Offset(bx, by), radius = 220f), radius = 220f, center = Offset(bx, by))
    }
    fun vp(): Path = Path().apply {
        moveTo(228f, 66f); addOval(Rect(150f - 78f, 66f - 52f, 150f + 78f, 66f + 52f))
        moveTo(348f, 66f); addOval(Rect(292f - 56f, 66f - 40f, 292f + 56f, 66f + 40f))
        addRect(Rect(205f, 34f, 255f, 98f))
    }
    val nc = 1 + (mor * 5f).roundToInt()
    for (k in nc - 1 downTo 1) {
        translate(210f, 66f) {
            scale(1f - 0.05f * k, 1f - 0.05f * k, pivot = Offset.Zero) {
                translate(-210f - 16f * k, -66f + (if (k % 2 == 1) 1f else -1f) * 12f * k) {
                    drawPath(vp(), hs(40f * k, 80f, 62f, 0.5f / k), style = Stroke(width = 1f))
                }
            }
        }
    }
    for (k in 0 until 5) {
        translate(210f, 66f) {
            scale(1f - 0.1f * k, 1f - 0.1f * k, pivot = Offset.Zero) {
                translate(-210f, -66f) {
                    drawPath(vp(), hs(k * 8f, 85f, 65f, (0.55f - 0.08f * k) * (0.5f + 0.5f * (1f - sol))),
                        style = Stroke(width = 0.9f))
                }
            }
        }
    }
    clipPath(vp()) {
        drawRect(brush = Brush.linearGradient(listOf(hs(10f, 70f, 28f + tone * 10f), hs(30f, 75f, 18f + bd * 8f)),
            start = Offset(70f, 0f), end = Offset(350f, 0f)), topLeft = Offset(60f, 10f), size = Size(300f, 116f),
            alpha = sol)
        val bigA = 2f + tone * 4f; val bigB = 3f + bd * 4f + sin(t * 0.2f) * 0.3f
        val thr = 0.16f + 0.08f * sin(t * 1.7f) * pr
        for (i in 0 until 40) for (j in 0 until 24) {
            val u = i / 39f; val w = j / 23f
            val f = cos(bigA * Math.PI.toFloat() * u) * cos(bigB * Math.PI.toFloat() * w) -
                    cos(bigB * Math.PI.toFloat() * u) * cos(bigA * Math.PI.toFloat() * w)
            if (abs(f) < thr) {
                drawRect(hs(50f, 60f, 88f, 0.35f + 0.45f * sol),
                    topLeft = Offset(72f + u * 274f + sin(i * 7f + j * 3f + t * 8f) * pr * 1.2f,
                        14f + w * 104f + cos(i * 5f + j * 9f + t * 7f) * pr * 1.2f),
                    size = Size(1.5f, 1.5f))
            }
        }
    }
    // particles: the body dissolves, the bow gathers them back
    var acc = (30f + 260f * (1f - sol)) * (0.5f + 0.5f * (mot + spd) / 1.5f) * dt
    while (acc >= 1f && st.strP.size < 480) {
        acc -= 1f
        val px = 72f + Random.nextFloat() * 274f
        val py = 14f + Random.nextFloat() * 104f
        val inBody = ((px - 150f) / 78f).pow(2) + ((py - 66f) / 52f).pow(2) < 1f ||
                     ((px - 292f) / 56f).pow(2) + ((py - 66f) / 40f).pow(2) < 1f ||
                     (px > 205f && px < 255f && py > 34f && py < 98f)
        if (inBody) st.strP.add(floatArrayOf(px, py, px, py, (Random.nextFloat() - 0.3f) * 14f, -4f - Random.nextFloat() * 16f,
            0f, 0.8f + sp * 3f + Random.nextFloat() * 1.2f, Random.nextFloat() * 60f, 1f + Random.nextFloat() * 2f))
    }
    for (i in st.strP.indices.reversed()) {
        val p = st.strP[i]
        p[6] += dt
        if (p[6] > p[7]) { st.strP[i] = st.strP.last(); st.strP.removeAt(st.strP.size - 1); continue }
        val kk = if (sol > 0.5f) 3f * sol else 0f
        p[4] += ((p[2] - p[0]) * kk * 2f + sin(t * 3f + p[1] * 0.1f) * mot * 30f) * dt
        p[5] += ((p[3] - p[1]) * kk * 2f) * dt
        p[4] *= 0.985f; p[5] *= 0.985f
        p[0] += p[4] * dt; p[1] += p[5] * dt
        drawCircle(hs(p[8], 85f, 70f), radius = p[9] * (1f - p[6] / p[7] * 0.4f),
            center = Offset(p[0], p[1]), alpha = (1f - p[6] / p[7]) * 0.9f)
    }
    // neck, scroll, strings, bridge, f-holes
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFF1A1210), Color(0xFF0A0706)),
        start = Offset(0f, 56f), end = Offset(0f, 76f)), topLeft = Offset(340f, 56f), size = Size(206f, 20f))
    drawCircle(hs(20f, 60f, 70f, 0.6f), radius = 13f, center = Offset(570f, 66f), style = Stroke(width = 1.2f))
    drawArc(hs(20f, 60f, 70f, 0.6f), 28f, 270f, false, topLeft = Offset(563f, 59f), size = Size(14f, 14f), style = Stroke(width = 1.2f))
    for ((qx, qy) in listOf(540f to 50f, 552f to 82f)) {
        drawOval(hs(20f, 40f, 60f, 0.8f), topLeft = Offset(qx - 5f, qy - 3f), size = Size(10f, 6f))
    }
    drawPath(Path().apply {
        moveTo(190f, 48f); quadraticBezierTo(178f, 66f, 190f, 84f)
        moveTo(236f, 48f); quadraticBezierTo(248f, 66f, 236f, 84f)
    }, hs(30f, 80f, 60f, 0.7f), style = Stroke(width = 1.6f))
    val bx2 = 175f + sin(t * (0.6f + spd * 3f)) * (20f + spd * 35f)
    for (k in 0 until 4) {
        val ys = 58.5f + k * 5f
        val vy = sin(t * (80f + k * 20f)) * (0.5f + pr * 2f) * (if (k == 1) 1f else 0.6f) * (if (act) 1f else 0.2f)
        drawPath(Path().apply {
            moveTo(84f, ys + (k - 1.5f) * 0.6f); lineTo(215f, ys + vy); lineTo(545f, 58.5f + k * 5f + vy * 0.4f)
        }, hs(40f, 60f, 80f, 0.9f), style = Stroke(width = 0.8f + (3 - k) * 0.2f))
    }
    drawPath(Path().apply { moveTo(209f, 52f); lineTo(221f, 52f); lineTo(224f, 80f); lineTo(206f, 80f); close() },
        hs(10f, 40f, 40f))
    translate(bx2, 66f) {
        rotate(6.9f) {
            drawRect(hs(30f, 30f, 92f, 0.95f), topLeft = Offset(-1.5f - pr * 2f, -62f),
                size = Size(3f + pr * 4f, 128f))
        }
    }
    if (sp > 0.05f) {
        val u = (t * 0.3f) % 1f
        drawOval(hs(0f, 80f, 65f), topLeft = Offset(250f - (100f + u * 420f), 66f - (60f + u * 130f)),
            size = Size((100f + u * 420f) * 2, (60f + u * 130f) * 2), style = Stroke(width = 2f),
            alpha = (1f - u) * sp * 0.4f)
    }
    tag(tm, "BOW ${(pr * 100).roundToInt()} · ${(spd * 100).roundToInt()}", bx2, 140f)
}

fun DrawScope.sceneGuitar(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    val bt = (v[0] * 3f).roundToInt(); val pick = v[1]; val str = v[2]; val mute = v[3]; val bend = v[4]
    val pu = v[5]; val fin = (v[6] * 3f).roundToInt(); val tone = v[7]; val res = v[8]; val sp = v[9]
    val elec = bt >= 2
    val bx = if (elec) 676f else 708f
    val pal = listOf(
        listOf("#241810", "#07060a", "#e0a060"), listOf("#2a2418", "#0a0906", "#e8d0a0"),
        listOf("#141a26", "#05070c", "#6ab0ff"), listOf("#241010", "#080304", "#ff6a4a"))[bt]
    drawRect(brush = Brush.linearGradient(listOf(parseHex(pal[0]), parseHex(pal[1])),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    drawCircle(brush = Brush.radialGradient(listOf(parseHex(pal[2]).copy(alpha = 0.16f), Color.Transparent),
        center = Offset(560f, 90f), radius = 420f), radius = 420f, center = Offset(560f, 90f))
    val finCols = if (elec)
        listOf(listOf("#d89a40", "#6a2a10"), listOf("#f0eadc", "#bdb4a0"), listOf("#26262c", "#0c0c10"), listOf("#b02a2a", "#5a1010"))[fin]
    else
        listOf(listOf("#e8c488", "#b88a48"), listOf("#d8943c", "#5a2a10"), listOf("#3a2e26", "#14100c"), listOf("#c88838", "#7a4a18"))[fin]
    val bodyPath = Path().apply {
        if (!elec) addOval(Rect(716f - 178f, 92f - 128f, 716f + 178f, 92f + 128f))
        else if (bt == 2) {
            addOval(Rect(716f - 168f, 92f - 112f, 716f + 168f, 92f + 112f))
            addOval(Rect(616f - 38f, -8f - 38f, 616f + 38f, -8f + 38f))
            addOval(Rect(616f - 38f, 190f - 38f, 616f + 38f, 190f + 38f))
        } else {
            addOval(Rect(716f - 170f, 96f - 112f, 716f + 170f, 96f + 112f))
            addOval(Rect(620f - 44f, 196f - 44f, 620f + 44f, 196f + 44f))
        }
    }
    clipPath(bodyPath) {
        drawPath(bodyPath, brush = Brush.radialGradient(listOf(parseHex(finCols[0]), parseHex(finCols[1])),
            center = Offset(700f, 70f), radius = 200f))
        drawPath(bodyPath, if (elec) Color(0x88000000) else Color(0xFFF2E6C8), style = Stroke(width = 3f))
        for (k in 0 until 30) {
            val p = Path()
            var u = 520f
            while (u <= 800f) {
                val yy = -30f + k * 8f + sin(u * 0.03f + k) * 3f
                if (u == 520f) p.moveTo(u, yy) else p.lineTo(u, yy)
                u += 14f
            }
            drawPath(p, Color(0x1A000000), style = Stroke(width = 1f))
        }
        if (!elec) {
            val r0 = if (bt == 1) 40f else 36f
            drawCircle(brush = Brush.radialGradient(listOf(Color.Black, Color(0xFF1A0E06)),
                center = Offset(650f, 92f), radius = r0), radius = r0, center = Offset(650f, 92f))
            for (k in 0..2) drawCircle(if (k == 1) Color(0xFF1A1208) else Color(0xFFF2E6C8), radius = r0 + 3f + k * 3f,
                center = Offset(650f, 92f), style = Stroke(width = if (k == 1) 2f else 1.2f))
            if (res > 0.05f) drawCircle(brush = Brush.radialGradient(
                listOf(Color(0xFFFFB45A).copy(alpha = 0.35f * res), Color.Transparent),
                center = Offset(650f, 92f), radius = r0 + 60f), radius = r0 + 60f, center = Offset(650f, 92f))
        }
    }
    // neck: fretboard, binding, fret wires, inlays
    val fb0 = 64f; val fb1 = 118f; val fe = 580f
    drawRect(brush = Brush.linearGradient(listOf(hsl(18f, 0.42f, 0.15f + tone * 0.09f), hsl(14f, 0.46f, 0.09f + tone * 0.07f)),
        start = Offset(0f, fb0), end = Offset(0f, fb1)), topLeft = Offset(104f, fb0), size = Size(fe - 104f, fb1 - fb0))
    for (k in 0 until 26) {
        val yy = fb0 + 1f + k * 2.1f
        val p = Path()
        var u = 104f
        while (u <= fe) {
            val y2 = yy + sin(u * 0.021f + k * 1.3f) * 0.9f
            if (u == 104f) p.moveTo(u, y2) else p.lineTo(u, y2)
            u += 18f
        }
        drawPath(p, if (k % 2 == 1) Color.Black else Color(0xFFB86A40), style = Stroke(width = 0.7f),
            alpha = 0.1f + 0.06f * (k % 3))
    }
    for (k in 1..15) {
        val fx = FW[k]
        if (fx > fe - 4f) break
        drawLine(Color(0x88000000), Offset(fx + 1.4f, fb0 + 1f), Offset(fx + 1.4f, fb1 - 1f), 3f)
        drawLine(Color(0xFFD9D6CC), Offset(fx, fb0 + 1f), Offset(fx, fb1 - 1f), 2f)
        drawLine(Color(0xB3FFFFFF), Offset(fx - 0.5f, fb0 + 1f), Offset(fx - 0.5f, fb1 - 1f), 0.8f)
    }
    for (k in listOf(3, 5, 7, 9, 15)) {
        val cx = (FW[k - 1] + FW[k]) / 2f
        drawCircle(brush = Brush.radialGradient(listOf(Color.White, Color(0xFFB8B0A0)),
            center = Offset(cx - 1f, 89f), radius = 4f), radius = 3.6f, center = Offset(cx, 91f))
    }
    drawRect(Color(0xFFEFE6D0), topLeft = Offset(102f, fb0 - 2f), size = Size(7f, fb1 - fb0 + 4f))
    // headstock
    drawPath(Path().apply {
        if (elec) {
            moveTo(6f, 56f); lineTo(104f, 63f); lineTo(104f, 119f); lineTo(6f, 126f)
            quadraticBezierTo(0f, 91f, 6f, 56f)
        } else {
            moveTo(10f, 46f); lineTo(104f, 60f); lineTo(104f, 122f); lineTo(10f, 136f)
            quadraticBezierTo(0f, 91f, 10f, 46f)
        }
    }, brush = Brush.linearGradient(listOf(Color(0xFF2A1C14), Color(0xFF120C08)),
        start = Offset(0f, 40f), end = Offset(0f, 140f)))
    val pegs = listOf(26f to 48f, 58f to 52f, 90f to 57f, 26f to 134f, 58f to 130f, 90f to 125f)
    // strings with chord, pluck energy
    val ci = (floor(t / 1.7f).toInt()) % 4
    val sh = GCH[ci]
    val ph2 = t % 1.7f
    val dt = max(0f, min(0.1f, t - st.gtrT)); st.gtrT = t
    for (s2 in 0 until 6) {
        val delay = s2 * (0.02f + str * 0.09f)
        val played = sh[s2] >= 0
        if (played && ph2 > delay && ph2 - dt <= delay) st.gtrE[s2] = 1f
        st.gtrE[s2] *= exp(-dt * (1.4f + mute * 8f))
        val ys = 69.5f + s2 * 8f
        val amp = (2f + pick * 5f) * st.gtrE[s2] * (1f - 0.6f * mute)
        val nylon = bt == 1
        val wound = s2 < 3
        val p = Path()
        for (i in 0..30) {
            val u = i / 30f
            val xx = 110f + u * (bx - 110f)
            var yy = ys + sin(Math.PI.toFloat() * u) * amp * sin(t * (60f + s2 * 14f))
            if (s2 == 3) yy -= sin(Math.PI.toFloat() * u) * bend * 12f
            if (i == 0) p.moveTo(xx, yy) else p.lineTo(xx, yy)
        }
        drawPath(p, if (wound) (if (nylon) Color(0xFFD8C890) else Color(0xFFC9A86A)) else (if (nylon) Color(0xFFEEF0EA) else Color(0xFFD4D8DE)),
            style = Stroke(width = listOf(2.6f, 2.1f, 1.6f, 1.2f, 0.95f, 0.75f)[s2]))
        val f = sh[s2]
        if (f > 0 && FW[f] < fe) {
            val fx = (FW[f - 1] + FW[f]) / 2f
            drawCircle(brush = Brush.radialGradient(
                listOf(Color.White, parseHex(pal[2]), parseHex(pal[2]).copy(alpha = 0f)),
                center = Offset(fx, ys), radius = 8f), radius = 8f, center = Offset(fx, ys))
        } else if (f == 0) {
            drawCircle(parseHex(pal[2]), radius = 3f, center = Offset(97f, ys), style = Stroke(width = 1.2f))
        } else {
            drawLine(Color(0x55FFFFFF), Offset(94f, ys - 3f), Offset(100f, ys + 3f), 1.2f)
            drawLine(Color(0x55FFFFFF), Offset(100f, ys - 3f), Offset(94f, ys + 3f), 1.2f)
        }
    }
    if (ph2 < 0.25f) for (k in 0 until 7) {
        drawCircle(parseHex(pal[2]), radius = 1.5f,
            center = Offset(bx - 70f + cos(k * 1.7f) * ph2 * 60f, 66f + k * 8f + sin(k * 2.3f) * ph2 * 26f),
            alpha = (1f - ph2 * 4f) * 0.9f)
    }
    if (mute > 0.05f) drawPath(rrect(bx - 34f, 60f, 34f, 62f, 6f),
        color = Color(0xB30A0808).copy(alpha = 0.7f * min(1f, mute * 2f)))
    if (sp > 0.05f) {
        val u = (t * 0.3f) % 1f
        drawCircle(parseHex(pal[2]), radius = 60f + u * 330f, center = Offset(700f, 92f),
            style = Stroke(width = 2f), alpha = (1f - u) * sp * 0.4f)
    }
    val nmG = listOf("PICK", "STRUM", "MUTE", "BEND", if (elec) "PICKUP" else "MIC", "FINISH")
    pegs.forEachIndexed { i, q ->
        val value = v[i + 1]
        val an = -2.2f + value * 4.4f
        drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFF4EFE6), Color(0xFF7A7064)),
            center = Offset(q.first - 3f, q.second - 3f), radius = 12f), radius = 10f, center = Offset(q.first, q.second))
        drawLine(Color(0xFF1A120A), Offset(q.first + 2f * cos(an), q.second + 2f * sin(an)),
            Offset(q.first + 8f * cos(an), q.second + 8f * sin(an)), 2.4f)
        tag(tm, nmG[i], q.first, q.second + (if (q.second < 91f) -15f else 24f))
        tag(tm, "${(value * 100).roundToInt()}%", q.first, q.second + (if (q.second < 91f) -6f else 33f))
    }
}

fun DrawScope.sceneAmp(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    val gain = v[0]; val ch = (v[6] * 2f).roundToInt(); val boost = v[9] > 0.5f
    val lvl = v[11]; val mpos = v[7]; val mdist = v[8]
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFF1A1410), Color(0xFF07050A)),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    val heat = min(1f, gain * (0.55f + 0.3f * ch) + (if (boost) 0.2f else 0f))
    drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFFF8C32).copy(alpha = 0.1f + 0.3f * heat), Color.Transparent),
        center = Offset(400f, 60f), radius = 420f), radius = 420f, center = Offset(400f, 60f))
    drawPath(rrect(24f, 36f, 764f, 92f, 12f), brush = Brush.linearGradient(
        listOf(Color(0xFF2A2622), Color(0xFF16130F), Color(0xFF0C0A08)), start = Offset(0f, 4f), end = Offset(0f, 126f)))
    drawPath(rrect(24f, 36f, 764f, 92f, 12f), Color(0xFFC9A45C), style = Stroke(width = 1.5f))
    listOf(560f, 612f, 664f).forEachIndexed { k, tx ->
        val f = 0.35f + 0.65f * heat * (0.85f + 0.15f * sin(t * 9f + k * 2f))
        drawCircle(brush = Brush.radialGradient(
            listOf(Color(1f, 0.71f - 0.24f * heat, 0.27f, f), Color.Transparent),
            center = Offset(tx, 86f), radius = 26f), radius = 26f, center = Offset(tx, 86f))
        drawPath(rrect(tx - 11f, 52f, 22f, 56f, 11f), color = Color(0xFF0A0806))
        drawPath(rrect(tx - 11f, 52f, 22f, 56f, 11f), Color(0x30FFFFFF), style = Stroke(width = 1f))
        drawPath(rrect(tx - 2.5f, 64f, 5f, 36f, 2.5f), color = Color(1f, 0.78f, 0.47f, 0.4f + 0.6f * f))
    }
    val cx = 410f; val cy = 212f
    drawPath(rrect(170f, 138f, 480f, 150f, 10f), brush = Brush.linearGradient(
        listOf(Color(0xFF2A1D12), Color(0xFF120B06)), start = Offset(0f, 138f), end = Offset(0f, 284f)))
    drawPath(rrect(170f, 138f, 480f, 150f, 10f), Color(0x88C9A45C), style = Stroke(width = 2f))
    clipPath(rrect(182f, 148f, 456f, 130f, 8f)) {
        drawRect(Color(0xFF0C0907), topLeft = Offset(170f, 138f), size = Size(480f, 150f))
        for (k in 0 until 70) drawLine(Color(0x408A7A5A), Offset(170f + k * 7f, 138f), Offset(170f + k * 7f, 288f), 1f)
        for (k in 0 until 22) drawLine(Color(0x408A7A5A), Offset(170f, 138f + k * 7f), Offset(650f, 138f + k * 7f), 1f)
        val pump = 1f + 0.04f * sin(t * 50f) * (0.3f + lvl * 0.7f) * (0.4f + heat)
        drawCircle(Color(0xFF0A0A0C), radius = 66f, center = Offset(cx, cy))
        drawCircle(Color(0xFF44444A), radius = 60f, center = Offset(cx, cy), style = Stroke(width = 5f))
        drawCircle(brush = Brush.radialGradient(
            listOf(Color(0xFF3A3A40), Color(0xFF1C1C20), Color(0xFF060608)),
            center = Offset(cx, cy), radius = 50f * pump), radius = 50f * pump, center = Offset(cx, cy))
        drawCircle(Color(0xFF16161A), radius = 13f * pump, center = Offset(cx, cy))
        for (k in 0 until 3) {
            val u = (t * 0.8f + k / 3f) % 1f
            drawCircle(Color(0xFFFF9A3C), radius = 18f + u * 34f, center = Offset(cx, cy),
                style = Stroke(width = 1.5f), alpha = (1f - u) * 0.25f * (0.3f + heat))
        }
    }
    val mx = 340f + mpos * 140f; val my = 150f + mdist * 90f
    drawPath(Path().apply { moveTo(mx, my); quadraticBezierTo(mx + 60f, my - 30f, 720f, 170f) },
        Color(0x40FFFFFF), style = Stroke(width = 1.2f))
    mic(mx, my, 10f)
    tag(tm, "MIC ${(mpos * 100).roundToInt()} · ${(mdist * 100).roundToInt()}", mx, my + 24f)
}

fun DrawScope.sceneBass(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    val dr = v[0]; val cp = v[1]; val cab = (v[8] * 2f).roundToInt(); val lvl = v[9]
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFF1C1A14), Color(0xFF07060A)),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    val sig = (0.45f + 0.25f * sin(t * 3.1f) * sin(t * 0.9f + 1f) + 0.15f * sin(t * 7.3f)) * (0.6f + dr * 0.6f) * (0.5f + lvl * 0.7f)
    drawPath(rrect(14f, 4f, 776f, 120f, 12f), brush = Brush.linearGradient(
        listOf(Color(0xFF2C2820), Color(0xFF0E0C09)), start = Offset(0f, 4f), end = Offset(0f, 124f)))
    drawPath(rrect(14f, 4f, 776f, 120f, 12f), Color(0x88F0C060), style = Stroke(width = 1.5f))
    val vx = 122f; val vy = 76f
    drawPath(rrect(34f, 14f, 176f, 100f, 10f), brush = Brush.linearGradient(
        listOf(Color(0xFFEFE3C0), Color(0xFFC9B88A)), start = Offset(0f, 14f), end = Offset(0f, 114f)))
    drawPath(rrect(34f, 14f, 176f, 100f, 10f), Color(0xFF3A2A10), style = Stroke(width = 2f))
    for (k in 0..10) {
        val an = Math.PI.toFloat() * (1.2f + 0.6f * k / 10f)
        val r1 = 62f; val r2 = if (k % 5 != 0) 70f else 76f
        drawLine(if (k > 7) Color(0xFFC0392B) else Color(0xFF2A1A08),
            Offset(vx + r1 * cos(an), vy + 34f + r1 * sin(an)),
            Offset(vx + r2 * cos(an), vy + 34f + r2 * sin(an)), if (k % 5 != 0) 1f else 2f)
    }
    val na = Math.PI.toFloat() * (1.2f + 0.6f * min(1f, max(0f, sig)))
    drawLine(Color(0xFF1A0E04), Offset(vx, vy + 34f), Offset(vx + 66f * cos(na), vy + 34f + 66f * sin(na)), 2.2f)
    for (k in 0 until 8) {
        val on = k < (cp * sig * 9f).roundToInt()
        val col = if (k > 5) Color(0xFFFF5040) else if (k > 3) Color(0xFFFFB040) else Color(0xFF60E070)
        drawCircle(if (on) col else Color(0xFF2A2A2A), radius = 4.5f, center = Offset(244f, 24f + k * 13f))
        if (on) drawCircle(col, radius = 9f, center = Offset(244f, 24f + k * 13f), alpha = 0.4f)
    }
    for (k in 0 until 5) {
        val sx = 575f + k * 42f; val value = v[3 + k]; val ty = 104f - value * 80f
        drawPath(rrect(sx - 3f, 22f, 6f, 84f, 3f), color = Color(0xAA000000))
        drawRect(brush = Brush.linearGradient(listOf(Color(0xFFFFD070), Color(0xFF7A5A20)),
            start = Offset(0f, 22f), end = Offset(0f, 106f)), topLeft = Offset(sx - 1.5f, ty), size = Size(3f, 106f - ty))
        drawPath(rrect(sx - 9f, ty - 6f, 18f, 12f, 3f), brush = Brush.linearGradient(
            listOf(Color(0xFF6A6258), Color(0xFFF0E8DA), Color(0xFF5A5248)), start = Offset(sx - 9f, 0f), end = Offset(sx + 9f, 0f)))
        val lt = tm.measure(listOf("63", "250", "1k", "3k", "8k")[k],
            style = TextStyle(color = Color(0xFFC9B88F), fontSize = 8.sp, fontFamily = FxFonts.mono))
        drawText(lt, topLeft = Offset(sx - lt.size.width / 2f, 119f - lt.size.height / 2f))
    }
    val cols: List<Triple<Float, Float, Float>> = when (cab) {
        0 -> listOf(Triple(410f, 212f, 62f))
        1 -> listOf(Triple(366f, 176f, 30f), Triple(454f, 176f, 30f), Triple(366f, 246f, 30f), Triple(454f, 246f, 30f))
        else -> (0..3).map { Triple(338f + it * 48f, 176f, 19f) } + (0..3).map { Triple(338f + it * 48f, 246f, 19f) }
    }
    val bw = if (cab == 2) 228f else if (cab == 1) 188f else 172f
    val bx = if (cab == 2) 296f else if (cab == 1) 316f else 324f
    drawPath(rrect(bx, 136f, bw, 148f, 10f), brush = Brush.linearGradient(
        listOf(Color(0xFF1E1A14), Color(0xFF0A0806)), start = Offset(0f, 136f), end = Offset(0f, 286f)))
    drawPath(rrect(bx, 136f, bw, 148f, 10f), Color(0x66F0C060), style = Stroke(width = 2f))
    cols.forEachIndexed { k, (ccx, ccy, r) ->
        val pump = 1f + 0.07f * sin(t * 14f + k * 0.3f) * (0.3f + sig * 0.9f)
        drawCircle(brush = Brush.radialGradient(
            listOf(Color(0xFF3A3A3E), Color(0xFF1A1A1E), Color(0xFF050506)),
            center = Offset(ccx, ccy), radius = r * pump), radius = r * pump, center = Offset(ccx, ccy))
        drawCircle(Color(0xFF666666), radius = r + 2f, center = Offset(ccx, ccy), style = Stroke(width = 1.6f))
        drawCircle(Color(0xFF111111), radius = r * 0.24f * pump, center = Offset(ccx, ccy))
    }
    val vt = tm.measure("VU", style = TextStyle(color = Color(0xFF3A2A10), fontSize = 8.sp,
        fontFamily = FxFonts.mono, fontWeight = FontWeight.Bold))
    drawText(vt, topLeft = Offset(vx - vt.size.width / 2f, 32f - vt.size.height / 2f))
}

fun DrawScope.sceneBrass(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    val lips = v[0]; val bite = v[1]; val vib = v[2]; val sec = (v[3] * 4f).roundToInt() + 1
    val mute = (v[4] * 4f).roundToInt(); val tone = v[5]; val sp = v[7]
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFF2E0C18), Color(0xFF08040A)),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFFFD278).copy(alpha = 0.12f + 0.25f * bite), Color.Transparent),
        center = Offset(700f, 80f), radius = 400f), radius = 400f, center = Offset(700f, 80f))
    val n = BXP.size
    fun tube(dx: Float, dy: Float, sc: Float, al: Float) {
        translate(dx, dy) {
            scale(sc, sc, pivot = Offset.Zero) {
                for (pass in 0 until 3) for (i in 1 until n) {
                    val u = i / n.toFloat()
                    val w = (8f + (if (u > 0.8f) ((u - 0.8f) / 0.2f).pow(2) * 44f else 0f)) *
                            (if (pass == 0) 1.4f else if (pass == 1) 1f else 0.25f)
                    val aa = BXP[i - 1]; val bb = BXP[i]
                    val off = if (pass == 2) -2f else 0f
                    val col = when (pass) {
                        0 -> Color(0xFF2A1C08)
                        1 -> hsl(42f + tone * 8f, 0.7f, 0.3f + tone * 0.22f)
                        else -> Color(0xFFFFF4D2).copy(alpha = 0.35f + 0.35f * tone)
                    }
                    drawLine(col, Offset(aa[0] + off, aa[1] + off), Offset(bb[0] + off, bb[1] + off), w,
                        cap = StrokeCap.Round, alpha = al)
                }
            }
        }
    }
    for (s2 in sec - 1 downTo 1) tube(-20f * s2, -14f * s2 + 20f, 1f - 0.06f * s2, 0.18f)
    tube(0f, 20f, 1f, 1f)
    val dt = max(0f, min(0.1f, t - st.bt)); st.bt = t
    if (st.bsP.isEmpty()) repeat(70) { st.bsP.add(floatArrayOf(Random.nextFloat(), 0.5f + Random.nextFloat() * 0.9f)) }
    for (p in st.bsP) {
        p[0] += dt * (0.12f + 0.6f * lips) * p[1]
        if (p[0] > 1f) p[0] -= 1f
        val i = min(n - 1, floor(p[0] * n).toInt())
        val q = BXP[i]
        drawCircle(Color(0xFFFFF3D0), radius = 1f + bite * 2f,
            center = Offset(q[0], q[1] + 20f + sin(p[0] * 30f - t * (4f + vib * 14f)) * (2f + vib * 8f)),
            alpha = sin(p[0] * Math.PI.toFloat()) * 0.6f)
    }
    listOf(230f, 266f, 302f).forEachIndexed { k, vx ->
        val pr = if (max(0f, sin(t * (2f + k * 0.7f) + k * 2f)) > 0.85f) 8f else 0f
        drawPath(rrect(vx - 8f, 74f + pr, 16f, 54f, 5f), color = Color(0xFF9A7A2A))
        drawPath(rrect(vx - 6f, 76f + pr, 12f, 48f, 4f), color = Color(0xFFE8C060))
        drawCircle(Color(0xFFF4EFE6), radius = 12f, center = Offset(vx, 62f + pr))
        drawArc(Color(0xFFE8C060), -135f, 270f * v[k], false,
            topLeft = Offset(vx - 15f, 62f + pr - 15f), size = Size(30f, 30f), style = Stroke(width = 2.2f))
        tag(tm, listOf("LIPS", "BITE", "VIB")[k], vx, 42f)
    }
    if (mute != st.ampMute) { st.ampMute = mute; st.ampMuteT = t }
    val slide = max(0f, 1f - (t - st.ampMuteT) * 3f)
    translate(706f + slide * 70f, 100f) {
        when (mute) {
            1 -> {
                val p = Path().apply { moveTo(-20f, -18f); lineTo(26f, -6f); lineTo(26f, 6f); lineTo(-20f, 18f); close() }
                drawPath(p, Color(0xFF8A8A92)); drawPath(p, Color(0xFFD0D0D8), style = Stroke(width = 2f))
            }
            2 -> {
                val p = Path().apply {
                    moveTo(-14f, -30f); quadraticBezierTo(26f, -32f, 28f, 0f)
                    quadraticBezierTo(26f, 32f, -14f, 30f); close()
                }
                drawPath(p, Color(0xFF8A8A92)); drawPath(p, Color(0xFFD0D0D8), style = Stroke(width = 2f))
            }
            3 -> {
                drawOval(Color(0xFF8A8A92), topLeft = Offset(4f - 22f, -30f), size = Size(44f, 60f))
                drawOval(Color(0xFFD0D0D8), topLeft = Offset(4f - 22f, -30f), size = Size(44f, 60f), style = Stroke(width = 2f))
                drawCircle(Color(0xFF2A2A30), radius = 8f, center = Offset(8f, 0f))
            }
            4 -> {
                drawCircle(Color(0xFF4A2A22), radius = 28f, center = Offset(10f, 0f))
                drawCircle(Color(0xFFD0D0D8), radius = 28f, center = Offset(10f, 0f), style = Stroke(width = 2f))
                drawCircle(Color(0xFF2A1A14), radius = 8f, center = Offset(10f, 0f))
            }
        }
    }
    if (sp > 0.05f) {
        val u = (t * 0.35f) % 1f
        drawCircle(Color(0xFFE8C060), radius = 40f + u * 160f, center = Offset(700f, 100f),
            style = Stroke(width = 2f), alpha = (1f - u) * sp * 0.5f)
    }
}

fun DrawScope.sceneVoices(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    val vx = v[0]; val vy = v[1]; val br = v[2]; val ch = v[3]; val vib = v[4]; val tone = v[5]; val sp = v[7]
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFF14102A), Color(0xFF05040C)),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    val nc = 3 + (ch * 16f).roundToInt()
    for (k in 0 until nc) {
        val px = x0 + rw * (k + 0.5f) / nc
        val h = (60f + 90f * (0.5f + 0.5f * sin(t * (0.8f + br * 2f) + k * 1.7f)) * (0.4f + 0.6f * (1f - abs(vy - 0.5f))) +
                 vib * 14f * sin(t * (4f + vib * 8f) + k)) * 2.2f
        val hue = 250f + vx * 60f
        drawRect(brush = Brush.linearGradient(
            listOf(hsl(hue, 0.7f, 0.7f, 0f), hsl(hue, 0.7f, 0.75f, 0.1f + 0.12f * br + 0.08f * tone), Color.Transparent),
            start = Offset(0f, y0 + rh), end = Offset(0f, y0 + rh - h)),
            topLeft = Offset(px - rw / nc * 0.35f, y0 + rh - h), size = Size(rw / nc * 0.7f, h))
    }
    if (sp > 0.05f) {
        val u = (t * 0.3f) % 1f
        drawOval(Color(0xFFC9B8FF), topLeft = Offset(400f - (80f + u * 400f), 110f - (50f + u * 120f)),
            size = Size((80f + u * 400f) * 2, (50f + u * 120f) * 2), style = Stroke(width = 2f),
            alpha = (1f - u) * sp * 0.4f)
    }
    val px0 = 150f; val py0 = 10f; val pw = 400f; val ph = 190f
    drawPath(rrect(px0, py0, pw, ph, 16f), brush = Brush.linearGradient(
        listOf(Color(0x18FFFFFF), Color(0x08FFFFFF)), start = Offset(0f, py0), end = Offset(0f, py0 + ph)))
    drawPath(rrect(px0, py0, pw, ph, 16f), Color(0x66C9B8FF), style = Stroke(width = 1.4f))
    for (k in 1 until 4) {
        drawLine(Color(0x14FFFFFF), Offset(px0 + pw * k / 4f, py0), Offset(px0 + pw * k / 4f, py0 + ph), 1f)
        drawLine(Color(0x14FFFFFF), Offset(px0, py0 + ph * k / 4f), Offset(px0 + pw, py0 + ph * k / 4f), 1f)
    }
    for ((l, u, w) in listOf(Triple("I", 0.1f, 0.9f), Triple("U", 0.9f, 0.9f), Triple("E", 0.25f, 0.55f),
        Triple("O", 0.75f, 0.55f), Triple("A", 0.5f, 0.1f))) {
        val lt = tm.measure(l, style = TextStyle(color = Color(0xFFC9B8FF), fontSize = 22.sp,
            fontFamily = FxFonts.spaceGrotesk, fontWeight = FontWeight.Bold))
        drawText(lt, topLeft = Offset(px0 + pw * u - lt.size.width / 2f, py0 + ph * (1f - w) + 8f - lt.size.height / 2f),
            alpha = 0.7f)
    }
    val ux = px0 + pw * vx; val uy = py0 + ph * (1f - vy)
    drawCircle(brush = Brush.radialGradient(listOf(Color(0xAAC9B8FF), Color.Transparent),
        center = Offset(ux, uy), radius = 60f), radius = 60f, center = Offset(ux, uy))
    drawCircle(Color.White, radius = 10f + sin(t * 5f) * vib * 3f, center = Offset(ux, uy))
    drawCircle(Color(0xFFC9B8FF), radius = 16f, center = Offset(ux, uy), style = Stroke(width = 2f))
    val f1 = 0.1f + 0.2f * (1f - vy); val f2 = 0.3f + 0.4f * vx; val f3 = 0.82f
    val fp = Path()
    for (i in 0..80) {
        val u = i / 80f
        var h = 0f
        for ((c, w, am) in listOf(Triple(f1, 0.05f, 1f), Triple(f2, 0.07f, 0.85f), Triple(f3, 0.06f, 0.5f))) {
            h += am * exp(-(((u - c) / w).toDouble().pow(2.0))).toFloat()
        }
        val xx = 590f + u * 190f; val yy = 190f - h * 70f
        if (i == 0) fp.moveTo(xx, yy) else fp.lineTo(xx, yy)
    }
    drawPath(fp, Color(0xFFC9B8FF), style = Stroke(width = 2f))
    val ft = tm.measure("FORMANTS  F1 F2 F3", style = TextStyle(color = Color(0xFFC9B8FF), fontSize = 8.sp, fontFamily = FxFonts.mono))
    drawText(ft, topLeft = Offset(590f, 206f))
}

fun DrawScope.sceneSynth(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFF0C1424), Color(0xFF04060C)),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    for (k in 0 until 40) drawLine(Color(0x127AD0FF), Offset(x0 + k * 22f, y0), Offset(x0 + k * 22f, y0 + rh), 1f)
    for (k in 0 until 16) drawLine(Color(0x127AD0FF), Offset(x0, y0 + k * 22f), Offset(x0 + rw, y0 + k * 22f), 1f)
    fun wave(u: Float, w: Float): Float {
        val ph = ((u % 1f) + 1f) % 1f
        val sn = sin(6.283f * ph)
        val tr = 4f * abs(ph - 0.5f) - 1f
        val sw = 2f * ph - 1f
        val sq = if (ph < 0.5f) 1f else -1f
        return when {
            w < 0.33f -> sn + (tr - sn) * (w / 0.33f)
            w < 0.66f -> tr + (sw - tr) * ((w - 0.33f) / 0.33f)
            else -> sw + (sq - sw) * ((w - 0.66f) / 0.34f)
        }
    }
    fun scope(sx0: Float, sy0: Float, sw: Float, sh: Float, fn: (Float) -> Float, col: Color) {
        drawPath(rrect(sx0, sy0, sw, sh, 8f), color = Color(0x66000000))
        drawPath(rrect(sx0, sy0, sw, sh, 8f), Color(0x18FFFFFF), style = Stroke(width = 1f))
        val p = Path()
        for (i in 0..80) {
            val u = i / 80f
            val yy = sy0 + sh / 2f - fn(u) * sh * 0.38f
            if (i == 0) p.moveTo(sx0, yy) else p.lineTo(sx0 + u * sw, yy)
        }
        drawPath(p, col, style = Stroke(width = 1.8f))
    }
    val fc = 20f * 1000f.pow(v[7])
    val q = 0.6f + v[8] * 10f
    val ft = (v[11] * 3f).roundToInt()
    fun resp(f: Float): Float {
        val r = f / fc
        val d = sqrt((1f - r * r).pow(2) + (r / q).pow(2)) + 1e-6f
        return when (ft) {
            0 -> 1f / d
            1 -> r * r / d
            2 -> (r / q) / d
            else -> abs(1f - r * r) / d
        }
    }
    when (st.presetIndex.coerceIn(0, 2)) {
        0 -> {
            scope(14f, 8f, 110f, 58f, { u -> wave(u * 2f + t * 0.4f, v[0]) * 0.6f + wave(u * 2f + t * 0.4f + v[2] * 0.3f, v[0]) * 0.4f }, Color(0xFF7AD0FF))
            scope(134f, 8f, 110f, 58f, { u -> wave(u * 2f - t * 0.3f, v[1]) }, Color(0xFFFF9AD0))
            drawPath(rrect(270f, 8f, 290f, 104f, 10f), color = Color(0x66000000))
            val p = Path()
            for (i in 0..120) {
                val u = i / 120f
                val f = 20f * 1000f.pow(u)
                val hh = max(-30f, min(20f, 20f * log10(resp(f) + 1e-6f)))
                val yy = 94f - (hh + 30f) / 50f * 80f
                if (i == 0) p.moveTo(270f, yy) else p.lineTo(270f + u * 290f, yy)
            }
            drawPath(p, Color(0xFF7AD0FF), style = Stroke(width = 2.2f))
            val pxx = 270f + v[7] * 290f; val pyy = 8f + (1f - v[8]) * 104f
            drawCircle(Color.White, radius = 7f + sin(t * 4f) * 1.2f, center = Offset(pxx, pyy))
            drawCircle(Color(0xFF7AD0FF), radius = 12f, center = Offset(pxx, pyy), style = Stroke(width = 2f))
            val lt = tm.measure("CUTOFF ${fc.roundToInt()} Hz  ·  RES ${(v[8] * 100).roundToInt()}%",
                style = TextStyle(color = Color(0xFF8FB8D8), fontSize = 8.sp, fontFamily = FxFonts.mono))
            drawText(lt, topLeft = Offset(278f, 22f - lt.size.height / 2f))
            val ax = 282f + v[12] * 70f; val dx = ax + v[13] * 70f
            val sy = 136f + (1f - v[14]) * 64f; val sx = dx + 40f; val rx = sx + v[15] * 70f
            drawPath(rrect(270f, 120f, 290f, 88f, 10f), color = Color(0x66000000))
            val ap = Path().apply {
                moveTo(282f, 200f); lineTo(ax, 136f); lineTo(dx, sy); lineTo(sx, sy); lineTo(rx, 200f)
            }
            drawPath(ap, Color(0xFFFFA0D0), style = Stroke(width = 2.2f))
            listOf(ax to 136f, dx to sy, rx to 200f).forEach { (hx, hy) ->
                drawCircle(Color.White, radius = 6f, center = Offset(hx, hy))
                drawCircle(Color(0xFFFFA0D0), radius = 10f, center = Offset(hx, hy), style = Stroke(width = 2f))
            }
            val ot = tm.measure("OSC A", style = TextStyle(color = Color(0xFF8FB8D8), fontSize = 8.sp,
                fontFamily = FxFonts.mono, fontWeight = FontWeight.Bold))
            drawText(ot, topLeft = Offset(22f, 20f - ot.size.height / 2f))
            val ob = tm.measure("OSC B", style = TextStyle(color = Color(0xFF8FB8D8), fontSize = 8.sp,
                fontFamily = FxFonts.mono, fontWeight = FontWeight.Bold))
            drawText(ob, topLeft = Offset(142f, 20f - ob.size.height / 2f))
        }
        1 -> {
            fun lfoFn(s2: Float): (Float) -> Float = { u ->
                val ph = ((u % 1f) + 1f) % 1f
                when {
                    s2 < 0.33f -> sin(6.283f * u)
                    s2 < 0.66f -> 4f * abs(ph - 0.5f) - 1f
                    s2 < 0.9f -> 2f * ph - 1f
                    else -> if (ph < 0.5f) 1f else -1f
                }
            }
            scope(14f, 8f, 220f, 62f, { u -> lfoFn(v[18])(u * 2f + t * (0.3f + v[16] * 3f)) * (0.2f + v[17] * 0.8f) }, Color(0xFF7AD0FF))
            scope(250f, 8f, 220f, 62f, { u -> sin(6.283f * (u * 2f + t * (0.3f + v[20] * 3f))) * (0.2f + v[21] * 0.8f) }, Color(0xFFFF9AD0))
            val src = listOf("LFO1" to v[17], "LFO2" to v[21], "ENV" to v[10], "MACRO 4" to v[25])
            val dst = listOf("CUTOFF", "PITCH", "DRIVE", "LEVEL")
            val map = intArrayOf((v[19] * 3f).roundToInt(), 1, 0, 3)
            src.forEachIndexed { k, (nm, am) ->
                val y1 = 34f + k * 28f; val y2 = 34f + map[k] * 28f
                drawPath(Path().apply { moveTo(548f, y1); cubicTo(640f, y1, 660f, y2, 752f, y2) },
                    brush = Brush.linearGradient(listOf(Color(0xFF7AD0FF), Color(0xFFFF9AD0)), start = Offset(530f, 0f), end = Offset(770f, 0f)),
                    style = Stroke(width = 1f + am * 5f), alpha = 0.25f + 0.6f * am)
                val lt = tm.measure(nm, style = TextStyle(color = Color(0xFF8FB8D8), fontSize = 8.sp, fontFamily = FxFonts.mono))
                drawText(lt, topLeft = Offset(540f - lt.size.width, y1 + 3f - lt.size.height / 2f))
            }
            dst.forEachIndexed { k, nm ->
                val y2 = 34f + k * 28f
                val lt = tm.measure(nm, style = TextStyle(color = Color(0xFF8FB8D8), fontSize = 8.sp, fontFamily = FxFonts.mono))
                drawText(lt, topLeft = Offset(764f, y2 + 3f - lt.size.height / 2f))
            }
        }
        else -> {
            val act = intArrayOf(0, 2, 4, 7, 9, 4)[(floor(t * 3.2f).toInt()) % 6]
            for (k in 0 until 15) {
                val kx = 20f + k * 50.6f; val on = k == act
                drawPath(rrect(kx, 150f, 48f, 56f, 4f), brush = Brush.linearGradient(
                    listOf(if (on) Color(0xFF7AD0FF) else Color(0xFFF4F6FA), if (on) Color(0xFF4AA0D0) else Color(0xFFC8CCD6)),
                    start = Offset(0f, 150f), end = Offset(0f, 208f)))
            }
            val nv = 1 + (v[28] * 15f).roundToInt()
            for (k in 0 until 16) drawCircle(if (k < nv) Color(0xFF7AD0FF) else Color(0x22FFFFFF), radius = 4.2f,
                center = Offset(560f + k * 13f, 40f))
            val lt = tm.measure("VOICES $nv  ·  ${listOf("POLY", "MONO", "LEGATO")[(v[26] * 2f).roundToInt().coerceIn(0, 2)]}",
                style = TextStyle(color = Color(0xFF8FB8D8), fontSize = 8.sp, fontFamily = FxFonts.mono, fontWeight = FontWeight.Bold))
            drawText(lt, topLeft = Offset(560f, 62f - lt.size.height / 2f))
        }
    }
}
