package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.model.LoopFile
import com.example.model.LoopFolder
import com.example.ui.theme.*

@Composable
fun TopBar(
    transpose: Int,
    octave: Int,
    onTransposeChange: (Int) -> Unit,
    onOctaveChange: (Int) -> Unit,
    isLoopsOpen: Boolean,
    onToggleLoops: () -> Unit,
    selectedBeats: Int,
    onSelectBeats: (Int) -> Unit,
    loopFolders: List<LoopFolder>,
    activeLoopFile: LoopFile?,
    onToggleLoopFolder: (String) -> Unit,
    onSelectLoopFile: (LoopFile) -> Unit,
    onOpenDrumPad: () -> Unit,
    onOpenTonicPad: () -> Unit,
    onPanic: () -> Unit,
    onOpenScenes: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topBarHeight = 34.dp
    val loopsButtonWidth by animateDpAsState(
        targetValue = if (isLoopsOpen) 155.dp else 130.dp,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "loopsBtnWidth"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topBarHeight)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Transpose Stepper
            StepperControl(
                label = "TRANS",
                value = if (transpose > 0) "+$transpose" else "$transpose",
                onMinus = { onTransposeChange(-1) },
                onPlus = { onTransposeChange(1) },
                modifier = Modifier.testTag("stepper_trans")
            )

            // Octave Stepper
            StepperControl(
                label = "OCT",
                value = if (octave > 0) "+$octave" else "$octave",
                onMinus = { onOctaveChange(-1) },
                onPlus = { onOctaveChange(1) },
                modifier = Modifier.testTag("stepper_oct")
            )

            // Loops Pill Button
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .width(loopsButtonWidth)
                    .clip(RoundedCornerShape(17.dp))
                    .background(
                        if (isLoopsOpen) {
                            Brush.horizontalGradient(listOf(NeonPurpleLight, NeonPurple, NeonPurpleDark))
                        } else {
                            Brush.verticalGradient(listOf(NeonPurple, NeonPurpleDark))
                        }
                    )
                    .border(
                        1.dp,
                        if (isLoopsOpen) NeonPurpleLight else Color(0x33FFFFFF),
                        RoundedCornerShape(17.dp)
                    )
                    .clickable { onToggleLoops() }
                    .padding(horizontal = 12.dp)
                    .testTag("loops_pill_btn"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .shadow(4.dp, CircleShape)
                            .clip(CircleShape)
                            .background(if (isLoopsOpen) NeonCyanLight else NeonViolet)
                    )
                    Text(
                        text = "Loops",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isLoopsOpen) "▲" else "▼",
                        fontSize = 10.sp,
                        color = Color(0xCCFFFFFF)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Drum Pad Launcher Icon (3x3 neon gradient dots)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                    .clickable { onOpenDrumPad() }
                    .testTag("btn_drum_pad"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(modifier = Modifier.size(5.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFFF5C8A)))
                        Box(modifier = Modifier.size(5.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFFF6FAE)))
                        Box(modifier = Modifier.size(5.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFE84FE0)))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(modifier = Modifier.size(5.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFF472E0)))
                        Box(modifier = Modifier.size(5.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFC026D3)))
                        Box(modifier = Modifier.size(5.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFA855F7)))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(modifier = Modifier.size(5.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFC026D3)))
                        Box(modifier = Modifier.size(5.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFF8B5CF6)))
                        Box(modifier = Modifier.size(5.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFF3B82F6)))
                    }
                }
            }

            // Tonic Pad Launcher Icon
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                    .clickable { onOpenTonicPad() }
                    .testTag("btn_tonic_pad"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .width(18.dp)
                        .height(16.dp)
                        .border(1.dp, NeonPurple, RoundedCornerShape(2.dp))
                        .padding(1.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(NeonPurple))
                    Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(NeonPurple))
                    Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(NeonPurple))
                }
            }

            // Panic Button
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(
                        Brush.verticalGradient(listOf(PanicRed, PanicRedDark))
                    )
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(17.dp))
                    .clickable { onPanic() }
                    .padding(horizontal = 14.dp)
                    .testTag("btn_panic"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Panic",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Scenes Menu Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                    .clickable { onOpenScenes() }
                    .testTag("btn_scene"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Scenes",
                    tint = TextDim,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Settings Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                    .clickable { onOpenSettings() }
                    .testTag("btn_settings"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Réglages",
                    tint = TextDim,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun StepperControl(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Minus Button
        Box(
            modifier = Modifier
                .width(26.dp)
                .fillMaxHeight()
                .background(Color(0x1022D3EE))
                .clickable { onMinus() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "−",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = NeonCyan
            )
        }

        // Center Value & Label
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .defaultMinSize(minWidth = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDim2,
                letterSpacing = 0.5.sp,
                lineHeight = 9.sp
            )
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                lineHeight = 14.sp
            )
        }

        // Plus Button
        Box(
            modifier = Modifier
                .width(26.dp)
                .fillMaxHeight()
                .background(Color(0x1022D3EE))
                .clickable { onPlus() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = NeonCyan
            )
        }
    }
}

@Composable
fun LoopsFloatingPanel(
    isOpen: Boolean,
    selectedBeats: Int,
    onSelectBeats: (Int) -> Unit,
    loopFolders: List<LoopFolder>,
    activeLoopFile: LoopFile?,
    onToggleFolder: (String) -> Unit,
    onSelectFile: (LoopFile) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(200)) + expandHorizontally(
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Start
        ) + expandVertically(
            animationSpec = tween(280, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Top
        ),
        exit = fadeOut(tween(150)) + shrinkHorizontally(
            animationSpec = tween(220),
            shrinkTowards = Alignment.Start
        ) + shrinkVertically(
            animationSpec = tween(200),
            shrinkTowards = Alignment.Top
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .heightIn(max = 280.dp)
                .shadow(20.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF2A2140), Color(0xFF1C1830))
                    )
                )
                .border(1.dp, Color(0x668B5CF6), RoundedCornerShape(18.dp))
                .padding(12.dp)
        ) {
            // Section: Durée
            Text(
                text = "DURÉE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextDim,
                letterSpacing = 0.6.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Beat duration chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(2, 4, 8, 16, 32).forEach { beats ->
                    val isSelected = selectedBeats == beats
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) {
                                    Brush.verticalGradient(listOf(NeonPurpleLight, NeonPurple))
                                } else {
                                    Brush.verticalGradient(listOf(Color(0x0DFFFFFF), Color(0x08FFFFFF)))
                                }
                            )
                            .border(
                                1.dp,
                                if (isSelected) Color.Transparent else Color(0x1AFFFFFF),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectBeats(beats) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$beats beats",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else TextDim
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Section: Fichiers
            Text(
                text = "FICHIERS (DOSSIER LOOPS)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextDim,
                letterSpacing = 0.6.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Folders and audio files
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(loopFolders) { folder ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Folder Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x0AFFFFFF))
                                .border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(8.dp))
                                .clickable { onToggleFolder(folder.name) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (folder.isOpen) "▼" else "▶",
                                fontSize = 8.sp,
                                color = TextDim
                            )
                            Text(text = folder.icon, fontSize = 12.sp)
                            Text(
                                text = folder.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${folder.files.size}",
                                fontSize = 9.sp,
                                color = TextDim2
                            )
                        }

                        // Folder items
                        if (folder.isOpen) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                folder.files.forEach { file ->
                                    val isActive = activeLoopFile == file
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isActive) Color(0x2E7C3AED) else Color.Transparent
                                            )
                                            .border(
                                                1.dp,
                                                if (isActive) NeonPurpleLight else Color.Transparent,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { onSelectFile(file) }
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(Color(0x26A78BFA))
                                                .border(1.dp, Color(0x66A78BFA), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play",
                                                tint = NeonPurpleLight,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.name,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "${file.duration} · Loops/${file.folder}/",
                                                fontSize = 8.5.sp,
                                                color = TextDim2
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
