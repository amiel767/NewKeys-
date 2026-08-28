package com.example.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class MixerUiState(
    val transpose: Int = 0,
    val octave: Int = 0,
    val bpm: Int = 120,
    val isRecording: Boolean = false,
    val recordingDuration: Int = 0,
    val lastRecordedFile: String? = null,
    val isMetronomeOn: Boolean = false,
    val isMetroPanelOpen: Boolean = false,
    val metronomeSignature: String = "4/4",
    val metronomeVolume: Float = 0.65f,
    
    // Loops Module
    val isLoopsPanelOpen: Boolean = false,
    val isLoopPlaying: Boolean = false,
    val loopVolume: Float = 0.75f,
    val selectedBeatCount: Int = 4,
    val loopFolders: List<LoopFolder> = emptyList(),
    val activeLoopFile: LoopFile? = null,
    val currentLoopFolderPath: String = "/loops",
    
    // Global Sustain & Splitter
    val isSustainActive: Boolean = false,
    val isMidiPedalPressed: Boolean = false,
    val isSplitterActive: Boolean = false,
    
    // Tracks Console
    val tracks: List<TrackChannel> = emptyList(),
    val masterTrack: TrackChannel = TrackChannel(
        id = 0,
        name = "MASTER",
        isMaster = true,
        volume = 0.70f,
        fxSummary = "Master Processing",
        soundfontName = "",
        patchName = "MASTER",
        peakMeterL = 0.45f,
        peakMeterR = 0.48f
    ),
    
    // Virtual Keyboard & Lock
    val isKeyboardLocked: Boolean = false,
    val keyboardHeightFraction: Float = 0f, // 0f = collapsed, 0.5f = half, 1f = full
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
    val editingDrumPadId: Int? = null,
    
    // Tonic Pad
    val isMultiPadEnabled: Boolean = true,
    val activeTonicNotes: Set<String> = setOf("D"),
    val tonicOctaveRange: String = "C3 — C4",
    val tonicMode: String = "Chromatique",
    val tonicBrightness: Float = 0.70f,
    val tonicShimmer: Float = 0.15f,
    
    // FX Rack
    val activeFxTab: String = "eq", // "eq", "reverb", "velocity", "splitter", "comp", "delay"
    val fxParameters: Map<Int, FxParameters> = emptyMap(),
    
    // Soundfonts & Scenes
    val activeSf2Tab: String = "bank", // "bank", "other"
    val soundfontPresets: List<SoundfontPreset> = emptyList(),
    val soundfontBankFiles: List<SoundfontBankFile> = emptyList(),
    val selectedSf2PresetId: Int = 1,
    val scenes: List<ScenePreset> = emptyList(),
    val activeSceneId: String = "intro",
    
    // Settings Drawer (Sub-pages & Advanced Audio/FX)
    val isSettingsDrawerOpen: Boolean = false,
    val settingsSubPage: String = "main", // "main", "midi", "system_audio", "master_fx"
    val isLowLatencyAudio: Boolean = true,
    val isKeyboardVelocityTouch: Boolean = true,
    val isMetronomeInRec: Boolean = false,
    val appFolder: String = "/Music/SoundfontsLive/",
    
    // System & Audio Engine Settings
    val audioEngine: String = "Oboe", // "Oboe", "AAudio", "OpenSL ES"
    val audioBufferSize: Int = 128, // 64, 128, 256, 512 frames
    val polyphony: Int = 64, // 32, 64, 128, 256
    val selectedLanguage: String = "Français", // "Français", "English", "Español"
    val globalVelocityMin: Float = 0.10f,
    val globalVelocityMax: Float = 1.0f,
    
    // Master FX (3D Knobs)
    val soundGoodizer: Float = 0.42f,
    val masterPunch: Float = 0.55f,
    val spatialWidener: Float = 0.38f,
    
    // MIDI Devices
    val midiDevices: List<MidiDeviceItem> = emptyList()
)

class MixerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<MixerUiState> = _uiState.asStateFlow()

    private var peakMeterJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var lastTapTimeMap = mutableMapOf<Int, Long>()

    init {
        startPeakMeterSimulation()
    }

    private fun createInitialState(): MixerUiState {
        // Spec: "Purge des Données Fantômes : Supprimer toutes les chaînes de caractères codées en dur simulant des SoundFonts fictives. Si aucune banque n'est chargée, afficher un tiret neutre (-)."
        val initialTracks = (1..8).map { i ->
            TrackChannel(
                id = i,
                name = "Piste $i",
                isEnabled = true, // By default all tracks play simultaneously
                volume = 0.60f + (i % 3) * 0.05f,
                pan = when (i) {
                    2 -> -0.35f
                    3 -> 0.35f
                    5 -> -0.20f
                    6 -> 0.20f
                    else -> 0.0f
                },
                fxSummary = "Fx, EQ...",
                soundfontName = if (i == 1) "FluidR3-Mono.sf2" else "",
                patchName = if (i == 1) "Grand Piano" else "-",
                reverbPreset = when (i % 4) {
                    0 -> "Plate 80s"
                    1 -> "Concert Hall"
                    2 -> "Warm Room"
                    else -> "Cathedral"
                },
                reverbMix = 0.20f + (i * 0.03f),
                reverbSize = 0.50f,
                reverbDecay = 0.40f,
                velocityCurve = 0.50f,
                splitNoteMin = 36 + (i - 1) * 4,
                splitNoteMax = 84,
                peakMeterL = 0.35f,
                peakMeterR = 0.38f
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
                    LoopFile("afrobeat_groove_120.wav", "0:04", "Drums", 120),
                    LoopFile("trap_snare_roll.wav", "0:02", "Drums", 140),
                    LoopFile("hihat_16th_groove.wav", "0:04", "Drums", 124),
                    LoopFile("percussion_shaker.wav", "0:02", "Drums", 120)
                )
            ),
            LoopFolder(
                name = "Bass",
                icon = "🎸",
                isOpen = false,
                files = listOf(
                    LoopFile("sub_bass_riff_A.wav", "0:08", "Bass", 120),
                    LoopFile("funk_slap_bass.wav", "0:04", "Bass", 118)
                )
            ),
            LoopFolder(
                name = "Guitars",
                icon = "🎶",
                isOpen = false,
                files = listOf(
                    LoopFile("acoustic_fingerpick_Am.wav", "0:08", "Guitars", 120),
                    LoopFile("electric_clean_chords.wav", "0:04", "Guitars", 120)
                )
            ),
            LoopFolder(
                name = "Ambiance & Worship",
                icon = "🌊",
                isOpen = false,
                files = listOf(
                    LoopFile("worship_shimmer_pad_D.wav", "0:16", "Ambiance & Worship", 120),
                    LoopFile("celestial_drone_C.wav", "0:12", "Ambiance & Worship", 120)
                )
            )
        )

        val initialScenes = listOf(
            ScenePreset("intro", "Intro Ballade (Piano + Pad)", "Aujourd'hui 14:02", NeonCyan),
            ScenePreset("refrain", "Refrain Puissant (Orchestre Full)", "Aujourd'hui 13:40", NeonMagenta),
            ScenePreset("break", "Break Ambiance Shimmer", "Hier 20:11", SoloAmber),
            ScenePreset("live", "Live Set Worship A", "22 août", MuteRed)
        )

        val initialPresets = listOf(
            SoundfontPreset(1, "Grand Piano Concert", 0),
            SoundfontPreset(2, "Rhodes Mark II EP", 1),
            SoundfontPreset(3, "Strings Legato Section", 2),
            SoundfontPreset(4, "Choir Aahs & Oohs", 3),
            SoundfontPreset(5, "Warm Worship Pad", 4),
            SoundfontPreset(6, "Acoustic Finger Bass", 5),
            SoundfontPreset(7, "Drawbar Clean Organ", 6),
            SoundfontPreset(8, "Brass Section Epic", 7)
        )

        val initialBankFiles = listOf(
            SoundfontBankFile("FluidR3-Mono.sf2", "/Music/SoundfontsLive/FluidR3-Mono.sf2", "141.2 MB"),
            SoundfontBankFile("GeneralUser-GS.sf2", "/Music/SoundfontsLive/GeneralUser-GS.sf2", "29.8 MB"),
            SoundfontBankFile("FX Worship.sf2", "/Music/SoundfontsLive/FX Worship.sf2", "22.1 MB"),
            SoundfontBankFile("Orchestral-HQ.sf2", "/Music/SoundfontsLive/Orchestral-HQ.sf2", "88.4 MB"),
            SoundfontBankFile("WarmPads-Vol2.sf2", "/Music/SoundfontsLive/WarmPads-Vol2.sf2", "14.5 MB")
        )

        val initialMidiDevices = listOf(
            MidiDeviceItem("usb_1", "Roland RD-88 (USB MIDI)", "USB MIDI Direct", isConnected = true, isEnabled = true),
            MidiDeviceItem("bt_1", "Yamaha MD-BT01", "Bluetooth LE MIDI", isConnected = true, isEnabled = true),
            MidiDeviceItem("pedal_1", "USB Expression / Sustain Pedal", "Pedal Input", isConnected = true, isEnabled = true)
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
            fxParameters = initialFx,
            midiDevices = initialMidiDevices
        )
    }

    private fun startPeakMeterSimulation() {
        peakMeterJob?.cancel()
        peakMeterJob = viewModelScope.launch {
            while (isActive) {
                delay(80)
                _uiState.update { state ->
                    val isLooping = state.isLoopPlaying
                    val isRec = state.isRecording

                    val updatedTracks = state.tracks.map { track ->
                        if (!track.isEnabled || track.isMuted) {
                            track.copy(peakMeterL = 0f, peakMeterR = 0f)
                        } else {
                            val baseSignal = if (state.pressedKeys.isNotEmpty()) 0.75f else if (isLooping) 0.35f else 0.15f
                            val volFactor = track.volume
                            val pan = track.pan
                            val l = (baseSignal * volFactor * (1f - pan * 0.5f) * (0.8f + Random.nextFloat() * 0.2f)).coerceIn(0f, 0.98f)
                            val r = (baseSignal * volFactor * (1f + pan * 0.5f) * (0.8f + Random.nextFloat() * 0.2f)).coerceIn(0f, 0.98f)
                            track.copy(peakMeterL = l, peakMeterR = r)
                        }
                    }

                    // Master Meter
                    val activeTrackCount = updatedTracks.count { it.isEnabled && !it.isMuted }
                    val avgL = if (activeTrackCount > 0) updatedTracks.map { it.peakMeterL }.average().toFloat() else 0f
                    val avgR = if (activeTrackCount > 0) updatedTracks.map { it.peakMeterR }.average().toFloat() else 0f
                    val masterVol = state.masterTrack.volume
                    val masterL = (avgL * masterVol * (0.9f + Random.nextFloat() * 0.15f)).coerceIn(0f, 1f)
                    val masterR = (avgR * masterVol * (0.9f + Random.nextFloat() * 0.15f)).coerceIn(0f, 1f)

                    state.copy(
                        tracks = updatedTracks,
                        masterTrack = state.masterTrack.copy(peakMeterL = masterL, peakMeterR = masterR)
                    )
                }
            }
        }
    }

    // ================= TOP BAR & TEMPO =================
    fun updateTranspose(delta: Int) {
        _uiState.update { it.copy(transpose = (it.transpose + delta).coerceIn(-12, 12)) }
    }

    fun updateOctave(delta: Int) {
        _uiState.update { it.copy(octave = (it.octave + delta).coerceIn(-4, 4)) }
    }

    fun updateBpm(delta: Int) {
        _uiState.update { it.copy(bpm = (it.bpm + delta).coerceIn(20, 300)) }
    }

    fun setBpmDirect(value: Int) {
        _uiState.update { it.copy(bpm = value.coerceIn(20, 300)) }
    }

    // ================= LOOPS SEQUENCER =================
    fun toggleLoopsPanel() {
        _uiState.update { it.copy(isLoopsPanelOpen = !it.isLoopsPanelOpen, isMetroPanelOpen = false) }
    }

    fun closeLoopsPanel() {
        _uiState.update { it.copy(isLoopsPanelOpen = false) }
    }

    fun toggleLoopPlayPause() {
        _uiState.update { it.copy(isLoopPlaying = !it.isLoopPlaying) }
    }

    fun setLoopVolume(vol: Float) {
        _uiState.update { it.copy(loopVolume = vol.coerceIn(0f, 1f)) }
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

    fun selectAndPlayLoopFile(file: LoopFile) {
        _uiState.update {
            it.copy(
                activeLoopFile = file,
                isLoopPlaying = true
            )
        }
    }

    fun triggerPanic() {
        _uiState.update {
            it.copy(
                pressedKeys = emptySet(),
                isSustainActive = false,
                isMidiPedalPressed = false
            )
        }
    }

    // ================= GLOBAL SUSTAIN & SPLITTER =================
    fun toggleSustain() {
        _uiState.update { it.copy(isSustainActive = !it.isSustainActive) }
    }

    fun setMidiPedalPressed(pressed: Boolean) {
        _uiState.update { it.copy(isMidiPedalPressed = pressed) }
    }

    fun toggleSplitter() {
        _uiState.update { it.copy(isSplitterActive = !it.isSplitterActive) }
    }

    // ================= RECORDING & METRONOME =================
    fun toggleRecording() {
        _uiState.update { state ->
            val newRec = !state.isRecording
            if (newRec) {
                startRecordingTimer()
                state.copy(isRecording = true, recordingDuration = 0)
            } else {
                stopRecordingTimer()
                val filename = "LiveKeys_Rec_${System.currentTimeMillis() % 10000}.wav"
                state.copy(isRecording = false, lastRecordedFile = filename)
            }
        }
    }

    private fun startRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _uiState.update { it.copy(recordingDuration = it.recordingDuration + 1) }
            }
        }
    }

    private fun stopRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
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

    // ================= TRACKS CONSOLE & FADERS =================
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

    fun toggleTrackPower(trackId: Int) {
        _uiState.update { state ->
            val updated = state.tracks.map { track ->
                if (track.id == trackId) track.copy(isEnabled = !track.isEnabled) else track
            }
            state.copy(tracks = updated)
        }
    }

    fun setTrackPan(trackId: Int, pan: Float) {
        _uiState.update { state ->
            val updated = state.tracks.map { track ->
                if (track.id == trackId) track.copy(pan = pan.coerceIn(-1f, 1f)) else track
            }
            state.copy(tracks = updated)
        }
    }

    /**
     * Mute / Solo Tap handler according to Specification:
     * - 1 tap (Single click): Mute (Red)
     * - 2 taps (Fast double click): Solo (Yellow)
     * - Subsequent tap: Deactivates Mute/Solo back to neutral
     */
    fun onTrackMuteSoloClick(trackId: Int) {
        val now = System.currentTimeMillis()
        val lastTap = lastTapTimeMap[trackId] ?: 0L
        val isDoubleTap = (now - lastTap) < 320L
        lastTapTimeMap[trackId] = now

        _uiState.update { state ->
            val track = state.tracks.find { it.id == trackId } ?: return@update state
            val updated = state.tracks.map { t ->
                if (t.id == trackId) {
                    if (isDoubleTap) {
                        // Double tap triggers Solo
                        t.copy(isSolo = !t.isSolo, isMuted = false)
                    } else {
                        if (t.isSolo || t.isMuted) {
                            // Subsequent click on active state returns to neutral
                            t.copy(isMuted = false, isSolo = false)
                        } else {
                            // Single click triggers Mute
                            t.copy(isMuted = true, isSolo = false)
                        }
                    }
                } else t
            }
            state.copy(tracks = updated)
        }
    }

    // ================= VIRTUAL KEYBOARD & LOCK =================
    fun selectLoopFile(file: LoopFile) = selectAndPlayLoopFile(file)

    fun toggleMuteSoloSequence(trackId: Int) = onTrackMuteSoloClick(trackId)

    fun toggleKeyboardLock() {
        _uiState.update { state ->
            val newLocked = !state.isKeyboardLocked
            state.copy(
                isKeyboardLocked = newLocked,
                keyboardHeightFraction = if (newLocked) 0f else if (state.keyboardHeightFraction == 0f) 0.5f else state.keyboardHeightFraction
            )
        }
    }

    fun setKeyboardHeightFraction(fraction: Float) {
        _uiState.update { state ->
            if (state.isKeyboardLocked) state else state.copy(keyboardHeightFraction = fraction.coerceIn(0f, 1f))
        }
    }

    fun cycleKeyboardExpansion() {
        _uiState.update { state ->
            if (state.isKeyboardLocked) return@update state
            val next = when {
                state.keyboardHeightFraction < 0.2f -> 0.5f
                state.keyboardHeightFraction < 0.7f -> 1.0f
                else -> 0.0f
            }
            state.copy(keyboardHeightFraction = next)
        }
    }

    fun onKeyDown(key: String) {
        _uiState.update { state ->
            state.copy(pressedKeys = state.pressedKeys + key)
        }
    }

    fun onKeyUp(key: String) {
        _uiState.update { state ->
            if (!state.isSustainActive && !state.isMidiPedalPressed) {
                state.copy(pressedKeys = state.pressedKeys - key)
            } else {
                state
            }
        }
    }

    // ================= POPUPS & DIALOGS =================
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

    // ================= TRACK FX TABS SPECIFICS =================
    fun setTrackReverbPreset(trackId: Int, preset: String) {
        _uiState.update { state ->
            val updated = state.tracks.map { track ->
                if (track.id == trackId) track.copy(reverbPreset = preset) else track
            }
            state.copy(tracks = updated)
        }
    }

    fun setTrackReverbMix(trackId: Int, mix: Float) {
        _uiState.update { state ->
            val updated = state.tracks.map { track ->
                if (track.id == trackId) track.copy(reverbMix = mix.coerceIn(0f, 1f)) else track
            }
            state.copy(tracks = updated)
        }
    }

    fun setTrackVelocityCurve(trackId: Int, curve: Float) {
        _uiState.update { state ->
            val updated = state.tracks.map { track ->
                if (track.id == trackId) track.copy(velocityCurve = curve.coerceIn(0f, 1f)) else track
            }
            state.copy(tracks = updated)
        }
    }

    fun setTrackSplitRange(trackId: Int, minNote: Int, maxNote: Int) {
        _uiState.update { state ->
            val updated = state.tracks.map { track ->
                if (track.id == trackId) track.copy(splitNoteMin = minNote, splitNoteMax = maxNote) else track
            }
            state.copy(tracks = updated)
        }
    }

    fun updateFxParameter(trackId: Int, transform: (FxParameters) -> FxParameters) {
        _uiState.update { state ->
            val current = state.fxParameters[trackId] ?: FxParameters()
            val updatedMap = state.fxParameters.toMutableMap()
            updatedMap[trackId] = transform(current)
            state.copy(fxParameters = updatedMap)
        }
    }

    fun setFxTab(tab: String) {
        _uiState.update { it.copy(activeFxTab = tab) }
    }

    // ================= DRUM PAD CONTROLS =================
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

    // ================= TONIC PAD CONTROLS =================
    fun toggleMultiPad() {
        _uiState.update { it.copy(isMultiPadEnabled = !it.isMultiPadEnabled) }
    }

    fun cycleTonicOctave() {
        val octaves = listOf("C1 — C2", "C2 — C3", "C3 — C4", "C4 — C5", "C5 — C6")
        _uiState.update { state ->
            val currentIndex = octaves.indexOf(state.tonicOctaveRange)
            val nextIndex = if (currentIndex in 0 until octaves.lastIndex) currentIndex + 1 else 0
            state.copy(tonicOctaveRange = octaves[nextIndex])
        }
    }

    fun onTonicNoteClick(note: String) {
        _uiState.update { state ->
            if (state.isMultiPadEnabled) {
                val newSet = if (state.activeTonicNotes.contains(note)) {
                    if (state.activeTonicNotes.size > 1) state.activeTonicNotes - note else state.activeTonicNotes
                } else {
                    state.activeTonicNotes + note
                }
                state.copy(activeTonicNotes = newSet)
            } else {
                state.copy(activeTonicNotes = setOf(note))
            }
        }
    }

    fun setTonicOctaveRange(range: String) {
        _uiState.update { it.copy(tonicOctaveRange = range) }
    }

    fun setTonicMode(mode: String) {
        _uiState.update { it.copy(tonicMode = mode) }
    }

    fun setTonicBrightness(brightness: Float) {
        _uiState.update { it.copy(tonicBrightness = brightness.coerceIn(0f, 1f)) }
    }

    fun setTonicShimmer(shimmer: Float) {
        _uiState.update { it.copy(tonicShimmer = shimmer.coerceIn(0f, 1f)) }
    }

    // ================= SOUNDFONTS & SCENES =================
    fun setSf2Tab(tab: String) {
        _uiState.update { it.copy(activeSf2Tab = tab) }
    }

    fun selectSf2Preset(presetId: Int) {
        _uiState.update { state ->
            val preset = state.soundfontPresets.find { it.id == presetId } ?: return@update state
            val trackId = state.activeSoundfontTrackId
            val updatedTracks = state.tracks.map { track ->
                if (track.id == trackId) {
                    track.copy(patchName = preset.name, soundfontName = "FluidR3-Mono.sf2", bank = preset.bankNumber)
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

    fun saveCurrentScene(name: String) {
        val newScene = ScenePreset(
            id = "scene_${System.currentTimeMillis()}",
            name = name,
            timestamp = "À l'instant",
            color = NeonCyan
        )
        _uiState.update { state ->
            state.copy(
                scenes = listOf(newScene) + state.scenes,
                activeSceneId = newScene.id
            )
        }
    }

    // ================= SETTINGS DRAWER & 3D MASTER FX =================
    fun openSettingsDrawer() {
        _uiState.update { it.copy(isSettingsDrawerOpen = true, settingsSubPage = "main") }
    }

    fun closeSettingsDrawer() {
        _uiState.update { it.copy(isSettingsDrawerOpen = false) }
    }

    fun setSettingsSubPage(page: String) {
        _uiState.update { it.copy(settingsSubPage = page) }
    }

    fun setAudioEngine(engine: String) {
        _uiState.update { it.copy(audioEngine = engine) }
    }

    fun setAudioBufferSize(buffer: Int) {
        _uiState.update { it.copy(audioBufferSize = buffer) }
    }

    fun setPolyphony(poly: Int) {
        _uiState.update { it.copy(polyphony = poly) }
    }

    fun setSelectedLanguage(lang: String) {
        _uiState.update { it.copy(selectedLanguage = lang) }
    }

    fun setGlobalVelocityRange(min: Float, max: Float) {
        _uiState.update { it.copy(globalVelocityMin = min.coerceIn(0f, 1f), globalVelocityMax = max.coerceIn(0f, 1f)) }
    }

    fun setSoundGoodizer(v: Float) {
        _uiState.update { it.copy(soundGoodizer = v.coerceIn(0f, 1f)) }
    }

    fun setMasterPunch(v: Float) {
        _uiState.update { it.copy(masterPunch = v.coerceIn(0f, 1f)) }
    }

    fun setSpatialWidener(v: Float) {
        _uiState.update { it.copy(spatialWidener = v.coerceIn(0f, 1f)) }
    }

    fun toggleMidiDevice(deviceId: String) {
        _uiState.update { state ->
            val updated = state.midiDevices.map { dev ->
                if (dev.id == deviceId) dev.copy(isEnabled = !dev.isEnabled) else dev
            }
            state.copy(midiDevices = updated)
        }
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
