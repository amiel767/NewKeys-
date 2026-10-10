package com.aetherkeys.sheets.data

import com.aetherkeys.sheets.model.*
import org.json.JSONArray
import org.json.JSONObject

internal object SheetJson {
    const val VERSION = 1

    private inline fun <reified E : Enum<E>> enumOr(name: String?, def: E): E =
        runCatching { enumValueOf<E>(name ?: "") }.getOrDefault(def)

    private fun JSONObject.intOrNull(k: String): Int? = if (has(k) && !isNull(k)) getInt(k) else null
    private fun JSONObject.longOrNull(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
    private fun JSONObject.strOrNull(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
    private fun JSONObject.floatOrNull(k: String): Float? = if (has(k) && !isNull(k)) getDouble(k).toFloat() else null
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { getString(it) }
    private fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> =
        if (this == null) emptyList() else List(length()) { f(getJSONObject(it)) }

    // ---------- Sheet ----------
    fun encode(s: Sheet): String = JSONObject().apply {
        put("id", s.id); put("title", s.title); put("keyPc", s.keyPc); put("bpm", s.bpm)
        put("beats", s.beatsPerBar); put("beatUnit", s.beatUnit); put("color", s.colorArgb)
        put("chordFont", s.chordFontId); put("lyricsFont", s.lyricsFontId)
        put("chordSize", s.chordSizeSp.toDouble()); put("barsPerLine", s.barsPerLine)
        put("pagination", s.pagination.name); put("groupShort", s.groupShortBars)
        put("favorite", s.favorite); put("created", s.createdAt); put("updated", s.updatedAt)
        put("sections", JSONArray().also { a -> s.sections.forEach { a.put(section(it)) } })
    }.toString()

    private fun section(s: Section) = JSONObject().apply {
        put("id", s.id); put("kind", s.kind.name); put("name", s.name)
        putOpt("keyPc", s.keyPc); putOpt("chordFont", s.chordFontId); putOpt("lyricsFont", s.lyricsFontId)
        putOpt("color", s.colorArgb); putOpt("barsPerLine", s.barsPerLine)
        s.chordSizeSp?.let { put("chordSize", it.toDouble()) }
        put("bars", JSONArray().also { a -> s.bars.forEach { a.put(bar(it)) } })
    }

    private fun bar(b: Bar) = JSONObject().apply {
        put("id", b.id); put("split", b.split.name); put("nc", b.noChord); put("rp", b.repeatPrevious)
        put("rs", b.repeatStart); put("re", b.repeatEnd); put("rc", b.repeatCount); putOpt("volta", b.volta)
        put("marks", JSONArray(b.marks.map { it.name })); put("lyric", b.lyric)
        put("chords", JSONArray().also { a ->
            b.chords.forEach { c ->
                a.put(
                    if (c == null) JSONObject.NULL else JSONObject().apply {
                        put("r", c.root); put("q", c.quality.name); put("e", JSONArray(c.exts.map { it.name }))
                        putOpt("b", c.bass)
                    }
                )
            }
        })
    }

    fun decode(text: String): Sheet {
        val o = JSONObject(text)
        return Sheet(
            id = o.getString("id"),
            title = o.optString("title", "Untitled"),
            keyPc = o.optInt("keyPc", 0),
            bpm = o.optInt("bpm", 120),
            beatsPerBar = o.optInt("beats", 4),
            beatUnit = o.optInt("beatUnit", 4),
            colorArgb = o.optLong("color", PalettePresets.Paper),
            chordFontId = o.optString("chordFont", FontIds.ArchitectsDaughter),
            lyricsFontId = o.optString("lyricsFont", FontIds.PlusJakartaSans),
            chordSizeSp = o.optDouble("chordSize", 32.0).toFloat(),
            barsPerLine = o.optInt("barsPerLine", 4),
            pagination = enumOr(o.optString("pagination"), Pagination.Unified),
            groupShortBars = o.optInt("groupShort", 0),
            favorite = o.optBoolean("favorite", false),
            createdAt = o.optLong("created", System.currentTimeMillis()),
            updatedAt = o.optLong("updated", System.currentTimeMillis()),
            sections = o.optJSONArray("sections").mapObjects { decodeSection(it) },
        )
    }

    private fun decodeSection(o: JSONObject) = Section(
        id = o.getString("id"),
        kind = enumOr(o.optString("kind"), SectionKind.Custom),
        name = o.optString("name", "Section"),
        keyPc = o.intOrNull("keyPc"),
        chordFontId = o.strOrNull("chordFont"),
        lyricsFontId = o.strOrNull("lyricsFont"),
        colorArgb = o.longOrNull("color"),
        barsPerLine = o.intOrNull("barsPerLine"),
        chordSizeSp = o.floatOrNull("chordSize"),
        bars = o.optJSONArray("bars").mapObjects { decodeBar(it) },
    )

    private fun decodeBar(o: JSONObject): Bar {
        val arr = o.optJSONArray("chords")
        val chords: List<Chord?> = if (arr == null) listOf(null) else List(arr.length()) { i ->
            if (arr.isNull(i)) null else arr.getJSONObject(i).let { c ->
                Chord(
                    root = c.getInt("r"),
                    quality = enumOr(c.optString("q"), Quality.Major),
                    exts = c.optJSONArray("e").strings().mapNotNull { runCatching { Ext.valueOf(it) }.getOrNull() }.toSet(),
                    bass = c.intOrNull("b"),
                )
            }
        }
        return Bar(
            id = o.getString("id"),
            chords = chords.ifEmpty { listOf(null) },
            split = enumOr(o.optString("split"), Split.None),
            noChord = o.optBoolean("nc", false),
            repeatPrevious = o.optBoolean("rp", false),
            repeatStart = o.optBoolean("rs", false),
            repeatEnd = o.optBoolean("re", false),
            repeatCount = o.optInt("rc", 2),
            volta = o.intOrNull("volta"),
            marks = o.optJSONArray("marks").strings().mapNotNull { runCatching { Mark.valueOf(it) }.getOrNull() }.toSet(),
            lyric = o.optString("lyric", ""),
        )
    }

    // ---------- Library (settings + setlists) ----------
    fun encodeLibrary(l: LibraryData): String = JSONObject().apply {
        val s = l.settings
        put("settings", JSONObject().apply {
            put("notation", s.notation.name); put("passing", s.passing.name); put("defaultKey", s.defaultKeyPc)
            put("defaultPagination", s.defaultPagination.name); put("defaultChordFont", s.defaultChordFontId)
            put("defaultLyricsFont", s.defaultLyricsFontId); put("lockOnOpen", s.lockOnOpen)
            put("swipeReversed", s.swipeReversed); put("export", s.exportFormat.name)
        })
        put("setlists", JSONArray().also { a ->
            l.setlists.forEach { sl ->
                a.put(JSONObject().apply { put("id", sl.id); put("name", sl.name); put("sheets", JSONArray(sl.sheetIds)) })
            }
        })
    }.toString()

    fun decodeLibrary(text: String): LibraryData {
        val o = JSONObject(text)
        val s = o.optJSONObject("settings")
        val settings = if (s == null) SheetsSettings() else SheetsSettings(
            notation = enumOr(s.optString("notation"), Notation.Nashville),
            passing = enumOr(s.optString("passing"), PassingDisplay.Auto),
            defaultKeyPc = s.optInt("defaultKey", 0),
            defaultPagination = enumOr(s.optString("defaultPagination"), Pagination.Unified),
            defaultChordFontId = s.optString("defaultChordFont", FontIds.ArchitectsDaughter),
            defaultLyricsFontId = s.optString("defaultLyricsFont", FontIds.PlusJakartaSans),
            lockOnOpen = s.optBoolean("lockOnOpen", false),
            swipeReversed = s.optBoolean("swipeReversed", false),
            exportFormat = enumOr(s.optString("export"), ExportFormat.Pdf),
        )
        val lists = o.optJSONArray("setlists").mapObjects {
            Setlist(it.getString("id"), it.optString("name", "Setlist"), it.optJSONArray("sheets").strings())
        }
        return LibraryData(settings, lists)
    }
}
