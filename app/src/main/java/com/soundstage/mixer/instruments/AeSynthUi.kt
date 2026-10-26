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
    val tabShape = RoundedCornerShape(50)
    Box(Modifier.at(318, 7, 364, 32).clip(tabShape).background(Color(0x10FFFFFF)).border(1.su(), Color(0x18FFFFFF), tabShape))
    arrayOf("SOUND", "MOTION", "VOICE").forEachIndexed { i, t ->
        val on = c.s.page == i
        Box(Modifier.at(322 + i * 119, 10, 117, 26).clip(tabShape).then(if (on) Modifier.background(Brush.horizontalGradient(listOf(AeC.p1, AeC.p2))) else Modifier)
            .clickable(remember { MutableInteractionSource() }, null) { c.s.page = i }, contentAlignment = Alignment.Center) {
            AeT(t, 10, Color.White, spacing = 0.14f)
        }
    }

    when (c.s.page) {
        0 -> {
            Card(22, 54, 272, 286, "OSCILLATORS")
            Canvas(Modifier.at(36, 88, 240, 42)) { a.frame.intValue; drawWave(y(0), if (c.s.held.isEmpty()) 0f else a.t * 0.6f) }
            Canvas(Modifier.at(36, 136, 240, 42)) { a.frame.intValue; drawWave(y(1), if (c.s.held.isEmpty()) 0.3f else a.t * 0.6f + 0.3f) }
            AeGlassKnob(c, 0, "Wave A", 36, 190, 42); AeGlassKnob(c, 1, "Wave B", 104, 190, 42); AeGlassKnob(c, 2, "Detune", 172, 190, 42); AeGlassKnob(c, 3, "Sub", 240, 190, 42)
            AeGlassKnob(c, 4, "Noise", 36, 262, 42); AeGlassKnob(c, 5, "Unison", 104, 262, 42); AeGlassKnob(c, 6, "Spread", 172, 262, 42)

            Card(706, 54, 272, 196, "FILTER")
            arrayOf("LP", "HP", "BP", "NOTCH").forEachIndexed { i, t -> AeChip(c, 11, i, 4, t, 722 + i * 56, 82, 52) }
            Canvas(Modifier.at(720, 108, 250, 75).drag01 { px, py -> c.set(7, px); c.set(8, 1f - py) }) { a.frame.intValue; drawFilter(a, y) }
            AeGlassKnob(c, 7, "Cutoff", 720, 192, 36); AeGlassKnob(c, 8, "Reso", 782, 192, 36); AeGlassKnob(c, 9, "Drive", 844, 192, 36); AeGlassKnob(c, 10, "Env", 906, 192, 36)

            Card(706, 256, 272, 84, "ENVELOPE")
            Canvas(Modifier.at(720, 282, 250, 50).drag01 { px, py ->
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
            AeGlassKnob(c, 22, "Color", 338, 308, 30); AeGlassKnob(c, 23, "Motion", 414, 308, 30); AeGlassKnob(c, 24, "Grit", 490, 308, 30); AeGlassKnob(c, 25, "Air", 566, 308, 30)
        }
        else -> {
            Card(22, 54, 312, 286, "VOICE")
            arrayOf("POLY", "MONO", "LEGATO").forEachIndexed { i, t -> AeChip(c, 26, i, 3, t, 38 + i * 90, 86, 84) }
            AeGlassKnob(c, 27, "Glide", 54, 156, 50); AeGlassKnob(c, 28, "Voices", 166, 156, 50)
            Card(666, 54, 312, 286, "PITCH · OUTPUT")
            AeGlassKnob(c, 29, "Bend", 700, 100, 50); AeGlassKnob(c, 30, "Tune", 812, 100, 50); AeGlassKnob(c, 31, "Level", 756, 196, 56)
        }
    }
    AeKeyboard(c, 52, 360, 896, 90)
}
