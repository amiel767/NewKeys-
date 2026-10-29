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
import com.soundstage.mixer.ui.components.FaderRail
import com.soundstage.mixer.ui.components.FaderThumb
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

/**
 * LED fader: the mixer's rail and cap, live value bar. Drag anywhere on the track.
 *
 * Only the *width* is slimmed (44 -> 34 units). The length is the original 160 with the
 * original 40-unit cap: the first attempt shortened the throw to 120 and read as a stubby
 * block rather than a fader, which is not what "make the rails thinner" asked for. The rail
 * body is [RAIL_W], inset inside [FADER_W], so the cap is wider than its track the way a real
 * fader's is.
 *
 * The value readout under the rail is gone on purpose: five numbers under five rails competed
 * with the rail LEDs for attention, and the LED height already carries the level.
 *
 * The cap travels inside [CAP_TOP]..[CAP_BOTTOM]. Everything derives from [capCenterY], so the
 * drawing and the drag cannot disagree.
 */
private const val FADER_H = 160f
private const val FADER_W = 34f
private const val CAP_H = 40f
private const val CAP_W = CAP_H * (1327f / 1657f)  // FaderThumb is portrait
private const val RAIL_W = 16f
private const val GROOVE_TOP = 8f
private const val LABEL_TOP = 145f
private const val CAP_TOP = GROOVE_TOP + CAP_H / 2f
private const val CAP_BOTTOM = LABEL_TOP - CAP_H / 2f
private const val CAP_TRAVEL = CAP_BOTTOM - CAP_TOP

/** Centre of the cap for a 0..1 value. */
private fun capCenterY(v: Float) = CAP_TOP + (1f - v.coerceIn(0f, 1f)) * CAP_TRAVEL

@Composable
private fun AeFader(c: AeCtl, idx: Int, x: Number, y: Number) {
    val sc = LocalAeScale.current
    val v = c.v(idx)
    fun fromY(py: Float) = (1f - ((py / sc) - CAP_TOP) / CAP_TRAVEL).coerceIn(0f, 1f)
    Box(Modifier.at(x, y, FADER_W, FADER_H).pointerInput(idx) {
        awaitEachGesture {
            val d = awaitFirstDown(requireUnconsumed = false)
            c.set(idx, fromY(d.position.y)); d.consume()
            drag(d.id) { c.set(idx, fromY(it.position.y)); it.consume() }
        }
    }) {
        // The track is the mixer's own FaderRail, not the flat SVG slot it replaced: the same
        // chassis, rim, groove and LED halo the channel faders use, so a Leads fader reads as the
        // same piece of hardware. It is 86 units wide in its own space and scales from the box.
        FaderRail(
            accent = Color(0xFF19E3FF),
            modifier = Modifier.at(FADER_W / 2f - RAIL_W / 2f, 0f, RAIL_W, FADER_H),
            level = v,
            glow = 0.30f + 0.70f * v,
        )
        val cy = capCenterY(v)
        // Cap = the same Kotlin bonnet the mixer track faders use (FaderThumb), not the flat
        // SVG rectangle it replaced. The box centres the cap on its value.
        Box(Modifier.at(FADER_W / 2f - CAP_W / 2f, cy - CAP_H / 2f, CAP_W, CAP_H)) {
            FaderThumb(
                accent = Color(0xFF19E3FF),
                modifier = Modifier.fillMaxSize(),
                glow = 0.28f + 0.72f * v,
            )
        }
    }
}

@Composable
private fun LeadsTabs(c: AeCtl) = AePageTabs(c, arrayOf("SOUND", "SHAPE", "FX", "PLAY"))

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
                // Centred on the wave icon above it: the icon box is x+30..x+70, so x+50 is its
                // axis, and the fader box is [FADER_W] wide from x+33.
                AeFader(c, i, x + 33, 118)
                // One octave switch per layer, the app's segmented toggle rather than three
                // loose chips: "−1 0 +1" is a single setting with three positions.
                AeSeg(listOf("−1", "0", "+1"), (c.v(5 + i) * 2f).roundToInt(), x + 3, 278, 102, CYAN, onSelect = { j -> c.set(5 + i, j / 2f) })
            }
            Canvas(Modifier.at(22, 118, 566, 160)) { a.frame.intValue; drawMeters(a) }
            AeCardBox(604, 54, 374, 150, "OSCILLOSCOPE", CYAN, border)
            Canvas(Modifier.at(620, 82, 342, 107).clip(RoundedCornerShape(6.su()))) { a.frame.intValue; drawLeadScope(a, y, c.s.held.isNotEmpty(), samples) }
            AeCardBox(604, 210, 374, 130, "SHAPING", CYAN, border)
            AeGlassKnob(c, 10, "Pulse W", 640, 238, 46); AeGlassKnob(c, 11, "Detune", 724, 238, 46); AeGlassKnob(c, 29, "Glide", 808, 238, 46); AeGlassKnob(c, 31, "Level", 892, 238, 46)
        }
        1 -> {
            AeCardBox(22, 54, 566, 286, "AHDSR ENVELOPE", CYAN, border)
            Canvas(Modifier.at(36, 84, 538, 150).drag01 { px, py ->
                if (px < 0.2f) c.set(12, px / 0.2f)
                else if (px < 0.36f) c.set(13, (px - 0.2f) / 0.16f)
                else if (px < 0.62f) { c.set(14, (px - 0.36f) / 0.26f); c.set(15, 1f - py) }
                else if (px > 0.74f) c.set(16, (px - 0.74f) / 0.26f)
            }) { a.frame.intValue; drawLeadEnv(y) }
            AeGlassKnob(c, 12, "Attack", 60, 240, 48); AeGlassKnob(c, 13, "Hold", 160, 240, 48); AeGlassKnob(c, 14, "Decay", 260, 240, 48); AeGlassKnob(c, 15, "Sustain", 360, 240, 48); AeGlassKnob(c, 16, "Release", 460, 240, 48)
            AeCardBox(604, 54, 374, 286, "FILTER", CYAN, border)
            arrayOf("LP", "HP", "BP").forEachIndexed { i, t -> AeChip(c, 17, i, 3, t, 620 + i * 62, 82, 56) }
            Canvas(Modifier.at(620, 112, 342, 93).drag01 { px, py -> c.set(18, px); c.set(19, 1f - py) }) { a.frame.intValue; drawLeadFilter(a, y) }
            AeGlassKnob(c, 18, "Cutoff", 640, 218, 48); AeGlassKnob(c, 19, "Reso", 726, 218, 48); AeGlassKnob(c, 20, "Env Amt", 812, 218, 48)
        }
        2 -> {
            AeCardBox(22, 54, 300, 286, "DELAY", CYAN, border)
            Canvas(Modifier.at(38, 84, 268, 56)) { drawDelayViz(y) }
            AeGlassKnob(c, 21, "Time", 50, 166, 48); AeGlassKnob(c, 22, "Feedback", 130, 166, 48); AeGlassKnob(c, 23, "Mix", 210, 166, 48)
            AeCardBox(336, 54, 300, 286, "REVERB", CYAN, border)
            Canvas(Modifier.at(352, 84, 268, 56)) { drawReverbViz(y) }
            AeGlassKnob(c, 24, "Size", 390, 166, 48); AeGlassKnob(c, 25, "Mix", 490, 166, 48)
            AeCardBox(650, 54, 328, 286, "CHORUS · TONE", CYAN, border)
            Canvas(Modifier.at(666, 84, 296, 56)) { a.frame.intValue; drawToneViz(y, a.t) }
            AeGlassKnob(c, 26, "Chorus", 700, 166, 48); AeGlassKnob(c, 27, "Tone", 810, 166, 48)
        }
        else -> {
            AeCardBox(22, 54, 420, 286, "VOICE MODE", CYAN, border)
            arrayOf("POLY", "MONO", "LEGATO").forEachIndexed { i, t -> AeChip(c, 28, i, 3, t, 38 + i * 128, 84, 120) }
            // Intensity sits in the empty band under the mode chips: the card is 286 tall
            // and the knobs end at 206, so this is the "de la place" the user pointed at.
            AeIntensitySlider(c, 32, 38, 122, 370, CYAN)
            AeGlassKnob(c, 29, "Glide", 70, 182, 58); AeGlassKnob(c, 30, "Vibrato", 190, 182, 58); AeGlassKnob(c, 31, "Level", 310, 182, 58)
            AeCardBox(458, 54, 520, 286, "PERFORMANCE", CYAN, border)
            AeT(if (c.s.held.isEmpty()) "—" else noteName(c.s.lastNote), 64, Color(0xFFD9FBFF), Modifier.at(474, 96, 200, 80), AeFonts.display, FontWeight.Light)
            Canvas(Modifier.at(690, 90, 270, 86)) { a.frame.intValue; drawVibViz(y, a.t, c.s.held.isNotEmpty()) }
            Canvas(Modifier.at(474, 210, 490, 110)) { drawLeadKeys { c.s.held.containsKey(it) } }
        }
    }
    AeKeyboard(c, 52, 360, 896, 90)
}
