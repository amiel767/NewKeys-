package com.soundstage.mixer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.*
import com.soundstage.mixer.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrumPadDialog(
    drumPads: List<DrumPadItem>,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    reverb: Float,
    onReverbChange: (Float) -> Unit,
    activeTab: String,
    onTabChange: (String) -> Unit,
    subView: String,
    onSetSubView: (String) -> Unit,
    soundfonts: List<StorageItem> = emptyList(),
    currentSoundfontName: String = "FluidR3_GM.sf2",
    loadedSf2Presets: List<SoundfontPreset> = emptyList(),
    onSelectPreset: (SoundfontPreset) -> Unit = {},
    onSelectSf2File: (StorageItem) -> Unit = {},
    onOpenSoundfontPicker: () -> Unit = {},
    audioFiles: List<StorageItem>,
    loopFiles: List<StorageItem> = emptyList(),
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onClose: () -> Unit,
    onPadPressed: (Int) -> Unit,
    onPadReleased: (Int) -> Unit,
    onPlayNote: (note: String, octave: Int) -> Unit = { _, _ -> },
    onPlaySample: (StorageItem) -> Unit = {},
    onUpdatePadCustomization: (padId: Int, label: String, style: DrumPadStyle, isLoopMode: Boolean) -> Unit = { _, _, _, _ -> },
    onAssignPadSample: (padId: Int, sample: StorageItem) -> Unit,
    onAssignPadSampleOrLoop: ((padId: Int, sample: StorageItem, isLoop: Boolean) -> Unit)? = null,
    onDeleteLoopFile: (StorageItem) -> Unit = {},
    onAssignPadNote: (padId: Int, noteStr: String, oct: Int, key: String) -> Unit,
    onImportAudioFile: (() -> Unit)? = null,
    isRecording: Boolean = false,
    isArmed: Boolean = false,
    onToggleArmLoop: () -> Unit = {},
    bpm: Int = 140,
    timeSignature: String = "4/4",
    onToggleTimeSignature: () -> Unit = {},
    loopBars: Int = 2,
    onSetLoopBars: (Int) -> Unit = {},
    onIncrementLoopBars: () -> Unit = {},
    onDecrementLoopBars: () -> Unit = {},
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
    isRendering: Boolean = false,
    initialOffsetX: Float = 0f,
    initialOffsetY: Float = 0f,
    initialSizeDp: Float = 440f,
    onTransformChange: (Float, Float, Float) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    val density = LocalDensity.current
    var editingPad by remember { mutableStateOf<DrumPadItem?>(null) }
    var quickAssignSample by remember { mutableStateOf<StorageItem?>(null) }

    // Persistent drag position and size state
    var offsetX by remember { mutableFloatStateOf(initialOffsetX) }
    var offsetY by remember { mutableFloatStateOf(initialOffsetY) }
    var windowSizeDp by remember { mutableStateOf(initialSizeDp.dp) }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.94f, animationSpec = tween(200)),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.94f, animationSpec = tween(160)),
        modifier = modifier
    ) {
        val rootModifier = if (!isPinned) {
            Modifier
                .fillMaxSize()
                .background(Color(0x77000000))
                .clickable { onClose() }
                .testTag("drum_pad_overlay")
        } else {
            Modifier
                .fillMaxSize()
                .testTag("drum_pad_overlay")
        }

        BoxWithConstraints(
            modifier = rootModifier,
            contentAlignment = Alignment.Center
        ) {
            val maxDragX = (maxWidth.value - 300f).coerceAtLeast(0f) * 1.5f
            val maxDragY = (maxHeight.value - 300f).coerceAtLeast(0f) * 1.5f

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .size(windowSizeDp)
                    .shadow(if (isPinned) 24.dp else 32.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0A0C13))
                    .border(
                        1.dp,
                        if (isPinned) NeonCyan.copy(alpha = 0.8f) else Color(0x22FFFFFF),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(10.dp)
            ) {
                when {
                    editingPad != null -> {
                        PadCustomizerScreen(
                            pad = editingPad!!,
                            onSave = { newLabel, newStyle, isLoopMode ->
                                onUpdatePadCustomization(editingPad!!.id, newLabel, newStyle, isLoopMode)
                                editingPad = null
                            },
                            onAssignSample = {
                                onTabChange("files")
                                editingPad = null
                            },
                            onBack = { editingPad = null }
                        )
                    }
                    else -> {
                        MainDrumPadSquareContent(
                            drumPads = drumPads,
                            volume = volume,
                            onVolumeChange = onVolumeChange,
                            reverb = reverb,
                            onReverbChange = onReverbChange,
                            activeTab = activeTab,
                            onTabChange = onTabChange,
                            isPinned = isPinned,
                            onTogglePin = onTogglePin,
                            onClose = onClose,
                            onDragWindow = { dx, dy ->
                                offsetX = (offsetX + dx).coerceIn(-maxDragX, maxDragX)
                                offsetY = (offsetY + dy).coerceIn(-maxDragY, maxDragY)
                                onTransformChange(offsetX, offsetY, windowSizeDp.value)
                            },
                            onPadPressed = onPadPressed,
                            onPadReleased = onPadReleased,
                            onLongPressPad = { pad -> editingPad = pad },
                            audioFiles = audioFiles,
                            loopFiles = loopFiles,
                            onPlaySample = onPlaySample,
                            onLongPressSample = { file -> quickAssignSample = file },
                            onDeleteLoopFile = onDeleteLoopFile,
                            onAssignPadSampleOrLoop = onAssignPadSampleOrLoop,
                            onImportAudioFile = onImportAudioFile,
                            isRecording = isRecording,
                            isArmed = isArmed,
                            onToggleArmLoop = onToggleArmLoop,
                            bpm = bpm,
                            timeSignature = timeSignature,
                            onToggleTimeSignature = onToggleTimeSignature,
                            loopBars = loopBars,
                            onSetLoopBars = onSetLoopBars,
                            onIncrementLoopBars = onIncrementLoopBars,
                            onDecrementLoopBars = onDecrementLoopBars,
                            onStartRecording = onStartRecording,
                            onStopRecording = onStopRecording,
                            onCancelRecording = onCancelRecording,
                            isRendering = isRendering
                        )
                    }
                }

                // Discrete Resize Arrow (Only visible & active when Pinned)
                if (isPinned) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val dDp = with(density) { (dragAmount.x + dragAmount.y) / 2f }.toDp()
                                    windowSizeDp = (windowSizeDp + dDp).coerceIn(300.dp, 600.dp)
                                    onTransformChange(offsetX, offsetY, windowSizeDp.value)
                                }
                            }
                            .padding(end = 4.dp, bottom = 4.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Text(text = "◢", fontSize = 12.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                }

                // Quick Pad Assignment Modal (When long-pressing sample)
                if (quickAssignSample != null) {
                    val sample = quickAssignSample!!
                    QuickPadAssignModal(
                        title = "Assigner ${sample.name.take(16)} au Pad",
                        pads = drumPads,
                        initialIsLoop = (activeTab == "loops"),
                        onSelectPad = { padId, isLoop ->
                            if (onAssignPadSampleOrLoop != null) {
                                onAssignPadSampleOrLoop(padId, sample, isLoop)
                            } else {
                                onAssignPadSample(padId, sample)
                            }
                            quickAssignSample = null
                        },
                        onDismiss = { quickAssignSample = null }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MainDrumPadSquareContent(
    drumPads: List<DrumPadItem>,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    reverb: Float,
    onReverbChange: (Float) -> Unit,
    activeTab: String,
    onTabChange: (String) -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onClose: () -> Unit,
    onDragWindow: (Float, Float) -> Unit,
    onPadPressed: (Int) -> Unit,
    onPadReleased: (Int) -> Unit,
    onLongPressPad: (DrumPadItem) -> Unit,
    audioFiles: List<StorageItem>,
    loopFiles: List<StorageItem> = emptyList(),
    onPlaySample: (StorageItem) -> Unit,
    onLongPressSample: (StorageItem) -> Unit,
    onDeleteLoopFile: (StorageItem) -> Unit = {},
    onAssignPadSampleOrLoop: ((padId: Int, sample: StorageItem, isLoop: Boolean) -> Unit)? = null,
    onImportAudioFile: (() -> Unit)? = null,
    isRecording: Boolean = false,
    isArmed: Boolean = false,
    onToggleArmLoop: () -> Unit = {},
    bpm: Int = 140,
    timeSignature: String = "4/4",
    onToggleTimeSignature: () -> Unit = {},
    loopBars: Int = 2,
    onSetLoopBars: (Int) -> Unit = {},
    onIncrementLoopBars: () -> Unit = {},
    onDecrementLoopBars: () -> Unit = {},
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
    isRendering: Boolean = false,
    lastPath: String = "",
    onUpdateLastPath: (String) -> Unit = {}
) {
    var isNativeBrowserOpen by remember { mutableStateOf(false) }
    var localAssignSample by remember { mutableStateOf<StorageItem?>(null) }
    var currentDirPath by remember { mutableStateOf(lastPath.ifEmpty { "/storage/emulated/0/SoundStage/DrumPad" }) }
    var tuneKnobValue by remember { mutableFloatStateOf(0.5f) }
    var isFxEnabled by remember { mutableStateOf(true) }
    var isSoloEnabled by remember { mutableStateOf(false) }
    var selectedKitIndex by remember { mutableIntStateOf(0) }
    val kitNames = listOf("KIT 01: TECHNO LIVE", "KIT 02: 808 TRAP", "KIT 03: ACOUSTIC JAZZ", "KIT 04: RETRO SYNTH", "KIT 05: LO-FI DREAMS")

    Column(modifier = Modifier.fillMaxSize()) {
        // ================= TOP BAR WITH PRO SAMPLING PAD TITLE =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDragWindow(dragAmount.x, dragAmount.y)
                    }
                }
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "DRUMPAD",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 1.5.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "PRO",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFF43F5E),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "SAMPLING PAD",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B),
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Minimalist Close Button
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
                    .clickable { onClose() }
                    .testTag("btn_close_drumpad"),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✕", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }

        val infinitePulse = rememberInfiniteTransition(label = "rec_pulse")
        val recPulseAlpha by infinitePulse.animateFloat(
            initialValue = 0.35f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(450),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rec_alpha"
        )

        // ================= MAIN CONTENT AREA =================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (activeTab) {
                "pad" -> {
                    // MAIN PRO PAD VIEW: 6 PADS (2 Rows x 3 Columns) + PRO HARDWARE DECK
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // --- 6 PRO PADS MATRIX (2 rows x 3 columns) ---
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (0..1).forEach { rowIndex ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    (0..2).forEach { colIndex ->
                                        val padIndex = rowIndex * 3 + colIndex
                                        val pad = drumPads.getOrNull(padIndex)
                                        ProDrumPadCell(
                                            padIndex = padIndex,
                                            pad = pad,
                                            onPress = {
                                                if (pad != null) onPadPressed(pad.id)
                                                else onPadPressed(padIndex)
                                            },
                                            onRelease = {
                                                if (pad != null) onPadReleased(pad.id)
                                                else onPadReleased(padIndex)
                                            },
                                            onLongPress = {
                                                if (pad != null) onLongPressPad(pad)
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        // --- BOTTOM PRO HARDWARE DECK ---
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F121C))
                                .border(1.dp, Color(0xFF202638), RoundedCornerShape(12.dp))
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. LEFT: OLED DISPLAY & MODE BUTTONS
                            Column(
                                modifier = Modifier
                                    .weight(1.35f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // OLED Screen Box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(82.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF0A0F1A), Color(0xFF050810))
                                            )
                                        )
                                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Header: Kit Name
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = kitNames[selectedKitIndex % kitNames.size],
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF00E5FF),
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = "$timeSignature • ${loopBars} BARS",
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }

                                        // Center: Large BPM + Audio Waveform Canvas
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.Bottom,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(
                                                    text = "$bpm",
                                                    fontSize = 24.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White,
                                                    letterSpacing = (-0.5).sp
                                                )
                                                Text(
                                                    text = "BPM",
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF00E5FF),
                                                    modifier = Modifier.padding(bottom = 3.dp)
                                                )
                                            }

                                            // Animated Cyan Waveform Canvas
                                            val infiniteWave = rememberInfiniteTransition(label = "oled_wave")
                                            val wavePhase by infiniteWave.animateFloat(
                                                initialValue = 0f,
                                                targetValue = (2 * Math.PI).toFloat(),
                                                animationSpec = infiniteRepeatable(
                                                    animation = tween(1200),
                                                    repeatMode = RepeatMode.Restart
                                                ),
                                                label = "wave_phase"
                                            )

                                            androidx.compose.foundation.Canvas(
                                                modifier = Modifier
                                                    .width(62.dp)
                                                    .height(20.dp)
                                            ) {
                                                val width = size.width
                                                val height = size.height
                                                val centerY = height / 2f
                                                val points = 24
                                                for (i in 0 until points - 1) {
                                                    val x1 = (i.toFloat() / (points - 1)) * width
                                                    val x2 = ((i + 1).toFloat() / (points - 1)) * width
                                                    val y1 = centerY + (kotlin.math.sin(wavePhase + (i * 0.4f)) * (height * 0.35f)).toFloat()
                                                    val y2 = centerY + (kotlin.math.sin(wavePhase + ((i + 1) * 0.4f)) * (height * 0.35f)).toFloat()
                                                    drawLine(
                                                        color = Color(0xFF00E5FF),
                                                        start = androidx.compose.ui.geometry.Offset(x1, y1),
                                                        end = androidx.compose.ui.geometry.Offset(x2, y2),
                                                        strokeWidth = 1.5.dp.toPx()
                                                    )
                                                }
                                            }
                                        }

                                        // Bottom badges: FX ON | LOOP | REC
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(if (isFxEnabled) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0x14FFFFFF))
                                                    .border(0.5.dp, if (isFxEnabled) Color(0xFF00E5FF) else Color(0x22FFFFFF), RoundedCornerShape(3.dp))
                                                    .clickable { isFxEnabled = !isFxEnabled }
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = if (isFxEnabled) "FX: ON" else "FX: OFF",
                                                    fontSize = 7.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isFxEnabled) Color(0xFF00E5FF) else Color(0xFF64748B)
                                                )
                                            }

                                            Text(
                                                text = "LOOP ${loopBars * 4}b",
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF94A3B8)
                                            )

                                            Spacer(modifier = Modifier.weight(1f))

                                            // REC indicator dot
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isRecording) Color(0xFFF43F5E).copy(alpha = recPulseAlpha)
                                                            else Color(0xFF64748B)
                                                        )
                                                )
                                                Text(
                                                    text = if (isRecording) "REC" else "IDLE",
                                                    fontSize = 7.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isRecording) Color(0xFFF43F5E) else Color(0xFF64748B)
                                                )
                                            }
                                        }
                                    }
                                }

                                // 5 Mode Buttons below OLED
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(28.dp),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // [Pads]
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Color(0xFF00E5FF))
                                            .clickable { onTabChange("pad") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "Pads", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF041E28))
                                    }

                                    // [Fichiers]
                                    Box(
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Color(0xFF1E2232))
                                            .border(0.8.dp, Color(0x22FFFFFF), RoundedCornerShape(5.dp))
                                            .clickable { onTabChange("files") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "Fichiers", fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    }

                                    // [Loops]
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Color(0xFF1E2232))
                                            .border(0.8.dp, Color(0x22FFFFFF), RoundedCornerShape(5.dp))
                                            .clickable { onTabChange("loops") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "Loops", fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    }

                                    // [→ LOOP]
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(if (isArmed) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color(0xFF1E2232))
                                            .border(0.8.dp, if (isArmed) Color(0xFF00E5FF) else Color(0x22FFFFFF), RoundedCornerShape(5.dp))
                                            .clickable { onToggleArmLoop() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "→ LOOP",
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isArmed) Color(0xFF00E5FF) else Color(0xFFCBD5E1)
                                        )
                                    }

                                    // [● REC]
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(
                                                if (isRecording) Color(0xFF4C0519)
                                                else Color(0xFF2A121A)
                                            )
                                            .border(
                                                0.8.dp,
                                                if (isRecording) Color(0xFFF43F5E).copy(alpha = recPulseAlpha) else Color(0xFFF43F5E).copy(alpha = 0.6f),
                                                RoundedCornerShape(5.dp)
                                            )
                                            .clickable {
                                                if (isRecording) onStopRecording()
                                                else onStartRecording()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFF43F5E))
                                            )
                                            Text(
                                                text = if (isRecording) "REC" else "REC",
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFF43F5E)
                                            )
                                        }
                                    }
                                }
                            }

                            // 2. CENTER: F1/F2/F3 + D-PAD + MENU/EXIT
                            Row(
                                modifier = Modifier
                                    .weight(1.15f)
                                    .fillMaxHeight(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Matrix: F-Keys + D-Pad
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // F1, F2, F3 Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        // F1: Cycle Kit
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(20.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF1E2232))
                                                .border(0.8.dp, Color(0x22FFFFFF), RoundedCornerShape(4.dp))
                                                .clickable {
                                                    selectedKitIndex = (selectedKitIndex + 1) % kitNames.size
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "F1", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                                        }

                                        // F2: Mute / Solo
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(20.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isSoloEnabled) Color(0xFFF59E0B).copy(alpha = 0.3f) else Color(0xFF1E2232))
                                                .border(0.8.dp, if (isSoloEnabled) Color(0xFFF59E0B) else Color(0x22FFFFFF), RoundedCornerShape(4.dp))
                                                .clickable { isSoloEnabled = !isSoloEnabled },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "F2", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = if (isSoloEnabled) Color(0xFFF59E0B) else Color.White)
                                        }

                                        // F3: FX Toggle
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(20.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isFxEnabled) Color(0xFF10B981).copy(alpha = 0.3f) else Color(0xFF1E2232))
                                                .border(0.8.dp, if (isFxEnabled) Color(0xFF10B981) else Color(0x22FFFFFF), RoundedCornerShape(4.dp))
                                                .clickable { isFxEnabled = !isFxEnabled },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "F3", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = if (isFxEnabled) Color(0xFF10B981) else Color.White)
                                        }
                                    }

                                    // Hardware D-Pad Cross (Up, Down, Left, Right + OK)
                                    Box(
                                        modifier = Modifier
                                            .size(86.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF141724))
                                            .border(1.dp, Color(0xFF23283B), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // UP: Loop Bars / Tempo increment
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .size(24.dp)
                                                .clickable { onIncrementLoopBars() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "▲", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                        }

                                        // DOWN: Loop Bars / Tempo decrement
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .size(24.dp)
                                                .clickable { onDecrementLoopBars() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "▼", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                        }

                                        // LEFT: Toggle Time Signature
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .size(24.dp)
                                                .clickable { onToggleTimeSignature() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "◀", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                        }

                                        // RIGHT: Toggle Time Signature
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .size(24.dp)
                                                .clickable { onToggleTimeSignature() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "▶", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                        }

                                        // CENTER OK BUTTON (Glowing Neon Cyan)
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.radialGradient(
                                                        listOf(Color(0xFF00E5FF), Color(0xFF0891B2))
                                                    )
                                                )
                                                .border(1.2.dp, Color(0xFFE0F2FE), CircleShape)
                                                .clickable {
                                                    onPadPressed(4)
                                                    onPadReleased(4)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "OK",
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF082F49)
                                            )
                                        }
                                    }
                                }

                                // Side Buttons: MENU & EXIT
                                Column(
                                    modifier = Modifier
                                        .width(28.dp)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Color(0xFF1E2232))
                                            .border(0.8.dp, Color(0x22FFFFFF), RoundedCornerShape(5.dp))
                                            .clickable { isNativeBrowserOpen = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "MENU", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Color(0xFF1E2232))
                                            .border(0.8.dp, Color(0x22FFFFFF), RoundedCornerShape(5.dp))
                                            .clickable { onTabChange("pad") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "EXIT", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF43F5E))
                                    }
                                }
                            }

                            // 3. RIGHT: 3 ANALOG 3D ROTARY KNOBS WITH GLOWING LED RINGS
                            Column(
                                modifier = Modifier
                                    .width(88.dp)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Top row: CTRL 1 (Reverb/Red) & CTRL 2 (Tune/Cyan)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // CTRL 1 (Red LED Knob) -> Reverb
                                    Led3DKnob(
                                        value = reverb,
                                        onValueChange = onReverbChange,
                                        label = "CTRL 1",
                                        showFloatingTooltipOnTouch = false,
                                        size = 32.dp,
                                        baseColor = Color(0xFFF43F5E),
                                        dynamicColorMorph = false
                                    )

                                    // CTRL 2 (Cyan LED Knob) -> Tune
                                    Led3DKnob(
                                        value = tuneKnobValue,
                                        onValueChange = { tuneKnobValue = it },
                                        label = "CTRL 2",
                                        showFloatingTooltipOnTouch = false,
                                        size = 32.dp,
                                        baseColor = Color(0xFF00E5FF),
                                        dynamicColorMorph = false
                                    )
                                }

                                // Bottom row: MASTER VOL (Large Knob with Purple LED ring)
                                Led3DKnob(
                                    value = volume,
                                    onValueChange = onVolumeChange,
                                    label = "MASTER VOL",
                                    showFloatingTooltipOnTouch = false,
                                    size = 42.dp,
                                    baseColor = Color(0xFFD946EF),
                                    dynamicColorMorph = false
                                )
                            }
                        }
                    }
                }

                "files" -> {
                    // IN-WINDOW FILE EXPLORER FOR INTERNAL STORAGE & SD
                    val currentDir = remember(currentDirPath) {
                        val f = java.io.File(currentDirPath)
                        if (f.exists() && f.isDirectory) f else java.io.File("/storage/emulated/0")
                    }
                    val fileEntries = remember(currentDir) {
                        try {
                            currentDir.listFiles()?.filter { !it.name.startsWith(".") }?.sortedWith(
                                compareBy<java.io.File> { !it.isDirectory }.thenBy { it.name.lowercase() }
                            ) ?: emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF10131F))
                            .border(1.dp, Color(0xFF22283A), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Header: Navigation and Back to Pads button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF00E5FF))
                                    .clickable { onTabChange("pad") }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "← Retour aux Pads",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF041E28)
                                )
                            }

                            Text(
                                text = "Court = Preview · Long = Assigner au Pad",
                                fontSize = 8.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        // Storage Quick Jump Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "DrumPad" to "/storage/emulated/0/SoundStage/DrumPad",
                                "SoundStage" to "/storage/emulated/0/SoundStage",
                                "Interne" to "/storage/emulated/0",
                                "Music" to "/storage/emulated/0/Music",
                                "Download" to "/storage/emulated/0/Download"
                            ).forEach { (lbl, pth) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF1E2232))
                                        .border(0.8.dp, Color(0x22FFFFFF), RoundedCornerShape(5.dp))
                                        .clickable {
                                            val targetF = java.io.File(pth)
                                            if (targetF.exists()) {
                                                currentDirPath = targetF.absolutePath
                                                onUpdateLastPath(targetF.absolutePath)
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = lbl, fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                }
                            }
                        }

                        // Parent Directory Row
                        val parent = currentDir.parentFile
                        if (parent != null && parent.canRead()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF181B26))
                                    .clickable {
                                        currentDirPath = parent.absolutePath
                                        onUpdateLastPath(parent.absolutePath)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(text = "📁 ..", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                                Text(
                                    text = "Dossier parent (${currentDir.name.ifEmpty { "/" }})",
                                    fontSize = 9.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }

                        // Files & Folders List
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (fileEntries.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Aucun fichier audio trouvé dans ce dossier",
                                            fontSize = 9.5.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            } else {
                                items(fileEntries) { file ->
                                    val isAudio = file.extension.lowercase() in listOf("wav", "mp3", "ogg", "flac", "sf2", "aif", "m4a")
                                    val storageItem = StorageItem(name = file.name, path = file.absolutePath, isDirectory = file.isDirectory)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(if (file.isDirectory) Color(0xFF161A28) else Color(0xFF1C2132))
                                            .border(0.8.dp, if (file.isDirectory) Color(0x18FFFFFF) else Color(0x3300E5FF), RoundedCornerShape(7.dp))
                                            .combinedClickable(
                                                onClick = {
                                                    if (file.isDirectory) {
                                                        currentDirPath = file.absolutePath
                                                        onUpdateLastPath(file.absolutePath)
                                                    } else {
                                                        // Instant Preview on Single Tap
                                                        onPlaySample(storageItem)
                                                    }
                                                },
                                                onLongClick = {
                                                    if (!file.isDirectory) {
                                                        localAssignSample = storageItem
                                                    }
                                                }
                                            )
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = if (file.isDirectory) "📁" else if (isAudio) "🎵" else "📄",
                                                fontSize = 11.sp
                                            )
                                            Column {
                                                Text(
                                                    text = file.name,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (!file.isDirectory) {
                                                    Text(
                                                        text = "${(file.length() / 1024)} KB · Appui court = Play · Long = Assigner",
                                                        fontSize = 7.5.sp,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                }
                                            }
                                        }

                                        if (!file.isDirectory) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                                                    .border(0.8.dp, Color(0xFF00E5FF), RoundedCornerShape(4.dp))
                                                    .clickable { localAssignSample = storageItem }
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                            ) {
                                                Text(text = "Assigner", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "loops" -> {
                    // IN-WINDOW RECORDED LOOPS LIBRARY
                    var fileToDelete by remember { mutableStateOf<StorageItem?>(null) }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF10131F))
                            .border(1.dp, Color(0xFF22283A), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF00E5FF))
                                    .clickable { onTabChange("pad") }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "← Retour aux Pads",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF041E28)
                                )
                            }

                            Text(
                                text = "${loopFiles.size} boucle(s) enregistrée(s)",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF00E5FF)
                            )
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (loopFiles.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(30.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(text = "🔄", fontSize = 24.sp)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Aucune boucle enregistrée pour le moment.",
                                                fontSize = 10.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                            Text(
                                                text = "Appuyez sur [● REC] pour capturer votre rythme en direct.",
                                                fontSize = 8.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(loopFiles) { file ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1E2232))
                                            .border(0.8.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                                                    .clickable { onPlaySample(file) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = "▶", fontSize = 10.sp, color = Color(0xFF00E5FF))
                                            }
                                            Column {
                                                Text(
                                                    text = file.name,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Boucle Audio WAV",
                                                    fontSize = 7.5.sp,
                                                    color = Color(0xFF94A3B8)
                                                )
                                            }
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                                                    .border(0.8.dp, Color(0xFF00E5FF), RoundedCornerShape(4.dp))
                                                    .clickable { localAssignSample = file }
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                            ) {
                                                Text(text = "Assigner", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0x22F43F5E))
                                                    .border(0.8.dp, Color(0xFFF43F5E), RoundedCornerShape(4.dp))
                                                    .clickable { fileToDelete = file },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = "✕", fontSize = 10.sp, color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (fileToDelete != null) {
                            val target = fileToDelete!!
                            AlertDialog(
                                onDismissRequest = { fileToDelete = null },
                                title = {
                                    Text(text = "Supprimer la boucle ?", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                                },
                                text = {
                                    Text(text = "Voulez-vous supprimer « ${target.name} » ?", color = Color(0xFFCBD5E1), fontSize = 10.sp)
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            onDeleteLoopFile(target)
                                            fileToDelete = null
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF43F5E))
                                    ) {
                                        Text("Supprimer", fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { fileToDelete = null }) {
                                        Text("Annuler", color = Color(0xFF94A3B8))
                                    }
                                },
                                containerColor = Color(0xFF1E2232),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (localAssignSample != null) {
        val sample = localAssignSample!!
        QuickPadAssignModal(
            title = "Assigner ${sample.name.take(16)} au Pad",
            pads = drumPads,
            initialIsLoop = (activeTab == "loops"),
            onSelectPad = { padId, isLoop ->
                onAssignPadSampleOrLoop?.invoke(padId, sample, isLoop)
                localAssignSample = null
            },
            onDismiss = { localAssignSample = null }
        )
    }
}

@Composable
fun ProDrumPadCell(
    padIndex: Int,
    pad: DrumPadItem?,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    // Authentic SVG definitions for 6 pads:
    val defaultTags = listOf("P 01", "P 02", "P 03", "P 04", "P 05 [ACTIVE]", "P 06")
    val defaultColors = listOf(
        Color(0xFF00E5FF),
        Color(0xFF10B981),
        Color(0xFFF59E0B),
        Color(0xFFD946EF),
        Color(0xFF00E5FF),
        Color(0xFFF43F5E)
    )
    val defaultTitles = listOf(
        "KICK DRY",
        "SNARE VERB",
        "HI-HAT CLSD",
        "TAMBOURINE",
        "CLAP 808 ST",
        "CRASH CYMBAL"
    )
    val defaultSubtitles = listOf(
        "909 Acoustic Bass",
        "Fat Gated Trap 04",
        "Crisp Bright Edge",
        "Studio Natural Perc",
        "Stereo Wide Spread",
        "18\" Heavy Dark Ride"
    )

    val tagText = defaultTags.getOrElse(padIndex) { "P ${padIndex + 1}" }
    val tagColor = defaultColors.getOrElse(padIndex) { Color(0xFF00E5FF) }

    val titleText = if (pad != null && pad.sampleFileName.isNotEmpty() && !pad.sampleFileName.startsWith("kick_808")) {
        pad.sampleFileName.substringBeforeLast(".").uppercase()
    } else if (pad != null && pad.label.isNotEmpty() && !pad.label.startsWith("PAD")) {
        pad.label.uppercase()
    } else {
        defaultTitles.getOrElse(padIndex) { "SAMPLE ${padIndex + 1}" }
    }

    val subtitleText = if (pad != null && pad.sampleFilePath.isNotEmpty()) {
        "Custom Wave Sample"
    } else {
        defaultSubtitles.getOrElse(padIndex) { "Studio Percussion" }
    }

    val isActivePad = (padIndex == 4) || (pad?.isPressed == true) || isPressed

    val backgroundGradient = if (isPressed || (pad?.isPressed == true)) {
        Brush.radialGradient(
            colors = listOf(
                tagColor.copy(alpha = 0.4f),
                Color(0xFF1E2235),
                Color(0xFF12141F)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1B1E2B),
                Color(0xFF12141F),
                Color(0xFF0D0F17)
            )
        )
    }

    val borderBrush = if (isPressed || (pad?.isPressed == true)) {
        Brush.verticalGradient(listOf(tagColor, tagColor.copy(alpha = 0.6f)))
    } else if (isActivePad) {
        Brush.verticalGradient(listOf(Color(0xFF3B435C), Color(0xFF222636)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF2C3144), Color(0xFF181B26)))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundGradient)
            .border(1.2.dp, borderBrush, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPress()
                        tryAwaitRelease()
                        isPressed = false
                        onRelease()
                    },
                    onLongPress = {
                        onLongPress()
                    }
                )
            }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top glowing horizontal LED lightbar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isPressed || isActivePad) {
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF00E5FF).copy(alpha = 0.7f),
                                    Color(0xFF00E5FF),
                                    Color(0xFF00E5FF).copy(alpha = 0.7f)
                                )
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFF43F5E).copy(alpha = 0.6f),
                                    Color(0xFFF43F5E),
                                    Color(0xFFF43F5E).copy(alpha = 0.6f)
                                )
                            )
                        }
                    )
            )

            // Pad Identifier & Name
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = tagText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = tagColor,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = titleText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitleText,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF7E8B9B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Subtle tactile center circle
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(
                        if (isPressed || (pad?.isPressed == true)) tagColor.copy(alpha = 0.25f)
                        else Color(0x08FFFFFF)
                    )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FluidSquareDrumPadCell(
    pad: DrumPadItem,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onLongPress: () -> Unit
) {
    val style = pad.colorStyle
    val isLoop = pad.isLoopMode
    val isPlaying = pad.isLoopPlaying

    // Pulsing animation for active loop playback
    val infiniteTransition = rememberInfiniteTransition(label = "pad_loop_pulse")
    val loopPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loop_pulse"
    )

    // Vivid LED color palette based on pad ID / style
    val vividLedColor = remember(pad.id, style) {
        when (pad.id % 8) {
            0 -> Color(0xFF00E5FF) // Electric Cyan
            1 -> Color(0xFF84CC16) // Acid Lime
            2 -> Color(0xFFF43F5E) // Hot Pink / Red
            3 -> Color(0xFFFFB300) // Amber Gold
            4 -> Color(0xFF38BDF8) // Ice Blue
            5 -> Color(0xFFA855F7) // Laser Violet
            6 -> Color(0xFF22C55E) // Matrix Green
            else -> Color(0xFFEC4899)// Hot Magenta
        }
    }

    // Silicone Pad Chassis
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0C0E17)) // Deep matte silicone black
            .border(
                width = if (pad.isPressed) 2.8.dp else if (isPlaying) 2.2.dp else 1.8.dp,
                color = if (pad.isPressed) Color.White else if (isPlaying) vividLedColor.copy(alpha = loopPulseAlpha) else vividLedColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(1.5.dp)
    ) {
        // Soft radial center white translucency highlight (silicone core)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(7.dp))
                .background(
                    Brush.radialGradient(
                        colors = if (pad.isPressed) listOf(
                            Color.White.copy(alpha = 0.70f),
                            vividLedColor.copy(alpha = 0.50f),
                            Color(0xFF0C0E17)
                        ) else listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.05f),
                            Color(0xFF0C0E17)
                        )
                    )
                )
                .offset(y = if (pad.isPressed) 1.dp else 0.dp)
                .pointerInput(pad.id) {
                    detectTapGestures(
                        onPress = {
                            onPress()
                            tryAwaitRelease()
                            onRelease()
                        },
                        onLongPress = {
                            onLongPress()
                        }
                    )
                }
                .padding(horizontal = 4.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            // Top Status Row: Pad ID (Left) & Loop Mode Badge (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format("P%02d", pad.id + 1),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (pad.isPressed) Color.White else vividLedColor
                )

                if (isLoop) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isPlaying) NeonCyan.copy(alpha = loopPulseAlpha) else Color(0x4400E5FF)
                            )
                            .padding(horizontal = 3.dp, vertical = 0.5.dp)
                    ) {
                        Text(
                            text = if (isPlaying) "▶ LOOP" else "LOOP",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }

            // Center Content: Label & Sample / Note info in Crisp Bold White
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            ) {
                val labelText = if (pad.label.isNotEmpty()) pad.label else if (pad.soundType == DrumSoundType.SF2_NOTE) pad.sf2Note else pad.sampleFileName.substringBeforeLast(".")
                Text(
                    text = labelText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )

                if (pad.label.isNotEmpty()) {
                    val subText = if (pad.soundType == DrumSoundType.SF2_NOTE) pad.sf2Note else pad.sampleFileName.substringBeforeLast(".")
                    Text(
                        text = subText.take(10),
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFCBD5E1),
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Bottom Active Waveform Indicator when Loop is Playing
            if (isPlaying) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    repeat(5) { idx ->
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height((3 + ((idx * 2) % 5)).dp)
                                .background(Color.White, RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrumSoundfontPickerSubView(
    soundfonts: List<StorageItem>,
    loadedSf2Presets: List<SoundfontPreset>,
    onSelectPreset: (SoundfontPreset) -> Unit,
    onSelectSf2File: (StorageItem) -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onDragHeader: ((Float, Float) -> Unit)?
) {
    var soundTab by remember { mutableStateOf("presets") } // "presets" or "files"

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with Back, Title, Pin & Close
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(isPinned) {
                    if (isPinned && onDragHeader != null) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDragHeader(dragAmount.x, dragAmount.y)
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x2200E5FF))
                        .border(1.dp, NeonCyan, RoundedCornerShape(6.dp))
                        .clickable { onBack() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(text = "← Retour", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                }

                Text(
                    text = "Soundfonts & Presets",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                // Pin Button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isPinned) Color(0x3322D3EE) else Color(0x14FFFFFF))
                        .border(1.dp, if (isPinned) NeonCyan else Color.Transparent, RoundedCornerShape(6.dp))
                        .clickable { onTogglePin() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (isPinned) "📍" else "📌", fontSize = 11.sp, color = if (isPinned) NeonCyan else TextDim)
                }

                // Close Button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x14FFFFFF))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", fontSize = 11.5.sp, color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2 Tabs: Presets Soundfont & Soundfonts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x14FFFFFF))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Tab 1: Presets Soundfont
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (soundTab == "presets") NeonCyan else Color.Transparent)
                    .clickable { soundTab = "presets" }
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Presets Soundfont",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (soundTab == "presets") Color(0xFF003844) else TextDim
                )
            }

            // Tab 2: Soundfonts
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (soundTab == "files") NeonCyan else Color.Transparent)
                    .clickable { soundTab = "files" }
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Soundfonts",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (soundTab == "files") Color(0xFF003844) else TextDim
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (soundTab == "presets") {
            if (loadedSf2Presets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x08FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucun preset disponible.\nSélectionnez un fichier dans l'onglet Soundfonts (.sf2)",
                        fontSize = 10.sp,
                        color = TextDim,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(loadedSf2Presets) { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x0EFFFFFF))
                                .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(8.dp))
                                .clickable { onSelectPreset(preset) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = preset.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(text = "Bank: ${preset.bankNumber} · Preset: ${preset.id}", fontSize = 8.5.sp, color = TextDim2)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x2200E5FF))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(text = "Charger", fontSize = 8.5.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // Soundfonts files list (.sf2 files)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (soundfonts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Aucun fichier .sf2 trouvé", fontSize = 10.sp, color = TextDim)
                        }
                    }
                } else {
                    items(soundfonts) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x0EFFFFFF))
                                .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(8.dp))
                                .clickable { onSelectSf2File(file) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Column {
                                    Text(text = file.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x2200E5FF))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(text = "Ouvrir", fontSize = 8.5.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickPadAssignModal(
    title: String,
    pads: List<DrumPadItem>,
    initialIsLoop: Boolean = false,
    onSelectPad: (padId: Int, isLoop: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var isLoopMode by remember { mutableStateOf(initialIsLoop) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable { onDismiss() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1A1D2C))
                .border(1.dp, NeonCyan, RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .padding(14.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Toggle: One-Shot vs Continuous Loop
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF111420))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!isLoopMode) Color(0x3322D3EE) else Color.Transparent)
                            .clickable { isLoopMode = false }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⚡ Coup Simple (One-Shot)",
                            fontSize = 9.sp,
                            fontWeight = if (!isLoopMode) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isLoopMode) NeonCyan else TextDim
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isLoopMode) NeonCyan else Color.Transparent)
                            .clickable { isLoopMode = true }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔁 Loop Continu",
                            fontSize = 9.sp,
                            fontWeight = if (isLoopMode) FontWeight.Bold else FontWeight.Normal,
                            color = if (isLoopMode) Color(0xFF002233) else TextDim
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 160.dp)
                ) {
                    items(pads.take(8)) { pad ->
                        val hasSample = pad.sampleFileName.isNotEmpty()
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (hasSample) Color(0xFF22283A) else Color(0xFF161926))
                                .border(1.5.dp, pad.colorStyle.primaryColor, RoundedCornerShape(10.dp))
                                .clickable { onSelectPad(pad.id, isLoopMode) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "PAD ${pad.id}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = pad.colorStyle.primaryColor
                                )
                                Text(
                                    text = if (pad.label.isNotEmpty()) pad.label else if (hasSample) "Assigned" else "Empty",
                                    fontSize = 8.5.sp,
                                    color = if (hasSample) TextPrimary else TextDim2,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                TextButton(onClick = onDismiss) {
                    Text(text = "Cancel", fontSize = 11.sp, color = TextDim)
                }
            }
        }
    }
}

@Composable
private fun PadCustomizerScreen(
    pad: DrumPadItem,
    onSave: (newLabel: String, newStyle: DrumPadStyle, isLoopMode: Boolean) -> Unit,
    onAssignSample: () -> Unit,
    onAssignSoundfont: () -> Unit = {},
    onBack: () -> Unit
) {
    var labelText by remember { mutableStateOf(pad.label) }
    var selectedStyle by remember { mutableStateOf(pad.colorStyle) }
    var isLoopMode by remember { mutableStateOf(pad.isLoopMode) }
    var selectedCategory by remember { mutableStateOf(DrumPadCategory.GRADIENT) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Back Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x14FFFFFF))
                    .clickable { onBack() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = "← Cancel", fontSize = 9.5.sp, color = NeonCyan)
            }

            Text(
                text = String.format("Pad Settings - P%02d", pad.id + 1),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(NeonCyan)
                    .clickable { onSave(labelText, selectedStyle, isLoopMode) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(text = "Save", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF002233))
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Rename Input
            item {
                Text(text = "PAD LABEL", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                Spacer(modifier = Modifier.height(3.dp))
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it.take(12) },
                    placeholder = { Text("Ex: Kick, Snare...", fontSize = 10.sp, color = TextDim) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 2. Trigger Mode (One-Shot vs Loop)
            item {
                Text(text = "TRIGGER MODE", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E212E))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!isLoopMode) Color(0x3322D3EE) else Color.Transparent)
                            .clickable { isLoopMode = false }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ONE-SHOT",
                            fontSize = 9.sp,
                            fontWeight = if (!isLoopMode) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isLoopMode) NeonCyan else TextDim
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isLoopMode) NeonCyan else Color.Transparent)
                            .clickable { isLoopMode = true }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CONTINUOUS LOOP",
                            fontSize = 9.sp,
                            fontWeight = if (isLoopMode) FontWeight.Bold else FontWeight.Normal,
                            color = if (isLoopMode) Color(0xFF002233) else TextDim
                        )
                    }
                }
            }

            // 3. Sound Assignment Shortcuts
            item {
                Text(text = "SOUND SOURCE", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E212E))
                        .border(1.dp, Color(0x3322D3EE), RoundedCornerShape(8.dp))
                        .clickable { onAssignSample() }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(text = "Select Audio Sample", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 4. Style Categories & Palette
            item {
                Text(text = "COLOR PALETTE & STYLE", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DrumPadCategory.values().forEach { cat ->
                        val isCatSel = (cat == selectedCategory)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isCatSel) NeonCyan else Color(0x14FFFFFF))
                                .clickable { selectedCategory = cat }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat.title,
                                fontSize = 8.sp,
                                fontWeight = if (isCatSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCatSel) Color(0xFF002233) else TextDim
                            )
                        }
                    }
                }
            }

            // Style Swatches
            val stylesInCat = DrumPadStyle.values().filter { it.category == selectedCategory }
            items(stylesInCat.chunked(3)) { rowStyles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowStyles.forEach { st ->
                        val isSelected = (st == selectedStyle)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.verticalGradient(listOf(st.primaryColor, st.secondaryColor)))
                                .border(
                                    2.dp,
                                    if (isSelected) Color.White else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedStyle = st }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = st.displayName,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrumSf2PickerSubView(
    soundfonts: List<StorageItem>,
    onSelectNote: (key: String, oct: Int) -> Unit,
    onBack: () -> Unit
) {
    var selectedKey by remember { mutableStateOf("C") }
    var selectedOct by remember { mutableIntStateOf(2) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x14FFFFFF))
                    .clickable { onBack() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = "← Retour", fontSize = 9.5.sp, color = NeonCyan)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = "Assigner Note Soundfont", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(text = "CHOISIR LA NOTE & OCTAVE", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
            }

            item {
                val notes = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(130.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(notes) { n ->
                        val isSel = (n == selectedKey)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) NeonCyan else Color(0xFF1E212E))
                                .clickable { selectedKey = n }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = n,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color(0xFF002233) else Color.White
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Octave :", fontSize = 9.5.sp, color = TextDim)
                    (1..4).forEach { o ->
                        val isSel = (o == selectedOct)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) NeonCyan else Color(0x14FFFFFF))
                                .clickable { selectedOct = o }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "C$o",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color(0xFF002233) else TextPrimary
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonCyan)
                        .clickable { onSelectNote(selectedKey, selectedOct) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Valider $selectedKey$selectedOct",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF002233)
                    )
                }
            }
        }
    }
}
