package com.soundstage.mixer.instruments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.max
import kotlin.math.roundToInt

private val CYAN = Color(0xFF6FE9FF)
private val NN = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
private fun noteName(n: Int) = "${NN[n % 12]}${n / 12 - 1}"

/** Card used by Leads and Sax (title in the instrument's accent). */
@Composable
internal fun AeCardBox(x: Number, y: Number, w: Number, h: Number, title: String, titleColor: Color, border: Color, font: FontFamily = AeFonts.ui) {
    val sh = RoundedCornerShape(18.su())
    Box(Modifier.at(x, y, w, h).clip(sh).background(Brush.linearGradient(listOf(Color(0x10FFFFFF), Color(0x03FFFFFF)))).border(1.su(), border, sh))
    if (title.isNotEmpty()) AeT(title, 9, titleColor, Modifier.at(x.toFloat() + 16f, y.toFloat() + 10f, 420, 12), font, FontWeight.ExtraBold, 0.2f)
}

/** LED fader: SVG track + cap, live value bar, LED line on the cap. Drag anywhere on the track. */
@Composable
private fun AeFader(c: AeCtl, idx: Int, x: Number, y: Number) {
    val sc = LocalAeScale.current
    val v = c.v(idx)
    fun fromY(py: Float) = (1f - ((py / sc) - 16f) / 118f).coerceIn(0f, 1f)
    Box(Modifier.at(x, y, 44, 160).pointerInput(idx) {
        awaitEachGesture {
            val d = awaitFirstDown(requireUnconsumed = false)
            c.set(idx, fromY(d.position.y)); d.consume()
            drag(d.id) { c.set(idx, fromY(it.position.y)); it.consume() }
        }
    }) {
        Box(Modifier.fillMaxSize().aeSvg(AeSvgs.FADER_TRACK))
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / 44f; val yy = 4f + (1f - c.v(idx)) * 118f
            drawRoundRect(Color(0x8C19E3FF), Offset(19.5f * k, (yy + 13f) * k), Size(5f * k, max(0f, 140f - yy - 13f) * k), CornerRadius(2.5f * k))
        }
        val capY = 4f + (1f - v) * 118f
        Box(Modifier.at(3, capY, 38, 26).aeSvg(AeSvgs.FADER_CAP))
        Canvas(Modifier.at(8, capY + 12f, 28, 3)) { drawRoundRect(Color(0xFF19E3FF).copy(alpha = 0.35f + 0.65f * v), Offset.Zero, size, CornerRadius(size.height / 2f)) }
        AeT("${(v * 100).roundToInt()}", 10, Color(0xFF9FDCEA), Modifier.at(0, 145, 44, 13), AeFonts.mono, FontWeight.Normal, align = TextAlign.Center)
    }
}

@Composable
private fun LeadsTabs(c: AeCtl) {
    val shape = RoundedCornerShape(50)
    Box(Modifier.at(354, 7, 280, 32).clip(shape).background(Color(0x10FFFFFF)).border(1.su(), Color(0x18FFFFFF), shape))
    arrayOf("SOUND", "SHAPE", "FX", "PLAY").forEachIndexed { i, t ->
        val on = c.s.page == i
        Box(Modifier.at(358 + i * 68, 10, 66, 26).clip(shape)
            .then(if (on) Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF19E3FF), Color(0xFF3A7BFF)))) else Modifier)
            .clickable(remember { MutableInteractionSource() }, null) { c.s.page = i }, contentAlignment = Alignment.Center) {
            AeT(t, 10, if (on) Color(0xFF02101A) else AeC.tx, spacing = 0.14f)
        }
    }
}

/** Mono lead: five layers with LED faders, oscilloscope, AHDSR + filter, FX, performance. Layout of the validated preview. */
@Composable
fun BoxScope.AeLeadsUi(c: AeCtl, a: AeAnim, samples: FloatArray? = null) {
    val y: (Int) -> Float = { c.v(it) }
    val border = Color(0x2619E3FF)
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF0E1F33), Color(0xFF070D18), Color(0xFF03060B)), radius = 900f)))
    Canvas(Modifier.at(0, 46, 1000, 4)) { a.frame.intValue; drawLedStrip(a) }
    LeadsTabs(c)
    when (c.s.page) {
        0 -> {
            AeCardBox(22, 54, 566, 286, "LAYERS · LEVEL & OCTAVE", CYAN, border)
            val names = arrayOf("SINE", "SQUARE", "SAW", "SUPER", "SOFT")
            for (i in 0 until 5) {
                val x = 36 + i * 108
                AeT(names[i], 9, CYAN, Modifier.at(x, 82, 100, 12), weight = FontWeight.ExtraBold, spacing = 0.2f, align = TextAlign.Center)
                Canvas(Modifier.at(x + 30, 96, 40, 20)) { drawWaveIcon(i) }
                AeFader(c, i, x + 6, 118)
                arrayOf("-1", "0", "+1").forEachIndexed { j, t -> AeChip(c, 5 + i, j, 3, t, x + 5 + j * 33, 284, 30) }
            }
            Canvas(Modifier.at(22, 118, 566, 160)) { a.frame.intValue; drawMeters(a) }
            AeCardBox(604, 54, 374, 150, "OSCILLOSCOPE", CYAN, border)
            Canvas(Modifier.at(620, 82, 342, 107).clip(RoundedCornerShape(6.su()))) { a.frame.intValue; drawLeadScope(a, y, c.s.held.isNotEmpty(), samples) }
            AeCardBox(604, 210, 374, 130, "SHAPING", CYAN, border)
            AeGlassKnob(c, 10, "Pulse W", 640, 240, 40); AeGlassKnob(c, 11, "Detune", 724, 240, 40); AeGlassKnob(c, 29, "Glide", 808, 240, 40); AeGlassKnob(c, 31, "Level", 892, 240, 40)
        }
        1 -> {
            AeCardBox(22, 54, 566, 286, "AHDSR ENVELOPE", CYAN, border)
            Canvas(Modifier.at(36, 84, 538, 150).drag01 { px, py ->
                if (px < 0.2f) c.set(12, px / 0.2f)
                else if (px < 0.36f) c.set(13, (px - 0.2f) / 0.16f)
                else if (px < 0.62f) { c.set(14, (px - 0.36f) / 0.26f); c.set(15, 1f - py) }
                else if (px > 0.74f) c.set(16, (px - 0.74f) / 0.26f)
            }) { a.frame.intValue; drawLeadEnv(y) }
            AeGlassKnob(c, 12, "Attack", 60, 244, 40); AeGlassKnob(c, 13, "Hold", 160, 244, 40); AeGlassKnob(c, 14, "Decay", 260, 244, 40); AeGlassKnob(c, 15, "Sustain", 360, 244, 40); AeGlassKnob(c, 16, "Release", 460, 244, 40)
            AeCardBox(604, 54, 374, 286, "FILTER", CYAN, border)
            arrayOf("LP", "HP", "BP").forEachIndexed { i, t -> AeChip(c, 17, i, 3, t, 620 + i * 62, 82, 56) }
            Canvas(Modifier.at(620, 112, 342, 93).drag01 { px, py -> c.set(18, px); c.set(19, 1f - py) }) { a.frame.intValue; drawLeadFilter(a, y) }
            AeGlassKnob(c, 18, "Cutoff", 640, 222, 44); AeGlassKnob(c, 19, "Reso", 726, 222, 44); AeGlassKnob(c, 20, "Env Amt", 812, 222, 44)
        }
        2 -> {
            AeCardBox(22, 54, 300, 286, "DELAY", CYAN, border)
            Canvas(Modifier.at(38, 84, 268, 56)) { drawDelayViz(y) }
            AeGlassKnob(c, 21, "Time", 50, 170, 46); AeGlassKnob(c, 22, "Feedback", 130, 170, 46); AeGlassKnob(c, 23, "Mix", 210, 170, 46)
            AeCardBox(336, 54, 300, 286, "REVERB", CYAN, border)
            Canvas(Modifier.at(352, 84, 268, 56)) { drawReverbViz(y) }
            AeGlassKnob(c, 24, "Size", 390, 170, 46); AeGlassKnob(c, 25, "Mix", 490, 170, 46)
            AeCardBox(650, 54, 328, 286, "CHORUS · TONE", CYAN, border)
            Canvas(Modifier.at(666, 84, 296, 56)) { a.frame.intValue; drawToneViz(y, a.t) }
            AeGlassKnob(c, 26, "Chorus", 700, 170, 46); AeGlassKnob(c, 27, "Tone", 810, 170, 46)
        }
        else -> {
            AeCardBox(22, 54, 420, 286, "VOICE MODE", CYAN, border)
            arrayOf("POLY", "MONO", "LEGATO").forEachIndexed { i, t -> AeChip(c, 28, i, 3, t, 38 + i * 128, 84, 120) }
            AeGlassKnob(c, 29, "Glide", 70, 150, 56); AeGlassKnob(c, 30, "Vibrato", 190, 150, 56); AeGlassKnob(c, 31, "Level", 310, 150, 56)
            AeCardBox(458, 54, 520, 286, "PERFORMANCE", CYAN, border)
            AeT(if (c.s.held.isEmpty()) "—" else noteName(c.s.lastNote), 64, Color(0xFFD9FBFF), Modifier.at(474, 96, 200, 80), AeFonts.display, FontWeight.Light)
            Canvas(Modifier.at(690, 90, 270, 86)) { a.frame.intValue; drawVibViz(y, a.t, c.s.held.isNotEmpty()) }
            Canvas(Modifier.at(474, 210, 490, 110)) { drawLeadKeys { c.s.held.containsKey(it) } }
        }
    }
    AeKeyboard(c, 52, 360, 896, 90)
}
