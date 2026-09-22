package com.soundstage.mixer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * CustomVerticalFader Composable 100% faithful to mixer_material_you.svg:
 * - Clean oblong dark slot track (#0A0C12, stroke #1B1F2C)
 * - Track color gauge & audio-reactive aura
 * - Pristine Matte Slate Fader Cap (34dp x 24dp, rx=8dp) with a SINGLE vibrant horizontal indicator slot (14dp x 3dp, rx=1.5dp)
 * - Reference dB ticks (0 dB and -∞)
 */
@Composable
fun CustomVerticalFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    auraColor: Color = Color(0xFFF6C445),
    audioActivity: Float = 0f,
    isEnabled: Boolean = true,
    showTicks: Boolean = true,
    trackWidth: Dp = 16.dp,
    trackHeight: Dp = 220.dp,
    thumbWidth: Dp = 34.dp,
    thumbHeight: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    val thumbHeightPx = with(density) { thumbHeight.toPx() }

    var isDragging by remember { mutableStateOf(false) }
    var localValue by remember { mutableFloatStateOf(value) }

    LaunchedEffect(value) {
        if (!isDragging) {
            localValue = value.coerceIn(0f, 1f)
        }
    }

    val currentOnValueChange by rememberUpdatedState(onValueChange)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .onSizeChanged { size ->
                if (size.height > 0) {
                    containerHeightPx = size.height.toFloat()
                }
            }
            .pointerInput(isEnabled, containerHeightPx, thumbHeightPx) {
                if (!isEnabled) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val usableHeight = (containerHeightPx - thumbHeightPx).coerceAtLeast(1f)
                        val newValue = (1f - ((offset.y - thumbHeightPx / 2f) / usableHeight)).coerceIn(0f, 1f)
                        localValue = newValue
                        currentOnValueChange(newValue)
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val usableHeight = (containerHeightPx - thumbHeightPx).coerceAtLeast(1f)
                        val newValue = (1f - ((change.position.y - thumbHeightPx / 2f) / usableHeight)).coerceIn(0f, 1f)
                        localValue = newValue
                        currentOnValueChange(newValue)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // ================= 0. dB REFERENCE TICKS AND TEXT (0 dB & -∞) =================
        if (showTicks) {
            val tickColor = Color(0x334E556A)
            val tickPositions = listOf(0.20f, 0.50f, 0.75f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val usableH = (h - thumbHeightPx).coerceAtLeast(10f)

                tickPositions.forEach { frac ->
                    val y = thumbHeightPx / 2f + (1f - frac) * usableH
                    // Left tick mark
                    drawLine(
                        color = tickColor,
                        start = Offset(4.dp.toPx(), y),
                        end = Offset(w * 0.28f, y),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Right tick mark
                    drawLine(
                        color = tickColor,
                        start = Offset(w * 0.72f, y),
                        end = Offset(w - 4.dp.toPx(), y),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // dB Text labels
            Box(modifier = Modifier.fillMaxSize()) {
                // "0 dB" label
                Text(
                    text = "0 dB",
                    color = Color(0xFF6B7280),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 26.dp, end = 2.dp)
                )
                // "-∞" label
                Text(
                    text = "-∞",
                    color = Color(0xFF6B7280),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 14.dp, end = 2.dp)
                )
            }
        }

        // Calculate precise vertical position of thumb bonnet and its center
        val usableHeightPx = (containerHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val offsetYPx = ((1f - localValue.coerceIn(0f, 1f)) * usableHeightPx).roundToInt()
        val bonnetCenterYPx = offsetYPx + thumbHeightPx / 2f

        // ================= 1. PRECISION OBLONG RAIL & AUDIO REACTION =================
        val isAudioSoundActive = isEnabled && audioActivity > 0.015f
        val dynamicAura = auraColor.copy(alpha = 1.0f)

        Box(
            modifier = Modifier
                .width(trackWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            // Dark Oblong Slot Track
            Canvas(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight(0.96f)
            ) {
                val h = size.height
                val w = size.width
                val corner = CornerRadius(w / 2f, w / 2f)

                // 1. Dark Slot Cavity
                drawRoundRect(
                    color = Color(0xFF0A0C12),
                    topLeft = Offset(0f, 0f),
                    size = Size(w, h),
                    cornerRadius = corner
                )

                // 2. Stroke Border
                drawRoundRect(
                    color = Color(0xFF1B1F2C),
                    topLeft = Offset(0.5f, 0.5f),
                    size = Size(w - 1f, h - 1f),
                    cornerRadius = corner,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
            }

            // Central Track Color Gauge Line
            if (isEnabled) {
                Canvas(
                    modifier = Modifier
                        .width(4.5.dp)
                        .fillMaxHeight(0.96f)
                ) {
                    val h = size.height
                    val w = size.width
                    val railTopPx = (containerHeightPx - h) / 2f
                    val relativeBonnetY = (bonnetCenterYPx - railTopPx).coerceIn(3.dp.toPx(), h - 3.dp.toPx())
                    val activeHeight = (h - 3.dp.toPx()) - relativeBonnetY
                    val lineWidthPx = 4.dp.toPx()
                    val lineLeft = (w - lineWidthPx) / 2f
                    val lineCorner = CornerRadius(lineWidthPx / 2f, lineWidthPx / 2f)

                    if (activeHeight > 0f) {
                        // Saturated track color fill
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    dynamicAura.copy(alpha = 0.95f),
                                    dynamicAura.copy(alpha = 0.80f)
                                ),
                                startY = relativeBonnetY,
                                endY = h - 3.dp.toPx()
                            ),
                            topLeft = Offset(lineLeft, relativeBonnetY),
                            size = Size(lineWidthPx, activeHeight),
                            cornerRadius = lineCorner
                        )

                        // Audio reactive glow bloom when playing sound
                        if (isAudioSoundActive) {
                            val intensity = audioActivity.coerceIn(0f, 1f)
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        dynamicAura.copy(alpha = 0.1f * intensity),
                                        dynamicAura.copy(alpha = 0.7f * intensity),
                                        dynamicAura.copy(alpha = 0.1f * intensity)
                                    )
                                ),
                                topLeft = Offset(0f, relativeBonnetY),
                                size = Size(w, activeHeight),
                                cornerRadius = CornerRadius(w / 2f, w / 2f)
                            )
                        }
                    }
                }
            }
        }

        // ================= 2. FADER CAP (BONNET) 100% SVG EXACT =================
        // Smooth Matte Slate rectangle (34dp x 24dp, rx=8dp) with single glowing pill slot (14dp x 3dp, rx=1.5dp)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, offsetYPx) }
                .width(thumbWidth)
                .height(thumbHeight),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(8.dp), spotColor = Color.Black)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF383F50), // Top satin bevel
                                Color(0xFF222735), // Matte dark slate body
                                Color(0xFF181B24)  // Shadow bottom
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Color(0xFF2E3547),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // The SINGLE illuminated horizontal indicator slot from the SVG
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(dynamicAura)
                )
            }
        }
    }
}
