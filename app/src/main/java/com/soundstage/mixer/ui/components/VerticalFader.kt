package com.soundstage.mixer.ui.components

import com.soundstage.mixer.ui.settings.mappable


import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.zIndex
import com.soundstage.mixer.model.TrackChannel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawWithContent
import com.soundstage.mixer.ui.theme.*
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
 * Returns distinct, elegant Sunday Keys instrument track color.
 */
fun getTrackThemeColor(trackId: Int, isMaster: Boolean = false): Color {
    if (isMaster) return Color(0xFF22D3EE)
    return when ((trackId - 1) % 8) {
        0 -> Color(0xFFE58929) // Track 1: Amber
        1 -> Color(0xFF36B37E) // Track 2: Emerald Green
        2 -> Color(0xFF3B82F6) // Track 3: Cobalt Sky Blue
        3 -> Color(0xFF9333EA) // Track 4: Violet / Purple
        4 -> Color(0xFFEC4899) // Track 5: Rose
        5 -> Color(0xFF14B8A6) // Track 6: Teal
        6 -> Color(0xFFF59E0B) // Track 7: Gold
        7 -> Color(0xFF6366F1) // Track 8: Indigo
        else -> Color(0xFF3B82F6)
    }
}

/** Resolves a track's colour, honouring a user override when set. */
fun TrackChannel.resolvedColor(): Color =
    customColorArgb?.let { Color(it) } ?: getTrackThemeColor(id, isMaster)

/** The 8 built-in track colours, offered as presets in the colour picker. */
val TrackColorPresets: List<Color> = (1..8).map { getTrackThemeColor(it) }

/**
 * VerticalTrackChannel faithfully built to user specifications:
 *
 * 1. Fader Body: Natural slate surface without harsh white outlines.
 * 2. Lower & Ultra-faded Gradient: Starts low near the bottom quarter with subtle alpha.
 * 3. Central Slot: Lightened visible groove capsule with strict bottom termination.
 * 4. Sound-Reactive VU Light: Fluorescent LED column lights up with volume.
 * 5. Realistic Fader Cap: Enlarged 46x58dp mechanical knob matching user asset.
 */
@Composable
fun VerticalTrackChannel(
    track: TrackChannel,
    onVolumeChange: (Float) -> Unit,
    onPowerToggle: () -> Unit = {},
    onDeleteTrack: () -> Unit = {},
    onPanChange: (Float) -> Unit = {},
    onMuteClick: () -> Unit = {},
    onSoloClick: () -> Unit = {},
    onTrackNameClick: () -> Unit = {},
    onFxClick: () -> Unit = {},
    onTrackSettingsClick: () -> Unit = {},
    showTicks: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isMaster = track.isMaster
    val isEnabled = track.isEnabled
    var showDeleteBubble by remember { mutableStateOf(false) }

    val vibrantLedColor = remember(track.id, isMaster, track.customColorArgb) {
        track.resolvedColor()
    }

    // Base body color: dynamic surface adapting to active theme palette
    val faderBodyColor = MaterialTheme.colorScheme.surface
    val subtleDarkBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    // Sound-reactive audio activity (Peak Meter) with guaranteed base visibility
    val audioActivity = if (isEnabled) maxOf(track.peakMeterL, track.peakMeterR).coerceIn(0f, 1f) else 0f
    val baseIdleAlpha = if (isEnabled) 0.28f else 0.08f
    val reactiveAlpha = (baseIdleAlpha + audioActivity * 0.65f).coerceIn(0.10f, 0.95f)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(faderBodyColor)
            .border(1.dp, subtleDarkBorder, RoundedCornerShape(8.dp))
            .testTag("track_${track.id}")
    ) {
        // Dynamic bottom ambient color halo (smooth fluid gradient across full width without hard edges or clipping)
        if (isEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(95.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                vibrantLedColor.copy(alpha = (0.05f + audioActivity * 0.12f).coerceIn(0.05f, 0.22f)),
                                vibrantLedColor.copy(alpha = (0.18f + audioActivity * 0.35f).coerceIn(0.18f, 0.50f))
                            )
                        )
                    )
            )
        }

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

                // Right: Mute / Solo Buttons
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val isSolo = track.isSolo
                    val soloBg = if (!isEnabled) Color(0x14FFFFFF) else if (isSolo) Color(0xFFFFD600) else Color(0x22FFFFFF)
                    val soloBorder = if (!isEnabled) Color(0x12FFFFFF) else if (isSolo) Color(0xFFFFF176) else Color(0x12FFFFFF)
                    
                    Box(
                        modifier = Modifier
                            .mappable("mixer.t${track.id}.solo")
                            .weight(1f)
                            .height(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(soloBg)
                            .border(1.dp, soloBorder, RoundedCornerShape(4.dp))
                            .clickable { onSoloClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "S", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isSolo) Color.Black else Color.White)
                    }

                    val isMuted = track.isMuted
                    val muteBg = if (!isEnabled) Color(0x14FFFFFF) else if (isMuted) Color(0xFFFF1E40) else Color(0x22FFFFFF)
                    val muteBorder = if (!isEnabled) Color(0x12FFFFFF) else if (isMuted) Color(0xFFFF8A80) else Color(0x12FFFFFF)

                    Box(
                        modifier = Modifier
                            .mappable("mixer.t${track.id}.mute")
                            .weight(1f)
                            .height(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(muteBg)
                            .border(1.dp, muteBorder, RoundedCornerShape(4.dp))
                            .clickable { onMuteClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "M", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        // ================= 3. VERTICAL FADER WITH REALISTIC 3D BONNET & AUDIO-REACTIVE VU =================
        FaderSliderWithVuMeter(
            value = track.volume,
            peakMeterL = track.peakMeterL,
            peakMeterR = track.peakMeterR,
            onValueChange = onVolumeChange,
            isMaster = isMaster,
            isEnabled = isEnabled,
            ledColor = vibrantLedColor,
            showTicks = showTicks,
            modifier = Modifier
                .mappable(if (isMaster) "mixer.master" else "mixer.t${track.id}.fader")
                .weight(1f)
                .fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(5.dp))

        // ================= 4. BOTTOM ACTION BUTTONS =================
        if (!isMaster) {
            TrackBottomRow(
                trackColor = vibrantLedColor,
                active = track.isEnabled,
                onFx = onFxClick,
                onSettings = onTrackSettingsClick,
                onToggle = onPowerToggle,
                onUnload = onDeleteTrack,
                height = 22.dp
            )
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

    // Bouncy Bubble "Delete channel" micro popup on long-press with touch-outside dismissal
    if (showDeleteBubble && !isMaster) {
        // Transparent touch-outside dismissal interceptor
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(99f)
                .pointerInput(Unit) {
                    detectTapGestures {
                        showDeleteBubble = false
                    }
                }
        )

        androidx.compose.animation.AnimatedVisibility(
            visible = showDeleteBubble,
            enter = androidx.compose.animation.scaleIn(
                initialScale = 0.5f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + androidx.compose.animation.fadeIn(tween(180)),
            exit = androidx.compose.animation.scaleOut(targetScale = 0.6f) + androidx.compose.animation.fadeOut(tween(140)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .zIndex(100f)
        ) {
            Box(
                modifier = Modifier
                    .shadow(14.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFF43F5E), Color(0xFFBE123C))
                        )
                    )
                    .border(1.dp, Color(0xFFFF8FA3), RoundedCornerShape(10.dp))
                    .clickable {
                        showDeleteBubble = false
                        onDeleteTrack()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("btn_delete_channel_${track.id}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Delete channel",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1
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
                    detectTapGestures(
                        onDoubleTap = {
                            currentPan = 0.0f
                            onPanChangeState(0.0f)
                        }
                    )
                }
            }
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
    showTicks: Boolean = true,
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
            showTicks = showTicks,
            trackWidth = 18.dp,
            trackHeight = 205.dp,
            thumbWidth = 32.dp,
            thumbHeight = 40.dp,
            modifier = Modifier.fillMaxSize()
        )
    }
}
