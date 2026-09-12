package com.soundstage.mixer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.SoundfontBankFile
import com.soundstage.mixer.model.SoundfontPreset
import com.soundstage.mixer.model.StorageItem
import kotlin.math.cos
import kotlin.math.sin

/**
 * SoundfontDialog:
 * Pure iOS Liquid Glass Pop-Up with smooth 3-color ambient aura background,
 * high-contrast typography, and persistent scroll position restoration.
 */
@Composable
fun SoundfontDialog(
    trackId: Int,
    source: String,
    presets: List<SoundfontPreset>,
    bankFiles: List<SoundfontBankFile> = emptyList(),
    soundfontStorageFiles: List<StorageItem> = emptyList(),
    selectedPresetId: Int,
    selectedSoundfontName: String = "",
    onSelectPreset: (Int) -> Unit,
    onSelectSf2File: ((StorageItem) -> Unit)? = null,
    activeTab: String,
    onTabChange: (String) -> Unit,
    onImportSf2: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subtitle = if (trackId == 0) "Piste Master" else "Piste $trackId"

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    // Search query state
    var searchQuery by remember { mutableStateOf("") }

    // LazyListStates for scroll position persistence
    val presetListState = rememberLazyListState()
    val fileListState = rememberLazyListState()

    // Sorted and filtered presets
    val sortedPresets = remember(presets) {
        presets.sortedWith(compareBy({ it.bankNumber }, { it.id }))
    }

    val filteredPresets = remember(sortedPresets, searchQuery) {
        if (searchQuery.isBlank()) sortedPresets
        else sortedPresets.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.id.toString().contains(searchQuery) ||
                    it.bankNumber.toString().contains(searchQuery)
        }
    }

    // Persistent scroll to the selected preset when dialog opens or preset changes
    LaunchedEffect(selectedPresetId, sortedPresets) {
        val targetIdx = sortedPresets.indexOfFirst { it.id == selectedPresetId }
        if (targetIdx >= 0) {
            val scrollTarget = (targetIdx - 1).coerceAtLeast(0)
            presetListState.scrollToItem(scrollTarget)
        }
    }

    // Files list
    val rawFiles = remember(soundfontStorageFiles, bankFiles) {
        if (soundfontStorageFiles.isNotEmpty()) soundfontStorageFiles
        else bankFiles.map {
            StorageItem(name = it.name, path = it.path, isDirectory = false, formattedSize = it.size)
        }
    }

    val filteredFiles = remember(rawFiles, searchQuery) {
        if (searchQuery.isBlank()) rawFiles
        else rawFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // Persistent scroll to active soundfont file
    LaunchedEffect(selectedSoundfontName, rawFiles) {
        if (selectedSoundfontName.isNotBlank()) {
            val fileIdx = rawFiles.indexOfFirst { it.name.contains(selectedSoundfontName, ignoreCase = true) }
            if (fileIdx >= 0) {
                fileListState.scrollToItem((fileIdx - 1).coerceAtLeast(0))
            }
        }
    }

    // Auto-switch to SF2 files tab if no presets are loaded yet
    LaunchedEffect(presets) {
        if (presets.isEmpty() && activeTab == "bank") {
            onTabChange("other")
        }
    }

    // Modal Background overlay: dark semi-transparent backdrop peeking through to mixer
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x88040814))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        // ================= HIGH-CONTRAST DARK GLASS POPUP CONTAINER =================
        Box(
            modifier = Modifier
                .width(620.dp) // Wider for landscape
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xF00D1322)) // Solid crisp dark glass surface
                .border(1.dp, Color(0x33A855F7), RoundedCornerShape(26.dp))
                .clickable(enabled = false) {}
                .testTag("dialog_soundfont")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                // ================= 1. CLEAN HEADER (SEARCH + ACTIONS) =================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Search Bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1EFFFFFF))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Rechercher",
                                tint = Color(0x88FFFFFF),
                                modifier = Modifier.size(18.dp)
                            )
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(Color(0xFF00E5FF)),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = if (activeTab == "bank") "Rechercher un preset..." else "Rechercher une soundfont...",
                                            color = Color(0x55FFFFFF),
                                            fontSize = 13.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Effacer",
                                    tint = Color(0x88FFFFFF),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { searchQuery = "" }
                                )
                            }
                        }
                    }

                    // Actions: [ + ] [ ✕ ]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onImportSf2 != null) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF))
                                    .clickable { onImportSf2() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Importer .sf2",
                                    tint = Color(0xFF002B33),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Close Pill
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF))
                                .clickable {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    onClose()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ================= 2. CONTENT AREA (LEFT TABS, RIGHT LIST) =================
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column (Tabs)
                    Column(
                        modifier = Modifier
                            .width(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x14FFFFFF)),
                        verticalArrangement = Arrangement.Top
                    ) {
                        listOf("bank" to "Soundfonts preset", "other" to "soundfonts").forEach { (tabKey, tabLabel) ->
                            val isSelected = activeTab == tabKey
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .background(if (isSelected) Color(0xFF00E5FF) else Color.Transparent)
                                    .clickable { onTabChange(tabKey) }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = tabLabel,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF0B101B) else Color.White
                                )
                            }
                        }
                    }

                    // Right Column (Lists)
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (activeTab == "bank") {
                            if (filteredPresets.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0x14FFFFFF))
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(text = "🎵", fontSize = 28.sp)
                                        Text(
                                            text = if (searchQuery.isNotEmpty()) "Aucun preset correspondant à \"$searchQuery\""
                                            else "Aucun preset chargé pour cette piste.",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xAAFFFFFF),
                                            textAlign = TextAlign.Center
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0x3300E5FF))
                                                .clickable { onTabChange("other") }
                                                .padding(horizontal = 14.dp, vertical = 7.dp)
                                        ) {
                                            Text(
                                                text = "📁 Choisir un fichier .sf2",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF00E5FF)
                                            )
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    state = presetListState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(
                                        items = filteredPresets,
                                        key = { "${it.bankNumber}:${it.id}" }
                                    ) { preset ->
                                        val isSelected = selectedPresetId == preset.id
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) Color(0x2A00E5FF) else Color(0x14FFFFFF))
                                                .clickable { onSelectPreset(preset.id) }
                                                .padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Number Badge
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) Color(0xFF00E5FF) else Color(0x25FFFFFF)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${preset.id}",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isSelected) Color(0xFF0B101B) else Color.White
                                                )
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = preset.name,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color.White else Color(0xEEFFFFFF),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Banque ${preset.bankNumber} · Preset #${preset.id}",
                                                    fontSize = 9.5.sp,
                                                    color = if (isSelected) Color(0xCC00E5FF) else Color(0x77FFFFFF)
                                                )
                                            }

                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0x3300E5FF))
                                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "✓ ACTIF",
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color(0xFF00E5FF)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Soundfonts list (.sf2 files in storage)
                            if (filteredFiles.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0x14FFFFFF))
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(text = "📦", fontSize = 32.sp)
                                        Text(
                                            text = if (searchQuery.isNotEmpty()) "Aucun fichier .sf2 correspondant à \"$searchQuery\""
                                            else "Aucun fichier SoundFont (.sf2) détecté.",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xAAFFFFFF),
                                            textAlign = TextAlign.Center
                                        )
                                        if (onImportSf2 != null) {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF00E5FF))
                                                    .clickable { onImportSf2() },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Importer un fichier .sf2",
                                                    tint = Color(0xFF002B33),
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    state = fileListState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(filteredFiles) { file ->
                                        val isLoaded = selectedSoundfontName.isNotBlank() &&
                                                file.name.contains(selectedSoundfontName, ignoreCase = true)

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isLoaded) Color(0x268B5CF6) else Color(0x14FFFFFF))
                                                .clickable {
                                                    onSelectSf2File?.invoke(file)
                                                    onTabChange("bank")
                                                }
                                                .padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0x22FFFFFF)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = "📦", fontSize = 13.sp)
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = file.name,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (file.formattedSize.isNotEmpty()) file.formattedSize else "Soundfont SF2",
                                                    fontSize = 9.5.sp,
                                                    color = Color(0x77FFFFFF)
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isLoaded) Color(0x44A78BFA) else Color(0x2200E5FF))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (isLoaded) "CHARGÉ" else "CHARGER",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isLoaded) Color(0xFFDDD6FE) else Color(0xFF00E5FF)
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
