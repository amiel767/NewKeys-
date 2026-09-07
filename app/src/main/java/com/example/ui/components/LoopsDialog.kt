package com.example.ui.components

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
import com.example.model.LoopFile
import com.example.model.LoopFolder
import com.example.ui.theme.*
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

                // Bouton + (Importer) en plus compact (28dp)
                IconButton(
                    onClick = onImportLoop,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x2622D3EE))
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Importer",
                        tint = NeonCyanLight,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Bouton x (Fermer) en plus compact (28dp)
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x20FFFFFF))
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
                                            // Edit Button (Pen)
                                            IconButton(
                                                onClick = { onOpenEdit(file) },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0x2822D3EE))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Éditer",
                                                    tint = NeonCyan,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }

                                            // Delete Button (Trash)
                                            IconButton(
                                                onClick = { onConfirmDelete(file) },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0x28FB4570))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Supprimer",
                                                    tint = MuteRed,
                                                    modifier = Modifier.size(17.dp)
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
    onBack: () -> Unit
) {
    val totalSteps = (beats * 4).coerceIn(8, 256)
    val currentStartStep = startStep.coerceIn(1, totalSteps - 1)
    val currentEndStep = endStep.coerceIn(currentStartStep + 1, totalSteps)

    var localStartFrac by remember(file.name, currentStartStep, totalSteps) {
        mutableFloatStateOf((currentStartStep - 1).toFloat() / totalSteps.toFloat())
    }
    var localEndFrac by remember(file.name, currentEndStep, totalSteps) {
        mutableFloatStateOf(currentEndStep.toFloat() / totalSteps.toFloat())
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Editor Top Bar with Compact Back, Overwrite, Copy, and Close buttons
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
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x20FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column {
                    Text(
                        text = "Édition de Boucle",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = file.name,
                        fontSize = 10.5.sp,
                        color = NeonCyanLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Action Buttons: [ Écraser ] [ Copie ] [ x compact ]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Button Écraser le fichier
                Button(
                    onClick = onOverwrite,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9333EA)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Écraser",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Écraser",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Button Enregistrer une copie
                Button(
                    onClick = onSaveCopy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Enregistrer une copie",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Copie",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Compact Close Button (28dp)
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x20FFFFFF))
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

        Spacer(modifier = Modifier.height(10.dp))

        // Time / Beat Stepper: 2 to 64 with odd numbers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x14FFFFFF))
                .border(1.dp, Color(0x1EFFFFFF), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "RÉGLAGE DE TEMPS (TEMPS / BEATS)",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "De 2 à 64 (impairs inclus) • $totalSteps steps totaux",
                    fontSize = 10.sp,
                    color = TextDim
                )
            }

            // Compact Stepper: [ - ] [ nombre ] [ + ]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (beats > 2) Color(0x288B5CF6) else Color(0x10FFFFFF))
                        .border(1.dp, if (beats > 2) NeonPurpleLight else Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                        .clickable(enabled = beats > 2) {
                            val next = (beats - 1).coerceIn(2, 64)
                            onUpdateBeats(next)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Diminuer",
                        tint = if (beats > 2) NeonPurpleLight else TextDim2,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .widthIn(min = 38.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x22000000))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$beats",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (beats < 64) Color(0x288B5CF6) else Color(0x10FFFFFF))
                        .border(1.dp, if (beats < 64) NeonPurpleLight else Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                        .clickable(enabled = beats < 64) {
                            val next = (beats + 1).coerceIn(2, 64)
                            onUpdateBeats(next)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Augmenter",
                        tint = if (beats < 64) NeonPurpleLight else TextDim2,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Step Start / End Selection Panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x14FFFFFF))
                .border(1.dp, Color(0x1EFFFFFF), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Step Début
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "DÉBUT :",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyanLight
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x18FFFFFF))
                        .clickable(enabled = currentStartStep > 1) {
                            val next = (currentStartStep - 1).coerceAtLeast(1)
                            onUpdateSteps(next, currentEndStep)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Moins", tint = NeonCyanLight, modifier = Modifier.size(14.dp))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x26000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Step $currentStartStep",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonCyanLight
                    )
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x18FFFFFF))
                        .clickable(enabled = currentStartStep < currentEndStep - 1) {
                            val next = (currentStartStep + 1).coerceAtMost(currentEndStep - 1)
                            onUpdateSteps(next, currentEndStep)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Plus", tint = NeonCyanLight, modifier = Modifier.size(14.dp))
                }
            }

            // Step Fin
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "FIN :",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonPink
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x18FFFFFF))
                        .clickable(enabled = currentEndStep > currentStartStep + 1) {
                            val next = (currentEndStep - 1).coerceAtLeast(currentStartStep + 1)
                            onUpdateSteps(currentStartStep, next)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Moins", tint = NeonPink, modifier = Modifier.size(14.dp))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x26000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Step $currentEndStep",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonPink
                    )
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x18FFFFFF))
                        .clickable(enabled = currentEndStep < totalSteps) {
                            val next = (currentEndStep + 1).coerceAtMost(totalSteps)
                            onUpdateSteps(currentStartStep, next)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Plus", tint = NeonPink, modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Interactive Step Sequencer Strip (1 .. totalSteps)
        StepSequencerStrip(
            totalSteps = totalSteps,
            startStep = currentStartStep,
            endStep = currentEndStep,
            onSelectStep = { step ->
                if (step < currentStartStep) {
                    onUpdateSteps(step, currentEndStep)
                } else if (step > currentEndStep) {
                    onUpdateSteps(currentStartStep, step)
                } else {
                    val distToStart = kotlin.math.abs(step - currentStartStep)
                    val distToEnd = kotlin.math.abs(step - currentEndStep)
                    if (distToStart <= distToEnd && step < currentEndStep) {
                        onUpdateSteps(step, currentEndStep)
                    } else if (step > currentStartStep) {
                        onUpdateSteps(currentStartStep, step)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Waveform Display Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "FORME D'ONDE & REPÈRES TEMPORELS",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextDim,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Glisser les repères cyan et rose",
                fontSize = 9.sp,
                color = NeonCyanLight
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Waveform Display with start/end trim drag handles
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0F121C))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
        ) {
            WaveformDisplay(
                modifier = Modifier.fillMaxSize(),
                seed = file.name.hashCode(),
                startFraction = localStartFrac,
                endFraction = localEndFrac,
                onStartDrag = { frac ->
                    localStartFrac = frac.coerceIn(0f, localEndFrac - 0.05f)
                    val calculatedStep = (1 + (localStartFrac * totalSteps)).toInt().coerceIn(1, currentEndStep - 1)
                    onUpdateSteps(calculatedStep, currentEndStep)
                },
                onEndDrag = { frac ->
                    localEndFrac = frac.coerceIn(localStartFrac + 0.05f, 1.0f)
                    val calculatedStep = (localEndFrac * totalSteps).toInt().coerceIn(currentStartStep + 1, totalSteps)
                    onUpdateSteps(currentStartStep, calculatedStep)
                }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Trim positions summary indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Début : Step $currentStartStep (${(localStartFrac * 100).toInt()}%)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = NeonCyan
            )
            Text(
                text = "Boucle : ${currentEndStep - currentStartStep + 1} steps",
                fontSize = 10.sp,
                color = TextDim
            )
            Text(
                text = "Fin : Step $currentEndStep (${(localEndFrac * 100).toInt()}%)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = NeonPink
            )
        }
    }
}

/**
 * StepSequencerStrip:
 * Horizontal scrollable step buttons (1 .. totalSteps).
 * Highlights selected loop segment from startStep to endStep.
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
                    .height(30.dp)
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$stepNumber",
                        fontSize = 9.sp,
                        fontWeight = if (isInside) FontWeight.Bold else FontWeight.Normal,
                        color = if (isStart || isEnd) Color.Black else if (isInside) Color.White else TextDim2
                    )
                    if (isStart) {
                        Text(
                            text = "IN",
                            fontSize = 6.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                    } else if (isEnd) {
                        Text(
                            text = "OUT",
                            fontSize = 6.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * WaveformDisplay:
 * Draws procedural audio waveforms and provides draggable Start & End handles.
 */
@Composable
private fun WaveformDisplay(
    modifier: Modifier = Modifier,
    seed: Int,
    startFraction: Float,
    endFraction: Float,
    onStartDrag: (Float) -> Unit,
    onEndDrag: (Float) -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val density = androidx.compose.ui.platform.LocalDensity.current

        // Background Waveform Bars
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barCount = 70
            val barWidth = size.width / barCount
            val centerY = size.height / 2f

            for (i in 0 until barCount) {
                val progress = i.toFloat() / barCount.toFloat()
                val isInsideLoop = progress in startFraction..endFraction

                // Synthetic harmonic waveform height
                val h1 = kotlin.math.abs(sin((i * 0.35f + (seed % 10))))
                val h2 = kotlin.math.abs(sin(i * 0.85f + 1.2f))
                val barHeight = ((h1 * 0.6f + h2 * 0.4f) * (size.height * 0.76f)).coerceAtLeast(4f)

                val barColor = if (isInsideLoop) {
                    NeonCyanLight.copy(alpha = 0.85f)
                } else {
                    Color(0x28FFFFFF)
                }

                drawRect(
                    color = barColor,
                    topLeft = Offset(i * barWidth + barWidth * 0.2f, centerY - barHeight / 2f),
                    size = Size(barWidth * 0.6f, barHeight)
                )
            }

            // Darken outside regions
            if (startFraction > 0f) {
                drawRect(
                    color = Color(0x66000000),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width * startFraction, size.height)
                )
            }
            if (endFraction < 1f) {
                drawRect(
                    color = Color(0x66000000),
                    topLeft = Offset(size.width * endFraction, 0f),
                    size = Size(size.width * (1f - endFraction), size.height)
                )
            }
        }

        // Start Trim Handle (Draggable)
        Box(
            modifier = Modifier
                .offset {
                    val handleHalfWidthPx = with(density) { 14.dp.toPx() }
                    androidx.compose.ui.unit.IntOffset(
                        (widthPx * startFraction - handleHalfWidthPx).toInt(),
                        0
                    )
                }
                .fillMaxHeight()
                .width(28.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newFrac = (startFraction + dragAmount.x / widthPx).coerceIn(0f, 0.95f)
                        onStartDrag(newFrac)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(NeonCyan)
                        .border(1.5.dp, Color.White, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .weight(1f)
                        .background(NeonCyan)
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(NeonCyan)
                        .border(1.5.dp, Color.White, CircleShape)
                )
            }
        }

        // End Trim Handle (Draggable)
        Box(
            modifier = Modifier
                .offset {
                    val handleHalfWidthPx = with(density) { 14.dp.toPx() }
                    androidx.compose.ui.unit.IntOffset(
                        (widthPx * endFraction - handleHalfWidthPx).toInt(),
                        0
                    )
                }
                .fillMaxHeight()
                .width(28.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newFrac = (endFraction + dragAmount.x / widthPx).coerceIn(0.05f, 1.0f)
                        onEndDrag(newFrac)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(NeonPink)
                        .border(1.5.dp, Color.White, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .weight(1f)
                        .background(NeonPink)
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(NeonPink)
                        .border(1.5.dp, Color.White, CircleShape)
                )
            }
        }
    }
}
