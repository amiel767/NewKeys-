package com.aetherkeys.sheets.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aetherkeys.sheets.data.SheetsRepository
import com.aetherkeys.sheets.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LibraryFilter {
    object All : LibraryFilter
    object Favorites : LibraryFilter
    data class OfSetlist(val id: String) : LibraryFilter
}

data class LibraryUiState(
    val sheets: List<Sheet> = emptyList(),
    val library: LibraryData = LibraryData(),
    val query: String = "",
    val filter: LibraryFilter = LibraryFilter.All,
    val floatingId: String? = null,
    val loading: Boolean = true,
) {
    val settings: SheetsSettings get() = library.settings

    fun visibleSheets(): List<Sheet> {
        val q = query.trim()
        val list = if (q.isEmpty()) sheets else sheets.filter { it.title.contains(q, ignoreCase = true) }
        return when (val f = filter) {
            LibraryFilter.All -> list.sortedByDescending { it.updatedAt }
            LibraryFilter.Favorites -> list.filter { it.favorite }.sortedByDescending { it.updatedAt }
            is LibraryFilter.OfSetlist -> {
                val order = library.setlists.firstOrNull { it.id == f.id }?.sheetIds.orEmpty()
                order.mapNotNull { id -> list.firstOrNull { it.id == id } }
            }
        }
    }

    fun floatingSheet(): Sheet? = sheets.firstOrNull { it.id == floatingId }
}

interface HomeActions {
    fun setQuery(q: String)
    fun setFilter(f: LibraryFilter)
    fun createSheet(onCreated: (String) -> Unit)
    fun toggleFavorite(id: String)
    fun duplicate(id: String)
    fun delete(id: String)
    fun createSetlist(name: String)
    fun addToSetlist(sheetId: String, setlistId: String)
    fun removeFromSetlist(sheetId: String, setlistId: String)
    fun moveInSetlist(setlistId: String, sheetId: String, delta: Int)
    fun openFloating(id: String)
    fun closeFloating()
    fun setNotation(n: Notation)
}

interface SettingsActions {
    fun update(f: (SheetsSettings) -> SheetsSettings)
}

class SheetsLibraryViewModel(private val repo: SheetsRepository) : ViewModel(), HomeActions, SettingsActions {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val sheets = repo.listSheets()
            val lib = repo.loadLibrary()
            _state.value = _state.value.copy(sheets = sheets, library = lib, loading = false)
        }
    }

    private fun saveLibrary(lib: LibraryData) {
        _state.value = _state.value.copy(library = lib)
        viewModelScope.launch { repo.saveLibrary(lib) }
    }

    private fun putSheet(s: Sheet) {
        val list = _state.value.sheets
        _state.value = _state.value.copy(sheets = if (list.any { it.id == s.id }) list.map { if (it.id == s.id) s else it } else list + s)
        viewModelScope.launch { repo.save(s) }
    }

    override fun setQuery(q: String) { _state.value = _state.value.copy(query = q) }
    override fun setFilter(f: LibraryFilter) { _state.value = _state.value.copy(filter = f) }
    override fun openFloating(id: String) { _state.value = _state.value.copy(floatingId = id) }
    override fun closeFloating() { _state.value = _state.value.copy(floatingId = null) }

    override fun createSheet(onCreated: (String) -> Unit) {
        val s = _state.value.settings
        val color = PalettePresets.Presets.random()
        val sheet = Sheet(
            keyPc = s.defaultKeyPc, colorArgb = color,
            chordFontId = s.defaultChordFontId, lyricsFontId = s.defaultLyricsFontId,
            pagination = s.defaultPagination,
            sections = listOf(Section(kind = SectionKind.Intro, name = "Intro", bars = List(4) { Bar() })),
        )
        _state.value = _state.value.copy(sheets = _state.value.sheets + sheet)
        viewModelScope.launch { repo.save(sheet); onCreated(sheet.id) }
    }

    override fun toggleFavorite(id: String) {
        _state.value.sheets.firstOrNull { it.id == id }?.let { putSheet(it.copy(favorite = !it.favorite, updatedAt = System.currentTimeMillis())) }
    }

    override fun duplicate(id: String) {
        _state.value.sheets.firstOrNull { it.id == id }?.let {
            val now = System.currentTimeMillis()
            putSheet(it.copy(id = newId(), title = it.title + " copy", favorite = false, createdAt = now, updatedAt = now))
        }
    }

    override fun delete(id: String) {
        val st = _state.value
        val lib = st.library.copy(setlists = st.library.setlists.map { it.copy(sheetIds = it.sheetIds - id) })
        _state.value = st.copy(sheets = st.sheets.filter { it.id != id }, library = lib, floatingId = if (st.floatingId == id) null else st.floatingId)
        viewModelScope.launch { repo.delete(id); repo.saveLibrary(lib) }
    }

    override fun createSetlist(name: String) {
        val n = name.trim().ifEmpty { return }
        val lib = _state.value.library
        saveLibrary(lib.copy(setlists = lib.setlists + Setlist(name = n)))
    }

    private fun mapSetlist(id: String, f: (Setlist) -> Setlist) {
        val lib = _state.value.library
        saveLibrary(lib.copy(setlists = lib.setlists.map { if (it.id == id) f(it) else it }))
    }

    override fun addToSetlist(sheetId: String, setlistId: String) = mapSetlist(setlistId) { if (sheetId in it.sheetIds) it else it.copy(sheetIds = it.sheetIds + sheetId) }
    override fun removeFromSetlist(sheetId: String, setlistId: String) = mapSetlist(setlistId) { it.copy(sheetIds = it.sheetIds - sheetId) }

    override fun moveInSetlist(setlistId: String, sheetId: String, delta: Int) = mapSetlist(setlistId) { sl ->
        val i = sl.sheetIds.indexOf(sheetId); val j = i + delta
        if (i < 0 || j !in sl.sheetIds.indices) sl else sl.copy(sheetIds = sl.sheetIds.toMutableList().apply { add(j, removeAt(i)) })
    }

    override fun setNotation(n: Notation) = update { it.copy(notation = n) }

    override fun update(f: (SheetsSettings) -> SheetsSettings) {
        val lib = _state.value.library
        saveLibrary(lib.copy(settings = f(lib.settings)))
    }
}
