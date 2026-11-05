package com.aetherkeys.sheets.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.soundstage.mixer.R
import com.aetherkeys.sheets.model.*
import com.aetherkeys.sheets.ui.components.*
import com.aetherkeys.sheets.ui.theme.*
import com.aetherkeys.sheets.vm.HomeActions
import com.aetherkeys.sheets.vm.LibraryFilter
import com.aetherkeys.sheets.vm.LibraryUiState

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
    val sets = state.visibleSetlists()
    val inSet = state.filter as? LibraryFilter.OfSetlist
    var menuFor by remember { mutableStateOf<Sheet?>(null) }
    var setMenuFor by remember { mutableStateOf<Setlist?>(null) }
    var newSetlist by remember { mutableStateOf(false) }

    SheetsPageBackground(modifier) {
        Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 20.dp)) {
            HomeHeader(state, actions, onBackToMixer, onOpenSettings)
            Spacer(Modifier.height(14.dp))
            FilterRow(state, actions, onNewSet = { newSetlist = true })

            // Les Sets vivent en haut de la page, pas derriere un filtre : c'est le
            // premier niveau de la bibliotheque. Meme hauteur de carte que Setlists.
            if (sets.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SetRow(
                    sets = sets,
                    sheets = state.sheets,
                    settings = settings,
                    selectedId = inSet?.id,
                    onOpen = { actions.setFilter(LibraryFilter.OfSetlist(it)) },
                    onMenu = { setMenuFor = it },
                )
            }

            Spacer(Modifier.height(18.dp))
            ListHeader(
                state = state,
                count = visible.size,
                inSetName = inSet?.let { f -> state.library.setlists.firstOrNull { it.id == f.id }?.name },
                onAll = { actions.setFilter(LibraryFilter.All) },
            )
            Spacer(Modifier.height(8.dp))

            if (visible.isEmpty() && !state.loading) {
                EmptyState(state = state, onCreate = { actions.createSheet(onOpenEditor) }, onNewSet = { newSetlist = true })
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 104.dp + bottomInset),
                ) {
                    items(visible, key = { it.id }) { sheet ->
                        SheetRow(
                            sheet = sheet,
                            settings = settings,
                            setCount = state.setlistsContaining(sheet.id).size,
                            onOpen = { onOpenEditor(sheet.id) },
                            onFloat = { onFloatOverMixer(sheet) },
                            onMenu = { menuFor = sheet },
                        )
                    }
                }
            }
        }

        // Bouton flottant de creation (bas droite, au pouce)
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 34.dp, bottom = 30.dp + bottomInset)
                .shadow(8.dp, CircleShape).size(56.dp).clip(CircleShape).background(SheetsColors.Accent)
                .clickable { actions.createSheet(onOpenEditor) },
            contentAlignment = Alignment.Center,
        ) { Icon(SheetsIcons.Plus, contentDescription = "New sheet", tint = SheetsColors.AccentText, modifier = Modifier.size(26.dp)) }
    }

    menuFor?.let { s -> CardMenuDialog(s, state, actions, onDismiss = { menuFor = null }) }
    setMenuFor?.let { sl -> SetMenuDialog(sl, actions, onDismiss = { setMenuFor = null }) }
    if (newSetlist) NameDialog("New Set", "Set name", onDismiss = { newSetlist = false }) { actions.createSetlist(it); newSetlist = false }
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
            SegmentedToggle(
                options = listOf("1-7", "A-G"),
                selectedIndex = if (notation == Notation.Nashville) 0 else 1,
                onSelect = { actions.setNotation(if (it == 0) Notation.Nashville else Notation.Standard) },
                modifier = Modifier.width(132.dp),
            )
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

/**
 * Two doors only: everything, or favorites. Sets are deliberately NOT repeated here —
 * they are already cards right below, and the same name in two places reads as a bug.
 * You enter a Set through its card and leave it through "← All".
 */
@Composable
private fun FilterRow(state: LibraryUiState, actions: HomeActions, onNewSet: () -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Pill("All · ${state.sheets.size}", selected = state.filter == LibraryFilter.All) { actions.setFilter(LibraryFilter.All) } }
        item { Pill("★ Favorites", selected = state.filter == LibraryFilter.Favorites) { actions.setFilter(LibraryFilter.Favorites) } }
        item { Pill("+ Set") { onNewSet() } }
    }
}

// ────────────────────────────────────────────────────────────────────────────────────────
// Rangee des Sets : cartes de la taille de celles de la page Setlists (96 dp).
// Same material as the sheets (gradient + spine + shadow), not glass.
// ────────────────────────────────────────────────────────────────────────────────────────

private val SetCardWidth = 252.dp
private val SetCardHeight = 96.dp

@Composable
private fun SetRow(
    sets: List<Setlist>,
    sheets: List<Sheet>,
    settings: SheetsSettings,
    selectedId: String?,
    onOpen: (String) -> Unit,
    onMenu: (Setlist) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(sets, key = { it.id }) { sl ->
            SetChip(
                setlist = sl,
                // The Set's sheets, in play order: feeds the count and the preview.
                sheets = sl.sheetIds.mapNotNull { id -> sheets.firstOrNull { it.id == id } },
                settings = settings,
                selected = sl.id == selectedId,
                onOpen = { onOpen(sl.id) },
                onMenu = { onMenu(sl) },
            )
        }
    }
}

/**
 * Set card: a folder of sheets, at the Setlists page gauge (96 dp tall). The colour is
 * the Set's own; the content announces what is inside — the first sheet titles and a
 * preview of their chords. [LibraryTexture] gives it the same material as a sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SetChip(
    setlist: Setlist,
    sheets: List<Sheet>,
    settings: SheetsSettings,
    selected: Boolean,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.55f, stiffness = 900f), label = "setPress")
    val base = (setlist.colorArgb ?: PalettePresets.Presets.first()).toColor()
    val ink = onColorFor(base)
    val shape = RoundedCornerShape(16.dp)
    val first = sheets.firstOrNull()
    val firstSection = first?.sections?.firstOrNull { it.bars.isNotEmpty() }
    val key = firstSection?.let { first.keyFor(it) } ?: first?.keyPc ?: 0
    val preview = firstSection?.bars?.take(4)
        ?.joinToString("  ") { Theory.barText(it, key, settings.notation, settings.passing) }.orEmpty()

    Box(
        Modifier.testTag("set-card")
            .width(SetCardWidth).height(SetCardHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (pressed) 5.dp else 12.dp, shape, clip = false)
            .clip(shape)
            .background(Brush.linearGradient(listOf(base, lerp(base, Color.Black, 0.32f))))
            .combinedClickable(
                interactionSource = source, indication = null,
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onOpen() },
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onMenu() },
            ),
    ) {
        LibraryTexture(ink)
        // Spine: the same landmark as a sheet, so the colour reads at a glance.
        Box(Modifier.width(4.dp).fillMaxHeight().background(ink.copy(alpha = 0.5f)))
        Column(
            Modifier.fillMaxSize().padding(start = 16.dp, end = 12.dp, top = 11.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(setlist.name, style = SheetsType.CardTitle, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (selected) Text("OPEN", style = SheetsType.Overline.copy(fontSize = 8.sp), color = ink.copy(alpha = 0.85f))
            }
            Text(
                if (sheets.isEmpty()) "No sheets yet"
                else sheets.take(2).joinToString(" · ") { it.title },
                style = SheetsType.CardMeta.copy(fontSize = 11.sp), color = ink.copy(alpha = 0.86f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (preview.isNotEmpty()) {
                    Text(
                        preview,
                        style = SheetsType.chord(SheetsFonts.family(first?.chordFontId ?: FontIds.ArchitectsDaughter), 14f),
                        color = ink.copy(alpha = 0.92f), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f),
                    )
                } else Spacer(Modifier.weight(1f))
                Text(
                    "${sheets.size} sheet${if (sheets.size > 1) "s" else ""}",
                    style = SheetsType.CardMeta.copy(fontSize = 10.sp), color = ink.copy(alpha = 0.75f),
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────────────
// The list below: one sheet per row, at the Setlists row gauge (58 dp). The chord preview
// gets real room, so the row reads as a line of music rather than a table row.
// ────────────────────────────────────────────────────────────────────────────────────────

private val SheetRowHeight = 58.dp

@Composable
private fun ListHeader(state: LibraryUiState, count: Int, inSetName: String?, onAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (inSetName != null) {
            Box(
                Modifier.clip(RoundedCornerShape(SheetsRadius.Chip)).background(SheetsColors.Chip)
                    .clickable { onAll() }.padding(horizontal = 10.dp, vertical = 3.dp),
            ) { Text("← All", style = SheetsType.Caption, color = SheetsColors.TextPrimary) }
        }
        OverlineLabel(
            when {
                inSetName != null -> inSetName
                state.filter == LibraryFilter.Favorites -> "Favorites"
                state.query.isNotBlank() -> "Results"
                else -> "Recently edited"
            },
        )
        Text("$count", style = SheetsType.Overline, color = SheetsColors.TextMuted)
        Spacer(Modifier.weight(1f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetRow(
    sheet: Sheet,
    settings: SheetsSettings,
    setCount: Int,
    onOpen: () -> Unit,
    onFloat: () -> Unit,
    onMenu: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val base = sheet.colorArgb.toColor()
    val ink = onColorFor(base)
    val shape = RoundedCornerShape(13.dp)
    val family = SheetsFonts.family(sheet.chordFontId)
    val firstSection = sheet.sections.firstOrNull { it.bars.isNotEmpty() }
    val key = firstSection?.let { sheet.keyFor(it) } ?: sheet.keyPc
    // Six bars, not three: the row is wide and tall now, and a six-bar line is what
    // actually tells one sheet from another at a glance.
    val preview = firstSection?.bars?.take(6)
        ?.joinToString("   ") { Theory.barText(it, key, settings.notation, settings.passing) }.orEmpty()
    val keyInk = onColorFor(base)

    Row(
        Modifier.fillMaxWidth().height(SheetRowHeight)
            .graphicsLayer { scaleX = if (pressed) 0.99f else 1f; scaleY = if (pressed) 0.99f else 1f }
            .clip(shape)
            .background(lerp(SheetsColors.Surface, base, 0.18f))
            .border(1.dp, ink.copy(alpha = 0.12f), shape)
            .combinedClickable(
                interactionSource = source, indication = null,
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onOpen() },
                onDoubleClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onFloat() },
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onMenu() },
            )
            .padding(start = 13.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Colour tab: the row's only tint, the same landmark as a Setlists dot.
        Box(Modifier.width(4.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(base))
        Text(
            sheet.title, style = SheetsType.RowTitle, color = SheetsColors.TextPrimary,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(186.dp),
        )
        if (preview.isNotEmpty()) {
            Text(
                preview,
                style = SheetsType.chord(family, 15f), color = SheetsColors.TextPrimary.copy(alpha = 0.86f),
                maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f),
            )
        } else Spacer(Modifier.weight(1f))
        // Key: dark plate, readable on any tint. Spelling follows the key signature.
        Box(Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xE6000000)).padding(horizontal = 9.dp, vertical = 3.dp)) {
            Text(Theory.noteName(key, key), style = SheetsType.CardMeta, color = Color.White)
        }
        Text("${sheet.bpm} BPM", style = SheetsType.CardMeta, color = SheetsColors.TextMuted, modifier = Modifier.width(58.dp))
        Text(
            "${sheet.sections.size} section${if (sheet.sections.size > 1) "s" else ""}",
            style = SheetsType.CardMeta, color = SheetsColors.TextMuted, modifier = Modifier.width(72.dp),
        )
        Box(Modifier.width(16.dp), contentAlignment = Alignment.Center) {
            if (sheet.favorite) Icon(SheetsIcons.Star, contentDescription = "Favorite", tint = SheetsColors.Warning, modifier = Modifier.size(13.dp))
            else if (setCount > 0) Icon(SheetsIcons.Layers, contentDescription = "In a Set", tint = keyInk.copy(alpha = 0.55f), modifier = Modifier.size(13.dp))
        }
        Icon(SheetsIcons.ChevronRight, contentDescription = null, tint = SheetsColors.TextMuted, modifier = Modifier.size(16.dp))
    }
}

/**
 * Card material, shared by the sheet rows and the Set cards: a diagonal light band, a
 * bright top edge and a dark bottom edge. Drawn in code rather than in XML so it can
 * take the ink colour of any tint.
 */
@Composable
private fun BoxScope.LibraryTexture(ink: Color) {
    Box(
        Modifier.matchParentSize().background(
            Brush.linearGradient(
                0f to ink.copy(alpha = 0.13f),
                0.42f to ink.copy(alpha = 0.03f),
                1f to Color.Transparent,
            ),
        ),
    )
    Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.28f)))
    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.22f)))
}

@Composable
private fun EmptyState(state: LibraryUiState, onCreate: () -> Unit, onNewSet: () -> Unit) {
    val searching = state.query.isNotBlank()
    val setsOnly = state.filter == LibraryFilter.Sets
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            when {
                searching -> "No results"
                setsOnly -> "No Sets yet"
                else -> "No sheets yet"
            },
            style = SheetsType.Title, color = Color.White,
        )
        if (!searching) {
            Text(
                if (setsOnly) "A Set gathers the sheets you play together."
                else "Create your first sheet to start writing.",
                style = SheetsType.Body, color = SheetsColors.TextMuted, textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("New sheet", selected = true, onClick = onCreate)
                Pill("New Set", onClick = onNewSet)
            }
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
                    MenuItem(if (state.library.setlists.isEmpty()) "Add to a Set…" else "Add to a Set…") { page = MenuPage.Setlists }
                    if (setlistFilter != null) {
                        MenuItem("Move earlier") { actions.moveInSetlist(setlistFilter, sheet.id, -1); onDismiss() }
                        MenuItem("Move later") { actions.moveInSetlist(setlistFilter, sheet.id, 1); onDismiss() }
                        MenuItem("Remove from this Set", danger = true) { actions.removeFromSetlist(sheet.id, setlistFilter); onDismiss() }
                    }
                    MenuItem("Delete", danger = true) { page = MenuPage.ConfirmDelete }
                }
                MenuPage.Setlists -> {
                    if (state.library.setlists.isEmpty()) {
                        Text("Create a Set first (+ Set in the filter row).", style = SheetsType.Body, color = SheetsColors.TextMuted, modifier = Modifier.padding(14.dp))
                    } else {
                        Text("Add to a Set", style = SheetsType.Overline, color = SheetsColors.TextMuted, modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 6.dp))
                        state.library.setlists.forEach { sl ->
                            val inSet = sheet.id in sl.sheetIds
                            MenuItem(if (inSet) "✓ ${sl.name}" else sl.name) {
                                if (inSet) actions.removeFromSetlist(sheet.id, sl.id) else actions.addToSetlist(sheet.id, sl.id)
                                onDismiss()
                            }
                        }
                    }
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

private enum class SetMenuPage { Main, Rename, Color, ConfirmDelete }

/**
 * Menu d'un Set : renommer, recolorer, supprimer. Supprimer un Set ne supprime
 * deletes no sheet — it only removes the Set from the library.
 */
@Composable
private fun SetMenuDialog(setlist: Setlist, actions: HomeActions, onDismiss: () -> Unit) {
    var page by remember { mutableStateOf(SetMenuPage.Main) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(18.dp)).background(SheetsColors.Surface).padding(8.dp).fillMaxWidth()) {
            when (page) {
                SetMenuPage.Main -> {
                    MenuItem("Open this Set") { actions.setFilter(LibraryFilter.OfSetlist(setlist.id)); onDismiss() }
                    MenuItem("Rename") { page = SetMenuPage.Rename }
                    MenuItem("Colour") { page = SetMenuPage.Color }
                    MenuItem("Delete Set", danger = true) { page = SetMenuPage.ConfirmDelete }
                }
                SetMenuPage.Rename -> {
                    Text("Rename Set", style = SheetsType.Overline, color = SheetsColors.TextMuted, modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 6.dp))
                    NameField(initial = setlist.name, placeholder = "Set name", modifier = Modifier.padding(horizontal = 12.dp)) { v ->
                        actions.renameSetlist(setlist.id, v); onDismiss()
                    }
                }
                SetMenuPage.Color -> {
                    Text("Set colour", style = SheetsType.Overline, color = SheetsColors.TextMuted, modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 6.dp))
                    PaletteRow(selected = setlist.colorArgb) { actions.setSetlistColor(setlist.id, it) }
                    MenuItem("Remove colour") { actions.setSetlistColor(setlist.id, null); onDismiss() }
                }
                SetMenuPage.ConfirmDelete -> {
                    Text("Delete “${setlist.name}”? The sheets inside stay in the library.", style = SheetsType.Body, color = SheetsColors.TextPrimary, modifier = Modifier.padding(14.dp))
                    MenuItem("Delete Set", danger = true) { actions.deleteSetlist(setlist.id); onDismiss() }
                    MenuItem("Cancel") { onDismiss() }
                }
            }
        }
    }
}

/** Champ de saisie d'un nom, reutilise par les dialogues (creation et renommage). */
@Composable
private fun NameField(initial: String, placeholder: String, modifier: Modifier = Modifier, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(SheetsColors.SurfaceChip).padding(12.dp)) {
            if (text.isEmpty()) Text(placeholder, style = SheetsType.Body, color = SheetsColors.TextMuted)
            BasicTextField(text, { text = it }, singleLine = true, textStyle = SheetsType.Body.copy(color = SheetsColors.TextPrimary), cursorBrush = SolidColor(SheetsColors.Accent), modifier = Modifier.fillMaxWidth())
        }
        Pill("Save", selected = true) { onDone(text) }
    }
}

/** Rangee de pastilles de couleur, comme le selecteur de couleur de l'editeur. */
@Composable
private fun PaletteRow(selected: Long?, onPick: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PalettePresets.Presets.take(10).forEach { argb ->
            val on = selected == argb
            Box(
                Modifier.size(if (on) 26.dp else 22.dp).clip(CircleShape)
                    .background(argb.toColor())
                    .border(if (on) 2.dp else 1.dp, if (on) Color.White else Color(0x33FFFFFF), CircleShape)
                    .clickable { onPick(argb) },
            )
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
private fun NameDialog(title: String, placeholder: String, initial: String = "", onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(18.dp)).background(SheetsColors.Surface).padding(18.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = SheetsType.Label, color = Color.White)
            NameField(initial = initial, placeholder = placeholder) { onDone(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pill("Cancel", onClick = onDismiss) }
        }
    }
}
