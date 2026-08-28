package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
                .padding(horizontal = 10.dp, vertical = 8.dp)
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
                    selectedBeats = uiState.selectedBeatCount,
                    onSelectBeats = { viewModel.selectBeatCount(it) },
                    loopFolders = uiState.loopFolders,
                    activeLoopFile = uiState.activeLoopFile,
                    onToggleLoopFolder = { viewModel.toggleLoopFolder(it) },
                    onSelectLoopFile = { viewModel.selectLoopFile(it) },
                    onOpenDrumPad = { viewModel.openPopup(ActivePopup.DRUM_PAD) },
                    onOpenTonicPad = { viewModel.openPopup(ActivePopup.TONIC_PAD) },
                    onPanic = { viewModel.triggerPanic() },
                    onOpenScenes = { viewModel.openPopup(ActivePopup.SCENE) },
                    onOpenSettings = { viewModel.openSettingsDrawer() }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 2. MIXER TRACKS SECTION (8 Tracks + 1 Master)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Regular Tracks 1..8
                    uiState.tracks.forEach { track ->
                        VerticalTrackChannel(
                            track = track,
                            onVolumeChange = { vol -> viewModel.setTrackVolume(track.id, vol) },
                            onMuteToggle = { viewModel.toggleMute(track.id) },
                            onSoloToggle = { viewModel.toggleSolo(track.id) },
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
                        onMuteToggle = {},
                        onSoloToggle = {},
                        onTrackNameClick = {},
                        onFxClick = { viewModel.openEffectsForTrack(0) },
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 3. BOTTOM BAR
                BottomBar(
                    isRecording = uiState.isRecording,
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
                    onKeyboardHandleClick = { viewModel.cycleKeyboardExpansion() },
                    onKeyboardDrag = { deltaY ->
                        // dragging up decreases Y, so delta < 0 increases fraction
                        val fractionDelta = -deltaY / 200f
                        viewModel.setKeyboardHeightFraction(uiState.keyboardHeightFraction + fractionDelta)
                    }
                )

                // 4. RETRACTABLE VIRTUAL PIANO KEYBOARD
                VirtualPianoKeyboard(
                    heightFraction = animatedKbFraction,
                    isVelocityEnabled = uiState.isVelocityEnabled,
                    onToggleVelocity = { viewModel.toggleVelocity() },
                    isSustainActive = uiState.isSustainActive,
                    onToggleSustain = { viewModel.toggleSustain() },
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

            // Floating Loops Dropdown Panel
            if (uiState.isLoopsPanelOpen) {
                LoopsFloatingPanel(
                    isOpen = uiState.isLoopsPanelOpen,
                    selectedBeats = uiState.selectedBeatCount,
                    onSelectBeats = { viewModel.selectBeatCount(it) },
                    loopFolders = uiState.loopFolders,
                    activeLoopFile = uiState.activeLoopFile,
                    onToggleFolder = { viewModel.toggleLoopFolder(it) },
                    onSelectFile = { viewModel.selectLoopFile(it) },
                    onClose = { viewModel.closeLoopsPanel() },
                    modifier = Modifier
                        .padding(top = 40.dp, start = 140.dp)
                        .align(Alignment.TopStart)
                )
            }

            // Floating Metronome Dropdown Panel
            if (uiState.isMetroPanelOpen) {
                MetronomeFloatingPanel(
                    isOpen = uiState.isMetroPanelOpen,
                    isMetronomeOn = uiState.isMetronomeOn,
                    onToggleMetronome = { viewModel.toggleMetronome() },
                    selectedSignature = uiState.metronomeSignature,
                    onSelectSignature = { viewModel.setMetronomeSignature(it) },
                    volume = uiState.metronomeVolume,
                    onVolumeChange = { viewModel.setMetronomeVolume(it) },
                    onClose = { viewModel.closeMetroPanel() },
                    modifier = Modifier
                        .padding(bottom = 44.dp, start = 140.dp)
                        .align(Alignment.BottomStart)
                )
            }
        }

        // ================= POPUP OVERLAYS & FLOATING WINDOWS =================

        // Drum Pad (Shown if pinned or active)
        if (uiState.isDrumPadPinned || uiState.activePopup == ActivePopup.DRUM_PAD) {
            DrumPadDialog(
                drumPads = uiState.drumPads,
                volume = uiState.drumVolume,
                onVolumeChange = { viewModel.setDrumVolume(it) },
                reverb = uiState.drumReverb,
                onReverbChange = { viewModel.setDrumReverb(it) },
                activeTab = uiState.drumActiveTab,
                onTabChange = { viewModel.setDrumTab(it) },
                isPinned = uiState.isDrumPadPinned,
                onTogglePin = { viewModel.togglePinDrumPad() },
                onClose = { viewModel.closeDrumPad() },
                onOpenSf2Picker = { viewModel.openSoundfontForTrack(1, "drum") },
                onPadPressed = { viewModel.onDrumPadPressed(it) },
                onPadReleased = { viewModel.onDrumPadReleased(it) },
                onOpenAssigner = { viewModel.openDrumSoundAssigner(it) },
                editingPadId = uiState.editingDrumPadId,
                onAssignSample = { id, sample -> viewModel.assignDrumSample(id, sample) },
                onAssignSf2Note = { id, key, oct -> viewModel.assignDrumSf2Note(id, key, oct) },
                onCloseAssigner = { viewModel.closeDrumSoundAssigner() }
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
                mode = uiState.tonicMode,
                brightness = uiState.tonicBrightness,
                onBrightnessChange = { viewModel.setTonicBrightness(it) },
                shimmer = uiState.tonicShimmer,
                onShimmerChange = { viewModel.setTonicShimmer(it) },
                isPinned = uiState.isTonicPadPinned,
                onTogglePin = { viewModel.togglePinTonicPad() },
                onClose = { viewModel.closeTonicPad() },
                onOpenSf2Picker = { viewModel.openSoundfontForTrack(1, "pad") }
            )
        }

        when (uiState.activePopup) {
            ActivePopup.EFFECTS -> {
                val fxParams = uiState.fxParameters[uiState.activeEffectTrackId] ?: FxParameters()
                EffectsDialog(
                    trackId = uiState.activeEffectTrackId,
                    fxParameters = fxParams,
                    onUpdateFx = { transform -> viewModel.updateFxParameter(uiState.activeEffectTrackId, transform) },
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
            ActivePopup.SCENE -> {
                SceneDialog(
                    scenes = uiState.scenes,
                    activeSceneId = uiState.activeSceneId,
                    onSelectScene = { viewModel.selectScene(it) },
                    onClose = { viewModel.closePopup() }
                )
            }
            ActivePopup.DRUM_PAD, ActivePopup.TONIC_PAD, ActivePopup.NONE -> {}
        }

        // Settings Drawer
        SettingsDrawer(
            isOpen = uiState.isSettingsDrawerOpen,
            onClose = { viewModel.closeSettingsDrawer() },
            isLowLatency = uiState.isLowLatencyAudio,
            onToggleLowLatency = { viewModel.toggleLowLatencyAudio() },
            isVelocityTouch = uiState.isKeyboardVelocityTouch,
            onToggleVelocityTouch = { viewModel.toggleKeyboardVelocityTouch() },
            isMetroInRec = uiState.isMetronomeInRec,
            onToggleMetroInRec = { viewModel.toggleMetronomeInRec() },
            appFolder = uiState.appFolder
        )
    }
}
