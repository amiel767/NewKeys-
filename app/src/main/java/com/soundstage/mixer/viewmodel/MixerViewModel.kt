package com.soundstage.mixer.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soundstage.mixer.audio.NativeAudioBridge
import com.soundstage.mixer.model.*
import com.soundstage.mixer.ui.theme.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val selectedRootKey: String = "C",
    
    // Real Storage / File Manager State
    val storageBaseDirPath: String = "",
    val realSoundfonts: List<StorageItem> = emptyList(),
    val realLoopFiles: List<StorageItem> = emptyList(),
    val realDrumPadFiles: List<StorageItem> = emptyList(),
    val realDrumPadLoopFiles: List<StorageItem> = emptyList(),
    val realStyleFiles: List<StorageItem> = emptyList(),
    val realRecordingFiles: List<StorageItem> = emptyList(),
    val realMidiFiles: List<StorageItem> = emptyList(),
    val currentLoopDirPath: String = "",
    val currentLoopDirItems: List<StorageItem> = emptyList(),
    val currentMidiDirPath: String = "",
    val currentMidiDirItems: List<StorageItem> = emptyList(),
    val isScanningStorage: Boolean = false,
    
    // Loops Module
    val isLoopsPanelOpen: Boolean = false,
    val isLoopPlaying: Boolean = false,
    val loopVolume: Float = 0.75f,
    val selectedBeatCount: Int = 0,
    val loopFolders: List<LoopFolder> = emptyList(),
    val activeLoopFile: LoopFile? = null,
    val lastSelectedLoopFile: LoopFile? = null,
    val activeStorageLoopItem: StorageItem? = null,
    val editingLoopFile: LoopFile? = null,
    val loopEditorStartMs: Int = 0,
    val loopEditorEndMs: Int = 0,
    val loopEditorBeats: Int = 0,
    val loopEditorStartStep: Int = 1,
    val loopEditorEndStep: Int = 16,
    
    // MIDI Player Module (.mid) - Replaces .sty per user instructions
    val isMidiPlaying: Boolean = false,
    val isMidiPanelOpen: Boolean = false,
    val selectedMidiName: String = "-",
    val selectedMidiFile: StorageItem? = null,
    val midiVolume: Float = 0.80f,
    val midiFolders: List<LoopFolder> = emptyList(),
    
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
    val isKeyboardLocked: Boolean = false,
    val keyboardHeightFraction: Float = 0f,
    val isKeyboardLayerExpanded: Boolean = false,
    val pressedKeys: Set<String> = emptySet(),
    val keyboardKeyScale: Float = 1.0f,
    val keyboardScrollOffset: Float = 0f,
    val pitchBend: Float = 0.0f,
    
    // Fullscreen Pages Navigation
    val currentPage: AppScreenPage = AppScreenPage.MIXER,
    
    // Popups
    val activePopup: ActivePopup = ActivePopup.NONE,
    val isDrumPadPinned: Boolean = false,
    val drumPadWasPinned: Boolean = false,
    val drumPadOffsetX: Float = 0f,
    val drumPadOffsetY: Float = 0f,
    val drumPadSizeDp: Float = 440f,
    val isTonicPadPinned: Boolean = false,
    val tonicPadWasPinned: Boolean = false,
    val tonicPadOffsetX: Float = 0f,
    val tonicPadOffsetY: Float = 0f,
    val tonicPadSizeDp: Float = 440f,
    val activeEffectTrackId: Int = 1,
    val activeSoundfontSlotId: Int = 0,
    val audioSlots: List<AudioSlot> = emptyList(),
    
    // Drum Pad
    val drumPads: List<DrumPadItem> = emptyList(),
    val drumVolume: Float = 0.75f,
    val drumReverb: Float = 0.24f,
    val drumActiveTab: String = "pad",
    val drumSubView: String = "main",
    val isDrumPadMixMode: Boolean = false,
    val drumFeelSwing: Int = 54,
    val activeDrumKitName: String = "Drum Kit 1",
    val activeTonicPadName: String = "Analog Pad",
    val isDrumPadMiniBrowserOpen: Boolean = false,
    val isDrumPadLoopsOpen: Boolean = false,
    val isDrumPadFileExplorerOpen: Boolean = false,
    val drumPadAssignTargetPadId: Int? = null,
    val isDrumLoopArmed: Boolean = false,
    val isDrumLoopRecording: Boolean = false,
    val drumLoopBars: Int = 2,
    val drumTimeSignature: String = "4/4",
    val isDrumLoopRendering: Boolean = false,
    val editingDrumPadId: Int? = null,
    val selectedDrumSampleForAssign: StorageItem? = null,
    val isAssignPadDialogOpen: Boolean = false,
    
    // Tonic Pad
    val isMultiPadEnabled: Boolean = false,
    val activeTonicNotes: Set<String> = emptySet(),
    val tonicOctaveRange: String = "C3 — C4",
    val tonicMode: String = "Chromatique",
    val tonicBrightness: Float = 0.70f,
    val tonicShimmer: Float = 0.15f,
    val tonicSubView: String = "main",
    
    // FX Rack
    val activeFxTab: String = "eq",
    val fxParameters: Map<Int, FxParameters> = emptyMap(),
    
    // Soundfonts & Scenes
    val activeSf2Tab: String = "bank",
    val soundfontBankFiles: List<SoundfontBankFile> = emptyList(),
    val scenes: List<ScenePreset> = emptyList(),
    val activeSceneId: String = "intro",
    
    // Settings Drawer (Material Expressive AOSP style)
    val isSettingsDrawerOpen: Boolean = false,
    val settingsSubPage: String = "main",
    val isLowLatencyAudio: Boolean = true,
    val isKeyboardVelocityTouch: Boolean = true,
    val isMetronomeInRec: Boolean = false,
    val useFlats: Boolean = false,
    val appFolder: String = "/LiveKeys",
    
    // System & Audio Engine Settings
    val audioEngine: String = "Oboe (C++)",
    val audioBufferSize: Int = 256,
    val polyphony: Int = 128,
    val selectedLanguage: String = "English",
    val selectedAppLanguage: AppLanguage = AppLanguage.ENGLISH,
    val globalVelocityMin: Float = 0.10f,
    val globalVelocityMax: Float = 1.0f,
    
    // Master FX SoundGoodizer (FL Studio Engine)
    val soundGoodizer: Float = 0.45f,
    val soundGoodizerMode: SoundGoodizerMode = SoundGoodizerMode.A,
    val masterPunch: Float = 0.55f,
    val spatialWidener: Float = 0.38f,
    
    // Connected MIDI Devices
    val midiDevices: List<MidiDeviceItem> = emptyList(),
    
    // Screen & Scale Settings
    val keepScreenOn: Boolean = true,
    val selectedScaleMode: String = "Majeur",
    
    // Live Notes & Chords
    val notesText: String = "",

    // Snapshots / Sub-Scenes (Section 2)
    val isSnapshotArmMode: Boolean = false,
    val activeSnapshotSlot: String? = null,
    val snapshots: Map<String, SubSceneSnapshot> = emptyMap(),
    val customLibreTracks: List<TrackChannel>? = null,
    val snapshotTransitionProgress: Float = 1.0f,
    val snapshotCustomNames: Map<String, String> = emptyMap(),

    // In-App File Browser Persistence (Section 4)
    val lastLoopsPath: String = "",
    val lastDrumPadPath: String = "",
    val showStoragePermissionDialog: Boolean = false,

    // StepDrum Sequencer State (Module 1)
    val stepDrumState: StepDrumUiState = StepDrumUiState()
) {
    val soundfontFiles: List<StorageItem> get() = realSoundfonts
    val loopAudioFiles: List<StorageItem> get() = realLoopFiles
    val drumPadAudioFiles: List<StorageItem> get() = realDrumPadFiles
    val drumPadLoopAudioFiles: List<StorageItem> get() = realDrumPadLoopFiles
    val styleFiles: List<StorageItem> get() = realStyleFiles
    val midiFiles: List<StorageItem> get() = realMidiFiles
}

class MixerViewModel(application: Application) : AndroidViewModel(application) {
    val fileManager = FileManager(application.applicationContext)
    val audioEngine = AudioEngine(application.applicationContext)
    private val appStatePersistence = AppStatePersistence(application.applicationContext)
    private val soundFontLoadMutex = Mutex()

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<MixerUiState> = _uiState.asStateFlow()

    private val drumPadLooper = com.soundstage.mixer.audio.DrumPadLooperEngine { pad, _ ->
        com.soundstage.mixer.audio.DrumPadSampleProvider.getOrGeneratePadPcm(application.applicationContext, pad)
    }

    private var peakMeterJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var snapshotTransitionJob: Job? = null
    private var refreshStorageJob: Job? = null
    private var lastTapTimeMap = mutableMapOf<Int, Long>()

    init {
        startPeakMeterSimulation()

        // Start Native FluidSynth engine asynchronously to prevent freezing UI thread on startup
        viewModelScope.launch(Dispatchers.Default) {
            NativeAudioBridge.safeStartEngine()
            audioEngine.setBufferSize(_uiState.value.audioBufferSize)
            audioEngine.setPolyphony(_uiState.value.polyphony)
            _uiState.value.tracks.forEachIndexed { index, track ->
                NativeAudioBridge.safeSetTrackVolume(index, track.volume)
                NativeAudioBridge.safeSetTrackPan(index, track.pan)
            }
        }

        // Detect and register USB MIDI Hardware devices
        val initialMidiDevs = audioEngine.getConnectedUsbMidiDevices()
        _uiState.update { it.copy(midiDevices = initialMidiDevs) }
        audioEngine.onDeviceListChanged = { updatedList ->
            _uiState.update { it.copy(midiDevices = updatedList) }
        }

        // Listen to USB MIDI hardware events (updates UI key states safely without recursion)
        audioEngine.onMidiNoteOnListener = { noteName, _ ->
            _uiState.update { it.copy(pressedKeys = it.pressedKeys + noteName) }
        }
        audioEngine.onMidiNoteOffListener = { noteName ->
            _uiState.update { it.copy(pressedKeys = it.pressedKeys - noteName) }
        }
        audioEngine.onMidiPitchBendListener = { bendValue ->
            _uiState.update { it.copy(pitchBend = bendValue) }
        }
        audioEngine.onMidiSustainListener = { isPressed ->
            _uiState.update { it.copy(isMidiPedalPressed = isPressed) }
        }
        audioEngine.onMidiCcListener = { channel, cc, value ->
            when (cc) {
                7 -> { // CC#7 Volume (Global Master Volume)
                    val vol = (value / 127f).coerceIn(0f, 1f)
                    setTrackVolume(0, vol)
                }
                11 -> { // CC#11 Expression
                    val vol = (value / 127f).coerceIn(0f, 1f)
                    val targetTrackId = if (channel in 0..7) (channel + 1) else 1
                    setTrackVolume(targetTrackId, vol)
                }
                10 -> { // CC#10 Pan
                    val pan = ((value - 64) / 63f).coerceIn(-1f, 1f)
                    val targetTrackId = if (channel in 0..7) (channel + 1) else 1
                    setTrackPan(targetTrackId, pan)
                }
            }
        }

        // Connect multi-channel layer performance routing for USB MIDI
        audioEngine.activeLayerChannelsProvider = { midiNote ->
            getActivePerformanceChannels(midiNote)
        }
        audioEngine.trackVelocityCurveProvider = { ch ->
            _uiState.value.tracks.getOrNull(ch)?.velocityCurve ?: 0.5f
        }

        // Restore persisted state from previous session
        viewModelScope.launch(Dispatchers.IO) {
            try {
                fileManager.ensureDirectoriesExist()
            } catch (e: Exception) {
                Log.w("MixerViewModel", "ensureDirectoriesExist warning: ${e.message}")
            }
            try {
                restoreSavedAppState()
            } catch (e: Exception) {
                Log.w("MixerViewModel", "restoreSavedAppState warning: ${e.message}")
            }
            try {
                refreshStorageFiles()
            } catch (e: Exception) {
                Log.w("MixerViewModel", "refreshStorageFiles warning: ${e.message}")
            }
            
            // Non-intrusive permission check on startup (Android 11+)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    if (!android.os.Environment.isExternalStorageManager()) {
                        _uiState.update { it.copy(showStoragePermissionDialog = true) }
                    }
                }
            } catch (e: Exception) {
                Log.w("MixerViewModel", "Storage manager check skipped: ${e.message}")
            }
        }
    }

    private fun restoreSavedAppState() {
        try {
            val saved = appStatePersistence.loadAppState()
            if (saved != null) {
                val drumPreloadList = mutableListOf<String>()

                _uiState.update { state ->
                    val restoredTheme = saved.themeName?.let { name ->
                        try { AppTheme.valueOf(name) } catch (_: Exception) { null }
                    } ?: state.currentTheme

                    val restoredMode = saved.soundGoodizerMode?.let { name ->
                        try { SoundGoodizerMode.valueOf(name) } catch (_: Exception) { null }
                    } ?: state.soundGoodizerMode

                    val restoredTracks = if (saved.tracks.isNotEmpty()) {
                        state.tracks.map { currentTrack ->
                            val savedT = saved.tracks.find { it.id == currentTrack.id }
                            if (savedT != null) {
                                currentTrack.copy(
                                    isEnabled = savedT.isEnabled,
                                    volume = savedT.volume,
                                    pan = savedT.pan,
                                    soundfontName = savedT.soundfontName,
                                    patchName = savedT.patchName,
                                    bank = savedT.bank,
                                    program = savedT.program,
                                    reverbPreset = savedT.reverbPreset,
                                    reverbMix = savedT.reverbMix,
                                    velocityCurve = savedT.velocityCurve,
                                    splitNoteMin = savedT.splitNoteMin,
                                    splitNoteMax = savedT.splitNoteMax
                                )
                            } else currentTrack
                        }
                    } else state.tracks

                    val restoredFx = if (saved.fxParameters.isNotEmpty()) {
                        state.fxParameters + saved.fxParameters
                    } else state.fxParameters

                    val restoredDrums = if (saved.drumPads.isNotEmpty()) {
                        state.drumPads.map { currentPad ->
                            val savedD = saved.drumPads.find { it.id == currentPad.id }
                            if (savedD != null) {
                                val style = try { DrumPadStyle.valueOf(savedD.styleName) } catch (_: Exception) { currentPad.colorStyle }
                                val soundType = try { DrumSoundType.valueOf(savedD.soundType) } catch (_: Exception) { currentPad.soundType }
                                val filePath = if (savedD.sampleFilePath.isNotEmpty()) savedD.sampleFilePath else {
                                    val f = File(getApplication<Application>().filesDir, "LiveKeys/DrumPad/${savedD.sampleFileName}")
                                    if (f.exists()) f.absolutePath else ""
                                }
                                if (filePath.isNotEmpty()) {
                                    drumPreloadList.add(filePath)
                                }
                                currentPad.copy(
                                    label = savedD.label,
                                    soundType = soundType,
                                    sampleFileName = savedD.sampleFileName,
                                    sampleFilePath = filePath,
                                    sf2Note = savedD.sf2Note,
                                    colorStyle = style
                                )
                            } else currentPad
                        }
                    } else state.drumPads

                    val restoredSlots = state.audioSlots.map { slot ->
                        if (slot.slotId in 0..7) {
                            val savedT = saved.tracks.find { it.id == (slot.slotId + 1) }
                            if (savedT != null) {
                                slot.copy(
                                    patchName = savedT.patchName,
                                    bank = savedT.bank,
                                    preset = savedT.program,
                                    volume = savedT.volume,
                                    pan = savedT.pan
                                )
                            } else slot
                        } else slot
                    }

                    state.copy(
                        currentTheme = restoredTheme,
                        bpm = saved.bpm ?: state.bpm,
                        transpose = saved.transpose ?: state.transpose,
                        octave = saved.octave ?: state.octave,
                        activeSceneId = saved.activeSceneId ?: state.activeSceneId,
                        soundGoodizer = saved.soundGoodizerAmount ?: state.soundGoodizer,
                        soundGoodizerMode = restoredMode,
                        masterPunch = saved.masterPunch ?: state.masterPunch,
                        spatialWidener = saved.spatialWidener ?: state.spatialWidener,
                        masterTrack = state.masterTrack.copy(volume = saved.masterVolume ?: state.masterTrack.volume),
                        tracks = restoredTracks,
                        audioSlots = restoredSlots,
                        drumPads = restoredDrums,
                        fxParameters = restoredFx,
                        snapshots = saved.snapshots.ifEmpty { state.snapshots },
                        activeSnapshotSlot = saved.activeSnapshotSlot,
                        tonicBrightness = saved.tonicBrightness ?: state.tonicBrightness,
                        tonicShimmer = saved.tonicShimmer ?: state.tonicShimmer,
                        drumReverb = saved.drumReverb ?: state.drumReverb,
                        keepScreenOn = saved.keepScreenOn ?: state.keepScreenOn,
                        selectedScaleMode = saved.selectedScaleMode ?: state.selectedScaleMode
                    )
                }

                // Preload drums safely outside state update
                drumPreloadList.forEach { path ->
                    try { audioEngine.preloadDrumSample(path) } catch (_: Exception) {}
                }

                // Apply restored parameters to native DSP
                val effectiveMasterVol = saved.masterVolume ?: _uiState.value.masterTrack.volume
                audioEngine.masterVolume = effectiveMasterVol
                NativeAudioBridge.safeSetMasterVolume(effectiveMasterVol)

                // Restore Master FX (EQ, Reverb, Delay, Chorus, Compressor, SoundGoodizer)
                val masterFx = saved.fxParameters[0]
                if (masterFx != null) {
                    val lowDb = (masterFx.eqLow - 0.5f) * 24.0f
                    val midDb = (masterFx.eqMid - 0.5f) * 24.0f
                    val highDb = (masterFx.eqHigh - 0.5f) * 24.0f
                    audioEngine.setMasterEq(lowDb, midDb, highDb)
                    audioEngine.setMasterReverb(
                        enabled = masterFx.isReverbEnabled,
                        size = masterFx.reverbSize,
                        decay = masterFx.reverbDecay,
                        damp = masterFx.reverbDamp,
                        mix = masterFx.reverbMix
                    )
                    val isDelayActive = masterFx.isDelayEnabled && masterFx.delayMix > 0.005f
                    audioEngine.setMasterDelay(
                        enabled = isDelayActive,
                        timeSec = 0.05f + masterFx.delayTime * 0.95f,
                        feedback = if (isDelayActive) masterFx.delayFeedback else 0f,
                        mix = if (isDelayActive) masterFx.delayMix else 0f,
                        pingPong = masterFx.delayPingPong > 0.5f
                    )
                    val isChorusActive = masterFx.isChorusEnabled && masterFx.chorusMix > 0.005f
                    audioEngine.setMasterChorus(
                        enabled = isChorusActive,
                        rateHz = 0.2f + masterFx.chorusRate * 4.8f,
                        depthMs = 1.0f + masterFx.chorusDepth * 14.0f,
                        mix = if (isChorusActive) masterFx.chorusMix else 0f
                    )
                    val compThreshold = -36f + masterFx.compThresh * 28f
                    val compRatioVal = 1.5f + masterFx.compRatio * 8.5f
                    val makeupGain = if (masterFx.isCompEnabled) {
                        val reductionEst = (-compThreshold) * (1f - 1f / compRatioVal)
                        (reductionEst * 0.75f).coerceIn(0f, 15f)
                    } else 0f
                    audioEngine.setMasterCompressor(
                        enabled = masterFx.isCompEnabled,
                        thresholdDb = compThreshold,
                        ratio = compRatioVal,
                        attackMs = 1.0f + masterFx.compAttack * 40f,
                        releaseMs = 20.0f + masterFx.compRelease * 350f,
                        makeupGainDb = makeupGain
                    )
                    val modeStr = when (masterFx.sgMode) {
                        0 -> "A"
                        1 -> "B"
                        2 -> "C"
                        3 -> "D"
                        else -> "A"
                    }
                    audioEngine.soundGoodizerMode = modeStr
                    audioEngine.isSoundGoodizerEnabled = masterFx.isSgEnabled
                    audioEngine.soundGoodizerAmount = masterFx.sgAmount
                    NativeAudioBridge.safeSetSoundGoodizer(masterFx.isSgEnabled, masterFx.sgMode, masterFx.sgAmount)
                } else {
                    val restoredMode = saved.soundGoodizerMode?.let { name ->
                        try { SoundGoodizerMode.valueOf(name) } catch (_: Exception) { null }
                    } ?: _uiState.value.soundGoodizerMode
                    val sgAmt = saved.soundGoodizerAmount ?: _uiState.value.soundGoodizer
                    audioEngine.soundGoodizerMode = restoredMode.name
                    audioEngine.soundGoodizerAmount = sgAmt
                    NativeAudioBridge.safeSetSoundGoodizer(audioEngine.isSoundGoodizerEnabled, restoredMode.ordinal, sgAmt)
                }

                saved.tracks.forEach { t ->
                    val ch = (t.id - 1).coerceIn(0, 7)
                    NativeAudioBridge.safeSetTrackVolume(ch, t.volume)
                    NativeAudioBridge.safeSetTrackPan(ch, t.pan)
                    val trackFx = saved.fxParameters[t.id]
                    val reverbMix = if (trackFx != null && trackFx.isReverbEnabled) trackFx.reverbMix else t.reverbMix
                    audioEngine.setChannelReverb(ch, reverbMix)
                    val chorusMix = if (trackFx != null && trackFx.isChorusEnabled) trackFx.chorusMix else 0f
                    audioEngine.setChannelChorus(ch, chorusMix)
                }

                var hasLoadedAnySlot = false
                if (saved.audioSlots.isNotEmpty()) {
                    saved.audioSlots.forEach { savedSlot ->
                        val savedTrack = saved.tracks.find { it.id == (savedSlot.slotId + 1) }
                        val candidatePath = when {
                            !savedSlot.soundFontPath.isNullOrEmpty() && File(savedSlot.soundFontPath).exists() -> savedSlot.soundFontPath
                            savedTrack != null && savedTrack.soundfontName.isNotEmpty() && File(fileManager.soundfontsDir, savedTrack.soundfontName).exists() -> File(fileManager.soundfontsDir, savedTrack.soundfontName).absolutePath
                            !savedSlot.soundFontPath.isNullOrEmpty() && File(fileManager.soundfontsDir, File(savedSlot.soundFontPath).name).exists() -> File(fileManager.soundfontsDir, File(savedSlot.soundFontPath).name).absolutePath
                            !savedSlot.patchName.isNullOrEmpty() && File(fileManager.soundfontsDir, savedSlot.patchName).exists() -> File(fileManager.soundfontsDir, savedSlot.patchName).absolutePath
                            else -> null
                        }
                        if (candidatePath != null) {
                            loadSoundFontForSlot(savedSlot.slotId, candidatePath, savedSlot.bank, savedSlot.preset, savedSlot.patchName, saveAfterLoad = false)
                            hasLoadedAnySlot = true
                        }
                    }
                }

                // If audioSlots were empty or missing paths, check saved tracks for soundfont names
                if (!hasLoadedAnySlot && saved.tracks.isNotEmpty()) {
                    saved.tracks.forEachIndexed { index, track ->
                        if (track.soundfontName.isNotEmpty()) {
                            val candidate = File(fileManager.soundfontsDir, track.soundfontName)
                            if (candidate.exists()) {
                                loadSoundFontForSlot(index, candidate.absolutePath, bank = track.bank, preset = track.program, patchName = track.patchName, saveAfterLoad = false)
                                hasLoadedAnySlot = true
                            }
                        }
                    }
                }

                // If nothing was loaded at all and Slot 0 is empty, load default SoundFont ONLY on Slot 0
                if (!hasLoadedAnySlot) {
                    val defaultSf = File(fileManager.soundfontsDir, "VintageDreamsWaves-v2.sf2")
                    val fallbackSf = File(getApplication<Application>().filesDir, "LiveKeys/SoundFonts/VintageDreamsWaves-v2.sf2")
                    val sfToLoad = if (defaultSf.exists()) defaultSf else if (fallbackSf.exists()) fallbackSf else null
                    if (sfToLoad != null) {
                        val slot0Path = _uiState.value.audioSlots.getOrNull(0)?.soundFontPath
                        if (slot0Path.isNullOrEmpty() || !File(slot0Path).exists()) {
                            loadSoundFontForSlot(0, sfToLoad.absolutePath, bank = 0, preset = 0)
                        }
                    }
                }
            } else {
                // First run / no saved state: load default soundfont on Slot 0 (Track 1) ONLY
                val defaultSf = File(fileManager.soundfontsDir, "VintageDreamsWaves-v2.sf2")
                val fallbackSf = File(getApplication<Application>().filesDir, "LiveKeys/SoundFonts/VintageDreamsWaves-v2.sf2")
                val sfToLoad = if (defaultSf.exists()) defaultSf else if (fallbackSf.exists()) fallbackSf else null
                if (sfToLoad != null) {
                    loadSoundFontForSlot(0, sfToLoad.absolutePath, bank = 0, preset = 0)
                }
            }
        } catch (e: Exception) {
            Log.e("MixerViewModel", "Error restoring saved app state: ${e.message}", e)
        }
    }

    private var persistJob: Job? = null

    private fun persistCurrentStateDebounced() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            persistCurrentState()
        }
    }

    private fun persistCurrentState() {
        val state = _uiState.value
        appStatePersistence.saveAppState(
            currentTheme = state.currentTheme,
            bpm = state.bpm,
            transpose = state.transpose,
            octave = state.octave,
            activeSceneId = state.activeSceneId,
            soundGoodizerAmount = state.soundGoodizer,
            soundGoodizerMode = state.soundGoodizerMode,
            masterPunch = state.masterPunch,
            spatialWidener = state.spatialWidener,
            masterVolume = state.masterTrack.volume,
            tracks = state.tracks,
            audioSlots = state.audioSlots,
            drumPads = state.drumPads,
            fxParameters = state.fxParameters,
            activeSf2TrackId = state.activeSoundfontSlotId,
            lastActivity = "mixer",
            tonicBrightness = state.tonicBrightness,
            tonicShimmer = state.tonicShimmer,
            drumReverb = state.drumReverb,
            keepScreenOn = state.keepScreenOn,
            selectedScaleMode = state.selectedScaleMode,
            snapshots = state.snapshots,
            activeSnapshotSlot = state.activeSnapshotSlot
        )
    }

    override fun onCleared() {
        super.onCleared()
        NativeAudioBridge.safeStopEngine()
        audioEngine.release()
    }

    private fun createInitialState(): MixerUiState {
        val initialAudioSlots = List(10) { idx ->
            AudioSlot(
                slotId = idx,
                midiChannel = idx
            )
        }

        val initialTracks = (1..8).map { i ->
            TrackChannel(
                id = i,
                name = "Track $i",
                isEnabled = i <= 4, // Tracks 1-4 enabled, 5-8 disabled by default
                volume = 0.65f,
                pan = 0.0f,
                fxSummary = "Fx, EQ...",
                reverbPreset = "Concert Hall",
                reverbMix = 0.20f,
                reverbSize = 0.50f,
                reverbDecay = 0.40f,
                velocityCurve = 0.50f,
                splitNoteMin = 24, // C1
                splitNoteMax = 108, // C7
                peakMeterL = 0.0f,
                peakMeterR = 0.0f
            )
        }

        val defaultPadStylesSideA = listOf(
            DrumPadStyle.DUBSTEP_CORAL, DrumPadStyle.DUBSTEP_CORAL, DrumPadStyle.DUBSTEP_PURPLE,
            DrumPadStyle.DUBSTEP_CORAL, DrumPadStyle.DUBSTEP_CORAL, DrumPadStyle.DUBSTEP_BLUE,
            DrumPadStyle.DUBSTEP_BLUE, DrumPadStyle.DUBSTEP_PURPLE, DrumPadStyle.DUBSTEP_GREEN,
            DrumPadStyle.DUBSTEP_YELLOW, DrumPadStyle.DUBSTEP_CORAL, DrumPadStyle.DUBSTEP_BLUE
        )
        val defaultPadStylesSideB = listOf(
            DrumPadStyle.DUBSTEP_BLUE, DrumPadStyle.DUBSTEP_PURPLE, DrumPadStyle.DUBSTEP_CORAL,
            DrumPadStyle.DUBSTEP_GREEN, DrumPadStyle.DUBSTEP_YELLOW, DrumPadStyle.DUBSTEP_CORAL,
            DrumPadStyle.DUBSTEP_CORAL, DrumPadStyle.DUBSTEP_BLUE, DrumPadStyle.DUBSTEP_PURPLE,
            DrumPadStyle.DUBSTEP_PURPLE, DrumPadStyle.DUBSTEP_GREEN, DrumPadStyle.DUBSTEP_YELLOW
        )

        val svgDefaultNames = listOf("Kick 1", "Clap 1", "Wood 1", "Tumb 1", "Tom 1", "Tom 2", "Tom 3", "Sub Kick")
        val svgDefaultVols = listOf(1.00f, 0.84f, 0.71f, 0.88f, 0.78f, 0.74f, 0.65f, 0.92f)
        val svgDefaultColors = listOf(
            androidx.compose.ui.graphics.Color(0xFFE11D48),
            androidx.compose.ui.graphics.Color(0xFFD97706),
            androidx.compose.ui.graphics.Color(0xFF881337),
            androidx.compose.ui.graphics.Color(0xFF581C87),
            androidx.compose.ui.graphics.Color(0xFFBE123C),
            androidx.compose.ui.graphics.Color(0xFF9F1239),
            androidx.compose.ui.graphics.Color(0xFFF43F5E),
            androidx.compose.ui.graphics.Color(0xFF4C1D95)
        )

        val initialDrumPads = (1..24).map { padIdx ->
            val style = if (padIdx <= 12) {
                defaultPadStylesSideA.getOrElse(padIdx - 1) { DrumPadStyle.DUBSTEP_CORAL }
            } else {
                defaultPadStylesSideB.getOrElse(padIdx - 13) { DrumPadStyle.DUBSTEP_BLUE }
            }
            val label = if (padIdx <= 8) svgDefaultNames[padIdx - 1] else "Pad $padIdx"
            val vol = if (padIdx <= 8) svgDefaultVols[padIdx - 1] else 0.80f
            val color = if (padIdx <= 8) svgDefaultColors[padIdx - 1] else null
            DrumPadItem(
                id = padIdx,
                label = label,
                soundType = DrumSoundType.SAMPLE,
                sampleFileName = "",
                colorStyle = style,
                volume = vol,
                customColor = color
            )
        }

        val initialScenes = emptyList<ScenePreset>()

        val defaultFx = (0..8).associateWith { FxParameters() }

        return MixerUiState(
            audioSlots = initialAudioSlots,
            tracks = initialTracks,
            drumPads = initialDrumPads,
            scenes = initialScenes,
            fxParameters = defaultFx,
            currentTheme = AppTheme.CYBER_NEON
        )
    }

    // ================= REAL STORAGE & FILE REFRESH =================
    fun setStoragePermissionDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showStoragePermissionDialog = visible) }
    }

    fun refreshStorageFiles() {
        refreshStorageJob?.cancel()
        refreshStorageJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isScanningStorage = true) }
            try {
                fileManager.ensureDirectoriesExist()

                val sfs = fileManager.getSoundFontFiles()
                val loops = fileManager.getLoopFiles()
                val drumPads = fileManager.getDrumPadFiles()
                val drumPadLoops = fileManager.getDrumPadLoopFiles()
                val loopFolderTree = fileManager.getLoopFolderTree()
                val midis = fileManager.getMidiFiles()
                val midiFolderTree = fileManager.getMidiFolderTree()
                val styles = fileManager.getStyleFiles()
                val recs = fileManager.getRecordingFiles()
                val sceneFiles = fileManager.getSceneFiles()

                val realScenes = sceneFiles.map { f ->
                    ScenePreset(
                        id = f.name,
                        name = f.name,
                        timestamp = f.formattedSize,
                        color = NeonCyan
                    )
                }

                val loopDirItems = fileManager.listItemsInDirectory(fileManager.loopsDir.absolutePath)
                val midiDirItems = fileManager.listItemsInDirectory(fileManager.midiDir.absolutePath)

                val bankFiles = sfs.map { sf ->
                    SoundfontBankFile(
                        name = sf.name,
                        path = sf.path,
                        size = sf.formattedSize
                    )
                }

                _uiState.update {
                    it.copy(
                        storageBaseDirPath = fileManager.baseDir.absolutePath,
                        currentLoopDirPath = fileManager.loopsDir.absolutePath,
                        currentMidiDirPath = fileManager.midiDir.absolutePath,
                        realSoundfonts = sfs,
                        realLoopFiles = loops,
                        realDrumPadFiles = drumPads,
                        realDrumPadLoopFiles = drumPadLoops,
                        loopFolders = loopFolderTree,
                        realStyleFiles = styles,
                        realRecordingFiles = recs,
                        realMidiFiles = midis,
                        midiFolders = midiFolderTree,
                        currentLoopDirItems = loopDirItems,
                        currentMidiDirItems = midiDirItems,
                        soundfontBankFiles = bankFiles,
                        scenes = realScenes,
                        isScanningStorage = false
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isScanningStorage = false) }
            }
        }
    }

    // ================= URI IMPORT FUNCTIONS =================
    fun importSoundFontUri(uri: android.net.Uri, targetSlotId: Int? = null) {
        importSoundFontUris(listOf(uri), targetSlotId)
    }

    fun importSoundFontUris(uris: List<android.net.Uri>, targetSlotId: Int? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val importedFiles = fileManager.importMultipleSoundFonts(uris)
            if (importedFiles.isNotEmpty()) {
                refreshStorageFiles()
                val slotToLoad = targetSlotId ?: _uiState.value.activeSoundfontSlotId
                importedFiles.firstOrNull()?.let { firstFile ->
                    loadSoundFontForSlot(slotToLoad, firstFile.absolutePath)
                }
            }
        }
    }

    fun importDrumPadUri(uri: android.net.Uri) {
        importDrumPadUris(listOf(uri))
    }

    fun importDrumPadUris(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val importedFiles = fileManager.importMultipleDrumPads(uris)
            if (importedFiles.isNotEmpty()) {
                refreshStorageFiles()
            }
        }
    }

    fun importLoopUri(uri: android.net.Uri) {
        importLoopUris(listOf(uri))
    }

    fun importLoopUris(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val importedFiles = fileManager.importMultipleLoops(uris)
            if (importedFiles.isNotEmpty()) {
                refreshStorageFiles()
            }
        }
    }

    fun importMidiUris(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val importedFiles = fileManager.importMultipleMidis(uris)
            if (importedFiles.isNotEmpty()) {
                refreshStorageFiles()
            }
        }
    }

    // ================= MIDI FILE PLAYER (.MID) =================
    fun toggleMidiPlayPause() {
        val currPlaying = _uiState.value.isMidiPlaying
        if (currPlaying) {
            audioEngine.pauseMidiPlayer()
            _uiState.update { it.copy(isMidiPlaying = false) }
        } else {
            audioEngine.resumeMidiPlayer()
            _uiState.update { it.copy(isMidiPlaying = true) }
        }
    }

    fun playMidiFile(item: StorageItem) {
        audioEngine.playMidiFile(filePath = item.path, isLooping = false, volume = _uiState.value.midiVolume)
        _uiState.update {
            it.copy(
                selectedMidiFile = item,
                selectedMidiName = item.name.substringBeforeLast("."),
                isMidiPlaying = true,
                isMidiPanelOpen = false
            )
        }
    }

    fun playMidiFile(loopFile: LoopFile) {
        val file = java.io.File(fileManager.midiDir, "${loopFile.folder}/${loopFile.name}".replace("Racine /Midi/", "").replace("Midi/", ""))
        val path = if (file.exists()) file.absolutePath else java.io.File(fileManager.midiDir, loopFile.name).absolutePath
        audioEngine.playMidiFile(filePath = path, isLooping = false, volume = _uiState.value.midiVolume)
        _uiState.update {
            it.copy(
                selectedMidiName = loopFile.name.substringBeforeLast("."),
                isMidiPlaying = true,
                isMidiPanelOpen = false
            )
        }
    }

    fun toggleMidiPanel() {
        _uiState.update { it.copy(isMidiPanelOpen = !it.isMidiPanelOpen, isLoopsPanelOpen = false, isMetroPanelOpen = false) }
    }

    fun closeMidiPanel() {
        _uiState.update { it.copy(isMidiPanelOpen = false) }
    }

    fun setMidiVolume(vol: Float) {
        _uiState.update { it.copy(midiVolume = vol.coerceIn(0f, 1f)) }
        audioEngine.setMidiVolume(vol)
    }

    fun navigateToMidiDirectory(dirPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = fileManager.listItemsInDirectory(dirPath)
            _uiState.update {
                it.copy(
                    currentMidiDirPath = dirPath,
                    currentMidiDirItems = items
                )
            }
        }
    }

    fun navigateUpMidiDirectory() {
        val current = _uiState.value.currentMidiDirPath
        val baseMidi = fileManager.midiDir.absolutePath
        if (current.startsWith(baseMidi) && current.length > baseMidi.length) {
            val parent = java.io.File(current).parentFile?.absolutePath ?: baseMidi
            navigateToMidiDirectory(parent)
        }
    }

    // ================= LOOPS SEQUENCER =================
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

    fun openLoopsDialog() {
        openPopup(ActivePopup.LOOPS)
    }

    fun closeLoopsDialog() {
        closePopup()
        _uiState.update { it.copy(editingLoopFile = null) }
    }

    fun toggleLoopsPanel() {
        // Open the full modal popup for LOOPS
        openPopup(ActivePopup.LOOPS)
    }

    fun closeLoopsPanel() {
        if (_uiState.value.activePopup == ActivePopup.LOOPS) {
            closePopup()
        }
        _uiState.update { it.copy(isLoopsPanelOpen = false, editingLoopFile = null) }
    }

    fun toggleLoopPlayPause() {
        val next = !_uiState.value.isLoopPlaying
        if (next) {
            val target = _uiState.value.activeLoopFile
                ?: _uiState.value.lastSelectedLoopFile
                ?: _uiState.value.loopFolders.firstNotNullOfOrNull { folder -> folder.files.firstOrNull() }

            if (target != null) {
                val f = java.io.File(fileManager.loopsDir, "${target.folder}/${target.name}".replace("Racine /Loops/", "").replace("Loops/", ""))
                val path = if (f.exists()) f.absolutePath else java.io.File(fileManager.loopsDir, target.name).absolutePath
                val baseBpm = if (target.bpm > 0) target.bpm else _uiState.value.bpm
                val semitones = if (target.musicalKey.isNotEmpty()) calculateSemitoneDiff(target.musicalKey, _uiState.value.selectedRootKey) else 0
                audioEngine.playLoopFile(
                    filePath = path,
                    volume = _uiState.value.loopVolume,
                    beatCount = target.beats.takeIf { b -> b > 0 } ?: _uiState.value.selectedBeatCount,
                    bpm = _uiState.value.bpm,
                    startMs = target.startMs,
                    endMs = target.endMs,
                    pitchShiftSemitones = semitones,
                    baseBpm = baseBpm
                )
                _uiState.update {
                    it.copy(
                        isLoopPlaying = true,
                        activeLoopFile = target,
                        lastSelectedLoopFile = target
                    )
                }
            } else {
                _uiState.update { it.copy(isLoopPlaying = false) }
            }
        } else {
            audioEngine.stopLoopPlayer()
            _uiState.update { it.copy(isLoopPlaying = false) }
        }
    }

    fun setLoopVolume(vol: Float) {
        _uiState.update { it.copy(loopVolume = vol.coerceIn(0f, 1f)) }
        audioEngine.setLoopVolume(vol)
    }

    fun selectBeatCount(beats: Int) {
        val clamped = beats.coerceIn(2, 64)
        _uiState.update { it.copy(selectedBeatCount = clamped) }
        audioEngine.setLoopBeats(clamped, _uiState.value.bpm)
        _uiState.value.activeLoopFile?.let { current ->
            val updated = current.copy(beats = clamped)
            _uiState.update { state ->
                state.copy(
                    activeLoopFile = updated,
                    lastSelectedLoopFile = updated,
                    loopFolders = state.loopFolders.map { folder ->
                        folder.copy(files = folder.files.map { if (it.name == current.name) updated else it })
                    }
                )
            }
        }
    }

    fun toggleLoopFolder(folderName: String) {
        _uiState.update { state ->
            val updated = state.loopFolders.map { folder ->
                if (folder.name == folderName) folder.copy(isOpen = !folder.isOpen) else folder
            }
            state.copy(loopFolders = updated)
        }
    }

    fun toggleMidiFolder(folderName: String) {
        _uiState.update { state ->
            val updated = state.midiFolders.map { folder ->
                if (folder.name == folderName) folder.copy(isOpen = !folder.isOpen) else folder
            }
            state.copy(midiFolders = updated)
        }
    }

    fun selectAndToggleLoopFile(file: LoopFile) {
        val isSame = (_uiState.value.activeLoopFile?.name == file.name)
        if (isSame && _uiState.value.isLoopPlaying) {
            audioEngine.stopLoopPlayer()
            _uiState.update { it.copy(isLoopPlaying = false, lastSelectedLoopFile = file) }
        } else {
            val directFile = when {
                file.path.isNotEmpty() && java.io.File(file.path).exists() -> java.io.File(file.path)
                java.io.File(file.folder, file.name).exists() -> java.io.File(file.folder, file.name)
                java.io.File(file.name).exists() -> java.io.File(file.name)
                else -> null
            }
            val f = java.io.File(fileManager.loopsDir, "${file.folder}/${file.name}".replace("Racine /Loops/", "").replace("Loops/", ""))
            val path = directFile?.absolutePath ?: if (f.exists()) f.absolutePath else java.io.File(fileManager.loopsDir, file.name).absolutePath
            val effectiveBpm = if (file.bpm > 0) file.bpm else _uiState.value.bpm
            val effectiveKey = if (file.musicalKey.isNotEmpty()) file.musicalKey else _uiState.value.selectedRootKey
            val effectiveSig = if (file.timeSignature.isNotEmpty()) file.timeSignature else _uiState.value.metronomeSignature
            val effectiveBeats = file.beats.takeIf { b -> b > 0 } ?: _uiState.value.selectedBeatCount
            val baseBpm = if (file.bpm > 0) file.bpm else _uiState.value.bpm
            val semitones = if (file.musicalKey.isNotEmpty()) calculateSemitoneDiff(file.musicalKey, effectiveKey) else 0

            audioEngine.playLoopFile(
                filePath = path,
                volume = _uiState.value.loopVolume,
                beatCount = effectiveBeats,
                bpm = effectiveBpm,
                startMs = file.startMs,
                endMs = file.endMs,
                pitchShiftSemitones = semitones,
                baseBpm = baseBpm
            )
            _uiState.update {
                it.copy(
                    bpm = effectiveBpm,
                    selectedRootKey = effectiveKey,
                    metronomeSignature = effectiveSig,
                    selectedBeatCount = effectiveBeats,
                    activeLoopFile = file,
                    lastSelectedLoopFile = file,
                    isLoopPlaying = true
                )
            }
        }
    }

    fun deleteLoopFile(file: LoopFile) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_uiState.value.activeLoopFile?.name == file.name) {
                audioEngine.stopLoopPlayer()
                _uiState.update { it.copy(isLoopPlaying = false, activeLoopFile = null) }
            }
            fileManager.deleteLoopFile(file.name, file.folder)
            refreshStorageFiles()
        }
    }

    fun openLoopEditor(file: LoopFile) {
        val beats = if (file.beats in 2..64) file.beats else 4
        val totalSteps = (beats * 4).coerceIn(8, 256)
        val sStep = if (file.startStep in 1..totalSteps) file.startStep else 1
        val eStep = if (file.endStep in sStep..totalSteps) file.endStep else totalSteps
        _uiState.update {
            it.copy(
                editingLoopFile = file,
                loopEditorStartMs = file.startMs,
                loopEditorEndMs = file.endMs,
                loopEditorBeats = beats,
                loopEditorStartStep = sStep,
                loopEditorEndStep = eStep
            )
        }
    }

    fun closeLoopEditor() {
        _uiState.update { it.copy(editingLoopFile = null) }
    }

    fun toggleLoopEditorPlayback() {
        val editing = _uiState.value.editingLoopFile ?: return
        val isSamePlaying = _uiState.value.isLoopPlaying && (_uiState.value.activeLoopFile?.name == editing.name)
        if (isSamePlaying) {
            audioEngine.stopLoopPlayer()
            _uiState.update { it.copy(isLoopPlaying = false) }
        } else {
            val f = java.io.File(fileManager.loopsDir, "${editing.folder}/${editing.name}".replace("Racine /Loops/", "").replace("Loops/", ""))
            val path = if (f.exists()) f.absolutePath else java.io.File(fileManager.loopsDir, editing.name).absolutePath
            val baseBpm = if (editing.bpm > 0) editing.bpm else _uiState.value.bpm
            val semitones = if (editing.musicalKey.isNotEmpty()) calculateSemitoneDiff(editing.musicalKey, _uiState.value.selectedRootKey) else 0
            audioEngine.playLoopFile(
                filePath = path,
                volume = _uiState.value.loopVolume,
                beatCount = _uiState.value.loopEditorBeats,
                bpm = _uiState.value.bpm,
                startMs = _uiState.value.loopEditorStartMs,
                endMs = _uiState.value.loopEditorEndMs,
                pitchShiftSemitones = semitones,
                baseBpm = baseBpm
            )
            _uiState.update {
                it.copy(
                    activeLoopFile = editing,
                    lastSelectedLoopFile = editing,
                    isLoopPlaying = true
                )
            }
        }
    }

    fun updateLoopEditorBeats(beats: Int) {
        val clamped = beats.coerceIn(2, 64)
        val totalSteps = clamped * 4
        val currentStartStep = _uiState.value.loopEditorStartStep.coerceIn(1, totalSteps - 1)
        val currentEndStep = _uiState.value.loopEditorEndStep.coerceIn(currentStartStep + 1, totalSteps)

        _uiState.update {
            it.copy(
                loopEditorBeats = clamped,
                loopEditorStartStep = currentStartStep,
                loopEditorEndStep = currentEndStep
            )
        }
        audioEngine.setLoopBeats(clamped, _uiState.value.bpm)
    }

    fun updateLoopEditorTrims(startMs: Int, endMs: Int) {
        _uiState.update { it.copy(loopEditorStartMs = startMs, loopEditorEndMs = endMs) }
        audioEngine.updateLoopTrims(startMs, endMs)
    }

    fun updateLoopEditorSteps(startStep: Int, endStep: Int) {
        val beats = _uiState.value.loopEditorBeats.coerceIn(2, 64)
        val totalSteps = beats * 4
        val clampedStart = startStep.coerceIn(1, totalSteps - 1)
        val clampedEnd = endStep.coerceIn(clampedStart + 1, totalSteps)

        val startFraction = (clampedStart - 1).toFloat() / totalSteps.toFloat()
        val endFraction = clampedEnd.toFloat() / totalSteps.toFloat()

        val startMs = (startFraction * 10000).toInt()
        val endMs = (endFraction * 10000).toInt()

        _uiState.update {
            it.copy(
                loopEditorStartStep = clampedStart,
                loopEditorEndStep = clampedEnd,
                loopEditorStartMs = startMs,
                loopEditorEndMs = endMs
            )
        }
        audioEngine.updateLoopTrims(startMs, endMs)
    }

    /**
     * Overwrites current loop settings in place
     */
    fun overwriteLoopFile(beats: Int, startMs: Int, endMs: Int, startStep: Int, endStep: Int) {
        val editing = _uiState.value.editingLoopFile ?: return
        val updatedFile = editing.copy(
            beats = beats.coerceIn(2, 64),
            startMs = startMs.coerceAtLeast(0),
            endMs = endMs.coerceAtLeast(0),
            startStep = startStep,
            endStep = endStep
        )
        _uiState.update { state ->
            val updatedFolders = state.loopFolders.map { folder ->
                folder.copy(files = folder.files.map { f -> if (f.name == editing.name) updatedFile else f })
            }
            state.copy(
                loopFolders = updatedFolders,
                activeLoopFile = if (state.activeLoopFile?.name == editing.name) updatedFile else state.activeLoopFile,
                lastSelectedLoopFile = updatedFile,
                editingLoopFile = null
            )
        }
        if (_uiState.value.activeLoopFile?.name == editing.name && _uiState.value.isLoopPlaying) {
            val f = java.io.File(fileManager.loopsDir, "${updatedFile.folder}/${updatedFile.name}".replace("Racine /Loops/", "").replace("Loops/", ""))
            val path = if (f.exists()) f.absolutePath else java.io.File(fileManager.loopsDir, updatedFile.name).absolutePath
            val baseBpm = if (updatedFile.bpm > 0) updatedFile.bpm else _uiState.value.bpm
            val semitones = if (updatedFile.musicalKey.isNotEmpty()) calculateSemitoneDiff(updatedFile.musicalKey, _uiState.value.selectedRootKey) else 0
            audioEngine.playLoopFile(
                filePath = path,
                volume = _uiState.value.loopVolume,
                beatCount = updatedFile.beats,
                bpm = _uiState.value.bpm,
                startMs = updatedFile.startMs,
                endMs = updatedFile.endMs,
                pitchShiftSemitones = semitones,
                baseBpm = baseBpm
            )
        }
    }

    /**
     * Saves changes as a new copy of the file
     */
    fun saveCopyLoopFile(beats: Int, startMs: Int, endMs: Int, startStep: Int, endStep: Int) {
        val editing = _uiState.value.editingLoopFile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val ext = if (editing.name.contains(".")) ".${editing.name.substringAfterLast('.')}" else ".wav"
            val base = editing.name.substringBeforeLast('.')
            val newName = "${base}_edit$ext"

            fileManager.copyLoopFile(editing.name, editing.folder, newName)
            val newFile = LoopFile(
                name = newName,
                duration = editing.duration,
                folder = editing.folder,
                bpm = editing.bpm,
                musicalKey = editing.musicalKey,
                startMs = startMs.coerceAtLeast(0),
                endMs = endMs.coerceAtLeast(0),
                beats = beats.coerceIn(2, 64),
                startStep = startStep,
                endStep = endStep
            )
            refreshStorageFiles()
            _uiState.update { state ->
                val updatedFolders = state.loopFolders.map { folder ->
                    if (folder.name == editing.folder || (folder.name.contains("Racine") && editing.folder.isEmpty())) {
                        folder.copy(files = folder.files + newFile)
                    } else folder
                }
                state.copy(
                    loopFolders = updatedFolders,
                    activeLoopFile = newFile,
                    lastSelectedLoopFile = newFile,
                    editingLoopFile = null
                )
            }
        }
    }

    fun renameLoopFile(file: LoopFile, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == file.name) return
        val finalName = if (!trimmed.contains(".")) {
            val ext = if (file.name.contains(".")) ".${file.name.substringAfterLast('.')}" else ".wav"
            "$trimmed$ext"
        } else trimmed

        viewModelScope.launch(Dispatchers.IO) {
            val success = fileManager.renameLoopFile(file.name, file.folder, finalName)
            if (success) {
                val updatedFile = file.copy(name = finalName)
                refreshStorageFiles()
                _uiState.update { state ->
                    val updatedFolders = state.loopFolders.map { folder ->
                        folder.copy(files = folder.files.map { f -> if (f.name == file.name) updatedFile else f })
                    }
                    state.copy(
                        loopFolders = updatedFolders,
                        activeLoopFile = if (state.activeLoopFile?.name == file.name) updatedFile else state.activeLoopFile,
                        lastSelectedLoopFile = if (state.lastSelectedLoopFile?.name == file.name) updatedFile else state.lastSelectedLoopFile,
                        editingLoopFile = if (state.editingLoopFile?.name == file.name) updatedFile else state.editingLoopFile
                    )
                }
            }
        }
    }

    // ================= THEME & LANGUAGE SELECTION =================
    fun setAppTheme(theme: AppTheme) {
        _uiState.update { it.copy(currentTheme = theme) }
    }

    fun setAppLanguage(language: AppLanguage) {
        _uiState.update { it.copy(selectedAppLanguage = language, selectedLanguage = language.displayName) }
    }

    // ================= PEAK METERS SIMULATION =================
    private fun noteNameToMidi(note: String): Int {
        val regex = "([A-Ga-g]#?)(-?\\d+)".toRegex()
        val match = regex.matchEntire(note.trim()) ?: return 60
        val key = match.groupValues[1].uppercase()
        val oct = match.groupValues[2].toIntOrNull() ?: 4
        val semitone = when (key) {
            "C" -> 0; "C#" -> 1; "DB" -> 1
            "D" -> 2; "D#" -> 3; "EB" -> 3
            "E" -> 4
            "F" -> 5; "F#" -> 6; "GB" -> 6
            "G" -> 7; "G#" -> 8; "AB" -> 8
            "A" -> 9; "A#" -> 10; "BB" -> 10
            "B" -> 11
            else -> 0
        }
        return (oct + 1) * 12 + semitone
    }

    private fun startPeakMeterSimulation() {
        peakMeterJob?.cancel()
        peakMeterJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                try {
                    val curr = _uiState.value
                    val pressedMidiNotes = curr.pressedKeys.map { noteNameToMidi(it) }
                    val isAnyMeterActive = curr.tracks.any { it.peakMeterL > 0.005f || it.peakMeterR > 0.005f } ||
                            curr.masterTrack.peakMeterL > 0.005f || curr.masterTrack.peakMeterR > 0.005f

                    // If idle (no keys pressed and all meters already decayed to 0), throttle loop and avoid State updates
                    if (pressedMidiNotes.isEmpty() && !isAnyMeterActive) {
                        delay(250)
                        continue
                    }

                    delay(50)
                    val anySolo = curr.tracks.any { it.isSolo }

                    _uiState.update { state ->
                        var maxPlayingLevelL = 0f
                        var maxPlayingLevelR = 0f

                        val updatedTracks = state.tracks.map { track ->
                            val isAllowedBySolo = if (anySolo) track.isSolo else true
                            val isTrackActive = track.isEnabled && !track.isMuted && isAllowedBySolo

                            val isPlayingSound = if (!isTrackActive || pressedMidiNotes.isEmpty() || track.soundfontName.isEmpty()) {
                                false
                            } else {
                                pressedMidiNotes.any { midi -> midi in track.splitNoteMin..track.splitNoteMax }
                            }

                            val targetAmp = if (isPlayingSound) {
                                val baseAmp = track.volume * (0.65f + Random.nextFloat() * 0.28f)
                                if (baseAmp.isFinite()) baseAmp.coerceIn(0f, 1f) else 0f
                            } else {
                                0f
                            }

                            val panL = (1f - track.pan).coerceIn(0f, 1f)
                            val panR = (1f + track.pan).coerceIn(0f, 1f)
                            val targetL = (targetAmp * panL).coerceIn(0f, 1f)
                            val targetR = (targetAmp * panR).coerceIn(0f, 1f)

                            val prevL = if (track.peakMeterL.isFinite()) track.peakMeterL else 0f
                            val prevR = if (track.peakMeterR.isFinite()) track.peakMeterR else 0f

                            // Fluid rise and decay
                            val newL = if (targetL > prevL) {
                                (prevL * 0.3f + targetL * 0.7f).coerceIn(0f, 1f)
                            } else {
                                (prevL * 0.75f - 0.015f).coerceIn(0f, 1f)
                            }

                            val newR = if (targetR > prevR) {
                                (prevR * 0.3f + targetR * 0.7f).coerceIn(0f, 1f)
                            } else {
                                (prevR * 0.75f - 0.015f).coerceIn(0f, 1f)
                            }

                            val finalL = if (newL < 0.005f) 0f else newL
                            val finalR = if (newR < 0.005f) 0f else newR

                            if (finalL > maxPlayingLevelL) maxPlayingLevelL = finalL
                            if (finalR > maxPlayingLevelR) maxPlayingLevelR = finalR

                            track.copy(peakMeterL = finalL, peakMeterR = finalR)
                        }

                        val masterTargetL = if (state.masterTrack.isEnabled && !state.masterTrack.isMuted) {
                            (maxPlayingLevelL * state.masterTrack.volume).coerceIn(0f, 1f)
                        } else 0f

                        val masterTargetR = if (state.masterTrack.isEnabled && !state.masterTrack.isMuted) {
                            (maxPlayingLevelR * state.masterTrack.volume).coerceIn(0f, 1f)
                        } else 0f

                        val masterPrevL = if (state.masterTrack.peakMeterL.isFinite()) state.masterTrack.peakMeterL else 0f
                        val masterPrevR = if (state.masterTrack.peakMeterR.isFinite()) state.masterTrack.peakMeterR else 0f

                        val masterL = if (masterTargetL > masterPrevL) {
                            (masterPrevL * 0.3f + masterTargetL * 0.7f).coerceIn(0f, 1f)
                        } else {
                            (masterPrevL * 0.75f - 0.015f).coerceIn(0f, 1f)
                        }

                        val masterR = if (masterTargetR > masterPrevR) {
                            (masterPrevR * 0.3f + masterTargetR * 0.7f).coerceIn(0f, 1f)
                        } else {
                            (masterPrevR * 0.75f - 0.015f).coerceIn(0f, 1f)
                        }

                        val finalMasterL = if (masterL < 0.005f) 0f else masterL
                        val finalMasterR = if (masterR < 0.005f) 0f else masterR

                        state.copy(
                            tracks = updatedTracks,
                            masterTrack = state.masterTrack.copy(peakMeterL = finalMasterL, peakMeterR = finalMasterR)
                        )
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // Suppress excessive log spam in high frequency loops
                }
            }
        }
    }

    // ================= TOP BAR & TEMPO =================
    fun updateTranspose(delta: Int) {
        val newTranspose = (_uiState.value.transpose + delta).coerceIn(-12, 12)
        _uiState.update { it.copy(transpose = newTranspose) }
        audioEngine.globalTranspose = newTranspose
        // Update transpose on all 8 MIDI channels
        for (ch in 0..7) {
            NativeAudioBridge.safeSetTrackTranspose(ch, newTranspose)
        }
        persistCurrentStateDebounced()
    }

    fun updateOctave(delta: Int) {
        val newOctave = (_uiState.value.octave + delta).coerceIn(-3, 3)
        if (newOctave != _uiState.value.octave) {
            audioEngine.globalOctaveShift = newOctave
            _uiState.update { it.copy(octave = newOctave) }
            persistCurrentStateDebounced()
        }
    }

    fun updateBpm(delta: Int) {
        setGlobalBpm(_uiState.value.bpm + delta)
    }

    fun setGlobalBpm(newBpm: Int) {
        val bpm = newBpm.coerceIn(20, 300)
        _uiState.update { it.copy(bpm = bpm) }
        if (_uiState.value.isMetronomeOn) {
            audioEngine.startMetronome(bpm, _uiState.value.metronomeSignature, _uiState.value.metronomeVolume)
        }
        if (_uiState.value.isLoopPlaying) {
            audioEngine.setLoopBpm(bpm)
        }
    }

    // ================= RECORDING =================
    fun toggleRecording() {
        val next = !_uiState.value.isRecording
        if (next) {
            startRecordingTimer()
        } else {
            stopRecordingTimer()
        }
        _uiState.update { it.copy(isRecording = next) }
    }

    private fun startRecordingTimer() {
        recordingTimerJob?.cancel()
        _uiState.update { it.copy(recordingDuration = 0) }
        recordingTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _uiState.update { it.copy(recordingDuration = it.recordingDuration + 1) }
            }
        }
    }

    private fun stopRecordingTimer() {
        recordingTimerJob?.cancel()
        _uiState.update { it.copy(lastRecordedFile = "REC_${System.currentTimeMillis()}.wav") }
    }

    // ================= METRONOME =================
    fun toggleMetronome() {
        val next = !_uiState.value.isMetronomeOn
        if (next) {
            audioEngine.startMetronome(_uiState.value.bpm, _uiState.value.metronomeSignature, _uiState.value.metronomeVolume)
        } else {
            audioEngine.stopMetronome()
        }
        _uiState.update { it.copy(isMetronomeOn = next) }
    }

    fun toggleMetroPanel() {
        _uiState.update { it.copy(isMetroPanelOpen = !it.isMetroPanelOpen, isLoopsPanelOpen = false, isMidiPanelOpen = false) }
    }

    fun closeMetroPanel() {
        _uiState.update { it.copy(isMetroPanelOpen = false) }
    }

    fun setMetronomeSignature(sig: String) {
        _uiState.update { it.copy(metronomeSignature = sig) }
        if (_uiState.value.isMetronomeOn) {
            audioEngine.startMetronome(_uiState.value.bpm, sig, _uiState.value.metronomeVolume)
        }
    }

    fun setSelectedRootKey(key: String) {
        _uiState.update { it.copy(selectedRootKey = key) }
        val active = _uiState.value.activeLoopFile
        if (active != null && _uiState.value.isLoopPlaying) {
            val sourceKey = if (active.musicalKey.isNotEmpty()) active.musicalKey else "C"
            val semitones = calculateSemitoneDiff(sourceKey, key)
            audioEngine.setLoopPitchShift(semitones)
        }
    }

    private fun calculateSemitoneDiff(sourceKey: String, targetKey: String): Int {
        fun keyToVal(k: String): Int = when (k.trim().uppercase().replace("M", "").replace("MIN", "").replace("MAJ", "")) {
            "C" -> 0
            "C#", "DB" -> 1
            "D" -> 2
            "D#", "EB" -> 3
            "E" -> 4
            "F" -> 5
            "F#", "GB" -> 6
            "G" -> 7
            "G#", "AB" -> 8
            "A" -> 9
            "A#", "BB" -> 10
            "B" -> 11
            else -> 0
        }
        if (sourceKey.isBlank() || targetKey.isBlank()) return 0
        var diff = keyToVal(targetKey) - keyToVal(sourceKey)
        if (diff > 6) diff -= 12
        if (diff < -6) diff += 12
        return diff
    }

    fun setMetronomeVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _uiState.update { it.copy(metronomeVolume = clamped) }
        if (_uiState.value.isMetronomeOn) {
            audioEngine.startMetronome(_uiState.value.bpm, _uiState.value.metronomeSignature, clamped)
        }
    }

    // ================= SUSTAIN, PANIC & SPLITTER =================
    fun toggleSustain() {
        val nextSustain = !_uiState.value.isSustainActive
        audioEngine.setSustainPedal(nextSustain)
        _uiState.update { state ->
            state.copy(
                isSustainActive = nextSustain
            )
        }
    }

    fun toggleSplitter() {
        _uiState.update { it.copy(isSplitterActive = !it.isSplitterActive) }
    }

    fun triggerPanic() {
        audioEngine.setSustainPedal(false)
        audioEngine.allNotesOff()
        audioEngine.stopMetronome()
        audioEngine.stopLoopPlayer()
        audioEngine.stopMidiPlayer()
        _uiState.update { state ->
            state.copy(
                pressedKeys = emptySet(),
                activeTonicNotes = emptySet(),
                isSustainActive = false,
                isLoopPlaying = false,
                isMidiPlaying = false,
                isMetronomeOn = false
            )
        }
    }

    // ================= TRACK MIXER CONTROLS =================
    private fun applyTrackVolumes(state: MixerUiState) {
        val anySolo = state.tracks.any { it.isSolo }
        state.tracks.forEach { track ->
            val ch = track.id - 1
            if (ch in 0..7) {
                val effectiveVol = when {
                    !track.isEnabled -> 0f
                    anySolo && !track.isSolo -> 0f
                    track.isMuted -> 0f
                    else -> track.volume
                }
                audioEngine.setChannelVolume(ch, effectiveVol)
                NativeAudioBridge.safeSetTrackVolume(ch, effectiveVol)
            }
        }
    }

    fun setTrackVolume(trackId: Int, volume: Float) {
        val clampedVol = volume.coerceIn(0f, 1f)
        if (trackId == 0) {
            audioEngine.masterVolume = clampedVol
            NativeAudioBridge.safeSetMasterVolume(clampedVol)
            _uiState.update { state ->
                state.copy(masterTrack = state.masterTrack.copy(volume = clampedVol))
            }
        } else if (trackId in 1..8) {
            val ch = trackId - 1
            val currentState = _uiState.value
            val targetTrack = currentState.tracks.getOrNull(ch)
            val anySolo = currentState.tracks.any { it.isSolo }
            val effectiveVol = when {
                targetTrack == null || !targetTrack.isEnabled -> 0f
                anySolo && !targetTrack.isSolo -> 0f
                targetTrack.isMuted -> 0f
                else -> clampedVol
            }
            audioEngine.setChannelVolume(ch, effectiveVol)
            NativeAudioBridge.safeSetTrackVolume(ch, effectiveVol)

            _uiState.update { state ->
                val updated = state.tracks.map { track ->
                    if (track.id == trackId) track.copy(volume = clampedVol) else track
                }
                state.copy(tracks = updated)
            }
        }
        persistCurrentStateDebounced()
    }

    fun setTrackPan(trackId: Int, pan: Float) {
        val clampedPan = pan.coerceIn(-1f, 1f)
        if (trackId in 1..8) {
            val ch = trackId - 1
            audioEngine.setChannelPan(ch, clampedPan)
            NativeAudioBridge.safeSetTrackPan(ch, clampedPan)
        }
        _uiState.update { state ->
            val updated = state.tracks.map { track ->
                if (track.id == trackId) track.copy(pan = clampedPan) else track
            }
            state.copy(tracks = updated)
        }
        persistCurrentStateDebounced()
    }

    fun toggleTrackPower(trackId: Int) {
        _uiState.update { state ->
            val updated = state.tracks.map { track ->
                if (track.id == trackId) {
                    val nextState = !track.isEnabled
                    if (trackId in 1..8) {
                        val ch = trackId - 1
                        audioEngine.setChannelEnabled(ch, nextState)
                        if (!nextState) {
                            NativeAudioBridge.safeAllNotesOff(ch)
                        } else {
                            val slot = state.audioSlots.getOrNull(ch)
                            if (slot != null && slot.soundFontId > 0) {
                                NativeAudioBridge.safeSelectProgram(
                                    engineIndex = NativeAudioBridge.ENGINE_FADER,
                                    channel = ch,
                                    soundFontId = slot.soundFontId,
                                    bank = slot.bank,
                                    preset = slot.preset
                                )
                            }
                        }
                    }
                    track.copy(isEnabled = nextState)
                } else track
            }
            val newState = state.copy(tracks = updated)
            applyTrackVolumes(newState)
            newState
        }
        persistCurrentStateDebounced()
    }

    fun onTrackMuteClick(trackId: Int) {
        _uiState.update { state ->
            val updated = state.tracks.map { t ->
                if (t.id == trackId) {
                    t.copy(isMuted = !t.isMuted)
                } else t
            }
            val newState = state.copy(tracks = updated)
            applyTrackVolumes(newState)
            
            val isNowMuted = newState.tracks.find { it.id == trackId }?.let { it.isMuted || (newState.tracks.any { t -> t.isSolo } && !it.isSolo) } == true
            if (isNowMuted && trackId in 1..8) {
                 NativeAudioBridge.safeAllNotesOff(trackId - 1)
            }
            
            newState
        }
    }

    fun onTrackSoloClick(trackId: Int) {
        _uiState.update { state ->
            val updated = state.tracks.map { t ->
                if (t.id == trackId) {
                    t.copy(isSolo = !t.isSolo)
                } else t
            }
            val newState = state.copy(tracks = updated)
            applyTrackVolumes(newState)
            
            val isNowMuted = newState.tracks.find { it.id == trackId }?.let { it.isMuted || (newState.tracks.any { t -> t.isSolo } && !it.isSolo) } == true
            if (isNowMuted && trackId in 1..8) {
                 NativeAudioBridge.safeAllNotesOff(trackId - 1)
            }
            
            newState
        }
    }

    // ================= VIRTUAL KEYBOARD & MULTI-TOUCH =================
    companion object {
        const val FIXED_DEPLOYED_KEYBOARD_FRACTION = 0.21f
    }

    fun cycleKeyboardExpansion() {
        _uiState.update { state ->
            val nextFraction = if (state.keyboardHeightFraction > 0.05f) 0f else FIXED_DEPLOYED_KEYBOARD_FRACTION
            state.copy(
                keyboardHeightFraction = nextFraction,
                isKeyboardLayerExpanded = if (nextFraction == 0f) false else state.isKeyboardLayerExpanded
            )
        }
    }

    fun toggleKeyboardLock() {
        _uiState.update { state ->
            val newLocked = !state.isKeyboardLocked
            val nextFraction = if (newLocked) 0f else FIXED_DEPLOYED_KEYBOARD_FRACTION
            state.copy(
                isKeyboardLocked = newLocked,
                keyboardHeightFraction = nextFraction,
                isKeyboardLayerExpanded = if (nextFraction == 0f) false else state.isKeyboardLayerExpanded
            )
        }
    }

    fun setKeyboardHeightFraction(fraction: Float) {
        _uiState.update { state ->
            // Virtual keyboard is exclusively locked to fixed deployed height or collapsed
            val target = if (fraction > 0.10f) FIXED_DEPLOYED_KEYBOARD_FRACTION else 0f
            state.copy(
                keyboardHeightFraction = target,
                isKeyboardLayerExpanded = if (target == 0f) false else state.isKeyboardLayerExpanded
            )
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

    fun getActivePerformanceChannels(midiNote: Int? = null): List<Int> {
        val state = _uiState.value
        val anySolo = state.tracks.any { it.isSolo }

        // Dynamic Keyboard Layer Mapper: route notes strictly within each active track's assigned key range (splitNoteMin..splitNoteMax)
        val activeChannels = state.tracks.mapIndexedNotNull { idx, track ->
            val isAllowed = track.isEnabled && !track.isMuted && (!anySolo || track.isSolo)
            if (isAllowed) {
                val isWithinRange = (midiNote == null) || (midiNote in track.splitNoteMin..track.splitNoteMax)
                if (isWithinRange) {
                    val slot = state.audioSlots.getOrNull(idx)
                    if (slot != null && slot.soundFontId > 0) idx else null
                } else null
            } else null
        }

        return if (activeChannels.isNotEmpty()) {
            activeChannels
        } else {
            listOf(midiChannelForSlot(state.activeSoundfontSlotId))
        }
    }

    fun setPitchBend(bend: Float) {
        val clamped = bend.coerceIn(-1.0f, 1.0f)
        val midiBend = ((clamped + 1.0f) * 8191.5f).toInt().coerceIn(0, 16383)
        val channels = getActivePerformanceChannels()
        channels.forEach { channel ->
            NativeAudioBridge.safePitchBend(channel, midiBend)
        }
        audioEngine.setPitchBend(clamped)
        _uiState.update { it.copy(pitchBend = clamped) }
    }

    private fun applyVelocityCurve(velocity: Float, curve: Float): Float {
        val v = velocity.coerceIn(0.01f, 1.0f)
        return when {
            curve < 0.48f -> {
                // Soft (vers la gauche) : atténue la vélocité pour un jeu doux et expressif
                val factor = (0.5f - curve) * 2f
                (Math.pow(v.toDouble(), 1.0 + factor * 1.5)).toFloat()
            }
            curve > 0.52f -> {
                // Hard (vers la droite) : booste la vélocité pour une frappe percutante et forte
                val factor = (curve - 0.5f) * 2f
                (Math.pow(v.toDouble(), (1.0 - factor * 0.6).coerceAtLeast(0.3))).toFloat()
            }
            else -> v
        }.coerceIn(0.05f, 1.0f)
    }

    fun onKeyDown(key: String, velocity: Float = 0.85f) {
        val midiNote = noteNameToMidi(key)
        val channels = getActivePerformanceChannels(midiNote)
        val state = _uiState.value

        val baseVel = state.globalVelocityMin + velocity.coerceIn(0f, 1f) * (state.globalVelocityMax - state.globalVelocityMin)

        channels.forEach { channel ->
            val track = state.tracks.getOrNull(channel)
            val finalVel = if (track != null) {
                applyVelocityCurve(baseVel, track.velocityCurve)
            } else baseVel
            audioEngine.noteOn(key, finalVel, channel)
        }
        _uiState.update { state ->
            state.copy(pressedKeys = state.pressedKeys + key)
        }
    }

    fun onKeyUp(key: String) {
        val midiNote = noteNameToMidi(key)
        val channels = getActivePerformanceChannels(midiNote)
        channels.forEach { channel ->
            audioEngine.noteOff(key, channel)
        }
        _uiState.update { state ->
            state.copy(pressedKeys = state.pressedKeys - key)
        }
    }

    // ================= POPUPS & DIALOGS =================
    fun openPopup(popup: ActivePopup) {
        _uiState.update {
            val drumPinned = if (popup == ActivePopup.DRUM_PAD) (it.drumPadWasPinned || it.isDrumPadPinned) else it.isDrumPadPinned
            val tonicPinned = if (popup == ActivePopup.TONIC_PAD) (it.tonicPadWasPinned || it.isTonicPadPinned) else it.isTonicPadPinned
            it.copy(
                activePopup = popup,
                isLoopsPanelOpen = false,
                isMetroPanelOpen = false,
                isMidiPanelOpen = false,
                isDrumPadPinned = drumPinned,
                isTonicPadPinned = tonicPinned,
                drumActiveTab = if (popup == ActivePopup.DRUM_PAD) "pad" else it.drumActiveTab
            )
        }
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

    fun updateNotesText(text: String) {
        _uiState.update { it.copy(notesText = text) }
    }

    fun closeDrumPad() {
        _uiState.update {
            val wasPinned = it.isDrumPadPinned || it.drumPadWasPinned
            it.copy(
                drumPadWasPinned = wasPinned,
                isDrumPadPinned = false,
                activePopup = if (it.activePopup == ActivePopup.DRUM_PAD) ActivePopup.NONE else it.activePopup,
                editingDrumPadId = null,
                drumSubView = "main"
            )
        }
    }

    fun closeTonicPad() {
        _uiState.update {
            val wasPinned = it.isTonicPadPinned || it.tonicPadWasPinned
            it.copy(
                tonicPadWasPinned = wasPinned,
                isTonicPadPinned = false,
                activePopup = if (it.activePopup == ActivePopup.TONIC_PAD) ActivePopup.NONE else it.activePopup,
                tonicSubView = "main"
            )
        }
    }

    fun togglePinDrumPad() {
        _uiState.update {
            val newPinned = !it.isDrumPadPinned
            it.copy(isDrumPadPinned = newPinned, drumPadWasPinned = newPinned)
        }
    }

    fun togglePinTonicPad() {
        _uiState.update {
            val newPinned = !it.isTonicPadPinned
            it.copy(isTonicPadPinned = newPinned, tonicPadWasPinned = newPinned)
        }
    }

    fun updateDrumPadTransform(offsetX: Float, offsetY: Float, sizeDp: Float) {
        _uiState.update { it.copy(drumPadOffsetX = offsetX, drumPadOffsetY = offsetY, drumPadSizeDp = sizeDp) }
    }

    fun updateTonicPadTransform(offsetX: Float, offsetY: Float, sizeDp: Float) {
        _uiState.update { it.copy(tonicPadOffsetX = offsetX, tonicPadOffsetY = offsetY, tonicPadSizeDp = sizeDp) }
    }

    fun openEffectsForTrack(trackId: Int) {
        _uiState.update { it.copy(activePopup = ActivePopup.EFFECTS, activeEffectTrackId = trackId) }
    }

    fun openSoundfontForSlot(slotId: Int) {
        audioEngine.activeTargetChannel = AudioSlot.midiChannelForSlot(slotId)
        _uiState.update {
            it.copy(
                activePopup = ActivePopup.SOUNDFONT,
                activeSoundfontSlotId = slotId
            )
        }
    }

    // ================= DRUM PAD CONTROLS =================
    fun setDrumTab(tab: String) {
        _uiState.update { it.copy(drumActiveTab = tab) }
    }

    fun setDrumSubView(subView: String) {
        _uiState.update { it.copy(drumSubView = subView) }
    }

    fun setDrumVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _uiState.update { it.copy(drumVolume = clamped) }
        audioEngine.setChannelVolume(8, clamped) // 8 is DRUM_PAD channel
    }

    fun setDrumReverb(rev: Float) {
        val clamped = rev.coerceIn(0f, 1f)
        _uiState.update { it.copy(drumReverb = clamped) }
        audioEngine.setChannelReverb(8, clamped)
    }

    fun onDrumPadPressed(padId: Int) {
        val pad = _uiState.value.drumPads.find { it.id == padId }
        if (pad != null) {
            if (pad.isLoopMode) {
                // Toggle loop playback continuously
                val willPlay = !pad.isLoopPlaying
                if (willPlay) {
                    val path = when {
                        pad.sampleFilePath.isNotEmpty() && File(pad.sampleFilePath).exists() -> pad.sampleFilePath
                        File(getApplication<Application>().filesDir, "LiveKeys/DrumPad/${pad.sampleFileName}").exists() -> File(getApplication<Application>().filesDir, "LiveKeys/DrumPad/${pad.sampleFileName}").absolutePath
                        File(getApplication<Application>().filesDir, "LiveKeys/Loops/${pad.sampleFileName}").exists() -> File(getApplication<Application>().filesDir, "LiveKeys/Loops/${pad.sampleFileName}").absolutePath
                        File(fileManager.loopsDir, pad.sampleFileName).exists() -> File(fileManager.loopsDir, pad.sampleFileName).absolutePath
                        File(fileManager.drumPadDir, pad.sampleFileName).exists() -> File(fileManager.drumPadDir, pad.sampleFileName).absolutePath
                        else -> ""
                    }
                    if (path.isNotEmpty() && File(path).exists()) {
                        val effectiveBeats = when (pad.loopBeatsSetting) {
                            "Auto" -> 0
                            else -> pad.loopBeatsSetting.toIntOrNull() ?: 0
                        }
                        audioEngine.playLoopFile(
                            filePath = path,
                            volume = pad.volume * _uiState.value.drumVolume,
                            beatCount = effectiveBeats,
                            bpm = _uiState.value.bpm
                        )
                    } else {
                        audioEngine.playDrumPadSound(pad, pad.volume * _uiState.value.drumVolume)
                    }
                } else {
                    audioEngine.stopLoopPlayer()
                }

                _uiState.update { state ->
                    val updated = state.drumPads.map { p ->
                        if (p.id == padId) p.copy(isPressed = true, isLoopPlaying = willPlay)
                        else if (willPlay && p.isLoopPlaying) p.copy(isLoopPlaying = false) // Choke other loops if needed
                        else p
                    }
                    state.copy(drumPads = updated)
                }
                return
            }

            // High precision low-latency live drum hit
            audioEngine.playDrumPadSound(pad, pad.volume * _uiState.value.drumVolume)

            // Auto-trigger recording on 1st pad hit when loop is armed!
            if (_uiState.value.isDrumLoopArmed && !_uiState.value.isDrumLoopRecording) {
                _uiState.update { it.copy(isDrumLoopArmed = false, isDrumLoopRecording = true) }
                val bpm = _uiState.value.bpm
                val bars = _uiState.value.drumLoopBars
                drumPadLooper.startRecording(bpm, bars)
                drumPadLooper.recordHit(pad, _uiState.value.drumVolume)

                // Schedule auto-completion at exact loop boundary
                viewModelScope.launch {
                    val durationMs = ((bars * 4.0 / bpm.toDouble()) * 60.0 * 1000.0).toLong()
                    kotlinx.coroutines.delay(durationMs)
                    if (_uiState.value.isDrumLoopRecording) {
                        stopAndRenderDrumLoop { loopFile ->
                            // Auto-play the recorded loop smoothly
                            audioEngine.playLoopFile(
                                filePath = loopFile.absolutePath,
                                volume = _uiState.value.drumVolume,
                                beatCount = bars * 4,
                                bpm = bpm
                            )
                        }
                    }
                }
            } else if (_uiState.value.isDrumLoopRecording) {
                drumPadLooper.recordHit(pad, _uiState.value.drumVolume)
            }
        }

        _uiState.update { state ->
            val updated = state.drumPads.map { p ->
                if (p.id == padId) p.copy(isPressed = true) else p
            }
            state.copy(drumPads = updated)
        }
    }

    fun onDrumPadReleased(padId: Int) {
        _uiState.update { state ->
            val updated = state.drumPads.map { p ->
                if (p.id == padId) p.copy(isPressed = false) else p
            }
            state.copy(drumPads = updated)
        }
    }

    fun toggleDrumPadLoopMode(padId: Int) {
        _uiState.update { state ->
            val updated = state.drumPads.map { pad ->
                if (pad.id == padId) {
                    val next = !pad.isLoopMode
                    if (!next && pad.isLoopPlaying) {
                        audioEngine.stopLoopPlayer()
                    }
                    pad.copy(isLoopMode = next, isLoopPlaying = false)
                } else pad
            }
            state.copy(drumPads = updated)
        }
        persistCurrentStateDebounced()
    }

    fun setDrumPadLoopMode(padId: Int, isLoop: Boolean) {
        _uiState.update { state ->
            val updated = state.drumPads.map { pad ->
                if (pad.id == padId) {
                    if (!isLoop && pad.isLoopPlaying) {
                        audioEngine.stopLoopPlayer()
                    }
                    pad.copy(isLoopMode = isLoop, isLoopPlaying = if (!isLoop) false else pad.isLoopPlaying)
                } else pad
            }
            state.copy(drumPads = updated)
        }
        persistCurrentStateDebounced()
    }

    fun deleteDrumPadLoopFile(item: StorageItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val f = File(item.path)
                if (f.exists()) {
                    f.delete()
                }
                refreshStorageFiles()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateDrumPadCustomization(padId: Int, label: String, style: DrumPadStyle, isLoopMode: Boolean = false) {
        _uiState.update { state ->
            val updated = state.drumPads.map { pad ->
                if (pad.id == padId) {
                    pad.copy(label = label.take(12), colorStyle = style, isLoopMode = isLoopMode)
                } else pad
            }
            state.copy(drumPads = updated)
        }
        persistCurrentStateDebounced()
    }

    fun assignDrumSampleOrLoop(padId: Int, sampleName: String, samplePath: String, isLoop: Boolean = false) {
        val resolvedPath = if (samplePath.isNotEmpty()) samplePath else {
            val f = File(getApplication<Application>().filesDir, "LiveKeys/DrumPad/$sampleName")
            if (f.exists()) f.absolutePath else ""
        }
        if (resolvedPath.isNotEmpty()) {
            audioEngine.preloadDrumSample(resolvedPath)
        }

        _uiState.update { state ->
            val updated = state.drumPads.map { pad ->
                if (pad.id == padId) {
                    pad.copy(
                        soundType = DrumSoundType.SAMPLE,
                        sampleFileName = sampleName,
                        sampleFilePath = resolvedPath,
                        label = sampleName.substringBeforeLast(".").take(8),
                        isLoopMode = isLoop,
                        isLoopPlaying = false
                    )
                } else pad
            }
            state.copy(drumPads = updated)
        }
        persistCurrentStateDebounced()
    }

    fun assignDrumSample(padId: Int, sampleName: String, samplePath: String = "") {
        assignDrumSampleOrLoop(padId, sampleName, samplePath, isLoop = false)
    }

    fun assignDrumSf2Note(padId: Int, key: String, octave: Int) {
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
        persistCurrentStateDebounced()
    }

    fun playDrumNote(note: String, octave: Int) {
        audioEngine.noteOn("$note$octave", _uiState.value.drumVolume, channel = NativeAudioBridge.CHANNEL_DRUMPAD)
        viewModelScope.launch {
            delay(180)
            audioEngine.noteOff("$note$octave", channel = NativeAudioBridge.CHANNEL_DRUMPAD)
        }
    }

    fun playDrumSample(sample: StorageItem) {
        audioEngine.playDrumSample(sample.name, sample.path, _uiState.value.drumVolume)
    }

    fun stopDrumPlayback() {
        audioEngine.stopLoopPlayer()
    }

    fun previewFileInExplorer(sample: StorageItem, isLoopMode: Boolean = false) {
        val isAudioLoop = isLoopMode || sample.path.contains("Loops", ignoreCase = true) || sample.name.contains("loop", ignoreCase = true) || sample.extension.equals("wav", ignoreCase = true) || sample.extension.equals("mp3", ignoreCase = true)
        if (isAudioLoop) {
            audioEngine.playLoopFile(
                filePath = sample.path,
                volume = _uiState.value.drumVolume,
                beatCount = 0,
                bpm = _uiState.value.bpm
            )
        } else {
            audioEngine.playDrumSample(sample.name, sample.path, _uiState.value.drumVolume)
        }
    }

    fun stopFileExplorerPreview() {
        audioEngine.stopLoopPlayer()
    }

    // ================= DRUM PAD LOOPER CONTROLS =================
    fun setDrumLoopBars(bars: Int) {
        _uiState.update { it.copy(drumLoopBars = bars.coerceIn(1, 16)) }
    }

    fun incrementDrumLoopBars() {
        _uiState.update { state ->
            val nextBars = when (state.drumLoopBars) {
                1 -> 2
                2 -> 4
                4 -> 8
                8 -> 16
                else -> (state.drumLoopBars + 1).coerceAtMost(16)
            }
            state.copy(drumLoopBars = nextBars)
        }
    }

    fun decrementDrumLoopBars() {
        _uiState.update { state ->
            val prevBars = when (state.drumLoopBars) {
                16 -> 8
                8 -> 4
                4 -> 2
                2 -> 1
                else -> (state.drumLoopBars - 1).coerceAtLeast(1)
            }
            state.copy(drumLoopBars = prevBars)
        }
    }

    fun setDrumTimeSignature(sig: String) {
        _uiState.update { it.copy(drumTimeSignature = sig) }
    }

    fun toggleDrumTimeSignature() {
        _uiState.update { state ->
            val nextSig = when (state.drumTimeSignature) {
                "4/4" -> "3/4"
                "3/4" -> "6/8"
                "6/8" -> "2/4"
                else -> "4/4"
            }
            state.copy(drumTimeSignature = nextSig)
        }
    }

    fun toggleArmDrumLoop() {
        if (_uiState.value.isDrumLoopRecording) {
            stopAndRenderDrumLoop()
        } else {
            _uiState.update { it.copy(isDrumLoopArmed = !it.isDrumLoopArmed) }
        }
    }

    fun startDrumLoopRecording() {
        val bpm = _uiState.value.bpm
        val bars = _uiState.value.drumLoopBars
        drumPadLooper.startRecording(bpm, bars)
        _uiState.update { it.copy(isDrumLoopRecording = true) }
    }

    fun stopAndRenderDrumLoop(customName: String? = null, onFinished: ((File) -> Unit)? = null) {
        if (!_uiState.value.isDrumLoopRecording) return
        _uiState.update { it.copy(isDrumLoopRecording = false, isDrumLoopRendering = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                fileManager.ensureDirectoriesExist()
                val targetDir = fileManager.drumPadLoopDir
                val bpm = _uiState.value.bpm
                val bars = _uiState.value.drumLoopBars
                val loopName = customName?.takeIf { it.isNotBlank() }
                    ?: "DrumLoop_${bpm}BPM_${bars}Bars_${System.currentTimeMillis() % 10000}"
                val targetFile = File(targetDir, if (loopName.endsWith(".wav", ignoreCase = true)) loopName else "$loopName.wav")

                val success = drumPadLooper.stopAndRenderLoop(targetFile)
                if (success != null && targetFile.exists()) {
                    refreshStorageFiles()
                    withContext(Dispatchers.Main) {
                        onFinished?.invoke(targetFile)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.update { it.copy(isDrumLoopRendering = false) }
            }
        }
    }

    fun cancelDrumLoopRecording() {
        drumPadLooper.cancelRecording()
        _uiState.update { it.copy(isDrumLoopRecording = false, isDrumLoopRendering = false) }
    }

    // ================= TONIC PAD CONTROLS =================
    fun toggleMultiPad() {
        _uiState.update { state ->
            val nextMulti = !state.isMultiPadEnabled
            val newNotes = if (!nextMulti && state.activeTonicNotes.size > 1) {
                setOf(state.activeTonicNotes.first())
            } else {
                state.activeTonicNotes
            }
            audioEngine.setTonicDrone(newNotes, state.tonicOctaveRange, state.tonicBrightness, state.tonicShimmer)
            state.copy(isMultiPadEnabled = nextMulti, activeTonicNotes = newNotes)
        }
    }

    fun setTonicSubView(subView: String) {
        _uiState.update { it.copy(tonicSubView = subView) }
    }

    fun setTonicVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        audioEngine.setChannelVolume(NativeAudioBridge.CHANNEL_TONIC_PAD, clamped)
        _uiState.update { state ->
            val updatedSlots = state.audioSlots.map { slot ->
                if (slot.slotId == 9) slot.copy(volume = clamped) else slot
            }
            state.copy(audioSlots = updatedSlots)
        }
        persistCurrentStateDebounced()
    }

    fun setTonicReverb(rev: Float) {
        val clamped = rev.coerceIn(0f, 1f)
        audioEngine.channelParams[9].reverb = clamped
        persistCurrentStateDebounced()
    }

    fun onTonicNoteClick(note: String) {
        if (note.isEmpty()) {
            audioEngine.setTonicDrone(emptySet(), _uiState.value.tonicOctaveRange, _uiState.value.tonicBrightness, _uiState.value.tonicShimmer)
            _uiState.update { it.copy(activeTonicNotes = emptySet()) }
            return
        }
        _uiState.update { state ->
            val newSet = if (state.isMultiPadEnabled) {
                if (state.activeTonicNotes.contains(note)) state.activeTonicNotes - note else state.activeTonicNotes + note
            } else {
                if (state.activeTonicNotes.contains(note)) emptySet() else setOf(note)
            }
            audioEngine.setTonicDrone(newSet, state.tonicOctaveRange, state.tonicBrightness, state.tonicShimmer)
            state.copy(activeTonicNotes = newSet)
        }
    }

    fun onTonicOctaveMinus() {
        val octaves = listOf("C1 — C2", "C2 — C3", "C3 — C4", "C4 — C5", "C5 — C6")
        _uiState.update { state ->
            val idx = octaves.indexOf(state.tonicOctaveRange)
            val nextIdx = if (idx > 0) idx - 1 else 0
            val newOctave = octaves[nextIdx]
            audioEngine.setTonicDrone(state.activeTonicNotes, newOctave, state.tonicBrightness, state.tonicShimmer)
            state.copy(tonicOctaveRange = newOctave)
        }
    }

    fun onTonicOctavePlus() {
        val octaves = listOf("C1 — C2", "C2 — C3", "C3 — C4", "C4 — C5", "C5 — C6")
        _uiState.update { state ->
            val idx = octaves.indexOf(state.tonicOctaveRange)
            val nextIdx = if (idx in 0 until octaves.lastIndex) idx + 1 else octaves.lastIndex
            val newOctave = octaves[nextIdx]
            audioEngine.setTonicDrone(state.activeTonicNotes, newOctave, state.tonicBrightness, state.tonicShimmer)
            state.copy(tonicOctaveRange = newOctave)
        }
    }

    fun setTonicBrightness(brightness: Float) {
        val clamped = brightness.coerceIn(0f, 1f)
        audioEngine.setTonicDrone(_uiState.value.activeTonicNotes, _uiState.value.tonicOctaveRange, clamped, _uiState.value.tonicShimmer)
        _uiState.update { it.copy(tonicBrightness = clamped) }
    }

    fun setTonicShimmer(shimmer: Float) {
        val clamped = shimmer.coerceIn(0f, 1f)
        audioEngine.setTonicDrone(_uiState.value.activeTonicNotes, _uiState.value.tonicOctaveRange, _uiState.value.tonicBrightness, clamped)
        _uiState.update { it.copy(tonicShimmer = clamped) }
    }

    // ================= SOUNDFONTS & SCENES =================
    fun setSf2Tab(tab: String) {
        _uiState.update { it.copy(activeSf2Tab = tab) }
    }

    fun loadPatchForTrack(trackIndex: Int, sf2Path: String, bank: Int, preset: Int, displayName: String) {
        loadSoundFontForSlot(trackIndex, sf2Path, bank, preset, displayName)
    }

    fun loadSoundFontForSlot(slotId: Int, sf2Path: String, bank: Int = 0, preset: Int = 0, patchName: String? = null, saveAfterLoad: Boolean = true) {
        val slot = _uiState.value.audioSlots.getOrNull(slotId) ?: return
        val targetChannel = AudioSlot.midiChannelForSlot(slotId)

        viewModelScope.launch(Dispatchers.IO) {
            soundFontLoadMutex.withLock {
                val oldSfId = slot.soundFontId

                Log.d("SoundFontLoad", "[DIAGNOSTIC] Requested load for slot=$slotId, raw sf2Path=$sf2Path")

                // Ensure file path is accessible by native C++ fopen (bridge external storage files if needed)
                val nativeReadableFile = fileManager.getNativeReadableSoundFontFile(sf2Path)
                val readablePath = nativeReadableFile.absolutePath
                Log.d("SoundFontLoad", "[DIAGNOSTIC] Resolved native-readable path=$readablePath (exists=${nativeReadableFile.exists()}, length=${nativeReadableFile.length()})")

                // 1. Charger d'abord le nouveau SoundFont via le chemin natif garanti sur le moteur unifié
                val newSfId = NativeAudioBridge.safeLoadSoundFont(NativeAudioBridge.ENGINE_FADER, readablePath)
                Log.d("SoundFontLoad", "[DIAGNOSTIC] Native safeLoadSoundFont returned ID=$newSfId for slot=$slotId")

                // 2. Décharger l'ancien UNIQUEMENT si le nouveau a réussi et que l'ancien n'est plus utilisé nulle part
                if (newSfId >= 0 && oldSfId > 0 && oldSfId != newSfId) {
                    val inUse = _uiState.value.audioSlots.any { it.slotId != slotId && it.soundFontId == oldSfId }
                    if (!inUse) {
                        NativeAudioBridge.safeUnloadSoundFont(NativeAudioBridge.ENGINE_FADER, oldSfId)
                    }
                }

                // 3. Récupérer la liste des presets du SoundFont chargé en bornant strictly 0..127 par banque
                val effectiveSfId = if (newSfId >= 0) newSfId else oldSfId
                val nativePresets = if (effectiveSfId >= 0 && NativeAudioBridge.isNativeReady()) {
                    NativeAudioBridge.safeListPresets(effectiveSfId)
                } else emptyList()

                val realPresets = if (nativePresets.isNotEmpty()) {
                    nativePresets
                        .filter { info ->
                            val lower = info.name.trim().lowercase()
                            info.preset in 0..127 && info.bank >= 0 &&
                                !lower.contains("unknown") &&
                                !lower.contains("ghost") &&
                                !lower.startsWith("unused") &&
                                !lower.startsWith("null")
                        }
                        .distinctBy { Pair(it.bank, it.preset) }
                        .sortedWith(compareBy({ it.bank }, { it.preset }))
                        .map { info ->
                            val cleanName = info.name.trim().ifEmpty { "Preset ${info.preset + 1}" }
                            SoundfontPreset(
                                id = info.preset,
                                name = cleanName,
                                bankNumber = info.bank
                            )
                        }
                } else {
                    val file = File(sf2Path)
                    if (file.exists() && file.canRead()) {
                        val parsed = SF2Parser.parsePresets(file)
                        if (parsed.isNotEmpty()) {
                            parsed.map { p ->
                                SoundfontPreset(
                                    id = p.preset,
                                    name = p.displayName,
                                    bankNumber = p.bank
                                )
                            }
                        } else emptyList()
                    } else emptyList()
                }

                val targetPreset = realPresets.find { it.bankNumber == bank && it.id == preset }
                    ?: realPresets.firstOrNull()
                    ?: SoundfontPreset(preset, patchName ?: File(sf2Path).name.removeSuffix(".sf2"), bank)

                // 4. Appeler safeSelectProgram avec le soundFontId obtenu, le bank, et le preset demandés
                audioEngine.setChannelProgram(targetChannel, targetPreset.id, targetPreset.bankNumber)
                if (effectiveSfId >= 0) {
                    NativeAudioBridge.safeSelectProgram(
                        engineIndex = NativeAudioBridge.ENGINE_FADER,
                        channel = targetChannel,
                        soundFontId = effectiveSfId,
                        bank = targetPreset.bankNumber,
                        preset = targetPreset.id
                    )
                }

                // Re-validate program selection for all other active audio slots to ensure complete isolation
                val currentSlots = _uiState.value.audioSlots
                for (s in currentSlots) {
                    if (s.slotId != slotId && s.soundFontId > 0) {
                        NativeAudioBridge.safeSelectProgram(
                            engineIndex = NativeAudioBridge.ENGINE_FADER,
                            channel = s.midiChannel,
                            soundFontId = s.soundFontId,
                            bank = s.bank,
                            preset = s.preset
                        )
                    }
                }

                // 5 & 6. Mettre à jour audioSlots[slotId] et notifier l'UI
                val sfName = File(sf2Path).name
                _uiState.update { state ->
                    val updatedSlots = state.audioSlots.map { s ->
                        if (s.slotId == slotId) {
                            s.copy(
                                soundFontId = effectiveSfId,
                                soundFontPath = sf2Path,
                                patchName = targetPreset.name,
                                bank = targetPreset.bankNumber,
                                preset = targetPreset.id,
                                presets = realPresets
                            )
                        } else s
                    }
                    val updatedTracks = if (slotId in 0..7) {
                        state.tracks.mapIndexed { idx, t ->
                            if (idx == slotId) {
                                t.copy(
                                    soundfontName = sfName,
                                    patchName = targetPreset.name,
                                    bank = targetPreset.bankNumber,
                                    program = targetPreset.id
                                )
                            } else t
                        }
                    } else state.tracks

                    val newTonicPadName = if (slotId == 9) targetPreset.name else state.activeTonicPadName
                    state.copy(
                        audioSlots = updatedSlots,
                        tracks = updatedTracks,
                        activeTonicPadName = newTonicPadName
                    )
                }
                if (saveAfterLoad) {
                    persistCurrentStateDebounced()
                }
            }
        }
    }

    fun selectSf2Preset(presetId: Int) {
        val slotId = _uiState.value.activeSoundfontSlotId
        val slot = _uiState.value.audioSlots.getOrNull(slotId) ?: return
        val p = slot.presets.find { it.id == presetId } ?: SoundfontPreset(presetId, "Preset $presetId", slot.bank)
        selectSf2Preset(slotId, p)
    }

    fun selectSf2Preset(preset: SoundfontPreset) {
        selectSf2Preset(_uiState.value.activeSoundfontSlotId, preset)
    }

    fun selectSf2Preset(slotId: Int, preset: SoundfontPreset) {
        val slot = _uiState.value.audioSlots.getOrNull(slotId) ?: return
        val targetChannel = AudioSlot.midiChannelForSlot(slotId)

        audioEngine.setChannelProgram(targetChannel, preset.id, preset.bankNumber)

        if (slot.soundFontId >= 0) {
            NativeAudioBridge.safeSelectProgram(
                NativeAudioBridge.ENGINE_FADER,
                targetChannel,
                slot.soundFontId,
                preset.bankNumber,
                preset.id
            )
        }

        _uiState.update { state ->
            val updatedSlots = state.audioSlots.map { s ->
                if (s.slotId == slotId) {
                    s.copy(
                        patchName = preset.name,
                        bank = preset.bankNumber,
                        preset = preset.id
                    )
                } else s
            }
            val updatedTracks = if (slotId in 0..7) {
                state.tracks.mapIndexed { idx, t ->
                    if (idx == slotId) {
                        t.copy(
                            patchName = preset.name,
                            bank = preset.bankNumber,
                            program = preset.id
                        )
                    } else t
                }
            } else state.tracks

            val newTonicPadName = if (slotId == 9) preset.name else state.activeTonicPadName
            state.copy(audioSlots = updatedSlots, tracks = updatedTracks, activeTonicPadName = newTonicPadName)
        }
        persistCurrentState()
    }

    fun selectDrumKit(kitName: String) {
        _uiState.update { it.copy(activeDrumKitName = kitName) }
    }

    fun loadSoundfontFromStorage(file: StorageItem) {
        loadSoundFontForSlot(_uiState.value.activeSoundfontSlotId, file.path)
    }

    fun unloadSoundFontFromSlot(slotId: Int = _uiState.value.activeSoundfontSlotId) {
        val slot = _uiState.value.audioSlots.getOrNull(slotId) ?: return
        val targetChannel = AudioSlot.midiChannelForSlot(slotId)
        val oldSfId = slot.soundFontId

        viewModelScope.launch(Dispatchers.IO) {
            soundFontLoadMutex.withLock {
                // Cut any playing notes on this channel
                audioEngine.allNotesOff()

                // Safe native unload if not used by another slot
                if (oldSfId > 0) {
                    val inUse = _uiState.value.audioSlots.any { it.slotId != slotId && it.soundFontId == oldSfId }
                    if (!inUse) {
                        NativeAudioBridge.safeUnloadSoundFont(NativeAudioBridge.ENGINE_FADER, oldSfId)
                        Log.d("SoundFontUnload", "Freed SoundFont ID=$oldSfId from RAM for slot $slotId")
                    }
                }

                _uiState.update { state ->
                    val updatedSlots = state.audioSlots.map { s ->
                        if (s.slotId == slotId) {
                            s.copy(
                                soundFontId = -1,
                                soundFontPath = "",
                                patchName = "Aucun",
                                bank = 0,
                                preset = 0,
                                presets = emptyList()
                            )
                        } else s
                    }
                    val updatedTracks = if (slotId in 0..7) {
                        state.tracks.mapIndexed { idx, t ->
                            if (idx == slotId) {
                                t.copy(
                                    soundfontName = "",
                                    patchName = "Aucun",
                                    bank = 0,
                                    program = 0
                                )
                            } else t
                        }
                    } else state.tracks

                    state.copy(
                        audioSlots = updatedSlots,
                        tracks = updatedTracks
                    )
                }
                persistCurrentStateDebounced()
                System.gc() // Reclaim memory
            }
        }
    }

    fun deleteSoundFontFile(file: StorageItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val f = File(file.path)
                if (f.exists()) {
                    f.delete()
                }

                // If any slot was using this soundfont, unload it
                _uiState.value.audioSlots.forEach { slot ->
                    if (slot.soundFontPath == file.path || slot.soundFontPath?.contains(file.name) == true) {
                        unloadSoundFontFromSlot(slot.slotId)
                    }
                }

                // Instantly filter out from UI lists
                _uiState.update { state ->
                    val updatedList = state.realSoundfonts.filterNot { it.path == file.path || it.name == file.name }
                    val updatedBankFiles = state.soundfontBankFiles.filterNot { it.path == file.path || it.name == file.name }
                    state.copy(
                        realSoundfonts = updatedList,
                        soundfontBankFiles = updatedBankFiles
                    )
                }
            } catch (e: Exception) {
                Log.e("MixerViewModel", "Error deleting soundfont file: ${e.message}", e)
            }
        }
    }

    fun selectScene(sceneId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sceneFiles = fileManager.getSceneFiles()
                val targetFile = sceneFiles.find { it.name.equals(sceneId, ignoreCase = true) } ?: return@launch
                val jsonStr = fileManager.loadSceneFile(targetFile.path) ?: return@launch
                val obj = org.json.JSONObject(jsonStr)

                val bpm = obj.optInt("bpm", _uiState.value.bpm)
                val transpose = obj.optInt("transpose", _uiState.value.transpose)
                val octave = obj.optInt("octave", _uiState.value.octave)
                val tonicBrightness = obj.optDouble("tonicBrightness", _uiState.value.tonicBrightness.toDouble()).toFloat()
                val tonicShimmer = obj.optDouble("tonicShimmer", _uiState.value.tonicShimmer.toDouble()).toFloat()
                val tonicOctaveRange = obj.optString("tonicOctaveRange", _uiState.value.tonicOctaveRange)
                val drumVol = obj.optDouble("drumVolume", _uiState.value.drumVolume.toDouble()).toFloat()
                val drumRev = obj.optDouble("drumReverb", _uiState.value.drumReverb.toDouble()).toFloat()
                val tonicVol = obj.optDouble("tonicVolume", 0.8).toFloat()
                val tonicRev = obj.optDouble("tonicReverb", 0.25).toFloat()

                val tracksArr = obj.optJSONArray("tracks")
                val updatedTracks = if (tracksArr != null) {
                    val list = mutableListOf<TrackChannel>()
                    for (i in 0 until tracksArr.length()) {
                        val tObj = tracksArr.getJSONObject(i)
                        val id = tObj.optInt("id", i + 1)
                        val originalTrack = _uiState.value.tracks.find { it.id == id } ?: TrackChannel(id, "Track $id")
                        list.add(
                            originalTrack.copy(
                                isEnabled = tObj.optBoolean("isEnabled", originalTrack.isEnabled),
                                volume = tObj.optDouble("volume", originalTrack.volume.toDouble()).toFloat(),
                                pan = tObj.optDouble("pan", originalTrack.pan.toDouble()).toFloat(),
                                soundfontName = tObj.optString("soundfontName", originalTrack.soundfontName),
                                patchName = tObj.optString("patchName", originalTrack.patchName),
                                bank = tObj.optInt("bank", originalTrack.bank),
                                program = tObj.optInt("program", originalTrack.program),
                                reverbPreset = tObj.optString("reverbPreset", originalTrack.reverbPreset),
                                reverbMix = tObj.optDouble("reverbMix", originalTrack.reverbMix.toDouble()).toFloat()
                            )
                        )
                    }
                    list
                } else _uiState.value.tracks

                val slotsArr = obj.optJSONArray("audioSlots")
                val updatedSlots = if (slotsArr != null) {
                    val list = mutableListOf<AudioSlot>()
                    for (i in 0 until slotsArr.length()) {
                        val sObj = slotsArr.getJSONObject(i)
                        val slotId = sObj.optInt("slotId", i)
                        val origSlot = _uiState.value.audioSlots.find { it.slotId == slotId } ?: AudioSlot(slotId, AudioSlot.midiChannelForSlot(slotId))
                        val sfPath = sObj.optString("soundFontPath", "").ifEmpty { null }
                        list.add(
                            origSlot.copy(
                                soundFontPath = sfPath,
                                bank = sObj.optInt("bank", origSlot.bank),
                                preset = sObj.optInt("preset", origSlot.preset),
                                patchName = sObj.optString("patchName", origSlot.patchName ?: ""),
                                volume = sObj.optDouble("volume", origSlot.volume.toDouble()).toFloat(),
                                pan = sObj.optDouble("pan", origSlot.pan.toDouble()).toFloat()
                            )
                        )
                    }
                    list
                } else _uiState.value.audioSlots

                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        state.copy(
                            activeSceneId = sceneId,
                            bpm = bpm,
                            transpose = transpose,
                            octave = octave,
                            tonicBrightness = tonicBrightness,
                            tonicShimmer = tonicShimmer,
                            tonicOctaveRange = tonicOctaveRange,
                            drumVolume = drumVol,
                            drumReverb = drumRev,
                            tracks = updatedTracks,
                            audioSlots = updatedSlots
                        )
                    }
                    applyTrackVolumes(_uiState.value)
                    setTonicVolume(tonicVol)
                    setTonicReverb(tonicRev)
                    setDrumVolume(drumVol)
                    setDrumReverb(drumRev)
                    audioEngine.globalTranspose = transpose
                    audioEngine.globalOctaveShift = octave
                }

                // Re-apply soundfonts into native engine
                updatedSlots.forEach { s ->
                    if (!s.soundFontPath.isNullOrEmpty() && java.io.File(s.soundFontPath).exists()) {
                        val sfId = NativeAudioBridge.safeLoadSoundFont(NativeAudioBridge.ENGINE_FADER, s.soundFontPath)
                        if (sfId >= 0) {
                            NativeAudioBridge.safeSelectProgram(
                                engineIndex = NativeAudioBridge.ENGINE_FADER,
                                channel = s.midiChannel,
                                soundFontId = sfId,
                                bank = s.bank,
                                preset = s.preset
                            )
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveCurrentScene(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val state = _uiState.value
                val json = org.json.JSONObject().apply {
                    put("name", name)
                    put("bpm", state.bpm)
                    put("transpose", state.transpose)
                    put("octave", state.octave)
                    put("tonicBrightness", state.tonicBrightness)
                    put("tonicShimmer", state.tonicShimmer)
                    put("tonicOctaveRange", state.tonicOctaveRange)
                    put("drumVolume", state.drumVolume)
                    put("drumReverb", state.drumReverb)
                    put("tonicVolume", state.audioSlots.getOrNull(9)?.volume ?: 0.8f)
                    put("tonicReverb", 0.25f)
                    put("masterVolume", state.masterTrack.volume)

                    val tracksArray = org.json.JSONArray()
                    state.tracks.forEach { t ->
                        val tObj = org.json.JSONObject().apply {
                            put("id", t.id)
                            put("name", t.name)
                            put("isEnabled", t.isEnabled)
                            put("volume", t.volume)
                            put("pan", t.pan)
                            put("soundfontName", t.soundfontName)
                            put("patchName", t.patchName)
                            put("bank", t.bank)
                            put("program", t.program)
                            put("reverbPreset", t.reverbPreset)
                            put("reverbMix", t.reverbMix)
                        }
                        tracksArray.put(tObj)
                    }
                    put("tracks", tracksArray)

                    val slotsArray = org.json.JSONArray()
                    state.audioSlots.forEach { s ->
                        val sObj = org.json.JSONObject().apply {
                            put("slotId", s.slotId)
                            put("soundFontPath", s.soundFontPath ?: "")
                            put("bank", s.bank)
                            put("preset", s.preset)
                            put("patchName", s.patchName ?: "")
                            put("volume", s.volume)
                            put("pan", s.pan)
                        }
                        slotsArray.put(sObj)
                    }
                    put("audioSlots", slotsArray)
                }

                fileManager.saveSceneFile(name, json.toString())
                val sceneFiles = fileManager.getSceneFiles()
                val updatedScenes = sceneFiles.map { f ->
                    ScenePreset(
                        id = f.name,
                        name = f.name,
                        timestamp = f.formattedSize,
                        color = NeonCyan
                    )
                }

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            scenes = updatedScenes,
                            activeSceneId = name
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteScene(sceneId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                fileManager.deleteSceneFile(sceneId)
                val sceneFiles = fileManager.getSceneFiles()
                val updatedScenes = sceneFiles.map { f ->
                    ScenePreset(
                        id = f.name,
                        name = f.name,
                        timestamp = f.formattedSize,
                        color = NeonCyan
                    )
                }
                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        val nextActive = if (state.activeSceneId == sceneId) {
                            updatedScenes.firstOrNull()?.id ?: ""
                        } else state.activeSceneId
                        state.copy(
                            scenes = updatedScenes,
                            activeSceneId = nextActive
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateActiveScene() {
        val currentActiveId = _uiState.value.activeSceneId
        if (currentActiveId.isNotEmpty()) {
            saveCurrentScene(currentActiveId)
        } else {
            saveCurrentScene("Scène ${_uiState.value.scenes.size + 1}")
        }
    }

    // ================= SNAPSHOTS / SUB-SCENES (SECTION 2) =================
    fun toggleSnapshotArm() {
        _uiState.update { it.copy(isSnapshotArmMode = !it.isSnapshotArmMode) }
    }

    fun renameSnapshotSlot(slotName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        _uiState.update {
            val updated = it.snapshotCustomNames + (slotName to trimmed)
            it.copy(snapshotCustomNames = updated)
        }
    }

    fun onSnapshotSlotClick(slotName: String) {
        val currentState = _uiState.value
        if (currentState.isSnapshotArmMode) {
            val trackSnapshots = currentState.tracks.map { t ->
                TrackSnapshot(
                    id = t.id,
                    volume = t.volume,
                    pan = t.pan,
                    isMuted = t.isMuted,
                    isSolo = t.isSolo,
                    isEnabled = t.isEnabled,
                    soundfontName = t.soundfontName,
                    patchName = t.patchName,
                    bank = t.bank,
                    program = t.program,
                    reverbSend = t.reverbMix
                )
            }
            val newSubScene = SubSceneSnapshot(
                slotName = slotName,
                tracks = trackSnapshots,
                globalTranspose = currentState.transpose,
                globalOctaveShift = currentState.octave,
                masterVolume = currentState.tracks.find { it.isMaster }?.volume ?: 0.85f,
                fxParameters = currentState.fxParameters[0]
            )
            val updatedSnapshots = currentState.snapshots + (slotName to newSubScene)
            _uiState.update {
                it.copy(
                    isSnapshotArmMode = false,
                    activeSnapshotSlot = slotName,
                    snapshots = updatedSnapshots,
                    snapshotTransitionProgress = 1.0f
                )
            }
            persistCurrentStateDebounced()
        } else {
            snapshotTransitionJob?.cancel()
            val startTracks = currentState.tracks

            if (currentState.activeSnapshotSlot == slotName) {
                // 2nd tap -> Return to Custom Libre state smoothly
                val freeTracks = currentState.customLibreTracks
                if (freeTracks != null) {
                    _uiState.update { state ->
                        state.copy(
                            activeSnapshotSlot = null,
                            snapshotTransitionProgress = 0.0f
                        )
                    }
                    persistCurrentStateDebounced()
                    snapshotTransitionJob = viewModelScope.launch {
                        val totalDurationMs = 1200L
                        val steps = 48
                        val stepDelay = totalDurationMs / steps
                        for (step in 1..steps) {
                            val linearProgress = step.toFloat() / steps
                            // Cosine progressive interpolation
                            val eased = (1f - kotlin.math.cos(linearProgress * kotlin.math.PI.toFloat())) / 2f

                            val interpolated = startTracks.mapIndexed { idx, startTr ->
                                val targetTr = freeTracks.getOrNull(idx) ?: startTr
                                startTr.copy(
                                    volume = startTr.volume + (targetTr.volume - startTr.volume) * eased,
                                    pan = startTr.pan + (targetTr.pan - startTr.pan) * eased,
                                    isMuted = if (eased >= 0.5f) targetTr.isMuted else startTr.isMuted,
                                    isSolo = if (eased >= 0.5f) targetTr.isSolo else startTr.isSolo,
                                    isEnabled = if (eased >= 0.5f) targetTr.isEnabled else startTr.isEnabled
                                )
                            }
                            _uiState.update {
                                it.copy(tracks = interpolated, snapshotTransitionProgress = linearProgress)
                            }
                            interpolated.forEachIndexed { idx, track ->
                                val effectiveVol = if (track.isMuted || !track.isEnabled) 0f else track.volume
                                NativeAudioBridge.safeSetTrackVolume(idx, effectiveVol)
                                NativeAudioBridge.safeSetTrackPan(idx, track.pan)
                            }
                            delay(stepDelay)
                        }
                        _uiState.update {
                            it.copy(tracks = freeTracks, snapshotTransitionProgress = 1.0f)
                        }
                        persistCurrentStateDebounced()
                    }
                } else {
                    _uiState.update { it.copy(activeSnapshotSlot = null, snapshotTransitionProgress = 1.0f) }
                    persistCurrentStateDebounced()
                }
            } else {
                // 1st tap (or switching slot)
                val targetSnapshot = currentState.snapshots[slotName]
                if (targetSnapshot != null) {
                    val savedCustomTracks = currentState.customLibreTracks ?: currentState.tracks
                    val targetTracks = currentState.tracks.map { tr ->
                        val snap = targetSnapshot.tracks.find { it.id == tr.id }
                        if (snap != null) {
                            tr.copy(
                                volume = snap.volume,
                                pan = snap.pan,
                                isMuted = snap.isMuted,
                                isSolo = snap.isSolo,
                                isEnabled = snap.isEnabled
                            )
                        } else tr
                    }

                    // Cut off notes immediately on tracks that are turning off or muting to avoid hanging voices
                    targetTracks.forEachIndexed { idx, track ->
                        if (!track.isEnabled || track.isMuted) {
                            audioEngine.setChannelEnabled(idx, false)
                        }
                    }

                    // Update AudioEngine globals directly
                    audioEngine.globalOctaveShift = targetSnapshot.globalOctaveShift
                    audioEngine.globalTranspose = targetSnapshot.globalTranspose

                    _uiState.update {
                        it.copy(
                            customLibreTracks = savedCustomTracks,
                            activeSnapshotSlot = slotName,
                            snapshotTransitionProgress = 0.0f,
                            transpose = targetSnapshot.globalTranspose,
                            octave = targetSnapshot.globalOctaveShift
                        )
                    }
                    persistCurrentStateDebounced()

                    snapshotTransitionJob = viewModelScope.launch {
                        val totalDurationMs = 1200L
                        val steps = 48
                        val stepDelay = totalDurationMs / steps
                        for (step in 1..steps) {
                            val linearProgress = step.toFloat() / steps
                            // Cosine progressive interpolation
                            val eased = (1f - kotlin.math.cos(linearProgress * kotlin.math.PI.toFloat())) / 2f

                            val interpolated = startTracks.mapIndexed { idx, startTr ->
                                val targetTr = targetTracks.getOrNull(idx) ?: startTr
                                startTr.copy(
                                    volume = startTr.volume + (targetTr.volume - startTr.volume) * eased,
                                    pan = startTr.pan + (targetTr.pan - startTr.pan) * eased,
                                    isMuted = if (eased >= 0.5f) targetTr.isMuted else startTr.isMuted,
                                    isSolo = if (eased >= 0.5f) targetTr.isSolo else startTr.isSolo,
                                    isEnabled = if (eased >= 0.5f) targetTr.isEnabled else startTr.isEnabled
                                )
                            }
                            _uiState.update {
                                it.copy(tracks = interpolated, snapshotTransitionProgress = linearProgress)
                            }
                            interpolated.forEachIndexed { idx, track ->
                                val effectiveVol = if (track.isMuted || !track.isEnabled) 0f else track.volume
                                NativeAudioBridge.safeSetTrackVolume(idx, effectiveVol)
                                NativeAudioBridge.safeSetTrackPan(idx, track.pan)
                            }
                            delay(stepDelay)
                        }
                        _uiState.update {
                            it.copy(tracks = targetTracks, snapshotTransitionProgress = 1.0f)
                        }
                        persistCurrentStateDebounced()
                    }
                } else {
                    _uiState.update { it.copy(activeSnapshotSlot = slotName, snapshotTransitionProgress = 1.0f) }
                    persistCurrentStateDebounced()
                }
            }
        }
    }

    // ================= IN-APP FILE BROWSER PERSISTENCE (SECTION 4) =================
    fun updateLastLoopsPath(path: String) {
        _uiState.update { it.copy(lastLoopsPath = path) }
        appStatePersistence.saveLastPath("last_loops_path", path)
    }

    fun updateLastDrumPadPath(path: String) {
        _uiState.update { it.copy(lastDrumPadPath = path) }
        appStatePersistence.saveLastPath("last_drumpad_path", path)
    }

    // ================= SETTINGS DRAWER & FL SOUNDGOODIZER =================
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
        val driverType = if (engine.contains("OpenSL", ignoreCase = true)) {
            NativeAudioBridge.DRIVER_OPENSL_ES
        } else {
            NativeAudioBridge.DRIVER_OBOE
        }
        NativeAudioBridge.safeSetAudioDriver(driverType)
        _uiState.update { it.copy(audioEngine = engine) }
    }

    fun setAudioBufferSize(buffer: Int) {
        _uiState.update { it.copy(audioBufferSize = buffer) }
        audioEngine.setBufferSize(buffer)
    }

    fun setPolyphony(poly: Int) {
        _uiState.update { it.copy(polyphony = poly) }
        audioEngine.setPolyphony(poly)
    }

    fun setSelectedLanguage(lang: String) {
        val appLang = when {
            lang.contains("Français", ignoreCase = true) || lang == "fr" -> AppLanguage.FRENCH
            lang.contains("Español", ignoreCase = true) || lang == "es" -> AppLanguage.SPANISH
            else -> AppLanguage.ENGLISH
        }
        _uiState.update { it.copy(selectedLanguage = lang, selectedAppLanguage = appLang) }
        persistCurrentStateDebounced()
    }

    fun setGlobalVelocityRange(min: Float, max: Float) {
        val safeMin = min.coerceIn(0f, 1f)
        val safeMax = max.coerceIn(0f, 1f)
        _uiState.update { it.copy(globalVelocityMin = safeMin, globalVelocityMax = safeMax) }
        audioEngine.globalVelocityMin = safeMin
        audioEngine.globalVelocityMax = safeMax
        persistCurrentStateDebounced()
    }

    fun toggleUseFlats() {
        val sharpToFlat = mapOf("C#" to "Db", "D#" to "Eb", "F#" to "Gb", "G#" to "Ab", "A#" to "Bb")
        val flatToSharp = mapOf("Db" to "C#", "Eb" to "D#", "Gb" to "F#", "Ab" to "G#", "Bb" to "A#")
        _uiState.update { state ->
            val nextUseFlats = !state.useFlats
            val updatedNotes = state.activeTonicNotes.map { n ->
                if (nextUseFlats) (sharpToFlat[n] ?: n) else (flatToSharp[n] ?: n)
            }.toSet()
            state.copy(useFlats = nextUseFlats, activeTonicNotes = updatedNotes)
        }
        persistCurrentStateDebounced()
    }

    // ================= FULLSCREEN PAGE NAVIGATION & DRUMPAD PRO =================
    fun navigateToPage(page: AppScreenPage) {
        _uiState.update { it.copy(currentPage = page) }
    }

    fun navigateBackToMixer() {
        _uiState.update { it.copy(currentPage = AppScreenPage.MIXER, isDrumPadMiniBrowserOpen = false) }
    }

    fun toggleDrumPadMixMode() {
        _uiState.update { it.copy(isDrumPadMixMode = !it.isDrumPadMixMode) }
    }

    fun setDrumFeelSwing(swing: Int) {
        _uiState.update { it.copy(drumFeelSwing = swing.coerceIn(0, 100)) }
    }

    fun toggleDrumPadMiniBrowser() {
        _uiState.update { it.copy(isDrumPadMiniBrowserOpen = !it.isDrumPadMiniBrowserOpen) }
    }

    fun closeDrumPadMiniBrowser() {
        _uiState.update { it.copy(isDrumPadMiniBrowserOpen = false) }
    }

    fun openDrumPadLoopsView(open: Boolean = true) {
        _uiState.update { it.copy(isDrumPadLoopsOpen = open) }
    }

    fun openDrumPadFileExplorer(open: Boolean = true) {
        _uiState.update { it.copy(isDrumPadFileExplorerOpen = open) }
    }

    fun setDrumPadVolumeDirect(padId: Int, volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _uiState.update { state ->
            val updatedPads = state.drumPads.map { pad ->
                if (pad.id == padId) pad.copy(volume = clamped) else pad
            }
            state.copy(drumPads = updatedPads)
        }
    }

    fun setDrumPadLoopBeats(padId: Int, beatsSetting: String) {
        _uiState.update { state ->
            val updatedPads = state.drumPads.map { pad ->
                if (pad.id == padId) pad.copy(loopBeatsSetting = beatsSetting) else pad
            }
            state.copy(drumPads = updatedPads)
        }
    }

    fun createBlankScene(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = org.json.JSONObject().apply {
                    put("name", name)
                    put("bpm", 120)
                    put("transpose", 0)
                    put("octave", 0)
                    put("tonicBrightness", 0.70f)
                    put("tonicShimmer", 0.15f)
                    put("tonicOctaveRange", "C3 — C4")
                    put("drumVolume", 0.75f)
                    put("drumReverb", 0.20f)
                    put("tonicVolume", 0.80f)
                    put("tonicReverb", 0.25f)
                    put("masterVolume", 0.85f)

                    val tracksArray = org.json.JSONArray()
                    for (i in 1..8) {
                        val tObj = org.json.JSONObject().apply {
                            put("id", i)
                            put("name", "Track $i")
                            put("isEnabled", false)
                            put("volume", 0.0f)
                            put("pan", 0.0f)
                            put("soundfontName", "")
                            put("patchName", "")
                            put("bank", 0)
                            put("program", 0)
                            put("reverbPreset", "Concert Hall")
                            put("reverbMix", 0.25f)
                        }
                        tracksArray.put(tObj)
                    }
                    put("tracks", tracksArray)

                    val slotsArray = org.json.JSONArray()
                    for (i in 0..9) {
                        val sObj = org.json.JSONObject().apply {
                            put("slotId", i)
                            put("soundFontPath", "")
                            put("bank", 0)
                            put("preset", 0)
                            put("patchName", "")
                            put("volume", if (i == 0) 0.85f else 0.0f)
                            put("pan", 0.0f)
                        }
                        slotsArray.put(sObj)
                    }
                    put("audioSlots", slotsArray)
                }

                fileManager.saveSceneFile(name, json.toString())
                val sceneFiles = fileManager.getSceneFiles()
                val updatedScenes = sceneFiles.map { f ->
                    ScenePreset(id = f.name, name = f.name, timestamp = f.formattedSize, color = NeonCyan)
                }
                _uiState.update { it.copy(scenes = updatedScenes, activeSceneId = name) }
                // Appliquer immédiatement cette scène vierge
                selectScene(name)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun duplicateCurrentScene(name: String) {
        saveCurrentScene(name)
    }

    fun setSoundGoodizer(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        _uiState.update { state ->
            val curMasterFx = state.fxParameters[0] ?: FxParameters()
            val updatedMasterFx = curMasterFx.copy(
                isSgEnabled = clamped > 0.001f,
                sgAmount = clamped
            )
            val updatedMap = state.fxParameters.toMutableMap()
            updatedMap[0] = updatedMasterFx
            state.copy(
                soundGoodizer = clamped,
                fxParameters = updatedMap
            )
        }
        audioEngine.soundGoodizerAmount = clamped
        audioEngine.isSoundGoodizerEnabled = clamped > 0.001f
        val mode = _uiState.value.soundGoodizerMode
        NativeAudioBridge.safeSetSoundGoodizer(clamped > 0.001f, mode.ordinal, clamped)
        persistCurrentStateDebounced()
    }

    fun setSoundGoodizerMode(mode: SoundGoodizerMode) {
        _uiState.update { state ->
            val curMasterFx = state.fxParameters[0] ?: FxParameters()
            val updatedMasterFx = curMasterFx.copy(sgMode = mode.ordinal)
            val updatedMap = state.fxParameters.toMutableMap()
            updatedMap[0] = updatedMasterFx
            state.copy(
                soundGoodizerMode = mode,
                fxParameters = updatedMap
            )
        }
        audioEngine.soundGoodizerMode = mode.name
        val amt = _uiState.value.soundGoodizer
        NativeAudioBridge.safeSetSoundGoodizer(amt > 0.001f, mode.ordinal, amt)
        persistCurrentStateDebounced()
    }

    fun setMasterPunch(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        _uiState.update { it.copy(masterPunch = clamped) }
        audioEngine.masterPunch = clamped
        persistCurrentStateDebounced()
    }

    fun setSpatialWidener(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        _uiState.update { it.copy(spatialWidener = clamped) }
        audioEngine.spatialWidener = clamped
        persistCurrentStateDebounced()
    }

    fun toggleMidiDevice(deviceId: String) {
        _uiState.update { state ->
            val dev = state.midiDevices.find { it.id == deviceId }
            val nextEnabled = dev?.let { !it.isEnabled } ?: true
            audioEngine.setMidiDeviceEnabled(deviceId, nextEnabled)
            val updated = state.midiDevices.map { d ->
                if (d.id == deviceId) d.copy(isEnabled = nextEnabled) else d
            }
            state.copy(midiDevices = updated)
        }
    }

    fun toggleLowLatencyAudio() {
        _uiState.update { it.copy(isLowLatencyAudio = !it.isLowLatencyAudio) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _uiState.update { it.copy(keepScreenOn = enabled) }
        persistCurrentStateDebounced()
    }

    fun toggleKeepScreenOn() {
        _uiState.update { it.copy(keepScreenOn = !it.keepScreenOn) }
        persistCurrentStateDebounced()
    }

    fun setScaleMode(mode: String) {
        _uiState.update { it.copy(selectedScaleMode = mode) }
        persistCurrentStateDebounced()
    }

    // ================= FX PARAMETERS =================
    fun updateFxParameter(trackId: Int, transform: (FxParameters) -> FxParameters) {
        _uiState.update { state ->
            val current = state.fxParameters[trackId] ?: FxParameters()
            val newFx = transform(current)
            val updatedMap = state.fxParameters.toMutableMap()
            updatedMap[trackId] = newFx

            // If trackId == 0 (Master), update native master EQ, Reverb, Delay, SoundGoodizer
            if (trackId == 0) {
                val lowDb = (newFx.eqLow - 0.5f) * 24.0f
                val midDb = (newFx.eqMid - 0.5f) * 24.0f
                val highDb = (newFx.eqHigh - 0.5f) * 24.0f
                audioEngine.setMasterEq(lowDb, midDb, highDb)
                audioEngine.setMasterReverb(
                    enabled = newFx.isReverbEnabled,
                    size = newFx.reverbSize,
                    decay = newFx.reverbDecay,
                    damp = newFx.reverbDamp,
                    mix = newFx.reverbMix
                )
                val isDelayActive = newFx.isDelayEnabled && newFx.delayMix > 0.005f
                audioEngine.setMasterDelay(
                    enabled = isDelayActive,
                    timeSec = 0.05f + newFx.delayTime * 0.95f,
                    feedback = if (isDelayActive) newFx.delayFeedback else 0f,
                    mix = if (isDelayActive) newFx.delayMix else 0f,
                    pingPong = newFx.delayPingPong > 0.5f
                )
                val isChorusActive = newFx.isChorusEnabled && newFx.chorusMix > 0.005f
                audioEngine.setMasterChorus(
                    enabled = isChorusActive,
                    rateHz = 0.2f + newFx.chorusRate * 4.8f,
                    depthMs = 1.0f + newFx.chorusDepth * 14.0f,
                    mix = if (isChorusActive) newFx.chorusMix else 0f
                )
                val compThreshold = -36f + newFx.compThresh * 28f
                val compRatioVal = 1.5f + newFx.compRatio * 8.5f
                val makeupGain = if (newFx.isCompEnabled) {
                    val reductionEst = (-compThreshold) * (1f - 1f / compRatioVal)
                    (reductionEst * 0.75f).coerceIn(0f, 15f)
                } else 0f
                audioEngine.setMasterCompressor(
                    enabled = newFx.isCompEnabled,
                    thresholdDb = compThreshold,
                    ratio = compRatioVal,
                    attackMs = 1.0f + newFx.compAttack * 40f,
                    releaseMs = 20.0f + newFx.compRelease * 350f,
                    makeupGainDb = makeupGain
                )
                val modeStr = when (newFx.sgMode) {
                    0 -> "A"
                    1 -> "B"
                    2 -> "C"
                    3 -> "D"
                    else -> "A"
                }
                audioEngine.soundGoodizerMode = modeStr
                audioEngine.isSoundGoodizerEnabled = newFx.isSgEnabled
                audioEngine.soundGoodizerAmount = newFx.sgAmount
                NativeAudioBridge.safeSetSoundGoodizer(newFx.isSgEnabled, newFx.sgMode, newFx.sgAmount)
                val sgEnumMode = when (newFx.sgMode) {
                    0 -> SoundGoodizerMode.A
                    1 -> SoundGoodizerMode.B
                    2 -> SoundGoodizerMode.C
                    else -> SoundGoodizerMode.D
                }
                state.copy(
                    fxParameters = updatedMap,
                    soundGoodizer = newFx.sgAmount,
                    soundGoodizerMode = sgEnumMode
                )
            } else {
                if (trackId in 1..8) {
                    val channel = trackId - 1
                    audioEngine.setChannelReverb(channel, if (newFx.isReverbEnabled) newFx.reverbMix else 0f)
                    audioEngine.setChannelChorus(channel, if (newFx.isChorusEnabled) newFx.chorusMix else 0f)
                }
                state.copy(fxParameters = updatedMap)
            }
        }
        persistCurrentStateDebounced()
    }

    fun setFxTab(tab: String) {
        _uiState.update { it.copy(activeFxTab = tab) }
    }

    fun setTrackReverbPreset(trackId: Int, preset: String) {
        val presetParams = when (preset) {
            "Concert Hall" -> Quad(0.42f, 0.85f, 0.70f, 0.30f)
            "Warm Room" -> Quad(0.35f, 0.40f, 0.30f, 0.60f)
            "Plate 80s" -> Quad(0.48f, 0.70f, 0.55f, 0.20f)
            "Cathedral" -> Quad(0.65f, 0.95f, 0.90f, 0.15f)
            "Ambient Shimmer" -> Quad(0.60f, 0.90f, 0.85f, 0.10f)
            "Vocal Chamber" -> Quad(0.30f, 0.50f, 0.40f, 0.45f)
            "Studio Room" -> Quad(0.25f, 0.30f, 0.20f, 0.50f)
            else -> Quad(0.24f, 0.60f, 0.45f, 0.30f)
        }
        _uiState.update { state ->
            val currentFx = state.fxParameters[trackId] ?: FxParameters()
            val isCurrentPreset = (currentFx.reverbPreset == preset && currentFx.isReverbEnabled)
            val isDeselecting = isCurrentPreset
            val nextPreset = if (isDeselecting) "None" else preset
            val nextEnabled = !isDeselecting
            val nextMix = if (isDeselecting) 0f else presetParams.a

            val updatedFxMap = state.fxParameters.toMutableMap()
            if (!isDeselecting) {
                updatedFxMap[trackId] = currentFx.copy(
                    isReverbEnabled = true,
                    reverbPreset = nextPreset,
                    reverbMix = presetParams.a,
                    reverbSize = presetParams.b,
                    reverbDecay = presetParams.c,
                    reverbDamp = presetParams.d
                )
            } else {
                updatedFxMap[trackId] = currentFx.copy(
                    isReverbEnabled = false,
                    reverbPreset = "None",
                    reverbMix = 0f
                )
            }

            if (trackId in 1..8) {
                audioEngine.setChannelReverb(trackId - 1, if (nextEnabled) nextMix else 0f)
            } else if (trackId == 0) {
                val size = if (!isDeselecting) presetParams.b else currentFx.reverbSize
                val decay = if (!isDeselecting) presetParams.c else currentFx.reverbDecay
                val damp = if (!isDeselecting) presetParams.d else currentFx.reverbDamp
                audioEngine.setMasterReverb(nextEnabled, size, decay, damp, if (nextEnabled) nextMix else 0f)
            }

            if (trackId == 0) {
                state.copy(
                    masterTrack = state.masterTrack.copy(
                        reverbPreset = nextPreset,
                        reverbMix = nextMix,
                        reverbSize = if (!isDeselecting) presetParams.b else state.masterTrack.reverbSize,
                        reverbDecay = if (!isDeselecting) presetParams.c else state.masterTrack.reverbDecay
                    ),
                    fxParameters = updatedFxMap
                )
            } else {
                val updated = state.tracks.map { track ->
                    if (track.id == trackId) {
                        track.copy(
                            reverbPreset = nextPreset,
                            reverbMix = nextMix,
                            reverbSize = if (!isDeselecting) presetParams.b else track.reverbSize,
                            reverbDecay = if (!isDeselecting) presetParams.c else track.reverbDecay
                        )
                    } else track
                }
                state.copy(tracks = updated, fxParameters = updatedFxMap)
            }
        }
        persistCurrentStateDebounced()
    }

    fun toggleTrackReverb(trackId: Int) {
        _uiState.update { state ->
            val current = state.fxParameters[trackId] ?: FxParameters()
            val newEnabled = !current.isReverbEnabled
            val updatedMap = state.fxParameters.toMutableMap()
            val newMix = if (newEnabled) (if (current.reverbMix <= 0.01f) 0.35f else current.reverbMix) else 0f
            updatedMap[trackId] = current.copy(isReverbEnabled = newEnabled, reverbMix = newMix)
            if (trackId in 1..8) {
                audioEngine.setChannelReverb(trackId - 1, if (newEnabled) newMix else 0f)
            } else if (trackId == 0) {
                audioEngine.setMasterReverb(newEnabled, current.reverbSize, current.reverbDecay, current.reverbDamp, if (newEnabled) newMix else 0f)
            }
            if (trackId == 0) {
                state.copy(
                    masterTrack = state.masterTrack.copy(reverbMix = newMix),
                    fxParameters = updatedMap
                )
            } else {
                val updated = state.tracks.map { track ->
                    if (track.id == trackId) track.copy(reverbMix = newMix) else track
                }
                state.copy(tracks = updated, fxParameters = updatedMap)
            }
        }
        persistCurrentStateDebounced()
    }

    private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)

    fun setTrackVelocityCurve(trackId: Int, curve: Float) {
        _uiState.update { state ->
            val updated = state.tracks.map { track ->
                if (track.id == trackId) track.copy(velocityCurve = curve.coerceIn(0f, 1f)) else track
            }
            state.copy(tracks = updated)
        }
        persistCurrentStateDebounced()
    }

    fun setTrackSplitRange(trackId: Int, minNote: Int, maxNote: Int) {
        _uiState.update { state ->
            val updated = state.tracks.map { track ->
                if (track.id == trackId) track.copy(splitNoteMin = minNote, splitNoteMax = maxNote) else track
            }
            state.copy(tracks = updated)
        }
        persistCurrentStateDebounced()
    }

    fun updateTrackKeyRange(trackId: Int, minNote: Int, maxNote: Int) {
        setTrackSplitRange(trackId, minNote, maxNote)
    }

    fun toggleKeyboardLayer() {
        _uiState.update { it.copy(isKeyboardLayerExpanded = !it.isKeyboardLayerExpanded) }
    }

    // ================= STEPDRUM SEQUENCER ENGINE =================
    private var stepDrumJob: kotlinx.coroutines.Job? = null
    private var copiedVariation: com.soundstage.mixer.model.PatternVariation? = null

    fun toggleStepDrumPlay() {
        val willPlay = !_uiState.value.stepDrumState.isPlaying
        _uiState.update {
            it.copy(
                stepDrumState = it.stepDrumState.copy(isPlaying = willPlay)
            )
        }
        if (willPlay) {
            startStepDrumClock()
        } else {
            stepDrumJob?.cancel()
            stepDrumJob = null
            _uiState.update {
                it.copy(stepDrumState = it.stepDrumState.copy(currentStepIndex = 0))
            }
        }
    }

    fun toggleStepDrumRecord() {
        _uiState.update {
            it.copy(
                stepDrumState = it.stepDrumState.copy(isRecording = !it.stepDrumState.isRecording)
            )
        }
    }

    fun toggleStepDrumRecordMode() {
        _uiState.update {
            val nextMode = if (it.stepDrumState.recordMode == "REPLACE") "ADD" else "REPLACE"
            it.copy(stepDrumState = it.stepDrumState.copy(recordMode = nextMode))
        }
    }

    fun selectStepDrumVariation(variationId: String) {
        _uiState.update { state ->
            val curr = state.stepDrumState
            val targetVar = curr.variations[variationId] ?: com.soundstage.mixer.model.PatternVariation(id = variationId, tracks = com.soundstage.mixer.model.createDefaultStepDrumTracks())
            val updatedMap = curr.variations.toMutableMap()
            updatedMap[variationId] = targetVar
            state.copy(
                stepDrumState = curr.copy(
                    variations = updatedMap,
                    activeVariationId = variationId
                )
            )
        }
    }

    fun addStepDrumVariation() {
        val currState = _uiState.value.stepDrumState
        val availableIds = listOf("A", "B", "C", "D", "E", "F", "G", "H")
        val nextId = availableIds.firstOrNull { it !in currState.variations.keys } ?: return
        val newVar = com.soundstage.mixer.model.PatternVariation(id = nextId, tracks = com.soundstage.mixer.model.createDefaultStepDrumTracks())
        _uiState.update { state ->
            val updatedMap = state.stepDrumState.variations.toMutableMap()
            updatedMap[nextId] = newVar
            state.copy(stepDrumState = state.stepDrumState.copy(variations = updatedMap, activeVariationId = nextId))
        }
    }

    fun duplicateStepDrumVariation(sourceId: String) {
        val currState = _uiState.value.stepDrumState
        val sourceVar = currState.variations[sourceId] ?: return
        val availableIds = listOf("A", "B", "C", "D", "E", "F", "G", "H")
        val nextId = availableIds.firstOrNull { it !in currState.variations.keys } ?: return
        val duplicatedVar = sourceVar.copy(id = nextId)
        _uiState.update { state ->
            val updatedMap = state.stepDrumState.variations.toMutableMap()
            updatedMap[nextId] = duplicatedVar
            state.copy(stepDrumState = state.stepDrumState.copy(variations = updatedMap, activeVariationId = nextId))
        }
    }

    fun toggleStepDrumStep(trackIndex: Int, stepIndex: Int) {
        _uiState.update { state ->
            val currState = state.stepDrumState
            val curVar = currState.variations[currState.activeVariationId] ?: com.soundstage.mixer.model.PatternVariation(id = currState.activeVariationId, tracks = com.soundstage.mixer.model.createDefaultStepDrumTracks())
            val updatedTracks = curVar.tracks.mapIndexed { tIdx, track ->
                if (tIdx == trackIndex) {
                    val updatedSteps = track.steps.mapIndexed { sIdx, step ->
                        if (sIdx == stepIndex) {
                            step.copy(enabled = !step.enabled)
                        } else step
                    }
                    track.copy(steps = updatedSteps)
                } else track
            }
            val updatedVar = curVar.copy(tracks = updatedTracks)
            val updatedMap = currState.variations.toMutableMap()
            updatedMap[currState.activeVariationId] = updatedVar
            state.copy(stepDrumState = currState.copy(variations = updatedMap))
        }
    }

    fun setStepDrumStepVelocity(trackIndex: Int, stepIndex: Int, velocity: Int) {
        _uiState.update { state ->
            val currState = state.stepDrumState
            val curVar = currState.variations[currState.activeVariationId] ?: return@update state
            val updatedTracks = curVar.tracks.mapIndexed { tIdx, track ->
                if (tIdx == trackIndex) {
                    val updatedSteps = track.steps.mapIndexed { sIdx, step ->
                        if (sIdx == stepIndex) step.copy(velocity = velocity.coerceIn(1, 127), enabled = true) else step
                    }
                    track.copy(steps = updatedSteps)
                } else track
            }
            val updatedVar = curVar.copy(tracks = updatedTracks)
            val updatedMap = currState.variations.toMutableMap()
            updatedMap[currState.activeVariationId] = updatedVar
            state.copy(stepDrumState = currState.copy(variations = updatedMap))
        }
    }

    fun setStepDrumStepRepeat(trackIndex: Int, stepIndex: Int, repeat: Int) {
        _uiState.update { state ->
            val currState = state.stepDrumState
            val curVar = currState.variations[currState.activeVariationId] ?: return@update state
            val updatedTracks = curVar.tracks.mapIndexed { tIdx, track ->
                if (tIdx == trackIndex) {
                    val updatedSteps = track.steps.mapIndexed { sIdx, step ->
                        if (sIdx == stepIndex) step.copy(repeatCount = repeat.coerceIn(1, 4)) else step
                    }
                    track.copy(steps = updatedSteps)
                } else track
            }
            val updatedVar = curVar.copy(tracks = updatedTracks)
            val updatedMap = currState.variations.toMutableMap()
            updatedMap[currState.activeVariationId] = updatedVar
            state.copy(stepDrumState = currState.copy(variations = updatedMap))
        }
    }

    fun setStepDrumStepChance(trackIndex: Int, stepIndex: Int, chance: Int) {
        _uiState.update { state ->
            val currState = state.stepDrumState
            val curVar = currState.variations[currState.activeVariationId] ?: return@update state
            val updatedTracks = curVar.tracks.mapIndexed { tIdx, track ->
                if (tIdx == trackIndex) {
                    val updatedSteps = track.steps.mapIndexed { sIdx, step ->
                        if (sIdx == stepIndex) step.copy(chance = chance.coerceIn(0, 100)) else step
                    }
                    track.copy(steps = updatedSteps)
                } else track
            }
            val updatedVar = curVar.copy(tracks = updatedTracks)
            val updatedMap = currState.variations.toMutableMap()
            updatedMap[currState.activeVariationId] = updatedVar
            state.copy(stepDrumState = currState.copy(variations = updatedMap))
        }
    }

    fun setStepDrumTrackLoopLength(trackIndex: Int, length: Int) {
        _uiState.update { state ->
            val currState = state.stepDrumState
            val curVar = currState.variations[currState.activeVariationId] ?: return@update state
            val updatedTracks = curVar.tracks.mapIndexed { tIdx, track ->
                if (tIdx == trackIndex) track.copy(loopLength = length.coerceIn(1, 16)) else track
            }
            val updatedVar = curVar.copy(tracks = updatedTracks)
            val updatedMap = currState.variations.toMutableMap()
            updatedMap[currState.activeVariationId] = updatedVar
            state.copy(stepDrumState = currState.copy(variations = updatedMap))
        }
    }

    fun toggleStepDrumTrackMute(trackIndex: Int) {
        _uiState.update { state ->
            val currState = state.stepDrumState
            val curVar = currState.variations[currState.activeVariationId] ?: return@update state
            val updatedTracks = curVar.tracks.mapIndexed { tIdx, track ->
                if (tIdx == trackIndex) track.copy(isMuted = !track.isMuted) else track
            }
            val updatedVar = curVar.copy(tracks = updatedTracks)
            val updatedMap = currState.variations.toMutableMap()
            updatedMap[currState.activeVariationId] = updatedVar
            state.copy(stepDrumState = currState.copy(variations = updatedMap))
        }
    }

    fun toggleStepDrumTrackSolo(trackIndex: Int) {
        _uiState.update { state ->
            val currState = state.stepDrumState
            val curVar = currState.variations[currState.activeVariationId] ?: return@update state
            val updatedTracks = curVar.tracks.mapIndexed { tIdx, track ->
                if (tIdx == trackIndex) track.copy(isSolo = !track.isSolo) else track
            }
            val updatedVar = curVar.copy(tracks = updatedTracks)
            val updatedMap = currState.variations.toMutableMap()
            updatedMap[currState.activeVariationId] = updatedVar
            state.copy(stepDrumState = currState.copy(variations = updatedMap))
        }
    }

    fun setStepDrumEditMode(mode: com.soundstage.mixer.model.StepDrumMode) {
        _uiState.update { it.copy(stepDrumState = it.stepDrumState.copy(editMode = mode)) }
    }

    fun setStepDrumViewMode(mode: com.soundstage.mixer.model.StepDrumViewMode) {
        _uiState.update { it.copy(stepDrumState = it.stepDrumState.copy(viewMode = mode)) }
    }

    fun triggerStepDrumFill() {
        _uiState.update {
            it.copy(stepDrumState = it.stepDrumState.copy(isFillActive = !it.stepDrumState.isFillActive))
        }
    }

    fun triggerStepDrumBreak() {
        _uiState.update {
            it.copy(stepDrumState = it.stepDrumState.copy(isBreakActive = !it.stepDrumState.isBreakActive))
        }
    }

    fun toggleStepDrumAutoFill() {
        _uiState.update {
            it.copy(stepDrumState = it.stepDrumState.copy(fillAutoEvery4 = !it.stepDrumState.fillAutoEvery4))
        }
    }

    fun selectSequenceMeasure(measureIdx: Int) {
        _uiState.update {
            it.copy(stepDrumState = it.stepDrumState.copy(activeMeasureIndex = measureIdx))
        }
    }

    fun setSequenceBlockVariation(measureIdx: Int, varId: String) {
        _uiState.update { state ->
            val blocks = state.stepDrumState.sequenceBlocks.mapIndexed { idx, block ->
                if (idx == measureIdx) block.copy(variationId = varId) else block
            }
            state.copy(stepDrumState = state.stepDrumState.copy(sequenceBlocks = blocks))
        }
    }

    fun toggleSequenceBlockFill(measureIdx: Int) {
        _uiState.update { state ->
            val blocks = state.stepDrumState.sequenceBlocks.mapIndexed { idx, block ->
                if (idx == measureIdx) block.copy(isFill = !block.isFill) else block
            }
            state.copy(stepDrumState = state.stepDrumState.copy(sequenceBlocks = blocks))
        }
    }

    fun toggleSequenceBlockBreak(measureIdx: Int) {
        _uiState.update { state ->
            val blocks = state.stepDrumState.sequenceBlocks.mapIndexed { idx, block ->
                if (idx == measureIdx) block.copy(isBreak = !block.isBreak) else block
            }
            state.copy(stepDrumState = state.stepDrumState.copy(sequenceBlocks = blocks))
        }
    }

    fun clearStepDrumPattern() {
        _uiState.update { state ->
            val currState = state.stepDrumState
            val curVar = currState.variations[currState.activeVariationId] ?: return@update state
            val clearedTracks = curVar.tracks.map { track ->
                track.copy(steps = List(16) { com.soundstage.mixer.model.StepCell() })
            }
            val updatedVar = curVar.copy(tracks = clearedTracks)
            val updatedMap = currState.variations.toMutableMap()
            updatedMap[currState.activeVariationId] = updatedVar
            state.copy(stepDrumState = currState.copy(variations = updatedMap))
        }
    }

    fun copyStepDrumPattern() {
        val currState = _uiState.value.stepDrumState
        copiedVariation = currState.variations[currState.activeVariationId]
    }

    fun pasteStepDrumPattern() {
        val copied = copiedVariation ?: return
        _uiState.update { state ->
            val currState = state.stepDrumState
            val updatedVar = copied.copy(id = currState.activeVariationId)
            val updatedMap = currState.variations.toMutableMap()
            updatedMap[currState.activeVariationId] = updatedVar
            state.copy(stepDrumState = currState.copy(variations = updatedMap))
        }
    }

    // ================= THEME SELECTION =================
    fun selectTheme(theme: AppTheme) {
        _uiState.update { it.copy(currentTheme = theme) }
        persistCurrentStateDebounced()
    }

    fun cycleTheme() {
        val themes = AppTheme.values()
        val currentIndex = themes.indexOf(_uiState.value.currentTheme)
        val nextTheme = themes[(currentIndex + 1) % themes.size]
        selectTheme(nextTheme)
    }

    private fun startStepDrumClock() {
        stepDrumJob?.cancel()
        stepDrumJob = viewModelScope.launch(Dispatchers.Default) {
            var step = 0
            while (coroutineContext[kotlinx.coroutines.Job]?.isActive == true && _uiState.value.stepDrumState.isPlaying) {
                val bpm = _uiState.value.bpm.coerceIn(40, 300)
                val baseStepDurationMs = ((60000.0 / bpm) / 4.0)
                val swingPercent = _uiState.value.drumFeelSwing.coerceIn(0, 100)
                // Swing delay calculation: swing offsets even steps (off-beats)
                val swingFactor = (swingPercent - 50) / 100.0 // -0.5 to +0.5
                val currentStepDurationMs = if (step % 2 == 0) {
                    (baseStepDurationMs * (1.0 + swingFactor * 0.5)).toLong().coerceAtLeast(10L)
                } else {
                    (baseStepDurationMs * (1.0 - swingFactor * 0.5)).toLong().coerceAtLeast(10L)
                }

                val state = _uiState.value
                val drumState = state.stepDrumState
                val activeVar = drumState.variations[drumState.activeVariationId]

                _uiState.update {
                    it.copy(stepDrumState = it.stepDrumState.copy(currentStepIndex = step))
                }

                if (activeVar != null && !drumState.isBreakActive) {
                    val anySolo = activeVar.tracks.any { it.isSolo }
                    activeVar.tracks.forEach { track ->
                        val shouldAudition = if (anySolo) track.isSolo else !track.isMuted
                        if (shouldAudition) {
                            val activeStepIndex = step % track.loopLength
                            val cell = track.steps.getOrNull(activeStepIndex)
                            if (cell != null && cell.enabled) {
                                val shouldPlay = if (cell.chance >= 100) true else (kotlin.random.Random.nextInt(100) < cell.chance)
                                if (shouldPlay) {
                                    val padId = track.trackIndex % 16
                                    onDrumPadPressed(padId)
                                    viewModelScope.launch {
                                        kotlinx.coroutines.delay(80)
                                        onDrumPadReleased(padId)
                                    }
                                }
                            }
                        }
                    }
                }

                step = (step + 1) % 16
                kotlinx.coroutines.delay(currentStepDurationMs)
            }
        }
    }
}
