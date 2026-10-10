package com.aetherkeys.sheets.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.aetherkeys.sheets.model.SectionKind
import com.aetherkeys.sheets.ui.theme.*
import kotlin.math.roundToInt
import android.graphics.Color as AColor

/** Pastille : filtres, setlists, bascules. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(SheetsRadius.Chip))
            .background(if (selected) Color.White else SheetsColors.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = SheetsType.Label, color = if (selected) SheetsColors.AccentText else SheetsColors.TextPrimary, maxLines = 1)
    }
}

/** Bouton glyphe (←, ↶, ⚙, +, 🔒…). */
@Composable
fun GlyphButton(
    glyph: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, filled: Boolean = false, size: Int = 40,
) {
    Box(
        modifier
            .size(size.dp)
            .clip(RoundedCornerShape(SheetsRadius.Button))
            .background(if (filled) SheetsColors.Accent else SheetsColors.SurfaceRaised)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph, style = SheetsType.Title.copy(fontSize = 16.sp),
            color = when {
                !enabled -> SheetsColors.TextMuted.copy(alpha = 0.4f)
                filled -> SheetsColors.AccentText
                else -> SheetsColors.TextPrimary
            },
        )
    }
}

/** Icône Mixer : trois curseurs. */
@Composable
fun MixerIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(40.dp).clip(RoundedCornerShape(SheetsRadius.Chip)).background(SheetsColors.Chip).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp, 18.dp)) {
            val c = SheetsColors.TextPrimary
            val w = 2.dp.toPx()
            val xs = listOf(0.2f, 0.5f, 0.8f)
            val ys = listOf(0.62f, 0.34f, 0.66f)
            xs.forEachIndexed { i, fx ->
                drawLine(c, Offset(size.width * fx, size.height * 0.1f), Offset(size.width * fx, size.height * 0.9f), w, StrokeCap.Round)
                drawCircle(c, 2.2.dp.toPx(), Offset(size.width * fx, size.height * ys[i]))
            }
        }
    }
}

@Composable
fun SectionTagChip(name: String, kind: SectionKind, modifier: Modifier = Modifier, colorOverride: Color? = null) {
    val bg = colorOverride ?: kind.color()
    Box(
        modifier.clip(RoundedCornerShape(SheetsRadius.Chip)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) { Text(name, style = SheetsType.SectionTag, color = onColorFor(bg), maxLines = 1) }
}

@Composable
fun OverlineLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = SheetsType.Overline, color = SheetsColors.TextMuted, modifier = modifier)
}

/** Tiroir en accordéon : titre + résumé quand fermé. */
@Composable
fun Drawer(
    title: String, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier,
    summary: String? = null, warn: Boolean = false, content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(SheetsRadius.Drawer)).background(SheetsColors.Surface).animateContentSize(),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = SheetsType.Label, color = if (warn) SheetsColors.Warning else SheetsColors.TextPrimary)
            Text(
                if (expanded) "▾" else (summary?.let { "$it  ▸" } ?: "▸"),
                style = SheetsType.Caption, color = if (warn) SheetsColors.Warning else SheetsColors.TextMuted, maxLines = 1,
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

/** Bouton d'outil compact (extensions, altérations, symboles…). */
@Composable
fun ToolChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, family: FontFamily? = null, enabled: Boolean = true) {
    val bg = if (selected) SheetsColors.Accent else SheetsColors.SurfaceChip
    val fg = if (selected) SheetsColors.AccentText else SheetsColors.Accent
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(bg)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (enabled) fg else fg.copy(alpha = 0.4f), maxLines = 1,
            style = if (family != null) SheetsType.Body.copy(fontFamily = family, fontSize = 15.sp) else SheetsType.Label.copy(fontSize = 12.sp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

/** Touche de degré 1-7 (police des notes). */
@Composable
fun DegreeKey(n: Int, family: FontFamily, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(10.dp)).background(SheetsColors.SurfaceChip).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("$n", style = SheetsType.chord(family, 26f), color = Color.White) }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = SheetsType.Body, color = SheetsColors.TextPrimary)
        Box(
            Modifier.size(width = 44.dp, height = 26.dp).clip(CircleShape).background(if (checked) SheetsColors.Accent else SheetsColors.SurfaceChip).padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) { Box(Modifier.size(20.dp).clip(CircleShape).background(if (checked) SheetsColors.AccentText else SheetsColors.TextMuted)) }
    }
}

@Composable
fun SimpleSlider(
    value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f, trackBrush: Brush? = null,
) {
    var widthPx by remember { mutableStateOf(1) }
    val thumb = with(LocalDensity.current) { 20.dp.toPx() }
    val span = valueRange.endInclusive - valueRange.start
    val frac = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    fun at(x: Float) = valueRange.start + ((x - thumb / 2f) / (widthPx - thumb).coerceAtLeast(1f)).coerceIn(0f, 1f) * span
    Box(
        modifier.fillMaxWidth().height(28.dp).onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(valueRange) { detectTapGestures { onValueChange(at(it.x)) } }
            .pointerInput(valueRange) { detectHorizontalDragGestures { change, _ -> change.consume(); onValueChange(at(change.position.x)) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(trackBrush ?: SolidColor(SheetsColors.SurfaceChip)))
        Box(
            Modifier.offset { IntOffset(((widthPx - thumb) * frac).roundToInt(), 0) }.size(20.dp)
                .clip(CircleShape).background(Color.White).border(BorderStroke(1.dp, Color(0x33000000)), CircleShape),
        )
    }
}

/** Presets + sélecteur libre (teinte, saturation, luminosité). */
@Composable
fun ColorPickerPanel(selected: Color, onPick: (Color) -> Unit, modifier: Modifier = Modifier) {
    val hsv = remember(selected) { FloatArray(3).also { AColor.colorToHSV(selected.toArgb(), it) } }
    fun pick(h: Float, s: Float, v: Float) = onPick(Color(AColor.HSVToColor(floatArrayOf(h, s, v))))
    val hueBrush = remember { Brush.horizontalGradient((0..6).map { Color(AColor.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }) }
    val satBrush = Brush.horizontalGradient(listOf(Color(AColor.HSVToColor(floatArrayOf(hsv[0], 0f, hsv[2]))), Color(AColor.HSVToColor(floatArrayOf(hsv[0], 1f, hsv[2])))))
    val valBrush = Brush.horizontalGradient(listOf(Color.Black, Color(AColor.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f)))))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OverlineLabel("Presets")
        ChipFlow {
            SheetPalette.Presets.forEach { c ->
                val on = c.toArgb() == selected.toArgb()
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(c)
                        .border(BorderStroke(if (on) 3.dp else 1.dp, if (on) Color.White else Color(0x33FFFFFF)), CircleShape)
                        .clickable { onPick(c) },
                )
            }
        }
        OverlineLabel("Custom")
        SimpleSlider(hsv[0], { pick(it, hsv[1].coerceAtLeast(0.05f), hsv[2].coerceAtLeast(0.05f)) }, valueRange = 0f..360f, trackBrush = hueBrush)
        SimpleSlider(hsv[1], { pick(hsv[0], it, hsv[2]) }, trackBrush = satBrush)
        SimpleSlider(hsv[2], { pick(hsv[0], hsv[1], it) }, trackBrush = valBrush)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(selected))
            Text("#%06X".format(selected.toArgb() and 0xFFFFFF), style = SheetsType.Caption, color = SheetsColors.TextMuted, textAlign = TextAlign.Start)
        }
    }
}
