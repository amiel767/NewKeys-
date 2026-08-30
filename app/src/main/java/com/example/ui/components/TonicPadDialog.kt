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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SoundfontPreset
import com.example.model.StorageItem
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun TonicPadDialog(
    activeNotes: Set<String>,
    onNoteClick: (String) -> Unit,
    isMultiPadEnabled: Boolean,
    onToggleMultiPad: () -> Unit,
    octaveRange: String,
    onOctaveMinus: () -> Unit,
    onOctavePlus: () -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    shimmer: Float,
    onShimmerChange: (Float) -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onClose: () -> Unit,
    soundfonts: List<StorageItem> = emptyList(),
    currentLoadedSf2Name: String = "Worship Ambient Pad.sf2",
    loadedSf2Presets: List<SoundfontPreset> = emptyList(),
    onSelectPreset: (SoundfontPreset) -> Unit = {},
    onSelectSf2File: (StorageItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var isSoundPickerOpen by remember { mutableStateOf(false) }

    // Position and size state maintained consistently
    var floatingOffsetX by remember { mutableFloatStateOf(100f) }
    var floatingOffsetY by remember { mutableFloatStateOf(60f) }
    var windowSizeDp by remember { mutableStateOf(440.dp) }

    // Format single octave display (e.g. "C4" if range is "C3 — C4" or "C4")
    val singleOctaveText = remember(octaveRange) {
        val match = Regex("C[0-8]").findAll(octaveRange).map { it.value }.toList()
        if (match.isNotEmpty()) match.last() else "C4"
    }

    if (isPinned) {
        // Pinned Mobile & Resizable Window (stays exactly in place and size)
        Box(
            modifier = modifier
                .offset { IntOffset(floatingOffsetX.roundToInt(), floatingOffsetY.roundToInt()) }
                .size(windowSizeDp)
                .shadow(24.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF241C30), Color(0xFF161220), Color(0xFF0F0C16)))
                )
                .border(1.5.dp, Color(0xFF8B5CF6), RoundedCornerShape(18.dp))
                .padding(10.dp)
                .testTag("floating_tonic_pad")
        ) {
            TonicPadContent(
                activeNotes = activeNotes,
                onNoteClick = onNoteClick,
                isMultiPadEnabled = isMultiPadEnabled,
                onToggleMultiPad = onToggleMultiPad,
                singleOctaveText = singleOctaveText,
                onOctaveMinus = onOctaveMinus,
                onOctavePlus = onOctavePlus,
                brightness = brightness,
                onBrightnessChange = onBrightnessChange,
                shimmer = shimmer,
                onShimmerChange = onShimmerChange,
                isPinned = isPinned,
                onTogglePin = onTogglePin,
                onClose = onClose,
                isSoundPickerOpen = isSoundPickerOpen,
                onToggleSoundPicker = { isSoundPickerOpen = it },
                soundfonts = soundfonts,
                currentLoadedSf2Name = currentLoadedSf2Name,
                loadedSf2Presets = loadedSf2Presets,
                onSelectPreset = onSelectPreset,
                onSelectSf2File = onSelectSf2File,
                onDragHeader = { dx, dy ->
                    floatingOffsetX += dx
                    floatingOffsetY += dy
                }
            )

            // Discrete Resize Arrow (No background box around the arrow)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .pointerInput(density) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val dDp = with(density) { (dragAmount.x + dragAmount.y) / 2f }.toDp()
                            windowSizeDp = (windowSizeDp + dDp).coerceIn(300.dp, 600.dp)
                        }
                    }
                    .padding(end = 4.dp, bottom = 4.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Text(text = "◢", fontSize = 12.sp, color = NeonPurpleLight, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        // Standard Centered Modal (Cannot be moved until pinned)
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
                        .size(440.dp)
                        .shadow(24.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF241C30), Color(0xFF161220), Color(0xFF0F0C16)))
                        )
                        .border(1.2.dp, Color(0xFF8B5CF6), RoundedCornerShape(20.dp))
                        .clickable(enabled = false) {}
                        .padding(12.dp)
                        .testTag("dialog_tonic_pad")
                ) {
                    TonicPadContent(
                        activeNotes = activeNotes,
                        onNoteClick = onNoteClick,
                        isMultiPadEnabled = isMultiPadEnabled,
                        onToggleMultiPad = onToggleMultiPad,
                        singleOctaveText = singleOctaveText,
                        onOctaveMinus = onOctaveMinus,
                        onOctavePlus = onOctavePlus,
                        brightness = brightness,
                        onBrightnessChange = onBrightnessChange,
                        shimmer = shimmer,
                        onShimmerChange = onShimmerChange,
                        isPinned = isPinned,
                        onTogglePin = onTogglePin,
                        onClose = onClose,
                        isSoundPickerOpen = isSoundPickerOpen,
                        onToggleSoundPicker = { isSoundPickerOpen = it },
                        soundfonts = soundfonts,
                        currentLoadedSf2Name = currentLoadedSf2Name,
                        loadedSf2Presets = loadedSf2Presets,
                        onSelectPreset = onSelectPreset,
                        onSelectSf2File = onSelectSf2File,
                        onDragHeader = null
                    )
                }
            }
        }
    }
}

@Composable
private fun TonicPadContent(
    activeNotes: Set<String>,
    onNoteClick: (String) -> Unit,
    isMultiPadEnabled: Boolean,
    onToggleMultiPad: () -> Unit,
    singleOctaveText: String,
    onOctaveMinus: () -> Unit,
    onOctavePlus: () -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    shimmer: Float,
    onShimmerChange: (Float) -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onClose: () -> Unit,
    isSoundPickerOpen: Boolean,
    onToggleSoundPicker: (Boolean) -> Unit,
    soundfonts: List<StorageItem>,
    currentLoadedSf2Name: String,
    loadedSf2Presets: List<SoundfontPreset>,
    onSelectPreset: (SoundfontPreset) -> Unit,
    onSelectSf2File: (StorageItem) -> Unit,
    onDragHeader: ((Float, Float) -> Unit)?
) {
    val chromaticNotes = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    var soundTab by remember { mutableStateOf("presets") } // "presets" (SF2 chargé) or "files" (Dossier /Soundfonts)

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
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
                if (isSoundPickerOpen) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x228B5CF6))
                            .border(1.dp, NeonPurpleLight, RoundedCornerShape(6.dp))
                            .clickable { onToggleSoundPicker(false) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(text = "← Retour", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonPurpleLight)
                    }
                }
                Text(
                    text = if (isSoundPickerOpen) "Sound: Sélectionner" else "🎵 Tonic Pad Drone & Ambience",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (!isSoundPickerOpen) {
                    // "Sound" Button (Renamed from Banque SF2 per Page 1)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x228B5CF6))
                            .border(1.dp, NeonPurpleLight, RoundedCornerShape(6.dp))
                            .clickable { onToggleSoundPicker(true) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = "🔊 Sound", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = NeonPurpleLight)
                    }
                }

                // Pin Button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isPinned) Color(0x448B5CF6) else Color(0x14FFFFFF))
                        .border(1.dp, if (isPinned) NeonPurpleLight else Color.Transparent, RoundedCornerShape(6.dp))
                        .clickable { onTogglePin() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (isPinned) "📌" else "📍", fontSize = 11.sp)
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

        if (isSoundPickerOpen) {
            // SOUND PICKER WITH 2 TABS (1. .SF2 CHARGÉ INSTRUMENTS, 2. DOSSIER /SOUNDFONTS)
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Tab 1: .sf2 Chargé
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (soundTab == "presets") Color(0xFF8B5CF6) else Color.Transparent)
                            .clickable { soundTab = "presets" }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "1. .sf2 Chargé",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (soundTab == "presets") Color.White else TextDim
                        )
                    }

                    // Tab 2: Dossier /Soundfonts
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (soundTab == "files") Color(0xFF8B5CF6) else Color.Transparent)
                            .clickable { soundTab = "files" }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "2. Dossier /Soundfonts",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (soundTab == "files") Color.White else TextDim
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (soundTab == "presets") {
                    // Instruments list in current .sf2
                    val presetsList = if (loadedSf2Presets.isNotEmpty()) loadedSf2Presets else listOf(
                        SoundfontPreset(0, "Worship Warm Pad", 0),
                        SoundfontPreset(1, "Deep Shimmer Drone", 0),
                        SoundfontPreset(2, "Celestial Choir Pad", 0),
                        SoundfontPreset(3, "Soft Analog Strings", 0),
                        SoundfontPreset(4, "Glass Bell Ambience", 0)
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
                                    .clickable {
                                        onSelectPreset(preset)
                                        onToggleSoundPicker(false)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(text = preset.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text(text = "Bank: ${preset.bankNumber} · Preset: ${preset.id}", fontSize = 8.5.sp, color = TextDim2)
                                }
                                Text(text = "Charger", fontSize = 9.sp, color = NeonPurpleLight, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Files in /LiveKeys/SoundFonts
                    if (soundfonts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x08FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Aucun .sf2 dans /LiveKeys/SoundFonts\nDéposez vos fichiers SoundFont pour les charger",
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
                            items(soundfonts) { sf2 ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x0EFFFFFF))
                                        .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(8.dp))
                                        .clickable {
                                            onSelectSf2File(sf2)
                                            onToggleSoundPicker(false)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = sf2.name, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Text(text = sf2.formattedSize, fontSize = 8.sp, color = TextDim2)
                                    }
                                    Text(text = "Ouvrir", fontSize = 9.sp, color = NeonPurpleLight, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // TOP CONTROLS: SINGLE OCTAVE DISPLAY (C4) WITH - / + BUTTONS & MULTI-PAD TOGGLE
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Interactive Octave Stepper showing ONLY single octave "C4"
                Row(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0x1AFFFFFF))
                        .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(7.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .fillMaxHeight()
                            .clickable { onOctaveMinus() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "−", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeonPurpleLight)
                    }

                    Text(
                        text = "Octave: $singleOctaveText",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .fillMaxHeight()
                            .clickable { onOctavePlus() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeonPurpleLight)
                    }
                }

                // Mode Multi-Pad Toggle
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isMultiPadEnabled) Color(0xFF8B5CF6) else Color(0x14FFFFFF))
                        .border(1.dp, if (isMultiPadEnabled) NeonPurpleLight else Color(0x22FFFFFF), RoundedCornerShape(7.dp))
                        .clickable { onToggleMultiPad() }
                        .padding(horizontal = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isMultiPadEnabled) "Multi-Pad: ON" else "Multi-Pad: OFF",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMultiPadEnabled) Color.White else TextDim
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 12 CHROMATIC NOTES GRID (4x3 Arrangement)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                for (row in 0 until 3) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        for (col in 0 until 4) {
                            val note = chromaticNotes[row * 4 + col]
                            val isActive = activeNotes.contains(note)

                            val padBg = if (isActive) {
                                Brush.verticalGradient(listOf(NeonPurpleLight, Color(0xFF7C3AED), Color(0xFF4C1D95)))
                            } else {
                                Brush.verticalGradient(listOf(Color(0xFF282038), Color(0xFF1A1426)))
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(padBg)
                                    .border(
                                        1.2.dp,
                                        if (isActive) Color(0xFFC4B5FD) else Color(0x268B5CF6),
                                        RoundedCornerShape(9.dp)
                                    )
                                    .clickable { onNoteClick(note) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = note,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isActive) Color.White else TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3D SEMI-REALISTIC KNOBS WITH FLUID GLOWING LED (Brightness & Shimmer)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x0CFFFFFF))
                    .padding(vertical = 4.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Led3DKnob(
                    value = brightness,
                    onValueChange = onBrightnessChange,
                    label = "Brightness",
                    valueText = "${(brightness * 100).toInt()}%",
                    size = 46.dp,
                    baseColor = Color(0xFF8B5CF6)
                )

                Led3DKnob(
                    value = shimmer,
                    onValueChange = onShimmerChange,
                    label = "Shimmer",
                    valueText = "${(shimmer * 100).toInt()}%",
                    size = 46.dp,
                    baseColor = NeonCyan
                )
            }
        }
    }
}
