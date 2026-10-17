package com.soundstage.mixer.ui.components.drumpad

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.audio.LooperEngine
import com.soundstage.mixer.model.DrumPadItem
import com.soundstage.mixer.model.SurfaceFxChain
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────
// DpPad — une seule surface, flood radial, LED ring, swipe-volume
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
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    var dragVol by remember { mutableStateOf<Float?>(null) }
    var entryAlpha by remember { mutableStateOf(if (appear) 0f else 1f) }

    LaunchedEffect(appear) {
        if (appear) {
            entryAlpha = 0f
            delay(index * 30L)
            entryAlpha = 1f
        } else entryAlpha = 1f
    }

    val pressScale by animateFloatAsState(
        targetValue = if (pad.isPressed || ghost) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "dpPadScale"
    )

    val gradient = if (isEmpty) {
        Brush.linearGradient(
            colors = listOf(Color(0xFF1D2537), Color(0xFF141A28)),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    } else {
        Brush.linearGradient(
            colors = listOf(color.copy(alpha = 0.25f), Color(0xFF1F2739), Color(0xFF151B2A)),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale; alpha = entryAlpha }
            .clip(RoundedCornerShape(14.dp))
            .background(gradient)
            .pointerInput(pad.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    touchPos = down.position
                    val startY = down.position.y
                    val startVol = pad.volume
                    var dragged = false
                    var currentVol = startVol
                    onDown(pad.id)
                    while (true) {
                        val event = awaitPointerEvent()
                        val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        val dy = ch.position.y - startY
                        if (!dragged && abs(dy) > viewConfiguration.touchSlop) {
                            dragged = true
                        }
                        if (dragged) {
                            val delta = -dy / (size.height.toFloat() * 0.7f)
                            currentVol = (startVol + delta).coerceIn(0f, 1f)
                            dragVol = currentVol
                            ch.consume()
                        }
                    }
                    if (dragged) onVolumeChange(pad.id, (currentVol * 100).toInt())
                    dragVol = null
                    touchPos = null
                    onUp(pad.id)
                }
            }
    ) {
        // flood radial + hairline top + ring grain
        Box(
            Modifier
                .fillMaxSize()
                .let { m ->
                    val pos = touchPos
                    if (pos == null) m else m.background(
                        Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = 0.55f),
                                color.copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            center = pos,
                            radius = 500f
                        )
                    )
                }
        )

        // LED ring (top-right)
        if (runner) {
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(10.dp)) {
                DpLedRing(
                    mode = if (flash) "solid" else "off",
                    color = color,
                )
            }
        }

        // content
        if (isEmpty) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "+ Assign",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "P$index",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (mode == "Loop") Text(
                        "LOOP",
                        color = Color(0xFFD8A24A),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = pad.label.ifEmpty { pad.sampleFileName.substringBeforeLast('.') },
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${(pad.volume * 100).toInt()}%",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Volume overlay during drag
        dragVol?.let { v ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DpVolumeOverlay(
                    value = v,
                    padColor = color,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// DpNoteKey — touche TonicPad (ON/OFF + octave)
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun DpNoteKey(
    label: String,
    oct: Int,
    idx: Int,
    on: Boolean,
    ghost: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = DpPalette.at(idx % 8)
    val pressScale by animateFloatAsState(
        targetValue = if (ghost) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "dpNoteScale"
    )
    val bg = if (on) {
        Brush.verticalGradient(
            colors = listOf(tint.copy(alpha = 0.98f), tint.copy(alpha = 0.66f))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(tint.copy(alpha = 0.26f), Color(0xFF1B2336).copy(alpha = 0.92f))
        )
    }
    val textColor = if (on) Color.White else tint
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .pointerInput(idx) {
                detectTapGestures(onTap = { onToggle() })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, color = textColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            if (oct != 0) {
                Text(
                    oct.toString(),
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// DpFxCase — slot FX (Tonic 5 effets / Pads 4 effets)
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun DpFxCase(
    fx: SurfaceFxChain,
    includeShimmer: Boolean,
    label: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = if (includeShimmer) 5 else 4
    val active = fx.enabledCount(includeShimmer)
    val bars = buildList {
        if (fx.filterOn) add(DpPalette.at(0))
        if (fx.brightnessOn) add(DpPalette.at(3))
        if (fx.delayOn) add(DpPalette.at(5))
        if (fx.reverbOn) add(DpPalette.at(4))
        if (includeShimmer && fx.shimmerOn) add(DpPalette.at(1))
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF232C3E), Color(0xFF1A2230))))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onOpen()
                }
            }
            .padding(8.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    label,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "$active/$total",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (bars.isEmpty()) {
                    Text(
                        "—",
                        color = Color.White.copy(alpha = 0.2f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    bars.forEach { c ->
                        Box(
                            Modifier
                                .size(width = 4.dp, height = 12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(c)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// DpLoopCase — cadran looper (SVG-style via Canvas)
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
    val borderColor: Color = when (case.state) {
        LooperEngine.CaseState.Recording,
        LooperEngine.CaseState.Overdubbing -> Color(0xFFC2587A)
        LooperEngine.CaseState.Queued -> Color(0xFFD8A24A)
        LooperEngine.CaseState.Playing -> Color(0xFF8FB2E8)
        LooperEngine.CaseState.Stopped -> Color(0xFF6F7A90)
        LooperEngine.CaseState.Empty -> Color(0xFF6F7A90).copy(alpha = 0.30f)
    }
    val totalDuration = (case.bars.coerceAtLeast(1).toLong()) * barDurationNs
    val elapsed = if (case.startAtNs > 0L && totalDuration > 0L) {
        ((nowNs - case.startAtNs).coerceAtLeast(0L)) % totalDuration
    } else 0L
    val headFraction = if (totalDuration > 0L) elapsed.toFloat() / totalDuration.toFloat() else 0f

    val ledMode = when (case.state) {
        LooperEngine.CaseState.Recording, LooperEngine.CaseState.Overdubbing -> "solid"
        LooperEngine.CaseState.Queued -> "queued"
        else -> "off"
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1F2739), Color(0xFF151B2A))))
            .pointerInput(index) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = min(size.width, size.height) / 2f - 2f
            val scale = maxR / 48f

            // outer ring r=44
            drawCircle(
                color = borderColor.copy(alpha = 0.55f),
                radius = 44f * scale,
                center = center,
                style = Stroke(width = 1.5f * scale)
            )

            // bar ticks around outer ring
            val bars = case.bars.coerceAtLeast(1)
            for (i in 0 until bars) {
                val angle = (-PI / 2 + i * 2 * PI / bars).toFloat()
                val inner = Offset(center.x + cos(angle) * 46f * scale, center.y + sin(angle) * 46f * scale)
                val outer = Offset(center.x + cos(angle) * 49f * scale, center.y + sin(angle) * 49f * scale)
                drawLine(borderColor.copy(alpha = 0.35f), inner, outer, strokeWidth = 1f * scale)
            }

            // 4 layer rings r=[38, 31, 24, 17]
            val ringRadii = listOf(38f, 31f, 24f, 17f).map { it * scale }
            ringRadii.forEachIndexed { layerIdx, r ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = r,
                    center = center,
                    style = Stroke(width = 0.8f * scale)
                )
                val layer = case.layers.getOrNull(layerIdx) ?: return@forEachIndexed
                val totalBeats = bars * beatsPerBar.coerceAtLeast(1)
                layer.forEach { ev ->
                    val frac = (ev.beatOffset / totalBeats.toDouble()).toFloat()
                    val angle = (-PI / 2 + frac * 2 * PI).toFloat()
                    val dotR = 1.8f * scale
                    val dotC = Offset(center.x + cos(angle) * r, center.y + sin(angle) * r)
                    val dotColor = parseHexColorOr(ev.color, borderColor.copy(alpha = 0.8f))
                    drawCircle(dotColor, dotR, dotC)
                }
            }

            // moving head — arc 0.14 circumference + dot
            if (case.startAtNs > 0L && totalDuration > 0L) {
                val sweep = 0.14f * 2f * PI.toFloat()
                val headAngle = (-PI / 2).toFloat() + headFraction * 2f * PI.toFloat()
                drawArc(
                    color = borderColor,
                    startAngle = Math.toDegrees((headAngle - sweep).toDouble()).toFloat(),
                    sweepAngle = Math.toDegrees(sweep.toDouble()).toFloat(),
                    useCenter = false,
                    style = Stroke(width = 2f * scale)
                )
                val hx = center.x + cos(headAngle) * 44f * scale
                val hy = center.y + sin(headAngle) * 44f * scale
                drawCircle(borderColor, 2.5f * scale, Offset(hx, hy))
            }
        }

        // center text
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val headline = when (case.state) {
                LooperEngine.CaseState.Recording -> "REC"
                LooperEngine.CaseState.Queued -> "QUEUED"
                LooperEngine.CaseState.Playing,
                LooperEngine.CaseState.Overdubbing -> "L${case.layers.size.coerceAtLeast(1)}"
                LooperEngine.CaseState.Stopped -> "STOP"
                LooperEngine.CaseState.Empty -> "L$index"
            }
            Text(
                headline,
                color = borderColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            if (case.state != LooperEngine.CaseState.Empty && case.bars > 0) {
                val curBar = (headFraction * case.bars).toInt().coerceIn(0, case.bars - 1) + 1
                Text(
                    "BAR $curBar/${case.bars}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // chips OF 0 / CH 0 (inertes V1)
        Box(Modifier.fillMaxSize().padding(6.dp)) {
            Text(
                "OF 0",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 7.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.BottomStart)
            )
            Text(
                "CH 0",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 7.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        // LED ring top-right
        Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(10.dp)) {
            DpLedRing(
                mode = ledMode,
                color = borderColor,
            )
        }
    }
}

private fun parseHexColorOr(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    fallback
}