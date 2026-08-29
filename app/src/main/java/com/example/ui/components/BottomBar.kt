package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun BottomBar(
    isRecording: Boolean,
    recordingDuration: Int = 0,
    lastRecordedFile: String? = null,
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
    
    // Style / Arranger Controls (.sty)
    isStylePlaying: Boolean = false,
    isSyncStartActive: Boolean = false,
    activeStyleSection: String = "MAIN A",
    selectedStyleName: String = "-",
    onOpenStyleDialog: () -> Unit = {},
    onToggleSyncStart: () -> Unit = {},
    onTriggerStyleSection: (String) -> Unit = {},
    
    // Virtual Keyboard
    isKeyboardActive: Boolean,
    onToggleKeyboard: () -> Unit,
    onKeyboardHandleClick: () -> Unit,
    onKeyboardDrag: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val barHeight = 36.dp
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // 1. REC BUTTON WITH SOFT BLINKING LED
        val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
        val recPulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rec_pulse_alpha"
        )

        val recBrush = if (isRecording) {
            Brush.verticalGradient(listOf(Color(0xFFFF1E1E), Color(0xFFB30000)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFFF4B4B), RecRed))
        }

        Box(
            modifier = Modifier
                .width(if (isRecording) 78.dp else 52.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(9.dp))
                .background(recBrush)
                .border(1.dp, Color(0x4DFFFFFF), RoundedCornerShape(9.dp))
                .clickable { onToggleRecording() }
                .testTag("btn_rec"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .shadow(if (isRecording) 6.dp else 0.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) Color.White.copy(alpha = recPulseAlpha) else Color.White
                        )
                )

                val mins = recordingDuration / 60
                val secs = recordingDuration % 60
                val timeStr = String.format("%02d:%02d", mins, secs)

                Text(
                    text = if (isRecording) timeStr else "REC",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }

        // 2. BPM BOX WITH FAST LONG-PRESS SCROLLING
        Row(
            modifier = Modifier
                .height(barHeight)
                .clip(RoundedCornerShape(9.dp))
                .background(DarkSurface)
                .border(1.dp, BorderSubtle, RoundedCornerShape(9.dp))
                .testTag("bpm_box"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .fillMaxHeight()
                    .background(Color(0x1022D3EE))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onBpmChange(-1)
                                val job = coroutineScope.launch {
                                    delay(300)
                                    while (isActive) {
                                        onBpmChange(-1)
                                        delay(60)
                                    }
                                }
                                tryAwaitRelease()
                                job.cancel()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "−", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = NeonCyan)
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .defaultMinSize(minWidth = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$bpm",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    lineHeight = 12.sp
                )
                Text(
                    text = "BPM",
                    fontSize = 6.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDim2,
                    letterSpacing = 0.5.sp,
                    lineHeight = 7.sp
                )
            }

            Box(
                modifier = Modifier
                    .width(22.dp)
                    .fillMaxHeight()
                    .background(Color(0x1022D3EE))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onBpmChange(1)
                                val job = coroutineScope.launch {
                                    delay(300)
                                    while (isActive) {
                                        onBpmChange(1)
                                        delay(60)
                                    }
                                }
                                tryAwaitRelease()
                                job.cancel()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "+", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = NeonCyan)
            }
        }

        // 3. MINIMALIST METRONOME BUTTON
        Box(
            modifier = Modifier
                .height(barHeight)
                .clip(RoundedCornerShape(9.dp))
                .background(DarkSurface)
                .border(1.dp, if (isMetronomeOn) NeonCyan else BorderSubtle, RoundedCornerShape(9.dp))
                .clickable { onToggleMetroPanel() }
                .padding(horizontal = 7.dp)
                .testTag("btn_metro"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Canvas(modifier = Modifier.size(14.dp)) {
                    val w = size.width
                    val h = size.height

                    val bodyPath = Path().apply {
                        moveTo(w * 0.35f, h * 0.15f)
                        lineTo(w * 0.65f, h * 0.15f)
                        lineTo(w * 0.85f, h * 0.90f)
                        lineTo(w * 0.15f, h * 0.90f)
                        close()
                    }
                    drawPath(
                        path = bodyPath,
                        color = if (isMetronomeOn) NeonCyan.copy(alpha = 0.3f) else Color(0x22FFFFFF)
                    )
                    drawPath(
                        path = bodyPath,
                        color = if (isMetronomeOn) NeonCyan else TextDim,
                        style = Stroke(width = 1.2f)
                    )
                    drawLine(
                        color = if (isMetronomeOn) NeonCyanLight else TextPrimary,
                        start = Offset(w * 0.5f, h * 0.85f),
                        end = Offset(w * 0.5f + (if (isMetronomeOn) 3f else 0f), h * 0.25f),
                        strokeWidth = 1.4f,
                        cap = StrokeCap.Round
                    )
                }

                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (isMetronomeOn) NeonCyan else TextDim2)
                )
            }
        }

        // ================= 4. STYLE / ARRANGER SYNC & CONTROLS DECK =================
        // Added in empty space next to piano per user request
        Row(
            modifier = Modifier
                .height(barHeight)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF141722))
                .border(1.dp, Color(0x334F46E5), RoundedCornerShape(9.dp))
                .padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // [ .STY ] File Selector Button
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFF4F46E5), Color(0xFF6366F1)))
                    )
                    .border(1.dp, Color(0xFF818CF8), RoundedCornerShape(6.dp))
                    .clickable { onOpenStyleDialog() }
                    .padding(horizontal = 6.dp)
                    .testTag("btn_sty_file"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "🎵", fontSize = 8.sp)
                    Text(
                        text = if (selectedStyleName.length > 7) selectedStyleName.take(7) + "…" else if (selectedStyleName != "-") selectedStyleName else ".STY",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            // SYNC START Button
            StyleControlButton(
                label = "SYNC",
                isActive = isSyncStartActive,
                activeColor = SoloAmber,
                onClick = onToggleSyncStart,
                testTag = "btn_style_sync"
            )

            // INTRO Button
            StyleControlButton(
                label = "INTRO",
                isActive = isStylePlaying && activeStyleSection == "INTRO",
                activeColor = NeonCyan,
                onClick = { onTriggerStyleSection("INTRO") },
                testTag = "btn_style_intro"
            )

            // MAIN A Button
            StyleControlButton(
                label = "MAIN A",
                isActive = isStylePlaying && activeStyleSection == "MAIN A",
                activeColor = Color(0xFF10B981),
                onClick = { onTriggerStyleSection("MAIN A") },
                testTag = "btn_style_main_a"
            )

            // MAIN B Button
            StyleControlButton(
                label = "MAIN B",
                isActive = isStylePlaying && activeStyleSection == "MAIN B",
                activeColor = Color(0xFF06B6D4),
                onClick = { onTriggerStyleSection("MAIN B") },
                testTag = "btn_style_main_b"
            )

            // FILL IN Button
            StyleControlButton(
                label = "FILL",
                isActive = isStylePlaying && activeStyleSection == "FILL IN",
                activeColor = NeonMagenta,
                onClick = { onTriggerStyleSection("FILL IN") },
                testTag = "btn_style_fill"
            )

            // ENDING Button
            StyleControlButton(
                label = "END",
                isActive = isStylePlaying && activeStyleSection == "ENDING",
                activeColor = MuteRed,
                onClick = { onTriggerStyleSection("ENDING") },
                testTag = "btn_style_ending"
            )
        }

        // 5. RETRACTABLE KEYBOARD GRABBER BAR (Clean, no text)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(barHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF19202E), Color(0xFF121622))
                    )
                )
                .border(1.dp, Color(0x2E67E8F9), RoundedCornerShape(10.dp))
                .clickable { onKeyboardHandleClick() }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onKeyboardDrag(dragAmount.y)
                    }
                }
                .padding(horizontal = 8.dp)
                .testTag("kb_handle"),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(modifier = Modifier.width(20.dp).height(2.5.dp).clip(RoundedCornerShape(1.5.dp)).background(Color(0x6667E8F9)))
                Box(modifier = Modifier.width(20.dp).height(2.5.dp).clip(RoundedCornerShape(1.5.dp)).background(Color(0x6667E8F9)))
                Box(modifier = Modifier.width(20.dp).height(2.5.dp).clip(RoundedCornerShape(1.5.dp)).background(Color(0x6667E8F9)))
            }
        }

        // 6. PIANO LOGO TOGGLE BUTTON (Aligned below Master Fader)
        val kbBtnBg by animateColorAsState(
            targetValue = if (isKeyboardActive) Color(0xFF1E3A5F) else DarkSurface,
            label = "kb_btn_bg"
        )
        val kbBorderColor by animateColorAsState(
            targetValue = if (isKeyboardActive) NeonCyan else BorderSubtle,
            label = "kb_border_color"
        )

        Box(
            modifier = Modifier
                .width(44.dp)
                .height(barHeight)
                .clip(RoundedCornerShape(9.dp))
                .background(kbBtnBg)
                .border(1.2.dp, kbBorderColor, RoundedCornerShape(9.dp))
                .clickable { onToggleKeyboard() }
                .testTag("btn_piano_toggle"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(18.dp)) {
                val w = size.width
                val h = size.height

                val keyOutlineColor = if (isKeyboardActive) NeonCyanLight else TextDim
                drawRoundRect(
                    color = keyOutlineColor,
                    topLeft = Offset(1f, 2f),
                    size = Size(w - 2f, h - 4f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    style = Stroke(width = 1.4f)
                )

                val keySpacing = (w - 2f) / 4f
                for (i in 1..3) {
                    drawLine(
                        color = keyOutlineColor.copy(alpha = 0.7f),
                        start = Offset(1f + i * keySpacing, 2f),
                        end = Offset(1f + i * keySpacing, h - 2f),
                        strokeWidth = 1f
                    )
                }

                val blackKeyColor = if (isKeyboardActive) NeonCyan else Color.White
                drawRect(
                    color = blackKeyColor,
                    topLeft = Offset(1f + keySpacing * 0.7f, 2f),
                    size = Size(keySpacing * 0.6f, (h - 4f) * 0.55f)
                )
                drawRect(
                    color = blackKeyColor,
                    topLeft = Offset(1f + keySpacing * 1.7f, 2f),
                    size = Size(keySpacing * 0.6f, (h - 4f) * 0.55f)
                )
                drawRect(
                    color = blackKeyColor,
                    topLeft = Offset(1f + keySpacing * 2.7f, 2f),
                    size = Size(keySpacing * 0.6f, (h - 4f) * 0.55f)
                )
            }
        }
    }
}

/**
 * Hyper-reactive tactile button for Style / Arranger Sync & Section controls
 */
@Composable
private fun StyleControlButton(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    val btnBg by animateColorAsState(
        targetValue = if (isActive) activeColor.copy(alpha = 0.28f) else Color(0x0CFFFFFF),
        animationSpec = tween(60),
        label = "style_btn_bg"
    )
    val borderColor = if (isActive) activeColor else Color(0x1EFFFFFF)
    val textColor = if (isActive) activeColor else TextDim

    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(btnBg)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = textColor
        )
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

            Text(
                text = "SIGNATURE RYTHMIQUE",
                fontSize = 9.5.sp,
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
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sig,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF00232B) else TextDim
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "VOLUME DU CLIC",
                fontSize = 9.5.sp,
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
