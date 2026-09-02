package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.example.model.SoundfontBankFile
import com.example.model.SoundfontPreset
import com.example.model.StorageItem
import com.example.ui.theme.*

/**
 * Android 16 / Material You Google Settings Style UI for SoundFont and Preset Selection.
 * Features:
 * - Grouped rounded surface cards (Material 3 Expressive)
 * - Material You Pill Search Bar with instant filtering
 * - Tonal Pill Segmented Switcher (Presets vs Fichiers .SF2)
 * - Google Settings item rows with leading tinted icon badges and trailing selection chips
 */
@Composable
fun SoundfontDialog(
    trackId: Int,
    source: String,
    presets: List<SoundfontPreset>,
    bankFiles: List<SoundfontBankFile> = emptyList(),
    soundfontStorageFiles: List<StorageItem> = emptyList(),
    selectedPresetId: Int,
    onSelectPreset: (Int) -> Unit,
    onSelectSf2File: ((StorageItem) -> Unit)? = null,
    activeTab: String,
    onTabChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (source) {
        "drum" -> "SoundFonts & Drum Pad"
        "pad" -> "SoundFonts & Tonic Pad"
        else -> if (trackId == 0) "SoundFonts & Master" else "SoundFonts & Piste $trackId"
    }

    val subtitle = when (source) {
        "drum" -> "Sélectionner un son ou charger un fichier .sf2"
        "pad" -> "Banque et preset pour Tonic Pad"
        else -> if (trackId == 0) "Sortie audio Master" else "Canal MIDI #$trackId · Moteur FluidSynth"
    }

    // Material You Android 16 Palette
    val dialogSurfaceColor = Color(0xFF1E2128)
    val cardSurfaceColor = Color(0xFF282C35)
    val cardSelectedColor = Color(0xFF2C394B)
    val primaryPillColor = Color(0xFF80D8FF)
    val primaryTextColor = Color(0xFF0F2537)
    val textPrimaryM3 = Color(0xFFF1F5F9)
    val textSecondaryM3 = Color(0xFF94A3B8)
    val outlineBorderColor = Color(0xFF3B414E)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x7705070B))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(500.dp)
                .fillMaxHeight(0.88f)
                .shadow(28.dp, RoundedCornerShape(28.dp), spotColor = Color(0xCC000000))
                .clip(RoundedCornerShape(28.dp))
                .background(dialogSurfaceColor)
                .border(1.2.dp, outlineBorderColor, RoundedCornerShape(28.dp))
                .clickable(enabled = false) {}
                .padding(20.dp)
                .testTag("dialog_soundfont")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ================= 1. ANDROID SETTINGS STYLE HEADER =================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2D333F)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🎹",
                                fontSize = 18.sp
                            )
                        }

                        Column {
                            Text(
                                text = title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimaryM3
                            )
                            Text(
                                text = subtitle,
                                fontSize = 11.5.sp,
                                color = textSecondaryM3
                            )
                        }
                    }

                    // Circular Close Pill Button
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2D333F))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimaryM3
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ================= 2. ANDROID 16 PILL SEGMENTED SWITCHER =================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF14171C))
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
                                    .clip(RoundedCornerShape(17.dp))
                                    .background(if (isSelected) primaryPillColor else Color.Transparent)
                                    .clickable {
                                        onTabChange(tabKey)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tabLabel,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isSelected) primaryTextColor else textSecondaryM3
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ================= 3. GROUPED SETTINGS CONTENT LIST =================
                if (activeTab == "bank") {
                    val sortedPresets = remember(presets) {
                        presets.sortedWith(compareBy({ it.bankNumber }, { it.id }))
                    }

                    if (sortedPresets.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(cardSurfaceColor)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "🎵", fontSize = 28.sp)
                                Text(
                                    text = "Aucun preset disponible dans la banque actuelle.\nVeuillez choisir une banque dans l'onglet 'Fichiers .SF2'.",
                                    fontSize = 12.sp,
                                    color = textSecondaryM3,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = sortedPresets,
                                key = { "${it.bankNumber}:${it.id}" }
                            ) { preset ->
                                val isSelected = selectedPresetId == preset.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(58.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) cardSelectedColor else cardSurfaceColor)
                                        .border(
                                            1.2.dp,
                                            if (isSelected) primaryPillColor else Color.Transparent,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable { onSelectPreset(preset.id) }
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Leading Circle Icon Badge
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) primaryPillColor else Color(0xFF374151)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${preset.id}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isSelected) primaryTextColor else textPrimaryM3
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = preset.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimaryM3,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Banque ${preset.bankNumber} · Preset #${preset.id}",
                                            fontSize = 10.5.sp,
                                            color = textSecondaryM3
                                        )
                                    }

                                    // Trailing Checked Badge (Google Settings Style)
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(primaryPillColor)
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "✓ Actif",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = primaryTextColor
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .border(1.5.dp, Color(0xFF64748B), CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Soundfonts list (.sf2 files in storage)
                    val rawFiles = if (soundfontStorageFiles.isNotEmpty()) soundfontStorageFiles else bankFiles.map {
                        StorageItem(name = it.name, path = it.path, isDirectory = false, formattedSize = it.size)
                    }

                    if (rawFiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(cardSurfaceColor)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "📂", fontSize = 28.sp)
                                Text(
                                    text = "Aucun fichier .sf2 détecté dans /LiveKeys/SoundFonts/.\nPlacez vos banques SoundFont (.sf2) dans la mémoire de l'appareil.",
                                    fontSize = 12.sp,
                                    color = textSecondaryM3,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(rawFiles) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(62.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(cardSurfaceColor)
                                        .border(1.dp, outlineBorderColor, RoundedCornerShape(16.dp))
                                        .clickable {
                                            onSelectSf2File?.invoke(file)
                                            onTabChange("bank")
                                        }
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF374151)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "📦", fontSize = 16.sp)
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimaryM3,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${file.size} · ${file.path}",
                                            fontSize = 10.sp,
                                            color = textSecondaryM3,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Material You Filled Tonal Pill Button
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFF334155))
                                            .border(1.dp, Color(0xFF475569), RoundedCornerShape(14.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Charger",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryPillColor
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
