package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.example.model.StorageItem
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
    subView: String,
    onSetSubView: (String) -> Unit,
    soundfonts: List<StorageItem>,
    audioFiles: List<StorageItem>,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onClose: () -> Unit,
    onPadPressed: (Int) -> Unit,
    onPadReleased: (Int) -> Unit,
    onAssignPadSample: (Int, StorageItem) -> Unit,
    onAssignPadNote: (Int, String, Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    var floatingOffsetX by remember { mutableFloatStateOf(40f) }
    var floatingOffsetY by remember { mutableFloatStateOf(30f) }
    var windowWidthDp by remember { mutableStateOf(520.dp) }
    var windowHeightDp by remember { mutableStateOf(310.dp) }

    var selectedSampleForAssign by remember { mutableStateOf<StorageItem?>(null) }
    var selectedNoteForAssign by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var isAssignModalOpen by remember { mutableStateOf(false) }

    if (isPinned) {
        // Floating resizable window
        Box(
            modifier = modifier
                .offset { IntOffset(floatingOffsetX.roundToInt(), floatingOffsetY.roundToInt()) }
                .size(windowWidthDp, windowHeightDp)
                .shadow(24.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF282838), Color(0xFF1B1B24), Color(0xFF14141C)))
                )
                .border(1.5.dp, NeonCyan, RoundedCornerShape(16.dp))
                .testTag("floating_drum_pad")
        ) {
            DrumPadContent(
                drumPads = drumPads,
                volume = volume,
                reverb = reverb,
                activeTab = activeTab,
                onTabChange = onTabChange,
                subView = subView,
                onSetSubView = onSetSubView,
                soundfonts = soundfonts,
                audioFiles = audioFiles,
                isPinned = isPinned,
                onTogglePin = onTogglePin,
                onClose = onClose,
                onPadPressed = onPadPressed,
                onPadReleased = onPadReleased,
                onSelectSampleForAssign = { sample ->
                    selectedSampleForAssign = sample
                    selectedNoteForAssign = null
                    isAssignModalOpen = true
                },
                onSelectNoteForAssign = { note, oct ->
                    selectedNoteForAssign = note to oct
                    selectedSampleForAssign = null
                    isAssignModalOpen = true
                },
                onDragHeader = { dx, dy ->
                    floatingOffsetX += dx
                    floatingOffsetY += dy
                }
            )

            // Resize Handle
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
                            windowWidthDp = (windowWidthDp + dxDp).coerceIn(320.dp, 800.dp)
                            windowHeightDp = (windowHeightDp + dyDp).coerceIn(200.dp, 500.dp)
                        }
                    }
                    .padding(4.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Text(text = "◢", fontSize = 12.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        // Standard Modal Dialog with smooth entrance animation
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0x660A0A0E))
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ),
                exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.95f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .fillMaxHeight(0.90f)
                        .shadow(24.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF2A2A38), Color(0xFF1B1B24), Color(0xFF17171F)))
                        )
                        .border(1.2.dp, NeonCyan, RoundedCornerShape(20.dp))
                        .clickable(enabled = false) {}
                        .padding(14.dp)
                        .testTag("dialog_drum_pad")
                ) {
                    DrumPadContent(
                        drumPads = drumPads,
                        volume = volume,
                        reverb = reverb,
                        activeTab = activeTab,
                        onTabChange = onTabChange,
                        subView = subView,
                        onSetSubView = onSetSubView,
                        soundfonts = soundfonts,
                        audioFiles = audioFiles,
                        isPinned = isPinned,
                        onTogglePin = onTogglePin,
                        onClose = onClose,
                        onPadPressed = onPadPressed,
                        onPadReleased = onPadReleased,
                        onSelectSampleForAssign = { sample ->
                            selectedSampleForAssign = sample
                            selectedNoteForAssign = null
                            isAssignModalOpen = true
                        },
                        onSelectNoteForAssign = { note, oct ->
                            selectedNoteForAssign = note to oct
                            selectedSampleForAssign = null
                            isAssignModalOpen = true
                        }
                    )
                }
            }
        }
    }

    // Modal to choose which Pad (1..8) to assign the selected sound to
    if (isAssignModalOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x80000000))
                .clickable { isAssignModalOpen = false },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(340.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1E28))
                    .border(1.dp, NeonCyan, RoundedCornerShape(16.dp))
                    .clickable(enabled = false) {}
                    .padding(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ASSIGNER AU PAD",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val targetName = selectedSampleForAssign?.name ?: selectedNoteForAssign?.let { "${it.first}${it.second}" } ?: ""
                    Text(
                        text = "Son : $targetName",
                        fontSize = 10.sp,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4x2 Pad selection grid
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (row in 0 until 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (col in 0 until 4) {
                                    val padId = row * 4 + col + 1
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x2222D3EE))
                                            .border(1.dp, Color(0x6622D3EE), RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedSampleForAssign?.let { onAssignPadSample(padId, it) }
                                                selectedNoteForAssign?.let { (k, o) -> onAssignPadNote(padId, "$k$o", o, k) }
                                                isAssignModalOpen = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Pad $padId",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x14FFFFFF))
                            .clickable { isAssignModalOpen = false }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Annuler", fontSize = 11.sp, color = TextDim)
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
    reverb: Float,
    activeTab: String,
    onTabChange: (String) -> Unit,
    subView: String,
    onSetSubView: (String) -> Unit,
    soundfonts: List<StorageItem>,
    audioFiles: List<StorageItem>,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onClose: () -> Unit,
    onPadPressed: (Int) -> Unit,
    onPadReleased: (Int) -> Unit,
    onSelectSampleForAssign: (StorageItem) -> Unit,
    onSelectNoteForAssign: (String, Int) -> Unit,
    onDragHeader: (Float, Float) -> Unit = { _, _ -> }
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(isPinned) {
                    if (isPinned) {
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
                if (subView == "sf2_picker") {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x2222D3EE))
                            .border(1.dp, NeonCyan, RoundedCornerShape(6.dp))
                            .clickable { onSetSubView("main") }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = "← Retour", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                    }
                }

                Text(
                    text = if (subView == "sf2_picker") "Choisir un SoundFont (.sf2)" else "🥁 Drum Pad Synth & Sampler",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (subView == "main") {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x1A8B5CF6))
                            .border(1.dp, NeonPurpleLight, RoundedCornerShape(6.dp))
                            .clickable { onSetSubView("sf2_picker") }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = "📦 Banques SF2", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = NeonPurpleLight)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isPinned) Color(0x3322D3EE) else Color(0x14FFFFFF))
                        .clickable { onTogglePin() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (isPinned) "📌" else "📍", fontSize = 11.sp)
                }

                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x14FFFFFF))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", fontSize = 12.sp, color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (subView == "sf2_picker") {
            // INNER SOUNDFONT PICKER (Does not exit popup!)
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "SOUNDFONTS DÉTECTÉS DANS LE STOCKAGE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDim
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (soundfonts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x08FFFFFF))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aucun fichier .sf2 trouvé.\nPlacez vos SoundFonts dans /LiveKeys/SoundFonts",
                            fontSize = 10.sp,
                            color = TextDim,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(soundfonts) { sf2 ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x0AFFFFFF))
                                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp))
                                    .clickable { onSetSubView("main") }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "📦", fontSize = 12.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sf2.name,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = sf2.formattedSize,
                                        fontSize = 8.sp,
                                        color = TextDim2
                                    )
                                }
                                Text(text = "Charger", fontSize = 9.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // MAIN VIEW WITH TABS: PADS, FICHIERS, NOTES SF2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("pads" to "8 Pads", "files" to "Fichiers Audio", "sf2_notes" to "Notes SoundFont").forEach { (tabKey, tabLabel) ->
                    val isSel = activeTab == tabKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) NeonCyan else Color(0x0DFFFFFF))
                            .clickable { onTabChange(tabKey) }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color(0xFF002933) else TextDim
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (activeTab) {
                "pads" -> {
                    // 2x4 Pads Grid
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (row in 0 until 2) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (col in 0 until 4) {
                                    val index = row * 4 + col
                                    val pad = drumPads.getOrNull(index) ?: DrumPadItem(index + 1, "Pad ${index + 1}")
                                    val isPressed = pad.isPressed

                                    val padBrush = if (isPressed) {
                                        Brush.verticalGradient(listOf(NeonPinkLight, NeonPink))
                                    } else {
                                        Brush.verticalGradient(listOf(Color(0xFF262636), Color(0xFF161622)))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(padBrush)
                                            .border(
                                                1.2.dp,
                                                if (isPressed) NeonCyan else Color(0x2EFFFFFF),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .pointerInput(pad.id) {
                                                detectTapGestures(
                                                    onPress = {
                                                        onPadPressed(pad.id)
                                                        tryAwaitRelease()
                                                        onPadReleased(pad.id)
                                                    }
                                                )
                                            }
                                            .padding(6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = pad.label,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isPressed) Color.White else TextPrimary
                                            )
                                            Text(
                                                text = if (pad.soundType == DrumSoundType.SAMPLE) pad.sampleFileName else pad.sf2Note,
                                                fontSize = 8.sp,
                                                color = if (isPressed) Color.White.copy(alpha = 0.8f) else TextDim2,
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

                "files" -> {
                    // Files tab: short tap = play sample; long press = assign to Pad
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "TOUCHER = ÉCOUTER · APPUI LONG = ASSIGNER À UN PAD",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        if (audioFiles.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x08FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "Placez des fichiers audio dans /LiveKeys/Loops", fontSize = 9.sp, color = TextDim)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                items(audioFiles) { file ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0x0AFFFFFF))
                                            .pointerInput(file.path) {
                                                detectTapGestures(
                                                    onTap = { /* Play sample preview */ },
                                                    onLongPress = { onSelectSampleForAssign(file) }
                                                )
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(text = "🎵", fontSize = 10.sp)
                                        Text(
                                            text = file.name,
                                            fontSize = 9.5.sp,
                                            color = TextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(text = "Appui long ↗", fontSize = 8.sp, color = NeonCyan)
                                    }
                                }
                            }
                        }
                    }
                }

                "sf2_notes" -> {
                    // Soundfont notes: short tap = preview; long press = assign to Pad
                    val notesList = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
                    val octaves = listOf(1, 2, 3, 4)

                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "TOUCHER = ÉCOUTER · APPUI LONG = ASSIGNER",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            items(octaves) { oct ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    notesList.forEach { note ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(28.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (note.contains("#")) Color(0xFF1E2235) else Color(0xFF2E344E))
                                                .pointerInput("$note$oct") {
                                                    detectTapGestures(
                                                        onTap = { /* preview note */ },
                                                        onLongPress = { onSelectNoteForAssign(note, oct) }
                                                    )
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$note$oct",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
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
}
