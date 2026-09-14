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
    auraColor: Color = Color(0xFF19EF71), // Ultra-bright neon green by default
    audioActivity: Float = 0f,
    isEnabled: Boolean = true,
    showTicks: Boolean = true,
    trackWidth: Dp = 16.dp,
    trackHeight: Dp = 220.dp,
    thumbWidth: Dp = 38.dp,
    thumbHeight: Dp = 51.dp,
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
        // ================= 0. dB REFERENCE TICKS AND TEXT (3 Levels: -24dB, -12dB, 0dB) =================
        if (showTicks) {
            val tickColor = Color(0x664E556A)
            // Exactly 3 cleanly spaced reference levels adapting smoothly to any height
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
                        start = Offset(4.dp.toPx(), y),
                        end = Offset(w * 0.26f, y),
                        strokeWidth = 1.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Right tick mark
                    drawLine(
                        color = tickColor,
                        start = Offset(w * 0.74f, y),
                        end = Offset(w - 4.dp.toPx(), y),
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
                    color = Color(0xFF6B7280),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 18.dp, end = 2.dp)
                )
                // "-∞" label
                Text(
                    text = "-∞",
                    color = Color(0xFF6B7280),
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

        // ================= 1. RAIL / TRACK AUTHENTIC PNG ELEMENT =================
        val isAudioSoundActive = isEnabled && audioActivity > 0.015f
        val dynamicAura = auraColor.copy(alpha = 1.0f)

        Box(
            modifier = Modifier
                .width(trackWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            // Slot background: Dark recessed groove
            Canvas(
                modifier = Modifier
                    .width(4.5.dp)
                    .fillMaxHeight(0.92f)
            ) {
                val h = size.height
                val w = size.width
                val railTopPx = (containerHeightPx - h) / 2f
                val relativeBonnetY = (bonnetCenterYPx - railTopPx).coerceIn(0f, h)

                // Top segment (above bonnet) - deep black/charcoal slot
                if (relativeBonnetY > 0f) {
                    drawRoundRect(
                        color = Color(0xFF0D0F14),
                        topLeft = Offset(0f, 0f),
                        size = Size(w, relativeBonnetY),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }

                // Bottom segment (below bonnet) - dark recessed slot base
                val bottomHeight = h - relativeBonnetY
                if (bottomHeight > 0f) {
                    drawRoundRect(
                        color = Color(0xFF141720),
                        topLeft = Offset(0f, relativeBonnetY),
                        size = Size(w, bottomHeight),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }

            // ================= BRILLIANT GLOWING NEON LINE (SHINES / BRILLE) =================
            // Visible at all times when track is enabled, dynamically pulses and blooms with audio
            if (isEnabled) {
                val baseAlpha = if (isAudioSoundActive) (0.6f + audioActivity.coerceIn(0f, 1f) * 0.4f) else 0.85f
                val bloomAlpha = if (isAudioSoundActive) (0.45f + audioActivity.coerceIn(0f, 1f) * 0.5f).coerceIn(0.45f, 0.95f) else 0.35f

                // 1. Wide ambient neon halo / bloom (radiates outward to create authentic shine)
                Canvas(
                    modifier = Modifier
                        .width(9.dp)
                        .fillMaxHeight(0.92f)
                ) {
                    val h = size.height
                    val w = size.width
                    val railTopPx = (containerHeightPx - h) / 2f
                    val relativeBonnetY = (bonnetCenterYPx - railTopPx).coerceIn(0f, h)
                    val activeHeight = h - relativeBonnetY
                    if (activeHeight > 0f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    dynamicAura.copy(alpha = 0f),
                                    dynamicAura.copy(alpha = bloomAlpha),
                                    dynamicAura.copy(alpha = 0f)
                                )
                            ),
                            topLeft = Offset(0f, relativeBonnetY),
                            size = Size(w, activeHeight),
                            cornerRadius = CornerRadius(4.dp.toPx())
                        )
                    }
                }

                // 2. Saturated neon body line with vertical gradient
                Canvas(
                    modifier = Modifier
                        .width(3.6.dp)
                        .fillMaxHeight(0.92f)
                ) {
                    val h = size.height
                    val w = size.width
                    val railTopPx = (containerHeightPx - h) / 2f
                    val relativeBonnetY = (bonnetCenterYPx - railTopPx).coerceIn(0f, h)
                    val activeHeight = h - relativeBonnetY
                    if (activeHeight > 0f) {
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = baseAlpha),
                                    dynamicAura.copy(alpha = baseAlpha),
                                    dynamicAura.copy(alpha = (baseAlpha * 0.90f).coerceIn(0.5f, 1f))
                                ),
                                startY = relativeBonnetY,
                                endY = h
                            ),
                            topLeft = Offset(0f, relativeBonnetY),
                            size = Size(w, activeHeight),
                            cornerRadius = CornerRadius(1.8.dp.toPx())
                        )
                    }
                }

                // 3. Incandescent white-hot core line down the center (gives electric laser / neon tube brilliance)
                Canvas(
                    modifier = Modifier
                        .width(1.4.dp)
                        .fillMaxHeight(0.92f)
                ) {
                    val h = size.height
                    val w = size.width
                    val railTopPx = (containerHeightPx - h) / 2f
                    val relativeBonnetY = (bonnetCenterYPx - railTopPx).coerceIn(0f, h)
                    val activeHeight = h - relativeBonnetY
                    if (activeHeight > 0f) {
                        val coreAlpha = if (isAudioSoundActive) 0.95f else 0.82f
                        drawRoundRect(
                            color = Color.White.copy(alpha = coreAlpha),
                            topLeft = Offset(0f, relativeBonnetY),
                            size = Size(w, activeHeight),
                            cornerRadius = CornerRadius(0.7.dp.toPx())
                        )
                    }
                }
            }

            // Authentic Rail PNG element: Dark metallic contour above bonnet
            Image(
                painter = painterResource(id = R.drawable.ic_fader_track),
                contentDescription = "Fader Track Rail Dark",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(top = 0f, bottom = bonnetCenterYPx, left = 0f, right = size.width) {
                            this@drawWithContent.drawContent()
                        }
                    },
                colorFilter = ColorFilter.tint(Color(0xFF1E212B), androidx.compose.ui.graphics.BlendMode.SrcIn)
            )

            // Authentic Rail PNG element: Sleek metallic lighter gray contour below bonnet
            Image(
                painter = painterResource(id = R.drawable.ic_fader_track),
                contentDescription = "Fader Track Rail Light",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(top = bonnetCenterYPx, bottom = size.height, left = 0f, right = size.width) {
                            this@drawWithContent.drawContent()
                        }
                    },
                colorFilter = ColorFilter.tint(Color(0xFF3E4554), androidx.compose.ui.graphics.BlendMode.SrcIn)
            )
        }

        // ================= 2. CAP / THUMB BONNET AUTHENTIC PNG ELEMENT =================
        // Lightened ColorMatrix: subtly lighter grey while preserving 100% texture, ridges, and bevels
        val lightenedBonnetMatrix = remember {
            ColorMatrix(
                floatArrayOf(
                    1.24f, 0f, 0f, 0f, 24f,
                    0f, 1.24f, 0f, 0f, 24f,
                    0f, 0f, 1.26f, 0f, 24f,
                    0f, 0f, 0f, 1.0f, 0f
                )
            )
        }

        // Fader Thumb Bonnet (exact PNG with baked-in drop shadow, bevels & colors)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, offsetYPx) }
                .width(thumbWidth)
                .height(thumbHeight),
            contentAlignment = Alignment.Center
        ) {
            // Authentic Bonnet PNG element with lighter grey metallic color matrix
            Image(
                painter = painterResource(id = R.drawable.ic_fader_thumb),
                contentDescription = "Fader Thumb Cap",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                colorFilter = ColorFilter.colorMatrix(lightenedBonnetMatrix)
            )

            // Central neon indicator slit following dynamic fader aura color
            Box(
                modifier = Modifier
                    .width(thumbWidth * 0.44f)
                    .height(3.2.dp)
                    .clip(RoundedCornerShape(1.6.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                dynamicAura.copy(alpha = 0.85f),
                                Color.White.copy(alpha = 0.95f),
                                dynamicAura.copy(alpha = 0.85f)
                            )
                        )
                    )
            )
        }
    }
}
