package com.soundstage.mixer.ui.pages

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.StorageItem
import com.soundstage.mixer.ui.theme.*

// Palettes fidèles à soundstage_explorateur_dossiers.svg et soundstage_explorateur_fichiers.svg
private val ExplorerDarkBg = Color(0xFF0E1416)
private val CardExplorerBg = Color(0xFF141B1E)
private val SidebarItemActive = Color(0xFF004D40)
private val CyanPrimary = Color(0xFF26C6DA)
private val CyanDark = Color(0xFF00838F)

// Tuiles exactes du SVG
private val FolderColorDrumPad = Color(0xFF283593)
private val FolderColorLogs = Color(0xFF37474F)
private val FolderColorLoops = Color(0xFF00695C)
private val FolderColorNotes = Color(0xFF2E3B4E)
private val FolderColorPresets = Color(0xFF2A4240)
private val FolderColorRecordings = Color(0xFF5A2435)

data class ExplorerFolderItem(
    val name: String,
    val count: Int,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileExplorerPage(
    title: String = "Explorateur de loops audio",
    audioFiles: List<StorageItem>,
    isLoopMode: Boolean = false,
    onPreviewAudioFile: (StorageItem) -> Unit,
    onStopPreview: () -> Unit,
    onAssignToPad: ((Int, StorageItem, Boolean) -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(if (isLoopMode) "Fichiers" else "Dossiers") }
    var currentSubFolder by remember { mutableStateOf<String?>(if (isLoopMode) "Loops" else null) }
    var currentlyPlayingFile by remember { mutableStateOf<StorageItem?>(null) }
    var isLooping by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var selectedTargetPadId by remember { mutableIntStateOf(1) }

    // Dialogue d'assignation au pad par appui long
    var fileToAssign by remember { mutableStateOf<StorageItem?>(null) }

    // Dialogue de réglage de tonalité
    var fileForKeyEdit by remember { mutableStateOf<StorageItem?>(null) }
    val fileKeysMap = remember { mutableStateMapOf<String, String>() }

    // BPM par fichier
    val fileBpmMap = remember { mutableStateMapOf<String, Int>() }

    val folders = listOf(
        ExplorerFolderItem("DrumPad", 8, FolderColorDrumPad, Icons.Default.Apps),
        ExplorerFolderItem("Logs", 3, FolderColorLogs, Icons.Default.Description),
        ExplorerFolderItem("Loops", audioFiles.size.coerceAtLeast(24), FolderColorLoops, Icons.Default.GraphicEq),
        ExplorerFolderItem("Notes", 5, FolderColorNotes, Icons.Default.MusicNote),
        ExplorerFolderItem("Presets", 12, FolderColorPresets, Icons.Default.Tune),
        ExplorerFolderItem("Recordings", 7, FolderColorRecordings, Icons.Default.Mic)
    )

    val filteredFiles = remember(audioFiles, searchQuery, currentSubFolder) {
        audioFiles.filter { file ->
            val matchesQuery = searchQuery.isEmpty() || file.name.contains(searchQuery, ignoreCase = true)
            matchesQuery
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ExplorerDarkBg)
            .padding(8.dp)
            .testTag("file_explorer_page_root")
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ================= 1. BARRE LATÉRALE GAUCHE (SVG MATCH) =================
            Column(
                modifier = Modifier
                    .width(68.dp)
                    .fillMaxHeight()
                    .padding(end = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Item DrumPad
                SidebarIconItem(
                    icon = Icons.Default.Apps,
                    label = "DrumPad",
                    isSelected = selectedTab == "DrumPad",
                    onClick = {
                        selectedTab = "Dossiers"
                        currentSubFolder = "DrumPad"
                    }
                )

                // Item SoundStage
                SidebarIconItem(
                    icon = Icons.Default.Piano,
                    label = "SoundStage",
                    isSelected = selectedTab == "SoundStage",
                    onClick = {
                        selectedTab = "Dossiers"
                        currentSubFolder = null
                    }
                )

                // Item Interne
                SidebarIconItem(
                    icon = Icons.Default.Smartphone,
                    label = "Interne",
                    isSelected = false,
                    onClick = {
                        selectedTab = "Fichiers"
                        currentSubFolder = "Interne"
                    }
                )

                // Item Downloads
                SidebarIconItem(
                    icon = Icons.Default.FileDownload,
                    label = "Downloads",
                    isSelected = selectedTab == "Downloads",
                    onClick = {
                        selectedTab = "Fichiers"
                        currentSubFolder = "Downloads"
                    }
                )

                // Item Music
                SidebarIconItem(
                    icon = Icons.Default.MusicNote,
                    label = "Music",
                    isSelected = selectedTab == "Music",
                    onClick = {
                        selectedTab = "Fichiers"
                        currentSubFolder = "Music"
                    }
                )
            }

            // ================= 2. CONTENU PRINCIPAL =================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Barre Supérieure de l'explorateur
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isLoopMode) "Explorateur de loops audio" else title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Bouton Recherche
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF))
                                .clickable { },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Recherche", tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        // Bouton Fermer
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF))
                                .clickable {
                                    onStopPreview()
                                    onClose()
                                }
                                .testTag("btn_close_file_explorer"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Fil d'ariane & Filtre
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (currentSubFolder != null) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x22FFFFFF))
                                    .clickable {
                                        currentSubFolder = null
                                        selectedTab = "Dossiers"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }

                        // Pilule dossier courant
                        Box(
                            modifier = Modifier
                                .height(26.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(CyanDark)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Text(
                                    text = currentSubFolder ?: "SoundStage",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Text(
                        text = if (selectedTab == "Dossiers") "${folders.size} dossiers" else "${filteredFiles.size} fichiers",
                        fontSize = 11.sp,
                        color = Color(0x88FFFFFF)
                    )
                }

                // Vue Dossiers ou Vue Fichiers
                Box(modifier = Modifier.weight(1f)) {
                    if (selectedTab == "Dossiers") {
                        // GRILLE DES DOSSIERS (soundstage_explorateur_dossiers.svg)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(folders) { folder ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(76.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(folder.color)
                                        .clickable {
                                            currentSubFolder = folder.name
                                            selectedTab = "Fichiers"
                                        }
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(verticalArrangement = Arrangement.Center) {
                                            Icon(folder.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(folder.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text("${folder.count} éléments", fontSize = 10.sp, color = Color(0xCCFFFFFF))
                                        }

                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0x88FFFFFF), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    } else {
                        // LISTE DES FICHIERS (soundstage_explorateur_fichiers.svg)
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredFiles) { file ->
                                val isThisPlaying = currentlyPlayingFile?.path == file.path && isPlaying
                                val detected = remember(file.path) {
                                    com.soundstage.mixer.audio.AudioKeyBpmDetector.detectKeyAndBpm(file.name, java.io.File(file.path))
                                }
                                val currentKey = fileKeysMap[file.path] ?: (if (detected.key.isNotEmpty()) detected.key else "C")
                                val currentBpm = fileBpmMap[file.path] ?: (if (detected.bpm > 0) detected.bpm else 120)

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isThisPlaying) Color(0xFF003844) else CardExplorerBg)
                                        .border(
                                            1.dp,
                                            if (isThisPlaying) CyanPrimary else Color(0x14FFFFFF),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                if (isThisPlaying) {
                                                    isPlaying = false
                                                    onStopPreview()
                                                } else {
                                                    currentlyPlayingFile = file
                                                    isPlaying = true
                                                    onPreviewAudioFile(file)
                                                }
                                            },
                                            onLongClick = {
                                                // Appui long : popup pour assigner à un pad D1..D8
                                                fileToAssign = file
                                            }
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("file_item_${file.name}")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Icône / Statut
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isThisPlaying) CyanPrimary else CyanDark),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isThisPlaying) Icons.Default.Check else Icons.Default.GraphicEq,
                                                    contentDescription = null,
                                                    tint = if (isThisPlaying) Color.Black else Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            Column {
                                                Text(
                                                    text = file.name,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(file.name.substringAfterLast('.', "WAV").uppercase(), fontSize = 9.sp, color = Color(0x88FFFFFF))
                                                    Text(file.formattedSize, fontSize = 9.sp, color = Color(0x88FFFFFF))
                                                    Text(detected.timeSignature, fontSize = 9.sp, color = Color(0x88FFFFFF))
                                                }
                                            }
                                        }

                                        // Badges interactifs Signature Rythmique, Tonalité et BPM à droite du fichier
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            // 1. Badge Signature Rythmique (4/4, 3/4, 6/8...)
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF1E293B))
                                                    .border(1.dp, Color(0x4426C6DA), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = detected.timeSignature,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = CyanPrimary
                                                )
                                            }

                                            // 2. Badge Tonalité (clic pour modifier en pop-up avec choix Majeur / Mineur)
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF334155))
                                                    .clickable { fileForKeyEdit = file }
                                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(11.dp))
                                                    Text(
                                                        text = currentKey,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }

                                            // Badge BPM avec boutons [-] et [+] centrés géométriquement
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(CyanDark)
                                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(RoundedCornerShape(3.dp))
                                                        .background(Color(0x22FFFFFF))
                                                        .clickable {
                                                            val next = (currentBpm - 1).coerceAtLeast(40)
                                                            fileBpmMap[file.path] = next
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Remove, contentDescription = "BPM moins", tint = Color.White, modifier = Modifier.size(10.dp))
                                                }

                                                Text(
                                                    text = "$currentBpm BPM",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 3.dp)
                                                )

                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(RoundedCornerShape(3.dp))
                                                        .background(Color(0x22FFFFFF))
                                                        .clickable {
                                                            val next = (currentBpm + 1).coerceAtMost(240)
                                                            fileBpmMap[file.path] = next
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "BPM plus", tint = Color.White, modifier = Modifier.size(10.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ================= 3. LECTEUR PERSISTANT EN BAS (SVG MATCH) =================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardExplorerBg)
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Bouton Play/Pause
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(CyanPrimary)
                                .clickable {
                                    if (currentlyPlayingFile != null) {
                                        isPlaying = !isPlaying
                                        if (!isPlaying) onStopPreview() else onPreviewAudioFile(currentlyPlayingFile!!)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Nom du fichier & Waveform cyan
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = currentlyPlayingFile?.name ?: "Sélectionner un fichier",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("0:12 / 0:38", fontSize = 9.sp, color = Color(0x88FFFFFF))
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Waveform simulée
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val heights = listOf(4, 8, 12, 16, 14, 10, 6, 12, 18, 15, 8, 5, 11, 14, 8, 12, 16, 10, 6, 14, 18, 12, 8, 5)
                                heights.forEachIndexed { i, h ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(h.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(if (i < 10) CyanPrimary else Color(0x44FFFFFF))
                                    )
                                }
                            }
                        }

                        // Bouton Répétition Boucle
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (isLooping) CyanDark else Color(0x22FFFFFF))
                                .clickable { isLooping = !isLooping },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Repeat, contentDescription = "Loop", tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Bouton CHARGER
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(CyanPrimary)
                                .clickable {
                                    if (currentlyPlayingFile != null) {
                                        onAssignToPad?.invoke(selectedTargetPadId, currentlyPlayingFile!!, isLoopMode)
                                        onClose()
                                    }
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Apps, contentDescription = null, tint = Color.Black, modifier = Modifier.size(13.dp))
                                Text("Charger", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                            }
                        }
                    }
                }
            }
        }

        // ================= POPUP D'ASSIGNATION AU PAD (D1 À D8) =================
        if (fileToAssign != null) {
            AlertDialog(
                onDismissRequest = { fileToAssign = null },
                title = {
                    Text(
                        text = "Assigner à quel pad ?",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = fileToAssign!!.name,
                            fontSize = 11.sp,
                            color = Color(0xCCFFFFFF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Grille des 8 cases de pad : [D1] [D2] [D3] [D4] et [D5] [D6] [D7] [D8]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (padId in 1..4) {
                                Button(
                                    onClick = {
                                        onAssignToPad?.invoke(padId, fileToAssign!!, isLoopMode)
                                        fileToAssign = null
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanDark)
                                ) {
                                    Text("D$padId", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (padId in 5..8) {
                                Button(
                                    onClick = {
                                        onAssignToPad?.invoke(padId, fileToAssign!!, isLoopMode)
                                        fileToAssign = null
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanDark)
                                ) {
                                    Text("D$padId", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { fileToAssign = null }) {
                        Text("Annuler", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1E2330)
            )
        }

        // ================= POPUP DE RÉGLAGE DE TONALITÉ =================
        if (fileForKeyEdit != null) {
            val targetPath = fileForKeyEdit!!.path
            val currentFileKey = fileKeysMap[targetPath] ?: "C"
            val initialIsMinor = currentFileKey.endsWith("m")
            val initialNote = currentFileKey.removeSuffix("m")
            val chromaticNotes = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
            var selectedNote by remember(targetPath) {
                mutableStateOf(if (chromaticNotes.contains(initialNote)) initialNote else "C")
            }
            var isMinor by remember(targetPath) { mutableStateOf(initialIsMinor) }

            AlertDialog(
                onDismissRequest = { fileForKeyEdit = null },
                title = {
                    Column {
                        Text("Tonalité de la Loop", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(fileForKeyEdit!!.name, fontSize = 11.sp, color = Color(0xCCFFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Choix de la note fondamentale
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.height(120.dp)
                        ) {
                            items(chromaticNotes) { note ->
                                val isCurrent = selectedNote == note
                                Box(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isCurrent) CyanPrimary else Color(0x22FFFFFF))
                                        .clickable { selectedNote = note },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = note,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) Color.Black else Color.White
                                    )
                                }
                            }
                        }

                        // Choix Majeur / Mineur
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { isMinor = false },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!isMinor) CyanPrimary else Color(0x22FFFFFF)
                                )
                            ) {
                                Text("Majeur", color = if (!isMinor) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { isMinor = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMinor) CyanPrimary else Color(0x22FFFFFF)
                                )
                            ) {
                                Text("Mineur", color = if (isMinor) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val finalKey = if (isMinor) "${selectedNote}m" else selectedNote
                            fileKeysMap[fileForKeyEdit!!.path] = finalKey
                            fileForKeyEdit = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                    ) {
                        Text("Appliquer", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { fileForKeyEdit = null }) {
                        Text("Annuler", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1E2330)
            )
        }
    }
}

@Composable
private fun SidebarIconItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) SidebarItemActive else Color(0x14FFFFFF))
                .border(
                    1.dp,
                    if (isSelected) CyanPrimary else Color.Transparent,
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) CyanPrimary else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            fontSize = 9.sp,
            color = if (isSelected) CyanPrimary else Color(0xAAFFFFFF),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
