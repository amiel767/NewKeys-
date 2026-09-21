package com.soundstage.mixer.ui.pages

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.DrumPadItem
import com.soundstage.mixer.model.GospelChord
import com.soundstage.mixer.model.ScenePreset
import com.soundstage.mixer.model.StorageItem
import com.soundstage.mixer.model.AppTheme
import com.soundstage.mixer.ui.theme.*

// Palettes Material You fidèles aux maquettes SVG (soundstage_drumpad.svg et soundstage_drumpad_mix.svg)
private val DrumPadCanvasDark = Color(0xFF4C0E1A)
private val DrumPadCanvasDarkEnd = Color(0xFF330912)
private val TopBarCardBg = Color(0x33FFFFFF)
private val TopBarCardBorder = Color(0x22FFFFFF)
private val TonicPadKeyDark = Color(0xFF1E1418)
private val TonicPadKeyActive = Color(0xFFC44066)
private val TonicPadLedGreen = Color(0xFF4ADE80)
private val StopButtonRed = Color(0xFFDC2626)

// Palettes dynamiques des 8 pads selon le thème sélectionné
private fun getThemePadColors(theme: AppTheme): List<Color> = when (theme) {
    AppTheme.CYBER_VIOLET -> listOf(
        Color(0xFF8B5CF6), Color(0xFF06B6D4), Color(0xFFEC4899), Color(0xFF6366F1),
        Color(0xFF3B82F6), Color(0xFFA855F7), Color(0xFFF43F5E), Color(0xFF14B8A6)
    )
    AppTheme.RUBY_VELVET -> listOf(
        Color(0xFFC92A45), Color(0xFFC47A2B), Color(0xFF801B2E), Color(0xFF6E1E30),
        Color(0xFF8B4868), Color(0xFFD68F7A), Color(0xFFB91C1C), Color(0xFF9F1239)
    )
    AppTheme.NEON_AMBER -> listOf(
        Color(0xFFF59E0B), Color(0xFFD97706), Color(0xFFB45309), Color(0xFFEAB308),
        Color(0xFFCA8A04), Color(0xFFFB923C), Color(0xFFF97316), Color(0xFFEA580C)
    )
    AppTheme.EMERALD_SYNTH -> listOf(
        Color(0xFF10B981), Color(0xFF059669), Color(0xFF047857), Color(0xFF34D399),
        Color(0xFF14B8A6), Color(0xFF0D9488), Color(0xFF84CC16), Color(0xFF65A30D)
    )
    AppTheme.DEEP_OCEAN -> listOf(
        Color(0xFF2563EB), Color(0xFF1D4ED8), Color(0xFF1E40AF), Color(0xFF0284C7),
        Color(0xFF0369A1), Color(0xFF0891B2), Color(0xFF0E7490), Color(0xFF38BDF8)
    )
}

@Composable
fun DrumPadPage(
    drumPads: List<DrumPadItem>,
    isMixMode: Boolean,
    onToggleMixMode: () -> Unit,
    currentTheme: AppTheme = AppTheme.CYBER_VIOLET,
    onCycleTheme: () -> Unit = {},
    feelSwing: Int = 50,
    onFeelSwingChange: (Int) -> Unit = {},
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    scenes: List<ScenePreset>,
    activeSceneId: String,
    onSelectScene: (String) -> Unit,
    onCreateBlankScene: (String) -> Unit,
    onDuplicateScene: (String) -> Unit,
    activeTonicNotes: Set<String>,
    onTonicNoteClick: (String) -> Unit,
    useFlats: Boolean,
    onToggleUseFlats: () -> Unit,
    activeTonicPadName: String,
    onOpenTonicSoundPicker: () -> Unit,
    activeDrumKitName: String,
    onOpenDrumKitPicker: () -> Unit,
    onStopTonicDrone: () -> Unit,
    tonicOctaveRange: String = "C3 — C4",
    onTonicOctaveMinus: () -> Unit = {},
    onTonicOctavePlus: () -> Unit = {},
    isMultiPadEnabled: Boolean = false,
    onToggleMultiPad: () -> Unit = {},
    tonicVolume: Float = 0.85f,
    onTonicVolumeChange: (Float) -> Unit = {},
    tonicShimmer: Float = 0.30f,
    onTonicShimmerChange: (Float) -> Unit = {},
    isMiniBrowserOpen: Boolean = false,
    onToggleMiniBrowser: () -> Unit = {},
    onCloseMiniBrowser: () -> Unit = {},
    audioFiles: List<StorageItem> = emptyList(),
    onPreviewAudioFile: (StorageItem) -> Unit = {},
    onAssignSampleToPad: (Int, StorageItem) -> Unit = { _, _ -> },
    onPadPressed: (Int) -> Unit,
    onPadReleased: (Int) -> Unit,
    onPadVolumeChange: (Int, Float) -> Unit,
    onLoopBeatsChange: (Int, String) -> Unit = { _, _ -> },
    onOpenFileExplorer: () -> Unit,
    onOpenLoopsView: () -> Unit,
    onOpenStepDrum: () -> Unit = {},
    onBackToMixer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showNewSceneDialog by remember { mutableStateOf(false) }
    var newSceneName by remember { mutableStateOf("") }
    var isDarkTheme by remember { mutableStateOf(true) }

    val chromaticSharps = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val chromaticFlats = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    val currentChromaticNotes = if (useFlats) chromaticFlats else chromaticSharps

    // Display formatted octave (e.g. C3 or 3)
    val displayOctaveNumber = remember(tonicOctaveRange) {
        val match = Regex("C([1-6])").find(tonicOctaveRange)
        match?.groupValues?.getOrNull(1) ?: "3"
    }

    val defaultPadColors = remember(currentTheme) {
        getThemePadColors(currentTheme)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(currentTheme.canvasDark, currentTheme.canvasDarkEnd))
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("drumpad_page_root")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ================= 1. BARRE SUPÉRIEURE (SVG MATCH EXACT) =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TopBarCardBg)
                    .border(1.dp, TopBarCardBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Menu Burger (Retour Mixeur)
                IconButton(
                    onClick = onBackToMixer,
                    modifier = Modifier.size(32.dp).testTag("btn_drumpad_back_mixer")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Retour Mixeur",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "Soundstage",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Boutons d'accès direct [📁 Fichiers] & [🔁 Loops] & [🥁 StepDrum]
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x22FFFFFF))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenFileExplorer() }
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = "Fichiers", tint = Color.White, modifier = Modifier.size(13.dp))
                        Text("Fichiers", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenLoopsView() }
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Repeat, contentDescription = "Loops", tint = NeonCyan, modifier = Modifier.size(13.dp))
                        Text("Loops", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33F43F5E))
                            .clickable { onOpenStepDrum() }
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = "StepDrum", tint = Color(0xFFFF5C8A), modifier = Modifier.size(13.dp))
                        Text("StepDrum", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF5C8A))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Sélecteur Scène 1 2 3 4 + bouton '+'
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Scène", fontSize = 11.sp, color = Color(0xCCFFFFFF), fontWeight = FontWeight.Medium)
                    listOf("1", "2", "3", "4").forEach { sceneNum ->
                        val isSelected = activeSceneId.contains(sceneNum) || (activeSceneId.isEmpty() && sceneNum == "1")
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White else Color(0x22FFFFFF))
                                .clickable { onSelectScene("scene_$sceneNum") },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sceneNum,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else Color.White
                            )
                        }
                    }

                    // Bouton '+' pour ajouter/dupliquer une scène
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF))
                            .clickable {
                                newSceneName = "Scène ${scenes.size + 1}"
                                showNewSceneDialog = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Nouvelle scène", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }

                // Bouton Thème Dynamique (Icône fixe 32dp x 32dp, taille stable sans texte)
                IconButton(
                    onClick = onCycleTheme,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                        .border(1.dp, currentTheme.primaryColor.copy(alpha = 0.7f), CircleShape)
                        .testTag("btn_drumpad_theme")
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Thème ${currentTheme.displayName}",
                        tint = currentTheme.primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // BPM avec boutons - et +
                Row(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x22FFFFFF))
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                            .clickable { onBpmChange((bpm - 1).coerceAtLeast(40)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "BPM moins", tint = Color.White, modifier = Modifier.size(12.dp))
                    }

                    Text("$bpm BPM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))

                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                            .clickable { onBpmChange((bpm + 1).coerceAtMost(240)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "BPM plus", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }

                // Bouton Mix (Active le mode faders verticaux)
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isMixMode) Color.White else Color(0x22FFFFFF))
                        .clickable { onToggleMixMode() }
                        .padding(horizontal = 8.dp)
                        .testTag("btn_drumpad_mix_mode"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Mix",
                            tint = if (isMixMode) Color.Black else Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Mix",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMixMode) Color.Black else Color.White
                        )
                    }
                }

                // Bouton Thème rapide
                IconButton(
                    onClick = { onCycleTheme() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Changer Thème",
                        tint = currentTheme.primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ================= 2. CORPS PRINCIPAL (TONICPAD GAUCHE + DRUMPADS DROITE) =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ================= FLANC GAUCHE : TONIC PAD ULTRA FIDÈLE AU SVG =================
                Column(
                    modifier = Modifier
                        .width(180.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Contrôle d'octave et Multi-Pad en haut du TonicPad (sans texte superflu)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x1FFFFFFF))
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Octave - / +
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Oct", fontSize = 10.sp, color = Color(0xCCFFFFFF), fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x22FFFFFF))
                                    .clickable { onTonicOctaveMinus() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Octave moins", tint = Color.White, modifier = Modifier.size(11.dp))
                            }
                            Text(displayOctaveNumber, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x22FFFFFF))
                                    .clickable { onTonicOctavePlus() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Octave plus", tint = Color.White, modifier = Modifier.size(11.dp))
                            }
                        }

                        // Bascule Multi-Pad
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isMultiPadEnabled) TonicPadKeyActive else Color(0x22FFFFFF))
                                .clickable { onToggleMultiPad() }
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Multi",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // Bascule Dièses / Bémols
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable { onToggleUseFlats() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (useFlats) "b" else "#",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Grille 4x3 de parfaits carrés (ratio 1:1, squarcles, non déformés)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (row in 0 until 4) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (col in 0 until 3) {
                                    val index = row * 3 + col
                                    val note = currentChromaticNotes.getOrElse(index) { "C" }
                                    val isActive = activeTonicNotes.contains(note)

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isActive) TonicPadKeyActive else TonicPadKeyDark)
                                            .border(
                                                1.dp,
                                                if (isActive) Color.White.copy(alpha = 0.5f) else Color(0x1AFFFFFF),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable { onTonicNoteClick(note) }
                                            .testTag("tonic_note_$note"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = note,
                                            fontSize = 13.sp,
                                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Panneau d'état & Cartes dynamiques de sons
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1FFFFFFF))
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Carte Pad : [Nom dynamique du preset soundfont]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x14FFFFFF))
                                .clickable { onOpenTonicSoundPicker() }
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Waves, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                Text(
                                    text = "Pad: $activeTonicPadName",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0x88FFFFFF), modifier = Modifier.size(13.dp))
                        }

                        // Carte Kit : [Nom dynamique du drum kit] -> Clic ouvre le sélecteur inline / DrumPad
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x14FFFFFF))
                                .clickable { onOpenDrumKitPicker() }
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Apps, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                Text(
                                    text = "Kit: $activeDrumKitName",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0x88FFFFFF), modifier = Modifier.size(13.dp))
                        }

                        // Ligne Note active + Double Dénomination Accords Gospel/Jazz + Bouton Stop rouge
                        val displayNote = activeTonicNotes.firstOrNull() ?: "---"
                        val gospelChordInfo = remember(displayNote) {
                            when (displayNote) {
                                "C" -> GospelChord("C Maj9 (Drone)", "C / C", "G / C")
                                "C#", "Db" -> GospelChord("Db min9", "Ab min \\ Db min", "E Maj7 \\ Db")
                                "D" -> GospelChord("D min11", "A min \\ D min", "F Maj7 \\ D")
                                "D#", "Eb" -> GospelChord("Eb Maj9", "Bb min \\ Eb min", "Gb Maj7 \\ Eb")
                                "E" -> GospelChord("E min9", "B min \\ E min", "G Maj7 \\ E")
                                "F" -> GospelChord("F Maj9", "C min \\ F min", "Ab Maj7 \\ F")
                                "F#", "Gb" -> GospelChord("Gb min11", "Db min \\ Gb min", "A Maj7 \\ Gb")
                                "G" -> GospelChord("G Maj9", "D min \\ G min", "Bb Maj7 \\ G")
                                "G#", "Ab" -> GospelChord("Ab Maj9", "Eb min \\ Ab min", "B Maj7 \\ Ab")
                                "A" -> GospelChord("A min9", "E min \\ A min", "C Maj7 \\ A")
                                "A#", "Bb" -> GospelChord("Bb min9", "F min \\ Bb min", "C# Maj7 \\ Bb")
                                "B" -> GospelChord("B min9", "F# min \\ B min", "D Maj7 \\ B")
                                else -> GospelChord("---", "---", "---")
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x18FFFFFF))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = if (activeTonicNotes.isNotEmpty()) gospelChordInfo.mainChord else "---",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (activeTonicNotes.isNotEmpty()) Color(0xFFFFD166) else Color.White
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (activeTonicNotes.isNotEmpty()) TonicPadLedGreen else Color(0x44FFFFFF))
                                    )
                                }
                                if (activeTonicNotes.isNotEmpty()) {
                                    Text(
                                        text = "${gospelChordInfo.slashDecomp1} • ${gospelChordInfo.slashDecomp2}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Bouton rouge carré arrondi Stop ⏹
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(StopButtonRed)
                                    .clickable { onStopTonicDrone() }
                                    .testTag("btn_stop_tonic_drone"),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.White)
                                )
                            }
                        }

                        // Deux petits sliders : Volume et Shimmer (prise d'effet immédiate)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Slider Volume
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Vol ${(tonicVolume * 100).toInt()}%", fontSize = 8.5.sp, color = Color(0xCCFFFFFF))
                                Slider(
                                    value = tonicVolume,
                                    onValueChange = { onTonicVolumeChange(it) },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = TonicPadKeyActive,
                                        inactiveTrackColor = Color(0x33FFFFFF)
                                    ),
                                    modifier = Modifier.height(18.dp)
                                )
                            }

                            // Slider Shimmer
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Shimmer ${(tonicShimmer * 100).toInt()}%", fontSize = 8.5.sp, color = Color(0xCCFFFFFF))
                                Slider(
                                    value = tonicShimmer,
                                    onValueChange = { onTonicShimmerChange(it) },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color(0xFFF59E0B),
                                        inactiveTrackColor = Color(0x33FFFFFF)
                                    ),
                                    modifier = Modifier.height(18.dp)
                                )
                            }
                        }
                    }
                }

                // ================= FLANC DROIT : LES 8 DRUM PADS OU GESTIONNAIRE SUR D3, D4, D7, D8 =================
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. BLOC GAUCHE (D1, D2 en haut / D5, D6 en bas) - TOUJOURS VISIBLES ET JOUABLES
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Rangée haut : D1, D2
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0, 1).forEach { idx ->
                                val pad = drumPads.getOrNull(idx) ?: DrumPadItem(id = idx + 1, label = "Pad ${idx + 1}")
                                val padColor = defaultPadColors.getOrElse(idx) { Color(0xFFC92A45) }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    SingleDrumPadBox(
                                        pad = pad,
                                        padColor = padColor,
                                        isMixMode = isMixMode,
                                        onPadPressed = { onPadPressed(pad.id) },
                                        onPadReleased = { onPadReleased(pad.id) },
                                        onVolumeChange = { onPadVolumeChange(pad.id, it) },
                                        onLoopBeatsChange = { beats -> onLoopBeatsChange(pad.id, beats) }
                                    )
                                }
                            }
                        }

                        // Rangée bas : D5, D6
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(4, 5).forEach { idx ->
                                val pad = drumPads.getOrNull(idx) ?: DrumPadItem(id = idx + 1, label = "Pad ${idx + 1}")
                                val padColor = defaultPadColors.getOrElse(idx) { Color(0xFFC92A45) }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    SingleDrumPadBox(
                                        pad = pad,
                                        padColor = padColor,
                                        isMixMode = isMixMode,
                                        onPadPressed = { onPadPressed(pad.id) },
                                        onPadReleased = { onPadReleased(pad.id) },
                                        onVolumeChange = { onPadVolumeChange(pad.id, it) },
                                        onLoopBeatsChange = { beats -> onLoopBeatsChange(pad.id, beats) }
                                    )
                                }
                            }
                        }
                    }

                    // 2. BLOC DROITE (D3, D4 en haut / D7, D8 en bas) OU MINI GESTIONNAIRE DE FICHIERS RECOUVRANT CES 4 CASES
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        if (isMiniBrowserOpen) {
                            InlineDrumKitBrowserView(
                                audioFiles = audioFiles,
                                onPreviewAudioFile = onPreviewAudioFile,
                                onAssignSampleToPad = onAssignSampleToPad,
                                onClose = onCloseMiniBrowser
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Rangée haut : D3, D4
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(2, 3).forEach { idx ->
                                        val pad = drumPads.getOrNull(idx) ?: DrumPadItem(id = idx + 1, label = "Pad ${idx + 1}")
                                        val padColor = defaultPadColors.getOrElse(idx) { Color(0xFFC92A45) }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        ) {
                                            SingleDrumPadBox(
                                                pad = pad,
                                                padColor = padColor,
                                                isMixMode = isMixMode,
                                                onPadPressed = { onPadPressed(pad.id) },
                                                onPadReleased = { onPadReleased(pad.id) },
                                                onVolumeChange = { onPadVolumeChange(pad.id, it) },
                                                onLoopBeatsChange = { beats -> onLoopBeatsChange(pad.id, beats) }
                                            )
                                        }
                                    }
                                }

                                // Rangée bas : D7, D8
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(6, 7).forEach { idx ->
                                        val pad = drumPads.getOrNull(idx) ?: DrumPadItem(id = idx + 1, label = "Pad ${idx + 1}")
                                        val padColor = defaultPadColors.getOrElse(idx) { Color(0xFFC92A45) }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        ) {
                                            SingleDrumPadBox(
                                                pad = pad,
                                                padColor = padColor,
                                                isMixMode = isMixMode,
                                                onPadPressed = { onPadPressed(pad.id) },
                                                onPadReleased = { onPadReleased(pad.id) },
                                                onVolumeChange = { onPadVolumeChange(pad.id, it) },
                                                onLoopBeatsChange = { beats -> onLoopBeatsChange(pad.id, beats) }
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

        // Dialogue Nouvelle Scène (Vierge ou Dupliquée)
        if (showNewSceneDialog) {
            AlertDialog(
                onDismissRequest = { showNewSceneDialog = false },
                title = { Text("Nouvelle Scène", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newSceneName,
                            onValueChange = { newSceneName = it },
                            label = { Text("Nom de la scène") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onCreateBlankScene(newSceneName)
                                    showNewSceneDialog = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                            ) {
                                Text("✨ Vierge", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    onDuplicateScene(newSceneName)
                                    showNewSceneDialog = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                            ) {
                                Text("📋 Dupliquer", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showNewSceneDialog = false }) {
                        Text("Annuler", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1E2330)
            )
        }
    }
}

@Composable
private fun InlineDrumKitBrowserView(
    audioFiles: List<StorageItem>,
    onPreviewAudioFile: (StorageItem) -> Unit,
    onAssignSampleToPad: (Int, StorageItem) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedPadTarget by remember { mutableIntStateOf(1) }

    val filteredFiles = remember(audioFiles, searchQuery) {
        if (searchQuery.isBlank()) audioFiles
        else audioFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x22000000))
            .border(1.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(10.dp)
    ) {
        // En-tête gestionnaire
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFFFD166), modifier = Modifier.size(16.dp))
                Text(
                    text = "DRUMKITS & SAMPLES (/DrumPad)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Champ recherche et sélecteur de Pad cible
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher kit ou sample...", fontSize = 10.sp, color = Color(0x88FFFFFF)) },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFFD166),
                    unfocusedBorderColor = Color(0x44FFFFFF)
                ),
                singleLine = true
            )

            // Sélecteur pad cible D1..D8
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x22FFFFFF))
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Pad cible:", fontSize = 9.sp, color = Color(0xCCFFFFFF))
                Text(
                    text = "D$selectedPadTarget",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFD166),
                    modifier = Modifier.clickable {
                        selectedPadTarget = if (selectedPadTarget >= 8) 1 else selectedPadTarget + 1
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Liste des fichiers avec pré-écoute et assignation 1-clic
        if (filteredFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("Aucun kit audio trouvé dans /DrumPad", fontSize = 11.sp, color = Color(0x88FFFFFF))
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredFiles.size) { idx ->
                    val file = filteredFiles[idx]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x18FFFFFF))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = { onPreviewAudioFile(file) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Écouter", tint = Color(0xFF4ADE80), modifier = Modifier.size(16.dp))
                            }
                            Column {
                                Text(
                                    text = file.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (file.isDirectory) "Dossier DrumKit" else "Sample Audio (${file.formattedSize})",
                                    fontSize = 8.sp,
                                    color = Color(0x88FFFFFF)
                                )
                            }
                        }

                        Button(
                            onClick = { onAssignSampleToPad(selectedPadTarget, file) },
                            modifier = Modifier.height(26.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
                        ) {
                            Text("Assigner à D$selectedPadTarget", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleDrumPadBox(
    pad: DrumPadItem,
    padColor: Color,
    isMixMode: Boolean,
    onPadPressed: () -> Unit,
    onPadReleased: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onLoopBeatsChange: (String) -> Unit = {}
) {
    val volPercent = (pad.volume * 100).toInt()
    val currentVol by rememberUpdatedState(pad.volume)
    val onVolChangeUpdated by rememberUpdatedState(onVolumeChange)
    val isPadPressed by rememberUpdatedState(pad.isPressed)
    val isPadLooping by rememberUpdatedState(pad.isLoopPlaying)

    // Barre de progression lumineuse animée (0f à 1f)
    val playProgressAnim = remember { Animatable(0f) }

    LaunchedEffect(isPadPressed, isPadLooping) {
        if (isPadPressed || isPadLooping) {
            playProgressAnim.snapTo(0f)
            playProgressAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = if (isPadLooping) 1200 else 320,
                    easing = LinearEasing
                )
            )
            if (!isPadLooping) {
                playProgressAnim.snapTo(0f)
            }
        } else {
            playProgressAnim.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(padColor)
            .border(
                1.5.dp,
                if (pad.isPressed) Color.White else Color.White.copy(alpha = 0.18f),
                RoundedCornerShape(16.dp)
            )
            .pointerInput(pad.id, isMixMode) {
                if (isMixMode) {
                    var startDragVol = currentVol
                    var totalDragY = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            startDragVol = currentVol
                            totalDragY = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            totalDragY -= dragAmount
                            // 150px = 100% de volume
                            val deltaVol = totalDragY / 150f
                            val newVol = (startDragVol + deltaVol).coerceIn(0f, 1f)
                            onVolChangeUpdated(newVol)
                        }
                    )
                } else {
                    detectTapGestures(
                        onPress = {
                            onPadPressed()
                            tryAwaitRelease()
                            onPadReleased()
                        }
                    )
                }
            }
            .padding(10.dp)
            .testTag("drumpad_${pad.id}")
    ) {
        if (isMixMode) {
            // MODE MIX (soundstage_drumpad_mix.svg)
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = pad.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Grand pourcentage blanc au centre avec boutons - / + tactiles
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x33000000))
                            .clickable {
                                val newVol = (currentVol - 0.05f).coerceAtLeast(0f)
                                onVolChangeUpdated(newVol)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Volume moins", tint = Color.White, modifier = Modifier.size(14.dp))
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "$volPercent%",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x33000000))
                            .clickable {
                                val newVol = (currentVol + 0.05f).coerceAtMost(1f)
                                onVolChangeUpdated(newVol)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Volume plus", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }

                // Si c'est une loop assignée, affichage Temps de lecture DJ (+ - Auto, 1, 2, 4, 6, 8, etc.)
                if (pad.isLoopMode) {
                    val beatsOptions = listOf("Auto", "1", "2", "4", "6", "8", "16", "32")
                    val curIdx = beatsOptions.indexOf(pad.loopBeatsSetting).let { if (it >= 0) it else 0 }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x44000000))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0x33FFFFFF))
                                .clickable {
                                    val prevIdx = (curIdx - 1 + beatsOptions.size) % beatsOptions.size
                                    onLoopBeatsChange(beatsOptions[prevIdx])
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Temps moins", tint = Color.White, modifier = Modifier.size(10.dp))
                        }

                        Text(
                            text = "Temps: ${pad.loopBeatsSetting}",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0x33FFFFFF))
                                .clickable {
                                    val nextIdx = (curIdx + 1) % beatsOptions.size
                                    onLoopBeatsChange(beatsOptions[nextIdx])
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Temps plus", tint = Color.White, modifier = Modifier.size(10.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Pied : identifiant D1..D8 à gauche, flèches verticales ↕ à droite
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "D${pad.id}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xCCFFFFFF)
                    )

                    Icon(
                        imageVector = Icons.Default.UnfoldMore,
                        contentDescription = "Glisser pour régler",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else {
            // MODE NORMAL (soundstage_drumpad.svg)
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = pad.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.weight(1f))

                // Fine barre blanche de volume / progression intégrée (animée en lecture)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0x33FFFFFF))
                ) {
                    val fillRatio = if (playProgressAnim.value > 0f) playProgressAnim.value else pad.volume
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fillRatio)
                            .fillMaxHeight()
                            .background(Color.White)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Identifiant D1..D8 et pourcentage en bas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "D${pad.id}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xCCFFFFFF)
                    )

                    Text(
                        text = "$volPercent%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xCCFFFFFF)
                    )
                }
            }
        }
    }
}
