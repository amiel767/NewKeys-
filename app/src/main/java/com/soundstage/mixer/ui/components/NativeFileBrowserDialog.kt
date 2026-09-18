package com.soundstage.mixer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.soundstage.mixer.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Native In-App File Browser Dialog (Section 4)
 * Allows browsing storage without system intents.
 * Remembers last visited folder.
 */
data class StorageShortcut(
    val name: String,
    val path: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconTint: Color
)

@Composable
fun NativeFileBrowserDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    initialPath: String,
    title: String = "Sélecteur de Fichiers Audio",
    onPathChanged: (String) -> Unit = {},
    onPreviewAudio: ((File) -> Unit)? = null,
    onFileSelected: (File) -> Unit
) {
    if (!isOpen) return

    val defaultRoot = "/storage/emulated/0"
    var currentPath by remember(initialPath) {
        val startFile = File(if (initialPath.isNotBlank()) initialPath else defaultRoot)
        mutableStateOf(if (startFile.exists() && startFile.isDirectory) startFile.absolutePath else defaultRoot)
    }

    val currentDir = remember(currentPath) { File(currentPath) }

    val dirContents = remember(currentPath) {
        currentDir.listFiles()?.filter { file ->
            !file.isHidden && (file.isDirectory || isSupportedAudioFile(file.name))
        }?.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
        ) ?: emptyList()
    }

    val storageShortcuts = remember {
        val list = mutableListOf<StorageShortcut>()
        list.add(StorageShortcut("DrumPad", "$defaultRoot/SoundStage/DrumPad", Icons.Default.MusicNote, NeonCyan))
        list.add(StorageShortcut("SoundStage", "$defaultRoot/SoundStage", Icons.Default.Piano, Color.White))
        list.add(StorageShortcut("Interne", defaultRoot, Icons.Default.PhoneAndroid, NeonCyanLight))
        try {
            val rootStorage = File("/storage")
            if (rootStorage.exists()) {
                rootStorage.listFiles()?.forEach { f ->
                    if (f.isDirectory && f.name != "emulated" && f.name != "self" && f.canRead()) {
                        list.add(StorageShortcut("SD Card (${f.name})", f.absolutePath, Icons.Default.SdCard, NeonCyanLight))
                    }
                }
            }
        } catch (_: Exception) {}
        list.add(StorageShortcut("Downloads", "$defaultRoot/Download", Icons.Default.Download, NeonMagenta))
        list.add(StorageShortcut("Music", "$defaultRoot/Music", Icons.Default.MusicNote, NeonCyan))
        list
    }

    LaunchedEffect(currentPath) {
        onPathChanged(currentPath)
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 400.dp, max = 680.dp)
                    .fillMaxWidth(0.90f)
                    .heightIn(min = 360.dp, max = 500.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.2.dp, NeonCyan.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                    .shadow(24.dp, RoundedCornerShape(18.dp))
                    .testTag("native_file_browser_dialog"),
                color = Color(0xFF111420)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // Header: Title & Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick Jump Shortcuts (Horizontally scrollable for internal + SD cards)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(storageShortcuts) { shortcut ->
                            ShortcutChip(shortcut) { currentPath = it }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Current Path & Up (..) Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A1F2E))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val parent = currentDir.parentFile
                        IconButton(
                            onClick = {
                                if (parent != null && parent.canRead()) {
                                    currentPath = parent.absolutePath
                                }
                            },
                            enabled = parent != null && parent.canRead(),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Parent",
                                tint = if (parent != null && parent.canRead()) NeonCyan else TextDim,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = currentPath,
                            fontSize = 10.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // File & Folder List
                    if (dirContents.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0C0E15)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Dossier vide ou aucun fichier audio trouvé", fontSize = 11.sp, color = TextDim)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(dirContents) { file ->
                                val isDir = file.isDirectory
                                val dateFormat = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()) }
                                val modTime = dateFormat.format(Date(file.lastModified()))
                                val sizeKb = if (!isDir) "${file.length() / 1024} KB" else ""

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDir) Color(0xFF161B29) else Color(0xFF192033))
                                        .border(0.8.dp, if (isDir) Color(0x3300E5FF) else Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (isDir) {
                                                currentPath = file.absolutePath
                                            } else {
                                                onFileSelected(file)
                                                onClose()
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (isDir) Icons.Default.Folder else Icons.Default.Audiotrack,
                                            contentDescription = null,
                                            tint = if (isDir) NeonCyan else NeonPurpleLight,
                                            modifier = Modifier.size(18.dp)
                                        )

                                        Column {
                                            Text(
                                                text = file.name,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isDir) FontWeight.Bold else FontWeight.Medium,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!isDir) {
                                                Text(
                                                    text = "$sizeKb · $modTime",
                                                    fontSize = 8.5.sp,
                                                    color = TextDim
                                                )
                                            }
                                        }
                                    }

                                    if (isDir) {
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = TextDim,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (onPreviewAudio != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0x3300E5FF))
                                                        .clickable { onPreviewAudio(file) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = "Pré-écoute",
                                                        tint = NeonCyan,
                                                        modifier = Modifier.size(16.dp)
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
}

@Composable
private fun ShortcutChip(
    shortcut: StorageShortcut,
    onClick: (String) -> Unit
) {
    val targetFile = remember(shortcut.path) { File(shortcut.path) }
    if (!targetFile.exists()) return

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1C2235))
            .border(0.8.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
            .clickable { onClick(shortcut.path) }
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = shortcut.icon,
            contentDescription = null,
            tint = shortcut.iconTint,
            modifier = Modifier.size(12.dp)
        )
        Text(shortcut.name, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

private fun isSupportedAudioFile(fileName: String): Boolean {
    val ext = fileName.substringAfterLast(".", "").lowercase()
    return ext in listOf("wav", "mp3", "ogg", "flac", "aiff", "m4a", "aac")
}
