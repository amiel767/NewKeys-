package com.soundstage.mixer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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
 * CustomVerticalFader Composable faithful to user reference image `fader_visuel_cible.png`:
 * - Dark background (#1B1E2B)
 * - Fader slot card (#25293A)
 * - Rail & dB reference ticks (#4E556A)
 * - Ultra-bright neon green/aura line (#19EF71)
 * - Thumb cap indicator glowing with audio volume / aura
 */
@Composable
fun CustomVerticalFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    auraColor: Color = Color(0xFFE58929),
    audioActivity: Float = 0f,
    isEnabled: Boolean = true,
    showTicks: Boolean = true,
    trackWidth: Dp = 20.dp,
    trackHeight: Dp = 220.dp,
    thumbWidth: Dp = 48.dp,
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
            .fillMaxHeight()
            .heightIn(min = 90.dp, max = trackHeight)
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
        // ================= 0. dB REFERENCE TICKS AND TEXT (3 Levels: -24dB, -12dB, 0dB) =================
        if (showTicks) {
            val tickColor = Color(0x554E556A)
            val tickPositions = listOf(0.20f, 0.55f, 0.88f)

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
                        strokeWidth = 1.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Right tick mark
                    drawLine(
                        color = tickColor,
                        start = Offset(w * 0.78f, y),
                        end = Offset(w - 2.dp.toPx(), y),
                        strokeWidth = 1.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // dB Text labels
            Box(modifier = Modifier.fillMaxSize()) {
                // "0 dB" label
                Text(
                    text = "0 dB",
                    color = Color(0xFF7A8394),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 18.dp, end = 2.dp)
                )
                // "-∞" label
                Text(
                    text = "-∞",
                    color = Color(0xFF7A8394),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 12.dp, end = 4.dp)
                )
            }
        }

        // Calculate precise vertical position of thumb bonnet and its center
        val usableHeightPx = (containerHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val offsetYPx = ((1f - localValue.coerceIn(0f, 1f)) * usableHeightPx).roundToInt()
        val bonnetCenterYPx = offsetYPx + thumbHeightPx / 2f

        // ================= 1. SUNDAYKEYS PRECISION RAIL & AUDIO-REACTIVE LUMINOUS NEON =================
        val isAudioSoundActive = isEnabled && audioActivity > 0.015f
        val dynamicAura = auraColor.copy(alpha = 1.0f)

        val lightenedTrackMatrix = remember {
            ColorMatrix(
                floatArrayOf(
                    1.40f, 0f, 0f, 0f, 32f,
                    0f, 1.40f, 0f, 0f, 32f,
                    0f, 0f, 1.45f, 0f, 36f,
                    0f, 0f, 0f, 1.0f, 0f
                )
            )
        }

        Box(
            modifier = Modifier
                .width(trackWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            // ================= REALISTIC OBLONG CAPSULE RAIL CHASSIS (LIGHTENED SLATE GROOVE) =================
            Image(
                painter = painterResource(id = R.drawable.ic_fader_track),
                contentDescription = "Fader Rail Track",
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.colorMatrix(lightenedTrackMatrix),
                modifier = Modifier
                    .width(trackWidth)
                    .fillMaxHeight()
            )

            // ================= CENTRAL NEON LINE: GLOWING IN THE GROOVE STRICTLY TERMINATED AT BOTTOM =================
            if (isEnabled) {
                // 1. BASELINE TRACK LINE (Runs in the slot below thumb to the bottom inner boundary)
                Canvas(
                    modifier = Modifier
                        .width(trackWidth)
                        .fillMaxHeight()
                ) {
                    val h = size.height
                    val w = size.width
                    val bottomInsetPx = minOf(h * 0.075f, 16.dp.toPx()).coerceAtLeast(10.dp.toPx())
                    val railTopPx = (containerHeightPx - h) / 2f
                    val relativeBonnetY = (bonnetCenterYPx - railTopPx).coerceIn(4.dp.toPx(), h - bottomInsetPx)
                    val activeHeight = (h - bottomInsetPx) - relativeBonnetY
                    val lineWidthPx = 2.4.dp.toPx()
                    val lineLeft = (w - lineWidthPx) / 2f
                    val lineCorner = CornerRadius(lineWidthPx / 2f, lineWidthPx / 2f)

                    if (activeHeight > 0f) {
                        // ALWAYS VISIBLE EVEN AT REST: distinct rich track color in the groove; glows intensely with audio
                        val idleGrooveAlpha = if (isAudioSoundActive) 0.30f else 0.18f
                        val coreAlpha = if (isAudioSoundActive) 1.0f else 0.65f

                        // Soft slot cavity tint
                        drawRoundRect(
                            color = dynamicAura.copy(alpha = idleGrooveAlpha),
                            topLeft = Offset(lineLeft - 1.dp.toPx(), relativeBonnetY),
                            size = Size(lineWidthPx + 2.dp.toPx(), activeHeight),
                            cornerRadius = lineCorner
                        )

                        // Core slot line
                        drawRoundRect(
                            color = dynamicAura.copy(alpha = coreAlpha),
                            topLeft = Offset(lineLeft, relativeBonnetY),
                            size = Size(lineWidthPx, activeHeight),
                            cornerRadius = lineCorner
                        )
                    }
                }

                // 2. AUDIO-REACTIVE LUMINOUS LAYER (Pulsing glow ONLY when audio is actively sounding)
                if (isAudioSoundActive && audioActivity > 0.02f) {
                    val intensity = audioActivity.coerceIn(0f, 1f)
                    val glowBloomAlpha = (0.45f + intensity * 0.55f).coerceIn(0.45f, 1.0f)
                    val coreLaserAlpha = (0.85f + intensity * 0.15f).coerceIn(0.85f, 1.0f)

                    // Layer A: Radiant Bloom
                    Canvas(
                        modifier = Modifier
                            .width(trackWidth)
                            .fillMaxHeight()
                    ) {
                        val h = size.height
                        val w = size.width
                        val bottomInsetPx = minOf(h * 0.075f, 16.dp.toPx()).coerceAtLeast(10.dp.toPx())
                        val railTopPx = (containerHeightPx - h) / 2f
                        val relativeBonnetY = (bonnetCenterYPx - railTopPx).coerceIn(4.dp.toPx(), h - bottomInsetPx)
                        val activeHeight = (h - bottomInsetPx) - relativeBonnetY

                        if (activeHeight > 0f) {
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        dynamicAura.copy(alpha = 0f),
                                        dynamicAura.copy(alpha = glowBloomAlpha * 0.70f),
                                        dynamicAura.copy(alpha = 0f)
                                    )
                                ),
                                topLeft = Offset(0f, relativeBonnetY),
                                size = Size(w, activeHeight),
                                cornerRadius = CornerRadius(7.dp.toPx())
                            )
                        }
                    }

                    // Layer B: Intense Saturated Core Glow
                    Canvas(
                        modifier = Modifier
                            .width(6.dp)
                            .fillMaxHeight()
                    ) {
                        val h = size.height
                        val w = size.width
                        val bottomInsetPx = minOf(h * 0.075f, 16.dp.toPx()).coerceAtLeast(10.dp.toPx())
                        val railTopPx = (containerHeightPx - h) / 2f
                        val relativeBonnetY = (bonnetCenterYPx - railTopPx).coerceIn(4.dp.toPx(), h - bottomInsetPx)
                        val activeHeight = (h - bottomInsetPx) - relativeBonnetY

                        if (activeHeight > 0f) {
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = coreLaserAlpha),
                                        dynamicAura.copy(alpha = coreLaserAlpha),
                                        dynamicAura.copy(alpha = 0.90f)
                                    ),
                                    startY = relativeBonnetY,
                                    endY = h - bottomInsetPx
                                ),
                                topLeft = Offset(0f, relativeBonnetY),
                                size = Size(w, activeHeight),
                                cornerRadius = CornerRadius(3.dp.toPx())
                            )
                        }
                    }

                    // Layer C: Incandescent White-Hot Laser Filament
                    Canvas(
                        modifier = Modifier
                            .width(2.5.dp)
                            .fillMaxHeight()
                    ) {
                        val h = size.height
                        val w = size.width
                        val bottomInsetPx = minOf(h * 0.075f, 16.dp.toPx()).coerceAtLeast(10.dp.toPx())
                        val railTopPx = (containerHeightPx - h) / 2f
                        val relativeBonnetY = (bonnetCenterYPx - railTopPx).coerceIn(4.dp.toPx(), h - bottomInsetPx)
                        val activeHeight = (h - bottomInsetPx) - relativeBonnetY

                        if (activeHeight > 0f) {
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.92f),
                                topLeft = Offset(0f, relativeBonnetY),
                                size = Size(w, activeHeight),
                                cornerRadius = CornerRadius(1.2.dp.toPx())
                            )
                        }
                    }
                }
            }
        }

        // ================= 2. CAP / THUMB BONNET AUTHENTIC PNG ELEMENT =================
        val lightenedBonnetMatrix = remember {
            ColorMatrix(
                floatArrayOf(
                    1.28f, 0f, 0f, 0f, 26f,
                    0f, 1.28f, 0f, 0f, 26f,
                    0f, 0f, 1.30f, 0f, 28f,
                    0f, 0f, 0f, 1.0f, 0f
                )
            )
        }

        // Fader Thumb Bonnet (enlarged, natural proportions, directly touchable)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, offsetYPx) }
                .width(thumbWidth)
                .height(thumbHeight)
                .pointerInput(isEnabled, containerHeightPx, thumbHeightPx) {
                if (!isEnabled) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val usableHeight = (containerHeightPx - thumbHeightPx).coerceAtLeast(1f)
                        val deltaFraction = dragAmount / usableHeight
                        val newValue = (localValue - deltaFraction).coerceIn(0f, 1f)
                        localValue = newValue
                        currentOnValueChange(newValue)
                    }
                )
            },
            contentAlignment = Alignment.Center
        ) {
            // 1. Center backing matching the track's glowing color to fill the transparent center cutout
            Box(
                modifier = Modifier
                    .width(thumbWidth * 0.58f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                dynamicAura.copy(alpha = 0.85f),
                                dynamicAura,
                                dynamicAura.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // 2. Authentic Bonnet PNG element with lighter grey metallic color matrix
            Image(
                painter = painterResource(id = R.drawable.ic_fader_thumb),
                contentDescription = "Fader Thumb Cap",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                colorFilter = ColorFilter.colorMatrix(lightenedBonnetMatrix)
            )

            // 3. Central neon indicator slit: bright and vivid over the groove
            Box(
                modifier = Modifier
                    .width(thumbWidth * 0.52f)
                    .height(7.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                dynamicAura.copy(alpha = 0.90f),
                                Color.White,
                                dynamicAura.copy(alpha = 0.90f)
                            )
                        )
                    )
            )
        }
    }
}
