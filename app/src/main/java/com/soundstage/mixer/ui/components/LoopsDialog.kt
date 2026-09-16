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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    bpm: Int = 120,
    onUpdateBpm: (Int) -> Unit = {},
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
    onRenameFile: (LoopFile, String) -> Unit = { _, _ -> },
    onImportLoop: () -> Unit,
    lastPath: String = "",
    onUpdateLastPath: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isNativeBrowserOpen by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    val isEditing = editingLoopFile != null
    val targetWidth by animateDpAsState(
        targetValue = if (isEditing) 720.dp else 660.dp,
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
                .padding(12.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (isEditing) onCloseEditFile() else onClose()
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = modifier
                    .widthIn(min = 400.dp, max = targetWidth)
                    .fillMaxWidth(if (isEditing) 0.95f else 0.90f)
                    .heightIn(min = 340.dp, max = 460.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, Color(0x33A78BFA), RoundedCornerShape(22.dp))
                    .shadow(16.dp, RoundedCornerShape(22.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
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
                            onRenameFile = onRenameFile,
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
                            bpm = bpm,
                            onUpdateBpm = onUpdateBpm,
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
                            onImportLoop = { isNativeBrowserOpen = true },
                            onClose = onClose
                        )
                    }
                }
            }
        }
    }

    if (isNativeBrowserOpen) {
        NativeFileBrowserDialog(
            isOpen = isNativeBrowserOpen,
            onClose = { isNativeBrowserOpen = false },
            initialPath = lastPath.ifEmpty { "/storage/emulated/0/SoundStage/Loops" },
            title = "Explorateur de Loops Audio",
            onPathChanged = { newPath -> onUpdateLastPath(newPath) },
            onFileSelected = { file ->
                val imported = LoopFile(
                    name = file.name,
                    duration = "Audio",
                    folder = file.parentFile?.name ?: "Stockage",
                    beats = selectedBeats.coerceAtLeast(2),
                    path = file.absolutePath
                )
                onSelectFile(imported)
                isNativeBrowserOpen = false
            }
        )
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
    bpm: Int = 120,
    onUpdateBpm: (Int) -> Unit = {},
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
    var isTempsActive by remember(selectedBeats) { mutableStateOf(selectedBeats > 0) }
    val evenBeats = if (selectedBeats > 0) {
        if (selectedBeats % 2 != 0) (selectedBeats + 1).coerceIn(2, 64) else selectedBeats
    } else 4

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
                Text(
                    text = "Loops",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Right Actions: [ Case Réglage Temps ] [ + compact ] [ x compact ]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Case de réglage de temps (Temps / Beats)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isTempsActive) Color(0x2800E5FF) else Color(0x10FFFFFF))
                        .border(
                            1.dp,
                            if (isTempsActive) Color(0x6600E5FF) else Color(0x1AFFFFFF),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isTempsActive) Color(0x18FFFFFF) else Color(0x08FFFFFF))
                            .clickable(enabled = isTempsActive && evenBeats > 2) {
                                val prevEven = evenBeats - 2
                                onSelectBeats(prevEven.coerceIn(2, 64))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Diminuer temps",
                            tint = if (isTempsActive && evenBeats > 2) NeonCyanLight else Color(0x33FFFFFF),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isTempsActive) Color(0x3300E5FF) else Color(0x0CFFFFFF))
                            .clickable {
                                if (isTempsActive) {
                                    isTempsActive = false
                                    onSelectBeats(0)
                                } else {
                                    isTempsActive = true
                                    onSelectBeats(evenBeats)
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isTempsActive) "${evenBeats}T" else "OFF",
                            fontSize = 11.5.sp,
                            fontWeight = if (isTempsActive) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isTempsActive) Color.White else Color(0x55FFFFFF)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isTempsActive) Color(0x18FFFFFF) else Color(0x08FFFFFF))
                            .clickable(enabled = isTempsActive && evenBeats < 64) {
                                val nextEven = evenBeats + 2
                                onSelectBeats(nextEven.coerceIn(2, 64))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Augmenter temps",
                            tint = if (isTempsActive && evenBeats < 64) NeonCyanLight else Color(0x33FFFFFF),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Mini Volume Slider in Header
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x18FFFFFF))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .width(120.dp)
                        .height(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Volume",
                        tint = NeonPurpleLight,
                        modifier = Modifier.size(14.dp)
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
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.width(28.dp),
                        textAlign = TextAlign.End
                    )
                }

                // Bouton Parcourir Stockage & SD (Explorateur de fichiers)
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
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Parcourir Stockage / SD",
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

        Spacer(modifier = Modifier.height(10.dp))

        // 2-PANE EXPLORER: DOSSIERS (Gauche) + FICHIERS AUDIO & METADATA (Droite)
        var selectedFolderIndex by remember { mutableStateOf(0) }
        val activeFolder = loopFolders.getOrNull(selectedFolderIndex.coerceIn(0, (loopFolders.size - 1).coerceAtLeast(0)))
        val filesToShow = activeFolder?.files ?: loopFolders.flatMap { it.files }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ================= PANE 1 (GAUCHE) : DOSSIERS =================
            Column(
                modifier = Modifier
                    .width(185.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x0EFFFFFF))
                    .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(16.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "DOSSIERS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonPurpleLight,
                        letterSpacing = 0.6.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x188B5CF6))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${loopFolders.size}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonPurpleLight
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(loopFolders) { index, folder ->
                        val isFolderSelected = (index == selectedFolderIndex)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isFolderSelected) Color(0x2E8B5CF6) else Color(0x06FFFFFF)
                                )
                                .border(
                                    1.dp,
                                    if (isFolderSelected) NeonPurpleLight.copy(alpha = 0.7f) else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedFolderIndex = index
                                    onToggleFolder(folder.name)
                                }
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isFolderSelected) Icons.Default.FolderOpen else Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (isFolderSelected) NeonPurpleLight else TextDim,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = folder.name,
                                fontSize = 11.5.sp,
                                fontWeight = if (isFolderSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isFolderSelected) Color.White else TextPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${folder.files.size}",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFolderSelected) NeonCyanLight else TextDim2
                            )
                        }
                    }
                }
            }

            // ================= PANE 2 (DROITE) : FICHIERS & MÉTADONNÉES =================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x0EFFFFFF))
                    .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(16.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "FICHIERS (${activeFolder?.name ?: "TOUS"})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonCyanLight,
                        letterSpacing = 0.6.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${filesToShow.size} élément${if (filesToShow.size > 1) "s" else ""}",
                        fontSize = 9.5.sp,
                        color = TextDim
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (filesToShow.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aucun fichier audio dans ce dossier",
                            fontSize = 11.sp,
                            color = TextDim2
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filesToShow) { file ->
                            val isPlayingThis = (activeLoopFile?.name == file.name && isLoopPlaying)
                            val isSelected = (activeLoopFile?.name == file.name)
                            val isActionsRevealed = (revealedActionFileName == file.name)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) Color(0x288B5CF6) else Color(0x06FFFFFF)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) NeonPurpleLight.copy(alpha = 0.6f) else Color.Transparent,
                                        RoundedCornerShape(12.dp)
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
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Play / Pause Button
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(if (isPlayingThis) NeonCyan else Color(0x20A78BFA)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Lecture",
                                        tint = if (isPlayingThis) Color(0xFF003844) else NeonPurpleLight,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                // File Details
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (file.beats > 0) "${file.beats}T · ${file.duration}" else file.duration,
                                        fontSize = 9.sp,
                                        color = if (isSelected) NeonCyanLight else TextDim
                                    )
                                }

                                // Metadata Badges (Key & BPM)
                                AnimatedVisibility(
                                    visible = !isActionsRevealed,
                                    enter = fadeIn() + expandHorizontally(),
                                    exit = fadeOut() + shrinkHorizontally()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        if (file.musicalKey.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(if (isSelected) Color(0x338B5CF6) else Color(0x18FFFFFF))
                                                    .border(0.8.dp, if (isSelected) Color(0xFFA78BFA) else Color(0x22FFFFFF), RoundedCornerShape(5.dp))
                                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                            ) {
                                                Text(
                                                    text = file.musicalKey,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isSelected) Color(0xFFDDD6FE) else Color(0xCCFFFFFF)
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(if (isSelected) Color(0x3300E5FF) else Color(0x14FFFFFF))
                                                .border(0.8.dp, if (isSelected) Color(0xFF00E5FF) else Color(0x20FFFFFF), RoundedCornerShape(5.dp))
                                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        ) {
                                            Text(
                                                text = "${file.bpm} BPM",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color(0xFF00E5FF) else Color(0xAAFFFFFF)
                                            )
                                        }
                                    }
                                }

                                // Actions (Edit / Delete)
                                AnimatedVisibility(
                                    visible = isActionsRevealed,
                                    enter = fadeIn() + expandHorizontally(),
                                    exit = fadeOut() + shrinkHorizontally()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0x2222D3EE))
                                                .border(1.dp, NeonCyan.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                                                .clickable { onOpenEdit(file) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Éditer",
                                                tint = NeonCyanLight,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0x22FB4570))
                                                .border(1.dp, MuteRed.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                                                .clickable { onConfirmDelete(file) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Supprimer",
                                                tint = MuteRed,
                                                modifier = Modifier.size(13.dp)
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
    onRenameFile: (LoopFile, String) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember(file.name) { mutableStateOf(file.name.substringBeforeLast('.')) }

    var isMagnetMode by remember { mutableStateOf(false) }
    var isTempsEnabled by remember { mutableStateOf(false) }

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

    // Snapping helper when in Magnet (Aimant) mode
    val snapFrac = { rawFrac: Float ->
        if (isMagnetMode) {
            val sliceCount = (beats * 2).coerceIn(4, 64)
            val step = 1f / sliceCount.toFloat()
            (kotlin.math.round(rawFrac / step) * step).coerceIn(0f, 1f)
        } else {
            rawFrac
        }
    }

    // Helper to commit trims smoothly in real-time
    val updateTrimsSmooth = { sFrac: Float, eFrac: Float ->
        val snappedS = snapFrac(sFrac).coerceIn(0f, 0.98f)
        val snappedE = snapFrac(eFrac).coerceIn(snappedS + 0.015f, 1.0f)
        localStartFrac = snappedS
        localEndFrac = snappedE
        val newStartMs = (snappedS * totalDurationMs).toInt()
        val newEndMs = (snappedE * totalDurationMs).toInt()
        val totalSteps = (beats * 4).coerceIn(8, 256)
        val nominalStartStep = (1 + (snappedS * totalSteps)).toInt().coerceIn(1, totalSteps - 1)
        val nominalEndStep = (snappedE * totalSteps).toInt().coerceIn(nominalStartStep + 1, totalSteps)
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
                // Bouton retour
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
                        text = "Loops Maker",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                renameInput = file.name.substringBeforeLast('.')
                                showRenameDialog = true
                            }
                    ) {
                        Text(
                            text = "${file.name} · $safeBpm BPM",
                            fontSize = 10.sp,
                            color = NeonCyanLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Renommer le fichier",
                            tint = NeonCyan,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            // Right Actions: [ 🧲 Aimant ] [ Play/Pause ] [ Écraser ] [ Enregistrer ] [ Fermer ]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Bouton Aimant (Magnétique / Libre)
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isMagnetMode) Color(0x338B5CF6) else Color(0x18FFFFFF))
                        .border(
                            1.dp,
                            if (isMagnetMode) NeonPurpleLight else Color(0x33FFFFFF),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { isMagnetMode = !isMagnetMode }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("🧲", fontSize = 11.sp)
                        Text(
                            text = if (isMagnetMode) "Aimant ON" else "Aimant OFF",
                            fontSize = 10.sp,
                            fontWeight = if (isMagnetMode) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isMagnetMode) NeonPurpleLight else Color(0x88FFFFFF)
                        )
                    }
                }

                // Bouton Play / Pause
                Box(
                    modifier = Modifier
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
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLoopPlaying) NeonCyanLight else Color.White
                        )
                    }
                }

                // Bouton Écraser
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
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Bouton Enregistrer (Copie)
                Button(
                    onClick = onSaveCopy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Enregistrer",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Enregistrer",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Bouton Fermer
                Box(
                    modifier = Modifier
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
            // Stepper Temps / Beats (Strictement pair, activable/désactivable au touché de la case)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "TEMPS:",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isTempsEnabled) NeonCyan else Color(0x77FFFFFF),
                    letterSpacing = 0.5.sp
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isTempsEnabled) Color(0x18FFFFFF) else Color(0x0AFFFFFF))
                        .clickable(enabled = isTempsEnabled && beats > 2) {
                            val prevEven = if (beats % 2 != 0) beats - 1 else beats - 2
                            onUpdateBeats(prevEven.coerceIn(2, 64))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Moins",
                        tint = if (isTempsEnabled) NeonPurpleLight else Color(0x44FFFFFF),
                        modifier = Modifier.size(13.dp)
                    )
                }

                // Case Chiffre Temps: activé/désactivé au touché, couleur fade si inactive
                val displayBeats = if (beats % 2 != 0) (beats + 1).coerceIn(2, 64) else beats
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isTempsEnabled) Color(0x3300E5FF) else Color(0x12FFFFFF))
                        .border(
                            width = 1.dp,
                            color = if (isTempsEnabled) NeonCyan else Color(0x22FFFFFF),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { isTempsEnabled = !isTempsEnabled }
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$displayBeats T",
                        fontSize = 11.5.sp,
                        fontWeight = if (isTempsEnabled) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isTempsEnabled) Color.White else Color(0x55FFFFFF)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isTempsEnabled) Color(0x18FFFFFF) else Color(0x0AFFFFFF))
                        .clickable(enabled = isTempsEnabled && beats < 64) {
                            val nextEven = if (beats % 2 != 0) beats + 1 else beats + 2
                            onUpdateBeats(nextEven.coerceIn(2, 64))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Plus",
                        tint = if (isTempsEnabled) NeonPurpleLight else Color(0x44FFFFFF),
                        modifier = Modifier.size(13.dp)
                    )
                }

                // Bouton AUTOMATIQUE
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isTempsEnabled) Color(0x2200E5FF) else Color(0x10FFFFFF))
                        .border(
                            1.dp,
                            if (isTempsEnabled) NeonCyan.copy(alpha = 0.7f) else Color(0x1AFFFFFF),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(enabled = isTempsEnabled) {
                            val durSec = try {
                                if (file.duration.contains(':')) {
                                    val parts = file.duration.split(':')
                                    parts[0].trim().toFloat() * 60f + parts[1].trim().toFloat()
                                } else {
                                    file.duration.replace("s", "").trim().toFloat()
                                }
                            } catch (_: Exception) {
                                (displayBeats * 60f / safeBpm)
                            }
                            val rawBeats = ((durSec * safeBpm) / 60f).toInt()
                            val calculatedEven = (((rawBeats + 1) / 2) * 2).coerceIn(2, 64)
                            onUpdateBeats(calculatedEven)
                            updateTrimsSmooth(0f, 1f)
                        }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AUTO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isTempsEnabled) NeonCyan else Color(0x55FFFFFF)
                    )
                }
            }

                // Mode Sélection: Handled exclusively via header 🧲 Aimant button
                Spacer(modifier = Modifier.width(4.dp))

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

        Spacer(modifier = Modifier.height(8.dp))

        // ================= FORME D'ONDE DJ TACTILE LIBRE / AIMANT =================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF090C14))
                .border(1.dp, Color(0x33A78BFA), RoundedCornerShape(16.dp))
        ) {
            WaveformDisplay(
                modifier = Modifier.fillMaxSize(),
                seed = file.name.hashCode(),
                beats = beats,
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

        Spacer(modifier = Modifier.height(4.dp))

        if (showRenameDialog) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                containerColor = Color(0xFF161A26),
                title = {
                    Text(
                        text = "Renommer la boucle",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Entrez le nouveau nom pour le fichier :",
                            color = Color(0xBBFFFFFF),
                            fontSize = 12.sp
                        )
                        OutlinedTextField(
                            value = renameInput,
                            onValueChange = { renameInput = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = Color(0x44FFFFFF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (renameInput.isNotBlank()) {
                                onRenameFile(file, renameInput.trim())
                                showRenameDialog = false
                            }
                        }
                    ) {
                        Text("Renommer", color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Annuler", color = Color(0x88FFFFFF))
                    }
                }
            )
        }
    }
}

/**
 * WaveformDisplay:
 * Ultra-sleek DAW-grade waveform renderer with natural audio transient envelopes,
 * beat divisions, zero-crossing line, and tactile IN/OUT cursors supporting free/magnet positioning.
 */
@Composable
private fun WaveformDisplay(
    modifier: Modifier = Modifier,
    seed: Int,
    beats: Int,
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
            val totalBars = 120
            val barGap = 1.5f
            val barWidth = (size.width / totalBars) - barGap
            val rulerHeight = 18f
            val waveHeight = size.height - rulerHeight
            val centerY = rulerHeight + waveHeight / 2f

            // 0. DAW Timeline Ruler (FL Studio Bar / Beat numbers)
            drawRect(
                color = Color(0xFF141724),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, rulerHeight)
            )
            drawLine(
                color = Color(0x40FFFFFF),
                start = Offset(0f, rulerHeight),
                end = Offset(size.width, rulerHeight),
                strokeWidth = 1f
            )

            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(200, 200, 220, 255)
                textSize = density.run { 9.sp.toPx() }
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }

            // 1. Draw background beat grid divisions & Ruler ticks
            val numBeats = beats.coerceIn(2, 64)
            for (b in 0..numBeats) {
                val beatX = (b.toFloat() / numBeats.toFloat()) * size.width
                val isMeasure = (b % 4 == 0)
                val measureNum = (b / 4) + 1

                // Ruler tick
                drawLine(
                    color = if (isMeasure) Color(0x90FFFFFF) else Color(0x40FFFFFF),
                    start = Offset(beatX, if (isMeasure) 0f else rulerHeight * 0.45f),
                    end = Offset(beatX, rulerHeight),
                    strokeWidth = if (isMeasure) 1.5f else 1.0f
                )

                // Draw measure number
                if (isMeasure && beatX < size.width - 20f) {
                    drawContext.canvas.nativeCanvas.drawText(
                        "$measureNum",
                        beatX + 4f,
                        rulerHeight - 4f,
                        textPaint
                    )
                }

                // Waveform grid line
                drawLine(
                    color = if (isMeasure) Color(0x28FFFFFF) else Color(0x12FFFFFF),
                    start = Offset(beatX, rulerHeight),
                    end = Offset(beatX, size.height),
                    strokeWidth = if (isMeasure) 1.5f else 1.0f
                )
            }

            // 2. Center zero-crossing line
            drawLine(
                color = Color(0x20FFFFFF),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 1f
            )

            // 3. Ultra-Fluid Professional DAW Continuous Vector Waveform
            val numSamples = 240
            val seedOffset = kotlin.math.abs(seed % 1000)

            // Synthesize audio amplitude envelope points
            val amps = FloatArray(numSamples)
            val halfHeights = FloatArray(numSamples)
            val dx = size.width / (numSamples - 1).toFloat()

            for (i in 0 until numSamples) {
                val progress = i.toFloat() / (numSamples - 1).toFloat()
                val beatPhase = (progress * beats) % 1.0f
                val transientImpact = (1.0f - beatPhase * 0.72f).coerceIn(0.28f, 1.0f)
                val fundamental = kotlin.math.sin(progress * 24.0 + seedOffset * 0.15).toFloat()
                val harmonic = kotlin.math.cos(progress * 56.0 + seedOffset * 0.35).toFloat()
                val noise = kotlin.math.sin(progress * 140.0 + seedOffset * 0.8).toFloat()

                val rawAmp = (kotlin.math.abs(fundamental) * 0.45f + kotlin.math.abs(harmonic) * 0.35f + kotlin.math.abs(noise) * 0.20f) * transientImpact
                val amp = rawAmp.coerceIn(0.04f, 0.94f)
                amps[i] = amp
                halfHeights[i] = amp * (waveHeight * 0.43f)
            }

            // Path for the complete filled waveform envelope
            val fullWavePath = Path().apply {
                moveTo(0f, centerY)
                // Upper envelope contour
                for (i in 0 until numSamples) {
                    val x = i * dx
                    val y = centerY - halfHeights[i]
                    lineTo(x, y)
                }
                // Lower envelope contour (reversed)
                for (i in (numSamples - 1) downTo 0) {
                    val x = i * dx
                    val y = centerY + halfHeights[i]
                    lineTo(x, y)
                }
                close()
            }

            // Path for dense RMS core body
            val rmsWavePath = Path().apply {
                moveTo(0f, centerY)
                for (i in 0 until numSamples) {
                    val x = i * dx
                    val y = centerY - (halfHeights[i] * 0.52f)
                    lineTo(x, y)
                }
                for (i in (numSamples - 1) downTo 0) {
                    val x = i * dx
                    val y = centerY + (halfHeights[i] * 0.52f)
                    lineTo(x, y)
                }
                close()
            }

            // Path for top crest stroke
            val topCrestPath = Path().apply {
                moveTo(0f, centerY - halfHeights[0])
                for (i in 1 until numSamples) {
                    lineTo(i * dx, centerY - halfHeights[i])
                }
            }

            // Path for bottom crest stroke
            val bottomCrestPath = Path().apply {
                moveTo(0f, centerY + halfHeights[0])
                for (i in 1 until numSamples) {
                    lineTo(i * dx, centerY + halfHeights[i])
                }
            }

            // A. Draw base unselected waveform body
            drawPath(
                path = fullWavePath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x3500E5FF),
                        Color(0x1500E5FF),
                        Color(0x15FB4570),
                        Color(0x35FB4570)
                    ),
                    startY = rulerHeight,
                    endY = size.height
                )
            )

            // Draw base crest lines (dimmed)
            drawPath(
                path = topCrestPath,
                color = Color(0x5500E5FF),
                style = Stroke(width = 1f)
            )
            drawPath(
                path = bottomCrestPath,
                color = Color(0x55FB4570),
                style = Stroke(width = 1f)
            )

            // B. Draw active loop region with radiant vibrant DAW styling
            val loopStartX = size.width * startFraction
            val loopEndX = size.width * endFraction

            if (loopEndX > loopStartX) {
                clipRect(
                    left = loopStartX,
                    top = rulerHeight,
                    right = loopEndX,
                    bottom = size.height
                ) {
                    // 1. Radiant saturated vector waveform fill
                    drawPath(
                        path = fullWavePath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                NeonCyan.copy(alpha = 0.85f),
                                NeonPurpleLight.copy(alpha = 0.88f),
                                NeonPink.copy(alpha = 0.90f)
                            ),
                            startX = loopStartX,
                            endX = loopEndX
                        )
                    )

                    // 2. Dense RMS white luminous core
                    drawPath(
                        path = rmsWavePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.70f),
                                Color.White.copy(alpha = 0.40f),
                                Color.White.copy(alpha = 0.70f)
                            ),
                            startY = rulerHeight,
                            endY = size.height
                        )
                    )

                    // 3. Glowing top crest vector line
                    drawPath(
                        path = topCrestPath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFFE0FFFF), Color(0xFFF3E8FF), Color(0xFFFFE4E6)),
                            startX = loopStartX,
                            endX = loopEndX
                        ),
                        style = Stroke(width = 1.6f)
                    )

                    // 4. Glowing bottom crest vector line
                    drawPath(
                        path = bottomCrestPath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF00E5FF), Color(0xFFA78BFA), Color(0xFFFB4570)),
                            startX = loopStartX,
                            endX = loopEndX
                        ),
                        style = Stroke(width = 1.4f)
                    )
                }
            }

            // 4. Darken outside loop regions
            if (startFraction > 0f) {
                drawRect(
                    color = Color(0x95080A12),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width * startFraction, size.height)
                )
            }
            if (endFraction < 1f) {
                drawRect(
                    color = Color(0x95080A12),
                    topLeft = Offset(size.width * endFraction, 0f),
                    size = Size(size.width * (1f - endFraction), size.height)
                )
            }

            // 5. Active loop window luminous glow & frame
            val loopWidth = (loopEndX - loopStartX).coerceAtLeast(0f)

            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0x1200E5FF),
                        Color(0x12A78BFA),
                        Color(0x12FB4570)
                    ),
                    startX = loopStartX,
                    endX = loopEndX
                ),
                topLeft = Offset(loopStartX, 0f),
                size = Size(loopWidth, size.height)
            )

            // Top & bottom glowing border lines of the active loop
            drawLine(
                color = Color(0x8000E5FF),
                start = Offset(loopStartX, rulerHeight),
                end = Offset(loopEndX, rulerHeight),
                strokeWidth = 2f
            )
            drawLine(
                color = Color(0x80FB4570),
                start = Offset(loopStartX, size.height),
                end = Offset(loopEndX, size.height),
                strokeWidth = 2f
            )
        }

        // ================= START TRIM HANDLE (IN) =================
        Box(
            modifier = Modifier
                .offset {
                    val handleHalfWidthPx = with(density) { 18.dp.toPx() }
                    androidx.compose.ui.unit.IntOffset(
                        (widthPx * startFraction - handleHalfWidthPx).toInt(),
                        0
                    )
                }
                .fillMaxHeight()
                .width(36.dp)
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
                // Top IN Flag
                Box(
                    modifier = Modifier
                        .size(24.dp, 18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF042F38))
                        .border(1.5.dp, NeonCyan, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("IN", fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold, color = NeonCyanLight)
                }

                // Vertical Laser line
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .weight(1f)
                        .background(
                            Brush.verticalGradient(
                                listOf(NeonCyan, NeonCyanLight, NeonCyan)
                            )
                        )
                )

                // Bottom IN Flag
                Box(
                    modifier = Modifier
                        .size(24.dp, 18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF042F38))
                        .border(1.5.dp, NeonCyan, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("IN", fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold, color = NeonCyanLight)
                }
            }
        }

        // ================= END TRIM HANDLE (OUT) =================
        Box(
            modifier = Modifier
                .offset {
                    val handleHalfWidthPx = with(density) { 18.dp.toPx() }
                    androidx.compose.ui.unit.IntOffset(
                        (widthPx * endFraction - handleHalfWidthPx).toInt(),
                        0
                    )
                }
                .fillMaxHeight()
                .width(36.dp)
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
                // Top OUT Flag
                Box(
                    modifier = Modifier
                        .size(26.dp, 18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF4C0519))
                        .border(1.5.dp, NeonPink, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("OUT", fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold, color = NeonPink)
                }

                // Vertical Laser line
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .weight(1f)
                        .background(
                            Brush.verticalGradient(
                                listOf(NeonPink, Color(0xFFFF7E98), NeonPink)
                            )
                        )
                )

                // Bottom OUT Flag
                Box(
                    modifier = Modifier
                        .size(26.dp, 18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF4C0519))
                        .border(1.5.dp, NeonPink, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("OUT", fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold, color = NeonPink)
                }
            }
        }
    }
}
