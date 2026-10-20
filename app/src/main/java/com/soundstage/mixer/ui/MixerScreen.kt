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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.soundstage.mixer.ui.components.v2.MixerBackgroundV2
import com.soundstage.mixer.ui.components.v2.ChannelStripV2
import com.soundstage.mixer.ui.components.v2.MixerTopBarV2
import com.soundstage.mixer.ui.components.v2.MixerBottomBarV2
import com.soundstage.mixer.ui.pages.DrumPadScreen
import com.soundstage.mixer.ui.pages.ProjectsPage
import com.soundstage.mixer.ui.pages.SheetsPage
import com.soundstage.mixer.ui.settings.NoOpSettingsActions
import com.soundstage.mixer.ui.settings.SettingsScreen
import com.soundstage.mixer.ui.settings.SettingsState
import com.soundstage.mixer.ui.setlists.NoOpSetlistsActions
import com.soundstage.mixer.ui.setlists.SetlistsScreen
import com.soundstage.mixer.ui.setlists.SetlistsUiState
import com.soundstage.mixer.ui.settings.*
import com.soundstage.mixer.ui.setlists.*
import com.soundstage.mixer.ui.setlists.SetlistColor
import com.soundstage.mixer.ui.theme.*
import com.soundstage.mixer.ui.theme.AppColors
import androidx.compose.runtime.CompositionLocalProvider
import com.soundstage.mixer.ui.theme.LocalHapticsEnabled
import com.soundstage.mixer.viewmodel.MixerViewModel
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.ScrollState
import com.soundstage.mixer.viewmodel.MixerUiState
import com.soundstage.mixer.ui.components.DetectedChord
import com.soundstage.mixer.ui.components.ChordDisplay
import com.aetherkeys.sheets.ui.SheetsRoot
import com.soundstage.mixer.ui.patches.PatchesOverlay
import com.soundstage.mixer.ui.patches.PatchesUiState
import com.soundstage.mixer.ui.patches.Patch
import com.soundstage.mixer.ui.patches.PatchesActions
import com.soundstage.mixer.ui.tracksettings.TsDeviceItem
import com.soundstage.mixer.ui.tracksettings.TsPage
import com.soundstage.mixer.ui.tracksettings.TsVelocity
import com.soundstage.mixer.ui.tracksettings.TrackSettingsActions
import com.soundstage.mixer.ui.tracksettings.TrackSettingsState
import com.soundstage.mixer.ui.tracksettings.TrackSettingsWindow
import com.aetherkeys.sheets.data.SheetsRepository
import com.soundstage.mixer.ui.components.SoundfontWindow
import com.soundstage.mixer.ui.components.SoundfontWindowState
import com.soundstage.mixer.ui.components.SoundfontVariant
import com.soundstage.mixer.ui.components.SfRow
import com.soundstage.mixer.ui.components.SfWindowActions
import com.soundstage.mixer.model.SoundfontPreset

private val com.soundstage.mixer.model.PatchItem.snapshotCount: Int get() = snapshots.size

@Composable
private fun MixerMainPageContent(
    uiState: MixerUiState,
    viewModel: MixerViewModel,
    safeBottomPadding: Dp,
    detectedChord: DetectedChord?,
    isKeyboardVisible: Boolean,
    trackScrollState: ScrollState,
    onImportDrumPadAudio: () -> Unit = {}
) {
// Full-screen Studio Layout (No enclosing bento box, edge-to-edge layout)
// Fond V2 (degrade + vignettes) : remplace MaterialTheme.colorScheme.background.
                MixerBackgroundV2 {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = 11.dp,
                                end = 11.dp,
                                top = 8.dp,
                                bottom = safeBottomPadding
                            )
                    ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // 1. TOP BAR (V2)
                MixerTopBarV2(
                    transpose = uiState.transpose,
                    octave = uiState.octave,
                    onTransposeChange = { viewModel.updateTranspose(it) },
                    onOctaveChange = { viewModel.updateOctave(it) },
                    chord = ChordDisplay.from(detectedChord),
                    isSustainActive = uiState.isSustainActive || uiState.isMidiPedalPressed,
                    onToggleSustain = { viewModel.toggleSustain() },
                    onOpenNotes = { viewModel.navigateToPage(AppScreenPage.SHEETS) },
                    onOpenDrumPad = { viewModel.navigateToPage(AppScreenPage.DRUMPAD) },
                    onPanic = { viewModel.triggerPanic() },
                    onOpenScenes = { viewModel.openPopup(ActivePopup.SCENE) },
                    onOpenSettings = { viewModel.openSettingsDrawer() },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp)) // JSX: topbar top 8 + height 44 -> strips top 60 (gap 8)

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
                            // trackScrollState lifted to MixerScreen level
                            val overlayWidth = if (isPadOpen) (this@BoxWithConstraints.maxWidth * 0.50f) else 0.dp
                            val visibleTracks = uiState.tracks.take(uiState.visibleTracksCount.coerceIn(1, 8))
                            val showAddButton = uiState.visibleTracksCount < 8 && !isPadOpen
                            val activeItemCount = visibleTracks.size + (if (showAddButton) 1 else 0)

                            // Fader width: JSX Strip is 126 dp (MixerPreview.jsx:220), gap 6.
                            // In Unlock mode it keeps that width and scrolls horizontally.
                            // In Lock mode it keeps 126 until tracks overflow, then shrinks to fit.
                            val defaultTrackWidth = 126.dp
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
                                val isAnySolo = uiState.tracks.any { it.isSolo }
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

                                    val isDimmed = !track.isEnabled || track.isMuted || (isAnySolo && !track.isSolo)
                                    val accentColor = track.resolvedColor()

                                    ChannelStripV2(
                                        track = track,
                                        isDimmed = isDimmed,
                                        accentColor = accentColor,
                                        onVolumeChange = { vol -> viewModel.setTrackVolume(track.id, vol) },
                                        onPanChange = { pan -> viewModel.setTrackPan(track.id, pan) },
                                        onSoloToggle = { viewModel.onTrackSoloClick(track.id) },
                                        onMuteToggle = { viewModel.onTrackMuteClick(track.id) },
                                        onTrackNameClick = { viewModel.openSoundfontForSlot(track.id - 1) },
                                        onFxClick = { viewModel.openEffectsForTrack(track.id) },
                                        onTrackSettingsClick = { viewModel.openTrackSettings(track.id) },
                                        onPowerToggle = { viewModel.toggleTrackPower(track.id) },
                                        onDeleteTrack = { viewModel.removeTrack(track.id) },
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

                                    // JSX Add Sound case (MixerPreview.jsx:445): a dashed
                                    // outline on a near-transparent fill, with a rounded
                                    // icon chip inside. No nested bordered box.
                                    Box(
                                        modifier = addBtnModifier
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(Color(0x08FFFFFF))
                                            .drawBehind {
                                                drawRoundRect(
                                                    color = Color(0x24FFFFFF),
                                                    cornerRadius = CornerRadius(18.dp.toPx()),
                                                    style = Stroke(
                                                        width = 1.5.dp.toPx(),
                                                        pathEffect = PathEffect.dashPathEffect(
                                                            floatArrayOf(6.dp.toPx(), 5.dp.toPx())
                                                        )
                                                    )
                                                )
                                            }
                                            .clickable { viewModel.addSoundTrack() }
                                            .testTag("btn_add_sound_track"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(Color(0x148FB2E8))
                                                    .border(1.5.dp, Color(0x738FB2E8), RoundedCornerShape(14.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Add Sound",
                                                    tint = Color(0xFF8FB2E8),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Text(
                                                text = "Add Sound",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF8E99AD)
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
                                onTrackSettingsClick = { id -> viewModel.openTrackSettings(id) },
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
                                            onImportAudioFile = { onImportDrumPadAudio() },
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

                // 3. BOTTOM BAR (V2)
                MixerBottomBarV2(
                    masterTrack = uiState.masterTrack,
                    onMasterVolumeChange = { vol -> viewModel.setTrackVolume(0, vol) },
                    onMasterFxClick = { viewModel.openEffectsForTrack(0) },
                    bpm = uiState.bpm,
                    onBpmChange = { viewModel.setGlobalBpm(it) },
                    isSnapshotArmMode = uiState.isSnapshotArmMode,
                    onToggleSnapshotArm = { viewModel.toggleSnapshotArm() },
                    activeSnapshotSlot = uiState.activeSnapshotSlot,
                    snapshots = uiState.snapshots,
                    configuredSnapshotSlots = uiState.configuredSnapshotSlots,
                    onSnapshotSlotClick = { key -> viewModel.onSnapshotSlotClick(key) },
                    onAddSnapshot = { name -> viewModel.addSnapshotSlot(name) },
                    onDeleteSnapshot = { key -> viewModel.removeSnapshotSlot(key) },
                    snapshotCustomNames = uiState.snapshotCustomNames,
                    onRenameSnapshotSlot = { key, name ->
                        viewModel.renameSnapshotSlotWithMode(key, name, "crossfade")
                    },
                    isKeyboardActive = isKeyboardVisible,
                    onToggleKeyboard = { viewModel.cycleKeyboardExpansion() },
                    isLayerExpanded = uiState.isKeyboardLayerExpanded,
                    onToggleLayer = { viewModel.toggleKeyboardLayer() },
                    modifier = Modifier.fillMaxWidth()
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
                // Note names (app storage) <-> MIDI numbers (new UI) adapter, at the boundary only.
                val vkbLines = remember(uiState.tracks, uiState.visibleTracksCount) {
                    activePianoTracks.map { t ->
                        KeyboardLine(
                            id = t.id.toString(),
                            name = t.name,
                            color = t.resolvedColor(),
                            low = t.splitNoteMin,
                            high = t.splitNoteMax
                        )
                    }
                }
                val vkbPressed = remember(uiState.pressedKeys) {
                    uiState.pressedKeys.mapNotNull { name ->
                        VkbMidi.ktToMidi(name)?.takeIf { it in VkbMidi.NOTE_MIN..VkbMidi.NOTE_MAX }
                    }.toSet()
                }
                VirtualKeyboardContainer(
                    lines = vkbLines,
                    expanded = uiState.isKeyboardLayerExpanded,
                    pressedNotes = vkbPressed,
                    theme = KeyboardThemes.byId(uiState.vkbTheme),
                    pressStyle = if (uiState.vkbPressStyle == "led") KeyboardPressStyle.Led else KeyboardPressStyle.Color,
                    actions = remember {
                        VirtualKeyboardActions(
                            onExpandedChange = { viewModel.toggleKeyboardLayer() },
                            onLineRangeChange = { id, low, high ->
                                id.toIntOrNull()?.let { viewModel.updateTrackKeyRange(it, low, high) }
                            },
                            onKeyDown = { note -> viewModel.onKeyDown(VkbMidi.midiToKt(note)) },
                            onKeyUp = { note -> viewModel.onKeyUp(VkbMidi.midiToKt(note)) },
                            onPitchBend = { viewModel.setPitchBend(it) }
                        )
                    }
                )
            }


            // Outside touch scrim for quick closing of floating dropdowns
            if (uiState.isMidiPanelOpen || uiState.activePopup == ActivePopup.SCENE) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x22000000))
                        .clickable {
                            if (uiState.isMidiPanelOpen) viewModel.closeMidiPanel()
                            if (uiState.activePopup == ActivePopup.SCENE) viewModel.closePopup()
                        }
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



/**
 * Switches the Effects popup to the ported Aether FX window. The legacy
 * EffectsDialog stays reachable until the new window is validated on device
 * (doc: remove the old FX once everything is implemented).
 */
private const val FX_WINDOW_V2 = true


@Composable
fun MixerScreen(
    viewModel: MixerViewModel,
    modifier: Modifier = Modifier,
    onRequestManageStoragePermission: () -> Unit = {},
    skipSplash: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isSplashFinished by remember { mutableStateOf(skipSplash) }
    var tsPage by remember { mutableStateOf(TsPage.Track) }
    LaunchedEffect(uiState.activeTrackSettingsId) { tsPage = TsPage.Track }

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
    val trackScrollState = rememberScrollState()

    // Fullscreen Stage Container (Unpadded for 100% full-bleed overlays like SplashScreen)
    CompositionLocalProvider(LocalHapticsEnabled provides uiState.hapticsEnabled) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("mixer_screen_root")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = safeBottomPadding)
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
                AppScreenPage.PROJECTS, AppScreenPage.SETLISTS -> {
                    SetlistsScreen(
                        state = SetlistsUiState(
                            setlists = uiState.scenes.map { scene ->
                                Setlist(
                                    id = scene.id,
                                    name = scene.name,
                                    patchCount = scene.patchesCount,
                                    color = scene.color.toSetlistColor(),
                                    isPinned = false,
                                    lastUsedAt = System.currentTimeMillis()
                                )
                            },
                            openSetlistId = uiState.activeSceneId.ifEmpty { null },
                            lastOpenedId = uiState.lastOpenedSetlistId,
                            isLoading = uiState.isSetlistsLoading,
                            query = uiState.setlistsQuery,
                            sort = uiState.setlistsSort,
                            onlyPinned = uiState.setlistsOnlyPinned,
                            prefs = uiState.setlistsPrefs
                        ),
                        actions = object : SetlistsActions {
                            override fun onCancel() { viewModel.navigateBackToMixer() }
                            override fun onOpenSetlist(id: String) { viewModel.openSetlistFromScreen(id) }
                            override fun onCreateSetlist(name: String, mode: CreateMode) { viewModel.createSetlistFromScreen(name, mode) }
                            override fun onRenameSetlist(id: String, name: String) { viewModel.renameSetlistFromScreen(id, name) }
                            override fun onChangeColor(id: String, color: SetlistColor) { viewModel.changeSetlistColor(id, color) }
                            override fun onDuplicateSetlist(id: String) { viewModel.duplicateSetlistFromScreen(id) }
                            override fun onTogglePin(id: String) { viewModel.togglePinSetlist(id) }
                            override fun onDeleteSetlist(id: String) { viewModel.deleteSetlistWithUndo(id) }
                            override fun onUndoDelete() { viewModel.undoDeleteSetlist() }
                            override fun onQueryChange(query: String) { viewModel.setSetlistsQuery(query) }
                            override fun onSortChange(sort: SetlistSort) { viewModel.setSetlistsSort(sort) }
                            override fun onOnlyPinnedChange(only: Boolean) { viewModel.setSetlistsOnlyPinned(only) }
                            override fun onPrefsChange(prefs: SetlistsPrefs) { viewModel.updateSetlistsPrefs(prefs) }
                            override fun onResetPrefs() { viewModel.resetSetlistsPrefs() }
                            override fun onOpenAppSettings() { viewModel.openSettingsDrawer() }
                        }
                    )
                }
                AppScreenPage.DRUMPAD -> {
                    DrumPadScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onImportFiles = { tab ->
                            if (tab == "Loops") loopPickerLauncher.launch(arrayOf("audio/*", "*/*"))
                            else drumPadPickerLauncher.launch(arrayOf("audio/*", "*/*"))
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                AppScreenPage.SHEETS -> {
                    SheetsRoot(
                        repo = SheetsRepository(viewModel.fileManager.sheetsDir),
                        bottomInset = safeBottomPadding,
                        onBackToMixer = { viewModel.navigateBackToMixer() }
                    )
                }
                AppScreenPage.MIXER -> {
                    MixerMainPageContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        safeBottomPadding = safeBottomPadding,
                        detectedChord = detectedChord,
                        isKeyboardVisible = isKeyboardVisible,
                        trackScrollState = trackScrollState,
                        onImportDrumPadAudio = { drumPadPickerLauncher.launch(arrayOf("audio/*", "*/*")) }
                    )
                }
                AppScreenPage.SETTINGS -> {
                val settingsState = SettingsState(
                    tab = uiState.settingsTab,
                    query = uiState.settingsQuery,
                    bufferSize = uiState.audioBufferSize,
                    maxPolyphony = uiState.polyphony,
                    headroom = uiState.headroom,
                    autoFit = uiState.isFaderAutoFit,
                    hue = uiState.themeHue,
                    accentSaturation = uiState.themeAccentSaturation,
                    backgroundTone = uiState.themeBgSaturation,
                    backgroundLightness = uiState.themeBgLightness,
                    vkbTheme = uiState.vkbTheme,
                    vkbPressStyle = uiState.vkbPressStyle,
                    audioEngine = uiState.audioEngine,
                    fluidSynthGain = uiState.fluidSynthGain,
                    hapticsEnabled = uiState.hapticsEnabled,
                    reducedMotion = uiState.reducedMotion,
                    antiJitter = uiState.antiJitter,
                    jitterWindowMs = uiState.jitterWindowMs,
                    keepScreenOn = uiState.keepScreenOn,
                    notation = if (uiState.useFlats) Notation.Flats else Notation.Sharps,
                    mappingPage = uiState.mappingPage,
                    listeningId = uiState.mappingListeningControlId,
                    conflict = uiState.mappingConflict,
                    otherActionsOpen = uiState.mappingOtherActionsOpen,
                    mappings = viewModel.getActiveSettingsStateMappings(),
                    catalog = MappingCatalog.controls,
                    midiDevices = uiState.midiDevices
                )
                val settingsActions = object : SettingsActions {
                    override fun onBack() { viewModel.navigateBackToMixer() }
                    override fun onTabSelected(tab: SettingsTab) { viewModel.onSettingsTabSelected(tab) }
                    override fun onQueryChange(query: String) { viewModel.onSettingsQueryChanged(query) }
                    override fun onBufferSizeChange(size: Int) { viewModel.setAudioBufferSize(size) }
                    override fun onMaxPolyphonyChange(voices: Int) { viewModel.setPolyphony(voices) }
                    override fun onHeadroomChange(value: Float) { viewModel.setHeadroom(value) }
                    override fun onAutoFitChange(enabled: Boolean) { viewModel.setFaderAutoFit(enabled) }
                    override fun onHueChange(value: Float) { viewModel.setThemeHue(value) }
                    override fun onAccentSaturationChange(value: Float) { viewModel.setThemeAccentSaturation(value) }
                    override fun onBackgroundToneChange(value: Float) { viewModel.setThemeBgSaturation(value) }
                    override fun onBackgroundLightnessChange(value: Float) { viewModel.setThemeBgLightness(value) }
                    override fun onResetColors() {
                        viewModel.resetThemeToDefault()
                        viewModel.setVkbTheme("classic")
                        viewModel.setVkbPressStyle("color")
                    }
                    override fun onVkbThemeChange(themeId: String) { viewModel.setVkbTheme(themeId) }
                    override fun onVkbPressStyleChange(style: String) { viewModel.setVkbPressStyle(style) }
                    override fun onAntiJitterChange(enabled: Boolean) { viewModel.setAntiJitter(enabled) }
                    override fun onJitterWindowChange(ms: Int) { viewModel.setJitterWindowMs(ms) }
                    override fun onOpenMapping(page: MappingPage) { viewModel.onOpenMappingMode(page) }
                    override fun onResetMappingsToDefaults() { viewModel.resetMidiMappingsToDefaults() }
                    override fun onClearAllMappings() { viewModel.clearAllMidiMappings() }
                    override fun onNotationChange(notation: Notation) { viewModel.toggleUseFlats() }
                    override fun onKeepScreenOnChange(enabled: Boolean) { viewModel.setKeepScreenOn(enabled) }
                    override fun onAudioEngineChange(engine: String) { viewModel.setAudioEngine(engine) }
                    override fun onFluidSynthGainChange(value: Float) { viewModel.setFluidSynthGain(value) }
                    override fun onHapticsChange(enabled: Boolean) { viewModel.setHapticsEnabled(enabled) }
                    override fun onReducedMotionChange(enabled: Boolean) { viewModel.setReducedMotion(enabled) }
                    override fun onOpenLanguage() { /* no-op */ }
                    override fun onOpenAppSettings() { /* no-op */ }
                    override fun onCloseMapping() { viewModel.onCloseMappingMode() }
                    override fun onStartListening(controlId: String) { viewModel.startMappingListening(controlId) }
                    override fun onCancelListening() { viewModel.cancelMappingListening() }
                    override fun onClearMapping(controlId: String) { viewModel.clearMappingForControl(controlId) }
                    override fun onReplaceConflict() { viewModel.replaceMappingConflict() }
                    override fun onToggleOtherActions() { viewModel.toggleMappingOtherActions() }
                }

                if (uiState.mappingPage == null) {
                    SettingsScreen(
                        state = settingsState,
                        actions = settingsActions,
                        background = Brush.verticalGradient(listOf(AppColors.BgTop, AppColors.BgBottom))
                    )
                } else {
                    MappingMode(
                        state = settingsState,
                        actions = settingsActions,
                        pageContent = {
                            when (uiState.mappingPage) {
                                MappingPage.Mixer -> {
                                    MixerMainPageContent(
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        safeBottomPadding = safeBottomPadding,
                                        detectedChord = detectedChord,
                                        isKeyboardVisible = isKeyboardVisible,
                                        trackScrollState = trackScrollState,
                                        onImportDrumPadAudio = { drumPadPickerLauncher.launch(arrayOf("audio/*", "*/*")) }
                                    )
                                }
                                MappingPage.DrumPad -> {
                                    DrumPadScreen(
                                        viewModel = viewModel,
                                        uiState = uiState,
                                        onImportFiles = { tab ->
                                            if (tab == "Loops") loopPickerLauncher.launch(arrayOf("audio/*", "*/*"))
                                            else drumPadPickerLauncher.launch(arrayOf("audio/*", "*/*"))
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                else -> {}
                            }
                        },
                        pageScroll = if (uiState.mappingPage == MappingPage.Mixer && !uiState.isFaderAutoFit) trackScrollState else null
                    )
                }
            }
        }
    }

        when (uiState.activePopup) {
            ActivePopup.EFFECTS -> {
                if (FX_WINDOW_V2) {
                    val slotIdx = if (uiState.activeEffectTrackId == 0) null else uiState.activeEffectTrackId - 1
                    val sfTrack = slotIdx?.let { uiState.tracks.getOrNull(it) }
                    com.soundstage.mixer.ui.fx.FxOverlay(
                        trackId = uiState.activeEffectTrackId,
                        soundfontName = sfTrack?.soundfontName.orEmpty(),
                        program = sfTrack?.program ?: 0,
                        onClose = { viewModel.closePopup() },
                        onChooseSoundfont = {
                            if (uiState.activeEffectTrackId in 1..8) {
                                viewModel.openSoundfontForSlot(uiState.activeEffectTrackId - 1)
                            }
                        },
                        modifier = Modifier.fillMaxSize().zIndex(130f),
                    )
                } else {
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
            }
            ActivePopup.SOUNDFONT -> {
                val slot = uiState.audioSlots.getOrNull(uiState.activeSoundfontSlotId)
                val activePresets = slot?.presets ?: emptyList()
                val activePresetId = slot?.preset ?: 0
                // tracks a 8 elements (0..7) : les slots 8 (Drum) et 9 (Pad) n'y ont pas
                // d'entree, getOrNull renvoie null. Le nom fiable est soundFontPath (ecrit
                // pour tous les slots), comme le fait deja DrumPadScreen.
                val activeSfName = if (uiState.activeSoundfontSlotId in 0..7) {
                    uiState.tracks.getOrNull(uiState.activeSoundfontSlotId)?.soundfontName.orEmpty()
                } else {
                    slot?.soundFontPath?.substringAfterLast("/").orEmpty()
                }
                val activeBank = slot?.bank ?: 0

                val variant = when (uiState.activeSoundfontSlotId) {
                    8 -> SoundfontVariant.Drum
                    9 -> SoundfontVariant.Pad
                    else -> SoundfontVariant.Track
                }

                val loadedFileId = activeSfName.ifEmpty { null }

                val availableBanks = remember(activePresets) {
                    activePresets.map { it.bankNumber }.distinct().sorted()
                }

                val sfWindowState = SoundfontWindowState(
                    isOpen = true,
                    variant = variant,
                    loadedFileId = loadedFileId,
                    loadedFileName = activeSfName,
                    loadedPresets = activePresets,
                    loadedBank = activeBank,
                    loadedPresetId = activePresetId,
                    loadedBanks = availableBanks,
                    files = uiState.realSoundfonts.map { item ->
                        val parsed = uiState.sfParsedPresets[item.path]
                        SfRow.File(
                            key = item.name,
                            name = item.name,
                            sizeBytes = item.size,
                            formattedSize = item.formattedSize,
                            isLoaded = item.name == activeSfName,
                            presetCount = parsed?.size ?: 0,
                        )
                    },
                    favorites = uiState.sfFavorites,
                    overrides = uiState.sfCategoryOverrides,
                    parsedPresets = uiState.sfParsedPresets.mapValues { (_, v) ->
                        v.map { SoundfontPreset(it.preset, it.displayName, it.bank) }
                    },
                    parsingFiles = uiState.sfParsingFiles,
                    loadingFileId = null,
                    loadingProgress = null,
                    importingName = null,
                    importingProgress = null,
                )

                SoundfontWindow(
                    state = sfWindowState,
                    actions = object : SfWindowActions {
                        override fun onClose() { viewModel.closePopup() }

                        override fun onSelectPreset(row: SfRow.Preset) {
                            viewModel.selectSf2Preset(
                                SoundfontPreset(row.presetId, row.name, row.bankNumber)
                            )
                        }

                        override fun onLoadFile(fileId: String) {
                            val item = uiState.realSoundfonts.firstOrNull { it.name == fileId } ?: return
                            viewModel.loadSoundfontFromStorage(item)
                        }

                        override fun onCancelLoad() {}

                        override fun onToggleFavorite(key: String) {
                            viewModel.toggleSfFavorite(key)
                        }

                        override fun onMoveToCategory(key: String, categoryId: String) {
                            viewModel.moveSfToCategory(key, categoryId)
                        }

                        override fun onImportFile() {
                            sf2PickerLauncher.launch(arrayOf("*/*"))
                        }

                        override fun onDeleteFile(fileId: String) {
                            val item = uiState.realSoundfonts.firstOrNull { it.name == fileId } ?: return
                            viewModel.deleteSoundFontFile(item)
                        }

                        override fun onRequestParse(fileId: String) {
                            val item = uiState.realSoundfonts.firstOrNull { it.name == fileId } ?: return
                            viewModel.parseSfInBackground(item.path)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(120f)
                )
            }
            ActivePopup.TRACK_SETTINGS -> {
                val targetTrack = uiState.tracks.find { it.id == uiState.activeTrackSettingsId }
                if (targetTrack != null) {
                    val tsScreen = tsPage
                    val tsActions = remember(targetTrack.id) {
                        TrackSettingsActions(
                            onNavigate = { tsPage = it },
                            onClose = { viewModel.closeTrackSettings() },
                            onVelocityChange = { viewModel.updateTrackVelocityConfig(targetTrack.id, TsVelocity.toEngine(it)) },
                            onMidiDevice = { viewModel.updateTrackMidiDevice(targetTrack.id, it) },
                            onMidiChannel = { viewModel.updateTrackMidiChannel(targetTrack.id, it) },
                            onNoteRange = { lo, hi -> viewModel.updateTrackKeyRange(targetTrack.id, lo, hi) },
                            onTranspose = { viewModel.setTrackTranspose(targetTrack.id, it) },
                            onOctave = { viewModel.setTrackOctave(targetTrack.id, it) },
                            onPitchBendRange = { viewModel.setTrackPitchBendRange(targetTrack.id, it) },
                            onDefaultVolume = { viewModel.setTrackDefaultVolume(targetTrack.id, it) },
                            onDefaultPan = { viewModel.setTrackDefaultPan(targetTrack.id, it) },
                            onSustain = { viewModel.setTrackSustain(targetTrack.id, it) },
                            onAftertouch = { viewModel.setTrackAftertouch(targetTrack.id, it) },
                            onProgramChange = { viewModel.setTrackProgramChange(targetTrack.id, it) }
                        )
                    }
                    TrackSettingsWindow(
                        state = TrackSettingsState(
                            trackName = "Track ${targetTrack.id}",
                            soundName = if (targetTrack.patchName.isNotBlank()) targetTrack.patchName else if (targetTrack.soundfontName.isNotBlank()) targetTrack.soundfontName else "Default GM",
                            color = targetTrack.resolvedColor(),
                            velocity = TsVelocity.toUi(targetTrack.velocityConfig),
                            devices = uiState.midiDevices.map { TsDeviceItem(id = it.id, name = it.name, subtitle = if (it.isConnected) "Connected" else "Offline") },
                            midiDeviceId = targetTrack.selectedMidiDeviceId ?: "all",
                            midiChannel = targetTrack.midiChannel,
                            noteLow = targetTrack.splitNoteMin,
                            noteHigh = targetTrack.splitNoteMax,
                            transpose = targetTrack.transpose,
                            octave = targetTrack.octave,
                            pitchBendRange = targetTrack.pitchBendRange,
                            defaultVolume = targetTrack.defaultVolume,
                            defaultPan = targetTrack.defaultPan,
                            sustain = targetTrack.sustain,
                            aftertouch = targetTrack.aftertouch,
                            programChange = targetTrack.programChange
                        ),
                        screen = tsScreen,
                        actions = tsActions,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(600.dp, 370.dp)
                            .zIndex(120f)
                    )
                }
            }
            ActivePopup.VELOCITY_SETTINGS -> {
                val targetTrack = uiState.tracks.find { it.id == uiState.activeTrackSettingsId }
                if (targetTrack != null) {
                    VelocitySettingsDialog(
                        trackNumber = targetTrack.id,
                        soundName = if (targetTrack.patchName.isNotBlank()) targetTrack.patchName else if (targetTrack.soundfontName.isNotBlank()) targetTrack.soundfontName else "Default GM",
                        trackColor = targetTrack.resolvedColor(),
                        config = targetTrack.velocityConfig,
                        onConfigChange = { newConfig ->
                            viewModel.updateTrackVelocityConfig(targetTrack.id, newConfig)
                        },
                        onBack = {
                            viewModel.openTrackSettings(targetTrack.id)
                        },
                        onDismiss = {
                            viewModel.closeTrackSettings()
                        }
                    )
                }
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
            midiMappings = uiState.midiMappings,
            learningActionType = uiState.learningActionType,
            lastMidiSummary = uiState.lastMidiSummary,
            onStartMidiLearn = { viewModel.startMidiLearn(it) },
            onCancelMidiLearn = { viewModel.cancelMidiLearn() },
            onUnmapMidiAction = { viewModel.unmapMidiAction(it) },
            onToggleMidiMappingEnabled = { viewModel.toggleMidiMappingEnabled(it) },
            onUpdateManualMidiMapping = { action, type, ch, num -> viewModel.updateMidiMappingManual(action, type, ch, num) },
            onResetMidiMappings = { viewModel.resetMidiMappingsToDefaults() },
            onClearMidiMappings = { viewModel.clearAllMidiMappings() },
            midiDebounceEnabled = uiState.midiDebounceEnabled,
            midiDebounceMs = uiState.midiDebounceMs,
            onToggleMidiDebounce = { viewModel.toggleMidiDebounce() },
            onUpdateMidiDebounceMs = { viewModel.updateMidiDebounceMs(it) },
            tracks = uiState.tracks,
            selectedMidiOutputDevice = uiState.selectedMidiOutputDevice,
            onSelectMidiOutputDevice = { viewModel.selectMidiOutputDevice(it) },
            onUpdateTrackMidiChannel = { trackId, channel -> viewModel.updateTrackMidiChannel(trackId, channel) },
            onUpdateTrackColor = { trackId, argb -> viewModel.setTrackColor(trackId, argb) },
            audioEngine = uiState.audioEngine,
            onSelectAudioEngine = { viewModel.setAudioEngine(it) },
            audioBufferSize = uiState.audioBufferSize,
            onSelectBufferSize = { viewModel.setAudioBufferSize(it) },
            polyphony = uiState.polyphony,
            onSelectPolyphony = { viewModel.setPolyphony(it) },
            fluidSynthGain = uiState.fluidSynthGain,
            onFluidSynthGainChange = { viewModel.setFluidSynthGain(it) },
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

        // Floating Sheet Window (Spotify style, 0 contour, free drag + magnetic snap, double-tap dismiss)
        if (uiState.floatingSheet != null && uiState.currentPage == AppScreenPage.MIXER) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(85f)
            ) {
                FloatingSheetWindow(
                    sheet = uiState.floatingSheet,
                    scale = uiState.floatingSheetScale,
                    onScaleChange = { viewModel.updateFloatingSheetScale(it) },
                    onClose = { viewModel.closeFloatingSheet() }
                )
            }
        }

        // Patches Sliding Side Drawer (Exact sliding drawer from right matching Settings)
        if (uiState.activePopup == ActivePopup.SCENE) {
            PatchesOverlay(
                state = PatchesUiState(
                    patches = uiState.patches.map { p ->
                        Patch(
                            id = p.id,
                            name = p.name,
                            snapshotCount = p.snapshotCount,
                        )
                    },
                    activePatchId = uiState.activePatchId,
                ),
                actions = object : PatchesActions {
                    override fun onClose() { viewModel.closePopup() }
                    override fun onOpenSetlists() {
                        viewModel.closePopup()
                        viewModel.navigateToProjectsHome()
                    }
                    override fun onSelectPatch(id: String) { viewModel.selectPatch(id) }
                    override fun onToggleActive(id: String) { viewModel.selectPatch(id) }
                    override fun onRenamePatch(id: String) {}
                    override fun onRenamePatchConfirmed(id: String, name: String) { viewModel.renamePatch(id, name) }
                    override fun onDeletePatch(id: String) { viewModel.deletePatch(id) }
                    override fun onUndoDeletePatch() { viewModel.undoDeletePatch() }
                    override fun onCreatePatch(name: String, duplicateFrom: String?) {
                        viewModel.createNewPatch(name, duplicateFrom == null)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(120f)
            )
        }

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

        if (uiState.showLanguageDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.closeLanguageDialog() },
                title = {
                    Text(
                        text = "Language",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("English", "Français", "Español").forEach { label ->
                            val selected = uiState.selectedLanguage == label
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        viewModel.setSelectedLanguage(label)
                                        viewModel.closeLanguageDialog()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.closeLanguageDialog() }) {
                        Text("Close")
                    }
                }
            )
        }

        // Close inner padded box
        }

        // Startup Splash Screen Animation (100% Edge-to-Edge Fullscreen Overlay)
        if (!isSplashFinished) {
            LiveKeysSplashScreen(
                onFinished = { isSplashFinished = true },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(999f)
            )
        }
    }
    }
}

private fun androidx.compose.ui.graphics.Color.toSetlistColor(): SetlistColor {
    val candidates = SetlistColor.values()
    return candidates.minByOrNull { c ->
        val dr = (red - c.base.red) * (red - c.base.red)
        val dg = (green - c.base.green) * (green - c.base.green)
        val db = (blue - c.base.blue) * (blue - c.base.blue)
        dr + dg + db
    } ?: SetlistColor.Blue
}



