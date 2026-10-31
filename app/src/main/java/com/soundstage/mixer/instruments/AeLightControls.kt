package com.soundstage.mixer.instruments

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Light "scenic" look shared by Piano and Strings. Colours glide when the model changes (dark or light scene). */
class AeTheme(val ink: Color, val ink2: Color, val glass: Color, val edge: Color, val acc: Color)

@Composable
fun rememberAeTheme(dark: Boolean, acc: Color): AeTheme {
    val sp = tween<Color>(900)
    val ink by animateColorAsState(if (dark) Color(0xFFF3EEE6) else Color(0xFF2B2420), sp, label = "ink")
    val ink2 by animateColorAsState(if (dark) Color(0xFFB9B0A4) else Color(0xFF6B5F55), sp, label = "ink2")
    val glass by animateColorAsState(if (dark) Color(0x570C0E1C) else Color(0x42FFFFFF), sp, label = "glass")
    val edge by animateColorAsState(if (dark) Color(0x38FFFFFF) else Color(0x80FFFFFF), sp, label = "edge")
    val a by animateColorAsState(acc, sp, label = "acc")
    return AeTheme(ink, ink2, glass, edge, a)
}

/**
 * Theme derived from a measured luminance rather than a hand-written flag.
 *
 * Above 0.5 the backdrop is light, so the ink goes dark; below, it stays light. With no
 * measurement at all we keep the light ink, which is the safe choice on the dark shell.
 */
@Composable
fun rememberAeThemeFromLuma(topLuma: Float?, acc: Color): AeTheme =
    rememberAeTheme((topLuma ?: 0f) < 0.5f, acc)

@Composable
fun AeGlassCard(th: AeTheme, x: Number, y: Number, w: Number, h: Number) {
    val sh = RoundedCornerShape(20.su())
    Box(Modifier.at(x, y, w, h).clip(sh).background(th.glass).border(1.su(), th.edge, sh))
}

@Composable
fun AeLightKnob(th: AeTheme, value: Float, label: String, text: String, x: Number, y: Number, onChange: (Float) -> Unit) {
    val sc = LocalAeScale.current
    val v by rememberUpdatedState(value)
    val cb by rememberUpdatedState(onChange)
    Column(Modifier.at(x, y, 64, 72).pointerInput(Unit) { detectDragGestures { ch, d -> ch.consume(); cb((v - d.y / (130f * sc)).coerceIn(0f, 1f)) } }, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(40.su())) {
            val k = size.width / 60f; val r = 26f * k; val st = 3f * k
            val tl = Offset(30f * k - r, 30f * k - r); val sz = Size(2 * r, 2 * r); val c = Offset(30f * k, 30f * k)
            drawArc(th.ink.copy(alpha = 0.22f), 135f, 270f, false, tl, sz, style = Stroke(st, cap = StrokeCap.Round))
            if (value > 0.001f) drawArc(th.acc, 135f, 270f * value, false, tl, sz, style = Stroke(st, cap = StrokeCap.Round))
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFECE6DC), Color(0xFFC9C0B3)), Offset(c.x - 7f * k, c.y - 9f * k), 28f * k), 19f * k, c)
            drawCircle(Color(0x2E000000), 19f * k, c, style = Stroke(0.8f * k))
            rotate(-135f + 270f * value, c) { drawRoundRect(Color(0xFF3A3028), Offset(28.8f * k, 14f * k), Size(2.4f * k, 9f * k), CornerRadius(1.2f * k)) }
        }
        AeT(label.uppercase(), 9, th.ink2, weight = FontWeight.Normal, spacing = 0.18f, align = TextAlign.Center)
        AeT(text, 10, th.ink, font = AeFonts.mono, weight = FontWeight.Normal, align = TextAlign.Center)
    }
}

@Composable
fun AeLightSlider(th: AeTheme, value: Float, label: String, x: Number, y: Number, onChange: (Float) -> Unit) {
    val cb by rememberUpdatedState(onChange)
    Box(Modifier.at(x, y, 236, 22)) {
        AeT(label.uppercase(), 9, th.ink2, Modifier.at(0, 6, 76, 12), weight = FontWeight.Normal, spacing = 0.16f)
        Canvas(Modifier.at(76, 0, 150, 22).pointerInput(Unit) {
            awaitEachGesture {
                val d = awaitFirstDown(requireUnconsumed = false)
                cb((d.position.x / size.width).coerceIn(0f, 1f)); d.consume()
                drag(d.id) { cb((it.position.x / size.width).coerceIn(0f, 1f)); it.consume() }
            }
        }) {
            val u = size.width / 150f; val cy = size.height / 2f; val c = Offset(size.width * value, cy)
            drawLine(th.ink2, Offset(0f, cy), Offset(size.width, cy), 2f * u)
            drawLine(th.acc, Offset(0f, cy), c, 2f * u)
            drawCircle(Color(0x4D000000), 8f * u, c + Offset(0f, 2f * u))
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFD9D1C5)), c - Offset(2f * u, 2f * u), 9f * u), 7f * u, c)
        }
    }
}

@Composable
fun AeSmallBtn(th: AeTheme, label: String, x: Number, y: Number, w: Number, h: Number, on: Boolean = false, onClick: () -> Unit) {
    val sh = RoundedCornerShape(50)
    Box(Modifier.at(x, y, w, h).clip(sh).background(if (on) th.acc else th.glass).border(1.su(), th.edge, sh).clickable(remember { MutableInteractionSource() }, null) { onClick() }, contentAlignment = Alignment.Center) {
        AeT(label, 11, th.ink, weight = FontWeight.Normal)
    }
}

@Composable
fun AeModelList(th: AeTheme, names: List<String>, selected: Int, x: Number, y: Number, onPick: (Int) -> Unit) {
    names.forEachIndexed { i, n ->
        val on = i == selected
        Box(Modifier.at(x, y.toFloat() + i * 30f, 176, 30).clip(RoundedCornerShape(10.su()))
            .then(if (on) Modifier.background(Brush.horizontalGradient(listOf(th.glass, Color.Transparent))) else Modifier)
            .clickable(remember { MutableInteractionSource() }, null) { onPick(i) }) {
            if (on) Box(Modifier.at(0, 6, 3, 18).background(th.acc))
            AeT(n.uppercase(), 14, th.ink.copy(alpha = if (on) 1f else 0.72f), Modifier.at(14, 6, 160, 18), AeFonts.display, if (on) FontWeight.Medium else FontWeight.Light, 0.2f)
        }
    }
}

/** 3-octave glass keyboard, multi-touch with glissando. Notes go through the manager like hardware MIDI. */
@Composable
fun AeGlassKeyboard(c: AeCtl, th: AeTheme, base: Int, x: Number, y: Number, w: Number, h: Number) {
    val sc = LocalAeScale.current
    val wn = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    val whites = (0..36).filter { wn.contains(it % 12) }
    val ww = w.toFloat() / whites.size; val bh = h.toFloat() * 0.6f
    fun blackLeft(s: Int): Float { val wi = whites.indexOfFirst { it > s } - 1; return (wi + 1) * ww - ww * 0.3f }
    fun noteAt(px: Float, py: Float): Int {
        val ux = px / sc; val uy = py / sc
        if (uy < bh) for (s in 0 until 36) if (!wn.contains(s % 12)) { val l = blackLeft(s); if (ux >= l && ux <= l + ww * 0.6f) return base + s }
        return base + whites[(ux / ww).toInt().coerceIn(0, whites.size - 1)]
    }
    Canvas(Modifier.at(x, y, w, h).clip(RoundedCornerShape(12.su())).pointerInput(c, base) {
        awaitPointerEventScope {
            val down = HashMap<PointerId, Int>()
            while (true) {
                val ev = awaitPointerEvent()
                ev.changes.forEach { ch ->
                    val n = noteAt(ch.position.x, ch.position.y)
                    val vel = (50 + 77 * (ch.position.y / sc / h.toFloat()).coerceIn(0f, 1f)).toInt()
                    if (ch.pressed) {
                        val prev = down[ch.id]
                        if (prev == null) { down[ch.id] = n; c.m.noteOn(c.s.strip, n, vel) }
                        else if (prev != n) { c.m.noteOff(c.s.strip, prev); down[ch.id] = n; c.m.noteOn(c.s.strip, n, vel) }
                        ch.consume()
                    } else down.remove(ch.id)?.let { c.m.noteOff(c.s.strip, it); ch.consume() }
                }
            }
        }
    }) {
        val u = size.width / w.toFloat()
        drawRoundRect(th.glass, Offset.Zero, size, CornerRadius(12f * u)); drawRoundRect(th.edge, Offset.Zero, size, CornerRadius(12f * u), style = Stroke(u))
        whites.forEachIndexed { i, s ->
            val on = c.s.held.containsKey(base + s)
            drawRect(Brush.verticalGradient(if (on) listOf(th.acc, Color(0x80FFFFFF)) else listOf(Color(0x9EFFFFFF), Color(0x57FFFFFF)), 0f, size.height), Offset(i * ww * u, 0f), Size(ww * u, size.height))
            drawLine(Color(0x1F000000), Offset((i + 1) * ww * u, 0f), Offset((i + 1) * ww * u, size.height), u)
        }
        for (s in 0 until 36) if (!wn.contains(s % 12)) {
            val on = c.s.held.containsKey(base + s)
            drawRoundRect(Brush.verticalGradient(if (on) listOf(th.acc, Color(0xE60E0C0C)) else listOf(Color(0xE6221C18), Color(0xDB0E0C0C)), 0f, bh * u), Offset(blackLeft(s) * u, 0f), Size(ww * 0.6f * u, bh * u), CornerRadius(4f * u))
        }
    }
}

/** Full-screen mode: a dialog without system bars (swipe from an edge to peek). [content] receives `true` when full screen. */
@Composable
fun AeFullscreenHost(fullscreen: Boolean, onExit: () -> Unit, content: @Composable (Boolean) -> Unit) {
    if (!fullscreen) { content(false); return }
    Dialog(onDismissRequest = onExit, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        SideEffect {
            val win = (view.parent as? DialogWindowProvider)?.window
            if (win != null) {
                win.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                WindowCompat.setDecorFitsSystemWindows(win, false)
                WindowInsetsControllerCompat(win, view).apply { hide(WindowInsetsCompat.Type.systemBars()); systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) { content(true) }
    }
}
