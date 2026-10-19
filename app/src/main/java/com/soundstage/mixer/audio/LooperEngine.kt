package com.soundstage.mixer.audio

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Looper event-only, 8 cases, 4 couches par case. Synchronisé sur TransportClock.
 * Aucun son joué ici — les events sont exposés via StateFlow ; c'est le consumer
 * (DrumPadScreen via ViewModel) qui les rejoue en audio.
 *
 * Supporte : length Auto / 1 / 2 / 4 / 8 bars, quantize 1/16 ou Free,
 * swing 0-100 % appliqué aux pas impairs du cycle, count-in au prochain début
 * de mesure.
 */
class LooperEngine(private val clock: TransportClock) {

    enum class CaseState { Empty, Queued, Recording, Playing, Overdubbing, Stopped }

    data class RecordedEvent(
        val kind: String,       // "pad" ou "note"
        val idx: Int,           // padId ou index de note 0-11
        val color: String,      // couleur UI au moment de l'enregistrement
        val beatOffset: Double, // position dans le cycle en beats (0..bars*beatsPerBar)
    )

    data class Case(
        val id: Int,
        val state: CaseState = CaseState.Empty,
        val bars: Int = 1,
        val lengthMode: String = "auto",  // "auto" | "1" | "2" | "4" | "8"
        val layers: List<List<RecordedEvent>> = emptyList(),
        val pendingRecEvents: List<RecordedEvent> = emptyList(),
        val pendingOverdubEvents: List<RecordedEvent> = emptyList(),
        val queuedAction: String? = null, // "rec" | "overdub" | "play"
        val queuedAtNs: Long = 0L,
        val recStartNs: Long = 0L,
        val startAtNs: Long = 0L,
        val lastBeatIndex: Double = -1.0,
        val offset: Int = 0,
        val chokeGroup: Int = 0,
    )

    data class UiState(
        val cases: List<Case> = List(8) { Case(id = it) },
        val quantize: Boolean = true,
        val swing: Int = 54,
        val lengthMode: String = "auto",
        val isPlayingAny: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // B5a: playback callbacks (invoked from runScheduler)
    var onPlayPadEvent: ((padIndex: Int) -> Unit)? = null
    var onPlayNoteEvent: ((noteIndex: Int) -> Unit)? = null

    // B5a: dernier beat joue par case, hors UiState (le scheduler tourne a 15 ms).
    private val playCursor = HashMap<Int, Double>()

    private var schedulerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start() {
        if (schedulerJob?.isActive == true) return
        schedulerJob = scope.launch { runScheduler() }
    }

    fun stop() {
        schedulerJob?.cancel()
        schedulerJob = null
    }

    fun release() {
        stop()
        scope.cancel()
    }

    fun setQuantize(q: Boolean) = _state.update { it.copy(quantize = q) }
    fun setSwing(s: Int) = _state.update { it.copy(swing = s.coerceIn(0, 100)) }
    fun setLengthMode(m: String) = _state.update { it.copy(lengthMode = m) }

    /** Tap sur une case (empty → queued rec ; rec → finish ; play → queued overdub ; etc.). */
    fun tapCase(caseIndex: Int) {
        val nowNs = System.nanoTime()
        val s = _state.value
        val busy = s.cases.any { it.id != caseIndex && (it.state == CaseState.Recording || (it.state == CaseState.Queued && it.queuedAction == "rec")) }
        val c = s.cases[caseIndex]
        val next = when (c.state) {
            CaseState.Empty -> {
                if (busy) return
                val nextBarNs = nowNs + clock.timeToNextBar(nowNs)
                c.copy(state = CaseState.Queued, queuedAction = "rec", queuedAtNs = nextBarNs, lengthMode = s.lengthMode)
            }
            CaseState.Queued -> c.copy(state = CaseState.Empty, queuedAction = null)
            CaseState.Recording -> finishRecording(c, nowNs)
            CaseState.Playing -> {
                val loopDur = clock.barDurationNs() * c.bars
                val sinceStart = (nowNs - c.startAtNs).coerceAtLeast(0L)
                val nextBoundary = c.startAtNs + ((sinceStart / loopDur) + 1) * loopDur
                c.copy(state = CaseState.Queued, queuedAction = "overdub", queuedAtNs = nextBoundary)
            }
            CaseState.Overdubbing -> commitOverdub(c)
            CaseState.Stopped -> c.copy(state = CaseState.Queued, queuedAction = "play", queuedAtNs = nowNs + clock.timeToNextBar(nowNs))
        }
        _state.update { st -> st.copy(cases = st.cases.toMutableList().also { it[caseIndex] = next }) }
    }

    fun longPressCaseAction(caseIndex: Int, action: String) {
        // Clear all wipes every case, so it bypasses the per-case replace below.
        if (action == "clearAll") {
            _state.update { st -> st.copy(cases = st.cases.map { Case(id = it.id) }) }
            return
        }
        val c = _state.value.cases[caseIndex]
        val next = when (action) {
            "stop" -> c.copy(state = CaseState.Stopped)
            "play" -> c.copy(state = CaseState.Queued, queuedAction = "play", queuedAtNs = System.nanoTime() + clock.timeToNextBar())
            "clear" -> Case(id = caseIndex)
            "undo" -> if (c.layers.isNotEmpty()) {
                val rest = c.layers.dropLast(1)
                if (rest.isEmpty()) c.copy(state = CaseState.Empty, layers = emptyList()) else c.copy(layers = rest)
            } else c
            "double" -> if (c.bars < 8 && c.layers.isNotEmpty()) c.copy(bars = c.bars * 2, layers = c.layers.map { l -> l + l.map { e -> e.copy(beatOffset = e.beatOffset + c.bars * clock.signature.beatsPerBar) } }) else c
            else -> c
        }
        _state.update { st -> st.copy(cases = st.cases.toMutableList().also { it[caseIndex] = next }) }
    }

    fun stopAll() {
        _state.update { st -> st.copy(cases = st.cases.map { if (it.state == CaseState.Playing || it.state == CaseState.Overdubbing) it.copy(state = CaseState.Stopped) else it }) }
    }

    fun playAll() {
        val now = System.nanoTime()
        val nextBar = now + clock.timeToNextBar(now)
        _state.update { st -> st.copy(cases = st.cases.map { if (it.state == CaseState.Stopped) it.copy(state = CaseState.Queued, queuedAction = "play", queuedAtNs = nextBar) else it }) }
    }

    /** Appelé par le consumer quand un pad ou une note est joué pendant un REC ou OVERDUB. */
    fun record(kind: String, idx: Int, color: String) {
        val nowNs = System.nanoTime()
        _state.update { s ->
            val updated = s.cases.map { c ->
                when (c.state) {
                    CaseState.Recording -> {
                        val beatsPerBar = clock.signature.beatsPerBar
                        val elapsedNs = (nowNs - c.recStartNs).coerceAtLeast(0L)
                        val beatOffset = elapsedNs.toDouble() / clock.beatDurationNs().toDouble()
                        val quantized = if (s.quantize) {
                            val subdiv = clock.subdivision.subdivPerBeat.toDouble()
                            (Math.round(beatOffset * subdiv) / subdiv)
                        } else beatOffset
                        c.copy(pendingRecEvents = c.pendingRecEvents + RecordedEvent(kind, idx, color, quantized))
                    }
                    CaseState.Overdubbing -> {
                        val beatsPerBar = clock.signature.beatsPerBar
                        val loopBeats = c.bars * beatsPerBar
                        val elapsedNs = (nowNs - c.startAtNs).coerceAtLeast(0L)
                        val cycleNs = clock.barDurationNs() * c.bars
                        val offsetInCycleNs = elapsedNs % cycleNs
                        var beatOffset = offsetInCycleNs.toDouble() / clock.beatDurationNs().toDouble()
                        beatOffset %= loopBeats
                        val quantized = if (s.quantize) {
                            val subdiv = clock.subdivision.subdivPerBeat.toDouble()
                            (Math.round(beatOffset * subdiv) / subdiv)
                        } else beatOffset
                        c.copy(pendingOverdubEvents = c.pendingOverdubEvents + RecordedEvent(kind, idx, color, quantized))
                    }
                    else -> c
                }
            }
            s.copy(cases = updated)
        }
    }

    private fun finishRecording(c: Case, nowNs: Long): Case {
        val elapsedNs = nowNs - c.recStartNs
        val bars = if (c.lengthMode == "auto") {
            (Math.round(elapsedNs.toDouble() / clock.barDurationNs().toDouble())).toInt().coerceAtLeast(1)
        } else c.lengthMode.toIntOrNull() ?: 1
        val layer = c.pendingRecEvents.map { e ->
            val loopBeats = bars * clock.signature.beatsPerBar
            e.copy(beatOffset = e.beatOffset % loopBeats)
        }
        val nextStartNs = c.recStartNs + bars * clock.barDurationNs()
        return c.copy(
            state = CaseState.Playing,
            bars = bars,
            layers = listOf(layer),
            pendingRecEvents = emptyList(),
            startAtNs = nextStartNs,
            queuedAction = null,
        )
    }

    private fun commitOverdub(c: Case): Case {
        if (c.layers.size >= 4) return c.copy(state = CaseState.Playing, pendingOverdubEvents = emptyList(), queuedAction = null)
        val loopBeats = c.bars * clock.signature.beatsPerBar
        val layer = c.pendingOverdubEvents.map { e -> e.copy(beatOffset = e.beatOffset % loopBeats) }
        return c.copy(
            state = CaseState.Playing,
            layers = c.layers + listOf(layer),
            pendingOverdubEvents = emptyList(),
            queuedAction = null,
        )
    }

    private suspend fun runScheduler() {
        while (schedulerJob?.isActive == true) {
            val nowNs = System.nanoTime()
            var changed = false
            val updated = _state.value.cases.map { c ->
                when (c.state) {
                    CaseState.Queued -> if (nowNs >= c.queuedAtNs) {
                        changed = true
                        when (c.queuedAction) {
                            "rec" -> c.copy(state = CaseState.Recording, recStartNs = c.queuedAtNs, pendingRecEvents = emptyList(), queuedAction = null)
                            "overdub" -> c.copy(state = CaseState.Overdubbing, startAtNs = c.queuedAtNs, pendingOverdubEvents = emptyList(), queuedAction = null)
                            "play" -> {
                                playCursor.remove(c.id)
                                c.copy(state = CaseState.Playing, startAtNs = c.queuedAtNs, queuedAction = null, lastBeatIndex = -1.0)
                            }
                            else -> c
                        }
                    } else c
                    CaseState.Recording -> {
                        if (c.lengthMode != "auto") {
                            val targetBars = c.lengthMode.toIntOrNull() ?: 1
                            val endNs = c.recStartNs + targetBars * clock.barDurationNs()
                            if (nowNs >= endNs) { changed = true; finishRecording(c, endNs) } else c
                        } else c
                    }
                    CaseState.Overdubbing -> {
                        val endNs = c.startAtNs + c.bars * clock.barDurationNs()
                        if (nowNs >= endNs) { changed = true; commitOverdub(c) } else c
                    }
                    CaseState.Playing -> {
                        val loopBeats = (c.bars * clock.signature.beatsPerBar).toDouble()
                        if (loopBeats <= 0.0) c else {
                            // B5a: trigger playback callbacks on sub-boundary.
                            // The continuous cursor lives in `playCursor` (outside UiState)
                            // so the state flow is not re-emitted on every 15 ms tick.
                            // lastBeatIndex only records the last position that fired.
                            val __b5aNewBeat =
                                (((nowNs - c.startAtNs).toDouble() / clock.beatDurationNs().toDouble())
                                    % loopBeats)
                            val __b5aOldBeat = playCursor[c.id] ?: -1.0
                            val __b5aWrapped = __b5aOldBeat >= 0.0 && __b5aNewBeat < __b5aOldBeat
                            var __b5aFired = false
                            for (__b5aLayer in c.layers) {
                                for (__b5aEvent in __b5aLayer) {
                                    val __b5aDue = if (__b5aWrapped) {
                                        __b5aEvent.beatOffset > __b5aOldBeat ||
                                            __b5aEvent.beatOffset <= __b5aNewBeat
                                    } else {
                                        __b5aEvent.beatOffset > __b5aOldBeat &&
                                            __b5aEvent.beatOffset <= __b5aNewBeat
                                    }
                                    if (__b5aDue) {
                                        __b5aFired = true
                                        when (__b5aEvent.kind) {
                                            "pad" -> onPlayPadEvent?.invoke(__b5aEvent.idx)
                                            "note" -> onPlayNoteEvent?.invoke(__b5aEvent.idx)
                                        }
                                    }
                                }
                            }
                            playCursor[c.id] = __b5aNewBeat
                            if (__b5aFired) { changed = true; c.copy(lastBeatIndex = __b5aNewBeat) } else c
                        }
                    }
                    else -> c
                }
            }
            if (changed) _state.update { it.copy(cases = updated) }
            delay(15L)
        }
    }
}
