package com.aetherkeys.sheets.export

import com.aetherkeys.sheets.model.*

/** Export ChordPro (texte). Toujours en notation Standard (lettres), accords de préparation en degré brut. */
object ChordProExporter {
    fun format(sheet: Sheet): String = buildString {
        appendLine("{title: ${sheet.title}}")
        appendLine("{key: ${Theory.noteName(sheet.keyPc, sheet.keyPc)}}")
        appendLine("{tempo: ${sheet.bpm}}")
        appendLine("{time: ${sheet.beatsPerBar}/${sheet.beatUnit}}")
        sheet.sections.forEach { sec ->
            appendLine()
            appendLine("{comment: ${sec.name}}")
            val key = sheet.keyFor(sec)
            sec.bars.chunked((sec.barsPerLine ?: sheet.barsPerLine).coerceAtLeast(1)).forEach { line ->
                appendLine("| " + line.joinToString(" | ") { Theory.barText(it, key, Notation.Standard, PassingDisplay.Raw).ifBlank { "-" } } + " |")
                val lyrics = line.map { it.lyric }.filter { it.isNotBlank() }
                if (lyrics.isNotEmpty()) appendLine(lyrics.joinToString(" "))
            }
        }
    }
}
