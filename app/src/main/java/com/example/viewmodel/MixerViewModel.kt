package com.example.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.example.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MixerUiState(
    val transpose: Int = 0,
    val octave: Int = 0,
    val bpm: Int = 120,
    val isRecording: Boolean = false,
    val isMetronomeOn: Boolean = false,
    val isMetroPanelOpen: Boolean = false,
    val metronomeSignature: String = "4/4",
    val metronomeVolume: Float = 0.65f,
    
    val isLoopsPanelOpen: Boolean = false,
    val selectedBeatCount: Int = 4,
    val loopFolders: List<LoopFolder> = emptyList(),
    val activeLoopFile: LoopFile? = null,
    
    val tracks: List<TrackChannel> = emptyList(),
    val masterTrack: TrackChannel = TrackChannel(
        id = 0,
        name = "MASTER",
        isMaster = true,
        volume = 0.62f,
        fxSummary = "Effects (Reverb, comp...)"
    ),
    
    // Virtual Keyboard
    val keyboardHeightFraction: Float = 0f, // 0f = collapsed, 0.5f = half, 1f = full
    val isVelocityEnabled: Boolean = true,
    val isSustainActive: Boolean = false,
    val pressedKeys: Set<String> = emptySet(),
    
    // Popups
    val activePopup: ActivePopup = ActivePopup.NONE,
    val isDrumPadPinned: Boolean = false,
    val isTonicPadPinned: Boolean = false,
    val activeEffectTrackId: Int = 1,
    val activeSoundfontTrackId: Int = 1,
    val activeSoundfontSource: String = "track", // "track", "drum", "pad"
    
    // Drum Pad
    val drumPads: List<DrumPadItem> = emptyList(),
    val drumVolume: Float = 0.75f,
    val drumReverb: Float = 0.24f,
    val drumActiveTab: String = "pads", // "pads", "bank", "files"
    val editingDrumPadId: Int? = null, // for sound assigner dialog
    
    // Tonic Pad
    val isMultiPadEnabled: Boolean = true, // requested feature: multiple pads simultaneously
    val activeTonicNotes: Set<String> = setOf("D"),
    val tonicOctaveRange: String = "C4 — C5",
    val tonicMode: String = "Chromatique",
    val tonicBrightness: Float = 0.70f,
    val tonicShimmer: Float = 0.0f,
    
    // FX Rack
    val activeFxTab: String = "eq", // "eq", "reverb", "comp", "delay"
    val fxParameters: Map<Int, FxParameters> = emptyMap(),
    
    // Soundfonts & Scenes
    val activeSf2Tab: String = "bank", // "bank", "other"
    val soundfontPresets: List<SoundfontPreset> = emptyList(),
    val soundfontBankFiles: List<SoundfontBankFile> = emptyList(),
    val selectedSf2PresetId: Int = 1,
    val scenes: List<ScenePreset> = emptyList(),
    val activeSceneId: String = "intro",
    
    // Settings Drawer
    val isSettingsDrawerOpen: Boolean = false,
    val isLowLatencyAudio: Boolean = true,
    val isKeyboardVelocityTouch: Boolean = true,
    val isMetronomeInRec: Boolean = false,
    val appFolder: String = "/Music/SoundfontsLive/"
)

class MixerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<MixerUiState> = _uiState.asStateFlow()

    private fun createInitialState(): MixerUiState {
        val initialTracks = (1..8).map { i ->
            TrackChannel(
                id = i,
                name = if (i == 1) ".sf2 name" else "Piste $i",
                volume = 0.40f + (i * 0.04f).coerceAtMost(0.4f),
                fxSummary = "Fx, EQ...",
                soundfontName = if (i == 1) "FluidR3-Mono.sf2" else "GeneralUser.sf2",
                patchName = when (i) {
                    1 -> "Grand Piano"
                    2 -> "Rhodes E.Piano"
                    3 -> "Strings Legato"
                    4 -> "Warm Pad"
                    5 -> "Slap Bass"
                    6 -> "Clean Guitar"
                    7 -> "Brass Section"
                    else -> "Flute Solo"
                }
            )
        }

        val initialDrumPads = listOf(
            DrumPadItem(1, "Kick", DrumSoundType.SAMPLE, "kick_808_deep.wav", "C1", 1, "C"),
            DrumPadItem(2, "Snare", DrumSoundType.SAMPLE, "snare_crisp.wav", "D1", 1, "D"),
            DrumPadItem(3, "HH Closed", DrumSoundType.SAMPLE, "hat_trap_closed.wav", "F#1", 1, "F#"),
            DrumPadItem(4, "HH Open", DrumSoundType.SAMPLE, "hat_open_bright.wav", "A#1", 1, "A#"),
            DrumPadItem(5, "Clap", DrumSoundType.SAMPLE, "clap_vinyl.mp3", "D#1", 1, "D#"),
            DrumPadItem(6, "Tom Low", DrumSoundType.SAMPLE, "tom_floor_punch.wav", "F1", 1, "F"),
            DrumPadItem(7, "Tom High", DrumSoundType.SAMPLE, "tom_rack_hi.wav", "A1", 1, "A"),
            DrumPadItem(8, "Crash", DrumSoundType.SAMPLE, "crash_bright.mp3", "C#2", 2, "C#")
        )

        val initialLoopFolders = listOf(
            LoopFolder(
                name = "Drums",
                icon = "🥁",
                isOpen = true,
                files = listOf(
                    LoopFile("drumloop_120bpm.wav", "0:04", "Drums"),
                    LoopFile("perc_shaker_loop.wav", "0:04", "Drums"),
                    LoopFile("hihat_groove_16.wav", "0:02", "Drums")
                )
            ),
            LoopFolder(
                name = "Bass",
                icon = "🎸",
                isOpen = false,
                files = listOf(
                    LoopFile("bass_groove_A.wav", "0:08", "Bass"),
                    LoopFile("sub_bass_riff.wav", "0:04", "Bass")
                )
            ),
            LoopFolder(
                name = "Vocal",
                icon = "🎤",
                isOpen = false,
                files = listOf(
                    LoopFile("vocal_chop_01.mp3", "0:02", "Vocal"),
                    LoopFile("adlib_ohh.mp3", "0:01", "Vocal")
                )
            ),
            LoopFolder(
                name = "Ambiance",
                icon = "🌊",
                isOpen = false,
                files = listOf(
                    LoopFile("pad_texture_wide.wav", "0:12", "Ambiance")
                )
            )
        )

        val initialScenes = listOf(
            ScenePreset("intro", "Intro Ballade", "Aujourd'hui 14:02", NeonCyan),
            ScenePreset("refrain", "Refrain Puissant", "Aujourd'hui 13:40", NeonMagenta),
            ScenePreset("break", "Break Ambiance", "Hier 20:11", SoloAmber),
            ScenePreset("live", "Live Set A", "22 août", MuteRed)
        )

        val initialPresets = listOf(
            SoundfontPreset(1, "Grand Piano", 0),
            SoundfontPreset(2, "Rhodes E.Piano", 1),
            SoundfontPreset(3, "Strings Legato", 2),
            SoundfontPreset(4, "Choir Ah", 3),
            SoundfontPreset(5, "Warm Synth Pad", 4),
            SoundfontPreset(6, "Acoustic Bass", 5),
            SoundfontPreset(7, "Organ B3 Clean", 6),
            SoundfontPreset(8, "Brass Ensembles", 7)
        )

        val initialBankFiles = listOf(
            SoundfontBankFile("GeneralUser-GS.sf2", "Soundfonts/GeneralUser-GS.sf2", "29.8 MB"),
            SoundfontBankFile("FluidR3-Mono.sf2", "Soundfonts/FluidR3-Mono.sf2", "141.2 MB"),
            SoundfontBankFile("Orchestral-HQ.sf2", "Soundfonts/Orchestral-HQ.sf2", "88.4 MB"),
            SoundfontBankFile("WarmPads-Vol2.sf2", "Soundfonts/WarmPads-Vol2.sf2", "14.5 MB"),
            SoundfontBankFile("FX Worship.sf2", "Soundfonts/FX Worship.sf2", "22.1 MB")
        )

        val initialFx = (0..8).associateWith { FxParameters() }

        return MixerUiState(
            tracks = initialTracks,
            loopFolders = initialLoopFolders,
            activeLoopFile = initialLoopFolders.first().files.first(),
            drumPads = initialDrumPads,
            scenes = initialScenes,
            soundfontPresets = initialPresets,
            soundfontBankFiles = initialBankFiles,
            fxParameters = initialFx
        )
    }

    // Top Bar Actions
    fun updateTranspose(delta: Int) {
        _uiState.update { it.copy(transpose = (it.transpose + delta).coerceIn(-12, 12)) }
    }

    fun updateOctave(delta: Int) {
        _uiState.update { it.copy(octave = (it.octave + delta).coerceIn(-4, 4)) }
    }

    fun updateBpm(delta: Int) {
        _uiState.update { it.copy(bpm = (it.bpm + delta).coerceIn(20, 300)) }
    }

    fun toggleLoopsPanel() {
        _uiState.update { it.copy(isLoopsPanelOpen = !it.isLoopsPanelOpen, isMetroPanelOpen = false) }
    }

    fun closeLoopsPanel() {
        _uiState.update { it.copy(isLoopsPanelOpen = false) }
    }

    fun selectBeatCount(beats: Int) {
        _uiState.update { it.copy(selectedBeatCount = beats) }
    }

    fun toggleLoopFolder(name: String) {
        _uiState.update { state ->
            val updated = state.loopFolders.map { folder ->
                if (folder.name == name) folder.copy(isOpen = !folder.isOpen) else folder
            }
            state.copy(loopFolders = updated)
        }
    }

    fun selectLoopFile(file: LoopFile) {
        _uiState.update { it.copy(activeLoopFile = file) }
    }

    fun triggerPanic() {
        // Silences all notes, resets active pad presses & held keys
        _uiState.update { it.copy(pressedKeys = emptySet()) }
    }

    // Metro & Recording
    fun toggleRecording() {
        _uiState.update { it.copy(isRecording = !it.isRecording) }
    }

    fun toggleMetronome() {
        _uiState.update { it.copy(isMetronomeOn = !it.isMetronomeOn) }
    }

    fun toggleMetroPanel() {
        _uiState.update { it.copy(isMetroPanelOpen = !it.isMetroPanelOpen, isLoopsPanelOpen = false) }
    }

    fun closeMetroPanel() {
        _uiState.update { it.copy(isMetroPanelOpen = false) }
    }

    fun setMetronomeSignature(sig: String) {
        _uiState.update { it.copy(metronomeSignature = sig) }
    }

    fun setMetronomeVolume(vol: Float) {
        _uiState.update { it.copy(metronomeVolume = vol.coerceIn(0f, 1f)) }
    }

    // Tracks Control
    fun setTrackVolume(trackId: Int, volume: Float) {
        _uiState.update { state ->
            if (trackId == 0) {
                state.copy(masterTrack = state.masterTrack.copy(volume = volume.coerceIn(0f, 1f)))
            } else {
                val updated = state.tracks.map { track ->
                    if (track.id == trackId) track.copy(volume = volume.coerceIn(0f, 1f)) else track
                }
                state.copy(tracks = updated)
            }
        }
    }

    fun toggleMute(trackId: Int) {
        _uiState.update { state ->
            val updated = state.tracks.map { track ->
                if (track.id == trackId) track.copy(isMuted = !track.isMuted) else track
            }
            state.copy(tracks = updated)
        }
    }

    fun toggleSolo(trackId: Int) {
        _uiState.update { state ->
            val updated = state.tracks.map { track ->
                if (track.id == trackId) track.copy(isSolo = !track.isSolo) else track
            }
            state.copy(tracks = updated)
        }
    }

    // Virtual Keyboard
    fun setKeyboardHeightFraction(fraction: Float) {
        _uiState.update { it.copy(keyboardHeightFraction = fraction.coerceIn(0f, 1f)) }
    }

    fun cycleKeyboardExpansion() {
        _uiState.update { state ->
            val next = when {
                state.keyboardHeightFraction < 0.2f -> 0.5f
                state.keyboardHeightFraction < 0.7f -> 1.0f
                else -> 0.0f
            }
            state.copy(keyboardHeightFraction = next)
        }
    }

    fun toggleVelocity() {
        _uiState.update { it.copy(isVelocityEnabled = !it.isVelocityEnabled) }
    }

    fun toggleSustain() {
        _uiState.update { it.copy(isSustainActive = !it.isSustainActive) }
    }

    fun onKeyDown(key: String) {
        _uiState.update { state ->
            state.copy(pressedKeys = state.pressedKeys + key)
        }
    }

    fun onKeyUp(key: String) {
        _uiState.update { state ->
            if (!state.isSustainActive) {
                state.copy(pressedKeys = state.pressedKeys - key)
            } else {
                state // keep sustained until sustain toggled or panic
            }
        }
    }

    // Popups Management
    fun openPopup(popup: ActivePopup) {
        _uiState.update { it.copy(activePopup = popup, isLoopsPanelOpen = false, isMetroPanelOpen = false) }
    }

    fun closePopup() {
        _uiState.update { it.copy(activePopup = ActivePopup.NONE, editingDrumPadId = null) }
    }

    fun closeDrumPad() {
        _uiState.update { 
            it.copy(
                isDrumPadPinned = false,
                activePopup = if (it.activePopup == ActivePopup.DRUM_PAD) ActivePopup.NONE else it.activePopup,
                editingDrumPadId = null
            ) 
        }
    }

    fun closeTonicPad() {
        _uiState.update { 
            it.copy(
                isTonicPadPinned = false,
                activePopup = if (it.activePopup == ActivePopup.TONIC_PAD) ActivePopup.NONE else it.activePopup
            ) 
        }
    }

    fun togglePinDrumPad() {
        _uiState.update { it.copy(isDrumPadPinned = !it.isDrumPadPinned) }
    }

    fun togglePinTonicPad() {
        _uiState.update { it.copy(isTonicPadPinned = !it.isTonicPadPinned) }
    }

    fun openEffectsForTrack(trackId: Int) {
        _uiState.update { it.copy(activePopup = ActivePopup.EFFECTS, activeEffectTrackId = trackId) }
    }

    fun openSoundfontForTrack(trackId: Int, source: String = "track") {
        _uiState.update {
            it.copy(
                activePopup = ActivePopup.SOUNDFONT,
                activeSoundfontTrackId = trackId,
                activeSoundfontSource = source
            )
        }
    }

    // Drum Pad Controls
    fun setDrumTab(tab: String) {
        _uiState.update { it.copy(drumActiveTab = tab) }
    }

    fun setDrumVolume(vol: Float) {
        _uiState.update { it.copy(drumVolume = vol.coerceIn(0f, 1f)) }
    }

    fun setDrumReverb(rev: Float) {
        _uiState.update { it.copy(drumReverb = rev.coerceIn(0f, 1f)) }
    }

    fun onDrumPadPressed(padId: Int) {
        // Fast touch down visual flash
        _uiState.update { state ->
            val updated = state.drumPads.map { pad ->
                if (pad.id == padId) pad.copy(isPressed = true) else pad
            }
            state.copy(drumPads = updated)
        }
    }

    fun onDrumPadReleased(padId: Int) {
        _uiState.update { state ->
            val updated = state.drumPads.map { pad ->
                if (pad.id == padId) pad.copy(isPressed = false) else pad
            }
            state.copy(drumPads = updated)
        }
    }

    fun openDrumSoundAssigner(padId: Int) {
        _uiState.update { it.copy(editingDrumPadId = padId) }
    }

    fun closeDrumSoundAssigner() {
        _uiState.update { it.copy(editingDrumPadId = null) }
    }

    fun assignDrumSample(padId: Int, sampleName: String) {
        _uiState.update { state ->
            val updated = state.drumPads.map { pad ->
                if (pad.id == padId) {
                    pad.copy(
                        soundType = DrumSoundType.SAMPLE,
                        sampleFileName = sampleName
                    )
                } else pad
            }
            state.copy(drumPads = updated, editingDrumPadId = null)
        }
    }

    fun assignDrumSf2Note(padId: Int, key: String, octave: Int) {
        val noteName = "$key$octave"
        _uiState.update { state ->
            val updated = state.drumPads.map { pad ->
                if (pad.id == padId) {
                    pad.copy(
                        soundType = DrumSoundType.SF2_NOTE,
                        sf2Note = noteName,
                        sf2NoteOctave = octave,
                        sf2NoteKey = key
                    )
                } else pad
            }
            state.copy(drumPads = updated, editingDrumPadId = null)
        }
    }

    // Tonic Pad Controls
    fun toggleMultiPad() {
        _uiState.update { it.copy(isMultiPadEnabled = !it.isMultiPadEnabled) }
    }

    fun onTonicNoteClick(note: String) {
        _uiState.update { state ->
            if (state.isMultiPadEnabled) {
                // Multi-pad: toggle note in set
                val newSet = if (state.activeTonicNotes.contains(note)) {
                    if (state.activeTonicNotes.size > 1) state.activeTonicNotes - note else state.activeTonicNotes
                } else {
                    state.activeTonicNotes + note
                }
                state.copy(activeTonicNotes = newSet)
            } else {
                // Single note active
                state.copy(activeTonicNotes = setOf(note))
            }
        }
    }

    fun setTonicBrightness(brightness: Float) {
        _uiState.update { it.copy(tonicBrightness = brightness.coerceIn(0f, 1f)) }
    }

    fun setTonicShimmer(shimmer: Float) {
        _uiState.update { it.copy(tonicShimmer = shimmer.coerceIn(0f, 1f)) }
    }

    // FX Controls
    fun setFxTab(tab: String) {
        _uiState.update { it.copy(activeFxTab = tab) }
    }

    fun updateFxParameter(trackId: Int, transform: (FxParameters) -> FxParameters) {
        _uiState.update { state ->
            val current = state.fxParameters[trackId] ?: FxParameters()
            val updatedMap = state.fxParameters.toMutableMap()
            updatedMap[trackId] = transform(current)
            state.copy(fxParameters = updatedMap)
        }
    }

    // Soundfonts & Scenes
    fun setSf2Tab(tab: String) {
        _uiState.update { it.copy(activeSf2Tab = tab) }
    }

    fun selectSf2Preset(presetId: Int) {
        _uiState.update { state ->
            val preset = state.soundfontPresets.find { it.id == presetId } ?: return@update state
            val trackId = state.activeSoundfontTrackId
            val updatedTracks = state.tracks.map { track ->
                if (track.id == trackId) {
                    track.copy(patchName = preset.name, bank = preset.bankNumber)
                } else track
            }
            state.copy(
                selectedSf2PresetId = presetId,
                tracks = updatedTracks
            )
        }
    }

    fun selectScene(sceneId: String) {
        _uiState.update { it.copy(activeSceneId = sceneId) }
    }

    // Settings Drawer
    fun openSettingsDrawer() {
        _uiState.update { it.copy(isSettingsDrawerOpen = true) }
    }

    fun closeSettingsDrawer() {
        _uiState.update { it.copy(isSettingsDrawerOpen = false) }
    }

    fun toggleLowLatencyAudio() {
        _uiState.update { it.copy(isLowLatencyAudio = !it.isLowLatencyAudio) }
    }

    fun toggleKeyboardVelocityTouch() {
        _uiState.update { it.copy(isKeyboardVelocityTouch = !it.isKeyboardVelocityTouch) }
    }

    fun toggleMetronomeInRec() {
        _uiState.update { it.copy(isMetronomeInRec = !it.isMetronomeInRec) }
    }
}
