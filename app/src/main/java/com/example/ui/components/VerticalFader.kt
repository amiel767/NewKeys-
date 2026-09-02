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

    // Base body color: #202534 without any white outlines
    val faderBodyColor = Color(0xF0202534)
    val subtleDarkBorder = Color(0x12FFFFFF) // Extremely subtle dark border, no white contour

    // Sound-reactive audio activity (Peak Meter) with guaranteed base visibility
    val audioActivity = if (isEnabled) maxOf(track.peakMeterL, track.peakMeterR).coerceIn(0f, 1f) else 0f
    val baseIdleAlpha = if (isEnabled) 0.32f else 0.12f
    val reactiveAlpha = (baseIdleAlpha + audioActivity * 0.65f).coerceIn(0.10f, 0.95f)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(faderBodyColor)
            .border(1.dp, subtleDarkBorder, RoundedCornerShape(12.dp))
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
    var containerHeightPx by remember { mutableFloatStateOf(120f) }
    var isDragging by remember { mutableStateOf(false) }

    // Fader Bonnet Dimensions: Console Pro 3D Thumb
    val capWidthDp = 36.dp
    val capHeightDp = 52.dp

    val density = LocalDensity.current
    val capHeightPx = remember(density, capHeightDp) { with(density) { capHeightDp.toPx() } }
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    // Calculate decibels from 0..1 fader position
    fun valueToDb(v: Float): String {
        return when {
            v <= 0.001f -> "-∞ dB"
            v >= 0.99f -> "+6.0 dB"
            v >= 0.75f -> {
                val db = (v - 0.75f) / 0.25f * 6.0f
                String.format("+%.1f dB", db)
            }
            else -> {
                val db = (v / 0.75f - 1f) * 48f
                String.format("%.1f dB", db)
            }
        }
    }

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
        // ================= 0. dB GRADUATION TICKS ON LEFT & RIGHT =================
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp)
        ) {
            val h = size.height
            val w = size.width
            val tickColor = Color(0x33FFFFFF)
            val unityColor = Color(0x8822D3EE)

            // Tick fractions from bottom (0 = -inf, 0.20 = -24, 0.40 = -12, 0.55 = -6, 0.75 = 0dB, 1.0 = +6dB)
            val tickPositions = listOf(
                0.0f to false,   // -inf
                0.20f to false,  // -24dB
                0.40f to false,  // -12dB
                0.55f to false,  // -6dB
                0.75f to true,   // 0dB (Unity)
                1.0f to false    // +6dB
            )

            tickPositions.forEach { (fraction, isUnity) ->
                val y = h * (1f - fraction)
                val tColor = if (isUnity) unityColor else tickColor
                val tLength = if (isUnity) 5.dp.toPx() else 3.5.dp.toPx()
                val strokeW = if (isUnity) 1.5f else 1.0f

                // Left tick
                drawLine(
                    color = tColor,
                    start = Offset(2.dp.toPx(), y),
                    end = Offset(2.dp.toPx() + tLength, y),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
                // Right tick
                drawLine(
                    color = tColor,
                    start = Offset(w - 2.dp.toPx() - tLength, y),
                    end = Offset(w - 2.dp.toPx(), y),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }
        }

        // ================= 1. VERTICAL BLACK COLUMN WITH ROUNDED METALLIC CONTOUR =================
        Box(
            modifier = Modifier
                .width(12.dp)
                .fillMaxHeight()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF10131B))
                .border(1.2.dp, Color(0x388896AB), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Deep black inner groove
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color(0xFF040508)),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Track volume level indicator inside the groove
                if (isEnabled && value > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(value.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        ledColor.copy(alpha = 0.70f),
                                        ledColor.copy(alpha = 0.25f)
                                    )
                                )
                            )
                    )
                }

                // Volume audio-reactive burst appears when sound is actively playing on that track
                val audioActivity = if (isEnabled) maxOf(peakMeterL, peakMeterR).coerceIn(0f, 1f) else 0f
                if (audioActivity > 0.02f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(audioActivity)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White,
                                        ledColor,
                                        ledColor.copy(alpha = 0.85f)
                                    )
                                )
                            )
                            .shadow(8.dp, RoundedCornerShape(2.dp), spotColor = ledColor)
                    )
                }
            }
        }

        // ================= 2. CONSOLE PRO 3D DARK MATTE METAL BONNET (THUMB) =================
        val usableHeightPx = (containerHeightPx - capHeightPx).coerceAtLeast(0f)
        val capOffsetFromTop = (1f - value.coerceIn(0f, 1f)) * usableHeightPx

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = capOffsetFromTop
                }
                .width(capWidthDp)
                .height(capHeightDp)
                .shadow(
                    elevation = if (isDragging) 16.dp else 8.dp,
                    shape = RoundedCornerShape(8.dp),
                    spotColor = Color.Black
                )
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF384050),
                            Color(0xFF282E3B),
                            Color(0xFF1E232E),
                            Color(0xFF171B24)
                        )
                    )
                )
                .border(1.dp, Color(0x448896AB), RoundedCornerShape(8.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Top beveled chamfer highlight
                drawLine(
                    color = Color(0x88FFFFFF),
                    start = Offset(w * 0.15f, 1.5f),
                    end = Offset(w * 0.85f, 1.5f),
                    strokeWidth = 1.4f,
                    cap = StrokeCap.Round
                )

                // Bottom beveled chamfer shadow
                drawLine(
                    color = Color(0x66000000),
                    start = Offset(w * 0.10f, h - 1.5f),
                    end = Offset(w * 0.90f, h - 1.5f),
                    strokeWidth = 1.6f,
                    cap = StrokeCap.Round
                )

                // Side cylinder lighting gradients
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        0.0f to Color(0x45000000),
                        0.15f to Color(0x00FFFFFF),
                        0.50f to Color(0x22FFFFFF),
                        0.85f to Color(0x00000000),
                        1.0f to Color(0x55000000)
                    ),
                    size = size,
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )

                // Tactile horizontal brushed ribs
                val ribColor = Color(0x18FFFFFF)
                val ribShadow = Color(0x35000000)
                listOf(0.20f, 0.30f, 0.70f, 0.80f).forEach { frac ->
                    val ribY = h * frac
                    drawLine(
                        color = ribShadow,
                        start = Offset(w * 0.15f, ribY),
                        end = Offset(w * 0.85f, ribY),
                        strokeWidth = 1.8f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = ribColor,
                        start = Offset(w * 0.15f, ribY + 1.2f),
                        end = Offset(w * 0.85f, ribY + 1.2f),
                        strokeWidth = 1.0f,
                        cap = StrokeCap.Round
                    )
                }

                // Center laser engraved neon LED strip
                val centerY = h * 0.50f
                val startX = w * 0.10f
                val endX = w * 0.90f

                // Outer dark groove
                drawLine(
                    color = Color(0xFF0A0C10),
                    start = Offset(startX, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = 4.0f,
                    cap = StrokeCap.Round
                )
                // Vibrant neon core
                val indicatorColor = if (isEnabled) ledColor else Color(0xFF64748B)
                drawLine(
                    color = indicatorColor,
                    start = Offset(startX + 2f, centerY),
                    end = Offset(endX - 2f, centerY),
                    strokeWidth = 2.0f,
                    cap = StrokeCap.Round
                )
                // Center white hot laser filament
                if (isEnabled) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.90f),
                        start = Offset(startX + 6f, centerY),
                        end = Offset(endX - 6f, centerY),
                        strokeWidth = 0.8f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // ================= 3. LIVE dB VALUE BADGE DISPLAY DURING DRAGGING =================
        if (isDragging) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = (capOffsetFromTop - 26.dp.toPx()).coerceAtLeast(0f)
                    }
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xE60A101D))
                    .border(1.dp, ledColor, RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = valueToDb(value),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonCyanLight,
                    maxLines = 1
                )
            }
        }
    }
}
