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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
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
    val subtitle = when (source) {
        "drum" -> "Sons & Échantillons Drum Pad · .sf2"
        "pad" -> "Banque et Presets · Tonic Pad"
        else -> if (trackId == 0) "Sortie Audio Master · Moteur FluidSynth" else "Piste $trackId · Canal MIDI #$trackId"
    }

    // 3-Color Floating Aura / Halo in Background (Lissajous gentle fluid motion)
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_glass_halo")
    val animPhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_1"
    )
    val animPhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_2"
    )

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

    // Modal Background overlay
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x8A030712))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        // ================= LIQUID GLASS IOS POPUP CONTAINER =================
        Box(
            modifier = Modifier
                .width(520.dp)
                .fillMaxHeight(0.88f)
                .shadow(32.dp, RoundedCornerShape(26.dp), spotColor = Color(0x6600E5FF))
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xEE0B101C))
                // iOS Frosted Glass border
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0x40FFFFFF),
                            Color(0x14FFFFFF),
                            Color(0x22FFFFFF)
                        )
                    ),
                    shape = RoundedCornerShape(26.dp)
                )
                .clickable(enabled = false) {}
                .testTag("dialog_soundfont")
        ) {
            // 3-Color Floating Aura / Halo Canvas (moving softly in the background)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Halo 1: Cyan / Azure
                val cx1 = (w * 0.35f) + (cos(animPhase1) * (w * 0.22f))
                val cy1 = (h * 0.30f) + (sin(animPhase1 * 1.2f) * (h * 0.18f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x4200E5FF), Color(0x1800E5FF), Color.Transparent),
                        center = Offset(cx1, cy1),
                        radius = w * 0.48f
                    ),
                    center = Offset(cx1, cy1),
                    radius = w * 0.48f
                )

                // Halo 2: Electric Purple / Indigo
                val cx2 = (w * 0.68f) + (sin(animPhase2) * (w * 0.20f))
                val cy2 = (h * 0.65f) + (cos(animPhase2 * 0.9f) * (h * 0.22f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x3A8B5CF6), Color(0x148B5CF6), Color.Transparent),
                        center = Offset(cx2, cy2),
                        radius = w * 0.52f
                    ),
                    center = Offset(cx2, cy2),
                    radius = w * 0.52f
                )

                // Halo 3: Soft Coral / Rose
                val cx3 = (w * 0.50f) + (cos(animPhase2 * 1.3f) * (w * 0.25f))
                val cy3 = (h * 0.20f) + (sin(animPhase1 * 0.8f) * (h * 0.15f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x32FA5C7C), Color(0x10FA5C7C), Color.Transparent),
                        center = Offset(cx3, cy3),
                        radius = w * 0.42f
                    ),
                    center = Offset(cx3, cy3),
                    radius = w * 0.42f
                )
            }

            // Translucent Glass Blur / Frosted Shield (ensures supreme text contrast)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xCC0D1322),
                                Color(0xD40A0F1A),
                                Color(0xDE070A12)
                            )
                        )
                    )
            )

            // Specular Glass Top Highlight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0x28FFFFFF),
                                Color(0x08FFFFFF),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // ================= 1. CLEAN HEADER (NO KEYBOARD ICON, NO WINDOW TITLE) =================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Subtitle / Slot status indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF))
                        )
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xDDCBD5E1),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Actions: [ + Importer .sf2 ] [ ✕ ]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onImportSf2 != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x2500E5FF))
                                    .border(1.dp, Color(0x6600E5FF), RoundedCornerShape(16.dp))
                                    .clickable { onImportSf2() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("+", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF00E5FF))
                                    Text("Importer .sf2", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                                }
                            }
                        }

                        // Close Pill
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF))
                                .border(0.8.dp, Color(0x33FFFFFF), CircleShape)
                                .clickable { onClose() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ================= 2. LIQUID GLASS PILL SEGMENTED SWITCHER =================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color(0x2B0A0E18))
                        .border(1.dp, Color(0x1EFFFFFF), RoundedCornerShape(19.dp))
                        .padding(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("bank" to "Presets Soundfont", "other" to "Fichiers .SF2").forEach { (tabKey, tabLabel) ->
                            val isSelected = activeTab == tabKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) {
                                            Brush.horizontalGradient(
                                                listOf(Color(0x4000E5FF), Color(0x388B5CF6))
                                            )
                                        } else SolidColor(Color.Transparent)
                                    )
                                    .border(
                                        width = if (isSelected) 1.dp else 0.dp,
                                        color = if (isSelected) Color(0x6600E5FF) else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { onTabChange(tabKey) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tabLabel,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0x99CBD5E1)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ================= 3. LIQUID GLASS SEARCH BAR =================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x1EFFFFFF))
                        .border(0.8.dp, Color(0x24FFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp),
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
                            modifier = Modifier.size(15.dp)
                        )
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(Color(0xFF00E5FF)),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = if (activeTab == "bank") "Rechercher un preset..." else "Rechercher un fichier .sf2...",
                                        color = Color(0x55FFFFFF),
                                        fontSize = 12.sp
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
                                    .size(14.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ================= 4. CONTENT LIST WITH POSITION PERSISTENCE =================
                if (activeTab == "bank") {
                    if (filteredPresets.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0x14FFFFFF))
                                .border(0.8.dp, Color(0x18FFFFFF), RoundedCornerShape(18.dp))
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
                                    else "Aucun preset sélectionné pour ce slot.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xAAFFFFFF),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = presetListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(
                                items = filteredPresets,
                                key = { "${it.bankNumber}:${it.id}" }
                            ) { preset ->
                                val isSelected = selectedPresetId == preset.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isSelected) Color(0x2A00E5FF) else Color(0x14FFFFFF)
                                        )
                                        .border(
                                            width = if (isSelected) 1.2.dp else 0.8.dp,
                                            color = if (isSelected) Color(0xFF00E5FF) else Color(0x18FFFFFF),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable { onSelectPreset(preset.id) }
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Number Badge
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color(0xFF00E5FF) else Color(0x25FFFFFF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${preset.id}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isSelected) Color(0xFF0B101B) else Color.White
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = preset.name,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else Color(0xEEFFFFFF),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Banque ${preset.bankNumber} · Preset #${preset.id}",
                                            fontSize = 10.sp,
                                            color = if (isSelected) Color(0xCC00E5FF) else Color(0x77FFFFFF)
                                        )
                                    }

                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0x3300E5FF))
                                                .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "✓ ACTIF",
                                                fontSize = 10.sp,
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
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    } else {
                        LazyColumn(
                            state = fileListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredFiles) { file ->
                                val isLoaded = selectedSoundfontName.isNotBlank() &&
                                        file.name.contains(selectedSoundfontName, ignoreCase = true)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isLoaded) Color(0x268B5CF6) else Color(0x14FFFFFF))
                                        .border(
                                            width = if (isLoaded) 1.2.dp else 0.8.dp,
                                            color = if (isLoaded) Color(0xFFA78BFA) else Color(0x18FFFFFF),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            onSelectSf2File?.invoke(file)
                                            onTabChange("bank")
                                        }
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x22FFFFFF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "📦", fontSize = 15.sp)
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (file.formattedSize.isNotEmpty()) file.formattedSize else "Soundfont SF2",
                                            fontSize = 10.sp,
                                            color = Color(0x77FFFFFF)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isLoaded) Color(0x44A78BFA) else Color(0x2200E5FF))
                                            .border(
                                                1.dp,
                                                if (isLoaded) Color(0xFFA78BFA) else Color(0x8800E5FF),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (isLoaded) "CHARGÉ" else "CHARGER",
                                            fontSize = 10.5.sp,
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
