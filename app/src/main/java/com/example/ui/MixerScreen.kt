package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ActivePopup
import com.example.model.FxParameters
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.MixerViewModel

@Composable
fun MixerScreen(
    viewModel: MixerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isSplashFinished by remember { mutableStateOf(false) }

    // Real-time zero-latency chord analysis from pressed keys
    val detectedChord = remember(uiState.pressedKeys) {
        ChordCalculator.detect(uiState.pressedKeys)
    }

    // Smooth animation for keyboard retraction & tracks compression
    val animatedKbFraction by animateFloatAsState(
        targetValue = uiState.keyboardHeightFraction,
        animationSpec = tween(280),
        label = "kb_fraction_anim"
    )

    // Fullscreen Stage Container
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(6.dp)
            .testTag("mixer_screen_root")
    ) {
        // Device Chassis Card
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(16.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF15151D), Color(0xFF101014), Color(0xFF0D0D11))
                    )
                )
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. TOP BAR
                TopBar(
                    transpose = uiState.transpose,
                    octave = uiState.octave,
                    onTransposeChange = { viewModel.updateTranspose(it) },
                    onOctaveChange = { viewModel.updateOctave(it) },
                    isLoopsOpen = uiState.isLoopsPanelOpen,
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
                    isSplitterActive = uiState.isSplitterActive,
                    onToggleSplitter = { viewModel.toggleSplitter() },
                    onOpenDrumPad = { viewModel.openPopup(ActivePopup.DRUM_PAD) },
                    onOpenTonicPad = { viewModel.openPopup(ActivePopup.TONIC_PAD) },
                    onPanic = { viewModel.triggerPanic() },
                    onOpenScenes = {
                        if (uiState.activePopup == ActivePopup.SCENE) viewModel.closePopup() else viewModel.openPopup(ActivePopup.SCENE)
                    },
                    onOpenSettings = { viewModel.openSettingsDrawer() }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 2. MIXER TRACKS SECTION (8 Tracks + 1 Master)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Regular Tracks 1..8
                    uiState.tracks.forEach { track ->
                        VerticalTrackChannel(
                            track = track,
                            onVolumeChange = { vol -> viewModel.setTrackVolume(track.id, vol) },
                            onPowerToggle = { viewModel.toggleTrackPower(track.id) },
                            onPanChange = { pan -> viewModel.setTrackPan(track.id, pan) },
                            onMuteSoloClick = { viewModel.onTrackMuteSoloClick(track.id) },
                            onTrackNameClick = { viewModel.openSoundfontForTrack(track.id) },
                            onFxClick = { viewModel.openEffectsForTrack(track.id) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }

                    // Master Channel
                    VerticalTrackChannel(
                        track = uiState.masterTrack,
                        onVolumeChange = { vol -> viewModel.setTrackVolume(0, vol) },
                        onPowerToggle = {},
                        onPanChange = {},
                        onMuteSoloClick = {},
                        onTrackNameClick = {},
                        onFxClick = { viewModel.openEffectsForTrack(0) },
                        modifier = Modifier
                            .weight(1.08f)
                            .fillMaxHeight()
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 3. BOTTOM BAR (With MIDI Player, Real Chord Detector & Piano Toggle)
                BottomBar(
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
                    
                    // MIDI Player Controls
                    isMidiPlaying = uiState.isMidiPlaying,
                    selectedMidiName = uiState.selectedMidiName,
                    onOpenMidiDialog = { viewModel.toggleMidiPanel() },
                    onToggleMidiPlayPause = { viewModel.toggleMidiPlayPause() },
                    
                    // Real-time Detected Chord
                    detectedChord = detectedChord,
                    
                    isKeyboardActive = uiState.keyboardHeightFraction > 0f,
                    onToggleKeyboard = { viewModel.cycleKeyboardExpansion() },
                    onKeyboardHandleClick = { viewModel.cycleKeyboardExpansion() },
                    onKeyboardDrag = { deltaY ->
                        val fractionDelta = -deltaY / 200f
                        viewModel.setKeyboardHeightFraction(uiState.keyboardHeightFraction + fractionDelta)
                    }
                )

                // 4. RETRACTABLE MULTI-TOUCH VIRTUAL PIANO KEYBOARD WITH OCTAVE NAV & SCROLL BUTTONS
                VirtualPianoKeyboard(
                    heightFraction = animatedKbFraction,
                    pressedKeys = uiState.pressedKeys,
                    onKeyDown = { viewModel.onKeyDown(it) },
                    onKeyUp = { viewModel.onKeyUp(it) },
                    onGrabberDrag = { deltaY ->
                        val fractionDelta = -deltaY / 200f
                        viewModel.setKeyboardHeightFraction(uiState.keyboardHeightFraction + fractionDelta)
                    },
                    onGrabberClick = { viewModel.cycleKeyboardExpansion() }
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

            // Floating Loops Dropdown Panel
            AnimatedVisibility(
                visible = uiState.isLoopsPanelOpen,
                enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { -20 }, animationSpec = tween(200)),
                exit = fadeOut(tween(150)) + slideOutVertically(targetOffsetY = { -20 }, animationSpec = tween(150)),
                modifier = Modifier
                    .padding(top = 38.dp, start = 120.dp)
                    .align(Alignment.TopStart)
            ) {
                LoopsFloatingPanel(
                    isOpen = uiState.isLoopsPanelOpen,
                    isLoopPlaying = uiState.isLoopPlaying,
                    onToggleLoopPlayPause = { viewModel.toggleLoopPlayPause() },
                    loopVolume = uiState.loopVolume,
                    onLoopVolumeChange = { viewModel.setLoopVolume(it) },
                    selectedBeats = uiState.selectedBeatCount,
                    onSelectBeats = { viewModel.selectBeatCount(it) },
                    loopFolders = uiState.loopFolders,
                    activeLoopFile = uiState.activeLoopFile,
                    onToggleFolder = { viewModel.toggleLoopFolder(it) },
                    onSelectFile = { viewModel.selectAndToggleLoopFile(it) },
                    onClose = { viewModel.closeLoopsPanel() }
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

            // Scene In-Place Expanding View
            AnimatedVisibility(
                visible = uiState.activePopup == ActivePopup.SCENE,
                enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { -20 }, animationSpec = tween(200)),
                exit = fadeOut(tween(150)) + slideOutVertically(targetOffsetY = { -20 }, animationSpec = tween(150)),
                modifier = Modifier
                    .padding(top = 38.dp, end = 10.dp)
                    .align(Alignment.TopEnd)
            ) {
                SceneDialog(
                    isOpen = true,
                    scenes = uiState.scenes,
                    activeSceneId = uiState.activeSceneId,
                    onSelectScene = { viewModel.selectScene(it) },
                    onSaveCurrentScene = { viewModel.saveCurrentScene(it) },
                    onClose = { viewModel.closePopup() }
                )
            }
        }

        // ================= POPUP OVERLAYS & FLOATING WINDOWS =================

        // Drum Pad (Square Grid with Long-Press Customization: Palette, Gradient, LED, Neon, Material You)
        if (uiState.isDrumPadPinned || uiState.activePopup == ActivePopup.DRUM_PAD) {
            DrumPadDialog(
                drumPads = uiState.drumPads,
                volume = uiState.drumVolume,
                onVolumeChange = { viewModel.setDrumVolume(it) },
                reverb = uiState.drumReverb,
                onReverbChange = { viewModel.setDrumReverb(it) },
                activeTab = uiState.drumActiveTab,
                onTabChange = { viewModel.setDrumTab(it) },
                subView = uiState.drumSubView,
                onSetSubView = { viewModel.setDrumSubView(it) },
                soundfonts = uiState.soundfontFiles,
                audioFiles = uiState.loopAudioFiles,
                isPinned = uiState.isDrumPadPinned,
                onTogglePin = { viewModel.togglePinDrumPad() },
                onClose = { viewModel.closeDrumPad() },
                onPadPressed = { viewModel.onDrumPadPressed(it) },
                onPadReleased = { viewModel.onDrumPadReleased(it) },
                onUpdatePadCustomization = { padId, label, style ->
                    viewModel.updateDrumPadCustomization(padId, label, style)
                },
                onAssignPadSample = { padId, sample -> viewModel.assignDrumSample(padId, sample.name) },
                onAssignPadNote = { padId, noteStr, oct, key -> viewModel.assignDrumSf2Note(padId, key, oct) }
            )
        }

        // Tonic Pad (Shown if pinned or active)
        if (uiState.isTonicPadPinned || uiState.activePopup == ActivePopup.TONIC_PAD) {
            TonicPadDialog(
                activeNotes = uiState.activeTonicNotes,
                onNoteClick = { viewModel.onTonicNoteClick(it) },
                isMultiPadEnabled = uiState.isMultiPadEnabled,
                onToggleMultiPad = { viewModel.toggleMultiPad() },
                octaveRange = uiState.tonicOctaveRange,
                onOctaveMinus = { viewModel.onTonicOctaveMinus() },
                onOctavePlus = { viewModel.onTonicOctavePlus() },
                brightness = uiState.tonicBrightness,
                onBrightnessChange = { viewModel.setTonicBrightness(it) },
                shimmer = uiState.tonicShimmer,
                onShimmerChange = { viewModel.setTonicShimmer(it) },
                isPinned = uiState.isTonicPadPinned,
                onTogglePin = { viewModel.togglePinTonicPad() },
                onClose = { viewModel.closeTonicPad() },
                soundfonts = uiState.soundfontFiles
            )
        }

        when (uiState.activePopup) {
            ActivePopup.EFFECTS -> {
                val fxParams = uiState.fxParameters[uiState.activeEffectTrackId] ?: FxParameters()
                val currentTrack = if (uiState.activeEffectTrackId == 0) uiState.masterTrack else uiState.tracks.find { it.id == uiState.activeEffectTrackId }
                EffectsDialog(
                    trackId = uiState.activeEffectTrackId,
                    track = currentTrack,
                    fxParameters = fxParams,
                    isGlobalSplitterActive = uiState.isSplitterActive,
                    onUpdateFx = { transform -> viewModel.updateFxParameter(uiState.activeEffectTrackId, transform) },
                    onSetReverbPreset = { viewModel.setTrackReverbPreset(uiState.activeEffectTrackId, it) },
                    onSetVelocityCurve = { viewModel.setTrackVelocityCurve(uiState.activeEffectTrackId, it) },
                    onSetSplitRange = { min, max -> viewModel.setTrackSplitRange(uiState.activeEffectTrackId, min, max) },
                    activeTab = uiState.activeFxTab,
                    onTabChange = { viewModel.setFxTab(it) },
                    onClose = { viewModel.closePopup() }
                )
            }
            ActivePopup.SOUNDFONT -> {
                SoundfontDialog(
                    trackId = uiState.activeSoundfontTrackId,
                    source = uiState.activeSoundfontSource,
                    presets = uiState.soundfontPresets,
                    bankFiles = uiState.soundfontBankFiles,
                    selectedPresetId = uiState.selectedSf2PresetId,
                    onSelectPreset = { viewModel.selectSf2Preset(it) },
                    activeTab = uiState.activeSf2Tab,
                    onTabChange = { viewModel.setSf2Tab(it) },
                    onClose = { viewModel.closePopup() }
                )
            }
            else -> {}
        }

        // Settings Drawer (AOSP Material You with Theme selector & FL SoundGoodizer A/B/C/D)
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
            isLowLatency = uiState.isLowLatencyAudio,
            onToggleLowLatency = { viewModel.toggleLowLatencyAudio() },
            currentTheme = uiState.currentTheme,
            onSelectTheme = { viewModel.setAppTheme(it) },
            selectedLanguage = uiState.selectedLanguage,
            onSelectLanguage = { viewModel.setSelectedLanguage(it) },
            soundGoodizer = uiState.soundGoodizer,
            onSoundGoodizerChange = { viewModel.setSoundGoodizer(it) },
            soundGoodizerMode = uiState.soundGoodizerMode,
            onSoundGoodizerModeChange = { viewModel.setSoundGoodizerMode(it) },
            masterPunch = uiState.masterPunch,
            onMasterPunchChange = { viewModel.setMasterPunch(it) },
            spatialWidener = uiState.spatialWidener,
            onSpatialWidenerChange = { viewModel.setSpatialWidener(it) },
            velocityMin = uiState.globalVelocityMin,
            velocityMax = uiState.globalVelocityMax,
            onVelocityRangeChange = { min, max -> viewModel.setGlobalVelocityRange(min, max) }
        )

        // Startup Splash Screen Animation
        if (!isSplashFinished) {
            LiveKeysSplashScreen(
                onFinished = { isSplashFinished = true }
            )
        }
    }
}
