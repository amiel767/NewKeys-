package com.soundstage.mixer.ui.pages

import android.media.MediaPlayer
import android.os.Environment
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.StorageItem
import com.soundstage.mixer.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File

private val ExplorerDarkBg = Color(0xFF0E1416)
private val CardExplorerBg = Color(0xFF141B1E)
private val SidebarItemActive = Color(0xFF004D40)
private val CyanPrimary = Color(0xFF26C6DA)
private val CyanDark = Color(0xFF00838F)

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
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val path: String? = null
)

private fun formatTime(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

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
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var activeSidebarTab by remember { mutableStateOf(if (isLoopMode) "SoundStage" else "DrumPad") }
    var currentSubFolder by remember { mutableStateOf<String?>(if (isLoopMode) "Loops" else "DrumPad") }
    var currentCustomDirectory by remember { mutableStateOf<File?>(null) }

    var currentlyPlayingFile by remember { mutableStateOf<StorageItem?>(null) }
    var isLooping by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPlaybackPositionMs by remember { mutableIntStateOf(0) }
    var currentDurationMs by remember { mutableIntStateOf(0) }

    val selectedTargetPadId by remember { mutableIntStateOf(1) }
    var fileToAssign by remember { mutableStateOf<StorageItem?>(null) }
    var fileForKeyEdit by remember { mutableStateOf<StorageItem?>(null) }
    val fileKeysMap = remember { mutableStateMapOf<String, String>() }
    val fileBpmMap = remember { mutableStateMapOf<String, Int>() }

    // Lecteur local ultra réactif avec mise à jour continue du temps
    val localPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (localPlayer.isPlaying) {
                    localPlayer.stop()
                }
                localPlayer.release()
            } catch (_: Exception) {}
            onStopPreview()
        }
    }

    LaunchedEffect(isPlaying, currentlyPlayingFile) {
        while (isPlaying && currentlyPlayingFile != null) {
            try {
                if (localPlayer.isPlaying) {
                    currentPlaybackPositionMs = localPlayer.currentPosition
                    val dur = localPlayer.duration
                    if (dur > 0) currentDurationMs = dur
                }
            } catch (_: Exception) {}
            delay(150)
        }
    }

    fun playFileLocally(file: StorageItem) {
        try {
            localPlayer.reset()
            val f = File(file.path)
            if (f.exists()) {
                localPlayer.setDataSource(f.absolutePath)
                localPlayer.isLooping = isLooping
                localPlayer.prepare()
                localPlayer.start()
                currentDurationMs = localPlayer.duration.coerceAtLeast(1000)
                currentPlaybackPositionMs = 0
                currentlyPlayingFile = file
                isPlaying = true
                localPlayer.setOnCompletionListener {
                    if (!isLooping) {
                        isPlaying = false
                        currentPlaybackPositionMs = 0
                    }
                }
            } else {
                onPreviewAudioFile(file)
                currentlyPlayingFile = file
                isPlaying = true
            }
        } catch (e: Exception) {
            onPreviewAudioFile(file)
            currentlyPlayingFile = file
            isPlaying = true
        }
    }

    fun stopLocalPlayback() {
        try {
            if (localPlayer.isPlaying) {
                localPlayer.pause()
            }
        } catch (_: Exception) {}
        isPlaying = false
        onStopPreview()
    }

    // Calcul des dossiers SoundStage
    val baseFolders = remember(audioFiles) {
        listOf(
            ExplorerFolderItem("DrumPad", audioFiles.count { it.path.contains("DrumPad", ignoreCase = true) }.coerceAtLeast(8), FolderColorDrumPad, Icons.Default.Apps),
            ExplorerFolderItem("Loops", audioFiles.count { it.path.contains("Loop", ignoreCase = true) || !it.path.contains("DrumPad", ignoreCase = true) }.coerceAtLeast(12), FolderColorLoops, Icons.Default.GraphicEq),
            ExplorerFolderItem("Recordings", 4, FolderColorRecordings, Icons.Default.Mic),
            ExplorerFolderItem("Presets", 8, FolderColorPresets, Icons.Default.Tune),
            ExplorerFolderItem("Notes", 3, FolderColorNotes, Icons.Default.MusicNote),
            ExplorerFolderItem("Logs", 2, FolderColorLogs, Icons.Default.Description)
        )
    }

    // Récupération des fichiers selon l'onglet actif et le dossier
    val displayedFiles = remember(audioFiles, activeSidebarTab, currentSubFolder, currentCustomDirectory, searchQuery) {
        val rawList: List<StorageItem> = when (activeSidebarTab) {
            "DrumPad" -> {
                audioFiles.filter { it.path.contains("DrumPad", ignoreCase = true) || it.name.contains("Drum", ignoreCase = true) || it.name.contains("Kick", ignoreCase = true) || it.name.contains("Snare", ignoreCase = true) || it.name.contains("Hat", ignoreCase = true) }.ifEmpty { audioFiles }
            }
            "SoundStage" -> {
                when (currentSubFolder) {
                    "DrumPad" -> audioFiles.filter { it.path.contains("DrumPad", ignoreCase = true) }.ifEmpty { audioFiles }
                    "Loops" -> audioFiles.filter { it.path.contains("Loop", ignoreCase = true) || !it.path.contains("DrumPad", ignoreCase = true) }.ifEmpty { audioFiles }
                    else -> audioFiles
                }
            }
            "Interne", "Downloads", "Music" -> {
                val targetDir = currentCustomDirectory ?: when (activeSidebarTab) {
                    "Downloads" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    "Music" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    else -> Environment.getExternalStorageDirectory()
                }
                val dirFiles = try {
                    if (targetDir.exists() && targetDir.isDirectory) {
                        targetDir.listFiles()?.filter { f ->
                            f.isFile && (f.name.endsWith(".wav", ignoreCase = true) || f.name.endsWith(".mp3", ignoreCase = true) || f.name.endsWith(".m4a", ignoreCase = true) || f.name.endsWith(".aac", ignoreCase = true) || f.name.endsWith(".mid", ignoreCase = true))
                        }?.map { f ->
                            val ext = f.extension.uppercase()
                            val sz = f.length()
                            val formatted = when {
                                sz >= 1024 * 1024 -> "%.1f MB".format(sz / (1024f * 1024f))
                                sz >= 1024 -> "%d KB".format(sz / 1024)
                                else -> "$sz B"
                            }
                            StorageItem(name = f.name, path = f.absolutePath, isDirectory = false, size = sz, extension = ext, formattedSize = formatted)
                        } ?: emptyList()
                    } else emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
                if (dirFiles.isNotEmpty()) dirFiles else audioFiles
            }
            else -> audioFiles
        }

        if (searchQuery.isBlank()) {
            rawList
        } else {
            rawList.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    // Sous-dossiers pour la navigation interne
    val customSubDirs = remember(activeSidebarTab, currentCustomDirectory) {
        if (activeSidebarTab in listOf("Interne", "Downloads", "Music")) {
            val targetDir = currentCustomDirectory ?: when (activeSidebarTab) {
                "Downloads" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                "Music" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                else -> Environment.getExternalStorageDirectory()
            }
            try {
                if (targetDir.exists() && targetDir.isDirectory) {
                    targetDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.map { dir ->
                        val count = dir.listFiles()?.size ?: 0
                        ExplorerFolderItem(name = dir.name, count = count, color = CardExplorerBg, icon = Icons.Default.Folder, path = dir.absolutePath)
                    } ?: emptyList()
                } else emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ExplorerDarkBg)
            .padding(8.dp)
            .testTag("file_explorer_page_root")
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ================= 1. BARRE LATÉRALE GAUCHE =================
            Column(
                modifier = Modifier
                    .width(68.dp)
                    .fillMaxHeight()
                    .padding(end = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SidebarIconItem(
                    icon = Icons.Default.Apps,
                    label = "DrumPad",
                    isSelected = activeSidebarTab == "DrumPad",
                    onClick = {
                        activeSidebarTab = "DrumPad"
                        currentSubFolder = "DrumPad"
                        currentCustomDirectory = null
                    }
                )

                SidebarIconItem(
                    icon = Icons.Default.Piano,
                    label = "SoundStage",
                    isSelected = activeSidebarTab == "SoundStage",
                    onClick = {
                        activeSidebarTab = "SoundStage"
                        currentSubFolder = null
                        currentCustomDirectory = null
                    }
                )

                SidebarIconItem(
                    icon = Icons.Default.Smartphone,
                    label = "Interne",
                    isSelected = activeSidebarTab == "Interne",
                    onClick = {
                        activeSidebarTab = "Interne"
                        currentSubFolder = "Interne"
                        currentCustomDirectory = Environment.getExternalStorageDirectory()
                    }
                )

                SidebarIconItem(
                    icon = Icons.Default.FileDownload,
                    label = "Downloads",
                    isSelected = activeSidebarTab == "Downloads",
                    onClick = {
                        activeSidebarTab = "Downloads"
                        currentSubFolder = "Downloads"
                        currentCustomDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    }
                )

                SidebarIconItem(
                    icon = Icons.Default.MusicNote,
                    label = "Music",
                    isSelected = activeSidebarTab == "Music",
                    onClick = {
                        activeSidebarTab = "Music"
                        currentSubFolder = "Music"
                        currentCustomDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
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
                        // Bouton Fermer
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF))
                                .clickable {
                                    stopLocalPlayback()
                                    onClose()
                                }
                                .testTag("btn_close_file_explorer"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Fil d'Ariane & Compteurs
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
                        if (currentSubFolder != null || currentCustomDirectory != null) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x22FFFFFF))
                                    .clickable {
                                        if (currentCustomDirectory != null && currentCustomDirectory?.parentFile != null) {
                                            currentCustomDirectory = currentCustomDirectory?.parentFile
                                        } else {
                                            currentSubFolder = null
                                            currentCustomDirectory = null
                                        }
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
                                    text = currentCustomDirectory?.name ?: (currentSubFolder ?: activeSidebarTab),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Text(
                        text = "${displayedFiles.size} fichiers",
                        fontSize = 11.sp,
                        color = Color(0x88FFFFFF)
                    )
                }

                // Affichage Contenu (Grille de dossiers OU Liste de fichiers)
                Box(modifier = Modifier.weight(1f)) {
                    if (activeSidebarTab == "SoundStage" && currentSubFolder == null) {
                        // GRILLE DES DOSSIERS SOUNDSTAGE
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(baseFolders) { folder ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(76.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(folder.color)
                                        .clickable {
                                            currentSubFolder = folder.name
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
                        // LISTE DES FICHIERS AUDIO & SOUS-DOSSIERS
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Sous-dossiers éventuels
                            if (customSubDirs.isNotEmpty()) {
                                items(customSubDirs) { subDir ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF1B2428))
                                            .clickable {
                                                if (subDir.path != null) {
                                                    currentCustomDirectory = File(subDir.path)
                                                }
                                            }
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.Folder, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                                                Text(subDir.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                            Text("${subDir.count} items", fontSize = 10.sp, color = Color(0x88FFFFFF))
                                        }
                                    }
                                }
                            }

                            items(displayedFiles) { file ->
                                val isThisPlaying = currentlyPlayingFile?.path == file.path && isPlaying
                                val detected = remember(file.path) {
                                    com.soundstage.mixer.audio.AudioKeyBpmDetector.detectKeyAndBpm(file.name, File(file.path))
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
                                                    stopLocalPlayback()
                                                } else {
                                                    playFileLocally(file)
                                                }
                                            },
                                            onLongClick = {
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
                                                    imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
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

                                        // Badges interactifs Signature Rythmique, Tonalité et BPM
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            // 1. Badge Signature Rythmique
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

                                            // 2. Badge Tonalité modifiable
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

                                            // 3. Badge BPM avec boutons [-] et [+]
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

                // ================= 3. LECTEUR PERSISTANT EN BAS AVEC VRAI TIMER =================
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
                                        if (isPlaying) {
                                            stopLocalPlayback()
                                        } else {
                                            playFileLocally(currentlyPlayingFile!!)
                                        }
                                    } else if (displayedFiles.isNotEmpty()) {
                                        playFileLocally(displayedFiles.first())
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

                        // Nom du fichier, Vraie position de lecture & Waveform
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
                                Text(
                                    text = "${formatTime(currentPlaybackPositionMs)} / ${formatTime(currentDurationMs)}",
                                    fontSize = 9.sp,
                                    color = Color(0xAAFFFFFF)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Waveform animée selon la progression réelle
                            val progressRatio = if (currentDurationMs > 0) {
                                (currentPlaybackPositionMs.toFloat() / currentDurationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val heights = listOf(4, 8, 12, 16, 14, 10, 6, 12, 18, 15, 8, 5, 11, 14, 8, 12, 16, 10, 6, 14, 18, 12, 8, 5)
                                val activeBars = (progressRatio * heights.size).toInt()
                                heights.forEachIndexed { i, h ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(h.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(if (i <= activeBars && isPlaying) CyanPrimary else Color(0x33FFFFFF))
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
                                .clickable {
                                    isLooping = !isLooping
                                    try {
                                        localPlayer.isLooping = isLooping
                                    } catch (_: Exception) {}
                                },
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
                                    val target = currentlyPlayingFile ?: displayedFiles.firstOrNull()
                                    if (target != null) {
                                        fileToAssign = target
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

