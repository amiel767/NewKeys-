package com.soundstage.mixer.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soundstage.mixer.audio.NativeAudioBridge
import com.soundstage.mixer.model.*
import com.soundstage.mixer.ui.components.VelocityConfig
import com.soundstage.mixer.ui.components.VelocityProcessor
import com.soundstage.mixer.ui.components.TrackColorMode
import com.soundstage.mixer.ui.components.TrackColorsLogic
import com.soundstage.mixer.ui.components.TrackPalettes
import com.soundstage.mixer.ui.theme.*
import com.soundstage.mixer.ui.setlists.*
import com.soundstage.mixer.ui.setlists.SetlistColor
import com.soundstage.mixer.ui.settings.*
import com.soundstage.mixer.ui.components.drumpad.DpPalette
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
        volume = 0.85f,
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
    // Virtual keyboard theme + pressed-key style (persisted, keys vkb_theme / vkb_press_style)
    val vkbTheme: String = "classic",
    val vkbPressStyle: String = "color",
    
    // Fullscreen Pages Navigation
    val currentPage: AppScreenPage = AppScreenPage.SETLISTS,
    val visibleTracksCount: Int = 1,
    val activeProjectName: String = "",
    
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
    val drumFeelSwing: Int = 54,
    val activeDrumKitName: String = "DrumKit",
    val activeTonicPadName: String = "Analog Pad",
    val isDrumLoopArmed: Boolean = false,
    val isDrumLoopRecording: Boolean = false,
    val drumLoopBars: Int = 2,
    val drumTimeSignature: String = "4/4",
    val isDrumLoopRendering: Boolean = false,
    
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
    
    // Soundfonts & Scenes / Setlists & Patches
    val activeSf2Tab: String = "bank",
    val soundfontBankFiles: List<SoundfontBankFile> = emptyList(),
    val scenes: List<ScenePreset> = emptyList(),
    val activeSceneId: String = "",
    val patches: List<PatchItem> = emptyList(),
    val activePatchId: String? = null,
    
    // Settings Drawer (Material Expressive AOSP style)
    val isSheetsDrawerOpen: Boolean = false,
    val isLowLatencyAudio: Boolean = true,
    val isKeyboardVelocityTouch: Boolean = true,
    val isMetronomeInRec: Boolean = false,
    val useFlats: Boolean = false,
    val appFolder: String = "/LiveKeys",
    
    // System & Audio Engine Settings
    val audioEngine: String = "Oboe (C++)",
    val audioBufferSize: Int = 256,
    val polyphony: Int = 128,
    val fluidSynthGain: Float = 1.0f,
    val selectedLanguage: String = "English",
    val selectedAppLanguage: AppLanguage = AppLanguage.ENGLISH,
    val globalVelocityMin: Float = 0.10f,
    val globalVelocityMax: Float = 1.0f,
    
    // Master FX SoundGoodizer (FL Studio Engine)
    val soundGoodizer: Float = 0.45f,
    val soundGoodizerMode: SoundGoodizerMode = SoundGoodizerMode.A,
    val masterPunch: Float = 0.0f,
    val spatialWidener: Float = 0.38f,
    
    // Connected MIDI Devices
    val midiDevices: List<MidiDeviceItem> = emptyList(),

    // MIDI Mapping & MIDI Learn
    val midiMappings: List<MidiMappingItem> = emptyList(),
    val learningActionType: MidiActionType? = null,
    val lastMidiSummary: String = "",
    val lastMidiTriggerTime: Long = 0L,
    val selectedMidiOutputDevice: String = "All Connected Devices",

    // MIDI mapping page (inside SettingsScreen, never a window)
    val midiMappingOpen: Boolean = false,
    val settingsTab: SettingsTab = SettingsTab.Audio,
    val settingsQuery: String = "",

    // Setlists Screen State
    val lastOpenedSetlistId: String? = null,
    val setlistsQuery: String = "",
    val setlistsSort: SetlistSort = SetlistSort.Recent,
    val setlistsOnlyPinned: Boolean = false,
    val setlistsPrefs: SetlistsPrefs = SetlistsPrefs(),
    val isSetlistsLoading: Boolean = false,
    val lastDeletedScene: ScenePreset? = null,
    
    // Screen & Scale Settings
    val keepScreenOn: Boolean = true,
    val selectedScaleMode: String = "Majeur",
    val headroom: Float = 1.00f,
    val antiJitter: Boolean = false,
    val jitterWindowMs: Int = 15,
    
    // Live Notes & Chords / Sheets Library
    val notesText: String = "",
    val sheets: List<SheetItem> = emptyList(),
    val activeSheetId: String? = null,
    val floatingSheet: SheetItem? = null,
    val floatingSheetIsLeft: Boolean = false,
    val floatingSheetScale: Float = 1.0f,

    // Snapshots / Sub-Scenes (Section 2)
    val isSnapshotArmMode: Boolean = false,
    val activeSnapshotSlot: String? = null,
    val snapshots: Map<String, SubSceneSnapshot> = emptyMap(),
    val configuredSnapshotSlots: List<String> = listOf("slot_default"),
    val customLibreTracks: List<TrackChannel>? = null,
    val snapshotTransitionProgress: Float = 1.0f,
    val snapshotCustomNames: Map<String, String> = emptyMap(),

    // In-App File Browser Persistence (Section 4)
    val lastLoopsPath: String = "",
    val lastDrumPadPath: String = "",
    val showStoragePermissionDialog: Boolean = false,

    // Dynamic Theme Customization & Fader Layout Mode
    val isFaderAutoFit: Boolean = false,
    val themeHue: Float = 220f,
    val themeSaturation: Float = 0.40f,
    val themeNuance: Float = 0.45f,
    val themeAccentSaturation: Float = 1.0f,
    val themeBgSaturation: Float = 1.60f,
    val themeBgLightness: Float = 1.0f,
    val themeModulesLightness: Float = 1.0f,
    val themePaletteName: String = "Midnight Slate (Default)",
    val trackColorSaturation: Float = 1.0f,

    // Track colours (Settings > Interface > Track colors). Persisted.
    val trackColorMode: TrackColorMode = TrackColorMode.Palette,
    val trackColorPaletteId: String = TrackPalettes.Default.id,
    /** Frozen random assignment (8 ARGB), used when the mode is Random. */
    val trackRandomColorArgb: List<Int> = emptyList(),
    /** The one colour used for every lane when the mode is Single. */
    val trackSingleColorArgb: Int = 0xFF8FB2E8.toInt(),
    /** Very slow automatic hue drift of every lane. */
    val trackColorDynamic: Boolean = false,
    /** Sub-page flag: the Track colors page replaces the Interface tab body. */
    val trackColorsOpen: Boolean = false,

    // Loop Player & AOSP Internal Storage Browser Module
    val sfFavorites: Set<String> = emptySet(),
    val sfCategoryOverrides: Map<String, String> = emptyMap(),
    val sfParsedPresets: Map<String, List<com.soundstage.mixer.model.SF2PresetInfo>> = emptyMap(),
    val sfParsingFiles: Set<String> = emptySet(),
    val sfLoadingFileId: String? = null,
    val sfLoadingProgress: Float? = null,
    val sfImportingName: String? = null,
    val sfImportingProgress: Float? = null,
    val hapticsEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val showLanguageDialog: Boolean = false,

    // Transport global
    val transportBpm: Int = 120,
    val transportSignature: String = "4/4",
    val transportSubdivision: String = "1/4",

    // Metronome
    val metronomeSound: String = "Woodblock",
    val metronomeAccent1: Boolean = true,
    val metronomeAccent13: Boolean = false,
    val metronomeVisualFlash: Boolean = true,
    val metronomeCountIn: String = "off",  // "off" | "1" | "2"
    val metronomeTapBpm: Int = 0,

    // FX chains
    val fxChains: com.soundstage.mixer.model.FxChainsState = com.soundstage.mixer.model.FxChainsState.DEFAULT,

    // Looper (miroir de LooperEngine, expose pour l'UI et la persistance)
    val looper: com.soundstage.mixer.audio.LooperEngine.UiState = com.soundstage.mixer.audio.LooperEngine.UiState(),

    // LiveLooper (nouvelle version) : etat complet du LooperController.
    val liveLooperState: com.soundstage.mixer.looper.LooperUiState = com.soundstage.mixer.looper.LooperUiState(),

    // DrumPad v5
    val padCount: Int = 8,
    val kits: List<com.soundstage.mixer.model.DrumKitData> = com.soundstage.mixer.model.DrumKitData.defaults(),

    val activeTrackSettingsId: Int? = null
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

    // Transport clock partage par tous les composants rythmiques
    private val transportClock = com.soundstage.mixer.audio.TransportClock()

    // Moteur metronome (synthese 6 sons, scheduler precis)
    private val metronomeEngine = com.soundstage.mixer.audio.MetronomeEngine(transportClock)

    // Looper event-only, 8 cases, 4 couches
    private val looperEngine = com.soundstage.mixer.audio.LooperEngine(transportClock)

    // LiveLooper (nouvelle version) : modele + logique pure + controleur.
    // Alimente en horloge par le TransportClock (en bars) et rejoue via l'AudioEngine.
    private val liveLooper = com.soundstage.mixer.looper.LooperController(
        clock = { transportClock.currentBarPosition() },
        initialPatches = com.soundstage.mixer.looper.LoopPatchJson.fromJson(
            readLiveLooperPatchesJson()
        ).first,
        initialActiveId = com.soundstage.mixer.looper.LoopPatchJson.fromJson(
            readLiveLooperPatchesJson()
        ).second,
        setlistName = appStatePersistence.getString("active_project_name", ""),
        onReplay = { _, event, volume -> replayLoopEvent(event, volume) },
        onPatchesChanged = { patches, activeId ->
            // Patches belong with their loops: written to Loops/LiveLooper/patches.json,
            // with SharedPreferences kept in sync as a fallback for older builds and for
            // the case where external storage is unavailable.
            val json = com.soundstage.mixer.looper.LoopPatchJson.toJson(patches, activeId)
            runCatching {
                if (!fileManager.liveLooperDir.exists()) fileManager.liveLooperDir.mkdirs()
                fileManager.liveLooperPatchesFile.writeText(json)
            }
            appStatePersistence.saveString("live_looper_patches", json)
        }
    )

    /**
     * Reads the LiveLooper patches from `Loops/LiveLooper/patches.json`, migrating the
     * older SharedPreferences copy on first run and never throwing on a bad file.
     */
    private fun readLiveLooperPatchesJson(): String {
        val file = fileManager.liveLooperPatchesFile
        if (file.exists()) {
            val text = runCatching { file.readText() }.getOrNull()
            if (!text.isNullOrBlank()) return text
        }
        return appStatePersistence.getString("live_looper_patches", "")
    }

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

    private var engineStarted = false

    private fun ensureEngineStarted() {
        if (engineStarted) return
        engineStarted = true
        viewModelScope.launch(Dispatchers.Default) {
            NativeAudioBridge.safeStartEngine()
            audioEngine.setBufferSize(_uiState.value.audioBufferSize)
            audioEngine.setPolyphony(_uiState.value.polyphony)
            val masterVol = _uiState.value.masterTrack.volume
            audioEngine.masterVolume = masterVol
            NativeAudioBridge.safeSetMasterVolume(masterVol)
        }
    }

    init {
        startPeakMeterSimulation()

        transportClock.bpm = _uiState.value.transportBpm
        transportClock.signature = com.soundstage.mixer.audio.TransportClock.Signature.parse(_uiState.value.transportSignature)
        transportClock.subdivision = com.soundstage.mixer.audio.TransportClock.Subdivision.parse(_uiState.value.transportSubdivision)

        // Miroir LooperEngine -> UiState
        viewModelScope.launch {
            looperEngine.state.collect { st ->
                _uiState.update { it.copy(looper = st) }
            }
        }

        // Metronome : demarre arrete, sera active via toggleMetronome()
        metronomeEngine.volume = _uiState.value.metronomeVolume
        metronomeEngine.accent1 = _uiState.value.metronomeAccent1
        metronomeEngine.accent13 = _uiState.value.metronomeAccent13
        metronomeEngine.visualFlash = _uiState.value.metronomeVisualFlash
        metronomeEngine.currentSound = com.soundstage.mixer.audio.MetronomeEngine.Sound.parse(_uiState.value.metronomeSound)

        looperEngine.start()

        // LiveLooper : miroir du controleur vers l'UiState + horloge de tick (~8 ms).
        viewModelScope.launch {
            liveLooper.state.collect { st ->
                _uiState.update { it.copy(liveLooperState = st) }
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            // Adaptive tick: the looper only needs sub-frame resolution while a case is
            // recording, playing or waiting for a boundary. Idle it backs off to 120 ms,
            // so an unused LiveLooper no longer wakes the CPU ~125 times a second (which
            // showed up as the device heating up and the UI losing frames).
            while (isActive) {
                val st = liveLooper.state.value
                val busy = st.cases.any {
                    it.mode == com.soundstage.mixer.looper.CaseMode.Rec ||
                        it.mode == com.soundstage.mixer.looper.CaseMode.Play ||
                        it.mode == com.soundstage.mixer.looper.CaseMode.Overdub ||
                        it.queued != null
                }
                runCatching { liveLooper.tick() }
                delay(if (busy) 8L else 120L)
            }
        }

        // B5a: wire LooperEngine callbacks → audio playback
        looperEngine.onPlayPadEvent = playPad@{ idx ->
            val pad = _uiState.value.drumPads.getOrNull(idx)
                ?: return@playPad
            if (pad.sampleFileName.isNotEmpty()) {
                audioEngine.playDrumPadSound(
                    pad, pad.volume * _uiState.value.drumVolume
                )
            }
        }
        // Replays a recorded note on every channel the track performs on, so a
        // layered/split setup sounds the same live and on playback.
        looperEngine.onPlayNoteEvent = playNote@{ midi ->
            val key = com.soundstage.mixer.ui.components.KeyPositionMapper.midiToNoteName(midi)
            val channels = getActivePerformanceChannels(midi)
            if (channels.isEmpty()) return@playNote
            channels.forEach { channel ->
                val track = _uiState.value.tracks.getOrNull(channel)
                val vel = track?.let { applyVelocityCurve(0.85f, it.velocityCurve) } ?: 0.85f
                audioEngine.noteOn(key, vel, channel)
            }
            // Release shortly after: the looper stores onsets, not durations.
            viewModelScope.launch(Dispatchers.Default) {
                kotlinx.coroutines.delay(220)
                channels.forEach { channel -> audioEngine.noteOff(key, channel) }
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

        audioEngine.onMidiRawEventListener = { channel, command, data1, data2 ->
            handleMidiMappingEvent(channel, command, data1, data2)
        }

        // Connect multi-channel layer performance routing for USB MIDI
        audioEngine.activeLayerChannelsProvider = { midiNote, incomingChannel, deviceId ->
            getActivePerformanceChannels(midiNote, incomingChannel, deviceId)
        }
        audioEngine.trackVelocityCurveProvider = { ch ->
            _uiState.value.tracks.getOrNull(ch)?.velocityCurve ?: 0.5f
        }
        audioEngine.trackTransposeProvider = { ch -> _uiState.value.tracks.getOrNull(ch)?.transpose ?: 0 }
        audioEngine.trackOctaveProvider = { ch -> _uiState.value.tracks.getOrNull(ch)?.octave ?: 0 }
        audioEngine.trackSustainProvider = { ch -> _uiState.value.tracks.getOrNull(ch)?.sustain ?: true }
        audioEngine.trackAftertouchProvider = { ch -> _uiState.value.tracks.getOrNull(ch)?.aftertouch ?: false }
        audioEngine.trackProgramChangeProvider = { ch -> _uiState.value.tracks.getOrNull(ch)?.programChange ?: true }
        audioEngine.mappedProgramChangeProvider = { number, _ ->
            _uiState.value.midiMappings.any { m ->
                m.isEnabled && m.isAssigned &&
                    m.midiType == MidiControlType.PROGRAM_CHANGE && m.controlNumber == number
            }
        }
        audioEngine.trackBendRangeProvider = { ch -> _uiState.value.tracks.getOrNull(ch)?.pitchBendRange ?: 2 }
        _uiState.value.tracks.forEach { track ->
            audioEngine.setTrackVelocityProcessor(track.id - 1, VelocityProcessor(track.velocityConfig))
            applyTrackPlaybackSettings(track)
        }

        // Restore persisted state from previous session
        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.d("MIXER_BOOT", "boot -> currentPage=${_uiState.value.currentPage}")
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
                loadGlobalSheets()
            } catch (e: Exception) {
                Log.w("MixerViewModel", "loadGlobalSheets warning: ${e.message}")
            }
            try {
                refreshStorageFiles()
            } catch (e: Exception) {
                Log.w("MixerViewModel", "refreshStorageFiles warning: ${e.message}")
            }
            android.util.Log.d("MIXER_BOOT", "boot state restored; engineStarted=$engineStarted")
            
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
                // Démarrage: ne jamais restaurer de Setlist active automatiquement.
                // L'utilisateur doit en choisir une explicitement pour que l'audio démarre.
                val hasActive = false
                val restoredProjectName = ""

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
                                    velocityConfig = savedT.velocityConfig,
                                    splitNoteMin = savedT.splitNoteMin,
                                    splitNoteMax = savedT.splitNoteMax,
                                    transpose = savedT.transpose,
                                    octave = savedT.octave,
                                    pitchBendRange = savedT.pitchBendRange,
                                    defaultVolume = savedT.defaultVolume,
                                    defaultPan = savedT.defaultPan,
                                    sustain = savedT.sustain,
                                    aftertouch = savedT.aftertouch,
                                    programChange = savedT.programChange,
                                    selectedMidiDeviceId = savedT.selectedMidiDeviceId,
                                    customColorArgb = savedT.customColorArgb
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

                    val restoredVisibleTracks = saved.visibleTracksCount ?: saved.tracks.count { it.isEnabled }.coerceIn(1, 8)
                    val restoredConfiguredSlots = if (saved.configuredSnapshotSlots.isNotEmpty()) saved.configuredSnapshotSlots else listOf("slot_default")
                    val restoredCustomNames = saved.snapshotCustomNames

                    val restoredPatches = if (saved.patches.isNotEmpty()) saved.patches else state.patches
                    val restoredActivePatchId = saved.activePatchId ?: restoredPatches.firstOrNull()?.id
                    val activePatch = restoredPatches.find { it.id == restoredActivePatchId }
                    val finalSnapshots = if (!activePatch?.snapshots.isNullOrEmpty()) activePatch!!.snapshots else saved.snapshots
                    val finalConfSlots = if (!activePatch?.configuredSnapshotSlots.isNullOrEmpty()) activePatch!!.configuredSnapshotSlots else restoredConfiguredSlots
                    val finalCustomNames = if (!activePatch?.snapshotCustomNames.isNullOrEmpty()) activePatch!!.snapshotCustomNames else restoredCustomNames
                    val finalActiveSlot = activePatch?.activeSnapshotSlot ?: saved.activeSnapshotSlot

                    state.copy(
                        currentTheme = restoredTheme,
                        currentPage = AppScreenPage.SETLISTS,
                        bpm = saved.bpm ?: state.bpm,
                        transpose = saved.transpose ?: state.transpose,
                        octave = saved.octave ?: state.octave,
                        activeSceneId = if (hasActive) restoredProjectName else "",
                        activeProjectName = if (hasActive) restoredProjectName else "",
                        visibleTracksCount = restoredVisibleTracks,
                        soundGoodizer = saved.soundGoodizerAmount ?: state.soundGoodizer,
                        soundGoodizerMode = restoredMode,
                        masterPunch = saved.masterPunch ?: state.masterPunch,
                        spatialWidener = saved.spatialWidener ?: state.spatialWidener,
                        masterTrack = state.masterTrack.copy(volume = saved.masterVolume ?: state.masterTrack.volume),
                        tracks = restoredTracks,
                        audioSlots = restoredSlots,
                        drumPads = restoredDrums,
                        fxParameters = restoredFx,
                        snapshots = finalSnapshots,
                        activeSnapshotSlot = finalActiveSlot,
                        customLibreTracks = null,
                        configuredSnapshotSlots = finalConfSlots,
                        snapshotCustomNames = finalCustomNames,
                        patches = restoredPatches,
                        activePatchId = restoredActivePatchId,
                        tonicBrightness = saved.tonicBrightness ?: state.tonicBrightness,
                        tonicShimmer = saved.tonicShimmer ?: state.tonicShimmer,
                        drumReverb = saved.drumReverb ?: state.drumReverb,
                        keepScreenOn = saved.keepScreenOn ?: state.keepScreenOn,
                        selectedScaleMode = saved.selectedScaleMode ?: state.selectedScaleMode,
                        themeHue = saved.themeHue ?: state.themeHue,
                        themeSaturation = saved.themeSaturation ?: state.themeSaturation,
                        themeNuance = saved.themeNuance ?: state.themeNuance,
                        themePaletteName = saved.themePalette ?: state.themePaletteName,
                        themeAccentSaturation = saved.themeAccentSaturation ?: state.themeAccentSaturation,
                        themeBgSaturation = saved.themeBgSaturation ?: 1.60f,
                        themeBgLightness = saved.themeBgLightness ?: state.themeBgLightness,
                        themeModulesLightness = saved.themeModulesLightness ?: state.themeModulesLightness,
                        trackColorSaturation = saved.trackColorSaturation ?: state.trackColorSaturation,
                        isFaderAutoFit = saved.isFaderAutoFit ?: state.isFaderAutoFit,
                        midiMappings = if (saved.midiMappings.isNotEmpty()) saved.midiMappings else state.midiMappings,
                        settingsTab = runCatching { SettingsTab.valueOf(appStatePersistence.getString("last_settings_tab", SettingsTab.Audio.name)) }.getOrDefault(SettingsTab.Audio),
                        lastOpenedSetlistId = appStatePersistence.getString("setlist_last_opened", "").ifEmpty { null },
                        setlistsSort = runCatching { SetlistSort.valueOf(appStatePersistence.getString("setlists_sort", SetlistSort.Recent.name)) }.getOrDefault(SetlistSort.Recent),
                        setlistsOnlyPinned = appStatePersistence.getBoolean("setlists_only_pinned", false),
                        setlistsPrefs = SetlistsPrefs(
                            animatedBackground = appStatePersistence.getBoolean("setlists_pref_anim_bg", true),
                            cardAuras = appStatePersistence.getBoolean("setlists_pref_auras", true),
                            compactRows = appStatePersistence.getBoolean("setlists_pref_compact", false),
                            patchDots = appStatePersistence.getBoolean("setlists_pref_dots", true),
                            openWith = runCatching { OpenGesture.valueOf(appStatePersistence.getString("setlists_pref_open_with", OpenGesture.Tap.name)) }.getOrDefault(OpenGesture.Tap),
                            pinnedOnTop = appStatePersistence.getBoolean("setlists_pref_pinned_top", true),
                            rememberSort = appStatePersistence.getBoolean("setlists_pref_rem_sort", true),
                            lockDeletion = appStatePersistence.getBoolean("setlists_pref_lock_del", false),
                            haptics = appStatePersistence.getBoolean("setlists_pref_haptics", true)
                        ),
                        sfFavorites = saved.sfFavorites,
                        sfCategoryOverrides = saved.sfCategoryOverrides,
                        audioBufferSize = saved.audioBufferSize ?: state.audioBufferSize,
                        polyphony = saved.polyphony ?: state.polyphony,
                        headroom = saved.headroom ?: state.headroom,
                        audioEngine = saved.audioEngine ?: state.audioEngine,
                        fluidSynthGain = saved.fluidSynthGain ?: state.fluidSynthGain,
                        antiJitter = saved.antiJitter ?: state.antiJitter,
                        jitterWindowMs = saved.jitterWindowMs ?: state.jitterWindowMs,
                        useFlats = saved.useFlats ?: state.useFlats,
                        hapticsEnabled = saved.hapticsEnabled ?: state.hapticsEnabled,
                        reducedMotion = saved.reducedMotion ?: state.reducedMotion,
                        vkbTheme = appStatePersistence.getString("vkb_theme", "classic"),
                        vkbPressStyle = appStatePersistence.getString("vkb_press_style", "color"),
                        transportBpm = saved.transportBpm ?: state.transportBpm,
                        transportSignature = saved.transportSignature ?: state.transportSignature,
                        transportSubdivision = saved.transportSubdivision ?: state.transportSubdivision,
                        metronomeSound = saved.metronomeSound ?: state.metronomeSound,
                        metronomeAccent1 = saved.metronomeAccent1 ?: state.metronomeAccent1,
                        metronomeAccent13 = saved.metronomeAccent13 ?: state.metronomeAccent13,
                        metronomeVisualFlash = saved.metronomeVisualFlash ?: state.metronomeVisualFlash,
                        metronomeCountIn = saved.metronomeCountIn ?: state.metronomeCountIn,
                        fxChains = parseFxChainsJson(saved.fxChainsJson),
                        padCount = saved.drumPadCount ?: state.padCount,
                        kits = parseKitsJson(saved.drumKitsJson).ifEmpty { com.soundstage.mixer.model.DrumKitData.defaults() },
                    )
                }

                _uiState.value.let { s ->
                    audioEngine.setBufferSize(s.audioBufferSize)
                    audioEngine.setPolyphony(s.polyphony)
                    audioEngine.fluidSynthGain = s.fluidSynthGain
                    val driver = if (s.audioEngine.contains("OpenSL", ignoreCase = true))
                        NativeAudioBridge.DRIVER_OPENSL_ES else NativeAudioBridge.DRIVER_OBOE
                    NativeAudioBridge.safeSetAudioDriver(driver)
                }

                // Reprogrammer le transport et pousser l'etat FX restaure vers les natifs
                transportClock.bpm = _uiState.value.transportBpm
                transportClock.signature = com.soundstage.mixer.audio.TransportClock.Signature.parse(_uiState.value.transportSignature)
                transportClock.subdivision = com.soundstage.mixer.audio.TransportClock.Subdivision.parse(_uiState.value.transportSubdivision)
                metronomeEngine.volume = _uiState.value.metronomeVolume
                metronomeEngine.accent1 = _uiState.value.metronomeAccent1
                metronomeEngine.accent13 = _uiState.value.metronomeAccent13
                metronomeEngine.visualFlash = _uiState.value.metronomeVisualFlash
                metronomeEngine.currentSound = com.soundstage.mixer.audio.MetronomeEngine.Sound.parse(_uiState.value.metronomeSound)
                val restoredFx = _uiState.value.fxChains
                com.soundstage.mixer.audio.NativeAudioBridge.safeSetTonicFxChain(
                    restoredFx.tonic.filterOn, restoredFx.tonic.filterAmount,
                    restoredFx.tonic.brightnessOn, restoredFx.tonic.brightnessAmount,
                    restoredFx.tonic.delayOn, restoredFx.tonic.delayAmount,
                    restoredFx.tonic.reverbOn, restoredFx.tonic.reverbAmount,
                    restoredFx.tonic.shimmerOn, restoredFx.tonic.shimmerAmount,
                )
                com.soundstage.mixer.audio.NativeAudioBridge.safeSetPadsFxChain(
                    restoredFx.pads.filterOn, restoredFx.pads.filterAmount,
                    restoredFx.pads.brightnessOn, restoredFx.pads.brightnessAmount,
                    restoredFx.pads.delayOn, restoredFx.pads.delayAmount,
                    restoredFx.pads.reverbOn, restoredFx.pads.reverbAmount,
                )
            } else {
                _uiState.update { it.copy(currentPage = AppScreenPage.SETLISTS, activeProjectName = "", activeSceneId = "") }
            }
            // Track colours live in their own flat prefs (independent of the app-state
            // blob), so they must be restored whether or not a full state was found.
            restoreTrackColorSettings()
        } catch (e: Exception) {
            Log.e("MixerViewModel", "Error restoring saved app state: ${e.message}", e)
        }
    }

    private fun restoreTrackColorSettings() {
        _uiState.update {
            it.copy(
                trackColorMode = runCatching {
                    TrackColorMode.valueOf(appStatePersistence.getString(KEY_TRACK_COLOR_MODE, TrackColorMode.Palette.name))
                }.getOrDefault(TrackColorMode.Palette),
                trackColorPaletteId = TrackPalettes.byId(
                    appStatePersistence.getString(KEY_TRACK_COLOR_PALETTE, TrackPalettes.Default.id)
                ).id,
                trackRandomColorArgb = appStatePersistence.getString(KEY_TRACK_COLOR_RANDOM, "")
                    .split(",").mapNotNull { v -> v.trim().toIntOrNull() },
                trackSingleColorArgb = appStatePersistence.getInt(KEY_TRACK_COLOR_SINGLE, 0xFF8FB2E8.toInt()),
                trackColorDynamic = appStatePersistence.getBoolean(KEY_TRACK_COLOR_DYNAMIC, false)
            )
        }
    }

    private var persistJob: Job? = null

    /**
     * True while a snapshot is being recalled. A recall writes the snapshot's own
     * values back into the live state, so syncing during it would be a no-op at best
     * and would fight the interpolation at worst.
     */
    private var isRecallingSnapshot = false

    private fun persistCurrentStateDebounced() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            syncActiveSnapshot()
            persistCurrentState()
        }
    }

    /**
     * Keeps the active snapshot in step with live edits.
     *
     * A snapshot used to be a one-shot copy: moving a fader after recalling it left
     * the stored copy at its old value, so the next recall of that same slot jumped
     * back to a stale mix. Every live mutation already funnels through
     * [persistCurrentStateDebounced], so the copy is refreshed there — one place, and
     * the 500 ms debounce keeps a fader drag from rewriting the blob on every frame.
     *
     * Skipped when nothing is recalled, while arming (the next tap writes the case),
     * and during a recall.
     */
    private fun syncActiveSnapshot() {
        val state = _uiState.value
        val slot = state.activeSnapshotSlot ?: return
        if (state.isSnapshotArmMode || isRecallingSnapshot) return
        val existing = state.snapshots[slot] ?: return
        val refreshed = captureSnapshot(slot, state).copy(timestamp = existing.timestamp)
        if (refreshed == existing) return
        val updatedSnapshots = state.snapshots + (slot to refreshed)
        _uiState.update { it.copy(snapshots = updatedSnapshots) }
    }

    private fun persistCurrentState() {
        val state = _uiState.value
        val updatedPatches = if (state.activePatchId != null && state.patches.isNotEmpty()) {
            state.patches.map { p ->
                if (p.id == state.activePatchId) {
                    p.copy(
                        tracks = state.tracks,
                        audioSlots = state.audioSlots,
                        visibleTracksCount = state.visibleTracksCount,
                        transpose = state.transpose,
                        octave = state.octave,
                        masterVolume = state.masterTrack.volume,
                        snapshots = state.snapshots,
                        configuredSnapshotSlots = state.configuredSnapshotSlots,
                        snapshotCustomNames = state.snapshotCustomNames,
                        activeSnapshotSlot = state.activeSnapshotSlot
                    )
                } else p
            }
        } else state.patches

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
            activeSnapshotSlot = state.activeSnapshotSlot,
            configuredSnapshotSlots = state.configuredSnapshotSlots,
            snapshotCustomNames = state.snapshotCustomNames,
            visibleTracksCount = state.visibleTracksCount,
            activeProjectName = state.activeProjectName,
            themeHue = state.themeHue,
            themeSaturation = state.themeSaturation,
            themeNuance = state.themeNuance,
            themePalette = state.themePaletteName,
            themeAccentSaturation = state.themeAccentSaturation,
            themeBgSaturation = state.themeBgSaturation,
            themeBgLightness = state.themeBgLightness,
            themeModulesLightness = state.themeModulesLightness,
            trackColorSaturation = state.trackColorSaturation,
            isFaderAutoFit = state.isFaderAutoFit,
            patches = updatedPatches,
            activePatchId = state.activePatchId,
            midiMappings = state.midiMappings,
            audioBufferSize = state.audioBufferSize,
            polyphony = state.polyphony,
            headroom = state.headroom,
            audioEngine = state.audioEngine,
            fluidSynthGain = state.fluidSynthGain,
            antiJitter = state.antiJitter,
            jitterWindowMs = state.jitterWindowMs,
            useFlats = state.useFlats,
            hapticsEnabled = state.hapticsEnabled,
            reducedMotion = state.reducedMotion,
            sfFavorites = state.sfFavorites,
            sfCategoryOverrides = state.sfCategoryOverrides,
            transportBpm = state.transportBpm,
            transportSignature = state.transportSignature,
            transportSubdivision = state.transportSubdivision,
            metronomeSound = state.metronomeSound,
            metronomeAccent1 = state.metronomeAccent1,
            metronomeAccent13 = state.metronomeAccent13,
            metronomeVisualFlash = state.metronomeVisualFlash,
            metronomeCountIn = state.metronomeCountIn,
            fxChainsJson = buildFxChainsJson(state.fxChains),
            drumPadCount = state.padCount,
            drumKitsJson = buildKitsJson(state.kits),
        )
        // Full auto-save: automatically sync to the current project's .scene file if a project is actively open
        if (state.activeProjectName.isNotEmpty() && state.activeProjectName != "None") {
            saveCurrentScene(state.activeProjectName)
        }
    }

    fun setPadCount(count: Int) {
        val c = if (count >= 16) 16 else 8
        _uiState.update { it.copy(padCount = c) }
        persistCurrentStateDebounced()
    }

    fun saveCurrentPadsAsKit(name: String) {
        val cleanName = name.trim().ifEmpty { "Kit ${_uiState.value.kits.size + 1}" }
        val kitId = "kit_${System.currentTimeMillis()}"
        val samples = _uiState.value.drumPads
            .sortedBy { it.id }
            .map { it.sampleFileName.ifEmpty { null } }
        val newKit = com.soundstage.mixer.model.DrumKitData(id = kitId, name = cleanName, padSamples = samples)
        _uiState.update { it.copy(kits = it.kits + newKit, activeDrumKitName = cleanName) }
        persistCurrentStateDebounced()
    }

    fun loadKit(kitId: String) {
        val kit = _uiState.value.kits.find { it.id == kitId || it.name == kitId } ?: return
        _uiState.update { state ->
            val updatedPads = state.drumPads.mapIndexed { idx, pad ->
                if (idx < kit.padSamples.size) {
                    val sampleName = kit.padSamples[idx]
                    pad.copy(
                        label = sampleName?.take(12) ?: "Empty",
                        sampleFileName = sampleName ?: "",
                        sampleFilePath = "",
                    )
                } else pad
            }
            state.copy(drumPads = updatedPads, activeDrumKitName = kit.name)
        }
        persistCurrentStateDebounced()
    }

    fun deleteKit(kitId: String) {
        _uiState.update { state ->
            val remaining = state.kits.filterNot { it.id == kitId }
            val nextName = if (state.kits.find { it.id == kitId }?.name == state.activeDrumKitName) {
                remaining.firstOrNull()?.name ?: ""
            } else state.activeDrumKitName
            state.copy(kits = remaining, activeDrumKitName = nextName)
        }
        persistCurrentStateDebounced()
    }

    private fun buildKitsJson(kits: List<com.soundstage.mixer.model.DrumKitData>): String {
        return try {
            val arr = org.json.JSONArray()
            kits.forEach { kit ->
                val o = org.json.JSONObject().apply {
                    put("id", kit.id)
                    put("name", kit.name)
                    val s = org.json.JSONArray()
                    kit.padSamples.forEach { if (it == null) s.put(org.json.JSONObject.NULL) else s.put(it) }
                    put("padSamples", s)
                }
                arr.put(o)
            }
            arr.toString()
        } catch (_: Exception) { "" }
    }

    private fun parseKitsJson(json: String?): List<com.soundstage.mixer.model.DrumKitData> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            val list = mutableListOf<com.soundstage.mixer.model.DrumKitData>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val sArr = o.optJSONArray("padSamples")
                val samples = mutableListOf<String?>()
                if (sArr != null) {
                    for (j in 0 until sArr.length()) {
                        if (sArr.isNull(j)) samples.add(null)
                        else samples.add(sArr.getString(j))
                    }
                }
                list.add(com.soundstage.mixer.model.DrumKitData(
                    id = o.optString("id", "kit_$i"),
                    name = o.optString("name", "Kit ${i + 1}"),
                    padSamples = samples,
                ))
            }
            list
        } catch (_: Exception) { emptyList() }
    }

    private fun buildFxChainsJson(chains: com.soundstage.mixer.model.FxChainsState): String {
        return try {
            val tonic = chains.tonic
            val pads = chains.pads
            org.json.JSONObject().apply {
                put("tonic", org.json.JSONObject().apply {
                    put("filterOn", tonic.filterOn); put("filterAmt", tonic.filterAmount.toDouble())
                    put("brightnessOn", tonic.brightnessOn); put("brightnessAmt", tonic.brightnessAmount.toDouble())
                    put("delayOn", tonic.delayOn); put("delayAmt", tonic.delayAmount.toDouble())
                    put("reverbOn", tonic.reverbOn); put("reverbAmt", tonic.reverbAmount.toDouble())
                    put("shimmerOn", tonic.shimmerOn); put("shimmerAmt", tonic.shimmerAmount.toDouble())
                })
                put("pads", org.json.JSONObject().apply {
                    put("filterOn", pads.filterOn); put("filterAmt", pads.filterAmount.toDouble())
                    put("brightnessOn", pads.brightnessOn); put("brightnessAmt", pads.brightnessAmount.toDouble())
                    put("delayOn", pads.delayOn); put("delayAmt", pads.delayAmount.toDouble())
                    put("reverbOn", pads.reverbOn); put("reverbAmt", pads.reverbAmount.toDouble())
                })
            }.toString()
        } catch (_: Exception) { "" }
    }

    private fun parseFxChainsJson(json: String?): com.soundstage.mixer.model.FxChainsState {
        if (json.isNullOrEmpty()) return com.soundstage.mixer.model.FxChainsState.DEFAULT
        return try {
            val o = org.json.JSONObject(json)
            val t = o.optJSONObject("tonic") ?: org.json.JSONObject()
            val p = o.optJSONObject("pads") ?: org.json.JSONObject()
            com.soundstage.mixer.model.FxChainsState(
                tonic = com.soundstage.mixer.model.SurfaceFxChain(
                    filterOn = t.optBoolean("filterOn", false), filterAmount = t.optDouble("filterAmt", 0.5).toFloat(),
                    brightnessOn = t.optBoolean("brightnessOn", false), brightnessAmount = t.optDouble("brightnessAmt", 0.5).toFloat(),
                    delayOn = t.optBoolean("delayOn", false), delayAmount = t.optDouble("delayAmt", 0.3).toFloat(),
                    reverbOn = t.optBoolean("reverbOn", true), reverbAmount = t.optDouble("reverbAmt", 0.4).toFloat(),
                    shimmerOn = t.optBoolean("shimmerOn", false), shimmerAmount = t.optDouble("shimmerAmt", 0.3).toFloat(),
                ),
                pads = com.soundstage.mixer.model.SurfaceFxChain(
                    filterOn = p.optBoolean("filterOn", false), filterAmount = p.optDouble("filterAmt", 0.5).toFloat(),
                    brightnessOn = p.optBoolean("brightnessOn", false), brightnessAmount = p.optDouble("brightnessAmt", 0.5).toFloat(),
                    delayOn = p.optBoolean("delayOn", false), delayAmount = p.optDouble("delayAmt", 0.3).toFloat(),
                    reverbOn = p.optBoolean("reverbOn", true), reverbAmount = p.optDouble("reverbAmt", 0.4).toFloat(),
                )
            )
        } catch (_: Exception) { com.soundstage.mixer.model.FxChainsState.DEFAULT }
    }

    override fun onCleared() {
        metronomeEngine.release()
        looperEngine.release()
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
                isEnabled = true, // All tracks powered on at startup, as in MixerPreview.jsx (CH0 power:true)
                volume = 0.85f,
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
        val defaultMidiMaps = createDefaultMidiMappings()

        return MixerUiState(
            audioSlots = initialAudioSlots,
            tracks = initialTracks,
            drumPads = initialDrumPads,
            scenes = initialScenes,
            fxParameters = defaultFx,
            midiMappings = defaultMidiMaps,
            currentTheme = AppTheme.CYBER_NEON
        )
    }

    private fun createDefaultMidiMappings(): List<MidiMappingItem> {
        return MidiActionType.values().map { action ->
            when (action) {
                MidiActionType.MASTER_VOLUME -> MidiMappingItem(action, MidiControlType.CC, -1, 7)
                else -> MidiMappingItem(action, action.defaultType, -1, -1)
            }
        }
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
                    val cleanName = if (f.name.startsWith("Project", ignoreCase = true)) {
                        f.name.replaceFirst(Regex("(?i)^Project"), "Setlist")
                    } else f.name

                    var patchesList = emptyList<PatchItem>()
                    try {
                        val jsonStr = fileManager.loadSceneFile(f.path)
                        if (jsonStr != null) {
                            val obj = org.json.JSONObject(jsonStr)
                            val pArr = obj.optJSONArray("patches")
                            if (pArr != null) {
                                val list = mutableListOf<PatchItem>()
                                for (pi in 0 until pArr.length()) {
                                    val pObj = pArr.getJSONObject(pi)
                                    list.add(PatchItem(
                                        id = pObj.optString("id", "patch_$pi"),
                                        name = pObj.optString("name", "Patch ${pi + 1}")
                                    ))
                                }
                                patchesList = list
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    val savedColorName = appStatePersistence.getString("setlist_col_$cleanName", SetlistColor.Blue.name)
                    val savedColor = runCatching { SetlistColor.valueOf(savedColorName) }.getOrDefault(SetlistColor.Blue)
                    ScenePreset(
                        id = cleanName,
                        name = cleanName,
                        timestamp = if (f.formattedDate.isNotEmpty()) f.formattedDate else "Recently used",
                        color = savedColor.base,
                        patchesCount = patchesList.size,
                        patches = patchesList
                    )
                }

                val effectiveScenes = realScenes

                val loopDirItems = fileManager.listItemsInDirectory(fileManager.loopsDir.absolutePath)
                val midiDirItems = fileManager.listItemsInDirectory(fileManager.midiDir.absolutePath)

                val bankFiles = sfs.map { sf ->
                    SoundfontBankFile(
                        name = sf.name,
                        path = sf.path,
                        size = sf.formattedSize
                    )
                }

                val currentActiveName = _uiState.value.activeProjectName.ifEmpty { _uiState.value.activeSceneId }
                val activeSceneMatch = realScenes.find { it.name.equals(currentActiveName, ignoreCase = true) }
                val patchesToUse = if (_uiState.value.patches.isEmpty() && activeSceneMatch != null && activeSceneMatch.patches.isNotEmpty()) {
                    activeSceneMatch.patches
                } else _uiState.value.patches

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
                        patches = patchesToUse,
                        activePatchId = if (it.activePatchId.isNullOrEmpty()) patchesToUse.firstOrNull()?.id else it.activePatchId,
                        scenes = effectiveScenes,
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
        _uiState.update { it.copy(isMidiPanelOpen = !it.isMidiPanelOpen, isMetroPanelOpen = false) }
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

    fun setThemeHue(hue: Float) {
        _uiState.update { it.copy(themeHue = hue.coerceIn(0f, 360f)) }
        persistCurrentStateDebounced()
    }

    fun setThemeSaturation(saturation: Float) {
        _uiState.update { it.copy(themeSaturation = saturation.coerceIn(0.05f, 1f)) }
        persistCurrentStateDebounced()
    }

    fun setThemeNuance(nuance: Float) {
        _uiState.update { it.copy(themeNuance = nuance.coerceIn(0.15f, 0.85f)) }
        persistCurrentStateDebounced()
    }

    fun setThemeAccentSaturation(sat: Float) {
        _uiState.update { it.copy(themeAccentSaturation = sat.coerceIn(0.5f, 2.0f)) }
        persistCurrentStateDebounced()
    }

    fun setThemeBgSaturation(sat: Float) {
        _uiState.update { it.copy(themeBgSaturation = sat.coerceIn(0.0f, 2.0f)) }
        persistCurrentStateDebounced()
    }

    fun setThemeBgLightness(lightness: Float) {
        _uiState.update { it.copy(themeBgLightness = lightness.coerceIn(0.7f, 1.4f)) }
        persistCurrentStateDebounced()
    }

    fun setThemeModulesLightness(lightness: Float) {
        _uiState.update { it.copy(themeModulesLightness = lightness.coerceIn(0.8f, 1.5f)) }
        persistCurrentStateDebounced()
    }

    fun setTrackColorSaturation(saturation: Float) {
        _uiState.update { it.copy(trackColorSaturation = saturation.coerceIn(0.2f, 2.0f)) }
        persistCurrentStateDebounced()
    }

    fun setThemePalette(preset: ThemePalettePreset) {
        _uiState.update {
            it.copy(
                themeHue = preset.hue,
                themeSaturation = preset.saturation,
                themeNuance = preset.nuance,
                themeAccentSaturation = 1.0f,
                themeBgSaturation = 1.60f,
                themeBgLightness = 1.0f,
                themeModulesLightness = 1.0f,
                themePaletteName = preset.name
            )
        }
        persistCurrentStateDebounced()
    }

    fun resetThemeToDefault() {
        _uiState.update {
            it.copy(
                themeHue = 220f,
                themeSaturation = 0.40f,
                themeNuance = 0.45f,
                themeAccentSaturation = 1.0f,
                themeBgSaturation = 1.60f,
                themeBgLightness = 1.0f,
                themeModulesLightness = 1.0f,
                themePaletteName = "Midnight Slate (Default)"
            )
        }
        persistCurrentStateDebounced()
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

                    // While a floating window (Track Settings, Patches, FX, soundfonts) is up, the
                    // mixer sits behind a full-screen blur. Every meter update recomposes that
                    // subtree and forces the blur to re-render its offscreen pass, which is the
                    // stutter felt in those windows. The meters are not readable through the blur,
                    // so keep them alive at a low rate instead of the 20 Hz they need when visible.
                    if (curr.activePopup.isFloatingWindow()) {
                        delay(200)
                    } else {
                        delay(50)
                    }
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
            metronomeEngine.volume = _uiState.value.metronomeVolume
            metronomeEngine.accent1 = _uiState.value.metronomeAccent1
            metronomeEngine.accent13 = _uiState.value.metronomeAccent13
            metronomeEngine.visualFlash = _uiState.value.metronomeVisualFlash
            metronomeEngine.currentSound = com.soundstage.mixer.audio.MetronomeEngine.Sound.parse(_uiState.value.metronomeSound)
            metronomeEngine.start()
        } else {
            metronomeEngine.stop()
        }
        _uiState.update { it.copy(isMetronomeOn = next) }
    }

    fun toggleMetroPanel() {
        _uiState.update { it.copy(isMetroPanelOpen = !it.isMetroPanelOpen, isMidiPanelOpen = false) }
    }

    fun closeMetroPanel() {
        _uiState.update { it.copy(isMetroPanelOpen = false) }
    }

    fun setMetronomeSignature(sig: String) {
        _uiState.update { it.copy(metronomeSignature = sig, transportSignature = sig) }
        transportClock.signature = com.soundstage.mixer.audio.TransportClock.Signature.parse(sig)
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
        metronomeEngine.volume = clamped
    }

    fun setTransportBpm(bpm: Int) {
        val clamped = bpm.coerceIn(40, 240)
        transportClock.bpm = clamped
        _uiState.update { it.copy(transportBpm = clamped, bpm = clamped) }
        persistCurrentStateDebounced()
    }

    /**
     * Step the transport BPM by [delta]. The current value is read here, inside the
     * update, so a press-and-hold ramp cannot lose increments to a stale UI snapshot
     * (the pill's callback used to capture one `transportBpm` and keep re-deriving
     * the same value, which is why the readout stuck at 120/119).
     */
    fun adjustTransportBpm(delta: Int) {
        setTransportBpm(_uiState.value.transportBpm + delta)
    }

    fun setTransportSubdivision(sub: String) {
        transportClock.subdivision = com.soundstage.mixer.audio.TransportClock.Subdivision.parse(sub)
        _uiState.update { it.copy(transportSubdivision = sub) }
    }

    fun setMetronomeSound(sound: String) {
        metronomeEngine.currentSound = com.soundstage.mixer.audio.MetronomeEngine.Sound.parse(sound)
        _uiState.update { it.copy(metronomeSound = sound) }
        persistCurrentStateDebounced()
    }

    fun setMetronomeAccent1(on: Boolean) {
        metronomeEngine.accent1 = on
        _uiState.update { it.copy(metronomeAccent1 = on) }
        persistCurrentStateDebounced()
    }

    fun setMetronomeAccent13(on: Boolean) {
        metronomeEngine.accent13 = on
        _uiState.update { it.copy(metronomeAccent13 = on) }
        persistCurrentStateDebounced()
    }

    fun setMetronomeVisualFlash(on: Boolean) {
        metronomeEngine.visualFlash = on
        _uiState.update { it.copy(metronomeVisualFlash = on) }
        persistCurrentStateDebounced()
    }

    fun setMetronomeCountIn(mode: String) {
        _uiState.update { it.copy(metronomeCountIn = mode) }
        persistCurrentStateDebounced()
    }

    fun setMetronomeTapBpm(bpm: Int) {
        _uiState.update { it.copy(metronomeTapBpm = bpm) }
    }

    fun tapLooperCase(caseIndex: Int) {
        looperEngine.tapCase(caseIndex)
    }

    fun longPressLooperCaseAction(caseIndex: Int, action: String) {
        looperEngine.longPressCaseAction(caseIndex, action)
    }

    fun setLooperQuantize(q: Boolean) {
        looperEngine.setQuantize(q)
    }

    fun setLooperSwing(s: Int) {
        looperEngine.setSwing(s)
    }

    fun setLooperLengthMode(m: String) {
        looperEngine.setLengthMode(m)
    }

    fun stopAllLoops() { looperEngine.stopAll() }
    fun playAllLoops() { looperEngine.playAll() }

    fun recordLooperEvent(kind: String, idx: Int, color: String) {
        looperEngine.record(kind, idx, color)
    }

    fun setTonicFxChain(chain: com.soundstage.mixer.model.SurfaceFxChain) {
        _uiState.update { it.copy(fxChains = it.fxChains.copy(tonic = chain)) }
        com.soundstage.mixer.audio.NativeAudioBridge.safeSetTonicFxChain(
            chain.filterOn, chain.filterAmount,
            chain.brightnessOn, chain.brightnessAmount,
            chain.delayOn, chain.delayAmount,
            chain.reverbOn, chain.reverbAmount,
            chain.shimmerOn, chain.shimmerAmount,
        )
        persistCurrentStateDebounced()
    }

    fun setPadsFxChain(chain: com.soundstage.mixer.model.SurfaceFxChain) {
        val sanitized = chain.withShimmerDisabled()
        _uiState.update { it.copy(fxChains = it.fxChains.copy(pads = sanitized)) }
        com.soundstage.mixer.audio.NativeAudioBridge.safeSetPadsFxChain(
            sanitized.filterOn, sanitized.filterAmount,
            sanitized.brightnessOn, sanitized.brightnessAmount,
            sanitized.delayOn, sanitized.delayAmount,
            sanitized.reverbOn, sanitized.reverbAmount,
        )
        persistCurrentStateDebounced()
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
        // Also drop the Æther effect tails: allNotesOff only clears the notes, so a
        // delay or reverb with high feedback kept ringing after the panic button.
        NativeAudioBridge.safeFxPanic()
        NativeAudioBridge.safeStopAllDrumSamples()
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

        // Track colours (Settings > Interface > Track colors), stored as flat prefs.
        const val KEY_TRACK_COLOR_MODE = "track_color_mode"
        const val KEY_TRACK_COLOR_PALETTE = "track_color_palette"
        const val KEY_TRACK_COLOR_RANDOM = "track_color_random"
        const val KEY_TRACK_COLOR_SINGLE = "track_color_single"
        const val KEY_TRACK_COLOR_DYNAMIC = "track_color_dynamic"
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

    fun getActivePerformanceChannels(midiNote: Int? = null, incomingChannel: Int? = null, deviceId: String? = null): List<Int> {
        val state = _uiState.value
        val anySolo = state.tracks.any { it.isSolo }

        // Dynamic Keyboard Layer Mapper: route notes strictly within each active track's assigned key range (splitNoteMin..splitNoteMax)
        // AND strictly match the track's configured MIDI channel:
        // track.midiChannel == 0 -> OMNI (accepts all channels)
        // track.midiChannel in 1..16 -> accepts only messages where incomingChannel == (track.midiChannel - 1)
        // AND match the track's selected input device: null/"all" -> every device.
        val activeChannels = state.tracks.mapIndexedNotNull { idx, track ->
            val isAllowed = track.isEnabled && !track.isMuted && (!anySolo || track.isSolo)
            if (isAllowed) {
                val isDeviceMatch = (deviceId == null) || (track.selectedMidiDeviceId == null) || (track.selectedMidiDeviceId == deviceId)
                val isChannelMatch = (incomingChannel == null) || (track.midiChannel == 0) || (incomingChannel == track.midiChannel - 1)
                if (isDeviceMatch && isChannelMatch) {
                    val isWithinRange = (midiNote == null) || (midiNote in track.splitNoteMin..track.splitNoteMax)
                    if (isWithinRange) {
                        val slot = state.audioSlots.getOrNull(idx)
                        if (slot != null && slot.soundFontId > 0) idx else null
                    } else null
                } else null
            } else null
        }

        return if (activeChannels.isNotEmpty()) {
            activeChannels
        } else {
            if (incomingChannel != null) {
                emptyList()
            } else {
                listOf(midiChannelForSlot(state.activeSoundfontSlotId))
            }
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

    /** Aftertouch value from the on-screen keyboard, gated by the per-track setting. */
    fun sendAftertouch(value: Int) {
        val v = value.coerceIn(0, 127)
        getActivePerformanceChannels().forEach { channel ->
            val track = _uiState.value.tracks.getOrNull(channel)
            if (track?.aftertouch == true) {
                NativeAudioBridge.safeChannelPressure(channel, v)
            }
        }
    }

    private fun applyVelocityCurve(velocity: Float, curve: Float): Float {
        val v = velocity.coerceIn(0.01f, 1.0f)
        return when {
            curve < 0.48f -> {
                // Soft (vers la gauche / 0.15f) : booste la sensibilité pour qu'un toucher léger sonne fort
                val factor = (0.5f - curve) * 2f
                (Math.pow(v.toDouble(), (1.0 - factor * 0.6).coerceAtLeast(0.3))).toFloat()
            }
            curve > 0.52f -> {
                // Hard (vers la droite / 0.85f) : exige une frappe forte, conserve le pianissimo sur toucher léger
                val factor = (curve - 0.5f) * 2f
                (Math.pow(v.toDouble(), 1.0 + factor * 1.5)).toFloat()
            }
            else -> v
        }.coerceIn(0.05f, 1.0f)
    }

    // ═══════════ LiveLooper (nouvelle version) ═══════════

    /** Actions de l'UI LiveLooper (toutes cablees sur le controleur). */
    fun liveLooperActions(): com.soundstage.mixer.looper.LooperActions = liveLooper.actions()

    /** Horloge en mesures, pour l'anneau de progression de l'UI (animé par frame). */
    fun liveLooperNowBar(): Double = transportClock.currentBarPosition()

    /** Signale une note ou un pad joue en direct, pour que les cases en REC l'enregistrent. */
    private fun feedLiveLooper(source: com.soundstage.mixer.looper.RecordSource, code: Int, velocity: Int, on: Boolean) {
        runCatching { liveLooper.onPlayed(source, code, velocity, on) }
    }

    /**
     * Rejoue un evenement enregistre : pad (banque de samples) ou note MIDI (synth).
     * Le volume de la case module la velocite / le gain.
     */
    private fun replayLoopEvent(event: com.soundstage.mixer.looper.LoopEvent, volume: Float) {
        // Replay goes through the same audio engine as live playing. A user who opens
        // the LiveLooper page first (without touching a key, pad or Tonic note) had no
        // engine yet, so the first replayed events were silent. Bring it up here.
        ensureEngineStarted()
        when (event.source) {
            com.soundstage.mixer.looper.RecordSource.Pads -> {
                val pad = _uiState.value.drumPads.getOrNull(event.code) ?: return
                if (pad.sampleFileName.isEmpty()) return
                audioEngine.playDrumPadSound(pad, pad.volume * _uiState.value.drumVolume * volume.coerceIn(0f, 1f))
            }
            com.soundstage.mixer.looper.RecordSource.Tonic -> {
                val key = com.soundstage.mixer.ui.components.KeyPositionMapper.midiToNoteName(event.code)
                if (event.on) audioEngine.noteOn(key, (event.velocity / 127f) * volume.coerceIn(0f, 1f), 9)
                else audioEngine.noteOff(key, 9)
            }
            com.soundstage.mixer.looper.RecordSource.Keys -> {
                val key = com.soundstage.mixer.ui.components.KeyPositionMapper.midiToNoteName(event.code)
                val channels = getActivePerformanceChannels(event.code)
                if (event.on) {
                    channels.forEach { ch -> audioEngine.noteOn(key, (event.velocity / 127f) * volume.coerceIn(0f, 1f), ch) }
                } else {
                    channels.forEach { ch -> audioEngine.noteOff(key, ch) }
                }
            }
        }
    }

    fun onKeyDown(key: String, velocity: Float = 0.85f) {
        val midiNote = noteNameToMidi(key)
        val channels = getActivePerformanceChannels(midiNote)
        val state = _uiState.value

        // Bring the engine up on the very first key, not only when a Setlist is opened.
        // Without this the user had to nudge a snapshot before any sound came out.
        ensureEngineStarted()

        // Update the UI state first: the key press and the chord readout must not
        // depend on the audio engine being available.
        _uiState.update { it.copy(pressedKeys = it.pressedKeys + key) }

        val baseVel = state.globalVelocityMin + velocity.coerceIn(0f, 1f) * (state.globalVelocityMax - state.globalVelocityMin)

        channels.forEach { channel ->
            // Velocity shaping is applied once, by the engine's VelocityProcessor (VelocityConfig).
            runCatching { audioEngine.noteOn(key, baseVel, channel) }
        }
        // Capture the onset for the looper. Playback goes straight to audioEngine,
        // so this never re-records what it replays.
        looperEngine.record("note", midiNote, "#8FB2E8")
        feedLiveLooper(com.soundstage.mixer.looper.RecordSource.Keys, midiNote, (velocity.coerceIn(0f, 1f) * 127f).toInt(), on = true)
    }

    fun onKeyUp(key: String) {
        val midiNote = noteNameToMidi(key)
        val channels = getActivePerformanceChannels(midiNote)
        channels.forEach { channel ->
            audioEngine.noteOff(key, channel)
        }
        feedLiveLooper(com.soundstage.mixer.looper.RecordSource.Keys, midiNote, 0, on = false)
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
                tonicSubView = "main"
            )
        }
    }

    fun updateNotesText(text: String) {
        _uiState.update { it.copy(notesText = text) }
        val activeId = _uiState.value.activeSheetId
        if (activeId != null) {
            _uiState.update { state ->
                val updated = state.sheets.map { if (it.id == activeId) it.copy(content = text, updatedAt = System.currentTimeMillis()) else it }
                state.copy(sheets = updated)
            }
            persistGlobalSheets()
        }
    }

    fun loadGlobalSheets() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(fileManager.sheetsDir, "sheets_library.json")
                if (file.exists()) {
                    val jsonStr = file.readText()
                    val arr = org.json.JSONArray(jsonStr)
                    val loaded = mutableListOf<SheetItem>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        loaded.add(
                            SheetItem(
                                id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                                title = obj.optString("title", "Untitled"),
                                key = obj.optString("key", "C"),
                                content = obj.optString("content", ""),
                                colorIndex = obj.optInt("colorIndex", i),
                                fontSize = obj.optInt("fontSize", 18),
                                textColorHex = obj.optString("textColorHex", "#FFFFFF"),
                                notationSystem = obj.optString("notationSystem", "STANDARD"),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                    }
                    _uiState.update {
                        val active = loaded.firstOrNull()?.id
                        val content = loaded.firstOrNull()?.content ?: it.notesText
                        it.copy(sheets = loaded, activeSheetId = active, notesText = content)
                    }
                } else {
                    // Empty library - do NOT create fake sample sheets
                    _uiState.update { it.copy(sheets = emptyList(), activeSheetId = null) }
                }
            } catch (e: Exception) {
                Log.w("MixerViewModel", "Error loading sheets library: ${e.message}")
            }
        }
    }

    private fun persistGlobalSheets() {
        val sheetsToSave = _uiState.value.sheets
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!fileManager.sheetsDir.exists()) fileManager.sheetsDir.mkdirs()
                val file = File(fileManager.sheetsDir, "sheets_library.json")
                val arr = org.json.JSONArray()
                for (sheet in sheetsToSave) {
                    val obj = org.json.JSONObject()
                    obj.put("id", sheet.id)
                    obj.put("title", sheet.title)
                    obj.put("key", sheet.key)
                    obj.put("content", sheet.content)
                    obj.put("colorIndex", sheet.colorIndex)
                    obj.put("fontSize", sheet.fontSize)
                    obj.put("textColorHex", sheet.textColorHex)
                    obj.put("notationSystem", sheet.notationSystem)
                    obj.put("updatedAt", sheet.updatedAt)
                    arr.put(obj)

                    // Also persist human-readable individual file in Sheets/ (.lsheet format)
                    try {
                        val safeName = sheet.title.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
                        val individualFile = File(fileManager.sheetsDir, "$safeName.lsheet")
                        val formattedSheet = buildString {
                            appendLine("Title: ${sheet.title}")
                            appendLine("Key: ${sheet.key}")
                            appendLine("Updated: ${java.util.Date(sheet.updatedAt)}")
                            appendLine("----------------------------------------")
                            appendLine(sheet.content)
                        }
                        individualFile.writeText(formattedSheet)
                    } catch (_: Exception) {}
                }
                file.writeText(arr.toString())
            } catch (e: Exception) {
                Log.w("MixerViewModel", "Error saving sheets library: ${e.message}")
            }
        }
    }

    fun createSheet(title: String, key: String = "C", content: String = ""): SheetItem {
        val newSheet = SheetItem(
            title = title.ifBlank { "Untitled Sheet" },
            key = key.ifBlank { "C" },
            content = content,
            colorIndex = _uiState.value.sheets.size
        )
        _uiState.update {
            it.copy(
                sheets = listOf(newSheet) + it.sheets,
                activeSheetId = newSheet.id,
                notesText = newSheet.content
            )
        }
        persistGlobalSheets()
        return newSheet
    }

    fun updateSheet(
        id: String,
        title: String,
        key: String,
        content: String,
        fontSize: Int = 18,
        textColorHex: String = "#FFFFFF",
        notationSystem: String = "STANDARD"
    ) {
        _uiState.update { state ->
            val updated = state.sheets.map { sheet ->
                if (sheet.id == id) {
                    sheet.copy(
                        title = title.ifBlank { sheet.title },
                        key = key.ifBlank { sheet.key },
                        content = content,
                        fontSize = fontSize,
                        textColorHex = textColorHex,
                        notationSystem = notationSystem,
                        updatedAt = System.currentTimeMillis()
                    )
                } else sheet
            }
            state.copy(
                sheets = updated,
                notesText = if (state.activeSheetId == id) content else state.notesText
            )
        }
        persistGlobalSheets()
    }

    fun deleteSheet(id: String) {
        _uiState.update { state ->
            val updated = state.sheets.filterNot { it.id == id }
            val nextActive = if (state.activeSheetId == id) updated.firstOrNull()?.id else state.activeSheetId
            val nextContent = updated.find { it.id == nextActive }?.content ?: ""
            state.copy(
                sheets = updated,
                activeSheetId = nextActive,
                notesText = nextContent
            )
        }
        persistGlobalSheets()
    }

    fun selectActiveSheet(id: String?) {
        _uiState.update { state ->
            val target = state.sheets.find { it.id == id }
            state.copy(
                activeSheetId = id,
                notesText = target?.content ?: state.notesText
            )
        }
    }

    fun openFloatingSheet(sheet: SheetItem) {
        _uiState.update {
            it.copy(
                floatingSheet = sheet,
                currentPage = AppScreenPage.MIXER
            )
        }
    }

    fun toggleFloatingSheetPosition() {
        _uiState.update { it.copy(floatingSheetIsLeft = !it.floatingSheetIsLeft) }
    }

    fun setFloatingSheetPosition(isLeft: Boolean) {
        _uiState.update { it.copy(floatingSheetIsLeft = isLeft) }
    }

    fun updateFloatingSheetScale(scale: Float) {
        _uiState.update { it.copy(floatingSheetScale = scale.coerceIn(0.75f, 1.5f)) }
    }

    fun closeFloatingSheet() {
        _uiState.update { it.copy(floatingSheet = null) }
    }

    fun closeDrumPad() {
        _uiState.update {
            val wasPinned = it.isDrumPadPinned || it.drumPadWasPinned
            it.copy(
                drumPadWasPinned = wasPinned,
                isDrumPadPinned = false,
                activePopup = if (it.activePopup == ActivePopup.DRUM_PAD) ActivePopup.NONE else it.activePopup,
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
        // DrumPad samples are played by the native engine, which is only brought up
        // lazily. Without this a user landing on the DrumPad page first hears nothing.
        ensureEngineStarted()
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

            // B5a: enregistre le hit dans le looper (kind/idx/couleur)
            looperEngine.record(
                "pad",
                padId - 1,
                "#" + Integer.toHexString(DpPalette.at(padId - 1).toArgb())
            )
            // LiveLooper : capture le hit pour les cases en REC.
            feedLiveLooper(com.soundstage.mixer.looper.RecordSource.Pads, padId - 1, 110, on = true)

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
        // The TonicPad plays through the native engine, which is only brought up lazily.
        // A user who opens the DrumPad page first and taps a pad or a Tonic key would
        // otherwise get no sound at all.
        ensureEngineStarted()
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
            // LiveLooper : capture le drone (note on / off) pour les cases en REC.
            val midi = noteNameToMidi(note)
            feedLiveLooper(com.soundstage.mixer.looper.RecordSource.Tonic, midi, 100, on = newSet.contains(note))
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

                // Anti-pop / Anti-crack: smoothly silence the channel and all notes before swapping soundfonts
                // to prevent DC offset pulse or abrupt buffer discontinuity ("TACK")
                NativeAudioBridge.safeAllNotesOff(targetChannel)
                NativeAudioBridge.safeSetTrackVolume(targetChannel, 0f)

                // The channel is silenced above and only restored at the end of the
                // load. If anything throws in between (an unreadable SF2, a native
                // bridge error), the old code left the track at 0 and the user saw
                // the fader pinned to the bottom with no way to hear it. The body is
                // wrapped so the volume is always re-applied from state on the way
                // out, success or not.
                try {

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

                // Sync channel volume immediately so fader movement is not required to
                // hear sound. Same rule as the finally block below (disabled and muted
                // both mean silence) so the two cannot disagree.
                val targetTrackNow = _uiState.value.tracks.getOrNull(slotId)
                val effectiveVol = when {
                    targetTrackNow == null -> 0.8f
                    !targetTrackNow.isEnabled -> 0f
                    targetTrackNow.isMuted -> 0f
                    else -> targetTrackNow.volume
                }
                audioEngine.setChannelVolume(targetChannel, effectiveVol)
                NativeAudioBridge.safeSetTrackVolume(targetChannel, effectiveVol)

                // Track Settings' "defaults at load" are applied when the user moves
                // those sliders, not here. Re-applying them on every soundfont load
                // overwrote the live fader: defaultVolume is 100 (0.787) while the
                // normal fader is 0.85, so every load silently pulled track 1 down and
                // the user had to nudge a snapshot to get the level back. Pan is still
                // restored here because it has no separate live fader write.
                _uiState.value.tracks.getOrNull(slotId)?.let { t ->
                    NativeAudioBridge.safeSetTrackPan(targetChannel, (t.defaultPan.coerceIn(-64, 63)) / 63f)
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
                } catch (t: Throwable) {
                    Log.e("SoundFontLoad", "Load failed for slot=$slotId: ${t.message}", t)
                } finally {
                    // Always re-apply the live fader volume: the channel was forced to
                    // 0 before the swap, and a failure must not leave it there. Read
                    // the current state so a load that happened while the user was
                    // already fading still lands on the right level. Slot 9 (TonicPad)
                    // has no TrackChannel, so its level comes from the audio slot.
                    val track = _uiState.value.tracks.getOrNull(slotId)
                    val vol = when {
                        track != null -> if (!track.isEnabled || track.isMuted) 0f else track.volume
                        else -> _uiState.value.audioSlots.getOrNull(slotId)?.volume ?: 0.8f
                    }
                    audioEngine.setChannelVolume(targetChannel, vol)
                    NativeAudioBridge.safeSetTrackVolume(targetChannel, vol)
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
                                patchName = "No Instrument",
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
                                    patchName = "No Instrument",
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

    fun toggleSfFavorite(key: String) {
        _uiState.update { state ->
            val next = if (state.sfFavorites.contains(key)) state.sfFavorites - key else state.sfFavorites + key
            state.copy(sfFavorites = next)
        }
        persistCurrentStateDebounced()
    }

    fun moveSfToCategory(key: String, categoryId: String) {
        _uiState.update { state ->
            state.copy(sfCategoryOverrides = state.sfCategoryOverrides + (key to categoryId))
        }
        persistCurrentStateDebounced()
    }

    fun parseSfInBackground(filePath: String) {
        val state = _uiState.value
        if (state.sfParsedPresets.containsKey(filePath)) return
        if (state.sfParsingFiles.contains(filePath)) return
        _uiState.update { it.copy(sfParsingFiles = it.sfParsingFiles + filePath) }
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(filePath)
            val parsed = try {
                kotlinx.coroutines.withTimeoutOrNull(2_500L) {
                    com.soundstage.mixer.model.SF2Parser.parsePresets(file)
                } ?: emptyList()
            } catch (e: Exception) {
                Log.w("MixerViewModel", "SF2 parse failed for $filePath: ${e.message}")
                emptyList()
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(
                    sfParsedPresets = it.sfParsedPresets + (filePath to parsed),
                    sfParsingFiles = it.sfParsingFiles - filePath,
                ) }
            }
        }
    }

    fun selectScene(sceneId: String) {
        ensureEngineStarted()
        snapshotTransitionJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Completely unload all previous soundfonts and silence active voices before switching Setlist
                NativeAudioBridge.safeAllNotesOff()
                NativeAudioBridge.safeUnloadAllSoundFonts(NativeAudioBridge.ENGINE_FADER)
                NativeAudioBridge.safeTrimMemory()

                val sceneFiles = fileManager.getSceneFiles()
                val targetFile = sceneFiles.find { 
                    it.name.equals(sceneId, ignoreCase = true) || 
                    it.name.substringBeforeLast('.').equals(sceneId, ignoreCase = true) || 
                    it.name.startsWith(sceneId, ignoreCase = true) 
                } ?: return@launch
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
                val masterVol = obj.optDouble("masterVolume", _uiState.value.masterTrack.volume.toDouble()).toFloat()

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
                                reverbMix = tObj.optDouble("reverbMix", originalTrack.reverbMix.toDouble()).toFloat(),
                                velocityCurve = tObj.optDouble("velocityCurve", originalTrack.velocityCurve.toDouble()).toFloat(),
                                splitNoteMin = tObj.optInt("splitNoteMin", originalTrack.splitNoteMin),
                                splitNoteMax = tObj.optInt("splitNoteMax", originalTrack.splitNoteMax)
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

                val enabledCount = if (obj.has("visibleTracksCount")) obj.optInt("visibleTracksCount", updatedTracks.count { it.isEnabled }.coerceIn(1, 8)) else updatedTracks.count { it.isEnabled }.coerceIn(1, 8)
                val restoredDrumKit = obj.optString("activeDrumKitName", _uiState.value.activeDrumKitName)
                val restoredTonicPad = obj.optString("activeTonicPadName", _uiState.value.activeTonicPadName)
                val restoredThemeHue = obj.optDouble("themeHue", _uiState.value.themeHue.toDouble()).toFloat()
                val restoredThemeSat = obj.optDouble("themeSaturation", _uiState.value.themeSaturation.toDouble()).toFloat()
                val restoredThemeNuance = obj.optDouble("themeNuance", _uiState.value.themeNuance.toDouble()).toFloat()
                val restoredThemePalette = obj.optString("themePalette", _uiState.value.themePaletteName)
                val restoredTrackSaturation = obj.optDouble("trackColorSaturation", _uiState.value.trackColorSaturation.toDouble()).toFloat()

                // Restore Drum Pads
                val drumArr = obj.optJSONArray("drumPads")
                val updatedDrumPads = if (drumArr != null) {
                    val list = mutableListOf<DrumPadItem>()
                    for (di in 0 until drumArr.length()) {
                        val dObj = drumArr.getJSONObject(di)
                        val id = dObj.optInt("id", di + 1)
                        val origPad = _uiState.value.drumPads.find { it.id == id } ?: continue
                        val styleName = dObj.optString("styleName", origPad.colorStyle.name)
                        val matchedStyle = DrumPadStyle.entries.find { it.name.equals(styleName, ignoreCase = true) } ?: origPad.colorStyle
                        list.add(
                            origPad.copy(
                                label = dObj.optString("label", origPad.label),
                                sampleFileName = dObj.optString("sampleFileName", origPad.sampleFileName),
                                sampleFilePath = dObj.optString("sampleFilePath", origPad.sampleFilePath),
                                volume = dObj.optDouble("volume", origPad.volume.toDouble()).toFloat(),
                                colorStyle = matchedStyle
                            )
                        )
                    }
                    if (list.isNotEmpty()) list else _uiState.value.drumPads
                } else _uiState.value.drumPads

                // Restore FX Parameters
                val fxObj = obj.optJSONObject("fxParameters")
                val updatedFx = if (fxObj != null) {
                    val map = _uiState.value.fxParameters.toMutableMap()
                    val keys = fxObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val chId = k.toIntOrNull() ?: continue
                        val fo = fxObj.getJSONObject(k)
                        map[chId] = FxParameters(
                            eqLow = fo.optDouble("eqLow", 0.5).toFloat(),
                            eqMid = fo.optDouble("eqMid", 0.5).toFloat(),
                            eqHigh = fo.optDouble("eqHigh", 0.5).toFloat(),
                            eqGain = fo.optDouble("eqGain", 0.5).toFloat(),
                            isReverbEnabled = fo.optBoolean("isReverbEnabled", false),
                            reverbPreset = fo.optString("reverbPreset", "Concert Hall"),
                            reverbMix = fo.optDouble("reverbMix", 0.24).toFloat(),
                            reverbSize = fo.optDouble("reverbSize", 0.6).toFloat(),
                            reverbDecay = fo.optDouble("reverbDecay", 0.45).toFloat(),
                            reverbDamp = fo.optDouble("reverbDamp", 0.3).toFloat(),
                            isChorusEnabled = fo.optBoolean("isChorusEnabled", false),
                            chorusRate = fo.optDouble("chorusRate", 0.35).toFloat(),
                            chorusDepth = fo.optDouble("chorusDepth", 0.50).toFloat(),
                            chorusMix = fo.optDouble("chorusMix", 0.0).toFloat(),
                            isSgEnabled = fo.optBoolean("isSgEnabled", false),
                            sgAmount = fo.optDouble("sgAmount", 0.0).toFloat(),
                            sgMode = fo.optInt("sgMode", 0),
                            isCompEnabled = fo.optBoolean("isCompEnabled", false),
                            compThresh = fo.optDouble("compThresh", 0.4).toFloat(),
                            compRatio = fo.optDouble("compRatio", 0.5).toFloat(),
                            compAttack = fo.optDouble("compAttack", 0.2).toFloat(),
                            compRelease = fo.optDouble("compRelease", 0.35).toFloat(),
                            isDelayEnabled = fo.optBoolean("isDelayEnabled", false),
                            delayTime = fo.optDouble("delayTime", 0.35).toFloat(),
                            delayFeedback = fo.optDouble("delayFeedback", 0.4).toFloat(),
                            delayMix = fo.optDouble("delayMix", 0.0).toFloat(),
                            delayPingPong = fo.optDouble("delayPingPong", 0.0).toFloat()
                        )
                    }
                    map
                } else _uiState.value.fxParameters

                // Restore Scene-specific Snapshots
                val snapshotsMap = SnapshotJson.mapFromJson(obj.optJSONObject("snapshots"))

                val configuredSlots = mutableListOf<String>()
                val confSlotsArr = obj.optJSONArray("configuredSnapshotSlots")
                if (confSlotsArr != null) {
                    for (ci in 0 until confSlotsArr.length()) {
                        configuredSlots.add(confSlotsArr.getString(ci))
                    }
                }
                if (configuredSlots.isEmpty()) configuredSlots.add("slot_default")
                val customNames = mutableMapOf<String, String>()
                val customNamesObj = obj.optJSONObject("snapshotCustomNames")
                if (customNamesObj != null) {
                    val cnKeys = customNamesObj.keys()
                    while (cnKeys.hasNext()) {
                        val k = cnKeys.next()
                        customNames[k] = customNamesObj.getString(k)
                    }
                }
                val activeSlot = obj.optString("activeSnapshotSlot", "slot_default")

                val restoredPatches = mutableListOf<PatchItem>()
                val patchesJsonArr = obj.optJSONArray("patches")
                if (patchesJsonArr != null) {
                    for (pi in 0 until patchesJsonArr.length()) {
                        val pObj = patchesJsonArr.getJSONObject(pi)
                        val pId = pObj.optString("id", "patch_$pi")
                        val pName = pObj.optString("name", "Patch ${pi + 1}")
                        val pTracksCount = pObj.optInt("visibleTracksCount", enabledCount)
                        val pTranspose = pObj.optInt("transpose", transpose)
                        val pOctave = pObj.optInt("octave", octave)
                        val pBpm = pObj.optInt("bpm", bpm)
                        val pMasterVol = pObj.optDouble("masterVolume", masterVol.toDouble()).toFloat()
                        val pActiveSlot = pObj.optString("activeSnapshotSlot", "slot_default")

                        val pConfSlots = mutableListOf<String>()
                        val pConfArr = pObj.optJSONArray("configuredSnapshotSlots")
                        if (pConfArr != null) {
                            for (ci in 0 until pConfArr.length()) {
                                pConfSlots.add(pConfArr.getString(ci))
                            }
                        }
                        if (pConfSlots.isEmpty()) pConfSlots.add("slot_default")

                        val pCustomNames = mutableMapOf<String, String>()
                        val pCnObj = pObj.optJSONObject("snapshotCustomNames")
                        if (pCnObj != null) {
                            val keys = pCnObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                pCustomNames[k] = pCnObj.getString(k)
                            }
                        }

                        val pSnapshots = SnapshotJson.mapFromJson(pObj.optJSONObject("snapshots"))

                        val pTracks = mutableListOf<TrackChannel>()
                        val trArr = pObj.optJSONArray("tracks")
                        if (trArr != null) {
                            for (ti in 0 until trArr.length()) {
                                val to = trArr.getJSONObject(ti)
                                pTracks.add(
                                    TrackChannel(
                                        id = to.optInt("id", ti + 1),
                                        name = to.optString("name", "Track ${ti + 1}"),
                                        isEnabled = to.optBoolean("isEnabled", ti == 0),
                                        volume = to.optDouble("volume", 0.65).toFloat(),
                                        pan = to.optDouble("pan", 0.0).toFloat(),
                                        soundfontName = to.optString("soundfontName", ""),
                                        patchName = to.optString("patchName", "-"),
                                        bank = to.optInt("bank", 0),
                                        program = to.optInt("program", 0),
                                        reverbPreset = to.optString("reverbPreset", "Concert Hall"),
                                        reverbMix = to.optDouble("reverbMix", 0.20).toFloat()
                                    )
                                )
                            }
                        }

                        restoredPatches.add(
                            PatchItem(
                                id = pId,
                                name = pName,
                                tracks = pTracks,
                                visibleTracksCount = pTracksCount,
                                transpose = pTranspose,
                                octave = pOctave,
                                bpm = pBpm,
                                masterVolume = pMasterVol,
                                snapshots = pSnapshots,
                                configuredSnapshotSlots = pConfSlots,
                                snapshotCustomNames = pCustomNames,
                                activeSnapshotSlot = pActiveSlot
                            )
                        )
                    }
                }

                if (restoredPatches.isEmpty()) {
                    restoredPatches.add(
                        PatchItem(
                            id = "patch_${System.currentTimeMillis()}",
                            name = "Patch 1",
                            tracks = updatedTracks,
                            audioSlots = updatedSlots,
                            visibleTracksCount = enabledCount,
                            transpose = transpose,
                            octave = octave,
                            bpm = bpm,
                            masterVolume = masterVol,
                            snapshots = snapshotsMap,
                            configuredSnapshotSlots = configuredSlots,
                            snapshotCustomNames = customNames,
                            activeSnapshotSlot = activeSlot
                        )
                    )
                }

                val restoredActivePatchId = obj.optString("activePatchId", restoredPatches.firstOrNull()?.id ?: "")
                val effectiveActivePatchId = if (restoredActivePatchId.isNotEmpty() && restoredPatches.any { it.id == restoredActivePatchId }) {
                    restoredActivePatchId
                } else {
                    restoredPatches.firstOrNull()?.id ?: ""
                }
                val activePatch = restoredPatches.find { it.id == effectiveActivePatchId }
                val activeSnapshots = if (!activePatch?.snapshots.isNullOrEmpty()) activePatch!!.snapshots else snapshotsMap
                val activeConfSlots = if (!activePatch?.configuredSnapshotSlots.isNullOrEmpty()) activePatch!!.configuredSnapshotSlots else configuredSlots
                val activeCustomNames = if (!activePatch?.snapshotCustomNames.isNullOrEmpty()) activePatch!!.snapshotCustomNames else customNames
                val activeSlotName = activePatch?.activeSnapshotSlot ?: activeSlot
                val activeTracks = if (!activePatch?.tracks.isNullOrEmpty()) activePatch!!.tracks else updatedTracks

                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        state.copy(
                            activeSceneId = sceneId,
                            activeProjectName = sceneId,
                            bpm = bpm,
                            transpose = transpose,
                            octave = octave,
                            tonicBrightness = tonicBrightness,
                            tonicShimmer = tonicShimmer,
                            tonicOctaveRange = tonicOctaveRange,
                            drumVolume = drumVol,
                            drumReverb = drumRev,
                            masterTrack = state.masterTrack.copy(volume = masterVol),
                            tracks = activeTracks,
                            audioSlots = updatedSlots,
                            drumPads = updatedDrumPads,
                            fxParameters = updatedFx,
                            activeDrumKitName = restoredDrumKit,
                            activeTonicPadName = restoredTonicPad,
                            themeHue = restoredThemeHue,
                            themeSaturation = restoredThemeSat,
                            themeNuance = restoredThemeNuance,
                            themePaletteName = restoredThemePalette,
                            trackColorSaturation = restoredTrackSaturation,
                            visibleTracksCount = enabledCount,
                            snapshots = activeSnapshots,
                            configuredSnapshotSlots = activeConfSlots,
                            snapshotCustomNames = activeCustomNames,
                            activeSnapshotSlot = activeSlotName,
                            patches = restoredPatches,
                            activePatchId = effectiveActivePatchId,
                            customLibreTracks = null
                        )
                    }
                    applyTrackVolumes(_uiState.value)
                    setTonicVolume(tonicVol)
                    setTonicReverb(tonicRev)
                    setDrumVolume(drumVol)
                    setDrumReverb(drumRev)
                    audioEngine.masterVolume = masterVol
                    NativeAudioBridge.safeSetMasterVolume(masterVol)
                    audioEngine.globalTranspose = transpose
                    audioEngine.globalOctaveShift = octave

                    // Apply Master FX from restored settings
                    val masterFx = updatedFx[0]
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
                        audioEngine.setMasterDelay(
                            enabled = masterFx.isDelayEnabled && masterFx.delayMix > 0.005f,
                            timeSec = 0.05f + masterFx.delayTime * 0.95f,
                            feedback = if (masterFx.isDelayEnabled) masterFx.delayFeedback else 0f,
                            mix = if (masterFx.isDelayEnabled) masterFx.delayMix else 0f,
                            pingPong = masterFx.delayPingPong > 0.5f
                        )
                    }
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
                persistCurrentStateDebounced()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveCurrentScene(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val state = _uiState.value
                val currentPatches = if (state.activePatchId != null && state.patches.isNotEmpty()) {
                    state.patches.map { p ->
                        if (p.id == state.activePatchId) {
                            p.copy(
                                tracks = state.tracks,
                                audioSlots = state.audioSlots,
                                visibleTracksCount = state.visibleTracksCount,
                                transpose = state.transpose,
                                octave = state.octave,
                                masterVolume = state.masterTrack.volume,
                                snapshots = state.snapshots,
                                configuredSnapshotSlots = state.configuredSnapshotSlots,
                                snapshotCustomNames = state.snapshotCustomNames,
                                activeSnapshotSlot = state.activeSnapshotSlot
                            )
                        } else p
                    }
                } else state.patches

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
                    put("visibleTracksCount", state.visibleTracksCount)
                    put("activeDrumKitName", state.activeDrumKitName)
                    put("activeTonicPadName", state.activeTonicPadName)
                    put("themeHue", state.themeHue)
                    put("themeSaturation", state.themeSaturation)
                    put("themeNuance", state.themeNuance)
                    put("themePalette", state.themePaletteName)
                    put("trackColorSaturation", state.trackColorSaturation)

                    val tonicNotesArr = org.json.JSONArray()
                    state.activeTonicNotes.forEach { tonicNotesArr.put(it) }
                    put("activeTonicNotes", tonicNotesArr)

                    val drumArray = org.json.JSONArray()
                    state.drumPads.forEach { d ->
                        val dObj = org.json.JSONObject().apply {
                            put("id", d.id)
                            put("label", d.label)
                            put("soundType", d.soundType.name)
                            put("sampleFileName", d.sampleFileName)
                            put("sampleFilePath", d.sampleFilePath)
                            put("volume", d.volume)
                            put("styleName", d.colorStyle.name)
                        }
                        drumArray.put(dObj)
                    }
                    put("drumPads", drumArray)

                    val fxObj = org.json.JSONObject()
                    state.fxParameters.forEach { (slot, fx) ->
                        val o = org.json.JSONObject().apply {
                            put("eqLow", fx.eqLow)
                            put("eqMid", fx.eqMid)
                            put("eqHigh", fx.eqHigh)
                            put("eqGain", fx.eqGain)
                            put("isReverbEnabled", fx.isReverbEnabled)
                            put("reverbPreset", fx.reverbPreset)
                            put("reverbMix", fx.reverbMix)
                            put("reverbSize", fx.reverbSize)
                            put("reverbDecay", fx.reverbDecay)
                            put("reverbDamp", fx.reverbDamp)
                            put("isChorusEnabled", fx.isChorusEnabled)
                            put("chorusRate", fx.chorusRate)
                            put("chorusDepth", fx.chorusDepth)
                            put("chorusMix", fx.chorusMix)
                            put("isSgEnabled", fx.isSgEnabled)
                            put("sgAmount", fx.sgAmount)
                            put("sgMode", fx.sgMode)
                            put("isCompEnabled", fx.isCompEnabled)
                            put("compThresh", fx.compThresh)
                            put("compRatio", fx.compRatio)
                            put("compAttack", fx.compAttack)
                            put("compRelease", fx.compRelease)
                            put("isDelayEnabled", fx.isDelayEnabled)
                            put("delayTime", fx.delayTime)
                            put("delayFeedback", fx.delayFeedback)
                            put("delayMix", fx.delayMix)
                            put("delayPingPong", fx.delayPingPong)
                        }
                        fxObj.put(slot.toString(), o)
                    }
                    put("fxParameters", fxObj)

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
                            put("velocityCurve", t.velocityCurve)
                            put("splitNoteMin", t.splitNoteMin)
                            put("splitNoteMax", t.splitNoteMax)
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

                    // Save scene-specific Snapshots (Hermetic Setlist Snapshots)
                    put("snapshots", SnapshotJson.mapToJson(state.snapshots))
                    val slotsArrConfig = org.json.JSONArray()
                    state.configuredSnapshotSlots.forEach { slotsArrConfig.put(it) }
                    put("configuredSnapshotSlots", slotsArrConfig)
                    val customNamesObj = org.json.JSONObject()
                    state.snapshotCustomNames.forEach { (k, v) -> customNamesObj.put(k, v) }
                    put("snapshotCustomNames", customNamesObj)
                    put("activeSnapshotSlot", state.activeSnapshotSlot ?: "slot_default")

                    // Save Patches (Level 2) with independent snapshots & settings per patch
                    val patchesArr = org.json.JSONArray()
                    currentPatches.forEach { p ->
                        val pObj = org.json.JSONObject().apply {
                            put("id", p.id)
                            put("name", p.name)
                            put("visibleTracksCount", p.visibleTracksCount)
                            put("transpose", p.transpose)
                            put("octave", p.octave)
                            put("bpm", p.bpm)
                            put("masterVolume", p.masterVolume.toDouble())
                            put("activeSnapshotSlot", p.activeSnapshotSlot ?: "slot_default")

                            val pSlotsConfig = org.json.JSONArray()
                            p.configuredSnapshotSlots.forEach { pSlotsConfig.put(it) }
                            put("configuredSnapshotSlots", pSlotsConfig)

                            val pCustomNames = org.json.JSONObject()
                            p.snapshotCustomNames.forEach { (k, v) -> pCustomNames.put(k, v) }
                            put("snapshotCustomNames", pCustomNames)

                            put("snapshots", SnapshotJson.mapToJson(p.snapshots))

                            val pTracksArr = org.json.JSONArray()
                            p.tracks.forEach { t ->
                                val to = org.json.JSONObject().apply {
                                    put("id", t.id)
                                    put("name", t.name)
                                    put("isEnabled", t.isEnabled)
                                    put("volume", t.volume.toDouble())
                                    put("pan", t.pan.toDouble())
                                    put("soundfontName", t.soundfontName)
                                    put("patchName", t.patchName)
                                    put("bank", t.bank)
                                    put("program", t.program)
                                    put("reverbPreset", t.reverbPreset)
                                    put("reverbMix", t.reverbMix.toDouble())
                                }
                                pTracksArr.put(to)
                            }
                            put("tracks", pTracksArr)
                        }
                        patchesArr.put(pObj)
                    }
                    put("patches", patchesArr)
                    put("activePatchId", state.activePatchId ?: "")
                }

                fileManager.saveSceneFile(name, json.toString())
                val sceneFiles = fileManager.getSceneFiles()
                val updatedScenes = sceneFiles.map { f ->
                    val isCurrent = f.name.equals(name, ignoreCase = true)
                    val currentPatchesList = if (isCurrent) currentPatches else emptyList()
                    val savedColorName = appStatePersistence.getString("setlist_col_${f.name}", SetlistColor.Blue.name)
                    val savedColor = runCatching { SetlistColor.valueOf(savedColorName) }.getOrDefault(SetlistColor.Blue)
                    ScenePreset(
                        id = f.name,
                        name = f.name,
                        timestamp = if (f.formattedDate.isNotEmpty()) f.formattedDate else "Recently used",
                        color = savedColor.base,
                        patchesCount = currentPatchesList.size,
                        patches = currentPatchesList
                    )
                }

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            scenes = updatedScenes,
                            activeSceneId = name,
                            patches = currentPatches
                        )
                    }
                }
                persistCurrentStateDebounced()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveCurrentProjectDirect() {
        val current = _uiState.value.activeProjectName.ifEmpty {
            _uiState.value.activeSceneId.ifEmpty {
                "Setlist ${_uiState.value.scenes.size + 1}"
            }
        }
        saveCurrentScene(current)
        persistCurrentState()
    }

    fun deleteScene(sceneId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                fileManager.deleteSceneFile(sceneId)
                val refreshedFiles = fileManager.getSceneFiles()
                val updatedScenes = refreshedFiles.map { f ->
                    val savedColorName = appStatePersistence.getString("setlist_col_${f.name}", SetlistColor.Blue.name)
                    val savedColor = runCatching { SetlistColor.valueOf(savedColorName) }.getOrDefault(SetlistColor.Blue)
                    ScenePreset(
                        id = f.name,
                        name = f.name,
                        timestamp = f.formattedSize,
                        color = savedColor.base
                    )
                }
                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        val nextActive = if (state.activeSceneId.equals(sceneId, ignoreCase = true) || 
                                             state.activeSceneId.equals("$sceneId.scene", ignoreCase = true) || 
                                             state.activeProjectName.equals(sceneId, ignoreCase = true)) {
                            updatedScenes.firstOrNull()?.id ?: ""
                        } else state.activeSceneId
                        val nextProjectName = if (state.activeProjectName.equals(sceneId, ignoreCase = true)) nextActive else state.activeProjectName
                        state.copy(
                            scenes = updatedScenes,
                            activeSceneId = nextActive,
                            activeProjectName = nextProjectName
                        )
                    }
                    persistCurrentState()
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addSoundTrack() {
        _uiState.update { current ->
            if (current.visibleTracksCount < 8) {
                val nextTrackIdx = current.visibleTracksCount
                val updatedTracks = current.tracks.mapIndexed { idx, track ->
                    if (idx == nextTrackIdx) track.copy(isEnabled = true) else track
                }
                current.copy(
                    visibleTracksCount = current.visibleTracksCount + 1,
                    tracks = updatedTracks
                )
            } else current
        }
    }

    fun garbageCollectUnusedSoundFonts() {
        val state = _uiState.value
        val visibleTracks = state.tracks.take(state.visibleTracksCount)
        val activeSoundfontNames = visibleTracks.filter { it.isEnabled && it.soundfontName.isNotEmpty() }
            .map { it.soundfontName }
            .toSet()

        val activeSfPaths = activeSoundfontNames.mapNotNull { name ->
            val f = File(fileManager.soundfontsDir, name)
            if (f.exists()) f.absolutePath else null
        }.toSet()

        val activeSfIds = mutableSetOf<Int>()
        state.audioSlots.forEachIndexed { idx, slot ->
            if (idx < state.visibleTracksCount && visibleTracks.getOrNull(idx)?.isEnabled == true) {
                if (slot.soundFontId > 0 && !slot.soundFontPath.isNullOrEmpty() && activeSfPaths.contains(slot.soundFontPath)) {
                    activeSfIds.add(slot.soundFontId)
                }
            }
        }

        // Unload any slot soundfonts that are not in activeSfIds
        _uiState.update { current ->
            val updatedSlots = current.audioSlots.mapIndexed { idx, slot ->
                if (idx >= current.visibleTracksCount || visibleTracks.getOrNull(idx)?.isEnabled != true) {
                    if (slot.soundFontId > 0 && !activeSfIds.contains(slot.soundFontId)) {
                        NativeAudioBridge.safeUnloadSoundFont(NativeAudioBridge.ENGINE_FADER, slot.soundFontId)
                        Log.d("SoundFontUnload", "Garbage collected unused soundfont ID=${slot.soundFontId} from RAM")
                    }
                    slot.copy(soundFontId = -1, soundFontPath = null, patchName = null, presets = emptyList())
                } else slot
            }
            current.copy(audioSlots = updatedSlots)
        }
        NativeAudioBridge.safeTrimMemory()
    }

    fun removeTrack(trackId: Int) {
        val targetIdx = trackId - 1
        if (targetIdx in 0..7) {
            audioEngine.setChannelEnabled(targetIdx, false)
            NativeAudioBridge.safeAllNotesOff(targetIdx)
        }
        _uiState.update { current ->
            if (current.visibleTracksCount > 1) {
                val newCount = current.visibleTracksCount - 1
                val updatedTracks = current.tracks.mapIndexed { idx, track ->
                    if (track.id == trackId) {
                        track.copy(isEnabled = false, volume = 0.65f, soundfontName = "", patchName = "")
                    } else if (idx >= newCount) {
                        track.copy(isEnabled = false)
                    } else track
                }
                val updatedSlots = current.audioSlots.mapIndexed { idx, slot ->
                    if (idx == targetIdx || idx >= newCount) {
                        if (slot.soundFontId > 0) {
                            val stillUsed = updatedTracks.take(newCount).any { it.isEnabled && it.soundfontName.isNotEmpty() && slot.soundFontPath?.endsWith(it.soundfontName) == true }
                            if (!stillUsed) {
                                NativeAudioBridge.safeUnloadSoundFont(NativeAudioBridge.ENGINE_FADER, slot.soundFontId)
                            }
                        }
                        slot.copy(soundFontId = -1, soundFontPath = null, patchName = null, presets = emptyList())
                    } else slot
                }
                current.copy(
                    visibleTracksCount = newCount,
                    tracks = updatedTracks,
                    audioSlots = updatedSlots
                )
            } else current
        }
        NativeAudioBridge.safeTrimMemory()
        persistCurrentStateDebounced()
    }

    /**
     * Captures the whole live mixer into a snapshot: every track, the global
     * transpose/octave, the master level and the master FX.
     *
     * [tracks] and the scalar overrides exist so a patch's own stored values can be
     * captured while it is not the live state yet — a patch is loaded by building
     * its default slot before its tracks reach `_uiState`.
     */
    private fun captureSnapshot(
        slotName: String,
        state: MixerUiState,
        tracks: List<TrackChannel> = state.tracks,
        globalTranspose: Int = state.transpose,
        globalOctaveShift: Int = state.octave,
        masterVolume: Float = state.masterTrack.volume,
        visibleTracksCount: Int = state.visibleTracksCount,
        fxParameters: FxParameters? = state.fxParameters[0],
        bpm: Int = state.transportBpm,
        trackFx: Map<Int, FxParameters> = state.fxParameters
    ) = SubSceneSnapshot(
        slotName = slotName,
        tracks = tracks.map { SnapshotLogic.captureTrack(it) },
        globalTranspose = globalTranspose,
        globalOctaveShift = globalOctaveShift,
        masterVolume = masterVolume,
        visibleTracksCount = visibleTracksCount,
        fxParameters = fxParameters,
        bpm = bpm,
        trackFx = trackFx
    )

    /** Absolute path of a soundfont named [soundfontName] inside the app's SoundFonts dir, or null. */
    private fun resolveSoundfontPath(soundfontName: String): String? {
        if (soundfontName.isEmpty()) return null
        val f = File(fileManager.soundfontsDir, soundfontName)
        return if (f.exists()) f.absolutePath else null
    }

    /**
     * Applies a snapshot entry back onto a live track. See [SnapshotLogic.applyToTrack].
     */
    private fun applySnapshotToTrack(track: TrackChannel, snap: TrackSnapshot?): TrackChannel =
        SnapshotLogic.applyToTrack(track, snap)

    fun addSnapshotSlot(name: String): String? {
        val cleanName = name.trim().ifEmpty { "Snapshot ${_uiState.value.configuredSnapshotSlots.size}" }
        var createdKey: String? = null
        _uiState.update { current ->
            if (current.configuredSnapshotSlots.size < 5) {
                val newSlotKey = "slot_${System.currentTimeMillis()}"
                createdKey = newSlotKey
                val updatedSlots = current.configuredSnapshotSlots + newSlotKey
                val updatedNames = current.snapshotCustomNames + (newSlotKey to cleanName)
                val currentSnapshot = captureSnapshot(cleanName, current)
                val updatedSnapshots = current.snapshots + (newSlotKey to currentSnapshot)
                val updatedPatches = current.patches.map { p ->
                    if (p.id == current.activePatchId) {
                        p.copy(
                            configuredSnapshotSlots = updatedSlots,
                            snapshotCustomNames = updatedNames,
                            snapshots = updatedSnapshots,
                            activeSnapshotSlot = newSlotKey
                        )
                    } else p
                }
                current.copy(
                    configuredSnapshotSlots = updatedSlots,
                    snapshotCustomNames = updatedNames,
                    snapshots = updatedSnapshots,
                    activeSnapshotSlot = newSlotKey,
                    patches = updatedPatches
                )
            } else current
        }
        persistCurrentStateDebounced()
        return createdKey
    }

    fun removeSnapshotSlot(slotKey: String) {
        if (slotKey == "slot_default") return
        _uiState.update { current ->
            val updatedSlots = current.configuredSnapshotSlots.filter { it != slotKey }
            val updatedNames = current.snapshotCustomNames - slotKey
            val updatedSnapshots = current.snapshots - slotKey
            val newActive = if (current.activeSnapshotSlot == slotKey) "slot_default" else current.activeSnapshotSlot
            val updatedPatches = current.patches.map { p ->
                if (p.id == current.activePatchId) {
                    p.copy(
                        configuredSnapshotSlots = updatedSlots,
                        snapshotCustomNames = updatedNames,
                        snapshots = updatedSnapshots,
                        activeSnapshotSlot = newActive
                    )
                } else p
            }
            current.copy(
                configuredSnapshotSlots = updatedSlots,
                snapshotCustomNames = updatedNames,
                snapshots = updatedSnapshots,
                activeSnapshotSlot = newActive,
                patches = updatedPatches
            )
        }
        persistCurrentStateDebounced()
    }

    fun openProject(projectName: String) {
        ensureEngineStarted()
        val currentActive = _uiState.value.activeProjectName
        if (projectName.equals(currentActive, ignoreCase = true) && currentActive.isNotEmpty() && currentActive != "None") {
            // Already active Setlist is loaded in memory - instantly switch page without interrupting audio!
            _uiState.update { it.copy(currentPage = AppScreenPage.MIXER) }
        } else {
            selectScene(projectName)
            _uiState.update {
                it.copy(
                    activeProjectName = projectName,
                    currentPage = AppScreenPage.MIXER
                )
            }
        }
    }

    fun deleteProject(projectName: String) {
        deleteScene(projectName)
    }

    fun createNewProject(projectName: String = "Setlist", isBlank: Boolean = true) {
        snapshotTransitionJob?.cancel()
        val cleanName = projectName.trim().ifEmpty { "Setlist ${_uiState.value.scenes.size + 1}" }
        val defaultPatchId = "patch_${System.currentTimeMillis()}"
        if (isBlank) {
            NativeAudioBridge.safeAllNotesOff()
            NativeAudioBridge.safeUnloadAllSoundFonts(NativeAudioBridge.ENGINE_FADER)
            NativeAudioBridge.safeTrimMemory()
            val fresh = createInitialState()
            val initialPatch = PatchItem(
                id = defaultPatchId,
                name = "Patch 1",
                tracks = fresh.tracks,
                audioSlots = fresh.audioSlots,
                visibleTracksCount = 1,
                transpose = 0,
                octave = 0,
                masterVolume = 0.85f,
                snapshots = emptyMap(),
                configuredSnapshotSlots = listOf("slot_default"),
                activeSnapshotSlot = "slot_default"
            )
            _uiState.update { current ->
                current.copy(
                    activeProjectName = cleanName,
                    activeSceneId = cleanName,
                    tracks = fresh.tracks,
                    audioSlots = fresh.audioSlots,
                    drumPads = fresh.drumPads,
                    visibleTracksCount = 1,
                    transpose = 0,
                    octave = 0,
                    snapshots = emptyMap(),
                    configuredSnapshotSlots = listOf("slot_default"),
                    snapshotCustomNames = emptyMap(),
                    activeSnapshotSlot = "slot_default",
                    patches = listOf(initialPatch),
                    activePatchId = defaultPatchId,
                    customLibreTracks = null,
                    currentPage = AppScreenPage.MIXER
                )
            }
            saveCurrentScene(cleanName)
        } else {
            val current = _uiState.value
            val initialPatch = PatchItem(
                id = defaultPatchId,
                name = "Patch 1",
                tracks = current.tracks,
                audioSlots = current.audioSlots,
                visibleTracksCount = current.visibleTracksCount,
                transpose = current.transpose,
                octave = current.octave,
                masterVolume = current.masterTrack.volume,
                snapshots = current.snapshots,
                configuredSnapshotSlots = current.configuredSnapshotSlots,
                snapshotCustomNames = current.snapshotCustomNames,
                activeSnapshotSlot = current.activeSnapshotSlot
            )
            _uiState.update { curr ->
                curr.copy(
                    activeProjectName = cleanName,
                    activeSceneId = cleanName,
                    patches = listOf(initialPatch),
                    activePatchId = defaultPatchId,
                    customLibreTracks = null,
                    currentPage = AppScreenPage.MIXER
                )
            }
            saveCurrentScene(cleanName)
        }
    }

    fun navigateToProjectsHome() {
        _uiState.update { it.copy(currentPage = AppScreenPage.SETLISTS) }
    }

    // ================= SETLISTS SCREEN (V2 FINAL DESIGN ACTIONS) =================
    fun openSetlistFromScreen(id: String) {
        val target = _uiState.value.scenes.find { it.id == id || it.name == id } ?: return
        val now = System.currentTimeMillis()
        appStatePersistence.saveLong("setlist_used_${target.id}", now)
        appStatePersistence.saveString("setlist_last_opened", target.id)
        _uiState.update { it.copy(lastOpenedSetlistId = target.id) }
        openProject(target.name)
    }

    fun createSetlistFromScreen(name: String, mode: CreateMode) {
        val cleanName = name.trim().ifEmpty { "Setlist ${_uiState.value.scenes.size + 1}" }
        val now = System.currentTimeMillis()
        appStatePersistence.saveLong("setlist_used_$cleanName", now)

        // Couleur aléatoire de la palette, en évitant la dernière utilisée.
        val existingColors = _uiState.value.scenes.mapNotNull { scene ->
            runCatching {
                SetlistColor.valueOf(
                    appStatePersistence.getString("setlist_col_${scene.name}", "")
                )
            }.getOrNull()
        }
        val lastColor = existingColors.lastOrNull()
        val palette = SetlistColor.values()
        val available = palette.filter { it != lastColor }
        val chosen = (if (available.isEmpty()) palette else available.toTypedArray()).random()
        appStatePersistence.saveString("setlist_col_$cleanName", chosen.name)

        viewModelScope.launch(Dispatchers.IO) {
            val defaultPatchId = "patch_${System.currentTimeMillis()}"
            if (mode == CreateMode.Duplicate && _uiState.value.activeProjectName.isNotEmpty() && _uiState.value.activeProjectName != "None") {
                val current = _uiState.value
                val initialPatch = PatchItem(
                    id = defaultPatchId,
                    name = "Patch 1",
                    tracks = current.tracks,
                    audioSlots = current.audioSlots,
                    visibleTracksCount = current.visibleTracksCount,
                    transpose = current.transpose,
                    octave = current.octave,
                    masterVolume = current.masterTrack.volume,
                    snapshots = current.snapshots,
                    configuredSnapshotSlots = current.configuredSnapshotSlots,
                    snapshotCustomNames = current.snapshotCustomNames,
                    activeSnapshotSlot = current.activeSnapshotSlot
                )
                val jsonToSave = org.json.JSONObject().apply {
                    put("name", cleanName)
                    put("bpm", current.bpm)
                    put("transpose", current.transpose)
                    put("octave", current.octave)
                    put("tonicBrightness", current.tonicBrightness)
                    put("tonicShimmer", current.tonicShimmer)
                    put("drumVolume", current.drumVolume)
                    put("drumReverb", current.drumReverb)
                    put("masterVolume", current.masterTrack.volume)
                    put("visibleTracksCount", current.visibleTracksCount)
                    put("patches", org.json.JSONArray().apply {
                        current.patches.ifEmpty { listOf(initialPatch) }.forEach { p ->
                            put(org.json.JSONObject().apply {
                                put("id", p.id)
                                put("name", p.name)
                                put("visibleTracksCount", p.visibleTracksCount)
                            })
                        }
                    })
                }.toString()
                fileManager.saveSceneFile(cleanName, jsonToSave)
            } else {
                val fresh = createInitialState()
                val initialPatch = PatchItem(
                    id = defaultPatchId,
                    name = "Patch 1",
                    tracks = fresh.tracks,
                    audioSlots = fresh.audioSlots,
                    visibleTracksCount = 1,
                    transpose = 0,
                    octave = 0,
                    masterVolume = 0.85f,
                    snapshots = emptyMap(),
                    configuredSnapshotSlots = listOf("slot_default"),
                    activeSnapshotSlot = "slot_default"
                )
                val jsonToSave = org.json.JSONObject().apply {
                    put("name", cleanName)
                    put("bpm", 120)
                    put("transpose", 0)
                    put("octave", 0)
                    put("tonicBrightness", 0.7)
                    put("tonicShimmer", 0.15)
                    put("drumVolume", 0.75)
                    put("drumReverb", 0.24)
                    put("masterVolume", 0.85)
                    // A brand new setlist starts on a single track. Without this key the
                    // loader falls back to `count { it.isEnabled }`, which is 8, and the
                    // setlist opened on eight faders.
                    put("visibleTracksCount", 1)
                    put("patches", org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply {
                            put("id", initialPatch.id)
                            put("name", initialPatch.name)
                            put("visibleTracksCount", 1)
                        })
                    })
                }.toString()
                fileManager.saveSceneFile(cleanName, jsonToSave)
            }
            refreshStorageFiles()
        }
    }

    fun renameSetlistFromScreen(id: String, newName: String) {
        val cleanNew = newName.trim().ifEmpty { return }
        viewModelScope.launch(Dispatchers.IO) {
            val sceneFiles = fileManager.getSceneFiles()
            val target = sceneFiles.find { it.name.equals(id, ignoreCase = true) || it.name.equals("$id.lkproj", ignoreCase = true) }
            if (target != null) {
                val oldFile = File(target.path)
                val newFile = File(oldFile.parentFile, "$cleanNew.${oldFile.extension}")
                if (!newFile.exists()) {
                    oldFile.renameTo(newFile)
                }
                // Also migrate color/pin/usedAt keys
                val col = appStatePersistence.getString("setlist_col_$id", "")
                if (col.isNotEmpty()) appStatePersistence.saveString("setlist_col_$cleanNew", col)
                val pin = appStatePersistence.getBoolean("setlist_pin_$id", false)
                if (pin) appStatePersistence.saveBoolean("setlist_pin_$cleanNew", true)
                val used = appStatePersistence.getLong("setlist_used_$id", 0L)
                if (used > 0) appStatePersistence.saveLong("setlist_used_$cleanNew", used)
                
                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        if (state.activeProjectName.equals(id, ignoreCase = true)) {
                            state.copy(activeProjectName = cleanNew, activeSceneId = cleanNew)
                        } else state
                    }
                }
                refreshStorageFiles()
            }
        }
    }

    fun changeSetlistColor(id: String, color: SetlistColor) {
        appStatePersistence.saveString("setlist_col_$id", color.name)
        _uiState.update { state ->
            state.copy(
                scenes = state.scenes.map { s ->
                    if (s.id == id || s.name == id) s.copy(color = color.base) else s
                }
            )
        }
    }

    fun togglePinSetlist(id: String) {
        val cur = appStatePersistence.getBoolean("setlist_pin_$id", false)
        appStatePersistence.saveBoolean("setlist_pin_$id", !cur)
        _uiState.update { it.copy(lastMidiTriggerTime = System.currentTimeMillis()) }
    }

    fun duplicateSetlistFromScreen(id: String) {
        val sourceScene = _uiState.value.scenes.find { it.id == id || it.name == id } ?: return
        val copyName = "${sourceScene.name} copy"
        viewModelScope.launch(Dispatchers.IO) {
            val sceneFiles = fileManager.getSceneFiles()
            val sourceFile = sceneFiles.find { it.name.equals(id, ignoreCase = true) || it.name.equals("$id.lkproj", ignoreCase = true) }
            val content = if (sourceFile != null) fileManager.loadSceneFile(sourceFile.path) else null
            if (content != null) {
                fileManager.saveSceneFile(copyName, content)
            }
            val sourceColorName = appStatePersistence.getString("setlist_col_$id", SetlistColor.Blue.name)
            val sourceColor = runCatching { SetlistColor.valueOf(sourceColorName) }.getOrDefault(SetlistColor.Blue)
            val nextCol = SetlistsLogic.autoColor(emptyList(), after = sourceColor)
            appStatePersistence.saveString("setlist_col_$copyName", nextCol.name)
            appStatePersistence.saveLong("setlist_used_$copyName", System.currentTimeMillis())
            refreshStorageFiles()
        }
    }

    fun deleteSetlistWithUndo(id: String) {
        val target = _uiState.value.scenes.find { it.id == id || it.name == id }
        _uiState.update { it.copy(lastDeletedScene = target) }
        deleteScene(id)
    }

    fun undoDeleteSetlist() {
        val deleted = _uiState.value.lastDeletedScene ?: return
        _uiState.update { it.copy(lastDeletedScene = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val jsonToSave = org.json.JSONObject().apply {
                put("name", deleted.name)
                put("bpm", 120)
                put("patches", org.json.JSONArray().apply {
                    deleted.patches.forEach { p ->
                        put(org.json.JSONObject().apply {
                            put("id", p.id)
                            put("name", p.name)
                        })
                    }
                })
            }.toString()
            fileManager.saveSceneFile(deleted.name, jsonToSave)
            refreshStorageFiles()
        }
    }

    fun setSetlistsQuery(query: String) {
        _uiState.update { it.copy(setlistsQuery = query) }
    }

    fun setSetlistsSort(sort: SetlistSort) {
        _uiState.update { it.copy(setlistsSort = sort) }
        if (_uiState.value.setlistsPrefs.rememberSort) {
            appStatePersistence.saveString("setlists_sort", sort.name)
        }
    }

    fun setSetlistsOnlyPinned(onlyPinned: Boolean) {
        _uiState.update { it.copy(setlistsOnlyPinned = onlyPinned) }
        if (_uiState.value.setlistsPrefs.rememberSort) {
            appStatePersistence.saveBoolean("setlists_only_pinned", onlyPinned)
        }
    }

    fun updateSetlistsPrefs(prefs: SetlistsPrefs) {
        _uiState.update { it.copy(setlistsPrefs = prefs) }
        appStatePersistence.saveBoolean("setlists_pref_anim_bg", prefs.animatedBackground)
        appStatePersistence.saveBoolean("setlists_pref_auras", prefs.cardAuras)
        appStatePersistence.saveBoolean("setlists_pref_compact", prefs.compactRows)
        appStatePersistence.saveBoolean("setlists_pref_dots", prefs.patchDots)
        appStatePersistence.saveString("setlists_pref_open_with", prefs.openWith.name)
        appStatePersistence.saveBoolean("setlists_pref_pinned_top", prefs.pinnedOnTop)
        appStatePersistence.saveBoolean("setlists_pref_rem_sort", prefs.rememberSort)
        appStatePersistence.saveBoolean("setlists_pref_lock_del", prefs.lockDeletion)
        appStatePersistence.saveBoolean("setlists_pref_haptics", prefs.haptics)
    }

    fun resetSetlistsPrefs() {
        val def = SetlistsPrefs()
        updateSetlistsPrefs(def)
    }

    fun updateActiveScene() {
        val currentActiveId = _uiState.value.activeSceneId.ifEmpty {
            _uiState.value.activeProjectName
        }
        saveCurrentScene(currentActiveId)
    }

    // ================= PATCHES MANAGEMENT (LEVEL 2) =================
    fun createNewPatch(patchName: String = "Patch", isBlank: Boolean = true) {
        val state = _uiState.value
        val cleanName = patchName.trim().ifEmpty { "Patch ${state.patches.size + 1}" }
        val newPatchId = "patch_${System.currentTimeMillis()}"
        val freshState = createInitialState()

        // 1. Save current active patch first
        val patchesWithSavedCurrent = state.patches.map { p ->
            if (p.id == state.activePatchId) {
                p.copy(
                    tracks = state.tracks,
                    audioSlots = state.audioSlots,
                    visibleTracksCount = state.visibleTracksCount,
                    transpose = state.transpose,
                    octave = state.octave,
                    masterVolume = state.masterTrack.volume,
                    snapshots = state.snapshots,
                    configuredSnapshotSlots = state.configuredSnapshotSlots,
                    snapshotCustomNames = state.snapshotCustomNames,
                    activeSnapshotSlot = state.activeSnapshotSlot
                )
            } else p
        }

        val newPatch = PatchItem(
            id = newPatchId,
            name = cleanName,
            tracks = if (isBlank) freshState.tracks else state.tracks,
            audioSlots = if (isBlank) freshState.audioSlots else state.audioSlots,
            visibleTracksCount = if (isBlank) 1 else state.visibleTracksCount,
            transpose = if (isBlank) 0 else state.transpose,
            octave = if (isBlank) 0 else state.octave,
            masterVolume = state.masterTrack.volume,
            snapshots = emptyMap(),
            configuredSnapshotSlots = listOf("slot_default"),
            snapshotCustomNames = emptyMap(),
            activeSnapshotSlot = "slot_default"
        )

        val updatedPatches = patchesWithSavedCurrent + newPatch
        _uiState.update { current ->
            current.copy(
                patches = updatedPatches,
                activePatchId = newPatchId,
                tracks = newPatch.tracks,
                audioSlots = newPatch.audioSlots,
                visibleTracksCount = newPatch.visibleTracksCount,
                transpose = newPatch.transpose,
                octave = newPatch.octave,
                snapshots = emptyMap(),
                configuredSnapshotSlots = listOf("slot_default"),
                snapshotCustomNames = emptyMap(),
                activeSnapshotSlot = "slot_default"
            )
        }
        saveCurrentProjectDirect()
    }

    fun selectPatch(patchId: String) {
        snapshotTransitionJob?.cancel()
        val state = _uiState.value
        // 1. Save current state into current active patch
        val patchesWithSavedCurrent = state.patches.map { p ->
            if (p.id == state.activePatchId) {
                p.copy(
                    tracks = state.tracks,
                    audioSlots = state.audioSlots,
                    visibleTracksCount = state.visibleTracksCount,
                    transpose = state.transpose,
                    octave = state.octave,
                    masterVolume = state.masterTrack.volume,
                    snapshots = state.snapshots,
                    configuredSnapshotSlots = state.configuredSnapshotSlots,
                    snapshotCustomNames = state.snapshotCustomNames,
                    activeSnapshotSlot = state.activeSnapshotSlot
                )
            } else p
        }

        val targetPatch = patchesWithSavedCurrent.find { it.id == patchId || it.name == patchId } ?: return

        // 2. Cut off active audio voices immediately
        audioEngine.allNotesOff()
        NativeAudioBridge.safeAllNotesOff()

        // 2b. Reset the FX queues and close the FX window on a patch change.
        // Switching patch loads a different set of effects on every strip, so any
        // delay/reverb tail still ringing belongs to the previous patch and must be
        // flushed, otherwise it bleeds into the new one. fxPanic() clears the FX
        // delay/reverb buffers; the EFFECTS popup is closed so the window never
        // keeps showing the previous patch's rack after the swap.
        NativeAudioBridge.safeFxPanic()
        if (state.activePopup == ActivePopup.EFFECTS) {
            _uiState.update { it.copy(activePopup = ActivePopup.NONE) }
        }

        // 3. Prepare target patch snapshots and ensure slot_default always exists for this patch
        val targetSnapshots = targetPatch.snapshots.toMutableMap()
        if (!targetSnapshots.containsKey("slot_default")) {
            // Built from the patch's own stored values, not from the live state: the
            // patch has not been loaded yet at this point.
            targetSnapshots["slot_default"] = captureSnapshot(
                slotName = "Default",
                state = state,
                tracks = targetPatch.tracks,
                globalTranspose = targetPatch.transpose,
                globalOctaveShift = targetPatch.octave,
                masterVolume = targetPatch.masterVolume,
                visibleTracksCount = targetPatch.visibleTracksCount,
                // A patch stores no FX of its own, so its default slot carries none.
                // Capturing the live master strip here would write the outgoing
                // patch's EQ into the incoming patch.
                fxParameters = null,
                trackFx = emptyMap()
            )
        }
        val targetConfiguredSlots = if (targetPatch.configuredSnapshotSlots.isNotEmpty()) targetPatch.configuredSnapshotSlots else listOf("slot_default")
        val targetCustomNames = targetPatch.snapshotCustomNames
        val targetActiveSlot = targetPatch.activeSnapshotSlot ?: "slot_default"

        // 4. Memory management: free every soundfont the outgoing patch loaded and the
        // incoming patch does not need.
        //
        // The previous version unloaded the native buffers but left `audioSlots`
        // untouched, so each slot kept a `soundFontId` pointing at freed memory. The
        // next load either reused a stale id (a silent track) or pushed a freed id to
        // `selectProgram` (a native abort). It also keyed the "still needed" set on
        // `targetPatch.tracks` alone, ignoring the slots the patch carries and the
        // Drum/Pad surfaces, so an empty-track patch unloaded everything.
        val targetSoundfontPaths = SnapshotLogic.retainedSoundfontPaths(
            targetTracks = targetPatch.tracks.ifEmpty { state.tracks },
            targetSlots = targetPatch.audioSlots,
            currentSlots = state.audioSlots,
            resolvePath = ::resolveSoundfontPath,
        )

        val releasedSlotIds = SnapshotLogic.slotsToRelease(state.audioSlots, targetSoundfontPaths)
        val releasedSlots = releasedSlotIds.mapNotNull { slotId ->
            val slot = state.audioSlots.firstOrNull { it.slotId == slotId } ?: return@mapNotNull null
            NativeAudioBridge.safeUnloadSoundFont(NativeAudioBridge.ENGINE_FADER, slot.soundFontId)
            Log.d("SoundFontUnload", "Released slot $slotId (id=${slot.soundFontId}) on patch change")
            // The id must be cleared in the state too: keeping a freed id would make
            // the next load reuse it (silent track) or hand it to selectProgram (abort).
            slot.copy(soundFontId = -1, soundFontPath = null, patchName = null, presets = emptyList())
        }

        // 4a. The outgoing patch was saved in step 1 with its slots still live, so it
        // now holds ids this step just freed. Without this, switching back to it handed
        // the native mixer a released id: a silent track, or an abort on selectProgram.
        // `patches` is kept in memory for the whole session, so the stale ids survived
        // every later switch. Clearing them here makes the patch reload from disk on
        // the way back, which is exactly the "the previous patch is fully unloaded"
        // behaviour: no audio buffer and no id of a freed one is retained.
        val patchesAfterRelease = if (releasedSlots.isEmpty()) patchesWithSavedCurrent else {
            val releasedById = releasedSlots.associateBy { it.slotId }
            patchesWithSavedCurrent.map { p ->
                if (p.id == state.activePatchId && p.audioSlots.isNotEmpty()) {
                    p.copy(audioSlots = p.audioSlots.map { releasedById[it.slotId] ?: it })
                } else p
            }
        }
        NativeAudioBridge.safeTrimMemory()
        // 4b. Clear all active voices, reset sustain, pitch and modulation to avoid any cross-patch bleed/parasites
        for (ch in 0..7) {
            NativeAudioBridge.safeAllNotesOff(ch)
            NativeAudioBridge.safeControlChange(ch, 64, 0) // Sustain pedal off
            NativeAudioBridge.safePitchBend(ch, 8192) // Pitch bend center
            NativeAudioBridge.safeControlChange(ch, 1, 0) // Modulation wheel zero
        }

        // 5. Restore target tracks from active snapshot if available (or exact saved patch states)
        // This ensures Solo/Mute (S/M) and snapshot settings are immediately active without needing a back-and-forth click!
        val snapshotToApply = targetSnapshots[targetActiveSlot] ?: targetSnapshots["slot_default"]
        val restoredTracks = if (snapshotToApply != null && snapshotToApply.tracks.isNotEmpty()) {
            val baseTracks = if (targetPatch.tracks.isNotEmpty()) targetPatch.tracks else state.tracks
            baseTracks.map { tr ->
                // Unlike a snapshot recall, a patch's own track list is authoritative:
                // a track missing from the snapshot is left as the patch stored it.
                val snap = snapshotToApply.tracks.find { it.id == tr.id }
                if (snap != null) applySnapshotToTrack(tr, snap) else tr
            }
        } else if (targetPatch.tracks.isNotEmpty()) {
            targetPatch.tracks
        } else {
            state.tracks
        }

        _uiState.update { current ->
            current.copy(
                patches = patchesAfterRelease,
                activePatchId = targetPatch.id,
                tracks = restoredTracks,
                audioSlots = releasedSlots.fold(
                    if (targetPatch.audioSlots.isNotEmpty()) targetPatch.audioSlots else current.audioSlots
                ) { acc, released -> acc.map { if (it.slotId == released.slotId) released else it } },
                visibleTracksCount = snapshotToApply?.visibleTracksCount ?: targetPatch.visibleTracksCount,
                transpose = snapshotToApply?.globalTranspose ?: targetPatch.transpose,
                octave = snapshotToApply?.globalOctaveShift ?: targetPatch.octave,
                snapshots = targetSnapshots,
                configuredSnapshotSlots = targetConfiguredSlots,
                snapshotCustomNames = targetCustomNames,
                activeSnapshotSlot = targetActiveSlot,
                customLibreTracks = null // Reset custom libre state so it never recalls the previous patch
            )
        }

        // Apply soundfonts for loaded tracks
        restoredTracks.forEachIndexed { idx, track ->
            if (track.soundfontName.isNotEmpty()) {
                val candidate = File(fileManager.soundfontsDir, track.soundfontName)
                if (candidate.exists()) {
                    loadSoundFontForSlot(idx, candidate.absolutePath, track.bank, track.program, track.patchName, saveAfterLoad = false)
                }
            }
        }
        applyTrackVolumes(_uiState.value)
        audioEngine.globalTranspose = snapshotToApply?.globalTranspose ?: targetPatch.transpose
        audioEngine.globalOctaveShift = snapshotToApply?.globalOctaveShift ?: targetPatch.octave

        saveCurrentProjectDirect()
    }

    fun deletePatch(patchId: String) {
        val state = _uiState.value
        val idx = state.patches.indexOfFirst { it.id == patchId || it.name == patchId }
        if (idx >= 0) {
            lastDeletedPatch = state.patches[idx]
            lastDeletedPatchIndex = idx
        }
        val updatedPatches = state.patches.filterNot { it.id == patchId || it.name == patchId }
        val nextActivePatch = updatedPatches.lastOrNull()
        _uiState.update { current ->
            current.copy(
                patches = updatedPatches,
                activePatchId = nextActivePatch?.id,
                snapshots = nextActivePatch?.snapshots ?: emptyMap(),
                configuredSnapshotSlots = nextActivePatch?.configuredSnapshotSlots ?: listOf("slot_default"),
                snapshotCustomNames = nextActivePatch?.snapshotCustomNames ?: emptyMap(),
                activeSnapshotSlot = nextActivePatch?.activeSnapshotSlot ?: "slot_default",
                tracks = nextActivePatch?.tracks ?: current.tracks,
                audioSlots = nextActivePatch?.audioSlots ?: current.audioSlots
            )
        }
        saveCurrentProjectDirect()
    }

    private var lastDeletedPatch: PatchItem? = null
    private var lastDeletedPatchIndex: Int = -1

    fun undoDeletePatch() {
        val p = lastDeletedPatch ?: return
        val idx = lastDeletedPatchIndex
        _uiState.update { state ->
            val list = state.patches.toMutableList()
            list.add(idx.coerceIn(0, list.size), p)
            state.copy(patches = list)
        }
        lastDeletedPatch = null
        lastDeletedPatchIndex = -1
        saveCurrentProjectDirect()
    }

    fun renamePatch(patchId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val updatedPatches = _uiState.value.patches.map { p ->
            if (p.id == patchId || p.name == patchId) p.copy(name = trimmed) else p
        }
        _uiState.update { current ->
            current.copy(patches = updatedPatches)
        }
        saveCurrentProjectDirect()
    }

    // ================= SNAPSHOTS / SUB-SCENES (SECTION 2) =================
    fun toggleSnapshotArm() {
        _uiState.update { it.copy(isSnapshotArmMode = !it.isSnapshotArmMode) }
    }

    fun renameSnapshotSlot(slotName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { current ->
            val updated = current.snapshotCustomNames + (slotName to trimmed)
            val updatedPatches = current.patches.map { p ->
                if (p.id == current.activePatchId) {
                    p.copy(snapshotCustomNames = updated)
                } else p
            }
            current.copy(
                snapshotCustomNames = updated,
                patches = updatedPatches
            )
        }
        persistCurrentStateDebounced()
    }

    /**
     * Overload de renameSnapshotSlot qui persiste aussi le recallMode choisi
     * dans ManageSnapshotDialogV2. Appelé depuis MixerBottomBarV2 -> onSave.
     */
    fun renameSnapshotSlotWithMode(
        slotName: String,
        newName: String,
        recallMode: String
    ) {
        renameSnapshotSlot(slotName, newName)
        _uiState.update { current ->
            val existing = current.snapshots[slotName] ?: return@update current
            val updatedSnapshots = current.snapshots.toMutableMap()
            updatedSnapshots[slotName] = existing.copy(recallMode = recallMode)
            val updatedPatches = current.patches.map { p ->
                if (p.id == current.activePatchId) {
                    p.copy(snapshots = updatedSnapshots)
                } else p
            }
            current.copy(snapshots = updatedSnapshots, patches = updatedPatches)
        }
        persistCurrentStateDebounced()
    }

    fun onSnapshotSlotClick(slotName: String) {
        val currentState = _uiState.value
        if (currentState.isSnapshotArmMode) {
            val newSubScene = captureSnapshot(slotName, currentState)
            val updatedSnapshots = currentState.snapshots + (slotName to newSubScene)
            val updatedPatches = currentState.patches.map { p ->
                if (p.id == currentState.activePatchId) {
                    p.copy(
                        snapshots = updatedSnapshots,
                        activeSnapshotSlot = slotName
                    )
                } else p
            }
            _uiState.update {
                it.copy(
                    isSnapshotArmMode = false,
                    activeSnapshotSlot = slotName,
                    snapshots = updatedSnapshots,
                    patches = updatedPatches,
                    snapshotTransitionProgress = 1.0f
                )
            }
            persistCurrentState()
        } else {
            snapshotTransitionJob?.cancel()
            isRecallingSnapshot = true
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
                        isRecallingSnapshot = false
                        persistCurrentState()
                    }
                } else {
                    _uiState.update { it.copy(activeSnapshotSlot = null, snapshotTransitionProgress = 1.0f) }
                    isRecallingSnapshot = false
                    persistCurrentState()
                }
            } else {
                // 1st tap (or switching slot)
                val targetSnapshot = currentState.snapshots[slotName] ?: if (slotName == "slot_default") {
                    val activePatch = currentState.patches.find { it.id == currentState.activePatchId }
                    activePatch?.snapshots?.get("slot_default") ?: captureSnapshot(
                        slotName = "Default",
                        state = currentState,
                        tracks = activePatch?.tracks ?: currentState.tracks,
                        globalTranspose = activePatch?.transpose ?: currentState.transpose,
                        globalOctaveShift = activePatch?.octave ?: currentState.octave,
                        masterVolume = activePatch?.masterVolume ?: currentState.masterTrack.volume,
                        visibleTracksCount = activePatch?.visibleTracksCount ?: currentState.visibleTracksCount,
                    )
                } else null

                if (targetSnapshot != null) {
                    val savedCustomTracks = currentState.customLibreTracks ?: currentState.tracks
                    val targetTracks = currentState.tracks.map { tr ->
                        applySnapshotToTrack(tr, targetSnapshot.tracks.find { it.id == tr.id })
                    }

                    // Cut off notes immediately on tracks that are turning off or muting to avoid hanging voices
                    targetTracks.forEachIndexed { idx, track ->
                        if (!track.isEnabled || track.isMuted) {
                            audioEngine.setChannelEnabled(idx, false)
                            if (idx in 0..7) {
                                NativeAudioBridge.safeAllNotesOff(idx)
                            }
                        }
                        if (track.soundfontName.isNotEmpty()) {
                            val candidate = File(fileManager.soundfontsDir, track.soundfontName)
                            val currentSlot = currentState.audioSlots.getOrNull(idx)
                            if (currentSlot != null && currentSlot.soundFontId > 0 &&
                                (currentSlot.soundFontPath == candidate.absolutePath || currentSlot.soundFontPath?.endsWith(track.soundfontName) == true)
                            ) {
                                // Already loaded in memory! Just switch preset & bank directly without re-reading file or leaking RAM
                                audioEngine.setChannelProgram(idx, track.program, track.bank)
                                NativeAudioBridge.safeSelectProgram(
                                    engineIndex = NativeAudioBridge.ENGINE_FADER,
                                    channel = idx,
                                    soundFontId = currentSlot.soundFontId,
                                    bank = track.bank,
                                    preset = track.program
                                )
                                val effectiveVol = if (track.isMuted || !track.isEnabled) 0f else track.volume
                                NativeAudioBridge.safeSetTrackVolume(idx, effectiveVol)
                            } else if (candidate.exists()) {
                                loadSoundFontForSlot(idx, candidate.absolutePath, track.bank, track.program, track.patchName, saveAfterLoad = false)
                            }
                        }
                    }

                    // Update AudioEngine globals directly
                    audioEngine.globalOctaveShift = targetSnapshot.globalOctaveShift
                    audioEngine.globalTranspose = targetSnapshot.globalTranspose
                    applySnapshotFxToEngine(targetSnapshot)

                    // Apply mute and solo states immediately to state and audio engine
                    val initialInterpTracks = startTracks.mapIndexed { idx, startTr ->
                        val targetTr = targetTracks.getOrNull(idx) ?: startTr
                        startTr.copy(
                            isMuted = targetTr.isMuted,
                            isSolo = targetTr.isSolo,
                            isEnabled = targetTr.isEnabled
                        )
                    }

                    _uiState.update {
                        it.copy(
                            tracks = initialInterpTracks,
                            customLibreTracks = savedCustomTracks,
                            activeSnapshotSlot = slotName,
                            snapshotTransitionProgress = 0.0f,
                            transpose = targetSnapshot.globalTranspose,
                            octave = targetSnapshot.globalOctaveShift,
                            visibleTracksCount = maxOf(currentState.visibleTracksCount, if (targetSnapshot.visibleTracksCount in 1..8) targetSnapshot.visibleTracksCount else currentState.visibleTracksCount)
                        )
                    }
                    applyTrackVolumes(_uiState.value)
                    persistCurrentState()

                    snapshotTransitionJob = viewModelScope.launch {
                        val totalDurationMs = 600L
                        val steps = 24
                        val stepDelay = totalDurationMs / steps
                        for (step in 1..steps) {
                            val linearProgress = step.toFloat() / steps
                            // Cosine progressive interpolation for smooth volume glide
                            val eased = (1f - kotlin.math.cos(linearProgress * kotlin.math.PI.toFloat())) / 2f

                            val interpolated = startTracks.mapIndexed { idx, startTr ->
                                val targetTr = targetTracks.getOrNull(idx) ?: startTr
                                startTr.copy(
                                    volume = startTr.volume + (targetTr.volume - startTr.volume) * eased,
                                    pan = startTr.pan + (targetTr.pan - startTr.pan) * eased,
                                    isMuted = targetTr.isMuted,
                                    isSolo = targetTr.isSolo,
                                    isEnabled = targetTr.isEnabled
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
                        applyTrackVolumes(_uiState.value)
                        pushSnapshotTracksToEngine(targetTracks)
                        isRecallingSnapshot = false
                        persistCurrentStateDebounced()
                    }
                } else {
                    _uiState.update { it.copy(activeSnapshotSlot = slotName, snapshotTransitionProgress = 1.0f) }
                    isRecallingSnapshot = false
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

    // ================= SETTINGS FULL-SCREEN PAGE & MIDI MAPPING (FINAL DESIGN) =================
    fun openSettings() {
        _uiState.update { it.copy(currentPage = AppScreenPage.SETTINGS) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(currentPage = AppScreenPage.MIXER) }
    }

    fun onSettingsTabSelected(tab: SettingsTab) {
        _uiState.update { it.copy(settingsTab = tab) }
        appStatePersistence.saveString("last_settings_tab", tab.name)
    }

    fun onSettingsQueryChanged(query: String) {
        _uiState.update { it.copy(settingsQuery = query) }
    }

    // ================= SHEETS DRAWER OVERLAY =================
    fun openSheetsDrawer() {
        _uiState.update { it.copy(isSheetsDrawerOpen = true) }
    }

    fun closeSheetsDrawer() {
        _uiState.update { it.copy(isSheetsDrawerOpen = false) }
    }

    fun toggleSheetsDrawer() {
        _uiState.update { it.copy(isSheetsDrawerOpen = !it.isSheetsDrawerOpen) }
    }

    fun setAudioEngine(engine: String) {
        val driverType = if (engine.contains("OpenSL", ignoreCase = true)) {
            NativeAudioBridge.DRIVER_OPENSL_ES
        } else {
            NativeAudioBridge.DRIVER_OBOE
        }
        NativeAudioBridge.safeSetAudioDriver(driverType)
        _uiState.update { it.copy(audioEngine = engine) }
        persistCurrentStateDebounced()
    }

    fun setAudioBufferSize(buffer: Int) {
        _uiState.update { it.copy(audioBufferSize = buffer) }
        audioEngine.setBufferSize(buffer)
        persistCurrentStateDebounced()
    }

    fun setPolyphony(poly: Int) {
        _uiState.update { it.copy(polyphony = poly) }
        audioEngine.setPolyphony(poly)
        persistCurrentStateDebounced()
    }

    fun setHeadroom(value: Float) {
        _uiState.update { it.copy(headroom = value.coerceIn(0.5f, 2.0f)) }
        persistCurrentStateDebounced()
    }

    fun setFaderAutoFit(enabled: Boolean) {
        _uiState.update { it.copy(isFaderAutoFit = enabled) }
        persistCurrentStateDebounced()
    }

    fun setAntiJitter(enabled: Boolean) {
        _uiState.update { it.copy(antiJitter = enabled) }
        persistCurrentStateDebounced()
    }

    fun setJitterWindowMs(ms: Int) {
        _uiState.update { it.copy(jitterWindowMs = ms.coerceIn(1, 30)) }
        persistCurrentStateDebounced()
    }

    fun setFluidSynthGain(gain: Float) {
        val clamped = gain.coerceIn(0.2f, 3.0f)
        _uiState.update { it.copy(fluidSynthGain = clamped) }
        audioEngine.fluidSynthGain = clamped
        persistCurrentStateDebounced()
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

    fun setHapticsEnabled(enabled: Boolean) {
        _uiState.update { it.copy(hapticsEnabled = enabled) }
        persistCurrentStateDebounced()
    }

    fun setReducedMotion(enabled: Boolean) {
        _uiState.update { it.copy(reducedMotion = enabled) }
        persistCurrentStateDebounced()
    }

    fun openLanguageDialog() {
        _uiState.update { it.copy(showLanguageDialog = true) }
    }

    fun closeLanguageDialog() {
        _uiState.update { it.copy(showLanguageDialog = false) }
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
        _uiState.update { it.copy(currentPage = AppScreenPage.MIXER) }
    }

    fun setDrumFeelSwing(swing: Int) {
        _uiState.update { it.copy(drumFeelSwing = swing.coerceIn(0, 100)) }
    }

    fun setDrumPadVolumeDirect(padId: Int, volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _uiState.update { state ->
            val updatedPads = state.drumPads.map { pad ->
                if (pad.id == padId) pad.copy(volume = clamped) else pad
            }
            state.copy(drumPads = updatedPads)
        }
        persistCurrentStateDebounced()
    }

    fun adjustDrumPadVolume(padId: Int, delta: Float) {
        _uiState.update { state ->
            val updatedPads = state.drumPads.map { pad ->
                if (pad.id == padId) {
                    val newVol = (pad.volume + delta).coerceIn(0f, 1f)
                    pad.copy(volume = newVol)
                } else pad
            }
            state.copy(drumPads = updatedPads)
        }
        persistCurrentStateDebounced()
    }

    fun setDrumPadLoopBeats(padId: Int, beatsSetting: String) {
        _uiState.update { state ->
            val updatedPads = state.drumPads.map { pad ->
                if (pad.id == padId) pad.copy(loopBeatsSetting = beatsSetting) else pad
            }
            state.copy(drumPads = updatedPads)
        }
        persistCurrentStateDebounced()
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
                    val savedColorName = appStatePersistence.getString("setlist_col_${f.name}", SetlistColor.Blue.name)
                    val savedColor = runCatching { SetlistColor.valueOf(savedColorName) }.getOrDefault(SetlistColor.Blue)
                    ScenePreset(id = f.name, name = f.name, timestamp = f.formattedSize, color = savedColor.base)
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

    fun toggleFaderAutoFit() {
        _uiState.update { it.copy(isFaderAutoFit = !it.isFaderAutoFit) }
        persistCurrentState()
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

    fun updateTrackVelocityConfig(trackId: Int, config: VelocityConfig) {
        _uiState.update { state ->
            val updated = state.tracks.map { track ->
                if (track.id == trackId) track.copy(velocityConfig = config) else track
            }
            state.copy(tracks = updated)
        }
        val processor = VelocityProcessor(config)
        audioEngine.setTrackVelocityProcessor(trackId - 1, processor)
        persistCurrentStateDebounced()
    }

    /** Pushes the per-track playback settings to the audio engine (transpose, bend range, ...). */
    private fun applyTrackPlaybackSettings(track: TrackChannel) {
        val ch = track.id - 1
        // Native transpose is expressed in semitones; the octave setting is folded in (1 octave = 12 st).
        NativeAudioBridge.safeSetTrackTranspose(ch, track.transpose + track.octave * 12)
        NativeAudioBridge.safeSetPitchBendRange(ch, track.pitchBendRange)
    }

    /**
     * Pushes the settings a snapshot recall changes on a track that the plain
     * volume/pan pair does not cover: pitch (transpose folded with octave) and the
     * reverb send. Kept next to [applyTrackPlaybackSettings] so the two engine
     * pushes cannot drift apart.
     */
    private fun pushSnapshotTrackToEngine(channel: Int, track: TrackChannel) {
        if (channel !in 0..7) return
        NativeAudioBridge.safeSetTrackTranspose(channel, track.transpose + track.octave * 12)
        audioEngine.setChannelReverb(channel, if (track.reverbMix > 0.005f) track.reverbMix else 0f)
    }

    /** [pushSnapshotTrackToEngine] over the whole track list. Called once a recall has settled. */
    private fun pushSnapshotTracksToEngine(tracks: List<TrackChannel>) {
        tracks.forEachIndexed { idx, track -> pushSnapshotTrackToEngine(idx, track) }
    }

    /**
     * Applies a snapshot's FX racks and tempo.
     *
     * A snapshot used to carry a single [FxParameters] for the master strip, so a
     * recall left every track on the previous snapshot's EQ, reverb and delay. The
     * FX map is now part of the snapshot; this pushes it into the state and into the
     * engine, then restores the tempo.
     *
     * Snapshot slots saved before this field existed carry an empty map: the live FX
     * is then left untouched, which is what the old behaviour did.
     */
    private fun applySnapshotFxToEngine(snapshot: SubSceneSnapshot) {
        if (snapshot.trackFx.isNotEmpty()) {
            _uiState.update { it.copy(fxParameters = snapshot.trackFx) }
            // Master (0) goes through the master chain, tracks 1..8 through their own
            // reverb/chorus send. Both are what the live FX panel writes.
            snapshot.trackFx.forEach { (slot, fx) ->
                if (slot in 1..8) {
                    val channel = slot - 1
                    audioEngine.setChannelReverb(channel, if (fx.isReverbEnabled) fx.reverbMix else 0f)
                    audioEngine.setChannelChorus(channel, if (fx.isChorusEnabled) fx.chorusMix else 0f)
                }
            }
        }
        val bpm = snapshot.bpm.coerceIn(40, 240)
        _uiState.update { it.copy(transportBpm = bpm) }
        transportClock.bpm = bpm
    }

    fun setTrackTranspose(trackId: Int, semitones: Int) {
        val v = semitones.coerceIn(-12, 12)
        updateTrack(trackId) { it.copy(transpose = v) }
        pushTranspose(trackId)
        persistCurrentStateDebounced()
    }

    fun setTrackOctave(trackId: Int, octave: Int) {
        val v = octave.coerceIn(-3, 3)
        updateTrack(trackId) { it.copy(octave = v) }
        pushTranspose(trackId)
        persistCurrentStateDebounced()
    }

    private fun pushTranspose(trackId: Int) {
        val track = _uiState.value.tracks.getOrNull(trackId - 1) ?: return
        NativeAudioBridge.safeSetTrackTranspose(trackId - 1, track.transpose + track.octave * 12)
    }

    fun setTrackPitchBendRange(trackId: Int, semitones: Int) {
        val v = semitones.coerceIn(1, 12)
        updateTrack(trackId) { it.copy(pitchBendRange = v) }
        NativeAudioBridge.safeSetPitchBendRange(trackId - 1, v)
        persistCurrentStateDebounced()
    }

    fun setTrackDefaultVolume(trackId: Int, volume: Int) {
        val v = volume.coerceIn(0, 127)
        updateTrack(trackId) { it.copy(defaultVolume = v) }
        persistCurrentStateDebounced()
    }

    fun setTrackDefaultPan(trackId: Int, pan: Int) {
        val v = pan.coerceIn(-64, 63)
        updateTrack(trackId) { it.copy(defaultPan = v) }
        persistCurrentStateDebounced()
    }

    fun setTrackSustain(trackId: Int, enabled: Boolean) {
        updateTrack(trackId) { it.copy(sustain = enabled) }
        if (!enabled) {
            // The Playback switch only gated the *next* note, so turning Sustain off
            // while the pedal was down left the held notes ringing and the switch
            // looked inert. Release the pedal and the sustained voices now.
            val ch = trackId - 1
            NativeAudioBridge.safeControlChange(channel = ch, cc = 64, value = 0)
            NativeAudioBridge.safeControlChange(channel = ch, cc = 123, value = 0)
        }
        persistCurrentStateDebounced()
    }

    fun setTrackAftertouch(trackId: Int, enabled: Boolean) {
        updateTrack(trackId) { it.copy(aftertouch = enabled) }
        if (!enabled) {
            // Drop any pressure already applied so the switch has a visible effect
            // instead of waiting for the controller's next message.
            NativeAudioBridge.safeChannelPressure(trackId - 1, 0)
        }
        persistCurrentStateDebounced()
    }

    fun setTrackProgramChange(trackId: Int, enabled: Boolean) {
        updateTrack(trackId) { it.copy(programChange = enabled) }
        persistCurrentStateDebounced()
    }

    /** Sets a per-track colour override; pass null to fall back to the default palette colour. */
    fun setTrackColor(trackId: Int, argb: Int?) {
        updateTrack(trackId) { it.copy(customColorArgb = argb) }
        persistCurrentStateDebounced()
    }

    // ================= TRACK COLORS (Settings > Interface) =================
    // The lane colours come from the persisted mode; the per-track override above
    // still wins for a single track. `clearTrackColorOverrides` is what the modes
    // call so the scheme is actually visible after the user picks one.

    fun openTrackColorsPage() { _uiState.update { it.copy(trackColorsOpen = true) } }

    fun closeTrackColorsPage() { _uiState.update { it.copy(trackColorsOpen = false) } }

    /** Clears every per-track override so the scheme's colours show through. */
    private fun clearTrackColorOverrides() {
        _uiState.update { state ->
            state.copy(tracks = state.tracks.map { if (it.isMaster) it else it.copy(customColorArgb = null) })
        }
    }

    fun setTrackColorMode(mode: TrackColorMode) {
        _uiState.update { it.copy(trackColorMode = mode) }
        appStatePersistence.saveString(KEY_TRACK_COLOR_MODE, mode.name)
        clearTrackColorOverrides()
        if (mode == TrackColorMode.Random) randomizeTrackColors()
        persistCurrentStateDebounced()
    }

    fun setTrackColorPalette(paletteId: String) {
        val id = TrackPalettes.byId(paletteId).id
        _uiState.update {
            it.copy(trackColorPaletteId = id, trackColorMode = TrackColorMode.Palette)
        }
        appStatePersistence.saveString(KEY_TRACK_COLOR_PALETTE, id)
        appStatePersistence.saveString(KEY_TRACK_COLOR_MODE, TrackColorMode.Palette.name)
        clearTrackColorOverrides()
        persistCurrentStateDebounced()
    }

    fun setTrackSingleColor(argb: Int) {
        _uiState.update { it.copy(trackSingleColorArgb = argb, trackColorMode = TrackColorMode.Single) }
        appStatePersistence.saveInt(KEY_TRACK_COLOR_SINGLE, argb)
        appStatePersistence.saveString(KEY_TRACK_COLOR_MODE, TrackColorMode.Single.name)
        clearTrackColorOverrides()
        persistCurrentStateDebounced()
    }

    /** Re-rolls the random assignment; also selects the Random mode. */
    fun randomizeTrackColors() {
        val state = _uiState.value
        val pool = TrackPalettes.byId(state.trackColorPaletteId).colors
        val lanes = TrackColorsLogic.randomLanes(pool, kotlin.random.Random(System.nanoTime()))
        val argbs = lanes.map { c -> c.toArgbInt() }
        _uiState.update {
            it.copy(trackColorMode = TrackColorMode.Random, trackRandomColorArgb = argbs)
        }
        appStatePersistence.saveString(KEY_TRACK_COLOR_RANDOM, argbs.joinToString(","))
        appStatePersistence.saveString(KEY_TRACK_COLOR_MODE, TrackColorMode.Random.name)
        clearTrackColorOverrides()
        persistCurrentStateDebounced()
    }

    fun setTrackColorDynamic(enabled: Boolean) {
        _uiState.update { it.copy(trackColorDynamic = enabled) }
        appStatePersistence.saveBoolean(KEY_TRACK_COLOR_DYNAMIC, enabled)
        persistCurrentStateDebounced()
    }

    private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int =
        android.graphics.Color.argb(
            (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
        )

    private inline fun updateTrack(trackId: Int, transform: (TrackChannel) -> TrackChannel) {
        _uiState.update { state ->
            state.copy(tracks = state.tracks.map { if (it.id == trackId) transform(it) else it })
        }
    }

    fun openVelocitySettings(trackId: Int) {
        _uiState.update { it.copy(activePopup = ActivePopup.VELOCITY_SETTINGS, activeTrackSettingsId = trackId) }
    }

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

    /** Virtual keyboard theme id (see KeyboardThemes). Persisted as "vkb_theme". */
    fun setVkbTheme(id: String) {
        _uiState.update { it.copy(vkbTheme = id) }
        appStatePersistence.saveString("vkb_theme", id)
    }

    /** "color" or "led". Persisted as "vkb_press_style". */
    fun setVkbPressStyle(style: String) {
        _uiState.update { it.copy(vkbPressStyle = style) }
        appStatePersistence.saveString("vkb_press_style", style)
    }

    /** Test/capture hook: drives the same path as an incoming MIDI CC64. */
    fun debugSetMidiPedal(pressed: Boolean) {
        _uiState.update { it.copy(isMidiPedalPressed = pressed) }
    }

    // ================= MIDI LEARN & CONTROLLER MAPPING =================
    fun openMidiMappingPage() {
        _uiState.update { it.copy(midiMappingOpen = true) }
    }

    fun closeMidiMappingPage() {
        _uiState.update { it.copy(midiMappingOpen = false, settingsTab = SettingsTab.Midi) }
    }

    fun startMidiLearn(actionType: MidiActionType) {
        _uiState.update { it.copy(learningActionType = actionType) }
    }

    fun cancelMidiLearn() {
        _uiState.update { it.copy(learningActionType = null) }
    }

    fun unmapMidiAction(actionType: MidiActionType) {
        _uiState.update { state ->
            val updated = state.midiMappings.map { m ->
                if (m.actionType == actionType) {
                    m.copy(controlNumber = -1, channel = -1)
                } else m
            }
            state.copy(midiMappings = updated)
        }
        persistCurrentStateDebounced()
    }

    fun toggleMidiMappingEnabled(actionType: MidiActionType) {
        _uiState.update { state ->
            val updated = state.midiMappings.map { m ->
                if (m.actionType == actionType) {
                    m.copy(isEnabled = !m.isEnabled)
                } else m
            }
            state.copy(midiMappings = updated)
        }
        persistCurrentStateDebounced()
    }

    fun updateMidiMappingManual(actionType: MidiActionType, type: MidiControlType, channel: Int, controlNum: Int) {
        _uiState.update { state ->
            val updated = state.midiMappings.map { m ->
                if (m.actionType == actionType) {
                    m.copy(midiType = type, channel = channel, controlNumber = controlNum, isEnabled = true)
                } else m
            }
            state.copy(midiMappings = updated)
        }
        persistCurrentStateDebounced()
    }

    fun setMidiMappingMode(actionType: MidiActionType, mode: MidiControlMode) {
        _uiState.update { state ->
            val updated = state.midiMappings.map { m ->
                if (m.actionType == actionType) m.copy(mode = mode) else m
            }
            state.copy(midiMappings = updated)
        }
        persistCurrentStateDebounced()
    }

    fun resetMidiMappingsToDefaults() {
        _uiState.update { it.copy(midiMappings = createDefaultMidiMappings(), learningActionType = null) }
        persistCurrentStateDebounced()
    }

    fun clearAllMidiMappings() {
        _uiState.update { state ->
            val cleared = state.midiMappings.map { it.copy(controlNumber = -1, channel = -1) }
            state.copy(midiMappings = cleared, learningActionType = null)
        }
        persistCurrentStateDebounced()
    }

    fun panicAllNotesOff() {
        audioEngine.allNotesOff()
        _uiState.update { it.copy(pressedKeys = emptySet(), isMidiPedalPressed = false) }
    }

    fun selectPrevPatch() {
        val state = _uiState.value
        val patches = state.patches
        if (patches.isEmpty()) return
        val currentIdx = patches.indexOfFirst { it.id == state.activePatchId }
        val prevIdx = if (currentIdx <= 0) patches.size - 1 else currentIdx - 1
        selectPatch(patches[prevIdx].id)
    }

    fun selectNextPatch() {
        val state = _uiState.value
        val patches = state.patches
        if (patches.isEmpty()) return
        val currentIdx = patches.indexOfFirst { it.id == state.activePatchId }
        val nextIdx = if (currentIdx < 0 || currentIdx >= patches.size - 1) 0 else currentIdx + 1
        selectPatch(patches[nextIdx].id)
    }

    fun selectPrevSnapshot() {
        val state = _uiState.value
        val slots = state.configuredSnapshotSlots
        if (slots.isEmpty()) return
        val currentIdx = slots.indexOf(state.activeSnapshotSlot)
        val prevIdx = if (currentIdx <= 0) slots.size - 1 else currentIdx - 1
        onSnapshotSlotClick(slots[prevIdx])
    }

    fun selectNextSnapshot() {
        val state = _uiState.value
        val slots = state.configuredSnapshotSlots
        if (slots.isEmpty()) return
        val currentIdx = slots.indexOf(state.activeSnapshotSlot)
        val nextIdx = if (currentIdx < 0 || currentIdx >= slots.size - 1) 0 else currentIdx + 1
        onSnapshotSlotClick(slots[nextIdx])
    }

    fun toggleMuteTrackByIndex(index: Int) {
        val tracks = _uiState.value.tracks
        val targetTrack = tracks.getOrNull(index) ?: tracks.find { it.id == index + 1 }
        if (targetTrack != null) {
            onTrackMuteClick(targetTrack.id)
        }
    }

    fun toggleSoloTrackByIndex(index: Int) {
        val tracks = _uiState.value.tracks
        val targetTrack = tracks.getOrNull(index) ?: tracks.find { it.id == index + 1 }
        if (targetTrack != null) {
            onTrackSoloClick(targetTrack.id)
        }
    }

    private var tapTempoTimes = mutableListOf<Long>()
    fun handleTapTempo() {
        val now = System.currentTimeMillis()
        tapTempoTimes.add(now)
        if (tapTempoTimes.size > 4) {
            tapTempoTimes.removeAt(0)
        }
        if (tapTempoTimes.size >= 2) {
            val intervals = mutableListOf<Long>()
            for (i in 1 until tapTempoTimes.size) {
                val diff = tapTempoTimes[i] - tapTempoTimes[i - 1]
                if (diff in 200..3000) {
                    intervals.add(diff)
                }
            }
            if (intervals.isNotEmpty()) {
                val avgMs = intervals.average()
                val calculatedBpm = (60000.0 / avgMs).toInt().coerceIn(30, 280)
                setGlobalBpm(calculatedBpm)
            }
        }
    }

    private fun handleMidiMappingEvent(channel: Int, command: Int, data1: Int, data2: Int) {
        val statusCmd = if (command >= 0x80) (command and 0xF0) else command
        val midiType = when (statusCmd) {
            0x90, 0x80 -> MidiControlType.NOTE
            0xB0 -> MidiControlType.CC
            0xC0 -> MidiControlType.PROGRAM_CHANGE
            0xD0 -> MidiControlType.AFTERTOUCH
            0xE0 -> MidiControlType.PITCH_BEND
            else -> return
        }
        // A Program Change and a Channel Aftertouch carry ONE data byte: the value lives in
        // data1, and data2 is the channel byte. Reading data2 there would always give 0.
        // Pitch Bend carries LSB then MSB, so the 14-bit value is (MSB << 7) or LSB.
        val ctrlNumber = if (midiType == MidiControlType.PITCH_BEND) 0 else data1
        val value = when (statusCmd) {
            0x80 -> 0
            0xC0, 0xD0 -> data1
            0xE0 -> ((data2 shl 7) or data1) shr 6
            else -> data2
        }

        val learningType = _uiState.value.learningActionType
        if (learningType != null) {
            // Arm on ANY signal. A worn/at-rest controller can send value 0 (a CC whose
            // fader sits at the bottom, a Note Off); rejecting those made learn look dead.
            // Only ignore an explicit release we can identify as such.
            if (statusCmd == 0x80) return
            _uiState.update { state ->
                val updated = state.midiMappings.map { m ->
                    if (m.actionType == learningType) {
                        m.copy(
                            midiType = midiType,
                            channel = channel,
                            controlNumber = ctrlNumber,
                            isEnabled = true
                        )
                    } else m
                }
                state.copy(
                    midiMappings = updated,
                    learningActionType = null,
                    lastMidiSummary = "Assigned: ${learningType.title} ➔ ${midiType.name} #$ctrlNumber (Ch. ${channel + 1})",
                    lastMidiTriggerTime = System.currentTimeMillis()
                )
            }
            persistCurrentStateDebounced()
            return
        }

        // Live execution
        val summary = when (midiType) {
            MidiControlType.CC -> "CC #$ctrlNumber (Ch. ${channel + 1}, Val $value)"
            MidiControlType.NOTE -> "Note #$ctrlNumber (Ch. ${channel + 1}, Vel $value)"
            MidiControlType.AFTERTOUCH -> "Aftertouch (Ch. ${channel + 1}, Val $value)"
            MidiControlType.PITCH_BEND -> "Pitch Bend (Ch. ${channel + 1}, Val $value)"
            MidiControlType.PROGRAM_CHANGE -> "PC #$ctrlNumber (Ch. ${channel + 1})"
        }
        _uiState.update { it.copy(lastMidiSummary = summary, lastMidiTriggerTime = System.currentTimeMillis()) }

        val activeMappings = _uiState.value.midiMappings.filter { m ->
            m.isEnabled && m.isAssigned && m.midiType == midiType && m.controlNumber == ctrlNumber &&
                (m.channel < 0 || m.channel == channel)
        }

        val now = System.currentTimeMillis()
        // Single jitter filter. `antiJitter` / `jitterWindowMs` are the only knobs:
        // the old `midiDebounce*` pair was a second, hidden control for the same job.
        val debounceActive = _uiState.value.antiJitter
        val debounceMs = _uiState.value.jitterWindowMs.toLong()

        for (m in activeMappings) {
            if (debounceActive && debounceMs > 0) {
                // Precise jitter filter per control identity: prevents mechanical bounce bursts
                val controlKey = "${channel}_${midiType.name}_$ctrlNumber"
                val lastTime = midiControlLastTriggerTime[controlKey] ?: 0L
                val elapsed = now - lastTime
                if (elapsed < debounceMs) {
                    continue
                }
                midiControlLastTriggerTime[controlKey] = now
            }
            executeMidiAction(m.actionType, value, isProgramChange = (midiType == MidiControlType.PROGRAM_CHANGE))
        }
    }

    private val midiControlLastTriggerTime = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun openTrackSettings(trackId: Int) {
        _uiState.update { it.copy(activeTrackSettingsId = trackId, activePopup = ActivePopup.TRACK_SETTINGS) }
    }

    fun closeTrackSettings() {
        _uiState.update {
            it.copy(
                activeTrackSettingsId = null,
                activePopup = if (it.activePopup == ActivePopup.TRACK_SETTINGS) ActivePopup.NONE else it.activePopup
            )
        }
    }

    fun recallSnapshotDirect(slotName: String) {
        val currentState = _uiState.value
        snapshotTransitionJob?.cancel()

        val activePatch = currentState.patches.find { it.id == currentState.activePatchId }
        val targetSnapshot = currentState.snapshots[slotName]
            ?: activePatch?.snapshots?.get(slotName)
            ?: if (slotName == "slot_default") {
                activePatch?.snapshots?.get("slot_default") ?: captureSnapshot(
                    slotName = "Default",
                    state = currentState,
                    tracks = activePatch?.tracks ?: currentState.tracks,
                    globalTranspose = activePatch?.transpose ?: currentState.transpose,
                    globalOctaveShift = activePatch?.octave ?: currentState.octave,
                    masterVolume = activePatch?.masterVolume ?: currentState.masterTrack.volume,
                    visibleTracksCount = activePatch?.visibleTracksCount ?: currentState.visibleTracksCount,
                )
            } else null

        if (targetSnapshot != null) {
            val targetTracks = currentState.tracks.map { tr ->
                applySnapshotToTrack(tr, targetSnapshot.tracks.find { it.id == tr.id })
            }

            targetTracks.forEachIndexed { idx, track ->
                if (!track.isEnabled || track.isMuted) {
                    audioEngine.setChannelEnabled(idx, false)
                    if (idx in 0..7) {
                        NativeAudioBridge.safeAllNotesOff(idx)
                    }
                }
                if (track.soundfontName.isNotEmpty()) {
                    val candidate = File(fileManager.soundfontsDir, track.soundfontName)
                    val currentSlot = currentState.audioSlots.getOrNull(idx)
                    if (currentSlot != null && currentSlot.soundFontId > 0 &&
                        (currentSlot.soundFontPath == candidate.absolutePath || currentSlot.soundFontPath?.endsWith(track.soundfontName) == true)
                    ) {
                        audioEngine.setChannelProgram(idx, track.program, track.bank)
                        NativeAudioBridge.safeSelectProgram(
                            engineIndex = NativeAudioBridge.ENGINE_FADER,
                            channel = idx,
                            soundFontId = currentSlot.soundFontId,
                            bank = track.bank,
                            preset = track.program
                        )
                        val effectiveVol = if (track.isMuted || !track.isEnabled) 0f else track.volume
                        NativeAudioBridge.safeSetTrackVolume(idx, effectiveVol)
                    } else if (candidate.exists()) {
                        loadSoundFontForSlot(idx, candidate.absolutePath, track.bank, track.program, track.patchName, saveAfterLoad = false)
                    }
                }
            }

            audioEngine.globalOctaveShift = targetSnapshot.globalOctaveShift
            audioEngine.globalTranspose = targetSnapshot.globalTranspose
            applySnapshotFxToEngine(targetSnapshot)

            targetTracks.forEachIndexed { idx, track ->
                val effectiveVol = if (track.isMuted || !track.isEnabled) 0f else track.volume
                NativeAudioBridge.safeSetTrackVolume(idx, effectiveVol)
                NativeAudioBridge.safeSetTrackPan(idx, track.pan)
            }
            pushSnapshotTracksToEngine(targetTracks)

            _uiState.update { state ->
                state.copy(
                    activeSnapshotSlot = slotName,
                    tracks = targetTracks,
                    transpose = targetSnapshot.globalTranspose,
                    octave = targetSnapshot.globalOctaveShift,
                    visibleTracksCount = targetSnapshot.visibleTracksCount,
                    snapshotTransitionProgress = 1.0f
                )
            }
            persistCurrentStateDebounced()
        }
    }

    fun selectMidiOutputDevice(deviceName: String) {
        _uiState.update { it.copy(selectedMidiOutputDevice = deviceName) }
        persistCurrentStateDebounced()
    }

    fun updateTrackMidiDevice(trackId: Int, deviceId: String?) {
        _uiState.update { state ->
            val updatedTracks = state.tracks.map { tr ->
                if (tr.id == trackId) tr.copy(selectedMidiDeviceId = deviceId) else tr
            }
            state.copy(tracks = updatedTracks)
        }
        persistCurrentStateDebounced()
    }

    fun updateTrackMidiChannel(trackId: Int, channel: Int) {
        _uiState.update { state ->
            val updatedTracks = state.tracks.map { tr ->
                if (tr.id == trackId) tr.copy(midiChannel = channel) else tr
            }
            val updatedAudioSlots = state.audioSlots.map { slot ->
                if (slot.slotId == trackId - 1) slot.copy(midiChannel = channel) else slot
            }
            state.copy(tracks = updatedTracks, audioSlots = updatedAudioSlots)
        }
        persistCurrentStateDebounced()
    }

    private fun executeMidiAction(action: MidiActionType, value: Int, isProgramChange: Boolean = false) {
        val isContinuous = action.name.endsWith("_VOL") || action.name.endsWith("_PUNCH") ||
            action.name.endsWith("_GOODIZER") || action.name.endsWith("_PARAM")
        val isDrumPad = action.name.startsWith("DRUMPAD_PAD_")

        // For button actions (toggles, triggers, snapshots, patches), trigger on positive press (> 0),
        // or also support toggle controllers when configured.
        // Program Changes (PC) carry program number without velocity byte and must always trigger.
        val isButtonAction = !isContinuous && !isDrumPad
        if (isButtonAction && !isProgramChange) {
            val mapping = _uiState.value.midiMappings.find { it.actionType == action }
            val isToggle = mapping?.mode == MidiControlMode.TOGGLE
            if (value <= 0 && !isToggle) {
                return
            }
        }

        when (action) {
            MidiActionType.SNAPSHOT_DEFAULT -> onSnapshotSlotClick("slot_default")
            MidiActionType.SNAPSHOT_1 -> {
                val slot = _uiState.value.configuredSnapshotSlots.getOrNull(0) ?: "slot_1"
                onSnapshotSlotClick(slot)
            }
            MidiActionType.SNAPSHOT_2 -> {
                val slot = _uiState.value.configuredSnapshotSlots.getOrNull(1) ?: "slot_2"
                onSnapshotSlotClick(slot)
            }
            MidiActionType.SNAPSHOT_3 -> {
                val slot = _uiState.value.configuredSnapshotSlots.getOrNull(2) ?: "slot_3"
                onSnapshotSlotClick(slot)
            }
            MidiActionType.SNAPSHOT_4 -> {
                val slot = _uiState.value.configuredSnapshotSlots.getOrNull(3) ?: "slot_4"
                onSnapshotSlotClick(slot)
            }
            MidiActionType.SNAPSHOT_PREV -> selectPrevSnapshot()
            MidiActionType.SNAPSHOT_NEXT -> selectNextSnapshot()

            MidiActionType.PATCH_PREV -> selectPrevPatch()
            MidiActionType.PATCH_NEXT -> selectNextPatch()

            MidiActionType.TRACK_1_MUTE -> toggleMuteTrackByIndex(0)
            MidiActionType.TRACK_1_SOLO -> toggleSoloTrackByIndex(0)
            MidiActionType.TRACK_1_VOL -> setTrackVolume(1, (value / 127f).coerceIn(0f, 1f))

            MidiActionType.TRACK_2_MUTE -> toggleMuteTrackByIndex(1)
            MidiActionType.TRACK_2_SOLO -> toggleSoloTrackByIndex(1)
            MidiActionType.TRACK_2_VOL -> setTrackVolume(2, (value / 127f).coerceIn(0f, 1f))

            MidiActionType.TRACK_3_MUTE -> toggleMuteTrackByIndex(2)
            MidiActionType.TRACK_3_SOLO -> toggleSoloTrackByIndex(2)
            MidiActionType.TRACK_3_VOL -> setTrackVolume(3, (value / 127f).coerceIn(0f, 1f))

            MidiActionType.TRACK_4_MUTE -> toggleMuteTrackByIndex(3)
            MidiActionType.TRACK_4_SOLO -> toggleSoloTrackByIndex(3)
            MidiActionType.TRACK_4_VOL -> setTrackVolume(4, (value / 127f).coerceIn(0f, 1f))

            MidiActionType.TRACK_5_MUTE -> toggleMuteTrackByIndex(4)
            MidiActionType.TRACK_5_SOLO -> toggleSoloTrackByIndex(4)
            MidiActionType.TRACK_5_VOL -> setTrackVolume(5, (value / 127f).coerceIn(0f, 1f))

            MidiActionType.TRACK_6_MUTE -> toggleMuteTrackByIndex(5)
            MidiActionType.TRACK_6_SOLO -> toggleSoloTrackByIndex(5)
            MidiActionType.TRACK_6_VOL -> setTrackVolume(6, (value / 127f).coerceIn(0f, 1f))

            MidiActionType.TRACK_7_MUTE -> toggleMuteTrackByIndex(6)
            MidiActionType.TRACK_7_SOLO -> toggleSoloTrackByIndex(6)
            MidiActionType.TRACK_7_VOL -> setTrackVolume(7, (value / 127f).coerceIn(0f, 1f))

            MidiActionType.TRACK_8_MUTE -> toggleMuteTrackByIndex(7)
            MidiActionType.TRACK_8_SOLO -> toggleSoloTrackByIndex(7)
            MidiActionType.TRACK_8_VOL -> setTrackVolume(8, (value / 127f).coerceIn(0f, 1f))

            MidiActionType.DRUMPAD_PAD_1 -> if (value > 0) onDrumPadPressed(1) else onDrumPadReleased(1)
            MidiActionType.DRUMPAD_PAD_2 -> if (value > 0) onDrumPadPressed(2) else onDrumPadReleased(2)
            MidiActionType.DRUMPAD_PAD_3 -> if (value > 0) onDrumPadPressed(3) else onDrumPadReleased(3)
            MidiActionType.DRUMPAD_PAD_4 -> if (value > 0) onDrumPadPressed(4) else onDrumPadReleased(4)
            MidiActionType.DRUMPAD_PAD_5 -> if (value > 0) onDrumPadPressed(5) else onDrumPadReleased(5)
            MidiActionType.DRUMPAD_PAD_6 -> if (value > 0) onDrumPadPressed(6) else onDrumPadReleased(6)
            MidiActionType.DRUMPAD_PAD_7 -> if (value > 0) onDrumPadPressed(7) else onDrumPadReleased(7)
            MidiActionType.DRUMPAD_PAD_8 -> if (value > 0) onDrumPadPressed(8) else onDrumPadReleased(8)
            MidiActionType.DRUMPAD_STOP_ALL -> stopDrumPlayback()

            MidiActionType.MASTER_VOLUME -> setTrackVolume(0, (value / 127f).coerceIn(0f, 1f))
            MidiActionType.PANIC_ALL_NOTES_OFF -> panicAllNotesOff()
            MidiActionType.TAP_TEMPO -> handleTapTempo()
            MidiActionType.METRONOME_TOGGLE -> toggleMetronome()
            MidiActionType.TRANSPOSE_UP -> updateTranspose(_uiState.value.transpose + 1)
            MidiActionType.TRANSPOSE_DOWN -> updateTranspose(_uiState.value.transpose - 1)
            MidiActionType.OCTAVE_UP -> updateOctave(1)
            MidiActionType.OCTAVE_DOWN -> updateOctave(-1)
            MidiActionType.OPEN_PATCHES -> openPopup(ActivePopup.SCENE)
            MidiActionType.OPEN_SETTINGS -> openSettings()
        }
    }

}
