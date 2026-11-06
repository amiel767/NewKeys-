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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

/** Drawbar feet, left to right, in the Hammond order (16' … 1'). */
private val FOOT = arrayOf("16'", "5⅓'", "8'", "4'", "2⅔'", "2'", "1⅗'", "1⅓'", "1'")
/** Cap colour per drawbar: 0 brown, 1 white, 2 black — the Hammond B3 order. */
private val CAPS = intArrayOf(0, 0, 1, 1, 2, 1, 2, 2, 1)

/** Registration readout: the nine drawbar steps as one number, spaced every three bars. */
private fun reg(c: AeCtl, off: Int) = (0..8).joinToString("") { (if (it == 2 || it == 6) " " else "") + (c.v(off + it) * 8).roundToInt() }

/** End cheek: the same lacquered walnut as the cabinet, a shade darker, lit along its inner edge. */
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

/** Inset panel: darker walnut, so the manuals read as recessed into the cabinet. */
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

/**
 * Tonewheel organ, laid out like the reference console (stage 1000 x 460): two manuals of nine
 * drawbars with their registration number, the vibrato/chorus dial and its two switches, the
 * percussion row, and the five voicing knobs.
 *
 * The band the virtual keyboard used to occupy now carries the voicing row, which is what those
 * knobs needed: five of them could not be made larger while they were squeezed into the 372 units
 * right of the manuals. There is no output meter and no tonewheel window here — the window is a
 * control surface, and the instrument's own level knob already sits in the voicing row.
 */
@Composable
fun BoxScope.AeOrganUi(c: AeCtl, a: AeAnim) {
    Box(Modifier.fillMaxSize().drawBehind {
        // Smooth lacquered walnut: the cabinet finish, not raw timber.
        drawWood(Color(0xFF6A3A20), 4)
        // Soft key light from the upper left, so the grain survives instead of washing into a
        // flat tint.
        drawRect(Brush.radialGradient(listOf(Color(0x1E7FD4EE), Color.Transparent), center = Offset(size.width * 0.2f, -size.height * 0.15f), radius = size.width * 0.75f))
    })
    Rail(0, flip = false)
    Rail(954, flip = true)
    // The two manuals. Each registration number sits with its own drawbar set, so a manual reads
    // as one unit: panel, caption, registration, then the nine bars. The bars are 250 long instead
    // of 196, and their step spacing follows, which is the room the missing keyboard bought.
    listOf(Triple(54, 0, "UPPER MANUAL"), Triple(606, 9, "LOWER MANUAL")).forEach { (x, off, title) ->
        Panel(x, 54, 340, 286)
        AeT(title, 9, AeC.organ, Modifier.at(x + 14, 62, 170, 12), weight = FontWeight.ExtraBold, spacing = 0.2f)
        AeT(reg(c, off), 13, AeC.organ, Modifier.at(x + 196, 58, 130, 18), AeFonts.mono, FontWeight.Normal, 0.08f, TextAlign.End)
        for (i in 0 until 9) AeDrawbar(c, off + i, CAPS[i], FOOT[i], x + 17 + i * 34, 82, h = 250, step = 21, limitTop = 76)
        for (k in 0..8) AeT("$k", 8, Color(0xFF8FA6B8), Modifier.at(x + 4, 82 + 24 + k * 21, 10, 10), AeFonts.mono, FontWeight.Normal, align = TextAlign.End)
    }

    // Centre column, same height as the manuals so the three panels read as one row: vibrato/chorus
    // on top, then the two manual switches, percussion, and the rotary speaker.
    Panel(402, 54, 196, 286)
    Caption("VIBRATO · CHORUS", 402, 62, 196)
    val step = (c.v(22) * 5).roundToInt()
    // Drag, not click-to-advance: a six-position cycle needed up to five taps to cross the dial.
    val vibScale = LocalAeScale.current
    val vibClick = Modifier.at(440, 72, 120, 120).pointerInput(Unit) {
        awaitEachGesture {
            val d = awaitFirstDown(requireUnconsumed = false)
            val start = c.v(22)
            d.consume()
            drag(d.id) { c.set(22, start - it.position.y / (130f * vibScale)); it.consume() }
        }
    }
    Box(vibClick) {
        arrayOf("V1", "V2", "V3", "C1", "C2", "C3").forEachIndexed { i, t ->
            val an = Math.toRadians((-75 + 30 * i - 90).toDouble())
            AeT(t, 8, AeC.organ, Modifier.at(60 + cos(an).toFloat() * 50f - 12f, 60 + sin(an).toFloat() * 50f - 8f, 24, 11), AeFonts.plate, FontWeight.ExtraBold, align = TextAlign.Center)
        }
        Box(Modifier.at(27, 27, 66, 66).aeSvg(AeSvgs.KNOB_METAL))
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / 120f
            rotate(-75f + 30f * step, Offset(60f * k, 60f * k)) { drawRoundRect(Color(0xFF16161A), Offset(58.4f * k, 43f * k), Size(3.2f * k, 15f * k), CornerRadius(1.6f * k)) }
        }
    }
    AeRocker(c, 23, "UPPER", 414, 196, 84)
    AeRocker(c, 24, "LOWER", 504, 196, 84)

    Caption("PERCUSSION", 402, 230, 196)
    AeRocker(c, 18, "ON", 410, 242, 44)
    AeRocker(c, 19, "SOFT", 456, 242, 44)
    AeRocker(c, 20, "FAST", 502, 242, 44)
    AeRocker(c, 21, "3RD", 548, 242, 44)

    Caption("ROTARY", 402, 276, 196)
    // The rotor animation sat at x=420 and the switch at x=442, so the two overlapped. The knob
    // that also drove param 25 is gone (a two-way control for one setting), and what is left is
    // centred in the panel: rotor 424..482, switch 492..642.
    Canvas(Modifier.at(424, 290, 58, 34)) { a.frame.intValue; drawRotor(a) }
    val les = (c.v(25) * 2).roundToInt()
    val lx by animateFloatAsState(les * 50f, label = "leslie")
    Box(Modifier.at(492, 288, 150, 34).clickable(remember { MutableInteractionSource() }, null) { c.set(25, ((les + 1) % 3) / 2f) }) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 150f
            drawRoundRect(Color(0xFF0A0A0D), Offset.Zero, size, CornerRadius(8f * u))
            drawRoundRect(Color(0x22FFFFFF), Offset.Zero, size, CornerRadius(8f * u), style = Stroke(u))
            drawRoundRect(Color.Black, Offset(14f * u, 14f * u), Size(122f * u, 3f * u), CornerRadius(1.5f * u))
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

    // The band the keyboard used to fill, y 340..460.
    //
    // The four voicing knobs sit in the CENTRE, under the centre panel, not spread across the
    // whole width: they voice the organ as a whole, so they belong to the middle column. AeMetalKnob
    // occupies size + 22 (its label hangs below the cap), and they are on a 78 pitch to leave air.
    val kw = 78
    val kx = 475 - (kw * 3) / 2
    AeT("VOICING", 9, AeC.organ, Modifier.at(402, 344, 196, 12), weight = FontWeight.ExtraBold, spacing = 0.2f, align = TextAlign.Center)
    AeMetalKnob(c, 26, "CLICK", kx, 362, 58)
    AeMetalKnob(c, 28, "DRIVE", kx + kw, 362, 58)
    AeMetalKnob(c, 27, "TONE", kx + kw * 2, 362, 58)
    AeMetalKnob(c, 30, "LEVEL", kx + kw * 3, 362, 58)
    // Two settings the engine reads but no control reached: where the vibrato/chorus is routed,
    // and whether the rotary is stopped, slow or fast. Both are real parameters, not readouts.
    AeT("VIBRATO", 8, AeC.goldDim, Modifier.at(72, 352, 120, 11), AeFonts.plate, spacing = 0.14f)
    AeChip(c, 23, 0, 2, "UPPER", 72, 366, 46)
    AeChip(c, 24, 1, 2, "LOWER", 122, 366, 46)
    AeT("ROTARY", 8, AeC.goldDim, Modifier.at(806, 352, 120, 11), AeFonts.plate, spacing = 0.14f, align = TextAlign.End)
    val rot = (c.v(25) * 2).roundToInt()
    arrayOf("STOP", "SLOW", "FAST").forEachIndexed { i, t -> AeChip(c, 25, i, 3, t, 806 + i * 50, 366, 46) }
}
