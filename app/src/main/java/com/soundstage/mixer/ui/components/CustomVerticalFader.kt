package com.soundstage.mixer.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
    trackWidth: Dp = 18.dp,
    trackHeight: Dp = 205.dp,
    thumbWidth: Dp = 51.dp,
    thumbHeight: Dp = 64.dp,
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

        // ================= 1. PRECISION CODE-DRAWN FADER RAIL =================
        val isAudioSoundActive = isEnabled && audioActivity > 0.015f
        val dynamicAura = auraColor.copy(alpha = 1.0f)

        val trackWidthPx = with(density) { trackWidth.toPx() }
        val railScale = trackWidthPx / 86f
        val grooveInsetYPx = 36f * railScale
        val grooveUsableHeightPx = containerHeightPx - 2f * grooveInsetYPx

        val calculatedLevel = if (!isEnabled || grooveUsableHeightPx <= 0f) {
            0f
        } else {
            (((containerHeightPx - grooveInsetYPx) - bonnetCenterYPx) / grooveUsableHeightPx).coerceIn(0f, 1f)
        }

        val targetGlow = if (isEnabled) audioActivity.coerceIn(0f, 1f) else 0f
        val animatedGlow by animateFloatAsState(
            targetValue = targetGlow,
            animationSpec = if (targetGlow > 0.05f) {
                tween(durationMillis = 40)
            } else {
                tween(durationMillis = 350, easing = FastOutSlowInEasing)
            },
            label = "faderRailGlow"
        )

        Box(
            modifier = Modifier
                .width(trackWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            FaderRail(
                accent = auraColor,
                modifier = Modifier
                    .width(trackWidth)
                    .fillMaxHeight(),
                level = calculatedLevel,
                glow = animatedGlow
            )
        }

        // ================= 2. CAP / THUMB BONNET AUTHENTIC PNG ELEMENT (+10% SIZE, SUBTLY LIGHTENED) =================
        val lightenedThumbMatrix = remember {
            ColorMatrix(
                floatArrayOf(
                    1.18f, 0f, 0f, 0f, 16f,
                    0f, 1.18f, 0f, 0f, 16f,
                    0f, 0f, 1.20f, 0f, 18f,
                    0f, 0f, 0f, 1.0f, 0f
                )
            )
        }

        val slotCoreAlpha = if (isAudioSoundActive) 1.0f else 0.88f
        val slotCenterColor = if (isAudioSoundActive && audioActivity > 0.05f) {
            Color.White.copy(alpha = (0.70f + audioActivity.coerceIn(0f, 1f) * 0.25f).coerceIn(0.70f, 0.95f))
        } else {
            dynamicAura.copy(alpha = 0.92f)
        }

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
            // 1. Exact slot fill backing (19.0dp x 3.8dp, offset Y -1.7dp) extending downwards to seal bottom cavity
            Box(
                modifier = Modifier
                    .offset(y = (-1.7).dp)
                    .width(19.0.dp)
                    .height(3.8.dp)
                    .clip(RoundedCornerShape(1.4.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                dynamicAura.copy(alpha = slotCoreAlpha * 0.82f),
                                slotCenterColor,
                                dynamicAura.copy(alpha = slotCoreAlpha * 0.82f)
                            )
                        )
                    )
            )

            // 2. Authentic Bonnet PNG element with natural transparent slot cutout
            Image(
                painter = painterResource(id = R.drawable.ic_fader_thumb),
                contentDescription = "Fader Thumb Cap",
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.colorMatrix(lightenedThumbMatrix),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
