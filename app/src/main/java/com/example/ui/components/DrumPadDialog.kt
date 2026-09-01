package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*
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
    soundfonts: List<StorageItem>,
    currentSoundfontName: String = "FluidR3_GM.sf2",
    loadedSf2Presets: List<SoundfontPreset> = emptyList(),
    onSelectPreset: (SoundfontPreset) -> Unit = {},
    onSelectSf2File: (StorageItem) -> Unit = {},
    onOpenSoundfontPicker: () -> Unit = {},
    audioFiles: List<StorageItem>,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onClose: () -> Unit,
    onPadPressed: (Int) -> Unit,
    onPadReleased: (Int) -> Unit,
    onPlayNote: (note: String, octave: Int) -> Unit = { _, _ -> },
    onPlaySample: (StorageItem) -> Unit = {},
    onUpdatePadCustomization: (padId: Int, label: String, style: DrumPadStyle) -> Unit,
    onAssignPadSample: (padId: Int, sample: StorageItem) -> Unit,
    onAssignPadNote: (padId: Int, noteStr: String, oct: Int, key: String) -> Unit,
    initialOffsetX: Float = 0f,
    initialOffsetY: Float = 0f,
    initialSizeDp: Float = 440f,
    onTransformChange: (Float, Float, Float) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var editingPad by remember { mutableStateOf<DrumPadItem?>(null) }
    var quickAssignNote by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var quickAssignSample by remember { mutableStateOf<StorageItem?>(null) }
    var isSoundPickerOpen by remember { mutableStateOf(false) }

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
                    isSoundPickerOpen -> {
                        DrumSoundfontPickerSubView(
                            soundfonts = soundfonts,
                            loadedSf2Presets = loadedSf2Presets,
                            onSelectPreset = { preset ->
                                onSelectPreset(preset)
                                isSoundPickerOpen = false
                            },
                            onSelectSf2File = { file ->
                                onSelectSf2File(file)
                                isSoundPickerOpen = false
                            },
                            isPinned = isPinned,
                            onTogglePin = onTogglePin,
                            onClose = onClose,
                            onBack = { isSoundPickerOpen = false },
                            onDragHeader = { dx, dy ->
                                offsetX = (offsetX + dx).coerceIn(-maxDragX, maxDragX)
                                offsetY = (offsetY + dy).coerceIn(-maxDragY, maxDragY)
                                onTransformChange(offsetX, offsetY, windowSizeDp.value)
                            }
                        )
                    }
                    editingPad != null -> {
                        PadCustomizerScreen(
                            pad = editingPad!!,
                            onSave = { newLabel, newStyle ->
                                onUpdatePadCustomization(editingPad!!.id, newLabel, newStyle)
                                editingPad = null
                            },
                            onAssignSoundfont = {
                                onSetSubView("sf2_picker")
                            },
                            onAssignSample = {
                                onTabChange("files")
                                editingPad = null
                            },
                            onBack = { editingPad = null }
                        )
                    }
                    subView == "sf2_picker" -> {
                        DrumSf2PickerSubView(
                            soundfonts = soundfonts,
                            onSelectNote = { key, oct ->
                                editingPad?.let { pad ->
                                    onAssignPadNote(pad.id, "$key$oct", oct, key)
                                }
                                onSetSubView("main")
                            },
                            onBack = { onSetSubView("main") }
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
                            currentSoundfontName = currentSoundfontName,
                            onOpenSoundfontPicker = { isSoundPickerOpen = true },
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
                            onPlayNote = onPlayNote,
                            onPlaySample = onPlaySample,
                            onLongPressNote = { note, oct -> quickAssignNote = note to oct },
                            onLongPressSample = { file -> quickAssignSample = file }
                        )
                    }
                }

                // Discrete Resize Arrow (Identical to TonicPad)
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

                // Quick Pad Assignment Modal (When long-pressing note or sample)
                if (quickAssignNote != null) {
                    val (note, oct) = quickAssignNote!!
                    QuickPadAssignModal(
                        title = "Assigner Note $note$oct au Pad",
                        pads = drumPads,
                        onSelectPad = { padId ->
                            onAssignPadNote(padId, "$note$oct", oct, note)
                            quickAssignNote = null
                        },
                        onDismiss = { quickAssignNote = null }
                    )
                }

                if (quickAssignSample != null) {
                    val sample = quickAssignSample!!
                    QuickPadAssignModal(
                        title = "Assigner ${sample.name.take(16)} au Pad",
                        pads = drumPads,
                        onSelectPad = { padId ->
                            onAssignPadSample(padId, sample)
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
private fun MainDrumPadSquareContent(
    drumPads: List<DrumPadItem>,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    reverb: Float,
    onReverbChange: (Float) -> Unit,
    activeTab: String,
    onTabChange: (String) -> Unit,
    currentSoundfontName: String,
    onOpenSoundfontPicker: () -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onClose: () -> Unit,
    onDragWindow: (Float, Float) -> Unit,
    onPadPressed: (Int) -> Unit,
    onPadReleased: (Int) -> Unit,
    onLongPressPad: (DrumPadItem) -> Unit,
    audioFiles: List<StorageItem>,
    onPlayNote: (String, Int) -> Unit,
    onPlaySample: (StorageItem) -> Unit,
    onLongPressNote: (String, Int) -> Unit,
    onLongPressSample: (StorageItem) -> Unit
) {
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
                Text(text = "🥁", fontSize = 14.sp)
                Text(
                    text = "DrumPad",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Soundfont Capsule Indicator & Selector
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E2232))
                        .border(0.8.dp, Color(0x3322D3EE), RoundedCornerShape(6.dp))
                        .clickable { onOpenSoundfontPicker() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(text = "🎹", fontSize = 9.sp)
                        Text(
                            text = currentSoundfontName.ifEmpty { "Soundfont" },
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = NeonCyanLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 95.dp)
                        )
                        Text(text = "▼", fontSize = 7.sp, color = Color(0xAAFFFFFF))
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Minimalist Pin Icon
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (isPinned) Color(0x3322D3EE) else Color(0x14FFFFFF))
                        .border(1.dp, if (isPinned) NeonCyan else Color(0x22FFFFFF), CircleShape)
                        .clickable { onTogglePin() }
                        .testTag("btn_pin_drumpad"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPinned) "📍" else "📌",
                        fontSize = 11.sp,
                        color = if (isPinned) NeonCyan else TextDim
                    )
                }

                // Close Button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0x14FFFFFF))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", fontSize = 11.sp, color = TextPrimary)
                }
            }
        }

        // ================= 3 TABS: Pads, Notes, Fichiers =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1C1F2D))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            listOf(
                "pad" to "Pads",
                "notes" to "Notes",
                "files" to "Fichiers"
            ).forEach { (tabId, label) ->
                val isSel = (tabId == activeTab)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSel) NeonCyan else Color.Transparent)
                        .clickable { onTabChange(tabId) }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) Color(0xFF002233) else TextDim
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ================= TAB CONTENT =================
        when (activeTab) {
            "pad" -> {
                // 1_PAD TAB: 8 Fluid Adaptive Pads (2 rows x 4 cols) + 3D Knobs
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // 8-Pad Adaptive Fluid Grid
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Top Row (Pads 1..4)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            drumPads.take(4).forEach { pad ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    FluidSquareDrumPadCell(
                                        pad = pad,
                                        onPress = { onPadPressed(pad.id) },
                                        onRelease = { onPadReleased(pad.id) },
                                        onLongPress = { onLongPressPad(pad) }
                                    )
                                }
                            }
                        }

                        // Bottom Row (Pads 5..8)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            drumPads.drop(4).take(4).forEach { pad ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
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

                    Spacer(modifier = Modifier.height(6.dp))

                    // 3D Realistic Knobs with Morphing Glowing LED Rings (Volume & Reverb)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF181B26))
                            .border(0.8.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                            .padding(vertical = 3.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Led3DKnob(
                            value = volume,
                            onValueChange = onVolumeChange,
                            label = "VOLUME",
                            valueText = "${(volume * 100).toInt()}%",
                            size = 36.dp,
                            baseColor = NeonCyan
                        )

                        Led3DKnob(
                            value = reverb,
                            onValueChange = onReverbChange,
                            label = "REVERB",
                            valueText = "${(reverb * 100).toInt()}%",
                            size = 36.dp,
                            baseColor = NeonMagenta
                        )
                    }
                }
            }

            "notes" -> {
                // NOTES TAB: Notes C-B with Octave selector C1 to C8
                var selectedOctave by remember { mutableIntStateOf(3) }
                val notes = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Octave Selection with +/- arrow buttons (C1 to C8)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "Octave :", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDim)

                        // Decrement Button (-)
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(0x1EFFFFFF))
                                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                                .clickable { if (selectedOctave > 1) selectedOctave-- },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "−",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedOctave > 1) NeonCyan else TextDim2
                            )
                        }

                        // Current Octave Badge (C1..C8)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonCyan.copy(alpha = 0.2f))
                                .border(1.2.dp, NeonCyan, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "C$selectedOctave",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = NeonCyanLight
                            )
                        }

                        // Increment Button (+)
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(0x1EFFFFFF))
                                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                                .clickable { if (selectedOctave < 8) selectedOctave++ },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedOctave < 8) NeonCyan else TextDim2
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Appui court = Jouer · Long = Assigner",
                            fontSize = 8.sp,
                            color = TextDim2
                        )
                    }

                    // Notes Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(notes) { note ->
                            val isSharp = note.contains("#")
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1.2f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSharp) Color(0xFF171A24) else Color(0xFF222738))
                                    .border(1.dp, if (isSharp) Color(0x4400E5FF) else Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                                    .combinedClickable(
                                        onClick = { onPlayNote(note, selectedOctave) },
                                        onLongClick = { onLongPressNote(note, selectedOctave) }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$note$selectedOctave",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSharp) NeonCyanLight else Color.White
                                    )
                                    Text(text = "Note", fontSize = 7.5.sp, color = TextDim)
                                }
                            }
                        }
                    }
                }
            }

            "files" -> {
                // 3_FILES TAB: Elements of /DrumPad in clean AOSP style list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Text(
                        text = "Éléments du dossier /DrumPad (Appui court = Jouer · Long = Assigner)",
                        fontSize = 8.5.sp,
                        color = TextDim2,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

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
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(text = "🎵", fontSize = 11.sp)
                                        Column {
                                            Text(
                                                text = file.name,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                            Text(text = file.formattedSize, fontSize = 8.sp, color = TextDim2)
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0x2222D3EE))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = "Assigner", fontSize = 8.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (pad.isPressed) {
                    Brush.verticalGradient(listOf(style.primaryColor, style.secondaryColor))
                } else {
                    Brush.verticalGradient(
                        listOf(
                            style.primaryColor.copy(alpha = 0.25f),
                            style.secondaryColor.copy(alpha = 0.12f)
                        )
                    )
                }
            )
            .border(
                1.5.dp,
                if (pad.isPressed) Color.White else style.primaryColor.copy(alpha = 0.65f),
                RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = {
                    onPress()
                    onRelease()
                },
                onLongClick = onLongPress
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "P${pad.id}",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (pad.isPressed) Color.White else style.primaryColor
            )
            if (pad.label.isNotEmpty()) {
                Text(
                    text = pad.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (pad.soundType == DrumSoundType.SF2_NOTE) pad.sf2Note else pad.sampleFileName.take(6),
                fontSize = 7.5.sp,
                color = TextDim,
                maxLines = 1
            )
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
            val presetsList = if (loadedSf2Presets.isNotEmpty()) loadedSf2Presets else listOf(
                SoundfontPreset(0, "Standard Drum Kit", 128),
                SoundfontPreset(1, "Electronic Drum Kit", 128),
                SoundfontPreset(2, "Power Drum Kit", 128),
                SoundfontPreset(3, "Synth Bass & Kick", 0),
                SoundfontPreset(4, "Percussion Set", 128)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(presetsList) { preset ->
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
                                Text(text = "📦", fontSize = 11.sp)
                                Column {
                                    Text(text = file.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text(text = file.formattedSize, fontSize = 8.5.sp, color = TextDim2)
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
    onSelectPad: (Int) -> Unit,
    onDismiss: () -> Unit
) {
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
                                .clickable { onSelectPad(pad.id) }
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
    onSave: (newLabel: String, newStyle: DrumPadStyle) -> Unit,
    onAssignSoundfont: () -> Unit,
    onAssignSample: () -> Unit,
    onBack: () -> Unit
) {
    var labelText by remember { mutableStateOf(pad.label) }
    var selectedStyle by remember { mutableStateOf(pad.colorStyle) }
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
                Text(text = "← Annuler", fontSize = 9.5.sp, color = NeonCyan)
            }

            Text(
                text = "Modifier Pad ${pad.id}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(NeonCyan)
                    .clickable { onSave(labelText, selectedStyle) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(text = "Enregistrer", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF002233))
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
                Text(text = "NOM DU PAD", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
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

            // 2. Sound Assignment Shortcuts
            item {
                Text(text = "SOURCE SONORE", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E212E))
                            .clickable { onAssignSoundfont() }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🎹 Note Soundfont", fontSize = 9.sp, color = NeonCyanLight, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E212E))
                            .clickable { onAssignSample() }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "📁 Échantillon /DrumPad", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 3. Style Categories & Palette
            item {
                Text(text = "PALETTE DE COULEURS & STYLE", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
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
