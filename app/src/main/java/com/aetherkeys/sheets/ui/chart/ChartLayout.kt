package com.aetherkeys.sheets.ui.chart

import com.aetherkeys.sheets.model.*

data class PagePlan(
    val sectionIndex: Int,
    val part: Int,
    val parts: Int,
    val lines: List<List<Bar>>,
    val barsPerLine: Int,
    val sizeSp: Float,
    val chordFontId: String,
)

/**
 * Pure layout: guarantees a full line is never truncated.
 * 1) reduce bars-per-line (preferred -> 2 -> 1), 2) reduce text size down to a floor,
 * 3) split a too-long section into continuation pages (never a vertical scroll).
 * [measure100] = width in px of a text at 100sp for a given font id.
 */
object ChartLayout {
    fun plan(
        sheet: Sheet,
        settings: SheetsSettings,
        availWidthPx: Float,
        availHeightPx: Float,
        pxPerSp: Float,
        barPaddingPx: Float,
        minSp: Float,
        measure100: (text: String, fontId: String) -> Float,
    ): List<PagePlan> {
        val out = ArrayList<PagePlan>()
        sheet.sections.forEachIndexed { si, sec ->
            if (sec.bars.isEmpty()) return@forEachIndexed
            val fontId = sec.chordFontId ?: sheet.chordFontId
            val prefBpl = (sec.barsPerLine ?: sheet.barsPerLine).coerceIn(1, 8)
            val prefSize = sec.chordSizeSp ?: sheet.chordSizeSp
            val key = sheet.keyFor(sec)
            val widest = sec.bars.maxOf { measure100(Theory.barText(it, key, settings.notation, settings.passing), fontId) }.coerceAtLeast(1f)
            val candidates = (listOf(prefBpl) + listOf(4, 3, 2, 1).filter { it < prefBpl }).distinct()
            var bpl = candidates.last()
            var size = minSp
            for (c in candidates) {
                val barW = availWidthPx / c - barPaddingPx
                val fit = barW * 100f / widest
                if (fit >= minSp) { bpl = c; size = minOf(prefSize, fit); break }
            }
            val lineH = size * pxPerSp * 1.35f
            val linesPerPage = if (availHeightPx >= Float.MAX_VALUE / 2) Int.MAX_VALUE else maxOf(1, (availHeightPx / lineH).toInt())
            val lines = sec.bars.chunked(bpl)
            val pages = lines.chunked(linesPerPage)
            pages.forEachIndexed { pi, pl -> out.add(PagePlan(si, pi, pages.size, pl, bpl, size, fontId)) }
        }
        return out
    }
}
