package com.soundstage.mixer.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.audio.LooperEngine
import com.soundstage.mixer.ui.components.drumpad.DpColors
import com.soundstage.mixer.ui.components.drumpad.DpDrumPadSettings
import com.soundstage.mixer.ui.components.drumpad.DpFxCase
import com.soundstage.mixer.ui.components.drumpad.DpIcon
import com.soundstage.mixer.ui.components.drumpad.DpFxSheet
import com.soundstage.mixer.ui.components.drumpad.DpLoopCase
import com.soundstage.mixer.ui.components.drumpad.DpManager
import com.soundstage.mixer.ui.components.drumpad.DpMetronomeOverlay
import com.soundstage.mixer.ui.components.drumpad.DpNoteKey
import com.soundstage.mixer.ui.components.drumpad.DpPad
import com.soundstage.mixer.ui.components.drumpad.DpPadEditor
import com.soundstage.mixer.ui.components.drumpad.DpPalette
import com.soundstage.mixer.ui.components.drumpad.DpSeg
import com.soundstage.mixer.ui.components.drumpad.DpTopBar
import com.soundstage.mixer.ui.components.drumpad.DpVolumeKnob
import com.soundstage.mixer.ui.components.drumpad.MetroState
import com.soundstage.mixer.viewmodel.MixerUiState
import com.soundstage.mixer.viewmodel.MixerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val TONIC_NOTES = listOf(
    "C", "C#", "D",
    "D#", "E", "F",
    "F#", "G", "G#",
    "A", "A#", "B"
)

@Composable
fun DrumPadScreen(
    viewModel: MixerViewModel,
    uiState: MixerUiState,
    modifier: Modifier = Modifier,
) {
    // ─── Overlay visibility ───
    var metroOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var tonicFxOpen by remember { mutableStateOf(false) }
    var padsFxOpen by remember { mutableStateOf(false) }
    var editorPadId by remember { mutableStateOf<Int?>(null) }
    var managerOpen by remember { mutableStateOf(false) }
    var managerTab by remember { mutableStateOf("Kits") }
    var managerTarget by remember { mutableStateOf<Int?>(1) }

    // ─── Grid modes ───
    var padMode by remember { mutableStateOf("pads") }       // "pads" | "loops"
    var playEditMode by remember { mutableStateOf("play") }  // "play" | "edit"
    var loopQuantize by remember { mutableStateOf(if (uiState.looper.quantize) "1/16" else "Free") }
    var loopLengthMode by remember { mutableStateOf(uiState.looper.lengthMode) }
    var padCount by remember { mutableStateOf(uiState.padCount) }
    var localPreload by remember { mutableStateOf(true) }
    var localHapticPads by remember { mutableStateOf(true) }
    var localHapticLoops by remember { mutableStateOf(true) }

    // ─── Metronome local state ───
    var metroOn by remember { mutableStateOf(false) }
    var metroState by remember(
        uiState.transportSignature, uiState.transportSubdivision,
        uiState.metronomeSound, uiState.metronomeAccent1, uiState.metronomeAccent13,
        uiState.metronomeVisualFlash, uiState.metronomeCountIn, uiState.metronomeTapBpm
    ) {
        mutableStateOf(
            MetroState(
                on = metroOn,
                sig = uiState.transportSignature,
                sub = uiState.transportSubdivision,
                sound = uiState.metronomeSound,
                volume = 65,
                accent1 = uiState.metronomeAccent1,
                accent13 = uiState.metronomeAccent13,
                visualFlash = uiState.metronomeVisualFlash,
                countIn = uiState.metronomeCountIn,
                tapBpm = uiState.metronomeTapBpm,
            )
        )
    }

    // ─── Beat indicator ───
    var beatIdx by remember { mutableStateOf(0) }
    LaunchedEffect(uiState.transportBpm, uiState.transportSignature) {
        val bpm = uiState.transportBpm.coerceAtLeast(30)
        val beats = parseBeatsPerBar(uiState.transportSignature).coerceAtLeast(1)
        val beatMs = (60_000L / bpm).coerceAtLeast(1L)
        while (isActive) {
            beatIdx = ((System.currentTimeMillis() / beatMs) % beats).toInt()
            delay((beatMs / 4).coerceAtLeast(16L))
        }
    }

    // ─── Frame clock for DpLoopCase ───
    var nowNs by remember { mutableStateOf(System.nanoTime()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            nowNs = System.nanoTime()
            delay(16L)
        }
    }

    // ─── Derived ───
    val beatsPerBar = parseBeatsPerBar(uiState.transportSignature)
    val barDurationNs = (beatsPerBar.toLong() * (60_000_000_000L / uiState.transportBpm.coerceAtLeast(30)))
    val anyRec = uiState.looper.cases.firstOrNull {
        it.state == LooperEngine.CaseState.Recording || it.state == LooperEngine.CaseState.Overdubbing
    }
    val tonicVolume = uiState.audioSlots.firstOrNull { it.slotId == 9 }?.volume ?: 0.8f
    val activeKitName = uiState.activeDrumKitName.ifEmpty {
        uiState.kits.firstOrNull()?.name ?: ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF141B27), Color(0xFF0F141D))))
    ) {
        // ═══════════ TopBar ═══════════
        DpTopBar(
            bpm = uiState.transportBpm,
            onBpmChange = { delta ->
                val next = (uiState.transportBpm + delta).coerceIn(30, 300)
                viewModel.setTransportBpm(next)
            },
            onBackToMixer = { viewModel.navigateBackToMixer() },
            metroOn = metroOn,
            onOpenMetro = { metroOpen = true },
            onOpenSettings = { settingsOpen = true },
        )

        // ═══════════ Main row ═══════════
        Row(Modifier.weight(1f).fillMaxWidth()) {

            // ─── Colonne gauche (196dp) ───
            Column(Modifier.width(196.dp).fillMaxHeight().padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // TonicPad grid 3×4
                Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TONIC_NOTES.chunked(3).forEachIndexed { rowIdx, rowNotes ->
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            rowNotes.forEachIndexed { colIdx, note ->
                                val idx = rowIdx * 3 + colIdx
                                DpNoteKey(
                                    label = note, oct = 0, idx = idx,
                                    on = uiState.activeTonicNotes.contains(note),
                                    ghost = false,
                                    onToggle = { viewModel.onTonicNoteClick(note) },
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                        }
                    }
                }

                // Row [Oct-] [Oct 3] [Oct+] [Multi] [Clear]
                Row(Modifier.fillMaxWidth().height(26.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Bouton Oct -
                    Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF243047)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { viewModel.onTonicOctaveMinus() }, contentAlignment = Alignment.Center) {
                        Text("−", color = Color(0xFFE3E8F2), fontSize = 13.sp)
                    }
                    // Texte Oct + chiffre
                    Row(Modifier.width(48.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text("Oct", color = Color(0xFF7B859A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Text(parseOctave(uiState.tonicOctaveRange).toString(), color = Color(0xFFF1F4FA), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                    }
                    // Bouton Oct +
                    Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF243047)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { viewModel.onTonicOctavePlus() }, contentAlignment = Alignment.Center) {
                        Text("+", color = Color(0xFFE3E8F2), fontSize = 13.sp)
                    }
        
                    Spacer(Modifier.weight(1f))
        
                    // Multi
                    Box(Modifier.height(24.dp).clip(RoundedCornerShape(8.dp)).background(if (uiState.isMultiPadEnabled) Color(0x248FB2E8) else Color.Transparent).border(1.dp, if (uiState.isMultiPadEnabled) Color(0x8C8FB2E8) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { viewModel.toggleMultiPad() }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        Text("Multi", color = if (uiState.isMultiPadEnabled) DpColors.ACCENT else Color(0xFF8690A4), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    // Clear
                    Box(Modifier.height(24.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { viewModel.onTonicNoteClick("") }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        Text("Clear", color = Color(0xFFC7CFDD), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // Row 62dp: knob + tonic FX
                Row(Modifier.fillMaxWidth().height(62.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(62.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        DpVolumeKnob(value = (tonicVolume * 100f), onChange = { viewModel.setTonicVolume(it / 100f) }, modifier = Modifier.fillMaxSize(), showLabel = false)
                    }
                    DpFxCase(fx = uiState.fxChains.tonic, includeShimmer = true, label = "Tonic FX", onOpen = { tonicFxOpen = true }, modifier = Modifier.weight(1f).fillMaxHeight())
                }

                // Row 30dp: nav buttons
                Row(Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Pad Sound -> Ouvre le vrai Soundfont Picker
                    Row(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(Brush.verticalGradient(listOf(Color(0xFF2A3347), Color(0xFF1F2739)))).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { viewModel.openSoundfontForSlot(9) }.padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DpIcon("wave", 12.dp, DpColors.ACCENT)
                        Text("Pad", color = Color(0xFF7B859A), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        Text(uiState.audioSlots.firstOrNull { it.slotId == 9 }?.soundFontPath?.substringAfterLast("/") ?: "None", color = Color(0xFFE3E8F2), fontSize = 10.5.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    // Kit Picker
                    Row(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(Brush.verticalGradient(listOf(Color(0xFF2A3347), Color(0xFF1F2739)))).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { managerTab = "Kits"; managerOpen = true }.padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DpIcon("folder", 12.dp, DpColors.ACCENT)
                        Text("Kit", color = Color(0xFF7B859A), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        Text(activeKitName.ifEmpty { "None" }, color = Color(0xFFE3E8F2), fontSize = 10.5.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }

            // ─── Colonne droite (72%) ───
            Column(
                Modifier
                    .weight(0.72f)
                    .fillMaxHeight()
                    .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Header row 30dp
                Row(
                    Modifier.fillMaxWidth().height(30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DpSeg(
                        options = listOf("pads" to "Pads", "loops" to "Loops"),
                        selected = padMode,
                        onChange = { padMode = it },
                        height = 24.dp,
                        modifier = Modifier.width(120.dp)
                    )
                    if (padMode == "pads") {
                        DpSeg(
                            options = listOf("play" to "Play", "edit" to "Edit"),
                            selected = playEditMode,
                            onChange = { playEditMode = it },
                            height = 24.dp,
                            warn = true,
                            modifier = Modifier.width(100.dp)
                        )
                        SmallChip("${padCount}p", Modifier.height(24.dp)) {
                            val next = if (padCount == 8) 16 else 8
                            padCount = next
                            viewModel.setPadCount(next)
                        }
                        SmallChip("Pads FX", Modifier.height(24.dp)) { padsFxOpen = true }
                    } else {
                        DpSeg(
                            options = listOf(
                                "auto" to "Auto",
                                "1" to "1", "2" to "2",
                                "4" to "4", "8" to "8"
                            ),
                            selected = loopLengthMode,
                            onChange = { loopLengthMode = it; viewModel.setLooperLengthMode(it) },
                            height = 24.dp,
                            modifier = Modifier.width(170.dp)
                        )
                        DpSeg(
                            options = listOf("1/16" to "1/16", "Free" to "Free"),
                            selected = loopQuantize,
                            onChange = {
                                loopQuantize = it
                                viewModel.setLooperQuantize(it == "1/16")
                            },
                            height = 24.dp,
                            modifier = Modifier.width(90.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    BeatDots(beats = beatsPerBar, active = beatIdx)
                    if (anyRec != null && padMode == "pads") {
                        RecBadge()
                    }
                }

                // Grid
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (padMode == "pads") {
                        PadsGrid(
                            uiState = uiState,
                            padCount = padCount,
                            playEditMode = playEditMode,
                            onDown = { viewModel.onDrumPadPressed(it) },
                            onUp = { viewModel.onDrumPadReleased(it) },
                            onVolumeChange = { padId, vol -> viewModel.setDrumPadVolumeDirect(padId, vol / 100f) },
                            onOpenEditor = { editorPadId = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LoopsGrid(
                            cases = uiState.looper.cases,
                            nowNs = nowNs,
                            beatsPerBar = beatsPerBar,
                            barDurationNs = barDurationNs,
                            onTap = { viewModel.tapLooperCase(it) },
                            onLongPress = { viewModel.longPressLooperCaseAction(it, "menu") },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // ═══════════ Overlays ═══════════
    if (metroOpen) {
        DpMetronomeOverlay(
            state = metroState,
            onChange = { next ->
                val wasOn = metroState.on
                metroState = next
                metroOn = next.on
                viewModel.setMetronomeSignature(next.sig)
                viewModel.setTransportSubdivision(next.sub)
                viewModel.setMetronomeSound(next.sound)
                viewModel.setMetronomeVolume(next.volume / 100f)
                viewModel.setMetronomeAccent1(next.accent1)
                viewModel.setMetronomeAccent13(next.accent13)
                viewModel.setMetronomeVisualFlash(next.visualFlash)
                viewModel.setMetronomeCountIn(next.countIn)
                if (next.tapBpm > 0) viewModel.setMetronomeTapBpm(next.tapBpm)
                if (next.on != wasOn) viewModel.toggleMetronome()
            },
            onClose = { metroOpen = false }
        )
    }

    if (settingsOpen) {
        DpDrumPadSettings(
            padCount = padCount,
            onPadCountChange = { padCount = it; viewModel.setPadCount(it) },
            preload = localPreload,
            onPreloadChange = { localPreload = it },
            hapticPads = localHapticPads,
            onHapticPadsChange = { localHapticPads = it },
            hapticLoops = localHapticLoops,
            onHapticLoopsChange = { localHapticLoops = it },
            quantize = loopQuantize,
            onQuantizeChange = {
                loopQuantize = it
                viewModel.setLooperQuantize(it == "1/16")
            },
            onClose = { settingsOpen = false }
        )
    }

    if (tonicFxOpen) {
        DpFxSheet(
            chain = uiState.fxChains.tonic,
            includeShimmer = true,
            label = "TonicPad",
            onChange = { viewModel.setTonicFxChain(it) },
            onClose = { tonicFxOpen = false }
        )
    }

    if (padsFxOpen) {
        DpFxSheet(
            chain = uiState.fxChains.pads,
            includeShimmer = false,
            label = "Pad",
            onChange = { viewModel.setPadsFxChain(it) },
            onClose = { padsFxOpen = false }
        )
    }

    if (editorPadId != null) {
        val pad = uiState.drumPads.firstOrNull { it.id == editorPadId }
        if (pad != null) {
            DpPadEditor(
                pad = pad,
                color = DpPalette.at((pad.id - 1).coerceAtLeast(0)),
                onChange = { updated ->
                    // V1 : ne persiste pas, B7-1 câblera un setter VM dédié
                },
                onClear = {
                    viewModel.assignDrumSampleOrLoop(pad.id, "", "", isLoop = false)
                    editorPadId = null
                },
                onAssignClick = {
                    managerTab = "DrumPad"
                    managerTarget = pad.id
                    managerOpen = true
                    editorPadId = null
                },
                onClose = { editorPadId = null }
            )
        }
    }

    if (managerOpen) {
        DpManager(
            open = true,
            tab = managerTab,
            onTabChange = { managerTab = it },
            openTarget = managerTarget,
            pads = uiState.drumPads,
            drumSamples = uiState.realDrumPadFiles,
            loops = uiState.realLoopFiles,
            kits = uiState.kits,
            activeKitName = activeKitName,
            scenes = emptyList(),
            onLoadKit = { name -> viewModel.loadKit(name) },
            onDeleteKit = { name -> viewModel.deleteKit(name) },
            onSaveCurrentAsKit = { name -> viewModel.saveCurrentPadsAsKit(name) },
            onAssign = { padIndex, sample ->
                viewModel.assignDrumSampleOrLoop(padIndex, sample.name, sample.path, isLoop = false)
            },
            onLoadScene = { /* V1 stub */ },
            onClose = { managerOpen = false }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Grilles
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun PadsGrid(
    uiState: MixerUiState,
    padCount: Int,
    playEditMode: String,
    onDown: (Int) -> Unit,
    onUp: (Int) -> Unit,
    onVolumeChange: (Int, Int) -> Unit,
    onOpenEditor: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cols = 4
    val rows = if (padCount <= 8) 2 else 4
    val pads = uiState.drumPads.take(padCount)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (r in 0 until rows) {
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (c in 0 until cols) {
                    val idx = r * cols + c
                    val pad = pads.getOrNull(idx)
                    if (pad == null) {
                        Box(Modifier.weight(1f).fillMaxHeight())
                    } else if (playEditMode == "edit") {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onOpenEditor(pad.id) }
                        ) {
                            DpPad(
                                index = idx,
                                pad = pad,
                                color = DpPalette.at(idx),
                                mode = "edit",
                                runner = false,
                                flash = false,
                                ghost = false,
                                onDown = {},
                                onUp = {},
                                onVolumeChange = { _, _ -> },
                                appear = false,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        DpPad(
                            index = idx,
                            pad = pad,
                            color = DpPalette.at(idx),
                            mode = "play",
                            runner = pad.isLoopPlaying,
                            flash = pad.isPressed,
                            ghost = false,
                            onDown = onDown,
                            onUp = onUp,
                            onVolumeChange = onVolumeChange,
                            appear = false,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoopsGrid(
    cases: List<LooperEngine.Case>,
    nowNs: Long,
    beatsPerBar: Int,
    barDurationNs: Long,
    onTap: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cols = 4
    val rows = (cases.size + cols - 1) / cols
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (r in 0 until rows) {
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (c in 0 until cols) {
                    val idx = r * cols + c
                    val case = cases.getOrNull(idx)
                    if (case == null) {
                        Box(Modifier.weight(1f).fillMaxHeight())
                    } else {
                        DpLoopCase(
                            index = idx,
                            case = case,
                            nowNs = nowNs,
                            beatsPerBar = beatsPerBar,
                            barDurationNs = barDurationNs,
                            onTap = { onTap(idx) },
                            onLongPress = { onLongPress(idx) },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Small widgets
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun SmallChip(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    accentColor: Color = DpColors.ACCENT,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) accentColor.copy(alpha = 0.14f)
                else Color.White.copy(alpha = 0.05f)
            )
            .border(
                1.dp,
                if (active) accentColor.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f),
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) accentColor else DpColors.SOFT,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun BeatDots(beats: Int, active: Int) {
    Row(
        Modifier.height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (i in 0 until beats) {
            val on = i == active
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (on) DpColors.ACCENT else Color.White.copy(alpha = 0.12f))
            )
        }
    }
}

@Composable
private fun RecBadge() {
    Box(
        Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DpColors.ROSE.copy(alpha = 0.14f))
            .border(1.dp, DpColors.ROSE.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(DpColors.ROSE))
            Text("REC", color = DpColors.ROSE, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun MiniCard(
    icon: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2A3347), Color(0xFF1F2739))))
            .clickable { onClick() }
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(icon, color = DpColors.ACCENT, fontSize = 12.sp)
        Text(label, color = Color(0xFF7B859A), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        Text(
            value,
            color = DpColors.SOFT,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Utils
// ═══════════════════════════════════════════════════════════════════════════
private fun parseBeatsPerBar(sig: String): Int {
    return (sig.substringBefore('/').toIntOrNull() ?: 4).coerceIn(1, 32)
}

private fun parseOctave(range: String): Int {
    // Format attendu : "C2-C5" ou "3" ou ""
    val firstDigit = range.firstOrNull { it.isDigit() }
    return firstDigit?.digitToInt() ?: 3
}
