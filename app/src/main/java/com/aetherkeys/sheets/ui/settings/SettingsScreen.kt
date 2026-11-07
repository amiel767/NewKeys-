package com.aetherkeys.sheets.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aetherkeys.sheets.model.Notation
import com.aetherkeys.sheets.model.Pagination
import com.aetherkeys.sheets.model.PassingDisplay
import com.aetherkeys.sheets.model.SheetsSettings
import com.aetherkeys.sheets.model.Theory
import com.aetherkeys.sheets.ui.components.ChipFlow
import com.aetherkeys.sheets.ui.components.OverlineLabel
import com.aetherkeys.sheets.ui.components.SegmentedToggle
import com.aetherkeys.sheets.ui.components.SheetsIcons
import com.aetherkeys.sheets.ui.components.SwitchRow
import com.aetherkeys.sheets.ui.components.ToolChip
import com.aetherkeys.sheets.ui.theme.SheetsColors
import com.aetherkeys.sheets.ui.theme.SheetsFonts
import com.aetherkeys.sheets.ui.theme.SheetsSpacing
import com.aetherkeys.sheets.ui.theme.SheetsType
import com.aetherkeys.sheets.vm.SettingsActions

/**
 * Sheets' own settings: a panel anchored top-right over the library, not a screen of its
 * own. It follows the Setlists page settings, the same kind of control at the same size,
 * so the library stays visible behind it.
 *
 * The "Notation" group explains what the change does to what is on screen, and "Default
 * key" spells its chips the way the key signature asks for, so the list and the sheet
 * never disagree.
 */
@Composable
fun SheetsSettingsScreen(
    settings: SheetsSettings,
    actions: SettingsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onBack() } },
    ) {
        Box(Modifier.align(Alignment.TopEnd).padding(top = 60.dp, end = 18.dp).sheetsAppear()) {
            Panel(settings, actions, onBack)
        }
    }
}

@Composable
private fun Panel(settings: SheetsSettings, actions: SettingsActions, onClose: () -> Unit) {
    Column(
        Modifier
            .widthIn(max = 540.dp)
            .fillMaxWidth()
            .sheetsPanel(RoundedCornerShape(18.dp))
            // Swallow taps on the panel itself, so only the outside closes it.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Sheets settings",
                style = TextStyle(
                    fontFamily = SheetsFonts.PlusJakartaSans,
                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = SheetsColors.TextPrimary,
                ),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f))
                    .pointerInput(Unit) { detectTapGestures { onClose() } },
                contentAlignment = Alignment.Center,
            ) {
                Icon(SheetsIcons.Close, contentDescription = "Close", tint = SheetsColors.TextMuted, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f)) {
                Group("Notation") {
                    SegmentedToggle(
                        options = listOf("Nashville", "Standard"),
                        selectedIndex = if (settings.notation == Notation.Nashville) 0 else 1,
                        onSelect = { actions.update { s -> s.copy(notation = if (it == 0) Notation.Nashville else Notation.Standard) } },
                        height = 34.dp,
                    )
                    Hint(
                        if (settings.notation == Notation.Nashville)
                            "Chords read as scale degrees (1, 4, 5) — the same chart transposes for free."
                        else
                            "Chords read as note names (C, F, G), spelled to the key signature.",
                    )
                }
                Group("Passing chords") {
                    SegmentedToggle(
                        options = listOf("Auto", "Raw degree"),
                        selectedIndex = if (settings.passing == PassingDisplay.Auto) 0 else 1,
                        onSelect = { actions.update { s -> s.copy(passing = if (it == 0) PassingDisplay.Auto else PassingDisplay.Raw) } },
                        height = 34.dp,
                    )
                    Hint("Written as two chords in one bar (5/5) when Auto recognises it.")
                }
                Group("Pagination") {
                    SegmentedToggle(
                        options = listOf("Unified", "Per section"),
                        selectedIndex = if (settings.defaultPagination == Pagination.Unified) 0 else 1,
                        onSelect = { actions.update { s -> s.copy(defaultPagination = if (it == 0) Pagination.Unified else Pagination.PerSection) } },
                        height = 34.dp,
                    )
                }
                Group("Scene mode") {
                    SwitchRow("Lock on open", settings.lockOnOpen, onChange = { v -> actions.update { it.copy(lockOnOpen = v) } })
                    SwitchRow("Reverse swipe", settings.swipeReversed, onChange = { v -> actions.update { it.copy(swipeReversed = v) } })
                }
            }
            Column(Modifier.weight(1f)) {
                Group("Default key") {
                    ChipFlow {
                        // Spelled per key signature, so picking "Bb" does not then show "A#"
                        // on every new sheet.
                        for (pc in 0 until 12) {
                            ToolChip(
                                Theory.noteName(pc, pc),
                                { actions.update { s -> s.copy(defaultKeyPc = pc) } },
                                selected = settings.defaultKeyPc == pc,
                            )
                        }
                    }
                    Hint("New sheets start here. Existing sheets keep their own key.")
                }
                Group("Chord font") {
                    ChipFlow {
                        SheetsFonts.Options.forEach { o ->
                            ToolChip(o.label, { actions.update { it.copy(defaultChordFontId = o.id) } }, selected = settings.defaultChordFontId == o.id, family = o.family)
                        }
                    }
                }
                Group("Lyrics font") {
                    ChipFlow {
                        SheetsFonts.Options.forEach { o ->
                            ToolChip(o.label, { actions.update { it.copy(defaultLyricsFontId = o.id) } }, selected = settings.defaultLyricsFontId == o.id, family = o.family)
                        }
                    }
                }
                Box(
                    Modifier.height(30.dp).pointerInput(Unit) { detectTapGestures { actions.update { SheetsSettings() } } },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text("Reset settings", style = SheetsType.Label, color = SheetsColors.TextMuted)
                }
            }
        }
    }
}

/** One line of explanation under a control: what the choice actually does. */
@Composable
private fun Hint(text: String) {
    Text(text, style = SheetsType.Caption, color = SheetsColors.TextMuted)
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = SheetsSpacing.XS),
        verticalArrangement = Arrangement.spacedBy(SheetsSpacing.XS),
    ) {
        OverlineLabel(title)
        content()
    }
}

/** The panel material: raised gradient, thin light rim, drop shadow. Setlists' own panel. */
private fun Modifier.sheetsPanel(shape: RoundedCornerShape): Modifier = this
    .shadow(14.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
    .clip(shape)
    .background(Brush.verticalGradient(listOf(SheetsColors.SurfaceRaised, SheetsColors.Surface)))
    .border(1.dp, Color.White.copy(alpha = 0.10f), shape)

/** Fade and a small rise on first composition. */
@Composable
private fun Modifier.sheetsAppear(): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
    return this.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 8.dp.toPx()
        val s = 0.96f + 0.04f * p.value
        scaleX = s; scaleY = s
    }
}
