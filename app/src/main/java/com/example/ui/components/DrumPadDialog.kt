package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DrumPadItem
import com.example.model.DrumSoundType
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun DrumPadDialog(
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
    onOpenSf2Picker: () -> Unit,
    onPadPressed: (Int) -> Unit,
    onPadReleased: (Int) -> Unit,
    onOpenAssigner: (Int) -> Unit,
    editingPadId: Int?,
    onAssignSample: (Int, String) -> Unit,
    onAssignSf2Note: (Int, String, Int) -> Unit,
    onCloseAssigner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // Floating window state (offsets & dimensions)
    var floatingOffsetX by remember { mutableFloatStateOf(40f) }
    var floatingOffsetY by remember { mutableFloatStateOf(30f) }
    var windowWidthDp by remember { mutableStateOf(520.dp) }
    var windowHeightDp by remember { mutableStateOf(290.dp) }

    if (isPinned) {
        // FLOATING RESIZABLE WINDOW (No blocking backdrop scrim)
        Box(
            modifier = modifier
                .offset { IntOffset(floatingOffsetX.roundToInt(), floatingOffsetY.roundToInt()) }
                .size(windowWidthDp, windowHeightDp)
                .shadow(24.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF282838), Color(0xFF1B1B24), Color(0xFF14141C))
                    )
                )
                .border(1.5.dp, NeonCyan, RoundedCornerShape(16.dp))
                .testTag("floating_drum_pad")
        ) {
            DrumPadContent(
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
                onOpenSf2Picker = onOpenSf2Picker,
                onPadPressed = onPadPressed,
                onPadReleased = onPadReleased,
                onOpenAssigner = onOpenAssigner,
                onDragHeader = { dx, dy ->
                    floatingOffsetX = (floatingOffsetX + dx).coerceIn(0f, 600f)
                    floatingOffsetY = (floatingOffsetY + dy).coerceIn(0f, 350f)
                }
            )

            // Resize Handle in bottom-right corner
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .clip(RoundedCornerShape(bottomEnd = 16.dp))
                    .background(Color(0x2222D3EE))
                    .pointerInput(density) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val dxDp = with(density) { dragAmount.x.toDp() }
                            val dyDp = with(density) { dragAmount.y.toDp() }
                            windowWidthDp = (windowWidthDp + dxDp).coerceIn(320.dp, 750.dp)
                            windowHeightDp = (windowHeightDp + dyDp).coerceIn(180.dp, 440.dp)
                        }
                    }
                    .padding(4.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Text(
                    text = "◢",
                    fontSize = 12.sp,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
            }

            // Sound Assigner Modal on Long Press
            if (editingPadId != null) {
                val pad = drumPads.find { it.id == editingPadId }
                if (pad != null) {
                    DrumSoundAssignerModal(
                        pad = pad,
                        onAssignSample = { sample -> onAssignSample(pad.id, sample) },
                        onAssignSf2Note = { key, oct -> onAssignSf2Note(pad.id, key, oct) },
                        onClose = onCloseAssigner
                    )
                }
            }
        }
    } else {
        // STANDARD CENTERED MODAL DIALOG
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0x660A0A0E))
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.90f)
                    .shadow(24.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF2A2A38), Color(0xFF1B1B24), Color(0xFF17171F))
                        )
                    )
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                    .clickable(enabled = false) {}
                    .testTag("dialog_drum_pad")
            ) {
                DrumPadContent(
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
                    onOpenSf2Picker = onOpenSf2Picker,
                    onPadPressed = onPadPressed,
                    onPadReleased = onPadReleased,
                    onOpenAssigner = onOpenAssigner,
                    onDragHeader = null
                )

                // Sound Assigner Modal on Long Press
                if (editingPadId != null) {
                    val pad = drumPads.find { it.id == editingPadId }
                    if (pad != null) {
                        DrumSoundAssignerModal(
                            pad = pad,
                            onAssignSample = { sample -> onAssignSample(pad.id, sample) },
                            onAssignSf2Note = { key, oct -> onAssignSf2Note(pad.id, key, oct) },
                            onClose = onCloseAssigner
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrumPadContent(
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
    onOpenSf2Picker: () -> Unit,
    onPadPressed: (Int) -> Unit,
    onPadReleased: (Int) -> Unit,
    onOpenAssigner: (Int) -> Unit,
    onDragHeader: ((Float, Float) -> Unit)?
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        val isCompact = maxWidth < 440.dp || maxHeight < 240.dp
        val isUltraCompact = maxWidth < 360.dp || maxHeight < 200.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Header with draggable bar when pinned
            val headerModifier = if (onDragHeader != null) {
                Modifier
                    .fillMaxWidth()
                    .height(if (isCompact) 24.dp else 28.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDragHeader(dragAmount.x, dragAmount.y)
                        }
                    }
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(if (isCompact) 24.dp else 28.dp)
            }

            Row(
                modifier = headerModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isPinned) {
                        Text(text = "✥", fontSize = 11.sp, color = NeonCyan)
                    }
                    Text(
                        text = if (isUltraCompact) "Drum Pad" else "Drum Pad — FX Worship",
                        fontSize = if (isCompact) 12.sp else 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Pin Button (Toggles Floating Mode)
                    Box(
                        modifier = Modifier
                            .size(if (isCompact) 22.dp else 26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isPinned) NeonCyan else Color(0x14FFFFFF))
                            .border(1.dp, if (isPinned) Color.Transparent else Color(0x26FFFFFF), RoundedCornerShape(6.dp))
                            .clickable { onTogglePin() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "📌", fontSize = if (isCompact) 9.sp else 11.sp)
                    }

                    // Close Button
                    Box(
                        modifier = Modifier
                            .size(if (isCompact) 22.dp else 26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(6.dp))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "✕", fontSize = if (isCompact) 10.sp else 12.sp, color = TextPrimary)
                    }
                }
            }

            if (!isUltraCompact) {
                Spacer(modifier = Modifier.height(4.dp))

                // Soundfont Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x0AFFFFFF))
                        .border(1.dp, Color(0x17FFFFFF), RoundedCornerShape(8.dp))
                        .clickable { onOpenSf2Picker() }
                        .padding(horizontal = 8.dp, vertical = if (isCompact) 4.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isCompact) 22.dp else 26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Brush.linearGradient(listOf(NeonCyanLight, NeonCyanDark))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🎹", fontSize = if (isCompact) 10.sp else 11.sp)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Soundfont / Kit", fontSize = 8.sp, color = TextDim2)
                        Text(
                            text = "FX Worship.sf2",
                            fontSize = if (isCompact) 10.5.sp else 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Text(text = "›", fontSize = 14.sp, color = TextDim)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Tabs (Pads, Bank .sf2, Fichiers /DrumPad)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("pads" to "Pads", "bank" to "Bank .sf2", "files" to "Fichiers").forEach { (tabId, tabLabel) ->
                    val isSelected = activeTab == tabId
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) Brush.verticalGradient(listOf(NeonCyanLight, NeonCyan)) else Brush.linearGradient(listOf(Color(0x0DFFFFFF), Color(0x08FFFFFF)))
                            )
                            .border(
                                1.dp,
                                if (isSelected) Color.Transparent else Color(0x14FFFFFF),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onTabChange(tabId) }
                            .padding(horizontal = if (isCompact) 8.dp else 10.dp, vertical = if (isCompact) 3.dp else 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabLabel,
                            fontSize = if (isCompact) 9.5.sp else 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF003844) else TextDim
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Content View
            when (activeTab) {
                "pads" -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Knobs Side Column
                        Column(
                            modifier = Modifier
                                .width(if (isCompact) 65.dp else 80.dp)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            RotaryKnob(
                                value = volume,
                                onValueChange = onVolumeChange,
                                label = "Volume",
                                valueText = "${((volume * 12) - 12).toInt()} dB",
                                size = if (isCompact) 36.dp else 46.dp
                            )

                            RotaryKnob(
                                value = reverb,
                                onValueChange = onReverbChange,
                                label = "Reverb",
                                valueText = "${(reverb * 100).toInt()}%",
                                size = if (isCompact) 36.dp else 46.dp
                            )
                        }

                        // 8 Drum Pads Grid (4x2)
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            val gap = if (isCompact) 4.dp else 6.dp
                            val cellHeight = (maxHeight - gap) / 2

                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(gap)
                            ) {
                                // Row 1 (Pads 1..4)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(cellHeight),
                                    horizontalArrangement = Arrangement.spacedBy(gap)
                                ) {
                                    drumPads.take(4).forEach { pad ->
                                        DrumPadCell(
                                            pad = pad,
                                            onPressed = { onPadPressed(pad.id) },
                                            onReleased = { onPadReleased(pad.id) },
                                            onLongPress = { onOpenAssigner(pad.id) },
                                            isCompact = isCompact,
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        )
                                    }
                                }

                                // Row 2 (Pads 5..8)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(cellHeight),
                                    horizontalArrangement = Arrangement.spacedBy(gap)
                                ) {
                                    drumPads.drop(4).take(4).forEach { pad ->
                                        DrumPadCell(
                                            pad = pad,
                                            onPressed = { onPadPressed(pad.id) },
                                            onReleased = { onPadReleased(pad.id) },
                                            onLongPress = { onOpenAssigner(pad.id) },
                                            isCompact = isCompact,
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                "bank" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(listOf(
                            "Kick Tight" to "Bank 0",
                            "Kick Boomy" to "Bank 1",
                            "Snare Acoustic" to "Bank 2",
                            "Snare Electro" to "Bank 3",
                            "Clap Layered" to "Bank 4",
                            "Tom Room" to "Bank 5",
                            "Ride Jazz" to "Bank 6"
                        )) { (name, meta) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x0AFFFFFF))
                                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0x2622D3EE)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "🎵", fontSize = 10.sp)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(text = meta, fontSize = 8.5.sp, color = TextDim2)
                                }
                            }
                        }
                    }
                }
                "files" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(listOf(
                            "kick_808_deep.wav" to "240 KB",
                            "snare_crisp.wav" to "180 KB",
                            "clap_vinyl.mp3" to "95 KB",
                            "hat_trap_closed.wav" to "60 KB",
                            "perc_conga_hit.wav" to "210 KB",
                            "crash_bright.mp3" to "340 KB"
                        )) { (file, size) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x0AFFFFFF))
                                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "📁", fontSize = 12.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = file, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(text = "$size · /DrumPad/", fontSize = 8.5.sp, color = TextDim2)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DrumPadCell(
    pad: DrumPadItem,
    onPressed: () -> Unit,
    onReleased: () -> Unit,
    onLongPress: () -> Unit,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isPressed = pad.isPressed

    val padBrush = if (isPressed) {
        Brush.linearGradient(
            listOf(Color(0x5922D3EE), Color(0x2622D3EE), Color(0xFF24242E))
        )
    } else {
        Brush.linearGradient(
            listOf(Color(0xFF363642), Color(0xFF24242E), Color(0xFF1B1B23))
        )
    }

    Box(
        modifier = modifier
            .shadow(if (isPressed) 12.dp else 3.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(padBrush)
            .border(
                1.dp,
                if (isPressed) NeonCyan else Color(0x14FFFFFF),
                RoundedCornerShape(10.dp)
            )
            .pointerInput(pad.id) {
                detectTapGestures(
                    onPress = {
                        onPressed()
                        tryAwaitRelease()
                        onReleased()
                    },
                    onLongPress = {
                        onLongPress()
                    }
                )
            }
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = pad.label,
                fontSize = if (isCompact) 11.sp else 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPressed) NeonCyanLight else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val subtitle = when (pad.soundType) {
                DrumSoundType.SAMPLE -> pad.sampleFileName.substringBeforeLast(".")
                DrumSoundType.SF2_NOTE -> "Note: ${pad.sf2Note}"
            }

            Text(
                text = subtitle,
                fontSize = if (isCompact) 7.5.sp else 8.5.sp,
                color = if (isPressed) NeonCyan else TextDim2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DrumSoundAssignerModal(
    pad: DrumPadItem,
    onAssignSample: (String) -> Unit,
    onAssignSf2Note: (String, Int) -> Unit,
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(if (pad.soundType == DrumSoundType.SF2_NOTE) "sf2" else "sample") }
    var selectedOctave by remember { mutableIntStateOf(pad.sf2NoteOctave) }
    var selectedKey by remember { mutableStateOf(pad.sf2NoteKey) }

    val keys = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val octaves = (1..8).toList()

    val sampleFiles = listOf(
        "kick_808_deep.wav",
        "snare_crisp.wav",
        "clap_vinyl.mp3",
        "hat_trap_closed.wav",
        "hat_open_bright.wav",
        "tom_floor_punch.wav",
        "tom_rack_hi.wav",
        "crash_bright.mp3",
        "perc_conga_hit.wav",
        "ride_jazz_clean.wav"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .heightIn(max = 340.dp)
                .shadow(24.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF241E38), Color(0xFF141220)))
                )
                .border(1.dp, NeonPurple, RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .padding(14.dp)
        ) {
            // Modal Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Assigner le son — ${pad.label}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x1AFFFFFF))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", fontSize = 11.sp, color = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub Tabs (SF2 Note C1..C8 / Sample File)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("sf2" to "Note Soundfont .sf2 (C1 — C8)", "sample" to "Fichier audio sample (.wav, .mp3)").forEach { (tId, tLabel) ->
                    val isSelected = selectedTab == tId
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Brush.verticalGradient(listOf(NeonPurpleLight, NeonPurple)) else Brush.linearGradient(listOf(Color(0x0DFFFFFF), Color(0x08FFFFFF))))
                            .clickable { selectedTab = tId }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tLabel,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else TextDim
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedTab == "sf2") {
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Text(text = "CHOISIR L'OCTAVE (C1 À C8)", fontSize = 9.5.sp, color = TextDim, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        octaves.forEach { oct ->
                            val isSel = selectedOctave == oct
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) NeonCyan else Color(0x14FFFFFF))
                                    .clickable { selectedOctave = oct }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "C$oct",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color(0xFF002B33) else TextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "CHOISIR LA NOTE", fontSize = 9.5.sp, color = TextDim, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        keys.forEach { k ->
                            val isSel = selectedKey == k
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) NeonPurpleLight else Color(0x14FFFFFF))
                                    .clickable { selectedKey = k }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = k,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else TextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Confirm SF2 Note
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.horizontalGradient(listOf(NeonCyan, NeonPurple)))
                            .clickable { onAssignSf2Note(selectedKey, selectedOctave) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Assigner Note $selectedKey$selectedOctave à ${pad.label}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(sampleFiles) { sample ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x0DFFFFFF))
                                .clickable { onAssignSample(sample) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = sample, fontSize = 11.sp, color = TextPrimary)
                            Text(text = "Choisir", fontSize = 10.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

