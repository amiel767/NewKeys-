package com.soundstage.mixer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.R
import kotlin.math.roundToInt

/**
 * CustomVerticalFader Composable:
 * - Direct integration of user PNG assets (ic_fader_track.png for rail, ic_fader_thumb.png for knob)
 * - Exact proportions matching user reference mockup (thumb width 42dp approx 3x track rail width 14dp)
 * - Native bottom shadow on knob preserved untouched from PNG
 * - Dynamic color LED bar replacing violet area with current track dynamic palette
 */
@Composable
fun CustomVerticalFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    auraColor: Color = Color(0xFFF6C445),
    audioActivity: Float = 0f,
    isEnabled: Boolean = true,
    showTicks: Boolean = true,
    trackWidth: Dp = 25.dp,
    trackHeight: Dp = 220.dp,
    thumbWidth: Dp = 58.dp,
    thumbHeight: Dp = 60.dp,
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
            .heightIn(min = 60.dp, max = trackHeight)
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
                        start = Offset(2.dp.toPx(), y),
                        end = Offset(w * 0.22f, y),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Right tick mark
                    drawLine(
                        color = tickColor,
                        start = Offset(w * 0.78f, y),
                        end = Offset(w - 2.dp.toPx(), y),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // dB Text labels
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "0 dB",
                    color = Color(0xFF6B7280),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 28.dp, end = 2.dp)
                )
                Text(
                    text = "-∞",
                    color = Color(0xFF4B5563),
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 12.dp, end = 2.dp)
                )
            }
        }

        // Calculate precise vertical position of thumb bonnet and its center
        val usableHeightPx = (containerHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val offsetYPx = ((1f - localValue.coerceIn(0f, 1f)) * usableHeightPx).roundToInt()
        val bonnetCenterYPx = offsetYPx + thumbHeightPx / 2f

        // ================= 1. DIRECT PNG FADER RAIL (ic_fader_track.png) =================
        val isAudioSoundActive = isEnabled && audioActivity > 0.015f
        val dynamicAura = auraColor.copy(alpha = 1.0f)

        // Clean exposure gain for the rail PNG so metal graduations and groove are clearly visible
        val trackColorFilter = remember {
            val offset = 48f
            val matrix = ColorMatrix(
                floatArrayOf(
                    1.22f, 0f, 0f, 0f, offset,
                    0f, 1.22f, 0f, 0f, offset,
                    0f, 0f, 1.22f, 0f, offset,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            ColorFilter.colorMatrix(matrix)
        }

        Box(
            modifier = Modifier
                .width(trackWidth)
                .fillMaxHeight(0.96f),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_fader_track),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                colorFilter = trackColorFilter
            )

            // Dynamic LED Line in the central groove of the rail
            if (isEnabled) {
                Canvas(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                ) {
                    val h = size.height
                    val w = size.width
                    val railTopPx = (containerHeightPx - h) / 2f
                    // Clamp strictly within physical groove stops to never exceed the bottom/top
                    val grooveTopPx = 10.dp.toPx()
                    val grooveBottomPx = h - 10.dp.toPx()
                    val relativeBonnetY = (bonnetCenterYPx - railTopPx).coerceIn(grooveTopPx, grooveBottomPx)
                    val activeHeight = grooveBottomPx - relativeBonnetY
                    val lineWidthPx = 3.2.dp.toPx()
                    val lineLeft = (w - lineWidthPx) / 2f

                    if (activeHeight > 0f) {
                        // Sound-reactive outer glow bloom when audio is playing
                        if (isAudioSoundActive) {
                            val glowWidthPx = 5.2.dp.toPx()
                            val glowLeft = (w - glowWidthPx) / 2f
                            drawRoundRect(
                                color = dynamicAura.copy(alpha = (0.45f + audioActivity * 0.45f).coerceIn(0.2f, 0.85f)),
                                topLeft = Offset(glowLeft, relativeBonnetY),
                                size = Size(glowWidthPx, activeHeight),
                                cornerRadius = CornerRadius(glowWidthPx / 2f, glowWidthPx / 2f)
                            )
                        }

                        // Core brilliant LED line
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    if (isAudioSoundActive) Color.White.copy(alpha = 0.95f) else dynamicAura.copy(alpha = 0.95f),
                                    dynamicAura.copy(alpha = if (isAudioSoundActive) 1.0f else 0.85f)
                                ),
                                startY = relativeBonnetY,
                                endY = grooveBottomPx
                            ),
                            topLeft = Offset(lineLeft, relativeBonnetY),
                            size = Size(lineWidthPx, activeHeight),
                            cornerRadius = CornerRadius(lineWidthPx / 2f, lineWidthPx / 2f)
                        )
                    }
                }
            }
        }

        // ================= 2. DIRECT PNG FADER KNOB (ic_fader_thumb.png) =================
        // Lighten the dark knob PNG cleanly so brushed metal and bevels are visible without darkness
        val knobColorFilter = remember {
            val offset = 44f
            val matrix = ColorMatrix(
                floatArrayOf(
                    1.22f, 0f, 0f, 0f, offset,
                    0f, 1.22f, 0f, 0f, offset,
                    0f, 0f, 1.22f, 0f, offset,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            ColorFilter.colorMatrix(matrix)
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, offsetYPx) }
                .width(thumbWidth)
                .height(thumbHeight),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_fader_thumb),
                contentDescription = "Fader Handle",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = knobColorFilter
            )

            // Dynamic LED Line on the knob's indicator slit
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.50f)
                    .height(2.5.dp)
                    .offset(y = (-1.0).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (isAudioSoundActive) Color.White.copy(alpha = 0.95f) else dynamicAura
                    )
            )
        }
    }
}
