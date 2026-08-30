package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*

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
    onUpdatePadCustomization: (padId: Int, label: String, style: DrumPadStyle) -> Unit,
    onAssignPadSample: (padId: Int, sample: StorageItem) -> Unit,
    onAssignPadNote: (padId: Int, noteStr: String, oct: Int, key: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingPad by remember { mutableStateOf<DrumPadItem?>(null) }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220)),
        exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x88000000))
                .testTag("drum_pad_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 440.dp, max = 560.dp)
                    .fillMaxHeight(0.88f)
                    .shadow(32.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF141722))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                    .padding(14.dp)
            ) {
                when {
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
                        MainDrumPadContent(
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
                            onPadPressed = onPadPressed,
                            onPadReleased = onPadReleased,
                            onLongPressPad = { pad -> editingPad = pad },
                            audioFiles = audioFiles,
                            onAssignSample = { padId, file -> onAssignPadSample(padId, file) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainDrumPadContent(
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
    onPadPressed: (Int) -> Unit,
    onPadReleased: (Int) -> Unit,
    onLongPressPad: (DrumPadItem) -> Unit,
    audioFiles: List<StorageItem>,
    onAssignSample: (Int, StorageItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEC4899).copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🥁", fontSize = 16.sp)
                }
                Column {
                    Text(
                        text = "Drum Pad",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Appui long = Modifier couleur & style",
                        fontSize = 9.sp,
                        color = TextDim2
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Pin button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isPinned) Color(0x3322D3EE) else Color(0x14FFFFFF))
                        .border(1.dp, if (isPinned) NeonCyan else Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                        .clickable { onTogglePin() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isPinned) "📌 Épinglé" else "📌 Épingler",
                        fontSize = 10.sp,
                        color = if (isPinned) NeonCyan else TextDim
                    )
                }

                // Close button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x14FFFFFF))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", fontSize = 11.sp, color = TextPrimary)
                }
            }
        }

        // Tabs (Pads, Samples)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E212E))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("pads" to "Grille 8 Pads", "files" to "Échantillons / Samples").forEach { (tabId, label) ->
                val isSel = (tabId == activeTab)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) NeonCyan else Color.Transparent)
                        .clickable { onTabChange(tabId) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) Color(0xFF002233) else TextDim
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (activeTab == "pads") {
            // Square 8-Pad Grid (4 cols x 2 rows)
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(drumPads) { pad ->
                    SquareDrumPadCell(
                        pad = pad,
                        onPress = { onPadPressed(pad.id) },
                        onRelease = { onPadReleased(pad.id) },
                        onLongPress = { onLongPressPad(pad) }
                    )
                }
            }

            // Quick Volume Slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Vol Drum", fontSize = 10.sp, color = TextDim)
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan),
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            // Audio samples file list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (audioFiles.isEmpty()) {
                    item {
                        Text(
                            text = "Aucun échantillon audio trouvé dans /LiveKeys/DrumPad",
                            fontSize = 11.sp,
                            color = TextDim,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(audioFiles) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E212E))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = file.name, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(text = file.formattedSize, fontSize = 9.sp, color = TextDim2)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                (1..4).forEach { pId ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0x2222D3EE))
                                            .clickable { onAssignSample(pId, file) }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = "P$pId", fontSize = 9.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
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
private fun SquareDrumPadCell(
    pad: DrumPadItem,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onLongPress: () -> Unit
) {
    val style = pad.colorStyle

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (pad.isPressed) {
                    Brush.verticalGradient(listOf(style.primaryColor, style.secondaryColor))
                } else {
                    Brush.verticalGradient(
                        listOf(
                            style.primaryColor.copy(alpha = 0.22f),
                            style.secondaryColor.copy(alpha = 0.12f)
                        )
                    )
                }
            )
            .border(
                2.dp,
                if (pad.isPressed) Color.White else style.primaryColor.copy(alpha = 0.65f),
                RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onClick = {
                    onPress()
                    onRelease()
                },
                onLongClick = onLongPress
            )
            .padding(8.dp),
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
            Text(
                text = pad.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = if (pad.soundType == DrumSoundType.SF2_NOTE) pad.sf2Note else pad.sampleFileName.take(6),
                fontSize = 8.sp,
                color = TextDim
            )
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
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x14FFFFFF))
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(text = "← Annuler", fontSize = 10.sp, color = NeonCyan)
            }

            Text(
                text = "Modifier Pad ${pad.id}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeonCyan)
                    .clickable { onSave(labelText, selectedStyle) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = "Enregistrer", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF002233))
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Rename Input
            item {
                Text(text = "NOM DU PAD", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it.take(12) },
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
                Text(text = "SOURCE SONORE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E212E))
                            .clickable { onAssignSoundfont() }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🎹 Note SoundFont (SF2)", fontSize = 10.sp, color = NeonCyan)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E212E))
                            .clickable { onAssignSample() }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🎵 Échantillon WAV/MP3", fontSize = 10.sp, color = NeonMagenta)
                    }
                }
            }

            // 3. Category selector (GRADIENT, LED, NEON, MATERIAL YOU)
            item {
                Text(text = "PALETTE DE COULEURS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DrumPadCategory.values().forEach { cat ->
                        val isSel = (cat == selectedCategory)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) NeonCyan else Color(0x14FFFFFF))
                                .clickable { selectedCategory = cat }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat.title,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color(0xFF002233) else TextPrimary
                            )
                        }
                    }
                }
            }

            // 4. Colors inside the selected category
            item {
                val stylesInCat = DrumPadStyle.values().filter { it.category == selectedCategory }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    stylesInCat.forEach { st ->
                        val isSel = (st == selectedStyle)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) Color(0xFF2A2E40) else Color(0xFF1E212E))
                                .border(1.5.dp, if (isSel) NeonCyan else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { selectedStyle = st }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Brush.linearGradient(listOf(st.primaryColor, st.secondaryColor)))
                            )
                            Text(
                                text = st.displayName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSel) {
                                Text(text = "✓", fontSize = 12.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                            }
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
    var selectedOct by remember { mutableStateOf(1) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x14FFFFFF))
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(text = "← Retour", fontSize = 10.sp, color = NeonCyan)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Assigner Note SoundFont", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Text(text = "CHOISIR LA NOTE ET L'OCTAVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
        Spacer(modifier = Modifier.height(8.dp))

        // Note Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B").forEach { k ->
                val isSel = (k == selectedKey)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSel) NeonCyan else Color(0x14FFFFFF))
                        .clickable { selectedKey = k }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = k,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSel) Color(0xFF002233) else TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Octave Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (0..6).forEach { oct ->
                val isSel = (oct == selectedOct)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) Color(0xFFEC4899) else Color(0x14FFFFFF))
                        .clickable { selectedOct = oct }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Oct $oct",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSel) Color.White else TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { onSelectNote(selectedKey, selectedOct) },
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Assigner $selectedKey$selectedOct au Pad", color = Color(0xFF002233), fontWeight = FontWeight.Bold)
        }
    }
}
