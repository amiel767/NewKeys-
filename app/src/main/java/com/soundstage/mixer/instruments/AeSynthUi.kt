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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt

@Composable
private fun Card(x: Number, y: Number, w: Number, h: Number, title: String) {
    val sh = RoundedCornerShape(18.su())
    Box(Modifier.at(x, y, w, h).clip(sh).background(Brush.linearGradient(listOf(Color(0x14FFFFFF), Color(0x05FFFFFF)))).border(1.su(), Color(0x1CFFFFFF), sh))
    AeT(title, 9, AeC.violet, Modifier.at(x.toFloat() + 16f, y.toFloat() + 12f, 200, 12), spacing = 0.2f, weight = FontWeight.ExtraBold)
}

/** Draggable canvas (filter puck / ADSR nodes): reports a 0..1 position inside the canvas. */
internal fun Modifier.drag01(onPos: (Float, Float) -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        val d = awaitFirstDown(requireUnconsumed = false)
        onPos((d.position.x / size.width).coerceIn(0f, 1f), (d.position.y / size.height).coerceIn(0f, 1f)); d.consume()
        drag(d.id) { onPos((it.position.x / size.width).coerceIn(0f, 1f), (it.position.y / size.height).coerceIn(0f, 1f)); it.consume() }
    }
}

/** Polysynth: SOUND / MOTION / VOICE pages around the central sphere. Same layout as the validated preview. */
@Composable
fun BoxScope.AeSynthUi(c: AeCtl, a: AeAnim) {
    val y: (Int) -> Float = { c.v(it) }
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF2A1A66), Color(0xFF0D0A24), Color(0xFF05040F)), center = androidx.compose.ui.geometry.Offset.Unspecified, radius = 900f)))
    Canvas(Modifier.at(375, 50, 250, 250)) { a.frame.intValue; drawOrb(a, y) }

    // page tabs
    AePageTabs(c, arrayOf("SOUND", "MOTION", "VOICE"))

    when (c.s.page) {
        0 -> {
            // The cards run to y=426 instead of 340: the band the on-screen keyboard used to
            // occupy now belongs to the controls that were cramped against it. The two waveform
            // views, the filter curve and the envelope all roughly double in height, which is
            // where the extra room actually reads — the knob rows are already at their tightest
            // pitch (66 units, and an AeGlassKnob's touch box is its size plus 22).
            Card(22, 54, 272, 372, "OSCILLATORS")
            Canvas(Modifier.at(36, 84, 240, 56)) { a.frame.intValue; drawWave(y(0), if (c.s.held.isEmpty()) 0f else a.t * 0.6f) }
            Canvas(Modifier.at(36, 146, 240, 56)) { a.frame.intValue; drawWave(y(1), if (c.s.held.isEmpty()) 0.3f else a.t * 0.6f + 0.3f) }
            AeGlassKnob(c, 0, "Wave A", 40, 212, 46); AeGlassKnob(c, 1, "Wave B", 106, 212, 46); AeGlassKnob(c, 2, "Detune", 172, 212, 46); AeGlassKnob(c, 3, "Sub", 238, 212, 46)
            AeGlassKnob(c, 4, "Noise", 40, 300, 46); AeGlassKnob(c, 5, "Unison", 106, 300, 46); AeGlassKnob(c, 6, "Spread", 172, 300, 46)

            Card(706, 54, 272, 246, "FILTER")
            arrayOf("LP", "HP", "BP", "NOTCH").forEachIndexed { i, t -> AeChip(c, 11, i, 4, t, 722 + i * 56, 82, 52) }
            Canvas(Modifier.at(720, 112, 250, 106).drag01 { px, py -> c.set(7, px); c.set(8, 1f - py) }) { a.frame.intValue; drawFilter(a, y) }
            AeGlassKnob(c, 7, "Cutoff", 722, 228, 46); AeGlassKnob(c, 8, "Reso", 790, 228, 46); AeGlassKnob(c, 9, "Drive", 858, 228, 46); AeGlassKnob(c, 10, "Env", 926, 228, 46)

            Card(706, 306, 272, 120, "ENVELOPE")
            Canvas(Modifier.at(720, 330, 250, 86).drag01 { px, py ->
                if (px < 0.28f) c.set(12, px / 0.28f * 0.9f)
                else if (px < 0.55f) { c.set(13, (px - 0.28f) / 0.27f); c.set(14, 1f - py) }
                else if (px > 0.7f) c.set(15, (px - 0.7f) / 0.3f)
            }) { a.frame.intValue; drawEnv(y) }
        }
        1 -> {
            Card(22, 54, 312, 286, "LFO 1")
            Canvas(Modifier.at(38, 86, 280, 55)) { a.frame.intValue; drawLfo(0.1f * Math.pow(100.0, y(16).toDouble()).toFloat(), (y(18) * 3).roundToInt(), y(17), a.t * 0.1f * Math.pow(100.0, y(16).toDouble()).toFloat()) }
            arrayOf("SIN", "TRI", "SQR", "SAW").forEachIndexed { i, t -> AeChip(c, 18, i, 4, t, 38 + i * 66, 150, 62) }
            arrayOf("CUTOFF", "PITCH", "AMP", "BOTH").forEachIndexed { i, t -> AeChip(c, 19, i, 4, t, 38 + i * 66, 182, 62) }
            AeGlassKnob(c, 16, "Rate", 60, 226, 46); AeGlassKnob(c, 17, "Depth", 160, 226, 46)
            Card(666, 54, 312, 286, "LFO 2 · VIBRATO")
            Canvas(Modifier.at(682, 86, 280, 55)) { a.frame.intValue; drawLfo(0.1f * Math.pow(100.0, y(20).toDouble()).toFloat(), 0, y(21), a.t * 0.1f * Math.pow(100.0, y(20).toDouble()).toFloat()) }
            AeGlassKnob(c, 20, "Rate", 710, 226, 46); AeGlassKnob(c, 21, "Depth", 810, 226, 46)
            AeT("MACROS", 9, AeC.violet, Modifier.at(372, 296, 256, 12), spacing = 0.2f, weight = FontWeight.ExtraBold, align = TextAlign.Center)
            AeGlassKnob(c, 22, "Color", 338, 306, 38); AeGlassKnob(c, 23, "Motion", 414, 306, 38); AeGlassKnob(c, 24, "Grit", 490, 306, 38); AeGlassKnob(c, 25, "Air", 566, 306, 38)
        }
        else -> {
            Card(22, 54, 312, 286, "VOICE")
            arrayOf("POLY", "MONO", "LEGATO").forEachIndexed { i, t -> AeChip(c, 26, i, 3, t, 38 + i * 90, 86, 84) }
            AeIntensitySlider(c, 32, 38, 126, 270, AeC.p2)
            AeGlassKnob(c, 27, "Glide", 54, 190, 50); AeGlassKnob(c, 28, "Voices", 166, 190, 50)
            Card(666, 54, 312, 286, "PITCH · OUTPUT")
            // No "Bend" knob here: it was a dead control (the range now comes from
            // Track Settings, which is the single place the user edits it) and it
            // displayed semitones, so it contradicted the Track Settings readout.
            AeGlassKnob(c, 30, "Tune", 700, 130, 50); AeGlassKnob(c, 31, "Level", 812, 130, 50)
        }
    }
}
