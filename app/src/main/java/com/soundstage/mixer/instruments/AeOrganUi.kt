package com.soundstage.mixer.instruments

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val FOOT = arrayOf("16'", "5⅓'", "8'", "4'", "2⅔'", "2'", "1⅗'", "1⅓'", "1'")
private val CAPS = intArrayOf(0, 0, 1, 1, 2, 1, 2, 2, 1)   // 0 brown, 1 white, 2 black (Hammond B3 order)

@Composable
private fun Panel(x: Number, y: Number, w: Number, h: Number) {
    val sh = RoundedCornerShape(10.su())
    Box(Modifier.at(x, y, w, h).clip(sh).background(Brush.verticalGradient(listOf(Color(0xFF17171B), Color(0xFF0B0B0E)))).border(1.su(), Color.Black, sh)
        .drawBehind { drawLine(Color(0x14FFFFFF), Offset(8f, 1f), Offset(size.width - 8f, 1f), 2f) })
}

private fun reg(c: AeCtl, off: Int) = (0..8).joinToString("") { (if (it == 2 || it == 6) " " else "") + (c.v(off + it) * 8).roundToInt() }

/** Tonewheel organ, laid out exactly like the validated preview (stage 1000 x 460). */
@Composable
fun BoxScope.AeOrganUi(c: AeCtl, a: AeAnim) {
    Box(Modifier.fillMaxSize().aeWood())
    Box(Modifier.at(0, 0, 46, 460).background(Brush.horizontalGradient(listOf(Color(0xFF4A2815), Color(0xFF6A3A20)))))
    Box(Modifier.at(954, 0, 46, 460).background(Brush.horizontalGradient(listOf(Color(0xFF6A3A20), Color(0xFF4A2815)))))
    AeT("ÆTHER · TONEWHEEL", 13, AeC.gold, Modifier.at(64, 15, 300, 18), AeFonts.plate, FontWeight.ExtraBold, 0.2f)

    // two manuals
    listOf(Triple(54, 0, "UPPER MANUAL"), Triple(606, 9, "LOWER MANUAL")).forEach { (x, off, title) ->
        Panel(x, 54, 340, 238)
        AeT(title, 9, AeC.goldDim, Modifier.at(x + 14, 62, 170, 12), AeFonts.plate, spacing = 0.14f)
        AeT(reg(c, off), 13, AeC.reg, Modifier.at(x + 196, 58, 130, 18), AeFonts.mono, FontWeight.Normal, 0.08f, TextAlign.End)
        for (i in 0 until 9) AeDrawbar(c, off + i, CAPS[i], FOOT[i], x + 17 + i * 34, 82)
        for (k in 0..8) AeT("$k", 8, Color(0xFFA99E80), Modifier.at(x + 4, 82 + 23 + k * 17, 10, 10), AeFonts.mono, FontWeight.Normal, align = TextAlign.End)
    }

    // centre: vibrato / chorus dial, vibrato switches, percussion
    Panel(402, 54, 196, 238)
    AeT("VIBRATO · CHORUS", 8, AeC.goldDim, Modifier.at(402, 62, 196, 12), AeFonts.plate, spacing = 0.14f, align = TextAlign.Center)
    val step = (c.v(22) * 5).roundToInt()
    // Drag, not click-to-advance: a six-position cycle needed up to five taps to cross the
    // dial, which is what read as "you have to force it". A vertical drag moves it like the
    // other knobs; the touch target keeps the 96-unit box the labels sit in.
    val vibScale = LocalAeScale.current
    val vibClick = Modifier.at(452, 78, 96, 96).pointerInput(Unit) {
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
            AeT(t, 8, AeC.gold, Modifier.at(48 + cos(an).toFloat() * 42f - 12f, 50 + sin(an).toFloat() * 42f - 8f, 24, 11), AeFonts.plate, FontWeight.ExtraBold, align = TextAlign.Center)
        }
        Box(Modifier.at(21, 29, 54, 54).aeSvg(AeSvgs.KNOB_METAL))
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / 96f
            rotate(-75f + 30f * step, Offset(48f * k, 56f * k)) { drawRoundRect(Color(0xFF16161A), Offset(46.6f * k, 39f * k), Size(2.8f * k, 12f * k), CornerRadius(1.4f * k)) }
        }
    }
    AeRocker(c, 23, "UPPER", 418, 182, 76); AeRocker(c, 24, "LOWER", 506, 182, 76)
    AeT("PERCUSSION", 8, AeC.goldDim, Modifier.at(402, 218, 196, 12), AeFonts.plate, spacing = 0.14f, align = TextAlign.Center)
    AeRocker(c, 18, "ON", 412, 232, 40); AeRocker(c, 19, "SOFT", 456, 232, 40); AeRocker(c, 20, "FAST", 500, 232, 40); AeRocker(c, 21, "3RD", 544, 232, 40)

    // tonewheel window + rotary speaker + Leslie lever
    Panel(54, 298, 312, 40)
    Canvas(Modifier.at(60, 301, 300, 34).clip(RoundedCornerShape(10.su()))) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0A0C10), Color(0xFF14171D))))
        a.frame.intValue; drawTonewheels(a)
    }
    AeT("ROTARY", 8, AeC.goldDim, Modifier.at(372, 304, 60, 10), AeFonts.plate, spacing = 0.14f, align = TextAlign.Center)
    Canvas(Modifier.at(374, 314, 58, 34)) { a.frame.intValue; drawRotor(a) }
    val les = (c.v(25) * 2).roundToInt()
    val lx by animateFloatAsState(les * 50f, label = "leslie")
    Box(Modifier.at(442, 301, 150, 34).clickable(remember { MutableInteractionSource() }, null) { c.set(25, ((les + 1) % 3) / 2f) }) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 150f
            drawRoundRect(Color(0xFF0A0A0D), Offset.Zero, size, CornerRadius(8f * u)); drawRoundRect(Color(0x22FFFFFF), Offset.Zero, size, CornerRadius(8f * u), style = Stroke(u))
            drawRoundRect(Color.Black, Offset(14f * u, 14f * u), Size(122f * u, 3f * u), CornerRadius(1.5f * u))
            for (i in 0..2) {
                if (i == les) drawCircle(Color(0x55FFA010), 6f * u, Offset((25f + i * 50f) * u, 8f * u))
                drawCircle(if (i == les) Color(0xFFFFB020) else Color(0xFF2A1A08), 3f * u, Offset((25f + i * 50f) * u, 8f * u))
            }
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF6C7078), Color(0xFFF0F2F6), Color(0xFF4C5058)), (19f + lx) * u, (31f + lx) * u), Offset((19f + lx) * u, 9f * u), Size(12f * u, 13f * u), CornerRadius(4f * u))
        }
        arrayOf("STOP", "SLOW", "FAST").forEachIndexed { i, t -> AeT(t, 7, AeC.goldDim, Modifier.at(25 + i * 50 - 20, 23, 40, 10), AeFonts.plate, FontWeight.ExtraBold, align = TextAlign.Center) }
    }
    // Pulled down clear of the lower-manual panel, whose bottom edge is at y=292: the caps used
    // to start at 288, so the top 4 units of each cap sat inside the panel. At y=298 the cap runs
    // 298..342 and the engraved label ends near 355, leaving a 6-unit gap above and 5 below
    // (the keyboard starts at 360). The 44-unit size the user asked for is kept.
    AeMetalKnob(c, 26, "CLICK", 640, 298, 44); AeMetalKnob(c, 28, "DRIVE", 716, 298, 44); AeMetalKnob(c, 27, "TONE", 792, 298, 44)
    AeMetalKnob(c, 29, "SPACE", 868, 298, 44); AeMetalKnob(c, 30, "LEVEL", 944, 298, 44)
    AeKeyboard(c, 52, 360, 896, 90)
}
