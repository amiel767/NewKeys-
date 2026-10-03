package com.soundstage.mixer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soundstage.mixer.model.ActivePopup
import com.soundstage.mixer.model.AppScreenPage
import com.soundstage.mixer.model.FxParameters
import com.soundstage.mixer.ui.components.*
import com.soundstage.mixer.ui.pages.DrumPadPage
import com.soundstage.mixer.ui.pages.FileExplorerPage
import com.soundstage.mixer.ui.pages.ProjectsPage
import com.soundstage.mixer.ui.pages.SheetsPage
import com.soundstage.mixer.ui.theme.*
import com.soundstage.mixer.viewmodel.MixerViewModel

@Composable
fun MixerScreen(
    viewModel: MixerViewModel,
    modifier: Modifier = Modifier,
    onRequestManageStoragePermission: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isSplashFinished by remember { mutableStateOf(false) }

    val sf2PickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            viewModel.importSoundFontUris(uris)
        }
    }

    val drumPadPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            viewModel.importDrumPadUris(uris)
        }
    }

    val loopPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            viewModel.importLoopUris(uris)
        }
    }

    // Real-time zero-latency chord analysis from pressed keys
    val rawChord = remember(uiState.pressedKeys) {
        ChordCalculator.detect(uiState.pressedKeys)
    }
    val detectedChord = remember(rawChord, uiState.useFlats) {
        if (uiState.useFlats && rawChord != null) {
            fun String.toFlats(): String = this
                .replace("C#", "Db")
                .replace("D#", "Eb")
                .replace("F#", "Gb")
                .replace("G#", "Ab")
                .replace("A#", "Bb")
            
            com.soundstage.mixer.ui.components.DetectedChord(
                primaryName = rawChord.primaryName.toFlats(),
                equivalentName = rawChord.equivalentName.toFlats(),
                variantName = rawChord.variantName.toFlats(),
                alternateNames = rawChord.alternateNames.toFlats(),
                alternateName2 = rawChord.alternateName2.toFlats(),
                formula = rawChord.formula,
                notesList = rawChord.notesList.map { it.toFlats() }
            )
        } else rawChord
    }

    val isKeyboardVisible = uiState.keyboardHeightFraction > 0f

    val animatedBottomPadding by animateDpAsState(
        targetValue = if (isKeyboardVisible) 0.dp else 6.dp,
        animationSpec = tween(
            durationMillis = 260,
            easing = FastOutSlowInEasing
        ),
        label = "bottomPaddingAnim"
    )
    val safeBottomPadding = animatedBottomPadding.coerceAtLeast(0.dp)

    // Fullscreen Stage Container
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                start = 6.dp,
                end = 6.dp,
                top = 6.dp,
                bottom = safeBottomPadding
            )
            .testTag("mixer_screen_root")
    ) {
        AnimatedContent(
            targetState = uiState.currentPage,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
            },
            label = "screen_page_switch",
            modifier = Modifier.fillMaxSize()
        ) { currentPage ->
            when (currentPage) {
                AppScreenPage.PROJECTS -> {
                    val hasActive = !uiState.activeProjectName.isNullOrBlank() && uiState.activeProjectName != "None" && !uiState.activeSceneId.isNullOrBlank() && uiState.activeSceneId != "None"
                    ProjectsPage(
                        projects = uiState.scenes,
                        hasActiveProject = hasActive,
                        onOpenProject = { viewModel.openProject(it) },
                        onCreateNewProject = { name, isBlank -> viewModel.createNewProject(name, isBlank) },
                        onDeleteProject = { viewModel.deleteProject(it) },
                        onOpenSettings = { viewModel.openSettingsDrawer() },
                        themeHue = uiState.themeHue,
                        themeBgSaturation = uiState.themeBgSaturation,
                        themeBgLightness = uiState.themeBgLightness
                    )
                }
                AppScreenPage.DRUMPAD -> {
                    if (uiState.isDrumPadFileExplorerOpen || uiState.isDrumPadLoopsOpen) {
                        FileExplorerPage(
                            title = if (uiState.isDrumPadLoopsOpen) "Explorateur de loops audio" else "Explorateur de fichiers audio",
                            audioFiles = if (uiState.isDrumPadLoopsOpen) uiState.loopAudioFiles else uiState.drumPadAudioFiles,
                            isLoopMode = uiState.isDrumPadLoopsOpen,
                            onPreviewAudioFile = { viewModel.previewFileInExplorer(it, uiState.isDrumPadLoopsOpen) },
                            onStopPreview = { viewModel.stopFileExplorerPreview() },
                            onAssignToPad = { padId, sample, isLoop ->
                                viewModel.assignDrumSampleOrLoop(padId, sample.name, sample.path, isLoop = isLoop)
                            },
                            onClose = {
                                viewModel.openDrumPadFileExplorer(false)
                                viewModel.openDrumPadLoopsView(false)
                            }
                        )
                    } else {
                        val tonicSlot = uiState.audioSlots.find { it.slotId == 9 }
                        val tonicVol = tonicSlot?.volume ?: 0.85f
                        DrumPadPage(
                            drumPads = uiState.drumPads,
                            isMixMode = uiState.isDrumPadMixMode,
                            onToggleMixMode = { viewModel.toggleDrumPadMixMode() },
                            feelSwing = uiState.drumFeelSwing,
                            onFeelSwingChange = { viewModel.setDrumFeelSwing(it) },
                            bpm = uiState.bpm,
                            onBpmChange = { viewModel.setGlobalBpm(it) },
                            scenes = uiState.scenes,
                            activeSceneId = uiState.activeSceneId,
                            onSelectScene = { viewModel.selectScene(it) },
                            onCreateBlankScene = { viewModel.createBlankScene(it) },
                            onDuplicateScene = { viewModel.duplicateCurrentScene(it) },
                            activeTonicNotes = uiState.activeTonicNotes,
                            onTonicNoteClick = { viewModel.onTonicNoteClick(it) },
                            useFlats = uiState.useFlats,
                            onToggleUseFlats = { viewModel.toggleUseFlats() },
                            activeTonicPadName = uiState.activeTonicPadName,
                            onOpenTonicSoundPicker = {
                                viewModel.openSoundfontForSlot(9)
                            },
                            activeDrumKitName = uiState.activeDrumKitName,
                            onOpenDrumKitPicker = { viewModel.toggleDrumPadMiniBrowser() },
                            onStopTonicDrone = { viewModel.onTonicNoteClick("") },
                            tonicOctaveRange = uiState.tonicOctaveRange,
                            onTonicOctaveMinus = { viewModel.onTonicOctaveMinus() },
                            onTonicOctavePlus = { viewModel.onTonicOctavePlus() },
                            isMultiPadEnabled = uiState.isMultiPadEnabled,
                            onToggleMultiPad = { viewModel.toggleMultiPad() },
                            tonicVolume = tonicVol,
                            onTonicVolumeChange = { viewModel.setTonicVolume(it) },
                            tonicShimmer = uiState.tonicShimmer,
                            onTonicShimmerChange = { viewModel.setTonicShimmer(it) },
                            isMiniBrowserOpen = uiState.isDrumPadMiniBrowserOpen,
                            onToggleMiniBrowser = { viewModel.toggleDrumPadMiniBrowser() },
                            onCloseMiniBrowser = { viewModel.closeDrumPadMiniBrowser() },
                            audioFiles = uiState.drumPadAudioFiles,
                            onPreviewAudioFile = { viewModel.playDrumSample(it) },
                            onAssignSampleToPad = { padId, sample ->
                                viewModel.assignDrumSampleOrLoop(padId, sample.name, sample.path, isLoop = false)
                            },
                            onPadPressed = { viewModel.onDrumPadPressed(it) },
                            onPadReleased = { viewModel.onDrumPadReleased(it) },
                            onPadVolumeChange = { padId, vol -> viewModel.setDrumPadVolumeDirect(padId, vol) },
                            onLoopBeatsChange = { padId, beats -> viewModel.setDrumPadLoopBeats(padId, beats) },
                            onOpenFileExplorer = { viewModel.openDrumPadFileExplorer(true) },
                            onOpenLoopsView = { viewModel.openDrumPadLoopsView(true) },
                            onBackToMixer = { viewModel.navigateBackToMixer() }
                        )
                    }
                }
                AppScreenPage.SHEETS -> {
                    SheetsPage(
                        notesText = uiState.notesText,
                        onNotesChange = { viewModel.updateNotesText(it) },
                        transpose = uiState.transpose,
                        onTransposeChange = { viewModel.updateTranspose(it) },
                        onBackToMixer = { viewModel.navigateBackToMixer() }
                    )
                }
                AppScreenPage.MIXER -> {
                    // Full-screen Studio Layout (No enclosing bento box, edge-to-edge layout)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(
                                start = 4.dp,
                                end = 4.dp,
                                top = 2.dp,
                                bottom = safeBottomPadding
                            )
                    ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // 1. TOP BAR
                TopBar(
                    transpose = uiState.transpose,
                    octave = uiState.octave,
                    onTransposeChange = { viewModel.updateTranspose(it) },
                    onOctaveChange = { viewModel.updateOctave(it) },
                    pressedKeys = uiState.pressedKeys,
                    useFlats = uiState.useFlats,
                    isLoopsOpen = uiState.activePopup == ActivePopup.LOOPS,
                    onToggleLoops = { viewModel.toggleLoopsPanel() },
                    isLoopPlaying = uiState.isLoopPlaying,
                    onToggleLoopPlayPause = { viewModel.toggleLoopPlayPause() },
                    loopVolume = uiState.loopVolume,
                    onLoopVolumeChange = { viewModel.setLoopVolume(it) },
                    selectedBeats = uiState.selectedBeatCount,
                    onSelectBeats = { viewModel.selectBeatCount(it) },
                    loopFolders = uiState.loopFolders,
                    activeLoopFile = uiState.activeLoopFile,
                    onToggleLoopFolder = { viewModel.toggleLoopFolder(it) },
                    onSelectLoopFile = { viewModel.selectAndToggleLoopFile(it) },
                    isSustainActive = uiState.isSustainActive,
                    isMidiPedalPressed = uiState.isMidiPedalPressed,
                    onToggleSustain = { viewModel.toggleSustain() },
                    onOpenNotes = {
                        viewModel.openSheetsDrawer()
                    },
                    onOpenDrumPad = {
                        viewModel.navigateToPage(AppScreenPage.DRUMPAD)
                    },
                    onOpenTonicPad = {
                        if (uiState.activePopup == ActivePopup.TONIC_PAD) viewModel.closePopup() else viewModel.openPopup(ActivePopup.TONIC_PAD)
                    },
                    onPanic = { viewModel.triggerPanic() },
                    onOpenScenes = {
                        if (uiState.activePopup == ActivePopup.SCENE) viewModel.closePopup() else viewModel.openPopup(ActivePopup.SCENE)
                    },
                    onOpenSettings = { viewModel.openSettingsDrawer() },
                    onBackToProjects = { viewModel.navigateToProjectsHome() },
                    onSaveProjectDirect = { viewModel.saveCurrentProjectDirect() }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 2. MIXER TRACKS SECTION (8 Tracks + 1 Master)
                val defaultSfName = uiState.tracks.firstOrNull { it.soundfontName.isNotEmpty() }?.soundfontName ?: "FluidR3_GM.sf2"
                val isPadOpen = uiState.activePopup == ActivePopup.DRUM_PAD || uiState.activePopup == ActivePopup.TONIC_PAD || uiState.activePopup == ActivePopup.NOTES

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val availableH = maxHeight
                    val isVerticalMode = availableH >= 85.dp
                    val showTicks = true

                    AnimatedContent(
                        targetState = isVerticalMode,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith
                                fadeOut(animationSpec = tween(180))
                        },
                        label = "fader_mode_transition"
                    ) { verticalMode ->
                        if (verticalMode) {
                            val trackScrollState = rememberScrollState()
                            val overlayWidth = if (isPadOpen) (this@BoxWithConstraints.maxWidth * 0.50f) else 0.dp
                            val visibleTracks = uiState.tracks.take(uiState.visibleTracksCount.coerceIn(1, 8))
                            val showAddButton = uiState.visibleTracksCount < 8 && !isPadOpen
                            val activeItemCount = visibleTracks.size + (if (showAddButton) 1 else 0)

                            // Fader Width & Layout Logic:
                            // Reference default width = 120.dp (+30% increase for generous, comfortable touch targets).
                            // In Unlock mode: Always maintains default width (120.dp) with horizontal scroll.
                            // In Lock mode: Keeps default width (120.dp) until tracks exceed screen space, then adapts down proportionally to fit all tracks.
                            val defaultTrackWidth = 120.dp
                            val totalSpacing = 6.dp * (activeItemCount - 1).coerceAtLeast(0)
                            val availableSpace = (this@BoxWithConstraints.maxWidth - totalSpacing).coerceAtLeast(0.dp)
                            val totalNeededWidth = defaultTrackWidth * activeItemCount
                            val isOverflowingInLock = uiState.isFaderAutoFit && (totalNeededWidth > availableSpace)
                            val autoFitWidth = if (activeItemCount > 0) (availableSpace / activeItemCount) else defaultTrackWidth
                            val finalTrackWidth = if (isOverflowingInLock) autoFitWidth else defaultTrackWidth

                            val rowModifier = if (uiState.isFaderAutoFit && isOverflowingInLock) {
                                Modifier.fillMaxSize()
                            } else if (uiState.isFaderAutoFit) {
                                Modifier.fillMaxHeight().wrapContentWidth()
                            } else {
                                Modifier.fillMaxSize().horizontalScroll(trackScrollState)
                            }

                            Row(
                                modifier = rowModifier,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                visibleTracks.forEach { track ->
                                    val itemModifier = if (isOverflowingInLock) {
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    } else {
                                        Modifier
                                            .width(finalTrackWidth)
                                            .fillMaxHeight()
                                    }

                                    VerticalTrackChannel(
                                        track = track,
                                        onVolumeChange = { vol -> viewModel.setTrackVolume(track.id, vol) },
                                        onPowerToggle = { viewModel.toggleTrackPower(track.id) },
                                        onDeleteTrack = { viewModel.removeTrack(track.id) },
                                        onPanChange = { pan -> viewModel.setTrackPan(track.id, pan) },
                                        onMuteClick = { viewModel.onTrackMuteClick(track.id) },
                                        onSoloClick = { viewModel.onTrackSoloClick(track.id) },
                                        onTrackNameClick = { viewModel.openSoundfontForSlot(track.id - 1) },
                                        onFxClick = { viewModel.openEffectsForTrack(track.id) },
                                        showTicks = showTicks,
                                        modifier = itemModifier
                                    )
                                }

                                // "+ (Add Sound)" Button next to faders: studio square layout with thick professional border
                                if (showAddButton) {
                                    val addBtnModifier = if (isOverflowingInLock) {
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    } else {
                                        Modifier
                                            .width(finalTrackWidth)
                                            .fillMaxHeight()
                                    }

                                    Box(
                                        modifier = addBtnModifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF131722))
                                            .border(
                                                width = 2.dp,
                                                color = Color(0xFF2C394E),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { viewModel.addSoundTrack() }
                                            .padding(6.dp)
                                            .testTag("btn_add_sound_track"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF1A2232))
                                                    .border(2.dp, Color(0xFF3B4864), RoundedCornerShape(8.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Add Sound",
                                                    tint = Color(0xFF93C5FD),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Add Sound",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }

                                if (isPadOpen) {
                                    Spacer(modifier = Modifier.width(overlayWidth))
                                }
                            }
                        } else {
                            CompactHorizontalFadersRack(
                                tracks = uiState.tracks,
                                masterTrack = uiState.masterTrack,
                                onVolumeChange = { id, vol -> viewModel.setTrackVolume(id, vol) },
                                onPowerToggle = { id -> viewModel.toggleTrackPower(id) },
                                onPanChange = { id, pan -> viewModel.setTrackPan(id, pan) },
                                onMuteClick = { id -> viewModel.onTrackMuteClick(id) },
                                onSoloClick = { id -> viewModel.onTrackSoloClick(id) },
                                onTrackNameClick = { id -> viewModel.openSoundfontForSlot(id - 1) },
                                onFxClick = { id -> viewModel.openEffectsForTrack(id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.Center)
                            )
                        }
                    }

                    // Fluid non-modal DrumPad & TonicPad overlay panel covering the 4 last faders area with bouncy spring animation
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isPadOpen,
                        enter = scaleIn(
                            initialScale = 0.85f,
                            transformOrigin = TransformOrigin(0.95f, 0.5f),
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + slideInHorizontally(
                            initialOffsetX = { it / 2 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + fadeIn(tween(180)),
                        exit = scaleOut(
                            targetScale = 0.90f,
                            transformOrigin = TransformOrigin(0.95f, 0.5f),
                            animationSpec = tween(150)
                        ) + slideOutHorizontally(
                            targetOffsetX = { it / 2 },
                            animationSpec = tween(150)
                        ) + fadeOut(tween(150)),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width((this@BoxWithConstraints.maxWidth * 0.50f).coerceIn(310.dp, 480.dp))
                            .fillMaxHeight()
                            .zIndex(40f)
                    ) {
                        var isTonicSoundPickerOpen by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .shadow(16.dp, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                .padding(4.dp)
                        ) {
                            AnimatedContent(
                                targetState = uiState.activePopup,
                                transitionSpec = {
                                    (scaleIn(
                                        initialScale = 0.90f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    ) + fadeIn(tween(160)))
                                        .togetherWith(scaleOut(targetScale = 0.95f, animationSpec = tween(120)) + fadeOut(tween(120)))
                                },
                                label = "inline_pad_view_switch"
                            ) { popup ->
                                when (popup) {
                                    ActivePopup.DRUM_PAD -> {
                                        MainDrumPadSquareContent(
                                            drumPads = uiState.drumPads,
                                            volume = uiState.drumVolume,
                                            onVolumeChange = { viewModel.setDrumVolume(it) },
                                            reverb = uiState.drumReverb,
                                            onReverbChange = { viewModel.setDrumReverb(it) },
                                            activeTab = uiState.drumActiveTab,
                                            onTabChange = { viewModel.setDrumTab(it) },
                                            isPinned = false,
                                            onTogglePin = {},
                                            onClose = { viewModel.closePopup() },
                                            onDragWindow = { _, _ -> },
                                            onPadPressed = { viewModel.onDrumPadPressed(it) },
                                            onPadReleased = { viewModel.onDrumPadReleased(it) },
                                            onLongPressPad = {},
                                            audioFiles = uiState.drumPadAudioFiles,
                                            loopFiles = uiState.drumPadLoopAudioFiles,
                                            onPlaySample = { viewModel.playDrumSample(it) },
                                            onLongPressSample = { file -> viewModel.assignDrumSampleOrLoop(1, file.name, file.path, isLoop = true) },
                                            onDeleteLoopFile = { viewModel.deleteDrumPadLoopFile(it) },
                                            onAssignPadSampleOrLoop = { padId, sample, isLoop ->
                                                viewModel.assignDrumSampleOrLoop(padId, sample.name, sample.path, isLoop)
                                            },
                                            onImportAudioFile = { drumPadPickerLauncher.launch(arrayOf("audio/*", "*/*")) },
                                            isRecording = uiState.isDrumLoopRecording,
                                            isArmed = uiState.isDrumLoopArmed,
                                            onToggleArmLoop = { viewModel.toggleArmDrumLoop() },
                                            bpm = uiState.bpm,
                                            timeSignature = uiState.drumTimeSignature,
                                            onToggleTimeSignature = { viewModel.toggleDrumTimeSignature() },
                                            loopBars = uiState.drumLoopBars,
                                            onSetLoopBars = { viewModel.setDrumLoopBars(it) },
                                            onIncrementLoopBars = { viewModel.incrementDrumLoopBars() },
                                            onDecrementLoopBars = { viewModel.decrementDrumLoopBars() },
                                            onStartRecording = { viewModel.startDrumLoopRecording() },
                                            onStopRecording = { viewModel.stopAndRenderDrumLoop(onFinished = {}) },
                                            onCancelRecording = { viewModel.cancelDrumLoopRecording() },
                                            isRendering = uiState.isDrumLoopRendering,
                                            lastPath = uiState.lastDrumPadPath,
                                            onUpdateLastPath = { viewModel.updateLastDrumPadPath(it) }
                                        )
                                    }
                                    ActivePopup.TONIC_PAD -> {
                                        val singleOctaveText = remember(uiState.tonicOctaveRange) {
                                            val match = Regex("C[0-8]").findAll(uiState.tonicOctaveRange).map { it.value }.toList()
                                            if (match.isNotEmpty()) match.last() else "C4"
                                        }
                                        TonicPadContent(
                                            activeNotes = uiState.activeTonicNotes,
                                            onNoteClick = { viewModel.onTonicNoteClick(it) },
                                            isMultiPadEnabled = uiState.isMultiPadEnabled,
                                            onToggleMultiPad = { viewModel.toggleMultiPad() },
                                            singleOctaveText = singleOctaveText,
                                            onOctaveMinus = { viewModel.onTonicOctaveMinus() },
                                            onOctavePlus = { viewModel.onTonicOctavePlus() },
                                            useFlats = uiState.useFlats,
                                            volume = uiState.audioSlots.getOrNull(9)?.volume ?: 0.8f,
                                            onVolumeChange = { viewModel.setTonicVolume(it) },
                                            reverb = viewModel.audioEngine.channelParams[9].reverb,
                                            onReverbChange = { viewModel.setTonicReverb(it) },
                                            brightness = uiState.tonicBrightness,
                                            onBrightnessChange = { viewModel.setTonicBrightness(it) },
                                            shimmer = uiState.tonicShimmer,
                                            onShimmerChange = { viewModel.setTonicShimmer(it) },
                                            isPinned = false,
                                            onTogglePin = {},
                                            onClose = { viewModel.closePopup() },
                                            isSoundPickerOpen = isTonicSoundPickerOpen,
                                            onToggleSoundPicker = { isTonicSoundPickerOpen = it },
                                            soundfonts = uiState.soundfontFiles,
                                            currentLoadedSf2Name = uiState.audioSlots.getOrNull(9)?.soundFontPath?.substringAfterLast("/") ?: defaultSfName,
                                            loadedSf2Presets = uiState.audioSlots.getOrNull(9)?.presets ?: emptyList(),
                                            onSelectPreset = { viewModel.selectSf2Preset(9, it) },
                                            onSelectSf2File = { viewModel.loadSoundFontForSlot(9, it.path) },
                                            onOpenSoundfontPicker = { viewModel.openSoundfontForSlot(9) },
                                            onDragHeader = null
                                        )
                                    }
                                    ActivePopup.NOTES -> {
                                        NotesDialog(
                                            isOpen = true,
                                            onClose = { viewModel.closePopup() },
                                            detectedChord = detectedChord,
                                            selectedRootKey = uiState.selectedRootKey,
                                            useFlats = uiState.useFlats,
                                            notesDir = viewModel.fileManager.notesDir,
                                            fileManager = viewModel.fileManager
                                        )
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 3. BOTTOM BAR (With MIDI Player, Real Chord Detector & Piano Toggle)
                BottomBar(
                    masterTrack = uiState.masterTrack,
                    onMasterVolumeChange = { vol -> viewModel.setTrackVolume(0, vol) },
                    onMasterFxClick = { viewModel.openEffectsForTrack(0) },
                    isRecording = uiState.isRecording,
                    recordingDuration = uiState.recordingDuration,
                    lastRecordedFile = uiState.lastRecordedFile,
                    onToggleRecording = { viewModel.toggleRecording() },
                    bpm = uiState.bpm,
                    onBpmChange = { viewModel.updateBpm(it) },
                    isMetronomeOn = uiState.isMetronomeOn,
                    onToggleMetronome = { viewModel.toggleMetronome() },
                    isMetroPanelOpen = uiState.isMetroPanelOpen,
                    onToggleMetroPanel = { viewModel.toggleMetroPanel() },
                    metroSignature = uiState.metronomeSignature,
                    onSelectSignature = { viewModel.setMetronomeSignature(it) },
                    metroVolume = uiState.metronomeVolume,
                    onMetroVolumeChange = { viewModel.setMetronomeVolume(it) },
                    selectedKey = uiState.selectedRootKey,
                    onSelectKey = { viewModel.setSelectedRootKey(it) },
                    selectedScaleMode = uiState.selectedScaleMode,
                    onSelectScaleMode = { viewModel.setScaleMode(it) },
                    useFlats = uiState.useFlats,
                    
                    // Real-time Detected Chord
                    detectedChord = detectedChord,

                    // Snapshots / Sub-Scenes (Section 2)
                    isSnapshotArmMode = uiState.isSnapshotArmMode,
                    onToggleSnapshotArm = { viewModel.toggleSnapshotArm() },
                    activeSnapshotSlot = uiState.activeSnapshotSlot,
                    snapshots = uiState.snapshots,
                    configuredSnapshotSlots = uiState.configuredSnapshotSlots,
                    onSnapshotSlotClick = { slotKey -> viewModel.onSnapshotSlotClick(slotKey) },
                    onAddSnapshot = { name -> viewModel.addSnapshotSlot(name) },
                    onDeleteSnapshot = { slotKey -> viewModel.removeSnapshotSlot(slotKey) },
                    snapshotCustomNames = uiState.snapshotCustomNames,
                    onRenameSnapshotSlot = { slotKey, newName -> viewModel.renameSnapshotSlot(slotKey, newName) },
                    snapshotTransitionProgress = uiState.snapshotTransitionProgress,
                    
                    isKeyboardActive = isKeyboardVisible,
                    onToggleKeyboard = { viewModel.cycleKeyboardExpansion() },
                    isLayerActive = uiState.isKeyboardLayerExpanded,
                    onToggleLayer = { viewModel.toggleKeyboardLayer() },
                    onKeyboardHandleClick = { viewModel.cycleKeyboardExpansion() },
                    onKeyboardDrag = { deltaY ->
                        val fractionDelta = -deltaY / 200f
                        viewModel.setKeyboardHeightFraction(uiState.keyboardHeightFraction + fractionDelta)
                    }
                )

                // 4. FIXED LAYOUT SLOT FOR RETRACTABLE PIANO (Exact fit with 5dp margin without overlapping)
                val activePianoTracksCount = remember(uiState.tracks, uiState.visibleTracksCount) {
                    uiState.tracks.take(uiState.visibleTracksCount.coerceIn(1, 8)).count { it.isEnabled }.coerceIn(1, 8)
                }
                val pianoRestStripHeight = (3.4f * activePianoTracksCount + 3f).dp.coerceIn(6.dp, 32.dp)
                val targetKeyboardSlotHeight = if (isKeyboardVisible) 48.dp + pianoRestStripHeight + 11.dp else 0.dp
                val keyboardHeight by animateDpAsState(
                    targetValue = targetKeyboardSlotHeight,
                    animationSpec = tween(300, easing = EaseOutExpo),
                    label = "keyboardHeight"
                )
                
                if (keyboardHeight > 0.dp) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(keyboardHeight)
                    )
                }
            }

            // Outside tap dismisser & subtle dark background scrim for expanded keyboard layer lines
            if (isKeyboardVisible && uiState.isKeyboardLayerExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x88000000))
                        .zIndex(60f)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            viewModel.toggleKeyboardLayer()
                        }
                )
            }

            // 5. RETRACTABLE MULTI-TOUCH VIRTUAL PIANO KEYBOARD (Smooth 380ms transition)
            val activeSlotLedColor = getTrackThemeColor(uiState.activeSoundfontSlotId + 1)
            val activePianoTracks = remember(uiState.tracks, uiState.visibleTracksCount) {
                uiState.tracks.take(uiState.visibleTracksCount.coerceIn(1, 8)).filter { it.isEnabled }
            }
            AnimatedVisibility(
                visible = isKeyboardVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(380, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(280)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(200)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(top = 5.dp)
                    .zIndex(100f)
            ) {
                VirtualPianoKeyboard(
                    heightFraction = 1f,
                    pressedKeys = uiState.pressedKeys,
                    octave = uiState.octave,
                    tracks = activePianoTracks,
                    isLayerExpanded = uiState.isKeyboardLayerExpanded,
                    onToggleLayerExpanded = { viewModel.toggleKeyboardLayer() },
                    onRangeChanged = { trackId, minN, maxN -> viewModel.updateTrackKeyRange(trackId, minN, maxN) },
                    onKeyDown = { viewModel.onKeyDown(it) },
                    onKeyUp = { viewModel.onKeyUp(it) },
                    onKeyDownWithVelocity = { key, vel -> viewModel.onKeyDown(key, vel) },
                    onGrabberDrag = { deltaY ->
                        val fractionDelta = -deltaY / 200f
                        viewModel.setKeyboardHeightFraction(uiState.keyboardHeightFraction + fractionDelta)
                    },
                    onGrabberClick = { viewModel.cycleKeyboardExpansion() },
                    isSustainActive = uiState.isSustainActive,
                    onToggleSustain = { viewModel.toggleSustain() },
                    pitchBend = uiState.pitchBend,
                    onPitchBendChange = { viewModel.setPitchBend(it) },
                    onOctaveChange = { delta -> viewModel.updateOctave(delta) },
                    activeAuraColor = activeSlotLedColor
                )
            }


            // Outside touch scrim for quick closing of floating dropdowns
            if (uiState.isLoopsPanelOpen || uiState.isMetroPanelOpen || uiState.isMidiPanelOpen || uiState.activePopup == ActivePopup.SCENE) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x22000000))
                        .clickable {
                            if (uiState.isLoopsPanelOpen) viewModel.closeLoopsPanel()
                            if (uiState.isMetroPanelOpen) viewModel.closeMetroPanel()
                            if (uiState.isMidiPanelOpen) viewModel.closeMidiPanel()
                            if (uiState.activePopup == ActivePopup.SCENE) viewModel.closePopup()
                        }
                )
            }

            // Floating Metronome Dropdown Panel
            AnimatedVisibility(
                visible = uiState.isMetroPanelOpen,
                enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { 20 }, animationSpec = tween(200)),
                exit = fadeOut(tween(150)) + slideOutVertically(targetOffsetY = { 20 }, animationSpec = tween(150)),
                modifier = Modifier
                    .padding(bottom = 40.dp, start = 120.dp)
                    .align(Alignment.BottomStart)
            ) {
                MetronomeFloatingPanel(
                    isOpen = uiState.isMetroPanelOpen,
                    isMetronomeOn = uiState.isMetronomeOn,
                    onToggleMetronome = { viewModel.toggleMetronome() },
                    selectedSignature = uiState.metronomeSignature,
                    onSelectSignature = { viewModel.setMetronomeSignature(it) },
                    volume = uiState.metronomeVolume,
                    onVolumeChange = { viewModel.setMetronomeVolume(it) },
                    selectedKey = uiState.selectedRootKey,
                    onSelectKey = { viewModel.setSelectedRootKey(it) },
                    selectedScaleMode = uiState.selectedScaleMode,
                    onSelectScaleMode = { viewModel.setScaleMode(it) },
                    useFlats = uiState.useFlats,
                    onClose = { viewModel.closeMetroPanel() }
                )
            }

            // Floating MIDI File Browser Panel
            AnimatedVisibility(
                visible = uiState.isMidiPanelOpen,
                enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { 20 }, animationSpec = tween(200)),
                exit = fadeOut(tween(150)) + slideOutVertically(targetOffsetY = { 20 }, animationSpec = tween(150)),
                modifier = Modifier
                    .padding(bottom = 40.dp, start = 220.dp)
                    .align(Alignment.BottomStart)
            ) {
                MidiFloatingPanel(
                    isOpen = uiState.isMidiPanelOpen,
                    isMidiPlaying = uiState.isMidiPlaying,
                    onToggleMidiPlayPause = { viewModel.toggleMidiPlayPause() },
                    midiVolume = uiState.midiVolume,
                    onMidiVolumeChange = { viewModel.setMidiVolume(it) },
                    midiFolders = uiState.midiFolders,
                    activeMidiName = uiState.selectedMidiName,
                    onToggleFolder = { viewModel.toggleMidiFolder(it) },
                    onSelectMidiFile = { viewModel.playMidiFile(it) },
                    onClose = { viewModel.closeMidiPanel() }
                )
            }
                    }
                }
            }
        }

        when (uiState.activePopup) {
            ActivePopup.EFFECTS -> {
                val fxParams = uiState.fxParameters[uiState.activeEffectTrackId] ?: FxParameters()
                val currentTrack = if (uiState.activeEffectTrackId == 0) uiState.masterTrack else uiState.tracks.find { it.id == uiState.activeEffectTrackId }
                EffectsDialog(
                    trackId = uiState.activeEffectTrackId,
                    track = currentTrack,
                    fxParameters = fxParams,
                    onUpdateFx = { transform -> viewModel.updateFxParameter(uiState.activeEffectTrackId, transform) },
                    onSetReverbPreset = { viewModel.setTrackReverbPreset(uiState.activeEffectTrackId, it) },
                    onSetVelocityCurve = { viewModel.setTrackVelocityCurve(uiState.activeEffectTrackId, it) },
                    activeTab = uiState.activeFxTab,
                    onTabChange = { viewModel.setFxTab(it) },
                    onClose = { viewModel.closePopup() }
                )
            }
            ActivePopup.SOUNDFONT -> {
                val slot = uiState.audioSlots.getOrNull(uiState.activeSoundfontSlotId)
                val activePresets = slot?.presets ?: emptyList()
                val activePresetId = slot?.preset ?: 0
                val activeTrack = uiState.tracks.getOrNull(uiState.activeSoundfontSlotId)
                val activeSfName = activeTrack?.soundfontName.orEmpty()

                SoundfontDialog(
                    trackId = uiState.activeSoundfontSlotId,
                    source = if (uiState.activeSoundfontSlotId == 8) "drum" else if (uiState.activeSoundfontSlotId == 9) "pad" else "track",
                    presets = activePresets,
                    bankFiles = uiState.soundfontBankFiles,
                    soundfontStorageFiles = uiState.realSoundfonts,
                    selectedPresetId = activePresetId,
                    selectedSoundfontName = activeSfName,
                    onSelectPreset = { viewModel.selectSf2Preset(it) },
                    onSelectPresetFull = { viewModel.selectSf2Preset(it) },
                    onSelectSf2File = { viewModel.loadSoundfontFromStorage(it) },
                    onUnloadSoundFont = { viewModel.unloadSoundFontFromSlot() },
                    onDeleteSf2File = { viewModel.deleteSoundFontFile(it) },
                    activeTab = uiState.activeSf2Tab,
                    onTabChange = { viewModel.setSf2Tab(it) },
                    onImportSf2 = { sf2PickerLauncher.launch(arrayOf("*/*")) },
                    onClose = { viewModel.closePopup() }
                )
            }
            ActivePopup.LOOPS -> {
                LoopsDialog(
                    isOpen = true,
                    onClose = { viewModel.closeLoopsDialog() },
                    loopFolders = uiState.loopFolders,
                    activeLoopFile = uiState.activeLoopFile,
                    isLoopPlaying = uiState.isLoopPlaying,
                    loopVolume = uiState.loopVolume,
                    onLoopVolumeChange = { viewModel.setLoopVolume(it) },
                    bpm = uiState.bpm,
                    onUpdateBpm = { viewModel.setGlobalBpm(it) },
                    selectedBeats = uiState.selectedBeatCount,
                    onSelectBeats = { viewModel.selectBeatCount(it) },
                    onToggleFolder = { viewModel.toggleLoopFolder(it) },
                    onSelectFile = { viewModel.selectAndToggleLoopFile(it) },
                    onDeleteFile = { viewModel.deleteLoopFile(it) },
                    editingLoopFile = uiState.editingLoopFile,
                    onOpenEditFile = { viewModel.openLoopEditor(it) },
                    onCloseEditFile = { viewModel.closeLoopEditor() },
                    onToggleEditorPlay = { viewModel.toggleLoopEditorPlayback() },
                    editorBeats = uiState.loopEditorBeats,
                    onUpdateEditorBeats = { viewModel.updateLoopEditorBeats(it) },
                    editorStartMs = uiState.loopEditorStartMs,
                    editorEndMs = uiState.loopEditorEndMs,
                    onUpdateEditorTrims = { start, end -> viewModel.updateLoopEditorTrims(start, end) },
                    editorStartStep = uiState.loopEditorStartStep,
                    editorEndStep = uiState.loopEditorEndStep,
                    onUpdateEditorSteps = { start, end -> viewModel.updateLoopEditorSteps(start, end) },
                    onOverwriteEditChanges = { beats, start, end, sStep, eStep ->
                        viewModel.overwriteLoopFile(beats, start, end, sStep, eStep)
                    },
                    onSaveCopyEditChanges = { beats, start, end, sStep, eStep ->
                        viewModel.saveCopyLoopFile(beats, start, end, sStep, eStep)
                    },
                    onRenameFile = { file, newName ->
                        viewModel.renameLoopFile(file, newName)
                    },
                    onImportLoop = { loopPickerLauncher.launch(arrayOf("audio/*", "*/*")) },
                    lastPath = uiState.lastLoopsPath,
                    onUpdateLastPath = { viewModel.updateLastLoopsPath(it) }
                )
            }
            else -> {}
        }

        // Settings Drawer (AOSP Material You settings & engine controls)
        SettingsDrawer(
            isOpen = uiState.isSettingsDrawerOpen,
            onClose = { viewModel.closeSettingsDrawer() },
            subPage = uiState.settingsSubPage,
            onNavigateSubPage = { viewModel.setSettingsSubPage(it) },
            midiDevices = uiState.midiDevices,
            onToggleMidiDevice = { viewModel.toggleMidiDevice(it) },
            audioEngine = uiState.audioEngine,
            onSelectAudioEngine = { viewModel.setAudioEngine(it) },
            audioBufferSize = uiState.audioBufferSize,
            onSelectBufferSize = { viewModel.setAudioBufferSize(it) },
            polyphony = uiState.polyphony,
            onSelectPolyphony = { viewModel.setPolyphony(it) },
            selectedLanguage = uiState.selectedLanguage,
            onSelectLanguage = { viewModel.setSelectedLanguage(it) },
            masterPunch = uiState.masterPunch,
            onMasterPunchChange = { viewModel.setMasterPunch(it) },
            spatialWidener = uiState.spatialWidener,
            onSpatialWidenerChange = { viewModel.setSpatialWidener(it) },
            keepScreenOn = uiState.keepScreenOn,
            onToggleKeepScreenOn = { viewModel.toggleKeepScreenOn() },
            velocityMin = uiState.globalVelocityMin,
            velocityMax = uiState.globalVelocityMax,
            onVelocityRangeChange = { min, max -> viewModel.setGlobalVelocityRange(min, max) },
            useFlats = uiState.useFlats,
            onToggleUseFlats = { viewModel.toggleUseFlats() },
            themeHue = uiState.themeHue,
            onThemeHueChange = { viewModel.setThemeHue(it) },
            themeAccentSaturation = uiState.themeAccentSaturation,
            onThemeAccentSaturationChange = { viewModel.setThemeAccentSaturation(it) },
            themeBgSaturation = uiState.themeBgSaturation,
            onThemeBgSaturationChange = { viewModel.setThemeBgSaturation(it) },
            themeBgLightness = uiState.themeBgLightness,
            onThemeBgLightnessChange = { viewModel.setThemeBgLightness(it) },
            themeModulesLightness = uiState.themeModulesLightness,
            onThemeModulesLightnessChange = { viewModel.setThemeModulesLightness(it) },
            themePaletteName = uiState.themePaletteName,
            onSelectThemePalette = { viewModel.setThemePalette(it) },
            onResetTheme = { viewModel.resetThemeToDefault() },
            isFaderAutoFit = uiState.isFaderAutoFit,
            onToggleFaderAutoFit = { viewModel.toggleFaderAutoFit() }
        )

        // Sheets & Chords Sliding Side Drawer Overlay
        SheetsDrawer(
            isOpen = uiState.isSheetsDrawerOpen,
            onClose = { viewModel.closeSheetsDrawer() },
            notesText = uiState.notesText,
            onNotesChange = { viewModel.updateNotesText(it) },
            transpose = uiState.transpose,
            onTransposeChange = { viewModel.updateTranspose(it) },
            onOpenFullPage = {
                viewModel.closeSheetsDrawer()
                viewModel.navigateToPage(AppScreenPage.SHEETS)
            },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(115f)
        )

        // Patches Sliding Side Drawer (Exact sliding drawer from right matching Settings)
        SceneDialog(
            isOpen = uiState.activePopup == ActivePopup.SCENE,
            patches = uiState.patches,
            activePatchId = uiState.activePatchId,
            onSelectPatch = { viewModel.selectPatch(it) },
            onCreateNewPatch = { name, isBlank -> viewModel.createNewPatch(name, isBlank) },
            onDeletePatch = { viewModel.deletePatch(it) },
            onRenamePatch = { id, newName -> viewModel.renamePatch(id, newName) },
            onUpdateActiveScene = { viewModel.updateActiveScene() },
            onNavigateHome = { viewModel.navigateToProjectsHome() },
            onClose = { viewModel.closePopup() },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(120f)
        )

        // Material 3 Storage Permission Explanation Dialog
        if (uiState.showStoragePermissionDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.setStoragePermissionDialogVisible(false) },
                title = {
                    Text(
                        text = "SoundStage Storage Access",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                text = {
                    Text(
                        text = "To detect your custom SoundFonts (.sf2), audio loops, and presets located in the external /SoundStage folder, the application requires full file access permission.\n\nWithout this permission, only built-in application asset folders will be scanned.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.setStoragePermissionDialogVisible(false)
                            onRequestManageStoragePermission()
                        }
                    ) {
                        Text("Grant Permission")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.setStoragePermissionDialogVisible(false) }
                    ) {
                        Text("Later")
                    }
                }
            )
        }

        // Startup Splash Screen Animation
        if (!isSplashFinished) {
            LiveKeysSplashScreen(
                onFinished = { isSplashFinished = true }
            )
        }
    }
}
