package com.aetherkeys.sheets.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.aetherkeys.sheets.model.Notation
import com.aetherkeys.sheets.model.Theory
import com.aetherkeys.sheets.model.PassingDisplay
import com.aetherkeys.sheets.model.Pagination
import com.aetherkeys.sheets.model.SheetsSettings
import com.aetherkeys.sheets.ui.components.ChipFlow
import com.aetherkeys.sheets.ui.components.GlyphButton
import com.aetherkeys.sheets.ui.components.OverlineLabel
import com.aetherkeys.sheets.ui.components.Pill
import com.aetherkeys.sheets.ui.components.SheetsIcons
import com.aetherkeys.sheets.ui.components.SwitchRow
import com.aetherkeys.sheets.ui.components.ToolChip
import com.aetherkeys.sheets.ui.theme.SheetsColors
import com.aetherkeys.sheets.ui.theme.SheetsFonts
import com.aetherkeys.sheets.ui.theme.SheetsPageBackground
import com.aetherkeys.sheets.ui.theme.SheetsSpacing
import com.aetherkeys.sheets.ui.theme.SheetsType
import com.aetherkeys.sheets.vm.SettingsActions

/**
 * Sheets' own settings (not the app's global ones). Everything applies immediately.
 *
 * The screen follows the current library: the "Notation" group explains what the
 * change does to what is on screen, and "Default key" spells its chips the way the
 * key signature asks for, so the list and the sheet never disagree.
 */
@Composable
fun SheetsSettingsScreen(
    settings: SheetsSettings,
    actions: SettingsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SheetsPageBackground(modifier) {
        Column(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GlyphButton(SheetsIcons.Back, onBack, contentDescription = "Back")
                Text("Sheets settings", style = SheetsType.Title, color = SheetsColors.TextPrimary)
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Group("NOTATION") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Nashville", selected = settings.notation == Notation.Nashville) { actions.update { it.copy(notation = Notation.Nashville) } }
                        Pill("Standard", selected = settings.notation == Notation.Standard) { actions.update { it.copy(notation = Notation.Standard) } }
                    }
                    Hint(
                        if (settings.notation == Notation.Nashville)
                            "Chords read as scale degrees (1, 4, 5) — the same chart transposes for free."
                        else
                            "Chords read as note names (C, F, G). Spelling follows the key signature: " +
                                "flats in ${Theory.noteName(settings.defaultKeyPc, settings.defaultKeyPc)}-style keys, sharps elsewhere.",
                    )
                }
                Group("PASSING CHORDS") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Auto (5/5 when detected)", selected = settings.passing == PassingDisplay.Auto) { actions.update { it.copy(passing = PassingDisplay.Auto) } }
                        Pill("Raw degree", selected = settings.passing == PassingDisplay.Raw) { actions.update { it.copy(passing = PassingDisplay.Raw) } }
                    }
                    Hint("A passing chord is written as two chords in one bar (5/5) when Auto recognises it.")
                }
                Group("DEFAULT KEY") {
                    ChipFlow {
                        // Spelled per key signature, so picking "Bb" does not then show "A#"
                        // on every new sheet.
                        for (pc in 0 until 12) {
                            ToolChip(
                                Theory.noteName(pc, pc),
                                { actions.update { it.copy(defaultKeyPc = pc) } },
                                selected = settings.defaultKeyPc == pc,
                            )
                        }
                    }
                    Hint("New sheets start in this key. Existing sheets keep their own.")
                }
                Group("DEFAULT PAGINATION") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Unified", selected = settings.defaultPagination == Pagination.Unified) { actions.update { it.copy(defaultPagination = Pagination.Unified) } }
                        Pill("One page per section", selected = settings.defaultPagination == Pagination.PerSection) { actions.update { it.copy(defaultPagination = Pagination.PerSection) } }
                    }
                }
                Group("DEFAULT CHORD FONT") {
                    ChipFlow { SheetsFonts.Options.forEach { o -> ToolChip(o.label, { actions.update { it.copy(defaultChordFontId = o.id) } }, selected = settings.defaultChordFontId == o.id, family = o.family) } }
                }
                Group("DEFAULT LYRICS FONT") {
                    ChipFlow { SheetsFonts.Options.forEach { o -> ToolChip(o.label, { actions.update { it.copy(defaultLyricsFontId = o.id) } }, selected = settings.defaultLyricsFontId == o.id, family = o.family) } }
                }
                Group("SCENE MODE") {
                    SwitchRow("Lock on open", settings.lockOnOpen, onChange = { v -> actions.update { it.copy(lockOnOpen = v) } })
                    Hint("Locked: the scene is read-only — a tap only opens the section list.")
                    SwitchRow("Reverse swipe direction", settings.swipeReversed, onChange = { v -> actions.update { it.copy(swipeReversed = v) } })
                    Hint("Swiping left goes to the next section. On: swiping right does.")
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
    Column(verticalArrangement = Arrangement.spacedBy(SheetsSpacing.S)) {
        OverlineLabel(title)
        content()
    }
}
