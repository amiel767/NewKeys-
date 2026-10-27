package com.aetherkeys.sheets.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aetherkeys.sheets.model.*
import com.aetherkeys.sheets.ui.components.SectionTagChip
import com.aetherkeys.sheets.ui.components.SheetsIcons
import com.aetherkeys.sheets.ui.theme.*

/** Marque musicale -> icône vectorielle (aucun glyphe texte : le rendu ne dépend plus de la police). */
private fun Mark.icon(): ImageVector = when (this) {
    Mark.Fermata -> SheetsIcons.Fermata
    Mark.Stop -> SheetsIcons.Stop
    Mark.Push -> SheetsIcons.Push
    Mark.Accent -> SheetsIcons.Accent
    Mark.Segno -> SheetsIcons.Segno
    Mark.Coda -> SheetsIcons.Coda
}

private fun DrawScope.drawBarLines(bar: Bar, ink: Color) {
    val lw = 2.5.dp.toPx()
    val pad = size.height * 0.12f
    val a = ink.copy(alpha = 0.85f)
    val top = pad
    val bottom = size.height - pad
    if (bar.repeatStart) {
        drawLine(a, Offset(lw * 1.6f, top), Offset(lw * 1.6f, bottom), lw * 2.4f)
        drawLine(a, Offset(lw * 5f, top), Offset(lw * 5f, bottom), lw * 0.7f)
        drawCircle(a, lw * 0.8f, Offset(lw * 8f, size.height * 0.38f))
        drawCircle(a, lw * 0.8f, Offset(lw * 8f, size.height * 0.62f))
    } else drawLine(a, Offset(lw / 2f, top), Offset(lw / 2f, bottom), lw)
    if (bar.repeatEnd) {
        val x = size.width - lw * 1.6f
        drawLine(a, Offset(x, top), Offset(x, bottom), lw * 2.4f)
        drawLine(a, Offset(x - lw * 3.4f, top), Offset(x - lw * 3.4f, bottom), lw * 0.7f)
        drawCircle(a, lw * 0.8f, Offset(x - lw * 6.4f, size.height * 0.38f))
        drawCircle(a, lw * 0.8f, Offset(x - lw * 6.4f, size.height * 0.62f))
    }
}

@Composable
fun BarCell(
    bar: Bar, text: String, sizeSp: Float, family: FontFamily, ink: Color, selected: Boolean,
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
) {
    val above = buildList {
        bar.volta?.let { add("$it.") }
        if (bar.repeatEnd && bar.repeatCount > 2) add("x${bar.repeatCount}")
    }.joinToString("  ")
    Box(
        modifier.fillMaxHeight().drawBehind { drawBarLines(bar, ink) }
            .clip(RoundedCornerShape(SheetsRadius.Chord))
            .background(if (selected) ink.copy(alpha = 0.12f) else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (above.isNotEmpty() || bar.marks.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (above.isNotEmpty()) Text(above, style = SheetsType.Caption, color = ink.copy(alpha = 0.8f), maxLines = 1)
                    bar.marks.forEach { mark ->
                        Icon(mark.icon(), contentDescription = null, tint = ink.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                    }
                }
            }
            Text(text, style = SheetsType.chord(family, sizeSp), color = ink, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
fun ChartLines(
    plan: PagePlan, sheet: Sheet, settings: SheetsSettings, ink: Color,
    selectedBarId: String?, onBarClick: ((Section, Bar) -> Unit)?, modifier: Modifier = Modifier,
) {
    val section = sheet.sections[plan.sectionIndex]
    val key = sheet.keyFor(section)
    val family = SheetsFonts.family(plan.chordFontId)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        plan.lines.forEach { line ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                line.forEach { bar ->
                    BarCell(
                        bar = bar, text = Theory.barText(bar, key, settings.notation, settings.passing),
                        sizeSp = plan.sizeSp, family = family, ink = ink, selected = bar.id == selectedBarId,
                        modifier = Modifier.weight(1f).height((plan.sizeSp * 1.55f).dp),
                        onClick = onBarClick?.let { cb -> { cb(section, bar) } },
                    )
                }
                repeat(plan.barsPerLine - line.size) { Spacer(Modifier.weight(1f)) }
                Box(Modifier.width(2.5.dp).height((plan.sizeSp * 1.55f).dp).padding(vertical = (plan.sizeSp * 0.19f).dp).background(ink.copy(alpha = 0.85f)))
            }
        }
    }
}

/** Vue Lyrics : grandes lignes, ligne active en plein, les autres atténuées. */
@Composable
fun LyricsView(sheet: Sheet, activeBarId: String?, ink: Color, modifier: Modifier = Modifier) {
    val family = SheetsFonts.family(sheet.lyricsFontId)
    val weight = SheetsFonts.lyricsWeight(sheet.lyricsFontId)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        sheet.sections.forEach { sec ->
            val lines = sec.bars.filter { it.lyric.isNotBlank() }
            if (lines.isNotEmpty()) {
                item(key = "tag-${sec.id}") { SectionTagChip(sec.name, sec.kind, colorOverride = sec.colorArgb?.toColor()) }
                items(lines, key = { it.id }) { bar ->
                    Text(
                        bar.lyric,
                        style = TextStyle(fontFamily = family, fontWeight = weight, fontSize = 30.sp, lineHeight = 38.sp),
                        color = ink.copy(alpha = if (bar.id == activeBarId) 1f else 0.4f),
                    )
                }
            }
        }
    }
}
