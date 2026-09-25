package com.soundstage.mixer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalDensity
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
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF161924))
                .border(1.dp, Color(0xFF1E2232), RoundedCornerShape(12.dp))
                .testTag("bpm_box"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1F2333))
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
                Text(text = "−", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
            }

            Box(
                modifier = Modifier.padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$bpm",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        lineHeight = 13.sp
                    )
                    Text(
                        text = "BPM",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7A8096),
                        lineHeight = 8.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1F2333))
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
                Text(text = "+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
            }
        }

        // ================= 3. FUSED METRONOME & SIGNATURE/KEY BUTTON =================
        val metroBg by animateColorAsState(
            targetValue = if (isMetronomeOn) Color(0xFF0F394A) else Color(0xFF161924),
            label = "metro_bg"
        )
        val metroBorder by animateColorAsState(
            targetValue = if (isMetronomeOn) NeonCyan else Color(0xFF1E2232),
            label = "metro_border"
        )

        Row(
            modifier = Modifier
                .height(barHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(metroBg)
                .border(1.dp, metroBorder, RoundedCornerShape(12.dp))
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
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF13151F))
                .border(1.dp, Color(0xFF1E2232), RoundedCornerShape(12.dp))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Floppy Disk Save Button (Studio Dark)
            val armBlinkAlpha by animateFloatAsState(
                targetValue = if (isSnapshotArmMode) 1.0f else 0.40f,
                animationSpec = infiniteRepeatable(
                    animation = tween(450),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "arm_blink"
            )

            val diskBg = if (isSnapshotArmMode) Color(0x33FF2A55) else Color(0xFF1C202C)
            val diskBorder = if (isSnapshotArmMode) Color(0xFFFF2A55).copy(alpha = armBlinkAlpha) else Color(0xFF282E40)

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(diskBg)
                    .border(
                        1.dp,
                        diskBorder,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onToggleSnapshotArm() }
                    .testTag("btn_snapshot_arm"),
                contentAlignment = Alignment.Center
            ) {
                // Material You Floppy Disk (Save) Icon - crisp studio white/silver
                FloppyDiskIcon(
                    isArmed = isSnapshotArmMode,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 5 Snapshot Slots: Défaut, Snapshot 1, Snapshot 2, Snapshot 3, END
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

                val slotBg = if (isActive) Color(0xFF384058) else Color(0xFF2C3246)
                val slotBorder = if (isActive) Color(0xFF4A5578) else Color(0xFF363E56)
                val slotTextColor = when {
                    isActive -> Color(0xFFE2E8F0)
                    isSaved -> Color(0xFFE2E8F0)
                    else -> Color(0xFF5B6175)
                }
                val slotFontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(7.dp))
                        .background(slotBg)
                        .border(1.dp, slotBorder, RoundedCornerShape(7.dp))
                        .pointerInput(slotKey, displayName) {
                            detectTapGestures(
                                onTap = { onSnapshotSlotClick(slotKey) },
                                onLongPress = {
                                    renamingSlotKey = slotKey
                                    renamingSlotCurrentName = displayName
                                }
                            )
                        }
                        .testTag("btn_snapshot_$slotKey"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName,
                        fontSize = if (displayName.length > 7) 7.5.sp else 9.sp,
                        fontWeight = slotFontWeight,
                        color = slotTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // ================= 5. MASTER FADER (CLEAN STUDIO SLIDER & FX) =================
        Row(
            modifier = Modifier
                .width(150.dp)
                .height(barHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF12141E))
                .border(1.dp, Color(0xFF1E2232), RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("M", color = Color(0xFF8E95A5), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(4.dp))

            // Studio Horizontal Master Fader
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val trackWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
                val thumbWidthDp = 12.dp
                val thumbWidthPx = with(LocalDensity.current) { thumbWidthDp.toPx() }
                val usableWidthPx = (trackWidthPx - thumbWidthPx).coerceAtLeast(1f)
                val masterVol = masterTrack.volume.coerceIn(0f, 1f)
                val thumbOffsetXDp = with(LocalDensity.current) { (masterVol * usableWidthPx).toDp() }

                val updateMasterVolume: (Float) -> Unit = { rawX ->
                    val newVol = ((rawX - thumbWidthPx / 2f) / usableWidthPx).coerceIn(0f, 1f)
                    onMasterVolumeChange(newVol)
                }

                // Master Track Container with unified, jitter-free tap & drag gesture
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(usableWidthPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                updateMasterVolume(down.position.x)

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    updateMasterVolume(change.position.x)
                                }
                            }
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Groove Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF141724))
                            .border(0.8.dp, Color(0x33FFFFFF), RoundedCornerShape(3.dp))
                    ) {
                        // Active Solid Studio Fill (clean sky blue, no generic multi-color gradient)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(thumbOffsetXDp + 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF38BDF8))
                        )
                    }

                    // Precision Studio Metal Thumb Knob
                    Box(
                        modifier = Modifier
                            .offset(x = thumbOffsetXDp)
                            .width(thumbWidthDp)
                            .height(22.dp)
                            .shadow(3.dp, RoundedCornerShape(3.dp))
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1), Color(0xFF94A3B8))
                                )
                            )
                            .border(1.dp, Color(0x77FFFFFF), RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Center slit indicator
                        Box(
                            modifier = Modifier
                                .width(1.5.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(0.5.dp))
                                .background(Color(0xFF1E293B))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF161C28))
                    .border(1.dp, Color(0xFF2C3242), RoundedCornerShape(6.dp))
                    .clickable { onMasterFxClick() },
                contentAlignment = Alignment.Center
            ) {
                Text("FX", color = Color(0xFF8E94A8), fontSize = 8.sp, fontWeight = FontWeight.Bold)
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
                    targetValue = if (isLayerActive) Color(0xFF0F394A) else Color(0xFF161924),
                    label = "layer_bg"
                )
                val layerBorder by animateColorAsState(
                    targetValue = if (isLayerActive) NeonCyan else Color(0xFF1E2232),
                    label = "layer_border"
                )

                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(10.dp))
                        .background(layerBg)
                        .border(1.dp, layerBorder, RoundedCornerShape(10.dp))
                        .clickable { onToggleLayer() }
                        .testTag("btn_toggle_layer"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(20.dp, 16.dp)) {
                        val w = size.width
                        val h = size.height
                        val strokeCol = if (isLayerActive) NeonCyan else Color(0xFF94A3B8)

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

            // Keyboard Toggle Button - Sleek Studio Button with 4 white notes and 3 black notes
            val (_, activeSlotLedColor) = rememberDynamicFaderHue(1)
            val kbdBg = if (isKeyboardActive) Color(0xFF1E2333) else Color(0xFF161922)
            val kbdBorder = if (isKeyboardActive) Color(0xFF4F6BF7) else Color(0xFF242938)

            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(kbdBg)
                    .border(1.2.dp, kbdBorder, RoundedCornerShape(10.dp))
                    .clickable { onToggleKeyboard() }
                    .testTag("btn_toggle_keyboard"),
                contentAlignment = Alignment.Center
            ) {
                // Precise 4 White Keys + 3 Black Keys Studio Piano Icon
                Canvas(modifier = Modifier.size(24.dp, 16.dp)) {
                    val w = size.width
                    val h = size.height
                    val cornerRadius = 2.dp.toPx()

                    // Piano Frame Base (4 White Keys background)
                    drawRoundRect(
                        color = if (isKeyboardActive) Color(0xFFF8FAFC) else Color(0xFFCBD5E1),
                        topLeft = Offset(0f, 0f),
                        size = Size(w, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
                    )

                    // 4 White keys dividers (3 internal lines)
                    val numWhiteKeys = 4
                    val whiteKeyWidth = w / numWhiteKeys.toFloat()
                    for (i in 1 until numWhiteKeys) {
                        val x = i * whiteKeyWidth
                        drawLine(
                            color = Color(0xFF64748B),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f
                        )
                    }

                    // Exactly 3 Black keys placed centered over the 3 white key dividing lines
                    val blackKeyWidth = whiteKeyWidth * 0.55f
                    val blackKeyHeight = h * 0.58f
                    val blackKeyColor = Color(0xFF0F172A)
                    val blackKeyDividers = listOf(1, 2, 3)

                    blackKeyDividers.forEach { dividerIdx ->
                        val x = dividerIdx * whiteKeyWidth - (blackKeyWidth / 2f)
                        drawRoundRect(
                            color = blackKeyColor,
                            topLeft = Offset(x, 0f),
                            size = Size(blackKeyWidth, blackKeyHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f, 1f)
                        )
                    }

                    // Outer border
                    drawRoundRect(
                        color = if (isKeyboardActive) Color(0xFF93C5FD) else Color(0xFF475569),
                        topLeft = Offset(0f, 0f),
                        size = Size(w, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
                        style = Stroke(width = 1.2f)
                    )
                }
            }
        }
    }
}

/**
 * Modern Metronome & Tonalité Floating Widget (Compact, Studio Minimalist)
 */
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
    val signatures = listOf("2/4", "3/4", "4/4", "5/4", "6/8", "7/8", "12/8")
    val rawKeys = if (useFlats) {
        listOf("Ab", "A", "Bb", "B", "C", "Db", "D", "Eb", "E", "F", "Gb", "G")
    } else {
        listOf("G#", "A", "A#", "B", "C", "C#", "D", "D#", "E", "F", "F#", "G")
    }

    val isMajor = selectedScaleMode.equals("Majeur", ignoreCase = true)

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(200)) + expandVertically(tween(220)),
        exit = fadeOut(tween(160)) + shrinkVertically(tween(180)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .width(270.dp)
                .shadow(16.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0F141C))
                .border(1.dp, Color(0x3322D3EE), RoundedCornerShape(14.dp))
                .padding(10.dp)
                .testTag("metronome_widget_panel")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ================= TOP ROW: SIGNATURE (LEFT) + SEPARATOR + TONALITÉ (RIGHT) =================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT COLUMN: Signature Horizontal Scroll / Selector
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Horizontal scrollable signatures
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            signatures.forEach { sig ->
                                val isSelected = selectedSignature == sig
                                Text(
                                    text = sig,
                                    fontSize = if (isSelected) 22.sp else 14.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF475569),
                                    modifier = Modifier
                                        .clickable { onSelectSignature(sig) }
                                        .padding(horizontal = 6.dp)
                                )
                            }
                        }

                        Text(
                            text = "Signature",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    // Vertical Separator Line
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight(0.7f)
                            .background(Color(0x33FFFFFF))
                    )

                    // RIGHT COLUMN: Tonalité Horizontal Scroll / Selector
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Horizontal scrollable keys
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            rawKeys.forEach { note ->
                                val isSelected = selectedKey.equals(note, ignoreCase = true)
                                Text(
                                    text = note,
                                    fontSize = if (isSelected) 24.sp else 14.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) NeonCyan else Color(0xFF475569),
                                    modifier = Modifier
                                        .clickable { onSelectKey(note) }
                                        .padding(horizontal = 6.dp)
                                )
                            }
                        }

                        Text(
                            text = "Tonalité",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // ================= BOTTOM ROW: MODE PILL (LEFT) + VOLUME SLIDER & METRO BTN (RIGHT) =================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Maj / Min Pill Switch
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1E2433))
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Majeur Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isMajor) NeonCyan else Color.Transparent)
                                .clickable { onSelectScaleMode("Majeur") }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Majeur",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMajor) Color(0xFF032830) else Color(0xFF64748B)
                            )
                        }

                        // Mineur Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (!isMajor) NeonCyan else Color.Transparent)
                                .clickable { onSelectScaleMode("Mineur") }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Mineur",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isMajor) Color(0xFF032830) else Color(0xFF64748B)
                            )
                        }
                    }

                    // Speaker Volume Icon + Slider
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Speaker Icon
                        Canvas(modifier = Modifier.size(16.dp, 14.dp)) {
                            val w = size.width
                            val h = size.height
                            val p = Path().apply {
                                moveTo(0f, h * 0.35f)
                                lineTo(w * 0.35f, h * 0.35f)
                                lineTo(w * 0.70f, h * 0.05f)
                                lineTo(w * 0.70f, h * 0.95f)
                                lineTo(w * 0.35f, h * 0.65f)
                                lineTo(0f, h * 0.65f)
                                close()
                            }
                            drawPath(p, color = Color(0xFF94A3B8))
                            // Sound waves
                            drawArc(
                                color = Color(0xFF94A3B8),
                                startAngle = -45f,
                                sweepAngle = 90f,
                                useCenter = false,
                                topLeft = Offset(w * 0.45f, h * 0.20f),
                                size = Size(w * 0.50f, h * 0.60f),
                                style = Stroke(width = 1.4f, cap = StrokeCap.Round)
                            )
                        }

                        // Stable teal slider
                        Slider(
                            value = volume,
                            onValueChange = onVolumeChange,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = NeonCyan,
                                inactiveTrackColor = Color(0xFF242C3D)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp)
                        )
                    }

                    // Dedicated Metronome Toggle Button (Small icon button to turn click ON/OFF)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isMetronomeOn) NeonCyan else Color(0xFF1E2433))
                            .border(1.dp, if (isMetronomeOn) NeonCyanLight else Color(0xFF333E56), RoundedCornerShape(8.dp))
                            .clickable { onToggleMetronome() }
                            .testTag("btn_metronome_widget_power"),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(14.dp)) {
                            val w = size.width
                            val h = size.height
                            val iconColor = if (isMetronomeOn) Color(0xFF022830) else Color(0xFF94A3B8)
                            
                            // Body triangle
                            val body = Path().apply {
                                moveTo(w * 0.35f, 0f)
                                lineTo(w * 0.65f, 0f)
                                lineTo(w * 0.90f, h)
                                lineTo(w * 0.10f, h)
                                close()
                            }
                            drawPath(body, color = iconColor, style = Stroke(width = 1.2f))
                            
                            // Swinging pendulum arm
                            val armAngle = if (isMetronomeOn) 0.38f else 0.0f
                            val pivotX = w * 0.50f
                            val pivotY = h * 0.85f
                            val topX = pivotX + kotlin.math.sin(armAngle) * (h * 0.65f)
                            val topY = pivotY - kotlin.math.cos(armAngle) * (h * 0.65f)
                            drawLine(
                                color = iconColor,
                                start = Offset(pivotX, pivotY),
                                end = Offset(topX, topY),
                                strokeWidth = 1.4f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
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
    val diskColor = if (isArmed) Color(0xFFFF2A55) else Color(0xFFE2E8F0)
    val innerCutoutColor = Color(0xFF161924)
    val labelColor = if (isArmed) Color(0xFFFFB3BA) else Color(0xFF94A3B8)

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

