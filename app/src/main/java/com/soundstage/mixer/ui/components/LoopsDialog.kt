package com.soundstage.mixer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.soundstage.mixer.model.LoopFile
import com.soundstage.mixer.model.LoopFolder
import com.soundstage.mixer.ui.theme.*
import kotlin.math.sin

/**
 * LoopsDialog:
 * Material You Expressive Modal Dialog for Loops & Waveform Editing.
 * Features:
 * - Clean Material You Expressive surface styling (no textures, pure colors & icons)
 * - Single click: Play / Select loop file
 * - Long click: Reveals contextual minimalist Delete (trash) and Edit (pen) icons
 * - Waveform Editor page:
 *    * Dialog expands width dynamically
 *    * Full waveform display with interactive start/end trim drag handles
 *    * Duration & Time settings stepper (2 to 64 including odd numbers: [ - ] [ number ] [ + ])
 *    * Minimalist Material You icons
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoopsDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    loopFolders: List<LoopFolder>,
    activeLoopFile: LoopFile?,
    isLoopPlaying: Boolean,
    loopVolume: Float,
    onLoopVolumeChange: (Float) -> Unit,
    selectedBeats: Int,
    onSelectBeats: (Int) -> Unit,
    onToggleFolder: (String) -> Unit,
    onSelectFile: (LoopFile) -> Unit,
    onDeleteFile: (LoopFile) -> Unit,
    editingLoopFile: LoopFile?,
    onOpenEditFile: (LoopFile) -> Unit,
    onCloseEditFile: () -> Unit,
    onToggleEditorPlay: () -> Unit = {},
    editorBeats: Int,
    onUpdateEditorBeats: (Int) -> Unit,
    editorStartMs: Int,
    editorEndMs: Int,
    onUpdateEditorTrims: (Int, Int) -> Unit,
    editorStartStep: Int,
    editorEndStep: Int,
    onUpdateEditorSteps: (Int, Int) -> Unit,
    onOverwriteEditChanges: (Int, Int, Int, Int, Int) -> Unit,
    onSaveCopyEditChanges: (Int, Int, Int, Int, Int) -> Unit,
    onImportLoop: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val isEditing = editingLoopFile != null
    val targetWidth by animateDpAsState(
        targetValue = if (isEditing) 640.dp else 440.dp,
        animationSpec = tween(300),
        label = "dialog_width"
    )

    // State to track which file currently has its long-press action icons shown
    var revealedActionFileName by remember { mutableStateOf<String?>(null) }
    var fileToDelete by remember { mutableStateOf<LoopFile?>(null) }

    Dialog(
        onDismissRequest = {
            if (isEditing) onCloseEditFile() else onClose()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = modifier
                    .widthIn(min = 340.dp, max = targetWidth)
                    .fillMaxWidth(if (isEditing) 0.95f else 0.88f)
                    .heightIn(min = 380.dp, max = 580.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, Color(0x33A78BFA), RoundedCornerShape(28.dp))
                    .shadow(16.dp, RoundedCornerShape(28.dp)),
                color = Color(0xFF161926),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    if (isEditing && editingLoopFile != null) {
                        // ================= LOOP EDITOR VIEW (EXPANDED) =================
                        LoopEditorContent(
                            file = editingLoopFile,
                            beats = editorBeats,
                            onUpdateBeats = onUpdateEditorBeats,
                            startMs = editorStartMs,
                            endMs = editorEndMs,
                            onUpdateTrims = onUpdateEditorTrims,
                            startStep = editorStartStep,
                            endStep = editorEndStep,
                            onUpdateSteps = onUpdateEditorSteps,
                            onOverwrite = {
                                onOverwriteEditChanges(editorBeats, editorStartMs, editorEndMs, editorStartStep, editorEndStep)
                            },
                            onSaveCopy = {
                                onSaveCopyEditChanges(editorBeats, editorStartMs, editorEndMs, editorStartStep, editorEndStep)
                            },
                            isLoopPlaying = isLoopPlaying,
                            onTogglePlay = onToggleEditorPlay,
                            onBack = onCloseEditFile
                        )
                    } else {
                        // ================= LOOP FILE LIST VIEW (MATERIAL YOU EXPRESSIVE) =================
                        LoopListContent(
                            loopFolders = loopFolders,
                            activeLoopFile = activeLoopFile,
                            isLoopPlaying = isLoopPlaying,
                            loopVolume = loopVolume,
                            onLoopVolumeChange = onLoopVolumeChange,
                            selectedBeats = selectedBeats,
                            onSelectBeats = onSelectBeats,
                            onToggleFolder = onToggleFolder,
                            onSelectFile = onSelectFile,
                            revealedActionFileName = revealedActionFileName,
                            onRevealActions = { revealedActionFileName = it },
                            onOpenEdit = { file ->
                                revealedActionFileName = null
                                onOpenEditFile(file)
                            },
                            onConfirmDelete = { file ->
                                fileToDelete = file
                            },
                            onImportLoop = onImportLoop,
                            onClose = onClose
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (fileToDelete != null) {
        val target = fileToDelete!!
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = {
                Text(
                    text = "Supprimer la boucle ?",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Voulez-vous vraiment supprimer définitivement « ${target.name} » ?",
                    color = TextDim,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFile(target)
                        fileToDelete = null
                        revealedActionFileName = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MuteRed)
                ) {
                    Text("Supprimer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Annuler", color = TextDim)
                }
            },
            containerColor = Color(0xFF222638),
            shape = RoundedCornerShape(20.dp)
        )
    }
}

/**
 * LoopListContent:
 * Displays volume slider, import action, and Material You Expressive file items.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoopListContent(
    loopFolders: List<LoopFolder>,
    activeLoopFile: LoopFile?,
    isLoopPlaying: Boolean,
    loopVolume: Float,
    onLoopVolumeChange: (Float) -> Unit,
    selectedBeats: Int,
    onSelectBeats: (Int) -> Unit,
    onToggleFolder: (String) -> Unit,
    onSelectFile: (LoopFile) -> Unit,
    revealedActionFileName: String?,
    onRevealActions: (String?) -> Unit,
    onOpenEdit: (LoopFile) -> Unit,
    onConfirmDelete: (LoopFile) -> Unit,
    onImportLoop: () -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x26A78BFA)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Loops Icon",
                        tint = NeonPurpleLight,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Column {
                    Text(
                        text = "Boucles Audio",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Lecture DJ gapless",
                        fontSize = 10.sp,
                        color = TextDim
                    )
                }
            }

            // Right Actions: [ Case Réglage Temps ] [ + compact ] [ x compact ]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Case de réglage de temps (Temps / Beats) positionnée à côté de +
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x1CFFFFFF))
                        .border(1.dp, Color(0x33A78BFA), RoundedCornerShape(10.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x18FFFFFF))
                            .clickable(enabled = selectedBeats > 2) {
                                onSelectBeats((selectedBeats - 1).coerceIn(2, 64))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Diminuer temps",
                            tint = if (selectedBeats > 2) NeonPurpleLight else TextDim2,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Text(
                        text = "${selectedBeats}T",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonCyanLight,
                        modifier = Modifier.padding(horizontal = 3.dp)
                    )

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x18FFFFFF))
                            .clickable(enabled = selectedBeats < 64) {
                                onSelectBeats((selectedBeats + 1).coerceIn(2, 64))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Augmenter temps",
                            tint = if (selectedBeats < 64) NeonPurpleLight else TextDim2,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Bouton + (Importer) en carré bordure néon petit et adapté
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x2222D3EE))
                        .border(1.dp, NeonCyan.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onImportLoop),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Importer",
                        tint = NeonCyanLight,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Bouton x (Fermer) en carré bordure néon petit et adapté
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x22FFFFFF))
                        .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(8.dp))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fermer",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Volume Controller Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x14FFFFFF))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Volume",
                tint = NeonPurpleLight,
                modifier = Modifier.size(20.dp)
            )
            Slider(
                value = loopVolume,
                onValueChange = onLoopVolumeChange,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = NeonPurpleLight,
                    activeTrackColor = NeonPurpleLight,
                    inactiveTrackColor = Color(0x22FFFFFF)
                )
            )
            Text(
                text = "${(loopVolume * 100).toInt()}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Folders and Files (Material You Expressive List)
        Text(
            text = "BIBLIOTHÈQUE DE BOUCLES",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextDim,
            letterSpacing = 0.8.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(loopFolders) { folder ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Folder Item Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (folder.isOpen) Color(0x1A8B5CF6) else Color(0x0CFFFFFF))
                            .border(1.dp, if (folder.isOpen) Color(0x338B5CF6) else Color(0x10FFFFFF), RoundedCornerShape(16.dp))
                            .clickable { onToggleFolder(folder.name) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (folder.isOpen) Icons.Default.FolderOpen else Icons.Default.Folder,
                            contentDescription = "Dossier",
                            tint = NeonPurpleLight,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = folder.name,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${folder.files.size} piste${if (folder.files.size > 1) "s" else ""}",
                            fontSize = 10.sp,
                            color = TextDim2
                        )
                        Icon(
                            imageVector = if (folder.isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = TextDim,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Expanded Files
                    if (folder.isOpen) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            folder.files.forEach { file ->
                                val isPlayingThis = (activeLoopFile?.name == file.name && isLoopPlaying)
                                val isSelected = (activeLoopFile?.name == file.name)
                                val isActionsRevealed = (revealedActionFileName == file.name)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isSelected) Color(0x288B5CF6) else Color(0x08FFFFFF)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) NeonPurpleLight.copy(alpha = 0.6f) else Color.Transparent,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                if (isActionsRevealed) {
                                                    onRevealActions(null)
                                                } else {
                                                    onSelectFile(file)
                                                }
                                            },
                                            onLongClick = {
                                                onRevealActions(if (isActionsRevealed) null else file.name)
                                            }
                                        )
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Play / Playing badge
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(if (isPlayingThis) NeonCyan else Color(0x20A78BFA)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Lecture",
                                            tint = if (isPlayingThis) Color(0xFF003844) else NeonPurpleLight,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // File Details
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${file.beats}T · ${file.bpm} BPM · ${file.duration}",
                                            fontSize = 9.5.sp,
                                            color = if (isSelected) NeonCyanLight else TextDim
                                        )
                                    }

                                    // Contextual Actions on Long Press: Edit (pen) and Delete (trash)
                                    AnimatedVisibility(
                                        visible = isActionsRevealed,
                                        enter = fadeIn() + expandHorizontally(),
                                        exit = fadeOut() + shrinkHorizontally()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Edit Button (Pen) en carré bordure néon petit et adapté
                                            Box(
                                                modifier = Modifier
                                                    .padding(horizontal = 2.dp)
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0x2222D3EE))
                                                    .border(1.dp, NeonCyan.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                                                    .clickable { onOpenEdit(file) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Éditer",
                                                    tint = NeonCyanLight,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }

                                            // Delete Button (Trash) en carré bordure néon petit et adapté
                                            Box(
                                                modifier = Modifier
                                                    .padding(horizontal = 2.dp)
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0x22FB4570))
                                                    .border(1.dp, MuteRed.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                                                    .clickable { onConfirmDelete(file) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Supprimer",
                                                    tint = MuteRed,
                                                    modifier = Modifier.size(15.dp)
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
}

/**
 * LoopEditorContent:
 * Waveform display with interactive start/end trim drag handles,
 * Beat/Time settings stepper (2 to 64 with odd numbers: [ - ] [ number ] [ + ]),
 * Step selection (Step Début / Step Fin and interactive step sequencer grid),
 * and Overwrite / Save Copy actions.
 */
@Composable
private fun LoopEditorContent(
    file: LoopFile,
    beats: Int,
    onUpdateBeats: (Int) -> Unit,
    startMs: Int,
    endMs: Int,
    onUpdateTrims: (Int, Int) -> Unit,
    startStep: Int,
    endStep: Int,
    onUpdateSteps: (Int, Int) -> Unit,
    onOverwrite: () -> Unit,
    onSaveCopy: () -> Unit,
    isLoopPlaying: Boolean = false,
    onTogglePlay: () -> Unit = {},
    onBack: () -> Unit
) {
    val totalSteps = (beats * 4).coerceIn(8, 256)
    val safeBpm = if (file.bpm > 20) file.bpm else 120
    val totalDurationMs = remember(beats, file.bpm) {
        ((beats * 60_000L) / safeBpm).toInt().coerceAtLeast(400)
    }

    var localStartFrac by remember(file.name) {
        val frac = if (startMs > 0 && totalDurationMs > 0) {
            (startMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 0.95f)
        } else {
            0f
        }
        mutableFloatStateOf(frac)
    }

    var localEndFrac by remember(file.name) {
        val frac = if (endMs > 0 && totalDurationMs > 0 && endMs > startMs) {
            (endMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0.05f, 1f)
        } else {
            1f
        }
        mutableFloatStateOf(frac)
    }

    // Helper to commit trims smoothly in real-time
    val updateTrimsSmooth = { sFrac: Float, eFrac: Float ->
        val clampedS = sFrac.coerceIn(0f, 0.98f)
        val clampedE = eFrac.coerceIn(clampedS + 0.015f, 1.0f)
        localStartFrac = clampedS
        localEndFrac = clampedE
        val newStartMs = (clampedS * totalDurationMs).toInt()
        val newEndMs = (clampedE * totalDurationMs).toInt()
        val nominalStartStep = (1 + (clampedS * totalSteps)).toInt().coerceIn(1, totalSteps - 1)
        val nominalEndStep = (clampedE * totalSteps).toInt().coerceIn(nominalStartStep + 1, totalSteps)
        onUpdateTrims(newStartMs, newEndMs)
        onUpdateSteps(nominalStartStep, nominalEndStep)
    }

    val currentStartSec = (localStartFrac * totalDurationMs) / 1000f
    val currentEndSec = (localEndFrac * totalDurationMs) / 1000f
    val currentLoopDurationSec = (currentEndSec - currentStartSec).coerceAtLeast(0.01f)

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // ================= DJ EDITOR TOP BAR =================
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // Bouton retour en carré bordure néon petit et adapté
                Box(
                    modifier = Modifier
                        .padding(end = 2.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x22FFFFFF))
                        .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(8.dp))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Column {
                    Text(
                        text = "Édition de Boucle DJ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${file.name} · $safeBpm BPM",
                        fontSize = 10.sp,
                        color = NeonCyanLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right Actions: [ Play/Pause ] [ Écraser ] [ Copie ] [ x carré néon ]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Bouton Play / Pause DJ
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isLoopPlaying) Color(0x3322D3EE) else Color(0x22FFFFFF))
                        .border(1.dp, if (isLoopPlaying) NeonCyan else Color(0x66FFFFFF), RoundedCornerShape(8.dp))
                        .clickable(onClick = onTogglePlay)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isLoopPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isLoopPlaying) "Pause" else "Play",
                            tint = if (isLoopPlaying) NeonCyanLight else Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isLoopPlaying) "Pause" else "Play",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLoopPlaying) NeonCyanLight else Color.White
                        )
                    }
                }

                // Bouton Écraser le fichier
                Button(
                    onClick = onOverwrite,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9333EA)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Écraser",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Écraser",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Bouton Enregistrer une copie
                Button(
                    onClick = onSaveCopy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copie",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Copie",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Bouton X en carré bordure néon petit et adapté
                Box(
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x22FFFFFF))
                        .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(8.dp))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fermer",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ================= TEMPO & ROLLS DJ BAR =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x14FFFFFF))
                .border(1.dp, Color(0x1EFFFFFF), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Stepper Temps / Beats
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "TEMPS:",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonCyan,
                    letterSpacing = 0.5.sp
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x18FFFFFF))
                        .clickable(enabled = beats > 2) {
                            onUpdateBeats((beats - 1).coerceIn(2, 64))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Moins", tint = NeonPurpleLight, modifier = Modifier.size(13.dp))
                }
                Text(
                    text = "$beats T",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 3.dp)
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x18FFFFFF))
                        .clickable(enabled = beats < 64) {
                            onUpdateBeats((beats + 1).coerceIn(2, 64))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Plus", tint = NeonPurpleLight, modifier = Modifier.size(13.dp))
                }
            }

            // DJ Quick Loop Roll buttons: [ 1/4 ] [ 1/2 ] [ 1T ] [ 2T ] [ 4T ] [ /2 ] [ x2 ] [ TOUT ]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Half / Double loop
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x20A78BFA))
                        .border(1.dp, NeonPurpleLight.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .clickable {
                            val currentLen = localEndFrac - localStartFrac
                            updateTrimsSmooth(localStartFrac, localStartFrac + (currentLen / 2f).coerceAtLeast(0.015f))
                        }
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("/2", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NeonPurpleLight)
                }

                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x20A78BFA))
                        .border(1.dp, NeonPurpleLight.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .clickable {
                            val currentLen = localEndFrac - localStartFrac
                            updateTrimsSmooth(localStartFrac, (localStartFrac + (currentLen * 2f)).coerceAtMost(1f))
                        }
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("x2", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NeonPurpleLight)
                }

                // 1 Beat Loop
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x2022D3EE))
                        .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .clickable {
                            val oneBeatFrac = 1f / beats.toFloat()
                            updateTrimsSmooth(localStartFrac, (localStartFrac + oneBeatFrac).coerceAtMost(1f))
                        }
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("1T", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = NeonCyanLight)
                }

                // 2 Beats Loop
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x2022D3EE))
                        .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .clickable {
                            val twoBeatsFrac = 2f / beats.toFloat()
                            updateTrimsSmooth(localStartFrac, (localStartFrac + twoBeatsFrac).coerceAtMost(1f))
                        }
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("2T", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = NeonCyanLight)
                }

                // 4 Beats Loop
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x2022D3EE))
                        .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .clickable {
                            val fourBeatsFrac = 4f / beats.toFloat()
                            updateTrimsSmooth(localStartFrac, (localStartFrac + fourBeatsFrac).coerceAtMost(1f))
                        }
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("4T", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = NeonCyanLight)
                }

                // Reset Tout
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x20FFFFFF))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                        .clickable {
                            updateTrimsSmooth(0f, 1f)
                        }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("TOUT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ================= DJ LOOP MONITOR (IN / LONGUEUR / OUT) =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x10FFFFFF))
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // IN (Début) + micro boutons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "IN :",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonCyanLight
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x2222D3EE))
                        .clickable {
                            updateTrimsSmooth(localStartFrac - 0.01f, localEndFrac)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Moins", tint = NeonCyanLight, modifier = Modifier.size(12.dp))
                }
                Text(
                    text = String.format(java.util.Locale.US, "%.2fs (%d%%)", currentStartSec, (localStartFrac * 100).toInt()),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x2222D3EE))
                        .clickable {
                            updateTrimsSmooth(localStartFrac + 0.01f, localEndFrac)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Plus", tint = NeonCyanLight, modifier = Modifier.size(12.dp))
                }
            }

            // Centre: Durée de Boucle (0ms coupure DJ)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Boucle",
                    tint = NeonPurpleLight,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = String.format(java.util.Locale.US, "%.2fs (DJ 0ms)", currentLoopDurationSec),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }

            // OUT (Fin) + micro boutons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "OUT :",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonPink
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x22FB4570))
                        .clickable {
                            updateTrimsSmooth(localStartFrac, localEndFrac - 0.01f)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Moins", tint = NeonPink, modifier = Modifier.size(12.dp))
                }
                Text(
                    text = String.format(java.util.Locale.US, "%.2fs (%d%%)", currentEndSec, (localEndFrac * 100).toInt()),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonPink
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x22FB4570))
                        .clickable {
                            updateTrimsSmooth(localStartFrac, localEndFrac + 0.01f)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Plus", tint = NeonPink, modifier = Modifier.size(12.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ================= FORME D'ONDE DJ TACTILE LIBRE =================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0C0F17))
                .border(1.dp, Color(0x33A78BFA), RoundedCornerShape(14.dp))
        ) {
            WaveformDisplay(
                modifier = Modifier.fillMaxSize(),
                seed = file.name.hashCode(),
                startFraction = localStartFrac,
                endFraction = localEndFrac,
                onStartDrag = { frac ->
                    updateTrimsSmooth(frac, localEndFrac)
                },
                onEndDrag = { frac ->
                    updateTrimsSmooth(localStartFrac, frac)
                },
                onTapPoint = { touchFrac ->
                    val distToStart = kotlin.math.abs(touchFrac - localStartFrac)
                    val distToEnd = kotlin.math.abs(touchFrac - localEndFrac)
                    if (distToStart < distToEnd) {
                        updateTrimsSmooth(touchFrac, localEndFrac)
                    } else {
                        updateTrimsSmooth(localStartFrac, touchFrac)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ================= GRILLE BEAT GRID DJ =================
        StepSequencerStrip(
            totalSteps = totalSteps,
            startStep = (1 + (localStartFrac * totalSteps)).toInt().coerceIn(1, totalSteps - 1),
            endStep = (localEndFrac * totalSteps).toInt().coerceIn(2, totalSteps),
            onSelectStep = { step ->
                val frac = (step.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
                val distToStart = kotlin.math.abs(frac - localStartFrac)
                val distToEnd = kotlin.math.abs(frac - localEndFrac)
                if (distToStart <= distToEnd) {
                    updateTrimsSmooth(frac, localEndFrac)
                } else {
                    updateTrimsSmooth(localStartFrac, frac)
                }
            }
        )
    }
}

/**
 * StepSequencerStrip:
 * Horizontal scrollable beat grid helper (1 .. totalSteps).
 */
@Composable
private fun StepSequencerStrip(
    totalSteps: Int,
    startStep: Int,
    endStep: Int,
    onSelectStep: (Int) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x18000000))
            .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(totalSteps) { index ->
            val stepNumber = index + 1
            val isInside = stepNumber in startStep..endStep
            val isStart = stepNumber == startStep
            val isEnd = stepNumber == endStep
            val isBeatAccent = (index % 4 == 0)

            Box(
                modifier = Modifier
                    .width(26.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            isStart -> NeonCyan
                            isEnd -> NeonPink
                            isInside -> Color(0x558B5CF6)
                            isBeatAccent -> Color(0x20FFFFFF)
                            else -> Color(0x0EFFFFFF)
                        }
                    )
                    .border(
                        width = if (isStart || isEnd) 1.5.dp else 1.dp,
                        color = when {
                            isStart -> Color.White
                            isEnd -> Color.White
                            isInside -> NeonPurpleLight.copy(alpha = 0.6f)
                            else -> Color(0x12FFFFFF)
                        },
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelectStep(stepNumber) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$stepNumber",
                    fontSize = 9.sp,
                    fontWeight = if (isInside) FontWeight.Bold else FontWeight.Normal,
                    color = if (isStart || isEnd) Color.Black else if (isInside) Color.White else TextDim2
                )
            }
        }
    }
}

/**
 * WaveformDisplay:
 * Draws DJ waveform with free touchable placement, smooth dragging without steps,
 * neon-bordered square IN/OUT flags, and instant sample-accurate visual feedback.
 */
@Composable
private fun WaveformDisplay(
    modifier: Modifier = Modifier,
    seed: Int,
    startFraction: Float,
    endFraction: Float,
    onStartDrag: (Float) -> Unit,
    onEndDrag: (Float) -> Unit,
    onTapPoint: (Float) -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val density = androidx.compose.ui.platform.LocalDensity.current

        // Background Waveform & Touch Surface
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(startFraction, endFraction, widthPx) {
                    detectTapGestures { offset ->
                        val touchFrac = (offset.x / widthPx).coerceIn(0f, 1f)
                        onTapPoint(touchFrac)
                    }
                }
                .pointerInput(startFraction, endFraction, widthPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val currentTouchFrac = (change.position.x / widthPx).coerceIn(0f, 1f)
                        val distToStart = kotlin.math.abs(currentTouchFrac - startFraction)
                        val distToEnd = kotlin.math.abs(currentTouchFrac - endFraction)
                        if (distToStart < distToEnd) {
                            val newStart = (startFraction + dragAmount.x / widthPx).coerceIn(0f, 0.98f)
                            onStartDrag(newStart)
                        } else {
                            val newEnd = (endFraction + dragAmount.x / widthPx).coerceIn(0.02f, 1.0f)
                            onEndDrag(newEnd)
                        }
                    }
                }
        ) {
            val barCount = 80
            val barWidth = size.width / barCount
            val centerY = size.height / 2f

            for (i in 0 until barCount) {
                val progress = i.toFloat() / barCount.toFloat()
                val isInsideLoop = progress in startFraction..endFraction

                // Dynamic DJ waveform amplitude
                val h1 = kotlin.math.abs(sin(i * 0.38f + (seed % 7)))
                val h2 = kotlin.math.abs(sin(i * 0.92f + 1.4f))
                val barHeight = ((h1 * 0.65f + h2 * 0.35f) * (size.height * 0.74f)).coerceAtLeast(4f)

                val barColor = if (isInsideLoop) {
                    NeonCyanLight.copy(alpha = 0.9f)
                } else {
                    Color(0x28FFFFFF)
                }

                drawRect(
                    color = barColor,
                    topLeft = Offset(i * barWidth + barWidth * 0.15f, centerY - barHeight / 2f),
                    size = Size(barWidth * 0.7f, barHeight)
                )
            }

            // Darken outside loop regions
            if (startFraction > 0f) {
                drawRect(
                    color = Color(0x77000000),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width * startFraction, size.height)
                )
            }
            if (endFraction < 1f) {
                drawRect(
                    color = Color(0x77000000),
                    topLeft = Offset(size.width * endFraction, 0f),
                    size = Size(size.width * (1f - endFraction), size.height)
                )
            }

            // Loop active window highlight tint
            val loopWidth = size.width * (endFraction - startFraction).coerceAtLeast(0f)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0x2222D3EE),
                        Color(0x22A78BFA),
                        Color(0x22FB4570)
                    ),
                    startX = size.width * startFraction,
                    endX = size.width * endFraction
                ),
                topLeft = Offset(size.width * startFraction, 0f),
                size = Size(loopWidth, size.height)
            )
        }

        // ================= START TRIM HANDLE (IN) =================
        Box(
            modifier = Modifier
                .offset {
                    val handleHalfWidthPx = with(density) { 16.dp.toPx() }
                    androidx.compose.ui.unit.IntOffset(
                        (widthPx * startFraction - handleHalfWidthPx).toInt(),
                        0
                    )
                }
                .fillMaxHeight()
                .width(32.dp)
                .pointerInput(startFraction, widthPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newFrac = (startFraction + dragAmount.x / widthPx).coerceIn(0f, 0.98f)
                        onStartDrag(newFrac)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top IN Flag: Carré bordure néon petit et adapté
                Box(
                    modifier = Modifier
                        .size(20.dp, 16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF083344))
                        .border(1.2.dp, NeonCyan, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("IN", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = NeonCyanLight)
                }

                // Vertical line
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .weight(1f)
                        .background(NeonCyan)
                )

                // Bottom IN Flag: Carré bordure néon petit et adapté
                Box(
                    modifier = Modifier
                        .size(20.dp, 16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF083344))
                        .border(1.2.dp, NeonCyan, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("IN", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = NeonCyanLight)
                }
            }
        }

        // ================= END TRIM HANDLE (OUT) =================
        Box(
            modifier = Modifier
                .offset {
                    val handleHalfWidthPx = with(density) { 16.dp.toPx() }
                    androidx.compose.ui.unit.IntOffset(
                        (widthPx * endFraction - handleHalfWidthPx).toInt(),
                        0
                    )
                }
                .fillMaxHeight()
                .width(32.dp)
                .pointerInput(endFraction, widthPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newFrac = (endFraction + dragAmount.x / widthPx).coerceIn(0.02f, 1.0f)
                        onEndDrag(newFrac)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top OUT Flag: Carré bordure néon petit et adapté
                Box(
                    modifier = Modifier
                        .size(22.dp, 16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF4C0519))
                        .border(1.2.dp, NeonPink, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("OUT", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = NeonPink)
                }

                // Vertical line
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .weight(1f)
                        .background(NeonPink)
                )

                // Bottom OUT Flag: Carré bordure néon petit et adapté
                Box(
                    modifier = Modifier
                        .size(22.dp, 16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF4C0519))
                        .border(1.2.dp, NeonPink, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("OUT", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = NeonPink)
                }
            }
        }
    }
}
