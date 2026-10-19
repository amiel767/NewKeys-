package com.soundstage.mixer.ui.components.drumpad

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.audio.LooperEngine
import com.soundstage.mixer.model.DrumPadItem
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────
// Ring burst model (partagé Pad + NoteKey conceptuel — ici Pad uniquement)
// ─────────────────────────────────────────────────────────────────────────
private data class RingBurst(val id: Long, val center: Offset)

// ─────────────────────────────────────────────────────────────────────────
// DpPad — fidèle au JSX <Pad>
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun DpPad(
    index: Int,
    pad: DrumPadItem,
    color: Color,
    mode: String,
    runner: Boolean,
    flash: Boolean,
    ghost: Boolean,
    onDown: (Int) -> Unit,
    onUp: (Int) -> Unit,
    onVolumeChange: (Int, Int) -> Unit,
    appear: Boolean,
    modifier: Modifier = Modifier,
) {
    val isEmpty = pad.sampleFileName.isEmpty()
    val scope = rememberCoroutineScope()

    // ─── Local UI state ───
    var down by remember { mutableStateOf(false) }
    var spec by remember { mutableStateOf(Offset(0.30f, 0.20f)) } // flood center normalisé 0..1
    var bursts by remember { mutableStateOf(listOf<RingBurst>()) }
    var volOverlay by remember { mutableStateOf(false) }
    var displayVol by remember { mutableStateOf((pad.volume * 100).toInt()) }
    var entryProgress by remember { mutableStateOf(if (appear) 0f else 1f) }

    // Sync display vol when pad.volume changes
    LaunchedEffect(pad.volume) { displayVol = (pad.volume * 100).toInt() }

    // Entry animation
    LaunchedEffect(appear) {
        if (appear) {
            kotlinx.coroutines.delay(index * 34L)
            entryProgress = 1f
        } else entryProgress = 1f
    }

    // Press scale
    val pressScale by animateFloatAsState(
        targetValue = if (down && !volOverlay) 0.978f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "padScale"
    )

    // Flood alpha (JSX: lit ? .04s in : .5s out)
    val floodAlpha by animateFloatAsState(
        targetValue = if (flash && !isEmpty) 0.95f else 0f,
        animationSpec = if (flash) tween(40) else tween(500, easing = FastOutSlowInEasing),
        label = "floodAlpha"
    )

    // Entry alpha
    val entryAlpha = entryProgress

    // ─── Background gradient (JSX exact) ───
    val bgBrush = if (isEmpty) {
        Brush.linearGradient(
            colors = listOf(Color(0xFF1D2537), Color(0xFF141A28)),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                color.copy(alpha = 0.25f),
                Color(0xFF1F2739),
                Color(0xFF151B2A)
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale * (0.94f + 0.06f * entryAlpha)
                scaleY = pressScale * (0.94f + 0.06f * entryAlpha)
                alpha = entryAlpha
                translationY = (1f - entryAlpha) * 12f
            }
            .clip(RoundedCornerShape(18.dp))
            .background(bgBrush)
            .noiseOverlay(0.07f)
            .pointerInput(pad.id) {
                // Swipe-volume via awaitEachGesture (up = augmenter)
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val startY = firstDown.position.y
                    val startVol = pad.volume * 100f
                    var isVol = false
                    var currentVol = startVol
                    var lastDy = 0f

                    down = true
                    onDown(pad.id)
                    spec = Offset(
                        (firstDown.position.x / size.width.toFloat()).coerceIn(0.05f, 0.95f),
                        (firstDown.position.y / size.height.toFloat()).coerceIn(0.05f, 0.95f)
                    )
                    val id = System.nanoTime()
                    bursts = bursts + RingBurst(id, firstDown.position)
                    scope.launch {
                        kotlinx.coroutines.delay(700)
                        bursts = bursts.filterNot { it.id == id }
                    }

                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == firstDown.id } ?: break
                        if (!ch.pressed) break
                        val dy = ch.position.y - startY
                        lastDy = abs(dy)
                        if (!isVol && lastDy > 8f * density) {
                            isVol = true
                            volOverlay = true
                            displayVol = (startVol).toInt()
                        }
                        if (isVol) {
                            currentVol = (startVol - dy / (size.height.toFloat() * 0.55f) * 100f)
                                .coerceIn(0f, 100f)
                            displayVol = currentVol.toInt()
                            onVolumeChange(pad.id, displayVol)
                            ch.consume()
                        }
                    }

                    down = false
                    if (isVol) {
                        scope.launch {
                            kotlinx.coroutines.delay(520)
                            volOverlay = false
                        }
                    } else {
                        onUp(pad.id)
                    }
                }
            }
    ) {
        // Highlight radial top-left (JSX: radial-gradient 120% 90% at 18% 0%)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.11f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = 2000f
                    )
                )
        )

        // Flood radial depuis touch (screen)
        if (floodAlpha > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    color,
                                    color.copy(alpha = 0.60f),
                                    color.copy(alpha = 0.25f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * spec.x, size.height * spec.y),
                                radius = 3000f
                            ),
                            alpha = floodAlpha
                        )
                    }
            )
        }

        // Ghost radial (event loopé récent)
        if (ghost) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    DpColors.ACCENT.copy(alpha = 0.26f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.5f, size.height * 0.45f),
                                radius = 2500f
                            )
                        )
                    }
            )
        }

        // Ring bursts (Canvas)
        Canvas(Modifier.fillMaxSize()) {
            val now = System.nanoTime()
            bursts.forEach { b ->
                // animation simple : rayon grossit, alpha diminue
                val ageMs = ((System.nanoTime() - b.id) / 1_000_000L).toFloat().coerceIn(0f, 700f)
                val progress = ageMs / 700f
                val scaleFactor = 0.35f + 2.35f * progress
                val r = 70.dp.toPx() * scaleFactor
                val alpha = (1f - progress) * 0.55f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                        center = b.center,
                        radius = r
                    ),
                    radius = r,
                    center = b.center
                )
            }
        }

        // Noise overlay (déjà via modifier, mais on garde un léger local pour cohérence)
        // (déjà appliqué)

        // Content
        if (volOverlay) {
            DpVolumeOverlay(
                value = displayVol,
                color = color,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 9.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isEmpty) "+ Assign" else pad.label,
                        color = if (isEmpty) Color(0xFF6F7A90) else Color(0xFFF1F4FA),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (mode == "edit") {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color.White.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("›", color = Color(0xFFB5BFD3), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "D${index + 1}${if (pad.isLoopMode && !isEmpty) " · loop" else ""}",
                        color = if (flash && !isEmpty) Color.White else Color(0xFF8E99AE),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (!isEmpty) {
                        Text(
                            text = "${(pad.volume * 100).toInt()}%",
                            color = if (flash) Color.White else Color(0xFF8E99AE),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // LED ring (bord) si runner
        if (runner) {
            DpLedRing(
                color = color,
                mode = "run",
                durMs = 2000,
                radius = 18.dp,
                strokeWidth = 2.dp,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// DpLoopCase — fidèle au JSX <LoopCase>
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun DpLoopCase(
    index: Int,
    case: LooperEngine.Case,
    nowNs: Long,
    beatsPerBar: Int,
    barDurationNs: Long,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = case.state
    val stateColor: Color = when (state) {
        LooperEngine.CaseState.Recording, LooperEngine.CaseState.Overdubbing -> DpColors.ROSE
        LooperEngine.CaseState.Queued -> DpColors.WARM
        LooperEngine.CaseState.Playing -> DpColors.ACCENT
        LooperEngine.CaseState.Stopped -> Color(0xFF7B859A)
        LooperEngine.CaseState.Empty -> Color(0xFF566075)
    }
    val borderColor = when (state) {
        LooperEngine.CaseState.Empty -> Color.White.copy(alpha = 0.06f)
        else -> stateColor.copy(alpha = 0.4f)
    }

    // Phase / barNow
    val bars = case.bars.coerceAtLeast(1)
    val loopMs = (bars.toLong() * barDurationNs).coerceAtLeast(1L)
    var headPhase: Float? = null
    var barNow = 0
    when (state) {
        LooperEngine.CaseState.Playing, LooperEngine.CaseState.Overdubbing -> {
            if (case.startAtNs > 0L && nowNs >= case.startAtNs) {
                val elapsed = nowNs - case.startAtNs
                val ph = (elapsed.toFloat() / loopMs.toFloat()) % 1f
                headPhase = ph
                barNow = (ph * bars).toInt() + 1
            }
        }
        LooperEngine.CaseState.Recording -> {
            // V1: recStartNs absent → approximer via startAtNs
            if (case.startAtNs > 0L && nowNs >= case.startAtNs) {
                val el = (nowNs - case.startAtNs).toFloat() / barDurationNs.toFloat()
                val denom = bars.toFloat().coerceAtLeast(1f)
                headPhase = (el % denom) / denom
                barNow = el.toInt() + 1
            }
        }
        else -> {}
    }

    // State label / icon
    val stateLabel: String = when (state) {
        LooperEngine.CaseState.Recording -> "REC"
        LooperEngine.CaseState.Queued -> "QUEUED"
        LooperEngine.CaseState.Playing -> "PLAY"
        LooperEngine.CaseState.Overdubbing -> "OVERDUB"
        LooperEngine.CaseState.Stopped -> "STOPPED"
        LooperEngine.CaseState.Empty -> "EMPTY"
    }
    val statePulse = state == LooperEngine.CaseState.Recording || state == LooperEngine.CaseState.Queued
    val pulseAlpha = if (statePulse) {
        val t = ((System.nanoTime() % 1_000_000_000L) / 1_000_000_000f)
        (0.55f + 0.45f * kotlin.math.abs(kotlin.math.sin(t * PI.toFloat()))).coerceIn(0f, 1f)
    } else 1f

    // Center text
    val centerPrimary: String = when (state) {
        LooperEngine.CaseState.Empty -> "+"
        LooperEngine.CaseState.Recording -> "●"
        LooperEngine.CaseState.Stopped -> "❚❚"
        LooperEngine.CaseState.Queued -> "…"
        LooperEngine.CaseState.Playing, LooperEngine.CaseState.Overdubbing -> "$barNow/$bars"
    }
    val centerSub: String = when (state) {
        LooperEngine.CaseState.Empty -> "tap to arm"
        LooperEngine.CaseState.Recording -> "bar $barNow"
        else -> "$bars bar${if (bars == 1) "" else "s"}"
    }

    // Layer count / event dots
    val layers = case.layers
    val layerCount = layers.size
    val maxLayers = 4

    // Beat pulse indicator (very simplified — VM ne pousse pas encore de beat)
    val beatIndex = ((nowNs / (barDurationNs / 4).coerceAtLeast(1L)) % 4).toInt()

    // LedRing mode
    val ledMode = when (state) {
        LooperEngine.CaseState.Recording, LooperEngine.CaseState.Overdubbing -> "solid"
        LooperEngine.CaseState.Queued -> "queued"
        else -> "off"
    }

    // Gesture (tap vs long-press exclusifs via detectTapGestures)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(19.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF222B3E), Color(0xFF161C2B))))
            .border(1.dp, borderColor, RoundedCornerShape(19.dp))
            .noiseOverlay(0.06f)
            .pointerInput(index) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            }
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ─── Cadran (Canvas) ───
            // Sized from the case box instead of fillMaxHeight+aspectRatio: that
            // combination let the dial eat the whole width and squeezed the label
            // column, which is where "EMPTY" got clipped. The dial now takes at most
            // 44 % of the width so the state label always has room to render whole.
            BoxWithConstraints(Modifier.fillMaxHeight()) {
                val dial = minOf(maxHeight, maxWidth * 0.44f)
                Box(Modifier.size(dial)) {
                Canvas(Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxR = size.minDimension / 2f
                    val scale = maxR / 50f

                    // outer ring r=44
                    drawCircle(
                        color = Color.White.copy(alpha = 0.08f),
                        radius = 44f * scale,
                        center = center,
                        style = Stroke(width = 3f * scale)
                    )

                    // bar ticks (une par bar)
                    for (i in 0 until bars) {
                        val angle = (i.toFloat() / bars.toFloat()) * 2f * PI.toFloat() - PI.toFloat() / 2f
                        val r1 = 41.5f * scale
                        val r2 = 47f * scale
                        val x1 = center.x + cos(angle) * r1
                        val y1 = center.y + sin(angle) * r1
                        val x2 = center.x + cos(angle) * r2
                        val y2 = center.y + sin(angle) * r2
                        val highlighted = headPhase != null && barNow == i + 1
                        drawLine(
                            color = if (highlighted) stateColor else Color.White.copy(alpha = 0.28f),
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 1.6f * scale,
                            cap = StrokeCap.Round
                        )
                    }

                    // layer rings
                    val ringRadii = listOf(38f, 31f, 24f, 17f)
                    ringRadii.forEachIndexed { li, rr ->
                        drawCircle(
                            color = Color.White.copy(alpha = if (li < layerCount) 0.13f else 0.04f),
                            radius = rr * scale,
                            center = center,
                            style = Stroke(width = 1f * scale)
                        )
                    }

                    // event dots (par layer)
                    layers.forEachIndexed { li, layer ->
                        if (li >= ringRadii.size) return@forEachIndexed
                        val r = ringRadii[li] * scale
                        layer.forEach { ev ->
                            val frac = (ev.beatOffset.toFloat() / bars.toFloat()).coerceIn(0f, 1f)
                            val a = frac * 2f * PI.toFloat() - PI.toFloat() / 2f
                            val x = center.x + cos(a) * r
                            val y = center.y + sin(a) * r
                            val dotColor = parseHexColorOr(ev.color, stateColor.copy(alpha = 0.95f))
                            drawCircle(dotColor, radius = 2.5f * scale, center = Offset(x, y))
                        }
                    }

                    // head arc + dot
                    val hp = headPhase
                    if (hp != null) {
                        val startFrac = (hp - 0.14f).coerceAtLeast(0f)
                        val startAngle = startFrac * 360f - 90f
                        val sweep = (hp - startFrac) * 360f
                        drawArc(
                            color = stateColor.copy(alpha = 0.45f),
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(center.x - 44f * scale, center.y - 44f * scale),
                            size = Size(88f * scale, 88f * scale),
                            style = Stroke(width = 3f * scale, cap = StrokeCap.Round)
                        )
                        val ha = hp * 2f * PI.toFloat() - PI.toFloat() / 2f
                        val hx = center.x + cos(ha) * 44f * scale
                        val hy = center.y + sin(ha) * 44f * scale
                        drawCircle(color = stateColor, radius = 4.2f * scale, center = Offset(hx, hy))
                    }

                    // center primary + sub (dessinés en texte dans la Box, voir ci-dessous)
                }

                // center text overlay
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = centerPrimary,
                        color = Color(0xFFF1F4FA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = centerSub,
                        color = Color(0xFF8E99AE),
                        fontSize = 6.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                }
            }

            // ─── Right panel ───
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top : L1 + état pulsé
                Column {
                    Text(
                        text = "L${index + 1}",
                        color = Color(0xFF8E99AD),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = stateLabel,
                        color = if (state == LooperEngine.CaseState.Empty) Color(0xFF6F7A90) else stateColor.copy(alpha = pulseAlpha),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.4.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Bottom : layer bars + beat dots, as in the JSX LoopCase
                Column {
                    // 4 layer bars; filled layers glow like the reference.
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        for (i in 0 until maxLayers) {
                            val filled = i < layerCount
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (filled) stateColor
                                        else Color.White.copy(alpha = 0.09f)
                                    )
                                    .drawBehind {
                                        if (filled) {
                                            drawRoundRect(
                                                color = stateColor.copy(alpha = 0.45f),
                                                cornerRadius = CornerRadius(2.dp.toPx()),
                                                topLeft = Offset(-1.dp.toPx(), -1.dp.toPx()),
                                                size = Size(size.width + 2.dp.toPx(), size.height + 2.dp.toPx()),
                                                style = Stroke(1.5.dp.toPx())
                                            )
                                        }
                                    }
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    // Bar-relative beat dots (the transport beat, not per-case), like JSX.
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        for (i in 0 until 4) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (state != LooperEngine.CaseState.Empty && beatIndex == i) stateColor
                                        else Color.White.copy(alpha = 0.12f)
                                    )
                            )
                        }
                    }
                }
            }
        }

        // LedRing (bord)
        DpLedRing(
            color = stateColor,
            mode = ledMode,
            durMs = 2000,
            radius = 19.dp,
            strokeWidth = 2.dp,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Utilitaire couleur (event hex → Color)
// ─────────────────────────────────────────────────────────────────────────
private fun parseHexColorOr(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    fallback
}
