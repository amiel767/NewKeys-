package com.soundstage.mixer.ui.pages

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.SheetItem
import com.soundstage.mixer.utils.ChordNotationConverter
import com.soundstage.mixer.utils.NotationSystem
import kotlinx.coroutines.delay

// ======================================================================================
// COLOR TOKENS EXACTLY MATCHING THE SVG DESIGN & BRIEF
// ======================================================================================
private val BgGradientTop = Color(0xFF121A32)
private val BgGradientBottom = Color(0xFF0A0E1C)
private val PrimaryBlueAccent = Color(0xFF9DB8EA)
private val PrimaryBlueBtn = Color(0xFFBDD3F9)
private val PrimaryBlueBtnText = Color(0xFF172036)
private val AmberAccent = Color(0xFFF5A524)
private val ChipBg = Color(0xFF262E45)
private val ChipBorder = Color(0xFF3A4560)
private val SlateTextSecondary = Color(0xFF8F9BB8)

// 5 Readable Handwritten Styles & 5 Lyrics Styles
enum class SheetFontStyle(
    val label: String,
    val isHandwritten: Boolean,
    val fontFamily: FontFamily,
    val fontStyle: FontStyle = FontStyle.Normal
) {
    HAND_ARCHITECT("Architects Daughter", true, FontFamily.Cursive),
    HAND_CAVEAT("Caveat", true, FontFamily.Cursive),
    HAND_PATRICK("Patrick Hand", true, FontFamily.Cursive),
    HAND_KALAM("Kalam", true, FontFamily.Cursive),
    HAND_SHADOW("Shadows Into Light", true, FontFamily.Cursive),

    LYRIC_INTER("Inter Sans", false, FontFamily.SansSerif),
    LYRIC_MONO("Roboto Mono", false, FontFamily.Monospace),
    LYRIC_MONTSERRAT("Montserrat", false, FontFamily.SansSerif),
    LYRIC_POPPINS("Poppins", false, FontFamily.SansSerif),
    LYRIC_OUTFIT("Outfit", false, FontFamily.SansSerif)
}

// Notepad Page Colors from the SVG
val PageThemeColors = listOf(
    Pair("Night Slate", Color(0xFF181D2F)),
    Pair("Vintage Cream", Color(0xFFF4ECD8)),
    Pair("Blueprint Blue", Color(0xFF2C4D94)),
    Pair("Sage Forest", Color(0xFF1B2824)),
    Pair("Plum Wine", Color(0xFF281D2B)),
    Pair("Kraft Olive", Color(0xFF2A281A))
)

// Ink / Text Colors
val InkColors = listOf(
    Pair("White", Color(0xFFF2F5FF)),
    Pair("Ice Cyan", Color(0xFF67E8F9)),
    Pair("Gold", Color(0xFFFDE047)),
    Pair("Mint", Color(0xFF86EFAC)),
    Pair("Sunset", Color(0xFFFDBA74)),
    Pair("Lavender", Color(0xFFC4B5FD)),
    Pair("Charcoal Ink", Color(0xFF1E293B))
)

// Active Editor Ribbon Tabs
enum class EditorTab {
    CHORDS, SYMBOLS, TEXT, PAGE
}

@Composable
fun SheetsPage(
    sheets: List<SheetItem>,
    activeSheetId: String?,
    onSelectSheet: (String) -> Unit,
    onCreateSheet: (title: String, key: String, content: String) -> Unit,
    onUpdateSheet: (id: String, title: String, key: String, content: String) -> Unit,
    onDeleteSheet: (id: String) -> Unit,
    notesText: String,
    onNotesChange: (String) -> Unit,
    transpose: Int,
    onTransposeChange: (Int) -> Unit,
    onBackToMixer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFloatingSheet: (SheetItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var editingSheetId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf("All") }

    // Fixed Segmented Toggle: 1-7 (Nashville) vs A-G (Standard)
    var globalNotation by remember { mutableStateOf(NotationSystem.NASHVILLE) }

    val activeSheet = remember(sheets, editingSheetId) {
        sheets.find { it.id == editingSheetId }
    }

    // Default sample sheets if list is empty (so cards show beautifully right away)
    val displaySheets = remember(sheets) {
        if (sheets.isEmpty()) {
            listOf(
                SheetItem(
                    id = "sample_1",
                    title = "New Song",
                    key = "C",
                    tempo = 120,
                    timeSignature = "4/4",
                    swing = 54,
                    colorIndex = 0,
                    content = "[Intro]\n| 1 | 3m | 6 | 4 - 5 |\n\n[Verse]\n| 2m | 3m | 5 | 6m |\n| 6m | 5 | <4> | ^ 1 |\n\n[Chorus]\n|: 1 | 5 | 6m | 4 |\n| 1 | 5 | 4 < 5 | 1 :| x2"
                ),
                SheetItem(
                    id = "sample_2",
                    title = "Gospel in G",
                    key = "G",
                    tempo = 72,
                    timeSignature = "6/8",
                    swing = 50,
                    colorIndex = 1,
                    content = "[Intro]\n| 1 | 4 | 1 | 4 - 5 |\n\n[Verse]\n| 6m | 4 | 1 | 5 |\n| 1 | 4 | 5 | 1 |"
                ),
                SheetItem(
                    id = "sample_3",
                    title = "Sunrise Pad",
                    key = "A",
                    tempo = 96,
                    timeSignature = "4/4",
                    swing = 52,
                    colorIndex = 2,
                    content = "[Verse]\n| 1 | 5 | 6m | 4 |\n| 1 | 5 | 4 - 5 | 1 |\n\n[Chorus]\n|: 4 | 1 | 5 | 6m :| x2"
                ),
                SheetItem(
                    id = "sample_4",
                    title = "Morning Funk",
                    key = "E",
                    tempo = 104,
                    timeSignature = "4/4",
                    swing = 58,
                    colorIndex = 0,
                    content = "[Groove]\n| 1 | 1 | 4 | 4 |\n| 5 | 4 | 1 | 1 - 5 |"
                ),
                SheetItem(
                    id = "sample_5",
                    title = "Slow Burn",
                    key = "D",
                    tempo = 68,
                    timeSignature = "4/4",
                    swing = 50,
                    colorIndex = 1,
                    content = "[Verse]\n| 6m | 6m | 4 | 4 |\n| 1 | 5 | 6m | 6m |"
                )
            )
        } else {
            sheets
        }
    }

    val filteredSheets = remember(displaySheets, searchQuery, activeFilter) {
        var list = if (searchQuery.isBlank()) displaySheets else {
            displaySheets.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.key.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true)
            }
        }
        if (activeFilter == "Favorites") {
            list = list.filter { it.isFavorite }
        }
        list
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BgGradientTop, BgGradientBottom)
                )
            )
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("sheets_main_container")
    ) {
        if (activeSheet != null) {
            // ==========================================================================
            // 2. ÉDITEUR SHEETS (aether-keys-sheets-editor.svg)
            // ==========================================================================
            SvgSheetsEditor(
                sheet = activeSheet,
                globalNotation = globalNotation,
                onSave = { newTitle, newKey, newContent, newTempo, newSig ->
                    onUpdateSheet(activeSheet.id, newTitle, newKey, newContent)
                    editingSheetId = null
                },
                onDelete = {
                    onDeleteSheet(activeSheet.id)
                    editingSheetId = null
                },
                onBack = { editingSheetId = null }
            )
        } else {
            // ==========================================================================
            // 1. ACCUEIL SHEETS (aether-keys-sheets-home-v2.svg)
            // ==========================================================================
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // --- TOP HEADER BAR (Filter, Search, 1-7 | A-G Switch, Title, Create Button, Settings) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Filter Button + Search Bar + Segmented 1-7 | A-G Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Filter button (equalizer icon)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ChipBg)
                                .border(1.dp, ChipBorder, RoundedCornerShape(12.dp))
                                .clickable { onBackToMixer() }
                                .testTag("btn_sheets_filter"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Mixer",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Search Bar
                        Box(
                            modifier = Modifier
                                .width(220.dp)
                                .height(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ChipBg)
                                .border(1.dp, ChipBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = SlateTextSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    cursorBrush = SolidColor(PrimaryBlueAccent),
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Search...",
                                                fontSize = 13.sp,
                                                color = SlateTextSecondary
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                            }
                        }

                        // Segmented 1-7 | A-G Switch (Fixed Width Pill)
                        Box(
                            modifier = Modifier
                                .height(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ChipBg)
                                .border(1.dp, ChipBorder, RoundedCornerShape(12.dp))
                                .padding(3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 1-7 (Nashville)
                                val isNashville = globalNotation == NotationSystem.NASHVILLE
                                Box(
                                    modifier = Modifier
                                        .height(36.dp)
                                        .width(48.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (isNashville) PrimaryBlueBtn else Color.Transparent)
                                        .clickable { globalNotation = NotationSystem.NASHVILLE },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "1-7",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isNashville) PrimaryBlueBtnText else SlateTextSecondary
                                    )
                                }

                                // A-G (Standard)
                                Box(
                                    modifier = Modifier
                                        .height(36.dp)
                                        .width(48.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (!isNashville) PrimaryBlueBtn else Color.Transparent)
                                        .clickable { globalNotation = NotationSystem.STANDARD },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "A-G",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!isNashville) PrimaryBlueBtnText else SlateTextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Center: "Sheets" Title
                    Text(
                        text = "Sheets",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // Right: "+ Create New Sheet" primary button + Settings Gear
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBtn),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 11.dp),
                            onClick = {
                                val created = SheetItem(
                                    title = "New Song",
                                    key = "C",
                                    content = "[Intro]\n| 1 | 3m | 6 | 4 - 5 |\n\n[Verse]\n| 2m | 3m | 5 | 6m |"
                                )
                                onCreateSheet(created.title, created.key, created.content)
                                editingSheetId = created.id
                            },
                            modifier = Modifier.testTag("btn_sheets_create_new")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = PrimaryBlueBtnText,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Create New Sheet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlueBtnText
                            )
                        }

                        // Settings button
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ChipBg)
                                .border(1.dp, ChipBorder, RoundedCornerShape(12.dp))
                                .clickable { onOpenSettings() }
                                .testTag("btn_sheets_settings"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // --- SUB-HEADER: Filters (All · 12, Favorites, Setlists) + Tri Recent ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "All · ${displaySheets.size}" to "All",
                            "Favorites" to "Favorites",
                            "Setlists" to "Setlists"
                        ).forEach { (label, key) ->
                            val isSelected = activeFilter == key
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) PrimaryBlueBtn else ChipBg)
                                    .clickable { activeFilter = key }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) PrimaryBlueBtnText else Color.White
                                )
                            }
                        }
                    }

                    // Sort: Recent
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ChipBg)
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "Recent ▾",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = SlateTextSecondary
                        )
                    }
                }

                // --- HORIZONTAL CAROUSEL OF CARDS (LazyRow) ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        // Existing Song Cards
                        itemsIndexed(filteredSheets, key = { _, item -> item.id }) { index, sheet ->
                            SvgHomeSheetCard(
                                sheet = sheet,
                                index = index,
                                globalNotation = globalNotation,
                                onClick = {
                                    editingSheetId = sheet.id
                                    onSelectSheet(sheet.id)
                                },
                                onDoubleClick = {
                                    onOpenFloatingSheet(sheet)
                                }
                            )
                        }

                        // Last Dotted Quick Start Card "+ New Sheet"
                        item {
                            SvgQuickStartNewSheetCard(
                                onCreateBlank = {
                                    val newS = SheetItem(title = "New Song", key = "C", content = "[Intro]\n| 1 | 3m | 6 | 4 - 5 |")
                                    onCreateSheet(newS.title, newS.key, newS.content)
                                    editingSheetId = newS.id
                                },
                                onCreateBlues = {
                                    val newS = SheetItem(
                                        title = "12-Bar Blues",
                                        key = "E",
                                        content = "[12-Bar Blues]\n| 1 | 1 | 1 | 1 |\n| 4 | 4 | 1 | 1 |\n| 5 | 4 | 1 | 5 |"
                                    )
                                    onCreateSheet(newS.title, newS.key, newS.content)
                                    editingSheetId = newS.id
                                },
                                onCreate1564 = {
                                    val newS = SheetItem(
                                        title = "Pop Progression",
                                        key = "G",
                                        content = "[Progression 1-5-6m-4]\n| 1 | 5 | 6m | 4 |\n| 1 | 5 | 6m | 4 |"
                                    )
                                    onCreateSheet(newS.title, newS.key, newS.content)
                                    editingSheetId = newS.id
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ======================================================================================
// SVG HOME SHEET CARD (Exact layout from aether-keys-sheets-home-v2.svg)
// ======================================================================================
@Composable
private fun SvgHomeSheetCard(
    sheet: SheetItem,
    index: Int,
    globalNotation: NotationSystem,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit = {}
) {
    val pageColors = PageThemeColors[Math.abs(sheet.colorIndex % PageThemeColors.size)].second
    val isLightPage = pageColors == Color(0xFFF4ECD8)
    val cardTextColor = if (isLightPage) Color(0xFF1E293B) else Color.White
    val chordTextColor = if (isLightPage) Color(0xFF0F172A) else Color(0xFFF1F5F9)

    var isAnimated by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index * 40L).coerceAtMost(400L))
        isAnimated = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (isAnimated) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "svg_sheet_card_alpha"
    )

    // Convert chart content to active notation
    val convertedContent = remember(sheet.content, sheet.key, globalNotation) {
        ChordNotationConverter.convertSheetText(
            text = sheet.content,
            songKey = sheet.key,
            targetSystem = globalNotation,
            isMinor = sheet.key.contains("m") && !sheet.key.contains("maj")
        )
    }

    Box(
        modifier = Modifier
            .width(310.dp)
            .fillMaxHeight(0.92f)
            .graphicsLayer { this.alpha = alpha }
            .shadow(6.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
            .clip(RoundedCornerShape(24.dp))
            .background(pageColors)
            .drawWithContent {
                drawContent()
                // Top white highlight line (~16%)
                drawLine(
                    color = Color.White.copy(alpha = if (isLightPage) 0.35f else 0.16f),
                    start = Offset(12.dp.toPx(), 1.5f),
                    end = Offset(size.width - 12.dp.toPx(), 1.5f),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            .pointerInput(sheet.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { onDoubleClick() }
                )
            }
            .padding(18.dp)
            .testTag("svg_sheet_card_${sheet.id}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Handwritten Title + Amber Key Chip Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = sheet.title.ifBlank { "Untitled Sheet" },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Cursive,
                    color = cardTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Amber Square-Rounded Key Badge (#F5A524)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(AmberAccent.copy(alpha = if (isLightPage) 0.35f else 0.26f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = sheet.key,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLightPage) Color(0xFFB45309) else AmberAccent
                    )
                }
            }

            // Body: 2 Lines of Inline Measure Grid in handwritten font (e.g. | 1 | 4 | 1 | 4 - 5 |)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val lines = convertedContent.lines().filter { it.isNotBlank() && !it.trim().startsWith("[") }.take(2)
                if (lines.isEmpty()) {
                    Text(
                        text = "|  1  |  4  |  5  |  1  |\n|  6m |  4  |  5  |  1  |",
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Cursive,
                        fontWeight = FontWeight.SemiBold,
                        color = chordTextColor.copy(alpha = 0.6f),
                        lineHeight = 22.sp
                    )
                } else {
                    lines.forEach { line ->
                        Text(
                            text = line,
                            fontSize = 17.sp,
                            fontFamily = FontFamily.Cursive,
                            fontWeight = FontWeight.Bold,
                            color = chordTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Footer: BPM · 4/4 on left, Date on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${sheet.tempo} BPM · ${sheet.timeSignature}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isLightPage) Color(0xFF64748B) else SlateTextSecondary
                )

                Text(
                    text = "Oct 4",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isLightPage) Color(0xFF64748B) else SlateTextSecondary
                )
            }
        }
    }
}

// ======================================================================================
// DOTTED QUICK START CARD "+ New Sheet"
// ======================================================================================
@Composable
private fun SvgQuickStartNewSheetCard(
    onCreateBlank: () -> Unit,
    onCreateBlues: () -> Unit,
    onCreate1564: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight(0.92f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF161C2C).copy(alpha = 0.65f))
            .border(1.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = PrimaryBlueAccent,
                modifier = Modifier.size(36.dp)
            )

            Text(
                text = "New Sheet",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Cursive,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3 Quick Starts
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChipBg)
                        .border(1.dp, ChipBorder, RoundedCornerShape(8.dp))
                        .clickable { onCreateBlank() }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(text = "Blank", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChipBg)
                        .border(1.dp, ChipBorder, RoundedCornerShape(8.dp))
                        .clickable { onCreateBlues() }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(text = "12-bar Blues", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChipBg)
                        .border(1.dp, ChipBorder, RoundedCornerShape(8.dp))
                        .clickable { onCreate1564() }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(text = "1-5-6m-4", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ======================================================================================
// SVG SHEETS EDITOR (aether-keys-sheets-editor.svg)
// Left Panel: Chords / Symbols / Text / Page. Right Panel: Handwritten Word-like Chart Page
// ======================================================================================
@Composable
private fun SvgSheetsEditor(
    sheet: SheetItem,
    globalNotation: NotationSystem,
    onSave: (title: String, key: String, content: String, tempo: Int, timeSig: String) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf(sheet.title) }
    var selectedKeyRoot by remember {
        val root = ChordNotationConverter.extractRoot(sheet.key) ?: "C"
        mutableStateOf(root)
    }
    var isMinorMode by remember {
        mutableStateOf(sheet.key.contains("m") && !sheet.key.contains("maj"))
    }
    val fullKeyName by remember {
        derivedStateOf { if (isMinorMode) "${selectedKeyRoot}m" else selectedKeyRoot }
    }

    var content by remember { mutableStateOf(sheet.content) }
    var currentFontSize by remember { mutableIntStateOf(sheet.fontSize.coerceIn(14, 32)) }
    var selectedFontStyle by remember { mutableStateOf(SheetFontStyle.HAND_ARCHITECT) }
    var selectedPageThemeIndex by remember { mutableIntStateOf(sheet.colorIndex) }
    var selectedInkColor by remember { mutableStateOf(InkColors[0].second) }

    var isLyricsMode by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(EditorTab.CHORDS) }
    var lastInsertedChord by remember { mutableStateOf<String?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showTonalityDialog by remember { mutableStateOf(false) }

    val pageColor = PageThemeColors[Math.abs(selectedPageThemeIndex % PageThemeColors.size)].second
    val isLightPage = pageColor == Color(0xFFF4ECD8)
    val documentTextColor = if (isLightPage) Color(0xFF1E293B) else selectedInkColor

    val diatonicChords = remember(selectedKeyRoot, isMinorMode, globalNotation) {
        ChordNotationConverter.getDiatonicChords(selectedKeyRoot, isMinorMode, globalNotation)
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==============================================================================
        // LEFT PANEL (36%): Full Height Tool Ribbon (Chords, Symbols, Text, Page)
        // ==============================================================================
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.36f)
                .shadow(6.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF131926))
                .border(1.dp, Color(0xFF1F283B), RoundedCornerShape(20.dp))
                .padding(14.dp)
                .testTag("svg_editor_left_panel")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. TOP HEADER ROW: [<-] [Key C] [Chart | Lyrics] [Save] [...]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back arrow
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ChipBg)
                            .clickable { onBack() }
                            .testTag("btn_editor_back"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Key badge [C]
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(ChipBg)
                            .border(1.dp, ChipBorder, RoundedCornerShape(9.dp))
                            .clickable { showTonalityDialog = true }
                            .testTag("btn_editor_key_badge"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = fullKeyName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Segmented [Chart | Lyrics]
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(ChipBg)
                            .padding(2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (!isLyricsMode) PrimaryBlueBtn else Color.Transparent)
                                    .clickable { isLyricsMode = false }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Chart",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isLyricsMode) PrimaryBlueBtnText else SlateTextSecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (isLyricsMode) PrimaryBlueBtn else Color.Transparent)
                                    .clickable { isLyricsMode = true }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Lyrics",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLyricsMode) PrimaryBlueBtnText else SlateTextSecondary
                                )
                            }
                        }
                    }

                    // Save Button
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBtn),
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                        onClick = { onSave(title, fullKeyName, content, sheet.tempo, sheet.timeSignature) },
                        modifier = Modifier.testTag("btn_editor_save")
                    ) {
                        Text(
                            text = "Save",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlueBtnText
                        )
                    }

                    // More Menu (...)
                    Box {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ChipBg)
                                .clickable { showMoreMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear Sheet") },
                                onClick = {
                                    content = ""
                                    showMoreMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Sheet", color = Color(0xFFEF4444)) },
                                onClick = {
                                    showMoreMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }

                // 2. RIBBON TABS (Chords, Symbols, Text, Page)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    EditorTab.values().forEach { tab ->
                        val isSelected = activeTab == tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { activeTab = tab }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = tab.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else SlateTextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(2.dp)
                                    .background(if (isSelected) PrimaryBlueAccent else Color.Transparent)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // 3. TAB SPECIFIC CONTENT
                when (activeTab) {
                    EditorTab.CHORDS -> {
                        // CHORDS · NASHVILLE (Degree Keyboard 1 to 7)
                        Text(
                            text = "CHORDS · ${globalNotation.shortName.uppercase()}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateTextSecondary,
                            letterSpacing = 0.5.sp
                        )

                        // Row 1: Tall Pill Keys 1 to 7
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val degreeLabels = if (globalNotation == NotationSystem.NASHVILLE) {
                                listOf("1", "2", "3", "4", "5", "6", "7")
                            } else {
                                diatonicChords.take(7)
                            }

                            degreeLabels.forEach { deg ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF20293C))
                                        .border(1.dp, ChipBorder, RoundedCornerShape(10.dp))
                                        .clickable {
                                            val sep = if (content.isEmpty() || content.endsWith(" ") || content.endsWith("\n") || content.endsWith("| ")) "" else " "
                                            content = "$content$sep$deg"
                                            lastInsertedChord = deg
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = deg,
                                        fontSize = 18.sp,
                                        fontFamily = FontFamily.Cursive,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Row 2: Accidentals & Modifiers (b, #, / bass, N.C., %)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("b", "#", "/ bass", "N.C.", "%").forEach { mod ->
                                Box(
                                    modifier = Modifier
                                        .weight(if (mod.length > 2) 1.5f else 1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1A2234))
                                        .border(1.dp, ChipBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            val active = lastInsertedChord
                                            if (active != null) {
                                                val modified = ChordNotationConverter.applyExtension(active, mod)
                                                if (content.endsWith(active)) {
                                                    content = content.substring(0, content.length - active.length) + modified
                                                } else {
                                                    content = "$content $mod"
                                                }
                                                lastInsertedChord = modified
                                            } else {
                                                content = "$content $mod"
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mod,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // EXTENSIONS · ACTIVE CHORD (Row 1 & Row 2)
                        Text(
                            text = "EXTENSIONS · ACTIVE CHORD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateTextSecondary,
                            letterSpacing = 0.5.sp
                        )

                        // Ext Row 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("m", "7", "maj7", "m7", "sus2", "sus4", "11").forEach { ext ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(Color(0xFF1E2638))
                                        .border(1.dp, ChipBorder.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
                                        .clickable {
                                            val active = lastInsertedChord
                                            if (active != null) {
                                                val modified = ChordNotationConverter.applyExtension(active, ext)
                                                if (content.endsWith(active)) {
                                                    content = content.substring(0, content.length - active.length) + modified
                                                } else {
                                                    content = "$content$ext"
                                                }
                                                lastInsertedChord = modified
                                            } else {
                                                content = "$content$ext"
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = ext, fontSize = 11.sp, color = Color(0xFFE2E8F0), fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        // Ext Row 2
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("add9", "6", "9", "dim", "aug", "5", "13").forEach { ext ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(Color(0xFF1E2638))
                                        .border(1.dp, ChipBorder.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
                                        .clickable {
                                            val active = lastInsertedChord
                                            if (active != null) {
                                                val modified = ChordNotationConverter.applyExtension(active, ext)
                                                if (content.endsWith(active)) {
                                                    content = content.substring(0, content.length - active.length) + modified
                                                } else {
                                                    content = "$content$ext"
                                                }
                                                lastInsertedChord = modified
                                            } else {
                                                content = "$content$ext"
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = ext, fontSize = 11.sp, color = Color(0xFFE2E8F0), fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // PASSING CHORD SECTION (Split 2, Split 4, Passing 4 - 5)
                        Text(
                            text = "PASSING CHORD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateTextSecondary,
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E2638))
                                    .clickable { content = "$content | 1 - 5 |" },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Split 2", fontSize = 11.sp, color = Color.White)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E2638))
                                    .clickable { content = "$content | 1 - 4 - 5 - 1 |" },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Split 4", fontSize = 11.sp, color = Color.White)
                            }

                            // Passing X - Y (Amber outlined button)
                            Box(
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AmberAccent.copy(alpha = 0.12f))
                                    .border(1.dp, AmberAccent, RoundedCornerShape(8.dp))
                                    .clickable { content = "$content 4 - 5" },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Passing 4 - 5", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // EDIT TOOLS (6 Icons with labels below: Undo, Redo, Dup bar, Copy line, Transpose, Page)
                        Text(
                            text = "EDIT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateTextSecondary,
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(
                                Triple("Undo", Icons.AutoMirrored.Filled.Undo) {
                                    if (content.isNotEmpty()) content = content.dropLast(1)
                                },
                                Triple("Redo", Icons.AutoMirrored.Filled.Redo) {},
                                Triple("Dup bar", Icons.Default.ContentCopy) {
                                    content = "$content | 1 |"
                                },
                                Triple("Copy line", Icons.Default.Menu) {
                                    content = "$content\n| 1 | 4 | 5 | 1 |"
                                },
                                Triple("Transpose", Icons.Default.Exposure) {
                                    showTonalityDialog = true
                                },
                                Triple("Page", Icons.Default.Palette) {
                                    activeTab = EditorTab.PAGE
                                }
                            ).forEach { (label, icon, onClickAction) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { onClickAction() }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(Color(0xFF1E2638)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(17.dp))
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(text = label, fontSize = 9.sp, color = SlateTextSecondary)
                                }
                            }
                        }
                    }

                    EditorTab.SYMBOLS -> {
                        // STRUCTURE TAGS
                        Text("STRUCTURE TAGS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Intro", "Verse", "Chorus", "Bridge", "Pre-Chorus", "Solo", "Outro").forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ChipBg)
                                        .clickable {
                                            val pfx = if (content.isEmpty() || content.endsWith("\n")) "" else "\n\n"
                                            content = "$content$pfx[$tag]\n| "
                                        }
                                        .padding(horizontal = 9.dp, vertical = 6.dp)
                                ) {
                                    Text("[$tag]", fontSize = 11.sp, color = Color(0xFFCBD5E1), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // EXPRESSIVE AMBER SYMBOLS
                        Text("EXPRESSIVE SYMBOLS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "< 4 > (Held / Tenuto)" to "<4>",
                                "^ (Fermata)" to "^",
                                "< (Push)" to "<",
                                "|: (Repeat Start)" to "|:",
                                ":| x2 (Repeat End)" to ":| x2"
                            ).forEach { (label, sym) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AmberAccent.copy(alpha = 0.14f))
                                        .border(1.dp, AmberAccent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .clickable { content = "$content $sym" }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
                                }
                            }
                        }
                    }

                    EditorTab.TEXT -> {
                        // 5 HANDWRITTEN + 5 LYRICS FONTS
                        Text("FONTS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SheetFontStyle.values().forEach { style ->
                                val isSelected = selectedFontStyle == style
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) PrimaryBlueBtn else ChipBg)
                                        .clickable { selectedFontStyle = style }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = style.label,
                                        fontFamily = style.fontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) PrimaryBlueBtnText else Color.White
                                    )
                                    Text(
                                        text = if (style.isHandwritten) "Handwritten" else "Lyrics",
                                        fontSize = 10.sp,
                                        color = if (isSelected) PrimaryBlueBtnText.copy(alpha = 0.7f) else SlateTextSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // FONT SIZE CONTROLS
                        Text("FONT SIZE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(ChipBg).clickable { if (currentFontSize > 12) currentFontSize -= 2 }, contentAlignment = Alignment.Center) {
                                Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Text("$currentFontSize pt", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(ChipBg).clickable { if (currentFontSize < 36) currentFontSize += 2 }, contentAlignment = Alignment.Center) {
                                Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    EditorTab.PAGE -> {
                        // PAGE THEMES
                        Text("PAGE THEME & PAPER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PageThemeColors.forEachIndexed { idx, (name, col) ->
                                val isSelected = selectedPageThemeIndex == idx
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ChipBg)
                                        .border(if (isSelected) 1.5.dp else 0.dp, PrimaryBlueAccent, RoundedCornerShape(8.dp))
                                    .clickable { selectedPageThemeIndex = idx }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(col))
                                    Text(text = name, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==============================================================================
        // RIGHT PANEL (64%): Word-like Document Page (aether-keys-sheets-editor.svg)
        // ==============================================================================
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.64f)
                .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                .clip(RoundedCornerShape(22.dp))
                .background(pageColor)
                .padding(24.dp)
                .testTag("svg_editor_document_page")
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header: Large Handwritten Title with underline stretching to right
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BasicTextField(
                            value = title,
                            onValueChange = { title = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = documentTextColor,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = selectedFontStyle.fontFamily
                            ),
                            cursorBrush = SolidColor(PrimaryBlueAccent),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Fine underline extending to the right
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.5.dp)
                            .background(documentTextColor.copy(alpha = 0.25f))
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Subtitle Metadata (Key C · 120 BPM · 4/4 · Swing 54 %)
                    Text(
                        text = "Key $fullKeyName · ${sheet.tempo} BPM · ${sheet.timeSignature} · Swing ${sheet.swing} %",
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        color = documentTextColor.copy(alpha = 0.7f)
                    )
                }

                // Chart Document Body Text Field (Full Width & Height)
                BasicTextField(
                    value = content,
                    onValueChange = { content = it },
                    textStyle = TextStyle(
                        color = documentTextColor,
                        fontSize = currentFontSize.sp,
                        fontFamily = selectedFontStyle.fontFamily,
                        lineHeight = (currentFontSize + 12).sp
                    ),
                    cursorBrush = SolidColor(PrimaryBlueAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .testTag("svg_chart_content_input"),
                    decorationBox = { innerTextField ->
                        if (content.isEmpty()) {
                            Text(
                                text = "[Intro]\n| 1 | 3m | 6 | 4 - 5 |\n\n[Verse]\n| 2m | 3m | 5 | 6m |\n| 6m | 5 | <4> | ^ 1 |\n\n[Chorus]\n|: 1 | 5 | 6m | 4 |\n| 1 | 5 | 4 < 5 | 1 :| x2",
                                color = documentTextColor.copy(alpha = 0.35f),
                                fontSize = currentFontSize.sp,
                                fontFamily = selectedFontStyle.fontFamily
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }
    }

    // TONALITY ROOT & MODE PICKER DIALOG
    if (showTonalityDialog) {
        AlertDialog(
            onDismissRequest = { showTonalityDialog = false },
            title = {
                Text(
                    text = "Select Tonality",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = if (!isMinorMode) PrimaryBlueBtn else ChipBg),
                            shape = RoundedCornerShape(8.dp),
                            onClick = { isMinorMode = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Major", color = if (!isMinorMode) PrimaryBlueBtnText else Color.White, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = if (isMinorMode) PrimaryBlueBtn else ChipBg),
                            shape = RoundedCornerShape(8.dp),
                            onClick = { isMinorMode = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Minor", color = if (isMinorMode) PrimaryBlueBtnText else Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    val roots = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
                    roots.chunked(4).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { r ->
                                val isSel = selectedKeyRoot == r
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) PrimaryBlueBtn else ChipBg)
                                        .clickable {
                                            selectedKeyRoot = r
                                            showTonalityDialog = false
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = r, fontWeight = FontWeight.Bold, color = if (isSel) PrimaryBlueBtnText else Color.White)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTonalityDialog = false }) {
                    Text("Done", color = PrimaryBlueAccent)
                }
            }
        )
    }
}
