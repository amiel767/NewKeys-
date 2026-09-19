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
import com.soundstage.mixer.model.ScenePreset
import com.soundstage.mixer.model.StorageItem
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

// Nuances exactes des 8 pads issues de soundstage_drumpad.svg
private val ColorPadKick1 = Color(0xFFC92A45)
private val ColorPadClap1 = Color(0xFFC47A2B)
private val ColorPadWood1 = Color(0xFF6E1E30)
private val ColorPadTumb1 = Color(0xFF61182B)
private val ColorPadTom1 = Color(0xFF6E1E30)
private val ColorPadTom2 = Color(0xFF6E1E30)
private val ColorPadTom3 = Color(0xFFD68F7A)
private val ColorPadSubKick = Color(0xFF8B4868)

@Composable
fun DrumPadPage(
    drumPads: List<DrumPadItem>,
    isMixMode: Boolean,
    onToggleMixMode: () -> Unit,
    feelSwing: Int,
    onFeelSwingChange: (Int) -> Unit,
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
    onBackToMixer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showNewSceneDialog by remember { mutableStateOf(false) }
    var newSceneName by remember { mutableStateOf("") }
    var isDarkTheme by remember { mutableStateOf(true) }
    var tonicOctave by remember { mutableIntStateOf(3) }
    var isMultiPadEnabled by remember { mutableStateOf(false) }
    var tonicVolume by remember { mutableFloatStateOf(0.85f) }
    var tonicShimmer by remember { mutableFloatStateOf(0.30f) }

    val chromaticSharps = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val chromaticFlats = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    val currentChromaticNotes = if (useFlats) chromaticFlats else chromaticSharps

    val defaultPadColors = listOf(
        ColorPadKick1, ColorPadClap1, ColorPadWood1, ColorPadTumb1,
        ColorPadTom1, ColorPadTom2, ColorPadTom3, ColorPadSubKick
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(DrumPadCanvasDark, DrumPadCanvasDarkEnd))
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

                // Boutons d'accès direct [📁 Fichiers] & [🔁 Loops] déplacés dans la TopBar
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

                // Feel Swing %
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable {
                            val nextSwing = if (feelSwing >= 75) 0 else feelSwing + 25
                            onFeelSwingChange(nextSwing)
                        }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Waves, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Text("Feel Swing $feelSwing %", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
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

                // Bouton Thème (Soleil / Lune)
                IconButton(
                    onClick = { isDarkTheme = !isDarkTheme },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.WbSunny else Icons.Default.DarkMode,
                        contentDescription = "Thème",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
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
                                    .clickable { if (tonicOctave > 1) tonicOctave-- },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Octave moins", tint = Color.White, modifier = Modifier.size(11.dp))
                            }
                            Text("$tonicOctave", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x22FFFFFF))
                                    .clickable { if (tonicOctave < 6) tonicOctave++ },
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
                                .clickable { isMultiPadEnabled = !isMultiPadEnabled }
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

                        // Carte Kit : [Nom dynamique du drum kit] -> Clic ouvre le dossier Loops
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x14FFFFFF))
                                .clickable { onOpenLoopsView() }
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

                        // Ligne Note active + Bouton Stop rouge arrondi (sans texte "Drone arrêté")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                val displayNote = activeTonicNotes.firstOrNull() ?: "---"
                                Text(
                                    text = displayNote,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (activeTonicNotes.isNotEmpty()) TonicPadLedGreen else Color(0x44FFFFFF))
                                )
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
                                    onValueChange = { tonicVolume = it },
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
                                    onValueChange = { tonicShimmer = it },
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

                // ================= FLANC DROIT : LES 8 DRUM PADS (GRILLE 2x4) =================
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Rangée 1 (Pads D1 à D4)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (i in 0 until 4) {
                            val pad = drumPads.getOrNull(i) ?: DrumPadItem(id = i + 1, label = "Pad ${i + 1}")
                            val padColor = defaultPadColors.getOrElse(i) { ColorPadKick1 }

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

                    // Rangée 2 (Pads D5 à D8)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (i in 4 until 8) {
                            val pad = drumPads.getOrNull(i) ?: DrumPadItem(id = i + 1, label = "Pad ${i + 1}")
                            val padColor = defaultPadColors.getOrElse(i) { ColorPadKick1 }

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
                    detectVerticalDragGestures { _, dragAmount ->
                        val delta = -dragAmount / 200f
                        val newVol = (pad.volume + delta).coerceIn(0f, 1f)
                        onVolumeChange(newVol)
                    }
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

                // Grand pourcentage blanc au centre
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$volPercent%",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
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

                // Fine barre de volume / wave blanche
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0x33FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(pad.volume)
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
