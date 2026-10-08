package com.soundstage.mixer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
import com.soundstage.mixer.ui.theme.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    onSelectPresetFull: ((SoundfontPreset) -> Unit)? = null,
    onSelectSf2File: ((StorageItem) -> Unit)? = null,
    onUnloadSoundFont: (() -> Unit)? = null,
    onDeleteSf2File: ((StorageItem) -> Unit)? = null,
    activeTab: String,
    onTabChange: (String) -> Unit,
    onImportSf2: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subtitle = if (trackId == 0) "Master Track" else "Track $trackId"

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
    var pendingDeleteFile by remember { mutableStateOf<StorageItem?>(null) }

    // LazyListStates for scroll position persistence
    val presetListState = rememberLazyListState()
    val fileListState = rememberLazyListState()

    // Distinct banks available in current SoundFont
    val availableBanks = remember(presets) {
        presets.map { it.bankNumber }.distinct().sorted()
    }

    var selectedBank by remember(presets) {
        val initialBank = presets.find { it.id == selectedPresetId }?.bankNumber ?: availableBanks.firstOrNull() ?: 0
        mutableStateOf(initialBank)
    }

    LaunchedEffect(availableBanks) {
        if (selectedBank !in availableBanks && availableBanks.isNotEmpty()) {
            selectedBank = availableBanks.first()
        }
    }

    // Filter presets: If multiple banks exist and search is blank, filter by selectedBank
    val bankPresets = remember(presets, selectedBank, availableBanks) {
        if (availableBanks.size <= 1) {
            presets.sortedWith(compareBy({ it.bankNumber }, { it.id }))
        } else {
            presets.filter { it.bankNumber == selectedBank }.sortedBy { it.id }
        }
    }

    val filteredPresets = remember(bankPresets, presets, searchQuery) {
        if (searchQuery.isBlank()) bankPresets
        else presets.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.id.toString().contains(searchQuery) ||
                    it.bankNumber.toString().contains(searchQuery)
        }.sortedWith(compareBy({ it.bankNumber }, { it.id }))
    }

    // Persistent scroll to the selected preset when dialog opens or preset changes
    LaunchedEffect(selectedPresetId, bankPresets) {
        val targetIdx = bankPresets.indexOfFirst { it.id == selectedPresetId }
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
                .shadow(16.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black, spotColor = Color(0xAA000000))
                .clip(RoundedCornerShape(26.dp))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(26.dp))
                .clickable(enabled = false) {}
                .testTag("dialog_soundfont")
        ) {
            AmbientStudioBackground(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            drawLine(
                                color = Color.White.copy(alpha = 0.15f),
                                start = Offset(16.dp.toPx(), 1f),
                                end = Offset(size.width - 16.dp.toPx(), 1f),
                                strokeWidth = 1.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
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
                                contentDescription = "Search",
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
                                cursorBrush = SolidColor(Color(0xFF9BC0F8)),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = if (activeTab == "bank") "Search presets..." else "Search soundfonts...",
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
                                    contentDescription = "Clear",
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
                                    .background(Color(0xFF9BC0F8))
                                    .clickable { onImportSf2() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Import .sf2",
                                    tint = Color(0xFF101726),
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
                                contentDescription = "Close",
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
                        listOf("bank" to "Presets", "other" to "SoundFonts").forEach { (tabKey, tabLabel) ->
                            val isSelected = activeTab == tabKey
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .background(if (isSelected) Color(0xFF9BC0F8) else Color.Transparent)
                                    .clickable { onTabChange(tabKey) }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = tabLabel,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF101726) else Color.White
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
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = Color(0xFF9BC0F8),
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Text(
                                            text = if (searchQuery.isNotEmpty()) "No presets match \"$searchQuery\""
                                            else "No presets loaded for this track.",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xAAFFFFFF),
                                            textAlign = TextAlign.Center
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0x339BC0F8))
                                                .clickable { onTabChange("other") }
                                                .padding(horizontal = 14.dp, vertical = 7.dp)
                                        ) {
                                            Text(
                                                text = "Select Sound",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF9BC0F8)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Bank selector tabs when multiple banks exist
                                    if (availableBanks.size > 1 && searchQuery.isBlank()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 6.dp)
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            availableBanks.forEach { bankNum ->
                                                val isBankSelected = selectedBank == bankNum
                                                val bankLabel = when (bankNum) {
                                                    0 -> "Bank 0 (Main)"
                                                    128 -> "Bank 128 (Drums)"
                                                    else -> "Bank $bankNum"
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isBankSelected) Color(0xFF9BC0F8) else Color(0x18FFFFFF))
                                                        .border(
                                                            1.dp,
                                                            if (isBankSelected) Color(0xFF9BC0F8) else Color(0x22FFFFFF),
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable { selectedBank = bankNum }
                                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = bankLabel,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isBankSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isBankSelected) Color(0xFF101726) else Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    LazyColumn(
                                        state = presetListState,
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(
                                            items = filteredPresets,
                                            key = { "${it.bankNumber}:${it.id}" }
                                        ) { preset ->
                                            val isSelected = selectedPresetId == preset.id && (availableBanks.size <= 1 || selectedBank == preset.bankNumber)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(44.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isSelected) Color(0x2A9BC0F8) else Color(0x14FFFFFF))
                                                    .clickable {
                                                        if (onSelectPresetFull != null) {
                                                            onSelectPresetFull(preset)
                                                        } else {
                                                            onSelectPreset(preset.id)
                                                        }
                                                    }
                                                    .padding(horizontal = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Number Badge
                                                Box(
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) Color(0xFF9BC0F8) else Color(0x25FFFFFF)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "${preset.id}",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = if (isSelected) Color(0xFF101726) else Color.White
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
                                                        text = "Bank ${preset.bankNumber} · Preset #${preset.id}",
                                                        fontSize = 9.5.sp,
                                                        color = if (isSelected) Color(0xCC9BC0F8) else Color(0x77FFFFFF)
                                                    )
                                                }

                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color(0x339BC0F8))
                                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "✓ ACTIVE",
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = Color(0xFF9BC0F8)
                                                        )
                                                    }
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
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = Color(0xFF9BC0F8),
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Text(
                                            text = if (searchQuery.isNotEmpty()) "No .sf2 files match \"$searchQuery\""
                                            else "No SoundFont (.sf2) files detected.",
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
                                                    .background(Color(0xFF9BC0F8))
                                                    .clickable { onImportSf2() },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Importer un fichier .sf2",
                                                    tint = Color(0xFF101726),
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
                                        val isDeletePrompt = pendingDeleteFile == file

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isLoaded) Color(0x2A9BC0F8) else Color(0x14FFFFFF))
                                                .combinedClickable(
                                                    onClick = {
                                                        if (!isDeletePrompt) {
                                                            if (isLoaded) {
                                                                onUnloadSoundFont?.invoke()
                                                            } else {
                                                                onSelectSf2File?.invoke(file)
                                                                onTabChange("bank")
                                                            }
                                                        } else {
                                                            pendingDeleteFile = null
                                                        }
                                                    },
                                                    onLongClick = { pendingDeleteFile = file }
                                                )
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
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = Color(0xFF9BC0F8),
                                                    modifier = Modifier.size(13.dp)
                                                )
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

                                            AnimatedVisibility(
                                                visible = isDeletePrompt,
                                                enter = scaleIn(initialScale = 0.35f, animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(180)),
                                                exit = scaleOut(targetScale = 0.35f, animationSpec = tween(150)) + fadeOut(tween(150))
                                            ) {
                                                FloatingTrashBubble(
                                                    onClick = {
                                                        if (onDeleteSf2File != null) {
                                                            onDeleteSf2File.invoke(file)
                                                        } else {
                                                            val javaFile = java.io.File(file.path)
                                                            if (javaFile.exists()) {
                                                                javaFile.delete()
                                                            }
                                                        }
                                                        pendingDeleteFile = null
                                                    }
                                                )
                                            }

                                            if (!isDeletePrompt) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isLoaded) Color(0x339BC0F8) else Color(0x11FFFFFF))
                                                        .border(
                                                            width = 1.2.dp,
                                                            color = if (isLoaded) Color(0xFF9BC0F8) else Color(0x44FFFFFF),
                                                            shape = CircleShape
                                                        )
                                                        .clickable {
                                                            if (isLoaded) {
                                                                onUnloadSoundFont?.invoke()
                                                            } else {
                                                                onSelectSf2File?.invoke(file)
                                                                onTabChange("bank")
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isLoaded) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Soundfont Chargée (cliquer pour décharger)",
                                                            tint = Color(0xFF9BC0F8),
                                                            modifier = Modifier.size(14.dp)
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
}
}
}

@Composable
private fun FloatingTrashBubble(
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "floating_trash_bubble")
    val floatOffsetY by infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trash_float_y"
    )
    val floatScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trash_float_scale"
    )

    Box(
        modifier = Modifier
            .offset(y = floatOffsetY.dp)
            .graphicsLayer {
                scaleX = floatScale
                scaleY = floatScale
            }
            .size(30.dp)
            .shadow(4.dp, CircleShape, ambientColor = Color(0x66FF2A55), spotColor = Color(0x99FF2A55))
            .clip(CircleShape)
            .background(Color(0xFFFF2A55))
            // Strict rule: Aucune bordure / aucun contour
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Delete",
            tint = Color.White,
            modifier = Modifier.size(15.dp)
        )
    }
}
