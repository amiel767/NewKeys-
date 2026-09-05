package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun BottomBar(
    masterTrack: com.example.model.TrackChannel,
    onMasterVolumeChange: (Float) -> Unit,
    onMasterFxClick: () -> Unit,
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
    
    // Chord Display (Afficheur d'accords)
    detectedChord: DetectedChord? = null,
    
    // Virtual Keyboard
    isKeyboardActive: Boolean,
    onToggleKeyboard: () -> Unit,
    onKeyboardHandleClick: () -> Unit,
    onKeyboardDrag: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val barHeight = 44.dp
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // ================= 1. REC BUTTON =================
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
                .width(if (isRecording) 78.dp else 50.dp)
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

        // ================= 2. BPM BOX =================
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
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                val job = coroutineScope.launch {
                                    onBpmChange(-1)
                                    delay(400)
                                    while (isActive) {
                                        onBpmChange(-1)
                                        delay(70)
                                    }
                                }
                                tryAwaitRelease()
                                job.cancel()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "−", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            Box(
                modifier = Modifier.padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$bpm",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonCyanLight,
                        lineHeight = 13.sp
                    )
                    Text(
                        text = "BPM",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDim,
                        lineHeight = 8.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(22.dp)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                val job = coroutineScope.launch {
                                    onBpmChange(1)
                                    delay(400)
                                    while (isActive) {
                                        onBpmChange(1)
                                        delay(70)
                                    }
                                }
                                tryAwaitRelease()
                                job.cancel()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }

        // ================= 3. METRONOME BUTTON =================
        val metroBg by animateColorAsState(
            targetValue = if (isMetronomeOn) Color(0xFF0F394A) else DarkSurface,
            label = "metro_bg"
        )
        val metroBorder by animateColorAsState(
            targetValue = if (isMetronomeOn) NeonCyan else BorderSubtle,
            label = "metro_border"
        )

        Box(
            modifier = Modifier
                .width(42.dp)
                .height(barHeight)
                .clip(RoundedCornerShape(9.dp))
                .background(metroBg)
                .border(1.dp, metroBorder, RoundedCornerShape(9.dp))
                .clickable { onToggleMetroPanel() }
                .testTag("btn_metronome"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                val w = size.width
                val h = size.height

                val bodyPath = Path().apply {
                    moveTo(w * 0.28f, h * 0.90f)
                    lineTo(w * 0.42f, h * 0.18f)
                    lineTo(w * 0.58f, h * 0.18f)
                    lineTo(w * 0.72f, h * 0.90f)
                    close()
                }

                drawPath(
                    path = bodyPath,
                    color = if (isMetronomeOn) NeonCyan.copy(alpha = 0.25f) else Color(0x18FFFFFF)
                )
                drawPath(
                    path = bodyPath,
                    color = if (isMetronomeOn) NeonCyan else TextDim,
                    style = Stroke(width = 1.3f)
                )

                val armAngle = if (isMetronomeOn) 0.35f else 0.0f
                val pivotX = w * 0.50f
                val pivotY = h * 0.85f
                val topArmX = pivotX + kotlin.math.sin(armAngle) * (h * 0.68f)
                val topArmY = pivotY - kotlin.math.cos(armAngle) * (h * 0.68f)

                drawLine(
                    color = if (isMetronomeOn) Color.White else TextPrimary,
                    start = Offset(pivotX, pivotY),
                    end = Offset(topArmX, topArmY),
                    strokeWidth = 1.6f,
                    cap = StrokeCap.Round
                )
            }
        }

        // ================= 4. AFFICHEUR D'ACCORDS (CHORD DISPLAY BAR) =================
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(barHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            if (isKeyboardActive) Color(0xFF192538) else Color(0xFF161E2E),
                            if (isKeyboardActive) Color(0xFF121B2B) else Color(0xFF0E131E)
                        )
                    )
                )
                .border(
                    1.dp,
                    if (isKeyboardActive) NeonCyan.copy(alpha = 0.8f) else Color(0x3300E5FF),
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 6.dp, vertical = 3.dp)
                .testTag("chord_display_box"),
            contentAlignment = Alignment.CenterStart
        ) {
            if (detectedChord != null) {
                // Real-time Jazz & Pop Chord Display
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = detectedChord.primaryName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonCyanLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = detectedChord.formula,
                        fontSize = 7.sp,
                        color = NeonCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isKeyboardActive) "CLAVIER" else "ACCORDS",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isKeyboardActive) NeonCyan else Color(0x66FFFFFF)
                    )
                }
            }
        }

        // ================= 5. MASTER FADER =================
        Row(
            modifier = Modifier
                .weight(1f)
                .height(barHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0A0E15))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("M", color = Color(0xFF8E95A5), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(4.dp))
            Slider(
                value = masterTrack.volume,
                onValueChange = onMasterVolumeChange,
                modifier = Modifier.weight(0.25f).height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = NeonCyan,
                    activeTrackColor = NeonCyan,
                    inactiveTrackColor = Color(0xFF1E2238)
                )
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF161C28))
                    .border(1.dp, Color(0xFF2C3242), RoundedCornerShape(4.dp))
                    .clickable { onMasterFxClick() },
                contentAlignment = Alignment.Center
            ) {
                Text("FX", color = NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ================= 6. KEYBOARD TOGGLE BUTTON =================
        val keyboardBg by animateColorAsState(
            targetValue = if (isKeyboardActive) Color(0xFF0F394A) else DarkSurface,
            label = "kb_bg"
        )
        val keyboardBorder by animateColorAsState(
            targetValue = if (isKeyboardActive) NeonCyan else BorderSubtle,
            label = "kb_border"
        )

        Box(
            modifier = Modifier
                .width(44.dp)
                .height(barHeight)
                .clip(RoundedCornerShape(9.dp))
                .background(keyboardBg)
                .border(1.dp, keyboardBorder, RoundedCornerShape(9.dp))
                .clickable { onToggleKeyboard() }
                .testTag("btn_toggle_keyboard"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(24.dp, 18.dp)) {
                val w = size.width
                val h = size.height

                val keyOutlineColor = if (isKeyboardActive) NeonCyan else TextDim
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
    val allSignatures = listOf(
        "2/4", "3/4", "4/4", "5/4",
        "6/4", "7/4", "3/8", "5/8",
        "6/8", "7/8", "9/8", "12/8"
    )

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(180)) + expandVertically(tween(220)),
        exit = fadeOut(tween(150)) + shrinkVertically(tween(180)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
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
                    fontSize = 13.sp,
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
                text = "SIGNATURE RYTHMIQUE (TOUTES SIGNATURES)",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextDim,
                letterSpacing = 0.6.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 3 Rows x 4 Columns Grid of Signatures
            allSignatures.chunked(4).forEach { rowList ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    rowList.forEach { sig ->
                        val isSelected = selectedSignature == sig
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) Brush.verticalGradient(listOf(NeonCyanLight, NeonCyanDark)) else Brush.linearGradient(listOf(Color(0x0DFFFFFF), Color(0x08FFFFFF)))
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color.Transparent else Color(0x1AFFFFFF),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onSelectSignature(sig) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sig,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF00232B) else TextDim
                            )
                        }
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
