package com.soundstage.mixer.ui.pages

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.soundstage.mixer.R
import com.soundstage.mixer.model.SheetItem
import com.soundstage.mixer.ui.components.AmbientStudioBackground
import com.soundstage.mixer.utils.ChordNotationConverter
import com.soundstage.mixer.utils.NotationSystem
import kotlinx.coroutines.delay

private val SnapshotAccentColor = Color(0xFF9BC0F8)

// Palette of colors for chord sheet text
private val SheetTextPalette = listOf(
    Pair("White", Color(0xFFFFFFFF)),
    Pair("Ice Cyan", Color(0xFF67E8F9)),
    Pair("Gold", Color(0xFFFDE047)),
    Pair("Mint", Color(0xFF86EFAC)),
    Pair("Sunset", Color(0xFFFDBA74)),
    Pair("Lavender", Color(0xFFC4B5FD)),
    Pair("Coral", Color(0xFFFDA4AF)),
    Pair("Slate", Color(0xFFCBD5E1))
)

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
    modifier: Modifier = Modifier
) {
    var editingSheetId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Global notation system across all of Sheets (Default: Nashville)
    var globalNotation by remember { mutableStateOf(NotationSystem.NASHVILLE) }

    val activeSheet = remember(sheets, editingSheetId) {
        sheets.find { it.id == editingSheetId }
    }

    val filteredSheets = remember(sheets, searchQuery) {
        if (searchQuery.isBlank()) {
            sheets
        } else {
            sheets.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.key.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Official Setlists ambient background with luminous floating bubbles
    AmbientStudioBackground(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("sheets_full_page")
        ) {
            if (activeSheet != null) {
                // INVERTED 60% (Right) / 40% (Left) SPLIT SCREEN EDITOR
                InvertedSplitScreenSheetEditor(
                    sheet = activeSheet,
                    globalNotation = globalNotation,
                    onSave = { updatedTitle, updatedKey, updatedContent ->
                        onUpdateSheet(activeSheet.id, updatedTitle, updatedKey, updatedContent)
                        editingSheetId = null
                    },
                    onDelete = {
                        onDeleteSheet(activeSheet.id)
                        editingSheetId = null
                    },
                    onBack = { editingSheetId = null }
                )
            } else {
                // MAIN SHEETS CATALOG (Style Setlists: Search + Notation left, Title center, Create+Settings right)
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. TOP HEADER (Exact Setlists layout + Notation toggle pill next to search)
                    SheetsCatalogHeader(
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        globalNotation = globalNotation,
                        onToggleNotation = { globalNotation = globalNotation.next() },
                        onBackToMixer = onBackToMixer,
                        onOpenSettings = onOpenSettings,
                        onCreateNew = {
                            val newTitle = "New Song ${sheets.size + 1}"
                            onCreateSheet(newTitle, "C", "")
                            editingSheetId = sheets.firstOrNull()?.id
                        }
                    )

                    // 2. DIRECT CARDS GRID (No Recently used or All sections, no Cancel or Load)
                    if (filteredSheets.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x14FFFFFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_sheet_books),
                                        contentDescription = "Sheets Library",
                                        tint = Color(0x66FFFFFF),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Text(
                                    text = if (searchQuery.isBlank()) "No sheets in library" else "No matching sheets",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (searchQuery.isBlank()) "Create your first song sheet to start composing" else "Try another search keyword",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                                if (searchQuery.isBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        colors = ButtonDefaults.buttonColors(containerColor = SnapshotAccentColor),
                                        onClick = {
                                            onCreateSheet("New Song", "C", "")
                                            editingSheetId = sheets.firstOrNull()?.id
                                        },
                                        modifier = Modifier.testTag("btn_sheets_create_first")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Create",
                                            tint = Color(0xFF0F172A),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Create Sheet",
                                            color = Color(0xFF0F172A),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 280.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            itemsIndexed(filteredSheets, key = { _, item -> item.id }) { index, sheet ->
                                StaggeredSheetGridCard(
                                    sheet = sheet,
                                    index = index,
                                    globalNotation = globalNotation,
                                    onClick = {
                                        editingSheetId = sheet.id
                                        onSelectSheet(sheet.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================================================================================
// TOP BAR (Style Setlists: Mixer + Narrowed Search + Notation Pill on left, Title center, Create + Settings on right)
// ======================================================================================
@Composable
private fun SheetsCatalogHeader(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    globalNotation: NotationSystem,
    onToggleNotation: () -> Unit,
    onBackToMixer: () -> Unit,
    onOpenSettings: () -> Unit,
    onCreateNew: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Mixer return button + Search Bar + Notation Toggle Pill with relief
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Mixer Return Button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(2.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E2435))
                    .clickable { onBackToMixer() }
                    .testTag("btn_sheets_back_mixer"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Mixer",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Search Bar (Narrowed slightly to give room to notation pill)
            Box(
                modifier = Modifier
                    .widthIn(min = 160.dp, max = 220.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E2435))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(SnapshotAccentColor),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search...",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }

            // GLOBAL NOTATION TOGGLE PILL (Tactile relief effect, single tap directly switches mode)
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .shadow(3.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black, spotColor = Color(0x66000000))
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF283144), Color(0xFF1D2433))
                        )
                    )
                    .drawWithContent {
                        drawContent()
                        drawLine(
                            color = Color.White.copy(alpha = 0.16f),
                            start = Offset(4.dp.toPx(), 1f),
                            end = Offset(size.width - 4.dp.toPx(), 1f),
                            strokeWidth = 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    .clickable { onToggleNotation() }
                    .padding(horizontal = 12.dp)
                    .testTag("btn_toggle_global_notation"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = globalNotation.shortName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SnapshotAccentColor
                )
            }
        }

        // Center: Title "Sheets"
        Text(
            text = "Sheets",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 14.dp)
        )

        // Right: "+ Create New Sheet" button + Settings Gear icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            modifier = Modifier.weight(1f)
        ) {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = SnapshotAccentColor),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
                onClick = { onCreateNew() },
                modifier = Modifier.testTag("btn_sheets_create_new")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Create New Sheet",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(2.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E2435))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                    .clickable { onOpenSettings() }
                    .testTag("btn_sheets_settings"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ======================================================================================
// STAGGERED GRID CARD (With fluid cascading delay animation & Global Notation conversion)
// ======================================================================================
@Composable
private fun StaggeredSheetGridCard(
    sheet: SheetItem,
    index: Int,
    globalNotation: NotationSystem,
    onClick: () -> Unit
) {
    val keyColor = sheet.getKeyColor()

    var isAnimated by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index * 40L).coerceAtMost(400L))
        isAnimated = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (isAnimated) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "sheet_card_alpha"
    )

    val translateY by animateFloatAsState(
        targetValue = if (isAnimated) 0f else 24f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "sheet_card_slide"
    )

    // Convert preview lines to active global notation (e.g. Nashville numbers)
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
            .fillMaxWidth()
            .height(138.dp)
            .graphicsLayer {
                this.alpha = alpha
                this.translationY = translateY
            }
            .shadow(3.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black, spotColor = Color(0x66000000))
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF222836), Color(0xFF191F2C))
                )
            )
            .drawWithContent {
                drawContent()
                drawLine(
                    color = Color.White.copy(alpha = 0.10f),
                    start = Offset(6.dp.toPx(), 1f),
                    end = Offset(size.width - 6.dp.toPx(), 1f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            .clickable { onClick() }
            .padding(14.dp)
            .testTag("grid_sheet_card_${sheet.id}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row: Title + Key Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = sheet.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(keyColor.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = sheet.key,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = keyColor
                    )
                }
            }

            // Preview Text (Converted dynamically to Nashville / Roman / Solfège, no prompt text below)
            if (convertedContent.isNotEmpty()) {
                val annotatedContent = buildAnnotatedString {
                    val lines = convertedContent.lines().take(3)
                    lines.forEachIndexed { i, line ->
                        if (line.trim().startsWith("[") && line.contains("]")) {
                            val tag = line.substringBefore("]") + "]"
                            val rest = line.substringAfter("]")
                            withStyle(SpanStyle(color = Color(0xFF60A5FA), fontWeight = FontWeight.SemiBold)) {
                                append(tag)
                            }
                            withStyle(SpanStyle(color = Color(0xFFCBD5E1))) {
                                append(rest)
                            }
                        } else {
                            withStyle(SpanStyle(color = Color(0xFF94A3B8))) {
                                append(line)
                            }
                        }
                        if (i < lines.size - 1) append("\n")
                    }
                }

                Text(
                    text = annotatedContent,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "No chords written yet",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }

            // Empty spacer replacing the deleted "tap to open in split screen" text
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

// ======================================================================================
// INVERTED SPLIT SCREEN FULL PAGE EDITOR
// Left (40%): Tools & Settings + Header (Back, Title, Tonality, Delete, Save)
// Right (60%): Clean Document Page stretching FULL HEIGHT up to top of screen!
// ======================================================================================
@Composable
private fun InvertedSplitScreenSheetEditor(
    sheet: SheetItem,
    globalNotation: NotationSystem,
    onSave: (title: String, key: String, content: String) -> Unit,
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
    var currentFontSize by remember { mutableIntStateOf(sheet.fontSize.coerceIn(12, 32)) }
    var selectedTextColor by remember { mutableStateOf(sheet.getTextColor()) }

    // Centered Bubble Popups
    var showTonalityBubble by remember { mutableStateOf(false) }
    var showColorBubble by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Last inserted chord tracking for smart extension attachment
    var lastInsertedChord by remember { mutableStateOf<String?>(null) }

    // Smart diatonic chords generated based on Root Key, Mode (Major/Minor) and active Global Notation
    val diatonicChords = remember(selectedKeyRoot, isMinorMode, globalNotation) {
        ChordNotationConverter.getDiatonicChords(selectedKeyRoot, isMinorMode, globalNotation)
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ==============================================================================
        // LEFT PANE (40%): Tools, Settings & Action Header
        // ==============================================================================
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.40f)
                .shadow(4.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black, spotColor = Color(0x66000000))
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF161A26))
                .drawWithContent {
                    drawContent()
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(8.dp.toPx(), 1f),
                        end = Offset(size.width - 8.dp.toPx(), 1f),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                .padding(14.dp)
                .testTag("editor_left_tools_panel")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. TOP HEADER OF LEFT PANEL: Back, Title, Tonality, Delete, Save Sheet
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Back Button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0x1AFFFFFF))
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

                        // Compact Tonality Button (No arrow, clicks to open centered bubble popup)
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22FFFFFF))
                                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                                .clickable { showTonalityBubble = true }
                                .padding(horizontal = 10.dp)
                                .testTag("btn_editor_tonality"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = fullKeyName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // Delete Button (Corbeille with Snapshot Accent Color background!)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SnapshotAccentColor)
                                .clickable { showDeleteConfirm = true }
                                .testTag("btn_editor_delete"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Save Sheet Action Button
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SnapshotAccentColor)
                                .clickable { onSave(title, fullKeyName, content) }
                                .padding(horizontal = 12.dp)
                                .testTag("btn_editor_save"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Save Sheet",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }

                    // Editable Song Title Field
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        cursorBrush = SolidColor(SnapshotAccentColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x14FFFFFF))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("input_song_title"),
                        decorationBox = { innerTextField ->
                            if (title.isEmpty()) {
                                Text(
                                    text = "Song Title...",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0x4DFFFFFF)
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // 2. TYPOGRAPHY & COLOR
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "TYPOGRAPHY & COLOR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.5.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Font Size Stepper [-]
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x18FFFFFF))
                                .clickable {
                                    if (currentFontSize > 12) currentFontSize -= 2
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "−", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        // Size Label
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x12FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$currentFontSize pt",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // Font Size Stepper [+]
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x18FFFFFF))
                                .clickable {
                                    if (currentFontSize < 32) currentFontSize += 2
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        // Compact Color Pill (No arrow, clicks to open centered bubble popup)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x18FFFFFF))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .clickable { showColorBubble = true }
                            .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(selectedTextColor)
                            )
                        }
                    }

                    // Font Size Preset Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(14 to "S", 18 to "M", 22 to "L", 26 to "XL").forEach { (sz, label) ->
                            val isSelected = currentFontSize == sz
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) SnapshotAccentColor else Color(0x14FFFFFF))
                                    .clickable { currentFontSize = sz }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$label ($sz)",
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF0F172A) else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                // 3. STRUCTURE TAGS (Positioned immediately below Typography & Color)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "STRUCTURE TAGS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.5.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        listOf("Intro", "Verse", "Chorus", "Bridge", "Pre-Chorus", "Solo", "Outro").forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x18FFFFFF))
                                    .clickable {
                                        val prefix = if (content.isEmpty() || content.endsWith("\n")) "" else "\n\n"
                                        content = "$content$prefix[$tag]\n"
                                        lastInsertedChord = null
                                    }
                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "[$tag]",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }
                }

                // 4. SMART CHORD PALETTE & FULL EXTENSIONS
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "CHORDS (${globalNotation.shortName})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 0.5.sp
                        )
                        if (lastInsertedChord != null) {
                            Text(
                                text = "Active: $lastInsertedChord",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SnapshotAccentColor
                            )
                        }
                    }

                    // Core Diatonic Chords
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        diatonicChords.forEach { chord ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x22FFFFFF))
                                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        val separator = if (content.isEmpty() || content.endsWith(" ") || content.endsWith("\n")) "" else " · "
                                        content = "$content$separator$chord"
                                        lastInsertedChord = chord
                                    }
                                    .padding(horizontal = 11.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = chord,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Full Extensions Bar (No neon blue! Refined slate with Snapshot Accent borders)
                    Text(
                        text = "EXTENSIONS (Applies to active chord)",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B),
                        letterSpacing = 0.3.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        ChordNotationConverter.fullExtensions.forEach { ext ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF222B3D))
                                    .border(1.dp, SnapshotAccentColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
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
                                            content = if (content.isEmpty() || content.endsWith(" ")) "$content$ext" else "$content $ext"
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = ext,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SnapshotAccentColor
                                )
                            }
                        }
                    }
                }

                // 5. QUICK ACTIONS
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "QUICK ACTIONS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.5.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Bar Line |
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x16FFFFFF))
                                .clickable {
                                    val sep = if (content.isEmpty() || content.endsWith(" ")) "" else " "
                                    content = "$content$sep| "
                                    lastInsertedChord = null
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Bar Line |", fontSize = 11.sp, color = Color.White)
                        }

                        // Clear Text
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x14EF4444))
                                .clickable {
                                    content = ""
                                    lastInsertedChord = null
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Clear Text", fontSize = 11.sp, color = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }

        // ==============================================================================
        // RIGHT PANE (60%): Clean Document Page stretching FULL HEIGHT up to top!
        // ==============================================================================
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.60f)
                .shadow(4.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black, spotColor = Color(0x66000000))
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF191F2C))
                .drawWithContent {
                    drawContent()
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(8.dp.toPx(), 1f),
                        end = Offset(size.width - 8.dp.toPx(), 1f),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                .padding(20.dp)
                .testTag("editor_right_sheet_page")
        ) {
            BasicTextField(
                value = content,
                onValueChange = {
                    content = it
                    lastInsertedChord = null
                },
                textStyle = TextStyle(
                    color = selectedTextColor,
                    fontSize = currentFontSize.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = (currentFontSize * 1.55f).sp
                ),
                cursorBrush = SolidColor(SnapshotAccentColor),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .testTag("input_sheet_content"),
                decorationBox = { innerTextField ->
                    if (content.isEmpty()) {
                        Text(
                            text = "Type your chords and lyrics here...\n\nExample:\n[Intro] 1 · 6m · 4 · 5\n\n[Verse 1]\n1               5\nAmazing grace, how sweet the sound\n6m              4\nThat saved a wretch like me",
                            fontSize = currentFontSize.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = (currentFontSize * 1.55f).sp,
                            color = Color(0x4DFFFFFF)
                        )
                    }
                    innerTextField()
                }
            )
        }
    }

    // ==================================================================================
    // CENTERED BUBBLE POP-UP: TONALITY SELECTOR
    // Elastic spring animation, Snapshot Accent colors, Major/Minor toggle
    // ==================================================================================
    if (showTonalityBubble) {
        Dialog(onDismissRequest = { showTonalityBubble = false }) {
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1E2435))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Song Key & Mode",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // Major / Minor Mode Toggle Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x18FFFFFF))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!isMinorMode) SnapshotAccentColor else Color.Transparent)
                                .clickable { isMinorMode = false }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Major",
                                fontSize = 12.sp,
                                fontWeight = if (!isMinorMode) FontWeight.Bold else FontWeight.Normal,
                                color = if (!isMinorMode) Color(0xFF0F172A) else Color(0xFF94A3B8)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isMinorMode) SnapshotAccentColor else Color.Transparent)
                                .clickable { isMinorMode = true }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Minor",
                                fontSize = 12.sp,
                                fontWeight = if (isMinorMode) FontWeight.Bold else FontWeight.Normal,
                                color = if (isMinorMode) Color(0xFF0F172A) else Color(0xFF94A3B8)
                            )
                        }
                    }

                    // 12 Roots Grid (Selected note uses Snapshot Accent Color!)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChordNotationConverter.roots.chunked(4).forEach { rowRoots ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowRoots.forEach { r ->
                                    val isSel = (r == selectedKeyRoot)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) SnapshotAccentColor else Color(0x14FFFFFF))
                                            .clickable {
                                                selectedKeyRoot = r
                                                showTonalityBubble = false
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = r,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSel) Color(0xFF0F172A) else Color(0xFFCBD5E1)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================================================================================
    // CENTERED BUBBLE POP-UP: TEXT COLOR PALETTE
    // ==================================================================================
    if (showColorBubble) {
        Dialog(onDismissRequest = { showColorBubble = false }) {
            Box(
                modifier = Modifier
                    .width(280.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1E2435))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Select Text Color",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    SheetTextPalette.chunked(4).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowColors.forEach { (name, color) ->
                                val isSel = selectedTextColor == color
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSel) 3.dp else 1.dp,
                                            color = if (isSel) SnapshotAccentColor else Color(0x33FFFFFF),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            selectedTextColor = color
                                            showColorBubble = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSel) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF0F172A))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog (Styled with Snapshot Accent color)
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Sheet", color = Color.White) },
            text = { Text("Are you sure you want to delete \"${sheet.title}\"?", color = Color(0xFFCBD5E1)) },
            containerColor = Color(0xFF1E2435),
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = SnapshotAccentColor),
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}
