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
