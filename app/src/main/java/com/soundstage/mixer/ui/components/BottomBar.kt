package com.soundstage.mixer.ui.components

import android.app.ActivityManager
import android.content.Context
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
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
    configuredSnapshotSlots: List<String> = listOf("slot_default"),
    onSnapshotSlotClick: (String) -> Unit = {},
    onAddSnapshot: (String) -> Unit = {},
    onDeleteSnapshot: (String) -> Unit = {},
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
    val barHeight = 42.dp
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF161A26), Color(0xFF11141E))
                )
            )
            .drawWithContent {
                drawContent()
                // Subtle top specular highlight for natural hardware relief
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(10.dp.toPx(), 0.5f),
                    end = Offset(size.width - 10.dp.toPx(), 0.5f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
        // ================= 1. DAW PRO REAL SYSTEM RESOURCE MONITOR (CPU & RAM) =================
        val runtime = remember { Runtime.getRuntime() }
        var memoryMb by remember { mutableIntStateOf(65) }
        var cpuPercent by remember { mutableIntStateOf(8) }

        LaunchedEffect(Unit) {
            val cores = runtime.availableProcessors().coerceAtLeast(1)
            var prevCpuTime = android.os.Process.getElapsedCpuTime()
            var prevWallTime = System.currentTimeMillis()

            while (true) {
                // Precise App PSS & Heap RAM consumption (JVM + Native FluidSynth / AudioTrack heap)
                val jvmUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                val nativeUsedMb = android.os.Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
                val totalAppMb = (jvmUsedMb + nativeUsedMb).toInt()
                memoryMb = totalAppMb.coerceIn(12, 9999)

                val curCpuTime = android.os.Process.getElapsedCpuTime()
                val curWallTime = System.currentTimeMillis()
                val deltaCpu = curCpuTime - prevCpuTime
                val deltaWall = curWallTime - prevWallTime

                if (deltaWall > 0) {
                    val rawLoad = ((deltaCpu.toFloat() / (deltaWall * cores)) * 100f).roundToInt()
                    cpuPercent = rawLoad.coerceIn(2, 99)
                }

                prevCpuTime = curCpuTime
                prevWallTime = curWallTime
                delay(1500)
            }
        }

        Box(
            modifier = Modifier
                .width(78.dp)
                .height(barHeight)
                .shadow(2.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF283144), Color(0xFF1D2433))
                    )
                )
                .drawWithContent {
                    drawContent()
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(4.dp.toPx(), 1f),
                        end = Offset(size.width - 4.dp.toPx(), 1f),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.Start
            ) {
                // CPU indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (cpuPercent > 70) Color(0xFFEF4444) else if (cpuPercent > 40) Color(0xFFF59E0B) else Color(0xFF10B981))
                    )
                    Text(
                        text = "CPU $cpuPercent%",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E8F0),
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                        ),
                        maxLines = 1
                    )
                }
                // RAM indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8))
                    )
                    Text(
                        text = "RAM ${memoryMb}M",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCBD5E1),
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        // ================= 2. BPM BOX =================
        Row(
            modifier = Modifier
                .height(barHeight)
                .shadow(2.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF283144), Color(0xFF1D2433))
                    )
                )
                .drawWithContent {
                    drawContent()
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(4.dp.toPx(), 1f),
                        end = Offset(size.width - 4.dp.toPx(), 1f),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
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
                Text(text = "−", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
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
                        color = Color.White,
                        lineHeight = 13.sp
                    )
                    Text(
                        text = "BPM",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
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
                Text(text = "+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
            }
        }

        // ================= 3. FUSED METRONOME & SIGNATURE/KEY BUTTON =================
        val metroBgBrush = if (isMetronomeOn) {
            Brush.verticalGradient(listOf(Color(0xFF163E52), Color(0xFF0F2D3D)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFF283144), Color(0xFF1D2433)))
        }

        Row(
            modifier = Modifier
                .height(barHeight)
                .shadow(2.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                .clip(RoundedCornerShape(8.dp))
                .background(metroBgBrush)
                .drawWithContent {
                    drawContent()
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(4.dp.toPx(), 1f),
                        end = Offset(size.width - 4.dp.toPx(), 1f),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
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
                    color = if (isMetronomeOn) NeonCyan else Color(0xFF94A3B8),
                    style = Stroke(width = 1.3f)
                )

                val armAngle = if (isMetronomeOn) 0.35f else 0.0f
                val pivotX = w * 0.50f
                val pivotY = h * 0.85f
                val topArmX = pivotX + kotlin.math.sin(armAngle) * (h * 0.68f)
                val topArmY = pivotY - kotlin.math.cos(armAngle) * (h * 0.68f)

                drawLine(
                    color = if (isMetronomeOn) Color.White else Color(0xFFE2E8F0),
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
                    color = if (isMetronomeOn) NeonCyanLight else Color.White
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
        var isAddSnapshotDialogOpen by remember { mutableStateOf(false) }
        var newSnapshotNameInput by remember { mutableStateOf("") }

        if (isAddSnapshotDialogOpen) {
            AlertDialog(
                onDismissRequest = { isAddSnapshotDialogOpen = false },
                title = {
                    Text(
                        text = "New Snapshot",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Enter snapshot name:",
                            color = Color(0xFFB0BEC5),
                            fontSize = 12.sp
                        )
                        OutlinedTextField(
                            value = newSnapshotNameInput,
                            onValueChange = { newSnapshotNameInput = it },
                            singleLine = true,
                            placeholder = { Text("Ex: Intro, Chorus, Bridge...", color = Color(0xFF64748B)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = Color(0x66FFFFFF),
                                cursorColor = NeonCyan
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_add_snapshot")
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newSnapshotNameInput.isNotBlank()) {
                                onAddSnapshot(newSnapshotNameInput.trim())
                            }
                            isAddSnapshotDialogOpen = false
                        },
                        modifier = Modifier.testTag("btn_confirm_add_snapshot")
                    ) {
                        Text("Add", color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isAddSnapshotDialogOpen = false }) {
                        Text("Cancel", color = Color(0xFF8E95A5))
                    }
                },
                containerColor = Color(0xFF161B29),
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (renamingSlotKey != null) {
            val isCustomSlot = renamingSlotKey != "slot_default"
            AlertDialog(
                onDismissRequest = { renamingSlotKey = null },
                title = {
                    Text(
                        text = "Manage Snapshot",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Snapshot name:",
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
                        if (isCustomSlot) {
                            TextButton(
                                onClick = {
                                    val key = renamingSlotKey
                                    if (key != null) {
                                        onDeleteSnapshot(key)
                                    }
                                    renamingSlotKey = null
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Delete snapshot", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
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
                        Text("Save", color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { renamingSlotKey = null }
                    ) {
                        Text("Close", color = Color(0xFF8E95A5))
                    }
                },
                containerColor = Color(0xFF161B29),
                shape = RoundedCornerShape(16.dp)
            )
        }

        // ================= 4. DYNAMIC SNAPSHOTS RACK =================
        // Floppy Disk arm blinking animation
        val armBlinkAlpha by animateFloatAsState(
            targetValue = if (isSnapshotArmMode) 1.0f else 0.40f,
            animationSpec = infiniteRepeatable(
                animation = tween(450),
                repeatMode = RepeatMode.Reverse
            ),
            label = "arm_blink"
        )

        val isAdaptiveSnapshots = configuredSnapshotSlots.size >= 4

        Row(
            modifier = if (isAdaptiveSnapshots) Modifier.weight(1f).height(barHeight) else Modifier.wrapContentWidth().height(barHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Configured Dynamic Snapshot Slots (Expand fluidly to fill space up to Master Fader)
            val slotKeys = configuredSnapshotSlots
            slotKeys.forEach { slotKey ->
                val defaultLabel = when (slotKey) {
                    "slot_default" -> "Default"
                    else -> snapshotCustomNames[slotKey] ?: "Snapshot"
                }
                val displayName = snapshotCustomNames[slotKey] ?: defaultLabel

                // Strict single active selection: only ONE slot is active
                val isActive = (activeSnapshotSlot == slotKey) || (activeSnapshotSlot == null && slotKey == "slot_default")
                val isSaved = snapshots.containsKey(slotKey) || (slotKey == "slot_default" && snapshots.isNotEmpty())

                // 1Gallery Reference Hardware Palette:
                // Active: Solid Soft Pastel Periwinkle Blue with dark anthracite text (No harsh neon)
                // Inactive: Matte Studio Slate with subtle embossed tactile relief gradient & subtle top bevel
                val slotBg = when {
                    isActive -> Brush.verticalGradient(
                        listOf(Color(0xFF9BC0F8), Color(0xFF8FB6F2))
                    )
                    else -> Brush.verticalGradient(
                        listOf(Color(0xFF283144), Color(0xFF1D2433))
                    )
                }
                val slotBorderColor = when {
                    isSnapshotArmMode && !isActive -> Color(0xFFFF2A55).copy(alpha = armBlinkAlpha)
                    isActive -> Color(0x33000000)
                    else -> Color(0xFF38455E)
                }
                val slotTextColor = when {
                    isActive -> Color(0xFF101726)
                    else -> Color(0xFFE2E8F0)
                }
                val slotFontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold

                // 2-line format when space allows (matching reference screenshot: "Snapshot\n2")
                val formattedLabel = if (displayName.contains(" ") && displayName.length > 7) {
                    val p = displayName.split(" ", limit = 2)
                    "${p[0]}\n${p[1]}"
                } else {
                    displayName
                }

                val slotModifier = if (isAdaptiveSnapshots) {
                    Modifier.weight(1f).height(barHeight)
                } else {
                    Modifier.width(88.dp).height(barHeight)
                }

                Box(
                    modifier = slotModifier
                        .shadow(
                            elevation = if (isActive) 0.dp else 2.5.dp,
                            shape = RoundedCornerShape(8.dp),
                            ambientColor = Color.Black,
                            spotColor = Color(0x99000000)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(slotBg)
                        .then(
                            if (isSnapshotArmMode && !isActive) {
                                Modifier.border(
                                    1.dp,
                                    Color(0xFFFF2A55).copy(alpha = armBlinkAlpha),
                                    RoundedCornerShape(8.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .drawWithContent {
                            drawContent()
                            // Natural top-edge highlight for inactive physical relief (no side borders)
                            if (!isActive && !isSnapshotArmMode) {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.12f),
                                    start = Offset(5.dp.toPx(), 1f),
                                    end = Offset(size.width - 5.dp.toPx(), 1f),
                                    strokeWidth = 1.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
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
                        text = formattedLabel,
                        fontSize = if (formattedLabel.contains("\n")) 9.5.sp else 11.sp,
                        fontWeight = slotFontWeight,
                        color = slotTextColor,
                        textAlign = TextAlign.Center,
                        lineHeight = 11.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // [+] Add Snapshot button (up to 5 max) - attached directly to the right of snapshot slots, same barHeight (44dp)
            if (configuredSnapshotSlots.size < 5) {
                Box(
                    modifier = Modifier
                        .size(36.dp, barHeight)
                        .shadow(2.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF283144), Color(0xFF1D2433))
                            )
                        )
                        .drawWithContent {
                            drawContent()
                            drawLine(
                                color = Color.White.copy(alpha = 0.12f),
                                start = Offset(4.dp.toPx(), 1f),
                                end = Offset(size.width - 4.dp.toPx(), 1f),
                                strokeWidth = 1.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                        .clickable {
                            newSnapshotNameInput = "Snapshot ${configuredSnapshotSlots.size}"
                            isAddSnapshotDialogOpen = true
                        }
                        .testTag("btn_add_snapshot"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                }
            }

            // Floppy Disk (Save) Icon - placed immediately to the RIGHT of [+] button, exact matching 44dp barHeight
            val diskBgBrush = if (isSnapshotArmMode) {
                SolidColor(Color(0xFF4A0014))
            } else {
                Brush.verticalGradient(
                    listOf(Color(0xFF283144), Color(0xFF1D2433))
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp, barHeight)
                    .shadow(2.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                    .clip(RoundedCornerShape(8.dp))
                    .background(diskBgBrush)
                    .then(
                        if (isSnapshotArmMode) {
                            Modifier.border(
                                1.dp,
                                Color(0xFFFF2A55).copy(alpha = armBlinkAlpha),
                                RoundedCornerShape(8.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .drawWithContent {
                        drawContent()
                        if (!isSnapshotArmMode) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.12f),
                                start = Offset(4.dp.toPx(), 1f),
                                end = Offset(size.width - 4.dp.toPx(), 1f),
                                strokeWidth = 1.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    .clickable { onToggleSnapshotArm() }
                    .testTag("btn_snapshot_arm"),
                contentAlignment = Alignment.Center
            ) {
                FloppyDiskIcon(
                    isArmed = isSnapshotArmMode,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (!isAdaptiveSnapshots) {
            Spacer(modifier = Modifier.weight(1f))
        }

        // ================= 5. MASTER FADER (COMPACT FIXED WIDTH) =================
        Row(
            modifier = Modifier
                .width(150.dp)
                .height(barHeight)
                .shadow(2.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF202738), Color(0xFF141824))
                    )
                )
                .drawWithContent {
                    drawContent()
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(4.dp.toPx(), 1f),
                        end = Offset(size.width - 4.dp.toPx(), 1f),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
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
                    thumbColor = Color(0xFF9BC0F8),
                    activeTrackColor = Color(0xFF9BC0F8),
                    inactiveTrackColor = Color(0xFF1E2238)
                )
            )
            Spacer(modifier = Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .shadow(1.5.dp, RoundedCornerShape(6.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF283144), Color(0xFF1D2433))
                        )
                    )
                    .drawWithContent {
                        drawContent()
                        drawLine(
                            color = Color.White.copy(alpha = 0.12f),
                            start = Offset(2.dp.toPx(), 1f),
                            end = Offset(size.width - 2.dp.toPx(), 1f),
                            strokeWidth = 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    .clickable { onMasterFxClick() },
                contentAlignment = Alignment.Center
            ) {
                Text("FX", color = Color(0xFF9BC0F8), fontSize = 8.sp, fontWeight = FontWeight.Bold)
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
                val layerBgBrush = if (isLayerActive) {
                    Brush.verticalGradient(listOf(Color(0xFF9BC0F8), Color(0xFF8FB6F2)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF283144), Color(0xFF1D2433)))
                }

                Box(
                    modifier = Modifier
                        .requiredWidth(42.dp)
                        .height(barHeight)
                        .shadow(
                            elevation = if (isLayerActive) 0.dp else 2.5.dp,
                            shape = RoundedCornerShape(8.dp),
                            ambientColor = Color.Black,
                            spotColor = Color(0x99000000)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(layerBgBrush)
                        .drawWithContent {
                            drawContent()
                            if (!isLayerActive) {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.12f),
                                    start = Offset(4.dp.toPx(), 1f),
                                    end = Offset(size.width - 4.dp.toPx(), 1f),
                                    strokeWidth = 1.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                        .clickable { onToggleLayer() }
                        .testTag("btn_toggle_layer"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(20.dp, 16.dp)) {
                        val w = size.width
                        val h = size.height
                        val strokeCol = if (isLayerActive) Color(0xFF101726) else Color(0xFFE2E8F0)

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

            // Keyboard Toggle Button (Matching Snapshot button style & relief)
            val keyboardBgBrush = if (isKeyboardActive) {
                Brush.verticalGradient(listOf(Color(0xFF9BC0F8), Color(0xFF8FB6F2)))
            } else {
                Brush.verticalGradient(listOf(Color(0xFF283144), Color(0xFF1D2433)))
            }

            Box(
                modifier = Modifier
                    .requiredWidth(44.dp)
                    .height(barHeight)
                    .shadow(
                        elevation = if (isKeyboardActive) 0.dp else 2.5.dp,
                        shape = RoundedCornerShape(8.dp),
                        ambientColor = Color.Black,
                        spotColor = Color(0x99000000)
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .background(keyboardBgBrush)
                    .drawWithContent {
                        drawContent()
                        if (!isKeyboardActive) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.12f),
                                start = Offset(4.dp.toPx(), 1f),
                                end = Offset(size.width - 4.dp.toPx(), 1f),
                                strokeWidth = 1.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    .clickable { onToggleKeyboard() }
                    .testTag("btn_toggle_keyboard"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(24.dp, 18.dp)) {
                    val w = size.width
                    val h = size.height

                    val keyOutlineColor = if (isKeyboardActive) Color(0xFF101726) else Color(0xFFE2E8F0)
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

                    val blackKeyColor = if (isKeyboardActive) Color(0xFF101726) else Color.White
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

