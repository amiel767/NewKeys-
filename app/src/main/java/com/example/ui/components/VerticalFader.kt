package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Helper to convert HSL to Compose Color.
 */
fun hslToRgbColor(hue: Float, saturation: Float, lightness: Float, alpha: Float = 1f): Color {
    val h = (hue % 360f + 360f) % 360f
    val c = (1f - abs(2f * lightness - 1f)) * saturation
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = lightness - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
        alpha = alpha
    )
}

/**
 * Generates an ultra-slow 10-minute (600,000ms) RGB color cycle with an initial phase per track.
 */
@Composable
fun rememberDynamicFaderHue(trackId: Int): Pair<Color, Color> {
    val initialPhase = remember(trackId) {
        ((trackId * 45.0f) % 360f)
    }
    val infiniteTransition = rememberInfiniteTransition(label = "rgb_cycle_$trackId")
    // 10 minutes = 600,000 ms cycle per user requirement
    val hueAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hue_anim_$trackId"
    )

    val currentHue = (initialPhase + hueAnim) % 360f
    val baseSubtleColor = remember(currentHue) {
        hslToRgbColor(hue = currentHue, saturation = 0.90f, lightness = 0.50f)
    }
    val vibrantLedColor = remember(currentHue) {
        hslToRgbColor(hue = currentHue, saturation = 1.0f, lightness = 0.65f)
    }
    return Pair(baseSubtleColor, vibrantLedColor)
}

/**
 * VerticalTrackChannel faithfully built to user specifications:
 *
 * 1. Fader Body: #202534 without white outlines (seamless borderless dark tone).
 * 2. Lower & Ultra-faded Gradient: Starts low near the bottom quarter with very subtle alpha.
 * 3. Central Slot: Rounded vertical black column with a subtle transparent grey rounded border.
 * 4. Sound-Reactive VU Light: Fluorescent LED column lights up ONLY when audio is actively playing on that track.
 * 5. Realistic 3D White/Silver Bonnet: Custom 3D fader cap matching the user reference image with center black line and 2 wavy grooves above and below.
 * 6. Ultra-slow 10-minute RGB cycle.
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

    // Ultra-slow 10-minute dynamic hue
    val (baseSubtleColor, vibrantLedColor) = rememberDynamicFaderHue(track.id)

    // Base body color: #25293A matching user spec (Slot background)
    val faderBodyColor = Color(0xFF25293A)
    val subtleDarkBorder = Color(0x18FFFFFF)

    // Sound-reactive audio activity (Peak Meter) with guaranteed base visibility
    val audioActivity = if (isEnabled) maxOf(track.peakMeterL, track.peakMeterR).coerceIn(0f, 1f) else 0f
    val baseIdleAlpha = if (isEnabled) 0.32f else 0.12f
    val reactiveAlpha = (baseIdleAlpha + audioActivity * 0.65f).coerceIn(0.10f, 0.95f)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(faderBodyColor)
            .border(1.dp, subtleDarkBorder, RoundedCornerShape(8.dp))
            .testTag("track_${track.id}")
    ) {
        // 1. Ambient & Reactive Neon Aura at the bottom of the fader (visible from start, pulses with sound)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.60f)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.45f to vibrantLedColor.copy(alpha = reactiveAlpha * 0.25f),
                        0.80f to vibrantLedColor.copy(alpha = reactiveAlpha * 0.60f),
                        1.0f to vibrantLedColor.copy(alpha = reactiveAlpha * 0.95f)
                    )
                )
        )

        // 2. Radial bottom floor glow
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            vibrantLedColor.copy(alpha = reactiveAlpha * 0.70f),
                            vibrantLedColor.copy(alpha = reactiveAlpha * 0.20f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 3. Crisp luminous bottom LED strip accent
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.85f)
                .height(2.5.dp)
                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                .background(vibrantLedColor.copy(alpha = reactiveAlpha))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // ================= 1. TOP DISPLAY BOX =================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(
                    if (isMaster) Color(0xFF162132) else if (isEnabled) Color(0xFF162132) else Color(0x14FFFFFF)
                )
                .border(
                    1.dp,
                    if (isMaster) Color(0x4422D3EE) else Color(0x18FFFFFF),
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
                color = if (isEnabled || isMaster) TextPrimary else TextDim2,
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
                        isEnabled = isEnabled,
                        activeLedColor = vibrantLedColor
                    )
                }

                Spacer(modifier = Modifier.width(3.dp))

                // Right: Pure Colored Mute / Solo Button
                val isSolo = track.isSolo
                val isMuted = track.isMuted

                val btnBg by animateColorAsState(
                    targetValue = when {
                        !isEnabled -> Color(0x14FFFFFF)
                        isSolo -> Color(0xFFFFD600)
                        isMuted -> Color(0xFFFF1E40)
                        else -> Color(0xFF10B981)
                    },
                    animationSpec = tween(80),
                    label = "mute_solo_color"
                )

                val btnBorder = when {
                    !isEnabled -> Color(0x12FFFFFF)
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

        // ================= 3. VERTICAL FADER WITH REALISTIC 3D BONNET & AUDIO-REACTIVE VU =================
        FaderSliderWithVuMeter(
            value = track.volume,
            peakMeterL = track.peakMeterL,
            peakMeterR = track.peakMeterR,
            onValueChange = onVolumeChange,
            isMaster = isMaster,
            isEnabled = isEnabled,
            ledColor = vibrantLedColor,
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
                // FX Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(23.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF162132))
                        .border(1.dp, Color(0x3322D3EE), RoundedCornerShape(6.dp))
                        .clickable { onFxClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "FX",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonCyanLight
                    )
                }

                Spacer(modifier = Modifier.width(3.dp))

                // Power Square Button
                val powerBg by animateColorAsState(
                    targetValue = if (isEnabled) Color(0xFF0077B6) else Color(0xFF1A1F2C),
                    animationSpec = tween(100),
                    label = "power_bg"
                )
                val powerBorder = if (isEnabled) NeonCyan else Color(0x1EFFFFFF)
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
                .border(1.2.dp, Color(0x66FFB74D), RoundedCornerShape(7.dp))
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
}

/**
 * Micro Rotary Pan Knob (-1.0f Left to +1.0f Right)
 */
@Composable
fun MicroPanKnob(
    pan: Float,
    onPanChange: (Float) -> Unit,
    isEnabled: Boolean,
    activeLedColor: Color = NeonCyan,
    modifier: Modifier = Modifier
) {
    var currentPan by remember(pan) { mutableFloatStateOf(pan) }
    val onPanChangeState by rememberUpdatedState(onPanChange)

    Box(
        modifier = modifier
            .size(24.dp)
            .pointerInput(isEnabled) {
                if (isEnabled) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        currentPan = (currentPan + dragAmount * 0.04f).coerceIn(-1.0f, 1.0f)
                        onPanChangeState(currentPan)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.minDimension / 2f - 1.5f
            val innerRadius = outerRadius - 2f

            // Outer ring
            drawCircle(
                color = Color(0xFF0F131C),
                radius = outerRadius,
                center = center
            )
            drawCircle(
                color = Color(0x18FFFFFF),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 1.0f)
            )

            // Inner knob body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF323A4D), Color(0xFF1E2330)),
                    center = center,
                    radius = innerRadius
                ),
                radius = innerRadius,
                center = center
            )

            // Center Notch Pointer
            val angleDeg = 270f + (currentPan * 65f)
            val angleRad = (angleDeg * PI / 180f).toFloat()

            val pointerColor = if (isEnabled) activeLedColor else Color.DarkGray
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
 * Fader Slider with:
 * - Vertical black column with rounded transparent grey fade border
 * - Light in the black column appears ONLY when audio is actively playing on that track
 * - 3D Realistic White/Silver Fader Cap (Bonnet) from user image (larger, wider, center black line + 2 wavy grooves above/below)
 */
@Composable
fun FaderSliderWithVuMeter(
    value: Float,
    peakMeterL: Float,
    peakMeterR: Float,
    onValueChange: (Float) -> Unit,
    isMaster: Boolean,
    isEnabled: Boolean,
    ledColor: Color,
    modifier: Modifier = Modifier
) {
    val audioActivity = maxOf(peakMeterL, peakMeterR).coerceIn(0f, 1f)
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        CustomVerticalFader(
            value = value,
            onValueChange = onValueChange,
            auraColor = ledColor,
            audioActivity = audioActivity,
            isEnabled = isEnabled,
            trackWidth = 18.dp,
            trackHeight = 220.dp,
            thumbWidth = 38.dp,
            thumbHeight = 44.dp,
            modifier = Modifier.fillMaxHeight()
        )
    }
}
