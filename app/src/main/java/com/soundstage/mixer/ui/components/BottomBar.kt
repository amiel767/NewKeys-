package com.soundstage.mixer.ui.components

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun BottomBar(
    masterTrack: com.soundstage.mixer.model.TrackChannel,
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
    selectedKey: String = "C",
    onSelectKey: (String) -> Unit = {},
    selectedScaleMode: String = "Majeur",
    onSelectScaleMode: (String) -> Unit = {},
    useFlats: Boolean = false,
    
    // Snapshots / Sub-Scenes (Section 2)
    isSnapshotArmMode: Boolean = false,
    onToggleSnapshotArm: () -> Unit = {},
    activeSnapshotSlot: String? = null,
    snapshots: Map<String, com.soundstage.mixer.model.SubSceneSnapshot> = emptyMap(),
    onSnapshotSlotClick: (String) -> Unit = {},
    snapshotCustomNames: Map<String, String> = emptyMap(),
    onRenameSnapshotSlot: (String, String) -> Unit = { _, _ -> },
    snapshotTransitionProgress: Float = 1.0f,
    
    // Chord Display (Afficheur d'accords)
    detectedChord: DetectedChord? = null,
    
    // Virtual Keyboard
    isKeyboardActive: Boolean,
    onToggleKeyboard: () -> Unit,
    isLayerActive: Boolean = false,
    onToggleLayer: () -> Unit = {},
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

        // ================= 3. FUSED METRONOME & SIGNATURE/KEY BUTTON =================
        val metroBg by animateColorAsState(
            targetValue = if (isMetronomeOn) Color(0xFF0F394A) else DarkSurface,
            label = "metro_bg"
        )
        val metroBorder by animateColorAsState(
            targetValue = if (isMetronomeOn) NeonCyan else BorderSubtle,
            label = "metro_border"
        )

        Row(
            modifier = Modifier
                .height(barHeight)
                .clip(RoundedCornerShape(9.dp))
                .background(metroBg)
                .border(1.dp, metroBorder, RoundedCornerShape(9.dp))
                .clickable { onToggleMetroPanel() }
                .padding(horizontal = 8.dp)
                .testTag("btn_metronome"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Left: Metronome Icon
            Canvas(modifier = Modifier.size(18.dp)) {
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

            // Subtle vertical separator
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(if (isMetronomeOn) NeonCyan.copy(alpha = 0.4f) else Color(0x22FFFFFF))
            )

            // Right: Signature & Key indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = metroSignature,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isMetronomeOn) NeonCyanLight else TextPrimary
                )
                Text(
                    text = "·",
                    fontSize = 10.sp,
                    color = Color(0x44FFFFFF)
                )
                val formattedKey = if (useFlats) {
                    when (selectedKey) {
                        "C#" -> "Db"
                        "D#" -> "Eb"
                        "F#" -> "Gb"
                        "G#" -> "Ab"
                        "A#" -> "Bb"
                        else -> selectedKey
                    }
                } else selectedKey
                Text(
                    text = if (selectedScaleMode.contains("Min", ignoreCase = true)) "${formattedKey}m" else formattedKey,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0x66FFFFFF) // Gris fade éteint comme demandé
                )
            }
        }

        // ================= 4. SNAPSHOTS / SUB-SCENES RACK (SECTION 2) =================
        // Slow rotating vivid RGB LED phase for active slot halo
        val ledInfiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "rgb_led_glow")
        val ledHue by ledInfiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "led_hue"
        )
        val vividLedColor = Color.hsv(ledHue, 0.95f, 1.0f)

        var renamingSlotKey by remember { mutableStateOf<String?>(null) }
        var renamingSlotCurrentName by remember { mutableStateOf("") }

        if (renamingSlotKey != null) {
            AlertDialog(
                onDismissRequest = { renamingSlotKey = null },
                title = {
                    Text(
                        text = "Renommer la case",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Entrez le nouveau nom pour ce snapshot :",
                            color = Color(0xFFB0BEC5),
                            fontSize = 12.sp
                        )
                        OutlinedTextField(
                            value = renamingSlotCurrentName,
                            onValueChange = { renamingSlotCurrentName = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = Color(0x66FFFFFF),
                                cursorColor = NeonCyan
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_rename_snapshot")
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val key = renamingSlotKey
                            if (key != null && renamingSlotCurrentName.isNotBlank()) {
                                onRenameSnapshotSlot(key, renamingSlotCurrentName.trim())
                            }
                            renamingSlotKey = null
                        },
                        modifier = Modifier.testTag("btn_confirm_rename_snapshot")
                    ) {
                        Text("Valider", color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { renamingSlotKey = null }
                    ) {
                        Text("Annuler", color = Color(0xFF8E95A5))
                    }
                },
                containerColor = Color(0xFF161B29),
                shape = RoundedCornerShape(16.dp)
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .height(barHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0C101A))
                .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(10.dp))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // REC (ARM) Material You Floppy Disk Save Button (matching save.jpg)
            val armBlinkAlpha by animateFloatAsState(
                targetValue = if (isSnapshotArmMode) 1.0f else 0.40f,
                animationSpec = infiniteRepeatable(
                    animation = tween(450),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "arm_blink"
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSnapshotArmMode) {
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFFF2A55), Color(0xFF4A0014)),
                                radius = 45f
                            )
                        } else {
                            SolidColor(Color(0xFF161A26))
                        }
                    )
                    .border(
                        1.2.dp,
                        if (isSnapshotArmMode) Color(0xFFFF2A55).copy(alpha = armBlinkAlpha) else Color(0x33FFFFFF),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onToggleSnapshotArm() }
                    .testTag("btn_snapshot_arm"),
                contentAlignment = Alignment.Center
            ) {
                // Material You Floppy Disk (Save) Icon - flat colors, geometric forms, no textures
                FloppyDiskIcon(
                    isArmed = isSnapshotArmMode,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 5 Snapshot Slots: Défaut, Snapshot 1, Snapshot 2, Snapshot 3, END (renamable on long press)
            val slotKeys = listOf("slot_default", "slot_1", "slot_2", "slot_3", "slot_end")
            slotKeys.forEach { slotKey ->
                val defaultLabel = when (slotKey) {
                    "slot_default" -> "Défaut"
                    "slot_1" -> "Snapshot 1"
                    "slot_2" -> "Snapshot 2"
                    "slot_3" -> "Snapshot 3"
                    "slot_end" -> "END"
                    else -> slotKey
                }
                val displayName = snapshotCustomNames[slotKey] ?: defaultLabel

                // Key aliases for backward compatibility with previously saved snapshots
                val keyAliases = when (slotKey) {
                    "slot_default" -> listOf("slot_default", "INTRO", "Intro", "Défaut", "Default")
                    "slot_1" -> listOf("slot_1", "Snapshot 1", "S2")
                    "slot_2" -> listOf("slot_2", "Snapshot 2", "S3")
                    "slot_3" -> listOf("slot_3", "Snapshot 3", "S4")
                    "slot_end" -> listOf("slot_end", "END", "End")
                    else -> listOf(slotKey)
                }
                val isActive = keyAliases.any { activeSnapshotSlot == it }
                val isSaved = keyAliases.any { snapshots.containsKey(it) }

                val slotBgBrush = if (isActive) {
                    Brush.verticalGradient(listOf(Color(0xFF00E5FF), Color(0xFF0088FF)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF111522), Color(0xFF111522)))
                }

                val slotTextColor = when {
                    isActive -> Color(0xFF001E36)
                    isSaved -> Color(0xFFE2E8F0)
                    else -> TextDim
                }
                val slotFontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(7.dp))
                        .background(slotBgBrush)
                        .pointerInput(slotKey, displayName) {
                            detectTapGestures(
                                onTap = { onSnapshotSlotClick(slotKey) },
                                onLongPress = {
                                    renamingSlotKey = slotKey
                                    renamingSlotCurrentName = displayName
                                }
                            )
                        }
                        .drawWithContent {
                            drawContent()
                            val w = this@drawWithContent.size.width
                            val h = this@drawWithContent.size.height
                            val r = 7.dp.toPx()
                            val rrect = androidx.compose.ui.geometry.RoundRect(
                                left = 1f, top = 1f, right = w - 1f, bottom = h - 1f,
                                radiusX = r, radiusY = r
                            )
                            val p = Path().apply { addRoundRect(rrect) }

                            if (isActive) {
                                if (snapshotTransitionProgress < 1.0f) {
                                    val pm = PathMeasure()
                                    pm.setPath(p, false)
                                    val totalLen = pm.length
                                    val curLen = (totalLen * snapshotTransitionProgress.coerceIn(0.01f, 1.0f))
                                    val seg = Path()
                                    pm.getSegment(0f, curLen, seg, true)
                                    drawPath(
                                        path = seg,
                                        color = Color.White,
                                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                } else {
                                    drawPath(
                                        path = p,
                                        color = Color.White,
                                        style = Stroke(width = 1.5.dp.toPx())
                                    )
                                }
                            } else if (isSaved) {
                                drawPath(
                                    path = p,
                                    color = Color.White.copy(alpha = 0.3f),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            } else {
                                drawPath(
                                    path = p,
                                    color = Color(0x22FFFFFF),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            }
                        }
                        .testTag("btn_snapshot_$slotKey"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName,
                        fontSize = if (displayName.length > 7) 7.5.sp else 9.5.sp,
                        fontWeight = slotFontWeight,
                        color = slotTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // ================= 5. MASTER FADER (COMPACT FIXED WIDTH) =================
        Row(
            modifier = Modifier
                .width(150.dp)
                .height(barHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0A0E15))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("M", color = Color(0xFF8E95A5), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(2.dp))
            Slider(
                value = masterTrack.volume,
                onValueChange = onMasterVolumeChange,
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = NeonCyan,
                    activeTrackColor = NeonCyan,
                    inactiveTrackColor = Color(0xFF1E2238)
                )
            )
            Spacer(modifier = Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF161C28))
                    .border(1.dp, Color(0xFF2C3242), RoundedCornerShape(4.dp))
                    .clickable { onMasterFxClick() },
                contentAlignment = Alignment.Center
            ) {
                Text("FX", color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ================= 6. KEYBOARD & LAYER TOGGLE BUTTONS =================
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Layer Toggle Button (Appears next to keyboard icon when keyboard is active)
            androidx.compose.animation.AnimatedVisibility(
                visible = isKeyboardActive,
                enter = fadeIn(tween(220)) + expandHorizontally(tween(220)) + scaleIn(),
                exit = fadeOut(tween(180)) + shrinkHorizontally(tween(180)) + scaleOut()
            ) {
                val layerBg by animateColorAsState(
                    targetValue = if (isLayerActive) Color(0xFF0F394A) else DarkSurface,
                    label = "layer_bg"
                )
                val layerBorder by animateColorAsState(
                    targetValue = if (isLayerActive) NeonCyan else BorderSubtle,
                    label = "layer_border"
                )

                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(9.dp))
                        .background(layerBg)
                        .border(1.dp, layerBorder, RoundedCornerShape(9.dp))
                        .clickable { onToggleLayer() }
                        .testTag("btn_toggle_layer"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(20.dp, 16.dp)) {
                        val w = size.width
                        val h = size.height
                        val strokeCol = if (isLayerActive) NeonCyan else TextDim

                        // Top Layer
                        val p1 = Path().apply {
                            moveTo(w * 0.5f, h * 0.10f)
                            lineTo(w * 0.90f, h * 0.32f)
                            lineTo(w * 0.5f, h * 0.54f)
                            lineTo(w * 0.10f, h * 0.32f)
                            close()
                        }
                        drawPath(p1, color = strokeCol, style = Stroke(width = 1.4f))

                        // Middle Layer Curve
                        val p2 = Path().apply {
                            moveTo(w * 0.12f, h * 0.52f)
                            lineTo(w * 0.5f, h * 0.74f)
                            lineTo(w * 0.88f, h * 0.52f)
                        }
                        drawPath(p2, color = strokeCol, style = Stroke(width = 1.4f))

                        // Bottom Layer Curve
                        val p3 = Path().apply {
                            moveTo(w * 0.12f, h * 0.72f)
                            lineTo(w * 0.5f, h * 0.94f)
                            lineTo(w * 0.88f, h * 0.72f)
                        }
                        drawPath(p3, color = strokeCol, style = Stroke(width = 1.4f))
                    }
                }
            }

            // Keyboard Toggle Button
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
    selectedKey: String = "C",
    onSelectKey: (String) -> Unit = {},
    selectedScaleMode: String = "Majeur",
    onSelectScaleMode: (String) -> Unit = {},
    useFlats: Boolean = false,
    modifier: Modifier = Modifier
) {
    val allSignatures = listOf(
        "2/4", "3/4", "4/4", "5/4",
        "6/4", "7/4", "3/8", "5/8",
        "6/8", "7/8", "9/8", "12/8"
    )
    val chromaticKeys = listOf(
        "C", "C#", "D", "D#", "E", "F",
        "F#", "G", "G#", "A", "A#", "B"
    )
    val displayedKeys = listOf(
        "C", if (useFlats) "Db" else "C#", "D", if (useFlats) "Eb" else "D#", "E", "F",
        if (useFlats) "Gb" else "F#", "G", if (useFlats) "Ab" else "G#", "A", if (useFlats) "Bb" else "A#", "B"
    )

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(180)) + expandVertically(tween(220)),
        exit = fadeOut(tween(150)) + shrinkVertically(tween(180)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .width(288.dp)
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
                    text = "Métronome & Tonalité",
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

            // TONALITÉ (ROOT KEY)
            Text(
                text = "TONALITÉ (ROOT KEY)",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextDim,
                letterSpacing = 0.6.sp
            )

            Spacer(modifier = Modifier.height(5.dp))

            chromaticKeys.zip(displayedKeys).chunked(6).forEach { rowPairs ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowPairs.forEach { (actualNote, displayedNote) ->
                        val isSelected = selectedKey.equals(actualNote, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) Brush.verticalGradient(listOf(NeonCyanLight, NeonCyanDark))
                                    else Brush.linearGradient(listOf(Color(0x0DFFFFFF), Color(0x08FFFFFF)))
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color.Transparent else Color(0x1AFFFFFF),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onSelectKey(actualNote) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayedNote,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF00232B) else TextDim
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // MODE TONALITÉ (MAJEUR / MINEUR)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Majeur", "Mineur").forEach { mode ->
                    val isModeSelected = selectedScaleMode.equals(mode, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isModeSelected) Brush.verticalGradient(listOf(NeonCyanLight, NeonCyanDark))
                                else Brush.linearGradient(listOf(Color(0x0DFFFFFF), Color(0x08FFFFFF)))
                            )
                            .border(
                                1.dp,
                                if (isModeSelected) Color.Transparent else Color(0x1AFFFFFF),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onSelectScaleMode(mode) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.uppercase(),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isModeSelected) Color(0xFF00232B) else TextDim
                        )
                    }
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

/**
 * Material You Floppy Disk Save Icon
 * Geometric flat shapes, vibrant solid colors, no textures (matching save.jpg).
 */
@Composable
fun FloppyDiskIcon(
    isArmed: Boolean,
    modifier: Modifier = Modifier
) {
    val diskColor = if (isArmed) Color(0xFFFF2A55) else Color(0xFF8E95A5)
    val innerCutoutColor = Color(0xFF0C101A)
    val labelColor = if (isArmed) Color(0xFFFFB3BA) else Color(0xFFECEFF1)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val corner = w * 0.12f
        val notch = w * 0.22f

        // Outer disk body with chamfered top-right corner
        val bodyPath = Path().apply {
            moveTo(corner, 0f)
            lineTo(w - notch, 0f)
            lineTo(w, notch)
            lineTo(w, h - corner)
            arcTo(
                androidx.compose.ui.geometry.Rect(w - 2 * corner, h - 2 * corner, w, h),
                0f, 90f, false
            )
            lineTo(corner, h)
            arcTo(
                androidx.compose.ui.geometry.Rect(0f, h - 2 * corner, 2 * corner, h),
                90f, 90f, false
            )
            lineTo(0f, corner)
            arcTo(
                androidx.compose.ui.geometry.Rect(0f, 0f, 2 * corner, 2 * corner),
                180f, 90f, false
            )
            close()
        }
        drawPath(bodyPath, color = diskColor)

        // Top shutter cutout
        val sliderW = w * 0.58f
        val sliderH = h * 0.38f
        val sliderLeft = (w - sliderW) / 2f
        drawRoundRect(
            color = innerCutoutColor,
            topLeft = Offset(sliderLeft, 0f),
            size = Size(sliderW, sliderH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner * 0.25f, corner * 0.25f)
        )

        // Shutter window cutout
        val winW = sliderW * 0.26f
        val winH = sliderH * 0.55f
        val winLeft = sliderLeft + sliderW * 0.18f
        val winTop = sliderH * 0.22f
        drawRoundRect(
            color = diskColor,
            topLeft = Offset(winLeft, winTop),
            size = Size(winW, winH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
        )

        // Bottom label rectangle
        val labelW = w * 0.72f
        val labelH = h * 0.40f
        val labelLeft = (w - labelW) / 2f
        val labelTop = h - labelH
        drawRoundRect(
            color = labelColor,
            topLeft = Offset(labelLeft, labelTop),
            size = Size(labelW, labelH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner * 0.35f, corner * 0.35f)
        )

        // Write notch hole
        val notchHole = w * 0.11f
        drawRect(
            color = innerCutoutColor,
            topLeft = Offset(w * 0.10f, h - notchHole - 2f),
            size = Size(notchHole, notchHole)
        )
    }
}

