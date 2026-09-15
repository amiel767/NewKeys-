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

        // ================= 1. SUNDAYKEYS PRECISION RAIL & AUDIO-REACTIVE LUMINOUS NEON =================
        val isAudioSoundActive = isEnabled && audioActivity > 0.015f
        val dynamicAura = auraColor.copy(alpha = 1.0f)

        Box(
            modifier = Modifier
                .width(trackWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            // ================= REALISTIC OBLONG CAPSULE RAIL CHASSIS (SUNDAYKEYS EXACT AS IMAGE) =================
            Canvas(
                modifier = Modifier
                    .width(13.dp)
                    .fillMaxHeight(0.96f)
            ) {
                val h = size.height
                val w = size.width
                // Fully rounded semi-circular caps top & bottom
                val corner = CornerRadius(w / 2f, w / 2f)

                // 1. Outer Satin Slate-Grey Chassis Body (Smooth Capsule as in image)
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF343B4E), // Left subtle light bevel
                            Color(0xFF242A38), // Matte dark slate body
                            Color(0xFF202532), // Core slate
                            Color(0xFF2D3444)  // Right edge
                        )
                    ),
                    topLeft = Offset(0f, 0f),
                    size = Size(w, h),
                    cornerRadius = corner
                )

                // 2. Smooth Machined Perimeter Bevel (Soft Satin Chamfer border)
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF4C5670), // Crisp top-left light highlight
                            Color(0xFF363E52),
                            Color(0xFF282F40),
                            Color(0xFF454F68)  // Right rim edge
                        )
                    ),
                    topLeft = Offset(0.5f, 0.5f),
                    size = Size(w - 1f, h - 1f),
                    cornerRadius = corner,
                    style = Stroke(width = 1.2.dp.toPx())
                )

                // 3. Inner Recessed Mechanical Slot Groove (Oblong Dark Channel)
                val slotWidthPx = 4.2.dp.toPx()
                val slotLeft = (w - slotWidthPx) / 2f
                val slotTop = 4.dp.toPx()
                val slotHeight = h - (slotTop * 2)
                val slotCorner = CornerRadius(slotWidthPx / 2f, slotWidthPx / 2f)

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF10131B), // Dark recessed top
                            Color(0xFF161A24), // Center depth
                            Color(0xFF12151E)  // Recessed bottom
                        )
                    ),
                    topLeft = Offset(slotLeft, slotTop),
                    size = Size(slotWidthPx, slotHeight),
                    cornerRadius = slotCorner
                )

                // Subtle inner shadow stroke around the groove
                drawRoundRect(
                    color = Color(0x66000000),
                    topLeft = Offset(slotLeft, slotTop),
                    size = Size(slotWidthPx, slotHeight),
                    cornerRadius = slotCorner,
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }

            // ================= CENTRAL NEON LINE: GLOWING IN THE GROOVE AS IN IMAGE =================
            // When audio is played, a radiant luminous glow layer shines on top in sync with volume
            if (isEnabled) {
                // 1. BASELINE NEON VIOLET / TRACK LINE (Runs in the slot below thumb to the bottom)
                Canvas(
                    modifier = Modifier
                        .width(4.2.dp)
                        .fillMaxHeight(0.96f)
                ) {
                    val h = size.height
                    val w = size.width
                    val railTopPx = (containerHeightPx - h) / 2f
                    val relativeBonnetY = (bonnetCenterYPx - railTopPx).coerceIn(4.dp.toPx(), h - 4.dp.toPx())
                    val activeHeight = (h - 4.dp.toPx()) - relativeBonnetY
                    val lineWidthPx = 2.2.dp.toPx()
                    val lineLeft = (w - lineWidthPx) / 2f
                    val lineCorner = CornerRadius(lineWidthPx / 2f, lineWidthPx / 2f)

                    if (activeHeight > 0f) {
                        // Soft slot cavity neon glow diffusion
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    dynamicAura.copy(alpha = 0.15f),
                                    dynamicAura.copy(alpha = 0.50f),
                                    dynamicAura.copy(alpha = 0.15f)
                                )
                            ),
                            topLeft = Offset(0f, relativeBonnetY),
                            size = Size(w, activeHeight),
                            cornerRadius = CornerRadius(w / 2f, w / 2f)
                        )

                        // Saturated core neon line (Crisp violet / track color)
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    dynamicAura.copy(alpha = 0.98f),
                                    dynamicAura.copy(alpha = 0.90f)
                                ),
                                startY = relativeBonnetY,
                                endY = h - 4.dp.toPx()
                            ),
                            topLeft = Offset(lineLeft, relativeBonnetY),
                            size = Size(lineWidthPx, activeHeight),
                            cornerRadius = lineCorner
                        )
                    }
                }

                // 2. AUDIO-REACTIVE LUMINOUS LAYER (Brilliant glow that pulses when sound is active)
                if (isAudioSoundActive) {
                    val intensity = audioActivity.coerceIn(0f, 1f)
                    val glowBloomAlpha = (0.50f + intensity * 0.50f).coerceIn(0.50f, 1.0f)
                    val coreLaserAlpha = (0.85f + intensity * 0.15f).coerceIn(0.85f, 1.0f)

                    // Layer A: Wide Radiant Neon Bloom Layer
                    Canvas(
                        modifier = Modifier
                            .width(18.dp)
                            .fillMaxHeight(0.96f)
                    ) {
                        val h = size.height
                        val w = size.width
                        val railTopPx = (containerHeightPx - h) / 2f
                        val relativeBonnetY = (bonnetCenterYPx - railTopPx).coerceIn(4.dp.toPx(), h - 4.dp.toPx())
                        val activeHeight = (h - 4.dp.toPx()) - relativeBonnetY

                        if (activeHeight > 0f) {
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        dynamicAura.copy(alpha = 0f),
                                        dynamicAura.copy(alpha = glowBloomAlpha * 0.85f),
                                        dynamicAura.copy(alpha = 0f)
                                    )
                                ),
                                topLeft = Offset(0f, relativeBonnetY),
                                size = Size(w, activeHeight),
                                cornerRadius = CornerRadius(6.dp.toPx())
                            )
                        }
                    }

                    // Layer B: Intense Saturated Core Glow
                    Canvas(
                        modifier = Modifier
                            .width(6.dp)
                            .fillMaxHeight(0.96f)
                    ) {
                        val h = size.height
                        val w = size.width
                        val railTopPx = (containerHeightPx - h) / 2f
                        val relativeBonnetY = (bonnetCenterYPx - railTopPx).coerceIn(4.dp.toPx(), h - 4.dp.toPx())
                        val activeHeight = (h - 4.dp.toPx()) - relativeBonnetY

                        if (activeHeight > 0f) {
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = coreLaserAlpha),
                                        dynamicAura.copy(alpha = coreLaserAlpha),
                                        dynamicAura.copy(alpha = 0.95f)
                                    ),
                                    startY = relativeBonnetY,
                                    endY = h - 4.dp.toPx()
                                ),
                                topLeft = Offset(0f, relativeBonnetY),
                                size = Size(w, activeHeight),
                                cornerRadius = CornerRadius(3.dp.toPx())
                            )
                        }
                    }

                    // Layer C: Incandescent White-Hot Laser Filament (Electric brilliance)
                    Canvas(
                        modifier = Modifier
                            .width(2.2.dp)
                            .fillMaxHeight(0.96f)
                    ) {
                        val h = size.height
                        val w = size.width
                        val railTopPx = (containerHeightPx - h) / 2f
                        val relativeBonnetY = (bonnetCenterYPx - railTopPx).coerceIn(4.dp.toPx(), h - 4.dp.toPx())
                        val activeHeight = (h - 4.dp.toPx()) - relativeBonnetY

                        if (activeHeight > 0f) {
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.95f),
                                topLeft = Offset(0f, relativeBonnetY),
                                size = Size(w, activeHeight),
                                cornerRadius = CornerRadius(1.1.dp.toPx())
                            )
                        }
                    }
                }
            }
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
