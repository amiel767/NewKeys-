package com.aetherkeys.sheets.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aetherkeys.sheets.model.*
import com.aetherkeys.sheets.ui.components.*
import com.aetherkeys.sheets.ui.theme.*
import com.aetherkeys.sheets.vm.HomeActions
import com.aetherkeys.sheets.vm.LibraryFilter
import com.aetherkeys.sheets.vm.LibraryUiState
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HomeScreen(
    state: LibraryUiState,
    actions: HomeActions,
    bottomInset: Dp,
    onBackToMixer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEditor: (String) -> Unit,
    onFloatOverMixer: (Sheet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    val visible = state.visibleSheets()
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var menuFor by remember { mutableStateOf<Sheet?>(null) }
    var newSetlist by remember { mutableStateOf(false) }

    SheetsPageBackground(modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HomeHeader(state, actions, onBackToMixer, onOpenSettings)
            SetlistRow(state, actions, onNewSetlist = { newSetlist = true })
            if (visible.isEmpty() && !state.loading) {
                EmptyState(hasQuery = state.query.isNotBlank() || state.filter != LibraryFilter.All) { actions.createSheet(onOpenEditor) }
            } else {
                BoxWithConstraints(Modifier.weight(1f)) {
                    val cols = when { maxWidth < 360.dp -> 1; maxWidth < 600.dp -> 2; else -> 3 }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(cols),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp + bottomInset),
                    ) {
                        items(visible, key = { it.id }) { sheet ->
                            SheetCard(
                                sheet = sheet, settings = settings,
                                modifier = Modifier,
                                onOpen = { onOpenEditor(sheet.id) },
                                onFloat = { onFloatOverMixer(sheet) },
                                onMenu = { menuFor = sheet },
                            )
                        }
                    }
                }
            }
        }

        // Bouton flottant de création (bas droite, au pouce)
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 34.dp, bottom = 30.dp + bottomInset)
                .shadow(8.dp, CircleShape).size(56.dp).clip(CircleShape).background(SheetsColors.Accent)
                .clickable { actions.createSheet(onOpenEditor) },
            contentAlignment = Alignment.Center,
        ) { Icon(SheetsIcons.Plus, contentDescription = "New sheet", tint = SheetsColors.AccentText, modifier = Modifier.size(26.dp)) }

    }

    menuFor?.let { s -> CardMenuDialog(s, state, actions, onDismiss = { menuFor = null }) }
    if (newSetlist) NameDialog("New setlist", onDismiss = { newSetlist = false }) { actions.createSetlist(it); newSetlist = false }
}

@Composable
private fun HomeHeader(state: LibraryUiState, actions: HomeActions, onBackToMixer: () -> Unit, onOpenSettings: () -> Unit) {
    val notation = state.settings.notation
    Box(Modifier.fillMaxWidth().height(44.dp)) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MixerIconButton(onBackToMixer)
            SearchField(state.query, actions::setQuery, Modifier.width(220.dp))
        }
        Text("Sheets", style = SheetsType.Title, color = Color.White, modifier = Modifier.align(Alignment.Center))
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.clip(RoundedCornerShape(SheetsRadius.Chip)).background(SheetsColors.Chip).padding(4.dp)) {
                Pill("1-7", selected = notation == Notation.Nashville) { actions.setNotation(Notation.Nashville) }
                Pill("A-G", selected = notation == Notation.Standard) { actions.setNotation(Notation.Standard) }
            }
            GlyphButton(SheetsIcons.Settings, onOpenSettings, contentDescription = "Sheets settings")
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.height(40.dp).clip(RoundedCornerShape(SheetsRadius.Chip)).background(SheetsColors.Chip).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        if (query.isEmpty()) Text("Search", style = SheetsType.Body, color = SheetsColors.TextMuted)
        BasicTextField(query, onChange, singleLine = true, textStyle = SheetsType.Body.copy(color = SheetsColors.TextPrimary), cursorBrush = SolidColor(SheetsColors.Accent), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SetlistRow(state: LibraryUiState, actions: HomeActions, onNewSetlist: () -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Pill("All · ${state.sheets.size}", selected = state.filter == LibraryFilter.All) { actions.setFilter(LibraryFilter.All) } }
        item { Pill("★ Favorites", selected = state.filter == LibraryFilter.Favorites) { actions.setFilter(LibraryFilter.Favorites) } }
        items(state.library.setlists.size) { i ->
            val sl = state.library.setlists[i]
            Pill(sl.name, selected = (state.filter as? LibraryFilter.OfSetlist)?.id == sl.id) { actions.setFilter(LibraryFilter.OfSetlist(sl.id)) }
        }
        item { Pill("New setlist") { onNewSetlist() } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetCard(
    sheet: Sheet, settings: SheetsSettings, modifier: Modifier,
    onOpen: () -> Unit, onFloat: () -> Unit, onMenu: () -> Unit,
) {
    val base = sheet.colorArgb.toColor()
    val ink = onColorFor(base)
    val family = SheetsFonts.family(sheet.chordFontId)
    val dateFmt = remember { SimpleDateFormat("MMM d", Locale.ENGLISH) }
    val firstSection = sheet.sections.firstOrNull { it.bars.isNotEmpty() }
    val key = firstSection?.let { sheet.keyFor(it) } ?: sheet.keyPc
    val preview = firstSection?.bars?.take(4)?.joinToString("   ") { Theory.barText(it, key, settings.notation, settings.passing) }.orEmpty()
    Column(
        modifier.height(150.dp).clip(RoundedCornerShape(SheetsRadius.Card))
            .background(Brush.linearGradient(listOf(base, lerp(base, Color.Black, 0.35f))))
            .combinedClickable(onClick = onOpen, onDoubleClick = onFloat, onLongClick = onMenu)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(sheet.title, style = SheetsType.chord(family, 26f), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (sheet.favorite) Icon(SheetsIcons.Star, contentDescription = "Favorite", tint = SheetsColors.Warning, modifier = Modifier.padding(horizontal = 6.dp).size(16.dp))
            Box(Modifier.clip(RoundedCornerShape(9.dp)).background(ink.copy(alpha = 0.18f)).padding(horizontal = 11.dp, vertical = 5.dp)) {
                Text(Theory.KEY_NAMES[sheet.keyPc], style = SheetsType.Label, color = ink)
            }
        }
        Text(preview, style = SheetsType.chord(family, 28f), color = ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${sheet.bpm} BPM · ${sheet.beatsPerBar}/${sheet.beatUnit}", style = SheetsType.Caption, color = ink.copy(alpha = 0.75f))
            Text(dateFmt.format(sheet.updatedAt), style = SheetsType.Caption, color = ink.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun EmptyState(hasQuery: Boolean, onCreate: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (hasQuery) "No sheet matches" else "No sheets yet", style = SheetsType.Title, color = Color.White)
        if (!hasQuery) {
            Text("Create your first sheet to start writing.", style = SheetsType.Body, color = SheetsColors.TextMuted, textAlign = TextAlign.Center)
            Pill("New sheet", selected = true, onClick = onCreate)
        }
    }
}

private enum class MenuPage { Main, Setlists, ConfirmDelete }

@Composable
private fun CardMenuDialog(sheet: Sheet, state: LibraryUiState, actions: HomeActions, onDismiss: () -> Unit) {
    var page by remember { mutableStateOf(MenuPage.Main) }
    val setlistFilter = (state.filter as? LibraryFilter.OfSetlist)?.id
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(18.dp)).background(SheetsColors.Surface).padding(8.dp).fillMaxWidth()) {
            when (page) {
                MenuPage.Main -> {
                    MenuItem(if (sheet.favorite) "Remove from favorites" else "Add to favorites") { actions.toggleFavorite(sheet.id); onDismiss() }
                    MenuItem("Duplicate") { actions.duplicate(sheet.id); onDismiss() }
                    MenuItem("Add to setlist") { page = MenuPage.Setlists }
                    if (setlistFilter != null) {
                        MenuItem("Move earlier") { actions.moveInSetlist(setlistFilter, sheet.id, -1); onDismiss() }
                        MenuItem("Move later") { actions.moveInSetlist(setlistFilter, sheet.id, 1); onDismiss() }
                        MenuItem("Remove from this setlist") { actions.removeFromSetlist(sheet.id, setlistFilter); onDismiss() }
                    }
                    MenuItem("Delete", danger = true) { page = MenuPage.ConfirmDelete }
                }
                MenuPage.Setlists -> {
                    if (state.library.setlists.isEmpty()) Text("Create a setlist first (+ in the filter row).", style = SheetsType.Body, color = SheetsColors.TextMuted, modifier = Modifier.padding(14.dp))
                    state.library.setlists.forEach { sl -> MenuItem(sl.name) { actions.addToSetlist(sheet.id, sl.id); onDismiss() } }
                }
                MenuPage.ConfirmDelete -> {
                    Text("Delete “${sheet.title}”? This cannot be undone.", style = SheetsType.Body, color = SheetsColors.TextPrimary, modifier = Modifier.padding(14.dp))
                    MenuItem("Delete", danger = true) { actions.delete(sheet.id); onDismiss() }
                    MenuItem("Cancel") { onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text, style = SheetsType.Body, color = if (danger) Color(0xFFF87171) else SheetsColors.TextPrimary,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
    )
}

@Composable
private fun NameDialog(title: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(18.dp)).background(SheetsColors.Surface).padding(18.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = SheetsType.Label, color = Color.White)
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SheetsColors.SurfaceChip).padding(12.dp)) {
                BasicTextField(text, { text = it }, singleLine = true, textStyle = SheetsType.Body.copy(color = SheetsColors.TextPrimary), cursorBrush = SolidColor(SheetsColors.Accent), modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Cancel", onClick = onDismiss)
                Pill("Create", selected = true) { onDone(text) }
            }
        }
    }
}
