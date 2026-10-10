package com.aetherkeys.sheets.model

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

object FontIds {
    const val ArchitectsDaughter = "architects_daughter"
    const val Caveat = "caveat"
    const val Kalam = "kalam"
    const val PatrickHand = "patrick_hand"
    const val PlusJakartaSans = "plus_jakarta_sans"
    const val FiraCode = "fira_code"
    const val JetBrainsMono = "jetbrains_mono"
}

object PalettePresets {
    const val Paper = 0xFFF6EFDCL
    val Presets: List<Long> = listOf(
        0xFF7A5CFFL, 0xFF8B5CF6L, 0xFFA855F7L, 0xFFD946EFL,
        0xFFE0508FL, 0xFFEC4899L, 0xFFF43F5EL, 0xFFEF4444L,
        0xFFF08A3CL, 0xFFF97316L, 0xFFF59E0BL, 0xFFD9B13AL,
        0xFF84CC16L, 0xFF22C55EL, 0xFF2FB58AL, 0xFF14B8A6L,
        0xFF06B6D4L, 0xFF38BDF8L, 0xFF3F7BE8L, 0xFF6366F1L,
    )
}

enum class Quality { Major, Minor, Dim, Aug, Sus2, Sus4, Power5 }
enum class Ext { Seven, Maj7, Six, Nine, Add9 }
enum class Split { None, Two, Four, Passing }
enum class Mark { Fermata, Stop, Push, Accent, Segno, Coda }
enum class SectionKind { Intro, Verse, PreChorus, Chorus, Bridge, Solo, Outro, Tag, Interlude, Custom }
enum class Pagination { Unified, PerSection }
enum class Notation { Nashville, Standard }
enum class PassingDisplay { Auto, Raw }
enum class ExportFormat { Pdf, Image, ChordPro }

fun SectionKind.label(): String = when (this) {
    SectionKind.Intro -> "Intro"
    SectionKind.Verse -> "Verse"
    SectionKind.PreChorus -> "Pre-chorus"
    SectionKind.Chorus -> "Chorus"
    SectionKind.Bridge -> "Bridge"
    SectionKind.Solo -> "Solo"
    SectionKind.Outro -> "Outro"
    SectionKind.Tag -> "Tag"
    SectionKind.Interlude -> "Interlude"
    SectionKind.Custom -> "Section"
}

/** root / bass = pitch class 0..11 (0 = C). Stored by pitch, never by text. */
data class Chord(
    val root: Int,
    val quality: Quality = Quality.Major,
    val exts: Set<Ext> = emptySet(),
    val bass: Int? = null,
)

data class Bar(
    val id: String = newId(),
    val chords: List<Chord?> = listOf(null),
    val split: Split = Split.None,
    val noChord: Boolean = false,
    val repeatPrevious: Boolean = false,
    val repeatStart: Boolean = false,
    val repeatEnd: Boolean = false,
    val repeatCount: Int = 2,
    val volta: Int? = null,
    val marks: Set<Mark> = emptySet(),
    val lyric: String = "",
)

data class Section(
    val id: String = newId(),
    val kind: SectionKind = SectionKind.Verse,
    val name: String = "Verse",
    val bars: List<Bar> = emptyList(),
    val keyPc: Int? = null,
    val chordFontId: String? = null,
    val lyricsFontId: String? = null,
    val colorArgb: Long? = null,
    val barsPerLine: Int? = null,
    val chordSizeSp: Float? = null,
)

data class Sheet(
    val id: String = newId(),
    val title: String = "Untitled",
    val keyPc: Int = 0,
    val bpm: Int = 120,
    val beatsPerBar: Int = 4,
    val beatUnit: Int = 4,
    val colorArgb: Long = PalettePresets.Paper,
    val chordFontId: String = FontIds.ArchitectsDaughter,
    val lyricsFontId: String = FontIds.PlusJakartaSans,
    val chordSizeSp: Float = 32f,
    val barsPerLine: Int = 4,
    val pagination: Pagination = Pagination.Unified,
    val groupShortBars: Int = 0,
    val sections: List<Section> = emptyList(),
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class SheetsSettings(
    val notation: Notation = Notation.Nashville,
    val passing: PassingDisplay = PassingDisplay.Auto,
    val defaultKeyPc: Int = 0,
    val defaultPagination: Pagination = Pagination.Unified,
    val defaultChordFontId: String = FontIds.ArchitectsDaughter,
    val defaultLyricsFontId: String = FontIds.PlusJakartaSans,
    val lockOnOpen: Boolean = false,
    val swipeReversed: Boolean = false,
    val exportFormat: ExportFormat = ExportFormat.Pdf,
)

data class Setlist(val id: String = newId(), val name: String, val sheetIds: List<String> = emptyList())

data class LibraryData(
    val settings: SheetsSettings = SheetsSettings(),
    val setlists: List<Setlist> = emptyList(),
)

// ---------- helpers ----------
fun Bar.slotCount(): Int = when (split) {
    Split.None -> 1
    Split.Two, Split.Passing -> 2
    Split.Four -> 4
}

fun Bar.withSplit(s: Split): Bar {
    val n = when (s) { Split.None -> 1; Split.Two, Split.Passing -> 2; Split.Four -> 4 }
    return copy(split = s, chords = List(n) { chords.getOrNull(it) })
}

fun Bar.setChord(slot: Int, chord: Chord?): Bar {
    val l = chords.toMutableList()
    while (l.size <= slot) l.add(null)
    l[slot] = chord
    return copy(chords = l)
}

fun Chord.shift(n: Int): Chord = copy(root = (root + n + 120) % 12, bass = bass?.let { (it + n + 120) % 12 })

fun Sheet.mapSection(id: String, f: (Section) -> Section): Sheet =
    copy(sections = sections.map { if (it.id == id) f(it) else it })

fun Sheet.mapBar(sectionId: String, barId: String, f: (Bar) -> Bar): Sheet =
    mapSection(sectionId) { s -> s.copy(bars = s.bars.map { if (it.id == barId) f(it) else it }) }

fun Sheet.keyFor(section: Section): Int = section.keyPc ?: keyPc

fun Sheet.transposed(n: Int): Sheet = copy(
    keyPc = (keyPc + n + 120) % 12,
    sections = sections.map { s ->
        s.copy(
            keyPc = s.keyPc?.let { (it + n + 120) % 12 },
            bars = s.bars.map { b -> b.copy(chords = b.chords.map { it?.shift(n) }) },
        )
    },
)
