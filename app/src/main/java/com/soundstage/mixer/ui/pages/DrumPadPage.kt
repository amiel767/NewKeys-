package com.soundstage.mixer.ui.pages

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.soundstage.mixer.ui.components.AmbientStudioBackground
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
    onBackToMixer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chromaticSharps = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val chromaticFlats = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    val currentChromaticNotes = if (useFlats) chromaticFlats else chromaticSharps

    // Display formatted octave (e.g. C3 or 3)
    val displayOctaveNumber = remember(tonicOctaveRange) {
        val match = Regex("C([1-6])").find(tonicOctaveRange)
        match?.groupValues?.getOrNull(1) ?: "3"
    }

    var sampleToAssign by remember { mutableStateOf<StorageItem?>(null) }

    // Fallback default samples if audioFiles list is empty
    val effectiveAudioFiles = remember(audioFiles) {
        if (audioFiles.isNotEmpty()) audioFiles else listOf(
            StorageItem("Kick_808.wav", "/sdcard/DrumPad/Kick_808.wav", false, extension = "wav"),
            StorageItem("Snare_Studio_Punch.wav", "/sdcard/DrumPad/Snare_Studio_Punch.wav", false, extension = "wav"),
            StorageItem("Clap_Hand_Pro.wav", "/sdcard/DrumPad/Clap_Hand_Pro.wav", false, extension = "wav"),
            StorageItem("HiHat_Closed_Tight.wav", "/sdcard/DrumPad/HiHat_Closed_Tight.wav", false, extension = "wav"),
            StorageItem("HiHat_Open_Sizzle.wav", "/sdcard/DrumPad/HiHat_Open_Sizzle.wav", false, extension = "wav"),
            StorageItem("Tom_High_Studio.wav", "/sdcard/DrumPad/Tom_High_Studio.wav", false, extension = "wav"),
            StorageItem("Tom_Low_Deep.wav", "/sdcard/DrumPad/Tom_Low_Deep.wav", false, extension = "wav"),
            StorageItem("Perc_Rimshot.wav", "/sdcard/DrumPad/Perc_Rimshot.wav", false, extension = "wav"),
            StorageItem("Crash_Cymbal_16.wav", "/sdcard/DrumPad/Crash_Cymbal_16.wav", false, extension = "wav"),
            StorageItem("Sub_Kick_Analog.wav", "/sdcard/DrumPad/Sub_Kick_Analog.wav", false, extension = "wav")
        )
    }

    AmbientStudioBackground(
        modifier = modifier
            .fillMaxSize()
            .testTag("drumpad_page_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 4.dp)
        ) {
            // ================= 1. BARRE SUPÉRIEURE (CLEAN & RELIEF BENTO) =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .shadow(2.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF283144), Color(0xFF1D2433))
                        )
                    )
                    .drawWithContent {
                        drawContent()
                        drawLine(
                            color = Color.White.copy(alpha = 0.12f),
                            start = Offset(6.dp.toPx(), 1f),
                            end = Offset(size.width - 6.dp.toPx(), 1f),
                            strokeWidth = 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
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

                Spacer(modifier = Modifier.weight(1f))

                // Feel Swing %
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .shadow(1.5.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF283144), Color(0xFF1D2433))
                            )
                        )
                        .drawWithContent {
                            drawContent()
                            drawLine(
                                color = Color.White.copy(alpha = 0.12f),
                                start = Offset(4.dp.toPx(), 1f),
                                end = Offset(size.width - 4.dp.toPx(), 1f),
                                strokeWidth = 1.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
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
                        .shadow(1.5.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF283144), Color(0xFF1D2433))
                            )
                        )
                        .drawWithContent {
                            drawContent()
                            drawLine(
                                color = Color.White.copy(alpha = 0.12f),
                                start = Offset(4.dp.toPx(), 1f),
                                end = Offset(size.width - 4.dp.toPx(), 1f),
                                strokeWidth = 1.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
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
                            .size(20.dp)
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
                        .shadow(1.5.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isMixMode) {
                                Brush.verticalGradient(listOf(Color(0xFF9BC0F8), Color(0xFF8FB6F2)))
                            } else {
                                Brush.verticalGradient(listOf(Color(0xFF283144), Color(0xFF1D2433)))
                            }
                        )
                        .drawWithContent {
                            drawContent()
                            if (!isMixMode) {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.12f),
                                    start = Offset(4.dp.toPx(), 1f),
                                    end = Offset(size.width - 4.dp.toPx(), 1f),
                                    strokeWidth = 1.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
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
                            tint = if (isMixMode) Color(0xFF101726) else Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Mix",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMixMode) Color(0xFF101726) else Color.White
                        )
                    }
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
                    // Contrôle d'octave et Multi-Pad en haut du TonicPad (avec relief Bento)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .shadow(1.5.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF283144), Color(0xFF1D2433))
                                )
                            )
                            .drawWithContent {
                                drawContent()
                                drawLine(
                                    color = Color.White.copy(alpha = 0.12f),
                                    start = Offset(4.dp.toPx(), 1f),
                                    end = Offset(size.width - 4.dp.toPx(), 1f),
                                    strokeWidth = 1.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
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

                    // Panneau d'état & Cartes dynamiques de sons (Bento relief)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF283144), Color(0xFF1D2433))
                                )
                            )
                            .drawWithContent {
                                drawContent()
                                drawLine(
                                    color = Color.White.copy(alpha = 0.12f),
                                    start = Offset(4.dp.toPx(), 1f),
                                    end = Offset(size.width - 4.dp.toPx(), 1f),
                                    strokeWidth = 1.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Deux petits sliders : Volume et Shimmer (déplacés en haut, juste en bas des cases de notes)
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

                        // Ligne Note active + Bouton Stop rouge arrondi
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

                        // Carte Pad : [Nom dynamique du preset soundfont] (poussée vers le bas)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .shadow(1.dp, RoundedCornerShape(6.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF283144), Color(0xFF1D2433))
                                    )
                                )
                                .drawWithContent {
                                    drawContent()
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.12f),
                                        start = Offset(3.dp.toPx(), 1f),
                                        end = Offset(size.width - 3.dp.toPx(), 1f),
                                        strokeWidth = 1.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
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

                        // Carte Kit : [Nom dynamique du drum kit] (poussée en bas) -> Clic ouvre le sélecteur de Kit
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .shadow(1.dp, RoundedCornerShape(6.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF283144), Color(0xFF1D2433))
                                    )
                                )
                                .drawWithContent {
                                    drawContent()
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.12f),
                                        start = Offset(3.dp.toPx(), 1f),
                                        end = Offset(size.width - 3.dp.toPx(), 1f),
                                        strokeWidth = 1.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
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

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                SingleDrumPadBox(
                                    pad = pad,
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

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                SingleDrumPadBox(
                                    pad = pad,
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

        // ================= OUTSIDE SCRIM BACKDROP (TOUCH OUTSIDE TO DISMISS) =================
        AnimatedVisibility(
            visible = isMiniBrowserOpen,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onCloseMiniBrowser() }
            )
        }

        // ================= DRUMKIT LIQUID GLASS OVERLAY (SLIDES FROM RIGHT OVER PADS 3,4,6,7) =================
        AnimatedVisibility(
            visible = isMiniBrowserOpen,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(320, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(250)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.52f) // Covers right column of pads (Pads 3, 4, 6, 7)
                    .shadow(16.dp, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp), ambientColor = Color.Black, spotColor = Color(0xAA000000))
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFA1A2130), Color(0xFA111522))
                        )
                    )
                    .drawWithContent {
                        drawContent()
                        // Top specular highlight for natural physical liquid glass sheen
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(12.dp.toPx(), 1f),
                            end = Offset(size.width - 2.dp.toPx(), 1f),
                            strokeWidth = 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Title: Just "DrumKit" (No X button, touch outside to dismiss)
                    Text(
                        text = "DrumKit",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "Tap to preview • Long press to assign",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Unified clean Liquid Glass list
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(effectiveAudioFiles) { sample ->
                            val cleanName = sample.name
                                .removeSuffix(".wav")
                                .removeSuffix(".mp3")
                                .removeSuffix(".ogg")
                                .replace("_", " ")

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF283144), Color(0xFF1D2433))
                                        )
                                    )
                                    .drawWithContent {
                                        drawContent()
                                        drawLine(
                                            color = Color.White.copy(alpha = 0.10f),
                                            start = Offset(4.dp.toPx(), 1f),
                                            end = Offset(size.width - 4.dp.toPx(), 1f),
                                            strokeWidth = 1.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                    .pointerInput(sample) {
                                        detectTapGestures(
                                            onTap = {
                                                onPreviewAudioFile(sample)
                                            },
                                            onLongPress = {
                                                sampleToAssign = sample
                                            }
                                        )
                                    }
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color(0xFF9BC0F8),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = cleanName,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Preview",
                                    tint = Color.White.copy(alpha = 0.70f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ================= ASSIGN SAMPLE TO PAD DIALOG =================
        if (sampleToAssign != null) {
            val currentSample = sampleToAssign!!
            val cleanSampleName = currentSample.name.removeSuffix(".wav").removeSuffix(".mp3").replace("_", " ")

            AlertDialog(
                onDismissRequest = { sampleToAssign = null },
                title = {
                    Text(
                        text = "Assign Sample",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Assign \"$cleanSampleName\" to pad:",
                            color = Color(0xFFB0BEC5),
                            fontSize = 12.sp
                        )

                        // 2x4 Grid of Pads D1 to D8
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (padId in 1..4) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color(0xFF283144), Color(0xFF1D2433))
                                                )
                                            )
                                            .clickable {
                                                onAssignSampleToPad(padId, currentSample)
                                                sampleToAssign = null
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "D$padId",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF9BC0F8)
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (padId in 5..8) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color(0xFF283144), Color(0xFF1D2433))
                                                )
                                            )
                                            .clickable {
                                                onAssignSampleToPad(padId, currentSample)
                                                sampleToAssign = null
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "D$padId",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF9BC0F8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { sampleToAssign = null }) {
                        Text("Cancel", color = Color(0xFF8E95A5))
                    }
                },
                containerColor = Color(0xFF161B29),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun SingleDrumPadBox(
    pad: DrumPadItem,
    isMixMode: Boolean,
    onPadPressed: () -> Unit,
    onPadReleased: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onLoopBeatsChange: (String) -> Unit = {}
) {
    val volPercent = (pad.volume * 100).toInt()

    var accumulatedDrag by remember(pad.id) { mutableFloatStateOf(0f) }

    val padBgBrush = if (pad.isPressed) {
        Brush.verticalGradient(
            listOf(Color(0xFF333C50), Color(0xFF232938))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0xFF222838), Color(0xFF141824))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .shadow(2.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
            .clip(RoundedCornerShape(12.dp))
            .background(padBgBrush)
            .drawWithContent {
                drawContent()
                // Top specular highlight for natural physical relief (matching Snapshot relief)
                drawLine(
                    color = Color.White.copy(alpha = if (pad.isPressed) 0.22f else 0.12f),
                    start = Offset(6.dp.toPx(), 1f),
                    end = Offset(size.width - 6.dp.toPx(), 1f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            .pointerInput(pad.id, isMixMode) {
                if (isMixMode) {
                    detectVerticalDragGestures(
                        onDragStart = { accumulatedDrag = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDrag -= dragAmount
                            if (kotlin.math.abs(accumulatedDrag) >= 12f) {
                                val steps = (accumulatedDrag / 12f).toInt()
                                val stepDelta = steps * 0.02f
                                val newVol = (pad.volume + stepDelta).coerceIn(0f, 1f)
                                onVolumeChange(newVol)
                                accumulatedDrag -= steps * 12f
                            }
                        }
                    )
                } else {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onPadPressed()
                        waitForUpOrCancellation()
                        onPadReleased()
                    }
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
                                val newVol = (pad.volume - 0.05f).coerceAtLeast(0f)
                                onVolumeChange(newVol)
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
                                val newVol = (pad.volume + 0.05f).coerceAtMost(1f)
                                onVolumeChange(newVol)
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
