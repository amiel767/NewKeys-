package com.soundstage.mixer.ui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.*
import com.soundstage.mixer.ui.theme.*

private val StepDrumBg = Color(0xFF1E040B)
private val StepDrumSurface = Color(0xFF2E0A14)
private val StepDrumSurfaceLight = Color(0xFF451020)
private val StepDrumAccent = Color(0xFFF43F5E)
private val StepDrumAccentGlow = Color(0xFFFF6B8B)
private val StepDrumYellow = Color(0xFFFFD166)
private val StepDrumCyan = Color(0xFF00E5FF)
private val StepDrumGreen = Color(0xFF00E676)

@Composable
fun StepDrumPage(
    state: StepDrumUiState,
    bpm: Int,
    isMetronomeOn: Boolean,
    onPlayPause: () -> Unit,
    onRecordToggle: () -> Unit,
    onRecordModeToggle: () -> Unit,
    onViewModeChange: (StepDrumViewMode) -> Unit,
    onEditModeChange: (StepDrumMode) -> Unit,
    onVariationSelect: (String) -> Unit,
    onAddVariation: () -> Unit,
    onDuplicateVariation: (String) -> Unit,
    onClearPattern: () -> Unit,
    onStepToggle: (trackIdx: Int, stepIdx: Int) -> Unit,
    onStepVelocityChange: (trackIdx: Int, stepIdx: Int, velocity: Int) -> Unit,
    onStepRepeatChange: (trackIdx: Int, stepIdx: Int, repeat: Int) -> Unit,
    onStepChanceChange: (trackIdx: Int, stepIdx: Int, chance: Int) -> Unit,
    onTrackLoopLengthChange: (trackIdx: Int, length: Int) -> Unit,
    onTrackPreview: (trackIdx: Int) -> Unit,
    onTrackMuteToggle: (trackIdx: Int) -> Unit,
    onTrackSoloToggle: (trackIdx: Int) -> Unit,
    onFillToggle: () -> Unit,
    onBreakToggle: () -> Unit,
    onAutoFillToggle: () -> Unit,
    onSequenceMeasureSelect: (measureIdx: Int) -> Unit,
    onSequenceBlockVariationChange: (measureIdx: Int, varId: String) -> Unit,
    onSequenceBlockFillToggle: (measureIdx: Int) -> Unit,
    onSequenceBlockBreakToggle: (measureIdx: Int) -> Unit,
    onBpmChange: (Int) -> Unit,
    onSwingChange: (Int) -> Unit,
    onKitSelect: () -> Unit,
    onBackToMixer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeVar = state.variations[state.activeVariationId] ?: PatternVariation(
        id = state.activeVariationId,
        tracks = emptyList()
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF280710),
                        Color(0xFF190308),
                        Color(0xFF0F0205)
                    )
                )
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("stepdrum_page")
    ) {
        // ================= TOP HEADER & TRANSPORT =================
        StepDrumHeader(
            state = state,
            bpm = bpm,
            onPlayPause = onPlayPause,
            onRecordToggle = onRecordToggle,
            onRecordModeToggle = onRecordModeToggle,
            onViewModeChange = onViewModeChange,
            onVariationSelect = onVariationSelect,
            onAddVariation = onAddVariation,
            onDuplicateVariation = onDuplicateVariation,
            onClearPattern = onClearPattern,
            onFillToggle = onFillToggle,
            onBreakToggle = onBreakToggle,
            onAutoFillToggle = onAutoFillToggle,
            onKitSelect = onKitSelect,
            onBpmChange = onBpmChange,
            onBackToMixer = onBackToMixer
        )

        Spacer(modifier = Modifier.height(4.dp))

        // ================= MAIN CONTENT (GRID vs SEQUENCE) =================
        if (state.viewMode == StepDrumViewMode.GRID) {
            StepDrumGridView(
                tracks = activeVar.tracks,
                editMode = state.editMode,
                currentStep = if (state.isPlaying) state.currentStepIndex else -1,
                onEditModeChange = onEditModeChange,
                onStepToggle = onStepToggle,
                onStepVelocityChange = onStepVelocityChange,
                onStepRepeatChange = onStepRepeatChange,
                onStepChanceChange = onStepChanceChange,
                onTrackLoopLengthChange = onTrackLoopLengthChange,
                onTrackPreview = onTrackPreview,
                onTrackMuteToggle = onTrackMuteToggle,
                onTrackSoloToggle = onTrackSoloToggle,
                modifier = Modifier.weight(1f)
            )
        } else {
            StepDrumSequenceView(
                sequenceBlocks = state.sequenceBlocks,
                activeMeasureIdx = state.activeMeasureIndex,
                currentStep = if (state.isPlaying) state.currentStepIndex else -1,
                isPlaying = state.isPlaying,
                availableVariations = state.variations.keys.toList(),
                onSequenceMeasureSelect = onSequenceMeasureSelect,
                onSequenceBlockVariationChange = onSequenceBlockVariationChange,
                onSequenceBlockFillToggle = onSequenceBlockFillToggle,
                onSequenceBlockBreakToggle = onSequenceBlockBreakToggle,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StepDrumHeader(
    state: StepDrumUiState,
    bpm: Int,
    onPlayPause: () -> Unit,
    onRecordToggle: () -> Unit,
    onRecordModeToggle: () -> Unit,
    onViewModeChange: (StepDrumViewMode) -> Unit,
    onVariationSelect: (String) -> Unit,
    onAddVariation: () -> Unit,
    onDuplicateVariation: (String) -> Unit,
    onClearPattern: () -> Unit,
    onFillToggle: () -> Unit,
    onBreakToggle: () -> Unit,
    onAutoFillToggle: () -> Unit,
    onKitSelect: () -> Unit,
    onBpmChange: (Int) -> Unit,
    onBackToMixer: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = StepDrumSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33F43F5E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Section Gauche: Logo / Titre & Kit
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Menu Burger / Retour Mixeur
                IconButton(
                    onClick = onBackToMixer,
                    modifier = Modifier.size(28.dp).testTag("btn_stepdrum_back_mixer")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Retour Mixeur",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brush.horizontalGradient(listOf(StepDrumAccent, Color(0xFFBE123C))))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "STEPDRUM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }

                // Kit Selector
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = StepDrumSurfaceLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
                    modifier = Modifier.clickable { onKitSelect() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Kit",
                            tint = StepDrumYellow,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = state.activeDrumKitName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Grille vs Séquence Tabs
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF180308))
                        .padding(2.dp)
                ) {
                    TabButton(
                        text = "GRILLE",
                        isSelected = state.viewMode == StepDrumViewMode.GRID,
                        onClick = { onViewModeChange(StepDrumViewMode.GRID) }
                    )
                    TabButton(
                        text = "SÉQUENCE",
                        isSelected = state.viewMode == StepDrumViewMode.SEQUENCE,
                        onClick = { onViewModeChange(StepDrumViewMode.SEQUENCE) }
                    )
                }
            }

            // Section Centre: Sélecteur de variations A-H
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "PATTERN:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.6f)
                )

                listOf("A", "B", "C", "D", "E", "F", "G", "H").forEach { varId ->
                    val isAvailable = state.variations.containsKey(varId)
                    val isSelected = state.activeVariationId == varId
                    val isPending = state.pendingNextVariation == varId

                    val btnColor = when {
                        isSelected -> StepDrumAccent
                        isPending -> StepDrumYellow
                        isAvailable -> StepDrumSurfaceLight
                        else -> Color(0x22FFFFFF)
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(btnColor)
                            .clickable { onVariationSelect(varId) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = varId,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            color = if (isSelected || isPending) Color.White else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Section Droite: Transport & Actions Live (Play, Rec, Fill, Break, AutoFill)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // FILL Button
                LiveActionButton(
                    text = "FILL",
                    isActive = state.isFillActive,
                    activeColor = StepDrumYellow,
                    onClick = onFillToggle
                )

                // BREAK Button
                LiveActionButton(
                    text = "BREAK",
                    isActive = state.isBreakActive,
                    activeColor = Color(0xFFFF5252),
                    onClick = onBreakToggle
                )

                // AUTO FILL 4
                LiveActionButton(
                    text = "AUTO 4",
                    isActive = state.fillAutoEvery4,
                    activeColor = StepDrumCyan,
                    onClick = onAutoFillToggle
                )

                // RECORD (REPLACE / ADD)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (state.isRecording) Color(0xFFE11D48) else StepDrumSurfaceLight,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (state.isRecording) Color.White else Color(0x33FFFFFF)
                    ),
                    modifier = Modifier.clickable { onRecordToggle() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (state.isRecording) Color.White else Color(0xFFFF5252))
                        )
                        Text(
                            text = if (state.recordMode == "REPLACE") "REC (R)" else "REC (A)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // PLAY / STOP
                Button(
                    onClick = onPlayPause,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isPlaying) StepDrumGreen else StepDrumAccent
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(28.dp).testTag("stepdrum_play_button")
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Stop" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (state.isPlaying) "STOP" else "PLAY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) StepDrumAccent else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun LiveActionButton(
    text: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) activeColor else StepDrumSurfaceLight)
            .border(
                1.dp,
                if (isActive) Color.White else Color(0x22FFFFFF),
                RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) Color.Black else Color.White
        )
    }
}

// ================= VUE 1 : GRILLE PATTERN =================

@Composable
private fun StepDrumGridView(
    tracks: List<PatternTrack>,
    editMode: StepDrumMode,
    currentStep: Int,
    onEditModeChange: (StepDrumMode) -> Unit,
    onStepToggle: (Int, Int) -> Unit,
    onStepVelocityChange: (Int, Int, Int) -> Unit,
    onStepRepeatChange: (Int, Int, Int) -> Unit,
    onStepChanceChange: (Int, Int, Int) -> Unit,
    onTrackLoopLengthChange: (Int, Int) -> Unit,
    onTrackPreview: (Int) -> Unit,
    onTrackMuteToggle: (Int) -> Unit,
    onTrackSoloToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(StepDrumSurface)
            .padding(6.dp)
    ) {
        // Barre des 5 Modes Tactiles (Pas, Vélocité, Répétition, Chance, Boucle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MODE TACTILE:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.6f)
                )

                StepDrumMode.values().forEach { mode ->
                    val isSelected = editMode == mode
                    val modeColor = when (mode) {
                        StepDrumMode.STEP -> StepDrumAccent
                        StepDrumMode.VELOCITY -> StepDrumYellow
                        StepDrumMode.REPEAT -> StepDrumCyan
                        StepDrumMode.CHANCE -> StepDrumGreen
                        StepDrumMode.LOOP -> Color(0xFFA855F7)
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isSelected) modeColor else StepDrumSurfaceLight,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color.White else Color(0x1AFFFFFF)
                        ),
                        modifier = Modifier.clickable { onEditModeChange(mode) }
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            color = if (isSelected) Color.Black else Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Indicateur de temps / Beat Numbers (1..4)
            Row(
                modifier = Modifier.width(520.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("TEMPS 1", "TEMPS 2", "TEMPS 3", "TEMPS 4").forEachIndexed { index, beatName ->
                    Text(
                        text = beatName,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (currentStep in (index * 4)..(index * 4 + 3)) StepDrumYellow else Color.White.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 8 Pistes de Batterie (Kick, Clap, Wood, Tumb, Tom 1, Tom 2, Tom 3, Sub Kick)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(tracks) { trackIdx, track ->
                StepDrumTrackRow(
                    track = track,
                    trackIdx = trackIdx,
                    editMode = editMode,
                    currentStep = currentStep,
                    onStepToggle = { stepIdx -> onStepToggle(trackIdx, stepIdx) },
                    onStepVelocityChange = { stepIdx, vel -> onStepVelocityChange(trackIdx, stepIdx, vel) },
                    onStepRepeatChange = { stepIdx, rep -> onStepRepeatChange(trackIdx, stepIdx, rep) },
                    onStepChanceChange = { stepIdx, chance -> onStepChanceChange(trackIdx, stepIdx, chance) },
                    onTrackLoopLengthChange = { len -> onTrackLoopLengthChange(trackIdx, len) },
                    onTrackPreview = { onTrackPreview(trackIdx) },
                    onTrackMuteToggle = { onTrackMuteToggle(trackIdx) },
                    onTrackSoloToggle = { onTrackSoloToggle(trackIdx) }
                )
            }
        }
    }
}

@Composable
private fun StepDrumTrackRow(
    track: PatternTrack,
    trackIdx: Int,
    editMode: StepDrumMode,
    currentStep: Int,
    onStepToggle: (Int) -> Unit,
    onStepVelocityChange: (Int, Int) -> Unit,
    onStepRepeatChange: (Int, Int) -> Unit,
    onStepChanceChange: (Int, Int) -> Unit,
    onTrackLoopLengthChange: (Int) -> Unit,
    onTrackPreview: () -> Unit,
    onTrackMuteToggle: () -> Unit,
    onTrackSoloToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1F050C))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // En-tête de piste (Nom + Preview Pad + Mute/Solo)
        Row(
            modifier = Modifier
                .width(160.dp)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Pad Déclencheur
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(track.color)
                    .clickable { onTrackPreview() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Preview",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }

            // Nom de l'instrument
            Text(
                text = track.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Mute / Solo
            MiniButton(
                text = "M",
                isActive = track.isMuted,
                activeColor = MuteRed,
                onClick = onTrackMuteToggle
            )
            MiniButton(
                text = "S",
                isActive = track.isSolo,
                activeColor = SoloAmber,
                onClick = onTrackSoloToggle
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Grille des 16 Pas
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (stepIdx in 0 until 16) {
                val step = track.steps.getOrElse(stepIdx) { StepCell() }
                val isBeatFirst = stepIdx % 4 == 0
                val isPlayheadHere = currentStep == stepIdx
                val isPastLoopLength = stepIdx >= track.loopLength

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = 2.dp)
                ) {
                    StepCellBox(
                        step = step,
                        stepIdx = stepIdx,
                        trackColor = track.color,
                        editMode = editMode,
                        isBeatFirst = isBeatFirst,
                        isPlayhead = isPlayheadHere,
                        isPastLoop = isPastLoopLength,
                        onToggle = { onStepToggle(stepIdx) },
                        onVelocityChange = { vel -> onStepVelocityChange(stepIdx, vel) },
                        onRepeatChange = { rep -> onStepRepeatChange(stepIdx, rep) },
                        onChanceChange = { ch -> onStepChanceChange(stepIdx, ch) },
                        onSetLoopEnd = { onTrackLoopLengthChange(stepIdx + 1) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StepCellBox(
    step: StepCell,
    stepIdx: Int,
    trackColor: Color,
    editMode: StepDrumMode,
    isBeatFirst: Boolean,
    isPlayhead: Boolean,
    isPastLoop: Boolean,
    onToggle: () -> Unit,
    onVelocityChange: (Int) -> Unit,
    onRepeatChange: (Int) -> Unit,
    onChanceChange: (Int) -> Unit,
    onSetLoopEnd: () -> Unit
) {
    val cellBg = when {
        isPastLoop -> Color(0x11FFFFFF)
        step.enabled -> trackColor
        isBeatFirst -> Color(0xFF3B0D18)
        else -> Color(0xFF280710)
    }

    val borderColor = when {
        isPlayhead -> Color.White
        step.enabled -> Color.White.copy(alpha = 0.5f)
        else -> Color(0x1AFFFFFF)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(3.dp))
            .background(cellBg)
            .border(if (isPlayhead) 1.5.dp else 0.5.dp, borderColor, RoundedCornerShape(3.dp))
            .pointerInput(editMode, stepIdx, step.enabled) {
                when (editMode) {
                    StepDrumMode.STEP -> {
                        detectTapGestures { onToggle() }
                    }
                    StepDrumMode.VELOCITY -> {
                        detectTapGestures(
                            onTap = {
                                if (!step.enabled) onToggle()
                                else {
                                    val nextVel = if (step.velocity < 60) 100 else if (step.velocity < 110) 127 else 45
                                    onVelocityChange(nextVel)
                                }
                            }
                        )
                    }
                    StepDrumMode.REPEAT -> {
                        detectTapGestures(
                            onTap = {
                                if (!step.enabled) onToggle()
                                val nextRep = if (step.repeatCount >= 4) 1 else step.repeatCount + 1
                                onRepeatChange(nextRep)
                            }
                        )
                    }
                    StepDrumMode.CHANCE -> {
                        detectTapGestures(
                            onTap = {
                                if (!step.enabled) onToggle()
                                val nextChance = when (step.chance) {
                                    100 -> 75
                                    75 -> 50
                                    50 -> 25
                                    else -> 100
                                }
                                onChanceChange(nextChance)
                            }
                        )
                    }
                    StepDrumMode.LOOP -> {
                        detectTapGestures { onSetLoopEnd() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (step.enabled) {
            when (editMode) {
                StepDrumMode.VELOCITY -> {
                    Text(
                        text = "${step.velocity}",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                }
                StepDrumMode.REPEAT -> {
                    if (step.repeatCount > 1) {
                        Text(
                            text = "x${step.repeatCount}",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                }
                StepDrumMode.CHANCE -> {
                    if (step.chance < 100) {
                        Text(
                            text = "${step.chance}%",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                }
                else -> {
                    // Petit point central
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.8f))
                    )
                }
            }
        }
    }
}

// ================= VUE 2 : SÉQUENCE MORCEAU =================

@Composable
private fun StepDrumSequenceView(
    sequenceBlocks: List<SequenceBlock>,
    activeMeasureIdx: Int,
    currentStep: Int,
    isPlaying: Boolean,
    availableVariations: List<String>,
    onSequenceMeasureSelect: (Int) -> Unit,
    onSequenceBlockVariationChange: (Int, String) -> Unit,
    onSequenceBlockFillToggle: (Int) -> Unit,
    onSequenceBlockBreakToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(StepDrumSurface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "STRUCTURE DU MORCEAU (8 MESURES)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = StepDrumYellow,
                letterSpacing = 0.8.sp
            )

            Text(
                text = if (isPlaying) "MESURE EN COURS : ${activeMeasureIdx + 1}/8 (PAS ${currentStep + 1}/16)" else "EN ATTENTE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        // Grille des 8 Mesures
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(sequenceBlocks) { measureIdx, block ->
                val isActive = isPlaying && activeMeasureIdx == measureIdx

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isActive) Color(0xFF5C1024) else StepDrumSurfaceLight,
                    border = androidx.compose.foundation.BorderStroke(
                        if (isActive) 2.dp else 1.dp,
                        if (isActive) StepDrumYellow else Color(0x33FFFFFF)
                    ),
                    modifier = Modifier
                        .width(130.dp)
                        .height(180.dp)
                        .clickable { onSequenceMeasureSelect(measureIdx) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Numéro de mesure
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "BAR ${measureIdx + 1}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isActive) StepDrumYellow else Color.White
                            )

                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(StepDrumGreen)
                                )
                            }
                        }

                        // Sélecteur de Variation (A, B, C...)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "PATTERN",
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(StepDrumAccent)
                                    .clickable {
                                        val nextIdx = (availableVariations.indexOf(block.variationId) + 1) % availableVariations.size
                                        onSequenceBlockVariationChange(measureIdx, availableVariations[nextIdx])
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = block.variationId,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }

                        // Boutons FILL et BREAK pour la mesure
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = { onSequenceBlockFillToggle(measureIdx) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (block.isFill) StepDrumYellow else Color(0x22FFFFFF)
                                ),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f).height(24.dp)
                            ) {
                                Text(
                                    text = "FILL",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (block.isFill) Color.Black else Color.White
                                )
                            }

                            Button(
                                onClick = { onSequenceBlockBreakToggle(measureIdx) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (block.isBreak) Color(0xFFFF5252) else Color(0x22FFFFFF)
                                ),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f).height(24.dp)
                            ) {
                                Text(
                                    text = "BREAK",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (block.isBreak) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniButton(
    text: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (isActive) activeColor else Color(0x22FFFFFF))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = if (isActive) Color.Black else Color.White.copy(alpha = 0.8f)
        )
    }
}
