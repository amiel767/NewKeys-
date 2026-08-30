package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.example.model.LoopFile
import com.example.model.LoopFolder
import com.example.model.StorageItem
import com.example.ui.theme.*

/**
 * Modern floating MIDI Player & File Manager window accessing /LiveKeys/Midi
 */
@Composable
fun MidiFloatingPanel(
    isOpen: Boolean,
    isMidiPlaying: Boolean,
    onToggleMidiPlayPause: () -> Unit,
    midiVolume: Float,
    onMidiVolumeChange: (Float) -> Unit,
    midiFolders: List<LoopFolder>,
    activeMidiName: String,
    onToggleFolder: (String) -> Unit,
    onSelectMidiFile: (LoopFile) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    Box(
        modifier = modifier
            .width(360.dp)
            .height(290.dp)
            .shadow(24.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF090D16))
                )
            )
            .border(1.dp, Color(0x6638BDF8), RoundedCornerShape(18.dp))
            .clickable(enabled = false) {}
            .padding(12.dp)
            .testTag("panel_midi_floating")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with Large Play/Pause Button, Title, and Volume
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Large Prominent Play Button (32dp)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isMidiPlaying) {
                                    Brush.verticalGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7)))
                                } else {
                                    Brush.verticalGradient(listOf(Color(0x3338BDF8), Color(0x1A38BDF8)))
                                }
                            )
                            .border(
                                1.5.dp,
                                if (isMidiPlaying) Color(0xFF38BDF8) else Color(0x5538BDF8),
                                CircleShape
                            )
                            .clickable { onToggleMidiPlayPause() }
                            .testTag("btn_midi_panel_play"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isMidiPlaying) "❚❚" else "▶",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isMidiPlaying) Color(0xFF002233) else Color.White
                        )
                    }

                    Column {
                        Text(
                            text = "LECTEUR MIDI (.MID)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (activeMidiName != "-") activeMidiName else "Sélectionnez un fichier .mid",
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isMidiPlaying) Color(0xFF38BDF8) else TextDim
                        )
                    }
                }

                // Volume & Close
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.width(90.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(text = "VOL", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = TextDim)
                        Slider(
                            value = midiVolume,
                            onValueChange = onMidiVolumeChange,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF38BDF8),
                                activeTrackColor = Color(0xFF38BDF8),
                                inactiveTrackColor = Color(0x1AFFFFFF)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0x1AFFFFFF))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "✕", fontSize = 10.sp, color = TextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle / Path hint
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x12FFFFFF))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "DOSSIER : /LiveKeys/Midi",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8)
                )
                Text(
                    text = "${midiFolders.sumOf { it.files.size }} fichiers",
                    fontSize = 8.sp,
                    color = TextDim
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Scrollable List of Folders & MIDI Files
            if (midiFolders.isEmpty() || midiFolders.all { it.files.isEmpty() }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x08FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "🎹", fontSize = 24.sp)
                        Text(
                            text = "Aucun fichier MIDI trouvé dans /LiveKeys/Midi",
                            fontSize = 9.5.sp,
                            color = TextDim
                        )
                        Text(
                            text = "Déposez vos fichiers .mid ou .midi dans le dossier",
                            fontSize = 8.5.sp,
                            color = TextDim2
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(midiFolders) { folder ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Folder Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (folder.isOpen) Color(0x1AFFFFFF) else Color(0x0AFFFFFF))
                                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp))
                                    .clickable { onToggleFolder(folder.name) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (folder.isOpen) "▼" else "▶",
                                    fontSize = 8.sp,
                                    color = TextDim
                                )
                                Text(text = folder.icon, fontSize = 11.sp)
                                Text(
                                    text = folder.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${folder.files.size} .mid",
                                    fontSize = 8.sp,
                                    color = TextDim2
                                )
                            }

                            // Files inside folder
                            if (folder.isOpen) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    folder.files.forEach { file ->
                                        val isSel = activeMidiName == file.name
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(
                                                    if (isSel) Color(0x330284C7) else Color(0x08FFFFFF)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSel) Color(0xFF38BDF8) else Color.Transparent,
                                                    RoundedCornerShape(7.dp)
                                                )
                                                .clickable { onSelectMidiFile(file) }
                                                .padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSel && isMidiPlaying) Color(0xFF38BDF8) else Color(0x2238BDF8)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (isSel && isMidiPlaying) "❚❚" else "▶",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSel && isMidiPlaying) Color(0xFF002233) else Color.White
                                                )
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = file.name,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isSel) Color.White else TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "MIDI · ${file.bpm} BPM",
                                                    fontSize = 7.5.sp,
                                                    color = if (isSel) Color(0xFF38BDF8) else TextDim2
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
