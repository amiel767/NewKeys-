package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    
    // Loops
    isLoopsOpen: Boolean,
    onToggleLoops: () -> Unit,
    isLoopPlaying: Boolean,
    onToggleLoopPlayPause: () -> Unit,
    loopVolume: Float,
    onLoopVolumeChange: (Float) -> Unit,
    selectedBeats: Int,
    onSelectBeats: (Int) -> Unit,
    loopFolders: List<LoopFolder>,
    activeLoopFile: LoopFile?,
    onToggleLoopFolder: (String) -> Unit,
    onSelectLoopFile: (LoopFile) -> Unit,
    
    // Sustain & Splitter
    isSustainActive: Boolean,
    isMidiPedalPressed: Boolean,
    onToggleSustain: () -> Unit,
    isSplitterActive: Boolean,
    onToggleSplitter: () -> Unit,
    
    // Launchers
    onOpenDrumPad: () -> Unit,
    onOpenTonicPad: () -> Unit,
    onPanic: () -> Unit,
    onOpenScenes: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topBarHeight = 38.dp
    val loopsButtonWidth by animateDpAsState(
        targetValue = if (isLoopsOpen) 160.dp else 138.dp,
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
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Loops Pill with Direct Extremity Play/Pause Button
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(
                        if (isLoopsOpen) {
                            Brush.horizontalGradient(listOf(NeonPurpleLight, NeonPurple, NeonPurpleDark))
                        } else {
                            Brush.verticalGradient(listOf(NeonPurple, NeonPurpleDark))
                        }
                    )
                    .border(
                        1.dp,
                        if (isLoopsOpen) NeonPurpleLight else Color(0x4DFFFFFF),
                        RoundedCornerShape(19.dp)
                    )
                    .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                    .testTag("loops_pill_btn"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Clickable Text Label to expand dropdown
                    Row(
                        modifier = Modifier.clickable { onToggleLoops() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Loops",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isLoopsOpen) "▲" else "▼",
                            fontSize = 9.sp,
                            color = Color(0xCCFFFFFF)
                        )
                    }

                    // Large Prominent Play/Pause Action Button at the Extremity
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (isLoopPlaying) {
                                    Brush.verticalGradient(listOf(NeonCyanLight, NeonCyan))
                                } else {
                                    Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF)))
                                }
                            )
                            .border(
                                1.dp,
                                if (isLoopPlaying) NeonCyanLight else Color(0x44FFFFFF),
                                CircleShape
                            )
                            .clickable { onToggleLoopPlayPause() }
                            .testTag("btn_loops_play_extremity"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isLoopPlaying) "❚❚" else "▶",
                            fontSize = if (isLoopPlaying) 12.sp else 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isLoopPlaying) Color(0xFF003844) else Color.White
                        )
                    }
                }
            }

            // 2. Transpose Stepper (Left)
            StepperControl(
                label = "TRANS",
                value = if (transpose > 0) "+$transpose" else "$transpose",
                onMinus = { onTransposeChange(-1) },
                onPlus = { onTransposeChange(1) },
                modifier = Modifier.testTag("stepper_trans")
            )

            // 3. Octave Stepper (Right)
            StepperControl(
                label = "OCT",
                value = if (octave > 0) "+$octave" else "$octave",
                onMinus = { onOctaveChange(-1) },
                onPlus = { onOctaveChange(1) },
                modifier = Modifier.testTag("stepper_oct")
            )

            Spacer(modifier = Modifier.weight(1f))

            // 4. Global Sustain Button with Active Green Halo
            val infiniteTransition = rememberInfiniteTransition(label = "sustain_pedal_blink")
            val pedalBlinkAlpha by infiniteTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pedal_blink_alpha"
            )

            val isSustainLit = isSustainActive || isMidiPedalPressed
            val sustainAlpha = if (isMidiPedalPressed) pedalBlinkAlpha else if (isSustainActive) 1.0f else 0.4f
            val sustainGreen = Color(0xFF10B981)

            Box(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSustainLit) Color(0x2610B981) else DarkSurface)
                    .border(
                        1.dp,
                        if (isSustainLit) sustainGreen.copy(alpha = sustainAlpha) else BorderSubtle,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onToggleSustain() }
                    .padding(horizontal = 9.dp)
                    .testTag("btn_global_sustain"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isSustainLit) sustainGreen.copy(alpha = sustainAlpha) else TextDim2)
                    )
                    Text(
                        text = "SUSTAIN",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSustainLit) sustainGreen else TextDim
                    )
                }
            }

            // 5. Global Splitter Button (Scissors ✂️)
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSplitterActive) Color(0x3322D3EE) else DarkSurface)
                    .border(
                        1.dp,
                        if (isSplitterActive) NeonCyan else BorderSubtle,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onToggleSplitter() }
                    .padding(horizontal = 9.dp)
                    .testTag("btn_global_splitter"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(text = "✂️", fontSize = 11.sp)
                    Text(
                        text = "SPLIT",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSplitterActive) NeonCyan else TextDim
                    )
                }
            }

            // 6. Drum Pad Launcher Icon (Neon Pad Matrix)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
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

            // 7. Tonic Pad Launcher Icon
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .clickable { onOpenTonicPad() }
                    .testTag("btn_tonic_pad"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .width(20.dp)
                        .height(18.dp)
                        .border(1.dp, NeonPurple, RoundedCornerShape(2.dp))
                        .padding(1.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(NeonPurple))
                    Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(NeonPurple))
                    Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(NeonPurple))
                }
            }

            // 8. Panic Button (All Notes Off - Rouge clignotant / vibrant)
            val panicAlpha by infiniteTransition.animateFloat(
                initialValue = 0.82f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "panic_pulse"
            )
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(
                        Brush.verticalGradient(listOf(PanicRed.copy(alpha = panicAlpha), PanicRedDark))
                    )
                    .border(1.dp, PanicRed.copy(alpha = panicAlpha), RoundedCornerShape(19.dp))
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

            // 9. Scene Expansion Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .clickable { onOpenScenes() }
                    .testTag("btn_scene"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Scenes",
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            // 10. Settings Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .clickable { onOpenSettings() }
                    .testTag("btn_settings"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Réglages",
                    tint = TextDim,
                    modifier = Modifier.size(18.dp)
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
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Minus Button
        Box(
            modifier = Modifier
                .width(28.dp)
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
                .padding(horizontal = 7.dp)
                .defaultMinSize(minWidth = 40.dp),
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
                .width(28.dp)
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
    isLoopPlaying: Boolean,
    onToggleLoopPlayPause: () -> Unit,
    loopVolume: Float,
    onLoopVolumeChange: (Float) -> Unit,
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
        enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) + scaleIn(
            initialScale = 0.94f,
            animationSpec = tween(240, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.96f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .heightIn(max = 340.dp)
                .shadow(24.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF2A2140), Color(0xFF1C1830), Color(0xFF131022))
                    )
                )
                .border(1.dp, Color(0x668B5CF6), RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            // Header with Large Play/Pause Button, Title, and Volume
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Large Prominent Play Button (32dp)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isLoopPlaying) {
                                    Brush.verticalGradient(listOf(NeonCyanLight, NeonCyan))
                                } else {
                                    Brush.verticalGradient(listOf(Color(0x448B5CF6), Color(0x228B5CF6)))
                                }
                            )
                            .border(
                                1.5.dp,
                                if (isLoopPlaying) NeonCyanLight else NeonPurpleLight,
                                CircleShape
                            )
                            .clickable { onToggleLoopPlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isLoopPlaying) "❚❚" else "▶",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isLoopPlaying) Color(0xFF003844) else Color.White
                        )
                    }

                    Column {
                        Text(
                            text = "LECTEUR DE LOOPS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (activeLoopFile != null) activeLoopFile.name else "Aucune boucle active",
                            fontSize = 9.sp,
                            color = if (isLoopPlaying) NeonCyan else TextDim
                        )
                    }
                }

                // Volume Slider
                Row(
                    modifier = Modifier.width(130.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "VOL", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = TextDim)
                    Slider(
                        value = loopVolume,
                        onValueChange = onLoopVolumeChange,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonPurpleLight,
                            activeTrackColor = NeonPurpleLight,
                            inactiveTrackColor = Color(0x1AFFFFFF)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Loop Duration / Bars Selector: 2, 4, 8, 16, 32, 64
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x12FFFFFF))
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "DURÉE DE BOUCLE :",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )

                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(0, 2, 4, 8, 16, 32, 64).forEach { beats ->
                        val isSel = (selectedBeats == beats)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) NeonPurpleLight else Color(0x18FFFFFF))
                                .border(1.dp, if (isSel) NeonPurpleLight else Color(0x22FFFFFF), RoundedCornerShape(6.dp))
                                .clickable { onSelectBeats(beats) }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (beats == 0) "AUTO" else "${beats}T",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSel) Color.White else TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Simple, Unified Scrollable List of Folders & Audio Files
            Text(
                text = "DOSSIERS & FICHIERS AUDIO",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextDim,
                letterSpacing = 0.6.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(loopFolders) { folder ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Folder Header Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (folder.isOpen) Color(0x1AFFFFFF) else Color(0x0AFFFFFF))
                                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp))
                                .clickable { onToggleFolder(folder.name) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (folder.isOpen) "▼" else "▶",
                                fontSize = 8.5.sp,
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
                                text = "${folder.files.size} fichiers",
                                fontSize = 8.5.sp,
                                color = TextDim2
                            )
                        }

                        // Folder items (Files)
                        if (folder.isOpen) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, top = 3.dp, bottom = 3.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                folder.files.forEach { file ->
                                    val isActive = activeLoopFile == file
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isActive) Color(0x337C3AED) else Color(0x08FFFFFF)
                                            )
                                            .border(
                                                1.dp,
                                                if (isActive) NeonPurpleLight else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { onSelectFile(file) }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(if (isActive && isLoopPlaying) NeonCyan else Color(0x26A78BFA))
                                                .border(
                                                    1.dp,
                                                    if (isActive && isLoopPlaying) NeonCyan else Color(0x66A78BFA),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (isActive && isLoopPlaying) "❚❚" else "▶",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isActive && isLoopPlaying) Color(0xFF003844) else NeonPurpleLight
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.name,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isActive) Color.White else TextPrimary
                                            )
                                            Text(
                                                text = "${file.duration} · ${file.bpm} BPM · loops/${file.folder}/",
                                                fontSize = 8.sp,
                                                color = if (isActive) NeonPurpleLight else TextDim2
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
