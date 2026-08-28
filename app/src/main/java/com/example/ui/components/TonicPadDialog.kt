package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun TonicPadDialog(
    activeNotes: Set<String>,
    onNoteClick: (String) -> Unit,
    isMultiPadEnabled: Boolean,
    onToggleMultiPad: () -> Unit,
    octaveRange: String,
    onCycleOctave: () -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    shimmer: Float,
    onShimmerChange: (Float) -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onClose: () -> Unit,
    onOpenSf2Picker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // Floating window state (offsets & dimensions) - free 360 drag
    var floatingOffsetX by remember { mutableFloatStateOf(60f) }
    var floatingOffsetY by remember { mutableFloatStateOf(40f) }
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
                .testTag("floating_tonic_pad")
        ) {
            TonicPadContent(
                activeNotes = activeNotes,
                onNoteClick = onNoteClick,
                isMultiPadEnabled = isMultiPadEnabled,
                onToggleMultiPad = onToggleMultiPad,
                octaveRange = octaveRange,
                onCycleOctave = onCycleOctave,
                brightness = brightness,
                onBrightnessChange = onBrightnessChange,
                shimmer = shimmer,
                onShimmerChange = onShimmerChange,
                isPinned = isPinned,
                onTogglePin = onTogglePin,
                onClose = onClose,
                onOpenSf2Picker = onOpenSf2Picker,
                onDragHeader = { dx, dy ->
                    // Free dragging anywhere on the screen canvas
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
                    .testTag("dialog_tonic_pad")
            ) {
                TonicPadContent(
                    activeNotes = activeNotes,
                    onNoteClick = onNoteClick,
                    isMultiPadEnabled = isMultiPadEnabled,
                    onToggleMultiPad = onToggleMultiPad,
                    octaveRange = octaveRange,
                    onCycleOctave = onCycleOctave,
                    brightness = brightness,
                    onBrightnessChange = onBrightnessChange,
                    shimmer = shimmer,
                    onShimmerChange = onShimmerChange,
                    isPinned = isPinned,
                    onTogglePin = onTogglePin,
                    onClose = onClose,
                    onOpenSf2Picker = onOpenSf2Picker,
                    onDragHeader = null
                )
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
    onCycleOctave: () -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    shimmer: Float,
    onShimmerChange: (Float) -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onClose: () -> Unit,
    onOpenSf2Picker: () -> Unit,
    onDragHeader: ((Float, Float) -> Unit)?
) {
    val chromaticNotes = listOf(
        "C", "D♭", "D", "E♭",
        "E", "F", "G♭", "G",
        "A♭", "A", "B♭", "B"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        val isCompact = maxWidth < 440.dp || maxHeight < 240.dp
        val isUltraCompact = maxWidth < 360.dp || maxHeight < 200.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Header with draggable area when pinned
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
                        text = if (isUltraCompact) "Tonic Pad" else "Tonic Pad — Ambient Layers",
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

                // Soundfont Banner (Clean realistic patch picker)
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
                        Text(text = "Soundfont Source", fontSize = 8.sp, color = TextDim2)
                        Text(
                            text = "Warm Worship Ambient Layer",
                            fontSize = if (isCompact) 10.5.sp else 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Text(text = "›", fontSize = 14.sp, color = TextDim)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Octave & Polyphonic Row (Interactive Octave Tag, removed chromatic mode)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Interactive Clickable Octave Range Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0x1A22D3EE))
                        .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
                        .clickable { onCycleOctave() }
                        .padding(horizontal = 8.dp, vertical = if (isCompact) 4.dp else 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "OCTAVE", fontSize = 8.sp, color = NeonCyan)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(text = octaveRange, fontSize = if (isCompact) 10.5.sp else 11.5.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                            Text(text = "↻", fontSize = 10.sp, color = NeonCyanLight)
                        }
                    }
                }

                // Multi-Pad Feature Tag (Polyphonic trigger)
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isMultiPadEnabled) Color(0x2E22D3EE) else Color(0x0DFFFFFF))
                        .border(
                            1.dp,
                            if (isMultiPadEnabled) NeonCyan else Color(0x14FFFFFF),
                            RoundedCornerShape(7.dp)
                        )
                        .clickable { onToggleMultiPad() }
                        .padding(horizontal = 8.dp, vertical = if (isCompact) 4.dp else 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "POLYPHONIE", fontSize = 8.sp, color = if (isMultiPadEnabled) NeonCyan else TextDim)
                        Text(
                            text = if (isMultiPadEnabled) "MULTI: ON" else "OFF",
                            fontSize = if (isCompact) 9.5.sp else 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMultiPadEnabled) NeonCyanLight else TextDim
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Tonic Body: 12 Note Grid + Knobs Sidebar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 12 Chromatic Note Grid (4 cols x 3 rows)
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val gap = if (isCompact) 4.dp else 6.dp
                    val rowHeight = (maxHeight - (gap * 2)) / 3

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        for (rowIndex in 0..2) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rowHeight),
                                horizontalArrangement = Arrangement.spacedBy(gap)
                            ) {
                                for (colIndex in 0..3) {
                                    val note = chromaticNotes[rowIndex * 4 + colIndex]
                                    val isActive = activeNotes.contains(note)

                                    val cellBrush = if (isActive) {
                                        Brush.verticalGradient(listOf(NeonCyanLight, NeonCyanDark))
                                    } else {
                                        Brush.linearGradient(listOf(Color(0x0FFFFFFF), Color(0x08FFFFFF)))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .shadow(if (isActive) 12.dp else 2.dp, RoundedCornerShape(8.dp))
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(cellBrush)
                                            .border(
                                                1.dp,
                                                if (isActive) NeonCyan else Color(0x1AFFFFFF),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { onNoteClick(note) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = note,
                                            fontSize = if (isCompact) 12.sp else 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isActive) Color(0xFF00232B) else TextDim
                                        )
                                    }
                                }
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
                        value = brightness,
                        onValueChange = onBrightnessChange,
                        label = "Brightness",
                        valueText = "${(brightness * 100).toInt()}%",
                        size = if (isCompact) 36.dp else 46.dp
                    )

                    RotaryKnob(
                        value = shimmer,
                        onValueChange = onShimmerChange,
                        label = "Shimmer",
                        valueText = "${(shimmer * 100).toInt()}%",
                        size = if (isCompact) 36.dp else 46.dp
                    )
                }
            }
        }
    }
}
