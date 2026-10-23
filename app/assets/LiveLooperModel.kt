package com.soundstage.mixer.looper // ← adapt to the real package

/*
 * LiveLooper — model, pure logic and controller. No Compose UI here (only @Immutable).
 * Faithful to LoopsPreview.jsx (approved), with the real-app parts the preview only simulated:
 *   - time is expressed in BARS (Double), never in milliseconds, so a tempo change cannot break a loop;
 *   - events carry what is needed to be replayed (source, note or pad code, velocity, on/off);
 *   - recording and replay are driven by the app transport clock, not by the UI frames.
 *
 * LooperLogic        : pure functions (testable without Android).
 * LooperController   : holds the StateFlow<LooperUiState>, applies the logic, exposes LooperActions.
 * LoopPatchJson      : (de)serialization of the loop patches stored with a setlist.
 */

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/* ═══════════════════════════ MODEL ═══════════════════════════ */

enum class CaseMode { Empty, Rec, Play, Overdub, Stopped }

/** An action waiting for its boundary (the case blinks ARMED or QUEUED until then). */
enum class LoopAction { Rec, Finish, Overdub, Commit, Play, Stop }

enum class Quantize { Off, Sixteenth, Bar }
enum class StopAt { EndOfLoop, Now }
enum class AfterRecording { Play, Overdub }
enum class RecordSource { Pads, Tonic, Keys }
enum class PendingLoadChoice { SaveAndLoad, Discard, Cancel }

/** One recorded gesture. posBars = position inside the loop, in bars (0 until the loop length). */
@Immutable
data class LoopEvent(
    val posBars: Float,
    val layer: Int,
    val source: RecordSource,
    val code: Int,          // pad index or MIDI note
    val velocity: Int,
    val on: Boolean = true  // note on / note off (drones and held notes need both)
)

@Immutable
data class RemovedLayer(val events: List<LoopEvent>, val muted: Boolean, val bars: Int)

@Immutable
data class RecordSources(val pads: Boolean = true, val tonic: Boolean = true, val keys: Boolean = true) {
    fun has(s: RecordSource) = when (s) { RecordSource.Pads -> pads; RecordSource.Tonic -> tonic; RecordSource.Keys -> keys }
    fun toggled(s: RecordSource) = when (s) {
        RecordSource.Pads -> copy(pads = !pads); RecordSource.Tonic -> copy(tonic = !tonic); RecordSource.Keys -> copy(keys = !keys)
    }
}

@Immutable
data class LoopCase(
    val id: Int,                                  // 0..7, shown as L1..L8
    val name: String = "",
    val mode: CaseMode = CaseMode.Empty,
    val bars: Int = 0,                            // loop length: 1..8 bars
    val layers: Int = 0,                          // 0..4
    val events: List<LoopEvent> = emptyList(),
    val startBar: Double = 0.0,                   // bar at which loop position 0 occurs (Play / Overdub)
    val recStartBar: Double = 0.0,
    val fixedLength: Int? = null,                 // length chosen when the recording was armed (null = Auto)
    val queued: LoopAction? = null,
    val queuedAtBar: Double = 0.0,
    val mute: Boolean = false,
    val volume: Int = 100,                        // 0..100
    val layerMute: List<Boolean> = List(4) { false },
    val redo: List<RemovedLayer> = emptyList(),
    val sources: RecordSources = RecordSources(),
    val stopAt: StopAt = StopAt.EndOfLoop,
    val afterRecording: AfterRecording = AfterRecording.Play,
    val oneShot: Boolean = false
) {
    val hasLoop: Boolean get() = layers > 0 && bars > 0

    /** What a patch stores: the content and the case settings, never the transport state. */
    fun snapshot(): LoopCase = copy(
        mode = if (layers > 0) CaseMode.Stopped else CaseMode.Empty,
        startBar = 0.0, recStartBar = 0.0, fixedLength = null, queued = null, queuedAtBar = 0.0, redo = emptyList()
    )
}

@Immutable
data class LooperSettings(
    val quantize: Quantize = Quantize.Bar,
    val fixedLength: Int? = null,   // null = Auto; 1, 2, 4 or 8
    val countInBars: Int = 0,       // 0, 1 or 2 (applied to Rec only)
    val beatsPerBar: Int = 4
)

@Immutable
data class LoopPatch(val id: String, val name: String, val cases: List<LoopCase>)

@Immutable
data class LooperToast(val key: Long, val text: String, val undoable: Boolean = false)

@Immutable
data class LooperUiState(
    val cases: List<LoopCase> = List(8) { LoopCase(it) },
    val settings: LooperSettings = LooperSettings(),
    val focusId: Int? = null,
    val sheetCaseId: Int? = null,
    val patches: List<LoopPatch> = emptyList(),
    val activePatchId: String? = null,
    val patchWindowOpen: Boolean = false,
    val pendingLoadId: String? = null,
    val confirmClearAll: Boolean = false,
    val setlistName: String = "",
    val toast: LooperToast? = null
) {
    val activePatch: LoopPatch? get() = patches.firstOrNull { it.id == activePatchId }
    /** Unsaved changes: the amber dot. Compared on snapshots, so transport state never counts. */
    val isDirty: Boolean get() = activePatch?.let { p -> cases.map { it.snapshot() } != p.cases } ?: cases.any { it.hasLoop }
    val usedCount: Int get() = cases.count { it.hasLoop }
    val anyPlaying: Boolean get() = cases.any { it.mode == CaseMode.Play || it.mode == CaseMode.Overdub }
    val anyStopped: Boolean get() = cases.any { it.mode == CaseMode.Stopped && it.layers > 0 }
}

@Immutable
data class LoopPosition(val fraction: Float, val bar: Int)

/** All the callbacks of the UI. Defaults are no-ops (previews). LooperController.actions() fills them. */
class LooperActions(
    val onFocus: (Int?) -> Unit = {},
    val onSheet: (Int?) -> Unit = {},
    val onRec: (Int) -> Unit = {},
    val onPlay: (Int) -> Unit = {},
    val onHoldPlay: (Int) -> Unit = {},          // hold Play: mute
    val onUndo: (Int) -> Unit = {},
    val onHoldUndo: (Int) -> Unit = {},          // hold Undo: redo
    val onRedo: (Int) -> Unit = {},
    val onRemoveLayer: (Int, Int) -> Unit = { _, _ -> },
    val onToggleLayerMute: (Int, Int) -> Unit = { _, _ -> },
    val onVolume: (Int, Int) -> Unit = { _, _ -> },
    val onToggleMute: (Int) -> Unit = {},
    val onDouble: (Int) -> Unit = {},
    val onHalf: (Int) -> Unit = {},
    val onDuplicate: (Int) -> Unit = {},
    val onClear: (Int) -> Unit = {},
    val onMidiLearn: (Int) -> Unit = {},
    val onToggleSource: (Int, RecordSource) -> Unit = { _, _ -> },
    val onStopAt: (Int, StopAt) -> Unit = { _, _ -> },
    val onAfterRecording: (Int, AfterRecording) -> Unit = { _, _ -> },
    val onOneShot: (Int, Boolean) -> Unit = { _, _ -> },
    val onRename: (Int, String) -> Unit = { _, _ -> },
    val onQuantize: (Quantize) -> Unit = {},
    val onFixedLength: (Int?) -> Unit = {},
    val onCountIn: (Int) -> Unit = {},
    val onStopAll: () -> Unit = {},
    val onPlayAll: () -> Unit = {},
    val onRequestClearAll: () -> Unit = {},
    val onConfirmClearAll: () -> Unit = {},
    val onDismissClearAll: () -> Unit = {},
    val onOpenPatches: () -> Unit = {},
    val onClosePatches: () -> Unit = {},
    val onLoadPatch: (String) -> Unit = {},
    val onStepPatch: (Int) -> Unit = {},
    val onSavePatch: () -> Unit = {},
    val onSaveNewPatch: () -> Unit = {},
    val onRevertPatch: () -> Unit = {},
    val onRenamePatch: (String, String) -> Unit = { _, _ -> },
    val onDuplicatePatch: (String) -> Unit = {},
    val onOverwritePatch: (String) -> Unit = {},
    val onDeletePatch: (String) -> Unit = {},
    val onPendingLoad: (PendingLoadChoice) -> Unit = {},
    val onToastUndo: () -> Unit = {},
    val onToastDismiss: () -> Unit = {}
)

/* ═══════════════════════════ PURE LOGIC ═══════════════════════════ */

object LooperLogic {
    const val MAX_LAYERS = 4
    const val MAX_BARS = 8

    /** Next boundary strictly after nowBar for the chosen quantization. Off = now. */
    fun nextBoundary(nowBar: Double, q: Quantize, beatsPerBar: Int): Double = when (q) {
        Quantize.Off -> nowBar
        Quantize.Sixteenth -> { val u = 1.0 / (beatsPerBar * 4); (floor(nowBar / u) + 1) * u }
        Quantize.Bar -> floor(nowBar) + 1.0
    }

    fun nextLoopBoundary(c: LoopCase, nowBar: Double): Double {
        if (c.bars <= 0) return nowBar
        val base = max(c.startBar, nowBar)
        return c.startBar + (floor((base - c.startBar) / c.bars) + 1) * c.bars
    }

    /** Position inside the loop: fraction 0..1 and the 1-based bar. Null when the case is not running. */
    fun position(c: LoopCase, nowBar: Double): LoopPosition? = when (c.mode) {
        CaseMode.Play, CaseMode.Overdub -> if (c.bars <= 0) null else {
            val f = (((nowBar - c.startBar) / c.bars) % 1.0 + 1.0) % 1.0
            LoopPosition(f.toFloat(), floor(f * c.bars).toInt() + 1)
        }
        CaseMode.Rec -> {
            val el = nowBar - c.recStartBar
            LoopPosition((((el % 1.0) + 1.0) % 1.0).toFloat(), floor(el).toInt() + 1)   // the first take is drawn on one bar
        }
        else -> null
    }

    /** Puts an action in the queue. It will apply when the transport reaches queuedAtBar (see tick). */
    fun queue(c: LoopCase, action: LoopAction, s: LooperSettings, nowBar: Double): LoopCase {
        var at = when {
            action == LoopAction.Stop && c.stopAt == StopAt.Now -> nowBar
            action == LoopAction.Stop && (c.mode == CaseMode.Play || c.mode == CaseMode.Overdub) -> nextLoopBoundary(c, nowBar)
            action == LoopAction.Stop -> nowBar
            else -> nextBoundary(nowBar, s.quantize, s.beatsPerBar)
        }
        if (action == LoopAction.Rec && s.countInBars > 0) at += s.countInBars
        return c.copy(queued = action, queuedAtBar = at)
    }

    fun apply(c: LoopCase, action: LoopAction, atBar: Double): LoopCase = when (action) {
        LoopAction.Rec -> c.copy(mode = CaseMode.Rec, recStartBar = atBar, events = emptyList(), redo = emptyList())
        LoopAction.Finish -> {
            val bars = c.fixedLength ?: max(1, (atBar - c.recStartBar).roundToInt()).coerceAtMost(MAX_BARS)
            c.copy(
                bars = bars, layers = 1, startBar = c.recStartBar, queued = null, redo = emptyList(),
                mode = if (c.afterRecording == AfterRecording.Overdub) CaseMode.Overdub else CaseMode.Play,
                events = c.events.map { it.copy(posBars = (it.posBars % bars)) }
            )
        }
        LoopAction.Overdub -> c.copy(mode = CaseMode.Overdub, startBar = if (c.mode == CaseMode.Stopped) atBar else c.startBar)
        LoopAction.Commit -> c.copy(mode = CaseMode.Play, layers = min(MAX_LAYERS, c.layers + 1), redo = emptyList())
        LoopAction.Play -> c.copy(mode = CaseMode.Play, startBar = atBar)
        LoopAction.Stop -> c.copy(mode = CaseMode.Stopped, layers = if (c.mode == CaseMode.Overdub) min(MAX_LAYERS, c.layers + 1) else c.layers)
    }

    /** Called continuously by the controller: applies due actions, fixed-length stop and one-shot end. */
    fun tick(c: LoopCase, nowBar: Double): LoopCase {
        var x = c
        val q = x.queued
        if (q != null && nowBar >= x.queuedAtBar) x = apply(x.copy(queued = null), q, x.queuedAtBar)
        val fixed = x.fixedLength
        if (x.mode == CaseMode.Rec && fixed != null && nowBar - x.recStartBar >= fixed) x = apply(x, LoopAction.Finish, x.recStartBar + fixed)
        if (x.mode == CaseMode.Play && x.oneShot && x.bars > 0 && nowBar - x.startBar >= x.bars) x = x.copy(mode = CaseMode.Stopped)
        return x
    }

    /** Records one played gesture into a case that is recording (first take or overdub). */
    fun recordEvent(c: LoopCase, nowBar: Double, source: RecordSource, code: Int, velocity: Int, on: Boolean): LoopCase {
        if (!c.sources.has(source)) return c
        return when (c.mode) {
            CaseMode.Rec -> c.copy(events = c.events + LoopEvent((nowBar - c.recStartBar).toFloat(), 0, source, code, velocity, on))
            CaseMode.Overdub -> {
                val len = c.bars.toDouble()
                val pos = (((nowBar - c.startBar) % len) + len) % len
                c.copy(events = c.events + LoopEvent(pos.toFloat(), c.layers, source, code, velocity, on))
            }
            else -> c
        }
    }

    /** Events to replay between two transport positions (wrap-around handled). Skips muted layers and muted cases. */
    fun dueEvents(c: LoopCase, fromBar: Double, toBar: Double): List<LoopEvent> {
        if ((c.mode != CaseMode.Play && c.mode != CaseMode.Overdub) || c.mute || c.bars <= 0 || toBar <= fromBar) return emptyList()
        val len = c.bars.toDouble()
        val a = (((fromBar - c.startBar) % len) + len) % len
        val b = (((toBar - c.startBar) % len) + len) % len
        return c.events.filter { e ->
            if (e.layer >= c.layers || c.layerMute.getOrElse(e.layer) { false }) return@filter false
            val p = e.posBars.toDouble()
            if (a <= b) p > a && p <= b else p > a || p <= b
        }
    }

    fun undo(c: LoopCase): LoopCase {
        if (c.layers == 0 || c.mode == CaseMode.Rec || c.mode == CaseMode.Overdub) return c
        val i = c.layers - 1
        val removed = RemovedLayer(c.events.filter { it.layer == i }, c.layerMute[i], c.bars)
        val x = c.copy(
            redo = c.redo + removed, events = c.events.filter { it.layer != i },
            layerMute = c.layerMute.toMutableList().also { it[i] = false }, layers = c.layers - 1
        )
        return if (x.layers == 0) x.copy(mode = CaseMode.Empty, queued = null) else x
    }

    fun redo(c: LoopCase, nowBar: Double): LoopCase {
        if (c.redo.isEmpty()) return c
        val r = c.redo.last()
        val x = c.copy(
            redo = c.redo.dropLast(1), bars = r.bars, events = c.events + r.events.map { it.copy(layer = c.layers) },
            layerMute = c.layerMute.toMutableList().also { it[c.layers] = r.muted }, layers = c.layers + 1
        )
        return if (c.mode == CaseMode.Empty) x.copy(mode = CaseMode.Stopped, startBar = nextBoundary(nowBar, Quantize.Bar, 4)) else x
    }

    /** Removes one chosen layer and shifts the layers above it. The case is emptied when it was the last one. */
    fun removeLayer(c: LoopCase, i: Int): LoopCase {
        if (i !in 0 until c.layers) return c
        val ev = c.events.filter { it.layer != i }.map { if (it.layer > i) it.copy(layer = it.layer - 1) else it }
        val mutes = c.layerMute.toMutableList().also { it.removeAt(i); it.add(false) }
        val x = c.copy(events = ev, layerMute = mutes, layers = c.layers - 1)
        return if (x.layers == 0) LoopCase(c.id) else x
    }

    fun toggleLayerMute(c: LoopCase, i: Int) =
        if (i in 0 until c.layers) c.copy(layerMute = c.layerMute.toMutableList().also { it[i] = !it[i] }) else c

    fun doubled(c: LoopCase): LoopCase {
        if (!c.hasLoop || c.bars * 2 > MAX_BARS || c.mode == CaseMode.Rec || c.mode == CaseMode.Overdub) return c
        val old = c.bars
        return c.copy(bars = old * 2, events = c.events + c.events.map { it.copy(posBars = it.posBars + old) })
    }

    fun halved(c: LoopCase): LoopCase {
        if (!c.hasLoop || c.bars < 2 || c.mode == CaseMode.Rec || c.mode == CaseMode.Overdub) return c
        val n = c.bars / 2
        return c.copy(bars = n, events = c.events.map { it.copy(posBars = it.posBars % n) })   // the second half is folded onto the first
    }

    fun duplicateInto(src: LoopCase, target: LoopCase): LoopCase =
        src.snapshot().copy(id = target.id, mode = CaseMode.Stopped, startBar = src.startBar)

    fun busyOther(cases: List<LoopCase>, id: Int) =
        cases.any { it.id != id && (it.mode == CaseMode.Rec || it.mode == CaseMode.Overdub || it.queued == LoopAction.Rec) }
}

/* ═══════════════════════════ CONTROLLER ═══════════════════════════ */

/**
 * @param clock        the app transport position in bars (Double, 1.0 per bar). Same clock as the metronome.
 * @param onReplay     called for every event that must sound now (case, event, case volume 0..1). Route it to the
 *                     SAME trigger path as live playing. Call tick() from the transport / audio clock, not from UI frames.
 * @param onPatchesChanged called whenever the patches or the active patch change: persist them with the setlist.
 */
class LooperController(
    private val clock: () -> Double,
    initialPatches: List<LoopPatch> = emptyList(),
    initialActiveId: String? = null,
    setlistName: String = "",
    private val onReplay: (caseId: Int, event: LoopEvent, volume: Float) -> Unit = { _, _, _ -> },
    private val onPatchesChanged: (patches: List<LoopPatch>, activeId: String?) -> Unit = { _, _ -> }
) {
    private val _state = MutableStateFlow(LooperUiState(patches = initialPatches, activePatchId = initialActiveId, setlistName = setlistName))
    val state: StateFlow<LooperUiState> = _state.asStateFlow()
    private var lastBar = clock()
    private var undoToast: (() -> Unit)? = null

    private fun now() = clock()
    private fun cur() = _state.value
    private fun updateCase(id: Int, f: (LoopCase) -> LoopCase) = _state.update { s -> s.copy(cases = s.cases.map { if (it.id == id) f(it) else it }) }
    private fun toast(text: String, undo: (() -> Unit)? = null) {
        undoToast = undo
        _state.update { it.copy(toast = LooperToast(System.nanoTime(), text, undo != null)) }
    }

    /** Advance the state machine and replay recorded events. Call it at least every 10 ms from the transport clock. */
    fun tick() {
        val nb = now()
        val prev = lastBar
        lastBar = nb
        val s = cur()
        val after = s.cases.map { LooperLogic.tick(it, nb) }
        if (after != s.cases) _state.update { it.copy(cases = after) }
        after.forEach { c -> LooperLogic.dueEvents(c, prev, nb).forEach { onReplay(c.id, it, c.volume / 100f) } }
    }

    /** The app calls this for every note or pad played live, so the recording cases can store it. */
    fun onPlayed(source: RecordSource, code: Int, velocity: Int, on: Boolean) {
        val nb = now()
        _state.update { s -> s.copy(cases = s.cases.map { LooperLogic.recordEvent(it, nb, source, code, velocity, on) }) }
    }

    /* ── case actions ── */
    fun pressRec(id: Int) {
        val s = cur(); val c = s.cases[id]; val nb = now()
        when {
            c.queued != null && c.mode == CaseMode.Empty -> updateCase(id) { it.copy(queued = null) }
            c.mode == CaseMode.Empty -> {
                if (LooperLogic.busyOther(s.cases, id)) { toast("One case records at a time"); return }
                updateCase(id) { LooperLogic.queue(it.copy(fixedLength = s.settings.fixedLength), LoopAction.Rec, s.settings, nb) }
            }
            c.mode == CaseMode.Rec -> updateCase(id) { LooperLogic.queue(it, LoopAction.Finish, s.settings, nb) }
            c.mode == CaseMode.Play || c.mode == CaseMode.Stopped -> {
                if (c.layers >= LooperLogic.MAX_LAYERS) { toast("4 layers max"); return }
                updateCase(id) { LooperLogic.queue(it, LoopAction.Overdub, s.settings, nb) }
            }
            c.mode == CaseMode.Overdub -> updateCase(id) { LooperLogic.queue(it, LoopAction.Commit, s.settings, nb) }
        }
    }

    fun pressPlay(id: Int) {
        val s = cur(); val c = s.cases[id]; val nb = now()
        val action = when (c.mode) {
            CaseMode.Rec -> LoopAction.Finish
            CaseMode.Play, CaseMode.Overdub -> LoopAction.Stop
            CaseMode.Stopped -> LoopAction.Play
            CaseMode.Empty -> { toast("Record a layer first"); return }
        }
        updateCase(id) { LooperLogic.queue(it, action, s.settings, nb) }
    }

    fun toggleMute(id: Int) {
        val c = cur().cases[id]
        if (c.layers == 0) return
        updateCase(id) { it.copy(mute = !it.mute) }
        toast("L${id + 1} ${if (!c.mute) "muted" else "unmuted"}")
    }

    fun undo(id: Int) {
        val before = cur().cases[id]
        if (before.layers == 0 || before.mode == CaseMode.Rec || before.mode == CaseMode.Overdub) return
        updateCase(id) { LooperLogic.undo(it) }
        toast("L${id + 1} · last layer removed")
    }

    fun redo(id: Int) {
        if (cur().cases[id].redo.isEmpty()) { toast("Nothing to redo"); return }
        val nb = now()
        updateCase(id) { LooperLogic.redo(it, nb) }
        toast("L${id + 1} · layer restored")
    }

    fun removeLayer(id: Int, i: Int) = updateCase(id) { LooperLogic.removeLayer(it, i) }
    fun toggleLayerMute(id: Int, i: Int) = updateCase(id) { LooperLogic.toggleLayerMute(it, i) }
    fun setVolume(id: Int, v: Int) = updateCase(id) { it.copy(volume = v.coerceIn(0, 100)) }
    fun double(id: Int) { updateCase(id) { LooperLogic.doubled(it) }; _state.update { it.copy(sheetCaseId = null) }; toast("L${id + 1} · ${cur().cases[id].bars} bars") }
    fun half(id: Int) { updateCase(id) { LooperLogic.halved(it) }; _state.update { it.copy(sheetCaseId = null) }; toast("L${id + 1} · ${cur().cases[id].bars} bar${if (cur().cases[id].bars > 1) "s" else ""}") }

    fun duplicate(id: Int) {
        val s = cur(); val src = s.cases[id]
        val target = s.cases.firstOrNull { it.id != id && it.mode == CaseMode.Empty && it.layers == 0 } ?: run { toast("No empty case"); return }
        updateCase(target.id) { LooperLogic.duplicateInto(src, it) }
        _state.update { it.copy(sheetCaseId = null) }
        toast("Copied to L${target.id + 1} · press play")
    }

    fun clear(id: Int) {
        val saved = cur().cases[id]
        updateCase(id) { LoopCase(id) }
        _state.update { it.copy(sheetCaseId = null) }
        toast("L${id + 1} cleared") { updateCase(id) { saved } }   // no confirmation: UNDO for a few seconds
    }

    fun midiLearn(id: Int) { _state.update { it.copy(sheetCaseId = null) }; toast("MIDI learn: map the actions of L${id + 1}") /* hook to the app MIDI mapping */ }
    fun toggleSource(id: Int, s: RecordSource) = updateCase(id) { it.copy(sources = it.sources.toggled(s)) }
    fun setStopAt(id: Int, v: StopAt) = updateCase(id) { it.copy(stopAt = v) }
    fun setAfterRecording(id: Int, v: AfterRecording) = updateCase(id) { it.copy(afterRecording = v) }
    fun setOneShot(id: Int, v: Boolean) = updateCase(id) { it.copy(oneShot = v) }
    fun rename(id: Int, name: String) = updateCase(id) { it.copy(name = name.take(22)) }

    /* ── transport ── */
    fun setQuantize(q: Quantize) = _state.update { it.copy(settings = it.settings.copy(quantize = q)) }
    fun setFixedLength(n: Int?) = _state.update { it.copy(settings = it.settings.copy(fixedLength = n)) }
    fun setCountIn(n: Int) = _state.update { it.copy(settings = it.settings.copy(countInBars = n)) }
    fun stopAll() { val s = cur(); val nb = now(); _state.update { st -> st.copy(cases = st.cases.map { if (it.mode == CaseMode.Play || it.mode == CaseMode.Overdub) LooperLogic.queue(it, LoopAction.Stop, s.settings, nb) else it }) } }
    fun playAll() { val s = cur(); val nb = now(); _state.update { st -> st.copy(cases = st.cases.map { if (it.mode == CaseMode.Stopped && it.layers > 0) LooperLogic.queue(it, LoopAction.Play, s.settings, nb) else it }) } }
    fun confirmClearAll() { _state.update { it.copy(cases = List(8) { i -> LoopCase(i) }, confirmClearAll = false, focusId = null, sheetCaseId = null) }; toast("All cases cleared") }

    fun setFocus(id: Int?) = _state.update { it.copy(focusId = id) }
    fun setSheet(id: Int?) = _state.update { it.copy(sheetCaseId = id) }

    /* ── patches (stored with the setlist) ── */
    private fun persist() = cur().let { onPatchesChanged(it.patches, it.activePatchId) }

    private fun applyPatch(p: LoopPatch) {
        val s = cur(); val nb = now()
        val cases = s.cases.indices.map { i ->
            val snap = p.cases.getOrNull(i)?.copy(id = i) ?: LoopCase(i)
            val c = snap.copy(mode = if (snap.layers > 0) CaseMode.Stopped else CaseMode.Empty, startBar = LooperLogic.nextBoundary(nb, Quantize.Bar, s.settings.beatsPerBar))
            if (c.layers > 0) LooperLogic.queue(c, LoopAction.Play, s.settings, nb) else c   // every case starts together, on the quantized boundary
        }
        _state.update { it.copy(cases = cases, activePatchId = p.id, focusId = null, sheetCaseId = null, pendingLoadId = null) }
        persist(); toast("“${p.name}” loaded · starts on the next bar")
    }

    fun loadPatch(id: String) {
        val s = cur(); val p = s.patches.firstOrNull { it.id == id } ?: return
        if (id == s.activePatchId && !s.isDirty) return
        if (s.isDirty && s.activePatch != null) { _state.update { it.copy(pendingLoadId = id, patchWindowOpen = false) }; return }
        _state.update { it.copy(patchWindowOpen = false) }
        applyPatch(p)
    }
    fun stepPatch(d: Int) {
        val s = cur(); if (s.patches.isEmpty()) return
        val i = s.patches.indexOfFirst { it.id == s.activePatchId }.coerceAtLeast(0)
        loadPatch(s.patches[(i + d + s.patches.size) % s.patches.size].id)
    }
    fun resolvePendingLoad(choice: PendingLoadChoice) {
        val s = cur(); val target = s.patches.firstOrNull { it.id == s.pendingLoadId }
        _state.update { it.copy(pendingLoadId = null) }
        if (target == null) return
        when (choice) {
            PendingLoadChoice.Cancel -> Unit
            PendingLoadChoice.Discard -> applyPatch(target)
            PendingLoadChoice.SaveAndLoad -> { savePatch(); applyPatch(target) }
        }
    }
    fun savePatch() {
        val s = cur(); val id = s.activePatchId ?: return
        _state.update { st -> st.copy(patches = st.patches.map { if (it.id == id) it.copy(cases = st.cases.map { c -> c.snapshot() }) else it }) }
        persist(); toast("Patch saved with the setlist")
    }
    fun saveNewPatch() {
        val s = cur()
        val p = LoopPatch("lp" + System.currentTimeMillis(), "Loop patch ${s.patches.size + 1}", s.cases.map { it.snapshot() })
        _state.update { it.copy(patches = it.patches + p, activePatchId = p.id) }
        persist(); toast("“${p.name}” saved")
    }
    fun revertPatch() { cur().activePatch?.let { _state.update { s -> s.copy(patchWindowOpen = false) }; applyPatch(it) } }
    fun renamePatch(id: String, name: String) { _state.update { s -> s.copy(patches = s.patches.map { if (it.id == id) it.copy(name = name.take(22)) else it }) }; persist() }
    fun duplicatePatch(id: String) {
        _state.update { s ->
            val i = s.patches.indexOfFirst { it.id == id }; if (i < 0) return@update s
            val p = s.patches[i]
            s.copy(patches = s.patches.toMutableList().also { it.add(i + 1, p.copy(id = "lp" + System.currentTimeMillis(), name = p.name + " copy")) })
        }
        persist()
    }
    fun overwritePatch(id: String) {
        _state.update { s -> s.copy(patches = s.patches.map { if (it.id == id) it.copy(cases = s.cases.map { c -> c.snapshot() }) else it }) }
        persist(); toast("Patch overwritten")
    }
    fun deletePatch(id: String) {
        val s = cur(); val i = s.patches.indexOfFirst { it.id == id }
        if (i < 0 || id == s.activePatchId || s.patches.size <= 1) return
        val p = s.patches[i]
        _state.update { it.copy(patches = it.patches.filter { x -> x.id != id }) }
        persist()
        toast("“${p.name}” deleted") { _state.update { st -> st.copy(patches = st.patches.toMutableList().also { l -> l.add(min(i, l.size), p) }) }; persist() }
    }
    fun openPatches() = _state.update { it.copy(patchWindowOpen = true) }
    fun closePatches() = _state.update { it.copy(patchWindowOpen = false) }
    fun requestClearAll() = _state.update { it.copy(confirmClearAll = true) }
    fun dismissClearAll() = _state.update { it.copy(confirmClearAll = false) }
    fun undoToast() { undoToast?.invoke(); undoToast = null; _state.update { it.copy(toast = null) } }
    fun dismissToast() { undoToast = null; _state.update { it.copy(toast = null) } }

    fun actions() = LooperActions(
        onFocus = ::setFocus, onSheet = ::setSheet, onRec = ::pressRec, onPlay = ::pressPlay, onHoldPlay = ::toggleMute,
        onUndo = ::undo, onHoldUndo = ::redo, onRedo = ::redo, onRemoveLayer = ::removeLayer, onToggleLayerMute = ::toggleLayerMute,
        onVolume = ::setVolume, onToggleMute = ::toggleMute, onDouble = ::double, onHalf = ::half, onDuplicate = ::duplicate,
        onClear = ::clear, onMidiLearn = ::midiLearn, onToggleSource = ::toggleSource, onStopAt = ::setStopAt,
        onAfterRecording = ::setAfterRecording, onOneShot = ::setOneShot, onRename = ::rename,
        onQuantize = ::setQuantize, onFixedLength = ::setFixedLength, onCountIn = ::setCountIn,
        onStopAll = ::stopAll, onPlayAll = ::playAll, onRequestClearAll = ::requestClearAll,
        onConfirmClearAll = ::confirmClearAll, onDismissClearAll = ::dismissClearAll,
        onOpenPatches = ::openPatches, onClosePatches = ::closePatches, onLoadPatch = ::loadPatch, onStepPatch = ::stepPatch,
        onSavePatch = ::savePatch, onSaveNewPatch = ::saveNewPatch, onRevertPatch = ::revertPatch, onRenamePatch = ::renamePatch,
        onDuplicatePatch = ::duplicatePatch, onOverwritePatch = ::overwritePatch, onDeletePatch = ::deletePatch,
        onPendingLoad = ::resolvePendingLoad, onToastUndo = ::undoToast, onToastDismiss = ::dismissToast
    )
}

/* ═══════════════════════════ PERSISTENCE (stored with the setlist) ═══════════════════════════ */

object LoopPatchJson {
    private const val VERSION = 1

    fun toJson(patches: List<LoopPatch>, activeId: String?): String = JSONObject().apply {
        put("v", VERSION)
        put("active", activeId ?: JSONObject.NULL)
        put("patches", JSONArray().apply { patches.forEach { put(patchToJson(it)) } })
    }.toString()

    fun fromJson(json: String?): Pair<List<LoopPatch>, String?> {
        if (json.isNullOrBlank()) return emptyList<LoopPatch>() to null
        return try {
            val o = JSONObject(json)
            val arr = o.optJSONArray("patches") ?: JSONArray()
            val patches = (0 until arr.length()).map { patchFromJson(arr.getJSONObject(it)) }
            patches to (if (o.isNull("active")) null else o.optString("active"))
        } catch (e: Exception) { emptyList<LoopPatch>() to null }   // never crash a show on a bad file
    }

    private fun patchToJson(p: LoopPatch) = JSONObject().apply {
        put("id", p.id); put("name", p.name)
        put("cases", JSONArray().apply { p.cases.forEach { put(caseToJson(it)) } })
    }

    private fun patchFromJson(o: JSONObject): LoopPatch {
        val a = o.optJSONArray("cases") ?: JSONArray()
        return LoopPatch(o.getString("id"), o.optString("name", "Loop patch"), (0 until a.length()).map { caseFromJson(a.getJSONObject(it)) })
    }

    private fun caseToJson(c: LoopCase) = JSONObject().apply {
        put("id", c.id); put("name", c.name); put("bars", c.bars); put("layers", c.layers)
        put("mute", c.mute); put("volume", c.volume); put("oneShot", c.oneShot)
        put("stopAt", c.stopAt.name); put("after", c.afterRecording.name)
        put("layerMute", JSONArray().apply { c.layerMute.forEach { put(it) } })
        put("src", JSONObject().apply { put("pads", c.sources.pads); put("tonic", c.sources.tonic); put("keys", c.sources.keys) })
        put("events", JSONArray().apply {
            c.events.forEach { e ->
                put(JSONArray().apply { put(e.posBars.toDouble()); put(e.layer); put(e.source.ordinal); put(e.code); put(e.velocity); put(if (e.on) 1 else 0) })
            }
        })
    }

    private fun caseFromJson(o: JSONObject): LoopCase {
        val lm = o.optJSONArray("layerMute")
        val ev = o.optJSONArray("events") ?: JSONArray()
        val src = o.optJSONObject("src")
        val srcs = RecordSource.values()
        val layers = o.optInt("layers", 0)
        return LoopCase(
            id = o.optInt("id", 0), name = o.optString("name", ""), bars = o.optInt("bars", 0), layers = layers,
            mode = if (layers > 0) CaseMode.Stopped else CaseMode.Empty,
            mute = o.optBoolean("mute", false), volume = o.optInt("volume", 100), oneShot = o.optBoolean("oneShot", false),
            stopAt = runCatching { StopAt.valueOf(o.optString("stopAt")) }.getOrDefault(StopAt.EndOfLoop),
            afterRecording = runCatching { AfterRecording.valueOf(o.optString("after")) }.getOrDefault(AfterRecording.Play),
            layerMute = List(4) { lm?.optBoolean(it, false) ?: false },
            sources = RecordSources(src?.optBoolean("pads", true) ?: true, src?.optBoolean("tonic", true) ?: true, src?.optBoolean("keys", true) ?: true),
            events = (0 until ev.length()).map {
                val a = ev.getJSONArray(it)
                LoopEvent(a.getDouble(0).toFloat(), a.getInt(1), srcs[a.getInt(2).coerceIn(0, srcs.size - 1)], a.getInt(3), a.getInt(4), a.optInt(5, 1) == 1)
            }
        )
    }
}
