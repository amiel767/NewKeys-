package com.example.viewmodel

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class MixerUiState(
    val currentTheme: AppTheme = AppTheme.CYBER_NEON,
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
    
    // Real Storage / File Manager State
    val storageBaseDirPath: String = "",
    val realSoundfonts: List<StorageItem> = emptyList(),
    val realLoopFiles: List<StorageItem> = emptyList(),
    val realStyleFiles: List<StorageItem> = emptyList(),
    val realRecordingFiles: List<StorageItem> = emptyList(),
    val currentLoopDirPath: String = "",
    val currentLoopDirItems: List<StorageItem> = emptyList(),
    val isScanningStorage: Boolean = false,
    
    // Loops Module
    val isLoopsPanelOpen: Boolean = false,
    val isLoopPlaying: Boolean = false,
    val loopVolume: Float = 0.75f,
    val selectedBeatCount: Int = 4,
    val loopFolders: List<LoopFolder> = emptyList(),
    val activeLoopFile: LoopFile? = null,
    val activeStorageLoopItem: StorageItem? = null,
    
    // Style / Arranger Module (.sty engine)
    val isStylePlaying: Boolean = false,
    val isSyncStartActive: Boolean = false,
    val activeStyleSection: String = "MAIN A", // "INTRO", "MAIN A", "MAIN B", "FILL IN", "ENDING"
    val selectedStyleName: String = "-",
    val selectedStyleFile: StorageItem? = null,
    val styleVolume: Float = 0.75f,
    val styleActiveTab: String = "files", // "files", "effects", "sf2"
    val styleFxLow: Float = 0.5f,
    val styleFxMid: Float = 0.5f,
    val styleFxHigh: Float = 0.5f,
    val styleReverbMix: Float = 0.20f,
    
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
        peakMeterL = 0.0f,
        peakMeterR = 0.0f
    ),
    
    // Virtual Keyboard & Multi-Touch
    val isKeyboardLocked: Boolean = true, // Default retracted / locked until user toggles piano icon
    val keyboardHeightFraction: Float = 0f, // 0f = collapsed, 0.5f = half, 1f = full
    val pressedKeys: Set<String> = emptySet(),
    val keyboardKeyScale: Float = 1.0f, // Pinch to zoom keys (0.6f .. 1.8f)
    val keyboardScrollOffset: Float = 0f,
    
    // Popups
    val activePopup: ActivePopup = ActivePopup.NONE,
    val isDrumPadPinned: Boolean = false,
    val isTonicPadPinned: Boolean = false,
    val activeEffectTrackId: Int = 1,
    val activeSoundfontTrackId: Int = 1,
    val activeSoundfontSource: String = "track", // "track", "drum", "pad", "style"
    
    // Drum Pad
    val drumPads: List<DrumPadItem> = emptyList(),
    val drumVolume: Float = 0.75f,
    val drumReverb: Float = 0.24f,
    val drumActiveTab: String = "pads", // "pads", "bank", "files"
    val drumSubView: String = "main", // "main", "sf2_picker"
    val editingDrumPadId: Int? = null,
    val selectedDrumSampleForAssign: StorageItem? = null,
    val isAssignPadDialogOpen: Boolean = false,
    
    // Tonic Pad
    val isMultiPadEnabled: Boolean = false, // Multi is OFF by default per user request
    val activeTonicNotes: Set<String> = emptySet(),
    val tonicOctaveRange: String = "C3 — C4",
    val tonicMode: String = "Chromatique",
    val tonicBrightness: Float = 0.70f,
    val tonicShimmer: Float = 0.15f,
    val tonicSubView: String = "main", // "main", "sf2_picker"
    
    // FX Rack
    val activeFxTab: String = "eq", // "eq", "reverb", "velocity", "splitter", "comp", "delay"
    val fxParameters: Map<Int, FxParameters> = emptyMap(),
    
    // Soundfonts & Scenes
    val activeSf2Tab: String = "bank", // "bank", "other"
    val soundfontPresets: List<SoundfontPreset> = emptyList(),
    val soundfontBankFiles: List<SoundfontBankFile> = emptyList(),
    val selectedSf2PresetId: Int = 0,
    val scenes: List<ScenePreset> = emptyList(),
    val activeSceneId: String = "intro",
    
    // Settings Drawer (Material Expressive AOSP style)
    val isSettingsDrawerOpen: Boolean = false,
    val settingsSubPage: String = "main", // "main", "themes", "midi", "system_audio", "master_fx"
    val isLowLatencyAudio: Boolean = true,
    val isKeyboardVelocityTouch: Boolean = true,
    val isMetronomeInRec: Boolean = false,
    val appFolder: String = "/LiveKeys",
    
    // System & Audio Engine Settings
    val audioEngine: String = "Oboe (C++)", // "Oboe (C++)", "AAudio", "OpenSL ES"
    val audioBufferSize: Int = 128, // 64, 128, 256, 512 frames
    val polyphony: Int = 128, // 32, 64, 128, 256
    val selectedLanguage: String = "Français", // "Français", "English", "Español"
    val globalVelocityMin: Float = 0.10f,
    val globalVelocityMax: Float = 1.0f,
    
    // Master FX 3D (renamed to Effect 3D)
    val soundGoodizer: Float = 0.42f,
    val masterPunch: Float = 0.55f,
    val spatialWidener: Float = 0.38f,
    
    // Connected MIDI Devices (Pedal removed per user request)
    val midiDevices: List<MidiDeviceItem> = emptyList()
) {
    val soundfontFiles: List<StorageItem> get() = realSoundfonts
    val loopAudioFiles: List<StorageItem> get() = realLoopFiles
    val styleFiles: List<StorageItem> get() = realStyleFiles
}

class MixerViewModel(application: Application) : AndroidViewModel(application) {
    val fileManager = FileManager(application.applicationContext)

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<MixerUiState> = _uiState.asStateFlow()

    private var peakMeterJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var styleTransitionJob: Job? = null
    private var lastTapTimeMap = mutableMapOf<Int, Long>()

    init {
        startPeakMeterSimulation()
        refreshStorageFiles()
    }

    private fun createInitialState(): MixerUiState {
        // Spec: "Purge des Données Fantômes : si getSoundFontFiles() est vide, le nom affiché sur la piste doit être un simple tiret '-'."
        val initialTracks = (1..8).map { i ->
            TrackChannel(
                id = i,
                name = "Piste $i",
                isEnabled = true,
                volume = 0.65f,
                pan = 0.0f,
                fxSummary = "Fx, EQ...",
                soundfontName = "",
                patchName = "-",
                reverbPreset = "Concert Hall",
                reverbMix = 0.20f,
                reverbSize = 0.50f,
                reverbDecay = 0.40f,
                velocityCurve = 0.50f,
                splitNoteMin = 36,
                splitNoteMax = 84,
                peakMeterL = 0.0f,
                peakMeterR = 0.0f
            )
        }

        val initialDrumPads = (1..8).map { padIdx ->
            val defaultLabel = when (padIdx) {
                1 -> "Kick"
                2 -> "Snare"
                3 -> "HH Close"
                4 -> "HH Open"
                5 -> "Clap"
                6 -> "Tom Low"
                7 -> "Tom Hi"
                else -> "Crash"
            }
            val defaultNote = when (padIdx) {
                1 -> "C1"
                2 -> "D1"
                3 -> "F#1"
                4 -> "A#1"
                5 -> "D#1"
                6 -> "F1"
                7 -> "A1"
                else -> "C#2"
            }
            DrumPadItem(
                id = padIdx,
                label = defaultLabel,
                soundType = DrumSoundType.SAMPLE,
                sampleFileName = "-",
                sf2Note = defaultNote,
                sf2NoteOctave = 1,
                sf2NoteKey = "C"
            )
        }

        val initialScenes = listOf(
            ScenePreset("intro", "Intro Worship Ballade", "Aujourd'hui 14:02", NeonCyan),
            ScenePreset("refrain", "Refrain Puissant Orchestral", "Aujourd'hui 13:40", NeonMagenta),
            ScenePreset("break", "Break Shimmer Ambiance", "Hier 20:11", SoloAmber),
            ScenePreset("live", "Live Set Principal A", "22 août", MuteRed)
        )

        // Only show connected USB/Bluetooth MIDI controllers (no pedal row per user instruction)
        val initialMidiDevices = listOf(
            MidiDeviceItem("usb_1", "Roland RD-88 (USB MIDI)", "USB MIDI Direct", isConnected = true, isEnabled = true),
            MidiDeviceItem("bt_1", "Yamaha MD-BT01", "Bluetooth LE MIDI", isConnected = true, isEnabled = true),
            MidiDeviceItem("usb_2", "Korg nanoKEY Studio", "USB MIDI Controller", isConnected = true, isEnabled = false)
        )

        val initialFx = (0..8).associateWith { FxParameters() }

        return MixerUiState(
            storageBaseDirPath = fileManager.baseDir.absolutePath,
            currentLoopDirPath = fileManager.loopsDir.absolutePath,
            tracks = initialTracks,
            drumPads = initialDrumPads,
            scenes = initialScenes,
            fxParameters = initialFx,
            midiDevices = initialMidiDevices
        )
    }

    /**
     * Scans real storage folders on Dispatchers.IO and updates UI state dynamically
     */
    fun refreshStorageFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isScanningStorage = true) }
            try {
                val soundfonts = fileManager.getSoundFontFiles()
                val loops = fileManager.getLoopFiles()
                val loopFolders = fileManager.getLoopFolderTree()
                val styles = fileManager.getStyleFiles()
                val recordings = fileManager.getRecordingFiles()
                val loopDirItems = fileManager.listItemsInDirectory(fileManager.loopsDir.absolutePath)

                // SoundFont Bank Files
                val bankFiles = soundfonts.map {
                    SoundfontBankFile(it.name, it.path, it.formattedSize)
                }

                _uiState.update { state ->
                    // If tracks have no soundfont assigned and we have soundfonts, update only if user loads one
                    state.copy(
                        realSoundfonts = soundfonts,
                        realLoopFiles = loops,
                        loopFolders = loopFolders,
                        realStyleFiles = styles,
                        realRecordingFiles = recordings,
                        currentLoopDirItems = loopDirItems,
                        soundfontBankFiles = bankFiles,
                        isScanningStorage = false
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isScanningStorage = false) }
            }
        }
    }

    /**
     * Navigates to a specific directory in the Material Loops File Explorer
     */
    fun navigateToLoopDirectory(dirPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = fileManager.listItemsInDirectory(dirPath)
            _uiState.update {
                it.copy(
                    currentLoopDirPath = dirPath,
                    currentLoopDirItems = items
                )
            }
        }
    }

    fun navigateUpLoopDirectory() {
        val current = _uiState.value.currentLoopDirPath
        val baseLoops = fileManager.loopsDir.absolutePath
        if (current.startsWith(baseLoops) && current.length > baseLoops.length) {
            val parent = java.io.File(current).parentFile?.absolutePath ?: baseLoops
            navigateToLoopDirectory(parent)
        }
    }

    // ================= THEME SELECTION =================
    fun setAppTheme(theme: AppTheme) {
        _uiState.update { it.copy(currentTheme = theme) }
    }

    // ================= PEAK METERS SIMULATION =================
    private fun startPeakMeterSimulation() {
        peakMeterJob?.cancel()
        peakMeterJob = viewModelScope.launch {
            while (isActive) {
                delay(80)
                _uiState.update { state ->
                    val isLooping = state.isLoopPlaying
                    val isStyling = state.isStylePlaying
                    val hasPressedKeys = state.pressedKeys.isNotEmpty()
                    val hasActiveTonic = state.activeTonicNotes.isNotEmpty()

                    val isAudioActive = hasPressedKeys || isLooping || isStyling || hasActiveTonic

                    val updatedTracks = state.tracks.map { track ->
                        if (!track.isEnabled || track.isMuted || !isAudioActive) {
                            track.copy(peakMeterL = 0f, peakMeterR = 0f)
                        } else {
                            val baseSignal = when {
                                hasPressedKeys -> 0.72f
                                hasActiveTonic -> 0.50f
                                isStyling -> 0.60f
                                isLooping -> 0.45f
                                else -> 0.0f
                            }
                            val volFactor = track.volume
                            val pan = track.pan
                            val l = (baseSignal * volFactor * (1f - pan * 0.5f) * (0.85f + Random.nextFloat() * 0.15f)).coerceIn(0f, 0.98f)
                            val r = (baseSignal * volFactor * (1f + pan * 0.5f) * (0.85f + Random.nextFloat() * 0.15f)).coerceIn(0f, 0.98f)
                            track.copy(peakMeterL = l, peakMeterR = r)
                        }
                    }

                    // Master Meter
                    val activeTrackCount = updatedTracks.count { it.isEnabled && !it.isMuted && (it.peakMeterL > 0f || it.peakMeterR > 0f) }
                    val masterL = if (activeTrackCount > 0 && isAudioActive) {
                        val avgL = updatedTracks.map { it.peakMeterL }.average().toFloat()
                        (avgL * state.masterTrack.volume * (0.9f + Random.nextFloat() * 0.12f)).coerceIn(0f, 1f)
                    } else 0f

                    val masterR = if (activeTrackCount > 0 && isAudioActive) {
                        val avgR = updatedTracks.map { it.peakMeterR }.average().toFloat()
                        (avgR * state.masterTrack.volume * (0.9f + Random.nextFloat() * 0.12f)).coerceIn(0f, 1f)
                    } else 0f

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

    // ================= LOOPS SEQUENCER / MATERIAL EXPLORER =================
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

    /**
     * Touching a loop file starts it; touching it again stops it.
     */
    fun selectAndToggleLoopFile(file: LoopFile) {
        _uiState.update { state ->
            if (state.activeLoopFile == file && state.isLoopPlaying) {
                state.copy(isLoopPlaying = false)
            } else {
                state.copy(
                    activeLoopFile = file,
                    isLoopPlaying = true
                )
            }
        }
    }

    /**
     * Touching a StorageItem in the Material Explorer starts it; touching again stops it.
     */
    fun selectAndToggleStorageLoop(item: StorageItem) {
        _uiState.update { state ->
            if (state.activeStorageLoopItem?.path == item.path && state.isLoopPlaying) {
                state.copy(isLoopPlaying = false)
            } else {
                state.copy(
                    activeStorageLoopItem = item,
                    activeLoopFile = LoopFile(item.name, item.formattedSize, "Loops", 120),
                    isLoopPlaying = true
                )
            }
        }
    }

    fun triggerPanic() {
        _uiState.update {
            it.copy(
                pressedKeys = emptySet(),
                isSustainActive = false,
                isMidiPedalPressed = false,
                isLoopPlaying = false,
                isStylePlaying = false,
                activeTonicNotes = emptySet()
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
     * Mute / Solo Tap handler:
     * - 1 tap: Mute (Red)
     * - 2 fast taps: Solo (Yellow)
     * - Subsequent tap: Neutral (Active Green)
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
                        t.copy(isSolo = !t.isSolo, isMuted = false)
                    } else {
                        if (t.isSolo || t.isMuted) {
                            t.copy(isMuted = false, isSolo = false)
                        } else {
                            t.copy(isMuted = true, isSolo = false)
                        }
                    }
                } else t
            }
            state.copy(tracks = updated)
        }
    }

    // ================= VIRTUAL KEYBOARD & MULTI-TOUCH =================
    fun toggleKeyboardLock() {
        _uiState.update { state ->
            val newLocked = !state.isKeyboardLocked
            state.copy(
                isKeyboardLocked = newLocked,
                keyboardHeightFraction = if (newLocked) 0f else 0.55f
            )
        }
    }

    fun setKeyboardHeightFraction(fraction: Float) {
        _uiState.update { state ->
            if (state.isKeyboardLocked) state else state.copy(keyboardHeightFraction = fraction.coerceIn(0f, 1f))
        }
    }

    fun setKeyboardKeyScale(scale: Float) {
        _uiState.update { state ->
            state.copy(keyboardKeyScale = scale.coerceIn(0.55f, 1.85f))
        }
    }

    fun setKeyboardScrollOffset(offset: Float) {
        _uiState.update { state ->
            state.copy(keyboardScrollOffset = offset)
        }
    }

    fun onKeyDown(key: String) {
        if (_uiState.value.isKeyboardLocked || _uiState.value.keyboardHeightFraction <= 0f) return

        // If Sync Start is armed, automatically trigger style playback upon touching a piano key
        if (_uiState.value.isSyncStartActive && !_uiState.value.isStylePlaying) {
            val startSec = if (_uiState.value.activeStyleSection == "INTRO") "INTRO" else "MAIN A"
            triggerStyleSection(startSec)
        }

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

    // ================= ARRANGER / STYLE (.STY) CONTROLS =================
    fun toggleStylePlay() {
        styleTransitionJob?.cancel()
        _uiState.update { it.copy(isStylePlaying = !it.isStylePlaying) }
    }

    fun toggleSyncStart() {
        _uiState.update { it.copy(isSyncStartActive = !it.isSyncStartActive) }
    }

    /**
     * Automatic Style Section Sequencer:
     * - INTRO: Plays intro, then automatically transitions to MAIN A when done.
     * - FILL / FILL IN: Plays 1-bar fill, then automatically returns to previous MAIN section.
     * - MAIN A / MAIN B: Loops continuously until another section is triggered.
     * - ENDING / END: Plays ending, then automatically stops style playback.
     */
    fun triggerStyleSection(section: String) {
        styleTransitionJob?.cancel()
        val currentBpm = _uiState.value.bpm.coerceIn(40, 260)
        val oneBarMillis = (4 * 60_000L) / currentBpm

        val normSection = when (section.uppercase()) {
            "INTRO" -> "INTRO"
            "FILL", "FILL IN" -> "FILL IN"
            "ENDING", "END" -> "ENDING"
            "MAIN B" -> "MAIN B"
            else -> "MAIN A"
        }

        when (normSection) {
            "INTRO" -> {
                _uiState.update {
                    it.copy(
                        activeStyleSection = "INTRO",
                        isStylePlaying = true,
                        isSyncStartActive = false
                    )
                }
                // Automatic transition: After INTRO (2 bars), advance to MAIN A
                styleTransitionJob = viewModelScope.launch {
                    val introDuration = (2 * oneBarMillis).coerceIn(2000L, 8000L)
                    delay(introDuration)
                    _uiState.update {
                        if (it.isStylePlaying && it.activeStyleSection == "INTRO") {
                            it.copy(activeStyleSection = "MAIN A")
                        } else it
                    }
                }
            }
            "FILL IN" -> {
                val previousMain = if (_uiState.value.activeStyleSection == "MAIN B") "MAIN B" else "MAIN A"
                _uiState.update {
                    it.copy(
                        activeStyleSection = "FILL IN",
                        isStylePlaying = true,
                        isSyncStartActive = false
                    )
                }
                // Automatic transition: After 1 bar FILL IN, return to previous MAIN
                styleTransitionJob = viewModelScope.launch {
                    val fillDuration = oneBarMillis.coerceIn(1000L, 4000L)
                    delay(fillDuration)
                    _uiState.update {
                        if (it.isStylePlaying && it.activeStyleSection == "FILL IN") {
                            it.copy(activeStyleSection = previousMain)
                        } else it
                    }
                }
            }
            "ENDING" -> {
                _uiState.update {
                    it.copy(
                        activeStyleSection = "ENDING",
                        isStylePlaying = true,
                        isSyncStartActive = false
                    )
                }
                // Automatic transition: After ENDING (2 bars), stop all playback
                styleTransitionJob = viewModelScope.launch {
                    val endDuration = (2 * oneBarMillis).coerceIn(2000L, 8000L)
                    delay(endDuration)
                    _uiState.update {
                        if (it.activeStyleSection == "ENDING") {
                            it.copy(isStylePlaying = false, activeStyleSection = "MAIN A")
                        } else it
                    }
                }
            }
            "MAIN A" -> {
                _uiState.update {
                    it.copy(
                        activeStyleSection = "MAIN A",
                        isStylePlaying = true,
                        isSyncStartActive = false
                    )
                }
            }
            "MAIN B" -> {
                _uiState.update {
                    it.copy(
                        activeStyleSection = "MAIN B",
                        isStylePlaying = true,
                        isSyncStartActive = false
                    )
                }
            }
        }
    }

    fun selectStyleFile(file: StorageItem) {
        _uiState.update {
            it.copy(
                selectedStyleName = file.name.substringBeforeLast("."),
                selectedStyleFile = file,
                isStylePlaying = true
            )
        }
    }

    fun setStyleVolume(vol: Float) {
        _uiState.update { it.copy(styleVolume = vol.coerceIn(0f, 1f)) }
    }

    fun setStyleActiveTab(tab: String) {
        _uiState.update { it.copy(styleActiveTab = tab) }
    }

    fun setStyleFxLow(v: Float) = _uiState.update { it.copy(styleFxLow = v.coerceIn(0f, 1f)) }
    fun setStyleFxMid(v: Float) = _uiState.update { it.copy(styleFxMid = v.coerceIn(0f, 1f)) }
    fun setStyleFxHigh(v: Float) = _uiState.update { it.copy(styleFxHigh = v.coerceIn(0f, 1f)) }
    fun setStyleReverbMix(v: Float) = _uiState.update { it.copy(styleReverbMix = v.coerceIn(0f, 1f)) }

    // ================= POPUPS & DIALOGS =================
    fun openPopup(popup: ActivePopup) {
        _uiState.update { it.copy(activePopup = popup, isLoopsPanelOpen = false, isMetroPanelOpen = false) }
    }

    fun closePopup() {
        _uiState.update {
            it.copy(
                activePopup = ActivePopup.NONE,
                editingDrumPadId = null,
                drumSubView = "main",
                tonicSubView = "main",
                isAssignPadDialogOpen = false
            )
        }
    }

    fun closeDrumPad() {
        _uiState.update {
            it.copy(
                isDrumPadPinned = false,
                activePopup = if (it.activePopup == ActivePopup.DRUM_PAD) ActivePopup.NONE else it.activePopup,
                editingDrumPadId = null,
                drumSubView = "main"
            )
        }
    }

    fun closeTonicPad() {
        _uiState.update {
            it.copy(
                isTonicPadPinned = false,
                activePopup = if (it.activePopup == ActivePopup.TONIC_PAD) ActivePopup.NONE else it.activePopup,
                tonicSubView = "main"
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
    /**
     * Reverb preset toggle: Touch once to activate, touch again to deactivate and return to "Custom"
     */
    fun toggleTrackReverbPreset(trackId: Int, preset: String) {
        _uiState.update { state ->
            val updated = state.tracks.map { track ->
                if (track.id == trackId) {
                    val nextPreset = if (track.reverbPreset == preset) "Custom" else preset
                    track.copy(reverbPreset = nextPreset)
                } else track
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

    fun setDrumSubView(subView: String) {
        _uiState.update { it.copy(drumSubView = subView) }
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

    fun openAssignPadDialog(sample: StorageItem) {
        _uiState.update {
            it.copy(
                selectedDrumSampleForAssign = sample,
                isAssignPadDialogOpen = true
            )
        }
    }

    fun closeAssignPadDialog() {
        _uiState.update {
            it.copy(
                selectedDrumSampleForAssign = null,
                isAssignPadDialogOpen = false
            )
        }
    }

    fun assignSampleToPad(padId: Int, sample: StorageItem) {
        _uiState.update { state ->
            val updated = state.drumPads.map { pad ->
                if (pad.id == padId) {
                    pad.copy(
                        soundType = DrumSoundType.SAMPLE,
                        sampleFileName = sample.name,
                        label = sample.name.substringBeforeLast(".").take(8)
                    )
                } else pad
            }
            state.copy(
                drumPads = updated,
                isAssignPadDialogOpen = false,
                selectedDrumSampleForAssign = null
            )
        }
    }

    fun assignSf2NoteToPad(padId: Int, note: String, octave: Int, key: String) {
        _uiState.update { state ->
            val updated = state.drumPads.map { pad ->
                if (pad.id == padId) {
                    pad.copy(
                        soundType = DrumSoundType.SF2_NOTE,
                        sf2Note = "$key$octave",
                        sf2NoteOctave = octave,
                        sf2NoteKey = key,
                        label = "$key$octave"
                    )
                } else pad
            }
            state.copy(drumPads = updated)
        }
    }

    // ================= TONIC PAD CONTROLS =================
    fun toggleMultiPad() {
        _uiState.update { it.copy(isMultiPadEnabled = !it.isMultiPadEnabled) }
    }

    fun setTonicSubView(subView: String) {
        _uiState.update { it.copy(tonicSubView = subView) }
    }

    fun cycleTonicOctave() {
        val octaves = listOf("C1 — C2", "C2 — C3", "C3 — C4", "C4 — C5", "C5 — C6")
        _uiState.update { state ->
            val currentIndex = octaves.indexOf(state.tonicOctaveRange)
            val nextIndex = if (currentIndex in 0 until octaves.lastIndex) currentIndex + 1 else 0
            state.copy(tonicOctaveRange = octaves[nextIndex])
        }
    }

    /**
     * In Pad, touch a cell to activate, touch again to deactivate.
     */
    fun onTonicNoteClick(note: String) {
        _uiState.update { state ->
            if (state.isMultiPadEnabled) {
                val newSet = if (state.activeTonicNotes.contains(note)) {
                    state.activeTonicNotes - note
                } else {
                    state.activeTonicNotes + note
                }
                state.copy(activeTonicNotes = newSet)
            } else {
                val newSet = if (state.activeTonicNotes.contains(note)) {
                    emptySet()
                } else {
                    setOf(note)
                }
                state.copy(activeTonicNotes = newSet)
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

    fun selectRealSoundfont(storageItem: StorageItem) {
        _uiState.update { state ->
            val trackId = state.activeSoundfontTrackId
            val updatedTracks = state.tracks.map { track ->
                if (track.id == trackId) {
                    track.copy(
                        soundfontName = storageItem.name,
                        patchName = storageItem.name.substringBeforeLast(".")
                    )
                } else track
            }
            state.copy(tracks = updatedTracks)
        }
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

    // ================= SETTINGS DRAWER & EFFECT 3D =================
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

    // Convenience and alias methods
    fun selectLoopFile(file: LoopFile) {
        selectAndToggleLoopFile(file)
    }

    fun selectLoopFile(item: StorageItem) {
        selectAndToggleStorageLoop(item)
    }

    fun toggleMuteSoloSequence(trackId: Int) {
        onTrackMuteSoloClick(trackId)
    }

    fun cycleKeyboardExpansion() {
        _uiState.update { state ->
            val nextFraction = when {
                state.keyboardHeightFraction <= 0f -> 0.55f
                state.keyboardHeightFraction in 0.01f..0.65f -> 1.0f
                else -> 0f
            }
            state.copy(
                keyboardHeightFraction = nextFraction,
                isKeyboardLocked = nextFraction == 0f
            )
        }
    }

    fun setStyleTab(tab: String) {
        setStyleActiveTab(tab)
    }

    fun selectStyleSf2Source(source: String) {
        _uiState.update { it.copy(activeSoundfontSource = source) }
    }

    fun selectStyleSf2Source(source: StorageItem) {
        _uiState.update { it.copy(activeSoundfontSource = source.name) }
    }

    fun assignDrumSample(padId: Int, sampleName: String) {
        _uiState.update { state ->
            val updated = state.drumPads.map { pad ->
                if (pad.id == padId) {
                    pad.copy(
                        soundType = DrumSoundType.SAMPLE,
                        sampleFileName = sampleName,
                        label = sampleName.substringBeforeLast(".").take(8)
                    )
                } else pad
            }
            state.copy(drumPads = updated)
        }
    }

    fun assignDrumSf2Note(padId: Int, key: String, octave: Int) {
        assignSf2NoteToPad(padId, "$key$octave", octave, key)
    }

    fun onTonicOctaveMinus() {
        val octaves = listOf("C1 — C2", "C2 — C3", "C3 — C4", "C4 — C5", "C5 — C6")
        _uiState.update { state ->
            val idx = octaves.indexOf(state.tonicOctaveRange)
            val nextIdx = if (idx > 0) idx - 1 else 0
            state.copy(tonicOctaveRange = octaves[nextIdx])
        }
    }

    fun onTonicOctavePlus() {
        val octaves = listOf("C1 — C2", "C2 — C3", "C3 — C4", "C4 — C5", "C5 — C6")
        _uiState.update { state ->
            val idx = octaves.indexOf(state.tonicOctaveRange)
            val nextIdx = if (idx in 0 until octaves.lastIndex) idx + 1 else octaves.lastIndex
            state.copy(tonicOctaveRange = octaves[nextIdx])
        }
    }

    fun setTrackReverbPreset(trackId: Int, preset: String) {
        toggleTrackReverbPreset(trackId, preset)
    }
}
