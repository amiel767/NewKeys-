package com.soundstage.mixer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.ui.components.TrackColorMode
import com.soundstage.mixer.ui.components.TrackColorScheme
import com.soundstage.mixer.ui.components.TrackColorsLogic
import com.soundstage.mixer.ui.components.TrackPalette
import com.soundstage.mixer.ui.components.TrackPalettes

/**
 * Track colors, as a page inside Settings — reached from a tappable card in the
 * Interface tab, never a floating window. Every row uses the Settings components
 * (`SectionLabel`, `SettingRow`, `SetSwitch`, `SetDivider`) so the page is
 * indistinguishable from the rest of the screen.
 *
 * The live preview is a miniature of the mixer: 8 lane strips plus the master.
 * It renders the *effective* scheme, so what the user picks is what the mixer
 * will paint.
 */
@Composable
fun TrackColorsPage(
    mode: TrackColorMode,
    paletteId: String,
    singleColor: Color,
    randomLanes: List<Color>,
    dynamic: Boolean,
    onModeChange: (TrackColorMode) -> Unit,
    onPaletteChange: (String) -> Unit,
    onSingleColorChange: (Color) -> Unit,
    onRandomize: () -> Unit,
    onDynamicChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = remember(mode, paletteId, singleColor, randomLanes) {
        TrackColorsLogic.scheme(mode, paletteId, randomLanes, singleColor)
    }

    Column(modifier.fillMaxSize()) {
        TrackColorsHeader(onBack)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp)
        ) {
            item(key = "preview") {
                SectionLabel("Preview")
                MixerMiniPreview(scheme)
            }

            item(key = "mode") {
                SectionLabel("Color mode")
                ModeChoice(
                    title = "Palette",
                    hint = "A named set of eight lane colors.",
                    selected = mode == TrackColorMode.Palette,
                    accent = scheme.lane(1),
                    onClick = { onModeChange(TrackColorMode.Palette) }
                )
                SetDivider()
                ModeChoice(
                    title = "Random",
                    hint = "Eight colors drawn at random from the selected palette.",
                    selected = mode == TrackColorMode.Random,
                    accent = scheme.lane(1),
                    onClick = { onModeChange(TrackColorMode.Random) }
                )
                SetDivider()
                ModeChoice(
                    title = "Single color",
                    hint = "One color for every track.",
                    selected = mode == TrackColorMode.Single,
                    accent = scheme.lane(1),
                    onClick = { onModeChange(TrackColorMode.Single) }
                )
            }

            item(key = "palette") {
                SectionLabel("Palette")
                PaletteGrid(
                    selectedId = paletteId,
                    active = mode != TrackColorMode.Single,
                    onSelect = onPaletteChange
                )
                SetDivider()
                SettingRow(
                    title = "Surprise me",
                    hint = "Re-draws the random colors from the selected palette.",
                    onClick = onRandomize
                ) {
                    SetIcon(SetIconKind.Reset, SetColors.Soft, Modifier.size(17.dp))
                }
            }

            if (mode == TrackColorMode.Single) {
                item(key = "single") {
                    SectionLabel("Track color")
                    SingleColorPicker(
                        selected = singleColor,
                        onSelect = onSingleColorChange
                    )
                }
            }

            item(key = "dynamic") {
                SectionLabel("Animation")
                SettingRow(
                    title = "Dynamic colors",
                    hint = "Lets the lane colors drift through the spectrum, very slowly. " +
                        "The movement is imperceptible; turn it off to keep them fixed."
                ) {
                    SetSwitch(dynamic, onDynamicChange)
                }
            }
        }
    }
}

@Composable
private fun TrackColorsHeader(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(white(0.06f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { SetIcon(SetIconKind.Back, SetColors.Soft, Modifier.size(18.dp)) }
        Column(Modifier.weight(1f)) {
            Text("Track colors", style = setText(17.sp, FontWeight.Bold, SetColors.Ink), maxLines = 1)
            Text("Per-lane color scheme", style = setText(11.sp, color = SetColors.Dim), maxLines = 1)
        }
    }
}

/**
 * A miniature of the mixer: 8 lane strips with a name plate, a fader slot and a
 * meter, plus the master strip on the right. It is drawn from the resolved
 * scheme, so it always matches the real mixer.
 */
@Composable
private fun MixerMiniPreview(scheme: TrackColorScheme) {
    val lanes = scheme.lanes
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1B2331), Color(0xFF141A24))))
            .border(1.dp, white(0.06f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lanes.forEachIndexed { i, color ->
            MiniStrip(
                index = i + 1,
                color = color,
                isMaster = false,
                modifier = Modifier.weight(1f)
            )
        }
        MiniStrip(index = 0, color = scheme.master, isMaster = true, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MiniStrip(index: Int, color: Color, isMaster: Boolean, modifier: Modifier) {
    // The fader cap sits at a fixed spot: the preview shows colour, not level.
    val capFraction = if (isMaster) 0.42f else 0.30f + (index % 4) * 0.10f
    Column(
        modifier
            .fillMaxHeight()
            .height(96.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF20283A))
            .drawBehind {
                // Aura at the foot, like the real strip.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 1.05f),
                        radius = size.height * 0.7f
                    )
                )
            }
            .padding(horizontal = 3.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Name plate: a small bar of the lane colour.
        Box(
            Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        // Fader slot with the cap.
        Box(
            Modifier
                .width(4.dp)
                .weight(1f)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF0E141E))
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .align(Alignment.TopCenter)
                    .padding(top = 0.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFE6EAF3))
                )
            }
        }
        // Meter: a short bar of the lane colour at the foot.
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF0E141E))
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f + (index % 5) * 0.09f)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

/** One selectable mode: a leading colour dot, the label, the hint, and a check. */
@Composable
private fun ModeChoice(
    title: String,
    hint: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .tapScale(0.99f, onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(accent)
                .border(1.dp, white(0.18f), CircleShape)
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = setText(14.5.sp, FontWeight.Bold, SetColors.Ink), maxLines = 1)
            Text(hint, style = setText(11.5.sp, color = SetColors.Dim), maxLines = 2)
        }
        RadioMark(selected)
    }
}

/** The Settings radio: an outlined circle, filled when selected. */
@Composable
private fun RadioMark(selected: Boolean) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) SetColors.Accent else Color.Transparent)
            .border(1.5.dp, if (selected) SetColors.Accent else white(0.20f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(SetColors.AccentText))
        }
    }
}

/**
 * Palette cards, 2 per row. Each shows the eight colours as a strip. The selected
 * card gets an accent border; when the mode is Single the grid dims but stays
 * tappable, and a tap switches back to the Palette mode.
 */
@Composable
private fun PaletteGrid(
    selectedId: String,
    active: Boolean,
    onSelect: (String) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TrackPalettes.all.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    PaletteCard(
                        palette = p,
                        selected = active && p.id == selectedId,
                        onClick = { onSelect(p.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PaletteCard(
    palette: TrackPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .tapScale(0.97f, onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A2230))
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) SetColors.Accent else white(0.07f),
                RoundedCornerShape(12.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(5.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            palette.colors.forEach { c ->
                Box(Modifier.weight(1f).fillMaxHeight().background(c))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                palette.name,
                style = setText(12.5.sp, FontWeight.Bold, if (selected) SetColors.Ink else SetColors.Soft),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) SetIcon(SetIconKind.Chevron, SetColors.Accent, Modifier.size(14.dp))
        }
    }
}

/** A row of swatches for the single-colour mode, plus the current value. */
@Composable
private fun SingleColorPicker(selected: Color, onSelect: (Color) -> Unit) {
    val swatches = remember { SINGLE_COLORS }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A2230))
                .border(1.dp, white(0.07f), RoundedCornerShape(12.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(selected)
                    .border(1.dp, white(0.18f), CircleShape)
            )
            Text("Every track uses this color.", style = setText(12.sp, color = SetColors.Dim))
        }
        // 8 swatches per row, two rows.
        swatches.chunked(8).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    val isSel = c.toArgb() == selected.toArgb()
                    Box(
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .tapScale(0.92f, { onSelect(c) })
                            .clip(RoundedCornerShape(9.dp))
                            .background(c)
                            .border(
                                if (isSel) 2.dp else 1.dp,
                                if (isSel) SetColors.Ink else white(0.10f),
                                RoundedCornerShape(9.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSel) {
                            Box(
                                Modifier.size(10.dp).clip(CircleShape)
                                    .background(SetColors.AccentText)
                            )
                        }
                    }
                }
                repeat(8 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 16 readable single colours: the 8 lane defaults, the 8 hue anchors. */
private val SINGLE_COLORS: List<Color> = listOf(
    Color(0xFFE58929), Color(0xFF36B37E), Color(0xFF3B82F6), Color(0xFF9333EA),
    Color(0xFFEC4899), Color(0xFF14B8A6), Color(0xFFF59E0B), Color(0xFF6366F1),
    Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFEAB308), Color(0xFF22C55E),
    Color(0xFF0EA5E9), Color(0xFF8FB2E8), Color(0xFFCBD5E1), Color(0xFF22D3EE)
)
