package com.soundstage.mixer.looper // ← adapt to the real package

/*
 * LiveLooper UI — faithful to LoopsPreview.jsx (approved). Stateless: it renders LooperUiState and
 * reports through LooperActions. Pure logic and the controller live in LiveLooperModel.kt.
 *
 * What goes where in the DrumPad page:
 *   top bar      : LooperPageSelector (replaces the "DrumPad" pill) and, on the LiveLooper page, LoopPatchChip
 *   left column  : on the LiveLooper page, LoopsSummaryCard replaces the "Tonic FX" card
 *   right area   : LiveLooperContent (transport + 8 cases, or the focused case)
 *   page root    : LiveLooperOverlays (patch window, dialogs, toast), placed ABOVE the top bar, filling the page
 *
 * Surfaces: every shadowed surface has an OPAQUE base under its translucent fill (no grey boxes).
 * Replace the colors of the private LL object with the app palette once the palette module exists.
 */

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/* ═══════════════════════════ STYLE ═══════════════════════════ */

private object LL {
    val Amber = Color(0xFFD8A24A)
    val Rose = Color(0xFFC2587A)
    val Accent = Color(0xFF8FB2E8)
    val Sage = Color(0xFF86A394)
    val Ink = Color(0xFFE9EDF5)
    val Soft = Color(0xFFC9D2E2)
    val Mute = Color(0xFF8E99AE)
    val Dim = Color(0xFF6F7A90)
    val Faint = Color(0xFF566075)
    val Pal = listOf(0xFF7F9BC7, 0xFF9A86C2, 0xFFC27A8E, 0xFFC9A15C, 0xFF86A394, 0xFF6FB3A8, 0xFFB58E6B, 0xFF8D97B8).map { Color(it) }
    val CardBase = Color(0xFF171E2C)
    val CardEnd = Color(0xFF161C2B)
    val Mono = FontFamily.Monospace
}

private fun white(a: Float) = Color.White.copy(alpha = a)
private fun Color.a(x: Float) = copy(alpha = x)
// Bounded both ways: Kotlin's `%` keeps the sign of the dividend, so a negative case id
// (a legacy or hand-edited patches.json can carry any integer) indexed Pal[-1] and crashed
// the whole LiveLooper page with an IndexOutOfBoundsException. Same defensive form as
// DpPalette.at().
fun loopCaseColor(id: Int): Color = LL.Pal[((id % LL.Pal.size) + LL.Pal.size) % LL.Pal.size]

// Patch window of the reference: `left:120, right:120, top:58, bottom:12` on the
// 891 x 411 frame -> 651 x 341 dp. Unlike the grid this is a floating window, so it
// keeps its designed size and only ever shrinks (see `PatchWindow`).
private const val LL_PATCH_W = 651f
private const val LL_PATCH_H = 341f

private val PanelBrush = Brush.verticalGradient(listOf(Color(0xFF2A3447), Color(0xFF1E2736)))
private val WindowBrush = Brush.verticalGradient(listOf(Color(0xFF232C3E), Color(0xFF1A2230)))
private val ButtonBrush = Brush.verticalGradient(listOf(Color(0xFF2F3850), Color(0xFF252D40)))
private val RowBrush = Brush.verticalGradient(listOf(white(0.065f), white(0.03f)))

/* ═══════════════════════════ ICONS ═══════════════════════════ */

enum class LoopIcon { Rec, Play, Stop, Undo, Redo, More, X, Back, Down, Left, Right, Check, Layers, Speaker, Mute }

@Composable
private fun LoopIconView(icon: LoopIcon, tint: Color, size: Dp = 14.dp, strokeWidth: Float = 2.1f) {
    Canvas(Modifier.size(size)) {
        val sc = this.size.width / 24f
        withTransform({ scale(sc, sc, Offset.Zero) }) {
            val st = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x1, y1), Offset(x2, y2), strokeWidth, StrokeCap.Round)
            fun poly(vararg p: Float) { for (i in 0 until p.size / 2 - 1) line(p[i * 2], p[i * 2 + 1], p[i * 2 + 2], p[i * 2 + 3]) }
            when (icon) {
                LoopIcon.Rec -> drawCircle(tint, 6f, Offset(12f, 12f))
                LoopIcon.Play -> drawPath(Path().apply { moveTo(8f, 5.5f); lineTo(18f, 12f); lineTo(8f, 18.5f); close() }, tint)
                LoopIcon.Stop -> drawRoundRect(tint, Offset(7f, 7f), Size(10f, 10f), CornerRadius(1.6f))
                LoopIcon.Undo -> {
                    poly(9f, 14f, 4.5f, 9.5f, 9f, 5f)
                    drawPath(Path().apply { moveTo(4.5f, 9.5f); lineTo(14f, 9.5f); arcTo(Rect(8.5f, 9.5f, 19.5f, 20.5f), -90f, 180f, false); lineTo(11f, 20.5f) }, tint, style = st)
                }
                LoopIcon.Redo -> {
                    poly(15f, 14f, 19.5f, 9.5f, 15f, 5f)
                    drawPath(Path().apply { moveTo(19.5f, 9.5f); lineTo(10f, 9.5f); arcTo(Rect(4.5f, 9.5f, 15.5f, 20.5f), -90f, -180f, false); lineTo(13f, 20.5f) }, tint, style = st)
                }
                LoopIcon.More -> { drawCircle(tint, 1.6f, Offset(6f, 12f)); drawCircle(tint, 1.6f, Offset(12f, 12f)); drawCircle(tint, 1.6f, Offset(18f, 12f)) }
                LoopIcon.X -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
                LoopIcon.Back -> { line(19f, 12f, 5f, 12f); poly(11f, 6f, 5f, 12f, 11f, 18f) }
                LoopIcon.Down -> poly(6f, 9f, 12f, 15f, 18f, 9f)
                LoopIcon.Left -> poly(15f, 6f, 9f, 12f, 15f, 18f)
                LoopIcon.Right -> poly(9f, 6f, 15f, 12f, 9f, 18f)
                LoopIcon.Check -> poly(5f, 12.5f, 9.5f, 17f, 19f, 7.5f)
                LoopIcon.Layers -> {
                    drawPath(Path().apply { moveTo(12f, 3f); lineTo(21f, 8f); lineTo(12f, 13f); lineTo(3f, 8f); close() }, tint, style = st)
                    poly(3f, 13f, 12f, 18f, 21f, 13f)
                }
                LoopIcon.Speaker, LoopIcon.Mute -> {
                    drawPath(Path().apply { moveTo(4f, 9.5f); lineTo(7.5f, 9.5f); lineTo(12f, 6f); lineTo(12f, 18f); lineTo(7.5f, 14.5f); lineTo(4f, 14.5f); close() }, tint, style = st)
                    if (icon == LoopIcon.Speaker) drawPath(Path().apply { moveTo(15.5f, 9f); cubicTo(17.6f, 10.3f, 17.6f, 13.7f, 15.5f, 15f) }, tint, style = st)
                    else { line(16f, 9.5f, 20f, 14.5f); line(20f, 9.5f, 16f, 14.5f) }
                }
            }
        }
    }
}

/* ═══════════════════════════ SMALL CONTROLS ═══════════════════════════ */

/** Spring press feedback + tap + optional long press, with haptics. */
@Composable
private fun Modifier.pressable(enabled: Boolean = true, onLongPress: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (pressed) 0.95f else 1f, spring(0.55f, 600f), label = "press")
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongPress)
    val haptic = LocalHapticFeedback.current
    val hasLong = onLongPress != null
    return this.scale(sc).pointerInput(enabled, hasLong) {
        if (!enabled) return@pointerInput
        detectTapGestures(
            onPress = { pressed = true; tryAwaitRelease(); pressed = false },
            onLongPress = if (hasLong) fun(_: Offset) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); long?.invoke() } else null,
            onTap = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); click() }
        )
    }
}

@Composable
private fun <T> Segmented(
    options: List<Pair<T, String>>, value: T, onChange: (T) -> Unit, width: Dp,
    height: Dp = 20.dp, fontSize: TextUnit = 11.sp
) {
    val idx = options.indexOfFirst { it.first == value }.coerceAtLeast(0)
    BoxWithConstraints(Modifier.width(width).clip(RoundedCornerShape(10.dp)).background(Color(0xD90E131C)).padding(2.dp)) {
        val segW = maxWidth / options.size
        val x by animateDpAsState(segW * idx, spring(0.65f, 420f), label = "seg")
        Box(Modifier.offset(x = x).width(segW).height(height).background(Brush.verticalGradient(listOf(Color(0xFF3A4663), Color(0xFF2B3650))), RoundedCornerShape(8.dp)))
        Row {
            options.forEach { (k, label) ->
                Box(
                    Modifier.width(segW).height(height).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(k) },
                    contentAlignment = Alignment.Center
                ) { Text(label, color = if (k == value) Color(0xFFF1F4FA) else Color(0xFF8690A4), fontSize = fontSize, lineHeight = fontSize, fontWeight = FontWeight.ExtraBold, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun Group(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, Modifier.padding(start = 3.dp), color = LL.Dim, fontSize = 8.5.sp, lineHeight = 8.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
        content()
    }
}

@Composable
private fun LoopTextField(value: String, placeholder: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(value) }
    BasicTextField(
        value = text, onValueChange = { if (it.length <= 22) { text = it; onChange(it) } }, singleLine = true,
        textStyle = TextStyle(color = Color(0xFFF1F4FA), fontSize = 13.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold),
        cursorBrush = SolidColor(LL.Accent),
        modifier = modifier.height(30.dp),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color(0xFF0E131C)).border(1.dp, white(0.12f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) { if (text.isEmpty()) Text(placeholder, color = LL.Faint, fontSize = 13.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold); inner() }
        }
    )
}

private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    drawRoundRect(color, cornerRadius = CornerRadius(radius.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))))
}

@Composable
private fun RoundButton(icon: LoopIcon, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(ButtonBrush).border(1.dp, white(0.1f), CircleShape).pressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { LoopIconView(icon, Color(0xFFE3E8F2), 14.dp, 2.2f) }
}

/**
 * Current bar position (bars since the transport origin). Visual only.
 *
 * [active] lets the caller drop the refresh rate when nothing is moving: at 30 Hz while a
 * case records, plays or waits for a boundary, and at ~4 Hz when the page is idle, so an
 * open-but-silent LiveLooper page does not keep the compositor busy 30 times a second.
 */
@Composable
fun rememberNowBar(clock: () -> Double, active: Boolean = true): Double {
    var now by remember { mutableStateOf(clock()) }
    val c by rememberUpdatedState(clock)
    val a by rememberUpdatedState(active)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            val interval = if (a) 30_000_000L else 250_000_000L
            androidx.compose.runtime.withFrameNanos { t -> if (t - last >= interval) { last = t; now = c() } }
        }
    }
    return now
}

/* ═══════════════════════════ STATE → LOOK ═══════════════════════════ */

private enum class BadgeAnim { None, Blink, Pulse, Breathe }
private data class StateInfo(val label: String, val color: Color, val anim: BadgeAnim = BadgeAnim.None, val sub: String = "")

private fun stateInfo(c: LoopCase, nowBar: Double, color: Color): StateInfo {
    if (c.queued != null) {
        if (c.queued == LoopAction.Rec && c.mode == CaseMode.Empty) {
            val n = max(1, ceil(c.queuedAtBar - nowBar).toInt())
            return StateInfo("ARMED", LL.Amber, BadgeAnim.Blink, "in $n bar${if (n > 1) "s" else ""}")
        }
        return StateInfo("QUEUED", LL.Amber, BadgeAnim.Blink, "next bar")
    }
    return when (c.mode) {
        CaseMode.Rec -> StateInfo("REC", LL.Rose, BadgeAnim.Pulse)
        CaseMode.Play -> StateInfo("PLAY", color, if (c.mute) BadgeAnim.None else BadgeAnim.Breathe)
        CaseMode.Overdub -> StateInfo("OVERDUB", LL.Amber, BadgeAnim.Pulse)
        CaseMode.Stopped -> StateInfo("STOPPED", Color(0xFF7B859A))
        CaseMode.Empty -> StateInfo("EMPTY", LL.Faint)
    }
}

private fun posText(c: LoopCase, pos: LoopPosition?, info: StateInfo): String = when {
    c.queued != null -> info.sub
    c.mode == CaseMode.Empty -> if (c.redo.isNotEmpty()) "undone · hold ↶" else "tap ● to arm"
    c.mode == CaseMode.Stopped -> "${c.bars} bar${if (c.bars > 1) "s" else ""}"
    c.mode == CaseMode.Rec -> "bar ${pos?.bar ?: 1}"
    else -> "${pos?.bar ?: 1} / ${c.bars}"
}

@Composable
private fun StateBadge(info: StateInfo, size: TextUnit = 11.sp) {
    val t = rememberInfiniteTransition(label = "badge")
    val low = when (info.anim) { BadgeAnim.Blink -> 0.35f; BadgeAnim.Pulse -> 0.6f; BadgeAnim.Breathe -> 0.82f; else -> 1f }
    val ms = when (info.anim) { BadgeAnim.Blink -> 450; BadgeAnim.Pulse -> 500; BadgeAnim.Breathe -> 1200; else -> 1000 }
    val a by t.animateFloat(1f, low, infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Text(info.label, Modifier.alpha(if (info.anim == BadgeAnim.None) 1f else a), color = info.color, fontSize = size, lineHeight = size, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp, maxLines = 1)
}

/* ═══════════════════════════ RING AND LAYERS ═══════════════════════════ */

@Composable
private fun LoopRing(c: LoopCase, color: Color, size: Dp, info: StateInfo, pos: LoopPosition?) {
    val measurer = rememberTextMeasurer()
    val a by animateFloatAsState(if (c.mute) 0.55f else 1f, tween(300), label = "ringmute")
    Canvas(Modifier.size(size).alpha(a)) {
        val s = this.size.width / 100f
        fun pt(f: Float, r: Float): Offset { val ang = f * 2f * PI.toFloat() - PI.toFloat() / 2f; return Offset((50f + r * cos(ang)) * s, (50f + r * sin(ang)) * s) }
        val center = Offset(50f * s, 50f * s)
        drawCircle(white(0.08f), 44f * s, center, style = Stroke(3f * s))
        if (c.hasLoop) for (i in 0 until c.bars) {
            val cur = pos != null && floor(pos.fraction * c.bars).toInt() == i
            drawLine(if (cur) color else white(0.26f), pt(i / c.bars.toFloat(), 41f), pt(i / c.bars.toFloat(), 47f), 1.8f * s, StrokeCap.Round)
        }
        listOf(32f, 24f, 16f, 8f).forEachIndexed { i, r -> drawCircle(if (i < c.layers) color.a(0.2f) else white(0.035f), r * s, center, style = Stroke(1f * s)) }
        val len = if (c.mode == CaseMode.Rec) 1f else c.bars.toFloat()
        if (len > 0f && (c.hasLoop || c.mode == CaseMode.Rec)) c.events.forEach { e ->
            if (!e.on) return@forEach
            val fr = (((e.posBars % len) + len) % len) / len
            val muted = c.layerMute.getOrElse(e.layer) { false }
            drawCircle(color.a(if (muted) 0.15f else 0.55f + 0.15f * min(e.layer, 3)), 3f * s, pt(fr, 32f - min(e.layer, 3) * 8f))
        }
        if (pos != null) {
            val f = pos.fraction
            val s0 = max(0f, f - 0.12f)
            drawArc(info.color.a(0.5f), s0 * 360f - 90f, (f - s0) * 360f, false, Offset(6f * s, 6f * s), Size(88f * s, 88f * s), style = Stroke(3.4f * s, cap = StrokeCap.Round))
            drawCircle(info.color.a(0.3f), 8f * s, pt(f, 44f))
            drawCircle(info.color, 4.6f * s, pt(f, 44f))
        }
        val centre = when {
            c.mode == CaseMode.Empty && c.queued == null -> "+"
            c.mode == CaseMode.Rec -> "●"
            c.mode == CaseMode.Stopped -> "❚❚"
            c.queued != null -> "…"
            else -> (pos?.bar ?: 1).toString()
        }
        val style = TextStyle(color = if (c.mode == CaseMode.Empty) LL.Faint else Color(0xFFF1F4FA), fontSize = ((if (centre.length > 1) 15f else 22f) * s).toSp(), lineHeight = ((if (centre.length > 1) 15f else 22f) * s).toSp(), fontWeight = FontWeight.ExtraBold, fontFamily = LL.Mono)
        val l = measurer.measure(centre, style)
        drawText(l, topLeft = Offset(center.x - l.size.width / 2f, center.y - l.size.height / 2f))
    }
}

@Composable
private fun LayerBars(c: LoopCase, color: Color, gap: Dp, barHeight: Dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "build")
    val blink by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        for (i in 0 until 4) {
            val on = i < c.layers
            val muted = on && c.layerMute[i]
            val building = (c.mode == CaseMode.Overdub || c.mode == CaseMode.Rec) && i == c.layers
            val shape = RoundedCornerShape(barHeight / 2)
            Box(
                Modifier.fillMaxWidth().height(barHeight)
                    .drawBehind { if (on && !muted) drawRoundRect(color.a(0.22f), Offset(-2.dp.toPx(), -2.dp.toPx()), Size(size.width + 4.dp.toPx(), size.height + 4.dp.toPx()), CornerRadius(size.height)) }
                    .alpha(if (building) blink else 1f)
                    .clip(shape)
                    .background(if (muted) Color.Transparent else if (on) color else if (building) LL.Rose.a(0.6f) else white(0.09f))
                    .then(if (muted) Modifier.border(1.dp, color.a(0.5f), shape) else Modifier)
            )
        }
    }
}

/* ═══════════════════════════ ACTIONS ROW ═══════════════════════════ */

private class ActionSpec(
    val key: String, val icon: LoopIcon, val text: String, val on: Boolean, val tint: Color,
    val enabled: Boolean = true, val onHold: (() -> Unit)? = null, val onClick: () -> Unit
)

@Composable
private fun ActionRow(c: LoopCase, color: Color, height: Dp, labels: Boolean, actions: LooperActions) {
    val running = c.mode == CaseMode.Play || c.mode == CaseMode.Overdub || c.mode == CaseMode.Rec
    val busy = c.mode == CaseMode.Rec || c.mode == CaseMode.Overdub
    val specs = buildList {
        add(ActionSpec("rec", LoopIcon.Rec, if (c.mode == CaseMode.Play || c.mode == CaseMode.Stopped) "Overdub" else "Record", busy, LL.Rose, onClick = { actions.onRec(c.id) }))
        add(ActionSpec("play", if (running) LoopIcon.Stop else LoopIcon.Play, if (running) "Stop" else "Play", c.mode == CaseMode.Play, color, onHold = { actions.onHoldPlay(c.id) }, onClick = { actions.onPlay(c.id) }))
        add(ActionSpec("undo", LoopIcon.Undo, "Undo", false, LL.Accent, enabled = !((c.layers == 0 && c.redo.isEmpty()) || busy), onHold = { actions.onHoldUndo(c.id) }, onClick = { actions.onUndo(c.id) }))
        if (labels) add(ActionSpec("redo", LoopIcon.Redo, "Redo", false, LL.Accent, enabled = c.redo.isNotEmpty() && !busy, onClick = { actions.onRedo(c.id) }))
        add(ActionSpec("more", LoopIcon.More, "More", false, LL.Accent, onClick = { actions.onSheet(c.id) }))
    }
    Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        specs.forEach { b ->
            val shape = RoundedCornerShape(10.dp)
            Row(
                Modifier
                    .weight(if (b.key == "more" && !labels) 0.7f else 1f).fillMaxHeight()
                    .drawBehind { if (b.on) drawRoundRect(b.tint.a(0.3f), Offset(-2.dp.toPx(), -2.dp.toPx()), Size(size.width + 4.dp.toPx(), size.height + 4.dp.toPx()), CornerRadius(12.dp.toPx())) }
                    .alpha(if (b.enabled) 1f else 0.55f)
                    .pressable(enabled = b.enabled, onLongPress = b.onHold, onClick = b.onClick)
                    .clip(shape)
                    .background(if (b.on) b.tint.a(0.2f) else Color.Transparent)
                    .background(if (b.on) SolidColor(Color.Transparent) else ButtonBrush)
                    .border(1.dp, if (b.on) b.tint.a(0.6f) else white(0.06f), shape),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                val tint = if (!b.enabled) Color(0xFF434C61) else if (b.on) b.tint else LL.Soft
                LoopIconView(b.icon, if (b.on && b.key == "rec") b.tint else tint, if (labels) 15.dp else 13.dp)
                if (labels) { Spacer(Modifier.width(6.dp)); Text(b.text, color = tint, fontSize = 12.5.sp, lineHeight = 12.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1) }
            }
        }
    }
}

/* ═══════════════════════════ CASE CARD ═══════════════════════════ */

@Composable
private fun CaseSurface(c: LoopCase, color: Color, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val active = c.mode == CaseMode.Play || c.mode == CaseMode.Rec || c.mode == CaseMode.Overdub
    val start by animateColorAsState(if (active) color.a(if (c.mute) 0.08f else 0.2f) else Color(0xFF222B3E), tween(400), label = "cs")
    val border by animateColorAsState(if (c.mode == CaseMode.Empty) white(0.06f) else color.a(0.4f), tween(400), label = "cb")
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .shadow(6.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(LL.CardBase) // OPAQUE base under the translucent gradient
            .background(Brush.linearGradient(0f to start, 0.62f to LL.CardEnd, 1f to LL.CardEnd, start = Offset.Zero, end = Offset.Infinite))
            .border(1.dp, border, shape)
            .drawBehind {
                drawLine(white(0.07f), Offset(14.dp.toPx(), 0.5f), Offset(size.width - 14.dp.toPx(), 0.5f), 1f)
                drawLine(white(0.07f), Offset(0.5f, 14.dp.toPx()), Offset(0.5f, size.height - 14.dp.toPx()), 1f)
            },
        content = content
    )
}

@Composable
private fun CaseCard(c: LoopCase, nowBar: Double, beatsPerBar: Int, actions: LooperActions, modifier: Modifier) {
    val color = loopCaseColor(c.id)
    val pos = LooperLogic.position(c, nowBar)
    val info = stateInfo(c, nowBar, color)
    CaseSurface(c, color, modifier.padding(0.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("L${c.id + 1}", Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { actions.onFocus(c.id) }, color = LL.Soft, fontSize = 11.5.sp, lineHeight = 11.5.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LL.Mono)
                StateBadge(info)
                if (c.mute && c.layers > 0) Text("MUTE", Modifier.border(1.dp, LL.Amber.a(0.5f), RoundedCornerShape(5.dp)).padding(horizontal = 4.dp), color = LL.Amber, fontSize = 8.5.sp, lineHeight = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
                Spacer(Modifier.weight(1f))
                if (c.hasLoop) Text("${c.bars} bar${if (c.bars > 1) "s" else ""}", color = LL.Mute, fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LL.Mono)
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                // The reference draws the ring at 68 dp inside a 168 dp card (~40% of the
                // width). Keeping that ratio instead of a hard 68 dp is what lets the text
                // column survive on a 640 dp phone, where a card is only ~98 dp wide.
                val ring = if (maxWidth * 0.42f < 68.dp) maxWidth * 0.42f else 68.dp
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    LoopRing(c, color, ring, info, pos)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                        Text(posText(c, pos, info), color = if (c.mode == CaseMode.Empty) LL.Faint else LL.Soft, fontSize = 11.sp, lineHeight = 11.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LL.Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LayerBars(c, color, 4.dp, 6.dp)
                    }
                }
            }
            ActionRow(c, color, 28.dp, false, actions)
        }
    }
}

@Composable
private fun CaseChip(c: LoopCase, nowBar: Double, active: Boolean, onSelect: () -> Unit) {
    val color = loopCaseColor(c.id)
    val info = stateInfo(c, nowBar, color)
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().height(33.dp).clip(shape)
            .background(if (active) SolidColor(color.a(0.18f)) else Brush.verticalGradient(listOf(Color(0xFF222B3E), Color(0xFF1A2132))))
            .border(1.dp, if (active) color.a(0.7f) else white(0.06f), shape)
            .pressable(onClick = onSelect).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("L${c.id + 1}", color = LL.Soft, fontSize = 11.sp, lineHeight = 11.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LL.Mono)
        Box(Modifier.size(7.dp).drawBehind { if (c.mode != CaseMode.Empty) drawCircle(info.color.a(0.35f), 6.dp.toPx()) }.background(info.color, CircleShape))
        Spacer(Modifier.weight(1f))
        Text(if (c.layers > 0) "${c.bars}b·${c.layers}" else "", color = Color(0xFF7B859A), fontSize = 9.5.sp, lineHeight = 9.5.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LL.Mono)
    }
}

@Composable
private fun VolumeSlider(value: Int, color: Color, onChange: (Int) -> Unit, modifier: Modifier) {
    val frac = value / 100f
    BoxWithConstraints(
        modifier.height(18.dp).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val d = awaitFirstDown(requireUnconsumed = false)
                    onChange(((d.position.x / size.width).coerceIn(0f, 1f) * 100).roundToInt()); d.consume()
                    drag(d.id) { ch -> ch.consume(); onChange(((ch.position.x / size.width).coerceIn(0f, 1f) * 100).roundToInt()) }
                }
            }
        }
    ) {
        Box(Modifier.fillMaxWidth().offset(y = 7.dp).height(4.dp).background(Color(0xFF0E131C), RoundedCornerShape(2.dp)))
        Box(Modifier.fillMaxWidth(frac).offset(y = 7.dp).height(4.dp).background(Brush.horizontalGradient(listOf(color.a(0.4f), color)), RoundedCornerShape(2.dp)))
        Box(Modifier.offset(x = maxWidth * frac - 7.dp, y = 2.dp).size(14.dp).background(Brush.verticalGradient(listOf(Color(0xFF6E7799), Color(0xFF3F4766))), RoundedCornerShape(5.dp)).border(1.dp, white(0.16f), RoundedCornerShape(5.dp)))
    }
}

@Composable
private fun SmallSquareButton(icon: LoopIcon, tint: Color, bg: Color, onClick: () -> Unit) {
    Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(bg).pressable(onClick = onClick), contentAlignment = Alignment.Center) { LoopIconView(icon, tint, 12.dp, 2.4f) }
}

@Composable
private fun FocusView(c: LoopCase, state: LooperUiState, nowBar: Double, actions: LooperActions, modifier: Modifier) {
    val color = loopCaseColor(c.id)
    val pos = LooperLogic.position(c, nowBar)
    val info = stateInfo(c, nowBar, color)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CaseSurface(c, color, Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.height(28.dp).clip(RoundedCornerShape(9.dp)).background(white(0.05f)).border(1.dp, white(0.1f), RoundedCornerShape(9.dp))
                            .pressable { actions.onFocus(null) }.padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) { LoopIconView(LoopIcon.Back, LL.Soft, 12.dp); Text("All cases", color = LL.Soft, fontSize = 11.5.sp, lineHeight = 11.5.sp, fontWeight = FontWeight.ExtraBold) }
                    Text("L${c.id + 1}", color = LL.Ink, fontSize = 14.sp, lineHeight = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LL.Mono)
                    if (c.name.isNotEmpty()) Text(c.name, color = LL.Soft, fontSize = 13.sp, lineHeight = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
                    StateBadge(info, 13.sp)
                    Spacer(Modifier.weight(1f))
                    if (c.layers > 0) Text("${c.bars} bar${if (c.bars > 1) "s" else ""} · ${c.layers} layer${if (c.layers > 1) "s" else ""}", color = LL.Mute, fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LL.Mono)
                }
                Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    LoopRing(c, color, 158.dp, info, pos)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
                        Text("LAYERS", color = LL.Dim, fontSize = 9.5.sp, lineHeight = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp)
                        for (i in 0 until 4) {
                            if (i < c.layers) {
                                val muted = c.layerMute[i]
                                val shape = RoundedCornerShape(10.dp)
                                Row(
                                    Modifier.fillMaxWidth().height(30.dp).alpha(if (muted) 0.7f else 1f).clip(shape)
                                        .background(color.a(if (muted) 0.04f else 0.12f)).border(1.dp, color.a(if (muted) 0.2f else 0.4f), shape).padding(start = 11.dp, end = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(Modifier.size(6.dp, 15.dp).then(if (muted) Modifier.border(1.dp, color, RoundedCornerShape(3.dp)) else Modifier.background(color, RoundedCornerShape(3.dp))))
                                    Text("Layer ${i + 1}", Modifier.weight(1f), color = LL.Ink, fontSize = 12.5.sp, lineHeight = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                                    Text("${c.events.count { it.layer == i && it.on }} hits", color = LL.Mute, fontSize = 10.5.sp, lineHeight = 10.5.sp, fontFamily = LL.Mono)
                                    SmallSquareButton(if (muted) LoopIcon.Mute else LoopIcon.Speaker, if (muted) LL.Amber else LL.Soft, if (muted) LL.Amber.a(0.25f) else white(0.08f)) { actions.onToggleLayerMute(c.id, i) }
                                    SmallSquareButton(LoopIcon.X, LL.Soft, white(0.08f)) { actions.onRemoveLayer(c.id, i) }
                                }
                            } else {
                                Box(Modifier.fillMaxWidth().height(30.dp).dashedBorder(white(0.1f), 10.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                                    Text("Layer ${i + 1}", color = Color(0xFF4F596E), fontSize = 12.5.sp, lineHeight = 12.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().height(28.dp).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LoopIconView(LoopIcon.Speaker, LL.Mute, 14.dp)
                            VolumeSlider(c.volume, color, { actions.onVolume(c.id, it) }, Modifier.weight(1f))
                            Text("${c.volume}%", Modifier.width(34.dp), color = LL.Ink, fontSize = 11.sp, lineHeight = 11.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LL.Mono, textAlign = TextAlign.End)
                            val shape = RoundedCornerShape(9.dp)
                            Box(
                                Modifier.height(26.dp).clip(shape).background(if (c.mute) LL.Amber.a(0.2f) else white(0.05f)).border(1.dp, if (c.mute) LL.Amber.a(0.6f) else white(0.1f), shape)
                                    .pressable { actions.onToggleMute(c.id) }.padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("Mute", color = if (c.mute) LL.Amber else LL.Soft, fontSize = 11.sp, lineHeight = 11.sp, fontWeight = FontWeight.ExtraBold) }
                        }
                    }
                }
                ActionRow(c, color, 40.dp, true, actions)
            }
        }
        Column(Modifier.width(96.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            state.cases.forEach { x -> CaseChip(x, nowBar, x.id == c.id) { actions.onFocus(x.id) } }
        }
    }
}

/* ═══════════════════════════ CASE SETTINGS SHEET ═══════════════════════════ */

@Composable
private fun CaseSheet(c: LoopCase, actions: LooperActions, modifier: Modifier) {
    val color = loopCaseColor(c.id)
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(260)) }
    val idle = c.mode != CaseMode.Rec && c.mode != CaseMode.Overdub
    Box(modifier) {
        Box(Modifier.fillMaxSize().alpha(enter.value).background(Color(0xFF060912).a(0.5f), RoundedCornerShape(16.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { actions.onSheet(null) })
        val shape = RoundedCornerShape(16.dp)
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 14.dp.toPx() }
                .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape).background(PanelBrush).border(1.dp, white(0.12f), shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).drawBehind { drawCircle(color.a(0.4f), 6.dp.toPx()) }.background(color, CircleShape))
                Text(
                    "L${c.id + 1}${if (c.name.isNotEmpty()) " · ${c.name}" else ""} · " + if (c.layers > 0) "${c.bars} bar${if (c.bars > 1) "s" else ""} · ${c.layers} layer${if (c.layers > 1) "s" else ""}" else "empty",
                    Modifier.weight(1f), color = Color(0xFFF1F4FA), fontSize = 14.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Box(Modifier.size(28.dp).clip(CircleShape).background(white(0.07f)).pressable { actions.onSheet(null) }, contentAlignment = Alignment.Center) { LoopIconView(LoopIcon.X, Color(0xFF9AA5BA), 12.dp, 2.2f) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    Triple("×2", "Double", c.layers > 0 && c.bars * 2 <= LooperLogic.MAX_BARS && idle) to { actions.onDouble(c.id) },
                    Triple("÷2", "Half", c.bars > 1 && c.layers > 0 && idle) to { actions.onHalf(c.id) },
                    Triple("⧉", "Duplicate", c.layers > 0 && c.mode != CaseMode.Rec) to { actions.onDuplicate(c.id) },
                    Triple("⌁", "MIDI learn", true) to { actions.onMidiLearn(c.id) },
                    Triple("✕", "Clear", c.mode != CaseMode.Empty || c.redo.isNotEmpty()) to { actions.onClear(c.id) }
                ).forEach { (t, fn) ->
                    val (glyph, label, ok) = t
                    val sh = RoundedCornerShape(11.dp)
                    Column(
                        Modifier.weight(1f).height(48.dp).clip(sh).background(if (ok) Brush.verticalGradient(listOf(white(0.08f), white(0.03f))) else SolidColor(Color.Transparent))
                            .border(1.dp, white(0.06f), sh).pressable(enabled = ok, onClick = fn),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
                    ) {
                        Text(glyph, color = if (!ok) Color(0xFF4F596E) else if (label == "Clear") Color(0xFFE3A0B4) else LL.Accent, fontSize = 15.sp, lineHeight = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LL.Mono)
                        Text(label, color = if (ok) LL.Ink else Color(0xFF4F596E), fontSize = 10.5.sp, lineHeight = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                Group("RECORDS") {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(RecordSource.Pads to "Pads", RecordSource.Tonic to "Tonic", RecordSource.Keys to "Keys").forEach { (s, label) ->
                            val on = c.sources.has(s)
                            val sh = RoundedCornerShape(12.dp)
                            Box(
                                Modifier.height(24.dp).clip(sh).background(if (on) LL.Accent.a(0.16f) else Color.Transparent).border(1.dp, if (on) LL.Accent.a(0.55f) else white(0.1f), sh)
                                    .pressable { actions.onToggleSource(c.id, s) }.padding(horizontal = 11.dp),
                                contentAlignment = Alignment.Center
                            ) { Text(label, color = if (on) Color(0xFFD5E3FA) else Color(0xFF7B859A), fontSize = 11.5.sp, lineHeight = 11.5.sp, fontWeight = FontWeight.ExtraBold) }
                        }
                    }
                }
                Group("STOP AT") { Segmented(listOf(StopAt.EndOfLoop to "End of loop", StopAt.Now to "Now"), c.stopAt, { actions.onStopAt(c.id, it) }, 150.dp, 22.dp, 10.5.sp) }
                Group("AFTER RECORDING") { Segmented(listOf(AfterRecording.Play to "Play", AfterRecording.Overdub to "Overdub"), c.afterRecording, { actions.onAfterRecording(c.id, it) }, 130.dp, 22.dp, 10.5.sp) }
                Group("MODE") { Segmented(listOf(false to "Loop", true to "One-shot"), c.oneShot, { actions.onOneShot(c.id, it) }, 118.dp, 22.dp, 10.5.sp) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("NAME", color = LL.Dim, fontSize = 8.5.sp, lineHeight = 8.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 3.dp))
                key(c.id) { LoopTextField(c.name, "L${c.id + 1}", { actions.onRename(c.id, it) }, Modifier.weight(1f)) }
            }
        }
    }
}

/* ═══════════════════════════ TRANSPORT ═══════════════════════════ */

@Composable
private fun PillButton(text: String, icon: LoopIcon?, enabled: Boolean, rose: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.padding(bottom = 2.dp).height(28.dp).alpha(if (enabled) 1f else 0.4f).clip(shape)
            .background(if (rose) LL.Rose.a(0.1f) else white(0.05f)).border(1.dp, if (rose) LL.Rose.a(0.4f) else white(0.1f), shape)
            .pressable(enabled = enabled, onClick = onClick).padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) LoopIconView(icon, LL.Soft, 11.dp)
        Text(text, color = if (rose) Color(0xFFE3A0B4) else LL.Soft, fontSize = 11.5.sp, lineHeight = 11.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun Transport(state: LooperUiState, nowBar: Double, actions: LooperActions) {
    val s = state.settings
    val barN = floor(nowBar).toInt() + 1
    val beat = floor((nowBar - floor(nowBar)) * s.beatsPerBar).toInt()
    Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Group("QUANTIZE") { Segmented(listOf(Quantize.Off to "Off", Quantize.Sixteenth to "1/16", Quantize.Bar to "Bar"), s.quantize, actions.onQuantize, 112.dp) }
        Group("LENGTH") { Segmented(listOf<Pair<Int?, String>>(null to "Auto", 1 to "1", 2 to "2", 4 to "4", 8 to "8"), s.fixedLength, actions.onFixedLength, 152.dp) }
        Group("COUNT-IN") { Segmented(listOf(0 to "Off", 1 to "1", 2 to "2"), s.countInBars, actions.onCountIn, 96.dp) }
        Group("BAR $barN") {
            Row(Modifier.height(24.dp).padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (i in 0 until s.beatsPerBar) {
                    val on = beat == i
                    Box(Modifier.size(8.dp).drawBehind { if (on && i == 0) drawCircle(LL.Accent.a(0.4f), 7.dp.toPx()) }.background(if (on) (if (i == 0) LL.Accent else Color(0xFFC7CFDD)) else white(0.12f), CircleShape))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PillButton(if (state.anyStopped) "Play all" else "Stop all", if (state.anyStopped) LoopIcon.Play else LoopIcon.Stop, state.anyPlaying || state.anyStopped, false) {
            if (state.anyStopped) actions.onPlayAll() else actions.onStopAll()
        }
        PillButton("Clear all", null, state.usedCount > 0, true, actions.onRequestClearAll)
    }
}

/* ═══════════════════════════ PAGE CONTENT ═══════════════════════════ */

/**
 * Right area of the LiveLooper page: transport row + the 2x4 case grid (or the focused case).
 *
 * The area is laid out **responsively**, filling whatever box the page gives it: the
 * transport row is fixed height and the 2x4 grid shares the remainder with `weight(1f)`,
 * exactly like `LoopsPreview.jsx` (`grid-template-rows: repeat(2, minmax(0,1fr))`).
 *
 * A previous revision laid the cards out on a fixed 693 x 343 dp canvas and scaled the
 * whole thing with `min(w/W, h/H)`. On a phone that shrinks every card at once — the two
 * rows collapse into thin strips and the card numbers read as a squeezed line. The
 * reference grid is fluid, so the port must be too.
 */
@Composable
fun LiveLooperContent(state: LooperUiState, nowBar: Double, actions: LooperActions, modifier: Modifier = Modifier) {
    val focus = state.focusId?.let { state.cases.getOrNull(it) }
    val sheet = state.sheetCaseId?.let { state.cases.getOrNull(it) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Transport(state, nowBar, actions)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (focus != null) {
                FocusView(focus, state, nowBar, actions, Modifier.fillMaxSize())
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (r in 0 until 2) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (col in 0 until 4) {
                            val i = r * 4 + col
                            // getOrNull, not [i]: a loaded patch may hold fewer than 8 cases and
                            // the grid must not index past the end while it renders.
                            val c = state.cases.getOrNull(i)
                            if (c == null) {
                                Box(Modifier.weight(1f).fillMaxHeight())
                            } else {
                                val a = remember { Animatable(0f) }
                                LaunchedEffect(Unit) { delay(i * 25L); a.animateTo(1f, tween(300)) }
                                CaseCard(c, nowBar, state.settings.beatsPerBar, actions, Modifier.weight(1f).fillMaxHeight().alpha(a.value))
                            }
                        }
                    }
                }
            }
            if (sheet != null) CaseSheet(sheet, actions, Modifier.fillMaxSize())
        }
    }
}

/**
 * Convenience host: drives [LiveLooperContent]'s clock itself.
 *
 * Keeping the clock inside this small composable is the point: the position state it reads
 * every frame only invalidates this subtree, not the page that hosts it (top bar, TonicPad,
 * page selectors). Pass [active] = false when no case is moving so the idle page is cheap.
 */
@Composable
fun LiveLooperHost(
    state: LooperUiState,
    clock: () -> Double,
    active: Boolean,
    actions: LooperActions,
    modifier: Modifier = Modifier,
) {
    val now = rememberNowBar(clock, active)
    LiveLooperContent(state, now, actions, modifier)
}

/* ═══════════════════════════ OVERLAYS (page root) ═══════════════════════════ */

@Composable
private fun Scrim(alpha: Float, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF060912).a(alpha)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick))
}

@Composable
private fun DialogCard(width: Dp, title: String, text: String, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.width(width).shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape).background(PanelBrush).border(1.dp, white(0.12f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}.padding(18.dp)
    ) {
        Text(title, color = Color(0xFFF1F4FA), fontSize = 16.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black)
        Text(text, Modifier.padding(top = 6.dp), color = LL.Mute, fontSize = 12.5.sp, lineHeight = 18.sp)
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), content = content)
    }
}

@Composable
private fun DialogButton(text: String, kind: Int, onClick: () -> Unit) {   // 0 neutral, 1 rose outline, 2 primary, 3 rose solid
    val shape = RoundedCornerShape(11.dp)
    val bg = when (kind) { 2 -> Brush.verticalGradient(listOf(Color(0xFF9DBDEB), Color(0xFF7FA3DD))); 3 -> Brush.verticalGradient(listOf(Color(0xFFB8465F), Color(0xFF8E2F45))); 1 -> SolidColor(LL.Rose.a(0.1f)); else -> SolidColor(white(0.05f)) }
    val fg = when (kind) { 2 -> Color(0xFF0F1726); 3 -> Color(0xFFFFE7EE); 1 -> Color(0xFFE3A0B4); else -> LL.Soft }
    Box(
        Modifier.height(36.dp).clip(shape).background(bg).then(if (kind == 0 || kind == 1) Modifier.border(1.dp, if (kind == 1) LL.Rose.a(0.4f) else white(0.12f), shape) else Modifier)
            .pressable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = fg, fontSize = 13.sp, lineHeight = 13.sp, fontWeight = if (kind >= 2) FontWeight.Black else FontWeight.ExtraBold) }
}

@Composable
private fun PatchMiniBar(cases: List<LoopCase>) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        cases.forEachIndexed { i, x -> Box(Modifier.size(13.dp, 5.dp).background(if (x.layers > 0) loopCaseColor(i) else white(0.1f), RoundedCornerShape(2.dp))) }
    }
}

@Composable
private fun SmallChip(text: String, danger: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier.height(26.dp).clip(shape).background(if (danger) LL.Rose.a(0.1f) else white(0.05f)).border(1.dp, if (danger) LL.Rose.a(0.4f) else white(0.1f), shape)
            .pressable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (!enabled) Color(0xFF4F596E) else if (danger) Color(0xFFE3A0B4) else LL.Soft, fontSize = 11.sp, lineHeight = 11.sp, fontWeight = FontWeight.ExtraBold) }
}

@Composable
private fun PatchWindow(state: LooperUiState, actions: LooperActions, modifier: Modifier) {
    var open by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(320)) }
    val shape = RoundedCornerShape(22.dp)
    // The reference draws this window at a fixed `left:120, right:120, top:58, bottom:12`
    // on the 891 x 411 frame: a 651 x 341 dp box whose patch list and 196 dp right column
    // are sized against that width. Filling a phone's padded area stretched those columns
    // and squashed their text, so the window is laid out at 651 x 341 and scaled as a
    // whole — it only gets smaller on a small screen, never distorted.
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // Only ever shrink: a floating window keeps its designed 651 x 341 size on a large
        // screen (the reference draws it at scale 1 inside the 891 x 411 frame).
        val scale = min(maxWidth.value / LL_PATCH_W, maxHeight.value / LL_PATCH_H).coerceAtMost(1f)
        Box(Modifier.width((LL_PATCH_W * scale).dp).height((LL_PATCH_H * scale).dp)) {
            PatchWindowColumn(state, actions, open, { open = it }, renaming, { renaming = it }, enter, scale, shape)
        }
    }
}

@Composable
private fun PatchWindowColumn(
    state: LooperUiState, actions: LooperActions,
    open: String?, setOpen: (String?) -> Unit,
    renaming: String?, setRenaming: (String?) -> Unit,
    enter: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, scale: Float,
    shape: androidx.compose.ui.graphics.Shape,
) {
    Column(
        Modifier
            .width(LL_PATCH_W.dp).height(LL_PATCH_H.dp)
            .graphicsLayer {
                val e = enter.value
                scaleX = scale * (0.94f + 0.06f * e); scaleY = scale * (0.94f + 0.06f * e)
                translationY = (1f - e) * 8.dp.toPx(); alpha = e
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            }
            .shadow(26.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape).background(WindowBrush).border(1.dp, white(0.1f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Row(
            Modifier.fillMaxWidth().height(62.dp).drawBehind { drawLine(white(0.06f), Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx()) }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(4.dp, 32.dp).drawBehind { drawRoundRect(LL.Accent.a(0.35f), Offset(-3.dp.toPx(), 0f), Size(size.width + 6.dp.toPx(), size.height), CornerRadius(4.dp.toPx())) }.background(LL.Accent, RoundedCornerShape(2.dp)))
            Column(Modifier.weight(1f)) {
                Text("Loop patches", color = Color(0xFFF4F6FB), fontSize = 17.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black)
                Text("Setlist · ${state.setlistName} · saved with the setlist", color = Color(0xFF7B859A), fontSize = 11.5.sp, lineHeight = 11.5.sp, modifier = Modifier.padding(top = 2.dp))
            }
            RoundButton(LoopIcon.X, 34.dp, actions.onClosePatches)
        }
        Row(Modifier.fillMaxWidth().weight(1f).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.patches.forEach { p ->
                    val on = p.id == state.activePatchId
                    val exp = open == p.id
                    val used = p.cases.count { it.layers > 0 }
                    val maxBars = p.cases.filter { it.layers > 0 }.maxOfOrNull { it.bars } ?: 0
                    val sh = RoundedCornerShape(13.dp)
                    Column(
                        Modifier.fillMaxWidth().clip(sh)
                            .background(if (on) Brush.verticalGradient(listOf(LL.Accent.a(0.16f), LL.Accent.a(0.06f))) else RowBrush)
                            .border(1.dp, if (on) LL.Accent.a(0.55f) else white(0.06f), sh)
                    ) {
                        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(
                                Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { actions.onLoadPatch(p.id) },
                                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text(p.name, color = Color(0xFFF1F4FA), fontSize = 13.5.sp, lineHeight = 13.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 170.dp))
                                    if (on) Text("ACTIVE", Modifier.border(1.dp, LL.Accent.a(0.5f), RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 1.dp), color = LL.Accent, fontSize = 8.5.sp, lineHeight = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                    if (on && state.isDirty) Box(Modifier.size(7.dp).drawBehind { drawCircle(LL.Amber.a(0.4f), 6.dp.toPx()) }.background(LL.Amber, CircleShape))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    PatchMiniBar(p.cases)
                                    Text(if (used > 0) "$used case${if (used > 1) "s" else ""} · $maxBars bar${if (maxBars > 1) "s" else ""} max" else "empty", color = Color(0xFF7B859A), fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, fontFamily = LL.Mono)
                                }
                            }
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(if (exp) white(0.1f) else Color.Transparent).pressable { setOpen(if (exp) null else p.id) }, contentAlignment = Alignment.Center) {
                                LoopIconView(LoopIcon.More, Color(0xFF9AA5BA), 15.dp)
                            }
                        }
                        if (exp) Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (renaming == p.id) {
                                LoopTextField(p.name, "Name", { actions.onRenamePatch(p.id, it) }, Modifier.weight(1f))
                                SmallChip("Done", false, true) { setRenaming(null) }
                            } else {
                                SmallChip("Rename", false, true) { setRenaming(p.id) }
                                SmallChip("Duplicate", false, true) { actions.onDuplicatePatch(p.id) }
                                SmallChip("Overwrite with current", false, !on) { actions.onOverwritePatch(p.id) }
                                SmallChip("Delete", true, !on && state.patches.size > 1) { actions.onDeletePatch(p.id) }
                            }
                        }
                    }
                }
            }
            Column(Modifier.width(196.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CURRENT", color = LL.Dim, fontSize = 9.5.sp, lineHeight = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp)
                val sh = RoundedCornerShape(13.dp)
                Column(Modifier.fillMaxWidth().clip(sh).background(RowBrush).border(1.dp, white(0.06f), sh).padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.activePatch?.name ?: "Unsaved loops", color = Color(0xFFF1F4FA), fontSize = 14.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val c = if (state.isDirty) LL.Amber else LL.Sage
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(7.dp).drawBehind { drawCircle(c.a(0.4f), 6.dp.toPx()) }.background(c, CircleShape))
                        Text(if (state.isDirty) "Unsaved changes" else "Saved", color = c, fontSize = 11.5.sp, lineHeight = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        state.cases.forEach { x -> Box(Modifier.weight(1f).height(6.dp).background(if (x.layers > 0) loopCaseColor(x.id) else white(0.1f), RoundedCornerShape(3.dp))) }
                    }
                }
                val dirty = state.isDirty
                Box(
                    Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (dirty) Brush.verticalGradient(listOf(Color(0xFF9DBDEB), Color(0xFF7FA3DD))) else SolidColor(white(0.06f)))
                        .pressable(enabled = dirty && state.activePatchId != null, onClick = actions.onSavePatch),
                    contentAlignment = Alignment.Center
                ) { Text("Save", color = if (dirty) Color(0xFF0F1726) else LL.Faint, fontSize = 13.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black) }
                Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(12.dp)).background(white(0.05f)).border(1.dp, white(0.12f), RoundedCornerShape(12.dp)).pressable(onClick = actions.onSaveNewPatch), contentAlignment = Alignment.Center) {
                    Text("Save as new patch", color = Color(0xFFE3E8F2), fontSize = 12.5.sp, lineHeight = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                }
                Box(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(11.dp)).border(1.dp, white(0.1f), RoundedCornerShape(11.dp)).pressable(enabled = dirty && state.activePatch != null, onClick = actions.onRevertPatch), contentAlignment = Alignment.Center) {
                    Text("Revert to saved", color = if (dirty) LL.Soft else Color(0xFF4F596E), fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.weight(1f))
                Text("Patches belong to the setlist. They are stored with it and travel with it.", color = Color(0xFF59647A), fontSize = 10.5.sp, lineHeight = 15.sp)
            }
        }
    }
}

/** Patch window, dialogs and toast. Place it at the ROOT of the page, above the top bar, filling the page. */
@Composable
fun LiveLooperOverlays(state: LooperUiState, actions: LooperActions, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (state.patchWindowOpen) {
            Scrim(0.62f, actions.onClosePatches)
            PatchWindow(state, actions, Modifier.fillMaxSize())
        }
        val target = state.patches.firstOrNull { it.id == state.pendingLoadId }
        if (target != null) {
            Scrim(0.55f) { actions.onPendingLoad(PendingLoadChoice.Cancel) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DialogCard(380.dp, "Unsaved changes in “${state.activePatch?.name ?: "loops"}”", "Load “${target.name}” and keep your changes first?") {
                    DialogButton("Cancel", 0) { actions.onPendingLoad(PendingLoadChoice.Cancel) }
                    DialogButton("Discard", 1) { actions.onPendingLoad(PendingLoadChoice.Discard) }
                    DialogButton("Save and load", 2) { actions.onPendingLoad(PendingLoadChoice.SaveAndLoad) }
                }
            }
        }
        if (state.confirmClearAll) {
            Scrim(0.55f, actions.onDismissClearAll)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DialogCard(360.dp, "Clear all 8 cases?", "Every layer of every case is erased. The saved patch is not touched.") {
                    DialogButton("Cancel", 0, actions.onDismissClearAll)
                    DialogButton("Clear all", 3, actions.onConfirmClearAll)
                }
            }
        }
        state.toast?.let { t ->
            LaunchedEffect(t.key) { delay(if (t.undoable) 4200 else 1700); actions.onToastDismiss() }
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).shadow(10.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(shape).background(Color(0xFF222C40)).border(1.dp, white(0.1f), shape).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(t.text, color = LL.Ink, fontSize = 13.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (t.undoable) Text("UNDO", Modifier.pressable(onClick = actions.onToastUndo), color = LL.Accent, fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}

/* ═══════════════════════════ TOP BAR PIECES ═══════════════════════════ */

enum class DrumPadPage { DrumPad, LiveLooper }

@Composable
private fun TopPill(
    modifier: Modifier = Modifier,
    borderColor: Color = white(0.07f),
    height: Dp = 44.dp,
    cornerRadius: Dp = 14.dp,
    shadow: Dp = 5.dp,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Row(
        modifier.height(height).shadow(shadow, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF2A3347), Color(0xFF202839)))).border(1.dp, borderColor, shape),
        verticalAlignment = Alignment.CenterVertically, content = content
    )
}

/** Replaces the "DrumPad" pill: shows the current page and an arrow that opens the list DrumPad / LiveLooper. */
@Composable
fun LooperPageSelector(current: DrumPadPage, onSelect: (DrumPadPage) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 180f else 0f, tween(250), label = "arrow")
    val density = LocalDensity.current
    // 40 dp / r13 matches the Metro and BPM pills it sits next to: the selector is on
    // every page of this bar, so it must read as one of the bar's pills, not a taller
    // island. The old 44 dp / r14 / drop-shadow made it stand out from its neighbours.
    val pillHeight = 40.dp
    // Fixed width, not `widthIn(min = …)`: the inner `Text` carries `weight(1f)`, so
    // with only a minimum the pill is measured against the whole remaining top-bar
    // width and swallows it (the DrumPad case stretched across the entire bar). A
    // hard width keeps it the same size as the Metro/BPM pills next to it.
    val pillWidth = 150.dp
    Box(modifier) {
        TopPill(
            Modifier.width(pillWidth).pressable { open = !open },
            if (open) LL.Accent.a(0.5f) else white(0.08f),
            height = pillHeight,
            cornerRadius = 13.dp,
            shadow = 0.dp,
        ) {
            Text(if (current == DrumPadPage.LiveLooper) "LiveLooper" else "DrumPad", Modifier.padding(start = 14.dp).weight(1f), color = Color(0xFFE3E8F2), fontSize = 14.sp, lineHeight = 14.sp, fontWeight = FontWeight.ExtraBold)
            Box(Modifier.padding(end = 12.dp).rotate(rot)) { LoopIconView(LoopIcon.Down, LL.Accent, 16.dp, 2.4f) }
        }
        if (open) Popup(alignment = Alignment.TopStart, offset = IntOffset(0, with(density) { (pillHeight + 6.dp).roundToPx() }), onDismissRequest = { open = false }) {
            val e = remember { Animatable(0.85f) }
            LaunchedEffect(Unit) { e.animateTo(1f, tween(180)) }
            val shape = RoundedCornerShape(14.dp)
            Column(
                Modifier.width(190.dp).graphicsLayer { scaleY = e.value; alpha = (e.value - 0.85f) / 0.15f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f) }
                    .shadow(16.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape).background(PanelBrush).border(1.dp, white(0.12f), shape).padding(5.dp)
            ) {
                listOf(DrumPadPage.DrumPad to "DrumPad", DrumPadPage.LiveLooper to "LiveLooper").forEach { (p, label) ->
                    val sel = p == current
                    Row(
                        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) LL.Accent.a(0.14f) else Color.Transparent)
                            .pressable { open = false; onSelect(p) }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, Modifier.weight(1f), color = if (sel) Color(0xFFF1F4FA) else Color(0xFFB5BFD3), fontSize = 14.sp, lineHeight = 14.sp, fontWeight = FontWeight.ExtraBold)
                        if (sel) LoopIconView(LoopIcon.Check, LL.Accent, 15.dp, 2.6f)
                    }
                }
            }
        }
    }
}

/** Top bar chip of the LiveLooper page: previous / name (opens the window) / next. The amber dot means unsaved changes. */
@Composable
fun LoopPatchChip(state: LooperUiState, actions: LooperActions, modifier: Modifier = Modifier) {
    TopPill(modifier) {
        Box(Modifier.size(34.dp, 44.dp).pressable { actions.onStepPatch(-1) }, contentAlignment = Alignment.Center) { LoopIconView(LoopIcon.Left, LL.Soft, 16.dp, 2.4f) }
        Row(
            Modifier.height(44.dp).widthIn(min = 156.dp, max = 190.dp)
                .drawBehind { drawLine(white(0.06f), Offset(0.5f, 0f), Offset(0.5f, size.height), 1.dp.toPx()); drawLine(white(0.06f), Offset(size.width - 0.5f, 0f), Offset(size.width - 0.5f, size.height), 1.dp.toPx()) }
                .pressable(onClick = actions.onOpenPatches).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LoopIconView(LoopIcon.Layers, LL.Accent, 15.dp, 2f)
            Column(Modifier.weight(1f, fill = false)) {
                Text("LOOP PATCH", color = LL.Dim, fontSize = 8.5.sp, lineHeight = 8.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp)
                Text(state.activePatch?.name ?: "Unsaved", Modifier.widthIn(max = 112.dp), color = Color(0xFFE3E8F2), fontSize = 13.sp, lineHeight = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (state.isDirty) Box(Modifier.size(7.dp).drawBehind { drawCircle(LL.Amber.a(0.4f), 6.dp.toPx()) }.background(LL.Amber, CircleShape))
        }
        Box(Modifier.size(34.dp, 44.dp).pressable { actions.onStepPatch(1) }, contentAlignment = Alignment.Center) { LoopIconView(LoopIcon.Right, LL.Soft, 16.dp, 2.4f) }
    }
}

/** Replaces the "Tonic FX" card of the left column on the LiveLooper page. */
@Composable
fun LoopsSummaryCard(state: LooperUiState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.clip(shape).background(Color(0xFF1B2336)).padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row {
            Text("Loops ", color = LL.Soft, fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold)
            Text("${state.usedCount}/8", color = LL.Amber, fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            state.cases.forEach { c -> Box(Modifier.weight(1f).height(5.dp).background(if (c.layers == 0) white(0.1f) else loopCaseColor(c.id), RoundedCornerShape(2.dp))) }
        }
    }
}

/* ═══════════════════════════ PREVIEW ═══════════════════════════ */

@Preview(widthDp = 891, heightDp = 411, backgroundColor = 0xFF0F141D, showBackground = true)
@Composable
private fun LiveLooperPreview() {
    val barMs = 4 * 60000.0 / 119.0
    val t0 = remember { System.nanoTime() }
    val clock: () -> Double = { (System.nanoTime() - t0) / 1e6 / barMs }
    val ctrl = remember {
        LooperController(
            clock, setlistName = "Sunday Show", initialActiveId = "p1",
            initialPatches = listOf(LoopPatch("p1", "Verse groove", List(8) { LoopCase(it) }), LoopPatch("p2", "Chorus stack", List(8) { LoopCase(it) }))
        )
    }
    LaunchedEffect(Unit) { while (true) { ctrl.tick(); delay(8) } }   // preview only: in the app the transport clock drives tick()
    val state by ctrl.state.collectAsState()
    val actions = remember { ctrl.actions() }
    val now = rememberNowBar(clock)
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.width(168.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { LooperPageSelector(DrumPadPage.LiveLooper, {}); LoopPatchChip(state, actions) }
                Spacer(Modifier.weight(1f))
                LoopsSummaryCard(state, Modifier.fillMaxWidth().height(56.dp))
            }
            LiveLooperContent(state, now, actions, Modifier.weight(1f).fillMaxHeight().padding(top = 54.dp))
        }
        LiveLooperOverlays(state, actions, Modifier.fillMaxSize())
    }
}
