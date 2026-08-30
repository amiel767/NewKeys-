package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
 * VerticalTrackChannel faithfully built to user specifications:
 *
 * For Regular Tracks (1..8):
 * 1. Top: Display Box with Soundfont / Patch Title (.sf2 or "-" if none)
 * 2. Under Display:
 *    - Left: Pan Rotary Knob with Neon LED glowing arc
 *    - Right: Pure Saturated Mute (Rouge) & Solo (Jaune LED) buttons (clean without inner white boxes)
 * 3. Middle: Vertical Fader with 3D Metallic Ribbed Bonnet & Center Bright Glowing LED Line + Stereo Neon VU-Meter
 * 4. Bottom:
 *    - Left: FX Button (Cyan outline)
 *    - Right: Power Button (Square button glowing Blue/Cyan when ON, dark when OFF)
 *
 * For Master Track:
 * 1. Top: Master Display Box (Cyan text "MASTER")
 * 2. Middle: Vertical Fader with 3D Realistic Metallic Bonnet (Magenta LED) + Master VU-Meter
 * 3. Bottom inside Master Track: FX Button (Amber "FX")
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
        if (track.isSolo) Color(0xFFFFD600).copy(alpha = 0.8f) else if (track.isMuted) Color(0xFFFF1E40).copy(alpha = 0.8f) else Color(0x22FFFFFF)
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

        // ================= 2. KNOB & PURE COLORED M/S ROW =================
        if (!isMaster) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(25.dp)
                    .padding(horizontal = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Pan Rotary Knob
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MicroPanKnob(
                        pan = track.pan,
                        onPanChange = onPanChange,
                        isEnabled = isEnabled
                    )
                }

                Spacer(modifier = Modifier.width(3.dp))

                // Right: Pure Colored Mute / Solo Button (Clean pure saturated color without inner white dot)
                val isSolo = track.isSolo
                val isMuted = track.isMuted

                val btnBg by animateColorAsState(
                    targetValue = when {
                        !isEnabled -> Color(0x14FFFFFF)
                        isSolo -> Color(0xFFFFD600) // Pure Yellow LED for Solo
                        isMuted -> Color(0xFFFF1E40) // Pure Red for Mute
                        else -> Color(0xFF10B981) // Pure Green for Unmuted Active
                    },
                    animationSpec = tween(80),
                    label = "mute_solo_color"
                )

                val btnBorder = when {
                    !isEnabled -> Color(0x1AFFFFFF)
                    isSolo -> Color(0xFFFFF176)
                    isMuted -> Color(0xFFFF8A80)
                    else -> Color(0xFF6EE7B7)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(btnBg)
                        .border(1.dp, btnBorder, RoundedCornerShape(6.dp))
                        .shadow(
                            elevation = if (isEnabled && (isSolo || isMuted)) 4.dp else 1.dp,
                            shape = RoundedCornerShape(6.dp),
                            spotColor = if (isSolo) Color(0xFFFFD600) else if (isMuted) Color(0xFFFF1E40) else Color(0xFF10B981)
                        )
                        .clickable { onMuteSoloClick() }
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // ================= 3. VERTICAL FADER WITH REALISTIC METALLIC LED BONNET =================
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // FX Button (Cyan outline)
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

                // Power Square Button: Lights up in Cyan/Blue when ON
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
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(powerDotColor)
                    )
                }
            }
        } else {
            // Master Track: FX Button
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
 * Compact Horizontal Track Channel (Used when keyboard is expanded to maximum height)
 */
@Composable
fun CompactHorizontalTrack(
    track: TrackChannel,
    onVolumeChange: (Float) -> Unit,
    onPowerToggle: () -> Unit = {},
    onTrackNameClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isMaster = track.isMaster
    val isEnabled = track.isEnabled

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isMaster) Color(0xFF281335) else Color(0xFF171722))
            .border(1.dp, if (isMaster) Color(0x66D946EF) else Color(0x22FFFFFF), RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Name Box
        Text(
            text = if (isMaster) "MST" else "P${track.id}",
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isMaster) NeonMagenta else NeonCyan,
            modifier = Modifier
                .clickable { if (!isMaster) onTrackNameClick() }
                .padding(end = 4.dp)
        )

        // Horizontal Volume Slider
        var widthPx by remember { mutableFloatStateOf(60f) }
        val currentOnVolumeChange by rememberUpdatedState(onVolumeChange)

        Box(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        val target = (change.position.x / widthPx).coerceIn(0f, 1f)
                        currentOnVolumeChange(target)
                        change.consume()
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Track Groove
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color(0xFF0C0D12))
            )
            // Filled Level
            Box(
                modifier = Modifier
                    .fillMaxWidth(track.volume)
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(if (isMaster) NeonMagenta else NeonCyan)
            )
        }

        if (!isMaster) {
            Spacer(modifier = Modifier.width(3.dp))
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (isEnabled) NeonCyan else Color(0x33FFFFFF))
                    .clickable { onPowerToggle() }
            )
        }
    }
}

/**
 * Micro Rotary Pan Knob (-1.0f Left to +1.0f Right)
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

            // Center Notch Pointer
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
 * Fader Slider with 3D Realistic Brushed Metal Bonnet & Illuminated Center LED Line
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

    val capHeightDp = 36.dp
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
        // ================= BACKGROUND STEREO NEON VU-METER =================
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

        // ================= 3D REALISTIC METALLIC BONNET WITH CENTER LED LINE =================
        val usableHeightPx = (containerHeightPx - capHeightPx).coerceAtLeast(0f)
        val capOffsetFromTop = (1f - value.coerceIn(0f, 1f)) * usableHeightPx
        val ledColor = if (isMaster) NeonMagenta else NeonCyan

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = capOffsetFromTop
                }
                .width(27.dp)
                .height(capHeightDp)
                .shadow(
                    elevation = if (isDragging) 8.dp else 4.dp,
                    shape = RoundedCornerShape(5.dp),
                    spotColor = Color.Black
                )
                .clip(RoundedCornerShape(5.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF4A4E5C),
                            Color(0xFF2C2F3A),
                            Color(0xFF1E2028),
                            Color(0xFF121319),
                            Color(0xFF0A0B0E)
                        )
                    )
                )
                .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(5.dp))
        ) {
            // Horizontal Ribbed Metallic Grooves + Center Bright LED Slit
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Highlight Bevel
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(1.5.dp)
                        .clip(RoundedCornerShape(0.5.dp))
                        .background(Color(0x88FFFFFF))
                )

                // Top Grip Ribs
                Box(modifier = Modifier.fillMaxWidth(0.75f).height(1.dp).background(Color(0x33FFFFFF)))
                Box(modifier = Modifier.fillMaxWidth(0.75f).height(1.dp).background(Color(0x22000000)))

                // CENTER BRIGHT GLOWING LED SLIT (La petite ligne de couleur au milieu)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(3.dp)
                        .shadow(4.dp, RoundedCornerShape(1.5.dp), spotColor = ledColor)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(ledColor.copy(alpha = 0.4f), Color.White, ledColor, ledColor.copy(alpha = 0.4f))
                            )
                        )
                )

                // Bottom Grip Ribs
                Box(modifier = Modifier.fillMaxWidth(0.75f).height(1.dp).background(Color(0x22000000)))
                Box(modifier = Modifier.fillMaxWidth(0.75f).height(1.dp).background(Color(0x33FFFFFF)))

                // Bottom Shadow Rim
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(1.5.dp)
                        .clip(RoundedCornerShape(0.5.dp))
                        .background(Color(0x55000000))
                )
            }
        }
    }
}
