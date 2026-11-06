package com.soundstage.mixer.instruments

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * OrganLab — the tonewheel console, translated from the reference interface.
 *
 * Geometry comes from the reference panel (632 x 364), scaled onto this stage (1000 x 460) by
 * x 1.582 and y 1.264. Nothing here is placed by eye: every x and y below is a measured
 * position times that scale. The reference is red lacquer; this cabinet is walnut, as asked.
 *
 * ONE drawbar row, not two — "on aura qu'un seul Drawbar comme dans l'image".
 *
 * What the reference has and this console keeps, because the engine gained it:
 * PROGRAMS (the user preset name), ROOM/STAGE/HALL, TREBLE, CABINET, AMOUNT, VEL SENS.
 * What it drops, as asked: TRANSPOSE, KEY RANGE, PITCH BEND, SUSTAIN, MIDI CC.
 */

/** Drawbar feet, left to right in the Hammond order (16' … 1'). */
private val FOOT = arrayOf("16'", "5⅓'", "8'", "4'", "2⅔'", "2'", "1⅗'", "1⅓'", "1'")
/** Cap colour per drawbar: 0 brown, 1 white, 2 black — the Hammond B3 order. */
private val CAPS = intArrayOf(0, 0, 1, 1, 2, 1, 2, 2, 1)

/** Registration readout: the nine drawbar steps as one number, spaced every three bars. */
private fun reg(c: AeCtl, off: Int) = (0..8).joinToString("") { (if (it == 2 || it == 6) " " else "") + (c.v(off + it) * 8).roundToInt() }

/**
 * End cheek: the same lacquered walnut as the cabinet, a shade darker, lit along its inner edge.
 * Kept from the previous console — the user approved "le cadre et le fond".
 */
@Composable
private fun BoxScope.Rail(x: Number, flip: Boolean) {
    val sh = RoundedCornerShape(topStart = if (flip) 0.su() else 24.su(), topEnd = if (flip) 24.su() else 0.su())
    Box(
        Modifier.at(x, 0, 46, 460).clip(sh).aeWood(Color(0xFF6A3A20), if (flip) 9 else 4)
            .drawBehind {
                val ex = if (flip) 0f else size.width
                drawLine(Color(0x33FFFFFF), Offset(ex, 0f), Offset(ex, size.height), 1.5f)
            },
    )
}

/** Inset panel: darker walnut, so the controls read as recessed into the cabinet. */
@Composable
private fun Panel(x: Number, y: Number, w: Number, h: Number, r: Number = 10) {
    val sh = RoundedCornerShape(r.su())
    Box(
        Modifier.at(x, y, w, h).clip(sh).aeWood(Color(0xFF4A2815), (x.toInt() / 7) + 3)
            .border(1.su(), Color(0x33000000), sh),
    )
}

/** Section caption, in the instrument's accent. */
@Composable
private fun Caption(text: String, x: Number, y: Number, w: Number) {
    AeT(text, 9, AeC.organ, Modifier.at(x, y, w, 12), weight = FontWeight.ExtraBold, spacing = 0.2f, align = TextAlign.Center)
}

/** One lit readout line: the registration, or the loaded preset's name. */
@Composable
private fun Readout(text: String, x: Number, y: Number, w: Number, size: Number = 13, color: Color = AeC.organ) {
    val sh = RoundedCornerShape(6.su())
    Box(
        Modifier.at(x, y, w, 20).clip(sh).background(Color(0xFF070B10))
            .border(1.su(), Color(0x22FFFFFF), sh),
        contentAlignment = Alignment.Center,
    ) {
        AeT(text, size, color, Modifier.padding(horizontal = 4.su()), AeFonts.mono, FontWeight.Bold, 0.1f, TextAlign.Center)
    }
}

/**
 * OrganLab console.
 *
 * Columns, in stage units, from the reference's own:
 *   title bar       y   8..30   title left, preset name, octave
 *   UPPER           x  18..190  y  70..278   readout, then the nine drawbars
 *   VIBRATO         x 296..400  y  70..278   the six-position dial and its two switches
 *   PERCUSSION      x 478..618  y  70..278   the four percussion rockers
 *   BASS / TREBLE   x 718..980  y  70..278   two intensity sliders
 *   ROOM·STAGE·HALL x 718..980  y 292..396   reverb mode, cabinet depth, rotor speed
 *   VOICING         the five knobs, in the band the keyboard used to fill
 */
@Composable
fun BoxScope.AeOrganUi(c: AeCtl, a: AeAnim) {
    Box(Modifier.fillMaxSize().drawBehind {
        drawWood(Color(0xFF6A3A20), 4)
        drawRect(Brush.radialGradient(listOf(Color(0x1E7FD4EE), Color.Transparent), center = Offset(size.width * 0.2f, -size.height * 0.15f), radius = size.width * 0.75f))
    })
    Rail(0, flip = false)
    Rail(954, flip = true)

    // ── PROGRAMS: the loaded user preset, where the reference shows it. The name comes from the
    // window's own manager, the same read the header pill uses, so the two never disagree.
    Caption("PROGRAMS", 24, 34, 160)
    Readout(c.m.presetNameOn(c.s.strip) ?: c.s.instrument.title, 24, 48, 160, 12)

    // ── UPPER: one row of nine drawbars, with its registration number above it.
    Panel(18, 70, 172, 208)
    AeT("UPPER", 9, AeC.organ, Modifier.at(30, 76, 148, 12), weight = FontWeight.ExtraBold, spacing = 0.2f)
    Readout(reg(c, 0), 30, 90, 148, 12)
    for (i in 0 until 9) AeDrawbar(c, i, CAPS[i], FOOT[i], 28 + i * 17, 114, h = 158, step = 17, limitTop = 112, w = 16)

    // ── VIBRATO · CHORUS: the six-position dial and the two manual switches.
    Panel(296, 70, 104, 208)
    Caption("VIBRATO", 296, 76, 104)
    val step = (c.v(22) * 5).roundToInt()
    val vibScale = LocalAeScale.current
    val vibClick = Modifier.at(318, 88, 60, 60).pointerInput(Unit) {
        awaitEachGesture {
            val d = awaitFirstDown(requireUnconsumed = false)
            val start = c.v(22)
            d.consume()
            drag(d.id) { c.set(22, start - it.position.y / (70f * vibScale)); it.consume() }
        }
    }
    Box(vibClick) {
        arrayOf("V1", "V2", "V3", "C1", "C2", "C3").forEachIndexed { i, t ->
            val an = Math.toRadians((-75 + 30 * i - 90).toDouble())
            AeT(t, 8, AeC.organ, Modifier.at(30 + cos(an).toFloat() * 26f - 10f, 30 + sin(an).toFloat() * 26f - 5f, 20, 10), AeFonts.plate, FontWeight.ExtraBold, align = TextAlign.Center)
        }
        Box(Modifier.at(14, 14, 32, 32).aeSvg(AeSvgs.KNOB_METAL))
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / 60f
            rotate(-75f + 30f * step, Offset(30f * k, 30f * k)) { drawRoundRect(Color(0xFF16161A), Offset(29.2f * k, 21f * k), Size(1.8f * k, 8f * k), CornerRadius(1f * k)) }
        }
    }
    AeRocker(c, 23, "UPPER", 300, 168, 44)
    AeRocker(c, 24, "LOWER", 348, 168, 44)

    // ── PERCUSSION: the four rockers, the reference's own order.
    Panel(478, 70, 140, 208)
    Caption("PERCUSSION", 478, 76, 140)
    AeRocker(c, 18, "ON", 484, 96, 60)
    AeRocker(c, 19, "SOFT", 484, 138, 60)
    AeRocker(c, 20, "FAST", 548, 138, 60)
    AeRocker(c, 21, "3RD", 484, 180, 60)

    // ── BASS / TREBLE: the reference's two intensity sliders.
    Panel(718, 70, 262, 208)
    Caption("BASS / TREBLE", 718, 76, 262)
    AeIntensitySlider(c, 27, 736, 96, 226, AeC.organ)
    AeIntensitySlider(c, 32, 736, 148, 226, AeC.organ)

    // ── ROOM · STAGE · HALL, then CABINET and the rotor speed, the two bottom rows.
    Panel(718, 292, 262, 104)
    Caption("ROOM · STAGE · HALL", 718, 298, 262)
    AeSeg(listOf("ROOM", "STAGE", "HALL"), (c.v(31) * 2).roundToInt(), 736, 314, 226, AeC.organ, onSelect = { c.set(31, it / 2f) })
    Caption("CABINET · SPEED", 718, 344, 262)
    AeIntensitySlider(c, 33, 736, 356, 130, AeC.organ)
    val les = (c.v(25) * 2).roundToInt()
    val lx by animateFloatAsState(les * 50f, label = "leslie")
    Box(Modifier.at(882, 354, 80, 36).clickable(remember { MutableInteractionSource() }, null) { c.set(25, ((les + 1) % 3) / 2f) }) {
        Canvas(Modifier.fillMaxSize()) { a.frame.intValue; drawRotor(a) }
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 150f
            drawRoundRect(Color(0xFF0A0A0D), Offset.Zero, size, CornerRadius(8f * u))
            drawRoundRect(Color(0x22FFFFFF), Offset.Zero, size, CornerRadius(8f * u), style = Stroke(u))
            for (i in 0..2) {
                if (i == les) drawCircle(Color(0x55FFA010), 6f * u, Offset((25f + i * 50f) * u, 8f * u))
                drawCircle(if (i == les) Color(0xFFFFB020) else Color(0xFF2A1A08), 3f * u, Offset((25f + i * 50f) * u, 8f * u))
            }
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF6C7078), Color(0xFFF0F2F6), Color(0xFF4C5058)), (19f + lx) * u, (31f + lx) * u), Offset((19f + lx) * u, 9f * u), Size(12f * u, 13f * u), CornerRadius(4f * u))
        }
        arrayOf("STOP", "SLOW", "FAST").forEachIndexed { i, t ->
            AeT(t, 7, AeC.organ, Modifier.at(25 + i * 50 - 20, 23, 40, 10), AeFonts.plate, FontWeight.ExtraBold, align = TextAlign.Center)
        }
    }

    // ── VOICING: CLICK, DRIVE and TONE voice the organ; LEVEL is the output gain; VEL SENS is the
    // reference's velocity row, which the engine now honours.
    val kw = 78
    val kx = 354
    AeT("VOICING", 9, AeC.organ, Modifier.at(402, 344, 196, 12), weight = FontWeight.ExtraBold, spacing = 0.2f, align = TextAlign.Center)
    AeMetalKnob(c, 26, "CLICK", kx, 362, 58)
    AeMetalKnob(c, 28, "DRIVE", kx + kw, 362, 58)
    AeMetalKnob(c, 27, "TONE", kx + kw * 2, 362, 58)
    AeMetalKnob(c, 30, "LEVEL", kx + kw * 3, 362, 58)
    AeMetalKnob(c, 35, "VEL SENS", kx + kw * 4, 362, 58)
}
