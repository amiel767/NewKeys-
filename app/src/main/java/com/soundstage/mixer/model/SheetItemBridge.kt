package com.soundstage.mixer.model

import com.aetherkeys.sheets.model.Notation
import com.aetherkeys.sheets.model.PassingDisplay
import com.aetherkeys.sheets.model.Sheet
import com.aetherkeys.sheets.model.Theory
import com.aetherkeys.sheets.model.keyFor

/**
 * Bridges a Sheets library [Sheet] to the mixer's floating-sheet [SheetItem].
 *
 * The floating window over the mixer renders plain text, so the chart is
 * flattened here: one line per bar group, lyrics on the line below. Chords are
 * written in Standard notation because the floating window re-converts them to
 * the mixer's current notation itself.
 */
fun Sheet.toSheetItem(colorIndex: Int = 0): SheetItem {
    val keyName = Theory.noteName(keyPc, keyPc)
    val text = buildString {
        sections.forEach { section ->
            if (isEmpty()) {
                appendLine("$keyName · $bpm BPM · $beatsPerBar/$beatUnit")
                appendLine()
            }
            appendLine("[${section.name}]")
            val key = keyFor(section)
            val perLine = (section.barsPerLine ?: barsPerLine).coerceAtLeast(1)
            section.bars.chunked(perLine).forEach { line ->
                appendLine(
                    "| " + line.joinToString(" | ") {
                        Theory.barText(it, key, Notation.Standard, PassingDisplay.Raw).ifBlank { "-" }
                    } + " |"
                )
                val lyrics = line.map { it.lyric }.filter { it.isNotBlank() }
                if (lyrics.isNotEmpty()) appendLine(lyrics.joinToString(" "))
            }
            appendLine()
        }
    }.trim()

    return SheetItem(
        id = id,
        title = title,
        key = keyName,
        tempo = bpm,
        timeSignature = "$beatsPerBar/$beatUnit",
        content = text,
        colorIndex = colorIndex,
        isFavorite = favorite,
        updatedAt = updatedAt,
    )
}
