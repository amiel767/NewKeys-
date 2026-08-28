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

    // Floating window state (offsets & dimensions) - free 360 drag
    var floatingOffsetX by remember { mutableFloatStateOf(40f) }
    var floatingOffsetY by remember { mutableFloatStateOf(30f) }
    var windowWidthDp by remember { mutableStateOf(520.dp) }
    var windowHeightDp by remember { mutableStateOf(290.dp) }

    if (isPinned) {
        // FLOATING RESIZABLE WINDOW (Free movement anywhere on screen)
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
                    // Free dragging across screen
                    floatingOffsetX += dx
                    floatingOffsetY += dy
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
                            windowWidthDp = (windowWidthDp + dxDp).coerceIn(300.dp, 900.dp)
                            windowHeightDp = (windowHeightDp + dyDp).coerceIn(160.dp, 500.dp)
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
                        text = if (isUltraCompact) "Drum Pad" else "Drum Pad Matrix",
                        fontSize = if (isCompact) 12.sp else 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Pin Button (Floating toggle)
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
                        Text(text = "🥁", fontSize = if (isCompact) 10.sp else 11.sp)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Kit Soundfont", fontSize = 8.sp, color = TextDim2)
                        Text(
                            text = "Standard Studio Drum Kit",
                            fontSize = if (isCompact) 10.5.sp else 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Text(text = "›", fontSize = 14.sp, color = TextDim)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Sub-Tabs Header (Pads / Bank / Files)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("pads" to "Pads Matrix", "bank" to "Samples Preset", "files" to "Fichiers").forEach { (tabKey, tabLabel) ->
                    val isSelected = activeTab == tabKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) Brush.horizontalGradient(listOf(NeonCyanLight, NeonCyanDark)) else Brush.linearGradient(listOf(Color(0x0FFFFFFF), Color(0x08FFFFFF)))
                            )
                            .border(
                                1.dp,
                                if (isSelected) Color.Transparent else Color(0x1AFFFFFF),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onTabChange(tabKey) }
                            .padding(vertical = if (isCompact) 3.dp else 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabLabel,
                            fontSize = if (isCompact) 9.sp else 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF00232B) else TextDim
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Tab Content
            when (activeTab) {
                "pads" -> {
                    // Pads Grid (2 rows x 4 cols) + Volume/Reverb Sidebar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 8-Pad Dynamic Matrix (4x2)
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            val gap = if (isCompact) 4.dp else 6.dp
                            val rowHeight = (maxHeight - gap) / 2

                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(gap)
                            ) {
                                // Top row: Pads 1..4
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(rowHeight),
                                    horizontalArrangement = Arrangement.spacedBy(gap)
                                ) {
                                    drumPads.take(4).forEach { pad ->
                                        DrumPadCell(
                                            pad = pad,
                                            isCompact = isCompact,
                                            onPressed = { onPadPressed(pad.id) },
                                            onReleased = { onPadReleased(pad.id) },
                                            onLongPress = { onOpenAssigner(pad.id) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        )
                                    }
                                }

                                // Bottom row: Pads 5..8
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(rowHeight),
                                    horizontalArrangement = Arrangement.spacedBy(gap)
                                ) {
                                    drumPads.drop(4).take(4).forEach { pad ->
                                        DrumPadCell(
                                            pad = pad,
                                            isCompact = isCompact,
                                            onPressed = { onPadPressed(pad.id) },
                                            onReleased = { onPadReleased(pad.id) },
                                            onLongPress = { onOpenAssigner(pad.id) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        )
                                    }
                                }
                            }
                        }

                        // Knobs Sidebar
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
                                valueText = "${(volume * 100).toInt()}%",
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
                    }
                }
                "bank" -> {
                    // Sample Bank list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(drumPads) { pad ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x0AFFFFFF))
                                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp))
                                    .clickable { onOpenAssigner(pad.id) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Pad ${pad.id} : ${pad.label}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                val assignedText = if (pad.soundType == DrumSoundType.SAMPLE) pad.sampleFileName else "Note SF2: ${pad.sf2Note}"
                                Text(text = assignedText, fontSize = 9.5.sp, color = NeonCyan)
                            }
                        }
                    }
                }
                "files" -> {
                    // Explorer / Samples list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val samples = listOf(
                            "kick_808_deep.wav",
                            "snare_crisp.wav",
                            "hat_trap_closed.wav",
                            "hat_open_bright.wav",
                            "clap_vinyl.mp3",
                            "tom_floor_punch.wav",
                            "crash_bright.mp3"
                        )
                        items(samples) { sample ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x0AFFFFFF))
                                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "🎵 $sample", fontSize = 11.sp, color = TextPrimary)
                                Text(text = "44.1 kHz", fontSize = 9.sp, color = TextDim2)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrumPadCell(
    pad: DrumPadItem,
    isCompact: Boolean,
    onPressed: () -> Unit,
    onReleased: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPressed = pad.isPressed
    val padColors = when (pad.id) {
        1 -> listOf(Color(0xFFFF5C8A), Color(0xFFE11D48))
        2 -> listOf(Color(0xFFFF6FAE), Color(0xFFC026D3))
        3 -> listOf(Color(0xFF00E5FF), Color(0xFF00B0FF))
        4 -> listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
        5 -> listOf(Color(0xFFF472B6), Color(0xFFDB2777))
        6 -> listOf(Color(0xFFA78BFA), Color(0xFF7C3AED))
        7 -> listOf(Color(0xFFC084FC), Color(0xFF9333EA))
        else -> listOf(Color(0xFFE879F9), Color(0xFFC026D3))
    }

    val cellBrush = if (isPressed) {
        Brush.verticalGradient(listOf(Color.White, padColors.first()))
    } else {
        Brush.verticalGradient(padColors)
    }

    Box(
        modifier = modifier
            .shadow(if (isPressed) 14.dp else 3.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(cellBrush)
            .border(
                1.5.dp,
                if (isPressed) Color.White else Color(0x33FFFFFF),
                RoundedCornerShape(10.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPressed()
                        tryAwaitRelease()
                        onReleased()
                    }
                )
            }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = pad.label,
                fontSize = if (isCompact) 10.sp else 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isPressed) Color(0xFF1E1E2E) else Color.White,
                textAlign = TextAlign.Center
            )

            val subText = if (pad.soundType == DrumSoundType.SAMPLE) {
                pad.sampleFileName.substringBeforeLast(".")
            } else {
                pad.sf2Note
            }

            Text(
                text = subText,
                fontSize = if (isCompact) 7.5.sp else 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isPressed) Color(0xFF333344) else Color(0xCCFFFFFF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(360.dp)
                .shadow(20.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF262635), Color(0xFF171720))))
                .border(1.dp, NeonCyan, RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Assigner Pad ${pad.id} (${pad.label})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = "✕", fontSize = 12.sp, color = TextDim, modifier = Modifier.clickable { onClose() })
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(text = "ÉCHANTILLONS DISPONIBLES :", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextDim)
                Spacer(modifier = Modifier.height(6.dp))

                val sampleList = listOf(
                    "kick_808_deep.wav",
                    "snare_crisp.wav",
                    "hat_trap_closed.wav",
                    "hat_open_bright.wav",
                    "clap_vinyl.mp3",
                    "tom_floor_punch.wav",
                    "crash_bright.mp3"
                )

                LazyColumn(modifier = Modifier.height(160.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(sampleList) { sample ->
                        val isSelected = pad.sampleFileName == sample
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Color(0x3322D3EE) else Color(0x0DFFFFFF))
                                .border(1.dp, if (isSelected) NeonCyan else Color(0x14FFFFFF), RoundedCornerShape(6.dp))
                                .clickable {
                                    onAssignSample(sample)
                                    onClose()
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(text = sample, fontSize = 11.sp, color = if (isSelected) NeonCyan else TextPrimary)
                        }
                    }
                }
            }
        }
    }
}
