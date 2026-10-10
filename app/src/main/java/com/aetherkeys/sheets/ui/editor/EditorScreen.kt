package com.aetherkeys.sheets.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aetherkeys.sheets.model.*
import com.aetherkeys.sheets.ui.chart.ChartLayout
import com.aetherkeys.sheets.ui.chart.ChartLines
import com.aetherkeys.sheets.ui.chart.LyricsView
import com.aetherkeys.sheets.ui.components.*
import com.aetherkeys.sheets.ui.theme.*
import com.aetherkeys.sheets.vm.EditorActions
import com.aetherkeys.sheets.vm.EditorUiState
import com.aetherkeys.sheets.vm.StyleScope

private enum class EditorTab(val label: String) { Chords("Chords"), Symbols("Symbols"), Text("Text"), Page("Page") }

/**
 * Éditeur : ruban à gauche (40 %), page à droite (60 %). Plein écran, sans cadres.
 * Ombre légère sur le bord du ruban. Libellés en anglais.
 */
@Composable
fun EditorScreen(
    state: EditorUiState,
    settings: SheetsSettings,
    actions: EditorActions,
    onBack: () -> Unit,
    onOpenScene: () -> Unit,
    onSetNotation: (Notation) -> Unit,
    onExport: (ExportFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheet = state.sheet
    if (sheet == null) {
        Box(modifier.fillMaxSize().background(SheetsColors.Background))
        return
    }
    var tab by rememberSaveable { mutableStateOf(EditorTab.Chords.name) }
    var lyricsView by rememberSaveable { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val current = EditorTab.valueOf(tab)

    Row(modifier.fillMaxSize().background(SheetsColors.Background)) {
        // ---------------- Ribbon (40 %) ----------------
        Column(
            Modifier.weight(SheetsLayout.RibbonWeight).fillMaxHeight().zIndex(1f)
                .shadow(14.dp).background(SheetsColors.Ribbon).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlyphButton("←", onBack)
                ToolChip("Key ${Theory.KEY_NAMES[sheet.keyPc]}", { tab = EditorTab.Page.name; open = "display" })
                Row(Modifier.clip(RoundedCornerShape(SheetsRadius.Button)).background(SheetsColors.Surface).padding(3.dp)) {
                    Pill("Chart", selected = !lyricsView) { lyricsView = false }
                    Pill("Lyrics", selected = lyricsView) { lyricsView = true }
                }
                Box(Modifier.weight(1f))
                GlyphButton("↶", actions::undo, enabled = state.canUndo)
                GlyphButton("↷", actions::redo, enabled = state.canRedo)
                GlyphButton("▶", onOpenScene)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                EditorTab.values().forEach { t ->
                    val on = t == current
                    Text(
                        t.label, style = SheetsType.Label.copy(fontSize = 14.sp), color = if (on) SheetsColors.Accent else SheetsColors.TextMuted,
                        modifier = Modifier.clickable { tab = t.name; open = null }
                            .then(if (on) Modifier.background(Color.Transparent) else Modifier).padding(bottom = 6.dp),
                    )
                }
                Box(Modifier.weight(1f))
                Text(if (state.saved) "Saved" else "Saving…", style = SheetsType.Caption, color = SheetsColors.TextMuted)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(SheetsColors.SurfaceChip))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (current) {
                    EditorTab.Chords -> ChordsTab(state, sheet, settings, actions, open) { open = it }
                    EditorTab.Symbols -> SymbolsTab(actions, state, open) { open = it }
                    EditorTab.Text -> TextTab(state, sheet, actions, open) { open = it }
                    EditorTab.Page -> PageTab(state, sheet, settings, actions, onSetNotation, onExport, open) { open = it }
                }
            }
        }

        // ---------------- Page (60 %) ----------------
        val base = sheet.colorArgb.toColor()
        val ink = onColorFor(base)
        BoxWithConstraints(Modifier.weight(SheetsLayout.PageWeight).fillMaxHeight().background(base)) {
            val density = LocalDensity.current
            val textMeasurer = rememberTextMeasurer()
            val pagePad = SheetsSpacing.Page
            val widthPx = with(density) { (maxWidth - pagePad * 2 - 78.dp).toPx() }
            val plans = remember(sheet, settings, widthPx) {
                ChartLayout.plan(
                    sheet, settings, widthPx, Float.MAX_VALUE, with(density) { 1.sp.toPx() }, with(density) { 16.dp.toPx() },
                    SheetsLayout.ChordMinSizeSp,
                ) { text, fontId -> textMeasurer.measure(text, TextStyle(fontFamily = SheetsFonts.family(fontId), fontSize = 100.sp), maxLines = 1, softWrap = false).size.width.toFloat() }
            }
            if (lyricsView) {
                LyricsView(sheet, state.barId, ink)
            } else {
                val shown = if (sheet.pagination == Pagination.PerSection) plans.filter { sheet.sections[it.sectionIndex].id == state.sectionId } else plans
                val family = SheetsFonts.family(sheet.chordFontId)
                Column(
                    Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            var acc = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { acc = 0f },
                                onDragEnd = { if (acc < -60.dp.toPx()) actions.nextBar() else if (acc > 60.dp.toPx()) actions.prevBar() },
                            ) { _, d -> acc += d }
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = pagePad, vertical = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    BasicTextField(
                        value = sheet.title, onValueChange = actions::setTitle, singleLine = true,
                        textStyle = SheetsType.chord(family, 44f).copy(color = ink), cursorBrush = SolidColor(ink),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Key ${Theory.KEY_NAMES[sheet.keyPc]} · ${sheet.bpm} BPM · ${sheet.beatsPerBar}/${sheet.beatUnit}",
                        style = SheetsType.Caption.copy(fontSize = 13.sp), color = ink.copy(alpha = 0.65f),
                    )
                    if (sheet.pagination == Pagination.PerSection) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            sheet.sections.forEach { s ->
                                Box(Modifier.clickable { actions.selectSection(s.id) }) { SectionTagChip(s.name, s.kind, colorOverride = s.colorArgb?.toColor()) }
                            }
                        }
                    }
                    shown.forEach { plan ->
                        val sec = sheet.sections[plan.sectionIndex]
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.width(66.dp).padding(top = 10.dp).clickable { actions.selectSection(sec.id) }) {
                                SectionTagChip(sec.name, sec.kind, colorOverride = sec.colorArgb?.toColor())
                            }
                            ChartLines(
                                plan, sheet, settings, ink, selectedBarId = state.barId,
                                onBarClick = { s, b -> actions.selectBar(s.id, b.id) }, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================================
// Tabs
// =====================================================================================

private fun chordSummary(st: EditorUiState, sheet: Sheet, settings: SheetsSettings): String {
    val c = st.chord ?: return "—"
    val key = st.section?.let { sheet.keyFor(it) } ?: sheet.keyPc
    return Theory.format(c, key, settings.notation, settings.passing)
}

@Composable
private fun ChordsTab(st: EditorUiState, sheet: Sheet, settings: SheetsSettings, a: EditorActions, open: String?, setOpen: (String?) -> Unit) {
    val family = SheetsFonts.family(sheet.chordFontId)
    val chord = st.chord
    val bar = st.bar
    fun tg(id: String) = setOpen(if (open == id) null else id)
    val summary = chordSummary(st, sheet, settings)
    OverlineLabel("Degrees")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        (1..7).forEach { n -> DegreeKey(n, family, { a.typeDegree(n) }, Modifier.weight(1f)) }
    }
    Drawer("Extensions", open == "ext", { tg("ext") }, summary = summary) {
        ChipFlow {
            ToolChip("m", { a.toggleQuality(Quality.Minor) }, selected = chord?.quality == Quality.Minor)
            ToolChip("7", { a.toggleExt(Ext.Seven) }, selected = chord?.exts?.contains(Ext.Seven) == true)
            ToolChip("maj7", { a.toggleExt(Ext.Maj7) }, selected = chord?.exts?.contains(Ext.Maj7) == true)
            ToolChip("sus2", { a.toggleQuality(Quality.Sus2) }, selected = chord?.quality == Quality.Sus2)
            ToolChip("sus4", { a.toggleQuality(Quality.Sus4) }, selected = chord?.quality == Quality.Sus4)
            ToolChip("6", { a.toggleExt(Ext.Six) }, selected = chord?.exts?.contains(Ext.Six) == true)
            ToolChip("9", { a.toggleExt(Ext.Nine) }, selected = chord?.exts?.contains(Ext.Nine) == true)
            ToolChip("add9", { a.toggleExt(Ext.Add9) }, selected = chord?.exts?.contains(Ext.Add9) == true)
            ToolChip("dim", { a.toggleQuality(Quality.Dim) }, selected = chord?.quality == Quality.Dim)
            ToolChip("aug", { a.toggleQuality(Quality.Aug) }, selected = chord?.quality == Quality.Aug)
            ToolChip("5", { a.toggleQuality(Quality.Power5) }, selected = chord?.quality == Quality.Power5)
        }
    }
    Drawer("Alterations", open == "alt", { tg("alt") }, summary = "b · # · /bass") {
        ChipFlow {
            ToolChip("b", { a.alter(-1) })
            ToolChip("#", { a.alter(1) })
            ToolChip("/ bass", { a.startBass() }, selected = st.pendingBass)
            ToolChip("N.C.", { a.toggleNoChord() }, selected = bar?.noChord == true)
            ToolChip("%", { a.toggleRepeatPrevious() }, selected = bar?.repeatPrevious == true)
        }
    }
    Drawer("Passing chord", open == "pass", { tg("pass") }, summary = if (bar?.split == Split.Passing) "Passing" else bar?.split?.name, warn = bar?.split == Split.Passing) {
        ChipFlow {
            ToolChip("Single", { a.setSplit(Split.None) }, selected = bar?.split == Split.None)
            ToolChip("Split 2", { a.setSplit(Split.Two) }, selected = bar?.split == Split.Two)
            ToolChip("Split 4", { a.setSplit(Split.Four) }, selected = bar?.split == Split.Four)
            ToolChip("Passing", { a.setSplit(Split.Passing) }, selected = bar?.split == Split.Passing)
        }
        if ((bar?.slotCount() ?: 1) > 1) {
            OverlineLabel("Slot")
            ChipFlow { (0 until (bar?.slotCount() ?: 1)).forEach { i -> ToolChip("${i + 1}", { a.selectSlot(i) }, selected = st.slot == i) } }
        }
    }
    Drawer("Measure", open == "measure", { tg("measure") }, summary = "Duplicate · Transpose") {
        ChipFlow {
            ToolChip("Duplicate", { a.duplicateBar() })
            ToolChip("Copy line", { a.copyLine() })
            ToolChip("Add bar", { a.addBar() })
            ToolChip("Delete", { a.deleteBar() })
            ToolChip("Clear", { a.clearBar() })
        }
        ChipFlow {
            ToolChip("◀ Prev", { a.prevBar() })
            ToolChip("Next ▶", { a.nextBar() })
            ToolChip("Transpose −1", { a.transpose(-1) })
            ToolChip("Transpose +1", { a.transpose(1) })
        }
    }
}

@Composable
private fun SymbolsTab(a: EditorActions, st: EditorUiState, open: String?, setOpen: (String?) -> Unit) {
    val bar = st.bar
    fun tg(id: String) = setOpen(if (open == id) null else id)
    Drawer("Repeats & barlines", open == "rep", { tg("rep") }, summary = "Repeat · Volta") {
        ChipFlow {
            ToolChip("Repeat start", { a.toggleRepeatStart() }, selected = bar?.repeatStart == true)
            ToolChip("Repeat end", { a.toggleRepeatEnd() }, selected = bar?.repeatEnd == true)
            ToolChip("x2", { a.setRepeatCount(2) }, selected = bar?.repeatEnd == true && bar.repeatCount == 2)
            ToolChip("x3", { a.setRepeatCount(3) }, selected = bar?.repeatEnd == true && bar.repeatCount == 3)
            ToolChip("x4", { a.setRepeatCount(4) }, selected = bar?.repeatEnd == true && bar.repeatCount == 4)
            ToolChip("Volta 1", { a.toggleVolta(1) }, selected = bar?.volta == 1)
            ToolChip("Volta 2", { a.toggleVolta(2) }, selected = bar?.volta == 2)
        }
    }
    Drawer("Accents", open == "acc", { tg("acc") }, summary = "Stop · Fermata · Push") {
        ChipFlow {
            ToolChip("Stop ◇", { a.toggleMark(Mark.Stop) }, selected = bar?.marks?.contains(Mark.Stop) == true)
            ToolChip("Fermata ⌒", { a.toggleMark(Mark.Fermata) }, selected = bar?.marks?.contains(Mark.Fermata) == true)
            ToolChip("Push <", { a.toggleMark(Mark.Push) }, selected = bar?.marks?.contains(Mark.Push) == true)
            ToolChip("Accent >", { a.toggleMark(Mark.Accent) }, selected = bar?.marks?.contains(Mark.Accent) == true)
        }
    }
    Drawer("Navigation", open == "nav", { tg("nav") }, summary = "Segno · Coda") {
        ChipFlow {
            ToolChip("Segno §", { a.toggleMark(Mark.Segno) }, selected = bar?.marks?.contains(Mark.Segno) == true)
            ToolChip("Coda ⊕", { a.toggleMark(Mark.Coda) }, selected = bar?.marks?.contains(Mark.Coda) == true)
        }
    }
}

@Composable
private fun FieldBox(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, singleLine: Boolean = true) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SheetsColors.SurfaceChip).padding(12.dp)) {
        if (value.isEmpty()) Text(placeholder, style = SheetsType.Body, color = SheetsColors.TextMuted)
        BasicTextField(value, onChange, singleLine = singleLine, textStyle = SheetsType.Body.copy(color = SheetsColors.TextPrimary), cursorBrush = SolidColor(SheetsColors.Accent), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ScopeChips(scope: StyleScope, onChange: (StyleScope) -> Unit) {
    ChipFlow {
        ToolChip("Whole sheet", { onChange(StyleScope.Sheet) }, selected = scope == StyleScope.Sheet)
        ToolChip("This section", { onChange(StyleScope.Section) }, selected = scope == StyleScope.Section)
    }
}

@Composable
private fun TextTab(st: EditorUiState, sheet: Sheet, a: EditorActions, open: String?, setOpen: (String?) -> Unit) {
    var scope by rememberSaveable { mutableStateOf(StyleScope.Sheet.name) }
    val sc = StyleScope.valueOf(scope)
    val section = st.section
    fun tg(id: String) = setOpen(if (open == id) null else id)
    val chordId = if (sc == StyleScope.Section) section?.chordFontId ?: sheet.chordFontId else sheet.chordFontId
    val lyricsId = if (sc == StyleScope.Section) section?.lyricsFontId ?: sheet.lyricsFontId else sheet.lyricsFontId
    val size = if (sc == StyleScope.Section) section?.chordSizeSp ?: sheet.chordSizeSp else sheet.chordSizeSp
    Drawer("Sections", open == "sec", { tg("sec") }, summary = section?.name) {
        OverlineLabel("Add")
        ChipFlow { SectionKind.values().forEach { k -> ToolChip(k.label(), { a.addSection(k) }) } }
        if (section != null) {
            OverlineLabel("Selected")
            FieldBox(section.name, { a.renameSection(section.id, it) }, "Section name")
            ChipFlow {
                ToolChip("Move ↑", { a.moveSection(section.id, -1) })
                ToolChip("Move ↓", { a.moveSection(section.id, 1) })
                ToolChip("Delete", { a.deleteSection(section.id) })
            }
        }
    }
    Drawer("Lyrics", open == "lyr", { tg("lyr") }, summary = "Selected bar") {
        val bar = st.bar
        if (bar == null) Text("Select a bar first.", style = SheetsType.Caption, color = SheetsColors.TextMuted)
        else FieldBox(bar.lyric, { a.setLyric(bar.id, it) }, "Lyric under this bar", singleLine = false)
    }
    Drawer("Style", open == "style", { tg("style") }, summary = "Fonts · Size") {
        ScopeChips(sc) { scope = it.name }
        OverlineLabel("Notes font")
        ChipFlow { SheetsFonts.Options.forEach { f -> ToolChip(f.label, { a.setChordFont(f.id, sc) }, selected = chordId == f.id, family = f.family) } }
        OverlineLabel("Notes size · ${size.toInt()} sp")
        SimpleSlider(size, { a.setChordSize(it, sc) }, valueRange = SheetsLayout.ChordMinSizeSp..SheetsLayout.ChordMaxSizeSp)
        OverlineLabel("Lyrics font")
        ChipFlow { SheetsFonts.Options.forEach { f -> ToolChip(f.label, { a.setLyricsFont(f.id, sc) }, selected = lyricsId == f.id, family = f.family) } }
    }
}

@Composable
private fun PageTab(
    st: EditorUiState, sheet: Sheet, settings: SheetsSettings, a: EditorActions,
    onSetNotation: (Notation) -> Unit, onExport: (ExportFormat) -> Unit, open: String?, setOpen: (String?) -> Unit,
) {
    var scope by rememberSaveable { mutableStateOf(StyleScope.Sheet.name) }
    val sc = StyleScope.valueOf(scope)
    val section = st.section
    fun tg(id: String) = setOpen(if (open == id) null else id)
    val bpl = if (sc == StyleScope.Section) section?.barsPerLine ?: sheet.barsPerLine else sheet.barsPerLine
    val color = (if (sc == StyleScope.Section) section?.colorArgb ?: sheet.colorArgb else sheet.colorArgb).toColor()
    Drawer("Layout", open == "layout", { tg("layout") }, summary = "${sheet.barsPerLine} bars per line") {
        ScopeChips(sc) { scope = it.name }
        OverlineLabel("Bars per line")
        ChipFlow { listOf(1, 2, 3, 4, 6, 8).forEach { n -> ToolChip("$n", { a.setBarsPerLine(n, sc) }, selected = bpl == n) } }
        OverlineLabel("Pagination")
        ChipFlow {
            ToolChip("Unified", { a.setPagination(Pagination.Unified) }, selected = sheet.pagination == Pagination.Unified)
            ToolChip("One page per section", { a.setPagination(Pagination.PerSection) }, selected = sheet.pagination == Pagination.PerSection)
        }
    }
    Drawer("Page color", open == "color", { tg("color") }, summary = "Presets · Custom") {
        ScopeChips(sc) { scope = it.name }
        ColorPickerPanel(color, { a.setColor(it.toArgbLong(), sc) })
    }
    Drawer("Display", open == "display", { tg("display") }, summary = settings.notation.name) {
        OverlineLabel("Notation (all sheets)")
        ChipFlow {
            ToolChip("Nashville", { onSetNotation(Notation.Nashville) }, selected = settings.notation == Notation.Nashville)
            ToolChip("Standard", { onSetNotation(Notation.Standard) }, selected = settings.notation == Notation.Standard)
        }
        OverlineLabel("Song key")
        ChipFlow { Theory.KEY_NAMES.forEachIndexed { pc, n -> ToolChip(n, { a.setSongKey(pc) }, selected = sheet.keyPc == pc) } }
        if (section != null) {
            OverlineLabel("Key of “${section.name}”")
            ChipFlow {
                ToolChip("Song", { a.setSectionKey(section.id, null) }, selected = section.keyPc == null)
                Theory.KEY_NAMES.forEachIndexed { pc, n -> ToolChip(n, { a.setSectionKey(section.id, pc) }, selected = section.keyPc == pc) }
            }
        }
        OverlineLabel("Tempo · ${sheet.bpm} BPM")
        ChipFlow {
            ToolChip("−5", { a.setBpm(sheet.bpm - 5) }); ToolChip("−1", { a.setBpm(sheet.bpm - 1) })
            ToolChip("+1", { a.setBpm(sheet.bpm + 1) }); ToolChip("+5", { a.setBpm(sheet.bpm + 5) })
        }
        OverlineLabel("Time signature")
        ChipFlow {
            listOf(4 to 4, 3 to 4, 2 to 4, 6 to 8, 12 to 8).forEach { (b, u) -> ToolChip("$b/$u", { a.setTimeSig(b, u) }, selected = sheet.beatsPerBar == b && sheet.beatUnit == u) }
        }
    }
    Drawer("Export", open == "export", { tg("export") }, summary = settings.exportFormat.name) {
        ChipFlow { ExportFormat.values().forEach { f -> ToolChip(f.name, { onExport(f) }) } }
    }
}
