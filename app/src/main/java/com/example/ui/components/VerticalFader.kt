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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TrackChannel
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * VerticalTrackChannel designed faithfully to user specifications and screenshots:
 *
 * For Regular Tracks (1..8):
 * 1. Top: Display Box with Soundfont / Patch Title (.sf2 or "-" if none)
 * 2. Under Display:
 *    - Left: Pan Rotary Knob with Neon LED glowing arc
 *    - Right: Extended Rounded Rectangle M/S capsule with pure glowing colors (no letters)
 * 3. Middle: Vertical Fader with 3D Realistic Cap and Stereo 2-Color Neon VU-Meter
 * 4. Bottom:
 *    - Left: FX Button (Cyan outline)
 *    - Right: Power Button (Square button that glows bright Blue/Cyan when ON, fades dark when OFF)
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
        if (track.isSolo) SoloAmber.copy(alpha = 0.65f) else if (track.isMuted) MuteRed.copy(alpha = 0.55f) else Color(0x22FFFFFF)
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

        // ================= 2. KNOB & EXTENDED M/S ROW (Regular Tracks) =================
        if (!isMaster) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .padding(horizontal = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Pan Rotary Knob with glowing LED Neon arc
                Box(
                    modifier = Modifier
                        .size(25.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MicroPanKnob(
                        pan = track.pan,
                        onPanChange = onPanChange,
                        isEnabled = isEnabled
                    )
                }

                Spacer(modifier = Modifier.width(3.dp))

                // Right: Extended Rounded Rectangle M/S Capsule (no letters, pure reactive color)
                val isSolo = track.isSolo
                val isMuted = track.isMuted

                val pillBg by animateColorAsState(
                    targetValue = when {
                        !isEnabled -> Color(0x14FFFFFF)
                        isSolo -> SoloAmber
                        isMuted -> Color(0xFFE11D48)
                        else -> Color(0xFF10B981) // Clean glowing green active
                    },
                    animationSpec = tween(80),
                    label = "ms_pill_bg"
                )

                val pillBorderColor = when {
                    !isEnabled -> Color(0x1AFFFFFF)
                    isSolo -> Color(0xFFFFD54F)
                    isMuted -> Color(0xFFFF4D6D)
                    else -> Color(0xFF34D399)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(23.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(pillBg)
                        .border(1.dp, pillBorderColor, RoundedCornerShape(6.dp))
                        .shadow(
                            elevation = if (isEnabled && (isSolo || isMuted)) 4.dp else 1.dp,
                            shape = RoundedCornerShape(6.dp),
                            spotColor = if (isSolo) SoloAmber else if (isMuted) MuteRed else Color(0xFF10B981)
                        )
                        .clickable { onMuteSoloClick() },
                    contentAlignment = Alignment.Center
                ) {
                    // Small internal status dot / indicator
                    Box(
                        modifier = Modifier
                            .size(width = 8.dp, height = 3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color.White.copy(alpha = if (isEnabled) 0.85f else 0.2f))
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
            // Regular Track: Left = FX button (Cyan), Right = Power square button (Blue when ON, dark when OFF)
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

                // Power Square Button: Lights up in Cyan/Blue when ON, fades dark/dim when OFF
                val powerBg by animateColorAsState(
                    targetValue = if (isEnabled) Color(0xFF0077B6) else Color(0xFF1E1E26),
                    animationSpec = tween(100),
                    label = "power_bg"
                )
                val powerBorder = if (isEnabled) NeonCyan else Color(0x2EFFFFFF)
                val powerDotColor = if (isEnabled) Color(0xFF90E0EF) else Color(0x44FFFFFF)

                Box(
                    modifier = Modifier
                        .size(23.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(powerBg)
                        .border(1.dp, powerBorder, RoundedCornerShape(6.dp))
                        .clickable { onPowerToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    // Small illuminated power glyph / dot
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(powerDotColor)
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
 * Styled with outer glowing LED Neon arc.
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
            .size(25.dp)
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
        Canvas(modifier = Modifier.size(22.dp)) {
            val strokeWidth = 2.0f
            val radius = (size.minDimension - strokeWidth) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Outer dark track
            drawCircle(
                color = Color(0xFF14141E),
                radius = radius,
                center = center
            )

            // Glowing LED Neon arc
            if (isEnabled) {
                val startAngle = 135f
                val sweepAngle = 270f * ((currentPan + 1f) / 2f)
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(NeonCyan, Color(0xFFFF9E00), NeonMagenta, NeonCyan),
                        center = center
                    ),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle.coerceAtLeast(6f),
                    useCenter = false,
                    style = Stroke(width = 2.4f, cap = StrokeCap.Round)
                )
            }

            // Knob Center Body
            val innerRadius = radius * 0.72f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF3E3E52),
                        Color(0xFF242432),
                        Color(0xFF12121A)
                    ),
                    center = center,
                    radius = innerRadius
                ),
                radius = innerRadius,
                center = center
            )

            // Center Notch Pointer (12 o'clock = 0 center)
            val angleDeg = 270f + (currentPan * 65f)
            val angleRad = (angleDeg * PI / 180f).toFloat()

            val pointerColor = if (isEnabled) NeonCyanLight else Color.DarkGray
            val endX = center.x + innerRadius * 0.85f * cos(angleRad)
            val endY = center.y + innerRadius * 0.85f * sin(angleRad)

            drawLine(
                color = pointerColor,
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 2.0f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Fader Slider with 3D Realistic Fader Cap and Stereo 2-Color Neon VU-Meter
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
        // ================= BACKGROUND STEREO 2-COLOR NEON VU-METER =================
        // Only illuminates when isEnabled and peakMeter > 0f
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
                val meterHeight = if (isEnabled) (peakMeterL * h).coerceIn(0f, h) else 0f

                // Dark groove slot background
                drawRect(
                    color = Color(0x1A000000),
                    topLeft = Offset.Zero,
                    size = Size(barWidth, h)
                )

                if (meterHeight > 0.5f) {
                    // 2-Color Neon LED style (Neon Cyan in mid/low, transitioning to Neon Magenta/Amber at peak)
                    val brush = Brush.verticalGradient(
                        colors = listOf(NeonMagenta, SoloAmber, NeonCyan),
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
                val meterHeight = if (isEnabled) (peakMeterR * h).coerceIn(0f, h) else 0f

                drawRect(
                    color = Color(0x1A000000),
                    topLeft = Offset.Zero,
                    size = Size(barWidth, h)
                )

                if (meterHeight > 0.5f) {
                    val brush = Brush.verticalGradient(
                        colors = listOf(NeonMagenta, SoloAmber, NeonCyan),
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
