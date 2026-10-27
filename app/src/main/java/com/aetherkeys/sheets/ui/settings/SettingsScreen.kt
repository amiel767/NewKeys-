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
import com.aetherkeys.sheets.model.ExportFormat
import com.aetherkeys.sheets.model.Notation
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

private val KEY_NAMES = listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

/** Réglages propres à Sheets (pas les réglages globaux de l'app). Tout est appliqué immédiatement. */
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
                }
                Group("PASSING CHORDS") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Auto (5/5 when detected)", selected = settings.passing == PassingDisplay.Auto) { actions.update { it.copy(passing = PassingDisplay.Auto) } }
                        Pill("Raw degree", selected = settings.passing == PassingDisplay.Raw) { actions.update { it.copy(passing = PassingDisplay.Raw) } }
                    }
                }
                Group("DEFAULT KEY") {
                    ChipFlow { KEY_NAMES.forEachIndexed { pc, n -> ToolChip(n, { actions.update { it.copy(defaultKeyPc = pc) } }, selected = settings.defaultKeyPc == pc) } }
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
                    SwitchRow("Reverse swipe direction", settings.swipeReversed, onChange = { v -> actions.update { it.copy(swipeReversed = v) } })
                }
                Group("EXPORT") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportFormat.values().forEach { f -> Pill(f.name, selected = settings.exportFormat == f) { actions.update { it.copy(exportFormat = f) } } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SheetsSpacing.S)) {
        OverlineLabel(title)
        content()
    }
}
