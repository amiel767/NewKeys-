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
                    .background(Color(0xFF131622))
                    .border(
                        1.2.dp,
                        if (isPinned) NeonCyan.copy(alpha = 0.8f) else Color(0x33FFFFFF),
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

    Column(modifier = Modifier.fillMaxSize()) {
        // ================= TOP BAR WITH TITLE, SOUNDFONT PICKER & MINIMALIST PIN =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDragWindow(dragAmount.x, dragAmount.y)
                    }
                }
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "DrumPad",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Close Button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                        .border(1.dp, Color(0x33FFFFFF), CircleShape)
                        .clickable { onClose() }
                        .testTag("btn_close_drumpad"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }
        }

        // ================= TABS & LIVE CONTROLS =================
        val infinitePulse = rememberInfiniteTransition(label = "rec_pulse")
        val recPulseAlpha by infinitePulse.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(450),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rec_alpha"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Tempo, Time Signature & Stepper [-] ${loopBars}B [+]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Tempo indicator
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E2232))
                        .border(0.8.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                        .padding(horizontal = 5.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$bpm BPM",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Time Signature Indicator (linked to case rhythm signature, clickable)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF181B26))
                        .border(0.8.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { onToggleTimeSignature() }
                        .padding(horizontal = 5.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = timeSignature,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonCyanLight
                    )
                }

                // Bar length stepper: [-] ${loopBars}B [+]
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF181B26))
                        .border(0.8.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF24283B))
                            .clickable { onDecrementLoopBars() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "−", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Text(
                        text = "${loopBars}B",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonCyan,
                        modifier = Modifier.padding(horizontal = 3.dp)
                    )

                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF24283B))
                            .clickable { onIncrementLoopBars() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "+", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // Center: Tabs [ Pads | Fichiers | Loops ]
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1C1F2D))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(
                    "pad" to "Pads",
                    "files" to "Fichiers",
                    "loops" to "Loops"
                ).forEach { (tabId, label) ->
                    val isSel = (tabId == activeTab)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) NeonCyan else Color.Transparent)
                            .clickable { onTabChange(tabId) }
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 9.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Color(0xFF002233) else TextDim
                        )
                    }
                }
            }

            // Right: Minimal Loop Arm Icon + Pro Studio REC Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Minimal Loop Arm Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isArmed) Color(0xFF382D10)
                            else Color(0xFF1E2232)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isArmed) NeonCyan.copy(alpha = recPulseAlpha) else Color(0x33FFFFFF),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onToggleArmLoop() }
                        .padding(horizontal = 5.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Loop Arm",
                            tint = if (isArmed) NeonCyan else TextDim,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = if (isArmed) "ARM" else "Loop",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isArmed) NeonCyanLight else TextDim
                        )
                    }
                }

                // Studio REC Icon Button (Pro DrumPad Record)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isRecording) Color(0xFF3B0D14)
                            else Color(0xFF1E2232)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isRecording) MuteRed.copy(alpha = recPulseAlpha) else Color(0x33FFFFFF),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            if (isRecording) {
                                onStopRecording()
                            } else {
                                onStartRecording()
                            }
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isRendering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                color = NeonCyan,
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            // Studio Record Glowing Dot
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isRecording) MuteRed.copy(alpha = recPulseAlpha)
                                        else MuteRed
                                    )
                            )
                        }
                        Text(
                            text = if (isRecording) "REC..." else "REC",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isRecording) MuteRed else Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ================= TAB CONTENT =================
        when (activeTab) {
            "pad" -> {
                // EXACTLY 8 PADS (2 Rows x 4 Columns) + Volume & Reverb Knobs
                val currentPads = drumPads.take(8)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 2 rows x 4 columns Grid (8 Cases)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        (0..1).forEach { rowIndex ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                (0..3).forEach { colIndex ->
                                    val padIndex = rowIndex * 4 + colIndex
                                    val pad = currentPads.getOrNull(padIndex)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    ) {
                                        if (pad != null) {
                                            FluidSquareDrumPadCell(
                                                pad = pad,
                                                onPress = { onPadPressed(pad.id) },
                                                onRelease = { onPadReleased(pad.id) },
                                                onLongPress = { onLongPressPad(pad) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Vertical 3D Realistic Knobs Column on the right
                    Column(
                        modifier = Modifier
                            .width(68.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF181B26))
                            .border(0.8.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Led3DKnob(
                            value = volume,
                            onValueChange = onVolumeChange,
                            label = "VOLUME",
                            showFloatingTooltipOnTouch = true,
                            size = 38.dp,
                            baseColor = NeonCyan
                        )

                        Led3DKnob(
                            value = reverb,
                            onValueChange = onReverbChange,
                            label = "REVERB",
                            showFloatingTooltipOnTouch = true,
                            size = 38.dp,
                            baseColor = NeonMagenta
                        )
                    }
                }
            }
            "files" -> {
                // FILES TAB: Elements of /DrumPad in clean AOSP style list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Éléments audio /DrumPad (Court = Jouer · Long = Assigner)",
                            fontSize = 8.5.sp,
                            color = TextDim2
                        )
                        if (onImportAudioFile != null) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(NeonCyan)
                                    .clickable { isNativeBrowserOpen = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Parcourir Stockage / SD",
                                    tint = Color(0xFF0F2537),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    if (isNativeBrowserOpen) {
                        NativeFileBrowserDialog(
                            isOpen = isNativeBrowserOpen,
                            onClose = { isNativeBrowserOpen = false },
                            initialPath = lastPath.ifEmpty { "/storage/emulated/0/SoundStage/DrumPad" },
                            title = "Explorateur DrumPad (Stockage & SD)",
                            onPathChanged = { newPath -> onUpdateLastPath(newPath) },
                            onFileSelected = { file ->
                                val sampleItem = StorageItem(name = file.name, path = file.absolutePath, isDirectory = false)
                                onPlaySample(sampleItem)
                                onLongPressSample(sampleItem)
                                isNativeBrowserOpen = false
                            }
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (audioFiles.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Aucun fichier audio dans /LiveKeys/DrumPad",
                                        fontSize = 10.sp,
                                        color = TextDim
                                    )
                                }
                            }
                        } else {
                            items(audioFiles) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E212E))
                                        .border(0.8.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                                        .combinedClickable(
                                            onClick = { onPlaySample(file) },
                                            onLongClick = { onLongPressSample(file) }
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
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = NeonCyan,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Column {
                                            Text(
                                                text = file.name,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Direct Assigner Button
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0x2222D3EE))
                                            .border(0.8.dp, NeonCyan, RoundedCornerShape(6.dp))
                                            .clickable { onLongPressSample(file) }
                                            .padding(horizontal = 7.dp, vertical = 3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Assigner",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeonCyanLight
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "loops" -> {
                // LOOPS TAB: Recorded drum pad loops with Reveal Actions & Delete pattern
                var revealedLoopName by remember { mutableStateOf<String?>(null) }
                var fileToDelete by remember { mutableStateOf<StorageItem?>(null) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Boucles enregistrées (Court = Jouer · Long = Assigner / Supprimer)",
                            fontSize = 8.5.sp,
                            color = TextDim2
                        )
                        Text(
                            text = "${loopFiles.size} boucle${if (loopFiles.size > 1) "s" else ""}",
                            fontSize = 8.5.sp,
                            color = NeonCyanLight
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
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Aucune boucle enregistrée.",
                                        fontSize = 10.sp,
                                        color = TextDim
                                    )
                                }
                            }
                        } else {
                            items(loopFiles) { file ->
                                val isActionsRevealed = (revealedLoopName == file.name)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isActionsRevealed) Color(0x288B5CF6) else Color(0xFF222533))
                                        .border(
                                            0.8.dp,
                                            if (isActionsRevealed) NeonPurpleLight.copy(alpha = 0.7f) else Color(0x18FFFFFF),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                if (isActionsRevealed) {
                                                    revealedLoopName = null
                                                } else {
                                                    onPlaySample(file)
                                                }
                                            },
                                            onLongClick = {
                                                revealedLoopName = if (isActionsRevealed) null else file.name
                                            }
                                        )
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
                                                .background(Color(0xFF0F2537)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("🔄", fontSize = 10.sp)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.name,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Action buttons when revealed
                                    AnimatedVisibility(
                                        visible = isActionsRevealed,
                                        enter = fadeIn() + expandHorizontally(),
                                        exit = fadeOut() + shrinkHorizontally()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Assign to Pad Button
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0x2222D3EE))
                                                    .border(0.8.dp, NeonCyan, RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        revealedLoopName = null
                                                        onLongPressSample(file)
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Assigner",
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NeonCyanLight
                                                )
                                            }

                                            // Delete Button (Trash icon)
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0x22FB4570))
                                                    .border(1.dp, MuteRed.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        fileToDelete = file
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Supprimer",
                                                    tint = MuteRed,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Delete Confirmation Dialog
                    if (fileToDelete != null) {
                        val target = fileToDelete!!
                        AlertDialog(
                            onDismissRequest = { fileToDelete = null },
                            title = {
                                Text(
                                    text = "Supprimer la boucle ?",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            },
                            text = {
                                Text(
                                    text = "Voulez-vous vraiment supprimer définitivement « ${target.name} » ?",
                                    color = TextDim,
                                    fontSize = 11.sp
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        onDeleteLoopFile(target)
                                        fileToDelete = null
                                        revealedLoopName = null
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MuteRed)
                                ) {
                                    Text("Supprimer", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { fileToDelete = null }) {
                                    Text("Annuler", color = TextDim)
                                }
                            },
                            containerColor = Color(0xFF222638),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }
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
private fun QuickPadAssignModal(
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(pads) { pad ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF252A3C))
                                .border(1.dp, pad.colorStyle.primaryColor, RoundedCornerShape(8.dp))
                                .clickable { onSelectPad(pad.id, isLoopMode) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Pad ${pad.id}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = pad.colorStyle.primaryColor
                                )
                                if (pad.label.isNotEmpty()) {
                                    Text(
                                        text = pad.label,
                                        fontSize = 8.sp,
                                        color = TextDim
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                TextButton(onClick = onDismiss) {
                    Text(text = "Annuler", fontSize = 10.sp, color = TextDim)
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
