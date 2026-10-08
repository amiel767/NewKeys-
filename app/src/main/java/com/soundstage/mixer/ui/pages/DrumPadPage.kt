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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withTimeoutOrNull
import com.soundstage.mixer.model.DrumPadItem
import com.soundstage.mixer.model.ScenePreset
import com.soundstage.mixer.model.StorageItem
import com.soundstage.mixer.model.AudioStorageItem
import com.soundstage.mixer.ui.components.AmbientStudioBackground
import com.soundstage.mixer.ui.components.ExternalLoopPlayerDaw
import com.soundstage.mixer.ui.components.LoopPlayerScreen
import com.soundstage.mixer.ui.components.LoopPlayerUiState
import com.soundstage.mixer.ui.components.LoopPlayerActions
import com.soundstage.mixer.ui.components.LoopPlayerLogic
import com.soundstage.mixer.ui.components.LoopKeyTheory
import com.soundstage.mixer.ui.components.LoopEntry
import com.soundstage.mixer.ui.components.LoopFolder
import com.soundstage.mixer.ui.components.LoopFile
import com.soundstage.mixer.ui.components.Crumb
import com.soundstage.mixer.ui.components.LoopSort
import com.soundstage.mixer.ui.components.LoopFilter
import com.soundstage.mixer.ui.components.LoopStatus
import com.soundstage.mixer.ui.components.FxType
import com.soundstage.mixer.ui.components.FxState
import com.soundstage.mixer.audio.AudioKeyBpmDetector
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
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
    onPadVolumeChange: (Int, Float) -> Unit = { _, _ -> },
    onPadVolumeDelta: (Int, Float) -> Unit = { _, _ -> },
    onLoopBeatsChange: (Int, String) -> Unit = { _, _ -> },
    onBackToMixer: () -> Unit,
    rightPanelMode: Int = 0,
    onRightPanelModeChange: (Int) -> Unit = {},
    loopFileName: String = "",
    loopFilePath: String? = null,
    loopDurationMs: Long = 0L,
    loopPositionMs: Long = 0L,
    loopProgress: Float = 0.0f,
    loopIsPlaying: Boolean = false,
    loopIsLooping: Boolean = true,
    loopPitchSemitones: Int = 0,
    loopStepMode: String = "DEFAULT",
    loopWaveform: FloatArray = FloatArray(0),
    loopVolume: Float = 1.0f,
    loopExplorerPath: String = "/storage/emulated/0/SoundStage/Loops",
    loopExplorerItems: List<AudioStorageItem> = emptyList(),
    loopIsExplorerOpen: Boolean = false,
    onToggleLoopPlayPause: () -> Unit = {},
    onStopLoop: () -> Unit = {},
    onRetriggerLoop: () -> Unit = {},
    onSeekLoopProgress: (Float) -> Unit = {},
    onToggleLoopLooping: () -> Unit = {},
    onChangeLoopPitch: (Int) -> Unit = {},
    onResetLoopPitch: () -> Unit = {},
    onSelectLoopStepMode: (String) -> Unit = {},
    onLoopVolumeChange: (Float) -> Unit = {},
    onToggleLoopExplorer: () -> Unit = {},
    onNavigateLoopExplorer: (String) -> Unit = {},
    onSelectLoopAudioFile: (java.io.File) -> Unit = {},
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

    // Real audio files strictly from folder (no fake hardcoded fallback samples)
    val effectiveAudioFiles = remember(audioFiles) {
        audioFiles.filter { it.path.isNotEmpty() && java.io.File(it.path).exists() }
    }

    AmbientStudioBackground(
        modifier = modifier
            .fillMaxSize()
            .testTag("drumpad_page_root")
    ) {
        val pagerState = rememberPagerState(initialPage = rightPanelMode.coerceIn(0, 1)) { 2 }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(pagerState.currentPage) {
            onRightPanelModeChange(pagerState.currentPage)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 4.dp)
        ) {
            // ================= 1. BARRE SUPERIEURE (BENTO RELIEF STYLE) =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
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
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Back to Mixer (Bento raised square)
                BentoTopBarButton(
                    onClick = onBackToMixer,
                    modifier = Modifier.size(34.dp).testTag("btn_drumpad_back_mixer")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Mixer",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Mode Switcher Case: DrumPad <-> Loop Player
                BentoTopBarButton(
                    onClick = {
                        coroutineScope.launch {
                            val target = if (pagerState.currentPage == 0) 1 else 0
                            pagerState.animateScrollToPage(target)
                        }
                    },
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (pagerState.currentPage == 0) "Loop Player" else "DrumPad",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Icon(
                            imageVector = if (pagerState.currentPage == 0) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                            contentDescription = "Switch View",
                            tint = Color(0xFF8BB5F8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (pagerState.currentPage == 0) {
                    // Feel Swing % (DrumPad mode only)
                    BentoTopBarButton(
                        onClick = {
                            val nextSwing = if (feelSwing >= 75) 0 else feelSwing + 25
                            onFeelSwingChange(nextSwing)
                        },
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Default.Waves, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("Swing $feelSwing%", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // BPM Case: ALWAYS VISIBLE in both DrumPad and Loop Player modes!
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .shadow(3.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF2F394D), Color(0xFF252D3D))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)),
                            RoundedCornerShape(10.dp)
                        )
                        .drawWithContent {
                            drawContent()
                            drawLine(
                                color = Color.White.copy(alpha = 0.16f),
                                start = Offset(4.dp.toPx(), 1f),
                                end = Offset(size.width - 4.dp.toPx(), 1f),
                                strokeWidth = 1.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        BentoStepButton("−") { onBpmChange((bpm - 1).coerceAtLeast(40)) }

                        Text(
                            "$bpm BPM",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )

                        BentoStepButton("+") { onBpmChange((bpm + 1).coerceAtMost(240)) }
                    }
                }

                if (pagerState.currentPage == 0) {
                    // Mix Button (DrumPad mode only)
                    BentoTopBarButton(
                        onClick = onToggleMixMode,
                        modifier = Modifier.height(34.dp).testTag("btn_drumpad_mix_mode"),
                        isActive = isMixMode
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Mix",
                                tint = if (isMixMode) Color(0xFF101726) else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Mix",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMixMode) Color(0xFF101726) else Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ================= 2. CORPS PRINCIPAL PAGER =================
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> {
                        // ================= PAGE 0: VUE COMPLETE DRUMPAD (TONIC PAD GAUCHE + 8 DRUMPADS DROITE) =================
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // ================= FLANC GAUCHE : TONIC PAD =================
                            Column(
                                modifier = Modifier
                                    .width(180.dp)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                    // Grille 4x3 de notes chromatiques (étendue vers le haut)
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

                                    val keyBg = if (isActive) {
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF9BC0F8), Color(0xFF38BDF8))
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF283144), Color(0xFF1D2433))
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .shadow(
                                                elevation = if (isActive) 8.dp else 2.dp,
                                                shape = RoundedCornerShape(10.dp),
                                                ambientColor = if (isActive) Color(0xFF38BDF8) else Color.Black,
                                                spotColor = if (isActive) Color(0xFF38BDF8) else Color(0x99000000)
                                            )
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(keyBg)
                                            .drawWithContent {
                                                drawContent()
                                                if (!isActive) {
                                                    drawLine(
                                                        color = Color.White.copy(alpha = 0.12f),
                                                        start = Offset(4.dp.toPx(), 1f),
                                                        end = Offset(size.width - 4.dp.toPx(), 1f),
                                                        strokeWidth = 1.dp.toPx(),
                                                        cap = StrokeCap.Round
                                                    )
                                                }
                                            }
                                            .clickable { onTonicNoteClick(note) }
                                            .testTag("tonic_note_$note"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = note,
                                            fontSize = 13.sp,
                                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                            color = if (isActive) Color(0xFF101726) else Color.White
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
                        // Ligne de contrôle Octave (- / +) et bouton Multi (placés en premier)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp),
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
                                    Icon(Icons.Default.Remove, contentDescription = "Octave minus", tint = Color.White, modifier = Modifier.size(11.dp))
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
                                    .background(if (isMultiPadEnabled) Color(0xFF9BC0F8) else Color(0x22FFFFFF))
                                    .clickable { onToggleMultiPad() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Multi",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMultiPadEnabled) Color(0xFF101726) else Color.White
                                )
                            }
                        }

                        // Deux petits sliders : Volume et Shimmer (placés en second)
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
                                        activeTrackColor = Color(0xFF9BC0F8),
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

                        // Carte Pad : Displays "-" by default when empty or "Analog Pad"
                        val displayPadName = if (activeTonicPadName.isBlank() || activeTonicPadName.equals("Analog Pad", ignoreCase = true) || activeTonicPadName == "-") "-" else activeTonicPadName

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
                                    text = "Pad: $displayPadName",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0x88FFFFFF), modifier = Modifier.size(13.dp))
                        }

                        // Carte Kit : [Nom dynamique du drum kit] -> Clic ouvre le sélecteur de Kit
                        val displayKitName = if (activeDrumKitName.isBlank()) "DrumKit" else activeDrumKitName

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
                                    text = "Kit: $displayKitName",
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

                // ================= FLANC DROIT : 8 DRUM PADS MATRIX (2x4) =================
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Row 1 (D1 to D4)
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
                                    onVolumeDelta = { onPadVolumeDelta(pad.id, it) },
                                    onLoopBeatsChange = { beats -> onLoopBeatsChange(pad.id, beats) }
                                )
                            }
                        }
                    }

                    // Row 2 (D5 to D8)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (i in 4 until 8) {
                            val pad = drumPads.getOrNull(i) ?: DrumPadItem(id = i + 1, label = "")
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
                                    onVolumeDelta = { onPadVolumeDelta(pad.id, it) },
                                    onLoopBeatsChange = { beats -> onLoopBeatsChange(pad.id, beats) }
                                )
                            }
                        }
                    }
                }
            }
        }
        1 -> {
            // PAGE 1: LOOP PLAYER (ÆTHER KEYS)
            LoopPlayerDawContainer(
                loopFileName = loopFileName,
                loopFilePath = loopFilePath,
                loopProgress = loopProgress,
                loopIsPlaying = loopIsPlaying,
                loopPitchSemitones = loopPitchSemitones,
                loopStepMode = loopStepMode,
                loopWaveform = loopWaveform,
                loopVolume = loopVolume,
                loopExplorerPath = loopExplorerPath,
                loopExplorerItems = loopExplorerItems,
                bpm = bpm,
                onToggleLoopPlayPause = onToggleLoopPlayPause,
                onSeekLoopProgress = onSeekLoopProgress,
                onChangeLoopPitch = onChangeLoopPitch,
                onSelectLoopStepMode = onSelectLoopStepMode,
                onLoopVolumeChange = onLoopVolumeChange,
                onNavigateLoopExplorer = onNavigateLoopExplorer,
                onSelectLoopAudioFile = onSelectLoopAudioFile,
                onPreviewAudioFile = onPreviewAudioFile,
                modifier = Modifier.fillMaxSize()
            )
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
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Grouped Inset Card Container (iOS / macOS Grouped List Style as in reference image)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(4.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF252D3E), Color(0xFF191F2D))
                                )
                            )
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                    ) {
                        if (effectiveAudioFiles.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No samples found in DrumPad folder",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(effectiveAudioFiles) { index, sample ->
                                    val cleanName = sample.name
                                        .removeSuffix(".wav")
                                        .removeSuffix(".mp3")
                                        .removeSuffix(".ogg")
                                        .replace("_", " ")

                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
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
                                                .padding(horizontal = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = cleanName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )

                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Preview",
                                                tint = Color.White.copy(alpha = 0.75f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        if (index < effectiveAudioFiles.size - 1) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                thickness = 0.8.dp,
                                                color = Color.White.copy(alpha = 0.08f)
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
    onVolumeDelta: (Float) -> Unit = {},
    onLoopBeatsChange: (String) -> Unit = {}
) {
    val volPercent = (pad.volume * 100).toInt()

    val cleanLabel = remember(pad.label, pad.sampleFileName, pad.sampleFilePath, pad.sf2Note) {
        when {
            pad.sampleFileName.isNotEmpty() -> pad.sampleFileName.removeSuffix(".wav").removeSuffix(".mp3").replace("_", " ")
            pad.sampleFilePath.isNotEmpty() -> java.io.File(pad.sampleFilePath).nameWithoutExtension.replace("_", " ")
            pad.sf2Note.isNotEmpty() -> pad.sf2Note
            else -> {
                val lower = pad.label.trim().lowercase()
                if (lower.isEmpty() || lower.startsWith("pad") || lower in listOf("kick", "snare", "hat", "clap", "open hat", "wood", "tumb", "tom 1", "tom 2", "tom 3", "sub kick", "kick 1", "clap 1", "wood 1", "tumb 1")) {
                    ""
                } else {
                    pad.label
                }
            }
        }
    }

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
                if (!isMixMode) {
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
                    text = cleanLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Grand pourcentage blanc au centre avec boutons - / + à appui continu (press & hold)
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    RepeatableIconButton(
                        onAction = {
                            onVolumeDelta(-0.02f)
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0x44000000))
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Volume moins", tint = Color.White, modifier = Modifier.size(16.dp))
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "$volPercent%",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    RepeatableIconButton(
                        onAction = {
                            onVolumeDelta(0.02f)
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0x44000000))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Volume plus", tint = Color.White, modifier = Modifier.size(16.dp))
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
                    text = cleanLabel,
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

@Composable
private fun RepeatableIconButton(
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val currentOnAction by rememberUpdatedState(onAction)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    currentOnAction() // First step immediately

                    // Wait 220ms initial delay before continuous auto-repeat
                    val releasedEarly = withTimeoutOrNull(220) {
                        waitForUpOrCancellation()
                    }

                    if (releasedEarly == null) {
                        while (true) {
                            currentOnAction()
                            val up = withTimeoutOrNull(35) {
                                waitForUpOrCancellation()
                            }
                            if (up != null) break
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun LoopPlayerDawContainer(
    loopFileName: String,
    loopFilePath: String?,
    loopProgress: Float,
    loopIsPlaying: Boolean,
    loopPitchSemitones: Int,
    loopStepMode: String,
    loopWaveform: FloatArray,
    loopVolume: Float,
    loopExplorerPath: String,
    loopExplorerItems: List<AudioStorageItem>,
    bpm: Int,
    onToggleLoopPlayPause: () -> Unit,
    onSeekLoopProgress: (Float) -> Unit,
    onChangeLoopPitch: (Int) -> Unit,
    onSelectLoopStepMode: (String) -> Unit,
    onLoopVolumeChange: (Float) -> Unit,
    onNavigateLoopExplorer: (String) -> Unit,
    onSelectLoopAudioFile: (java.io.File) -> Unit,
    onPreviewAudioFile: (StorageItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(LoopSort.NearestBpm) }
    var filters by rememberSaveable { mutableStateOf(emptySet<LoopFilter>()) }
    var favorites by remember { mutableStateOf(setOf<String>()) }
    var recents by remember { mutableStateOf(mapOf<String, Long>()) }

    var localVolume by remember { mutableFloatStateOf(loopVolume.coerceIn(0f, 1f)) }
    var localMuted by remember { mutableStateOf(false) }
    var localPan by remember { mutableFloatStateOf(0f) }
    var localFx by remember {
        mutableStateOf(FxType.values().associateWith { FxState() })
    }

    LaunchedEffect(loopExplorerPath) {
        if (loopExplorerItems.isEmpty()) {
            onNavigateLoopExplorer(loopExplorerPath)
        }
    }

    val breadcrumb = remember(loopExplorerPath) {
        val list = mutableListOf<Crumb>()
        val f = java.io.File(loopExplorerPath)
        val folderName = if (f.name.isNotBlank()) f.name else "Loops"
        list.add(Crumb(loopExplorerPath, folderName))
        list
    }

    val entries = remember(loopExplorerItems, favorites, recents) {
        loopExplorerItems.map { item ->
            if (item.isDirectory) {
                LoopFolder(
                    id = item.path,
                    name = item.name,
                    itemCount = 0
                )
            } else {
                val detected = AudioKeyBpmDetector.detectKeyAndBpm(item.name, java.io.File(item.path))
                LoopFile(
                    id = item.path,
                    name = item.name,
                    bpm = detected.bpm,
                    keyLabel = detected.key,
                    bars = 4,
                    isFavorite = favorites.contains(item.path),
                    lastPlayedAt = recents[item.path] ?: 0L
                )
            }
        }
    }

    val status = when {
        loopIsPlaying -> LoopStatus.Playing
        !loopFilePath.isNullOrEmpty() -> LoopStatus.Ready
        else -> LoopStatus.Idle
    }

    val totalBars = 4
    val posBar = ((loopProgress * totalBars).toInt() % totalBars) + 1
    val posBeat = (((loopProgress * totalBars * 4).toInt()) % 4) + 1

    val keyInfo = remember(loopFileName, loopPitchSemitones) {
        val detected = if (loopFileName.isNotBlank()) {
            AudioKeyBpmDetector.detectKeyAndBpm(loopFileName, loopFilePath?.let { java.io.File(it) })
        } else null
        val origKey = detected?.key?.ifBlank { "C" } ?: "C"
        val parsed = LoopKeyTheory.parse(origKey) ?: (0 to false)
        val roots = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
        val shiftedIndex = (parsed.first + (loopPitchSemitones % 12) + 12) % 12
        val shiftedRoot = roots[shiftedIndex]
        Triple(shiftedRoot, parsed.second, origKey)
    }

    val wavePeaks = remember(loopWaveform) {
        if (loopWaveform.isNotEmpty()) loopWaveform.toList() else emptyList()
    }

    val uiState = LoopPlayerUiState(
        breadcrumb = breadcrumb,
        entries = entries,
        query = query,
        sort = sort,
        filters = filters,
        selectedId = loopFilePath,
        playingId = if (loopIsPlaying) loopFilePath else null,
        status = status,
        loopName = loopFileName.ifBlank { "" },
        positionBar = posBar,
        positionBeat = posBeat,
        progress = loopProgress.coerceIn(0f, 1f),
        totalBars = totalBars,
        waveform = wavePeaks,
        currentBpm = bpm,
        keyRoot = keyInfo.first,
        isMinor = keyInfo.second,
        originalKeyLabel = keyInfo.third,
        semitoneShift = loopPitchSemitones,
        timeSignature = "4/4",
        stepLabel = loopStepMode.ifBlank { "Auto" },
        volume = localVolume,
        muted = localMuted,
        pan = localPan,
        fx = localFx
    )

    var lastSeekMs by remember { mutableLongStateOf(0L) }

    val actions = remember(uiState) {
        object : LoopPlayerActions {
            override fun onOpenFolder(id: String) {
                onNavigateLoopExplorer(id)
            }

            override fun onNavigateUp() {
                val parent = java.io.File(loopExplorerPath).parentFile?.absolutePath ?: "/storage/emulated/0/SoundStage/Loops"
                onNavigateLoopExplorer(parent)
            }

            override fun onBreadcrumbClick(index: Int) {
                breadcrumb.getOrNull(index)?.id?.let { onNavigateLoopExplorer(it) }
            }

            override fun onSelectFile(id: String) {
                onSelectLoopAudioFile(java.io.File(id))
                recents = recents + (id to System.currentTimeMillis())
            }

            override fun onToggleFavorite(id: String) {
                favorites = if (favorites.contains(id)) favorites - id else favorites + id
            }

            override fun onPreviewStart(id: String) {
                onPreviewAudioFile(StorageItem(name = java.io.File(id).name, path = id, isDirectory = false))
            }

            override fun onPreviewStop() {}

            override fun onQueryChange(q: String) {
                query = q
            }

            override fun onSortChange(s: LoopSort) {
                sort = s
            }

            override fun onFilterToggle(f: LoopFilter) {
                filters = if (filters.contains(f)) filters - f else filters + f
            }

            override fun onTogglePlay() {
                onToggleLoopPlayPause()
            }

            override fun onSeek(fraction: Float) {
                val now = System.currentTimeMillis()
                if (now - lastSeekMs >= 30L) {
                    lastSeekMs = now
                    onSeekLoopProgress(fraction)
                }
            }

            override fun onKeyStep(delta: Int) {
                onChangeLoopPitch(loopPitchSemitones + delta)
            }

            override fun onMinorChange(minor: Boolean) {
                // TODO(loop-player): Major/Minor shift if supported by pitch shifter
            }

            override fun onStepChange(delta: Int) {
                val steps = listOf("DEFAULT", "AUTO", "1/4", "1/8", "1/16")
                val curIdx = steps.indexOf(loopStepMode).coerceAtLeast(0)
                val nextIdx = (curIdx + delta + steps.size) % steps.size
                onSelectLoopStepMode(steps[nextIdx])
            }

            override fun onSignatureClick() {}

            override fun onVolumeChange(position: Float) {
                localVolume = position
                val gain = LoopPlayerLogic.volumeGain(position, localMuted)
                onLoopVolumeChange(gain)
            }

            override fun onMuteToggle() {
                localMuted = !localMuted
                val gain = LoopPlayerLogic.volumeGain(localVolume, !localMuted)
                onLoopVolumeChange(gain)
            }

            override fun onPanChange(pan: Float) {
                localPan = pan
            }

            override fun onFxEnabledChange(type: FxType, enabled: Boolean) {
                val old = localFx[type] ?: FxState()
                localFx = localFx + (type to old.copy(enabled = enabled))
            }

            override fun onFxAmountChange(type: FxType, amount: Float) {
                val old = localFx[type] ?: FxState()
                localFx = localFx + (type to old.copy(amount = amount))
            }
        }
    }

    LoopPlayerScreen(
        state = uiState,
        actions = actions,
        modifier = modifier
    )
}

@Composable
private fun BentoTopBarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    content: @Composable () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "bento_topbar_btn"
    )
    val shape = RoundedCornerShape(10.dp)
    val bgBrush = if (isActive) {
        Brush.verticalGradient(listOf(Color(0xFF9BC0F8), Color(0xFF8FB6F2)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF2F394D), Color(0xFF252D3D)))
    }

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(3.dp, shape, ambientColor = Color.Black, spotColor = Color(0x99000000))
            .clip(shape)
            .background(bgBrush)
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = if (isActive) 0.25f else 0.12f), Color.Transparent)),
                shape
            )
            .drawWithContent {
                drawContent()
                if (!isActive) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.16f),
                        start = Offset(4.dp.toPx(), 1f),
                        end = Offset(size.width - 4.dp.toPx(), 1f),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun BentoStepButton(label: String, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.90f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "bento_step_btn"
    )
    Box(
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(1.5.dp, CircleShape, ambientColor = Color.Black, spotColor = Color(0x99000000))
            .clip(CircleShape)
            .background(Color(0xFF1E2638))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}
