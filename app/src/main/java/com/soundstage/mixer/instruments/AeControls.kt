package com.soundstage.mixer.instruments

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt

/** Binds the controls to one session. All edits go through the manager (state + native engine). */
class AeCtl(val s: AeSession, val m: AeManager) {
    fun v(i: Int): Float = s.values[i]
    fun set(i: Int, x: Float) = m.setParam(s.strip, i, x.coerceIn(0f, 1f))
    fun fmt(i: Int) = AeFmt.format(s.instrument, i, s.values[i])
}

@Composable
private fun Modifier.noRipple(onClick: () -> Unit): Modifier = clickable(remember { MutableInteractionSource() }, null, onClick = onClick)

/** Synth knob: glowing value ring + glass cap. Vertical drag, 130 stage units = full range (same as the preview). */
@Composable
fun AeGlassKnob(c: AeCtl, idx: Int, label: String, x: Number, y: Number, size: Number) {
    val p = c.pal
    val sc = LocalAeScale.current
    val sz = size.toFloat()
    Column(
        Modifier.at(x.toFloat() - 11f, y, sz + 22f, sz + 28f).pointerInput(idx) {
            detectDragGestures { ch, d -> ch.consume(); c.set(idx, c.v(idx) - d.y / (130f * sc)) }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(size.su())) {
            Canvas(Modifier.fillMaxSize()) {
                val v = c.v(idx); val k = this.size.width / 60f; val r = 26f * k; val st = 3.2f * k
                val tl = Offset(30f * k - r, 30f * k - r); val sz2 = Size(2 * r, 2 * r)
                drawArc(Color(0x1CFFFFFF), 135f, 270f, false, tl, sz2, style = Stroke(st, cap = StrokeCap.Round))
                if (v > 0.001f) {
                    drawArc(p.glow, 135f, 270f * v, false, tl, sz2, style = Stroke(st * 2.6f, cap = StrokeCap.Round))
                    drawArc(Brush.linearGradient(listOf(p.ring0, p.ring1)), 135f, 270f * v, false, tl, sz2, style = Stroke(st, cap = StrokeCap.Round))
                }
            }
            Box(Modifier.fillMaxSize().aeSvg(p.knob))
            Canvas(Modifier.fillMaxSize()) {
                val k = this.size.width / 60f
                rotate(-135f + 270f * c.v(idx), Offset(30f * k, 30f * k)) {
                    drawCircle(Color(0x66FFFFFF), 4f * k, Offset(30f * k, 17.5f * k))
                    drawCircle(Color.White, 2.1f * k, Offset(30f * k, 17.5f * k))
                }
            }
        }
        AeT(label.uppercase(), 9, p.label, spacing = 0.12f, align = TextAlign.Center)
        AeT(c.fmt(idx), 10, AeC.tx, font = AeFonts.mono, weight = FontWeight.Normal, align = TextAlign.Center)
    }
}

/** Organ knob: knurled metal cap with a pointer, engraved label (no value text, as in the preview). */
@Composable
fun AeMetalKnob(c: AeCtl, idx: Int, label: String, x: Number, y: Number, size: Number) {
    val sc = LocalAeScale.current
    val sz = size.toFloat()
    Column(
        Modifier.at(x.toFloat() - 8f, y, sz + 16f, sz + 14f).pointerInput(idx) {
            detectDragGestures { ch, d -> ch.consume(); c.set(idx, c.v(idx) - d.y / (130f * sc)) }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(size.su()).aeSvg(AeSvgs.KNOB_METAL)) {
            Canvas(Modifier.fillMaxSize()) {
                val k = this.size.width / 60f
                rotate(-135f + 270f * c.v(idx), Offset(30f * k, 30f * k)) {
                    drawRoundRect(Color(0xFF16161A), Offset(28.6f * k, 12f * k), Size(2.8f * k, 11f * k), CornerRadius(1.4f * k))
                }
            }
        }
        AeT(label, 7.5f, AeC.goldDim, font = AeFonts.plate, spacing = 0.1f, align = TextAlign.Center, modifier = Modifier.padding(top = 2.su()))
    }
}

/** Synth chip group member: [i] of [n] sets the value i / (n - 1). */
@Composable
fun AeChip(c: AeCtl, idx: Int, i: Int, n: Int, label: String, x: Number, y: Number, w: Number) {
    val p = c.pal
    val on = (c.v(idx) * (n - 1)).roundToInt() == i
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.at(x, y, w, 21)
            .clip(shape)
            .background(if (on) Brush.horizontalGradient(listOf(p.chipOn0, p.chipOn1)) else Brush.horizontalGradient(listOf(Color(0x10FFFFFF), Color(0x10FFFFFF))))
            .border(1.su(), if (on) Color.Transparent else Color(0x1CFFFFFF), shape)
            .noRipple { c.set(idx, i.toFloat() / (n - 1)) },
        contentAlignment = Alignment.Center,
    ) { AeT(label, 9, if (on) p.chipText else AeC.tx, spacing = 0.08f) }
}

/** Hammond drawbar: SVG slot + cap, chrome shaft drawn live, snap spring, drag anywhere on the bar. */
@Composable
fun AeDrawbar(c: AeCtl, idx: Int, color: Int, foot: String, x: Number, y: Number) {
    val sc = LocalAeScale.current
    val n = (c.v(idx) * 8).roundToInt()
    val capY by animateFloatAsState(12f + n * 17f, spring(0.7f, Spring.StiffnessMedium), label = "drawbar")
    fun fromY(py: Float) = (((py / sc) - 12f - 16f) / 17f).roundToInt().coerceIn(0, 8) / 8f
    Box(
        Modifier.at(x, y, 34, 200).pointerInput(idx) {
            awaitEachGesture {
                val d = awaitFirstDown(requireUnconsumed = false)
                c.set(idx, fromY(d.position.y)); d.consume()
                drag(d.id) { c.set(idx, fromY(it.position.y)); it.consume() }
            }
        },
    ) {
        Box(Modifier.fillMaxSize().aeSvg(AeSvgs.DB_SLOT))
        AeT(foot, 8, Color(0xFFD6C79C), Modifier.at(0, 0.5f, 34, 11), AeFonts.plate, align = TextAlign.Center)
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / 34f
            drawRect(
                Brush.horizontalGradient(listOf(Color(0xFF6C7078), Color(0xFFF0F2F6), Color(0xFF9AA0AA), Color(0xFF4C5058)), 14.2f * k, 19.8f * k),
                Offset(14.2f * k, 12f * k), Size(5.6f * k, (capY + 4f - 12f) * k),
            )
        }
        Box(Modifier.at(2.5f, capY, 29, 31).aeSvg(when (color) { 0 -> AeSvgs.DB_CAP_BROWN; 1 -> AeSvgs.DB_CAP_WHITE; else -> AeSvgs.DB_CAP_BLACK }))
        AeT("$n", 13, AeC.reg, Modifier.at(0, 180, 34, 16), AeFonts.mono, FontWeight.Normal, align = TextAlign.Center)
    }
}

/** Organ on/off tab (percussion, vibrato upper/lower): lit bar, pressed face. */
@Composable
fun AeRocker(c: AeCtl, idx: Int, label: String, x: Number, y: Number, w: Number) {
    val on = c.v(idx) > 0.5f
    Box(Modifier.at(x, y, w, 30).noRipple { c.set(idx, if (on) 0f else 1f) }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / w.toFloat(); val ww = w.toFloat()
            drawRoundRect(Color(0xFF050507), Offset(u, u), Size(size.width - 2 * u, 28f * u), CornerRadius(6f * u))
            val dy = if (on) 1.2f * u else 0f
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color(0xFF4A4A54), Color(0xFF1C1C22), Color(0xFF050507)), 3f * u, (ww - 3f) * u),
                Offset(3f * u, 3f * u + dy), Size((ww - 6f) * u, 24f * u), CornerRadius(5f * u),
            )
            val lx = ww / 2f - 8f
            if (on) drawRoundRect(Color(0x55FFB020), Offset((lx - 3f) * u, 20f * u), Size(22f * u, 7f * u), CornerRadius(3f * u))
            drawRoundRect(if (on) Color(0xFFFFC24A) else Color(0xFF3A2C10), Offset(lx * u, 22f * u), Size(16f * u, 2.4f * u), CornerRadius(1.2f * u))
        }
        AeT(label, 8, AeC.gold, Modifier.padding(bottom = 6.su()), AeFonts.plate)
    }
}

/**
 * Multi-touch keyboard C3..C5 (MIDI 48..72), glissando between keys, velocity from the touch height.
 * Notes go through the manager (engine + MIDI-style state), so UI and hardware MIDI share the same path.
 */
@Composable
fun AeKeyboard(c: AeCtl, x: Number, y: Number, w: Number, h: Number) {
    val wn = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    val blackNotes = intArrayOf(1, 3, 6, 8, 10); val blackWhite = intArrayOf(0, 1, 3, 4, 5)
    val ww = w.toFloat() / 15f; val bw = w.toFloat() * 0.046f; val bh = h.toFloat() * 0.6f
    fun blackLeft(j: Int) = ((j / 5) * 7 + blackWhite[j % 5] + 1) * ww - w.toFloat() * 0.023f
    fun hit(px: Float, py: Float, sc: Float): Int {
        val ux = px / sc; val uy = py / sc
        if (uy < bh) for (j in 0 until 10) { val l = blackLeft(j); if (ux >= l && ux <= l + bw) return 48 + (j / 5) * 12 + blackNotes[j % 5] }
        val i = (ux / ww).toInt().coerceIn(0, 14)
        return if (i == 14) 72 else 48 + (i / 7) * 12 + wn[i % 7]
    }
    val sc = LocalAeScale.current
    Canvas(
        Modifier.at(x, y, w, h).clip(RoundedCornerShape(10.su())).pointerInput(c) {
            awaitPointerEventScope {
                val down = HashMap<PointerId, Int>()
                while (true) {
                    val ev = awaitPointerEvent()
                    ev.changes.forEach { ch ->
                        val note = hit(ch.position.x, ch.position.y, sc)
                        val vel = (50 + 77 * (ch.position.y / sc / h.toFloat()).coerceIn(0f, 1f)).toInt()
                        if (ch.pressed) {
                            val prev = down[ch.id]
                            if (prev == null) { down[ch.id] = note; c.m.noteOn(c.s.strip, note, vel) }
                            else if (prev != note) { c.m.noteOff(c.s.strip, prev); down[ch.id] = note; c.m.noteOn(c.s.strip, note, vel) }
                            ch.consume()
                        } else down.remove(ch.id)?.let { c.m.noteOff(c.s.strip, it); ch.consume() }
                    }
                }
            }
        },
    ) {
        val u = size.width / w.toFloat()
        drawRect(Color.Black)
        for (i in 0 until 15) {
            val note = if (i == 14) 72 else 48 + (i / 7) * 12 + wn[i % 7]
            val on = c.s.held.containsKey(note)
            drawRect(
                Brush.verticalGradient(if (on) listOf(Color(0xFFF2D99A), Color(0xFFD9B866)) else listOf(Color(0xFFFBF7EE), Color(0xFFD9D0BE)), 0f, size.height),
                Offset(i * ww * u, 0f), Size(ww * u, size.height),
            )
            drawLine(Color(0x40000000), Offset((i + 1) * ww * u, 0f), Offset((i + 1) * ww * u, size.height), u)
        }
        for (j in 0 until 10) {
            val note = 48 + (j / 5) * 12 + blackNotes[j % 5]; val on = c.s.held.containsKey(note)
            drawRoundRect(
                Brush.verticalGradient(if (on) listOf(Color(0xFF7A6A34), Color(0xFF2A2210)) else listOf(Color(0xFF2C2C36), Color(0xFF0B0B0F)), 0f, bh * u),
                Offset(blackLeft(j) * u, 0f), Size(bw * u, bh * u), CornerRadius(5f * u),
            )
            drawLine(Color(0x14FFFFFF), Offset(blackLeft(j) * u + 2f * u, bh * u - 3f * u), Offset((blackLeft(j) + bw) * u - 2f * u, bh * u - 3f * u), 2f * u)
        }
    }
}

/** Colour language per instrument: synth violet, leads cyan LED, sax warm amber. */
class AePal(val ring0: Color, val ring1: Color, val glow: Color, val label: Color, val chipOn0: Color, val chipOn1: Color, val chipText: Color, val knob: String)
object AePals {
    val SYNTH = AePal(Color(0xFF5A3CFF), Color(0xFFE05CFF), Color(0x55C06AFF), AeC.knobLabel, AeC.p1, AeC.p2, Color.White, AeSvgs.KNOB_GLASS)
    val LEADS = AePal(Color(0xFF3A7BFF), Color(0xFF19E3FF), Color(0x5519E3FF), Color(0xFF79AEBD), Color(0xFF19E3FF), Color(0xFF3A7BFF), Color(0xFF02101A), AeSvgs.KNOB_GLASS_CYAN)
    val SAX = AePal(Color(0xFFC8702A), Color(0xFFF2B84A), Color(0x55F2B84A), Color(0xFFC9A56A), Color(0xFFF2B84A), Color(0xFFC8702A), Color(0xFF1A0D04), AeSvgs.KNOB_GLASS_AMBER)
}
val AeCtl.pal: AePal get() = when (s.instrument) { AeInstrument.LEADS -> AePals.LEADS; AeInstrument.SAX -> AePals.SAX; else -> AePals.SYNTH }
