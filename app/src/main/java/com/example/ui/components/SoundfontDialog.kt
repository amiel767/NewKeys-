package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SoundfontBankFile
import com.example.model.SoundfontPreset
import com.example.model.StorageItem
import com.example.ui.theme.*

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
        "drum" -> "Soundfont — Drum Pad"
        "pad" -> "Soundfont — Tonic Pad"
        else -> if (trackId == 0) "Soundfont — Master" else "Soundfont — Piste $trackId"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x6608080C))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .fillMaxHeight(0.85f)
                .shadow(24.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF2A2A38), Color(0xFF1B1B24), Color(0xFF17171F))
                    )
                )
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                .clickable(enabled = false) {}
                .padding(14.dp)
                .testTag("dialog_soundfont")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(8.dp))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "✕", fontSize = 12.sp, color = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tabs (Presets du Soundfont, Fichiers Soundfonts)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("bank" to "Presets Soundfont", "other" to "Fichiers .SF2").forEach { (tabKey, tabLabel) ->
                        val isSelected = activeTab == tabKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) Brush.verticalGradient(listOf(NeonCyanLight, NeonCyan)) else Brush.linearGradient(listOf(Color(0x0DFFFFFF), Color(0x08FFFFFF)))
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color.Transparent else Color(0x14FFFFFF),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onTabChange(tabKey) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF003844) else TextDim
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Content Lists
                if (activeTab == "bank") {
                    val memoizedPresets = androidx.compose.runtime.remember(presets) {
                        presets.sortedWith(compareBy({ it.bankNumber }, { it.id }))
                    }

                    if (memoizedPresets.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Aucun preset disponible.\nSélectionnez une banque .sf2 dans l'onglet 'Fichiers .SF2'.",
                                fontSize = 11.sp,
                                color = TextDim,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(
                                items = memoizedPresets,
                                key = { "${it.bankNumber}:${it.id}" }
                            ) { preset ->
                                val isSelected = selectedPresetId == preset.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) Color(0x2622D3EE) else Color(0x0AFFFFFF)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) NeonCyan else Color(0x14FFFFFF),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onSelectPreset(preset.id) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(Brush.linearGradient(listOf(NeonCyanLight, NeonCyanDark))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${preset.id}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF003844)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = preset.name,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Bank ${preset.bankNumber} · Preset #${preset.id}",
                                            fontSize = 9.5.sp,
                                            color = TextDim2
                                        )
                                    }

                                    if (isSelected) {
                                        Text(
                                            text = "✓ Actif",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeonCyan
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Soundfonts list (.sf2 files found in storage)
                    val filesToDisplay = if (soundfontStorageFiles.isNotEmpty()) soundfontStorageFiles else bankFiles.map {
                        StorageItem(name = it.name, path = it.path, isDirectory = false, formattedSize = it.size)
                    }

                    if (filesToDisplay.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Aucun fichier .sf2 détecté dans /LiveKeys/SoundFonts/.\nPlacez vos banques SoundFont (.sf2) dans la mémoire de l'appareil.",
                                fontSize = 11.sp,
                                color = TextDim,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filesToDisplay) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x0AFFFFFF))
                                        .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(10.dp))
                                        .clickable {
                                            onSelectSf2File?.invoke(file)
                                            onTabChange("bank")
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(Brush.linearGradient(listOf(NeonPurpleLight, NeonPurple))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "🎹", fontSize = 12.sp)
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${file.size} · ${file.path}",
                                            fontSize = 9.sp,
                                            color = TextDim2,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0x2222D3EE))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Charger",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeonCyan
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
