package com.aetherkeys.sheets.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aetherkeys.sheets.data.SheetsRepository
import com.aetherkeys.sheets.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditorUiState(
    val sheet: Sheet? = null,
    val sectionId: String? = null,
    val barId: String? = null,
    val slot: Int = 0,
    val pendingBass: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val saved: Boolean = true,
) {
    val section: Section? get() = sheet?.sections?.firstOrNull { it.id == sectionId }
    val bar: Bar? get() = section?.bars?.firstOrNull { it.id == barId }
    val chord: Chord? get() = bar?.chords?.getOrNull(slot)
}

class SheetEditorViewModel(private val repo: SheetsRepository) : ViewModel(), EditorActions {
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var history: UndoHistory<Sheet>? = null
    private var saveJob: Job? = null

    fun load(id: String) {
        if (_state.value.sheet?.id == id) return
        viewModelScope.launch {
            val s = repo.load(id) ?: return@launch
            val h = SessionHistories.forSheet(id)
            history = h
            val sec = s.sections.firstOrNull()
            _state.value = EditorUiState(
                sheet = s, sectionId = sec?.id, barId = sec?.bars?.firstOrNull()?.id,
                canUndo = h.canUndo, canRedo = h.canRedo,
            )
        }
    }

    // ---------- core ----------
    private fun edit(group: String? = null, transform: (Sheet) -> Sheet) {
        val st = _state.value
        val before = st.sheet ?: return
        val h = history ?: return
        val after = transform(before)
        if (after == before) return
        val now = System.currentTimeMillis()
        val stamped = after.copy(updatedAt = now)
        h.record(before, group, now)
        _state.value = _state.value.copy(sheet = stamped, canUndo = h.canUndo, canRedo = h.canRedo, saved = false)
        scheduleSave(stamped)
    }

    private fun scheduleSave(sheet: Sheet) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            repo.save(sheet)
            val cur = _state.value
            if (cur.sheet?.id == sheet.id && cur.sheet.updatedAt == sheet.updatedAt) _state.value = cur.copy(saved = true)
        }
    }

    override fun onCleared() {
        val st = _state.value
        val s = st.sheet
        if (s != null && !st.saved) CoroutineScope(Dispatchers.IO).launch { repo.save(s) }
        super.onCleared()
    }

    private fun clampSelection(sheet: Sheet, st: EditorUiState): EditorUiState {
        val sec = sheet.sections.firstOrNull { it.id == st.sectionId } ?: sheet.sections.firstOrNull()
        val bar = sec?.bars?.firstOrNull { it.id == st.barId } ?: sec?.bars?.firstOrNull()
        return st.copy(sectionId = sec?.id, barId = bar?.id, slot = if (bar != null) st.slot.coerceAtMost(bar.slotCount() - 1) else 0)
    }

    // ---------- undo / redo ----------
    override fun undo() {
        val st = _state.value; val cur = st.sheet ?: return; val h = history ?: return
        val prev = h.undo(cur) ?: return
        val next = clampSelection(prev, st.copy(sheet = prev, canUndo = h.canUndo, canRedo = h.canRedo, saved = false, pendingBass = false))
        _state.value = next
        scheduleSave(prev)
    }

    override fun redo() {
        val st = _state.value; val cur = st.sheet ?: return; val h = history ?: return
        val nxt = h.redo(cur) ?: return
        _state.value = clampSelection(nxt, st.copy(sheet = nxt, canUndo = h.canUndo, canRedo = h.canRedo, saved = false, pendingBass = false))
        scheduleSave(nxt)
    }

    // ---------- selection ----------
    override fun selectSection(id: String) {
        val sh = _state.value.sheet ?: return
        val sec = sh.sections.firstOrNull { it.id == id } ?: return
        _state.value = _state.value.copy(sectionId = id, barId = sec.bars.firstOrNull()?.id, slot = 0, pendingBass = false)
    }

    override fun selectBar(sectionId: String, barId: String) {
        _state.value = _state.value.copy(sectionId = sectionId, barId = barId, slot = 0, pendingBass = false)
    }

    override fun selectSlot(slot: Int) {
        val bar = _state.value.bar ?: return
        _state.value = _state.value.copy(slot = slot.coerceIn(0, bar.slotCount() - 1), pendingBass = false)
    }

    private fun moveSelection(delta: Int) {
        val st = _state.value; val sh = st.sheet ?: return
        val flat = sh.sections.flatMap { s -> s.bars.map { s.id to it.id } }
        if (flat.isEmpty()) return
        val i = flat.indexOfFirst { it.first == st.sectionId && it.second == st.barId }
        val n = (i + delta).coerceIn(0, flat.lastIndex)
        _state.value = st.copy(sectionId = flat[n].first, barId = flat[n].second, slot = 0, pendingBass = false)
    }

    override fun nextBar() = moveSelection(1)
    override fun prevBar() = moveSelection(-1)

    // ---------- chord entry ----------
    private fun onSelectedBar(group: String? = null, f: (Bar) -> Bar) {
        val st = _state.value; val sid = st.sectionId ?: return; val bid = st.barId ?: return
        edit(group) { it.mapBar(sid, bid, f) }
    }

    private fun onSelectedChord(group: String? = null, f: (Chord) -> Chord) {
        val slot = _state.value.slot
        onSelectedBar(group) { bar ->
            val c = bar.chords.getOrNull(slot)
            if (c == null) bar else bar.setChord(slot, f(c))
        }
    }

    override fun typeDegree(n: Int) {
        val st = _state.value; val sid = st.sectionId ?: return; val bid = st.barId ?: return
        val slot = st.slot; val bass = st.pendingBass
        edit("deg:$bid") { sh ->
            val sec = sh.sections.firstOrNull { it.id == sid } ?: return@edit sh
            val root = Theory.degreeToRoot(n, sh.keyFor(sec))
            sh.mapBar(sid, bid) { bar ->
                if (bass) bar.setChord(slot, bar.chords.getOrNull(slot)?.copy(bass = root))
                else bar.setChord(slot, Chord(root)).copy(noChord = false, repeatPrevious = false)
            }
        }
        if (bass) _state.value = _state.value.copy(pendingBass = false)
    }

    override fun toggleQuality(q: Quality) = onSelectedChord { c -> c.copy(quality = if (c.quality == q) Quality.Major else q) }

    override fun toggleExt(e: Ext) = onSelectedChord { c ->
        val s = c.exts.toMutableSet()
        if (!s.remove(e)) {
            s.add(e)
            if (e == Ext.Seven) s.remove(Ext.Maj7)
            if (e == Ext.Maj7) s.remove(Ext.Seven)
        }
        c.copy(exts = s)
    }

    override fun alter(delta: Int) = onSelectedChord { c -> c.copy(root = (c.root + delta + 12) % 12) }

    override fun startBass() {
        if (_state.value.chord == null) return
        _state.value = _state.value.copy(pendingBass = !_state.value.pendingBass)
    }

    override fun toggleNoChord() = onSelectedBar { it.copy(noChord = !it.noChord, repeatPrevious = false) }
    override fun toggleRepeatPrevious() = onSelectedBar { it.copy(repeatPrevious = !it.repeatPrevious, noChord = false) }
    override fun clearBar() = onSelectedBar { Bar(id = it.id) }

    override fun setSplit(s: Split) {
        onSelectedBar { it.withSplit(s) }
        val bar = _state.value.bar
        if (bar != null) _state.value = _state.value.copy(slot = _state.value.slot.coerceAtMost(bar.slotCount() - 1))
    }

    // ---------- bars ----------
    override fun duplicateBar() {
        val st = _state.value; val sid = st.sectionId ?: return; val bid = st.barId ?: return
        var created: String? = null
        edit { sh ->
            sh.mapSection(sid) { s ->
                val i = s.bars.indexOfFirst { it.id == bid }
                if (i < 0) s else {
                    val copy = s.bars[i].copy(id = newId())
                    created = copy.id
                    s.copy(bars = s.bars.toMutableList().apply { add(i + 1, copy) })
                }
            }
        }
        created?.let { _state.value = _state.value.copy(barId = it) }
    }

    override fun copyLine() {
        val st = _state.value; val sid = st.sectionId ?: return; val bid = st.barId ?: return
        edit { sh ->
            sh.mapSection(sid) { s ->
                val i = s.bars.indexOfFirst { it.id == bid }
                if (i < 0) s else {
                    val bpl = (s.barsPerLine ?: sh.barsPerLine).coerceAtLeast(1)
                    val start = (i / bpl) * bpl
                    val end = minOf(start + bpl, s.bars.size)
                    val copies = s.bars.subList(start, end).map { it.copy(id = newId()) }
                    s.copy(bars = s.bars.toMutableList().apply { addAll(end, copies) })
                }
            }
        }
    }

    override fun deleteBar() {
        val st = _state.value; val sid = st.sectionId ?: return; val bid = st.barId ?: return
        var next: String? = null
        edit { sh ->
            sh.mapSection(sid) { s ->
                if (s.bars.size <= 1) s else {
                    val i = s.bars.indexOfFirst { it.id == bid }
                    val bars = s.bars.filter { it.id != bid }
                    next = bars.getOrNull(minOf(i, bars.lastIndex))?.id
                    s.copy(bars = bars)
                }
            }
        }
        next?.let { _state.value = _state.value.copy(barId = it, slot = 0) }
    }

    override fun addBar() {
        val st = _state.value; val sid = st.sectionId ?: return
        var created: String? = null
        edit { sh ->
            sh.mapSection(sid) { s ->
                val nb = Bar()
                created = nb.id
                val i = s.bars.indexOfFirst { it.id == st.barId }
                s.copy(bars = s.bars.toMutableList().apply { add(if (i < 0) size else i + 1, nb) })
            }
        }
        created?.let { _state.value = _state.value.copy(barId = it, slot = 0) }
    }

    // ---------- key ----------
    override fun transpose(semitones: Int) = edit { it.transposed(semitones) }

    override fun setSongKey(pc: Int) {
        val cur = _state.value.sheet ?: return
        var d = ((pc - cur.keyPc) % 12 + 12) % 12
        if (d > 6) d -= 12
        if (d != 0) transpose(d)
    }

    override fun setSectionKey(sectionId: String, pc: Int?) = edit { it.mapSection(sectionId) { s -> s.copy(keyPc = pc) } }

    // ---------- symbols ----------
    override fun toggleMark(m: Mark) = onSelectedBar { it.copy(marks = if (m in it.marks) it.marks - m else it.marks + m) }
    override fun toggleRepeatStart() = onSelectedBar { it.copy(repeatStart = !it.repeatStart) }
    override fun toggleRepeatEnd() = onSelectedBar { it.copy(repeatEnd = !it.repeatEnd) }
    override fun setRepeatCount(n: Int) = onSelectedBar { it.copy(repeatCount = n, repeatEnd = true) }
    override fun toggleVolta(n: Int) = onSelectedBar { it.copy(volta = if (it.volta == n) null else n) }

    // ---------- sections ----------
    override fun addSection(kind: SectionKind) {
        val st = _state.value
        val sec = Section(kind = kind, name = kind.label(), bars = List(4) { Bar() })
        edit { sh ->
            val i = sh.sections.indexOfFirst { it.id == st.sectionId }
            sh.copy(sections = sh.sections.toMutableList().apply { add(if (i < 0) size else i + 1, sec) })
        }
        _state.value = _state.value.copy(sectionId = sec.id, barId = sec.bars.first().id, slot = 0)
    }

    override fun renameSection(id: String, name: String) = edit("rename:$id") { it.mapSection(id) { s -> s.copy(name = name) } }

    override fun deleteSection(id: String) {
        edit { sh -> if (sh.sections.size <= 1) sh else sh.copy(sections = sh.sections.filter { it.id != id }) }
        _state.value.sheet?.let { _state.value = clampSelection(it, _state.value) }
    }

    override fun moveSection(id: String, dir: Int) = edit { sh ->
        val i = sh.sections.indexOfFirst { it.id == id }
        val j = i + dir
        if (i < 0 || j !in sh.sections.indices) sh
        else sh.copy(sections = sh.sections.toMutableList().apply { add(j, removeAt(i)) })
    }

    // ---------- text / meta ----------
    override fun setLyric(barId: String, text: String) {
        val sid = _state.value.sectionId ?: return
        edit("lyric:$barId") { it.mapBar(sid, barId) { b -> b.copy(lyric = text) } }
    }

    override fun setTitle(title: String) = edit("title") { it.copy(title = title) }
    override fun setBpm(bpm: Int) = edit("bpm") { it.copy(bpm = bpm.coerceIn(30, 300)) }
    override fun setTimeSig(beats: Int, unit: Int) = edit { it.copy(beatsPerBar = beats, beatUnit = unit) }

    // ---------- style ----------
    private fun scoped(scope: StyleScope, onSheet: (Sheet) -> Sheet, onSection: (Section) -> Section) {
        val sid = _state.value.sectionId
        edit { sh -> if (scope == StyleScope.Sheet || sid == null) onSheet(sh) else sh.mapSection(sid, onSection) }
    }

    override fun setChordFont(id: String, scope: StyleScope) = scoped(scope, { it.copy(chordFontId = id) }, { it.copy(chordFontId = id) })
    override fun setLyricsFont(id: String, scope: StyleScope) = scoped(scope, { it.copy(lyricsFontId = id) }, { it.copy(lyricsFontId = id) })
    override fun setChordSize(sp: Float, scope: StyleScope) = scoped(scope, { it.copy(chordSizeSp = sp) }, { it.copy(chordSizeSp = sp) })
    override fun setBarsPerLine(n: Int, scope: StyleScope) = scoped(scope, { it.copy(barsPerLine = n) }, { it.copy(barsPerLine = n) })
    override fun setColor(argb: Long, scope: StyleScope) = scoped(scope, { it.copy(colorArgb = argb) }, { it.copy(colorArgb = argb) })
    override fun setPagination(p: Pagination) = edit { it.copy(pagination = p) }
}
