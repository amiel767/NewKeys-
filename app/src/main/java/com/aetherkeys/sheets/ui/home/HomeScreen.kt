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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
    // Dans un Set on ne montre que son contenu ; ailleurs les Sets ouvrent la grille.
    val showSets = state.filter !is LibraryFilter.OfSetlist
    var menuFor by remember { mutableStateOf<Sheet?>(null) }
    var setMenuFor by remember { mutableStateOf<Setlist?>(null) }
    var newSetlist by remember { mutableStateOf(false) }

    SheetsPageBackground(modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HomeHeader(state, actions, onBackToMixer, onOpenSettings)
            FilterRow(state, actions, onNewSet = { newSetlist = true })
            if (visible.isEmpty() && sets.isEmpty() && !state.loading) {
                EmptyState(
                    state = state,
                    onCreate = { actions.createSheet(onOpenEditor) },
                    onNewSet = { newSetlist = true },
                )
            } else {
                BoxWithConstraints(Modifier.weight(1f)) {
                    // La preview se cale sur la largeur reelle d'une carte de la grille.
                    val previewWidthSp = previewWidthFor(maxWidth / 4)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp + bottomInset),
                    ) {
                        if (showSets && sets.isNotEmpty()) {
                            item(key = "sets-label", span = { GridItemSpan(maxLineSpan) }) {
                                OverlineLabel("Sets")
                            }
                            items(sets, key = { "set-${it.id}" }) { sl ->
                                SetCard(
                                    setlist = sl,
                                    // Le nom des feuilles du Set, dans l'ordre de jeu.
                                    sheetNames = sl.sheetIds.mapNotNull { id -> state.sheets.firstOrNull { it.id == id }?.title },
                                    modifier = Modifier,
                                    onOpen = { actions.setFilter(LibraryFilter.OfSetlist(sl.id)) },
                                    onMenu = { setMenuFor = sl },
                                )
                            }
                            if (visible.isNotEmpty()) {
                                item(key = "sheets-label", span = { GridItemSpan(maxLineSpan) }) {
                                    OverlineLabel("Sheets")
                                }
                            }
                        }
                        items(visible, key = { it.id }) { sheet ->
                            SheetCard(
                                sheet = sheet, settings = settings, modifier = Modifier,
                                previewWidthSp = previewWidthSp,
                                onOpen = { onOpenEditor(sheet.id) },
                                onFloat = { onFloatOverMixer(sheet) },
                                onMenu = { menuFor = sheet },
                            )
                        }
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
    if (newSetlist) NameDialog("Nouveau Set", "Nom du Set", onDismiss = { newSetlist = false }) { actions.createSetlist(it); newSetlist = false }
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

@Composable
private fun FilterRow(state: LibraryUiState, actions: HomeActions, onNewSet: () -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Pill("All · ${state.sheets.size}", selected = state.filter == LibraryFilter.All) { actions.setFilter(LibraryFilter.All) } }
        item { Pill("★ Favorites", selected = state.filter == LibraryFilter.Favorites) { actions.setFilter(LibraryFilter.Favorites) } }
        item { Pill("Sets · ${state.library.setlists.size}", selected = state.filter == LibraryFilter.Sets) { actions.setFilter(LibraryFilter.Sets) } }
        items(state.library.setlists.size) { i ->
            val sl = state.library.setlists[i]
            Pill(sl.name, selected = (state.filter as? LibraryFilter.OfSetlist)?.id == sl.id) { actions.setFilter(LibraryFilter.OfSetlist(sl.id)) }
        }
        item { Pill("+ Set") { onNewSet() } }
    }
}

/**
 * Taille de la preview d'accords, calee sur la largeur reelle de la carte.
 * La reference est la carte de 212 dp (quatre par ligne sur une page de 891 dp) :
 * une carte plus etroite reduit la preview, pour que les trois accords tiennent.
 */
private fun previewWidthFor(cardWidth: Dp): Float = (25f * (cardWidth.value / 212f)).coerceIn(9f, 25f)

/**
 * Carte de bibliotheque, quatre par ligne. Le fond porte la couleur de la carte,
 * `sheet_card_sheen.xml` dessine liseret et bande de lumiere, `shadow` pose l'ombre.
 *
 * - tap : ouvrir ; double-tap : poser sur le mixer ; appui long : menu
 * - la carte s'enfonce au doigt (echelle 0.94, ressort) avec retour haptique
 * - [previewWidthSp] : taille de la preview, calee sur la largeur reelle de la carte
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetCard(
    sheet: Sheet, settings: SheetsSettings, modifier: Modifier,
    previewWidthSp: Float,
    onOpen: () -> Unit, onFloat: () -> Unit, onMenu: () -> Unit,
) {
    val base = sheet.colorArgb.toColor()
    val ink = onColorFor(base)
    val family = SheetsFonts.family(sheet.chordFontId)

    val firstSection = sheet.sections.firstOrNull { it.bars.isNotEmpty() }
    val key = firstSection?.let { sheet.keyFor(it) } ?: sheet.keyPc
    val preview = firstSection?.bars?.take(3)?.joinToString("  ") { Theory.barText(it, key, settings.notation, settings.passing) }.orEmpty()

    LibraryCard(modifier, base, ink, onOpen, onFloat, onMenu) {
        Column(
            Modifier.fillMaxSize().padding(start = 15.dp, end = 14.dp, top = 13.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                sheet.title, style = SheetsType.CardTitle, color = ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.width(2.5.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(ink.copy(alpha = 0.75f)))
                Text(preview, style = SheetsType.chord(family, previewWidthSp), color = ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Tonalite : plaque noire, texte blanc — lisible quelle que soit la teinte.
                Box(Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xE6000000)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                    Text(Theory.KEY_NAMES[sheet.keyPc], style = SheetsType.CardMeta, color = Color.White)
                }
                Spacer(Modifier.width(6.dp))
                Text("${sheet.bpm}", style = SheetsType.CardMeta, color = ink.copy(alpha = 0.7f))
                Spacer(Modifier.weight(1f))
                if (sheet.favorite) Icon(SheetsIcons.Star, contentDescription = "Favorite", tint = SheetsColors.Warning, modifier = Modifier.size(13.dp))
                else Text("${sheet.sections.size} sect.", style = SheetsType.CardMeta, color = ink.copy(alpha = 0.7f))
            }
        }
    }
}

/**
 * Carte de Set : le dossier de feuilles, affiche exactement comme une feuille —
 * meme gabarit, meme profondeur, meme rebond. La tranche reste, le contenu change.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SetCard(
    setlist: Setlist, sheetNames: List<String>, modifier: Modifier,
    onOpen: () -> Unit, onMenu: () -> Unit,
) {
    val base = (setlist.colorArgb ?: PalettePresets.Presets.first()).toColor()
    val ink = onColorFor(base)
    // Apercu du contenu : les trois premiers titres ranges dans le Set.
    val names = remember(sheetNames) { sheetNames.take(3) }

    LibraryCard(modifier.testTag("set-card"), base, ink, onOpen, onDouble = null, onMenu = onMenu) {
        Column(
            Modifier.fillMaxSize().padding(start = 15.dp, end = 14.dp, top = 13.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                setlist.name, style = SheetsType.CardTitle, color = ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(SheetsIcons.Folder, contentDescription = null, tint = ink.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
                Text(
                    if (names.isEmpty()) "Aucune feuille" else names.joinToString(" · "),
                    style = SheetsType.CardMeta.copy(fontSize = 11.sp), color = ink.copy(alpha = 0.82f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xE6000000)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                    Text("SET", style = SheetsType.CardMeta, color = Color.White)
                }
                Spacer(Modifier.width(6.dp))
                Text("${sheetNames.size} feuille${if (sheetNames.size > 1) "s" else ""}", style = SheetsType.CardMeta, color = ink.copy(alpha = 0.7f))
                Spacer(Modifier.weight(1f))
                Icon(SheetsIcons.ChevronRight, contentDescription = null, tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

/**
 * Gabarit commun des cartes de la bibliotheque : fond, volume en XML, tranche,
 * ombre, gestes et rebond. Les feuilles et les Sets n'en different que par le contenu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCard(
    modifier: Modifier, base: Color, ink: Color,
    onOpen: () -> Unit, onDouble: (() -> Unit)?, onMenu: () -> Unit,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.55f, stiffness = 900f), label = "cardPress")

    Box(
        modifier.height(164.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (pressed) 6.dp else 14.dp, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(base)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onOpen() },
                onDoubleClick = onDouble?.let { { haptic.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onMenu() },
            ),
    ) {
        // Volume : degrade + liserets + bande de lumiere, le second dessine en XML.
        Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(base, lerp(base, Color.Black, 0.34f)))))
        Image(
            painter = painterResource(R.drawable.sheet_card_sheen),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize(),
        )
        // Tranche : identite de la carte, lisible d'un coup d'oeil.
        Box(Modifier.width(5.dp).fillMaxHeight().background(ink.copy(alpha = 0.55f)))
        content()
    }
}

@Composable
private fun EmptyState(state: LibraryUiState, onCreate: () -> Unit, onNewSet: () -> Unit) {
    val searching = state.query.isNotBlank()
    val setsOnly = state.filter == LibraryFilter.Sets
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            when {
                searching -> "Aucun resultat"
                setsOnly -> "Aucun Set"
                else -> "Aucune feuille"
            },
            style = SheetsType.Title, color = Color.White,
        )
        if (!searching) {
            Text(
                if (setsOnly) "Un Set rassemble les feuilles que tu joues ensemble."
                else "Cree ta premiere feuille pour commencer a ecrire.",
                style = SheetsType.Body, color = SheetsColors.TextMuted, textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Nouvelle feuille", selected = true, onClick = onCreate)
                Pill("Nouveau Set", onClick = onNewSet)
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
 * aucune feuille — il ne fait que le retirer de la bibliotheque.
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
                    NameField(initial = setlist.name, placeholder = "Nom du Set", modifier = Modifier.padding(horizontal = 12.dp)) { v ->
                        actions.renameSetlist(setlist.id, v); onDismiss()
                    }
                }
                SetMenuPage.Color -> {
                    Text("Couleur du Set", style = SheetsType.Overline, color = SheetsColors.TextMuted, modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 6.dp))
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
