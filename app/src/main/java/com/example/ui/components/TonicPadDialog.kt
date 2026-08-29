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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var isSf2PickerOpen by remember { mutableStateOf(false) }

    var floatingOffsetX by remember { mutableFloatStateOf(60f) }
    var floatingOffsetY by remember { mutableFloatStateOf(40f) }
    var windowSizeDp by remember { mutableStateOf(440.dp) } // Square format

    if (isPinned) {
        // Floating resizable window (Square)
        Box(
            modifier = modifier
                .offset { IntOffset(floatingOffsetX.roundToInt(), floatingOffsetY.roundToInt()) }
                .size(windowSizeDp)
                .shadow(24.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF282238), Color(0xFF1B1626), Color(0xFF130F1C)))
                )
                .border(1.5.dp, NeonPurpleLight, RoundedCornerShape(18.dp))
                .testTag("floating_tonic_pad")
        ) {
            TonicPadContent(
                activeNotes = activeNotes,
                onNoteClick = onNoteClick,
                isMultiPadEnabled = isMultiPadEnabled,
                onToggleMultiPad = onToggleMultiPad,
                octaveRange = octaveRange,
                onOctaveMinus = onOctaveMinus,
                onOctavePlus = onOctavePlus,
                brightness = brightness,
                onBrightnessChange = onBrightnessChange,
                shimmer = shimmer,
                onShimmerChange = onShimmerChange,
                isPinned = isPinned,
                onTogglePin = onTogglePin,
                onClose = onClose,
                isSf2PickerOpen = isSf2PickerOpen,
                onToggleSf2Picker = { isSf2PickerOpen = it },
                soundfonts = soundfonts,
                onDragHeader = { dx, dy ->
                    floatingOffsetX += dx
                    floatingOffsetY += dy
                }
            )

            // Resize Handle
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(26.dp)
                    .clip(RoundedCornerShape(bottomEnd = 18.dp))
                    .background(Color(0x228B5CF6))
                    .pointerInput(density) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val dDp = with(density) { (dragAmount.x + dragAmount.y) / 2f }.toDp()
                            windowSizeDp = (windowSizeDp + dDp).coerceIn(320.dp, 600.dp)
                        }
                    }
                    .padding(4.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Text(text = "◢", fontSize = 11.sp, color = NeonPurpleLight, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        // Standard Square Modal Dialog
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
                        .size(460.dp) // Square form factor per user request
                        .shadow(24.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF282238), Color(0xFF1B1626), Color(0xFF130F1C)))
                        )
                        .border(1.2.dp, NeonPurpleLight, RoundedCornerShape(20.dp))
                        .clickable(enabled = false) {}
                        .padding(14.dp)
                        .testTag("dialog_tonic_pad")
                ) {
                    TonicPadContent(
                        activeNotes = activeNotes,
                        onNoteClick = onNoteClick,
                        isMultiPadEnabled = isMultiPadEnabled,
                        onToggleMultiPad = onToggleMultiPad,
                        octaveRange = octaveRange,
                        onOctaveMinus = onOctaveMinus,
                        onOctavePlus = onOctavePlus,
                        brightness = brightness,
                        onBrightnessChange = onBrightnessChange,
                        shimmer = shimmer,
                        onShimmerChange = onShimmerChange,
                        isPinned = isPinned,
                        onTogglePin = onTogglePin,
                        onClose = onClose,
                        isSf2PickerOpen = isSf2PickerOpen,
                        onToggleSf2Picker = { isSf2PickerOpen = it },
                        soundfonts = soundfonts,
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
    isSf2PickerOpen: Boolean,
    onToggleSf2Picker: (Boolean) -> Unit,
    soundfonts: List<StorageItem>,
    onDragHeader: ((Float, Float) -> Unit)?
) {
    val chromaticNotes = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

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
                if (isSf2PickerOpen) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x228B5CF6))
                            .border(1.dp, NeonPurpleLight, RoundedCornerShape(6.dp))
                            .clickable { onToggleSf2Picker(false) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = "← Retour", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonPurpleLight)
                    }
                }
                Text(
                    text = if (isSf2PickerOpen) "Choisir un SoundFont" else "🎵 Tonic Pad Drone & Ambience",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!isSf2PickerOpen) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x1A8B5CF6))
                            .border(1.dp, NeonPurpleLight, RoundedCornerShape(6.dp))
                            .clickable { onToggleSf2Picker(true) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = "📦 Banques SF2", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NeonPurpleLight)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isPinned) Color(0x338B5CF6) else Color(0x14FFFFFF))
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

        Spacer(modifier = Modifier.height(8.dp))

        if (isSf2PickerOpen) {
            // INNER SOUNDFONT PICKER
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "BANQUES SOUNDFONT /LiveKeys/SoundFonts",
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
                            .background(Color(0x08FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Déposez vos fichiers .sf2 dans /LiveKeys/SoundFonts",
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
                                    .clickable { onToggleSf2Picker(false) }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "📦", fontSize = 12.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = sf2.name, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text(text = sf2.formattedSize, fontSize = 8.sp, color = TextDim2)
                                }
                                Text(text = "Sélectionner", fontSize = 9.sp, color = NeonPurpleLight, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // Top Controls: Interactive Octave Stepper & Multi-Pad toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Interactive Octave Stepper
                Row(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x1AFFFFFF))
                        .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .fillMaxHeight()
                            .clickable { onOctaveMinus() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "−", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonPurpleLight)
                    }

                    Text(
                        text = "Octave: $octaveRange",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .fillMaxHeight()
                            .clickable { onOctavePlus() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "+", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonPurpleLight)
                    }
                }

                // Mode Multi Pad toggle (Single-touch on/off)
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isMultiPadEnabled) NeonPurple else Color(0x14FFFFFF))
                        .border(1.dp, if (isMultiPadEnabled) NeonPurpleLight else Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                        .clickable { onToggleMultiPad() }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isMultiPadEnabled) "Multi-Pad: ACTIF" else "Multi-Pad: OFF",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMultiPadEnabled) Color.White else TextDim
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 12 Chromatic Notes Grid (4x3 Square arrangement)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (row in 0 until 3) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (col in 0 until 4) {
                            val note = chromaticNotes[row * 4 + col]
                            val isActive = activeNotes.contains(note)

                            val padBg = if (isActive) {
                                Brush.verticalGradient(listOf(NeonPurpleLight, NeonPurple, NeonPurpleDark))
                            } else {
                                Brush.verticalGradient(listOf(Color(0xFF262035), Color(0xFF181422)))
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(padBg)
                                    .border(
                                        1.2.dp,
                                        if (isActive) NeonCyanLight else Color(0x268B5CF6),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onNoteClick(note) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = note,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isActive) Color.White else TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sliders: Luminosité & Shimmer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Luminosité", fontSize = 8.sp, color = TextDim)
                    Slider(
                        value = brightness,
                        onValueChange = onBrightnessChange,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonPurpleLight,
                            activeTrackColor = NeonPurple,
                            inactiveTrackColor = Color(0x1AFFFFFF)
                        )
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Shimmer Ambience", fontSize = 8.sp, color = TextDim)
                    Slider(
                        value = shimmer,
                        onValueChange = onShimmerChange,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = Color(0x1AFFFFFF)
                        )
                    )
                }
            }
        }
    }
}
