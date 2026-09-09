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
    
    // Real Storage / File Manager State
    val storageBaseDirPath: String = "",
    val realSoundfonts: List<StorageItem> = emptyList(),
    val realLoopFiles: List<StorageItem> = emptyList(),
    val realDrumPadFiles: List<StorageItem> = emptyList(),
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
    val selectedBeatCount: Int = 4,
    val loopFolders: List<LoopFolder> = emptyList(),
    val activeLoopFile: LoopFile? = null,
    val lastSelectedLoopFile: LoopFile? = null,
    val activeStorageLoopItem: StorageItem? = null,
    val editingLoopFile: LoopFile? = null,
    val loopEditorStartMs: Int = 0,
    val loopEditorEndMs: Int = 0,
    val loopEditorBeats: Int = 4,
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
    val pressedKeys: Set<String> = emptySet(),
    val keyboardKeyScale: Float = 1.0f,
    val keyboardScrollOffset: Float = 0f,
    val pitchBend: Float = 0.0f,
    
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
    val appFolder: String = "/LiveKeys",
    
    // System & Audio Engine Settings
    val audioEngine: String = "Oboe (C++)",
    val audioBufferSize: Int = 128,
    val polyphony: Int = 128,
    val selectedLanguage: String = "Français",
    val globalVelocityMin: Float = 0.10f,
    val globalVelocityMax: Float = 1.0f,
    
    // Master FX SoundGoodizer (FL Studio Engine)
    val soundGoodizer: Float = 0.45f,
    val soundGoodizerMode: SoundGoodizerMode = SoundGoodizerMode.A,
    val masterPunch: Float = 0.55f,
    val spatialWidener: Float = 0.38f,
    
    // Connected MIDI Devices
    val midiDevices: List<MidiDeviceItem> = emptyList()
) {
    val soundfontFiles: List<StorageItem> get() = realSoundfonts
    val loopAudioFiles: List<StorageItem> get() = realLoopFiles
    val drumPadAudioFiles: List<StorageItem> get() = realDrumPadFiles
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

    private var peakMeterJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var lastTapTimeMap = mutableMapOf<Int, Long>()

    init {
        startPeakMeterSimulation()
        refreshStorageFiles()
        
        // Start Native FluidSynth engine if available
        NativeAudioBridge.safeStartEngine()

        // Apply initial buffer size and polyphony to native engine
        audioEngine.setBufferSize(_uiState.value.audioBufferSize)
        audioEngine.setPolyphony(_uiState.value.polyphony)

        // Sync initial track volumes and pans to NativeAudioBridge (channels 0 to 7)
        _uiState.value.tracks.forEachIndexed { index, track ->
            NativeAudioBridge.safeSetTrackVolume(index, track.volume)
            NativeAudioBridge.safeSetTrackPan(index, track.pan)
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
            restoreSavedAppState()
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
                                    reverbMix = savedT.reverbMix
                                )
                            } else currentTrack
                        }
                    } else state.tracks

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
                        drumPads = restoredDrums
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

                saved.tracks.forEach { t ->
                    val ch = (t.id - 1).coerceIn(0, 7)
                    NativeAudioBridge.safeSetTrackVolume(ch, t.volume)
                    NativeAudioBridge.safeSetTrackPan(ch, t.pan)
                    audioEngine.setChannelReverb(ch, t.reverbMix)
                }

                var hasLoadedAnySlot = false
                if (saved.audioSlots.isNotEmpty()) {
                    saved.audioSlots.forEach { savedSlot ->
                        if (!savedSlot.soundFontPath.isNullOrEmpty() && File(savedSlot.soundFontPath).exists()) {
                            loadSoundFontForSlot(savedSlot.slotId, savedSlot.soundFontPath, savedSlot.bank, savedSlot.preset, savedSlot.patchName)
                            hasLoadedAnySlot = true
                        }
                    }
                }

                // If no slot had a valid SoundFont restored, load the default soundfont on Slot 0
                if (!hasLoadedAnySlot) {
                    val defaultSf = File(fileManager.soundfontsDir, "VintageDreamsWaves-v2.sf2")
                    val fallbackSf = File(getApplication<Application>().filesDir, "LiveKeys/SoundFonts/VintageDreamsWaves-v2.sf2")
                    val sfToLoad = if (defaultSf.exists()) defaultSf else if (fallbackSf.exists()) fallbackSf else null
                    if (sfToLoad != null) {
                        loadSoundFontForSlot(0, sfToLoad.absolutePath, bank = 0, preset = 0)
                    }
                }
            } else {
                // First run / no saved state: load default soundfont on Slot 0
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
            activeSf2TrackId = state.activeSoundfontSlotId,
            lastActivity = "mixer"
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
                name = "Piste $i",
                isEnabled = true,
                volume = 0.65f,
                pan = 0.0f,
                fxSummary = "Fx, EQ...",
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
            val defaultStyle = when (padIdx) {
                1, 2 -> DrumPadStyle.GRADIENT_CYAN
                3, 4 -> DrumPadStyle.LED_AMBER
                5, 6 -> DrumPadStyle.NEON_MAGENTA
                else -> DrumPadStyle.MY_CORAL
            }
            DrumPadItem(
                id = padIdx,
                label = "", // No predefined name at startup per user instructions
                soundType = DrumSoundType.SAMPLE,
                sampleFileName = "sample_$padIdx.wav",
                colorStyle = defaultStyle
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
    fun refreshStorageFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isScanningStorage = true) }
            try {
                fileManager.ensureDirectoriesExist()

                val sfs = fileManager.getSoundFontFiles()
                val loops = fileManager.getLoopFiles()
                val drumPads = fileManager.getDrumPadFiles()
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

                // Automatically load default SoundFont ONLY on Slot 0 (Track 1) at initial launch if completely unassigned
                val defaultSf = sfs.firstOrNull()
                if (defaultSf != null && File(defaultSf.path).exists()) {
                    val slot0 = _uiState.value.audioSlots.getOrNull(0)
                    if (slot0 != null && (slot0.presets.isEmpty() || slot0.soundFontId <= 0 || slot0.soundFontPath.isNullOrEmpty() || !File(slot0.soundFontPath).exists())) {
                        loadSoundFontForSlot(0, defaultSf.path, bank = 0, preset = 0)
                    }
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
                audioEngine.playLoopFile(
                    filePath = path,
                    volume = _uiState.value.loopVolume,
                    beatCount = target.beats.takeIf { b -> b > 0 } ?: _uiState.value.selectedBeatCount,
                    bpm = _uiState.value.bpm,
                    startMs = target.startMs,
                    endMs = target.endMs
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
            val f = java.io.File(fileManager.loopsDir, "${file.folder}/${file.name}".replace("Racine /Loops/", "").replace("Loops/", ""))
            val path = if (f.exists()) f.absolutePath else java.io.File(fileManager.loopsDir, file.name).absolutePath
            audioEngine.playLoopFile(
                filePath = path,
                volume = _uiState.value.loopVolume,
                beatCount = file.beats.takeIf { b -> b > 0 } ?: _uiState.value.selectedBeatCount,
                bpm = _uiState.value.bpm,
                startMs = file.startMs,
                endMs = file.endMs
            )
            _uiState.update {
                it.copy(
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
            audioEngine.playLoopFile(
                filePath = path,
                volume = _uiState.value.loopVolume,
                beatCount = _uiState.value.loopEditorBeats,
                bpm = _uiState.value.bpm,
                startMs = _uiState.value.loopEditorStartMs,
                endMs = _uiState.value.loopEditorEndMs
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
            audioEngine.playLoopFile(path, _uiState.value.loopVolume, updatedFile.beats, _uiState.value.bpm, updatedFile.startMs, updatedFile.endMs)
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

    // ================= THEME SELECTION =================
    fun setAppTheme(theme: AppTheme) {
        _uiState.update { it.copy(currentTheme = theme) }
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
                    delay(40)
                    val curr = _uiState.value
                    val anySolo = curr.tracks.any { it.isSolo }
                    val pressedMidiNotes = curr.pressedKeys.map { noteNameToMidi(it) }

                    _uiState.update { state ->
                        var maxPlayingLevelL = 0f
                        var maxPlayingLevelR = 0f

                        val updatedTracks = state.tracks.map { track ->
                            val isAllowedBySolo = if (anySolo) track.isSolo else true
                            val isTrackActive = track.isEnabled && !track.isMuted && isAllowedBySolo

                            val isPlayingSound = if (!isTrackActive || pressedMidiNotes.isEmpty() || track.soundfontName.isEmpty()) {
                                false
                            } else if (state.isSplitterActive) {
                                pressedMidiNotes.any { midi -> midi in track.splitNoteMin..track.splitNoteMax }
                            } else {
                                true
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

                            if (newL > maxPlayingLevelL) maxPlayingLevelL = newL
                            if (newR > maxPlayingLevelR) maxPlayingLevelR = newR

                            track.copy(peakMeterL = newL, peakMeterR = newR)
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

                        state.copy(
                            tracks = updatedTracks,
                            masterTrack = state.masterTrack.copy(peakMeterL = masterL, peakMeterR = masterR)
                        )
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.e("MixerViewModel", "Error in peak meter calculation: ${e.message}")
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
            val slotId = _uiState.value.activeSoundfontSlotId
            val channel = midiChannelForSlot(slotId)
            _uiState.value.pressedKeys.forEach { key ->
                audioEngine.noteOff(key, channel)
            }
            audioEngine.globalOctaveShift = newOctave
            _uiState.update { it.copy(octave = newOctave, pressedKeys = emptySet()) }
            persistCurrentStateDebounced()
        }
    }

    fun updateBpm(delta: Int) {
        val newBpm = (_uiState.value.bpm + delta).coerceIn(20, 300)
        _uiState.update { it.copy(bpm = newBpm) }
        if (_uiState.value.isMetronomeOn) {
            audioEngine.startMetronome(newBpm, _uiState.value.metronomeSignature, _uiState.value.metronomeVolume)
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

    fun setMetronomeVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _uiState.update { it.copy(metronomeVolume = clamped) }
        if (_uiState.value.isMetronomeOn) {
            audioEngine.startMetronome(_uiState.value.bpm, _uiState.value.metronomeSignature, clamped)
        }
    }

    // ================= SUSTAIN, PANIC & SPLITTER =================
    fun toggleSustain() {
        _uiState.update { state ->
            val nextSustain = !state.isSustainActive
            val updatedKeys = if (!nextSustain && !state.isMidiPedalPressed) emptySet() else state.pressedKeys
            state.copy(
                isSustainActive = nextSustain,
                pressedKeys = updatedKeys
            )
        }
    }

    fun toggleSplitter() {
        _uiState.update { it.copy(isSplitterActive = !it.isSplitterActive) }
    }

    fun triggerPanic() {
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
    fun cycleKeyboardExpansion() {
        _uiState.update { state ->
            val nextFraction = when {
                state.keyboardHeightFraction <= 0.05f -> 0.42f
                state.keyboardHeightFraction < 0.65f -> 0.70f
                else -> 0f
            }
            state.copy(keyboardHeightFraction = nextFraction)
        }
    }

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
            state.copy(keyboardHeightFraction = fraction.coerceIn(0f, 1f))
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

        // If keyboard splitter is active (Split point at C4 = 60)
        if (state.isSplitterActive && midiNote != null) {
            val splitNote = 60
            return if (midiNote < splitNote) {
                // Lower hand (Bass / Track 1)
                listOf(0)
            } else {
                // Upper hand (Layered active tracks 2..8)
                val upperChannels = state.tracks.mapIndexedNotNull { idx, track ->
                    if (idx > 0 && track.isEnabled && !track.isMuted && (!anySolo || track.isSolo)) {
                        val slot = state.audioSlots.getOrNull(idx)
                        if (slot != null && slot.soundFontId > 0) idx else null
                    } else null
                }
                if (upperChannels.isNotEmpty()) upperChannels else listOf(midiChannelForSlot(state.activeSoundfontSlotId))
            }
        }

        // Standard Full Performance Layering:
        // Play on ALL enabled & unmuted tracks (1..8) that have a loaded SoundFont
        val activeChannels = state.tracks.mapIndexedNotNull { idx, track ->
            val isAllowed = track.isEnabled && !track.isMuted && (!anySolo || track.isSolo)
            if (isAllowed) {
                val slot = state.audioSlots.getOrNull(idx)
                if (slot != null && slot.soundFontId > 0) idx else null
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
                val factor = (0.5f - curve) * 2f
                (Math.pow(v.toDouble(), (1.0 - factor * 0.6).coerceAtLeast(0.3))).toFloat()
            }
            curve > 0.52f -> {
                val factor = (curve - 0.5f) * 2f
                (Math.pow(v.toDouble(), (1.0 + factor * 1.5))).toFloat()
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
        if (!_uiState.value.isSustainActive && !_uiState.value.isMidiPedalPressed) {
            channels.forEach { channel ->
                audioEngine.noteOff(key, channel)
            }
        }
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
            audioEngine.playDrumPadSound(pad, _uiState.value.drumVolume)
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

    fun updateDrumPadCustomization(padId: Int, label: String, style: DrumPadStyle) {
        _uiState.update { state ->
            val updated = state.drumPads.map { pad ->
                if (pad.id == padId) {
                    pad.copy(label = label.take(12), colorStyle = style)
                } else pad
            }
            state.copy(drumPads = updated)
        }
    }

    fun assignDrumSample(padId: Int, sampleName: String, samplePath: String = "") {
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
                        label = sampleName.substringBeforeLast(".").take(8)
                    )
                } else pad
            }
            state.copy(drumPads = updated)
        }
        persistCurrentStateDebounced()
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

    // ================= TONIC PAD CONTROLS =================
    fun toggleMultiPad() {
        _uiState.update { it.copy(isMultiPadEnabled = !it.isMultiPadEnabled) }
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

    fun loadSoundFontForSlot(slotId: Int, sf2Path: String, bank: Int = 0, preset: Int = 0, patchName: String? = null) {
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

                // 1. Charger d'abord le nouveau SoundFont via le chemin natif garanti sur TOUS les moteurs (Fader, Pad, Drum)
                val newSfId = NativeAudioBridge.safeLoadSoundFont(NativeAudioBridge.ENGINE_FADER, readablePath)
                NativeAudioBridge.safeLoadSoundFont(NativeAudioBridge.ENGINE_PAD, readablePath)
                NativeAudioBridge.safeLoadSoundFont(NativeAudioBridge.ENGINE_DRUM, readablePath)
                Log.d("SoundFontLoad", "[DIAGNOSTIC] Native safeLoadSoundFont returned ID=$newSfId for slot=$slotId")

                // 2. Décharger l'ancien UNIQUEMENT si le nouveau a réussi et que l'ancien n'est plus utilisé nulle part
                if (newSfId >= 0 && oldSfId > 0 && oldSfId != newSfId) {
                    val inUse = _uiState.value.audioSlots.any { it.slotId != slotId && it.soundFontId == oldSfId }
                    if (!inUse) {
                        NativeAudioBridge.safeUnloadSoundFont(NativeAudioBridge.ENGINE_FADER, oldSfId)
                        NativeAudioBridge.safeUnloadSoundFont(NativeAudioBridge.ENGINE_PAD, oldSfId)
                        NativeAudioBridge.safeUnloadSoundFont(NativeAudioBridge.ENGINE_DRUM, oldSfId)
                    }
                }

                // 3. Récupérer la liste des presets du SoundFont chargé en bornant strictly 0..127 par banque
                val effectiveSfId = if (newSfId >= 0) newSfId else oldSfId
                val nativePresets = if (effectiveSfId >= 0 && NativeAudioBridge.isNativeReady()) {
                    NativeAudioBridge.listPresets(effectiveSfId).toList()
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
                    if (slotId == 8) {
                        NativeAudioBridge.safeSelectProgram(
                            engineIndex = NativeAudioBridge.ENGINE_DRUM,
                            channel = 9,
                            soundFontId = effectiveSfId,
                            bank = targetPreset.bankNumber,
                            preset = targetPreset.id
                        )
                    } else if (slotId == 9) {
                        NativeAudioBridge.safeSelectProgram(
                            engineIndex = NativeAudioBridge.ENGINE_PAD,
                            channel = 0,
                            soundFontId = effectiveSfId,
                            bank = targetPreset.bankNumber,
                            preset = targetPreset.id
                        )
                    }
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

                    state.copy(
                        audioSlots = updatedSlots,
                        tracks = updatedTracks
                    )
                }
                persistCurrentState()
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
            if (slotId == 8) {
                NativeAudioBridge.safeSelectProgram(
                    NativeAudioBridge.ENGINE_DRUM,
                    9,
                    slot.soundFontId,
                    preset.bankNumber,
                    preset.id
                )
            } else if (slotId == 9) {
                NativeAudioBridge.safeSelectProgram(
                    NativeAudioBridge.ENGINE_PAD,
                    0,
                    slot.soundFontId,
                    preset.bankNumber,
                    preset.id
                )
            }
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

            state.copy(audioSlots = updatedSlots, tracks = updatedTracks)
        }
        persistCurrentState()
    }

    fun loadSoundfontFromStorage(file: StorageItem) {
        loadSoundFontForSlot(_uiState.value.activeSoundfontSlotId, file.path)
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
        _uiState.update { it.copy(selectedLanguage = lang) }
    }

    fun setGlobalVelocityRange(min: Float, max: Float) {
        val safeMin = min.coerceIn(0f, 1f)
        val safeMax = max.coerceIn(0f, 1f)
        _uiState.update { it.copy(globalVelocityMin = safeMin, globalVelocityMax = safeMax) }
        audioEngine.globalVelocityMin = safeMin
        audioEngine.globalVelocityMax = safeMax
    }

    fun setSoundGoodizer(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        _uiState.update { it.copy(soundGoodizer = clamped) }
        audioEngine.soundGoodizerAmount = clamped
    }

    fun setSoundGoodizerMode(mode: SoundGoodizerMode) {
        _uiState.update { it.copy(soundGoodizerMode = mode) }
        audioEngine.soundGoodizerMode = mode.name
    }

    fun setMasterPunch(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        _uiState.update { it.copy(masterPunch = clamped) }
        audioEngine.masterPunch = clamped
    }

    fun setSpatialWidener(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        _uiState.update { it.copy(spatialWidener = clamped) }
        audioEngine.spatialWidener = clamped
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
            } else if (trackId in 1..8) {
                val channel = trackId - 1
                audioEngine.setChannelReverb(channel, if (newFx.isReverbEnabled) newFx.reverbMix else 0f)
            }
            state.copy(fxParameters = updatedMap)
        }
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
    }

    private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)

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
}
