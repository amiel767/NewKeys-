package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TrackChannel
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun VerticalTrackChannel(
    track: TrackChannel,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    onTrackNameClick: () -> Unit,
    onFxClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMaster = track.isMaster

    val cardBrush = if (isMaster) {
        Brush.linearGradient(
            colors = listOf(Color(0xFF3D1F4D), Color(0xFF2A1230), Color(0xFF1F0D24))
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFF262630), Color(0xFF1C1C24))
        )
    }

    val borderColor = if (isMaster) {
        Color(0x59D946EF)
    } else {
        BorderSubtle
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBrush)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag("track_${track.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Track / SF2 Name Pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isMaster) Color(0x2EFFFFFF) else Color(0x2422D3EE)
                )
                .border(
                    1.dp,
                    if (isMaster) Color(0x80FFFFFF) else Color(0x8022D3EE),
                    RoundedCornerShape(6.dp)
                )
                .clickable { onTrackNameClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isMaster) "MASTER" else track.patchName.ifEmpty { track.name },
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isMaster) Color.White else NeonCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Mute / Solo Row
        if (!isMaster) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Mute button
                val muteBg by animateColorAsState(
                    targetValue = if (track.isMuted) MuteRed else Color(0x0DFFFFFF),
                    animationSpec = tween(120),
                    label = "mute_bg"
                )
                val muteText by animateColorAsState(
                    targetValue = if (track.isMuted) Color(0xFF2A0410) else TextDim,
                    label = "mute_text"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(muteBg)
                        .border(
                            1.dp,
                            if (track.isMuted) Color.Transparent else Color(0x14FFFFFF),
                            RoundedCornerShape(5.dp)
                        )
                        .clickable { onMuteToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "M",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = muteText
                    )
                }

                // Solo button
                val soloBg by animateColorAsState(
                    targetValue = if (track.isSolo) SoloAmber else Color(0x0DFFFFFF),
                    animationSpec = tween(120),
                    label = "solo_bg"
                )
                val soloText by animateColorAsState(
                    targetValue = if (track.isSolo) Color(0xFF2A1C00) else TextDim,
                    label = "solo_text"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(soloBg)
                        .border(
                            1.dp,
                            if (track.isSolo) Color.Transparent else Color(0x14FFFFFF),
                            RoundedCornerShape(5.dp)
                        )
                        .clickable { onSoloToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "S",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = soloText
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(20.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Fader Container
        FaderSlider(
            value = track.volume,
            onValueChange = onVolumeChange,
            isMaster = isMaster,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        // FX Slot Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isMaster) 22.dp else 18.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    if (isMaster) {
                        Brush.linearGradient(listOf(Color(0x40D946EF), Color(0x267C3AED)))
                    } else {
                        Brush.linearGradient(listOf(Color(0x0AFFFFFF), Color(0x05FFFFFF)))
                    }
                )
                .border(
                    1.dp,
                    if (isMaster) Color(0x59D946EF) else Color(0x4022D3EE),
                    RoundedCornerShape(5.dp)
                )
                .clickable { onFxClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isMaster) "Effects (Reverb, comp...)" else "Fx, EQ...",
                fontSize = if (isMaster) 7.5.sp else 8.sp,
                fontWeight = if (isMaster) FontWeight.Bold else FontWeight.Medium,
                color = if (isMaster) Color(0xFFF0D9F5) else TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
fun FaderSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    isMaster: Boolean,
    modifier: Modifier = Modifier
) {
    var containerHeightPx by remember { mutableFloatStateOf(100f) }
    var isDragging by remember { mutableStateOf(false) }

    val capHeightDp = 34.dp
    val trackWidthDp = 6.dp
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
            .pointerInput(capHeightPx) {
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
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Vertical track groove
        Box(
            modifier = Modifier
                .width(trackWidthDp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF0A0A0E), Color(0xFF1C1C24), Color(0xFF0A0A0E))
                    )
                )
        ) {
            // Illuminated Level Fill
            val fillBrush = if (isMaster) {
                Brush.verticalGradient(listOf(NeonPinkLight, NeonMagenta))
            } else {
                Brush.verticalGradient(listOf(NeonCyan, NeonCyanDark))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(value.coerceIn(0.01f, 1f))
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(3.dp))
                    .background(fillBrush)
            )
        }

        // Fader Cap (Tactile Slider Knob)
        val usableHeightPx = (containerHeightPx - capHeightPx).coerceAtLeast(0f)
        val capOffsetFromTop = (1f - value.coerceIn(0f, 1f)) * usableHeightPx

        val capBrush = if (isMaster) {
            Brush.verticalGradient(
                colors = listOf(Color(0xFFFFD6F9), Color(0xFFF2A6EF), Color(0xFFD946EF))
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    if (isDragging) Color(0xFF5A5A6C) else Color(0xFF4A4A58),
                    Color(0xFF2C2C36),
                    Color(0xFF232329)
                )
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, capOffsetFromTop.roundToInt()) }
                .width(26.dp)
                .height(capHeightDp)
                .shadow(4.dp, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .background(capBrush)
                .border(1.dp, Color(0x66000000), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Horizontal tactile groove & top specular highlight
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top shine
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp, start = 3.dp, end = 3.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.verticalGradient(listOf(Color(0x99FFFFFF), Color(0x00FFFFFF)))
                        )
                )

                // Middle marker notch
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (isMaster) Color(0x80000000) else Color(0x99000000))
                )

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
