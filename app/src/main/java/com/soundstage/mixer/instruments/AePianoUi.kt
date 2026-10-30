package com.soundstage.mixer.instruments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.soundstage.mixer.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val BRASS = Color(0xFFE3C48A)

/**
 * Piano scene: the harp seen from the player's side. Strings are drawn straight
 * with the bass crossing over the treble, and the whole thing is tilted so the
 * strings run away from the viewer. That crossover is the piano's silhouette --
 * without it the harp reads as a harp, not a piano.
 */
private fun DrawScope.drawPianoHero(x: Float, y: Float, w: Float, h: Float, phase: Float) {
    val body = Path().apply {
        moveTo(x, y + h)
        lineTo(x + w * 0.02f, y + h * 0.30f)
        lineTo(x + w * 0.30f, y)
        lineTo(x + w, y + h * 0.10f)
        lineTo(x + w * 0.98f, y + h)
        close()
    }
    drawPath(body, Brush.linearGradient(
        listOf(Color(0xFF3A2415), Color(0xFF160C06)), start = Offset(x, y), end = Offset(x + w, y + h)))
    drawPath(body, Color(0x2AE3C48A), style = Stroke(width = 1.5f))

    // The pinblock: a darker band the strings terminate on.
    drawRect(Color(0xFF0B0603), topLeft = Offset(x + w * 0.02f, y + h * 0.06f), size = Size(w * 0.96f, h * 0.16f))
    for (k in 0 until 22) {
        val px = x + w * (0.05f + 0.9f * k / 21f)
        drawLine(Color(0x66E3C48A), Offset(px, y + h * 0.07f), Offset(px, y + h * 0.21f), strokeWidth = 1f)
    }

    // Strings: bass at the left, crossing the treble before the bridge.
    for (k in 0 until 30) {
        val u = k / 29f
        val bx = x + w * (0.03f + 0.94f * u)
        val bx2 = x + w * (0.03f + 0.94f * u)
        val by = y + h * (0.22f + 0.55f * u)
        // the bass strings start low and rise, so they cross the treble line
        val cy = y + h * (0.22f + 0.55f * (1f - u))
        val wob = sin(phase * (6f + u * 20f) + u * 30f) * (1.6f * (1f - abs(u - 0.5f)))
        val p = Path().apply {
            moveTo(bx, by)
            quadraticBezierTo(bx2 + wob * 3f, (by + cy) * 0.5f, x + w * (0.90f - 0.06f * u), cy)
        }
        val shade = Color(0xFFB9C4D0).copy(alpha = 0.20f + 0.35f * (1f - u))
        drawPath(p, shade, style = Stroke(width = 0.8f + (1f - u) * 1.1f))
    }

    // Hammers sitting under the strings, one row.
    for (k in 0 until 12) {
        val u = k / 11f
        val hx = x + w * (0.10f + 0.80f * u)
        val hy = y + h * (0.80f + 0.10f * u)
        drawLine(Color(0xFF2A1A0E), Offset(hx, hy), Offset(hx, hy - h * 0.10f), strokeWidth = 3f)
        drawCircle(Color(0xFF6E5A3E), radius = 3f, center = Offset(hx, hy - h * 0.11f))
    }

    // Soundboard glow, brightest where the treble strings are (short strings, more energy).
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0x33FFE2B0), Color.Transparent),
            center = Offset(x + w * 0.72f, y + h * 0.45f), radius = h * 0.7f),
        radius = h * 0.7f, center = Offset(x + w * 0.72f, y + h * 0.45f))
}

/**
 * Hammer-head bar: a felt strip whose deformation follows the note envelope.
 * Each key lights the felt above it, so a chord is visible as a moving ridge.
 */
private fun DrawScope.drawHammerBar(a: AeAnim, x: Float, y: Float, w: Float, h: Float, n: Int) {
    val n0 = max(8, n)
    for (k in 0 until n0) {
        val u = k / (n0 - 1f)
        val e = a.pianoE[k % a.pianoE.size]
        val px = x + w * u
        val lift = e * h * 0.55f
        drawLine(
            Color(0xFFE8DCC4).copy(alpha = 0.10f + 0.55f * e),
            Offset(px, y + h), Offset(px, y + h - lift), strokeWidth = w / n0 * 0.8f)
    }
}

/**
 * String decay display: the aftersound model made visible. Two slopes are drawn
 * because the engine models two: the fast initial drop (tau1, measured) and the
 * slow aftersound (tau2, modelled). A single line would misrepresent the model.
 */
private fun DrawScope.drawDecayCurves(x: Float, y: Float, w: Float, h: Float, body: Float, rel: Float, phase: Float) {
    drawRect(Color(0x33000000), topLeft = Offset(x, y), size = Size(w, h))
    val tau1 = 0.45f + (1f - body) * 0.35f
    for (k in 0 until 2) {
        val p = Path()
        for (i in 0..64) {
            val t = i / 64f
            val px = x + w * t
            val amp = if (k == 0) exp(-t * 3.2f / tau1 * 0.5f)
            else exp(-t * 0.55f) * (0.35f + 0.65f * rel)
            val py = y + h - h * amp * (0.9f + 0.1f * sin(phase * 2f + k))
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        drawPath(p, if (k == 0) Color(0xCCE3C48A) else Color(0x88B9C4D0),
            style = Stroke(width = if (k == 0) 1.6f else 1.1f))
    }
}

/** The 12-parameter face. Same grid as the sax so the two windows feel like one family. */
@Composable
fun BoxScope.AePianoUi(c: AeCtl, a: AeAnim) {
    val border = Color(0x2AE3C48A)
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(
        listOf(Color(0xFF2A1C10), Color(0xFF120B06), Color(0xFF050302)), radius = 820f)))
    // The preset's own photograph, not a single shared drawing: each model has its own
    // instrument, and that difference is most of what tells them apart at a glance.
    // WebP rather than the AVIF the previews use — AVIF needs API 31 and the app ships
    // minSdk 26, where it would decode to nothing.
    Image(
        painter = painterResource(pianoPlate(c)),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.at(22, 54, 300, 286),
    )
    // Fallback line-art, kept underneath: it fills the frame if a plate is ever missing.
    Canvas(Modifier.fillMaxSize()) { a.frame.intValue; drawPianoHero(340f, 44f, 320f, 300f, a.t) }

    AeCardBox(22, 54, 300, 286, "", BRASS, border)
    AeT("PIANO", 9, BRASS, Modifier.at(38, 62, 200, 12), AeFonts.plate, FontWeight.ExtraBold, 0.2f)
    AeT("sample + boost", 9, Color(0x99E3C48A), Modifier.at(38, 74, 220, 12), AeFonts.plate)

    // Decay model, then the hammer bar: the two things that change with every preset.
    Canvas(Modifier.at(38, 96, 268, 46)) { a.frame.intValue; drawDecayCurves(0f, 0f, size.width, size.height, c.v(2), c.v(4), a.t) }
    Canvas(Modifier.at(38, 150, 268, 44)) { a.frame.intValue; drawHammerBar(a, 0f, 0f, size.width, size.height, 34) }
    AeT("DECAY", 9, BRASS, Modifier.at(38, 90, 100, 12), AeFonts.plate, FontWeight.ExtraBold, 0.2f)
    AeT("HAMMER", 9, BRASS, Modifier.at(38, 144, 100, 12), AeFonts.plate, FontWeight.ExtraBold, 0.2f)

    AeGlassKnob(c, 0, "Hammer", 66, 218, 40)
    AeGlassKnob(c, 1, "Tone", 150, 218, 40)
    AeGlassKnob(c, 2, "Body", 234, 218, 40)
    AeGlassKnob(c, 3, "Sustain", 66, 292, 40)
    AeGlassKnob(c, 4, "Release", 150, 292, 40)
    AeGlassKnob(c, 5, "Lid", 234, 292, 40)

    AeCardBox(678, 54, 300, 286, "TONE · SPACE · VOICING", BRASS, border, AeFonts.plate)
    AeGlassKnob(c, 6, "Space", 704, 86, 38)
    AeGlassKnob(c, 7, "Width", 786, 86, 38)
    AeGlassKnob(c, 8, "Tune", 868, 86, 38)
    AeGlassKnob(c, 9, "Vel", 704, 164, 38)
    AeGlassKnob(c, 10, "Symp", 786, 164, 38)
    AeGlassKnob(c, 11, "Level", 868, 164, 38)
    Canvas(Modifier.at(704, 226, 262, 16)) { a.frame.intValue; drawHammerBar(a, 0f, 0f, size.width, size.height, 24) }
    AeT("SYMPATHETIC", 9, BRASS, Modifier.at(704, 246, 160, 12), AeFonts.plate, FontWeight.ExtraBold, 0.2f)
    AeT("boite · cordes · etouffoirs", 9, Color(0x99E3C48A), Modifier.at(704, 258, 220, 12), AeFonts.plate)

    AeKeyboard(c, 52, 360, 896, 90)
}

/** Photograph of the model behind the session's current preset. */
private fun pianoPlate(c: AeCtl): Int {
    val list = c.m.presets(AeInstrument.PIANO)
    val name = list.getOrNull(c.s.preset)?.name ?: "Studio"
    return when (name) {
        "Felt" -> R.drawable.piano_felt
        "Studio" -> R.drawable.piano_studio
        "Bright" -> R.drawable.piano_bright
        "Mellow" -> R.drawable.piano_mellow
        "Dark" -> R.drawable.piano_dark
        "Upright" -> R.drawable.piano_upright
        "Lady D" -> R.drawable.piano_lady
        "Honky" -> R.drawable.piano_honky
        "Electric" -> R.drawable.piano_electric
        else -> R.drawable.piano_studio
    }
}

/**
 * Tile thumbnail: the harp seen from the side, strings converging on the pinblock.
 * Drawn rather than loaded from an asset: the AVIF previews need API 31 to decode
 * and the app ships minSdk 26, so a bitmap here would be blank on a third of devices.
 */
fun DrawScope.drawPianoMini() {
    val w = size.width; val h = size.height
    val body = Path().apply {
        moveTo(w * 0.06f, h * 0.92f)
        lineTo(w * 0.10f, h * 0.16f)
        lineTo(w * 0.88f, h * 0.04f)
        lineTo(w * 0.94f, h * 0.92f)
        close()
    }
    drawPath(body, Brush.linearGradient(listOf(Color(0xFF4A3018), Color(0xFF160C06))))
    drawPath(body, Color(0x55E3C48A), style = Stroke(width = 1.2f))
    for (k in 0 until 14) {
        val u = k / 13f
        drawLine(
            Color(0xFFC8D2DE).copy(alpha = 0.25f + 0.4f * (1f - u)),
            Offset(w * (0.14f + 0.66f * u), h * (0.22f + 0.10f * u)),
            Offset(w * (0.14f + 0.66f * u), h * (0.80f + 0.06f * u)),
            strokeWidth = 0.7f + (1f - u) * 0.9f)
    }
    drawCircle(Brush.radialGradient(listOf(Color(0x44FFE2B0), Color.Transparent),
        center = Offset(w * 0.62f, h * 0.42f), radius = h * 0.8f),
        radius = h * 0.8f, center = Offset(w * 0.62f, h * 0.42f))
}
