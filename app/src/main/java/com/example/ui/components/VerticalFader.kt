package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TrackChannel
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * VerticalTrackChannel designed faithfully to the provided Excalidraw specifications:
 *
 * For Regular Tracks (1..8):
 * 1. Top: Display Box with Soundfont / Patch Title (.sf2)
 * 2. Under Display: Left = Pan Rotary Knob, Right = M/S Button (Mute/Solo)
 * 3. Middle: Vertical Fader with 3D Realistic Cap and Stereo VU-Meter
 * 4. Bottom: Left = FX Button (Cyan outline), Right = Power Button (Magenta ⏻)
 *
 * For Master Track:
 * 1. Top: Master Display Box (Cyan text "MASTER")
 * 2. Middle: Vertical Fader with 3D Realistic Cap and Master Stereo VU-Meter
 * 3. Bottom inside Master Track: FX Button (Orange "FX")
 */
@Composable
fun VerticalTrackChannel(
    track: TrackChannel,
    onVolumeChange: (Float) -> Unit,
    onPowerToggle: () -> Unit = {},
    onPanChange: (Float) -> Unit = {},
    onMuteSoloClick: () -> Unit = {},
    onTrackNameClick: () -> Unit = {},
    onFxClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isMaster = track.isMaster
    val isEnabled = track.isEnabled

    val cardBrush = if (isMaster) {
        Brush.linearGradient(
            colors = listOf(Color(0xFF281335), Color(0xFF1B0C24), Color(0xFF13081A))
        )
    } else {
        if (isEnabled) {
            Brush.linearGradient(
                colors = listOf(Color(0xFF252532), Color(0xFF1B1B24), Color(0xFF14141B))
            )
        } else {
            Brush.linearGradient(
                colors = listOf(Color(0xFF17171F), Color(0xFF101015))
            )
        }
    }

    val borderColor = if (isMaster) {
        Color(0x66D946EF)
    } else if (isEnabled) {
        if (track.isSolo) SoloAmber.copy(alpha = 0.6f) else if (track.isMuted) MuteRed.copy(alpha = 0.5f) else Color(0x22FFFFFF)
    } else {
        Color(0x12FFFFFF)
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBrush)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 4.dp, vertical = 5.dp)
            .testTag("track_${track.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ================= 1. TOP DISPLAY BOX =================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(
                    if (isMaster) Color(0xFF0F3B4A) else if (isEnabled) Color(0xFF0F3B4A) else Color(0x14FFFFFF)
                )
                .border(
                    1.dp,
                    if (isMaster) NeonCyan else if (isEnabled) NeonCyan.copy(alpha = 0.8f) else Color(0x22FFFFFF),
                    RoundedCornerShape(7.dp)
                )
                .clickable { if (!isMaster) onTrackNameClick() },
            contentAlignment = Alignment.Center
        ) {
            val displayName = if (isMaster) {
                "MASTER"
            } else {
                track.patchName.ifEmpty { track.soundfontName.ifEmpty { "-" } }
            }

            Text(
                text = displayName,
                fontSize = if (isMaster) 9.5.sp else 8.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isEnabled || isMaster) NeonCyanLight else TextDim2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ================= 2. KNOB & M/S ROW (Regular Tracks) =================
        if (!isMaster) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .padding(horizontal = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Pan Rotary Knob (Slightly larger, perfectly centered)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    MicroPanKnob(
                        pan = track.pan,
                        onPanChange = onPanChange,
                        isEnabled = isEnabled
                    )
                }

                Spacer(modifier = Modifier.width(3.dp))

                // Right: M/S (Mute/Solo) Square Button
                val isSolo = track.isSolo
                val isMuted = track.isMuted

                val btnBg by animateColorAsState(
                    targetValue = when {
                        isSolo -> SoloAmber
                        isMuted -> Color(0xFFE11D48)
                        else -> Color(0xFFE11D48).copy(alpha = 0.25f)
                    },
                    animationSpec = tween(120),
                    label = "mute_solo_bg"
                )
                val btnText = when {
                    isSolo -> "S"
                    isMuted -> "M"
                    else -> "M/S"
                }
                val btnTextColor = when {
                    isSolo -> Color(0xFF201300)
                    isMuted -> Color.White
                    else -> Color(0xFFFF85A1)
                }

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(btnBg)
                        .border(
                            1.dp,
                            if (isSolo) SoloAmber else if (isMuted) Color(0xFFFF5277) else Color(0x4DE11D48),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onMuteSoloClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = btnText,
                        fontSize = if (btnText.length > 1) 7.5.sp else 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = btnTextColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // ================= 3. VERTICAL FADER WITH 3D CAP & VU-METER =================
        FaderSliderWithVuMeter(
            value = track.volume,
            peakMeterL = track.peakMeterL,
            peakMeterR = track.peakMeterR,
            onValueChange = onVolumeChange,
            isMaster = isMaster,
            isEnabled = isEnabled,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        // ================= 4. BOTTOM ACTION BUTTONS =================
        if (!isMaster) {
            // Regular Track: Left = FX button (Cyan), Right = Power button (Magenta)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // FX Button (Cyan outlined box with bold text)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(23.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0E2833))
                        .border(1.2.dp, NeonCyan, RoundedCornerShape(6.dp))
                        .clickable { onFxClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "FX",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonCyan
                    )
                }

                Spacer(modifier = Modifier.width(3.dp))

                // Power Icon Button (Magenta box with ⏻)
                val powerBg = if (isEnabled) Color(0xFFD946EF) else Color(0x26D946EF)
                val powerTextColor = if (isEnabled) Color.White else Color(0xFFF0ABFC)

                Box(
                    modifier = Modifier
                        .size(23.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(powerBg)
                        .border(1.dp, if (isEnabled) NeonPinkLight else Color(0x4DD946EF), RoundedCornerShape(6.dp))
                        .clickable { onPowerToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⏻",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = powerTextColor
                    )
                }
            }
        } else {
            // Master Track: FX Button (Orange / Amber "FX")
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFFF7A00), Color(0xFFE65100))
                        )
                    )
                    .border(1.2.dp, Color(0xFFFFB74D), RoundedCornerShape(7.dp))
                    .clickable { onFxClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "FX",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Micro Rotary Pan Knob (-1.0f Left to +1.0f Right)
 * Sized and centered accurately to align with adjacent M/S button.
 */
@Composable
fun MicroPanKnob(
    pan: Float,
    onPanChange: (Float) -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var currentPan by remember(pan) { mutableFloatStateOf(pan) }
    val onPanChangeState by rememberUpdatedState(onPanChange)

    Box(
        modifier = modifier
            .size(24.dp)
            .pointerInput(isEnabled) {
                if (isEnabled) {
                    detectVerticalDragGestures { _, dragAmount ->
                        val delta = -dragAmount / 80f
                        currentPan = (currentPan + delta).coerceIn(-1f, 1f)
                        onPanChangeState(currentPan)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(21.dp)) {
            val strokeWidth = 2.0f
            val radius = (size.minDimension - strokeWidth) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Knob Bezel 3D shadow & metallic body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF4A4A62),
                        Color(0xFF2C2C3C),
                        Color(0xFF181822)
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius
            )

            // Outer Neon/Bezel Accent Ring
            drawCircle(
                color = if (isEnabled) NeonCyan.copy(alpha = 0.65f) else Color(0x2AFFFFFF),
                radius = radius,
                style = Stroke(width = 1.3f)
            )

            // Center Notch (12 o'clock = 0 center)
            val angleDeg = 270f + (currentPan * 65f)
            val angleRad = (angleDeg * PI / 180f).toFloat()

            val pointerColor = if (isEnabled) NeonCyanLight else Color.LightGray
            val endX = center.x + radius * 0.82f * cos(angleRad)
            val endY = center.y + radius * 0.82f * sin(angleRad)

            drawLine(
                color = pointerColor,
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 2.4f,
                cap = StrokeCap.Round
            )

            // Center metallic core dot
            drawCircle(
                color = if (isEnabled) NeonCyan else Color.Gray,
                radius = 2f,
                center = center
            )
        }
    }
}

/**
 * Fader Slider with 3D Realistic Fader Cap and Stereo Background VU-Meter
 */
@Composable
fun FaderSliderWithVuMeter(
    value: Float,
    peakMeterL: Float,
    peakMeterR: Float,
    onValueChange: (Float) -> Unit,
    isMaster: Boolean,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var containerHeightPx by remember { mutableFloatStateOf(120f) }
    var isDragging by remember { mutableStateOf(false) }

    val capHeightDp = 34.dp
    val density = LocalDensity.current
    val capHeightPx = remember(density, capHeightDp) { with(density) { capHeightDp.toPx() } }
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                if (size.height > 0) {
                    containerHeightPx = size.height.toFloat()
                }
            }
            .pointerInput(capHeightPx, isEnabled) {
                if (isEnabled) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val usableHeight = (containerHeightPx - capHeightPx).coerceAtLeast(1f)
                        val targetVal = 1f - ((down.position.y - capHeightPx / 2f) / usableHeight)
                        currentOnValueChange(targetVal.coerceIn(0f, 1f))
                        isDragging = true

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull() ?: break
                            if (!change.pressed) break

                            val curVal = 1f - ((change.position.y - capHeightPx / 2f) / usableHeight)
                            currentOnValueChange(curVal.coerceIn(0f, 1f))
                            change.consume()
                        }
                        isDragging = false
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        // ================= BACKGROUND STEREO VU-METER =================
        Row(
            modifier = Modifier
                .width(16.dp)
                .fillMaxHeight()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Channel
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer { }
            ) {
                val barWidth = size.width - 1f
                val h = size.height
                val meterHeight = (peakMeterL * h).coerceIn(0f, h)

                // Groove slot background
                drawRect(
                    color = Color(0x1A000000),
                    topLeft = Offset.Zero,
                    size = Size(barWidth, h)
                )

                if (meterHeight > 0f) {
                    val brush = Brush.verticalGradient(
                        colors = listOf(MuteRed, SoloAmber, NeonCyan, Color(0xFF10B981)),
                        startY = 0f,
                        endY = h
                    )
                    drawRect(
                        brush = brush,
                        topLeft = Offset(0f, h - meterHeight),
                        size = Size(barWidth, meterHeight)
                    )
                }
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Right Channel
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer { }
            ) {
                val barWidth = size.width - 1f
                val h = size.height
                val meterHeight = (peakMeterR * h).coerceIn(0f, h)

                drawRect(
                    color = Color(0x1A000000),
                    topLeft = Offset.Zero,
                    size = Size(barWidth, h)
                )

                if (meterHeight > 0f) {
                    val brush = Brush.verticalGradient(
                        colors = listOf(MuteRed, SoloAmber, NeonCyan, Color(0xFF10B981)),
                        startY = 0f,
                        endY = h
                    )
                    drawRect(
                        brush = brush,
                        topLeft = Offset(0f, h - meterHeight),
                        size = Size(barWidth, meterHeight)
                    )
                }
            }
        }

        // ================= CENTRAL FADER SLOT =================
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF08080C))
                .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(2.dp))
        )

        // ================= 3D REALISTIC FADER CAP =================
        val usableHeightPx = (containerHeightPx - capHeightPx).coerceAtLeast(0f)
        val capOffsetFromTop = (1f - value.coerceIn(0f, 1f)) * usableHeightPx

        // Realistic 3D metallic/curved white-silver fader cap
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = capOffsetFromTop
                }
                .width(26.dp)
                .height(capHeightDp)
                .shadow(
                    elevation = if (isDragging) 8.dp else 4.dp,
                    shape = RoundedCornerShape(6.dp),
                    spotColor = Color.Black
                )
                .clip(RoundedCornerShape(6.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFEBEBF2),
                            Color(0xFFD6D6E0),
                            Color(0xFFB5B5C4),
                            Color(0xFF8E8E9E),
                            Color(0xFF707080)
                        )
                    )
                )
                .border(1.dp, Color(0xFFFFFFFF), RoundedCornerShape(6.dp))
        ) {
            // 3D Bevel & Grip Ridges
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Light Reflection Rim
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp, start = 3.dp, end = 3.dp)
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.White.copy(alpha = 0.9f))
                )

                // Tactile Grip Grooves
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.width(14.dp).height(1.5.dp).background(Color(0x33000000)))
                    // Center Indicator Line (Neon Cyan for track, Neon Magenta for Master)
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(
                                if (isMaster) Color(0xFFD946EF) else Color(0xFF06B6D4)
                            )
                    )
                    Box(modifier = Modifier.width(14.dp).height(1.5.dp).background(Color(0x33000000)))
                }

                // Bottom Shadow Bumper
                Box(
                    modifier = Modifier
                        .padding(bottom = 2.dp, start = 3.dp, end = 3.dp)
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color(0x66000000))
                )
            }
        }
    }
}
