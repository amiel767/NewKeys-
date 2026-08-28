package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun BottomBar(
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    isMetronomeOn: Boolean,
    onToggleMetronome: () -> Unit,
    isMetroPanelOpen: Boolean,
    onToggleMetroPanel: () -> Unit,
    metroSignature: String,
    onSelectSignature: (String) -> Unit,
    metroVolume: Float,
    onMetroVolumeChange: (Float) -> Unit,
    onKeyboardHandleClick: () -> Unit,
    onKeyboardDrag: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val barHeight = 34.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // REC Button
        val recBrush = if (isRecording) {
            Brush.verticalGradient(listOf(Color(0xFFFF2222), Color(0xFFCC0000)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFFF5B5B), RecRed))
        }

        Box(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(recBrush)
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                .clickable { onToggleRecording() }
                .testTag("btn_rec"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isRecording) "● REC" else "Rec",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }

        // BPM Box
        Row(
            modifier = Modifier
                .height(barHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .testTag("bpm_box"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(26.dp)
                    .fillMaxHeight()
                    .background(Color(0x1022D3EE))
                    .clickable { onBpmChange(-1) },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "−", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = NeonCyan)
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .defaultMinSize(minWidth = 42.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$bpm",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    lineHeight = 15.sp
                )
                Text(
                    text = "BPM",
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDim2,
                    letterSpacing = 0.6.sp,
                    lineHeight = 8.sp
                )
            }

            Box(
                modifier = Modifier
                    .width(26.dp)
                    .fillMaxHeight()
                    .background(Color(0x1022D3EE))
                    .clickable { onBpmChange(1) },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "+", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = NeonCyan)
            }
        }

        // Metronome Button
        Box(
            modifier = Modifier
                .height(barHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .clickable { onToggleMetroPanel() }
                .padding(horizontal = 12.dp)
                .testTag("btn_metro"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                // Metronome LED Indicator
                val infiniteTransition = rememberInfiniteTransition(label = "metro_pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = (60000 / bpm).coerceIn(200, 1000), easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "metro_pulse_alpha"
                )

                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .shadow(if (isMetronomeOn) 6.dp else 0.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            if (isMetronomeOn) NeonCyan.copy(alpha = pulseAlpha) else TextDim2
                        )
                )

                Text(
                    text = "Metro",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isMetronomeOn) NeonCyan else TextDim
                )
            }
        }

        // Retractable Keyboard Handle (Sliding pull-tab)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(barHeight)
                .clip(RoundedCornerShape(17.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                    )
                )
                .border(1.dp, Color(0x2E67E8F9), RoundedCornerShape(17.dp))
                .clickable { onKeyboardHandleClick() }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onKeyboardDrag(dragAmount.y)
                    }
                }
                .padding(horizontal = 14.dp)
                .testTag("kb_handle"),
            contentAlignment = Alignment.Center
        ) {
            // Central Grip Ridges
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.width(26.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x5967E8F9)))
                Box(modifier = Modifier.width(26.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x5967E8F9)))
                Box(modifier = Modifier.width(26.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x5967E8F9)))
            }

            // Hint Text
            Text(
                text = "glisser ↑ clavier",
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xB394A3B8),
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
fun MetronomeFloatingPanel(
    isOpen: Boolean,
    isMetronomeOn: Boolean,
    onToggleMetronome: () -> Unit,
    selectedSignature: String,
    onSelectSignature: (String) -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(180)) + expandVertically(tween(220)),
        exit = fadeOut(tween(150)) + shrinkVertically(tween(180)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .width(260.dp)
                .shadow(20.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1C2A38), Color(0xFF101820)))
                )
                .border(1.dp, Color(0x4D22D3EE), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            // Title & Toggle Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Métronome",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // Neon Toggle
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isMetronomeOn) Brush.horizontalGradient(listOf(NeonCyanLight, NeonCyan)) else Brush.linearGradient(listOf(Color(0x1AFFFFFF), Color(0x1AFFFFFF))))
                        .clickable { onToggleMetronome() }
                        .padding(2.dp),
                    contentAlignment = if (isMetronomeOn) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Signature Section
            Text(
                text = "SIGNATURE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextDim,
                letterSpacing = 0.6.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("2/4", "3/4", "4/4", "6/8").forEach { sig ->
                    val isSelected = selectedSignature == sig
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) Brush.verticalGradient(listOf(NeonCyanLight, NeonCyanDark)) else Brush.linearGradient(listOf(Color(0x0DFFFFFF), Color(0x08FFFFFF)))
                            )
                            .border(
                                1.dp,
                                if (isSelected) Color.Transparent else Color(0x1AFFFFFF),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectSignature(sig) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sig,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF00232B) else TextDim
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Volume Section
            Text(
                text = "VOLUME CLIC",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextDim,
                letterSpacing = 0.6.sp
            )

            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                colors = SliderDefaults.colors(
                    thumbColor = NeonCyan,
                    activeTrackColor = NeonCyan,
                    inactiveTrackColor = Color(0x1AFFFFFF)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
